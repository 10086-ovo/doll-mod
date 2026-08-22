package com.example.client.mixin;

import com.example.doll.DollMod;
import com.example.doll.DollModConstants;
import com.example.doll.entity.DollEntity;
import com.example.doll.entity.DollVariant;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 运行时让人偶实体实现 ClientAvatarEntity，使其能被 vanilla AvatarRenderer 渲染。
 * 皮肤根据实体类型动态切换：
 * - 普通 DollEntity → 默认 doll.png
 * - 监守者变体（dollLevel >= 5）→ warden_doll.png（驯服态）
 * - WardenDollEntity（野生）→ warden_doll_wild.png
 * - 苍白变体 → pale_doll.png
 * - 下界变体 → nether_doll.png
 * - 末影变体 → ender_doll.png
 * - 海洋变体 → sea_doll.png
 * - 森林变体 → forest_doll.png
 * 全部使用 Alex/SLIM 细臂模型。
 */
@Mixin(DollEntity.class)
public abstract class DollEntityClientMixin implements ClientAvatarEntity {

	private static final Identifier DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/doll");
	private static final Identifier WARDEN_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/warden_doll");
	private static final Identifier WARDEN_DOLL_WILD_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/warden_doll_wild");
	private static final Identifier PALE_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/pale_doll");
	private static final Identifier NETHER_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/nether_doll");
	private static final Identifier ENDER_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/ender_doll");
	private static final Identifier SEA_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/sea_doll");
	private static final Identifier FOREST_DOLL_SKIN_TEXTURE =
		Identifier.fromNamespaceAndPath(DollModConstants.MOD_ID, "entity/doll/forest_doll");

	private final ClientAvatarState avatarState = new ClientAvatarState();

	/**
	 * 皮肤缓存：对应三种变体，避免每帧 new PlayerSkin。
	 */
	private final PlayerSkin cachedNormalSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedWardenSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(WARDEN_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedWardenWildSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(WARDEN_DOLL_WILD_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedPaleSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(PALE_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedNetherSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(NETHER_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedEnderSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(ENDER_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedSeaSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(SEA_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);
	private final PlayerSkin cachedForestSkin = PlayerSkin.insecure(
		new ClientAsset.ResourceTexture(FOREST_DOLL_SKIN_TEXTURE), null, null, PlayerModelType.SLIM);

	@Shadow
	public abstract ResolvableProfile getProfile();

	@Shadow
	public abstract boolean isWardenDoll();

	@Shadow
	public abstract DollVariant getDollVariant();

	/**
	 * 注意：不能 @Shadow 继承自 Entity 基类的 getType()——
	 * Mixin 的 @Shadow 只在目标类自身声明的方法中查找（不搜索父类层级），
	 * DollEntity/Avatar 均未覆写 getType()，会导致 InvalidMixinException。
	 * 调用处已用 ((DollEntity)(Object)this).getType() 强转，无需 shadow。
	 */

	/**
	 * 客户端诊断：人偶实体在客户端创建完成的时刻标记。
	 * 配合 DollModClient 的 tick 看门狗：若"生成人偶后客户端冻结"，
	 * 用本行时间戳与看门狗警告的时间差即可确定卡顿发生在实体创建之后、
	 * 首次渲染期间还是更早。
	 */
	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void dollClientLogCreate(EntityType<? extends LivingEntity> entityType, Level level, CallbackInfo ci) {
		DollMod.LOGGER.info("[DollClient] 人偶实体客户端构造完成");
	}

	@Override
	public ClientAvatarState avatarState() {
		return avatarState;
	}

	@Override
	public PlayerSkin getSkin() {
		// 动态选择皮肤：通过 getType() 判断是否为野生监守者人偶实体类型
		if (this.isWardenDoll()) {
			if (((DollEntity)(Object)this).getType() == DollMod.WARDEN_DOLL_ENTITY) {
				return cachedWardenWildSkin;
			}
			return cachedWardenSkin;
		}
		if (this.getDollVariant() == DollVariant.PALE) {
			return cachedPaleSkin;
		}
		if (this.getDollVariant() == DollVariant.NETHER) {
			return cachedNetherSkin;
		}
		if (this.getDollVariant() == DollVariant.ENDER) {
			return cachedEnderSkin;
		}
		if (this.getDollVariant() == DollVariant.SEA) {
			return cachedSeaSkin;
		}
		if (this.getDollVariant() == DollVariant.FOREST) {
			return cachedForestSkin;
		}
		return cachedNormalSkin;
	}

	@Override
	public net.minecraft.world.entity.animal.parrot.Parrot.Variant getParrotVariantOnShoulder(boolean left) {
		return null;
	}

	@Override
	public boolean showExtraEars() {
		return false;
	}
}