package com.example.doll.network;

import com.example.doll.network.payload.OpenDollControlPanelPayload;
import com.example.doll.network.payload.RecallDollPayload;
import com.example.doll.network.payload.SelectDollModePayload;
import com.example.doll.network.payload.UpdateDollSnapshotPayload;
import com.example.doll.screen.DollControlScreen;
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
	}
}
