package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import io.github.a10086ovo.doll.entity.DollVariant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽匿人偶"掠夺者"天赋实现：
 * <ul>
 *   <li><b>自带抢夺 III</b>：当 {@link EnchantmentHelper#getEnchantmentLevel} 查询
 *       攻击者身上的抢夺附魔等级时，若攻击者是幽匿人偶则返回
 *       <code>max(原值, 3)</code>。影响 26.2 掉落表中
 *       {@code random_chance_with_enchanted_bonus} 条件和
 *       {@code enchanted_count_increase} 函数——它们都通过
 *       <code>ATTACKING_ENTITY</code> + <code>getEnchantmentLevel</code>
 *       获取抢夺等级来决定额外掉落概率和数量。</li>
 *   <li><b>击杀经验×3</b>：当 {@link EnchantmentHelper#processMobExperience}
 *       处理完原版附魔经验加成后，若攻击者是幽匿人偶则将最终经验值×3。</li>
 * </ul>
 * <p>
 * 两处注入互不干扰：getEnchantmentLevel 影响掉落物，
 * processMobExperience 影响经验球——后者内部走 runIterationOnEquipment →
 * getItemEnchantmentLevel（逐件装备检查），不经过 getEnchantmentLevel。
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {

	/**
	 * 幽匿人偶自带抢夺 III：
	 * 当查询攻击者身上 looting 附魔等级时，幽匿人偶返回 max(原值, 3)。
	 */
	@Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
	private static void wardenDollInnateLooting(
			Holder<Enchantment> enchantment,
			LivingEntity entity,
			CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof DollEntity doll && doll.getDollVariant() == DollVariant.WARDEN) {
			if (enchantment.is(Enchantments.LOOTING)) {
				int original = cir.getReturnValue();
				if (original < 3) {
					cir.setReturnValue(3);
				}
			}
		}
	}

	/**
	 * 幽匿人偶击杀经验×3：
	 * 原版 processMobExperience 已根据攻击者装备上的附魔（如 looting）
	 * 计算了经验加成，此处在此基础上再×3。
	 */
	@Inject(method = "processMobExperience", at = @At("RETURN"), cancellable = true)
	private static void wardenDollTripleExperience(
			ServerLevel serverLevel,
			Entity attacker,
			Entity target,
			int baseXp,
			CallbackInfoReturnable<Integer> cir) {
		if (attacker instanceof DollEntity doll && doll.getDollVariant() == DollVariant.WARDEN) {
			cir.setReturnValue(cir.getReturnValue() * 3);
		}
	}
}
