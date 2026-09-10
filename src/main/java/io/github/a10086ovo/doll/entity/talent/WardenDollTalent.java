package io.github.a10086ovo.doll.entity.talent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 驯服幽匿人偶（WARDEN 变体）天赋：恢复VI + 抗性IV + 抗火（击退免疫由属性提供）。
 * <p>
 * 常驻药效同原 {@code DollEntity.tickPermanentEffects} 的 WARDEN 分支；
 * 战斗技能：音波攻击（原 {@code handleWardenSonicBoom} / {@code fireSonicBoom} 迁入），
 * 无距离限制——锁定目标即蓄力 1.5 秒发射，4 秒冷却；冷却/蓄力状态为本实例字段。
 */
public class WardenDollTalent implements DollTalent {

	private int sonicChargeTicks = -1;  // -1 = 不在蓄力
	private int sonicCooldown = 0;      // 发射后冷却计数

	@Override
	public void tickPermanentEffects(DollEntity doll) {
		if (!doll.hasEffect(MobEffects.REGENERATION))
			doll.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 5, false, false));
		if (!doll.hasEffect(MobEffects.RESISTANCE))
			doll.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 3, false, false));
		if (!doll.hasEffect(MobEffects.FIRE_RESISTANCE))
			doll.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
	}

	/**
	 * 音波攻击决策：近战/射手模式下有目标时被动蓄力发射音波。
	 * 无距离限制——只要锁定目标就蓄力，冷却期间不蓄力。蓄力 1.5 秒后发射：
	 * 音爆音效 + 粒子 + 拉扯目标 + 音波伤害。
	 */
	@Override
	public void tickCombat(DollEntity doll) {
		if (!(doll.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (sonicCooldown > 0) {
			sonicCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = doll.getCombatTarget();
		if (target == null || !target.isAlive() || target.isRemoved()) {
			sonicChargeTicks = -1;
			return;
		}

		// 开始蓄力
		if (sonicChargeTicks < 0) {
			sonicChargeTicks = 0;
			serverLevel.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
				SoundEvents.WARDEN_SONIC_CHARGE, doll.getSoundSource(), 3.0f, 1.0f);
		}

		// 蓄力中
		sonicChargeTicks++;
		if (sonicChargeTicks >= DollEntity.SONIC_CHARGE_TICKS) {
			fireSonicBoom(serverLevel, doll, target);
			sonicChargeTicks = -1;
			sonicCooldown = DollEntity.SONIC_COOLDOWN_TICKS;
		}
	}

	/**
	 * 发射音波攻击：粒子 + 拉扯目标 + 伤害。
	 * 音效（蓄力阶段 WARDEN_SONIC_CHARGE）已在蓄力开始时播放，
	 * 这里补发射瞬间的 WARDEN_SONIC_BOOM 音效。
	 */
	private void fireSonicBoom(ServerLevel serverLevel, DollEntity doll, LivingEntity target) {
		double dx = doll.getX() - target.getX();
		double dz = doll.getZ() - target.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 0.5) return;

		// 音爆音效（发射瞬间，在目标位置播放）
		serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.WARDEN_SONIC_BOOM, doll.getSoundSource(), 3.0f, 1.0f);

		// 粒子：从人偶到目标画一条音波线
		double stepX = dx / 10.0;
		double stepZ = dz / 10.0;
		for (int i = 0; i < 10; i++) {
			double px = target.getX() + stepX * i;
			double pz = target.getZ() + stepZ * i;
			serverLevel.sendParticles(
				ParticleTypes.SONIC_BOOM,
				px, target.getY() + 0.5, pz,
				1, 0, 0, 0, 0);
		}
		// 目标位置额外爆一下
		serverLevel.sendParticles(
			ParticleTypes.SONIC_BOOM,
			target.getX(), target.getY() + 0.5, target.getZ(),
			5, 0.3, 0.3, 0.3, 0);

		// 拉扯目标：向人偶方向拉近
		double pull = distance * DollEntity.SONIC_PULL_STRENGTH;
		target.setDeltaMovement(
			dx / distance * pull,
			0.3,
			dz / distance * pull
		);
		target.hurtMarked = true;

		// 造成伤害（穿甲音波）
		target.hurtServer(serverLevel, doll.damageSources().sonicBoom(doll), DollEntity.SONIC_BOOM_DAMAGE);

		// 音波命中后同样补设 lastHurtByPlayer，使经验/稀有掉落正常
		if (doll.getOwnerUuid() != null) {
			target.setLastHurtByPlayer(doll.getOwnerUuid(), 100);
		}

		// 施加缓慢 V（100 tick = 5 秒，覆盖到下次音波冷却结束）
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4));
	}
}
