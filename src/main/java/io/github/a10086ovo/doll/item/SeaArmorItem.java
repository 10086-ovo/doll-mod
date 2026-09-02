package io.github.a10086ovo.doll.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

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

	private static final int EFFECT_RENEW_THRESHOLD = 20;
	private static final int NIGHT_VISION_RENEW_THRESHOLD = 240;
	private static final float FALL_CLUTCH_THRESHOLD = 3.5f;
	private static final int DURABILITY_PER_CLUTCH = 1;

	/** 正在施"落地水"之穿着者（已活化置水者）—— 逐玩家活化标志 */
	private static final Set<ServerPlayer> CLUTCH_ACTIVE = new HashSet<>();
	/** 各穿着者所置水块之簿，澈除时据此还空 */
	private static final IdentityHashMap<ServerPlayer, Set<BlockPos>> CLUTCH_WATER = new IdentityHashMap<>();

	public SeaArmorItem(Properties properties) {
		super(properties);
	}

	/** 服务端逐件装备 tick —— 按装备槽分发对应特效，之后统计全套抗性。 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		// All effects are server-side only (level is already ServerLevel, no client check needed)
		// Only LivingEntity can wear armor and have effects
		if (!(entity instanceof LivingEntity living)) return;
		// 26.2 中 inventoryTick 亦会以 null 槽被调（物品在背包/光标等非装备位）——此时无特效可施，
		// 且 switch 于 null 即 NPE，故早返。全套抗性只由着辈（非 null）之各件 tick 自足承担。
		if (slot == null) return;

		// Per-piece effects dispatched by equipment slot
		switch (slot) {
			case HEAD -> tickHelmet(entity);
			case CHEST -> tickChestplate(living);
			case LEGS -> tickLegs(level, living);
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

	/** 靴子：行水如履 —— 水面承载（真行走/跑/跳）由
	 * {@link io.github.a10086ovo.doll.mixin.LiquidBlockWaterWalkMixin} 于水块碰撞处落实；
	 * 此处仅清摔落兜底。 */
	private void tickBoots(LivingEntity entity) {
		entity.fallDistance = 0.0f;
	}

	/** 护腿（落地水）：正在下蹲且坠速将损血时，于足下一格续生水源为垫，落之既愈即澈。
	 * 仅当 FET 槽未着潮汐甲（着靴之防摔已由 tickBoots 全免）时生效；每番触动耗耐一点。 */
	private void tickLegs(ServerLevel level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer sp)) return;
		ItemStack legs = sp.getItemBySlot(EquipmentSlot.LEGS);
		if (!(legs.getItem() instanceof SeaArmorItem)) { drain(sp); return; }
		if (sp.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SeaArmorItem) { drain(sp); return; }
		if (sp.isRemoved() || sp.getHealth() <= 0.0f) { drain(sp); return; }

		boolean crouching = sp.isCrouching();
		boolean dangerous = sp.fallDistance > FALL_CLUTCH_THRESHOLD;

		if (crouching && dangerous) {
			if (!CLUTCH_ACTIVE.contains(sp)) {
				if (legs.getDamageValue() >= legs.getMaxDamage()) return;   // 耐尽不受施
				legs.hurtAndBreak(DURABILITY_PER_CLUTCH, sp, EquipmentSlot.LEGS);
				CLUTCH_ACTIVE.add(sp);
			}
			placeWater(level, sp);
		} else if (CLUTCH_ACTIVE.contains(sp)) {
			drain(sp);
		}
	}

	/** 于足下一格置源水并登簿：格须为非流体之可替块（air/植被），不覆既有水体/岩浆/实块。 */
	private void placeWater(ServerLevel level, ServerPlayer sp) {
		Set<BlockPos> water = CLUTCH_WATER.computeIfAbsent(sp, k -> new HashSet<>());
		BlockPos below = sp.blockPosition().below();
		BlockState state = level.getBlockState(below);
		if (state.getFluidState().isEmpty() && state.canBeReplaced() && water.add(below)) {
			level.setBlock(below, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
		}
	}

	/** 澈除该穿着者簿中仍为水之诸格，复位活化标志并去簿。 */
	private static void drain(ServerPlayer sp) {
		if (!CLUTCH_ACTIVE.remove(sp)) {
			CLUTCH_WATER.remove(sp);
			return;
		}
		Set<BlockPos> water = CLUTCH_WATER.remove(sp);
		if (water == null) return;
		ServerLevel level = (ServerLevel) sp.level();
		for (BlockPos p : water) {
			if (level.getBlockState(p).getFluidState().is(Fluids.WATER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
			}
		}
	}

	/** 供脱机等外部事件澈簿（DollMod 于 ServerPlayerEvents.DISCONNECT 调用）。 */
	public static void drainPlayer(ServerPlayer sp) {
		drain(sp);
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
