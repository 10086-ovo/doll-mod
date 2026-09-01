package io.github.a10086ovo.doll.guide;

import java.util.List;
import java.util.Map;

/**
 * 指南书页面。参考 Patchouli 的 page 概念，简化支持三种类型：
 * <ul>
 *   <li>{@code text}：纯文本页（支持 \n 换行）</li>
 *   <li>{@code item}：物品展示页（图标 + 标题 + 描述）</li>
 *   <li>{@code crafting}：合成表页（3×3 网格 + 结果）</li>
 * </ul>
 * 字段由 Gson 直接映射 JSON，未使用的字段保持默认值。
 */
public class GuidePage {
	/** 页面类型：text / item / crafting */
	public String type = "text";
	/** text 页正文；item 页描述 */
	public String text = "";
	/** item 页标题 */
	public String title = "";
	/** item 页展示的物品 ID（如 "doll-mod:doll_egg_s1"） */
	public String item = "";
	/** crafting 页配方图案（每行一个字符串，字符对应 keys） */
	public List<String> pattern = List.of();
	/** crafting 页字符 → 物品 ID 映射 */
	public Map<String, String> keys = Map.of();
	/** crafting 页合成结果物品 ID */
	public String result = "";
}
