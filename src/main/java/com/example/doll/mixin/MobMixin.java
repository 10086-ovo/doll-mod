package com.example.doll.mixin;

import com.example.doll.entity.DollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 下界/海洋/森林人偶安抚光环 — 索敌层拦截（等价创造模式机制）。
 * <p>
 * 创造模式玩家不被攻击的核心是 {@code Player.canBeSeenAsEnemy()} 返回 false，
 * 导致 {@code Mob.canAttack(player)} 返回 false，所有索敌路径无法将玩家设为目标。
 * <p>
 * 本 Mixin 在对应类别生物调用 canAttack 时，如果目标是对应人偶或其主人，返回 false。
 * 效果等价于"目标对该类生物不可见为敌人"——仇恨无法建立，无需每 tick 清除。
 * <p>
 * 已有遗留仇恨（Mixin 生效前 / 被 alertOthers 等非 canAttack 路径设置）
 * 由 {@link DollEntity#applyNetherPacifyAura()} / {@link DollEntity#applySeaPacifyAura()}
 * 低频清理。
 * <p>
 * 注意：森林人偶<b>不</b>在此拦截——其"敌怪安抚（仇恨豁免）"天赋已被移除，
 * 仅保留 {@link DollEntity#applyForestVineAura()} 的藤蔓缠绕减速（不阻止仇恨）。
 */
@Mixin(Mob.class)
public class MobMixin {

	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void netherPacifyAura(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target == null) return;
		Mob self = (Mob)(Object)this;
		// 只对下界生物生效
		if (!DollEntity.isNetherMobType(self.getType())) return;
		// 检查 target 是否被下界人偶保护
		if (DollEntity.isNetherDollProtected(target)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void seaPacifyAura(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target == null) return;
		Mob self = (Mob)(Object)this;
		// 只对敌对海洋生物生效
		if (!DollEntity.isSeaMobType(self.getType())) return;
		// 检查 target 是否被海洋人偶保护
		if (DollEntity.isSeaDollProtected(target)) {
			cir.setReturnValue(false);
		}
	}
}
