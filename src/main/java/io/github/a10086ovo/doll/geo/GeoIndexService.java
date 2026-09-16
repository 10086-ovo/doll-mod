package io.github.a10086ovo.doll.geo;


import io.github.a10086ovo.doll.DollModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
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
 * 只枚举<b>候选区块</b>（对每个区域索引调用 {@code getPotentialStructureChunk} 取该区域唯一可能的候选区块，
 * <b>注意该方法收的是区块坐标</b>——见 {@link #collectRandomSpread} 的坐标单位陷阱），
 * 候选数降到约 {@code (2*radiusChunks/spacing)^2}。以 spacing=34 计约 3,500 个/结构，<b>约降 1000 倍</b>，
 * 且判定仍走 {@code isStructureChunk}（保留频率削减与排除区语义），结果集与满网格扫描一致。
 *
 * <p>要塞（{@link ConcentricRingsStructurePlacement}）单独处理：直接取原版已算好的
 * {@code getRingPositionsFor} 列表，无需枚举。未知 placement 类型回退满网格扫描（罕见路径）。
 *
 * <p><b>群系级校验（对所有结构都做）</b>：placement 级判定<b>完全不含群系约束</b>
 * （{@code isStructureChunk} 只有"潜在区块 + 频率削减 + 排除区"），所以枚举出的候选必须再用
 * {@code Structure.biomes()} 过滤一次，否则桶里会装进大量"当地根本没有该结构"的坐标。
 * 例外是"允许群系与同结构集兄弟重叠"的混型集合（下界堡垒/要塞、基础传送门与 6 个变体）——
 * 群系分不开，整类不做预索引，改走实时精确路径。详见 {@link #biomeAmbiguousWithSiblings}。
 *
 * <p>线程模型：读取 {@code ChunkGeneratorStructureState} 需在主线程安全情境（见
 * {@code GeoIndexBuildJob} 的每 tick 时间片推进），本类所有方法都只应在服务端主线程调用，
 * 不跨线程共享。
 */
public final class GeoIndexService {

	/** 诊断日志（与 geo 包其它类同一 logger）。 */
	private static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	/**
	 * 单个结构/村庄最多枚举的<b>区域环数</b>——给"单个结构的收集耗时"钉一个硬上界。
	 *
	 * <p><b>为什么必须钉这个上界</b>：{@link #collectRandomSpread} 的枚举量是
	 * {@code (2*ringMax+1)²}，而 {@code ringMax ≈ 半径区块数 / spacing}。半径 16000（1000 区块）下，
	 * {@code spacing=34} 的村庄只有约 29 环（约 3.5k 次区域判定，很快），
	 * 但 {@code spacing=1} 的密结构（埋藏的宝藏、废弃矿井）是 <b>1000 环 ≈ 400 万次</b>区域判定
	 * ——实测单个结构 3.9 秒；而 {@code GeoIndexBuildJob} 的时间片死线检查写在整段收集<b>返回之后</b>，
	 * 于是这 3.9 秒完全无法让出，直接把服务端线程卡到 {@code Can't keep up!}。
	 *
	 * <p>取 128 环后单个结构最多约 {@code (2*128+1)² ≈ 6.6 万} 次区域判定（实测数十毫秒），
	 * 让出粒度重新可控。代价只落在<b>密结构</b>上：覆盖半径被压到 {@code 128 × spacing} 区块区间，
	 * 例如 {@code spacing=1} 的埋藏宝藏/废弃矿井只剩约 2048 格（原 16000）；而 {@code spacing}
	 * 较大、环数本来就到不了 128 的类型（村庄、要塞等）<b>完全不受影响</b>。
	 * 密结构"全球概览"的价值本来就最低（到处都是），其近处结果由按需收集与实时兜底补上。
	 */
	private static final int MAX_REGION_RINGS = 128;

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

		// 群系级校验：对**所有**结构都做。
		// placement 级判定（getPotentialStructureChunk + isStructureChunk）**完全不含群系约束**
		// —— isStructureChunk 只有「潜在区块 + 频率削减 + 排除区」三段（见类注释/技能 §1），
		// 所以不校验的桶里会装进大量"当地根本没有该结构"的坐标。实测（受控对比，同一允许群系集合）：
		// desert_pyramid 桶仅 4% 落在沙漠群系采样附近，而同为 {desert} 的村庄桶是 100%
		// —— 前者当时因"单成员结构集跳过校验"而未被过滤。
		// 本校验只会丢掉"不在该结构自身声明的 biomes() 里"的点，**不可能误杀合法点**，
		// 成本 ≤5 次 getNoiseBiome/候选（相对群系阶段的数十万次可忽略）。
		//
		// 唯一例外：允许群系与"同结构集兄弟"重叠的类型（下界 bastion_remnant↔fortress；
		// 基础 ruined_portal↔6 个变体）。群系校验分不开它们——同一个点两边都能通过，桶里会混进
		// 兄弟类型的坐标。这类**整类不做预索引**（返回空），查询会自然回落到类型精确的实时定位路径
		//（DollNetworking 链路：索引为空 → 共享缓存 → 分片式 findNearestMapStructure）。
		try {
			if (biomeAmbiguousWithSiblings(state, structureKey)) {
				return List.of();
			}
		} catch (Throwable t) {
			// 混型判定失败：按"不混型"处理，继续走群系校验（宁可保留可疑点也不误杀）
		}
		BiomeGate gate = buildBiomeGate(level, structureKey);

		List<int[]> out = new ArrayList<>();
		int radiusChunks = Math.max(0, (radiusBlocks + 15) / 16);
		int cx = centerX >> 4;
		int cz = centerZ >> 4;
		for (StructurePlacement placement : placements) {
			try {
				if (placement instanceof RandomSpreadStructurePlacement rsp) {
					collectRandomSpread(state, rsp, cx, cz, radiusChunks, maxResults, out, gate);
				} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
					collectRings(state, rings, cx, cz, radiusChunks, maxResults, out, gate);
				} else {
					collectByGridScan(state, placement, cx, cz, radiusChunks, maxResults, out, gate);
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
	 * <p>区域索引 → 候选区块由 {@code getPotentialStructureChunk(seed, x, z)} 给出；
	 * 再过滤到半径内、并以 {@code isStructureChunk} 复核（含频率削减 {@code frequency} 与
	 * 排除区 {@code exclusionZone}），因此结果与满网格扫描完全一致，只是判定次数少了约三个数量级。
	 *
	 * <p><b>⚠ 坐标单位陷阱（曾因此漏掉约 8/9 的结构、村庄几乎全丢）</b>：
	 * {@code getPotentialStructureChunk} 的第 2/3 个参数是<b>区块坐标</b>，不是区域索引——
	 * 它内部会自己做 {@code floorDiv(x, spacing)} 再换算成区域。旁证：原版
	 * {@code StructurePlacement.isPlacementChunk(state, x, z)} 就是把原始区块坐标直接传进去做自比较。
	 * 因此这里必须传 {@code rx * spacing}（该区域内的任一区块坐标，{@code floorDiv(rx*spacing, spacing) == rx}
	 * 对负值同样成立）。若误传区域索引本身，{@code floorDiv(rx, spacing)} 会在整个窗口上只取到极少数几个值，
	 * 候选集塌成 (2*radiusChunks/spacing²)² 个（spacing=34 时仅 2×2=4 个），且结果与区域窗口无关。
	 */
	private static void collectRandomSpread(ChunkGeneratorStructureState state, RandomSpreadStructurePlacement rsp,
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out, BiomeGate gate) {
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
		// ★ 硬上界：见 MAX_REGION_RINGS 的说明——不夹这一下，spacing=1 的密结构会一口气枚举 400 万次，
		// 独占服务端线程数秒，而时间片是在整段收集返回之后才检查的，兜不住。
		ringMax = Math.min(ringMax, MAX_REGION_RINGS);

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
					// 传区块坐标（rx*spacing），不能传区域索引——见方法注释的坐标单位陷阱
					ChunkPos cand = rsp.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
					if (cand == null || cand.getChessboardDistance(cx, cz) > radiusChunks) {
						continue;
					}
					if (!rsp.isStructureChunk(state, cand.x(), cand.z())) {
						continue;
					}
					BlockPos p = rsp.getLocatePos(cand);
					if (gate != null && !gate.allows(p.getX(), p.getZ())) {
						continue;   // 类型级校验不过：该点群系不属于目标结构（多为同集兄弟类型的位置）
					}
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
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out, BiomeGate gate) {
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
			if (gate != null && !gate.allows(p.getX(), p.getZ())) {
				continue;   // 类型级校验不过：该点群系不属于目标结构
			}
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
			int cx, int cz, int radiusChunks, int maxResults, List<int[]> out, BiomeGate gate) {
		// 上界（量纲与 collectRandomSpread 的"环数"不同）：满网格是 O(半径²)，这里把边长夹到
		// MAX_REGION_RINGS*16 = 2048 区块，即最多约 (2*2048+1)² ≈ 1680 万次判定。
		// ⚠ 该上界其实只对"半径 > 32768 格（2048 区块）"才生效——默认半径 1000 区块时
		// min(1000, 2048) = 1000，等于没夹。之所以仍留 2048 这个宽松值：本路径仅在
		// "未知 placement 类型"（mod 自带 placement）时可达，原版的 RandomSpread / ConcentricRings
		// 都走各自的上界分支 ⇒ 这里的量级风险只是理论上的（现网近似死代码）。
		int rc = Math.min(radiusChunks, MAX_REGION_RINGS * 16);
		for (int dz = -rc; dz <= rc; dz++) {
			for (int dx = -rc; dx <= rc; dx++) {
				int chunkX = cx + dx;
				int chunkZ = cz + dz;
				if (!placement.isStructureChunk(state, chunkX, chunkZ)) {
					continue;
				}
				BlockPos p = placement.getLocatePos(new ChunkPos(chunkX, chunkZ));
				if (gate != null && !gate.allows(p.getX(), p.getZ())) {
					continue;   // 类型级校验不过：该点群系不属于目标结构
				}
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

	/**
	 * 目标的允许群系是否与"同结构集里的其它类型"重叠——重叠即为<b>混型</b>，群系级校验分不开它们。
	 *
	 * <p>只有多成员结构集才可能重叠，且这些成员**共用同一批候选区块**（同一 {@link StructurePlacement}）。
	 * 判定用两侧 {@code biomes()} 的 holder 身份求交：有交集 → 同一个候选点在兄弟类型那边也通过校验
	 * → 桶里必然混入兄弟类型的坐标。此时做预索引只会产出"类型张冠李戴"的结果，不如**整类不做**
	 * （返回空 → 查询回落到类型精确的实时定位路径）。
	 *
	 * <p>实测混型集合（原版仅 6 个多成员结构集，其中 2 个重叠）：
	 * <ul>
	 *   <li>{@code nether_complexes}：{@code fortress}(2) ↔ {@code bastion_remnant}(3)，允许群系重叠
	 *       → 两桶 jaccard 实测 0.70；</li>
	 *   <li>{@code ruined_portals}：基础 {@code ruined_portal} 允许群系=全部 → 与 6 个变体全部重叠
	 *       （实测重合 0.37~0.62）。</li>
	 * </ul>
	 * 其余 4 个多成员集合（villages / mineshafts / ocean_ruins / shipwrecks）的成员允许群系互斥，
	 * 群系校验即可分开（实测 5 种村庄桶两两 jaccard = 0），故不算混型。
	 */
	private static boolean biomeAmbiguousWithSiblings(ChunkGeneratorStructureState state,
			ResourceKey<Structure> structureKey) {
		if (state == null || state.possibleStructureSets() == null) {
			return false;
		}
		for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
			if (setHolder == null || setHolder.value() == null) {
				continue;
			}
			StructureSet set = setHolder.value();
			if (set.structures() == null || set.structures().size() <= 1) {
				continue;
			}
			HolderSet<Biome> own = null;
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry != null && entry.structure() != null && entry.structure().is(structureKey)) {
					own = entry.structure().value().biomes();
					break;
				}
			}
			if (own == null) {
				continue;   // 目标不在该集合 → 不判混型
			}
			if (own.size() == 0) {
				// 目标确在该集合，但自身允许群系为空。空集按"不判混型"处理 ⇒ 它既不排除，
				// buildBiomeGate 又会因 allowed.size()==0 返回 null（不过滤）⇒ 该类型的桶保持"未过滤"。
				// 原版实测未出现此情形（多成员集里每个成员的 biomes() 都非空），故当前无害；
				// 但这是一条静默假设 —— 若将来数据包/版本让某成员允许群系为空（语义=全部），
				// 该桶会静默混入兄弟类型坐标。故留一条 debug 记录以便事后追查。
				LOGGER.debug("[GeoIndex] 结构 {} 位于多成员结构集但自身允许群系为空：既不判混型也不做群系过滤",
					structureKey);
				continue;
			}
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry == null || entry.structure() == null || entry.structure().is(structureKey)) {
					continue;
				}
				HolderSet<Biome> sibling = entry.structure().value().biomes();
				if (sibling != null && intersects(own, sibling)) {
					return true;
				}
			}
		}
		return false;
	}

	/** 混型判定（供日志/自检使用）。主线程调用。 */
	public static boolean biomeAmbiguousWithSiblings(ServerLevel level, ResourceKey<Structure> structureKey) {
		try {
			ChunkGeneratorStructureState st = level.getChunkSource().getGeneratorState();
			return st != null && biomeAmbiguousWithSiblings(st, structureKey);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 两个允许群系集合是否有共同 holder（比较 holder 身份，tag 型集合与直列型集合都适用）。 */
	private static boolean intersects(HolderSet<Biome> a, HolderSet<Biome> b) {
		HolderSet<Biome> small = a.size() <= b.size() ? a : b;
		HolderSet<Biome> big = small == a ? b : a;
		for (Holder<Biome> h : small) {
			if (big.contains(h)) {
				return true;
			}
		}
		return false;
	}

	/** 构造目标的群系级校验器（读其 {@code Structure.biomes()} 与 BiomeSource/Sampler）；失败返回 null=不校验。 */
	private static BiomeGate buildBiomeGate(ServerLevel level, ResourceKey<Structure> structureKey) {
		try {
			Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.get(structureKey).map(h -> h.value()).orElse(null);
			if (structure == null) {
				return null;
			}
			HolderSet<Biome> allowed = structure.biomes();
			if (allowed == null || allowed.size() == 0) {
				return null;
			}
			BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
			Climate.Sampler sampler = level.getChunkSource().getGeneratorState().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED);
			if (source == null || sampler == null) {
				return null;
			}
			return new BiomeGate(allowed, source, sampler);
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 类型级过滤器：判断某候选点所在群系是否属于目标结构<b>自身声明</b>的 {@code biomes()}。
	 *
	 * <p><b>Y 的取值</b>：{@code Structure.biomes()} 声明的是"允许群系"，能否命中与采样高度有关——
	 * 地表村庄看 Y≈64，深暗之域的远古城市要看 Y≈-64。故按高度分档探测（见 {@link #PROBE_QUART_YS}）：
	 * 第一档就是群系索引的地表档（64），命中即止，地表结构只多花一次采样；未命中再依次探更高与更深档。
	 *
	 * <p><b>⚠ 本档位表刻意比群系搜索更宽，勿当成"同一套采样高度"</b>：群系搜索用 3 档
	 * （{@link GeoIndexService#BIOME_PROBE_BLOCK_YS} = 64/0/-64），本 gate 用 5 档（多出 320 与 -32）。
	 * 这是有意的"宁宽勿窄"——gate 只做粗筛，多留下的假阳性由后续生成校验
	 * （{@code StructureGenVerifier}）逐点剔除；反之漏筛掉真点不可挽回。
	 *
	 * <p>这是"够用的近似"：它只校验<b>群系</b>，不含原版逐结构完整判定里的最低高度、结构内概率等
	 * 条件，故仍可能有极小出入；<b>采样异常时一律放行（返回 true）</b>，宁可保留可疑点也不误杀，
	 * 漏掉的部分由实时定位（类型精确）兜底。
	 *
	 * <p>适用范围：<b>所有结构</b>（不只多成员集）。唯一例外是"允许群系与同集兄弟重叠"的混型集合，
	 * 那种情况群系校验无效，由 {@link #biomeAmbiguousWithSiblings} 整类跳过预索引。
	 */
	private static final class BiomeGate {
		/** 探测用的四分位 Y 档：地表(64) → 高位(320) → 0 → -32 → -64，覆盖地表与地下结构。 */
		private static final int[] PROBE_QUART_YS = {64 >> 2, 320 >> 2, 0, -32 >> 2, -64 >> 2};

		private final HolderSet<Biome> allowed;
		private final BiomeSource source;
		private final Climate.Sampler sampler;

		BiomeGate(HolderSet<Biome> allowed, BiomeSource source, Climate.Sampler sampler) {
			this.allowed = allowed;
			this.source = source;
			this.sampler = sampler;
		}

		/** 该方块坐标处的群系（任一探测高度）是否属于目标结构的允许群系。 */
		boolean allows(int blockX, int blockZ) {
			try {
				int qx = blockX >> 2;
				int qz = blockZ >> 2;
				BiomeResolver resolver = source.createResolver(sampler);
				for (int qy : PROBE_QUART_YS) {
					Holder<Biome> h = resolver.getNoiseBiome(qx, qy, qz);
					if (h != null && allowed.contains(h)) {
						return true;
					}
				}
			} catch (Throwable t) {
				return true;
			}
			return false;
		}
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
	 * {@code targetIndex}，也是 GeoIndex 的 geoKey 后半段。必须与 {@code DollNetworking.resolveStructureKey}
	 * 完全一致（同样的字符串排序、同样的 {@code village_} 前缀过滤），否则索引坐标会与目标错位；
	 * {@link #orderedBiomeKeys} 同理对应 {@code DollNetworking.resolveBiomeKey}。
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
	 * 群系多档采样的探测高度（方块 Y）：地表 → 地下中层 → 深层。
	 *
	 * <p><b>为什么要多档</b>：群系在 1.18+ 是三维噪声——同一 (x,z) 列，地表（Y≈64）与地下
	 * （Y≈0 / Y≈-64）可以是完全不同的群系（如地表平原、地下繁茂洞穴、深层深暗之域）。旧实现
	 * 只探地表一档，导致洞穴类群系（繁茂洞穴、滴水石洞穴、深暗之域）<b>永远搜不到</b>；
	 * 且预索引（固定地表 64）与实时搜索（玩家所在高度）采样口径不一致，同一目标两处结果对不上。
	 * 现预索引与实时搜索统一走本数组，口径一致。
	 *
	 * <p><b>代价</b>：采样次数 = 网格点数 × 档数，故档数即索引「群系阶段」耗时的倍数（当前 3 档）。
	 * 想扩档（例如再补 Y=32 提高洞穴命中率）直接往数组里加即可，无需改其它逻辑。
	 */
	public static final int[] BIOME_PROBE_BLOCK_YS = {64, 0, -64};

	/** 「该列在任何探测高度都不属于目标群系」的哨兵值；客户端据此不显示 Y。 */
	public static final int NO_Y = Integer.MIN_VALUE;

	/**
	 * 该 (x,z) 列上命中目标群系的<b>代表方块 Y</b>（首个命中的探测高度）；任一档都不命中返回 {@link #NO_Y}。
	 *
	 * <p>纯噪声采样，只读 {@code source}/{@code sampler}，<b>线程安全</b>（可用于后台搜索线程）。
	 * 返回的 Y 是「探测高度」而非群系的精确上下界——它只保证"此高度处确实属于该群系"，
	 * 恰好就是玩家需要的落点信息。
	 */
	public static int probeMatchY(BiomeSource source, Climate.Sampler sampler,
			int blockX, int blockZ, ResourceKey<Biome> target) {
		if (source == null || sampler == null || target == null) {
			return NO_Y;
		}
		try {
			int qx = blockX >> 2;
			int qz = blockZ >> 2;
			BiomeResolver resolver = source.createResolver(sampler);
			for (int by : BIOME_PROBE_BLOCK_YS) {
				Holder<Biome> h = resolver.getNoiseBiome(qx, by >> 2, qz);
				if (h != null && h.is(target)) {
					return by;
				}
			}
		} catch (Throwable t) {
			return NO_Y;
		}
		return NO_Y;
	}

	/**
	 * 该 (x,z) 列上各探测高度命中的群系键（去重、按 {@link #BIOME_PROBE_BLOCK_YS} 顺序）。
	 *
	 * <p>索引构建用：一次列探测把地表与地下的群系一并登记，洞穴类群系因此可被索引到。
	 * 纯噪声采样，线程安全。
	 */
	public static List<ResourceKey<Biome>> probeKeys(BiomeSource source, Climate.Sampler sampler,
			int blockX, int blockZ) {
		if (source == null || sampler == null) {
			return List.of();
		}
		List<ResourceKey<Biome>> out = null;
		try {
			int qx = blockX >> 2;
			int qz = blockZ >> 2;
			BiomeResolver resolver = source.createResolver(sampler);
			for (int by : BIOME_PROBE_BLOCK_YS) {
				Holder<Biome> h = resolver.getNoiseBiome(qx, by >> 2, qz);
				if (h == null) {
					continue;
				}
				ResourceKey<Biome> k = h.unwrapKey().orElse(null);
				if (k == null) {
					continue;
				}
				if (out == null) {
					out = new ArrayList<>(2);
				} else if (out.contains(k)) {
					continue;   // 同列多档命中同一群系（如地表与深层都是它）：只登记一次
				}
				out.add(k);
			}
		} catch (Throwable t) {
			return out != null ? out : List.of();
		}
		return out != null ? out : List.of();
	}

	/**
	 * 群系方形网格采样器：一次性取好 {@code BiomeSource} / {@code Climate.Sampler} 引用（必须在主线程），
	 * 之后可按点逐步采样，便于时间片推进（见 {@code GeoIndexBuildJob}）。
	 *
	 * <p>纯噪声采样（{@code getNoiseBiome}），全程不加载、不生成区块。
	 */
	public static final class BiomeGridSampler {
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
				Climate.Sampler sampler = level.getChunkSource().getGeneratorState().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED);
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

		/** 采样网格点 (gx,gz) 在<b>各探测高度</b>命中的群系键（去重）；单点失败或无可取键时返回空列表。 */
		public List<ResourceKey<Biome>> keysAt(int gx, int gz) {
			return probeKeys(source, sampler, centerX + gx * step, centerZ + gz * step);
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
