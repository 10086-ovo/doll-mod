package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 苍白人偶恐惧光环 -- 跳跃抑制（易伤倍率已移交 {@link LivingEntityVulnerabilityMixin}）。
 * <p>
 * 本类不再施加易伤伤害倍率：为配合苍白弓易伤的<b>加算叠加</b>，易伤统一收敛到
 * {@code LivingEntityVulnerabilityMixin} 一个入口计算（每项 +50%，同处触发 ×2.0）。
 * <p>
 * 此处仅保留跳跃抑制：{@code AbstractCubeMob}（史莱姆 / 岩浆怪 / 硫磺立方体的父类）覆写了
 * {@code tick()}，其中自带独立于 AI goal 的跳跃逻辑（{@code getJumpDelay} +
 * {@code jumpFromGround}）。仅取消 {@code serverAiStep}（见
 * {@link MobMixin#paleFearImmobilize}）无法阻止这类跳跃，因为
 * {@code AbstractCubeMob.tick()} 在 {@code super.tick()} 返回后继续执行自己的跳跃代码。
 * 本注入直接在 {@code LivingEntity.jumpFromGround()} HEAD 拦截：
 * 处于恐惧光环内的 {@link Enemy} 一律 cancel，从根源上阻断跳跃。
 * <p>
 * 光环中心由 {@link DollEntity#getAuraCenter()} 决定（跟随时为玩家，不跟随时为人偶自身）。
 */
@Mixin(LivingEntity.class)
public class LivingEntityFearAuraMixin {

	@Inject(method = "jumpFromGround()V", at = @At("HEAD"), cancellable = true)
	private void paleFearNoJump(CallbackInfo ci) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.level().isClientSide()) return;
		if (!(self instanceof Enemy)) return;
		if (DollEntity.isInPaleFearAura(self.position())) {
			ci.cancel();
		}
	}
}
