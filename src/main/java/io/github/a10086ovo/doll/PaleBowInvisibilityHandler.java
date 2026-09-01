package io.github.a10086ovo.doll;

import io.github.a10086ovo.doll.item.PaleBowItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * 苍白弓被动：玩家主手或副手持有苍白弓时获得持续隐身。
 * <p>
 * 通过服务端每刻（{@code END_SERVER_TICK}）检查玩家手持物品实现：持有则补充一层
 * 短时效隐身。为避免与玩家自饮的隐身药水冲突，仅在玩家<b>当前没有任何隐身效果</b>时才施加，
 * 因此药水提供的隐身不会被覆盖或缩短；松开弓后该层隐身自然到期，玩家恢复可见。
 * <p>
 * 仅在服务端施加（状态效果由服务端权威并同步到客户端）。
 */
public final class PaleBowInvisibilityHandler {

	/** 每次补充的隐身持续时长（tick）。略大于 1 秒，避免逐刻补加产生的可见闪烁。 */
	private static final int INVISIBILITY_DURATION = 30;

	private PaleBowInvisibilityHandler() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PaleBowInvisibilityHandler::onEndTick);
	}

	private static void onEndTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack mainHand = player.getMainHandItem();
			ItemStack offHand = player.getOffhandItem();
			boolean holdingBow = mainHand.getItem() instanceof PaleBowItem
				|| offHand.getItem() instanceof PaleBowItem;
			if (holdingBow && !player.hasEffect(MobEffects.INVISIBILITY)) {
				player.addEffect(new MobEffectInstance(
					MobEffects.INVISIBILITY, INVISIBILITY_DURATION, 0, false, false, false));
			}
		}
	}
}
