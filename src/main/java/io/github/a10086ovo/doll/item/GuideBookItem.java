package io.github.a10086ovo.doll.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 人偶模组指南书。右键打开原版成书阅读界面（双页 + 翻页按钮）。
 * <p>
 * 服务端只返回成功；打开屏幕的实际动作由
 * {@code io.github.a10086ovo.doll.DollModClient} 在客户端初始化时注入到
 * {@link #openScreenAction}，避免在 {@code src/main} 引用 net.minecraft.client.*。
 */
public class GuideBookItem extends Item {

	/** 客户端注入的打开动作（在 DollModClient 初始化时设置）。 */
	public static Consumer<Player> openScreenAction = player -> {};

	public GuideBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			openScreenAction.accept(player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
