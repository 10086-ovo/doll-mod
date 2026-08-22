package com.example.doll;

import com.example.doll.client.renderer.entity.WildWardenDollRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * 客户端入口：
 * - 用 vanilla AvatarRenderer 渲染人偶（玩家皮肤 + 盔甲）；
 * - DollEntity 通过 DollEntityClientMixin 在运行时实现 ClientAvatarEntity，
 *   所以这里以 raw 类型构造 AvatarRenderer（unchecked，运行时安全）。
 * - 屏幕注册由 DollScreenRegistration（同包普通类 + accessWidener 提权）完成。
 */
public class DollModClient implements ClientModInitializer {

	private static long lastClientTickNanos = System.nanoTime();

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(DollMod.DOLL_ENTITY,
			ctx -> new net.minecraft.client.renderer.entity.player.AvatarRenderer(ctx, true));
		EntityRendererRegistry.register(DollMod.WARDEN_DOLL_ENTITY,
			WildWardenDollRenderer::new);

		net.minecraft.client.gui.screens.DollScreenRegistration.register();
		com.example.doll.network.DollClientNetworking.registerReceivers();

		// 注册头颅方块 BER（自定义皮肤）
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.WARDEN_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.WardenDollHeadRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.PALE_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.PaleDollHeadRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.NETHER_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.NetherDollHeadRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.ENDER_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.EnderDollHeadRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.SEA_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.SeaDollHeadRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			DollMod.FOREST_DOLL_HEAD_BLOCK_ENTITY,
			com.example.doll.client.renderer.blockentity.ForestDollHeadRenderer::new);

		// 客户端 tick 看门狗：任何单 tick 超过 500ms 都打 WARN（用户反馈"用蛋生成人偶
		// 约 8 秒卡顿"。客户端 tick 与渲染共用渲染线程，若渲染卡死，此处 delta 会一并
		// 暴露总时长；结合服务端 [DollEgg] 计时与客户端实体构造日志即可定位卡在哪个环节）。
		lastClientTickNanos = System.nanoTime();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long now = System.nanoTime();
			long dtMs = (now - lastClientTickNanos) / 1_000_000L;
			lastClientTickNanos = now;
			if (dtMs > 500) {
				DollMod.LOGGER.warn("[DollClient] 客户端 tick 间隔 {}ms（>500ms 即卡顿现场，若接近 8000ms 请连同前后日志反馈）", dtMs);
			}
		});
	}
}
