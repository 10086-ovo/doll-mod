package com.example.doll.util;

import com.example.doll.entity.DollEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * 人偶实体查找工具。
 *
 * <p>按引擎 UUID 跨维度查找已加载的人偶（O(1) 查找，无全维度实体遍历）。
 * DollBatonItem（指挥棒选中/盾构机）与 DollSpawnEggItem（召回）共用，
 * 避免两处重复实现同一逻辑。
 */
public final class DollEntityLookup {

	private DollEntityLookup() {
	}

	/**
	 * 按引擎 UUID 查找已加载的人偶；未找到或已移除时返回 null。
	 * 注意：必须排除 isRemoved 的实体——discard 后引擎仍可能短暂持有引用，
	 * 直接返回会导致对已销毁实体的操作（如传送/回收）落空。
	 */
	public static DollEntity findLoadedDoll(ServerLevel level, UUID uuid) {
		if (uuid == null) {
			return null;
		}
		Entity entity = level.getEntityInAnyDimension(uuid);
		if (entity instanceof DollEntity doll && !doll.isRemoved()) {
			return doll;
		}
		return null;
	}
}
