package io.github.a10086ovo.doll.client.renderer.blockentity;

import io.github.a10086ovo.doll.block.NetherDollHeadBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class NetherDollHeadRenderer extends AbstractDollHeadRenderer<NetherDollHeadBlockEntity> {
	public NetherDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context, "textures/entity/doll/nether_doll.png");
	}
}
