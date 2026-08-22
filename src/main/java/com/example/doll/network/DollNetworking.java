package com.example.doll.network;

import com.example.doll.DollModConstants;
import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollRecallRegistry;
import com.example.doll.network.payload.DollSnapshot;
import com.example.doll.network.payload.OpenDollControlPanelPayload;
import com.example.doll.network.payload.RecallDollPayload;
import com.example.doll.network.payload.SelectDollModePayload;
import com.example.doll.network.payload.UpdateDollSnapshotPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.ArrayList;
import java.util.List;

public final class DollNetworking {

	private DollNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SelectDollModePayload.TYPE, SelectDollModePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RecallDollPayload.TYPE, RecallDollPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDollControlPanelPayload.TYPE, OpenDollControlPanelPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateDollSnapshotPayload.TYPE, UpdateDollSnapshotPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectDollModePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
			Entity entity = player.level().getEntity(payload.dollEntityId());
			if (!(entity instanceof DollEntity doll)) {
				return;
			}
				// 仅主人可切换模式（远程指挥：同维度、实体在加载范围内即可，无距离限制）
				// 统一走 isOwnedBy：owner 为 null（未设置 / NBT 异常）时同样拒绝，避免任意玩家越权操控
				if (!doll.isOwnedBy(player)) {
					player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
					return;
				}
				boolean ok = doll.switchMode(payload.modeSlot08());
				if (ok) {
					// 切模式成功：向 owner 发实时快照，控制面板据此更新激活高亮
					// （远程切模式要求实体同维度，dimensionName 即玩家所在维度）
					String name = doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
					String dimName = player.level().dimension().identifier().getPath();
					BlockPos dp = doll.blockPosition();
					DollSnapshot snap = new DollSnapshot(
						doll.getId(), doll.getUUID().toString(), name, doll.getDollLevel(), doll.getActiveMode(),
						doll.isFollowEnabled(), doll.isTunneling(),
						true, (int) doll.distanceToSqr(player), dimName,
						dp.getX(), dp.getY(), dp.getZ());
					ServerPlayNetworking.send(player, new UpdateDollSnapshotPayload(snap));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RecallDollPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				java.util.UUID uuid;
				try {
					uuid = java.util.UUID.fromString(payload.dollUuid());
				} catch (IllegalArgumentException e) {
					return;
				}
				recallDoll(player, uuid, payload.dimensionName(), payload.lastX(), payload.lastY(), payload.lastZ());
			});
		});
	}

	/**
	 * 加强版人偶召回：优先找已加载实体，未加载时依据包内坐标强制加载区块，
	 * 不设"无存档数据就放弃"的限制。
	 */
	private static void recallDoll(ServerPlayer player, java.util.UUID uuid, String dimName, int lastX, int lastY, int lastZ) {
		if (player == null) return;
		ServerLevel playerLevel = (ServerLevel) player.level();

		// 1. 先查出人偶的 UUID 实体（跨维度、加载中）
		Entity entity = playerLevel.getEntityInAnyDimension(uuid);
		if (entity instanceof DollEntity doll && !doll.isRemoved()) {
			if (!doll.isOwnedBy(player)) {
				player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
				return;
			}
			if (doll.teleportToSpot(playerLevel, player.blockPosition())) {
				player.sendSystemMessage(Component.literal("已召回 " + (doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶")));
			}
			return;
		}

		// 2. 未加载：根据包内坐标强制加载区块
		ServerLevel dollLevel = playerLevel.getServer().getLevel(
			ResourceKey.create(Registries.DIMENSION,
				Identifier.fromNamespaceAndPath("minecraft", dimName)));
		if (dollLevel == null) {
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".recall_not_found"));
			return;
		}

		// 强制加载区块（无论有无存档数据）
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(lastX >> 4, lastZ >> 4);
		dollLevel.setChunkForced(chunk.x(), chunk.z(), true);

		// 每 tick 重试，等待实体从磁盘加载
		recallRetry(playerLevel, dollLevel, uuid, chunk, 40, player);
	}

	private static void recallRetry(ServerLevel targetLevel, ServerLevel dollLevel,
			java.util.UUID uuid, net.minecraft.world.level.ChunkPos chunk, int remainingTries, ServerPlayer player) {
		Entity entity = dollLevel.getEntity(uuid);
		if (entity instanceof DollEntity doll && !doll.isRemoved()) {
			if (doll.isOwnedBy(player)) {
				if (doll.teleportToSpot(targetLevel, player.blockPosition())) {
					player.sendSystemMessage(Component.literal("已召回 " + (doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶")));
				}
			}
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			return;
		}
		if (remainingTries <= 0) {
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".recall_not_found"));
			return;
		}
		targetLevel.getServer().execute(
			() -> recallRetry(targetLevel, dollLevel, uuid, chunk, remainingTries - 1, player));
	}

	/**
	 * 收集玩家所有存活人偶（跨维度扫描）并发送控制面板数据包。
	 * 同时也从 DollRecallRegistry 中查出已卸载的人偶，一并列出供召回。
	 */
	public static void sendControlPanel(ServerPlayer player) {
		List<DollSnapshot> snapshots = new ArrayList<>();
		java.util.UUID owner = player.getUUID();
		EntityTypeTest<Entity, DollEntity> typeTest = EntityTypeTest.forExactClass(DollEntity.class);
		// 收集已加载的人偶
		for (ServerLevel lv : player.level().getServer().getAllLevels()) {
			boolean sameDim = lv == player.level();
			String dimName = lv.dimension().identifier().getPath();
			for (DollEntity doll : lv.getEntities(typeTest, d -> !d.isRemoved() && owner.equals(d.getOwnerUuid()))) {
				String name = doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
				int distSqr = sameDim ? (int) doll.distanceToSqr(player) : Integer.MAX_VALUE;
				BlockPos dp = doll.blockPosition();
				snapshots.add(new DollSnapshot(
					doll.getId(), doll.getUUID().toString(), name, doll.getDollLevel(), doll.getActiveMode(),
					doll.isFollowEnabled(), doll.isTunneling(), sameDim, distSqr, dimName,
					dp.getX(), dp.getY(), dp.getZ()));
			}
		}
		// 补充离线人偶（已加载的人偶已在上方列出，用 UUID 去重）
		java.util.Set<String> loadedUuids = snapshots.stream()
			.map(DollSnapshot::uuid).collect(java.util.stream.Collectors.toSet());
		for (var entry : DollRecallRegistry.getAll().entrySet()) {
			java.util.UUID dollUuid = entry.getKey();
			DollRecallRegistry.DollLocation loc = entry.getValue();
			if (!loc.ownerUuid().equals(owner)) continue;
			String uuidStr = dollUuid.toString();
			if (loadedUuids.contains(uuidStr)) continue;
			String dimName = loc.dimension().identifier().getPath();
			boolean sameDim = loc.dimension().equals(player.level().dimension());
			// 离线人偶无实体 id，用 -1 标记
			snapshots.add(new DollSnapshot(
				-1, uuidStr, "未知人偶", 0, -1,
				false, false, sameDim, Integer.MAX_VALUE, dimName,
				loc.pos().getX(), loc.pos().getY(), loc.pos().getZ()));
		}
		ServerPlayNetworking.send(player, new OpenDollControlPanelPayload(snapshots));
	}
}
