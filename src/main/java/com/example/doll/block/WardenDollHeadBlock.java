package com.example.doll.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WardenDollHeadBlock extends SkullBlock {
	public static final MapCodec<WardenDollHeadBlock> CODEC = simpleCodec(WardenDollHeadBlock::new);

	public WardenDollHeadBlock(BlockBehaviour.Properties properties) {
		super(WardenDollSkullType.WARDEN_DOLL, properties);
	}

	@Override
	public MapCodec<? extends SkullBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WardenDollHeadBlockEntity(pos, state);
	}
}