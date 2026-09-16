package io.github.a10086ovo.doll.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SeaDollHeadBlock extends SkullBlock {

	public SeaDollHeadBlock(BlockBehaviour.Properties properties) {
		super(SeaDollSkullType.SEA_DOLL, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SeaDollHeadBlockEntity(pos, state);
	}
}
