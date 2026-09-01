package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：单只人偶状态变更（模式 / 跟随 / 盾构机）。
 * 由 {@code switchMode} 成功后发送给 owner，用于打开控制面板时高亮同步。
 * 客户端可忽略不在面板中的人偶。
 */
public record UpdateDollSnapshotPayload(DollSnapshot snapshot) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<UpdateDollSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "update_doll_snapshot")
	);

	public static final StreamCodec<ByteBuf, UpdateDollSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
		DollSnapshot.STREAM_CODEC, UpdateDollSnapshotPayload::snapshot,
		UpdateDollSnapshotPayload::new
	);

	@Override
	public Type<UpdateDollSnapshotPayload> type() {
		return TYPE;
	}
}