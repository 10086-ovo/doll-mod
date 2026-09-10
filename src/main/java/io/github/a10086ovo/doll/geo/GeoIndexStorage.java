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
 */
public final class GeoIndexStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	private static final String FILE_PREFIX = "geoindex_";

	private GeoIndexStorage() {
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
	 * 读取某维度索引文件；不存在或损坏时返回空 Map。
	 * key = "category:targetIndex"，value = 候选坐标（x,z）。
	 */
	public static java.util.Map<String, List<int[]>> load(MinecraftServer server, String dimensionId) {
		Path f = fileFor(server, dimensionId);
		if (!Files.exists(f)) {
			return new java.util.HashMap<>();
		}
		java.util.Map<String, List<int[]>> out = new java.util.HashMap<>();
		try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(f)), StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(r);
			if (root.isJsonObject()) {
				for (var entry : root.getAsJsonObject().entrySet()) {
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
			return new java.util.HashMap<>();
		}
		return out;
	}

	/**
	 * 将某维度索引写盘（覆盖）。格式：{ "category:targetIndex": [[x,z], ...], ... }，GZIP 压缩。
	 */
	public static synchronized void save(MinecraftServer server, String dimensionId, java.util.Map<String, List<int[]>> map) {
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
			// 预索引后台线程的逐维度落盘与服务器停止时的统一落盘可能并发执行，
			// 若共用同一 .tmp 文件名会互相 move 掉对方的临时文件而报 NoSuchFileException。
			// 改用唯一临时名，最后一次写入以 REPLACE_EXISTING 原子替换正式文件。
			Path f = fileFor(server, dimensionId);
			Path tmp = f.resolveSibling(f.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
			try (OutputStreamWriter w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8)) {
				w.write(root.toString());
			}
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("GeoIndex 写盘失败: {}", dimensionId, e);
		}
	}
}