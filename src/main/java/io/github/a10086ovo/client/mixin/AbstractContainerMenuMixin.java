package io.github.a10086ovo.client.mixin;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器槽位越界护栏（对原版缺陷兜底）。
 *
 * <p><b>背景：</b>
 * {@code ClientPacketListener.handleContainerSetSlot} 末尾有一段<b>无条件</b>分支：
 * <pre>
 * if (this.minecraft.gui.screen() instanceof CreativeModeInventoryScreen) {
 *     player.inventoryMenu.setRemoteSlot(slot, stack);   // 不查 containerId，不查 slot 范围
 *     player.inventoryMenu.broadcastChanges();
 * }
 * </pre>
 * {@code player.inventoryMenu} 恒为 <b>46</b> 槽。只要此刻屏幕是创造模式物品栏，
 * 任何 {@code slot >= 46} 的容器包都会在这里直接 {@code slots.get(slot)} 越界 ——
 * 与当前 {@code containerMenu} 是哪个菜单毫无关系。对照字节码，常规分支（写入
 * {@code containerMenu}）才做 {@code getContainerId() == containerMenu.containerId} 判定，
 * 这一段不做。
 *
 * <p><b>为什么要留这道护栏：</b>
 * 主修（{@code GuiSetScreenMixin} 补发关容器包）解决的是本模组自己的漏洞，但它无法覆盖
 * 其它模组、或将来新增入口造成的同类不同步。这里对越界下标直接跳过写入，使客户端不会再
 * 因为一个越界槽位包而断开；同时打一条 WARN 保留现场，便于发现真实的同步异常。
 *
 * <p>正常情况下这条 WARN 永远不会出现 —— 一旦出现，说明仍有"服务端还在同步某个 &gt;46 槽的
 * 菜单，而客户端已经切到创造物品栏"的情形，应据此继续排查。
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

	@Inject(method = "setRemoteSlot", at = @At("HEAD"), cancellable = true)
	private void doll$guardOutOfRangeSlot(int slot, ItemStack stack, CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		int size = self.slots.size();
		if (slot < 0 || slot >= size) {
			DollMod.LOGGER.warn("[DollMenu] 已拦截越界槽位同步（原版创造物品栏分支缺陷）: 菜单={} 槽数={}"
					+ " containerId={} slot={} item={}。若频繁出现请检查是否有菜单未正常关闭。",
				self.getClass().getName(), size, self.containerId, slot, stack);
			ci.cancel();
		}
	}
}
