package io.github.a10086ovo.doll.util;

/**
 * 荆棘盾牌反伤上下文：线程级标志位，标记当前调用栈是否正处于荆棘反伤中。
 * <p>
 * 从 ThornsShieldMixin 中提取，因为 Mixin 类不允许包含非 private static 方法。
 * <p>
 * 末影斧 80% 闪避注入在 Player.hurtServer HEAD，会对荆棘反伤触发的 hurtServer 误判闪避，
 * 使森林人偶/玩家荆棘在攻击者持末影斧时几乎无效。反伤期间置位，令闪避 Mixin 跳过。
 * ThreadLocal 保证仅作用于同一次调用栈，不跨线程、不跨请求残留。
 */
public final class ThornsShieldContext {

	private static final ThreadLocal<Boolean> THORNS_REFLECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private ThornsShieldContext() {}

	public static boolean isThornsReflecting() {
		return THORNS_REFLECTING.get();
	}

	public static void setThornsReflecting(boolean value) {
		THORNS_REFLECTING.set(value);
	}
}
