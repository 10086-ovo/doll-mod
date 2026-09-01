package io.github.a10086ovo.doll.item;

import net.minecraft.world.item.BowItem;

/**
 * 苍白弓 -- 苍白花园主题远程武器，继承原版弓全部机制（蓄力射箭、附魔、耐久消耗）。
 * <p>
 * 特殊效果：箭矢命中生物时施加易伤（受伤 +15%，持续 5 秒）。
 * 该效果通过 {@link io.github.a10086ovo.doll.mixin.AbstractArrowMixin} 实现：
 * Mixin 注入 {@code AbstractArrow.onHitEntity} 尾部，
 * 检查射击者（{@code getOwner()}）主手或副手是否持有苍白弓，
 * 若是则调用 {@link io.github.a10086ovo.doll.PaleVulnerabilityTracker#apply} 施加易伤。
 * 伤害乘算由 {@link io.github.a10086ovo.doll.mixin.LivingEntityVulnerabilityMixin}
 * 在 {@code hurtServer} 中执行（×1.15）。玩家和人偶射箭均生效。
 * <p>
 * 易伤与苍白人偶恐惧光环的 ×1.67 乘算叠加，总计 ×1.92。
 * 重复命中只刷新持续时间，不叠加倍率。
 * <p>
 * 耐久 1536（钻石级，原版弓 384 的四倍）。
 */
public class PaleBowItem extends BowItem {

	public PaleBowItem(Properties properties) {
		super(properties);
	}
}
