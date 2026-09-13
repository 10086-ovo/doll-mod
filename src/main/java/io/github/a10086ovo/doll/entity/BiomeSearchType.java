package io.github.a10086ovo.doll.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * 向导人偶可搜索的生物群系定义。
 * <p>
 * 每种群系绑定：注册键（用于 {@link net.minecraft.server.level.ServerLevel} 群系查询）、
 * 所在维度、图标物品（GUI 展示）、翻译键。
 * <p>
 * 只搜索当前维度：GUI 仅显示与玩家所在维度匹配的群系。
 */
public enum BiomeSearchType {
	// --- 主世界 ---
	CHERRY_GROVE    (0, "cherry_grove",    Level.OVERWORLD, Items.CHERRY_SAPLING,   "biome.doll-mod.cherry_grove"),
	DESERT          (1, "desert",          Level.OVERWORLD, Items.CACTUS,           "biome.doll-mod.desert"),
	BAMBOO_JUNGLE   (2, "bamboo_jungle",   Level.OVERWORLD, Items.BAMBOO,           "biome.doll-mod.bamboo_jungle"),
	MUSHROOM_FIELDS (3, "mushroom_fields", Level.OVERWORLD, Items.RED_MUSHROOM,     "biome.doll-mod.mushroom_fields"),
	SNOWY_PLAINS    (4, "snowy_plains",    Level.OVERWORLD, Items.SNOW_BLOCK,       "biome.doll-mod.snowy_plains"),
	PLAINS          (5, "plains",          Level.OVERWORLD, Items.GRASS_BLOCK,      "biome.doll-mod.plains"),
	SWAMP           (6, "swamp",           Level.OVERWORLD, Items.LILY_PAD,         "biome.doll-mod.swamp"),
	LUSH_CAVES      (7, "lush_caves",      Level.OVERWORLD, Items.GLOW_BERRIES,     "biome.doll-mod.lush_caves"),
	PALE_GARDEN     (8, "pale_garden",     Level.OVERWORLD, Items.PALE_OAK_LEAVES,  "biome.doll-mod.pale_garden"),
	// --- 下界 ---
	NETHER_WASTES   (9,  "nether_wastes",   Level.NETHER, Items.NETHERRACK,      "biome.doll-mod.nether_wastes"),
	SOUL_SAND_VALLEY(10, "soul_sand_valley",Level.NETHER, Items.SOUL_SAND,       "biome.doll-mod.soul_sand_valley"),
	CRIMSON_FOREST  (11, "crimson_forest",  Level.NETHER, Items.CRIMSON_FUNGUS,  "biome.doll-mod.crimson_forest"),
	WARPED_FOREST   (12, "warped_forest",   Level.NETHER, Items.WARPED_FUNGUS,   "biome.doll-mod.warped_forest"),
	BASALT_DELTAS   (13, "basalt_deltas",   Level.NETHER, Items.BASALT,          "biome.doll-mod.basalt_deltas");

	private static final BiomeSearchType[] VALUES = values();

	private final int index;
	private final String biomeName;
	private final ResourceKey<Level> dimension;
	private final Item iconItem;
	private final String translationKey;

	BiomeSearchType(int index, String biomeName, ResourceKey<Level> dimension, Item iconItem, String translationKey) {
		this.index = index;
		this.biomeName = biomeName;
		this.dimension = dimension;
		this.iconItem = iconItem;
		this.translationKey = translationKey;
	}

	public int getIndex() {
		return index;
	}

	/** 群系注册键，如 "minecraft:cherry_grove" */
	public Identifier biomeKey() {
		return Identifier.withDefaultNamespace(biomeName);
	}

	public ResourceKey<Biome> biomeResourceKey() {
		return ResourceKey.create(Registries.BIOME, biomeKey());
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public ItemStack getIcon() {
		return new ItemStack(iconItem);
	}

	public String translationKey() {
		return translationKey;
	}

	public static BiomeSearchType byIndex(int index) {
		if (index < 0 || index >= VALUES.length) {
			return CHERRY_GROVE;
		}
		return VALUES[index];
	}

	/** 返回指定维度中可搜索的群系列表。 */
	public static List<BiomeSearchType> forDimension(ResourceKey<Level> dimension) {
		return java.util.Arrays.stream(VALUES)
			.filter(t -> t.dimension.equals(dimension))
			.toList();
	}
}
