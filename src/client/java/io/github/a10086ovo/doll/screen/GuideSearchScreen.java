package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.network.DollClientNetworking;
import io.github.a10086ovo.doll.network.SearchCategory;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 向导人偶统一搜索菜单（纯 Screen，非容器）。
 *
 * <p>打开方式：GUIDE 人偶背包界面点击左侧单一「搜索」按钮，由
 * {@link net.minecraft.client.Minecraft#setScreenAndShow} 进入本屏。整体替代旧的
 * 结构/群系/村庄三套独立二级菜单。
 *
 * <p>三视图：
 * <ul>
 *   <li>整合搜索（SEARCH，默认）：初始为空白，顶部一个支持中英文输入的搜索框；
 *       输入关键词即实时筛出当前维度匹配的目标。界内最左侧是一个「☰」按钮，
 *       打开二级目的清单（分类查找）；</li>
 *   <li>分类查找（PICK，二级菜单）：顶部分类页签（结构/群系/村庄）+ 可滚动目标列表，
 *       点击某行即发起统一搜索并与玩家分类归属；</li>
 *   <li>搜索结果（RESULTS）：列表从上到下按与玩家水平距离由近及远排列，每行显示目标图标、
 *       名称、水平距离、坐标，右侧一个「√」打卡按钮（点击翻转前端 + 通知服务端持久化）。
 *       右上角「刷新」按钮以玩家当前位置为中心强制重新搜索（半径 100 区块）。</li>
 * </ul>
 *
 * <p>搜索目标池（SEARCH 视图的实时筛选项）为当前维度下可搜索的结构/群系/村庄的并集；
 * 结果与既有搜索工作流完全一致（服务端优先回放缓存，其次重新搜索）。
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

	// 视图标识
	private static final int VIEW_SEARCH = 0;
	private static final int VIEW_PICK = 1;
	private static final int VIEW_RESULTS = 2;

	// 整合搜索（SEARCH）布局
	private static final int MENU_BTN_W = 20;
	private static final int MENU_BTN_H = 20;
	private static final int SEARCH_BOX_H = 20;
	private static final int SEARCH_TOP = 30;
	private static final int SEARCH_ROW = 24;
	private static final int SEARCH_ROWS = (PANEL_H - SEARCH_TOP - 6) / SEARCH_ROW;

	// 分类查找（PICK）布局
	private static final int PICK_TOP = 46;
	private static final int PICK_ROW = 24;
	private static final int PICK_ROWS = (PANEL_H - PICK_TOP - 6) / PICK_ROW;

	// 结果（RESULTS）布局
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
	private int view = VIEW_SEARCH;
	private int category = SearchCategory.STRUCTURE;
	private int pickScroll;
	private int resScroll;
	private int searchScroll;
	private int lastPickHoverRow = 1;   // 类型列表末次所指行号（鼠标离列表时依此示之）
	private int lastResHoverRow = 1;    // 结果列表末次所指行号（鼠标离列表时依此示之）
	private int lastSearchHoverRow = 1; // 搜索列表末次所指行号
	private List<Pickable> pickByCat = List.of();  // 当前维度该页面下的全部目标
	private List<Pickable> searchPool = List.of(); // SEARCH 视图检索池（三类并集）
	private List<Pickable> searchMatches = List.of(); // 匹配当前关键词的目标
	private Pickable current;          // 正在展示结果的目标
	private boolean pending;           // 等待服务端返回搜索结果
	private boolean notInDimension;    // 服务端标记：目标在当前维度不可能存在
	private final List<ResultRow> rows = new ArrayList<>();

	// 搜索框（支持中英文输入；仅 SEARCH 视图显示）
	private EditBox searchBox;

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
			String s = Component.translatable(translationKey).getString();
			// 原版语言表没有此键时 translatable 会原样回退成键名，
			// 此时改用一个可读英文名兜底，避免界面显示光秃秃的键名字符串。
			if (s.equals(translationKey)) {
				return readableFallback();
			}
			return s;
		}

		/** 兜底可读英文名：取翻译键最后一段 id，下划线换成空格并按空格首字母大写。 */
		String readableFallback() {
			String id = translationKey.substring(translationKey.lastIndexOf('.') + 1);
			id = id.replace('_', ' ');
			StringBuilder sb = new StringBuilder(id.length());
			boolean cap = true;
			for (int i = 0; i < id.length(); i++) {
				char c = id.charAt(i);
				if (c == ' ') {
					sb.append(c);
					cap = true;
				} else if (cap) {
					sb.append(Character.toUpperCase(c));
					cap = false;
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
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

	/** 某分类的目标池：直接读官方注册表的<b>全部</b>条目（注册键排序，序号即目标索引，与对端一致）。 */
	private List<Pickable> buildPickablesFor(int cat) {
		return switch (cat) {
			case SearchCategory.STRUCTURE -> registryStructures(false);
			case SearchCategory.BIOME -> registryBiomes();
			case SearchCategory.VILLAGE -> registryStructures(true);
			default -> List.of();
		};
	}

	/** 全部生物群系（主世界/下界/末地均列出，所属维度由实际搜索判定）。 */
	private List<Pickable> registryBiomes() {
		Registry<Biome> reg = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME);
		List<Identifier> ids = new ArrayList<>(reg.keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		List<Pickable> out = new ArrayList<>(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			Identifier id = ids.get(i);
			out.add(new Pickable(SearchCategory.BIOME, i, genericIcon(SearchCategory.BIOME),
				"biome." + id.getNamespace() + "." + id.getPath()));
		}
		return out;
	}

	/** 全部结构；villageOnly=true 只取村庄类结构（与二级菜单「结构」「村庄」两页一致，避免重复）。
	 *  结构清单由服务端进服时推送（客户端不加载结构注册表），域名取注册键字符串。 */
	private List<Pickable> registryStructures(boolean villagesOnly) {
		List<Identifier> ids = new ArrayList<>();
		for (String s : DollClientNetworking.getStructureCatalog()) {
			Identifier id = Identifier.parse(s);
			if (id.getPath().startsWith("village_") == villagesOnly) {
				ids.add(id);
			}
		}
		ids.sort(Comparator.comparing(Identifier::toString));
		List<Pickable> out = new ArrayList<>(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			Identifier id = ids.get(i);
			int cat = villagesOnly ? SearchCategory.VILLAGE : SearchCategory.STRUCTURE;
			String key = villagesOnly
				? "village.doll-mod." + id.getPath().substring("village_".length())
				: "structure." + id.getNamespace() + "." + id.getPath();
			out.add(new Pickable(cat, i, genericIcon(cat), key));
		}
		return out;
	}

	/** 注册表条目无专属图标，按分类给统一示意图标。 */
	private static ItemStack genericIcon(int cat) {
		return new ItemStack(switch (cat) {
			case SearchCategory.STRUCTURE -> Items.COMPASS;
			case SearchCategory.VILLAGE -> Items.VILLAGER_SPAWN_EGG;
			default -> Items.OAK_SAPLING;
		});
	}

	/** SEARCH 视图检索池：当前维度三类目标并集。 */
	private List<Pickable> buildSearchPool() {
		List<Pickable> pool = new ArrayList<>();
		pool.addAll(buildPickablesFor(SearchCategory.STRUCTURE));
		pool.addAll(buildPickablesFor(SearchCategory.BIOME));
		pool.addAll(buildPickablesFor(SearchCategory.VILLAGE));
		return pool;
	}

	/** 依当前关键词筛出匹配目标（中英文均可：比较本地化名与英文 id/文案）。 */
	private List<Pickable> filterMatches(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		String norm = normalize(query);
		if (norm.isEmpty()) {
			return List.of();
		}
		List<Pickable> out = new ArrayList<>();
		for (Pickable p : searchPool) {
			String zh = normalize(p.localizedName());
			String enId = p.translationKey.substring(p.translationKey.lastIndexOf('.') + 1);
			String enRead = normalize(p.readableFallback());
			if (zh.contains(norm) || enId.contains(norm) || enRead.contains(norm)) {
				out.add(p);
			}
		}
		return out;
	}

	/** 归一化关键词与目标名（转小写、去空格/下划线），便于中英文/英文 id 模糊匹配。 */
	private static String normalize(String s) {
		return s.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
	}

	@Override
	protected void init() {
		this.leftPos = (this.width - PANEL_W) / 2;
		this.topPos = (this.height - PANEL_H) / 2;
		this.pickByCat = buildPickablesFor(category);
		this.searchPool = buildSearchPool();

		// 搜索框（自定义深色底由 extractBackground 绘制，EditBox 只画文字/光标/候选）
		this.searchBox = new EditBox(this.font,
			searchBoxX() + 1, topPos + 7, PANEL_W - (searchBoxX() - leftPos) - 7, SEARCH_BOX_H,
			Component.translatable("gui." + DollModConstants.MOD_ID + ".search_box_hint"));
		this.searchBox.setBordered(false);
		this.searchBox.setTextColor(COLOR_NAME);
		this.searchBox.setTextColorUneditable(COLOR_HINT);
		this.searchBox.setHint(Component.translatable("gui." + DollModConstants.MOD_ID + ".search_box_hint"));
		this.searchBox.setMaxLength(32);
		this.searchBox.setResponder(q -> {
			this.searchMatches = filterMatches(q);
			this.searchScroll = 0;
			this.lastSearchHoverRow = 1;
		});
		this.searchBox.setVisible(true);
		this.searchBox.setCanLoseFocus(true);
		this.addRenderableWidget(this.searchBox);
		// 保守策略：打开搜索界面默认不启用搜索栏（无键入光标），由玩家点击后再输入。
		// 避免自动聚焦在该版本焦点系统下不可靠而出现"光标在闪却打不出字"。
		this.searchBox.setFocused(false);

		if (view != VIEW_SEARCH) {
			view = VIEW_SEARCH;
		}
		this.searchMatches = filterMatches(this.searchBox.getValue());
	}

	private int searchBoxX() {
		return leftPos + MENU_BTN_W + 8;   // 紧跟在最左侧「☰」按钮右侧
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** 
	 * 重写无参 setInitialFocus 钩子：Screen.init(width,height) 在调用本屏 init() 之后还会调用它，
	 * 当最后一次输入为键盘时，它可能用 Tab 遍历自动把焦点抢给搜索框（进而出现"光标在闪"）。
	 * 保守策略要求打开界面时不启用搜索栏，故在合并搜索视图置空实现，阻止其自动聚焦；
	 * 玩家需要输入时点击搜索框（super.mouseClicked 会负责聚焦）。
	 */
	@Override
	protected void setInitialFocus() {
		if (view == VIEW_SEARCH) {
			this.searchBox.setFocused(false);
		} else {
			super.setInitialFocus();
		}
	}

	/** 切换回 SEARCH 视图（不自动聚焦搜索框，与"点击后再输入"一致）。 */
	private void focusSearch() {
		this.view = VIEW_SEARCH;
		this.searchBox.setFocused(false);
		this.searchMatches = filterMatches(this.searchBox.getValue());
		this.searchScroll = 0;
		this.lastSearchHoverRow = 1;
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
		if (view == VIEW_RESULTS) {
			int max = rows.size() - RES_ROWS;
			if (rows.size() > RES_ROWS) {
				resScroll = Math.max(0, Math.min(max, resScroll + (vDelta > 0 ? -1 : 1)));
				return true;
			}
		} else if (view == VIEW_PICK) {
			int max = pickByCat.size() - PICK_ROWS;
			if (pickByCat.size() > PICK_ROWS) {
				pickScroll = Math.max(0, Math.min(max, pickScroll + (vDelta > 0 ? -1 : 1)));
				return true;
			}
		} else {
			int max = searchMatches.size() - SEARCH_ROWS;
			if (searchMatches.size() > SEARCH_ROWS) {
				searchScroll = Math.max(0, Math.min(max, searchScroll + (vDelta > 0 ? -1 : 1)));
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
			if (view == VIEW_RESULTS) {
				if (handleResultsClick(mx, my)) {
					return true;
				}
			} else if (view == VIEW_PICK) {
				if (handlePickClick(mx, my)) {
					return true;
				}
			} else {
				if (handleSearchClick(mx, my)) {
					return true;
				}
			}
		}
		// 未命中任何自定义控件：交回给 super 处理（含搜索框聚焦与文字输入）
		return super.mouseClicked(event, bl);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		// E 键（打开物品栏）关闭搜索面板，与原版容器行为一致；
		// 但当搜索框正在输入时按键应交由输入框（输入 e 应录入而非关屏）。
		if (!this.searchBox.isFocused() && Minecraft.getInstance().options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- SEARCH 视图点击 ----

	private boolean handleSearchClick(int mx, int my) {
		// 最左侧「☰」按钮 → 打开二级分类菜单
		if (isHovering(leftPos + 4, topPos + 6, MENU_BTN_W, MENU_BTN_H, mx, my)) {
			playClick();
			view = VIEW_PICK;
			searchBox.setFocused(false);
			pickScroll = 0;
			lastPickHoverRow = 1;
			return true;
		}
		// 匹配目标行
		int visible = Math.min(SEARCH_ROWS, searchMatches.size());
		for (int i = 0; i < visible; i++) {
			Pickable p = searchMatches.get(searchScroll + i);
			int ry = topPos + SEARCH_TOP + i * SEARCH_ROW;
			if (isHovering(leftPos + 4, ry, PANEL_W - 8, SEARCH_ROW - 1, mx, my)) {
				playClick();
				startSearch(p);
				return true;
			}
		}
		return false;
	}

	// ---- PICK 视图点击 ----

	private boolean handlePickClick(int mx, int my) {
		// 返回整合搜索视图
		if (isHovering(leftPos + 6, topPos + 6, 24, 16, mx, my)) {
			playClick();
			focusSearch();
			return true;
		}
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
		// 返回按钮 → 回到整合搜索视图
		if (isHovering(leftPos + 6, topPos + 6, 24, 16, mx, my)) {
				playClick();
				pending = false;
				rows.clear();
				focusSearch();
				return true;
			}
		// 刷新按钮：以玩家当前位置为中心强制重新搜索（服务端覆盖缓存）
		if (isHovering(refreshBtnX(), topPos + 6, REFRESH_BTN_W, REFRESH_BTN_H, mx, my)) {
			playClick();
			if (current != null) {
						this.pending = true;
						this.rows.clear();
						this.notInDimension = false;
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
		this.notInDimension = false;
		this.resScroll = 0;
		this.lastResHoverRow = 1;
		this.view = VIEW_RESULTS;
		this.searchBox.setFocused(false);
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
		this.notInDimension = payload.notInDimension();
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

		// 最左侧「☰」按钮底（仅 SEARCH 视图；PICK/RESULTS 视图该位置为「返回」按钮，二者互斥避免叠加）
		if (view == VIEW_SEARCH) {
			int bmX = leftPos + 4;
			boolean mHover = isHovering(bmX, topPos + 6, MENU_BTN_W, MENU_BTN_H, lastMouseX, lastMouseY);
			g.fill(bmX, topPos + 6, bmX + MENU_BTN_W, topPos + 6 + MENU_BTN_H, mHover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			g.fill(bmX, topPos + 6, bmX + MENU_BTN_W, topPos + 7, COLOR_EDGE_LIGHT);
			g.fill(bmX, topPos + 6, bmX + 1, topPos + 6 + MENU_BTN_H, COLOR_EDGE_LIGHT);
			g.fill(bmX + MENU_BTN_W - 1, topPos + 6, bmX + MENU_BTN_W, topPos + 6 + MENU_BTN_H, COLOR_EDGE_DARK);
			g.fill(bmX, topPos + 6 + MENU_BTN_H - 1, bmX + MENU_BTN_W, topPos + 6 + MENU_BTN_H, COLOR_EDGE_DARK);
		}

		// 搜索框底（仅 SEARCH 视图：自定义深色输入框底）
		if (view == VIEW_SEARCH) {
			int sx = searchBoxX();
			int sy = topPos + 6;
			int sw = PANEL_W - (sx - leftPos) - 6;
			g.fill(sx, sy, sx + sw, sy + SEARCH_BOX_H, COLOR_ROW_BG);
			g.fill(sx, sy, sx + sw, sy + 1, COLOR_EDGE_LIGHT);
			g.fill(sx, sy, sx + 1, sy + SEARCH_BOX_H, COLOR_EDGE_LIGHT);
			g.fill(sx + sw - 1, sy, sx + sw, sy + SEARCH_BOX_H, COLOR_EDGE_DARK);
			g.fill(sx, sy + SEARCH_BOX_H - 1, sx + sw, sy + SEARCH_BOX_H, COLOR_EDGE_DARK);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		this.searchBox.setVisible(view == VIEW_SEARCH);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (view == VIEW_RESULTS) {
			if (current == null) {
				view = VIEW_SEARCH;   // 防御：无常驻目标则回整合搜索
				return;
			}
			renderResults(g);
		} else if (view == VIEW_PICK) {
			renderPick(g);
		} else {
			renderSearch(g);
		}
	}

	private void renderSearch(GuiGraphicsExtractor g) {
		// 最左侧「☰」按钮符号
		g.centeredText(this.font, "☰", leftPos + 4 + MENU_BTN_W / 2, topPos + 6 + 4, COLOR_NAME);
		if (isHovering(leftPos + 4, topPos + 6, MENU_BTN_W, MENU_BTN_H, lastMouseX, lastMouseY)) {
			g.setTooltipForNextFrame(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".category_menu"),
				(int) lastMouseX, (int) lastMouseY);
		}

		// 初始为空（无关键词）时仅提示
		String query = this.searchBox.getValue();
		if (query == null || query.isBlank()) {
			g.centeredText(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".search_default_hint"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			return;
		}
		if (searchMatches.isEmpty()) {
			g.centeredText(this.font,
				Component.translatable("gui." + DollModConstants.MOD_ID + ".search_no_match"),
				leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			return;
		}
		int visible = Math.min(SEARCH_ROWS, searchMatches.size());
		for (int i = 0; i < visible; i++) {
			Pickable p = searchMatches.get(searchScroll + i);
			int ry = topPos + SEARCH_TOP + i * SEARCH_ROW;
			boolean hover = isHovering(leftPos + 4, ry, PANEL_W - 8, SEARCH_ROW - 1, lastMouseX, lastMouseY);
			g.fill(leftPos + 2, ry, leftPos + PANEL_W - 2, ry + SEARCH_ROW - 1, hover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			g.item(p.icon, leftPos + 10, ry + 4);
			g.text(this.font, p.localizedName(), leftPos + 32, ry + 7, COLOR_NAME, true);
			// 右下角类别标识
			String cat = tabLabelFor(p.category);
			int catW = this.font.width(cat);
			g.text(this.font, cat, leftPos + PANEL_W - 8 - catW, ry + 7, COLOR_HINT, true);
			if (hover) {
				lastSearchHoverRow = searchScroll + i + 1;
				g.setTooltipForNextFrame(this.font,
					Component.translatable("gui." + DollModConstants.MOD_ID + ".search_pick_hint"),
					(int) lastMouseX, (int) lastMouseY);
			}
		}
		if (searchMatches.size() > SEARCH_ROWS) {
			int curRow = Math.min(lastSearchHoverRow, searchMatches.size());
			String info = curRow + "/" + searchMatches.size();
			g.text(this.font, info, leftPos + PANEL_W - 8 - this.font.width(info), topPos + SEARCH_TOP + SEARCH_ROWS * SEARCH_ROW + 8,
				COLOR_HINT, true);
		}
	}

	private void renderPick(GuiGraphicsExtractor g) {
		// 返回整合搜索按钮
		boolean backHover = isHovering(leftPos + 6, topPos + 6, 24, 16, lastMouseX, lastMouseY);
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 22, backHover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 7, COLOR_EDGE_LIGHT);
		g.fill(leftPos + 6, topPos + 6, leftPos + 7, topPos + 22, COLOR_EDGE_LIGHT);
		g.fill(leftPos + 29, topPos + 6, leftPos + 30, topPos + 22, COLOR_EDGE_DARK);
		g.fill(leftPos + 6, topPos + 21, leftPos + 30, topPos + 22, COLOR_EDGE_DARK);
		g.centeredText(this.font, "◀", leftPos + 18, topPos + 10, COLOR_NAME);
		// 分类标题
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
		// 返回整合搜索按钮
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
			// 目标在当前维度不可能存在 → 提示「该维度不存在此结构或群系」；
			// 否则（存在但 1600 格内未搜到）→ 仍显示「1600 格内未找到」。
			String key = notInDimension ? "search_not_in_dim" : "search_no_result";
			g.centeredText(this.font, Component.translatable("gui." + DollModConstants.MOD_ID + "." + key),
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

	private String tabLabelFor(int cat) {
		String key = switch (cat) {
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