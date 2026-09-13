package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum ForestDollSkullType implements SkullBlock.Type {
	FOREST_DOLL("forest_doll");

	private final String name;

	ForestDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
