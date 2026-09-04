package io.github.a10086ovo.doll.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时镜像「玩家是否已领取指导书」标记。
 * <p>
 * 该标记应「按存档」生效：每个新世界首次进入都应发放指南书。
 * {@link #clear()} 在服务端启动（{@code SERVER_STARTED}）时调用，用于清空上一个
 * 会话遗留在内存里的「已发放」记忆，使新存档能再次发放；同一存档内的防重复由
 * {@code PlayerGuideBookMixin} 将标记持久化到该世界 player.dat 的 NBT 提供。
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

    public static void clear() {
        GIVEN.clear();
    }
}
