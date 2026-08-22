# 人偶模组（Doll Mod）

一个把人偶当作**可消耗工具**来用的 Minecraft 模组：用刷怪蛋召唤属于你的、可自定义名字的人偶，给它装备武器与盔甲，再用 8 种行为模式指挥它干活。人偶是消耗品——死了会连配置一起丢失，所以放心派它去危险环境、成群流水线作业。

> 依赖 Fabric API，目标版本 Minecraft 26.2（Java 25）。

## 玩法流程

1. **合成 S1 蛋**：上下左右四个工作台 + 中间箱子 → S1 人偶刷怪蛋。
2. **铁砧赐名**：只有命名过的蛋才能召唤人偶（命名后蛋带附魔光效）。
3. **召唤/召回**：右键地面召唤；已绑定的蛋再右键地面把人偶传送到身边（支持跨维度、区块未加载自动等待加载）。
4. **升级不丢东西**：回收人偶后把蛋放工作台中间围升级材料升级，蛋的名字、物品栏（含护甲/副手）、作业区与盾构机配置会原样带到新蛋上，再召唤无需重新赐名、装备物品全部保留。注意蛋必须处于"未绑定"状态（回收后或从未召唤过）才能升级。
   - S1 → S2：围一圈铜锭
   - S2 → S3：十字铁锭 + 四角铜锭
   - S3 → S4：十字金锭 + 四角铁锭
   - S4 → S5：上下钻石 + 左右金锭 + 四角铁锭
5. **背包**：右键人偶打开 45 格专属背包（装饰/护甲/副手/存储/快捷栏），左侧实时显示血量。
6. **模式**：背包右侧 9 个按钮切换 8 种行为模式 + 跟随开关；也可以用**人偶遥控器**（`doll_panel`，铁锭-红石-铁锭竖排合成）右键任意位置打开控制面板，远程查看/切换所有已召唤人偶。
7. **指挥棒**（`doll_baton`，三根木棍对角合成）：
   - **作业模式**（砍树/耕种/挖矿）：右键人偶选中 → 右键方块两点划定**作业区**；挖矿模式下右键两个竖方块设定**盾构机入口**，沿点击面反方向掘进 1×2 隧道。
   - **战斗模式**（近战/射手）：右键人偶进入**目标选择模式** → 右键任意生物指定为攻击目标，人偶立即追杀；右键地面取消选择。幽匿人偶在非作业模式下均可指定。
   - 潜行右键人偶清除作业区/隧道/强制目标；作业区单边上限 64 格。

## 8 种行为模式

| 模式 | 行为 |
|------|------|
| 近战 | 主动攻击主人攻击的目标与附近敌对生物（需快捷栏放剑/斧等武器） |
| 射手 | 拉弓/弩射击，自动预判目标移动、追踪修正（需弓/弩 + 箭） |
| 耕种 | 锄地、播种、收获、灌水（需锄头/种子/水桶） |
| 喂食 | 贴身跟随主人，饥饿/低血时喂食 |
| 砍树 | 找真树（原木连通体 + 邻接树叶判定），连锁砍完整棵树（需斧头） |
| 挖矿 | BFS 扫描矿石，价值评分选目标，连锁挖同族矿脉（需镐子） |
| 插火把 | 寻找方块光与天空光都低的位置补光（需火把） |
| 钓鱼 | 找水域抛竿、等咬钩、按原版战利品表收鱼（需钓鱼竿） |

## 人偶等级（五阶阶梯式成长）

| 阶段 | 生命值 | 常驻效果 |
|------|--------|----------|
| S1 | 20 | 无 |
| S2 | 40 | 生命恢复 I |
| S3 | 60 | 生命恢复 II |
| S4 | 80 | 生命恢复 III + 抗性提升 I |
| S5 | 100 | 生命恢复 IV + 抗性提升 II |

工具材质等级（木/石/铁/钻石/下界合金）决定人偶能挖/砍动什么方块。

特殊天赋人偶（幽匿/苍白/末影/下界/海洋）独立于 S1-S5 体系，统一为 120 血 + 恢复 V + 抗性 III + 抗火；幽匿人偶为终局强化版，200 血 + 恢复 V + 抗性 IV + 抗火。满级击退抗性为幽匿人偶独有（对标监守者）。

## 幽匿人偶（终局内容）

远古城市专属的高风险、高回报内容，与人偶系统完全兼容。

1. **触发**：手持**回响碎片**右键**幽匿灵龛**，消耗一个碎片召唤野生幽匿人偶。每个灵龛在上一个野生幽匿人偶死亡前不能再次召唤（状态持久化至 NBT，区块重载不丢失）。
2. **击败野生体**：250 血 / 8 攻击力 / 满抗击退 / 免疫火焰；目标脱离近战范围 4 秒后蓄力音波攻击（1.5 秒前摇 + 20 点伤害 + 拉扯玩家）；出场时从灵龛中上升 2 格，伴随粒子特效，期间无敌。被击败后 40% 概率掉落幽匿人偶头颅，每级抢夺 +10%。
3. **合成幽匿人偶蛋**：工作台中用 8 种矿物（煤/铜/铁/金/青金石/红石/绿宝石/钻石）围住头颅 → 幽匿人偶蛋（仍需在铁砧赐名后才能召唤）。
4. **驯服形态**：与普通人偶玩法完全一致（跟随、工作模式、指挥棒、回收），属性为 200 血 + 恢复 V + 抗性 IV + 抗火 + 满抗击退。

### 天赋：音波攻击

在近战/射手模式下锁定敌人后被动触发，无距离限制（贴脸可放）：

1. 播放蓄力音效（`WARDEN_SONIC_CHARGE`），蓄力 1.5 秒（30 tick）
2. 发射音波：播放音爆音效（`WARDEN_SONIC_BOOM`）+ 从人偶到目标画音波粒子线 + 拉扯目标向人偶方向移动 + 造成 20 点伤害 + 施加缓慢 V（5 秒）
3. 发射后进入 4 秒（80 tick）冷却，冷却结束后再次蓄力

音波伤害与普通攻击独立，不影响人偶的近战/射击节奏。缓慢效果持续 5 秒，覆盖 4 秒冷却期，确保目标在冷却结束时仍受减速影响。

### 天赋：掠夺者

- 击杀生物时掉落经验 ×3
- 攻击自带抢夺 III（额外掉落概率和数量按原版抢夺 III 计算）

## 苍白人偶

苍白花园主题人偶，拥有独特的恐惧光环和献祭天赋。与普通人偶共享 8 种行为模式、背包、指挥棒、回收等基础系统。

### 获取流程

1. **获取苍白人偶头颅**：在苍白花园中找到嘎枝之心（苍白橡树中 10% 概率生成）→ 破坏后获得树脂块 → 熔炉烧制为树脂砖 → 4 个树脂砖 2×2 工作台合成苍白人偶头颅。
2. **合成苍白人偶蛋**：8 苍白橡木原木围一圈，中间放苍白人偶头颅 → 苍白人偶蛋（仍需铁砧赐名后才能召唤）。
3. **召唤**：苍白人偶为 0 阶，但因特殊变体属性，血量固定 120（不受等级影响）。

### 属性

| 属性 | 数值 |
|------|------|
| 生命值 | 120 |
| 永久药水效果 | 恢复 V + 抗性 III + 抗火 |
| 击退抗性 | 无（仅幽匿人偶独有） |

### 天赋：恐惧光环

16 格半径，每秒扫描一次光环内的敌对生物：

- **失去 AI**：光环内敌对生物的索敌、寻路、游荡、碰撞攻击全部停止，变成活靶子。对史莱姆等不依赖目标判定的生物同样有效。
- **自动恢复**：生物离开光环范围后自动恢复 AI。人偶死亡或被回收时恢复所有受影响生物。
- **光环中心**：跟随时为玩家位置，不跟随时为人偶自身位置。

### 天赋：67% 易伤

光环内敌对生物受到的所有伤害 ×1.67，与恐惧光环共享同一范围和中心判定。

### 天赋：献祭（以命相抵）

当玩家受到致命伤害即将死亡时自动触发，按以下优先级搜索苍白人偶：

1. **放出的实体**：搜索 16 格内属于该玩家的存活苍白人偶（取最近的）→ 触发后击杀实体
2. **物品栏中的蛋**：搜索玩家物品栏中**命名过的**苍白人偶蛋 → 触发后将蛋标记为失效（不消失，变为失效蛋留作纪念）

未命名的苍白人偶蛋无法触发献祭。

触发效果：
- HUD 弹出苍白人偶头颅图标（非原版不死图腾图标）
- 播放不死图腾音效 + 视觉特效
- 应用不死图腾药水效果（恢复 II、抗性、吸收等）
- 玩家获得 +100 临时最大生命值（20→120），持续 60 秒后移除
- 玩家恢复至满血（120），获得 2 秒无敌帧
- 苍白人偶当场死亡 / 蛋变为失效状态
- 阻止玩家死亡

> 虚空、`/kill` 等绕过无敌的伤害不触发献祭。
> 命名过的蛋视为一种"不死图腾"，带在身上即可触发，无需放出人偶。

### 苍白人偶头颅方块

可放置、可佩戴的头颅方块（`pale_doll_head`），使用苍白人偶皮肤纹理渲染。技术实现见「开发」章节。

## 下界人偶

来自下界的火焰主题人偶，通过工作台合成获取。具备烈焰弹、凋零/灵魂沙免疫和下界生物安抚光环三大天赋。

### 获取方式

1. **下界人偶头颅**：工作台中 4 个石英块 2×2 摆放 → 下界人偶头颅。
2. **下界人偶蛋**：工作台中 8 个下界岩围住头颅 → 下界人偶蛋（仍需在铁砧赐名后才能召唤）。

### 属性

| 属性 | 数值 |
|------|------|
| 生命值 | 120（固定，不受等级影响） |
| 永久药水效果 | 恢复 V + 抗性 III + 抗火 |
| 击退抗性 | 无（仅幽匿人偶独有） |

### 天赋

1. **烈焰弹**：近战/射手模式下锁定目标后自动发射凋灵骷髅头颅弹（WitherSkull），无需武器。无蓄力前摇，3 秒冷却，命中造成 13 点伤害并点燃 5 秒。弹道投射物可被方块遮挡。头颅不炸方块（只伤害实体），安全在木质建筑附近使用。
2. **凋零免疫**：免疫凋灵 Boss 和凋灵骷髅施加的凋零效果。
3. **灵魂沙免疫**：在灵魂沙和浆果丛上不减速，移动速度不受影响。
4. **下界生物安抚光环**：16 格半径内（跟随时以玩家为中心），下界生物无法对人偶及其主人建立仇恨。受影响的下界生物包括：僵尸猪灵、猪灵、猪灵蛮兵、疣猪兽、僵尸疣猪兽、恶魂、岩浆怪、烈焰人、凋灵骷髅、凋灵。生物可自由移动并攻击其他目标，只是不会主动攻击人偶和主人。机制等价创造模式（见「开发」章节）。

## 末影人偶

来自末地的龙息主题人偶，通过工作台合成获取。具备龙息喷吐和瞬移处决两大天赋。

### 获取方式

1. **末影人偶头颅**：工作台中 4 个紫珀块 2×2 摆放 → 末影人偶头颅。
2. **末影人偶蛋**：工作台中 8 个末影石围住头颅 → 末影人偶蛋（仍需在铁砧赐名后才能召唤）。

### 属性

| 属性 | 数值 |
|------|------|
| 生命值 | 120（固定，不受等级影响） |
| 永久药水效果 | 恢复 V + 抗性 III + 抗火 |
| 击退抗性 | 无（仅幽匿人偶独有） |

### 天赋

1. **龙息喷吐**：近战/射手模式下锁定目标后自动发射凋灵骷髅头颅弹（WitherSkull，和下界人偶统一投射物），无需武器。无蓄力前摇，3 秒冷却。头颅弹外观为人偶 3D 头颅（按变体选择贴图：末影人偶用 ender_doll.png，下界人偶用 nether_doll.png）。命中后直接造成 2 点伤害并生成龙息云（AreaEffectCloud，半径 3 格，持续 30 秒，每 10 tick 判定一次即时伤害 II），对范围内所有生物施加即时伤害 II 药水效果。龙息云不破坏方块，且只伤害敌对生物（实现 Enemy 接口），所有非敌对生物（动物、村民、铁傀儡、玩家、其他人偶等）自动被保护。发射时播放烈焰人射击音效。
2. **瞬移处决**：当锁定目标血量低于 30% 时触发。人偶瞬移到目标身边发动斩杀（Float.MAX_VALUE 伤害），随后瞬移回原位。60 秒冷却。瞬移时播放末影人传送粒子和音效。
3. **80% 闪避**：受击时（攻击类伤害，来源实体非空）有 80% 概率完全免伤并瞬移躲避。触发时在原位置与落点各播放一次末影人传送粒子（PORTAL）与音效（ENDERMAN_TELEPORT）；落点为当前位置半径 4 格内的随机可站立空位，找不到安全落点时降级为原地免伤（仍播放特效）。环境伤害（跌落、火焰、虚空等来源实体为空）正常结算，不受该天赋影响。



## 海洋人偶

来自海洋的水下主题人偶，通过工作台合成获取。具备海洋生物安抚光环、水下适应、钓鱼必出宝藏、守卫者激光、主人增益光环五大天赋。

### 获取方式

1. **海洋人偶头颅**：工作台中 4 个海晶石 2×2 摆放 → 海洋人偶头颅。
2. **海洋人偶蛋**：工作台中 8 个海带围住头颅 → 海洋人偶蛋（仍需在铁砧赐名后才能召唤）。

### 属性

| 属性 | 数值 |
|------|------|
| 生命值 | 120（固定，不受等级影响） |
| 永久药水效果 | 恢复 V + 抗性 III + 抗火 |
| 特殊能力 | 水下呼吸（不溺水） |
| 击退抗性 | 无（仅幽匿人偶独有） |

### 天赋

1. **海洋生物安抚光环**：16 格半径内（跟随时以玩家为中心），海洋敌对生物无法对人偶及其主人建立仇恨。受影响的生物包括：守卫者、远古守卫者、溺尸。生物可自由移动并攻击其他目标，只是不会主动攻击人偶和主人。机制等价创造模式（见「开发」章节）。
2. **水下适应**：① 不会溺水（水下呼吸常驻）；② 水下全速移动——覆写 `getWaterSlowDown()` 使水中移速等同陆地；③ 仿溺尸自由行动——`DollNavigator` 为海洋人偶单独开放水下寻路（`allowWater` 专属），可沿水柱下潜/上浮，其他八种人偶不受影响。
3. **钓鱼必出宝藏**：FISH 模式下抛竿钓鱼必定产出宝藏战利品（不依赖运气附魔）。
4. **守卫者激光**：近战/射手模式下锁定目标后自动蓄力发射 hitscan 激光，无需武器。蓄力 1.5 秒，冷却 1 秒，命中造成 9 点魔法伤害（无视护甲），需视线、射程 16 格、可被方块遮挡。蓄力时显示瞄准线粒子并播放渐强压迫音，发射瞬间有响亮一击音效与可见光束。
5. **主人增益光环**：主人在人偶 16 格半径内时获得增益——水下呼吸（只要在范围内持续给予）；水下速掘（仅当主人**身处水中**时给予**急迫 XXV** 高等级效果，抵消水中非地面挖掘约 ÷5 的减速，使游泳时挖掘≈陆地、站海底时明显更快；离开水不加速）。效果约 10 秒，每 20 tick 刷新，离开范围后自然过期。仅主人受益，且仅海洋人偶触发。

### 森林人偶天赋

1. **藤蔓缠绕（减速 + 中毒）**：16 格半径内（跟随时以玩家为中心）的陆地敌对生物被持续牵制——`Slowness II`（2 秒，每 20 tick 刷新）+ 温和 `Poison I`（中毒 I，2 秒，同频刷新）。入侵者踏入即被减速并缓慢掉血，离开光环自然过期。受影响的生物：`zombie`/`husk`/`zombie_villager`/`skeleton`/`stray`/`bogged`/`spider`/`cave_spider`/`creeper`/`witch`/`slime`/`ravager`/`vindicator`/`pillager`/`evoker`/`vex`（故意不含 `enderman`/`phantom`/`drowned`，语义不符"森林"）。
2. **友善生物吸引**：8 格半径内未驯服的友善动物（猪/羊/牛/鸡/兔/蘑菇牛/马/驴/骡/羊驼/猫/狼/鹦鹉/蜜蜂/海龟/狐狸/嗅探兽/山羊/骆驼，共 19 种）被吸引着以正常步速走向人偶（对齐原版手持小麦的 `TemptGoal` 满速行为，不额外加速、不黏着）。已驯服宠物（狼/猫等）被排除，避免跟主人走丢。离开范围后动物恢复自身 AI。
3. **主人回血光环**：主人在人偶 16 格半径内时持续获得 `Regeneration I`（生命恢复 I，约 10 秒，每 20 tick 刷新），离开范围自然过期。仅主人受益，联机时朋友不享受。
4. **常驻喂食（不消耗物品）**：森林人偶开启 FEED 模式无需背包有食物（其他变体仍需前置食物）。无食物时人偶安静地（不挥手）每 0.75 秒给主人固定 +6 饥饿（3 大格），不消耗任何物品；有食物时走原版消耗逻辑递喂。表现与"主人回血光环"同为被动滋养，无主动挥手表演。
5. **近身威胁高亮**：以主人为中心 4 格半径内的陆地敌对生物获得 `Glowing` 效果（光灵箭同款白色描边，3 秒，每 20 tick 重刷），方便主人看清贴脸威胁。离开范围或人偶不在场即自然过期。

> 注：早期设计稿曾包含"工作方块加速（熔炉/高炉/烟熏炉/营火 3×）"与"作物催长（小麦/胡萝卜/马铃薯/甜菜/树苗/甘蔗 3×）"两项，后因实现成本高（需 Mixin 拦截方块实体 tick 与全局随机刻派发，作用面宽、易引入影响整个世界作物生长的bug）且偏离"一切贴近原版"的设计理念，已从规划中移除，森林人偶最终不实现这两项。

> 以上六种特殊天赋人偶（幽匿/苍白/下界/末影/海洋/森林）**已全部实现并实机验证**。当前无规划中变体。

**光环系统**：16 格半径，跟随时光环中心为玩家，不跟随时为人偶自身。

## 开发

### 设计理念

**一切贴近原版 Minecraft，原版怎么做就怎么做，不到万不得已不擅自主张。**

这条原则适用于 API 调用、注册模式、翻译键规范、方块/物品行为、命名约定等所有方面，具体体现为以下设计决策：

1. **能用原版机制就不造轮子** — 凋零免疫用 `@Override canBeAffected()` 而非 Mixin 拦截；灵魂沙免疫用 `@Override getBlockSpeedFactor()`。需要修改原版行为时，优先用 Mixin 拦截原版调用链中的某个点（如 `canAttack`），而非绕过原版自己实现一套。

2. **人偶是消耗品，不是宠物** — 人偶死了就死了，配置、装备、名字全部丢失，没有复活机制。这决定了模组的玩法张力：玩家愿意派人偶去危险环境，但也要承担损失风险。

3. **变体用枚举分支，不建子类** — 所有变体（幽匿/苍白/下界/末影/海洋/森林）共享同一个 `DollEntity` 类，通过 `DollVariant` 枚举做行为分支。新增变体只需加枚举值和 switch 分支，不引入类继承层级膨胀。

4. **统一投射物，按 owner 区分行为** — 下界人偶和末影人偶共用原版 `WitherSkull`，在 Mixin 中通过 `getOwner()` 检查 `DollVariant` 区分命中效果（下界=13 伤+燃烧 5 秒，末影=2 伤+龙息云）。新增远程变体只需在 switch 中加一个 case，不需要新建投射物类。

5. **友军保护用 `instanceof Enemy` 反转逻辑** — 不维护友善生物白名单，而是判断目标是否实现 `Enemy` 接口：是敌对生物才受伤害，非敌对自动被保护。原版新增友善生物类型时无需修改模组代码。

6. **覆写优先于 Mixin** — 能用 `@Override` 实现的不用 Mixin。Mixin 是最后手段，只在原版方法 `private`/`final` 或需要拦截内部调用链时使用。覆写有编译期签名检查，不会因原版重构导致运行时注入失败。

### 快速上手

```bash
# 1. 设置 JDK（必须 25+，本项目用 JDK 26）
export JAVA_HOME=E:/Java/JDK26

# 2. 全量编译（首次会下载 MC 依赖，约 5-10 分钟）
./gradlew build

# 3. 启动游戏客户端测试
./gradlew runClient

# 4. 只编译服务端代码（快速检查编译错误）
./gradlew compileJava

# 5. 重新生成数据包（配方 + 翻译）
./gradlew runDatagen
```

> **注意**：`JAVA_HOME` 必须指向 JDK 25+，否则 Gradle 会找到旧的 JDK 17 导致配置阶段失败。

### 环境与路径

| 项 | 值 |
|---|---|
| 项目路径 | `E:\template-mod-template-26.2\template-mod-template-26.2` |
| JDK | `E:\Java\JDK26`（JDK 26.0.2），构建要求 JDK 25+ |
| 构建 | `./gradlew build`（编译 `./gradlew compileJava`） |
| datagen | `./gradlew runDatagen` |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.157.0+26.2 |
| Loom | 1.17-SNAPSHOT |
| 映射方案 | Mojang 官方映射（mojmap），**不使用 Yarn**（无 Yarn 26.x） |
| modid | `doll-mod` |
| 包根 | `com.example.doll`（main）/ `com.example.client`（client mixin）/ `com.example.doll.client`（client 渲染/GUI） |

> 构建/编译前必须设置 `JAVA_HOME=E:/Java/JDK26`，否则 Gradle 会找到旧的 JDK 17 导致失败。

### AccessWidener

文件：`src/main/resources/doll-mod.accesswidener`

26.2 中部分原版 API 是 `private`/包级可见，Mixin 无法放在 `net.minecraft.*` 包（会抛 `IllegalClassLoadError`），此时用 accessWidener 提权：

| 提权项 | 原因 |
|---|---|
| `MenuType$MenuSupplier`（class） | `MenuType` 构造需要此接口，原版包级可见 |
| `MenuType.<init>`（method） | 注册自定义 MenuType 需调用此构造器 |
| `MenuScreens$ScreenConstructor`（class） | `MenuScreens.register` 的参数类型，原版包级可见 |
| `MenuScreens.register`（method） | 注册屏幕渲染器，原版 private |

> 提权后由放在同包的 `DollScreenRegistration` 完成注册。需要提权新的原版 API 时，在同一个 `.accesswidener` 文件中追加行即可。

### 网络协议

客户端 ↔ 服务端通信使用 Fabric Networking API（CustomPacketPayload）。共 4 个包，2 个方向：

**客户端 → 服务端（serverbound）**

| Payload 类 | 触发场景 | 携带数据 |
|---|---|---|
| `SelectDollModePayload` | 遥控器面板点击模式按钮 | `dollEntityId`（实体 ID）+ `modeSlot08`（0-8 格子编号） |
| `RecallDollPayload` | 遥控器面板点击召回按钮 | `dollUuid` + `dimensionName` + `lastX/Y/Z`（客户端不依赖服务端注册表，直接带坐标） |

**服务端 → 客户端（clientbound）**

| Payload 类 | 触发场景 | 携带数据 |
|---|---|---|
| `OpenDollControlPanelPayload` | 遥控器右键任意位置 | `List<DollSnapshot>`（所有存活人偶快照 + 离线人偶） |
| `UpdateDollSnapshotPayload` | `switchMode` 成功后推送给 owner | 单个 `DollSnapshot`（控制面板据此高亮选中行） |

**`DollSnapshot` record 字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `entityId` | int | 实体 ID（-1 = 离线人偶，仅用于同维度切模式） |
| `uuid` | String | 人偶 UUID（召回用，跨维度/跨加载） |
| `name` | String | 显示名 |
| `level` | int | 蛋等级 |
| `activeMode` | int | 当前模式（-1 = 空闲） |
| `followEnabled` | boolean | 跟随开关 |
| `isTunneling` | boolean | 盾构机掘进中 |
| `inSameDimension` | boolean | 是否与玩家同维度 |
| `distanceSqr` | int | 同维度距离平方（异维度 = MAX_VALUE） |
| `dimensionName` | String | 维度 path（overworld/the_nether/the_end） |
| `lastX/Y/Z` | int | 最后已知坐标（离线人偶定位用） |

**注册入口**：`DollNetworking.register()`（服务端）+ `DollClientNetworking.registerReceivers()`（客户端）。

### 数据生成

- `./gradlew runDatagen`
  - **datagen 管理的**：S1 蛋、指挥棒、遥控器配方与全部翻译键，权威来源是 `DollDataGenerator`，改文案必须同步改它，否则重新生成会被覆盖。翻译键的生成产物位于 `src/main/generated/assets/doll-mod/lang/`（`en_us.json` / `zh_cn.json`），**勿在 `src/main/resources` 下寻找手写 lang 文件**——本仓库没有手写的语言文件。
  - **手写特殊配方**（datagen 不生成）：S2~S5 升级配方和幽匿人偶蛋合成配方，位于 `src/main/resources/data/doll-mod/recipe/`，序列化器类型 `upgrade_tier2` ~ `upgrade_tier5` 与 `warden_doll_synthesis`，合成时搬运蛋的 NBT（见 `DollUpgradeRecipe` / `WardenDollSynthesisRecipe`）。

### 源码结构

`src/main`（服务端/通用逻辑） | `src/client`（客户端逻辑：渲染、GUI、mixin）

**main 源文件**

| 文件 | 职责 |
|---|---|
| `DollMod` | 主类，注册所有方块/物品/实体/BlockEntity/配方序列化器/菜单/事件 |
| `DollModConstants` | 所有注册 ID 字符串常量 |
| `DollCreativeTab` | 创造模式标签页注册（所有物品加入 `doll_tab` 标签页） |
| `entity/DollEntity` | **核心实体类（6200+ 行）**，包含全部 8 种模式 AI、战斗、变体、光环、NBT 持久化 |
| `entity/WildWardenDollEntity` | 野生幽匿人偶——独立于 DollEntity 的敌对生物，继承 `Monster`，用原版 Goal 系统 |
| `entity/DollVariant` | 变体枚举（**全部已实现**）：`NONE` / `WARDEN` / `PALE` / `NETHER` / `ENDER` / `SEA` / `FOREST` |
| `entity/DollRecallRegistry` | 蛋召回时跨维度/区块加载的传送登记 |
| `entity/DollNavigator` | 自包含 A* 网格寻路器，不依赖原版 Mob 寻路体系，支持绕障和翻越一格台阶（海洋人偶专属 `allowWater` 可沿水柱下潜/上浮，其他人偶不受影响） |
| `entity/PaleSacrificeHandler` | 苍白人偶献祭——监听 `ServerLivingEntityEvents.ALLOW_DEATH` |
| `mode/DollMode` | 8 种行为模式枚举（MELEE=0 ~ FISH=7），FOLLOW=8 独立开关 |
| `item/DollSpawnEggItem` | 人偶蛋物品，构造接收 `(level, DollVariant)` |
| `item/DollBatonItem` | 指挥棒——作业区选区 + 目标选择模式状态机 |
| `item/DollControlPanelItem` | 遥控器——远程打开控制面板 |
| `inventory/DollInventory` | 人偶 45 格背包（装饰/护甲/副手/存储/快捷栏） |
| `screen/DollScreenHandler` | 背包 GUI 的 Slot/同步逻辑 |
| `block/SculkShrineBlock` | 幽匿灵龛——回响碎片召唤野生幽匿人偶 |
| `block/WardenDollHeadBlock` | 幽匿人偶头颅方块（8 层架构参考实现） |
| `block/PaleDollHeadBlock` | 苍白人偶头颅方块 |
| `block/NetherDollHeadBlock` | 下界人偶头颅方块 |
| `block/EnderDollHeadBlock` | 末影人偶头颅方块 |
| `block/RockAnvilBlock` | 石砧——三级损伤链（正常→破损→损坏），通过 Mixin 接管原版铁砧损伤逻辑 |
| `recipe/DollUpgradeRecipe` | 自定义升级配方序列化器，8 位置多材料数组 |
| `recipe/WardenDollSynthesisRecipe` | 幽匿人偶蛋合成配方序列化器 |
| `data/DollDataGenerator` | 配方 + 中英文翻译的数据生成入口 |
| `network/DollNetworking` | 服务端网络包注册与处理 |
| `util/DollEntityLookup` | 跨维度人偶查找（`ServerLevel.getEntityInAnyDimension(uuid)`） |

**client 源文件**

| 文件 | 职责 |
|---|---|
| `DollModClient` | 客户端初始化：实体渲染器/BER/屏幕注册 |
| `client/DollSkullState` | Duck Typing 接口，让 `WitherSkullRenderState` 携带 `DollVariant` 标记，区分人偶/原版发射的头颅 |
| `client/mixin/DollEntityClientMixin` | 让 DollEntity 实现 `ClientAvatarEntity`，按变体切换皮肤纹理（doll/warden_doll/warden_doll_wild/pale_doll/nether_doll/ender_doll/sea_doll/forest_doll） |
| `client/mixin/DollEntityClientMixin` | 让 DollEntity 实现 `ClientAvatarEntity`，按变体切换皮肤纹理（doll/warden_doll/warden_doll_wild/pale_doll/nether_doll/ender_doll/sea_doll/forest_doll）。**稳定性**：纯净版稳定，用户实测不崩。曾因植入诊断代码（`getSkin()` 内调 `TextureManager.getTexture` 唤醒渲染管线）导致偶发 `avatarState()` NPE，诊断代码已回滚，非既有 bug。详见文末「历史事故记录」。 |
| `client/mixin/AvatarRendererDollMixin` | 首次渲染诊断日志 |
| `client/mixin/SkullBlockRendererMixin` | 注入原版 `SkullBlockRenderer`，注册自定义头颅皮肤和模型 |
| `client/mixin/ClientPacketListenerMixin` | 苍白献祭触发时将不死图腾 HUD 图标替换为苍白人偶头颅 |
| `client/mixin/MinecraftUseItemOnDollMixin` | 阻断右键人偶时弓/食物误触发使用 |
| `client/mixin/MinecraftStopUseItemMixin` | 打开背包时停止正在使用的物品 |
| `client/renderer/blockentity/*DollHeadRenderer` | 4 个头颅方块的 BlockEntityRenderer（8 层架构层 7），世界中 3D 渲染 |
| `client/renderer/entity/WildWardenDollRenderer` | 野生幽匿人偶实体渲染器 |
| `screen/DollInventoryScreen` | 人偶背包 GUI 渲染 |
| `screen/DollControlScreen` | 遥控器控制面板 GUI |
| `net/minecraft/.../DollScreenRegistration` | 26.2 屏幕 注册绕过：`MenuScreens.register` 为 private，用同包普通类 + accessWidener 提权（Mixin 不能放在 `net.minecraft.*` 包） |

### DollEntity 架构速查

**关键枚举**

| 枚举 | 位置 | 值 |
|---|---|---|
| `DollMode` | `mode/DollMode.java` | MELEE=0, RANGED=1, FARM=2, FEED=3, CHOP=4, MINE=5, TORCH=6, FISH=7 |
| `DollVariant` | `entity/DollVariant.java` | NONE=0, WARDEN=1, PALE=2, NETHER=3, ENDER=4, SEA=5, FOREST=6（**已全部实现并落地**，含蛋/头颅/皮肤/创造栏/datagen/三处属性一致） |

**SynchedEntityData 同步字段**

| 字段 | 类型 | 用途 |
|---|---|---|
| `DATA_ACTIVE_MODE` | Integer | 当前模式（0-7） |
| `DATA_FOLLOW_ENABLED` | Boolean | 跟随开关 |
| `DATA_DOLL_LEVEL` | Integer | 等级（0=S1 ~ 4=S5，5=幽匿五阶蛋） |
| `DATA_IS_WARDEN_VARIANT` | Boolean | 向后兼容旧存档（level≥5 推导为 WARDEN） |
| `DATA_DOLL_VARIANT` | Integer | 变体（DollVariant.ordinal()） |

**transient 战斗状态（不存 NBT，召回时 `resetModeWorkState()` 清空）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `meleeTarget` | `LivingEntity` | 近战锁定目标 |
| `rangedTarget` | `LivingEntity` | 射手锁定目标 |
| `forcedTargetUuid` | `UUID` | 指挥棒指定的强制攻击目标（null=正常 AI 搜寻） |

**核心方法**

| 方法 | 可见性 | 说明 |
|---|---|---|
| `tick()` | public | 总调度入口，按模式分发到各 update/apply 方法 |
| `switchMode(int modeSlot08)` | public | 模式切换，含武器前置检查（幽匿人偶跳过武器检查） |
| `resetModeWorkState()` | private | 清空所有模式工作状态（含 forcedTargetUuid） |
| `updateMeleeMind()` / `updateRangedMind()` | private | 目标搜寻+攻击决策，开头有 `resolveForcedTarget()` 优先分支 |
| `applyMeleeInput()` / `applyRangedInput()` | private | 移动/射击执行，追击距离上限仅在无强制目标时检查 |
| `handleWardenSonicBoom()` / `fireSonicBoom()` | private | 幽匿人偶音波攻击 |
| `handleNetherFireball()` / `fireWitherSkull()` | private | 下界人偶烈焰弹（发射 WitherSkull，13 伤 + 燃烧 5 秒） |
| `handleEnderBreath()` / `fireEnderSkull()` | private | 末影人偶龙息喷吐（发射 WitherSkull，2 伤 + 命中点生成龙息云） |
| `applyFearAura()` / `restoreFearedMobs()` | private | 苍白人偶恐惧光环（setNoAi + UUID 追踪） |
| `applyNetherPacifyAura()` | private | 下界人偶安抚光环——低频清理遗留仇恨（核心拦截由 MobMixin 完成） |
| `handleSeaLaser()` / `fireSeaLaser()` | private | 海洋人偶守卫者激光（hitscan，9 魔法伤绕甲，蓄力 1.5s + 冷却 1s，需视线、射程 16、可被方块遮挡） |
| `applySeaPacifyAura()` | private | 海洋人偶安抚光环——低频清理海洋生物遗留仇恨（核心拦截由 MobMixin 完成） |
| `applySeaPlayerAura()` | private | 海洋人偶主人增益光环——16 格内给主人水下呼吸 + 仅水下给急迫 XXV（抵消水中挖掘减速） |
| `applySeaSwim()` / `applySeaSwimIfNeeded()` | private | 海洋人偶水中竖直跟随（下潜/上浮，仿溺尸自由行动） |
| `canBeAffected(MobEffectInstance)` | public override | 下界人偶凋零免疫 |
| `getBlockSpeedFactor()` | public override | 下界人偶灵魂沙免疫 |
| `getWaterSlowDown()` | public override | 海洋人偶水中全速（返回 1.0，抵消默认 0.8 减速） |
| `canBreatheUnderwater()` | public override | 海洋人偶不溺水（自然下沉，不浮起） |
| `hurtServer(ServerLevel, DamageSource, float)` | public override | 末影人偶 80% 闪避：攻击类伤害 80% 概率无伤 + 末影瞬移粒子/音效 + 附近随机落点（找不到则原地免伤）；环境伤害正常结算 |
| `getAuraCenter()` | public | 光环中心：跟随时为玩家位置，不跟随时为人偶自身（苍白恐惧光环和下界安抚光环共用） |
| `setForcedTargetUuid(UUID)` / `hasForcedTarget()` | public | 指挥棒目标选择接口 |
| `setDollLevel(int)` / `setDollVariant(DollVariant)` | public | 等级/变体设置，内部调整血量上限和常驻药水 |
| `isWardenDoll()` / `isNetherDoll()` / `isEnderDoll()` / `isSeaDoll()` | public | 变体类型查询（苍白人偶用 `getDollVariant() == PALE` 判断） |
| `getActiveMode()` / `isFollowEnabled()` | public | 当前模式/跟随状态查询 |
| `getDollUuid()` | public | 获取人偶 UUID（使用引擎 UUID，非自定义字段） |

**`tick()` 调度流程**

`tick()` 是每帧总调度入口（766 行起），理解这个流程就理解了人偶的全部运行逻辑：

```
tick()
│
├─ 1. 移动输入（super.tick() 之前写入，aiStep/travel 会消费）
│   ├─ 跟随优先 → applyFollowInput()
│   ├─ 火把模式可叠加跟随 → applyTorchInput()
│   └─ 非跟随时按模式分发：
│       MELEE→applyMeleeInput  RANGED→applyRangedInput
│       FARM→applyFarmInput    FEED→applyFeedInput
│       CHOP→applyChopInput    MINE→applyMineInput
│       FISH→applyFishInput
│
├─ 2. super.tick()  ← 原版物理/AI step/travel 消费移动输入
│
├─ 3. 生动化修正（super.tick() 之后）
│   ├─ updateSwingTime()  ← 手动推进挥臂动画（原版只给 Player/Monster 推进）
│   ├─ 撞墙自动跳跃 + 清空导航（强制下 tick 重寻路）
│   ├─ idle 头部微动（静止时每 80 tick 随机偏转 ±22.5°）
│   └─ 禁用自然回血 + 食物回血（pendingFoodHeal）
│
├─ 4. 永久药效施加（按等级/变体阶梯式）
│   ├─ PALE/NETHER/ENDER → 恢复V + 抗性III + 抗火
│   ├─ WARDEN(level≥5)  → 恢复V + 抗性IV + 抗火
│   └─ level 1-4 → 恢复I~IV + 抗性I~II
│
├─ 5. 自动进食：tryAutoEat()  ← 血量不满时从背包找食物
│
├─ 6. 决策逻辑（跟随和模式可并存）
│   ├─ 跟随 → updateFollowMind()  ← 只负责跨维度/远距传送
│   └─ 模式分发：
│       MELEE→updateMeleeMind  RANGED→updateRangedMind
│       FARM→updateFarmMind    FEED→updateFeedMind
│       CHOP→updateChopMind    MINE→updateMineMind
│       TORCH→updateTorchMind  FISH→updateFishMind
│
├─ 7. 后处理
│   ├─ trackArrows()  ← 已发射箭矢飞行中追踪修正
│   └─ collectNearbyDrops()  ← 拾取附近掉落物
│
├─ 8. 变体被动技能（仅 MELEE/RANGED 模式）
│   ├─ WARDEN → handleWardenSonicBoom()
│   ├─ NETHER → handleNetherFireball()
│   ├─ ENDER  → handleEnderBreath() + handleEnderExecute()
│   └─ SEA    → handleSeaLaser()
│
├─ 9. 变体光环（每 20 tick 执行一次）
│   ├─ PALE   → applyFearAura()  ← setNoAi 冻结敌对生物
│   ├─ NETHER → applyNetherPacifyAura()  ← 清除下界生物仇恨
│   └─ SEA    → applySeaPacifyAura() + applySeaPlayerAura()  ← 清除海洋生物仇恨 + 主人增益
│
└─ 10. 召回登记：DollRecallRegistry.record()
    ← 记录当前位置/维度，供蛋召回时定位未加载区块中的人偶
```

> **设计要点**：步骤 1 和步骤 6 都按模式分发——步骤 1 负责"移动"（applyXxxInput），步骤 6 负责"决策"（updateXxxMind）。跟随与模式可并存：跟随只控制传送，模式控制工作/战斗。

### 已有 Mixin 职责

**服务端（`doll-mod.mixins.json`，包 `com.example.doll.mixin`）**

| Mixin 类 | 目标 | 职责 |
|---|---|---|
| `AnvilBlockMixin` | `AnvilBlock.damage()` | 石砧损伤链：检测 `RockAnvilBlock` 时返回下一级变体 |
| `AreaEffectCloudAccessor` | — | `@Accessor` 暴露 `reapplicationDelay` 字段，龙息云判定间隔设为 10 tick |
| `AreaEffectCloudMixin` | `AreaEffectCloud.serverTick` | 龙息云友军保护：只伤害 `Enemy` 实体，非敌对生物自动被保护 |
| `EnchantmentHelperMixin` | `getEnchantmentLevel` / `processMobExperience` | 幽匿人偶掠夺者天赋：自带抢夺 III + 经验×3 |
| `LivingEntityFearAuraMixin` | `LivingEntity.hurtServer` | 苍白人偶恐惧光环：光环内敌对生物受到伤害×1.67 |
| `MobMixin` | `Mob.canAttack` | 安抚光环索敌层拦截：下界生物（NETHER）与海洋生物（SEA）对受保护的人偶/主人返回 false，从源头阻止仇恨建立 |
| `WitherSkullMixin` | `WitherSkull.onHit` / `onHitEntity` | 人偶头颅弹：不炸方块 + 按 owner 变体区分命中效果（NETHER→伤害 13+燃烧 5 秒，ENDER→直接命中 2 伤+生成龙息云判定间隔 10 tick）+ 友军保护（非 Enemy 实体不受伤害和效果） |

**客户端（`doll-mod.client.mixins.json`，包 `com.example.client.mixin`）**

| Mixin 类 | 目标 | 职责 |
|---|---|---|
| `DollEntityClientMixin` | `DollEntity` | 实现 `ClientAvatarEntity`，按变体切换皮肤 |
| `AvatarRendererDollMixin` | `AvatarRenderer.extractRenderState` | 首次渲染诊断日志 |
| `SkullBlockRendererMixin` | `SkullBlockRenderer` | 注册自定义头颅皮肤纹理和模型 |
| `WitherSkullRenderStateMixin` | `WitherSkullRenderState` | Duck Typing 注入 `DollVariant` 标记，区分人偶/原版凋灵发射的头颅 |
| `WitherSkullRendererMixin` | `WitherSkullRenderer` | 人偶发射的头颅替换模型层为 PLAYER_HEAD + 按变体选择贴图（NETHER→nether_doll.png，ENDER→ender_doll.png） |
| `ClientPacketListenerMixin` | `handleEntityEvent` | 苍白献祭时替换不死图腾 HUD 图标 |
| `MinecraftUseItemOnDollMixin` | `Minecraft` | 阻断右键人偶时弓/食物误触发 |
| `MinecraftStopUseItemMixin` | `Minecraft.setScreenAndShow` | 打开屏幕时停止使用物品 |

### 资源三处源集与诊断陷阱（给后续开发者）

Fabric 项目的资源 / 配置分散在**三个独立源集**，诊断"某文件是否存在、某配置是否注册"前必须三处全扫，否则极易得出错误结论：

| 源集 | 位置 | 典型内容 |
|---|---|---|
| 主端资源 | `src/main/resources` | 方块 / 物品模型、配方、战利品表、主端 mixin 配置 `doll-mod.mixins.json` |
| 客户端资源 | `src/client/resources` | client-only 资源与配置，如 `doll-mod.client.mixins.json` |
| datagen 产物 | `src/main/generated` | 自动生成物，如 `assets/doll-mod/lang/en_us.json`、`zh_cn.json` |

> **真实翻车案例（2026-08-22 发布前自检）**：曾有人仅凭 `src/main/resources` 下找不到 `doll-mod.client.mixins.json` 和 lang 文件，就误判「client mixin 未登记」「语言文件缺失」为发布 blocker。实际二者分别位于 `client/resources` 与 `generated/`，均正常生效。该误判被「游戏内表现完全正常」推翻——若 client mixin 真未加载，`DollEntityClientMixin` 不生效会导致人偶渲染 `ClassCastException` 崩溃；若 lang 真缺失，切到 `zh_tw` 等语言只会回退 `en_us` 显示英文，而非裸奔成 key 字符串。
>
> **另一同类翻车（2026-08-22 森林头颅紫黑方块）**：森林人偶头颅第一轮修复时，把 `models/block/forest_doll_head.json` 的 `particle` 改指 entity 皮肤 `doll-mod:entity/doll/forest_doll`，并删除了 `textures/block/forest_doll_head.png`。实机破坏头颅显示**紫黑方块**。根因是误以为「entity 纹理也能当 block 粒子」，忽视了 **block 与 entity 是两套独立 atlas**——方块破坏覆盖层只在 block atlas 采样，指向 entity 纹理必然 missing texture。正确做法是保留 `textures/block/<variant>_doll_head.png` 并让 particle 指向它（见上文「关键实现要点」末条）。

**两点铁律：**

1. **语言回退 ≠ 裸奔**：Minecraft 选无对应文件的语言（如 `zh_tw`）时回退 `en_us`；裸 key（如 `item.doll-mod.xxx`）仅当连 `en_us` 也无该 key 时才出现。一见「没翻译」就断言「会裸奔」是错的。
2. **不轻信过时文档 / 记忆里的「缺 X」**：以当前代码与资源事实为准。本仓库早期文档曾记「缺 en_us.json」，但 datagen 早已生成它。

**当前发布就绪度结论（2026-08-22 更新）**

- **功能性 / 本地化**：7 变体（含 FOREST）实现完整，lang/配方/datagen 齐全，**无功能性或本地化 blocker**。
- **NPE 崩溃（已解决，非既有 bug）**：2026-08-22 调试首帧卡顿时，往 `DollEntityClientMixin.getSkin()` 内植入诊断代码（调 `TextureManager.getTexture`）导致用蛋生成人偶瞬间偶发 NPE 崩溃——`avatarState()` 返回 null。**根因=诊断代码提前唤醒渲染管线**，在 `DollEntity` 实例 `<init>` 期间、Mixin 的 `avatarState` 字段尚未赋值完时触发了渲染管线同一调用栈读取该字段。**诊断代码已全部回滚，纯净版用户实测不崩**。这不是模组既有 bug，是诊断代码副作用，无需修复。详见文末「历史事故记录」节。
- **首帧渲染卡顿（经确认非 bug，无需修复）**：日志曾报 `[DollClient] 客户端 tick 间隔 1801ms`，发生在游戏开启前的加载阶段（首次渲染人偶时皮肤纹理/模型/着色器惰性加载 + JVM 类加载/JIT 热身）。经用户实测确认：**卡顿仅在加载阶段出现，进入游戏后（操作/联机/人偶干活）完全流畅、无感**。属加载期一次性正常开销，玩家感知不到，非 bug。外部 AI 曾建议"找 render 方法外提资源/删 if(model==null)/ClientEntrypoint 静态缓存"——**不适用本项目**（人偶复用原版 `AvatarRenderer`，无自定义 render 方法；纹理 Identifier 全是 `static final` 常量、皮肤 `PlayerSkin` 是字段级 `cachedXxxSkin`，无懒加载可删）。预热优化（`onInitializeClient` 提前 `getTexture`）属锦上添花、非必需，暂不实施。
- **非阻塞清理项**：`fabric.mod.json` 占位符（contact 的 `yourname`、modrinth / curseforge 的 `todo`，发版前替换真实值）。

> ⚠️ 历史误判记录：早前曾把"森林孤儿纹理（待实现或移除）"列为清理项，实际 `forest_doll.png` 等美术已随 FOREST 变体落地使用，非孤儿；该条已撤销。

### 26.2 API 踩坑备忘

以下是在 26.2 + mojmap 环境中踩过的坑，开发时务必注意：

**伤害与生命**

- `LivingEntity.hurtServer(ServerLevel, DamageSource, float)`——26.2 签名变更，旧 `hurt(DamageSource, float)` 已不可用。
- `Entity.kill(ServerLevel)`——26.2 正确签名，内部调 `hurtServer` 伤害值 `Float.MAX_VALUE`。旧 `kill()` 无参签名已不可用。
- `broadcastEntityState`（非 `broadcastEntityState`，也不是旧的 `broadcastEntityEvent`）——实体状态事件用这个。
- `AttributeModifier` 用 `addOrUpdateTransientModifier()` 添加临时修饰器（如苍白献祭的 +100 血量），会在指定 tick 后自动移除。
- `setLastHurtByPlayer(UUID, int)`——public 方法，人偶击杀生物后需手动调用使掉落/经验归属玩家。原版 `resolvePlayerResponsibleForDamage` 只认 `Player` 和驯服的 `Wolf`，不认识 `DollEntity`。
- `LivingEntity.canBeAffected(MobEffectInstance)`——覆写此方法返回 `false` 可实现药水免疫（如 NETHER 变体免疫凋零）。
- `Entity.getBlockSpeedFactor()`——覆写返回 `1.0f` 可实现方块减速免疫（如 NETHER 变体免疫灵魂沙/浆果丛减速）。
- `Entity.igniteForSeconds(float)`——26.2 点燃实体的方法名（旧版 `setRemainingFireTicks` 不可用）。
- `LivingEntity.completeUsingItem()`——`Avatar`/`LivingEntity` 无 `FoodData`，原版 `FoodProperties.onConsume()` 对非 Player 实体跳过 nutrition。需在 `super.completeUsingItem()` **之前**读取 `food.nutrition()` 存入字段，`tick()` 中在禁用自然回血之后手动 `heal()`。

**物品与交互**

- 创造模式下 `Item.interactLivingEntity` / `ItemStack` 交互入口，引擎传入 `ItemStack.copy()` 副本，直接改入参 NBT 不持久化——必须用 `player.getItemInHand(hand)` 取真实实例再读写。
- `setScreenAndShow`（非旧版 `setScreen`）——26.2 打开屏幕的统一入口。

**实体与同步**

- `SynchedEntityData.set()` 在 `readAdditionalSaveData` 中设置的值可能不标记脏——在 `startSeenByPlayer` 中先设临时不同值再设回正确值可强制标记脏，修复重进后客户端状态丢失。
- `ServerLevel.getEntityInAnyDimension(UUID)`——跨维度 O(1) 实体查找的正确方法。`MinecraftServer` 无 `getEntity(UUID)` 方法。
- `Entity.getUUID()`——使用引擎 UUID，不要自定义 UUID 字段（曾有 `dollUuid` 字段已移除）。
- `Mob.setTarget(null)`——清除生物当前目标引用，但**不清除底层仇恨机制**（NeutralMob 持久愤怒计时器、Brain `ANGRY_AT` 记忆），下一 tick 会被重新索敌覆盖。需要配合 `stopBeingAngry()`（NeutralMob）和 `eraseMemory(MemoryModuleType.ANGRY_AT)`（Brain）才能彻底清除。
- `Mob.canAttack(LivingEntity)`——索敌层入口，所有 TargetGoal、Brain `StartAttacking`、`NeutralMob.isAngryAt` 最终都调此方法。Mixin 注入 HEAD 返回 false 可从源头阻止目标建立，等价创造模式机制（`Player.canBeSeenAsEnemy()` 返回 false → `canAttack` 返回 false）。适用于需要"完全不被索敌"而非"清除已有仇恨"的场景。
- `SmallFireball(Level, LivingEntity, Vec3)`——构造函数第三个参数是方向向量（非速度），内部 `assignDirectionalMovement` 会 `normalize()` 后乘 `accelerationPower`（默认 0.1）作为初始速度。发射后需 `setPos` 调整到眼睛高度，再 `addFreshEntity` 生成。`onHitEntity` 硬编码 5 点伤害 + 点燃 5 秒（不可配置），需 `@ModifyArg` 拦截 `hurtServer` 的 float 参数才能自定义伤害。`onHitBlock` 受 `mobGriefing` 控制是否点火——若需禁用方块点燃，Mixin `onHitBlock` HEAD 并检查 `getOwner() instanceof DollEntity` 后 `cancel()` 即可，火球仍会碰撞消失（由父类 `onHit` 处理 `discard`）。
- `WitherSkull(Level, LivingEntity, Vec3)`——构造函数第三参数也是方向向量。`onHitEntity` 硬编码伤害 8.0f（owner 为 LivingEntity 时）+ 凋零 I（普通 10 秒/困难 40 秒）。`onHit` 调 `level.explode()` 破坏方块。自定义需三个 Mixin 注入点：`@Redirect` 拦 `explode`（禁用方块破坏）、`@Redirect` 拦 `hurtServer`（自定义伤害 + 友军保护）、`@Redirect` 拦 `LivingEntity.addEffect`（凋零替换为其他效果 + 友军保护）。渲染器 `WitherSkullRenderer` 硬编码 `wither.png` 贴图 + `WITHER_SKULL` 模型层，`setItem` 无效——需 Duck Typing 扩展 RenderState + Mixin 替换贴图和模型。下界人偶和末影人偶共用 WitherSkull 投射物，通过 owner 的 `DollVariant` 区分命中效果：NETHER→伤害 13 + 燃烧 5 秒，ENDER→直接命中 2 伤 + 在命中点生成龙息云（AreaEffectCloud，reapplicationDelay 10 tick）。END 变体在 `@Redirect` 拦截 `explode` 时手动创建龙息云（复刻原版 `DragonFireball.onHit` 逻辑：半径 3、持续 600 tick、即时伤害 II、龙息粒子），并通过 `AreaEffectCloudAccessor` 将 `reapplicationDelay` 从默认 20 缩短为 10（判定频率翻倍）。友军保护：`hurtServer` 和 `addEffect` 的 `@Redirect` 以及 `AreaEffectCloudMixin` 均检查目标是否实现 `Enemy` 接口——非敌对生物（动物、村民、铁傀儡、玩家、其他人偶等）自动被保护，不需要维护友善生物名单。
- `DragonFireball(Level, LivingEntity, Vec3)`——构造函数第三参数是方向向量。`onHit` 不硬编码伤害也不破坏方块——命中后自动生成 `AreaEffectCloud`（半径 3、持续 600 tick、即时伤害 II 药水效果）。渲染器 `DragonFireballRenderer` 用 `submitCustomGeometry` 画 2D billboard，贴图硬编码在 `private static final RENDER_TYPE` 中。本项目不再使用 DragonFireball——末影人偶远程攻击已统一为 WitherSkull，龙息云在 `WitherSkullMixin.dollNoExplode` 中手动生成。
- `BuiltInRegistries.ENTITY_TYPE.getKey(EntityType)`——获取实体类型的注册 ID（`Identifier`），用于按实体类型名做白名单过滤（如光环只影响特定生物）。

**NBT 持久化**

- 26.2 使用 `ValueInput` / `ValueOutput`（非旧版 `CompoundTag` 读写），`addAdditionalSaveData` / `readAdditionalSaveData` 签名已变更。

**数据生成**

- datagen 翻译用字符串键 `b.add("item." + MOD_ID + "." + ID, ...)` 比用 `Item` 对象方式 `b.add(Item, String)` 更可靠——后者对某些 Item 不生效。
- Minecraft 纹理只支持 PNG 格式，JPG 会导致渲染异常（曾有 `doll_control_panel` 为 JPG 导致问题）。

**附魔**

- `Enchantments.LOOTING` 是 `ResourceKey<Enchantment>`（非旧版 `Registry` 引用）。
- `Holder.is(ResourceKey)` 判断附魔身份。
- `processMobExperience` 内部走 `getItemEnchantmentLevel` 逐件检查，不经 `getEnchantmentLevel`——两个 Mixin 注入点互不干扰。

**渲染**

- 26.2 HUD 心形改用独立 sprite：`GuiGraphicsExtractor.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier, x, y, w, h)`。旧版 `blit` + UV 方式会渲染出黑色条纹。
- `AvatarRenderer` 构造第二个参数 `true`=SLIM（细臂），`false`=WIDE（粗臂）。皮肤 UV 必须与模型匹配。
- `DollEntity` 覆写 `isModelPartShown()` 返回 `true` 可修复多层皮肤不渲染问题（原版通过 `DATA_PLAYER_MODE_CUSTOMISATION` 字节控制）。
- `NOTE_BLOCK_*` 常量在 26.2 中为 `Holder$Reference<SoundEvent>`，须 `.value()` 解引用。

### 26.2 编码规范

> 以下规范基于 MC 26.2 + mojmap + Fabric Loom 1.17 + JDK 25+ 环境。开发新方块/物品/实体时必须遵循。

**方块注册**

- 每个自定义方块类**必须**声明 `MapCodec`：`public static final MapCodec<XxxBlock> CODEC = simpleCodec(XxxBlock::new);` 并覆写 `codec()` 返回该 CODEC——26.2 序列化硬性要求，缺失会报错。
- 注册方块和物品时**必须**调用 `Properties.setId(ResourceKey.create(Registries.BLOCK/ITEM, key))`，否则注册报错。
- 透明形状方块（头颅、栅栏等）注册时加 `.noOcclusion()`。
- 注册顺序：**Identifier → Block → Item → BlockEntityType**。方块和物品共用一个 `Identifier`，BlockEntity 用独立的。

**BlockEntityRenderer（BER）**

- 26.2 BER 使用双泛型签名 `BlockEntityRenderer<BlockEntityType, RenderStateType>`，第二个泛型是 RenderState。
- `extractRenderState()` 替代旧版 `render()`，从 BlockEntity 提取渲染状态到 state 对象。
- `submit()` 负责实际渲染提交。
- 在客户端初始化类中注册：`BlockEntityRenderers.register(BLOCK_ENTITY_TYPE, RendererClass::new)`。

**物品模型系统**

- 26.2 物品渲染入口是 `assets/<modid>/items/<item_id>.json`，取代旧的 `models/item/`。`models/item/` 下的旧文件不再参与物品栏/手持渲染。
- 特殊模型（头颅、盾牌等）用 `"type": "minecraft:special"` + `"base": "minecraft:item/template_skull"`。

**纹理路径格式**

| 使用位置 | 格式 | 示例 |
|---|---|---|
| Java 代码 `Identifier` | 完整路径，含 `textures/` 和 `.png` | `Identifier.fromNamespaceAndPath("doll-mod", "textures/entity/doll/warden_doll.png")` |
| `items/` JSON `texture` 字段 | 命名空间简写，不带 `textures/` 和 `.png` | `"doll-mod:doll/warden_doll"` |

**数据包目录**

- 26.2 使用单数形式：`loot_table/`、`recipe/`（非 `loot_tables/`、`recipes/`）。

**变体枚举模式**

- 新增人偶变体**不建子类**，通过 `DollVariant` 枚举在 `DollEntity` 内部做分支——最轻量方案，避免子类膨胀。
- 枚举值（**已全部实现**）：`NONE`（普通 1-5 阶）/ `WARDEN` / `PALE` / `NETHER` / `ENDER` / `SEA` / `FOREST`。
- 变体属性（血量上限、常驻药水、特殊免疫）必须在**三处**保持一致：`setDollLevel()` / `setDollVariant()` / `readAdditionalSaveData()`。遗漏任一处会导致存档重载后属性丢失。
- `setDollLevel()` 中特殊变体（PALE/NETHER 等）必须排除出 level→WARDEN 自动推导逻辑（`getDollVariant() != PALE && != NETHER`）。
- 蛋物品 `DollSpawnEggItem` 构造函数接收 `DollVariant` 参数，NBT 中存储变体信息。

**物品命名格式**

- 变体蛋统一为 `{variant}_doll_egg`（短格式），不再使用 `{variant}_doll_spawn_egg`。
- 普通等级蛋为 `doll_egg_s1`~`doll_egg_s5`，属于不同维度，不纳入变体命名格式。
- `assets/<modid>/items/<item_id>.json` 文件名必须与物品注册 ID 完全一致，`model` 字段指向 `doll-mod:item/<item_id>`。
- 纹理文件名同样与注册 ID 一致：`textures/item/{item_id}.png`。

**光环框架模式**

- 各变体光环在 `tick()` 中按变体分发，直接调用 `applyXxxAura()` 方法（无统一调度入口）。
- 每 20 tick 执行一次（内部 cooldown 计数器），用 `serverLevel.getEntities(EntityTypeTest.forClass(Mob.class), box, predicate)` 收集范围内生物。
- 光环中心：跟随时为玩家位置，不跟随时为人偶自身位置（复用 `getAuraCenter()`）。
- 两种光环模式：
  - **冻结型**（苍白恐惧光环）：`setNoAi(true)` 完全停止 AI，需要 `Set<UUID>` 追踪受影响生物并在人偶移除时恢复。
  - **安抚型**（下界/海洋安抚光环）：`setTarget(null)` 清除仇恨，瞬时操作无需追踪恢复，生物离开光环后可重新索敌。
  - **索敌拦截型**（下界/海洋安抚光环强化）：Mixin 注入 `Mob.canAttack` HEAD 返回 false，从源头阻止新目标建立（等价创造模式 `canBeSeenAsEnemy` → false）。适用于需要"仇恨无法重建"的场景。与 `setTarget(null)` 组合使用：Mixins 阻止新索敌，低频清理已有遗留仇恨。两层白名单：攻击者按 `EntityType` ID 过滤（下界 `isNetherMobType` / 海洋 `isSeaMobType`），目标按变体/主人 UUID 过滤（下界 `isNetherDollProtected` / 海洋 `isSeaDollProtected`）。
  - **玩家增益型**（海洋主人增益光环）：仅对范围内主人施放——水下呼吸常驻 + 仅水下给急迫 XXV（抵消水中挖掘约 ÷5 减速），离开范围后效果自然过期，无需追踪恢复。

**灵龛交互**

- `SculkShrineBlock.useItemOn()` 中按手持物品类型分发：回响碎片 → 召唤野生幽匿人偶。
- 非回响碎片直接 `PASS`，不拦截其他交互。

**Mixin 规范**

- Mixin 类必须在对应 mixin JSON 配置文件的数组中注册。
- `compatibilityLevel` 与项目 JDK 版本匹配（JDK 25 → `JAVA_25`）。
- 当前配置：
  - `doll-mod.mixins.json`（`com.example.doll.mixin`，通用）：`AnvilBlockMixin`、`AreaEffectCloudAccessor`、`AreaEffectCloudMixin`、`EnchantmentHelperMixin`、`LivingEntityFearAuraMixin`、`MobMixin`、`WitherSkullMixin`
  - `doll-mod.client.mixins.json`（`com.example.client.mixin`，客户端）：`AvatarRendererDollMixin`、`ClientPacketListenerMixin`、`DollEntityClientMixin`、`MinecraftStopUseItemMixin`、`MinecraftUseItemOnDollMixin`、`SkullBlockRendererMixin`、`WitherSkullRenderStateMixin`、`WitherSkullRendererMixin`

### 自定义头颅方块

新增头颅需按 8 层架构实现，三条渲染路径缺一不可。参考现有 `WardenDollHeadBlock` 体系。

**8 层架构：**

| 层 | 组件 | source set | 职责 |
|---|---|---|---|
| 1 | SkullType 枚举 | main | 实现 `SkullBlock.Type` 接口，定义头颅类型 |
| 2 | HeadBlock 方块类 | main | 继承 `SkullBlock`，绑定类型和 BlockEntity |
| 3 | HeadBlockEntity | main | 继承 `BlockEntity`（**不继承** `SkullBlockEntity`），承载动画接口 |
| 4 | HeadItem 物品类 | main | 继承 `BlockItem`，`equippable(EquipmentSlot.HEAD)` |
| 5 | 主类注册 | main | 注册方块/物品/BlockEntityType |
| 6 | SkullBlockRenderer Mixin | client | 注入原版渲染器，注册皮肤和模型 |
| 7 | BlockEntityRenderer | client | 方块在世界中的 3D 渲染 |
| 8 | items/ 物品模型 JSON | resources | 26.2 物品模型，控制物品栏/手持显示 |

**三条渲染路径：**

| 路径 | 触发场景 | 实现层 |
|---|---|---|
| A | 方块放置在世界中 | 层 7（自定义 BER） |
| B | 玩家戴在头上 / 原版头颅渲染管线 | 层 6（Mixin 注入 `SkullBlockRenderer`） |
| C | 物品栏 / 手持 / 掉落物显示 | 层 8（`items/` JSON） |

漏配后果：A 缺 → 方块不可见（只有破坏粒子）；B 缺 → 佩戴显示默认头颅或紫黑方块；C 缺 → 物品栏显示紫黑方块。

**关键实现要点：**

- BlockEntity 继承 `BlockEntity`，**不继承** `SkullBlockEntity`——原版 `SkullBlockEntity` 绑定了原版头颅类型检查逻辑，自定义类型会出问题。
- SkullType 枚举实现 `SkullBlock.Type` 接口，序列化名**必须全小写**。
- HeadBlock **必须**声明 `CODEC = simpleCodec()` + 覆写 `codec()` + `newBlockEntity()`。
- HeadItem 继承 `BlockItem`，构造器调用 `properties.equippable(EquipmentSlot.HEAD)` 使其可佩戴。
- SkullBlockRenderer Mixin 两个注入点：
  - `lambda$static$0`（`TAIL`）：追加 `SkullBlock.Type → Identifier` 皮肤纹理映射
  - `createModel`（`HEAD`，`cancellable = true`）：遇到自定义类型时返回自定义模型
- BER 朝向处理：`SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(rotation)` 处理地面 16 方向，`wallTransformation(facing)` 处理墙挂。
- BER 渲染类型：`RenderTypes.entityCutoutZOffset(texture)`（支持透明像素 + Z 偏移避免 Z-fighting）。
- BER `submit()` 中委托 `SkullBlockRenderer.submitSkull()` 完成实际绘制，避免重复造轮子。
- 方块模型 JSON（`models/block/`）仅定义 `particle` 纹理，无 `elements`、无 `parent`——3D 外观完全由 BER 绘制。
- 方块模型的 `particle` 纹理**必须指向 `textures/block/<variant>_doll_head.png` 这类 block atlas 真实纹理**，用于破坏进度覆盖层（crack）与破坏粒子。**绝不能指向 entity 皮肤**（如 `doll-mod:entity/doll/xxx`）——entity 与 block 是两套独立的纹理图集（atlas），方块破坏覆盖层只在 block atlas 采样，指向 entity 纹理会因找不到图集条目而回退成**紫黑方块（missing texture）**。`textures/block/<variant>_doll_head.png` 可用 PIL 从 64×64 实体皮肤裁剪头部正面 (8,8)-(15,15) + 头盔层 (40,8)-(47,15) 合成再缩放生成（Warden 16×24 / SEA 16×16），**不可删除该文件**。

## 实用编码技巧（减少编译报错）

> 以下技巧基于本项目实际开发中踩过的坑总结，目标是减少编译错误和 Mixin 注入失败。

### 1. 反编译确认再写 Mixin

写 Mixin 注入注解（`@ModifyArg`、`@Redirect`、`@Inject` 的 `target`）之前，**必须**用 `javap -p -c` 反编译目标方法字节码，确认：

- 方法调用的**实际接收类**（可能是接口而非实现类，或子接口而非父接口）
- 方法签名的**完整参数列表**（泛型擦除后的原始类型）
- 调用是 `INVOKEVIRTUAL`（实例方法）还是 `INVOKESTATIC`（静态方法）还是 `INVOKESPECIAL`（构造器/super）

```bash
# 反编译某个类的方法字节码
$JAVA_HOME/bin/javap -p -c -cp "<loom-cache>/minecraft-common-deobf-26.2.jar" net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull
```

**反面教材**：`@ModifyArg` 的 `target` 写了 `OrderedSubmitNodeCollector;submitModel(...)`，但字节码中实际调用在 `SubmitNodeCollector` 接口上——扫描到 0 个目标，Mixin 启动直接崩溃。

### 2. Mixin 注入失败的标准排查流程

Mixin 崩溃报 `Critical injection failure: ... (0/1) succeeded. Scanned 0 target(s)` 时：

1. **检查 `target` 中的类名**——是否和字节码中 `invokeinterface`/`invokevirtual` 的实际类一致
2. **检查方法签名**——参数类型、数量、顺序是否和字节码 `#常量池引用` 一致
3. **检查 `@At` 的 `value`**——`INVOKE` 用于方法调用，`FIELD` 用于字段访问，`NEW` 用于构造
4. **检查 `refMap`**——报错信息含 `No refMap loaded` 说明 Loom 的 refMap 配置有问题，但大多数情况下真正原因是 target 本身写错

### 3. 常量跨包引用用 `public static final`

Mixin 类在不同包中（如 `com.example.doll.mixin` 需引用 `com.example.doll.entity.DollEntity` 的常量），常量必须声明为 `public static final`：

```java
// DollEntity.java — Mixin 需跨包引用
public static final float FIREBALL_DAMAGE = 13.0f;  // ✅ Mixin 可直接 DollEntity.FIREBALL_DAMAGE
private static final float FIREBALL_DAMAGE = 13.0f; // ❌ Mixin 无法访问
```

### 4. Duck Typing 扩展 RenderState

Minecraft 的 RenderState 是原版类，无法直接添加字段。当渲染器需要额外信息（如"这个投射物是谁发射的"）时，用 Duck Typing 模式：

1. 定义接口（如 `DollSkullState`）放在客户端能访问的包
2. Mixin 目标 RenderState 类 `implements DollSkullState`，注入 `@Unique` 字段
3. 渲染器 Mixin 在 `extractRenderState` 中写入标记，在 `submit`/`getTextureLocation` 中读取

```java
// 接口（存储 DollVariant 而非 boolean，支持多变体区分）
public interface DollSkullState {
    DollVariant dollMod$getVariant();
    void dollMod$setVariant(DollVariant variant);
}
// Mixin 目标类
@Mixin(WitherSkullRenderState.class)
public class WitherSkullRenderStateMixin implements DollSkullState {
    @Unique private DollVariant dollMod$variant = DollVariant.NONE;
    // 实现接口方法...
}
```

方法名加 `dollMod$` 前缀避免与其他 Mixin 冲突。

### 5. 渲染器单例的状态传递

渲染器是单例，每帧渲染多个实体。需要在 `submit` 和 `getTextureLocation` 之间传递状态时：

- `@Inject submit HEAD`：从 state 读取标记存入 `@Unique` 实例字段
- `@ModifyArg` / `@Inject getTextureLocation`：读取实例字段做条件判断

渲染是单线程的，实例字段不会被并发修改。

### 6. 先验证再执行——编译前自检清单

写完代码、按编译前过一遍：

| 检查项 | 说明 |
|---|---|
| **import 清理** | 重构后删除不再使用的 import（如 SmallFireball → WitherSkull 后删 SmallFireball import） |
| **mixin JSON 同步** | 新增/删除/重命名 Mixin 类后必须同步更新对应 mixin JSON 配置文件 |
| **方法签名** | 不确定的方法签名先用 `javap` 确认，不要从教程或记忆推断 |
| **可见性** | Mixin 跨包引用的字段/方法必须是 `public`；`@Unique` 字段不需要 public |
| **target 描述符** | Mixin `@At(target=)` 中的类名和方法签名必须与字节码完全一致 |

### 7. 编译命令速查

```bash
./gradlew compileJava          # 只编译服务端（快速检查）
./gradlew compileClientJava    # 编译客户端（含 client mixin）
./gradlew build                # 全量编译 + 打包
```

改了 `src/client/` 下的代码（client mixin、渲染器、GUI）后必须跑 `compileClientJava`，`compileJava` 不会编译 client source set。

### 8. 自定义原版投射物外观（头颅飞行物模式）

当需要让原版投射物（WitherSkull、Fireball 等）显示自定义外观时，`setItem()` **对某些投射物无效**——因为渲染器可能硬编码贴图和模型，完全不读 item 数据。

**判断方法**：反编译渲染器，看 `getTextureLocation` 是否直接返回常量 `Identifier`，以及构造器是否用固定 `ModelLayers` 创建模型。如果是，就必须 Mixin 替换。

**完整方案（4 个文件）**：

| 文件 | 作用 |
|---|---|
| `DollSkullState` 接口 | Duck Typing，让 RenderState 携带 `DollVariant` 标记 |
| `WitherSkullRenderStateMixin` | 给 RenderState 实现 `DollSkullState`，注入 `@Unique` 字段 |
| `WitherSkullRendererMixin` | 构造器创建备用模型 + `extractRenderState` 写变体 + `getTextureLocation`/`submit` 按变体替换贴图和模型 |
| client mixin JSON | 注册上面两个 Mixin |

**关键点**：
- 贴图替换用 `@Inject getTextureLocation RETURN` + `cir.setReturnValue()` 最可靠（不依赖 `submitModel` 调用匹配）
- 模型替换用 `@ModifyArg submitModel index=0`，target 类名必须和字节码中的实际调用类一致
- 模型层选 `PLAYER_HEAD`（UV 匹配玩家皮肤格式），不要用原版的 `WITHER_SKULL`（UV 偏移不同导致贴图错位）

### 9. SynchedEntityData 脏标记强制刷新

`SynchedEntityData.set()` 在 `readAdditionalSaveData` 中设置的值可能不标记脏，导致重进游戏后客户端状态视觉重置（模式/跟随状态丢失，需要重新打开背包才恢复）。

**症状**：存档重载后服务端逻辑正常，但客户端显示不正确，重新打开背包 GUI 后恢复。

**修复**：覆写 `startSeenByPlayer`，先设一个临时不同值再设回正确值，强制标记脏：

```java
@Override
public void startSeenByPlayer(ServerPlayer player) {
    super.startSeenByPlayer(player);
    // 强制刷新客户端同步——readAdditionalSaveData 中的 set 可能不标记脏
    int mode = getEntityData().get(DATA_ACTIVE_MODE);
    getEntityData().set(DATA_ACTIVE_MODE, -1);  // 临时不同值
    getEntityData().set(DATA_ACTIVE_MODE, mode); // 设回正确值
}
```

### 10. 变体属性三处一致性

自定义实体的等级/变体属性如果影响血量上限、常驻药水等，必须在**三处**保持一致，遗漏任一处会导致存档重载后属性丢失：

| 位置 | 时机 | 说明 |
|---|---|---|
| `setDollLevel()` | 升级 / 蛋生成 | 设置等级时同步调整血量和药水 |
| `setDollVariant()` | 蛋生成 | 设置变体时同步调整血量和药水 |
| `readAdditionalSaveData()` | 存档加载 | 从 NBT 读取后重新应用所有属性 |

**排查方法**：存档重载后血量变回 20（默认值）→ 说明 `readAdditionalSaveData` 中遗漏了属性重设。

**读取顺序**：`readAdditionalSaveData` 中先读自定义数据（等级、变体）再调 `super.readAdditionalSaveData()`，否则 super 可能覆盖自定义属性。

### 11. 纹理文件格式与命名校验

| 检查项 | 要求 | 后果 |
|---|---|---|
| 格式 | 必须是 PNG | JPG 会导致渲染异常（黑块/紫黑方块） |
| 文件名 | 必须与注册 ID 完全一致 | 不一致导致找不到纹理 |
| 残留文件 | 项目目录不残留 JPG/旧版纹理 | 可能被构建系统打包进去导致冲突 |

**排查方法**：贴图显示异常时，先用 `file` 命令确认格式，再检查路径与 `Identifier` 是否匹配：

```bash
# 确认文件格式（不是看扩展名，看文件头）
file src/main/resources/assets/doll-mod/textures/entity/doll/nether_doll.png
# 输出应为 "PNG image data, ..."
```

### 12. 重写优先于 Mixin

能用原版 API 覆写（`@Override`）实现的，**不要用 Mixin**。Mixin 是最后手段，只在以下场景使用：

- 原版方法 `private` 或 `final` 无法覆写
- 原版类 `final` 无法继承
- 需要拦截原版内部调用链中的某个点（如 `canAttack` 被多个 Goal 调用）

**覆写更安全的原因**：
- 编译期检查签名（Mixin 的 `target` 字符串不做编译期检查）
- 不会因原版代码重构导致运行时注入失败
- 不需要在 mixin JSON 中注册

**示例**：免疫凋零效果用 `@Override canBeAffected()` 返回 false，而不是 Mixin 拦截 `addEffect`。

### 13. datagen 翻译键用字符串键

`FabricLanguageProvider` 中添加翻译时，用字符串键比用 `Item` 对象更可靠：

```java
// ✅ 可靠——直接用字符串键
b.add("item." + MOD_ID + "." + id, "下界人偶蛋");

// ❌ 对某些 Item 不生效
b.add(ModItems.NETHER_DOLL_EGG, "下界人偶蛋");
```

**排查方法**：`runDatagen` 后检查生成的 `en_us.json` / `zh_cn.json`，搜索缺失的翻译键。如果某个键的值为空或缺失，改成字符串键重跑。

### 14. 新增物品的三处注册清单

新增物品/方块时，除了在 `DollMod` 中注册外，还有几处容易遗漏。每次新增后对照检查：

| 检查项 | 文件 | source set | 说明 |
|---|---|---|---|
| 主类注册 | `DollMod.java` | main | `Registry.register(...)` 注册方块/物品/BlockEntityType |
| 创造物品栏 | `DollCreativeTab.java` | main | `output.accept(...)` 将物品加入创造标签页 |
| datagen 翻译+配方 | `DollDataGenerator.java` | main | 翻译键（字符串键）+ 合成配方 |
| BER 注册（头颅方块） | `DollModClient.java` | client | `BlockEntityRenderers.register(...)` 注册方块实体渲染器 |
| SkullBlockRenderer Mixin | `SkullBlockRendererMixin.java` | client | 头颅皮肤纹理映射 + 模型创建 |
| 皮肤贴图 | `DollEntityClientMixin.java` | client | `getSkin()` 按变体返回对应皮肤纹理 |

**常见症状与排查**：

| 症状 | 漏了什么 |
|---|---|
| 创造模式物品栏里找不到 | `DollCreativeTab` 漏 `output.accept()` |
| 头颅方块放置后透明（只有破坏粒子） | `DollModClient` 漏 BER 注册，或缺 `XxxDollHeadRenderer.java` |
| 头颅佩戴在头上显示紫黑方块 | `SkullBlockRendererMixin` 漏贴图映射 |
| 破坏头颅时方块显示紫黑方块（crack 覆盖层 / 破坏粒子） | `models/block/xxx_head.json` 的 `particle` 指向了 entity 纹理，或 `textures/block/<variant>_doll_head.png` 缺失 |
| 人偶实体显示默认皮肤 | `DollEntityClientMixin.getSkin()` 漏变体分支 |
| 翻译键缺失（显示为 `item.doll-mod.xxx`） | `DollDataGenerator` 漏翻译键 |

### 15. 同一投射物按 owner 变体区分行为

当多个变体共用同一种原版投射物（如 WitherSkull）但需要不同命中效果时，在 Mixin 中通过 `getOwner()` 获取发射者并检查变体：

```java
// WitherSkullMixin — 按 owner 变体区分
@Redirect(method = "onHit", at = @At(...))
private void dollNoExplode(...) {
    WitherSkull self = (WitherSkull)(Object)this;
    if (self.getOwner() instanceof DollEntity doll) {
        if (doll.isEnderDoll()) {
            // 末影人偶：生成龙息云
            spawnBreathCloud(level, self, doll);
        }
        // NETHER 和 ENDER 都跳过爆炸
    } else {
        level.explode(...);  // 原版行为
    }
}

// 渲染端 — DollSkullState 存储 DollVariant 而非 boolean
@Inject(method = "extractRenderState", at = @At("RETURN"))
private void dollMod$setVariant(WitherSkull skull, WitherSkullRenderState state, float pt, CallbackInfo ci) {
    DollVariant variant = skull.getOwner() instanceof DollEntity doll
        ? doll.getDollVariant() : DollVariant.NONE;
    ((DollSkullState)(Object)state).dollMod$setVariant(variant);
}

@Inject(method = "getTextureLocation", at = @At("RETURN"), cancellable = true)
private void dollMod$swapTexture(WitherSkullRenderState state, CallbackInfoReturnable<Identifier> cir) {
    switch (((DollSkullState)(Object)state).dollMod$getVariant()) {
        case NETHER -> cir.setReturnValue(nether_doll_texture);
        case ENDER  -> cir.setReturnValue(ender_doll_texture);
        default -> {}
    }
}
```

**优势**：统一投射物类型（WSkull），复用同一套 Mixin/渲染管线，仅通过 owner 变体区分行为和贴图。新增变体只需在 switch 中加一个 case。

### 16. 新增变体检查清单

> 海洋人偶已按此清单完成实现（枚举 / 蛋物品 / 头颅方块 / 皮肤 / datagen / 属性三处一致 / 行为分支均已落地）。

新增一个人偶变体（如森林）需要改的文件和检查项，按顺序执行：

| 步骤 | 文件 | source set | 说明 |
|---|---|---|---|
| 1. 枚举值 | `DollVariant.java` | main | 加新变体枚举值（已落地示例：`SEA`/`FOREST`） |
| 2. 蛋物品 | `DollMod.java` | main | 注册 `{variant}_doll_egg` 物品（`DollSpawnEggItem(level, DollVariant.XXX)`） |
| 3. 头颅方块 8 层 | `block/` + `item/` + `client/renderer/` | main+client | 按 8 层架构实现 SkullType/HeadBlock/HeadBlockEntity/HeadItem/注册/RendererMixin/BER/items JSON（参考上方「自定义头颅方块」章节） |
| 4. 创造物品栏 | `DollCreativeTab.java` | main | `output.accept()` 加入蛋和头颅 |
| 5. datagen 翻译+配方 | `DollDataGenerator.java` | main | 翻译键（字符串键）+ 合成配方（头颅 2×2 + 头颅围 8 主题方块→蛋） |
| 6. 属性三处一致 | `DollEntity.java` | main | `setDollLevel()` + `setDollVariant()` + `readAdditionalSaveData()` 三处加变体分支（血量/药水/特殊免疫） |
| 7. 排除自动推导 | `DollEntity.setDollLevel()` | main | `getDollVariant() != XXX` 加入 level→WARDEN 排除条件 |
| 8. 变体查询方法 | `DollEntity.java` | main | 如需 `isXxxDoll()` 方法，加在 `isEnderDoll()` 旁边 |
| 9. 被动技能 | `DollEntity.tick()` | main | 步骤 8（变体被动技能）和步骤 9（光环）加 `if (isXxxDoll())` 分支 |
| 10. 无武器战斗 | `DollEntity.java` | main | 5 处"无武器兜底"判断加 `|| isXxxDoll()`（参考 `isWardenDoll()||isNetherDoll()||isEnderDoll()` 模式） |
| 11. 皮肤贴图 | `DollEntityClientMixin.java` | client | `getSkin()` 加变体分支返回对应皮肤 `Identifier` |
| 12. 头颅渲染 | `SkullBlockRendererMixin.java` | client | 皮肤纹理映射 + 模型创建加变体分支 |
| 13. 投射物贴图（如有远程） | `WitherSkullRendererMixin.java` | client | 如复用 WitherSkull，`getTextureLocation` 的 switch 加 `case XXX` |
| 14. 投射物行为（如有远程） | `WitherSkullMixin.java` | main | `dollNoExplode`/`dollSkullDamage`/`dollReplaceWitherEffect` 的变体判断加 `case XXX` |
| 15. 编译验证 | — | — | `./gradlew build` 全量通过 |

> **关键约束**：步骤 6 三处一致性遗漏任一处会导致存档重载后属性丢失（详见技巧 #10）。步骤 10 无武器战斗判断遗漏会导致空手时人偶发呆不攻击。

## 历史事故记录（2026-08-22，已解决，非既有 bug）

### 事故：首帧渲染诊断代码导致 NPE 崩溃

**起因**：2026-08-22 调试首帧渲染卡顿时，往 `DollEntityClientMixin.getSkin()` 内植入诊断代码——调用 `Minecraft.getInstance().getTextureManager().getTexture(skinId)`，目的是测量首次取皮肤纹理的耗时。

**崩溃现象**：用蛋生成人偶的瞬间，客户端偶发 NPE 崩溃：
```
java.lang.NullPointerException: Cannot invoke
"net.minecraft.client.entity.ClientAvatarState.deltaMovementOnPreviousTick()"
because the return value of "net.minecraft.client.entity.ClientAvatarEntity.avatarState()"
is null
```

**根因（最终确认）**：`getSkin()` 内调 `TextureManager.getTexture` 对未知纹理 id 走 `SimpleTexture` + `registerAndLoad`，**把渲染管线提前唤醒到实体构造期**。此时 `DollEntity` 实例正在 `<init>`、Mixin 的 `avatarState` 字段（`private final ClientAvatarState avatarState = new ClientAvatarState();`）尚未赋值完，渲染管线在同一调用栈内读 `avatarState()` → null → NPE。无诊断代码时，`getSkin()` 只在构造完成后的正常渲染帧被调用，`avatarState` 已就绪，故不崩。

**解决**：诊断代码已全部回滚（`getSkin()` 恢复到原始稳定版，仅返回 `cachedXxxSkin` 分支），纯净版用户实测不崩。这不是模组既有 bug，是诊断代码副作用。

**早期过度归因（已纠正）**：曾一度把 NPE 升级为"时序敏感的独立问题 / 疑似 Mixin 字段织入时机 / 26.2 `ClientAvatarEntity` 调用约定相关"，理由是"仅记时间戳、不碰渲染管线的版本仍崩"。**此归因不成立**——用户最终确认：纯净版（无任何诊断代码）从不崩，崩溃只在植入诊断代码后才出现。所谓"时序敏感"是诊断代码改变 `getSkin` 调用形态的副作用，而非框架本身的坑。把责任从"诊断代码副作用"推给"框架可能有坑"是错误的。

**铁律（记入，避免再犯）**：往 `ClientAvatarEntity` Mixin 的 `getSkin` / `avatarState` 相关方法加诊断代码时，**严禁调用 `Minecraft.getInstance()` / `TextureManager.getTexture` 等会唤醒渲染管线的 API**——会在实体构造期触发 `avatarState` 未就绪 NPE。只记时间戳也有风险（改变方法形态），后续如需诊断首帧渲染，须用独立 Mixin 注入 `AvatarRenderer` 而非改 `getSkin`。

### 问题 B：首帧渲染卡顿 1801ms（经确认非 bug，已关闭）

**现象**：`[DollClient] 客户端 tick 间隔 1801ms`（>500ms 即卡顿），发生在首次渲染某变体人偶时，根因为皮肤纹理 / 模型 / 着色器首帧惰性加载 + JVM 类加载/JIT 热身。

**最终定性（2026-08-22 用户实测确认）**：卡顿**仅在游戏开启前的加载阶段出现**，进入游戏后（操作/联机/人偶干活）完全流畅、无感。属加载期一次性正常开销，玩家感知不到，**非 bug，无需修复**。绝大多数模组开发者在日志里都会看到同类 tick 超时提示，属正常现象。

**外部 AI 方案评估（驳回）**：曾有外部 AI 建议"找 render 方法外提纹理/模型创建 → 删 if(model==null) → ClientEntrypoint 静态缓存 → 加渲染耗时日志"。经核查**不适用本项目**：
- 人偶主渲染复用原版 `AvatarRenderer`（`DollModClient` 注册 `new AvatarRenderer(ctx, true)`），**无自定义 render 方法**可改；
- 纹理 `Identifier` 全是 `static final` 常量（`DollEntityClientMixin` 39-54 行），皮肤 `PlayerSkin` 是字段级 `cachedXxxSkin`（61-76 行），**无懒加载 if(model==null) 可删**；
- 模型走 `ModelLayers.PLAYER_SLIM` 标准 bakeLayer，非 render 内 new。
照做只会改了个寂寞或误删现有缓存保护引入 NPE 风险。

**预热优化（可选，非必需）**：若想让加载期更顺，可在 `DollModClient.onInitializeClient()` 末尾对 8 个变体皮肤 `Identifier` 调 `TextureManager.getTexture(id)` 预热（启动空闲期注册+异步加载，首次渲染不再阻塞）。安全前提：`onInitializeClient` 在客户端启动早期、无人偶实体，绝不从实体构造路径触发 → 不撞问题 A 的 NPE 雷。阻碍：8 个 Identifier 常量当前是 `DollEntityClientMixin` 的 private static，`DollModClient` 访问不到 → 需提取到公共位置。属锦上添花，暂不实施。

**诊断工具（已移除）**：`DollEntityClientMixin.getSkin()` 内的 `[DollSkin]` 一次性计时日志原用于定位卡顿层，因会提高问题 A 触发率，**已移除**。现存诊断噪音（`AvatarRendererDollMixin` 的"人偶首次渲染开始"日志、`DollEntityClientMixin` 的"人偶实体客户端构造完成"日志、`DollModClient` 的 500ms tick 看门狗）均为当初查 8s 延迟/首帧卡顿时埋的诊断代码，8s 问题已靠关 UUID 修复解决、三老 bug 全修复，这些诊断日志现属可清理噪音（"无伤删除"候选，待用户拍板）。

## License

CC0-1.0（全文见根目录 `LICENSE`）。
