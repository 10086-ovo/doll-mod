package io.github.a10086ovo.doll.recipe;

import io.github.a10086ovo.doll.item.DollSpawnEggItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 人偶刷怪蛋升级配方。
 * <p>
 * 原版合成配方生成结果时会丢弃输入物品的所有 NBT：升级后蛋的名字没了、
 * 回收时携带的物品栏/作业区/盾构机配置全部消失。这个特殊配方在合成时把
 * 源蛋的 {@link DataComponents#CUSTOM_NAME} 与 {@link DataComponents#CUSTOM_DATA}
 * 原样搬到新蛋上（仅剔除绑定 UUID 与失效标记），做到"回收→升级→再召唤"
 * 一条龙不丢任何东西，升级后也无需重新赐名。
 * <p>
 * 只允许升级"已命名无绑定"或"未命名无绑定"的蛋：已绑定（人偶还活着）的蛋
 * 升级会让人偶永久失去对应蛋而卡死，已失效的蛋也不允许复活。
 * 布局：3×3 满格、蛋在正中心、四周 8 格为升级材料（每格可指定不同材料）。
 */
public class DollUpgradeRecipe extends CustomRecipe {

	private final Item sourceEgg;
	private final Item targetEgg;
	private final Item[] surroundingMaterials;
	private final RecipeSerializer<DollUpgradeRecipe> serializer;

	/**
	 * @param sourceEgg           源蛋物品（合成格正中心）
	 * @param targetEgg           升级后的目标蛋物品
	 * @param surroundingMaterials 四周 8 格材料，按 3×3 网格行优先跳过中心排列：
	 *                             左上、上中、右上、左中、右中、左下、下中、右下
	 */
	public DollUpgradeRecipe(Item sourceEgg, Item targetEgg, Item... surroundingMaterials) {
		if (surroundingMaterials.length != 8) {
			throw new IllegalArgumentException(
				"DollUpgradeRecipe 需要 8 个四周材料（3×3 网格跳过中心），实际传入 "
					+ surroundingMaterials.length + " 个");
		}
		this.sourceEgg = sourceEgg;
		this.targetEgg = targetEgg;
		this.surroundingMaterials = surroundingMaterials;
		this.serializer = new RecipeSerializer<>(
			MapCodec.unit(this),
			StreamCodec.<RegistryFriendlyByteBuf, DollUpgradeRecipe>unit(this));
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
			return false;
		}
		ItemStack center = input.getItem(1, 1);
		if (center.getItem() != sourceEgg) {
			return false;
		}
		// 已绑定（对应人偶还活着）或已失效的蛋不允许升级
		CompoundTag tag = eggData(center);
		if (tag.getBooleanOr(DollSpawnEggItem.INVALIDATED_NBT_KEY, false)
				|| !tag.getStringOr(DollSpawnEggItem.DOLL_UUID_NBT_KEY, "").isEmpty()) {
			return false;
		}
		// 四周 8 格按网格位置 0,1,2,3,5,6,7,8 依次匹配
		int[] gridPositions = {0, 1, 2, 3, 5, 6, 7, 8};
		for (int j = 0; j < 8; j++) {
			if (input.getItem(gridPositions[j]).getItem() != surroundingMaterials[j]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack result = new ItemStack(targetEgg);
		for (int i = 0; i < input.size(); i++) {
			ItemStack source = input.getItem(i);
			if (source.getItem() != sourceEgg) {
				continue;
			}
			// 名字跟随升级：升级后无需重新赐名
			if (source.getCustomName() != null) {
				result.set(DataComponents.CUSTOM_NAME, source.getCustomName());
			}
			// 物品栏 / 作业区 / 盾构机配置随蛋携带（回收后升级不丢东西）
			CustomData data = source.get(DataComponents.CUSTOM_DATA);
			if (data != null) {
				CompoundTag tag = data.copyTag();
				tag.remove(DollSpawnEggItem.DOLL_UUID_NBT_KEY);
				tag.remove(DollSpawnEggItem.INVALIDATED_NBT_KEY);
				if (!tag.isEmpty()) {
					result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				}
			}
			break;
		}
		return result;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return serializer;
	}

	private static CompoundTag eggData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}
}
