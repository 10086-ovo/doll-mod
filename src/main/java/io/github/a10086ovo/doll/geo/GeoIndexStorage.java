package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;

/**
 * GeoIndex 落盘（D5）：随存档写入 {@code world/dollmod/geoindex/&lt;dimension&gt;.json.gz}。
 *
 * <p>存储内容：某维度内、各目标结构收集到的候选坐标，按"dimension → 目标 key → 候选坐标列表"
 * 组织。序列化用 {@code Gson}（Minecraft 环境自带），文件经 GZIP 压缩以缩小体积，
 * 因为结构索引由世界种子固定、永不陈腐，适合跨重启长期缓存。
 *
 * <p>落盘时机：服务端停止/世界保存时 flush；加载时机：服务器启动（SERVER_STARTED）。
 * 容量受底图半径约束，属有界数据，无需清库。
 *
 * <p><b>索引格式版本（{@link #SCHEMA_VERSION}）——为什么必须有</b>：本类原假设"结构索引由世界种子
 * 固定、永不陈腐"，故 {@link GeoIndex#merge} 只增不删、加载时整份读回。该假设只对<b>世界</b>成立，
 * 对<b>生成索引的代码</b>不成立：一旦索引语义变更（例如某次修复开始对每个结构做群系级校验、
 * 或把混型集合整类踢出预索引），磁盘上由旧代码生成的桶就变成"脏数据"——它们会与新建的干净点
 * 合并在同一个桶里，且已废弃的桶（如混型集合）根本不会被新代码再写、也就永远不会被清掉，
 * 表现为"代码明明修好了，进游戏看到的还是旧结果"。
 *
 * <p>因此文件里额外存一个格式版本号：<b>加载时版本不符就整份丢弃、触发重建</b>。这样任何
 * 索引语义变更都只需把 {@link #SCHEMA_VERSION} 递增，旧文件自动失效，无需人工删档。
 * 版本号存放在保留键 {@link #SCHEMA_KEY} 下——该键不含 {@code ':'}，与桶键
 * {@code "category:targetIndex"} 的构词规则天然不冲突。
 */
public final class GeoIndexStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	private static final String FILE_PREFIX = "geoindex_";

	/**
	 * 索引文件格式版本。<b>凡是改动索引的语义/桶的构成（而非仅修 bug 而不变语义）都必须 +1</b>，
	 * 否则旧存档里的脏桶不会被清掉。
	 *
	 * <p>版本沿革：
	 * <ul>
	 *   <li>1（隐含，无版本号字段）：结构预索引只对多成员结构集做群系级校验；单成员集合未过滤，
	 *       桶里混有大量"当地根本没有该结构"的坐标。</li>
	 *   <li>2：群系级校验改为对<b>所有</b>结构都做；"允许群系与同结构集兄弟重叠"的混型集合
	 *       （下界堡垒/要塞、基础传送门及其 6 个变体）整类不做预索引，查询回落到实时精确路径。</li>
	 *   <li>3：新增 {@link #BUILT_KEY} 完成标记。此前"加载索引"与"是否需要重建"完全脱钩——
	 *       每次开服都把已落盘的索引<b>从零重扫一遍</b>（实测单次 32s~129s 全白干，见日志
	 *       {@code 停服落盘：0 个维度有新增已写入}），这才是"重进游戏要重新加载"的真正原因。</li>
	 *   <li>4：新增村庄「已确认」桶（键见 {@link GeoIndex#confirmedKey}）。建索引时会挑每类村庄最近的
	 *       若干个做生成校验并存入该桶，搜索时免检直接回放。旧文件没有这个桶 → 若不废弃，村庄搜索
	 *       仍要每次现装配（440~550ms/个），"代码修好了却看不到效果"。</li>
	 *   <li>5：26.3 新增 18 个 {@code abandoned_camp_*} 结构（结构 34→52）与 1 个 {@code dappled_forest}
	 *       群系。结构与群系列表都按注册键字符串排序、下标即 targetIndex，新条目按字母序插在表头，
	 *       使既有下标<b>整体平移</b>（结构 +18、群系 +1）。旧桶里的坐标于是张冠李戴（把远古城市
	 *       当"竹林废弃营地"报给你）且 {@link #BUILT_KEY} 为 true 会跳过重建、永不自愈。</li>
	 * </ul>
	 */
	public static final int SCHEMA_VERSION = 5;

	/** 版本号在文件根对象里的保留键；不含 {@code ':'}，不会与桶键相撞。 */
	private static final String SCHEMA_KEY = "_schema";

	/**
	 * 该维度是否已完成预索引的保留键。为 {@code true} 时启动流程直接跳过该维度的重建
	 * （结构位置由世界种子决定、永不陈腐，落盘即是完整结果）。
	 * 只在 {@code finishDimension} 成功收尾后才写 true，故"建到一半掉线"的维度下次仍会重建。
	 */
	private static final String BUILT_KEY = "_built";

	private GeoIndexStorage() {
	}

	/**
	 * 一次加载的结果。
	 *
	 * @param buckets 桶数据（key = "category:targetIndex"）
	 * @param built   该维度是否已完整预索引（见 {@link #BUILT_KEY}）；false = 需要重建
	 */
	public record Loaded(java.util.Map<String, List<int[]>> buckets, boolean built) {
	}

	/** 索引根目录：世界根目录下的 dollmod/geoindex。 */
	public static Path rootDir(MinecraftServer server) {
		return server.getWorldPath(new LevelResource("dollmod/geoindex"));
	}

	/** 维度 id（如 {@code minecraft:overworld}）含 {@code ':'}，在 Win 上非法，清洗为安全文件名片段。 */
	private static String sanitize(String dimensionId) {
		return dimensionId.replaceAll("[<>:\"/\\\\|?*]", "_");
	}

	/** 某维度索引文件路径。 */
	public static Path fileFor(MinecraftServer server, String dimensionId) {
		return rootDir(server).resolve(FILE_PREFIX + sanitize(dimensionId) + ".json.gz");
	}

	/**
	 * 读取某维度索引文件；不存在、损坏、或<b>格式版本不符</b>时返回空结果（视为"无索引"，触发重建）。
	 * 桶 key = "category:targetIndex"，value = 候选坐标（x,z）。
	 */
	public static Loaded load(MinecraftServer server, String dimensionId) {
		Path f = fileFor(server, dimensionId);
		if (!Files.exists(f)) {
			return new Loaded(new java.util.HashMap<>(), false);
		}
		java.util.Map<String, List<int[]>> out = new java.util.HashMap<>();
		boolean built = false;
		try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(f)), StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(r);
			if (root.isJsonObject()) {
				JsonObject obj = root.getAsJsonObject();
				// 格式版本校验：不符（含旧文件无版本号 = 版本 0）即整份丢弃。
				// 丢弃而非"尽力读一部分"是刻意的：新旧语义混在同一个桶里无法分辨哪些点是脏的。
				JsonElement verEl = obj.get(SCHEMA_KEY);
				int ver = verEl != null && verEl.isJsonPrimitive() ? verEl.getAsInt() : 0;
				if (ver != SCHEMA_VERSION) {
					LOGGER.info("GeoIndex 忽略陈旧索引 {}（文件版本 {} != 当前 {}），将重建", f, ver, SCHEMA_VERSION);
					return new Loaded(new java.util.HashMap<>(), false);
				}
				JsonElement builtEl = obj.get(BUILT_KEY);
				built = builtEl != null && builtEl.isJsonPrimitive() && builtEl.getAsBoolean();
				for (var entry : obj.entrySet()) {
					if (SCHEMA_KEY.equals(entry.getKey()) || BUILT_KEY.equals(entry.getKey())) {
						continue;   // 保留键不是桶
					}
					List<int[]> coords = new ArrayList<>();
					if (entry.getValue().isJsonArray()) {
						for (JsonElement e : entry.getValue().getAsJsonArray()) {
							if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
								JsonArray c = e.getAsJsonArray();
								coords.add(new int[]{c.get(0).getAsInt(), c.get(1).getAsInt()});
							}
						}
					}
					out.put(entry.getKey(), coords);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("GeoIndex 读取失败（忽略，将重建）: {}", f, e);
			return new Loaded(new java.util.HashMap<>(), false);
		}
		return new Loaded(out, built);
	}

	/**
	 * 将某维度索引写盘（覆盖）。
	 * 格式：{ "_schema": 版本, "_built": 是否已完整预索引, "category:targetIndex": [[x,z], ...], ... }，GZIP 压缩。
	 *
	 * @param built 该维度是否已完整预索引完成；true 时下次启动将跳过重建（见 {@link #BUILT_KEY}）
	 * @return true=写入成功；false=失败（调用方据此保留"脏"状态，下次仍会重试，不丢数据）
	 */
	public static synchronized boolean save(MinecraftServer server, String dimensionId,
			java.util.Map<String, List<int[]>> map, boolean built) {
		try {
			Path dir = rootDir(server);
			Files.createDirectories(dir);
			JsonObject root = new JsonObject();
			for (var entry : map.entrySet()) {
				JsonArray coords = new JsonArray();
				for (int[] c : entry.getValue()) {
					JsonArray pair = new JsonArray();
					pair.add(c[0]);
					pair.add(c[1]);
					coords.add(pair);
				}
				root.add(entry.getKey(), coords);
			}
			// 格式版本随文件落盘：下次加载时版本不符即整份丢弃（见 load 与 SCHEMA_VERSION 注释）。
			root.addProperty(SCHEMA_KEY, SCHEMA_VERSION);
			// 完成标记：为 true 时下次启动直接跳过该维度的重建（这是"重进游戏不再白跑上百秒"的关键）。
			root.addProperty(BUILT_KEY, built);
			// 预索引后台线程的逐维度落盘与服务器停止时的统一落盘可能并发执行，
			// 若共用同一 .tmp 文件名会互相 move 掉对方的临时文件而报 NoSuchFileException。
			// 改用唯一临时名，最后一次写入以 REPLACE_EXISTING 原子替换正式文件。
			Path f = fileFor(server, dimensionId);
			Path tmp = f.resolveSibling(f.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
			try (OutputStreamWriter w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8)) {
				w.write(root.toString());
			}
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			LOGGER.warn("GeoIndex 写盘失败: {}", dimensionId, e);
			return false;
		}
	}
}