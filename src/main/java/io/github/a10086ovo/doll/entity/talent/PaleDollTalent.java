package io.github.a10086ovo.doll.entity.talent;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 苍白人偶天赋：恐惧光环（原 DollEntity.applyFearAura 迁入）。
 * <p>
 * 每 20 tick 清除光环（半径 16 格）内敌对生物（{@link Enemy}）的遗留仇恨——
 * 持续索敌抑制与移动抑制由 {@code MobMixin}（canAttack/serverAiStep 取消）与
 * {@code LivingEntityFearAuraMixin}（30% 易伤）按光环中心登记表判定，不在此重复。
 */
public class PaleDollTalent extends SpecialDollTalent {

	private int fearAuraCooldown = 0;

	public PaleDollTalent() {
		super(false);
	}

	@Override
	public void tickAura(DollEntity doll) {
		if (fearAuraCooldown-- > 0) {
			return;
		}
		fearAuraCooldown = 20; // 每 20 tick（1 秒）清一次遗留仇恨
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 16.0;
		AABB box = DollTalent.auraBox(center, radius);
		List<Mob> enemies = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob instanceof Enemy && mob.isAlive()
		);
		for (Mob mob : enemies) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			// 统一软化：清空当前目标打断进行中的攻击（含史莱姆等接触伤害单位）。
			// 持续索敌抑制由 MobMixin.paleFearAura（canAttack→false）负责，
			// 移动抑制由 MobMixin.paleFearImmobilize（serverAiStep 取消）负责——均不持久化 NoAi。
			mob.setTarget(null);
		}
	}
}
