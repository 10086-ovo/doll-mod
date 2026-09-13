package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：请求召回指定人偶到玩家身边。
 * 携带人偶 UUID、最后所在维度与坐标，服务端直接据此强加载区块，
 * 不依赖服务端内存态注册表（避免跨线程/跨会话可见性问题）。
 */
public record RecallDollPayload(String dollUuid, String dimensionName, int lastX, int lastY, int lastZ) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RecallDollPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "recall_doll")
	);

	public static final StreamCodec<ByteBuf, RecallDollPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, RecallDollPayload::dollUuid,
		ByteBufCodecs.STRING_UTF8, RecallDollPayload::dimensionName,
		ByteBufCodecs.VAR_INT, RecallDollPayload::lastX,
		ByteBufCodecs.VAR_INT, RecallDollPayload::lastY,
		ByteBufCodecs.VAR_INT, RecallDollPayload::lastZ,
		RecallDollPayload::new
	);

	@Override
	public Type<RecallDollPayload> type() {
		return TYPE;
	}
}