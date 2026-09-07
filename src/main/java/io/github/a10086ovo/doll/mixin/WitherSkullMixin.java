package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.entity.DollEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 人偶头颅弹 — 统一投射物 WitherSkull 的行为定制。
 * <p>
 * 下界人偶（NETHER）和末影人偶（ENDER）都发射 WitherSkull，通过 owner 的变体区分命中效果：
 * <ul>
 *   <li><b>NETHER</b>：禁爆炸 + 直接命中 20 + 命中点 3 格 AoE 20 + 点燃 5 秒（手持下界剑翻倍 10 秒）</li>
 *   <li><b>ENDER</b>：禁爆炸 + 直接命中 20 + 命中点 3 格 AoE 20（不点燃）——
 *       龙息云机制已移除，彻底对标下界人偶的小范围伤害模型，弹道投射物可被遮挡</li>
 * </ul>
 * 原版凋灵发射的 WitherSkull 不受影响。
 * <p>
 * <b>禁爆炸</b>：{@code onHit} 中 {@code level.explode()} 被拦截，owner 为 DollEntity 时跳过，
 * 改为在命中点施加"只伤害敌对生物"的小范围 AoE（discard 在 explode 之后，头颅仍正常消失）。
 * <p>
 * <b>自定义伤害</b>：{@code onHitEntity} 中 {@code hurtServer} 被 {@code @Redirect} 拦截。
 * 友军保护：非 {@link Enemy} 实体直接返回 false（不造成伤害），直接命中统一 20 伤害。
 * <p>
 * <b>凋零替换</b>：{@code onHitEntity} 末尾的 {@code addEffect}（凋零 I）被拦截。
 * 友军保护：非 {@link Enemy} 实体跳过一切效果。NETHER 改为点燃，ENDER 跳过（无状态附加）。
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
			// 下界/末影人偶统一：命中点小范围 AoE（只伤害敌对生物）；差异仅在是否点燃
			boolean ignite = doll.isNetherDoll();
			dollMod$spawnPointBlast(level, self, doll, ignite);
			// 跳过爆炸，discard 在原方法中 explode 后面正常执行
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
			float damage = DollEntity.FIREBALL_DAMAGE; // 下界烈焰弹与末影弹直击共用 20 档
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
				// 末影人偶：跳过凋零/点燃（无状态附加，小范围 AoE 已由 dollNoExplode 处理）
				return false;
			}
			// 下界人偶：跳过凋零效果，改为点燃目标 5 秒；手持下界剑翻倍 10 秒（灼烧伤害×2）
			target.igniteForSeconds(doll.hasNetherSwordEquipped() ? 10.0f : 5.0f);
			return false;
		}
		return target.addEffect(effect, source);
	}

	/**
	 * 人偶头颅弹命中点的小范围爆破（不破坏方块）：对命中点 3 格半径内所有敌对生物
	 * 造成 {@link DollEntity#FIREBALL_DAMAGE}（20）范围伤害。下界人偶附带点燃 5 秒
	 * （手持下界剑翻倍），末影人偶不点燃——与直接命中伤害（同 20 档）叠加，
	 * 形成「直接命中高伤害 + 命中点小范围 AoE」的统一伤害模型。
	 * 粒子按主题区分：下界火焰/岩浆，末影传送门粒子。
	 */
	@Unique
	private static void dollMod$spawnPointBlast(Level level, WitherSkull skull, DollEntity owner, boolean ignite) {
		if (!(level instanceof ServerLevel serverLevel)) return;

		double x = skull.getX();
		double y = skull.getY();
		double z = skull.getZ();
		float radius = 3.0f;
		DamageSource source = owner.damageSources().mobAttack(owner);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius),
				e -> e != owner && e.isAlive() && e instanceof Enemy)) {
			target.hurtServer(serverLevel, source, DollEntity.FIREBALL_DAMAGE);
			if (ignite) {
				target.igniteForSeconds(5.0f);
			}
		}

		if (ignite) {
			serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 60, 2.0, 2.0, 2.0, 0.04);
			serverLevel.sendParticles(ParticleTypes.LAVA, x, y, z, 20, 1.5, 1.5, 1.5, 0.02);
		} else {
			serverLevel.sendParticles(ParticleTypes.PORTAL, x, y, z, 60, 2.0, 2.0, 2.0, 0.04);
		}
		serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.2f);
	}
}
