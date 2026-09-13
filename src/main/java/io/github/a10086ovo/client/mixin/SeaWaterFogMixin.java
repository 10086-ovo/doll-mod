package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.doll.item.SeaArmorItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side water fog widening for sea armor chestplate.
 * <p>
 * When the local player is underwater and wearing the {@link SeaArmorItem} chestplate,
 * this mixin multiplies the water fog distance to widen the view and reduce blue murk.
 * Only affects the local client, only when conditions are met — zero impact otherwise.
 */
@Mixin(WaterFogEnvironment.class)
public abstract class SeaWaterFogMixin {

    /** Multiplier to widen fog distance — 2.5x makes the underwater sea view much clearer. */
    private static final float SEA_ARMOR_FOG_MULTIPLIER = 2.5f;

    @Inject(
        method = "setupFog(Lnet/minecraft/client/renderer/fog/FogData;Lnet/minecraft/client/Camera;Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "TAIL")
    )
    private void dollMod$widenFogForSeaArmor(FogData fogData, net.minecraft.client.Camera camera,
        net.minecraft.client.multiplayer.ClientLevel level, float partialTick, net.minecraft.client.DeltaTracker tracker,
        CallbackInfo ci)
    {
        Entity cameraEntity = camera.entity();
        // Only apply to LocalPlayer (the client's own player) when it's the camera
        if (!(cameraEntity instanceof LocalPlayer localPlayer)) {
            return;
        }

        // Only when the player is actually underwater (where fog is active)
        if (!localPlayer.isUnderWater()) {
            return;
        }

        // Check if chestplate slot has SeaArmorItem — this is where the effect comes from
        ItemStack chestStack = localPlayer.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof SeaArmorItem)) {
            return;
        }

        // Widen both environmentalEnd (main fog plane) and derived planes (sky/cloud)
        fogData.environmentalEnd *= SEA_ARMOR_FOG_MULTIPLIER;
        fogData.skyEnd = fogData.environmentalEnd;
        fogData.cloudEnd = fogData.environmentalEnd;
    }
}
