package io.github.a10086ovo.doll.network.payload;

import io.github.a10086ovo.doll.DollModConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C：服务端推送本世界<b>全部</b>结构的注册键（如 {@code "minecraft:stronghold"}），按注册键排序。
 *
 * <p>结构数据只在服务端动态注册表里，客户端不加载结构注册表，因此搜索界面无法自行枚举结构。
 * 玩家进服时服务端推送一次，客户端据此构建「结构 / 村庄」两页目标池（村庄 = {@code village_*} 前缀过滤）。
 * 序号取在<b>排序后的完整清单</b>中的位次，与服务端按同一清单解析一致。
 *
 * @param structureIds 本世界全部结构注册键，已按键名字符串排序
 */
public record StructureCatalogPayload(List<String> structureIds) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<StructureCatalogPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, DollModConstants.NETWORK_STRUCTURE_CATALOG_ID)
	);

	public static final StreamCodec<ByteBuf, StructureCatalogPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.collection(java.util.ArrayList::new, ByteBufCodecs.STRING_UTF8), StructureCatalogPayload::structureIds,
		StructureCatalogPayload::new
	);

	@Override
	public Type<StructureCatalogPayload> type() {
		return TYPE;
	}
}