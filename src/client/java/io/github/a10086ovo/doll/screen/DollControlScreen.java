package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.mode.DollMode;
import io.github.a10086ovo.doll.network.DollClientNetworking;
import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 人偶控制面板（纯 Screen，非容器）。
 *
 * <p>打开方式：手持指挥棒右键（空气 / 方块 / 箱子 / 非人偶实体），由服务端
 * 收集玩家所有存活人偶并通过 {@link io.github.a10086ovo.doll.network.payload.OpenDollControlPanelPayload}
 * 送达客户端打开。点击人偶只走区域调试（指挥棒原交互），不会打开本面板。
 *
 * <p>两视图：
 * <ul>
 *   <li>列表视图：每行一个人偶（名字+等级 / 模式或状态 / 距离），点击行进入详情；滚轮滚动；
 *   <li>详情视图：8 个模式图标 + 跟随开关，点击即远程切换（复用 select_mode 网络包）。
 * </ul>
 *
 * <p>26.2 坐标系：本屏幕是纯 Screen（无容器 leftPos 平移），{@link MouseButtonEvent#x()}、
 * {@link #mouseMoved}、{@link GuiGraphicsExtractor} 绘图、tooltip 全部使用窗口绝对坐标。
 */
public class DollControlScreen extends Screen {

	private static final int PANEL_W = 250;
	private static final int PANEL_H = 236;
	private static final int HEADER_H = 30;
	private static final int ROW_H = 24;
	private static final int ROWS_VISIBLE = 8;
	private static final int LIST_H = ROW_H * ROWS_VISIBLE;

	// 详情视图模式图标
	private static final int ICON_SIZE = 26;
	private static final int ICON_GAP = 8;
	private static final int ICONS_PER_ROW = 5;

	private static final int COLOR_PANEL_BG = 0xE6101016;
	private static final int COLOR_PANEL_EDGE = 0xFF4A4555;
	private static final int COLOR_ROW_BG = 0xFF1A1722;
	private static final int COLOR_ROW_HOVER = 0xFF2A2438;
	private static final int COLOR_NAME = 0xFFFFFFFF;
	private static final int COLOR_LEVEL = 0xFFFFD75E;
	private static final int COLOR_MODE = 0xFFFFFF55;
	private static final int COLOR_STATUS = 0xFFA0A0A0;
	private static final int COLOR_BAD = 0xFFFF5555;
	private static final int COLOR_HINT = 0xFF7A7487;

	// 跟随开关图标（跟随不是 DollMode 成员，此处单独持有）
	private static final ItemStack FOLLOW_ICON = new ItemStack(Items.LEAD);
	private static final ItemStack RECALL_ICON = new ItemStack(Items.ENDER_PEARL);

	private static final String[] LEVEL_MARKS = { " Ⅰ", " Ⅱ", " Ⅲ" };

	/** 列表标题缓存（避免每帧 Component.translatable 分配）。 */
	private static final Component LIST_TITLE = Component.translatable("container." + DollModConstants.MOD_ID + ".control_panel");

	private final List<DollSnapshot> dolls;
	private DollSnapshot selected;   // null = 列表视图
	private int scrollOffset;
	private int leftPos;
	private int topPos;
	private double lastMouseX;
	private double lastMouseY;

	public DollControlScreen(List<DollSnapshot> dolls) {
		super(Component.translatable("container." + DollModConstants.MOD_ID + ".control_panel"));
		this.dolls = dolls;
	}

	/**
	 * 接收服务端推送的最新人偶快照（switchMode 成功后）：
	 * 同步列表中对应行 + 当前选中行的状态（激活模式 / 跟随 / 盾构机），
	 * 实现点击模式按钮后金框高亮即时跟随。UI 下一帧重绘即生效。
	 */
	public void applySnapshotUpdate(DollSnapshot snap) {
		for (int i = 0; i < dolls.size(); i++) {
			if (dolls.get(i).entityId() == snap.entityId()) {
				dolls.set(i, snap);
				break;
			}
		}
		if (selected != null && selected.entityId() == snap.entityId()) {
			selected = snap;
		}
	}

	@Override
	protected void init() {
		this.leftPos = (this.width - PANEL_W) / 2;
		this.topPos = (this.height - PANEL_H) / 2;
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
		if (selected == null && dolls.size() > ROWS_VISIBLE) {
			int maxOffset = dolls.size() - ROWS_VISIBLE;
			scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + (vDelta > 0 ? -1 : 1)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
		if (event.button() == 0) {
			int mx = (int) event.x();
			int my = (int) event.y();
			if (selected != null) {
				if (handleDetailClick(mx, my)) return true;
			} else {
				if (handleListClick(mx, my)) return true;
			}
		}
		return super.mouseClicked(event, bl);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		// E 键（打开物品栏）关闭面板，与原版容器行为一致
		if (Minecraft.getInstance().options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	/** 列表视图点击：命中行则进入该人偶的详情视图。离线人偶点击坐标复制并预输入 /tp 指令。 */
	private boolean handleListClick(int mx, int my) {
		if (mx < leftPos + 4 || mx >= leftPos + PANEL_W - 4) return false;
		if (my < topPos + HEADER_H || my >= topPos + HEADER_H + LIST_H) return false;
		int row = (my - (topPos + HEADER_H)) / ROW_H + scrollOffset;
		if (row >= 0 && row < dolls.size()) {
			DollSnapshot d = dolls.get(row);
			if (d.entityId() < 0) {
				// 离线人偶：检测是否点击了坐标文本
				int rowY = topPos + HEADER_H + (row - scrollOffset) * ROW_H;
				String coords = d.lastX() + ", " + d.lastY() + ", " + d.lastZ();
				int coordX = leftPos + PANEL_W - 8 - this.font.width(coords);
				int coordY = rowY + 8;
				int coordW = this.font.width(coords);
				int coordH = this.font.lineHeight;
				if (mx >= coordX && mx < coordX + coordW && my >= coordY && my < coordY + coordH) {
					// 复制坐标到剪贴板
					Minecraft.getInstance().keyboardHandler.setClipboard(coords);
					// 关闭面板，打开聊天栏并预输入传送指令（X/Z 加 0.5 站方块中心）
					String tpCmd = "/tp @p " + (d.lastX() + 0.5) + " " + d.lastY() + " " + (d.lastZ() + 0.5);
					Minecraft.getInstance().setScreenAndShow(new ChatScreen(tpCmd, true));
					playClick();
					return true;
				}
				// 离线人偶行未命中坐标文本：不消费事件，传递给 super（原 return true 形成死区）
				return false;
			}
			// 检测是否点击了召回按钮区域（位置与渲染对齐：紧跟模式文本右侧）
			int rowY = topPos + HEADER_H + (row - scrollOffset) * ROW_H;
			String status = statusText(d);
			int statusWidth = this.font.width(status);
			int statusX = leftPos + 120;
			int recallX = statusX + statusWidth + 4;
			if (mx >= recallX && mx < recallX + 20 && my >= rowY + 2 && my < rowY + 22) {
				playClick();
				DollClientNetworking.sendRecallDoll(d.uuid(), d.dimensionName(), d.lastX(), d.lastY(), d.lastZ());
				return true;
			}
			selected = d;
			lastMouseX = mx;
			lastMouseY = my;
			return true;
		}
		return false;
	}

	/** 详情视图点击：返回按钮 / 模式图标。 */
	private boolean handleDetailClick(int mx, int my) {
		// 返回按钮
		if (mx >= leftPos + 6 && mx < leftPos + 6 + 24 && my >= topPos + 6 && my < topPos + 6 + 16) {
			playClick();
			selected = null;
			return true;
		}
		for (int i = 0; i < 9; i++) {
			int x = iconX(i % ICONS_PER_ROW);
			int y = iconY(i / ICONS_PER_ROW);
			if (mx >= x && mx < x + ICON_SIZE && my >= y && my < y + ICON_SIZE) {
				int modeIdx = (i == 8) ? DollMode.FOLLOW_SLOT_INDEX : i;
				playClick();
				DollClientNetworking.sendSelectMode(selected.entityId(), modeIdx);
				return true;
			}
		}
		return false;
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

	// ---- 渲染 ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// 全屏半透明遮罩
		g.fill(0, 0, this.width, this.height, 0x80000000);
		// 面板底 + 四边高光
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_BG);
		g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + 1, COLOR_PANEL_EDGE);
		g.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_H, COLOR_PANEL_EDGE);
		g.fill(leftPos + PANEL_W - 1, topPos, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_EDGE);
		g.fill(leftPos, topPos + PANEL_H - 1, leftPos + PANEL_W, topPos + PANEL_H, COLOR_PANEL_EDGE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (selected != null) {
			renderDetail(g);
		} else {
			renderList(g);
		}
	}

	// ---- 列表视图 ----

	private void renderList(GuiGraphicsExtractor g) {
		g.centeredText(this.font, LIST_TITLE, leftPos + PANEL_W / 2, topPos + 12, COLOR_NAME);

		if (dolls.isEmpty()) {
			g.centeredText(this.font, "没有存活的人偶", leftPos + PANEL_W / 2, topPos + PANEL_H / 2, COLOR_HINT);
			return;
		}

		int visible = Math.min(ROWS_VISIBLE, dolls.size());
		for (int i = 0; i < visible; i++) {
			int idx = scrollOffset + i;
			DollSnapshot d = dolls.get(idx);
			int rowY = topPos + HEADER_H + i * ROW_H;
			boolean hover = isHoveringRow(rowY);
			g.fill(leftPos + 2, rowY, leftPos + PANEL_W - 2, rowY + ROW_H - 1, hover ? COLOR_ROW_HOVER : COLOR_ROW_BG);

			// 名字 + 等级标记
			int tx = leftPos + 8;
			g.text(this.font, d.name(), tx, rowY + 8, COLOR_NAME, true);
			int levelMark = d.level() >= 0 && d.level() < LEVEL_MARKS.length ? d.level() : 0;
			g.text(this.font, LEVEL_MARKS[levelMark], tx + this.font.width(d.name()), rowY + 8, COLOR_LEVEL, true);

			// 模式 / 状态
			String status = statusText(d);
			int statusColor = statusColor(d);
			int statusWidth = this.font.width(status);
			int statusX = leftPos + 120;
			g.text(this.font, status, statusX, rowY + 8, statusColor, true);

			// 离线人偶：显示最后坐标 / 在线人偶：显示召回按钮（紧跟在模式文本右侧）
			if (d.entityId() < 0) {
				// 离线人偶 → 显示坐标
				String coords = d.lastX() + ", " + d.lastY() + ", " + d.lastZ();
				g.text(this.font, coords, leftPos + PANEL_W - 8 - this.font.width(coords), rowY + 8, COLOR_BAD, true);
			} else {
				// 在线人偶 → 召回按钮（末影珍珠图标，紧跟模式文本右侧，避免与距离数字重叠）
				int recallX = statusX + statusWidth + 4;
				boolean recallHover = lastMouseX >= recallX && lastMouseX < recallX + 20
					&& lastMouseY >= rowY + 2 && lastMouseY < rowY + 22;
				g.fill(recallX, rowY + 2, recallX + 20, rowY + 22, recallHover ? 0xFF4A3040 : 0xFF2A1828);
				g.fill(recallX, rowY + 2, recallX + 20, rowY + 3, 0xFF483241);
				g.fill(recallX, rowY + 2, recallX + 3, rowY + 22, 0xFF483241);
				g.fill(recallX + 17, rowY + 2, recallX + 20, rowY + 22, 0xFF0A090D);
				g.fill(recallX, rowY + 21, recallX + 20, rowY + 22, 0xFF0A090D);
				g.item(RECALL_ICON, recallX + 2, rowY + 4);
				if (recallHover) {
					g.setTooltipForNextFrame(this.font, Component.literal("召回"), (int) lastMouseX, (int) lastMouseY);
				}
			}

			// 距离（同维度，离线人偶不显示距离）
			// 若按钮已占空间较大，距离显示自动右移避免重叠
			if (d.inSameDimension() && d.entityId() >= 0) {
				String dist = (int) Math.sqrt(d.distanceSqr()) + "格";
				int distWidth = this.font.width(dist);
				int distRight = leftPos + PANEL_W - 8;
				int distLeft = distRight - distWidth;
				int recallRight = statusX + statusWidth + 4 + 20 + 2; // 按钮右边界 + 最小间距
				if (distLeft < recallRight) {
					distLeft = recallRight; // 被按钮挤占时右移，确保不重叠
				}
				g.text(this.font, dist, distLeft, rowY + 8, COLOR_STATUS, true);
			}
		}

		// 底部滚动指示
		if (dolls.size() > ROWS_VISIBLE) {
			String info = (scrollOffset + 1) + "/" + dolls.size();
			g.text(this.font, info, leftPos + PANEL_W - 8 - this.font.width(info), topPos + HEADER_H + LIST_H + 8,
				COLOR_HINT, true);
		}
	}

	private boolean isHoveringRow(int rowY) {
		return lastMouseX >= leftPos + 2 && lastMouseX < leftPos + PANEL_W - 2
			&& lastMouseY >= rowY && lastMouseY < rowY + ROW_H - 1;
	}

	private static String statusText(DollSnapshot d) {
		if (d.entityId() < 0) return "离线";
		if (!d.inSameDimension()) return dimensionDisplayName(d.dimensionName());
		if (d.isTunneling()) return "掘进中";
		if (d.followEnabled()) return "跟随";
		if (d.activeMode() < 0) return "空闲";
		return DollMode.byIndex(d.activeMode()).getName();
	}

	/** 维度 ResourceLocation path → 中文显示名（未知维度原样返回）。 */
	private static String dimensionDisplayName(String dimPath) {
		return switch (dimPath) {
			case "overworld" -> "主世界";
			case "the_nether" -> "下界";
			case "the_end" -> "末地";
			default -> dimPath;
		};
	}

	private static int statusColor(DollSnapshot d) {
		if (d.entityId() < 0) return COLOR_BAD;
		if (!d.inSameDimension()) return COLOR_BAD;
		if (d.isTunneling() || d.followEnabled() || d.activeMode() >= 0) return COLOR_MODE;
		return COLOR_STATUS;
	}

	// ---- 详情视图 ----

	private void renderDetail(GuiGraphicsExtractor g) {
		// 返回按钮
		boolean backHover = lastMouseX >= leftPos + 6 && lastMouseX < leftPos + 6 + 24
			&& lastMouseY >= topPos + 6 && lastMouseY < topPos + 6 + 16;
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 22, backHover ? COLOR_ROW_HOVER : COLOR_ROW_BG);
		g.fill(leftPos + 6, topPos + 6, leftPos + 30, topPos + 7, 0xFF383241);
		g.fill(leftPos + 6, topPos + 6, leftPos + 7, topPos + 22, 0xFF383241);
		g.fill(leftPos + 29, topPos + 6, leftPos + 30, topPos + 22, 0xFF0A090D);
		g.fill(leftPos + 6, topPos + 21, leftPos + 30, topPos + 22, 0xFF0A090D);
		g.centeredText(this.font, "◀", leftPos + 18, topPos + 10, COLOR_NAME);

		// 标题：人偶名
		g.centeredText(this.font, selected.name(), leftPos + PANEL_W / 2, topPos + 12, COLOR_LEVEL);

		// 模式图标（两行：5 + 4）
		boolean follow = selected.followEnabled();
		int activeMode = selected.activeMode();
		for (int i = 0; i < 9; i++) {
			int col = i % ICONS_PER_ROW;
			int row = i / ICONS_PER_ROW;
			int x = iconX(col);
			int y = iconY(row);
			boolean isFollow = (i == 8);
			boolean active = isFollow ? follow : (i == activeMode);
			boolean hover = lastMouseX >= x && lastMouseX < x + ICON_SIZE
				&& lastMouseY >= y && lastMouseY < y + ICON_SIZE;
			drawIcon(g, x, y, isFollow ? FOLLOW_ICON : DollMode.byIndex(i).getIcon(), active, hover);
			if (hover) {
				Component name = isFollow ? DollMode.getFollowName(follow)
					: DollMode.byIndex(i).getNormalName();
				g.setTooltipForNextFrame(this.font, name, (int) lastMouseX, (int) lastMouseY);
			}
		}

		// 底部提示（挪到模式图标下方空白处，留在面板内）
		g.centeredText(this.font, "点击图标切换模式", leftPos + PANEL_W / 2, topPos + 116, COLOR_HINT);
	}

	private int iconX(int col) {
		int rowW = ICONS_PER_ROW * ICON_SIZE + (ICONS_PER_ROW - 1) * ICON_GAP;
		return leftPos + (PANEL_W - rowW) / 2 + col * (ICON_SIZE + ICON_GAP);
	}

	private int iconY(int row) {
		return topPos + 40 + row * (ICON_SIZE + ICON_GAP);
	}

	private void drawIcon(GuiGraphicsExtractor g, int x, int y, ItemStack icon, boolean active, boolean hovered) {
		g.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, hovered ? COLOR_ROW_HOVER : COLOR_ROW_BG);
		g.fill(x, y, x + ICON_SIZE, y + 1, 0xFF383241);
		g.fill(x, y, x + 1, y + ICON_SIZE, 0xFF383241);
		g.fill(x + ICON_SIZE - 1, y, x + ICON_SIZE, y + ICON_SIZE, 0xFF0A090D);
		g.fill(x, y + ICON_SIZE - 1, x + ICON_SIZE, y + ICON_SIZE, 0xFF0A090D);
		g.item(icon, x + (ICON_SIZE - 16) / 2, y + (ICON_SIZE - 16) / 2);
		if (active) {
			int gold = 0xFFD4AF37;
			g.fill(x, y, x + ICON_SIZE, y + 2, gold);
			g.fill(x, y + ICON_SIZE - 2, x + ICON_SIZE, y + ICON_SIZE, gold);
			g.fill(x, y, x + 2, y + ICON_SIZE, gold);
			g.fill(x + ICON_SIZE - 2, y, x + ICON_SIZE, y + ICON_SIZE, gold);
		}
	}
}
