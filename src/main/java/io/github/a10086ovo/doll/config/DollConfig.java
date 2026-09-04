package io.github.a10086ovo.doll.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import io.github.a10086ovo.doll.DollMod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 人偶模组外置配置。
 * <p>
 * 载荷整体置于 {@code config/dollmod/doll.json}（JSON，Gson 随原版内置、零新依赖）。
 * 凡索敌/觅途/跟随/各模式之阈值、时限、开关，皆尽归于此，服主与玩家可调而勿须重编。
 * <p>
 * 设计要点（一以贯之）：
 * <ul>
 *   <li>失格容错：文件缺失或 JSON 解析失败，一律回退本类字段初值（即当前硬码默认），日志示警，宁默不崩。</li>
 *   <li>首启自生：首次运行若无文件，自动写入一个带 {@code _comment} 注疏的完整默认档，免手创之劳。</li>
 *   <li>注疏：JSON 本无注释，于每值旁附 {@code _comment} 字段释其义、明其量；Gson 默认忽略未知键，故注疏不入运行时。</li>
 *   <li>运行期重载：{@link #reload()} 重读文件并覆写静态镜像（见 DollEntity.applyConfig / DollNavigator.applyConfig），
 *       由 /dollmod reload 命令驱动，改动即生效勿须重启。</li>
 * </ul>
 * <p>
 * 距离类字段一律存「基准值」（非平方），派生平方由应用侧 {@code v*v} 即时推算，免双值失和。
 */
public class DollConfig {

	private static final Logger LOGGER = DollMod.LOGGER;
	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		// 关键：doll.json 以"下划线"键书写（如 resume_distance、combat_leash），
		// 而 Java 字段为驼峰（resumeDistance、combatLeash）。Gson 默认按字段名精确匹配，
		// 不设此命名策略则下划线键全部静默不生效（配置形同虚设）——这里令二者相接。
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.create();
	private static volatile DollConfig root = new DollConfig();

	// ==================== 分块定义（字段初值即默认，未写入配置的键保持初值） ====================

	/** 跟随玩家：自然跟随距离 / 直行重算 / 卡住传送兜底。 */
	public Follow follow = new Follow();
	/** 战斗中玩家引力（跟随模式下战斗时，边打边朝主人挪）。 */
	public CombatLeash combatLeash = new CombatLeash();
	/** 近战战斗：攻速、命中范围、目标记忆。 */
	public Combat combat = new Combat();
	/** 闻声助战疆界：此界内主人攻敌/被袭，不问目见即生仇。 */
	public Assist assist = new Assist();
	/** 觅途：A* 重试限频与贴墙滑行。 */
	public Pathfind pathfind = new Pathfind();
	/** 网格寻路器（DollNavigator）的搜索规模与代价。 */
	public Navigator navigator = new Navigator();
	/** 近战模式：搜寻半径 / 追击上限 / 移速。 */
	public Melee melee = new Melee();
	/** 射手模式：弓/弩双武器之蓄力、冷却、理想射程、散布。 */
	public Ranged ranged = new Ranged();
	/** 幽匿人偶音波（蓄力/冷却/伤害/拉扯）。 */
	public Sonic sonic = new Sonic();
	/** 下界人偶烈焰弹（冷却/伤害）。 */
	public Fireball fireball = new Fireball();
	/** 末影人偶：龙息/瞬移处决/闪避。 */
	public Ender ender = new Ender();
	/** 海洋人偶：激光/水中垂直跟随/急迫。 */
	public Sea sea = new Sea();
	/** 耕种模式。 */
	public Farm farm = new Farm();
	/** 喂食模式。 */
	public Feed feed = new Feed();
	/** 砍树模式。 */
	public Chop chop = new Chop();
	/** 低优先级补种。 */
	public Sapling sapling = new Sapling();
	/** 插火把模式。 */
	public Torch torch = new Torch();
	/** 挖矿模式。 */
	public Mine mine = new Mine();
	/** 盾构机（挖矿隧道掘进）。 */
	public Tunnel tunnel = new Tunnel();
	/** 掉落物拾取。 */
	public DropPickup dropPickup = new DropPickup();
	/** 钓鱼模式。 */
	public Fish fish = new Fish();

	public static class Follow {
		public double resumeDistance = 3.5;
		public double directRecalc = 5.0;
		public float rotationSpeedPerTick = 15.0f;
		public int navRetryCooldownTicks = 40;
		public double stuckTeleportDistance = 6.0;
		public int stuckTeleportTicks = 60;
		public double stuckMoveEps = 0.03;
	}
	public static class CombatLeash {
		public double leashDistance = 10.0;
		public double maxLeashDistance = 20.0;
	}
	public static class Combat {
		public int cooldownTicks = 12;
		public double range = 3.0;
		public int targetRememberTicks = 200;
	}
	public static class Assist {
		public double hearRadius = 16.0;
	}
	public static class Pathfind {
		public int recalcBackoffTicks = 20;
		public double wallSlideOffset = 2.0;
	}
	public static class Navigator {
		public int maxNodes = 1024;
		public double nodeReach = 1.2;
		public int maxSafeFall = 3;
		public int upStepPenalty = 4;
	}
	public static class Melee {
		public double searchRange = 8.0;
		public double maxPursue = 8.0;
		public float moveSpeed = 0.13f;
	}
	public static class Ranged {
		public double searchRange = 18.0;
		public double maxPursue = 24.0;
		public int bowChargeTicks = 12;
		public int bowShootCooldown = 10;
		public int crossbowShootCooldown = 15;
		public double tooCloseDistance = 5.0;
		public double bowIdealMaxDistance = 15.0;
		public double crossbowIdealMaxDistance = 18.0;
		public float retreatSpeedFactor = 1.0f;
		public float bowShootPower = 1.6f;
		public float bowShootDivergence = 0.0f;
		public float crossbowShootDivergence = 0.0f;
		public int arrowTrackInterval = 2;
		public double arrowTrackMaxPredictTicks = 15.0;
		public double crossbowMultishotSpread = 0.6;
	}
	public static class Sonic {
		public int chargeTicks = 30;
		public int cooldownTicks = 80;
		public float boomDamage = 30.0f;
		public double pullStrength = 0.15;
	}
	public static class Fireball {
		public int cooldownTicks = 60;
		public float damage = 20.0f;
	}
	public static class Ender {
		public int breathCooldownTicks = 60;
		public int executeCooldownTicks = 1200;
		public float executeHealthThreshold = 0.25f;
		public float executeHealthThresholdAxe = 0.55f;
		public float dodgeChance = 0.67f;
		public float dodgeChanceProjectile = 1.0f;
		public int dodgeRadius = 4;
	}
	public static class Sea {
		public int laserChargeTicks = 30;
		public int laserCooldownTicks = 20;
		public float laserDamage = 20.0f;
		public double laserRange = 16.0;
		public double diveSpeed = 0.18;
		public double verticalDeadzone = 0.25;
		public int hasteLevel = 24;
	}
	public static class Farm {
		public double searchRange = 12.0;
		public double reach = 3.5;
		public int actionCooldown = 8;
		public int regionHalf = 4;
		public double anchorResetDistance = 24.0;
		public float moveSpeedFactor = 0.6f;
		public double navRecalc = 1.0;
	}
	public static class Feed {
		public double closeDistance = 2.0;
		public int actionCooldown = 15;
	}
	public static class Chop {
		public double searchRange = 16.0;
		public double reach = 3.5;
		public int actionCooldown = 8;
		public float moveSpeedFactor = 1.0f;
		public double navRecalc = 1.0;
		public int searchCooldown = 20;
		public int maxTreeBlocks = 4096;
		public int treeBlacklistTicks = 600;
		// 跟随为主轴：路过砍 + 主人静止小偏离（对齐 Mine 的"顺路捡"哲学）
		public int excursionMaxTicks = 100;
		public double excursionMaxDist = 12.0;
		public double followScanRange = 8.0;
		public double followMaxTargetDist = 5.0;
		public int ownerStillTicks = 80;
		public int movingDepartureMaxTicks = 80;
	}
	public static class Sapling {
		public double searchRange = 8.0;
		public double reach = 3.5;
		public int plantCooldown = 20;
	}
	public static class Torch {
		public double searchRange = 12.0;
		public double reach = 3.5;
		public int actionCooldown = 10;
		public int searchCooldown = 20;
		public int lightThreshold = 8;
		public double navRecalc = 1.0;
		public double followRange = 6.0;
	}
	public static class Mine {
		public double searchRange = 16.0;
		public double reach = 3.5;
		public int actionCooldown = 8;
		public int searchCooldown = 20;
		public int maxScanTargets = 8;
		public float moveSpeedFactor = 1.0f;
		public double navRecalc = 1.0;
		public int blacklistTicks = 100;
		public int maxChainBlocks = 512;
		public int excursionMaxTicks = 100;
		public double excursionMaxDist = 12.0;
		public double targetMaxDist = 24.0;
		public double followScanRange = 8.0;
		public double followMaxTargetDist = 5.0;
	}
	public static class Tunnel {
		public int actionCooldown = 8;
		public int lavaScanRange = 2;
	}
	public static class DropPickup {
		public int interval = 4;
		public double range = 3.0;
	}
	public static class Fish {
		public double searchRange = 12.0;
		public double reach = 3.5;
		public int actionCooldown = 40;
		public int searchCooldown = 30;
		public float moveSpeedFactor = 0.6f;
		public double navRecalc = 1.0;
		public int biteBaseTicks = 180;
		public int biteMaxTicks = 320;
		public int skipTicks = 300;
	}

	// ==================== 加载/存写 ====================

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("dollmod").resolve("doll.json");
	}

	/** 当前生效配置（静态镜像的应用源）。 */
	public static DollConfig get() {
		return root;
	}

	/** 补空：文件缺某块或某块为 null 时，以默认块回填，保证任何键缺失都不致 NPE。 */
	private static void ensureDefaults(DollConfig cfg) {
		if (cfg.follow == null) cfg.follow = new Follow();
		if (cfg.combatLeash == null) cfg.combatLeash = new CombatLeash();
		if (cfg.combat == null) cfg.combat = new Combat();
		if (cfg.assist == null) cfg.assist = new Assist();
		if (cfg.pathfind == null) cfg.pathfind = new Pathfind();
		if (cfg.navigator == null) cfg.navigator = new Navigator();
		if (cfg.melee == null) cfg.melee = new Melee();
		if (cfg.ranged == null) cfg.ranged = new Ranged();
		if (cfg.sonic == null) cfg.sonic = new Sonic();
		if (cfg.fireball == null) cfg.fireball = new Fireball();
		if (cfg.ender == null) cfg.ender = new Ender();
		if (cfg.sea == null) cfg.sea = new Sea();
		if (cfg.farm == null) cfg.farm = new Farm();
		if (cfg.feed == null) cfg.feed = new Feed();
		if (cfg.chop == null) cfg.chop = new Chop();
		if (cfg.sapling == null) cfg.sapling = new Sapling();
		if (cfg.torch == null) cfg.torch = new Torch();
		if (cfg.mine == null) cfg.mine = new Mine();
		if (cfg.tunnel == null) cfg.tunnel = new Tunnel();
		if (cfg.dropPickup == null) cfg.dropPickup = new DropPickup();
		if (cfg.fish == null) cfg.fish = new Fish();
	}

	/** 重载配置。成功返回 true；缺失/失格则回退默认并返回 false。 */
	public static synchronized boolean reload() {
		Path file = configPath();
		if (!Files.exists(file)) {
			writeDefault(file);
		}
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			DollConfig cfg = GSON.fromJson(text, DollConfig.class);
			if (cfg == null) {
				cfg = new DollConfig();
			}
			ensureDefaults(cfg);
			root = cfg;
			return true;
		} catch (Exception e) {
			LOGGER.warn("doll-mod 配置读取失败（{}），已回退默认值。请校验 {} 是否合法 JSON。", e.getMessage(), file);
			root = new DollConfig();
			return false;
		}
	}

	/** 首启或文件缺失时，写一张带注疏的完整默认档。 */
	private static void writeDefault(Path file) {
		try {
			Path dir = file.getParent();
			Files.createDirectories(dir);
			Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
			LOGGER.info("doll-mod 已生成默认配置：{}", file);
		} catch (IOException e) {
			LOGGER.warn("doll-mod 无法写默认配置（{}）。", e.getMessage());
		}
	}

	/** 默认档内容（含每值 _comment 注疏；数值与代码默认一致）。 */
	private static final String DEFAULT_JSON = """
{
  "_top": "人偶配置。距离类字段为基准值；方块内数字皆格，时间皆 tick（20tick=1秒）。调后 /dollmod reload 即生效。",
  "follow": {
    "_comment": "跟随玩家",
    "resume_distance": 3.5,
    "_c_resume": "单位: 格。超过此距离开始行走，贴近则停下",
    "direct_recalc": 5.0,
    "_c_direct": "单位: 格。直线模式下目标移动此量方重算视线/路径",
    "rotation_speed_per_tick": 15.0,
    "_c_rot": "单位: 度/tick。平滑转向速率",
    "nav_retry_cooldown_ticks": 40,
    "_c_retry": "撞墙强制重寻路冷却(2s)，防每tick重复A*",
    "stuck_teleport_distance": 6.0,
    "_c_tpdist": "单位: 格。距主人超此格才启用卡住传送",
    "stuck_teleport_ticks": 60,
    "_c_tpticks": "连续3s无法水平推进则传送拉回(贴地tick)",
    "stuck_move_eps": 0.03,
    "_c_eps": "每tick水平推进小于此格视作卡住"
  },
  "combat_leash": {
    "_comment": "战斗中玩家引力(跟随下边打边往主人挪)",
    "leash_distance": 10.0,
    "_c_ld": "单位: 格。此范围内无修正",
    "max_leash_distance": 20.0,
    "_c_mld": "单位: 格。此距离外引力最大"
  },
  "combat": {
    "_comment": "近战战斗",
    "cooldown_ticks": 12,
    "_c_cooldown": "攻速约每0.6s一次",
    "range": 3.0,
    "_c_range": "单位: 格。近战命中范围",
    "target_remember_ticks": 200,
    "_c_remember": "只攻击主人最近10s内攻击过的目标"
  },
  "assist": {
    "_comment": "闻声助战疆界",
    "hear_radius": 16.0,
    "_c_hear": "单位: 格。此界内主人攻敌/被袭则不问目见即生仇"
  },
  "pathfind": {
    "_comment": "觅途(核心智能调参)",
    "recalc_backoff_ticks": 20,
    "_c_recalc": "A*直抵不可达时至少隔此tick才再算",
    "wall_slide_offset": 2.0,
    "_c_slide": "单位: 格。直抵被挡时沿墙面偏行绕障"
  },
  "navigator": {
    "_comment": "网格寻路器DollNavigator",
    "max_nodes": 1024,
    "_c_nodes": "A*展开节点上界(防卡顿)",
    "node_reach": 1.2,
    "_c_nodereach": "单位: 格。到节点判定到达",
    "max_safe_fall": 3,
    "_c_fall": "单位: 格。人偶敢主动走下去的最大落差",
    "up_step_penalty": 4,
    "_c_uppen": "上坡台阶附加代价，偏好平路/下坡"
  },
  "melee": {
    "_comment": "近战模式",
    "search_range": 8.0,
    "_c_search": "单位: 格。目标搜寻半径",
    "max_pursue": 8.0,
    "_c_pursue": "单位: 格。追击上限，超出放弃",
    "move_speed": 0.13,
    "_c_speed": "移速(玩家疾跑速度)"
  },
  "ranged": {
    "_comment": "射手模式(弓/弩)",
    "search_range": 18.0,
    "_c_search": "单位: 格。搜寻半径",
    "max_pursue": 24.0,
    "_c_pursue": "单位: 格。追击上限",
    "bow_charge_ticks": 12,
    "_c_charge": "弓拉弓蓄力时长",
    "bow_shoot_cooldown": 10,
    "_c_bsc": "弓发射后冷却",
    "crossbow_shoot_cooldown": 15,
    "_c_csc": "弩发射后冷却",
    "too_close_distance": 5.0,
    "_c_tooclose": "单位: 格。低于此距离则后退",
    "bow_ideal_max_distance": 15.0,
    "_c_bideal": "单位: 格。弓理想射程上限",
    "crossbow_ideal_max_distance": 18.0,
    "_c_cideal": "单位: 格。弩理想射程上限",
    "retreat_speed_factor": 1.0,
    "_c_retreat": "后退移速因子(风筝怪物)",
    "bow_shoot_power": 1.6,
    "_c_power": "弓初速",
    "bow_shoot_divergence": 0.0,
    "_c_div": "弓散布(0=必中)",
    "crossbow_shoot_divergence": 0.0,
    "_c_cdiv": "弩散布(0=必中)",
    "arrow_track_interval": 2,
    "_c_track": "箭每N tick修正一次弹道",
    "arrow_track_max_predict_ticks": 15.0,
    "_c_predict": "预判移动最大飞行时间(tick)",
    "crossbow_multishot_spread": 0.6,
    "_c_ms": "多重射击三箭横向散开距离(格)"
  },
  "sonic": {
    "_comment": "幽匿人偶音波",
    "charge_ticks": 30,
    "_c_charge": "蓄力1.5s",
    "cooldown_ticks": 80,
    "_c_cd": "发射后冷却4s",
    "boom_damage": 30.0,
    "_c_dmg": "音波伤害(穿甲)",
    "pull_strength": 0.15,
    "_c_pull": "拉扯强度(距离比例)"
  },
  "fireball": {
    "_comment": "下界人偶烈焰弹",
    "cooldown_ticks": 60,
    "_c_cd": "发射后冷却3s",
    "damage": 20.0,
    "_c_dmg": "烈焰弹伤害"
  },
  "ender": {
    "_comment": "末影人偶(龙息/处决/闪避)",
    "breath_cooldown_ticks": 60,
    "_c_bcd": "龙息喷吐冷却3s",
    "execute_cooldown_ticks": 1200,
    "_c_ecd": "瞬移处决冷却60s",
    "execute_health_threshold": 0.25,
    "_c_e1": "基础斩杀线(25%)",
    "execute_health_threshold_axe": 0.55,
    "_c_e2": "持末影斧斩杀线(55%)",
    "dodge_chance": 0.67,
    "_c_dc": "近战受击无伤瞬移概率",
    "dodge_chance_projectile": 1.0,
    "_c_dcp": "投射物闪避概率",
    "dodge_radius": 4,
    "_c_dr": "瞬移落点搜索半径(格)"
  },
  "sea": {
    "_comment": "海洋人偶",
    "laser_charge_ticks": 30,
    "_c_lc": "激光蓄力1.5s",
    "laser_cooldown_ticks": 20,
    "_c_lcd": "激光冷却1s",
    "laser_damage": 20.0,
    "_c_ldmg": "激光魔法伤害(绕甲)",
    "laser_range": 16.0,
    "_c_lr": "单位: 格。激光最大射程",
    "dive_speed": 0.18,
    "_c_dive": "下潜竖直速度",
    "vertical_deadzone": 0.25,
    "_c_dz": "竖直跟随死区(避抖)",
    "haste_level": 24,
    "_c_haste": "水下速掘急迫等级(XXV)"
  },
  "farm": {
    "_comment": "耕种模式",
    "search_range": 12.0,
    "_c_search": "单位: 格。搜寻半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。工作距离",
    "action_cooldown": 8,
    "_c_ac": "锄/种/收之间停顿(tick)",
    "region_half": 4,
    "_c_half": "9x9农田半边长",
    "anchor_reset_distance": 24.0,
    "_c_anchor": "离农田锚点过远重新选址(格)",
    "move_speed_factor": 0.6,
    "_c_msf": "移动速度因子",
    "nav_recalc": 1.0,
    "_c_nav": "目标块变化此距离才重算路径"
  },
  "feed": {
    "_comment": "喂食模式",
    "close_distance": 2.0,
    "_c_cl": "单位: 格。喂食时贴近玩家距离",
    "action_cooldown": 15,
    "_c_ac": "喂食间隔(0.75s一口)"
  },
  "chop": {
    "_comment": "砍树模式",
    "search_range": 16.0,
    "_c_search": "单位: 格。找树半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。挥斧工作距离",
    "action_cooldown": 8,
    "_c_ac": "挥斧冷却",
    "move_speed_factor": 1.0,
    "_c_msf": "移动速度因子",
    "nav_recalc": 1.0,
    "_c_nav": "目标块变化重算路径",
    "search_cooldown": 20,
    "_c_sc": "找不到树后重搜间隔",
    "max_tree_blocks": 4096,
    "_c_mtree": "单棵树的原木上限",
    "tree_blacklist_ticks": 600,
    "_c_tbl": "整棵够不着的树拉黑时长",
    "excursion_max_ticks": 100,
    "_c_emax": "主人静止时单次小偏离上限(5s)",
    "excursion_max_dist": 12.0,
    "_c_emaxd": "小偏离时距主人最远距离(格)",
    "follow_scan_range": 8.0,
    "_c_fscan": "单位: 格。跟随时找树半径(只找顺路的)",
    "follow_max_target_dist": 5.0,
    "_c_fmdist": "单位: 格。主人行进中路过树原木距人偶硬上限",
    "owner_still_ticks": 80,
    "_c_ostill": "主人静止超此tick才允许小偏离(4s)",
    "moving_departure_max_ticks": 80,
    "_c_mdep": "主人行进中顺路偏离单次预算(4s)，静止偏离用excursion_max_ticks"
  },
  "sapling": {
    "_comment": "低优先级补种",
    "search_range": 8.0,
    "_c_search": "单位: 格。可种方块半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。种植工作距离",
    "plant_cooldown": 20,
    "_c_pc": "种完冷却(防连种)"
  },
  "torch": {
    "_comment": "插火把模式",
    "search_range": 12.0,
    "_c_search": "单位: 格。搜寻半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。插火把工作距离",
    "action_cooldown": 10,
    "_c_ac": "插火把冷却",
    "search_cooldown": 20,
    "_c_sc": "找不到暗处后重搜间隔",
    "light_threshold": 8,
    "_c_lt": "光照<=此值视为需插火把",
    "nav_recalc": 1.0,
    "_c_nav": "目标变化重算路径",
    "follow_range": 6.0,
    "_c_fr": "跟随时叠加半径(格)"
  },
  "mine": {
    "_comment": "挖矿模式",
    "search_range": 16.0,
    "_c_search": "单位: 格。BFS扫描最大半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。挖矿工作距离",
    "action_cooldown": 8,
    "_c_ac": "挖矿冷却",
    "search_cooldown": 20,
    "_c_sc": "找不到矿重搜间隔",
    "max_scan_targets": 8,
    "_c_mst": "BFS早停找到K个就停",
    "move_speed_factor": 1.0,
    "_c_msf": "移动速度因子",
    "nav_recalc": 1.0,
    "_c_nav": "目标变化重算路径",
    "blacklist_ticks": 100,
    "_c_bl": "不可达矿石拉黑时长(5s)",
    "max_chain_blocks": 512,
    "_c_mcb": "单次连锁破坏上限",
    "excursion_max_ticks": 100,
    "_c_emax": "单次离队上限(5s)",
    "excursion_max_dist": 12.0,
    "_c_emaxd": "离队距主人最远(格)",
    "target_max_dist": 24.0,
    "_c_tmd": "非跟随候选矿距人偶硬上限(格)",
    "follow_scan_range": 8.0,
    "_c_fsr": "跟随时BFS扫描半径(格)",
    "follow_max_target_dist": 5.0,
    "_c_fmtd": "跟随候选矿距人偶硬上限(格)"
  },
  "tunnel": {
    "_comment": "盾构机(挖矿隧道掘进)",
    "action_cooldown": 8,
    "_c_ac": "每N tick前进1格",
    "lava_scan_range": 2,
    "_c_lava": "岩浆探测半径(前后左右上下)-"
  },
  "drop_pickup": {
    "_comment": "掉落物拾取",
    "interval": 4,
    "_c_int": "每N tick扫一次",
    "range": 3.0,
    "_c_range": "单位: 格。拾取半径"
  },
  "fish": {
    "_comment": "钓鱼模式",
    "search_range": 12.0,
    "_c_search": "单位: 格。找水半径",
    "reach": 3.5,
    "_c_reach": "单位: 格。岸边抛竿工作距离",
    "action_cooldown": 40,
    "_c_ac": "收竿到下次抛竿间隔(2s)",
    "search_cooldown": 30,
    "_c_sc": "找不到水重搜间隔",
    "move_speed_factor": 0.6,
    "_c_msf": "移动速度因子",
    "nav_recalc": 1.0,
    "_c_nav": "目标变化重算路径",
    "bite_base_ticks": 180,
    "_c_bitebase": "咬钩基准等待(约9s)",
    "bite_max_ticks": 320,
    "_c_bitemax": "咬钩等待上限(随机浮动)",
    "skip_ticks": 300,
    "_c_skip": "不可达水域跳过时长(15s)"
  }
}
""";
}