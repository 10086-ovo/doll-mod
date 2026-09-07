package io.github.a10086ovo.doll.entity.talent;

import io.github.a10086ovo.doll.entity.DollVariant;

/**
 * 变体天赋工厂：按 {@link DollVariant} 创建独立天赋实例。
 * <p>
 * 天赋实例内含 tick 冷却状态，必须每只人偶一个（不可共享单例）；
 * 由 {@code DollEntity} 惰性创建并按变体缓存。
 */
public final class DollTalents {

	private DollTalents() {
	}

	/** 按变体创建天赋。新增变体时在此登记即可（对应分支实现放各 *Talent 类）。 */
	public static DollTalent create(DollVariant variant) {
		return switch (variant) {
			case WARDEN -> new WardenDollTalent();
			case PALE -> new PaleDollTalent();
			case NETHER -> new NetherDollTalent();
			case ENDER -> new EnderDollTalent();
			case SEA -> new SeaDollTalent();
			case FOREST -> new ForestDollTalent();
			case GUIDE -> new GuideDollTalent();
			case NONE -> new WorkerDollTalent();
		};
	}
}
