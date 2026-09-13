package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家手持「向导的登山镐」（主手或副手）时获得平滑翻越一格高方块的能力。
 *
 * <p><b>为什么只能"抬高"不能"覆写"</b>：26.2 中步高并非一个固定字段，而是由
 * {@code Attributes.STEP_HEIGHT} 属性决定，且 {@link LivingEntity#maxUpStep()} 内还带一条
 * 骑乘判定——当该生物被玩家操控时至少返回 {@code 1.0F}。
 * 若用 {@code @Overwrite} 直接返回常量，会连同这两者一起抹掉：
 * 劫掠兽 / 骆驼 / 青蛙等自带更高 {@code STEP_HEIGHT} 的生物会被压回 0.6，
 * 玩家骑乘坐骑（马 / 猪 / 骆驼）时"上 1 格台阶"的加成也会消失。
 *
 * <p>因此这里改为在 {@code RETURN} 处只做一次取大：仅当目标是玩家且手持登山镐时
 * 返回 {@code max(原值, 1.0F)}，其余情况一律原样放行，对原版与其它模组零影响。
 * （人偶自身在 {@code DollEntity#maxUpStep()} 已 override 为 1.0，与此无关。）
 */
@Mixin(LivingEntity.class)
public class GuidePickaxeSmoothStepMixin {

	@Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
	private void doll$raiseStepHeightWhenHoldingGuidePickaxe(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof Player player && isHoldingGuidePickaxe(player)) {
			cir.setReturnValue(Math.max(cir.getReturnValueF(), 1.0F));
		}
	}

	private static boolean isHoldingGuidePickaxe(Player player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return main.is(DollMod.GUIDE_PICKAXE_ITEM) || off.is(DollMod.GUIDE_PICKAXE_ITEM);
	}
}
