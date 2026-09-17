# DEV_NOTES — DollMod 开发笔记

> 仅收录**可复用的开发技巧与 Fabric / MC 26.2 适配踩坑**，由本项目开发迭代整合而来。
> 一切以当前代码（`io.github.a10086ovo` 包）为准；过时的策划 / 玩法 / 设计性内容已删除。
> 行号锚点基于编写时的源码，仅作定位参考，后续以实际代码为准。

## 0. 构建与运行

- 环境：Minecraft 26.2 + Fabric Loader 0.19.3 + Fabric API 0.157.0+26.2；本地构建用 JDK 26（`fabric.mod.json` 要求 java ≥ 25；`gradle.properties` `group=io.github.a10086ovo`）。
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
  - `item` — 装备（`EnderAxeItem`、`NetherSwordItem`、`PaleBowItem`、`ThornsShieldItem`、`GuidePickaxeItem`）、头颅、盾、`GuideBookItem`、刷怪蛋 `DollSpawnEggItem`。
  - `block` — 7 种头颅方块（`*DollHeadBlock` + `*DollSkullType` + `*DollHeadBlockEntity`）+ `RockAnvilBlock`（三级损伤）+ `SculkShrineBlock`（祭坛）。
  - `config` — 外置配置 `DollConfig`（`config/dollmod/doll.json`）。
  - `guide` — 指南书数据模型与加载（自定义 JSON）。
  - `geo` — 底图索引子系统：`GeoIndex`（桶存储）/ `GeoIndexBuildJob`（分阶段构建）/ `GeoIndexService`（候选枚举 + 群系校验）/ `GeoIndexStorage`（落盘与版本自愈）/ `StructureGenVerifier`（生成判定）。见 §6。
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
  - `NetherSwordHealthMixin` — 下界剑手持生命上限 +10。
  - `WitherSkullMixin` — 下界人偶烈焰弹（凋灵骷髅头颅弹）。
  - `GuidePickaxeSmoothStepMixin` — 玩家持有登山镐（主手或副手）时平滑翻越一格高方块（`@Inject` 于 `LivingEntity#maxUpStep` 的 `RETURN`，仅取 `max(原值, 1.0)`；**不可用 `@Overwrite`**，否则会抹掉 `STEP_HEIGHT` 属性与骑乘加成）。
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
- 配 `Item.Properties.humanoidArmor(material, ArmorType)` + `.repairable(repairTag)`。→ `DollMod.java` 海洋套装（耐久基准 33、防御 3/8/6/3、附魔值 10、韧性 2.0）。
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
- **镐头选择策略（已与作者确认）**：
  - 能力判定用 `findBestPickaxeStack()`（背包里 Tier 最高的镐）。**修复缺陷**：旧 `findPickaxeStack()` 只按格子顺序返回第一把镐（石镐在前、钻石镐在后时，人偶误判"挖不动钻石矿"而绕开它）。
  - 实际挖掘用 `findPickaxeForState(state)`（在 `isCorrectToolForDrops(state)` 为真的镐里挑 Tier 最低的一把），把钻石 / 下界合金镐留给真正需要的矿，省耐久。
  - 分级 `pickaxeTierLevel()`：26.2 已移除 `TieredItem`，改为测 `isCorrectToolForDrops` 在参考方块上的结果定级——铁矿石（`NEEDS_STONE_TOOL`）=石级、钻石矿（`NEEDS_IRON_TOOL`）=铁级、黑曜石（`NEEDS_DIAMOND_TOOL`）=钻石级；下界合金镐单独记最高级，保证优先用钻石镐省耐久；木/金镐连铁矿石都挖不动 → 0 级。**正确性始终由 `isCorrectToolForDrops` 兜底，分级只用于"够用的镐里挑最弱"的排序**，即便分级偏差也绝不会选到不够用的镐。

### 药水效果常量名（26.2 改名，踩坑）
- 26.2 已将挖掘疲劳从 `MobEffects.DIG_SLOWDOWN` 改名为 **`MobEffects.MINING_FATIGUE`**（`DIG_SLOWDOWN` 编译直接报"找不到符号"）。凡涉及清除/施加挖掘疲劳（如海洋人偶清主人挖掘疲劳）务必用新名。
- 其余常用常量名在 26.2 保持：`SLOWNESS`、`SPEED`、`JUMP_BOOST`、`HASTE`、`POISON`、`WITHER`、`ABSORPTION`、`WATER_BREATHING` 等。若要核查任何效果常量，用 `javap -cp <minecraft-client.jar> net.minecraft.world.effect.MobEffects` 列全量字段。
- **吸收（金心）**：`ABSORPTION` 等级对应血量 = `4×(amp+1)`，无法精确表达"5 颗"（10 点）——amp=2 → 12 点（6 颗）。做"额外金色血量"类能力时按此换算并明确数值。

### 水下挖掘惩罚（26.2 已将 ÷5 属性化，勿用高等级急迫 hack）
- 26.2 "水中挖掘 ÷5"不再是硬编码，而是**原版属性 `Attributes.SUBMERGED_MINING_SPEED`（默认 base=0.2）**：`Player.getDestroySpeed` 中 `if (isEyeInFluid(WATER)) f *= getAttributeValue(SUBMERGED_MINING_SPEED)`。（`BlockState.getDestroyProgress` → `Player.getDestroySpeed` 即为方块破坏速度唯一链路。）
- 要"真正移除水中惩罚"（而非叠急迫）→ 给目标玩家 `SUBMERGED_MINING_SPEED = 1.0`：`AttributeModifier(id, +0.8, ADD_VALUE)`（0.2→1.0 恰好无惩罚）。属性自动服务端权威 + 同步客户端、天然"仅该玩家生效"。
- 推荐模式照抄 `GuideDollTalent` 的主护甲修饰：网格范围给 owner `addTransientModifier`、范围外 `removeModifier(id)`（powered 幂等，`AttributeInstance.hasModifier(id)`/`removeModifier(id)`）。
- **空中(悬浮)挖掘 ÷5** 是另一条硬编码 → 26.2 无对应属性，需注入 `getDestroySpeed` 才可破，风险更高，通常不做。

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
- **A\* 下降步幅**：常规下降邻居是 `cur` 正下方 1 格；另有 drop-edge 分支允许贴着边缘跳落最多 `MAX_SAFE_FALL_BLOCKS`（3）格——`dropColumnClear()` 要求下落通道无阻挡，`isSafeLanding()` 排除岩浆 / 火 / 岩浆块 / 仙人掌等伤害性落点（这类方块无碰撞箱，会被 `canOccupy` 误判成可落脚）。**（旧笔记曾写"每步下降天然 ≤1 格"，与 drop-edge 实现矛盾，已更正。）** → `DollNavigator.java`。
- **`MAX_SAFE_FALL_BLOCKS = 3`**：安全落差上限（格），盾构机悬崖判定复用同一常量，保证"人偶敢走下去"与"盾构机敢挖过去"口径一致。

### 入水自救 / 放水自困（2026-09-04 反编译实证）
- **陆偶在水里几乎无水平推力是"掉进水坑出不来"的根因**：26.2 `travelInWater` 的水平推力由 `WATER_MOVEMENT_EFFICIENCY`（`RangedAttribute` 默认 **0.0**）决定：`speed += (getSpeed()-speed)*waterWalker`，空中再 `*=0.5`。陆偶默认 0 → 入水 `speed≈0.02`、游不到坑沿 → 原版出水 `jumpOutOfFluid`（需 `horizontalCollision` 贴墙）永不触发 → 永久卡死（诊断铁证：`zza=1` 指令已下但 `vel≈0`、`onGround=false`）。
- **正解**：需要入水可自救的陆地类实体，在 `createXxxAttributes()` 里 `.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0)`，即可正常水平游向岸沿 → 由原版 `jumpOutOfFluid`(贴壁自动抬升 0.3) + `auto-step`(maxUpStep=1.0) 自行登岸。**这是纯原版物理根治，勿用逐 tick `setJumping`/直接 `setDeltaMovement` 强推**（会引入"跳出坑后一直跳跃停不下来"回归）。→ `entity/DollEntity.java:createDollAttributes`。
- **放水自困的另面**：寻路终点节点常停在"可站的井格"上 → 人偶会站上未放水井格、原地放水自困。修复：放水动作加双守卫（`applyFarmInput` 移动侧 + `updateFarmMind` 决策侧），当目标=未放水 anchor 且 `blockPosition()==anchor` 时，先经 `tryStepOffUnplacedWell`/`findStandCellBeside` 引到井格旁同层(或高1格)安全站格再放水。`findStandCellBeside` **绝不选低 1 格**（会被水源漫灌）。→ `entity/DollEntity.java`。

### 挖矿模式（MINE）与盾构机
- **设计决策（已与作者确认）**：① 跟随 + 挖矿共存 = 挖矿优先跟随（有矿目标时暂时离队去挖，挖完 / 无矿自动回跟随）；② 镐等级 = 严格匹配（`canPickaxeMine`，只挖当前镐挖得动且会掉落的矿）；③ "平地识别为悬崖"已修。
- **镐等级唯一真相源 `canPickaxeMine(BlockState)`**：选目标 / 单块挖 / 连锁 / 盾构机掘进 / 盾构机侧向探矿 五个入口全部调用它，**不要各写一份判断**。→ `entity/DollEntity.java`。
- **跟随离队边界**：`mineExcursionAllowed()` 受 `MINE_EXCURSION_MAX_TICKS`（5s）/ `MINE_EXCURSION_MAX_DIST_SQR`（12²）约束，否则人偶会一路追矿越跑越远。（注：砍树离队是 20s / 32²，与挖矿不同。）
- **扫描中心 `getMineScanCenter()`**：跟随时人偶离主人 ≤8 格用主人为中心（保留设计），离远改用人偶自身为中心，否则会挑主人身边够不着的矿 → 寻路失败 → 拉黑，表现为"跟随时不认矿"。
- **盾构机断面宽度**：普通为 1 宽 × 2 高；向导人偶**主手持有并使用**登山镐时扩为 3×3（每周期掘进 9 格），侧向探矿半径同步扩大，且跳过重力方块判定（岩浆 / 水仍会停）。判据是主手实际手持（`isHoldingGuidePickaxe()`）——放背包里、放副手都不算。
- **盾构机停止条件（`updateTunnelDrill`）一览，顺序即优先级**：
  1. `mine_stop_cliff`：前方落差 > `MAX_SAFE_FALL_BLOCKS` 才停（早期判定写反 → 平地秒停；后又过严 → 山区小空腔频繁误停，已放宽）。
  2. `mine_stop_lava`：前方 ±2 格有岩浆。
  3. `mine_stop_gravity`：前方是沙砾 / 沙子（3×3 宽断面模式跳过此判定）。
  4. `mine_stop_water`：前方两格有水（盾构机不游泳）。
  5. `mine_stop_unbreakable`：需要分级工具且 `!canPickaxeMine`（统一后不会漏判不在 `MINEABLE_WITH_PICKAXE` tag 内的分级方块）。
  6. `mine_stop_no_pickaxe`：镐空 → **必须在开挖前停**，否则深板岩这类 `requiresCorrectToolForDrops` 路障会被"零掉落"挖掉。
  7. `mine_stop_backpack_full`：背包满。
  - （开挖后仍校验断面各格 `digAll`（普通 1×2 / 宽断面 3×3）是否已挖通，否则 `mine_stop_blocked`；）
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

### 飞行下界剑（NetherFlyingSwordEntity）——继承 ItemEntity 做守护飞剑
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
- 数据为 JSON，位于 `assets/doll-mod/guide_book/`（book.json + categories + entries）。
- 加载在客户端进行（`GuideBookContent.get()` 传 `Minecraft.getInstance().getResourceManager()`），懒加载并缓存；服务端 `GuideBookItem` 只返回成功，打开动作由 `DollModClient` 注入 `openScreenAction`，避免 main 包引用 client。
- 每个新存档首次进入世界自动发放（`GuideBookGivenStore` 记录"按存档"的已发放状态；`SERVER_STARTED` 时清空内存记忆，使新存档可再次发放，同存档防重复靠 Mixin 写入 player.dat）。
- **图标引用用注册 ID（如 `doll-mod:xxx`），不是 Java 字段名。**

### 向导人偶搜索
- 入口：GUIDE 人偶**背包界面里的「搜索」图标** → `GuideSearchScreen`。屏内是「☰ 分类菜单 + 搜索框 + 匹配列表」整合视图，右上角另有「全域索引」按钮；结果视图带「◀ 返回 / 刷新」。分类仍是 结构 / 群系 / 村庄。
- 网络通道在 `DollNetworking`：C2S `search_request`、S2C `search_results`、C2S `toggle_search_mark`、S2C `structure_catalog`（结构清单，客户端无结构注册表，须由服务端下发）；索引相关通道见下节。
- **搜索范围已不是固定半径**：结构/村庄由 `ON_DEMAND_RADII = {2048,4096,8192,16384}` 由近及远分档（自适应刹车：某档 ≥50ms 不再放大，累计 ≥500ms 硬停）；群系由 `BIOME_SCAN_BANDS = {{2048,48},{8192,192},{16384,640}}` 近细远粗螺旋。`SEARCH_RADIUS_BLOCKS`(1600) **如今只剩「共享缓存复用窗口」一个用途**——旧文档把它当搜索半径，是误判的来源。
- **查询四级链路**（旧「索引优先」已重排）：① 玩家结果缓存（非 refresh）秒回 → ② 累积索引有候选则走生成校验 → ③ 首次搜该目标走 `collectOnDemand`（以**玩家**为中心按需收集并 `GeoIndex.merge` 进累积缓存，跨会话落盘）→ ④ 兜底才是原版 `findNearestMapStructure`（类型精确，跨 tick 分片）。
- 群系搜索**异步化**：主线程只取 `BiomeSource`/`Sampler` 引用、提交工作线程纯噪声采样（`getNoiseBiome`，不生成区块）。旧实现每格 `level.getBiome()` 强制同步生成区块，是多人卡顿主因。采样走**多档 Y**（`GeoIndexService.BIOME_PROBE_BLOCK_YS = {64,0,-64}`），故洞穴类群系（繁茂洞穴 / 深暗之域）可被搜到；结果带**命中高度 Y**（`SearchResultsPayload.Entry.y`，无法判定时为 `GeoIndexService.NO_Y`）。
- **结构 / 村庄候选的生成校验并行化**：`VERIFY_WORKERS = 8` 个线程共享 `AtomicInteger` 抢单，领号上限 = `MAX_RESULTS + failed`（在飞候选也算已领、失败退还名额，低通过率类型才不会少给结果），凑够 `MAX_RESULTS = 10` 条即整体收工；结果用候选位次做下标，天然保持「由近及远」。主线程不阻塞：由最后一个收工的工作线程 `server.execute(finishVerify)` 回主线程发包。村庄单候选 440~550ms（jigsaw 装配 + `WORLD_SURFACE_WG` 投影），是唯一慢目标。
- **世界级共享缓存**（多人核心收益）：以「维度:目标」存候选坐标条目，甲搜过、乙在复用窗口内以自己的坐标过滤 + 排序即可复用，同一目标多人不再各查一遍；服务器启动清空（结构位置随种子而定，跨世界不复用）。
- **连点保护**：`verifyInFlight`（同玩家同目标去重）+ `PENDING_SEARCH_MAX`。**旧的「每玩家 2s 全局冷却」已删除**。
- LRU 结果缓存（上限 `CACHE_MAX_PLAYERS = 256`）+ 断线清理，防内存泄漏。
- 打卡状态存 `SearchMarkStore`，玩家 NBT 键 `guide_search_marks`，跨会话持久。

### 全域索引（GeoIndex）
> **定性（本版定稿）**：索引是**「随用随长的缓存」，不是「开服预建的底图」**。旧做法在开服把「全维度 × 半径 16000 × 三高度」一次算完——实测新世界 81.4s（群系占 79%、未访问维度占 38%），且单个结构枚举不可让出会卡服。现在**开服零成本**，玩家点按钮才付这笔钱。

- **入口**：搜索屏右上角「全域索引」按钮 → C2S `index_build_request`(dollEntityId, cancel)。服务端**权威**：校验发起者持有**自己**的 GUIDE 人偶，并以**发起者当前位置**为中心（锚在出生点会在玩家走远后失效，这是旧实现的隐性错配）。
- **进度回报**：S2C `index_build_progress`(percent, phase, state)；state：`0` 进行中 / `1` 完成 / `2` 已取消 / `3` 空闲。**广播给全体在线玩家**（索引是服务端全局资源，谁建的都一样；单播会在发起者掉线后永久卡在"构建中"），取消也是全局的。客户端把「已取消」**归一成「空闲」**——否则按钮会停在"构建中"再也点不动。
- **阶段编号**（`GeoIndexBuildJob.PHASE_*`，也是 `IndexBuildProgressPayload.phase` 的**唯一权威编号**）：`0=结构 1=村庄 2=群系 3=村庄预确认 4=已结束`。`DollNetworking.INDEX_PHASE_END` 直接引用 `PHASE_DONE`，**勿各写一份数字**。
- **桶分两类语义，绝不能混**：`"category:targetIndex"` = **候选桶**（placement + 群系合法，**未装配**，用时必须再生成校验）；`GeoIndex.confirmedKey(cat,idx)` = **已确认桶**（已装配、可**免检**直接回放）。
- **村庄「已确认」**：建索引最后阶段把每类村庄离中心最近的 `VILLAGE_PRECONFIRM_TRIES = 16` 个逐个装配确认，存入已确认桶。装配在**独立**线程池 `CONFIRM_EXECUTOR`（8 线程，**刻意不复用搜索校验池**——否则"刚建完就搜"会排在上百个预确认任务后面）；主线程只**派发 + 每 tick 轮询** `Confirm.finished`。搜索侧一律**逐点免检**，**不得**改成"已确认桶非空就整条快速通道"（玩家走远后会给出远处村庄却漏掉脚下的）。
- **硬上界**：`GeoIndexService.MAX_REGION_RINGS = 128` 夹住单结构枚举量（`spacing=1` 的埋藏宝藏 / 废弃矿井从约 400 万次区域判定压到约 6.6 万次）；代价是密结构覆盖半径被压到 `128 × spacing` 区块。
- **群系级校验（`BiomeGate`）**：placement 级判定（`getPotentialStructureChunk` + `isStructureChunk`）**完全不含群系约束**，故候选必须再用 `Structure.biomes()` 过滤一次；允许群系与同集兄弟重叠的**混型集合**（下界堡垒↔要塞、基础传送门↔6 变体）**整类不做预索引**，回落到类型精确的实时路径。`BiomeGate` 探 **5 档** Y（64/320/0/-32/-64），**刻意比群系搜索的 3 档更宽**（宁宽勿窄：假阳性由后续生成校验逐点剔除，漏筛真点才不可挽回）。
- **进度权重必须按「实测耗时」折算**：`GeoIndexBuildJob` 的 `PROGRESS_BIOME_POINTS_PER_STRUCTURE = 2470`、`PROGRESS_BIOME_POINTS_PER_CONFIRM_ITEM = 1090` 都来自实测；**改任一阶段的常数都要回头重算权重**，否则进度条会卡在 1% 再一瞬冲到头（纯显示层参数，不影响结果）。
- **落盘与版本自愈**（`GeoIndexStorage`）：文件带保留键 `_schema`（`SCHEMA_VERSION`）与 `_built`（完成标记）。**凡改动索引语义 / 桶构成必须 `SCHEMA_VERSION += 1`**，加载时版本不符**整份丢弃**（新语义混进旧桶无法分辨哪些点脏）。**且必须告知玩家"请重新点一次「全域索引」"**：索引是玩家手动点的，旧文件被丢弃后不会自动重建。版本沿革：`1`（隐含，无版本字段；单成员结构集未做群系校验）→ `2`（群系级校验改为对**所有**结构都做；混型集合整类出局）→ `3`（新增 `_built`；此前"加载索引"与"是否需要重建"脱钩，每次开服都把已落盘的索引从零重扫一遍）→ `4`（新增村庄「已确认」桶）。
- **并发安全**：`GeoIndex.query()` 返回桶内**活列表**，要排序或长期持有必须改用 `querySnapshot()`（加锁内复制）——`merge()` 可能由工作线程调用（异步群系搜索、预确认收尾）。`StructureTemplateManager.structureRepository` 在 26.2 是 `ConcurrentHashMap`（源码已核），故工作线程并发 `Structure.generate` 安全——这正是并行校验的前提。
- **依赖方向**：生成判定统一放 `geo/StructureGenVerifier`（`Env` / `create` / `reallyGenerates`），搜索校验与建索引预确认**共用这一份**；方向必须是 `network → geo`，**禁止反向**（会成包循环）。
- **调试优先级**：坐标 / 索引类 bug，**离线解剖索引文件 > 开游戏实测**（更快且更有说服力）；`build/` 下留有几支读原版源码 / 离线解剖索引的诊断脚本。


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
- **自定义实体渲染**：投掷物（末影斧 / 飞行剑）须自定义 `RenderState` + `Renderer`，用 `EntityDataAccessor` 同步姿态供渲染读取（见 §4 飞行下界剑）。

## 9. 性能与优化模组兼容性

> 本模组兼容主流性能优化模组（Sodium、Lithium、FerriteCore、ImmediatelyFast）。以下为维护时须守住的红线，破坏任何一条都可能拖累性能优化模组或造成卡顿。

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
- 群系搜索用 `getNoiseBiome` 纯噪声采样放**工作线程**（主线程只提交任务；固定 3 线程池），勿用 `level.getBiome()`（会强制同步生成区块）。→ `DollNetworking.startBiomeSearchAsync`。
- 结构 / 村庄搜索**分两段、线程要求不同**：**候选枚举**读 `ChunkGeneratorStructureState`，须在服务端**主线程**（故 `collectOnDemand` 仍在主线程，靠 `ON_DEMAND_EXPAND_MAX_MS` / `ON_DEMAND_TOTAL_MAX_MS` 刹车兜住）；**生成校验**（`Structure.generate`）可放**工作线程**——`StructureTemplateManager.structureRepository` 在 26.2 是 `ConcurrentHashMap`（源码已核），故 `VERIFY_WORKERS = 8` 抢单并行安全。→ `DollNetworking.enqueueStructureVerify`。（**旧说法「结构搜索一律不可移工作线程」已过时**，照抄会误判。）
- 实时兜底仍走**跨 tick 分片**：`STRUCTURE_SLICES_PER_TICK = 3` + `pendingSearches`，每 tick 有界执行、消除单 tick 峰值。
- 结构位置随种子固定，落**世界级共享缓存**按中心点复用，多人搜索同一目标免重复调用 `findNearestMapStructure`（多人不卡之根本）。
- **连点保护**：`verifyInFlight`（同玩家同目标去重）+ `PENDING_SEARCH_MAX`（任务队列上限）——**旧的「每玩家 2s 冷却」已删除**；另有结构分片预算 + LRU 结果缓存（上限 `CACHE_MAX_PLAYERS = 256`）+ 断线清理，防主线程过载与内存泄漏。
- 召回前用 `getChunk(..., ChunkStatus.FULL, false)` 做存档存在性检查，**避免对不存在区块同步建块**（重模组存档可卡数秒）。

### 内存 / 生命周期
- 各静态登记表须防泄漏：`SearchMarkStore` 每玩家 `CAP=128` FIFO；`DollRecallRegistry` 服务器启动 `clear()`、人偶死亡/回收 `remove()`；搜索缓存断线清理。
- 历史问题：早期 `NoClassDefFoundError: DollRecallRegistry` crash 已修复（现 `SERVER_STARTED` 正确调用 `clear()`），并对跨存档残留位置触发同步建块做了加固。

---

> 以上技巧均经与 `io.github.a10086ovo` 包当前源码核对。策划 / 玩法 / 数值类内容（天赋、获取流程、战斗定位等）已移除，如需查阅以代码实现为准。
