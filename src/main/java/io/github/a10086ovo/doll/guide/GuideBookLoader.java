package io.github.a10086ovo.doll.guide;

import io.github.a10086ovo.doll.DollMod;
import com.google.gson.Gson;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指南书 JSON 加载器。数据格式参考 Patchouli：
 * <pre>
 * data/doll-mod/patchouli_books/guide_book/
 *   book.json                        — 书籍定义
 *   en_us/categories/&lt;id&gt;.json      — 章节（id 取自文件名）
 *   en_us/entries/&lt;id&gt;.json          — 条目（category 字段归入章节）
 * </pre>
 * 加载在客户端进行（传入 {@code Minecraft.getInstance().getResourceManager()}）。
 */
public final class GuideBookLoader {

	private static final Gson GSON = new Gson();
	private static final String BOOK_DIR = "patchouli_books/guide_book";
	private static final String CATEGORY_DIR = BOOK_DIR + "/en_us/categories";
	private static final String ENTRY_DIR = BOOK_DIR + "/en_us/entries";

	private GuideBookLoader() {
	}

	/** 从资源管理器加载整本指南书；任何文件缺失时回退到空定义而非崩溃。 */
	public static GuideBook load(ResourceManager manager) {
		GuideBook book = loadJson(manager, BOOK_DIR + "/book.json", GuideBook.class);
		if (book == null) {
			book = new GuideBook();
		}

		// 章节：文件名即 id
		Map<String, GuideCategory> categories = new HashMap<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources(CATEGORY_DIR,
			p -> p.getPath().endsWith(".json")).entrySet()) {
			GuideCategory cat = loadResource(e.getKey(), e.getValue(), GuideCategory.class);
			if (cat == null) {
				continue;
			}
			cat.id = fileName(e.getKey());
			categories.put(cat.id, cat);
		}

		// 条目：按 category 字段归入章节
		List<GuideEntry> allEntries = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources(ENTRY_DIR,
			p -> p.getPath().endsWith(".json")).entrySet()) {
			GuideEntry entry = loadResource(e.getKey(), e.getValue(), GuideEntry.class);
			if (entry != null) {
				allEntries.add(entry);
			}
		}
		for (GuideEntry entry : allEntries) {
			GuideCategory cat = categories.get(entry.category);
			if (cat != null) {
				cat.entries.add(entry);
			} else {
				DollMod.LOGGER.warn("[GuideBook] 条目 {} 引用了不存在的章节 {}", entry.name, entry.category);
			}
		}

		// 排序
		List<GuideCategory> sorted = new ArrayList<>(categories.values());
		sorted.sort(Comparator.comparingInt(c -> c.sortnum));
		for (GuideCategory cat : sorted) {
			cat.entries.sort(Comparator.comparingInt(e -> e.sortnum));
		}
		book.categories = sorted;
		return book;
	}

	private static <T> T loadJson(ResourceManager manager, String path, Class<T> clazz) {
		Identifier id = Identifier.fromNamespaceAndPath("doll-mod", path);
		return manager.getResource(id)
			.map(res -> loadResource(id, res, clazz))
			.orElse(null);
	}

	private static <T> T loadResource(Identifier id, Resource resource, Class<T> clazz) {
		try (Reader reader = resource.openAsReader()) {
			return GSON.fromJson(reader, clazz);
		} catch (Exception e) {
			DollMod.LOGGER.error("[GuideBook] 解析资源失败 {}: {}", id, e.getMessage());
			return null;
		}
	}

	/** 从资源路径提取文件名（不含 .json 后缀）。 */
	private static String fileName(Identifier id) {
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		String name = slash >= 0 ? path.substring(slash + 1) : path;
		return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
	}
}
