package com.example.doll.item;

import com.example.doll.DollModConstants;
import com.example.doll.entity.DollEntity;
import com.example.doll.util.DollEntityLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 人偶指挥棒。
 *
 * <p>交互状态机：
 * <ol>
 *   <li>右键人偶 → 选中（保存目标人偶 UUID + 名字，附魔光效）；潜行右键人偶 → 清除其作业区；
 *   <li>右键方块（已选中人偶）→ 第一次设"角 A"（粒子标记），第二次设"角 B"（生成矩形作业区，
 *       写入人偶实体，粒子边框反馈）；潜行右键方块 → 取消本次选区；
 *   <li>必须先选择模式才能调试：作业区仅砍树/耕种模式可划，盾构机入口仅挖矿模式可设，
 *       其他模式（含空闲）右键方块会提示先切换模式——避免"提前划区域但模式不消费"的困惑；
 *   <li>作业区单边上限 {@value #WORK_AREA_MAX_EDGE} 格；
 *   <li><b>未选中人偶时</b>：右键空气 / 方块 → 提示先选中目标人偶
 *       （控制面板功能已拆分到 {@link DollControlPanelItem} 人偶遥控器）。
 * </ol>
 *
 * <p>作业区语义（已实现）：作业区只约束劳作类模式——砍树在区域内找树根、耕种在区域内找
 * 作物/空耕地/可锄方块（仅 XZ 平面判定，Y 不限）；攻击（近战/射手）、插火把、钓鱼、喂食
 * 不受作业区约束（全图自由）；跟随与作业区互斥（复用钓鱼↔跟随互斥逻辑，区域只对独立模式生效）。
 * 挖矿模式 = 盾构机（入口两竖方块 + 右键面反方向掘进 + 五个停止条件广播）。
 */
public class DollBatonItem extends Item {

	public static final String SELECTED_DOLL_UUID_NBT_KEY = "SelectedDollUuid";
	public static final String SELECTED_DOLL_NAME_NBT_KEY = "SelectedDollName";
	/** 角 A 位置（3 个 int）；存在表示选区进行中，等待右键角 B。 */
	public static final String SELECTION_CORNER_NBT_KEY = "SelectionCorner";
	/** 目标选择模式标记；true 时右键生物指定为人偶的强制攻击目标。 */
	public static final String TARGETING_MODE_NBT_KEY = "TargetingMode";
	/** 作业区单边上限（格）。 */
	public static final int WORK_AREA_MAX_EDGE = 64;

	public DollBatonItem(Properties properties) {
		super(properties);
	}

	private static CompoundTag batonData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/**
	 * 清除玩家背包中选中了指定人偶的指挥棒选择状态（附魔光效同步消失）。
	 * 用于人偶被召回传送 / 回收进蛋时，指挥棒不应继续保持选中——否则光效残留误导玩家。
	 */
	public static void clearSelectionForDoll(Player player, UUID dollUuid) {
		if (player == null) return;
		String uuidStr = dollUuid.toString();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty() || !(stack.getItem() instanceof DollBatonItem)) continue;
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null) continue;
			if (uuidStr.equals(data.copyTag().getStringOr(SELECTED_DOLL_UUID_NBT_KEY, ""))) {
				clearSelection(stack);
			}
		}
	}

	/** 结束一次调试：清除选中人偶与选区状态（附魔光效随之消失）。 */
	private static void clearSelection(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.remove(SELECTED_DOLL_UUID_NBT_KEY);
			tag.remove(SELECTED_DOLL_NAME_NBT_KEY);
			tag.remove(SELECTION_CORNER_NBT_KEY);
			tag.remove(TARGETING_MODE_NBT_KEY);
		});
	}

	/**
	 * 手持指挥棒右键实体：
	 * <ol>
	 *   <li>右键人偶（近战/射手模式）→ 进入目标选择模式，附魔光效；
	 *   <li>右键人偶（砍树/耕种/挖矿模式）→ 选中并进入作业区/盾构机选区模式（原有逻辑）；
	 *   <li>目标选择模式下右键任意生物 → 指定为人偶的强制攻击目标；
	 *   <li>潜行右键人偶 → 清除作业区/盾构机/强制目标并结束选中。
	 * </ol>
	 *
	 * <p>26.2 关键坑：客户端两次发包（主手+副手各一次），必须 {@code hand != MAIN_HAND} 早退；
	 * 且必须按真实手持实例读写 NBT（创造模式引擎可能传入副本），参照 {@link DollSpawnEggItem}。
	 */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
		Level level = user.level();
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack realStack = user.getItemInHand(hand);
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollBatonItem)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		CompoundTag tag = batonData(realStack);
		String uuidStr = tag.getStringOr(SELECTED_DOLL_UUID_NBT_KEY, "");
		boolean targetingMode = tag.getBooleanOr(TARGETING_MODE_NBT_KEY, false);

		// ---- 目标选择模式：右键任意生物指定为强制攻击目标 ----
		if (targetingMode && !uuidStr.isEmpty() && !(target instanceof DollEntity)) {
			return designateForcedTarget(serverLevel, user, realStack, uuidStr, target);
		}

		if (!(target instanceof DollEntity doll)) {
			return InteractionResult.PASS;
		}

		// 仅可操作自己的人偶（统一走 isOwnedBy：无主人时同样拒绝）
		if (!doll.isOwnedBy(user)) {
			com.example.doll.DollMod.LOGGER.warn("[DollOwner] 指挥棒选中被拒: doll={} ownerUuid={} player={} uuid={}",
				doll.getId(), doll.getOwnerUuid(), user.getName().getString(), user.getUUID());
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
			return InteractionResult.FAIL;
		}

		// 潜行右键人偶：清除作业区/盾构机/强制目标并结束本次调试（解除选中，光效消失）
		if (user.isShiftKeyDown()) {
			doll.clearWorkArea();
			doll.clearTunnelConfig();
			doll.setForcedTargetUuid(null);
			clearSelection(realStack);
			user.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".area_cleared",
				Component.literal(getDollName(doll)).withStyle(ChatFormatting.YELLOW)
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		UUID dollUuid = doll.getDollUuid();
		int mode = doll.getActiveMode();
		String name = getDollName(doll);

		// ---- 战斗模式（近战/射手）：进入目标选择模式 ----
		// 幽匿人偶跳过战斗模式检测：除挖矿/砍树外任意模式均可指定目标
		boolean isWarden = doll.isWardenDoll();
		boolean isCombatMode = mode == com.example.doll.mode.DollMode.MELEE.getIndex()
				|| mode == com.example.doll.mode.DollMode.RANGED.getIndex();
		boolean isJobMode = mode == com.example.doll.mode.DollMode.CHOP.getIndex()
				|| mode == com.example.doll.mode.DollMode.FARM.getIndex()
				|| mode == com.example.doll.mode.DollMode.MINE.getIndex();
		boolean canTarget = isCombatMode || (isWarden && !isJobMode);

		if (canTarget) {
			CustomData.update(DataComponents.CUSTOM_DATA, realStack, t -> {
				t.putString(SELECTED_DOLL_UUID_NBT_KEY, dollUuid.toString());
				t.putString(SELECTED_DOLL_NAME_NBT_KEY, name);
				t.putBoolean(TARGETING_MODE_NBT_KEY, true);
			});
			user.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_targeting_mode",
				Component.literal(name).withStyle(ChatFormatting.YELLOW)
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		// ---- 作业模式（砍树/耕种/挖矿）：原有选区流程 ----
		if (!isJobMode) {
			user.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_mode_required",
				Component.literal(name).withStyle(ChatFormatting.YELLOW)));
			return InteractionResult.FAIL;
		}

		CustomData.update(DataComponents.CUSTOM_DATA, realStack, t -> {
			t.putString(SELECTED_DOLL_UUID_NBT_KEY, dollUuid.toString());
			t.putString(SELECTED_DOLL_NAME_NBT_KEY, name);
		});

		user.sendSystemMessage(Component.translatable(
			"message." + DollModConstants.MOD_ID + ".baton_selected",
			Component.literal(name).withStyle(ChatFormatting.YELLOW)
		));
		if (doll.getActiveMode() != com.example.doll.mode.DollMode.MINE.getIndex()) {
			if (doll.hasWorkArea()) {
				user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".area_status_set"));
			} else {
				user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".area_status_unset"));
			}
		}
		if (doll.hasTunnelConfig() && !doll.isTunneling()) {
			if (doll.isAlignedWithTunnel()) {
				doll.setTunneling(true);
				user.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".mine_tunnel_resumed",
					Component.literal(name).withStyle(ChatFormatting.YELLOW)
				));
			} else {
				user.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".mine_tunnel_not_at_spot",
					Component.literal(name).withStyle(ChatFormatting.YELLOW)
				));
			}
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * 目标选择模式：将右键的生物指定为人偶的强制攻击目标。
	 * 不允许指定人偶主人自身。目标死亡/移除后人偶自动回退到正常 AI 搜寻。
	 */
	private InteractionResult designateForcedTarget(ServerLevel serverLevel, Player user,
			ItemStack realStack, String uuidStr, LivingEntity target) {
		if (!target.isAlive() || target.isRemoved()) {
			user.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_target_invalid"));
			return InteractionResult.FAIL;
		}
		// 不允许指定主人自身为人偶的攻击目标
		if (target.getUUID().toString().equals(user.getUUID().toString())) {
			user.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_target_self"));
			return InteractionResult.FAIL;
		}
		UUID uuid;
		try {
			uuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return InteractionResult.FAIL;
		}
		DollEntity doll = DollEntityLookup.findLoadedDoll(serverLevel, uuid);
		if (doll == null) {
			user.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".doll_not_found"));
			return InteractionResult.FAIL;
		}
		doll.setForcedTargetUuid(target.getUUID());
		String dollName = getDollName(doll);
		String targetName = target.getCustomName() != null
			? target.getCustomName().getString()
			: target.getType().getDescription().getString();
		clearSelection(realStack);
		user.sendSystemMessage(Component.translatable(
			"message." + DollModConstants.MOD_ID + ".baton_target_set",
			Component.literal(dollName).withStyle(ChatFormatting.YELLOW),
			Component.literal(targetName).withStyle(ChatFormatting.RED)
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * 手持指挥棒右键方块：两点选区状态机。
	 * <ol>
	 *   <li>未选中人偶 → 提示先选中目标（不再打开控制面板，控制面板已拆分到遥控器）；
	 *   <li>潜行右键 → 取消当前选区（清角 A）；
	 *   <li>无角 A → 记录角 A 并撒粒子标记；
	 *   <li>已有角 A → 记录角 B，校验上限后把作业区写入人偶实体，撒边框粒子。
	 * </ol>
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (context.getHand() != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		ItemStack realStack = player.getItemInHand(context.getHand());
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollBatonItem)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		CompoundTag tag = batonData(realStack);
		String uuidStr = tag.getStringOr(SELECTED_DOLL_UUID_NBT_KEY, "");
		if (uuidStr.isEmpty()) {
			// 未选中人偶：提示玩家先选中目标（控制面板已拆分到遥控器物品）
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".need_select_doll"));
			return InteractionResult.PASS;
		}

		// 目标选择模式下右键方块 = 取消（目标选择不消费方块点击）
		if (tag.getBooleanOr(TARGETING_MODE_NBT_KEY, false)) {
			clearSelection(realStack);
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_targeting_cancelled"));
			return InteractionResult.SUCCESS_SERVER;
		}

		// 潜行右键：取消本次选区（不需要人偶在场/模式）
		if (player.isShiftKeyDown()) {
			CustomData.update(DataComponents.CUSTOM_DATA, realStack, t -> t.remove(SELECTION_CORNER_NBT_KEY));
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".selection_cancelled"));
			return InteractionResult.SUCCESS_SERVER;
		}

		// 解析目标人偶（角 A/角 B 都需要，用于校验模式）
		UUID uuid;
		try {
			uuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return InteractionResult.FAIL;
		}
		DollEntity doll = DollEntityLookup.findLoadedDoll(serverLevel, uuid);
		if (doll == null) {
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".doll_not_found"));
			return InteractionResult.FAIL;
		}

		// 必须先选择模式才能调试：作业区=砍树/耕种，盾构机=挖矿；其他模式（含空闲）拒绝
		// 避免"提前划了区域但当前模式不消费区域"的困惑（区域与模式状态解耦的根源）
		int mode = doll.getActiveMode();
		if (mode != com.example.doll.mode.DollMode.CHOP.getIndex()
				&& mode != com.example.doll.mode.DollMode.FARM.getIndex()
				&& mode != com.example.doll.mode.DollMode.MINE.getIndex()) {
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_mode_required",
				Component.literal(getDollName(doll)).withStyle(ChatFormatting.YELLOW)));
			return InteractionResult.FAIL;
		}

		BlockPos clicked = context.getClickedPos();
		int[] corner = tag.getIntArray(SELECTION_CORNER_NBT_KEY).orElse(null);

		// 第一次右键：记录角 A
		if (corner == null || corner.length != 3) {
			CustomData.update(DataComponents.CUSTOM_DATA, realStack,
				t -> t.putIntArray(SELECTION_CORNER_NBT_KEY, new int[] { clicked.getX(), clicked.getY(), clicked.getZ() }));
			serverLevel.sendParticles(ParticleTypes.END_ROD,
				clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5,
				16, 0.2, 0.2, 0.2, 0.02);
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".corner_a_set"));
			return InteractionResult.SUCCESS_SERVER;
		}

		// 第二次右键：角 B，生成作业区
		BlockPos cornerA = new BlockPos(corner[0], corner[1], corner[2]);
		int maxEdge = Math.max(
			Math.abs(clicked.getX() - cornerA.getX()),
			Math.max(Math.abs(clicked.getY() - cornerA.getY()), Math.abs(clicked.getZ() - cornerA.getZ())));
		if (maxEdge > WORK_AREA_MAX_EDGE) {
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".area_too_large"));
			return InteractionResult.FAIL;
		}
		// 角 B 再校验一次模式（角 A 后玩家可能切换了模式）
		mode = doll.getActiveMode();
		if (mode != com.example.doll.mode.DollMode.CHOP.getIndex()
				&& mode != com.example.doll.mode.DollMode.FARM.getIndex()
				&& mode != com.example.doll.mode.DollMode.MINE.getIndex()) {
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".baton_mode_required",
				Component.literal(getDollName(doll)).withStyle(ChatFormatting.YELLOW)));
			return InteractionResult.FAIL;
		}

		// 挖矿模式下角 B 解读为盾构机入口：两个竖方块必须竖直堆叠，方向 = 第二次右键面的反方向
		if (mode == com.example.doll.mode.DollMode.MINE.getIndex()) {
			return setupTunnel(serverLevel, player, realStack, doll, cornerA, clicked, context.getClickedFace());
		}

		doll.setWorkArea(cornerA, clicked);
		spawnAreaFrameParticles(serverLevel, doll.getWorkAreaMin(), doll.getWorkAreaMax());
		// 生成完成：结束本次调试（解除选中，附魔光效消失，需重新右键人偶才能再调）
		clearSelection(realStack);
		player.sendSystemMessage(Component.translatable(
			"message." + DollModConstants.MOD_ID + ".area_set",
			Component.literal(getDollName(doll)).withStyle(ChatFormatting.YELLOW)
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * 盾构机设置：校验两竖方块竖直堆叠 + 面为水平，方向取面反方向（向墙内钻）。
	 * 配置成功后把人偶传送到入口前方，进入掘进状态。
	 */
	private InteractionResult setupTunnel(ServerLevel serverLevel, Player player, ItemStack realStack,
			DollEntity doll, BlockPos cornerA, BlockPos clicked, net.minecraft.core.Direction face) {
		if (cornerA.getX() != clicked.getX() || cornerA.getZ() != clicked.getZ()
				|| Math.abs(cornerA.getY() - clicked.getY()) != 1) {
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".mine_tunnel_invalid_stack"));
			return InteractionResult.FAIL;
		}
		if (!face.getAxis().isHorizontal()) {
			player.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + ".mine_tunnel_invalid_face"));
			return InteractionResult.FAIL;
		}
		net.minecraft.core.Direction dir = face.getOpposite(); // 向墙内钻
		BlockPos entry = cornerA.getY() < clicked.getY() ? cornerA : clicked; // 下层方块
		doll.setTunnelConfig(entry, dir);
		// 入口截面小框粒子标记
		spawnAreaFrameParticles(serverLevel, entry, entry.above());
		clearSelection(realStack);
		player.sendSystemMessage(Component.translatable(
			"message." + DollModConstants.MOD_ID + ".mine_tunnel_set",
			Component.literal(getDollName(doll)).withStyle(ChatFormatting.YELLOW),
			Component.translatable("direction." + DollModConstants.MOD_ID + "." + dir.getSerializedName())));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** 已选中的指挥棒显示附魔光效，便于玩家一眼分辨状态。 */
	@Override
	public boolean isFoil(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		return data.copyTag().contains(SELECTED_DOLL_UUID_NBT_KEY);
	}

	/** Tooltip 显示当前选中的人偶名字。 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
			Consumer<Component> tooltip, TooltipFlag flag) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		CompoundTag tag = data.copyTag();
		if (tag.getBooleanOr(TARGETING_MODE_NBT_KEY, false)) {
			// 目标选择模式：显示提示
			tooltip.accept(Component.translatable(
				"tooltip." + DollModConstants.MOD_ID + ".baton_targeting",
				Component.literal(tag.getStringOr(SELECTED_DOLL_NAME_NBT_KEY, "人偶")).withStyle(ChatFormatting.YELLOW)
			));
		} else if (tag.contains(SELECTED_DOLL_NAME_NBT_KEY)) {
			tooltip.accept(Component.translatable(
				"tooltip." + DollModConstants.MOD_ID + ".baton_selected",
				Component.literal(tag.getStringOr(SELECTED_DOLL_NAME_NBT_KEY, "人偶")).withStyle(ChatFormatting.YELLOW)
			));
		}
	}

	private static String getDollName(DollEntity doll) {
		return doll.getCustomName() != null ? doll.getCustomName().getString() : "人偶";
	}

	/**
	 * 沿作业区 AABB 的 12 条边撒末影棒粒子，生成后一次性边框反馈（约 5 秒消散）。
	 */
	private static void spawnAreaFrameParticles(ServerLevel level, BlockPos min, BlockPos max) {
		int x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
		int x1 = max.getX(), y1 = max.getY(), z1 = max.getZ();
		for (int z : new int[] { z0, z1 }) {
			for (int y : new int[] { y0, y1 }) {
				for (int x = x0; x <= x1; x++) {
					level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
				}
			}
		}
		for (int z : new int[] { z0, z1 }) {
			for (int x : new int[] { x0, x1 }) {
				for (int y = y0; y <= y1; y++) {
					level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
				}
			}
		}
		for (int x : new int[] { x0, x1 }) {
			for (int y : new int[] { y0, y1 }) {
				for (int z = z0; z <= z1; z++) {
					level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
				}
			}
		}
	}
}
