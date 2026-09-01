package io.github.a10086ovo.doll.item;

import io.github.a10086ovo.doll.entity.NetherFlyingSwordEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 地狱剑 —— 下界主题近战武器（数值对标下界合金剑，注册见 DollMod.NETHER_SWORD_ITEM）。
 * <p>
 * 玩家专属能力（与末影斧同模式，人偶不触发——人偶另有自身常驻抗火逻辑，见 DollEntity）：
 * <ul>
 *   <li>手持（主手或副手）时持续获得抗火效果，放下后 2 秒内自然消退</li>
 *   <li>手持时生命上限额外 +10（20 → 30，{@code NetherSwordHealthMixin} 维护，放下回落）</li>
 *   <li>攻击命中点燃目标 4 秒（与原版火焰附加 I 时长一致，可与火焰附加附魔叠加）</li>
 *   <li>长按右键蓄力 1 秒：召唤一把飞行的地狱剑（{@link NetherFlyingSwordEntity}），
 *       自动攻击 16 格半径内最近的敌对生物；同时仅一把，重复召唤顶替旧剑</li>
 * </ul>
 */
public class NetherSwordItem extends Item {

	/** 点燃时长（秒），与原版火焰附加 I 一致 */
	private static final int IGNITE_SECONDS = 4;
	/** 抗火效果持续 tick 数：手持期间每 tick 续期，放下后 2 秒内消退 */
	private static final int FIRE_RESISTANCE_TICKS = 40;
	/** 蓄力时长（tick）：20 = 1 秒，蓄满自动召唤飞剑 */
	private static final int CHARGE_TICKS = 20;
	/** 玩家攻击伤害基础值（Default 显示在带玩家上下文时加回，见 ItemAttributeModifiers.Display.Default） */
	private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

	/**
	 * 26.2 构造「剑」的官方方式：Item.Properties.sword(ToolMaterial, attackDamageBonus, attackSpeed)，
	 * 内部一次性挂三样东西（与 26.2 原版剑完全一致）：
	 * <ul>
	 *   <li><b>TOOL 组件</b>：蜘蛛网挖掘速度 15.0 + sword_instantly_mines 秒挖 + sword_efficient 1.5 倍速
	 *       ——「剑挖蜘蛛网快」的能力就来自这里；手动构造属性时漏掉它会挖蜘蛛网很慢</li>
	 *   <li><b>攻击属性</b>：ATTACK_DAMAGE amount = attackDamageBonus + 材质加成、ATTACK_SPEED amount = attackSpeed，
	 *       且用原版标准 ID（minecraft:base_attack_*），tooltip 自动显示最终值（+8 攻击伤害 / 1.6 攻击速度）</li>
	 *   <li><b>WEAPON 组件</b>：横扫等剑专属特性</li>
	 * </ul>
	 * 因此不再需要手动 ItemAttributeModifiers.builder()（那会漏掉 TOOL/WEAPON 组件）。
	 * <p>
	 * <b>JADE 显示一致性修复：</b>原版 Default 显示只有在「带玩家上下文」时才把基础值加回
	 * （攻击伤害 +1.0、攻击速度 +4.0）。当玩家用 JADE 看向掉落物 / 飞行状态的飞剑（ItemEntity）时，
	 * 工具提示上下文里 player 为 null，基础值不会加回，于是攻击伤害误显示为「+7」而不是「+8」。
	 * 这里在 sword() 之后用 attributes() 覆盖一份 ItemAttributeModifiers：修饰符数值保持不变（战斗实际伤害不变），
	 * 仅把攻击伤害的 display 改成 {@link ItemAttributeModifiers.Display#override} 的固定文本
	 * （复刻玩家手持时看到的最终值 +8 攻击伤害）。攻击速度保持默认显示——JADE 下掉落物显示负值
	 * （如 -2.4）是原版剑的普遍表现，不属于本 bug，不做覆盖。
	 */
	public NetherSwordItem(ToolMaterial material, float attackDamageBonus, float attackSpeed, Properties properties) {
		super(properties.sword(material, attackDamageBonus, attackSpeed)
			.attributes(swordAttributesWithFixedTooltip(material, attackDamageBonus, attackSpeed)));
	}

	/** 构造攻击属性（数值与 sword() 的 createSwordAttributes 完全一致），但攻击伤害的 tooltip 显示用固定最终值覆盖。 */
	private static ItemAttributeModifiers swordAttributesWithFixedTooltip(
			ToolMaterial material, float attackDamageBonus, float attackSpeed) {
		double damageModifier = material.attackDamageBonus() + attackDamageBonus; // 7.0（基础 1.0 之上 +7）
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE,
				new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damageModifier, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND,
				ItemAttributeModifiers.Display.override(
					fixedFinalTooltip(damageModifier + PLAYER_BASE_ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE)))
			.add(Attributes.ATTACK_SPEED,
				new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.build();
	}

	/** 复刻原版 Default 显示带玩家上下文时的文案（如「 +8 攻击伤害」「 1.6 攻击速度」），供 override 固定显示。 */
	private static Component fixedFinalTooltip(double finalValue, Holder<Attribute> attr) {
		return Component.translatable(" %s %s",
				ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(finalValue),
				Component.translatable(attr.value().getDescriptionId()))
			.withStyle(ChatFormatting.DARK_GREEN);
	}

	/** 持有刷新抗火：短时续期避免放下武器后效果残留，图标稳定不闪 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) && entity instanceof Player player) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
				FIRE_RESISTANCE_TICKS, 0, false, false, true));
		}
	}

	/** 攻击命中点燃目标（仅玩家触发；下界人偶持剑另有专属增强，见 DollEntity 灼烧逻辑） */
	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker instanceof Player) {
			target.igniteForTicks(IGNITE_SECONDS * 20);
		}
		super.postHurtEnemy(stack, target, attacker);
	}

	/** 长按右键开始蓄力（三叉戟姿势），蓄力进度见 onUseTick。 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return CHARGE_TICKS;
	}

	/** 蓄满自动召唤：remainingTicks 递减到 1（即蓄满 CHARGE_TICKS-1 tick）时触发，主动停止使用避免 finishUsing 二次触发。 */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (remainingTicks <= 1 && entity instanceof Player player && level instanceof ServerLevel serverLevel) {
			player.stopUsingItem();

			NetherFlyingSwordEntity.replaceExisting(serverLevel, player); // 顶替规则：同时仅一把
			NetherFlyingSwordEntity sword = new NetherFlyingSwordEntity(serverLevel, player);
			serverLevel.addFreshEntity(sword);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
	}
}
