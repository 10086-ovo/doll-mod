package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.network.SearchCategory;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GeoIndex 底图（L1）构建任务：<b>服务端主线程 + 每 tick 时间片推进</b>，可断点续跑。
 *
 * <p><b>为什么这么改</b>：旧实现在一条守护线程里把整个维度一次性算完——结构侧是满网格枚举
 * （单维度约 1 亿次判定）、群系侧是 44 万次噪声采样，全程无预算约束，把 CPU 打满；
 * 实测单维度 35~44 秒、三维合计约 119 秒，同期客户端 tick 峰值达 25916 ms（整屏假死）。
 * 另外它还在非主线程使用 {@code ChunkGeneratorStructureState} 等非线程安全容器，与
 * {@link GeoIndexService} 自己声明的"仅主线程调用"相矛盾。
 *
 * <p>现按终稿 D9「主线程口径 = 可控而非零」落地：
 * <ul>
 *   <li>改由 {@code ServerTickEvents.END_SERVER_TICK} 驱动，每 tick 只花
 *       {@link #BUDGET_NANOS} 纳秒（默认 10ms）推进；</li>
 *   <li>结构与村庄逐个收集（单个约 3~10ms），群系按网格点逐点采样，
 *       预算耗尽即让出主线程，下个 tick 从游标处继续；</li>
 *   <li>顺序上先结构/村庄、后群系——前者几乎瞬间完成且查询价值最高，底图立刻可用；</li>
 *   <li>最后是「村庄预确认」阶段（{@link #VILLAGE_PRECONFIRM_TRIES}）：把每类村庄最近的若干个
 *       <b>真正装配确认</b>一遍并存进「已确认」桶。这一段不在主线程做——它由工作线程池
 *       {@link #CONFIRM_EXECUTOR} 承担，主线程每 tick 只查一次"收尾了没有"（见 {@link #stepConfirm}），
 *       故既不卡服、进度条也能如实走完；</li>
 *   <li>每个维度完成后立即落盘（D5），重启即读回，不必重算。</li>
 * </ul>
 *
 * <p><b>日志口径</b>：{@code 耗时} 记的是<b>该维度自己</b>的时长（从它成为当前任务起算），
 * 不是从开服起算；早期版本误用 Job 构造时刻，导致第二、三个维度的耗时成了累计值。
 * 同时分别记录「结构/村庄阶段」与「群系阶段」的耗时，便于定位热点。
 *
 * <p>线程：所有状态只在服务端主线程读写（{@code begin}/{@code advance}/{@code cancel}
 * 均由主线程事件调用），无需额外加锁。
 */
public final class GeoIndexBuildJob {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	/** 底图半径（方块）：以各维度出生点为中心预索引该范围内的结构/村庄/群系。 */
	public static final int PREINDEX_RADIUS_BLOCKS = 16000;

	/** 结构/村庄每类最多收录多少个「最近」候选（环形由近及远，只漏远处不漏近处）。 */
	public static final int PREINDEX_PER_CATEGORY_MAX = 2000;

	/**
	 * 群系网格采样步长（方块）——<b>只做「粗地图」</b>，近处精度由「按需螺旋」负责。
	 *
	 * <p>历史：48 → 64。64 在 16000 半径下仍是 {@code (2*250+1)^2 ≈ 25.1 万} 次采样/维度，
	 * 实测主世界群系阶段 34.2s、末地 28.5s，占整个预索引 81.4s 的 <b>79%</b>。
	 * 而这份细网格其实是<b>纯重复劳动</b>：群系搜索走 {@code BiomeSource.getNoiseBiome} 纯噪声采样
	 * （不加载、不生成区块），「按需螺旋」本就能在玩家附近以 48 格精度精确求解并写进累积缓存
	 * （见 {@code DollNetworking.collectBiomes}），不需要先把全世界算一遍。
	 *
	 * <p>现取 <b>640</b>：采样数降到 {@code (2*25+1)^2 = 2601}（约 1/96），耗时约 1 秒，
	 * 用途只剩「给玩家一张全球哪些地方有这种群系的粗地图」；要精确落点由按需螺旋做细。
	 *
	 * <p><b>与展示层的耦合</b>：这是最粗的采样步长，故 {@code DollNetworking.BIOME_PATCH_STEP}
	 * （连通片去重的格子边长）必须 ≥ 它。改这个值必须同时看那边，否则同一片会被切成多片、结果重复。
	 */
	public static final int PREINDEX_BIOME_STEP = 640;

	/** 群系粗网格的采样点总数 {@code (2*(半径/步长)+1)^2}——进度百分比的分母之一。 */
	private static final int BIOME_GRID_POINTS = biomeGridPoints();

	private static int biomeGridPoints() {
		int w = 2 * (PREINDEX_RADIUS_BLOCKS / PREINDEX_BIOME_STEP) + 1;
		return w * w;
	}

	/**
	 * 进度权重的等效换算：<b>1 个结构/村庄分类 ≈ 多少个群系采样点</b>（按实测耗时折算）。
	 *
	 * <p><b>为什么需要它</b>：若结构/村庄「每个分类只算 1 个单位」，30 个分类就只占进度条
	 * {@code 30/(30+2601)} ≈ 1.1%，而它实际吃掉 96.6% 的耗时（主世界实测：结构/村庄阶段
	 * 4687ms vs 群系阶段 165ms）→ 进度条会卡在 1% 上整整 5 秒、然后一瞬冲到 100%，等于没有。
	 * 这是 {@link #PREINDEX_BIOME_STEP} 从 64 粗化到 640 之后才暴露的：群系阶段便宜了约 100 倍，
	 * 旧的权重却还按老的采样点数在算。
	 *
	 * <p><b>取值依据</b>（主世界实测，见维度完成日志的「结构/村庄阶段 … ms + 群系阶段 … ms」）：
	 * 30 个分类共 4687ms ⇒ 单分类 ≈ 156ms；2601 个采样点共 165ms ⇒ 单点 ≈ 0.063ms；比值 ≈ 2470。
	 * 按此折算，结构/村庄阶段占进度条 {@code 30×2470/(30×2470+2601)} ≈ 96.6%，与实测耗时占比吻合。
	 *
	 * <p><b>它只是显示层的平滑参数</b>：取值偏差只影响进度条走得匀不匀，不影响任何功能结果。
	 * 换机器、换世界后若结构阶段明显变快变慢，按上面同一口径重新量一遍再改这个数即可。
	 */
	private static final int PROGRESS_BIOME_POINTS_PER_STRUCTURE = 2470;

	/**
	 * 村庄预确认：每类村庄把<b>离建索引中心最近的</b>最多多少个候选装配确认为"真实存在"。
	 *
	 * <p><b>为什么要这一步</b>：索引里存的只是"placement 合法 + 群系合法"的候选，不等于原版真会
	 * 在此生成。村庄的生成校验（jigsaw 装配整座村庄 + 地表投影）实测 <b>440~550ms/次</b>，比普通结构
	 * （2~95ms）贵一个数量级——所以哪怕 8 线程并发，点开村庄仍要等 1~2 秒（本功能的由来）。
	 * 在建索引时先把最近的若干个确认好，之后搜索<b>连一次装配都不用做</b>
	 *（见 {@code DollNetworking.enqueueStructureVerify} 的"已确认点免检"）。
	 *
	 * <p>取 16：搜索最多返回 {@code SearchResultsPayload.MAX_RESULTS}(10) 条，留 6 个余量
	 *（村庄通过率很高，少数候选可能因地形装不下），保证"离中心最近的 10 座村庄"都落在已确认集合里。
	 *
	 * <p><b>覆盖范围只有"离中心最近的一批"</b>：玩家走远后命中不到已确认点，会自然回落常规校验路径，
	 * 因此不会给出"远处的已确认村庄"却漏掉脚下的真村庄。
	 */
	public static final int VILLAGE_PRECONFIRM_TRIES = 16;

	/**
	 * 预确认的工作线程数。
	 *
	 * <p>与 {@code DollNetworking} 搜索时的校验池<b>刻意分开</b>：两者可能真的有活同时跑——玩家常在
	 * "刚建完索引"就去搜村庄，而那时预确认往往还没结束。共用一个池会让这次搜索排在最多上百个预确认
	 * 任务后面（多等好几秒），正好把本功能的意义抵消掉。两个池各 8 线程、只在短时突发时同时占用。
	 */
	private static final int CONFIRM_WORKERS = 8;

	/**
	 * 进度权重的等效换算：<b>1 个待确认候选 ≈ 多少个群系采样点</b>（按实测耗时折算）。
	 *
	 * <p>村庄装配单次约 550ms，但由 {@link #CONFIRM_WORKERS} 个线程并行 ⇒ 摊到墙钟约 69ms/个；
	 * 而 1 个群系采样点约 0.063ms（见 {@link #PROGRESS_BIOME_POINTS_PER_STRUCTURE} 的取值依据），
	 * 故比值 ≈ 69 / 0.063 ≈ 1090。不折算就会重演"进度条卡在 1% 然后一瞬冲到头"。
	 */
	private static final int PROGRESS_BIOME_POINTS_PER_CONFIRM_ITEM = 1090;

	/**
	 * 村庄预确认的工作线程池（守护线程）。任务都在 {@code GeoIndexBuildJob} 主线程阶段外执行，
	 * 主线程只轮询收尾标志，故不影响 TPS。详见 {@link #CONFIRM_WORKERS}。
	 */
	private static final ExecutorService CONFIRM_EXECUTOR =
		Executors.newFixedThreadPool(CONFIRM_WORKERS, r -> {
			Thread t = new Thread(r, "doll-village-preconfirm");
			t.setDaemon(true);
			return t;
		});

	/**
	 * 每 tick 允许占用的主线程时间预算（纳秒）——<b>这是唯一的调优旋钮</b>。
	 *
	 * <p>墙钟 ≈ 真实工作量 ÷ (预算 / 50ms)。最初取 2ms 只等于 4% 占空比，
	 * 实测三维总共约 5.2s 的工作量被拉长成 129s（×25），是要治的病本身。
	 * 取 10ms（20% 占空比）后同样工作量约 26s，且一个 tick 仍留 40ms 给原版逻辑，
	 * 正常世界下 TPS 不受影响。想更快就继续调大，但要留意下方两处让出粒度：
	 * 结构阶段是"每个结构让出一次"（单个结构约 3~10ms），群系阶段每
	 * {@link #BIOME_POINTS_PER_BUDGET_CHECK} 个点让出一次，故实际单 tick 会略超预算。
	 */
	public static final long BUDGET_NANOS = 10_000_000L;

	/** 群系采样每累计多少个点检查一次时间预算（点级让出粒度）。 */
	private static final int BIOME_POINTS_PER_BUDGET_CHECK = 64;

	/** 阶段编号：0=结构 1=村庄 2=群系 3=村庄预确认 4=已结束。与 {@code IndexBuildProgressPayload.phase} 同口径。 */
	private static final int PHASE_STRUCTURE = 0;
	private static final int PHASE_VILLAGE = 1;
	private static final int PHASE_BIOME = 2;
	private static final int PHASE_CONFIRM = 3;
	/**
	 * 已结束。本值是阶段编号的<b>唯一权威来源</b>：网络层回报「完成/取消」进度时用的结束阶段
	 * （{@code DollNetworking.INDEX_PHASE_END}）直接引用本常量，不再各写一份 {@code 4}
	 * ——两处独立魔法数一旦改一处忘另一处，进度包就会带着错的阶段号静默发出去。
	 */
	public static final int PHASE_DONE = 4;

	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
	private static Job current;
	private static long startedNanos;
	private static boolean doneLogged;

	private GeoIndexBuildJob() {
	}

	/**
	 * 手动开启「当前维度」的底图构建（由搜索屏的「全域索引」按钮触发）。
	 *
	 * <p><b>为什么改了触发方式</b>：原来是开服自动跑「全维度 × 半径 16000」的全款——实测新世界
	 * 81.4s（群系占 79%、玩家没去过的维度占 38%），期间还因单个结构不可让出而卡到
	 * {@code Can't keep up!}。现改为<b>玩家在搜索屏里主动点按钮才建、且只建当前维度</b>；
	 * 中心也从"维度出生点"改成<b>发起者所在位置</b>——原锚点有个隐性错配：结果是按<b>玩家</b>
	 * 距离排序取前 10 的，锚在出生点时，玩家一走远，桶里的点就基本用不上了。
	 *
	 * @param level   要构建的维度
	 * @param centerX 构建中心 X（方块，取发起者当前位置）
	 * @param centerZ 构建中心 Z（方块）
	 */
	public static void beginForLevel(ServerLevel level, int centerX, int centerZ) {
		abortCurrent();
		QUEUE.clear();
		current = null;
		doneLogged = false;
		startedNanos = System.nanoTime();
		try {
			QUEUE.addLast(new Job(level, centerX, centerZ));
		} catch (Throwable t) {
			LOGGER.error("DollGeoPreIndex 初始化维度任务失败 {}：{}",
				level.dimension().identifier(), t.toString());
		}
		current = QUEUE.poll();
		markJobStart(current);
		LOGGER.info("DollGeoPreIndex 手动底图构建开始（主线程时间片推进，每 tick {} ms）：维度 {}，中心 ({}, {})",
			BUDGET_NANOS / 1_000_000, level.dimension().identifier(), centerX, centerZ);
	}

	/** 是否有构建任务在进行中（供每 tick 事件与 UI 判断）。 */
	public static boolean isActive() {
		return current != null;
	}

	/**
	 * 当前构建进度快照（供 UI 显示）。{@code active=false} 时其余字段无意义。
	 *
	 * <p>计量单位：结构/村庄每个分类按 {@link #PROGRESS_BIOME_POINTS_PER_STRUCTURE}、每个待确认候选按
	 * {@link #PROGRESS_BIOME_POINTS_PER_CONFIRM_ITEM} 折算成等效的群系采样点数，群系则按粗网格的采样
	 * 点数计——这样百分比才真正反映<b>耗时</b>分布（各类工作的单位数之比＝实测耗时之比）。
	 */
	public record Progress(boolean active, int phase, int doneUnits, int totalUnits) {

		/** 0~100 的百分比。 */
		public int percent() {
			return totalUnits <= 0 ? 0 : (int) Math.min(100L, doneUnits * 100L / totalUnits);
		}
	}

	/** 当前进度；无任务时返回 {@code active=false}。 */
	public static Progress progress() {
		Job j = current;
		if (j == null || j.phase == PHASE_DONE) {
			return new Progress(false, PHASE_DONE, 0, 0);
		}
		// 结构/村庄阶段按「等效群系点数」折算（见 PROGRESS_BIOME_POINTS_PER_STRUCTURE 的取值依据），
		// 否则 30 个分类只占 1.1% 的进度条、而它才是耗时大头。
		int structUnits = (j.structures.size() + j.villages.size()) * PROGRESS_BIOME_POINTS_PER_STRUCTURE;
		// 预确认的单位数：已派发就取真实候选数；未派发则按"每类最多 VILLAGE_PRECONFIRM_TRIES 个"估。
		// 分母必须在阶段开始前就定下来（否则进度条会中途跳变），估计偏差只影响走得匀不匀、不影响结果。
		int confirmItems = j.confirm != null ? j.confirm.total
			: j.villages.size() * VILLAGE_PRECONFIRM_TRIES;
		int confirmUnits = confirmItems * PROGRESS_BIOME_POINTS_PER_CONFIRM_ITEM;
		int total = structUnits + BIOME_GRID_POINTS + confirmUnits;
		int done;
		if (j.phase == PHASE_STRUCTURE) {
			done = j.idx * PROGRESS_BIOME_POINTS_PER_STRUCTURE;
		} else if (j.phase == PHASE_VILLAGE) {
			done = (j.structures.size() + j.idx) * PROGRESS_BIOME_POINTS_PER_STRUCTURE;
		} else if (j.phase == PHASE_BIOME) {
			// 群系阶段：游标 (gz, gx) → 已扫过的采样点数
			int w = 2 * (PREINDEX_RADIUS_BLOCKS / PREINDEX_BIOME_STEP) + 1;
			int gmax = (w - 1) / 2;
			int row = Math.max(0, Math.min(w - 1, j.gz + gmax));
			int col = Math.max(0, Math.min(w, j.gx + gmax));
			done = structUnits + Math.min(BIOME_GRID_POINTS, row * w + col);
		} else {
			// 预确认：按已完成的候选数推进（remaining 是跨线程读的原子量，主线程读它没问题）
			Confirm c = j.confirm;
			int doneItems = c == null ? 0 : c.total - c.remaining.get();
			done = structUnits + BIOME_GRID_POINTS + doneItems * PROGRESS_BIOME_POINTS_PER_CONFIRM_ITEM;
		}
		return new Progress(true, j.phase, done, total);
	}

	/** 记录"该维度此刻成为当前任务"，作为它自己耗时的起点（勿改用 Job 构造时刻，那是全维度共用的）。 */
	private static void markJobStart(Job j) {
		if (j != null) {
			j.jobStartNanos = System.nanoTime();
			j.phaseStartNanos = j.jobStartNanos;
		}
	}

	/** 服务端停止/玩家取消：丢弃未完成的构建任务（已完成的维度都已落盘）。 */
	public static void cancel() {
		abortCurrent();
		QUEUE.clear();
		current = null;
	}

	/**
	 * 让"当前任务"的预确认工作线程放弃剩余候选。
	 *
	 * <p>任务的其它阶段都是主线程时间片推进，被丢弃时天然即刻停止；只有预确认跑在工作线程池上，
	 * 不打招呼就换任务的话，它还会把最多上百个候选（每个 440~550ms）白装配完。故换任务/取消时置位。
	 * 代价是被放弃的那一批确认结果不入桶——符合"取消"的语义。
	 */
	private static void abortCurrent() {
		Job j = current;
		if (j != null) {
			j.aborted = true;
		}
	}

	/**
	 * 推进当前维度一小段工作。由每 tick 事件调用。
	 *
	 * @return true 表示<b>全部维度</b>均已构建完成（调用方可据此更新"预载中"状态与提示）
	 */
	public static boolean advance(MinecraftServer server) {
		long deadline = System.nanoTime() + BUDGET_NANOS;
		while (current != null) {
			if (stepCurrent(deadline)) {
				// 预算已用尽但仍有后续工作：让出主线程，下个 tick 继续
				return false;
			}
			finishDimension(server, current);
			current = QUEUE.poll();
			if (current == null) {
				if (!doneLogged) {
					doneLogged = true;
					LOGGER.info("DollGeoPreIndex 底图预索引完成，总耗时 {} ms",
						(System.nanoTime() - startedNanos) / 1_000_000);
				}
				return true;
			}
			markJobStart(current);
			if (System.nanoTime() >= deadline) {
				return false;
			}
		}
		return true;
	}

	/** 当前维度是否已全部构建完成：true=完成，false=预算用尽但仍有工作。 */
	private static boolean stepCurrent(long deadline) {
		Job j = current;
		while (true) {
			if (j.phase == PHASE_DONE) {
				return false;
			}
			if (j.phase == PHASE_STRUCTURE || j.phase == PHASE_VILLAGE) {
				boolean villages = j.phase == PHASE_VILLAGE;
				List<ResourceKey<Structure>> list = villages ? j.villages : j.structures;
				if (j.idx >= list.size()) {
					j.phase++;
					j.idx = 0;
					if (j.phase == PHASE_BIOME) {
						// 结构/村庄阶段收尾：结掉这一段耗时，开始计群系阶段
						j.structNanos = System.nanoTime() - j.phaseStartNanos;
						j.phaseStartNanos = System.nanoTime();
					}
					continue;
				}
				int index = j.idx++;
				collectOneStructure(j, list.get(index), index, villages);
				if (System.nanoTime() >= deadline) {
					return true;
				}
				continue;
			}
			if (j.phase == PHASE_BIOME) {
				// 群系网格采样：逐点推进，预算耗尽或整格走完就返回
				if (stepBiome(j, deadline)) {
					return true;
				}
				j.biomeNanos = System.nanoTime() - j.phaseStartNanos;
				j.phase = PHASE_CONFIRM;
				j.phaseStartNanos = System.nanoTime();
				continue;
			}
			// 村庄预确认：装配在工作线程池上跑，主线程每 tick 只查一次收尾
			if (stepConfirm(j)) {
				return true;
			}
			j.confirmNanos = System.nanoTime() - j.phaseStartNanos;
			j.phase = PHASE_DONE;
			return false;
		}
	}

	/**
	 * 村庄预确认阶段的推进：本阶段在主线程上<b>只做两件事</b>——首次进入时派发任务、
	 * 之后每 tick 看一次是否收尾。
	 *
	 * <p><b>为什么不在主线程装配</b>：村庄装配 440~550ms/次，而主线程一个 tick 只有 50ms，
	 * 做一次就卡服；也不适合像结构/群系那样"时间片推进"——那会把 96 次装配摊成几百个 tick
	 *（约 10 秒纯等待），且每 tick 仍要超预算。故交给 {@link #CONFIRM_EXECUTOR}。
	 *
	 * @return true = 仍在进行（让出主线程，下个 tick 再看）；false = 已结束（含"无需确认"）
	 */
	private static boolean stepConfirm(Job j) {
		Confirm c = j.confirm;
		if (c == null) {
			j.confirm = c = dispatchConfirm(j);
			if (c == null) {
				return false;   // 该维度无村庄 / 无候选 / 判定环境不可用：本阶段直接跳过
			}
		}
		return !c.finished;
	}

	/**
	 * 派发村庄预确认：主线程把候选与判定环境取好，任务丢给 {@link #CONFIRM_EXECUTOR}。
	 *
	 * <p>只在有村庄候选的维度才有活干（下界/末地取不到村庄键 → 直接跳过，这些维度一分钱不多花）。
	 *
	 * @return null = 无需确认（无村庄 / 无候选 / 判定环境不可用）
	 */
	private static Confirm dispatchConfirm(Job j) {
		if (j.villages.isEmpty()) {
			return null;
		}
		StructureGenVerifier.Env env = StructureGenVerifier.create(j.level);
		if (env == null) {
			// 判定环境不可用（如 GeneratorState 未就绪）：跳过预确认。搜索仍走常规校验，不会出错。
			LOGGER.info("DollGeoPreIndex 维度 {} 村庄预确认跳过（生成判定环境不可用）", j.dimId);
			return null;
		}
		Registry<Structure> registry = j.level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> holders = new ArrayList<>(j.villages.size());
		// 待确认项先按 (结构位次, x, z) 收集，最后拆成并行数组——pass[] 用位次做下标，
		// 这样"最后一个收工者"能按位次无锁地读出全部结果（同 enqueueStructureVerify 的做法）。
		List<int[]> items = new ArrayList<>();
		for (int t = 0; t < j.villages.size(); t++) {
			Holder.Reference<Structure> holder = registry.get(j.villages.get(t)).orElse(null);
			holders.add(holder);
			if (holder == null) {
				continue;
			}
			// 用 querySnapshot（副本）而不是 query：下面要就地排序，不能在桶的活列表上动刀
			List<int[]> cands = GeoIndex.querySnapshot(j.dimId, SearchCategory.VILLAGE + ":" + t);
			if (cands.isEmpty()) {
				continue;
			}
			// 桶里的点大致按并入顺序（由近及远），但会混入后续按需累积的点，故显式再按到中心的
			// 距离排一次——必须确认的确实是"最近的一批"。
			cands.sort((a, b) -> Long.compare(distSq(j, a[0], a[1]), distSq(j, b[0], b[1])));
			int n = Math.min(cands.size(), VILLAGE_PRECONFIRM_TRIES);
			for (int i = 0; i < n; i++) {
				int[] p = cands.get(i);
				items.add(new int[]{t, p[0], p[1]});
			}
		}
		if (items.isEmpty()) {
			return null;
		}
		int total = items.size();
		List<int[]> points = new ArrayList<>(total);
		int[] typeOf = new int[total];
		for (int i = 0; i < total; i++) {
			int[] it = items.get(i);
			typeOf[i] = it[0];
			points.add(new int[]{it[1], it[2]});
		}
		Confirm c = new Confirm(total, points, typeOf, holders);
		AtomicInteger cursor = new AtomicInteger();
		for (int w = 0; w < CONFIRM_WORKERS; w++) {
			CONFIRM_EXECUTOR.submit(() -> runConfirm(j, env, c, cursor));
		}
		LOGGER.info("DollGeoPreIndex 维度 {} 村庄预确认开始：{} 类 / 共 {} 个候选（每类最多 {} 个，{} 线程抢单）",
			j.dimId, j.villages.size(), total, VILLAGE_PRECONFIRM_TRIES, CONFIRM_WORKERS);
		return c;
	}

	/** 到建索引中心的水平距离平方（用于把候选按"离中心越近越优先"排序）。 */
	private static long distSq(Job j, int x, int z) {
		long dx = (long) x - j.cx;
		long dz = (long) z - j.cz;
		return dx * dx + dz * dz;
	}

	/** 一个预确认工作线程：抢单领取候选逐个判定；做完最后一个候选的那个线程负责收尾。 */
	private static void runConfirm(Job j, StructureGenVerifier.Env env, Confirm c, AtomicInteger cursor) {
		while (true) {
			if (j.aborted) {
				return;   // 任务已被取消/替换：放弃剩余候选（不再入桶，符合"取消"语义）
			}
			int i = cursor.getAndIncrement();
			if (i >= c.total) {
				return;
			}
			// 先把 holder 与坐标取出来，再判定：异常一律按"通过"处理（同 StructureGenVerifier 的口径）
			Holder<Structure> holder = c.holders.get(c.typeOf[i]);
			int[] p = c.points.get(i);
			boolean ok;
			try {
				ok = StructureGenVerifier.reallyGenerates(env, holder, p[0] >> 4, p[1] >> 4);
			} catch (Throwable t) {
				ok = true;
			}
			c.pass[i] = ok;
			// remaining 的递减发生在 pass[] 写入之后（AtomicInteger 的易失语义给出 happens-before），
			// 故归零者读到的 pass[] 必定是全部线程写完的最终结果——无需额外加锁。
			if (c.remaining.decrementAndGet() == 0) {
				try {
					mergeConfirmed(j, c);
				} catch (Throwable t) {
					LOGGER.error("DollGeoPreIndex 维度 {} 村庄预确认收尾异常：{}", j.dimId, t.toString());
				} finally {
					// ★必须在 finally 里置位：主线程只认这个标志来结束本阶段，
					//   漏置一次就等于进度条永远停在"确认村庄"、按钮永远点不动。
					c.finished = true;
				}
				return;
			}
		}
	}

	/** 预确认收尾：按类型归拢通过的点，并入「已确认」桶（由最后一个完成候选的工作线程调用）。 */
	private static void mergeConfirmed(Job j, Confirm c) {
		int[] hits = new int[c.holders.size()];
		for (int t = 0; t < hits.length; t++) {
			List<int[]> ok = new ArrayList<>();
			for (int i = 0; i < c.total; i++) {
				if (c.pass[i] && c.typeOf[i] == t) {
					ok.add(c.points.get(i));
				}
			}
			if (!ok.isEmpty()) {
				GeoIndex.merge(j.dimId, GeoIndex.confirmedKey(SearchCategory.VILLAGE, t), ok);
				hits[t] = ok.size();
			}
		}
		LOGGER.info("DollGeoPreIndex 维度 {} 村庄预确认完成：判定 {} 个候选 → 确认真会生成 {} 个（各类 {}）",
			j.dimId, c.total, Arrays.stream(hits).sum(), Arrays.toString(hits));
	}

	/** 收集单个结构/村庄的候选并并入索引。 */
	private static void collectOneStructure(Job j, ResourceKey<Structure> key, int index, boolean village) {
		try {
			if (!GeoIndexService.structurePossibleInDimension(j.level, key)) {
				return;
			}
			j.catsTried++;
			// 混型（允许群系与同结构集兄弟重叠：下界堡垒/要塞、基础传送门与 6 个变体）：
			// 群系级校验分不开它们，做预索引只会产出"类型张冠李戴"的坐标 → 整类跳过，
			// 查询会自然回落到类型精确的实时定位路径。单列计数便于核对。
			if (GeoIndexService.biomeAmbiguousWithSiblings(j.level, key)) {
				j.catsSkippedAmbiguous++;
				LOGGER.info("DollGeoPreIndex 结构混型跳过预索引 {}@{}（允许群系与同集兄弟重叠，查询回落到实时精确搜索）",
					key.identifier(), j.dimId);
				return;
			}
			// 其余一律过群系级校验：placement 级判定不含群系约束，不校验的桶会灌入大量无效坐标。
			j.catsGated++;
			long t0 = System.nanoTime();
			List<int[]> cands = GeoIndexService.collectNearest(
				j.level, key, j.cx, j.cz, PREINDEX_RADIUS_BLOCKS, PREINDEX_PER_CATEGORY_MAX);
			if (cands.isEmpty()) {
				// 该维度确实没有这个结构时属正常；但若某个密结构（如村庄）长期为 0，说明枚举又出问题了。
				LOGGER.info("DollGeoPreIndex 结构无候选 {}@{}", key.identifier(), j.dimId);
				return;
			}
			int cat = village ? SearchCategory.VILLAGE : SearchCategory.STRUCTURE;
			GeoIndex.merge(j.dimId, cat + ":" + index, cands);
			j.catsWithHits++;
			if (village) {
				j.villagePoints += cands.size();
			} else {
				j.structPoints += cands.size();
			}
			long ms = (System.nanoTime() - t0) / 1_000_000;
			if (cands.size() >= PREINDEX_PER_CATEGORY_MAX) {
				j.catsAtCap++;
			} else if (cands.size() < 8 || ms >= 50) {
				// 修复后仍出现"密结构却只有个位数候选"或"单结构超过 50ms"都值得盯：前者像枚举漏采，后者像让出粒度太粗。
				LOGGER.info("DollGeoPreIndex 结构收集偏少/偏慢 {}@{} -> {} 个坐标 / {} ms",
					key.identifier(), j.dimId, cands.size(), ms);
			}
		} catch (Throwable t) {
			// 单个结构失败只跳过它，不影响本维度其它结构与其后的群系阶段。
			LOGGER.error("DollGeoPreIndex 结构索引异常 {}：{}", key, t.toString());
		}
	}

	/**
	 * 群系网格采样推进。
	 *
	 * @return true 表示"还有工作"（预算用尽但网格未走完）；false 表示网格已全部走完
	 */
	private static boolean stepBiome(Job j, long deadline) {
		if (j.sampler == null) {
			// 首次进入群系阶段：主线程取 BiomeSource/Sampler 引用，并置游标于左上角
			j.buckets.clear();
			j.sampler = GeoIndexService.BiomeGridSampler.create(
				j.level, j.cx, j.cz, PREINDEX_RADIUS_BLOCKS, PREINDEX_BIOME_STEP);
			if (j.sampler == null) {
				return false;   // GeneratorState 未就绪：本维度群系跳过（结构/村庄索引仍有效）
			}
			int gmax = j.sampler.gmax();
			j.gz = -gmax;
			j.gx = -gmax;
			return true;
		}

		int gmax = j.sampler.gmax();
		int checks = 0;
		while (true) {
			if (j.gz > gmax) {
				return false;   // 网格全部走完（最后一行在收尾时已 flush）
			}
			// 多档采样：同一网格点可能在地表与地下分属不同群系，逐档登记到各自桶里
			//（洞穴类群系靠地下档才能入索引）。
			List<ResourceKey<Biome>> keys = j.sampler.keysAt(j.gx, j.gz);
			if (!keys.isEmpty()) {
				int[] pos = j.sampler.posAt(j.gx, j.gz);
				for (ResourceKey<Biome> key : keys) {
					j.buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(pos);
				}
			}
			j.gx++;
			if (j.gx > gmax) {
				// 一行走完：按群系键并入索引（渐进可用），游标移到下一行行首
				flushBuckets(j);
				j.gx = -gmax;
				j.gz++;
			}
			if (++checks >= BIOME_POINTS_PER_BUDGET_CHECK && System.nanoTime() >= deadline) {
				return true;
			}
		}
	}

	/** 把行缓冲按群系 targetIndex 并入 GeoIndex，然后清空。 */
	private static void flushBuckets(Job j) {
		if (j.buckets.isEmpty()) {
			return;
		}
		for (Map.Entry<ResourceKey<Biome>, List<int[]>> e : j.buckets.entrySet()) {
			Integer index = j.biomeIndex.get(e.getKey());
			if (index == null) {
				continue;
			}
			// 用 merge 的返回值（真正新增、去重后）计数：多档采样会让同一点多次进入同一桶，
			// 直接取 e.getValue().size() 会把这部分重复算进去。
			j.biomePoints += GeoIndex.merge(j.dimId, SearchCategory.BIOME + ":" + index, e.getValue());
		}
		j.buckets.clear();
	}

	/** 单维度收尾：标记已完成（跳过重建的依据）+ 落盘 + 记日志。 */
	private static void finishDimension(MinecraftServer server, Job j) {
		try {
			// 先标"已建成"再落盘：saveForDimension 会把它写进文件的 _built 标记，
			// 下次启动即可直接跳过本维度的重扫（这是"重进游戏不再白跑上百秒"的关键）。
			GeoIndex.markBuilt(j.dimId);
			GeoIndex.saveForDimension(server, j.level);
		} catch (Throwable t) {
			LOGGER.error("DollGeoPreIndex 维度 {} 落盘索引异常：{}", j.dimId, t.toString());
		}
		LOGGER.info("DollGeoPreIndex 维度 {} 预索引完成，本维度耗时 {} ms（结构/村庄阶段 {} ms + 群系阶段 {} ms + 村庄预确认 {} ms）",
			j.dimId, (System.nanoTime() - j.jobStartNanos) / 1_000_000,
			j.structNanos / 1_000_000, j.biomeNanos / 1_000_000, j.confirmNanos / 1_000_000);
		LOGGER.info("DollGeoPreIndex 维度 {} 明细：结构 {} 点 / 村庄 {} 点（有结果分类 {}/{}，达上限 {} 个，群系校验 {} 个，混型跳过 {} 个），群系入库点 {} 个",
			j.dimId, j.structPoints, j.villagePoints, j.catsWithHits, j.catsTried, j.catsAtCap,
			j.catsGated, j.catsSkippedAmbiguous, j.biomePoints);
	}

	/** 一次村庄预确认的运行状态（跨主线程与工作线程共享；只读字段不必同步）。 */
	private static final class Confirm {
		/** 待确认候选总数（进度分母与收尾判据）。 */
		final int total;
		/** 剩余未判定候选数：每判定完一个减 1，减到 0 的那个线程负责收尾。 */
		final AtomicInteger remaining;
		/** 各候选的判定结果，下标 = 候选位次；只有 remaining 归零者读取，故无需再加锁。 */
		final boolean[] pass;
		/** 各候选属于哪类村庄，与 {@link #pass} 同下标（值是 {@code holders} 的下标）。 */
		final int[] typeOf;
		/** 各候选的方块坐标（x,z），与 {@link #pass} 同下标。 */
		final List<int[]> points;
		/** 各类村庄的结构 holder（下标 = 该结构在 {@code villages} 里的位次）。 */
		final List<Holder<Structure>> holders;
		/** 收尾是否已完成（主线程据此结束本阶段）。 */
		volatile boolean finished;

		Confirm(int total, List<int[]> points, int[] typeOf, List<Holder<Structure>> holders) {
			this.total = total;
			this.remaining = new AtomicInteger(total);
			this.pass = new boolean[total];
			this.points = points;
			this.typeOf = typeOf;
			this.holders = holders;
		}
	}

	/** 一个维度的构建状态（可断点续跑）。 */
	private static final class Job {
		final ServerLevel level;
		final String dimId;
		final int cx;
		final int cz;
		final List<ResourceKey<Structure>> structures;
		final List<ResourceKey<Structure>> villages;
		final Map<ResourceKey<Biome>, Integer> biomeIndex;

		int phase = PHASE_STRUCTURE;
		int idx;
		int biomePoints;

		/** 本维度自己的计时（从它成为当前任务起算，由 {@code markJobStart} 赋值）。 */
		long jobStartNanos;
		long phaseStartNanos;
		long structNanos;
		long biomeNanos;
		long confirmNanos;

		/** 村庄预确认状态；进入该阶段时创建（见 {@link #dispatchConfirm}）。 */
		Confirm confirm;

		/**
		 * 取消标记：换任务/取消时由主线程置 true，预确认的工作线程据此放弃剩余候选。
		 * 这是本类<b>唯一</b>被非主线程读写的 Job 字段（其余字段都只在主线程读写）。
		 */
		volatile boolean aborted;

		/** 结构/村庄收集统计：检出点数、有结果分类数、尝试分类数、达上限分类数、过群系校验的分类数、混型跳过的分类数。 */
		int structPoints;
		int villagePoints;
		int catsTried;
		int catsWithHits;
		int catsAtCap;
		int catsGated;
		int catsSkippedAmbiguous;

		// 群系网格游标与行缓冲
		GeoIndexService.BiomeGridSampler sampler;
		int gz;
		int gx;
		final Map<ResourceKey<Biome>, List<int[]>> buckets = new HashMap<>();

		Job(ServerLevel level, int centerX, int centerZ) {
			this.level = level;
			this.dimId = level.dimension().identifier().toString();
			this.cx = centerX;
			this.cz = centerZ;

			List<ResourceKey<Structure>> st;
			List<ResourceKey<Structure>> vl;
			List<ResourceKey<Biome>> bi;
			try {
				st = GeoIndexService.orderedStructureKeys(level, false);
			} catch (Throwable t) {
				st = List.of();
			}
			try {
				vl = GeoIndexService.orderedStructureKeys(level, true);
			} catch (Throwable t) {
				vl = List.of();
			}
			try {
				bi = GeoIndexService.orderedBiomeKeys(level);
			} catch (Throwable t) {
				bi = List.of();
			}
			this.structures = st;
			this.villages = vl;
			this.biomeIndex = new HashMap<>(Math.max(16, bi.size() * 2));
			for (int i = 0; i < bi.size(); i++) {
				this.biomeIndex.put(bi.get(i), i);
			}
		}
	}
}
