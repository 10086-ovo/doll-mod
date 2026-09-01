package io.github.a10086ovo.doll.client.renderer.entity;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.client.model.NetherFlyingSwordModel;
import io.github.a10086ovo.doll.client.renderer.entity.state.NetherFlyingSwordRenderState;
import io.github.a10086ovo.doll.entity.NetherFlyingSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * 飞行地狱剑渲染器 —— 仿 {@link net.minecraft.client.renderer.entity.ThrownTridentRenderer} / 末影斧渲染器。
 * <p>
 * 用 {@link NetherFlyingSwordModel} 渲染 3D 剑，模型剑尖沿 +Y。全姿态定向（由实体同步 yaw/pitch/roll）：
 * <ul>
 *   <li>模型顶点变换顺序（由内到外）：roll（绕剑身长轴自转）→ yaw（绕 Y 水平）→ pitch（绕 X 仰俯）</li>
 *   <li>pitch 把模型 +Y（剑尖）指向目标：180°=剑刃竖直朝下（空闲）；0°=剑刃朝上（升空）；瞄准角=直指敌人</li>
 *   <li>roll 仅空闲用于绕长轴钻头式自转；锁敌/冲刺时归零使剑刃笔直</li>
 * </ul>
 * 附魔光泽时叠加 entityGlint 层。
 */
public class NetherFlyingSwordRenderer extends EntityRenderer<NetherFlyingSwordEntity, NetherFlyingSwordRenderState> {

	/** 模型层注册 ID（供 {@code ModelLayerRegistry} 注册与 {@code bakeLayer} 烘焙） */
	public static final ModelLayerLocation NETHER_FLYING_SWORD_LAYER =
		new ModelLayerLocation(Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "nether_flying_sword"), "main");

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		DollModConstants.MOD_ID, "textures/entity/nether_flying_sword/nether_flying_sword.png");

	/** 模型整体缩放（用户 Blockbench 模型 16×16 单位，1.0 ≈ 1 格高） */
	private static final float SWORD_SCALE = 1.0f;
	/**
	 * 模型对齐角：Blockbench 建模时剑身长轴（握把→刃尖）在 XY 平面斜置约 41°，
	 * 不平行于模型 +Y。绕 Z 旋转此角把长轴对齐到模型 +Y，使空闲自转真正绕剑身长轴。
	 * 该旋转不动 Y 轴，冲刺朝向（pitch/yaw 瞄敌）不受影响。若空闲剑仍有轻微倾斜，调此值正负/数值。
	 */
	private static final float MODEL_ALIGN_DEG = -41.0f;

	private final NetherFlyingSwordModel model;

	public NetherFlyingSwordRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new NetherFlyingSwordModel(context.bakeLayer(NETHER_FLYING_SWORD_LAYER));
	}

	@Override
	public NetherFlyingSwordRenderState createRenderState() {
		return new NetherFlyingSwordRenderState();
	}

	@Override
	public void extractRenderState(NetherFlyingSwordEntity entity, NetherFlyingSwordRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = entity.getVisualYaw();
		state.pitch = entity.getVisualPitch();
		state.roll = entity.getVisualRoll();
		state.isFoil = entity.isFoil();
	}

	@Override
	public void submit(NetherFlyingSwordRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
		poseStack.pushPose();
		poseStack.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);

		// 顶点变换顺序（由内到外）：
		//   align（绕 Z 把斜置剑身长轴对齐到模型 +Y）→ roll（绕剑身长轴自转）→
		//   yaw（水平朝向）→ pitch（仰俯，使长轴指向目标 / 空闲竖直刃尖朝下）
		poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
		poseStack.mulPose(Axis.YP.rotationDegrees(state.roll));
		poseStack.mulPose(Axis.ZP.rotationDegrees(MODEL_ALIGN_DEG));

		collector.order(0)
			.submitModel(this.model, Unit.INSTANCE, poseStack, TEXTURE,
				state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

		if (state.isFoil) {
			collector.order(1)
				.submitModel(this.model, Unit.INSTANCE, poseStack,
					RenderTypes.entityGlint(), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, cameraState);
	}
}
