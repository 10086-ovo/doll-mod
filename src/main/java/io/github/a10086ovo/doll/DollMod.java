package io.github.a10086ovo.doll;

import io.github.a10086ovo.doll.block.RockAnvilBlock;
import io.github.a10086ovo.doll.block.SculkShrineBlock;
import net.minecraft.network.chat.Component;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.DollRecallRegistry;
import io.github.a10086ovo.doll.entity.DollRecallService;
import io.github.a10086ovo.doll.entity.DollVariant;
import io.github.a10086ovo.doll.entity.PaleSacrificeHandler;
import io.github.a10086ovo.doll.loot.SeaArmorLootInjector;
import io.github.a10086ovo.doll.entity.NetherFlyingSwordEntity;
import io.github.a10086ovo.doll.entity.ThrownEnderAxe;
import io.github.a10086ovo.doll.entity.WildWardenDollEntity;
import io.github.a10086ovo.doll.item.DollBatonItem;
import io.github.a10086ovo.doll.item.DollControlPanelItem;
import io.github.a10086ovo.doll.item.DollSpawnEggItem;
import io.github.a10086ovo.doll.item.EnderAxeItem;
import io.github.a10086ovo.doll.item.PaleBowItem;
import io.github.a10086ovo.doll.item.WardenDollHeadItem;
import io.github.a10086ovo.doll.item.PaleDollHeadItem;
import io.github.a10086ovo.doll.item.NetherDollHeadItem;
import io.github.a10086ovo.doll.item.EnderDollHeadItem;
import io.github.a10086ovo.doll.item.SeaDollHeadItem;
import io.github.a10086ovo.doll.item.ForestDollHeadItem;
import io.github.a10086ovo.doll.item.GuideDollHeadItem;
import io.github.a10086ovo.doll.item.GuideBookItem;
import io.github.a10086ovo.doll.item.SeaArmorItem;
import io.github.a10086ovo.doll.network.DollNetworking;
import io.github.a10086ovo.doll.recipe.DollUpgradeRecipe;
import io.github.a10086ovo.doll.screen.DollScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import io.github.a10086ovo.doll.util.GuideBookGivenStore;
import io.github.a10086ovo.doll.item.NetherSwordItem;
import io.github.a10086ovo.doll.item.ThornsShieldItem;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import java.util.Map;
import io.github.a10086ovo.doll.config.DollConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(DollModConstants.MOD_ID)
@Mod.EventBusSubscriber(modid = DollModConstants.MOD_ID)
public class DollMod {

	public static final Logger LOGGER = LoggerFactory.getLogger(DollModConstants.MOD_ID);

	/** 辅助方法：用模组命名空间构造 Identifier，减少冗长的 fromNamespaceAndPath 调用。 */
	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, path);
	}

	private static final Identifier DOLL_ENTITY_KEY = id(DollModConstants.DOLL_ENTITY_ID);
	private static final Identifier DOLL_TIER1_EGG_KEY = id(DollModConstants.DOLL_TIER1_EGG_ID);
	private static final Identifier DOLL_TIER2_EGG_KEY = id(DollModConstants.DOLL_TIER2_EGG_ID);
	private static final Identifier DOLL_TIER3_EGG_KEY = id(DollModConstants.DOLL_TIER3_EGG_ID);
	private static final Identifier DOLL_TIER4_EGG_KEY = id(DollModConstants.DOLL_TIER4_EGG_ID);
	private static final Identifier DOLL_TIER5_EGG_KEY = id(DollModConstants.DOLL_TIER5_EGG_ID);
	private static final Identifier WARDEN_DOLL_EGG_KEY = id(DollModConstants.WARDEN_DOLL_EGG_ID);
	private static final Identifier PALE_DOLL_EGG_KEY = id(DollModConstants.PALE_DOLL_EGG_ID);
	private static final Identifier NETHER_DOLL_EGG_KEY = id(DollModConstants.NETHER_DOLL_EGG_ID);
	private static final Identifier ENDER_DOLL_EGG_KEY = id(DollModConstants.ENDER_DOLL_EGG_ID);
	private static final Identifier SEA_DOLL_EGG_KEY = id(DollModConstants.SEA_DOLL_EGG_ID);
	private static final Identifier WARDEN_DOLL_HEAD_KEY = id(DollModConstants.WARDEN_DOLL_HEAD_ID);
	private static final Identifier PALE_DOLL_HEAD_KEY = id(DollModConstants.PALE_DOLL_HEAD_ID);
	private static final Identifier NETHER_DOLL_HEAD_KEY = id(DollModConstants.NETHER_DOLL_HEAD_ID);
	private static final Identifier ENDER_DOLL_HEAD_KEY = id(DollModConstants.ENDER_DOLL_HEAD_ID);
	private static final Identifier SEA_DOLL_HEAD_KEY = id(DollModConstants.SEA_DOLL_HEAD_ID);
	private static final Identifier SCULK_SHRINE_KEY = id(DollModConstants.SCULK_SHRINE_ID);
	private static final Identifier WARDEN_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.WARDEN_DOLL_HEAD_ENTITY_ID);
	private static final Identifier PALE_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.PALE_DOLL_HEAD_ENTITY_ID);
	private static final Identifier NETHER_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.NETHER_DOLL_HEAD_ENTITY_ID);
	private static final Identifier ENDER_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.ENDER_DOLL_HEAD_ENTITY_ID);
	private static final Identifier SEA_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.SEA_DOLL_HEAD_ENTITY_ID);
	private static final Identifier FOREST_DOLL_EGG_KEY = id(DollModConstants.FOREST_DOLL_EGG_ID);
	private static final Identifier FOREST_DOLL_HEAD_KEY = id(DollModConstants.FOREST_DOLL_HEAD_ID);
	private static final Identifier FOREST_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.FOREST_DOLL_HEAD_ENTITY_ID);
	private static final Identifier GUIDE_DOLL_EGG_KEY = id(DollModConstants.GUIDE_DOLL_EGG_ID);
	private static final Identifier GUIDE_DOLL_HEAD_KEY = id(DollModConstants.GUIDE_DOLL_HEAD_ID);
	private static final Identifier GUIDE_DOLL_HEAD_ENTITY_KEY = id(DollModConstants.GUIDE_DOLL_HEAD_ENTITY_ID);
	private static final Identifier SCULK_SHRINE_ENTITY_KEY = id(DollModConstants.SCULK_SHRINE_ENTITY_ID);
	private static final Identifier WARDEN_DOLL_ENTITY_KEY = id(DollModConstants.WARDEN_DOLL_ENTITY_ID);
	private static final Identifier DOLL_SCREEN_KEY = id(DollModConstants.DOLL_SCREEN_HANDLER_ID);
	private static final Identifier DOLL_BATON_KEY = id(DollModConstants.DOLL_BATON_ID);
	private static final Identifier DOLL_CONTROL_PANEL_KEY = id(DollModConstants.DOLL_CONTROL_PANEL_ID);
	private static final Identifier ROCK_ANVIL_KEY = id(DollModConstants.ROCK_ANVIL_ID);
	private static final Identifier CHIPPED_ROCK_ANVIL_KEY = id(DollModConstants.CHIPPED_ROCK_ANVIL_ID);
	private static final Identifier DAMAGED_ROCK_ANVIL_KEY = id(DollModConstants.DAMAGED_ROCK_ANVIL_ID);
	private static final Identifier ENDER_AXE_KEY = id(DollModConstants.ENDER_AXE_ID);
	private static final Identifier THROWN_ENDER_AXE_ENTITY_KEY = id(DollModConstants.THROWN_ENDER_AXE_ID);
	private static final Identifier THORNS_SHIELD_KEY = id(DollModConstants.THORNS_SHIELD_ID);
	private static final Identifier NETHER_SWORD_KEY = id(DollModConstants.NETHER_SWORD_ID);
	private static final Identifier GUIDE_BOOK_KEY = id(DollModConstants.GUIDE_BOOK_ID);
	private static final Identifier SEA_HELMET_KEY = id(DollModConstants.SEA_HELMET_ID);
	private static final Identifier SEA_CHESTPLATE_KEY = id(DollModConstants.SEA_CHESTPLATE_ID);
	private static final Identifier SEA_LEGGINGS_KEY = id(DollModConstants.SEA_LEGGINGS_ID);
	private static final Identifier SEA_BOOTS_KEY = id(DollModConstants.SEA_BOOTS_ID);
	private static final Identifier PALE_BOW_KEY = id(DollModConstants.PALE_BOW_ID);

	public static EntityType<DollEntity> DOLL_ENTITY;
	public static EntityType<ThrownEnderAxe> THROWN_ENDER_AXE_ENTITY;
	public static EntityType<NetherFlyingSwordEntity> NETHER_FLYING_SWORD_ENTITY;

	// ---- 物品字段（在 onInitialize 中注册）----
	public static EnderAxeItem ENDER_AXE_ITEM;
	public static ThornsShieldItem THORNS_SHIELD_ITEM;
	public static NetherSwordItem NETHER_SWORD_ITEM;
	public static Item GUIDE_BOOK_ITEM;
	public static PaleBowItem PALE_BOW_ITEM;
	public static ArmorMaterial SEA_ARMOR_MATERIAL;
	public static TagKey<Item> SEA_ARMOR_REPAIR_TAG;
	public static TagKey<Item> ENDER_AXE_REPAIR_TAG;
	public static TagKey<Item> NETHER_SWORD_REPAIR_TAG;
	public static TagKey<Item> PALE_BOW_REPAIR_TAG;
	public static TagKey<Item> THORNS_SHIELD_REPAIR_TAG;
	public static SeaArmorItem SEA_HELMET;
	public static SeaArmorItem SEA_CHESTPLATE;
	public static SeaArmorItem SEA_LEGGINGS;
	public static SeaArmorItem SEA_BOOTS;
	public static Item DOLL_TIER1_EGG;
	public static Item DOLL_TIER2_EGG;
	public static Item DOLL_TIER3_EGG;
	public static Item DOLL_TIER4_EGG;
	public static Item DOLL_TIER5_EGG;
	public static Item WARDEN_DOLL_SPAWN_EGG;
	public static Item PALE_DOLL_EGG;
	public static Item NETHER_DOLL_EGG;
	public static Item ENDER_DOLL_EGG;
	public static Item SEA_DOLL_EGG;
	public static Item FOREST_DOLL_EGG;
	public static Item GUIDE_DOLL_EGG;
	public static Item WARDEN_DOLL_HEAD;
	public static Item PALE_DOLL_HEAD;
	public static Item NETHER_DOLL_HEAD;
	public static Item ENDER_DOLL_HEAD;
	public static Item SEA_DOLL_HEAD;
	public static Item FOREST_DOLL_HEAD;
	public static Item GUIDE_DOLL_HEAD;
	public static Item DOLL_BATON;
	public static Item DOLL_CONTROL_PANEL;
	public static Item SCULK_SHRINE_ITEM;
	public static Item ROCK_ANVIL_ITEM;
	public static Item CHIPPED_ROCK_ANVIL_ITEM;
	public static Item DAMAGED_ROCK_ANVIL_ITEM;

	// ---- 升级配方（在 onInitialize 中创建，依赖已注册的物品字段）----
	public static DollUpgradeRecipe DOLL_UPGRADE_TIER2;
	public static DollUpgradeRecipe DOLL_UPGRADE_TIER3;
	public static DollUpgradeRecipe DOLL_UPGRADE_TIER4;
	public static DollUpgradeRecipe DOLL_UPGRADE_TIER5;

	public static io.github.a10086ovo.doll.block.WardenDollHeadBlock WARDEN_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.PaleDollHeadBlock PALE_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.NetherDollHeadBlock NETHER_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.EnderDollHeadBlock ENDER_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.SeaDollHeadBlock SEA_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.ForestDollHeadBlock FOREST_DOLL_HEAD_BLOCK;
	public static io.github.a10086ovo.doll.block.GuideDollHeadBlock GUIDE_DOLL_HEAD_BLOCK;
	public static SculkShrineBlock SCULK_SHRINE_BLOCK;

	public static BlockEntityType<io.github.a10086ovo.doll.block.WardenDollHeadBlockEntity> WARDEN_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.PaleDollHeadBlockEntity> PALE_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.NetherDollHeadBlockEntity> NETHER_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.EnderDollHeadBlockEntity> ENDER_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.SeaDollHeadBlockEntity> SEA_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.ForestDollHeadBlockEntity> FOREST_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.GuideDollHeadBlockEntity> GUIDE_DOLL_HEAD_BLOCK_ENTITY;
	public static BlockEntityType<io.github.a10086ovo.doll.block.SculkShrineBlockEntity> SCULK_SHRINE_BLOCK_ENTITY;

	public static EntityType<WildWardenDollEntity> WARDEN_DOLL_ENTITY;

	public static net.minecraft.world.inventory.MenuType<DollScreenHandler> DOLL_SCREEN_HANDLER;

	public static final Component ROCK_ANVIL_CONTAINER_TITLE =
		Component.translatable("container.dollmod.rock_anvil");

	public static RockAnvilBlock ROCK_ANVIL_BLOCK;
	public static RockAnvilBlock CHIPPED_ROCK_ANVIL_BLOCK;
	public static RockAnvilBlock DAMAGED_ROCK_ANVIL_BLOCK;

	public DollMod() {
		// Forge EventBus 7 的 @Mod.EventBusSubscriber 不再需要把整类强行指定到某个总线：
		// 不写 bus 时，模组生命周期事件走模组总线，玩家登录/登出/命令等游戏事件走游戏总线。
	}

	// ==================== 注册（RegisterEvent 分派） ====================

	@SubscribeEvent
	public static void onRegister(net.minecraftforge.registries.RegisterEvent event) {
		ResourceKey<?> key = event.getRegistryKey();
		if (key.equals(Registries.ENTITY_TYPE)) {
			registerEntities(event);
		} else if (key.equals(Registries.BLOCK)) {
			registerBlocks(event);
		} else if (key.equals(Registries.BLOCK_ENTITY_TYPE)) {
			registerBlockEntities(event);
		} else if (key.equals(Registries.MENU)) {
			registerMenu(event);
		} else if (key.equals(Registries.CREATIVE_MODE_TAB)) {
			DollCreativeTab.register();
		} else if (key.equals(Registries.ITEM)) {
			registerItems(event);
		} else if (key.equals(Registries.RECIPE_SERIALIZER)) {
			registerRecipeSerializers(event);
		}
	}

	private static void registerEntities(net.minecraftforge.registries.RegisterEvent event) {
		DOLL_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			DOLL_ENTITY_KEY,
			EntityType.Builder.of(DollEntity::new, MobCategory.MISC)
				.sized(0.6f, 1.8f)
				.vehicleAttachment(new Vec3(0.0, 0.6, 0.0))
				.clientTrackingRange(10)
				.updateInterval(3)
				.build(ResourceKey.create(Registries.ENTITY_TYPE, DOLL_ENTITY_KEY))
		);

		THROWN_ENDER_AXE_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			THROWN_ENDER_AXE_ENTITY_KEY,
			EntityType.Builder.<ThrownEnderAxe>of(ThrownEnderAxe::new, MobCategory.MISC)
				.sized(0.5f, 0.5f)
				.clientTrackingRange(4)
				.updateInterval(20)
				.build(ResourceKey.create(Registries.ENTITY_TYPE, THROWN_ENDER_AXE_ENTITY_KEY))
		);

		NETHER_FLYING_SWORD_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			id(DollModConstants.NETHER_FLYING_SWORD_ID),
			EntityType.Builder.<NetherFlyingSwordEntity>of(NetherFlyingSwordEntity::new, MobCategory.MISC)
				.sized(0.5f, 0.5f)
				.clientTrackingRange(10)
				.updateInterval(1)
				.fireImmune()
				.build(ResourceKey.create(Registries.ENTITY_TYPE, id(DollModConstants.NETHER_FLYING_SWORD_ID)))
		);

		WARDEN_DOLL_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WARDEN_DOLL_ENTITY_KEY,
			EntityType.Builder.of(WildWardenDollEntity::new, MobCategory.MONSTER)
				.sized(0.6f, 1.8f)
				.clientTrackingRange(10)
				.updateInterval(3)
				.build(ResourceKey.create(Registries.ENTITY_TYPE, WARDEN_DOLL_ENTITY_KEY))
		);
	}

	private static void registerBlocks(net.minecraftforge.registries.RegisterEvent event) {
		WARDEN_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			WARDEN_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.WardenDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, WARDEN_DOLL_HEAD_KEY)))
		);
		PALE_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			PALE_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.PaleDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, PALE_DOLL_HEAD_KEY)))
		);
		NETHER_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			NETHER_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.NetherDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, NETHER_DOLL_HEAD_KEY)))
		);
		ENDER_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			ENDER_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.EnderDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, ENDER_DOLL_HEAD_KEY)))
		);
		SEA_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			SEA_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.SeaDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, SEA_DOLL_HEAD_KEY)))
		);
		FOREST_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			FOREST_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.ForestDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, FOREST_DOLL_HEAD_KEY)))
		);
		GUIDE_DOLL_HEAD_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			GUIDE_DOLL_HEAD_KEY,
			new io.github.a10086ovo.doll.block.GuideDollHeadBlock(BlockBehaviour.Properties.of()
				.strength(1.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, GUIDE_DOLL_HEAD_KEY)))
		);
		SCULK_SHRINE_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			SCULK_SHRINE_KEY,
			new SculkShrineBlock(BlockBehaviour.Properties.of()
				.strength(3.0f, 3.0f)
				.sound(SoundType.SCULK)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, SCULK_SHRINE_KEY)))
		);
		ROCK_ANVIL_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			ROCK_ANVIL_KEY,
			new RockAnvilBlock(BlockBehaviour.Properties.of()
				.strength(3.0f, 3.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, ROCK_ANVIL_KEY)))
		);
		CHIPPED_ROCK_ANVIL_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			CHIPPED_ROCK_ANVIL_KEY,
			new RockAnvilBlock(BlockBehaviour.Properties.of()
				.strength(3.0f, 3.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, CHIPPED_ROCK_ANVIL_KEY)))
		);
		DAMAGED_ROCK_ANVIL_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			DAMAGED_ROCK_ANVIL_KEY,
			new RockAnvilBlock(BlockBehaviour.Properties.of()
				.strength(3.0f, 3.0f)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, DAMAGED_ROCK_ANVIL_KEY)))
		);
		ROCK_ANVIL_BLOCK.nextVariant = CHIPPED_ROCK_ANVIL_BLOCK;
		CHIPPED_ROCK_ANVIL_BLOCK.nextVariant = DAMAGED_ROCK_ANVIL_BLOCK;
		DAMAGED_ROCK_ANVIL_BLOCK.nextVariant = null;
	}

	private static void registerBlockEntities(net.minecraftforge.registries.RegisterEvent event) {
		WARDEN_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			WARDEN_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.WardenDollHeadBlockEntity::new,
				java.util.Set.of(WARDEN_DOLL_HEAD_BLOCK)
			)
		);
		PALE_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			PALE_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.PaleDollHeadBlockEntity::new,
				java.util.Set.of(PALE_DOLL_HEAD_BLOCK)
			)
		);
		NETHER_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			NETHER_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.NetherDollHeadBlockEntity::new,
				java.util.Set.of(NETHER_DOLL_HEAD_BLOCK)
			)
		);
		ENDER_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ENDER_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.EnderDollHeadBlockEntity::new,
				java.util.Set.of(ENDER_DOLL_HEAD_BLOCK)
			)
		);
		SEA_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			SEA_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.SeaDollHeadBlockEntity::new,
				java.util.Set.of(SEA_DOLL_HEAD_BLOCK)
			)
		);
		FOREST_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			FOREST_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.ForestDollHeadBlockEntity::new,
				java.util.Set.of(FOREST_DOLL_HEAD_BLOCK)
			)
		);
		GUIDE_DOLL_HEAD_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			GUIDE_DOLL_HEAD_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.GuideDollHeadBlockEntity::new,
				java.util.Set.of(GUIDE_DOLL_HEAD_BLOCK)
			)
		);
		SCULK_SHRINE_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			SCULK_SHRINE_ENTITY_KEY,
			new BlockEntityType<>(
				io.github.a10086ovo.doll.block.SculkShrineBlockEntity::new,
				java.util.Set.of(SCULK_SHRINE_BLOCK)
			)
		);
	}

	private static void registerMenu(net.minecraftforge.registries.RegisterEvent event) {
		DOLL_SCREEN_HANDLER = Registry.register(
			BuiltInRegistries.MENU,
			DOLL_SCREEN_KEY,
			new net.minecraft.world.inventory.MenuType<>(
				(windowId, inv) -> new DollScreenHandler(windowId, inv,
					new io.github.a10086ovo.doll.inventory.DollInventory(null)),
				net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
		);
	}

	/** 注册所有物品、修复标签与海洋套装盔甲材质。 */
	private static void registerItems(net.minecraftforge.registries.RegisterEvent event) {
		ENDER_AXE_REPAIR_TAG = TagKey.create(Registries.ITEM, id("ender_axe_repair"));
		NETHER_SWORD_REPAIR_TAG = TagKey.create(Registries.ITEM, id("nether_sword_repair"));
		PALE_BOW_REPAIR_TAG = TagKey.create(Registries.ITEM, id("pale_bow_repair"));
		THORNS_SHIELD_REPAIR_TAG = TagKey.create(Registries.ITEM, id("thorns_shield_repair"));

		ENDER_AXE_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			ENDER_AXE_KEY,
			new EnderAxeItem(ToolMaterial.NETHERITE, 5.0f, -2.4f,
				new Item.Properties().durability(2031)
					.repairable(ENDER_AXE_REPAIR_TAG)
					.setId(ResourceKey.create(Registries.ITEM, ENDER_AXE_KEY)))
		);

		THORNS_SHIELD_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			THORNS_SHIELD_KEY,
			new ThornsShieldItem(new Item.Properties().durability(672)
				.repairable(THORNS_SHIELD_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, THORNS_SHIELD_KEY)))
		);

		NETHER_SWORD_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			NETHER_SWORD_KEY,
			new NetherSwordItem(ToolMaterial.NETHERITE, 3.0f, -2.0f,
				new Item.Properties()
					.repairable(NETHER_SWORD_REPAIR_TAG)
					.setId(ResourceKey.create(Registries.ITEM, NETHER_SWORD_KEY)))
		);

		GUIDE_BOOK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			GUIDE_BOOK_KEY,
			new GuideBookItem(new Item.Properties().stacksTo(1)
				.setId(ResourceKey.create(Registries.ITEM, GUIDE_BOOK_KEY)))
		);

		PALE_BOW_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			PALE_BOW_KEY,
			new PaleBowItem(new Item.Properties().durability(1536)
				.enchantable(1)
				.repairable(PALE_BOW_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, PALE_BOW_KEY)))
		);

		DOLL_BATON = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_BATON_KEY,
			new DollBatonItem(new Item.Properties().stacksTo(1)
				.setId(ResourceKey.create(Registries.ITEM, DOLL_BATON_KEY)))
		);

		DOLL_CONTROL_PANEL = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_CONTROL_PANEL_KEY,
			new DollControlPanelItem(new Item.Properties().stacksTo(1)
				.setId(ResourceKey.create(Registries.ITEM, DOLL_CONTROL_PANEL_KEY)))
		);

		DOLL_TIER1_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_TIER1_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER1_EGG_KEY)), 1)
		);
		DOLL_TIER2_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_TIER2_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER2_EGG_KEY)), 2)
		);
		DOLL_TIER3_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_TIER3_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER3_EGG_KEY)), 3)
		);
		DOLL_TIER4_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_TIER4_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER4_EGG_KEY)), 4)
		);
		DOLL_TIER5_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			DOLL_TIER5_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, DOLL_TIER5_EGG_KEY)), 5)
		);

		WARDEN_DOLL_SPAWN_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			WARDEN_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, WARDEN_DOLL_EGG_KEY)), 5, DollVariant.WARDEN)
		);
		PALE_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			PALE_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, PALE_DOLL_EGG_KEY)), 5, DollVariant.PALE)
		);
		NETHER_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			NETHER_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, NETHER_DOLL_EGG_KEY)), 5, DollVariant.NETHER)
		);
		ENDER_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			ENDER_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, ENDER_DOLL_EGG_KEY)), 5, DollVariant.ENDER)
		);
		SEA_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, SEA_DOLL_EGG_KEY)), 5, DollVariant.SEA)
		);
		FOREST_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			FOREST_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, FOREST_DOLL_EGG_KEY)), 5, DollVariant.FOREST)
		);
		GUIDE_DOLL_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			GUIDE_DOLL_EGG_KEY,
			new DollSpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, GUIDE_DOLL_EGG_KEY)), 5, DollVariant.GUIDE)
		);

		WARDEN_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			WARDEN_DOLL_HEAD_KEY,
			new WardenDollHeadItem(WARDEN_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, WARDEN_DOLL_HEAD_KEY)))
		);
		PALE_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			PALE_DOLL_HEAD_KEY,
			new PaleDollHeadItem(PALE_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, PALE_DOLL_HEAD_KEY)))
		);
		NETHER_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			NETHER_DOLL_HEAD_KEY,
			new NetherDollHeadItem(NETHER_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, NETHER_DOLL_HEAD_KEY)))
		);
		ENDER_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			ENDER_DOLL_HEAD_KEY,
			new EnderDollHeadItem(ENDER_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ENDER_DOLL_HEAD_KEY)))
		);
		SEA_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_DOLL_HEAD_KEY,
			new SeaDollHeadItem(SEA_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SEA_DOLL_HEAD_KEY)))
		);
		FOREST_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			FOREST_DOLL_HEAD_KEY,
			new ForestDollHeadItem(FOREST_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FOREST_DOLL_HEAD_KEY)))
		);
		GUIDE_DOLL_HEAD = Registry.register(
			BuiltInRegistries.ITEM,
			GUIDE_DOLL_HEAD_KEY,
			new GuideDollHeadItem(GUIDE_DOLL_HEAD_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, GUIDE_DOLL_HEAD_KEY)))
		);

		SCULK_SHRINE_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			SCULK_SHRINE_KEY,
			new BlockItem(SCULK_SHRINE_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SCULK_SHRINE_KEY)))
		);
		ROCK_ANVIL_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			ROCK_ANVIL_KEY,
			new BlockItem(ROCK_ANVIL_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ROCK_ANVIL_KEY)))
		);
		CHIPPED_ROCK_ANVIL_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			CHIPPED_ROCK_ANVIL_KEY,
			new BlockItem(CHIPPED_ROCK_ANVIL_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CHIPPED_ROCK_ANVIL_KEY)))
		);
		DAMAGED_ROCK_ANVIL_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			DAMAGED_ROCK_ANVIL_KEY,
			new BlockItem(DAMAGED_ROCK_ANVIL_BLOCK,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, DAMAGED_ROCK_ANVIL_KEY)))
		);

		SEA_ARMOR_REPAIR_TAG = TagKey.create(Registries.ITEM, id("sea_armor_repair"));
		SEA_ARMOR_MATERIAL = new ArmorMaterial(
			33,
			Map.of(
				ArmorType.HELMET, 3,
				ArmorType.CHESTPLATE, 8,
				ArmorType.LEGGINGS, 6,
				ArmorType.BOOTS, 3
			),
			10,
			SoundEvents.ARMOR_EQUIP_TURTLE,
			2.0f,
			0.0f,
			SEA_ARMOR_REPAIR_TAG,
			ResourceKey.create(EquipmentAssets.ROOT_ID, id("sea"))
		);

		SEA_HELMET = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_HELMET_KEY,
			new SeaArmorItem(new Item.Properties().humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.HELMET)
				.repairable(SEA_ARMOR_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, SEA_HELMET_KEY)))
		);
		SEA_CHESTPLATE = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_CHESTPLATE_KEY,
			new SeaArmorItem(new Item.Properties().humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.CHESTPLATE)
				.repairable(SEA_ARMOR_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, SEA_CHESTPLATE_KEY)))
		);
		SEA_LEGGINGS = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_LEGGINGS_KEY,
			new SeaArmorItem(new Item.Properties().humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.LEGGINGS)
				.repairable(SEA_ARMOR_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, SEA_LEGGINGS_KEY)))
		);
		SEA_BOOTS = Registry.register(
			BuiltInRegistries.ITEM,
			SEA_BOOTS_KEY,
			new SeaArmorItem(new Item.Properties().humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.BOOTS)
				.repairable(SEA_ARMOR_REPAIR_TAG)
				.setId(ResourceKey.create(Registries.ITEM, SEA_BOOTS_KEY)))
		);
	}

	private static void registerRecipeSerializers(net.minecraftforge.registries.RegisterEvent event) {
		DOLL_UPGRADE_TIER2 = new DollUpgradeRecipe(
			DOLL_TIER1_EGG, DOLL_TIER2_EGG,
			Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT,
			Items.COPPER_INGOT,                   Items.COPPER_INGOT,
			Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			id("upgrade_tier2"), DOLL_UPGRADE_TIER2.getSerializer());

		DOLL_UPGRADE_TIER3 = new DollUpgradeRecipe(
			DOLL_TIER2_EGG, DOLL_TIER3_EGG,
			Items.COPPER_INGOT,  Items.IRON_INGOT, Items.COPPER_INGOT,
			 Items.IRON_INGOT,                    Items.IRON_INGOT,
			Items.COPPER_INGOT,  Items.IRON_INGOT, Items.COPPER_INGOT);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			id("upgrade_tier3"), DOLL_UPGRADE_TIER3.getSerializer());

		DOLL_UPGRADE_TIER4 = new DollUpgradeRecipe(
			DOLL_TIER3_EGG, DOLL_TIER4_EGG,
			Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_INGOT,
			Items.GOLD_INGOT,                   Items.GOLD_INGOT,
			Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_INGOT);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			id("upgrade_tier4"), DOLL_UPGRADE_TIER4.getSerializer());

		DOLL_UPGRADE_TIER5 = new DollUpgradeRecipe(
			DOLL_TIER4_EGG, DOLL_TIER5_EGG,
			Items.IRON_INGOT,  Items.DIAMOND,    Items.IRON_INGOT,
			Items.GOLD_INGOT,                    Items.GOLD_INGOT,
			Items.IRON_INGOT,  Items.DIAMOND,    Items.IRON_INGOT);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			id("upgrade_tier5"), DOLL_UPGRADE_TIER5.getSerializer());
	}

	// ==================== 其它初始化 ====================

	@SubscribeEvent
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			boolean cfgLoaded;
			try {
				cfgLoaded = DollConfig.reload();
			} catch (Throwable t) {
				LOGGER.error("dollmod 配置初始化异常，回退默认值", t);
				cfgLoaded = false;
			}
			DollEntity.applyConfig();
			if (!cfgLoaded) {
				LOGGER.warn("dollmod 配置异常，本次以默认值运行（详见 config/dollmod/doll.json）。");
			}
		});

		// 网络与各类服务端处理器
		DollNetworking.init();
		DollNetworking.register();
		SeaArmorLootInjector.register();
		PaleSacrificeHandler.register();
		PaleBowInvisibilityHandler.register();
	}

	/** 注册实体的默认属性。 */
	@SubscribeEvent
	public static void onEntityAttributes(EntityAttributeCreationEvent event) {
		event.put(DOLL_ENTITY, DollEntity.createDollAttributes().build());
		event.put(WARDEN_DOLL_ENTITY, WildWardenDollEntity.createWildDollAttributes().build());
	}

	/** 服务器启动：清空跨会话内存态登记表，使每个新存档重新发放指南书。 */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		DollRecallRegistry.clear();
		DollEntity.clearPaleAuraCenters();
		DollEntity.clearNetherAuraCenters();
		DollRecallService.clearInFlight();
		GuideBookGivenStore.clear();
	}

	/** 玩家进入世界：赠送指南书（每个新存档一次）。 */
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		UUID id = player.getUUID();
		if (GuideBookGivenStore.has(id)) {
			return;
		}
		if (player.getInventory().countItem(GUIDE_BOOK_ITEM) > 0) {
			GuideBookGivenStore.set(id, true);
			return;
		}
		ItemStack book = new ItemStack(GUIDE_BOOK_ITEM);
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		GuideBookGivenStore.set(id, true);
		player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".guide_book_received"));
	}

	/** 玩家登出：潮汐护腿「落地水」澈簿，防水块簿泄漏。 */
	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			SeaArmorItem.drainPlayer(player);
		}
	}

	/** /dollmod reload 命令（OP 限权）。 */
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("dollmod")
				.requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
				.then(Commands.literal("reload")
					.executes(ctx -> {
						CommandSourceStack src = ctx.getSource();
						boolean ok;
						try {
							ok = DollConfig.reload();
						} catch (Throwable t) {
							LOGGER.error("dollmod reload 异常", t);
							ok = false;
						}
						if (ok) {
							DollEntity.applyConfig();
							src.sendSuccess(() ->
								Component.literal("§adollmod 配置已重载并应用（config/dollmod/doll.json），调参即时生效。"), false);
						} else {
							src.sendSuccess(() ->
								Component.literal("§cdollmod 配置读取失败，已回退默认值运行。请检查 config/dollmod/doll.json 是否合法 JSON，再 /dollmod reload 重试。"), false);
						}
						return 1;
					})));
	}
}
