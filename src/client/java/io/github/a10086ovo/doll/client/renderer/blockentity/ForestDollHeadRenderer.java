package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.ForestDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class ForestDollHeadRenderer extends AbstractDollHeadRenderer<ForestDollHeadBlockEntity> {
	public ForestDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/forest_doll.png");
	}
}
