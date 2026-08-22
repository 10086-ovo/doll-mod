package com.example.doll.mode;

import com.example.doll.DollModConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 8 种行为模式 + 1 个跟随开关。
 * 模式 0-7 互斥，跟随(8) 独立（钓鱼模式除外：钓鱼与跟随互斥但自动切换，
 * 开启其一会自动关闭另一个，无需手动取消）。
 * 物品栏最后一行（36-44）为人偶快捷栏，作为"工具池"存放各模式的前置工具
 * （镐/斧/锄/剑/弓/弩/火把/钓鱼竿等），人偶按当前模式从中查找所需工具；
 * 第 8 格（44）为跟随开关。模式按钮（0-7）与快捷栏格子已解耦，不再一一对应。
 * 点击已激活的模式按钮可取消选择（进入无模式空闲状态）。
 * <p>
 * 显示名统一走翻译键 {@code mode.doll-mod.<name>}（zh_cn/en_us 由 datagen 生成），
 * 按钮高亮状态用黄色（原版常用强调色）。
 */
public enum DollMode {
	MELEE(0, "近战模式", Items.IRON_SWORD),
	RANGED(1, "射手模式", Items.BOW),
	FARM (2, "耕种模式", Items.IRON_HOE),
	FEED (3, "喂食模式", Items.WHEAT),
	CHOP (4, "砍树模式", Items.IRON_AXE),
	MINE (5, "挖矿模式", Items.IRON_PICKAXE),
	TORCH(6, "插火把模式", Items.TORCH),
	FISH (7, "钓鱼模式", Items.FISHING_ROD);

	public static final int FOLLOW_SLOT_INDEX = 8;
	/** 物品栏中快捷栏起始格（ScreenHandler 槽位 36-44） */
	public static final int HOTBAR_SLOT_START = 36;
	public static final int HOTBAR_SLOT_END = 44;

	private final int index;
	private final String name;
	/** 模式图标的物品（GUI 模式按钮/标签栏展示，索引即枚举 ordinal 顺序）。 */
	private final Item iconItem;

	DollMode(int index, String name, Item iconItem) {
		this.index = index;
		this.name = name;
		this.iconItem = iconItem;
	}

	public int getIndex() {
		return index;
	}

	/** 模式的本地化显示名（中文兜底，供按钮等直接展示）。 */
	public String getName() {
		return name;
	}

	/** 模式图标（每次返回新 ItemStack，供 GUI 绘制）。 */
	public ItemStack getIcon() {
		return new ItemStack(iconItem);
	}

	public String lowerCaseName() {
		return this.name().toLowerCase();
	}

	/** 翻译键：mode.doll-mod.<name>（zh_cn/en_us 由 datagen 生成）。 */
	private Component displayName() {
		return Component.translatable("mode." + DollModConstants.MOD_ID + "." + lowerCaseName());
	}

	/** 高亮状态下的显示名（统一黄色，原版常用强调色）。 */
	public Component getHighlightName() {
		return displayName().copy().withStyle(ChatFormatting.YELLOW);
	}

	/** 默认状态下的显示名（不加 formatting，保留原版默认白色+阴影）。 */
	public Component getNormalName() {
		return displayName();
	}

	public static DollMode byIndex(int index) {
		for (DollMode mode : values()) {
			if (mode.index == index) {
				return mode;
			}
		}
		return MELEE;
	}

	/** 跟随开关的显示名（开启时高亮黄色，未开启保持默认）。 */
	public static Component getFollowName(boolean highlight) {
		Component base = Component.translatable("mode." + DollModConstants.MOD_ID + ".follow");
		return highlight ? base.copy().withStyle(ChatFormatting.YELLOW) : base;
	}
}
