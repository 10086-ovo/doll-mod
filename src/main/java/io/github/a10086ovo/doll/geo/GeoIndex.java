package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GeoIndex 运行时索引状态（M1：世界级、进服即有）。
 *
 * <p>以维度为单元持有 {@code "category:targetIndex" -> 候选坐标列表}，由底图/深扫任务（当前为
 * {@link GeoIndexService#collectCandidates}）灌入，查询时直接读内存索引得到结果，避免每次重算。
 *
 * <p>生命周期：服务器启动时自磁盘加载（D5），停服时落盘；可随时并入新收集到的候选。
 * 结构索引由世界种子固定永不陈腐，因此合并是幂等的（天然去重）。

 * <p>线程：本类方法仅在服务端主线程调用（tick），与 {@code DollNetworking} 现有搜索路径保持一致，
 * 无需额外加锁。
 */
public final class GeoIndex {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	private static final Map<String, Map<String, List<int[]>>> BY_DIMENSION = new HashMap<>();

	private GeoIndex() {
	}

	/**
	 * 全局静态锁：预索引在后台守护线程写、玩家搜索在主线程读、群系在工作线程并入，
	 * 故对 {@link #BY_DIMENSION} 的所有读写本类统一加 {@code synchronized}（方法级锁同一对象，即
	 * {@code GeoIndex} 的 Class 对象），保证并发安全、不丢数据。
	 */
	private static final Object LOCK = new Object();

	private static Map<String, List<int[]>> dimMap(String dimensionId) {
		synchronized (LOCK) {
			return BY_DIMENSION.computeIfAbsent(dimensionId, k -> new HashMap<>());
		}
	}

	/** 服务端启动：加载该维度索引（覆盖内存中旧值）。 */
	public static void loadForDimension(MinecraftServer server, ServerLevel level) {
		String dimId = dimId(level);
		Map<String, List<int[]>> loaded = GeoIndexStorage.load(server, dimId);
		synchronized (LOCK) {
			BY_DIMENSION.put(dimId, loaded);
		}
		LOGGER.info("GeoIndex 加载维度 {}：{} 个目标条目", dimId, loaded.size());
	}

	/** 服务端停止：将该维度索引落盘。 */
	public static void saveForDimension(MinecraftServer server, ServerLevel level) {
		String dimId = dimId(level);
		Map<String, List<int[]>> map;
		synchronized (LOCK) {
			map = BY_DIMENSION.get(dimId);
		}
		if (map == null || map.isEmpty()) {
			return;
		}
		GeoIndexStorage.save(server, dimId, map);
	}

	private static String dimId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/** 查询某目标索引是否存在候选；无则空列表。 */
	public static List<int[]> query(String dimensionId, String categoryTargetKey) {
		synchronized (LOCK) {
			Map<String, List<int[]>> m = BY_DIMENSION.get(dimensionId);
			if (m == null) {
				return List.of();
			}
			List<int[]> v = m.get(categoryTargetKey);
			return v != null ? v : List.of();
		}
	}

	/** 将新收集到的某目标候选并入索引（幂等去重，避免重复占用）。 */
	public static void merge(String dimensionId, String categoryTargetKey, List<int[]> fresh) {
		if (fresh == null || fresh.isEmpty()) {
			return;
		}
		synchronized (LOCK) {
			Map<String, List<int[]>> m = dimMap(dimensionId);
			List<int[]> existing = m.computeIfAbsent(categoryTargetKey, k -> new ArrayList<>());
			for (int[] c : fresh) {
				if (!contains(existing, c[0], c[1])) {
					existing.add(c);
				}
			}
		}
	}

	/** 是否已收录目标。 */
	public static boolean contains(String dimensionId, String categoryTargetKey, int x, int z) {
		synchronized (LOCK) {
			Map<String, List<int[]>> m = BY_DIMENSION.get(dimensionId);
			if (m == null) {
				return false;
			}
			return contains(m.get(categoryTargetKey), x, z);
		}
	}

	private static boolean contains(List<int[]> list, int x, int z) {
		if (list == null) {
			return false;
		}
		for (int[] c : list) {
			if (Math.abs(c[0] - x) <= 1 && Math.abs(c[1] - z) <= 1) {
				return true;
			}
		}
		return false;
	}
}