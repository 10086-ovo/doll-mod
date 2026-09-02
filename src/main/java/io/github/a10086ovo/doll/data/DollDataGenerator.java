package io.github.a10086ovo.doll.data;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.mode.DollMode;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

import static io.github.a10086ovo.doll.DollModConstants.DOLL_BATON_ID;
import static io.github.a10086ovo.doll.DollModConstants.DOLL_CONTROL_PANEL_ID;
import static io.github.a10086ovo.doll.DollModConstants.DOLL_TIER1_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.ENDER_AXE_ID;
import static io.github.a10086ovo.doll.DollModConstants.ENDER_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.ENDER_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.FOREST_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.FOREST_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.GUIDE_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.GUIDE_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.MOD_ID;
import static io.github.a10086ovo.doll.DollModConstants.NETHER_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.NETHER_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.NETHER_SWORD_ID;
import static io.github.a10086ovo.doll.DollModConstants.PALE_BOW_ID;
import static io.github.a10086ovo.doll.DollModConstants.PALE_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.PALE_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.ROCK_ANVIL_ID;
import static io.github.a10086ovo.doll.DollModConstants.SCULK_SHRINE_ID;
import static io.github.a10086ovo.doll.DollModConstants.SEA_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.SEA_DOLL_HEAD_ID;
import static io.github.a10086ovo.doll.DollModConstants.THORNS_SHIELD_ID;
import static io.github.a10086ovo.doll.DollModConstants.WARDEN_DOLL_EGG_ID;
import static io.github.a10086ovo.doll.DollModConstants.WARDEN_DOLL_HEAD_ID;

/**
 * 数据生成器：刷怪蛋合成配方 + 简体中文/英文语言。模型/贴图等静态资源
 * 直接放在 resources 目录，不通过 datagen 生成。
 */
public class DollDataGenerator implements DataGeneratorEntrypoint {

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(id(path)).get().value();
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(id(path)).get().value();
	}

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
		pack.addProvider(DollRecipeProvider::new);
		pack.addProvider(DollLangProvider::new);
		pack.addProvider(DollEnUsProvider::new);
	}

	private static class DollRecipeProvider extends FabricRecipeProvider {

		public DollRecipeProvider(FabricPackOutput output,
				CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		public RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup,
				RecipeOutput output) {
			return new RecipeProvider(registryLookup, output) {
		@Override
		public void buildRecipes() {
			// 人偶指挥棒：右上角到左下角对角线三根木棍（法杖造型）
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(DOLL_BATON_ID))
				.pattern("  S")
				.pattern(" S ")
				.pattern("S  ")
				.define('S', Items.STICK)
				.unlockedBy("has_stick", has(Items.STICK))
                    .save(output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(MOD_ID, DOLL_BATON_ID)));

			// 人偶遥控器：铁 + 红石 + 铁（控制面板造型）
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(DOLL_CONTROL_PANEL_ID))
				.pattern("I")
				.pattern("R")
				.pattern("I")
				.define('I', Items.IRON_INGOT)
				.define('R', Items.REDSTONE)
				.unlockedBy("has_redstone", has(Items.REDSTONE))
                    .save(output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(MOD_ID, DOLL_CONTROL_PANEL_ID)));

			// 一阶蛋：上下左右四个工作台 + 中间箱子（十字形）
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
							RecipeCategory.MISC, item(DOLL_TIER1_EGG_ID))
						.pattern(" W ")
						.pattern("WCW")
						.pattern(" W ")
						.define('C', Items.CHEST)
						.define('W', Items.CRAFTING_TABLE)
						.unlockedBy("has_chest", has(Items.CHEST))
                    .save(output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(MOD_ID, DOLL_TIER1_EGG_ID)));

				// 幽匿人偶蛋：8 幽匿块围一圈，中间幽匿人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(WARDEN_DOLL_EGG_ID))
				.pattern("SSS")
				.pattern("SHS")
				.pattern("SSS")
				.define('S', Items.SCULK)
				.define('H', block(WARDEN_DOLL_HEAD_ID))
				.unlockedBy("has_warden_doll_head", has(block(WARDEN_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.WARDEN_DOLL_EGG_ID)));

				// 幽匿灵龛：8 深板岩石砖围 1 灵魂沙
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.DECORATIONS, block(SCULK_SHRINE_ID))
				.pattern("DDD")
				.pattern("DSD")
				.pattern("DDD")
				.define('D', Items.DEEPSLATE_BRICKS)
				.define('S', Items.SOUL_SAND)
				.unlockedBy("has_deepslate_bricks", has(Items.DEEPSLATE_BRICKS))
                .save(output, ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(MOD_ID, SCULK_SHRINE_ID)));

			// 苍白人偶头颅：4 树脂砖 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(PALE_DOLL_HEAD_ID))
				.pattern("RR")
				.pattern("RR")
				.define('R', Items.RESIN_BRICK)
				.unlockedBy("has_resin_brick", has(Items.RESIN_BRICK))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.PALE_DOLL_HEAD_ID)));

			// 苍白人偶蛋：8 苍白橡木原木围一圈，中间苍白人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(PALE_DOLL_EGG_ID))
				.pattern("PPP")
				.pattern("PHP")
				.pattern("PPP")
				.define('P', Items.PALE_OAK_LOG)
				.define('H', block(PALE_DOLL_HEAD_ID))
				.unlockedBy("has_pale_doll_head", has(block(PALE_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, PALE_DOLL_EGG_ID)));

			// 下界人偶头颅：4 石英块 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(NETHER_DOLL_HEAD_ID))
				.pattern("QQ")
				.pattern("QQ")
				.define('Q', Items.QUARTZ_BLOCK)
				.unlockedBy("has_quartz_block", has(Items.QUARTZ_BLOCK))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.NETHER_DOLL_HEAD_ID)));

			// 下界人偶蛋：8 下界岩围一圈，中间下界人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(NETHER_DOLL_EGG_ID))
				.pattern("NNN")
				.pattern("NHN")
				.pattern("NNN")
				.define('N', Items.NETHERRACK)
				.define('H', block(NETHER_DOLL_HEAD_ID))
				.unlockedBy("has_nether_doll_head", has(block(NETHER_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.NETHER_DOLL_EGG_ID)));

			// 末影人偶头颅：4 紫珀块 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(ENDER_DOLL_HEAD_ID))
				.pattern("PP")
				.pattern("PP")
				.define('P', Items.PURPUR_BLOCK)
				.unlockedBy("has_purpur_block", has(Items.PURPUR_BLOCK))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.ENDER_DOLL_HEAD_ID)));

			// 末影人偶蛋：8 末影石围一圈，中间末影人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(ENDER_DOLL_EGG_ID))
				.pattern("EEE")
				.pattern("EHE")
				.pattern("EEE")
				.define('E', Items.END_STONE)
				.define('H', block(ENDER_DOLL_HEAD_ID))
				.unlockedBy("has_ender_doll_head", has(block(ENDER_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.ENDER_DOLL_EGG_ID)));

			// 海洋人偶头颅：4 海晶石 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(SEA_DOLL_HEAD_ID))
				.pattern("PP")
				.pattern("PP")
				.define('P', Items.PRISMARINE)
				.unlockedBy("has_prismarine", has(Items.PRISMARINE))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.SEA_DOLL_HEAD_ID)));

			// 海洋人偶蛋：8 海带围一圈，中间海洋人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(SEA_DOLL_EGG_ID))
				.pattern("KKK")
				.pattern("KHK")
				.pattern("KKK")
				.define('K', Items.KELP)
				.define('H', block(SEA_DOLL_HEAD_ID))
				.unlockedBy("has_sea_doll_head", has(block(SEA_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.SEA_DOLL_EGG_ID)));

			// 海洋套装无合成配方——仅通过埋藏宝藏战利品获取（见 SeaArmorLootInjector）

			// 森林人偶头颅：4 苔石 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(FOREST_DOLL_HEAD_ID))
				.pattern("MM")
				.pattern("MM")
				.define('M', Items.MOSSY_COBBLESTONE)
				.unlockedBy("has_mossy_cobblestone", has(Items.MOSSY_COBBLESTONE))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.FOREST_DOLL_HEAD_ID)));

			// 森林人偶蛋：8 橡木原木围一圈，中间森林人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(FOREST_DOLL_EGG_ID))
				.pattern("OOO")
				.pattern("OHO")
				.pattern("OOO")
				.define('O', Items.OAK_LOG)
				.define('H', block(FOREST_DOLL_HEAD_ID))
				.unlockedBy("has_forest_doll_head", has(block(FOREST_DOLL_HEAD_ID)))
			.save(output, ResourceKey.create(Registries.RECIPE,
				Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.FOREST_DOLL_EGG_ID)));

			// 向导人偶头颅：4 指南针 2×2 合成
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, block(GUIDE_DOLL_HEAD_ID))
				.pattern("CC")
				.pattern("CC")
				.define('C', Items.COMPASS)
				.unlockedBy("has_compass", has(Items.COMPASS))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.GUIDE_DOLL_HEAD_ID)));

			// 向导人偶蛋：8 纸围一圈，中间向导人偶头颅
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.MISC, item(GUIDE_DOLL_EGG_ID))
				.pattern("PPP")
				.pattern("PHP")
				.pattern("PPP")
				.define('P', Items.PAPER)
				.define('H', block(GUIDE_DOLL_HEAD_ID))
				.unlockedBy("has_guide_doll_head", has(block(GUIDE_DOLL_HEAD_ID)))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.GUIDE_DOLL_EGG_ID)));

			// 石砧：三圆石 + 六圆石半砖
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.DECORATIONS, block(ROCK_ANVIL_ID))
				.pattern("CCC")
				.pattern(" S ")
				.pattern("SSS")
				.define('C', Items.COBBLESTONE)
				.define('S', Items.COBBLESTONE_SLAB)
				.unlockedBy("has_cobblestone", has(Items.COBBLESTONE))
                .save(output, ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(MOD_ID, ROCK_ANVIL_ID)));

			// 进阶/附魔蛋升级配方不是普通合成配方：原版合成会丢蛋的 NBT
				// （名字、回收携带的物品栏/作业区/盾构机配置）。它们改为特殊配方，
				// 手写在 src/main/resources/data/doll-mod/recipe/ 下（合成时搬运 NBT），
				// 不由 datagen 生成，见 DollUpgradeRecipe。

			// 末影斧：第一列两个金锭，第二列一个末影珍珠，下面两根木棍
			//   G E
			//   G S
			//     S
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(ENDER_AXE_ID))
				.pattern("GE")
				.pattern("GS")
				.pattern(" S")
				.define('G', Items.GOLD_INGOT)
				.define('E', Items.ENDER_PEARL)
				.define('S', Items.STICK)
				.unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
			.save(output, ResourceKey.create(Registries.RECIPE,
				Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.ENDER_AXE_ID)));

			// 荆棘盾牌：原木环绕 + 中央铁块（森林主题防御装备）
			//   第一行：任意原木 / 铁块 / 任意原木
			//   第二行：任意原木 / 任意原木 / 任意原木
			//   第三行：空 / 任意原木 / 空
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(THORNS_SHIELD_ID))
				.pattern("LIL")
				.pattern("LLL")
				.pattern(" L ")
				.define('L', ItemTags.LOGS)
				.define('I', Items.IRON_BLOCK)
				.unlockedBy("has_iron_block", has(Items.IRON_BLOCK))
				.save(output, ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.THORNS_SHIELD_ID)));

			// 地狱剑：中间一列竖排——烈焰棒×2 + 木棍×1（下界主题近战武器，
			// 数值对标下界合金剑）
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(NETHER_SWORD_ID))
				.pattern(" N ")
				.pattern(" N ")
				.pattern(" S ")
				.define('N', Items.BLAZE_ROD)
				.define('S', Items.STICK)
				.unlockedBy("has_blaze_rod", has(Items.BLAZE_ROD))
			.save(output, ResourceKey.create(Registries.RECIPE,
				Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.NETHER_SWORD_ID)));

			// 苍白弓：3 树脂砖 + 3 线，锯齿形布局与原版弓一致（树脂砖替代木棍位置）
			ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM),
					RecipeCategory.TOOLS, item(PALE_BOW_ID))
				.pattern(" RS")
				.pattern("R S")
				.pattern(" RS")
				.define('R', Items.RESIN_BRICK)
				.define('S', Items.STRING)
				.unlockedBy("has_resin_brick", has(Items.RESIN_BRICK))
			.save(output, ResourceKey.create(Registries.RECIPE,
				Identifier.fromNamespaceAndPath(MOD_ID, DollModConstants.PALE_BOW_ID)));
			}
			};
		}

		@Override
		public String getName() {
			return MOD_ID + ":recipes";
		}
	}

	/**
	 * 语言条目总表：zh_cn / en_us 两个 provider 共用。
	 * 所有键必须两边同时维护，保持键集合一致（en 缺失会在英文客户端显示原始 key）。
	 */
	private static void addAllEntries(FabricLanguageProvider.TranslationBuilder b, boolean en) {
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_TIER1_EGG_ID, en ? "Tier 1 Doll Spawn Egg" : "一阶人偶刷怪蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_TIER2_EGG_ID, en ? "Tier 2 Doll Spawn Egg" : "二阶人偶刷怪蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_TIER3_EGG_ID, en ? "Tier 3 Doll Spawn Egg" : "三阶人偶刷怪蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_TIER4_EGG_ID, en ? "Tier 4 Doll Spawn Egg" : "四阶人偶刷怪蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_TIER5_EGG_ID, en ? "Tier 5 Doll Spawn Egg" : "五阶人偶刷怪蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.WARDEN_DOLL_EGG_ID, en ? "Sculk Doll Spawn Egg" : "幽匿人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.PALE_DOLL_EGG_ID, en ? "Pale Doll Spawn Egg" : "苍白人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.NETHER_DOLL_EGG_ID, en ? "Nether Doll Spawn Egg" : "下界人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.ENDER_DOLL_EGG_ID, en ? "Ender Doll Spawn Egg" : "末影人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_DOLL_EGG_ID, en ? "Sea Doll Spawn Egg" : "海洋人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.FOREST_DOLL_EGG_ID, en ? "Forest Doll Spawn Egg" : "森林人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.PALE_DOLL_HEAD_ID, en ? "Pale Doll Head" : "苍白人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.PALE_DOLL_HEAD_ID, en ? "Pale Doll Head" : "苍白人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.WARDEN_DOLL_HEAD_ID, en ? "Sculk Doll Head" : "幽匿人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.WARDEN_DOLL_HEAD_ID, en ? "Sculk Doll Head" : "幽匿人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.NETHER_DOLL_HEAD_ID, en ? "Nether Doll Head" : "下界人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.NETHER_DOLL_HEAD_ID, en ? "Nether Doll Head" : "下界人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.ENDER_DOLL_HEAD_ID, en ? "Ender Doll Head" : "末影人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.ENDER_DOLL_HEAD_ID, en ? "Ender Doll Head" : "末影人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_DOLL_HEAD_ID, en ? "Sea Doll Head" : "海洋人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.SEA_DOLL_HEAD_ID, en ? "Sea Doll Head" : "海洋人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_HELMET_ID, en ? "Current Helmet" : "洋流头盔");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_CHESTPLATE_ID, en ? "Abyssal Chestplate" : "深海胸甲");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_LEGGINGS_ID, en ? "Tidal Leggings" : "潮汐护腿");
		b.add("item." + MOD_ID + "." + DollModConstants.SEA_BOOTS_ID, en ? "Spray Boots" : "浪花靴子");
		b.add("item." + MOD_ID + "." + DollModConstants.FOREST_DOLL_HEAD_ID, en ? "Forest Doll Head" : "森林人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.FOREST_DOLL_HEAD_ID, en ? "Forest Doll Head" : "森林人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.GUIDE_DOLL_EGG_ID, en ? "Guide Doll Spawn Egg" : "向导人偶蛋");
		b.add("item." + MOD_ID + "." + DollModConstants.GUIDE_DOLL_HEAD_ID, en ? "Guide Doll Head" : "向导人偶头颅");
		b.add("block." + MOD_ID + "." + DollModConstants.GUIDE_DOLL_HEAD_ID, en ? "Guide Doll Head" : "向导人偶头颅");
		b.add("item." + MOD_ID + "." + DollModConstants.SCULK_SHRINE_ID, en ? "Sculk Shrine" : "幽匿灵龛");
		b.add("item." + MOD_ID + "." + DollModConstants.ROCK_ANVIL_ID, en ? "Rock Anvil" : "石砧");
		b.add("item." + MOD_ID + "." + DollModConstants.CHIPPED_ROCK_ANVIL_ID, en ? "Chipped Rock Anvil" : "微裂石砧");
		b.add("item." + MOD_ID + "." + DollModConstants.DAMAGED_ROCK_ANVIL_ID, en ? "Damaged Rock Anvil" : "大裂石砧");
		b.add("item." + MOD_ID + "." + DollModConstants.ENDER_AXE_ID, en ? "Ender Axe" : "末影斧");
		b.add("entity." + MOD_ID + "." + DollModConstants.THROWN_ENDER_AXE_ID, en ? "Thrown Ender Axe" : "投掷末影斧");
		b.add("item." + MOD_ID + "." + DollModConstants.GUIDE_BOOK_ID, en ? "Doll Guide Book" : "人偶指南书");
		b.add("item." + MOD_ID + "." + DollModConstants.THORNS_SHIELD_ID, en ? "Thorns Shield" : "荆棘盾牌");
		b.add("item." + MOD_ID + "." + DollModConstants.NETHER_SWORD_ID, en ? "Nether Sword" : "地狱剑");
		b.add("item." + MOD_ID + "." + DollModConstants.PALE_BOW_ID, en ? "Pale Bow" : "苍白弓");
		b.add("container.doll-mod.rock_anvil", en ? "Rock Anvil" : "石砧");
		b.add("item." + MOD_ID + "." + DollModConstants.DOLL_BATON_ID, en ? "Doll Baton" : "人偶指挥棒");
		b.add("item." + MOD_ID + "." + DOLL_CONTROL_PANEL_ID,
			en ? "Doll Remote Controller" : "人偶遥控器");
		b.add("entity." + MOD_ID + "." + DollModConstants.DOLL_ENTITY_ID, en ? "Doll" : "人偶");
		b.add("entity." + MOD_ID + "." + DollModConstants.WARDEN_DOLL_ENTITY_ID, en ? "Wild Sculk Doll" : "野生幽匿人偶");
		b.add("itemGroup." + MOD_ID + ".doll_tab", en ? "Dolls" : "人偶");
		b.add("container." + MOD_ID + ".doll_inventory", en ? "Doll Inventory" : "人偶物品栏");
		b.add("container." + MOD_ID + ".control_panel", en ? "Doll Control Panel" : "人偶控制面板");
		b.add("item." + MOD_ID + ".doll_egg.invalidated",
			en ? "Doll Spawn Egg [Invalidated]" : "人偶刷怪蛋[已失效]");
		b.add("tooltip." + MOD_ID + ".invalidated",
			en ? "The corresponding doll has died; this egg is invalid" : "对应人偶已死亡，此蛋已失效");
		b.add("tooltip." + MOD_ID + ".baton_selected",
			en ? "Currently selected: %s" : "当前选中：%s");
		b.add("tooltip." + MOD_ID + ".control_panel_hint",
			en ? "Right-click anywhere to open the control panel" : "右键任意位置打开控制面板");
		b.add("tooltip." + MOD_ID + ".structure_search", en ? "Structure Search" : "结构搜索");
		b.add("screen." + MOD_ID + ".structure_search", en ? "Structure Search" : "结构搜索");

		addMsg(b, "structure_search_found", en, "%s %s", "%s %s");
		addMsg(b, "structure_search_not_found", en, "No %s found within 12000 blocks", "12000 格内未找到 %s");
		addMsg(b, "structure_search_cooldown", en, "Search cooldown: %ss remaining", "搜索冷却中，还需 %s 秒");
		addMsg(b, "structure_search_wrong_dim", en, "This structure is not in the current dimension", "该结构不在当前维度");

		// 村庄搜索——UI 与消息（独立于结构/群系搜索）
		b.add("tooltip." + MOD_ID + ".village_search", en ? "Village Search" : "村庄搜索");
		b.add("screen." + MOD_ID + ".village_search", en ? "Village Search" : "村庄搜索");
		addMsg(b, "village_search_found", en, "Village found at %s", "已找到村庄：%s");
		addMsg(b, "village_search_not_found", en, "No unvisited %s found within 12000 blocks", "12000 格内未找到未搜索过的%s");
		addMsg(b, "village_search_cooldown", en, "Search cooldown: %ss remaining", "搜索冷却中，还需 %s 秒");

		// 村庄搜索——5 种村庄群系名称
		b.add("village." + MOD_ID + ".plains",  en ? "Plains Village" : "平原村庄");
		b.add("village." + MOD_ID + ".desert",  en ? "Desert Village" : "沙漠村庄");
		b.add("village." + MOD_ID + ".savanna", en ? "Savanna Village" : "热带草原村庄");
		b.add("village." + MOD_ID + ".snowy",   en ? "Snowy Village" : "雪原村庄");
		b.add("village." + MOD_ID + ".taiga",   en ? "Taiga Village" : "针叶林村庄");

		// 结构搜索——13 种结构名称
		b.add("structure." + MOD_ID + ".monster_room", en ? "Dungeon" : "地牢");
		b.add("structure." + MOD_ID + ".jungle_temple", en ? "Jungle Temple" : "丛林神庙");
		b.add("structure." + MOD_ID + ".monument", en ? "Ocean Monument" : "海底神殿");
		b.add("structure." + MOD_ID + ".ancient_city", en ? "Ancient City" : "远古城市");
		b.add("structure." + MOD_ID + ".mansion", en ? "Woodland Mansion" : "林地府邸");
		b.add("structure." + MOD_ID + ".trial_chambers", en ? "Trial Chambers" : "试炼密室");
		b.add("structure." + MOD_ID + ".mineshaft", en ? "Mineshaft" : "废弃矿井");
		b.add("structure." + MOD_ID + ".stronghold", en ? "Stronghold" : "要塞");
		b.add("structure." + MOD_ID + ".buried_treasure", en ? "Buried Treasure" : "藏宝堆");
		b.add("structure." + MOD_ID + ".shipwreck", en ? "Shipwreck" : "沉船");
		b.add("structure." + MOD_ID + ".fortress", en ? "Nether Fortress" : "下界要塞");
		b.add("structure." + MOD_ID + ".bastion_remnant", en ? "Bastion Remnant" : "猪灵堡垒");
		b.add("structure." + MOD_ID + ".end_city", en ? "End City" : "末地城");

		// 群系搜索——UI 与消息
		b.add("tooltip." + MOD_ID + ".biome_search", en ? "Biome Search" : "群系搜索");
		b.add("screen." + MOD_ID + ".biome_search", en ? "Biome Search" : "群系搜索");
		b.add("screen." + MOD_ID + ".biome_search_empty", en ? "No biomes in this dimension" : "当前维度无群系");
		addMsg(b, "biome_search_found", en, "%s found at %s", "已找到 %s：%s");
		addMsg(b, "biome_search_not_found", en, "No %s found within 12000 blocks", "12000 格内未找到 %s");
		addMsg(b, "biome_search_wrong_dim", en, "This biome is not in the current dimension", "该群系不在当前维度");

		// 群系搜索——主世界 9 种群系
		b.add("biome." + MOD_ID + ".cherry_grove",    en ? "Cherry Grove" : "樱花林");
		b.add("biome." + MOD_ID + ".desert",          en ? "Desert" : "沙漠");
		b.add("biome." + MOD_ID + ".bamboo_jungle",   en ? "Bamboo Jungle" : "竹林");
		b.add("biome." + MOD_ID + ".mushroom_fields",en ? "Mushroom Fields" : "蘑菇岛");
		b.add("biome." + MOD_ID + ".snowy_plains",  en ? "Snowy Plains" : "雪原");
		b.add("biome." + MOD_ID + ".plains",          en ? "Plains" : "平原");
		b.add("biome." + MOD_ID + ".swamp",           en ? "Swamp" : "沼泽");
		b.add("biome." + MOD_ID + ".lush_caves",     en ? "Lush Caves" : "繁茂洞穴");
		b.add("biome." + MOD_ID + ".pale_garden",    en ? "Pale Garden" : "苍白花园");

		// 群系搜索——下界 5 种群系
		b.add("biome." + MOD_ID + ".nether_wastes",    en ? "Nether Wastes" : "下界荒地");
		b.add("biome." + MOD_ID + ".soul_sand_valley",en ? "Soul Sand Valley" : "灵魂沙峡谷");
		b.add("biome." + MOD_ID + ".crimson_forest",  en ? "Crimson Forest" : "绯红森林");
		b.add("biome." + MOD_ID + ".warped_forest",   en ? "Warped Forest" : "诡异森林");
		b.add("biome." + MOD_ID + ".basalt_deltas",   en ? "Basalt Deltas" : "玄武岩三角洲");

		// 统一搜索菜单（分类页签 + 类型列表 + 结果列表 + 打卡）
		b.add("tooltip." + MOD_ID + ".guide_search", en ? "Search for biomes, structures and villages" : "搜索群系、结构与村庄");
		b.add("screen." + MOD_ID + ".guide_search", en ? "Search" : "目标搜索");
		b.add("gui." + MOD_ID + ".cat_structure", en ? "Structures" : "结构");
		b.add("gui." + MOD_ID + ".cat_biome", en ? "Biomes" : "群系");
		b.add("gui." + MOD_ID + ".cat_village", en ? "Villages" : "村庄");
		b.add("gui." + MOD_ID + ".search_empty_cat", en ? "No targets of this type in the current dimension" : "当前维度没有此类目标");
		b.add("gui." + MOD_ID + ".search_pick_hint", en ? "Click a target to search (results are kept until you refresh)" : "点击目标搜索（结果保留，可点刷新重搜）");
		b.add("gui." + MOD_ID + ".search_pending", en ? "Searching..." : "搜索中...");
		b.add("gui." + MOD_ID + ".search_no_result", en ? "Found no target within 1600 blocks" : "1600 格内未找到目标");
		b.add("gui." + MOD_ID + ".search_refresh", en ? "Refresh" : "刷新");
		b.add("gui." + MOD_ID + ".search_refresh_hint", en ? "Re-search around your current position (100-chunk radius)" : "以当前位置为中心重新搜索（半径 100 区块）");
		b.add("gui." + MOD_ID + ".search_result_mark", en ? "Mark as visited" : "标记为已探索");
		b.add("gui." + MOD_ID + ".search_result_unmark", en ? "Unmark visited" : "取消已探索标记");
		b.add("gui." + MOD_ID + ".search_dist", en ? "%s blocks" : "距离 %s 格");
		b.add("gui." + MOD_ID + ".search_coord", en ? "%s, %s" : "坐标 %s, %s");
		addMsg(b, "search_cooldown", en, "Search cooldown: %ss remaining", "搜索冷却中，还需 %s 秒");
		addMsg(b, "search_busy", en, "The server is busy, try again in a moment", "服务器繁忙，请稍后再试");

		addMsg(b, "need_name", en, "Please name the doll spawn egg on an anvil first",
			"请先使用铁砧给人偶刷怪蛋赐名");
		addMsg(b, "baton_selected", en, "Selected doll: %s", "已选中人偶：%s");
		addMsg(b, "need_select_doll", en, "Right-click a doll with the baton to select it first",
			"请先使用指挥棒右键人偶选中目标");
		addMsg(b, "corner_a_set", en, "Corner A marked. Right-click the opposite corner (B)",
			"已标记角A，请再右键对角方块（角B）");
		addMsg(b, "area_set", en, "Work area set for %s", "已为 %s 划定作业区");
		addMsg(b, "area_cleared", en, "Work area cleared for %s", "已清除 %s 的作业区");
		addMsg(b, "doll_not_found", en, "Doll is not nearby, cannot set work area",
			"人偶不在附近，无法设置作业区");
		addMsg(b, "area_too_large", en, "Work area too large (max 64 blocks per side)",
			"作业区过大（单边上限 64 格）");
		addMsg(b, "selection_cancelled", en, "Selection cancelled", "已取消选区");
		addMsg(b, "area_status_set", en, "This doll has a work area (sneak-right-click to clear)",
			"此人偶已设作业区（潜行右键人偶可清除）");
		addMsg(b, "area_status_unset", en, "This doll has no work area; right-click blocks to set one",
			"此人偶未设作业区，可右键方块划定");
		addMsg(b, "baton_mode_required", en,
			"%s's current mode cannot be configured: switch to Melee/Ranged/Chop/Farm/Mine first",
			"%s 当前模式无法调试：请先切换到近战/射手/砍树/耕种/挖矿模式");
		addMsg(b, "baton_targeting_mode", en,
			"%s is in targeting mode — right-click a mob to designate, or right-click ground to cancel",
			"%s 已进入目标选择模式——右键生物指定目标，或右键地面取消");
		addMsg(b, "baton_target_set", en,
			"%s is now attacking %s",
			"%s 开始攻击 %s");
		addMsg(b, "baton_target_invalid", en,
			"Target is invalid or dead",
			"目标无效或已死亡");
		addMsg(b, "baton_target_self", en,
			"Cannot target yourself",
			"不能指定自己为目标");
		addMsg(b, "baton_targeting_cancelled", en,
			"Targeting mode cancelled",
			"已取消目标选择模式");
		b.add("tooltip." + MOD_ID + ".baton_targeting",
			en ? "Targeting mode: %s" : "目标选择模式：%s");
		addMsg(b, "mine_tunnel_set", en, "Tunnel boring machine set for %s: heading %s",
			"已为 %s 设置盾构机：向%s掘进");
		addMsg(b, "mine_tunnel_invalid_stack", en,
			"Tunnel entrance needs two vertically stacked blocks",
			"盾构机入口需要两个竖直堆叠的方块");
		addMsg(b, "mine_tunnel_invalid_face", en,
			"Aim at the tunnel wall (horizontal face), not the floor/ceiling",
			"请对准入口的墙（水平面），不要点脚下/头顶");
		addMsg(b, "mine_tunnel_resumed", en, "%s resumed tunnelling", "%s 恢复掘进");
		addMsg(b, "mine_tunnel_not_at_spot", en,
			"%s is not at the tunnel; cannot resume tunnelling (bring the doll back to the tunnel)",
			"%s 不在隧道处，无法恢复掘进（请把人偶带回隧道口）");
		addMsg(b, "mine_stop_backpack_full", en, "%s backpack is full, tunnelling stopped",
			"%s 背包已满，盾构机停止");
		addMsg(b, "mine_stop_unbreakable", en,
			"%s cannot break the block ahead (pickaxe tier too low), tunnelling stopped",
			"%s 前方方块挖不动（镐等级不足），盾构机停止");
		addMsg(b, "mine_stop_gravity", en, "%s found gravel/sand ahead, tunnelling stopped",
			"%s 前方有沙砾/沙子，盾构机停止");
		addMsg(b, "mine_stop_lava", en, "%s found lava nearby, tunnelling stopped",
			"%s 附近有岩浆，盾构机停止");
		addMsg(b, "mine_stop_cliff", en, "%s reached a cliff, tunnelling stopped",
			"%s 前方是悬崖，盾构机停止");
		addMsg(b, "mine_stop_no_pickaxe", en, "%s ran out of pickaxes, tunnelling stopped",
			"%s 镐子用完了，盾构机停止");
		addMsg(b, "mine_stop_bedrock", en, "%s hit bedrock, tunnelling stopped",
			"%s 前方是基岩，盾构机停止");
		addMsg(b, "mine_no_pickaxe", en, "%s ran out of pickaxes, mining stopped (add another pickaxe to resume)",
			"%s 镐子用完了，挖矿模式停止（补充镐子后自动恢复）");
		addMsg(b, "mine_stop_water", en, "%s found water ahead, tunnelling stopped",
			"%s 前方有水，盾构机停止");
		addMsg(b, "mine_stop_blocked", en, "%s cannot dig ahead, tunnelling stopped",
			"%s 前方无法挖通，盾构机停止");
		addMsg(b, "mine_backpack_full", en, "%s backpack is full, mining paused (resumes when cleared)",
			"%s 背包已满，暂缓挖矿（清理背包后自动恢复）");
		addMsg(b, "no_pickaxe", en, "No pickaxe in the inventory, cannot enable Mining mode",
			"物品栏中没有镐子，无法开启挖矿模式");
		addMsg(b, "not_owner", en, "This spawn egg is not bound to the target doll",
			"此刷怪蛋绑定的人偶不是目标人偶，无法回收");
		addMsg(b, "invalidated", en, "The doll for this spawn egg has died",
			"此刷怪蛋对应的人偶已死亡，无法使用");
		addMsg(b, "level_mismatch", en, "Spawn egg tier does not match the doll tier",
			"刷怪蛋等级与人偶等级不匹配，无法回收");
		addMsg(b, "not_your_doll", en, "This is not your doll", "这不是你的人偶，无法操作");
		addMsg(b, "recall_no_spot", en, "No standable spot nearby, recall failed",
			"此位置附近没有可站立的落点，召回失败");
		addMsg(b, "recall_not_found", en, "Cannot locate the doll's chunk; try again near the doll",
			"找不到人偶所在区块，请靠近人偶所在区域后再试");
		addMsg(b, "no_melee_weapon", en, "No melee weapon in the inventory",
			"物品栏中没有近战武器，无法开启近战模式");
		addMsg(b, "no_ranged_weapon", en, "No bow or crossbow in the inventory",
			"物品栏中没有弓或弩，无法开启射手模式");
		addMsg(b, "no_seeds", en, "The doll has no seeds", "人偶缺少种子");
		addMsg(b, "no_food", en, "The doll has no food", "人偶没有食物，无法开启喂食模式");
		addMsg(b, "no_axe", en, "No axe in the inventory", "物品栏中没有斧头，无法开启砍树模式");
		addMsg(b, "chop_stop_no_axe", en, "%s ran out of axes, chopping stopped",
			"%s 斧头用完了，砍树模式停止");
		// 运行时前置消失（方案A：所有模式前置消失即关模式并提示）
		addMsg(b, "melee_stop_no_weapon", en, "%s has no melee weapon, melee stopped",
			"%s 近战武器没了，近战模式停止");
		addMsg(b, "ranged_stop_no_weapon", en, "%s has no ranged weapon, ranged stopped",
			"%s 远程武器没了，射手模式停止");
		addMsg(b, "farm_stop_no_seeds", en, "%s ran out of seeds, farming stopped",
			"%s 种子用完了，耕种模式停止");
		addMsg(b, "feed_stop_no_food", en, "The doll has no food, feeding stopped",
			"%s 没有食物了，喂食模式停止");
		addMsg(b, "torch_stop_no_torch", en, "%s ran out of torches, torch-placing stopped",
			"%s 火把用完了，插火把模式停止");
		addMsg(b, "fish_stop_no_rod", en, "%s ran out of fishing rods, fishing stopped",
			"%s 钓鱼竿用完了，钓鱼模式停止");
		addMsg(b, "no_torch", en, "The doll has no torches", "人偶没有火把，无法开启插火把模式");
		addMsg(b, "no_fishing_rod", en, "No fishing rod in the inventory",
			"物品栏中没有钓鱼竿，无法开启钓鱼模式");
		addMsg(b, "fish_follow_conflict", en, "The doll needs to focus on fishing",
			"人偶需要认真钓鱼");
		addMsg(b, "pale_sacrifice", en, "The pale doll sacrificed itself to save you",
			"苍白人偶以命相抵，为你挡下致命一击");
		addMsg(b, "guide_book_received", en, "You received the Doll Guide Book",
			"你获得了人偶指南书");

		b.add("gui." + MOD_ID + ".barrier_slot", en ? "Decorative slot" : "装饰栏位");
		b.add("gui." + MOD_ID + ".offhand_slot", en ? "Offhand slot" : "副手栏");

		// 盾构机掘进方向（mine_tunnel_set 的 %s 参数）
		b.add("direction." + MOD_ID + ".north", en ? "North" : "北");
		b.add("direction." + MOD_ID + ".south", en ? "South" : "南");
		b.add("direction." + MOD_ID + ".west", en ? "West" : "西");
		b.add("direction." + MOD_ID + ".east", en ? "East" : "东");

		// 模式按钮（DollMode.getNormalName/getHighlightName 使用）
		addMode(b, DollMode.MELEE, en, "Melee", "近战模式");
		addMode(b, DollMode.RANGED, en, "Ranged", "射手模式");
		addMode(b, DollMode.FARM, en, "Farm", "耕种模式");
		addMode(b, DollMode.FEED, en, "Feed", "喂食模式");
		addMode(b, DollMode.CHOP, en, "Chop", "砍树模式");
		addMode(b, DollMode.MINE, en, "Mine", "挖矿模式");
		addMode(b, DollMode.TORCH, en, "Torch", "插火把模式");
		addMode(b, DollMode.FISH, en, "Fish", "钓鱼模式");
		addModeFollow(b, en, "Follow", "跟随模式");
	}

	private static void addMsg(FabricLanguageProvider.TranslationBuilder b, String key, boolean en, String enValue, String zhValue) {
		b.add("message." + MOD_ID + "." + key, en ? enValue : zhValue);
	}

	private static void addMode(FabricLanguageProvider.TranslationBuilder b, DollMode mode, boolean en, String enValue, String zhValue) {
		b.add("mode." + MOD_ID + "." + mode.lowerCaseName(), en ? enValue : zhValue);
	}

	private static void addModeFollow(FabricLanguageProvider.TranslationBuilder b, boolean en, String enValue, String zhValue) {
		b.add("mode." + MOD_ID + ".follow", en ? enValue : zhValue);
	}

	private static class DollLangProvider extends FabricLanguageProvider {

		public DollLangProvider(FabricPackOutput dataOutput,
				CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(dataOutput, "zh_cn", registryLookup);
		}

		@Override
		public void generateTranslations(HolderLookup.Provider registryLookup,
				TranslationBuilder translationBuilder) {
			addAllEntries(translationBuilder, false);
		}
	}

	private static class DollEnUsProvider extends FabricLanguageProvider {

		public DollEnUsProvider(FabricPackOutput dataOutput,
				CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(dataOutput, "en_us", registryLookup);
		}

		@Override
		public void generateTranslations(HolderLookup.Provider registryLookup,
				TranslationBuilder translationBuilder) {
			addAllEntries(translationBuilder, true);
		}
	}
}
