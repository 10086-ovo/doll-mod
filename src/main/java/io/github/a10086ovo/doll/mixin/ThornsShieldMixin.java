package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.item.ThornsShieldItem;
import io.github.a10086ovo.doll.util.ThornsShieldContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 荆棘盾牌 Mixin。
 * <p>
 * 在 LivingEntity.hurtServer HEAD 注入：
 * <ul>
 *   <li>玩家持荆棘盾牌格挡时：完全取消伤害（无伤格挡），消耗耐久，播放音效，荆棘反伤</li>
 *   <li>森林人偶副手持荆棘盾牌时：100% 反伤（不格挡伤害）</li>
 * </ul>
 * <p>
 * 注意：thorns 伤害类型属于 BYPASSES_SHIELD 标签，荆棘反伤不会触发攻击者的盾牌格挡，无递归风险。
 */
@Mixin(LivingEntity.class)
public class ThornsShieldMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void onHurtWithThornsShield(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;

		// 只在服务端处理
		if (self.level().isClientSide()) return;

		// 不格挡不受盾牌阻挡的伤害类型（魔法、火焰、窒息等）
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) return;

		Entity attacker = source.getEntity();
		if (attacker == null || attacker == self) return;
		if (!(attacker instanceof LivingEntity)) return;

		// 情况1：玩家持荆棘盾牌格挡 —— 无伤格挡 + 荆棘反伤
		if (self instanceof Player player) {
			if (!ThornsShieldItem.isBlocking(player)) return;

			// 荆棘盾牌格挡：完全取消伤害（无伤格挡）
			cir.setReturnValue(false);

			// 消耗盾牌耐久（与原版 hurtCurrentlyUsedShield 逻辑一致：伤害 >= 3 才扣耐久）
			ItemStack shield = player.getUseItem();
			if (!shield.isEmpty() && amount >= 3.0F) {
				int durabilityCost = 1 + Mth.floor(amount);
				EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
					? EquipmentSlot.MAINHAND
					: EquipmentSlot.OFFHAND;
				shield.hurtAndBreak(durabilityCost, self, slot);
				if (shield.isEmpty()) {
					player.stopUsingItem();
				}
			}

			// 播放原版盾牌格挡音效
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F + self.getRandom().nextFloat() * 0.4F);

			// 执行荆棘反伤（期间置位：荆棘应穿透末影斧 80% 闪避，否则攻击者持末影斧时荆棘几乎无效）
			ThornsShieldContext.setThornsReflecting(true);
			try {
				ThornsShieldItem.applyThorns(level, self, source, amount);
			} finally {
				ThornsShieldContext.setThornsReflecting(false);
			}
			return;
		}

		// 情况2：森林人偶副手持荆棘盾牌 —— 100% 反伤（不格挡伤害）
		if (self instanceof DollEntity doll && doll.isForestDoll()) {
			var offhandStack = doll.getItemBySlot(EquipmentSlot.OFFHAND);
			if (offhandStack.getItem() instanceof ThornsShieldItem) {
				// 播放原版盾牌格挡音效
				level.playSound(null, doll.getX(), doll.getY(), doll.getZ(),
					SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.8F + doll.getRandom().nextFloat() * 0.4F);

			// 100% 反伤（期间置位：荆棘应穿透末影斧 80% 闪避）
			float reflectDamage = amount * 1.0f;
			DamageSource reflectSource = level.damageSources().thorns(doll);
			ThornsShieldContext.setThornsReflecting(true);
			try {
				attacker.hurtServer(level, reflectSource, reflectDamage);
			} finally {
				ThornsShieldContext.setThornsReflecting(false);
			}
			}
		}
	}
}
