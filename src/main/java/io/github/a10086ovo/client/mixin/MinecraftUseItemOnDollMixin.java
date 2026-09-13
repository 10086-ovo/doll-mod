package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 源头阻断"右键人偶触发物品使用"：
 * 26.2 客户端 {@code Minecraft.startUseItem} 对实体命中先走 interact（发包后返回 PASS），
 * PASS 会 fall-through 到 {@code useItem}，导致主手弓开始蓄力、食物开始进食。
 * 这里把 useItem 调用重定向：准星目标是 DollEntity 时直接返回 PASS（不触发吃/拉弓），
 * 交互仍由服务端处理（选中/打开背包不受影响）。
 *
 * <p>同时阻断"副手交互包"：26.2 的 {@code startUseItem} 会遍历主手+副手各发一次
 * {@code MultiPlayerGameMode.interact} 实体交互包。人偶的交互只认主手（服务端
 * {@code DollEntity.interact}、指挥棒、刷怪蛋均忽略副手），副手包纯属多余——
 * 某些副手物品（如绑定了人偶的刷怪蛋）会被服务端误当交互处理，导致人偶被意外
 * 回收（直接消失）。这里在源头把副手对 DollEntity 的交互包拦截掉，只保留主手。
 */
@Mixin(Minecraft.class)
public class MinecraftUseItemOnDollMixin {

	@Shadow
	private HitResult hitResult;

	@Redirect(
		method = "startUseItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"
		)
	)
	private InteractionResult skipUseItemOnDoll(MultiPlayerGameMode gameMode, Player player, InteractionHand hand) {
		if (this.hitResult instanceof EntityHitResult entityHit
				&& entityHit.getEntity() instanceof DollEntity) {
			return InteractionResult.PASS;
		}
		return gameMode.useItem(player, hand);
	}

	@Redirect(
		method = "startUseItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"
		)
	)
	private InteractionResult skipOffhandInteractOnDoll(MultiPlayerGameMode gameMode, Player player,
			Entity entity, EntityHitResult entityHit, InteractionHand hand) {
		if (hand == InteractionHand.OFF_HAND && entity instanceof DollEntity) {
			return InteractionResult.PASS;
		}
		return gameMode.interact(player, entity, entityHit, hand);
	}
}
