package io.github.a10086ovo.doll.guide;

import java.util.List;

/**
 * 指南书条目（一个可阅读的章节页组）。参考 Patchouli 的 entry 概念。
 * 字段由 Gson 直接映射 JSON。
 */
public class GuideEntry {
	/** 显示名（翻译键或纯文本） */
	public String name = "";
	/** 图标物品 ID（如 "doll-mod:doll_egg_s1"） */
	public String icon = "";
	/** 所属章节 ID（如 "getting_started"） */
	public String category = "";
	/** 章节内排序 */
	public int sortnum = 0;
	/** 页面列表 */
	public List<GuidePage> pages = List.of();
}
