package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：客户端请求为<b>当前维度</b>构建「全域索引」（底图索引），或取消正在进行的构建。
 *
 * <p>由搜索屏「全域索引」按钮发出。服务端据此校验发起者身份（必须持有向导人偶且为其主人），
 * 然后开始/取消 {@link io.github.a10086ovo.doll.geo.GeoIndexBuildJob} 的任务。
 * 构建以<b>发起者当前位置</b>为中心——结果是按玩家距离排序的，锚在出生点会在玩家走远后失效。
 *
 * @param dollEntityId 发起搜索屏所用的向导人偶实体 ID（服务端凭此校验"确实持有向导人偶"）
 * @param cancel       true=请求取消当前构建；false=请求开始构建
 */
public record RequestIndexBuildPayload(int dollEntityId, boolean cancel) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RequestIndexBuildPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_INDEX_BUILD_REQUEST_ID)
	);

	public static final StreamCodec<ByteBuf, RequestIndexBuildPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RequestIndexBuildPayload::dollEntityId,
		ByteBufCodecs.BOOL, RequestIndexBuildPayload::cancel,
		RequestIndexBuildPayload::new
	);

	@Override
	public Type<RequestIndexBuildPayload> type() {
		return TYPE;
	}
}
