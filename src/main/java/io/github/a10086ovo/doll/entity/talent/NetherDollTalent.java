package io.github.a10086ovo.doll.entity.talent;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 下界人偶天赋（原 DollEntity 的 applyNetherPacifyAura / applyNetherBurnAura 迁入）。
 * <p>
 * 安抚光环：每 20 tick、半径 16 格清理下界生物对本体/主人的遗留仇恨；
 * 新索敌拦截由 {@code MobMixin}（DollEntity.isNetherMobType）负责。
 * 灼烧光环：每 20 tick、半径 16 格内主人持续获得抗火（契合下界主题）。
 */
public class NetherDollTalent extends SpecialDollTalent {

	private int netherPacifyCooldown = 0;
	private int netherBurnCooldown = 0;
	private int fireballCooldown = 0;    // 烈焰弹发射后冷却计数

	public NetherDollTalent() {
		super(true); // 抗火（常驻）
	}

	/** 安抚光环——清理遗留仇恨（索敌层拦截见 MobMixin / DollEntity.isNetherDollProtected）。 */
	private void pacifyAura(DollEntity doll) {
		if (netherPacifyCooldown-- > 0) {
			return;
		}
		netherPacifyCooldown = 20; // 每 20 tick（1 秒）清理遗留仇恨
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
			if (!DollEntity.isNetherMobType(mob.getType())) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == doll || (owner != null && target == owner)) {
				// NeutralMob（僵尸猪灵）：清除持久仇恨
				if (mob instanceof NeutralMob neutralMob) {
					neutralMob.stopBeingAngry();
				}
				// Piglin/PiglinBrute：清除 Brain 愤怒记忆
				mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
				mob.getBrain().eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
				// 兜底
				mob.setTarget(null);
			}
		}
	}

	/** 灼烧光环：范围内主人持续获得抗火。 */
	private void burnAura(DollEntity doll) {
		if (netherBurnCooldown-- > 0) {
			return;
		}
		netherBurnCooldown = 20;
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
			return;
		}
		owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
	}

	@Override
	public void tickAura(DollEntity doll) {
		pacifyAura(doll);
		burnAura(doll);
	}

	/**
	 * 烈焰弹攻击决策（原 DollEntity.handleNetherFireball 迁入）：
	 * 近战/射手模式下有目标时自动发射 WitherSkull，3 秒冷却，弹道投射物可被遮挡。
	 */
	@Override
	public void tickCombat(DollEntity doll) {
		if (!(doll.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (fireballCooldown > 0) {
			fireballCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = doll.getCombatTarget();
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}

		fireWitherSkull(serverLevel, doll, target);
		fireballCooldown = DollEntity.FIREBALL_COOLDOWN_TICKS;
	}

	/**
	 * 发射凋灵骷髅头颅弹（原 DollEntity.fireWitherSkull 迁入）：创建 WitherSkull 并朝目标方向射出。
	 * <p>
 * 使用 WitherSkull 而非 SmallFireball——WitherSkull 有专属投射物渲染器，
 * SmallFireball 只有 item 级渲染。
	 * <p>
	 * WitherSkull 的 onHit 爆炸破坏由 WitherSkullMixin 的 @Redirect 禁用；
	 * onHitEntity 硬编码 8.0f 伤害由 WitherSkullMixin 替换（下界烈焰弹与末影弹直击共用
	 * {@code FIREBALL_DAMAGE} 档）；凋零效果由 WitherSkullMixin 替换为燃烧 5 秒。
	 */
	private void fireWitherSkull(ServerLevel serverLevel, DollEntity doll, LivingEntity target) {
		// 方向向量：从人偶眼睛高度指向目标中心
		double dx = target.getX() - doll.getX();
		double dy = (target.getY() + 0.5) - (doll.getY() + doll.getEyeHeight());
		double dz = target.getZ() - doll.getZ();
		Vec3 direction = new Vec3(dx, dy, dz).normalize();

		WitherSkull skull = new WitherSkull(serverLevel, doll, direction);
		skull.setPos(doll.getX(), doll.getY() + doll.getEyeHeight(), doll.getZ());

		serverLevel.addFreshEntity(skull);

		// 发射音效（烈焰人发射火球的音效，与下界主题一致）
		serverLevel.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
			SoundEvents.BLAZE_SHOOT, doll.getSoundSource(), 1.0f, 1.0f);
	}
}
