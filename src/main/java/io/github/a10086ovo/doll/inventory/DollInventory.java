package io.github.a10086ovo.doll.inventory;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 人偶的物品栏，固定 45 格，与 GUI 布局一一对应：
 * 0/5/7/8 装饰，1-4 护甲，6 副手，9-35 存储区，36-44 快捷栏。
 */
public class DollInventory implements Container {

	public static final int INVENTORY_SIZE = 45;

	private final DollEntity owner;
	private final NonNullList<ItemStack> stacks = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

	public DollInventory(DollEntity owner) {
		this.owner = owner;
	}

	public DollEntity getOwner() {
		return owner;
	}

	@Override
	public int getContainerSize() {
		return INVENTORY_SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= stacks.size()) {
			return ItemStack.EMPTY;
		}
		return stacks.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack stack = getItem(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = stack.split(amount);
		setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot < 0 || slot >= stacks.size()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = stacks.get(slot);
		stacks.set(slot, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= stacks.size()) {
			return;
		}
		stacks.set(slot, stack == null ? ItemStack.EMPTY : stack);
		setChanged();
	}

	@Override
	public void setChanged() {
		// 物品栏内容由实体死亡/回收时整体持久化，无需逐格标记脏数据
	}

	@Override
	public boolean stillValid(Player player) {
		if (owner.isRemoved()) {
			return false;
		}
		return player.distanceToSqr(owner) <= 64.0;
	}

	@Override
	public void startOpen(ContainerUser user) {
		// 无入场特效
	}

	@Override
	public void stopOpen(ContainerUser user) {
		// 无离场特效
	}

	@Override
	public void clearContent() {
		java.util.Collections.fill(stacks, ItemStack.EMPTY);
		setChanged();
	}

	/**
	 * 实体持久化：写入 45 格物品。
	 */
	public void saveToValue(ValueOutput output) {
		ContainerHelper.saveAllItems(output, stacks);
	}

	public void loadFromValue(ValueInput input) {
		ContainerHelper.loadAllItems(input, stacks);
	}

	/**
	 * 列出当前存储区（9-35）内所有物品，用于回收/生成时在蛋上保存。
	 */
	public NonNullList<ItemStack> getStorageStacks() {
		NonNullList<ItemStack> result = NonNullList.create();
		for (int i = 9; i <= 35; i++) {
			result.add(getItem(i));
		}
		return result;
	}

	/**
	 * 清空存储区（回收成功后调用）。
	 */
	public void clearStorage() {
		for (int i = 9; i <= 35; i++) {
			setItem(i, ItemStack.EMPTY);
		}
	}
}