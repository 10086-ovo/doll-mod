package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 下界人偶岩浆光环：消除岩浆中移动阻力。
 * <p>
 * MC 26.2 已移除 {@code LivingEntity#getVelocityMultiplier()}，岩浆减速改为在
 * {@link LivingEntity#travelInLava(Vec3, double, boolean, double)} 内部对
 * {@code getDeltaMovement()} 硬编码缩放：
 * <ul>
 *   <li>浅岩浆：{@code movement.multiply(0.5, 0.8, 0.5)}（水平减速 50%、垂直 20%）</li>
 *   <li>深岩浆：{@code movement.scale(0.5)}（整体减速 50%）</li>
 * </ul>
 * 因此这里用 {@code @Redirect} 把 {@code travelInLava} 内的两处减速缩放替换掉：
 * 当实体处于下界人偶 16 格光环内且在岩浆中时，直接返回原 {@code Vec3}，不减速。
 */
@Mixin(LivingEntity.class)
public abstract class EntityLavaSpeedMixin {

	/**
	 * 浅岩浆分支：{@code setDeltaMovement(getDeltaMovement().multiply(0.5, 0.8, 0.5))}
	 */
	@Redirect(method = "travelInLava",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 dollMod$netherAuraNoLavaSlowdown(Vec3 movement, double x, double y, double z) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.isInLava() && DollEntity.isInNetherAura(self)) {
			return movement;
		}
		return movement.multiply(x, y, z);
	}

	/**
	 * 深岩浆分支：{@code setDeltaMovement(getDeltaMovement().scale(0.5))}
	 */
	@Redirect(method = "travelInLava",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 dollMod$netherAuraNoLavaSlowdownDeep(Vec3 movement, double factor) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.isInLava() && DollEntity.isInNetherAura(self)) {
			return movement;
		}
		return movement.scale(factor);
	}
}
