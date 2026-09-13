package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：客户端请求打卡/取消打卡某个搜索结果（fire-and-forget，本地即时翻转 UI）。
 *
 * @param category    搜索分类（{@link io.github.a10086ovo.doll.network.SearchCategory}）
 * @param targetIndex 该分类下的目标索引
 * @param x           目标 x 坐标
 * @param z           目标 z 坐标
 */
public record ToggleMarkPayload(int category, int targetIndex, int x, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleMarkPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_TOGGLE_MARK_ID)
	);

	public static final StreamCodec<ByteBuf, ToggleMarkPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ToggleMarkPayload::category,
		ByteBufCodecs.VAR_INT, ToggleMarkPayload::targetIndex,
		ByteBufCodecs.VAR_INT, ToggleMarkPayload::x,
		ByteBufCodecs.VAR_INT, ToggleMarkPayload::z,
		ToggleMarkPayload::new
	);

	@Override
	public Type<ToggleMarkPayload> type() {
		return TYPE;
	}
}