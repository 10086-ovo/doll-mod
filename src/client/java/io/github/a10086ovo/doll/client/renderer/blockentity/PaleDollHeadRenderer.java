package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.PaleDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class PaleDollHeadRenderer extends AbstractDollHeadRenderer<PaleDollHeadBlockEntity> {
	public PaleDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/pale_doll.png");
	}
}
