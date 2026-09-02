package io.github.a10086ovo.doll.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;

/**
 * 荆棘盾牌 —— 森林主题防御装备。
 * <p>
 * 基础功能等同原版盾牌：右键格挡时完全免疫伤害，消耗耐久。
 * 特殊能力：格挡成功时反弹荆棘伤害给攻击者。
 * <p>
 * 格挡逻辑（无伤取消伤害 + 耐久消耗 + 音效 + 反伤）由
 * {@link io.github.a10086ovo.doll.mixin.ThornsShieldMixin} 在 hurtServer HEAD 注入实现。
 * <p>
 * 设计定位：森林人偶的主题配套装备，也可供玩家使用。
 * 耐久 672（×2，等同铁剑两倍），可附魔（enchantability=9，等同原版盾牌，可附魔耐久/经验修补/荆棘）。
 */
public class ThornsShieldItem extends ShieldItem {

	/** 格挡时反弹的伤害值 */
	public static final float THORNS_DAMAGE = 2.0f;
	/** 荆棘反伤的最大范围 */
	public static final double THORNS_RANGE = 3.0;

	public ThornsShieldItem(Properties properties) {
		// 关键：声明 blocks_attacks 组件，使 getUseAnimation() 返回 BLOCK、getUseDuration() 返回 72000。
		// 否则第三方举盾手臂姿态（AvatarRenderer.getArmPose → ArmPose.BLOCK）不会触发，
		// 且持握时长为 0 导致举盾状态无法持续，表现为"手臂不抬 + 盾牌位置偏离原版"。
		// 玩家侧格挡仍由 ThornsShieldMixin 完全接管（hurtServer HEAD 取消伤害），不会双算。
		super(properties.component(DataComponents.BLOCKS_ATTACKS,
			new BlocksAttacks(0.25f, 1.0f, List.of(),
				BlocksAttacks.ItemDamageFunction.DEFAULT,
				Optional.empty(), Optional.empty(), Optional.empty())));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/**
	 * 判断是否正在使用（格挡中）。
	 */
	public static boolean isBlocking(Player player) {
		return player.isUsingItem()
			&& player.getUseItem().getItem() instanceof ThornsShieldItem;
	}

	/**
	 * 执行荆棘反伤。
	 * 由 {@link io.github.a10086ovo.doll.mixin.ThornsShieldMixin} 在 LivingEntity.hurtServer 中调用。
	 *
	 * @param level  服务端世界
	 * @param victim 持盾格挡的玩家
	 * @param source 伤害来源
	 * @param amount 原始伤害量
	 */
	public static void applyThorns(ServerLevel level, LivingEntity victim,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		// 只有玩家持盾时才触发
		if (!(victim instanceof Player player)) return;
		if (!isBlocking(player)) return;

		// 寻找攻击者（直接攻击者或伤害来源实体）
		Entity attacker = source.getEntity();
		if (attacker == null || !(attacker instanceof LivingEntity livingAttacker)) return;
		if (attacker == victim) return; // 不反伤自己

		// 距离检查：攻击者必须在 3 格内
		if (!attacker.closerThan(victim, THORNS_RANGE)) return;

		// 造成荆棘伤害（无视护甲，类型为荆棘）
		net.minecraft.world.damagesource.DamageSource thornsSource =
			level.damageSources().thorns(victim);
		attacker.hurtServer(level, thornsSource, THORNS_DAMAGE);

		// 视觉反馈：生成伤害粒子
		if (level instanceof ServerLevel sv) {
			net.minecraft.core.particles.ParticleOptions particle =
				net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR;
			sv.sendParticles(particle,
				attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
				3, 0.3, 0.3, 0.3, 0.1);
		}

		// 玩家荆棘反伤：给攻击者施加无限中毒效果（-1 持续 = 永不消退）
		livingAttacker.addEffect(new MobEffectInstance(MobEffects.POISON, -1, 0, false, false, false));

		// 不额外消耗耐久：反伤是被动能力，盾牌格挡本身的耐久消耗由原版逻辑处理
	}
}
