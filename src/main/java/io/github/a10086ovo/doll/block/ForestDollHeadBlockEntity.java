package io.github.a10086ovo.doll.block;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ForestDollHeadBlockEntity extends BlockEntity implements DollHeadBlockEntity {
	public ForestDollHeadBlockEntity(BlockPos pos, BlockState state) {
		super(DollMod.FOREST_DOLL_HEAD_BLOCK_ENTITY, pos, state);
	}

	public float getAnimation(float partialTick) {
		return 0.0f;
	}
}
