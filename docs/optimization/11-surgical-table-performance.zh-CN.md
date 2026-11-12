# 11. 新手术台系统性能审计与优化方案

## 结论

本轮审计基于 `1.21.1` 分支、Minecraft 1.21.1、NeoForge 21.1.234、Create
6.0.10-281。Create 本地参考为 `ref/1.21.1/Create/` 的官方 6.0.10 tag；Ponder/Outliner
行为参考 `ref/1.21.1/Ponder/common/src/main/java/net/createmod/catnip/outliner/Outliner.java`。后者是
1.21.1 development branch快照，并非已证明与项目 Ponder 1.0.82 二进制逐提交一致，本轮只借用其
`showLine/keep/ticksTillRemoval` 生命周期契约，不复制版本敏感 API。

掉帧不是单一复杂模型造成的，而是五类成本叠加：

1. 手术用 Mixin 原本进入所有 LivingEntity 的每个 ModelPart cube 和 RenderLayer；无手术上下文时仍会
   ThreadLocal 查询，并为每个 cube 创建转发 lambda。局部功能因此污染了全局生物渲染热路径。
2. `SurgicalTableRenderer` 无条件返回 `shouldRender=true` 和 `shouldRenderOffScreen=true`，绕过距离和视锥
   裁剪；远处、镜头背后的复杂来源实体仍会走完整 LivingEntityRenderer。
3. 连通桌面 BFS（上限 1024 方块）被渲染包围盒、投影模式、放置预览、剪切预览、粘合预览和遮挡判断
   在同一帧重复调用。
4. 手持工具时每帧对所有已缓存 cube 做 6 个面的精确射线求交，并可能为多个候选执行世界方块 clip；
   高亮又为每条边重复刷新 Outliner 参数和颜色对象。
5. 静止鼠标下，放置、剪切移动和粘合移动仍每帧重新收集占地、搜索网格、平移全部 cube/contact、重建
   AABB 和轮廓；成本随桌面 tile、subject、cube、seam 数共同增长。

这是源码静态热点审计，不冒充 Spark/JFR 实测。代码已经完成第一阶段结构性优化；发布前仍应按本文末尾
场景采样 profiler。

## 已实施优化

### PERF-SURGERY-01：隔离全局 Mixin 热路径（P0）

- `ModelPartRenderMixin` 在无手术上下文时直接调用原始 cube compile，不再创建 lambda。
- `LivingEntityRendererMixin` 在无手术上下文时直接渲染原 RenderLayer，不再建立层上下文或 buffer 包装。
- `SurgicalModelRenderContext` 增加 O(1) active fast path；活跃手术渲染内部也直接复用当前 context，避免每个
  cube 的 ThreadLocal 查找。

预期结果：没有手术台、或手术台未被渲染时，手术系统对普通世界生物的新增成本缩减为 mixin wrapper
方法加一次 volatile 判断，不再产生 per-cube 垃圾。

### PERF-SURGERY-02：恢复可见性和距离裁剪（P0/P1）

- subject 所属 controller 继续用整个连通桌面与已测模型的联合 AABB 参与 BER 调度，保证玩家靠近大型桌面
  远端、controller 已离开所在 render section 时，远端 subject 仍能被唤醒。
- controller 被调度后，再按每个 subject 的精确世界 AABB 做视锥裁剪；冷缓存使用持久化占地与保守高度范围，
  不会因为还没有模型测量值而永远无法进入准备队列。
- 距离判断改为相机到整个桌面/模型包围盒的最近距离，而不是永远 true，也不会只按远端 controller 坐标
  错误裁掉大型桌面另一端的 subject。
- renderer 一帧只取一次 subjects 只读视图，不再反复 `List.copyOf`。

预期结果：镜头背面和默认 BER 视距外不再执行来源实体模型、渲染层与手术 cube 过滤。

### PERF-SURGERY-03：缓存桌面拓扑和 subject 索引（P1）

- 客户端每个 table BE 缓存连通平面、平面 AABB 和是否含投影手术台 5 tick；拓扑变化最多延迟 0.25 秒
  反映，交互服务端仍重新验证，不降低安全性。
- renderer、输入、预览、遮挡和 outline 判断共享该快照。
- `WorkArea.containsTile` 使用已有 long key set，替代 `tiles().contains` 线性查找。
- BE 增加 subject id 与 persistent UUID 索引；频繁 `getSubject` 从 O(subjects) 降为平均 O(1)。

1024 tile 上限场景由“同一帧多次 BFS + 多次 tile stream”降为“活跃起点每 5 tick 至多一次 BFS”。

### PERF-SURGERY-04：射线 broad phase 与单次遮挡（P1）

- 数据更新时预计算整 subject AABB 和每个 oriented cube 的 AABB。
- 每帧先做 subject AABB，再做 cube AABB 的无分配 segment/slab 测试；只有候选才执行 6 面精确求交。
- 世界 `level.clip` 从每个更近候选一次推迟到最终最近命中一次。
- 连通分量/直接连接的高亮结果按 table revision、render revision、subject/cube id 缓存。
- 同一 client tick 内，工具模式、视线、pending glue和 geometry generation均未变化时，整套选取结果直接复用；
  高 FPS 不再对同一条 ray重复窄相位。

射线完全不穿过手术模型时，复杂度从 O(全部可见 cube × 6 面) 收敛到 O(可见 subject) 的少量标量比较。

### PERF-SURGERY-05：消除静止预览重复规划（P1）

- 新 subject 放置在来源、方向、目标、桌面 revision 和 work area 均未变化时复用 `PlacementPreview`。
- 剪切/粘合移动目标未变化时复用布局与已平移几何，不再重复 `snapComponent`、占地收集、contacts 平移和
  render bounds 重建。
- 强力胶第二点预览按 pending request、目标 cube、精确 hit 和 table revision 复用。
- Outliner 只在边集合变化时重写端点/宽度/颜色；同一选择每 client tick 只 `keep` 一次。操作栏提示也从
  每 render frame 降为每 client tick 一次。

这部分直接针对高刷新率场景：120/240 FPS 不应把相同布局求解重复 6/12 次每游戏 tick。

### PERF-SURGERY-06：复用来源模型 cube id（P2）

- 同一 preview entity/renderer 的 source cube 和 layer cube identity 映射跨帧复用。
- 非 geometry capture 帧不再创建空 geometry ArrayList/BitSet，render-layer deque 改为按需创建，context
  stack backing deque保留在线程中复用。
- renderer 资源对象变化时丢弃对应 id cache，避免资源重载后沿用旧模型 identity。

### PERF-SURGERY-07：可见组分帧冷启动（P0）

- 进入视野时只准备视锥内 subject；若该 subject 通过胶点或 combination 与其他 subject 连通，则整组进入
  同一个准备队列，距离按各 subject 自己的世界 AABB 排序，不按 controller 距离排序。
- 每帧主线程最多开始 4 个冷 subject，并设置 2 ms 的软预算。共享同一来源模型的 pending plan 只轮询一次，
  不消耗后续帧的冷构建名额。
- 来源实体 renderer 的录制保留在客户端线程；耗时的 cuboid 恢复、材质可见性分析和不可变 render plan 构建
  移到后台。连通组内所有 geometry、contact topology 与 render revision 就绪前整组不提交显示，避免半组使用
  旧 grounding、半组使用新 grounding。

### PERF-SURGERY-08：有界后台队列与加权几何 LRU（P1）

- render plan 和大型 contact topology 共用模组私有、低优先级、最多 2 worker、128 队列深度的 executor；
  队列满时后续帧重投，不回退到公共 ForkJoinPool，也不在渲染线程同步计算。
- 待构建 render plan 上限为 64；资源重载以 generation 隔离旧 future 和纹理 alpha mask，旧任务完成后不能把
  过期结果重新写入缓存。
- `TABLES` 从固定 tick TTL 改为按 cube、seam、contact/face point 估算权重的 LRU；最近 1 秒正在渲染或作为
  连通 grounding 依赖的 geometry 受保护，大桌子短暂离开视锥不会立刻付出完整重建成本。
- 无旋转且无位移的 cube 直接复用原 geometry；其余变换使用预分配循环，减少 stream/lambda 和无效 corner
  对象分配。

### PERF-SURGERY-09：按拓扑修改失效连通缓存（2026-11-12）

本节来自 [手术台 Spark 实测](https://spark.lucko.me/Kid9myXPce)：手术台渲染占渲染线程采样的
75.53%，其中连通签名计算占 64.37%。先前的缓存每次查询仍扫描全部 subject，并深度哈希组合成员，
可见关联 subject 又逐个发起相同查询。

- 删除 table/subject 的 `connectionSignature`；subject 归属、保留体块、切缝、胶点和组合变更通过一个
  `invalidateConnections()` 入口失效普通图、参数化图和连通组缓存。读档、网络同步和跨桌迁移沿用
  subject 的注册/移除入口，旧 subject 脱离桌面时解除归属。
- 每帧只取一次连通组映射，所有同组 subject 共享成员集合；按集合身份合并准备工作，每组只检查一次
  就绪状态，删除逐 subject 的集合复制、集合内容哈希和 renderer 内重复维护的 subject 索引。
- 保留参数化图对调用方可变 cut override 的副本，以及组级渲染就绪检查，避免预览参数后续变更污染
  缓存或出现半组刷新。

### PERF-SURGERY-10：纸箱查询与完整 NBT 快照分离（2026-11-12）

[装箱后的 Spark 实测](https://spark.lucko.me/VEQCeL2wmn) 中，完整 NBT 快照读取占渲染线程采样
57.01%（复制 40.89%、计重 15.91%，两者包含在快照读取中）。采样后存档里的目标纸箱计重约
2.18 MiB；Guava 默认把 8 MiB 预算分为四段，每段只有 2 MiB，该条目每次载入后都会立即被淘汰。

- 完整只读快照缓存显式使用单段，保持 8 MiB 总预算。
- 生物存在性、类型、血量和 prototype 判定共用按不可变 `CustomData` 身份缓存的轻量元数据；弱 key
  的 value 不持有组件或实体 NBT，组件替换即自然切换结果。
- 元数据通过 `CustomData.read` 与 `Codec.PASSTHROUGH` 只读查询需要的字段；不能换成
  `CompoundTag.CODEC`，后者会复制整个 compound。对应本地 API：
  `ref/1.21.1/Minecraft/net/minecraft/world/item/component/CustomData.java`、
  `ref/1.21.1/Minecraft/net/minecraft/nbt/CompoundTag.java`。
- 图标描述符不再持有完整实体 NBT；只有实际加载实体时复制捕获数据，已有烘焙图标可直接复用。
  空箱判定先排除无关物品，放出生物沿用统一加载入口的血量恢复，删除重复读取和设置。

本次 `compileJava --offline`、`build --offline` 通过，现有 80 项测试全部通过；没有新增测试文件。
未启动客户端，修复后的平均 FPS 与 1% low 仍需在相同场景重新采样。

### PERF-SURGERY-11：拟态生物 / 手术台全量审计（2026-11-12）

范围是 `content/surgery/**`、`content/slimemimic/**`、`entity/**`（约 4.5 万行），按"过度设计、冗余防御、
性能"三类逐文件审计，每条结论都对照调用方核实后才落地。这是静态审计，没有新的 Spark 采样；下面的
"每帧 / 每 tick"均指调用频率，不是实测耗时。共改动 24 个文件，净减 88 行。

逐帧 / 逐 tick 路径：

- **仿生体动画器**（`SlimeBionicAnimator`）：躯干支点、静止游泳俯仰、腿部成员集合、步幅上限与步频系数只
  依赖装配，移入 `Rig` 随缓存条目计算一次；肢体层级只有"肩/髋 → 肘/膝"两层，删除环检测数组和逐立方体
  深度表，改为按深度预排序后依次覆盖；逐帧 `Map.copyOf` 删除；未在攻击时不再查询攻击扇区；没有翼时跳过
  翼成员查找；不在水中时跳过游泳混合。
- **仿生体渲染器**（`SlimeBionicRenderer`）：静态偏移与动画帧的"合并 + 转到渲染坐标系"合成一次遍历（每个
  source 每帧 10 次 map 构建降为 4 次）；删除 `UPRIGHT_OFFSETS` / `UPRIGHT_ROTATIONS` 两个静态缓存和
  `FirstPersonSourceState`，直接读缓存条目里已有的同一份数据；引信未点燃时不查特性参数。
- **`SurgicalCubeRotation.rotate`**：改为标量展开，运算顺序不变（结果逐位一致），每次调用由 7 个 `Vec3` 降为
  1 个；它位于逐动画立方体、逐旋转顶点的路径上。
- **手术台方块实体**：没有 subject 的台面格直接返回单格渲染包围盒（原先每格都是 17³ 的盒子，整张桌子的
  每一格每帧都被派发到渲染器再空转返回）；连通平面重扫后若格子未变则保留原实例，否则放置预览每 5 tick
  会因 `WorkArea` 身份变化而整体重新规划；`includeClientRenderBounds` 在已被包含时不再分配。
- **客户端处理器**：空纸箱的连通选区缓存改为两个变体各一槽（原来单槽在同一次拾取内被两种变体轮流覆盖，
  从不命中）；HUD 提示在同一射线、同一几何代数下复用选区已算出的拾取结果；放置探测改用已解析的临时
  移动缓存；粘合剪切预览静止时先返回、不再每帧重算剪切计划；`presentCubesFor` 与
  `SubjectGeometryState.matches` 的逐帧 `BitSet` 克隆去除。
- **手术工具包**（`SurgicalKitItem`）：`selectedTool` 改为按 `CustomData` 身份零拷贝读取工具 id（原先每次
  调用都经过整包自定义数据快照，每帧约 30 次）；`TemporaryMove` 不再在构造和每个访问器里深拷贝源状态与
  装配 NBT；`Tool.byId` 用静态表。
- **特性系统**：`BionicTraitResolver.revision()` 在计数器不变时返回同一实例；`BionicTraitSet.has` 用预先
  算好的 `EnumSet`；附着判定先看同步的格子再查特性；捕猎目标只在拥有该特性时才做范围扫描；`getMind`
  随特性集一起缓存；无装备、无乘客时装备 tick 直接返回；振动监听的位置源复用；部件名正则预编译，四处
  相同的校验合并为 `BionicAnatomySnapshot.validPartName`。
- **其它**：`matchesObservedTopology` 不再为已校验过的拓扑重跑 `validTopology`；
  `isInternalCombinationJoint` / `rotatesTogether` 直接从端点所属 subject 查询，不再每次收集整桌的组合；
  `AttackGeometry.arms()` 单侧时直接返回原列表；翼动画的部件解析按（档案、标签、左右）缓存。

冗余的防御性代码：`ComponentGroup`、`SeamCutPlan` / `GlueCutPlan` / `BatchCutPlan` 及对应状态记录在构造期
对刚生成、独占的集合再做深拷贝，全部改为直接接管；`SurgicalConnectionGraph.Component` 的第三份 map 与
`members()` 的二次拷贝；`groundingDependents`、动画采样、`CubeBox.of` 中不可达的判空。

过度设计：`packedAssembly` 包装层（带一个未使用的参数）与 `compositeAssembly` 合并；三个仅转发的
`*ForSupport` 方法；装箱、临时搬运、复制三处重复的"校验 → 对齐 → 写入几何 → 建实体"合并为
`measuredBionic`；`packComponent` 里与 `removeGroup` 相同的移除循环；`read()` 的两次清空重建；
`SlimeBionicAnimator.resolve` 的恒定参数与不可达回退；若干无调用方的方法和字段。

验证：`compileJava --offline` 通过，现有 80 项测试全部通过；没有新增测试文件，没有改动 mixin，未启动
客户端。视觉和手感仍需按下方矩阵抽样。

## 正确性边界

- 服务端 packet 距离、plane、占地与拓扑验证未删除；客户端缓存只影响显示和候选计算。
- 视锥使用包含整个连通桌面、已测 cube bounds 和未测模型保守 fallback 的 AABB；大型桌面另一端仍可见。
- 最终遮挡只做一次的依据是同一条有限射线上，更近的真实方块遮挡也必然遮挡更远候选；属于同一手术桌
  平面的桌面方块继续按原规则豁免。
- table 平面客户端缓存 TTL 为 5 tick；服务端交互仍即时扫描。因此方块刚替换时最多出现短暂预览旧态，
  不会让非法请求通过。
- cube id cache以 preview entity 和 EntityRenderer identity共同约束；level unload会统一清空。

## 后续可选阶段

以下改动暂不在没有 profiler 证据时继续扩大：

1. `orderedCells` 目前为每次新目标创建 `tileArea × 16` 个 GridCell并全排序。若“大桌面上持续拖动预览”仍是
   热点，应实现按 work-area topology缓存的格点索引，或严格按距离的增量 best-first 搜索；不能简单改为
   不保持距离顺序的环扫描，否则会改变“最近合法槽位”语义。
2. 多个可见 subject仍各自调用一次原实体 `LivingEntityRenderer`。若 GPU/vertex submission占主导，可增加
   可配置手术台专用视距/LOD；不要缓存动态 VertexBuffer，除非解决资源重载、RenderType、动画层和模组实体
   renderer兼容生命周期。

PERF-SURGERY-11 审计中已核实、但因为改动面大、不是严格等价或需要先有 profiler 证据而没有实施的项：

3. `SurgicalCapturedRenderPlan`：`renderSlime` 每个立方体每遍新建 `PoseStack` 与若干矩阵（约 20 个对象）；
   世界中的活体拟态生物每帧重建整套 plan（`shouldPreserveSource` 为 O(n²) 且每对分配 15–20 个对象）；
   带壳防御的仿生体每帧带拓扑重新捕获并反射扫描模型字段。这三处很可能是渲染线程上剩余的最大分配来源。
4. `SlimeBionicGroundNavigation.followThePath` 的直线捷径扫描每个导航 tick 都把失败过的候选重试一遍，在
   走廊和拐角处估计每实体每 tick 0.3–0.5 ms。节流会让捷径最多晚几 tick 生效，需要取舍。
5. 客户端处理器：粘液球悬停时对每个候选立方体做一次全桌配对扫描；剪刀拾取对每个胶点 / 关节先做凸裁剪再
   判射线；对称魔杖第三步每次拾取都重新规划；连通组接地对每个成员重算整组；`PendingCut` 与
   `PendingGlueCut` 是两份几乎相同的状态机；控制器迁移验证层（`OwnerHandoffKey` 等）只为省一次重新接地。
6. `SurgicalAssembly`：一个 `Source` 在加载路径上被校验 5–6 次后再深拷贝；`load()` 通过只为它存在的
   `with*` 方法构造最多 7 个临时装配；约 100 行无调用方或仅测试使用的重载；单体 `components()` 走通用多体
   图而不是并查集；`validTopology` 为每条缝装箱一个 `Long`。
7. 手术台数据包：12 个包类里重复的编解码与 10 份相同的"旁观 / 距离 / 平面 / 控制器"前置检查；处理器校验后
   方块实体再校验同一批条件；`Action.PLACE`、`CUT_CUBE_CONNECTIONS` 及其 9 参数重载无人发送（删除会移动
   枚举序号，需要同时提升 `NETWORK_VERSION`）。
8. 实体：`setAssembly` 把刚写出的 NBT 再解析一遍并解析两次特性；每个仿生体预先构造 21 个命中部件实体；
   `refreshLocomotion` 每 tick 执行两次；`getBoundingBoxForCulling` 每帧 24 个 `Vec3`；
   `BionicSpecialAttacks.choices()` 每 tick 重建。
9. `SurgicalClientTopology` 中约 170 行只被死代码引用；`SurgicalSourceModelRenderer.render` 的 10 个重载里
   6 个无调用方或只是转发，`yaw` 恒为 0、`partialTick` 未被读取。

审计中顺带发现、不属于本轮范围的疑似缺陷（均未改动）：

- `rewriteVillagerTrades` 配置只在 `setSlimeMimic` 检查，四个 mixin 钩子无条件重写，关闭配置不生效。
- SIMPLE 智力选臂在 `SlimeBionicEntity.selectArm` 内联了一份与 `SurgicalCombatCalibration.highestDpsArmIndex`
  不同的公式；文档和测试描述的是后者，运行时用的是前者。
- 带 NBT 复制的手术台（Ctrl + 中键、`/clone`、蓝图）并入同一平面后，`adoptSubjects` 只处理 id 冲突、不处理
  `persistentId` 冲突，服务端会出现两个 subject 共用一个持久 id，直到下次读档去重。
- `SurgicalSourceModelRenderer.PREVIEW_PROFILES` 的值强引用了 `PREVIEWS` 的键，两个弱表互相钉住，条目只在
  换世界或资源重载时释放。
- `SlimeMimicDeathHandler.PENDING` 中所属世界不再 tick 的条目不会被移除。

## 性能验证矩阵

推荐使用相同相机路径、固定渲染距离、关闭/开启 VSync各跑三次，并分别记录 client tick、render、分配率、
1% low；Spark 用于宏观 call tree，JFR/async-profiler 用于 allocation 和方法级 CPU。

| 场景 | 规模 | 重点指标/期望 |
| --- | ---: | --- |
| 无手术台，普通实体群 | 200/500 entity | `ModelPartRenderMixin` 不出现 lambda allocation；普通实体 render无可测回退 |
| 手术台在镜头后/视距外 | 1/16 table | `SurgicalTableRenderer.render` 调用为 0 |
| 单 subject静止观察 | 16/64/128 cube | 每帧无 cube-id IdentityHashMap重建；分配率稳定 |
| 剪刀/纸箱瞄准空处 | 16 table × 128 cube | 精确 `intersectQuad` 大幅少于总 cube×6；每帧最多一次 block clip |
| 静止放置/剪切/粘合预览 | 1024 tile，多个 subject | 同一游戏 tick不重复 layout search/geometry translate |
| 持续拖动预览 | 64/256/1024 tile | 定位 `orderedCells` 是否成为剩余首要热点 |
| 频繁进出视锥 | 64 cube subject | geometry capture次数、缓存回收和 1% low无周期性尖峰 |
| 首次看向大型桌面 | 64+ subject、混合模型 | `capture(plan-input)` 分散到多帧；`build(plan)` 仅出现在 Surgical Worker |
| 远离 controller 操作连通组 | 跨多个 table tile 的胶合 subject | 远端仍渲染/可选取；同一连通组不出现半刷新 grounding |

验收建议：

- 无手术台实体群相对改动前 FPS/1% low不再显著下降；
- 镜头背向手术台时 renderer CPU接近零；
- 静止预览规划方法每 client tick至多一次，理想为输入不变时零次；
- 128 cube瞄准空处不进入 cube face窄相位；
- `compileJava`、refmap检查和 `quickPlaySmoke`通过，手动抽样普通/投影桌、剪刀、大小纸箱、强力胶、跨
  subject连接和资源重载。
