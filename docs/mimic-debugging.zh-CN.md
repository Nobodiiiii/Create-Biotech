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
| `entity` | 实体类型、UUID、名称、位置、生命值、拟态状态、运行时主碰撞箱 `bounding_box`、真实眼部坐标 `eye_position` 和视线方向 `view_vector`。 |
| `block_attachment` | 装配体当前是否吸附、支撑方向、吸附格坐标、选中的底座、整体旋转四元数和窒息检测结果。与静态 `assembly.HitboxGeometry` 区分，用于核对实际旋转状态。 |
| `assembly_status` | `assembled` 为有效装配体；`invalid_or_missing` 表示装配数据无法加载；`unassembled_mimic` 为尚无手术装配信息的完整拟态生物。 |
| `assembly` | 服务端保存的完整 `SurgicalAssembly`，以 JSON 展开，包含供体档案、解剖标记、模型立方体、切缝、偏移、旋转、胶水连接、组合、关节及身体/攻击/碰撞几何。 |
| `assembly_summary` | 供体与部件数量、连通部件数量、有效肢体，以及每个供体的 UUID、类型、保留立方体 ID 和头部 ID，便于快速定位。 |
| `traits` | 每项能力的存在/生效状态、未生效原因、覆盖率、强度、数值、来源部件、子能力与攻击效果。`display_entries` 单独记录目标、效果、子项的展示证据及各自生效状态、唯一失效原因和缺件方案，用于核对同一特性拆分显示的多条提示，不参与运行时能力判定。保留无效能力的证据，供诊断缺头、槽位、覆盖率或翅膀配对等问题。 |
| `entity_snbt` | 完整实体存档的原始 SNBT 字符串，保留 NBT 数值类型、位集数组、UUID 数组及第三方数据，供检查装备、属性与运行状态。实体类型由 `entity.type` 提供。 |
| `mimic_profile` | 未装配拟态生物的生物档案；这类生物的 `assembly` 为 `null`，不伪造装配信息。 |
| `mod_versions` / `enabled_data_packs` | 模组版本与启用的数据包，用于复核能力规则来源。 |
| `captured_at_utc` / `game_time` / `trait_registry_generation` | 采集时间、世界 tick 与能力规则版本计数，用于区分多次采样。 |

建议先检查 `assembly_status` 和 `assembly_summary`，再结合 `traits` 中的 `inactive_reason`、`bindings`、`members` 与原始 `assembly` 分析。`missing_part_alternatives` 列出缺失部件的可选补齐方案，每个方案包含必须一起补齐的 `entity_type` 与 `role`。`bindings` 是实际保留、绑定该特性的体块，`members` 是装配判定后的贡献体块；未生效时保留判定证据。组合体提示取绑定特性的并集，命中其中任意体块均读取整个组合体的判定。`source` 是 `assembly.Sources` 的零基索引，`cube` 是该供体模型中的立方体 ID；摘要中的 ID 数组已将原始位集解码。JSON 中 NBT 布尔值按字节数值保存；如需还原精确 NBT 类型，应使用 `entity_snbt`。

报告仅采集服务端已有数据，不包含客户端渲染截图或运行时渲染模型。诊断报告会持续保留，可在排查结束后手动清理。

## 蜘蛛攀墙攻击高处目标后掉落

本次首份蜘蛛报告采集于 2026-11-11 23:39:23（北京时间，`captured_at_utc` 为
`2026-11-11T15:39:23.371477200Z`），实体 UUID 为 `b2b7dbb7-f3dc-4593-b35a-9ea1b4bdbade`。
报告紧接日志中 23:38–23:39 的攻击目标选择操作；目录里更早的报告属于其他装配体，不作为本次依据。
该蜘蛛的 `wall_climb` 已生效，覆盖率为 1，两条有效腿均为蜘蛛腿，智力为 `normal`，没有飞行或游泳能力。
采样时 `OnGround:1b`，报告不包含连续轨迹、攻击动作或导航历史，不能据此确定下落发生在哪个 tick。

代码对照发现两个中断点：`BionicWallClimbing.tick` 原先在正常抵达时调用 `stop()`，将移动控制器速度及输入清零；
`BionicAttackGoal` 与 `SlimeBionicMoveControl` 又会在普通智力攻击期间停止导航和移动。
原版 [WallClimberNavigation.tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/WallClimberNavigation.java)
正常抵达时只清除目标，保留 [MoveControl](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/control/MoveControl.java)
尚未落地的 `JUMPING` 输入；[SpiderAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java)
也不要求地面路径持续存在。贴墙移动被清零后，碰墙状态无法持续维持，攀爬上升随之中断，重力使身体下落。

修正将正常抵达与主动取消分开，无地面路径时也不再提交空路径清零旧指令；
追击可在路径结束后保持运行，空中贴墙攻击保留移动输入。
主动取消或失去攀墙能力仍清除移动指令；落地或离墙后的攻击停步规则照常生效。

## 吸附潜影贝受伤后不缩壳

2026-11-11 23:32:56（北京时间，报告 UTC 时间为 `15:32:56.193892600Z`）的潜影贝装配体
缩壳特性有效，受伤防御计时 `BionicShellGuard` 尚余 69 tick，却仍处于 `idle`。
报告同时记录已向下吸附、`OnGround:0b`：吸附定位不经过普通落地碰撞，原先仅接受 `onGround()`
的缩壳起始条件因此始终不成立。

缩壳流程已移除 `onGround()` 判定，空中受伤同样可以进入缩壳，不要求地面或方块吸附支撑。
修正位于 `SlimeBionicEntity.tickShellDefense`，不改变原版潜影贝开合动画。

## 倾斜潜影贝未旋转

2026-11-11 18:57:55 和 18:58:04（北京时间，报告 UTC 时间分别为 `10:57:55.153622300Z` 和
`10:58:04.548587100Z`）的两个潜影贝装配体中，第一只已有吸附状态，第二只的底座单独倾斜约 55°，
`block_attachment` 特性有效，但没有保存 `BionicAttachCell`。按第二份报告的真实角点重放旋转后，
底座底面能正确贴地，上壳最低点却进入支撑面下方约 0.16 格，原先的全身方块碰撞检查因此拒绝吸附。

吸附现在只要求有效支撑及世界范围，允许旋转后的身体嵌入方块。主碰撞箱和分部受击箱先旋转实际部件角点，
再重算轴对齐包围盒，避免旋转旧包围盒中的空白区域而额外膨胀。窒息检测随整体旋转变换原版眼部采样区域，
继续由正常的 `LivingEntity` 窒息伤害流程处理，不因吸附而免疫；非窒息方块和 `noPhysics` 等原版例外仍有效。

吸附时的眼部位置优先读取保留头部的三维中心，缺头时回退到身体眼高，再应用整体吸附旋转。
F3+B 自行构造 `(0, eyeHeight, 0)`，不调用实体眼部坐标；因此单独修正蓝线起点与红色眼平面，
并使实体眼部坐标、视线向量和窒息采样使用同一头部位置及旋转。
依附面失效时，有末影瞬移特性则立即调用一次现有 ±32 格随机瞬移流程，不先尝试原地换面；
传送遵守 NeoForge 事件取消及目标重定向，落点重新检查六面支撑，成功后播放两端粒子并对齐底座。
无末影瞬移特性时，方块吸附不再自行传送，而是立即解除移动、重力、推挤、击退和整体朝向限制，
逐 tick 检测底座接触点一格内最近的有效表面，找到后重新吸附。传送失败或取消也会解除限制，下一 tick 恢复附近检测，
不会因同一次失去支撑而每 tick 重复传送。六面支撑仍参照
[Shulker.canStayAt / findAttachableSurface](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java)，允许身体嵌入方块。

## 鞍座与玩家位置不一致

先从 `entity_snbt.BionicEquipment` 对应的 SNBT 内容确认鞍绑定的 `Source` / `Cube`，再核对
`assembly.AttackGeometry.CarrierOrigins` 中同一部件的 `Seat`、`RearSeat`、`SeatRotation` 和
`SaddleBounds`。旧报告可能包含绑定到头、嘴或腿的鞍；原版供体现在只允许在 `body` 部件装鞍。

2026-11-08 00:49:21（北京时间，报告 UTC 时间为 `2026-11-07T16:49:21.845055300Z`）的双驴装配体中，
下方供体 `Source=0` 的鞍绑定在 `Cube=4`（嘴）和 `Cube=10`（前腿）。两个鞍的包围盒几乎重合，
座位却分别投影到了嘴部和腿部。这是装备定位查找不到 `body` 标签后错误地使用目标部件作为原点造成的：
鞍的平移量变成零，而玩家仍跟随目标部件。

装备定位现在优先使用 `body` 标签，缺少标签时根据原生乘客锚点到立方体的距离恢复供体承载部件。
鞍模型、装备包围盒和座位使用相同的平移量，并保留原版乘客锚点相对鞍的偏移。
座位和装备包围盒在打包时冻结到装配数据；此前生成的装配体需要放回手术台重新打包，才能刷新这些数据。

原先丢失 `body` 标签的原因是采集时使用了不含 `CubeDeformation` 的 `Cube.min/max`，无法匹配驴等供体实际膨胀后的躯干。采集现在匹配实际输出的顶点；重新打包会将重新采集的部件标签一并传回服务端，补齐已有供体的解剖信息。旧存档中不符合新规则的鞍会掉落返还，重新打包后可在身体上重新安装。

## 单躯干难以上座与骑乘平移

2026-11-08 13:39:31（北京时间，报告 UTC 时间为 `2026-11-08T05:39:31.307512700Z`）的装配体只保留驴的
`Source=0, Cube=5`（`body`），可骑乘已生效，鞍绑定正确。座位高度为 `0.516223553`，而原版玩家的
`VEHICLE` 挂点距离脚底 `0.6` 格（见 [Player](../ref/1.21.1/Minecraft/net/minecraft/world/entity/player/Player.java)）。
将玩家站立碰撞箱直接放到座位上，会使箱底低于身体底部约 `0.083776447` 格，导致身体落地时上座检测失败。

原版 [HumanoidModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/HumanoidModel.java) 会将骑乘玩家的双腿向前弯曲，
因此骑乘碰撞箱从座位平面起算，保留头顶高度与座位旋转；玩家位置和视角继续按原生挂点计算。
此外，乘客碰撞在身体移动前裁剪位移时，必须同时保留碰撞方向、落地状态及受阻轴的速度处理，
避免下落已被乘客碰撞裁剪为零，身体却一直判定为空中，从而只能平移、无法再次触发跳跃。
该调整在运行时生效，此报告中的已有鞍座可直接使用。
