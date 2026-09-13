package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GeoIndex 运行时索引状态（M1：世界级、进服即有）。
 *
 * <p>以维度为单元持有 {@code "category:targetIndex" -> 候选坐标列表}，由底图/深扫任务（见
 * {@link GeoIndexBuildJob} 与 {@link GeoIndexService}）灌入，查询时直接读内存索引得到结果，
 * 避免每次重算。
 *
 * <p>生命周期：服务器启动时自磁盘加载（D5），停服时落盘；可随时并入新收集到的候选。
 * 结构索引由世界种子固定永不陈腐，因此合并是幂等的（天然去重）。
 *
 * <p><b>去重为何用哈希</b>：旧实现每并入一个点都要线性扫一遍已有列表，对群系这种动辄数万点的桶
 * 是 O(n²)——单维度累计上亿次比较，是开服卡顿的来源之一。现改为每个桶维护
 * {@code (x,z) 打包成 long 的 HashSet}，单点并入摊还 O(1)，语义不变（更精确：旧实现带 ±1 模糊）。
 *
 * <p>线程：本类方法可能被查询路径（服务端主线程）与构建任务先后调用，故对 {@link #BY_DIMENSION}
 * 的所有读写统一加 {@link #LOCK}，保证并发安全、不丢数据。
 */
public final class GeoIndex {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	private static final Map<String, Map<String, Bucket>> BY_DIMENSION = new HashMap<>();

	private GeoIndex() {
	}

	/** 全局静态锁：构建任务写、玩家搜索读，统一加锁保证并发安全。 */
	private static final Object LOCK = new Object();

	/**
	 * 每维度的「内容版本」：每次确有新点并入就 +1。用于判断该维度自上次落盘后是否真有变更。
	 *
	 * <p>动机：索引很大（单维度原始 JSON 可达 10MB 级、约百万个坐标对），每次落盘都要 Gson 逐点
	 * 装箱 + GZIP，成本高。而关服时 {@code SERVER_STOPPING} 会对所有维度各落一次盘，其中绝大多数
	 * 维度其实在预索引完成时（{@code finishDimension}）就已落过盘、期间没有任何新增——重写内容
	 * 与磁盘完全一致，纯属浪费，正是"关游戏保存变慢"的来源。有了版本号即可跳过这些冗余写盘。
	 */
	private static final Map<String, Long> VERSION = new HashMap<>();

	/** 每维度「最后一次成功落盘时的版本」。{@code VERSION > SAVED_VERSION} 即表示有未落盘变更。 */
	private static final Map<String, Long> SAVED_VERSION = new HashMap<>();

	/**
	 * 已完成完整预索引的维度集合（随索引文件里的 {@code _built} 标记一起持久化）。
	 *
	 * <p><b>为什么需要</b>：原实现里"加载索引"与"是否需要重建"完全脱钩——每次开服都把已落盘的
	 * 索引从零重扫一遍（`GeoIndexBuildJob` 每个维度一律从 PHASE_STRUCTURE 开始）。
	 * 实测单次白跑 32s~129s（日志佐证：某次 107s 重建后 {@code 停服落盘：0 个维度有新增已写入}），
	 * 这才是"重进游戏要重新加载"的真正原因。结构位置由世界种子决定、永不陈腐，落盘即完整结果，
	 * 故标了 {@code _built} 的维度启动时直接跳过。
	 */
	private static final Set<String> BUILT = new HashSet<>();

	/** 一个目标键下的候选坐标集合：列表保序（对外查询用）+ 哈希索引（去重用）。 */
	private static final class Bucket {
		private final List<int[]> points = new ArrayList<>();
		private final Set<Long> index = new HashSet<>();

		/** 并入一个点；已存在返回 false。 */
		boolean add(int x, int z) {
			if (!index.add(pack(x, z))) {
				return false;
			}
			points.add(new int[]{x, z});
			return true;
		}

		boolean isEmpty() {
			return points.isEmpty();
		}

		boolean contains(int x, int z) {
			return index.contains(pack(x, z));
		}

		static long pack(int x, int z) {
			return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		}
	}

	private static Map<String, Bucket> dimMap(String dimensionId) {
		synchronized (LOCK) {
			return BY_DIMENSION.computeIfAbsent(dimensionId, k -> new HashMap<>());
		}
	}

	/** 服务端启动：加载该维度索引（覆盖内存中旧值）。 */
	public static void loadForDimension(MinecraftServer server, ServerLevel level) {
		String dimId = dimId(level);
		GeoIndexStorage.Loaded loaded = GeoIndexStorage.load(server, dimId);
		Map<String, Bucket> buckets = new HashMap<>(Math.max(16, loaded.buckets().size() * 2));
		for (Map.Entry<String, List<int[]>> e : loaded.buckets().entrySet()) {
			Bucket b = new Bucket();
			for (int[] c : e.getValue()) {
				b.add(c[0], c[1]);
			}
			buckets.put(e.getKey(), b);
		}
		synchronized (LOCK) {
			BY_DIMENSION.put(dimId, buckets);
			// 刚从磁盘读回，内存与磁盘一致：版本归零，表示当前无未落盘变更。
			VERSION.put(dimId, 0L);
			SAVED_VERSION.put(dimId, 0L);
			if (loaded.built()) {
				BUILT.add(dimId);
			} else {
				BUILT.remove(dimId);
			}
		}
		// 日志带上解析到的绝对路径与文件是否存在：这一行是"重进游戏要不要重建"的唯一判据，
		// 历史上出现过"文件明明写了却读回 0 条"，把路径与存在性打出来才能一眼定位是路径错位、
		// 被外部删档、还是版本不符（后者由 GeoIndexStorage 另打一条日志）。
		java.nio.file.Path file = GeoIndexStorage.fileFor(server, dimId);
		int points = buckets.values().stream().mapToInt(b -> b.points.size()).sum();
		LOGGER.info("GeoIndex 加载维度 {}：{} 个目标条目 / {} 个候选点，预索引完成标记={}，文件={}（{}）",
			dimId, buckets.size(), points, loaded.built(), file,
			java.nio.file.Files.exists(file) ? "存在" : "不存在");
	}

	/**
	 * 该维度是否已完成预索引（可跳过重建）。
	 *
	 * <p>供 {@code GeoIndexBuildJob.begin} 决定是否入队；返回 true 表示磁盘上已有该维度的完整索引。
	 */
	public static boolean isBuilt(String dimensionId) {
		synchronized (LOCK) {
			return BUILT.contains(dimensionId);
		}
	}

	/**
	 * 标记该维度预索引已完整完成——由构建任务在收尾（{@code finishDimension}）时调用，
	 * 随后落盘写入 {@code _built} 标记，使下次启动直接跳过重建。
	 */
	public static void markBuilt(String dimensionId) {
		synchronized (LOCK) {
			BUILT.add(dimensionId);
			// 抬高版本：确保紧接着的这次落盘一定会写下 _built 标记——哪怕该维度一个桶都没有
			//（否则"无变更即跳过落盘"会让标记丢掉，下次又白跑一遍）。
			VERSION.merge(dimensionId, 1L, Long::sum);
		}
	}

	/**
	 * 服务端停止/单维度完成：将该维度索引落盘。
	 *
	 * <p>若该维度自上次成功落盘后没有任何新增（{@code VERSION <= SAVED_VERSION}），直接跳过——
	 * 磁盘内容与内存一致，重写只是白白花掉一次大文件序列化 + GZIP 的时间（关服卡"保存世界"的元凶）。
	 */
	public static void saveForDimension(MinecraftServer server, ServerLevel level) {
		String dimId = dimId(level);
		Map<String, Bucket> buckets;
		long snapshot;
		boolean built;
		synchronized (LOCK) {
			long version = VERSION.getOrDefault(dimId, 0L);
			if (version <= SAVED_VERSION.getOrDefault(dimId, 0L)) {
				return;
			}
			buckets = BY_DIMENSION.get(dimId);
			snapshot = version;
			built = BUILT.contains(dimId);
		}
		if (buckets == null || (buckets.isEmpty() && !built)) {
			// 无内容可写（例如空维度且未建完）：标记为已同步，避免下次再尝试。
			// 注意：已建成（built=true）的空维度仍要写盘——否则 _built 标记会随文件一起缺失。
			synchronized (LOCK) {
				SAVED_VERSION.merge(dimId, snapshot, Math::max);
			}
			return;
		}
		// 转换为落盘格式（GeoIndexStorage 只认 List<int[]>）
		Map<String, List<int[]>> plain = new HashMap<>(buckets.size() * 2);
		synchronized (LOCK) {
			for (Map.Entry<String, Bucket> e : buckets.entrySet()) {
				plain.put(e.getKey(), new ArrayList<>(e.getValue().points));
			}
		}
		if (GeoIndexStorage.save(server, dimId, plain, built)) {
			synchronized (LOCK) {
				// 只推进到快照版本：若序列化期间又有新点并入（VERSION 已更大），本维度仍保持"脏"，
				// 下次落盘会再写一遍，确保不丢数据。（GeoIndexStorage.save 已在方法内同步，但版本
				// 可能在它返回前被并发 merge 抬高，故必须比对快照而非直接取当前值。）
				SAVED_VERSION.merge(dimId, snapshot, Math::max);
			}
		}
	}

	/** 该维度是否存在未落盘变更（供停服统计与测试观测）。 */
	public static boolean hasUnsavedChanges(String dimensionId) {
		synchronized (LOCK) {
			return VERSION.getOrDefault(dimensionId, 0L) > SAVED_VERSION.getOrDefault(dimensionId, 0L);
		}
	}

	private static String dimId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/**
	 * 「已确认」桶的键前缀。这类桶里存的是<b>已经过生成校验、确认真会生成</b>的点，
	 * 与候选桶（placement 合法 + 群系合法，但没装配过）区分开。
	 *
	 * <p>分开存是必须的——两者的用法完全不同：候选桶的点必须再逐个过
	 * {@code StructureGenVerifier.reallyGenerates} 才能给玩家；已确认桶的点可以直接回放/免检。
	 * 混在一个桶里就无法分辨哪些能免检。
	 */
	private static final String CONFIRMED_PREFIX = "confirmed:";

	/**
	 * 「已确认」桶的键。当前只有村庄用它（见 {@code GeoIndexBuildJob} 的村庄预确认阶段）。
	 *
	 * <p>构词与候选桶 {@code "category:targetIndex"} 同形但多一段前缀，故两种桶天然不会相撞；
	 * 落盘/读回无需特殊处理（{@code GeoIndexStorage} 一视同仁地按桶键存取）。
	 */
	public static String confirmedKey(int category, int targetIndex) {
		return CONFIRMED_PREFIX + category + ":" + targetIndex;
	}

	/** 查询某目标索引的候选；无则空列表。返回的列表只读使用（调用方不得改动）。 */
	public static List<int[]> query(String dimensionId, String categoryTargetKey) {
		synchronized (LOCK) {
			Map<String, Bucket> m = BY_DIMENSION.get(dimensionId);
			if (m == null) {
				return List.of();
			}
			Bucket b = m.get(categoryTargetKey);
			return b != null && !b.isEmpty() ? b.points : List.of();
		}
	}

	/**
	 * 同 {@link #query}，但返回<b>副本</b>，可在锁外安全遍历/排序。
	 *
	 * <p>{@link #query} 返回的是桶内那个活列表（为了不让每次查询都复制上万点的群系桶）；
	 * 而 {@link #merge} 可能由工作线程调用（如群系异步搜索、村庄预确认收尾），
	 * 与主线程"取回来再遍历"之间存在并发追加。追加只增不删，实际危害有限，但副本是零成本的确定性做法——
	 * 凡是要排序或长时间持有该列表的调用方，一律用本方法。
	 */
	public static List<int[]> querySnapshot(String dimensionId, String categoryTargetKey) {
		synchronized (LOCK) {
			Map<String, Bucket> m = BY_DIMENSION.get(dimensionId);
			if (m == null) {
				return List.of();
			}
			Bucket b = m.get(categoryTargetKey);
			return b != null && !b.isEmpty() ? new ArrayList<>(b.points) : List.of();
		}
	}

	/**
	 * 将新收集到的某目标候选并入索引（哈希去重，幂等）；确有新点并入时抬高该维度版本号（标记为"脏"）。
	 *
	 * @return 本次<b>真正新增</b>（去重后）的点数；0 表示这些点已全部存在。群系多档采样会重复覆盖同一目标，
	 *         调用方靠它统计"实际入库点数"而不是"采样次数"。
	 */
	public static int merge(String dimensionId, String categoryTargetKey, List<int[]> fresh) {
		if (fresh == null || fresh.isEmpty()) {
			return 0;
		}
		synchronized (LOCK) {
			Bucket b = dimMap(dimensionId).computeIfAbsent(categoryTargetKey, k -> new Bucket());
			int added = 0;
			for (int[] c : fresh) {
				if (b.add(c[0], c[1])) {
					added++;
				}
			}
			if (added > 0) {
				VERSION.merge(dimensionId, 1L, Long::sum);
			}
			return added;
		}
	}

	/** 是否已收录该坐标。 */
	public static boolean contains(String dimensionId, String categoryTargetKey, int x, int z) {
		synchronized (LOCK) {
			Map<String, Bucket> m = BY_DIMENSION.get(dimensionId);
			if (m == null) {
				return false;
			}
			Bucket b = m.get(categoryTargetKey);
			return b != null && b.contains(x, z);
		}
	}
}
