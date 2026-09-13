package io.github.a10086ovo.doll.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 向导人偶可搜索的村庄类型定义。
 * <p>
 * 26.2 起村庄已拆分为 5 个独立结构（{@code minecraft:village_plains} 等），
 * 每个结构自带 {@code biomes} 群系限定，与生成群系一一对应。
 * 直接按结构注册键搜索即可，无需再做群系过滤。
 */
public enum VillageSearchType {
	PLAINS (0, "village_plains",  Items.HAY_BLOCK,  "village.doll-mod.plains"),
	DESERT (1, "village_desert",  Items.SAND,       "village.doll-mod.desert"),
	SAVANNA(2, "village_savanna", Items.ACACIA_LOG, "village.doll-mod.savanna"),
	SNOWY  (3, "village_snowy",   Items.SNOW_BLOCK, "village.doll-mod.snowy"),
	TAIGA  (4, "village_taiga",   Items.SPRUCE_LOG, "village.doll-mod.taiga");

	private static final VillageSearchType[] VALUES = values();

	private final int index;
	private final String structureName;
	private final Item iconItem;
	private final String translationKey;

	VillageSearchType(int index, String structureName, Item iconItem, String translationKey) {
		this.index = index;
		this.structureName = structureName;
		this.iconItem = iconItem;
		this.translationKey = translationKey;
	}

	public int getIndex() {
		return index;
	}

	/** 结构注册键，如 "minecraft:village_plains" */
	public Identifier structureKey() {
		return Identifier.withDefaultNamespace(structureName);
	}

	public ResourceKey<Structure> structureResourceKey() {
		return ResourceKey.create(Registries.STRUCTURE, structureKey());
	}

	public ItemStack getIcon() {
		return new ItemStack(iconItem);
	}

	public String translationKey() {
		return translationKey;
	}

	public static VillageSearchType byIndex(int index) {
		if (index < 0 || index >= VALUES.length) {
			return PLAINS;
		}
		return VALUES[index];
	}

	/** 返回全部可搜索的村庄类型（村庄仅生成于主世界，无维度过滤）。 */
	public static java.util.List<VillageSearchType> all() {
		return java.util.List.of(VALUES);
	}
}
