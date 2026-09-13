package io.github.a10086ovo.doll.entity.talent;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 向导人偶天赋：引导光环（原 DollEntity.applyGuideAura 迁入）。
 * <p>
 * 每 20 tick、半径 32 格（仅主人受益）：速度 II + 跳跃 II + 护甲 +4
 * （护甲为 transient 修饰符，主人离开范围时移除）；效果约 10 秒、范围内持续重刷。
 */
public class GuideDollTalent extends SpecialDollTalent {

	// 护甲 +4 的 transient 修饰符（不写盘），主人离开光环范围时移除
	private static final Identifier GUIDE_ARMOR_MOD_ID =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "guide_armor_bonus");
	private static final AttributeModifier GUIDE_ARMOR_MOD =
		new AttributeModifier(GUIDE_ARMOR_MOD_ID, 4.0, AttributeModifier.Operation.ADD_VALUE);

	private int guideAuraCooldown = 0;

	public GuideDollTalent() {
		super(false); // 向导：恢复IV + 抗性II；无抗火
	}

	@Override
	public void tickAura(DollEntity doll) {
		if (guideAuraCooldown-- > 0) {
			return;
		}
		guideAuraCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (doll.level().isClientSide()) {
			return;
		}
		Player owner = doll.getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 32.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			// 主人不在光环内：若身上残留护甲修饰符则移除，其余效果（速度/跳跃）自然过期
			AttributeInstance armorAttr = owner.getAttribute(Attributes.ARMOR);
			if (armorAttr != null && armorAttr.hasModifier(GUIDE_ARMOR_MOD_ID)) {
				armorAttr.removeModifier(GUIDE_ARMOR_MOD_ID);
			}
			return;
		}
		// 速度 II（SPEED amp1）+ 跳跃 II（JUMP_BOOST amp1），持续 200 tick（10 秒）
		owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1, false, false));
		owner.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1, false, false));
		// 护甲 +4：transient 修饰符，范围内持续提供
		AttributeInstance armorAttr = owner.getAttribute(Attributes.ARMOR);
		if (armorAttr != null && !armorAttr.hasModifier(GUIDE_ARMOR_MOD_ID)) {
			armorAttr.addTransientModifier(GUIDE_ARMOR_MOD);
		}
	}
}
