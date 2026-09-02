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
import io.github.a10086ovo.doll.network.payload.ToggleMarkPayload;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import io.github.a10086ovo.doll.util.SearchMarkStore;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DollNetworking {

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

		// 玩家断线：清理其搜索缓存与冷却记录，防长时运行内存泄漏
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			searchResultCache.remove(id);
			searchCooldown.remove(id);
		});

		// 服务器启动：清空世界级共享缓存与排队任务（结构位置随世界种子而定，跨世界不应复用）
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			sharedSearchCache.clear();
			pendingSearches.clear();
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
				new SearchResultsPayload(payload.category(), payload.targetIndex(), List.of()));
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
		ResourceKey<Level> dim = level.dimension();
		ResourceKey<Structure> structureKey;
		int slices;

		if (category == SearchCategory.VILLAGE) {
			// 村庄仅生成于主世界
			if (!dim.equals(Level.OVERWORLD)) {
				storeCache(player.getUUID(), cacheKey, List.of());
				ServerPlayNetworking.send(player,
					buildResults(player, category, targetIndex, List.of()));
				return;
			}
			structureKey = VillageSearchType.byIndex(targetIndex).structureResourceKey();
			slices = 1;
		} else {
			StructureSearchType type = StructureSearchType.byIndex(targetIndex);
			if (!type.dimension().equals(dim)) {
				storeCache(player.getUUID(), cacheKey, List.of());
				ServerPlayNetworking.send(player,
					buildResults(player, category, targetIndex, List.of()));
				return;
			}
			structureKey = type.structureResourceKey();
			slices = 9; // 中心 + 8 方向
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

	/**
	 * 执行任务的一片：分片 0 为中心 100 区块内最近结构，分片 1..8 为距中心 70 区块处、
	 * 各 30 区块半径的八个方向（合起来覆盖 40~100 区块环带），收集更多实例。
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
			int cx = job.centerX + (int) (Math.cos(angle) * 70 * 16);
			int cz = job.centerZ + (int) (Math.sin(angle) * 70 * 16);
			addStructureHit(job.candidates, job.level, job.set, new BlockPos(cx, job.centerY, cz), 30);
		}
	}

	/** 任务全部分片执行完毕：写本玩家缓存、写共享缓存、发结果。 */
	private static void finalizeSearch(StructureSearchJob job) {
		if (job.player.connection == null || job.player.isRemoved()) {
			return;
		}
		storeCache(job.player.getUUID(), job.cacheKey, job.candidates);
		storeShared(job.level, job.player, job.category, job.targetIndex, job.candidates);
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
		BiomeSearchType type = BiomeSearchType.byIndex(payload.targetIndex());
		// 维度不匹配：直接回空结果（与同步路径行为一致）
		if (!type.dimension().equals(level.dimension())) {
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
			List<int[]> found = collectBiomes(playerPos, type, source, sampler);
			server.execute(() -> {
				if (!server.isRunning() || player.connection == null || player.isRemoved()) {
					return;
				}
				storeCache(playerUuid, cacheKey, found);
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
	private static List<int[]> collectBiomes(BlockPos playerPos, BiomeSearchType type,
			BiomeSource source, Climate.Sampler sampler) {
		ResourceKey<Biome> biomeKey = type.biomeResourceKey();
		int quartY = playerPos.getY() >> 2;
		int px = playerPos.getX();
		int pz = playerPos.getZ();

		List<int[]> candidates = new ArrayList<>();
		// 玩家脚下本身也检查（可能已站在目标群系中）
		if (source.getNoiseBiome(px >> 2, quartY, pz >> 2, sampler).is(biomeKey)) {
			candidates.add(new int[] { px, pz });
		}
		int step = 128;
		for (int r = step; r <= SEARCH_RADIUS_BLOCKS; r += step) {
			// 命中足够多即提前结束，控制耗时
			if (candidates.size() >= SearchResultsPayload.MAX_RESULTS * 4) {
				break;
			}
			int checks = Math.max(8, (int) (2 * Math.PI * r / step));
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
		List<SearchResultsPayload.Entry> entries = new ArrayList<>();
		int n = Math.min(SearchResultsPayload.MAX_RESULTS, sorted.size());
		for (int i = 0; i < n; i++) {
			int[] c = sorted.get(i);
			boolean marked = SearchMarkStore.contains(owner, category, targetIndex, c[0], c[1]);
			entries.add(new SearchResultsPayload.Entry(c[0], c[1], marked));
		}
		return new SearchResultsPayload(category, targetIndex, entries);
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