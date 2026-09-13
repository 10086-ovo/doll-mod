package io.github.a10086ovo.doll.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 自包含的轻量网格寻路器（A*），不依赖原版绑定 Mob 的寻路体系。
 * 借鉴 Player2NPC 的"寻路驱动移动"思路：把目标转成一条条可站立的地面格子路径，
 * 供实体沿路径逐点移动，从而能绕开障碍并翻越一格高的台阶。
 */
public class DollNavigator {

	private static int MAX_NODES = 1024;
	private static double NODE_REACH_SQR = 1.2 * 1.2;
	/**
	 * 安全落差上限（格）：人偶敢主动走下去的最大垂直落差。
	 * 注意 A* 的下降邻居恒为 cur 的正下方 1 格（见 neighbors），且 canOccupy 要求
	 * 落点下方必有实心支撑，所以单步落差天然 ≤1——这里限制的不是单步高度，
	 * 而是「多深的坑不能往下跳」。盾构机的悬崖判定（DollEntity.updateTunnelDrill）
	 * 复用同一个常量，保证"人偶敢走下去"和"盾构机敢挖过去"的口径一致。
	 */
	public static int MAX_SAFE_FALL_BLOCKS = 3;
	/** 八方向：四 cardinal + 四对角线 */
	private static final int[][] DIRS = {
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	/** 整数代价体系：直线=10，对角线≈10×√2=14 */
	private static final int COST_STRAIGHT = 10;
	private static final int COST_DIAGONAL = 14;
	/** 上坡（台阶）附加代价：优先走平路/下坡，避免无谓地爬上爬下 */
	private static int UP_STEP_PENALTY = 4;

	private final LivingEntity entity;
	private List<Vec3> path;
	private int pathIndex;

	/** A* 复用字段：避免每次寻路重新分配 HashMap/PriorityQueue/ArrayList。 */
	private final Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
	private final Map<BlockPos, Integer> gScore = new HashMap<>();
	private final List<BlockPos> neighborList = new ArrayList<>(8);
	private final List<Vec3> reconstructList = new ArrayList<>();

	/**
	 * 从外置配置 {@link DollConfig#get()}{@code .navigator} 覆写寻路参数（搜索规模、可达判定、落差、上坡代价）。
	 * 由 DollEntity.applyConfig() 转发调用（/dollmod reload）。
	 */
	public static void applyConfig() {
		io.github.a10086ovo.doll.config.DollConfig.Navigator n = io.github.a10086ovo.doll.config.DollConfig.get().navigator;
		MAX_NODES = n.maxNodes;
		NODE_REACH_SQR = n.nodeReach * n.nodeReach;
		MAX_SAFE_FALL_BLOCKS = n.maxSafeFall;
		UP_STEP_PENALTY = n.upStepPenalty;
	}

	public DollNavigator(LivingEntity entity) {
		this.entity = entity;
	}

	/**
	 * 实时获取实体当前所在维度。
	 * <p>
	 * 早期实现把 {@code level} 缓存为构造期 final 字段（{@code this.level = entity.level()}），
	 * 人偶跨维度召回后 {@code entity.level()} 已是新维度，navigator 仍持有旧维度引用，导致
	 * computePath / canOccupy / hasLineOfSight 全部在错误维度方块数据上操作。改为每次实时获取，彻底修复。
	 */
	private Level level() {
		return entity.level();
	}

	/** 海洋人偶专属：允许 A* 将水方块视为可占据格，使其能沿水柱下潜/上浮。仅 SEA 置 true。 */
	private boolean allowWater = false;

	public void setAllowWater(boolean allowWater) {
		this.allowWater = allowWater;
	}

	/**
	 * 全局默认【树叶/菌光体 = 实心障碍】：不进树叶格、不站树叶顶、不从树冠里/夹缝中穿过。
	 * 根因在 canOccupy 的"脚下有碰撞箱=可落脚"判定：树叶有碰撞箱，被当成合法地面，
	 * 于是任何寻路（跟随/巡逻/砍树等）都会踩着低矮灌丛和树冠走，爬上树后下不来、
	 * 或在树冠夹缝里被卡住（用户多次实测：站在树叶上反复跳 / 寻路钻进树叶夹缝）。
	 * 注意 26.2 中相邻树冠常互相穿插，但本开关只是把叶当墙绕行，不影响"树"的连通定义。
	 */
	private boolean foliageIsObstacle = true;

	public void setFoliageIsObstacle(boolean obstacle) {
		this.foliageIsObstacle = obstacle;
	}

	/** pos 是否为树叶/菌光体（砍树时当作实心障碍的方块族）。 */
	private boolean isFoliage(BlockPos pos) {
		BlockState s = level().getBlockState(pos);
		return s.is(BlockTags.LEAVES) || s.is(BlockTags.WART_BLOCKS);
	}

	/** 是否已走完（或无路径）。 */
	public boolean isPathDone() {
		return path == null || pathIndex >= path.size();
	}

	public void clearPath() {
		path = null;
		pathIndex = 0;
	}

	/** 计算到目标的网格路径；成功返回 true。 */
	public boolean computePath(Vec3 target) {
		if (level().isClientSide()) {
			clearPath();
			return false;
		}
		BlockPos start = entity.blockPosition();
		BlockPos goal = BlockPos.containing(target);
		if (start.distManhattan(goal) <= 1) {
			clearPath();
			return true;
		}
		// 目标偏移不大时复用已有路径，避免每 tick 重算 A*
		if (path != null && !path.isEmpty()) {
			Vec3 end = path.get(path.size() - 1);
			if (end.distanceToSqr(target) <= 4.0) {
				return true;
			}
		}
		List<Vec3> nodes = aStar(start, goal);
		if (nodes == null || nodes.isEmpty()) {
			clearPath();
			return false;
		}
		this.path = nodes;
		this.pathIndex = 0;
		return true;
	}

	/** 推进路径：到达当前节点则前进到下一个；返回当前应走向的点，无路径则 null。 */
	public Vec3 advance() {
		if (isPathDone()) {
			return null;
		}
		Vec3 current = path.get(pathIndex);
		double dx = current.x - entity.getX();
		double dz = current.z - entity.getZ();
		if (dx * dx + dz * dz < NODE_REACH_SQR) {
			pathIndex++;
			if (isPathDone()) {
				return null;
			}
			return path.get(pathIndex);
		}
		return current;
	}

	// ---- A* 网格搜索 ----

	private List<Vec3> aStar(BlockPos start, BlockPos goal) {
		cameFrom.clear();
		gScore.clear();
		PriorityQueue<BlockPos> open = new PriorityQueue<>(
			Comparator.comparingDouble(p -> gScore.getOrDefault(p, 0) + heuristic(p, goal)));
		gScore.put(start, 0);
		open.add(start);
		int visited = 0;
		// 精确目标不可达时取"离目标最近的已访可达格"（最接近可达点），让物理碰撞补最后一段
		BlockPos best = start;
		int bestDist = start.distManhattan(goal);
		while (!open.isEmpty() && visited < MAX_NODES) {
			BlockPos cur = open.poll();
			visited++;
			int d = cur.distManhattan(goal);
			if (d < bestDist) {
				bestDist = d;
				best = cur;
			}
			if (d <= 1) {
				return reconstruct(cameFrom, cur, start);
			}
			// 邻居由 neighbors() 生成：同层 8 向 + 上 1 格台阶 + 下 1 格，
			// 使 A* 能沿楼梯/坡道换层，玩家在高处时也能真正规划出上楼路径。
			for (BlockPos next : neighbors(cur)) {
				boolean diagonal = next.getX() != cur.getX() && next.getZ() != cur.getZ();
				int moveCost = diagonal ? COST_DIAGONAL : COST_STRAIGHT;
				if (next.getY() > cur.getY()) {
					moveCost += UP_STEP_PENALTY;
				}
				int newG = gScore.getOrDefault(cur, 0) + moveCost;
				if (newG < gScore.getOrDefault(next, Integer.MAX_VALUE)) {
					cameFrom.put(next, cur);
					gScore.put(next, newG);
					open.add(next);
				}
			}
		}
		// 尽搜索而精点不可达：返最近可达点之路；best==start（四面尽堵）时退化为单点，
		// advance() 立即走尽 → 上层转贴墙滑行，不再每 tick 重算。
		return reconstruct(cameFrom, best, start);
	}

	/** 生成当前位置的可达邻居（同层 8 向 + 上跳一格 + 下坡/跳落 1..MAX_SAFE_FALL 格），八方向含穿墙检测。 */
	private List<BlockPos> neighbors(BlockPos cur) {
		neighborList.clear();
		for (int[] d : DIRS) {
			boolean diagonal = (d[0] != 0 && d[1] != 0);
			if (diagonal) {
				BlockPos side1 = cur.offset(d[0], 0, 0);
				BlockPos side2 = cur.offset(0, 0, d[1]);
				if (!isPassable(side1) && !isPassable(side2)) {
					continue;
				}
			}
			BlockPos nx = cur.offset(d[0], 0, d[1]);
			if (canOccupy(nx)) {
				neighborList.add(nx);
			} else {
				BlockPos up = nx.above();
				if (canOccupy(up)) {
					neighborList.add(up);
				}
			}
		BlockPos down = nx.below();
		if (canOccupy(down) && isSafeLanding(down)) {
			neighborList.add(down);
		}
		// 跳落边缘（drop edge）：nx 列向下 1..MAX_SAFE_FALL 内第一个可站立的安全落点。
		// 原实现只允许"下 1 格"，人偶一旦被寻路带上 2-3 格高的矮坎/低树叶层就无路可下，
		// 会原地反复跳（用户实测：卡在树叶上反复跳跃）。允许贴着边缘走下最多 MAX_SAFE_FALL
		// 格，使树冠矮层/台阶边缘可正常下地；落点仍要求中段无碰撞 + 落脚无伤害。
		for (int k = 2; k <= MAX_SAFE_FALL_BLOCKS; k++) {
			BlockPos landing = nx.below(k);
			if (!dropColumnClear(nx, k)) {
				break; // 下落通道被挡：更深的落差必然也被挡，直接终止
			}
			if (canOccupy(landing) && isSafeLanding(landing)) {
				neighborList.add(landing);
				break; // 取最近的可站立落点即可
			}
		}
	}
	return neighborList;
}

	/** nx 列上自当前层向下 k 格的下落通道（y-1..y-k+1）是否畅通（无碰撞、无伤害方块）。 */
	private boolean dropColumnClear(BlockPos nx, int k) {
		for (int j = 1; j < k; j++) {
			BlockPos cell = nx.below(j);
			if (!level().getBlockState(cell).getCollisionShape(level(), cell).isEmpty()
				|| isHazardous(cell)) {
				return false;
			}
		}
		return true;
	}

/**
 * 下坡落点是否安全：落点本身与脚下都不能是伤害性方块。
 * canOccupy 只保证「下方有实心支撑」，不保证踩上去不挨伤害——
 * 岩浆/火/岩浆块等没有碰撞箱，会被 canOccupy 误判成可落脚，这里补上。
 */
private boolean isSafeLanding(BlockPos landing) {
	return !isHazardous(landing) && !isHazardous(landing.below());
}

private boolean isHazardous(BlockPos pos) {
	BlockState state = level().getBlockState(pos);
	if (state.getFluidState().is(FluidTags.LAVA)) {
		return true;
	}
	Block block = state.getBlock();
	return block == Blocks.FIRE || block == Blocks.MAGMA_BLOCK
		|| block == Blocks.CACTUS || block == Blocks.SWEET_BERRY_BUSH
		|| block == Blocks.WITHER_ROSE;
}

	/** 该格子是否为水（含流体）。 */
	private boolean isWater(BlockPos pos) {
		return !level().getBlockState(pos).getFluidState().isEmpty();
	}

	/**
	 * 实体能否占据该列位置：本体格与头顶非固体（高 1.8 格）。
	 * 陆地：脚下必须是固体支撑。海洋人偶(allowWater)：水方块且头顶有空间即可游泳。
	 * 注：陆地人偶遇"脚下固体支撑的水格(如水坑)"时返回 true(可占据)，因为水没有碰撞箱、
	 * 原版物理允许陆地生物站在浅水里并能自然走出——若把水当硬障碍会让人偶贴坑/误入后被卡。
	 * 海泳人偶(allowWater=true)另有专用游泳分支。
	 */
	private boolean canOccupy(BlockPos pos) {
		if (foliageIsObstacle && (isFoliage(pos) || isFoliage(pos.above()))) {
			return false; // 砍树导航：不进树叶格，头也不埋进树叶（树冠矮檐下不穿行）
		}
		if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
			return false;
		}
		if (!level().getBlockState(pos.above()).getCollisionShape(level(), pos.above()).isEmpty()) {
			return false;
		}
		if (!level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty()) {
			if (foliageIsObstacle && isFoliage(pos.below())) {
				return false; // 砍树导航：树叶/菌光体顶不可落脚（避免爬上树冠下不来）
			}
			return true; // 陆地：脚下固体（含水格——浅水可自然走出）
		}
		if (allowWater && isWater(pos)) {
			return true; // 海洋人偶：水方块可游泳占据
		}
		return false;
	}

	/** 原陆地可站立语义（脚下固体），保留以兼容语义。 */
	private boolean isStandable(BlockPos pos) {
		return canOccupy(pos) && !level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty();
	}

	/** 格子是否可通过（非固体，或上方可翻越）。用于对角线穿墙检测。 */
	private boolean isPassable(BlockPos pos) {
		if (foliageIsObstacle && isFoliage(pos)) {
			return false; // 砍树导航：树叶不算可穿越侧壁，对角线不许切树叶角
		}
		if (level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
			return true;
		}
		return canOccupy(pos.above());
	}

	private List<Vec3> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos end, BlockPos start) {
		reconstructList.clear();
		BlockPos cur = end;
		while (cur != null && !cur.equals(start)) {
			reconstructList.add(Vec3.atBottomCenterOf(cur).add(0.0, 0.5, 0.0));
			cur = cameFrom.get(cur);
		}
		reconstructList.add(Vec3.atBottomCenterOf(start).add(0.0, 0.5, 0.0));
		Collections.reverse(reconstructList);
		return reconstructList;
	}

	/** 八方向距离（octile distance），与 8 方向搜索 + 整数代价体系匹配。 */
	private double heuristic(BlockPos a, BlockPos b) {
		double dx = Math.abs(a.getX() - b.getX());
		double dz = Math.abs(a.getZ() - b.getZ());
		double min = Math.min(dx, dz);
		double max = Math.max(dx, dz);
		return COST_STRAIGHT * (max - min) + COST_DIAGONAL * min;
	}
}