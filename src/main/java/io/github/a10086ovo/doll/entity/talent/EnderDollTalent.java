package io.github.a10086ovo.doll.entity.talent;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.WildWardenDollEntity;
import io.github.a10086ovo.doll.item.EnderAxeItem;

/**
 * 末影人偶天赋。
 * <p>
 * 常驻药效基线（恢复 IV + 抗性 II，无抗火）由 {@link SpecialDollTalent} 提供；
 * 战斗天赋（末影弹 / 原地处决）于 Wave 2B 从 DollEntity 迁入，由 {@link #tickCombat} 分发。
 * <p>
 * 安抚光环：每 20 tick、半径 16 格清理末地敌对生物（末影人/末影螨/潜影贝，
 * 见 {@link DollEntity#isEndMobType}）对本体/主人的遗留仇恨；新索敌拦截由
 * {@code MobMixin}（canAttack → {@link DollEntity#isEnderDollProtected}）负责。
 * 末影龙为 Boss 战保留，不在安抚之列。
 */
public class EnderDollTalent extends SpecialDollTalent {

	private int enderPacifyCooldown = 0;

	// ---- 末影弹战斗状态（迁自 DollEntity，实例状态每只人偶独立） ----
	private int breathCooldown = 0;     // 末影弹发射冷却计数

	public EnderDollTalent() {
		super(false);
	}

	/** 安抚光环——清理末地生物遗留仇恨（索敌层拦截见 MobMixin / DollEntity.isEnderDollProtected）。 */
	private void pacifyAura(DollEntity doll) {
		if (enderPacifyCooldown-- > 0) {
			return;
		}
		enderPacifyCooldown = 20; // 每 20 tick（1 秒）清理遗留仇恨
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
			if (!DollEntity.isEndMobType(mob.getType())) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == doll || (owner != null && target == owner)) {
				// 末影人（NeutralMob）：清除持久仇恨
				if (mob instanceof NeutralMob neutralMob) {
					neutralMob.stopBeingAngry();
				}
				// 潜影贝/末影螨等：清除 Brain 愤怒记忆
				mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
				mob.getBrain().eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
				// 兜底
				mob.setTarget(null);
			}
		}
	}

	@Override
	public void tickAura(DollEntity doll) {
		pacifyAura(doll);
	}

	/**
	 * 战斗天赋分发（近战/射手模式由 DollEntity.tickSpecialAttacks 调用）：末影弹发射 + 原地致命处决。
	 */
	@Override
	public void tickCombat(DollEntity doll) {
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		handleEnderBreath(doll, serverLevel);
		handleEnderExecute(doll, serverLevel);
	}

	/**
	 * 末影弹发射决策：近战/射手模式下有目标时自动发射 WitherSkull（和下界人偶统一投射物）。
	 * 命中后由 WitherSkullMixin 做命中点 3 格范围爆破，范围伤害与下界烈焰弹一致。3 秒冷却，弹道可被遮挡。
	 */
	private void handleEnderBreath(DollEntity doll, ServerLevel serverLevel) {
		if (breathCooldown > 0) {
			breathCooldown--;
			return;
		}
		LivingEntity target = doll.getCombatTarget();
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}
		fireEnderSkull(doll, serverLevel, target);
		breathCooldown = DollEntity.BREATH_COOLDOWN_TICKS;
	}

	/**
	 * 发射末影弹：创建 WitherSkull 并朝目标方向射出。
	 * 命中行为由 WitherSkullMixin 按 owner 变体区分：ENDER = 直击 20 + 命中点 3 格 AoE 20，不点燃；
	 * 渲染端按变体区分贴图（WitherSkullRendererMixin：ENDER→ender_doll.png）。
	 */
	private void fireEnderSkull(DollEntity doll, ServerLevel serverLevel, LivingEntity target) {
		double dx = target.getX() - doll.getX();
		double dy = (target.getY() + 0.5) - (doll.getY() + doll.getEyeHeight());
		double dz = target.getZ() - doll.getZ();
		Vec3 direction = new Vec3(dx, dy, dz).normalize();

		WitherSkull skull = new WitherSkull(serverLevel, doll, direction);
		skull.setPos(doll.getX(), doll.getY() + doll.getEyeHeight(), doll.getZ());

		serverLevel.addFreshEntity(skull);

		// 烈焰人发射音效（和下界人偶统一）
		serverLevel.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
			SoundEvents.BLAZE_SHOOT, doll.getSoundSource(), 1.0f, 1.0f);
	}

	/**
	 * 原地致命处决：近战/射手模式下目标血量 ≤ 斩杀线时**原地立即斩杀，无冷却**（天赋向补刀）。
	 * <p>
	 * 相比旧版已去掉「瞬移到敌后 → 停顿 → 斩杀 → 瞬移回归」的瞬移演出（闪避瞬移为受击被动，不受影响）。
	 * 自制 BOSS 野生幽匿人偶（终点挑战）豁免。
	 */
	private void handleEnderExecute(DollEntity doll, ServerLevel serverLevel) {
		LivingEntity target = doll.getCombatTarget();
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}
		// 自制 BOSS（野生幽匿人偶）豁免，保留终局挑战
		if (target instanceof WildWardenDollEntity) {
			return;
		}
		// 血量阈值判定：持末影斧时取最大值，否则基础 25%
		ItemStack mainHand = doll.getItemBySlot(EquipmentSlot.MAINHAND);
		float threshold = mainHand.getItem() instanceof EnderAxeItem
			? DollEntity.EXECUTE_HEALTH_THRESHOLD_AXE : DollEntity.EXECUTE_HEALTH_THRESHOLD;
		float healthRatio = target.getHealth() / target.getMaxHealth();
		if (healthRatio > threshold) {
			return;
		}

		// 原地立即斩杀（不做瞬移）。用有限值斩杀（Float.MAX_VALUE 经伤害链路可能溢出为 Inf/NaN）
		target.hurtServer(serverLevel, doll.damageSources().mobAttack(doll), target.getMaxHealth() * 10f);
		serverLevel.sendParticles(ParticleTypes.CRIT,
			target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			30, 0.4, 0.5, 0.4, 0.05);
		serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.2f);
	}
}