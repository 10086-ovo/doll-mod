package io.github.a10086ovo.doll.block;

/**
 * 人偶头颅方块实体公共接口：渲染器基类 {@link io.github.a10086ovo.doll.client.renderer.blockentity.AbstractDollHeadRenderer}
 * 通过此接口获取动画进度，无需关心具体子类类型。
 */
public interface DollHeadBlockEntity {
	float getAnimation(float partialTick);
}
