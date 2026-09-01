package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 将苍白人偶献祭触发的不死图腾 HUD 图标替换为苍白人偶头颅。
 * <p>
 * 原版事件 35 在玩家触发不死图腾效果时，调用 findTotem(player) 获取图腾物品
 * 并在 HUD 上播放物品弹出动效。findTotem 找不到手持图腾时会返回默认的
 * TOTEM_OF_UNDYING 堆栈——导致苍白人偶献祭时 HUD 显示原版图腾图标。
 * <p>
 * 此 Mixin 检测：当本地玩家手中没有 DEATH_PROTECTION 物品时（说明是苍白人偶献祭），
 * 将 displayItemActivation 的参数替换为苍白人偶头颅。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

	@ModifyArg(
		method = "handleEntityEvent",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V"
		),
		index = 0,
		require = 0
	)
	private ItemStack replaceTotemWithPaleDollHead(ItemStack original) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return original;
		}
		// 检查玩家手中是否有真正的图腾（DEATH_PROTECTION 组件）
		boolean hasTotem = false;
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
				hasTotem = true;
				break;
			}
		}
		// 没有真正的图腾 → 苍白人偶献祭触发 → 替换为苍白人偶头颅
		if (!hasTotem) {
			return new ItemStack(DollMod.PALE_DOLL_HEAD);
		}
		return original;
	}
}
