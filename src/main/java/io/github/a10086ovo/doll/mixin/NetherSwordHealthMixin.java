package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.item.NetherSwordItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 地狱剑生命加成：
 * <p>
 * 玩家手持地狱剑（主手或副手）时，生命上限额外 +10（20 → 30），持续提供；
 * 放下后上限回落 20，超出部分由原版 {@code LivingEntity.onAttributeUpdated}
 * 自动 clamp（与原版 Health Boost 效果同一套机制）。
 * <p>
 * 修饰符用 transient（不写盘）：重登后消失，持剑时本 tick 逻辑会重新补上，
 * 不会出现"剑已离身但加成残留"的脏状态。
 */
@Mixin(Player.class)
public class NetherSwordHealthMixin {

	private static final Identifier HEALTH_MOD_ID =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "nether_sword_health");
	private static final AttributeModifier HEALTH_MOD =
		new AttributeModifier(HEALTH_MOD_ID, 10.0, AttributeModifier.Operation.ADD_VALUE);

	@Inject(method = "tick", at = @At("TAIL"))
	private void doll$netherSwordHealth(CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (player.level().isClientSide()) {
			return;
		}
		AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) {
			return;
		}

		boolean holding = player.getMainHandItem().getItem() instanceof NetherSwordItem
			|| player.getOffhandItem().getItem() instanceof NetherSwordItem;
		if (holding) {
			if (!attr.hasModifier(HEALTH_MOD_ID)) {
				attr.addTransientModifier(HEALTH_MOD);
			}
		} else if (attr.hasModifier(HEALTH_MOD_ID)) {
			attr.removeModifier(HEALTH_MOD_ID);
		}
	}
}
