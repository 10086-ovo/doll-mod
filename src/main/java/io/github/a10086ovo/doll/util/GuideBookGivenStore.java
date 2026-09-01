package io.github.a10086ovo.doll.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时镜像「玩家是否已领取指导书」标记。
 * <p>
 * 跨重启持久性由 {@code PlayerGuideBookMixin} 把该标记读写入 player.dat 的 NBT 提供；
 * 本类仅在内存中缓存当前会话的值，玩家登录 load 时由 Mixin 从 NBT 重新填充。
 */
public final class GuideBookGivenStore {
    private static final Map<UUID, Boolean> GIVEN = new HashMap<>();

    private GuideBookGivenStore() {
    }

    public static boolean has(UUID id) {
        return GIVEN.getOrDefault(id, false);
    }

    public static void set(UUID id, boolean value) {
        GIVEN.put(id, value);
    }
}
