package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.EnderAxeItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影斧玩家暴击「表现层」：在 {@link Player#attack} 末尾为主手末影斧的近战命中
 * 补上跳劈音效与暴击粒子。
 * <p>
 * 注意（2026-09-04 收敛）：本处<b>不再</b>伪造 fallDistance / onGround 去触发原版跳劈，
 * 否则原版跳劈自带的约 ×1.5 伤害加成会与
 * {@link EnderAxeCriticalMixin} 的统一 ×1.5 叠乘成 ≈×2.25（指南书口径为最终 ×1.5）。
 * 现在数值只由 EnderAxeCriticalMixin 乘一次（净 ×1.5），本处仅负责玩家端的视听表现。
 */
@Mixin(Player.class)
public class PlayerEnderAxeMixin {

	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
	private void dollMod$enderAxeCritVisuals(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		ItemStack mainHand = self.getMainHandItem();
		if (!(mainHand.getItem() instanceof EnderAxeItem)) {
			return;
		}
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!(target instanceof LivingEntity living) || !living.isAlive()) {
			return;
		}
		// 跳劈音效 + 暴击粒子（仅表现，不加数值）
		serverLevel.playSound(null, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.2f);
		serverLevel.sendParticles(ParticleTypes.CRIT,
			living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
			15, 0.2, 0.2, 0.2, 0.0);
		serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
			living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
			15, 0.2, 0.2, 0.2, 0.0);
	}
}
