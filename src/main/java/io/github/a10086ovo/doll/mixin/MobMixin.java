package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

	/**
	 * 合并下界/海洋安抚光环为单次注入，避免同一方法上两个 @Inject 各执行一次类型匹配。
	 */
	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void dollModPacifyAura(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target == null) return;
		Mob self = (Mob)(Object)this;
		// 下界生物：检查目标是否被下界人偶保护
		if (DollEntity.isNetherMobType(self.getType())) {
			if (DollEntity.isNetherDollProtected(target)) {
				cir.setReturnValue(false);
			}
			return;
		}
		// 敌对海洋生物：检查目标是否被海洋人偶保护
		if (DollEntity.isSeaMobType(self.getType())) {
			if (DollEntity.isSeaDollProtected(target)) {
				cir.setReturnValue(false);
			}
		}
	}

	/**
	 * 苍白人偶恐惧光环 — 索敌抑制：范围内 Enemy 失去索敌 AI + 攻击 AI。
	 * <p>
	 * 任何处于存活苍白人偶 16 格光环内的 {@link Enemy} 调用 canAttack 时一律返回 false，
	 * 无法建立/维持任何目标。判定基于 {@link DollEntity#isInPaleFearAura} 的低开销登记表查询。
	 * <p>
	 * <b>注意</b>：史莱姆/岩浆怪等不靠目标判定、依赖接触伤害的单位不再特例 setNoAi，
	 * 改由 {@link #paleFearImmobilize} 在 serverAiStep 取消 AI 滴答统一抑制移动。
	 * 立方体生物（Slime / MagmaCube / SulfurCube）的跳跃逻辑在 {@code AbstractCubeMob.tick()}
	 * 中独立于 AI goal 运行，serverAiStep 取消无法阻止——由
	 * {@link LivingEntityFearAuraMixin#paleFearNoJump} 在 jumpFromGround 拦截补完。
	 */
	@Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void paleFearAura(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target == null) return;
		Mob self = (Mob)(Object)this;
		if (!(self instanceof Enemy)) return;
		if (DollEntity.isInPaleFearAura(self)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * 苍白人偶恐惧光环 — 移动抑制（非 NoAi）：处于光环内的 Enemy 每 tick 取消 serverAiStep，
	 * 寻路 / 仇恨 / 随机游荡等 AI 滴答一并停止，使其无法仇恨玩家、无法动弹。
	 * <p>
	 * 与 {@code setNoAi} 不同：① 不写入实体 NBT、不持久化，人偶卸载/死亡即从
	 * {@link DollEntity#paleAuraCenters} 移除条目，生物下 tick 自然恢复正常 AI；② 仍保留重力与
	 * 击退等物理（非"粗暴冻结"雕像化）。仅服务端生效；常见场景（无活跃苍白人偶）登记表为空，
	 * {@code isInPaleFearAura} 立即返回 false，开销近似为零。
	 */
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void paleFearImmobilize(CallbackInfo ci) {
		Mob self = (Mob)(Object)this;
		if (self.level().isClientSide()) return;
		if (!(self instanceof Enemy)) return;
		if (DollEntity.isInPaleFearAura(self.position())) {
			ci.cancel();
		}
	}
}
