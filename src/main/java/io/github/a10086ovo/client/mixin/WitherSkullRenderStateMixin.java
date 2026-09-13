package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.client.DollSkullState;
import io.github.a10086ovo.doll.entity.DollVariant;
import net.minecraft.client.renderer.entity.state.WitherSkullRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 WitherSkullRenderState 注入发射者变体标记，
 * 用于区分人偶发射的头颅（NETHER/ENDER）和原版凋灵发射的头颅。
 */
@Mixin(WitherSkullRenderState.class)
public class WitherSkullRenderStateMixin implements DollSkullState {

	@Unique
	private DollVariant dollMod$variant = DollVariant.NONE;

	@Override
	public DollVariant dollMod$getVariant() {
		return this.dollMod$variant;
	}

	@Override
	public void dollMod$setVariant(DollVariant variant) {
		this.dollMod$variant = variant;
	}
}
