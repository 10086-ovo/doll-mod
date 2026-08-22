package com.example.client.mixin;

import com.example.doll.DollModConstants;
import com.example.doll.block.EnderDollSkullType;
import com.example.doll.block.ForestDollSkullType;
import com.example.doll.block.NetherDollSkullType;
import com.example.doll.block.PaleDollSkullType;
import com.example.doll.block.SeaDollSkullType;
import com.example.doll.block.WardenDollSkullType;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererMixin {

	@Inject(method = "lambda$static$0", at = @At("TAIL"))
	private static void onRegisterSkins(HashMap<SkullBlock.Type, Identifier> map, CallbackInfo ci) {
		map.put(WardenDollSkullType.WARDEN_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/warden_doll.png"));
		map.put(PaleDollSkullType.PALE_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/pale_doll.png"));
		map.put(NetherDollSkullType.NETHER_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/nether_doll.png"));
		map.put(EnderDollSkullType.ENDER_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/ender_doll.png"));
		map.put(SeaDollSkullType.SEA_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/sea_doll.png"));
		map.put(ForestDollSkullType.FOREST_DOLL,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "textures/entity/doll/forest_doll.png"));
	}

	@Inject(method = "createModel(Lnet/minecraft/client/model/geom/EntityModelSet;Lnet/minecraft/world/level/block/SkullBlock$Type;)Lnet/minecraft/client/model/object/skull/SkullModelBase;", at = @At("HEAD"), cancellable = true)
	private static void onCreateModel(EntityModelSet entityModelSet, SkullBlock.Type type, CallbackInfoReturnable<SkullModelBase> cir) {
		if (type == WardenDollSkullType.WARDEN_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
		if (type == PaleDollSkullType.PALE_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
		if (type == NetherDollSkullType.NETHER_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
		if (type == EnderDollSkullType.ENDER_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
		if (type == SeaDollSkullType.SEA_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
		if (type == ForestDollSkullType.FOREST_DOLL) {
			cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)));
		}
	}
}