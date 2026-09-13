package io.github.a10086ovo.doll;

import io.github.a10086ovo.doll.client.model.EnderAxeModel;
import io.github.a10086ovo.doll.client.model.NetherFlyingSwordModel;
import io.github.a10086ovo.doll.client.renderer.entity.ThrownEnderAxeRenderer;
import io.github.a10086ovo.doll.client.renderer.entity.NetherFlyingSwordRenderer;
import io.github.a10086ovo.doll.client.renderer.entity.WildWardenDollRenderer;
import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import io.github.a10086ovo.doll.network.payload.SearchResultsPayload;
import io.github.a10086ovo.doll.screen.DollControlScreen;
import io.github.a10086ovo.doll.screen.GuideSearchScreen;
import io.github.a10086ovo.doll.screen.DollInventoryScreen;
import io.github.a10086ovo.doll.screen.DollScreenHandler;
import io.github.a10086ovo.doll.item.GuideBookItem;
import io.github.a10086ovo.doll.network.DollClientNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/**
 * 客户端初始化：
 * - 用 vanilla AvatarRenderer 渲染人偶（玩家皮肤 + 盔甲）；
 * - DollEntity 通过 DollEntityClientMixin 在运行时实现 ClientAvatarEntity，
 *   所以这里以 raw 类型构造 AvatarRenderer（unchecked，运行时安全）。
 * - 屏幕注册改用原版 {@code MenuScreens.register}（26.2 Forge 无 RegisterMenuScreensEvent）。
 */
@Mod.EventBusSubscriber(modid = DollModConstants.FORGE_MOD_ID, value = Dist.CLIENT)
public class DollModClient {

	private static long lastClientTickNanos = System.nanoTime();

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			// 指南书：右键打开自定义 GuideBookScreen（分类导航 + 多页布局）。
			GuideBookItem.openScreenAction = player -> {
				io.github.a10086ovo.doll.guide.GuideBookContent.invalidate();
				net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
					new io.github.a10086ovo.doll.screen.GuideBookScreen());
			};

			// 客户端收包处理器（实现含客户端屏幕类，仅存在于客户端）
			DollClientNetworking.setClientHandlers(
				(List<DollSnapshot> dolls) -> net.minecraft.client.Minecraft.getInstance()
					.setScreenAndShow(new DollControlScreen(dolls)),
				(DollSnapshot snap) -> {
					var current = net.minecraft.client.Minecraft.getInstance().gui.screen();
					if (current instanceof DollControlScreen screen) {
						screen.applySnapshotUpdate(snap);
					}
				},
				(SearchResultsPayload payload) -> {
					var current = net.minecraft.client.Minecraft.getInstance().gui.screen();
					if (current instanceof GuideSearchScreen screen) {
						screen.receiveResults(payload);
					}
				});

			// 全域索引构建进度：路由到当前打开的搜索屏（按钮就地变进度条，可点取消）
			DollClientNetworking.setIndexBuildProgressConsumer(
				(io.github.a10086ovo.doll.network.payload.IndexBuildProgressPayload payload) -> {
					var current = net.minecraft.client.Minecraft.getInstance().gui.screen();
					if (current instanceof GuideSearchScreen screen) {
						screen.receiveIndexProgress(payload);
					}
				});

			// 注册人偶物品栏菜单屏幕：26.2 Forge 无 RegisterMenuScreensEvent，改用原版 MenuScreens.register。
			net.minecraft.client.gui.screens.MenuScreens.register(DollMod.DOLL_SCREEN_HANDLER,
				(DollScreenHandler menu, net.minecraft.world.entity.player.Inventory inventory,
					net.minecraft.network.chat.Component title) -> new DollInventoryScreen(menu, inventory, title));

			// 荆棘盾牌特殊渲染器防御性补注册：
			// items/thorns_shield.json 里 "type": "doll-mod:thorns_shield" 依赖 SpecialModelRenderers
			// 的 ID_MAPPER；正常情况下由 SpecialModelRenderersMixin 在 bootstrap 尾部注入，
			// 万一 mixin 时机不巧（bootstrap 先于配置注册执行），这里用反射再 put 一次（幂等覆盖）。
			registerThornsShieldSpecialRenderer();
		});
	}

	/** 反射向 SpecialModelRenderers.ID_MAPPER 注册荆棘盾牌渲染器（幂等，失败只记警告不抛错）。 */
	private static void registerThornsShieldSpecialRenderer() {
		try {
			Class<?> holders = Class.forName("net.minecraft.client.renderer.special.SpecialModelRenderers");
			java.lang.reflect.Field field = holders.getDeclaredField("ID_MAPPER");
			field.setAccessible(true);
			Object mapper = field.get(null);
			Object key = net.minecraft.resources.Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "thorns_shield");
			Object codec = io.github.a10086ovo.client.renderer.special.ThornsShieldSpecialRenderer.Unbaked.MAP_CODEC;
			mapper.getClass().getMethod("put", Object.class, Object.class).invoke(mapper, key, codec);
			DollMod.LOGGER.info("[DollClient] thorns_shield 特殊渲染器已注册（防御路径）");
		} catch (Throwable t) {
			DollMod.LOGGER.warn("[DollClient] thorns_shield 特殊渲染器防御注册失败（mixin 路径可能已生效）：{}", t.toString());
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@SubscribeEvent
	public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(DollMod.DOLL_ENTITY,
			ctx -> new net.minecraft.client.renderer.entity.player.AvatarRenderer(ctx, true));
		event.registerEntityRenderer(DollMod.WARDEN_DOLL_ENTITY,
			WildWardenDollRenderer::new);
		event.registerEntityRenderer(DollMod.THROWN_ENDER_AXE_ENTITY,
			ThrownEnderAxeRenderer::new);
		event.registerEntityRenderer(DollMod.NETHER_FLYING_SWORD_ENTITY,
			NetherFlyingSwordRenderer::new);

		event.registerBlockEntityRenderer(
			DollMod.WARDEN_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.WardenDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.PALE_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.PaleDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.NETHER_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.NetherDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.ENDER_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.EnderDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.SEA_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.SeaDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.FOREST_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.ForestDollHeadRenderer::new);
		event.registerBlockEntityRenderer(
			DollMod.GUIDE_DOLL_HEAD_BLOCK_ENTITY,
			io.github.a10086ovo.doll.client.renderer.blockentity.GuideDollHeadRenderer::new);
	}

	@SubscribeEvent
	public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(ThrownEnderAxeRenderer.ENDER_AXE_LAYER, EnderAxeModel::createLayer);
		event.registerLayerDefinition(NetherFlyingSwordRenderer.NETHER_FLYING_SWORD_LAYER,
			NetherFlyingSwordModel::createLayer);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
		long now = System.nanoTime();
		long dtMs = (now - lastClientTickNanos) / 1_000_000L;
		lastClientTickNanos = now;
		if (dtMs > 500) {
			DollMod.LOGGER.warn("[DollClient] 客户端 tick 间隔 {}ms（>500ms 即卡顿现场，若接近 8000ms 请连同前后日志反馈）", dtMs);
		}
	}
}
