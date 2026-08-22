package com.example.doll.block;

import net.minecraft.world.level.block.SkullBlock;

public enum WardenDollSkullType implements SkullBlock.Type {
	WARDEN_DOLL("warden_doll");

	private final String name;

	WardenDollSkullType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}