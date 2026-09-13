package io.github.a10086ovo.client;

import io.github.a10086ovo.doll.entity.DollVariant;

/**
 * Duck Typing 接口，让 {@link net.minecraft.client.renderer.entity.state.WitherSkullRenderState}
 * 携带 "发射者变体" 的标记。
 * <p>
 * 渲染器单例在每帧渲染多个投射物时，需要区分哪些是人偶发射的、哪个变体发射的。
 * 通过 Mixin 给 WitherSkullRenderState 实现此接口，在 extractRenderState 中写入，
 * 在 submit / getTextureLocation 中读取，据此切换模型和贴图。
 * <p>
 * NONE 表示非人偶发射（原版凋灵等），NETHER/ENDER 分别对应下界/末影人偶。
 */
public interface DollSkullState {

	DollVariant dollMod$getVariant();

	void dollMod$setVariant(DollVariant variant);
}
