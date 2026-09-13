package io.github.a10086ovo.doll.entity;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.item.DollBatonItem;
import io.github.a10086ovo.doll.util.DollEntityLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一人偶召回服务：物品端（右键蛋）与网络端（遥控器/控制面板）共用。
 * <p>
 * 安全原则：
 * <ul>
 *   <li>坐标始终从 {@link DollRecallRegistry} 服务端数据获取，不信任网络包内客户端坐标（C-6 修复）
 *   <li>区块强加载前先检查存档数据是否存在，避免同步世界生成卡顿
 *   <li>{@code RECALLS_IN_FLIGHT} 防止同一人偶并发召回堆叠
 * </ul>
 * <p>
 * 此类消除了原先 DollSpawnEggItem 与 DollNetworking 各自实现的两套召回逻辑（E-6 修复）。
 */
public final class DollRecallService {

	/** 每 tick 重试次数上限（40 tick = 2 秒）。 */
	private static final int RECALL_RETRY_LIMIT = 40;

	/** 正在召回中的人偶 UUID 字符串集合，防止连点堆叠。 */
	private static final Set<String> RECALLS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private DollRecallService() {
	}

	/** 清空在途召回集合（服务器启动时调用，防止上一会话残留的 UUID 永久阻塞召回）。 */
	public static void clearInFlight() {
		RECALLS_IN_FLIGHT.clear();
	}

	/** 召回结果。 */
	public enum RecallResult {
		/** 已加载且已传送成功 */
		TELEPORTED,
		/** 未加载，已排队强加载并在途重试 */
		IN_FLIGHT,
		/** 人偶不属于该玩家 */
		NOT_OWNER,
		/** 传送失败（无有效落点） */
		NO_SPOT,
		/** 注册表无记录 / 区块无存档数据 / 维度未加载 */
		NOT_FOUND
	}

	/**
	 * 召回人偶到目标位置。
	 * <ol>
	 *   <li>先跨维度查找已加载实体，直接传送；
	 *   <li>未加载则从 {@link DollRecallRegistry} 取服务端坐标（不信任客户端坐标），
	 *       检查区块存档存在后强加载并 tick 重试。
	 * </ol>
	 *
	 * @param targetLevel 传送目标维度（玩家所在维度）
	 * @param player      召回发起者（可为 null，仅用于权限检查与消息发送）
	 * @param dollUuid    人偶引擎 UUID
	 * @param targetPos   传送目标坐标
	 * @return 召回结果
	 */
	public static RecallResult recall(ServerLevel targetLevel, Player player,
									   UUID dollUuid, BlockPos targetPos) {
		// 1. 跨维度查找已加载实体
		DollEntity doll = DollEntityLookup.findLoadedDoll(targetLevel, dollUuid);
		if (doll != null) {
			if (player != null && !doll.isOwnedBy(player)) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".not_your_doll"));
				return RecallResult.NOT_OWNER;
			}
			boolean ok = doll.teleportToSpot(targetLevel, targetPos);
			if (ok) {
				DollBatonItem.clearSelectionForDoll(player, dollUuid);
				if (player != null) {
					player.sendSystemMessage(Component.literal("已召回 " + dollName(doll)));
				}
			} else if (player != null) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".recall_no_spot"));
			}
			return ok ? RecallResult.TELEPORTED : RecallResult.NO_SPOT;
		}

		// 2. 未加载：从 DollRecallRegistry 获取服务端坐标（不信任客户端坐标）
		DollRecallRegistry.DollLocation loc = DollRecallRegistry.get(dollUuid);
		if (loc == null) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".recall_not_found"));
			}
			return RecallResult.NOT_FOUND;
		}

		// 3. 防并发：同一人偶已在途则跳过
		String key = dollUuid.toString();
		if (!RECALLS_IN_FLIGHT.add(key)) {
			return RecallResult.IN_FLIGHT;
		}

		scheduleRecall(targetLevel, dollUuid, targetPos, loc, player, key);
		return RecallResult.IN_FLIGHT;
	}

	/**
	 * 人偶区块未加载时的召回：强制加载其最后位置所在区块，然后每 tick 重试
	 * 直到实体从磁盘加载出来，再执行传送；完成后解除强制加载。
	 * 区块本身已加载时跳过强加载（重试链会等到实体从区块里出来）。
	 */
	private static void scheduleRecall(ServerLevel targetLevel, UUID uuid, BlockPos targetPos,
			DollRecallRegistry.DollLocation loc, Player player, String inFlightKey) {
		MinecraftServer server = targetLevel.getServer();
		ServerLevel dollLevel = server.getLevel(loc.dimension());
		if (dollLevel == null) {
			RECALLS_IN_FLIGHT.remove(inFlightKey);
			if (player != null) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".recall_not_found"));
			}
			return;
		}
		ChunkPos chunk = new ChunkPos(loc.pos().getX() >> 4, loc.pos().getZ() >> 4);
		if (!dollLevel.isLoaded(chunk.getWorldPosition())) {
			// 关键加固：仅当区块在磁盘上已有数据（无需世界生成）时才强加载。
			// 若区块不存在（跨世界残留位置 / 世界重置 / 存档异常），setChunkForced 会
			// 在服务器线程同步生成 proto-chunk——40+ 模组的模组地形生成比原版慢一个
			// 量级，同步建一块可卡数秒（用户实测：干净环境不卡、重模组存档卡）。
			// 存在性检查 getChunk(..., FULL, false)：无数据返回 null 且不触发生成。
			boolean chunkExists = dollLevel.getChunk(chunk.x(), chunk.z(), ChunkStatus.FULL, false) != null;
			if (!chunkExists) {
				RECALLS_IN_FLIGHT.remove(inFlightKey);
				DollMod.LOGGER.warn(
					"[DollRecall] 召回放弃：区块 {} 无存档数据，跳过强加载（避免同步世界生成卡顿）", chunk);
				if (player != null) {
					player.sendSystemMessage(Component.translatable(
						"message." + DollModConstants.MOD_ID + ".recall_not_found"));
				}
				return;
			}
			dollLevel.setChunkForced(chunk.x(), chunk.z(), true);
		}
		attemptRecall(targetLevel, uuid, targetPos, dollLevel, chunk,
			RECALL_RETRY_LIMIT, player, inFlightKey);
	}

	private static void attemptRecall(ServerLevel targetLevel, UUID uuid, BlockPos targetPos,
			ServerLevel dollLevel, ChunkPos chunk, int remainingTries,
			Player player, String inFlightKey) {
		DollEntity doll = DollEntityLookup.findLoadedDoll(dollLevel, uuid);
		if (doll != null) {
			// 区块加载后补做归属校验：仅主人可召回
			if (player != null && doll.isOwnedBy(player)) {
				if (doll.teleportToSpot(targetLevel, targetPos)) {
					DollBatonItem.clearSelectionForDoll(player, uuid);
					player.sendSystemMessage(Component.literal("已召回 " + dollName(doll)));
				}
			}
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			RECALLS_IN_FLIGHT.remove(inFlightKey);
			return;
		}
		if (remainingTries <= 0) {
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			RECALLS_IN_FLIGHT.remove(inFlightKey);
			if (player != null) {
				player.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".recall_not_found"));
			}
			return;
		}
		targetLevel.getServer().execute(
			() -> attemptRecall(targetLevel, uuid, targetPos, dollLevel, chunk,
				remainingTries - 1, player, inFlightKey));
	}

	private static String dollName(DollEntity doll) {
		return doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
	}
}
