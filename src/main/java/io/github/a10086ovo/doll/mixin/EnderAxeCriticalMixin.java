package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.EnderAxeItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 末影斧必定暴击（伤害侧）：持末影斧的攻击，伤害 ×1.5（等同跳劈暴击倍率）。
 * 用 {@link ModifyVariable} 在 {@link LivingEntity#hurtServer} 入口处修改 amount 参数，
 * 对玩家、人偶、乃至其他生物均生效，无需各自覆写。
 * <p>
 * 收敛规则（2026-09-04 定案，净倍率恰好 ×1.5，指南书口径「伤害最终 ×1.5」）：
 * <ul>
 *   <li>玩家近战不再强制原版跳劈暴击去叠数值（见 {@link PlayerEnderAxeMixin}：仅保留跳劈音效/粒子等表现），
 *       统一只在本处乘一次，避免与原版暴击加成叠乘出 ≈×2.25；</li>
 *   <li>投掷弹（{@code ThrownEnderAxe}，伤害来源直击为 {@link Projectile}）命中<b>不</b>乘 ×1.5——
 *       必暴是近战特性，投掷按面板基础伤害 10 结算。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class EnderAxeCriticalMixin {

	@Unique
	private static final float CRITICAL_MULTIPLIER = 1.5f;

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float applyEnderAxeCritical(float amount, ServerLevel level, DamageSource source, float amt) {
		// 投掷弹命中不乘 1.5：必暴是近战特性
		if (source.getDirectEntity() instanceof Projectile) {
			return amount;
		}
		if (source.getEntity() instanceof LivingEntity attacker) {
			ItemStack weapon = attacker.getMainHandItem();
			if (weapon.getItem() instanceof EnderAxeItem) {
				return amount * CRITICAL_MULTIPLIER;
			}
		}
		return amount;
	}
}