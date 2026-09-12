package io.github.a10086ovo.doll.network;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.DollRecallRegistry;
import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import io.github.a10086ovo.doll.network.payload.IndexBuildProgressPayload;
import io.github.a10086ovo.doll.network.payload.OpenDollControlPanelPayload;
import io.github.a10086ovo.doll.network.payload.RecallDollPayload;
import io.github.a10086ovo.doll.network.payload.RequestIndexBuildPayload;
import io.github.a10086ovo.doll.network.payload.RequestSearchPayload;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import io.github.a10086ovo.doll.network.payload.SelectDollModePayload;
import io.github.a10086ovo.doll.network.payload.StructureCatalogPayload;
import io.github.a10086ovo.doll.network.payload.ToggleMarkPayload;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import io.github.a10086ovo.doll.util.SearchMarkStore;
import io.github.a10086ovo.doll.geo.GeoIndex;
import io.github.a10086ovo.doll.geo.GeoIndexBuildJob;
import io.github.a10086ovo.doll.geo.GeoIndexService;
import io.github.a10086ovo.doll.geo.StructureGenVerifier;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DollNetworking {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

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

/**
 * 一次搜索最多校验多少个索引候选。校验用的是原版 {@code Structure.generate(...)}（jigsaw 要装配
 * 整个结构），比枚举贵得多，所以必须封顶——否则一个候选全都"不会真生成"的结构（如远古城市）
 * 会白跑很多次。40 这个量级足以在"误报率 2/3"的最坏情况下仍凑出 10 条结果。
 */
private static final int VERIFY_MAX_TRIES = 40;

/**
 * 校验的并发度：每次搜索同时开这么多个"抢单"工作线程。
 *
 * <p><b>为什么必须并发</b>：各类目标的单次生成校验成本差一个数量级——普通结构 2~95ms，
 * 而村庄（jigsaw 要装配整座村庄 + 地表投影）实测 <b>440~550ms</b>（日志：plains 6013ms/11 个候选、
 * desert 4870ms/11 个）。返回 10 条结果至少要校验 11 个候选，<b>串行就是 5~6 秒</b>；
 * 并发抢单后同样 11 个候选只需「轮数 × 单次耗时」≈ 2 × 500ms ≈ 1 秒。
 *
 * <p><b>为什么用"抢单"而不是固定分片</b>：目标是「凑够 {@link SearchResultsPayload#MAX_RESULTS}
 * 就整体收工」。固定分片会让每个线程各自跑完自己那份，把 40 个候选全烧掉；抢单则由共享游标
 * 统一分配，任何线程一发现配额已满就立刻停止抢单。同时也避免"某线程分到的候选恰好全落空"。
 *
 * <p>取值 8：本机 8 物理核 / 16 逻辑核，留出主线程与世界生成的余量。任务池大小＝本值，
 * 故单个请求即可占满；两个请求同时搜索时会自然排队（都只是 ~1 秒，可接受）。
 */
private static final int VERIFY_WORKERS = 8;

/**
 * 结构生成校验的工作线程池。
 *
 * <p><b>为什么可以放工作线程</b>：原版世界生成本身就在工作线程上执行
 * {@code ChunkGenerator.createStructures} —— 即 {@code structure.generate(...)} 天然是
 * 工作线程调用；它只依赖 {@code RandomState}/{@code StructureTemplateManager}/高度范围/注册表，
 * 全是只读或线程安全对象（{@code Structure.GenerationContext} 里连 {@code LevelReader} 都没有）。
 * <b>前提是这些引用必须在主线程先取好</b>（{@code ChunkGeneratorStructureState} 是懒初始化的
 * 可变容器），做法同 {@code startBiomeSearchAsync}。
 *
 * <p><b>并发安全性的关键一环</b>：生成装配要读结构模板，走
 * {@code StructureTemplateManager.structureRepository}，26.2 里它是
 * {@code new ConcurrentHashMap<>()}(见 loom sources) —— 故多个校验线程并发读同一张模板表是安全的，
 * 这也是原版能把结构生成铺到工作线程池的前提。本池只要保证"不嵌套提交"（本类的做法是
 * 由主线程一次性派发所有抢单任务，任务之间互不等待），就不会自锁。
 *
 * <p>放在工作线程而不是主线程时间片推进，是因为后者把延迟摊成了 tick 数：每 tick 2ms 预算、
 * 一次校验就超预算 ⇒ 10 条结果要 10 tick ≈ 0.5 秒（用户实测反馈"不秒出了"）。放工作线程后
 * 延迟≈真实计算耗时（几十毫秒），且不占主线程。
 *
 * <p>用独立池而非复用群系搜索池：校验是 CPU 密集型，不该挤掉群系搜索。
 */
private static final ExecutorService STRUCTURE_VERIFY_EXECUTOR =
	Executors.newFixedThreadPool(VERIFY_WORKERS, r -> {
		Thread t = new Thread(r, "doll-structure-verify");
		t.setDaemon(true);
		return t;
	});

	/**
	 * 正在校验中的「玩家|缓存键」集合（只在主线程读写）。
	 * 作用是防止同一玩家连点搜索时重复提交同一个校验任务——校验很贵，重复提交纯浪费。
	 */
	private static final java.util.Set<String> verifyInFlight = new java.util.HashSet<>();

	/**
	 * 是否正在广播「全域索引」构建进度（true = 有一个手动构建任务在跑）。
	 *
	 * <p>用<b>广播</b>而不是"只发给发起者"：底图索引是服务端全局资源，谁建的、谁看都一样；
	 * 而且发起者一旦中途掉线，单播的目标就永久失效、进度会卡死在"构建中"。
	 */
	private static boolean indexBuilding;
	/** 上一次已回报的百分比 / 阶段：只在变化时发包（每 tick 都发等于刷屏）。 */
	private static int lastIndexPercent = -1;
	private static int lastIndexPhase = -1;

	/**
	 * 进度阶段「已结束」的取值。<b>直接引用</b> {@link GeoIndexBuildJob#PHASE_DONE}（值 4），
	 * 不在此另写一份数字——两处独立常量一旦改一处忘另一处，完成/取消包就会带上错的阶段号。
	 * 其余阶段（0=结构 1=村庄 2=群系 3=村庄预确认）见 {@code GeoIndexBuildJob.PHASE_*}。
	 */
	private static final int INDEX_PHASE_END = GeoIndexBuildJob.PHASE_DONE;

	/** 搜索结果缓存 LRU 上限（玩家数）。超过后淘汰最久未访问的玩家条目。 */
	private static final int CACHE_MAX_PLAYERS = 256;

	/**
	 * 共享缓存的复用窗口：100 区块（1600 格）。
	 *
	 * <p><b>注意：它已不再是「搜索半径」。</b>实际搜索范围由 {@link #ON_DEMAND_RADII}（结构/村庄，
	 * 自适应分档）与 {@link #BIOME_SCAN_BANDS}（群系，最远 16384 格）各自决定。本常量如今只剩两处用途：
	 * ① 推导 {@link #SHARED_REUSE_BLOCKS}；② 复用他人共享缓存时，过滤掉离玩家过远的旧候选。
	 */
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	private static final int SEARCH_RADIUS_BLOCKS = SEARCH_RADIUS_CHUNKS * 16;

	/**
	 * 群系「连通片」相邻判定半径（格）：<b>必须 ≥ 任一采样步长</b>，现取最粗的那个（粗扫网格 640）。
	 *
	 * <p>群系有两套采样：① 预索引的<b>粗扫网格</b>，步长
	 * {@link GeoIndexBuildJob#PREINDEX_BIOME_STEP}（=640）；② 按需螺旋的<b>分档采样</b>
	 * （{@link #BIOME_SCAN_BANDS}：48 / 192 / 640，近细远粗）。相邻判定必须按<b>最粗</b>的那个来：
	 * 取小了 → 远处同一连通片的相邻采样点（间距 192 / 640）会被判成"两片"，同一地点在结果里重复占位；
	 * 取大了 → 会把真正相邻的两个小片并成一个（只少列一个落点，无实质损失）。
	 *
	 * <p>旧实现是「固定最小间距」阈值，且用严格小于判定（{@code d² < 64²}）——而相邻点距离恰好 64，
	 * {@code 64² < 64²} 恒为假 → <b>一个点都删不掉，去重是空操作</b>，这才改成了现在的连通片模型。
	 * 详见 {@link #representativePerBiomePatch(List)}。
	 */
	private static final int BIOME_PATCH_STEP = GeoIndexBuildJob.PREINDEX_BIOME_STEP;

	/** 共享缓存复用判定：玩家与已缓存搜索中心点水平距离不超过此值即可直接复用其候选（零主线程搜索）。 */
	private static final int SHARED_REUSE_BLOCKS = SEARCH_RADIUS_BLOCKS - 100;

	/**
	 * 按需收集的自适应半径档位（方块，由近及远）。
	 *
	 * <p>第一次搜某目标时，以<b>玩家当前位置</b>为中心环形枚举该结构的 placement 候选，
	 * 凑够 {@link #VERIFY_MAX_TRIES} 个即停；不够就放大一档再试。
	 *
	 * <p><b>为什么不一次给个大半径</b>（旧预索引固定给 16000）：枚举成本 ∝ 半径²，而"要的只是
	 * 离你最近的几个"。分档让成本只与<b>你真正需要的距离</b>成正比——多数目标第一档就凑够了。
	 */
	private static final int[] ON_DEMAND_RADII = {2048, 4096, 8192, 16384};

	/** 某一档耗时已达此值就不<b>再放大半径</b>：成本随半径²增长，再放大 4 倍不划算。 */
	private static final long ON_DEMAND_EXPAND_MAX_MS = 50L;

	/** 按需收集的累计时间硬上限（毫秒）：极端地形下（密集+群系受限）兜住主线程占用。 */
	private static final long ON_DEMAND_TOTAL_MAX_MS = 500L;

	/**
	 * 群系螺旋采样的「分档」表：{@code {半径上限（方块）, 采样步长}}，由近及远、近细远粗。
	 *
	 * <p>群系是成片的，远处不需要 48 格精度。用固定步长扫到 16384 需约 44 万次采样
	 *（旧预索引的全球网格正是这个量级，单维度 ~34s）；分档后总采样数约 1.3 万次
	 *（≈1.5s 且在工作线程），近处仍保持 48 格精度不漏小片。
	 */
	private static final int[][] BIOME_SCAN_BANDS = {{2048, 48}, {8192, 192}, {16384, 640}};
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
		PayloadTypeRegistry.serverboundPlay().register(RequestIndexBuildPayload.TYPE, RequestIndexBuildPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDollControlPanelPayload.TYPE, OpenDollControlPanelPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateDollSnapshotPayload.TYPE, UpdateDollSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SearchResultsPayload.TYPE, SearchResultsPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StructureCatalogPayload.TYPE, StructureCatalogPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(IndexBuildProgressPayload.TYPE, IndexBuildProgressPayload.STREAM_CODEC);

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

				// ---- 结果三级：玩家缓存 → 累积索引 → 按需收集 ----
				//
				// 索引已从"开服预建的底图"降级为「随搜索累积的缓存」：每次搜索得到的候选都会并进去
				//（见 finalizeSearch / collectOnDemand），停服落盘，下次开服 loadForDimension 读回。
				// 所以这里对它的态度也变了——命中即代表"这一带曾经搜过"，直接进校验/回放即可。

				// ① 本玩家缓存优先：上次出过的结果直接回放，零开销（只有显式 refresh 才强制重算）
				if (!payload.refresh() && cached != null) {
					ServerPlayNetworking.send(player,
						buildResults(player, payload.category(), payload.targetIndex(), cached));
					return;
				}

				// ② 累积索引命中：结构/村庄要过生成校验；群系没有"生成"这一层（纯噪声采样），直接回放
				//（buildResults 会对群系做「连通片」去重，索引点本身即精确结果）。
				String dimId = serverLevel.dimension().identifier().toString();
				String geoKey = payload.category() + ":" + payload.targetIndex();
				List<int[]> indexed = GeoIndex.query(dimId, geoKey);
				if (!indexed.isEmpty()) {
					if (payload.category() == SearchCategory.BIOME) {
						storeCache(player.getUUID(), cacheKey, indexed);
						ServerPlayNetworking.send(player,
							buildResults(player, payload.category(), payload.targetIndex(), indexed));
						return;
					}
					// 结构/村庄："索引命中"只等于"placement 合法 + 群系合法"，**不等于原版真会在此生成**
					//（原版还要 Structure.generate(...) 成功：jigsaw 得装得下、海底废墟得有海床）。
					// 直接回放会出现"搜得到坐标、飞过去却没有"，故必须过生成校验（工作线程）。
					if (enqueueStructureVerify(serverLevel, player, payload, cacheKey, indexed)) {
						return;   // 已提交后台校验，结果稍后由 finishVerify 送达
					}
				}

				// ③ 群系：索引里没有该目标 → 异步螺旋采样（纯噪声、工作线程）。
				//    ★半径分档由近及远、近细远粗（见 BIOME_SCAN_BANDS）：群系是成片的，远处不需要 48 格精度。
				//    采样结果会 GeoIndex.merge 进累积缓存，同目标下次直接走 ② 秒回。
				if (payload.category() == SearchCategory.BIOME) {
					startBiomeSearchAsync(context.server(), player, serverLevel, payload, cacheKey);
					return;
				}

				// ④ 结构/村庄：**按需收集**——以玩家为中心、半径由近及远分档放大，凑够校验额度即停。
				//    这正是旧"开服预建底图"想办的事，只是改成"你要哪个才算哪个"。
				List<int[]> onDemand = collectOnDemand(serverLevel, player, payload);
				if (!onDemand.isEmpty()) {
					GeoIndex.merge(dimId, geoKey, onDemand);   // 并入累积缓存：下次走 ②，且跨会话保留
					if (enqueueStructureVerify(serverLevel, player, payload, cacheKey, onDemand)) {
						return;
					}
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

				// ⑤ 最后兜底：类型精确的实时定位（分片式，每 tick 有界执行）。
				//    走到这里说明既无缓存、索引也无候选、按需收集也没捞到——多见于"该维度确实没有此结构"
				//    或"混型跳过索引"的目标（下界堡垒/要塞、基础传送门与变体）。
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

		// ---- 全域索引：玩家主动请求为「当前维度」构建底图索引（或取消） ----
		//
		// 这是"要不要花这笔钱"的选择权交还给玩家的唯一入口。不点，就永远维持按需模式
		// （开服不再有任何自动预索引；见 SERVER_STARTED 的说明）。
		// 服务端权威：客户端只是发个请求，身份校验、中心选取、限流与进度都由服务端说了算。
		ServerPlayNetworking.registerGlobalReceiver(RequestIndexBuildPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				// 身份校验：必须是自己持有的向导人偶（防止任意玩家触发或取消别人的构建）
				Entity entity = player.level().getEntity(payload.dollEntityId());
				if (!(entity instanceof DollEntity doll) || !doll.isOwnedBy(player)) {
					return;
				}
				if (doll.getDollVariant() != io.github.a10086ovo.doll.entity.DollVariant.GUIDE) {
					return;
				}
				if (payload.cancel()) {
					boolean was = GeoIndexBuildJob.isActive();
					GeoIndexBuildJob.cancel();
					indexBuilding = false;
					lastIndexPercent = -1;
					lastIndexPhase = -1;
					sendIndexProgressToAll(context.server(),
						new IndexBuildProgressPayload(0, INDEX_PHASE_END, IndexBuildProgressPayload.STATE_CANCELLED));
					if (was) {
						LOGGER.info("DollGeoPreIndex 手动底图构建被 {} 取消（已并入的部分仍然有效）", player.getName().getString());
					}
					return;
				}
				// 以发起者当前位置为中心：结果是按玩家距离排序的，锚在出生点会在玩家走远后失效
				ServerLevel level = (ServerLevel) player.level();
				BlockPos center = player.blockPosition();
				GeoIndexBuildJob.beginForLevel(level, center.getX(), center.getZ());
				indexBuilding = true;
				lastIndexPercent = -1;
				lastIndexPhase = -1;
				sendIndexProgressToAll(context.server(),
					new IndexBuildProgressPayload(0, 0, IndexBuildProgressPayload.STATE_RUNNING));
			});
		});

		// 玩家进服：推送本世界全部结构注册键清单（客户端不加载结构注册表，须凭此构建可搜结构/村庄清单）。
		// 不再有任何"索引预载中/已就绪"聊天提示：索引已改为随搜索累积的缓存，开服瞬间无事发生。
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p == null) {
				return;
			}
			ServerPlayNetworking.send(p, new StructureCatalogPayload(listStructureIds((ServerLevel) p.level())));
			// 若正有构建在跑，顺手同步一次进度：否则他打开搜索屏时按钮会显示"没人在建"，
			// 而服务端其实在跑（进度包只在百分比变化时发，他可能整段都等不到一包）。
			if (indexBuilding && GeoIndexBuildJob.isActive()) {
				GeoIndexBuildJob.Progress pr = GeoIndexBuildJob.progress();
				ServerPlayNetworking.send(p,
					new IndexBuildProgressPayload(pr.percent(), pr.phase(), IndexBuildProgressPayload.STATE_RUNNING));
			}
		});

		// 玩家断线：清理其搜索缓存，防长时运行内存泄漏
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			searchResultCache.remove(id);
		});

		// 服务器启动：清空世界级共享缓存、排队任务，并加载各维度<b>已累积</b>的 GeoIndex 缓存。
		//
		// ★这里刻意不再预建"底图索引"。旧做法在开服瞬间为「全维度 × 半径 16000 × 三种探测高度」
		// 一次性扫完全款：实测新世界 81.4s（群系占 79%、玩家没去过的维度占 38%），期间还会因为
		// 单个结构的枚举不可让出而卡住服务端线程（实测 Can't keep up! 88 ticks behind）。
		// 而玩家绝大多数时候只搜几个目标、且只要"离我最近的几个"——为用不到的全款买单没有道理。
		// 现在索引降级为「随用随长的缓存」：搜索时才以玩家为中心按需收集，并入索引并在停服时落盘。
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			sharedSearchCache.clear();
			pendingSearches.clear();
			verifyInFlight.clear();
			for (ServerLevel lv : server.getAllLevels()) {
				GeoIndex.loadForDimension(server, lv);
			}
		});

		// 服务器停止：统一落盘各维度索引——只在"自上次落盘后确有新增"时才重写，避免关服时冗余写大文件。
		// 索引已从"开服预建的底图"变成"随搜索累积的缓存"，故这一步现在就是<b>缓存的持久化</b>：
		// 下次开服 loadForDimension 读回，同一个目标（含跨会话）即可直接校验/回放，不必重新收集。
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			verifyInFlight.clear();
			int written = 0;
			int skipped = 0;
			for (ServerLevel lv : server.getAllLevels()) {
				if (GeoIndex.hasUnsavedChanges(lv.dimension().identifier().toString())) {
					GeoIndex.saveForDimension(server, lv);
					written++;
				} else {
					skipped++;
				}
			}
			LOGGER.info("GeoIndex 停服落盘：{} 个维度有新增已写入，{} 个维度无变化已跳过", written, skipped);
		});

		// 每 tick：递进结构/村庄搜索分片（主线程有界执行，避免单 tick 瞬时过载）。
		// 开服底图构建已移除（见 SERVER_STARTED 的说明），故此循环现在只服务"实时兜底"这一条路径。
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// ① 手动「全域索引」：每 tick 推进一个时间片，并把进度回报给发起者。
			//    这里是<b>唯一</b>推进底图构建的地方——开服不再自动跑（见 SERVER_STARTED 的说明）。
			if (indexBuilding) {
				if (GeoIndexBuildJob.isActive()) {
					GeoIndexBuildJob.advance(server);
				}
				if (GeoIndexBuildJob.isActive()) {
					sendIndexProgress(server, IndexBuildProgressPayload.STATE_RUNNING);
				} else {
					// 本 tick 内建完：补一条「完成」再收工
					sendIndexProgress(server, IndexBuildProgressPayload.STATE_DONE);
					indexBuilding = false;
					lastIndexPercent = -1;
					lastIndexPhase = -1;
				}
			}

			// ② 实时兜底：结构/村庄搜索分片（主线程有界执行，避免单 tick 瞬时过载）。
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

	/**
	 * 向所有玩家回报「全域索引」进度；<b>只在百分比或阶段变化时发包</b>（否则每 tick 一包等于刷屏）。
	 *
	 * @param state 见 {@link IndexBuildProgressPayload} 的 {@code STATE_*} 常量
	 */
	private static void sendIndexProgress(MinecraftServer server, int state) {
		IndexBuildProgressPayload payload;
		if (state == IndexBuildProgressPayload.STATE_RUNNING) {
			GeoIndexBuildJob.Progress pr = GeoIndexBuildJob.progress();
			if (pr.percent() == lastIndexPercent && pr.phase() == lastIndexPhase) {
				return;
			}
			lastIndexPercent = pr.percent();
			lastIndexPhase = pr.phase();
			payload = new IndexBuildProgressPayload(pr.percent(), pr.phase(), state);
		} else {
			payload = new IndexBuildProgressPayload(100, INDEX_PHASE_END, state);
		}
		sendIndexProgressToAll(server, payload);
	}

	/** 把进度包发给当前在线的所有玩家（客户端只在搜索屏打开时才使用它，其余情况直接丢弃）。 */
	private static void sendIndexProgressToAll(MinecraftServer server, IndexBuildProgressPayload payload) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/**
	 * 按需收集某结构/村庄的候选坐标（替代旧"开服预建底图"）。
	 *
	 * <p>以玩家当前位置为中心，按 {@link #ON_DEMAND_RADII} 由近及远逐档调用
	 * {@link GeoIndexService#collectNearest}（内含 placement 枚举 + 群系级校验，环形由近及远），
	 * 累计到 {@link #VERIFY_MAX_TRIES} 个候选、或该档太贵、或累计超预算就停。
	 *
	 * <p><b>只在服务端主线程调用</b>：{@code collectNearest} 内部读 {@code ChunkGeneratorStructureState}
	 *（懒初始化容器）。单档成本随半径²增长，故用"上一档耗时 ≥
	 * {@link #ON_DEMAND_EXPAND_MAX_MS} 就不再放大"这条自适应规则兜住最坏情况：
	 * 密集结构（spacing 小 → 枚举量大）在第一档就能凑够，根本不会走到大半径档。
	 *
	 * @return 空 = 该维度无此结构 / 属"混型跳过" / 各档都无候选；调用方继续走兜底链路
	 */
	private static List<int[]> collectOnDemand(ServerLevel level, ServerPlayer player, RequestSearchPayload payload) {
		boolean villagesOnly = payload.category() == SearchCategory.VILLAGE;
		ResourceKey<Structure> key = resolveStructureKey(level, payload.targetIndex(), villagesOnly);
		if (key == null) {
			return List.of();
		}
		BlockPos pos = player.blockPosition();
		List<int[]> out = new ArrayList<>();
		long t0 = System.nanoTime();
		long totalMs = 0L;
		for (int radius : ON_DEMAND_RADII) {
			long sliceStart = System.nanoTime();
			List<int[]> found = GeoIndexService.collectNearest(
				level, key, pos.getX(), pos.getZ(), radius, VERIFY_MAX_TRIES);
			long sliceMs = (System.nanoTime() - sliceStart) / 1_000_000L;
			totalMs += sliceMs;
			for (int[] c : found) {
				if (out.size() >= VERIFY_MAX_TRIES) {
					break;
				}
				if (!nearExisting(out, c[0], c[1])) {
					out.add(c);
				}
			}
			if (out.size() >= VERIFY_MAX_TRIES) {
				break;
			}
			// 自适应刹车：这一档已经这么贵，半径再翻倍成本约 ×4，不值得（密集结构本来就已在近处凑够）
			if (sliceMs >= ON_DEMAND_EXPAND_MAX_MS || totalMs >= ON_DEMAND_TOTAL_MAX_MS) {
				break;
			}
		}
		if (out.isEmpty()) {
			return List.of();
		}
		out.sort((a, b) -> Long.compare(horizDistSq(pos, a[0], a[1]), horizDistSq(pos, b[0], b[1])));
		LOGGER.info("DollSearch 按需收集 {}/{}@{}：{} 个候选（{} ms）",
			payload.category(), payload.targetIndex(), level.dimension().identifier(),
			out.size(), (System.nanoTime() - t0) / 1_000_000L);
		return out;
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
	 * 该结构是否为当前维度可能生成（委托 GeoIndex 侧，避免两处各写一份结构集反查）。
	 * 只在服务端主线程调用（读取 GeneratorState 需主线程安全情境）。
	 */
		private static boolean structureExistsInDim(ServerLevel level, ResourceKey<Structure> key) {
			return GeoIndexService.structurePossibleInDimension(level, key);
		}

		/** 该群系是否为当前维度可能生成（委托 GeoIndex 侧）。只在服务端主线程调用。 */
		private static boolean biomeExistsInDim(ServerLevel level, ResourceKey<Biome> key) {
			return GeoIndexService.biomePossibleInDimension(level, key);
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
	 * 群系：以玩家为中心螺旋向外采样，收集命中点。
	 * <p>
	 * 性能关键：通过 {@code BiomeSource.getNoiseBiome(四分位坐标, Climate.Sampler)} 做纯噪声计算，
	 * 与区块生成用的是同一套噪声，结果一致但<b>全程不加载、不生成区块</b>。
	 * 本方法只读传入的 source/sampler（无 level 依赖），可在工作线程安全执行。
	 *
	 * <p><b>采样高度</b>：旧实现只探玩家所在高度，玩家在地表时洞穴类群系（繁茂洞穴、滴水石洞穴、
	 * 深暗之域）永远搜不到。现统一走 {@link GeoIndexService#probeMatchY} 的多档探测（地表/地下中层/深层），
	 * 任一档命中即算命中；具体命中的 Y 由服务端在拼结果包时回填展示。
	 *
	 * <p><b>半径分档（近细远粗）</b>：见 {@link #BIOME_SCAN_BANDS}。旧实现固定只扫到
	 * {@code SEARCH_RADIUS_BLOCKS}(1600)，最近的群系片一旦在更远处就报"未找到"；分档后可扫到 16384 格
	 * 而总采样数仍在万级（纯噪声、工作线程，开销可忽略）。
	 */
	private static List<int[]> collectBiomes(BlockPos playerPos, ResourceKey<Biome> biomeKey,
			BiomeSource source, Climate.Sampler sampler) {
		int px = playerPos.getX();
		int pz = playerPos.getZ();

		List<int[]> candidates = new ArrayList<>();
		// 玩家脚下本身也检查（可能已站在目标群系中）
		if (matchesBiome(source, sampler, px, pz, biomeKey)) {
			candidates.add(new int[] { px, pz });
		}
		// 命中足够多即提前结束，控制耗时（连通片去重后要凑够 MAX_RESULTS 片，故留 4 倍余量）
		final int enough = SearchResultsPayload.MAX_RESULTS * 4;
		int inner = 0;
		for (int[] band : BIOME_SCAN_BANDS) {
			int outer = band[0];
			int step = band[1];
			boolean stop = false;
			for (int r = inner + step; r <= outer; r += step) {
				if (candidates.size() >= enough) {
					stop = true;
					break;
				}
				// 每环采样数按周长定，下限 12 保证小半径环也有足够的角向覆盖
				int checks = Math.max(12, (int) (2 * Math.PI * r / step));
				for (int i = 0; i < checks; i++) {
					double angle = 2 * Math.PI * i / checks;
					int x = px + (int) (Math.cos(angle) * r);
					int z = pz + (int) (Math.sin(angle) * r);
					if (matchesBiome(source, sampler, x, z, biomeKey)
						&& !nearExisting(candidates, x, z)) {
						candidates.add(new int[] { x, z });
					}
				}
			}
			if (stop) {
				break;
			}
			inner = outer;
		}
		return candidates;
	}

	/** 该 (x,z) 列在任一探测高度是否属于目标群系（多档采样口径，见 {@link GeoIndexService}）。 */
	private static boolean matchesBiome(BiomeSource source, Climate.Sampler sampler,
			int x, int z, ResourceKey<Biome> biomeKey) {
		return GeoIndexService.probeMatchY(source, sampler, x, z, biomeKey) != GeoIndexService.NO_Y;
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

	/**
	 * 抢下一个候选序号；返回 {@code -1} 表示不必再抢（已凑够，或候选已用尽）。
	 *
	 * <p><b>上限为什么是「MAX_RESULTS + failed」而不是「看 passed 到没到 MAX_RESULTS」</b>：
	 * {@code passed} 只在候选装配<b>完成</b>后才自增，而装配很慢（村庄 440~550ms）——在这个窗口里
	 * 8 个工作线程会各自认为"还不够"，于是一口气把 17~20 个候选都领走。实测日志：
	 * {@code 17 / 已试 17}、{@code 17 / 已试 20}——**比需要的多做约 70% 的村庄装配**，
	 * 直接把等待从 2 轮拖成 3 轮（1661ms → 差一轮 ≈ 550ms）。
	 *
	 * <p>改用"领号即计数"的 CAS：<b>在飞候选也算作已领</b>，于是最多只领
	 * {@code MAX_RESULTS + failed} 个；每失败一个就把名额退还一个，所以低通过率（普通结构
	 * 实测 13/40）不会因此少给结果，仍然会一路试到 {@code limit}。
	 *
	 * <p><b>不会空转卡住</b>：某线程拿到 -1 返回，只可能发生在"此刻 passed + 在飞 ≥ MAX_RESULTS"时；
	 * 而此刻必然还有线程在装配中，它装配完会重新领号——若失败，名额被退还，它就能接着领。
	 */
	private static int claimNext(AtomicInteger cursor, AtomicInteger failed, int limit) {
		while (true) {
			int c = cursor.get();
			if (c >= limit || c >= SearchResultsPayload.MAX_RESULTS + failed.get()) {
				return -1;
			}
			if (cursor.compareAndSet(c, c + 1)) {
				return c;
			}
		}
	}

	/**
	 * 索引命中后转入「生成校验」环节（替代原先直接回放索引结果）。
	 *
	 * <p>索引里的点是"placement 合法 + 群系合法"的候选；原版还要求 {@code Structure.generate(...)}
	 * 成功（jigsaw 得装得下、海底废墟得有海床），否则直接回放会出现"搜得到坐标、飞过去却没有"。
	 * 这里把候选按距玩家由近及远排序后，交给 {@link #VERIFY_WORKERS} 个线程<b>抢单并发</b>校验，
	 * 凑够 {@link SearchResultsPayload#MAX_RESULTS} 条就整体收工，完成后回主线程发包。
	 *
	 * <p><b>已确认点免检</b>：建索引时已把每类村庄最近的若干个装配确认过
	 *（见 {@link GeoIndex#confirmedKey}），这些点直接判为通过、不再做那 440~550ms 的装配——
	 * 这正是"刚建完索引就搜村庄可以零等待"的实现方式。
	 *
	 * <p>用"逐点免检"而不是"整条快速通道"，是为了不依赖"玩家还在建索引中心附近"这个前提：
	 * 候选仍然按<b>玩家</b>距离挑选，其中已确认的免检、其余照常校验；玩家走远后只是退化成原来的
	 * 1~2 秒，而不会给出"离得远的已确认村庄"、却漏掉脚下的真村庄。
	 *
	 * @return true = 已接管本次请求（已提交校验，或该玩家该目标已有校验在跑，结果会由那次送达）；
	 *         false = 未接管，调用方回放索引（结构键/判定环境拿不到等边缘情况）
	 */
	private static boolean enqueueStructureVerify(ServerLevel level, ServerPlayer player,
			RequestSearchPayload payload, String cacheKey, List<int[]> indexed) {
		if (indexed.isEmpty()) {
			return false;   // 无候选可校验：直接交回调用方
		}
		String inFlightKey = player.getUUID() + "|" + cacheKey;
		if (!verifyInFlight.add(inFlightKey)) {
			return true;   // 同玩家同目标已在校验中：不重复提交（防连点重复烧 CPU），结果会由那次送达
		}
		boolean villagesOnly = payload.category() == SearchCategory.VILLAGE;
		ResourceKey<Structure> key = resolveStructureKey(level, payload.targetIndex(), villagesOnly);
		Holder.Reference<Structure> holder = key == null ? null
			: level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key).orElse(null);
		// ★全部引用必须在主线程取好（GeneratorState 是懒初始化的可变容器），再交工作线程只读使用
		StructureGenVerifier.Env env = holder == null ? null : StructureGenVerifier.create(level);
		if (holder == null || env == null) {
			verifyInFlight.remove(inFlightKey);
			return false;   // 拿不到判定环境：交回调用方直接回放索引
		}
		// 已确认点坐标集合（建索引时装配确认过）：空集 = 没有预确认数据，行为与没有本机制时一致。
		final java.util.Set<Long> confirmed = confirmedSet(level, payload.category(), payload.targetIndex());
		MinecraftServer server = level.getServer();
		UUID playerId = player.getUUID();
		List<int[]> sorted = new ArrayList<>(indexed);
		BlockPos p = player.blockPosition();
		sorted.sort((a, b) -> Long.compare(horizDistSq(p, a[0], a[1]), horizDistSq(p, b[0], b[1])));
		// ★抢单并发：所有工作线程共享一个游标，各自取下一个候选来校验。
		//   为什么不让主线程阻塞等待：主线程每 tick 只有 50ms，等 1 秒等于卡服；
		//   这里改成「最后一个收工的工作线程负责回主线程交付」，主线程全程不阻塞。
		//   pass[] 用候选位次做下标（而不是往共享 List 里 add），这样最终结果天然保持
		//   sorted 的"由近及远"顺序、无需再排一次；并发写的下标互不相同，无竞争。
		//   ⚠ 领号上限必须走 claimNext（见其 javadoc）：只看 passed 会让 8 个线程各多领一轮，
		//     实测多装配 70% 的村庄（日志 17 / 已试 17），白白拖长一个轮次。
		final int limit = Math.min(sorted.size(), VERIFY_MAX_TRIES);
		final AtomicInteger cursor = new AtomicInteger();
		final AtomicInteger passed = new AtomicInteger();
		final AtomicInteger failed = new AtomicInteger();
		final boolean[] pass = new boolean[limit];
		final AtomicInteger remaining = new AtomicInteger(VERIFY_WORKERS);
		final long t0 = System.nanoTime();
		for (int w = 0; w < VERIFY_WORKERS; w++) {
			STRUCTURE_VERIFY_EXECUTOR.submit(() -> {
				try {
					while (true) {
						int i = claimNext(cursor, failed, limit);
						if (i < 0) {
							return;   // 已凑够，或候选已用尽
						}
						int[] c = sorted.get(i);
						// 候选方块坐标 → 区块：getLocatePos = 区块角 + locateOffset，而 locateOffset < 16
						//（见 StructureGenVerifier.reallyGenerates 的偏移说明），故 >> 4 可唯一反推。
						// 已确认点直接放行（省下 440~550ms 的村庄装配）。
						if (confirmed.contains(packXZ(c[0], c[1]))
							|| StructureGenVerifier.reallyGenerates(env, holder, c[0] >> 4, c[1] >> 4)) {
							pass[i] = true;
							passed.incrementAndGet();
						} else {
							failed.incrementAndGet();
						}
					}
				} finally {
					// remaining 的递减发生在所有 pass[] 写入之后（AtomicInteger 的易失语义给出
					// happens-before），故归零者读到的 pass[] 必定是全部线程写完的最终结果
					if (remaining.decrementAndGet() == 0) {
						long ms = (System.nanoTime() - t0) / 1_000_000L;
						int triedCount = Math.min(cursor.get(), limit);
						List<int[]> verified = new ArrayList<>();
						for (int i = 0; i < limit; i++) {
							if (pass[i] && !nearExisting(verified, sorted.get(i)[0], sorted.get(i)[1])) {
								verified.add(sorted.get(i));
							}
						}
						server.execute(() -> finishVerify(server, playerId, payload, cacheKey, inFlightKey,
							level, verified, triedCount, ms));
					}
				}
			});
		}
		return true;
	}

	/**
	 * 取某目标的「已确认点」坐标集合（打包成 long，便于工作线程 O(1) 命中）。
	 *
	 * <p>返回空集表示没有预确认数据——此时搜索行为与引入本机制之前完全一致（全部走生成校验）。
	 */
	private static java.util.Set<Long> confirmedSet(ServerLevel level, int category, int targetIndex) {
		List<int[]> pts = GeoIndex.querySnapshot(level.dimension().identifier().toString(),
			GeoIndex.confirmedKey(category, targetIndex));
		if (pts.isEmpty()) {
			return java.util.Set.of();
		}
		java.util.Set<Long> out = new java.util.HashSet<>(Math.max(16, pts.size() * 2));
		for (int[] p : pts) {
			out.add(packXZ(p[0], p[1]));
		}
		return out;
	}

	/**
	 * 校验任务收尾（由工作线程 {@code server.execute(...)} 回到主线程执行）。
	 *
	 * <p>有通过的：写缓存（本玩家 + 世界级共享）并发包；一个都没通过：说明索引候选对本次请求
	 * 已无参考价值，回落到类型精确的实时定位兜底——宁可慢也不能返回"飞过去什么都没有"的坐标。
	 *
	 * <p>玩家在校验期间离线时直接丢弃结果（不发包、不写缓存）。跨线程只能带 {@code UUID}/{@code ServerLevel}
	 * 这类值对象，<b>不能</b>持有 {@code ServerPlayer} 本身——否则拿到的可能是已卸载的实体。
	 * 同理，若玩家在校验期间换了维度，旧维度的坐标已无意义，按当前维度重新入队一次。
	 */
	private static void finishVerify(MinecraftServer server, UUID playerId, RequestSearchPayload payload,
			String cacheKey, String inFlightKey, ServerLevel level, List<int[]> verified, int tried, long ms) {
		verifyInFlight.remove(inFlightKey);
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		int category = payload.category();
		int targetIndex = payload.targetIndex();
		if (player == null || player.connection == null || player.isRemoved()) {
			return;
		}
		if (player.level() != level) {
			LOGGER.info("DollSearch 生成校验期间玩家换了维度 {}/{}：按当前维度重新入队",
				category, targetIndex);
			enqueueStructureSearch((ServerLevel) player.level(), player, payload, cacheKey);
			return;
		}
		if (verified.isEmpty()) {
			LOGGER.info("DollSearch 生成校验全部落空 {}/{}@{}（已试 {} 个候选，{} ms），回落实时定位",
				category, targetIndex, level.dimension().identifier(), tried, ms);
			enqueueStructureSearch(level, player, payload, cacheKey);
			return;
		}
		LOGGER.info("DollSearch 生成校验完成 {}/{}@{}：{} / 已试 {} 个候选通过（{} ms）",
			category, targetIndex, level.dimension().identifier(), verified.size(), tried, ms);
		// 村庄：把这次装配确认过的点顺路并进「已确认」桶——"过一遍生成校验"与"建索引时的预确认"
		// 是同一件事，故这次校验的结果下次可直接复用（含换玩家、跨会话）。只对村庄做：普通结构单次
		// 校验只有 2~95ms，为它存一份已确认点的收益抵不上索引文件的体积增长。
		if (category == SearchCategory.VILLAGE) {
			GeoIndex.merge(level.dimension().identifier().toString(),
				GeoIndex.confirmedKey(category, targetIndex), verified);
		}
		storeCache(playerId, cacheKey, verified);
		storeShared(level, player, category, targetIndex, verified);
		ServerPlayNetworking.send(player, buildResults(player, category, targetIndex, verified));
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
		// 做「连通片」聚合（仅对 BIOME），使同一片群系只留一个离玩家最近的代表点。
		// 在 buildResults 统一处理，保证「索引回放」与「现算 fallback」两条路径结果一致；
		// 结构/村庄分类不受影响（每处结构本就彼此相距甚远，保持原样）。
		if (category == SearchCategory.BIOME) {
			sorted = representativePerBiomePatch(sorted);
		}
		List<SearchResultsPayload.Entry> entries = new ArrayList<>();
		ServerLevel sl = (ServerLevel) player.level();
		// 群系结果回填「命中的探测高度 Y」：与索引/实时搜索的多档采样同口径。
		// 只对最终 ≤MAX_RESULTS 条各探一次（≤30 次噪声采样，开销可忽略）。
		// 结构/村庄的索引只存 (x,z)，没有可靠 Y，故留哨兵值（客户端不显示 Y）。
		BiomeSource src = null;
		Climate.Sampler samp = null;
		ResourceKey<Biome> biomeKey = null;
		if (category == SearchCategory.BIOME) {
			try {
				src = sl.getChunkSource().getGenerator().getBiomeSource();
				samp = sl.getChunkSource().getGeneratorState().randomState().sampler();
				biomeKey = resolveBiomeKey(sl, targetIndex);
			} catch (Throwable t) {
				src = null;
				samp = null;
				biomeKey = null;
			}
		}
		int n = Math.min(SearchResultsPayload.MAX_RESULTS, sorted.size());
		for (int i = 0; i < n; i++) {
			int[] c = sorted.get(i);
			boolean marked = SearchMarkStore.contains(owner, category, targetIndex, c[0], c[1]);
			int y = (src != null && samp != null && biomeKey != null)
				? GeoIndexService.probeMatchY(src, samp, c[0], c[1], biomeKey)
				: GeoIndexService.NO_Y;
			entries.add(new SearchResultsPayload.Entry(c[0], c[1], y, marked));
		}
		// buildResults 的全部调用路径均位于服务端主线程（见各调用点），
		// 故可安全地在此按 category+targetIndex 重解析 key 并判定「该维度是否可能生成」。
		boolean notInDimension = computeNotInDimension(sl, category, targetIndex);
		return new SearchResultsPayload(category, targetIndex, entries, notInDimension);
	}

	/**
	 * 群系结果去重：按「连通片」聚合，每片只保留一个离玩家最近的代表点。
	 *
	 * <p><b>为什么不用固定间距阈值</b>：群系片的大小差异极大，实测恶地 70 片、片大小 1~150 个采样格点，
	 * 片与片的间隔也不固定。于是固定阈值两头都会错——取小 → 同一片里重复列出多条；取大 → 把离玩家
	 * 更近的孤立小片挤掉。实测 8 个群系：固定阈值只能覆盖 1~9/10 个不同的片，且随群系而变、不可预测。
	 *
	 * <p><b>连通片模型</b>不设距离阈值，只问「两个采样点是否相邻」：以格子边长 {@link #BIOME_PATCH_STEP}
	 * 做空间哈希，切比雪夫距离 ≤ 一个步长即视为同一片（单链聚合，8 邻接）。这样小片（哪怕只有 1 个
	 * 采样点）必定有自己的席位，大片也必定只出一个代表；同一批数据 8 个群系实测全部 10/10。
	 *
	 * <p>入参已按与玩家距离升序排好，故「先遇到的未访问点」必是该片离玩家最近的点，直接取作代表即可。
	 * 复杂度 O(n)（n = 该目标的候选点数，实测最大约 5000），服务端主线程开销可忽略。
	 *
	 * @param sortedByDistance 已按与玩家水平距离升序排好的候选坐标（元素为 {@code {x, z}}，只读）
	 * @return 每个连通片各一个代表点，按距离升序；最多 {@link SearchResultsPayload#MAX_RESULTS} 个
	 */
	private static List<int[]> representativePerBiomePatch(List<int[]> sortedByDistance) {
		int n = sortedByDistance.size();
		Map<Long, List<int[]>> cells = new java.util.HashMap<>(Math.max(16, n));
		for (int[] c : sortedByDistance) {
			cells.computeIfAbsent(packXZ(Math.floorDiv(c[0], BIOME_PATCH_STEP), Math.floorDiv(c[1], BIOME_PATCH_STEP)),
				k -> new ArrayList<>(2)).add(c);
		}
		java.util.Set<Long> visited = new java.util.HashSet<>(Math.max(16, n * 2));
		List<int[]> reps = new ArrayList<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		for (int[] seed : sortedByDistance) {
			if (!visited.add(packXZ(seed[0], seed[1]))) {
				continue;                        // 已属于前面某一片
			}
			reps.add(seed);                      // 升序遍历 → 该片离玩家最近的点
			if (reps.size() >= SearchResultsPayload.MAX_RESULTS) {
				break;
			}
			// 洪泛标记整片，避免同片的其他采样点再占名额
			queue.clear();
			queue.add(seed);
			while (!queue.isEmpty()) {
				int[] cur = queue.poll();
				int cx = Math.floorDiv(cur[0], BIOME_PATCH_STEP);
				int cz = Math.floorDiv(cur[1], BIOME_PATCH_STEP);
				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						List<int[]> bucket = cells.get(packXZ(cx + ox, cz + oz));
						if (bucket == null) {
							continue;
						}
						for (int[] nb : bucket) {
							// 相邻格的两个点最远可差近 2 个步长（跨格对角），必须再验一次真实距离
							if (Math.abs(nb[0] - cur[0]) > BIOME_PATCH_STEP
								|| Math.abs(nb[1] - cur[1]) > BIOME_PATCH_STEP) {
								continue;
							}
							if (visited.add(packXZ(nb[0], nb[1]))) {
								queue.add(nb);
							}
						}
					}
				}
			}
		}
		return reps;
	}

	/** 把两个 int 打包成一个 long 作哈希键（高 32 位存 x、低 32 位存 z）；坐标均在 int 范围内。 */
	private static long packXZ(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
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