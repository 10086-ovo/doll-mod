package com.example.client.mixin;

import com.example.doll.DollMod;
import com.example.doll.entity.DollEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端诊断：人偶首次渲染的时间标记。
 * <p>用于定位"生成人偶后客户端约 7 秒冻结"（用户实测，单机/联机均复现）：
 * <ul>
 *   <li>若本行之后长时间无任何日志 → 卡顿发生在首次渲染内部（渲染管线/着色器/贴图）；</li>
 *   <li>若本行出现在冻结结束之后（时间戳晚于看门狗警告） → 卡顿在渲染之外（实体创建/包处理）。</li>
 * </ul>
 * 目标方法用完整描述符限定，避免与 LivingEntity/Entity 的桥接重载混淆。
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererDollMixin {

	private static boolean firstDollRenderLogged = false;

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("HEAD"),
		require = 0
	)
	private void dollFirstRenderLog(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar instanceof DollEntity && !firstDollRenderLogged) {
			firstDollRenderLogged = true;
			DollMod.LOGGER.info("[DollClient] 人偶首次渲染开始（若其后长时间无日志，卡顿即发生在渲染环节）");
		}
	}
}
