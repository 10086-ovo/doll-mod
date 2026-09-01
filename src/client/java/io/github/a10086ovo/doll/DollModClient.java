package io.github.a10086ovo.doll;

import io.github.a10086ovo.doll.client.model.EnderAxeModel;
import io.github.a10086ovo.doll.client.model.NetherFlyingSwordModel;
import io.github.a10086ovo.doll.client.renderer.entity.ThrownEnderAxeRenderer;
import io.github.a10086ovo.doll.client.renderer.entity.NetherFlyingSwordRenderer;
import io.github.a10086ovo.doll.client.renderer.entity.WildWardenDollRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

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
		EntityRenderers.register(DollMod.DOLL_ENTITY,
			ctx -> new net.minecraft.client.renderer.entity.player.AvatarRenderer(ctx, true));
		EntityRenderers.register(DollMod.WARDEN_DOLL_ENTITY,
			WildWardenDollRenderer::new);
		// 投掷末影斧：自定义 3D 模型渲染器（与三叉戟一致，不再是平面贴图）
		ModelLayerRegistry.registerModelLayer(ThrownEnderAxeRenderer.ENDER_AXE_LAYER,
			EnderAxeModel::createLayer);
		EntityRenderers.register(DollMod.THROWN_ENDER_AXE_ENTITY,
			ThrownEnderAxeRenderer::new);
		// 飞行地狱剑：自定义 3D 剑模型渲染器（脑后左悬浮 / 空闲 3D 翻滚 / 锁定剑刃直指+直线穿刺）
		ModelLayerRegistry.registerModelLayer(NetherFlyingSwordRenderer.NETHER_FLYING_SWORD_LAYER,
			NetherFlyingSwordModel::createLayer);
		EntityRenderers.register(DollMod.NETHER_FLYING_SWORD_ENTITY,
			NetherFlyingSwordRenderer::new);

		net.minecraft.client.gui.screens.DollScreenRegistration.register();
		io.github.a10086ovo.doll.network.DollClientNetworking.registerReceivers();

		// 指南书：右键打开自定义 GuideBookScreen（分类导航 + 多页布局）。
		// 数据由 GuideBookContent.get() 从 JSON 加载，Screen 直接消费 GuideBook 数据模型。
		io.github.a10086ovo.doll.item.GuideBookItem.openScreenAction = player -> {
			io.github.a10086ovo.doll.guide.GuideBookContent.invalidate();
			net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
				new io.github.a10086ovo.doll.screen.GuideBookScreen());
		};

		// 注册头颅方块 BER（自定义皮肤）
		BlockEntityRenderers.register(
			DollMod.WARDEN_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.WardenDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.PALE_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.PaleDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.NETHER_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.NetherDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.ENDER_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.EnderDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.SEA_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.SeaDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.FOREST_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.ForestDollHeadRenderer::new);
		BlockEntityRenderers.register(
			DollMod.GUIDE_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.GuideDollHeadRenderer::new);

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
