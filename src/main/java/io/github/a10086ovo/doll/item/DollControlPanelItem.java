package io.github.a10086ovo.doll.item;

import io.github.a10086ovo.doll.network.DollNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 人偶遥控器。
 *
 * <p>手持右键（空气 / 方块 / 实体均可）→ 打开人偶控制面板（群像管理 GUI，
 * 一次操作所有存活人偶的模式 / 跟随）。
 *
 * <p>与人偶指挥棒的职责分离：指挥棒负责「选中人偶 + 划定作业区」的现场调试，
 * 遥控器负责「全局控制面板」的远程管理。
 */
public class DollControlPanelItem extends Item {

	public DollControlPanelItem(Properties properties) {
		super(properties);
	}

	/** 右键空气 → 打开控制面板。 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack realStack = player.getItemInHand(hand);
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollControlPanelItem)) {
			return InteractionResult.PASS;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			DollNetworking.sendControlPanel(sp);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	/** 右键方块 → 打开控制面板。 */
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
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollControlPanelItem)) {
			return InteractionResult.PASS;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			DollNetworking.sendControlPanel(sp);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	/** 右键实体（含人偶）→ 打开控制面板。 */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player user,
			net.minecraft.world.entity.LivingEntity target, InteractionHand hand) {
		Level level = user.level();
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack realStack = user.getItemInHand(hand);
		if (realStack.isEmpty() || !(realStack.getItem() instanceof DollControlPanelItem)) {
			return InteractionResult.PASS;
		}
		if (user instanceof net.minecraft.server.level.ServerPlayer sp) {
			DollNetworking.sendControlPanel(sp);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}
}