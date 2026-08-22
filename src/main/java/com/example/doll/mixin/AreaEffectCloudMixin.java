package com.example.doll.mixin;

import com.example.doll.entity.DollEntity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 末影人偶龙息云友军保护。
 * <p>
 * 原版 {@link AreaEffectCloud#serverTick} 对范围内所有 LivingEntity 无差别施加药水效果，
 * 包括即时伤害（龙息云）。当龙息云的 owner 是 {@link DollEntity} 时，
 * <b>反转逻辑</b>：只伤害实现了 {@link Enemy} 接口的敌对生物，
 * 所有非敌对生物（动物、村民、铁傀儡、玩家、其他人偶等）自动被保护。
 * <p>
 * 注入点：{@code serverTick} 中对每个实体调用 {@code isAffectedByPotions()} 的位置。
 * 返回 false 会让该实体被跳过，与原版"不受药水影响"的实体处理一致。
 */
@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin {

	@Shadow
	public abstract LivingEntity getOwner();

	@Redirect(method = "serverTick",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"))
	private boolean dollMod$skipFriendlyEntities(LivingEntity entity) {
		LivingEntity owner = getOwner();
		if (owner instanceof DollEntity) {
			// 只伤害敌对生物（Enemy 接口实例），所有非敌对生物自动被保护
			return entity instanceof Enemy && entity.isAffectedByPotions();
		}
		return entity.isAffectedByPotions();
	}
}
