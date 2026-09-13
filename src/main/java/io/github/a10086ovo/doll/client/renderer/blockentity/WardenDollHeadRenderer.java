package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.WardenDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class WardenDollHeadRenderer extends AbstractDollHeadRenderer<WardenDollHeadBlockEntity> {
	public WardenDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/warden_doll.png");
	}
}
