package io.github.a10086ovo.doll.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ForestDollHeadBlock extends SkullBlock {
	public static final MapCodec<ForestDollHeadBlock> CODEC = simpleCodec(ForestDollHeadBlock::new);

	public ForestDollHeadBlock(BlockBehaviour.Properties properties) {
		super(ForestDollSkullType.FOREST_DOLL, properties);
	}

	@Override
	public MapCodec<? extends SkullBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ForestDollHeadBlockEntity(pos, state);
	}
}
