package io.github.a10086ovo.doll.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * 飞行地狱剑渲染状态 —— 仿 {@link net.minecraft.client.renderer.entity.state.ThrownTridentRenderState}。
 * <p>
 * 持有全姿态角（yaw 水平朝向 / pitch 仰俯 / roll 绕剑身翻滚）与附魔光泽标记。
 */
public class NetherFlyingSwordRenderState extends EntityRenderState {
	public float yaw;
	public float pitch;
	public float roll;
	public boolean isFoil;
}
