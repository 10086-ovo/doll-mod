package io.github.a10086ovo.doll.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WardenDollHeadBlock extends SkullBlock {

	public WardenDollHeadBlock(BlockBehaviour.Properties properties) {
		super(WardenDollSkullType.WARDEN_DOLL, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WardenDollHeadBlockEntity(pos, state);
	}
}