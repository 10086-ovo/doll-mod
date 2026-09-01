package io.github.a10086ovo.doll.screen;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.inventory.DollInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

/**
 * 人偶背包界面（81 槽）：
 * 人偶部分 45 槽（0-8 装备/装饰行，9-35 存储，36-44 快捷栏）
 * + 玩家部分 36 槽（45-71 主物品栏，72-80 热键）。
 */
public class DollScreenHandler extends AbstractContainerMenu {

	public static final int DOLL_INVENTORY_START = 0;
	public static final int DOLL_INVENTORY_END = 44;

	public static final int DOLL_ROW0_START = 0;
	public static final int DOLL_ROW0_END = 8;
	public static final int DOLL_STORAGE_START = 9;
	public static final int DOLL_STORAGE_END = 35;
	public static final int DOLL_HOTBAR_START = 36;
	public static final int DOLL_HOTBAR_END = 44;

	// 玩家部分：45-71 主物品栏（对应 Inventory 9-35），72-80 热键（对应 Inventory 0-8）。
	// quickMoveStack 的"转到玩家背包"用 45-80 全区间（主物品栏在前、热键在后，原版容器习惯）。
	public static final int PLAYER_INVENTORY_START = 45;
	public static final int PLAYER_INVENTORY_END = 80;

	private static final int EQUIPMENT_HEAD = 1;
	private static final int EQUIPMENT_CHEST = 2;
	private static final int EQUIPMENT_LEGS = 3;
	private static final int EQUIPMENT_FEET = 4;
	private static final int OFFHAND_SLOT = 6;

	private final DollInventory dollInventory;
	private final Inventory playerInventory;

	public DollScreenHandler(int syncId, Inventory playerInventory, DollInventory dollInventory) {
		super(DollMod.DOLL_SCREEN_HANDLER, syncId);
		this.dollInventory = dollInventory;
		this.playerInventory = playerInventory;
		checkContainerSize(dollInventory, DollInventory.INVENTORY_SIZE);
		dollInventory.startOpen(playerInventory.player);
		addDollSlots();
		addPlayerSlots();
	}

	/**
	 * 由 ELoom 正常同步路径创建：根据人偶实体 ID 恢复背包。
	 */
	public static DollScreenHandler create(int syncId, Inventory inv, Integer dollEntityId) {
		Level level = inv.player.level();
		net.minecraft.world.entity.Entity entity = level.getEntity(dollEntityId);
		if (entity instanceof DollEntity doll) {
			return new DollScreenHandler(syncId, inv, doll.getInventoryBag());
		}
		return null;
	}

	private void addDollSlots() {
		DollEntity owner = dollInventory.getOwner();
		// 第一行：0 屏障 | 1 头盔 2 胸甲 3 护腿 4 靴子 | 5 屏障 | 6 副手 | 7 屏障 | 8 屏障
		addSlot(new BarrierSlot(dollInventory, 0, 8, 44));
		addSlot(new ArmorSlot(dollInventory, 1, 26, 44, EquipmentSlot.HEAD, owner));
		addSlot(new ArmorSlot(dollInventory, 2, 44, 44, EquipmentSlot.CHEST, owner));
		addSlot(new ArmorSlot(dollInventory, 3, 62, 44, EquipmentSlot.LEGS, owner));
		addSlot(new ArmorSlot(dollInventory, 4, 80, 44, EquipmentSlot.FEET, owner));
		addSlot(new BarrierSlot(dollInventory, 5, 98, 44));
		addSlot(new OffhandSlot(dollInventory, 6, 116, 44, owner));
		addSlot(new BarrierSlot(dollInventory, 7, 134, 44));
		addSlot(new BarrierSlot(dollInventory, 8, 152, 44));

		// 第二至四行：人偶存储区 9-35
		for (int i = 9; i <= 35; i++) {
			int row = (i - 9) / 9;
			int col = (i - 9) % 9;
			addSlot(new Slot(dollInventory, i, 8 + col * 18, 62 + row * 18));
		}

		// 第五行：人偶快捷栏 36-44（可存储物品）
		for (int i = 36; i <= 44; i++) {
			int col = i - 36;
			addSlot(new Slot(dollInventory, i, 8 + col * 18, 116));
		}
	}

	private void addPlayerSlots() {
		// 玩家主物品栏 45-71（对应 Inventory 9-35）
		for (int i = 0; i < 27; i++) {
			int row = i / 9;
			int col = i % 9;
			addSlot(new Slot(playerInventory, i + 9, 8 + col * 18, 166 + row * 18));
		}
		// 玩家热键 72-80（对应 Inventory 0-8）
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 220));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot sourceSlot = slots.get(index);
		if (!sourceSlot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = sourceSlot.getItem();

		// 装饰屏障槽 0/5/7/8：禁止交互
		if (index == 0 || index == 5 || index == 7 || index == 8) {
			return ItemStack.EMPTY;
		}
		if (index >= DOLL_ROW0_START && index <= DOLL_ROW0_END) {
			// 装备/副手槽（1-4、6）-> 人偶存储区，放不下再进玩家背包
			if (!moveItemStackTo(stack, DOLL_STORAGE_START, DOLL_STORAGE_END + 1, false)) {
				if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END + 1, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else if (index >= DOLL_STORAGE_START && index <= DOLL_STORAGE_END) {
			// 人偶存储区 -> 直接转移到玩家背包（先主物品栏后热键，原版容器习惯）。
			// 修复：原先先转入人偶快捷栏，导致 shift 点击在"人偶存储区↔人偶快捷栏"
			// 之间来回倒腾，物品永远到不了玩家背包。
			if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= DOLL_HOTBAR_START && index <= DOLL_HOTBAR_END) {
			// 人偶快捷栏（工具池）-> 直接转移到玩家背包，不再先转人偶存储区
			if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= PLAYER_INVENTORY_START && index <= PLAYER_INVENTORY_END) {
			// 玩家背包 -> 先尝试装备/副手槽，再人偶快捷栏（工具池），最后人偶存储区
			if (!moveItemStackTo(stack, DOLL_ROW0_START + 1, DOLL_ROW0_END, false)) {
				if (!moveItemStackTo(stack, DOLL_HOTBAR_START, DOLL_HOTBAR_END + 1, false)) {
					if (!moveItemStackTo(stack, DOLL_STORAGE_START, DOLL_STORAGE_END + 1, false)) {
						return ItemStack.EMPTY;
					}
				}
			}
		} else {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			sourceSlot.setByPlayer(ItemStack.EMPTY);
		} else {
			sourceSlot.setChanged();
		}
		if (stack.getCount() == sourceSlot.getItem().getCount()) {
			return ItemStack.EMPTY;
		}
		sourceSlot.onTake(player, stack);
		return stack;
	}

	/**
	 * 屏障装饰槽（0/5/7/8）禁止交互；
	 * 装备槽（1-4）、副手槽（6）、存储槽（9-35）、快捷栏（36-44）允许正常点击。
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId == 0 || slotId == 5 || slotId == 7 || slotId == 8) {
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public boolean stillValid(Player player) {
		return dollInventory.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		dollInventory.stopOpen(player);
	}

	public DollInventory getDollInventory() {
		return dollInventory;
	}

	// ------------------------------------------------------------------
	// 槽类型
	// ------------------------------------------------------------------

	/** 装饰用屏障槽：不可放、不可取，图标始终显示屏障。 */
	static class BarrierSlot extends Slot {
		private static final ItemStack BARRIER_ICON = new ItemStack(Items.BARRIER);

		BarrierSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public ItemStack getItem() {
			return BARRIER_ICON;
		}

		@Override
		public boolean hasItem() {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	/** 装备槽：仅接受对应装备栏位的可装备物品；空槽显示原版透明盔甲占位图标；穿戴时播放原版穿盔甲声。 */
	static class ArmorSlot extends Slot {
		private final DollEntity owner;
		private final EquipmentSlot equipmentSlot;
		private final Identifier emptyIcon;

		ArmorSlot(Container container, int slot, int x, int y, EquipmentSlot equipmentSlot, DollEntity owner) {
			super(container, slot, x, y);
			this.owner = owner;
			this.equipmentSlot = equipmentSlot;
			this.emptyIcon = switch (equipmentSlot) {
				case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
				case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
				case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
				case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
				default -> null;
			};
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			return equippable != null && equippable.slot() == equipmentSlot;
		}

		@Override
		public boolean mayPickup(Player player) {
			return true;
		}

		@Override
		public Identifier getNoItemIcon() {
			return emptyIcon;
		}

		/**
		 * 复刻原版 ArmorSlot.setByPlayer：装备变化时通知 owner，
		 * 由 LivingEntity.onEquipItem 播放对应材质的穿盔甲声并触发 GameEvent。
		 * 参数顺序与原版一致：newItem = 玩家放入的物品，oldItem = 槽位原物品。
		 */
		@Override
		public void setByPlayer(ItemStack newItem, ItemStack oldItem) {
			owner.onEquipItem(equipmentSlot, oldItem, newItem);
			super.setByPlayer(newItem, oldItem);
		}
	}

	/** 副手槽：接受任何物品；空时显示盾牌占位图标。 */
	static class OffhandSlot extends Slot {
		private final DollEntity owner;

		OffhandSlot(Container container, int slot, int x, int y, DollEntity owner) {
			super(container, slot, x, y);
			this.owner = owner;
		}

		@Override
		public ItemStack getItem() {
			return super.getItem();
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !stack.isEmpty();
		}

		@Override
		public boolean mayPickup(Player player) {
			return true;
		}

		@Override
		public Identifier getNoItemIcon() {
			return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
		}
	}

}
