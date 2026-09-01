# 海洋人偶甲 — 套装特效 执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为海洋人偶甲四件（盔/胸/胫/靴）增益专属特效，令其贴合"海"之主题；保留原版甲之天性——可于锻造台附纹、可按原修补腕修理；并实现海偶着甲增伤、随主深浅进退。

**Architecture:** 采用丙案——立 `SeaArmorItem extends ArmorItem`，四件改挂此类，特效内蕴于物之 `tick`；胸甲水下视界辅以极小客户端 fog Mixin；增伤乘算植于人偶出伤入口；随主深浅则桥接现有 `applySeaSwim`。

**Tech Stack:** Fabric 26.2 API, `ArmorItem`, Item tick on equipped entities, `LivingEntity`, Mixin for fog rendering, `DollVariant.SEA` variant.

**执行须知（API 校准）：** 本工程为 26.2 映射，未公开等同任何 vanilla 版本。任务中出现的映射方法名仅是**按通行语义的候选**：其一，务必先照本工程既有之式（如 `EnderAxeItem`、`AreaEffectCloudMixin`、`DollEntity` 中同名调用）核其签名；其二，若 `gradlew build` 报错，即按编译器提示校准为正确签名（如 `onArmorTick` 或 `inventoryTick`、`isUnderWater()` 或 `isInWater()`、`addEffect` 之参数）。凡效果续期一律循"**将尽之闸**"之式，限频减耗。

---

### 文件结构

| 文件 | 责任 | 类型 |
|---|---|---|
| `src/main/java/io/github/a10086ovo/doll/item/SeaArmorItem.java` | `SeaArmorItem extends ArmorItem`，覆写装备 tick，实现逐件特效、全套抗性、数件计数 | New |
| `src/main/java/io/github/a10086ovo/doll/item/SeaArmor.java` | 静态工具类：计数计数/伤乘计算/装备查询 | New |
| `io.github.a10086ovo.doll.mixin.SeaWaterFogMixin` | 客户端 Mixin：本地玩家着胸甲水没时扩雾距 | New |
| `io.github.a10086ovo.doll.mixin.SeaChestNightVisionMixin` | 若需服务端夜视持续亦可此，今由 `SeaArmorItem.tick` 处理 | 并入 `SeaArmorItem` |
| `src/main/resources/doll-mod.mixins.json` | 注册上述 Mixin | Modify |
| `src/main/java/io/github/a10086ovo/doll/DollMod.java` | 四件改为 `SeaArmorItem` 实例，不换 `ArmorMaterial`、不换键 | Modify |
| `src/main/java/io/github/a10086ovo/doll/entity/DollEntity.java` | 近战/激光出伤乘海偶甲伤乘数；`applySeaSwimIfNeeded` 处接入随主深浅逻辑 | Modify |
| `docs/superpowers/specs/2026-09-01-sea-armor-effects-design.md` | 设计文（已写） | Existing |

---

### Task 1: 创建 `SeaArmorItem` 基础结构

**Files:**
- Create: `src/main/java/io/github/a10086ovo/doll/item/SeaArmorItem.java`

- [ ] **Step 1: Write skeleton class**

```java
package io.github.a10086ovo.doll.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.MiningEfficiencyEnchantment;
import net.minecraft.world.level.Level;

/**
 * 海洋人偶甲 — 自定义 ArmorItem 子类，实现逐件装备 tick 与特效触发。
 * 保留原版 ArmorItem 所有天性：锻造台纹饰支持、装备槽分配、耐久/附魔/修理均原生。
 * <p>
 * 逐件特效（设计文 § 四）：
 * <ul>
 *   <li>靴：水面行走（非潜行则浮，潜行沉；坠速撞水直没清摔落伤）</li>
 *   <li>盔：每刻续满空气，永无窒息</li>
 *   <li>胫甲：水中加速，几若陆行</li>
 *   <li>胸甲：入水续夜视（祛幽暗）；视界由客户端 Mixin 扩雾</li>
 *   <li>全套四件：续抗性 II</li>
 * </ul>
 */
public class SeaArmorItem extends ArmorItem {

    // 常量（设计核定）
    private static final float WATER_WALK_SPEED_BOOST = 0.35f;       // 胫甲水中速增
    private static final float DAMAGE_FALL_CLEAR_THRESHOLD = 3.0f;  // 速坠撞水此值以上直没清伤
    private static final int EFFECT_RENEW_THRESHOLD = 20;           // 效果仅剩 N tick 才续，限频减耗

    public SeaArmorItem(ArmorMaterial material, ArmorType type, Properties properties) {
        super(material, type, properties);
    }

    /**
     * 每刻装备 tick — 当物品在实体装备槽中时，原版每刻调用一次。
     * 仅服务端执行特效（客户端雾改走 Mixin）。
     */
    @Override
    public void onArmorTick(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide()) {
            return;
        }
        // 后续：在此按 this.type 分发调用对应特效
        // 计数：遍历四个槽，统计海洋甲件数 → 存临时静态或借 SeaArmor.countSeaArmor(entity)
        // 全套四件：续抗性 II（仅剩 < EFFECT_RENEW_THRESHOLD 才续）
    }

    // 后续：按 armor type 各写私有方法：tickHelmet、tickChest、tickLegs、tickBoots
}
```

- [ ] **Step 2: 编译以验证语法无误**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors related to this file.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/item/SeaArmorItem.java
git commit -m "feat(sea-armor): add SeaArmorItem skeleton"
```

---

### Task 2: 实现 `SeaArmorItem` 逐件特效与全套抗性

**Files:**
- Modify: `src/main/java/io/github/a10086ovo/doll/item/SeaArmorItem.java`
- Create: `src/main/java/io/github/a10086ovo/doll/item/SeaArmor.java`

- [ ] **Step 1: 创建 `SeaArmor` 静态工具类**

```java
package io.github.a10086ovo.doll.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 海洋甲通用工具：计数、伤乘。
 */
public class SeaArmor {

    /** 统计实体身上穿了几件海洋甲。 */
    public static int countSeaArmor(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof SeaArmorItem) {
                count++;
            }
        }
        return count;
    }

    /** 计算海偶伤害乘数：每件 +10%，四件 +40%。 */
    public static float damageMultiplier(int pieceCount) {
        return 1.0f + 0.1f * pieceCount;
    }
}
```

- [ ] **Step 2: 实现头盔特效（每刻续满空气）**

在 `SeaArmorItem.tickHelmet`:
```java
private void tickHelmet(Level level, LivingEntity entity) {
    if (entity.getAirSupply() < entity.getMaxAirSupply()) {
        entity.setAirSupply(entity.getMaxAirSupply());
    }
}
```

- [ ] **Step 3: 实现胫甲特效（水中加速）**

水中加速以**短期移速Ⅲ**承载（真 API，循"将尽之闸"续期），入水方续，几若陆行疾驰：

```java
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

private void tickLegs(Level level, LivingEntity entity) {
    if (entity.isInWater()) {
        if (!(entity.hasEffect(MobEffects.MOVEMENT_SPEED)
            && entity.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() > EFFECT_RENEW_THRESHOLD)) {
            // 等级 2 = 速度 III；不含图标以减少对其它玩家的通知
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 2, false, false));
        }
    }
}
```

- [ ] **Step 4: 实现胸甲特效（入水续夜视）**

在 `SeaArmorItem.tickChest`:
```java
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

private void tickChest(Level level, LivingEntity entity) {
    if (entity.isInWater()) {
        // 仅当效果将尽时续期，限频减耗
        if (!(entity.hasEffect(MobEffects.NIGHT_VISION)
            && entity.getEffect(MobEffects.NIGHT_VISION).getDuration() > SeaArmorItem.EFFECT_RENEW_THRESHOLD)) {
            entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
        }
    }
}
```

- [ ] **Step 5: 实现靴子特效（浮水/沉水/免摔伤）**

在 `SeaArmorItem.tickBoots`:
```java
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

private void tickBoots(Level level, LivingEntity entity) {
    // 靴已装备，entity.onGround 不成立于水表，检测 isOnWater 或 接触流体
    boolean isShiftDown = entity.isShiftKeyDown();
    if (entity.isOnWater() && !isShiftDown) {
        // 水表且未潜行 → 止沉，抵消向下动量
        Vec3 vel = entity.getDeltaMovement();
        if (vel.y < 0.0) {
            entity.setDeltaMovement(new Vec3(vel.x, 0.0, vel.z));
        }
        // 若落下速度过巨撞水，仍直没清摔伤
        if (entity.fallDistance > SeaArmorItem.DAMAGE_FALL_CLEAR_THRESHOLD) {
            entity.fallDistance = 0.0f;
            // 不拦下沉，自然直没
        } else {
            entity.fallDistance = 0.0f;
        }
    }
    // 潜行则顺沉，不拦 —— 原版重力自生
}
```

- [ ] **Step 6: 实现全套四件抗性 II 续期**

在 `SeaArmorItem.onArmorTick` 末尾：
```java
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

int count = SeaArmor.countSeaArmor(entity);
if (count >= 4) {
    if (!(entity.hasEffect(MobEffects.DAMAGE_RESISTANCE)
        && entity.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration() > EFFECT_RENEW_THRESHOLD)) {
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, false));
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/item/SeaArmor.java src/main/java/io/github/a10086ovo/doll/item/SeaArmorItem.java
git commit -m "feat(sea-armor): implement per-piece + full-set effects"
```

---

### Task 3: 修改 `DollMod.java` 注册 — 四件改用 `SeaArmorItem`

**Files:**
- Modify: `src/main/java/io/github/a10086ovo/doll/DollMod.java:716-746`

- [ ] **Step 1: 修改四件注册，由裸 `Item` 转 `SeaArmorItem`**

行 716 (`SEA_HELMET`):
```java
SEA_HELMET = Registry.register(
    BuiltInRegistries.ITEM,
    SEA_HELMET_KEY,
    new SeaArmorItem(SEA_ARMOR_MATERIAL, ArmorType.HELMET, new Item.Properties()
        .humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.HELMET)
        .repairable(SEA_ARMOR_REPAIR_TAG)
        .setId(ResourceKey.create(Registries.ITEM, SEA_HELMET_KEY)))
);
```

行 724 (`SEA_CHESTPLATE`):
```java
SEA_CHESTPLATE = Registry.register(
    BuiltInRegistries.ITEM,
    SEA_CHESTPLATE_KEY,
    new SeaArmorItem(SEA_ARMOR_MATERIAL, ArmorType.CHESTPLATE, new Item.Properties()
        .humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.CHESTPLATE)
        .repairable(SEA_ARMOR_REPAIR_TAG)
        .setId(ResourceKey.create(Registries.ITEM, SEA_CHESTPLATE_KEY)))
);
```

行 732 (`SEA_LEGGINGS`):
```java
SEA_LEGGINGS = Registry.register(
    BuiltInRegistries.ITEM,
    SEA_LEGGINGS_KEY,
    new SeaArmorItem(SEA_ARMOR_MATERIAL, ArmorType.LEGGINGS, new Item.Properties()
        .humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.LEGGINGS)
        .repairable(SEA_ARMOR_REPAIR_TAG)
        .setId(ResourceKey.create(Registries.ITEM, SEA_LEGGINGS_KEY)))
);
```

行 740 (`SEA_BOOTS`):
```java
SEA_BOOTS = Registry.register(
    BuiltInRegistries.ITEM,
    SEA_BOOTS_KEY,
    new SeaArmorItem(SEA_ARMOR_MATERIAL, ArmorType.BOOTS, new Item.Properties()
        .humanoidArmor(SEA_ARMOR_MATERIAL, ArmorType.BOOTS)
        .repairable(SEA_ARMOR_REPAIR_TAG)
        .setId(ResourceKey.create(Registries.ITEM, SEA_BOOTS_KEY)))
);
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/DollMod.java
git commit -m "refactor(sea-armor): register as SeaArmorItem instead of raw Item"
```

---

### Task 4: 创建客户端 `SeaWaterFogMixin` 实现胸甲扩雾

**Files:**
- Create: `src/main/java/io/github/a10086ovo/doll/mixin/SeaWaterFogMixin.java`
- Modify: `src/main/resources/doll-mod.mixins.json`

- [ ] **Step 1: 添加 Mixin 至 doll-mod.mixins.json**

在 `"mixins": [` 末尾追加:
```json
,
			"SeaWaterFogMixin"
```

- [ ] **Step 2: 写 `SeaWaterFogMixin`**

```java
package io.github.a10086ovo.doll.mixin;

import io.github.a10086ovo.doll.item.SeaArmorItem;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 海洋胸甲特效：本地玩家穿着且水没时，扩大水下雾距，减深蓝蒙翳，使海景清明。
 * <p>
 * 仅客户端、仅本地玩家生效；不影响干地、不影响胸甲未着、不影响他人，故极俭。
 * 不覆盖其他模组雾改，仅当条件满足时乘扩系数。
 */
@Mixin(FogRenderer.class)
public abstract class SeaWaterFogMixin {

    // 扩距系数（水下原本距 ×1.8）
    private static final float FOG_SCALE = 1.8f;

    /**
     * Modify the fog distance when player is submerged in water and wearing sea chestplate.
     */
    @ModifyVariable(method = "setupFog", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private static float onSetupWaterFog(float originalFogDistance,
                                         FogRenderer.FogData fogData,
                                         net.minecraft.client.Camera camera,
                                         float partialTick,
                                         Level level,
                                         int renderDistance,
                                         float far) {
        Entity entity = camera.getEntity();
        if (!(entity instanceof LocalPlayer player)) {
            return originalFogDistance;
        }
        // 只在玩家水没（眼睛入水）时生效
        if (!entity.isUnderWater()) {
            return originalFogDistance;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SeaArmorItem seaArmor)
            || seaArmor.getType() != EquipmentSlot.CHEST) {
            return originalFogDistance;
        }
        // 扩雾距
        return originalFogDistance * FOG_SCALE;
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/mixin/SeaWaterFogMixin.java src/main/resources/doll-mod.mixins.json
git commit -m "feat(sea-armor): add client fog mixin for water clear view"
```

---

### Task 5: 在人偶出伤处接入海偶增伤乘

**Files:**
- Modify: `src/main/java/io/github/a10086ovo/doll/entity/DollEntity.java`

- [ ] **Step 1: 在近战 stabAttack 出伤前乘伤**

查找 `DollEntity.stabAttack` 命中后调用 `target.hurtServer(...)` 处，在伤害值上乘伤乘数。定位约 1960-1990 行:

```java
import io.github.a10086ovo.doll.item.SeaArmor;

// ...
if (this.isSeaDoll()) {
    int count = SeaArmor.countSeaArmor(this);
    if (count > 0) {
        damage *= SeaArmor.damageMultiplier(count);
    }
}
boolean hit = this.stabAttack(
    target,
    this.damageSources().mobAttack(this),
    damage
);
```

- [ ] **Step 2: 在守卫者激光出伤处乘伤**

查找 `DollEntity.fireSeaLaser`，定位约 2303 行后:

```java
import io.github.a10086ovo.doll.item.SeaArmor;

// ...
if (this.isSeaDoll()) {
    int count = SeaArmor.countSeaArmor(this);
    if (count > 0) {
        laserDamage *= SeaArmor.damageMultiplier(count);
    }
}
target.hurtServer(serverLevel,
    this.damageSources().indirectMagic(this, this),
    laserDamage);
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/entity/DollEntity.java
git commit -m "feat(sea-armor): apply sea armor damage multiplier to doll attacks"
```

---

### Task 6: 实现"随主深浅·动态进退" — 桥接现有 `applySeaSwimIfNeeded`

**Files:**
- Modify: `src/main/java/io/github/a10086ovo/doll/entity/DollEntity.java:1594-1598`

- [ ] **Step 1: 修改 `applySeaSwimIfNeeded`，在靴浮水与随主之间抉择**

原代码：
```java
private void applySeaSwimIfNeeded(double goalY) {
    if (isSeaDoll() && this.isInWater()) {
        applySeaSwim(goalY);
    }
}
```

修改后（加入"随主深浅"逻辑）：

```java
/**
 * 海洋人偶专属：仅 isInWater 时按目标 Y 做竖直跟随，陆地与其他人偶不触发。
 * <p>
 * 若人偶着海洋靴，则优先以主人深浅为鹄：主在水面则浮，主潜下则随没。
 * 不拦着 applySeaSwim — 主人深浅既定，竖直跟随仍按原目标走。
 */
private void applySeaSwimIfNeeded(double goalY) {
    if (!isSeaDoll() || !this.isInWater()) {
        return;
    }
    // 若主人存在且着靴，主人在水面则此偶也要浮在水表
    Player owner = getOwnerPlayer();
    if (owner != null) {
        boolean hasSeaBoots = getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SeaArmorItem;
        if (hasSeaBoots && !owner.isInWater()) {
            // 主人不在水 → 人偶在水也要止沉，保持浮在水表
            Vec3 d = this.getDeltaMovement();
            if (d.y < 0.0) {
                this.setDeltaMovement(d.x, 0.0, d.z);
            }
        }
        // 主人在水 → 不拦沉，让 applySeaSwim 按目标深度走
    }
    applySeaSwim(goalY);
}
```

- [ ] **Step 2: 补 import（若缺）**

Add (若未引入):
```java
import io.github.a10086ovo.doll.item.SeaArmorItem;
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat compileJava --console=plain -q`
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/a10086ovo/doll/entity/DollEntity.java
git commit -m "feat(sea-armor): follow-owner depth dynamic water walking"
```

---

### Task 7: 最终全编译验证

**Files:** 全项目

- [ ] **Step 1: 完整编译**

Run: `.\gradlew.bat clean compileJava --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 无错后提交验证**

若编译失败，调试修正；成功则：

```bash
git status
```

- [ ] **Step 3: 验收收尾**

- 检查无未提交文件
- 所有设计文要求皆已实现：
  - [x] 靴可行水面，Shift沉水，速坠免摔
  - [x] 盔能永无窒碍
  - [x] 胫甲水中行速增
  - [x] 胸甲水中清明视界（夜视 + 扩雾）
  - [x] 全套四件抗性 II
  - [x] 海偶每件增伤一成，四件四成
  - [x] 靴偶随主深浅动态进退
  - [x] 所有能着甲之实体皆受此效（玩家 / 人偶 / 生物）
  - [x] 保留 ArmorItem 本性，锻造台可附纹

（End）
