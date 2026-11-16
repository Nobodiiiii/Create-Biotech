# 05. 运行时性能与状态生命周期审计

## 结论摘要

本章是静态热点审计，不等同于 Spark/JFR 实测。仓库当前最值得先处理的并非一般性的对象创建，而是三个会随世界规模放大的结构性问题：

1. `LiquidLivingSlimeInteractionHandler` 对每个已加载 LivingEntity、客户端和服务端、每 tick 都读写两项 persistent NBT；这会把临时运动状态写入实体持久数据并扩大所有生物的热路径与存档负担。
2. `CreeperBlastChamberBlockEntity` 在每 tick、每 20 tick 和客户端动画中反复重建机械压力机/打包机列表、扫描结构与查询实体；配置允许结构边长达到 32，使部分路径从默认规模的小开销放大到数十万甚至数百万次方块/方块实体访问。
3. 多个 static 世界状态没有统一 server/level stop 清理。`BioPackagerContraptionTracker` 的弱引用失效分支甚至不会移除 entry；Allay courier、目标注册表和液态活性史莱姆敲击计数也会跨集成服务器会话残留。

其次是 Shulker Teleporter 的无条件实体扫描、Allay 目标全量排序/HUD 二次遍历，以及 Slime/Magma Belt 的每 tick 容器分配。建议先修生命周期和算法级热点，再用 Spark/JFR 确认微优化收益。

## 1. Creeper Blast Chamber

### PERF-01：结构校验频率与允许的最大结构尺寸不匹配

- 优先级：P0/P1
- 文件：`CreeperBlastChamberBlockEntity.java`
- 配置：`CBConfigs.CreeperBlastChamberConfig.maxSize` 允许 1–32，默认 5

`scanStructure` 的主体遍历 `4 * size²` 个方块。`findStructure` 又尝试控制器可能位于边框上的约 `4 * size - 4` 个 origin，并从 `minSize` 一直尝试到 `maxSize`。多数错误结构能在中心压力机检查时提前退出，但近似成型、错误位于扫描末端的结构会接近三次方/跨尺寸四次方的最坏工作量。

更直接的问题是：只要 `pendingUnpacks` 非空，`tickPendingUnpacks` 就会每 tick 调用一次完整 `scanStructure(level, structureOrigin, structureSize)`。边长 32 时单次完整扫描约 4096 次方块状态读取；若内部检查继续解析 vault controller、kinetic 和结构关系，实际成本更高。正常结构本身还会每 20 tick 重新执行 `tryDetectStructure()`。

建议：

1. 结构形成后缓存不可变的 `packagerPositions`、`pressPositions`、vault controller 和校验摘要。
2. 通过相关方块的 `onPlace/onRemove/neighborChanged` 或一个结构版本号把控制器标记为 dirty；只有 dirty、区块重新加载或低频容错检查时才做全量扫描。
3. pending 操作每 tick只验证直接使用的 packager/press 是否仍存在，不要全结构扫描。
4. 若暂不做事件驱动校验，应把 `maxSize` 的安全上限与压测结果绑定；当前允许 32，却没有对应性能门禁。
5. 为默认 3/5、较大 16、上限 32 的有效结构和“最后一个方块错误”的结构分别记录 tick 耗时。

### PERF-02：压力机/打包机列表在热路径反复重建

- 优先级：P1

`getMechanicalPresses()` 每次创建 `ArrayList` 并遍历内部 `(size - 2)²` 个位置。服务端 `tickPressProcessing()` 每 tick 调用一次；客户端 `syncClientPressControllers()` 每 tick也调用一次。更严重的是 `tickClientWorkingCreeperEffects()` 对每个工作中的 creeper 调用 `getRenderedCreeperEffectPressOffset()`，后者再次通过 `getMechanicalPresses()` 寻找 master press。

在边长 32、最多约 900 个内部位置的极端结构中，客户端“每个 creeper 再扫描全部 press”会形成近似 O(N²) 的方块实体查找，并且每次产生新列表。`getPackagerPositions()` 也在输入选择、类型汇总、输出查找和渲染状态中反复扫描内部方块并分配列表。

建议在结构成功形成时缓存：

- 有序 packager positions；
- mechanical press block entities/positions；
- master press position；
- packager position 到 tracked creeper UUID/entity 的索引。

结构 dirty 时整体重建；单个实体消失时只更新实体索引。渲染一帧/一 tick 内至少把 master press 和当前 positions 作为局部快照复用，不要在每只 creeper 上重新求值。

### PERF-03：marked creeper 查找把世界实体查询当作主索引

- 优先级：P1

服务端已经维护 `syncedMarkedCreepers: UUID -> BlockPos`，但多个路径仍使用 `level.getEntitiesOfClass`：

- `cleanupMissingMarkedCreepers()` 每 tick对 pending/tracked entry 逐个查询；
- `findMarkedCreeperForOutput()` 对每个 packager 查询局部 AABB；
- `getMarkedCreeperAtPackager()` 对单个 packager 却查询“整个结构再 inflate 32”的大 AABB；
- 工作汇总和渲染列表会重复调用上述方法。

建议把服务端索引改为 `UUID -> tracked state` 加 `BlockPos -> UUID`，优先通过 `ServerLevel#getEntity(UUID)` 或已有 entity reference 验证；只有恢复旧存档、索引缺失或实体跨 chunk 重新加载时才做有节流的空间扫描。清理可以轮询分片，而不是每 tick验证全部 entry。

### PERF-04：客户端动画每 tick创建多个 Map/Set

- 优先级：P2

`syncClientPressControllers()` 每 tick创建一个 `HashSet`；`tickClientWorkingCreeperEffects()` 每 tick创建一个 `HashMap` 和两个 `HashSet`，随后把 `clientPressOffsets` clear 再 putAll。单个默认结构影响不大，但与 PERF-02 的重复结构扫描叠加后会增加 GC 抖动。

先修缓存和 O(N²) 查找，再考虑复用集合或用 generation counter 标记活跃 key。不要为了消除小集合而引入跨线程池化。

## 2. 全局 LivingEntity 热路径

### PERF-05：液态活性史莱姆为所有 LivingEntity 每 tick写 persistent NBT

- 优先级：P0/P1
- 文件：`LiquidLivingSlimeInteractionHandler.java`

`LivingTickEvent` 会覆盖所有已加载生物和玩家。当前 handler 在客户端与服务端都执行：

1. 从 `entity.getPersistentData()` 读取“上 tick 是否接触流体”和“上 tick Y 速度”；
2. 查询自定义 fluid type height；
3. 无论实体是否曾接近该流体，都写回一个 boolean 和一个 double。

结果是临时动画/碰撞状态进入每个实体的持久 NBT，可能随实体保存；双端重复执行还会增加客户端热路径。这是典型的“功能很局部，成本作用于整个实体集合”。

建议：

- 只在服务端处理声音判定；客户端不需要维护同一份 persistent data。
- 使用非持久的 per-entity transient state，例如 Forge capability/attachment、按 entity id 的 level-scoped map，或实体流体交互回调；level unload/server stop 时清理。
- 只跟踪可能触发落地音的短窗口，例如正在下落、最近接触目标 fluid 的实体；离开后尽快删除状态。
- 如果仍需全局 LivingTick 检查，至少不要对未接触过流体的实体写 NBT，并用 profiler 测量 100/500/1000 生物场景。

### PERF-06：液态活性史莱姆 source hit map 会永久保留失效位置

- 优先级：P1

`SOURCE_HIT_COUNTS` 是进程级 static map。只有继续敲击到破坏，或通过本 handler 的 `clearFluid()` 删除流体时才移除。如果方块被其他机制替换、区块/维度卸载、服务器停止或玩家中断挖掘，entry 可以无限期残留；集成服务器重新开档后，相同 dimension/pos 还可能继承旧敲击次数。

建议给 entry 增加 `lastHitGameTime` 和 server identity/level scope，定期清理超时或已非目标 source 的位置；监听 server stopped/level unload 做确定性清理。破坏进度也应在超时清理时发送 `-1`。

## 3. 实体空间查询

### PERF-07：Shulker Teleporter 在便宜条件之前每 tick扫描实体

- 优先级：P1
- 文件：`ShulkerTeleporterBlockEntity.java:99-103`

每个服务端 tick 都先执行：

```java
level.getEntitiesOfClass(Entity.class, getTeleportArea(), this::canTeleportEntity)
```

然后才检查 `hasUsableTarget()` 和 `Math.abs(getSpeed()) > 0`。因此没有转速、没有有效地址的 teleporter 仍持续创建列表和查询实体。

建议先短路：无速度、地址空、无目标、正在 cooldown/结构无效时直接打开并返回；只有确实可以传送时才查询区域。若只需知道“是否存在一个实体”，优先使用可早停的 entity query/遍历方式，而不是构建完整 `List<Entity>`。把目标地址可用性缓存到地址或 SavedData 版本变化时更新。

### PERF-08：Experience Pump 在活跃 nozzle 上每 tick可能做多次实体查询

- 优先级：P2
- 文件：`ExperiencePumpBlockEntity.java`

吸引路径查询一次较大 AABB 的 `ExperienceOrb`；抽取路径又查询吸收 AABB 的 orb 和 player。通过 capability 被 SIMULATE/EXECUTE 多次调用时，同一 tick还可能重复构建这些列表。单个泵的范围较小，但大量活跃泵、经验球农场和高转速吸引范围会放大成本。

建议在同一 server tick缓存 nozzle 周围 orb/player 快照，SIMULATE 不修改且 EXECUTE 前验证实体仍存活；或为吸引和抽取设置 2–5 tick 的扫描节流，同时保持流体速率按 tick计算。需要用经验球密集场景验证行为，避免因为缓存导致重复扣取。

### PERF-09：Ghast Helm 客户端 chunk block entity 扫描目前有界，但可改为索引

- 优先级：P3

控制热气球时，每 10 tick扫描 16/24 格半径内已加载 chunk 的所有 block entities，以寻找装配站。该路径只在主动驾驶时运行，范围也有界，优先级低于前述服务端热点。

若后续装配站/其他 block entity 数量增长，可在客户端维护 `GhastHotAirBalloonAssemblyStationBlockEntity` 的 level-scoped position set，由 load/remove 更新；查询时只比较候选坐标。当前不建议为了这一个有界扫描引入复杂全局索引，除非 profiler 证明它显著占用客户端 tick。

## 4. Allay courier 与目标注册表

### PERF-10：Allay target 查找全量 stream + sort

- 优先级：P2
- 文件：`AllayPortTargetRegistry.java`

同维度和跨维度查找都会过滤后对全部匹配项排序，再取 `findFirst()`；跨维度路径还为每个候选创建 `TargetLocation`。这里只需要 comparator 最小值，可以使用单次遍历/`min(comparator)`，但更根本的优化是按 canonical address 建立二级索引：

```text
canonical address -> dimension -> positions
```

模糊地址匹配仍可能扫描 address bucket，但无需扫描所有 port。`getKnownNames()` 也可维护有引用计数的有序 name set，而不是每次跨全部维度排序。

注册表 entry 由每个 AllayPort 每 20 tick刷新，但全表超时清理每 server tick执行。建议把清理同样节流到 20 tick，或用 expiry bucket/优先队列；60 tick timeout 无需 20 次/秒全量检查。

### PERF-11：Air Courier HUD 对 player、observation 和 entry 进行嵌套遍历

- 优先级：P2
- 文件：`AllayCourierHudSync.java`

每 tick先为所有 task创建 snapshot/preview，然后对每个在线玩家遍历 `OBSERVED_THIS_CYCLE.values()` 并移除属于该玩家的条目；处理已有 entry 时又对 `playerObservations.stream().anyMatch(...)`。规模较小时无碍，但多玩家、多 courier 时近似 O(players × observations + entries × observations)，并产生多组临时 list/stream 结果。

建议收集阶段直接使用 `Map<UUID, List<ObservedHudCandidate>>` 按玩家分桶，再为每个玩家建立本 tick observed source set；已有 entry 判定降为 O(1)。只有 payload 实际变化或保证刷新周期到期时再创建完整网络 payload/ItemStack preview。

### PERF-12：Courier task 每 tick同步 visual entity并标记 SavedData dirty

- 优先级：P2/P3

每个 task 每 tick更新逻辑状态，然后把位置、速度、包裹、阶段、任务写到 visual entity并设置 `hurtMarked`。`SavedData#setDirty()` 本身主要是 dirty flag，并不代表每 tick立即写盘；由于 courier 位置需要崩溃恢复，不能简单删除。但 `setPackage/setMission/setPhase` 若触发 SynchedEntityData，即使值未变也应避免重复赋值。

建议区分：

- 每 tick变化：位置、速度；
- 阶段变化才同步：phase、mission、package；
- 存档 dirty：状态确实变化时标记；可接受最多 N tick回退时，按 5–20 tick checkpoint，而完成/传送/交付时立即 dirty。

先通过网络包统计和 autosave profile 确认实际成本，再改变恢复语义。

## 5. Belt 热路径

### PERF-13：Slime Belt inventory 每 tick分配并排序四个工作列表

- 优先级：P2
- 文件：`SlimeBeltInventory.java`

移动时每 tick创建：

- 一个两元素 `Ending[]`；
- 一个基于 `IdentityHashMap` 的 transferred set；
- FRONT/BACK 两个 track ordered list；
- START/END 两个 connector ordered list；
- 四次基于进度的 sort。

这套逻辑比本地 Create 6.0.8 的线性 BeltInventory 更复杂，是环形双轨语义带来的合理成本，但在大量长 belt 和多物品场景中会成为 GC/排序热点。建议：

1. 先记录每个 controller 的 item count、四次 sort大小与耗时。
2. 若列表通常很小，保留算法只复用 scratch collections即可。
3. 若列表经常很大，维护按 loop position 排序的主容器或一次排序后按 section分桶，避免四次全量过滤。
4. 不要在没有基准的情况下直接把 `LinkedList` 换成 `ArrayList`；插入/删除模式需一起评估。

### PERF-14：Slime/Magma Belt controller 每 tick分配 passenger removal list

- 优先级：P2
- 文件：`SlimeBeltBlockEntity.java`、`MagmaBeltBlockEntity.java`

只要 belt 有速度，就会确保 `passengers` map存在并创建新的 `ArrayList<Entity> toRemove`，即使没有 passenger。建议先判断 `passengers == null || passengers.isEmpty()`；非空时使用 entry iterator安全移除，或复用 scratch list。这个优化简单且行为风险低。

两套 block entity 和 movement handler 的逻辑高度平行；将 passenger aging/transport/removal 提取为共享 helper 可以避免一边修复、另一边继续分配或产生行为差异。

## 6. static 状态生命周期

### PERF-15：`BioPackagerContraptionTracker` 的弱引用失效 entry 不会删除

- 优先级：P1

`ACTIVE` 保存 `UUID -> ContraptionEntry`，entry 内是 weak entity reference。但 `tickAll` 遇到：

```java
contraption == null || contraption.level() != level
```

会直接 `continue`，不会删除。weak reference 清空后，entry 和内部 `PackagingState`/ItemStack 会永久留在 static map。`clearAll()` 已实现但全仓没有调用；server stop 也没有清理。

建议：

- reference 为 null 时立即移除 entry；若未 deposit 的箱子无法安全恢复，至少记录一次结构化 warning。
- server stopped 时调用 `clearAll()`，并在 level unload/contraption dimension change时明确迁移或清理。
- 该状态只在 server thread访问时，评估是否需要 `ConcurrentHashMap`；单线程 `HashMap` 更容易定义 iterator 和生命周期语义。

### PERF-16：Allay 与客户端 static map 缺少统一会话清理

- 优先级：P1/P2

以下状态没有完整的 server stopped/client level unload 清理入口：

- `AllayCourierTaskManager.savedData` 与 `courierEntities`；
- `AllayPortTargetRegistry.TARGETS`；
- `AllayCourierHudSync.HUD_STATES` / `OBSERVED_THIS_CYCLE`；
- `LiquidLivingSlimeInteractionHandler.SOURCE_HIT_COUNTS`；
- Creeper Blast Chamber 的客户端 `CLIENT_TRACKED_CREEPERS` / `CLIENT_PRESS_CONTROLLERS` 主要依赖各 BE `setRemoved`。

集成服务器在同一 JVM 中切换世界时最容易暴露跨会话污染。建议建立单一 lifecycle coordinator，监听 `ServerStartingEvent`、`ServerStoppedEvent`、`LevelEvent.Unload` 和客户端断线/level unload；每个 manager 提供幂等 `clear/reset`。启动时也先 reset，再绑定新 server 的 SavedData，避免仅依赖正常 stop 路径。

## 7. 实施与测量顺序

1. 移除全 LivingEntity persistent NBT 热写，补 level/server lifecycle 清理。
2. 修复 `BioPackagerContraptionTracker` weak-reference entry 泄漏，并接入 server stop。
3. 缓存 Blast Chamber 结构拓扑和 entity 索引，取消 pending 状态下每 tick全结构扫描。
4. 将 Shulker Teleporter 的速度/目标短路移到实体扫描前。
5. 优化 Allay target/HUD 的索引和遍历结构。
6. 在大型 belt、经验球农场和最大 Blast Chamber 场景运行 Spark/JFR，再决定集合复用与数据结构重写。

建议至少建立以下性能场景：

| 场景 | 指标 |
| --- | --- |
| 100/500/1000 LivingEntity，无液态史莱姆 | handler 总耗时、实体 NBT大小、GC |
| Blast Chamber 5/16/32，空闲/加工/错误末端方块 | 控制器 tick、block state/BE query次数 |
| 64 个无转速/无目标 Shulker Teleporter | entity query次数、server tick |
| 100 个 AllayPort、100 个 courier、20 玩家 | target lookup、HUD tick、网络包数 |
| 64 条 Slime Belt，每条 1/16/64 item | sort次数、分配率、controller tick |

## 8. 验证清单

- [ ] 无目标 fluid 的普通实体不再获得两项 Create: Biotech persistent NBT。
- [ ] server stop/start 和客户端换世界后所有 static manager 为空且绑定新 level/server。
- [ ] weak contraption reference 失效后 tracker entry 可回收。
- [ ] Blast Chamber 的结构扫描次数由 dirty/节流驱动，pending 每 tick不做全量扫描。
- [ ] Blast Chamber 客户端 master press/positions 每 tick只求值一次。
- [ ] 无速度或无目标 Shulker Teleporter 不执行实体 AABB 查询。
- [ ] Allay target lookup 不为“取最优一个”排序全部候选。
- [ ] 性能改动有默认规模与上限规模的 profiler/基准对比，且玩法时序没有回归。

## 9. 爆破室压力机并行渲染修复（2026-11-17）

仅路过爆破室时，Create `PressVisual.beginFrame` 也会经 Biotech 的压力机同步入口解析爆破室。
同一结构中多个压力机 Visual 可由 Flywheel 并行更新；此前 `getMechanicalPresses` 清空、重填并
返回同一个 ArrayList，而 `resolveRenderPressState` 的 tick 标记与主压力机状态也没有同步，
导致客户端 `ConcurrentModificationException` 和列表读取空指针。该故障不要求玩家操作结构。

修复后：

- `ChamberPressRenderState` 只保存动画的布尔值、tick 数、偏移和不可变位置映射，不保存可变压力机对象。
- `ChamberPressRenderHandler` 在客户端 tick 结束后统一捕获，完整构建后通过一个 volatile 引用发布。
  加载、移除、结构/数据包变化会标记失效，在帧前补充更新；卸载世界及断线清理快照和加载结构登记。
- 压力机与内部苦力怕渲染只查询同一份已发布快照，不再从并行回调重建列表、解析主压力机或修改维度登记。
  非结构压力机继续使用自身动画。停机、混合转速回退、不同工作模式偏移和 partial tick 插值保持原有规则。
- `getMechanicalPresses` 返回查询独有的局部列表，工具提示及逻辑调用不能改变其他读者正在遍历的结果。
  客户端粒子/声音 tick 使用私有捕获，避免改变其原有执行时点，也不发布实体 tick 顺序中的中间状态。

Create 6.0.10 的精确本地参考：
`ref/1.21.1/Create/src/main/java/com/simibubi/create/content/kinetics/press/PressVisual.java:39`、
`ref/1.21.1/Create/src/main/java/com/simibubi/create/content/kinetics/press/PressingBehaviour.java:101`。
客户端事件时序核对了本机 NeoForge 21.1.234 sources.jar 的 `ClientTickEvent.Post` 与
`RenderFrameEvent.Pre`。未修改 Mixin、存档格式或版本号。

验证：`build --offline` 及现有 80 项测试通过，未创建测试文件。另通过 JShell 在内存中调用实际编译类，
完成 50,000 组旧/新插值及偏移对照，检查快照防御复制、只读映射和同步/混合转速/停机/缺失位置行为；
以 100,000 次发布和 10 个读取线程的 1,000,000 次读取检查完整快照可见性。按非 Mixin 改动规则未启动
客户端；路过运行/停机结构、混合转速切换、拆装及跨区块/世界卸载仍需在游戏内复测。
