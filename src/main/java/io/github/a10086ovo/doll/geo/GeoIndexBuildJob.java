package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.network.SearchCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 * 群系网格采样步长（方块）。旧值 48 在 16000 半径下是 {@code (2*333+1)^2 ≈ 44.5 万} 次采样/维度，
	 * 是单维度耗时的大头；改为 64 后采样数降为约 56%（{@code (2*250+1)^2 ≈ 25.1 万}）。
	 * 取 64 而非更稀的 96，是为了在速度与覆盖之间折中：步长越大，小尺度群系越可能
	 * 落在采样空隙里被漏检，而剩余漏检由后续 L2 深扫（D2）兜底。
	 */
	public static final int PREINDEX_BIOME_STEP = 64;

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

	private static final int PHASE_STRUCTURE = 0;
	private static final int PHASE_VILLAGE = 1;
	private static final int PHASE_BIOME = 2;
	private static final int PHASE_DONE = 3;

	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
	private static Job current;
	private static long startedNanos;
	private static boolean doneLogged;

	private GeoIndexBuildJob() {
	}

	/** 服务端启动：按维度初始化构建队列。索引已在 {@link GeoIndex#loadForDimension} 载入，此处只做增量补齐。 */
	public static void begin(MinecraftServer server) {
		QUEUE.clear();
		current = null;
		doneLogged = false;
		startedNanos = System.nanoTime();
		for (ServerLevel lv : server.getAllLevels()) {
			try {
				QUEUE.addLast(new Job(lv));
			} catch (Throwable t) {
				LOGGER.error("DollGeoPreIndex 初始化维度任务失败 {}：{}", lv.dimension().identifier(), t.toString());
			}
		}
		current = QUEUE.poll();
		markJobStart(current);
		LOGGER.info("DollGeoPreIndex 开服底图预索引开始（主线程时间片推进，每 tick {} ms）：{} 个维度",
			BUDGET_NANOS / 1_000_000, QUEUE.size() + (current != null ? 1 : 0));
	}

	/** 记录"该维度此刻成为当前任务"，作为它自己耗时的起点（勿改用 Job 构造时刻，那是全维度共用的）。 */
	private static void markJobStart(Job j) {
		if (j != null) {
			j.jobStartNanos = System.nanoTime();
			j.phaseStartNanos = j.jobStartNanos;
		}
	}

	/** 服务端停止：丢弃未完成的构建任务（已完成的维度都已落盘）。 */
	public static void cancel() {
		QUEUE.clear();
		current = null;
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
			// 群系网格采样：逐点推进，预算耗尽或整格走完就返回
			if (stepBiome(j, deadline)) {
				return true;
			}
			j.biomeNanos = System.nanoTime() - j.phaseStartNanos;
			j.phase = PHASE_DONE;
			return false;
		}
	}

	/** 收集单个结构/村庄的候选并并入索引。 */
	private static void collectOneStructure(Job j, ResourceKey<Structure> key, int index, boolean village) {
		try {
			if (!GeoIndexService.structurePossibleInDimension(j.level, key)) {
				return;
			}
			j.catsTried++;
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
			ResourceKey<Biome> key = j.sampler.keyAt(j.gx, j.gz);
			if (key != null) {
				j.buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(j.sampler.posAt(j.gx, j.gz));
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
			GeoIndex.merge(j.dimId, SearchCategory.BIOME + ":" + index, e.getValue());
			j.biomePoints += e.getValue().size();
		}
		j.buckets.clear();
	}

	/** 单维度收尾：落盘 + 记日志。 */
	private static void finishDimension(MinecraftServer server, Job j) {
		try {
			GeoIndex.saveForDimension(server, j.level);
		} catch (Throwable t) {
			LOGGER.error("DollGeoPreIndex 维度 {} 落盘索引异常：{}", j.dimId, t.toString());
		}
		LOGGER.info("DollGeoPreIndex 维度 {} 预索引完成，本维度耗时 {} ms（结构/村庄阶段 {} ms + 群系阶段 {} ms）",
			j.dimId, (System.nanoTime() - j.jobStartNanos) / 1_000_000,
			j.structNanos / 1_000_000, j.biomeNanos / 1_000_000);
		LOGGER.info("DollGeoPreIndex 维度 {} 明细：结构 {} 点 / 村庄 {} 点（有结果分类 {}/{}，达上限 {} 个），群系采样点 {} 个",
			j.dimId, j.structPoints, j.villagePoints, j.catsWithHits, j.catsTried, j.catsAtCap, j.biomePoints);
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

		/** 结构/村庄收集统计：检出点数、有结果分类数、尝试分类数、达上限分类数。 */
		int structPoints;
		int villagePoints;
		int catsTried;
		int catsWithHits;
		int catsAtCap;

		// 群系网格游标与行缓冲
		GeoIndexService.BiomeGridSampler sampler;
		int gz;
		int gx;
		final Map<ResourceKey<Biome>, List<int[]>> buckets = new HashMap<>();

		Job(ServerLevel level) {
			this.level = level;
			this.dimId = level.dimension().identifier().toString();
			int x = 0;
			int z = 0;
			try {
				// 维度出生点：26.2 中经 LevelData.getRespawnData().pos() 取出生方块坐标
				BlockPos spawn = level.getLevelData().getRespawnData().pos();
				x = spawn.getX();
				z = spawn.getZ();
			} catch (Exception e) {
				// 出生点不可用则退化为原点，不影响索引正确性（只是中心不同）
			}
			this.cx = x;
			this.cz = z;

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
