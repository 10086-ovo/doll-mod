package io.github.a10086ovo.doll.guide;

import io.github.a10086ovo.doll.DollMod;

/**
 * 指南书数据加载与缓存。
 * <p>
 * 从 {@code assets/doll-mod/guide_book/} 目录加载 JSON 到
 * {@link GuideBook} 数据模型，供 {@link io.github.a10086ovo.doll.screen.GuideBookScreen}
 * 直接渲染。不再编译为原版成书（WrittenBookContent）。
 * <p>
 * 加载在客户端进行（传入 {@code Minecraft.getInstance().getResourceManager()}）。
 */
public final class GuideBookContent {

	private GuideBookContent() {
	}

	/** 客户端缓存：避免每次右键都重新加载。 */
	private static GuideBook cachedBook;

	/** 懒加载并缓存。根据游戏语言加载对应翻译。 */
	public static GuideBook get() {
		if (cachedBook == null) {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			String lang = mc.getLanguageManager().getSelected();
			cachedBook = GuideBookLoader.load(
				mc.getResourceManager(), lang);
		}
		return cachedBook;
	}

	/** 调试 / 重载用。 */
	public static void invalidate() {
		cachedBook = null;
	}
}
