package io.github.a10086ovo.doll.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

/** 末影斧 3D 投掷模型 —— Blockbench 设计，UV 逐方块映射，32x32 纹理。 */
public class EnderAxeModel extends Model<Unit> {

	public EnderAxeModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootDef = mesh.getRoot();

		PartDefinition axe1 = rootDef.addOrReplaceChild("axe1",
			CubeListBuilder.create(),
			PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -3.1416f, 0.0f, 0.0f));

		CubeListBuilder handleBuilder = CubeListBuilder.create();
		handleBuilder.texOffs(8, 16).addBox(-3.0f, -3.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(4, 18).addBox(-3.0f, -2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(8, 18).addBox(-2.0f, -1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(12, 18).addBox(-1.0f, 0.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 18).addBox(1.0f, 2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(0, 20).addBox(-1.0f, -2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(20, 0).addBox(2.0f, 3.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(20, 2).addBox(-4.0f, -3.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 8).addBox(-2.0f, -2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 10).addBox(-1.0f, -1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(12, 16).addBox(-4.0f, -4.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(4, 20).addBox(-2.0f, -3.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(20, 4).addBox(-3.0f, -4.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(20, 6).addBox(1.0f, 0.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(8, 20).addBox(2.0f, 1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(20, 8).addBox(3.0f, 2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(12, 20).addBox(0.0f, 1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(0, 0).addBox(0.0f, -1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 12).addBox(3.0f, 3.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 14).addBox(2.0f, 2.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(16, 16).addBox(1.0f, 1.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		handleBuilder.texOffs(0, 18).addBox(0.0f, 0.0f, -0.5f, 1.0f, 1.0f, 1.0f);
		axe1.addOrReplaceChild("handle", handleBuilder, PartPose.offset(0.0f, 0.0f, 0.0f));

		CubeListBuilder axeBladeBuilder = CubeListBuilder.create();
		axeBladeBuilder.texOffs(0, 2).addBox(-1.1579f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 4).addBox(-2.1579f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 0).addBox(-3.1579f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 2).addBox(-0.1579f, -2.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 4).addBox(0.8421f, -3.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 6).addBox(1.8421f, -3.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 6).addBox(2.8421f, -2.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 8).addBox(2.8421f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 0).addBox(1.8421f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 2).addBox(1.8421f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 8).addBox(0.8421f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 4).addBox(0.8421f, 1.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 6).addBox(0.8421f, 2.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 8).addBox(-0.1579f, 3.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 10).addBox(-1.1579f, 3.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 10).addBox(-2.1579f, 2.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 10).addBox(-3.1579f, 1.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 12).addBox(-4.1579f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 0).addBox(-4.1579f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 2).addBox(-3.1579f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 12).addBox(-3.1579f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 4).addBox(-2.1579f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 6).addBox(-2.1579f, 1.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 12).addBox(-2.1579f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 8).addBox(-1.1579f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 10).addBox(-1.1579f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 12).addBox(-1.1579f, 1.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 14).addBox(-1.1579f, 2.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 14).addBox(-0.1579f, 2.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(8, 14).addBox(-0.1579f, 1.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(12, 14).addBox(-0.1579f, 0.0789f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(0, 16).addBox(-0.1579f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(16, 0).addBox(-0.1579f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(16, 2).addBox(0.8421f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(4, 16).addBox(0.8421f, -0.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(16, 4).addBox(0.8421f, -2.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(16, 6).addBox(1.8421f, -2.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axeBladeBuilder.texOffs(20, 10).addBox(1.8421f, -1.9211f, -0.5f, 1.0f, 1.0f, 1.0f);
		axe1.addOrReplaceChild("axeBlade", axeBladeBuilder, PartPose.offset(4.1579f, 5.9211f, 0.0f));

		CubeListBuilder bone2Builder = CubeListBuilder.create();
		axe1.addOrReplaceChild("bone2", bone2Builder, PartPose.offset(0.0f, 0.0f, 0.0f));

		return LayerDefinition.create(mesh, 32, 32);
	}
}