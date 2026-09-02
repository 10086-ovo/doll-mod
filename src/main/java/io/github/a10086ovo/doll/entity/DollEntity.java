package io.github.a10086ovo.doll.entity;

import io.github.a10086ovo.doll.DollMod;
import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.inventory.DollInventory;
import io.github.a10086ovo.doll.item.DollSpawnEggItem;
import io.github.a10086ovo.doll.item.EnderAxeItem;
import io.github.a10086ovo.doll.item.NetherSwordItem;
import io.github.a10086ovo.doll.item.SeaArmorItem;
import io.github.a10086ovo.doll.mode.DollMode;
import io.github.a10086ovo.doll.network.payload.DollSnapshot;
import io.github.a10086ovo.doll.network.payload.UpdateDollSnapshotPayload;
import io.github.a10086ovo.doll.screen.DollScreenHandler;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import io.github.a10086ovo.doll.config.DollConfig;
import io.github.a10086ovo.doll.entity.DollNavigator;
import io.github.a10086ovo.doll.item.PaleBowItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class DollEntity extends Avatar {

	public static final String DOLL_UUID_NBT_KEY = "DollUuid";
	public static final String ACTIVE_MODE_NBT_KEY = "ActiveMode";
	public static final String FOLLOW_ENABLED_NBT_KEY = "FollowEnabled";
	public static final String PROFILE_NAME_NBT_KEY = "ProfileName";
	public static final String OWNER_UUID_NBT_KEY = "OwnerUuid";
	public static final String DOLL_LEVEL_NBT_KEY = "DollLevel";
	public static final String WORK_AREA_NBT_KEY = "WorkArea";
	public static final String TUNNEL_DIR_NBT_KEY = "TunnelDir";
	public static final String TUNNEL_ENTRY_NBT_KEY = "TunnelEntry";
	public static final String TUNNEL_ACTIVE_NBT_KEY = "TunnelActive";
	public static final String DOLL_VARIANT_NBT_KEY = "DollVariant";

	/** 作业区（指挥棒两点选区结果）：归一化后的两个对角，均为 null 表示未设置。随实体存档/蛋回收携带；死亡即失。 */
	private BlockPos workAreaMin;
	private BlockPos workAreaMax;
	/** 前往作业区的寻路目标（用于判断是否需要重算路径）。 */
	private Vec3 workAreaNavTarget;
	/** 盾构机配置：掘进方向（水平）+ 入口截面下层方块。均为 null 表示未配置；死亡即失。 */
	private Direction tunnelDir;
	private BlockPos tunnelEntry;
	/** 盾构机掘进中（停止条件触发后为 false，指挥棒右键人偶恢复）。 */
	private boolean tunneling;
	private int tunnelActionCooldown = 0;
	private final DollInventory inventory = new DollInventory(this);
	private ResolvableProfile profile = ResolvableProfile.createUnresolved("Doll");

	protected final DollNavigator navigator = new DollNavigator(this);
	private Vec3 lastNavTarget;

	// ---- 客户端同步数据：模式状态 ----
	private static final EntityDataAccessor<Integer> DATA_ACTIVE_MODE =
		SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_FOLLOW_ENABLED =
		SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DOLL_LEVEL =
		SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_IS_WARDEN_VARIANT =
		SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DOLL_VARIANT =
		SynchedEntityData.defineId(DollEntity.class, EntityDataSerializers.INT);

	// ---- 自动进食（startUsingItem 使用动画：持物抬臂约 32 tick + 咀嚼音效/粒子）----
	private static final int EAT_COOLDOWN_TICKS = 40;
	private int eatCooldown = 0;
	private int eatSourceSlot = -1;   // 进食期间主手临时指向的食物槽；-1 = 未进食
	// Avatar/LivingEntity 没有 FoodData，原版 FoodProperties.onConsume 对非 Player 实体跳过 nutrition 处理，
	// 食物营养值不会自动转为血量。这里在 completeUsingItem 中手动读取 nutrition 并暂存，
	// 在 tick 的禁用自然回血逻辑之后统一应用，避免被回退代码吞掉。
	private float pendingFoodHeal = 0.0f;

	// ---- 播种挥动动画的手持渲染 ----
	// 播种是瞬时 swing（约 6 tick），期间主手应显示种子而非模式工具（锄头）。
	// plantSourceSlot 指向种子槽；plantSeedHandTicks 递减至 0 后恢复 findToolForMode。
	private static final int PLANT_SWING_TICKS = 6;
	private int plantSourceSlot = -1;
	private int plantSeedHandTicks = 0;

	// ---- 射手模式（RANGED）：弓/弩双武器，与目标保持距离远程射击 ----
	// 弓：拉弓蓄力后发射（12 tick 前摇），每次射击消耗 1 支箭（无限附魔不消耗），
	//     伤害继承原版附魔（力量/火焰/击退/穿透）；蓄力与发射均不锁死射程，边退边射。
	// 弩：先装填（消耗 1 支箭，多重射击消耗 3 支）再发射，快装附魔缩短装填时间；
	//     装填不要求射程，边退边装，装填完成在射程内即射。
	private static double RANGED_SEARCH_RANGE = 18.0;           // 搜寻半径（比近战远）
	private static double RANGED_MAX_PURSUE_DISTANCE_SQR = 24.0 * 24.0; // 追击上限
	private static int BOW_CHARGE_TICKS = 12;                  // 弓拉弓蓄力时长（原版玩家 20 tick，人偶训练有素更快）
	private static int BOW_SHOOT_COOLDOWN_TICKS = 10;          // 弓发射后冷却
	private static int CROSSBOW_SHOOT_COOLDOWN_TICKS = 15;     // 弩发射后冷却（装填本身是主要间隔）
	private static double RANGED_TOO_CLOSE_DISTANCE_SQR = 5.0 * 5.0;    // 低于此距离则后退
	private static double RANGED_BOW_IDEAL_MAX_DISTANCE_SQR = 15.0 * 15.0;     // 弓理想射程上限
	private static double RANGED_CROSSBOW_IDEAL_MAX_DISTANCE_SQR = 18.0 * 18.0; // 弩理想射程上限（箭速更快，可站更远）
	private static float RANGED_RETREAT_SPEED_FACTOR = 1.0f;   // 后退全速（风筝怪物，避免相对速度被反超）
	private static float BOW_SHOOT_POWER = 1.6f;                // 弓初速（与原版骷髅一致）
	private static float BOW_SHOOT_DIVERGENCE = 0.0f;           // 弓散布（归零：消除随机脱靶）
	private static float CROSSBOW_SHOOT_DIVERGENCE = 0.0f;      // 弩散布（归零：消除随机脱靶）
	// ---- 精确射击：预判 + 飞行中追踪，保证每发必中 ----
	private static int ARROW_TRACK_INTERVAL = 2;                // 箭每 N tick 修正一次弹道方向
	private static double ARROW_TRACK_MAX_PREDICT_TICKS = 15.0; // 预判目标移动的最大飞行时间（tick）
	private static double CROSSBOW_MULTISHOT_SPREAD = 0.6;      // 多重射击三支箭的横向散开距离（格）
	private LivingEntity rangedTarget;
	private int crossbowLoadTicks = -1;  // 弩装填进度：-1=未装填，0..时长-1=装填中，到达时长后完成装填
	private int bowChargeTicks = -1;     // 弓拉弓进度：-1=未拉弓，0..时长-1=拉弓中，>=时长=已拉满等待发射
	private final Map<Integer, Integer> trackedArrows = new HashMap<>(); // 已发射箭矢：箭实体ID -> 目标实体ID（服务端追踪）

	// ---- 跟随玩家（自然跟随距离 + headYaw 同步，卡住传送兜底） ----
	private static double FOLLOW_RESUME_DISTANCE_SQR = 3.5 * 3.5;    // 超过则开始行走，贴近则停下
	private static double FOLLOW_DIRECT_RECALC_SQR = 5.0 * 5.0;      // 直线模式下目标移动 5 格才重算 LOS / 路径
	private static float ROTATION_SPEED_PER_TICK = 15.0f;             // 平滑转向：每 tick 最多转 15 度
	// ---- 跟随卡住兜底：玩家在高处/隔墙时寻路失败会顶墙原地踏步，超时传送拉回 ----
	private static int NAV_RETRY_COOLDOWN_TICKS = 40;                 // 撞墙后强制重寻路的冷却（2 秒），防每 tick 重复 1024 节点 A*
	private static double FOLLOW_STUCK_TELEPORT_DISTANCE_SQR = 6.0 * 6.0; // 距主人超 6 格才启用卡住传送（太近直接走到）
	private static int STUCK_TELEPORT_TICKS = 60;                     // 连续 3 秒（贴地 tick）无法水平推进则传送
	private static double STUCK_MOVE_EPS_SQR = 0.03 * 0.03;           // 每 tick 水平推进 < 0.03 格视为卡住
	// ---- 战斗中玩家引力（跟随模式下战斗时，边打边往主人方向挪） ----
	private static double COMBAT_LEASH_DISTANCE = 10.0;               // 牵引半径：10 格内无修正
	private static double COMBAT_LEASH_DISTANCE_SQR = COMBAT_LEASH_DISTANCE * COMBAT_LEASH_DISTANCE;
	private static double COMBAT_MAX_LEASH_DISTANCE = 20.0;           // 满强度距离：20 格外引力最大
	private boolean directMoveMode = false;                                  // 当前是否处于直线跟随模式
	private int navRetryCooldown = 0;                                        // 撞墙后 A* 重试冷却
	private int stuckTicks = 0;                                              // 跟随卡住连续计数
	private Vec3 lastStuckPos;                                               // 卡住检测的位移采样点
	private UUID ownerUuid;

	// ---- idle 动画 ----
	private int idleTickCounter = 0;      // 空闲计数器（静止时头部随机微动）

	// ---- 音符盒语音系统 ----
	// 人偶用音符盒 harp 音色"说话"：通用音只保留两种——
	// 噔↓噔↑（低→高）= 成功/确认；噔↑噔↓（高→低）= 失败/无法执行。
	// 模式切换播专属主题旋律（见 MODE_MELODIES），听声即可分辨模式。
	// 音高编号 0-12 对应 F#3 到 F#4（与 MC 音符盒 note 0-12 一致），
	// pitch = 2^((note-12)/12)。
	private static final int N_F3 = 0;    // F#3 — 低音
	private static final int N_A3 = 3;    // A3 — 高音

	/**
	 * 8 种模式的专属主题旋律（下标 = DollMode.getIndex()，与枚举顺序一致）。
	 * 每个模式的高音轮廓（上行/下行/拱形/重音+跳进）刻意不同，这是听感辨识度的来源；
	 * 统一落在 F# 调内（F#3=0, C#4=7, F#4=12），多个人偶同时切换也不会互相冲突刺耳。
	 */
	private static final int[][] MODE_MELODIES = {
		{0, 7, 12},     // 0 近战：低-高-八度跳进，"抽刀"棱角
		{0, 3, 7, 12},  // 1 射手：四音上行，"拉弓蓄力"
		{7, 10, 12},    // 2 耕种：轻盈三连上行，"撒种"
		{3, 0, 3},      // 3 喂食：中-低-中拱形，"安抚"
		{0, 0, 7},      // 4 砍树：重音重复+跳高，"砍-砍-咔"
		{0, -2, 0},     // 5 挖矿：低音下潜回弹，"掘进"
		{7, 12},        // 6 插火把：高亮双音四度，"啪-亮"
		{12, 7, 3},     // 7 钓鱼：高到低波浪下坠，"抛竿等待"
	};

	/**
	 * 8 种模式的专属音色（下标同样 = DollMode.getIndex()）。
	 * 原版音符盒"音色由下方方块决定"：木头=贝斯、石头=底鼓、玻璃=踩镲、
	 * 金块=铃铛、泥土=长笛、浮冰=钟琴、铁块=电钢琴、黏土=叮……每个模式按
	 * 语义配一种乐器，与旋律叠加成"音色 + 高音轮廓"双维度辨识，听一声即知模式。
	 * 26.2 中 NOTE_BLOCK_* 均为 Holder$Reference<SoundEvent>，须 .value() 解引用。
	 */
	private static final SoundEvent[] MODE_SOUNDS = {
		SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), // 0 近战：铁块电钢琴，金属利刃
		SoundEvents.NOTE_BLOCK_PLING.value(),          // 1 射手：黏土叮，弓弦弹拨
		SoundEvents.NOTE_BLOCK_FLUTE.value(),          // 2 耕种：泥土长笛，田园
		SoundEvents.NOTE_BLOCK_BELL.value(),           // 3 喂食：金块铃铛，温和
		SoundEvents.NOTE_BLOCK_BASS.value(),           // 4 砍树：木头贝斯，斧砍厚重
		SoundEvents.NOTE_BLOCK_BASEDRUM.value(),       // 5 挖矿：石头底鼓，凿石
		SoundEvents.NOTE_BLOCK_HAT.value(),            // 6 插火把：玻璃踩镲，火花清脆
		SoundEvents.NOTE_BLOCK_CHIME.value(),          // 7 钓鱼：浮冰钟琴，水波粼粼
	};
	/** 取消模式（回到空闲）复位音：默认竖琴 = 回到中性状态。 */
	private static final SoundEvent IDLE_SOUND = SoundEvents.NOTE_BLOCK_HARP.value();
	/** 取消模式（回到空闲）的旋律：高到低收工下坠。 */
	private static final int[] IDLE_MELODY = {12, 7, 0};

	// ---- 装备槽位（对应 DollScreenHandler 人偶物品栏的槽号）----
	public static final int EQUIP_HEAD_SLOT = 1;
	public static final int EQUIP_CHEST_SLOT = 2;
	public static final int EQUIP_LEGS_SLOT = 3;
	public static final int EQUIP_FEET_SLOT = 4;
	public static final int OFFHAND_SLOT = 6;

	// ---- 战斗（复用官方 stabAttack 统一近战 + 玩家式攻速/挥臂） ----
	private static int ATTACK_COOLDOWN_TICKS = 12;     // 攻速：约每 0.6 秒一次（接近玩家连击节奏）
	private static double ATTACK_RANGE_SQR = 3.0 * 3.0; // 近战命中范围
	private static int TARGET_REMEMBER_TICKS = 200;    // 只攻击主人最近 10 秒内攻击过的目标
	/** 闻声助战疆界（格）：此界内主人攻敌或被袭，不问目见，傀儡即对彼敌生仇。 */
	private static double ASSIST_HEAR_RADIUS = 16.0;
	private static double ASSIST_HEAR_RADIUS_SQR = ASSIST_HEAR_RADIUS * ASSIST_HEAR_RADIUS;
	/** 寻路失败重试限频：A* 直抵不可达时，至少隔此 tick 数才再算（防每 tick 反复寻） */
	private static int PATH_RECALC_BACKOFF_TICKS = 20;
	/** 贴墙滑行侧向偏移（格）：直抵被挡时沿墙面偏行绕障 */
	private static double WALL_SLIDE_OFFSET = 2.0;
	private int attackCooldown = 0;
	/** 下此 tick 才允重试 A*（寻路失败限频） */
	private int nextPathRecalcTick = 0;
	/** 贴墙滑行方向（顺时针）；固定单侧，够用且稳定 */
	private boolean slideClockwise = true;

	// ---- 幽匿人偶音波攻击（WARDEN 变体被动，近战/射手模式均触发） ----
	// 无距离限制：只要锁定了目标就蓄力发射。4 秒冷却期间不蓄力。
	private static int SONIC_CHARGE_TICKS = 30;          // 蓄力前摇 1.5 秒（对齐音效长度）
	private static int SONIC_COOLDOWN_TICKS = 80;       // 发射后冷却 4 秒
	private static float SONIC_BOOM_DAMAGE = 30.0f;     // 音波伤害（平衡重做：20→30）
	private static double SONIC_PULL_STRENGTH = 0.15;   // 拉扯强度（距离比例）
	private int sonicChargeTicks = -1;  // -1 = 不在蓄力
	private int sonicCooldown = 0;      // 发射后冷却计数

	// ---- 下界人偶烈焰弹（NETHER 变体被动，近战/射手模式均触发） ----
	// 对齐幽匿音波框架：锁定目标 → 无需武器 → 自动发射。
	// 差异：无蓄力前摇（烈焰人也不蓄力）、低伤害高频、弹道投射物可被遮挡。
	private static int FIREBALL_COOLDOWN_TICKS = 60;      // 发射后冷却 3 秒
	/** 烈焰弹伤害（public 供 WitherSkullMixin 引用——原版 onHitEntity 硬编码 8.0f，需 Mixin 替换） */
	public static float FIREBALL_DAMAGE = 20.0f;         // 烈焰弹伤害
	private int fireballCooldown = 0;    // 发射后冷却计数

	// ---- 末影人偶龙息喷吐 + 瞬移处决（ENDER 变体被动，近战/射手模式均触发） ----
	// 龙息喷吐：发射 WitherSkull（和下界人偶统一投射物），命中后生成龙息云造成范围持续伤害。3 秒冷却。
	//   对齐下界烈焰弹框架：锁定目标 → 无需武器 → 自动发射 → WitherSkull 投射物。
	//   差异：命中效果不同——下界点燃 5 秒，末影生成龙息云（AreaEffectCloud 即时伤害 II）。
	//   WitherSkullMixin 按 owner 变体区分行为：NETHER→燃烧，ENDER→龙息云。
	//   渲染端按变体区分贴图：NETHER→nether_doll.png，ENDER→ender_doll.png。
	// 瞬移处决：目标血量 ≤ EXECUTE_THRESHOLD 时瞬移过去直接斩杀。60 秒冷却。
	//   原版末影人瞬移机制 + 斩杀判定，处决后瞬移回原位。
	private static int BREATH_COOLDOWN_TICKS = 60;        // 龙息喷吐冷却 3 秒
	private static int EXECUTE_COOLDOWN_TICKS = 1200;     // 瞬移处决冷却 60 秒
	private static float EXECUTE_HEALTH_THRESHOLD = 0.25f; // 目标血量百分比阈值（25%）
	private static float EXECUTE_HEALTH_THRESHOLD_AXE = 0.55f; // 持末影斧时斩杀线提升至 55%（基础25%+斧30%）
	private int breathCooldown = 0;     // 龙息喷吐冷却计数
	private int executeCooldown = 0;    // 瞬移处决冷却计数

	// 末影人偶闪避（ENDER 变体受击被动）：收到"攻击类伤害"时按来源类型完全免伤并瞬移躲避。
	// 近战（来源实体非投射物）67% 概率；投射物（arrows/trident/wither skull 等）100% 概率。
	// 环境伤害（跌落/火焰/虚空等）不拦截，正常结算。找不到附近安全落点则原地免伤（仍播特效）。
	private static float ENDER_DODGE_CHANCE = 0.67f;      // 近战受击 67% 概率无伤瞬移
	private static float ENDER_DODGE_CHANCE_PROJECTILE = 1.0f; // 投射物 100% 闪避
	private static int ENDER_DODGE_RADIUS = 4;            // 瞬移躲避落点搜索半径（格）

	// ---- 海洋人偶激光蓄力（SEA 变体被动，近战/射手模式均触发） ----
	// 对齐原版守卫者：蓄力 → 视线内 hitscan → 魔法伤害（绕护甲）。
	// 差异：粒子光束（不创建实体），蓄力 3 秒，冷却 2 秒，高频低伤法师定位。
	private static int LASER_CHARGE_TICKS = 30;         // 蓄力前摇 1.5 秒（原 3 秒，缩短更跟手）
	private static int LASER_COOLDOWN_TICKS = 20;       // 发射后冷却 1 秒（原 2 秒，呼应高频低伤法师）
	private static float LASER_DAMAGE = 20.0f;          // 魔法伤害（绕护甲）
	private static double LASER_RANGE = 16.0;           // 最大射程
	private static double LASER_RANGE_SQR = LASER_RANGE * LASER_RANGE;
	private int laserChargeTicks = -1;  // -1 = 不在蓄力
	private int laserCooldown = 0;      // 发射后冷却计数

	/** 召回登记降频：每 100 tick 记录一次位置，减少 HashMap 写入开销。 */
	private int recallRegistryCooldown = 0;

	// ---- 海洋人偶水中垂直跟随（SEA 专属，仅 isInWater 时生效） ----
	private static double SEA_DIVE_SPEED = 0.18;        // 下潜竖直速度（注入 deltaMovement.y）
	private static double SEA_VERTICAL_DEADZONE = 0.25; // 竖直跟随死区，避免抖动
	// 水下速掘的急迫等级：水中（非地面）挖掘默认 ÷5，需 amp≥20 抵消；取 24（急迫XXV）
	// → 游泳时 ≈1.16×陆地、站海底 ≈5.8×陆地，达成"水中≈陆地甚至更快"。
	private static int SEA_HASTE_LEVEL = 24;            // HASTE amplifier（0=急迫I），即急迫 XXV

	// ---- 近战模式（MELEE）：自主搜寻并攻击附近敌对生物 ----
	// 搜寻半径刻意收紧：寻路是轻量网格 A*，过远目标（尤其隔墙）容易"干瞪眼"——
	// 16 格内任何敌对生物都会被锁定，墙体阻挡时既打不到也走不过去。
	// 8 格内绝大多数情况已有视线/短绕行路径，干瞪眼概率大幅下降。
	private static double MELEE_SEARCH_RANGE = 8.0;        // 目标搜寻半径（8格）
	private static double MELEE_MAX_PURSUE_DISTANCE_SQR = 8.0 * 8.0; // 追击上限，超出放弃
	private static float MELEE_MOVE_SPEED = 0.13f;         // 移动速度（玩家疾跑速度）
	private LivingEntity meleeTarget;

	/** 指挥棒指定的强制攻击目标 UUID（null=无强制目标，走正常 AI 搜寻）。 */
	private UUID forcedTargetUuid;

	// ---- 耕种模式（FARM）：锄地建 9x9 农田 / 播种 / 收获 / 补种 ----
	// 行为分两档：
	//   1) 物品栏同时有锄头+水桶（缺一不可）且背包有种子 → 先锄一块标准 9x9 耕地（中心挖空放水），建好后转入种收；
	//   2) 否则（背包只有种子）→ 直接对已有农田播种、收获成熟作物、收获后立刻补种。
	// 种子从人偶物品栏取，收获物也放入人偶物品栏（装满才掉落在地）。
	private static double FARM_SEARCH_RANGE = 12.0;        // 目标搜寻半径（以玩家或人偶为中心）
	private static double FARM_REACH_SQR = 3.5 * 3.5;      // 工作距离：到达该距离内才能执行动作
	private static int FARM_ACTION_COOLDOWN = 8;           // 动作冷却（锄/种/收之间的停顿 tick）
	private static int FARM_REGION_HALF = 4;               // 9x9 农田半边长（中心=水井）
	private static double FARM_ANCHOR_RESET_DISTANCE_SQR = 24.0 * 24.0; // 离农田锚点过远则重新选址
	private static float FARM_MOVE_SPEED_FACTOR = 0.6f;    // 移动速度因子（稳健步行）
	private static double FARM_NAV_RECALC_SQR = 1.0 * 1.0; // 目标方块变化超过该距离才重算路径
	/** 锄头可耕方块 → 结果方块（复刻 26.2 原版 HoeItem.TILLABLES 行为） */
	private static final Map<Block, Block> TILLABLE_TO_RESULT = Map.of(
		Blocks.GRASS_BLOCK, Blocks.FARMLAND,
		Blocks.DIRT_PATH, Blocks.FARMLAND,
		Blocks.DIRT, Blocks.FARMLAND,
		Blocks.COARSE_DIRT, Blocks.DIRT,
		Blocks.ROOTED_DIRT, Blocks.DIRT
	);
	private BlockPos farmTargetPos;     // 当前工作目标方块
	private BlockPos farmAnchor;        // 9x9 农田锚点（中心=水井）
	private boolean farmWaterPlaced;    // 中心水源是否已放置
	private int farmActionCooldown = 0; // 动作冷却
	private Vec3 farmNavTarget;         // 种植寻路目标（用于判断是否需要重算路径）

	// ---- 喂食模式（FEED）：玩家饿/血少时贴近喂饭 ----
	// 触发条件：玩家血量 < 19 滴 且 无饱和度 且 饥饿度不满；开启要求背包有正向食物。
	// 触发时人偶贴近玩家（2 格，比跟随的 3.5 格更近）并消耗 1 个食物给玩家
	// 恢复饥饿度+饱和度；不触发时按普通跟随距离跟随。
	private static double FEED_CLOSE_DISTANCE_SQR = 2.0 * 2.0; // 喂食时贴近玩家的距离
	private static int FEED_ACTION_COOLDOWN = 15;              // 喂食间隔 tick（0.75s 一口）
	private int feedCooldown = 0;

	// ---- 砍树模式（CHOP）：找真树，连锁砍完整棵 ----
	// "真树"判定：原木连通体（#minecraft:logs，含菌柄）中至少有一块邻接树叶/菌光体，
	// 避免误砍建筑里的木梁；选中的树用 BFS 收集全部原木，按 y 从低到高逐个尝试，
	// 砍掉任意一块可达原木后，连锁破坏（模仿连锁挖矿模组）沿原木六向连通收集整棵树
	// 剩余原木并瞬间掉落入包——不放大挖掘范围、不搭柱踮脚，一棵树只需砍一块；
	// 整棵都够不着的树（如浮空树）暂时拉黑，稍后再试。斧头（物品栏）是开启前提。
	private static double CHOP_SEARCH_RANGE = 16.0;            // 找树半径（以玩家或人偶为中心）
	private static double CHOP_REACH_SQR = 3.5 * 3.5;          // 挥斧工作距离
	private static int CHOP_ACTION_COOLDOWN = 8;               // 挥斧冷却 tick
	private static float CHOP_MOVE_SPEED_FACTOR = 1.0f;        // 移动速度因子：与跟随/挖矿一致的全速（用户反馈 0.6 太慢）
	private static double CHOP_NAV_RECALC_SQR = 1.0 * 1.0;     // 目标块变化超过该距离才重算路径
	private static int CHOP_SEARCH_COOLDOWN = 20;              // 找不到树后的重搜间隔 tick
	private static int CHOP_MAX_TREE_BLOCKS = 4096;            // 单棵树的原木上限（防死循环，覆盖巨型丛林/深色橡木 2×2 巨树 + 相连邻树）
	private static int CHOP_TREE_BLACKLIST_TICKS = 600;        // 整棵够不着的树拉黑时长（30 秒）
	// ---- 砍树 × 跟随 共存：「砍树优先跟随」的离队边界 ----
	// 跟随时人偶可以为砍树暂时脱离队列走向树，但离队必须有边界：
	// 超时或离主人过远就放弃目标、回到主人身边，否则人偶会一路追着树越跑越远。
	private static int CHOP_EXCURSION_MAX_TICKS = 400;               // 单次离队上限（20 秒）
	private static double CHOP_EXCURSION_MAX_DIST_SQR = 32.0 * 32.0; // 离队时距主人的最远距离
	/** 六方向邻接（BFS 连通树 + 邻接树叶检测） */
	private static final int[][] TREE_NEIGHBORS = {
		{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};
	private List<BlockPos> chopQueue;       // 当前树的待砍原木（y 升序）
	private BlockPos chopTargetPos;         // 当前正在砍的原木
	private int chopActionCooldown = 0;     // 挥斧冷却
	private int chopSearchCooldown = 0;     // 找树冷却
	private Vec3 chopNavTarget;             // 砍树寻路目标
	private BlockPos chopTreeRoot;          // 当前树的树根（y 最低原木），用于整棵拉黑判定
	private final Map<BlockPos, Long> chopTreeBlacklist = new HashMap<>(); // 够不着的树根 -> 拉黑截止 gameTime
	private int chopExcursionTicks = 0;                // 本次「离队砍树」已持续 tick 数（0 = 未离队）

	// 低优先级补种：背包有树苗时，在"找不到树可砍"的空闲间隙，就近种到草方块/泥土上
	private static double SAPLING_SEARCH_RANGE = 8.0;      // 找可种方块半径
	private static double SAPLING_REACH_SQR = 3.5 * 3.5;   // 种植工作距离
	private static int SAPLING_PLANT_COOLDOWN = 20;        // 种完后的冷却 tick（防连续种）
	private BlockPos saplingTargetPos;      // 当前要种树苗的位置（空气方块）
	private int saplingPlantCooldown = 0;   // 种植冷却
	private Vec3 saplingNavTarget;          // 种植寻路目标

	// ---- 插火把模式（TORCH）：在黑暗区域自动插火把照明 ----
	// 检测周围光照等级低于阈值的位置，走到附近后插火把；
	// 优先尝试地面（点击方块顶部），地面无法放置则尝试墙面（点击侧面）。
	private static double TORCH_SEARCH_RANGE = 12.0;       // 搜寻半径
	private static double TORCH_REACH_SQR = 3.5 * 3.5;     // 插火把工作距离
	private static int TORCH_ACTION_COOLDOWN = 10;         // 插火把冷却 tick
	private static int TORCH_SEARCH_COOLDOWN = 20;         // 找不到暗处后的重搜间隔
	private static int TORCH_LIGHT_THRESHOLD = 8;          // 光照阈值：<=8 视为需要插火把
	private static double TORCH_NAV_RECALC_SQR = 1.0 * 1.0; // 目标变化重算路径
	private static double TORCH_FOLLOW_RANGE = 6.0;        // 跟随时叠加半径：只插人偶身边该范围内的暗处（边走边插，插完继续跟随）
	private BlockPos torchTargetPos;       // 当前要插火把的目标位置（暗处的空气方块）
	private int torchActionCooldown = 0;   // 插火把冷却
	private int torchSearchCooldown = 0;   // 搜索冷却
	private Vec3 torchNavTarget;             // 寻路目标

	// ---- 挖矿模式（MINE）：BFS 扫描矿石 → 评分选目标 → 寻路 → 连锁挖矿 ----
	// 物品栏放镐头即可开启。扫描用 BFS 扩展壳层（找到 K 个矿就停，O(found) 不卡顿）；
	// 评分按 矿物价值 + 距离惩罚 + 深度奖励 + 簇奖励 + 可达性 加权选择目标；
	// 钻石/绿宝石/远古残骸=100，金铁红石青金石=50，铜煤=20，下界石英=10。
	// 挖矿带连锁破坏（仿砍树模式的整棵连锁）：挖掉任意一块矿石后，六向连通的
	// 同族矿石（如铁矿石+深层铁矿石视为同族）瞬间一并破坏入包——只需够着一块
	// 即可清空整条矿脉，彻底解决"坑里/墙后的矿够不着导致挖不干净"的问题。
	private static double MINE_SEARCH_RANGE = 16.0;            // BFS 扫描最大半径
	private static double MINE_REACH_SQR = 3.5 * 3.5;          // 挖矿工作距离
	private static int MINE_ACTION_COOLDOWN = 8;               // 挖矿冷却 tick
	private static int MINE_SEARCH_COOLDOWN = 20;              // 找不到矿后的重搜间隔 tick
	private static int MINE_MAX_SCAN_TARGETS = 8;              // BFS 早停：找到 K 个矿就停
	private static float MINE_MOVE_SPEED_FACTOR = 1.0f;        // 移动速度因子：与近战一致的全速（用户反馈 0.6 太慢）
	private static double MINE_NAV_RECALC_SQR = 1.0 * 1.0;     // 目标变化超过该距离才重算路径
	private static int MINE_BLACKLIST_TICKS = 100;             // 不可达矿石拉黑时长（5 秒：主人移动后可达性会变，短拉黑即可）
	private static int MINE_MAX_CHAIN_BLOCKS = 512;            // 单次连锁破坏的矿石上限（防死循环，覆盖巨型矿脉）
	// ---- 挖矿 × 跟随 共存：「机会式顺路捡矿」的离队边界 ----
	// 跟随时人偶只挖行进路线附近暴露在空气里、当前够得着的矿（顺路捡），
	// 埋藏矿不掘进、远矿不绕路。离队边界收紧：超时或离主人过远就放弃目标回主人身边，
	// 否则人偶会一路追着矿越跑越远（用户实测反馈）。
	private static int MINE_EXCURSION_MAX_TICKS = 100;               // 单次离队上限（5 秒）
	private static double MINE_EXCURSION_MAX_DIST_SQR = 12.0 * 12.0; // 离队时距主人的最远距离
	private static double MINE_TARGET_MAX_DIST_SQR = 24.0 * 24.0;    // 非跟随模式候选矿距人偶的硬上限（超了必然够不着）
	private static double MINE_FOLLOW_SCAN_RANGE = 8.0;              // 跟随时 BFS 扫描半径（只找顺路的）
	private static double MINE_FOLLOW_MAX_TARGET_DIST_SQR = 5.0 * 5.0; // 跟随时候选矿距人偶的硬上限（只挖顺路的）
	// ---- 盾构机（MINE 模式的隧道掘进）----
	private static int TUNNEL_ACTION_COOLDOWN = 8;              // 每 N tick 前进 1 格（每秒约 2.5 格）
	private static int TUNNEL_LAVA_SCAN_RANGE = 2;              // 岩浆探测半径（前后左右上下 ±2）
	private BlockPos mineTargetPos;        // 当前挖矿目标方块
	private BlockPos mineStandPos;         // 挖矿目标的可站立点（人偶站到此处即可够着矿；直接够得着时为 null）
	private int mineActionCooldown = 0;    // 挖矿冷却
	private int mineSearchCooldown = 0;    // 搜索冷却
	private Vec3 mineNavTarget;            // 寻路目标（用于判断是否需要重算路径）
	private final Map<BlockPos, Long> mineBlacklist = new HashMap<>(); // 不可达矿石 -> 拉黑截止 gameTime
	private boolean mineBackpackFullNotified = false;  // 背包满已广播过（避免每 tick 刷屏；清包后复位）
	private int mineExcursionTicks = 0;                // 本次「离队采掘」已持续 tick 数（0 = 未离队）

	// ---- 掉落物拾取（人偶不是玩家，原版 Mob 不会自动捡拾地上的物品）----
	private static int DROP_PICKUP_INTERVAL = 4;     // 每 N tick 扫一次（0.2 秒，跟得上挖掘节奏）
	private static double DROP_PICKUP_RANGE = 3.0;   // 拾取半径（覆盖人偶面前 1 格挖掘点 + 侧向偏移）
	private int dropPickupCooldown = 0;

	// ---- 钓鱼模式（FISH）：找水域 → 岸边抛竿 → 等咬钩 → 收竿入包 ----
	// 与跟随模式互斥且自动切换（开启钓鱼会自动关闭跟随，开启跟随会自动关闭钓鱼）；开启条件：物品栏有钓鱼竿。
	// 抛竿后等待随机时长（鱼饵附魔按比例缩短），咬钩时水面溅起水花粒子与音效，
	// 收竿按原版钓鱼战利品表（鱼/垃圾/宝藏）生成掉落进人偶存储区，
	// 海之眷顾附魔提高宝藏权重，钓获附带少量经验。
	private static double FISH_SEARCH_RANGE = 12.0;        // 找水半径（以人偶为中心，钓鱼时跟随已自动关闭）
	private static double FISH_REACH_SQR = 3.5 * 3.5;      // 岸边抛竿工作距离
	private static int FISH_ACTION_COOLDOWN = 40;          // 收竿后到下次抛竿的间隔 tick（2 秒，避免收竿即抛的急促感）
	private static int FISH_SEARCH_COOLDOWN = 30;          // 找不到水后的重搜间隔 tick
	private static float FISH_MOVE_SPEED_FACTOR = 0.6f;    // 移动速度因子（稳健步行）
	private static double FISH_NAV_RECALC_SQR = 1.0 * 1.0; // 目标变化超过该距离才重算路径
	private static int FISH_BITE_BASE_TICKS = 180;         // 咬钩基准等待（约 9 秒）
	private static int FISH_BITE_MAX_TICKS = 320;          // 咬钩等待上限（随机浮动）
	private static int FISH_SKIP_TICKS = 300;              // 不可达水域跳过时长（15 秒）
	private BlockPos fishTargetWater;   // 当前目标水域方块
	private int fishActionCooldown = 0; // 收竿/抛竿动作冷却
	private int fishSearchCooldown = 0; // 找水冷却
	private Vec3 fishNavTarget;         // 抛竿寻路目标
	private boolean fishCastActive;     // 是否已抛竿（等待咬钩中）
	private int fishWaitTicks;          // 距咬钩剩余 tick
	private BlockPos fishSkipPos;       // 近期寻路失败的水域（暂时跳过）
	private long fishSkipUntil;         // 跳过截止 gameTime

	/**
	 * 玩家开始追踪本实体时强制重新同步自定义 SynchedEntityData。
	 * readAdditionalSaveData 中 set 的值在实体加入世界前的初始同步包中
	 * 可能未被正确标记为脏数据，导致客户端收到默认值（mode=-1, follow=false）。
	 * 此处通过先设临时值再设回正确值来强制标记脏，确保客户端状态正确。
	 */
	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		// SynchedEntityData 在玩家开始追踪时由引擎自动下发全部字段。
		// 原先"先设 -1 再设回"的脏标记 hack 依赖 SynchedEntityData 不在同 tick 去重连续 set
		// 的内部实现，未来版本若引入批量去重优化会静默失效、客户端状态不同步。
		// 改为：若新追踪者为主人，主动推送快照刷新其控制面板（DollControlScreen 靠快照渲染）。
		if (player.getUUID().equals(getOwnerUuid())) {
			pushSnapshotToOwner();
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTIVE_MODE, -1);
		builder.define(DATA_FOLLOW_ENABLED, false);
		builder.define(DATA_DOLL_LEVEL, 0);
		builder.define(DATA_IS_WARDEN_VARIANT, false);
		builder.define(DATA_DOLL_VARIANT, DollVariant.NONE.ordinal());
	}

	/**
	 * 从外置配置 {@link DollConfig} 覆写本类静态调参镜像（含 {@link DollNavigator}）。
	 * 由 /dollmod reload 命令驱动；距离类字段以基准值覆写、平方值随之即时重推。
	 */
	public static void applyConfig() {
		DollConfig c = DollConfig.get();

		// ---- 跟随玩家 ----
		FOLLOW_RESUME_DISTANCE_SQR = c.follow.resumeDistance * c.follow.resumeDistance;
		FOLLOW_DIRECT_RECALC_SQR = c.follow.directRecalc * c.follow.directRecalc;
		ROTATION_SPEED_PER_TICK = c.follow.rotationSpeedPerTick;
		NAV_RETRY_COOLDOWN_TICKS = c.follow.navRetryCooldownTicks;
		FOLLOW_STUCK_TELEPORT_DISTANCE_SQR = c.follow.stuckTeleportDistance * c.follow.stuckTeleportDistance;
		STUCK_TELEPORT_TICKS = c.follow.stuckTeleportTicks;
		STUCK_MOVE_EPS_SQR = c.follow.stuckMoveEps * c.follow.stuckMoveEps;

		// ---- 战斗中玩家引力 ----
		COMBAT_LEASH_DISTANCE = c.combatLeash.leashDistance;
		COMBAT_LEASH_DISTANCE_SQR = COMBAT_LEASH_DISTANCE * COMBAT_LEASH_DISTANCE;
		COMBAT_MAX_LEASH_DISTANCE = c.combatLeash.maxLeashDistance;

		// ---- 近战战斗 ----
		ATTACK_COOLDOWN_TICKS = c.combat.cooldownTicks;
		ATTACK_RANGE_SQR = c.combat.range * c.combat.range;
		TARGET_REMEMBER_TICKS = c.combat.targetRememberTicks;

		// ---- 闻声助战 ----
		ASSIST_HEAR_RADIUS = c.assist.hearRadius;
		ASSIST_HEAR_RADIUS_SQR = ASSIST_HEAR_RADIUS * ASSIST_HEAR_RADIUS;

		// ---- 觅途 ----
		PATH_RECALC_BACKOFF_TICKS = c.pathfind.recalcBackoffTicks;
		WALL_SLIDE_OFFSET = c.pathfind.wallSlideOffset;

		// ---- 近战模式 ----
		MELEE_SEARCH_RANGE = c.melee.searchRange;
		MELEE_MAX_PURSUE_DISTANCE_SQR = c.melee.maxPursue * c.melee.maxPursue;
		MELEE_MOVE_SPEED = c.melee.moveSpeed;

		// ---- 射手模式 ----
		RANGED_SEARCH_RANGE = c.ranged.searchRange;
		RANGED_MAX_PURSUE_DISTANCE_SQR = c.ranged.maxPursue * c.ranged.maxPursue;
		BOW_CHARGE_TICKS = c.ranged.bowChargeTicks;
		BOW_SHOOT_COOLDOWN_TICKS = c.ranged.bowShootCooldown;
		CROSSBOW_SHOOT_COOLDOWN_TICKS = c.ranged.crossbowShootCooldown;
		RANGED_TOO_CLOSE_DISTANCE_SQR = c.ranged.tooCloseDistance * c.ranged.tooCloseDistance;
		RANGED_BOW_IDEAL_MAX_DISTANCE_SQR = c.ranged.bowIdealMaxDistance * c.ranged.bowIdealMaxDistance;
		RANGED_CROSSBOW_IDEAL_MAX_DISTANCE_SQR = c.ranged.crossbowIdealMaxDistance * c.ranged.crossbowIdealMaxDistance;
		RANGED_RETREAT_SPEED_FACTOR = c.ranged.retreatSpeedFactor;
		BOW_SHOOT_POWER = c.ranged.bowShootPower;
		BOW_SHOOT_DIVERGENCE = c.ranged.bowShootDivergence;
		CROSSBOW_SHOOT_DIVERGENCE = c.ranged.crossbowShootDivergence;
		ARROW_TRACK_INTERVAL = c.ranged.arrowTrackInterval;
		ARROW_TRACK_MAX_PREDICT_TICKS = c.ranged.arrowTrackMaxPredictTicks;
		CROSSBOW_MULTISHOT_SPREAD = c.ranged.crossbowMultishotSpread;

		// ---- 幽匿音波 ----
		SONIC_CHARGE_TICKS = c.sonic.chargeTicks;
		SONIC_COOLDOWN_TICKS = c.sonic.cooldownTicks;
		SONIC_BOOM_DAMAGE = c.sonic.boomDamage;
		SONIC_PULL_STRENGTH = c.sonic.pullStrength;

		// ---- 下界烈焰弹 ----
		FIREBALL_COOLDOWN_TICKS = c.fireball.cooldownTicks;
		FIREBALL_DAMAGE = c.fireball.damage;

		// ---- 末影 ----
		BREATH_COOLDOWN_TICKS = c.ender.breathCooldownTicks;
		EXECUTE_COOLDOWN_TICKS = c.ender.executeCooldownTicks;
		EXECUTE_HEALTH_THRESHOLD = c.ender.executeHealthThreshold;
		EXECUTE_HEALTH_THRESHOLD_AXE = c.ender.executeHealthThresholdAxe;
		ENDER_DODGE_CHANCE = c.ender.dodgeChance;
		ENDER_DODGE_CHANCE_PROJECTILE = c.ender.dodgeChanceProjectile;
		ENDER_DODGE_RADIUS = c.ender.dodgeRadius;

		// ---- 海洋 ----
		LASER_CHARGE_TICKS = c.sea.laserChargeTicks;
		LASER_COOLDOWN_TICKS = c.sea.laserCooldownTicks;
		LASER_DAMAGE = c.sea.laserDamage;
		LASER_RANGE = c.sea.laserRange;
		LASER_RANGE_SQR = LASER_RANGE * LASER_RANGE;
		SEA_DIVE_SPEED = c.sea.diveSpeed;
		SEA_VERTICAL_DEADZONE = c.sea.verticalDeadzone;
		SEA_HASTE_LEVEL = c.sea.hasteLevel;

		// ---- 耕种 ----
		FARM_SEARCH_RANGE = c.farm.searchRange;
		FARM_REACH_SQR = c.farm.reach * c.farm.reach;
		FARM_ACTION_COOLDOWN = c.farm.actionCooldown;
		FARM_REGION_HALF = c.farm.regionHalf;
		FARM_ANCHOR_RESET_DISTANCE_SQR = c.farm.anchorResetDistance * c.farm.anchorResetDistance;
		FARM_MOVE_SPEED_FACTOR = c.farm.moveSpeedFactor;
		FARM_NAV_RECALC_SQR = c.farm.navRecalc * c.farm.navRecalc;

		// ---- 喂食 ----
		FEED_CLOSE_DISTANCE_SQR = c.feed.closeDistance * c.feed.closeDistance;
		FEED_ACTION_COOLDOWN = c.feed.actionCooldown;

		// ---- 砍树 ----
		CHOP_SEARCH_RANGE = c.chop.searchRange;
		CHOP_REACH_SQR = c.chop.reach * c.chop.reach;
		CHOP_ACTION_COOLDOWN = c.chop.actionCooldown;
		CHOP_MOVE_SPEED_FACTOR = c.chop.moveSpeedFactor;
		CHOP_NAV_RECALC_SQR = c.chop.navRecalc * c.chop.navRecalc;
		CHOP_SEARCH_COOLDOWN = c.chop.searchCooldown;
		CHOP_MAX_TREE_BLOCKS = c.chop.maxTreeBlocks;
		CHOP_TREE_BLACKLIST_TICKS = c.chop.treeBlacklistTicks;
		CHOP_EXCURSION_MAX_TICKS = c.chop.excursionMaxTicks;
		CHOP_EXCURSION_MAX_DIST_SQR = c.chop.excursionMaxDist * c.chop.excursionMaxDist;

		// ---- 低优先级补种 ----
		SAPLING_SEARCH_RANGE = c.sapling.searchRange;
		SAPLING_REACH_SQR = c.sapling.reach * c.sapling.reach;
		SAPLING_PLANT_COOLDOWN = c.sapling.plantCooldown;

		// ---- 插火把 ----
		TORCH_SEARCH_RANGE = c.torch.searchRange;
		TORCH_REACH_SQR = c.torch.reach * c.torch.reach;
		TORCH_ACTION_COOLDOWN = c.torch.actionCooldown;
		TORCH_SEARCH_COOLDOWN = c.torch.searchCooldown;
		TORCH_LIGHT_THRESHOLD = c.torch.lightThreshold;
		TORCH_NAV_RECALC_SQR = c.torch.navRecalc * c.torch.navRecalc;
		TORCH_FOLLOW_RANGE = c.torch.followRange;

		// ---- 挖矿 ----
		MINE_SEARCH_RANGE = c.mine.searchRange;
		MINE_REACH_SQR = c.mine.reach * c.mine.reach;
		MINE_ACTION_COOLDOWN = c.mine.actionCooldown;
		MINE_SEARCH_COOLDOWN = c.mine.searchCooldown;
		MINE_MAX_SCAN_TARGETS = c.mine.maxScanTargets;
		MINE_MOVE_SPEED_FACTOR = c.mine.moveSpeedFactor;
		MINE_NAV_RECALC_SQR = c.mine.navRecalc * c.mine.navRecalc;
		MINE_BLACKLIST_TICKS = c.mine.blacklistTicks;
		MINE_MAX_CHAIN_BLOCKS = c.mine.maxChainBlocks;
		MINE_EXCURSION_MAX_TICKS = c.mine.excursionMaxTicks;
		MINE_EXCURSION_MAX_DIST_SQR = c.mine.excursionMaxDist * c.mine.excursionMaxDist;
		MINE_TARGET_MAX_DIST_SQR = c.mine.targetMaxDist * c.mine.targetMaxDist;
		MINE_FOLLOW_SCAN_RANGE = c.mine.followScanRange;
		MINE_FOLLOW_MAX_TARGET_DIST_SQR = c.mine.followMaxTargetDist * c.mine.followMaxTargetDist;

		// ---- 盾构机 ----
		TUNNEL_ACTION_COOLDOWN = c.tunnel.actionCooldown;
		TUNNEL_LAVA_SCAN_RANGE = c.tunnel.lavaScanRange;

		// ---- 掉落物拾取 ----
		DROP_PICKUP_INTERVAL = c.dropPickup.interval;
		DROP_PICKUP_RANGE = c.dropPickup.range;

		// ---- 钓鱼 ----
		FISH_SEARCH_RANGE = c.fish.searchRange;
		FISH_REACH_SQR = c.fish.reach * c.fish.reach;
		FISH_ACTION_COOLDOWN = c.fish.actionCooldown;
		FISH_SEARCH_COOLDOWN = c.fish.searchCooldown;
		FISH_MOVE_SPEED_FACTOR = c.fish.moveSpeedFactor;
		FISH_NAV_RECALC_SQR = c.fish.navRecalc * c.fish.navRecalc;
		FISH_BITE_BASE_TICKS = c.fish.biteBaseTicks;
		FISH_BITE_MAX_TICKS = c.fish.biteMaxTicks;
		FISH_SKIP_TICKS = c.fish.skipTicks;

		// ---- 寻路器 ----
		DollNavigator.applyConfig();
	}

	public DollEntity(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
		this.setHealth(20.0f);
	}

	@Override
	public ResolvableProfile getProfile() {
		return this.profile;
	}

	/**
	 * 判断物品是否是近战武器（剑、长矛、斧头、三叉戟、锤等）。
	 * 26.2 无 SwordItem/SpearItem 类，剑/长矛均走 ItemTags 标签体系。
	 */
	private boolean isMeleeWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(ItemTags.SWORDS)
			|| stack.is(ItemTags.SPEARS)
			|| stack.getItem() instanceof AxeItem
			|| stack.getItem() instanceof TridentItem
			|| stack.getItem() instanceof MaceItem;
	}

	/** 在人偶物品栏（全45格，含快捷栏与存储区）中查找近战武器，返回找到的槽位索引，未找到返回 -1。 */
	private int findMeleeWeaponInHotbar() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isMeleeWeapon(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 切换模式。格子 8 为跟随开关（独立切换），0-7 为互斥行为模式。
	 * 点击同一模式可取消选择（设为 -1，进入空闲状态）。
	 * 近战模式需要快捷栏中有近战武器、射手模式需要快捷栏中有弓或弩才能开启；
	 * 钓鱼模式需要快捷栏中有钓鱼竿，且与跟随模式互斥（自动切换：开启跟随会关闭钓鱼，
	 * 开启钓鱼会关闭跟随）。
	 * 只在服务端执行。
	 */
	public boolean switchMode(int modeSlot08) {
		if (this.level().isClientSide()) {
			return false;
		}
		if (modeSlot08 < 0 || modeSlot08 > 8) {
			return false;
		}
		if (modeSlot08 == DollMode.FOLLOW_SLOT_INDEX) {
			// 钓鱼模式与跟随互斥且自动切换：钓鱼中开启跟随 → 自动关闭钓鱼并切换到跟随
			if (getActiveMode() == DollMode.FISH.getIndex()) {
				getEntityData().set(DATA_ACTIVE_MODE, -1);
				// 清空钓鱼进行中的状态，防止残留目标/等待干扰后续模式
				fishTargetWater = null;
				fishCastActive = false;
				fishWaitTicks = 0;
				fishActionCooldown = 0;
				fishSearchCooldown = 0;
				fishNavTarget = null;
				fishSkipPos = null;
			}
			getEntityData().set(DATA_FOLLOW_ENABLED, !isFollowEnabled());
			// 跟随开=签名上行（确认），关=签名下行（释放）
			if (isFollowEnabled()) voiceConfirm(); else voiceNoTool();
		} else {
			int current = getActiveMode();
			// 切换到钓鱼模式时，检查物品栏是否有钓鱼竿
			if (modeSlot08 == DollMode.FISH.getIndex() && current != modeSlot08) {
				if (findFishingRodStack().isEmpty()) {
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_fishing_rod"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 切换到近战模式时，检查物品栏是否有近战武器
			// 幽匿/下界/末影人偶可无武器战斗（音波/烈焰弹/龙息兜底），跳过武器检查
			if (modeSlot08 == DollMode.MELEE.getIndex() && current != modeSlot08
				&& !hasInnateCombatAbility()) {
				int weaponSlot = findMeleeWeaponInHotbar();
				if (weaponSlot == -1) {
					// 没有近战武器，发送提示给玩家
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_melee_weapon"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 切换到射手模式时，检查物品栏是否有弓或弩
			// 幽匿/下界/末影人偶可无武器战斗（音波/烈焰弹/龙息兜底），跳过武器检查
			if (modeSlot08 == DollMode.RANGED.getIndex() && current != modeSlot08
				&& !hasInnateCombatAbility()) {
				int weaponSlot = findRangedWeaponInHotbar();
				if (weaponSlot == -1) {
					// 没有弓/弩，发送提示给玩家
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_ranged_weapon"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 切换到耕种模式时，检查背包是否有种子（无种子则锄地/播种都无意义）
			if (modeSlot08 == DollMode.FARM.getIndex() && current != modeSlot08) {
				if (!hasSeedsInInventory()) {
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_seeds"));
					}
					voiceNoTool();
					return false;
				}
			}
		// 切换到喂食模式时：检查背包是否有可食用的正向食物（毒土豆/蜘蛛眼等排除）；
		// 所有人偶（含森林）一致，无食物不允许开启喂食模式
		if (modeSlot08 == DollMode.FEED.getIndex() && current != modeSlot08) {
			if (!hasPositiveFoodInInventory()) {
				Player owner = getOwnerPlayer();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_food"));
				}
				voiceNoTool();
				return false;
			}
		}
			// 切换到砍树模式时，检查物品栏是否有斧头
			if (modeSlot08 == DollMode.CHOP.getIndex() && current != modeSlot08) {
				if (!hasAxeInHotbar()) {
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_axe"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 切换到挖矿模式时，检查物品栏是否有镐头；运行中的工具切换则由 findPickaxeStack 全背包兜底
			if (modeSlot08 == DollMode.MINE.getIndex() && current != modeSlot08) {
				if (!hasPickaxeInHotbar()) {
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_pickaxe"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 切换到插火把模式时，检查背包是否有火把
			if (modeSlot08 == DollMode.TORCH.getIndex() && current != modeSlot08) {
				if (!hasTorchInInventory()) {
					Player owner = getOwnerPlayer();
					if (owner != null) {
						owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".no_torch"));
					}
					voiceNoTool();
					return false;
				}
			}
			// 跟随中切换到钓鱼：自动关闭跟随（互斥自动切换，前置检查通过后才生效，避免无鱼竿时误关跟随）
			if (modeSlot08 == DollMode.FISH.getIndex() && current != modeSlot08 && isFollowEnabled()) {
				getEntityData().set(DATA_FOLLOW_ENABLED, false);
				Player owner = getOwnerPlayer();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".fish_follow_conflict"));
				}
			}
			int newMode = (current == modeSlot08) ? -1 : modeSlot08;
			getEntityData().set(DATA_ACTIVE_MODE, newMode);
			// 切换/取消模式：清空旧工作状态（目标/冷却/盾构机等），防止残留目标干扰新模式
			resetModeWorkState();
			// 模式切换反馈音（统一按键声）
			playModeVoice(newMode);
		}
		return true;
	}

	/**
	 * 清空所有模式的工作状态（目标/冷却/盾构机/使用中的武器等）。
	 * 切模式与"召回传送"共用：传送后人偶立即停止传送前的模式（跟随保留）。
	 */
	private void resetModeWorkState() {
		farmTargetPos = null;
		farmAnchor = null;
		farmWaterPlaced = false;
		farmActionCooldown = 0;
		farmNavTarget = null;
		feedCooldown = 0;
		chopQueue = null;
		chopTargetPos = null;
		chopActionCooldown = 0;
		chopSearchCooldown = 0;
		chopNavTarget = null;
		chopTreeRoot = null;
		chopTreeBlacklist.clear();
		saplingTargetPos = null;
		saplingPlantCooldown = 0;
		saplingNavTarget = null;
		torchTargetPos = null;
		torchActionCooldown = 0;
		torchSearchCooldown = 0;
		torchNavTarget = null;
		navigator.clearPath();
		mineTargetPos = null;
		mineActionCooldown = 0;
		mineSearchCooldown = 0;
		mineNavTarget = null;
		mineBlacklist.clear();
		mineBackpackFullNotified = false;
		fishTargetWater = null;
		fishCastActive = false;
		fishWaitTicks = 0;
		fishActionCooldown = 0;
		fishSearchCooldown = 0;
		fishNavTarget = null;
		fishSkipPos = null;
		// 切换/取消模式时清空旧目标，防止残留目标在空闲或无关模式下被继续追击
		endUsingRangedWeapon(); // 若正在拉弓/装填/进食，先退出使用状态复位姿势
		eatSourceSlot = -1; // 进食被打断（stopUsingItem）后 completeUsingItem 不会再触发，必须在此清槽，否则主手残留食物渲染且近战伤害被食物顶替
		meleeTarget = null;
		rangedTarget = null;
		forcedTargetUuid = null; // 指挥棒指定的强制目标也随模式切换清除
		crossbowLoadTicks = -1;
		bowChargeTicks = -1;
		tunneling = false; // 盾构机暂停（配置保留，回到 MINE 后用指挥棒恢复）
		workAreaNavTarget = null; // 重置"前往作业区"目标
		// 切断模式切换/召回前的移动速度与输入，防止旧惯性继续驱动实体漂移
		clearMovementInput();
		setDeltaMovement(Vec3.ZERO);
	}

	/** 像玩家一样能翻越一格高的方块（默认步高仅 0.6，翻不过 1 格）。 */
	@Override
	public float maxUpStep() {
		return 1.0f;
	}

	/**
	 * 盔甲耐久消耗：26.2 原版 LivingEntity.hurtArmor/hurtHelmet 是空方法（no-op），
	 * 真正的实现是 doHurtEquipment（按 Equippable.damageOnHurt 判定 + hurtAndBreak，
	 * 每个护甲槽消耗 max(1, 伤害/4) 点耐久）。这里覆写 hurtArmor，把四个护甲槽
	 * 交给 doHurtEquipment 处理，使盔甲受击时正常掉耐久。
	 */
	@Override
	public void hurtArmor(DamageSource source, float amount) {
		doHurtEquipment(source, amount,
			new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD});
	}

	/**
	 * 人偶的声音归入“玩家”音量分类。
	 * 原版 Entity.getSoundSource() 默认返回 NEUTRAL（只有 Player 是 PLAYERS 档），
	 * 覆写后脚步声/受伤声等与玩家同档，便于玩家统一调节音量。
	 */
	@Override
	public SoundSource getSoundSource() {
		return SoundSource.PLAYERS;
	}

	/**
	 * 末影人偶 80% 闪避：覆写受击入口，在 super 扣血前拦截。
	 * 仅对"攻击类伤害"（来源实体非空）生效；环境伤害（跌落/火焰/虚空等）正常结算。
	 * 命中概率则完全免伤并瞬移躲避：离开位置 + 到达位置各播一次末影人传送粒子/音效，
	 * 落点为附近随机可站立空位；找不到安全落点时降级为原地免伤（仍播放特效）。
	 * 其他人偶 isEnderDoll() 为 false，零影响。
	 */
	@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
			// 幽匿人偶投射物免疫（类似凋零二阶段）
			if (isWardenDoll() && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
				return false;
			}
			if (isEnderDoll() && source.getEntity() != null) {
			// 投射物（arrows/trident/wither skull 等）100% 闪避；近战（来源实体非投射物）67%
			boolean isProjectile = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
			float dodgeChance = isProjectile ? ENDER_DODGE_CHANCE_PROJECTILE : ENDER_DODGE_CHANCE;
			if (this.random.nextFloat() < dodgeChance) {
			// 离开位置：传送门粒子 + 末影人传送音效
			level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(),
				20, 0.5, 1.0, 0.5, 0.5);
			level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0f, 1.0f);

			// 搜索附近安全落点并瞬移（找不到则原地免伤）
			BlockPos safe = findDodgeSpot();
			if (safe != null) {
				this.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
			}

			// 到达位置粒子（原地时也播放，强化"瞬移抖动"观感）
			level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(),
				20, 0.5, 1.0, 0.5, 0.5);
			return false;  // 完全免伤
			}
		}
		return super.hurtServer(level, source, amount);
	}

	/** 末影闪避落点：在当前位置附近随机搜索一个与原位不同的可站立空位（复用 findStandableSpot）。 */
	private BlockPos findDodgeSpot() {
		BlockPos origin = this.blockPosition();
		for (int attempt = 0; attempt < 16; attempt++) {
			int dx = this.random.nextInt(ENDER_DODGE_RADIUS * 2 + 1) - ENDER_DODGE_RADIUS;
			int dz = this.random.nextInt(ENDER_DODGE_RADIUS * 2 + 1) - ENDER_DODGE_RADIUS;
			if (dx == 0 && dz == 0) {
				continue;
			}
			BlockPos column = origin.offset(dx, 0, dz);
			BlockPos spot = findStandableSpot(this.level(), column);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	@Override
	public void tick() {
		boolean serverSide = !this.level().isClientSide() && this.isAlive();
		float healthBefore = serverSide ? getHealth() : 0;
		boolean wasIdle = false;

		// 移动输入要在 super.tick() 之前写入，aiStep()/travel() 会在本 tick 消费它们
		if (serverSide) {
			wasIdle = applyMovementInput();
		}

		super.tick();

		// 播种挥动计时递减（仅服务端设置，客户端恒为 0）
		if (plantSeedHandTicks > 0) {
			plantSeedHandTicks--;
		}

		// 客户端实体没有 AI 决策：yHeadRot 不随实体数据同步（构造函数还会随机化），
		// 这里强制头部跟随身体朝向，避免渲染出头相对身体扭 180° 的诡异姿势。
		if (this.level().isClientSide()) {
			this.setYHeadRot(this.getYRot());
		}

		// 挥臂：26.2 中 updateSwingTime() 只在 Player/Monster 的 aiStep 里被调用，
		// Avatar/LivingEntity 分支不会推进 swingTime，这里手动补齐。
		this.updateSwingTime();

		if (serverSide) {
			tickAutoJump();
			tickIdleAndHealth(wasIdle, healthBefore);
			tickPermanentEffects();
			tryAutoEat();
			int modeIdx = getActiveMode();
			updateModeMinds(modeIdx);
			tickSpecialAttacks(modeIdx);
			tickVariantAuras();
			// 登记当前位置，供"刷怪蛋右键召回"在人偶区块未加载时定位（降频至每 100 tick）
			if (recallRegistryCooldown-- <= 0) {
				recallRegistryCooldown = 100;
				DollRecallRegistry.record(this.getUUID(), this.level().dimension(), this.blockPosition(), this.getOwnerUuid());
			}
		}
	}

	/**
	 * 移动输入：跟随优先，但火把模式可以叠加（边走边插身边暗处）。
	 * 必须在 super.tick() 之前写入，aiStep()/travel() 会在本 tick 消费它们。
	 * @return 是否处于静止状态（zza == 0 && xxa == 0）
	 */
	private boolean applyMovementInput() {
		if (isFollowEnabled()) {
			applyFollowInput();
		}
		int modeIdx = getActiveMode();
		if (modeIdx == DollMode.TORCH.getIndex()) {
			applyTorchInput();
		} else if (!isFollowEnabled()) {
			if (modeIdx == DollMode.MELEE.getIndex()) {
				applyMeleeInput();
			} else if (modeIdx == DollMode.RANGED.getIndex()) {
				applyRangedInput();
			} else if (modeIdx == DollMode.FARM.getIndex()) {
				applyFarmInput();
			} else if (modeIdx == DollMode.FEED.getIndex()) {
				applyFeedInput();
			} else if (modeIdx == DollMode.CHOP.getIndex()) {
				applyChopInput();
			} else if (modeIdx == DollMode.MINE.getIndex()) {
				applyMineInput();
			} else if (modeIdx == DollMode.FISH.getIndex()) {
				applyFishInput();
			}
		}
		return (this.zza == 0.0f && this.xxa == 0.0f);
	}

	/**
	 * 玩家式自动跳跃：贴墙（高差 ≥2 格的障碍）且正在移动时起跳。
	 * 1 格台阶由 maxUpStep()=1.0f 自动跨上，不会触发本分支。
	 * 撞墙时清空导航状态，下 tick 强制重寻路（换层 A* 会尝试找楼梯/坡道绕行），
	 * 冷却节流避免寻路失败时每 tick 重复 1024 节点搜索。
	 */
	private void tickAutoJump() {
		if (this.onGround() && this.horizontalCollision
			&& (this.zza != 0.0f || this.xxa != 0.0f)) {
			this.jumpFromGround();
			if (navRetryCooldown <= 0) {
				navRetryCooldown = NAV_RETRY_COOLDOWN_TICKS;
				navigator.clearPath();
				directMoveMode = false;
				lastNavTarget = null;
			}
		}
		if (navRetryCooldown > 0) {
			navRetryCooldown--;
		}
	}

	/**
	 * 静止时每 4 秒随机偏转头部（避免完全僵死），禁用自然回血，处理食物回血。
	 */
	private void tickIdleAndHealth(boolean wasIdle, float healthBefore) {
		if (wasIdle) {
			idleTickCounter++;
			if (idleTickCounter % 80 == 0) {
				float randomOffset = (this.getRandom().nextFloat() - 0.5f) * 45.0f;
				this.setYHeadRot(this.getYRot() + randomOffset);
			}
		} else {
			idleTickCounter = 0;
		}
		// 禁用自然回血：无生命恢复效果时，回退 super.tick() 产生的小幅回血
		if (getHealth() > healthBefore && getHealth() - healthBefore <= 1.0f
			&& !hasEffect(MobEffects.REGENERATION)) {
			setHealth(healthBefore);
		}
		// 食物回血：原版 onConsume 对非 Player 实体跳过 nutrition，手动按食物营养值回血。
		// 放在禁用自然回血之后，避免被回退代码吞掉。
		if (pendingFoodHeal > 0) {
			heal(pendingFoodHeal);
			pendingFoodHeal = 0;
		}
	}

	/**
	 * 永久药效（每 tick 补漏，保证持久）：
	 * 普通工人按阶缩放；特殊变体统一基线；驯服幽匿 apex。
	 */
	private void tickPermanentEffects() {
		if (getDollVariant() == DollVariant.WARDEN) {
			// 驯服幽匿：恢复VI + 抗性IV + 抗火（击退免疫由属性提供）
			if (!hasEffect(MobEffects.REGENERATION))
				addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 5, false, false));
			if (!hasEffect(MobEffects.RESISTANCE))
				addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 3, false, false));
			if (!hasEffect(MobEffects.FIRE_RESISTANCE))
				addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
		} else if (getDollVariant() == DollVariant.GUIDE) {
			// 向导：恢复IV + 抗性II；无抗火（与锁定 spec 一致）
			if (!hasEffect(MobEffects.REGENERATION))
				addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 3, false, false));
			if (!hasEffect(MobEffects.RESISTANCE))
				addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, false));
		} else if (getDollVariant() == DollVariant.PALE
				|| getDollVariant() == DollVariant.NETHER
				|| getDollVariant() == DollVariant.ENDER
				|| getDollVariant() == DollVariant.SEA
				|| getDollVariant() == DollVariant.FOREST) {
			// 特殊变体统一基线：恢复IV + 抗性II；抗火仅下界/海洋
			if (!hasEffect(MobEffects.REGENERATION))
				addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 3, false, false));
			if (!hasEffect(MobEffects.RESISTANCE))
				addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, false));
			if (getDollVariant() == DollVariant.NETHER || getDollVariant() == DollVariant.SEA) {
				if (!hasEffect(MobEffects.FIRE_RESISTANCE))
					addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
			}
		} else {
			// 普通工人：按阶缩放（5 阶封顶 100HP / 恢复IV / 抗性II）
			int level = getDollLevel();
			if (level >= 4) {
				if (!hasEffect(MobEffects.REGENERATION))
					addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 3, false, false));
				if (!hasEffect(MobEffects.RESISTANCE))
					addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, false));
			} else if (level >= 3) {
				if (!hasEffect(MobEffects.REGENERATION))
					addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 2, false, false));
				if (!hasEffect(MobEffects.RESISTANCE))
					addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false));
			} else if (level >= 2) {
				if (!hasEffect(MobEffects.REGENERATION))
					addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 1, false, false));
			} else if (level >= 1) {
				if (!hasEffect(MobEffects.REGENERATION))
					addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 0, false, false));
			}
		}
	}

	/**
	 * 决策逻辑：跟随和模式并存。跟随负责传送，模式负责攻击。
	 */
	private void updateModeMinds(int modeIdx) {
		if (isFollowEnabled()) {
			updateFollowMind();
		}
		if (modeIdx == DollMode.MELEE.getIndex()) {
			updateMeleeMind();
		} else if (modeIdx == DollMode.RANGED.getIndex()) {
			updateRangedMind();
		} else if (modeIdx == DollMode.FARM.getIndex()) {
			updateFarmMind();
		} else if (modeIdx == DollMode.FEED.getIndex()) {
			updateFeedMind();
		} else if (modeIdx == DollMode.CHOP.getIndex()) {
			updateChopMind();
		} else if (modeIdx == DollMode.MINE.getIndex()) {
			updateMineMind();
		} else if (modeIdx == DollMode.TORCH.getIndex()) {
			updateTorchMind();
		} else if (modeIdx == DollMode.FISH.getIndex()) {
			updateFishMind();
		}
	}

	/**
	 * 特殊攻击：箭矢追踪、掉落物拾取、变体专属攻击技能。
	 * 近战/射手模式下锁定目标后自动释放。
	 */
	private void tickSpecialAttacks(int modeIdx) {
		trackArrows();
		collectNearbyDrops();

		boolean combatMode = modeIdx == DollMode.MELEE.getIndex() || modeIdx == DollMode.RANGED.getIndex();

		if (getDollVariant() == DollVariant.WARDEN && combatMode) {
			handleWardenSonicBoom();
		}
		if (getDollVariant() == DollVariant.NETHER && combatMode) {
			handleNetherFireball();
		}
		if (getDollVariant() == DollVariant.ENDER && combatMode) {
			handleEnderBreath();
			handleEnderExecute();
		}
		if (getDollVariant() == DollVariant.SEA && combatMode) {
			handleSeaLaser();
		}
	}

	/**
	 * 变体光环效果：苍白恐惧、下界安抚/灼烧、海洋安抚/玩家增益、森林藤蔓/动物/回血/标记、向导引导。
	 */
	private void tickVariantAuras() {
		if (getDollVariant() == DollVariant.PALE) {
			updatePaleAuraCenter();
			applyFearAura();
		}
		if (getDollVariant() == DollVariant.NETHER) {
				updateNetherAuraCenter();
				applyNetherPacifyAura();
				applyNetherBurnAura();
			}
		if (getDollVariant() == DollVariant.SEA) {
			applySeaPacifyAura();
			applySeaPlayerAura();
		}
		if (getDollVariant() == DollVariant.FOREST) {
			applyForestVineAura();
			applyForestAnimalAttract();
			applyForestRegenAura();
			applyForestMarkAura();
		}
		if (getDollVariant() == DollVariant.GUIDE) {
			applyGuideAura();
		}
	}

	/**
	 * 自动进食：血量未满时从存储区(9-44)找食物。
	 * 找到后不直接回血，而是把"主手"临时指向该槽并 startUsingItem：
	 * 服务端置 USING_ITEM 标志（SynchedEntityData 同步）→ 客户端按主手装备包拿
	 * 到食物 → HumanoidModel 摆出持物进食姿势；aiStep 每 tick 递减使用剩余时间，
	 * 到 0 时 completeUsingItem → finishUsingItem → LivingEntity.eat()，统一处理
	 * 回血、消耗 1 个、进食音效与粒子。每 2 秒最多吃一次。
	 */
	private void tryAutoEat() {
		if (eatCooldown > 0) {
			eatCooldown--;
			return;
		}
		// 喂食模式下人偶不吃存货，把食物留给玩家
		if (getActiveMode() == DollMode.FEED.getIndex()) {
			return;
		}
		// 射手模式战斗中不吃存货：拉弓/装填姿势由 startUsingItem 驱动，
		// 进食会抢占同一条"使用"通道，导致姿势在食物与弓弩之间打架
		if (getActiveMode() == DollMode.RANGED.getIndex() && rangedTarget != null) {
			return;
		}
		if (getHealth() >= getMaxHealth()) {
			return;
		}
		if (isUsingItem()) {
			return; // 正在吃/用物品，等吃完再找下一份
		}
		for (int i = DollScreenHandler.DOLL_STORAGE_START; i <= DollScreenHandler.DOLL_HOTBAR_END; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null) {
				eatSourceSlot = i; // 先指槽，再 startUsingItem（内部用 getItemInHand(主手) 取物品）
				startUsingItem(InteractionHand.MAIN_HAND);
				eatCooldown = EAT_COOLDOWN_TICKS;
				return;
			}
		}
	}

	/**
	 * 进食结束（服务端 aiStep 在剩余时间归零时调用）。
	 * 必须在 super 之后清理 eatSourceSlot：父类内部会用 getItemInHand(主手)
	 * 与 useItem 做 equals 比对，提前清会走 releaseUsingItem 中断进食。
	 * <p>
	 * Avatar/LivingEntity 没有 FoodData，原版 onConsume 对非 Player 实体只播音效、
	 * 跳过 nutrition——食物营养值不会自动转血。这里在 super 消耗物品之前读取
	 * nutrition，暂存到 pendingFoodHeal，由 tick() 在禁用自然回血之后统一应用。
	 */
	@Override
	protected void completeUsingItem() {
		if (eatSourceSlot != -1) {
			ItemStack foodStack = inventory.getItem(eatSourceSlot);
			FoodProperties food = foodStack.get(DataComponents.FOOD);
			if (food != null) {
				pendingFoodHeal += food.nutrition();
			}
		}
		super.completeUsingItem();
		eatSourceSlot = -1;
	}

	/**
	 * 射手模式移动输入：与目标保持 6~15（弓）/ 6~18（弩）格理想距离，
	 * 太近则全速后退，太远则追击，在射程内则停下瞄准。
	 * 弓拉弓蓄力、弩装填均不限制移动——边退边射/边追边装，拉扯时持续输出。
	 */
	private void applyRangedInput() {
		if (rangedTarget == null
			|| (forcedTargetUuid == null && this.distanceToSqr(rangedTarget) > RANGED_MAX_PURSUE_DISTANCE_SQR)) {
			clearMovementInput();
			return;
		}
		double distSqr = this.distanceToSqr(rangedTarget);
		if (distSqr < RANGED_TOO_CLOSE_DISTANCE_SQR) {
			// 目标太近，后退拉开距离（全速，风筝怪物）
			retreatFrom(rangedTarget.position());
		} else if (distSqr > getRangedIdealMaxDistanceSqr()) {
			// 目标超出理想射程，追击
			moveToPosition(rangedTarget.position(), 1.0f);
		} else {
			// 在理想射程内，停下瞄准射击
			smoothLookAt(rangedTarget.getX(), rangedTarget.getEyeY(), rangedTarget.getZ());
			clearMovementInput();
		}
	}

	/** 当前武器（弓/弩）的理想射程上限（平方距离）。弩箭速更快，允许站得更远。 */
	private double getRangedIdealMaxDistanceSqr() {
		return getRangedWeapon().getItem() instanceof CrossbowItem
			? RANGED_CROSSBOW_IDEAL_MAX_DISTANCE_SQR
			: RANGED_BOW_IDEAL_MAX_DISTANCE_SQR;
	}

	/** 当前武器在射程内且对目标有清晰视线（发射判定；近距离不过滤，贴脸也能射）。 */
	private boolean canShoot(LivingEntity target) {
		return this.distanceToSqr(target) <= getRangedIdealMaxDistanceSqr()
			&& hasLineOfSight(target.position());
	}

	/** 清空本 tick 的移动输入，令实体原地停下。 */
	protected void clearMovementInput() {
		this.xxa = 0.0f;
		this.zza = 0.0f;
		setSpeed(0.0f);
		this.setSprinting(false);
	}

	/**
	 * 生成时面向玩家：实体朝向、身体朝向、头部朝向全部对齐指向玩家，
	 * 并同步设置上一 tick 的朝向值（避免插值动画）。
	 * <p>
	 * 背景：LivingEntity 构造函数会把 yRot/yHeadRot 随机化，且 yHeadRot/yBodyRot
	 * 不随实体数据同步——生成时若只 setYRot，客户端渲染会以随机 yHeadRot 当头部
	 * 朝向、以 yBodyRot(=0) 当身体朝向，头相对身体可能差 180°，出现"头扭过 180°"。
	 */
	public void faceTowardsPlayer(Player player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		if (dx == 0.0 && dz == 0.0) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.yBodyRot = yaw;
		this.yBodyRotO = yaw;
		this.yHeadRotO = yaw;
	}

	/**
	 * 平滑转向目标方向：身体每 tick 最多转 ROTATION_SPEED_PER_TICK 度，
	 * 头部允许转得更快（1.8 倍），使头先于身体对准目标——玩家就是这样转头的。
	 */
	protected void smoothFaceTowards(double x, double z) {
		double dx = x - this.getX();
		double dz = z - this.getZ();
		if (dx == 0.0 && dz == 0.0) {
			return;
		}
		float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		// 身体：平滑转向
		float bodyDiff = wrapDegrees(targetYaw - this.getYRot());
		float bodyClamped = Mth.clamp(bodyDiff, -ROTATION_SPEED_PER_TICK, ROTATION_SPEED_PER_TICK);
		this.setYRot(this.getYRot() + bodyClamped);
		// 头：比身体转得更快，允许头先于身体到达目标方向
		float headDiff = wrapDegrees(targetYaw - this.getYHeadRot());
		float headClamped = Mth.clamp(headDiff, -ROTATION_SPEED_PER_TICK * 1.8f, ROTATION_SPEED_PER_TICK * 1.8f);
		this.setYHeadRot(this.getYHeadRot() + headClamped);
	}

	/**
	 * 平滑看向目标（含抬头/低头）：水平朝向复用 smoothFaceTowards，
	 * 额外根据目标高度相对人偶眼睛的高度差平滑调整俯仰角（XRot）。
	 * 用于人偶看向玩家等场景——当玩家与人偶错开 Y 轴时，人偶会仰头/低头
	 * 看向玩家，而不是只转动身体方向。
	 */
	protected void smoothLookAt(double x, double y, double z) {
		smoothFaceTowards(x, z);
		double dx = x - this.getX();
		double dz = z - this.getZ();
		double dy = y - this.getEyeY();
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		// 目标在人偶眼睛上方时抬头（负 XRot），下方时低头（正 XRot）
		float targetPitch = (float) -(Mth.atan2(dy, horizontalDist) * 180.0 / Math.PI);
		float pitchDiff = wrapDegrees(targetPitch - this.getXRot());
		float pitchClamped = Mth.clamp(pitchDiff, -ROTATION_SPEED_PER_TICK * 1.8f, ROTATION_SPEED_PER_TICK * 1.8f);
		this.setXRot(Mth.clamp(this.getXRot() + pitchClamped, -90.0f, 90.0f));
	}

	/** 将角度差归一化到 [-180, 180]，确保走最短旋转路径。 */
	private static float wrapDegrees(float deg) {
		return Mth.wrapDegrees(deg);
	}

	/**
	 * 检测从实体当前位置到目标位置在 XZ 平面上是否有清晰视线。
	 * 使用 Bresenham 线段算法沿水平方向逐格检测；检测高度沿视线从实体脚部
	 * 线性渐变到目标高度（而非固定取最大 Y），每格检查人偶所需的脚部+头部
	 * 两层通行空间。两层都被挡才判定为遮挡，允许翻越 1 格高台阶；
	 * 高差 ≥2 格的垂直墙会被正确判为遮挡（触发 A* 绕路找楼梯），不会误判
	 * 视线通畅而直线撞墙。
	 */
	protected boolean hasLineOfSight(Vec3 target) {
		double x0 = this.getX();
		double z0 = this.getZ();
		double x1 = target.x;
		double z1 = target.z;
		double dx = Math.abs(x1 - x0);
		double dz = Math.abs(z1 - z0);
		int steps = (int) Math.max(dx, dz);
		if (steps == 0) {
			return true;
		}
		double stepX = (x1 - x0) / steps;
		double stepZ = (z1 - z0) / steps;
		double curX = x0;
		double curZ = z0;
		double fromY = this.getY();
		// 重用单个 MutableBlockPos，避免每格分配 BlockPos + above() 两个对象
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 1; i < steps; i++) {
			curX += stepX;
			curZ += stepZ;
			int bx = Mth.floor(curX);
			int bz = Mth.floor(curZ);
			double t = i / (double) steps;
			int footY = Mth.floor(fromY + (target.y - fromY) * t);
			cursor.set(bx, footY, bz);
			if (!level().getBlockState(cursor).getCollisionShape(level(), cursor).isEmpty()
				&& !level().getBlockState(cursor.setY(footY + 1)).getCollisionShape(level(), cursor.setY(footY + 1)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** 背对目标后退（用于射手模式拉开距离）。 */
	private void retreatFrom(Vec3 target) {
		smoothFaceTowards(target.x, target.z);
		this.xxa = 0.0f;
		this.zza = -1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * RANGED_RETREAT_SPEED_FACTOR);
		this.setSprinting(false);
	}

	/**
	 * 混合模式移动：有视线时直线跟随（平滑转向），无视线时 A* 绕路。
	 * 直线模式下直接向目标实际坐标移动，路径不经过网格对齐，消除 Z 字形。
	 */
	protected void moveToPosition(Vec3 target, float speedFactor) {
		// 海洋人偶离开水面时清除游泳跳跃标记，避免在陆地继续起跳
		if (isSeaDoll() && !this.isInWater()) {
			this.setJumping(false);
		}

		float moveSpeed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
		Vec3 moveTarget;
		double goalY;

		if (hasLineOfSight(target)) {
			// ---- 直线跟随模式 ----
			directMoveMode = true;
			lastNavTarget = target;
			navigator.clearPath();
			moveTarget = target;
			goalY = target.y;
		} else {
			// ---- A* 寻路模式 ----
			// DollNavigator 现于精点不可达时返"最近可达点"之路，故 computePath 罕败；
			// 败时（四面尽堵）与路径走尽仍被挡时，皆限频后贴墙滑行，勿每 tick 反复求。
			if (tickCount >= nextPathRecalcTick
				&& (directMoveMode || targetMoved(target) || navigator.isPathDone())) {
				directMoveMode = false;
				lastNavTarget = target;
				if (!navigator.computePath(target)) {
					nextPathRecalcTick = tickCount + PATH_RECALC_BACKOFF_TICKS;
					slideAlongWall(target, moveSpeed);
					applySeaSwimIfNeeded(target.y);
					return;
				}
			}
			Vec3 node = navigator.advance();
			if (node == null) {
				// 路径走尽仍被挡：贴墙滑行绕障，限频勿每 tick 反复算
				nextPathRecalcTick = tickCount + PATH_RECALC_BACKOFF_TICKS;
				slideAlongWall(target, moveSpeed);
				applySeaSwimIfNeeded(target.y);
				return;
			}
			moveTarget = node;
			goalY = node.y;
		}

		steerAndMove(moveTarget, moveSpeed);
		applySeaSwimIfNeeded(goalY);
	}

	/** 水平转向 + 输入写入（统一原 moveToPosition 中各分支的重复代码）。 */
	private void steerAndMove(Vec3 moveTarget, float moveSpeed) {
		smoothFaceTowards(moveTarget.x, moveTarget.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed(moveSpeed);
		this.setSprinting(true);
	}

	/** 海洋人偶专属：仅 isInWater 时按目标 Y 做竖直跟随，陆地与其他人偶不触发。 */
	private void applySeaSwimIfNeeded(double goalY) {
		if (isSeaDoll() && this.isInWater()) {
			// 穿着海洋靴跟随、且主人不在水里的海人偶：抵消下沉，使其贴合主人深度浮在水表，
			// 避免靴子浮力与跟随竖直控制互相拉扯。主人潜水时(在水中)则刻意不干预，交给 applySeaSwim 下潜。
			// 与 SeaArmorItem.tickBoots 的浮力语义交叉引用：此处关闭靴子通用上浮、改由竖直驱动接管，
			// 改一处需留意另一处。已知潜水边界：主人入水后 tickBoots 的通用浮力可能仍抵消下沉，需在游戏中验证下潜跟随不被卡住。
			if (this.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SeaArmorItem) {
				Player owner = getOwnerPlayer();
				if (owner != null && !owner.isInWater()) {
					Vec3 d = this.getDeltaMovement();
					if (d.y < 0.0) {
						this.setDeltaMovement(d.x, 0.0, d.z);
					}
				}
			}
			applySeaSwim(goalY);
		}
	}

	/**
	 * 海洋人偶水中竖直跟随：复刻溺尸"自由上下"——目标在下方注入向下速度下潜，
	 * 在上方借跳跃输入上浮。浮力由 canBreatheUnderwater()=true 关闭后自然下沉，
	 * 此处补主动竖直驱动使下潜/上浮跟手。moveToPosition 在 super.tick() 前写入，
	 * travel() 的 getFluidFallingAdjustedMovement 会继承注入的竖直速度（约 ×15/16）。
	 */
	private void applySeaSwim(double goalY) {
		double diff = goalY - this.getY();
		if (diff < -SEA_VERTICAL_DEADZONE) {
			Vec3 d = this.getDeltaMovement();
			this.setDeltaMovement(d.x, Math.min(d.y, -SEA_DIVE_SPEED), d.z);
		} else if (diff > SEA_VERTICAL_DEADZONE) {
			this.setJumping(true);
		} else {
			this.setJumping(false);
		}
	}

	private boolean targetMoved(Vec3 target) {
		return lastNavTarget == null
			|| target.distanceToSqr(lastNavTarget) > FOLLOW_DIRECT_RECALC_SQR;
	}

	/**
	 * 战斗中的玩家引力修正：人偶在跟随模式下战斗时，若离主人过远，
	 * 在战斗移动（风筝/追击/站定）已设定的 xxa/zza 基础上，叠加一个朝向主人的分量，
	 * 使人偶"边打边往主人方向挪"，避免战斗中走失。
	 * <p>
	 * 引力强度随距离线性增长：10 格内无修正，10→20 格线性增强，20 格外满强度。
	 * 叠加方式：将朝向主人的世界方向投影到当前朝向坐标系（forward/left），
	 * 分别加到 zza/xxa 上，乘以 2×强度使远距离时可完全覆盖战斗移动方向。
	 * 人偶朝向不变（仍面对战斗目标），仅移动方向被偏转——视觉上是横移/倒退回主人身边。
	 */
	private void applyOwnerGravity(Player owner) {
		double distSqr = this.distanceToSqr(owner);
		if (distSqr <= COMBAT_LEASH_DISTANCE_SQR) return;

		double dist = Math.sqrt(distSqr);
		double t = Math.min(1.0, (dist - COMBAT_LEASH_DISTANCE)
			/ (COMBAT_MAX_LEASH_DISTANCE - COMBAT_LEASH_DISTANCE));

		// 朝向主人的方向（XZ 平面，归一化）
		double dx = owner.getX() - this.getX();
		double dz = owner.getZ() - this.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01) return;
		double ownerDirX = dx / len;
		double ownerDirZ = dz / len;

		// 当前朝向方向（yaw 0 = +Z 南）
		float yawRad = this.getYRot() * ((float) Math.PI / 180.0f);
		float forwardX = -Mth.sin(yawRad);
		float forwardZ = Mth.cos(yawRad);
		// 左侧方向（xxa 正 = 左移）
		float leftX = Mth.cos(yawRad);
		float leftZ = Mth.sin(yawRad);

		// 朝主人的方向在 forward/strafe 坐标系中的分量
		float forwardComponent = (float)(ownerDirX * forwardX + ownerDirZ * forwardZ);
		float strafeComponent = (float)(ownerDirX * leftX + ownerDirZ * leftZ);

		// 叠加引力（×2 使远距离时可覆盖战斗移动方向）
		this.zza = (float) Mth.clamp(this.zza + forwardComponent * (float) t * 2.0f, -1.0, 1.0);
		this.xxa = (float) Mth.clamp(this.xxa + strafeComponent * (float) t * 2.0f, -1.0, 1.0);

		// 引力较强时确保有足够速度（不被战斗分支的低速/停止覆盖）
		if (t > 0.3f) {
			setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
			this.setSprinting(true);
		}
	}

	/**
	 * 跟随分支的移动输入：有战斗目标则追击，否则跟随主人。
	 * 战斗目标（meleeTarget/rangedTarget）由 updateMeleeMind/updateRangedMind 管理，
	 * 这里只读取用于移动方向，不重复搜索。
	 */
	private void applyFollowInput() {
		Player owner = getOwnerPlayer();
		if (owner == null || !owner.isAlive() || owner.isSpectator()) {
			directMoveMode = false;
			clearMovementInput();
			return;
		}
		// 喂食模式：以玩家为中心，触发喂食时比跟随更贴近（移动逻辑统一走喂食分支）
		if (getActiveMode() == DollMode.FEED.getIndex()) {
			applyFeedInput();
			return;
		}
		// 工作模式：砍树/挖矿在跟随开启时走"离队工作"——人偶可以暂时脱离队列走向
		// 主人附近的树/矿，但受离队边界（超时/距主人过远）约束，砍完挖完自动回落跟随，
		// 不会一路追着目标越跑越远（用户实测反馈）。耕种保留"跟随时走向工作目标"。
		int mode = getActiveMode();
		if (mode == DollMode.FARM.getIndex() && farmTargetPos != null && isValidFarmTarget(farmTargetPos)) {
			applyFarmInput();
			return;
		}
		if (mode == DollMode.CHOP.getIndex()) {
			// 砍树优先跟随：有树且在离队边界内 → 移动权交给砍树，人偶暂时脱离队列去砍；
			// 砍完 / 目标失效 / 超时 / 离主人过远 → 自动回落到下面的跟随逻辑回主人身边
			if (chopExcursionAllowed()) {
				chopExcursionTicks++;
				applyChopInput();
				return;
			}
			chopExcursionTicks = 0;
		}
		if (mode == DollMode.MINE.getIndex()) {
			// 挖矿优先跟随：有矿目标且在离队边界内 → 移动权交给挖矿，人偶暂时脱离队列去挖；
			// 挖完 / 目标失效 / 超时 / 离主人过远 → 自动回落到下面的跟随逻辑回主人身边
			if (mineExcursionAllowed()) {
				mineExcursionTicks++;
				applyMineInput();
				return;
			}
			mineExcursionTicks = 0;
		}
		// 仅当当前模式有对应目标时才追击，避免空闲/未实现模式追击残留目标
		LivingEntity target = null;
		if (mode == DollMode.MELEE.getIndex()) {
			target = meleeTarget;
		} else if (mode == DollMode.RANGED.getIndex()) {
			target = rangedTarget;
		}
		if (target != null && target.isAlive() && !target.isRemoved()) {
			if (mode == DollMode.RANGED.getIndex()) {
				// 射手模式：与纯射手模式一致的拉扯——太近后退、射程内站定、太远追击
				double distSqr = this.distanceToSqr(target);
				if (distSqr < RANGED_TOO_CLOSE_DISTANCE_SQR) {
					retreatFrom(target.position());
				} else if (distSqr <= getRangedIdealMaxDistanceSqr()) {
					smoothLookAt(target.getX(), target.getEyeY(), target.getZ());
					clearMovementInput();
				} else {
					moveToPosition(target.position(), 1.0f);
				}
				applyOwnerGravity(owner);
				return;
			}
			// 幽匿/下界/末影人偶无近战武器：跟随时也使用风筝移动，音波/烈焰弹/龙息负责伤害
			if (mode == DollMode.MELEE.getIndex()
				&& (isWardenDoll() || isNetherDoll() || isEnderDoll() || isSeaDoll()) && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
				applyWardenKitingInput(target);
				applyOwnerGravity(owner);
				return;
			}
			if (canStrikeNow(target)) {
				smoothLookAt(target.getX(), target.getEyeY(), target.getZ());
				clearMovementInput();
			} else {
				moveToPosition(target.position(), 1.0f);
			}
			applyOwnerGravity(owner);
			return;
		}
		double distSqr = this.distanceToSqr(owner);
		if (distSqr > FOLLOW_RESUME_DISTANCE_SQR) {
			moveToPosition(owner.position(), 1.0f);
		} else {
			smoothLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
			clearMovementInput();
		}
	}

	/**
	 * 跟随分支的决策：负责离主人过远时的短距传送拉回，以及"卡住兜底"——
	 * 玩家在高处/隔墙、寻路失败顶墙时，长时间无法推进就传送到玩家身边。
	 * 攻击逻辑由模式逻辑（近战/射手）负责。
	 */
	private void updateFollowMind() {
		Player owner = getOwnerPlayer();
		if (owner == null || !this.isAlive()) {
			return;
		}
		double distSqr = this.distanceToSqr(owner);
		// 1) 卡住兜底：有工作/战斗目标时优先处理（不打断），仅纯跟随场景生效
		if (hasActiveTask()) {
			stuckTicks = 0;
			lastStuckPos = null;
			return;
		}
		// 有移动意图、贴地、且距主人仍较远时，检测是否长时间无法水平推进
		boolean moveIntent = this.zza != 0.0f || this.xxa != 0.0f;
		if (moveIntent && this.onGround() && distSqr > FOLLOW_STUCK_TELEPORT_DISTANCE_SQR) {
			if (lastStuckPos == null) {
				lastStuckPos = this.position();
				return;
			}
			double dx = this.getX() - lastStuckPos.x;
			double dz = this.getZ() - lastStuckPos.z;
			lastStuckPos = this.position();
			if (dx * dx + dz * dz < STUCK_MOVE_EPS_SQR) {
				if (++stuckTicks >= STUCK_TELEPORT_TICKS) {
					stuckTicks = 0;
					lastStuckPos = null;
					teleportNearOwner(owner);
				}
			} else {
				stuckTicks = 0;
			}
		} else {
			stuckTicks = 0;
			lastStuckPos = null;
		}
	}

	/** 是否有正在处理的工作/战斗目标（卡住传送只兜底纯跟随场景，避免打断工作/战斗）。 */
	private boolean hasActiveTask() {
		int mode = getActiveMode();
		if (mode == DollMode.CHOP.getIndex() && chopTargetPos != null && isLogBlock(chopTargetPos)) {
			return true;
		}
		if (mode == DollMode.FARM.getIndex() && farmTargetPos != null && isValidFarmTarget(farmTargetPos)) {
			return true;
		}
		if (mode == DollMode.MINE.getIndex() && hasValidMineTarget()) {
			return true;
		}
		if (mode == DollMode.MELEE.getIndex() && meleeTarget != null && meleeTarget.isAlive() && !meleeTarget.isRemoved()) {
			return true;
		}
		if (mode == DollMode.RANGED.getIndex() && rangedTarget != null && rangedTarget.isAlive() && !rangedTarget.isRemoved()) {
			return true;
		}
		return false;
	}

	/**
	 * 闻声助战：聚合主人最近 {@link #TARGET_REMEMBER_TICKS} tick 内的作战目标。
	 * <p>
	 * 双源：① 主人主动攻击之敌（getLastHurtMob）；② 攻击主人之敌（getLastHurtByMob）。
	 * 不问目见（隔墙亦知），但须在 {@link #ASSIST_HEAR_RADIUS} 疆界之内，且存活、非玩家、非自身。
	 * 近战/远程模式均可触发，不要求开启跟随。
	 */
	private LivingEntity resolveAssistTarget() {
		Player owner = getOwnerPlayer();
		if (owner == null) {
			return null;
		}
		// 主攻之敌优先（主动助战）；被袭之敌次之（应激相救）
		if (owner.getLastHurtMobTimestamp() + TARGET_REMEMBER_TICKS >= owner.tickCount) {
			LivingEntity victim = owner.getLastHurtMob();
			if (isAssistable(victim)) {
				return victim;
			}
		}
		if (owner.getLastHurtByMobTimestamp() + TARGET_REMEMBER_TICKS >= owner.tickCount) {
			LivingEntity attacker = owner.getLastHurtByMob();
			if (isAssistable(attacker)) {
				return attacker;
			}
		}
		return null;
	}

	/** 闻声助战的目标判据：存活、非自身、非玩家，且落于疆界之内（不问目见）。 */
	private boolean isAssistable(LivingEntity target) {
		return target != null && target.isAlive() && !target.isRemoved()
			&& target != this && !(target instanceof Player)
			&& this.distanceToSqr(target) <= ASSIST_HEAR_RADIUS_SQR;
	}

	private void teleportNearOwner(Player owner) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// 传送后位置变化：清空导航状态，避免继续沿旧路径/旧直线方向移动
		navigator.clearPath();
		directMoveMode = false;
		lastNavTarget = null;
		BlockPos playerPos = owner.blockPosition();
		// 在玩家周围逐层找可站立空位，避免直接传送到玩家坐标卡进方块
		for (int d = 0; d <= 4; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
						continue;
					}
					BlockPos candidate = playerPos.offset(dx, 0, dz);
					if (isStandableSpot(this.level(), candidate)) {
						this.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
						return;
					}
				}
			}
		}
		// 兜底：直接传到玩家坐标
		Vec3 pos = owner.position();
		this.teleportTo(pos.x, pos.y, pos.z);
	}

	/** 该格子可站立：脚下是固体支撑，自身与头顶非固体（实体高 1.8 格）。 */
	private static boolean isStandableSpot(Level level, BlockPos pos) {
		return !!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			&& !!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
	}

	/**
	 * 刷怪蛋召回：把人偶传送到指定维度/位置附近，并播放末影人传送音效与传送门粒子。
	 * 同维度用原位传送；跨维度用 teleportTo(ServerLevel, ...) 传送。
	 *
	 * @return 是否找到可站立落点并完成传送
	 */
	public boolean teleportToSpot(ServerLevel targetLevel, BlockPos near) {
		BlockPos spot = findStandableSpot(targetLevel, near);
		if (spot == null) {
			return false;
		}
		double x = spot.getX() + 0.5;
		double y = spot.getY();
		double z = spot.getZ() + 0.5;
		if (this.level() == targetLevel) {
			this.teleportTo(x, y, z);
		} else {
			this.teleportTo(targetLevel, x, y, z, Set.of(), this.getYRot(), this.getXRot(), false);
		}
		// 末影人传送音效 + 传送门粒子，让玩家知道人偶被拉过来了
		targetLevel.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 1.0f);
		targetLevel.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 32, 0.3, 0.5, 0.3, 0.1);
		// 召回传送 = 紧急召回：除跟随外，所有模式及模式配置全部重置。
		// 人偶到了新位置就是一个"干净"状态，不会继续旧位置的劳作任务。
		resetModeWorkState();
		clearWorkArea();
		clearTunnelConfig();
		getEntityData().set(DATA_ACTIVE_MODE, -1);
		return true;
	}

	/** 在目标位置附近逐层寻找可站立空位（优先点击处，向四周、上下扩展）。 */
	private static BlockPos findStandableSpot(Level level, BlockPos near) {
		for (int d = 0; d <= 4; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
						continue;
					}
					for (int dy = 1; dy >= -2; dy--) {
						BlockPos candidate = near.offset(dx, dy, dz);
						if (isStandableSpot(level, candidate)) {
							return candidate;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * 玩家式近战攻击：复用官方 stabAttack 统一入口，自动处理附魔伤害、
	 * 击退、武器损耗、附魔特效（火焰等）与攻击音效，并触发挥臂动画。
	 *
	 * @return 是否成功命中
	 */
	public boolean attackTarget(LivingEntity target) {
		if (attackCooldown > 0) {
			return false;
		}
		// 26.2 的 stabAttack 只调用 ItemStack.hurtEnemy（Item.hurtEnemy 大多为空实现），
		// 不会调用 ItemStack.postHurtEnemy——近战武器耐久消耗实际在 postHurtEnemy
		// （按 Weapon.itemDamagePerAttack 扣耐久）。必须命中后手动补一次，
		// 否则人偶拿剑/斧打怪，武器耐久永远不会下降。
		ItemStack weapon = getItemBySlot(EquipmentSlot.MAINHAND);
		// 海洋人偶甲——海洋人偶穿戴海洋甲时近战伤害按件数提升（每件 +10%，最高 +40%）；非海洋人偶不受影响
		float meleeDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if (this.isSeaDoll()) {
			int pieces = SeaArmorItem.countSeaArmor(this);
			if (pieces > 0) {
				meleeDamage = (float) (meleeDamage * (1.0 + 0.10 * pieces));
			}
		}
		boolean hit = this.stabAttack(
			EquipmentSlot.MAINHAND,
			target,
			meleeDamage,
			true, // 造成伤害
			true, // 附带击退
			false // 不把骑乘者拉下坐骑
		);
		attackCooldown = ATTACK_COOLDOWN_TICKS;
		this.swing(InteractionHand.MAIN_HAND); // 无论是否命中都挥臂，视觉反馈
		if (hit && !weapon.isEmpty()) {
			weapon.postHurtEnemy(target, this);
		}
		// 命中后将目标 lastHurtByPlayer 设为人偶主人，
		// 使怪物掉落经验/稀有掉落（killed_by_player 条件）正确触发。
		// 原版 resolvePlayerResponsibleForDamage 只处理 Player 和 tamed Wolf，
		// 人偶不属于这两类，stabAttack 内部 hurtServer 调用后会被清空，故在此补设。
		if (hit && this.getOwnerUuid() != null) {
			target.setLastHurtByPlayer(this.getOwnerUuid(), 100);
		}
		// 下界人偶火焰附加：近战命中后点燃目标 8 秒；手持地狱剑翻倍 16 秒（灼烧伤害×2）
			if (hit && getDollVariant() == DollVariant.NETHER) {
				target.igniteForSeconds(hasNetherSwordEquipped() ? 16.0f : 8.0f);
			}
			// 末影斧暴击音效 / 粒子（人偶端：玩家端由 PlayerEnderAxeMixin 处理）
			if (hit && weapon.getItem() instanceof EnderAxeItem && this.level() instanceof ServerLevel serverLevel) {
				serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
				serverLevel.sendParticles(ParticleTypes.CRIT,
					target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					15, 0.2, 0.2, 0.2, 0.0);
				serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
					target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					15, 0.2, 0.2, 0.2, 0.0);
			}
			return hit;
	}

	/**
	 * 幽匿人偶无武器时的风筝移动：与射手模式一致的拉扯行为——
	 * 太近后退、理想距离内站定、太远追击。不依赖武器类型，
	 * 固定使用弓的射程参数（5~15 格），供音波攻击在安全距离输出。
	 */
	private void applyWardenKitingInput(LivingEntity target) {
		double distSqr = this.distanceToSqr(target);
		if (distSqr < RANGED_TOO_CLOSE_DISTANCE_SQR) {
			retreatFrom(target.position());
		} else if (distSqr > RANGED_BOW_IDEAL_MAX_DISTANCE_SQR) {
			moveToPosition(target.position(), 1.0f);
		} else {
			smoothLookAt(target.getX(), target.getEyeY(), target.getZ());
			clearMovementInput();
		}
	}

	/** 近战模式：向当前的近战目标移动；无目标或已贴近则停下。强制目标不受追击距离限制。 */
	private void applyMeleeInput() {
		if (meleeTarget == null
			|| (forcedTargetUuid == null && this.distanceToSqr(meleeTarget) > MELEE_MAX_PURSUE_DISTANCE_SQR)) {
			clearMovementInput();
			return;
		}
		// 幽匿/下界/末影人偶无近战武器：使用风筝移动（与射手模式一致的拉扯），音波/烈焰弹/龙息负责伤害
		if ((isWardenDoll() || isNetherDoll() || isEnderDoll() || isSeaDoll()) && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			applyWardenKitingInput(meleeTarget);
			return;
		}
		if (canStrikeNow(meleeTarget)) {
			smoothLookAt(meleeTarget.getX(), meleeTarget.getEyeY(), meleeTarget.getZ());
			clearMovementInput();
			return;
		}
		// 使用玩家疾跑速度（0.13），计算 speedFactor
		float speedFactor = MELEE_MOVE_SPEED / (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
		moveToPosition(meleeTarget.position(), speedFactor);
	}

	/**
	 * 近战模式决策：指挥棒指定的强制目标优先（无距离限制），否则正常搜寻附近敌对生物。
	 */
	private void updateMeleeMind() {
		attackCooldown = Math.max(0, attackCooldown - 1);
		// 运行时前置检查（方案A）：普通人偶主手+物品栏均无近战武器 → 关闭模式并同步控制面板高亮
		if (!hasInnateCombatAbility()
				&& getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && findMeleeWeaponInHotbar() == -1) {
			Player owner = getOwnerPlayer();
			if (owner != null && meleeTarget != null) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".melee_stop_no_weapon", dollDisplayName()));
				voiceNoTool();
			}
			meleeTarget = null;
			clearMovementInput();
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		// 指挥棒指定的强制目标优先：跳过距离检查和自动搜寻
		LivingEntity forced = resolveForcedTarget();
		if (forced != null) {
			meleeTarget = forced;
		} else {
			if (meleeTarget != null && (!meleeTarget.isAlive() || meleeTarget.isRemoved()
				|| this.distanceToSqr(meleeTarget) > MELEE_MAX_PURSUE_DISTANCE_SQR)) {
				meleeTarget = null;
			}
			if (meleeTarget == null) {
				meleeTarget = findHostileTarget();
			}
		}
		if (meleeTarget != null && canStrikeNow(meleeTarget)) {
			// 幽匿/下界/末影人偶无近战武器时不进行近战攻击，仅保留目标供音波/烈焰弹/龙息使用
			if (!((isWardenDoll() || isNetherDoll() || isEnderDoll() || isSeaDoll()) && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty())) {
				attackTarget(meleeTarget);
			}
		}
	}

	/**
	 * 在搜寻半径内找到最近的敌对生物。优先协助主人正在攻击的目标，
	 * 否则搜寻附近实现 Enemy 接口的敌对生物（僵尸、骷髅、幻翼等），无则返回 null。
	 * 跟随开启时，以玩家为中心搜寻，优先攻击离玩家最近的敌人。
	 */
	private LivingEntity findHostileTarget() {
		// 当前目标仍有效时直接复用，避免每 tick 全量搜索
		if (meleeTarget != null && meleeTarget.isAlive() && !meleeTarget.isRemoved()
			&& this.distanceToSqr(meleeTarget) <= MELEE_MAX_PURSUE_DISTANCE_SQR) {
			return meleeTarget;
		}
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		// 主人正在攻击的目标最优先（不要求开启跟随）
		LivingEntity assistTarget = resolveAssistTarget();
		if (assistTarget != null) {
			return assistTarget;
		}
		
		Player owner = getOwnerPlayer();
		boolean followEnabled = isFollowEnabled();
		
		// 跟随开启时，以玩家为中心；否则以人偶自身为中心
		Vec3 center = (followEnabled && owner != null) ? owner.position() : this.position();
		AABB area = new AABB(
			center.x - MELEE_SEARCH_RANGE, center.y - MELEE_SEARCH_RANGE, center.z - MELEE_SEARCH_RANGE,
			center.x + MELEE_SEARCH_RANGE, center.y + MELEE_SEARCH_RANGE, center.z + MELEE_SEARCH_RANGE);
		
		List<LivingEntity> candidates = serverLevel.getEntities(
			EntityTypeTest.forClass(LivingEntity.class), area,
			e -> e.isAlive() && e != this && !(e instanceof Player) && e instanceof Enemy && hasClearSightTo(e));
		
		LivingEntity nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (LivingEntity e : candidates) {
			// 跟随开启时，计算离玩家的距离；否则计算离人偶的距离
			double d = (followEnabled && owner != null) ? owner.distanceToSqr(e) : this.distanceToSqr(e);
			if (d < nearestDist) {
				nearestDist = d;
				nearest = e;
			}
		}
		return nearest;
	}

	// ------------------------------------------------------------------
	// 幽匿人偶音波攻击（WARDEN 变体被动）
	// ------------------------------------------------------------------

	/**
	 * 音波攻击决策：近战/射手模式下有目标时被动蓄力发射音波。
	 * 无距离限制——只要锁定目标就蓄力，4 秒冷却期间不蓄力。
	 * 蓄力 1.5 秒后发射：音爆音效 + 粒子 + 拉扯目标 + 5 点伤害。
	 */
	private void handleWardenSonicBoom() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (sonicCooldown > 0) {
			sonicCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = getActiveMode() == DollMode.MELEE.getIndex() ? meleeTarget : rangedTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			sonicChargeTicks = -1;
			return;
		}

		// 开始蓄力
		if (sonicChargeTicks < 0) {
			sonicChargeTicks = 0;
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.WARDEN_SONIC_CHARGE, this.getSoundSource(), 3.0f, 1.0f);
		}

		// 蓄力中
		sonicChargeTicks++;
		if (sonicChargeTicks >= SONIC_CHARGE_TICKS) {
			fireSonicBoom(target);
			sonicChargeTicks = -1;
			sonicCooldown = SONIC_COOLDOWN_TICKS;
		}
	}

	/**
	 * 发射音波攻击：粒子 + 拉扯目标 + 伤害。
	 * 音效（蓄力阶段 WARDEN_SONIC_CHARGE）已在蓄力开始时播放，
	 * 这里补发射瞬间的 WARDEN_SONIC_BOOM 音效。
	 */
	private void fireSonicBoom(LivingEntity target) {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		double dx = this.getX() - target.getX();
		double dz = this.getZ() - target.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 0.5) return;

		// 音爆音效（发射瞬间，在目标位置播放）
		serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.WARDEN_SONIC_BOOM, this.getSoundSource(), 3.0f, 1.0f);

		// 粒子：从人偶到目标画一条音波线
		double stepX = dx / 10.0;
		double stepZ = dz / 10.0;
		for (int i = 0; i < 10; i++) {
			double px = target.getX() + stepX * i;
			double pz = target.getZ() + stepZ * i;
			serverLevel.sendParticles(
				ParticleTypes.SONIC_BOOM,
				px, target.getY() + 0.5, pz,
				1, 0, 0, 0, 0);
		}
		// 目标位置额外爆一下
		serverLevel.sendParticles(
			ParticleTypes.SONIC_BOOM,
			target.getX(), target.getY() + 0.5, target.getZ(),
			5, 0.3, 0.3, 0.3, 0);

		// 拉扯目标：向人偶方向拉近
		double pull = distance * SONIC_PULL_STRENGTH;
		target.setDeltaMovement(
			dx / distance * pull,
			0.3,
			dz / distance * pull
		);
		target.hurtMarked = true;

		// 造成伤害（穿甲音波，对标原版 Warden sonicBoom）
		target.hurtServer(serverLevel, this.damageSources().sonicBoom(this), SONIC_BOOM_DAMAGE);

		// 音波命中后同样补设 lastHurtByPlayer，使经验/稀有掉落正常
		if (this.getOwnerUuid() != null) {
			target.setLastHurtByPlayer(this.getOwnerUuid(), 100);
		}

		// 施加缓慢 V（100 tick = 5 秒，覆盖到下次音波冷却结束）
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4));
	}

	// ------------------------------------------------------------------
	// 海洋人偶激光蓄力（SEA 变体被动）
	// ------------------------------------------------------------------

	/**
	 * 激光蓄力决策：近战/射手模式下锁定目标后自动蓄力发射。
	 * <p>
	 * 对齐原版守卫者：蓄力期间需要维持视线，视线中断则重置蓄力。
	 * 蓄力 1.5 秒 → 发射 hitscan 光束 → 魔法伤害（绕护甲）→ 1 秒冷却。
	 * <p>
	 * 与其他变体的差异：
	 * <ul>
	 *   <li>幽匿音波：hitscan，高伤(20) + 拉扯，无视线要求，4 秒冷却</li>
	 *   <li>下界/末影：投射物，弹道可被遮挡</li>
	 *   <li>海洋激光：hitscan，中伤(9) + 魔法绕甲，需视线，1 秒冷却 = 高频低伤法师</li>
	 * </ul>
	 */
	private void handleSeaLaser() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (laserCooldown > 0) {
			laserCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = getActiveMode() == DollMode.MELEE.getIndex() ? meleeTarget : rangedTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			laserChargeTicks = -1;
			return;
		}

		// 射程检查
		if (this.distanceToSqr(target) > LASER_RANGE_SQR) {
			laserChargeTicks = -1;
			return;
		}

		// 视线检查：蓄力期间必须维持视线，断了就重置（原版守卫者核心机制）
		if (!hasLineOfSight(target)) {
			laserChargeTicks = -1;
			return;
		}

		// 开始蓄力
		if (laserChargeTicks < 0) {
			laserChargeTicks = 0;
			// 蓄力开始：渐强压迫感音效（WARDEN_SONIC_CHARGE 音量 2.5），给玩家"正在蓄力"的张力预告
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.WARDEN_SONIC_CHARGE, this.getSoundSource(), 2.5f, 1.0f);
		}

		// 蓄力中：每 2 tick 画一条 eye→target 瞄准线（鹦鹉螺粒子），并在目标处加大电火花迸发，
		// 让玩家清晰看到"正在锁定"，补足原版守卫者蓄力阶段缺乏的视觉张力。
		laserChargeTicks++;
		if (laserChargeTicks % 2 == 0) {
			double ex = this.getX();
			double ey = this.getY() + this.getEyeHeight();
			double ez = this.getZ();
			double tx = target.getX();
			double ty = target.getY() + target.getEyeHeight();
			double tz = target.getZ();
			int lineSteps = 12;
			for (int i = 0; i <= lineSteps; i++) {
				double t = i / (double) lineSteps;
				serverLevel.sendParticles(ParticleTypes.NAUTILUS,
					ex + (tx - ex) * t, ey + (ty - ey) * t, ez + (tz - ez) * t,
					1, 0.04, 0.04, 0.04, 0.0);
			}
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				tx, ty, tz, 6, 0.4, 0.4, 0.4, 0.08);
		}

		// 蓄力完成
		if (laserChargeTicks >= LASER_CHARGE_TICKS) {
			fireSeaLaser(serverLevel, target);
			laserChargeTicks = -1;
			laserCooldown = LASER_COOLDOWN_TICKS;
		}
	}

	/**
	 * 发射激光：粒子光束 + 魔法伤害。
	 * <p>
	 * hitscan 机制——无投射物，视线内必定命中（和幽匿音波一致）。
	 * 伤害类型为 indirectMagic，绕过护甲（原版守卫者行为）。
	 * 粒子用 NAUTILUS（海洋主题）画光束线，ELECTRIC_SPARK 在目标位置爆开。
	 */
	private void fireSeaLaser(ServerLevel serverLevel, LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dy = (target.getY() + target.getEyeHeight()) - (this.getY() + this.getEyeHeight());
		double dz = target.getZ() - this.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance < 0.5) return;

		// 光束粒子：从人偶眼睛到目标画一条 NAUTILUS 粒子线（每步 3 颗 + END_ROD 亮芯），
		// 加密并加亮芯后肉眼清晰可见，给发射瞬间充足张力。
		int steps = Math.max(8, (int) (distance * 1.5));
		double stepX = dx / steps;
		double stepY = dy / steps;
		double stepZ = dz / steps;
		for (int i = 0; i < steps; i++) {
			double px = this.getX() + stepX * i;
			double py = this.getY() + this.getEyeHeight() + stepY * i;
			double pz = this.getZ() + stepZ * i;
			serverLevel.sendParticles(ParticleTypes.NAUTILUS, px, py, pz, 3, 0.05, 0.05, 0.05, 0.0);
			serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
		}

		// 目标位置电火花大迸发
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
			target.getX(), target.getY() + target.getEyeHeight(), target.getZ(),
			16, 0.5, 0.5, 0.5, 0.12);

		// 发射瞬间补响亮音效（原版守卫者激光命中的"啪"一声，vol 3.0 给张力收束）
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.GUARDIAN_ATTACK, this.getSoundSource(), 3.0f, 1.0f);

		// 造成魔法伤害（绕护甲，对齐原版守卫者 indirectMagic）
		// 海洋人偶甲——海洋人偶穿戴海洋甲时激光伤害按件数提升（每件 +10%，最高 +40%）；非海洋人偶不受影响
		float laserDamage = LASER_DAMAGE;
		if (this.isSeaDoll()) {
			int pieces = SeaArmorItem.countSeaArmor(this);
			if (pieces > 0) {
				laserDamage = (float) (laserDamage * (1.0 + 0.10 * pieces));
			}
		}
		target.hurtServer(serverLevel,
			this.damageSources().indirectMagic(this, this), laserDamage);

		// 补设 lastHurtByPlayer，使经验/稀有掉落正常
		if (this.getOwnerUuid() != null) {
			target.setLastHurtByPlayer(this.getOwnerUuid(), 100);
		}
	}

	// ------------------------------------------------------------------
	// 下界人偶烈焰弹（NETHER 变体被动）
	// ------------------------------------------------------------------

	/**
	 * 烈焰弹决策：近战/射手模式下有目标时自动发射 WitherSkull（凋灵骷髅头颅弹）。
	 * 无蓄力前摇（对齐原版烈焰人/凋灵行为），3 秒冷却。
	 * 弹道投射物——可被方块遮挡，与音波的"必定命中"形成差异。
	 * 命中后施加凋零效果（普通 10 秒 / 困难 40 秒 Wither I），伤害由 WitherSkullMixin 替换为 13。
	 */
	private void handleNetherFireball() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		// 冷却中
		if (fireballCooldown > 0) {
			fireballCooldown--;
			return;
		}

		// 获取当前模式的目标
		LivingEntity target = getActiveMode() == DollMode.MELEE.getIndex() ? meleeTarget : rangedTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}

		fireWitherSkull(serverLevel, target);
		fireballCooldown = FIREBALL_COOLDOWN_TICKS;
	}

	/**
	 * 发射凋灵骷髅头颅弹：创建 WitherSkull 并朝目标方向射出。
	 * <p>
	 * 使用 WitherSkull 而非 SmallFireball——WitherSkull 有专属渲染器（投射物尺寸），
	 * 头颅模型在空中飞行视觉效果远优于 SmallFireball 的 item 级渲染。
	 * <p>
	 * 原版凋灵发射逻辑：normalize 方向向量 → WitherSkull 构造（内部 assignDirectionalMovement
	 * 用 accelerationPower 缩放为速度）→ addFreshEntity。
	 * <p>
	 * WitherSkull 的 onHit 会产生爆炸破坏方块——由 WitherSkullMixin 的 @Redirect 禁用。
	 * WitherSkull 的 onHitEntity 硬编码 8.0f 伤害——由 WitherSkullMixin 的 @Redirect 替换为 13。
	 * 凋零效果（Wither I）由 WitherSkullMixin 的 @Redirect 替换为燃烧 5 秒，和下界主题契合。
	 */
	private void fireWitherSkull(ServerLevel serverLevel, LivingEntity target) {
		// 方向向量：从人偶眼睛高度指向目标中心
		double dx = target.getX() - this.getX();
		double dy = (target.getY() + 0.5) - (this.getY() + this.getEyeHeight());
		double dz = target.getZ() - this.getZ();
		Vec3 direction = new Vec3(dx, dy, dz).normalize();

		WitherSkull skull = new WitherSkull(serverLevel, this, direction);
		skull.setPos(this.getX(), this.getY() + this.getEyeHeight(), this.getZ());

		serverLevel.addFreshEntity(skull);

		// 发射音效（烈焰人发射火球的音效，与下界主题一致）
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.BLAZE_SHOOT, this.getSoundSource(), 1.0f, 1.0f);
	}

	// ------------------------------------------------------------------
	// 末影人偶龙息喷吐 + 瞬移处决（ENDER 变体被动）
	// ------------------------------------------------------------------

	/**
	 * 龙息喷吐决策：近战/射手模式下有目标时自动发射 WitherSkull（和下界人偶统一投射物）。
	 * 命中后由 WitherSkullMixin 生成龙息云（AreaEffectCloud），对范围内实体造成持续即时伤害。
	 * 3 秒冷却，弹道投射物可被遮挡。
	 */
	private void handleEnderBreath() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		if (breathCooldown > 0) {
			breathCooldown--;
			return;
		}

		LivingEntity target = getActiveMode() == DollMode.MELEE.getIndex() ? meleeTarget : rangedTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}

		fireEnderSkull(serverLevel, target);
		breathCooldown = BREATH_COOLDOWN_TICKS;
	}

	/**
	 * 发射龙息头颅弹：创建 WitherSkull 并朝目标方向射出（和下界人偶 fireWitherSkull 统一）。
	 * <p>
	 * 使用 WitherSkull 而非 DragonFireball——统一投射物类型，复用 WitherSkullRenderer 渲染管线。
	 * WitherSkullMixin 按 owner 变体区分命中效果：
	 * <ul>
	 *   <li>NETHER：伤害 20 + 燃烧 5 秒（原有行为）</li>
	 *   <li>ENDER：直接命中 2 点伤害 + 在命中点生成龙息云（AreaEffectCloud 即时伤害 II，判定间隔 10 tick）</li>
	 * </ul>
	 * 渲染端按变体区分贴图：WitherSkullRendererMixin 中 NETHER→nether_doll.png，ENDER→ender_doll.png。
	 */
	private void fireEnderSkull(ServerLevel serverLevel, LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dy = (target.getY() + 0.5) - (this.getY() + this.getEyeHeight());
		double dz = target.getZ() - this.getZ();
		Vec3 direction = new Vec3(dx, dy, dz).normalize();

		WitherSkull skull = new WitherSkull(serverLevel, this, direction);
		skull.setPos(this.getX(), this.getY() + this.getEyeHeight(), this.getZ());

		serverLevel.addFreshEntity(skull);

		// 烈焰人发射音效（和下界人偶统一）
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.BLAZE_SHOOT, this.getSoundSource(), 1.0f, 1.0f);
	}

	/**
	 * 瞬移处决决策：近战/射手模式下目标血量 ≤ 30% 时触发。
	 * 瞬移到目标身边 → 直接斩杀 → 瞬移回原位。
	 * 60 秒冷却。处决时有末影瞬移粒子和音效。
	 */
	private void handleEnderExecute() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;

		if (executeCooldown > 0) {
			executeCooldown--;
			return;
		}

		LivingEntity target = getActiveMode() == DollMode.MELEE.getIndex() ? meleeTarget : rangedTarget;
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}

		// 血量阈值判定：持末影斧时 50%，否则 30%
		ItemStack mainHand = getItemBySlot(EquipmentSlot.MAINHAND);
		float threshold = mainHand.getItem() instanceof EnderAxeItem
			? EXECUTE_HEALTH_THRESHOLD_AXE : EXECUTE_HEALTH_THRESHOLD;
		float healthRatio = target.getHealth() / target.getMaxHealth();
		if (healthRatio > threshold) {
			return;
		}

		// 记住原位，处决后瞬移回来
		double originX = this.getX();
		double originY = this.getY();
		double originZ = this.getZ();

		// 瞬移到目标身边（偏移 1 格，避免卡在目标体内）
		double tx = target.getX() + (this.random.nextDouble() - 0.5) * 2.0;
		double ty = target.getY();
		double tz = target.getZ() + (this.random.nextDouble() - 0.5) * 2.0;

		// 发射瞬移粒子（离开位置）
		serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(),
			20, 0.5, 1.0, 0.5, 0.5);
		// 瞬移音效
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0f, 1.0f);

		// 执行瞬移
		this.teleportTo(tx, ty, tz);

		// 到达粒子（到达位置）
		serverLevel.sendParticles(ParticleTypes.PORTAL, tx, ty + 1.0, tz,
			20, 0.5, 1.0, 0.5, 0.5);

		// 处决：直接对目标造成致命伤害
		target.hurtServer(serverLevel, this.damageSources().mobAttack(this), Float.MAX_VALUE);

		// 处决后短暂延迟再瞬移回原位（用标记 + tick 计数器实现非阻塞延迟）
		// 简化方案：立即瞬移回去
		this.teleportTo(originX, originY, originZ);
		serverLevel.sendParticles(ParticleTypes.PORTAL, originX, originY + 1.0, originZ,
			20, 0.5, 1.0, 0.5, 0.5);
		serverLevel.playSound(null, originX, originY, originZ,
			SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0f, 1.0f);

		executeCooldown = EXECUTE_COOLDOWN_TICKS;
	}



	/**
	 * 耕种模式移动输入：走 A* 路径接近工作目标（可绕路、逐格爬台阶），
	 * 进入工作距离后停下；目标不可达（无路可走）时立即放弃并重新选目标，
	 * 避免直线移动撞上高差 ≥2 格的墙体卡死。
	 * 目标由 updateFarmMind 在决策阶段刷新，本方法只负责本 tick 的位移。
	 */
	private void applyFarmInput() {
		if (farmTargetPos == null || !isValidFarmTarget(farmTargetPos)) {
			clearMovementInput();
			return;
		}
		if (canReachFarmBlock(farmTargetPos)) {
			clearMovementInput();
			return;
		}
		Vec3 target = Vec3.atCenterOf(farmTargetPos);
		boolean recalc = farmNavTarget == null
			|| target.distanceToSqr(farmNavTarget) > FARM_NAV_RECALC_SQR
			|| navigator.isPathDone();
		if (recalc) {
			farmNavTarget = target;
			if (!navigator.computePath(target)) {
				// 无路可达（如目标在高处且无台阶）→ 放弃该目标，稍作停顿后重新选
				farmTargetPos = null;
				farmNavTarget = null;
				farmActionCooldown = Math.max(farmActionCooldown, 5);
				clearMovementInput();
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			// 路径走完仍未到达（目标在障碍后）→ 重新规划；仍不可达则交给下一次放弃
			farmNavTarget = null;
			clearMovementInput();
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * FARM_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/** 是否够得着目标方块：水平距离在锄地半径内，且高度差允许翻越 1 格台阶后触达。 */
	private boolean canReachFarmBlock(BlockPos pos) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		if (dx * dx + dz * dz > FARM_REACH_SQR) {
			return false;
		}
		double dy = (pos.getY() + 0.5) - this.getY();
		return Math.abs(dy) <= 2.5;
	}

	/**
	 * 耕种模式决策：选择工作目标（锄地/放水/收获/播种），到达工作距离后执行动作。
	 * 动作之间有冷却，避免每 tick 疯狂锄地/收获。
	 */
	private void updateFarmMind() {
		farmActionCooldown = Math.max(0, farmActionCooldown - 1);
		if (farmTargetPos != null && !isValidFarmTarget(farmTargetPos)) {
			farmTargetPos = null;
		}
		// 有作业区但人偶不在区域内 → 先前往作业区再种收（否则原地搜索够不到远处责任田）。
		// 跟随开启时作业区不生效（跟随与作业区互斥），人偶跟随玩家优先。
		if (!isFollowEnabled() && hasWorkArea() && !isWithinWorkAreaXZ()) {
			navigateToWorkArea();
			return;
		}
		if (farmTargetPos == null) {
			farmTargetPos = selectFarmTarget();
		}
		// 方案A：种子耗尽时不立即关模式，先收完附近的成熟作物；
		// 仅当既无种子、附近也无任何可干活目标（selectFarmTarget 返回 null）时才关闭模式并同步控制面板高亮。
		if (farmTargetPos == null && !hasSeedsInInventory()) {
			if (getEntityData().get(DATA_ACTIVE_MODE) != -1) {
				Player owner = getOwnerPlayer();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable(
						"message." + DollModConstants.MOD_ID + ".farm_stop_no_seeds", dollDisplayName()));
					voiceNoTool();
				}
				farmAnchor = null;
				farmWaterPlaced = false;
				farmNavTarget = null;
				clearMovementInput();
				getEntityData().set(DATA_ACTIVE_MODE, -1);
				pushSnapshotToOwner();
			}
			return;
		}
		if (farmTargetPos == null) {
			clearMovementInput();
			return;
		}
		if (canReachFarmBlock(farmTargetPos)) {
			clearMovementInput();
			smoothFaceTowards(farmTargetPos.getX() + 0.5, farmTargetPos.getZ() + 0.5);
			if (farmActionCooldown <= 0) {
				performFarmAction(farmTargetPos);
				farmTargetPos = null; // 做完一个换下一个
				farmActionCooldown = FARM_ACTION_COOLDOWN;
			}
		}
	}

	/** 目标方块是否仍有效（移动途中方块可能被玩家改动）。 */
	private boolean isValidFarmTarget(BlockPos pos) {
		if (isMatureCropBlock(pos)) {
			return true;
		}
		if (isFarmlandWithAirAbove(pos) && hasSeedsInInventory()) {
			return true;
		}
		if (isTillableBlock(pos)) {
			return true;
		}
		return farmAnchor != null && pos.equals(farmAnchor) && !farmWaterPlaced;
	}

	/**
	 * 选择当前工作目标：
	 * 建造模式（快捷栏锄头+水桶且背包有种子）先在中心放水、再以水为中心锄 9x9 农田
	 * （先放水可让后续锄出的耕地立即被湿润，避免先耕的地干涸回泥土），建好后转入种收；
	 * 常规模式先收成熟作物，再往空耕地播种。
	 */
	private BlockPos selectFarmTarget() {
		if (hasHoeAndWaterInHotbar() && hasSeedsInInventory()) {
			ensureFarmAnchor();
			if (farmAnchor != null) {
				// 先放水：水井湿润半径 4 格，正好覆盖 9x9，后续锄地立即湿润
				if (!farmWaterPlaced) {
					return farmAnchor;
				}
				// 区域内还有未耕的方块 → 以水井为中心锄地
				BlockPos tillPos = findTillableInRegion();
				if (tillPos != null) {
					return tillPos;
				}
				// 农田建好 → 转入常规种收（锚点保留，防止原地反复重建）
			}
		} else {
			// 不再满足建造条件（锄头/水桶被拿走）→ 放弃建造状态
			farmAnchor = null;
			farmWaterPlaced = false;
		}
		// 常规种收：先收成熟作物，再补种空耕地
		BlockPos harvest = scanFarmBlocks(getFarmWorkCenter(), (int) FARM_SEARCH_RANGE, this::isMatureCropBlock);
		if (harvest != null) {
			return harvest;
		}
		if (!hasSeedsInInventory()) {
			return null;
		}
		return scanFarmBlocks(getFarmWorkCenter(), (int) FARM_SEARCH_RANGE, this::isFarmlandWithAirAbove);
	}

	/** 确保 9x9 农田锚点有效：离锚点过远或为空时，在附近重新选一块可耕方块作为中心。 */
	private void ensureFarmAnchor() {
		if (farmAnchor != null) {
			if (farmAnchor.distSqr(getFarmWorkCenter()) > FARM_ANCHOR_RESET_DISTANCE_SQR) {
				farmAnchor = null;
				farmWaterPlaced = false;
			} else {
				return;
			}
		}
		BlockPos anchor = scanFarmBlocks(getFarmWorkCenter(), (int) FARM_SEARCH_RANGE, this::isTillableBlock);
		if (anchor != null) {
			farmAnchor = anchor;
			farmWaterPlaced = false;
		}
	}

	/** 在 9x9 区域内找最近的可耕方块（中心留作水井，不参与锄地）。 */
	private BlockPos findTillableInRegion() {
		if (farmAnchor == null) {
			return null;
		}
		BlockPos center = farmAnchor;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -FARM_REGION_HALF; dx <= FARM_REGION_HALF; dx++) {
				for (int dz = -FARM_REGION_HALF; dz <= FARM_REGION_HALF; dz++) {
					if (dx == 0 && dz == 0) {
						continue; // 中心留给水井
					}
					BlockPos p = center.offset(dx, dy, dz);
					double d = p.distSqr(center);
					if (d >= bestDist) {
						continue;
					}
					if (!withinWorkAreaXZ(p)) {
						continue;
					}
					if (isTillableBlock(p)) {
						best = p;
						bestDist = d;
					}
				}
			}
		}
		return best;
	}

	/** 在当前目标方块执行对应动作：优先放水（中心），其次收获、播种、锄地。 */
	private boolean performFarmAction(BlockPos pos) {
		if (farmAnchor != null && pos.equals(farmAnchor) && !farmWaterPlaced) {
			return placeWaterAt(pos);
		}
		if (isMatureCropBlock(pos)) {
			return harvestCrop(pos);
		}
		if (isFarmlandWithAirAbove(pos)) {
			return plantSeed(pos);
		}
		if (isTillableBlock(pos)) {
			return tillBlock(pos);
		}
		return false;
	}

	/** 锄地：草方块/土径/泥土→耕地，砂土/缠根泥土→泥土（复刻原版锄头行为）。 */
	private boolean tillBlock(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		Block target = TILLABLE_TO_RESULT.get(state.getBlock());
		if (target == null || !level().isEmptyBlock(pos.above())) {
			return false;
		}
		level().setBlock(pos, target.defaultBlockState(), 3);
		this.playSound(SoundEvents.HOE_TILL, 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		ItemStack hoe = findHoeStack();
		if (!hoe.isEmpty()) {
			hoe.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	/** 中心放水：清空中心方块后放入水源（水桶不消耗，作为建造工具的象征）。 */
	private boolean placeWaterAt(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		if (!state.isAir()) {
			this.playSound(state.getSoundType().getBreakSound(), 1.0f, 1.0f);
			level().levelEvent(2001, pos, Block.getId(state));
			level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		level().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		farmWaterPlaced = true;
		this.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	/**
	 * 收获成熟作物：按玩家徒手收获规则计算掉落并放入人偶物品栏，
	 * 随后立刻用背包种子补种，避免农田空置。
	 */
	private boolean harvestCrop(BlockPos pos) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		BlockState state = level().getBlockState(pos);
		// 与玩家徒手收获一致的掉落表（含种子/作物/附魔概率等）
		LootParams.Builder builder = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
			.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
		List<ItemStack> drops = state.getDrops(builder);
		// 直接改为空气（不掉落物实体，改由自己入包）
		level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level().levelEvent(2001, pos, Block.getId(state));
		this.playSound(SoundEvents.CROP_BREAK, 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		for (ItemStack drop : drops) {
			addToDollInventory(drop);
		}
		// 收获后立刻补种（作物下方是耕地，种回原格）
		plantSeed(pos.below());
		return true;
	}

	/**
	 * 在指定耕地上种下种子（种子放在耕地正上方，从人偶物品栏取，成功消耗 1 个）。
	 * 注意：canSurvive/setBlock 用的是耕地上方一格——原版作物位置即耕地正上方，
	 * 传耕地本身会导致 canSurvive 检查耕地下方（泥土）而永远失败。
	 */
	private boolean plantSeed(BlockPos farmlandPos) {
		int seedSlot = findSeedSlotInInventory();
		if (seedSlot < 0) {
			return false;
		}
		ItemStack seed = inventory.getItem(seedSlot);
		BlockPos cropPos = farmlandPos.above();
		BlockState state = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
		if (!state.canSurvive(level(), cropPos)) {
			return false; // 光照/地面不符，不硬种
		}
		level().setBlock(cropPos, state, 3);
		seed.shrink(1);
		this.playSound(SoundEvents.CROP_PLANTED, 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		// 播种挥动期间主手临时显示种子（否则主手仍渲染锄头，视觉与动作不符）
		plantSourceSlot = seedSlot;
		plantSeedHandTicks = PLANT_SWING_TICKS;
		return true;
	}

	/**
	 * 物品放入人偶存储区（9-44，快捷栏亦作可容之地）：先堆叠同种物品，再放空格；装满则掉落在地。
	 * 只写存储区与快捷栏——护甲槽(1-4)/装饰槽(0/5/7/8)在 GUI 中禁止放置普通物品。
	 */
	public void addToDollInventory(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (int i = DollScreenHandler.DOLL_STORAGE_START; i <= DollScreenHandler.DOLL_HOTBAR_END; i++) {
			ItemStack slot = inventory.getItem(i);
			if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stack) || slot.getCount() >= slot.getMaxStackSize()) {
				continue;
			}
			int move = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
			slot.grow(move);
			stack.shrink(move);
			if (stack.isEmpty()) {
				return;
			}
		}
		for (int i = DollScreenHandler.DOLL_STORAGE_START; i <= DollScreenHandler.DOLL_HOTBAR_END; i++) {
			if (inventory.getItem(i).isEmpty()) {
				inventory.setItem(i, stack.copy());
				// 必须消费原栈：放入的是副本，若原栈不清空，调用方持有的引用（如
				// collectNearbyDrops 的掉落物实体栈）会与背包各持一份 → 物品复制 bug
				stack.setCount(0);
				return;
			}
		}
		// 存储区与快捷栏俱满：掉落在地（原栈已转移，同样消费，避免调用方再持有一份）
		if (this.level() instanceof ServerLevel serverLevel) {
			ItemEntity itemEntity = new ItemEntity(serverLevel, getX(), getY() + 0.5, getZ(), stack.copy());
			itemEntity.setDefaultPickUpDelay();
			serverLevel.addFreshEntity(itemEntity);
			stack.setCount(0);
		}
	}

	/** 人偶物品栏（全45格）是否同时有锄头和水桶（建造模式的前提）。 */
	private boolean hasHoeAndWaterInHotbar() {
		boolean hoe = false;
		boolean water = false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof HoeItem) {
				hoe = true;
			} else if (stack.getItem() == Items.WATER_BUCKET) {
				water = true;
			}
		}
		return hoe && water;
	}

	/** 人偶物品栏是否有种子（作物/瓜藤类方块物品）。 */
	private boolean hasSeedsInInventory() {
		return findSeedSlotInInventory() >= 0;
	}

	/** 找到人偶物品栏中第一个种子物品（CropBlock/StemBlock 的方块物品）。 */
	/**
	 * 找到人偶物品栏中第一个可播种种子的槽位（找不到返回 -1）。
	 * 返回槽位而非物品：播种挥动动画期间需要指向该槽让主手渲染种子。
	 */
	private int findSeedSlotInInventory() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isSeedItem(stack)) {
				return i;
			}
		}
		return -1;
	}

	/** 是否为可种在耕地上的种子：小麦/胡萝卜/土豆/甜菜根/火把花/瓶子草/西瓜/南瓜等。 */
	private boolean isSeedItem(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block block = blockItem.getBlock();
		return block instanceof CropBlock || block instanceof StemBlock;
	}

	/** 工作中心：跟随开启以玩家为中心，否则以人偶自身为中心。 */
	private BlockPos getFarmWorkCenter() {
		Player owner = getOwnerPlayer();
		if (isFollowEnabled() && owner != null) {
			return owner.blockPosition();
		}
		return this.blockPosition();
	}

	/** 在指定中心周围的球形范围（半径 radius，Y ±3）内扫描满足条件的方块，返回最近的一个。 */
	private BlockPos scanFarmBlocks(BlockPos center, int radius, java.util.function.Predicate<BlockPos> predicate) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int y = center.getY() - 3; y <= center.getY() + 3; y++) {
			for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					BlockPos p = new BlockPos(x, y, z);
					double d = p.distSqr(center);
					if (d > radius * radius || d >= bestDist) {
						continue;
					}
					if (!withinWorkAreaXZ(p)) {
						continue;
					}
					if (predicate.test(p)) {
						best = p;
						bestDist = d;
					}
				}
			}
		}
		return best;
	}

	/** 该位置是否为成熟作物（CropBlock 满龄 / StemBlock 满龄）。 */
	private boolean isMatureCropBlock(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof StemBlock) {
			return state.getValue(StemBlock.AGE) >= StemBlock.MAX_AGE;
		}
		return false;
	}

	/** 该位置是否为耕地且上方是空气（可播种）。 */
	private boolean isFarmlandWithAirAbove(BlockPos pos) {
		return level().getBlockState(pos).is(Blocks.FARMLAND) && level().isEmptyBlock(pos.above());
	}

	/** 该位置是否可锄地（锄头可耕方块且上方是空气）。 */
	private boolean isTillableBlock(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		return TILLABLE_TO_RESULT.containsKey(state.getBlock()) && level().isEmptyBlock(pos.above());
	}

	// ------------------------------------------------------------------
	// 喂食模式（FEED）
	// ------------------------------------------------------------------

	/**
	 * 喂食模式移动输入：以玩家为中心。玩家需要喂食（血 <19 且无饱和度且饥饿不满）
	 * 且背包有正向食物时，贴近玩家到 2 格内再喂（比跟随的 3.5 格更近）；
	 * 否则按普通跟随距离跟随。
	 */
	private void applyFeedInput() {
		Player owner = getOwnerPlayer();
		if (owner == null || !owner.isAlive() || owner.isSpectator()) {
			directMoveMode = false;
			clearMovementInput();
			return;
		}
		boolean needFeed = shouldFeedPlayer(owner) && !findPositiveFoodInInventory().isEmpty();
		double distSqr = this.distanceToSqr(owner);
		double stopDistanceSqr = needFeed ? FEED_CLOSE_DISTANCE_SQR : FOLLOW_RESUME_DISTANCE_SQR;
		if (distSqr > stopDistanceSqr) {
			moveToPosition(owner.position(), 1.0f);
		} else {
			smoothLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
			clearMovementInput();
		}
	}

	/** 喂食模式决策：玩家满足触发条件且人偶贴近到 2 格内时，消耗 1 个食物喂给玩家。 */
	private void updateFeedMind() {
		if (feedCooldown > 0) {
			feedCooldown--;
			return;
		}
		// 运行时前置检查（方案A）：食物耗尽 → 关闭模式并同步控制面板高亮
		if (!hasPositiveFoodInInventory()) {
			Player owner = getOwnerPlayer();
			if (owner != null) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".feed_stop_no_food", dollDisplayName()));
				voiceNoTool();
			}
			clearMovementInput();
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || !owner.isAlive()) {
			return;
		}
		if (!shouldFeedPlayer(owner)) {
			return;
		}
		double feedDistSqr = FEED_CLOSE_DISTANCE_SQR;
		if (this.distanceToSqr(owner) > feedDistSqr) {
			return; // 还没到位，由移动逻辑走过去
		}
		feedPlayer(owner);
	}

	/**
	 * 喂食触发条件：玩家血量低于 18 滴（9 颗心）或饥饿度低于 4（不足 2 个鸡腿），
	 * 两者满足其一即触发喂食。所有人偶（含森林）一致。
	 */
	private boolean shouldFeedPlayer(Player player) {
		return player.getHealth() < 18.0f || player.getFoodData().getFoodLevel() < 4;
	}

	/**
	 * 消耗人偶背包 1 个正向食物，完整模拟玩家吃下该食物：
	 * 恢复饥饿度与饱和度、应用全部状态效果（附魔金苹果等特殊食物的
	 * 伤害吸收/抗性/再生等也会一并给予）、播放进食粒子音效。
	 * 物品消耗由 Consumable.onConsume 内部完成，无需手动 shrink。
	 * 所有人偶（含森林）一致：背包无食物时不投喂。
	 */
	private void feedPlayer(Player player) {
		// 防御性检查：正常调用链（updateFeedMind）已先过 shouldFeedPlayer；
		// 这里再兜底一次，确保未来新增调用方时不会在玩家不缺食物时白白消耗食物
		if (!shouldFeedPlayer(player)) {
			return;
		}
		ItemStack food = findPositiveFoodInInventory();
		if (food.isEmpty()) {
			return;
		}
		Consumable consumable = food.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return;
		}
		consumable.onConsume(level(), player, food);
		this.swing(InteractionHand.MAIN_HAND);
		feedCooldown = FEED_ACTION_COOLDOWN;
	}

	/** 人偶物品栏是否有可食用的正向食物。 */
	private boolean hasPositiveFoodInInventory() {
		return !findPositiveFoodInInventory().isEmpty();
	}

	/** 找到人偶物品栏中第一个正向食物。 */
	private ItemStack findPositiveFoodInInventory() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isPositiveFood(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 正向食物判定：可食用（有 FOOD 组件且营养 > 0），且食用效果不含负面状态
	 * （中毒/饥饿/反胃/虚弱/凋零/挖掘疲劳——毒土豆、蜘蛛眼、腐肉、河豚、生鸡肉等
	 * 均会被排除）。26.2 中食物负面效果位于 CONSUMABLE 组件的
	 * ApplyStatusEffectsConsumeEffect 里。
	 */
	private boolean isPositiveFood(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null || food.nutrition() <= 0) {
			return false;
		}
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
				if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply) {
					for (MobEffectInstance instance : apply.effects()) {
						MobEffect effect = instance.getEffect().value();
						if (effect == MobEffects.POISON.value()
							|| effect == MobEffects.HUNGER.value()
							|| effect == MobEffects.NAUSEA.value()
							|| effect == MobEffects.WEAKNESS.value()
							|| effect == MobEffects.WITHER.value()
							|| effect == MobEffects.MINING_FATIGUE.value()) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// 砍树模式（CHOP）
	// ------------------------------------------------------------------

	/**
	 * 砍树模式移动输入：走 A* 路径接近当前待砍原木（可绕路爬台阶），
	 * 进入挥斧距离后停下；寻路失败（悬空树冠/浮空树等无站立点可达）
	 * 则放弃该块换下一块。连锁破坏下只需够着树上任意一块原木即可
	 * 整棵砍完，不再需要踮脚搭柱。
	 */
	private void applyChopInput() {
		// 没树可砍但有待补种位置 → 走向种植目标（低优先级，直行逼近）
		if ((chopTargetPos == null || !isLogBlock(chopTargetPos)) && saplingTargetPos != null) {
			if (canReachBlockPos(saplingTargetPos, SAPLING_REACH_SQR)) {
				clearMovementInput();
				return;
			}
			Vec3 sTarget = Vec3.atBottomCenterOf(saplingTargetPos);
			boolean sRecalc = saplingNavTarget == null
				|| sTarget.distanceToSqr(saplingNavTarget) > CHOP_NAV_RECALC_SQR;
			if (sRecalc) {
				saplingNavTarget = sTarget;
			}
			straightToward(sTarget);
			return;
		}
		if (chopTargetPos == null || !isLogBlock(chopTargetPos)) {
			clearMovementInput();
			return;
		}
		if (canReachBlockPos(chopTargetPos, CHOP_REACH_SQR)) {
			clearMovementInput();
			return;
		}
		Vec3 target = Vec3.atCenterOf(chopTargetPos);
		boolean recalc = chopNavTarget == null
			|| target.distanceToSqr(chopNavTarget) > CHOP_NAV_RECALC_SQR
			|| navigator.isPathDone();
		if (recalc) {
			chopNavTarget = target;
			if (!navigator.computePath(target)) {
				// 无路可达（如悬空树冠）→ 放弃该块，换下一块/下一棵树
				chopTargetPos = null;
				chopNavTarget = null;
				chopActionCooldown = Math.max(chopActionCooldown, 5);
				clearMovementInput();
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			chopNavTarget = null;
			clearMovementInput();
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * CHOP_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/**
	 * 跟随时是否允许为砍树暂时脱离队列：有有效砍树目标 + 未超时 + 未离主人过远。
	 * 砍树优先跟随，但离队必须有边界，否则人偶会一路追着树越跑越远。
	 */
	private boolean chopExcursionAllowed() {
		if (chopTargetPos == null || !isLogBlock(chopTargetPos)) {
			return false;
		}
		if (chopExcursionTicks >= CHOP_EXCURSION_MAX_TICKS) {
			return false;
		}
		Player owner = getOwnerPlayer();
		return owner == null || this.distanceToSqr(owner) <= CHOP_EXCURSION_MAX_DIST_SQR;
	}

	/**
	 * 低优先级补种树苗：只在"没树可砍"的空闲时执行。
	 * 背包有树苗 → 在附近找草方块/泥土上方的空气位；够得着就原地种，
	 * 不够得着只设目标（移动由 applyChopInput 处理，跟随时不追远目标）。
	 * 不打断砍树节奏：一旦找到新树（chopTargetPos 非空）立即放弃补种。
	 */
	private void tryPlantSaplingLowPriority() {
		saplingPlantCooldown = Math.max(0, saplingPlantCooldown - 1);
		// 没有树苗 → 清空种植目标
		if (!hasSaplingInInventory()) {
			saplingTargetPos = null;
			saplingNavTarget = null;
			return;
		}
		// 目标已失效（被占了/不是可种位）→ 放弃重找
		if (saplingTargetPos != null && !isPlantableSpot(saplingTargetPos)) {
			saplingTargetPos = null;
			saplingNavTarget = null;
		}
		// 没目标 → 找附近可种位置
		if (saplingTargetPos == null) {
			saplingTargetPos = findPlantableSpot();
			if (saplingTargetPos == null) {
				saplingPlantCooldown = SAPLING_PLANT_COOLDOWN; // 附近没地，稍后再找
				return;
			}
		}
		// 够得着 → 直接种（跟随时路过可种位也顺手种，不专门追）
		if (canReachBlockPos(saplingTargetPos, SAPLING_REACH_SQR)) {
			clearMovementInput();
			if (saplingPlantCooldown <= 0 && plantSapling(saplingTargetPos)) {
				saplingTargetPos = null;
				saplingNavTarget = null;
				saplingPlantCooldown = SAPLING_PLANT_COOLDOWN;
			}
		}
		// 不够得着：非跟随时由 applyChopInput 走向目标；跟随时不追（让跟随控制移动）
	}

	/** 背包里是否有树苗。 */
	private boolean hasSaplingInInventory() {
		return !findSaplingInInventory().isEmpty();
	}

	/** 找到人偶物品栏中第一个树苗（橡树/云杉/白桦/丛林/金合欢/深色橡木/樱花）。 */
	private ItemStack findSaplingInInventory() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isSaplingItem(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 判断物品是否为树苗方块物品（6 种主世界树苗 + 樱花）。 */
	private boolean isSaplingItem(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block block = blockItem.getBlock();
		return block == Blocks.OAK_SAPLING || block == Blocks.SPRUCE_SAPLING
			|| block == Blocks.BIRCH_SAPLING || block == Blocks.JUNGLE_SAPLING
			|| block == Blocks.ACACIA_SAPLING || block == Blocks.DARK_OAK_SAPLING
			|| block == Blocks.CHERRY_SAPLING;
	}

	/** 附近草方块/泥土上方的空气位（补种目标），取最近的。 */
	private BlockPos findPlantableSpot() {
		BlockPos center = getFarmWorkCenter();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int y = center.getY() - 3; y <= center.getY() + 3; y++) {
			for (int x = center.getX() - (int) SAPLING_SEARCH_RANGE; x <= center.getX() + (int) SAPLING_SEARCH_RANGE; x++) {
				for (int z = center.getZ() - (int) SAPLING_SEARCH_RANGE; z <= center.getZ() + (int) SAPLING_SEARCH_RANGE; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (pos.distSqr(center) > SAPLING_SEARCH_RANGE * SAPLING_SEARCH_RANGE) {
						continue;
					}
					if (isPlantableSpot(pos)) {
						double d = pos.distSqr(center);
						if (d < bestDist) {
							bestDist = d;
							best = pos;
						}
					}
				}
			}
		}
		return best;
	}

	/** 该空气位下方是否为草方块/泥土（可种树苗）。 */
	private boolean isPlantableSpot(BlockPos pos) {
		if (!level().isEmptyBlock(pos)) {
			return false;
		}
		Block below = level().getBlockState(pos.below()).getBlock();
		return below == Blocks.GRASS_BLOCK || below == Blocks.DIRT;
	}

	/** 在目标空气位种下树苗，成功消耗 1 个。 */
	private boolean plantSapling(BlockPos pos) {
		ItemStack sapling = findSaplingInInventory();
		if (sapling.isEmpty() || !(sapling.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		BlockState state = blockItem.getBlock().defaultBlockState();
		if (!state.canSurvive(level(), pos)) {
			return false;
		}
		level().setBlock(pos, state, 3);
		sapling.shrink(1);
		this.swing(InteractionHand.MAIN_HAND);
		this.playSound(SoundEvents.CROP_PLANTED, 1.0f, 1.0f);
		return true;
	}

	/**
	 * 直行走向目标位置（用于 A* 到不了/已走完时的最后逼近）。
	 * 配合 maxUpStep=1.0 可自动翻越 1 格台阶，撞墙时原地尝试，不会破坏其他状态。
	 */
	protected void straightToward(Vec3 target) {
		smoothFaceTowards(target.x, target.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * CHOP_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/**
	 * 砍树模式决策：维护"当前树的待砍队列"（整棵收集、y 从低到高逐个尝试），
	 * 走到可达的原木后砍掉它，连锁破坏把整棵树剩余原木瞬间掉落入包；
	 * 队列空了就找下一棵真树。整棵都够不着的树（如浮空树）暂时拉黑，
	 * 稍后再试——连锁破坏已取代踮脚搭柱。
	 */
	private void updateChopMind() {
		// 有作业区但人偶不在区域内 → 先前往作业区再找树（否则原地搜索够不到远处责任田）。
		// 跟随开启时作业区不生效（跟随与作业区互斥）：人偶必须先回到玩家身边，
		// 不能因为作业区把"跟随"变成"跑回责任田"。
		if (!isFollowEnabled() && hasWorkArea() && !isWithinWorkAreaXZ()) {
			navigateToWorkArea();
			return;
		}
		// 未跟随时不存在"离队"概念，复位计时，避免关闭再开启跟随后残留旧计数立即掐断离队
		if (!isFollowEnabled()) {
			chopExcursionTicks = 0;
		}
		chopActionCooldown = Math.max(0, chopActionCooldown - 1);
		// 斧头耗尽时停止砍树（与挖矿模式镐头检测对齐）
		ItemStack axe = findAxeStack();
		if (axe.isEmpty()) {
			if (chopTargetPos != null || chopTreeRoot != null) {
				Player owner = getOwnerPlayer();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable(
						"message." + DollModConstants.MOD_ID + ".chop_stop_no_axe",
						dollDisplayName()));
				}
				voiceNoTool();
			}
			chopTargetPos = null;
			chopQueue = null;
			chopTreeRoot = null;
			clearMovementInput();
			// 斧头消失 → 取消砍树模式并同步控制面板高亮
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		// target block already dug/no longer log → abandon, next block
		if (chopTargetPos != null && !isLogBlock(chopTargetPos)) {
			chopTargetPos = null;
		}
		// 跟随时离队超时 / 离主人过远 → 放弃目标回到主人身边（下一轮会重挑更近的树）
		if (isFollowEnabled() && chopTargetPos != null && !chopExcursionAllowed()) {
			chopTargetPos = null;
			chopNavTarget = null;
			chopQueue = null;
			chopTreeRoot = null;
		}
		if (chopTargetPos == null) {
			advanceChopQueue();
		}
		if (chopTargetPos == null) {
			// 没树可砍：低优先级补种树苗（背包有树苗且附近有草方块/泥土时顺手种）
			tryPlantSaplingLowPriority();
			if (chopTargetPos == null && saplingTargetPos == null) {
				clearMovementInput();
			}
			return;
		}
		if (canReachBlockPos(chopTargetPos, CHOP_REACH_SQR)) {
			clearMovementInput();
			smoothFaceTowards(chopTargetPos.getX() + 0.5, chopTargetPos.getZ() + 0.5);
			if (chopActionCooldown <= 0) {
				chopBlock(chopTargetPos);
				chainFellTree(chopTargetPos); // 连锁：整棵树剩余原木瞬间掉落入包
				chopTargetPos = null; // 砍完一棵换下一棵
				chopActionCooldown = CHOP_ACTION_COOLDOWN;
			}
		} else if (isHorizontallyWithinReach(chopTargetPos, CHOP_REACH_SQR)) {
			// 水平够得着但垂直够不着（目标太高/太低，如树冠悬空原木）→ 放弃该块换下一块
			chopTargetPos = null;
			chopNavTarget = null;
		}
		// 够不着：移动交给 applyChopInput 负责靠近，寻路失败会放弃该块换下一块
	}

	/**
	 * 推进待砍队列：队列空则找下一棵真树；跳过已被挖走的原木。
	 * 队列耗尽且树根仍是原木（整棵都没砍动、全部够不着）→ 暂时拉黑该树，
	 * 避免反复对同一棵浮空树重试。
	 */
	private void advanceChopQueue() {
		if (chopQueue == null || chopQueue.isEmpty()) {
			chopQueue = null;
			// 树根还在 → 这棵树整棵没砍动（够不着/寻路失败），拉黑稍后再试；
			// 树根已消失 → 连锁破坏已砍完，正常换下一棵。
			if (chopTreeRoot != null && isLogBlock(chopTreeRoot)) {
				chopTreeBlacklist.put(chopTreeRoot, level().getGameTime() + CHOP_TREE_BLACKLIST_TICKS);
			}
			chopTreeRoot = null;
			if (chopSearchCooldown > 0) {
				chopSearchCooldown--;
				return;
			}
			BlockPos root = findTreeRoot();
			if (root == null) {
				chopSearchCooldown = CHOP_SEARCH_COOLDOWN; // 附近没树，稍后再找
				return;
			}
			chopTreeRoot = root;
			chopQueue = collectTreeLogs(root);
		}
		while (!chopQueue.isEmpty()) {
			BlockPos next = chopQueue.remove(0);
			if (isLogBlock(next)) {
				chopTargetPos = next;
				return;
			}
		}
		chopQueue = null;
	}

	/**
	 * 找一棵真树的树根（连通体的 y 最低原木）：扫描范围内所有原木，
	 * 对每个未处理的原木做连通收集（BFS），连通体里有一块邻接树叶/菌光体即为真树，
	 * 取「离中心最近的一块原木」最近的一棵（同距离取树根更低）；建筑里的孤立木头会被跳过。
	 * 连锁破坏下只需够着树上任意一块原木即可整棵砍完，因此按「最近原木」而非「树根」
	 * 距离选树——斜坡/树冠近的树不会被误判为远。
	 */
	private BlockPos findTreeRoot() {
		BlockPos center = getFarmWorkCenter();
		Set<BlockPos> scanned = new HashSet<>();
		BlockPos bestRoot = null;
		double bestDistSqr = Double.MAX_VALUE;
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
		for (int y = center.getY() - 4; y <= center.getY() + 24; y++) {
			for (int x = center.getX() - (int) CHOP_SEARCH_RANGE; x <= center.getX() + (int) CHOP_SEARCH_RANGE; x++) {
				for (int z = center.getZ() - (int) CHOP_SEARCH_RANGE; z <= center.getZ() + (int) CHOP_SEARCH_RANGE; z++) {
					mpos.set(x, y, z);
					if (scanned.contains(mpos) || !isLogBlock(mpos) || !withinWorkAreaXZ(mpos)) {
						continue;
					}
					List<BlockPos> tree = collectTreeLogs(mpos);
					scanned.addAll(tree);
					BlockPos minY = new BlockPos(mpos.getX(), mpos.getY(), mpos.getZ());
					boolean hasLeaves = false;
					double closestDistSqr = Double.MAX_VALUE;
					for (BlockPos log : tree) {
						if (log.getY() < minY.getY()) {
							minY = log;
						}
						if (!hasLeaves && touchesLeavesOrWart(log)) {
							hasLeaves = true;
						}
						double dd = log.distSqr(center);
						if (dd < closestDistSqr) {
							closestDistSqr = dd;
						}
					}
					if (!hasLeaves) {
						continue; // 建筑木头，不砍
					}
					if (isTreeBlacklisted(minY)) {
						continue; // 近期整棵够不着，暂时跳过
					}
					if (bestRoot == null || closestDistSqr < bestDistSqr
						|| (closestDistSqr == bestDistSqr && minY.getY() < bestRoot.getY())) {
						bestRoot = minY;
						bestDistSqr = closestDistSqr;
					}
				}
			}
		}
		return bestRoot;
	}

	/** 该树根是否在拉黑期内（整棵够不着，暂时跳过）。过期条目顺带移除，防长期运行内存增长。 */
	private boolean isTreeBlacklisted(BlockPos root) {
		Long expire = chopTreeBlacklist.get(root);
		if (expire == null) {
			return false;
		}
		if (expire > level().getGameTime()) {
			return true;
		}
		chopTreeBlacklist.remove(root);
		return false;
	}

	/**
	 * BFS 收集与 root 六向连通的全部原木（限 CHOP_MAX_TREE_BLOCKS），按离人偶的距离从近到远排序。
	 * 原木与树叶都作为 BFS 跳板（visited+入队），但只把原木计入结果：
	 * 2×2 巨型丛林/深色橡木树冠中的分支原木常与主干隔着树叶，纯原木连通会漏掉它们。
	 * 树叶不产出（chainFellTree 只掉落原木），仅用于跨越树冠间隙连通整棵树。
	 * 连锁破坏下砍任意一块原木即可整棵清空，因此按距离就近先砍，而不是从下往上。
	 */
	private List<BlockPos> collectTreeLogs(BlockPos root) {
		List<BlockPos> result = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(root);
		visited.add(root);
		while (!queue.isEmpty() && result.size() < CHOP_MAX_TREE_BLOCKS) {
			BlockPos cur = queue.poll();
			if (isLogBlock(cur)) {
				result.add(cur);
			}
			for (int[] d : TREE_NEIGHBORS) {
				BlockPos next = cur.offset(d[0], d[1], d[2]);
				if (visited.contains(next)) {
					continue;
				}
				BlockState nextState = level().getBlockState(next);
				if (nextState.is(BlockTags.LOGS) || nextState.is(BlockTags.LEAVES)) {
					visited.add(next);
					queue.add(next);
				}
			}
		}
		BlockPos dollPos = this.blockPosition();
		result.sort(Comparator.comparingDouble(log -> log.distSqr(dollPos))); // 就近先砍
		return result;
	}

	/** 是否为原木/菌柄（#minecraft:logs）。 */
	private boolean isLogBlock(BlockPos pos) {
		return level().getBlockState(pos).is(BlockTags.LOGS);
	}

	/** 原木的六向邻接是否有树叶或菌光体（用于真树判定）。 */
	private boolean touchesLeavesOrWart(BlockPos logPos) {
		for (int[] d : TREE_NEIGHBORS) {
			BlockState s = level().getBlockState(logPos.offset(d[0], d[1], d[2]));
			if (s.is(BlockTags.LEAVES) || s.is(BlockTags.WART_BLOCKS)) {
				return true;
			}
		}
		return false;
	}

	/** 破坏原木：按原版掉落表计算掉落进人偶存储区，播放破坏粒子与原木音效。 */
	private boolean chopBlock(BlockPos pos) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		BlockState state = level().getBlockState(pos);
		ItemStack axe = findAxeStack();
		LootParams.Builder builder = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withParameter(LootContextParams.TOOL, axe)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
			.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
		List<ItemStack> drops = state.getDrops(builder);
		level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level().levelEvent(2001, pos, Block.getId(state));
		this.playSound(state.getSoundType().getBreakSound(), 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		for (ItemStack drop : drops) {
			addToDollInventory(drop);
		}
		if (!axe.isEmpty()) {
			axe.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	/**
	 * 连锁砍树（模仿连锁挖矿模组，砍树模式的核心能力）：砍掉一块原木后，
	 * 从该位置沿原木六向连通（BFS）收集整棵树剩余原木，全部静默掉落进
	 * 人偶存储区并移除方块（不逐块播粒子/音效，避免几十上百块刷屏卡顿）。
	 * 不是放大挖掘范围：只连锁原木连通体，建筑木头/踮脚柱（已移除）等
	 * 不与树连通的方块不会被误拆；上限沿用 CHOP_MAX_TREE_BLOCKS 防死循环。
	 * 掉落物超背包容量时由 addToDollInventory 兜底落地，不会丢物品。
	 */
	private void chainFellTree(BlockPos chopped) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		List<BlockPos> rest = collectTreeLogs(chopped);
		ItemStack axe = findAxeStack();
		int mined = 0;
		for (BlockPos pos : rest) {
			if (pos.equals(chopped)) {
				continue; // 当前块已由 chopBlock 处理
			}
			BlockState state = serverLevel.getBlockState(pos);
			LootParams.Builder builder = new LootParams.Builder(serverLevel)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.TOOL, axe)
				.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
				.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
			for (ItemStack drop : state.getDrops(builder)) {
				addToDollInventory(drop);
			}
			serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			mined++;
		}
		// 连锁的每块原木消耗 1 点斧头耐久（与玩家逐块砍一致）
		if (!axe.isEmpty() && mined > 0) {
			axe.hurtAndBreak(mined, this, EquipmentSlot.MAINHAND);
		}
	}

	/**
	 * 是否够得着目标方块：水平距离在范围内；垂直方向向上放宽到 5 格、
	 * 向下 2.5 格——连锁破坏下只需够着树上任意一块原木即可整棵砍完，
	 * 垂直放宽保证站在树旁（或略高/略低处）都能砍到第一块。
	 */
	private boolean canReachBlockPos(BlockPos pos, double reachSqr) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		if (dx * dx + dz * dz > reachSqr) {
			return false;
		}
		double dy = (pos.getY() + 0.5) - this.getY();
		return dy <= 5.0 && dy >= -2.5;
	}

	/** 仅水平距离是否在工作范围内（用于判断"水平够得着但垂直够不着"的悬空目标）。 */
	private boolean isHorizontallyWithinReach(BlockPos pos, double reachSqr) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= reachSqr;
	}

	private boolean hasAxeInHotbar() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).getItem() instanceof AxeItem) {
				return true;
			}
		}
		return false;
	}

	private boolean hasPickaxeInHotbar() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isPickaxe(inventory.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// 插火把模式（TORCH）
	// ------------------------------------------------------------------

	/** 人偶背包中是否有照明物（火把类/南瓜灯/灯笼类/全方块光源）。 */
	private boolean hasTorchInInventory() {
		return !findTorchInInventory().isEmpty();
	}

	/**
	 * 找到人偶物品栏中的照明物，按优先级返回：
	 * 火把类（含 26.2 铜火把） > 南瓜灯 > 灯笼类（含灵魂灯笼） > 全方块光源（萤石/蛙鸣灯/海晶灯/菌光体/红石灯）。
	 */
	private ItemStack findTorchInInventory() {
		// 第一优先：火把类
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.getItem() instanceof BlockItem bi) {
				if (isTorchKindBlock(bi.getBlock())) {
					return stack;
				}
			}
		}
		// 第二优先：南瓜灯
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.JACK_O_LANTERN) {
				return stack;
			}
		}
		// 第三优先：灯笼类
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.getItem() instanceof BlockItem bi
				&& (bi.getBlock() == Blocks.LANTERN || bi.getBlock() == Blocks.SOUL_LANTERN)) {
				return stack;
			}
		}
		// 第四优先：全方块光源（可浮空放置）
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.getItem() instanceof BlockItem bi && isFullBlockLightSource(bi.getBlock())) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 判断物品是否是照明物（火把类/南瓜灯/灯笼类/全方块光源）。 */
	private boolean isTorchItem(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block block = blockItem.getBlock();
		return isTorchKindBlock(block)
			|| block == Blocks.JACK_O_LANTERN
			|| block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN
			|| isFullBlockLightSource(block);
	}

	/** 火把类方块（含 26.2 铜火把）：有墙面变体，可插地面或墙面。 */
	private static boolean isTorchKindBlock(Block b) {
		return b == Blocks.TORCH || b == Blocks.SOUL_TORCH
			|| b == Blocks.REDSTONE_TORCH || b == Blocks.COPPER_TORCH;
	}

	/** 全方块光源：无需支撑面，任意空气位置均可放置（萤石/蛙鸣灯/海晶灯/菌光体/红石灯/南瓜灯）。 */
	private static boolean isFullBlockLightSource(Block b) {
		return b == Blocks.GLOWSTONE
			|| b == Blocks.SEA_LANTERN
			|| b == Blocks.SHROOMLIGHT
			|| b == Blocks.REDSTONE_LAMP
			|| b == Blocks.OCHRE_FROGLIGHT
			|| b == Blocks.VERDANT_FROGLIGHT
			|| b == Blocks.PEARLESCENT_FROGLIGHT
			|| b == Blocks.JACK_O_LANTERN;
	}

	/**
	 * 插火把模式移动输入：寻找周围光照不足的暗处并走过去。
	 * 跟随时叠加在跟随移动之上：只处理人偶身边（TORCH_FOLLOW_RANGE 内）的暗处，
	 * 够得着就停下插，稍远（在跟随范围内）就走过去插，插完继续跟随；
	 * 超出跟随范围的远处暗处不追（放弃重找），不干扰跟随移动。
	 */
	private void applyTorchInput() {
		if (torchTargetPos == null) {
			if (!isFollowEnabled()) {
				clearMovementInput(); // 非跟随时原地等待
			}
			return; // 跟随时让 applyFollowInput 控制移动
		}
		// 跟随时：目标已超出人偶身边范围（玩家走远/目标被选远）→ 放弃，让跟随移动继续
		if (isFollowEnabled()
			&& this.distanceToSqr(torchTargetPos.getX() + 0.5, torchTargetPos.getY() + 0.5, torchTargetPos.getZ() + 0.5)
				> TORCH_FOLLOW_RANGE * TORCH_FOLLOW_RANGE) {
			torchTargetPos = null;
			torchNavTarget = null;
			return; // 跟随控制移动，火把不干扰远处目标
		}
		// 已经够近了（3.5格内）：原地停下，让 updateTorchMind 执行放置
		if (canReachBlockPos(torchTargetPos, TORCH_REACH_SQR)) {
			clearMovementInput();
			return;
		}
		// 走到目标处（跟随时也允许走向身边的暗处，插完再继续跟随）
		Vec3 target = Vec3.atBottomCenterOf(torchTargetPos);
		boolean recalc = torchNavTarget == null
			|| target.distanceToSqr(torchNavTarget) > TORCH_NAV_RECALC_SQR
			|| navigator.isPathDone();
		if (recalc) {
			torchNavTarget = target;
			if (!navigator.computePath(target)) {
				torchNavTarget = null;
				// 寻路失败（被墙隔开）：跟随时放弃该目标恢复跟随，避免直线撞墙卡死
				if (isFollowEnabled()) {
					torchTargetPos = null;
					torchSearchCooldown = TORCH_SEARCH_COOLDOWN;
					return;
				}
				straightToward(target);
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			torchNavTarget = null;
			if (isFollowEnabled()) {
				torchTargetPos = null;
				torchSearchCooldown = TORCH_SEARCH_COOLDOWN;
				return;
			}
			straightToward(target);
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6f);
		this.setSprinting(false);
	}

	/**
	 * 插火把模式决策：寻找光照不足的暗处，走到附近后插火把。
	 * 优先地面（点击方块顶部），地面无法放置则尝试墙面。
	 * 跟随时扫描范围缩小到人偶身边，边走边插身边暗处。
	 */
	private void updateTorchMind() {
		torchActionCooldown = Math.max(0, torchActionCooldown - 1);
		// 目标位置已不再需要火把（被其他光源照亮）→ 放弃，重新寻找
		if (torchTargetPos != null && !isDarkSpot(torchTargetPos)) {
			torchTargetPos = null;
		}
		// 跟随时目标超出人偶身边范围（玩家走远/目标被选远）→ 放弃，避免一直追着远处的暗处不插火把
		if (torchTargetPos != null && isFollowEnabled()
			&& this.distanceToSqr(torchTargetPos.getX() + 0.5, torchTargetPos.getY() + 0.5, torchTargetPos.getZ() + 0.5)
				> TORCH_FOLLOW_RANGE * TORCH_FOLLOW_RANGE) {
			torchTargetPos = null;
			torchNavTarget = null;
		}
		if (torchTargetPos == null) {
			if (torchSearchCooldown > 0) {
				torchSearchCooldown--;
				return;
			}
			torchTargetPos = findDarkSpot();
			if (torchTargetPos == null) {
				torchSearchCooldown = TORCH_SEARCH_COOLDOWN;
				return;
			}
		}
		// 运行时前置检查（方案A）：火把耗尽 → 关闭模式并同步控制面板高亮
		ItemStack torch = findTorchInInventory();
		if (torch.isEmpty()) {
			Player owner = getOwnerPlayer();
			if (owner != null && (torchTargetPos != null || torchNavTarget != null)) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".torch_stop_no_torch", dollDisplayName()));
				voiceNoTool();
			}
			torchTargetPos = null;
			torchNavTarget = null;
			torchSearchCooldown = TORCH_SEARCH_COOLDOWN;
			clearMovementInput();
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		// 到达工作范围且冷却完毕 → 尝试插火把
		if (canReachBlockPos(torchTargetPos, TORCH_REACH_SQR) && torchActionCooldown <= 0) {
			if (tryPlaceTorch(torchTargetPos)) {
				torchActionCooldown = TORCH_ACTION_COOLDOWN;
				torchTargetPos = null; // 插完找下一个
			}
		}
		// 移动由 applyTorchInput 负责
	}

	/**
	 * 寻找需要插火把的暗处：扫描周围区域，找到光照等级 <= 阈值的空气方块。
	 * 优先选择离人偶最近的、且下方/侧面有可放置火把的方块支撑的暗处。
	 * 跟随时扫描范围缩小到人偶身边（TORCH_FOLLOW_RANGE 球形范围），只找能边走边插
	 * 的暗处；非跟随时用完整搜寻半径（TORCH_SEARCH_RANGE）。
	 */
	private BlockPos findDarkSpot() {
		boolean following = isFollowEnabled();
		// 跟随时以人偶自身为中心（人偶贴着玩家，身边即玩家身边），非跟随时复用工作中心（人偶自身）
		BlockPos center = following ? this.blockPosition() : getFarmWorkCenter();
		double range = following ? TORCH_FOLLOW_RANGE : TORCH_SEARCH_RANGE;
		// 解析当前照明物类型：全方块光源可浮空放置，灯笼类可悬挂天花板
		ItemStack torchStack = findTorchInInventory();
		Block torchBlock = torchStack.getItem() instanceof BlockItem torchBi ? torchBi.getBlock() : null;
		boolean fullBlockLight = torchBlock != null && isFullBlockLightSource(torchBlock);
		boolean lanternLight = torchBlock != null
			&& (torchBlock == Blocks.LANTERN || torchBlock == Blocks.SOUL_LANTERN);
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
		for (int y = center.getY() - 2; y <= center.getY() + 3; y++) {
			for (int x = center.getX() - (int) range; x <= center.getX() + (int) range; x++) {
				for (int z = center.getZ() - (int) range; z <= center.getZ() + (int) range; z++) {
					mpos.set(x, y, z);
					if (mpos.distSqr(center) > range * range) {
						continue; // 球形范围裁剪，避免扫到角落的超远方块
					}
					if (!isDarkSpot(mpos)) {
						continue;
					}
					// 检查是否能放置照明物（全方块任意空气位；支撑型需地面/墙面；灯笼可挂天花板）
					if (!canPlaceTorchAt(mpos, fullBlockLight, lanternLight)) {
						continue;
					}
					double dist = this.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
					if (dist < bestDist) {
						bestDist = dist;
						best = new BlockPos(mpos.getX(), mpos.getY(), mpos.getZ());
					}
				}
			}
		}
		return best;
	}

	/** 判断某位置是否为需要插火把的暗处：空气方块，且实际光照等级 <= 阈值。
	 *  用 getMaxLocalRawBrightness 取内部光照等级（max(方块光, 天空光-天空暗度)），
	 *  天空暗度随时间变化——白天=0（户外亮）、夜晚=11（户外暗），正确区分昼夜。 */
	private boolean isDarkSpot(BlockPos pos) {
		if (!level().isEmptyBlock(pos)) {
			return false;
		}
		return level().getMaxLocalRawBrightness(pos) <= TORCH_LIGHT_THRESHOLD;
	}

	/** 判断某位置是否可以放置当前照明物：全方块光源任意空气位即可；支撑型需地面/墙面；灯笼类还可挂天花板。 */
	private boolean canPlaceTorchAt(BlockPos pos, boolean fullBlockLight, boolean lanternLight) {
		if (!level().isEmptyBlock(pos)) {
			return false;
		}
		// 全方块光源：无需支撑，任意空气位置均可放置（可浮空）
		if (fullBlockLight) {
			return true;
		}
		// 支撑型：下方方块是否支持放置
		if (canPlaceTorchOnFace(pos.below(), Direction.UP)) {
			return true;
		}
		// 支撑型：四周墙面
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (canPlaceTorchOnFace(pos.relative(dir), dir.getOpposite())) {
				return true;
			}
		}
		// 灯笼类：天花板悬挂（HANGING=true 需上方方块底面坚固）
		if (lanternLight && canPlaceTorchOnFace(pos.above(), Direction.DOWN)) {
			return true;
		}
		return false;
	}

	/**
	 * 检查某方块的特定面是否适合放置火把：方块必须是实体方块且面是坚固的。
	 */
	private boolean canPlaceTorchOnFace(BlockPos supportPos, Direction face) {
		BlockState supportState = level().getBlockState(supportPos);
		return supportState.isFaceSturdy(level(), supportPos, face);
	}

	/**
	 * 尝试在目标位置放置照明物。按类型分派：
	 * 全方块光源直接放置（任意空气位）；火把类优先地面、其次墙面；灯笼类地面、其次悬挂天花板。
	 * 使用 setBlock 直接放置，避免 UseOnContext 传 null 玩家导致 NPE。
	 */
	private boolean tryPlaceTorch(BlockPos targetPos) {
		ItemStack torch = findTorchInInventory();
		if (torch.isEmpty() || !(torch.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		Block torchBlock = blockItem.getBlock();
		boolean placed;
		if (isFullBlockLightSource(torchBlock)) {
			placed = tryPlaceFullBlockLight(targetPos, torchBlock);
		} else if (torchBlock == Blocks.LANTERN || torchBlock == Blocks.SOUL_LANTERN) {
			// 灯笼类：先放地面（HANGING=false），再试悬挂天花板（HANGING=true）
			placed = tryPlaceTorchOnGround(targetPos, torchBlock) || tryPlaceTorchHanging(targetPos, torchBlock);
		} else {
			// 火把类：先地面，再四周墙面
			placed = tryPlaceTorchOnGround(targetPos, torchBlock);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				if (!placed) {
					placed = tryPlaceTorchOnWall(targetPos, dir, torchBlock);
				}
			}
		}
		if (placed) {
			torch.shrink(1);
			this.swing(InteractionHand.MAIN_HAND);
			this.playSound(SoundEvents.WOOD_PLACE, 1.0f, 1.0f);
			return true;
		}
		return false;
	}

	/**
	 * 尝试地面放置照明物：目标位置为空，且下方方块支撑面坚固。
	 * 南瓜灯有 FACING 属性（朝向人偶正面），其余用默认状态。
	 */
	private boolean tryPlaceTorchOnGround(BlockPos targetPos, Block torchBlock) {
		if (!level().isEmptyBlock(targetPos)) {
			return false;
		}
		BlockPos below = targetPos.below();
		BlockState belowState = level().getBlockState(below);
		if (!belowState.isFaceSturdy(level(), below, Direction.UP)) {
			return false;
		}
		BlockState state = torchBlock.defaultBlockState();
		// 南瓜灯：设置朝向为人偶水平正面方向
		if (torchBlock == Blocks.JACK_O_LANTERN) {
			state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.fromYRot(this.getYRot()));
		}
		if (state.canSurvive(level(), targetPos)) {
			level().setBlock(targetPos, state, 3);
			return true;
		}
		return false;
	}

	/**
	 * 尝试墙面放置火把：目标位置为空，且墙面方向支撑面坚固。
	 * 墙面火把使用 WallTorchBlock，根据方向设置 FACING。
	 */
	private boolean tryPlaceTorchOnWall(BlockPos targetPos, Direction face, Block torchBlock) {
		if (!level().isEmptyBlock(targetPos)) {
			return false;
		}
		BlockPos supportPos = targetPos.relative(face.getOpposite());
		BlockState supportState = level().getBlockState(supportPos);
		if (!supportState.isFaceSturdy(level(), supportPos, face)) {
			return false;
		}
		// 根据火把类型选择墙面变体
		Block wallTorchBlock;
		if (torchBlock == Blocks.TORCH) {
			wallTorchBlock = Blocks.WALL_TORCH;
		} else if (torchBlock == Blocks.SOUL_TORCH) {
			wallTorchBlock = Blocks.SOUL_WALL_TORCH;
		} else if (torchBlock == Blocks.REDSTONE_TORCH) {
			wallTorchBlock = Blocks.REDSTONE_WALL_TORCH;
		} else if (torchBlock == Blocks.COPPER_TORCH) {
			wallTorchBlock = Blocks.COPPER_WALL_TORCH;
		} else {
			return false; // 未知火把类型
		}
		BlockState state = wallTorchBlock.defaultBlockState()
			.setValue(HorizontalDirectionalBlock.FACING, face);
		if (state.canSurvive(level(), targetPos)) {
			level().setBlock(targetPos, state, 3);
			return true;
		}
		return false;
	}

	/**
	 * 尝试悬挂放置灯笼类（HANGING=true）：目标上方方块底面坚固，灯笼挂在下方。
	 */
	private boolean tryPlaceTorchHanging(BlockPos targetPos, Block torchBlock) {
		if (!level().isEmptyBlock(targetPos)) {
			return false;
		}
		BlockPos above = targetPos.above();
		BlockState aboveState = level().getBlockState(above);
		if (!aboveState.isFaceSturdy(level(), above, Direction.DOWN)) {
			return false;
		}
		BlockState state = torchBlock.defaultBlockState().setValue(LanternBlock.HANGING, true);
		if (state.canSurvive(level(), targetPos)) {
			level().setBlock(targetPos, state, 3);
			return true;
		}
		return false;
	}

	/**
	 * 放置全方块光源（萤石/蛙鸣灯/海晶灯/菌光体/红石灯/南瓜灯）：
	 * 全方块无需支撑面，目标位置为空气即可放置（可浮空）。
	 * 南瓜灯有 FACING 属性（朝向人偶正面）。
	 */
	private boolean tryPlaceFullBlockLight(BlockPos targetPos, Block torchBlock) {
		if (!level().isEmptyBlock(targetPos)) {
			return false;
		}
		BlockState state = torchBlock.defaultBlockState();
		if (torchBlock == Blocks.JACK_O_LANTERN) {
			state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.fromYRot(this.getYRot()));
		}
		if (state.canSurvive(level(), targetPos)) {
			level().setBlock(targetPos, state, 3);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// 挖矿模式（MINE）
	// ------------------------------------------------------------------

	/** 判断位置是否为可挖的矿石方块。 */
	private boolean isOreBlock(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		if (state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)) {
			return true;
		}
		Block block = state.getBlock();
		return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
			|| block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
			|| block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
			|| block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.ANCIENT_DEBRIS;
	}

	/** 矿物价值分级：钻石/绿宝石/远古残骸=100，金铁红石青金石=50，铜煤=20，其余=10。 */
	private int getOreValue(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		Block block = state.getBlock();
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.ANCIENT_DEBRIS) {
			return 100;
		}
		if (state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.IRON_ORES)
			|| block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
			|| block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
			return 50;
		}
		if (state.is(BlockTags.COPPER_ORES)
			|| block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return 20;
		}
		return 10;
	}

	/** 六方向邻接是否有空气方块（暴露矿优先挖）。 */
	private boolean hasAdjacentAir(BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (level().getBlockState(pos.relative(dir)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** 检查 pos 周围 radius 内是否存在岩浆方块（用于跳过危险矿）。 */
	private boolean hasLavaNearby(BlockPos pos, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (level().getBlockState(pos.offset(dx, dy, dz)).getBlock() == Blocks.LAVA) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * 镐等级匹配的唯一真相源（严格匹配策略）：
	 * 只有「手上有镐」且「该方块不需要分级工具，或当前镐挖它会正常掉落」时才允许采掘。
	 * 高等级矿（钻石/绿宝石/远古残骸等）在镐不够时一律返回 false——宁可留着，
	 * 也绝不用低级镐把矿脉空手拆掉（方块会消失但没有掉落，等于毁掉资源）。
	 * 所有挖矿入口（选目标 / 单块挖 / 连锁 / 盾构机 / 盾构机侧向探矿）都必须走这里，
	 * 避免某处漏判导致「低等级镐挖掉高等级矿」。
	 */
	private boolean canPickaxeMine(BlockState state) {
		// 能力判定必须用"背包里最强的镐"：石镐在槽位靠前、钻石镐在后时，
		// 若只看第一把镐就会误判"挖不动钻石矿"而绕开它（本就发现的缺陷）。
		ItemStack pickaxe = findBestPickaxeStack();
		if (pickaxe.isEmpty()) {
			return false;
		}
		return !requiresTieredTool(state) || pickaxe.isCorrectToolForDrops(state);
	}

	/** 当前是否持有有效的矿目标（不为 null 且该位置仍是矿石）。 */
	private boolean hasValidMineTarget() {
		return mineTargetPos != null && isOreBlock(mineTargetPos);
	}

	/**
	 * 跟随时是否允许为挖矿暂时脱离队列：有有效矿目标 + 未超时 + 未离主人过远。
	 * 挖矿优先跟随，但离队必须有边界，否则人偶会一路追着矿越跑越远。
	 */
	private boolean mineExcursionAllowed() {
		if (!hasValidMineTarget()) {
			return false;
		}
		if (mineExcursionTicks >= MINE_EXCURSION_MAX_TICKS) {
			return false;
		}
		Player owner = getOwnerPlayer();
		return owner == null || this.distanceToSqr(owner) <= MINE_EXCURSION_MAX_DIST_SQR;
	}

	/**
	 * 挖矿扫描中心：机会式策略下始终以人偶自身为中心。
	 * 跟随时只关心人偶行进路线附近够得着的矿；若以主人为中心扫描，
	 * 会挑到主人身边、人偶根本够不着的矿 → 反复寻路失败被拉黑 → 表现为「跟随时不认矿」。
	 */
	private BlockPos getMineScanCenter() {
		return this.blockPosition();
	}

	/** 挖矿目标边缘情况过滤：区块未加载 / 基岩层 / 岩浆邻近（无抗火）跳过。 */
	private boolean passesMineEdgeChecks(BlockPos ore) {
		if (!level().isLoaded(ore)) {
			return false;
		}
		if (ore.getY() <= level().getMinY() + 1) {
			return false; // 基岩层不可挖
		}
		if (getDollLevel() < 5 && hasLavaNearby(ore, 2)) {
			return false; // 无抗火时不碰岩浆旁的矿
		}
		return true;
	}

	/**
	 * 挖矿模式移动输入：导航到挖矿目标（或可站立点）；寻路失败则拉黑该目标并放弃。
	 */
	private void applyMineInput() {
		if (mineTargetPos == null || !isOreBlock(mineTargetPos)) {
			clearMovementInput();
			return;
		}
		// 跟随模式只挖当前直接够得着的矿：目标一旦够不着就放弃，绝不导航（避免狭长隧道卡死）
		if (isFollowEnabled()
			&& !(canReachBlockPos(mineTargetPos, MINE_REACH_SQR) && hasLineOfSight(mineTargetPos))) {
			mineTargetPos = null;
			mineStandPos = null;
			mineNavTarget = null;
			clearMovementInput();
			return;
		}
		// 已能直接挖到（距离+视线通畅）→ 停下交给 updateMineMind 动手
		if (canReachBlockPos(mineTargetPos, MINE_REACH_SQR) && hasLineOfSight(mineTargetPos)) {
			clearMovementInput();
			return;
		}
		// 导航到站立点（若有），否则导航到矿块本身
		BlockPos navPos = mineStandPos != null ? mineStandPos : mineTargetPos;
		Vec3 target = Vec3.atCenterOf(navPos);
		// 视线通畅时直线移动（与跟随一致的移动方式，统一速度），否则 A* 绕路
		if (hasLineOfSight(navPos)) {
			smoothFaceTowards(target.x, target.z);
			this.xxa = 0.0f;
			this.zza = 1.0f;
			setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * MINE_MOVE_SPEED_FACTOR);
			this.setSprinting(true);
			return;
		}
		boolean recalc = mineNavTarget == null
			|| target.distanceToSqr(mineNavTarget) > MINE_NAV_RECALC_SQR
			|| navigator.isPathDone();
		if (recalc) {
			mineNavTarget = target;
			if (!navigator.computePath(target)) {
				// 不可达 → 拉黑，放弃该块，下 tick 重选
				mineBlacklist.put(mineTargetPos, level().getGameTime() + MINE_BLACKLIST_TICKS);
				mineTargetPos = null;
				mineStandPos = null;
				mineNavTarget = null;
				mineActionCooldown = Math.max(mineActionCooldown, 5);
				clearMovementInput();
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			mineNavTarget = null;
			clearMovementInput();
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * MINE_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/** 挖矿模式决策：已配置盾构机则走隧道掘进；否则按原逻辑扫描找矿连锁挖。 */
	private void updateMineMind() {
		if (hasTunnelConfig()) {
			updateTunnelDrill();
			return;
		}
		mineActionCooldown = Math.max(0, mineActionCooldown - 1);
		// 未跟随时不存在"离队"概念，复位计时，避免关闭再开启跟随后残留旧计数立即掐断离队
		if (!isFollowEnabled()) {
			mineExcursionTicks = 0;
		}
		// 镐子耗尽时停止挖矿（与砍树模式斧头耗尽逻辑对齐）：
		// 不能继续空手拆矿石（方块变成忽略掉落），清空目标待玩家补充镐子后自动恢复
		ItemStack minePickaxe = findPickaxeStack();
		if (minePickaxe.isEmpty()) {
			if (mineTargetPos != null) {
				Player owner = getOwnerPlayer();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable(
						"message." + DollModConstants.MOD_ID + ".mine_no_pickaxe",
						dollDisplayName()));
				}
				voiceNoTool();
			}
			mineTargetPos = null;
			mineStandPos = null;
			mineNavTarget = null;
			mineExcursionTicks = 0;
			clearMovementInput();
			// 镐头消失 → 取消挖矿模式并同步控制面板高亮
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		// 目标块已被挖走 / 不再是矿 → 放弃
		if (mineTargetPos != null && !isOreBlock(mineTargetPos)) {
			mineTargetPos = null;
			mineStandPos = null;
		}
		// 跟随时离队超时 / 离主人过远 → 放弃目标回到主人身边（下一轮会重挑更近的矿）
		if (isFollowEnabled() && mineTargetPos != null && !mineExcursionAllowed()) {
			mineTargetPos = null;
			mineStandPos = null;
			mineNavTarget = null;
		}
		// 跟随模式只挖当前直接够得着的矿：目标一旦够不着（主人走开/地形变化）就放弃，
		// 不做站立点导航——否则在狭长隧道里 A* 走不通会反复尝试把人偶卡死（用户实测反馈）
		if (isFollowEnabled() && mineTargetPos != null
			&& !(canReachBlockPos(mineTargetPos, MINE_REACH_SQR) && hasLineOfSight(mineTargetPos))) {
			mineTargetPos = null;
			mineStandPos = null;
			mineNavTarget = null;
		}
		if (mineTargetPos == null) {
			mineExcursionTicks = 0;
			if (mineSearchCooldown > 0) {
				mineSearchCooldown--;
				clearMovementInput();
				return;
			}
			// 背包无空格：不找新目标，站立等待玩家清包——掉落物放不进背包会掉地
			// 堆积，所以宁可停下等（清包后自动恢复）
			if (!hasStorageSpace()) {
				notifyMineBackpackFull();
				clearMovementInput();
				return;
			}
			mineBackpackFullNotified = false;
			mineTargetPos = selectMineTarget();
			if (mineTargetPos == null) {
				mineSearchCooldown = MINE_SEARCH_COOLDOWN; // 附近没矿，稍后再找
				clearMovementInput();
				return;
			}
		}
		if (canReachBlockPos(mineTargetPos, MINE_REACH_SQR) && hasLineOfSight(mineTargetPos)) {
			clearMovementInput();
			smoothFaceTowards(mineTargetPos.getX() + 0.5, mineTargetPos.getZ() + 0.5);
			if (mineActionCooldown <= 0) {
				// 背包无空格：不挖（掉落物会掉地），等玩家清包后自动恢复
				if (!hasStorageSpace()) {
					notifyMineBackpackFull();
					mineTargetPos = null;
					mineStandPos = null;
					clearMovementInput();
					return;
				}
				mineBackpackFullNotified = false;
				BlockState mineState = level().getBlockState(mineTargetPos);
				if (!mineBlock(mineTargetPos)) {
					// 镐子耗尽或等级不足挖不动：放弃该目标；重选时 selectMineTarget 会过滤挖不动的矿
					mineTargetPos = null;
					mineStandPos = null;
					clearMovementInput();
					return;
				}
				chainMineOres(mineTargetPos, getOreFamilyBlock(mineState.getBlock())); // 连锁破坏：同族连通矿脉一并挖掉
				mineTargetPos = null; // 整条矿脉挖完换下一个
				mineStandPos = null;
				mineActionCooldown = MINE_ACTION_COOLDOWN;
			}
			return;
		}
		// 够得着但视线被挡（如隔墙角）且无可站立点 → 放弃该目标重选
		if (canReachBlockPos(mineTargetPos, MINE_REACH_SQR) && mineStandPos == null) {
			mineTargetPos = null;
			mineNavTarget = null;
			clearMovementInput();
			return;
		}
		// 距离远或需先走到站立点：移动由 applyMineInput 负责
	}

	/** 选择挖矿目标：BFS 壳层扫描 + 边缘过滤 + 镐等级分级匹配 + 评分排序，返回最高分矿石。 */
	private BlockPos selectMineTarget() {
		BlockPos center = getMineScanCenter();
		boolean following = isFollowEnabled();
		List<BlockPos> candidates = scanOresBfs(center);
		// 分级匹配（严格）：挖不动的矿（如木镐对钻石矿）直接不进候选，
		// 人偶只挑自己镐子挖得动且会掉落的矿。统一走 canPickaxeMine，杜绝某处漏判。
		BlockPos best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (BlockPos ore : candidates) {
			if (!passesMineEdgeChecks(ore)) {
				continue;
			}
			// 跟随模式（机会式顺路捡）：只挖人偶行进路线附近暴露在空气里、当前直接够得着的矿。
			// 埋藏矿不掘进、远矿不绕路、够不着不做站立点导航——否则在狭长隧道里
			// A* 会"成功"却走不通（死胡同/转不了身），反复尝试把人偶卡死（用户实测反馈）。
			if (following) {
				if (ore.distSqr(this.blockPosition()) > MINE_FOLLOW_MAX_TARGET_DIST_SQR) {
					continue;
				}
				if (!hasAdjacentAir(ore)) {
					continue; // 埋藏矿：不掘进
				}
				if (!canReachBlockPos(ore, MINE_REACH_SQR) || !hasLineOfSight(ore)) {
					continue; // 只挖当前直接够得着的矿
				}
			} else if (ore.distSqr(this.blockPosition()) > MINE_TARGET_MAX_DIST_SQR) {
				continue; // 非跟随：距人偶过远的矿直接不选，只会反复寻路失败被拉黑
			}
			BlockState oreState = level().getBlockState(ore);
			if (!canPickaxeMine(oreState)) {
				continue; // 无镐或镐等级不足：可拆但无掉落，绝不选（不浪费矿脉）
			}
			Long expire = mineBlacklist.get(ore);
			if (expire != null) {
				if (expire > level().getGameTime()) {
					continue; // 近期不可达，跳过
				}
				mineBlacklist.remove(ore); // 过期条目顺带移除，防长期运行内存增长
			}
			double score = scoreMineTarget(ore, center, candidates);
			if (canReachBlockPos(ore, MINE_REACH_SQR)) {
				score += 1000.0; // 够得着的矿优先挖：连锁破坏下挖一块即可清空整条矿脉，避免死磕墙后高价值矿
			}
			if (score > bestScore) {
				bestScore = score;
				best = ore;
			}
		}
		// 记录选中矿的站立点（供 applyMineInput 导航；直接够得着时为 null）
		mineStandPos = best != null ? findMineStandPos(best) : null;
		return best;
	}

	/**
	 * 找矿的可站立点：返回人偶站到后能挖到该矿的空气格；矿当前直接够得着时返回 null。
	 * 站立格要求：空气、下方有支撑、上方也空（人偶 2 格高）。
	 * 仅非跟随模式使用（跟随模式只挖直接够得着的矿，不做站立点导航）。
	 */
	private BlockPos findMineStandPos(BlockPos ore) {
		if (canReachBlockPos(ore, MINE_REACH_SQR) && hasLineOfSight(ore)) {
			return null;
		}
		for (Direction dir : Direction.values()) {
			BlockPos adj = ore.relative(dir);
			if (!level().getBlockState(adj).isAir()) {
				continue;
			}
			if (level().getBlockState(adj.below()).isAir()) {
				continue; // 下方无支撑
			}
			if (!level().getBlockState(adj.above()).isAir()) {
				continue; // 上方高度不够（人偶 2 格高）
			}
			return adj; // 从站立格中心到矿：水平 1 格、垂直 0，必然够得着
		}
		return null;
	}

	/** 从人偶眼睛到目标块中心是否无实心方块阻挡（隔墙矿不可挖）。 */
	private boolean hasLineOfSight(BlockPos pos) {
		Vec3 from = this.getEyePosition();
		Vec3 to = Vec3.atCenterOf(pos);
		BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/**
	 * 生灵级视线判定：自傀儡眼位直目标之眼位，以立方射线检测，遇实心方块即断。
	 * 与 {@link #hasLineOfSight(Vec3)} 同用 COLLIDER 射线、口径一致（所见即所行），
	 * 唯此以目标「眼位」为准，供新生目标入门之视线门禁用（弗透墙而窥）。
	 */
	private boolean hasClearSightTo(LivingEntity target) {
		Vec3 from = this.getEyePosition();
		Vec3 to = new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
		BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** 近战可否即刻命中：须在近战范围内且对目标有清晰视线（弗隔墙而击）。 */
	private boolean canStrikeNow(LivingEntity target) {
		return this.distanceToSqr(target) <= ATTACK_RANGE_SQR && hasClearSightTo(target);
	}

	/**
	 * A* 直抵目标失败、又无可达锚点时的贴墙滑行：向视线垂直侧滑移，绕出门缝/墙沿而非盲撞。
	 * 现 A* 已返"最近可达点"，故此仅于四面尽堵/路径走尽仍被挡时兜底。
	 */
	private void slideAlongWall(Vec3 target, float moveSpeed) {
		double dx = target.x - this.getX();
		double dz = target.z - this.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4) {
			return;
		}
		double nx = dx / len;
		double nz = dz / len;
		// 与视线垂直的滑向：顺时/逆时皆可，固定一侧
		double wx = -nz;
		double wz = nx;
		Vec3 waypoint = new Vec3(this.getX() + nx + wx * WALL_SLIDE_OFFSET, this.getY(), this.getZ() + nz + wz * WALL_SLIDE_OFFSET);
		steerAndMove(waypoint, moveSpeed);
	}

	/**
	 * BFS 扩展壳层扫描：从 center 出发六方向扩展，收集最多 MINE_MAX_SCAN_TARGETS 个矿石。
	 * 早停 + visited 上限防极端卡顿；矿石稀疏时退化为全半径扫描，返回已找到的矿。
	 * 跟随时用更小的扫描半径（机会式只找顺路的），非跟随用全半径。
	 */
	private List<BlockPos> scanOresBfs(BlockPos center) {
		List<BlockPos> ores = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(center);
		visited.add(center);
		int radius = (int) (isFollowEnabled() ? MINE_FOLLOW_SCAN_RANGE : MINE_SEARCH_RANGE);
		while (!queue.isEmpty() && ores.size() < MINE_MAX_SCAN_TARGETS && visited.size() < 8192) {
			BlockPos pos = queue.poll();
			if (center.distManhattan(pos) > radius) {
				continue;
			}
			if (!level().isLoaded(pos)) {
				continue; // 不扩展到未加载区块
			}
			if (isOreBlock(pos)) {
				ores.add(pos);
			}
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (!visited.contains(next)) {
					visited.add(next);
					queue.add(next);
				}
			}
		}
		return ores;
	}

	/** 评分：距离主导（就近先挖）+ 矿物价值 + 深度奖励 + 簇奖励 + 可达性。 */
	private double scoreMineTarget(BlockPos ore, BlockPos center, List<BlockPos> allOres) {
		double value = getOreValue(ore);
		double dist = center.distManhattan(ore);
		// 距离主导：每格 -8，矿物价值（10~100）只在距离相近时起决定作用，
		// 避免「远处钻石压过近处煤矿」的远矿优先问题（原公式 value - dist*0.5 价值压过距离）。
		double score = -dist * 8.0 + value + Math.max(0, center.getY() - ore.getY()) * 0.3;
		int cluster = 0;
		for (BlockPos o : allOres) {
			if (o.equals(ore)) {
				continue;
			}
			if (Math.abs(o.getX() - ore.getX()) <= 3
				&& Math.abs(o.getY() - ore.getY()) <= 3
				&& Math.abs(o.getZ() - ore.getZ()) <= 3) {
				cluster++;
			}
		}
		score += cluster * 0.4;
		if (hasAdjacentAir(ore)) {
			score += 0.2;
		}
		return score;
	}

	/**
	 * 破坏矿石方块：用镐头作为工具计算掉落（保证钻石等需正确工具才掉落），
	 * 掉落进人偶存储区，播放破坏粒子与音效，消耗镐头 1 点耐久。
	 * 分级兜底：镐子为空或其等级不足以采掘该矿石时返回 false——不拆方块、不连锁
	 * （空手虽然能移除方块但矿石无掉落，等于破坏资源）；调用方放弃该目标重选。
	 */
	private boolean mineBlock(BlockPos pos) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		BlockState state = level().getBlockState(pos);
		// 实际挖掘用"刚好够用的最低级镐"，把钻石/下界合金镐留给真正需要的矿，省耐久
		ItemStack pickaxe = findPickaxeForState(state);
		if (!canPickaxeMine(state)) {
			return false;
		}
		LootParams.Builder builder = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withParameter(LootContextParams.TOOL, pickaxe)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
			.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
		List<ItemStack> drops = state.getDrops(builder);
		level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level().levelEvent(2001, pos, Block.getId(state));
		this.playSound(state.getSoundType().getBreakSound(), 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		for (ItemStack drop : drops) {
			addToDollInventory(drop);
		}
		pickaxe.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
		return true;
	}

	/** 矿石同族归并：深层变体与普通变体视为同一种矿（连锁破坏时一并处理）。 */
	private Block getOreFamilyBlock(Block block) {
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
			return Blocks.DIAMOND_ORE;
		}
		if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
			return Blocks.EMERALD_ORE;
		}
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return Blocks.COAL_ORE;
		}
		if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
			return Blocks.LAPIS_ORE;
		}
		if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
			return Blocks.REDSTONE_ORE;
		}
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
			return Blocks.IRON_ORE;
		}
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
			return Blocks.GOLD_ORE;
		}
		if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
			return Blocks.COPPER_ORE;
		}
		return block; // 无变种的矿（下界石英/远古残骸/下界金矿石等）自成一族
	}

	/** BFS 收集与 root 六向连通的同族矿石（限 MINE_MAX_CHAIN_BLOCKS），按 y 从低到高排序。 */
	private List<BlockPos> collectConnectedOres(BlockPos root, Block family) {
		List<BlockPos> result = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(root);
		visited.add(root);
		while (!queue.isEmpty() && result.size() < MINE_MAX_CHAIN_BLOCKS) {
			BlockPos cur = queue.poll();
			result.add(cur);
			for (int[] d : TREE_NEIGHBORS) {
				BlockPos next = cur.offset(d[0], d[1], d[2]);
				if (!visited.contains(next)
					&& getOreFamilyBlock(level().getBlockState(next).getBlock()) == family) {
					visited.add(next);
					queue.add(next);
				}
			}
		}
		result.sort(Comparator.comparingInt(BlockPos::getY)); // 从下往上挖（与砍树一致）
		return result;
	}

	/**
	 * 连锁挖矿（模仿连锁挖矿模组，挖矿模式的核心能力）：挖掉一块矿石后，
	 * 从该位置沿矿石六向连通（BFS）收集整条矿脉的剩余同族矿石，全部静默掉落进
	 * 人偶存储区并移除方块（不逐块播粒子/音效，避免大矿脉刷屏卡顿）。
	 * 不是放大挖掘范围：只连锁矿石连通体，普通石头/泥土等不会被误拆；
	 * 上限沿用 MINE_MAX_CHAIN_BLOCKS 防死循环。
	 * 掉落物超背包容量时由 addToDollInventory 兜底落地，不会丢物品。
	 */
	private void chainMineOres(BlockPos mined, Block family) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		List<BlockPos> rest = collectConnectedOres(mined, family);
		int minedCount = 0;
		for (BlockPos pos : rest) {
			if (pos.equals(mined)) {
				continue; // 当前块已由 mineBlock 处理
			}
			// 连锁分级兜底：镐子为空或等级不足以采掘时停止连锁（剩余矿留待之后重选），
			// 避免第一把镐坏的瞬间、下一把低级镐把高级矿脉"空手拆掉"无掉落
			BlockState state = serverLevel.getBlockState(pos);
			if (!canPickaxeMine(state)) {
				break;
			}
			// 每块用"刚好够用的最低级镐"（同一条矿脉等级需求一致，通常就是同一把镐）
			ItemStack pickaxe = findPickaxeForState(state);
			if (pickaxe.isEmpty()) {
				break; // 理论上 canPickaxeMine 为真时必有一把够用，防御性保护
			}
			LootParams.Builder builder = new LootParams.Builder(serverLevel)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.TOOL, pickaxe)
				.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
				.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
			for (ItemStack drop : state.getDrops(builder)) {
				addToDollInventory(drop);
			}
			serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			pickaxe.hurtAndBreak(1, this, EquipmentSlot.MAINHAND); // 每块耗 1 点耐久（逐块记账，支持跨镐）
			minedCount++;
		}
	}

	// ------------------------------------------------------------------
	// 钓鱼模式（FISH）
	// ------------------------------------------------------------------

	/**
	 * 钓鱼模式移动输入：走向岸边目标水域（A* 寻路，可绕路爬台阶），
	 * 进入抛竿距离后停下面向水面；已抛竿时原地站定等待咬钩。
	 * 目标不可达（如悬崖下的水）则暂时跳过该水域，避免直线撞墙卡死。
	 */
	private void applyFishInput() {
		if (fishCastActive) {
			// 已抛竿：原地站定，面向水面等待咬钩
			if (fishTargetWater != null) {
				smoothFaceTowards(fishTargetWater.getX() + 0.5, fishTargetWater.getZ() + 0.5);
			}
			clearMovementInput();
			return;
		}
		if (fishTargetWater == null || !isWaterBlock(fishTargetWater)) {
			clearMovementInput();
			return;
		}
		if (canReachBlockPos(fishTargetWater, FISH_REACH_SQR)) {
			clearMovementInput();
			return;
		}
		Vec3 target = Vec3.atBottomCenterOf(fishTargetWater).add(0.0, 0.5, 0.0);
		boolean recalc = fishNavTarget == null
			|| target.distanceToSqr(fishNavTarget) > FISH_NAV_RECALC_SQR
			|| navigator.isPathDone();
		if (recalc) {
			fishNavTarget = target;
			if (!navigator.computePath(target)) {
				// 无路可达 → 暂时跳过该水域，稍后再找别的
				fishSkipPos = fishTargetWater;
				fishSkipUntil = level().getGameTime() + FISH_SKIP_TICKS;
				fishTargetWater = null;
				fishNavTarget = null;
				fishSearchCooldown = Math.max(fishSearchCooldown, FISH_SEARCH_COOLDOWN);
				clearMovementInput();
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			fishNavTarget = null;
			clearMovementInput();
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * FISH_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/**
	 * 钓鱼模式决策：找水 → 岸边抛竿 → 等待咬钩 → 收竿入包，循环往复。
	 * 鱼饵附魔（EnchantmentHelper.getFishingTimeReduction）按比例缩短咬钩等待；
	 * 海之眷顾附魔（withLuck）提高战利品表里宝藏的权重。
	 */
	private void updateFishMind() {
		fishActionCooldown = Math.max(0, fishActionCooldown - 1);
		// 运行时前置检查（方案A）：钓鱼竿耗尽/破损 → 关闭模式并同步控制面板高光
		if (findFishingRodStack().isEmpty()) {
			Player owner = getOwnerPlayer();
			if (owner != null && (fishTargetWater != null || fishCastActive)) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".fish_stop_no_rod", dollDisplayName()));
				voiceNoTool();
			}
			fishTargetWater = null;
			fishCastActive = false;
			fishWaitTicks = 0;
			fishNavTarget = null;
			clearMovementInput();
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		if (fishTargetWater != null && !isWaterBlock(fishTargetWater)) {
			// 水面被填/流走 → 放弃（连同抛竿状态一起重置）
			fishTargetWater = null;
			fishCastActive = false;
			fishWaitTicks = 0;
		}
		if (fishCastActive) {
			// 已抛竿：等待咬钩，倒计时归零即收竿
			if (fishWaitTicks > 0) {
				fishWaitTicks--;
				return;
			}
			reelInFish();
			return;
		}
		if (fishTargetWater == null) {
			if (fishSearchCooldown > 0) {
				fishSearchCooldown--;
				clearMovementInput();
				return;
			}
			fishTargetWater = findWaterTarget();
			if (fishTargetWater == null) {
				fishSearchCooldown = FISH_SEARCH_COOLDOWN; // 附近没水，稍后再找
				clearMovementInput();
				return;
			}
		}
		// 已到岸边抛竿距离且冷却完毕 → 抛竿
		if (canReachBlockPos(fishTargetWater, FISH_REACH_SQR) && fishActionCooldown <= 0) {
			castFishingRod();
		}
		// 移动由 applyFishInput 负责
	}

	/** 该位置是否为水（流体水方块）。 */
	private boolean isWaterBlock(BlockPos pos) {
		return level().getFluidState(pos).is(FluidTags.WATER);
	}

	/** 可抛竿水域：水方块且正上方是空气（浮漂能浮在水面）。 */
	private boolean isFishableWater(BlockPos pos) {
		return isWaterBlock(pos) && level().isEmptyBlock(pos.above());
	}

	/** 水域旁（同层四向 / 下坡一层）是否存在可站立岸边，保证人偶能站到抛竿距离内。 */
	private boolean hasShoreNear(BlockPos water) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos p = water.relative(dir);
			if (isStandableSpot(this.level(), p) || isStandableSpot(this.level(), p.below())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 在搜寻半径内找一处可抛竿的水域：水 + 上方空气 + 附近有可站立岸边，
	 * 且不在近期寻路失败的跳过列表中，离工作中心最近者优先。
	 */
	private BlockPos findWaterTarget() {
		BlockPos center = getFarmWorkCenter();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		int radius = (int) FISH_SEARCH_RANGE;
		for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
			for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					double d = pos.distSqr(center);
					if (d > FISH_SEARCH_RANGE * FISH_SEARCH_RANGE || d >= bestDist) {
						continue;
					}
					if (pos.equals(fishSkipPos) && level().getGameTime() < fishSkipUntil) {
						continue; // 近期寻路失败的水域，暂时跳过
					}
					if (!isFishableWater(pos) || !hasShoreNear(pos)) {
						continue;
					}
					best = pos;
					bestDist = d;
				}
			}
		}
		return best;
	}

	/**
	 * 抛竿：播放抛竿音效 + 挥臂动画，消耗钓鱼竿 1 点耐久，
	 * 按鱼饵附魔缩短后的随机时长设定咬钩倒计时，随后原地等待。
	 */
	private void castFishingRod() {
		ItemStack rod = findFishingRodStack();
		if (rod.isEmpty()) {
			return;
		}
		rod.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
		this.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.5f,
			0.4f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
		this.swing(InteractionHand.MAIN_HAND);
		fishCastActive = true;
		int base = FISH_BITE_BASE_TICKS
			+ this.getRandom().nextInt(FISH_BITE_MAX_TICKS - FISH_BITE_BASE_TICKS + 1);
		// 海洋人偶天赋：上钩间隔 ×0.5（更快咬钩），与设计平衡一致
		if (isSeaDoll()) {
			base = (int) (base * 0.5f);
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			float reduction = EnchantmentHelper.getFishingTimeReduction(serverLevel, rod, this);
			fishWaitTicks = Math.max(20, (int) (base * (1.0f - reduction)));
		} else {
			fishWaitTicks = base;
		}
	}

	/**
	 * 收竿（咬钩）：水面溅起水花粒子与音效，按原版钓鱼战利品表
	 * （BuiltInLootTables.FISHING，含鱼/垃圾/宝藏）生成掉落进人偶存储区，
	 * 附赠少量经验（与玩家钓鱼一致：1-6 点）。随后重置，冷却后换一处水域继续。
	 */
	private void reelInFish() {
		if (this.level() instanceof ServerLevel serverLevel) {
			if (fishTargetWater != null) {
				double x = fishTargetWater.getX() + 0.5;
				double y = fishTargetWater.getY() + 1.0;
				double z = fishTargetWater.getZ() + 0.5;
				serverLevel.sendParticles(ParticleTypes.SPLASH, x, y, z, 8, 0.3, 0.2, 0.3, 0.1);
				this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.5f,
					0.4f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
				this.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f,
					0.4f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
				ItemStack rod = findFishingRodStack();
				LootParams.Builder builder = new LootParams.Builder(serverLevel)
					.withParameter(LootContextParams.ORIGIN, new Vec3(x, y, z))
					.withParameter(LootContextParams.TOOL, rod)
					.withOptionalParameter(LootContextParams.THIS_ENTITY, this);
				if (!rod.isEmpty()) {
					// 与原版 FishingHook 一致：海之眷顾附魔 + 实体幸运属性（人偶被泼幸运药水后叠加生效）
					builder.withLuck(EnchantmentHelper.getFishingLuckBonus(serverLevel, rod, this) + this.getLuck());
				}
			// 海洋人偶重平衡：删除"内置必出宝藏"——改用标准 FISHING 表，
			// 海之眷顾/幸运改由钓竿附魔自然生效（已在 withLuck 传入）。
			// 收杆视作 ×2 产物：海洋人偶额外多滚一次战利品表。
			ResourceKey<LootTable> fishTableKey = BuiltInLootTables.FISHING;
			LootTable table = serverLevel.getServer().reloadableRegistries()
				.getLootTable(fishTableKey);
			int rolls = isSeaDoll() ? 2 : 1;
			for (int r = 0; r < rolls; r++) {
				for (ItemStack drop : table.getRandomItems(builder.create(LootContextParamSets.FISHING))) {
					addToDollInventory(drop);
				}
			}
				int xp = 1 + this.getRandom().nextInt(6);
				serverLevel.addFreshEntity(
					new ExperienceOrb(serverLevel, this.getX(), this.getY() + 0.5, this.getZ(), xp));
			}
		}
		this.swing(InteractionHand.MAIN_HAND);
		fishCastActive = false;
		fishTargetWater = null;
		fishNavTarget = null;
		fishActionCooldown = FISH_ACTION_COOLDOWN;
	}

	/**
	 * 射手模式决策：指挥棒指定的强制目标优先（无距离限制），否则正常搜寻。
	 * 蓄力/装填与射程解耦——只要目标有效就持续推进，发射时才校验射程与视线，
	 * 保证拉扯（后退/追击）过程中弓照常拉弓、弩照常装填，输出不断档。
	 */
	private void updateRangedMind() {
		attackCooldown = Math.max(0, attackCooldown - 1);
		// 运行时前置检查（方案A）：普通人偶无远程武器 → 关闭模式并同步控制面板高亮
		if (!hasInnateCombatAbility()
				&& getRangedWeapon().isEmpty()) {
			Player owner = getOwnerPlayer();
			if (owner != null && rangedTarget != null) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".ranged_stop_no_weapon", dollDisplayName()));
				voiceNoTool();
			}
			rangedTarget = null;
			endUsingRangedWeapon();
			crossbowLoadTicks = -1;
			bowChargeTicks = -1;
			clearMovementInput();
			getEntityData().set(DATA_ACTIVE_MODE, -1);
			pushSnapshotToOwner();
			return;
		}
		// 指挥棒指定的强制目标优先：跳过距离检查和自动搜寻
		LivingEntity forced = resolveForcedTarget();
		if (forced != null) {
			rangedTarget = forced;
		} else {
			if (rangedTarget != null && (!rangedTarget.isAlive() || rangedTarget.isRemoved()
				|| this.distanceToSqr(rangedTarget) > RANGED_MAX_PURSUE_DISTANCE_SQR)) {
				rangedTarget = null;
			}
			if (rangedTarget == null) {
				rangedTarget = findRangedTarget();
			}
		}
		if (rangedTarget == null) {
			// 无目标：重置远程状态（目标丢失时停止拉弓/装填，动画复位）
			endUsingRangedWeapon();
			crossbowLoadTicks = -1;
			bowChargeTicks = -1;
			return;
		}
		// 武器检查：末影人偶持末影斧时走投掷分支（优先于弓/弩）
		ItemStack mainHand = getItemBySlot(EquipmentSlot.MAINHAND);
		if (isEnderDoll() && mainHand.getItem() instanceof EnderAxeItem) {
			updateEnderAxeThrowMind(mainHand);
			return;
		}
		// 武器检查：下界人偶持地狱剑时走飞剑召唤分支（优先于弓/弩）
		if (isNetherDoll() && mainHand.getItem() instanceof NetherSwordItem) {
			updateNetherFlyingSwordMind(mainHand);
			return;
		}
		// 武器检查：主手优先，其次全物品栏兜底（主手物品被拿走时仍能战斗）
		ItemStack weapon = getRangedWeapon();
		if (weapon.isEmpty()) {
			endUsingRangedWeapon();
			crossbowLoadTicks = -1;
			bowChargeTicks = -1;
			return;
		}
		if (weapon.getItem() instanceof CrossbowItem) {
			updateCrossbowMind(weapon);
		} else {
			updateBowMind(weapon);
		}
	}

	/**
	 * 末影人偶投掷末影斧逻辑（射手模式专属）：
	 * 末影人偶在射手模式持末影斧时，不再使用弓/弩，而是像玩家一样投掷末影斧。
	 * 投掷命中后附带 50% 斩杀线（由 handleEnderExecute 配合处理）。
	 * 忠诚附魔时投掷斧会飞回人偶背包（ThrownEnderAxe.tick 回收）。
	 * 冷却 3 秒，与龙息喷吐冷却独立。
	 */
	private static final int AXE_THROW_COOLDOWN_TICKS = 60; // 投掷冷却 3 秒
	private void updateEnderAxeThrowMind(ItemStack weapon) {
		if (rangedTarget == null || !rangedTarget.isAlive() || rangedTarget.isRemoved()) {
			return;
		}
		if (attackCooldown > 0) {
			return;
		}
		if (!canShoot(rangedTarget)) {
			return;
		}
		if (weapon.nextDamageWillBreak()) {
			return;
		}
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// 消耗 1 把并投掷（耐久消耗在命中时由 postHurtEffects 处理）
		ItemStack consumed = weapon.consumeAndReturn(1, this);
		ThrownEnderAxe thrownAxe = new ThrownEnderAxe(serverLevel, this, consumed);
		shootProjectileAt(thrownAxe, predictAim(rangedTarget, EnderAxeItem.PROJECTILE_SHOOT_POWER),
			EnderAxeItem.PROJECTILE_SHOOT_POWER, 1.0f);
		serverLevel.addFreshEntity(thrownAxe);
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.TRIDENT_THROW, this.getSoundSource(), 1.0f, 1.0f);
		attackCooldown = AXE_THROW_COOLDOWN_TICKS;
		this.swing(InteractionHand.MAIN_HAND);
	}

	/**
	 * 下界人偶召唤飞剑逻辑（射手模式专属）：
	 * 下界人偶在射手模式持地狱剑时，像玩家蓄力召唤一样在身边召唤一把飞行的地狱剑
	 * （{@link NetherFlyingSwordEntity}），飞剑自动索敌攻击 16 格半径内的敌对生物。
	 * 同一召唤者同时仅一把：已有归属自己的飞剑时跳过召唤。
	 * 召唤冷却 60 tick（3 秒），避免每帧遍历检查。
	 */
	private static final int NETHER_FLYING_SWORD_SUMMON_COOLDOWN_TICKS = 60; // 召唤检查冷却 3 秒
	private void updateNetherFlyingSwordMind(ItemStack weapon) {
		if (rangedTarget == null || !rangedTarget.isAlive() || rangedTarget.isRemoved()) {
			return;
		}
		if (attackCooldown > 0) {
			return;
		}
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// 已有归属自己的飞剑则跳过（飞剑持续存在，自动索敌）
		boolean hasSword = !serverLevel.getEntitiesOfClass(NetherFlyingSwordEntity.class,
			this.getBoundingBox().inflate(NetherFlyingSwordEntity.SEARCH_RADIUS),
			sword -> sword.isOwnedBy(this)).isEmpty();
		if (hasSword) {
			attackCooldown = NETHER_FLYING_SWORD_SUMMON_COOLDOWN_TICKS;
			return;
		}
		// 召唤飞剑（顶替规则 + 音效，与玩家 NetherSwordItem.onUseTick 一致）
		NetherFlyingSwordEntity.replaceExisting(serverLevel, this);
		NetherFlyingSwordEntity sword = new NetherFlyingSwordEntity(serverLevel, this);
		serverLevel.addFreshEntity(sword);
		serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.TRIDENT_THROW, this.getSoundSource(), 1.0f, 0.8f);
		attackCooldown = NETHER_FLYING_SWORD_SUMMON_COOLDOWN_TICKS;
		this.swing(InteractionHand.MAIN_HAND);
	}

	/**
	 * 客户端 AvatarRenderer 据此把手臂摆成对应姿势：
	 * 弓 → BOW_AND_ARROW（双持拉弓，与骷髅完全一致——骷髅模型直接复用父类该姿势）；
	 * 弩 → CROSSBOW_CHARGE（动画装填，与掠夺者用的是同一个 AnimationUtils.animateCrossbowCharge）。
	 * 主手此时显示快捷栏里的弓/弩（getItemBySlot 按模式映射），与进食共用同一套机制。
	 */
	private void beginUsingRangedWeapon() {
		if (!isUsingItem()) {
			startUsingItem(InteractionHand.MAIN_HAND);
		}
	}

	/**
	 * 退出"使用武器"状态（动画复位）：
	 * 弓放弦恢复普通持弓；弩装填完成恢复普通持弩（装填组件已写入，
	 * 客户端会因 CHARGED_PROJECTILES 非空自动改摆 CROSSBOW_HOLD 瞄准姿势，
	 * 与掠夺者持满弩姿势一致）。
	 * 用 stopUsingItem 而非 releaseUsingItem：弓/弩的 releaseUsing 只在 Player 上
	 * 触发原版射击/装填，人偶要自己控制发射时机，避免被原版逻辑抢走。
	 */
	private void endUsingRangedWeapon() {
		if (isUsingItem()) {
			stopUsingItem();
		}
	}

	/**
	 * 弓射击逻辑（拉弓蓄力状态机）：
	 * 1) 未拉弓 → 有弹药（或无限附魔）则开始拉弓并进入 startUsingItem（客户端摆双持拉弓姿势）；
	 * 2) 拉弓中 → 推进进度（可边移动边拉弓，姿势保持）；
	 * 3) 已拉满 → 等待发射时机（射程+视线+冷却），满足即放箭（先 stopUsingItem 复位姿势）。
	 * 发射消耗 1 支背包箭（无限附魔不消耗），通过 ProjectileUtil.getMobArrow 创建箭矢——
	 * 26.2 中力量/火焰/击退/穿透附魔会在构造/命中时由原版自动应用，无需手动处理。
	 */
	private void updateBowMind(ItemStack weapon) {
		int paleChargeTicks = isPaleDollPaleBow() ? BOW_CHARGE_TICKS / 3 : BOW_CHARGE_TICKS;
		if (bowChargeTicks < 0) {
			// 未拉弓：有弹药才启动（同时进入使用状态，客户端摆出双持拉弓姿势）
			if (!hasAmmoForBow(weapon)) {
				return;
			}
			bowChargeTicks = 0;
			beginUsingRangedWeapon();
		} else if (bowChargeTicks >= paleChargeTicks) {
			// 已拉满：等可发射时机（拉满状态保持，目标进入射程即射，避免反复拉弓抖动）
			if (attackCooldown <= 0 && canShoot(rangedTarget)) {
				endUsingRangedWeapon(); // 放弦复位（先于发射，避免放箭帧仍保持拉弓姿势）
				releaseBowShot(weapon);
				bowChargeTicks = -1;
			}
			return;
		} else {
			bowChargeTicks++;
		}
	}

	/** 苍白人偶持苍白弓时，是否启用专属特效（快速蓄力 + 高速箭矢 + 不耗箭）。 */
	private boolean isPaleDollPaleBow() {
		return getDollVariant() == DollVariant.PALE
			&& getRangedWeapon().getItem() instanceof PaleBowItem;
	}

	/**
	 * 弓是否有弹药可用：无限附魔恒真，苍白人偶持苍白弓恒真，否则背包要有箭。 */
	private boolean hasAmmoForBow(ItemStack weapon) {
		if (isPaleDollPaleBow()) return true;
		if (EnchantmentHelper.getItemEnchantmentLevel(enchantmentHolder(Enchantments.INFINITY), weapon) > 0) {
			return true;
		}
		return !findArrowInInventory().isEmpty();
	}

	/** 释放弓弦放箭（服务端执行）。预判目标移动方向发射，并注册飞行中追踪。 */
	private void releaseBowShot(ItemStack weapon) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		boolean paleBow = isPaleDollPaleBow();
		float paleBowPower = paleBow ? BOW_SHOOT_POWER * 2.0f : BOW_SHOOT_POWER;
		float paleBowDivergence = paleBow ? 0.0f : BOW_SHOOT_DIVERGENCE;
		ItemStack ammo = findArrowInInventory();
		boolean infinite = paleBow || EnchantmentHelper.getItemEnchantmentLevel(enchantmentHolder(Enchantments.INFINITY), weapon) > 0;
		if (ammo.isEmpty() && !infinite) {
			return;
		}
		if (ammo.isEmpty()) {
			ammo = new ItemStack(Items.ARROW); // 无限附魔时凭空生成
		}
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, paleBowPower, weapon);
		shootProjectileAt(arrow, predictAim(rangedTarget, paleBowPower), paleBowPower, paleBowDivergence);
		this.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
		serverLevel.addFreshEntity(arrow);
		if (!infinite) {
			ammo.shrink(1);
		}
		trackedArrows.put(arrow.getId(), rangedTarget.getId());
		attackCooldown = BOW_SHOOT_COOLDOWN_TICKS;
		this.swing(InteractionHand.MAIN_HAND);
		weapon.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
	}

	/**
	 * 弩射击逻辑（状态机）：
	 * 1) 已装填（CHARGED_PROJECTILES 非空）→ 射程+视线内发射；
	 * 2) 未装填且未开始 → 背包有箭则开始装填（不要求射程，边退边装），
	 *    并进入 startUsingItem——客户端摆 CROSSBOW_CHARGE 装填姿势（与掠夺者同款动画，
	 *    进度按 getChargeDuration 与装填状态机自动同步），装填音效改由原版
	 *    CrossbowItem.onUseTick 按 20%/50% 进度自动播放（不再手动播开始音）；
	 * 3) 装填中 → 推进进度，达到装填时长后消耗箭写入装填组件并退出使用状态
	 *    （客户端随即切换为 CROSSBOW_HOLD 瞄准姿势，与掠夺者持满弩一致）。
	 */
	private void updateCrossbowMind(ItemStack crossbow) {
		if (CrossbowItem.isCharged(crossbow)) {
			// 已装填：射程内发射（performShooting 内部处理弹道、附魔、音效、耐久）
			if (attackCooldown <= 0 && canShoot(rangedTarget)) {
				rangedCrossbowShot(crossbow);
			}
			return;
		}
		if (crossbowLoadTicks < 0) {
			// 未装填且未开始：背包有箭才启动装填
			if (!findArrowInInventory().isEmpty()) {
				crossbowLoadTicks = 0;
				beginUsingRangedWeapon();
			}
			return;
		}
		int chargeDuration = CrossbowItem.getChargeDuration(crossbow, this);
		if (crossbowLoadTicks >= chargeDuration) {
			// 装填时长到达：消耗箭写入 CHARGED_PROJECTILES（多重射击装 3 支）
			if (loadCrossbowProjectiles(crossbow)) {
				endUsingRangedWeapon(); // 装填完成：退出使用状态，客户端切换为持弩瞄准姿势
				this.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 1.0f, 1.0f);
				this.swing(InteractionHand.MAIN_HAND);
			}
			crossbowLoadTicks = -1;
		} else {
			crossbowLoadTicks++;
		}
	}

	/**
	 * 发射已装填的弩（自定义发射，替代 performShooting 以支持箭矢追踪）：
	 * 清空装填组件，逐支创建弩箭，预判目标移动方向发射，多重射击三支横向散开。
	 * 穿透/多重等附魔由 getMobArrow 传入武器时自动生效。
	 */
	private void rangedCrossbowShot(ItemStack crossbow) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!(crossbow.getItem() instanceof CrossbowItem)) {
			return;
		}
		ChargedProjectiles loaded = crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
		if (loaded.isEmpty()) {
			return;
		}
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY); // 消耗装填
		List<ItemStack> ammos = loaded.itemCopies();
		int count = ammos.size();
		Vec3 aim = predictAim(rangedTarget, CrossbowItem.MOB_ARROW_POWER);
		Vec3 spreadAxis = horizontalPerpendicular(aim); // 多重射击横向散开轴
		for (int i = 0; i < count; i++) {
			AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammos.get(i), CrossbowItem.MOB_ARROW_POWER, crossbow);
			arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
			// 第 i 支围绕中心轴散开：(-0.6, 0, +0.6)
			Vec3 offset = spreadAxis.scale((i - (count - 1) / 2.0) * CROSSBOW_MULTISHOT_SPREAD);
			shootProjectileAt(arrow, aim.add(offset), CrossbowItem.MOB_ARROW_POWER, CROSSBOW_SHOOT_DIVERGENCE);
			serverLevel.addFreshEntity(arrow);
			trackedArrows.put(arrow.getId(), rangedTarget.getId());
		}
		this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
		attackCooldown = CROSSBOW_SHOOT_COOLDOWN_TICKS;
		this.swing(InteractionHand.MAIN_HAND);
		crossbow.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
	}

	/**
	 * 预判目标射击点：目标中心 + 目标当前速度 × 估算飞行时间。
	 * 对移动目标直接朝预测点打，追踪修正只需小幅纠偏。
	 */
	private Vec3 predictAim(LivingEntity target, float speed) {
		Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
		double dist = this.distanceTo(target);
		double flightTicks = Math.min(dist / speed, ARROW_TRACK_MAX_PREDICT_TICKS);
		return targetCenter.add(target.getDeltaMovement().scale(flightTicks));
	}

	/** 射击方向（从人偶到预测点）的水平法向量，用于多重射击横向散开。 */
	private Vec3 horizontalPerpendicular(Vec3 aim) {
		double dx = aim.x - this.getX();
		double dz = aim.z - this.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.001) {
			return new Vec3(1, 0, 0);
		}
		return new Vec3(-dz / len, 0, dx / len);
	}

	/** 朝预测点发射箭矢：无散布直射 + 按水平距离上仰补偿弹道。 */
	private void shootProjectileAt(AbstractArrow arrow, Vec3 aim, float speed, float divergence) {
		double dx = aim.x - arrow.getX();
		double dz = aim.z - arrow.getZ();
		double dy = aim.y - arrow.getY();
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + horizontalDist * 0.2, dz, speed, divergence);
		// 下界人偶火焰箭：弓/弩箭矢点燃 8 秒，命中目标后自动引燃；手持地狱剑翻倍 16 秒
		if (getDollVariant() == DollVariant.NETHER) {
			arrow.igniteForSeconds(hasNetherSwordEquipped() ? 16.0f : 8.0f);
		}
	}

	/**
	 * 已发射箭矢的飞行中修正（服务端每 tick 调用）：每 N tick 把箭的速度方向
	 * 重新指向目标当前位置，保证目标移动/弹道下坠都不影响命中。
	 * 箭落地（速度≈0）、命中消失或目标失效时自动结束追踪。
	 */
	private void trackArrows() {
		if (trackedArrows.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		trackedArrows.entrySet().removeIf(entry -> {
			Entity arrow = serverLevel.getEntity(entry.getKey());
			Entity target = serverLevel.getEntity(entry.getValue());
			if (arrow == null || !arrow.isAlive() || arrow.isRemoved()
				|| target == null || !target.isAlive() || target.isRemoved()) {
				return true;
			}
			if (arrow.tickCount % ARROW_TRACK_INTERVAL != 0) {
				return false;
			}
			double speed = arrow.getDeltaMovement().length();
			if (speed <= 0.01) {
				return true; // 箭已停下（落地/被挡住），结束追踪
			}
			// 方向偏差小时跳过修正，减少 Vec3 中间对象分配
			Vec3 currentDir = arrow.getDeltaMovement().normalize();
			Vec3 targetDir = target.getEyePosition().subtract(arrow.position()).normalize();
			if (currentDir.dot(targetDir) >= 0.95) {
				return false; // 偏差 < ~18°，不修正
			}
			Vec3 dir = targetDir;
			Vec3 velocity = dir.scale(speed);
			arrow.setDeltaMovement(velocity);
			// 让箭模型指向飞行方向
			arrow.setYRot((float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f);
			arrow.setXRot((float) (Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
			return false;
		});
	}

	/**
	 * 完成弩装填：从背包取箭（多重射击附魔需 3 支）写入 CHARGED_PROJECTILES，
	 * 同时从背包消耗对应数量。成功返回 true。
	 */
	private boolean loadCrossbowProjectiles(ItemStack crossbow) {
		ItemStack ammo = findArrowInInventory();
		if (ammo.isEmpty()) {
			return false;
		}
		int multishot = EnchantmentHelper.getItemEnchantmentLevel(enchantmentHolder(Enchantments.MULTISHOT), crossbow);
		int count = multishot > 0 ? 3 : 1;
		if (ammo.getCount() < count) {
			return false; // 多重射击需要 3 支箭，不够则装填失败
		}
		List<ItemStack> projectiles = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			projectiles.add(ammo.copyWithCount(1));
		}
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(projectiles));
		ammo.shrink(count);
		return true;
	}

	/**
	 * 判断物品是否是远程武器（弓或弩）。
	 */
	private boolean isRangedWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	/**
	 * 在人偶物品栏（全45格）中查找弓或弩，返回找到的槽位索引，未找到返回 -1。
	 */
	private int findRangedWeaponInHotbar() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isRangedWeapon(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 获取当前射击用武器：主手（快捷栏模式前置格）优先，
	 * 主手为空或不是弓/弩时，全物品栏兜底查找，都没有返回空。
	 */
	private ItemStack getRangedWeapon() {
		ItemStack hand = getItemBySlot(EquipmentSlot.MAINHAND);
		if (isRangedWeapon(hand)) {
			return hand;
		}
		// 末影人偶的末影斧也作为远程武器识别（影响 canShoot 中的射程判定）
		if (isEnderDoll() && hand.getItem() instanceof EnderAxeItem) {
			return hand;
		}
		// 下界人偶的地狱剑也作为远程武器识别（影响 canShoot 中的射程判定）
		if (isNetherDoll() && hand.getItem() instanceof NetherSwordItem) {
			return hand;
		}
		return findRangedWeaponInInventory();
	}

	/**
	 * 在整格人偶物品栏中查找弓或弩，作为射箭时的发射武器；没找到返回空。
	 */
	private ItemStack findRangedWeaponInInventory() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isRangedWeapon(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 在整格人偶物品栏中查找箭矢（普通箭/光灵箭/药箭等 ArrowItem 子类），
	 * 作为弓的消耗品与弩的装填物；没找到返回空。
	 */
	private ItemStack findArrowInInventory() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() instanceof ArrowItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 获取附魔注册表项对应的 Holder，用于读取附魔等级。 */
	private Holder<Enchantment> enchantmentHolder(ResourceKey<Enchantment> key) {
		return this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/**
	 * 射手模式专属目标搜寻：范围比近战大，优先主人攻击目标，
	 * 否则搜索附近敌对生物。
	 */
	private LivingEntity findRangedTarget() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		LivingEntity assistTarget = resolveAssistTarget();
		if (assistTarget != null) {
			return assistTarget;
		}
		AABB area = this.getBoundingBox().inflate(RANGED_SEARCH_RANGE);
		List<LivingEntity> candidates = serverLevel.getEntities(
			EntityTypeTest.forClass(LivingEntity.class), area,
			e -> e.isAlive() && e != this && !(e instanceof Player) && e instanceof Enemy && hasClearSightTo(e));
		LivingEntity nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (LivingEntity e : candidates) {
			double d = this.distanceToSqr(e);
			if (d < nearestDist) {
				nearestDist = d;
				nearest = e;
			}
		}
		return nearest;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt(ACTIVE_MODE_NBT_KEY, getActiveMode());
		output.putBoolean(FOLLOW_ENABLED_NBT_KEY, isFollowEnabled());
		output.putInt(DOLL_LEVEL_NBT_KEY, getDollLevel());
		if (ownerUuid != null) {
			output.putString(OWNER_UUID_NBT_KEY, ownerUuid.toString());
		}
		output.store(PROFILE_NAME_NBT_KEY, ResolvableProfile.CODEC, profile);
		inventory.saveToValue(output);
		if (workAreaMin != null && workAreaMax != null) {
			output.putIntArray(WORK_AREA_NBT_KEY, new int[] {
				workAreaMin.getX(), workAreaMin.getY(), workAreaMin.getZ(),
				workAreaMax.getX(), workAreaMax.getY(), workAreaMax.getZ()
			});
		}
		if (tunnelDir != null && tunnelEntry != null) {
			output.putInt(TUNNEL_DIR_NBT_KEY, tunnelDir.get3DDataValue());
			output.putIntArray(TUNNEL_ENTRY_NBT_KEY, new int[] {
				tunnelEntry.getX(), tunnelEntry.getY(), tunnelEntry.getZ()
			});
		}
		output.putBoolean(TUNNEL_ACTIVE_NBT_KEY, tunneling);
		output.putInt(NBT_VERSION_KEY, NBT_VERSION);
		// 变体用 name() 字符串持久化：未来在中间插入新变体不会让旧存档 ordinal 整体错位
		output.putString(DOLL_VARIANT_NBT_KEY, getDollVariant().name());
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		// 先读取等级并调整最大血量，再让 super 读取血量（避免高血量被 20 上限截断）
		int level = input.getIntOr(DOLL_LEVEL_NBT_KEY, 0);
		getEntityData().set(DATA_DOLL_LEVEL, level);
		// 读取 NBT 版本号（预留：未来字段语义变更时按版本分支迁移，默认值即兼容）
		@SuppressWarnings("unused")
		int nbtVersion = input.getIntOr(NBT_VERSION_KEY, 0);
		// 读取变体：新存档存为 name() 字符串（中间插入新变体不会错位）；旧存档存为 ordinal int
		DollVariant variant;
		String variantName = input.getStringOr(DOLL_VARIANT_NBT_KEY, "");
		if (!variantName.isEmpty()) {
			variant = DollVariant.byName(variantName);
		} else {
			int variantOrdinal = input.getIntOr(DOLL_VARIANT_NBT_KEY, -1);
			if (variantOrdinal < 0) {
				variantOrdinal = (level >= 5) ? DollVariant.WARDEN.ordinal() : DollVariant.NONE.ordinal();
			}
			variant = DollVariant.byOrdinal(variantOrdinal);
		}
		getEntityData().set(DATA_DOLL_VARIANT, variant.ordinal());
		getEntityData().set(DATA_IS_WARDEN_VARIANT, variant == DollVariant.WARDEN);
		double maxHp = computeMaxHealth(variant, level);
		AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(maxHp);
		}
		AttributeInstance knockbackRes = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockbackRes != null) {
			knockbackRes.setBaseValue(variant == DollVariant.WARDEN ? 1.0 : 0.0);
		}
		super.readAdditionalSaveData(input);
		// 海洋人偶专属：加载存档后恢复水中寻路许可（仅 SEA 生效）
		navigator.setAllowWater(isSeaDoll());
		// 向后兼容：旧存档存有自定义 dollUuid（与引擎 UUID 不同）。
		// 将其采纳为引擎 UUID，使已绑定的旧蛋仍能通过 server.getEntity(uuid) 召回。
		String oldUuidStr = input.getStringOr(DOLL_UUID_NBT_KEY, "");
		if (!oldUuidStr.isEmpty()) {
			try {
				this.setUUID(UUID.fromString(oldUuidStr));
			} catch (IllegalArgumentException ignored) {
			}
		}
		setActiveMode(input.getIntOr(ACTIVE_MODE_NBT_KEY, -1));
		boolean follow = input.getBooleanOr(FOLLOW_ENABLED_NBT_KEY, false);
		// 钓鱼模式与跟随互斥：旧存档可能出现两者同时开启的非法组合，加载时强制关闭跟随
		if (getActiveMode() == DollMode.FISH.getIndex() && follow) {
			follow = false;
		}
		getEntityData().set(DATA_FOLLOW_ENABLED, follow);
		String ownerStr = input.getStringOr(OWNER_UUID_NBT_KEY, "");
		if (!ownerStr.isEmpty()) {
			try {
				ownerUuid = UUID.fromString(ownerStr);
			} catch (IllegalArgumentException ignored) {
				ownerUuid = null;
			}
		}
		input.read(PROFILE_NAME_NBT_KEY, ResolvableProfile.CODEC).ifPresent(p -> profile = p);
		inventory.loadFromValue(input);
		input.getIntArray(WORK_AREA_NBT_KEY).ifPresent(arr -> {
			if (arr.length == 6) {
				workAreaMin = new BlockPos(arr[0], arr[1], arr[2]);
				workAreaMax = new BlockPos(arr[3], arr[4], arr[5]);
			}
		});
		int tunnelDirId = input.getIntOr(TUNNEL_DIR_NBT_KEY, -1);
		if (tunnelDirId >= 0 && tunnelDirId < 6) {
			tunnelDir = Direction.from3DDataValue(tunnelDirId);
		}
		input.getIntArray(TUNNEL_ENTRY_NBT_KEY).ifPresent(arr -> {
			if (arr.length == 3) {
				tunnelEntry = new BlockPos(arr[0], arr[1], arr[2]);
			}
		});
		// 读档后一律不自动掘进（旧存档可能残留 tunneling=true 的持久化状态，
		// 切回挖矿模式会被 updateTunnelDrill 激活并向旧方向瞬移掘进）；恢复需指挥棒右键人偶
		tunneling = false;
		sanitizeInventorySlots();
	}

	/**
	 * 下界人偶凋零免疫：免疫凋零效果（凋灵Boss/凋灵骷髅施加的 Wither 药水）。
	 */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (getDollVariant() == DollVariant.NETHER && effect.is(MobEffects.WITHER)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	/**
	 * 下界人偶灵魂沙免疫：在灵魂沙/浆果丛上不减速。
	 */
	@Override
	public float getBlockSpeedFactor() {
		if (getDollVariant() == DollVariant.NETHER) {
			return 1.0f;
		}
		return super.getBlockSpeedFactor();
	}

	/**
	 * 海洋人偶水下全速：原版水中移速系数 getWaterSlowDown() 默认 0.8（水中减速）。
	 * 仅 SEA 返回 1.0（全速），其他变体走 super（0.8）。只在水里生效，不影响陆地移动。
	 */
	@Override
	public float getWaterSlowDown() {
		if (isSeaDoll()) {
			return 1.0f;
		}
		return super.getWaterSlowDown();
	}

	/**
	 * 修正非法槽位：旧版本 bug 曾把收获物塞进护甲槽(1-4)/装饰槽(0/5/7/8)，
	 * 这些槽位在 GUI 中禁止放置普通物品。加载存档时把这类物品移回存储区，
	 * 存储区满则掉落在地（不丢物品）。副手槽(6)允许任意物品，不受影响。
	 */
	public void sanitizeInventorySlots() {
		if (level().isClientSide()) {
			return;
		}
		EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i <= 8; i++) {
			if (i == 6) {
				continue; // 副手槽允许任意物品
			}
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			boolean legal;
			if (i >= 1 && i <= 4) {
				// 护甲槽：必须是对应装备栏位的可装备物品
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				legal = equippable != null && equippable.slot() == armorSlots[i - 1];
			} else {
				// 装饰槽 0/5/7/8：一律不允许普通物品
				legal = false;
			}
			if (!legal) {
				inventory.setItem(i, ItemStack.EMPTY);
				addToDollInventory(stack);
			}
		}
	}

	/**
	 * 判断刷怪蛋与本实体是否绑定。蛋中存有人偶 UUID。
	 */
	public boolean matchesEgg(ItemStack eggStack) {
		if (eggStack.isEmpty()) {
			return false;
		}
		CustomData data = eggStack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		return this.getUUID().toString().equals(data.copyTag().getStringOr(DollSpawnEggItem.DOLL_UUID_NBT_KEY, ""));
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 pos) {
		if (this.level().isClientSide()) {
			return InteractionResult.PASS;
		}
		// 26.2 客户端 startUseItem 会遍历主手+副手各触发一次 interact，
		// 只处理主手、忽略副手，避免重复提示/重复打开菜单
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		// 手持刷怪蛋（回收）/ 指挥棒（选中/调试）时不打开菜单。
		// 26.2 服务端在 Entity.interact 返回非 SUCCESS 后会**自动**调用 Item.interactLivingEntity，
		// 因此这里直接返回 PASS 交给服务端转发即可（只调一次）；
		// 绝不能手动调用，否则"手动一次 + 服务端自动一次"会重复提示（not_your_doll 等）。
		ItemStack stackInHand = player.getItemInHand(hand);
		if (!stackInHand.isEmpty()) {
			net.minecraft.world.item.Item item = stackInHand.getItem();
			if (item instanceof io.github.a10086ovo.doll.item.DollSpawnEggItem
					|| item instanceof io.github.a10086ovo.doll.item.DollBatonItem) {
				return InteractionResult.PASS;
			}
		}
		MenuProvider provider = new ExtendedMenuProvider<Integer>() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("container." + DollModConstants.MOD_ID + ".doll_inventory");
			}

			@Override
			public Integer getScreenOpeningData(ServerPlayer serverPlayer) {
				return DollEntity.this.getId();
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
				return new DollScreenHandler(syncId, inv, DollEntity.this.inventory);
			}
		};
		// 人偶已有主人且不是当前玩家时，拒绝打开菜单，防止他人拿走物品/抢控制权
		if (ownerUuid != null && !ownerUuid.equals(player.getUUID())) {
			// 诊断：用户实测"刚用蛋召唤的人偶就提示不是你的"——此处打印双方 UUID 定位归属为何失配
			DollMod.LOGGER.warn("[DollOwner] 打开菜单被拒: doll={} ownerUuid={} player={} uuid={}",
				this.getId(), ownerUuid, player.getName().getString(), player.getUUID());
			player.sendSystemMessage(Component.translatable("message." + DollModConstants.MOD_ID + ".not_your_doll"));
			return InteractionResult.FAIL;
		}
		// 打开菜单的玩家即视为主人
		setOwner(player);
		player.openMenu(provider);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level().isClientSide()) {
			return;
		}
		invalidateEggsInDollInventory();
		dropInventoryContents();
		invalidateBoundEggs(source);
		voiceDeath();
	}

	/**
	 * 实体被移除时的位置登记清理：
	 * 区块卸载（shouldSave=true）时人偶仍存在，保留登记供召回定位；
	 * 死亡/回收/换维度（shouldSave=false）时清除登记（换维度后下个 tick 会自动重新登记）。
	 */
	@Override
	public void remove(RemovalReason reason) {
		// 人偶移除即从光环登记表移除：受影响生物的 AI 自然恢复（未持久化 NoAi，无需手动恢复）
			removePaleAuraCenter(this.getUUID());
			removeNetherAuraCenter(this.getUUID());
		super.remove(reason);
				// 维度切换时（CHANGED_DIMENSION）旧实体被移除，旧坐标失效，
				// 故一并清除登记——新维度实体将在下一 tick 重登记。
				if (!reason.shouldSave() || reason == RemovalReason.CHANGED_DIMENSION) {
						DollRecallRegistry.remove(this.getUUID());
				}
	}

	/**
	 * 人偶死亡前，先遍历自身物品栏，把绑定到本实体的蛋置为失效。
	 * 绑定到其他人偶的蛋或未绑定蛋不受影响，正常随掉落逻辑处理。
	 */
	private void invalidateEggsInDollInventory() {
		for (int i = 0; i < DollInventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !(stack.getItem() instanceof DollSpawnEggItem)) {
				continue;
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null) {
				continue;
			}
			CompoundTag tag = data.copyTag();
			String eggUuid = tag.getStringOr(DollSpawnEggItem.DOLL_UUID_NBT_KEY, "");
			if (!eggUuid.isEmpty() && eggUuid.equals(this.getUUID().toString())) {
				tag.putBoolean(DollSpawnEggItem.INVALIDATED_NBT_KEY, true);
				tag.remove(DollSpawnEggItem.DOLL_UUID_NBT_KEY);
				tag.remove(DollSpawnEggItem.INVENTORY_NBT_KEY);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				stack.set(DataComponents.CUSTOM_NAME,
					Component.translatable("item." + DollModConstants.MOD_ID + ".doll_egg.invalidated"));
			}
		}
	}

	public void dropInventoryContents() {
		Level level = this.level();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		for (int i = 0; i < DollInventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemEntity itemEntity = new ItemEntity(serverLevel, getX(), getY() + 0.5, getZ(), stack.copy());
			itemEntity.setDefaultPickUpDelay();
			serverLevel.addFreshEntity(itemEntity);
			inventory.setItem(i, ItemStack.EMPTY);
		}
	}

	/**
	 * 人偶死亡后，把与之绑定的所有蛋置为失效（清除绑定、物品栏数据）。
	 * 优先失效主人背包（无论距离），再按击杀者/附近玩家兜底，避免蛋在
	 * 远离人偶的玩家手里时永久卡死（无法召唤也无法回收）。
	 */
	private void invalidateBoundEggs(DamageSource source) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// 主人背包优先：主人可能在 32 格外
		Player owner = getOwnerPlayer();
		if (owner != null) {
			invalidateEggsInInventory(owner.getInventory());
		}
		// 击杀者是玩家时，其背包中的蛋也失效（可能蛋在他手上）
		if (source.getDirectEntity() instanceof Player attacker) {
			if (attacker != owner) {
				invalidateEggsInInventory(attacker.getInventory());
			}
			return;
		}
		// 兜底：32 格内其他玩家（蛋可能被遗弃/转交给他人）
		AABB range = this.getBoundingBox().inflate(32.0);
		List<Player> players = serverLevel.getEntities(EntityTypeTest.forClass(Player.class), range, player -> !player.isSpectator());
		for (Player player : players) {
			invalidateEggsInInventory(player.getInventory());
		}
	}

	private void invalidateEggsInInventory(Inventory playerInventory) {
		for (int i = 0; i < playerInventory.getContainerSize(); i++) {
			ItemStack stack = playerInventory.getItem(i);
			if (stack.isEmpty() || !(stack.getItem() instanceof DollSpawnEggItem)) {
				continue;
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null) {
				continue;
			}
			CompoundTag tag = data.copyTag();
			String eggUuid = tag.getStringOr(DollSpawnEggItem.DOLL_UUID_NBT_KEY, "");
			if (!eggUuid.isEmpty() && eggUuid.equals(this.getUUID().toString())) {
				tag.putBoolean(DollSpawnEggItem.INVALIDATED_NBT_KEY, true);
				tag.remove(DollSpawnEggItem.DOLL_UUID_NBT_KEY);
				tag.remove(DollSpawnEggItem.INVENTORY_NBT_KEY);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				stack.set(DataComponents.CUSTOM_NAME,
					Component.translatable("item." + DollModConstants.MOD_ID + ".doll_egg.invalidated"));
			}
		}
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public void push(double x, double y, double z) {
		// 位移型推动：直接以碰撞位移方式移动，绕过速度/AI 覆盖，
		// 任何状态（跟随/战斗中）都能被推动；撞到方块或实体即停，不会穿墙。
		// vanilla 每 tick 仅 0.05，放大 4 倍获得明显手感。
		if (x == 0.0 && y == 0.0 && z == 0.0) {
			return;
		}
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return;
		}
		this.move(MoverType.SELF, new Vec3(x * 4.0, y * 4.0, z * 4.0));
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	/**
	 * 与普通生物一致：不阻挡玩家移动（返回 false）。
	 *
	 * 26.2 中 Entity.move() → collide() → Level.getEntityCollisions() 会把
	 * canBeCollidedWith=true 的实体包围盒当作实心 VoxelShape 挡路：玩家走到
	 * 人偶边缘就被硬挡住，永远无法与其包围盒重叠；而推进检测
	 * （LivingEntity.aiStep → pushEntities → getPushableEntities，用"推者自身
	 * 包围盒"查询）只有两实体真正重叠才会命中。两者叠加的结果就是：人偶变成
	 * 推不动的障碍物。原版生物（牛/僵尸等）均返回 false，靠 push(Entity) 系统
	 * 实现"可推开但不穿模"，本方法必须与其保持一致。
	 */
	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
		return EquipmentSlot.MAINHAND;
	}

	// ---- 手持前置工具渲染：按模式从物品栏查找对应工具，不再按格子号硬映射 ----

	/** 判断物品是否是镐子（26.2 无 PickaxeItem 类，用官方工具标签判定）。 */
	private boolean isPickaxe(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
	}

	/** 判断物品是否是钓鱼竿。 */
	private boolean isFishingRod(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof FishingRodItem;
	}

	/** 在人偶物品栏（全45格，含存储区与快捷栏）中查找近战武器，返回物品；未找到返回空。 */
	private ItemStack findMeleeWeaponStack() {
		int slot = findMeleeWeaponInHotbar();
		return slot == -1 ? ItemStack.EMPTY : inventory.getItem(slot);
	}

	/** 在人偶物品栏（全45格）中查找弓/弩，返回物品；未找到返回空。
	 * 末影斧例外：忠诚附魔回归时可能落入背包任何格子，因此末影人偶查找末影斧时搜索全背包。
	 */
	private ItemStack findRangedWeaponStack() {
		// 末影人偶优先在全背包查找末影斧作为远程武器（投掷形式）
		if (isEnderDoll()) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && stack.getItem() instanceof EnderAxeItem) {
					return stack;
				}
			}
		}
		// 下界人偶在全背包查找地狱剑作为远程武器（投掷形式）
		if (isNetherDoll()) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && stack.getItem() instanceof NetherSwordItem) {
					return stack;
				}
			}
		}
		int slot = findRangedWeaponInHotbar();
		return slot == -1 ? ItemStack.EMPTY : inventory.getItem(slot);
	}

	/** 查找斧头：先快捷栏（36-43），快捷栏没有再到存储区/其余格兜底；未找到返回空。
	 *  斧头耗尽时按此顺序拿下一把可用斧头（存储区备件也能立即接上）。 */
	private ItemStack findAxeStack() {
		for (int i = DollMode.HOTBAR_SLOT_START; i < DollMode.HOTBAR_SLOT_START + 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
				return stack;
			}
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (i >= DollMode.HOTBAR_SLOT_START && i < DollMode.HOTBAR_SLOT_START + 9) {
				continue; // 快捷栏已查过
			}
			
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 快捷栏（36-43）查找锄头，返回物品；未找到返回空。 */
	private ItemStack findHoeStack() {
		for (int i = DollMode.HOTBAR_SLOT_START; i < DollMode.HOTBAR_SLOT_START + 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() instanceof HoeItem) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 查找镐子：先快捷栏（36-43），快捷栏没有再到存储区/其余格兜底；未找到返回空。
	 *  镐子耗尽时按此顺序拿下一把可用镐子（存储区备件也能立即接上）。 */
	private ItemStack findPickaxeStack() {
		for (int i = DollMode.HOTBAR_SLOT_START; i < DollMode.HOTBAR_SLOT_START + 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (isPickaxe(stack)) {
				return stack;
			}
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (i >= DollMode.HOTBAR_SLOT_START && i < DollMode.HOTBAR_SLOT_START + 9) {
				continue; // 快捷栏已查过
			}
			
			ItemStack stack = inventory.getItem(i);
			if (isPickaxe(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 全背包最强镐（Tier 等级最高）：用于"能不能挖"的能力判定。
	 * 修掉的原缺陷：石镐在槽位靠前、钻石镐在后时，findPickaxeStack 只返回第一把，
	 * 能力判定就会误以为挖不动钻石矿而绕开它。这里看最强镐，高矿才会进候选。
	 */
	private ItemStack findBestPickaxeStack() {
		ItemStack best = ItemStack.EMPTY;
		int bestLevel = -1;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			
			ItemStack stack = inventory.getItem(i);
			if (!isPickaxe(stack)) {
				continue;
			}
			int level = pickaxeTierLevel(stack);
			if (level > bestLevel) {
				bestLevel = level;
				best = stack;
			}
		}
		return best;
	}

	/**
	 * 给指定方块挑"刚好够用的最低级镐"作为实际挖掘工具：
	 * 在 isCorrectToolForDrops(state) 为 true 的镐里取 Tier 等级最低的一把，
	 * 把钻石/下界合金镐留给真正需要它们的矿，省耐久。
	 * 仅在 canPickaxeMine(state) 已为真（至少有一把够用）时调用才有意义；
	 * 若意外无够用的镐（防御性分支）返回空，调用方应放弃该块。
	 */
	private ItemStack findPickaxeForState(BlockState state) {
		ItemStack best = ItemStack.EMPTY;
		int bestLevel = Integer.MAX_VALUE;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			
			ItemStack stack = inventory.getItem(i);
			if (!isPickaxe(stack)) {
				continue;
			}
			if (!stack.isCorrectToolForDrops(state)) {
				continue;
			}
			int level = pickaxeTierLevel(stack);
			if (level < bestLevel) {
				bestLevel = level;
				best = stack;
			}
		}
		return best;
	}

	/**
	 * 镐子分级（不依赖内部 Tier 类，跨版本更稳）：用"能否正确采掘对应层级代表方块"定级。
	 * 参考方块取自确认的工具等级标签：铁矿石(NEEDS_STONE_TOOL)=石级、钻石矿(NEEDS_IRON_TOOL)=铁级、
	 * 黑曜石(NEEDS_DIAMOND_TOOL)=钻石级。下界合金镐挖掘能力与钻石镐完全相同、但耐久更贵，单独记最高级，
	 * 保证"刚好够用的最低级镐"永远优先选钻石镐而非下界合金镐。木/金镐连铁矿石都挖不动 → 记 0。
	 * 注意：实际采掘正确性由 isCorrectToolForDrops 保证，这里分级只用于"在够用的镐里挑最弱的一把"排序，
	 * 即便分级有偏差也绝不会挑到不够用的镐（不够用的已被 isCorrectToolForDrops 过滤）。
	 */
	private int pickaxeTierLevel(ItemStack stack) {
		if (!isPickaxe(stack)) {
			return -1;
		}
		if (stack.is(Items.NETHERITE_PICKAXE)) {
			return 4; // 与钻石同挖掘能力、耐久更贵，排最末
		}
		if (stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) {
			return 3; // 钻石级：黑曜石
		}
		if (stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) {
			return 2; // 铁级：钻石矿
		}
		if (stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) {
			return 1; // 石级：铁矿石
		}
		return 0; // 木/金级：连铁矿石都挖不动
	}

	/** 人偶物品栏（全45格）查找钓鱼竿，返回物品；未找到返回空。 */
	private ItemStack findFishingRodStack() {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isFishingRod(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 按当前模式查找对应的手持前置工具，只渲染该模式真正需要的工具，
	 * 避免出现"挖矿模式拿小麦种子"这类错位渲染。
	 * 近战/射手/砍树/挖矿/钓鱼从快捷栏查武器工具；种植查锄头（没有则空手，仍可播种收割）；
	 * 喂食复用全物品栏的食物查找；插火把复用全物品栏的火把查找。
	 */
	private ItemStack findToolForMode(int mode) {
		if (mode == DollMode.MELEE.getIndex()) {
			return findMeleeWeaponStack();
		}
		if (mode == DollMode.RANGED.getIndex()) {
			return findRangedWeaponStack();
		}
		if (mode == DollMode.FARM.getIndex()) {
			return findHoeStack();
		}
		if (mode == DollMode.FEED.getIndex()) {
			return findPositiveFoodInInventory();
		}
		if (mode == DollMode.CHOP.getIndex()) {
			return findAxeStack();
		}
		if (mode == DollMode.MINE.getIndex()) {
			return findPickaxeStack();
		}
		if (mode == DollMode.TORCH.getIndex()) {
			return findTorchInInventory();
		}
		if (mode == DollMode.FISH.getIndex()) {
			return findFishingRodStack();
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 让人偶的装备从"人偶物品栏"读取，而不是原版空装备栏。
	 * stabAttack / 附魔 / 护甲减伤 / 装备渲染都会走本方法。
	 * 护甲槽（1-4）参与减伤与渲染；
	 * 主手：按当前模式从快捷栏查找对应前置工具（近战=剑、射手=弓/弩、
	 * 砍树=斧头、挖矿=镐子、种植=锄头、钓鱼=钓鱼竿、插火把=火把），
	 * 只渲染该模式真正需要的工具；
	 * 无模式（-1/空闲）时无手持物品（空手）。
	 *
	 * 客户端例外：DollInventory 在未打开 GUI 前为空（仅靠 ScreenHandler 同步填充），
	 * 主手物品由服务端装备同步包（ClientboundSetEquipmentPacket）直接写入 vanilla 装备槽。
	 * 因此客户端主手必须走 super.getItemBySlot 读取同步值，否则重进游戏后人偶手里没武器。
	 */
	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		if (this.level().isClientSide() && slot == EquipmentSlot.MAINHAND) {
			return super.getItemBySlot(slot);
		}
		return switch (slot) {
			case MAINHAND -> {
				// 进食中：主手临时显示食物（服务端装备同步会把该物品发给客户端，客户端走 super 分支拿到并渲染）
				if (eatSourceSlot != -1) {
					yield inventory.getItem(eatSourceSlot);
				}
				// 播种挥动中：主手临时显示种子（否则仍渲染锄头，视觉与动作不符）
				if (plantSeedHandTicks > 0 && plantSourceSlot != -1) {
					yield inventory.getItem(plantSourceSlot);
				}
				yield findToolForMode(getActiveMode());
			}
			case OFFHAND -> inventory.getItem(OFFHAND_SLOT);
			case HEAD -> inventory.getItem(EQUIP_HEAD_SLOT);
			case CHEST -> inventory.getItem(EQUIP_CHEST_SLOT);
			case LEGS -> inventory.getItem(EQUIP_LEGS_SLOT);
			case FEET -> inventory.getItem(EQUIP_FEET_SLOT);
			default -> super.getItemBySlot(slot);
		};
	}

	/**
	 * 覆写 setItemSlot：客户端收到装备同步包时写入 DollInventory，
	 * 与 getItemBySlot 的读取来源保持一致，否则回收再召唤后盔甲不渲染。
	 */
	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		switch (slot) {
			case OFFHAND -> inventory.setItem(OFFHAND_SLOT, stack);
			case HEAD -> inventory.setItem(EQUIP_HEAD_SLOT, stack);
			case CHEST -> inventory.setItem(EQUIP_CHEST_SLOT, stack);
			case LEGS -> inventory.setItem(EQUIP_LEGS_SLOT, stack);
			case FEET -> inventory.setItem(EQUIP_FEET_SLOT, stack);
			default -> super.setItemSlot(slot, stack);
		}
	}

	/**
	 * 人偶固定显示皮肤全部外层（hat/jacket/sleeves/pants）。
	 * Avatar 父类将 DATA_PLAYER_MODE_CUSTOMISATION 初始化为 0（全关），
	 * AvatarRenderer.extractRenderState 通过此方法决定各层可见性；
	 * 不覆写则所有外层均不渲染。
	 */
	@Override
	public boolean isModelPartShown(PlayerModelPart part) {
		return true;
	}

	/**
	 * 海洋人偶水下呼吸：覆写返回 true，使其在水下不会溺水。
	 */
	@Override
	public boolean canBreatheUnderwater() {
		return isSeaDoll();
	}

	// ---- 基础状态访问器 ----

	public int getActiveMode() {
		return getEntityData().get(DATA_ACTIVE_MODE);
	}

	private void setActiveMode(int mode) {
		getEntityData().set(DATA_ACTIVE_MODE, mode);
	}

	/**
	 * 运行时模式被取消（前置工具消失等）时，向主人推送快照，
	 * 同步控制面板的模式按钮高亮边框（DollControlScreen 靠快照渲染，不读 SynchedEntityData）。
	 */
	private void pushSnapshotToOwner() {
		Player owner = getOwnerPlayer();
		if (!(owner instanceof ServerPlayer serverPlayer)) return;
		String name = getCustomName() != null ? getCustomName().getString() : "人偶";
		boolean sameDim = serverPlayer.level() == this.level();
		String dimName = this.level().dimension().identifier().getPath();
		BlockPos dp = blockPosition();
		int distSqr = sameDim ? (int) distanceToSqr(serverPlayer) : Integer.MAX_VALUE;
		DollSnapshot snap = new DollSnapshot(
			getId(), getUUID().toString(), name, getDollLevel(), getActiveMode(),
			isFollowEnabled(), isTunneling(),
			sameDim, distSqr, dimName,
			dp.getX(), dp.getY(), dp.getZ());
		ServerPlayNetworking.send(serverPlayer, new UpdateDollSnapshotPayload(snap));
	}

	public boolean isFollowEnabled() {
		return getEntityData().get(DATA_FOLLOW_ENABLED);
	}

	public int getDollLevel() {
		return getEntityData().get(DATA_DOLL_LEVEL);
	}

	public boolean isWardenDoll() {
		return getDollVariant() == DollVariant.WARDEN;
	}

	/** 是否为下界人偶变体。 */
	public boolean isNetherDoll() {
		return getDollVariant() == DollVariant.NETHER;
	}

	/**
	 * 是否手持地狱剑（主手或副手）——下界人偶专属增益开关：
	 * 持剑时其全部灼烧效果（近战点燃/火焰箭/烈焰弹燃烧）时长翻倍，
	 * 总灼烧伤害变为原本的两倍（MC 灼烧伤害固定 1 心/秒）。
	 */
	public boolean hasNetherSwordEquipped() {
		return getItemBySlot(EquipmentSlot.MAINHAND).is(DollMod.NETHER_SWORD_ITEM)
			|| getItemBySlot(EquipmentSlot.OFFHAND).is(DollMod.NETHER_SWORD_ITEM);
	}

	/** 是否为末影人偶变体。 */
	public boolean isEnderDoll() {
		return getDollVariant() == DollVariant.ENDER;
	}

	/** 是否为海洋人偶变体。 */
	public boolean isSeaDoll() {
		return getDollVariant() == DollVariant.SEA;
	}

	/**
	 * 是否具备天生战斗能力（无需武器即可攻击）：
	 * 幽匿（音波）/下界（烈焰弹）/末影（龙息）/海洋（三叉戟）。
	 * 这些变体在切换到近战或射手模式时跳过武器持有检查。
	 */
	public boolean hasInnateCombatAbility() {
		return isWardenDoll() || isNetherDoll() || isEnderDoll() || isSeaDoll();
	}

	/** 是否为森林人偶变体。 */
	public boolean isForestDoll() {
		return getDollVariant() == DollVariant.FOREST;
	}

	/**
	 * 指挥棒指定强制攻击目标。传入 null 清除强制目标，回退到正常 AI 搜寻。
	 * 强制目标不受搜索半径和追击距离限制——玩家明确指定了"打谁"，人偶会持续追击。
	 */
	public void setForcedTargetUuid(UUID uuid) {
		this.forcedTargetUuid = uuid;
	}

	/**
	 * 解析指挥棒指定的强制目标。目标死亡/移除/不在同维度时返回 null 并清除 UUID。
	 */
	private LivingEntity resolveForcedTarget() {
		if (forcedTargetUuid == null) return null;
		if (!(this.level() instanceof ServerLevel serverLevel)) return null;
		Entity entity = serverLevel.getEntity(forcedTargetUuid);
		if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
			&& living.level() == this.level()) {
			return living;
		}
		// 强制目标已失效
		forcedTargetUuid = null;
		return null;
	}

	/** 当前是否有指挥棒指定的强制攻击目标。 */
	public boolean hasForcedTarget() {
		return forcedTargetUuid != null;
	}

	public DollVariant getDollVariant() {
		return DollVariant.byOrdinal(getEntityData().get(DATA_DOLL_VARIANT));
	}

	/**
	 * 获取人偶光环的中心位置。
	 * 跟随时为玩家位置，不跟随时为人偶自身位置。供 Mixin 外部调用。
	 */
	public Vec3 getAuraCenter() {
		if (isFollowEnabled()) {
			Player owner = getOwnerPlayer();
			if (owner != null) return owner.position();
		}
		return this.position();
	}

	/** NBT 版本号：未来字段语义变更时按版本分支迁移（当前 v1）。 */
	private static final String NBT_VERSION_KEY = "NbtVersion";
	private static final int NBT_VERSION = 1;

	/**
	 * 变体 + 等级 → 最大血量映射。原先在 readAdditionalSaveData / setDollVariant /
	 * setDollLevel 三处独立实现，改平衡易漏改一处，统一于此。
	 */
	private static double computeMaxHealth(DollVariant variant, int level) {
		if (variant == DollVariant.WARDEN) return 200.0;
		if (variant == DollVariant.PALE || variant == DollVariant.NETHER
				|| variant == DollVariant.ENDER || variant == DollVariant.SEA
				|| variant == DollVariant.FOREST || variant == DollVariant.GUIDE) {
			return 120.0;
		}
		return switch (level) {
			case 5 -> 100.0;
			case 4 -> 80.0;
			case 3 -> 60.0;
			case 2 -> 40.0;
			case 1 -> 20.0;
			default -> 20.0;
		};
	}

	public void setDollVariant(DollVariant variant) {
		getEntityData().set(DATA_DOLL_VARIANT, variant.ordinal());
		getEntityData().set(DATA_IS_WARDEN_VARIANT, variant == DollVariant.WARDEN);
		// 变体设置后调整血量上限与击退免疫（setDollLevel 先于 setDollVariant 调用，
		// 此时 variant 才从 NONE 变为具体变体，需要补设 maxHp）。
		// 普通/NONE 不进入此分支，保持 setDollLevel 的阶阶梯。
		AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance knockbackRes = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		double maxHp = computeMaxHealth(variant, getDollLevel());
		if (maxHealth != null) maxHealth.setBaseValue(maxHp);
		if (knockbackRes != null) knockbackRes.setBaseValue(variant == DollVariant.WARDEN ? 1.0 : 0.0);
		setHealth((float) maxHp);
		// 海洋人偶专属：允许水中寻路（仅 SEA 生效，其他人偶 allowWater 保持 false）
		navigator.setAllowWater(isSeaDoll());
	}

	public DollInventory getInventoryBag() {
		return this.inventory;
	}

	/** 是否已划定作业区（两个对角均非 null）。 */
	public boolean hasWorkArea() {
		return workAreaMin != null && workAreaMax != null;
	}

	public BlockPos getWorkAreaMin() {
		return workAreaMin;
	}

	public BlockPos getWorkAreaMax() {
		return workAreaMax;
	}

	/** 设定作业区（自动归一化为 min/max 两个角）。 */
	public void setWorkArea(BlockPos a, BlockPos b) {
		this.workAreaMin = new BlockPos(
			Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		this.workAreaMax = new BlockPos(
			Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		this.workAreaNavTarget = null; // 区域变更后重置前往目标
	}

	/** 清除作业区（死亡/主动清除时调用）。 */
	public void clearWorkArea() {
		this.workAreaMin = null;
		this.workAreaMax = null;
		this.workAreaNavTarget = null;
	}

	/**
	 * 人偶脚底是否在作业区 XZ 范围内。
	 */
	private boolean isWithinWorkAreaXZ() {
		return withinWorkAreaXZ(blockPosition());
	}

	/**
	 * 有作业区但人偶不在其 XZ 范围内时，导航前往作业区中心再干活。
	 * 解决"划了区域但人偶原地发呆"：劳作搜索以人偶自身为中心，区域远了够不到目标。
	 */
	private void navigateToWorkArea() {
		if (!hasWorkArea()) {
			workAreaNavTarget = null;
			clearMovementInput();
			return;
		}
		BlockPos center = new BlockPos(
			(workAreaMin.getX() + workAreaMax.getX()) / 2,
			blockPosition().getY(),
			(workAreaMin.getZ() + workAreaMax.getZ()) / 2);
		Vec3 target = Vec3.atCenterOf(center);
		boolean recalc = workAreaNavTarget == null
			|| target.distanceToSqr(workAreaNavTarget) > 1.0
			|| navigator.isPathDone();
		if (recalc) {
			workAreaNavTarget = target;
			if (!navigator.computePath(target)) {
				// 目标（区域中心）可能是实心方块/不可达：回退直线走向目标，走到哪算哪，不卡死
				workAreaNavTarget = null;
				straightToward(target);
				return;
			}
		}
		Vec3 node = navigator.advance();
		if (node == null) {
			workAreaNavTarget = null;
			clearMovementInput();
			return;
		}
		smoothFaceTowards(node.x, node.z);
		this.xxa = 0.0f;
		this.zza = 1.0f;
		setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * CHOP_MOVE_SPEED_FACTOR);
		this.setSprinting(true);
	}

	/**
	 * 方块位置是否在作业区内。
	 * 仅劳作类模式（砍树/种植）消费；攻击/插火把/喂食/钓鱼不受作业区约束；
	 * 未设置作业区时恒为 true（不限制）。
	 */
	public boolean withinWorkArea(BlockPos pos) {
		if (!hasWorkArea()) {
			return true;
		}
		return pos.getX() >= workAreaMin.getX() && pos.getX() <= workAreaMax.getX()
			&& pos.getY() >= workAreaMin.getY() && pos.getY() <= workAreaMax.getY()
			&& pos.getZ() >= workAreaMin.getZ() && pos.getZ() <= workAreaMax.getZ();
	}

	/**
	 * 仅按 XZ 平面判定作业区（Y 不参与）。
	 * 劳作目标（树根原木/作物/耕地）通常在地面点击高度上方一格，
	 * 玩家在地面点选两点划区时 Y 只覆盖地面层，3D 判定会把目标全部挡在区域外。
	 * 作业区的正确心智模型是"水平责任田"：XZ 在区域内即算责任田，高度不限。
	 */
	public boolean withinWorkAreaXZ(BlockPos pos) {
		if (!hasWorkArea()) {
			return true;
		}
		return pos.getX() >= workAreaMin.getX() && pos.getX() <= workAreaMax.getX()
			&& pos.getZ() >= workAreaMin.getZ() && pos.getZ() <= workAreaMax.getZ();
	}

	// ------------------------------------------------------------------
	// 盾构机（MINE 模式隧道掘进）
	// ------------------------------------------------------------------

	/** 是否已配置盾构机（方向 + 入口均非 null）。 */
	public boolean hasTunnelConfig() {
		return tunnelDir != null && tunnelEntry != null;
	}

	/** 是否正在掘进（停止条件触发后为 false，指挥棒右键人偶恢复）。 */
	public boolean isTunneling() {
		return tunneling;
	}

	public void setTunneling(boolean tunneling) {
		this.tunneling = tunneling;
	}

	public Direction getTunnelDir() {
		return tunnelDir;
	}

	public BlockPos getTunnelEntry() {
		return tunnelEntry;
	}

	/**
	 * 人偶是否仍对齐盾构机隧道（"右键人偶恢复掘进"的合法性校验）。
	 * 恢复掘进要求人偶确实站在隧道里：当前脚底高度与入口下层方块（tunnelY）一致。
	 * 人偶停止掘进后若开了跟随被带回玩家身边，或被人推动/掉落，高度必然偏离——
	 * 此时从当前位置恢复会凭空向错误高度开掘并把自身传送进地下，表现为"人偶消失"。
	 */
	public boolean isAlignedWithTunnel() {
		return hasTunnelConfig() && Math.abs(blockPosition().getY() - tunnelEntry.getY()) <= 1;
	}

	/**
	 * 配置盾构机：入口截面下层方块 + 掘进方向（水平）。
	 * 人偶立即传送到入口前方地面开始掘进（指挥棒设入口时使用）。
	 */
	public void setTunnelConfig(BlockPos entry, Direction dir) {
		setTunnelConfig(entry, dir, true);
	}

	/**
	 * 配置盾构机（可选传送）。
	 * <p>{@code teleportToEntry=false} 用于"回收再召唤"恢复配置：人偶应留在玩家
	 * 点击的召唤位置，绝不能瞬移回旧隧道口（否则玩家看到人偶召唤出来的一瞬间
	 * 又凭空消失，且旧隧道若在未加载区块还会被丢到高空/虚空）。
	 */
	public void setTunnelConfig(BlockPos entry, Direction dir, boolean teleportToEntry) {
		this.tunnelEntry = entry;
		this.tunnelDir = dir;
		this.tunneling = true;
		this.tunnelActionCooldown = 0;
		if (teleportToEntry && this.level() instanceof ServerLevel serverLevel) {
			// 入口竖方块（墙下层）的 Y 即隧道地面高度，人偶站入口前方同一高度；
			// 若该位置被实心挡住（点选的方块偏高/地形异常），向上找第一个可站立点，防遁地/卡进方块；
			// 找不到安全点（如入口在未加载区块/悬崖边）则**不传送**，避免被扔到高空/虚空
			BlockPos stand = entry.offset(-dir.getStepX(), 0, -dir.getStepZ());
			int standY = stand.getY();
			boolean found = false;
			while (standY < serverLevel.getMaxY() - 1 && !isStandableSpot(serverLevel, stand.getX(), standY, stand.getZ())) {
				standY++;
			}
			if (isStandableSpot(serverLevel, stand.getX(), standY, stand.getZ())) {
				found = true;
			}
			if (found) {
				this.setPos((double) stand.getX() + 0.5, (double) standY, (double) stand.getZ() + 0.5);
				// 朝掘进方向看
				this.setYRot(dir.toYRot());
				this.setYHeadRot(dir.toYRot());
			}
		}
	}

	/** 位置可站立：脚下实心 + 自身两格空气（不卡头）。 */
	private static boolean isStandableSpot(ServerLevel level, int x, int y, int z) {
		BlockPos groundPos = new BlockPos(x, y - 1, z);
		return !level.getBlockState(groundPos).getCollisionShape(level, groundPos).isEmpty()
			&& level.isEmptyBlock(new BlockPos(x, y, z))
			&& level.isEmptyBlock(new BlockPos(x, y + 1, z));
	}

	/**
	 * 从 pos 起向下探测第一个实心支撑所需的落差格数：pos 本身实心则返回 0。
	 * 探测超过 maxProbe 格仍未找到支撑返回 Integer.MAX_VALUE，视为深坑 / 虚空。
	 */
	private static int measureDropBelow(ServerLevel level, BlockPos pos, int maxProbe) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < maxProbe; i++) {
			BlockState state = level.getBlockState(cursor);
			if (!state.getCollisionShape(level, cursor).isEmpty()) {
				return i;
			}
			cursor.move(Direction.DOWN);
		}
		return Integer.MAX_VALUE;
	}

	/** 清除盾构机配置（潜行右键人偶时与作业区一并清除）。 */
	public void clearTunnelConfig() {
		this.tunnelDir = null;
		this.tunnelEntry = null;
		this.tunneling = false;
	}

	/**
	 * 盾构机掘进：沿配置方向持续挖 1×2 隧道，不预设终点。
	 * 每个冷却周期：检查 5 个停止条件 → 挖前方两格 → 前进一格。
	 * 停止时向主人广播「人偶名 + 原因」。
	 */
	private void updateTunnelDrill() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!tunneling) {
			clearMovementInput();
			return;
		}
		// 始终保持面朝掘进方向（恢复掘进/传送后朝向可能丢失，避免"倒着走"）
		this.setYRot(tunnelDir.toYRot());
		this.setYHeadRot(tunnelDir.toYRot());
		tunnelActionCooldown = Math.max(0, tunnelActionCooldown - 1);
		if (tunnelActionCooldown > 0) {
			return;
		}

		BlockPos here = blockPosition();
		BlockPos ahead = here.offset(tunnelDir.getStepX(), 0, tunnelDir.getStepZ());
		int tunnelY = tunnelEntry.getY();
		// 前方要挖的两格（隧道 1 宽 × 2 高）
		BlockPos dig1 = new BlockPos(ahead.getX(), tunnelY, ahead.getZ());
		BlockPos dig2 = new BlockPos(ahead.getX(), tunnelY + 1, ahead.getZ());

		// 停止条件 1：前方是悬崖。允许最多 MAX_SAFE_FALL_BLOCKS 的落差（与 DollNavigator
		// 下坡能力同一口径），只有下方是真·深坑才判悬崖。
		// 早期实现把判定写反（脚下有支撑反而停），平地必然秒停；修正后又变成"差一格就停"，
		// 山区常见的小空腔/矿洞顶会让盾构机频繁误停，所以这里给一个可容忍的落差。
		BlockPos aheadFloor = new BlockPos(ahead.getX(), tunnelY - 1, ahead.getZ());
		if (measureDropBelow(serverLevel, aheadFloor, DollNavigator.MAX_SAFE_FALL_BLOCKS + 1)
			> DollNavigator.MAX_SAFE_FALL_BLOCKS) {
			stopTunneling("mine_stop_cliff");
			return;
		}
		// 停止条件 2：前方两格本身或周围 ±2 格有岩浆
		if (isLavaNear(serverLevel, dig1) || isLavaNear(serverLevel, dig2)) {
			stopTunneling("mine_stop_lava");
			return;
		}
		// 停止条件 3：前方是重力方块（沙砾/沙子）
		if (isFallingBlock(serverLevel, dig1) || isFallingBlock(serverLevel, dig2)) {
			stopTunneling("mine_stop_gravity");
			return;
		}
		// 停止条件 4：前方两格有水（盾构机不游泳，遇水停下避免窒息）
		if (isWaterBlock(serverLevel, dig1) || isWaterBlock(serverLevel, dig2)) {
			stopTunneling("mine_stop_water");
			return;
		}
		// 停止条件 5：前方是需要分级工具、但当前镐挖不动的方块（钻石矿/黑曜石等）。
		// 统一走 canPickaxeMine——旧写法额外要求 ds.is(MINEABLE_WITH_PICKAXE)，
		// 会把"需要分级工具但不在该 tag 内"的方块漏过去，进而被后面的 tunnelMineBlock
		// 当普通路障空手挖掉（方块消失、零掉落）。
		// 是否有"任意一把镐"用于开挖前预检（耗尽即停）。实际每格用哪把镐在下面按等级挑选。
		ItemStack pickaxe = findBestPickaxeStack();
		for (BlockPos dig : new BlockPos[] { dig1, dig2 }) {
			BlockState ds = serverLevel.getBlockState(dig);
			if (requiresTieredTool(ds) && !canPickaxeMine(ds)) {
				stopTunneling("mine_stop_unbreakable");
				return;
			}
		}
		// 停止条件 6：镐子用完了。必须在开挖前就停——深板岩这类 requiresCorrectToolForDrops
		// 的路障一旦无镐开挖就是"方块消失但零掉落"的静默损失，比停下来更糟。
		if (pickaxe.isEmpty()) {
			stopTunneling("mine_stop_no_pickaxe");
			return;
		}
		// 停止条件 7：存储区无空格（掉落物会掉地堆积）→ 停止，等玩家清包
		if (!hasStorageSpace()) {
			stopTunneling("mine_stop_backpack_full");
			return;
		}

		// 挖前方两格（掉落进背包），前进一格；
		// 路障空手挖不耗镐耐久，需要分级工具的方块（矿石/黑曜石）才用镐。
		// 前方挖到矿石时连锁采集同族矿脉（继承普通挖矿模式的连锁能力）
		for (BlockPos dig : new BlockPos[] { dig1, dig2 }) {
			BlockState digState = serverLevel.getBlockState(dig);
			if (digState.isAir()) {
				continue;
			}
			// 基岩挖不动：单独报原因。旧实现在这里 continue 跳过，结果被下面"挖不通"
			// 的兜底兜住，玩家收到的是「前方无法挖通」，看不出是撞到基岩层了。
			if (digState.is(Blocks.BEDROCK)) {
				stopTunneling("mine_stop_bedrock");
				return;
			}
			// 必须在挖之前判定是不是矿石：tunnelMineBlock 一执行这里就变空气，
			// 旧实现把 isOreBlock 写在挖之后，导致连锁矿脉一次都没触发过。
			boolean wasOre = isOreBlock(dig);
			// 每块按"刚好够用的最低级镐"挖（石镐够就别动钻石镐），省耐久
			tunnelMineBlock(dig, findPickaxeForState(digState));
			if (wasOre) {
				chainMineOres(dig, getOreFamilyBlock(digState.getBlock()));
			}
			// 每挖完一格立即检查背包：无空格马上停（不再挖下一格/探矿，避免掉落物掉地铺地）
			if (!hasStorageSpace()) {
				stopTunneling("mine_stop_backpack_full");
				return;
			}
		}
		// 挖完后验证实际开挖的隧道两格（dig1/dig2，固定 tunnelY 高度）是否已挖通（空气），
		// 否则停止——防止几何异常/液体残留导致人偶被传送进实心方块或水中窒息。
		// 注意必须校验 dig1/dig2 而非"人偶当前高度"的 ahead：人偶掉进洞穴/被推动后
		// 高度可能偏离 tunnelY，校验从未开挖的方块会触发无端的"前方挖不通"。
		if (!serverLevel.getBlockState(dig1).isAir() || !serverLevel.getBlockState(dig2).isAir()) {
			stopTunneling("mine_stop_blocked");
			return;
		}
		// 前进并把落脚点锚定到隧道列（tunnelY 固定高度），防止人偶高度漂移
		// （掉进洞穴/被玩家推动/地面沉降）后，下一周期从错误高度继续掘进或误判停止
		this.setPos((double) dig1.getX() + 0.5, (double) tunnelY, (double) dig1.getZ() + 0.5);
		tunnelActionCooldown = TUNNEL_ACTION_COOLDOWN;
		// 每前进一格：检测自身周围遗漏的矿石，有则优先采掉（连锁），再继续掘进
		BlockPos ore = scanNearbyOre(serverLevel);
		// 侧向探矿同样要过镐等级校验：否则木镐也会把身边扫到的钻石矿"拆掉但无掉落"，
		// 表现为「低等级镐挖掉高等级矿」，白白毁掉矿脉
		if (ore != null && canReachBlockPos(ore, MINE_REACH_SQR)
			&& canPickaxeMine(serverLevel.getBlockState(ore))) {
			BlockState oreState = serverLevel.getBlockState(ore);
			tunnelMineBlock(ore, findPickaxeForState(oreState));
			chainMineOres(ore, getOreFamilyBlock(oreState.getBlock()));
		}
	}

	/** 扫描人偶周围（XZ ±3、Y -2~+3）最近的矿石，用于盾构机侧向探矿。 */
	private BlockPos scanNearbyOre(ServerLevel level) {
		BlockPos center = blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int y = center.getY() - 2; y <= center.getY() + 3; y++) {
			for (int x = center.getX() - 3; x <= center.getX() + 3; x++) {
				for (int z = center.getZ() - 3; z <= center.getZ() + 3; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!isOreBlock(p)) {
						continue;
					}
					double d = p.distSqr(center);
					if (d < bestDist) {
						best = p;
						bestDist = d;
					}
				}
			}
		}
		return best;
	}

	/**
	 * 盾构机专用挖掘：路障（石头/圆石/深板岩/泥土等无 NEEDS_* 标签的方块）
	 * 空手挖（有掉落、不耗镐耐久）；只有需要分级工具的方块（矿石/黑曜石等）
	 * 才用镐挖并正常耗耐久。大幅降低盾构机对镐耐久的依赖。
	 */
	private void tunnelMineBlock(BlockPos pos, ItemStack pickaxe) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockState state = level().getBlockState(pos);
		boolean needsPick = requiresTieredTool(state);
		ItemStack tool = needsPick ? pickaxe : ItemStack.EMPTY;
		LootParams.Builder builder = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withParameter(LootContextParams.TOOL, tool)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, this)
			.withOptionalParameter(LootContextParams.BLOCK_STATE, state);
		List<ItemStack> drops = state.getDrops(builder);
		level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level().levelEvent(2001, pos, Block.getId(state));
		this.playSound(state.getSoundType().getBreakSound(), 1.0f, 1.0f);
		this.swing(InteractionHand.MAIN_HAND);
		for (ItemStack drop : drops) {
			addToDollInventory(drop);
		}
		if (needsPick && !pickaxe.isEmpty()) {
			pickaxe.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
		}
	}

	/** 停止掘进并向主人广播「人偶名 + 原因」+ 音符盒警报。 */
	private void stopTunneling(String reasonKey) {
		this.tunneling = false;
		Player owner = getOwnerPlayer();
		if (owner != null) {
			owner.sendSystemMessage(Component.translatable(
				"message." + DollModConstants.MOD_ID + "." + reasonKey,
				dollDisplayName()));
		}
		// 停止掘进 = 失败，统一播噔↑噔↓
		voiceNoTool();
	}

	/** 人偶显示名：有自定义名用自定义名，否则用实体类型翻译键兜底（i18n）。 */
	private Component dollDisplayName() {
		net.minecraft.network.chat.MutableComponent base = getCustomName() != null ? getCustomName().copy()
			: Component.translatable("entity." + DollModConstants.MOD_ID + ".doll");
		return base.withStyle(net.minecraft.ChatFormatting.YELLOW);
	}

	/** 方块是否需要分级工具（NEEDS_STONE/IRON/DIAMOND_TOOL）。 */
	private static boolean requiresTieredTool(BlockState state) {
		return state.is(BlockTags.NEEDS_STONE_TOOL)
			|| state.is(BlockTags.NEEDS_IRON_TOOL)
			|| state.is(BlockTags.NEEDS_DIAMOND_TOOL);
	}

	/** 前方两格本身或周围 ±2 格内是否有岩浆流体。 */
	private static boolean isLavaNear(ServerLevel level, BlockPos dig) {
		if (level.getFluidState(dig).is(FluidTags.LAVA)) {
			return true; // dig 自身是岩浆
		}
		int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
		for (int[] d : dirs) {
			for (int s = 1; s <= TUNNEL_LAVA_SCAN_RANGE; s++) {
				if (level.getFluidState(dig.offset(d[0] * s, d[1] * s, d[2] * s)).is(FluidTags.LAVA)) {
					return true;
				}
			}
		}
		return false;
	}

	/** 该位置是否为水（含水流）。 */
	private static boolean isWaterBlock(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}

	/** 是否为重力方块（沙砾/沙子/红沙）。 */
	private static boolean isFallingBlock(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.GRAVEL) || state.is(BlockTags.SAND);
	}

	/**
	 * 存储区是否还有"可容纳掉落物"的空间：有空格，或存在未满栈的槽位（同类可堆叠）。
	 * 注意：空格被一个物品占用后该格即变为"未满栈"，同类后续还能堆到满栈——
	 * 所以只认空格会过早判定"满了"（如 26 格满栈+1 格空，挖 1 个圆石进空格变成 1/64，
	 * 后面明明还能堆 63 个圆石）。
	 */
	private boolean hasStorageSpace() {
		for (int i = DollScreenHandler.DOLL_STORAGE_START; i <= DollScreenHandler.DOLL_HOTBAR_END; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 指定物品能否放进存储区：有空位，或存在未满栈的同物品槽可堆叠。
	 * 用于拾取前判断，避免"捡起来又掉回去"的空转。
	 */
	private boolean canFitInStorage(ItemStack stack) {
		for (int i = DollScreenHandler.DOLL_STORAGE_START; i <= DollScreenHandler.DOLL_HOTBAR_END; i++) {
			ItemStack slot = inventory.getItem(i);
			if (slot.isEmpty()) {
				return true;
			}
			if (ItemStack.isSameItemSameComponents(slot, stack)
					&& slot.getCount() < slot.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 扫描人偶周围 {@value #DROP_PICKUP_RANGE} 格内的掉落物并收进存储区。
	 * 人偶是 LivingEntity 而非玩家，原版不会自动拾取 ItemEntity——挖矿/砍树等
	 * 掉落在脚下的物品若没人捡会永久堆积。只捡当前能放进存储区的物品
	 * （有空格或可堆叠），放不进的留在原地等玩家处理；跳过仍有拾取延迟的
	 * 掉落物（刚落地 10 tick 内不吸，避免抢走玩家刚丢/刚掉的东西）。
	 */
	private void collectNearbyDrops() {
		if (dropPickupCooldown-- > 0) {
			return;
		}
		dropPickupCooldown = DROP_PICKUP_INTERVAL;
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		AABB box = this.getBoundingBox().inflate(DROP_PICKUP_RANGE);
		List<ItemEntity> drops = serverLevel.getEntitiesOfClass(ItemEntity.class, box,
			e -> e.isAlive() && !e.hasPickUpDelay());
		for (ItemEntity item : drops) {
			ItemStack stack = item.getItem();
			if (stack.isEmpty() || !canFitInStorage(stack)) {
				continue;
			}
			addToDollInventory(stack);
			if (stack.isEmpty()) {
				item.discard();
				// 拾取音效（与原版玩家拾取一致的高音"啵"声）
				serverLevel.playSound(null, item.getX(), item.getY(), item.getZ(),
					SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
					(this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 1.4f + 2.0f);
			}
		}
	}

	// ---- Aura 辅助 ----

	/** 创建以 center 为中心、XZ 和 Y 各延伸 radius 的 AABB。 */
	private static AABB createAuraAABB(Vec3 center, double radius) {
		return AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
	}

	/**
	 * 苍白人偶恐惧光环登记表（仅服务端内存态）。
	 * <p>
	 * 存活苍白人偶每 tick 把当前光环中心{@link #getAuraCenter()}登入此表，供：
	 * <ul>
	 *   <li>{@code io.github.a10086ovo.doll.mixin.MobMixin#paleFearAura} 与 {@link #isInPaleFearAura} 做索敌拦截 + 移动抑制的
	 *       低开销查询（O(活跃苍白人偶数) 次距离比较，替代每次 canAttack/hurt 都 32³ getEntities 扫描）；</li>
	 *   <li>{@link io.github.a10086ovo.doll.mixin.LivingEntityFearAuraMixin} 30% 易伤倍率的低开销判定。</li>
	 * </ul>
	 * 人偶死亡/卸载/换维度经 {@link #removePaleAuraCenter} 移除条目，受影响生物 AI 自然恢复；
	 * SERVER_STARTED 时 {@link #clearPaleAuraCenters} 清空（跨存档不残留）。
	 */
	private static final Map<UUID, Vec3> paleAuraCenters = new HashMap<>();

	/**
	 * 下界人偶岩浆无减速光环登记表（仅服务端内存态）。
	 * <p>
	 * 存活下界人偶每 tick 把当前光环中心登入此表，供 {@link #isInNetherAura} 低开销判定。
	 * 此表用于 {@link io.github.a10086ovo.doll.mixin.EntityLavaSpeedMixin} 中让光环内任意生物（含主人/玩家）在岩浆里不受移动减速。
	 * 人偶死亡/卸载/换维度移除条目，自然恢复岩浆减速。
	 */
	private static final Map<UUID, Vec3> netherAuraCenters = new HashMap<>();

	/** 每 tick 更新本苍白人偶在光环登记表中的中心；非苍白/已死亡则移除。 */
	private void updatePaleAuraCenter() {
		if (getDollVariant() == DollVariant.PALE && this.isAlive() && !this.level().isClientSide()) {
			paleAuraCenters.put(this.getUUID(), getAuraCenter());
		} else {
			paleAuraCenters.remove(this.getUUID());
		}
	}

	/** 人偶移除/死亡时从登记表移除，使受影响生物 AI 立即恢复（未持久化 NoAi，无需手动恢复）。 */
	private static void removePaleAuraCenter(UUID uuid) {
		if (uuid != null) {
			paleAuraCenters.remove(uuid);
		}
	}

	/** 服务器启动时清空登记表（跨存档不残留旧光环中心）。 */
	public static void clearPaleAuraCenters() {
		paleAuraCenters.clear();
	}

	/** 每 tick 更新本下界人偶在光环登记表中的中心；非下界/已死亡则移除。 */
	private void updateNetherAuraCenter() {
		if (getDollVariant() == DollVariant.NETHER && this.isAlive() && !this.level().isClientSide()) {
			netherAuraCenters.put(this.getUUID(), getAuraCenter());
		} else {
			netherAuraCenters.remove(this.getUUID());
		}
	}

	/** 人偶移除/死亡时从登记表移除。 */
	private static void removeNetherAuraCenter(UUID uuid) {
		if (uuid != null) {
			netherAuraCenters.remove(uuid);
		}
	}

	/** 服务器启动时清空登记表（跨存档不残留旧光环中心）。 */
	public static void clearNetherAuraCenters() {
		netherAuraCenters.clear();
	}

	/** 低开销判断：位置是否落在任意下界人偶的 16 格光环内。 */
	public static boolean isInNetherAura(Vec3 pos) {
		if (netherAuraCenters.isEmpty()) return false;
		double r2 = 16.0 * 16.0;
		for (Vec3 center : netherAuraCenters.values()) {
			if (pos.distanceToSqr(center) <= r2) return true;
		}
		return false;
	}

	/** 兼容实体签名重载。 */
	public static boolean isInNetherAura(Entity entity) {
		return entity != null && isInNetherAura(entity.position());
	}

	/**
	 * 苍白人偶恐惧光环（软化版，无 NoAi 持久化）：每 20 tick 清除光环内敌对生物的遗留仇恨。
	 * <p>
	 * 半径 16 格，中心由 {@link #getAuraCenter()} 决定（跟随时为玩家，不跟随时为人偶自身）。
	 * <ul>
	 *   <li><b>统一软化</b>：所有光环内 {@link Enemy}（含史莱姆/岩浆怪等接触伤害单位）一律
	 *       清空当前目标、打断进行中的攻击——不再对史莱姆特例 {@code setNoAi(true)}。</li>
	 *   <li><b>索敌抑制</b>：由 {@code io.github.a10086ovo.doll.mixin.MobMixin#paleFearAura} 在
	 *       {@code Mob.canAttack} HEAD 返回 false 实现（无法建立/维持任何目标）。</li>
	 *   <li><b>移动抑制（非 NoAi）</b>：由 {@code io.github.a10086ovo.doll.mixin.MobMixin#paleFearImmobilize}
	 *       在 {@code Mob.serverAiStep} HEAD 取消 AI 滴答实现——寻路/仇恨/随机游荡一并停止，
	 *       但重力与击退仍生效（非"粗暴冻结"）。判定基于本登记表，人偶卸载即条目移除，生物自然恢复。</li>
	 *   <li><b>跳跃抑制</b>：{@code AbstractCubeMob}（史莱姆/岩浆怪/硫磺立方体的父类）覆写
	 *       {@code tick()} 且自带独立于 AI goal 的跳跃逻辑，serverAiStep 取消无法阻止。
	 *       由 {@code io.github.a10086ovo.doll.mixin.LivingEntityFearAuraMixin#paleFearNoJump} 在
	 *       {@code LivingEntity.jumpFromGround()} HEAD 拦截补完。</li>
	 * </ul>
	 * 30% 易伤由 {@link io.github.a10086ovo.doll.mixin.LivingEntityFearAuraMixin} 在伤害计算时按本登记表判定。
	 */
	private int fearAuraCooldown = 0;
	private void applyFearAura() {
		if (fearAuraCooldown-- > 0) {
			return;
		}
		fearAuraCooldown = 20; // 每 20 tick（1 秒）清一次遗留仇恨
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		AABB box = createAuraAABB(center, radius);
		List<Mob> enemies = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob instanceof Enemy && mob.isAlive()
		);
		for (Mob mob : enemies) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			// 统一软化：清空当前目标打断进行中的攻击（含史莱姆等接触伤害单位）。
			// 持续索敌抑制由 MobMixin.paleFearAura（canAttack→false）负责，
			// 移动抑制由 MobMixin.paleFearImmobilize（serverAiStep 取消）负责——均不持久化 NoAi。
			mob.setTarget(null);
		}
	}

	/**
	 * 下界人偶安抚光环——索敌层拦截 + 遗留仇恨清理。
	 *
	 * <p><b>核心机制（MobMixin 注入 Mob.canAttack）</b>：等价创造模式。
	 * 创造模式玩家不被攻击的核心是 {@code Player.canBeSeenAsEnemy()} 返回 false，
	 * 导致 {@code Mob.canAttack(player)} 返回 false，所有索敌路径无法将玩家设为目标：
	 * <ul>
	 *   <li>TargetGoal 调 canAttack → false → 不设 target</li>
	 *   <li>StartAttacking（Piglin Brain）调 canAttack → false → 不设 ANGRY_AT</li>
	 *   <li>NeutralMob.isAngryAt 调 canAttack → false → 仇恨检查失败</li>
	 * </ul>
	 * Mixin 生效后新索敌被阻止，仇恨无法重建。
	 *
	 * <p><b>本方法（遗留仇恨清理）</b>：清除在 Mixin 生效前 / 被 alertOthers 等非 canAttack
	 * 路径设置的已有 target。Mixin 阻止新索敌后，本方法只需低频清理。
	 */
	public static final Set<String> NETHER_MOB_IDS = Set.of(
		"zombified_piglin", "piglin", "piglin_brute", "hoglin", "zoglin",
		"ghast", "magma_cube", "blaze", "wither_skeleton", "wither"
	);

	/**
	 * 海洋人偶安抚光环的目标：敌对海洋生物。
	 */
	public static final Set<String> SEA_MOB_IDS = Set.of(
		"guardian", "elder_guardian", "drowned"
	);

	/**
	 * 森林人偶自然守护光环的目标：主世界陆地敌对生物。
	 * 与下界（NETHER_MOB_IDS）/海洋（SEA_MOB_IDS）按维度划分不同，
	 * 森林覆盖主世界陆地常见敌对生物（僵尸/骷髅/蜘蛛/苦力怕/女巫等），
	 * 末影人（瞬移）与幻翼（飞行）不在此列。
	 */
	public static final Set<String> FOREST_MOB_IDS = Set.of(
		"zombie", "husk", "zombie_villager", "skeleton", "stray", "bogged",
		"spider", "cave_spider", "creeper", "witch", "slime",
		"ravager", "vindicator", "pillager", "evoker", "vex"
	);

	/**
	 * 判断实体类型是否为下界生物。
	 */
	public static boolean isNetherMobType(EntityType<?> type) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null && NETHER_MOB_IDS.contains(id.getPath());
	}

	/**
	 * 判断实体类型是否为敌对海洋生物。
	 */
	public static boolean isSeaMobType(EntityType<?> type) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null && SEA_MOB_IDS.contains(id.getPath());
	}

	/**
	 * 判断实体类型是否为主世界陆地敌对生物（森林人偶安抚光环目标）。
	 */
	public static boolean isForestMobType(EntityType<?> type) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null && FOREST_MOB_IDS.contains(id.getPath());
	}

	/** 下界人偶主人保护缓存：Player UUID → 过期 tick（避免每次 canAttack 都搜索实体） */
	private static final Map<UUID, Long> netherProtectedCache = new HashMap<>();
	private static final long NETHER_PROTECTED_CACHE_TTL = 60L; // 3 秒

	/** 海洋人偶主人保护缓存：Player UUID → 过期 tick */
	private static final Map<UUID, Long> seaProtectedCache = new HashMap<>();
	private static final long SEA_PROTECTED_CACHE_TTL = 60L; // 3 秒

	/**
	 * 判断实体是否被下界人偶保护（不被下界生物索敌）。
	 * 由 {@link io.github.a10086ovo.doll.mixin.MobMixin} 在 Mob.canAttack HEAD 注入时调用。
	 *
	 * @param target 被检查的实体（人偶本身或玩家主人）
	 * @return true 表示该实体对下界生物"不可见为敌人"（canAttack 返回 false）
	 */
	public static boolean isNetherDollProtected(LivingEntity target) {
		// NETHER variant 的 DollEntity 本身
		if (target instanceof DollEntity doll) {
			return doll.getDollVariant() == DollVariant.NETHER;
		}
		// 主人：附近 32 格内有活跃的 NETHER DollEntity
		if (target instanceof Player player) {
			UUID uuid = player.getUUID();
			Level level = target.level();
			long tick = level.getGameTime();
			// 缓存命中
			Long expiry = netherProtectedCache.get(uuid);
			if (expiry != null && tick < expiry) {
				return true;
			}
			// 缓存过期：搜索附近 NETHER DollEntity
			if (level instanceof ServerLevel serverLevel) {
				List<DollEntity> dolls = serverLevel.getEntities(
					EntityTypeTest.forClass(DollEntity.class),
					AABB.ofSize(target.position(), 32, 32, 32),
					d -> d.getDollVariant() == DollVariant.NETHER && d.isAlive()
						&& uuid.equals(d.getOwnerUuid())
				);
				if (!dolls.isEmpty()) {
					netherProtectedCache.put(uuid, tick + NETHER_PROTECTED_CACHE_TTL);
					return true;
				}
			}
			return false;
		}
		return false;
	}

	/**
	 * 判断实体是否被海洋人偶保护（不被敌对海洋生物索敌）。
	 * 由 {@link io.github.a10086ovo.doll.mixin.MobMixin} 在 Mob.canAttack HEAD 注入时调用。
	 *
	 * @param target 被检查的实体（人偶本身或玩家主人）
	 * @return true 表示该实体对海洋生物"不可见为敌人"（canAttack 返回 false）
	 */
	public static boolean isSeaDollProtected(LivingEntity target) {
		// SEA variant 的 DollEntity 本身
		if (target instanceof DollEntity doll) {
			return doll.getDollVariant() == DollVariant.SEA;
		}
		// 主人：附近 32 格内有活跃的 SEA DollEntity
		if (target instanceof Player player) {
			UUID uuid = player.getUUID();
			Level level = target.level();
			long tick = level.getGameTime();
			// 缓存命中
			Long expiry = seaProtectedCache.get(uuid);
			if (expiry != null && tick < expiry) {
				return true;
			}
			// 缓存过期：搜索附近 SEA DollEntity
			if (level instanceof ServerLevel serverLevel) {
				List<DollEntity> dolls = serverLevel.getEntities(
					EntityTypeTest.forClass(DollEntity.class),
					AABB.ofSize(target.position(), 32, 32, 32),
					d -> d.getDollVariant() == DollVariant.SEA && d.isAlive()
						&& uuid.equals(d.getOwnerUuid())
				);
				if (!dolls.isEmpty()) {
					seaProtectedCache.put(uuid, tick + SEA_PROTECTED_CACHE_TTL);
					return true;
				}
			}
			return false;
		}
		return false;
	}

	/**
	 * 低开销判断：位置是否落在任意活跃苍白人偶的 16 格恐惧光环内。
	 * <p>
	 * 仅遍历{@link #paleAuraCenters}登记表（活跃苍白人偶通常为个位数），替代每次调用都
	 * 32³ {@code getEntities} 扫描。由 {@link io.github.a10086ovo.doll.mixin.MobMixin} 的索敌拦截与
	 * 移动抑制、以及 {@link io.github.a10086ovo.doll.mixin.LivingEntityFearAuraMixin} 的易伤倍率调用。
	 *
	 * @param pos 待判定的世界坐标（通常为被检查生物的 position）
	 * @return true 表示该位置处于某个存活苍白人偶的光环内
	 */
	public static boolean isInPaleFearAura(Vec3 pos) {
		if (paleAuraCenters.isEmpty()) return false;
		double r2 = 16.0 * 16.0;
		for (Vec3 center : paleAuraCenters.values()) {
			if (pos.distanceToSqr(center) <= r2) return true;
		}
		return false;
	}

	/** 兼容实体签名重载。 */
	public static boolean isInPaleFearAura(LivingEntity mob) {
		return mob != null && isInPaleFearAura(mob.position());
	}

	private int netherPacifyCooldown = 0;
	private void applyNetherPacifyAura() {
		if (netherPacifyCooldown-- > 0) {
			return;
		}
		netherPacifyCooldown = 20; // 每 20 tick（1 秒）清理遗留仇恨
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		AABB box = createAuraAABB(center, radius);
		Player owner = getOwnerPlayer();
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && mob.getTarget() != null
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			if (!isNetherMobType(mob.getType())) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == this || (owner != null && target == owner)) {
				// NeutralMob（僵尸猪灵）：清除持久仇恨
				if (mob instanceof NeutralMob neutralMob) {
					neutralMob.stopBeingAngry();
				}
				// Piglin/PiglinBrute：清除 Brain 愤怒记忆
				mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
				mob.getBrain().eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
				// 兜底
				mob.setTarget(null);
			}
		}
	}

	// 灼烧光环冷却（下界人偶：给范围内主人抗火）
	private int netherBurnCooldown = 0;

	/**
	 * 下界人偶灼烧光环（每 20 tick，半径 16 格）：
	 * 主人在光环内时持续获得抗火，契合下界主题——跟随下界人偶即免疫火焰。
	 */
	private void applyNetherBurnAura() {
		if (netherBurnCooldown-- > 0) {
			return;
		}
		netherBurnCooldown = 20;
		if (this.level().isClientSide()) {
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			return;
		}
		owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
	}

	private int seaPacifyCooldown = 0;
	private int seaPlayerAuraCooldown = 0;
	private void applySeaPacifyAura() {
		if (seaPacifyCooldown-- > 0) {
			return;
		}
		seaPacifyCooldown = 20; // 每 20 tick（1 秒）清理遗留仇恨
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		AABB box = createAuraAABB(center, radius);
		Player owner = getOwnerPlayer();
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && mob.getTarget() != null
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			if (!isSeaMobType(mob.getType())) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == this || (owner != null && target == owner)) {
				mob.setTarget(null);
			}
		}
	}

	private int forestVineCooldown = 0;

	/**
	 * 森林人偶藤蔓缠绕光环（每 20 tick，半径 16 格）：
	 * <p>
	 * 对光环内所有主世界陆地敌对生物持续施加缓慢（Slowness II），
	 * 不区分是否在索敌——入侵者踏入森林即被藤蔓牵制。离开光环后效果自然过期。
	 * <p>
	 * 注意：原"敌怪安抚（仇恨豁免）"天赋已移除，故本方法不再清除仇恨，
	 * 也不再由 {@link io.github.a10086ovo.doll.mixin.MobMixin} 拦截 canAttack。
	 * 主世界敌对生物仍会正常仇恨主人，只是靠近人偶时会被减速牵制。
	 */
	private void applyForestVineAura() {
		if (forestVineCooldown-- > 0) {
			return;
		}
		forestVineCooldown = 20; // 每 20 tick（1 秒）刷新
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		AABB box = createAuraAABB(center, radius);
		// 重平衡：藤蔓缠绕覆盖范围内所有敌方生物（Enemy）AoE，不再限于森林列表；
		// 缓慢 IV（amp3）。原「中毒」已移除——毒伤移交副手荆棘盾的反伤（ThornsShieldMixin 单独处理），
		// 常态藤蔓不再附加中毒。
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && mob instanceof Enemy
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, false)); // 缓慢 IV
		}
	}

	// ------------------------------------------------------------------
	// 森林人偶天赋：敌方生物高亮（半径 32 格，光灵箭式 GLOWING 描边）
	// ------------------------------------------------------------------

	private int forestMarkCooldown = 0;

	/**
	 * 森林人偶威胁标记（每 20 tick，以光环为中心半径 32 格）：
	 * 对范围内的陆地敌对生物施加 {@link MobEffects#GLOWING}（光灵箭同款白色描边高亮），
	 * 持续刷新使其稳定可见，方便主人看清贴脸威胁。离开范围或人偶不在场即自然过期。
	 * 仅 FOREST 变体触发，且仅主人在线时生效（联机时其他玩家不享受该圈）。
	 */
	private void applyForestMarkAura() {
		if (forestMarkCooldown-- > 0) {
			return;
		}
		forestMarkCooldown = 20; // 每 20 tick 刷新一次高亮
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || !owner.isAlive()) {
			return;
		}
		double radius = 32.0;
		Vec3 center = getAuraCenter();
		AABB box = createAuraAABB(center, radius);
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive()
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			if (!isForestMobType(mob.getType())) {
				continue;
			}
			// 光灵箭式高亮：白色描边（持续 3 秒，每 20 tick 重刷，离开范围自然过期）
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
		}
	}

	// ------------------------------------------------------------------
	// 森林人偶天赋 2：友善生物吸引（半径 8 格，朝人偶移动）
	// ------------------------------------------------------------------

	/** 森林人偶吸引的友善动物：主世界常见被动/中立动物（不含已驯服宠物，见 isAnimalType 内处理）。 */
	public static final Set<String> FOREST_ANIMAL_IDS = Set.of(
		"pig", "sheep", "cow", "chicken", "rabbit", "mooshroom",
		"horse", "donkey", "mule", "llama", "cat", "wolf",
		"parrot", "bee", "turtle", "fox", "sniffer", "goat", "camel"
	);

	/** 判断实体类型是否属于森林人偶吸引的友善动物。 */
	public static boolean isForestAnimalType(EntityType<?> type) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null && FOREST_ANIMAL_IDS.contains(id.getPath());
	}

	private int forestAttractCooldown = 0;

	/**
	 * 森林人偶友善生物吸引（每 10 tick，半径 8 格）：
	 * 对范围内、属于 {@link #FOREST_ANIMAL_IDS} 且<b>未驯服</b>的动物施加"朝人偶移动"的导航力，
	 * 模拟"手持小麦吸引"的效果。离开范围后导航目标不再刷新，动物自然恢复自身 AI。
	 * 纯移动吸引，不繁殖、不喂食、不强制跟随（已驯服宠物如狼/猫会被排除，避免跟主人走丢）。
	 */
	private void applyForestAnimalAttract() {
		if (forestAttractCooldown-- > 0) {
			return;
		}
		forestAttractCooldown = 10; // 每 10 tick 刷新一次导航目标（足够顺滑且低开销）
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 8.0;
		AABB box = createAuraAABB(center, radius);
		List<Mob> animals = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && isForestAnimalType(mob.getType())
		);
		Vec3 target = this.position();
		for (Mob animal : animals) {
			if (animal.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			// 排除已驯服宠物（狼/猫/鹦鹉等），避免它们脱离主人
			if (animal instanceof TamableAnimal tamable && tamable.isTame()) {
				continue;
			}
			// 朝人偶移动：对齐原版 TemptGoal（手持小麦吸引）的满速行为（speed=1.0）。
		// 不额外加速——避免动物永远黏着人偶甩不掉（那反成负面天赋）。
		animal.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
		}
	}

	// ------------------------------------------------------------------
	// 森林人偶天赋 3：主人回血 I（半径 16 格，仅主人）
	// ------------------------------------------------------------------

	private int forestRegenCooldown = 0;

	/**
	 * 森林人偶主人回血光环（每 20 tick，半径 16 格）：
	 * 主人在人偶 16 格范围内时持续获得生命恢复 I（Regen I，amplifier 0）。
	 * 效果 duration≈10s，每 20 tick 重刷；主人离开范围后不再刷新，效果自然过期。
	 * 仅主人受益（联机时朋友不享受），且仅 FOREST 变体触发。
	 */
	private void applyForestRegenAura() {
		if (forestRegenCooldown-- > 0) {
			return;
		}
		forestRegenCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (this.level().isClientSide()) {
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			return; // 主人不在光环内，不刷新（已有效果将自然过期）
		}
		// 生命恢复 I（amplifier 0 = 等级 I），持续时间 200 tick（10 秒），每 20 tick 重刷
		owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, false));
	}

	/**
	 * 海洋人偶玩家增益光环：主人位于人偶 16 格半径内时获得增益。
	 * <ul>
	 *   <li>水下呼吸（WATER_BREATHING）：只要在主人的范围内就持续给予。</li>
	 *   <li>水下速掘（HASTE）：仅当主人<b>身处水中</b>时才给予；离开水不加速，
	 *       严格贴合"水下"二字，避免影响陆地挖掘节奏。</li>
	 * </ul>
	 * 效果 duration≈10s，每 20 tick 重刷；主人离开范围后不再刷新，效果自然过期，
	 * 无需主动移除。仅主人受益（联机时朋友不享受），且仅 SEA 变体触发。
	 */
	private void applySeaPlayerAura() {
		if (seaPlayerAuraCooldown-- > 0) {
			return;
		}
		seaPlayerAuraCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (this.level().isClientSide()) {
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 16.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			return; // 主人不在光环内，不刷新（已有效果将自然过期）
		}
		// 水下呼吸：只要在主人的 16 格内就持续给予
		owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false));
		// 清除主人身上的挖掘疲劳（MINING_FATIGUE）——海洋人偶庇护主人，避免减速/虚弱采集带来的手感拖沓
		if (owner.hasEffect(MobEffects.MINING_FATIGUE)) {
			owner.removeEffect(MobEffects.MINING_FATIGUE);
		}
		// 水下速掘：仅当主人身处水中时给予急迫(HASTE)，离开水不加速。
		// 高等级抵消水中(非地面)挖掘 ÷5 惩罚：游泳时≈1.16×陆地、站海底≈5.8×陆地。
		if (owner.isInWater()) {
			owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, SEA_HASTE_LEVEL, false, false));
		}
	}

	// ------------------------------------------------------------------
	// 向导人偶引导光环：主人 速度II + 跳跃II + 护甲+4（半径 32 格，仅主人）
	// ------------------------------------------------------------------

	// 护甲 +4 的 transient 修饰符（不写盘），主人离开光环范围时移除
	private static final Identifier GUIDE_ARMOR_MOD_ID =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "guide_armor_bonus");
	private static final AttributeModifier GUIDE_ARMOR_MOD =
		new AttributeModifier(GUIDE_ARMOR_MOD_ID, 4.0, AttributeModifier.Operation.ADD_VALUE);

	private int guideAuraCooldown = 0;

	/**
	 * 向导人偶引导光环（每 20 tick，半径 32 格）：
	 * 主人在人偶 32 格范围内时持续获得 速度 II（SPEED amp1）+ 跳跃 II（JUMP_BOOST amp1）+ 护甲 +4，
	 * 效果 duration≈10s，每 20 tick 重刷；主人离开范围后不再刷新（效果自然过期、护甲修饰符移除）。
	 * 仅主人受益（联机时朋友不享受），且仅 GUIDE 变体触发。
	 */
	private void applyGuideAura() {
		if (guideAuraCooldown-- > 0) {
			return;
		}
		guideAuraCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (this.level().isClientSide()) {
			return;
		}
		Player owner = getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = getAuraCenter();
		double radius = 32.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			// 主人不在光环内：若身上残留护甲修饰符则移除，其余效果（速度/跳跃）自然过期
			AttributeInstance armorAttr = owner.getAttribute(Attributes.ARMOR);
			if (armorAttr != null && armorAttr.hasModifier(GUIDE_ARMOR_MOD_ID)) {
				armorAttr.removeModifier(GUIDE_ARMOR_MOD_ID);
			}
			return;
		}
		// 速度 II（SPEED amp1）+ 跳跃 II（JUMP_BOOST amp1），持续 200 tick（10 秒）
		owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1, false, false));
		owner.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1, false, false));
		// 护甲 +4：transient 修饰符，范围内持续提供
		AttributeInstance armorAttr = owner.getAttribute(Attributes.ARMOR);
		if (armorAttr != null && !armorAttr.hasModifier(GUIDE_ARMOR_MOD_ID)) {
			armorAttr.addTransientModifier(GUIDE_ARMOR_MOD);
		}
	}

	/**
	 * 背包满：向主人广播一次"暂缓挖矿"提示（防刷屏，清包后自动复位）。
	 */
	private void notifyMineBackpackFull() {
		if (!mineBackpackFullNotified) {
			mineBackpackFullNotified = true;
			Player owner = getOwnerPlayer();
			if (owner != null) {
				owner.sendSystemMessage(Component.translatable(
					"message." + DollModConstants.MOD_ID + ".mine_backpack_full",
					dollDisplayName()));
			}
			// 背包满 = 失败
			voiceNoTool();
		}
	}

	// ==== 音符盒语音系统 ====

	/** 选中/成功：原版按键音（统一反馈，避免旋律噪音）。 */
	private void voiceConfirm() {
		playUiClick();
	}

	/** 失败/无法执行：原版按键音（统一反馈）。 */
	private void voiceNoTool() {
		playUiClick();
	}

	/**
	 * 模式切换：原版按键音（统一反馈）。原 MODE_MELODIES / MODE_SOUNDS 音色+旋律系统
	 * 已被简化去除，调用方仍保留传 mode 参数（便于后续如需按模式差异化音效时复用）。
	 */
	private void playModeVoice(int mode) {
		playUiClick();
	}

	/**
	 * 统一按键反馈音：已改为<b>客户端本地播放</b>，服务端不再发声（空实现）。
	 * <p>原实现 {@code level().playSound(null, owner, UI_BUTTON_CLICK, UI, 1.0F, 1.0F)} 有两个
	 * 被用户实测确认的 bug：
	 * <ol>
	 *   <li><b>4 倍音量</b>：原版按钮（AbstractWidget → SimpleSoundInstance.forUI(sound, 1.0F)）
	 *       的第二个参数是音调、音量恒为内部 0.25（26.2 字节码确认 forUI 三参为
	 *       {@code (pitch, volume)}）；而这里广播用的是 1.0 音量，比原版响 4 倍。</li>
	 *   <li><b>跨玩家广播</b>：{@code playSound(null, owner, ...)} 在服务端把声音广播给
	 *       owner 周围 16 格内<b>所有</b>玩家（except=null 不排除任何人），导致
	 *       "其他玩家点他们自己的人偶按钮，声音播到我电脑上"。</li>
	 * </ol>
	 * <p>正确做法与原版一致：按键音由点击者客户端在按下瞬间本地播放
	 * （{@code Minecraft.getSoundManager().play(SimpleSoundInstance.forUI(...))}），
	 * 相对音源、无距离衰减、走"界面"音量滑块，只有自己听得到。
	 * 两个 GUI（DollControlScreen / DollInventoryScreen）的点击处已补上该本地播放。
	 * <p>保留空方法：voiceConfirm / voiceNoTool / playModeVoice 及各处调用点无需改动。
	 * 自动劳作类的失败提示（如挖矿背包满）仍走系统消息文字，不再发声。
	 */
	private void playUiClick() {
	}

	/** 死亡：打碎玻璃的脆响（清脆明显，嘈杂环境中也能分辨）。 */
	private void voiceDeath() {
		this.playSound(SoundEvents.GLASS_BREAK, 1.0f, 1.0f);
	}

	public java.util.UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public void setOwner(Player player) {
		this.ownerUuid = player.getUUID();
	}

	/**
	 * 判断该玩家是否为人偶主人。无主人（ownerUuid 为 null）时返回 false。
	 * 所有归属校验（切模式 / 指挥棒 / 蛋回收与召回）统一走此方法，避免各处
	 * 重复实现且漏掉 null 分支。
	 */
	public boolean isOwnedBy(Player player) {
		return ownerUuid != null && ownerUuid.equals(player.getUUID());
	}

	public Player getOwnerPlayer() {
		if (ownerUuid == null) {
			return null;
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			return serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
		}
		return null;
	}

	public static AttributeSupplier.Builder createDollAttributes() {
		return LivingEntity.createLivingAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.1)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_KNOCKBACK);
	}

	/**
	 * 人偶登记 UUID。现直接使用引擎 UUID（Entity.getUUID()），
	 * 蛋上存的 DollUuid 即此值，召回/匹配都以此为准。
	 */
	public UUID getDollUuid() {
		return this.getUUID();
	}

	public void setDollLevel(int level) {
		getEntityData().set(DATA_DOLL_LEVEL, level);
		// #0 B：普通/NONE 不随等级晋升为 WARDEN；WARDEN 仅由 setDollVariant 显式获得。
		// 普通工人始终是 NONE，封顶 5 阶纯工具。
		if (getDollVariant() == DollVariant.NONE) {
			getEntityData().set(DATA_DOLL_VARIANT, DollVariant.NONE.ordinal());
		}
		getEntityData().set(DATA_IS_WARDEN_VARIANT, getDollVariant() == DollVariant.WARDEN);
		double maxHp = computeMaxHealth(getDollVariant(), level);
		AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(maxHp);
		}
		setHealth((float) maxHp);
		AttributeInstance knockbackRes = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockbackRes != null) {
			knockbackRes.setBaseValue(getDollVariant() == DollVariant.WARDEN ? 1.0 : 0.0);
		}
		// 海洋人偶专属：允许水中寻路（仅 SEA 生效，其他人偶 allowWater 保持 false）
		navigator.setAllowWater(isSeaDoll());
	}

	/**
	 * 覆盖 Avatar 默认的人偶附件点（DEFAULT_VEHICLE_ATTACHMENT 有座椅偏移）。
	 * CarryOn 等叠加模组调用此方法定位乘客/被堆叠实体——当乘客也是 DollEntity 时，
	 * 用贴合偏移返回，避免两个 Avatar 互相堆叠时出现可见空隙；其他组合（玩家/普通生物骑人偶）
	 * 沿用 Avatar 原行为，保持视觉一致。
	 */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		if (passenger instanceof DollEntity) {
			return new Vec3(0.0, this.getBbHeight() - 0.15, 0.0);
		}
		return super.getPassengerAttachmentPoint(passenger, dims, scale);
	}

	/**
	 * 骑乘定位：26.2 的骑乘附着点由 EntityType 注册时的 vehicleAttachment 提供（见 DollMod.DOLL_ENTITY，
	 * 设为 (0, 0.6, 0)，与原版 Avatar/Player 一致），人偶坐船/矿车时臀部对齐座位、不再悬浮。
	 * 注意：旧版 API getRidingOffset() 在 26.2 已废弃，不会被引擎调用（曾因此死代码误判"已修复"）。
	 */
}