package io.github.a10086ovo.doll.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 苍白人偶头颅。继承 BlockItem 以支持方块放置，同时通过 .equippable(HEAD) 实现可佩戴。
 */
public class PaleDollHeadItem extends BlockItem {

	public PaleDollHeadItem(Block block, Properties properties) {
		super(block, properties.equippable(EquipmentSlot.HEAD));
	}
}
