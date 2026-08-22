package com.example.doll.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人偶位置登记表（仅服务端内存态）。
 *
 * 用于"刷怪蛋右键召回"和"遥控器面板离线人偶列表"：
 * 当人偶所在区块已卸载、getEntity 按 UUID 找不到时，
 * 依据这里登记的最后位置强制加载区块，把人偶拉回玩家身边。
 * 人偶每 tick 更新一次登记；区块卸载(shouldSave=true)时保留登记，
 * 死亡/回收时清除登记。
 */
public final class DollRecallRegistry {

	private static final Map<UUID, DollLocation> LOCATIONS = new ConcurrentHashMap<>();

	private DollRecallRegistry() {
	}

	/** 人偶最后一次被观测到的维度、位置与主人 UUID。 */
	public record DollLocation(ResourceKey<Level> dimension, BlockPos pos, UUID ownerUuid) {
	}

	public static void record(UUID uuid, ResourceKey<Level> dimension, BlockPos pos, UUID ownerUuid) {
		if (uuid != null) {
			LOCATIONS.put(uuid, new DollLocation(dimension, pos, ownerUuid));
		}
	}

	public static DollLocation get(UUID uuid) {
		return uuid == null ? null : LOCATIONS.get(uuid);
	}

	public static void remove(UUID uuid) {
		if (uuid != null) {
			LOCATIONS.remove(uuid);
		}
	}

	/** 获取所有登记的位置（用于控制面板展示离线人偶）。 */
	public static Map<UUID, DollLocation> getAll() {
		return LOCATIONS;
	}

	/**
	 * 清空全部登记。在每次服务器启动（含单机存档切换）时调用：
	 * 该表是进程级静态内存态，跨世界/存档会话会残留旧位置——用旧存档的
	 * 绑定蛋在新存档召回时，会拿着旧坐标去 setChunkForced 一个不存在的区块，
	 * 触发服务端同步建块（深世界 proto-chunk 噪声采样可耗时数秒）。清空后
	 * 人偶每 tick 会重新登记，功能不受影响。
	 */
	public static void clear() {
		LOCATIONS.clear();
	}
}