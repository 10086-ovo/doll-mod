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
		Map<String, List<int[]>> loaded = GeoIndexStorage.load(server, dimId);
		Map<String, Bucket> buckets = new HashMap<>(Math.max(16, loaded.size() * 2));
		for (Map.Entry<String, List<int[]>> e : loaded.entrySet()) {
			Bucket b = new Bucket();
			for (int[] c : e.getValue()) {
				b.add(c[0], c[1]);
			}
			buckets.put(e.getKey(), b);
		}
		synchronized (LOCK) {
			BY_DIMENSION.put(dimId, buckets);
		}
		LOGGER.info("GeoIndex 加载维度 {}：{} 个目标条目", dimId, buckets.size());
	}

	/** 服务端停止：将该维度索引落盘。 */
	public static void saveForDimension(MinecraftServer server, ServerLevel level) {
		String dimId = dimId(level);
		Map<String, Bucket> buckets;
		synchronized (LOCK) {
			buckets = BY_DIMENSION.get(dimId);
		}
		if (buckets == null || buckets.isEmpty()) {
			return;
		}
		// 转换为落盘格式（GeoIndexStorage 只认 List<int[]>）
		Map<String, List<int[]>> plain = new HashMap<>(buckets.size() * 2);
		synchronized (LOCK) {
			for (Map.Entry<String, Bucket> e : buckets.entrySet()) {
				plain.put(e.getKey(), new ArrayList<>(e.getValue().points));
			}
		}
		GeoIndexStorage.save(server, dimId, plain);
	}

	private static String dimId(ServerLevel level) {
		return level.dimension().identifier().toString();
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

	/** 将新收集到的某目标候选并入索引（哈希去重，幂等）。 */
	public static void merge(String dimensionId, String categoryTargetKey, List<int[]> fresh) {
		if (fresh == null || fresh.isEmpty()) {
			return;
		}
		synchronized (LOCK) {
			Bucket b = dimMap(dimensionId).computeIfAbsent(categoryTargetKey, k -> new Bucket());
			for (int[] c : fresh) {
				b.add(c[0], c[1]);
			}
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
