package io.github.a10086ovo.doll.geo;


import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 结构「真会生成」判定器——<b>直接调用原版区块生成所用的同一个函数</b>。
 *
 * <p>本类原为 {@code DollNetworking} 的私有实现（搜索时的生成校验用它）。抽到 {@code geo} 包是因为
 * 底图构建任务（{@link GeoIndexBuildJob} 的村庄预确认阶段）也要用同一份判定：两处各写一份迟早会分叉，
 * 而反向依赖（{@code geo} 依赖 {@code network}）又无谓地制造包循环。
 *
 * <p>{@code ChunkGenerator.createStructures} 真实生成时就是：
 * {@code structure.generate(holder, level, registryAccess, this, biomeSource, randomState,
 * templateManager, seed, chunkPos, references, centerChunk, structure.biomes()::contains)}，
 * 随后 {@code start.isValid()} 为真才落盘。这里用<b>完全相同的参数</b>调用，故判定与真实生成等价
 *（差别只有 {@code references}：原版由 {@code fetchReferences} 数出，只写进存档的引用计数，
 * 不参与几何有效性，故传 0）。
 *
 * <p><b>为什么不需要加载区块</b>：{@code Structure.GenerationContext} 里只有
 * {@code registryAccess / chunkGenerator / biomeSource / randomState / structureTemplateManager /
 * seed / chunkPos / heightAccessor / validBiome}，<b>没有 LevelReader/ChunkAccess</b>；
 * jigsaw 装配（{@code JigsawPlacement.addPieces}）也只依赖噪声与结构模板。
 * 因此判定天然可在<b>工作线程</b>执行（原版世界生成本就在工作线程跑这段），
 * 前提是 {@link Env} 里的引用已在主线程取好。
 *
 * <p>线程安全性：{@link Env} 里的 {@code RandomState}/{@code StructureTemplateManager} 等均为只读或
 * 线程安全对象（26.2 的 {@code structureRepository} 是 {@code ConcurrentHashMap}），
 * 可被多个工作线程同时只读使用；{@link #create(ServerLevel)} 则<b>必须</b>在主线程调用。
 */
public final class StructureGenVerifier {

	private StructureGenVerifier() {
	}

	/**
	 * 一份"生成判定环境"：把 {@code structure.generate(...)} 需要的全部引用在主线程取好后固定下来，
	 * 供工作线程只读使用。
	 *
	 * <p>之所以要单独打包，是因为这些引用里 {@code ChunkGeneratorStructureState}（懒初始化的可变容器）
	 * 必须在主线程取；取出来的 {@code RandomState}/{@code StructureTemplateManager} 等则都是只读或
	 * 线程安全对象，可以安全跨线程。
	 */
	public record Env(RegistryAccess registryAccess, ChunkGenerator generator,
			BiomeSource biomeSource, RandomState randomState, StructureTemplateManager templateManager,
			long seed, LevelHeightAccessor heightAccessor, ResourceKey<Level> dimension) {
	}

	/**
	 * 在工作线程取好"生成判定环境"——<b>必须在主线程调用</b>。
	 *
	 * <p>{@code getGeneratorState()} 返回的 {@code ChunkGeneratorStructureState} 是懒初始化的可变容器，
	 * 且带 {@code getRingPositionsFor} 这类带缓存的懒加载方法；在主线程把它取出来、只把
	 * {@code RandomState}/{@code StructureTemplateManager} 等只读对象传出去，才谈得上线程安全。
	 * 与 {@code startBiomeSearchAsync} 的做法一致。
	 *
	 * @return null = 环境不可用（调用方应放弃本次校验/预确认，不会因此丢结果）
	 */
	public static Env create(ServerLevel level) {
		try {
			var chunkSource = level.getChunkSource();
			var generator = chunkSource.getGenerator();
			var state = chunkSource.getGeneratorState();
			if (generator == null || state == null) {
				return null;
			}
			BiomeSource source = generator.getBiomeSource();
			if (source == null) {
				return null;
			}
			return new Env(level.registryAccess(), generator, source, state.randomState(),
				level.getStructureManager(), state.getLevelSeed(), level, level.dimension());
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 判定"该结构在这个候选区块会不会<b>真的</b>生成"。
	 *
	 * <p><b>偏移假设</b>：候选坐标来自 {@code StructurePlacement.getLocatePos(cp) =
	 * (cp.getMinBlockX(), 0, cp.getMinBlockZ()) + locateOffset}，而 {@code locateOffset} 由
	 * {@code Vec3i.offsetCodec(16)} 约束在 16 以内（原版数据里只有 {@code buried_treasure} 用了
	 * [9,0,9]），因此把方块坐标 {@code >> 4} 能唯一反推区块。若将来某数据包用了 ≥16 或负的偏移，
	 * 需改为在索引里直接存区块坐标（并把索引 {@code SCHEMA_VERSION} +1）。
	 *
	 * <p>异常时一律<b>放行</b>（返回 true）：宁可多给一个候选，也不误杀合法结果。
	 */
	public static boolean reallyGenerates(Env env, Holder<Structure> holder, int chunkX, int chunkZ) {
		try {
			Structure structure = holder.value();
			// 参数顺序严格对齐原版 Structure.generate(selected, dimension, registryAccess,
			// chunkGenerator, biomeSource, randomState, structureTemplateManager, seed,
			// sourceChunkPos, references, heightAccessor, validBiome)——见 loom 源码 jar。
			StructureStart start = structure.generate(
				holder,
				env.dimension(),
				env.registryAccess(),
				env.generator(),
				env.biomeSource(),
				env.randomState(),
				env.templateManager(),
				env.seed(),
				new net.minecraft.world.level.ChunkPos(chunkX, chunkZ),
				0,
				env.heightAccessor(),
				structure.biomes()::contains);
			return start != StructureStart.INVALID_START && start.isValid();
		} catch (Throwable t) {
			// 单个候选判定异常时放行，绝不因一个坏候选打断整批校验
			return true;
		}
	}
}
