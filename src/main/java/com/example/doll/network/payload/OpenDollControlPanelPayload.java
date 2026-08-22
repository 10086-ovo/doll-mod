package com.example.doll.network.payload;

import com.example.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：请求打开人偶控制面板，并携带所有存活人偶的快照列表。
 * 由指挥棒右键（非人偶目标：空气 / 方块 / 箱子 / 其他生物）触发。
 */
public record OpenDollControlPanelPayload(List<DollSnapshot> dolls) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenDollControlPanelPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "open_control_panel")
	);

	public static final StreamCodec<ByteBuf, OpenDollControlPanelPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.collection(ArrayList::new, DollSnapshot.STREAM_CODEC), OpenDollControlPanelPayload::dolls,
		OpenDollControlPanelPayload::new
	);

	@Override
	public Type<OpenDollControlPanelPayload> type() {
		return TYPE;
	}
}
