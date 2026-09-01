package io.github.a10086ovo.doll.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 海洋人偶甲 — 自定义盔甲物品子类。
 * <p>
 * 说明：26.2 已移除 {@code ArmorItem} 基类，盔甲一律由 {@link Item} + {@code Item.Properties.humanoidArmor(...)}
 * 构成（见 DollMod 中 SEA_HELMET 等登记方式）。本类继承 {@link Item}，通过覆写 {@link #inventoryTick}
 * 实现逐件装备 tick 与特效触发。
 * <p>
 * 锻造台纹饰、装备槽分配、耐久/附魔/修理等均来自 Properties 中的盔甲组件，原生保留。
 */
public class SeaArmorItem extends Item {

	private static final float DAMAGE_FALL_CLEAR_THRESHOLD = 3.0f;
	private static final int EFFECT_RENEW_THRESHOLD = 20;
	private static final int NIGHT_VISION_RENEW_THRESHOLD = 240;

	public SeaArmorItem(Properties properties) {
		super(properties);
	}

	/** 服务端逐件装备 tick —— 按装备槽分发对应特效，之后统计全套抗性。 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		// All effects are server-side only (level is already ServerLevel, no client check needed)
		// Only LivingEntity can wear armor and have effects
		if (!(entity instanceof LivingEntity living)) return;

		// Per-piece effects dispatched by equipment slot
		switch (slot) {
			case HEAD -> tickHelmet(entity);
			case CHEST -> tickChestplate(living);
			case LEGS -> tickLeggings(living);
			case FEET -> tickBoots(living);
			default -> { /* no-op for other slots */ }
		}

		// Full-set resistance (count >= 4)
		int count = countSeaArmor(living);
		if (count >= 4) {
			if (!living.hasEffect(MobEffects.RESISTANCE)
				|| living.getEffect(MobEffects.RESISTANCE).getDuration() <= EFFECT_RENEW_THRESHOLD) {
				living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 1, false, false));
			}
		}
	}

	/** 头盔：永远充满氧气 —— 氧气不满时直接补满，永不下沉。 */
	private void tickHelmet(Entity entity) {
		if (entity.getAirSupply() < entity.getMaxAirSupply()) {
			entity.setAirSupply(entity.getMaxAirSupply());
		}
	}

	/** 胸甲：水中夜视 —— 仅快过期时续期，不重复添加。 */
	private void tickChestplate(LivingEntity entity) {
		if (!entity.isInWater()) return;

		if (!entity.hasEffect(MobEffects.NIGHT_VISION)
			|| entity.getEffect(MobEffects.NIGHT_VISION).getDuration() <= NIGHT_VISION_RENEW_THRESHOLD) {
			entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
		}
	}

	/** 护腿：水中速度 III —— 仅快过期时续期，不重复添加。 */
	private void tickLeggings(LivingEntity entity) {
		if (!entity.isInWater()) return;

		if (!entity.hasEffect(MobEffects.SPEED)
			|| entity.getEffect(MobEffects.SPEED).getDuration() <= EFFECT_RENEW_THRESHOLD) {
			entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 2, false, false));
		}
	}

	/** 靴子：水面行走 / 漂浮 —— 潜行不生效，不潜行抵消下沉并清摔落伤害。 */
	private void tickBoots(LivingEntity entity) {
		if (!entity.isInWater()) {
			return;
		}

		if (entity.isShiftKeyDown()) {
			// Let it sink normally when sneaking
			return;
		}

		Vec3 movement = entity.getDeltaMovement();
		// Clear falling damage if not falling fast (fast fall still takes fall damage but clears fall distance after hit)
		if (entity.fallDistance > DAMAGE_FALL_CLEAR_THRESHOLD) {
			// Fast fall hitting water: let it go straight down but clear fall distance after impact
			entity.fallDistance = 0.0f;
		} else {
			// Counteract sinking: zero out negative Y delta to float
			if (movement.y < 0.0) {
				entity.setDeltaMovement(movement.x, 0.0, movement.z);
			}
			entity.fallDistance = 0.0f;
		}
	}

	/** 统计 {@code entity} 四个盔甲槽中 {@code SeaArmorItem} 的数量 —— 用于全套四件触发抗性。 */
	public static int countSeaArmor(LivingEntity entity) {
		int count = 0;
		EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (EquipmentSlot slot : armorSlots) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.getItem() instanceof SeaArmorItem) {
				count++;
			}
		}
		return count;
	}
}
