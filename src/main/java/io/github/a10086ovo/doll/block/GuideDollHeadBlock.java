package io.github.a10086ovo.doll.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class GuideDollHeadBlock extends SkullBlock {
	public static final MapCodec<GuideDollHeadBlock> CODEC = simpleCodec(GuideDollHeadBlock::new);

	public GuideDollHeadBlock(BlockBehaviour.Properties properties) {
		super(GuideDollSkullType.GUIDE_DOLL, properties);
	}

	@Override
	public MapCodec<? extends SkullBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GuideDollHeadBlockEntity(pos, state);
	}
}
