package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.EnderAxeItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 末影斧必定暴击：任何实体持末影斧攻击时，伤害 ×1.5（等同跳劈暴击倍率）。
 * 用 {@link ModifyVariable} 在 {@link LivingEntity#hurtServer} 入口处修改 amount 参数，
 * 对玩家、人偶、乃至其他生物均生效，无需各自覆写。
 */
@Mixin(LivingEntity.class)
public class EnderAxeCriticalMixin {

	@Unique
	private static final float CRITICAL_MULTIPLIER = 1.5f;

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float applyEnderAxeCritical(float amount, ServerLevel level, DamageSource source, float amt) {
		if (source.getEntity() instanceof LivingEntity attacker) {
			ItemStack weapon = attacker.getMainHandItem();
			if (weapon.getItem() instanceof EnderAxeItem) {
				return amount * CRITICAL_MULTIPLIER;
			}
		}
		return amount;
	}
}