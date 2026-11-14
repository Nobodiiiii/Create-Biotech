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

## 共享存储与旧存档

`SurgicalAssembly.Version=4` 将相同的供体模型数据集中到
`SharedSurgicalData.DonorTemplates`。`Sources[i].DonorTemplate` 是该列表的零基索引，
对应的模板保存 `MimicProfile`、`CubeCount`、`Seams` 和 `Anatomy`。
`Anatomy` 内的名称、模型节点及父关系使用字典，体块通过索引引用；头部与其他部位统一按数据规则识别，不再单独保存头部快照。
供体 UUID、保留部件、切割、偏移和旋转仍由每个 source 独立保存；不同供体可以共用模板，
特性覆盖率仍按原有供体身份计算。字典只在当前装配体内部共享，因此实体、纸箱和刷怪蛋可独立携带完整数据。

`AttackGeometry.CarrierOrigins[].CornerBits` 替代旧的 `Corners` 列表，按原顺序保存八个角点的
`X/Y/Z`，共 24 个 long。每个 long 是 `Double.doubleToRawLongBits` 的结果，读取时用
`Double.longBitsToDouble` 还原，不能将其直接解释为整数坐标；坐标精度、顺序和负零均保留。

手术台 `SurgicalFormatVersion=3` 使用同样的模板字典，并将胶合连接、组合和肢体关节分别集中到
`SharedSurgicalData.GlueJoints/Combinations/LimbJoints`，每个对象通过
`GlueRefs/CombinationRefs/LimbRefs` 的 int 数组引用。每个定义只解码一次，共用不可变运行时对象。
客户端同步在结构去重之后，仍对 NBT 内存计量达到 256 KiB 的整个快照自动使用 GZIP，
字典与对象列表一起压缩；小快照直接发送。

已发布的 beta.1/beta.2 装配体、手术台及解剖 NBT 在加载时一次性转换，后续只保存当前格式；未发布的开发中间格式不在兼容范围内，详见[存档兼容说明](surgical-save-compatibility.zh-CN.md)。客户端和服务端需同步更新，网络协议版本为 45。

以下是 2026-11-12 共享存储优化时的历史测量，不代表当前格式。使用当时存档中 `(-260,-60,-456)` 手术台的 186 个对象，以及 UTC `04:07:37.506`
诊断报告中的 171 个 source 做纯内存读写验证，未修改世界文件。手术台保留 11 个模板、172 条唯一胶合连接
和 1 个组合；报告中的装配体保留 2 个模板。该报告早于本次优化，仅用作真实数据样本。

| 样本 | 原始 NBT 字节：优化前 → 后 | GZIP 字节：优化前 → 后 |
| --- | --- | --- |
| 手术台对象及共享数据快照 | 1,206,323 → 184,231 | 39,703 → 26,799 |
| 装配体完整数据 | 317,060 → 164,734 | 22,325 → 20,749 |

验证覆盖旧格式加载、新格式读写、供体与编辑状态一致、共享对象复用、同步快照在 2 MiB NBT 预算内解码，
以及角点位级还原和几何网络往返；非法字典索引与角点长度会被拒绝。该验证不代表游戏帧率或运行时堆占用测量。

## 马腿未显示强健跳跃

2026-11-12 17:50:35（北京时间，`captured_at_utc` 为 `2026-11-12T09:50:35.094838400Z`）
的报告中，实体 UUID 为 `cd15e374-d641-41aa-8d94-b0e0d999feed`。装配体已有四条有效 `HIP` 腿链，
但 `traits.strong_jump` 为 `present=false`、`active=false`、`inactive_reason=NO_CARRIER`，
`bindings` 与 `members` 均为空，所以共用该结果的前端提示不会列出强健跳跃。

报告的 `assembly.SharedSurgicalData.DonorTemplates[0].Anatomy.ModelParts` 将马腿 8–11 号体块记录为
`rightHindLeg`、`leftHindLeg`、`rightFrontLeg`、`leftFrontLeg`；原先的
[`strong_jump.json`](../src/main/resources/data/create_biotech/bionic_traits/strong_jump.json)
仅匹配下划线形式，无法识别这些体块。
[`SurgicalCapturedRenderPlan.collectModelPartNames`](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalCapturedRenderPlan.java)
同时采集模型字段名和子部件名；非层级模型的根部件可能只有字段名。对应名称见本地 1.21.1 原版参考
[`HorseModel`](../ref/1.21.1/Minecraft/net/minecraft/client/model/HorseModel.java)、
[`QuadrupedModel`](../ref/1.21.1/Minecraft/net/minecraft/client/model/QuadrupedModel.java)、
[`FrogModel`](../ref/1.21.1/Minecraft/net/minecraft/client/model/FrogModel.java) 和
[`RabbitModel`](../ref/1.21.1/Minecraft/net/minecraft/client/model/RabbitModel.java)；这些参考不包含 NeoForge 补丁。

规则现同时接受马、骷髅马、僵尸马、山羊、青蛙及兔相应腿部的字段名与原有子部件名。
兔仍限于后腿和后足，旋风人的全身载体规则不变。已有数据重新加载规则后即可识别，无需重新打包装配体。

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


## 多个同源小部件的首次预览、放置与取出卡顿

2026-11-13 01:26:42（北京时间；报告 UTC 时间为 `2026-11-12T17:26:42.352816400Z`）的装配体包含
171 个来源、182 个保留立方体，其中 170 个来源各保留烈焰人的一个立方体；完整来源模型合计有 2222 个
立方体槽位。来源由多个原生供体及复制体组成，不能通过合并来源身份来优化，否则会改变来源和接缝语义。

针对这种结构，客户端准备流程采用以下处理：

1. 放置与打包测量直接从捕获的渲染计划读取几何，不再向丢弃的顶点缓冲重放模型。
   快照按 `presentCubes` 迭代实际保留的立方体；模型完整立方体数仍保留，用于拓扑一致性检查。
2. 手术台的正常状态按连通组收集依赖，成员几何与拓扑齐备后只提交一次整组落地任务。
   落地、变换、接触和包围盒的计算使用冻结数据在有界工作池执行，客户端校验版本后一次发布整组结果。
   手术中的交互预览继续使用原有预览规则。
3. 手持放置、手术台准备和取出测量共用每帧约 2 ms 的客户端准备预算。
   原生模型捕获/预览实体创建另有每帧最多 4 个昂贵步骤的限制，缓存命中的小部件不再各占一个冷构建名额。
   预算在步骤间检查，单次不可拆分的原生捕获仍可能超过预算；不会在队列满时转回主线程计算。
4. 服务器确认放置后，以匹配的档案、姿态、拓扑、位置和资源代次复用预览中的中性几何。
   取出测量按连通体成员状态、特性规则版本和资源代次缓存，悬停属性预览、复制及装箱共用准备结果。
   供体的中性几何、部件标签、座位及装备几何（包括无装备的结果）按档案缓存；不需要吸附姿态校正时，
   攻击几何只变更参考原点，避免再次求手臂体积。有姿态校正时重新测量校正后的几何。

首次装箱或复制时，若测量尚未完成，客户端保留一次点击并逐帧推进，完成后才发包。
切换物品、离开范围、打开界面、手术台内容变化或资源重载会取消待提交操作；超时才显示模型不可用。
资源重载、世界清理和状态失效会丢弃相关任务或缓存，过期结果不会覆盖当前手术状态。

需要验证实际帧耗时时，可使用 JVM 参数 `-Dcreate_biotech.surgery.profile=true`。
关注 `captureGeometry`、`placementGeometry(async)`、`groundGroup(async)`、`groundGroup(publish)`、
`placementGeometry(handoff)`、`attackGeometry(async)`、`bodyMetrics(async)` 与 `packedPreview(cacheHit)`。
`(async)` 阶段应在 `Create Biotech Surgical Worker` 上执行；同一连通组一次准备应只发布一次落地结果。
对比冷启动、重复放回/取出、在同一连通体不同小部件间切换，以及资源重载后的日志；编译通过不能替代运行时帧耗时验证。

## 第三方模型的体块名称缺失

2026-11-13 23:07:37（北京时间；报告 UTC 时间为 `2026-11-13T15:07:37.144381100Z`）的
`alexsmobs:bison` 装配体完整保留了 16 个体块，但来源 `Anatomy` 只有 `CubeVolumes`，没有
`ModelParts`。几何恢复已成功，名称采集却只识别原版 `ModelPart`，因此第三方模型的部件名没有进入快照。

`SurgicalModelPartNames` 现在在现有渲染采集期间提供通用的名称来源：

1. 按包含 `PoseStack` 和 `VertexConsumer` 参数的绘制方法发现模型节点，从模型字段名、映射键和节点自身的
   `name` / `boxName` / `boneName` / `partName` 字段收集原始名称，不按模组 ID 或模型库类名分支。
2. 临时代理节点上以接口声明的可迭代集合，观察实际遍历的几何或子节点，把对应名称附在输出顶点上。
   正常结束和绘制异常都会恢复原集合；不修改模型变换、集合内容或输出顶点。
3. 体块仍由原有顶点恢复流程生成；只有整个候选体块共有的名称才写入 `Anatomy.ModelParts`。
   子部件保留自己的名称，不把祖先 `body` 等名称铺到所有后代。头部后代沿子节点集合继承头部标记。

该方式适用于通过这些集合输出几何的模型。使用具体集合类型、直接数组或完全自定义绘制且无法观察部件来源的
模型仍可能缺名称；不根据体块位置猜测名称。名称采集也不会自动创建肢体关节，特性继续遵守既有安装和覆盖率规则。
更新后应重新采集供体；已有装配体可放回手术台重新打包，通过既有攻击几何测量补回保留体块的名称。
仅载入旧装配体不会重写已保存的名称和关节数据。
