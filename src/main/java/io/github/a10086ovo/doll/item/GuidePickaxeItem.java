package io.github.a10086ovo.doll.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * 向导的登山镐 —— 向导人偶的专属道具。
 *
 * <p>耐久 2031、挖掘速度 9.0、攻击 +6、攻速 1.2、附魔能力 15。
 * 26.2 中 {@link Item.Properties#pickaxe(ToolMaterial, float, float)} 原生镐 helper
 * 自动挂载 TOOL/ATTACK 组件与 mineable/pickaxe 标签，故本类仅作身份标识。
 *
 * <p>专属机制（注册到对应钩子）：
 * <ul>
 *   <li>玩家主手或副手持有时获得 <b>平滑翻越一格高方块</b> 的能力（{@code GuidePickaxeSmoothStepMixin}）</li>
 *   <li>向导人偶变体识别为近战武器（{@code DollEntity.isMeleeWeaponForDoll}）</li>
 *   <li>向导人偶盾构机掘进时断面由 1×2 扩为 3×3、侧向探矿半径同步扩大（{@code DollEntity.updateTunnelDrill}）</li>
 * </ul>
 */
public class GuidePickaxeItem extends Item {

	public GuidePickaxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
		super(properties.pickaxe(material, attackDamage, attackSpeed));
	}
}