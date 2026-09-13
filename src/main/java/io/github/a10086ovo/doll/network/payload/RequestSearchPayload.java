package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：客户端请求向导人偶搜索某类目标。
 *
 * @param dollEntityId 目标人偶实体 ID
 * @param category       搜索分类（{@link io.github.a10086ovo.doll.network.SearchCategory}）
 * @param targetIndex    该分类下的目标索引（对应枚举 ordinal；结构/群系/村庄索引各自独立）
 * @param refresh        true=强制重新搜索（以玩家当前位置为中心）；false=优先返回上次缓存结果
 */
public record RequestSearchPayload(int dollEntityId, int category, int targetIndex, boolean refresh) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RequestSearchPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_SEARCH_REQUEST_ID)
	);

	public static final StreamCodec<ByteBuf, RequestSearchPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RequestSearchPayload::dollEntityId,
		ByteBufCodecs.VAR_INT, RequestSearchPayload::category,
		ByteBufCodecs.VAR_INT, RequestSearchPayload::targetIndex,
		ByteBufCodecs.BOOL, RequestSearchPayload::refresh,
		RequestSearchPayload::new
	);

	@Override
	public Type<RequestSearchPayload> type() {
		return TYPE;
	}
}