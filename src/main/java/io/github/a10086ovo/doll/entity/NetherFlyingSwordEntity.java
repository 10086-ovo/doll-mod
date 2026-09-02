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
 * 飞行地狱剑 —— 召唤者蓄力召唤的伴生守护飞剑（注册见 DollMod.NETHER_FLYING_SWORD_ENTITY）。
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
	/** 移动速度（格/tick） */
	private static final double HOVER_SPEED = 0.35;
	private static final double RISE_SPEED = 0.5;
	private static final double THRUST_SPEED = 1.6;
	private static final double RETURN_SPEED = 0.6;
	/** 归位判定距离平方 */
	private static final double HOME_THRESHOLD = 0.4;
	/** 冲刺停留安全边距：停在目标碰撞箱外这么远，保证剑尖刺入而剑柄留在外面 */
	private static final double STOP_MARGIN = 0.3;
	/** 命中判定容差（格）：在“停刺距离”基础上再放宽一点，避免离散 tick 移动导致判定漏掉 */
	private static final double HIT_TOLERANCE = 0.4;
	/** 刺中后回拉距离（格）：剑刺中敌人后向后拉开一点，再继续下一次突刺 */
	private static final double RETREAT_DISTANCE = 1.8;
	/** 刺中后回拉速度（格/tick） */
	private static final double RETREAT_SPEED = 0.5;

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

	public NetherFlyingSwordEntity(EntityType<? extends NetherFlyingSwordEntity> type, Level level) {
		super(type, level);
		setupSword();
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

	/** 附魔光泽标记（供渲染器叠加 glint 层）。 */
	public boolean isFoil() {
		return this.getItem().hasFoil();
	}

	// ===================== 主逻辑 =====================

	@Override
	public void tick() {
		if (!this.level().isClientSide()) {
			this.tickSword();
		}
		// super 链保留：位置插值（xo/yo/zo）、move 方块碰撞、age 自增
		super.tick();
	}

	/** 服务端逻辑：状态机 + 姿态计算，deltaMovement 交给 super.tick() 的 move 执行。 */
	private void tickSword() {
		LivingEntity owner = getSummoner();
		// 召唤者不存在/死亡/移除：飞剑消散
		if (owner == null || !owner.isAlive() || owner.isRemoved()) {
			this.discard();
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
				this.thrustPhase = PHASE_DASH;
			}
			return;
		}

		// 目标中心点：用于瞄准与回拉判定
		Vec3 targetCenter = new Vec3(
			target.getX(),
			target.getY() + target.getBbHeight() * 0.5,
			target.getZ());

		if (this.thrustPhase == PHASE_RETREAT) {
			// 刺中后回拉：剑尖始终对准敌人，向后拉开一小段距离后再继续突刺
			aimAt(targetCenter);
			Vec3 away = this.position().subtract(targetCenter);
			double awayLen = away.length();
			double stopDist = target.getBbWidth() * 0.5 + STOP_MARGIN;
			double retreatTargetDist = stopDist + RETREAT_DISTANCE;
			if (awayLen > 0.001 && awayLen < retreatTargetDist) {
				double step = Math.min(RETREAT_SPEED, retreatTargetDist - awayLen);
				this.setDeltaMovement(away.scale(step / awayLen));
			} else {
				this.setDeltaMovement(Vec3.ZERO);
				this.thrustPhase = PHASE_DASH;
			}
			return;
		}

		// PHASE_DASH：剑刃直指敌人、直线冲刺、命中后回拉再刺
		Vec3 aim = targetCenter.subtract(this.position());
		double len = aim.length();

		// 冲刺/待刺时始终瞄准敌人，取消插值：避免剑身慢慢转向导致的"转弯/剑柄朝前"观感
		aimAt(targetCenter);

		// 移动：冷却中贴近缓压，否则刃尖直刺；
		// 目标距离减去停留边距才是有效移动距离，防止冲进目标体内导致"剑柄砸人"
		double stopDist = target.getBbWidth() * 0.5 + STOP_MARGIN;
		double effectiveLen = Math.max(0.0, len - stopDist);
		Vec3 move;
		if (this.attackCooldown > 0) {
			double step = (len > 2.5) ? Math.min(effectiveLen, 0.35) : 0.0;
			move = (len > 0.001) ? aim.scale(step / len) : Vec3.ZERO;
		} else {
			double step = Math.min(effectiveLen, THRUST_SPEED);
			move = (len > 0.001) ? aim.scale(step / len) : Vec3.ZERO;
		}
		this.setDeltaMovement(move);

		// 命中判定：以“目标中心”为基准（与瞄准/停刺用的同一个点），
		// 而不是用实体脚底坐标——否则从上方斜刺时剑尖已贴到敌人身上，但到脚底的距离仍偏大，导致漏判无伤害。
		// 命中后进入回拉阶段，不归位——持续刺击直到目标死亡或出界才回收
		double hitRange = stopDist + HIT_TOLERANCE;
		double hitSqr = hitRange * hitRange;
		if (this.attackCooldown <= 0 && this.position().distanceToSqr(targetCenter) <= hitSqr) {
			ServerLevel serverLevel = (ServerLevel) this.level();
			DamageSource source = owner instanceof Player player
				? serverLevel.damageSources().playerAttack(player)
				: serverLevel.damageSources().mobAttack(owner);
			if (target.hurtServer(serverLevel, source, HIT_DAMAGE)) {
				target.igniteForSeconds(HIT_IGNITE_SECONDS);
				serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 1.4f);
			}
			this.attackCooldown = ATTACK_COOLDOWN_TICKS;
			this.thrustPhase = PHASE_RETREAT;
			// 不切 RETURN：保持锁定，回拉后继续刺击同一目标，直至其死亡 / 离开索敌半径才回到背后
		}
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

		// 空闲姿态：剑刃竖直朝下 + 水平朝向跟随玩家视角 + 绕剑身长轴自转（钻头翻旋）
		setVisualPitch(lerpAngle(getVisualPitch(), IDLE_PITCH_DEG, 0.2f));
		setVisualYaw(lerpAngle(getVisualYaw(), owner.getYHeadRot(), 0.2f));
		setVisualRoll(getVisualRoll() + IDLE_SPIN_DEG);
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

	/** 让剑尖（模型 +Y）始终指向目标中心：水平 yaw + 仰俯 pitch 直接锁定，roll 归零保证剑身笔直。 */
	private void aimAt(Vec3 targetCenter) {
		Vec3 delta = targetCenter.subtract(this.position());
		double len = delta.length();
		if (len <= 0.001) {
			return;
		}
		double horiz = Math.hypot(delta.x, delta.z);
		float aimYaw = (float) Math.toDegrees(Math.atan2(delta.x, delta.z));
		float aimPitch = (float) Math.toDegrees(Math.atan2(horiz, delta.y)); // +Y 指向目标
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
