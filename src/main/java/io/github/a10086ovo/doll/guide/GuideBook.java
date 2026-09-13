package io.github.a10086ovo.doll.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * 指南书整体定义。字段由 Gson 直接映射 JSON。
 */
public class GuideBook {
	/** 书名（翻译键或纯文本） */
	public String name = "";
	/** 首页欢迎文字 */
	public String landingText = "";
	/** GUI 背景纹理路径（用户绘制，占位为默认值） */
	public String bookTexture = "doll-mod:textures/gui/guide_book.png";
	/** 章节列表（加载时填充） */
	public List<GuideCategory> categories = new ArrayList<>();
}
