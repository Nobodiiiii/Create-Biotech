# 拟态生物装配诊断

拟态生物调试棒是隐藏工具，不加入创造物品栏、创造搜索、JEI 列表或任何配方。通过指令获取：

```mcfunction
/give @s create_biotech:mimic_debug_stick
```

外观为带附魔光效的原版木棒。主手或副手持棒，右键拟态生物即可生成报告；点击装配体的任意可交互部位均可。调试点击会优先于实体的装备、骑乘等交互处理，不消耗调试棒。每名玩家最多每秒导出一次。

报告写入**服务器世界存档**中的以下目录，单人游戏则是本地世界存档：

```text
<世界存档>/create_biotech/diagnostics/mimics/<UTC时间>_<实体UUID>_<唯一后缀>.json
```

每次导出保留独立文件。成功后聊天栏显示完整路径，点击路径可复制；多人服务器的路径指向服务器文件系统。写入失败会显示提示，异常详情记录到服务器日志。

## 交给 AI 的数据

将对应的 JSON 文件提供给后续诊断。报告使用 UTF-8，当前格式为 `create_biotech:mimic_diagnostic`，`schema_version` 为 `1`。

| 字段 | 内容及用途 |
| --- | --- |
| `entity` | 实体类型、UUID、名称、位置、生命值、拟态状态。 |
| `assembly_status` | `assembled` 为有效装配体；`invalid_or_missing` 表示装配数据无法加载；`unassembled_mimic` 为尚无手术装配信息的完整拟态生物。 |
| `assembly` | 服务端保存的完整 `SurgicalAssembly`，以 JSON 展开，包含供体档案、解剖标记、模型立方体、切缝、偏移、旋转、胶水连接、组合、关节及身体/攻击/碰撞几何。 |
| `assembly_summary` | 供体与部件数量、连通部件数量、有效肢体，以及每个供体的 UUID、类型、保留立方体 ID 和头部 ID，便于快速定位。 |
| `traits` | 每项能力的存在/生效状态、未生效原因、覆盖率、强度、数值、来源部件、子能力与攻击效果。保留无效能力的证据，供诊断缺头、槽位、覆盖率或翅膀配对等问题。 |
| `entity_snbt` | 完整实体存档的原始 SNBT 字符串，保留 NBT 数值类型、位集数组、UUID 数组及第三方数据，供检查装备、属性与运行状态。实体类型由 `entity.type` 提供。 |
| `mimic_profile` | 未装配拟态生物的生物档案；这类生物的 `assembly` 为 `null`，不伪造装配信息。 |
| `mod_versions` / `enabled_data_packs` | 模组版本与启用的数据包，用于复核能力规则来源。 |
| `captured_at_utc` / `game_time` / `trait_registry_generation` | 采集时间、世界 tick 与能力规则版本计数，用于区分多次采样。 |

建议先检查 `assembly_status` 和 `assembly_summary`，再结合 `traits` 中的 `inactive_reason`、`members` 与原始 `assembly` 分析。`source` 是 `assembly.Sources` 的零基索引，`cube` 是该供体模型中的立方体 ID；摘要中的 ID 数组已将原始位集解码。JSON 中 NBT 布尔值按字节数值保存；如需还原精确 NBT 类型，应使用 `entity_snbt`。

报告仅采集服务端已有数据，不包含客户端渲染截图或运行时渲染模型。诊断报告会持续保留，可在排查结束后手动清理。

## 鞍座与玩家位置不一致

先从 `entity_snbt.BionicEquipment` 对应的 SNBT 内容确认鞍绑定的 `Source` / `Cube`，再核对
`assembly.AttackGeometry.CarrierOrigins` 中同一部件的 `Seat`、`RearSeat`、`SeatRotation` 和
`SaddleBounds`。装备绑定的部件可能是头、嘴或腿，不一定是供体躯干。

2026-11-08 00:49:21（北京时间，报告 UTC 时间为 `2026-11-07T16:49:21.845055300Z`）的双驴装配体中，
下方供体 `Source=0` 的鞍绑定在 `Cube=4`（嘴）和 `Cube=10`（前腿）。两个鞍的包围盒几乎重合，
座位却分别投影到了嘴部和腿部。这是装备定位查找不到 `body` 标签后错误地使用目标部件作为原点造成的：
鞍的平移量变成零，而玩家仍跟随目标部件。

装备定位现在优先使用 `body` 标签，缺少标签时根据原生乘客锚点到立方体的距离恢复供体承载部件。
鞍模型、装备包围盒和座位使用相同的平移量，并保留原版乘客锚点相对鞍的偏移。
座位和装备包围盒在打包时冻结到装配数据；此前生成的装配体需要放回手术台重新打包，才能刷新这些数据。
