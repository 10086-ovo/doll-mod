package io.github.a10086ovo.doll.geo;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GeoIndex 索引服务（M1：底图圈内候选收集 + 内存索引）。
 *
 * <p>核心目标：对"当前维度某类目标"在给定方块半径内批量收集候选坐标，且全程<b>纯推演</b>——
 * 只读 {@link ChunkGeneratorStructureState} / {@code isStructureChunk} 做确定性判定，
 * <b>不加载、不生成区块</b>（对齐红线第6条与终稿 D10：复用原版定位 API，不自造网格枚举轮子）。
 *
 * <p><b>扫描方式（本轮优化重点）</b>：结构<b>不再逐区块满网格判定</b>。原实现对半径 16000 格
 * 要遍历 {@code (2*1000+1)^2 = 4,004,001} 个区块 × 每个结构，单维度约 1 亿次 {@code isStructureChunk}，
 * 实测每维度 35~44 秒。现改为原版 {@code /locate} 同款做法：按 placement 的 {@code spacing}
 * 只枚举<b>候选区块</b>（{@code getPotentialStructureChunk} 把"区域索引"换算成该区域唯一可能的候选区块），
 * 候选数降到约 {@code (2*1000/spacing)^2}。以 spacing=32 计约 4,200 个/结构，<b>约降 1000 倍</b>，
 * 且判定仍走 {@code isStructureChunk}（保留频率削减与排除区语义），结果集与满网格扫描一致。
 *
 * <p>要塞（{@link ConcentricRingsStructurePlacement}）单独处理：直接取原版已算好的
 * {@code getRingPositionsFor} 列表，无需枚举。未知 placement 类型回退满网格扫描（罕见路径）。
 *
 * <p>线程模型：读取 {@code ChunkGeneratorStructureState} 需在主线程安全情境（见
 * {@code GeoIndexBuildJob} 的每 tick 时间片推进），本类所有方法都只应在服务端主线程调用，
 * 不跨线程共享。
 */
public final class GeoIndexService {

	/**
	 * 收集某个结构/村庄在指定圆心与方块半径内的候选坐标，<b>环形由近及远</b>。
	 *
	 * <p>遍历顺序：先中心候选、再相邻候选环……因此一旦达到 {@code maxResults} 提前返回，
	 * 被丢弃的必定是更远处的候选，"最近命中"语义成立（L1 开服预索引用）。
	 *
	 * @param level           服务端世界（提供索引 state 与注册表）
	 * @param structureKey    目标结构注册键（如 minecraft:ancient_city）
	 * @param centerX,centerZ 圆心（方块坐标，通常为该维度出生点）
	 * @param radiusBlocks    收集半径（方块）
	 * @param maxResults      最多收集多少个候选；&gt;0 时命中即停
	 */
	public static List<int[]> collectNearest(ServerLevel level, ResourceKey<Structure> structureKey,
			int centerX, int centerZ, int radiusBlocks, int maxResults) {
		ChunkGeneratorStructureState state;
		List<StructurePlacement> placements;
		try {
			state = level.getChunkSource().getGeneratorState();
			if (state == null) {
				return List.of();
			}
			placements = placementsFor(state, structureKey);
		} catch (Throwable t) {
			// 刚进世界时 GeneratorState 可能尚未就绪：视为无可收集。
			return List.of();
		}
		if (placements.isEmpty()) {
			return List.of();
		}

		List<int[]> out = new ArrayList<>();
		int radiusChunks = Math.max(0, (radiusBlocks + 15) / 16);
		int cx = centerX >> 4;
		int cz = centerZ >> 4;
		for (StructurePlacement placement : placements) {
			try {
				if (placement instanceof RandomSpreadStructurePlacement rsp) {
					collectRandomSpread(state, rsp, cx, cz, radiusChunks, maxResults, out);
				} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
					collectRings(state, rings, cx, cz, radiusChunks, maxResults, out);
				} else {
					collectByGridScan(state, placement, cx, cz, radiusChunks, maxResults, out);
				}
			} catch (Throwable t) {
				// 单个 placement 失败只跳过它，不影响其它 placement（要塞等）。
			}
			if (maxResults > 0 && out.size() >= maxResults) {
				break;
			}
		}
		return out;
	}

	/** 与 {@link #collectNearest} 同义（保留旧入口：旧实现为"整圈无序收集"，现统一为环形由近及远）。 */
	public static List<int[]> collectCandidates(ServerLevel level, ResourceKey<Structure> structureKey,
			int centerX, int centerZ, int radiusBlocks, int maxResults) {
		return collectNearest(level, structureKey, centerX, centerZ, radiusBlocks, maxResults);
	}

	/**
	 * 按 spacing 只枚举候选区块（原版 {@code /locate} 同款），环形由近及远。
	 *
	 * <p>区域索引 → 候选区块由 {@code getPotentialStructureChunk(seed, regionX, regionZ)} 给出；
	 * 再过滤到半径内、并以 {@code isStructureChunk} 复核（含频率削减 {@code frequency} 与
	 * 排除区 {@code exclusionZone}），因此结果与满网格扫描完全一致，只是判定次数少了约三个数量级。
	 */
	private static void collectRandomSpread(ChunkGeneratorStructureState state, RandomSpreadStructurePlacement rsp,
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out) {
		long seed = state.getLevelSeed();
		int spacing = Math.max(1, rsp.spacing());
		int centerRX = Math.floorDiv(cx, spacing);
		int centerRZ = Math.floorDiv(cz, spacing);
		int minRX = Math.floorDiv(cx - radiusChunks, spacing);
		int maxRX = Math.floorDiv(cx + radiusChunks, spacing);
		int minRZ = Math.floorDiv(cz - radiusChunks, spacing);
		int maxRZ = Math.floorDiv(cz + radiusChunks, spacing);
		int ringMax = 0;
		ringMax = Math.max(ringMax, Math.abs(maxRX - centerRX));
		ringMax = Math.max(ringMax, Math.abs(minRX - centerRX));
		ringMax = Math.max(ringMax, Math.abs(maxRZ - centerRZ));
		ringMax = Math.max(ringMax, Math.abs(minRZ - centerRZ));

		for (int ring = 0; ring <= ringMax; ring++) {
			for (int rz = centerRZ - ring; rz <= centerRZ + ring; rz++) {
				for (int rx = centerRX - ring; rx <= centerRX + ring; rx++) {
					// 只走"环带"本身，跳过内部已处理过的区域
					if (Math.abs(rx - centerRX) != ring && Math.abs(rz - centerRZ) != ring) {
						continue;
					}
					if (rx < minRX || rx > maxRX || rz < minRZ || rz > maxRZ) {
						continue;
					}
					ChunkPos cand = rsp.getPotentialStructureChunk(seed, rx, rz);
					if (cand == null || cand.getChessboardDistance(cx, cz) > radiusChunks) {
						continue;
					}
					if (!rsp.isStructureChunk(state, cand.x(), cand.z())) {
						continue;
					}
					BlockPos p = rsp.getLocatePos(cand);
					if (nearExisting(out, p.getX(), p.getZ())) {
						continue;
					}
					out.add(new int[]{p.getX(), p.getZ()});
					if (maxResults > 0 && out.size() >= maxResults) {
						return;
					}
				}
			}
		}
	}

	/** 要塞：原版已把环上候选区块算好（{@code getRingPositionsFor}），直接取用，无需枚举。 */
	private static void collectRings(ChunkGeneratorStructureState state, ConcentricRingsStructurePlacement rings,
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out) {
		List<ChunkPos> positions = state.getRingPositionsFor(rings);
		if (positions == null || positions.isEmpty()) {
			return;
		}
		List<ChunkPos> inRange = new ArrayList<>();
		for (ChunkPos cp : positions) {
			if (cp != null && cp.getChessboardDistance(cx, cz) <= radiusChunks) {
				inRange.add(cp);
			}
		}
		inRange.sort(Comparator.comparingLong(cp -> sqDist(cp.x() - cx, cp.z() - cz)));
		for (ChunkPos cp : inRange) {
			if (!rings.isStructureChunk(state, cp.x(), cp.z())) {
				continue;
			}
			BlockPos p = rings.getLocatePos(cp);
			if (nearExisting(out, p.getX(), p.getZ())) {
				continue;
			}
			out.add(new int[]{p.getX(), p.getZ()});
			if (maxResults > 0 && out.size() >= maxResults) {
				return;
			}
		}
	}

	/** 未知 placement 类型的安全回退：满网格逐区块判定（mod 若自定义 placement 才会走到这里）。 */
	private static void collectByGridScan(ChunkGeneratorStructureState state, StructurePlacement placement,
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out) {
		for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
			for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
				int chunkX = cx + dx;
				int chunkZ = cz + dz;
				if (!placement.isStructureChunk(state, chunkX, chunkZ)) {
					continue;
				}
				BlockPos p = placement.getLocatePos(new ChunkPos(chunkX, chunkZ));
				if (nearExisting(out, p.getX(), p.getZ())) {
					continue;
				}
				out.add(new int[]{p.getX(), p.getZ()});
				if (maxResults > 0 && out.size() >= maxResults) {
					return;
				}
			}
		}
	}

	/**
	 * 自 {@code possibleStructureSets()} 反查包含目标结构的<b>全部</b> placement。
	 *
	 * <p>同一结构可能出现在多个结构集里（placement 不同），全部收集再合并，避免漏掉其中一套。
	 */
	private static List<StructurePlacement> placementsFor(ChunkGeneratorStructureState state,
			ResourceKey<Structure> structureKey) {
		List<StructurePlacement> out = new ArrayList<>(2);
		if (state.possibleStructureSets() == null) {
			return out;
		}
		for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
			if (setHolder == null || setHolder.value() == null) {
				continue;
			}
			StructureSet set = setHolder.value();
			if (set.structures() == null || set.placement() == null) {
				continue;
			}
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry != null && entry.structure() != null && entry.structure().is(structureKey)) {
					StructurePlacement p = set.placement();
					if (!containsIdentity(out, p)) {
						out.add(p);
					}
					break;
				}
			}
		}
		return out;
	}

	/** 该结构在当前维度是否可放置（结构集反查命中即视为可放置）。主线程调用。 */
	public static boolean structurePossibleInDimension(ServerLevel level, ResourceKey<Structure> structureKey) {
		try {
			ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
			return !placementsFor(state, structureKey).isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}

	/** 该群系是否为当前维度可能生成：查看维度 BiomeSource 的 possibleBiomes。主线程调用。 */
	public static boolean biomePossibleInDimension(ServerLevel level, ResourceKey<Biome> key) {
		try {
			return level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
				.anyMatch(h -> h.is(key));
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 某维度全部结构注册键，<b>按字符串排序；villagesOnly=true 只留 {@code village_} 前缀</b>。
	 *
	 * <p><b>不变量（切勿改动排序/过滤规则）</b>：本方法的下标即客户端与服务端展示该结构时使用的
	 * {@code targetIndex}，也是 GeoIndex 的 geoKey 后半段。必须与 {@code DollNetworking} 的
	 * {@code resolveStructureKey} / {@code structuresList} 完全一致，否则索引坐标会与目标错位。
	 */
	public static List<ResourceKey<Structure>> orderedStructureKeys(ServerLevel level, boolean villagesOnly) {
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<net.minecraft.resources.Identifier> ids = new ArrayList<>(reg.keySet());
		ids.removeIf(id -> id.getPath().startsWith("village_") != villagesOnly);
		ids.sort(Comparator.comparing(net.minecraft.resources.Identifier::toString));
		List<ResourceKey<Structure>> out = new ArrayList<>(ids.size());
		for (net.minecraft.resources.Identifier id : ids) {
			out.add(ResourceKey.create(Registries.STRUCTURE, id));
		}
		return out;
	}

	/** 某维度全部群系注册键，按字符串排序；下标即群系 targetIndex（同 {@link #orderedStructureKeys} 的不变量）。 */
	public static List<ResourceKey<Biome>> orderedBiomeKeys(ServerLevel level) {
		var reg = level.registryAccess().lookupOrThrow(Registries.BIOME);
		List<net.minecraft.resources.Identifier> ids = new ArrayList<>(reg.keySet());
		ids.sort(Comparator.comparing(net.minecraft.resources.Identifier::toString));
		List<ResourceKey<Biome>> out = new ArrayList<>(ids.size());
		for (net.minecraft.resources.Identifier id : ids) {
			out.add(ResourceKey.create(Registries.BIOME, id));
		}
		return out;
	}

	/**
	 * 群系方形网格采样器：一次性取好 {@code BiomeSource} / {@code Climate.Sampler} 引用（必须在主线程），
	 * 之后可按点逐步采样，便于时间片推进（见 {@code GeoIndexBuildJob}）。
	 *
	 * <p>纯噪声采样（{@code getNoiseBiome}），全程不加载、不生成区块。
	 */
	public static final class BiomeGridSampler {
		/** 采样 Y（4 格归一坐标）：固定地表高度 64，与旧实现一致。 */
		private static final int QUART_Y = 64 >> 2;

		private final BiomeSource source;
		private final Climate.Sampler sampler;
		private final int centerX;
		private final int centerZ;
		private final int step;
		private final int gmax;

		private BiomeGridSampler(BiomeSource source, Climate.Sampler sampler,
				int centerX, int centerZ, int step, int gmax) {
			this.source = source;
			this.sampler = sampler;
			this.centerX = centerX;
			this.centerZ = centerZ;
			this.step = step;
			this.gmax = gmax;
		}

		/** 取引用并构造；GeneratorState 未就绪时返回 null（调用方按"本次不采样"处理）。 */
		public static BiomeGridSampler create(ServerLevel level, int centerX, int centerZ,
				int radiusBlocks, int step) {
			try {
				int s = Math.max(1, step);
				BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
				Climate.Sampler sampler = level.getChunkSource().getGeneratorState().randomState().sampler();
				if (source == null || sampler == null) {
					return null;
				}
				return new BiomeGridSampler(source, sampler, centerX, centerZ, s, radiusBlocks / s);
			} catch (Throwable t) {
				return null;
			}
		}

		/** 网格半径（含端点）：gx/gz 的合法取值区间为 {@code [-gmax, gmax]}。 */
		public int gmax() {
			return gmax;
		}

		/** 采样网格点 (gx,gz) 所在群系键；单点失败或无可取键时返回 null。 */
		public ResourceKey<Biome> keyAt(int gx, int gz) {
			int x = centerX + gx * step;
			int z = centerZ + gz * step;
			try {
				Holder<Biome> h = source.getNoiseBiome(x >> 2, QUART_Y, z >> 2, sampler);
				if (h == null) {
					return null;
				}
				return h.unwrapKey().orElse(null);
			} catch (Throwable t) {
				return null;
			}
		}

		/** 网格点 (gx,gz) 的方块坐标（写入索引用的代表点）。 */
		public int[] posAt(int gx, int gz) {
			return new int[]{centerX + gx * step, centerZ + gz * step};
		}
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

	private static long sqDist(int dx, int dz) {
		return (long) dx * dx + (long) dz * dz;
	}

	private static boolean containsIdentity(List<StructurePlacement> list, StructurePlacement p) {
		for (StructurePlacement e : list) {
			if (e == p) {
				return true;
			}
		}
		return false;
	}

	private GeoIndexService() {
	}
}
