package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.network.SearchCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GeoIndex 索引服务（M1：底图圈内纯推演候选收集 + 内存索引）。
 *
 * <p>核心目标：对"当前维度某类目标"在给定方块半径内批量收集候选坐标，且全程<b>纯推演</b>——
 * 只读 {@link ChunkGeneratorStructureState} / {@code isStructureChunk} 做确定性判定，
 * <b>不加载、不生成区块</b>（对齐红线第6条与终稿 D10：复用原版定位 API，不自造网格枚举轮子）。
 *
 * <p>索引语义：结构/村庄 = 原版放置网格的确定性结果（由世界种子 + spacing/salt 固定，永不陈腐），
 * 因此收集到的索引可安全落盘并被全服共享。群系的纯推演由 {@code BiomeSource.getNoiseBiome} 提供，
 * 由 {@code DollNetworking} 的既有工作线程路径负责，本类不重复实现。
 *
 * <p>线程模型：{@code ChunkGeneratorStructureState} 与 {@code StructurePlacement} 为非线程安全容器，
 * 本类仅应在服务端主线程（tick）调用，用于喂给分片任务，不跨线程共享。
 */
public final class GeoIndexService {

	/**
	 * 收集某个 {@link StructureSearchType} / 村庄在指定圆心与方块半径内的候选坐标。
	 *
	 * <p>通过遍历 {@code ChunkGeneratorStructureState.possibleStructureSets()} 找到包含目标结构的
	 * StructureSet 及其 {@link StructurePlacement}，随后用原版 {@code isStructureChunk(state, x, z)}
	 * 对整个圈内逐候选区块做确定性判定（纯推演，零区块依赖）。命中区块坐标再经
	 * {@code getLocatePos} 折算为实际目标坐标（方块）。
	 *
	 * @param level           服务端世界（提供索引 state 与注册表）
	 * @param structureKey    目标结构注册键（如 minecraft:ancient_city）
	 * @param centerX,centerZ 圆心（方块坐标，通常为玩家位置）
	 * @param radiusBlocks    收集半径（方块）
	 * @param maxResults      最多收集多少个候选；&gt;0 时命中即停，用于"最近命中"快速返回
	 */
	public static List<int[]> collectCandidates(ServerLevel level, ResourceKey<Structure> structureKey,
			int centerX, int centerZ, int radiusBlocks, int maxResults) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();

		// 命中结构 → 找到它所在 StructureSet 的 placement（原版反查，D6）
		StructurePlacement placement = findPlacement(state, structureKey);
		if (placement == null) {
			return List.of();
		}

		List<int[]> out = new ArrayList<>();
		int radiusChunks = (radiusBlocks + 15) / 16;
		int cx = centerX >> 4;
		int cz = centerZ >> 4;
		for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
			for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
				int chunkX = cx + dx;
				int chunkZ = cz + dz;
				if (placement.isStructureChunk(state, chunkX, chunkZ)) {
					BlockPos p = placement.getLocatePos(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ));
					if (nearExisting(out, p.getX(), p.getZ())) {
						continue;
					}
					out.add(new int[]{p.getX(), p.getZ()});
					if (maxResults > 0 && out.size() >= maxResults) {
						return out;
					}
				}
			}
		}
		return out;
	}

	/**
	 * 自 {@code possibleStructureSets()} 反查包含目标结构的 StructureSet，返回其 placement。
	 * 与原版 /locate / EC 的 StructureSet 反查一致：遍历所有放得下的结构集，找包含该结构的那个。
	 */
	private static StructurePlacement findPlacement(ChunkGeneratorStructureState state, ResourceKey<Structure> structureKey) {
		if (state.possibleStructureSets() == null) {
			return null;
		}
		for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
			if (setHolder == null || setHolder.value() == null) {
				continue;
			}
			StructureSet set = setHolder.value();
			if (set.structures() == null) {
				continue;
			}
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry != null && entry.structure() != null && entry.structure().is(structureKey)) {
					// 该结构在当前维度可放置
					return set.placement();
				}
			}
		}
		return null;
	}

	/**
	 * 按「距中心 Chebyshev 环从近到远」逐环向外收集最近的一批结构候选（L1 开服预索引用）。
	 *
	 * <p>与 {@link #collectCandidates} 同一套判定（isStructureChunk + getLocatePos + nearExisting 去重），
	 * 唯一区别是遍历顺序：先放半径 0 环（中心格），再半径 1 环……直至 radiusChunks，使先收集到的
	 * 一定是最靠近中心的一批；人数到 maxResults 立即停。这样预索引结果上限只会漏掉远处、不会漏掉近处，
	 * 保证「最近结构」查询命中准确。
	 */
	public static List<int[]> collectNearest(ServerLevel level, ResourceKey<Structure> structureKey,
			int centerX, int centerZ, int radiusBlocks, int maxResults) {
		ChunkGeneratorStructureState state;
		try {
			state = level.getChunkSource().getGeneratorState();
		} catch (Throwable t) {
			// 刚进世界时 GeneratorState 可能尚未就绪：视为无可收集。
			return List.of();
		}
		if (state == null) {
			return List.of();
		}
		StructurePlacement placement;
		try {
			placement = findPlacement(state, structureKey);
		} catch (Throwable t) {
			return List.of();
		}
		if (placement == null) {
			return List.of();
		}

		List<int[]> out = new ArrayList<>();
		int radiusChunks = (radiusBlocks + 15) / 16;
		int cx = centerX >> 4;
		int cz = centerZ >> 4;
		for (int ring = 0; ring <= radiusChunks; ring++) {
			// 遍历以(cx,cz)为中心、Chebyshev 距离恰为 ring 的环带
			for (int dz = -ring; dz <= ring; dz++) {
				for (int dx = -ring; dx <= ring; dx++) {
					if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
						continue;   // 非环带内部点，跳过
					}
					int chunkX = cx + dx;
					int chunkZ = cz + dz;
					if (placement.isStructureChunk(state, chunkX, chunkZ)) {
						BlockPos p = placement.getLocatePos(new ChunkPos(chunkX, chunkZ));
						if (nearExisting(out, p.getX(), p.getZ())) {
							continue;
						}
						out.add(new int[]{p.getX(), p.getZ()});
						if (maxResults > 0 && out.size() >= maxResults) {
							return out;
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * 按方形网格采样，收集各采样点所在群系（L1 开服预索引用，也是纯推演、零区块依赖）。
	 *
	 * <p>以 (centerX, centerZ) 为中心、(centerX + gx*step, centerZ + gz*step)（gx,gz ∈ ±(radiusBlocks/step)）
	 * 网格采样，对每个点用与现有收搜 collectBiomes 完全相同的姿势取得该点群系：
	 * {@code getBiomeSource()} 取 source、{@code generatorState().randomState().sampler()} 取 sampler、
	 * {@code source.getNoiseBiome(x>>2, y>>2, z>>2, sampler)} 后按 holder {@code .unwrapKey()} 取键。
	 *
	 * @return 群系键 → 该网格命中的 {x,z} 采样点列表（每点经 4 格归一分区，相邻点间距=step）
	 */
	public static Map<ResourceKey<Biome>, List<int[]>> preIndexBiomes(ServerLevel level,
			int centerX, int centerZ, int radiusBlocks, int step) {
		net.minecraft.world.level.biome.BiomeSource source = null;
		net.minecraft.world.level.biome.Climate.Sampler sampler = null;
		try {
			source = level.getChunkSource().getGenerator().getBiomeSource();
			sampler = level.getChunkSource().getGeneratorState().randomState().sampler();
		} catch (Throwable t) {
			return new java.util.LinkedHashMap<>();
		}
		if (source == null || sampler == null) {
			return new java.util.LinkedHashMap<>();
		}

		Map<ResourceKey<Biome>, List<int[]>> buckets = new java.util.LinkedHashMap<>();
		int quartY = 64 >> 2;   // 固定取 y=64 的 4 格归一分区；噪声采样主要取决于 x/z，y 恒定时结论一致
		int gmax = radiusBlocks / step;
		for (int gz = -gmax; gz <= gmax; gz++) {
			for (int gx = -gmax; gx <= gmax; gx++) {
				int x = centerX + gx * step;
				int z = centerZ + gz * step;
				Holder<Biome> h;
				try {
					h = source.getNoiseBiome(x >> 2, quartY, z >> 2, sampler);
				} catch (Throwable t) {
					// 单点采样失败跳过，不影响整片网格。
					continue;
				}
				if (h == null) {
					continue;
				}
				ResourceKey<Biome> key;
				try {
					key = h.unwrapKey().orElse(null);
				} catch (Throwable t) {
					continue;
				}
				if (key != null) {
					buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[]{x, z});
				}
			}
		}
		return buckets;
	}

	/** 与已收集候选是否同格（差分区间 48 格，对齐现有 DollNetworking 的去重口径）。 */
	private static boolean nearExisting(List<int[]> candidates, int x, int z) {
		for (int[] c : candidates) {
			if (Math.abs(c[0] - x) <= 48 && Math.abs(c[1] - z) <= 48) {
				return true;
			}
		}
		return false;
	}

	private GeoIndexService() {
	}
}