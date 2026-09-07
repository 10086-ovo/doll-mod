package io.github.a10086ovo.doll.entity.talent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 特殊变体（苍白/下界/末影/海洋/森林/向导）天赋基类。
 * <p>
 * 常驻药效基线：恢复IV + 抗性II；由构造参数决定是否追加抗火（仅下界/海洋）。
 * 行为同原 {@code DollEntity.tickPermanentEffects} 的特殊变体分支；各变体光环
 * 由其子类各自实现 {@link #tickAura(DollEntity)}。
 */
public abstract class SpecialDollTalent implements DollTalent {

	private final boolean fireResistant;

	protected SpecialDollTalent(boolean fireResistant) {
		this.fireResistant = fireResistant;
	}

	@Override
	public void tickPermanentEffects(DollEntity doll) {
		if (!doll.hasEffect(MobEffects.REGENERATION))
			doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 3, false, false));
		if (!doll.hasEffect(MobEffects.RESISTANCE))
			doll.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, false));
		if (fireResistant && !doll.hasEffect(MobEffects.FIRE_RESISTANCE))
			doll.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
	}
}
