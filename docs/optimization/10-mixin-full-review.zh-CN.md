# Create: Biotech Mixin 全量审阅报告

状态：已按当前 `1.21.1` 工作树完成静态复核，并完成批次 0、批次 1 的实现与冒烟验证（2026-09-04）

## 1. 结论摘要

当前三个配置共声明 **95 个 Mixin**，与源码中 95 个带 `@Mixin` 的 Java 文件一一对应：

| 配置 | common | client | 合计 |
| --- | ---: | ---: | ---: |
| `create_biotech.mixins.json` | 57 | 33 | 90 |
| `create_biotech_alternate_current.mixins.json` | 2 | 0 | 2 |
| `create_biotech_sable.mixins.json` | 3 | 0 | 3 |
| 总计 | 62 | 33 | 95 |

本轮确认的 1 个 P0 发布阻断问题已在批次 0 修复：`BlockBreakingMovementBehaviourMixin` 不再依赖正常 `RETURN` 弹栈，伤害上下文现在由可校验的作用域在 `finally` 语义下关闭。

最高优先级的 P1 集中在六类边界：Surface Funnel 的坐标变换和整方法接管、Basin 的多视图一致性、全局实体同步/渲染注入、创造栏与 JEI 内部类耦合、实体红石与 Alternate Current 语义、Sable 物理兼容。其中 Basin 边界已在批次 1 完成代码收敛，剩余的是游戏内行为矩阵；旧报告中的 Creeper 状态恢复、`ModelPartRenderMixin` fallback、Basin 递归输出和 JEI layout 上下文清理已经由当前实现解决或替代，不再列为现存缺陷。

本报告的 P 级表示**整改优先级**，不是“Mixin 是否应删除”的判断：

- **P0**：可造成跨实体状态污染、数据损坏或难以恢复的错误；发布前必须修复。
- **P1**：核心玩法、启动兼容、存档/网络或高频全局路径的高风险问题；建议在下一个发行版前处理。
- **P2**：局部功能、版本漂移、异常恢复或可见性能风险；应建立测试并排期收敛。
- **P3**：低影响 accessor、代码清理或可选去耦；可随依赖升级处理。

## 2. 版本与参考基线

当前工程声明：Minecraft 1.21.1、NeoForge 21.1.234、Parchment 2024.11.17、Create `6.0.10-281`（运行范围 `[6.0.10,6.0.12)`）、Ponder 1.0.82、Flywheel 1.0.6、JEI 19.39.0.368（运行范围 `[19.21.0.247,)`）、MixinExtras 0.5.0、Sable companion 1.6.0、Sable 范围 `[1.1.3,3.0.0)`。

依据 [ref/SOURCES.md](../../ref/SOURCES.md)：

- [ref/1.21.1/Create](../../ref/1.21.1/Create) 是 Create 官方 `mc1.21.1-6.0.10` 的精确提交，可作为当前编译版本的权威基线；它不能证明 6.0.11 兼容。
- [ref/1.21.1/JustEnoughItems](../../ref/1.21.1/JustEnoughItems) 当前源码线的 specification version 为 19.39.0，与编译用 JEI 19.39.0.368 同线；项目声明的 19.21 下限和无上限范围仍远宽于实际核对面。
- [ref/1.21.1/Sable](../../ref/1.21.1/Sable) 是 Sable 2.0.3 的精确源码，而项目宣称支持 1.1.3 到 3.0.0 之前；旧版 API 不能由此推定。
- [ref/1.21.1/Simulated-Project](../../ref/1.21.1/Simulated-Project) 是最近可得的 `main`/1.3.0 源码，不是已证明与实际运行工件完全一致的发行快照。

因此，本报告对 Create 6.0.10 和 JEI 19.39.0 的源码结论是精确的；Create 6.0.11、JEI 19.21—19.38/未来版本和整个 Sable 声明范围必须靠版本矩阵补足。

## 3. P0：发布阻断项

### P0-01 `BlockBreakingMovementBehaviourMixin` 异常时泄漏伤害上下文（批次 0 已完成）

旧实现曾在 `damageEntities` 的 `HEAD` push、在 `RETURN` pop；目标方法异常退出时不会执行 `RETURN` 注入，可能让同一服务器线程后续的 Contraption 伤害读到过期上下文。

批次 0 已完成以下修改：

1. 用 MixinExtras `@WrapMethod` 包裹整个 `damageEntities`，通过 `AutoCloseable` scope 获得 `try/finally` 语义；Contraption 缺失时完整调用原方法。
2. scope 记录精确 token 和创建线程，关闭时校验 LIFO；栈失配会清空当前线程上下文并记录错误，重复关闭保持幂等。
3. 注入增加 `require = 1`、`expect = 1`，让 Create 调用点漂移在启动时显式失败，而不是静默丢失保护。

已通过编译和 `quickPlaySmoke` 的运行时注入/进世界检查。仍应补“目标实体 `hurt` 抛异常”“事件监听器抛异常”“嵌套伤害调用”和“异常后第二台 Contraption 继续伤害”四条专项行为测试；这些路径不由冒烟测试覆盖。

相关源码：[BlockBreakingMovementBehaviourMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BlockBreakingMovementBehaviourMixin.java)、[BioPackagerContraptionDamageTracker.java](../../src/main/java/com/nobodiiiii/createbiotech/content/biopackager/BioPackagerContraptionDamageTracker.java)。

## 4. P1：高优先级优化建议

### P1-01 修正 Surface Funnel 的局部/世界坐标旋转

[AbstractHorizontalFunnelBlockMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/AbstractHorizontalFunnelBlockMixin.java) 在 Create 已旋转 `HORIZONTAL_FACING` 后，又直接旋转 `ATTACHMENT_SURFACE`。这只在 `ATTACHMENT_SURFACE == DOWN`、局部坐标等同世界坐标时成立；侧面附着时，`HORIZONTAL_FACING` 是 Surface 局部方向，不能直接用世界 `Rotation` 处理。

应按以下顺序变换：

```text
oldWorldFacing = worldizeCanonical(oldLocalFacing, oldAttachment.opposite())
newWorldFacing = rotation.rotate(oldWorldFacing)
newAttachment  = rotation.rotate(oldAttachment)
newLocalFacing = localizeCanonical(newWorldFacing, newAttachment.opposite())
```

一个可复现反例是侧面 Surface：当前实现会把应保持水平的漏斗口转换成竖直世界方向。需覆盖四种 Y 轴 rotation、mirror、结构方块、Schematic 和 Contraption 变换。

### P1-02 收窄 `FluidTankRendererMixin` 的异常边界

[FluidTankRendererMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/FluidTankRendererMixin.java) 不仅捕获自定义经验球渲染的 `Throwable`，还捕获并吞掉 `original.call(...)` 的所有非 `ThreadDeath`/`VirtualMachineError` 异常。后者是 Create/Catnip 的正常 fallback；吞掉它会隐藏资源、渲染状态、链接或第三方兼容错误，并可能让损坏的图形状态继续影响本帧。

优化为：只围住本模组的经验球渲染，失败后恢复本模组自己修改的状态；`original.call` 放在 catch 外并正常传播。若确需降级，只捕获能够明确恢复的预期异常，不能把 `LinkageError`、普通 `Error` 和未知运行时错误都视为可恢复。

### P1-03 缩小 `FunnelBlockEntityMixin` 的整方法接管

[FunnelBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/FunnelBlockEntityMixin.java) 当前在 `activateExtractingBeltFunnel()` 的 `HEAD` 取消并重写完整 Create 状态机，而且对普通 Create Belt 也生效。实现目前复制了等待、模拟、占用、flap、transfer callback 和 cooldown 等 6.0.10 逻辑，但 Create 6.0.11 的任何新增副作用都可能被静默漏掉。

同一类还依赖：第二个 `WeakReference` 构造的 ordinal、字符串反射私有 `Mode`、在 `addBehaviours` 尾部替换行为对象。这些点组合后是当前 Create 升级最脆弱的单类之一。

建议：

1. 把整方法替换改成只包装最终 `DirectBeltInputBehaviour.handleInsertion(..., false)` 或捕获物品 materialize 的提交点；普通 Belt 继续完整执行 Create 原方法。
2. 用编译期 `@Invoker`/窄 accessor 或包装原返回值替代 `Class.forName` + `Enum.valueOf`。
3. 对 ordinal 注入增加 `expect = 1` 或 slice，并用 6.0.10/6.0.11 字节码或源码差异测试锁定调用点。
4. 避免替换已经注册的整个 `InvManipulationBehaviour`；优先包装其目标解析调用，减少未来旧引用悬挂的可能。

### P1-04 保证 Basin 三种库存视图和实体化输出的一致性（批次 1 已完成代码收敛）

批次 1 把相关 Mixin 和支撑类作为同一条边界完成收敛：

- 新增 `BasinItemHandlerAccess`，把 `INTERNAL`、`EXTERNAL`、`FUNNEL` 三种库存权限集中成一个显式契约；外部能力隐藏并保护控制物品，Funnel 可抽取但不能插入，配方和 Basin 自有逻辑使用原始内部 handler。
- [BasinOperatingBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinOperatingBlockEntityMixin.java) 的 `RecipeTrie.getVariants` 调用点已加 `require/expect = 1`；[BasinRecipeMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinRecipeMixin.java) 也锁定首次 capability 查询，并校验 Level、item capability、Basin 位置和空 context，不匹配即回退原调用。
- [BasinBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinBlockEntityMixin.java) 已锁定 `tryClearingSpoutputOverflow` 中模拟/提交两个调用点。实体化前会重新模拟并校验 remainder 的物品身份、组件和数量区间，成功后返回规范化 remainder，不再信任异常 handler 的任意返回值。
- 旧存档迁移改为“成功才锁存”；服务端实体区域未就绪时保留数据并延迟重试，完成后才清理 persistent tag，避免未加载实体被当作不存在。
- [ItemHelperMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/ItemHelperMixin.java) 的延迟抽取预览调用点已锁定；[BasinBlockBeltOutputMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinBlockBeltOutputMixin.java) 与 [BlockEntityPersistentDataAccessor.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BlockEntityPersistentDataAccessor.java) 复核后无需行为修改。

Create 6.0.10 使用本地精确源码核对；本机缓存的 Create 6.0.11-295 工件字节码也确认 `BasinRecipe.apply` 的 item/fluid 查询顺序、`RecipeTrie.getVariants` 唯一调用点，以及 spoutput 的模拟/提交双调用点未漂移。代码风险已收敛，但部分接收、数量大于 1、实体生成失败、模拟后目标变化、停止 Belt、六个方向、区块卸载，以及旧存档重复/缺失镜像和满 Basin，仍需专项游戏内测试。

### P1-05 避免给所有 `LivingEntity` 注册四个独立同步字段

[LivingEntitySlimeMimicMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/LivingEntitySlimeMimicMixin.java) 向每个 `LivingEntity` 增加 1 个 `SynchedEntityData` 字段；[LivingEntityButterRotationMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/LivingEntityButterRotationMixin.java) 再增加 3 个。即使世界里没有目标效果，所有生物都会承担 data item 和同步协议开销；在基类上调用 `defineId(LivingEntity.class, ...)` 还扩大了与其他模组数据注册顺序的兼容面。

建议把状态合并为一个版本化的 NeoForge attachment/自有同步 payload，或从已经同步的效果、实体时间和确定性种子推导黄油旋转阶段。至少应把 3 个旋转字段压成一个紧凑状态，并在大型生物群、加入/重连、跨维度和多模组实体数据注册下验证。

### P1-06 收敛全局实体渲染链路

[EntityRenderDispatcherSlimeMimicMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/EntityRenderDispatcherSlimeMimicMixin.java) 包装完整 `EntityRenderer.render`，[LivingEntityRendererMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/LivingEntityRendererMixin.java) 又包装/修改同一渲染链并抑制 layer，[ModelPartCubeGeometryMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/ModelPartCubeGeometryMixin.java) 进入每个 `ModelPart.Cube.compile`。快速门卫已经存在，异常清理也比旧实现完整，但覆盖面仍是所有实体和每个模型 cube。

优化方向：将 `CapturedEntityRenderTime` 的全局 depth 改成可嵌套的线程局部 token，或至少断言只在 render thread 使用；缓存同一实体/帧的捕获计划；对普通实体确保只执行常数级门卫。测试 Iris/Oculus、透明/发光/outline、隐身、盔甲层、第三方 renderer、资源重载、递归/嵌套渲染和异常退出。

### P1-07 不再向创造栏核心内容插入 `ItemStack.EMPTY`

[CreativeModeTabMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/CreativeModeTabMixin.java) 对主标签完整替换 `buildContents`，并用空栈填充布局。空栈破坏了 `CreativeModeTab` 输出通常只含有效展示物的约定，[JeiItemStackListFactoryMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/JeiItemStackListFactoryMixin.java) 因此必须再挂到 JEI 内部日志调用上压制错误。这是一条由上游无效数据制造、下游内部 Mixin 掩盖的耦合链。

建议保持标签 display/search 集合只含有效物品，把标题、分隔和补位作为 Creative Screen 的独立布局元数据绘制；同时保留 `ItemDisplayParameters.enabledFeatures()` 等原生成条件。完成后删除 JEI logger Mixin。若短期无法移除，应至少对 JEI 注入失败输出一次明确诊断，不能靠 `require = 0` 静默失效。

### P1-08 让 JEI 版本声明与内部类注入匹配

项目直接注入 `RecipeLayout`、`RecipeSlot` 和 `ItemStackListFactory` 等 JEI implementation class，却声明 `[19.21.0.247,)` 且没有上限。`@Pseudo` 只能处理类缺失，不能保证类存在但字段/方法 descriptor 改变时兼容；`require = 0` 也只会静默丢功能。

另有一个明确的映射整改项：`JeiRecipeLayoutMixin` 的类级 `@Mixin(..., remap = false)` 会关闭默认 remap，但两个 `@WrapOperation` 的 `@At` 目标 descriptor 都包含映射类型 `net.minecraft.client.gui.GuiGraphics`。应在 injector 和嵌套 `@At` 上显式启用 `remap = true`，再检查生产映射结果。不能因为目标 owner 属于 JEI 就关闭 descriptor 中 Minecraft 类型的映射。当前 Gradle 配置强制重跑 `compileJava` 后没有产出任何 `*refmap*.json`，所以还需先确认 ModDevGradle 的等价生产 remap 产物，或补齐可检查的 refmap 生成配置。

建议二选一：

1. 把 JEI 运行范围收窄到已经验证的 19.39.x；或
2. 新增 JEI mixin plugin，按每个目标的字段和完整方法 descriptor 门控，并对被关闭的功能记录一次版本化警告。

`JeiRecipeLayoutMixin` 已改为 `@WrapOperation` + `try/finally`，`ItemApplicationCategoryMixin` 也只在自定义 renderer 成功时取消；这两个旧问题已解决。剩余重点是内部类版本面和 `JeiRecipeSlotMixin` 的全局高频注入。

### P1-09 降低固定胡萝卜钓竿 AI 的侵入和扫描成本

[TemptGoalFixedCarrotFishingRodMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/TemptGoalFixedCarrotFishingRodMixin.java) shadow 约 12 个私有字段，并为固定目标取消 `start`/`tick` 等原版行为；[FollowTemptationFixedCarrotFishingRodMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/FollowTemptationFixedCarrotFishingRodMixin.java) 接管 Brain behavior；[TemptingSensorFixedCarrotFishingRodMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/TemptingSensorFixedCarrotFishingRodMixin.java) 在没有玩家目标时查询 POI 和相交的 Sable 子层级。

长期应实现独立 Goal/Behavior，通过公开 goal/brain 注册点与原诱惑行为并列，减少复制私有状态机。短期至少给 sensor 路径增加冷却或按 level/sublevel 建立固定钓竿索引，避免大量生物每个 sensor tick 重复空间查询；并测试玩家重新出现时的抢占、panic/breed、导航失败、目标卸载和 memory 清理。

### P1-10 固定实体红石语义并隔离 Alternate Current

[SignalGetterEntityRedstoneMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/SignalGetterEntityRedstoneMixin.java) 修改四个基础信号查询，[RedStoneWireEntityRedstoneMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/RedStoneWireEntityRedstoneMixin.java) 以 priority 500 修改红石线求值；即使有 `instanceof EntityRedstoneLevelAccess` 快速门卫，这仍是服务器红石高频核心路径。

必须建立语义矩阵：直接/弱信号、导体、二极管、比较器、观察者、红石线、源所在格和邻格、源增删、死亡/传送、区块卸载；另做“零实体源”和“高密度源”查询基准。应明确 `getBestNeighborSignal` 内部再次调用 `getSignal` 时是否会重复加入自格/邻格语义，不能只验证单根红石线亮起。

Alternate Current 两个可选 Mixin 目前只由 class resource 门控。应再检查 `Node.pos` 字段及 `WireHandler.getExternalPower` 的完整 descriptor，或限制兼容工件版本；测试未安装、只安装不兼容版、兼容版三种启动路径，以及 vanilla evaluator 与 Alternate Current evaluator 的数值一致性。

### P1-11 收紧 Sable 物理兼容范围和数值前置条件

[UniversalJointBlockEntitySableMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/compat/sable/UniversalJointBlockEntitySableMixin.java) 已从旧版反射改为直接 Sable API，但当前只检查 `MassData` 非空和 `isInvalid()`；本地 Sable 2.0.3 API 中质心仍可能为空，不能由 `isInvalid()` 推导为非空。需要同时验证质心、peer/handle 生命周期、逆质量和最终冲量均为有限值，并在子层级卸载或物理对象失效时跳过本 substep。

`SableMixinPlugin` 现已把 Simulated 与 Sable 门控分开，这是修复；但门控仍只验证类资源，无法支撑 `[1.1.3,3.0.0)` 的宽范围。应做方法/字段 descriptor probe 或收窄版本范围。`SimBlockMovementChecksMixin` 对应的 Simulated 源码已有 `registerAdditionalBlocks` 公共 API，应迁移后删除该 Mixin。`UniversalJointEndpointBlockSableMixin` 同时挂两个目标，建议拆分以支持单目标降级。

### P1-12 防止无线库存响应写入过期菜单会话

[LogisticalStockResponsePacketMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/LogisticalStockResponsePacketMixin.java) 只要当前菜单是无线菜单、holder 非空且目标坐标相同，就把 Create 的方块实体查询替换成 `menu.contentHolder`。坐标相同不足以证明响应仍属于当前请求：维度切换、菜单关闭后重开、holder removed 或网络延迟都可能复用位置。

建议给自有请求/响应增加会话 id、维度 key 和 holder 生命周期校验；长期最好使用自有 packet handler，不再冒充 `ClientLevel.getBlockEntity` 的返回值。至少测试快速换目标、同坐标跨维度、关闭重开、延迟/重复/乱序响应。

## 5. 其余 P2/P3 优化方向

### 配置与映射

- 主配置 `required: true`、`defaultRequire: 1` 适合核心行为；可降级的 JEI/纯视觉 Mixin 应拆到带插件诊断的可选配置，而不是把全局 `defaultRequire` 改为 0。
- `JeiRecipeLayoutMixin` 是本轮发现的明确 remap 整改项：类级 `remap = false` 向下影响了包含 `GuiGraphics` 的两个调用目标。`BlockEntityPersistentDataAccessor`、`AlternateCurrentNodeMixin` 等其他映射成员保持默认 remap；纯第三方成员才应局部使用 `remap = false`。以后每次改 descriptor 都仍需检查生成 refmap。
- `CameraMixin.setup`、JEI 兼容点等 `require = 0` 需要一次性诊断；否则升级后功能消失但日志没有因果线索。

### Funnel、Tunnel 与运输

- `BeltFunnelBlockStateMixin` 给所有 Create Belt Funnel 增加六值属性，属于存档协议。属性名、默认值和方向语义发布后不能随意改变；补旧世界、结构、Schematic、Ponder 和网络状态测试。
- `BeltFunnelBlockMixin` 与 `MagmaBeltFunnelBlockMixin` 修改相同目标方法，应统一分派或明确顺序，避免第三方依赖偶然的 Mixin 应用次序。
- `BeltTunnelBlockMixin` 复制部分 Create tunnel 状态逻辑；升级 Create 时逐分支 diff。`BeltTunnelItemMixin`、`BeltTunnelInteractionHandlerMixin` 和 capability invalidator 保持窄注入，可列 P2。
- `ItemHelperMixin` 位于 Create 通用抽取 helper 的高频路径。保留首个空槽快速返回，测试 `EXACTLY` 重试、predicate、副作用 handler 以及 simulate/commit 不一致。

### 渲染与 UI

- `CreativeModeInventoryScreenMixin` 调用的 `CreativeTabSectionRenderer` 修改深度测试并 push pose，但当前没有用 `try/finally` 恢复；应把状态恢复放进 finally，并在标签切换/init 时清除静态 `currentRow`。
- `CreeperRendererMixin` 当前已使用 `@WrapMethod` + `try/finally`，旧版 P1 已降为 P2；仍需测试它和另一个 `LivingEntityRenderer` 包装的嵌套顺序。
- `DeltaTrackerTimerMixin` 的捕获深度应改为线程局部或显式 render-thread 断言。
- `SpoutCategoryMixin` 可只包装 `AnimatedSpout.draw`，保留 Create 分类布局；`ItemApplicationCategoryMixin` 已有失败回退，不再是阻断项。
- `LevelRendererAccessor`、Creative screen/menu accessor 和简单纹理 invoker 属于 P3；有公开 API 时迁移即可。

### 存档、网络与可选兼容

- `SmartBlockEntityLegacyRefreshMixin` 和 Basin legacy 迁移都应保证“一次成功后清标记；失败可重试且不重复生成”。
- `BlockEntityConfigurationPacketMixin`、`ServerGamePacketListenerAccessor` 涉及客户端预测与跨空间方块实体定位，测试重复序列、过期序列、子层级卸载和权限校验。
- `UniversalJointEndpointBlockSableMixin` 的 before/after move 应在移动失败、异常和部分端点加载时恢复 listener/lift 状态。

## 6. 95 项全量清单

下表给每个 Mixin 一个单一最高优先级。P1/P2/P3 代表该类最需要处理的风险，不表示整类所有代码都同级。

### 6.1 主配置 common（57）

| Mixin | 作用摘要 | P级 | 审阅结论/下一步 |
| --- | --- | --- | --- |
| `AbstractContraptionEntityBufferPadMixin` | Contraption 缓冲垫 tick 补偿 | P2 | 缓存是否含缓冲垫；验证卸载/拆分后失效 |
| `AbstractVillagerAccessor` | 读写交易报价字段 | P3 | 窄 accessor，可保留 |
| `AbstractVillagerSlimeMimicTradesMixin` | 拟态交易载入/恢复 | P2 | 测试职业变化、旧 NBT 和普通报价 |
| `AbstractHorizontalFunnelBlockMixin` | Surface Funnel 旋转 | P1 | 修复局部/世界坐标变换 |
| `AssemblyOperatorBlockItemMixin` | 识别 Biotech Belt | P3 | 普通方块完整回退；Create 升级核对 predicate |
| `BasinBlockEntityMixin` | Basin handler 视图、迁移、实体化输出 | P1 | 批次 1 已收敛契约/迁移/提交校验；补游戏内失败矩阵 |
| `BasinBlockBeltOutputMixin` | 允许向 Biotech Belt 输出 | P2 | 批次 1 已复核，无需改动；仍测方向、满载和无 host |
| `BasinOperatingBlockEntityMixin` | 配方索引读取内部 handler | P1 | 批次 1 已锁定唯一 `RecipeTrie.getVariants` 调用点 |
| `BasinRecipeMixin` | 配方应用读取内部 handler | P1 | 批次 1 已校验 capability/Level/pos/context 并锁定调用点 |
| `BlockEntityConfigurationPacketMixin` | 跨空间配置包 BE 解析 | P1 | 补维度、权限、卸载和预测序列测试 |
| `BlockEntityPersistentDataAccessor` | 无分配读取持久数据字段 | P3 | 批次 1 已复核；默认 remap 正确，保持只读 |
| `BlockBreakingMovementBehaviourMixin` | Contraption 伤害上下文 | P0 | 批次 0 已改为校验式 scope + `@WrapMethod`；补异常行为测试 |
| `CreativeModeTabMixin` | 自定义主创造栏布局 | P1 | 移除核心列表中的空栈 |
| `BrassTunnelBlockEntityMixin` | 非标准 Belt 的 Tunnel 路由 | P1 | Create 版本逐分支 diff；模拟/提交一致性 |
| `ContraptionMixin` | 自有 Belt 的 Contraption 搬运 | P1 | 测试装配、拆解、跨区块和异常回滚 |
| `DeployerMovementBehaviourMixin` | Deployer 捕获实体/蓝图路径 | P1 | 取消原方法分支需覆盖失败和掉落 |
| `BeltTunnelBlockMixin` | 自有 Belt 的 Tunnel 状态机 | P1 | 建立普通 Create Belt 对照测试 |
| `BeltTunnelItemMixin` | 放置时补充 casing/侧效应 | P2 | 保持与 Create placement 同源 |
| `BeltTunnelBlockEntityCapabilityMixin` | 清理 Tunnel capability 缓存 | P2 | 验证邻居变化/重载后重新解析 |
| `BeltTunnelInteractionHandlerMixin` | Tunnel 插入/抽取桥接 | P2 | 测试模拟、阻塞和 flap |
| `LaunchedItemForBeltMixin` | 持久化链/偏移数据 | P2 | 校验 NBT 长度、旧数据和 place 失败 |
| `SchematicannonBlockEntityMixin` | Schematicannon 链数据发射 | P1 | HEAD 取消路径逐项保留消耗/失败语义 |
| `BeltFunnelBlockMixin` | Surface Funnel 状态/几何/有效性 | P1 | 与旋转修复共同验证；减少同目标多 Mixin |
| `BeltFunnelBlockStateMixin` | 增加 attachment surface 属性 | P1 | 固定存档协议，补旧世界/结构测试 |
| `BeltFunnelShapeMixin` | Surface shape/碰撞 | P2 | 六向 shape 与 ItemEntity 碰撞矩阵 |
| `FunnelBlockMixin` | 放置、revert、实体进入扩展 | P2 | 非 Surface 和普通物品必须完整回退 |
| `FunnelBlockEntityMixin` | Funnel 模式、能力、抽取状态机 | P1 | 缩小整方法接管并移除字符串反射 |
| `FunnelItemMixin` | Surface Funnel 放置解析 | P2 | 测试相邻多个 surface 的确定性 |
| `FluidTankBlockEntityMixin` | 特殊流体读取兼容 | P2 | 保护 controller/非 controller 和旧 NBT |
| `FollowTemptationFixedCarrotFishingRodMixin` | Brain 诱惑行为固定目标 | P1 | 迁移独立 behavior，避免取消原 tick |
| `ItemHelperMixin` | 捕获物品延迟抽取预览 | P2 | 批次 1 已锁定调用点；仍测 EXACTLY 和非幂等 handler |
| `MagmaBeltFunnelBlockMixin` | Magma Belt Funnel shape/扳手 | P2 | 与 `BeltFunnelBlockMixin` 统一分派 |
| `LivingEntitySlimeMimicHurtSoundMixin` | 拟态受伤声 | P2 | 普通实体快速回退；服务端/客户端各测 |
| `LivingEntitySlimeMimicMixin` | 全 LivingEntity 拟态同步状态 | P1 | 合并基类同步字段 |
| `LivingEntityButterRotationMixin` | 全 LivingEntity 黄油旋转状态 | P1 | 三字段压缩或由已同步状态推导 |
| `HauntingTypeSlimeMimicMixin` | Haunting 对拟态实体分流 | P2 | 取消路径需保留原副作用/失败返回 |
| `NozzleBlockMixin` | Nozzle 与 Belt/子空间适配 | P2 | 普通 Nozzle 和无 host 路径对照 |
| `NetherPortalBlockMixin` | Portal 状态/流体扩展 | P2 | 测试原版传送门形状、更新和流体查询 |
| `PortalForcerMixin` | 传送门查找/创建适配 | P1 | 世界生成边界、跨维度和失败回退 |
| `MobAccessor` | 清除持久化标记 | P3 | 窄 accessor；只在成对恢复路径使用 |
| `PackagerBlockEntityMixin` | 唤醒上方 Allay Port | P2 | 已移入主配置；Create 生命周期升级检查 |
| `PackageItemCardboardBoxMixin` | 包装箱捕获实体数据 | P2 | 版本化 payload，验证复制/拆包/网络 |
| `PressingBehaviourSoundMixin` | Chamber Press 声音同步 | P2 | Redirect 调用点升级核对 |
| `PowerBeltWalkAnimationMixin` | Belt 表面移动动画 | P2 | 测试脱离、倒向、零速和死亡 |
| `WalkAnimationStateAccessor` | 行走动画私有字段 | P3 | 可保留；有公开 delta API 时移除 |
| `MixinSuperGlueSelectionHelper` | Smart Glue 选择过滤 | P2 | 服务端选择范围与权限对照 |
| `SawBlockEntityMixin` | Saw 释放捕获实体 | P2 | 取消配方路径时保留消耗/输出/掉落 |
| `ServerLevelEntityRedstoneMixin` | 服务器实体红石索引 | P1 | 生命周期、区块卸载和查询基准 |
| `ServerGamePacketListenerAccessor` | 读取方块预测确认序列 | P2 | 网络字段耦合；测试乱序/重复 |
| `SignalGetterEntityRedstoneMixin` | 全局信号查询叠加实体源 | P1 | 固定自格/邻格/弱强信号语义 |
| `SmartBlockEntityLegacyRefreshMixin` | 旧状态一次性刷新 | P2 | 成功清标记、失败可重试且幂等 |
| `SpawnEggItemMixin` | 生成蛋拟态/捕获处理 | P2 | dispenser/useOn/use 与失败返回对照 |
| `StockKeeperRequestMenuAccessor` | 无线菜单权限/锁字段 | P2 | 私有 Create 字段；升级时检查 descriptor |
| `TemptGoalFixedCarrotFishingRodMixin` | Goal 诱惑固定目标 | P1 | 减少私有字段 shadow 和整段状态机复制 |
| `TemptingSensorFixedCarrotFishingRodMixin` | Brain sensor 查固定钓竿 | P1 | 增加冷却/索引，控制跨空间扫描 |
| `VillagerSlimeMimicTradesMixin` | 拟态村民交易更新 | P2 | 测试升级、补货、职业切换和普通交易 |
| `RedStoneWireEntityRedstoneMixin` | 红石线求值叠加实体源 | P1 | 与 vanilla/Alternate Current/其他优化模组对照 |

### 6.2 主配置 client（33）

| Mixin | 作用摘要 | P级 | 审阅结论/下一步 |
| --- | --- | --- | --- |
| `client.BasinRendererMixin` | Basin 捕获输出可视化 | P2 | PoseStack/缓冲区异常恢复测试 |
| `client.CameraMixin` | 传送器第一人称相机偏移 | P2 | `require=0` 加诊断；测试视角切换 |
| `client.ClientLevelMixin` | 放置预测 sequence 桥接 | P2 | 私有 handler shadow；优先公开 getter |
| `client.CompositeRenderStateAccessor` | 读取 composite texture state | P2 | 渲染内部字段；资源重载测试 |
| `client.CompositeRenderTypeAccessor` | 读取 composite state | P2 | 与上一 accessor 成对维护 |
| `client.CreeperAccessor` | 临时读写 swell | P3 | 窄 accessor；调用方已 finally 恢复 |
| `client.CreeperRendererMixin` | Ponder Creeper 脉动 | P2 | 当前 `WrapMethod`/finally 正确；测嵌套顺序 |
| `client.CreativeModeInventoryScreenAccessor` | 读取创造栏 UI 字段 | P3 | 低风险映射耦合 |
| `client.CreativeModeInventoryScreenMixin` | 绘制标题/行布局 | P2 | finally 恢复 render state；重置静态行 |
| `client.DeltaTrackerTimerMixin` | 捕获渲染固定 partial tick | P2 | 改线程局部 depth 或断言 render thread |
| `client.EntityRenderDispatcherSlimeMimicMixin` | 包装完整实体 renderer | P1 | 缩窄捕获面、缓存并做第三方 renderer 矩阵 |
| `client.FlapStuffsMixin` | Funnel flap 坐标变换 | P2 | 可嵌套 token + finally |
| `client.FluidTankRendererMixin` | 经验流体球渲染 | P1 | 不得吞掉 original renderer 异常 |
| `client.FunnelRendererMixin` | Funnel BER 坐标变换 | P2 | scope/pose 异常恢复 |
| `client.FunnelVisualMixin` | Flywheel Funnel visual 适配 | P2 | Flywheel 开关、重建、卸载测试 |
| `client.GoggleOverlayRendererMixin` | Chamber/拟态 Goggle 信息 | P2 | 缓存目标查找；可用 proxy API 的分支迁移 |
| `client.HumanoidArmorLayerMixin` | 特定纹理 render type 替换 | P3 | 纹理身份门卫充分，升级核对调用点 |
| `client.ItemApplicationCategoryMixin` | 自定义 JEI application 预览 | P2 | 成功才取消已正确；收窄 JEI 版本 |
| `client.ItemPickerMenuMixin` | 记录创造栏滚动行 | P3 | 避免全局静态状态跨 screen 泄漏 |
| `client.JeiItemStackListFactoryMixin` | 压制主标签空栈日志 | P1 | 修复上游列表后删除 |
| `client.JeiRecipeLayoutMixin` | JEI hover/slot context | P1 | finally 已正确；修正 mapped descriptor 的 remap |
| `client.JeiRecipeSlotMixin` | 全局捕获箱槽位 renderer | P1 | JEI 内部高频路径；版本门控和快速门卫 |
| `client.LivingEntityRendererMixin` | 包装全 LivingEntity renderer/layer | P1 | 与 dispatcher/Creeper 顺序和异常矩阵 |
| `client.LogisticalStockResponsePacketMixin` | 无线库存响应重路由 | P1 | 增加会话、维度和 holder 生命周期校验 |
| `client.PressingBehaviourMixin` | Chamber Press 动画相位 | P2 | 单点覆盖；升级核对签名 |
| `client.SpoutCategoryMixin` | 自定义 JEI Spout 场景 | P2 | 只包装动画调用，保留原分类布局 |
| `client.StockKeeperRequestScreenMixin` | 无线库存 Screen 生命周期 | P1 | 远程失效、换目标、断线和重开测试 |
| `client.TextureStateShardAccessor` | 调用 cutout texture invoker | P2 | MC 渲染内部 API；资源包矩阵 |
| `client.LevelRendererAccessor` | 读取 renderer ticks | P3 | 优先公开动画时间 API |
| `client.MixinSuperGlueSelectionHandler` | 客户端 Smart Glue 选择 | P2 | 与服务端判定保持一致 |
| `client.ModelPartAccessor` | 遍历 cubes/children | P2 | 模型内部字段；资源重载和自定义模型测试 |
| `client.ModelPartCubeGeometryMixin` | 捕获每个 cube 几何 | P2 | 全局高频点；普通路径必须常数级 |
| `client.WorldSectionElementImplMixin` | Ponder 透明 late buffer | P2 | Ponder 内部类；渲染阶段/版本检查 |

### 6.3 可选兼容配置（5）

| 配置 | Mixin | 作用摘要 | P级 | 审阅结论/下一步 |
| --- | --- | --- | --- | --- |
| Alternate Current | `AlternateCurrentNodeMixin` | 暴露 wire node 位置 | P1 | 目标字段 descriptor 门控；可选版本矩阵 |
| Alternate Current | `AlternateCurrentWireHandlerMixin` | 外部功率叠加实体源 | P1 | 与 vanilla evaluator 数值一致；签名门控 |
| Sable | `SimBlockMovementChecksMixin` | assembly 追加 Biotech blocks | P1 | 改用 `registerAdditionalBlocks` 后删除 |
| Sable | `UniversalJointEndpointBlockSableMixin` | 端点 listener/lift 接口 | P1 | 拆分两个目标；失败恢复 |
| Sable | `UniversalJointBlockEntitySableMixin` | 物理 substep 冲量 | P1 | nullable COM、失效 peer、有限值和版本范围 |

## 7. 与旧报告相比的状态变化

### 已删除或被替代

- `BasinBlockMixin`、`BasinInventoryMixin` 已删除；旧版堆栈扫描白名单和递归 `acceptOutputs` 结论作废，由 handler view + recipe call-site 包装取代。
- `BeltMovementHandlerMixin` 已删除；运输适配不再通过该旧全局入口。
- `client.ModelPartRenderMixin` 已删除；旧版“fallback 仍处在跳过 ModelPart 上下文导致零顶点”的问题由 `EntityRenderDispatcherSlimeMimicMixin` + surgical capture 方案替代。
- 独立 `create_biotech_allay.mixins.json` 已删除；`PackagerBlockEntityMixin` 已进入主配置。

### 已解决但仍需回归

- `BlockBreakingMovementBehaviourMixin` 已用作用域式 `@WrapMethod` 保证异常退出也关闭伤害上下文，并增加 token/LIFO 校验。
- Basin 相关 Mixin 已统一三种 handler 权限、守卫配方 capability 调用、校验 spoutput 模拟结果，并让旧数据迁移在实体区域就绪后才提交。
- `CreeperRendererMixin` 已改为 `@WrapMethod` 并在 `finally` 恢复 pose/context/swell。
- `JeiRecipeLayoutMixin` 已用 `@WrapOperation` 和 `finally` 结束 hover/slot context。
- `ItemApplicationCategoryMixin` 仅在自定义 renderer 成功时取消，失败可回到 Create 原渲染。
- Sable 兼容已移除主要反射调用，并把 Simulated/Sable 的类存在性门控分开；剩余问题是签名/版本范围和物理前置条件。

### 本轮新增审阅面

- Alternate Current 2 项。
- Basin handler-view/recipe 路由 3 项及无分配持久数据 accessor。
- 创造栏/JEI 空栈布局链路 5 项。
- 实体完整渲染捕获与模型 cube 几何链路。
- 黄油旋转的 LivingEntity 同步字段。
- 固定胡萝卜钓竿的 Goal/Brain/Sensor 三条 AI 路径。
- 实体红石索引、SignalGetter 和红石线/Alternate Current 求值。

## 8. 建议实施顺序

1. **批次 0（已完成，P0-01）**：Contraption 伤害上下文改为校验式 scope 和 `try/finally`；专项异常/嵌套行为测试仍待补。
2. **批次 1（已完成代码收敛，P1-04）**：聚合 Basin handler、配方索引/应用、Belt 输出、延迟抽取和旧数据迁移；专项游戏内矩阵仍待补。
3. **批次 2（P1-01、P1-03）**：聚合 Surface Funnel 坐标、放置和抽取状态机，修正旋转并缩小整方法接管。
4. **批次 3（P1-02、P1-05、P1-06）**：聚合实体同步与渲染热路径，收紧异常边界并减少全局状态/字段。
5. **批次 4（P1-07、P1-08）**：聚合创造栏和 JEI，移除空栈及 logger workaround，再收窄 JEI 兼容范围。
6. **批次 5（P1-09、P1-10、P1-11、P1-12）**：按 AI、红石、Sable、无线库存四个边界分别施工并完成版本矩阵。
7. 再处理 P2 的异常恢复、版本 drift 和性能基准；P3 accessor 随依赖升级清理。

## 9. 验证矩阵

| 层级 | 必测内容 | 通过标准 |
| --- | --- | --- |
| 配置/编译 | `./gradlew compileJava --rerun-tasks`；检查生产 remap/refmap 产物 | 95 项配置与源码一致；mapped descriptor 有可检查的映射结果；无 Mixin AP 错误 |
| Create 版本 | 6.0.10 与声明支持的 6.0.11 | 所有 ordinal、private method/field 和复制状态机均有明确结果 |
| 原版/Create 回归 | 普通 Basin/Funnel/Belt/Tunnel/Fluid Tank/Contraption/实体/创造栏 | 非目标路径完整执行原行为，输出、冷却、事件、声音和 UI 不变 |
| Surface | 六个 attachment、四种 rotation、mirror、结构/Schematic/Contraption | 世界朝向、shape、碰撞、目标 handler 和回退 Funnel 一致 |
| Basin | 内/外/Funnel handler、配方索引/应用、部分接收、实体生成失败、旧 NBT | 不重复、不丢失、不泄露控制物品；simulate/commit 一致 |
| AI | Goal/Brain 实体、玩家抢占、POI、多子层级、大型生物群 | memory 可清理，行为不锁死，扫描成本有界 |
| 红石 | vanilla/Alternate Current、强弱信号、导体/线/比较器、增删/卸载 | 两套 evaluator 语义一致；零源开销可接受 |
| 客户端 | JEI 版本矩阵、Iris/Oculus、透明/outline、资源重载、渲染异常 | scope/pose/render state 必定恢复；非目标渲染无变化 |
| 可选依赖 | 无 Sable/Simulated/AC、单独安装、兼容版、不兼容版 | 只禁用对应能力；日志给出明确原因；不在 apply/首 tick 崩溃 |
| 网络/存档 | 旧 NBT、跨维度、菜单换目标、延迟/重复包、断线重连 | 迁移幂等；过期响应不写入新会话 |

本轮已完成的机械验证：配置清单为 57 common + 33 client + 2 Alternate Current + 3 Sable，95 个配置项与 95 个 Mixin 源文件数量一致；`./gradlew compileJava --rerun-tasks --no-daemon` 成功，只有现存的 27 个 deprecated API 警告；`./gradlew quickPlaySmoke --no-daemon` 成功，在时限内进入世界并正常清理，日志未发现本批 Mixin 的 apply/injection 失败；构建 jar 已包含新增契约类和修改后的 Mixin。Markdown 表格分别包含 57、33、5 行，文档内相对链接均存在，`git diff --check` 通过。当前构建仍没有生成 `*refmap*.json`，因此不能用 refmap 关闭映射验证项。

冒烟验证证明当前组合能够完成运行时注入、启动和进世界，但不等同于 P0 异常分支或 Basin 行为矩阵全部正确。后续仍需执行上文列出的异常/嵌套伤害及 Basin 模拟—提交、实体生成失败、方向/卸载和旧存档迁移测试。客户端日志中仍可复现 P1-07 所述的 JEI 空 `ItemStack` 错误，该问题不属于批次 0/1，留待批次 4 处理。
