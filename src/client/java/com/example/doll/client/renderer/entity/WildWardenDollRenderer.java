package com.example.doll.client.renderer.entity;

import com.example.doll.DollModConstants;
import com.example.doll.entity.WildWardenDollEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * 野生幽匿人偶渲染器。
 * 使用 HumanoidModel（玩家模型骨架），Alex 细臂皮肤。
 */
public class WildWardenDollRenderer extends HumanoidMobRenderer<WildWardenDollEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		DollModConstants.MOD_ID, "textures/entity/doll/warden_doll_wild.png");

	public WildWardenDollRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(WildWardenDollEntity entity, HumanoidRenderState state, float delta) {
		super.extractRenderState(entity, state, delta);
		// 野生人偶不显示头顶名字（贴合原版敌对生物行为）
		state.nameTag = null;
	}

	@Override
	protected boolean shouldShowName(WildWardenDollEntity entity, double distance) {
		return false;
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}