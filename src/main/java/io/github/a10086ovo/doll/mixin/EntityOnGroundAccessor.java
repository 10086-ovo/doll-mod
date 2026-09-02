package io.github.a10086ovo.doll.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityOnGroundAccessor {
	@Accessor("onGround")
	void setOnGround(boolean onGround);
}