package io.github.a10086ovo.doll;

public class DollModConstants {
	/**
	 * 资源命名空间（ResourceLocation 域 / 注册表命名空间），两个加载器统一为 "doll-mod"。
	 * 所有贴图、模型、配方、数据包路径均使用它。
	 */
	public static final String MOD_ID = "doll-mod";
	/**
	 * Forge 平台的 mod id（仅用于 @Mod / @Mod.EventBusSubscriber / mods.toml）。
	 * ⚠ Forge 校验正则 ^[a-z][a-z0-9_]{1,63}$ 不允许连字符，故无法使用 MOD_ID。
	 * 新增代码时：注册 ResourceLocation / 资源路径用 MOD_ID；平台声明用 FORGE_MOD_ID。
	 */
	public static final String FORGE_MOD_ID = "dollmod";
	public static final String DOLL_ENTITY_ID = "doll";
	public static final String DOLL_TIER1_EGG_ID = "doll_egg_s1";
	public static final String DOLL_TIER2_EGG_ID = "doll_egg_s2";
	public static final String DOLL_TIER3_EGG_ID = "doll_egg_s3";
	public static final String DOLL_TIER4_EGG_ID = "doll_egg_s4";
	public static final String DOLL_TIER5_EGG_ID = "doll_egg_s5";
	public static final String WARDEN_DOLL_EGG_ID = "warden_doll_egg";
	public static final String PALE_DOLL_EGG_ID = "pale_doll_egg";
	public static final String NETHER_DOLL_EGG_ID = "nether_doll_egg";
	public static final String PALE_DOLL_HEAD_ID = "pale_doll_head";
	public static final String PALE_DOLL_HEAD_ENTITY_ID = "pale_doll_head_entity";
	public static final String WARDEN_DOLL_HEAD_ID = "warden_doll_head";
	public static final String WARDEN_DOLL_HEAD_ENTITY_ID = "warden_doll_head_entity";
	public static final String NETHER_DOLL_HEAD_ID = "nether_doll_head";
	public static final String NETHER_DOLL_HEAD_ENTITY_ID = "nether_doll_head_entity";
	public static final String ENDER_DOLL_EGG_ID = "ender_doll_egg";
	public static final String ENDER_DOLL_HEAD_ID = "ender_doll_head";
	public static final String ENDER_DOLL_HEAD_ENTITY_ID = "ender_doll_head_entity";
	public static final String SEA_DOLL_EGG_ID = "sea_doll_egg";
	public static final String SEA_DOLL_HEAD_ID = "sea_doll_head";
	public static final String SEA_DOLL_HEAD_ENTITY_ID = "sea_doll_head_entity";
	public static final String FOREST_DOLL_EGG_ID = "forest_doll_egg";
	public static final String FOREST_DOLL_HEAD_ID = "forest_doll_head";
	public static final String FOREST_DOLL_HEAD_ENTITY_ID = "forest_doll_head_entity";
	public static final String GUIDE_DOLL_EGG_ID = "guide_doll_egg";
	public static final String GUIDE_DOLL_HEAD_ID = "guide_doll_head";
	public static final String GUIDE_DOLL_HEAD_ENTITY_ID = "guide_doll_head_entity";
	/** 统一搜索：客户端请求搜索某类目标的 C2S 通道。 */
	public static final String NETWORK_SEARCH_REQUEST_ID = "search_request";
	/** 统一搜索：服务端返回结果列表的 S2C 通道。 */
	public static final String NETWORK_SEARCH_RESULTS_ID = "search_results";
	/** 统一搜索：客户端打卡/取消打卡某目标的 C2S 通道。 */
	public static final String NETWORK_TOGGLE_MARK_ID = "toggle_search_mark";
	/** 统一搜索：服务端推送本世界全部结构注册键清单的 S2C 通道。 */
	public static final String NETWORK_STRUCTURE_CATALOG_ID = "structure_catalog";
	/** 全域索引：客户端请求开始/取消索引构建的 C2S 通道。 */
	public static final String NETWORK_INDEX_BUILD_REQUEST_ID = "index_build_request";
	/** 全域索引：服务端广播索引构建进度的 S2C 通道。 */
	public static final String NETWORK_INDEX_BUILD_PROGRESS_ID = "index_build_progress";
	/** 人偶背包：客户端打开背包屏后请求全量重同步的 C2S 通道（Forge OpenContainer/内容包双队列竞态的自愈）。 */
	public static final String NETWORK_DOLL_INV_SYNC_ID = "doll_inv_sync";
	/** 搜索打卡记忆：玩家 NBT 中存储已打卡目标（扁平 int[]），跨会话持久。 */
	public static final String SEARCH_MARKS_NBT_KEY = "guide_search_marks";
	public static final String SCULK_SHRINE_ID = "sculk_shrine";
	public static final String SCULK_SHRINE_ENTITY_ID = "sculk_shrine_entity";
	public static final String WARDEN_DOLL_ENTITY_ID = "wild_warden_doll";
	public static final String DOLL_SCREEN_HANDLER_ID = "doll_inventory";
	public static final String DOLL_BATON_ID = "doll_baton";
	public static final String DOLL_CONTROL_PANEL_ID = "doll_panel";
	public static final String ROCK_ANVIL_ID = "rock_anvil";
	public static final String CHIPPED_ROCK_ANVIL_ID = "chipped_rock_anvil";
	public static final String DAMAGED_ROCK_ANVIL_ID = "damaged_rock_anvil";
	public static final String NETWORK_MODE_SELECT_ID = "select_mode";
	public static final String ENDER_AXE_ID = "ender_axe";
	public static final String THROWN_ENDER_AXE_ID = "thrown_ender_axe";
	public static final String THORNS_SHIELD_ID = "thorns_shield";
	public static final String NETHER_SWORD_ID = "nether_sword";
	/** 飞行下界剑实体 ID（玩家蓄力召唤的守护飞剑） */
	public static final String NETHER_FLYING_SWORD_ID = "nether_flying_sword";
	public static final String GUIDE_BOOK_ID = "guide_book";
	public static final String GUIDE_BOOK_GIVEN_TAG = "guide_book_given";
	/** 向导的登山镐（向导人偶专属） */
	public static final String GUIDE_PICKAXE_ID = "guide_pickaxe";
	// ---- 海洋套装 ----
	public static final String SEA_HELMET_ID = "sea_helmet";
	public static final String SEA_CHESTPLATE_ID = "sea_chestplate";
	public static final String SEA_LEGGINGS_ID = "sea_leggings";
	public static final String SEA_BOOTS_ID = "sea_boots";
	// ---- 苍白弓 ----
	public static final String PALE_BOW_ID = "pale_bow";
}
