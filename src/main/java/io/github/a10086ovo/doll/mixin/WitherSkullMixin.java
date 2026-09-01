package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 人偶头颅弹 — 统一投射物 WitherSkull 的行为定制。
 * <p>
 * 下界人偶（NETHER）和末影人偶（ENDER）都发射 WitherSkull，通过 owner 的变体区分命中效果：
 * <ul>
 *   <li><b>NETHER</b>：禁爆炸 + 伤害 20 + 凋零替换为燃烧 5 秒</li>
 *   <li><b>ENDER</b>：禁爆炸 + 直接命中 2 点伤害 + 在命中点生成龙息云（reapplicationDelay 10 tick，判定频率翻倍）</li>
 * </ul>
 * 原版凋灵发射的 WitherSkull 不受影响。
 * <p>
 * <b>禁用爆炸</b>：{@code onHit} 中 {@code level.explode()} 被拦截，owner 为 DollEntity 时跳过。
 * ENDER 变体在跳过爆炸的同时生成龙息云（复刻原版 DragonFireball.onHit 逻辑）。
 * discard 在 explode 之后，不受影响——头颅仍会正常消失。
 * <p>
 * <b>自定义伤害</b>：{@code onHitEntity} 中 {@code hurtServer} 被 {@code @Redirect} 拦截。
 * 友军保护：非 {@link Enemy} 实体直接返回 false（不造成伤害）。
 * NETHER 替换为 {@link DollEntity#FIREBALL_DAMAGE}，ENDER 返回 2（直接命中伤害 + 龙息云持续伤害）。
 * <p>
 * <b>凋零替换</b>：{@code onHitEntity} 末尾的 {@code addEffect}（凋零 I）被拦截。
 * 友军保护：非 {@link Enemy} 实体跳过一切效果。
 * NETHER 替换为燃烧 5 秒，ENDER 跳过（龙息云已在 onHit 中生成）。
 */
@Mixin(WitherSkull.class)
public class WitherSkullMixin {

	@Redirect(
		method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
		)
	)
	private void dollNoExplode(Level level, Entity source, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction) {
		WitherSkull self = (WitherSkull)(Object)this;
		Entity owner = self.getOwner();
		if (owner instanceof DollEntity doll) {
			if (doll.isEnderDoll()) {
				// 末影人偶：在命中点生成龙息云
				dollMod$spawnBreathCloud(level, self, doll);
			}
			// NETHER 和 ENDER 都跳过爆炸，discard 在原方法中 explode 后面正常执行
		} else {
			level.explode(source, x, y, z, radius, fire, interaction);
		}
	}

	@Redirect(
		method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean dollSkullDamage(Entity target, ServerLevel level, DamageSource source, float amount) {
		WitherSkull self = (WitherSkull)(Object)this;
		Entity owner = self.getOwner();
		if (owner instanceof DollEntity doll) {
			// 友军保护：非敌对生物不受直接命中伤害
			if (!(target instanceof Enemy)) return false;
			float damage = doll.isEnderDoll() ? 20.0f : DollEntity.FIREBALL_DAMAGE;
			return target.hurtServer(level, source, damage);
		}
		return target.hurtServer(level, source, amount);
	}

	@Redirect(
		method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean dollReplaceWitherEffect(LivingEntity target, MobEffectInstance effect, Entity source) {
		WitherSkull self = (WitherSkull)(Object)this;
		Entity owner = self.getOwner();
		if (owner instanceof DollEntity doll) {
			// 友军保护：非敌对生物不受任何效果影响
			if (!(target instanceof Enemy)) return false;
			if (doll.isEnderDoll()) {
				// 末影人偶：跳过凋零和燃烧，龙息云已在 onHit 中生成
				return false;
			}
			// 下界人偶：跳过凋零效果，改为点燃目标 5 秒；手持地狱剑翻倍 10 秒（灼烧伤害×2）
			target.igniteForSeconds(doll.hasNetherSwordEquipped() ? 10.0f : 5.0f);
			return false;
		}
		return target.addEffect(effect, source);
	}

	/**
	 * 在命中点生成龙息云（复刻原版 DragonFireball.onHit 的龙息云创建逻辑）。
	 * <p>
	 * 龙息云参数与原版一致：半径 3、持续 600 tick（30 秒）、即时伤害 II、龙息粒子。
	 * owner 设置为 DollEntity，AreaEffectCloudMixin 据此做友军保护。
	 */
	@Unique
	private static void dollMod$spawnBreathCloud(Level level, WitherSkull skull, DollEntity owner) {
		if (!(level instanceof ServerLevel serverLevel)) return;

		AreaEffectCloud cloud = new AreaEffectCloud(level, skull.getX(), skull.getY(), skull.getZ());
		cloud.setOwner(owner);
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f));
		cloud.setRadius(3.0f);
		cloud.setDuration(600);
		cloud.setRadiusPerTick((7.0f - cloud.getRadius()) / cloud.getDuration());
		cloud.setPotionDurationScale(0.25f);
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));

		// 缩短伤害判定间隔：原版默认 20 tick（每秒判定一次），改为 10 tick（每 0.5 秒判定一次）
		((AreaEffectCloudAccessor) cloud).dollMod$setReapplicationDelay(10);

		level.levelEvent(2006, skull.blockPosition(), skull.isSilent() ? -1 : 1);
		serverLevel.addFreshEntity(cloud);
	}
}
