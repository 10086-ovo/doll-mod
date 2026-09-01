package io.github.a10086ovo.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum GuideDollSkullType implements SkullBlock.Type {
	GUIDE_DOLL("guide_doll");

	private final String name;

	GuideDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
