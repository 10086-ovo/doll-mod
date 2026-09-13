package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：客户端打开人偶背包屏后请求服务端对当前菜单做一次<b>全量重同步</b>。
 *
 * <p>存在原因（Forge 26.2 移植回归，2026-09-13 诊断）：带附加数据的菜单服务端走 Forge 自家的
 * {@code OpenContainer} 包（{@code ctx.enqueueWork} 的 Forge 队列），而初始 81 槽内容走原版
 * {@code ClientboundContainerSetContentPacket}（Minecraft 自己的队列）。两条 FIFO 无顺序保证，
 * 内容包先到时 {@code handleContainerContent} 因 containerId 不匹配而<b>静默丢弃</b> →
 * 背包屏打开是空的，直到玩家点击触发服务端失配重发（蛋回收→再召唤后必现）。
 * Fabric 的扩展数据编在原版开屏包里（单队列），无此问题，故本包仅 Forge 侧使用。
 *
 * <p>请求-应答模式天然无竞态：客户端屏已建好才发，服务端处理时菜单一定已存在。
 */
public record RequestDollInvSyncPayload() implements CustomPacketPayload {

	public static final RequestDollInvSyncPayload INSTANCE = new RequestDollInvSyncPayload();

	public static final CustomPacketPayload.Type<RequestDollInvSyncPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_DOLL_INV_SYNC_ID)
	);

	public static final StreamCodec<ByteBuf, RequestDollInvSyncPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<RequestDollInvSyncPayload> type() {
		return TYPE;
	}
}
