package com.example.doll.block;

import com.example.doll.DollMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class NetherDollHeadBlockEntity extends BlockEntity {
	public NetherDollHeadBlockEntity(BlockPos pos, BlockState state) {
		super(DollMod.NETHER_DOLL_HEAD_BLOCK_ENTITY, pos, state);
	}

	public float getAnimation(float partialTick) {
		return 0.0f;
	}
}
