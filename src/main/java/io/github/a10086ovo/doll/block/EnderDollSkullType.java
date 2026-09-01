package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum EnderDollSkullType implements SkullBlock.Type {
	ENDER_DOLL("ender_doll");

	private final String name;

	EnderDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
