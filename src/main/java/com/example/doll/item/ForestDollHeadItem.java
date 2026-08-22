package com.example.doll.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 森林人偶头颅。继承 BlockItem 以支持方块放置，同时通过 .equippable(HEAD) 实现可佩戴。
 */
public class ForestDollHeadItem extends BlockItem {

	public ForestDollHeadItem(Block block, Properties properties) {
		super(block, properties.equippable(EquipmentSlot.HEAD));
	}
}
