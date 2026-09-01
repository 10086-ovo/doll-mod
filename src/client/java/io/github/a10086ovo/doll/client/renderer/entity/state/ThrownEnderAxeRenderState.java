package io.github.a10086ovo.doll.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * 投掷末影斧渲染状态 —— 仿照 {@link net.minecraft.client.renderer.entity.state.ThrownTridentRenderState}。
 * <p>
 * 持有飞行旋转角度（xRot/yRot）和附魔光泽标记（isFoil）。
 */
public class ThrownEnderAxeRenderState extends EntityRenderState {
	public float xRot;
	public float yRot;
	public boolean isFoil;
}
