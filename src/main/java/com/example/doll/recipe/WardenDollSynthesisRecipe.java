package com.example.doll.recipe;

import com.example.doll.DollMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * 监守者人偶蛋合成配方：
 * 8 种矿物（煤炭/铜锭/铁锭/金锭/青金石/红石/绿宝石/钻石）围一圈，
 * 中间放监守者人偶头颅 → 合成监守者人偶刷怪蛋。
 * 允许无命名头颅直接合成（监守者人偶蛋仍需要铁砧赐名后才能召唤）。
 */
public class WardenDollSynthesisRecipe extends CustomRecipe {

	private static final Set<Item> MINERALS = Set.of(
		Items.COAL, Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT,
		Items.LAPIS_LAZULI, Items.REDSTONE, Items.EMERALD, Items.DIAMOND
	);

	private final RecipeSerializer<WardenDollSynthesisRecipe> serializer;

	public WardenDollSynthesisRecipe() {
		this.serializer = new RecipeSerializer<>(
			MapCodec.unit(this),
			StreamCodec.<RegistryFriendlyByteBuf, WardenDollSynthesisRecipe>unit(this));
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
			return false;
		}
		ItemStack center = input.getItem(1, 1);
		if (center.getItem() != DollMod.WARDEN_DOLL_HEAD) {
			return false;
		}
		Set<Item> found = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			if (i == 4) continue;
			Item item = input.getItem(i).getItem();
			if (!MINERALS.contains(item)) {
				return false;
			}
			found.add(item);
		}
		return found.size() == 8;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack result = new ItemStack(DollMod.WARDEN_DOLL_SPAWN_EGG);
		ItemStack center = input.getItem(1, 1);
		if (center.getCustomName() != null) {
			result.set(DataComponents.CUSTOM_NAME, center.getCustomName());
		}
		return result;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return serializer;
	}
}