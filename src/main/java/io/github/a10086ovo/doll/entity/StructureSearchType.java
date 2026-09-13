package io.github.a10086ovo.doll.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

/**
 * 向导人偶可搜索的 13 种结构定义。
 * <p>
 * 每种结构绑定：注册键（用于 {@link net.minecraft.server.level.ServerLevel#findNearestMapStructure}）、
 * 所在维度、图标物品（GUI 展示）、翻译键。
 * <p>
 * 只搜索当前维度：GUI 仅显示与玩家所在维度匹配的结构。
 */
public enum StructureSearchType {
	MONSTER_ROOM (0, "monster_room",      Level.OVERWORLD, Items.MOSSY_COBBLESTONE,    "structure.doll-mod.monster_room"),
	JUNGLE_TEMPLE (1, "jungle_temple",    Level.OVERWORLD, Items.MOSSY_STONE_BRICKS,   "structure.doll-mod.jungle_temple"),
	MONUMENT      (2, "monument",         Level.OVERWORLD, Items.PRISMARINE,           "structure.doll-mod.monument"),
	ANCIENT_CITY  (3, "ancient_city",    Level.OVERWORLD, Items.SCULK_CATALYST,       "structure.doll-mod.ancient_city"),
	MANSION       (4, "mansion",          Level.OVERWORLD, Items.DARK_OAK_LOG,         "structure.doll-mod.mansion"),
	TRIAL_CHAMBERS(5, "trial_chambers",  Level.OVERWORLD, Items.TRIAL_KEY,            "structure.doll-mod.trial_chambers"),
	MINESHAFT     (6, "mineshaft",        Level.OVERWORLD, Items.RAIL,                 "structure.doll-mod.mineshaft"),
	STRONGHOLD    (7, "stronghold",       Level.OVERWORLD, Items.END_PORTAL_FRAME,    "structure.doll-mod.stronghold"),
	BURIED_TREASURE(8,"buried_treasure",  Level.OVERWORLD, Items.HEART_OF_THE_SEA,    "structure.doll-mod.buried_treasure"),
	SHIPWRECK     (9, "shipwreck",        Level.OVERWORLD, Items.OAK_BOAT,             "structure.doll-mod.shipwreck"),
	FORTRESS      (10,"fortress",         Level.NETHER,    Items.NETHER_BRICKS,        "structure.doll-mod.fortress"),
	BASTION       (11,"bastion_remnant",  Level.NETHER,    Items.GILDED_BLACKSTONE,    "structure.doll-mod.bastion_remnant"),
	END_CITY      (12,"end_city",         Level.END,       Items.PURPUR_BLOCK,         "structure.doll-mod.end_city");

	private static final StructureSearchType[] VALUES = values();

	private final int index;
	private final String structureName;
	private final ResourceKey<Level> dimension;
	private final Item iconItem;
	private final String translationKey;

	StructureSearchType(int index, String structureName, ResourceKey<Level> dimension, Item iconItem, String translationKey) {
		this.index = index;
		this.structureName = structureName;
		this.dimension = dimension;
		this.iconItem = iconItem;
		this.translationKey = translationKey;
	}

	public int getIndex() {
		return index;
	}

	/** 结构注册键，如 "minecraft:monster_room" */
	public Identifier structureKey() {
		return Identifier.withDefaultNamespace(structureName);
	}

	public ResourceKey<Structure> structureResourceKey() {
		return ResourceKey.create(Registries.STRUCTURE, structureKey());
	}

	/** 结构标签键，用于 ServerLevel.findNearestMapStructure 搜索。 */
	public TagKey<Structure> tagKey() {
		return TagKey.create(Registries.STRUCTURE, structureKey());
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

	public static StructureSearchType byIndex(int index) {
		if (index < 0 || index >= VALUES.length) {
			return MONSTER_ROOM;
		}
		return VALUES[index];
	}

	/** 返回指定维度中可搜索的结构列表。 */
	public static List<StructureSearchType> forDimension(ResourceKey<Level> dimension) {
		return java.util.Arrays.stream(VALUES)
			.filter(t -> t.dimension.equals(dimension))
			.toList();
	}
}
