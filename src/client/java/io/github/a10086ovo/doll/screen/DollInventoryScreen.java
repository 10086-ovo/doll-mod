package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.DollVariant;
import io.github.a10086ovo.doll.mode.DollMode;
import io.github.a10086ovo.doll.network.DollClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 人偶背包界面。布局与 DollScreenHandler/DollInventory 一一对应：
 * 顶部标题、人偶 45 格（18x18 每格）、玩家背包 36 格。
 * <p>
 * 模式切换采用"分页标签栏"设计（模仿创造模式物品栏的分离式图标 + 翻页按钮）：
 * 9 个图标（8 行为模式 + 跟随）分 2 页显示，每页最多 6 个 18×18 独立方块图标，
 * 右侧提供 ◀ ▶ 翻页按钮。整体宽度保持标准 176px，避免与 JEI 等右侧面板重叠。
 */
public class DollInventoryScreen extends AbstractContainerScreen<DollScreenHandler> {

	/** 心形 sprite 缓存（避免每帧创建新 Identifier）。 */
	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

	private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
		DollModConstants.MOD_ID, "textures/gui/doll_inventory.png");

	// 顶部图标区几何
	private static final int ICON_W = 18;
	private static final int ICON_H = 18;
	private static final int ICON_X0 = 11;            // 起始 x (相对 leftPos)
	private static final int ICON_Y0 = 4;             // 起始 y (相对 topPos)
	private static final int ICON_GAP = 3;
	private static final int ICONS_PER_PAGE = 6;
	private static final int TOTAL_ICONS = 9;          // 8 模式 + 跟随

	// 翻页按钮几何（位于图标区右侧）
	private static final int PAGER_W = 11;
	private static final int PAGER_H = 18;
	private static final int PAGER_GAP = 3;
	private static final int PAGER_X0 = ICON_X0 + ICONS_PER_PAGE * ICON_W
		+ (ICONS_PER_PAGE - 1) * ICON_GAP + 6;        // = 11 + 108 + 15 + 6 = 140
	private static final int PAGER_Y0 = ICON_Y0;

	private static final int TOTAL_PAGES = (TOTAL_ICONS + ICONS_PER_PAGE - 1) / ICONS_PER_PAGE; // 2

	// 跟随开关图标（跟随不是 DollMode 成员，此处单独持有）
	private static final ItemStack FOLLOW_ICON = new ItemStack(Items.LEAD);
	// 向导人偶统一搜索入口图标
	private static final ItemStack GUIDE_SEARCH_ICON = new ItemStack(Items.COMPASS);

	// 当前页码（仅客户端 UI state）
	private int currentPage = 0;

	public DollInventoryScreen(DollScreenHandler menu, Inventory playerInventory, Component title) {
		// 176 标准宽度；高度 248 = 222 + 26(顶部标签条)
		super(menu, playerInventory, title, 176, 248);
	}

	@Override
	protected void init() {
		super.init();
		this.leftPos = (this.width - this.imageWidth) / 2;
		this.topPos = (this.height - this.imageHeight) / 2;
		this.titleLabelX = 8;
		this.titleLabelY = 30;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 154;
		// 每次打开固定第一页（不做"跳到激活模式所在页"：开了跟随后跟随在第 2 页，
		// 每次打开都会跳到第二页，用户还得翻回第一页，反而碍事）
		this.currentPage = 0;
	}

	/** 当前页第 p 格（0..ICONS_PER_PAGE-1）映射到全局 displayIndex，>= TOTAL_ICONS 则为空位 */
	private int slotDisplayIndex(int p) {
		return currentPage * ICONS_PER_PAGE + p;
	}

	/** 当前页第 p 格对应的"模式 slot"：0..7=模式索引, FOLLOW_SLOT_INDEX=跟随, -1=空位 */
	private int slotModeIndex(int p) {
		int d = slotDisplayIndex(p);
		if (d >= TOTAL_ICONS) return -1;
		if (d == TOTAL_ICONS - 1) return DollMode.FOLLOW_SLOT_INDEX;
		return d;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
		// MouseButtonEvent 的坐标是 GUI 相对坐标，转为绝对坐标以匹配所有位置变量
		double mx = event.x() + this.leftPos;
		double my = event.y() + this.topPos;
		if (event.button() == 0) {
			// 向导人偶统一搜索按钮（打开搜索二级菜单屏）
			DollEntity owner = this.menu.getDollInventory().getOwner();
			if (owner.getDollVariant() == DollVariant.GUIDE) {
				int searchX = leftPos - 24;
				int searchY = topPos + 80;
				if (isHovering(searchX, searchY, 20, 20, mx, my)) {
					playClick();
					Minecraft.getInstance().setScreenAndShow(new GuideSearchScreen(owner.getId()));
					return true;
				}
			}
			int ty = topPos + ICON_Y0;
			// 翻页按钮
			if (currentPage > 0 && isHovering(leftPagerX(), ty, PAGER_W, PAGER_H, mx, my)) {
				playClick();
				currentPage--;
				return true;
			}
			if (currentPage < TOTAL_PAGES - 1
				&& isHovering(rightPagerX(), ty, PAGER_W, PAGER_H, mx, my)) {
				playClick();
				currentPage++;
				return true;
			}
			// 模式图标
			for (int p = 0; p < ICONS_PER_PAGE; p++) {
				int modeIdx = slotModeIndex(p);
				if (modeIdx < 0) continue;
				int tx = iconScreenX(p);
				if (isHovering(tx, ty, ICON_W, ICON_H, mx, my)) {
					playClick();
					DollClientNetworking.sendSelectMode(owner.getId(), modeIdx);
					return true;
				}
			}
		}
		return super.mouseClicked(event, bl);
	}

	/**
	 * 按键反馈：点击按钮时在<b>本机</b>播放原版按钮音。
	 * 与原版 AbstractWidget 完全一致：{@code SimpleSoundInstance.forUI(UI_BUTTON_CLICK, 1.0F)}
	 * （第二个参数是音调；音量恒为 forUI 内部的 0.25，所以与原版按钮同响度）。
	 * 相对音源、无距离衰减、走"界面"音量滑块，只在本机发声——
	 * 绝不会像旧版服务端广播那样被 16 格内其他玩家听到（且响 4 倍）。
	 */
	private static void playClick() {
		Minecraft.getInstance().getSoundManager().play(
			SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private int iconScreenX(int p) {
		return leftPos + ICON_X0 + p * (ICON_W + ICON_GAP);
	}

	private int leftPagerX() {
		return leftPos + PAGER_X0;
	}

	private int rightPagerX() {
		return leftPos + PAGER_X0 + PAGER_W + PAGER_GAP;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		// 背景槽位贴图往左上各移 1 像素，使物品（16x16，仍按 slot.x/slot.y 渲染）
		// 在 18x18 槽位内四边各留 1 像素、视觉居中。
		g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE,
			leftPos - 1, topPos - 1, 0.0F, 0.0F, imageWidth, imageHeight, 176, 248);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractContents(g, mouseX, mouseY, partialTick);

		// extractContents 的 mouseX/mouseY 是 GUI 相对坐标（原点 = leftPos, topPos）。
		// 元素位置都是屏幕绝对坐标，hover/tooltip 判定需做相应转换。
		int absMX = mouseX + this.leftPos;
		int absMY = mouseY + this.topPos;

		// 左侧渲染血条（模仿玩家 HUD 心形图标，每行 10 心 = 20HP，超出换行）
		DollEntity owner = this.menu.getDollInventory().getOwner();
		float health = owner.getHealth();
		float maxHealth = owner.getMaxHealth();
		int hearts = Math.max(1, (int) Math.ceil(maxHealth / 2.0f));
		int heartsPerRow = 10;

		// 26.2 心形使用独立 sprite（hud/heart/container、full、half），不再从 icons.png 取 UV
		// Identifier 已提取为 static final（HEART_CONTAINER/FULL/HALF），避免每帧分配
		int heartStartX = leftPos - heartsPerRow * 9 - 4;

		for (int i = 0; i < hearts; i++) {
			int row = i / heartsPerRow;
			int col = i % heartsPerRow;
			int x = heartStartX + col * 9;
			int y = topPos + 8 + row * 10;
			// 背景（空心容器）
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, y, 9, 9);
			// 前景（满心 / 半心）
			float heartHealth = health - i * 2;
			if (heartHealth >= 2.0F) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, x, y, 9, 9);
			} else if (heartHealth >= 1.0F) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, x, y, 9, 9);
			}
		}

		// ---- 向导人偶统一搜索按钮（仅 GUIDE 变体，位于血条下方，打开搜索菜单屏） ----
		if (owner.getDollVariant() == DollVariant.GUIDE) {
			int searchX = leftPos - 24;
			int searchY = topPos + 80;
			boolean searchHover = isHovering(searchX, searchY, 20, 20, absMX, absMY);
			drawIconBox(g, searchX, searchY, 20, 20);
			if (searchHover) {
				g.fill(searchX, searchY, searchX + 20, searchY + 20, 0x40FFFFFF);
			}
			g.item(GUIDE_SEARCH_ICON, searchX + 2, searchY + 2);
			if (searchHover) {
				g.setTooltipForNextFrame(this.font, Component.translatable("tooltip." + DollModConstants.MOD_ID + ".guide_search"),
					mouseX, mouseY);
			}
		}

		// ---- 顶部模式标签页（分页 + 翻页按钮） ----
		int activeMode = owner.getActiveMode();
		boolean followEnabled = owner.isFollowEnabled();
		int ty = topPos + ICON_Y0;

		for (int p = 0; p < ICONS_PER_PAGE; p++) {
			int modeIdx = slotModeIndex(p);
			int tx = iconScreenX(p);
			if (modeIdx < 0) continue;
			boolean isFollow = (modeIdx == DollMode.FOLLOW_SLOT_INDEX);
			boolean isActive = isFollow ? followEnabled : (modeIdx == activeMode);
			ItemStack icon = isFollow ? FOLLOW_ICON : DollMode.byIndex(modeIdx).getIcon();

			drawIconBox(g, tx, ty, ICON_W, ICON_H);
			g.item(icon, tx + 1, ty + 1);
			if (isActive) {
				drawActiveFrame(g, tx, ty, ICON_W, ICON_H);
			}
			if (isHovering(tx, ty, ICON_W, ICON_H, absMX, absMY)) {
				Component name = isFollow
					? DollMode.getFollowName(followEnabled)
					: DollMode.byIndex(modeIdx).getNormalName();
				// setTooltipForNextFrame 使用 GUI 相对坐标（与 extractContents 参数同系）
				g.setTooltipForNextFrame(this.font, name, mouseX, mouseY);
			}
		}

		// 翻页按钮 ◀ ▶
		boolean hoverLeft = isHovering(leftPagerX(), ty, PAGER_W, PAGER_H, absMX, absMY);
		boolean hoverRight = isHovering(rightPagerX(), ty, PAGER_W, PAGER_H, absMX, absMY);
		drawPager(g, leftPagerX(), ty, PAGER_W, PAGER_H, hoverLeft, currentPage > 0, "◀");
		drawPager(g, rightPagerX(), ty, PAGER_W, PAGER_H, hoverRight,
			currentPage < TOTAL_PAGES - 1, "▶");

		// 翻页按钮 tooltip
		if (currentPage > 0 && hoverLeft) {
			g.setTooltipForNextFrame(this.font, Component.literal("上一页"), mouseX, mouseY);
		} else if (currentPage < TOTAL_PAGES - 1 && hoverRight) {
			g.setTooltipForNextFrame(this.font, Component.literal("下一页"), mouseX, mouseY);
		}
	}

	/** 画一个独立图标方块背景（分离式效果） */
	private void drawIconBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF1F1B26);             // 深底色
		g.fill(x, y, x + w, y + 1, 0xFF383241);             // 顶高光
		g.fill(x, y, x + 1, y + h, 0xFF383241);             // 左高光
		g.fill(x + w - 1, y, x + w, y + h, 0xFF0A090D);     // 右暗边
		g.fill(x, y + h - 1, x + w, y + h, 0xFF0A090D);     // 下暗边
	}

	/** 画激活态金色边框（叠加在图标方块上） */
	private void drawActiveFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int gold = 0xFFD4AF37;
		g.fill(x, y, x + w, y + 2, gold);                   // 顶
		g.fill(x, y + h - 2, x + w, y + h, gold);           // 底
		g.fill(x, y, x + 2, y + h, gold);                   // 左
		g.fill(x + w - 2, y, x + w, y + h, gold);           // 右
	}

	/** 画翻页按钮（带 ◀/▶ 字符） */
	private void drawPager(GuiGraphicsExtractor g, int x, int y, int w, int h,
						   boolean hovered, boolean enabled, String arrow) {
		int bg, fg;
		if (!enabled) {
			bg = 0xFF1A171F;
			fg = 0xFF4A4555;
		} else if (hovered) {
			bg = 0xFF504A5C;
			fg = 0xFFFFFFFF;
		} else {
			bg = 0xFF34303D;
			fg = 0xFFC8C2D0;
		}
		g.fill(x, y, x + w, y + h, bg);
		g.fill(x, y, x + w, y + 1, 0xFF484552);            // 顶高光
		g.fill(x, y, x + 1, y + h, 0xFF484552);            // 左高光
		g.fill(x + w - 1, y, x + w, y + h, 0xFF0A090D);    // 右暗边
		g.fill(x, y + h - 1, x + w, y + h, 0xFF0A090D);    // 下暗边
		// 居中绘制箭头（text 坐标：x 居中, y 基线=cy-3 近似垂直居中）
		g.centeredText(this.font, arrow, x + w / 2, y + h / 2 - 3, fg);
	}

	}