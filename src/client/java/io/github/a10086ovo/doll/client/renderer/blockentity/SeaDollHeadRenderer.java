package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.SeaDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class SeaDollHeadRenderer extends AbstractDollHeadRenderer<SeaDollHeadBlockEntity> {
	public SeaDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/sea_doll.png");
	}
}
