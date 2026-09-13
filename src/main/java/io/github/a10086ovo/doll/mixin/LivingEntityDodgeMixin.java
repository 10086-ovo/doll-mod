package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.EnderAxeItem;
import net.minecraft.server.level.ServerLevel;
import io.github.a10086ovo.doll.util.ThornsShieldContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末影斧 80% 闪避：
 * <p>
 * 当玩家（仅 Player，不含人偶）主手或副手持有 {@link EnderAxeItem} 时，
 * 受到伤害有 80% 概率完全免伤。
 * <p>
 * 人偶持有末影斧时不触发此 Mixin（人偶保留自身 ENDER 变体闪避），
 * 玩家的闪避与人偶的闪避互不叠加。
 * <p>
 * 26.2 中 Player 覆写了 hurtServer，ServerPlayer 又再次覆写；
 * 注入 LivingEntity.hurtServer 会被 super 链最终调用，但改注 Player.hurtServer
 * 可确保闪避在 Player 难度缩放等逻辑之前生效。
 */
@Mixin(Player.class)
public class LivingEntityDodgeMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void enderAxeDodge(ServerLevel level,
			DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player)(Object) this;
		// 创造/旁观模式不触发闪避音效（这些模式下本就不受常规战斗伤害）
		if (player.isCreative() || player.isSpectator()) return;
		// 荆棘反伤不闪避：荆棘本应克制近战，攻击者持末影斧时不应让荆棘失效
		if (ThornsShieldContext.isThornsReflecting()) return;
		// 只拦截有攻击来源的伤害（玩家/怪物攻击），环境伤害正常结算
		if (source.getEntity() == null) return;

		// 检查主手和副手是否持有末影斧
		ItemStack mainHand = player.getInventory().getSelectedItem();
		ItemStack offHand = player.getInventory().getItem(Inventory.SLOT_OFFHAND);
		if (!(mainHand.getItem() instanceof EnderAxeItem)
				&& !(offHand.getItem() instanceof EnderAxeItem)) {
			return;
		}

		// 80% 闪避概率
		if (player.getRandom().nextFloat() < 0.80f) {
			// 闪避音效
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 0.5f, 1.5f);
			cir.setReturnValue(false);
		}
	}
}
