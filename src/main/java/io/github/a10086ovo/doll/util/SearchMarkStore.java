package io.github.a10086ovo.doll.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时镜像「玩家打卡过的搜索目标」。跨重启持久性由 {@code PlayerGuideBookMixin}
 * 把扁平 int[](category0,target0,x0,z0,...) 读写入 player.dat 的 NBT 提供；
 * 本类仅在内存中缓存当前会话的值，玩家登录 load 时由 Mixin 从 NBT 重新填充。
 * <p>
 * 上限 {@link #CAP}（128 条），超出时淘汰最旧（FIFO）。同一 (分类, 目标, x, z) 精确去重，
 * 玩家点击打卡按键时在此 添加/移除 翻转。
 */
public final class SearchMarkStore {

	public record Mark(int category, int targetIndex, int x, int z) {
	}

	private static final int CAP = 128;
	private static final Map<UUID, List<Mark>> MEM = new HashMap<>();

	private SearchMarkStore() {
	}

	private static List<Mark> get(UUID id) {
		return MEM.computeIfAbsent(id, k -> new ArrayList<>());
	}

	/** 是否已打卡该目标坐标。 */
	public static boolean contains(UUID id, int category, int targetIndex, int x, int z) {
		return get(id).contains(new Mark(category, targetIndex, x, z));
	}

	/** 打卡/取消打卡：存在则移除（取消），否则添加（打卡），并返回翻转后的状态。 */
	public static boolean toggle(UUID id, int category, int targetIndex, int x, int z) {
		Mark mark = new Mark(category, targetIndex, x, z);
		List<Mark> list = get(id);
		if (list.remove(mark)) {
			return false;
		}
		list.add(mark);
		while (list.size() > CAP) {
			list.remove(0);
		}
		return true;
	}

	/** 序列化为扁平 int[]（category0,target0,x0,z0,...），供 NBT 存储。 */
	public static int[] toFlatArray(UUID id) {
		List<Mark> list = get(id);
		int[] out = new int[list.size() * 4];
		int i = 0;
		for (Mark m : list) {
			out[i++] = m.category();
			out[i++] = m.targetIndex();
			out[i++] = m.x();
			out[i++] = m.z();
		}
		return out;
	}

	/** 从扁平 int[] 恢复（NBT 读取）。 */
	public static void loadFromFlatArray(UUID id, int[] arr) {
		if (arr == null) {
			return;
		}
		List<Mark> list = get(id);
		list.clear();
		for (int i = 0; i + 3 < arr.length; i += 4) {
			list.add(new Mark(arr[i], arr[i + 1], arr[i + 2], arr[i + 3]));
		}
	}
}