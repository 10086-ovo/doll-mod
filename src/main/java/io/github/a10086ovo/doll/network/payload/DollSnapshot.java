package io.github.a10086ovo.doll.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * 控制面板中一只人偶的只读快照（服务端 → 客户端）。
 *
 * <p>注意：切模式只用 {@link #entityId()}（玩家当前维度的实体 id），因此
 * 跨维度的人偶也可以显示在面板里（状态标维度名，如"下界"），但点击切换
 * 会被服务端 {@code doll_unreachable} 拒绝——与远程切模式的既有语义一致。
 *
 * @param entityId        实体 id（当前维度定位用，-1 表示离线人偶）
 * @param uuid            人偶 UUID（用于召回等需要跨维度/跨加载状态的定位）
 * @param name            显示名（自定义名，无则"人偶"）
 * @param level           蛋等级 0/1/2（Ⅰ/Ⅱ/Ⅲ）
 * @param activeMode      当前模式索引（-1 = 空闲）
 * @param followEnabled   跟随开关
 * @param isTunneling     盾构机掘进中
 * @param inSameDimension 是否与玩家同维度
 * @param distanceSqr     同维度时的玩家距离平方（异维度为 {@link Integer#MAX_VALUE}）
 * @param dimensionName   人偶所在维度的 ResourceLocation path（如 overworld / the_nether / the_end）
 * @param lastX           最后已知 X 坐标（离线人偶定位用）
 * @param lastY           最后已知 Y 坐标
 * @param lastZ           最后已知 Z 坐标
 */
public record DollSnapshot(int entityId, String uuid, String name, int level, int activeMode,
		boolean followEnabled, boolean isTunneling,
		boolean inSameDimension, int distanceSqr, String dimensionName,
		int lastX, int lastY, int lastZ) {

	public static final StreamCodec<ByteBuf, DollSnapshot> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public DollSnapshot decode(ByteBuf buf) {
			return new DollSnapshot(
				ByteBufCodecs.VAR_INT.decode(buf),
				decodeUuid(buf),
				ByteBufCodecs.STRING_UTF8.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.STRING_UTF8.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf)
			);
		}

		@Override
		public void encode(ByteBuf buf, DollSnapshot v) {
			ByteBufCodecs.VAR_INT.encode(buf, v.entityId);
			encodeUuid(buf, v.uuid);
			ByteBufCodecs.STRING_UTF8.encode(buf, v.name);
			ByteBufCodecs.VAR_INT.encode(buf, v.level);
			ByteBufCodecs.VAR_INT.encode(buf, v.activeMode);
			ByteBufCodecs.BOOL.encode(buf, v.followEnabled);
			ByteBufCodecs.BOOL.encode(buf, v.isTunneling);
			ByteBufCodecs.BOOL.encode(buf, v.inSameDimension);
			ByteBufCodecs.VAR_INT.encode(buf, v.distanceSqr);
			ByteBufCodecs.STRING_UTF8.encode(buf, v.dimensionName);
			ByteBufCodecs.VAR_INT.encode(buf, v.lastX);
			ByteBufCodecs.VAR_INT.encode(buf, v.lastY);
			ByteBufCodecs.VAR_INT.encode(buf, v.lastZ);
		}

		/** UUID 用 16 字节二进制传输（+1 字节空标志），替代 STRING_UTF8 的 36 字节，节省带宽。 */
		private static void encodeUuid(ByteBuf buf, String uuid) {
			if (uuid == null || uuid.isEmpty()) {
				buf.writeBoolean(false);
			} else {
				buf.writeBoolean(true);
				UUID u = UUID.fromString(uuid);
				buf.writeLong(u.getMostSignificantBits());
				buf.writeLong(u.getLeastSignificantBits());
			}
		}

		private static String decodeUuid(ByteBuf buf) {
			if (!buf.readBoolean()) return "";
			return new UUID(buf.readLong(), buf.readLong()).toString();
		}
	};
}