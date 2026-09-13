package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum PaleDollSkullType implements SkullBlock.Type {
	PALE_DOLL("pale_doll");

	private final String name;

	PaleDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
