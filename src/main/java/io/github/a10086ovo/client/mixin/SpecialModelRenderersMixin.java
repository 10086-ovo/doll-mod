package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.client.renderer.special.ThornsShieldSpecialRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link SpecialModelRenderers#bootstrap()} 执行完毕后，
 * 将荆棘盾牌自定义 SpecialModelRenderer 注册到 ID_MAPPER 中，
 * 使物品模型 JSON 中的 {@code "type": "doll-mod:thorns_shield"} 能被正确解析。
 */
@Mixin(SpecialModelRenderers.class)
public abstract class SpecialModelRenderersMixin {

    @Shadow
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void dollMod$registerThornsShieldRenderer(CallbackInfo ci) {
        ID_MAPPER.put(
                Identifier.fromNamespaceAndPath("doll-mod", "thorns_shield"),
                ThornsShieldSpecialRenderer.Unbaked.MAP_CODEC
        );
    }
}
