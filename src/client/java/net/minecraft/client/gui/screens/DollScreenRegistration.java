package net.minecraft.client.gui.screens;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.screen.DollInventoryScreen;
import io.github.a10086ovo.doll.screen.DollScreenHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 人偶背包屏幕注册入口。
 *
 * 26.2 中 {@link MenuScreens#register} 是 private static 方法，且
 * {@link MenuScreens.ScreenConstructor} 是包级接口：mod 侧无法跨包调用。
 * 本工具类与 MenuScreens 放在同一包内，配合 accessWidener 把 register
 * 与 ScreenConstructor 提升为 accessible，在客户端初始化时完成注册。
 * 之所以不用 mixin：Mixin 类不能放在 net.minecraft.* 包（会造成包污染，
 * 运行时抛 IllegalClassLoadError），因此改为"同包普通类 + AW 提权"。
 */
public final class DollScreenRegistration {

	private DollScreenRegistration() {
	}

	/**
	 * 把 DollScreenHandler 对应的 {@link DollInventoryScreen} 注册进
	 * MenuScreens.SCREENS 表，fabric-menu-api 打开人偶背包时才找得到构造器。
	 */
	public static void register() {
		MenuScreens.<DollScreenHandler, DollInventoryScreen>register(DollMod.DOLL_SCREEN_HANDLER,
			(DollScreenHandler menu, Inventory inventory, Component title) ->
				new DollInventoryScreen(menu, inventory, title));
	}
}