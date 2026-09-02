package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.NetherSwordItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 地狱剑金色血量：
 * <p>
 * 玩家手持地狱剑（主手或副手）时，获得 <b>6 颗金色血量</b>（吸收 amp=2 → 4×(2+1)=12 点，
 * 效果类似金苹果但数值更高，属玩家的常驻减伤垫）；放下后吸收消退。
 * <p>
 * 实现：每 tick 检测手持状态——手持时施加/续期 {@code MobEffects.ABSORPTION}（amp=2），
 * 离手时移除吸收效果。吸收会被消耗（先于本体血量受击扣减），被消耗掉的金色心不会自动回填，
 * 需重新拿起剑触发刷新才补齐，符合"额外金色血量"的直觉。
 * <p>
 * 注意：放下地狱剑会移除玩家身上<b>所有</b>吸收效果（吸收不区分来源，原版无法精确移除某一来源），
 * 属可接受的行为。
 */
@Mixin(Player.class)
public class NetherSwordHealthMixin {

	/** 吸收等级：amp=2 → 4×(2+1)=12 点 = 6 颗金色心 */
	private static final int ABSORPTION_AMPLIFIER = 2;
	/** 续期时长（tick）：手持期间每 tick 续期，放下后自然消退 */
	private static final int ABSORPTION_TICKS = 40;

	@Inject(method = "tick", at = @At("TAIL"))
	private void doll$netherSwordAbsorption(CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (player.level().isClientSide()) {
			return;
		}

		boolean holding = player.getMainHandItem().getItem() instanceof NetherSwordItem
			|| player.getOffhandItem().getItem() instanceof NetherSwordItem;
		if (holding) {
			// 手持：施加/续期吸收（同效果同等级 addEffect 只刷新时长，不叠加层数）
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
				ABSORPTION_TICKS, ABSORPTION_AMPLIFIER, false, false, true));
		} else if (player.hasEffect(MobEffects.ABSORPTION)) {
			player.removeEffect(MobEffects.ABSORPTION);
		}
	}
}
