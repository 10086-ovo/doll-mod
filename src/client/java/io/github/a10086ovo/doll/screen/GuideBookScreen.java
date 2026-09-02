package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.guide.GuideBook;
import io.github.a10086ovo.doll.guide.GuideBookContent;
import io.github.a10086ovo.doll.guide.GuideCategory;
import io.github.a10086ovo.doll.guide.GuideEntry;
import io.github.a10086ovo.doll.guide.GuidePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指南书自定义 Screen（替代原版 BookViewScreen）。
 *
 * <p>布局：
 * <pre>
 * +-------------------------------------------+
 * | 标题栏                                     |
 * +--------+----------------------------------+
 * | 分类栏  | 内容区                           |
 * | 入门   |  首页 / 条目列表 / 条目页面       |
 * | 基础   |                                  |
 * | ...    |                                  |
 * +--------+----------------------------------+
 * | [<上一页] [返回] [下一页>]  页码           |
 * +-------------------------------------------+
 * </pre>
 *
 * <p>导航状态：
 * <ul>
 *   <li>未选中分类 → 首页（书名 + 欢迎文字 + 分类预览）
 *   <li>选中分类、未选中条目 → 条目列表
 *   <li>选中条目 → 逐页阅读（支持 text/item/crafting 三种页面类型）
 * </ul>
 *
 * <p>数据来源：{@link GuideBookContent#get()} 返回的 {@link GuideBook} 数据模型，
 * JSON 加载由 {@link io.github.a10086ovo.doll.guide.GuideBookLoader} 完成，不改变数据格式。
 */
public class GuideBookScreen extends Screen {

	// ---- 布局常量 ----
	private static final int PANEL_W = 360;
	private static final int PANEL_H = 240;
	private static final int SIDEBAR_W = 100;
	private static final int HEADER_H = 0;
	private static final int FOOTER_H = 36;
	private static final int CAT_ITEM_H = 30;
	private static final int ENTRY_ROW_H = 22;
	private static final int SCROLLBAR_W = 6;

		// ---- 颜色 ----
	private static final int C_BG = 0xE6101016;
	private static final int C_EDGE = 0xFF4A4555;
	private static final int C_SIDEBAR = 0xFF1A1722;
	private static final int C_SIDEBAR_HOVER = 0xFF2A2438;
	private static final int C_CONTENT = 0xFF151218;
	private static final int C_ENTRY_HOVER = 0xFF2A2438;
	private static final int C_TITLE = 0xFFFFD75E;
	private static final int C_BODY = 0xFFD0C0E0;
	private static final int C_HINT = 0xFF7A7487;
	private static final int C_GOLD = 0xFFD4AF37;
	private static final int C_DESC = 0xFF9A90A8;
	private static final int C_BTN_EDGE = 0xFF383241;
	private static final int C_BTN_EDGE_DARK = 0xFF0A090D;
	private static final int C_SLOT_BG = 0xFF2A2438;

	// ---- 导航状态 ----
	private GuideBook book;
	private int selectedCategory = -1;   // -1 = 首页
	private int selectedEntry = -1;      // -1 = 条目列表
	private int currentPage = 0;
	// ---- 条目列表滚动 ----
	private int entryScrollOffset = 0;
	private boolean scrollbarDragging = false;
	private double scrollbarDragStartY;
	private int scrollbarDragStartOffset;

	// ---- 布局坐标 ----
	private int leftPos, topPos;
	private int contentX, contentY, contentW, contentH;
	private int footerY;
	private double lastMouseX, lastMouseY;

	// ---- Tips 轮换 ----
		private static final String[] TIPS_CN = {
			"人偶的坐标搜索在工作线程中执行，多人同服也不卡",
			"野生幽匿人偶的出场动画是纯代码手写的缓动曲线",
			"本模组代码 100% 由 AI 完成"
		};
		private static final String[] TIPS_EN = {
			"Coordinate search runs in a worker thread — no lag on multiplayer servers.",
			"The Wild Warden Doll's emergence animation uses hand-coded easing curves.",
			"100% of this mod's code was written by AI."
		};
		private String[] TIPS = TIPS_CN;
		private String currentTip = TIPS_CN[0];
		private int lastTipIndex = -1;

	// ---- 渲染缓存（避免每帧分配对象，init 时清空）----
	private final Map<String, ItemStack> iconCache = new HashMap<>();
	private final Map<String, List<String>> wrapCache = new HashMap<>();

	public GuideBookScreen() {
			super(Component.literal("Doll Guide Book"));
			String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
			boolean isZh = "zh_cn".equals(lang) || "zh_tw".equals(lang) || "zh_hk".equals(lang);
			TIPS = isZh ? TIPS_CN : TIPS_EN;
			pickRandomTip();
		}

	private void pickRandomTip() {
		int idx;
		do {
			idx = (int) (Math.random() * TIPS.length);
		} while (idx == lastTipIndex && TIPS.length > 1);
		lastTipIndex = idx;
			currentTip = TIPS[idx];
		}

	@Override
	protected void init() {
		this.leftPos = (this.width - PANEL_W) / 2;
		this.topPos = (this.height - PANEL_H) / 2;
		this.contentX = leftPos + SIDEBAR_W + 2;
		this.contentY = topPos + HEADER_H + 2;
		this.contentW = PANEL_W - SIDEBAR_W - 4;
		this.contentH = PANEL_H - HEADER_H - FOOTER_H - 4;
		this.footerY = topPos + PANEL_H - FOOTER_H;
		this.book = GuideBookContent.get();
		// 每次打开书刷新 tips
		pickRandomTip();
		// 重新打开时重置滚动
		this.entryScrollOffset = 0;
		this.scrollbarDragging = false;
		// 清空渲染缓存（内容可能在资源重载后变化）
		this.iconCache.clear();
		this.wrapCache.clear();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
		if (book == null) return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
		if (selectedEntry < 0 && selectedCategory >= 0) {
			GuideCategory cat = book.categories.get(selectedCategory);
			int visibleRows = getVisibleEntryRows();
			int maxOffset = Math.max(0, cat.entries.size() - visibleRows);
			if (maxOffset > 0) {
				int delta = vDelta > 0 ? -1 : 1;
				entryScrollOffset = Math.clamp(entryScrollOffset + delta, 0, maxOffset);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.scrollbarDragging = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (book == null) return super.mouseDragged(event, dragX, dragY);
		if (event.button() == 0 && this.scrollbarDragging && selectedEntry < 0 && selectedCategory >= 0) {
				GuideCategory cat = book.categories.get(selectedCategory);
				int visibleRows = getVisibleEntryRows();
				int maxOffset = Math.max(0, cat.entries.size() - visibleRows);
				if (maxOffset <= 0) return true;

				int trackHeight = getScrollbarTrackHeight();
				double deltaY = lastMouseY - scrollbarDragStartY;
				float ratio = (float) deltaY / trackHeight;
				int deltaOffset = Math.round(ratio * maxOffset);
				entryScrollOffset = Math.clamp(scrollbarDragStartOffset + deltaOffset, 0, maxOffset);
				return true;
			}
		return super.mouseDragged(event, dragX, dragY);
	}

	/** 条目列表可见行数 */
	private int getVisibleEntryRows() {
		return Math.max(1, (contentH - 30) / ENTRY_ROW_H);
	}

	/** 条目列表起始 Y（标题与描述多行后之实际起首；渲染与判定共用同一计算，防阴阳错位） */
	private int getEntryListStartY() {
		GuideCategory cat = book.categories.get(selectedCategory);
		int ty = contentY + 6 + wrapText(cat.name, contentW - 16).size() * this.font.lineHeight;
		if (!cat.description.isEmpty()) {
			ty += 4 + wrapText(cat.description, contentW - 16).size() * this.font.lineHeight;
			return ty + 8;
		}
		return ty + 6;
	}

	/** 滚动条轨道高度 */
	private int getScrollbarTrackHeight() {
		return getVisibleEntryRows() * ENTRY_ROW_H;
	}

	/** 滚动条X坐标 */
	private int getScrollbarX() {
		return contentX + contentW - SCROLLBAR_W - 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ============ 事件 ============

	@Override
	public void mouseMoved(double x, double y) {
		this.lastMouseX = x;
		this.lastMouseY = y;
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
		if (event.button() == 0) {
			int mx = (int) event.x();
			int my = (int) event.y();
			if (handleSidebarClick(mx, my)) return true;
			if (handleContentClick(mx, my)) return true;
			if (handleFooterClick(mx, my)) return true;
		}
		return super.mouseClicked(event, bl);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (Minecraft.getInstance().options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- 点击处理 ----

	private boolean handleSidebarClick(int mx, int my) {
		if (mx < leftPos + 1 || mx >= leftPos + SIDEBAR_W + 1) return false;
		if (my < topPos + HEADER_H || my >= topPos + PANEL_H - FOOTER_H) return false;
		if (book == null) return false;
		int itemIdx = (my - (topPos + HEADER_H)) / CAT_ITEM_H;
		if (itemIdx < 0 || itemIdx >= book.categories.size()) return false;
		playClick();
		if (itemIdx == selectedCategory) {
			// 再次点击同一分类 → 返回首页
			selectedCategory = -1;
			selectedEntry = -1;
		} else {
			selectedCategory = itemIdx;
			selectedEntry = -1;
			entryScrollOffset = 0;
		}
		return true;
	}

	private boolean handleContentClick(int mx, int my) {
		if (mx < contentX || mx >= contentX + contentW) return false;
		if (my < contentY || my >= contentY + contentH) return false;
		if (book == null) return false;

		if (selectedCategory < 0) {
			return handleLandingClick(mx, my);
		}
		if (selectedEntry < 0) {
			if (handleScrollbarClick(mx, my)) return true;
			return handleEntryListClick(mx, my);
		}
		return false;
	}

	private boolean handleScrollbarClick(int mx, int my) {
		GuideCategory cat = book.categories.get(selectedCategory);
		int visibleRows = getVisibleEntryRows();
		int maxOffset = Math.max(0, cat.entries.size() - visibleRows);
		if (maxOffset <= 0) return false;

		int sbX = getScrollbarX();
		int trackTop = getEntryListStartY();
		int trackHeight = getScrollbarTrackHeight();

		if (mx < sbX || mx >= sbX + SCROLLBAR_W || my < trackTop || my >= trackTop + trackHeight) {
			return false;
		}

		// 点击滚动条轨道 → 跳到对应位置
		float clickRatio = (float) (my - trackTop) / trackHeight;
		int targetOffset = Math.round(clickRatio * maxOffset);
		entryScrollOffset = Math.clamp(targetOffset, 0, maxOffset);

		// 如果点在 thumb 上，启动拖动
		int thumbHeight = Math.max(16, trackHeight * visibleRows / cat.entries.size());
		int thumbTop = trackTop + (int) ((float) entryScrollOffset / maxOffset * (trackHeight - thumbHeight));
		if (my >= thumbTop && my < thumbTop + thumbHeight) {
			scrollbarDragging = true;
			scrollbarDragStartY = my;
			scrollbarDragStartOffset = entryScrollOffset;
		}

		playClick();
		return true;
	}

	private boolean handleLandingClick(int mx, int my) {
		// 首页内容区不响应点击（分类从侧栏选）
		return false;
	}

	private boolean handleEntryListClick(int mx, int my) {
		GuideCategory cat = book.categories.get(selectedCategory);
		int visibleRows = getVisibleEntryRows();
		int maxOffset = Math.max(0, cat.entries.size() - visibleRows);
		// 重置滚动偏移到合法范围
		if (entryScrollOffset > maxOffset) entryScrollOffset = maxOffset;

		int listY = getEntryListStartY(); // 标题与描述多行后之实际起首
		for (int i = entryScrollOffset; i < cat.entries.size(); i++) {
			int rowIdx = i - entryScrollOffset;
			if (rowIdx >= visibleRows) break;
			int rowY = listY + rowIdx * ENTRY_ROW_H;
			if (my >= rowY && my < rowY + ENTRY_ROW_H - 2) {
				playClick();
				selectedEntry = i;
				currentPage = 0;
				return true;
			}
		}
		return false;
	}

	private boolean handleFooterClick(int mx, int my) {
		if (my < footerY || my >= footerY + FOOTER_H) return false;
		// 返回按钮
		int backX = leftPos + PANEL_W / 2 - 24;
		if (mx >= backX && mx < backX + 48 && selectedEntry >= 0) {
			playClick();
			selectedEntry = -1;
			return true;
		}
		// 上一页
		int prevX = leftPos + 8;
		if (mx >= prevX && mx < prevX + 60 && selectedEntry >= 0 && currentPage > 0) {
			playClick();
			currentPage--;
			return true;
		}
		// 下一页
		int nextX = leftPos + PANEL_W - 68;
		if (mx >= nextX && mx < nextX + 60 && selectedEntry >= 0) {
			GuideEntry entry = book.categories.get(selectedCategory).entries.get(selectedEntry);
			if (currentPage < entry.pages.size() - 1) {
				playClick();
				currentPage++;
				return true;
			}
		}
		return false;
	}

	private static void playClick() {
		Minecraft.getInstance().getSoundManager().play(
			SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ============ 渲染 ============

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// 全屏遮罩
		g.fill(0, 0, this.width, this.height, 0x80000000);
		// 面板
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, C_BG);
		// 四边
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + 1, C_EDGE);
		g.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_H, C_EDGE);
		g.fill(leftPos + PANEL_W - 1, topPos, leftPos + PANEL_W, topPos + PANEL_H, C_EDGE);
		g.fill(leftPos, topPos + PANEL_H - 1, leftPos + PANEL_W, topPos + PANEL_H, C_EDGE);
		// 分隔线（侧栏与内容之间）
		g.fill(leftPos + SIDEBAR_W, topPos + HEADER_H, leftPos + SIDEBAR_W + 1, topPos + PANEL_H - FOOTER_H, C_EDGE);
		// 分隔线（内容与底栏之间）
		g.fill(leftPos, footerY, leftPos + PANEL_W, footerY + 1, C_EDGE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (book == null) {
			g.centeredText(this.font, "[指南书加载失败]", leftPos + PANEL_W / 2, topPos + PANEL_H / 2, 0xFFFF5555);
			return;
		}

		renderSidebar(g);
		renderContent(g);
		renderFooter(g);

		// 重新绘制面板四边，确保边框始终在最上层
		// （renderSidebar 的分类项背景会覆盖顶边线，导致侧边栏顶部看起来凸出1像素）
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + 1, C_EDGE);
		g.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_H, C_EDGE);
		g.fill(leftPos + PANEL_W - 1, topPos, leftPos + PANEL_W, topPos + PANEL_H, C_EDGE);
		g.fill(leftPos, topPos + PANEL_H - 1, leftPos + PANEL_W, topPos + PANEL_H, C_EDGE);
	}

	// ---- 侧栏 ----

	private void renderSidebar(GuiGraphicsExtractor g) {
		int sbX = leftPos + 1;
		int sbY = topPos + HEADER_H;
		for (int i = 0; i < book.categories.size(); i++) {
			GuideCategory cat = book.categories.get(i);
			int itemY = sbY + i * CAT_ITEM_H;
			boolean hover = isMouseIn(sbX, itemY, SIDEBAR_W, CAT_ITEM_H - 1);
			boolean active = (i == selectedCategory);

			// 背景
			int bgColor = active ? C_SIDEBAR_HOVER : (hover ? C_SIDEBAR_HOVER : C_SIDEBAR);
			g.fill(sbX, itemY, sbX + SIDEBAR_W, itemY + CAT_ITEM_H - 1, bgColor);

			// 激活态金色左边框
			if (active) {
				g.fill(sbX, itemY, sbX + 2, itemY + CAT_ITEM_H - 1, C_GOLD);
			}

			// 图标
			ItemStack icon = resolveItemIcon(cat.icon);
			g.item(icon, sbX + 6, itemY + 7);

			// 名称（图标左侧 26px 起，自动换行完整显示，最多两行垂直居中，不截断）
			int textColor = active ? C_TITLE : (hover ? 0xFFFFFFFF : 0xFFC0C0C0);
			List<String> catLines = wrapText(cat.name, SIDEBAR_W - 26 - 6);
			if (catLines.size() > 2) catLines = catLines.subList(0, 2);
			int catNameY = itemY + Math.max(1, (CAT_ITEM_H - catLines.size() * this.font.lineHeight) / 2);
			for (String line : catLines) {
				g.text(this.font, line, sbX + 26, catNameY, textColor, true);
				catNameY += this.font.lineHeight;
			}
		}
	}

	// ---- 内容区 ----

	private void renderContent(GuiGraphicsExtractor g) {
		if (selectedCategory < 0) {
			renderLanding(g);
		} else if (selectedEntry < 0) {
			renderEntryList(g);
		} else {
			renderEntryPage(g);
		}
	}

	private void renderLanding(GuiGraphicsExtractor g) {
				String title = book.name.isEmpty() ? "Doll Guide Book" : book.name;
				g.centeredText(this.font, title, contentX + contentW / 2, contentY + 8, C_TITLE);

			// 欢迎文字（一句话概括 + 提示）
			String landing = book.landingText.isEmpty()
				? (TIPS == TIPS_CN ? "右键任意人偶开始你的旅程。" : "Right-click any doll to begin your journey.") : book.landingText;
			String[] parts = landing.split("\n", 2);
			String summary = parts[0];
			String hint = parts.length > 1 ? parts[1] : "";

			int cx = contentX + 8;
		int cy = contentY + 24;
		int maxTextWidth = contentW - 16;
		for (String line : wrapText(summary, maxTextWidth)) {
			g.text(this.font, line, cx, cy, C_BODY, true);
			cy += this.font.lineHeight + 1;
		}
		cy += 8;

		// 提示文字（分句显示）
		if (!hint.isEmpty()) {
			for (String paragraph : hint.split("\n")) {
				for (String line : wrapText(paragraph, maxTextWidth)) {
					g.text(this.font, line, cx, cy, C_HINT, true);
					cy += this.font.lineHeight + 1;
				}
				cy += 2;
			}
		}
		}

	private void renderEntryList(GuiGraphicsExtractor g) {
		GuideCategory cat = book.categories.get(selectedCategory);

		// 分类标题（自动换行完整显示，目录名不截断）
		int titleY = contentY + 6;
		for (String line : wrapText(cat.name, contentW - 16)) {
			g.text(this.font, line, contentX + 8, titleY, C_TITLE, true);
			titleY += this.font.lineHeight;
		}

		// 描述（自动换行完整显示，随栏而扩，不复截断）
		if (!cat.description.isEmpty()) {
			int descY = titleY + 4;
			for (String line : wrapText(cat.description, contentW - 16)) {
				g.text(this.font, line, contentX + 8, descY, C_DESC, true);
				descY += this.font.lineHeight;
			}
		}
		int listY = getEntryListStartY();

		// 计算可见行和滚动
		int visibleRows = getVisibleEntryRows();
		int totalEntries = cat.entries.size();
		int maxOffset = Math.max(0, totalEntries - visibleRows);
		if (entryScrollOffset > maxOffset) entryScrollOffset = maxOffset;

		// 条目列表（只渲染可见行，留出滚动条空间）
		int rowWidth = contentW - SCROLLBAR_W - 6;
		for (int i = entryScrollOffset; i < totalEntries; i++) {
			int rowIdx = i - entryScrollOffset;
			if (rowIdx >= visibleRows) break;
			GuideEntry entry = cat.entries.get(i);
			int rowY = listY + rowIdx * ENTRY_ROW_H;
			boolean hover = isMouseIn(contentX, rowY, rowWidth, ENTRY_ROW_H - 2);

			g.fill(contentX + 2, rowY, contentX + rowWidth, rowY + ENTRY_ROW_H - 2,
				hover ? C_ENTRY_HOVER : C_CONTENT);

			// 图标
			ItemStack icon = resolveItemIcon(entry.icon);
			g.item(icon, contentX + 8, rowY + 3);

			// 页数指示
			int pageCount = entry.pages.size();
			if (pageCount > 1) {
				String pages = pageCount + " 页";
				int pagesW = this.font.width(pages);
				g.text(this.font, pages, contentX + rowWidth - 8 - pagesW, rowY + 7, C_HINT, true);
			}

			// 名称（完整显示，条目名正常皆短，不截断）
			g.text(this.font, entry.name, contentX + 30, rowY + 7, 0xFFFFFFFF, true);
		}

		// 滚动条
		if (totalEntries > visibleRows) {
			renderScrollbar(g, entryScrollOffset, maxOffset, visibleRows, totalEntries, listY);
		}
	}

	/** 渲染金色滚动条 */
	private void renderScrollbar(GuiGraphicsExtractor g, int offset, int maxOffset,
								 int visibleRows, int totalRows, int listStartY) {
		int sbX = getScrollbarX();
		int trackTop = listStartY;
		int trackHeight = visibleRows * ENTRY_ROW_H;

		// 轨道背景（暗色）
		g.fill(sbX, trackTop, sbX + SCROLLBAR_W, trackTop + trackHeight, 0xFF0A090D);

		// Thumb（金色，加粗）
		int thumbHeight = Math.max(16, trackHeight * visibleRows / totalRows);
		int thumbTop = trackTop + (int) ((float) offset / maxOffset * (trackHeight - thumbHeight));
		// 判断鼠标是否在 thumb 上（hover 状态）
		boolean thumbHover = lastMouseX >= sbX && lastMouseX < sbX + SCROLLBAR_W
			&& lastMouseY >= thumbTop && lastMouseY < thumbTop + thumbHeight;
		int thumbColor = thumbHover || scrollbarDragging ? C_TITLE : C_GOLD;
		g.fill(sbX, thumbTop, sbX + SCROLLBAR_W, thumbTop + thumbHeight, thumbColor);
		// thumb 内部高亮边框
		g.fill(sbX + 1, thumbTop + 1, sbX + SCROLLBAR_W - 1, thumbTop + 2, 0x60FFFFFF);
	}

	private void renderEntryPage(GuiGraphicsExtractor g) {
		GuideCategory cat = book.categories.get(selectedCategory);
		GuideEntry entry = cat.entries.get(selectedEntry);
		if (currentPage >= entry.pages.size()) {
			currentPage = 0;
		}
		GuidePage page = entry.pages.get(currentPage);

		// 页面副标题已删，正文自内容区顶部偏下起
		int bodyY = contentY + 10;
		switch (page.type.toLowerCase(java.util.Locale.ROOT)) {
			case "text" -> renderTextPage(g, page, bodyY);
			case "item" -> renderItemPage(g, page, bodyY);
			case "crafting" -> renderCraftingPage(g, entry, page, bodyY);
			default -> g.text(this.font, "[未知页面类型: " + page.type + "]", contentX + 8, bodyY, 0xFFFF5555, true);
		}

		// 页码
		int pageY = contentY + contentH - 12;
		String pageInfo = (currentPage + 1) + "/" + entry.pages.size();
		g.centeredText(this.font, pageInfo, contentX + contentW / 2, pageY, C_HINT);
	}

	private void renderTextPage(GuiGraphicsExtractor g, GuidePage page, int startY) {
		List<String> lines = wrapText(page.text, contentW - 16);
		int y = startY;
		for (String line : lines) {
			g.text(this.font, line, contentX + 8, y, C_BODY, true);
			y += this.font.lineHeight + 1;
		}
	}

	private void renderItemPage(GuiGraphicsExtractor g, GuidePage page, int startY) {
		// 物品图标（居中放大效果：用 16x16 即可）
		ItemStack icon = resolveItemIcon(page.item);
		int iconX = contentX + contentW / 2 - 8;
		g.item(icon, iconX, startY);

		// 描述（副标题栏已尽去，正文自图标下方直接起）
		List<String> lines = wrapText(page.text, contentW - 16);
		int y = startY + 22;
		for (String line : lines) {
			g.text(this.font, line, contentX + 8, y, C_BODY, true);
			y += this.font.lineHeight + 1;
		}
	}

		private void renderCraftingPage(GuiGraphicsExtractor g, GuideEntry entry, GuidePage page, int startY) {
		// 3x3 合成网格
		int gridW = 3 * 18;
		int gridX = contentX + 8;
		int gridY = startY;

		for (int row = 0; row < 3; row++) {
			String patternRow = row < page.pattern.size() ? page.pattern.get(row) : "";
			for (int col = 0; col < 3; col++) {
				int slotX = gridX + col * 18;
				int slotY = gridY + row * 18;
				// 槽位背景
				g.fill(slotX, slotY, slotX + 17, slotY + 17, C_SLOT_BG);
				g.fill(slotX, slotY, slotX + 17, slotY + 18, C_BTN_EDGE_DARK);
				g.fill(slotX, slotY, slotX + 18, slotY + 17, C_BTN_EDGE);

				char c = col < patternRow.length() ? patternRow.charAt(col) : ' ';
				if (c != ' ' && c != '_') {
					String itemId = page.keys.get(String.valueOf(c));
					if (itemId != null) {
						ItemStack stack = resolveItemIcon(itemId);
						g.item(stack, slotX + 1, slotY + 1);
					}
				}
			}
		}

		// 结果
		if (page.result != null && !page.result.isEmpty()) {
			ItemStack resultStack = resolveItemIcon(page.result);
			int resultX = gridX + gridW + 12;
			int resultY = gridY + 18;
			g.text(this.font, "->", resultX - 12, resultY + 4, C_HINT, true);
			g.fill(resultX, resultY, resultX + 17, resultY + 17, C_SLOT_BG);
			g.fill(resultX, resultY, resultX + 17, resultY + 18, C_BTN_EDGE_DARK);
			g.fill(resultX, resultY, resultX + 18, resultY + 17, C_BTN_EDGE);
			g.item(resultStack, resultX + 1, resultY + 1);
		}

		// 描述
		if (page.text != null && !page.text.isEmpty()) {
				List<String> lines = wrapText(page.text, contentW - 16);
				int y = gridY + 3 * 18 + 8;
				for (String line : lines) {
					g.text(this.font, line, contentX + 8, y, C_BODY, true);
					y += this.font.lineHeight + 1;
				}
			}
	}

	// ---- 底栏 ----

	private void renderFooter(GuiGraphicsExtractor g) {
		if (selectedEntry < 0) {
			// 底部温馨提示：自动换行完整显示，全文毕显无遗，不复截断
			int maxW = PANEL_W - 16;
			String prefix = (TIPS == TIPS_CN) ? "你知道吗？ " : "Do U know? ";
			List<String> tipLines = wrapText(prefix + currentTip, maxW);
			if (tipLines.size() > 3) tipLines = tipLines.subList(0, 3); // 兜底
			int tipH = tipLines.size() * this.font.lineHeight;
			int tipY = footerY + Math.max(2, (FOOTER_H - tipH) / 2);
			for (String line : tipLines) {
				g.centeredText(this.font, line, leftPos + PANEL_W / 2, tipY, C_HINT);
				tipY += this.font.lineHeight;
			}
			return;
		}

		GuideEntry entry = book.categories.get(selectedCategory).entries.get(selectedEntry);

		// 上一页按钮
			boolean prevEnabled = currentPage > 0;
			String prevLabel = (TIPS == TIPS_CN) ? "< 上一页" : "< Prev";
			drawButton(g, leftPos + 8, footerY + 4, 60, 16, prevLabel, prevEnabled);

		// 返回按钮
			String backLabel = (TIPS == TIPS_CN) ? "返回" : "Back";
			drawButton(g, leftPos + PANEL_W / 2 - 24, footerY + 4, 48, 16, backLabel, true);

			// 下一页按钮
			boolean nextEnabled = currentPage < entry.pages.size() - 1;
			String nextLabel = (TIPS == TIPS_CN) ? "下一页 >" : "Next >";
			drawButton(g, leftPos + PANEL_W - 68, footerY + 4, 60, 16, nextLabel, nextEnabled);
		}

		private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled) {
		boolean hover = isMouseIn(x, y, w, h) && enabled;
		int bg = enabled ? (hover ? C_SIDEBAR_HOVER : C_SIDEBAR) : 0xFF0A090D;
		g.fill(x, y, x + w, y + h, bg);
		g.fill(x, y, x + w, y + 1, C_BTN_EDGE);
		g.fill(x, y, x + 1, y + h, C_BTN_EDGE);
		g.fill(x + w - 1, y, x + w, y + h, C_BTN_EDGE_DARK);
		g.fill(x, y + h - 1, x + w, y + h, C_BTN_EDGE_DARK);
		int color = enabled ? (hover ? C_TITLE : 0xFFC0C0C0) : C_HINT;
		g.centeredText(this.font, label, x + w / 2, y + 4, color);
	}

	// ============ 工具 ============

	private boolean isMouseIn(int x, int y, int w, int h) {
		return lastMouseX >= x && lastMouseX < x + w && lastMouseY >= y && lastMouseY < y + h;
	}

	/**
	 * 手动文字换行：按 \n 分段，每段内逐字符累加宽度，
	 * 超过 maxWidth 则断行。返回每行的 String 列表。
	 * <p>中文无空格，必须逐字检查宽度；英文单词可能被截断，
	 * 但在有限宽度内优先保证不溢出。
	 */
	private List<String> wrapText(String text, int maxWidth) {
		if (text == null || text.isEmpty()) return List.of();
		// 命中缓存直接返回，避免每帧重建 ArrayList + StringBuilder + 逐字宽度计算
		String cacheKey = maxWidth + ":" + text;
		List<String> cached = wrapCache.get(cacheKey);
		if (cached != null) return cached;

		List<String> result = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			if (paragraph.isEmpty()) {
				result.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < paragraph.length(); i++) {
				char c = paragraph.charAt(i);
				String candidate = line.toString() + c;
				if (this.font.width(candidate) > maxWidth) {
					if (!line.isEmpty()) {
							result.add(line.toString());
							line = new StringBuilder(String.valueOf(c));
						} else {
						// 单个字符即超宽，强制放入避免死循环
						line.append(c);
					}
				} else {
					line.append(c);
				}
			}
			if (line.length() > 0) {
				result.add(line.toString());
			}
		}
		wrapCache.put(cacheKey, result);
			return result;
		}

		/**
		 * 把字符串形式的物品 ID（如 "doll-mod:doll_egg_s1"）解析为 ItemStack。
	 * 找不到时返回屏障图标作为占位。
	 */
	private ItemStack resolveItemIcon(String itemId) {
		if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
		// 命中缓存直接返回，避免每帧 new ItemStack + 注册表查找
		ItemStack cached = iconCache.get(itemId);
		if (cached != null) return cached;
		ItemStack result;
		try {
			int colon = itemId.indexOf(':');
			Identifier id;
			if (colon >= 0) {
				id = Identifier.fromNamespaceAndPath(itemId.substring(0, colon), itemId.substring(colon + 1));
			} else {
				id = Identifier.fromNamespaceAndPath("minecraft", itemId);
			}
			// MC 26.2: BuiltInRegistries.ITEM.get() 返回 Optional<Holder.Reference<Item>>
			var holder = BuiltInRegistries.ITEM.get(id);
			if (holder.isEmpty()) {
				result = new ItemStack(Items.BARRIER);
			} else {
				Item item = holder.get().value();
					result = new ItemStack(item);
			}
		} catch (Exception e) {
			result = new ItemStack(Items.BARRIER);
		}
		iconCache.put(itemId, result);
		return result;
	}
}
