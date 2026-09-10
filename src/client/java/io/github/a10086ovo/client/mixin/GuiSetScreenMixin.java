package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 封住「容器屏被普通 Screen 顶替时不通知服务端」这个漏洞。
 *
 * <p><b>原版的安全网：</b>
 * {@code AbstractContainerScreen.keyPressed} 里，E 键会走 {@code onClose()}，而
 * {@code AbstractContainerScreen.onClose()} 会先调用 {@code player.closeContainer()}
 * 发出 {@code ServerboundContainerClosePacket}。也就是说，凡是「正常关闭容器屏」的路径，
 * 服务端都会同步关掉菜单。
 *
 * <p><b>漏洞：</b>
 * 用 {@code Minecraft.setScreenAndShow}（= {@code Gui.setScreen}）把容器屏换成**普通 Screen**
 * 时，原版只调用 {@code oldScreen.removed()}；而 {@code AbstractContainerScreen.removed()}
 * 仅执行 {@code menu.removed(player)}，**不发关容器包**。于是服务端仍然开着这个菜单并持续
 * {@code broadcastChanges()}，客户端却已经不在容器屏上了。
 *
 * <p><b>后果（人偶背包 81 槽）：</b>
 * 之后玩家在空屏状态按 E 进入创造模式物品栏时，
 * {@code ClientPacketListener.handleContainerSetSlot} 末尾有一段无条件分支：
 * <pre>
 * if (this.minecraft.gui.screen() instanceof CreativeModeInventoryScreen) {
 *     player.inventoryMenu.setRemoteSlot(slot, stack);   // 恒 46 槽，不查 slot 范围
 * }
 * </pre>
 * 服务端补发的 45~80 号槽位（slot ≥ 46）会直接写入 46 槽的 {@code inventoryMenu}，
 * 抛 {@code IndexOutOfBoundsException} → 客户端断线（界面显示「网络协议错误」）。
 *
 * <p><b>修法：</b>
 * 在 {@code Gui.setScreen} 这个唯一的屏幕切换收口处补一条规则 —— 当**旧屏是容器屏**、
 * **新屏不是容器屏**、且当前客户端仍持有非玩家的自定义菜单时，先补发关容器包。
 * 这正是原版本该做而没做的事，语义上只是把「屏幕已经离开容器」这个事实同步给服务端。
 *
 * <p>不会误伤的情形：
 * <ul>
 *   <li>ESC / E 正常关闭容器屏：{@code onClose()} 已先 {@code closeContainer()}，
 *       {@code containerMenu} 此时已是 {@code inventoryMenu}，守卫直接跳过；</li>
 *   <li>容器屏 → 容器屏（打开另一个菜单）：服务端自己会换菜单，守卫不介入；</li>
 *   <li>无屏状态开屏（按 E 进物品栏、开创造栏）：旧屏为 null，守卫不介入。</li>
 * </ul>
 */
@Mixin(Gui.class)
public class GuiSetScreenMixin {

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void doll$closeContainerWhenReplacingContainerScreen(Screen newScreen, CallbackInfo ci) {
		Gui self = (Gui) (Object) this;
		Screen oldScreen = self.screen();

		// 仅处理「容器屏 → 非容器屏」这一种顶替
		if (oldScreen == newScreen) {
			return;
		}
		if (!(oldScreen instanceof AbstractContainerScreen<?>)) {
			return;
		}
		if (newScreen instanceof AbstractContainerScreen<?>) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.getConnection() == null) {
			return;
		}
		// 玩家自己的物品栏菜单（containerId 恒为 0）不需要关闭，也没有可关的服务端菜单
		if (player.containerMenu == player.inventoryMenu || player.containerMenu.containerId == 0) {
			return;
		}

		DollMod.LOGGER.warn("[DollMenu] 容器屏被普通屏幕顶替，补发关容器包: menu={} containerId={} 新屏={}",
			player.containerMenu.getClass().getSimpleName(), player.containerMenu.containerId,
			newScreen == null ? "null" : newScreen.getClass().getSimpleName());
		// 发 ServerboundContainerClosePacket + 客户端把 containerMenu 复位为 inventoryMenu
		player.closeContainer();
	}
}
