<p align="center">
  <img src="src/main/resources/assets/doll-mod/icon.png" width="128" height="128" alt="人偶模组图标">
</p>

<h1 align="center">🧟 人偶模组 · Doll Mod</h1>

<p align="center">
  把人偶当作<strong>可消耗工具</strong>来用的 Minecraft Fabric 模组。
  <br/>
  召唤、武装、指挥 —— 人偶死了，也不心疼。
</p>

<p align="center">
  <a href="https://fabricmc.net/"><img src="https://img.shields.io/badge/Minecraft-26.2-1976d2?style=for-the-badge&logo=badgr" alt="Minecraft 26.2"/></a>
  <img src="https://img.shields.io/badge/Fabric-Loader%200.19.3+-1976d2?style=for-the-badge&logo=fabricmc" alt="Fabric Loader 0.19.3+"/>
  <img src="https://img.shields.io/badge/Java-25+-1976d2?style=for-the-badge&logo=openjdk" alt="Java 25+"/>
  <img src="https://img.shields.io/badge/License-CC0--1.0-1976d2?style=for-the-badge" alt="CC0-1.0"/>
  <img src="https://img.shields.io/badge/Platform-Client%20%2B%20Server-1976d2?style=for-the-badge" alt="双端"/>
</p>

<hr/>

## 🌟 这是什么？

用刷怪蛋召唤属于你的、可自定义名字的人偶，给它装备武器与盔甲，再用 **8 种行为模式** 指挥它干活。

人偶是**消耗品**——死了会连配置一起丢掉。所以放心派它淋雨、走夜路、闯岩浆房、成群流水线作业，一点也不心疼。

开局即送 **人偶指南书**，右键翻开，全部玩法与配方都在里面。

## 🧱 特性一览

| 分类 | 内容 |
|------|------|
| 🧍 人偶 | S1–S5 五阶升级体系 + 7 种天赋特殊人偶（幽匿/苍白/下界/末影/海洋/森林/向导） |
| ⚔️ 作战 | 近战 / 射手，附作业区与盾构机掘进 |
| 🌾 农务 | 耕种 / 喂食 / 砍树 / 挖矿 / 插火把 / 钓鱼 |
| 🎒 系统 | 45 格专属背包、跨维度召唤与召回、人偶遥控器远程管理 |
| 🛡️ 装备 | 海洋套装、7 种人偶头颅方块 |
| 🗡️ 武器 | 末影斧、地狱剑、苍白弓、荆棘盾牌 |
| 🧱 方块 | 石砧（廉价铁砧）、幽匿灵龛（召唤 BOSS 的祭坛） |
| 👹 内容 | 野生幽匿人偶 BOSS、内置人偶指南书 |

## 📸 截图

*（即将补充：人偶栏、行为切换、BOSS 战等实机截图）*

## 🚀 快速上手

1. **合成 S1 蛋**：上下左右四个工作台 + 中间箱子。
2. **铁砧赐名**：只有命名过的蛋才能召唤人偶。
3. **右键地面** 召唤；已绑定的蛋再右键可召回（支持跨维度）。
4. **右键人偶** 打开背包，切换行为模式。

> 💡 首次进世界背包中即有人偶**指南书**，右键开启即可得全部玩法与配方。

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 与 [Fabric API](https://modrinth.com/mod/fabric-api)（需匹配 Minecraft 26.2）。
2. 将模组 jar 放入 `.minecraft/mods` 文件夹。
3. 启动游戏，创造模式物品栏会出现「人偶」标签页。

> 服务端与客户端**需同时安装**本模组（新增了实体、方块与物品）。

## ⚙️ 配置

外置 JSON 配置：`config/dollmod/doll.json`（首启自动生成），涵盖索敌 / 觅途 / 跟随 / 各模式阈值等调参项。

改后执行 **`/dollmod reload`**（OP 权限）即可即时生效，无需重启。

## 🧪 构建

```bash
./gradlew runClient     # 启动开发实例
./gradlew runServer     # 启动服务端开发实例
./gradlew build         # 编译并打包，输出至 build/libs/
./gradlew runDatagen    # 运行数据生成器
```

> 基于 Fabric + Minecraft 26.2 + Mojang 映射 + Loom 1.17，需 JDK 25+。源码结构与核心类说明见 [DEV_NOTES.md](DEV_NOTES.md)。

## ⚠️ 兼容性与已知问题

- **首帧加载期卡顿**：日志可能出现 `客户端 tick 间隔 1801ms` 提示，仅发生在进游戏前的加载阶段（资源惰性加载 + JVM 热身）。进入游戏后完全流畅，属正常现象。
- **无已知运行时崩溃**。

## 🧑‍🤝‍🧑 关于与贡献

本模组**全部代码由 AI 编写**，作者负责美术资源制作、玩法设计与实机测试。这是作者首次尝试 Minecraft mod 开发的实践作品。

如果你发现 bug 或有改进想法，欢迎在 [GitHub Issues](https://github.com/10086-ovo/doll-mod/issues) 提出；喜欢的话也欢迎 Star ⭐ 支持。

## 📜 License

[CC0-1.0](LICENSE) — 放弃版权，随意使用、修改与分发。