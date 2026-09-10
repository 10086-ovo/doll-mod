package io.github.a10086ovo.doll.network;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.BiomeSearchType;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.DollRecallRegistry;
import io.github.a10086ovo.doll.entity.StructureSearchType;
import io.github.a10086ovo.doll.entity.VillageSearchType;
import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import io.github.a10086ovo.doll.network.payload.OpenDollControlPanelPayload;
import io.github.a10086ovo.doll.network.payload.RecallDollPayload;
import io.github.a10086ovo.doll.network.payload.RequestSearchPayload;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import io.github.a10086ovo.doll.network.payload.SelectDollModePayload;
import io.github.a10086ovo.doll.network.payload.StructureCatalogPayload;
import io.github.a10086ovo.doll.network.payload.ToggleMarkPayload;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import io.github.a10086ovo.doll.util.SearchMarkStore;
import io.github.a10086ovo.doll.geo.GeoIndex;
import io.github.a10086ovo.doll.geo.GeoIndexService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DollNetworking {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	/** 搜索冷却记录（玩家 UUID → 上次搜索时间戳 ms，三类搜索共用；按玩家限流，防多只人偶绕过冷却）。 */
	private static final Map<UUID, Long> searchCooldown = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 群系搜索工作线程池：纯噪声采样（{@code getNoiseBiome}）只读、线程安全，
	 * 交工作线程执行以卸去主线程 ~500 次采样负担。小规模固定池便于多人并发时并行吞吐。
	 */
	private static final ExecutorService BIOME_SEARCH_EXECUTOR =
		Executors.newFixedThreadPool(3, r -> {
			Thread t = new Thread(r, "doll-biome-search");
			t.setDaemon(true);
			return t;
		});

	/**
	 * 结构/村庄搜索为服务端 tick 专属路径（{@code StructureManager} 非线程安全，不可逕移工作线程）。
	 * 为避免单 tick 瞬时过载，将一次搜索的多次 {@code findNearestMapStructure} 拆为若干分片，
	 * 每 tick 最多执行 {@link #STRUCTURE_SLICES_PER_TICK} 片，消除单 tick 峰值、结果迟至界面仍呈"搜索中"。
	 */
	private static final int STRUCTURE_SLICES_PER_TICK = 3;
	private static final int PENDING_SEARCH_MAX = 64;

	/** 搜索结果缓存 LRU 上限（玩家数）。超过后淘汰最久未访问的玩家条目。 */
	private static final int CACHE_MAX_PLAYERS = 256;

	/** 搜索半径：100 区块（1600 格），以玩家发起搜索时所在位置为中心。 */
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	private static final int SEARCH_RADIUS_BLOCKS = SEARCH_RADIUS_CHUNKS * 16;

	/**
	 * L1 开服预索引参数：预索引各维度以出生点为中心、半径 16000 格（1000 区块）的结构/村庄/群系，
	 * 使玩家搜索落在该范围内的目标直接命中 GeoIndex、秒回。
	 * 结构/村庄每分类最多收录 PREINDEX_PER_CATEGORY_MAX 个「最近」候选（offering：只漏远处不漏近处）；
	 * 群系按 PREINDEX_BIOME_STEP 方形网格采样（与现搜索步长 48 一致）。
	 */
	private static final int PREINDEX_RADIUS_BLOCKS = 16000;
	private static final int PREINDEX_PER_CATEGORY_MAX = 2000;
	private static final int PREINDEX_BIOME_STEP = 48;

	/** 群系搜索结果的最小代表点间距（格）：同一片连续群系只保留间隔≥此值的代表性坐标，避免相邻点被重复列出。 */
	private static final int BIOME_RESULT_MIN_GAP = 64;

	/** 开服预索引后台线程（SERVER_STARTED 启动、SERVER_STOPPING 中断并 join）。 */
	private static volatile Thread preIndexThread;

	/**
	 * 预索引中止标志：服务端停止中断后台线程时置位，用于防止「索引已就绪」完成提示误发
	 * （后台线程内部多处用 {@code Thread.interrupted()} 消费中断位，仅靠标志位才能可靠识别提前 return）。
	 */
	private static volatile boolean preIndexAborted;

	/** 预索引是否仍在进行：进入预定为 false，全部维度完成后置 false；玩家加入时据此补发「正在预载」提示。 */
	private static volatile boolean preIndexRunning;

	/** 共享缓存复用判定：玩家与已缓存搜索中心点水平距离不超过此值即可直接复用其候选（零主线程搜索）。 */
	private static final int SHARED_REUSE_BLOCKS = SEARCH_RADIUS_BLOCKS - 100;
	/** 同一「维度:目标」的共享条目上限，防长时运行内存增长。 */
	private static final int SHARED_ENTRIES_PER_TARGET = 8;

	/**
	 * 搜索结果缓存：玩家 UUID → (分类:目标索引:维度 → 上次搜索的候选坐标)。
	 * 普通打开搜索界面直接回放缓存（零开销），点击刷新才重新搜索并覆盖缓存。
	 * LRU 上限 + 断线清理，防长时运行内存泄漏。
	 */
	private static final Map<UUID, Map<String, List<int[]>>> searchResultCache =
		java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<UUID, Map<String, List<int[]>>> eldest) {
				return size() > CACHE_MAX_PLAYERS;
			}
		});

	/** 一条可共享的结构/村庄搜索结果（中心点 + 绝对候选坐标）。 */
	private static final class SharedSearchEntry {
		final int cx;
		final int cz;
		final List<int[]> candidates;

		SharedSearchEntry(int cx, int cz, List<int[]> candidates) {
			this.cx = cx;
			this.cz = cz;
			this.candidates = candidates;
		}
	}

	/**
	 * 世界级共享缓存（多人核心收益）：维度:目标 → 若干中心点各异的结果条目。
	 * 甲在某处搜过，乙在附近搜索同一目标时直接复用甲的候选坐标（过滤+排序），不再各查一遍。
	 * 结构/村庄位置由世界种子固定，永不陈腐；维度切换或换世界时于服务器启动清空。
	 */
	private static final Map<String, List<SharedSearchEntry>> sharedSearchCache = new java.util.HashMap<>();

	/** 等待分片执行的结构/村庄搜索任务（服务端 tick 处理）。 */
	private static final java.util.ArrayDeque<StructureSearchJob> pendingSearches = new java.util.ArrayDeque<>();

	/** 一次分片式结构/村庄搜索任务的状态。 */
	private static final class StructureSearchJob {
		final ServerPlayer player;
		final int category;
		final int targetIndex;
		final String cacheKey;
		final ServerLevel level;
		final HolderSet<Structure> set;
		final int centerX;
		final int centerY;
		final int centerZ;
		final int totalSlices;
		int nextSlice;
		final List<int[]> candidates = new ArrayList<>();

		StructureSearchJob(ServerPlayer player, int category, int targetIndex, String cacheKey,
				ServerLevel level, HolderSet<Structure> set, int centerX, int centerY, int centerZ, int totalSlices) {
			this.player = player;
			this.category = category;
			this.targetIndex = targetIndex;
			this.cacheKey = cacheKey;
			this.level = level;
			this.set = set;
			this.centerX = centerX;
			this.centerY = centerY;
			this.centerZ = centerZ;
			this.totalSlices = totalSlices;
		}
	}

	private DollNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SelectDollModePayload.TYPE, SelectDollModePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RecallDollPayload.TYPE, RecallDollPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSearchPayload.TYPE, RequestSearchPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleMarkPayload.TYPE, ToggleMarkPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDollControlPanelPayload.TYPE, OpenDollControlPanelPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateDollSnapshotPayload.TYPE, UpdateDollSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SearchResultsPayload.TYPE, SearchResultsPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StructureCatalogPayload.TYPE, StructureCatalogPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectDollModePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				Entity entity = player.level().getEntity(payload.dollEntityId());
				if (!(entity instanceof DollEntity doll)) {
					return;
				}
				// 仅主人可切换模式（远程指挥：同维度、实体在加载范围内即可，无距离限制）
				// 统一走 isOwnedBy：owner 为 null（未设置 / NBT 异常）时同样拒绝，避免任意玩家越权操控
				if (!doll.isOwnedBy(player)) {
					player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
					return;
				}
				boolean ok = doll.switchMode(payload.modeSlot08());
				if (ok) {
					// 切模式成功：向 owner 发实时快照，控制面板据此更新激活高亮
					// （远程切模式要求实体同维度，dimensionName 即玩家所在维度）
					String name = doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
					String dimName = player.level().dimension().identifier().getPath();
					BlockPos dp = doll.blockPosition();
					DollSnapshot snap = new DollSnapshot(
						doll.getId(), doll.getUUID().toString(), name, doll.getDollLevel(), doll.getActiveMode(),
						doll.isFollowEnabled(), doll.isTunneling(),
						true, (int) doll.distanceToSqr(player), dimName,
						dp.getX(), dp.getY(), dp.getZ());
					ServerPlayNetworking.send(player, new UpdateDollSnapshotPayload(snap));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RecallDollPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				java.util.UUID uuid;
				try {
					uuid = java.util.UUID.fromString(payload.dollUuid());
				} catch (IllegalArgumentException e) {
					return;
				}
				// 不信任客户端坐标：统一走 DollRecallService 从 DollRecallRegistry 取服务端坐标
				io.github.a10086ovo.doll.entity.DollRecallService.recall(
					(ServerLevel) player.level(), player, uuid, player.blockPosition());
			});
		});

		// ---- 统一搜索：普通打开回放缓存；refresh=true 才以玩家当前位置为中心重新搜索 ----
		ServerPlayNetworking.registerGlobalReceiver(RequestSearchPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				Entity entity = player.level().getEntity(payload.dollEntityId());
				if (!(entity instanceof DollEntity doll) || !doll.isOwnedBy(player)) {
					return;
				}
				if (doll.getDollVariant() != io.github.a10086ovo.doll.entity.DollVariant.GUIDE) {
					return;
				}
				ServerLevel serverLevel = (ServerLevel) player.level();
				String cacheKey = payload.category() + ":" + payload.targetIndex() + ":" + serverLevel.dimension().identifier();
				List<int[]> cached = peekCache(player.getUUID(), cacheKey);

				// GeoIndex 优先（结构/村庄/群系）：索引已收录该目标候选则直接回放（纯推演结果，秒回、零主线程搜索）。
				// L1 后群系也走索引优先：开服预索引把群系写入 GeoIndex，命中直接回放；未命中才落到异步采样/分片。
				{
					String dimId = serverLevel.dimension().identifier().toString();
					String geoKey = payload.category() + ":" + payload.targetIndex();
					List<int[]> indexed = GeoIndex.query(dimId, geoKey);
					if (!indexed.isEmpty()) {
						storeCache(player.getUUID(), cacheKey, indexed);
						ServerPlayNetworking.send(player,
							buildResults(player, payload.category(), payload.targetIndex(), indexed));
						return;
					}
				}

				// 非刷新请求且已有本玩家缓存：直接回放上次结果（零开销，不触发冷却）
				if (!payload.refresh() && cached != null) {
					ServerPlayNetworking.send(player,
						buildResults(player, payload.category(), payload.targetIndex(), cached));
					return;
				}

				// 群系：工作线程异步采样（纯噪声、线程安全），与共享缓存无涉
				if (payload.category() == SearchCategory.BIOME) {
					// 冷却检查（2 秒，按玩家，三类搜索共用）
					if (!acceptCooldown(player)) {
						replayOrCooldown(player, payload, cached);
						return;
					}
					startBiomeSearchAsync(context.server(), player, serverLevel, payload, cacheKey);
					return;
				}

				// ---- 结构/村庄：先试共享缓存复用（多人免重复搜索，零主线程开销）----
				if (!payload.refresh()) {
					List<SharedSearchEntry> shared = sharedSearchCache.get(sharedTargetKey(serverLevel, payload.category(), payload.targetIndex()));
					if (shared != null) {
						BlockPos p = player.blockPosition();
						SharedSearchEntry best = null;
						long bestDist = Long.MAX_VALUE;
						for (SharedSearchEntry e : shared) {
							long d = horizDistSq(p, e.cx, e.cz);
							if (d < bestDist) {
								bestDist = d;
								best = e;
							}
						}
						if (best != null && horizDistSq(p, best.cx, best.cz) <= (long) SHARED_REUSE_BLOCKS * SHARED_REUSE_BLOCKS) {
							// 复用：以玩家坐标过滤+排序后回放，并回填本玩家缓存
							List<int[]> filtered = new ArrayList<>();
							for (int[] c : best.candidates) {
								if (horizDistSq(p, c[0], c[1]) <= (long) SEARCH_RADIUS_BLOCKS * SEARCH_RADIUS_BLOCKS) {
									filtered.add(c);
								}
							}
							storeCache(player.getUUID(), cacheKey, filtered);
							ServerPlayNetworking.send(player,
								buildResults(player, payload.category(), payload.targetIndex(), filtered));
							return;
						}
					}
				}

				// 需要真实搜索：冷却检查
				if (!acceptCooldown(player)) {
					replayOrCooldown(player, payload, cached);
					return;
				}

				// 分片式搜索入队（每 tick 有界执行，消除单 tick 峰值）
				if (pendingSearches.size() >= PENDING_SEARCH_MAX) {
					player.sendSystemMessage(Component.translatable(
						"message." + DollModConstants.MOD_ID + ".search_busy"));
					return;
				}
				enqueueStructureSearch(serverLevel, player, payload, cacheKey);
			});
		});

		// ---- 打卡：客户端点击 √ 后在服务端持久化翻转状态（fire-and-forget） ----
		ServerPlayNetworking.registerGlobalReceiver(ToggleMarkPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				SearchMarkStore.toggle(player.getUUID(), payload.category(), payload.targetIndex(), payload.x(), payload.z());
			});
		});

		// 玩家进服：推送本世界全部结构注册键清单（客户端不加载结构注册表，须凭此构建可搜结构/村庄清单）
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p == null) {
				return;
			}
			ServerPlayNetworking.send(p, new StructureCatalogPayload(listStructureIds((ServerLevel) p.level())));
			// 开服预索引的「正在预载」提示是在服务端启动瞬间广播的，此时玩家列表往往还是空的，
			// 玩家实际进服后补发一次，确保能看到后台预载的等待提示。
			if (preIndexRunning) {
				p.sendSystemMessage(Component.translatable("gui." + DollModConstants.MOD_ID + ".preindex_start"));
			}
		});

		// 玩家断线：清理其搜索缓存与冷却记录，防长时运行内存泄漏
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			searchResultCache.remove(id);
			searchCooldown.remove(id);
		});

		// 服务器启动：清空世界级共享缓存、排队任务，加载各维度 GeoIndex 索引，并在后台守护线程启动 L1 预索引
		// （结构位置随世界种子而定，跨世界不应复用）。预索引纯推演（读 generatorState/噪声采样，不加载区块），
		// 与现有群系异步搜索在后台线程访问一致，放后台线程避免阻塞主线程与玩家进入。
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			sharedSearchCache.clear();
			pendingSearches.clear();
			for (ServerLevel lv : server.getAllLevels()) {
				GeoIndex.loadForDimension(server, lv);
			}
			Thread t = new Thread(() -> runStartupPreIndex(server), "DollGeoPreIndex");
			t.setDaemon(true);
			preIndexThread = t;
			preIndexRunning = true;
			t.start();
		});

		// 服务器停止：中断并短暂 join 后台预索引线程以稳定停止，再落盘各维度 GeoIndex 索引（D5 持久化）
	ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
		preIndexAborted = true;
		preIndexRunning = false;
		Thread t = preIndexThread;
		if (t != null) {
			t.interrupt();
			try {
				t.join(50);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
		for (ServerLevel lv : server.getAllLevels()) {
			GeoIndex.saveForDimension(server, lv);
		}
	});

		// 每 tick 递进结构/村庄分片：主线程有界执行，避免单 tick 瞬时过载
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int budget = STRUCTURE_SLICES_PER_TICK;
			Iterator<StructureSearchJob> it = pendingSearches.iterator();
			while (it.hasNext() && budget > 0) {
				StructureSearchJob job = it.next();
				int remaining = job.totalSlices - job.nextSlice;
				int take = Math.min(budget, remaining);
				for (int k = 0; k < take; k++) {
					runSlice(job, job.nextSlice++);
				}
				budget -= take;
				if (job.nextSlice >= job.totalSlices) {
					it.remove();
					finalizeSearch(job);
				}
			}
		});
	}

	/** 冷却通过则记录本次搜索并返回 true；否则返回 false。 */
	private static boolean acceptCooldown(ServerPlayer player) {
		long now = System.currentTimeMillis();
		Long last = searchCooldown.get(player.getUUID());
		if (last != null && now - last < 2_000L) {
			return false;
		}
		searchCooldown.put(player.getUUID(), now);
		return true;
	}

	/** 冷却中：有缓存回放缓存，否则提示剩余冷却并回空。 */
	private static void replayOrCooldown(ServerPlayer player, RequestSearchPayload payload, List<int[]> cached) {
		Long last = searchCooldown.get(player.getUUID());
		long now = System.currentTimeMillis();
		if (cached != null) {
			ServerPlayNetworking.send(player,
				buildResults(player, payload.category(), payload.targetIndex(), cached));
		} else {
			if (last != null) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".search_cooldown",
					String.format("%.1f", (2_000L - (now - last)) / 1000.0)));
			}
			ServerPlayNetworking.send(player,
				new SearchResultsPayload(payload.category(), payload.targetIndex(), List.of(),
					computeNotInDimension((ServerLevel) player.level(), payload.category(), payload.targetIndex())));
		}
	}

	/**
	 * 结构/村庄分片式搜索入队。任务在服务端 tick 上逐片执行（{@code StructureManager} 线程安全，
	 * 但每 tick 量有界），完成后回写缓存并发包。
	 */
	private static void enqueueStructureSearch(ServerLevel level, ServerPlayer player,
			RequestSearchPayload payload, String cacheKey) {
		int category = payload.category();
		int targetIndex = payload.targetIndex();
		boolean villagesOnly = category == SearchCategory.VILLAGE;
		ResourceKey<Structure> structureKey = resolveStructureKey(level, targetIndex, villagesOnly);
		int slices = villagesOnly ? 1 : 9; // 结构：中心 + 8 方向；村庄单次
		if (structureKey == null) {
			storeCache(player.getUUID(), cacheKey, List.of());
			ServerPlayNetworking.send(player,
				buildResults(player, category, targetIndex, List.of()));
			return;
		}

		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> holder = registry.get(structureKey).orElse(null);
		if (holder == null) {
			storeCache(player.getUUID(), cacheKey, List.of());
			ServerPlayNetworking.send(player,
				buildResults(player, category, targetIndex, List.of()));
			return;
		}
		HolderSet<Structure> set = HolderSet.direct(holder);
		BlockPos p = player.blockPosition();
		pendingSearches.addLast(new StructureSearchJob(player, category, targetIndex, cacheKey,
			level, set, p.getX(), p.getY(), p.getZ(), slices));
	}

	/** 列出本世界全部结构注册键（按键名字符串排序），推送客户端作为可搜结构清单。 */
	private static List<String> listStructureIds(ServerLevel level) {
		Registry<Structure> reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		return reg.keySet().stream()
			.map(Identifier::toString)
			.sorted()
			.toList();
	}

	// 按「客户端传来的目标序号」在结构注册表内定位具体结构键。
		// 序号 = 按注册键排序后该结构的位次（villageOnly 时只保留村庄类结构），
		// 与客户端界面生成顺序一致，故可覆盖注册表内全部结构（含 mod 新增）。
		private static ResourceKey<Structure> resolveStructureKey(ServerLevel level, int targetIndex, boolean villagesOnly) {
			Registry<Structure> reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			List<Identifier> ids = new ArrayList<>(reg.keySet());
			ids.removeIf(id -> id.getPath().startsWith("village_") != villagesOnly);
			ids.sort(Comparator.comparing(Identifier::toString));
			if (targetIndex < 0 || targetIndex >= ids.size()) {
				return null;
			}
			return ResourceKey.create(Registries.STRUCTURE, ids.get(targetIndex));
		}

		/** 同 {@link #resolveStructureKey}，定位生物群系注册键。 */
		private static ResourceKey<Biome> resolveBiomeKey(ServerLevel level, int targetIndex) {
			Registry<Biome> reg = level.registryAccess().lookupOrThrow(Registries.BIOME);
			List<Identifier> ids = new ArrayList<>(reg.keySet());
			ids.sort(Comparator.comparing(Identifier::toString));
			if (targetIndex < 0 || targetIndex >= ids.size()) {
				return null;
			}
			return ResourceKey.create(Registries.BIOME, ids.get(targetIndex));
		}

		/**
		 * 返回某维度全部结构注册键（按字符串排序；villagesOnly=true 只留 village_ 前缀）。
		 * 顺序与 {@link #resolveStructureKey} 完全一致（同一过滤 + 排序规则），故列表下标 i 即是
		 * 客户端/服务端展示该结构时用的 targetIndex，供预索引拼接 geoKey = "category:i"。
		 */
		private static List<ResourceKey<Structure>> structuresList(ServerLevel level, boolean villagesOnly) {
			Registry<Structure> reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			List<Identifier> ids = new ArrayList<>(reg.keySet());
			ids.removeIf(id -> id.getPath().startsWith("village_") != villagesOnly);
			ids.sort(Comparator.comparing(Identifier::toString));
			List<ResourceKey<Structure>> out = new ArrayList<>(ids.size());
			for (Identifier id : ids) {
				out.add(ResourceKey.create(Registries.STRUCTURE, id));
			}
			return out;
		}

		/**
		 * 返回某维度全部群系注册键（按字符串排序），下标 i 即客户端 {@code registryBiomes} 的 targetIndex。
		 * 排序规则与 {@link #resolveBiomeKey} 一致。
		 */
		private static List<ResourceKey<Biome>> biomeKeysList(ServerLevel level) {
			Registry<Biome> reg = level.registryAccess().lookupOrThrow(Registries.BIOME);
			List<Identifier> ids = new ArrayList<>(reg.keySet());
			ids.sort(Comparator.comparing(Identifier::toString));
			List<ResourceKey<Biome>> out = new ArrayList<>(ids.size());
			for (Identifier id : ids) {
				out.add(ResourceKey.create(Registries.BIOME, id));
			}
			return out;
		}

		/**
		 * L1 开服预索引：为每个维度以该维度出生点为中心、半径 16000 格，把结构/村庄/群系全部纯推演定位
		 * 并写入 GeoIndex。后台守护线程执行（SERVER_STARTED 启动），不阻塞主线程与玩家进入。
		 *
		 * <p>geoKey 拼接：categoryInt 取自 {@link SearchCategory} 枚举（STRUCTURE=0/BIOME=1/VILLAGE=2），
		 * 与客户端请求的 payload.category() 同源；targetIndex 为该目标在 structuresList/biomeKeysList
		 * 里的下标，与客户端 registryStructures/registryBiomes 及服务端 resolveStructureKey/resolveBiomeKey
		 * 展示序号一致，保证坐标不错位。
		 */
		private static void runStartupPreIndex(MinecraftServer server) {
			preIndexAborted = false;
			preIndexRunning = true;
			long threadStart = System.currentTimeMillis();
			LOGGER.info("DollGeoPreIndex 开服预索引开始");
			try {
				// 预载开始：向当前在线的每个玩家温和提示（切主线程发送）；发送失败不得中断预索引
				broadcastPreIndexMessage(server, "gui." + DollModConstants.MOD_ID + ".preindex_start");
				int dimCount = 0;
				for (ServerLevel lv : server.getAllLevels()) {
					if (Thread.interrupted() || preIndexAborted) {
						preIndexRunning = false;
						return;
					}
					String dimId = lv.dimension().identifier().toString();
					long dt0 = System.currentTimeMillis();
					try {
						preIndexDimension(server, lv);
						dimCount++;
						LOGGER.info("DollGeoPreIndex 维度 {} 预索引完成，耗时 {} ms", dimId, System.currentTimeMillis() - dt0);
					} catch (Throwable t) {
						// 单维度失败仅为局部：记日志并继续其它维度，不得中断整个预索引线程。
						LOGGER.error("DollGeoPreIndex 维度 {} 预索引异常：{}", dimId, t.toString(), t);
					}
					if (Thread.interrupted() || preIndexAborted) {
						preIndexRunning = false;
						return;
					}
				}
				// 全部维度预索引完成且该线程未被中断/中止，才提示「就绪」；
				// 有任何维度局部失败也视为"已启动足够索引"，仍发完成提示（避免玩家永远等不到）。
				preIndexRunning = false;
				LOGGER.info("DollGeoPreIndex 预索引完成：{} 个维度，总耗时 {} ms", dimCount, System.currentTimeMillis() - threadStart);
				broadcastPreIndexMessage(server, "gui." + DollModConstants.MOD_ID + ".preindex_done");
			} catch (Throwable t) {
				// 线程级兜底：绝不让任何未捕获异常静默杀死后台线程（否则既无「就绪」提示、索引也缺一大块）。
				preIndexRunning = false;
				LOGGER.error("DollGeoPreIndex 开服预索引异常", t);
			}
		}

		/** 把预索引状态提示切到服务端主线程，逐个通知当前在线的玩家。后台线程调用（非主线程），由 server.execute 归队发送。 */
		private static void broadcastPreIndexMessage(MinecraftServer server, String langKey) {
			server.execute(() -> {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					p.sendSystemMessage(Component.translatable(langKey));
				}
			});
		}

		/** 单维度预索引：结构(STRUCTURE)→村庄(VILLAGE)→群系(BIOME)，完成后立即落盘。 */
		private static void preIndexDimension(MinecraftServer server, ServerLevel level) {
			int cx;
		int cz;
		try {
			// 维度出生点：26.2 中经 LevelData.getRespawnData().pos() 取出生方块坐标（getSharedSpawnPos 已不存在）。
			BlockPos spawn = level.getLevelData().getRespawnData().pos();
			cx = spawn.getX();
			cz = spawn.getZ();
		} catch (Exception e) {
			cx = 0;
			cz = 0;
		}
			String dimId = level.dimension().identifier().toString();

			// 结构：categoryInt = SearchCategory.STRUCTURE(=0)。仅收集该维度可能存在且收集到非空候选的目标。
			// 对每个结构单独 try/catch：任一结构失败只跳过该结构，不影响本维度的村庄/群系及其它结构。
			List<ResourceKey<Structure>> structures;
			try {
				structures = structuresList(level, false);
			} catch (Throwable t) {
				structures = List.of();
			}
			for (int i = 0; i < structures.size(); i++) {
				if (Thread.interrupted()) {
					return;
				}
				ResourceKey<Structure> key = structures.get(i);
				try {
					if (!structureExistsInDim(level, key)) {
						continue;
					}
					List<int[]> cands = GeoIndexService.collectNearest(
						level, key, cx, cz, PREINDEX_RADIUS_BLOCKS, PREINDEX_PER_CATEGORY_MAX);
					if (!cands.isEmpty()) {
						GeoIndex.merge(dimId, SearchCategory.STRUCTURE + ":" + i, cands);
					}
				} catch (Throwable t) {
					LOGGER.error("DollGeoPreIndex 结构索引异常 {}：{}", key, t.toString());
				}
			}

			// 村庄：categoryInt = SearchCategory.VILLAGE(=2)。
			List<ResourceKey<Structure>> villages;
			try {
				villages = structuresList(level, true);
			} catch (Throwable t) {
				villages = List.of();
			}
			for (int i = 0; i < villages.size(); i++) {
				if (Thread.interrupted()) {
					return;
				}
				ResourceKey<Structure> key = villages.get(i);
				try {
					if (!structureExistsInDim(level, key)) {
						continue;
					}
					List<int[]> cands = GeoIndexService.collectNearest(
						level, key, cx, cz, PREINDEX_RADIUS_BLOCKS, PREINDEX_PER_CATEGORY_MAX);
					if (!cands.isEmpty()) {
						GeoIndex.merge(dimId, SearchCategory.VILLAGE + ":" + i, cands);
					}
				} catch (Throwable t) {
					LOGGER.error("DollGeoPreIndex 村庄索引异常 {}：{}", key, t.toString());
				}
			}

			// 群系：categoryInt = SearchCategory.BIOME(=1)。按方形网格预采样分桶，按 biomeKeysList 下标写入。
			List<ResourceKey<Biome>> biomes;
			try {
				biomes = biomeKeysList(level);
			} catch (Throwable t) {
				biomes = List.of();
			}
			Map<ResourceKey<Biome>, List<int[]>> buckets;
			try {
				buckets = GeoIndexService.preIndexBiomes(level, cx, cz, PREINDEX_RADIUS_BLOCKS, PREINDEX_BIOME_STEP);
			} catch (Throwable t) {
				LOGGER.error("DollGeoPreIndex 群系预采样异常 {}：{}", dimId, t.toString());
				buckets = java.util.Collections.emptyMap();
			}
			for (int i = 0; i < biomes.size(); i++) {
				if (Thread.interrupted()) {
					return;
				}
				ResourceKey<Biome> key = biomes.get(i);
				List<int[]> pts = buckets.get(key);
				if (pts == null || pts.isEmpty()) {
					continue;
				}
				GeoIndex.merge(dimId, SearchCategory.BIOME + ":" + i, pts);
			}

			// 该维度预索引完成：立即落盘，避免整机崩溃丢失（落盘失败仅为局部，不影响后续维度）。
			try {
				GeoIndex.saveForDimension(server, level);
			} catch (Throwable t) {
				LOGGER.error("DollGeoPreIndex 维度 {} 落盘索引异常：{}", dimId, t.toString());
			}
		}

		/**
		 * 该结构是否为当前维度可能生成：遍历维度生成状态里的全部结构集，
		 * 只要某个候选结构命中目标结构键即返回 true。
		 * 只在服务端主线程调用（读取 GeneratorState 需主线程安全情境）。
		 */
		private static boolean structureExistsInDim(ServerLevel level, ResourceKey<Structure> key) {
			try {
				ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
				if (state == null || state.possibleStructureSets() == null) {
					return false;
				}
				for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
					if (setHolder == null || setHolder.value() == null) {
						continue;
					}
					for (StructureSet.StructureSelectionEntry entry : setHolder.value().structures()) {
						if (entry != null && entry.structure() != null && entry.structure().is(key)) {
							return true;
						}
					}
				}
			} catch (Throwable t) {
				// 刚进世界时 GeneratorState 可能尚未就绪：单个判断失败不影响其它结构。
				return false;
			}
			return false;
		}

		/** 该群系是否为当前维度可能生成：查看维度 BiomeSource 的 possibleBiomes。只在服务端主线程调用。 */
		private static boolean biomeExistsInDim(ServerLevel level, ResourceKey<Biome> key) {
			return level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
				.anyMatch(h -> h.is(key));
		}

		/**
		 * 计算目标在「当前维度是否不可能存在」：key 为 null（越界/无效注册键）或该维度不存在
		 * 此结构/群系，都视为 notInDimension=true。仅在服务端主线程调用。
		 */
		private static boolean computeNotInDimension(ServerLevel level, int category, int targetIndex) {
			if (category == SearchCategory.BIOME) {
				ResourceKey<Biome> key = resolveBiomeKey(level, targetIndex);
				return key == null || !biomeExistsInDim(level, key);
			}
			boolean villagesOnly = category == SearchCategory.VILLAGE;
			ResourceKey<Structure> key = resolveStructureKey(level, targetIndex, villagesOnly);
			return key == null || !structureExistsInDim(level, key);
		}

		/**
		 * 执行任务的一片：分片 0 为中心 100 区块内最近结构，分片 1..8 为距中心 60 区块处、
	 * 各 40 区块半径的八个方向（中心点弧向间距约 45°，半径 40 区块使相邻圆形搜索区间充分重叠），
	 * 无缝铺满 20~100 区块环带，收集更多实例且消除斜向方向的结构检测缺口。
	 */
	private static void runSlice(StructureSearchJob job, int slice) {
		if (job.player.connection == null || job.player.isRemoved()) {
			return;
		}
		if (slice == 0) {
			addStructureHit(job.candidates, job.level, job.set,
				new BlockPos(job.centerX, job.centerY, job.centerZ), SEARCH_RADIUS_CHUNKS);
		} else {
			int i = slice - 1;
			double angle = 2 * Math.PI * (i + 0.5) / 8;
			int cx = job.centerX + (int) (Math.cos(angle) * 60 * 16);
			int cz = job.centerZ + (int) (Math.sin(angle) * 60 * 16);
			addStructureHit(job.candidates, job.level, job.set, new BlockPos(cx, job.centerY, cz), 40);
		}
	}

	/** 任务全部分片执行完毕：写本玩家缓存、写共享缓存、并入 GeoIndex、发结果。 */
	private static void finalizeSearch(StructureSearchJob job) {
		if (job.player.connection == null || job.player.isRemoved()) {
			return;
		}
		storeCache(job.player.getUUID(), job.cacheKey, job.candidates);
		storeShared(job.level, job.player, job.category, job.targetIndex, job.candidates);
		String dimId = job.level.dimension().identifier().toString();
		String geoKey = job.category + ":" + job.targetIndex;
		GeoIndex.merge(dimId, geoKey, job.candidates);
		ServerPlayNetworking.send(job.player,
			buildResults(job.player, job.category, job.targetIndex, job.candidates));
	}

	/** 将结果并入世界级共享缓存（按中心点归入条目，超上限抛最远旧条目）。 */
	private static void storeShared(ServerLevel level, ServerPlayer player,
			int category, int targetIndex, List<int[]> candidates) {
		String key = sharedTargetKey(level, category, targetIndex);
		int cx = player.blockPosition().getX();
		int cz = player.blockPosition().getZ();
		List<SharedSearchEntry> list = sharedSearchCache.computeIfAbsent(key, k -> new ArrayList<>());
		list.add(new SharedSearchEntry(cx, cz, new ArrayList<>(candidates)));
		if (list.size() > SHARED_ENTRIES_PER_TARGET) {
			int farthestIndex = 0;
			long farthest = -1;
			for (int i = 0; i < list.size(); i++) {
				long d = horizDistSq(player.blockPosition(), list.get(i).cx, list.get(i).cz);
				if (d > farthest) {
					farthest = d;
					farthestIndex = i;
				}
			}
			list.remove(farthestIndex);
		}
	}

	private static String sharedTargetKey(ServerLevel level, int category, int targetIndex) {
		return level.dimension().identifier() + "|" + category + "|" + targetIndex;
	}

	/**
	 * 群系搜索异步化：主线程只做两件事——取 BiomeSource/Sampler 引用、提交任务。
	 * 纯噪声采样在工作线程执行（{@code getNoiseBiome} 只读 sampler，线程安全），
	 * 完成后回主线程写缓存并发包，主线程不再承担 ~500 次噪声采样。
	 */
	private static void startBiomeSearchAsync(MinecraftServer server, ServerPlayer player,
			ServerLevel level, RequestSearchPayload payload, String cacheKey) {
		ResourceKey<Biome> biomeKey = resolveBiomeKey(level, payload.targetIndex());
		// 序号越界（注册表无此项）：直接回空结果
		if (biomeKey == null) {
			storeCache(player.getUUID(), cacheKey, List.of());
			ServerPlayNetworking.send(player,
				buildResults(player, payload.category(), payload.targetIndex(), List.of()));
			return;
		}
		// 主线程取引用，避免工作线程首次访问触发 GeneratorState 懒初始化
		BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
		Climate.Sampler sampler = level.getChunkSource().getGeneratorState().randomState().sampler();
		BlockPos playerPos = player.blockPosition();
		UUID playerUuid = player.getUUID();
		int category = payload.category();
		int targetIndex = payload.targetIndex();

		BIOME_SEARCH_EXECUTOR.submit(() -> {
			List<int[]> found = collectBiomes(playerPos, biomeKey, source, sampler);
			server.execute(() -> {
				if (!server.isRunning() || player.connection == null || player.isRemoved()) {
					return;
				}
				storeCache(playerUuid, cacheKey, found);
				String dimId = level.dimension().identifier().toString();
				String geoKey = category + ":" + targetIndex;
				GeoIndex.merge(dimId, geoKey, found);
				ServerPlayNetworking.send(player,
					buildResults(player, category, targetIndex, found));
			});
		});
	}

	/**
	 * 群系：以玩家为中心螺旋向外采样（步长 128 格），收集命中点。
	 * <p>
	 * 性能关键：通过 {@code BiomeSource.getNoiseBiome(四分位坐标, Climate.Sampler)} 做纯噪声计算，
	 * 与区块生成用的是同一套噪声，结果一致但<b>全程不加载、不生成区块</b>。
	 * 旧实现每格采样都经 {@code level.getBiome()} 强制同步生成区块，是多人卡顿的主因。
	 * 本方法只读传入的 source/sampler（无 level 依赖），可在工作线程安全执行。
	 */
	private static List<int[]> collectBiomes(BlockPos playerPos, ResourceKey<Biome> biomeKey,
			BiomeSource source, Climate.Sampler sampler) {
		int quartY = playerPos.getY() >> 2;
		int px = playerPos.getX();
		int pz = playerPos.getZ();

		List<int[]> candidates = new ArrayList<>();
		// 玩家脚下本身也检查（可能已站在目标群系中）
		if (source.getNoiseBiome(px >> 2, quartY, pz >> 2, sampler).is(biomeKey)) {
			candidates.add(new int[] { px, pz });
		}
		// 步长 128 时相邻采样点的径向/弧向间距可达 ~128 格，小型/狭长群系常整块落在采样空隙里被漏检。
		// 改为 48 格步长并提高每环采样数下限，使相邻采样点间距 ≤ ~48 格，保证 1600 格半径内群系不再漏检
		//（纯噪声采样在后台线程执行，~3500 次采样开销可忽略）。
		int step = 48;
		for (int r = step; r <= SEARCH_RADIUS_BLOCKS; r += step) {
			// 命中足够多即提前结束，控制耗时
			if (candidates.size() >= SearchResultsPayload.MAX_RESULTS * 4) {
				break;
			}
			int checks = Math.max(12, (int) (2 * Math.PI * r / step));
			for (int i = 0; i < checks; i++) {
				double angle = 2 * Math.PI * i / checks;
				int x = px + (int) (Math.cos(angle) * r);
				int z = pz + (int) (Math.sin(angle) * r);
				if (source.getNoiseBiome(x >> 2, quartY, z >> 2, sampler).is(biomeKey)
					&& !nearExisting(candidates, x, z)) {
					candidates.add(new int[] { x, z });
				}
			}
		}
		return candidates;
	}

	/**
	 * 单次最近结构查询，命中且不与已有候选重复时记录。不生成区块，
	 * 直接用原版 {@code findNearestMapStructure}（以区块为半径，起点确定性生成，
	 * 同一结构多次命中会返回同一坐标，由去重过滤）。仅应在服务端 tick 线程调用。
	 */
	private static void addStructureHit(List<int[]> candidates, ServerLevel level, HolderSet<Structure> set,
			BlockPos center, int radiusChunks) {
		com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> hit =
			level.getChunkSource().getGenerator().findNearestMapStructure(level, set, center, radiusChunks, false);
		if (hit == null) {
			return;
		}
		BlockPos p = hit.getFirst();
		if (!nearExisting(candidates, p.getX(), p.getZ())) {
			candidates.add(new int[] { p.getX(), p.getZ() });
		}
	}

	private static List<int[]> peekCache(UUID player, String key) {
		Map<String, List<int[]>> perPlayer = searchResultCache.get(player);
		return perPlayer != null ? perPlayer.get(key) : null;
	}

	private static void storeCache(UUID player, String key, List<int[]> candidates) {
		searchResultCache.computeIfAbsent(player, k -> new java.util.concurrent.ConcurrentHashMap<>())
			.put(key, new ArrayList<>(candidates));
	}

	/**
	 * 由候选坐标构造返回包：按与玩家<b>当前</b>水平距离升序排序（缓存回放时玩家可能已移动），
	 * 填充最新打卡状态，截取前 {@link SearchResultsPayload#MAX_RESULTS} 条。
	 */
	private static SearchResultsPayload buildResults(ServerPlayer player, int category, int targetIndex,
			List<int[]> candidates) {
		BlockPos playerPos = player.blockPosition();
		UUID owner = player.getUUID();
		List<int[]> sorted = new ArrayList<>(candidates);
		sorted.sort((a, b) -> Long.compare(horizDistSq(playerPos, a[0], a[1]), horizDistSq(playerPos, b[0], b[1])));
		// 群系是一整片连续区域，网格/螺旋采样会在同一片里落多个相邻点；按与玩家距离升序排好后，
		// 做最小间距去重（仅对 BIOME），使同一片群系只保留间隔足够远的代表点。
		// 在 buildResults 统一去重，保证「索引回放」与「现算 fallback」两条路径结果一致；
		// 结构/村庄分类不受影响（每处结构本就彼此相距甚远，保持原样）。
		if (category == SearchCategory.BIOME) {
			List<int[]> deduped = new ArrayList<>();
			for (int[] c : sorted) {
				boolean tooClose = false;
				for (int[] k : deduped) {
					long ddx = c[0] - k[0];
					long ddz = c[1] - k[1];
					if (ddx * ddx + ddz * ddz < (long) BIOME_RESULT_MIN_GAP * BIOME_RESULT_MIN_GAP) {
						tooClose = true;
						break;
					}
				}
				if (!tooClose) {
					deduped.add(c);
					if (deduped.size() >= SearchResultsPayload.MAX_RESULTS) {
						break;
					}
				}
			}
			sorted = deduped;
		}
		List<SearchResultsPayload.Entry> entries = new ArrayList<>();
		int n = Math.min(SearchResultsPayload.MAX_RESULTS, sorted.size());
		for (int i = 0; i < n; i++) {
			int[] c = sorted.get(i);
			boolean marked = SearchMarkStore.contains(owner, category, targetIndex, c[0], c[1]);
			entries.add(new SearchResultsPayload.Entry(c[0], c[1], marked));
		}
		// buildResults 的全部调用路径均位于服务端主线程（见各调用点），
		// 故可安全地在此按 category+targetIndex 重解析 key 并判定「该维度是否可能生成」。
		boolean notInDimension = computeNotInDimension((ServerLevel) player.level(), category, targetIndex);
		return new SearchResultsPayload(category, targetIndex, entries, notInDimension);
	}

	/** 水平距离平方（用 long 避免大坐标溢出）。 */
	private static long horizDistSq(BlockPos origin, int x, int z) {
		long dx = origin.getX() - x;
		long dz = origin.getZ() - z;
		return dx * dx + dz * dz;
	}

	/** 与已收集候选是否相距过近（容差 48 格，视为同一目标以免重复占用名额）。 */
	private static boolean nearExisting(List<int[]> candidates, int x, int z) {
		for (int[] c : candidates) {
			if (Math.abs(c[0] - x) <= 48 && Math.abs(c[1] - z) <= 48) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 收集玩家所有存活人偶（跨维度扫描）并发送控制面板数据包。
	 * 同时也从 DollRecallRegistry 中查出已卸载的人偶，一并列出供召回。
	 */
	public static void sendControlPanel(ServerPlayer player) {
		List<DollSnapshot> snapshots = new ArrayList<>();
		java.util.UUID owner = player.getUUID();
		EntityTypeTest<Entity, DollEntity> typeTest = EntityTypeTest.forExactClass(DollEntity.class);
		// 收集已加载的人偶
		for (ServerLevel lv : player.level().getServer().getAllLevels()) {
			boolean sameDim = lv == player.level();
			String dimName = lv.dimension().identifier().getPath();
			for (DollEntity doll : lv.getEntities(typeTest, d -> !d.isRemoved() && owner.equals(d.getOwnerUuid()))) {
				String name = doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
				int distSqr = sameDim ? (int) doll.distanceToSqr(player) : Integer.MAX_VALUE;
				BlockPos dp = doll.blockPosition();
				snapshots.add(new DollSnapshot(
					doll.getId(), doll.getUUID().toString(), name, doll.getDollLevel(), doll.getActiveMode(),
					doll.isFollowEnabled(), doll.isTunneling(), sameDim, distSqr, dimName,
					dp.getX(), dp.getY(), dp.getZ()));
			}
		}
		// 补充离线人偶（已加载的人偶已在上方列出，用 UUID 去重）
		java.util.Set<String> loadedUuids = new java.util.HashSet<>(snapshots.size());
		for (DollSnapshot snap : snapshots) {
			loadedUuids.add(snap.uuid());
		}
		for (var entry : DollRecallRegistry.getAll().entrySet()) {
			java.util.UUID dollUuid = entry.getKey();
			DollRecallRegistry.DollLocation loc = entry.getValue();
			if (!loc.ownerUuid().equals(owner)) continue;
			String uuidStr = dollUuid.toString();
			if (loadedUuids.contains(uuidStr)) continue;
			String dimName = loc.dimension().identifier().getPath();
			boolean sameDim = loc.dimension().equals(player.level().dimension());
			// 离线人偶无实体 id，用 -1 标记
			snapshots.add(new DollSnapshot(
				-1, uuidStr, "未知人偶", 0, -1,
				false, false, sameDim, Integer.MAX_VALUE, dimName,
				loc.pos().getX(), loc.pos().getY(), loc.pos().getZ()));
		}
		ServerPlayNetworking.send(player, new OpenDollControlPanelPayload(snapshots));
	}
}