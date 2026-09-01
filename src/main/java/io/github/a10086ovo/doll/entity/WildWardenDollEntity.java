package io.github.a10086ovo.doll.entity;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.minecraft.world.phys.Vec3;

/**
 * 野生幽匿人偶 —— 独立于 DollEntity 体系的敌对生物（BOSS）。
 * 继承 Monster，使用原版 Goal 系统实现寻路/寻敌。
 * 300 HP / 10 攻击力 / 移动速度 0.3（仿监守者）。
 * 弱再生 I（boss 禁强再生，仅 I 级）；满抗击退；免疫火焰；无抗性提升（平衡重做已删）。
 * 被动音波：6 伤害 / 7 秒冷却 / 限 32 格。
 * 被攻击后反击，创造/旁观模式不锁定。
 * 60% 概率掉落头颅。
 * 出场动画：从幽匿神龛中丝滑上升 2 格，伴随火焰烟雾粒子 + 蓝色渐变拖尾，然后降落。
 * <p>
 * 战斗机制：固定监守者速度追击，超出近战范围时蓄力远程音波拉扯。
 */
public class WildWardenDollEntity extends Monster {

	private static final double WILD_MAX_HEALTH = 300.0;
	private static final double WILD_ATTACK_DAMAGE = 10.0;
	private static final double WILD_BASE_SPEED = 0.3;        // 监守者速度
	private static final double WILD_FOLLOW_RANGE = 32.0;
	private static final int ATTACK_COOLDOWN = 20;
	private static final double ATTACK_RANGE_SQR = 2.5 * 2.5;

	// ---- 音波攻击 ----
	private static final int OUT_OF_RANGE_THRESHOLD = 80;   // 超出近战范围 4 秒开始蓄力
	private static final int SONIC_CHARGE_TICKS = 30;        // 蓄力前摇 1.5 秒（对齐音效长度）
	private static final float SONIC_BOOM_DAMAGE = 6.0f;    // 音波伤害（平衡重做：5→6）
	private static final double SONIC_PULL_STRENGTH = 0.15; // 拉扯强度（距离比例）
	private static final int SONIC_COOLDOWN_TICKS = 140;    // 发射后冷却 7 秒（平衡重做：限频）

	private int outOfRangeTicks = 0;
	private int sonicChargeTicks = -1;  // -1 = 不在蓄力
	private int sonicCooldown = 0;      // 发射后冷却计数（7 秒）

	// ---- 出场动画（从神龛中丝滑上升 + 降落） ----
	private static final int EMERGE_RISE_TICKS = 100;      // 5 秒上升
	private static final int EMERGE_DESCEND_TICKS = 30;     // 1.5 秒降落
	private static final double EMERGE_HEIGHT = 2.0;        // 上升 2 格
	private static final String NBT_HAS_EMERGED = "WildWardenHasEmerged";

	private boolean emerging = true;
	private int emergeTicks = 0;
	private double emergeStartY;
	private boolean hasEmerged = false;

	private int attackTimer = 0;

	public WildWardenDollEntity(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createWildDollAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, WILD_MAX_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, WILD_ATTACK_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, WILD_BASE_SPEED)
			.add(Attributes.FOLLOW_RANGE, WILD_FOLLOW_RANGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);  // 满抗击退，仿监守者
	}

	@Override
	public boolean fireImmune() {
		return true; // 免疫火焰和岩浆伤害，仿监守者
	}

	/**
	 * 26.2 骑乘定位：Monster 链路（Monster → LivingEntity → Entity）未设置 VEHICLE 附件点，
	 * 回退到 AT_FEET = (0, 0, 0)，导致骑船/矿车时悬浮在载具上方约 0.6 格。
	 * 覆写为 (0, 0.6, 0)（与 Avatar/Player 一致），使坐姿高度正常。
	 */
	@Override
	public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
		return new Vec3(0.0, 0.6, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new HurtByTargetGoal(this));
		this.goalSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 0, true, false,
			(target, level) -> {
				Player p = (Player) target;
				return !p.isCreative() && !p.isSpectator();
			}));
		// 攻击所有非创造/旁观生物（仿监守者，排除同类）
		this.goalSelector.addGoal(3, new NearestAttackableTargetGoal<LivingEntity>(this, LivingEntity.class, 0, true, false,
			(target, level) -> {
				if (target instanceof Player player) {
					return !player.isCreative() && !player.isSpectator();
				}
				return !(target instanceof WildWardenDollEntity);
			}));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
	}

	@Override
	public void tick() {
		// 出场动画
		if (emerging && !hasEmerged) {
			tickEmerging();
			return;
		}
		if (emerging && hasEmerged) {
			emerging = false;
		}

		// 攻击冷却
		if (attackTimer > 0) attackTimer--;

		// 弱再生 I（boss 禁强再生，仅 I 级；用官方无限时长常量，跨版本语义稳定）
		if (!this.level().isClientSide() && !this.hasEffect(MobEffects.REGENERATION)) {
			this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}

		super.tick();

		// 战斗 AI：音波攻击（仅服务端，限 32 格内）
		if (!this.level().isClientSide() && this.getTarget() instanceof Player targetPlayer) {
			handleSonicBoom(targetPlayer);
		}
	}

	// ========== 音波攻击 ==========

	/**
	 * 当玩家处于近战范围外时累计计数，达到阈值进入蓄力阶段。
	 * 蓄力 1 秒后发射音波，期间有 WARDEN_SONIC_CHARGE 音效提示。
	 */
	private void handleSonicBoom(Player target) {
		double distSqr = this.distanceToSqr(target);

		// 32 格范围上限（不可无范围，否则全局 DOT）
		if (distSqr > 32.0 * 32.0) {
			outOfRangeTicks = 0;
			sonicChargeTicks = -1;
			return;
		}

		// 发射后冷却（7 秒）
		if (sonicCooldown > 0) {
			sonicCooldown--;
			return;
		}

		if (distSqr > ATTACK_RANGE_SQR) {
			outOfRangeTicks++;

			// 达到阈值，开始蓄力
			if (outOfRangeTicks >= OUT_OF_RANGE_THRESHOLD && sonicChargeTicks < 0) {
				sonicChargeTicks = 0;
				if (this.level() instanceof ServerLevel serverLevel) {
					serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.WARDEN_SONIC_CHARGE, this.getSoundSource(), 3.0f, 1.0f);
				}
			}

			// 蓄力中
			if (sonicChargeTicks >= 0) {
				sonicChargeTicks++;
				if (sonicChargeTicks >= SONIC_CHARGE_TICKS) {
					fireSonicBoom(target);
					sonicChargeTicks = -1;
					outOfRangeTicks = 0;
					sonicCooldown = SONIC_COOLDOWN_TICKS;
				}
			}
		} else {
			outOfRangeTicks = 0;
			sonicChargeTicks = -1;
		}
	}

	/**
	 * 发射音波攻击：粒子 + 拉扯玩家 + 伤害。
	 * 音效已在蓄力阶段播放，这里只做实际效果。
	 */
	private void fireSonicBoom(Player target) {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		double dx = this.getX() - target.getX();
		double dz = this.getZ() - target.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 0.5) return;

		// 音爆音效（发射瞬间）
		serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.WARDEN_SONIC_BOOM, this.getSoundSource(), 3.0f, 1.0f);

		// 粒子：从人偶到玩家画一条音波线
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
		// 玩家位置额外爆一下
		serverLevel.sendParticles(
			ParticleTypes.SONIC_BOOM,
			target.getX(), target.getY() + 0.5, target.getZ(),
			5, 0.3, 0.3, 0.3, 0);

		// 拉扯玩家：向人偶方向拉一半距离
		double pull = distance * SONIC_PULL_STRENGTH;
		target.setDeltaMovement(
			dx / distance * pull,
			0.3,
			dz / distance * pull
		);
		target.hurtMarked = true;

		// 造成伤害（穿甲音波，对标原版 Warden sonicBoom）
		target.hurtServer(serverLevel, this.damageSources().sonicBoom(this), SONIC_BOOM_DAMAGE);
	}

	// ========== 出场动画 ==========

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(NBT_HAS_EMERGED, hasEmerged);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		hasEmerged = input.getBooleanOr(NBT_HAS_EMERGED, false);
		if (hasEmerged) {
			emerging = false;
		}
	}

	private void tickEmerging() {
		if (emergeTicks == 0) {
			emergeStartY = this.getY();
			this.setNoGravity(true);
			this.setInvulnerable(true);
			this.playSound(SoundEvents.WARDEN_EMERGE, 3.0f, 1.0f);
			if (this.level() instanceof ServerLevel serverLevel) {
				spawnInitialBurst(serverLevel);
			}
		}

		emergeTicks++;
		int totalTicks = EMERGE_RISE_TICKS + EMERGE_DESCEND_TICKS;

		if (this.level() instanceof ServerLevel serverLevel) {
			if (emergeTicks <= EMERGE_RISE_TICKS) {
				double t = (double) emergeTicks / EMERGE_RISE_TICKS;
				double smoothT = easeInOutCubic(t);
				double currentY = emergeStartY + EMERGE_HEIGHT * smoothT;
				this.setPos(this.getX(), currentY, this.getZ());
				spawnTrailParticles(serverLevel, (float) t);

				if (emergeTicks % 20 == 0) {
					serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.WARDEN_DIG, this.getSoundSource(), 1.0f, 1.0f);
				}
			} else {
				int descendTick = emergeTicks - EMERGE_RISE_TICKS;
				double t = (double) descendTick / EMERGE_DESCEND_TICKS;
				double smoothT = smoothstep(t);
				double currentY = (emergeStartY + EMERGE_HEIGHT) - EMERGE_HEIGHT * smoothT;
				this.setPos(this.getX(), currentY, this.getZ());
				spawnTrailParticles(serverLevel, 1.0f - (float) t * 0.5f);
			}
		}

		this.setDeltaMovement(0, 0, 0);
		this.navigation.stop();

		if (emergeTicks >= totalTicks) {
			emerging = false;
			hasEmerged = true;
			this.setNoGravity(false);
			this.setInvulnerable(false);
			this.setPos(this.getX(), emergeStartY, this.getZ());
			this.playSound(SoundEvents.WARDEN_ROAR, 3.0f, 1.0f);
		}

		super.tick();
	}

	// ========== 缓动函数 ==========

	private static double easeInOutCubic(double t) {
		return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	private static double smoothstep(double t) {
		return t * t * (3 - 2 * t);
	}

	// ========== 粒子特效 ==========

	private void spawnInitialBurst(ServerLevel level) {
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();

		for (int i = 0; i < 24; i++) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE,
				x + (this.random.nextDouble() - 0.5) * 3.0,
				y + this.random.nextDouble() * 1.0,
				z + (this.random.nextDouble() - 0.5) * 3.0,
				1, 0, 0.1, 0, 0.05);
		}
		for (int i = 0; i < 18; i++) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
				x + (this.random.nextDouble() - 0.5) * 2.5,
				y + this.random.nextDouble() * 0.5,
				z + (this.random.nextDouble() - 0.5) * 2.5,
				1, 0, 0.1, 0, 0.03);
		}
		for (int i = 0; i < 12; i++) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
				x + (this.random.nextDouble() - 0.5) * 1.5,
				y,
				z + (this.random.nextDouble() - 0.5) * 1.5,
				1, 0, 0.3, 0, 0.01);
		}
	}

	private void spawnTrailParticles(ServerLevel level, float progress) {
		double x = this.getX();
		double y = this.getY() + 2.4;
		double z = this.getZ();

		float darkRatio = 1.0f - Math.min(progress * 1.5f, 1.0f);
		float lightRatio = Math.min(progress * 1.5f, 1.0f);

		if (this.random.nextFloat() < darkRatio) {
			level.sendParticles(ParticleTypes.SCULK_SOUL,
				x + (this.random.nextDouble() - 0.5) * 0.8,
				y + (this.random.nextDouble() - 0.5) * 0.6,
				z + (this.random.nextDouble() - 0.5) * 0.8,
				1, 0, 0, 0, 0.01);
		}
		if (this.random.nextFloat() < lightRatio) {
			level.sendParticles(ParticleTypes.END_ROD,
				x + (this.random.nextDouble() - 0.5) * 0.8,
				y + (this.random.nextDouble() - 0.5) * 0.6,
				z + (this.random.nextDouble() - 0.5) * 0.8,
				1, 0, 0, 0, 0.015);
		}
		if (this.random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
				x + (this.random.nextDouble() - 0.5) * 1.0,
				y + 0.3,
				z + (this.random.nextDouble() - 0.5) * 1.0,
				1, 0, 0, 0, 0.01);
		}
		if (progress < 0.5f) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
				x + (this.random.nextDouble() - 0.5) * 0.5,
				y - 0.5,
				z + (this.random.nextDouble() - 0.5) * 0.5,
				1, 0, 0.05, 0, 0.005);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.swing(InteractionHand.MAIN_HAND);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
			float baseChance = 0.6f;  // 平衡重做：头颅掉落率 40%→60%
			int lootingLevel = 0;
			Entity killer = source.getEntity();
			if (killer instanceof LivingEntity livingEntity) {
				lootingLevel = EnchantmentHelper.getEnchantmentLevel(
					serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(Enchantments.LOOTING),
					livingEntity);
			}
			float finalChance = baseChance + lootingLevel * 0.1f;
			if (this.random.nextFloat() < finalChance) {
				this.spawnAtLocation(serverLevel, DollMod.WARDEN_DOLL_HEAD.getDefaultInstance());
			}
		}
		super.die(source);
	}
}