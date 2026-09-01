package io.github.a10086ovo.doll.entity;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 投掷末影斧实体 —— 复刻 {@link net.minecraft.world.entity.projectile.arrow.ThrownTrident} 的行为。
 * <p>
 * 忠诚附魔回归：投掷命中后飞回持有者（支持 Player 和 DollEntity）。
 * 玩家投掷时附带 30% 斩杀效果。
 * 实现 {@link ItemSupplier} 供 {@link net.minecraft.client.renderer.entity.ThrownItemRenderer} 渲染。
 */
public class ThrownEnderAxe extends AbstractArrow implements ItemSupplier {

	private static final EntityDataAccessor<Byte> ID_LOYALTY =
		SynchedEntityData.defineId(ThrownEnderAxe.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> ID_FOIL =
		SynchedEntityData.defineId(ThrownEnderAxe.class, EntityDataSerializers.BOOLEAN);

	private static final float BASE_DAMAGE = 9.0f;
	private static final float EXECUTE_THRESHOLD = 0.3f;

	private boolean dealtDamage;

	public ThrownEnderAxe(EntityType<? extends ThrownEnderAxe> type, Level level) {
		super(type, level);
	}

	public ThrownEnderAxe(ServerLevel level, LivingEntity shooter, ItemStack axeStack) {
		super(DollMod.THROWN_ENDER_AXE_ENTITY, shooter, level, axeStack.copy(), axeStack.copy());
		setBaseDamage(BASE_DAMAGE);
		getEntityData().set(ID_LOYALTY, getLoyaltyFromItem(axeStack));
		getEntityData().set(ID_FOIL, axeStack.hasFoil());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ID_LOYALTY, (byte) 0);
		builder.define(ID_FOIL, false);
	}

	// ===================== tick：忠诚回归 =====================

	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
		}

		Entity owner = this.getOwner();
		byte loyalty = getEntityData().get(ID_LOYALTY);

		if (loyalty > 0 && (this.dealtDamage || this.isNoPhysics()) && owner != null) {
			if (!isAcceptibleReturnOwner()) {
				if (this.level() instanceof ServerLevel serverLevel && this.pickup == Pickup.ALLOWED) {
					this.spawnAtLocation(serverLevel, this.getPickupItem(), 0.1f);
				}
				this.discard();
				return;
			}

		if (!(owner instanceof Player)) {
			// 非玩家持有者（如 DollEntity）
			Vec3 eyePos = owner.getEyePosition();
			double distSqr = this.distanceToSqr(eyePos.x, eyePos.y, eyePos.z);
			// 忠诚回归超时/超距保护：防止人偶跨维度/死亡后斧头无限追逐
			if (this.tickCount > 200 || distSqr > 64.0 * 64.0) {
				if (this.level() instanceof ServerLevel serverLevel && this.pickup == Pickup.ALLOWED) {
					this.spawnAtLocation(serverLevel, this.getPickupItem(), 0.1f);
				}
				this.discard();
				return;
			}
			if (distSqr < (owner.getBbWidth() + 1.0) * (owner.getBbWidth() + 1.0)) {
					if (owner instanceof DollEntity doll) {
						doll.addToDollInventory(this.getPickupItem());
					}
					this.discard();
					return;
				}
				this.setNoPhysics(true);
				Vec3 dir = owner.getEyePosition().subtract(this.position());
				this.setDeltaMovement(dir.scale(0.5));
				super.tick();
				return;
			}

			// 玩家持有者 —— 复刻 ThrownTrident 的回归移动
			this.setNoPhysics(true);
			Vec3 dir = owner.getEyePosition().subtract(this.position());
			this.setPos(this.getX() + dir.x * 0.015 * loyalty,
				this.getY() + dir.y * 0.015 * loyalty,
				this.getZ() + dir.z * 0.015 * loyalty);
			this.setDeltaMovement(dir.scale(0.05 * loyalty));
		}

		super.tick();
	}

	private boolean isAcceptibleReturnOwner() {
		Entity owner = this.getOwner();
		if (owner == null || !owner.isAlive()) {
			return false;
		}
		if (owner instanceof Player player && player.isSpectator()) {
			return false;
		}
		return true;
	}

	// ===================== 命中实体 =====================

	@Override
	protected void onHitEntity(EntityHitResult result) {
		Entity target = result.getEntity();
		float damage = BASE_DAMAGE;
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().trident(this, owner != null ? owner : this);

		boolean hitSucceeded;
		if (this.level() instanceof ServerLevel serverLevel) {
			damage = EnchantmentHelper.modifyDamage(serverLevel,
				this.getWeaponItem(), target, source, damage);
			hitSucceeded = target.hurtServer(serverLevel, source, damage);
		} else {
			hitSucceeded = target.hurtClient(source);
		}

		this.dealtDamage = true;

		if (hitSucceeded) {
			// 末影人免疫末影斧伤害（对齐原版末影人瞬移闪避投掷物的设定）
			Identifier tgtId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
			if (tgtId != null && tgtId.getPath().equals("enderman")) {
				return;
			}

			if (this.level() instanceof ServerLevel serverLevel) {
				EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(serverLevel,
					target, source, this.getWeaponItem(),
					item -> this.kill(serverLevel));
			}

			if (target instanceof LivingEntity livingTarget) {
				this.doKnockback(livingTarget, source);
				this.doPostHurtEffects(livingTarget);

				// 玩家投掷时附带 30% 斩杀
				if (owner instanceof Player && livingTarget.isAlive() && livingTarget.getHealth() > 0.0f) {
					float ratio = livingTarget.getHealth() / livingTarget.getMaxHealth();
					if (ratio <= EXECUTE_THRESHOLD) {
						// 用有限值斩杀：Float.MAX_VALUE 经伤害链路乘除可能溢出为 Infinity/NaN
						if (this.level() instanceof ServerLevel serverLevel) {
							livingTarget.hurtServer(serverLevel, source, livingTarget.getMaxHealth() * 10f);
						} else {
							livingTarget.hurtClient(source);
						}
					}
				}
			}
		}

		// 引雷（Channeling）：雷暴天气且目标头顶可见天空时，命中后召唤落雷（修复此前不触发 bug）
		Holder<Enchantment> channeling = this.level().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING);
		if (EnchantmentHelper.getItemEnchantmentLevel(channeling, this.getWeaponItem()) > 0
				&& this.level() instanceof ServerLevel serverLevel) {
			BlockPos blockPos = target.blockPosition();
			if (serverLevel.isThundering() && serverLevel.canSeeSky(blockPos)) {
				ResourceKey<EntityType<?>> lightningKey = ResourceKey.create(Registries.ENTITY_TYPE,
					Identifier.fromNamespaceAndPath("minecraft", "lightning_bolt"));
				EntityType<?> lightningType = this.level().registryAccess()
					.lookupOrThrow(Registries.ENTITY_TYPE).getOrThrow(lightningKey).value();
				LightningBolt lightning = (LightningBolt) lightningType.create(serverLevel, EntitySpawnReason.TRIGGERED);
				if (lightning != null) {
					lightning.setPos(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
					serverLevel.addFreshEntity(lightning);
				}
			}
		}
	}

	// ===================== 拾取 / 回归 =====================

	/**
	 * 附魔光泽标记（供渲染器判断是否叠加附魔光效层）。
	 */
	public boolean isFoil() {
		return getEntityData().get(ID_FOIL);
	}

	@Override
	protected boolean tryPickup(Player player) {
		if (super.tryPickup(player)) {
			return true;
		}
		if (this.isNoPhysics() && this.ownedBy(player)) {
			return player.getInventory().add(this.getPickupItem());
		}
		return false;
	}

	@Override
	public void playerTouch(Player player) {
		if (this.ownedBy(player) || this.getOwner() == null) {
			super.playerTouch(player);
		}
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(DollMod.ENDER_AXE_ITEM);
	}

	@Override
	public ItemStack getWeaponItem() {
		ItemStack stack = super.getWeaponItem();
		return (stack == null || stack.isEmpty()) ? this.getPickupItem() : stack;
	}

	@Override
	public ItemStack getItem() {
		return this.getPickupItem();
	}

	// ===================== 杂项 =====================

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	@Override
	public void tickDespawn() {
		byte loyalty = getEntityData().get(ID_LOYALTY);
		if (this.pickup != Pickup.ALLOWED || loyalty <= 0) {
			super.tickDespawn();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.dealtDamage = input.getBooleanOr("DealtDamage", false);
		// 忠诚值直接从 NBT 持久化字段读取（旧存档无此字段时回退到从物品推导）。
		// 原先每次从 getPickupItemStackOrigin() 推导，反序列化后原始物品栈可能为空/无附魔，
		// 导致 loyalty=0、忠诚 III 末影斧重载后不再飞回。
		getEntityData().set(ID_LOYALTY, (byte) input.getIntOr("Loyalty", getLoyaltyFromItem(getPickupItemStackOrigin())));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("DealtDamage", this.dealtDamage);
		output.putInt("Loyalty", getEntityData().get(ID_LOYALTY));
	}

	@Override
	protected float getWaterInertia() {
		return 0.99f;
	}

	@Override
	public boolean shouldRender(double x, double y, double z) {
		// 原版投射物通常有渲染距离裁剪；恒返回 true 会导致远处的末影斧仍尝试渲染。
		return this.distanceToSqr(x, y, z) < 16384.0; // 128 格
	}

	private byte getLoyaltyFromItem(ItemStack stack) {
		if (this.level() instanceof ServerLevel serverLevel) {
			return (byte) Mth.clamp(
				EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, stack, this),
				0, 127);
		}
		return 0;
	}
}
