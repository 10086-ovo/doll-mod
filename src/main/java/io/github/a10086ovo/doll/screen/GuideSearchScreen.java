package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.entity.BiomeSearchType;
import io.github.a10086ovo.doll.entity.StructureSearchType;
import io.github.a10086ovo.doll.entity.VillageSearchType;
import io.github.a10086ovo.doll.network.DollClientNetworking;
import io.github.a10086ovo.doll.network.SearchCategory;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 向导人偶统一搜索菜单（纯 Screen，非容器）。
 *
 * <p>打开方式：GUIDE 人偶背包界面点击左侧单一「搜索」按钮，由
 * {@link net.minecraft.client.Minecraft#setScreenAndShow} 进入本屏。整体替代旧的
 * 结构/群系/村庄三套独立二级菜单。
 *
 * <p>两视图：
 * <ul>
 *   <li>类型选择（PICK）：顶部分类页签（结构/群系/村庄）+ 可滚动目标列表
 *       （仅为当前维度存在的目标）。点击某行即发起统一搜索；
 *   <li>搜索结果（RESULTS）：列表从上到下按与玩家水平距离由近及远排列，每行显示目标图标、
 *       名称、水平距离、坐标，右侧一个「√」打卡按钮（点击翻转前端 + 通知服务端持久化）。
 *       右上角「刷新」按钮以玩家当前位置为中心强制重新搜索（半径 100 区块），
 *       其余时候服务端优先回放本玩家缓存，其次复用世界级共享缓存，避免多人反复搜索；确实重新搜索时，
 *       群系在工作线程异步采样，结构/村庄在服务端跨 tick 分片执行（每 tick 有界），结果迟至期间本屏持续呈「搜索中」。
 * </ul>
 *
 * <p>26.2 坐标系：本屏是纯 Screen（无容器平移），事件坐标与绘图均为窗口绝对坐标。
 */
public class GuideSearchScreen extends Screen {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 240;

	// 颜色沿用控制面板同一套深色主题
	private static final int COLOR_PANEL_BG = 0xE6101016;
	private static final int COLOR_PANEL_EDGE = 0xFF4A4555;
	private static final int COLOR_ROW_BG = 0xFF1A1722;
	private static final int COLOR_ROW_HOVER = 0xFF2A2438;
	private static final int COLOR_TAB_SEL = 0xFF4A4555;
	private static final int COLOR_TAB = 0xFF34303D;
	private static final int COLOR_NAME = 0xFFFFFFFF;
	private static final int COLOR_HINT = 0xFF7A7487;
	private static final int COLOR_DIST = 0xFFFFD75E;
	private static final int COLOR_COORD = 0xFF9A93A6;
	private static final int COLOR_MARKED = 0xFF7CFC00;
	private static final int COLOR_UNMARKED = 0xFF6A6470;
	private static final int COLOR_HILITE = 0xFF504A5C;
	private static final int COLOR_EDGE_LIGHT = 0xFF484552;
	private static final int COLOR_EDGE_DARK = 0xFF0A090D;

	private static final int TAB_H = 18;
	private static final int TAB_PAD = 8;
	private static final int TAB_GAP = 4;
	private static final int[] TAB_CATS = { SearchCategory.STRUCTURE, SearchCategory.BIOME, SearchCategory.VILLAGE };

	// 类型选择列表（紧贴页签下沿，去掉搜索框后上移对齐）
	private static final int PICK_TOP = 46;
	private static final int PICK_ROW = 24;
	private static final int PICK_ROWS = (PANEL_H - PICK_TOP - 6) / PICK_ROW;

	// 结果列表
	private static final int RES_TOP = 26;
	private static final int RES_ROW = 26;
	private static final int RES_ROWS = (PANEL_H - RES_TOP - 6) / RES_ROW;
	private static final int MARK_BTN_SIZE = 18;
	private static final int REFRESH_BTN_W = 44;
	private static final int REFRESH_BTN_H = 16;

	private final int dollEntityId;

	private int leftPos;
	private int topPos;
	private double lastMouseX;
	private double lastMouseY;

	// ---- 视图与状态 ----
	private boolean resultsView;                 // false=类型选择, true=搜索结果
	private int category = SearchCategory.STRUCTURE;
	private int pickScroll;
	private int resScroll;
	private int lastPickHoverRow = 1;   // 类型列表末次所指行号（鼠标离列表时依此示之）
	private int lastResHoverRow = 1;    // 结果列表末次所指行号（鼠标离列表时依此示之）
	private List<Pickable> pickByCat = List.of();   // 当前维度该页面下的全部目标
	private Pickable current;                       // 正在展示结果的目标
	private boolean pending;                        // 等待服务端返回搜索结果
	private final List<ResultRow> rows = new ArrayList<>();

	/** 一个可选中的目标（名称经翻译键本地化显示）。 */
	private static final class Pickable {
		final int category;
		final int targetIndex;
		final ItemStack icon;
		final String translationKey;

		Pickable(int category, int targetIndex, ItemStack icon, String translationKey) {
			this.category = category;
			this.targetIndex = targetIndex;
			this.icon = icon;
			this.translationKey = translationKey;
		}

		String localizedName() {
			return Component.translatable(translationKey).getString();
		}
	}

	/** 一行搜索结果。 */
	private static final class ResultRow {
		final int x;
		final int z;
		boolean marked;

		ResultRow(int x, int z, boolean marked) {
			this.x = x;
			this.z = z;
			this.marked = marked;
		}
	}

	public GuideSearchScreen(int dollEntityId) {
		super(Component.translatable("screen." + DollModConstants.MOD_ID + ".guide_search"));
		this.dollEntityId = dollEntityId;
	}

	/** 构造类型列表：仅保留当前维度可搜索的目标（村庄仅主世界）。 */
	private List<Pickable> buildPickablesFor(int cat) {
		ResourceKey<Level> dim = Minecraft.getInstance().level.dimension();
		List<Pickable> list = new ArrayList<>();
		switch (cat) {
			case SearchCategory.STRUCTURE -> {
				for (StructureSearchType t : StructureSearchType.forDimension(dim)) {
					list.add(new Pickable(SearchCategory.STRUCTURE, t.getIndex(), t.getIcon(), t.translationKey()));
				}
			}
			case SearchCategory.BIOME -> {
				for (BiomeSearchType t : BiomeSearchType.forDimension(dim)) {
					list.add(new Pickable(SearchCategory.BIOME, t.getIndex(), t.getIcon(), t.translationKey()));
				}
			}
			case SearchCategory.VILLAGE -> {
				if (dim.equals(Level.OVERWORLD)) {
					for (VillageSearchType t : VillageSearchType.all()) {
						list.add(new Pickable(SearchCategory.VILLAGE, t.getIndex(), t.getIcon(), t.translationKey()));
					}
				}
			}
			default -> {
			}
		}
		return list;
	}

	@Override
	protected void init() {
		this.leftPos = (this.width - PANEL_W) / 2;
		this.topPos = (this.height - PANEL_H) / 2;
		this.pickByCat = buildPickablesFor(category);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- 事件 ----

	@Override
	public void mouseMoved(double x, double y) {
		this.lastMouseX = x;
		this.lastMouseY = y;
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
		if (resultsView) {
			int max = rows.size() - RES_ROWS;
			if (rows.size() > RES_ROWS) {
				resScroll = Math.max(0, Math.min(max, resScroll + (vDelta > 0 ? -1 : 1)));
				return true;
			}
		} else {
			int max = pickByCat.size() - PICK_ROWS;
			if (pickByCat.size() > PICK_ROWS) {
				pickScroll = Math.max(0, Math.min(max, pickScroll + (vDelta > 0 ? -1 : 1)));
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
		if (event.button() == 0) {
			int mx = (int) event.x();
			int my = (int) event.y();
			if (resultsView) {
				if (handleResultsClick(mx, my)) {
					return true;
				}
			} else {
				if (handlePickClick(mx, my)) {
					return true;
				}
			}
		}
		// 未命中任何自定义控件：交回给 super 处理
		return super.mouseClicked(event, bl);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		// E 键（打开物品栏）关闭搜索面板，与原版容器行为一致
		if (Minecraft.getInstance().options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- PICK 视图点击 ----

	private boolean handlePickClick(int mx, int my) {
		// 页签
		for (int i = 0; i < TAB_CATS.length; i++) {
			if (isHovering(tabX(i), topPos + 22, tabW(), TAB_H, mx, my)) {
				playClick();
				if (category != TAB_CATS[i]) {
						category = TAB_CATS[i];
						pickByCat = buildPickablesFor(category);
						pickScroll = 0;
						lastPickHoverRow = 1;
					}
				return true;
			}
		}
		// 目标列表行
		int visible = Math.min(PICK_ROWS, pickByCat.size());
		for (int i = 0; i < visible; i++) {
			Pickable p = pickByCat.get(pickScroll + i);
			int ry = topPos + PICK_TOP + i * PICK_ROW;
			if (isHovering(leftPos + 4, ry, PANEL_W - 8, PICK_ROW - 1, mx, my)) {
				playClick();
				startSearch(p);
				return true;
			}
		}
		return false;
	}

	// ---- RESULTS 视图点击 ----

	private boolean handleResultsClick(int mx, int my) {
		// 返回按钮
		if (isHovering(leftPos + 6, topPos + 6, 24, 16, mx, my)) {
				playClick();
				resultsView = false;
				pending = false;
				rows.clear();
				lastPickHoverRow = 1;
				return true;
			}
		// 刷新按钮：以玩家当前位置为中心强制重新搜索（服务端覆盖缓存）
		if (isHovering(refreshBtnX(), topPos + 6, REFRESH_BTN_W, REFRESH_BTN_H, mx, my)) {
			playClick();
			if (current != null) {
					this.pending = true;
					this.rows.clear();
					this.resScroll = 0;
					this.lastResHoverRow = 1;
					DollClientNetworking.sendSearch(dollEntityId, current.category, current.targetIndex, true);
				}
			return true;
		}
		int visible = Math.min(RES_ROWS, rows.size());
		for (int i = 0; i < visible; i++) {
			ResultRow r = rows.get(resScroll + i);
			int ry = topPos + RES_TOP + i * RES_ROW;
			int bx = leftPos + PANEL_W - 40;
			if (isHovering(bx, ry + (RES_ROW - MARK_BTN_SIZE) / 2, MARK_BTN_SIZE, MARK_BTN_SIZE, mx, my)) {
				playClick();
				r.marked = !r.marked;
				DollClientNetworking.sendToggleMark(current.category, current.targetIndex, r.x, r.z);
				return true;
			}
		}
		return false;
	}

	// ---- 搜索触发与结果接收 ----

	private void startSearch(Pickable p) {
		this.current = p;
		this.pending = true;
		this.rows.clear();
		this.resScroll = 0;
		this.lastResHoverRow = 1;
		this.resultsView = true;
		// refresh=false：服务端有缓存时直接回放上次结果，不重复搜索
		DollClientNetworking.sendSearch(dollEntityId, p.category, p.targetIndex, false);
	}

	/** 服务端结果回调（由 DollClientNetworking 路由进来）。 */
	public void receiveResults(SearchResultsPayload payload) {
		if (current == null
			|| payload.category() != current.category
			|| payload.targetIndex() != current.targetIndex) {
			return;   // 过期/不相干结果，忽略
		}
		this.rows.clear();
		for (SearchResultsPayload.Entry e : payload.results()) {
			this.rows.add(new ResultRow(e.x(), e.z(), e.marked()));
		}
		this.pending = false;
		this.resScroll = 0;
		this.lastResHoverRow = 1;
	}

	// ---- 渲染 ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, 0x80000000);
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_BG);
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + 1, COLOR_PANEL_EDGE);
		g.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_H, COLOR_PANEL_EDGE);
		g.fill(leftPos + PANEL_W - 1, topPos, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_EDGE);
		g.fill(leftPos, topPos + PANEL_H - 1, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_EDGE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (resultsView) {
			if (current == null) {
				resultsView = false;   // 防御：无常驻目标则回类型选择
				return;
			}
			renderResults(g);
		} else {
			renderPick(g);
		}
	}

	private void renderPick(GuiGraphicsExtractor g) {
		// 标题
		g.centeredText(this.font, Component.translatable("screen." + DollModConstants.MOD_ID + ".guide_search"),
			leftPos + PANEL_W / 2, topPos + 12, COLOR_NAME);
		// 页签
		for (int i = 0; i < TAB_CATS.length; i++) {
			int x = tabX(i);
			boolean sel = category == TAB_CATS[i];
			boolean hover = isHovering(x, topPos + 22, tabW(), TAB_H, lastMouseX, lastMouseY);
			g.fill(x, topPos + 22, x + tabW(), topPos + 22 + TAB_H, sel ? COLOR_TAB_SEL : (hover ? COLOR_HILITE : COLOR_TAB));
			g.fill(x, topPos + 22, x + tabW(), topPos + 23, COLOR_EDGE_LIGHT);
			g.fill(x, topPos + 22, x + 1, topPos + 22 + TAB_H, COLOR_EDGE_LIGHT);
			g.centeredText(this.font, tabLabel(i), x + tabW() / 2, topPos + 26, sel ? 0xFFFFFFFF : 0xFFC8C2D0);
		}
		// 目标列表
		if (pickByCat.isEmpty()) {
			g.centeredText(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".search_empty_cat"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			g.centeredText(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".search_pick_hint"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2 + 12, COLOR_HINT);
			return;
		}
		int visible = Math.min(PICK_ROWS, pickByCat.size());
		for (int i = 0; i < visible; i++) {
			Pickable p = pickByCat.get(pickScroll + i);
			int ry = topPos + PICK_TOP + i * PICK_ROW;
			boolean hover = isHovering(leftPos + 4, ry, PANEL_W - 8, PICK_ROW - 1, lastMouseX, lastMouseY);
			g.fill(leftPos + 2, ry, leftPos + PANEL_W - 2, ry + PICK_ROW - 1, hover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			g.item(p.icon, leftPos + 10, ry + 4);
			g.text(this.font, p.localizedName(), leftPos + 32, ry + 7, COLOR_NAME, true);
			if (hover) {
				lastPickHoverRow = pickScroll + i + 1;
				g.setTooltipForNextFrame(this.font, Component.translatable("gui." + DollModConstants.MOD_ID + ".search_pick_hint"),
					(int) lastMouseX, (int) lastMouseY);
			}
		}
		if (pickByCat.size() > PICK_ROWS) {
			int curRow = Math.min(lastPickHoverRow, pickByCat.size());
			String info = curRow + "/" + pickByCat.size();
			g.text(this.font, info, leftPos + PANEL_W - 8 - this.font.width(info), topPos + PICK_TOP + PICK_ROWS * PICK_ROW + 8,
				COLOR_HINT, true);
		}
	}

	private void renderResults(GuiGraphicsExtractor g) {
		// 返回按钮
		boolean backHover = isHovering(leftPos + 6, topPos + 6, 24, 16, lastMouseX, lastMouseY);
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 22, backHover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 7, COLOR_EDGE_LIGHT);
		g.fill(leftPos + 6, topPos + 6, leftPos + 7, topPos + 22, COLOR_EDGE_LIGHT);
		g.fill(leftPos + 29, topPos + 6, leftPos + 30, topPos + 22, COLOR_EDGE_DARK);
		g.fill(leftPos + 6, topPos + 21, leftPos + 30, topPos + 22, COLOR_EDGE_DARK);
		g.centeredText(this.font, "◀", leftPos + 18, topPos + 10, COLOR_NAME);
		// 标题：目标名称
		g.centeredText(this.font, current.localizedName(), leftPos + PANEL_W / 2, topPos + 12, COLOR_DIST);
		// 刷新按钮（右上角）：以当前位置为中心重新搜索
		int rx = refreshBtnX();
		boolean refreshHover = isHovering(rx, topPos + 6, REFRESH_BTN_W, REFRESH_BTN_H, lastMouseX, lastMouseY);
		g.fill(rx, topPos + 6, rx + REFRESH_BTN_W, topPos + 6 + REFRESH_BTN_H, refreshHover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
		g.fill(rx, topPos + 6, rx + REFRESH_BTN_W, topPos + 7, COLOR_EDGE_LIGHT);
		g.fill(rx, topPos + 6, rx + 1, topPos + 6 + REFRESH_BTN_H, COLOR_EDGE_LIGHT);
		g.fill(rx + REFRESH_BTN_W - 1, topPos + 6, rx + REFRESH_BTN_W, topPos + 6 + REFRESH_BTN_H, COLOR_EDGE_DARK);
		g.fill(rx, topPos + 6 + REFRESH_BTN_H - 1, rx + REFRESH_BTN_W, topPos + 6 + REFRESH_BTN_H, COLOR_EDGE_DARK);
		g.centeredText(this.font, Component.translatable("gui." + DollModConstants.MOD_ID + ".search_refresh"),
			rx + REFRESH_BTN_W / 2, topPos + 10, COLOR_NAME);
		if (refreshHover) {
			g.setTooltipForNextFrame(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".search_refresh_hint"),
				(int) lastMouseX, (int) lastMouseY);
		}

		if (pending) {
			g.centeredText(this.font, Component.translatable("gui." + DollModConstants.MOD_ID + ".search_pending"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			return;
		}
		if (rows.isEmpty()) {
			g.centeredText(this.font, Component.translatable("gui." + DollModConstants.MOD_ID + ".search_no_result"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			return;
		}
		int px = Minecraft.getInstance().player.blockPosition().getX();
		int pz = Minecraft.getInstance().player.blockPosition().getZ();
		int visible = Math.min(RES_ROWS, rows.size());
		for (int i = 0; i < visible; i++) {
			ResultRow r = rows.get(resScroll + i);
			int ry = topPos + RES_TOP + i * RES_ROW;
			boolean hover = isHovering(leftPos + 4, ry, PANEL_W - 8, RES_ROW - 1, lastMouseX, lastMouseY);
			if (hover) {
					lastResHoverRow = resScroll + i + 1;
				}
				g.fill(leftPos + 2, ry, leftPos + PANEL_W - 2, ry + RES_ROW - 1, hover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			// 名称
			g.item(current.icon, leftPos + 8, ry + 5);
			String name = current.localizedName();
			g.text(this.font, name, leftPos + 30, ry + 4, COLOR_NAME, true);
			// 坐标（第二行）
			String coord = Component.translatable("gui." + DollModConstants.MOD_ID + ".search_coord", r.x, r.z).getString();
			g.text(this.font, coord, leftPos + 30, ry + 15, COLOR_COORD, true);
			// 水平距离（第一行右侧）
			long dist = longDistSq(px, pz, r.x, r.z);
			String distStr = Component.translatable("gui." + DollModConstants.MOD_ID + ".search_dist",
				String.valueOf((int) Math.sqrt(dist))).getString();
			int distX = leftPos + PANEL_W - 44 - this.font.width(distStr);
			g.text(this.font, distStr, distX, ry + 4, COLOR_DIST, true);
			// 打卡按钮
			int bx = leftPos + PANEL_W - 40;
			int by = ry + (RES_ROW - MARK_BTN_SIZE) / 2;
			boolean btnHover = isHovering(bx, by, MARK_BTN_SIZE, MARK_BTN_SIZE, lastMouseX, lastMouseY);
			g.fill(bx, by, bx + MARK_BTN_SIZE, by + MARK_BTN_SIZE, r.marked ? 0xFF2A5A2A : (btnHover ? 0xFF38303C : 0xFF232024));
			g.fill(bx, by, bx + MARK_BTN_SIZE, by + 1, COLOR_EDGE_LIGHT);
			g.fill(bx, by, bx + 1, by + MARK_BTN_SIZE, COLOR_EDGE_LIGHT);
			g.fill(bx + MARK_BTN_SIZE - 1, by, bx + MARK_BTN_SIZE, by + MARK_BTN_SIZE, COLOR_EDGE_DARK);
			g.fill(bx, by + MARK_BTN_SIZE - 1, bx + MARK_BTN_SIZE, by + MARK_BTN_SIZE, COLOR_EDGE_DARK);
			g.centeredText(this.font, r.marked ? "√" : "○", bx + MARK_BTN_SIZE / 2, by + MARK_BTN_SIZE / 2 - 4,
				r.marked ? COLOR_MARKED : COLOR_UNMARKED);
			if (btnHover) {
				String tip = r.marked ? "search_result_unmark" : "search_result_mark";
				g.setTooltipForNextFrame(this.font,
					Component.translatable("gui." + DollModConstants.MOD_ID + "." + tip),
					(int) lastMouseX, (int) lastMouseY);
			}
		}
		if (rows.size() > RES_ROWS) {
			int curRow = Math.min(lastResHoverRow, rows.size());
			String info = curRow + "/" + rows.size();
			g.text(this.font, info, leftPos + PANEL_W - 8 - this.font.width(info), topPos + RES_TOP + RES_ROWS * RES_ROW + 8,
				COLOR_HINT, true);
		}
	}

	private long longDistSq(int px, int pz, int x, int z) {
		long dx = px - x;
		long dz = pz - z;
		return dx * dx + dz * dz;
	}

	private String tabLabel(int i) {
		String key = switch (TAB_CATS[i]) {
			case SearchCategory.STRUCTURE -> "cat_structure";
			case SearchCategory.BIOME -> "cat_biome";
			default -> "cat_village";
		};
		return Component.translatable("gui." + DollModConstants.MOD_ID + "." + key).getString();
	}

	private int tabW() {
		return (PANEL_W - 2 * TAB_PAD - (TAB_CATS.length - 1) * TAB_GAP) / TAB_CATS.length;
	}

	private int tabX(int i) {
		return leftPos + TAB_PAD + i * (tabW() + TAB_GAP);
	}

	/** 刷新按钮 X（结果视图右上角，与返回按钮同一水平带）。 */
	private int refreshBtnX() {
		return leftPos + PANEL_W - 6 - REFRESH_BTN_W;
	}

	private boolean isHovering(double x, double y, double w, double h, double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** 按键反馈：本机播放原版按钮音（与其余自定义 GUI 一致）。 */
	private static void playClick() {
		Minecraft.getInstance().getSoundManager().play(
			SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}