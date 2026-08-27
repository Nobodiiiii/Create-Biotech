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
