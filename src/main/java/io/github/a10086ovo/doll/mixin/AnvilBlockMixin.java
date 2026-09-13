package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.block.RockAnvilBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilBlock.class)
public class AnvilBlockMixin {

	/**
	 * 石砧不再走原版的“每次随机 12% 几率直接升一级损伤”逻辑，
	 * 而是由 AnvilMenuMixin + RockAnvilBlock.consumeUse 按“每阶段固定 3 次”确定性降级。
	 * 这里把 damage 对石砧改成原样返回，避免原版随机损伤路径干扰耐久计数。
	 */
	@Inject(method = "damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
	private static void onDamage(BlockState state, CallbackInfoReturnable<BlockState> cir) {
		if (state.getBlock() instanceof RockAnvilBlock) {
			cir.setReturnValue(state);
		}
	}
}