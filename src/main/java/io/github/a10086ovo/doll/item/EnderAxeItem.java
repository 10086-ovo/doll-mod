package io.github.a10086ovo.doll.item;

import io.github.a10086ovo.doll.entity.ThrownEnderAxe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 末影斧 —— 兼具斧头近战与三叉戟投掷的混合武器。
 * <p>
 * 近战：伤害 10，可剥皮原木。
 * 投掷：长按右键蓄力，松手扔出 {@link ThrownEnderAxe}（命中基础伤害 10），带忠诚附魔时可回归。
 * <p>
 * 特有能力：
 * <ul>
 *   <li>任意持用者（玩家或人偶）近战命中斩杀 30% 血以下目标（postHurtEnemy 中判定；
 *       末影人偶另有天赋向处决：不持斧 25%、持斧同样取 30%，见 EnderDollTalent，双路径先后判定无冲突）</li>
 *   <li>玩家专属动作：长按右键蓄力投掷（{@link ThrownEnderAxe}）；抛出的末影斧命中同样带 30%
 *       斩杀（玩家与人偶的投掷均触发）</li>
 *   <li>玩家专属被动：持有时 80% 闪避（主手/副手均生效，由 LivingEntityDodgeMixin 处理）；
 *       人偶不叠加——末影人偶保留自身变体闪避天赋</li>
 * </ul>
 */
public class EnderAxeItem extends net.minecraft.world.item.AxeItem {

	public static final int THROW_THRESHOLD_TIME = 10;
	public static final float PROJECTILE_SHOOT_POWER = 2.5f;
	private static final float EXECUTE_THRESHOLD = 0.3f;

	public EnderAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
		super(material, attackDamage, attackSpeed, properties);
	}

	// ===================== 投掷逻辑（复刻 TridentItem） =====================

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		int chargeTime = this.getUseDuration(stack, entity) - timeLeft;
		if (chargeTime < THROW_THRESHOLD_TIME) {
			return false;
		}
		if (stack.nextDamageWillBreak()) {
			return false;
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);

		if (level instanceof ServerLevel serverLevel) {
			stack.hurtWithoutBreaking(1, player);
			ItemStack consumed = stack.consumeAndReturn(1, player);
			ThrownEnderAxe thrownAxe = new ThrownEnderAxe(serverLevel, player, consumed);
			thrownAxe.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, PROJECTILE_SHOOT_POWER, 1.0f);
			// 创造模式下设为 CREATIVE_ONLY，捡起时只 discard 不往背包塞新物品（对齐三叉戟）
			if (player.hasInfiniteMaterials()) {
				thrownAxe.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			}
			serverLevel.addFreshEntity(thrownAxe);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	// ===================== 斩杀逻辑（任意持用者近战命中） =====================

	// 注：26.2 已移除 Item.canApplyAtEnchantingTable / isBookEnchantable，
	// 附魔适用性改由附魔定义自身决定。末影斧不再覆写此方法。
	// 引雷（Channeling）落雷由 ThrownEnderAxe.onHitEntity 在命中时读取物品附魔触发，
	// 若斧头通过任一途径携带 Channeling 即生效。

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		// 任意持用者近战命中即触发 30% 斩杀（玩家与人偶普适）。末影人偶的天赋向处决
		//（EnderDollTalent）与本路径并存：先斩先死，后判对已死目标为空操作，不重复结算。
		if (target.isAlive() && target.getHealth() > 0.0f) {
			float healthRatio = target.getHealth() / target.getMaxHealth();
			if (healthRatio <= EXECUTE_THRESHOLD) {
				if (target.level() instanceof ServerLevel serverLevel) {
					target.hurtServer(serverLevel,
						target.damageSources().mobAttack(attacker), Float.MAX_VALUE);
				}
			}
		}
		super.postHurtEnemy(stack, target, attacker);
	}
}
