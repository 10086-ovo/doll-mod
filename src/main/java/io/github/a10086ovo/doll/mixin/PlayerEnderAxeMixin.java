package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.EnderAxeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影斧玩家暴击音效：在 {@link Player#attack} 开始时将 fallDistance 设为正值 + onGround 置 false，
 * 使原版 critical hit 条件命中，自动触发跳劈音效、暴击粒子与水平减速。
 * <p>
 * 设置后无需恢复——fallDistance 在每 tick 末被 LivingEntity.aiStep 归零，
 * onGround 在下一帧移动计算中被重写，不会影响后续逻辑。
 */
@Mixin(Player.class)
public class PlayerEnderAxeMixin {

	@Inject(method = "attack", at = @At("HEAD"))
	private void dollMod$forceEnderAxeCrit(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self.getMainHandItem().getItem() instanceof EnderAxeItem) {
			// fallDistance > 0.0f 且 onGround == false 才触发原版跳劈判定
			if (self.fallDistance <= 0.0f) {
				self.fallDistance = 0.1f;
			}
			((EntityOnGroundAccessor) self).setOnGround(false);
		}
	}
}