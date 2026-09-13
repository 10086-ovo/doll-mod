package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.DollModConstants;
import io.github.a10086ovo.doll.util.GuideBookGivenStore;
import io.github.a10086ovo.doll.util.SearchMarkStore;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将「是否已赠送指导书」标记持久化到玩家存档（player.dat）。
 * <p>
 * 原版 Player 在 26.2 无 getPersistentData() 便捷 API，故通过 Mixin 注入
 * addAdditionalSaveData / readAdditionalSaveData，把标记写入玩家 NBT；
 * 运行时镜像保存在 {@link GuideBookGivenStore}（玩家登录 load 时由本 Mixin 从 NBT 重新填充），
 * 实现「每人仅限一本」跨重启持久。
 */
@Mixin(Player.class)
public abstract class PlayerGuideBookMixin {

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void dollmod_saveGuide(ValueOutput output, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level() != null && self.level().isClientSide()) return;
        output.putBoolean(DollModConstants.GUIDE_BOOK_GIVEN_TAG,
            GuideBookGivenStore.has(self.getUUID()));
        // 搜索打卡记忆：扁平 int[]（category0,target0,x0,z0,...），上限 128 溢出淘汰最旧
        output.putIntArray(DollModConstants.SEARCH_MARKS_NBT_KEY,
            SearchMarkStore.toFlatArray(self.getUUID()));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void dollmod_readGuide(ValueInput input, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level() != null && self.level().isClientSide()) return;
        GuideBookGivenStore.set(self.getUUID(),
            input.getBooleanOr(DollModConstants.GUIDE_BOOK_GIVEN_TAG, false));
        input.getIntArray(DollModConstants.SEARCH_MARKS_NBT_KEY)
            .ifPresent(arr -> SearchMarkStore.loadFromFlatArray(self.getUUID(), arr));
    }
}
