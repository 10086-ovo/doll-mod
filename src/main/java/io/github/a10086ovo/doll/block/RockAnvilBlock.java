package io.github.a10086ovo.doll.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.BlockHitResult;
import java.util.Map;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 石砧方块。与原版铁砧行为一致，但总耐久更少。
 * 三个损伤状态（完好/微裂/大裂）分别使用独立的方块实例，与原版铁砧架构一致。
 */
public class RockAnvilBlock extends FallingBlock {

	public static final MapCodec<RockAnvilBlock> CODEC = simpleCodec(RockAnvilBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** 当前阶段已使用的次数（0~2）。每个阶段可用 3 次，第 3 次用完后切换到下一损伤阶段。 */
	public static final IntegerProperty USES = IntegerProperty.create("uses", 0, 2);
	/** 每个损伤阶段可使用的次数。 */
	public static final int USES_PER_STAGE = 3;

// Z 轴朝向（南北方向）的碰撞箱——与模型默认朝向一致
	private static final VoxelShape BASE_Z = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
	private static final VoxelShape LOWER_NARROW_Z = Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0);
	private static final VoxelShape WIDER_SECTION_Z = Block.box(6.0, 5.0, 4.0, 10.0, 10.0, 12.0);
	private static final VoxelShape TOP_Z = Block.box(3.0, 10.0, 0.0, 13.0, 16.0, 16.0);
	private static final VoxelShape SHAPE_Z = Shapes.join(Shapes.join(Shapes.join(BASE_Z, LOWER_NARROW_Z, BooleanOp.OR), WIDER_SECTION_Z, BooleanOp.OR), TOP_Z, BooleanOp.OR);

	// X 轴朝向（东西方向）的碰撞箱——Z 轴形状旋转 90°
	private static final VoxelShape BASE_X = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
	private static final VoxelShape LOWER_NARROW_X = Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0);
	private static final VoxelShape WIDER_SECTION_X = Block.box(4.0, 5.0, 6.0, 12.0, 10.0, 10.0);
	private static final VoxelShape TOP_X = Block.box(0.0, 10.0, 3.0, 16.0, 16.0, 13.0);
	private static final VoxelShape SHAPE_X = Shapes.join(Shapes.join(Shapes.join(BASE_X, LOWER_NARROW_X, BooleanOp.OR), WIDER_SECTION_X, BooleanOp.OR), TOP_X, BooleanOp.OR);

	private static final Map<Direction.Axis, VoxelShape> SHAPES = Map.of(
		Direction.Axis.Z, SHAPE_Z,
		Direction.Axis.X, SHAPE_X
	);

	// 三个损伤状态的互相引用——在 DollMod 初始化时注入。
	// 必须是实例字段：若为 static，三个方块实例会共用同一个引用，
	// 最终所有阶段的 nextVariant 都会变成 null，导致只有第一阶段能用 3 次就损坏消失。
	public RockAnvilBlock nextVariant; // 下一级损伤（null 表示损坏后消失）
	public RockAnvilBlock prevVariant; // 上一级损伤（用于坠落时恢复）

	public RockAnvilBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(USES, 0));
	}

	@Override
	protected MapCodec<? extends RockAnvilBlock> codec() {
		return CODEC;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING).getAxis());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, USES);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getClockWise())
			.setValue(USES, 0);
	}

	/**
	 * 消耗一次使用次数。每个阶段可用 3 次，用完 3 次后切换到下一损伤阶段；
	 * 最后一个阶段用完 3 次后直接损坏消失。与原版铁砧一样由 AnvilMenu 在取出结果时调用。
	 */
	public static void consumeUse(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide()) return;
		if (!(state.getBlock() instanceof RockAnvilBlock block)) return;

		int uses = state.getValue(USES);
		if (uses < USES_PER_STAGE - 1) {
			level.setBlock(pos, state.setValue(USES, uses + 1), 3);
			level.levelEvent(1030, pos, 0); // 铁砧使用音效
		} else {
			RockAnvilBlock next = block.nextVariant;
			if (next != null) {
				level.setBlock(pos, next.defaultBlockState()
					.setValue(FACING, state.getValue(FACING))
					.setValue(USES, 0), 3);
				level.levelEvent(1030, pos, 0); // 铁砧使用音效
			} else {
				level.removeBlock(pos, false);
				level.levelEvent(1029, pos, 0); // 铁砧损坏消失音效
			}
		}
	}

	@Override
	public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getMapColor(level, pos).col;
	}

	@Override
	public DamageSource getFallDamageSource(Entity entity) {
		return entity.damageSources().anvil(entity);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
			player.awardStat(Stats.INTERACT_WITH_ANVIL);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@Nullable
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider((id, inv, player) ->
			new AnvilMenu(id, inv, ContainerLevelAccess.create(level, pos)),
			io.github.a10086ovo.doll.DollMod.ROCK_ANVIL_CONTAINER_TITLE
		);
	}

	@Override
	public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedState, FallingBlockEntity entity) {
		if (!level.isClientSide()) {
			int fallDistance = (int) Math.ceil(entity.fallDistance);
			if (fallDistance > 1) {
				// 坠落降级：rock → chipped → damaged，已破损则直接消失
				RockAnvilBlock next = this.nextVariant;
				if (next != null) {
					level.setBlock(pos, next.defaultBlockState()
						.setValue(FACING, state.getValue(FACING))
						.setValue(USES, 0), 3);
				} else {
					level.removeBlock(pos, false);
				}
			}
		}
	}
}