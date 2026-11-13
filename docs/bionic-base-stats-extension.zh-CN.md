# 拟态生物基础数值公式与校准

此接口处理由成品几何决定的基础数值，与 `bionic_traits` 的组织特性继承分开。天然护甲、供体击退、特殊攻击等仍由特性系统处理。头、臂、腿的数量继续来自有效关节拓扑，不能用数值公式伪造肢体。

运行时、纸箱 Alt 面板与近战 DPS 估算共用计算入口。数据包 `/reload` 后，下一次服务端 tick 更新已有生物的生命上限、地面速度和基础攻击属性；飞行、固有抗击退、感知距离也会刷新。提高生命上限不会自动治疗，降低时会把超过新上限的当前生命截下。已经开始的攻击保留当次快照，后续攻击使用新公式。

## 服务端配置：保留曲线类型，只改最大值与锚点

配置位于世界的 `serverconfig/create_biotech-server.toml`，区段为 `[bionicStats]`；可用 `defaultconfigs/create_biotech-server.toml` 为新世界提供默认配置。配置加载和重载时会重新计算曲线参数，并向在线客户端同步。配置文件不接受公式字符串。

默认生命校准配置如下；修改 `health_maximum` 可以调整生命曲线上限：

```toml
[bionicStats]
health_maximum = 400.0
health_anchor_volume = 0.40625
health_anchor_value = 20.0
health_second_anchor_volume = 5.184234619140625
health_second_anchor_value = 100.0
```

默认 `health_maximum` 为 400，校准偏移为 `19`、指数约 `0.7248744657`。调整上限时会保留僵尸体积 20 点、劫掠兽参考体积 100 点，并重新计算偏移与指数；不会把所有生物的生命按上限比例缩放。

| 参数 | 默认值 | 作用 |
| --- | ---: | --- |
| `health_maximum` | 400 | 生命曲线上限，Minecraft 属性允许最高 1024 |
| `health_anchor_volume` / `health_anchor_value` | 0.40625 / 20 | 第一个生命锚点 |
| `health_second_anchor_volume` / `health_second_anchor_value` | 5.184234619140625 / 100 | 第二个生命锚点 |
| `movement_maximum` | 0.6 | 解剖乘数之后的地面速度上限 |
| `leg_anchor_length` / `leg_anchor_speed` | 0.75 / 0.23 | 腿长曲线第一个锚点，未计腿数、体积和膝盖乘数 |
| `leg_second_anchor_length` / `leg_second_anchor_speed` | 1.875 / 0.36 | 腿长曲线第二个锚点 |
| `hopping_speed` | 0.23 | 不超过一条接地腿时的地面速度 |
| `flight_maximum` | 0.6 | 有效飞行组织占比 100% 时的速度 |
| `flight_anchor_ratio` / `flight_anchor_speed` | 0.25 / 0.3 | 飞行曲线锚点；自动重算对数曲线的幂指数 |
| `swimming_speed` | 1 | 专精游泳启用时的移动属性值 |
| `base_attack_damage` | 3 | 装备与手臂几何乘数之前的基础近战伤害 |
| `base_knockback_resistance` | 0 | 固有抗击退；供体特性贡献另外叠加，最终限制到 0–1 |
| `base_follow_range` | 35 | 基础感知距离；特殊攻击仍可提出更大的距离需求 |
| `arm_dps_maximum` | 4 | 单臂体积 DPS 倍率曲线的渐近上限 |
| `arm_anchor_volume` / `arm_anchor_dps_scale` | 0.046875 / 1 | 单臂体积与 DPS 倍率锚点 |
| `arm_anchor_thickness` / `arm_reference_interval` | 0.25 / 20 | 等效粗细与攻击间隔锚点 |
| `arm_minimum_interval` / `arm_maximum_interval` | 10 / 40 | 解剖攻击间隔范围，单位 tick |
| `global_minimum_interval` / `global_maximum_interval` | 12 / 40 | 实际攻击间隔范围，最小值也用于默认的单臂恢复 |

长度为方块，体积为方块³；速度为 Minecraft 属性值，非方块/秒。间隔参数最终取整。

生命锚点必须按体积和生命同时递增，并且低于上限。腿长锚点必须满足 `L1 < L2`、`S1 < S2`、`S2/S1 < L2/L1`、`S2 <= movement_maximum`，以便拟合饱和双曲线。飞行锚点占比在 0–1 之间，速度低于上限。单臂 DPS 锚点倍率低于其上限。粗细间隔锚点必须位于最小/最大解剖间隔内；全局最小间隔不得高于最大间隔。不合法的参数组合会记录错误并保留上一份有效校准；首次加载前的有效校准是内置默认值。

## 数据包：自定义公式

资源路径为 `data/create_biotech/bionic_stats/<数值ID>.json`。对同一个 ID，遵循标准数据包优先级；未提供的项目使用内置公式或附属模组注册的默认公式。

例如 `data/create_biotech/bionic_stats/max_health.json`：

```json
{
  "expression": "clamp(20 + volume * 15, 1, health_maximum)"
}
```

这是完整替换该项公式。自定义公式只遵循显式使用的参数；上例引用了配置的上限，但没有引用生命锚点，因此不保证经过两个锚点。要保留内置校准，可使用：

```json
{
  "expression": "clamp(health_maximum / (1 + health_offset * pow(health_anchor_volume / max(volume, 1e-12), health_exponent)), 1, health_maximum)"
}
```

| 数值 ID | 几何输入变量 | 输出及默认行为 |
| --- | --- | --- |
| `max_health` | `volume` | 生命上限；通过两个锚点的 Hill 曲线 |
| `movement_speed` | `leg_length`、`leg_count`、`knee_count`、`leg_volume_ratio` | 地面速度；腿长曲线与腿数、腿体积、膝盖乘数 |
| `flying_speed` | `flight_volume_ratio` | 飞行速度；有效飞行组织占比的对数曲线，先满足原有飞行特性条件 |
| `swimming_speed` | 无 | 专精游泳的移动属性值，启用条件仍由特性决定 |
| `attack_damage` | `volume` | 基础攻击伤害，默认 3 |
| `knockback_resistance` | `volume` | 固有抗击退，默认 0，不替换供体特性贡献 |
| `follow_range` | `volume` | 基础感知距离，默认 35 |
| `arm_dps_scale` | `arm_volume`、`arm_length`、`arm_thickness` | 单臂几何 DPS 倍率，默认以体积立方根作饱和曲线 |
| `arm_attack_interval` | 同上 | 单臂解剖攻击间隔，默认与粗细平方根相关 |
| `arm_damage_multiplier` | 同上，加 `arm_dps_scale`、`arm_interval` | 单次伤害倍率，默认 `arm_dps_scale * arm_interval / arm_reference_interval` |
| `arm_recovery` | `arm_interval` | 单臂恢复时间，默认 `max(global_minimum_interval, arm_interval)` |
| `attack_interval` | `arm_recovery`、`arm_count` | 全局出手间隔，默认计入多臂协调与全局上下限 |

`volume` 是封装成品的非重叠总体积；`leg_length` 是接地腿的平均有效长度；`leg_count`、`knee_count` 只统计接地腿及其膝盖。`arm_length` 是肩至手端的关节链长度，至少按 1/64 方块计；`arm_thickness = sqrt(arm_volume / arm_length)`。`arm_interval` 是解剖间隔的最终整数。`arm_count` 是能参与当前攻击协调的手臂数；纸箱 DPS 按全部可用攻击手臂计算，并排除翅膀。

每条公式还可引用上表全部配置参数，以及自动校准得到的 `health_offset`、`health_exponent`、`length_asymptote`、`length_half_saturation`、`flight_exponent`、`arm_dps_offset`。可引用的几何变量限定于该项的输入，拼写错误会在重载时拒绝。

表达式支持数字、科学计数法、括号、`+ - * / %`、`< <= > >= == !=`、`&& || !`、短路三元运算 `条件 ? 真值 : 假值`，常量 `pi`、`e`。函数包括 `min/max/pow`（两个参数）、`clamp(value,min,max)`、`lerp(t,a,b)`，以及单参数的 `abs/sqrt/cbrt/log/ln/log1p/exp/floor/ceil/round/sin/cos`。`log/ln` 是自然对数；幂运算使用 `pow`。条件结果用 0/1 表示。

例如，可把多臂协调规则改成固定每次 15 tick：

```json
{"expression": "15"}
```

将此文件保存为 `attack_interval.json`。这会同时影响实际出手与纸箱 DPS。动画周期、目标位置、手臂恢复和伤害免疫等仍可能影响实战输出，DPS 是装备外的理论估算。

公式在重载时编译，运行时不解析 JSON，也不启动 JavaScript 引擎。每条最多 4096 字符、256 个节点、嵌套深度 64。整个同步快照最多 65536 字符。语法错误、未知变量或未知服务端公式类型拒绝发布新快照；原有效快照保留。运行时异常或 NaN/无穷结果，对该次计算回退到当前配置校准的内置公式，每个数值每次重载最多记录一次错误。

最终结果仍受引擎边界限制：生命 1–1024，移动/飞行/游泳 0–1024，基础攻击伤害 0–2048，固有抗击退 0–1，感知距离 0–2048，单臂 DPS/伤害倍率 0–1024，各种攻击间隔 1–1200 tick。数据包可以自选比配置更高的值，但不能越过这些边界。

## KubeJS

沿用可选依赖 KubeJS `2101.7.2-build.381`、Rhino `2101.2.7-build.81`，在 `kubejs/server_scripts/` 使用：

```js
BionicStatEvents.formulas(event => {
  event.set('max_health', 'clamp(20 + volume * 15, 1, health_maximum)')
  event.set('movement_speed', 'leg_count <= 1 ? hopping_speed : min(movement_maximum, 0.2 + leg_length * 0.1)')
  event.set('attack_interval', 'max(global_minimum_interval, round(arm_recovery / sqrt(arm_count)))')
})
```

`set(stat, expression)` 生成对应 JSON；`define(stat, json)` 可指定附属模组的公式类型。`stat` 可写短名或 `create_biotech:<短名>`，同次事件重复设置同一个项目会报错。生成内容遵循 KubeJS 内部数据包的正常优先级，`/reload` 后移除的脚本公式会恢复为其他数据包或默认公式。客户端接收生成的公式与参数，无需安装服务端脚本或 KubeJS。

全局绑定 `BionicStats` 对应 Java 的 `BionicStatApi`，用于查询参数和版本；游戏计算不逐次调用脚本闭包。

## Java 附属模组 API v1

入口 `com.nobodiiiii.createbiotech.api.bionic.BionicStatApi`，`API_VERSION = 1`。数值枚举是 `BionicStat`，函数接口是 `BionicStatFormula`。在公共初始化 `enqueueWork`、首次数据加载前注册：

```java
BionicStatApi.registerFormulaType(ResourceLocation.parse("example:linear_volume"), (stat, json) -> {
    double base = json.get("base").getAsDouble();
    double scale = json.get("scale").getAsDouble();
    return context -> base + scale * context.get("volume");
});
```

数据包或 `event.define` 使用：

```json
{"type": "example:linear_volume", "base": 20, "scale": 15}
```

将该类型用于有 `volume` 输入的数值。工厂在重载时编译 JSON，返回函数必须不可变、纯计算、线程安全，不保存单实体状态、不访问客户端单例或修改世界。参数、几何输入均通过 `context.get(name)` 读取。

- `registerDefault(BionicStat, JsonObject)`：没有数据包资源时使用的默认定义，随服务端快照同步。
- `variables(BionicStat)`：此数值允许的变量集合。
- `parameters(Level)`：该世界使用的不可变校准参数。
- `evaluate(BionicStat, Level, Map<String,Double>)`：与内置计算一致的求值和边界处理。必须提供该项所有输入，且为有限数。
- `revision(Level)`：配置或数据快照变化时递增，可用于失效附属模组缓存。

重复注册、占用内置 `create_biotech:expression` 类型、首次数据加载后再注册都会报错。工厂 ID 使用完整命名空间。自定义 Java 类型需要客户端也安装提供该类型的附属模组，才能保证预览一致；缺少该类型的客户端会警告并使用校准后的内置公式预览，服务端仍使用真实公式。纯表达式定义不需要客户端附属模组。

客户端与服务端快照独立，单人游戏接收同步也不覆盖集成服务器数据；断线时清理客户端快照，停止服务器时清理本世界的数值与资源。所有 `Level` 查询都按所在逻辑侧选择快照；无 `Level` 的旧校准辅助方法使用服务端快照。

KubeJS 接口参考 `ref/1.21.1/KubeJS/src/main/java/dev/latvian/mods/kubejs/plugin/KubeJSPlugin.java` 和 `generator/KubeResourceGenerator.java`；此分支快照的版本 caveat 见 [ref/SOURCES.md](../ref/SOURCES.md#kubejs-extension-reference)，本项目按发布版 381 编译。属性边界参考 `ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/attributes/Attributes.java`。
