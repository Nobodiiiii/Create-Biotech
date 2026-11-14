# 仿生模块数据化审计（2026-11-14）

审计基线：`1.21.1` 分支当前工作区，包含已有未提交改动；Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281。此次新增本报告，没有修改实现或已有文档，没有创建测试文件或启动客户端。

## 结论

1. **现有方案的基础架构较完整，适合继续扩展。** 特性使用完整资源 ID；绑定、修改器、部位、解剖模板、供体事实、装配判定、展示、同步和缓存更新已有明确链路。新增特性可以通过通用组件、玩法规则和 Java/KubeJS 扩展执行行为。
2. **66/66 项内置特性已进入统一的供体与装配判定流程，也都存在对应 JSON。** 本轮未发现某项内置特性完全缺少数据入口，或仅有展示而没有运行时消费路径。
3. **不能据此认定内置玩法已经全部由数据驱动。** 内置资源中 `behaviors` 为 0/66，`gameplay` 为 0/66；全部仍依赖按内置 ID 调度的原生 Java 实现。现有组件和玩法接口提供了扩展入口，但尚未成为内置内容本身的统一实现方式。
4. **可配置范围不完全一致，并有配置被静默忽略的情况。** 潜影弹冷却覆盖、结构修改器无声失效、原生攻击未知变体空执行，见下文。部分规则在显式供体配置之后仍被 Java 强制限制。

这里分别评价“进入数据判定链路”“可以用数据调整玩法”“内置实现本身以数据声明”。三者不能合并为一个完成率。执行器用 Java 实现本身合理；缺口在于具体内容、参数和选择关系尚未全部通过数据选择这些执行器。

## 盘点结果

| 项目 | 当前数量 / 状态 |
| --- | --- |
| 内置特性常量 / 内置特性 JSON | 66 / 66，一一对应 |
| 完整 `schema_version: 1` 内置定义 | 2：`rideable`、`attack_knockback` |
| 依赖 Java 元数据默认值的内置配置 | 64；范围、值类型、继承类型和默认载体主要在 Java 中 |
| 开启 / 关闭 `automatic_detection` 的内置资源 | 26 / 40 |
| 内置资源使用通用 `behaviors` | 0 |
| 内置资源使用 `gameplay` | 0 |
| 公共部位定义 / 特性私有部位 | 15 / 5 |
| 内置解剖资源 | 7，部分只提供模型别名，不等于七套完整器官组合规则 |
| 基础属性公式 | 12 项，独立 `bionic_stats` 加载、同步和 API；默认表达式保留在 Java |

数量来自当前资源扫描，不把 `examples/` 中的示例算作内置实现。`behaviors/gameplay` 为零不表示这些接口未接入运行时，而表示内置内容尚未使用它们声明默认玩法。

## 实现方式评价

### 已有的有效设计

- **目录不再被枚举锁死。** `BionicTrait` 按完整 ID 比较；加载器和解析器遍历快照的动态目录，自定义命名空间可进入同一流程。[定义](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTrait.java)、[加载](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitDataLoader.java#L81)、[解析](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitResolver.java#L43)。
- **内容分层合理。** 原始定义、供体绑定和修改器分开；修改器按优先级和完整 ID 排序，递归合并对象、替换数组、允许 `null` 删除字段。附属内容不必复制整份供体表。[合并实现](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitResources.java#L21)。
- **供体事实与装配判定分开。** `BionicTraitDonors` 负责探测和覆盖；`BionicTraitCarriers/BionicTissue` 选择组织；`BionicTraitResolver` 统一计算覆盖率、数值贡献、器官前提、条件和替代关系。效果与 UI 可读取同一个结果及失败原因，而不必分别猜测。[供体](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitDonors.java#L78)、[载体](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitCarriers.java#L52)。
- **目录发布与网络有边界。** 特性、部位、解剖模板在完成编译后替换快照；服务端和客户端各持快照；完整原始资源分块同步，客户端收齐后才发布。目录有资源数、单资源、分块和总字节上限。[注册表](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitRegistry.java#L34)、[协议](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitRulesPacket.java)、[客户端提交](../src/main/java/com/nobodiiiii/createbiotech/client/BionicTraitClientRules.java#L36)。
- **扩展行为有生命周期。** 通用行为支持 tick、近战、受伤、效果免疫和移除清理；装配或目录变化后重建绑定；异常组件隔离停用。生产、交互、装备和特殊攻击也有公共事件入口。[行为执行](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicBehaviorRunner.java)、[玩法运行时](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicGameplayRuntime.java)。
- **基础属性也能扩展。** `bionic_stats` 支持表达式和注册的公式类型，公式编译失败拒绝加载，运行时非法结果使用默认公式，并保留属性范围限制。[属性目录](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/stats/BionicStats.java#L53)、[12 项属性](../src/main/java/com/nobodiiiii/createbiotech/api/bionic/BionicStat.java#L6)。

原子发布的评价限于特性目录本身：头部分类与基础属性仍由另外的加载器和数据包发布，不能称整个仿生子系统只有一个原子事务。

### 尚未统一的部分

- 64 项内置资源属于配置覆盖，而非完整定义。即使有 JSON，其默认范围、继承类型、条件身份和载体角色仍取自 `BionicTrait.java`。
- 专用效果集中在 `SlimeBionicEntity`、`BionicDefenses`、`BionicProduction`、`BionicSpecialAttacks` 等类。给内置特性增加 `behaviors` 会追加执行，移除 `behaviors` 不会关闭它的原生实现；没有统一的原生行为替换开关。
- 器官条件已有通用 `gameplay.anatomy`，但默认剪毛、挤奶、胸腔、壳体等仍按固定条件或内置 ID 分派。挤奶产品仍由牛、哞菇、山羊专用逻辑产生。
- 捕猎、威慑、呼救有通用关系规则，但默认关系表和特殊时机仍在 Java 中。保留原生 AI 适配是合理的；关系内容和参数可以进一步迁入数据。
- 部分参数只有非负数检查，再在 getter 中按键名截断，而没有逐参数的类型、单位和范围契约。错误配置有时拒绝、有时警告后回退、有时静默忽略，作者难以预判结果。

## 具体问题与复现条件

以下为静态调用链确认；未在真实世界运行复现。P2 表示配置可靠性问题，不表示当前默认玩法必然异常。

### P2：潜影弹冷却配置会被原生随机冷却覆盖

位置：[BionicSpecialAttacks.finish](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java#L216)。

先读取 `cooldown_ticks`，随后对 `shulker_bullet` 无条件改写成 `nextInt(10) * 10`。例如把 `head_projectile` 的 `minecraft:shulker_bullet.parameters.cooldown_ticks` 设为 200，完成攻击后仍使用 0–90 tick 随机冷却。当前默认配置的 0 也不是实际固定冷却。

建议：保留原版随机冷却作为明确的数据策略，例如配置最小值、最大值及步长；显式固定冷却不应再被覆盖。

### P2：64 项内置配置的结构修改器可能被接受但不生效

位置：[BionicTraitDataLoader.decode](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitDataLoader.java#L87)、[readDefinition](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitDataLoader.java#L201)。

例如向 `create_biotech:water_breathing` 应用：

```json
{
  "trait": "create_biotech:water_breathing",
  "patch": { "carrier": { "roles": ["create_biotech:eye"] } }
}
```

合并器会接受该字段，但原资源没有 `schema_version`，加载器直接沿用 Java 定义，不读取这项 `carrier.roles`。只修改 `scopes` 或 `inheritance.type` 也有相同边界。`carrier.selectors` 与 `replace_with_bindings` 是另行读取的字段，不属于这个问题。

目前可以用完整 schema 定义覆盖内置 ID，按文档保留值类型并填写所需结构字段；因此这不是结构完全不能配置，而是“局部修改”的行为不一致且没有诊断。

建议：先把 Java 默认值展开成完整定义，再统一应用修改器；过渡期至少拒绝或警告这类未被消费的字段。

### P2：原生攻击的新变体缺少实现时静默空执行

位置：[choices](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java#L140)、[Choice.kind](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java#L46)、[fireNative](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java#L246)。

给 `head_projectile` 新增已声明且已绑定供体的 `example:ice_ball` 变体，但不提供 `gameplay.attacks` 实现，加载器不会拒绝。它可以进入攻击候选，播放动作并消耗时序，最终在原生分派的 `default -> return` 中什么也不生成；外层还会发送攻击 after 事件。

`kind()` 只取变体 ID 的 path，因而 `example:snowball` 反而会套用原生雪球实现。这里尚未形成清晰的“完整变体 ID → 已注册执行器”契约。

建议：重载时检查每个可执行攻击变体必须拥有显式实现或匹配已知的完整原生 ID；未知实现应报错，避免合法目录产生空动作。

### 配置边界：显式供体值不是最后一道规则

位置：[BionicTraitDonors](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicTraitDonors.java#L161)。

`values`、标签与检测器执行完毕后，Java 仍会移除幼年疣猪兽/僵尸疣猪兽的 `launch_strike`，以及全部幼体的 `milkable/wool_shearing/egg_laying/scute_shedding`。数据包无法仅通过显式 `true` 覆盖这些限制。

这可以是有意的默认规则，但应单独声明其优先级；若目标是可完全调整供体资格，应把年龄前提移到可配置供体条件中。

### 数据化缺口：多个重要行为参数没有配置入口

| 内容 | 仍在 Java 中的例子 | 位置 |
| --- | --- | --- |
| 末影传送 | 投射物闪避尝试 64 次、随机半径 32、受伤后 0.9 概率、追击距离与等待时间 | [BionicDefenses](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicDefenses.java#L42) |
| 凋灵护盾 | 半血门槛、阻挡的投射物类型 | [BionicDefenses](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicDefenses.java#L33) |
| 振动感知 | 半径 12、记忆 40 tick、只追踪已选目标 | [BionicVibrationSense](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicVibrationSense.java#L31) |
| 接触反伤 / 冻伤易伤 | 固定反伤 2、冻伤额外倍率系数 4 | [SlimeBionicEntity.hurt](../src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicEntity.java#L1140) |
| 被动再生 / 日晒 / 缺水 | 再生每 10 tick、固定燃烧时长、干燥计时和伤害规则 | [SlimeBionicEntity](../src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicEntity.java#L1642) |
| 生产 / 自爆 | 剪毛每器官 1–3、刷取耐久 16、再生依赖吃草、自爆距离与效果云 | [BionicProduction](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicProduction.java) |
| 原生特殊攻击 | 女巫药水选择、尖牙数量/布局、风弹弹速与难度散布等 | [BionicSpecialAttacks](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicSpecialAttacks.java#L246) |

不建议把所有导航和物理算法都转成 JSON；优先迁出内容表、概率、范围、伤害、时序和物种选择，执行算法保留在有明确契约的 Java 类型中。

## 66 项内置特性逐项核对

下表所有条目均已有 JSON，经过动态供体目录和统一 resolver，存在运行时消费路径。表中列的是在通用供体/绑定/门槛/条件入口之外，已有的专用数据能力及剩余硬编码。所有行的默认效果仍属于原生 Java 实现。

| 特性 ID（省略 `create_biotech:`） | 运行时消费者 | 专用数据能力 / 剩余边界 |
| --- | --- | --- |
| disposition | BionicMind / BionicHeadTraits | 独立分类表可改；等级集合与多头合并算法固定 |
| intelligence | BionicMind / BionicHeadTraits | 独立分类表可改；等级集合与决策分支固定 |
| health_offset | SlimeBionicEntity | 有符号供体值、固定原体积份额；总血量公式独立可配 |
| fire_immune | SlimeBionicEntity | 供体和门槛可配；原生免疫钩子 |
| fire_resistance | SlimeBionicEntity | 覆盖率减伤；原生伤害标签和计算逻辑 |
| water_sensitive | SlimeBionicEntity | 供体和门槛可配；原生遇水逻辑 |
| freeze_immune | SlimeBionicEntity | 供体和门槛可配；原生冻伤钩子 |
| freeze_resistance | SlimeBionicEntity | 覆盖率减伤；原生冻伤分派 |
| freeze_vulnerable | SlimeBionicEntity | 覆盖率可配；额外倍率系数固定 |
| sun_sensitive | SlimeBionicEntity | 供体和门槛可配；点燃及头盔规则固定 |
| moisture_dependent | SlimeBionicEntity | 供体和门槛可配；水分计时与环境判定固定 |
| heat_sensitive | SlimeBionicEntity | 供体和门槛可配；生物群系标签与伤害时机固定 |
| inverted_healing | SlimeBionicEntity | 供体可配；原生治疗/伤害反转钩子 |
| water_sinking | SlimeBionicEntity | 供体和门槛可配；原生移动与漂浮控制 |
| web_adapted | SlimeBionicEntity | 供体可配；固定识别蛛网 |
| no_breathing | SlimeBionicEntity | 供体可配；原生流体呼吸钩子 |
| wingless_flight | SlimeBionicEntity | 组织与替代关系可配；飞行速度走独立公式 |
| shoulder_riding | BionicShoulderRidingGoal | 供体和门槛可配；上肩前提和等待时间固定 |
| projectile_deflection | SlimeBionicEntity | 供体可配；反射策略和风弹排除固定 |
| contact_retaliation | SlimeBionicEntity | 供体可配；伤害 2 与触发条件固定 |
| water_breathing | SlimeBionicEntity | 供体、器官绑定可配；原生水呼吸钩子 |
| dry_suffocation | SlimeBionicEntity | 供体、器官绑定可配；空气计时和伤害固定 |
| tameable | SlimeBionicEntity / BionicGameplayRuntime | gameplay 可改驯服物品/概率；默认驯服及坐下流程原生 |
| livestock_tameable | BionicGameplayRuntime / SlimeCompanionHandler | gameplay 可改驯服物品/概率；畜类行为原生 |
| vibration_sense | BionicVibrationSense | 供体、载体可配；半径、记忆与目标规则固定 |
| deterrence | BionicDeterrence | gameplay 关系可追加/替换；默认表与 Brain/Goal 适配在 Java |
| fall_damage_immune | SlimeBionicEntity | 供体和门槛可配；原生摔落钩子 |
| fall_reduction | SlimeBionicEntity | 安全距离、倍率、平减已有参数 |
| powder_snow_walk | PowderSnowBionicFootMixin | 查询统一结果；原生粉雪碰撞钩子 |
| lava_walk | SlimeBionicEntity | 供体、腿链条件可配；熔岩移动规则原生 |
| wall_climb | SlimeBionicEntity / BionicWallClimbing | 供体、腿链条件可配；攀爬算法原生 |
| damage_offset | SlimeBionicEntity | 有符号供体值、原生单臂体积分配；伤害公式另可配 |
| wing_flight | SlimeBionicEntity | 翼数、承重比例可配；升力条件与导航算法原生 |
| swim_specialist | SlimeBionicEntity | 供体和门槛可配；游泳速度走独立公式 |
| effect_attack | SlimeBionicEntity | 效果、时长、等级、概率、空手限制可配；默认未使用通用效果组件 |
| shell_defense | SlimeBionicEntity | 持续时间、器官绑定可配；姿态、停移动和减伤算法原生 |
| immune_effects | SlimeBionicEntity / BionicTraitSet | 效果集合、自动检测和覆盖率可配；默认未使用通用免疫组件 |
| natural_armor | SlimeBionicEntity | 供体数值和体积分配可配；直接设置原生属性 |
| knockback_resistance | SlimeBionicEntity | 供体数值和体积分配可配；基础值另走公式 |
| passive_regeneration | SlimeBionicEntity | 数值可配；固定 10 tick 节奏，未使用通用再生组件 |
| enchant_vulnerability | BionicDefenses | 变体与每级伤害可配；节肢杀手减速等特例原生 |
| hunting_instinct | BionicHunting | gameplay 关系可追加/替换；默认猎物表和物种冷却原生 |
| pack_alert | BionicPackAlertGoal | gameplay 关系可追加/替换；默认同类识别和呼救时机原生 |
| attack_knockback | SlimeBionicEntity | 完整 schema；按物种绑定替换默认载体，供体数值可配 |
| door_handling | SlimeBionicEntity / BionicBreakDoorGoal | 供体可配；开门/破门 AI 原生 |
| wing_slow_fall | SlimeBionicEntity | 翼数和下降倍率可配 |
| strong_jump | SlimeBionicEntity → BionicTraitSet | 跳跃加成可配；运行时通过 strongJumpBonus 消费 |
| ender_teleport | BionicDefenses / SlimeBionicEntity | 供体和门槛可配；距离、尝试、概率、追击时序原生 |
| block_attachment | BionicBlockAttachment | 私有底座与绑定可配；六面附着和几何算法原生 |
| magic_resistance | BionicDefenses | 减伤倍率可配；适用伤害标签固定 |
| wither_shield | BionicDefenses | 供体和门槛可配；半血与投射物规则固定 |
| launch_strike | SlimeBionicEntity | 上抛加成可配；部分幼体资格在数据之后强制禁用 |
| rideable | BionicEquipment / BionicRiding | 完整 schema；装备物品、座位、偏移可配；默认物种驾驶差异原生 |
| cargo_chest | BionicEquipment | 箱子物品与容量可配；默认骆马容量公式原生 |
| loot_pickup | BionicEquipment | 供体和载体可配；拾取武器筛选与替换策略原生 |
| sonic_boom | BionicSpecialAttacks | 射程、时序、伤害、击退可配；完整胸腔仍有原生默认条件 |
| head_projectile | BionicSpecialAttacks | 变体、部分弹速/连射参数与自定义攻击可配；潜影弹冷却例外及未知变体问题见上 |
| guardian_beam | BionicSpecialAttacks | 变体射程、时序、伤害可配；原生光束与困难难度附加值 |
| spellcasting | BionicSpecialAttacks | 变体射程、时序、自定义攻击可配；默认药水选择/尖牙布局原生 |
| self_destruct | BionicProduction | 引信、威力可配；距离、点燃、死亡与效果云规则原生 |
| snow_trail | BionicProduction | 供体和腿链可配；雪方块与落点规则固定 |
| pollination | BionicProduction | 采粉时长、催熟间隔、预算可配；花朵搜索及作物处理原生 |
| milkable | BionicMilking / BionicProduction | 产物/交互/解剖可替换；默认物种产品及幼体限制原生 |
| wool_shearing | BionicProduction | 产物可替换；默认产量、吃草再生、幼体限制原生 |
| egg_laying | BionicProduction | 周期和产物可配；默认器官计数与幼体限制原生 |
| scute_shedding | BionicProduction | 周期、产物、交互可配；默认刷子耐久与幼体限制原生 |

## 建议的实施顺序

1. **先解决配置可靠性。** 修正潜影弹冷却覆盖；对原生攻击变体验证完整实现；检测结构修改器中未被读取的字段。给已公开参数建立逐项类型和范围定义，不以键名猜测范围。
2. **把内置默认定义统一成完整 schema。** 先将剩余 64 项元数据迁入资源，再统一处理定义和修改器，保留 Java 常量作为 ID 句柄。这样修改内置和自定义特性可以遵守相同契约。
3. **选取能复用现有组件的内置效果迁移。** 优先免疫效果、命中效果、普通属性/减伤和再生；迁移时取消对应原生分支，防止原生与组件重复执行。数值属性迁移需保留当前体积、基础公式和属性更新语义。
4. **迁出玩法内容，保留专用执行器。** 默认关系表、生产物、器官组合、装备规则及攻击实现选择进入数据；导航、几何、物理钩子和原版 AI 适配保留 Java。年龄限制、随机冷却等应成为显式规则。
5. **统一扩展作者的反馈。** 明确拒绝未知结构和无实现攻击；报告最终生效的参数、选择的执行器及残留原生限制；同步更新当前扩展文档。

## 验证记录与限制

- 扫描并解析了全部 66 项内置特性 JSON，核对常量、文件名、schema、自动检测、组件与玩法字段，检查现有参数是否超过当前 getter 的截断值；当前内置参数未发现因此被截断的实例。
- 逐项追踪运行时消费者，强跳通过 `BionicTraitSet.strongJumpBonus()`、效果免疫通过 `isImmuneTo()` 等间接调用核对，未仅凭搜索常量名判断遗漏。
- 执行 `.\gradlew.bat compileJava --offline`，结果 `BUILD SUCCESSFUL`；`compileJava` 为 `UP-TO-DATE`，这是当前编译状态确认，不是强制全量重编译或数据包运行时加载验证。
- 未创建测试文件，未启动客户端，未验证真实世界动作、重载失败回滚、联机 UI 或 KubeJS 脚本执行。本文的配置失效结论由已读加载器和消费者代码推导；需要实机复现时应另行安排。
- 本轮判断不依赖第三方 API 内部实现，未查阅网上依赖源码、未反编译 jar。Gradle 的依赖准备使用已有缓存。
