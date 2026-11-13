# 仿生特性扩展：第一阶段

> 本文保留第一阶段的基础格式与历史边界。第 2–4 阶段已接入，当前能力、协议与示例见[完整扩展说明](bionic-traits-extension.zh-CN.md)。

适用：Minecraft 1.21.1、NeoForge 21.1.234。第一阶段实现特性 ID 化、动态目录、装配判定、完整定义同步与动态展示。内置 64 项特性保留原有效果实现。

## 能做什么

- 附属模组在自己的资源中提供特性 JSON，数据包也使用相同格式。
- 创建任意命名空间的特性，配置供体、载体、范围、继承规则、数值、效果集合、子特性与覆盖关系。
- 手术台、纸箱、实体共用当前目录判定，JEI 和调试报告发现新增条目。
- 登录与 `/reload` 同步完整特性目录，客户端不需要另外安装同一份特性数据包。
- 按 ID 查询自定义特性是否生效、覆盖率、数值和实际载体成员。

本阶段新增特性具有判定结果与展示，不会凭名称自动获得新的游戏行为。通用行为组件、KubeJS 专用事件、可注册的新条件和新组织角色属于后续阶段。`behaviors` 字段目前会明确报错，避免误以为尚未实现的行为已经执行。

## 定义文件

文件 `data/example/bionic_traits/conductive_tissue.json` 的特性 ID 为 `example:conductive_tissue`。其中 `example` 可以换成附属模组或整合包自己的命名空间。

```json
{
  "schema_version": 1,
  "display": {
    "name": { "text": "导电组织" },
    "description": { "text": "供其他扩展查询的导电组织标记。" }
  },
  "scopes": ["arm"],
  "value_kind": "ability",
  "inheritance": {
    "type": "coverage_threshold",
    "min_coverage": 0.5
  },
  "carrier": {
    "roles": ["ATTACK_HAND"]
  },
  "automatic_detection": false,
  "values": {
    "minecraft:zombie": true,
    "#example:conductive_donors": true
  },
  "carriers": {
    "minecraft:zombie": {
      "ATTACK_HAND": ["right_arm", "left_arm", "rightArm", "leftArm"]
    }
  }
}
```

数据包还需要有效的 `pack.mcmeta`。示例不会给僵尸组织附加电击效果；行为由后续组件或调用查询 API 的附属模组实现。

### 字段规则

| 字段 | 规则 |
| --- | --- |
| `schema_version` | 新定义必须为整数 `1`；仅内置特性原 ID 的配置可省略此字段 |
| `display` | `name`、`description` 接受文本组件，支持字面文本和翻译键；自定义特性缺省名称为完整 ID |
| `scopes` | 非空数组：`whole_body`、`head`、`torso`、`arm`、`leg`；全身不能和其他范围混用 |
| `value_kind` | `ability`、`number`、`effect_set`、`attack_effect_set`、`subtrait_set` |
| `inheritance.type` | `presence`、`coverage_threshold`、`coverage_scaled`；`special` 仅用于内置头部分类算法 |
| `inheritance.min_coverage` | `[0,1]`；存在型只能为 `0`。数值型采用 `coverage_scaled`，预算必须大于 `0`，缺省为 `0.5` |
| `carrier` | `roles`、`fallback`、`excluded` 使用现有组织角色；缺省或空 `roles` 表示全部组织 |
| `values` | 沿用现有供体格式，支持实体 ID 与 `#实体类型标签`；精确实体值覆盖标签值 |
| `carriers` | 沿用每种实体的组织角色到模型部件名或体块编号的映射 |
| `parameters` | 沿用现有非负数值参数；腿部默认需要一条达标腿链，可用 `min_legs` 调整 |
| `variants`、`effects` | 沿用原有子特性与攻击效果配置格式；集合现在按特性 ID 独立保存 |
| `overrides` | 完整特性 ID 数组；覆盖方通过判定后才抑制目标，循环覆盖边会被忽略并记录警告 |

`automatic_detection` 的原有探测逻辑只对应内置特性。自定义特性应使用显式供体值。

同一 ID 的定义遵循资源包优先级，较高优先级的文件整体替换较低优先级文件。第一阶段没有独立供体绑定文件和定向修改器；自定义特性的供体与参数在其定义文件中配置。

## 内置特性配置

`data/create_biotech/bionic_traits/<内置名称>.json` 可直接配置内置特性。其他命名空间中的定义必须声明 `schema_version: 1`；补充内置特性的供体应使用[独立绑定文件](bionic-traits-extension.zh-CN.md)。

带 `schema_version: 1` 的文件按其完整资源 ID 声明独立特性，不会被另一个命名空间的同名内置特性吸收。例如 `example:fire_immune` 是独立条目，不自动执行 `create_biotech:fire_immune` 的免疫逻辑。

需要为内置特性提供完整结构定义时，也可以在其原 ID 下使用新格式；必须保留原 `value_kind`，原有专属条件与效果实现继续按内置 ID 分派。普通门槛调整仍建议使用原有 `min_coverage` 字段。

特殊攻击的冷却存档使用完整特性 ID；声波攻击的初始冷却和近战后冷却使用同一标识。`effect_attack` 的攻击参数统一放在根对象 `attack_settings` 中，`effects` 条目配置效果 ID 与载体角色。

## Java 查询

```java
ResourceLocation id = ResourceLocation.parse("example:conductive_tissue");
BionicTrait trait = BionicTraitRegistry.find(entity.level(), id);
if (trait != null && entity.getBionicTraits().has(trait)) {
    double coverage = entity.getBionicTraits().coverage(trait);
    var members = entity.getBionicTraits().members(trait);
}
```

`BionicTrait.id()` 现在返回 `ResourceLocation`；相等性基于完整 ID。附属模组比较特性应使用 `equals`，不要使用对象身份或假定列表下标稳定。`BionicTrait.builtIns()` 仅列出内置默认值，运行时目录应使用 `BionicTraitRegistry.traits(level)`。

重载后应重新查找定义以读取最新元数据；旧 ID 句柄仍可用于结果查询，但被移除的特性不再生效。这里是阶段性接入接口，稳定的 API 发布与版本承诺在后续阶段完成。

子特性使用 `hasVariant(trait, ResourceLocation)` 查询，按完整 ID 匹配。

## 同步与重载

服务端与客户端分别保存快照。网络发送完整资源 ID 与 JSON，不再传输特性枚举下标；完整目录分块到齐后，客户端才一次性替换目录、刷新覆盖规则和供体缓存。JEI 使用动态配方查询与运行时条目更新，新增、修改、移除无需重新注册固定配方列表。

单个资源的 JSON 最多 128 KiB，每块资源总量最多 256 KiB，目录最多 4096 个资源、总量最多 8 MiB（计入资源 ID 与编码开销）。供体索引也按字节预算分块，大量特性的单个供体可拆成多条索引记录，客户端合并处理。网络协议由 36 升至 37，联机双方需更新到相同版本。

JSON 解析或结构定义错误会使本次特性目录加载失败，已有快照保留；内置特性配置中的单项无效参数会记录警告并采用缺省处理。原版资源重载不是跨系统事务，不保证其他重载监听器一起回滚。

本阶段不修改存档中的装配结构，不引入额外客户端效果执行器。
