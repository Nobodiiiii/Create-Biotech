# 仿生特性扩展

适用 Minecraft 1.21.1、NeoForge 21.1.234。动态目录、行为组件、可选 KubeJS 插件与公开 Java API 已接入。内置目录包含 66 项特性；新增 ID 通过组件或事件执行行为。当前网络协议 **38**，联机双方需要更新本模组。

## 数据包和附属模组

基础定义仍使用 [第一阶段格式](bionic-traits-extension-phase1.zh-CN.md)。三个目录分别负责：

| 路径 | 内容 |
| --- | --- |
| `data/<namespace>/bionic_traits/<path>.json` | 声明 `<namespace>:<path>`，含 `schema_version: 1`、范围、值类型、继承规则等 |
| `data/<namespace>/bionic_trait_bindings/<path>.json` | 独立向一个特性补充供体和载体 |
| `data/<namespace>/bionic_trait_modifiers/<path>.json` | 定向修改一个特性的配置或禁用它 |

同一资源 ID 仍按数据包优先级整体替换。加载顺序是：基础定义 → 按完整资源 ID 排序的绑定 → 按 `priority` 升序、完整资源 ID 排序的修改器。修改器后应用的字段覆盖先应用的字段。

绑定格式：

```json
{
  "trait": "example:frost_arm",
  "values": { "minecraft:zombie": true },
  "carriers": {
    "minecraft:zombie": { "ATTACK_HAND": ["right_arm", "left_arm"] }
  }
}
```

绑定可包含 `values`、`carriers`、`effects`、`variants`，对象按键递归合并；标签和精确实体值仍按既有规则解析。独立绑定可以扩充任意命名空间的特性，无需复制原定义。

修改器格式：

```json
{
  "trait": "example:frost_arm",
  "priority": 10,
  "patch": {
    "inheritance": { "min_coverage": 0.6 },
    "parameters": { "min_legs": 1 }
  }
}
```

`priority` 为 `[-100000,100000]` 的整数，缺省 `0`。`patch` 使用 JSON merge patch 语义：对象递归合并、数组与标量整体替换、`null` 删除字段。`{"patch":{"disabled":true}}` 禁用目标；禁用内置特性也会阻止它出现在判定结果中。目标不存在默认报错；绑定和修改器可设置 `"optional": true`，允许依赖的附属模组未安装时跳过。`create_biotech:__extension/` 是同步保留路径，不能用于声明特性。

结构、未知服务端组件或组件参数错误会拒绝本次目录加载，保留旧快照。完整目录到齐后客户端才切换，刷新 JEI 和供体缓存；客户端不执行组件行为。所有原始定义、绑定、修改器均纳入第一阶段的同步大小限制。

## 行为组件

在定义根对象添加 `behaviors` 数组，每个元素必须有完整 `type` ID。每种数组最多 64 项。参数在重载时编译，运行时不解析 JSON；仅对通过载体、范围、覆盖率、条件与覆盖关系判定的特性执行。

```json
"behaviors": [
  {
    "type": "create_biotech:on_hit_effect",
    "effect": "minecraft:slowness",
    "duration_ticks": 100,
    "amplifier": 0,
    "chance": 1
  }
]
```

| 类型 | 参数与执行规则 |
| --- | --- |
| `create_biotech:attribute` | 必填 `attribute`；`amount` 默认 0、范围 ±1024；`operation` 为 `add_value`（默认）、`add_multiplied_base`、`add_multiplied_total`；`scale` 为 `strength`（默认）、`value`、`none`。为该实体已有属性添加临时修正，不创建缺失属性 |
| `create_biotech:status_effect` | 必填 `effect`；`duration_ticks` 默认 100（1–24000），`amplifier` 默认 0（0–255），`interval_ticks` 默认 20（1–24000），`chance` 默认 1（0–1）。定期给自己施加效果 |
| `create_biotech:on_hit_effect` | 同样的 `effect`、时长、等级、概率；成功近战且实际攻击手臂提供该特性时执行，不使用 `interval_ticks` |
| `create_biotech:damage_multiplier` | `multiplier` 默认 1（0–100）；可选 `damage_type` 或 `damage_tag` 完整 ID；都填时必须同时匹配。倍率按强度从 1 插值，在原有防御前调整传入伤害；0 在满强度时免疫 |
| `create_biotech:regeneration` | `interval_ticks` 默认 100（1–24000），`amount` 默认 1（0–1024），按强度回复生命 |
| `create_biotech:item_production` | 必填 `item`，`count` 默认 1（1–64），`interval_ticks` 默认 6000（20–24000）；存活且 AI 启用时生成掉落物，不按组织数量倍增 |
| `create_biotech:effect_immunity` | 将该 `effect_set` 特性已经判定生效的效果集合用于状态免疫，无额外参数 |
| `create_biotech:inherited_attack_effects` | 将该 `attack_effect_set` 特性已判定的攻击效果用于实际近战手臂，保留各项概率、无武器限制、时长与等级，无额外参数 |

周期以世界时间和实体 ID 错开，重载不会额外发放一次产物或回复，也不补发离线期间的产物。属性修正在下一次服务端 tick 更新；特性移除、被覆盖、装配改变、重载或实体移除都会清理原组件的临时修正。已经施加的药水效果按原时长自然结束；已经生成的物品保留。行为组件抛出运行时异常会记录日志并停止该实体上的该组件，直到装配或目录重新判定；其他组件继续执行。

## 条件与载体选择器

`conditions` 是纯装配条件数组。原有范围、覆盖率和内置条件优先；条件只会进一步禁用特性。条件在原生判定后收敛，并在覆盖规则执行后再收敛，依赖项失败或被覆盖会阻止依赖它的特性。互相依赖且都已通过覆盖率的特性可以同时存在，不会凭空产生缺失供体。

| 类型 | 参数 |
| --- | --- |
| `create_biotech:body_volume` | `min` 默认 0，可选 `max`；需要已有服务端体积测量，要求体积落在闭区间内 |
| `create_biotech:requires_trait` | 必填完整 `trait` ID，要求该特性生效 |
| `create_biotech:source_count` | `min` 默认 1（1–4096），要求实际有效载体至少来自相应数量的不同来源 |

`carrier.selectors` 可以在现有组织角色之外继续限制模型体块。多个选择器取并集，再与原载体取交集；空结果不会回退到其他组织。内置选择器 `create_biotech:parts` 接受非空 `names` 数组，精确匹配保存的模型部件标签。缺少标签时不猜测体块。附属模组可注册任意新的命名空间选择器，不需要扩充内部组织角色枚举。

```json
"carrier": {
  "selectors": [{ "type": "create_biotech:parts", "names": ["crystal_core"] }]
},
"conditions": [{ "type": "create_biotech:source_count", "min": 1 }]
```

条件和选择器参与装配缓存，只能依赖保存的装配与判定结果。时间、天气、水中状态等动态逻辑应在行为或运行时事件中检查。服务端必须有所有自定义组件类型；客户端缺少行为类型时只跳过执行，缺少条件／选择器类型时采用保守的未生效结果。附属模组若要求客户端准确预览自定义条件或体块选择，必须同时安装在客户端。

## KubeJS

可选依赖 KubeJS **2101.7.2-build.381**、Rhino **2101.2.7-build.81**。未安装 KubeJS 时不加载插件，也不需要脚本依赖。插件不会将 KubeJS 或 Rhino 打包进模组。

在 `kubejs/server_scripts/` 中使用：

```js
BionicTraitEvents.definitions(event => {
  event.register('example:regeneration', {
    schema_version: 1,
    scopes: ['whole_body'],
    value_kind: 'ability',
    inheritance: { type: 'presence' },
    automatic_detection: false,
    behaviors: [
      { type: 'create_biotech:regeneration', interval_ticks: 100, amount: 1 }
    ]
  })
  event.bind('example:cow_regeneration', {
    trait: 'example:regeneration', values: { 'minecraft:cow': true }
  })
})

BionicTraitEvents.tick('example:regeneration', event => {
  if (event.entity.tickCount % 20 === 0 && event.entity.isInWater()) {
    event.entity.heal(0.25)
  }
})

BionicTraitEvents.hurt('example:regeneration', event => {
  event.amount *= 0.8
  // event.cancel() 将本次传入伤害设为 0。
})
```

| 入口 | 说明 |
| --- | --- |
| `definitions` | 服务端脚本加载后、数据包生成期间执行；`register(id, json)`、`bind(id, json)`、`modify(id, json)` 对应三个目录。同一次事件不能重复写同一资源 ID |
| `tick` | 每个有效特性每服务端 tick 一次，组件 tick 之后；支持可选特性 ID 过滤 |
| `melee` | 成功近战且该特性的有效载体与实际攻击手臂相交；额外提供 `target`、`arm`，组件近战回调之后 |
| `hurt` | 原有防御前，组件伤害倍率之后；提供 `source`、可写的非负有限 `amount`；支持 `cancel()`，按特性目录顺序累积 |
| `BionicTraits` | 绑定到 Java 的 `BionicTraitApi`；例如 `BionicTraits.result(entity, 'example:regeneration').active()` |

`tick`／`melee`／`hurt` 共有 `entity`、完整 `id`、不可变 `result`。回调保存在 KubeJS 的事件系统里，`/reload` 会按 KubeJS 规则重新加载；脚本删除定义后下次重载会移除该特性。脚本生成内容遵循 KubeJS 内部数据包的正常优先级，其他高优先级资源包仍可覆盖。客户端只接收生成的 JSON，不需要安装这些服务端脚本。

## Java 附属 API v1

入口是 `com.nobodiiiii.createbiotech.api.bionic.BionicTraitApi`，`API_VERSION = 1`。`definitions(level)`、`find(level,id)`、`result(entity,id)`、`evaluate(assembly,level)` 提供查询；结果为不可变对象。定义元数据在重载后重新查找，ID 句柄按完整 ID 比较。

在公共初始化的 `enqueueWork` 中、首次数据加载前注册类型：

```java
BionicTraitApi.registerBehavior(ResourceLocation.parse("addon:wet_regeneration"), json ->
    new BionicBehavior() {
        @Override public void tick(Context context) {
            if (context.every(20) && context.entity().isInWater()) context.entity().heal(1);
        }
    });
```

- `registerBehavior(id, factory)`：JSON 编译到不可变 `BionicBehavior`，提供 `tick`、`melee`、`incomingDamage`、`allowsEffect`、`removed`。`Context.modifierId()` 为每个特性和组件下标生成唯一属性修正 ID。
- `registerCondition(id, factory)`：JSON 编译到纯 `BionicCondition`，上下文有装配、世界、当前特性、候选结果集合和当前结果。
- `registerCarrierSelector(id, factory)`：JSON 编译到纯 `BionicCarrierSelector`，返回原始体块索引的 `BitSet`；返回新集合，勿修改保存的装配。
- `registerDefault(id, json)`：注册没有资源文件的默认定义；数据包同 ID 定义优先。实际资源随目录同步，不要求客户端重复注册默认值。

重复类型 ID、重复默认定义和第一次服务端加载后的注册均明确报错。工厂在每次重载时调用，返回的组件在实体间共享，不能把单个实体状态放在组件字段中。需要临时属性等副作用时在 `removed` 中对称清理。公开的 `BionicTraitEvent.Tick`、`.Melee`、`.Hurt` 可由附属模组在 `NeoForge.EVENT_BUS` 监听，时机与 KubeJS 相同；受伤事件可 `setAmount`。这些入口仅服务端触发。

API v1 保持上述接口语义；内部 `entity.trait` 数据加载、快照与规则实现不属于兼容承诺。内置的导航、头部分类和特殊攻击继续保留专属实现，新特性不会凭同名自动套用。

## 示例与验证边界

[可直接安装的示例](../examples/bionic-traits/README.zh-CN.md) 包含独立数据包和独立 KubeJS 脚本。

本次不修改 Mixin、不创建测试源文件、不启动客户端。编译和内存校验验证加载、覆盖顺序、注册、协议数据及移除等代码路径；实体动作、客户端 JEI 热更新和 KubeJS 实际脚本执行仍需在安装环境中验证。

KubeJS 接口参考本地 `ref/1.21.1/KubeJS/` 的 `KubeJSPlugin`、`EventGroup`、`KubeResourceGenerator`、`ServerScriptManager`；来源与版本差异记录于 [ref/SOURCES.md](../ref/SOURCES.md#kubejs-extension-reference)。该源分支使用 NeoForge 21.1.199，实际接口已按本项目 21.1.234 与发布版 KubeJS 381 编译核对。属性修正参考 `ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/attributes/AttributeInstance.java` 的 1.21.1 源码。

## 体质与力量

内置 `create_biotech:health_offset`（体质）与 `create_biotech:damage_offset`（力量）可通过 `values` 覆写供体的有符号数值，标签绑定和独立绑定沿用现有机制。默认值对应[最终修正表](vanilla-stat-offset-survey.zh-CN.md)；未配置供体为 0。只有这两项内置数值特性接受负值，其他数值特性仍要求非负数。

力量默认值保留原有人工平衡值，仅将末影人与猪灵蛮兵设为 +3，凋灵骷髅设为 +1，其余供体保持本轮 DPS 测算前的原始数值。DPS 测算与候选方案仅保存在[归档](vanilla-stat-offset-survey-archive-2026-11-13.zh-CN.md)，最终采用值以总表和资源文件为准。例如末影人的每臂力量为 +3；该值是每次命中的伤害偏移，不是直接增加 3 DPS。

`value_kind: "number"` 特性（包括内置数值特性和附属模组新增数值特性）默认按原供体体积分配：

`体块贡献 = 供体 values 数值 × 体块捕获体积 / 原供体全部体块捕获体积之和`

力量（`damage_offset`）只由原生手臂携带，单独使用 `体块贡献 = 供体力量 × 体块捕获体积 / 该体块所属原生手臂的完整捕获体积`。每条完整原生手臂各有完整的供体力量值，左右臂分别计算；不会平均到全身。模型标签区分左右手臂，原始连接将未命名的同臂段归组。原始手臂体积包含已切除部分，拆开后仍保留各碎片原有份额；无原生手臂标签或测量时不猜测力量。

装配后，直接求和生效部位内体块的贡献；臂、腿按每条关节链独立求和。`result.memberValues()` 保存每个体块未舍入的贡献，`armValue()` 对传入的唯一体块求和后统一归零或舍入。肢体特性的共用标签显示最终贡献绝对值最大的链，不把别的臂链用于当前出手。

原供体体积固定为捕获时完整数组的和，切割和重新拼装不会改变分母。相同来源的碎片仍分摊原数值，另一个独立供体有自己的分配总额；添加无修正组织只增加其自身贡献 0。载体、安装范围、附加条件仍生效，不会把范围外组织算入该特性。缺失原供体体积的旧组织贡献为 0。

数值继承仍声明 `coverage_scaled`；不再读取数值特性的 `min_coverage`（包含旧数据包或修改器中的体积预算）。供体总额和各体块贡献保留完整精度，在全身、躯干或每条关节链求和后处理：绝对值不足 0.1 直接归零，不显示也不生效；其余按四舍五入保留小数点后 1 位，显示和实际效果共用该值。正负值使用相同规则，例如 ±0.09 均归零，±0.15 分别成为 ±0.2。多个小额体块可先累加到 0.1，不逐块舍入。原有基础体积公式继续提供基础数值，生命、伤害等最终属性仍保留其范围限制。

例如：完整末影人的体质为 +20，切下原体积 30% 的组织贡献 +6；其余 70% 贡献 +14，两部分合回仍为 +20。再拼入一个或两个等体积的无修正体块，体质都保持 +20；若使用两个完整末影人供体则为 +40。牛的体质 −10 按相同方式分配，不会被额外的 0 值组织稀释。铁傀儡的力量 +10 则是每条原生手臂的总额：某条出手臂安装了其一条原生手臂体积 30% 的组织，就取得 +3 力量；完整该臂取得 +10，两条完整原生手臂拼成同一出手臂则为 +20。

体质在全身几何血量后相加；力量在出手臂的几何伤害后、附魔和正常伤害处理前相加。更新后 `/reload` 会重算已有实体，增加生命上限不会自动治疗。
