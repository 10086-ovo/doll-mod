package com.example.doll;

import com.example.doll.block.RockAnvilBlock;
import com.example.doll.block.SculkShrineBlock;
import net.minecraft.network.chat.Component;
import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollRecallRegistry;
import com.example.doll.entity.DollVariant;
import com.example.doll.entity.PaleSacrificeHandler;
import com.example.doll.entity.WildWardenDollEntity;
import com.example.doll.item.DollBatonItem;
import com.example.doll.item.DollControlPanelItem;
import com.example.doll.item.DollSpawnEggItem;
import com.example.doll.item.WardenDollHeadItem;
import com.example.doll.network.DollNetworking;
import com.example.doll.recipe.DollUpgradeRecipe;
import com.example.doll.recipe.WardenDollSynthesisRecipe;
import com.example.doll.screen.DollScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DollMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	private static final Identifier DOLL_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_ENTITY_ID);
	private static final Identifier DOLL_TIER1_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_TIER1_EGG_ID);
	private static final Identifier DOLL_TIER2_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_TIER2_EGG_ID);
	private static final Identifier DOLL_TIER3_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_TIER3_EGG_ID);
	private static final Identifier DOLL_TIER4_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_TIER4_EGG_ID);
	private static final Identifier DOLL_TIER5_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_TIER5_EGG_ID);
	private static final Identifier WARDEN_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.WARDEN_DOLL_EGG_ID);
	private static final Identifier PALE_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.PALE_DOLL_EGG_ID);
	private static final Identifier NETHER_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETHER_DOLL_EGG_ID);
	private static final Identifier ENDER_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.ENDER_DOLL_EGG_ID);
	private static final Identifier SEA_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.SEA_DOLL_EGG_ID);
	private static final Identifier WARDEN_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.WARDEN_DOLL_HEAD_ID);
	private static final Identifier PALE_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.PALE_DOLL_HEAD_ID);
	private static final Identifier NETHER_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETHER_DOLL_HEAD_ID);
	private static final Identifier ENDER_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.ENDER_DOLL_HEAD_ID);
	private static final Identifier SEA_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.SEA_DOLL_HEAD_ID);
	private static final Identifier SCULK_SHRINE_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.SCULK_SHRINE_ID);
	private static final Identifier WARDEN_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.WARDEN_DOLL_HEAD_ENTITY_ID);
	private static final Identifier PALE_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.PALE_DOLL_HEAD_ENTITY_ID);
	private static final Identifier NETHER_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETHER_DOLL_HEAD_ENTITY_ID);
	private static final Identifier ENDER_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.ENDER_DOLL_HEAD_ENTITY_ID);
	private static final Identifier SEA_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.SEA_DOLL_HEAD_ENTITY_ID);
	private static final Identifier FOREST_DOLL_EGG_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.FOREST_DOLL_EGG_ID);
	private static final Identifier FOREST_DOLL_HEAD_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.FOREST_DOLL_HEAD_ID);
	private static final Identifier FOREST_DOLL_HEAD_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.FOREST_DOLL_HEAD_ENTITY_ID);
	private static final Identifier SCULK_SHRINE_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.SCULK_SHRINE_ENTITY_ID);
	private static final Identifier WARDEN_DOLL_ENTITY_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.WARDEN_DOLL_ENTITY_ID);
	private static final Identifier DOLL_SCREEN_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_SCREEN_HANDLER_ID);
	private static final Identifier DOLL_BATON_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_BATON_ID);
	private static final Identifier DOLL_CONTROL_PANEL_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DOLL_CONTROL_PANEL_ID);
	private static final Identifier ROCK_ANVIL_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.ROCK_ANVIL_ID);
	private static final Identifier CHIPPED_ROCK_ANVIL_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.CHIPPED_ROCK_ANVIL_ID);
	private static final Identifier DAMAGED_ROCK_ANVIL_KEY =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.DAMAGED_ROCK_ANVIL_ID);

	public static final EntityType<DollEntity> DOLL_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		DOLL_ENTITY_KEY,
		EntityType.Builder.of(DollEntity::new, MobCategory.MISC)
			.sized(0.6f, 1.8f)
			.clientTrackingRange(10)
			.updateInterval(3)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, DOLL_ENTITY_KEY))
	);

	public static final Item DOLL_TIER1_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_TIER1_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER1_EGG_KEY)), 0)
	);

	public static final Item DOLL_TIER2_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_TIER2_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER2_EGG_KEY)), 1)
	);

	public static final Item DOLL_TIER3_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_TIER3_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER3_EGG_KEY)), 2)
	);

	public static final Item DOLL_TIER4_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_TIER4_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER4_EGG_KEY)), 3)
	);

	public static final Item DOLL_TIER5_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_TIER5_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER5_EGG_KEY)), 4)
	);

	public static final Item WARDEN_DOLL_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		WARDEN_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, WARDEN_DOLL_EGG_KEY)), 5)
	);

	public static final Item PALE_DOLL_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		PALE_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, PALE_DOLL_EGG_KEY)), 0, DollVariant.PALE)
	);

	public static final Item NETHER_DOLL_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		NETHER_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, NETHER_DOLL_EGG_KEY)), 0, DollVariant.NETHER)
	);

	public static final Item ENDER_DOLL_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		ENDER_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, ENDER_DOLL_EGG_KEY)), 0, DollVariant.ENDER)
	);

	public static final Item SEA_DOLL_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		SEA_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, SEA_DOLL_EGG_KEY)), 0, DollVariant.SEA)
	);

	public static final Item FOREST_DOLL_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		FOREST_DOLL_EGG_KEY,
		new DollSpawnEggItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, FOREST_DOLL_EGG_KEY)), 0, DollVariant.FOREST)
	);

	// ---- 监守者人偶头颅方块 ----
	public static final com.example.doll.block.WardenDollHeadBlock WARDEN_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		WARDEN_DOLL_HEAD_KEY,
		new com.example.doll.block.WardenDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, WARDEN_DOLL_HEAD_KEY)))
	);

	public static final Item WARDEN_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		WARDEN_DOLL_HEAD_KEY,
		new WardenDollHeadItem(WARDEN_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, WARDEN_DOLL_HEAD_KEY)))
	);

	// ---- 苍白人偶头颅方块 ----
	public static final com.example.doll.block.PaleDollHeadBlock PALE_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		PALE_DOLL_HEAD_KEY,
		new com.example.doll.block.PaleDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, PALE_DOLL_HEAD_KEY)))
	);

	public static final Item PALE_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		PALE_DOLL_HEAD_KEY,
		new com.example.doll.item.PaleDollHeadItem(PALE_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, PALE_DOLL_HEAD_KEY)))
	);

	// ---- 下界人偶头颅方块 ----
	public static final com.example.doll.block.NetherDollHeadBlock NETHER_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		NETHER_DOLL_HEAD_KEY,
		new com.example.doll.block.NetherDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, NETHER_DOLL_HEAD_KEY)))
	);

	public static final Item NETHER_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		NETHER_DOLL_HEAD_KEY,
		new com.example.doll.item.NetherDollHeadItem(NETHER_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, NETHER_DOLL_HEAD_KEY)))
	);

	// ---- 末影人偶头颅方块 ----
	public static final com.example.doll.block.EnderDollHeadBlock ENDER_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		ENDER_DOLL_HEAD_KEY,
		new com.example.doll.block.EnderDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, ENDER_DOLL_HEAD_KEY)))
	);

	public static final Item ENDER_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		ENDER_DOLL_HEAD_KEY,
		new com.example.doll.item.EnderDollHeadItem(ENDER_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, ENDER_DOLL_HEAD_KEY)))
	);

	// ---- 海洋人偶头颅方块 ----
	public static final com.example.doll.block.SeaDollHeadBlock SEA_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		SEA_DOLL_HEAD_KEY,
		new com.example.doll.block.SeaDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, SEA_DOLL_HEAD_KEY)))
	);

	public static final Item SEA_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		SEA_DOLL_HEAD_KEY,
		new com.example.doll.item.SeaDollHeadItem(SEA_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, SEA_DOLL_HEAD_KEY)))
	);

	// ---- 森林人偶头颅方块 ----
	public static final com.example.doll.block.ForestDollHeadBlock FOREST_DOLL_HEAD_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		FOREST_DOLL_HEAD_KEY,
		new com.example.doll.block.ForestDollHeadBlock(BlockBehaviour.Properties.of()
			.strength(1.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, FOREST_DOLL_HEAD_KEY)))
	);

	public static final Item FOREST_DOLL_HEAD = Registry.register(
		BuiltInRegistries.ITEM,
		FOREST_DOLL_HEAD_KEY,
		new com.example.doll.item.ForestDollHeadItem(FOREST_DOLL_HEAD_BLOCK, new Item.Properties()
			.stacksTo(64)
			.setId(ResourceKey.create(Registries.ITEM, FOREST_DOLL_HEAD_KEY)))
	);

	public static final Item DOLL_BATON = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_BATON_KEY,
		new DollBatonItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_BATON_KEY)))
	);

	public static final Item DOLL_CONTROL_PANEL = Registry.register(
		BuiltInRegistries.ITEM,
		DOLL_CONTROL_PANEL_KEY,
		new DollControlPanelItem(new Item.Properties().stacksTo(1)
			.setId(ResourceKey.create(Registries.ITEM, DOLL_CONTROL_PANEL_KEY)))
	);

	/**
	 * 升级配方实例：合成时把源蛋的名字与携带数据（物品栏/作业区/盾构机配置）
	 * 原样搬到新蛋上，升级后无需重新赐名、不丢任何东西。
	 * 对应配方 JSON 的 type 依次为：upgrade_tier2 / upgrade_tier3 / upgrade_tier4 / upgrade_tier5。
	 */
	// 四周 8 格按 左上、上中、右上、左中、右中、左下、下中、右下 排列
	// 一阶→二阶：围一圈铜锭
	public static final DollUpgradeRecipe DOLL_UPGRADE_TIER2 = new DollUpgradeRecipe(
		DOLL_TIER1_EGG, DOLL_TIER2_EGG,
		Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT,
		Items.COPPER_INGOT,                   Items.COPPER_INGOT,
		Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT);
	// 二阶→三阶：十字铁锭 + 四角铜锭
	public static final DollUpgradeRecipe DOLL_UPGRADE_TIER3 = new DollUpgradeRecipe(
		DOLL_TIER2_EGG, DOLL_TIER3_EGG,
		Items.COPPER_INGOT,  Items.IRON_INGOT, Items.COPPER_INGOT,
		 Items.IRON_INGOT,                    Items.IRON_INGOT,
		Items.COPPER_INGOT,  Items.IRON_INGOT, Items.COPPER_INGOT);
	// 三阶→四阶：十字金锭 + 四角铁锭
	public static final DollUpgradeRecipe DOLL_UPGRADE_TIER4 = new DollUpgradeRecipe(
		DOLL_TIER3_EGG, DOLL_TIER4_EGG,
		Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_INGOT,
		Items.GOLD_INGOT,                   Items.GOLD_INGOT,
		Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_INGOT);
	// 四阶→五阶：上下钻石 + 左右金锭 + 四角铁锭
	public static final DollUpgradeRecipe DOLL_UPGRADE_TIER5 = new DollUpgradeRecipe(
		DOLL_TIER4_EGG, DOLL_TIER5_EGG,
		Items.IRON_INGOT,  Items.DIAMOND,    Items.IRON_INGOT,
		Items.GOLD_INGOT,                    Items.GOLD_INGOT,
		Items.IRON_INGOT,  Items.DIAMOND,    Items.IRON_INGOT);

	public static final WardenDollSynthesisRecipe WARDEN_DOLL_SYNTHESIS = new WardenDollSynthesisRecipe();

	// ---- 幽匿灵龛方块 ----
	public static final SculkShrineBlock SCULK_SHRINE_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		SCULK_SHRINE_KEY,
		new SculkShrineBlock(BlockBehaviour.Properties.of()
			.strength(3.0f, 3.0f)
			.sound(SoundType.SCULK)
			.requiresCorrectToolForDrops()
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, SCULK_SHRINE_KEY)))
	);

	public static final Item SCULK_SHRINE_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		SCULK_SHRINE_KEY,
		new BlockItem(SCULK_SHRINE_BLOCK, new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, SCULK_SHRINE_KEY)))
	);

	public static final BlockEntityType<com.example.doll.block.WardenDollHeadBlockEntity> WARDEN_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			WARDEN_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.WardenDollHeadBlockEntity::new,
				java.util.Set.of(WARDEN_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.PaleDollHeadBlockEntity> PALE_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			PALE_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.PaleDollHeadBlockEntity::new,
				java.util.Set.of(PALE_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.NetherDollHeadBlockEntity> NETHER_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			NETHER_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.NetherDollHeadBlockEntity::new,
				java.util.Set.of(NETHER_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.EnderDollHeadBlockEntity> ENDER_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ENDER_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.EnderDollHeadBlockEntity::new,
				java.util.Set.of(ENDER_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.SeaDollHeadBlockEntity> SEA_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			SEA_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.SeaDollHeadBlockEntity::new,
				java.util.Set.of(SEA_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.ForestDollHeadBlockEntity> FOREST_DOLL_HEAD_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			FOREST_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.ForestDollHeadBlockEntity::new,
				java.util.Set.of(FOREST_DOLL_HEAD_BLOCK)
			)
		);

	public static final BlockEntityType<com.example.doll.block.SculkShrineBlockEntity> SCULK_SHRINE_BLOCK_ENTITY =
		Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			SCULK_SHRINE_ENTITY_KEY,
			new BlockEntityType<>(
				com.example.doll.block.SculkShrineBlockEntity::new,
				java.util.Set.of(SCULK_SHRINE_BLOCK)
			)
		);

	// ---- 野生幽匿人偶实体（独立于 DollEntity 体系的敌对生物）----
	public static final EntityType<WildWardenDollEntity> WARDEN_DOLL_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		WARDEN_DOLL_ENTITY_KEY,
		EntityType.Builder.of(WildWardenDollEntity::new, MobCategory.MONSTER)
			.sized(0.6f, 1.8f)
			.clientTrackingRange(10)
			.updateInterval(3)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, WARDEN_DOLL_ENTITY_KEY))
	);

	public static final ExtendedMenuType<DollScreenHandler, Integer> DOLL_SCREEN_HANDLER = Registry.register(
		BuiltInRegistries.MENU,
		DOLL_SCREEN_KEY,
		new ExtendedMenuType<>(DollScreenHandler::create, ByteBufCodecs.VAR_INT)
	);

	public static final Component ROCK_ANVIL_CONTAINER_TITLE = Component.translatable("container.doll-mod.rock_anvil");

	// ---- 石砧（三级损伤各为独立方块） ----
	public static final RockAnvilBlock ROCK_ANVIL_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		ROCK_ANVIL_KEY,
		new RockAnvilBlock(BlockBehaviour.Properties.of()
			.strength(3.0f, 3.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, ROCK_ANVIL_KEY)))
	);

	public static final Item ROCK_ANVIL_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		ROCK_ANVIL_KEY,
		new BlockItem(ROCK_ANVIL_BLOCK, new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, ROCK_ANVIL_KEY)))
	);

	public static final RockAnvilBlock CHIPPED_ROCK_ANVIL_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		CHIPPED_ROCK_ANVIL_KEY,
		new RockAnvilBlock(BlockBehaviour.Properties.of()
			.strength(3.0f, 3.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, CHIPPED_ROCK_ANVIL_KEY)))
	);

	public static final Item CHIPPED_ROCK_ANVIL_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		CHIPPED_ROCK_ANVIL_KEY,
		new BlockItem(CHIPPED_ROCK_ANVIL_BLOCK, new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, CHIPPED_ROCK_ANVIL_KEY)))
	);

	public static final RockAnvilBlock DAMAGED_ROCK_ANVIL_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		DAMAGED_ROCK_ANVIL_KEY,
		new RockAnvilBlock(BlockBehaviour.Properties.of()
			.strength(3.0f, 3.0f)
			.noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, DAMAGED_ROCK_ANVIL_KEY)))
	);

	public static final Item DAMAGED_ROCK_ANVIL_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		DAMAGED_ROCK_ANVIL_KEY,
		new BlockItem(DAMAGED_ROCK_ANVIL_BLOCK, new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, DAMAGED_ROCK_ANVIL_KEY)))
	);

	@Override
	public void onInitialize() {
		LOGGER.info("DollMod initializing...");

		// 升级配方序列化器
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "upgrade_tier2"),
			DOLL_UPGRADE_TIER2.getSerializer());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "upgrade_tier3"),
			DOLL_UPGRADE_TIER3.getSerializer());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "upgrade_tier4"),
			DOLL_UPGRADE_TIER4.getSerializer());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "upgrade_tier5"),
			DOLL_UPGRADE_TIER5.getSerializer());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "warden_doll_synthesis"),
			WARDEN_DOLL_SYNTHESIS.getSerializer());

		FabricDefaultAttributeRegistry.register(DOLL_ENTITY, DollEntity.createDollAttributes());
		FabricDefaultAttributeRegistry.register(WARDEN_DOLL_ENTITY, WildWardenDollEntity.createWildDollAttributes());

		// 石砧损伤链
		RockAnvilBlock.nextVariant = CHIPPED_ROCK_ANVIL_BLOCK;
		CHIPPED_ROCK_ANVIL_BLOCK.nextVariant = DAMAGED_ROCK_ANVIL_BLOCK;
		DAMAGED_ROCK_ANVIL_BLOCK.nextVariant = null;
		CHIPPED_ROCK_ANVIL_BLOCK.prevVariant = ROCK_ANVIL_BLOCK;
		DAMAGED_ROCK_ANVIL_BLOCK.prevVariant = CHIPPED_ROCK_ANVIL_BLOCK;

		DollNetworking.register();
		DollCreativeTab.register();

		// 苍白人偶献祭处理器
		PaleSacrificeHandler.register();

		// 清空召回位置登记表
		ServerLifecycleEvents.SERVER_STARTED.register(server -> DollRecallRegistry.clear());
	}
}