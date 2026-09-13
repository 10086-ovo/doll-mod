package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum NetherDollSkullType implements SkullBlock.Type {
	NETHER_DOLL("nether_doll");

	private final String name;

	NetherDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
