package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 苍白人偶恐惧光环 — 30% 易伤 + 跳跃抑制。
 * <p>
 * 当敌对生物（Enemy）受到伤害时，检查其是否处于任何存活苍白人偶的 16 格光环内。
 * 若是，则伤害量 ×1.67。
 * <p>
 * 光环中心由 {@link DollEntity#getAuraCenter()} 决定（跟随时为玩家，不跟随时为人偶自身）。
 * 恐惧光环的清除仇恨部分在 DollEntity.tick() 中实现，此处只负责易伤倍率。
 * <p>
 * 跳跃抑制：{@code AbstractCubeMob}（史莱姆 / 岩浆怪 / 硫磺立方体的父类）覆写了
 * {@code tick()}，其中自带独立于 AI goal 的跳跃逻辑（{@code getJumpDelay} +
 * {@code jumpFromGround}）。仅取消 {@code serverAiStep}（见
 * {@link MobMixin#paleFearImmobilize}）无法阻止这类跳跃，因为
 * {@code AbstractCubeMob.tick()} 在 {@code super.tick()} 返回后继续执行自己的跳跃代码。
 * 本注入直接在 {@code LivingEntity.jumpFromGround()} HEAD 拦截：
 * 处于恐惧光环内的 {@link Enemy} 一律 cancel，从根源上阻断跳跃。
 */
@Mixin(LivingEntity.class)
public class LivingEntityFearAuraMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float paleFearAuraDamageMultiplier(float amount) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.level().isClientSide()) return amount;
		if (!(self instanceof Enemy)) return amount;
		// 低开销查询苍白人偶恐惧光环登记表，替代每次受击都 32³ getEntities 扫描
		if (DollEntity.isInPaleFearAura(self.position())) {
			return amount * 1.67f;
		}
		return amount;
	}

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
