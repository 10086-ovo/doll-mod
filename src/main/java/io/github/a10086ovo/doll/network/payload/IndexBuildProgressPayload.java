package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C：全域索引构建进度回报（服务端 → <b>全体在线玩家</b>，非单播给发起者）。
 *
 * <p><b>为什么是广播</b>：底图索引是服务端全局资源，谁建的、谁看都一样；且发起者一旦中途掉线，
 * 单播目标会永久失效、进度会卡死在"构建中"。相应地，「取消」也是全局的——任意持向导人偶的玩家
 * 都能停掉当前构建（服务端权威，见 {@code DollNetworking} 的接收器）。
 *
 * <p>进度是玩家主动触发才有的东西，所以必须可见：旧实现那条"正在后台预载世界结构索引…"
 * 的广播是开服自动弹的、既没进度也无法取消，正是"看不懂的提示"。
 *
 * @param percent 0~100
 * @param phase   阶段：0=结构 1=村庄 2=群系 3=村庄预确认 4=已结束
 *                （口径唯一来源：{@code GeoIndexBuildJob.PHASE_*}，勿在此另立编号）
 * @param state   状态：见下方常量
 */
public record IndexBuildProgressPayload(int percent, int phase, int state) implements CustomPacketPayload {

	/** 正在构建。 */
	public static final int STATE_RUNNING = 0;
	/** 已全部完成（数据已并入索引并落盘）。 */
	public static final int STATE_DONE = 1;
	/** 已被取消（已收集到的部分仍然有效，已并入索引）。 */
	public static final int STATE_CANCELLED = 2;
	/** 当前没有构建任务（用于界面初始同步）。 */
	public static final int STATE_IDLE = 3;

	public static final CustomPacketPayload.Type<IndexBuildProgressPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_INDEX_BUILD_PROGRESS_ID)
	);

	public static final StreamCodec<ByteBuf, IndexBuildProgressPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, IndexBuildProgressPayload::percent,
		ByteBufCodecs.VAR_INT, IndexBuildProgressPayload::phase,
		ByteBufCodecs.VAR_INT, IndexBuildProgressPayload::state,
		IndexBuildProgressPayload::new
	);

	@Override
	public Type<IndexBuildProgressPayload> type() {
		return TYPE;
	}
}
