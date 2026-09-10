package io.github.a10086ovo.doll.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * FishingHook 私有字段 openWater 的访问器。
 * <p>
 * 用途：人偶钓鱼时没有真实抛竿实体，收竿滚取原版 FISHING 战利品表时，
 * 需要提供一个「THIS_ENTITY = FishingHook」的合成浮漂（见 DollEntity.reelInFish），
 * 而顶层表里 treasure 子表条目带 {@code entity_properties(type_specific/fishing_hook, in_open_water=true)}
 * 条件，读的正是这个私有 openWater 字段。给本字段写值即可让该条件按我们复刻的
 * 开放水域判定结果（真/假）正确放行或拦截。
 */
@Mixin(FishingHook.class)
public interface FishingHookOpenWaterAccessor {
	@Accessor("openWater")
	void dollMod$setOpenWater(boolean openWater);
}
