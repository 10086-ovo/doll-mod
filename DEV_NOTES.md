# DEV_NOTES — DollMod 开发笔记

> 仅收录**可复用的开发技巧与 Fabric / MC 26.2 适配踩坑**，由本项目开发迭代整合而来。
> 一切以当前代码（`io.github.a10086ovo` 包）为准；过时的策划 / 玩法 / 设计性内容已删除。
> 行号锚点基于编写时的源码，仅作定位参考，后续以实际代码为准。

## 0. 构建与运行

- 环境：JDK 26 + Fabric Loader 26.2 + Fabric API（`gradle.properties` `group=io.github.a10086ovo`）。
- 命令：
  - `./gradlew runClient` — 启动开发实例，读取 `build/` 已编译产物（`doll-mod-1.0.0.jar`），不依赖 `src/` 即可跑。
  - `./gradlew build` — 重新编译并打包到 `build/libs/`。
  - `./gradlew runDatagen` — 运行数据生成器（**覆盖式**输出资源 JSON）。
- 改源码 / 资源后须重新 build 才能反映到 runClient。

### Gradle 用户目录（GRADLE_USER_HOME）——接手者必读
- **本项目 Gradle 用户目录统一为系统标准位置 `C:\Users\<用户名>\.gradle`**（即 `~/.gradle`），勿再改指到项目内的 `.gradle-home`。
- 背景：旧配置曾把 IDEA 的 `serviceDirectoryPath` 指到项目内 `D:\minecraft26.2_fabric_mod\.gradle-home`（含完整 gradle 9.5.1 发行版 + 1.4G Loom 依赖缓存）。现已合并迁移到 `~/.gradle`，并将 IDEA 设置改回标准位置，命令行与 IDEA 统一。
- **命令行正确用法**（不设 `GRADLE_USER_HOME` 即命中 `~/.gradle` 缓存）：
  ```bash
  JAVA_HOME="D:/JDK26" ./gradlew build
  ```
- 若某台机器 `~/.gradle` 无缓存且联网受限，可用国内镜像手动补齐发行版（官方源 `services.gradle.org` 及 GitHub 直连常被墙）：
  ```bash
  curl -L -o "$HOME/.gradle/wrapper/dists/gradle-9.5.1-bin/<hash>/gradle-9.5.1-bin.zip" \
    https://mirrors.cloud.tencent.com/gradle/gradle-9.5.1-bin.zip
  ```
  （`<hash>` 为 wrapper 生成的目录，非固定；插件/依赖首次仍需联网解析。）
- 项目根 `.gradle/` 是**项目级**目录（Loom 中间产物、临时类），**不是** GRADLE_USER_HOME，勿把它当用户目录用。

## 1. 包结构约定

- 包根：`io.github.a10086ovo.doll`（main）/ `io.github.a10086ovo.client`（client 渲染、Screen、client mixin）。
- main / client 拆分：
  - `src/main/java/...` — 服务端通用逻辑、注册、AI、行为、非 client mixin。
  - `src/client/java/...` — 仅客户端：渲染器、Screen 渲染、client mixin。
- 关键包与职责：
  - `entity` — `DollEntity`（核心）/ 变体 `DollVariant` / `WildWardenDollEntity`（BOSS）/ 投掷物（`ThrownEnderAxe`、`NetherFlyingSwordEntity`）/ 召回（`DollRecallRegistry`、`DollRecallService`）/ 搜索类型（`Structure/Biome/VillageSearchType`）。
  - `item` — 装备（`EnderAxeItem`、`NetherSwordItem`、`PaleBowItem`、`ThornsShieldItem`）、头颅、盾、`GuideBookItem`、刷怪蛋 `DollSpawnEggItem`。
  - `block` — 7 种头颅方块（`*DollHeadBlock` + `*DollSkullType` + `*DollHeadBlockEntity`）+ `RockAnvilBlock`（三级损伤）+ `SculkShrineBlock`（祭坛）。
  - `config` — 外置配置 `DollConfig`（`config/dollmod/doll.json`）。
  - `guide` — 指南书数据模型与加载（Patchouli 风格 JSON）。
  - `inventory` — `DollInventory`（45 格人偶背包）。
  - `loot` — `SeaArmorLootInjector`（海洋套装战利品注入）。
  - `recipe` — `DollUpgradeRecipe`（升级配方）。
  - `screen` — `DollScreenHandler`（服务端菜单）。
  - `mode` — `DollMode`（8 种行为模式枚举）。
  - `network` + `network/payload` — C2S/S2C 通道与数据载荷。
  - `util` — `DollEntityLookup`、`GuideBookGivenStore`、`SearchMarkStore`、`ThornsShieldContext`。
  - `mixin` — main mixin；`io.github.a10086ovo.client.mixin` — client mixin。
  - client 渲染（头颅/盾/投掷物）：`client/renderer/blockentity/*DollHeadRenderer`（头颅方块 BER）、`client/DollSkullState`（头颅弹 Duck-Typing）、`client/renderer/entity/`（投掷末影斧 / 飞行剑 / 野生幽匿）、`client/renderer/special/`（荆棘盾）。
  - 注册 ID 集中在 `DollModConstants`，mod id = `doll-mod`。
  - 实际类清单以 `src/main/java/io/github/a10086ovo/doll/` 与 `src/client/java/io/github/a10086ovo/` 现状为准（勿沿用旧 `com/example` 路径）。

## 2. Mixin

- **配置文件即权威清单**，勿依赖任何静态列表（会随开发漂移）：
  - main：`src/main/resources/doll-mod.mixins.json`（package `io.github.a10086ovo.doll.mixin`）
  - client：`src/client/resources/doll-mod.client.mixins.json`（package `io.github.a10086ovo.client.mixin`）
- `compatibilityLevel: JAVA_25`；`injectors.defaultRequire = 1`（注入点须全部命中，缺失即崩溃）。
- main mixin 一览（新内容）：
  - `AbstractArrowMixin` — 苍白弓箭矢命中施加易伤。
  - `LivingEntityDodgeMixin` — 末影斧 80% 玩家闪避（注入 `Player.hurtServer` HEAD）。
  - `LivingEntityVulnerabilityMixin` — 苍白弓/恐惧光环的易伤伤害乘算。
  - `LivingEntityFearAuraMixin`、`MobMixin` — 苍白人偶恐惧光环（16 格内敌对生物失去攻击 AI）。
  - `NetherSwordHealthMixin` — 地狱剑手持生命上限 +10。
  - `WitherSkullMixin` — 下界人偶烈焰弹（凋灵骷髅头颅弹）。
  - `ThornsShieldMixin`、`AnvilBlockMixin`、`AnvilMenuMixin`、`ItemCombinerMenuAccessor`、`EnchantmentHelperMixin`、`AreaEffectCloudMixin`/`Accessor`、`PlayerGuideBookMixin` — 各功能适配。

## 3. API 适配要点（MC 26.2 / Fabric）

### 武器（剑 / 斧）——26.2 已移除 SwordItem/PickaxeItem
- **官方构造剑的方式**：`Item.Properties.sword(ToolMaterial, attackDamageBonus, attackSpeed)`，内部一次性挂三样东西：
  - **TOOL 组件**：蜘蛛网挖掘速度 15.0 + 秒挖 + 1.5 倍速（「剑挖蜘蛛网快」的能力；手动构造属性漏掉它会挖蜘蛛网很慢）。
  - **攻击属性**：`ATTACK_DAMAGE`/`ATTACK_SPEED`，用原版标准 ID（`minecraft:base_attack_*`），tooltip 自动显示最终值。
  - **WEAPON 组件**：横扫等剑专属特性。
  - 因此**不要手动 `ItemAttributeModifiers.builder()`**（会漏掉 TOOL/WEAPON 组件）。→ `item/NetherSwordItem.java`。
- **斧**：`EnderAxeItem` 继承 `Item`，用 `Item.Properties.attributes(ItemAttributeModifiers)` 手动挂攻击属性（同 26.2 官方范例）。
- **JADE 显示一致性修复**：原版 Default 显示只在「带玩家上下文」时才把基础值加回。用 JADE 看向掉落物 / 飞行状态的剑（`ItemEntity`）时 player 为 null，攻击伤害误显示偏低。修复：在 `sword()` 之后用 `attributes()` 覆盖一份 `ItemAttributeModifiers`，数值不变，仅把攻击伤害的 display 改为 `Display.override` 固定文本。→ `NetherSwordItem.java:82-103`。

### 盔甲（ArmorMaterial）——8 参构造
- `new ArmorMaterial(durabilityBase, Map<ArmorType,Integer>, enchantmentValue, equipSound, toughness, knockbackResistance, repairTag, equipmentAssetsKey)`。
- 配 `Item.Properties.humanoidArmor(material, ArmorType)` + `.repairable(repairTag)`。→ `DollMod.java` 海洋套装（数值对标钻石套）。
- 装备纹理资源走 `src/main/resources/assets/doll-mod/equipment/`（如 `sea.json`）。

### 盾牌（ThornsShieldItem）
- **必须显式注入 `DataComponents.BLOCKS_ATTACKS`**：26.2 不为自定义 Shield 自动设置该组件。构造函数 `properties.component(DataComponents.BLOCKS_ATTACKS, ...)`，否则 `getUseAnimation()` 不返回 `BLOCK`、第三人称举盾姿态失效。→ `item/ThornsShieldItem.java:42`。
- **`Sheets.SHIELD_MAPPER.apply()` 自动补 `entity/shield/` 前缀**：只传盾的名字（如 `thorns_shield`），不要传完整路径。→ `client/renderer/special/ThornsShieldSpecialRenderer.java:29`。
- **`AvatarRenderer.getArmPose()` 仅在 `isUsingItem() && useAnimation==BLOCK` 时返回 `ArmPose.BLOCK`**：被动副手盾不触发 BLOCK 姿态；自定义盾要正确显示须保证该条件成立或用 client mixin 适配。

### 实体交互
- **1.21.2+ `Entity.interact(...)` 返回非 `SUCCESS` 时，引擎会自动调用 `Item.interactLivingEntity(...)`**：人偶与物品交互若需走 `interactLivingEntity`，`interact` 应返回 `PASS` 让服务端转发，勿返回 `SUCCESS` 截断。

### 采矿工具等级
- 矿石挖掘须校验工具等级，`requiresTieredTool(state) && !pickaxe.isCorrectToolForDrops(state)` 时放弃挖掘，避免人偶用低等级镐白挖无掉落。
- **唯一真相源 `canPickaxeMine(BlockState)`**（→ `entity/DollEntity.java`）：所有挖矿入口必须调用它，**不要各写一份 `requiresTieredTool + isCorrectToolForDrops` 判断**。
  五个入口：选目标 `selectMineTarget` / 单块挖 `mineBlock` / 连锁 `chainMineOres` / 盾构机掘进 `updateTunnelDrill` / 盾构机侧向探矿 `scanNearbyOre`。
  历史教训：侧向探矿那段曾漏判，导致木镐把钻石矿"拆掉但零掉落"——方块消失、资源白丢，且表现极隐蔽。
- **镐子选择"最聪明"策略（已与作者确认）**：
  - 能力判定用 `findBestPickaxeStack()`（背包里 Tier 最高的镐）。**修复缺陷**：旧 `findPickaxeStack()` 只按格子顺序返回第一把镐（石镐在前、钻石镐在后时，人偶误判"挖不动钻石矿"而绕开它）。
  - 实际挖掘用 `findPickaxeForState(state)`（在 `isCorrectToolForDrops(state)` 为真的镐里挑 Tier 最低的一把），把钻石 / 下界合金镐留给真正需要的矿，省耐久。
  - 分级 `pickaxeTierLevel()`：26.2 已移除 `TieredItem`，改为测 `isCorrectToolForDrops` 在参考方块上的结果定级——铁矿石（`NEEDS_STONE_TOOL`）=石级、钻石矿（`NEEDS_IRON_TOOL`）=铁级、黑曜石（`NEEDS_DIAMOND_TOOL`）=钻石级；下界合金镐单独记最高级，保证优先用钻石镐省耐久；木/金镐连铁矿石都挖不动 → 0 级。**正确性始终由 `isCorrectToolForDrops` 兜底，分级只用于"够用的镐里挑最弱"的排序**，即便分级偏差也绝不会选到不够用的镐。

### 自定义头颅（Custom Head）——MC 26.2 全栈配方（重点）
> **本项目 7 种人偶头颅（warden / pale / forest / nether / sea / ender / guide）的实现是多次踩坑总结出来的全栈方案**，不是"加个纹理"那么简单。26.2 的自定义头颅涉及**方块 / 方块实体 / 方块实体渲染器 / 物品特殊模型 / 两个 SkullBlockRenderer Mixin** 五层，缺一环就紫黑块或根本不渲染。记录如下，新增头颅照着抄。

**① 方块侧：自定义 `SkullBlock.Type` + `SkullBlock` 子类 + 方块实体**
- 每个变体一个 `SkullBlock.Type` 枚举（如 `EnderDollSkullType implements SkullBlock.Type`，`getSerializedName()` 返回 `"ender_doll"`）。→ `block/*DollSkullType.java`
- 每个变体一个 `SkullBlock` 子类，构造传类型 + `properties`（`strength(1.0f).noOcclusion().setId(...)`），覆写 `codec()`（`simpleCodec`) 和 `newBlockEntity()`。→ `block/*DollHeadBlock.java`
- 每个变体一个 `BlockEntity` 子类，`implements DollHeadBlockEntity`（一个只有 `float getAnimation(float partialTick)` 的接口），供渲染器取动画进度。→ `block/*DollHeadBlockEntity.java` + `block/DollHeadBlockEntity.java`

**② 注册方块 / 方块实体**
- `DollMod` 里用 `Registry.register(BuiltInRegistries.BLOCK, ...)` 与 `BuiltInRegistries.BLOCK_ENTITY_TYPE` 逐个注册（方块实体 `new BlockEntityType<>(构造器, Set.of(对应方块))`）。
- **方块实体必须与方块成对**，`Set.of()` 里漏填方块则方块不会挂上实体。

**③ 物品侧：`BlockItem.equippable(HEAD)` + 特殊头颅物品模型（关键！）**
- 头颅物品继承 `BlockItem`，构造里 `.equippable(EquipmentSlot.HEAD)` 即可放置 + 佩戴。→ `item/*DollHeadItem.java`
- **物品在物品栏/手中要显示成"头颅"而非普通方块贴图，靠的是 `assets/<modid>/items/<id>.json` 的 `minecraft:special` 特殊模型**（不是 `models/item`！）：
  ```json
  {
    "model": {
      "type": "minecraft:special",
      "base": "minecraft:item/template_skull",
      "model": { "type": "minecraft:head", "kind": "player", "texture": "doll-mod:doll/ender_doll" },
      "transformation": { "left_rotation": [1,0,0,-0], "right_rotation": [0,0,0,1], "scale": [1,1,1], "translation": [0.5,0,0.5] }
    }
  }
  ```
  - `texture` 指向 `textures/entity/doll/<变体>.png`（**复用实体皮肤纹理**，UV 是玩家皮肤格式，故 `kind` 用 `"player"`）。
  - 26.2 的**物品模型目录是 `assets/<modid>/items/`**（新命名，非旧的 `models/item`）。`models/item/<id>.json` 旧式文件在本项目头颅上**可有可无**（仅 `warden_doll_head.json` 遗留存在），真正的渲染走 `items/` 的特殊模型。
  - 遗漏这步 → 物品栏/手持显示紫黑方块或空模型。

**④ 方块实体渲染器：走 26.2 新 `SkullBlockRenderState` 三阶段管线**
- 基类 `AbstractDollHeadRenderer<T extends BlockEntity & DollHeadBlockEntity> implements BlockEntityRenderer<T, SkullBlockRenderState>`，把 model / `createRenderState` / `extractRenderState` / `submit` 全部上提，**子类只需在构造器传纹理路径**。→ `client/renderer/blockentity/AbstractDollHeadRenderer.java`
- 关键实现点：
  - `extractRenderState` 里从 blockstate 区分 `WallSkullBlock`（用 `WallSkullBlock.FACING` → `SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(facing)`）与自由头颅（`SkullBlock.ROTATION` → `freeTransformations(rotation)`）；`state.skullType` = `((AbstractSkullBlock)block).getType()`；`state.renderType` = `RenderTypes.entityCutoutZOffset(texture)`。
  - `submit` 里 `SkullBlockRenderer.submitSkull(animationProgress, pose, collector, lightCoords, model, renderType, 0, breakProgress)`。
  - **两阶段 extract**（`extractRenderState` 先解析、`submit` 再提交），是 26.2 分离 render-state 与 render 的规范；不要回到老式 `render()` 直接绘制的写法。
- 客户端入口用 `BlockEntityRenderers.register(DollMod.*_HEAD_BLOCK_ENTITY, *HeadRenderer::new)` 逐个注册。→ `DollModClient.java`

**⑤ 让原版 `SkullBlockRenderer` 认领自定义类型：两个 Mixin（最容易漏）**
- 原版 `SkullBlockRenderer` 只认内置 `SkullBlock.Type`，自定义类型必须用 Mixin 注入两个点：
  1. **皮肤表**：注入 `lambda$static$0`（`HashMap<SkullBlock.Type, Identifier>` 初始化）`@At("TAIL")`，`map.put(自定义Type, 纹理Identifier)`。→ `client/mixin/SkullBlockRendererMixin.java`
  2. **模型选择**：注入 `createModel(EntityModelSet, SkullBlock.Type)` `@At("HEAD")`，`cancellable`，命中自定义 Type 时 `cir.setReturnValue(new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD)))`。
  - 漏第 1 个 → 头颅方块不显示皮肤（透明/黑块）；漏第 2 个 → 模型找不到崩溃或用错模型。
  - **`@At("TAIL")` 的 `lambda$static$0` 是合成方法名**，不同版本可能变；本项目 `require = 0` 容错，但改动需重新核对合成签名。

**⑥ 特例：头颅形状的投射物（下界/末影人偶烈焰弹）——Duck-Typing 标记 + 渲染器换模型换贴图**
- 烈焰弹复用原版 `WitherSkull` 实体与 `WitherSkullRenderer`，但要让"人偶发射的"显示成人偶皮肤，需要：
  - **Duck-Typing 接口** `DollSkullState`（`dollMod$getVariant/setVariant`），由 Mixin 让 `WitherSkullRenderState` 实现它，`extractRenderState` 写入发射者变体、`submit/getTextureLocation` 读取。→ `client/DollSkullState.java` + `client/mixin/WitherSkullRenderStateMixin.java`
  - **`WitherSkullRendererMixin`**：构造里 `new SkullModel(bakeLayer(PLAYER_HEAD))`；注入 `getTextureLocation` 按变体换贴图（NETHER→nether_doll.png / ENDER→ender_doll.png）；`@ModifyArg` 拦 `submitModel` 第 0 参（Model）换 playerHeadModel；`submit` HEAD 记录当前是否人偶发射（渲染单线程，用实例字段即可）。→ `client/mixin/WitherSkullRendererMixin.java`
  - 原版凋灵发射的头颅不受影响（变体标记为 `NONE` 走默认分支）。

---

## 4. 实体 / AI 行为陷阱

### 模式切换瞬移漂移
- `resetModeWorkState()` 必须 `setDeltaMovement(Vec3.ZERO)` + 清除移动输入，否则旧 deltaMovement 会让实体被"传送"漂移。→ `entity/DollEntity.java:764`。

### DollNavigator 寻路
- **`level()` 必须实时取 `entity.level()`，不可缓存为 final 字段**：早期把 `level` 缓存为构造期字段，人偶跨维度召回后 `entity.level()` 已是新维度，navigator 仍持旧维度引用，导致 `computePath / canOccupy / hasLineOfSight` 全在错误维度方块数据上操作。改为每次实时获取彻底修复。→ `entity/DollNavigator.java:44-53`。
- **路径复用**：目标偏移不大（`path 末端 distanceToSqr(target) <= 4.0`，即 ≤2 格）时直接复用旧路径，避免每 tick 重算 A*。→ `DollNavigator.java:84-90`。
- 自实现轻量 A*（八方向 + 台阶换层），不绑原版 Mob 寻路体系；海洋人偶 `allowWater` 将水方块视为可占据格以实现下潜 / 上浮。
- **A* 每步下降天然 ≤1 格**：下降邻居恒为 `cur` 正下方 1 格，且 `canOccupy` 要求落点下方必有实心支撑 → 人偶**不会**主动走下悬崖 / 掉进深坑（早期注释误报过"会走下悬崖"，已证伪）。`neighbors()` 的 `down` 邻居额外经 `isSafeLanding()` 过滤：岩浆 / 火 / 岩浆块 / 仙人掌等伤害性落点排除，因为这类方块无碰撞箱会被 `canOccupy` 误判成可落脚。→ `DollNavigator.java`。
- **`MAX_SAFE_FALL_BLOCKS = 3`**：安全落差上限（格），盾构机悬崖判定复用同一常量，保证"人偶敢走下去"与"盾构机敢挖过去"口径一致。

### 挖矿模式（MINE）与盾构机
- **设计决策（已与作者确认）**：① 跟随 + 挖矿共存 = 挖矿优先跟随（有矿目标时暂时离队去挖，挖完 / 无矿自动回跟随）；② 镐等级 = 严格匹配（`canPickaxeMine`，只挖当前镐挖得动且会掉落的矿）；③ "平地识别为悬崖"已修。
- **镐等级唯一真相源 `canPickaxeMine(BlockState)`**：选目标 / 单块挖 / 连锁 / 盾构机掘进 / 盾构机侧向探矿 五个入口全部调用它，**不要各写一份判断**。→ `entity/DollEntity.java`。
- **跟随离队边界**：`mineExcursionAllowed()` 受 `MINE_EXCURSION_MAX_TICKS`（5s）/ `MINE_EXCURSION_MAX_DIST_SQR`（12²）约束，否则人偶会一路追矿越跑越远。（注：砍树离队是 20s / 32²，与挖矿不同。）
- **扫描中心 `getMineScanCenter()`**：跟随时人偶离主人 ≤8 格用主人为中心（保留设计），离远改用人偶自身为中心，否则会挑主人身边够不着的矿 → 寻路失败 → 拉黑，表现为"跟随时不认矿"。
- **盾构机停止条件（`updateTunnelDrill`）一览，顺序即优先级**：
  1. `mine_stop_cliff`：前方落差 > `MAX_SAFE_FALL_BLOCKS` 才停（早期判定写反 → 平地秒停；后又过严 → 山区小空腔频繁误停，已放宽）。
  2. `mine_stop_lava`：前方 ±2 格有岩浆。
  3. `mine_stop_gravity`：前方是沙砾 / 沙子。
  4. `mine_stop_water`：前方两格有水（盾构机不游泳）。
  5. `mine_stop_unbreakable`：需要分级工具且 `!canPickaxeMine`（统一后不会漏判不在 `MINEABLE_WITH_PICKAXE` tag 内的分级方块）。
  6. `mine_stop_no_pickaxe`：镐空 → **必须在开挖前停**，否则深板岩这类 `requiresCorrectToolForDrops` 路障会被"零掉落"挖掉。
  7. `mine_stop_backpack_full`：背包满。
  - （开挖后仍校验 `dig1/dig2` 是否已挖通，否则 `mine_stop_blocked`；）
  - **`mine_stop_bedrock`**：基岩单独报原因（旧实现 `continue` 跳过，被"挖不通"兜底误导）。
  - **连锁矿脉 bug**：`updateTunnelDrill` 原把 `isOreBlock(dig)` 写在 `tunnelMineBlock` 之后，挖完变空气恒为 false → 连锁从未触发。已改为挖前 `wasOre` 判定。
  - **镐耗尽静默损失**：无镐时旧实现仍对石/深板岩继续挖 → 深板岩 `requiresCorrectToolForDrops` 零掉落。改由上述停止条件 6 拦截。
- 改语言键（如新增 `mine_stop_*`）走 `DollDataGenerator`，然后 `./gradlew runDatagen` 重新生成 `src/main/generated/assets/doll-mod/lang/*.json`（覆盖式），**不要手写**。

### 箭矢朝向修正
- `trackArrows()`：当 `currentDir.dot(targetDir) >= 0.95`（角度偏差 < ~18°）时跳过朝向修正，避免抖动。→ `entity/DollEntity.java`。

### 食物回血（非 Player 实体）
- Avatar / LivingEntity 无 `FoodData`；原版 `FoodProperties.onConsume` 对非 Player 实体**跳过 nutrition 处理**，食物营养值不会自动转血。须在 `super` 消耗物品前读取营养值并手动回血。→ `entity/DollEntity.java`。

### 忠诚回归防孤儿实体
- `ThrownEnderAxe` 忠诚附魔回归须加超时 / 超距保护：`tickCount > 200 || distSqr > 64.0 * 64.0` 时 `discard()`，防止人偶跨维度 / 死亡后斧头无限追逐成为孤儿实体。→ `entity/ThrownEnderAxe.java:97-102`。
- 须同步 `loyalty` 到 `EntityDataAccessor`，避免重载后 `loyalty=0` 导致忠诚 III 末影斧不再飞回。→ `ThrownEnderAxe.java:270`。

### 飞行地狱剑（NetherFlyingSwordEntity）——继承 ItemEntity 做守护飞剑
- 复用原版物品载体基建（`setPickUpDelay(32767)` + `setUnlimitedLifetime()` 关拾取 / 合并 / 老化 / 重力），但渲染改由自定义 3D 剑模型负责，tick 走 super 链保住位置插值。
- **姿态同步用 `EntityDataAccessor`**（yaw/pitch/roll 三个 FLOAT），服务端计算、客户端渲染读取，避免每 tick 发包。
- 状态机：HOVER（贴背悬停）→ THRUST（先升空避让再直刺）→ RETURN（归位）。召唤者死亡 / 移除 / 跨维度时 `discard()`；同一召唤者同时仅一把（`replaceExisting` 顶替）。
- **命中判定用"目标中心"而非脚底坐标**：从上方斜刺时剑尖已贴到敌人身上但到脚底距离仍偏大 → 漏判无伤害。→ `NetherFlyingSwordEntity.java`。

### 渲染姿态
- 强制头部跟随身体朝向（`yBodyRot`），否则头相对身体可差 180°，出现"头扭过 180°"诡异姿势。→ `entity/DollEntity.java`。

### 减少对象分配
- 遍历方块用单个 `BlockPos.MutableBlockPos` 复用，避免每格分配 `BlockPos` + `above()` 两个对象。→ `entity/DollEntity.java`。

### AI 目标复用
- `findHostileTarget()` 当目标仍存活且在追击距离内时复用 `meleeTarget`，避免每 tick 重选目标。

## 5. 外置配置（DollConfig）

- 载荷整体置于 `config/dollmod/doll.json`（JSON，Gson 随原版内置、零新依赖）。含索敌 / 觅途 / 跟随 / 各模式阈值时限开关。
- **Gson 命名策略 `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES`**：doll.json 以下划线键书写（如 `resume_distance`），Java 字段为驼峰（`resumeDistance`）。不设此策略则下划线键全部静默不生效（配置形同虚设）。→ `config/DollConfig.java:40`。
- 失格容错：文件缺失 / JSON 解析失败一律回退默认值，日志示警，宁默不崩；首启自动写一张带 `_comment` 注疏的完整默认档。
- 运行期重载：`/dollmod reload`（OP 权限）→ `DollConfig.reload()` 重读文件并覆写静态镜像（`DollEntity.applyConfig` / `DollNavigator.applyConfig`），改动即生效勿须重启。
- 距离类字段一律存「基准值」（非平方），派生平方由应用侧 `v*v` 即时推算，免双值失和。

## 6. 指南书 / 搜索系统

### 指南书（GuideBook）
- 数据为 Patchouli 风格 JSON，位于 `assets/doll-mod/patchouli_books/guide_book/`（book.json + categories + entries）。
- 加载在客户端进行（`GuideBookContent.get()` 传 `Minecraft.getInstance().getResourceManager()`），懒加载并缓存；服务端 `GuideBookItem` 只返回成功，打开动作由 `DollModClient` 注入 `openScreenAction`，避免 main 包引用 client。
- 首次进世界自动发放（`GuideBookGivenStore` 记忆 UUID 防重复）。
- **图标引用用注册 ID（如 `doll-mod:xxx`），不是 Java 字段名。**

### 向导人偶搜索
- 入口：GUIDE 人偶背包界面点击左侧**单一「搜索」按钮**，打开 `GuideSearchScreen`，按 结构 / 群系 / 村庄 三个标签分类。
- 网络通道在 `DollNetworking`：C2S `search_request`、S2C `search_results`、C2S `toggle_search_mark`。
- 搜索半径 **100 区块（1600 格）**，以玩家发起时位置为中心；结果缓存 LRU + 断线清理，防内存泄漏。
- 群系搜索**异步化**：主线程只取 `BiomeSource`/`Sampler` 引用、提交工作线程做纯噪声采样（`getNoiseBiome`，不生成区块），完成后回主线程写缓存并发包。旧实现每格 `level.getBiome()` 强制同步生成区块，是多人卡顿主因。
- 结构 / 村庄搜索用原版 `findNearestMapStructure`（半径单位区块），不生成区块；性能关键：中心 100 区块 1 次 + 外围 8 方向（70 区块处各 30 区块半径）1 次，共 9 次调用、全部收敛在 100 区块半径内。旧实现约 50 次调用、最远 12600 格。
- 每 tick 搜索配额 `MAX_SEARCHES_PER_TICK=2`，多人同点刷新时摊到后续 tick，避免主线程过载。
- 打卡状态存 `SearchMarkStore`，玩家 NBT 键 `guide_search_marks`，跨会话持久。

## 7. 资源 / 数据生成

### 物品模型
- **26.2 物品模型目录是 `assets/<modid>/items/<id>.json`**（新命名空间，替代旧 `models/item`；`models/item` 旧文件本项目仅残留 `warden_doll_head.json` 一个，可忽略）。
- 普通 `BlockItem` / 物品需要 `items/<id>.json` 引用模型，否则物品栏显示紫黑方块。
- **头颅 / 盾牌这类特殊渲染的物品不走普通模型**，`items/<id>.json` 用 `minecraft:special`（头颅用 `minecraft:head`，盾牌用自定义 `doll-mod:thorns_shield` SpecialModelRenderer，见 `SpecialModelRenderersMixin`）。→ 详 §3「自定义头颅」。
- 盔甲材质有独立的 `assets/<modid>/equipment/<id>.json`（如海洋套装 `sea.json`）。

### JSON 编码
- 资源 JSON 必须无 UTF-8 BOM，否则解析失败。

### 数据生成器
- 用 `DollDataGenerator`（Loom `DataGenEntrypoint`）批量生成 tag / recipe / advancement 等 JSON，**不要手写**。`runDatagen` 是覆盖式输出（直接覆盖磁盘 JSON）。

## 8. GUI / 渲染

- **边框后绘**：Screen 背景槽位先绘、边框后绘，否则边框被槽位盖住。
- **init 内重算布局**：窗口缩放会触发 Screen 重新 `init`，布局坐标须在 `init()` 内重算而非构造期固定。
- **两阶段 extract**：`extract(...)` 分两步防覆盖（先解析子区域再处理边距 / 裁剪），避免内容相互覆盖。
- **自定义实体渲染**：投掷物（末影斧 / 飞行剑）须自定义 `RenderState` + `Renderer`，用 `EntityDataAccessor` 同步姿态供渲染读取（见 §4 飞行地狱剑）。

## 9. 性能与优化模组兼容性

> 本模组与主流性能优化模组（Sodium、Lithium、FerriteCore、ImmediatelyFast）**高度兼容**，自身优化也已到位。以下为维护时须守住的红线，破坏任何一条都可能拖累性能优化模组或造成卡顿。

### 渲染层（与 Sodium / ImmediatelyFast 兼容的关键）
- 所有渲染器**必须走标准 `EntityRenderers.register` / `BlockEntityRenderers.register`** 注册；自定义实体渲染器继承 `EntityRenderer` / `HumanoidMobRenderer` 并用新 `SubmitNodeCollector` + `RenderTypes` 管线。→ `DollModClient.java`。
- 头颅 BER 走 26.2 `SkullBlockRenderState` 三阶段管线（`createRenderState` / `extractRenderState` / `submit`），`submit` 内**复用原版 `SkullBlockRenderer.submitSkull`**，勿自定义 buffer / 直接渲染。这是 Sodium 能正常接管渲染的关键。→ `AbstractDollHeadRenderer`。
- 自定义渲染类型选择 `RenderTypes.entityCutoutZOffset` / `entityGlint` 等标准类型，勿自定义 RenderType。
- 结论：当前渲染层全部合规，Sodium 的区块/实体渲染接管与 ImmediatelyFast 的网格批处理均能正常应用。

### tick / AI（与 Lithium 叠加时的成本控制）
- 自包含轻量 A*（`DollNavigator`）：`MAX_NODES=1024` 硬上限 + 复用 `cameFrom`/`gScore`/`neighborList` 防重分配 + `NAV_RETRY_COOLDOWN_TICKS=40` 防每 tick 重复 A* + 目标偏移小则复用已有路径。
- BFS 扫描（矿石 / 砍树）均有 `visited` 上限（矿石 8192 + `MINE_MAX_SCAN_TARGETS=8` 早停、砍树 `CHOP_MAX_TREE_BLOCKS`）+ **不扩展到未加载区块**（`isLoaded` 检查）。
- 不可达目标拉黑（`mineBlacklist` / `chopTreeBlacklist`），防反复寻路失败浪费算力。
- 掉落拾取 `DROP_PICKUP_INTERVAL=4` 限频 + 限定 AABB `getEntitiesOfClass`；召回位置登记降频至每 100 tick。
- 遍历方块优先 `BlockPos.MutableBlockPos` 复用，避免每格分配对象（见 §4「减少对象分配」）。

### 资源 / 搜索层（多人卡顿的历史教训）
- 群系搜索用 `getNoiseBiome` 纯噪声采样放**工作线程**（主线程只提交任务），勿用 `level.getBiome()`（会强制同步生成区块）。→ `DollNetworking.startBiomeSearchAsync`。
- 结构搜索减至 9 次 `findNearestMapStructure`（不生成区块），勿做全半径多次采样。
- `MAX_SEARCHES_PER_TICK=2` + 每玩家 2s 冷却 + LRU 结果缓存（上限 256 玩家）+ 断线清理，防主线程过载与内存泄漏。
- 召回前用 `getChunk(..., ChunkStatus.FULL, false)` 做存档存在性检查，**避免对不存在区块同步建块**（重模组存档可卡数秒）。

### 内存 / 生命周期
- 各静态登记表须防泄漏：`SearchMarkStore` 每玩家 `CAP=128` FIFO；`DollRecallRegistry` 服务器启动 `clear()`、人偶死亡/回收 `remove()`；搜索缓存断线清理。
- 历史问题：早期 `NoClassDefFoundError: DollRecallRegistry` crash 已修复（现 `SERVER_STARTED` 正确调用 `clear()`），并对跨存档残留位置触发同步建块做了加固。

---

> 以上技巧均经与 `io.github.a10086ovo` 包当前源码核对。策划 / 玩法 / 数值类内容（天赋、获取流程、战斗定位等）已移除，如需查阅以代码实现为准。
