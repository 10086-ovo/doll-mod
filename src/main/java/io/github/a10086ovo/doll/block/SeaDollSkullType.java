package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum SeaDollSkullType implements SkullBlock.Type {
	SEA_DOLL("sea_doll");

	private final String name;

	SeaDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
