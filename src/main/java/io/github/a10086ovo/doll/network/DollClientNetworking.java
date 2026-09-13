package io.github.a10086ovo.doll.network;

import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import io.github.a10086ovo.doll.network.payload.IndexBuildProgressPayload;
import io.github.a10086ovo.doll.network.payload.OpenDollControlPanelPayload;
import io.github.a10086ovo.doll.network.payload.RecallDollPayload;
import io.github.a10086ovo.doll.network.payload.RequestIndexBuildPayload;
import io.github.a10086ovo.doll.network.payload.RequestSearchPayload;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import io.github.a10086ovo.doll.network.payload.SelectDollModePayload;
import io.github.a10086ovo.doll.network.payload.StructureCatalogPayload;
import io.github.a10086ovo.doll.network.payload.ToggleMarkPayload;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * 网络客户端侧封装（公共类，不含任何客户端类引用）。
 * <p>
 * 发往服务端的包直接经 {@link PacketDistributor} 发送；
 * 服务端 → 客户端的收包处理委托给客户端初始化时注入的 {@link Consumer}，
 * 从而公共类可安全装载于专用服务端（处理逻辑实际只在客户端执行）。
 */
public final class DollClientNetworking {

	private DollClientNetworking() {
	}

	// ---- 客户端 -> 服务端 ----

	/** 客户端 -> 服务端：请求切换人偶行为模式。 */
	public static void sendSelectMode(int dollEntityId, int modeSlot08) {
		DollNetworking.sendToServer(new SelectDollModePayload(dollEntityId, modeSlot08));
	}

	/** 客户端 -> 服务端：请求召回指定人偶到玩家身边。 */
	public static void sendRecallDoll(String dollUuid, String dimensionName, int lastX, int lastY, int lastZ) {
		DollNetworking.sendToServer(new RecallDollPayload(dollUuid, dimensionName, lastX, lastY, lastZ));
	}

	/** 客户端 -> 服务端：请求向导人偶搜索某类目标（结构/群系/村庄统一入口）。
	 *  refresh=false 服务端有缓存时直接返回上次结果；refresh=true 强制以玩家当前位置为中心重搜。 */
	public static void sendSearch(int dollEntityId, int category, int targetIndex, boolean refresh) {
		DollNetworking.sendToServer(new RequestSearchPayload(dollEntityId, category, targetIndex, refresh));
	}

	/** 客户端 -> 服务端：打卡/取消打卡某搜索结果（fire-and-forget，本地已即时翻转 UI）。 */
	public static void sendToggleMark(int category, int targetIndex, int x, int z) {
		DollNetworking.sendToServer(new ToggleMarkPayload(category, targetIndex, x, z));
	}

	/** 客户端 -> 服务端：请求为<b>当前维度</b>构建「全域索引」；{@code cancel=true} 则取消正在进行的构建。 */
	public static void sendIndexBuild(int dollEntityId, boolean cancel) {
		DollNetworking.sendToServer(new RequestIndexBuildPayload(dollEntityId, cancel));
	}

	// ---- 客户端收包委托（消费者由客户端初始化时注入） ----

	/** 由服务端推送的本世界全部结构注册键清单（客户端无结构注册表）。仅客户端线程读写。 */
	private static volatile List<String> structureCatalog = List.of();

	/** 供搜索界面构建「结构/村庄」目标池使用（进服时由服务端推送一次）。 */
	public static List<String> getStructureCatalog() {
		return structureCatalog;
	}

	private static Consumer<List<DollSnapshot>> openPanelConsumer;
	private static Consumer<DollSnapshot> snapshotConsumer;
	private static Consumer<SearchResultsPayload> searchResultsConsumer;
	private static Consumer<IndexBuildProgressPayload> indexBuildProgressConsumer;

	/** 由客户端入口注入三个 clientbound 包的处理回调（实现含客户端屏幕类，仅存在于客户端）。 */
	public static void setClientHandlers(Consumer<List<DollSnapshot>> openPanel,
			Consumer<DollSnapshot> snapshot, Consumer<SearchResultsPayload> results) {
		openPanelConsumer = openPanel;
		snapshotConsumer = snapshot;
		searchResultsConsumer = results;
	}

	/** 由客户端入口注入全域索引构建进度回调（搜索屏据此刷新进度条）。 */
	public static void setIndexBuildProgressConsumer(Consumer<IndexBuildProgressPayload> consumer) {
		indexBuildProgressConsumer = consumer;
	}

	/** 服务端 -> 客户端：打开人偶控制面板。 */
	public static void handleOpenControlPanel(OpenDollControlPanelPayload payload, CustomPayloadEvent.Context context) {
		if (openPanelConsumer == null) return;
		context.enqueueWork(() -> openPanelConsumer.accept(payload.dolls()));
	}

	/** 服务端 -> 客户端：实时快照（切模式成功后刷新控制面板选中行高亮）。 */
	public static void handleUpdateSnapshot(UpdateDollSnapshotPayload payload, CustomPayloadEvent.Context context) {
		if (snapshotConsumer == null) return;
		context.enqueueWork(() -> snapshotConsumer.accept(payload.snapshot()));
	}

	/** 服务端 -> 客户端：搜索结果（路由到当前打开的统一直观人偶搜索屏）。 */
	public static void handleSearchResults(SearchResultsPayload payload, CustomPayloadEvent.Context context) {
		if (searchResultsConsumer == null) return;
		context.enqueueWork(() -> searchResultsConsumer.accept(payload));
	}

	/** 服务端 -> 客户端：本世界全部结构注册键清单（进服推送一次，缓存供搜索屏构建目标池）。 */
	public static void handleStructureCatalog(StructureCatalogPayload payload, CustomPayloadEvent.Context context) {
		List<String> ids = payload.structureIds();
		context.enqueueWork(() -> structureCatalog = ids);
	}

	/** 服务端 -> 客户端：全域索引构建进度广播（搜索屏打开时刷新进度条，其余情况丢弃）。 */
	public static void handleIndexBuildProgress(IndexBuildProgressPayload payload, CustomPayloadEvent.Context context) {
		if (indexBuildProgressConsumer == null) return;
		context.enqueueWork(() -> indexBuildProgressConsumer.accept(payload));
	}
}
