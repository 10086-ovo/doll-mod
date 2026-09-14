package io.github.a10086ovo.doll.entity;

import io.github.a10086ovo.doll.DollMod;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 飞行下界剑 —— 召唤者蓄力召唤的伴生守护飞剑（注册见 DollMod.NETHER_FLYING_SWORD_ENTITY）。
 * <p>
 * 支持玩家与下界人偶作为召唤者（owner 泛化为 {@link LivingEntity}）。
 * 继承 {@link ItemEntity} 复用原版物品载体基建（关闭拾取/合并/重力/老化），
 * 但渲染改由自定义 3D 剑模型（{@code NetherFlyingSwordRenderer}）负责，
 * tick 走 super 链保住位置插值，行为由服务端 {@link #tickSword()} 驱动。
 * <p>
 * 表现（服务端驱动，全姿态同步到客户端渲染）——「剑仙」风格：
 * <ul>
 *   <li>HOVER（空闲）：按召唤者<b>头部朝向（视角）</b>计算正后方锚点并即时锁定，剑身长轴经渲染器对齐到竖直、刃尖朝下，
 *       水平朝向同步跟随视角，绕剑身长轴自转 + 轻微浮动</li>
 *   <li>THRUST（锁定）：先<b>升空</b>一段高度（避开刺穿召唤者），再刃尖直指敌人高速直线冲刺；
 *       命中造成 {@link #HIT_DAMAGE} 伤害 + 点燃 {@link #HIT_IGNITE_SECONDS} 秒，<b>不归位</b>，
 *       刺中后<b>向后回拉一小段距离</b>（剑尖始终对准敌人），冷却后再继续突刺，
 *       对同一目标持续刺击直到目标<b>死亡</b>或离开索敌半径才转入 RETURN</li>
 *   <li>RETURN（归位）：直线飞回「正后方」锚点，到位后回到 HOVER，重新索敌（循环穿刺）</li>
 * </ul>
 * 召唤者死亡/移除/跨维度时消散；同一召唤者同时仅一把（新剑顶替旧剑）。
 * 召唤后约 1 秒宽限强制贴背、不索敌（避免脸前有敌时立刻飞出）；此后才进入锁定。
 */
public class NetherFlyingSwordEntity extends ItemEntity {

	private static final String OWNER_UUID_NBT_KEY = "OwnerUuid";

	// ============ 锚点 / 姿态常量 ============
	/** 悬停锚点：玩家正后方距离（格） */
	private static final double BACK_DIST = 0.9;
	/** 悬停锚点相对脚底的 Y 偏移（落在后背中段，而非头顶） */
	private static final double BACK_Y_OFFSET = 1.1;
	/** 空闲姿态：绕剑身长轴（世界 Y）自转角速度（度/tick） */
	private static final float IDLE_SPIN_DEG = 6.0f;
	/** 空闲姿态：剑刃竖直朝下（绕 X 180°，模型 +Y 翻向下） */
	private static final float IDLE_PITCH_DEG = 180.0f;
	/** 锁敌升空：升起高度（相对悬停锚点，格）与时长（tick） */
	private static final double RISE_HEIGHT = 1.6;
	private static final int RISE_TICKS = 14;
	/** 收势对准（2026-09-09）：进入冲刺的头几 tick 用慢速推进，等姿态在客户端插值中收敛后再全速突刺，
	 *  避免"升空→转向 120°+ 但冲刺 1~2 tick 就到 → 接触瞬间剑只转了一半、固定偏角撞上"的观感 */
	private static final int THRUST_SETTLE_TICKS = 8;
	private static final double THRUST_SETTLE_SPEED = 0.85;
	/** 移动速度（格/tick） */
	private static final double HOVER_SPEED = 0.35;
	private static final double RISE_SPEED = 0.5;
	private static final double THRUST_SPEED = 1.6;
	private static final double RETURN_SPEED = 0.6;
	/** 归位判定距离平方 */
	private static final double HOME_THRESHOLD = 0.4;
	/** 模型实测（2026-09-09 几何校准）：旋转轴心落在剑身长轴中点，轴心→剑尖 10.606px ÷ 16 ≈ 0.663 格 */
	private static final double TIP_LENGTH = 10.606 / 16.0;
	/** 命中接触：剑尖刺入目标体表的深度（格）。冲刺以「接触距离」钳制终点，命中即停、绝不穿体 */
	private static final double STAB_PENETRATION = 0.15;
	/**
	 * 判伤包络（格，2026-09-09 判伤重构为纯几何）：把剑尖端点的碰撞判伤放宽到目标碰撞箱外扩该距离。
	 * 目标是「贴住即伤」——剑尖端点进入 target.getBoundingBox().inflate(TIP_HIT_ENVELOPE) 且冷却结束
	 * 就结算伤害，与相对速度/位移收敛完全解耦；取值比旧中心距容差大，专门覆盖「剑已贴上怪、但中心距
	 * 因怪形/移动偏差差一点而漏判」的假死。0.2 约 3px，观感仍是「贴住才出伤」，不会隔空打怪。
	 */
	private static final double TIP_HIT_ENVELOPE = 0.2;
	/** 冷却中待刺的剑尖间隔（格）：命中回拉后、冷却未好前，剑悬停在目标外 READY_GAP 处，避免剑尖一直贴体 */
	private static final double READY_GAP = 1.2;
	/** 刺中后回拉距离（格）：剑刺中敌人后向后拉开一点，再继续下一次突刺 */
	private static final double RETREAT_DISTANCE = 1.8;
	/** 刺中后回拉速度（格/tick） */
	private static final double RETREAT_SPEED = 0.5;
	/**
	 * 回拉最大时长（tick，2026-09-09 实测卡点修复）：回拉到位判定带容差 + 超时强制转入冲刺。
	 * 旧逻辑用 `awayLen < stopDist+RETREAT_DISTANCE`（严格小于）逐 tick 逼近，目标稍动就永远差一丝
	 * （日志实测：每次命中后平均白等 13 tick、401 次 RETREAT_STUCK），且回拉分支提前 return 不判伤
	 * →「悬停在怪身边无攻击动作」。加容差(0.05) + 超时上限后，最坏 10 tick 必回到冲刺。
	 */
	private static final int RETREAT_MAX_TICKS = 10;
	private static final double RETREAT_ARRIVE_EPSILON = 0.05;

	// ============ 战斗 / 索敌 ============
	/** 索敌半径：以玩家为中心 */
	public static final double SEARCH_RADIUS = 16.0;
	/** 撞击伤害 */
	public static final float HIT_DAMAGE = 8.0f;
	/** 撞击点燃时长（秒） */
	public static final int HIT_IGNITE_SECONDS = 8;
	/** 攻击间隔（tick） */
	private static final int ATTACK_COOLDOWN_TICKS = 12;
	/** 索敌间隔（tick，仅 HOVER 态重算） */
	private static final int RETARGET_INTERVAL = 8;
	/** 姿态插值系数（0~1，越大越跟手） */
	private static final float ORIENT_LERP = 0.4f;

	// ============ 同步数据 ============
	/** 全姿态角（yaw 水平 / pitch 仰俯 / roll 绕长轴自转），覆盖原版 ItemEntity 默认自旋 */
	private static final EntityDataAccessor<Float> DATA_VISUAL_YAW =
		SynchedEntityData.defineId(NetherFlyingSwordEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_VISUAL_PITCH =
		SynchedEntityData.defineId(NetherFlyingSwordEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_VISUAL_ROLL =
		SynchedEntityData.defineId(NetherFlyingSwordEntity.class, EntityDataSerializers.FLOAT);

	// ============ 客户端插值 ============
	/** 上一 client tick 的姿态角（服务端数据 20Hz 到达，渲染时 prev→current 按 partialTick 插值取最短弧，
	 *  仿原版实体 yRotO/yRot；缺此则自转/转向呈 ~20Hz 阶梯跳变）。初值对齐同步数据默认值防出生闪变 */
	private float prevVisualYaw;
	private float prevVisualPitch = IDLE_PITCH_DEG;
	private float prevVisualRoll;

	// ============ 运行态 ============
	private static final int STATE_HOVER = 0;
	private static final int STATE_THRUST = 1;
	private static final int STATE_RETURN = 2;
	/** THRUST 子阶段：0=升空避让，1=直刺，2=刺中后回拉 */
	private static final int PHASE_RISE = 0;
	private static final int PHASE_DASH = 1;
	private static final int PHASE_RETREAT = 2;

	private UUID ownerUuid;
	private int state = STATE_HOVER;
	private int attackCooldown = 0;
	private int retargetCooldown = 0;
	private int thrustPhase = PHASE_RISE;
	private int thrustRiseTicks = 0;
	private LivingEntity currentTarget;
	/** 召唤宽限（tick）：召唤后先稳稳贴在背后、不索敌，避免脸前有敌时立刻飞出像"出现在脸上" */
	private int spawnGrace = 20;
	/** 冲刺收势计数：>0 时以慢速推进让姿态在客户端插值中收敛（进入冲刺时重置） */
	private int thrustSettleTicks;
	/** 本轮回拉已持续 tick（超时兜底：回拉超 RETREAT_MAX_TICKS 强制转冲刺，进入回拉时清零） */
	private int retreatTicks;

	public NetherFlyingSwordEntity(EntityType<? extends NetherFlyingSwordEntity> type, Level level) {
		super(type, level);
		setupSword();
		// 命令 /summon doll-mod:nether_flying_sword / 存档反序列化等「无 NBT 物品」入口：
		// 必须自备默认物品，否则 ItemEntity.tick() 首行判空直接 discard（表现为
		// 「播报生成的是空气、实际什么都没出现」）；读档时 ItemEntity.readAdditionalSaveData
		// 会用存档里的 Item 覆盖此默认值，互不冲突。
		this.setItem(new ItemStack(DollMod.NETHER_SWORD_ITEM));
	}

	/** 召唤入口：在召唤者身边生成飞剑（支持玩家与人偶）。 */
	public NetherFlyingSwordEntity(Level level, LivingEntity owner) {
		super(DollMod.NETHER_FLYING_SWORD_ENTITY, level);
		setupSword();
		this.ownerUuid = owner.getUUID();
		this.setItem(new ItemStack(DollMod.NETHER_SWORD_ITEM));
		Vec3 home = homePosition(owner);
		this.setPos(home.x, home.y, home.z);
	}

	/** 关闭原版掉落物行为的统一开关（两种构造器共用）。 */
	private void setupSword() {
		this.setNoGravity(true);
		this.setPickUpDelay(32767); // 原版拾取豁免值，同时让 isMergable 永远为 false
		this.setUnlimitedLifetime(); // age = INFINITE_LIFETIME，永不老化，也不参与合并
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VISUAL_YAW, 0.0f);
		builder.define(DATA_VISUAL_PITCH, IDLE_PITCH_DEG);
		builder.define(DATA_VISUAL_ROLL, 0.0f);
	}

	// ===================== 客户端读取（供渲染器） =====================

	public float getVisualYaw() {
		return getEntityData().get(DATA_VISUAL_YAW);
	}

	public float getVisualPitch() {
		return getEntityData().get(DATA_VISUAL_PITCH);
	}

	public float getVisualRoll() {
		return getEntityData().get(DATA_VISUAL_ROLL);
	}

	/** 渲染插值用：上一 client tick 的姿态角（配合 getVisual* 做 partialTick 最短弧插值）。 */
	public float getPrevVisualYaw() {
		return this.prevVisualYaw;
	}

	public float getPrevVisualPitch() {
		return this.prevVisualPitch;
	}

	public float getPrevVisualRoll() {
		return this.prevVisualRoll;
	}

	/** 附魔光泽标记（供渲染器叠加 glint 层）。 */
	public boolean isFoil() {
		return this.getItem().hasFoil();
	}

	// ===================== 主逻辑 =====================

	@Override
	public void tick() {
		if (!this.level().isClientSide()) {
			this.tickSword();
		} else {
			// 客户端：每 client tick 记录「上一拍」姿态角，供渲染器按 partialTick 插值取最短弧
			this.prevVisualYaw = this.getVisualYaw();
			this.prevVisualPitch = this.getVisualPitch();
			this.prevVisualRoll = this.getVisualRoll();
		}
		// super 链保留：位置插值（xo/yo/zo）、move 方块碰撞、age 自增
		super.tick();
	}

	/** 服务端逻辑：状态机 + 姿态计算，deltaMovement 交给 super.tick() 的 move 执行。 */
	private void tickSword() {
		LivingEntity owner = getSummoner();
		// 归属校验分两种：
		//  - 有主飞剑（ownerUuid != null）：主人消失/死亡/移除 → 消散；
		//  - 无主实例（如 /summon 裸召唤，ownerUuid == null）：无锚点可跟，原地悬停展示，不消散。
		if (this.ownerUuid != null && (owner == null || !owner.isAlive() || owner.isRemoved())) {
			this.discard();
			return;
		}
		if (owner == null) {
			this.tickOrphanIdle();
			return;
		}

		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}

		Vec3 home = homePosition(owner);

		// 召唤宽限：先贴在背后、不索敌，确保"召唤即上背"而非立刻飞出
		if (this.spawnGrace > 0) {
			this.spawnGrace--;
			this.state = STATE_HOVER;
			this.currentTarget = null;
			this.tickHoverOrReturn(owner, home);
			return;
		}

		// HOVER 态按间隔重新索敌，命中则转入 THRUST（先升空避让）
		if (this.state == STATE_HOVER) {
			if (this.retargetCooldown-- <= 0) {
				this.retargetCooldown = RETARGET_INTERVAL;
				this.currentTarget = findNearestEnemy(owner);
				if (this.currentTarget != null) {
					this.state = STATE_THRUST;
					this.thrustPhase = PHASE_RISE;
					this.thrustRiseTicks = 0;
				}
			}
		}

		// THRUST 态校验目标有效性，失效则转入 RETURN
		if (this.state == STATE_THRUST) {
			if (this.currentTarget == null || !this.currentTarget.isAlive()
					|| this.currentTarget.distanceToSqr(owner) > SEARCH_RADIUS * SEARCH_RADIUS) {
				this.currentTarget = null;
				this.state = STATE_RETURN;
			}
		}

		if (this.state == STATE_THRUST) {
			this.tickThrust(home);
		} else {
			this.tickHoverOrReturn(owner, home);
		}
	}

	/** 无归属实例（/summon 等裸召唤路径）：原地悬停 + 刃尖朝下 + 绕长轴自转，展示剑的完整姿态。
	 *  没有主人可跟随视角/索敌/造成伤害（伤害来源需要 owner），因此不做任何移动与战斗。 */
	private void tickOrphanIdle() {
		this.setDeltaMovement(Vec3.ZERO);
		setVisualPitch(lerpAngle(getVisualPitch(), IDLE_PITCH_DEG, 0.2f));
		setVisualYaw(0.0f);
		float nextRoll = getVisualRoll() + IDLE_SPIN_DEG;
		if (nextRoll >= 360.0f) {
			nextRoll -= 360.0f;
		}
		setVisualRoll(nextRoll);
	}

	/** THRUST：先升空避让（剑刃朝上），再到位后剑刃直指敌人直线冲刺。 */
	private void tickThrust(Vec3 home) {
		LivingEntity target = this.currentTarget;
		LivingEntity owner = getSummoner();
		float yaw = owner.getYHeadRot();
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad); // 前向

		if (this.thrustPhase == PHASE_RISE) {
			// 升空：移到玩家上方偏后，剑刃朝上，避免随后冲刺刺穿召唤者
			this.thrustRiseTicks++;
			Vec3 riseTarget = new Vec3(
				owner.getX() - fx * 0.4,
				owner.getY() + BACK_Y_OFFSET + RISE_HEIGHT,
				owner.getZ() - fz * 0.4);
			this.setDeltaMovement(approach(this.position(), riseTarget, RISE_SPEED));

			setVisualPitch(lerpAngle(getVisualPitch(), 0.0f, 0.3f));   // 剑刃朝上
			setVisualRoll(lerp(getVisualRoll(), 0.0f, 0.3f));
			setVisualYaw(lerpAngle(getVisualYaw(), yaw, 0.3f));        // 水平朝向保持跟随玩家视角

			boolean reached = this.position().distanceToSqr(riseTarget) <= 0.25;
			if (reached || this.thrustRiseTicks >= RISE_TICKS) {
				startDashPhase(); // 收势：先慢速对准再全速突刺
			}
			return;
		}

		// 目标中心点：用于瞄准与接触判定（同一基准点，斜刺不因脚底坐标偏差而漏判）
		Vec3 targetCenter = new Vec3(
			target.getX(),
			target.getY() + target.getBbHeight() * 0.5,
			target.getZ());
		// 接触距离（剑尖锚定）：命中时轴心（旋转中心=剑身中点）到目标中心的距离
		//   = 目标半径 + (轴心→剑尖长 TIP_LENGTH) − 刺入深度 STAB_PENETRATION
		// 轴心停在此处 ⇔ 剑尖恰好刺入目标体表 STAB_PENETRATION，剑尖先到、护手/柄留在体外。
		double stopDist = target.getBbWidth() * 0.5 + TIP_LENGTH - STAB_PENETRATION;

		if (this.thrustPhase == PHASE_RETREAT) {
			// 刺中后回拉：剑尖始终对准敌人，向后拉开到 stopDist+RETREAT_DISTANCE 后再继续突刺。
			// 到位判定带容差(RETREAT_ARRIVE_EPSILON) + 超时(RETREAT_MAX_TICKS)双保险：
			// 旧严格小于+渐进逼近在目标移动时永远差一丝 → 卡回拉不攻击（2026-09-09 数据实证修复）。
			this.retreatTicks++;
			aimAt(targetCenter);
			Vec3 away = this.position().subtract(targetCenter);
			double awayLen = away.length();
			double retreatTargetDist = stopDist + RETREAT_DISTANCE;
			boolean arrived = awayLen >= retreatTargetDist - RETREAT_ARRIVE_EPSILON;
			boolean timedOut = this.retreatTicks >= RETREAT_MAX_TICKS;
			if (awayLen > 0.001 && !arrived && !timedOut) {
				double step = Math.min(RETREAT_SPEED, retreatTargetDist - awayLen);
				this.setDeltaMovement(away.scale(step / awayLen));
			} else {
				this.setDeltaMovement(Vec3.ZERO);
				startDashPhase(); // 收势：回拉到位/超时后先慢速对准再突刺
			}
			return;
		}

		// PHASE_DASH：剑刃直指敌人直线冲刺，位移以「接触距离」为终点钳制——
		// 轴心永不超过 stopDist，剑尖先到、命中即停，从根上杜绝穿体与"剑柄/剑身撞人"。
		Vec3 aim = targetCenter.subtract(this.position());
		double len = aim.length();

		// 冲刺/待刺时始终瞄准敌人（不缓转插值，避免剑身慢慢转向的"转弯/剑柄朝前"观感）
		aimAt(targetCenter);

		Vec3 dir = (len > 0.001) ? aim.scale(1.0 / len) : Vec3.ZERO;
		Vec3 move;
		if (this.attackCooldown > 0) {
			// 冷却中：缓压推进到「接触距离 + READY_GAP」即待刺，剑尖不贴目标体表
			double holdDist = stopDist + READY_GAP;
			double step = (len > holdDist) ? Math.min(len - holdDist, 0.35) : 0.0;
			move = dir.scale(step);
		} else {
			// 冲刺：单 tick 位移 = min(剩余接触距离, 冲速)，终点恰在接触距离，绝不越位。
			// 进入冲刺的头几 tick 用慢速（收势对准），让姿态在客户端插值中收敛后再全速突刺
			double speed = THRUST_SPEED;
			if (this.thrustSettleTicks > 0) {
				speed = THRUST_SETTLE_SPEED;
				this.thrustSettleTicks--;
			}
			double step = (len > stopDist) ? Math.min(len - stopDist, speed) : 0.0;
			move = dir.scale(step);
		}
		this.setDeltaMovement(move);

		// 命中判定（2026-09-09 重构为纯几何，彻底与相对速度解耦）：
		// 不再用「轴心到目标中心的距离 ≤ 阈值」——那要求位移把距离收敛到接触位才判伤，
		// 当剑与怪相对静止/同速、轴心停在接触位外一点点（怪形估算偏差/移动干扰）时永不收敛 → 贴住假死。
		// 改为每 tick 检查剑尖端点是否进入目标碰撞箱（外扩 TIP_HIT_ENVELOPE）：几何相交即判刺中，
		// 即使两者完全静止、只要剑尖已贴住怪，冷却一结束立刻再结算。位移钳制只负责「不穿体」表现。
		boolean cooldownReady = this.attackCooldown <= 0;
		boolean tipInBox = cooldownReady && tipTouchesTarget(target, dir);
		boolean hurtOk = false;
		if (tipInBox) {
			ServerLevel serverLevel = (ServerLevel) this.level();
			DamageSource source = owner instanceof Player player
				? serverLevel.damageSources().playerAttack(player)
				: serverLevel.damageSources().mobAttack(owner);
			hurtOk = target.hurtServer(serverLevel, source, HIT_DAMAGE);
			if (hurtOk) {
				target.igniteForSeconds(HIT_IGNITE_SECONDS);
				serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 1.4f);
			}
			this.attackCooldown = ATTACK_COOLDOWN_TICKS;
			this.thrustPhase = PHASE_RETREAT;
			this.retreatTicks = 0;
		}
	}

	/** 进入冲刺相位：置相位 + 重置收势计数（先慢速对准让客户端姿态收敛，再全速突刺）。 */
	private void startDashPhase() {
		this.thrustPhase = PHASE_DASH;
		this.thrustSettleTicks = THRUST_SETTLE_TICKS;
	}

	/** 判伤几何：剑尖端点（轴心沿当前朝向 dir 延伸 TIP_LENGTH 处）是否进入目标碰撞箱外扩包络。
	 *  用「箱内最近点距离」判定，任何目标尺寸/形状都精确；不依赖相对速度、不依赖中心距收敛。 */
	private boolean tipTouchesTarget(LivingEntity target, Vec3 dir) {
		if (dir.lengthSqr() < 1.0E-6) {
			return false;
		}
		AABB box = target.getBoundingBox().inflate(TIP_HIT_ENVELOPE);
		Vec3 tip = this.position().add(dir.scale(TIP_LENGTH));
		double cx = Math.max(box.minX, Math.min(tip.x, box.maxX));
		double cy = Math.max(box.minY, Math.min(tip.y, box.maxY));
		double cz = Math.max(box.minZ, Math.min(tip.z, box.maxZ));
		return tip.distanceToSqr(cx, cy, cz) < 1.0E-4;
	}

	/** HOVER / RETURN：移向「正后方」锚点；空闲姿态（剑刃朝下 + 绕长轴自转 + 水平朝向跟随视角）。 */
	private void tickHoverOrReturn(LivingEntity owner, Vec3 home) {
		if (this.state == STATE_HOVER) {
			// HOVER：精确锁定到背后锚点，玩家转身剑即时跟随，不走 deltaMovement 渐进，
			// 避免"转身可见飞剑在侧面/前方"的位移延迟
			this.setPos(home.x, home.y, home.z);
			this.setDeltaMovement(Vec3.ZERO);
		} else {
			// RETURN：从远处飞回，保持平滑移动
			this.setDeltaMovement(approach(this.position(), home, RETURN_SPEED));
			if (this.position().distanceToSqr(home) <= HOME_THRESHOLD * HOME_THRESHOLD) {
				this.state = STATE_HOVER;
			}
		}

		// 空闲姿态：剑刃竖直朝下 + 水平朝向跟随玩家视角 + 绕剑身长轴自转（钻头翻旋）。
		// 自转角每 tick +6°，归一化到 [0,360)：防长期浮点累积，且让客户端 rotLerp 最短弧插值方向正确
		setVisualPitch(lerpAngle(getVisualPitch(), IDLE_PITCH_DEG, 0.2f));
		setVisualYaw(lerpAngle(getVisualYaw(), owner.getYHeadRot(), 0.2f));
		float nextRoll = getVisualRoll() + IDLE_SPIN_DEG;
		if (nextRoll >= 360.0f) {
			nextRoll -= 360.0f;
		}
		setVisualRoll(nextRoll);
	}

	/** 正后方锚点：每 tick 按持有者头部朝向（视角）计算，确保第一人称转动视角时剑始终贴在镜头背面。 */
	private Vec3 homePosition(LivingEntity owner) {
		float yaw = owner.getYHeadRot();
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad); // 前向
		// 后方 = -前向
		double offX = -fx * BACK_DIST;
		double offZ = -fz * BACK_DIST;
		double backY = owner.getY() + BACK_Y_OFFSET;
		double bob = Math.sin(this.tickCount * 0.08) * 0.1;
		return new Vec3(owner.getX() + offX, backY + bob, owner.getZ() + offZ);
	}

	// ===================== 工具 =====================

	private void setVisualYaw(float v) {
		getEntityData().set(DATA_VISUAL_YAW, v);
	}

	private void setVisualPitch(float v) {
		getEntityData().set(DATA_VISUAL_PITCH, v);
	}

	private void setVisualRoll(float v) {
		getEntityData().set(DATA_VISUAL_ROLL, v);
	}

	/** 让剑尖（模型 +Y，经渲染器 yaw/pitch 定向后）指向目标中心。
	 *  pitch = acos(dy)∈[0,180]（0=竖直朝上、90=水平、180=竖直朝下）。
	 *  yaw 符号口径尚未定案（2026-09-09 讨论中，勿再单方修改）：此处先用数学惯例 atan2(dx,dz)
	 *  = 上一版基线。曾尝试取反为 atan2(-dx,dz)，实测「退化」，说明用户所述"反了"并非简单水平 180°，
	 *  待与用户对齐精确症状（正前方/正右方指向、悬停基线、剑身是否顺飞行方向）后一并修正。
	 *  roll 归零保证剑身笔直（剑脊不歪）。 */
	private void aimAt(Vec3 targetCenter) {
		Vec3 delta = targetCenter.subtract(this.position());
		double len = delta.length();
		if (len <= 0.001) {
			return;
		}
		double dy = delta.y / len;
		if (dy > 1.0) {
			dy = 1.0;
		}
		if (dy < -1.0) {
			dy = -1.0;
		}
		float aimPitch = (float) Math.toDegrees(Math.acos(dy));
		float aimYaw = (float) Math.toDegrees(Math.atan2(delta.x, delta.z));
		setVisualYaw(aimYaw);
		setVisualPitch(aimPitch);
		setVisualRoll(0.0f);
	}

	/** 限制单 tick 位移不超过 maxSpeed 的定向移动。 */
	private static Vec3 approach(Vec3 from, Vec3 to, double maxSpeed) {
		Vec3 delta = to.subtract(from);
		double len = delta.length();
		if (len < 0.001) {
			return Vec3.ZERO;
		}
		double step = Math.min(len, maxSpeed);
		return delta.scale(step / len);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** 取最短弧插值角度（结果落在 [-180,180)）。 */
	private static float lerpAngle(float a, float b, float t) {
		float d = (b - a) % 360.0f;
		if (d > 180.0f) d -= 360.0f;
		if (d < -180.0f) d += 360.0f;
		return a + d * t;
	}

	/** 召唤者 16 格半径内最近的敌对生物（排除召唤者本人）。 */
	private LivingEntity findNearestEnemy(LivingEntity owner) {
		ServerLevel serverLevel = (ServerLevel) this.level();
		AABB box = owner.getBoundingBox().inflate(SEARCH_RADIUS);
		List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
			e -> e instanceof Enemy && e.isAlive() && e != owner);
		return candidates.stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(owner)))
			.orElse(null);
	}

	// ===================== 持有者工具 =====================

	/** 查找飞剑的召唤者（支持玩家与人偶）。跨维度时返回 null → 飞剑消散。 */
	private LivingEntity getSummoner() {
		if (this.ownerUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		Entity entity = serverLevel.getEntity(this.ownerUuid);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** 判断飞剑是否归属指定实体（供 DollEntity 检查是否已有飞剑）。 */
	public boolean isOwnedBy(LivingEntity entity) {
		return this.ownerUuid != null && this.ownerUuid.equals(entity.getUUID());
	}

	/** 顶替规则：召唤新剑前移除该召唤者已有的旧剑。 */
	public static void replaceExisting(ServerLevel level, LivingEntity owner) {
		for (NetherFlyingSwordEntity existing : level.getEntitiesOfClass(NetherFlyingSwordEntity.class,
				owner.getBoundingBox().inflate(SEARCH_RADIUS),
				sword -> sword.ownerUuid != null && sword.ownerUuid.equals(owner.getUUID()))) {
			existing.discard();
		}
	}

	// ===================== 关闭原版物品行为 =====================

	/** 飞剑不可被拾取（pickupDelay=32767 之外的双保险）。 */
	@Override
	public void playerTouch(Player player) {
	}

	/** 飞剑不与任何实体互推 / 不被推动（2026-09-09 修复"顶牛卡住"）：
	 *  原版实体互推会让冲刺的剑被不可推动的高击退抗性怪物每 tick 顶回去，
	 *  表现为"剑已贴着怪但不出伤害、直到双方相对移动才恢复"。关闭后飞剑的位置/伤害完全由代码控制，
	 *  可自由贴近/停在怪物碰撞箱外的精确位置。 */
	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	/** 火焰/熔岩免疫（hurtServer 在 ItemEntity 为 final，这里从根上防燃烧毁剑）。 */
	@Override
	public boolean fireImmune() {
		return true;
	}

	// ===================== 序列化（跨存档/换维度保住归属） =====================

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.ownerUuid != null) {
			output.putString(OWNER_UUID_NBT_KEY, this.ownerUuid.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String ownerStr = input.getStringOr(OWNER_UUID_NBT_KEY, "");
		if (!ownerStr.isEmpty()) {
			try {
				this.ownerUuid = UUID.fromString(ownerStr);
			} catch (IllegalArgumentException ignored) {
			}
		}
	}
}
