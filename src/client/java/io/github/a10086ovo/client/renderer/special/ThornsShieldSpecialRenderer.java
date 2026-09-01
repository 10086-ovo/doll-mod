package io.github.a10086ovo.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * 荆棘盾牌专用特殊渲染器。
 *
 * 继承原版 {@link ShieldSpecialRenderer} 以复用原版 {@link ShieldModel} 几何和手部动画管线，
 * 但重写 {@link #submit} 方法，将纹理 SpriteId 替换为自定义的荆棘盾牌纹理，
 * 从而在保持原版盾牌第三人称格挡动画的同时使用独立纹理。
 */
public class ThornsShieldSpecialRenderer extends ShieldSpecialRenderer {

    /** 指向 atlas 中荆棘盾牌自定义纹理的 SpriteId */
    private static final SpriteId THORNS_SHIELD_SPRITE =
            Sheets.SHIELD_MAPPER.apply(Identifier.fromNamespaceAndPath("doll-mod", "thorns_shield"));

    private final SpriteGetter sprites;
    private final ShieldModel model;

    public ThornsShieldSpecialRenderer(SpriteGetter sprites, ShieldModel model) {
        super(sprites, model);
        this.sprites = sprites;
        this.model = model;
    }

    @Override
    public void submit(DataComponentMap arg, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean hasEffect, int seed) {
        // 始终使用荆棘盾牌自定义纹理，不处理 banner pattern（荆棘盾牌不支持旗帜图案）
        collector.submitModel(model, Unit.INSTANCE, poseStack, light, overlay, -1,
                THORNS_SHIELD_SPRITE, sprites, seed, (ModelFeatureRenderer.CrumblingOverlay) null);
    }

    /**
     * Unbaked 实现，负责在模型烘焙阶段创建渲染器实例。
     * 通过 Mixin 注册到 {@link net.minecraft.client.renderer.special.SpecialModelRenderers} 的 ID_MAPPER 中，
     * 对应物品模型 JSON 中的 {@code "type": "doll-mod:thorns_shield"}。
     */
    public static final class Unbaked implements SpecialModelRenderer.Unbaked<DataComponentMap> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<DataComponentMap>> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<DataComponentMap> bake(SpecialModelRenderer.BakingContext context) {
            ShieldModel model = new ShieldModel(context.entityModelSet().bakeLayer(ModelLayers.SHIELD));
            return new ThornsShieldSpecialRenderer(context.sprites(), model);
        }
    }
}
