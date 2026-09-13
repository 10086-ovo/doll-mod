package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.GuideDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class GuideDollHeadRenderer extends AbstractDollHeadRenderer<GuideDollHeadBlockEntity> {
	public GuideDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/guide_doll.png");
	}
}
