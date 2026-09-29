# 末影人仓管树形合成方案

本文定义末影人仓库管理员中的 AE 式树形合成系统。方案采用：

> AE2 式模拟规划与任务状态机 + Create 原版工厂仪表配料、分包、地址和物流执行。

目标不是在 Create 中复制一套 ME 网络，而是让玩家已经搭建的工厂仪表关系自然成为合成图，
再由末影人仓管负责规划、保留需求和按依赖顺序下单。

---

## 1. 结论

当前执行采用“持久合成任务 + Create 原生承诺”，不临时修改仪表的 `count`、过滤器、地址或连接。

原因如下：

1. 仪表配置属于玩家的永久配置，临时改写会与红石、原生补货和其他玩家的请求互相覆盖。
2. Create 的 `RequestPromiseQueue` 只按物品汇总，没有任务 ID；取消任务只停止后续发料，已发配料的承诺继续由原版兑现或过期。
3. Create 的仪表请求只会处理一层配方，不会递归展开前驱仪表。
4. Create 的物理产物没有任务标签，完成判断必须采用“同种物品可互换 + 中央保留账本”。

任务所有权和现货保留仍由本模组管理。每轮实际发料成功后向 Create 的网络承诺队列登记
`recipeOutput`，不再通过 `getPromised()` Mixin 叠加数量。实际配料调用 Create
的 `LogisticsManager.findPackagersForRequest()` 和 `performPackageRequests()`。

同一配方的多个轮次按 `factoryGaugeTimer` 间隔继续发送，不等待上一轮产物回库；任务每 10 tick
调度一次，因此间隔向上取整到调度周期。所有轮次已派发后才统一等待本步骤产物齐备，随后进入依赖步骤。
例如 `1 原木 → 4 木板`、缺口 `40 木板` 时派发 10 轮，可以同时存在多个在途批次。

总体流程：

```text
中键物品
   │
   ▼
服务端读取目标仪表图
   │
   ▼
模拟库存中先取现货，再递归展开前驱
   │
   ├── 缺少叶子原料 ──► 显示缺料，不提交
   │
   ▼
确认并持久化合成任务
   │
   ▼
从叶到根分发临时仪表请求
   │
   ▼
Create 原生打包器向各仪表地址发送配料
   │
   ▼
产物进入网络，保留账本将它分配给父任务
   │
   ▼
根产物由仓库控制台按普通玩家订单发往目标地址
```

---

## 2. 当前基础

现有实现已经完成第一层能力：

- `FactoryGaugeCatalog` 按物流网络索引所有已加载且有前驱的输出仪表。
- 末影人仓管界面将这些输出显示为数量 `0` 的可合成物品。
- 自定义菜单、屏幕和 JEI 配方转移已复用 Create 的仓管路径。
- `FactoryPanelBehaviourMixin` 已有仪表 tick 尾部入口，可以继续承担索引刷新。

尚缺少：

- 从输出仪表递归读取前驱连接；
- 服务端模拟库存与缺料计算；
- 临时请求的所有权和持久化；
- 依赖调度、完成检测、取消和恢复；
- 中键请求包、数量界面与计划预览。

---

## 3. AE2 参考结论

参考源码为 `ref/1.21.1/Applied-Energistics-2`，对应 Minecraft 1.21.1、
NeoForge 标签 `v19.2.17`，提交 `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`。

只借鉴以下结构，不复制 AE2 网络、CPU、频道或能源系统：

| AE2 结构 | 可借鉴行为 | 本方案对应物 |
| --- | --- | --- |
| `CraftingSimulationState` | 在模拟库存上取用和生成物品 | `GaugePlanningLedger` |
| `ChildCraftingSimulationState` | 尝试分支，成功后才提交差异 | 仪表候选的事务式选择 |
| `CraftingTreeNode` | 先取现货，再选择合成模式 | `GaugeCraftPlanner.expand()` |
| `CraftingTreeProcess` | 递归请求输入，最后加入产物 | `GaugeCraftTask` |
| `CraftingPlan` | 分开记录现货、合成次数和缺料 | `GaugeCraftPlan` |
| `ExecutingCraftingJob` | 持久化任务、剩余次数和等待产物 | `GaugeCraftJob` |
| `CraftingCpuLogic` | 仅派发输入已就绪的任务 | `GaugeCraftJobManager.tick()` |

关键原则是“规划和执行分离”。规划阶段不得发送包裹、修改仪表或写入承诺；确认计划时才建立
保留，执行器随后逐步下单。

---

## 4. 工厂仪表如何映射为配方

每个有前驱的 `FactoryPanelBehaviour` 是一条工厂配方：

```text
输出：behaviour.getFilter() × behaviour.recipeOutput
输入：behaviour.targetedBy 中每个前驱的 filter × connection.amount
输入网络：各前驱 behaviour.network
输出网络：当前 behaviour.network
加工地址：behaviour.recipeAddress
排列信息：behaviour.activeCraftingArrangement
```

仪表身份必须使用：

```text
GaugeId = 维度 + 方块坐标 + PanelSlot
```

不能只按物品作为身份，因为同一种物品可能有多个仪表、不同输入或不同加工地址。

`GaugeRecipeSnapshot` 至少包含：

- `GaugeId id`
- `UUID outputNetwork`
- `ItemStack output`
- `int outputPerCraft`
- `List<GaugeIngredient> inputs`
- `String recipeAddress`
- `List<ItemStack> craftingArrangement`
- `long fingerprint`

`fingerprint` 由过滤器、数量、连接、网络、地址和排列信息计算。任务派发前重新核对；若玩家在任务中途
修改仪表，任务进入 `PAUSED_RECIPE_CHANGED`，不得按旧配方继续发送材料。

只有满足以下条件的仪表可作为配方：

- 仪表、方块实体和所在区块已加载；
- 仪表激活且输出过滤器非空；
- 至少存在一个普通仪表前驱；
- 所有前驱均可读取，物品非空且连接数量大于零；
- `recipeOutput > 0`；
- `recipeAddress` 非空。

区块未加载与配方不存在要区分：前者暂停等待，后者才是无配方。

---

## 5. 合成图规划

### 5.1 根仪表选择

末影人仓管所在的仓库控制台确定根物流网络。根候选只允许来自该网络、输出物品精确匹配
物品和数据组件的仪表。

同一物品存在多个根仪表时，按以下顺序选择：

1. 能完整满足且缺料为零；
2. 消耗现货种类较少；
3. 合成节点较少、深度较浅；
4. `GaugeId` 字典序，保证结果稳定。

候选尝试必须使用子模拟账本。失败候选的库存扣减和临时产物全部回滚，这一点对应 AE2 的
`ChildCraftingSimulationState`。

### 5.2 递归规则

对 `need(item, amount, network)` 执行：

1. 从模拟库存取用该网络的未保留现货。
2. 若已满足，记录现货消耗并返回。
3. 若剩余数量对应的是一个有前驱的指定仪表，计算：

   ```text
   crafts = ceil(remaining / outputPerCraft)
   ```

4. 对每个前驱递归请求 `connection.amount × crafts`。
5. 所有输入均可满足后，建立仪表任务，并把其产物写回模拟库存。
6. 从模拟库存取走当前节点需要的数量；余数作为可被同一计划后续节点使用的副产余额。
7. 叶子仪表没有前驱，剩余数量直接记入 `missingItems[network, item]`。

根候选以物品查找；进入图后严格沿玩家实际连接的前驱仪表递归，不随意换成网络中另一个同物品仪表。
这样玩家画出的工厂关系就是配方定义。

### 5.3 模拟库存必须带来源

普通的 `物品 -> 数量` 不足以生成运行时依赖。模拟库存中的每一批物品应记录：

```text
VirtualStack = 网络 + 物品 + 数量 + producerTaskId（现货为 null）
```

父任务从虚拟库存取到某个子任务的产物时，自动建立 `父任务 dependsOn 子任务`。这样可以安全地
把树压缩成 DAG，并允许多个父节点消费同一次合成留下的余量。

### 5.4 循环和规模限制

规划器维护当前 DFS 路径中的 `GaugeId`。再次遇到同一身份时立即报告完整循环路径，例如：

```text
铁板仪表 → 铁锭仪表 → 铁板仪表
```

建议默认限制：

- 最大递归深度：64；
- 最大任务节点：512；
- 单次请求物品数：65536；
- 单网络活动任务：16；
- 单任务并行生产步骤：8（已开始但未完成的步骤，包括等待产物和暂停的步骤）。

这些限制必须在服务端执行，客户端数值不可信。

### 5.5 计划结果

`GaugeCraftPlan` 包含：

- 根输出和请求数量；
- 从各网络使用的现货；
- 每个仪表的执行次数；
- 任务依赖 DAG；
- 副产或取整余量；
- 按网络分组的缺少物品；
- 图快照 fingerprint；
- 是否完整可执行。

第一版只允许提交缺料为零的计划。以后可增加“等待补料”模式，但不应在第一版中把缺料任务静默挂起。

---

## 6. 原生仪表承诺

### 6.1 所有权模型

`GaugeCraftJobs` 为每个步骤单独保存执行状态：

```text
remaining       = 尚未完成的轮数，包含 inFlight
inFlight        = 已派发但尚未结算完成的轮数
received        = 已分配给任务、尚未结算为完整轮次的产物数量
requestCooldown = 距离下一次发料尝试的 tick 数
```

只有真实派发成功才增加 `inFlight`，并往 `Create.LOGISTICS.getQueuedPromises(network)` 添加
`RequestPromise(new BigItemStack(output, outputPerCraft))`。未派发的计划数量不算承诺。

兑现、过期、手动清理和原版仪表的满足判断均复用 Create 的 `RequestPromiseQueue`。
`FactoryPanelBehaviourMixin` 只刷新仪表索引，不再修改 `getPromised()` 返回值。
准确参考路径为 `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/logistics/` 下的
`factoryBoard/FactoryPanelBehaviour.java`、`packagerLink/RequestPromiseQueue.java` 和
`packager/PackagerBlockEntity.java`。

承诺只用于记录已派发的生产，不能当作已经到手的原料。任务账本仍按实际到货分配库存，
每份产物只分配一次。取消任务释放本任务的保留并停止后续发料，不撤回已发包裹，也不清除网络承诺。
有限合成计划不会因为承诺被清除或过期而自动多派发一轮；缺货仍等待实际产物或由玩家取消后重新规划。

### 6.2 不修改永久仪表配置

严禁为临时任务执行以下操作：

- 临时增大 `FactoryPanelBehaviour.count`；
- 修改 `upTo`；
- 替换过滤器；
- 修改 `recipeOutput` 或 `recipeAddress`；
- 添加或删除 `targetedBy` 连接；
- 为取消任务调用 `RequestPromiseQueue.forceClear(item)`。

最后一项会清除同网络中所有相同物品的原版承诺，不具备任务隔离性。

---

## 7. 任务执行器

### 7.1 状态机

```text
PLANNED
  └─► WAITING_DEPENDENCIES
        └─► READY
              └─► DISPATCHED
                    └─► WAITING_OUTPUT
                          └─► COMPLETE

任意未完成状态 ─► PAUSED_UNLOADED / PAUSED_RECIPE_CHANGED / FAILED / CANCELLED
```

任务只有在所有依赖完成、所需现货已由本任务保留且仪表图 fingerprint 未变化时才能进入 `READY`。

同一订单最多同时运行 8 个步骤。`GaugeCraftFlow` 根据保存的步骤、输入网络及物品组件分配前驱产物，
包括被后续分支共用的取整余量；执行器和合成树共用这份依赖关系。每轮先结算所有步骤的到货，
再让依赖已完成的步骤竞争空闲名额。首次成功发料才占用名额，整个步骤完成后释放；原料不足或
打包机忙碌的未启动步骤不占位。一个分支暂停不会停止其他可运行分支。

进度以 `ProgressVersion = 1` 保存到各个 `Steps` 条目。旧存档的 `Index` 之前步骤标记为完成，
当前步骤保留原有在途、到货、冷却和承诺迁移信息，后续步骤才参与新的并行调度。

### 7.2 原生 Create 请求分发

每次仪表执行沿用 Create `FactoryPanelBehaviour.tickRequests()` 的核心流程：

发料前遵守红石禁用、仪表及物流网络加载状态，以及 `factoryGaugeTimer` 发单间隔（默认 100 tick）。
满足判断使用本订单步骤的有限目标：已结算产物加在途产物覆盖该步骤需求后停止发料，
不直接使用仪表常驻补货目标的 `satisfied` / `promisedSatisfied`，也不把已有根产物算作新订单产量。
每批真实派发仍登记在原版承诺队列中，供原版仪表的库存满足判断使用。

1. 按前驱的 `network` 分组输入物品；
2. 用 `LogisticsManager.getSummaryOfNetwork(network, true)` 复核库存；
3. 若仪表有 `activeCraftingArrangement`，构造对应 `CraftingEntry`；
4. 为每个输入网络调用 `LogisticsManager.findPackagersForRequest()`；
5. 检查所有相关打包器均未 `isTooBusyFor(RequestType.RESTOCK)`；
6. 全部可用后调用 `LogisticsManager.performPackageRequests()`；
7. 登记原生 `RequestPromise`，增加本任务的在途轮数。

每次只派发一次仪表配方。多个 `crafts` 分多轮发送，行为最接近 Create 原版仪表，也避免错误地把
多次排列合成编码为一次包裹上下文。只要还有未派发轮次，冷却结束且原料、打包器可用，就继续发料，
即使前面的轮次尚未产出。已派发轮次覆盖当前步骤全部需求后进入 `WAITING_OUTPUT`，不重复派发。

需要把上述逻辑封装为 `FactoryGaugeRequestDispatcher`，不要直接调用或反射私有的
`tickRequests()`，也不要复制整个 `FactoryPanelBehaviour`。

### 7.3 完成检测与物品保留

Create 的工厂产物不带任务 ID，因此不能判断“这一件是否由本任务的机器生产”。本方案采用可互换库存：

1. 中央账本周期性读取各物流网络的准确库存；
2. 按任务和步骤顺序，将新增匹配物品分配给有在途轮次的步骤，每个步骤最多领取 `inFlight * outputPerCraft - received`，同一库存增量只分配一次；
3. 分配成功后增加 `received` 并保留现货；按完整配方产量同时扣减 `remaining` 和 `inFlight`，保留零头；
4. 父任务派发输入包裹前释放自己即将消费的保留，并立即复核真实库存。

玩家手动放入同种物品可以让任务提前完成，这是物品可互换模型的正常行为。外部系统也可能在极短窗口内
取走物品，因此派发前复核不可省略；复核失败则回到等待状态，而不是复制物品或强行完成。

### 7.4 根产物交付

根任务获得足够产物后，通过关联的仓库控制台执行普通玩家订单：

```text
stockTicker.broadcastPackageRequest(
    RequestType.PLAYER,
    PackageOrderWithCrafts.simple(rootOutput),
    null,
    requestedAddress
)
```

该调用成功表示已由 Create 接受并打包，不表示包裹已经物理到达目标。第一版任务状态到这里记为完成，
不追踪运输路径。

### 7.5 调度公平性

- 每 tick 限制规划和调度操作数；
- 活动任务使用轮询，而不是一个大任务占满所有打包器；
- 每个任务每轮最多派发一个仪表批次；
- 打包器繁忙不算失败，只保持 `READY`；
- 区块卸载不强加载，任务进入暂停并在重新加载后恢复。

---

## 8. 持久化与恢复

任务管理器使用服务端 `SavedData`，建议统一保存在主世界数据存储中，以便任务引用 `GlobalPos`。

必须保存：

- job/task UUID；
- 请求玩家、仓库控制台和目标地址；
- 根物品、请求数量和剩余数量；
- 每个任务的仪表身份、配方 fingerprint、次数和状态；
- 依赖关系；
- 已保留现货、在途轮数、部分到货和发料冷却；
- 创建时间、最后进展时间和错误原因。

服务器恢复后：

1. 不重复发送 `DISPATCHED` 任务；
2. 恢复保留账本和在途轮数；原生承诺由 Create 自己恢复，不能重复登记；
3. 用真实库存重新满足 `WAITING_OUTPUT`；
4. 仪表未加载则暂停；
5. fingerprint 不同则要求重新规划。

不能只依赖当前的弱引用 `FactoryGaugeCatalog` 保存任务状态；弱引用索引只用于发现已加载仪表。

旧存档的 `Waiting=true` 迁移为一个在途轮次，只将尚未收到的产量登记一次原生承诺，
不重复发送原料。旧存档 `Waiting=false` 时残留的上一轮 `Received` 必须清零。

---

## 9. 客户端交互

### 9.1 中键行为

- 鼠标指向可合成物品时，中键打开数量输入界面；
- 可合成判断来自菜单同步的输出集合，不依赖该条目是否为 `GaugeOutputBigItemStack`；
- 因此物品已有真实库存时仍可中键要求额外合成；
- 左右键继续表示普通库存下单，数量 `0` 的纯合成条目仍禁止左右键。

### 9.2 确认界面

服务端完成计划后返回摘要：

- 请求产物与数量；
- “已有”数量；
- “需要合成”数量及仪表操作次数；
- 缺少的叶子材料；
- 预计任务节点数；
- 确认和取消按钮。

计划必须由服务端计算。确认时重新校验计划版本和库存；若已变化，自动重新规划并刷新摘要。

### 9.3 状态显示

第一版可以只在聊天栏和仓管界面顶部显示：

- 计划中；
- 等待材料/子任务；
- 正在加工；
- 仪表或区块未加载；
- 配方被修改；
- 完成/取消。

独立任务列表和进度明细可放到第二阶段，不应阻塞核心调度器落地。

---

## 10. 并发、取消与失败语义

### 并发

- 所有库存分配通过单一服务端保留账本完成；
- 保留键必须包含物流网络 UUID 和完整物品组件；
- 原版库存请求不认识本模组保留，因此真正发包前始终重新检查；
- 多任务可以并行，但同一批现货不能在本模组内部重复承诺。

### 取消

- 未派发任务立即释放全部保留；
- 已发送的物理包裹无法召回；
- 取消后停止派发后续节点，已经产出的物品成为普通网络库存；
- 保留已经登记的原生承诺，等待实际到货兑现或按仪表配置过期，不调用 `forceClear`。

### 超时

- “长时间无进展”只改变提示，不自动删除任务；
- 仪表缺失或配方变化进入暂停；
- 确认方块已被破坏后才标记失败；
- 管理员可取消或重新规划。

---

## 11. 推荐代码结构

```text
content/endermanstockkeeper/crafting/
  GaugeId.java
  GaugeRecipeSnapshot.java
  GaugeRecipeGraph.java
  GaugePlanningLedger.java
  GaugeCraftPlanner.java
  GaugeCraftPlan.java
  GaugeCraftTask.java
  GaugeCraftJob.java
  GaugeCraftJobManager.java
  FactoryGaugeRequestDispatcher.java
  GaugeCraftSavedData.java
  packet/
    GaugeCraftPlanRequestPacket.java
    GaugeCraftPlanResponsePacket.java
    GaugeCraftConfirmPacket.java
    GaugeCraftCancelPacket.java
    GaugeCraftStatusPacket.java
```

现有类调整：

- `FactoryGaugeCatalog`：从“输出物品列表”扩展为可生成不可变 `GaugeRecipeSnapshot`；
- `EndermanStockKeeperRequestMenu`：同步可合成集合和计划会话 ID；
- `EndermanStockKeeperRequestScreen`：中键入口、数量和计划摘要；
- `FactoryPanelBehaviourMixin`：保留现有仪表索引，不再叠加承诺数量；
- `StockKeeperRequestScreenMixin`：只负责识别中键和阻止零库存左右键，不承担规划逻辑。

预计不需要修改 Create 的 `StockTickerBlockEntity`、`LogisticsManager` 或保存格式。

---

## 12. 分阶段实现

### P1：单链路最小闭环

- 服务端读取唯一根仪表；
- 支持单一配方链和现货优先；
- 中键数量请求；
- 持久任务、原生承诺和叶到根派发；
- 缺料拒绝、取消和重启恢复；
- 根产物普通下单。

验收：`原木 → 木板 → 齿轮` 一类三层链能够一次中键完成，重启后不重复发料。

### P2：完整 DAG 与多候选

- 同物品多个根仪表；
- 事务式候选回滚；
- 带来源的虚拟库存；
- 共享子任务、余量复用；
- 循环路径和完整缺料摘要。

### P3：体验和管理

- AE 风格计划确认表；
- 活动任务列表、进度和取消按钮；
- 等待补料模式；
- 配方变化后一键重新规划；
- 配置化规模、并发和超时限制。

不建议先做漂亮的任务界面再补调度器。P1 必须先验证 Create 物理物流下的保留与恢复语义。

---

## 13. 风险与侵入性

总体实现难度约 `7/10`，核心风险不是树遍历，而是物理物流中的并发和完成归属。

| 风险 | 处理 |
| --- | --- |
| 产物没有任务标签 | 中央可互换库存保留账本 |
| Create 承诺没有任务所有权 | 已发料承诺归原版管理，取消只释放任务保留并停止后续发料 |
| 多网络输入无法真正事务提交 | 先收集和检查全部请求，再按 Create 原路径连续提交；失败重试 |
| 玩家中途修改仪表 | fingerprint 校验并暂停 |
| 区块卸载 | 不强加载，持久暂停 |
| 外部系统抢走保留物品 | 发包前复核，失败回到等待 |
| 取消后包裹仍在路上 | 明确不可召回，产物回归普通库存 |

Mixin 侵入性保持较低：复用现有中键入口和仪表索引刷新，不修改 `getPromised()` 返回值。
规划、持久化和物流分发都放在本模组普通类中，不覆盖 Create 方法。

---

## 14. 最终验收标准

1. 仓管显示网络内所有有前驱的已加载仪表输出，真实库存为零时显示 `0`。
2. 无论物品当前库存是否为零，中键均可发起“额外生产”。
3. 规划先消费现货，只对缺口递归合成。
4. 缺少叶子材料时不发送任何物理包裹，并准确列出缺口。
5. 多层任务严格从叶到根执行，不提前发送父任务。
6. 原版仪表能把临时任务识别为承诺，但取消一个任务不会清掉其他承诺。
7. 重启、区块卸载和打包器繁忙不会重复下单。
8. 修改配方仪表后旧任务暂停，不按错误配方消耗材料。
9. 两名玩家同时请求时，本模组内部不会重复使用同一批现货。
10. 最终产物通过仓库控制台的普通 Create 订单送往玩家填写的地址。
11. 同一配方可以同时有多轮在途；40 木板任务按 1 原木产 4 木板派发 10 轮，无需逐轮等待回库。
12. 部分到货、多个同物品任务和旧存档迁移不会重复分配产物或重复派发原料。
