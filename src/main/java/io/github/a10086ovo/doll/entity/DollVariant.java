package io.github.a10086ovo.doll.entity;

/**
 * 人偶变体类型。决定人偶的外观和能力差异。
 * <ul>
 *   <li>{@link #NONE} - 普通人偶（一至五阶），无特殊能力</li>
 *   <li>{@link #WARDEN} - 幽匿人偶，高难度变体，与普通等级体系无关（200HP + 恢复VI + 抗性IV + 抗火 + 满击退抗性），需击败野生体后合成获取</li>
 *   <li>{@link #PALE} - 苍白人偶，可在主人受到致命伤害时献祭自身以命相抵</li>
 *   <li>{@link #NETHER} - 下界人偶，火焰附加攻击 + 凋零/灵魂沙免疫 + 下界生物安抚光环</li>
 *   <li>{@link #ENDER} - 末影人偶，龙息喷吐（常规远程）+ 瞬移处决（血量低于阈值的斩杀大招）</li>
 *   <li>{@link #SEA} - 海洋人偶，高效钓鱼 + 水下适应 + 海洋生物安抚光环 + 守卫者激光 + 主人增益光环</li>
 *   <li>{@link #FOREST} - 森林人偶，自然守护：压制附近陆地敌对生物（不索敌主人）并藤蔓缠绕减速</li>
 *   <li>{@link #GUIDE} - 向导人偶，功能型（120HP + 恢复IV + 抗性II），不具备战斗天赋；
 *       引导光环为主人提供速度II + 跳跃II + 免疫凋零；可搜索结构/群系/村庄坐标</li>
 * </ul>
 */
public enum DollVariant {
	NONE,
	WARDEN,
	PALE,
	NETHER,
	ENDER,
	SEA,
	FOREST,
	GUIDE;

	private static final DollVariant[] VALUES = values();

	public static DollVariant byOrdinal(int ordinal) {
		if (ordinal < 0 || ordinal >= VALUES.length) {
			return NONE;
		}
		return VALUES[ordinal];
	}

	/**
	 * 按 name() 字符串解析变体（NBT 持久化用字符串，避免中间插入新变体导致 ordinal 错位）。
	 * 非法/未知 name 一律回落 NONE。
	 */
	public static DollVariant byName(String name) {
		if (name == null || name.isEmpty()) return NONE;
		for (DollVariant v : VALUES) {
			if (v.name().equals(name)) return v;
		}
		return NONE;
	}
}
