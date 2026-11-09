# 驮运箱载体修正总表

日期：2026-11-09。适用：Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281。

## 诊断依据

最早的报告采集于 `2026-11-07T16:49:21.845055300Z`，早于本次反馈，可能已过时；本次采用同一存档中更新的报告
`20261109-100245-593_d5701747-49bf-4d32-bf1a-7a5c79b93e02_10448029124009526858.json`。
该报告的 `captured_at_utc` 为 `2026-11-09T10:02:45.593419Z`，即北京时间 2026-11-09 18:02:45。

报告中的供体为驴，仅保留 `Source=0, Cube=0–4` 的头部，原始身子为已移除的 `Cube=5, body`。
`rideable` 已正确显示无载体，但 `cargo_chest` 仍绑定全部五个头部体块，覆盖率为 1 且生效。
原因是 `CARGO_CHEST` 使用 `Carrier.allTissue()`；装配范围 `TORSO` 只表示安装位置，不能代替供体身子载体筛选。

## 修改总表

| 生物 | 修改前 | 修改后 | 容量 |
| --- | --- | --- | --- |
| 驴 `minecraft:donkey` | 全部保留体块均可提供驮运箱，包括头、嘴、耳、腿、尾 | 仅原始模型 `body` 身子提供 | 每箱 15 格 |
| 骡 `minecraft:mule` | 同上 | 仅原始模型 `body` 身子提供 | 每箱 15 格 |
| 羊驼 `minecraft:llama` | 全部保留体块均可提供，包括头、腿等 | 仅原始模型 `body` 身子提供 | 每箱 Strength × 3 格 |
| 行商羊驼 `minecraft:trader_llama` | 同上 | 仅原始模型 `body` 身子提供 | 每箱 Strength × 3 格 |

共同调整：

- [BionicTrait.java](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTrait.java)：驮运箱改为 `TORSO` 供体载体，保留原有躯干范围与 50% 覆盖率门槛。
- [cargo_chest.json](../src/main/resources/data/create_biotech/bionic_traits/cargo_chest.json)：为上述四种生物明确配置 `TORSO: ["body"]`。身子被移除后不回退到其他部件，箱子模型本身也不提供特性。
- 手术台、纸箱检查和实体运行时共用载体解析，特性归属、装箱位置与有效容器成员同步修正；JEI 的通用说明自动显示躯干载体。
- 沿用现有 `BionicEquipment.tick()` 清理：旧存档中绑定在无效部位的箱子及箱内物品掉落返还。已有正确解剖标签的装配体重新加载后即可按新规则计算；早期缺少标签的供体需回手术台重新采集、打包。
- [特性总表](bionic-traits-reference.zh-CN.md)同步更新载体、交互位置及存档处理说明。

## 其他特性检查

检查了 64 项特性定义及其供体载体映射。与本问题相关的其他器官载体规则如下：

| 检查项 | 检查结果 |
| --- | --- |
| 可骑乘：马、驴、骡、骷髅马、僵尸马、骆驼、羊驼、行商羊驼、猪、炽足兽 | 原版供体已有独立背部选择器，仅匹配 `body`，无须修改 |
| 可挤奶：牛、哞菇、山羊；产蛋：鸡；可剪毛：羊 | 已分别绑定身体、乳房或羊毛，无须修改 |
| 缩壳防御：潜影贝、犰狳；脱落鳞甲：犰狳 | 已分别绑定壳和身体，无须修改 |
| 音波冲击、振动感知：监守者；吼叫：劫掠兽；授粉：蜜蜂 | 已有明确器官载体与配套条件，无须修改 |
| 头部投射、守卫者射线、施法、拾取装备、翅膀及腿部特性 | 已使用对应头、眼、攻击手、翅膀、腿或足载体，无须修改 |

悬浮飞行、身体弹跳、游泳专长、抗击退、自爆、击退强化、破门等现有规则允许对应供体组织安装到指定范围后贡献特性；这些规则在现行特性总表中明确使用全部供体组织，本次保持其设计。全身体质特性继续作用于全部供体组织。

原版部件依据：[ChestedHorseModel.java](../ref/1.21.1/Minecraft/net/minecraft/client/model/ChestedHorseModel.java)、[LlamaModel.java](../ref/1.21.1/Minecraft/net/minecraft/client/model/LlamaModel.java)；两类模型均有明确的 `body`，箱子部件为独立的 `left_chest` / `right_chest`。

## 验证

- `./gradlew --offline compileJava processResources` 通过，仅有现有弃用警告。
- 使用项目 Java 21 的 JShell 在内存中调用实际载体选择方法：四种生物均只选择报告中的 `Cube=5, body`；报告现存的头部无载体，单独保留身子时仍有载体；头、耳、嘴、腿、尾以及 `left_chest`、`body/head` 均不误匹配；覆盖率门槛仍为 0.5。
- 未新增测试文件，未启动游戏客户端；上述验证不包含游戏内实测。
