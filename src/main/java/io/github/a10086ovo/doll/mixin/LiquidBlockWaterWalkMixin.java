package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.item.SeaArmorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 浪花靴 · 水面承载 —— 着靴者足落水面，即得真实承载碰撞。
 * <p>
 * 原版引擎中“真行走/跑/跳”必赖实块之承；液体本无碰撞，故凡浮水皆属伪飞。
 * 此 mixin 使被着靴、且未潜行的实体临其水面时，该水块顶面薄层返以实碰撞，
 * `onGround` 由原版碰撞真为《真》,行跑跳俱是原物理；不设冰、无形、留水如故。
 * <p>
 * 潜行则不放此碰撞——实体乃得穿面下潜，可自由泳潜；松潜复归于面而立。
 * 至论他者（妖、舟、物等）与此无关，水仍无碰撞，故止此行无碍。
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockWaterWalkMixin {

	/** 水面承载薄层：置于水块顶面之下，供足落其上（局部坐标）。 */
	private static final VoxelShape WATER_WALK_FLOOR = Shapes.box(0.0, 0.8, 0.0, 1.0, 1.0, 1.0);

	@Inject(
		method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void dollMod$seaBootsWaterFloor(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		// 仅当实体正以此水块为面、且着靴未潜行时，方予承载
		if (!(context instanceof EntityCollisionContext ecc)
			|| !(ecc.getEntity() instanceof LivingEntity le)
			|| !state.getFluidState().is(FluidTags.WATER)
			|| le.isShiftKeyDown()) {
			return;
		}
		ItemStack boots = le.getItemBySlot(EquipmentSlot.FEET);
		if (!(boots.getItem() instanceof SeaArmorItem)) {
			return;
		}
		// 海人偶随主之深浅：主人已在水中，人偶当随之潜泳——不授水面承载，交由 applySeaSwim 下潜；
		// 主人不在水，则人偶亦践水面，与主同行。
		if (le instanceof DollEntity doll) {
			Player owner = doll.getOwnerPlayer();
			if (owner != null && owner.isInWater()) {
				return;
			}
		}
		// 惟“水面首层”始与承载——上方仍为水者乃水下各层，不予承托。
		// 若上方非水，则此块顶即水面，可承。
		if (getter.getFluidState(pos.above()).is(FluidTags.WATER)) {
			return;
		}
		// 承载仅当足恰临水面（足于此水面块顶时）——足一沉过面即释碰撞，归于正常泳潜。
		double feetY = le.getY();
		double blockTop = pos.getY() + 1.0;
		if (feetY > blockTop + 0.3 || feetY < blockTop - 0.2) {
			return;
		}
		cir.setReturnValue(WATER_WALK_FLOOR);
	}
}