package io.github.a10086ovo.doll.entity.talent;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.item.SeaArmorItem;

/**
 * 海洋人偶天赋（原 DollEntity 的 applySeaPacifyAura / applySeaPlayerAura / handleSeaLaser / fireSeaLaser 迁入）。
 * <p>
 * 安抚光环：每 20 tick、半径 16 格清理敌对海洋生物（守卫者/远古守卫者/溺尸）
 * 对本体/主人的遗留仇恨（新索敌拦截见 MobMixin / DollEntity.isSeaDollProtected）。
 * 主人增益光环：每 20 tick、半径 16 格——水下呼吸、清除挖掘疲劳、
 * 水下挖掘属性 +0.8（真正移除水中 ÷5 惩罚，仅主人受益）。
 * <p>
 * 战斗技能（tickCombat）：海洋激光——对齐原版守卫者蓄力 → 视线内 hitscan ≥ 魔法伤害（绕护甲）
 * （非投射物，蓄力 30t + 冷却 20t，高频中伤法师定位）。
 */
public class SeaDollTalent extends SpecialDollTalent {

	// 水中挖掘加速：26.2 由属性 SUBMERGED_MINING_SPEED 决定（默认 0.2 = 水中 ÷5）。
	// 给主人 +0.8 抬到 1.0 = 水中挖掘与陆地一致（真正移除惩罚，非"高等级急迫" hack）。
	// transient 修饰（不写盘），主人离开光环范围时移除（同 GuideDollTalent 的护甲修饰）。
	private static final Identifier SEA_SUBMERGED_MOD_ID =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "sea_submerged_mining_bonus");
	private static final AttributeModifier SEA_SUBMERGED_MOD =
		new AttributeModifier(SEA_SUBMERGED_MOD_ID, 0.8, AttributeModifier.Operation.ADD_VALUE);

	private int seaPacifyCooldown = 0;
	private int seaPlayerAuraCooldown = 0;
	private int laserChargeTicks = -1;  // -1 = 不在蓄力
	private int laserCooldown = 0;      // 发射后冷却计数

	public SeaDollTalent() {
		super(true); // 抗火（常驻）
	}

	/** 安抚光环——清理遗留仇恨。 */
	private void pacifyAura(DollEntity doll) {
		if (seaPacifyCooldown-- > 0) {
			return;
		}
		seaPacifyCooldown = 20; // 每 20 tick（1 秒）清理遗留仇恨
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 16.0;
		AABB box = DollTalent.auraBox(center, radius);
		Player owner = doll.getOwnerPlayer();
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && mob.getTarget() != null
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			if (!DollEntity.isSeaMobType(mob.getType())) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == doll || (owner != null && target == owner)) {
				mob.setTarget(null);
			}
		}
	}

	/** 主人增益光环（仅主人受益，联机时朋友不享受）。 */
	private void playerAura(DollEntity doll) {
		if (seaPlayerAuraCooldown-- > 0) {
			return;
		}
		seaPlayerAuraCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (doll.level().isClientSide()) {
			return;
		}
		Player owner = doll.getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 16.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			// 主人不在光环内：移除先前挂上的"水下挖掘加速"属性修饰，恢复原版水中慢速
			removeSeaSubmergedMod(owner);
			return;
		}
		// 水下呼吸：只要在主人的 16 格内就持续给予
		owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false));
		// 清除主人身上的挖掘疲劳（MINING_FATIGUE）——海洋人偶庇护主人，避免减速/虚弱采集带来的手感拖沓
		if (owner.hasEffect(MobEffects.MINING_FATIGUE)) {
			owner.removeEffect(MobEffects.MINING_FATIGUE);
		}
		// 真正移除水中挖掘惩罚：给主人挂上 SUBMERGED_MINING_SPEED +0.8（0.2→1.0），
		// 使水中挖掘速度与陆地一致；仅对主人生效、无需是否身处水中的判断（该属性只在水中判定时生效）。
		applySeaSubmergedMod(owner);
	}

	/** 给主人挂上"水下挖掘加速"属性修饰（幂等）。 */
	private static void applySeaSubmergedMod(Player owner) {
		AttributeInstance submerged = owner.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
		if (submerged != null && !submerged.hasModifier(SEA_SUBMERGED_MOD_ID)) {
			submerged.addTransientModifier(SEA_SUBMERGED_MOD);
		}
	}

	/** 移除主人身上的"水下挖掘加速"属性修饰（幂等）。 */
	private static void removeSeaSubmergedMod(Player owner) {
		AttributeInstance submerged = owner.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
		if (submerged != null && submerged.hasModifier(SEA_SUBMERGED_MOD_ID)) {
			submerged.removeModifier(SEA_SUBMERGED_MOD_ID);
		}
	}

	@Override
	public void tickAura(DollEntity doll) {
		pacifyAura(doll);
		playerAura(doll);
	}

	/**
	 * 海洋激光攻击决策（原 DollEntity.handleSeaLaser 迁入）：
	 * 近战/射手模式下锁定目标后自动蓄力发射，对齐原版守卫者——蓄力期间需维持视线，中断则重置。
	 */
	@Override
	public void tickCombat(DollEntity doll) {
		if (!(doll.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (laserCooldown > 0) {
			laserCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = doll.getCombatTarget();
		if (target == null || !target.isAlive() || target.isRemoved()) {
			laserChargeTicks = -1;
			return;
		}

		// 射程检查
		if (doll.distanceToSqr(target) > DollEntity.LASER_RANGE_SQR) {
			laserChargeTicks = -1;
			return;
		}

		// 视线检查：蓄力期间必须维持视线，断了就重置（原版守卫者核心机制）
		if (!doll.hasLineOfSight(target)) {
			laserChargeTicks = -1;
			return;
		}

		// 开始蓄力
		if (laserChargeTicks < 0) {
			laserChargeTicks = 0;
			// 蓄力开始：渐强压迫感音效（WARDEN_SONIC_CHARGE 音量 2.5），给玩家"正在蓄力"的张力预告
			serverLevel.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
				SoundEvents.WARDEN_SONIC_CHARGE, doll.getSoundSource(), 2.5f, 1.0f);
		}

		// 蓄力中：每 2 tick 画一条 eye→target 瞄准线（鹦鹉螺粒子），并在目标处加大电火花迸发，
		// 让玩家清晰看到"正在锁定"，补足原版守卫者蓄力阶段缺乏的视觉张力。
		laserChargeTicks++;
		if (laserChargeTicks % 2 == 0) {
			double ex = doll.getX();
			double ey = doll.getY() + doll.getEyeHeight();
			double ez = doll.getZ();
			double tx = target.getX();
			double ty = target.getY() + target.getEyeHeight();
			double tz = target.getZ();
			int lineSteps = 12;
			for (int i = 0; i <= lineSteps; i++) {
				double t = i / (double) lineSteps;
				serverLevel.sendParticles(ParticleTypes.NAUTILUS,
					ex + (tx - ex) * t, ey + (ty - ey) * t, ez + (tz - ez) * t,
					1, 0.04, 0.04, 0.04, 0.0);
			}
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				tx, ty, tz, 6, 0.4, 0.4, 0.4, 0.08);
		}

		// 蓄力完成
		if (laserChargeTicks >= DollEntity.LASER_CHARGE_TICKS) {
			fireSeaLaser(serverLevel, doll, target);
			laserChargeTicks = -1;
			laserCooldown = DollEntity.LASER_COOLDOWN_TICKS;
		}
	}

	/**
	 * 发射激光：粒子光束 + 魔法伤害（原 DollEntity.fireSeaLaser 迁入）。
	 * <p>
	 * hitscan 机制——无投射物，视线内必定命中（和幽匿音波一致）。
	 * 伤害类型为 indirectMagic，绕过护甲（原版守卫者行为）。
	 * 粒子用 NAUTILUS（海洋主题）画光束线，ELECTRIC_SPARK 在目标位置爆开。
	 */
	private void fireSeaLaser(ServerLevel serverLevel, DollEntity doll, LivingEntity target) {
		double dx = target.getX() - doll.getX();
		double dy = (target.getY() + target.getEyeHeight()) - (doll.getY() + doll.getEyeHeight());
		double dz = target.getZ() - doll.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance < 0.5) return;

		// 光束粒子：从人偶眼睛到目标画一条 NAUTILUS 粒子线（每步 3 颗 + END_ROD 亮芯），
		// 加密并加亮芯后肉眼清晰可见，给发射瞬间充足张力。
		int steps = Math.max(8, (int) (distance * 1.5));
		double stepX = dx / steps;
		double stepY = dy / steps;
		double stepZ = dz / steps;
		for (int i = 0; i < steps; i++) {
			double px = doll.getX() + stepX * i;
			double py = doll.getY() + doll.getEyeHeight() + stepY * i;
			double pz = doll.getZ() + stepZ * i;
			serverLevel.sendParticles(ParticleTypes.NAUTILUS, px, py, pz, 3, 0.05, 0.05, 0.05, 0.0);
			serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
		}

		// 目标位置电火花大迸发
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
			target.getX(), target.getY() + target.getEyeHeight(), target.getZ(),
			16, 0.5, 0.5, 0.5, 0.12);

		// 发射瞬间补响亮音效（原版守卫者激光命中的"啪"一声，vol 3.0 给张力收束）
		serverLevel.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
			SoundEvents.GUARDIAN_ATTACK, doll.getSoundSource(), 3.0f, 1.0f);

		// 造成魔法伤害（绕护甲，对齐原版守卫者 indirectMagic）
		// 海洋人偶甲——海洋人偶穿戴海洋甲时激光伤害按件数提升（每件 +10%，最高 +40%）；非海洋人偶不受影响
		float laserDamage = DollEntity.LASER_DAMAGE;
		if (doll.isSeaDoll()) {
			int pieces = SeaArmorItem.countSeaArmor(doll);
			if (pieces > 0) {
				laserDamage = (float) (laserDamage * (1.0 + 0.10 * pieces));
			}
		}
		target.hurtServer(serverLevel,
			doll.damageSources().indirectMagic(doll, doll), laserDamage);

		// 补设 lastHurtByPlayer，使经验/稀有掉落正常
		if (doll.getOwnerUuid() != null) {
			target.setLastHurtByPlayer(doll.getOwnerUuid(), 100);
		}
	}
}