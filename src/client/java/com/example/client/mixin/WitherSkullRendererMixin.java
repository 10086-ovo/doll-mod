package com.example.client.mixin;

import com.example.client.DollSkullState;
import com.example.doll.DollModConstants;
import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollVariant;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.client.renderer.entity.state.WitherSkullRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 人偶头颅弹 — 替换渲染模型和贴图。
 * <p>
 * 原版 WitherSkullRenderer 使用 {@code ModelLayers.WITHER_SKULL} 模型层 + 凋灵贴图。
 * 人偶发射的头颅需要显示为人偶外观（PLAYER_HEAD 模型层 + 变体皮肤）：
 * <ul>
 *   <li>NETHER：nether_doll.png</li>
 *   <li>ENDER：ender_doll.png</li>
 * </ul>
 * 模型统一使用 PLAYER_HEAD（UV 匹配玩家皮肤格式），仅贴图按变体区分。
 * 原版凋灵发射的头颅不受影响。
 * <p>
 * 渲染器是单例，每帧渲染多个投射物时通过 DollSkullState 标记区分。
 */
@Mixin(WitherSkullRenderer.class)
public class WitherSkullRendererMixin {

	/** 玩家头颅模型，UV 匹配玩家皮肤格式（nether_doll.png / ender_doll.png）。 */
	@Unique
	private SkullModel dollMod$playerHeadModel;

	/** 当前渲染的投射物是否由 DollEntity 发射（渲染单线程，用实例字段即可）。 */
	@Unique
	private boolean dollMod$currentDollOwned;

	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V", at = @At("RETURN"))
	private void dollMod$initPlayerHeadModel(EntityRendererProvider.Context context, CallbackInfo ci) {
		this.dollMod$playerHeadModel = new SkullModel(context.bakeLayer(ModelLayers.PLAYER_HEAD));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/hurtingprojectile/WitherSkull;Lnet/minecraft/client/renderer/entity/state/WitherSkullRenderState;F)V", at = @At("RETURN"))
	private void dollMod$setVariant(WitherSkull skull, WitherSkullRenderState state, float partialTick, CallbackInfo ci) {
		DollVariant variant = DollVariant.NONE;
		if (skull.getOwner() instanceof DollEntity doll) {
			variant = doll.getDollVariant();
		}
		((DollSkullState) (Object) state).dollMod$setVariant(variant);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/WitherSkullRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void dollMod$captureState(WitherSkullRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
		this.dollMod$currentDollOwned = ((DollSkullState) (Object) state).dollMod$getVariant() != DollVariant.NONE;
	}

	/** 拦截 getTextureLocation 返回值，人偶发射的头颅按变体选择皮肤贴图。 */
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/WitherSkullRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("RETURN"), cancellable = true)
	private void dollMod$swapTexture(WitherSkullRenderState state, CallbackInfoReturnable<Identifier> cir) {
		DollVariant variant = ((DollSkullState) (Object) state).dollMod$getVariant();
		switch (variant) {
			case NETHER -> cir.setReturnValue(Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/nether_doll.png"));
			case ENDER -> cir.setReturnValue(Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/ender_doll.png"));
			default -> {} // NONE: 原版凋灵贴图
		}
	}

	/** 拦截 submitModel 第 0 个参数（Model），人偶发射的头颅用 playerHeadModel。 */
	@ModifyArg(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/WitherSkullRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
		),
		index = 0
	)
	private net.minecraft.client.model.Model<?> dollMod$swapModel(net.minecraft.client.model.Model<?> model) {
		return this.dollMod$currentDollOwned ? this.dollMod$playerHeadModel : model;
	}
}
