package com.example.doll.client.renderer.blockentity;

import com.example.doll.DollModConstants;
import com.example.doll.block.ForestDollHeadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ForestDollHeadRenderer implements BlockEntityRenderer<ForestDollHeadBlockEntity, SkullBlockRenderState> {
	private static final Identifier SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/forest_doll.png");

	private final SkullModel model;

	public ForestDollHeadRenderer(BlockEntityRendererProvider.Context context) {
		this.model = new SkullModel(context.bakeLayer(ModelLayers.PLAYER_HEAD));
	}

	@Override
	public SkullBlockRenderState createRenderState() {
		return new SkullBlockRenderState();
	}

	@Override
	public void extractRenderState(ForestDollHeadBlockEntity entity, SkullBlockRenderState state,
			float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
		BlockEntityRenderState.extractBase(entity, state, overlay);
		state.animationProgress = entity.getAnimation(partialTick);
		BlockState blockState = entity.getBlockState();
		if (blockState.getBlock() instanceof WallSkullBlock) {
			Direction facing = blockState.getValue(WallSkullBlock.FACING);
			state.transformation = (Transformation) SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(facing);
		} else {
			int rotation = blockState.getValue(SkullBlock.ROTATION);
			state.transformation = (Transformation) SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(rotation);
		}
		state.skullType = ((AbstractSkullBlock) blockState.getBlock()).getType();
		state.renderType = RenderTypes.entityCutoutZOffset(SKIN_TEXTURE);
	}

	@Override
	public void submit(SkullBlockRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(state.transformation);
		SkullBlockRenderer.submitSkull(
			state.animationProgress,
			poseStack,
			collector,
			state.lightCoords,
			this.model,
			state.renderType,
			0,
			state.breakProgress
		);
		poseStack.popPose();
	}
}
