package io.github.a10086ovo.doll.entity.talent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 普通工人（NONE 变体，一至五阶）天赋：常驻药效按阶缩放，无光环。
 * <p>
 * 同原 {@code DollEntity.tickPermanentEffects} 的普通分支：
 * 二阶恢复 I、三阶恢复 II、四阶恢复 III + 抗性 I、五阶恢复 IV + 抗性 II；一阶无药效。
 */
public class WorkerDollTalent implements DollTalent {

	@Override
	public void tickPermanentEffects(DollEntity doll) {
		int level = doll.getDollLevel();
		if (level >= 5) {
			if (!doll.hasEffect(MobEffects.REGENERATION))
				doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 3, false, false));
			if (!doll.hasEffect(MobEffects.RESISTANCE))
				doll.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, false));
		} else if (level >= 4) {
			if (!doll.hasEffect(MobEffects.REGENERATION))
				doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 2, false, false));
			if (!doll.hasEffect(MobEffects.RESISTANCE))
				doll.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false));
		} else if (level >= 3) {
			if (!doll.hasEffect(MobEffects.REGENERATION))
				doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 1, false, false));
		} else if (level >= 2) {
			if (!doll.hasEffect(MobEffects.REGENERATION))
				doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 0, false, false));
		}
		// level 1（一阶）：不施加永久药水
	}
}
