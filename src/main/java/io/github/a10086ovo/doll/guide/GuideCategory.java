package io.github.a10086ovo.doll.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * 指南书章节（一组相关条目）。参考 Patchouli 的 category 概念。
 * 字段由 Gson 直接映射 JSON。
 */
public class GuideCategory {
	/** 章节 ID（如 "getting_started"） */
	public String id = "";
	/** 显示名（翻译键或纯文本） */
	public String name = "";
	/** 图标物品 ID */
	public String icon = "";
	/** 章节描述（翻译键或纯文本） */
	public String description = "";
	/** 书籍内排序 */
	public int sortnum = 0;
	/** 该章节下的条目（加载时填充） */
	public List<GuideEntry> entries = new ArrayList<>();
}
