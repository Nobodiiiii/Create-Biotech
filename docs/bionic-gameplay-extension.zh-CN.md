# 仿生玩法规则与扩展接口

适用 Minecraft 1.21.1、NeoForge 21.1.234，网络协议 **45**。客户端与服务端都需要更新本模组。扩展在现有 `bionic_traits` 定义根对象中使用 `gameplay`；也可用 `bionic_trait_modifiers` 的 `patch.gameplay` 修改内置特性。原有绑定、修改器顺序、目录大小限制、原子重载、客户端同步与缓存失效规则继续适用。没有 `gameplay` 时保留原有玩法。

所有玩法配置在重载时编译。未知服务端类型、非法范围、重复周期/交互 ID、未定义的攻击变体、未定义或跨特性引用的私有部位会拒绝新目录。客户端不执行行为；缺少自定义 Java 条件或解剖实现时采用不生效的保守预览。附属模组若需要准确预览，应同时安装在客户端。

## 关系表

在 `hunting_instinct`、`deterrence`、`pack_alert` 对应定义中使用：

```json
{
  "gameplay": {
    "relations": {
      "replace_native": false,
      "rules": [{
        "donors": ["minecraft:wolf"],
        "targets": ["minecraft:creeper", "#minecraft:skeletons"],
        "range": 16,
        "speed": 1.2,
        "cooldown_ticks": 200,
        "condition": {"type": "create_biotech:environment", "line_of_sight": true}
      }]
    }
  }
}
```

`donors` 可省略，表示该特性的所有有效供体。供体和目标均支持完整实体 ID 或 `#实体类型标签`。`targets` 必须非空。`replace_native` 默认 false；true 时移除原生关系。数据包应同时确保供体具有该特性，例如设置 `values`；捕猎和威慑在启用 `automatic_detection` 时也能从关系规则识别供体。

`range` 默认 16，范围 0–128 方块；`speed` 默认 1.2，范围 0.01–4，用于威慑逃跑；`cooldown_ticks` 默认 0，范围 0–24000，用于结束一次自定义捕猎后的冷却。关系不会越过已有的存活、队友、宠物主人等检查。呼救沿用原有反击及 Brain 适配。

内置 `environment` 条件支持 `line_of_sight`（默认 false）、`target_in_water`、`target_baby`、`tamed`（三者省略表示不限）、`maximum_light`（默认 1，范围 0–1）。复杂条件通过 Java 注册。供体详情和 JEI 的目标列表共用关系规则；运行时条件不会把某一时刻不满足的目标从静态目录删除。

## 产物与交互

`gameplay.outputs` 替换已有原生产物，键为 `egg`（产蛋）、`scute`（周期掉鳞甲）、`brush`（刷取）、`shear`（剪毛）、`milk`、`stew`。每项形如 `{"item":"minecraft:diamond","min":1,"max":2}`，或 `{"loot_table":"example:bio_product"}`。必须在 `item` 和 `loot_table` 中选一个。`min/max` 是闭区间整数，范围 0–64，默认都为 1。原生按器官计产的通道仍按器官数量独立抽取。剪毛替换产物后不再按颜色选择羊毛；未替换时保留原有颜色和数量。

战利品表使用 `minecraft:gift` 上下文，提供 `this_entity` 和 `origin`。数量表示抽表次数。每次事务最多 4096 个合法大小的物品堆。

新增周期产出适用于任意特性：

```json
"gameplay": {
  "production": [{
    "id": "example:gland_secretion",
    "item": "minecraft:slime_ball", "min": 1, "max": 2,
    "min_interval_ticks": 1200, "max_interval_ticks": 2400,
    "per_organ": "create_biotech:head"
  }],
  "interactions": [{
    "id": "example:collect_secretion",
    "ingredient": "minecraft:glass_bottle",
    "consume": 1, "tool_damage": 0, "cooldown_ticks": 200,
    "item": "minecraft:honey_bottle", "owner_only": true
  }]
}
```

周期与交互的 `id` 必填完整 ID，同一特性内不得重复。周期最小间隔默认 6000，最大间隔默认等于最小值，范围 20–24000 tick。`per_organ` 可省略，此时每周期产出一次；填写时按该部位的有效载体体块数量独立抽取，支持特性内部 `#私有部位`。新增周期仅在存活且 AI 启用时计发，首次加载先等待一个周期，不补发离线产出。计时保存在实体持久数据中，封装和存档都会保留。

交互原料支持物品 ID 或 `#物品标签`；`consume` 默认 1，范围 0–64；`tool_damage` 默认 0，范围 0–4096；`cooldown_ticks` 默认 0，范围 0–24000。`owner_only` 默认 true，有主人时仅主人可用。结果先经事件确认，再扣原料与工具耐久并交给玩家，放不下的物品掉落。创造模式遵循原有物品消耗规则。

同一特性配置非空 `interactions` 后，取代该特性内置的挤奶/刷取交互。自定义挤奶物种可通过供体绑定、下面的解剖规则与交互条目组合实现，无需修改牛/哞菇/山羊专用分支。其他特性的交互不受影响。

## 驯服、货箱与座位

对应特性的 `gameplay.equipment` 是规则数组，后匹配项优先：

```json
"gameplay": {"equipment": [{
  "donors": ["minecraft:pig"],
  "saddle_item": "minecraft:leather",
  "seats": 2,
  "seat_offsets": [[0,0,0], [0,0,-0.8]]
}]}
```

在 `tameable/livestock_tameable` 中使用 `tame_item`、`taming_chance`；在 `cargo_chest` 中使用 `chest_item`、`capacity`；在 `rideable` 中使用 `saddle_item`、`seats`、`seat_offsets`。物品支持 ID 或标签；省略字段沿用原有材料、概率、容量和座位数。`donors` 省略表示不限供体。

容量范围 0–54 格，分页界面和快捷转移使用相同规则。安装的实际货箱/鞍物品会保存，拆卸、死亡、回到手术台时返还原物品。重载缩小容量时保留仍有槽位的物品，溢出掉落；旧菜单因容器替换而失效。座位缩减后超出的乘客下车。

座位范围 1–16；自定义超过两席时必须给出每席偏移。`seat_offsets` 是相对第一个原生座位、沿器官座椅局部坐标的 `[x,y,z]`，每轴范围 ±16 方块。数量必须与 `seats` 相同，省略 `seats` 时由偏移数量确定。玩家位置、碰撞和渲染共用座位计算。

## 器官组合

```json
"gameplay": {"anatomy": {
  "replace_native": true,
  "group_by": "source",
  "any_of": [
    ["create_biotech:head", "example:antenna"],
    ["create_biotech:head", "create_biotech:eye"]
  ]
}}
```

外层 `any_of` 任一分支满足即可，分支内所有部位均须保留。`group_by` 可取 `species`（默认，同物种）、`source`（同一捕获来源）、`limb`（同一安装肢体，躯干单独成组）、`body`（整个装配）。先按安装范围筛选，再分组检查。部位支持完整 ID 和本特性 `#私有部位`。

`replace_native` 默认 false，在原条件之后追加；true 时替换原生器官组合要求，原有覆盖率、安装范围和腿数前提继续生效。有效载体限于完整分支中的部位，覆盖率和子特性载体随之收窄；缺失部位沿用现有诊断和界面提示。该入口不会凭空生成供体。

## 供体检测器和特殊攻击

自动检测仅在 `automatic_detection: true` 时启用，`values` 中标签及精确实体覆盖仍最后执行：

```json
"gameplay": {"detector": {
  "type": "create_biotech:attribute",
  "attribute": "minecraft:generic.armor", "scale": 0.5, "offset": 0
}}
```

检测器用于 `ability` 和 `number` 特性。属性不存在时不覆盖原自动结果；能力将非零结果视为 true。Java 检测器可读取供体自身的数据，返回 null 表示不提供事实。

`gameplay.attacks` 按已声明的变体 ID 指定实现：

```json
"gameplay": {"attacks": {
  "example:ice_ball": {"type":"create_biotech:projectile", "entity":"minecraft:snowball", "speed":1.6, "inaccuracy":3, "count":2}
}}
```

此条目需配合同一特性中的 `variants` 和 `values`；变体照常配置 `range`、`min_range`、`warmup_ticks`、`action_ticks`、`cooldown_ticks`。新增特性使用 `subtrait_set` 值类型。内置 `projectile` 支持弹速 0.01–20、散布 0–180、数量 1–64；实体必须能由实体类型创建为 Projectile。`direct_damage` 支持 `damage` 0–2048，默认 6。复杂构造通过 Java 攻击工厂实现。

新增攻击复用服务器调度、实际载体发射位置、目标合法性、视线、冷却、动作同步和投射物归属标记。自定义攻击抛出运行时异常时会记录错误，并对当前实体停用该攻击，直到装配或目录变化。内置攻击仍保留各自原生时序；其变体参数另支持：音爆 `damage/horizontal_knockback/vertical_knockback`，烈焰人 `burst_count/burst_interval_ticks`，恶魂 `explosion_power`，唾沫及雪球 `speed/inaccuracy`。

## Java API

`com.nobodiiiii.createbiotech.api.bionic.BionicGameplayApi`，`API_VERSION = 1`：

- `registerAttack(id, factory)`：编译到 `BionicSpecialAttack`，提供 `canUse/fire`。上下文包含实体、目标、特性 ID、变体 ID、有效载体和实际发射原点。自建投射物应设置 owner；Biotech 的实体加入事件会补充战斗归属。
- `registerDonorDetector(id, factory)`：编译到 `BionicDonorDetector`，纯读取供体，返回 Double 或 null。
- `registerRelationCondition(id, factory)`：编译到 `BionicRelationCondition`，输入仿生实体、供体类型和目标，返回是否符合。
- `registerAnatomy(id, factory)`：编译到 `BionicAnatomyEvaluator`，通过 `gameplay.anatomy.type` 使用。返回完整 `BionicTraitResult`，可禁用候选或收窄载体；不得添加原候选之外的载体。

在 common setup 的 `enqueueWork` 中、第一次目录加载前注册。重复或过晚注册会报错。工厂每次重载执行，实例在实体之间共享；不得在实例字段保存单实体状态。条件、检测器和解剖实现必须纯计算，攻击仅在服务端执行。

## Java 和 KubeJS 事件

Java 监听 `BionicGameplayEvent`，通过 `kind/phase` 区分操作。KubeJS 的对应组为 `BionicGameplayEvents`：

| 事件 | ID / 时机 |
| --- | --- |
| `interact` | `create_biotech:interact`，整个仿生实体右键处理前后 |
| `produce` | 原生通道用 `<特性ID>/<通道>`；自定义用条目的 `id`，结果确认前与成功产出后 |
| `specialAttack` | 攻击变体 ID，实际发射前后 |
| `equipment` | `create_biotech:equipment`、`create_biotech:remove_chest`，玩家直接安装/拆除前后 |
| `tame` | `create_biotech:tame`，尝试驯服前后；before.amount 为概率，after.amount 为成功 1 / 失败 0 |
| `pack` | `create_biotech:pack`，写入捕获箱实体载荷前后；此层可能没有玩家 |
| `release` | `create_biotech:release`，箱子释放实体生成前、成功生成并消费载荷后 |
| `assembly` | `create_biotech:assembly`，实体安装完整装配后，包含存档重建；仅 after |
| `traitChanged` | 特性 ID，判定结果变化后；amount 为生效 1 / 未生效 0，仅 after |

共有 `phase`（`before/after`）、`id`、`entity`、`assembly`、`player/target/hand`（不适用时 null）、`item`（副本）、`outputs`（副本）、`amount`、`result`。before 可以取消、设置产物和数值；interact 的 before 还可设置 `result` 接管交互。after 只观察已执行的操作。Java 使用 `cancel()`；KubeJS 使用事件系统的 `event.cancel()`。取消在本模组扣除耗材和生成行为之前生效；玩家直接剪毛也受此保护。发射器或其他模组直接调用 NeoForge `onSheared` 时，工具消耗由调用方决定，本事件仅控制剪毛状态和产物。周期产出被取消仍等待下一个周期，避免每 tick 重试。

```js
BionicTraitEvents.definitions(event => {
  event.gameplay('create_biotech:egg_laying', {
    outputs: {egg: {item: 'minecraft:gold_nugget', min: 1, max: 2}}
  })
})
BionicGameplayEvents.specialAttack('example:ice_ball', event => {
  if (event.phase === 'before' && event.entity.isInWater()) event.cancel()
})
BionicGameplayEvents.produce(event => {
  if (event.phase === 'before' && event.id.toString() === 'example:gland_secretion') {
    event.outputs = [Item.of('minecraft:slime_ball', 2)]
  }
})
```

`definitions.gameplay(trait,json)` 生成现有修改器目录资源；相同 trait 在该事件中只能调用一次，也可直接使用原有 `register/modify`。独立示例见 [examples/bionic-gameplay](../examples/bionic-gameplay/README.zh-CN.md)。KubeJS 接口继续按 [本地 2101 分支参考的版本说明](../ref/SOURCES.md#kubejs-extension-reference) 和项目锁定的 build.381 编译验证。

本轮不涉及 Mixin，不创建测试文件，不启动客户端。编译/构建与目录加载验证不能代替真实世界中的交互、渲染、第三方攻击实现及 KubeJS 脚本执行验证。
