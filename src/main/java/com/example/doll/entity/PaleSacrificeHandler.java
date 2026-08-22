package com.example.doll.entity;

import com.example.doll.DollMod;
import com.example.doll.DollModConstants;
import com.example.doll.item.DollSpawnEggItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 苍白人偶献祭处理器。
 * <p>
 * 当玩家受到致命伤害即将死亡时，按以下优先级触发献祭：
 * <ol>
 *   <li>附近 16 格内已放出的苍白人偶实体 → 击杀实体</li>
 *   <li>玩家物品栏中命名过的苍白人偶蛋 → 将蛋标记为失效</li>
 * </ol>
 * 命名过的蛋视为一种不死图腾：玩家死亡时触发，蛋变为失效状态（不消失）。
 * 未命名的蛋无法触发献祭。
 */
public class PaleSacrificeHandler {

	private static final Identifier PALE_SACRIFICE_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("doll-mod", "pale_sacrifice");
	private static final double PALE_SACRIFICE_BONUS = 100.0;
	private static final int PALE_SACRIFICE_DURATION_TICKS = 1200; // 60 seconds
	private static final double PALE_SACRIFICE_SEARCH_RADIUS = 16.0;

	private static final ConcurrentHashMap<UUID, Long> paleSacrificeExpirations = new ConcurrentHashMap<>();

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(PaleSacrificeHandler::onAllowDeath);
		ServerTickEvents.END_SERVER_TICK.register(PaleSacrificeHandler::onEndTick);
	}

	private static boolean onAllowDeath(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source, float damageAmount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		// 伤害类型绕过无敌（如 /kill、虚空）时不触发献祭
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return true;
		}

		// 优先搜索附近放出的苍白人偶实体
		DollEntity paleDoll = findNearbyPaleDoll(serverLevel, player);
		if (paleDoll != null) {
			triggerSacrifice(serverLevel, player);
			DollMod.LOGGER.info("[PaleSacrifice] Doll {} sacrificed for player {}",
				paleDoll.getUUID(), player.getName().getString());
			paleDoll.kill(serverLevel);
			return false;
		}

		// 其次搜索物品栏中命名过的苍白人偶蛋（未放出的形态）
		ItemStack namedEgg = findNamedPaleDollEggInInventory(player);
		if (namedEgg != null) {
			triggerSacrifice(serverLevel, player);
			DollMod.LOGGER.info("[PaleSacrifice] Named egg sacrificed for player {}",
				player.getName().getString());
			invalidateEgg(namedEgg);
			return false;
		}

		return true; // 未找到苍白人偶，正常死亡
	}

	/**
	 * 触发献祭公共效果（音效、视觉、药水、血量提升）。
	 */
	private static void triggerSacrifice(ServerLevel serverLevel, ServerPlayer player) {
		// 1. 播放不死图腾音效
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

		// 2. 触发不死图腾视觉特效（客户端播放手持图腾动画）
		serverLevel.broadcastEntityEvent(player, (byte) 35);

		// 3. 应用不死图腾药水效果（恢复 II、抗性、吸收、火焰抗性等）
		DeathProtection.TOTEM_OF_UNDYING.applyEffects(ItemStack.EMPTY, player);

		// 4. 添加 +100 临时最大生命值（20 → 120）
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			AttributeModifier modifier = new AttributeModifier(
				PALE_SACRIFICE_MODIFIER_ID,
				PALE_SACRIFICE_BONUS,
				AttributeModifier.Operation.ADD_VALUE
			);
			maxHealth.addOrUpdateTransientModifier(modifier);
		}

		// 5. 恢复至满血（120）
		player.setHealth(120.0f);
		player.invulnerableTime = 40; // 2 秒无敌帧防止连续触发

		// 6. 记录过期时间
		long expireTick = serverLevel.getGameTime() + PALE_SACRIFICE_DURATION_TICKS;
		paleSacrificeExpirations.put(player.getUUID(), expireTick);
	}

	private static DollEntity findNearbyPaleDoll(ServerLevel serverLevel, Player player) {
		AABB searchBox = AABB.ofSize(
			player.position(),
			PALE_SACRIFICE_SEARCH_RADIUS * 2,
			PALE_SACRIFICE_SEARCH_RADIUS * 2,
			PALE_SACRIFICE_SEARCH_RADIUS * 2
		);
		List<DollEntity> dolls = serverLevel.getEntities(
			EntityTypeTest.forClass(DollEntity.class),
			searchBox,
			doll -> !doll.isDeadOrDying()
				&& doll.getDollVariant() == DollVariant.PALE
				&& doll.isOwnedBy(player)
		);
		if (dolls.isEmpty()) {
			return null;
		}
		// 优先选择距离玩家最近的
		DollEntity nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (DollEntity doll : dolls) {
			double distSq = doll.distanceToSqr(player);
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = doll;
			}
		}
		return nearest;
	}

	/**
	 * 在玩家物品栏中搜索命名过的苍白人偶蛋（铁砧命名才算，未命名不触发）。
	 * 已失效的蛋跳过。
	 */
	private static ItemStack findNamedPaleDollEggInInventory(Player player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty() || !stack.is(DollMod.PALE_DOLL_EGG)) {
				continue;
			}
			// 必须经过铁砧命名（有 CUSTOM_NAME 组件）
			if (!stack.has(DataComponents.CUSTOM_NAME)) {
				continue;
			}
			// 已失效的蛋跳过
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().getBooleanOr(DollSpawnEggItem.INVALIDATED_NBT_KEY, false)) {
				continue;
			}
			return stack;
		}
		return null;
	}

	/**
	 * 将蛋标记为失效（清除绑定、物品栏数据，改名）。
	 * 与 DollEntity.invalidateEggsInInventory 逻辑一致。
	 */
	private static void invalidateEgg(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
		tag.putBoolean(DollSpawnEggItem.INVALIDATED_NBT_KEY, true);
		tag.remove(DollSpawnEggItem.DOLL_UUID_NBT_KEY);
		tag.remove(DollSpawnEggItem.INVENTORY_NBT_KEY);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME,
			Component.translatable("item." + DollModConstants.MOD_ID + ".doll_egg.invalidated"));
	}

	private static void onEndTick(MinecraftServer server) {
		if (paleSacrificeExpirations.isEmpty()) {
			return;
		}
		long currentTick = server.overworld().getGameTime();
		// 遍历检查过期条目
		var iterator = paleSacrificeExpirations.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.getValue() <= currentTick) {
				UUID playerUuid = entry.getKey();
				iterator.remove();
				// 移除临时最大生命值修饰符
				ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
				if (player != null) {
					AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
					if (maxHealth != null && maxHealth.hasModifier(PALE_SACRIFICE_MODIFIER_ID)) {
						maxHealth.removeModifier(PALE_SACRIFICE_MODIFIER_ID);
					}
					// 生命值超过当前上限时截断
					float currentHealth = player.getHealth();
					float maxHp = player.getMaxHealth();
					if (currentHealth > maxHp) {
						player.setHealth(maxHp);
					}
				}
			}
		}
	}
}
