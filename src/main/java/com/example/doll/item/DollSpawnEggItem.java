package com.example.doll.item;

import com.example.doll.DollMod;
import com.example.doll.DollModConstants;
import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollRecallRegistry;
import com.example.doll.entity.DollVariant;
import com.example.doll.inventory.DollInventory;
import com.example.doll.util.DollEntityLookup;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 人偶刷怪蛋。
 * 生命周期：
 * 1. 普通蛋（无绑定、未命名）——不可召唤；
 * 2. 铁砧赐名后蛋获得附魔光效；
 * 3. 右键地面召唤人偶，蛋与人偶绑定（持有 UUID），光效消失；
 * 4. 已绑定蛋再次右键地面：把人偶召回（传送）到点击位置，播放末影人传送音效；
 * 5. 手持对应蛋右键人偶可回收，蛋恢复"已命名无绑定"状态并携带物品栏；
 * 6. 人偶死亡后所有对应蛋变为失效纯物品。
 */
public class DollSpawnEggItem extends Item {

	public static final String INVALIDATED_NBT_KEY = "Invalidated";
	public static final String INVENTORY_NBT_KEY = "Inventory";
	public static final String DOLL_UUID_NBT_KEY = "DollUuid";

	private final int dollLevel;
	private final DollVariant variant;

	public DollSpawnEggItem(Properties properties, int dollLevel) {
		this(properties, dollLevel, DollVariant.NONE);
	}

	public DollSpawnEggItem(Properties properties, int dollLevel, DollVariant variant) {
		super(properties);
		this.dollLevel = dollLevel;
		this.variant = variant;
	}

	public DollVariant getDollVariant() {
		return variant;
	}

	private static CompoundTag eggData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static void updateEggData(ItemStack stack, Consumer<CompoundTag> action) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, action);
	}

	/**
	 * 未失效且已命名但尚未绑定的蛋：召唤人偶并绑定。
	 * 携带家具物品栏数据的蛋：优先按数据生成。
	 * 已绑定蛋：右键地面把人偶召回（传送）到点击位置。
	 *
	 * <p>外包装：仅做耗时诊断（用户反馈联机模式用蛋生成人偶时约 8 秒卡顿，
	 * 需在日志里定位是哪个环节耗时）。慢于 100ms 才打 WARN，避免正常操作刷屏。
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		long t0 = System.nanoTime();
		InteractionResult result = useOnServer(context);
		long costMs = (System.nanoTime() - t0) / 1_000_000L;
		if (costMs >= 100) {
			DollMod.LOGGER.warn("[DollEgg] useOn 总耗时 {}ms (result={}) —— 若接近 8000ms 即卡顿现场，请连同前后日志反馈",
				costMs, result);
		}
		return result;
	}

	private InteractionResult useOnServer(UseOnContext context) {
		Level level = context.getLevel();
		ItemStack stack = context.getItemInHand();
		Player player = context.getPlayer();
		CompoundTag tag = eggData(stack);

		if (tag.getBooleanOr(INVALIDATED_NBT_KEY, false)) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".invalidated"));
			}
			return InteractionResult.FAIL;
		}
		if (stack.getCustomName() == null) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".need_name"));
			}
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		// 已绑定蛋：右键地面把对应人偶召回（传送）到点击位置
		String boundUuid = tag.getStringOr(DOLL_UUID_NBT_KEY, "");
		if (!boundUuid.isEmpty()) {
			return recallDoll(serverLevel, player, boundUuid, context);
		}

		DollEntity doll = DollMod.DOLL_ENTITY.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
		if (doll == null) {
			return InteractionResult.FAIL;
		}

		UUID bindUuid = doll.getUUID();
		doll.setDollLevel(dollLevel);
		if (variant != DollVariant.NONE) {
			doll.setDollVariant(variant);
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		doll.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (player != null) {
			// 面向玩家且头/身体/实体朝向一致，避免 yHeadRot 随机导致头扭 180°
			doll.faceTowardsPlayer(player);
		}
		doll.setCustomName(stack.getCustomName().copy());
		// 召唤者即主人，绑定跟随/控制权
		doll.setOwner(player);
		restoreInventoryFromEgg(doll, tag, serverLevel);
		restoreWorkAreaFromEgg(doll, tag);
		restoreTunnelConfigFromEgg(doll, tag);

		long tAdd = System.nanoTime();
		serverLevel.addFreshEntity(doll);
		long addMs = (System.nanoTime() - tAdd) / 1_000_000L;
		if (addMs >= 100) {
			DollMod.LOGGER.warn("[DollEgg] addFreshEntity 耗时 {}ms —— 疑似卡顿环节（区块/实体广播压力）", addMs);
		}
		// 蛋绑定人偶（若为回收再召唤，蛋中仍可能残留物品栏缓存，一并清除）
		updateEggData(stack, t -> {
			t.putString(DOLL_UUID_NBT_KEY, bindUuid.toString());
			t.remove(INVENTORY_NBT_KEY);
		});
		return InteractionResult.SUCCESS_SERVER;
	}

	/** 召回重试次数上限（约 2 秒），等待人偶所在区块把实体从磁盘加载出来。 */
	private static final int RECALL_RETRY_LIMIT = 40;

	/**
	 * 召回在途保护：同一人偶的"强制加载+重试"链已在运行时不重复启动。
	 * 玩家快速连点绑定蛋时，旧实现每点一次就新开一条 40 tick 重试链并反复
	 * 强加载同一区块，服务端积压大量延迟任务（并可能出现多条链互相抢
	 * setChunkForced(false) 的竞态）。任一终态分支都会移除登记。
	 */
	private static final Set<String> RECALLS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

	/**
	 * 已绑定蛋右键地面：把对应人偶传送到点击位置（召回）。
	 * 已加载的人偶直接传送（支持跨维度）；未加载时依据登记的最后位置强制加载区块并延后重试。
	 */
	private InteractionResult recallDoll(ServerLevel level, Player player, String uuidStr, UseOnContext context) {
		UUID uuid;
		try {
			uuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return InteractionResult.FAIL;
		}
		BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace());

		long t0 = System.nanoTime();
		DollEntity doll = DollEntityLookup.findLoadedDoll(level, uuid);
		if (doll != null) {
			// 仅主人可召回（防他人持蛋把人偶召走）；无主人时同样拒绝
			if (!doll.isOwnedBy(player)) {
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
				}
				return InteractionResult.FAIL;
			}
			boolean ok = doll.teleportToSpot(level, targetPos);
			long costMs = (System.nanoTime() - t0) / 1_000_000L;
			if (costMs >= 100) {
				DollMod.LOGGER.warn("[DollEgg] 召回(已加载) 耗时 {}ms —— 疑似卡顿环节（传送/落点扫描）", costMs);
			}
			if (ok) {
				// 传送成功：指挥棒选中状态同步清除（附魔光效消失）
				DollBatonItem.clearSelectionForDoll(player, uuid);
			} else if (player != null) {
				player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".recall_no_spot"));
			}
			return ok ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		// 人偶区块未加载：查登记的最后位置，强制加载后延后重试
		DollRecallRegistry.DollLocation loc = DollRecallRegistry.get(uuid);
		if (loc == null) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".recall_not_found"));
			}
			return InteractionResult.FAIL;
		}
		// 已有同人偶的召回链在途：直接返回，让现有链完成，避免连点堆叠
		String key = uuid.toString();
		if (!RECALLS_IN_FLIGHT.add(key)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		scheduleRecall(level, uuid, targetPos, loc, player, key);
		long costMs = (System.nanoTime() - t0) / 1_000_000L;
		if (costMs >= 100) {
			DollMod.LOGGER.warn("[DollEgg] 召回(未加载,排队强加载) 耗时 {}ms", costMs);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * 人偶区块未加载时的召回：强制加载其最后位置所在区块，然后每 tick 重试
	 * 直到实体从磁盘加载出来，再执行传送；完成后解除强制加载。
	 * 区块本身已加载时跳过强加载（重试链会等到实体从区块里出来）。
	 */
	private void scheduleRecall(ServerLevel targetLevel, UUID uuid, BlockPos targetPos,
			DollRecallRegistry.DollLocation loc, Player player, String inFlightKey) {
		MinecraftServer server = targetLevel.getServer();
		ServerLevel dollLevel = server.getLevel(loc.dimension());
		if (dollLevel == null) {
			// 人偶所在维度未加载，无法定位；清理在途登记避免永久卡住召回
			RECALLS_IN_FLIGHT.remove(inFlightKey);
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
				DollMod.LOGGER.warn("[DollEgg] 召回放弃：区块 {} 无存档数据，跳过强加载（避免同步世界生成卡顿）", chunk);
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".recall_not_found"));
				}
				return;
			}
			dollLevel.setChunkForced(chunk.x(), chunk.z(), true);
		}
		attemptRecall(targetLevel, uuid, targetPos, dollLevel, chunk, RECALL_RETRY_LIMIT, player, inFlightKey);
	}

	private void attemptRecall(ServerLevel targetLevel, UUID uuid, BlockPos targetPos,
			ServerLevel dollLevel, ChunkPos chunk, int remainingTries, Player player, String inFlightKey) {
		DollEntity doll = DollEntityLookup.findLoadedDoll(dollLevel, uuid);
		if (doll != null) {
			// 区块加载后补做归属校验：仅主人可召回（防他人持蛋把未加载的人偶召走）
			if (player != null && doll.isOwnedBy(player)) {
				if (doll.teleportToSpot(targetLevel, targetPos)) {
					DollBatonItem.clearSelectionForDoll(player, uuid);
				}
			}
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			RECALLS_IN_FLIGHT.remove(inFlightKey);
			return;
		}
		if (remainingTries <= 0) {
			dollLevel.setChunkForced(chunk.x(), chunk.z(), false);
			RECALLS_IN_FLIGHT.remove(inFlightKey);
			return;
		}
		targetLevel.getServer().execute(
			() -> attemptRecall(targetLevel, uuid, targetPos, dollLevel, chunk, remainingTries - 1, player, inFlightKey));
	}

	/**
	 * 从蛋恢复作业区（回收再召唤时保留配置）。
	 */
	private static void restoreWorkAreaFromEgg(DollEntity doll, CompoundTag tag) {
		tag.getIntArray(DollEntity.WORK_AREA_NBT_KEY).ifPresent(arr -> {
			if (arr.length == 6) {
				doll.setWorkArea(new BlockPos(arr[0], arr[1], arr[2]), new BlockPos(arr[3], arr[4], arr[5]));
			}
		});
	}

	/**
	 * 从蛋恢复盾构机配置（回收再召唤时保留掘进方向与入口，避免退回普通挖矿模式）。
	 * 恢复后不自动掘进，需指挥棒右键人偶恢复（与读档后行为一致）。
	 */
	private static void restoreTunnelConfigFromEgg(DollEntity doll, CompoundTag tag) {
		int dirId = tag.getIntOr(DollEntity.TUNNEL_DIR_NBT_KEY, -1);
		int[] entryArr = tag.getIntArray(DollEntity.TUNNEL_ENTRY_NBT_KEY).orElse(null);
		if (dirId < 0 || dirId >= 6 || entryArr == null || entryArr.length != 3) {
			return;
		}
		doll.setTunnelConfig(new BlockPos(entryArr[0], entryArr[1], entryArr[2]),
			net.minecraft.core.Direction.from3DDataValue(dirId), false); // 不瞬移回旧隧道口，人偶留在召唤点
		doll.setTunneling(false); // 不自动掘进，恢复需指挥棒右键人偶
	}

	private void restoreInventoryFromEgg(DollEntity doll, CompoundTag tag, ServerLevel level) {
		if (!tag.contains(INVENTORY_NBT_KEY)) {
			return;
		}
		try {
			Tag inventoryTag = tag.get(INVENTORY_NBT_KEY);
			if (!(inventoryTag instanceof ListTag listTag)) {
				return;
			}
			RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
			DollInventory inventory = doll.getInventoryBag();
			for (Tag entryTag : listTag) {
				if (!(entryTag instanceof CompoundTag entry)) {
					continue;
				}
				int slot = entry.getIntOr("Slot", -1);
				if (slot < 0 || slot >= DollInventory.INVENTORY_SIZE) {
					continue;
				}
				DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(registryOps, entry.get("Item"));
				result.result().ifPresent(stack -> inventory.setItem(slot, stack));
			}
			// 旧版本 bug 可能把物品存进护甲/装饰槽，恢复后统一修正
			doll.sanitizeInventorySlots();
		} catch (Exception e) {
			DollMod.LOGGER.warn("Failed to restore doll inventory from egg", e);
		}
	}

	/**
	 * 手持对应蛋右键人偶：回收人偶，物品栏写入蛋，人偶消失。
	 * <p>
	 * 注意：创造模式下引擎会把交互传入的 ItemStack 换成 {@code copy()} 副本（
	 * {@code Player.interactOn} 中 {@code hasInfiniteMaterials()} 分支），直接修改
	 * 该参数不会同步回背包；必须用玩家手中真实实例做读写，否则清除绑定与写入物品栏
	 * 都会丢失，导致回收后蛋仍处于"已绑定"状态无法再次召唤。
	 */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
		Level level = user.level();
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		// 26.2 客户端会为主手+副手各发一次实体交互包；回收是"主手操作"，
		// 副手必须早退——否则副手恰好也持有绑定了人偶的蛋（或客户端重复发包）时，
		// 人偶会被意外回收而"直接消失"（与指挥棒同一约定）。
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(target instanceof DollEntity doll)) {
			return InteractionResult.PASS;
		}
		// 以真实手持实例为准（忽略引擎可能传入的副本）
		ItemStack realStack = user.getItemInHand(hand);
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollSpawnEggItem)) {
			return InteractionResult.PASS;
		}
		CompoundTag tag = eggData(realStack);
		if (tag.getBooleanOr(INVALIDATED_NBT_KEY, false)) {
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".invalidated"));
			return InteractionResult.FAIL;
		}
		// 仅主人可回收（蛋被他人捡走/转手时不能偷走人偶）；matchesEgg 只校验
		// "蛋绑定了这具人偶"，不校验身份，因此必须先做归属校验
		if (!doll.isOwnedBy(user)) {
			// 诊断：定位"刚召唤的人偶不是你的"归属失配（ownerUuid 为空或与玩家 UUID 不一致）
			DollMod.LOGGER.warn("[DollOwner] 蛋回收被拒: doll={} ownerUuid={} player={} uuid={}",
				doll.getId(), doll.getOwnerUuid(), user.getName().getString(), user.getUUID());
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
			return InteractionResult.FAIL;
		}
		if (!doll.matchesEgg(realStack)) {
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_owner"));
			return InteractionResult.FAIL;
		}
		// 蛋的等级必须匹配人偶等级：普通蛋只能收普通人偶，进阶蛋只能收进阶人偶
		if (this.dollLevel != doll.getDollLevel()) {
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".level_mismatch"));
			return InteractionResult.FAIL;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, serverLevel.registryAccess());
		updateEggData(realStack, t -> {
			t.put(INVENTORY_NBT_KEY, encodeInventory(doll, registryOps));
			t.remove(DOLL_UUID_NBT_KEY);
			// 作业区随蛋携带：回收（未死亡）保留配置，死亡时蛋失效则自然丢失
			if (doll.hasWorkArea()) {
				t.putIntArray(DollEntity.WORK_AREA_NBT_KEY, new int[] {
					doll.getWorkAreaMin().getX(), doll.getWorkAreaMin().getY(), doll.getWorkAreaMin().getZ(),
					doll.getWorkAreaMax().getX(), doll.getWorkAreaMax().getY(), doll.getWorkAreaMax().getZ()
				});
			} else {
				t.remove(DollEntity.WORK_AREA_NBT_KEY);
			}
			// 盾构机配置随蛋携带（回收保留；死亡时蛋失效自然丢失）
			if (doll.hasTunnelConfig()) {
				t.putInt(DollEntity.TUNNEL_DIR_NBT_KEY, doll.getTunnelDir().get3DDataValue());
				BlockPos entry = doll.getTunnelEntry();
				t.putIntArray(DollEntity.TUNNEL_ENTRY_NBT_KEY, new int[] {
					entry.getX(), entry.getY(), entry.getZ()
				});
			} else {
				t.remove(DollEntity.TUNNEL_DIR_NBT_KEY);
				t.remove(DollEntity.TUNNEL_ENTRY_NBT_KEY);
			}
		});

		// 回收时指挥棒选中状态同步清除（附魔光效消失）
		DollBatonItem.clearSelectionForDoll(user, doll.getDollUuid());
		doll.discard();
		return InteractionResult.SUCCESS_SERVER;
	}

	private Tag encodeInventory(DollEntity doll, RegistryOps<Tag> registryOps) {
		ListTag list = new ListTag();
		DollInventory inventory = doll.getInventoryBag();
		for (int i = 0; i < DollInventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int slot = i;
			DataResult<Tag> result = ItemStack.OPTIONAL_CODEC.encodeStart(registryOps, stack);
			result.result().ifPresentOrElse(itemTag -> {
				CompoundTag entry = new CompoundTag();
				entry.putInt("Slot", slot);
				entry.put("Item", itemTag);
				list.add(entry);
			}, () -> DollMod.LOGGER.warn("Failed to encode item {} for doll egg", stack));
		}
		return list;
	}

	/**
	 * 已命名且未绑定（可召唤）的蛋显示附魔光效。失效蛋不显示。
	 */
	@Override
	public boolean isFoil(ItemStack stack) {
		CompoundTag tag = eggData(stack);
		if (tag.getBooleanOr(INVALIDATED_NBT_KEY, false)) {
			return false;
		}
		return stack.getCustomName() != null && tag.getStringOr(DOLL_UUID_NBT_KEY, "").isEmpty();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
			Consumer<Component> tooltip, TooltipFlag flag) {
		CompoundTag tag = eggData(stack);
		if (tag.getBooleanOr(INVALIDATED_NBT_KEY, false)) {
			tooltip.accept(Component.translatable("tooltip." + DollModConstants.MOD_ID + ".invalidated"));
		}
	}
}