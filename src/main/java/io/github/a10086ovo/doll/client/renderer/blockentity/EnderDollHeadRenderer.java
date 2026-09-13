package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.EnderDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class EnderDollHeadRenderer extends AbstractDollHeadRenderer<EnderDollHeadBlockEntity> {
	public EnderDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/ender_doll.png");
	}
}
