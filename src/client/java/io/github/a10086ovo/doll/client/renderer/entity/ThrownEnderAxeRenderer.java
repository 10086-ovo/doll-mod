package io.github.a10086ovo.doll.client.renderer.entity;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.client.model.EnderAxeModel;
import io.github.a10086ovo.doll.client.renderer.entity.state.ThrownEnderAxeRenderState;
import io.github.a10086ovo.doll.entity.ThrownEnderAxe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * 投掷末影斧 3D 渲染器 —— 仿照 {@link net.minecraft.client.renderer.entity.ThrownTridentRenderer}。
 * <p>
 * 使用 {@link EnderAxeModel} 渲染 3D 斧头模型，飞行时按 yRot/xRot 旋转，
 * 附魔光泽时叠加附魔光效层。
 */
public class ThrownEnderAxeRenderer extends EntityRenderer<ThrownEnderAxe, ThrownEnderAxeRenderState> {

	/** 模型层注册 ID（供 {@link ModelLayers} 注册和 {@link EntityRendererProvider.Context#bakeLayer} 烘焙） */
	public static final ModelLayerLocation ENDER_AXE_LAYER =
		new ModelLayerLocation(Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "thrown_ender_axe"), "main");

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		DollModConstants.MOD_ID, "textures/entity/thrown_ender_axe/thrown_ender_axe.png");

	private final EnderAxeModel model;

	public ThrownEnderAxeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new EnderAxeModel(context.bakeLayer(ENDER_AXE_LAYER));
	}

	@Override
	public ThrownEnderAxeRenderState createRenderState() {
		return new ThrownEnderAxeRenderState();
	}

	@Override
	public void extractRenderState(ThrownEnderAxe entity, ThrownEnderAxeRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yRot = entity.getYRot(partialTick);
		state.xRot = entity.getXRot(partialTick);
		state.isFoil = entity.isFoil();
	}

	@Override
	public void submit(ThrownEnderAxeRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
		poseStack.pushPose();
		// 旋转：与三叉戟完全一致，Y 轴 (yRot-90) + Z 轴 (xRot+90)
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0f));

		// 主模型
		collector.order(0)
			.submitModel(this.model, Unit.INSTANCE, poseStack, TEXTURE,
				state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

		// 附魔光泽
		if (state.isFoil) {
			collector.order(1)
				.submitModel(this.model, Unit.INSTANCE, poseStack,
					RenderTypes.entityGlint(), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, cameraState);
	}
}
