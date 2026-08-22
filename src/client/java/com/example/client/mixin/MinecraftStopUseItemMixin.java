package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复"右键人偶打开物品栏时右键操作被延续"的问题：
 * 26.2 客户端 {@code Minecraft.startUseItem} 对实体命中先走 interact（发包后返回 PASS），
 * PASS 会 fall-through 到 useItem，导致主手弓开始拉弓蓄力、食物开始进食；
 * 服务端随后异步回包 openMenu → setScreen，但 setScreen 本身不会停止物品使用，
 * 于是弓/食物一直持续到玩家松手。
 *
 * <p>在打开任意屏幕（screen != null）时停止正在使用的物品，语义正确且幂等。
 * 26.2 已将 {@code setScreen} 改名为 {@code setScreenAndShow}（内部调 {@code Gui.setScreen}），
 * 所有打开屏幕的路径（含 OpenScreen packet 打开人偶物品栏）都会经过此入口。
 */
@Mixin(Minecraft.class)
public class MinecraftStopUseItemMixin {

	@Shadow
	private LocalPlayer player;

	@Inject(method = "setScreenAndShow", at = @At("HEAD"))
	private void stopUsingItemWhenOpeningScreen(Screen screen, CallbackInfo ci) {
		if (screen != null && this.player != null && this.player.isUsingItem()) {
			this.player.stopUsingItem();
		}
	}
}
