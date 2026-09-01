package io.github.a10086ovo.doll.mixin;

import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link AreaEffectCloud#reapplicationDelay} 字段的写访问。
 * <p>
 * 原版该字段为 private，无 setter。末影人偶的龙息云需要缩短伤害判定间隔，
 * 通过此 accessor 在创建龙息云后设置更短的 reapplicationDelay。
 */
@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {

	@Accessor("reapplicationDelay")
	void dollMod$setReapplicationDelay(int delay);
}
