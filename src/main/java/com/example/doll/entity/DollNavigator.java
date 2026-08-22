package com.example.doll.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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

	private static final int MAX_NODES = 1024;
	private static final double NODE_REACH_SQR = 1.2 * 1.2;
	/** 八方向：四 cardinal + 四对角线 */
	private static final int[][] DIRS = {
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	/** 整数代价体系：直线=10，对角线≈10×√2=14 */
	private static final int COST_STRAIGHT = 10;
	private static final int COST_DIAGONAL = 14;
	/** 上坡（台阶）附加代价：优先走平路/下坡，避免无谓地爬上爬下 */
	private static final int UP_STEP_PENALTY = 4;

	private final LivingEntity entity;
	private final Level level;
	private List<Vec3> path;
	private int pathIndex;

	public DollNavigator(LivingEntity entity) {
		this.entity = entity;
		this.level = entity.level();
	}

	/** 海洋人偶专属：允许 A* 将水方块视为可占据格，使其能沿水柱下潜/上浮。仅 SEA 置 true。 */
	private boolean allowWater = false;

	public void setAllowWater(boolean allowWater) {
		this.allowWater = allowWater;
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
		if (level.isClientSide()) {
			clearPath();
			return false;
		}
		BlockPos start = entity.blockPosition();
		BlockPos goal = BlockPos.containing(target);
		if (start.distManhattan(goal) <= 1) {
			clearPath();
			return true;
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
		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		Map<BlockPos, Integer> gScore = new HashMap<>();
		PriorityQueue<BlockPos> open = new PriorityQueue<>(
			Comparator.comparingDouble(p -> gScore.getOrDefault(p, 0) + heuristic(p, goal)));
		gScore.put(start, 0);
		open.add(start);
		int visited = 0;
		while (!open.isEmpty() && visited < MAX_NODES) {
			BlockPos cur = open.poll();
			visited++;
			if (cur.distManhattan(goal) <= 1) {
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
		return null;
	}

	/** 生成当前位置的可达邻居（含上跳一格、下坡一格），八方向含穿墙检测。 */
	private List<BlockPos> neighbors(BlockPos cur) {
		List<BlockPos> list = new ArrayList<>(8);
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
				list.add(nx);
			} else {
				BlockPos up = nx.above();
				if (canOccupy(up)) {
					list.add(up);
				}
			}
			BlockPos down = nx.below();
			if (canOccupy(down)) {
				list.add(down);
			}
		}
		return list;
	}

	/** 该格子是否为水（含流体）。 */
	private boolean isWater(BlockPos pos) {
		return !level.getBlockState(pos).getFluidState().isEmpty();
	}

	/**
	 * 实体能否占据该列位置：本体格与头顶非固体（高 1.8 格）。
	 * 陆地：脚下必须是固体支撑。海洋人偶(allowWater)：水方块且头顶有空间即可游泳。
	 * 其他人偶 allowWater=false，水方块不可占据 → 行为与原 isStandable 完全一致，零影响。
	 */
	private boolean canOccupy(BlockPos pos) {
		if (level.getBlockState(pos).blocksMotion()) {
			return false;
		}
		if (level.getBlockState(pos.above()).blocksMotion()) {
			return false;
		}
		if (level.getBlockState(pos.below()).blocksMotion()) {
			return true; // 陆地：脚下固体
		}
		if (allowWater && isWater(pos)) {
			return true; // 海洋人偶：水方块可游泳占据
		}
		return false;
	}

	/** 原陆地可站立语义（脚下固体），保留以兼容语义。 */
	private boolean isStandable(BlockPos pos) {
		return canOccupy(pos) && level.getBlockState(pos.below()).blocksMotion();
	}

	/** 格子是否可通过（非固体，或上方可翻越）。用于对角线穿墙检测。 */
	private boolean isPassable(BlockPos pos) {
		if (!level.getBlockState(pos).blocksMotion()) {
			return true;
		}
		return canOccupy(pos.above());
	}

	private List<Vec3> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos end, BlockPos start) {
		List<Vec3> nodes = new ArrayList<>();
		BlockPos cur = end;
		while (cur != null && !cur.equals(start)) {
			nodes.add(Vec3.atBottomCenterOf(cur).add(0.0, 0.5, 0.0));
			cur = cameFrom.get(cur);
		}
		nodes.add(Vec3.atBottomCenterOf(start).add(0.0, 0.5, 0.0));
		Collections.reverse(nodes);
		return nodes;
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