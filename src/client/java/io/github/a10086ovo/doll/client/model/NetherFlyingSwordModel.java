package io.github.a10086ovo.doll.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

/**
 * 飞行地狱剑 3D 模型 —— 用户 Blockbench 建模（nether_flying_sword.java + nether_flying_sword.png）。
 * <p>
 * 由 Blockbench 5.1.6 导出（Yarn 1.17+ EntityModel 格式），已转换为 mojmap 26.2
 * {@code LayerDefinition}/{@code MeshDefinition}/{@code CubeListBuilder} 范式。
 * <p>
 * 模型为扁平 XY 平面精灵（16×16×1 单位），剑尖沿 +Y 方向，与渲染器
 * {@link io.github.a10086ovo.doll.client.renderer.entity.NetherFlyingSwordRenderer} 的 baseTilt（+Y→+Z）
 * 兼容。贴图尺寸 32×32。
 */
public class NetherFlyingSwordModel extends Model<Unit> {

	public NetherFlyingSwordModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootDef = mesh.getRoot();

		CubeListBuilder bb_main = CubeListBuilder.create();
		bb_main.texOffs(0, 0).addBox(0.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 2).addBox(1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 4).addBox(1.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 0).addBox(0.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 2).addBox(-1.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 4).addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 6).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 6).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 8).addBox(1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 0).addBox(1.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 2).addBox(2.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 8).addBox(2.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 4).addBox(3.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 6).addBox(3.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 8).addBox(2.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 10).addBox(3.0F, -6.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 10).addBox(4.0F, -6.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 10).addBox(4.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 12).addBox(5.0F, -6.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 0).addBox(5.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 2).addBox(4.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 12).addBox(3.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 4).addBox(2.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 6).addBox(2.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 12).addBox(2.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 8).addBox(1.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 10).addBox(1.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 12).addBox(2.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 14).addBox(3.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 14).addBox(3.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 14).addBox(4.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 14).addBox(5.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 16).addBox(5.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 0).addBox(5.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 2).addBox(5.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 16).addBox(6.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 4).addBox(7.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 6).addBox(7.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 16).addBox(8.0F, -3.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 8).addBox(8.0F, -4.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 10).addBox(7.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 16).addBox(6.0F, -5.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 12).addBox(6.0F, -6.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 14).addBox(6.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 16).addBox(7.0F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 18).addBox(6.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 18).addBox(6.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 18).addBox(7.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 18).addBox(7.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 18).addBox(6.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 20).addBox(7.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 0).addBox(8.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 2).addBox(8.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 20).addBox(8.0F, -8.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 4).addBox(9.0F, -9.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 6).addBox(9.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 20).addBox(9.0F, -11.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 8).addBox(8.0F, -11.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 10).addBox(7.0F, -11.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 20).addBox(8.0F, -12.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 12).addBox(9.0F, -13.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 14).addBox(10.0F, -14.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 20).addBox(11.0F, -15.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 16).addBox(11.0F, -11.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 18).addBox(11.0F, -12.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 20).addBox(11.0F, -13.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 22).addBox(11.0F, -14.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 22).addBox(12.0F, -12.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 22).addBox(10.0F, -10.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 22).addBox(10.0F, -11.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(16, 22).addBox(10.0F, -12.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(20, 22).addBox(10.0F, -13.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(0, 24).addBox(9.0F, -12.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 0).addBox(12.0F, -13.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 2).addBox(12.0F, -14.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(4, 24).addBox(12.0F, -15.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 4).addBox(12.0F, -16.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 6).addBox(13.0F, -16.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(8, 24).addBox(14.0F, -16.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 8).addBox(14.0F, -15.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 10).addBox(14.0F, -14.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(12, 24).addBox(13.0F, -13.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 12).addBox(13.0F, -14.0F, -1.0F, 1.0F, 1.0F, 1.0F);
		bb_main.texOffs(24, 14).addBox(13.0F, -15.0F, -1.0F, 1.0F, 1.0F, 1.0F);

		rootDef.addOrReplaceChild("bb_main", bb_main, PartPose.offset(-7.0F, 8.0F, 0.5F));

		return LayerDefinition.create(mesh, 32, 32);
	}
}
