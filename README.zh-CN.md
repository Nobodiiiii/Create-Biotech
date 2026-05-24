# Create: Biotech

[English](README.md) · [中文](README.zh-CN.md) · [玩家介绍](docs/INTRODUCTION.zh-CN.md) · [Player Intro](docs/INTRODUCTION.md)

一个面向 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 的 Minecraft 1.20.1 Forge 附属模组：把活体生物和生物材料接入 Create 的动能与加工系统。史莱姆变成传送带，恶魂变成热气球，唤魔者主持附魔仪式 —— 模组其余部分负责把这些东西接到应力、工作盆、装置 (contraption)、漏斗以及 JEI 上。

本 README 面向贡献者与 AI 编码代理。玩家请阅读 [docs/INTRODUCTION.zh-CN.md](docs/INTRODUCTION.zh-CN.md)。

---

## 构建环境

| 项目 | 值 |
| --- | --- |
| Minecraft | 1.20.1 |
| 加载器 | Forge 47.1.33（通过 `net.neoforged.moddev.legacyforge`） |
| Java | 17 |
| 模组 id / 版本 | `create_biotech` / 见 [gradle.properties](gradle.properties) |
| 硬依赖 | Create 6.0.8、Registrate、Flywheel、Ponder |
| 软依赖 | JEI、Jade |
| Mixin 配置 | [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json) |
| 映射表 | Parchment 2023.09.03 |

```bash
./gradlew build                     # 构建 jar
./gradlew runClient                 # 启动开发客户端
./gradlew runServer                 # 启动开发服务端
./gradlew runData                   # 重新生成数据生成产物
./gradlew quickPlayClient -Pinstance=<name>   # 构建 + 拷贝 + 通过 test.py 启动外部实例
```

## 仓库结构

```
src/main/java/com/nobodiiiii/createbiotech/
  CreateBiotech.java        # @Mod 入口，注册表与事件总线接线
  registry/                 # CBBlocks、CBItems、CBFluids、CB*Types —— 所有 Registrate 注册
  content/                  # 一个特性 = 一个包（slimebelt、ghasthotairballoon、processing/basin…）
  client/                   # 仅客户端的渲染器、粒子、GUI 钩子
  network/                  # CBPackets 与各类数据包定义
  mixin/                    # Create 与原版 mixin（完整列表见 mixins.json）
  compat/                   # JEI、Jade 集成
  ponder/                   # Ponder 场景脚本
  foundation/, infrastructure/, event/   # 共享工具、GUI、装置移动行为辅助

src/main/resources/
  assets/create_biotech/    # 模型、贴图、lang（en_us.json、zh_cn.json）、ponder
  data/                     # 配方、tag、进度（手写 + 数据生成混合）
  META-INF/mods.toml        # 模组元数据，按 gradle.properties 模板化
  create_biotech.mixins.json

ref/                        # 内嵌的 Create + JEI 源码 —— 见下方 CLAUDE.md 规则
run/                        # 开发期运行时（存档、配置、日志）
tools/, test.py             # 辅助脚本；test.py 驱动 quickPlayClient
```

## 工作约定

先读后写：

1. **`ref/Create/` 与 `ref/jei/` 是 Create 和 JEI 的本地权威源码。** 修改集成代码前先用 `rg` 在这里搜索。**不要**反编译 jar，也不要联网获取上游代码，除非用户明确要求。详见 [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md)。
2. **注册一律走 Registrate。** 新的 block / item / fluid / entity / menu 加在对应的 `registry/CB*.java` 里，不要另起炉灶。
3. **特性代码集中在 `content/` 下的单一包内。** 每个特性自带 block、block entity、渲染器、item 及相关 handler。跨特性的公用代码放 `foundation/` 或 `infrastructure/`。
4. **修改 Create 或原版行为通过 mixin 完成。** 每个新 mixin 都要登记到 [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json)；涉及客户端代码的放到 `client` 列表。
5. **lang 键双语对齐。** 每新增一个 key，`en_us.json` 与 `zh_cn.json` 都要补。

## 主要系统速览

- **自定义传送带** —— 史莱姆传送带（双轨循环）、岩浆怪传送带（破坏性火焰运输）、人力传送带（玩家驱动发电）。三者都是 Create 风格的传送带形态，完整支持漏斗 / 通道 / mixin。
- **工作盆生物加工** —— 实体可通过漏斗投入工作盆，被机械压机 / 搅拌器作为材料消耗。配方放在 `content/processing/basin/`。
- **装置 (contraption)** —— 恶魂热气球可组装为移动装置，支持绳 / 剪刀 / 磁体交互；缓冲垫提供按颜色区分的移动行为。
- **专用机械** —— 蜘蛛组装台、鱿鱼打印机（附魔书复印）、唤魔者附魔室、苦力怕爆破室、生物打包机、经验泵 / 结晶器 / 经验簇 / 经验罐、薛定谔的猫（量子红石）、万向节（三维旋转传递）、骨棘轮、史莱姆离合器、固定胡萝卜钓竿、防爆物品仓、纸箱捕获生物。
- **流体** —— 液态活史莱姆，以及一种等价于经验的流体。

打开 [src/main/java/com/nobodiiiii/createbiotech/content/](src/main/java/com/nobodiiiii/createbiotech/content/)，子文件夹名称与上述特性一一对应。

## 许可证

MIT，详见 [gradle.properties](gradle.properties)。
