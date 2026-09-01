package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C：服务端返回某类目标的搜索结果列表。
 * <p>
 * 结果按与玩家水平距离由近及远排序，最多 {@link #MAX_RESULTS} 条。
 *
 * @param category    搜索分类（{@link io.github.a10086ovo.doll.network.SearchCategory}）
 * @param targetIndex 该分类下的目标索引
 * @param results     命中坐标列表（x, z 为方块坐标，marked 表示该目标已被玩家打卡）
 */
public record SearchResultsPayload(int category, int targetIndex, List<Entry> results) implements CustomPacketPayload {

	public static final int MAX_RESULTS = 10;

	public record Entry(int x, int z, boolean marked) {
		public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Entry::x,
			ByteBufCodecs.VAR_INT, Entry::z,
			ByteBufCodecs.BOOL, Entry::marked,
			Entry::new
		);
	}

	public static final CustomPacketPayload.Type<SearchResultsPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_SEARCH_RESULTS_ID)
	);

	public static final StreamCodec<ByteBuf, SearchResultsPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SearchResultsPayload::category,
		ByteBufCodecs.VAR_INT, SearchResultsPayload::targetIndex,
		ByteBufCodecs.collection(java.util.ArrayList::new, Entry.STREAM_CODEC), SearchResultsPayload::results,
		SearchResultsPayload::new
	);

	@Override
	public Type<SearchResultsPayload> type() {
		return TYPE;
	}
}