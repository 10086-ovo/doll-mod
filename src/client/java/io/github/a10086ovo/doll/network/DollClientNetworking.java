package io.github.a10086ovo.doll.network;

import io.github.a10086ovo.doll.screen.GuideSearchScreen;
import io.github.a10086ovo.doll.network.payload.OpenDollControlPanelPayload;
import io.github.a10086ovo.doll.network.payload.RecallDollPayload;
import io.github.a10086ovo.doll.network.payload.RequestSearchPayload;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import io.github.a10086ovo.doll.network.payload.SelectDollModePayload;
import io.github.a10086ovo.doll.network.payload.ToggleMarkPayload;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import io.github.a10086ovo.doll.screen.DollControlScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DollClientNetworking {

	private DollClientNetworking() {
	}

	/** 客户端 -> 服务端：请求切换人偶行为模式。 */
	public static void sendSelectMode(int dollEntityId, int modeSlot08) {
		ClientPlayNetworking.send(new SelectDollModePayload(dollEntityId, modeSlot08));
	}

	/** 客户端 -> 服务端：请求召回指定人偶到玩家身边。 */
	public static void sendRecallDoll(String dollUuid, String dimensionName, int lastX, int lastY, int lastZ) {
		ClientPlayNetworking.send(new RecallDollPayload(dollUuid, dimensionName, lastX, lastY, lastZ));
	}

	/** 客户端 -> 服务端：请求向导人偶搜索某类目标（结构/群系/村庄统一入口）。
	 *  refresh=false 服务端有缓存时直接返回上次结果；refresh=true 强制以玩家当前位置为中心重搜。 */
	public static void sendSearch(int dollEntityId, int category, int targetIndex, boolean refresh) {
		ClientPlayNetworking.send(new RequestSearchPayload(dollEntityId, category, targetIndex, refresh));
	}

	/** 客户端 -> 服务端：打卡/取消打卡某搜索结果（fire-and-forget，本地已即时翻转 UI）。 */
	public static void sendToggleMark(int category, int targetIndex, int x, int z) {
		ClientPlayNetworking.send(new ToggleMarkPayload(category, targetIndex, x, z));
	}

	/** 注册服务端 -> 客户端的接收器（在客户端初始化时调用一次）。 */
	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(OpenDollControlPanelPayload.TYPE, (payload, context) -> {
			context.client().setScreenAndShow(new DollControlScreen(payload.dolls()));
		});
		ClientPlayNetworking.registerGlobalReceiver(UpdateDollSnapshotPayload.TYPE, (payload, context) -> {
			// 实时同步：服务端在 switchMode 成功后推送最新快照，命中控制面板选中行时刷新高亮。
			// 26.2 当前屏幕存于 Minecraft.gui.screen()（setScreenAndShow 内部委托给 Gui.setScreen）。
			var current = context.client().gui.screen();
			if (current instanceof DollControlScreen screen) {
				screen.applySnapshotUpdate(payload.snapshot());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SearchResultsPayload.TYPE, (payload, context) -> {
			// 搜索结果：路由到当前打开的统一直观人偶搜索屏，按其选中的 (分类, 目标) 应用结果。
			var current = context.client().gui.screen();
			if (current instanceof GuideSearchScreen screen) {
				screen.receiveResults(payload);
			}
		});
	}
}