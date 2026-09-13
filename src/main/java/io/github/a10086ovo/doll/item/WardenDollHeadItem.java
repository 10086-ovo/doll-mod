package io.github.a10086ovo.doll.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 监守者人偶头颅。
 * 掉落物默认消失时长 5 分钟（同下界之星），防止在远古城市复杂地形中丢失。
 * 继承 BlockItem 以支持方块放置，同时通过 .equippable(HEAD) 实现可佩戴。
 */
public class WardenDollHeadItem extends BlockItem {

	public WardenDollHeadItem(Block block, Properties properties) {
		super(block, properties.equippable(EquipmentSlot.HEAD));
	}
}