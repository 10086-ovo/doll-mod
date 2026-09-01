package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.PaleVulnerabilityTracker;
import io.github.a10086ovo.doll.item.PaleBowItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 苍白弓箭矢命中效果 -- 注入 {@link AbstractArrow#onHitEntity} 尾部。
 * <p>
 * 当箭矢命中生物时，检查射击者（{@code getOwner()}）主手或副手是否持有
 * {@link PaleBowItem}。若持有则对目标施加易伤（受伤 +15%，持续 5 秒）。
 * <p>
 * 易伤由 {@link PaleVulnerabilityTracker} 服务端追踪，在
 * {@link LivingEntityVulnerabilityMixin} 的 {@code hurtServer} 中乘算生效。
 * 重复命中只刷新持续时间，不叠加倍率。玩家和人偶射箭均生效。
 */
@Mixin(AbstractArrow.class)
public class AbstractArrowMixin {

	@Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("TAIL"))
	private void paleBowVulnerabilityEffect(EntityHitResult hitResult, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide()) return;

		Entity target = hitResult.getEntity();
		if (!(target instanceof LivingEntity livingTarget)) return;

		Entity owner = self.getOwner();
		if (!(owner instanceof LivingEntity shooter)) return;

		ItemStack mainHand = shooter.getMainHandItem();
		ItemStack offHand = shooter.getOffhandItem();
		if (mainHand.getItem() instanceof PaleBowItem || offHand.getItem() instanceof PaleBowItem) {
			PaleVulnerabilityTracker.apply(livingTarget.getUUID(),
				livingTarget.level().getGameTime());
		}
	}
}
