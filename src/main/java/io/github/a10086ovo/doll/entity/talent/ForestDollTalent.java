package io.github.a10086ovo.doll.entity.talent;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.a10086ovo.doll.entity.DollEntity;

/**
 * 森林人偶天赋（原 DollEntity 的 applyForestVineAura / applyForestMarkAura /
 * applyForestAnimalAttract / applyForestRegenAura 迁入）：
 * <ul>
 *   <li>藤蔓缠绕：每 20 tick、半径 16 格——范围敌方生物（Enemy）缓慢 IV；</li>
 *   <li>威胁标记：每 20 tick、半径 32 格、仅主人存活——陆地敌对生物（isForestMobType）光灵式高亮；</li>
 *   <li>友善吸引：每 10 tick、半径 8 格——未驯服动物（isForestAnimalType）满速走向人偶；</li>
 *   <li>主人回血：每 20 tick、半径 16 格、仅主人——生命恢复 I。</li>
 * </ul>
 */
public class ForestDollTalent extends SpecialDollTalent {

	private int forestVineCooldown = 0;
	private int forestMarkCooldown = 0;
	private int forestAttractCooldown = 0;
	private int forestRegenCooldown = 0;

	public ForestDollTalent() {
		super(false);
	}

	/** 藤蔓缠绕：范围敌方生物缓慢 IV（原"敌怪安抚"仇恨豁免已移除，不在此清仇恨）。 */
	private void vineAura(DollEntity doll) {
		if (forestVineCooldown-- > 0) {
			return;
		}
		forestVineCooldown = 20; // 每 20 tick（1 秒）刷新
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 16.0;
		AABB box = DollTalent.auraBox(center, radius);
		// 重平衡：藤蔓缠绕覆盖范围内所有敌方生物（Enemy）AoE，不再限于森林列表；
		// 缓慢 IV（amp3）。原「中毒」已移除——毒伤移交副手荆棘盾的反伤（ThornsShieldMixin 单独处理）。
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

	/** 威胁标记：光灵箭式 GLOWING 描边高亮陆地敌对生物（半径 32 格），仅主人存活时生效。 */
	private void markAura(DollEntity doll) {
		if (forestMarkCooldown-- > 0) {
			return;
		}
		forestMarkCooldown = 20; // 每 20 tick 刷新一次高亮
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Player owner = doll.getOwnerPlayer();
		if (owner == null || !owner.isAlive()) {
			return;
		}
		double radius = 32.0;
		Vec3 center = doll.getAuraCenter();
		AABB box = DollTalent.auraBox(center, radius);
		List<Mob> mobs = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive()
		);
		for (Mob mob : mobs) {
			if (mob.position().distanceToSqr(center) > radius * radius) {
				continue;
			}
			if (!DollEntity.isForestMobType(mob.getType())) {
				continue;
			}
			// 光灵箭式高亮：白色描边（持续 3 秒，每 20 tick 重刷，离开范围自然过期）
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
		}
	}

	/** 友善动物吸引：未驯服动物以满速朝人偶移动（对齐原版 TemptGoal 手持小麦吸引，不繁殖不强制跟随）。 */
	private void animalAttract(DollEntity doll) {
		if (forestAttractCooldown-- > 0) {
			return;
		}
		forestAttractCooldown = 10; // 每 10 tick 刷新一次导航目标（足够顺滑且低开销）
		if (!(doll.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 8.0;
		AABB box = DollTalent.auraBox(center, radius);
		List<Mob> animals = serverLevel.getEntities(
			EntityTypeTest.forClass(Mob.class),
			box,
			mob -> mob.isAlive() && DollEntity.isForestAnimalType(mob.getType())
		);
		Vec3 target = doll.position();
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

	/** 主人回血 I（仅主人受益）。 */
	private void regenAura(DollEntity doll) {
		if (forestRegenCooldown-- > 0) {
			return;
		}
		forestRegenCooldown = 20; // 每 20 tick 给范围内主人刷新增益
		if (doll.level().isClientSide()) {
			return;
		}
		Player owner = doll.getOwnerPlayer();
		if (owner == null || owner.isSpectator()) {
			return;
		}
		Vec3 center = doll.getAuraCenter();
		double radius = 16.0;
		if (owner.position().distanceToSqr(center) > radius * radius) {
			return; // 主人不在光环内，不刷新（已有效果将自然过期）
		}
		// 生命恢复 I（amplifier 0 = 等级 I），持续时间 200 tick（10 秒），每 20 tick 重刷
		owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, false));
	}

	@Override
	public void tickAura(DollEntity doll) {
		vineAura(doll);
		markAura(doll);
		animalAttract(doll);
		regenAura(doll);
	}
}
