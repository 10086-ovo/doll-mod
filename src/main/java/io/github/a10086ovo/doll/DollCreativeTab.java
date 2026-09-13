package io.github.a10086ovo.doll;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 人偶模组专属创造标签页，展示全部模组物品。
 * <p>
 * Forge 下标签页构建后仍需注册进 {@link Registries#CREATIVE_MODE_TAB}，
 * 注册动作由 DollMod 在 RegisterEvent 中完成（见 DollCreativeTab#register）。
 */
public class DollCreativeTab {

	public static final ResourceKey<CreativeModeTab> DOLL_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "doll_tab")
	);

	public static final CreativeModeTab DOLL_TAB = CreativeModeTab.builder()
		.title(Component.translatable("itemGroup." + DollModConstants.MOD_ID + ".doll_tab"))
		.icon(() -> new ItemStack(DollMod.DOLL_TIER3_EGG))
		.displayItems((parameters, output) -> {
			output.accept(DollMod.DOLL_BATON);
			output.accept(DollMod.DOLL_CONTROL_PANEL);
			output.accept(DollMod.DOLL_TIER1_EGG);
			output.accept(DollMod.DOLL_TIER2_EGG);
			output.accept(DollMod.DOLL_TIER3_EGG);
			output.accept(DollMod.DOLL_TIER4_EGG);
			output.accept(DollMod.DOLL_TIER5_EGG);
			output.accept(DollMod.WARDEN_DOLL_SPAWN_EGG);
			output.accept(DollMod.WARDEN_DOLL_HEAD);
			output.accept(DollMod.SCULK_SHRINE_ITEM);
			output.accept(DollMod.PALE_DOLL_EGG);
			output.accept(DollMod.PALE_DOLL_HEAD);
			output.accept(DollMod.PALE_BOW_ITEM);
			output.accept(DollMod.NETHER_DOLL_EGG);
			output.accept(DollMod.NETHER_DOLL_HEAD);
			output.accept(DollMod.NETHER_SWORD_ITEM);
			output.accept(DollMod.ENDER_DOLL_EGG);
			output.accept(DollMod.ENDER_DOLL_HEAD);
			output.accept(DollMod.ENDER_AXE_ITEM);
			output.accept(DollMod.SEA_DOLL_EGG);
			output.accept(DollMod.SEA_DOLL_HEAD);
			output.accept(DollMod.SEA_HELMET);
			output.accept(DollMod.SEA_CHESTPLATE);
			output.accept(DollMod.SEA_LEGGINGS);
			output.accept(DollMod.SEA_BOOTS);
			output.accept(DollMod.FOREST_DOLL_EGG);
			output.accept(DollMod.FOREST_DOLL_HEAD);
			output.accept(DollMod.THORNS_SHIELD_ITEM);
			output.accept(DollMod.GUIDE_DOLL_EGG);
			output.accept(DollMod.GUIDE_DOLL_HEAD);
			output.accept(DollMod.GUIDE_BOOK_ITEM);
			output.accept(DollMod.GUIDE_PICKAXE_ITEM);
			output.accept(DollMod.ROCK_ANVIL_ITEM);
			output.accept(DollMod.CHIPPED_ROCK_ANVIL_ITEM);
			output.accept(DollMod.DAMAGED_ROCK_ANVIL_ITEM);
		})
		.build();

	/** 把标签页注册进创造标签注册表（由 DollMod 在 RegisterEvent 中调用）。 */
	public static void register() {
		net.minecraft.core.Registry.register(
			net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB,
			DOLL_TAB_KEY,
			DOLL_TAB
		);
	}
}
