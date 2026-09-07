package io.github.a10086ovo.doll.entity.talent;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 变体天赋策略接口（DollTalent）。
 * <p>
 * 每只人偶按 {@code DollVariant} 持有一个天赋实例（惰性缓存于 {@code DollEntity}），
 * 由 {@code DollEntity.tick()} 的固定生命周期钩子驱动。当前已抽取的天赋纵切面：
 * <ul>
 *   <li>{@link #tickPermanentEffects(DollEntity)}——每 tick 的常驻药效补漏（原 tickPermanentEffects）；</li>
 *   <li>{@link #tickAura(DollEntity)}——每 tick 的变体光环（原 tickVariantAuras）。
 *       光环中心登记表（pale/nether）属引擎职责，由 DollEntity 另行维护。</li>
 * </ul>
 * 后续波次将按同一切缝迁入战斗技能（音波/烈焰弹/末影弹/处决/激光）等纵切面。
 * <p>
 * 天赋实例持有自身 tick 冷却字段，因此每只人偶独立创建（不可跨实体共享单例）。
 */
public interface DollTalent {

	/** 常驻药效钩子：服务端每 tick 调用，保证药效持久。 */
	default void tickPermanentEffects(DollEntity doll) {
	}

	/** 变体光环钩子：服务端每 tick 调用，由各天赋按需节流。 */
	default void tickAura(DollEntity doll) {
	}

	/**
	 * 战斗技能钩子：近战/射手模式（combatMode）且已锁定目标时由
	 * {@code DollEntity.tickSpecialAttacks} 调用（原 handleWardenSonicBoom 等入口）。
	 * 各变体自行持有冷却/蓄力状态；默认空实现（普通/向导等无战斗技能变体）。
	 */
	default void tickCombat(DollEntity doll) {
	}

	/** 构建以 center 为中心、XZ/Y 各延伸 radius 的检测盒（原 DollEntity.createAuraAABB）。 */
	static AABB auraBox(Vec3 center, double radius) {
		return AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
	}
}
