<p align="center">
  <img src="src/main/resources/assets/doll-mod/icon.png" width="128" height="128" alt="人偶模组图标">
</p>

<h1 align="center">人偶模组 · Doll Mod</h1>

<p align="center">
一个把人偶当作<strong>可消耗工具</strong>来用的 Minecraft Fabric 模组：召唤、武装、指挥，人偶死了也不心疼。
</p>

<p align="center">
  <a href="https://fabricmc.net/"><img src="https://img.shields.io/badge/Fabric-26.2-1976d2?style=for-the-badge&logo=fabricmc" alt="Minecraft 26.2"/></a>
  <img src="https://img.shields.io/badge/Loader-0.19.3+-1976d2?style=for-the-badge" alt="Fabric Loader 0.19.3+"/>
  <img src="https://img.shields.io/badge/Java-25+-1976d2?style=for-the-badge&logo=openjdk" alt="Java 25+"/>
  <img src="https://img.shields.io/badge/License-CC0--1.0-1976d2?style=for-the-badge" alt="CC0-1.0"/>
</p>

<hr/>

## 简介

用刷怪蛋召唤属于你的、可自定义名字的人偶，给它装备武器与盔甲，再用 **8 种行为模式**指挥它干活。人偶是消耗品——死了会连配置一起丢失，所以放心派它去危险环境、成群流水线作业而不必心疼。

游戏内置 **人偶指南书**，首次进世界自动发放，分章节讲解全部玩法。

## 特性一览

| 分类 | 内容 |
|------|------|
| 人偶 | S1–S5 五阶升级体系 + 7 种天赋特殊人偶（幽匿/苍白/下界/末影/海洋/森林/向导） |
| 行为 | 近战 / 射手 / 耕种 / 喂食 / 砍树 / 挖矿 / 插火把 / 钓鱼，附作业区与盾构机掘进 |
| 系统 | 45 格专属背包、跨维度召唤与召回、人偶遥控器远程管理 |
| 武器 | 末影斧、地狱剑、苍白弓、荆棘盾牌 |
| 装备 | 海洋套装、7 种人偶头颅方块 |
| 方块 | 石砧（廉价铁砧）、幽匿灵龛（召唤 BOSS 的祭坛） |
| 内容 | 野生幽匿人偶 BOSS、内置人偶指南书 |

## 环境要求

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.3+**
- [Fabric API](https://modrinth.com/mod/fabric-api)（版本需匹配 26.2）
- Java **25+**（构建时需要）

## 安装

1. 安装 Fabric Loader 与 Fabric API。
2. 将模组 jar 放入 `.minecraft/mods` 文件夹。
3. 启动游戏，创造模式物品栏会出现「人偶」标签页。

## 快速上手

1. **合成 S1 蛋**：上下左右四个工作台 + 中间箱子。
2. **铁砧赐名**：只有命名过的蛋才能召唤人偶。
3. **右键地面** 召唤；已绑定的蛋再右键可召回（支持跨维度）。
4. 右键人偶打开背包，切换行为模式；浏览向导人偶的**搜索界面**可按结构/群系/村庄查找。

> 首次进世界背包中即有人偶**指南书**，右键开启即可得全部玩法与配方。

## 配置

外置 JSON 配置：`config/dollmod/doll.json`（首启自动生成），涵盖索敌 / 觅途 / 跟随 / 各模式阈值等调参项，改后执行 **`/dollmod reload`**（OP 权限）即可即时生效，无需重启。

## 构建

```bash
./gradlew runClient     # 启动开发实例
./gradlew runServer     # 启动服务端开发实例
./gradlew build         # 编译并打包，输出至 build/libs/
./gradlew runDatagen    # 运行数据生成器
```

> 基于 Fabric + Minecraft 26.2 + Mojang 映射 + Loom 1.17，需 JDK 25+。源码结构与核心类说明见 [DEV_NOTES.md](DEV_NOTES.md)。

## 兼容性与已知问题

- **首帧加载期卡顿**：日志可能出现 `客户端 tick 间隔 1801ms` 提示，仅发生在进游戏前的加载阶段（资源惰性加载 + JVM 热身）。进入游戏后完全流畅，属正常现象。
- **无已知运行时崩溃**。

## 关于本模组

本模组**全部代码由 AI 编写**，作者（人没事就好）负责美术资源制作、玩法设计与实机测试。这是作者首次尝试 Minecraft mod 开发的实践作品。

## License

CC0-1.0（全文见 [LICENSE](LICENSE)）。