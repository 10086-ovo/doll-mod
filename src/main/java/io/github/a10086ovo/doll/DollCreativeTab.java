package io.github.a10086ovo.doll;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 人偶模组专属创造标签页，展示全部模组物品。
 */
public class DollCreativeTab {

	public static final ResourceKey<CreativeModeTab> DOLL_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "doll_tab")
	);

	public static final CreativeModeTab DOLL_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		DOLL_TAB_KEY,
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup." + DollModConstants.MOD_ID + ".doll_tab"))
			.icon(() -> new ItemStack(DollMod.DOLL_TIER3_EGG))
			.build()
	);

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(DOLL_TAB_KEY).register(output -> {
			// ---- 通用工具 ----
			output.accept(DollMod.DOLL_BATON);
			output.accept(DollMod.DOLL_CONTROL_PANEL);
			// ---- 普通变体（一~五阶） ----
			output.accept(DollMod.DOLL_TIER1_EGG);
			output.accept(DollMod.DOLL_TIER2_EGG);
			output.accept(DollMod.DOLL_TIER3_EGG);
			output.accept(DollMod.DOLL_TIER4_EGG);
			output.accept(DollMod.DOLL_TIER5_EGG);
			// ---- 幽匿（深暗群系） ----
			output.accept(DollMod.WARDEN_DOLL_SPAWN_EGG);
			output.accept(DollMod.WARDEN_DOLL_HEAD);
			output.accept(DollMod.SCULK_SHRINE_ITEM);
			// ---- 苍白（苍白花园群系） ----
			output.accept(DollMod.PALE_DOLL_EGG);
			output.accept(DollMod.PALE_DOLL_HEAD);
			output.accept(DollMod.PALE_BOW_ITEM);
			// ---- 下界 ----
			output.accept(DollMod.NETHER_DOLL_EGG);
			output.accept(DollMod.NETHER_DOLL_HEAD);
			output.accept(DollMod.NETHER_SWORD_ITEM);
			// ---- 末影（末地） ----
			output.accept(DollMod.ENDER_DOLL_EGG);
			output.accept(DollMod.ENDER_DOLL_HEAD);
			output.accept(DollMod.ENDER_AXE_ITEM);
			// ---- 海洋 ----
			output.accept(DollMod.SEA_DOLL_EGG);
			output.accept(DollMod.SEA_DOLL_HEAD);
			output.accept(DollMod.SEA_HELMET);
			output.accept(DollMod.SEA_CHESTPLATE);
			output.accept(DollMod.SEA_LEGGINGS);
			output.accept(DollMod.SEA_BOOTS);
			// ---- 森林 ----
			output.accept(DollMod.FOREST_DOLL_EGG);
			output.accept(DollMod.FOREST_DOLL_HEAD);
			output.accept(DollMod.THORNS_SHIELD_ITEM);
			// ---- 向导 ----
			output.accept(DollMod.GUIDE_DOLL_EGG);
			output.accept(DollMod.GUIDE_DOLL_HEAD);
			output.accept(DollMod.GUIDE_BOOK_ITEM);
			// ---- 方块 ----
			output.accept(DollMod.ROCK_ANVIL_ITEM);
			output.accept(DollMod.CHIPPED_ROCK_ANVIL_ITEM);
			output.accept(DollMod.DAMAGED_ROCK_ANVIL_ITEM);
		});
	}
}
