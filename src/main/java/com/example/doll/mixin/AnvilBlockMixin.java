package com.example.doll.mixin;

import com.example.doll.DollMod;
import com.example.doll.block.RockAnvilBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilBlock.class)
public class AnvilBlockMixin {

	@Inject(method = "damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
	private static void onDamage(BlockState state, CallbackInfoReturnable<BlockState> cir) {
		if (state.getBlock() instanceof RockAnvilBlock rockAnvil) {
			RockAnvilBlock next = rockAnvil.nextVariant;
			if (next != null) {
				cir.setReturnValue(next.defaultBlockState().setValue(RockAnvilBlock.FACING, state.getValue(RockAnvilBlock.FACING)));
			} else {
				cir.setReturnValue(null);
			}
		}
	}
}