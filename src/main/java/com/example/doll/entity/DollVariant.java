package com.example.doll.entity;

/**
 * 人偶变体类型。决定人偶的外观和能力差异。
 * <ul>
 *   <li>{@link #NONE} - 普通人偶（一至五阶），无特殊能力</li>
 *   <li>{@link #WARDEN} - 幽匿人偶，五阶以上具备恢复/抗性/抗火/满抗击退</li>
 *   <li>{@link #PALE} - 苍白人偶，可在主人受到致命伤害时献祭自身以命相抵</li>
 *   <li>{@link #NETHER} - 下界人偶，火焰附加攻击 + 凋零/灵魂沙免疫 + 下界生物安抚光环</li>
 *   <li>{@link #ENDER} - 末影人偶，龙息喷吐（常规远程）+ 瞬移处决（血量低于阈值的斩杀大招）</li>
 *   <li>{@link #SEA} - 海洋人偶，钓鱼必出宝藏 + 水下适应 + 海洋生物安抚光环</li>
 *   <li>{@link #FOREST} - 森林人偶，自然守护：压制附近陆地敌对生物（不索敌主人）并藤蔓缠绕减速</li>
 * </ul>
 */
public enum DollVariant {
	NONE,
	WARDEN,
	PALE,
	NETHER,
	ENDER,
	SEA,
	FOREST;

	private static final DollVariant[] VALUES = values();

	public static DollVariant byOrdinal(int ordinal) {
		if (ordinal < 0 || ordinal >= VALUES.length) {
			return NONE;
		}
		return VALUES[ordinal];
	}
}
