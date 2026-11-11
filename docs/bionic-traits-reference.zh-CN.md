# 仿生生物特性总表 v3

修订日期：2026-11-12。适用分支：Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281。

本表按当前工作区代码与数据整理 v3 特性规则与方法选用。当前共 67 项，按全身、躯干、头、臂、腿排列。原“修正”列随完整原表归档于[原修正表](bionic-traits-reference-v2-corrections-2026-11-06.zh-CN.md)，此前记录见[特性修正历史](bionic-traits-history.zh-CN.md)。

## 一、通用规则

### 1. 方法选用与标签

现有实现直接复用可接入的方法、Goal、控制器、导航、物品容器与模型部件。绑定供体具体实体类的内部方法，以原版行为为依据，由本项目调度器或状态计数器适配，具体条件与时序见各项说明。相似行为合并后选一个较常见供体作为统一模板，供体共同贡献同一项覆盖率。具有不同效果种类的投射、药效与施法沿用各自子项。

“技能”标记参与索敌、寻路、移动控制、行为动作或主动施放的特性；“被动”标记体质、数值、命中附效与交互产出。技能的使用时机由实际接入的 Goal、控制器或项目调度器决定：直接复用的 Goal 沿用其 `canUse`、`start`、`tick`、`stop` 流程，其余能力按当前代码维护目标、状态与冷却。

装配占比、有效肢体数量、翅膀承重、史莱姆球驯服、主人关系、智力选择、装配座位及明确写出的现有装配系数沿用本项目规则。供体的行为时序按表内列出的当前实现执行。伤害、传送、物品交互和方块变更接入当前 NeoForge 事件流程。

| 合并行为 | 统一模板 | 采用的方法 |
| --- | --- | --- |
| 接触反伤、体表刺击 | 守卫者 | `Guardian.hurt` 的静止反伤分支，门槛达标后固定造成 2 点伤害 |
| 末影人、潜影贝瞬移 | 末影人 | `EnderMan.hurt`、`teleport`、`teleportTowards` 与目标 Goal 的追近计算，移除注视判断 |
| 潜影贝、犰狳缩壳 | 犰狳 | 参照 `ArmadilloBallUp` 的卷缩状态，由 `SlimeBionicEntity.tickShellDefense` 维护危险计数与状态切换 |
| 保湿依赖 | 海豚 | `Dolphin.tick` 的 2400 tick 保湿与干涸伤害 |
| 离水窒息 | 鱼类 | `WaterAnimal.handleAirSupply` 的计时与伤害 |
| 摔落抵抗 | 马 | 安全距离 6、伤害倍率 0.5 |
| 近战击飞 | 铁傀儡 | 成功命中后纵向增加 0.4×击退抗性系数 |
| 中毒附效攻击 | 蜜蜂 | 按难度施加中毒的分支 |
| 呼唤同伴 | 狼 | `HurtByTargetGoal.setAlertOthers` 的受伤呼援流程 |
| 扑翼缓降 | 鸡 | 下落纵向速度 ×0.6 |
| 游泳物理 | 美西螈 | 平滑游泳控制、中性浮力与水中阻尼 |

### 2. 范围、载体与覆盖率

| 作用范围 | 代码 | 判定体块 |
| --- | --- | --- |
| 全身 | `WHOLE_BODY` | 全部保留体块 |
| 躯干 | `TORSO` | 装配中归入躯干的保留体块 |
| 头 | `HEAD` | 颈关节安装的唯一头部及所属体块；未安装头部时按整个躯干计算 |
| 臂 | `ARM` | 每条肩关节及所属肘关节链 |
| 腿 | `LEG` | 每条髋关节及所属膝关节链 |

供体载体标签用于筛选贡献体块，作用范围由装配位置确定。覆盖率 c = 匹配供体及载体的体积 / 对应范围全部保留体块体积。全身、躯干整体统计；头、臂、腿逐条完整肢体链统计。体积采用捕获模型体块实际尺寸，平面体块每条边至少按 1 像素计。手术台以各原始体块保存的有效体积测量。

所有特性统一先按供体和载体定义绑定实际保留的体块，再检查装配位置、覆盖率与生效条件。组合体的特性集合是其中所有保留体块绑定特性的并集，生效状态统一按整个组合体的装配规则判定。手术台先取命中体块所在的连通组合体，命中其中任意体块都显示该组合体的同一份特性并集；纸箱与实体运行时共用同一解析结果。提示层不重新选择载体、不按命中单块过滤，也不随能力生效而切换归属。组合体没有某项特性的载体时不显示该特性；保留载体但缺少配套部件或未装在正确位置时显示灰色未生效提示。拆开、移除或黏合部件后，按新的组合体重新计算。

目标、效果和子特性分别保留实际载体提供的来源信息与生效结果，展示信息不会赋予运行时能力。同一特性内生效与未生效的子项分条显示；未生效原因或具体缺件方案不同的子项也分条显示，状态和原因相同的子项合并。每条最多显示一个失效原因：同一子项的重复来源和不同肢链先按原规则汇总，位置／覆盖率判定与后续条件检查沿用原有优先级，后续阶段不覆盖先前已经命中的失效原因。每条列表按 ID 排序、去重，最多列出三项，其余用省略号表示。没有可列内容时只显示基础名称，不生成空括号；有载体但未生效的数值特性仍显示名称和原因，不把供体原始数值作为成品数值。性情、智力继续显示运行时分类和默认值；强度百分比仅用于实际生效的能力，可挤奶的生效产出仍按当前配套计算。

缺少配套时显示“缺少对应部件（具体名称）”，例如“缺少对应部件（羊身体）”。同组必须补齐的多个部件用顿号列出；不同可行配套用“或”连接。缺件信息由特性解析结果提供，手术台、纸箱提示与诊断报告共用，同一头部链及同种供体的配套限制继续参与判定。

手术台按连通组合体缓存装配预览、几何测量与特性结果，组合体内每个保留体块均索引到同一份缓存。在同一组合体内移动准星，或切回同一台面上已计算过的另一组合体，直接复用结果。台面编辑版本、特性或解剖数据版本变化时失效；资源重载及退出世界时清空。首次查看组合体仍需完成一次完整计算。

组合能力的全部参与部件共同绑定特性，获取判定另行检查配套是否齐全：可剪毛绑定羊身子和羊毛，潜影贝缩壳防御只绑定上壳，犰狳缩壳防御与脱落鳞甲绑定身体和外壳，音波轰击绑定身体和左右胸腔，振动感知绑定头部主体和触须，吼叫绑定头部主体和嘴部。共享同一模型标签的附属器官不能兼任必需的身体或头部主体，这由通用载体排除规则处理。可挤奶绑定全身任意位置保留的乳房和身体；生效时按组合体实际可获取的产物列出“奶桶”“蘑菇煲”，子项与性情、智力等共用通用子特性显示流程。产出只统计对应器官，支撑身体不增加羊毛等产物数量。羊毛等模型部件的渲染显隐继续由原有状态控制。

未通过颈关节安装头部时，所有头部特性的判定范围回退为整个躯干，覆盖率与配套部件按同一个躯干组计算，不包含已安装的臂、腿；供体载体定义与各项门槛保持不变。安装头部后只按该头部判定，不再使用躯干。特性提示的头部分组在回退时显示“头部（未安装 按躯干计）”，括号部分为深灰字。

性情、智力始终作为独立条目显示，与全身、头部等分组标题同级，保留原有文字颜色，不归入头部分组。显示顺序为性情、智力、全身、头部、躯干、手臂、腿部，空的部位分组省略。无有效载体、装配位置不符或原始头部保留比例不足时分别回退为中立、简单，并在数值后用深灰字标注“（默认）”，不显示部位占比不足等失效提示。有效来源提供的中立性情或简单智力不标注默认。

性情、智力的继承类型为特殊型，采用安装在头部（或未安装头部时的躯干内）的供体原始头部保留比例，同一来源碎片按体块去重合并。JEI 使用独立的星芒图标，悬停只显示“载体规则：特殊型”，原始头部保留条件放在效果描述末尾。呼吸载体优先使用鳃映射，供体采用头部映射时使用完整头部。有效腿按达标髋关节链计数；有效翼按达标肩关节链计数。扑翼飞行还采用现有承重比 16。

### 3. 生效类型与数值

JEI 上方规则图标的悬停提示先显示“载体规则：类型”，下一行分别解释存在型“有效部位内保留匹配载体即可”、门槛型“覆盖率达到门槛（实际百分比）”、比例型“特性效果随占比变化”；特殊型不显示第二行。门槛比例从服务端实际加载的数据读取，在登录和 `/reload` 后同步；悬停时读取最新值，退出服务器后清空。肢体数量、承重、器官配套及特殊型头部保留条件统一放在效果描述末尾，以“生效条件：”标注。

| 类别 | 判定 | 效果 |
| --- | --- | --- |
| 存在型 `PRESENCE` | 对应范围内保留匹配载体 | 获得完整特性 |
| 门槛型 `COVERAGE_THRESHOLD` | 覆盖率达到 `min_coverage`，默认 0.5，并满足表内附加条件 | 获得固定效果 |
| 比例型 `COVERAGE_SCALED` | c > 0 | 按覆盖率或数值汇总系数执行 |
| 特殊型 `SPECIAL` | 性情、智力按供体原始头部保留比例判定，默认至少 50%；同源碎片合并 | 性情冲突取中立，智力取最高有效等级；无有效来源时分别默认中立、简单 |

天然护甲、击退抗性、自然恢复、击退强化按供体数值从高到低取前 `min_coverage`（默认 50%）体积加权平均，剩余取值体积以 0 补齐。**汇总原始值达到 0.1 时生效，并四舍五入保留一位小数；计算与提示共用该结果。** 例如单一马系自然恢复约 0.0222／秒，对应有效汇总值为 0.0；完整劫掠兽击退抗性贡献 0.75，量化后为 0.8。

效果免疫按每种效果分别累计体积并判定门槛。附效攻击按命中臂上的对应载体执行。腿部特性按有效腿数判定；扑翼飞行按有效翼数与承重判定；游泳专长按躯干覆盖率判定。持续呼吸模式优先采用“无需呼吸”，其次采用水生或空气呼吸模式。

供体覆盖按自动探测、实体标签、精确实体 ID 的顺序应用。规范文件位于 `data/create_biotech/bionic_traits/`；扩展命名空间的同名文件提供 `values` 覆盖。性情和智力分类分别沿用 `bionic_head/disposition/`、`bionic_head/intelligence/`。数值项的 `min_coverage` 表示取值体积比例，门槛型表示获取比例，特殊型表示供体原始头部保留比例，存在型和普通比例型按各自规则计算。

### 4. 特性覆盖配置

特性之间的覆盖统一写在**覆盖方**的 `data/create_biotech/bionic_traits/<特性 ID>.json` 顶层 `overrides` 数组中。例如 `fire_immune.json`：

```json
{
  "automatic_detection": true,
  "min_coverage": 0.5,
  "overrides": ["fire_resistance", "sun_sensitive"],
  "values": {}
}
```

数组接受特性短 ID 或 `create_biotech:<特性 ID>`；省略字段或设为 `[]` 表示不覆盖任何特性。修改覆盖关系应使用数据包替换 `create_biotech` 命名空间下覆盖方的同名文件，其他命名空间仍只扩展 `values`。

| 覆盖方 JSON | 默认 `overrides` |
| --- | --- |
| `fire_immune.json` | `fire_resistance`、`sun_sensitive` |
| `freeze_immune.json` | `freeze_resistance` |
| `fall_damage_immune.json` | `agile_landing` |
| `no_breathing.json` | `water_breathing`、`dry_suffocation` |
| `wing_flight.json` | `wing_slow_fall` |
| `wingless_flight.json` | `wing_slow_fall` |
| `tameable.json` | `livestock_tameable` |

解析先完成位置、覆盖率、数量、承重及配套部件判定，再由有效覆盖方移除目标特性；被覆盖项统一不显示、不生效，供体原始事实不变。覆盖方未生效时不会压制目标。覆盖链按强者优先处理：若 A 覆盖 B、B 覆盖 C，且三者原本均生效，则保留 A、C；已被 A 覆盖的 B 不再压制 C。未知 ID、非字符串和自身覆盖会记录警告并忽略；循环中的覆盖边全部忽略，其他规则继续生效。这里配置的是特性结果的替换关系；伤害免疫等能力本身的效果仍由对应特性执行。

覆盖配置在登录和 `/reload` 后同步到客户端，并使特性预览缓存失效；退出服务器清空客户端覆盖配置。手术台、纸箱和实体行为使用同一覆盖判定。翅膀的解剖归属与两类驯服组织的概率贡献在覆盖前保留，不因标签被移除而丢失。

## 二、按作用部位排列的特性总表（`bionic_traits`）

下表每项均列出供体、载体、生效规则、效果与方法依据。全身和腿部的“摔落免疫”保留各自 ID 与获取范围。

| 作用部位 | 特性 ID | 中文名 | 标签 | 供体载体 | 对应生物（来源） | 生效条件／汇总方式 | 效果 | 方法选用与依据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 全身 | `fire_immune` | 火焰免疫 | 被动 | 供体全部保留体块 | 自动：`EntityType.fireImmune()`，即烈焰人、末影龙、恶魂、岩浆怪、潜影贝、炽足兽、恼鬼、监守者、凋灵、凋灵骷髅、僵尸疣猪兽、僵尸猪灵 | c ≥ 0.5 | 获得火焰伤害免疫与灭火效果；提示显示火焰免疫 | 复用 [Entity.fireImmune](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 与火焰伤害标签 |
| 全身 | `fire_resistance` | 火焰抗性 | 被动 | 供体全部保留体块 | 同火焰免疫的供体，独立数据文件 | c > 0 | 火焰类伤害 ×(1−c)；提示在 c < 0.5 时显示 | 复用 [Entity.fireImmune](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 判定供体；沿用现有覆盖率减伤 |
| 全身 | `water_sensitive` | 遇水受伤 | 被动 | 供体全部保留体块 | 自动：`isSensitiveToWater()`，即烈焰人、末影人、炽足兽、雪傀儡 | c ≥ 0.5 | 在水、雨或气泡中每 tick 尝试受到 1 点溺水伤害，沿用原版伤害冷却 | 复用 [LivingEntity.isSensitiveToWater / aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `freeze_immune` | 冻结免疫 | 被动 | 供体全部保留体块 | 自动：标签`freeze_immune_entity_types`，即流浪者、北极熊、雪傀儡、凋灵 | c ≥ 0.5 | 获得冻结与冰冻伤害免疫；提示显示冻结免疫 | 复用 [Entity.canFreeze](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 与 `FREEZE_IMMUNE_ENTITY_TYPES` |
| 全身 | `freeze_resistance` | 冻结抗性 | 被动 | 供体全部保留体块 | 同冻结免疫的供体，独立数据文件 | c > 0 | 冰冻伤害 ×(1−c)；提示在 c < 0.5 时显示 | 复用 `FREEZE_IMMUNE_ENTITY_TYPES`；沿用现有覆盖率减伤，入口见 [LivingEntity.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `freeze_vulnerable` | 冻结易伤 | 被动 | 供体全部保留体块 | 自动：标签`freeze_hurts_extra_types`，即炽足兽、烈焰人、岩浆怪 | c > 0 | 冰冻伤害 ×(1+4c)，与冻结抗性系数相乘 | 复用 `FREEZE_HURTS_EXTRA_TYPES`；按覆盖率映射原版 5 倍冰冻伤害，见 [LivingEntity.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `sun_sensitive` | 阳光灼烧 | 被动 | 供体全部保留体块 | 数据：僵尸、僵尸村民、溺尸、骷髅、流浪者、沼骸、幻翼 | c ≥ 0.5；燃烧满足 `isSunBurnTick()` | 日晒点燃 8 秒；头盔遮挡并消耗耐久；白天避开露天路径，空闲时主动寻找阴影；尸壳、亡灵马等不提供本项 | 日晒复用 [Mob.isSunBurnTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java) 与 [Zombie.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)；避日适配 [RestrictSunGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/RestrictSunGoal.java)、[FleeSunGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/FleeSunGoal.java)，按组合体碰撞箱查找阴影落点 |
| 全身 | `inverted_healing` | 治疗反转 | 被动 | 供体全部保留体块 | 自动：`isInvertedHealAndHarm()`，即 `#undead` | c ≥ 0.5 | 瞬间治疗与瞬间伤害效果互换 | 复用 [LivingEntity.isInvertedHealAndHarm](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 与原版瞬时效果入口 |
| 全身 | `water_sinking` | 水中下沉 | 被动 | 供体全部保留体块 | 自动：`#minecraft:undead` | c ≥ 0.5 | 关闭水中自动上浮和地面导航浮水，身体随重力下沉；保留主动游泳、飞行与熔岩行为 | 停用 [FloatGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/FloatGoal.java) 的水中跳跃与 [GroundPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/GroundPathNavigation.java) 浮水，沿用 [LivingEntity.travel](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 水中重力 |
| 全身 | `moisture_dependent` | 保湿依赖 | 被动 | 供体全部保留体块 | 数据：美西螈、海豚 | c ≥ 0.5；统一采用海豚模板 | 保湿值 2400 tick；水、雨或气泡使其补满，干燥时逐 tick 减少，归零后每 tick 尝试受到 1 点干涸伤害 | 以海豚为模板，复刻 [Dolphin.tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Dolphin.java) 的保湿计时与伤害触发 |
| 全身 | `heat_sensitive` | 高温灼伤 | 被动 | 供体全部保留体块 | 数据：雪傀儡 | c ≥ 0.5；位于 `snow_golem_melts` 群系 | 每 tick 尝试受到 1 点火焰伤害，沿用原版伤害冷却 | 复刻 [SnowGolem.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) 的群系标签判断与伤害调用 |
| 全身 | `fall_damage_immune` | 摔落免疫 | 被动 | 供体全部保留体块 | 数据：岩浆怪、铁傀儡、雪傀儡、潜影贝、旋风人 | c ≥ 0.5 | 获得摔落伤害免疫 | 复用 [LivingEntity.causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的摔落免疫分支 |
| 全身 | `shoulder_riding` | 肩头伴行 | 技能 | 供体全部保留体块 | 数据：鹦鹉 | c ≥ 0.5；已驯服、未坐下；生成或落肩后超过 100 tick；接触符合原版条件的主人 | 骑在主人左右肩膀，每肩一只；保存完整装配、外观和装备；由玩家原版落肩流程恢复实体 | 适配 [LandOnOwnersShoulderGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/LandOnOwnersShoulderGoal.java)、[ShoulderRidingEntity.setEntityOnShoulder](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/ShoulderRidingEntity.java) 和 [Player.setEntityOnShoulder / removeEntitiesOnShoulder](../ref/1.21.1/Minecraft/net/minecraft/world/entity/player/Player.java)；肩上渲染保留拟态装配 |
| 全身 | `web_adapted` | 无视蛛网 | 被动 | 供体全部保留体块 | 数据：蜘蛛、洞穴蜘蛛 | c ≥ 0.5 | 穿过蛛网时保持正常移动速度 | 复刻 [Spider.makeStuckInBlock](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java) 的蛛网分支 |
| 全身 | `projectile_deflection` | 弹射物偏转 | 被动 | 供体全部保留体块 | 自动：标签`deflects_projectiles`，即旋风人 | c ≥ 0.5 | 采用原版偏转策略；普通弹射物反弹，风弹沿用原版独立命中规则 | 复用 [Projectile.hitTargetOrDeflectSelf](../ref/1.21.1/Minecraft/net/minecraft/world/entity/projectile/Projectile.java) 与原版偏转策略 |
| 全身 | `contact_retaliation` | 接触反伤 | 被动 | 守卫者、远古守卫者：全部保留体块；河豚：刺 `SPINE`（`body`／`spikes`／`spine`） | 守卫者、远古守卫者、河豚 | 门槛型，c ≥ 0.5；静止防御状态下受到活体直接攻击 | 向攻击者造成固定 2 点荆棘伤害；按守卫者的荆棘伤害标签判定适用伤害源 | 统一采用守卫者模板，复刻 [Guardian.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java) 的触发条件与固定伤害 |
| 全身 | `immune_effects` | 免疫效果（…） | 被动 | 供体全部保留体块 | 自动：逐个效果调用`canBeAffected` 探测。`#undead` 免疫中毒和生命恢复；蜘蛛、洞穴蜘蛛免疫中毒；凋灵、凋灵骷髅免疫凋零；蠹虫免疫寄生；史莱姆免疫渗浆 | 每种效果各自的全身覆盖率 ≥ 0.5 | 获得各自达标的药水效果免疫，可同时获得多种免疫 | 逐效果复用 [LivingEntity.canBeAffected](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 及供体覆盖方法 |
| 全身 | `natural_armor` | 天然护甲（…） | 被动 | 供体全部保留体块 | 自动读取基础护甲：僵尸、尸壳、溺尸、僵尸村民、僵尸猪灵 2；凋灵 4；岩浆怪为 Size×3；杀手兔 8 | 按数值最高的前 50% 全身体积加权平均；执行数值精度规则 | 结果作为成品护甲基础值 | 复用 `Attributes.ARMOR`；数值见 [Zombie.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)、[WitherBoss.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java)、[MagmaCube.setSize](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/MagmaCube.java)、[Rabbit.setVariant](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Rabbit.java) |
| 全身 | `passive_regeneration` | 自然恢复（…/秒） | 被动 | 供体全部保留体块 | 悦灵原始值 2.0／秒；凋灵原始值 1.0／秒；马系原始值约 0.0222／秒，按数值精度规则汇总 | 按数值最高的前 50% 全身体积加权平均；执行数值精度规则 | 生命值低于上限时恢复；每 10 tick 回复汇总每秒数值的一半 | 复用 `LivingEntity.heal`；数值见 [Allay.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/allay/Allay.java)、[WitherBoss.customServerAiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java)；以悦灵的 10 tick 节奏统一执行 |
| 全身 | `enchant_vulnerability` | 附魔易伤（亡灵杀手／节肢杀手／穿刺） | 被动 | 全部保留体块 | 自动：对应 `sensitive_to_*` 实体标签 | 亡灵杀手、节肢杀手、穿刺子项各自覆盖率 ≥ 0.5 | 对应武器、箭与三叉戟附魔每级增加 2.5 点伤害；节肢杀手直接命中附加缓慢 IV，时间为 30–[30+10×(等级−1)] tick，按附魔效果定义执行 | 复用附魔注册表、`sensitive_to_*` 标签与 [EnchantmentHelper.modifyDamage / doPostAttackEffects](../ref/1.21.1/Minecraft/net/minecraft/world/item/enchantment/EnchantmentHelper.java)；沿用装配子项映射 |
| 全身 | `ender_teleport` | 末影瞬移 | 技能 | 全部保留体块 | 末影人、潜影贝 | 末影人与潜影贝组织合计 c ≥ 0.5；统一采用末影人模板 | 弹射物命中时最多尝试 64 次 ±32 格瞬移；受到环境等伤害后以 90% 概率瞬移；目标远于 16 格时累计约 30 tick 后追近，不受玩家视线影响；清水药水先结算原版伤害 | 以 [EnderMan.hurt / teleport / teleportTowards / EndermanLookForPlayerGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java) 为模板，移除注视判断与近距离注视闪避；复用 `LivingEntity.randomTeleport` 与 NeoForge 瞬移事件 |
| 全身 | `block_attachment` | 方块吸附 | 技能 | 潜影贝底座 `BASE`：`base` | 数据：潜影贝 | 存在型；底座可保留在全身任意位置 | 按原始装配 Y 最低的底座选择唯一吸附基准（同高按来源与体块编号）；以实际底座角点确定底面；在正式装箱、临时装箱或复制时就将整个装配旋转摆正，并重新烘焙碰撞和技能几何，所选底座随纸箱及存档保存；放出后再按邻接方块支撑面旋转吸附；主碰撞箱与分部受击箱先同步旋转实际部件角点再重算包围盒；允许旋转后的身体嵌入方块，实体眼部、F3+B 视线及窒息采样随实际头部位置同步旋转并正常承受窒息伤害；当前依附面失效时，有末影瞬移则立即触发一次 ±32 格随机瞬移尝试，不先原地换面；无末影瞬移或传送失败、取消时解除吸附行动限制，逐 tick 检测底座接触点一格内最近的有效表面并重新吸附，方块吸附本身不提供随机传送；仅实际吸附后禁止 AI、看向目标或骑乘操作改变整体朝向，锁住主动移动、重力位移、推挤和击退，不获得潜影贝的实体方块碰撞；状态随存档和纸箱保存，重新放出后按新位置寻找支撑面 | 参照 [Shulker.canStayAt / findAttachableSurface](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java) 的六面支撑检查及 [ShulkerRenderer.setupRotations](../ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/ShulkerRenderer.java) 的朝向；由 `BionicBlockAttachment` 统一提供模型、碰撞和技能起点的旋转 |
| 全身 | `magic_resistance` | 魔法抗性 | 被动 | 全部保留体块 | 女巫 | c > 0 | `witch_resistant_to` 伤害 ×(1−0.85c) | 复刻 [Witch.getDamageAfterMagicAbsorb](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Witch.java) 的伤害标签分支；沿用覆盖率系数 |
| 全身 | `wither_shield` | 凋灵护甲 | 被动 | 全部保留体块 | 凋灵 | c ≥ 0.5；生命值 ≤ 50% | 获得凋灵半血护甲对箭、三叉戟和风弹直接伤害的免疫 | 复刻 [WitherBoss.hurt / isPowered](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java) |
| 全身 | `wool_shearing` | 可剪毛 | 技能 | 羊身子 `TORSO`：`body`；身体羊毛 `WOOL`：`body_wool` | 数据：成年绵羊 | 存在型：至少保留一个成年羊身子，可支持全身任意数量的成年身体羊毛部件；身子与羊毛可来自不同供体 | 每块羊毛独立剪下 1–3 个对应供体颜色的羊毛，按颜色分别累加；羊毛按拟态生物的被剪毛状态显隐；吃草后统一恢复；支持机械手剪刀 | 复用 [EatBlockGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/EatBlockGoal.java)；复刻 [Sheep.shear / ate / readyForShearing](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Sheep.java)；羊毛材质来自 [SheepFurLayer](../ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/layers/SheepFurLayer.java) |
| 全身 | `milkable` | 可挤奶（奶桶，蘑菇煲） | 被动 | 通用乳房 `UDDER`：牛、哞菇 `udder`；身体载体 `TORSO`：牛、哞菇、山羊 `body`，乳房不重复计作身体 | 数据：成年牛、哞菇、山羊 | 存在型；全身任意位置的牛或哞菇乳房配任意牛身子可获取奶桶，配任意哞菇身子可获取奶桶和蘑菇煲，无需同时保留牛身子；成年山羊身子仍可挤奶 | 每次交互填充一个对应的手持空桶或碗；蘑菇煲无需额外蘑菇部件；产量不随部件数量增加；生存模式每份消耗一个对应容器；无冷却并支持机械手；提示只列出实际可获取的类型 | 以牛为挤奶模板，复刻 [Cow.mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Cow.java)；蘑菇煲复刻 [MushroomCow.mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/MushroomCow.java)；复用 `ItemUtils.createFilledResult` |
| 躯干 | `wingless_flight` | 悬浮飞行 | 技能 | 供体全部保留体块 | 数据：恶魂、凋灵、烈焰人 | c ≥ 0.5 | 采用飞行移动与寻路、悬浮和摔落免疫；保留烈焰人供体的现有完整飞行效果 | 复用 [FlyingMoveControl](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/control/FlyingMoveControl.java)、[FlyingPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/FlyingPathNavigation.java)；沿用现有悬浮飞行规则 |
| 躯干 | `swim_specialist` | 游泳专长 | 技能 | 供体全部保留体块 | 自动：`WaterAnimal`、`Drowned`，或使用水中／两栖导航、平滑游泳控制的生物；原版供体见反查表 | 供体体块安装在躯干，c ≥ 0.5 | 水中输入 ×1.35；采用平滑游泳控制、两栖寻路与水中漫游，按美西螈中性浮力及 0.9 阻尼移动 | 复用 [SmoothSwimmingMoveControl](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/control/SmoothSwimmingMoveControl.java)、[AmphibiousPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/AmphibiousPathNavigation.java)；统一复刻 [Axolotl.travel](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/axolotl/Axolotl.java) 的游泳物理 |
| 躯干 | `shell_defense` | 缩壳防御 | 技能 | 潜影贝上壳 `SHELL`：`lid`；犰狳外壳 `SHELL` + 躯干 `TORSO`：`outer_shell` + `inner_body` | 潜影贝、犰狳 | 比例型；潜影贝只需在躯干内保留上壳，无需底座；犰狳仍需在躯干内同时保留外壳与身子；减伤率为有效缩壳部件体积除以全身保留体块体积总和 c，重叠部分不去重 | 仅受到有效伤害时刷新缩壳计时，默认 80 tick；按 ROLLING／SCARED／UNROLLING 状态推进，禁止移动但允许攻击；卷缩伤害乘以 1−c | 参照 [Armadillo](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java) 与 [ArmadilloAi.ArmadilloBallUp](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/ArmadilloAi.java) 的卷缩动画状态；由 [SlimeBionicEntity.tickShellDefense](../src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicEntity.java) 维护受伤计时、卷缩条件与状态 |
| 躯干 | `knockback_resistance` | 击退抗性（…%） | 被动 | 供体全部保留体块 | 自动：击退抗性基础值，即铁傀儡 1.0、监守者 1.0、劫掠兽 0.75、疣猪兽 0.6、僵尸疣猪兽 0.6 | 按数值最高的前 50% 躯干体积加权平均；执行数值精度规则 | 成品击退抗性 = min(1.0，0.15+汇总值)；供体汇总值保留一位小数，基础击退抗性沿用 0.15 | 复用 `Attributes.KNOCKBACK_RESISTANCE` 与 [LivingEntity.knockback](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 躯干 | `rideable` | 可骑乘 | 技能 | 可装鞍供体的背部体块：原版仅 `body`；模组按名称正则识别，无匹配时回退到点击部件 | 自动：`Saddleable`，即马、驴、骡、骷髅马、僵尸马、骆驼、羊驼、行商羊驼、猪、炽足兽 | 存在型：有效躯干范围内保留任一载体即可，无覆盖率和最小体积门槛 | 仅提供此特性的背部体块可装鞍，空手点击对应体块骑乘，沿用直接操控。供体有背部时头、嘴、腿等其他部件不提供此特性。骆驼背部保留前后双座和冲刺，强健跳跃支持蓄力跳；飞行、游泳按视线升降，跳跃键上升；驯服型由主人骑乘 | 复用 [Saddleable](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Saddleable.java)、[LivingEntity.tickRidden](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；复刻 [AbstractHorse.getRiddenInput / executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java)、[Camel.executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/camel/Camel.java)；复刻 [Camel.getPassengerAttachmentPoint](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/camel/Camel.java) 的前后座次，按所点体块记录座位 |
| 躯干 | `cargo_chest` | 驮运箱 | 被动 | 躯干 `TORSO`：驴、骡、羊驼、行商羊驼仅 `body` | 自动：`AbstractChestedHorse`，即驴、骡、羊驼、行商羊驼 | 身子安装在躯干，c ≥ 0.5 | 右击对应身子装箱，潜行空手点击该身子开箱；各箱容量相加，分别存物；驴、骡每箱 15 格，羊驼每箱 Strength×3 格；左右箱模型随该身子渲染；潜行持斧拆卸对应空箱，死亡掉落箱子与内容，驯服型由主人操作 | 复用 `SimpleContainer`；复刻 [AbstractChestedHorse.equipChest / setChest / 存档与掉落](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java)、[AbstractHorse.createInventory](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java) 与 [HorseInventoryMenu](../ref/1.21.1/Minecraft/net/minecraft/world/inventory/HorseInventoryMenu.java) 的槽位逻辑；模型见补充 |
| 躯干 | `sonic_boom` | 音波冲击 | 技能 | 躯干 `TORSO` + 左右胸腔：监守者 `body` + `left_ribcage` + `right_ribcage` | 数据：监守者 | 存在型；身子和左右胸腔均安装在躯干；目标相对载体质心处于水平 15 格、垂直 20 格内 | 蓄力 34 tick，动作 60 tick，结束后冷却 40 tick；命中造成 10 点音波伤害；成功伤害后沿射线按水平 2.5、纵向 0.5 及击退抗性系数推开目标；近战攻击尝试后设置 40 tick 冷却 | 参照 [SonicBoom](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/behavior/warden/SonicBoom.java) 的参数与伤害规则；由 [BionicSpecialAttacks](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java) 调度目标、动作与冷却，复用音波伤害源 |
| 躯干 | `self_destruct` | 自爆 | 技能 | 供体全部保留体块 | 数据：苦力怕 | c ≥ 0.5；自主索敌型或主人点燃 | 目标进入 3 格时停止寻路并膨胀；目标保持 7 格内且可见时继续，否则回缩；30 tick 后爆炸，威力 3，闪电充能后为 6；点燃后持续倒计时，爆炸后消亡并生成现有药效云 | 复刻 [SwellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/SwellGoal.java) 和 [Creeper.tick / explodeCreeper / spawnLingeringCloud](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Creeper.java)；沿用主人点燃规则 |
| 躯干 | `egg_laying` | 产蛋 | 被动 | 躯干 `TORSO`：`body` | 数据：成年鸡 | 存在型；成年鸡 `body` 安装在躯干；不区分供体捕获前的鸡骑士状态 | 每轮 6000–11999 tick 的生产周期，每个有效鸡身子各产 1 个蛋；倒计时随实体保存 | 参照 [Chicken.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Chicken.java) 的生产间隔，由 [BionicProduction](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicProduction.java) 维护成品的生产周期与存档计时 |
| 躯干 | `scute_shedding` | 脱落鳞甲 | 被动 | 壳 `SHELL` + 躯干 `TORSO`：犰狳 `outer_shell` + `inner_body` | 数据：成年犰狳 | 存在型；躯干内至少一个成年犰狳身子支持多个成年犰狳外壳，不要求逐一配对 | 每轮 6000–11999 tick 的生产周期，每个有效外壳各脱落 1 个鳞甲；一次刷取从每个有效外壳各获得 1 个，消耗 16 耐久；倒计时随实体保存，脱落不隐藏外壳 | 复刻 [Armadillo.customServerAiStep / pickNextScuteDropTime / brushOffScute / mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java) |
| 躯干 | `pollination` | 授粉催熟 | 技能 | 蜜蜂身体 `TORSO`：`body` | 数据：蜜蜂 | 存在型；躯干内保留蜜蜂身体即可，无需腿部 | 寻找 5 格内有效花，成功采粉超过 400 tick 后携粉；每个有效蜜蜂身体独立随机尝试促生脚下 1–2 格作物；每次携粉共用一个促生计数，总额度为 10×有效身体数 | 参照 [Bee.BeePollinateGoal / BeeGrowCropGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Bee.java)，由 [BionicProduction](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicProduction.java) 执行采粉 Goal、促生尝试与共享计数 |
| 头 | `disposition` | 性情（友好／中立／敌对） | 技能 | 头 `HEAD`，包括捕获头部 | 自动：`NeutralMob` 优先为中立，`Enemy` 为敌对，其余友好；`bionic_head/disposition/` 可覆盖 | 保留供体原始头部体积 ≥ 50%，同源碎片合并 | 友好不主动选取玩家或因受伤选敌；中立受伤后反击；敌对主动选取玩家并反击。性情只影响自然选敌，已指定目标由战斗 AI 执行；捕猎、呼援及主人协助由各自能力决定。混合性情取中立；提示与索敌共用结果 | 复用 `NeutralMob`、`Enemy` 分类与 [BionicDispositionRegistry](../src/main/java/com/nobodiiiii/createbiotech/entity/ai/BionicDispositionRegistry.java)；选敌沿用原版 `HurtByTargetGoal`、`NearestAttackableTargetGoal`，执行流程参照 [EnderMan](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java) 与 [MeleeAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java) |
| 头 | `intelligence` | 智力（简单／普通／高级） | 技能 | 头 `HEAD`，包括捕获头部 | 自动：`AbstractVillager`、`AbstractPiglin`、`Raider` 及其模组子类高级，其余普通；数据包可覆盖，供体简单智力由数据包指定 | 保留供体原始头部体积 ≥ 50%，同源碎片合并；无有效来源时显示默认值 | 简单、普通、高级取最高有效等级；默认简单；按等级使用现有攻击选择策略 | 复用 [BionicIntelligenceRegistry](../src/main/java/com/nobodiiiii/createbiotech/entity/ai/BionicIntelligenceRegistry.java) 的现有分类与智力策略 |
| 头 | `no_breathing` | 无需呼吸 | 被动 | 头 `HEAD` | 数据：`#minecraft:undead` | 头部载体覆盖率 ≥ 0.5 | 获得水下与陆地持续呼吸；呼吸提示优先显示本项 | 复用 `UNDEAD` 供体标签与 [LivingEntity.baseTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的呼吸流程 |
| 头 | `water_breathing` | 水下呼吸 | 被动 | 鳃`GILL`：鳕鱼、鲑鱼为 `head`；热带鱼、河豚、鱿鱼、发光鱿鱼、蝌蚪为 `body`；美西螈为 `topGills` / `leftGills` / `rightGills` / `head`；守卫者、远古守卫者、海龟为 `head`；海豚使用头 `HEAD` | 自动读取水下呼吸标签、NeoForge 水中溺水判定和长氧气供体：美西螈、青蛙、守卫者、远古守卫者、海龟、鱿鱼、发光鱿鱼、鳕鱼、鲑鱼、河豚、热带鱼、蝌蚪、海豚 | 头部安装范围内有对应载体 | 获得完整水下呼吸；海豚供体沿用现有完整水下呼吸转换 | 复用 `CAN_BREATHE_UNDER_WATER`、NeoForge `canDrownInFluidType` 与 [LivingEntity.getMaxAirSupply](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；沿用现有头部能力转换 |
| 头 | `dry_suffocation` | 离水窒息（负面） | 被动 | 鳃`GILL`：鳕鱼、鲑鱼为 `head`；其余为 `body` | 数据：鳕鱼、鲑鱼、热带鱼、河豚、鱿鱼、发光鱿鱼、蝌蚪 | 头部载体覆盖率 ≥ 0.5；采用水生呼吸模式 | 离水时按鱼类氧气计数：初始 300 tick，计数至 -20 时受到 2 点溺水伤害并重置为 0；水或气泡使氧气补满 | 统一采用鱼类模板，复刻 [WaterAnimal.handleAirSupply](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/WaterAnimal.java) 的 300 tick 初始氧气与 -20 tick 伤害周期 |
| 头 | `tameable` | 可驯服（宠物） | 技能 | 头`HEAD`：`head`、`head_cube` | 自动：`TamableAnimal`（狼、猫、鹦鹉）；数据补充：狐狸、豹猫、悦灵 | 头部安装范围内有对应载体 | 喂史莱姆球获得主人；主人无特殊交互时右键切换坐下，超过 10 格开始跟随、近于 2 格停止，远距离按跟随 Goal 尝试回到主人身边；协助主人战斗；覆盖家畜驯服，后者不显示也不生效；分裂史莱姆继承主人和宠物行为 | 沿用原 `tameable` ID；复刻 [FollowOwnerGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java)、[SitWhenOrderedToGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java) 与 [TamableAnimal.tryToTeleportToOwner](../ref/1.21.1/Minecraft/net/minecraft/world/entity/TamableAnimal.java) 的行为流程 |
| 头 | `livestock_tameable` | 可驯服（家畜） | 技能 | 头`HEAD`：`head`、`head_cube` | 自动：`AbstractHorse`（马、驴、骡、羊驼、行商羊驼；排除骷髅马、僵尸马、骆驼） | 头部安装范围内有对应载体，且无生效的宠物驯服 | 喂史莱姆球获得主人，保留同主识别、治疗和主人专属的骑乘／驮运操作；保留游走、生产与自身防御，不跟随或传送至主人，不接受坐下指令、不协助主人战斗，也不响应同主同伴呼援；分裂史莱姆继承主人和家畜类别 | 仅拆分已有供体；骑乘、驮运及生产仍须各自特性生效。马类原生游走参照 [AbstractHorse.registerGoals](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java) |
| 头 | `vibration_sense` | 振动感知 | 技能 | 头部主体 `HEAD_CORE` + 触须 `TENDRIL`：监守者 `head` + `left_tendril`／`right_tendril` | 监守者 | 存在型；同一头部链内保留监守者头部主体和至少一根触须；正在追踪目标 | 监听 12 格内已有目标的游戏振动，按潜行与羊毛隔振规则接收；追踪最近声源位置并保留 40 tick 记忆 | 复用 [VibrationSystem.Listener / Ticker / User](../ref/1.21.1/Minecraft/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java) 与现有 [目标跟踪接入](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicVibrationSense.java) |
| 头 | `water_aversion` | 避水偏好 | 技能 | 头 `HEAD`：`head`、`head_cube` | 末影人 | 头部安装范围内有对应载体 | 水域节点代价设为 -1.0，按末影人原版避水规则选择路线 | 复用 [Mob.setPathfindingMalus](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)；水域代价取 [EnderMan 构造器](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java) |
| 头 | `deterrence` | 生物威慑 | 技能 | 头`HEAD`：`head`、`head_cube` | 猫、豹猫、狼、北极熊、羊驼、行商羊驼、守卫者、远古守卫者、犰狳；村民/行商害怕的敌对生物、僵尸猪灵（见下方关系表） | 头部安装范围内有对应载体 | 按现有威慑关系适配目标生物的避让 Goal／Brain；距离、速度、概率和记忆时长采用关系表模板 | 复用 [AvoidEntityGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java)；Brain 生物沿用 [VillagerHostilesSensor](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/sensing/VillagerHostilesSensor.java)、[PiglinAi.avoidZombified](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinAi.java)；按关系表适配威胁身份 |
| 头 | `hunting_instinct` | 捕猎本能（目标生物…） | 技能 | 头 `HEAD`，包括捕获头部 | 见本表“捕猎关系” | 头部安装范围内有对应载体；目标满足捕猎关系表 | 按供体目标谓词、距离、亮度、驯服状态和冷却主动捕猎；沿用主人与盟友关系 | 复用原版目标 Goal、[AxolotlAttackablesSensor](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/sensing/AxolotlAttackablesSensor.java) 与 [PiglinAi](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinAi.java) 的目标谓词；复刻供体约束，见捕猎关系表 |
| 头 | `pack_alert` | 呼唤同伴 | 技能 | 头 `HEAD` | 狼、蜜蜂、海豚、烈焰人、末影螨、蠹虫、僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、潜影贝、唤魔者、幻术师、掠夺者、劫掠兽、恼鬼、卫道士、猪灵、疣猪兽 | 头部安装范围内有对应载体；进入原版受伤报复流程；统一采用狼模板 | 按原版伤害时间戳触发，通知跟随范围与纵向 ±10 格内符合关系的同伴，沿用原方法的目标与盟友判定；同伴身份映射到供体与主人关系 | 统一采用狼的受伤呼援模板，复用 `HurtByTargetGoal.setAlertOthers / alertOther`；供体入口见 [Wolf.registerGoals](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Wolf.java)；同伴身份沿用供体与主人关系 |
| 头 | `roar` | 咆哮 | 技能 | 头部主体 `HEAD_CORE` + 嘴 `MOUTH`：劫掠兽 `head_core` + `mouth` | 数据：劫掠兽 | 存在型；同一头部链内保留劫掠兽头部主体和嘴；角不提供此能力 | 50% 概率眩晕 40 tick，随后进入 20 tick 咆哮阶段，在剩余 10 tick 时结算；4 格范围内按劫掠兽的伤害与击退筛选造成 6 点伤害和强力击退，另一分支直接强力推开格挡者 | 复刻 [Ravager.blockedByShield / aiStep / roar / strongKnockback](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ravager.java) |
| 头 | `head_projectile` | 头部投射 | 技能 | 头`HEAD`：各供体的头部件，见子表 4 | 数据：羊驼、行商羊驼、雪傀儡、烈焰人、恶魂、潜影贝、凋灵、末影龙、旋风人 | 头部安装范围内有对应载体；按智力选择现有投射子项 | 从头部载体质心发射对应原版弹射物，目标选择、预热、射速、连发、散布和冷却采用投射子表；沿用主人、盟友、爆炸与引火规则；龙息云可装瓶 | 复用 [RangedAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/RangedAttackGoal.java) 与原版弹射物实体；按投射子表复刻供体发射方法 |
| 头 | `guardian_beam` | 守卫者光束 | 技能 | 眼`EYE`：守卫者、远古守卫者的 `eye` | 数据：守卫者、远古守卫者 | 头部安装范围内有眼载体；目标可见，普通守卫者距离 >3 格 | 停止寻路并注视目标，预热 10 tick，普通照射 80 tick、远古 60 tick；失去视线时清除攻击目标；结算基础魔法伤害 1（困难 +2、远古 +2）及原版近战伤害 6／8 | 复刻 [Guardian.GuardianAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java) 与 [ElderGuardian.getAttackDuration](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/ElderGuardian.java) |
| 臂 | `wing_flight` | 扑翼飞行 | 技能 | 左翅`LEFT_WING` 或右翅 `RIGHT_WING`：`leftWing` / `left_wing` / `leftWingBase` / `leftWingTip` 等及其右侧对应部件 | 蜜蜂、鹦鹉、蝙蝠、幻翼、悦灵、恼鬼、末影龙 | 翅膀安装为臂；每臂 c ≥ 0.5；有效翼 ≥2；身体体积 ≤16×有效翼载体体积 | 单臂翼覆盖率达标后作为翅膀；飞行按有效翼数与承重判定，播放供体飞行动画并提供摔落免疫；臂腿行走由装配关节动作驱动，攻击臂集合由普通手臂组成 | 复用飞行控制器、导航与供体 `setupAnim`；模型依据见 [BeeModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/BeeModel.java)、[BatModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/BatModel.java)；装配动作见补充 |
| 臂 | `effect_attack` | 附效攻击（中毒／凋零／饥饿） | 被动 | 中毒：口器 `MOUTH` 或刺 `SPINE`；凋零、饥饿：攻击手 `ATTACK_HAND` | 洞穴蜘蛛、蜜蜂、河豚：中毒；凋灵骷髅：凋零；尸壳：饥饿 | 对应口器、刺或攻击手装为臂，并由该臂命中；饥饿采用空手命中 | 中毒统一采用蜜蜂模板：普通 200 tick、困难 360 tick；凋零 I 200 tick；饥饿 I 140×局部有效难度整数 tick；每效果保留现有概率、等级与载体参数 | 复用 `LivingEntity.addEffect`；中毒统一取 [Bee.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Bee.java)，凋零取 [WitherSkeleton.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/WitherSkeleton.java)，饥饿取 [Husk.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Husk.java) |
| 臂 | `attack_knockback` | 击退强化（数值） | 被动 | 全部保留体块 | 自动读取基础攻击击退：疣猪兽、僵尸疣猪兽 1.0；劫掠兽、监守者 1.5 | 每条臂按数值最高的前 50% 体积加权平均；执行数值精度规则 | 使用出手臂的值；原版近战额外击退强度为值×0.5；提示显示最高臂值 | 复用 `Attributes.ATTACK_KNOCKBACK` 与 [Mob.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java) 的额外击退流程 |
| 臂 | `door_handling` | 破门 | 技能 | 对应供体全部保留体块，装配范围 `ARM` | 僵尸、尸壳、僵尸村民、僵尸猪灵 | 对应供体体块安装为臂，载体存在；困难难度且采用地面寻路 | 对路径中的关闭木门持续破坏 240 tick，采用原版破门进度与事件流程 | 直接复用 [BreakDoorGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java) 与 [DoorInteractGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java)；载体取安装为手臂的供体体块 |
| 臂 | `wing_slow_fall` | 扑翼缓降 | 技能 | 左右翅 `LEFT_WING` / `RIGHT_WING` | 鸡、鹦鹉 | 翅膀安装为臂；每臂 c ≥ 0.5；有效翼 ≥2 | 下降速度每 tick ×0.6并获得摔落免疫；与扑翼飞行共用供体振翅动作，绕翅膀与连接部件的实际交线旋转；扑翼或悬浮飞行生效时，缓降不显示且不生效 | 以鸡为模板，复刻 [Chicken.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Chicken.java) 的下降 ×0.6；复用供体翅膀动画 |
| 臂 | `launch_strike` | 近战击飞 | 被动 | 铁傀儡攻击手、末影龙翼、山羊／成年疣猪兽／成年僵尸疣猪兽的角 `HORN` | 铁傀儡、末影龙、山羊、成年疣猪兽、成年僵尸疣猪兽 | 对应载体装为臂，并由该臂成功命中；统一采用铁傀儡模板 | 目标纵向速度增加 0.4×max(0，1−击退抗性)，每次命中执行一次 | 统一采用铁傀儡模板，复刻 [IronGolem.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/IronGolem.java) 中命中成功后的纵向击飞段 |
| 臂 | `loot_pickup` | 拾取装备 | 技能 | 攻击手`ATTACK_HAND`：`right_arm`、`left_arm` | 数据：僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、骷髅、流浪者、沼骸、凋灵骷髅、猪灵 | 对应攻击手载体装为臂；处于可拾取状态 | 按 `Mob.aiStep` 的拾取时机检查邻近掉落物，复用原版装备优劣判定，将更好的近战武器交给对应攻击臂；主人可潜行右击交接武器 | 复用 [Mob.aiStep / pickUpItem / equipItemIfPossible / canReplaceCurrentItem](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)；沿用攻击臂接收武器与主人交接 |
| 臂 | `spellcasting` | 施法 | 技能 | 攻击手`ATTACK_HAND`：唤魔者的 `arms`、`right_arm`、`left_arm`；女巫的 `arms` | 数据：唤魔者、女巫 | 对应攻击手载体装为臂；按智力选择现有施法子项 | 尖牙：预热 20 tick、施法姿态 40 tick、从施法开始计 100 tick 再次可用，近处双环、远处 16 个；药水：10 格射程、60 tick 间隔，按女巫方法选择药水与弹道 | 复用 `RangedAttackGoal`、`ThrownPotion`、`EvokerFangs`；复刻 [SpellcasterUseSpellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/SpellcasterIllager.java)、[EvokerAttackSpellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Evoker.java) 和 [Witch.performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Witch.java) |
| 腿 | `agile_landing` | 摔落免疫 | 被动 | 腿`LEG`：四肢部件名（`leftHindLeg` / `rightFrontLeg` 等） | 自动：猫、豹猫（且属于`fall_damage_immune`） | 每条腿 c ≥ 0.5；有效腿 ≥2 | 获得摔落伤害免疫；与全身摔落免疫共用中文名，提示按生效部位归组 | 复用 `FALL_DAMAGE_IMMUNE` 标签与 [LivingEntity.causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；沿用有效腿数判定 |
| 腿 | `fall_reduction` | 摔落抵抗 | 被动 | 腿`LEG`：四肢部件名 | 马、驴、骡、羊驼、行商羊驼、山羊 | 每条腿 c ≥ 0.5；有效腿 ≥1；统一采用马模板 | 安全摔落距离 6 格，摔落伤害倍率 0.5；保留现有参数配置入口 | 以马为模板，复用 [AbstractHorse.createBaseHorseAttributes / causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java) 的 `SAFE_FALL_DISTANCE=6` 与倍率 0.5 |
| 腿 | `powder_snow_walk` | 细雪行者 | 被动 | 足`FOOT`：兔、狐狸为四肢部件名；蠹虫、末影螨为 `body` / `segment0` / `segment_0` | 自动：标签`powder_snow_walkable_mobs`，即兔、狐狸、蠹虫、末影螨 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 在细雪表面行走 | 复用 [PowderSnowBlock.canEntityWalkOnPowderSnow](../ref/1.21.1/Minecraft/net/minecraft/world/level/block/PowderSnowBlock.java) 与现有足部接入 |
| 腿 | `lava_walk` | 熔岩行者 | 技能 | 足`FOOT`：`leftLeg` / `rightLeg` / `left_leg` / `right_leg` | 炽足兽 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 采用炽足兽的熔岩节点、半格表面支撑、浸没上浮与熔岩内摔落距离重置 | 复用地面导航；复刻 [Strider.floatStrider / canStandOnFluid / StriderPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Strider.java) |
| 腿 | `wall_climb` | 攀墙 | 技能 | 腿`LEG`：蜘蛛的 8 条腿部件 | 蜘蛛、洞穴蜘蛛 | 每条腿 c ≥ 0.5；有效腿 ≥2 | 服务端同步碰墙状态，进入原版攀爬移动；地面路径失败或结束后继续朝目标移动，使追击与跟随能贴墙攀爬 | 复刻 [Spider.tick / onClimbable / setClimbing](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java) 和 [WallClimberNavigation.moveTo / tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/WallClimberNavigation.java)；复用 [LivingEntity.handleRelativeFrictionAndCalculateMovement](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的攀爬物理 |
| 腿 | `strong_jump` | 强健跳跃 | 技能 | 旋风人全身任意保留体块；其他供体为腿 `LEG`，兔为后腿和后足 | 马、骷髅马、僵尸马、兔、山羊、青蛙、旋风人 | 每条腿 c ≥ 0.5；有效腿 ≥2 | 沿用现有起跳速度 +0.2、两格高台寻路与普通迈步高度；骑乘时蓄力倍率 0.4–1.0 | 复用 [LivingEntity.jumpFromGround](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)、[AbstractHorse.onPlayerJump / executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java)；沿用现有 +0.2 与两格寻路参数 |
| 腿 | `snow_trail` | 积雪足迹 | 被动 | 足`FOOT`：雪傀儡的 `lower_body` | 数据：雪傀儡 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 在脚下四角可放置位置留下雪片；沿用现有全气候积雪规则与生物破坏事件 | 复刻 [SnowGolem.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) 的四点放雪循环；沿用现有气候参数 |

## 三、技能与交互补充

### 1. 瞬移、缩壳与动作

末影瞬移以末影人流程为模板：随机落点先检查实体支撑与水域条件，复用 `LivingEntity.randomTeleport`，通过 NeoForge `EventHooks.onEnderTeleport` 处理目标位置。成功后由服务端在出发点和落点各发送 32 个紫色传送门粒子（`PORTAL`），按对应位置的实体碰撞箱分布；普通落地与方块吸附瞬移共用此效果，失败或事件取消时不播放。粒子种类沿用 [LivingEntity.handleEntityEvent](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的瞬移事件，改为明确发送两端位置，替代 `randomTeleport` 的默认粒子事件。追近在目标距离超过 16 格时累计计数，约 30 tick 后开始尝试，成功后重置计数；不再判断玩家注视或触发近距离注视闪避。作为乘客、坐下或关闭 AI 时停用追近并重置计数。原潜影贝供体与末影人供体共同贡献本项覆盖率。

缩壳防御参照犰狳的卷缩状态，由 `SlimeBionicEntity.tickShellDefense` 维护自身的受伤计时、探头计时与 ROLLING／SCARED／UNROLLING 状态。仅实际受到伤害后刷新计时，活体和环境来源均可触发，不再扫描周围危险；持续时间读取 `shell_defense.json` 的 `duration_ticks`，默认 80 tick（4 秒），再次受伤刷新。液体、拴绳、作为乘客或载有乘客等状态继续参与卷缩条件，空中也可开始卷缩，无需落地或方块吸附支撑。当前流程使用自身计数器，未写入原版 `DANGER_DETECTED_RECENTLY` 记忆或直接运行 `ArmadilloBallUp`。缩壳期间禁止主动移动和跳跃，保留自然下落及攻击能力。减伤率 `c` 为有效缩壳部件体积除以全身所有保留体块的体积总和；分母包含头、躯干、手臂和腿，直接逐块累加，重叠部分不去重，伤害乘以 `1−c` 后再由原版护甲等流程结算，不再扣除固定 1 点伤害；占比 25%／50%／100% 分别减伤 25%／50%／100%。进入 `UNROLLING` 后开始开壳，移动限制与减伤持续到状态回到 `IDLE`。

存在型组合的必要部件作为共享前提，不按供体或部件一一消耗。可挤奶的乳房跨牛、哞菇通用，由身体类型决定产物；其他组合按同种身子支持多个产出部件。可剪毛与可挤奶检查全身保留部件，其余能力沿用各自安装部位；同种供体的部件可共同提供躯干能力；头部能力按每条头部链分别检查，两个不完整的头部不能互补。劫掠兽将头部大体块标记为 `head_core`，鼻部与角不代替头部主体。牛和哞菇的乳房单独标记为 `udder`；犰狳的同部位外壳和身子分别标记为 `outer_shell`、`inner_body`。标记依据原版 [CowModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/CowModel.java) 和 [ArmadilloModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ArmadilloModel.java)。

供体外观随拟态生物自身状态变化：犰狳卷缩和潜影贝开合读取自身缩壳状态；监守者胸腔读取自身音波蓄力状态；蜜蜂携粉纹理读取自身采粉状态。状态变化保留手术体块编号及切除结果，不能凭动画补回已切掉的部件。

犰狳与潜影贝动画由 [SurgicalShellAnimations](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalShellAnimations.java) 为每个装配供体保留独立的原版实体状态。犰狳每 tick 直接调用 `Armadillo.setupAnimationStates()`，包括原版探头事件 `64`、`inStateTicks` 与收壳显隐；潜影贝直接调用 `Shulker.updatePeekAmount()`，保留原版每 tick `0.05` 的开合速度及前后 tick 插值。每帧将真实 `partialTick` 传入原版渲染器，由 [ArmadilloModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ArmadilloModel.java) 与 [ShulkerModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ShulkerModel.java) 计算动画，再绑定到保留体块；不对离散姿势或开合档位另做近似插值。

潜影壳默认使用原版探壳开度 `30`，在 `ROLLING`／`SCARED` 防御状态闭合为 `0`。开合进度始终在 `0`–`0.3` 之间，因此不会触发原版模型在开度超过 `0.5` 时的呼吸起伏；继续调用原版开合与渲染流程，无需额外修改动画公式。

鸡的模型为非层级 `ChickenModel`，采集按字段名得到 `leftWing` / `rightWing`；扑翼缓降载体同时接受这组驼峰名及 `left_wing` / `right_wing`，避免命名不一致导致特性完全缺失。两种能力共用 `SlimeBionicAnimator.resolveTransform` 的翼臂路径，使用 `wingHinge` 从翅膀与连接部件的真实交线中确定转轴，按供体振翅通道选择方向，不使用普通手臂摆动。依据：[ChickenModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ChickenModel.java)、[SurgicalCapturedRenderPlan](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalCapturedRenderPlan.java)、[SlimeBionicAnimator](../src/main/java/com/nobodiiiii/createbiotech/entity/client/SlimeBionicAnimator.java)。

扑翼飞行与扑翼缓降中单臂翼覆盖率达标的翼臂由翅膀集合管理，飞行能力另按数量与承重判定；普通手臂构成攻击臂集合。臂腿行走使用装配关节动作，翅膀播放供体飞行动画。动画优先复用捕获供体的模型 `setupAnim` 结果，按原模型层级传递到保留翅膀部件，关节变换继续使用装配坐标。

飞行速度按有效飞行组织占全部保留组织的体积比例 `r` 计算。扑翼飞行只计入达标翼臂中的翅膀载体，悬浮飞行只计入生效的躯干载体；两种能力同时生效时合并去重。分子和分母均采用捕获时记录的体块体积，切除体块不参与。飞行基础速度属性为 `0.60 × ln(1 + 8r) / ln(9)`，复用腿部体积占比的对数递减收益曲线：占比 25%、50%、100% 时分别约为 0.30、0.44、0.60。自主飞行与骑乘飞行共用运动计算：每 tick 速度为 `旧速度 × 0.9 + 输入方向 × 目标速度 × 0.1`，然后按该速度移动。自主飞行使用归一化的下一寻路点方向，目标速度为飞行属性乘寻路倍率；骑乘使用按朝向转换的玩家输入，目标速度为飞行属性，组合输入长度最多为 1。相同属性、寻路倍率 1 和持续满输入下，两者的稳定总速度一致，单位为格/tick；0.30 对应 20 TPS 时约 6 格/秒。飞行不叠加骆驼疾跑加成或少腿自动跳跃。装配或数据包重载后重新计算；未生效的飞行能力及仅具备扑翼缓降的组织不提供飞行速度。

攀墙采用原版蜘蛛的碰墙状态：服务端在每 tick 移动后更新并同步，`onClimbable()` 读取该状态。攀爬物理由 `LivingEntity` 执行，重置摔落距离，水平速度限于每轴 ±0.15，下降速度不低于 −0.15；持续碰墙或起跳时将纵向速度设为 0.2，后续仍经过原版重力与阻尼。离墙后恢复普通移动和摔落结算；有攀墙特性时与蜘蛛一样以碰墙状态代替梯子判定。

AI 沿用 [WallClimberNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/WallClimberNavigation.java) 的策略：先走地面路径，路径不存在或走完后继续朝记录的目标直行，接触墙面后由攀爬物理带动上升。近战追击可在没有地面路径时启动，跟随主人不会仅因地面路径结束而中断；目标选择、性情和智力仍由原有头部能力决定。该策略不能规划完整墙面路线或倒挂天花板。地面与两栖导航共享此追进机制，保留强健跳跃和熔岩行者的节点规则；实际游泳或飞行时优先使用对应移动方式。停止导航、停止追击或失去特性会清除攀墙目标，单独探测路径不会启动追进。

抵达攀墙目标时与原版相同，只清除导航目标，保留移动控制器尚未落地的 `JUMPING` 输入；主动停止导航时才同时清零该移动指令。近战追击参照 `SpiderAttackGoal`，只要目标仍有效且在活动范围内，就不因路径结束而退出。简单／普通智力在空中贴墙攻击时保留贴墙输入，避免攻击停步导致脱墙下落；落地或离墙后继续遵守原有攻击停步规则。

### 2. 驮运箱与骑乘

驮运箱仅绑定供体躯干载体。驴、骡、羊驼、行商羊驼精确映射为 `body`，头、嘴、耳朵、腿、尾巴和箱子模型部件均不提供此特性；身子被切掉后不回退到其他保留体块。仍按躯干范围计算 50% 覆盖率。扩展供体可通过 `carriers` 指定 `TORSO` 载体。

驮运箱复用原版左右箱模型部件。驴、骡取 [ChestedHorseModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ChestedHorseModel.java) 的 `left_chest`、`right_chest`；羊驼取 [LlamaModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/LlamaModel.java) 的对应部件。每个装备箱子的身子各绑定一组原版左右箱模型，随该身子的装配位置和旋转同步显示；鞍同样逐体块绑定。旧存档中装在无效部位的箱子会在服务端更新时掉落返还，箱内物品一并掉落。

容器采用原版 `SimpleContainer`。装备箱子、调整容量、保留原物品、存档与死亡掉落复刻 `AbstractChestedHorse`／`AbstractHorse` 原方法；菜单按 `HorseInventoryMenu` 的槽位、距离、容器监听与快捷移动逻辑适配成品。每个驴、骡身子的背箱提供 15 格，每个羊驼身子的背箱按该供体 Strength 提供 3–15 格；已安装背箱的容量相加，各箱独立存物。右击对应身子装箱，潜行空手点击该身子开箱，潜行持斧点击该身子拆下空箱；权限沿用主人规则。

每个鞍物品装备到一个背部体块，空手点击已装鞍的体块即从该体块上座。原版可装鞍供体统一只接受模型标签 `body`，依据 [HorseModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/HorseModel.java)、猪继承的 [QuadrupedModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/QuadrupedModel.java)、[CamelModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/CamelModel.java)、[StriderModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/StriderModel.java) 和 [LlamaModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/LlamaModel.java) 的统一躯干命名。非原版供体对部件名最后一段做不区分大小写的正则匹配：`body`、`torso`、`back`、`trunk`、`chest`，可带上/下/主/前/后等英文前后缀及数字；`body/head`、`leftChest`、`body_saddle` 不匹配。原始供体模型完全没有匹配项时才允许点击任意保留体块装鞍；已识别的背部被切掉后不回退到头或腿。可骑乘为存在型特性，载体选择与装鞍规则共用同一实现：只要在有效躯干范围保留一个载体即可获得，头、嘴、腿等非背部不提供此特性；不要求供体组织占比或身体体积达标。

普通背部体块各一座，骆驼背部沿用原版前后双座；各鞍分别记录乘客，首位乘客操控身体。潜行持剪刀点击对应体块卸鞍，该鞍的乘客下座。旧存档中装在无效部件的鞍会让乘客下座并掉落返还。骑乘沿用主人规则，所有供体保持直接操控。通用入口复用 `Saddleable` 与 `LivingEntity` 骑乘流程；马式蓄力跳和骆驼冲刺复刻各自原方法。飞行、游泳使用现有视线与跳跃键升降。

### 3. 头部投射子项

各子项保留现有弹射物种类，具体发射条件、时序与原版依据见下表；自有发射流程由 `BionicSpecialAttacks` 调度。

| 供体／子项 | 行为模板与参数 | 方法依据 |
| --- | --- | --- |
| 羊驼、行商羊驼／唾沫 | `RangedAttackGoal`，20 格、40 tick 间隔；保留原版弹道、散布和吐击状态 | [Llama.registerGoals / performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/Llama.java) |
| 雪傀儡／雪球 | `RangedAttackGoal`，10 格、20 tick 间隔；初速 1.6、散布 12 | [SnowGolem.performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) |
| 烈焰人／小火球 | 供体跟随范围内充能 60 tick，三连发间隔 6 tick，之后冷却 100 tick；近身分支沿用原 Goal | [BlazeAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Blaze.java) |
| 恶魂／大火球 | 64 格内且可见时累积蓄力，10 tick 播放准备音，20 tick 发射后计数设为 -40；视线变化按原分支回退计数 | [GhastShootFireballGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ghast.java) |
| 潜影贝／潜影弹 | 首次等待 20 tick；20 格内发射，后续间隔 `20+random(0..9)×10` tick；弹道复用 `ShulkerBullet` | [ShulkerAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java) |
| 凋灵／凋灵之首 | 统一采用中央头的 `RangedAttackGoal`：20 格、40 tick 间隔，保留原版危险弹概率 | [WitherBoss.customServerAiStep / performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java) |
| 末影龙／龙息火球 | 64 格内可见目标累积至少 5 tick，三维视线方向与目标方向夹角小于 10°时发射，高低差参与判定；每轮发射一枚，结束后冷却 80 tick | 参照 [DragonStrafePlayerPhase.doServerTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase.java)；由 `BionicSpecialAttacks` 维护蓄力、三维朝向检查与冷却 |
| 旋风人／风弹 | 目标相对载体质心的距离大于 2 且小于 16 格并可见；充能 15 tick、动作共 20 tick，结束后冷却 10 tick；发射时检查三维朝向点积 >0.5，散布沿用原版难度公式；原 Brain 射击记忆与站姿不参与判定 | 参照 [breeze.Shoot](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/breeze/Shoot.java) 的朝向与弹道规则；由 `BionicSpecialAttacks` 调度动作与冷却 |

音波冲击由 `BionicSpecialAttacks` 调度，以载体质心为原点检查目标的水平与垂直距离，维护蓄力、动作及冷却计数；伤害与方向击退参照 `SonicBoom`，方向击退在伤害成功后执行。当前流程使用自身调度状态与冷却表，未接入 `SonicBoom` 的 Brain 记忆。守卫者光束由同一调度器适配原 Goal 的停止寻路、视线中断、伤害结算及目标清理流程。

尖牙施法由 `BionicSpecialAttacks` 参照 `SpellcasterUseSpellGoal` 的预热、施法姿态与再次施法时间维护计数。女巫药水按 `performRangedAttack` 顺序选择：8 格外已有目标满足条件时缓慢，生命至少 8 点时中毒，3 格内以 25% 概率虚弱，其余伤害；已有药效判断沿用原方法。投射物、药水和药效云复用原版实体与效果应用入口，主人及盟友关系沿用现有过滤。

### 4. 捕猎关系

捕猎关系保留现有供体与目标。通用目标 Goal 接收现有目标谓词；供体专属 Sensor／Brain 按原条件复刻，目标进入视线、有效攻击范围、环境与冷却后参与原版选取。

| 供体头 | 目标与条件 | 驯服后的规则 | 本地依据 |
| --- | --- | --- | --- |
| 狼 | 羊、兔、狐狸、陆上幼年海龟；骷髅、流浪者、凋灵骷髅、沼骸 | 捕猎骷髅类 | `animal/Wolf.java` 的目标 Goal 与 `PREY_SELECTOR` |
| 猫 | 兔、陆上幼年海龟 | 由主人战斗目标驱动 | `animal/Cat.java` 的目标 Goal |
| 豹猫 | 鸡、陆上幼年海龟 | 保留 | `animal/Ocelot.java` 的目标 Goal |
| 狐狸 | 鸡、兔、陆上幼年海龟、鳕鱼、鲑鱼、热带鱼 | 保留 | `animal/Fox.java` 的陆地／鱼类目标 Goal |
| 北极熊 | 狐狸 | 保留 | `animal/PolarBear.java` 的目标 Goal |
| 美西螈 | 水中 8 格内的溺尸、守卫者、远古守卫者及现有鱼类、鱿鱼、发光鱿鱼、蝌蚪；普通猎物战斗后冷却 2400 tick | 保留 | `ai/sensing/AxolotlAttackablesSensor.java`、`animal/axolotl/AxolotlAi.java` |
| 铁傀儡 | `Enemy` 目标按铁傀儡原版目标谓词选取 | 保留 | `animal/IronGolem.java` |
| 雪傀儡 | `Enemy` 目标 | 保留 | `animal/SnowGolem.java` |
| 守卫者、远古守卫者 | 3 格外的鱿鱼、发光鱿鱼、美西螈 | 保留 | `monster/Guardian.java` 的 `GuardianAttackSelector` |
| 末影人 | 末影螨 | 保留 | `monster/EnderMan.java` |
| 蜘蛛、洞穴蜘蛛 | 亮度低于 0.5 时选择铁傀儡 | 保留 | `monster/Spider.java` 的 `SpiderTargetGoal` |
| 史莱姆、岩浆怪 | 铁傀儡 | 保留 | `monster/Slime.java` |
| 猪灵 | 凋灵骷髅、凋灵；成年疣猪兽；猎杀冷却 30–120 秒 | 保留 | `monster/piglin/PiglinAi.java`、`ai/sensing/PiglinSpecificSensor.java` |
| 猪灵蛮兵 | 凋灵骷髅、凋灵 | 保留 | `monster/piglin/PiglinBruteAi.java`、`ai/sensing/PiglinBruteSpecificSensor.java` |
| 僵尸、尸壳、僵尸村民 | 村民、行商、铁傀儡、陆上幼年海龟 | 保留 | `monster/Zombie.java` |
| 溺尸 | 村民、行商、铁傀儡、美西螈、陆上幼年海龟 | 保留 | `monster/Drowned.java` |
| 骷髅、流浪者、沼骸 | 铁傀儡、陆上幼年海龟 | 保留 | `monster/AbstractSkeleton.java` |
| 凋灵骷髅 | 铁傀儡、陆上幼年海龟、猪灵、猪灵蛮兵 | 保留 | `monster/WitherSkeleton.java` |
| 卫道士、掠夺者、唤魔者、幻术师 | 村民、行商、铁傀儡 | 保留 | `monster/{Vindicator,Pillager,Evoker,Illusioner}.java` |
| 僵尸疣猪兽 | 按 `Zoglin` 原版目标谓词选择可攻击生物 | 保留 | `monster/Zoglin.java` |
| 旋风人 | 铁傀儡 | 保留 | `ai/sensing/BreezeAttackEntitySensor.java` |
| 凋灵 | 按 `WITHER_FRIENDS` 标签选择目标 | 保留 | `boss/wither/WitherBoss.java` |
| 羊驼、行商羊驼 | 10 格内的野生狼 | 保留 | `animal/horse/Llama.java` 的 `LlamaAttackWolfGoal` |

表内相对源码路径的根目录均为 `ref/1.21.1/Minecraft/net/minecraft/world/entity/`。

### 5. 生物威慑关系

保留现有关系，避让行为采用目标生物原有模板。猫与豹猫对幻翼的既有共同关系统一使用猫模板。

| 供体 | 被驱散生物 | 原版行为模板 |
| --- | --- | --- |
| 猫、豹猫 | 苦力怕 | `Creeper` 的 `AvoidEntityGoal`：6 格，速度 1.0／1.2 |
| 猫、豹猫 | 幻翼 | `PhantomSweepAttackGoal`：每 20 tick 检查 16 格内威胁并结束俯冲 |
| 狼 | 骷髅、流浪者、凋灵骷髅、沼骸 | `AbstractSkeleton` 的 `AvoidEntityGoal`：6 格，速度 1.0／1.2 |
| 狼 | 兔（含杀手兔）、狐狸 | 兔采用 `RabbitAvoidEntityGoal` 的 10 格与速度 2.2；狐狸采用 `Fox` 原避让距离和速度 |
| 北极熊 | 狐狸 | `Fox` 的北极熊避让 Goal |
| 羊驼、行商羊驼 | 野生狼 | `WolfAvoidEntityGoal`：24 格，速度 1.5；按 `Strength ≥ random(0..4)` 触发 |
| 守卫者、远古守卫者 | 海豚 | `Dolphin` 的 `AvoidEntityGoal`：8 格，速度 1.0 |
| 犰狳 | 蜘蛛、洞穴蜘蛛 | `Spider` 的犰狳避让 Goal，按原距离与速度执行 |
| 僵尸、尸壳、溺尸、僵尸村民、唤魔者、幻术师、掠夺者、劫掠兽、恼鬼、卫道士、僵尸疣猪兽 | 村民 | `VillagerHostilesSensor` 各物种距离 8–15 格与 `NEAREST_HOSTILE` 记忆 |
| 现有行商威胁供体：僵尸类、僵尸猪灵、唤魔者、幻术师、掠夺者、恼鬼、卫道士、僵尸疣猪兽 | 行商 | 采用 `WanderingTrader` 对应威胁的原避让 Goal 参数；僵尸猪灵沿用僵尸模板 |
| 僵尸猪灵、僵尸疣猪兽 | 猪灵 | `PiglinAi.avoidZombified` 与原版避让记忆、期望距离 |

依据位于同一原版根目录下的 `monster/{Creeper,Phantom,AbstractSkeleton,Spider}.java`、`animal/{Rabbit,Fox,Wolf,Dolphin}.java`、`npc/WanderingTrader.java`、`ai/sensing/VillagerHostilesSensor.java` 与 `monster/piglin/PiglinAi.java`。

### 6. 呼唤同伴与生产

呼唤同伴统一采用狼的受伤呼援，直接复用 `HurtByTargetGoal.setAlertOthers`，按原方法的伤害时间戳、跟随范围、纵向 ±10 格、同伴当前目标与关系条件触发。混合装配的同伴身份按现有供体集合与主人关系映射；坐下状态沿用主人命令。

授粉只绑定蜜蜂身体 `body`，在躯干内保留即可生效，无需腿部或其他配套。每个有效蜜蜂身体独立提供随机促生尝试，每次携粉共用一个促生计数，总额度为 `10×有效身体数`，不逐个身体单独限制 10 次。采粉参照 `BeePollinateGoal` 的有效花检查、5 格搜索、雨天与战斗状态、悬停累计、400 tick 成功时间、结束判定和再次寻花冷却，由 `BionicProduction.pollinationGoal` 执行。促生由 `BionicProduction.growCrops` 根据携粉状态按身体数量进行随机尝试，检查 `BEE_GROWABLES`、作物年龄与生物破坏规则；促生总数达到共享额度后清除携粉状态。

可剪毛为存在型，只需全身任意一个成年羊身子，即可支持所有保留的成年身体羊毛部件。每块羊毛独立产出 1–3 个自身供体颜色的羊毛，按颜色累加；支撑用身子不计入产量。采集时同时保留基础模型及独立毛层模型的部位名称：原版 `sheep_fur.png` 材质的全部毛块保留 `wool` 标签用于被剪毛状态显隐，仅毛层 `body` 体块额外标记 `body_wool` 并映射为产出载体 `WOOL`；头毛和腿毛不绑定可剪毛、不增加产量，也不能替代身体羊毛满足配套条件。羊毛隐藏不会删除载体或改变装配。羊毛显隐读取拟态生物同步的被剪毛状态，复用 `EatBlockGoal` 的吃草动作和 `ate()` 回调恢复羊毛。产蛋与自然脱鳞甲按各自原始随机区间计时，每轮分别按有效鸡身子与犰狳外壳数量产出，并随存档与纸箱捕获保存。牛和哞菇的乳房不区分来源，可配合任意一个牛身子获取奶桶，或任意一个哞菇身子同时支持奶桶与蘑菇煲，无需额外保留牛身子；两种组合均需乳房，乳房和身体均可保留在全身任意位置，且乳房不会同时充当身体。挤奶与蘑菇煲每次交互只填充一个手持容器，不按部件数量增产；蘑菇煲无需额外蘑菇部件。创造模式沿用原版结果物去重规则。完整捕获的原版生物按其原生交互显示类型，完整哞菇仍可提供原版的牛奶与蘑菇煲。供体颜色、羊驼 Strength、装备、物品栏与生产状态继续随供体档案保存。机械手沿用现有挤奶、蘑菇煲与剪毛交互入口。

无主仿生生物具有宠物或家畜任一驯服特性时，使用史莱姆球按头部可驯服载体的体积占比尝试驯服：两类有效载体合并去重，分别除以所在头部链的总体积，多头取最高占比；无独立头部时沿用整个躯干作为头部范围的回退规则。概率只用于服务端判定，不加入界面或提示。每次有效尝试在非创造模式消耗一个史莱姆球，成功建立主人关系并显示爱心，随机失败显示烟雾；两类特性都未生效时不处理该交互，不消耗物品、不显示粒子。只有宠物在驯服后接受坐下／起身指令：主人右键优先处理生产、装备等特殊交互，其余统一切换；史莱姆球与其他无特殊效果的物品及空手遵循相同规则，不消耗物品，每次点击只切换一次。家畜不接受该指令，也不执行主人跟随、追随传送或助战；自身防御和独立的捕猎本能仍按各自规则执行。

宠物驯服生效时，会从统一特性结果中移除家畜驯服，所有装配预览、纸箱提示与运行逻辑都不再显示或执行被覆盖的家畜驯服。多头装配仍只保留一套主人关系。旧存档中的马、驴、骡、羊驼、行商羊驼供体改按家畜判定，保留原主人并清除旧坐下命令；失去两类驯服特性时才清除主人。本次不增加牛、羊等扩展供体。

已驯服的仿生生物、继承主人的史莱姆及原版可驯服生物，沿用瓶装活性史莱姆恢复 4 点生命、桶装恢复 16 点生命。生命低于上限时消耗一份并返还玻璃瓶或桶，创造模式保留物品。死亡碎块、转化出的史莱姆及其后续分裂均保存主人和宠物／家畜类别；宠物史莱姆保留跟随和停留，家畜史莱姆自由游走、不接受停留指令；两者都不会用原版接触伤害误伤玩家。旧版已经生成且未记录类别的史莱姆继续按宠物处理。


## 四、按生物反查

性情、智力按头部通用规则显示。下表汇总 v3 其余特性；列名表示特性的作用范围，供体原始部件标签不改变归列。具体载体、部位与门槛见总表。数值效果执行一位小数与 0.1 生效规则。

| 生物 | 全身 | 躯干 | 头 | 臂 | 腿 |
| --- | --- | --- | --- | --- | --- |
| 美西螈 | 保湿依赖、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、捕猎本能（目标生物…） | — | — |
| 蝙蝠 | — | — | — | 扑翼飞行 | — |
| 蜜蜂 | 附魔易伤（节肢杀手） | 授粉催熟 | 呼唤同伴 | 扑翼飞行、附效攻击（中毒） | — |
| 烈焰人 | 火焰免疫／火焰抗性、遇水受伤、冻结易伤 | 悬浮飞行 | 呼唤同伴、头部投射 | — | — |
| 沼骸 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 旋风人 | 摔落免疫、弹射物偏转 | — | 捕猎本能（目标生物…）、头部投射 | — | 强健跳跃 |
| 骆驼 | — | 可骑乘 | — | — | — |
| 猫 | — | — | 可驯服（宠物）、生物威慑、捕猎本能（目标生物…） | — | 摔落免疫 |
| 洞穴蜘蛛 | 无视蛛网、免疫效果（中毒）、附魔易伤（节肢杀手） | — | 捕猎本能（目标生物…） | 附效攻击（中毒） | 攀墙 |
| 鳕鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 鲑鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 热带鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 海豚 | 保湿依赖、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、呼唤同伴 | — | — |
| 驴 | — | 可骑乘、驮运箱 | 可驯服（家畜） | — | 摔落抵抗 |
| 骡 | — | 可骑乘、驮运箱 | 可驯服（家畜） | — | 摔落抵抗 |
| 马 | — | 可骑乘 | 可驯服（家畜） | — | 摔落抵抗、强健跳跃 |
| 溺尸 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | 游泳专长 | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 拾取装备 | — |
| 尸壳 | 治疗反转、水中下沉、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 附效攻击（饥饿）、破门、拾取装备 | — |
| 僵尸 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 破门、拾取装备 | — |
| 僵尸村民 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 破门、拾取装备 | — |
| 远古守卫者 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、生物威慑、捕猎本能（目标生物…）、守卫者光束 | — | — |
| 守卫者 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、生物威慑、捕猎本能（目标生物…）、守卫者光束 | — | — |
| 末影龙 | 火焰免疫／火焰抗性 | — | 头部投射 | 扑翼飞行、近战击飞 | — |
| 末影人 | 遇水受伤、末影瞬移 | — | 避水偏好、捕猎本能（目标生物…） | — | — |
| 末影螨 | 附魔易伤（节肢杀手） | — | 呼唤同伴 | — | 细雪行者 |
| 蠹虫 | 免疫效果（寄生）、附魔易伤（节肢杀手） | — | 呼唤同伴 | — | 细雪行者 |
| 狐狸 | — | — | 可驯服（宠物）、捕猎本能（目标生物…） | — | 细雪行者 |
| 青蛙 | — | 游泳专长 | 水下呼吸 | — | 强健跳跃 |
| 恶魂 | 火焰免疫／火焰抗性 | 悬浮飞行 | 头部投射 | — | — |
| 山羊 | 可挤奶 | — | — | 近战击飞 | 摔落抵抗、强健跳跃 |
| 河豚 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | 附效攻击（中毒） | — |
| 鱿鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 发光鱿鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 疣猪兽 | — | 击退抗性（…%） | 呼唤同伴 | 击退强化（数值）、近战击飞 | — |
| 铁傀儡 | 摔落免疫 | 击退抗性（…%） | 捕猎本能（目标生物…） | 近战击飞 | — |
| 羊驼 | — | 可骑乘、驮运箱 | 可驯服（家畜）、生物威慑、捕猎本能（目标生物…）、头部投射 | — | 摔落抵抗 |
| 行商羊驼 | — | 可骑乘、驮运箱 | 可驯服（家畜）、生物威慑、捕猎本能（目标生物…）、头部投射 | — | 摔落抵抗 |
| 岩浆怪 | 火焰免疫／火焰抗性、冻结易伤、摔落免疫、天然护甲（…） | — | 捕猎本能（目标生物…） | — | — |
| 豹猫 | — | — | 可驯服（宠物）、生物威慑、捕猎本能（目标生物…） | — | 摔落免疫 |
| 鹦鹉 | 肩头伴行 | — | 可驯服（宠物） | 扑翼飞行、扑翼缓降 | — |
| 幻翼 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸 | 扑翼飞行 | — |
| 北极熊 | 冻结免疫／冻结抗性 | — | 生物威慑、捕猎本能（目标生物…） | — | — |
| 兔 | 天然护甲（杀手兔） | — | — | — | 细雪行者、强健跳跃 |
| 劫掠兽 | — | 击退抗性（…%） | 生物威慑、呼唤同伴、咆哮 | 击退强化（数值） | — |
| 潜影贝 | 火焰免疫／火焰抗性、摔落免疫、末影瞬移、方块吸附 | 缩壳防御 | 呼唤同伴、头部投射 | — | — |
| 犰狳 | — | 缩壳防御、脱落鳞甲 | 生物威慑 | — | — |
| 骷髅 | 治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 骷髅马 | 治疗反转、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 可骑乘 | 无需呼吸 | — | 强健跳跃 |
| 僵尸马 | 治疗反转、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 可骑乘 | 无需呼吸 | — | 强健跳跃 |
| 史莱姆 | 免疫效果（渗浆） | — | 捕猎本能（目标生物…） | — | — |
| 雪傀儡 | 遇水受伤、冻结免疫／冻结抗性、高温灼伤、摔落免疫 | — | 捕猎本能（目标生物…）、头部投射 | — | 积雪足迹 |
| 蜘蛛 | 无视蛛网、免疫效果（中毒）、附魔易伤（节肢杀手） | — | 捕猎本能（目标生物…） | — | 攀墙 |
| 流浪者 | 冻结免疫／冻结抗性、治疗反转、阳光灼烧、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 炽足兽 | 火焰免疫／火焰抗性、遇水受伤、冻结易伤 | 可骑乘 | — | — | 熔岩行者 |
| 蝌蚪 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 海龟 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸 | — | — |
| 恼鬼 | 火焰免疫／火焰抗性 | — | 生物威慑、呼唤同伴 | 扑翼飞行 | — |
| 悦灵 | 自然恢复（…/秒） | — | 可驯服（宠物） | 扑翼飞行 | — |
| 监守者 | 火焰免疫／火焰抗性 | 击退抗性（…%）、音波冲击 | 振动感知 | 击退强化（数值） | — |
| 凋灵 | 火焰免疫／火焰抗性、冻结免疫／冻结抗性、治疗反转、水中下沉、免疫效果（中毒、生命恢复、凋零）、天然护甲（…）、自然恢复（…/秒）、附魔易伤（亡灵杀手）、凋灵护甲 | 悬浮飞行 | 无需呼吸、捕猎本能（目标生物…）、头部投射 | — | — |
| 凋灵骷髅 | 火焰免疫／火焰抗性、治疗反转、水中下沉、免疫效果（中毒、生命恢复、凋零）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 附效攻击（凋零）、拾取装备 | — |
| 狼 | — | — | 可驯服（宠物）、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 僵尸疣猪兽 | 火焰免疫／火焰抗性、治疗反转、水中下沉、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 击退抗性（…%） | 无需呼吸、生物威慑、捕猎本能（目标生物…） | 击退强化（数值）、近战击飞 | — |
| 僵尸猪灵 | 火焰免疫／火焰抗性、治疗反转、水中下沉、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、呼唤同伴 | 破门、拾取装备 | — |
| 唤魔者 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 施法 | — |
| 幻术师 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 掠夺者 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 卫道士 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 猪灵 | — | — | 捕猎本能（目标生物…）、呼唤同伴 | 拾取装备 | — |
| 猪灵蛮兵 | — | — | 捕猎本能（目标生物…） | — | — |
| 巨人 | — | — | — | — | — |
| 哞菇 | 可挤奶 | — | — | — | — |
| 女巫 | 魔法抗性 | — | — | 施法 | — |
| 牛 | 可挤奶 | — | — | — | — |
| 猪 | — | 可骑乘 | — | — | — |
| 绵羊 | 可剪毛 | — | — | — | — |
| 苦力怕 | — | 自爆 | — | — | — |
| 鸡 | — | 产蛋 | — | 扑翼缓降 | — |

## 五、代码入口与来源

| 层 | 现有入口 | 职责 |
| --- | --- | --- |
| 特性定义 | `BionicTrait`、`BionicTraitScope`、`BionicTraitRule` | 范围、载体、获取类型、返回值与附加条件 |
| 数据与供体 | `BionicTraitDataLoader`、`BionicTraitRegistry`、`BionicTraitDonors` | 数据覆盖、不可变快照、供体事实与按世界／数据版本缓存 |
| 测量与解析 | `BionicTissue`、`BionicTraitCarriers`、`BionicAnatomyRegistry`、`BionicTraitResolver`、`BionicTraitConditions` | 体积、肢体链、覆盖率、数量、承重与依赖 |
| 结果与提示 | `BionicTraitSet`、`BionicTraitResult`、手术台、纸箱与 JEI | 共用获取结果、强度、数值、集合、有效成员与提示 |
| 行为执行 | `SlimeBionicEntity`、`BionicDefenses`、`BionicSpecialAttacks`、`BionicHunting`、`BionicVibrationSense` | 体质、技能、索敌、瞬移与振动 |
| 装备与生产 | `BionicEquipment`、`BionicProduction`、`BionicCombatEvents`、`BionicAreaEffectCloud` | 交互、容器、生产、区域效果与主人关系 |
| 细雪接入 | `PowderSnowBionicFootMixin` | 腿部细雪行走 |

提示先独立显示性情、智力，再按全身、头部、躯干、手臂、腿部归组，空组省略；未安装头部时沿用前述躯干回退提示。技能显示“技能”标签。被 `overrides` 覆盖的特性已从统一结果移除，提示层不再另设覆盖优先级。比例型显示有效占比；数值型显示量化后的有效值。实体、手术台预览、纸箱使用同一解析结果；JEI 展示各特性的独立定义与供体。

`BionicTraitSet.coverage()` 对全身和躯干返回整体占比，对独立肢体返回最高覆盖率；`has()` 表示获取结果，`strength()` 表示执行系数，数量与承重通过附加条件判定。特性功能沿用现有 `BionicTraits.ENABLED` 发布开关。数据与分类覆盖通过玩家登录和 `/reload` 同步并更新缓存。

源码以 [ref/SOURCES.md](../ref/SOURCES.md) 和 [Minecraft/SOURCE.json](../ref/1.21.1/Minecraft/SOURCE.json) 记录的 Minecraft 1.21.1 原版快照为依据；NeoForge 扩展以本地 `build/moddev/artifacts/neoforge-21.1.234-sources.jar` 的 `EnderMan`、`Shulker`、`BreakDoorGoal`、`EventHooks` 与瞬移事件源码为依据。目标 Goal 的方法签名及呼援流程还核对了本地 NeoForm 已有 Java 源码归档 `sourcesAndCompiledWithNeoForge_5478701d24f4f19996755d682a4e8b1da115b3b8_output.jar`。附魔时长与原版标签使用 `build/moddev/artifacts/neoforge-21.1.234-client-extra-aka-minecraft-resources.jar` 中 Minecraft 1.21.1 的原版数据；生物反查表按嵌套实体标签展开。治疗反转／阳光灼烧（含避日）／水中下沉拆分另经 `compileJava processResources --offline`、JSON 与特性目录／文档对应关系检查，未启动客户端。
