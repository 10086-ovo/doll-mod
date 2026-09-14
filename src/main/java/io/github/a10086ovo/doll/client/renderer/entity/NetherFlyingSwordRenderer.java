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
 * 飞行下界剑渲染器 —— 仿 {@link net.minecraft.client.renderer.entity.ThrownTridentRenderer} / 末影斧渲染器。
 * <p>
 * 用 {@link NetherFlyingSwordModel} 渲染 3D 剑，模型剑尖沿 +Y。全姿态定向（由实体同步 yaw/pitch/roll）：
 * <ul>
 *   <li>模型顶点变换顺序（由内到外，内层先作用于顶点）：align（绕 Z 把斜 45° 剑身对齐到模型 +Y）
 *       → roll（绕模型 +Y 长轴自转）→ pitch（绕 X 仰俯）→ yaw（绕世界 Y 水平转向，最外层）</li>
 *   <li>yaw 在最外层：先被 pitch 倾斜的剑尖矢量再经 yaw 转到目标的水平方位 —— 剑尖可指向任意方向。
 *       旧顺序 yaw 在 pitch 内侧时对 +Y 剑尖无效（绕自身轴转不动剑尖），剑尖只能被 pitch 困在
 *       竖直 Z 平面内摆动，水平方位永远偏不了 → 敌人一换方位就剑柄/剑身撞人（2026-09-09 修复根因）</li>
 *   <li>pitch：0°=剑尖竖直朝上（升空）、90°=水平、180°=竖直朝下（空闲）；瞄准角=直指敌人</li>
 *   <li>roll 仅空闲用于绕长轴钻头式自转（在 pitch/yaw 前施加，贴剑身轴不甩尾）；锁敌/冲刺时归零使剑刃笔直</li>
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
	 * 模型对齐角（2026-09-09 终版）：
	 * <p>
	 * 84 个体素实际几何上——"原点簇"（-1..2, -3..0，3×3=9 立方体）对应"柄/护手"，
	 * "远端簇"（12..15, -16..-13，3×4=11 立方体稍宽）对应"刃尖"。
	 * 用户实测报告："刃尖朝下(正常)" + "把柄追着怪物打"——只有把"刃尖"映射到 +Y
	 * （瞄准数学用 +Y 对准目标）才能同时满足这两个观察。
	 * <p>
	 * 远端刃尖方向 = (15-6.5, -16-(-8.5)) = (8.5, -7.5)，角度 ≈ -41.4°。
	 * R_Z(θ) 把该方向映射到 +Y 需要 -41.4° + θ = +90° → θ = **+135°**（不是 -45°）。
	 * 旧值 -45° 实际把"柄"映射到了 +Y，导致数学指着怪物的是柄、刃尖拖后——即"把柄追着怪"症状。
	 * 该旋转不动 Y 轴，冲刺朝向（pitch/yaw 瞄敌）方向约定不变；仅互换两端在 ±Y 间的归属。
	 */
	private static final float MODEL_ALIGN_DEG = 135.0f;

	private final NetherFlyingSwordModel model;

	public NetherFlyingSwordRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new NetherFlyingSwordModel(context.bakeLayer(NETHER_FLYING_SWORD_LAYER));
	}

	@Override
	public NetherFlyingSwordRenderState createRenderState() {
		return new NetherFlyingSwordRenderState();
	}

	/**
	 * 姿态插值快照阈值（度）：上一拍与当前值的最短弧差超过此值时直接取当前值（快照），
	 * 否则按 partialTick 平滑插值。悬停自转/小幅转向增量小 → 平滑；
	 * 升空→突刺/大角度变向增量大（可达 120°+）→ 若仍插值会滞后整整 1 tick，
	 * 而冲刺 1.6 格/tick 可能 1~2 tick 就接触，接触瞬间剑"只转了一半"呈固定偏角 → 改为直接快照到位。
	 */
	private static final float SNAP_ANGLE_DEG = 90.0f;

	/** 渲染用姿态角：增量大则快照到当前值，增量小则 partialTick 最短弧插值。 */
	private static float poseAngle(float partialTick, float prev, float current) {
		float d = (current - prev) % 360.0f;
		if (d > 180.0f) {
			d -= 360.0f;
		}
		if (d < -180.0f) {
			d += 360.0f;
		}
		if (Math.abs(d) > SNAP_ANGLE_DEG) {
			return current;
		}
		return prev + d * partialTick;
	}

	@Override
	public void extractRenderState(NetherFlyingSwordEntity entity, NetherFlyingSwordRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		// 全姿态客户端插值：prev(上一 client tick 捕获) → current(同步值)。
		// 服务端姿态每 tick(20Hz) 更新，若直接取原始值会在高帧率下呈现阶梯跳变（自转/转向"刷新率低"）；
		// 但小角度插值、大角度(>SNAP_ANGLE_DEG)快照，兼顾平滑与"接触瞬间姿态已就位"。
		state.yaw = poseAngle(partialTick, entity.getPrevVisualYaw(), entity.getVisualYaw());
		state.pitch = poseAngle(partialTick, entity.getPrevVisualPitch(), entity.getVisualPitch());
		state.roll = poseAngle(partialTick, entity.getPrevVisualRoll(), entity.getVisualRoll());
		state.isFoil = entity.isFoil();
	}

	@Override
	public void submit(NetherFlyingSwordRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
		poseStack.pushPose();
		poseStack.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);

		// 顶点变换顺序（由内到外；mulPose 依次后乘，代码越靠下越先作用于顶点）：
		//   align（绕 Z 把斜置剑身长轴对齐到模型 +Y）→ roll（绕 +Y 长轴自转）→
		//   pitch（绕 X 仰俯：倾斜剑尖离轴）→ yaw（绕世界 Y 水平转向，最外层）
		// yaw 必须在最外层：被 pitch 倾斜后的剑尖矢量经 yaw 才能转到目标水平方位。
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
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
