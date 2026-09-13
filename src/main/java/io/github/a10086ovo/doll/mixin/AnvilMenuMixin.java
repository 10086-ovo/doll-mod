package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.block.RockAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {

	/**
	 * 每次从石砧取出结果（onTake）都确定性地消耗一次耐久：
	 * 每个阶段固定用 3 次，第 3 次用完后切换到下一损伤阶段（最后一个阶段则损坏消失）。
	 * 创造模式玩家与原版铁砧一样不消耗耐久。
	 */
	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void dollMod$countRockAnvilUse(Player player, ItemStack stack, CallbackInfo ci) {
		if (player.hasInfiniteMaterials()) return; // 创造模式不消耗，和原版铁砧一致
		((ItemCombinerMenuAccessor) this).dollMod$getAccess().execute((level, pos) -> {
			if (!level.isClientSide()) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof RockAnvilBlock) {
					RockAnvilBlock.consumeUse(level, pos, state);
				}
			}
		});
	}

	/**
	 * 跳过原版 onTake 里对石砧的“随机 12% 损伤”处理，避免和上面的确定性计数叠加。
	 * 创造模式下不取消，让原版照常播放音效（且不消耗）。
	 */
	@Inject(method = "lambda$onTake$0(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true)
	private static void dollMod$skipVanillaRockAnvilDamage(Player player, Level level, BlockPos pos, CallbackInfo ci) {
		if (player.hasInfiniteMaterials()) return;
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof RockAnvilBlock) {
			ci.cancel();
		}
	}
}
