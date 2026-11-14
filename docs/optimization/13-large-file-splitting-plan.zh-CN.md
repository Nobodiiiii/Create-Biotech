# 13. 超大文件拆分方案

核验日期：2026-11-14。基于 `1.21.1` 分支、HEAD `db32bb912f16` 及当前未提交工作区；
Minecraft 1.21.1、NeoForge 21.1.234、Java 21、Create 6.0.10-281，模组声明版本为
`2.0.0-beta.3`。本文是静态审查后的重构建议，尚未实施源码拆分，也不表示已经验证运行行为。

## 1. 结论与统计口径

优先处理手术台客户端、手术台方块实体和苦力怕爆破室。它们的主要问题是可变状态与多种职责集中，
不能只把方法移入几个仍互相读取字段的 `Helper` 类。

建议继续使用现有按功能分包的结构，在包内通过组合拆分；当前没有必要引入多 Gradle 模块、
通用控制器框架或新的事件总线。每次只迁移一个职责及其状态，保持行为、公开入口和持久化格式。

统计对象为 `src/main/java/**/*.java`；物理行包括空行、注释和 import，排除测试、`ref/`、
`build/` 等目录。资源文件单独统计。

- 主源码共 **920 个 Java 文件、148,252 行**。
- 其中 `ponder/generated/` 为 **24 个文件、4,628 行**。
- 排除上述生成目录后为 **896 个文件、143,624 行**；其中 11 个文件至少 1,000 行，
  9 个至少 1,500 行，6 个至少 2,000 行。
- 最大的 6 个非 Ponder 生成文件共 **22,869 行，占该部分源码约 15.9%**。

这里的“非生成”仅按已确认的 `ponder/generated/` 目录区分，不声称其他文件全部由人工编写。
旧的 [02 架构审计](02-java-architecture-network.zh-CN.md) 有些数量和类名来自较早版本，
本次以当前源码为准；例如 Allay 已位于主体包内，配置也已有真正的 `ModConfigSpec`。

| 文件 | 当前物理行 | 建议顺序 | 核心拆分边界 |
| --- | ---: | --- | --- |
| `SurgicalTableClientHandler.java` | 8,348 | 第一批，P1 | 几何缓存、拾取、预览、工具交互、提交确认、提示与轮廓 |
| `SurgicalTableBlockEntity.java` | 3,704 | 第二批，P1 | 对象索引、连接查询、放置、切割、粘合、肢体、打包、存储入口 |
| `CreeperBlastChamberBlockEntity.java` | 3,298 | 第二批，P1 | 结构、加工与过载、容纳流程、输入输出、客户端表现 |
| `SlimeBionicEntity.java` | 2,734 | 第三批，P2 | AI Goal、运动、近战、环境响应与身体几何 |
| `SurgicalAssembly.java` | 2,473 | 第二批，P2 | 持久化编解码、肢体拓扑、几何运算；保留嵌套公共类型 |
| `SurgicalCapturedRenderPlan.java` | 2,312 | 第一批后段，P2 | 渲染捕获、网格恢复、材质归属、纹理缓存、回放 |
| `GeneratedPonderSupport.java` | 2,186 | 独立处理，P3 | 从思索者导出器拆运行支持代码，保留生成入口 |
| `SlimeBionicAnimator.java` | 1,883 | 第三批，P2 | 骨架构建、攻击几何烘焙、运动测量、逐帧姿态求解 |
| `AllayCourierTask.java` | 1,647 | 第三批，P2 | 任务状态、港口运动、目标丢失恢复、ETA、存储 |
| `SurgicalClientTopology.java` | 1,559 | 第一批起点，P2 | 接触几何、平面布局搜索、落地求解 |
| `SpiderAssemblyTableBlockEntity.java` | 1,411 | 第四批，P2 | 混合槽与流体策略、配方规划、加工执行 |
| `PetriDishBlockEntity.java` | 1,069 | 第四批，P2 | 目标记录、培养与出生流程、客户端动画 |

以下新类名均为建议名，实施时可按现有命名调整。行数只用于定位，不作为硬性拆分配额。

## 2. 手术台客户端：先建立明确的状态归属

源码根路径：`src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/`。

### 2.1 `SurgicalTableClientHandler` 的目标结构

保留原类作为事件订阅和外部调用入口，事件只做调度；用一个客户端会话对象持有长期组件。
会话对象负责统一清理，不集中保存全部业务字段，也不让所有组件互相访问。

| 建议组件 | 从当前类提取的内容 | 状态与边界 |
| --- | --- | --- |
| `SurgicalClientSession` | 当前 `clear()`、tick/render/input 的协调 | 每个客户端世界一份；拥有组件和统一取消入口 |
| `SurgicalGeometryStore`、`SurgicalSubjectGeometry` | `geometryFor/registerGeometry/bindGeometry`、`TableGeometry`（6967 行起）、reserved 与 owner handoff | 独占 `TABLES`、`SUBJECT_GEOMETRIES`、缓存权重与代数；对外提供读取和有限的更新操作 |
| `SurgicalGroundingPreparation` | `prepareGroundingGroups`（3922 行起）、`GroundingPreparation`、`GroupPreparationCache` | 拥有落地任务；提交结果前由 geometry store 核验版本并整组发布 |
| `SurgicalSelectionResolver` | `find*Selection`、`findNearestCubeHit`、射线与面求交、遮挡、selection cache | 接收几何只读视图和一次输入快照；返回命中结果，不发包、不消费工具 |
| `SurgicalPlacementController` | `updatePlacementPreview`（1064 行起）、`PlacementSource`、临时搬运与放置候选 | 拥有来源捕获、候选和放置预览；复用原异步任务与缓存 |
| `SurgicalCutController` | 单切、胶缝切、批量切、footprint 计算与切割动画（4625–5206 行附近） | 拥有 pending cut 与动画状态；通过统一提交协调器等待服务端结果 |
| `SurgicalGlueController`、`SurgicalGluePreviewPlanner` | `handleGlueClick`、`planGluePreview`、transform replay、编辑轴与预览调整 | controller 持有 pending/editor；planner 只根据输入生成方案，不修改缓存 |
| `SurgicalSymmetryController` | `handleSymmetryClick`、reference 与 plane 选择、镜像规划 | 共享胶水规划的明确输入输出，不直接读取另一个 controller 的私有状态 |
| `SurgicalJointController`、`SurgicalWrenchController` | 肢体/黏液接缝选择与安装；wrench editor、旋转与确认 | pending 状态随所属工具迁移；保留当前工具优先级与按住抑制行为 |
| `SurgicalVisualCommitCoordinator` | `beginVisualCommit/updatePendingVisualCommit`（3768 行起）、placement handoff、回滚与确认 | 唯一拥有 `PendingVisualCommit`；协调预览退出与权威几何接管 |
| `SurgicalPackedPreviewService` | `packedBodyPreview`、`measureBodyMetrics`、`PackedPreparation` | 独占打包预览与测量缓存；复用 `BionicTraitResolver` 的 revision |
| `SurgicalInteractionPresentation` | `interactionPrompt`（4240 行起）、outline 状态、预览绘制 | 读取当前交互结果和几何；HUD 与轮廓不重新执行拾取或布局搜索 |

最后一组如仍然过大，再分为提示构建和轮廓绘制两个类。Glue planner、selection resolver 等
算法较多的组件可以超过 800 行；先保证职责完整，再看是否存在第二条稳定边界。

### 2.2 依赖与生命周期约束

- 输入事件先由原入口按现有优先级路由。工具组件返回“已消费/未消费”或操作结果，避免拆成多个
  独立订阅者后同一次点击执行两种动作。
- `SurgicalGeometryStore` 是几何状态的唯一所有者。工具只提交预览变换与撤销请求，不拿到可任意修改的
  map/list；无需为每次读取深拷贝全部几何。
- 选择结果可在一帧内复用；外部发包格式和服务端验证不随客户端拆分变化。
- 所有后台任务只计算冻结输入，回到客户端线程后校验 world/session、resource generation、table revision
  和 subject render revision。保留当前整组落地一次发布的行为。
- `clear()` 在世界卸载、资源重载等现有入口统一取消任务、释放资源并清理工具状态。迁移组件后不得漏掉
  原本由 `clear()` 取消的测量、捕获和落地任务。
- 保留基于 persistent UUID 的几何身份，以及控制器位置移动时的接管窗口；不能改成只按位置重新捕获。

外部依赖已核验：`SurgicalTableRenderer` 调用约十余个几何/接管入口；HUD 使用
`InteractionPrompt`；`CBClientPacketHandlers` 使用 `acceptWrenchCommit`；
`MixinSuperGlueSelectionHandler` 使用 `shouldOverrideCreateGlue`。
第一阶段保留这些名称、签名和静态转发，避免同时修改 renderer、网络入口和 Mixin。

### 2.3 两个相邻大类同步收敛

**`SurgicalClientTopology`：最适合作为第一笔小提交。**

- `SurgicalContactGeometry`：`buildContactTopology/contactBetween`、凸体相交、面裁剪、prepared cube。
- `SurgicalPlacementSearch`：`planInitialPlacement`（390 行）、`snapConnectedGroup`、格点顺序、
  footprint 碰撞、批量布局搜索。
- `SurgicalGroundingSolver`：`groundComponents`（776 行）、`groundConnectedBodies`（795 行）与相关落地计算。

原类先保留转发和公共 record，逐块原样移动算法。继续使用现有 `SurgicalConnectionGraph`，
不要再写一份连接图；“客户端几何接触”和“服务端权威连接”仍是不同职责。

**`SurgicalCapturedRenderPlan`：按处理流水线拆。**

- `SurgicalRenderCapture`：`captureInput`（387 行）、recording buffer/vertex consumer、模型名称观察 scope。
- `SurgicalMeshRecovery`：`recoverCandidate/recoverSolid/recoverFlat`、未闭合网格、合成立方体与去重。
- `SurgicalMaterialBinding`：`appearanceOwner`、overlay、held item/equipment layer 归属。
- `SurgicalTextureMasks`：`alphaMask/loadAlphaMask`（1345 行起）与资源缓存清理。
- 原 plan 保留不可变结果和渲染入口；有必要时再抽 `SurgicalCapturedMeshRenderer`。

先提取后处理算法，再拆捕获与回放。保持稳定 cube id、纹理/图层顺序、持物层和薄片识别规则。
保留 `observeModelCube`、`bufferForLayer` 等 Mixin 调用入口及其无捕获时的快速返回。
不能在工作线程访问 renderer、资源纹理或执行绘制；缓存清理仍须覆盖全部新组件。

## 3. 手术台服务端与组装数据

源码根路径：`src/main/java/com/nobodiiiii/createbiotech/content/surgery/`。

### 3.1 `SurgicalTableBlockEntity`

| 建议组件 | 当前职责定位 | 所有权与迁移方式 |
| --- | --- | --- |
| `SurgicalSubjectStore` | `subjects/subjectsById/subjectsByPersistentId`（67 行起）、add/remove、id 分配 | 一份真实对象集合；三种查询视图由同一 store 更新 |
| `SurgicalTableConnections` | `connectionGraph`（2984 行起）、connected groups、组合与肢体查询 | 独占原连接缓存及失效标记；沿用现有图结构和失效粒度 |
| `SurgicalPlacementOperations` | `tryPlaceSubject`（465 行）、composite、temporary move、跨台搬运 | 先验证再提交；保留源台/目标台同时保存与同步的行为 |
| `SurgicalCutOperations` | seam/glue/cube/batch cut、对应状态和布局校验 | 接收操作参数，输出结果；修改经统一对象入口执行 |
| `SurgicalGlueOperations` | glue/symmetry、`validateGluePlan`、`applyGluePlan`（2021 行）与组合 | 验证方案与应用结果仍属于同一次操作；不拆成会先修改一半的多个回调 |
| `SurgicalLimbOperations` | attach/detach、limb topology、连接容量与肢体规则 | 复用连接查询，持有肢体规则；不复制对象库存 |
| `SurgicalPackingOperations` | `packComponent`（1299 行）、copied/temporary capture、`packedAssembly` | 复用现有打包对齐与几何校验类，集中物品产出 |
| `SurgicalTablePersistence` | `write/read`（3555/3578 行）、加载归一化 | 最后提取；保留 `SurgicalTableSubjectSync`、`SurgicalStorage`、迁移入口与客户端增量保留策略 |

BE 保留 Create 生命周期、方块/世界访问、plane/controller 协调、同步和原公开操作转发。
初始目标是减少到约 600–900 行，再根据生命周期代码实际规模调整。

不要给每个组件完整 BE 字段访问权限；使用同包的窄操作方法，至少明确对象查询、允许的修改、
连接缓存失效和操作完成通知。第一轮可以保留现有 BE 操作方法作为这些入口，不预先搭建大型接口体系。
材料扣除、工具耐久、音效、`setChangedAndSync` 应保留在一次已接受操作的收尾阶段，避免重复执行。

### 3.2 `SurgicalAssembly`

这个文件包含大量公共嵌套数据类型，和单纯的“大控制器”不同。直接搬走全部 record 会修改大量调用方，
收益低于先拆算法与存储实现。

1. 提取 `SurgicalAssemblyPersistence`：外层 `load/save`（262/463 行）、Source 与各几何类型的 NBT
   读写实现。原 `load/loadCached/save` 及嵌套类型的读写方法保留为转发；同包内部通过受控构造入口组装数据。
2. 提取 `SurgicalAssemblyLimbTopology`：`buildLimbTopology/buildMotionGraph/drivenMotionGroup`
   （753–942 行附近），缓存仍随不可变 assembly 实例存在。
3. 如仍过大，再提取几何类型中的变换、合并、合法性判定为 `SurgicalAssemblyGeometry`；
   `BodyBounds/VisualBounds/HitboxGeometry/AttackGeometry/Source` 等公共类型先保留原限定名。

已有 `SurgicalStorage` 负责共享存储，`SurgicalReleaseMigration` 负责发布格式迁移；
新 persistence 类只做编解码组织，不建立第二套字典或迁移体系。

**存档约束：** 本次仅移动实现，不修改 table 格式 3、assembly 格式 4、NBT key、网络字段或
`mod_version`。旧发布格式支持以 [存档兼容文档](../surgical-save-compatibility.zh-CN.md)
和真实发布边界为准；如实施中确需改格式，应另立变更并先核对 `mod_version` 的历史提交、更新清单、
保留全部发布格式到当前格式的直接转换。迁移只在加载时发生并回写，不能进入 tick、渲染或 tooltip。
保留当前格式快速路径、解码缓存容量及私有 NBT cache key 副本，不修改共享只读快照。

## 4. 苦力怕爆破室

源码：`content/creeperblastchamber/CreeperBlastChamberBlockEntity.java`，相对于上述 Java 主包。

建议使用五个组件，BE 保留调度、capability、存档入口和旧 API 转发：

| 建议组件 | 方法/字段定位 | 需要保持的行为 |
| --- | --- | --- |
| `ChamberStructure` | `scanStructure`（352 行）、形成/拆除、vault 角色绑定、position cache、区块卸载暂停 | 结构变化时才重建布局；继续使用现有 vault role 类 |
| `ChamberProcessing` | `tickPressProcessing`（1035 行）、配方匹配、输出预插入、overload 与衰减 | 检查全部输出可插入后才消耗输入；同一压制周期不重复加工 |
| `ChamberContainment` | `pendingUnpacks/Appearances/Packagings/readyOutputs/storedCreepers`、1518 行起的阶段 tick | 容纳与输出状态共用一个所有者；保存剩余时间和原状态记录 |
| `ChamberInventoryAccess` | `ChamberInputHandler`（3187 行）、接收纸箱、预览提取、控制器输出请求 | 委托 containment/processing；capability handler 保持稳定，不按查询重新创建 |
| `ChamberClientEffects` 与 level 级索引 | 1719 行起粒子/压机同步、client maps、render list 缓冲 | 按客户端 level 管理并清理；保留每 tick 压机缓存和渲染列表复用 |

当前代码已经有 `clientPressControllerDimension` 和按 `Level` 分组的 loaded chamber map，
不能照搬旧审计中“完全没有维度隔离”的描述。这里的目标是集中已有的客户端状态和清理责任。

第一阶段可先提取不依赖客户端类型的表现状态与计算，把实际客户端类的使用留在客户端注册入口，
确保 common BE 不在专用服务器加载时解析客户端专用类型。保留压缩查询等 Mixin 使用的原静态方法。
`saveAdditional/loadAdditional` 继续读写原扁平字段，不能因为组合化而擅自新增一层 NBT 根节点。

尤其保留当前 tick 顺序（212 行起）：卸载暂停检查 → 旧数据处理 → unpack → appearance → packaging →
controller output request → ready output → press processing → overload decay → block state → 定期结构检查。
组件化本身不应改变这些步骤的相对顺序。

## 5. 仿生生物、动画和悦灵物流

### 5.1 `SlimeBionicEntity`

位置：`src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicEntity.java`。
装备、生产、特殊攻击、振动感知、狩猎、飞行和爬墙已经有组件，继续使用它们。

- 先将 2333 行起的七个嵌套 Goal 移到现有 `entity/ai/`，保留注册优先级、flags 和条件。
- `BionicLocomotion`：`refreshLocomotion/travel`、水陆选择、跳跃与爬行策略；
  委托已有 `BionicFlightMovement/BionicWallClimbing`，不重新实现移动器。
- `BionicMeleeCombat`：选臂、冷却、朝向、命中检测、武器属性临时应用及攻击状态。
- `BionicBodyGeometry`：实体尺寸、multipart 注册、`updateHitParts`、渲染边界与坐标转换。
- 环境呼吸、干燥和外壳逻辑视剩余规模拆到 `BionicEnvironment` 或已有防御组件。

实体保留 Minecraft override、`SynchedEntityData` 声明/定义、NBT 入口、super 调用和 tick 协调。
数据 accessor 的声明归属和定义顺序先保持原样；新组件通过访问方法写同步数据，不重新分配 accessor。
先以约 900–1,200 行为可接受范围，不为压到 500 行而把大量一行 override 再包装一层。

### 5.2 `SlimeBionicAnimator`

位置：`entity/client/SlimeBionicAnimator.java`。

- `BionicRigBuilder`：`resolveLimbs/assignBones/linkHierarchy`、腿型与步态分配。
- `BionicAttackGeometryBaker`：`bakeAttackGeometry`（93 行起）、手臂末端与装备几何。
- `BionicMobilityMeasurements`：`measureMobility`（1177 行起）、腿长、支撑和主头高度。
- `BionicPoseSolver`：`resolve`（429 行）与逐肢体变换。

继续复用 `SurgicalGait` 和 `entity/client/animation/SlimeBionicAnimations`。
保留现有 `Rig/Frame/SourceState/CubeBox` 的公共入口；冷路径骨架构建与热路径逐帧求解分开，
`Rig` 已缓存的 apply order、body pivot、步态指标不能移回每帧计算。

### 5.3 `AllayCourierTask`

位置：`content/allay/logistics/courier/AllayCourierTask.java`。
已有 `AllayCourierDispatchService/AllayCourierDeliveryService`，不重复创建“配送服务”。

- `AllayCourierPortRoute`：港口局部坐标、进离站阶段、路径点、速度和加速度（589、756、1088 行起）。
- `AllayCourierTargetRecovery`：目标丢失扫描、last-known target、兜底到达和放置（391 行起）。
- `AllayCourierEta`：1297 行起的时间估计，和实际路线参数共用常量/阶段计算。
- `AllayCourierTaskPersistence`：1524 行起的 save/load 与历史坐标转换。

Task 继续拥有任务 id、包裹、mission/phase、计时、当前坐标和维度，是唯一任务状态来源。
辅助组件返回路由/恢复结果，由 Task 执行状态转换；不要再为 route、recovery 和 Task 各存一份位置。
保存字段和阶段意义保持原样，避免重复完成配送或重复归还载体。

## 6. 后续批次与不宜机械拆分的文件

### 6.1 其他手写 Java

| 文件 | 行数 | 建议 |
| --- | ---: | --- |
| `SpiderAssemblyTableBlockEntity` | 1,411 | 拆 `SpiderHybridStorage`（slot lock、物品与流体 handler）、`SpiderProcessPlanner`（932 行起）、`SpiderProcessController`；BE 保留运输行为、菜单与生命周期。规划仍需要配方查询上下文，不能假设完全没有世界依赖 |
| `PetriDishBlockEntity` | 1,069 | 拆 `PetriDishRecording`、`PetriDishCultivation`、客户端 growth/emergence animation；BE 保留库存、流体和读写入口，保持出生空间检查及取消语义 |
| `CBConfigs` | 977 | 各功能配置定义移到 `registry/config/`，根类保留三个 spec 的组合与注册；先保留 `CBConfigs.SERVER.xxx` 访问形式、配置路径、范围与默认值 |
| `SlimeBeltInventory` | 949 | 后续按物品推进、插入/输出和存储检查职责；不与 Magma/Power 强行合并成统一传送带基类 |
| `SurgicalSourceModelRenderer` | 894 | 与 capture pipeline 拆分一起复查；已有 source cache/渲染上下文应继续复用 |
| `SurgicalSubject` | 886 | 先保持对象修改与缓存失效集中；待 store/operations 稳定后再决定是否提取 persistence |
| `CreateBiotechClient` | 679 | 可按渲染、屏幕、重载和模型注册整理，但注册表式代码不因超过某个行数就必须重构 |

### 6.2 自动生成代码与 JSON

- **`GeneratedPonderSupport`（2,186 行）**：文件头明确标记由 The Ponderer 自动生成且下次导出会覆盖。
  应在导出器/模板侧拆成 structure、entity、text/effects 等支持类，保留原 facade 并重新导出。
  当前方案不假定导出器就在本仓库。迁移 `src/generated/java` 只是区分产物与手写代码，
  本身不能解决支持类的职责集中；接入导出工具前不单独移动生成文件。
- **自动放鱼机物品模型（3,615 行）**：主要是 composite children 与变换矩阵，体积约 75 KB。
  优先检查源模型/生成过程能否复用子模型、模板化重复 transform；需保持当前 composite loader 结构。
  实施前先核对本地依赖参考的 loader 能力，不能臆造 JSON include 或直接把对象拆成任意文件。
- **Magma/Power Belt blockstate（各 1,300 行）**：优先从 datagen 规则消除维护重复，产物可继续是完整文件；
  是否改成 multipart 要单独核对状态覆盖与模型/旋转语义，收益明确后再做。
- **`en_us.json/zh_cn.json`（各 1,210 行）**：按领域拆编辑源后在构建/生成时合并为标准语言文件；
  如果维护负担尚可，保持现状即可。不能假定随意新增的语言分片会自动合并。

这些资源的行数主要反映枚举和模型数据展开，与 8,000 行的交互控制器不是同一种维护问题。

## 7. 建议提交顺序与验收

每一项可以包含数笔小提交，每笔只移动一个职责；移动代码与调整规则/修复行为分开。

1. **接触几何与拾取**：先拆 `SurgicalClientTopology` 的接触几何，再提取 handler 的几何求交/拾取；
   保留门面，确认调用关系后再迁移 selection cache。
2. **几何与会话状态**：拆 geometry store、subject geometry、grounding preparation，集中 clear/dispose；
   保留 revision、缓存预算与控制器 handoff。
3. **客户端交互**：按放置 → 切割 → 粘合/对称 → 关节/旋转迁移状态；随后收敛 visual commit 和表现。
   单独拆 capture pipeline，避免与输入路由修改叠加。
4. **服务端手术台**：先 store/connection query，再逐项 operation，最后 persistence；
   assembly 的 topology/persistence 可作为独立提交审查，不与格式升级混合。
5. **爆破室**：先客户端表现和结构，再 processing/containment/inventory；保持 tick 顺序与存储平铺结构。
6. **仿生与物流**：Goal → locomotion/combat → animator → courier；之后再处理蜘蛛工作台、培养皿和配置。

建议入口/门面约 100–300 行、一般组件约 300–800 行；复杂几何组件可更大。
最重要的验收是“一份状态只有一个所有者、依赖方向清楚、取消/失效路径可追踪”，不是所有文件达成同一行数。
首轮完成后应消除单个 8,000 行入口，避免生成另一个 3,000 行的通用 context/helper。

实施时遵守项目现有约束：

- 不创建测试文件。每笔 Java 提取至少执行 `./gradlew compileJava`；涉及打包或资源路径时再执行
  `./gradlew processResources`/`./gradlew build` 等相应检查。
- 原 facade 的调用方保持可编译，检查 Mixin/accessor 引用、配置路径、注册 id、NBT key、网络字段无意外差异。
- 纯拆分不运行 `quickPlaySmoke`、`test.py` 或其他客户端启动验证，除非用户明确要求。
  只有实际修改 Mixin 时，才按项目规则核对映射/refmap，并在可行时运行 `quickPlaySmoke`。
- 如用户后续授权运行验证，重点覆盖：切割/粘合取消与确认、跨台搬运、控制器迁移、世界卸载/资源重载、
  发布旧存档读写、爆破室卸载暂停、攻击与多部件、悦灵丢失目标/跨维度/返回载体。
- 保留现有 profiler 的静止预览复用、捕获次数、整组落地发布等观察点；拆分不承诺帧率提升，
  但不得把冷路径移入每帧、恢复全台重算或增加大规模复制。

本轮仅完成源码规模统计、主要职责/调用边界审阅和方案落档，没有运行编译或游戏验证；
工作区原有的发布、物流、打包机、场景和语言改动应保持独立。
