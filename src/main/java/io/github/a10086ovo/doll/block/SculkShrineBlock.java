package io.github.a10086ovo.doll.block;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.entity.WildWardenDollEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 幽匿灵龛 —— 用于召唤野生幽匿人偶的仪式方块。
 * 手持回响碎片右键神龛，消耗一个碎片召唤野生幽匿人偶。
 * 每个灵龛在上一个召唤的野生幽匿人偶死亡之前不能再次召唤。
 */
public class SculkShrineBlock extends BaseEntityBlock {

	public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

	public SculkShrineBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TRIGGERED, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(SculkShrineBlock::new);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TRIGGERED);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SculkShrineBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// 不需要周期性 tick，冷却由实体存活状态控制
		return null;
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(TRIGGERED)) {
			level.setBlock(pos, state.setValue(TRIGGERED, false), 3);
		}
	}

	/**
	 * 右键交互：手持回响碎片右键神龛，消耗碎片召唤野生幽匿人偶。
	 * 前置条件：上一个召唤的野生幽匿人偶已死亡。
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!stack.is(Items.ECHO_SHARD)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ServerLevel serverLevel = (ServerLevel) level;

		// 获取方块实体
		if (!(level.getBlockEntity(pos) instanceof SculkShrineBlockEntity shrineEntity)) {
			return InteractionResult.PASS;
		}

		// 检查上一个召唤的野生幽匿人偶是否还活着
		if (shrineEntity.hasActiveDoll(serverLevel)) {
			return InteractionResult.PASS;
		}

		// 所有条件满足，执行召唤
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		WildWardenDollEntity warden = spawnWardenDoll(serverLevel, pos, player);
		if (warden != null) {
			shrineEntity.setSummonedDoll(warden.getUUID());
		}

		// 设置 triggered 状态（视觉反馈）
		serverLevel.setBlock(pos, state.setValue(TRIGGERED, true), 3);
		serverLevel.scheduleTick(pos, state.getBlock(), 100);

		return InteractionResult.CONSUME;
	}

	@Nullable
	private static WildWardenDollEntity spawnWardenDoll(ServerLevel level, BlockPos pos, Player player) {
		WildWardenDollEntity warden = DollMod.WARDEN_DOLL_ENTITY.create(level, EntitySpawnReason.TRIGGERED);
		if (warden == null) {
			return null;
		}
		warden.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		level.addFreshEntity(warden);
		warden.setLastHurtByMob(player);
		return warden;
	}
}
