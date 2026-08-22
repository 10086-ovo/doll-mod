package com.example.doll.network.payload;

import com.example.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：请求切换人偶模式。
 * modeSlot08 为人偶背包第 5 行的格子编号（0-8）。
 */
public record SelectDollModePayload(int dollEntityId, int modeSlot08) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectDollModePayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_MODE_SELECT_ID)
	);

	public static final StreamCodec<ByteBuf, SelectDollModePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SelectDollModePayload::dollEntityId,
		ByteBufCodecs.VAR_INT, SelectDollModePayload::modeSlot08,
		SelectDollModePayload::new
	);

	@Override
	public Type<SelectDollModePayload> type() {
		return TYPE;
	}
}