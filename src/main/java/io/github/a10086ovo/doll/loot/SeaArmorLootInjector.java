package io.github.a10086ovo.doll.loot;

import io.github.a10086ovo.doll.DollModConstants;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 向原版埋藏宝藏 (minecraft:chests/buried_treasure) 注入海洋套装战利品。
 * <p>
 * 注入逻辑（完整定义在自定义战利品表 JSON 中）：
 * <ul>
 *   <li>91% 概率决定是否出现海洋套装（出现时必为 2~4 个不同部位：6 种两两组合 + 4 种三件 + 1 种四件 group 条目等概率）</li>
 *   <li>每件耐久度随机（池级 set_damage，均匀映射到 1 ~ 该部位最大耐久）</li>
 *   <li>每件独立 50% 概率附带随机附魔（原版 #minecraft:on_random_loot 标签，
 *       附魔种类与等级均按原版随机规则；其余情况为无附魔白板）</li>
 * </ul>
 * 本类仅负责向原版表追加一个 pool（含一个 reference entry 指向自定义表），
 * 实际概率与条目逻辑全部在 JSON 中定义。
 */
public class SeaArmorLootInjector {

	/** 自定义战利品表的注册键：doll-mod:chests/buried_treasure_sea_armor */
	private static final ResourceKey<LootTable> SEA_ARMOR_TABLE_KEY = ResourceKey.create(
		Registries.LOOT_TABLE,
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "chests/buried_treasure_sea_armor")
	);

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, holder) -> {
			// 仅修改原版内置的埋藏宝藏表，不干预其他来源
			if (!source.isBuiltin() || !key.equals(BuiltInLootTables.BURIED_TREASURE)) return;

			// 追加一个 pool：1 roll，无额外条件，仅包含一个 reference entry
			// 91% 概率门控、group/alternatives 逻辑全部在引用的自定义表中定义
			LootPool.Builder pool = LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0f))
				.add(NestedLootTable.lootTableReference(SEA_ARMOR_TABLE_KEY));

			tableBuilder.withPool(pool);
		});
	}
}
