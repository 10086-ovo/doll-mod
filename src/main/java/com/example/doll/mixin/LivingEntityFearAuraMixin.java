package com.example.doll.mixin;

import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollVariant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * 苍白人偶恐惧光环 — 30% 易伤：
 * <p>
 * 当敌对生物（Enemy）受到伤害时，检查其是否处于任何存活苍白人偶的 16 格光环内。
 * 若是，则伤害量 ×1.67。
 * <p>
 * 光环中心由 {@link DollEntity#getAuraCenter()} 决定（跟随时为玩家，不跟随时为人偶自身）。
 * 恐惧光环的清除仇恨部分在 DollEntity.tick() 中实现，此处只负责易伤倍率。
 */
@Mixin(LivingEntity.class)
public class LivingEntityFearAuraMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float paleFearAuraDamageMultiplier(float amount) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.level().isClientSide()) return amount;
		if (!(self instanceof Enemy)) return amount;
		if (!(self.level() instanceof ServerLevel serverLevel)) return amount;

		// 搜索附近 16 格内的苍白人偶
		AABB box = self.getBoundingBox().inflate(16);
		List<DollEntity> dolls = serverLevel.getEntities(
			EntityTypeTest.forClass(DollEntity.class),
			box,
			doll -> doll.getDollVariant() == DollVariant.PALE && doll.isAlive()
		);
		for (DollEntity doll : dolls) {
			Vec3 center = doll.getAuraCenter();
			if (self.position().distanceToSqr(center) <= 16.0 * 16.0) {
				return amount * 1.67f;
			}
		}
		return amount;
	}
}
