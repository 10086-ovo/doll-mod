package io.github.a10086ovo.doll.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 海洋人偶甲 — 自定义盔甲物品子类。
 * <p>
 * 说明：26.2 已移除 {@code ArmorItem} 基类，盔甲一律由 {@link Item} + {@code Item.Properties.humanoidArmor(...)}
 * 构成（见 DollMod 中 SEA_HELMET 等登记方式）。本类继承 {@link Item}，通过覆写 {@link #inventoryTick}
 * 实现逐件装备 tick 与特效触发（Task 2 填充）。
 * <p>
 * 锻造台纹饰、装备槽分配、耐久/附魔/修理等均来自 Properties 中的盔甲组件，原生保留。
 */
public class SeaArmorItem extends Item {

	private static final float WATER_WALK_SPEED_BOOST = 0.35f;
	private static final float DAMAGE_FALL_CLEAR_THRESHOLD = 3.0f;
	private static final int EFFECT_RENEW_THRESHOLD = 20;

	public SeaArmorItem(Properties properties) {
		super(properties);
	}

	/** 服务端逐件装备 tick —— 具体特效在 Task 2 实现。 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
	}
}
