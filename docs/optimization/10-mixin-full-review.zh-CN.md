# Create: Biotech Mixin 全量审阅报告

状态：已按当前 `1.21.1` 工作树完成静态复核，并完成批次 0—4 的实现与冒烟验证（2026-09-04）

后续清理（2026-09-14）：已删除 `SpoutCategoryMixin` 及其配置项。当前自带的鱿鱼打印机制作配方为 `create:item_application`，没有触发该补丁的灌注配方；打印动画由独立的 `SquidPrinterJeiCategory` 直接调用 `AnimatedSquidSpout` 绘制。本报告的数量和验证记录保留 2026-09-04 审计基线。

## 1. 结论摘要

当前三个配置共声明 **92 个 Mixin**，与源码中 92 个带 `@Mixin` 的 Java 文件一一对应：

| 配置 | common | client | 合计 |
| --- | ---: | ---: | ---: |
| `create_biotech.mixins.json` | 55 | 32 | 87 |
| `create_biotech_alternate_current.mixins.json` | 2 | 0 | 2 |
| `create_biotech_sable.mixins.json` | 3 | 0 | 3 |
| 总计 | 60 | 32 | 92 |

本轮确认的 1 个 P0 发布阻断问题已在批次 0 修复：`BlockBreakingMovementBehaviourMixin` 不再依赖正常 `RETURN` 弹栈，伤害上下文现在由可校验的作用域在 `finally` 语义下关闭。

最高优先级的 P1 集中在六类边界：Surface Funnel 的坐标变换和整方法接管、Basin 的多视图一致性、全局实体同步/渲染注入、创造栏与 JEI 内部类耦合、实体红石与 Alternate Current 语义、Sable 物理兼容。其中 Basin 边界已在批次 1、Surface Funnel 坐标与抽取状态机已在批次 2、实体同步与渲染 scope 已在批次 3、创造栏与 JEI 边界已在批次 4 完成代码收敛，剩余的是各批次的游戏内行为矩阵和批次 5 的四个 P1 边界；旧报告中的 Creeper 状态恢复、`ModelPartRenderMixin` fallback、Basin 递归输出和 JEI layout 上下文清理已经由当前实现解决或替代，不再列为现存缺陷。

本报告的 P 级表示**整改优先级**，不是“Mixin 是否应删除”的判断：

- **P0**：可造成跨实体状态污染、数据损坏或难以恢复的错误；发布前必须修复。
- **P1**：核心玩法、启动兼容、存档/网络或高频全局路径的高风险问题；建议在下一个发行版前处理。
- **P2**：局部功能、版本漂移、异常恢复或可见性能风险；应建立测试并排期收敛。
- **P3**：低影响 accessor、代码清理或可选去耦；可随依赖升级处理。

## 2. 版本与参考基线

当前工程声明：Minecraft 1.21.1、NeoForge 21.1.234、Parchment 2024.11.17、Create `6.0.10-281`（运行范围 `[6.0.10,6.0.12)`）、Ponder 1.0.82、Flywheel 1.0.6、JEI 19.39.0.368（运行范围 `[19.39.0.368,19.40)`）、MixinExtras 0.5.0、Sable companion 1.6.0、Sable 范围 `[1.1.3,3.0.0)`。

依据 [ref/SOURCES.md](../../ref/SOURCES.md)：

- [ref/1.21.1/Create](../../ref/1.21.1/Create) 是 Create 官方 `mc1.21.1-6.0.10` 的精确提交，可作为当前编译版本的权威基线；它不能证明 6.0.11 兼容。
- [ref/1.21.1/JustEnoughItems](../../ref/1.21.1/JustEnoughItems) 当前源码线的 specification version 为 19.39.0，与编译用 JEI 19.39.0.368 同线；批次 4 已把运行范围收窄到 `[19.39.0.368,19.40)`。
- [ref/1.21.1/Sable](../../ref/1.21.1/Sable) 是 Sable 2.0.3 的精确源码，而项目宣称支持 1.1.3 到 3.0.0 之前；旧版 API 不能由此推定。
- [ref/1.21.1/Simulated-Project](../../ref/1.21.1/Simulated-Project) 是最近可得的 `main`/1.3.0 源码，不是已证明与实际运行工件完全一致的发行快照。

因此，本报告对 Create 6.0.10 和 JEI 19.39.0 的源码结论是精确的；Create 6.0.11、JEI 19.39.x 的其他构建和整个 Sable 声明范围仍需靠版本矩阵补足。JEI 19.21—19.38 及 19.40 以后版本已不再声明兼容。

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

### P1-01 修正 Surface Funnel 的局部/世界坐标旋转（批次 2 已完成）

[AbstractHorizontalFunnelBlockMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/AbstractHorizontalFunnelBlockMixin.java) 的旧实现曾在 Create 已旋转 `HORIZONTAL_FACING` 后直接旋转 `ATTACHMENT_SURFACE`。这只在 `ATTACHMENT_SURFACE == DOWN`、局部坐标等同世界坐标时成立；侧面附着时会混用两个坐标系。

批次 2 已把 rotation 改为完整的世界空间变换：

```text
oldWorldFacing = worldizeCanonical(oldLocalFacing, oldAttachment.opposite())
newWorldFacing = rotation.rotate(oldWorldFacing)
newAttachment  = rotation.rotate(oldAttachment)
newLocalFacing = localizeCanonical(newWorldFacing, newAttachment.opposite())
```

同时新增独立 mirror 路径：先对旧 local facing 做 worldize，再分别用 `Mirror.mirror` 变换世界朝向和 attachment，最后在新 surface frame 下 localize。这样不再依赖 Create 用局部 `HORIZONTAL_FACING` 推导世界 mirror rotation。两个注入均增加 `require/expect = 1`，普通 `ATTACHMENT_SURFACE == DOWN` 状态仍退化为 Create 原有水平语义。

编译和 `quickPlaySmoke` 已通过；仍需重点覆盖六个 attachment、四种 Y 轴 rotation、两种 mirror，以及结构方块、Schematic 和 Contraption 组合变换，确认漏斗口世界方向、shape、碰撞与目标 Belt 同步变化。

### P1-02 收窄 `FluidTankRendererMixin` 的异常边界（批次 3 已完成）

[FluidTankRendererMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/FluidTankRendererMixin.java) 旧实现不仅捕获自定义经验球渲染的 `Throwable`，还捕获并吞掉 `original.call(...)` 的所有非 `ThreadDeath`/`VirtualMachineError` 异常。后者是 Create/Catnip 的正常 fallback；吞掉它会隐藏资源、渲染状态、链接或第三方兼容错误，并可能让损坏的图形状态继续影响本帧。

批次 3 已删除两层 `catch (Throwable)` 和一次性 logger 状态：经验球 renderer 成功时仍替换流体盒，返回 `false` 时调用 Create 原 renderer；两条路径发生的异常都正常传播。经验球逐个修改 `PoseStack` 的代码改为 `pushPose` + `try/finally`，异常时只恢复本模组拥有的栈帧。注入目标补全 descriptor，并增加 `require/expect = 1`；descriptor 含 Minecraft 渲染类型，因此保持默认 remap。

### P1-03 缩小 `FunnelBlockEntityMixin` 的整方法接管（批次 2 已完成代码收敛）

[FunnelBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/FunnelBlockEntityMixin.java) 的旧实现曾在 `activateExtractingBeltFunnel()` 的 `HEAD` 取消并复制完整 Create 状态机，且使用字符串反射取得私有 `Mode`，在 `addBehaviours` 尾部创建第二个 `InvManipulationBehaviour` 替换已注册对象。

批次 2 已改为以下窄注入：

1. `activateExtractingBeltFunnel()` 完整执行 Create 原方法；只把 surface funnel 读取到的局部 facing 映射为 Belt 插入侧，并把 `BlockEntityBehaviour.get` 的目标位置改成实际 surface belt 坐标。
2. 只包装最终非模拟 `handleInsertion` 提交点；捕获史莱姆成功实体化时跳过物品插入，失败则回到 Create 原提交。
3. 模式判断不再取消原方法或访问私有枚举。Mixin 仅把 RETRACTED/EXTENDED surface 的临时 `Shape` 映射为等价 PUSHING/PULLING，由 Create 原分支返回自己的私有 `Mode`；字符串反射与缓存已删除。
4. `InvManipulationBehaviour` 在 Create 原构造点通过可链式 `@WrapOperation(NEW)` 直接创建 surface/Basin-aware 实例，不再在 `TAIL` 替换列表和字段中的旧对象。
5. Funnel filter slot 在原构造点替换为 surface-aware 定位器：点击面先从世界空间转到局部空间，Create 算出的槽位位置和姿态再整体转回世界空间，修复倒置/侧挂黄铜漏斗无法命中过滤槽的问题；普通漏斗及正立 Belt Funnel 完整回退原逻辑。
6. `WeakReference` ordinal、三个 `BlockState.getValue`、Belt 位置、最终提交和两个构造点都增加了匹配数量约束。

因此等待版本、模拟抽取、过滤数量、拒绝插入、占用检查、flap、`onTransfer` 和 `startCooldown` 均回到 Create 原实现。Create 6.0.10 用本地精确源码核对；本机缓存的 6.0.11-295 字节码确认上述调用点和构造器描述符未漂移。编译和 `quickPlaySmoke` 已通过，仍需用普通 Create Belt、Magma Belt 与六面 Slime Belt 对照测试实际传输语义。

### P1-04 保证 Basin 三种库存视图和实体化输出的一致性（批次 1 已完成代码收敛）

批次 1 把相关 Mixin 和支撑类作为同一条边界完成收敛：

- 新增 `BasinItemHandlerAccess`，把 `INTERNAL`、`EXTERNAL`、`FUNNEL` 三种库存权限集中成一个显式契约；外部能力隐藏并保护控制物品，Funnel 可抽取但不能插入，配方和 Basin 自有逻辑使用原始内部 handler。
- [BasinOperatingBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinOperatingBlockEntityMixin.java) 的 `RecipeTrie.getVariants` 调用点已加 `require/expect = 1`；[BasinRecipeMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinRecipeMixin.java) 也锁定首次 capability 查询，并校验 Level、item capability、Basin 位置和空 context，不匹配即回退原调用。
- [BasinBlockEntityMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinBlockEntityMixin.java) 已锁定 `tryClearingSpoutputOverflow` 中模拟/提交两个调用点。实体化前会重新模拟并校验 remainder 的物品身份、组件和数量区间，成功后返回规范化 remainder，不再信任异常 handler 的任意返回值。
- 旧存档迁移改为“成功才锁存”；服务端实体区域未就绪时保留数据并延迟重试，完成后才清理 persistent tag，避免未加载实体被当作不存在。
- [ItemHelperMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/ItemHelperMixin.java) 的延迟抽取预览调用点已锁定；[BasinBlockBeltOutputMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BasinBlockBeltOutputMixin.java) 与 [BlockEntityPersistentDataAccessor.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/BlockEntityPersistentDataAccessor.java) 复核后无需行为修改。

Create 6.0.10 使用本地精确源码核对；本机缓存的 Create 6.0.11-295 工件字节码也确认 `BasinRecipe.apply` 的 item/fluid 查询顺序、`RecipeTrie.getVariants` 唯一调用点，以及 spoutput 的模拟/提交双调用点未漂移。代码风险已收敛，但部分接收、数量大于 1、实体生成失败、模拟后目标变化、停止 Belt、六个方向、区块卸载，以及旧存档重复/缺失镜像和满 Basin，仍需专项游戏内测试。

### P1-05 避免给所有 `LivingEntity` 注册四个独立同步字段（批次 3 已完成）

旧版 `LivingEntitySlimeMimicMixin` 向每个 `LivingEntity` 增加 1 个 `SynchedEntityData` 字段，`LivingEntityButterRotationMixin` 再增加 3 个。即使世界里没有目标效果，所有生物都会承担四个 data item 和同步协议开销；在基类上调用四次 `defineId(LivingEntity.class, ...)` 也扩大了与其他模组数据注册顺序的兼容面。

批次 3 已删除上述两个 Mixin，改用 [LivingEntityBiotechDataMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/LivingEntityBiotechDataMixin.java) 的单个 `EntityDataSerializers.LONG`。其 payload 带 2-bit 版本，合并拟态 flag、12-bit amplifier、14-bit phase 和 35-bit phase-start tick；phase 最大量化误差约 0.011 度，tick 以可回卷方式覆盖 50 年以上连续游戏时间。拟态和旋转更新都会保留另一半状态，旋转的 amplifier/phase/start 由一次原子 data item 更新提交；玩家无需视觉旋转，不再产生无用旋转同步。`CreateBiotechSlimeMimic` NBT 协议保持不变。

### P1-06 收敛全局实体渲染链路（批次 3 已完成代码收敛）

[EntityRenderDispatcherSlimeMimicMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/EntityRenderDispatcherSlimeMimicMixin.java) 包装完整 `EntityRenderer.render`，[LivingEntityRendererMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/LivingEntityRendererMixin.java) 又包装/修改同一渲染链并抑制 layer，[ModelPartCubeGeometryMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/ModelPartCubeGeometryMixin.java) 进入每个 `ModelPart.Cube.compile`。这些全局注入仍是支持第三方 renderer 与独立 layer 几何所需的观察边界，批次 3 没有用特定模型白名单牺牲兼容性。

批次 3 已完成以下收敛：

1. `CapturedEntityRenderTime` 从全局静态 depth 改成线程局部链式 token；token 校验 owner thread、LIFO 和重复关闭，另保留一个原子活动数作为普通 timer 读取的单分支快速门卫。
2. `EntityGeometry` 的基础模型测量和 `SurgicalCapturedRenderPlan` 的 cube 捕获使用相同的可嵌套 scope 契约；cube capture 将原来的两个 ThreadLocal deque 合并为一个 frame，`recording.finish()` 异常也会在内层 `finally` 关闭 scope。
3. 普通 layer/cube 路径先做一次原子活动数读取，只有捕获活动期间才访问 ThreadLocal。现有 `(entity, renderer, yaw, partialTick)` 帧缓存继续复用 Iris/Oculus 同帧重复 pass，不跨帧复用动态姿态。
4. Dispatcher、Living renderer、layer 和 ModelPart cube 的核心调用点补上完整 descriptor 或 `require/expect = 1`，依赖升级时不再静默偏移。

代码边界已收敛，仍需测试 Iris/Oculus、透明/发光/outline、隐身、盔甲层、第三方 renderer、资源重载、递归/嵌套渲染和异常退出。

### P1-07 不再向创造栏核心内容插入 `ItemStack.EMPTY`（批次 4 已完成）

旧 `CreativeModeTabMixin` 曾对主标签完整替换 `buildContents`，并用空栈填充布局。空栈破坏了 `CreativeModeTab` 输出通常只含有效展示物的约定，旧 `JeiItemStackListFactoryMixin` 因此还要挂到 JEI 内部日志调用上压制错误，形成上游无效数据、下游内部补丁掩盖的耦合链。

批次 4 已把主标签迁回 [CBCreativeModeTabs.java](../../src/main/java/com/nobodiiiii/createbiotech/registry/CBCreativeModeTabs.java) 的公开 `displayItems` 回调：display/search 集合只接收有效栈，并显式遵守 `ItemDisplayParameters.enabledFeatures()` 与 `TabVisibility`。标题行和末行补位由 [CreativeModeInventoryScreenMixin.java](../../src/main/java/com/nobodiiiii/createbiotech/mixin/client/CreativeModeInventoryScreenMixin.java) 在 `selectTab` 时转换为只属于 `ItemPickerMenu` 的临时 screen layout，分区行元数据和滚动位置由 [CreativeTabSectionRenderer.java](../../src/main/java/com/nobodiiiii/createbiotech/client/CreativeTabSectionRenderer.java) 管理。旧 `CreativeModeTabMixin` 与 JEI logger Mixin 均已删除；标签切换会清空布局状态，banner 的 pose、shader color 和深度状态在 `finally` 中收束。

### P1-08 让 JEI 版本声明与内部类注入匹配（批次 4 已完成代码收敛）

项目仍需注入 `RecipeLayout` 和 `RecipeSlot` 两个 JEI implementation class 来支持任意分类中的捕获箱实体预览；批次 4 已删除对 `ItemStackListFactory` logger 的注入，并把 JEI 范围从 `[19.21.0.247,)` 收窄为本地源码与运行工件均已核对的 `[19.39.0.368,19.40)`。

`JeiRecipeLayoutMixin` 现在只保留 19.39 的 `IRecipeSlotDrawable.draw(GuiGraphics, boolean)` 调用，不再用两个 `require = 0` 分支猜测新旧 API；injector 与嵌套 `@At` 均显式 `remap = true`，并使用完整 descriptor、`require = 1`、`expect = 1`。`JeiRecipeSlotMixin` 同样锁定完整 `drawIngredient` descriptor 和单一调用点，且先以实际 `ItemStack` 类型快速门卫，再读取渲染配置。依赖升级造成签名漂移时会在启动时明确失败，而不是静默丢失捕获箱渲染。

当前 ModDevGradle/NeoForge 构建仍不生成 `*refmap*.json`；默认编译和 19.39.0.368 实际启动已验证显式 remap 与运行时调用点，但缺少独立 refmap 产物的问题仍作为工具链核查项保留。`ItemApplicationCategoryMixin` 继续只在自定义 renderer 成功时取消，失败会回到 Create 原渲染。

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
- `JeiRecipeLayoutMixin` 已在第三方类级 `remap = false` 下，对包含 `GuiGraphics` 的 injector 和调用目标局部启用 remap；以后每次改 descriptor 仍需检查生产映射或实际运行时注入。
- `CameraMixin.setup` 等剩余 `require = 0` 需要一次性诊断；批次 4 涉及的 JEI 调用点已改为 `require/expect = 1`。

### Funnel、Tunnel 与运输

- `BeltFunnelBlockStateMixin` 给所有 Create Belt Funnel 增加六值属性，属于存档协议。属性名、默认值和方向语义发布后不能随意改变；补旧世界、结构、Schematic、Ponder 和网络状态测试。
- `BeltFunnelBlockMixin` 与 `MagmaBeltFunnelBlockMixin` 修改相同目标方法，应统一分派或明确顺序，避免第三方依赖偶然的 Mixin 应用次序。
- `BeltTunnelBlockMixin` 复制部分 Create tunnel 状态逻辑；升级 Create 时逐分支 diff。`BeltTunnelItemMixin`、`BeltTunnelInteractionHandlerMixin` 和 capability invalidator 保持窄注入，可列 P2。
- `ItemHelperMixin` 位于 Create 通用抽取 helper 的高频路径。保留首个空槽快速返回，测试 `EXACTLY` 重试、predicate、副作用 handler 以及 simulate/commit 不一致。

### 渲染与 UI

- `CreativeModeInventoryScreenMixin` 的分区布局已与核心 tab 内容分离；`CreativeTabSectionRenderer` 用 `try/finally` 恢复 pose/shader/depth，并在标签切换时重置行与布局元数据。
- `CreeperRendererMixin` 当前已使用 `@WrapMethod` + `try/finally`，旧版 P1 已降为 P2；仍需测试它和另一个 `LivingEntityRenderer` 包装的嵌套顺序。
- `DeltaTrackerTimerMixin` 的捕获深度已在批次 3 改为线程局部 token；普通 timer 读取只做一次原子活动数判断。
- `SpoutCategoryMixin` 已在后续清理中移除；`ItemApplicationCategoryMixin` 已有失败回退，不再是阻断项。
- `LevelRendererAccessor`、Creative screen/menu accessor 和简单纹理 invoker 属于 P3；有公开 API 时迁移即可。

### 存档、网络与可选兼容

- `SmartBlockEntityLegacyRefreshMixin` 和 Basin legacy 迁移都应保证“一次成功后清标记；失败可重试且不重复生成”。
- `BlockEntityConfigurationPacketMixin`、`ServerGamePacketListenerAccessor` 涉及客户端预测与跨空间方块实体定位，测试重复序列、过期序列、子层级卸载和权限校验。
- `UniversalJointEndpointBlockSableMixin` 的 before/after move 应在移动失败、异常和部分端点加载时恢复 listener/lift 状态。

## 6. 92 项全量清单

下表给每个 Mixin 一个单一最高优先级。P1/P2/P3 代表该类最需要处理的风险，不表示整类所有代码都同级。

### 6.1 主配置 common（55）

| Mixin | 作用摘要 | P级 | 审阅结论/下一步 |
| --- | --- | --- | --- |
| `AbstractContraptionEntityBufferPadMixin` | Contraption 缓冲垫 tick 补偿 | P2 | 缓存是否含缓冲垫；验证卸载/拆分后失效 |
| `AbstractVillagerAccessor` | 读写交易报价字段 | P3 | 窄 accessor，可保留 |
| `AbstractVillagerSlimeMimicTradesMixin` | 拟态交易载入/恢复 | P2 | 测试职业变化、旧 NBT 和普通报价 |
| `AbstractHorizontalFunnelBlockMixin` | Surface Funnel 旋转 | P1 | 批次 2 已修复 rotation/mirror 坐标变换；补结构/Contraption 实测 |
| `AssemblyOperatorBlockItemMixin` | 识别 Biotech Belt | P3 | 普通方块完整回退；Create 升级核对 predicate |
| `BasinBlockEntityMixin` | Basin handler 视图、迁移、实体化输出 | P1 | 批次 1 已收敛契约/迁移/提交校验；补游戏内失败矩阵 |
| `BasinBlockBeltOutputMixin` | 允许向 Biotech Belt 输出 | P2 | 批次 1 已复核，无需改动；仍测方向、满载和无 host |
| `BasinOperatingBlockEntityMixin` | 配方索引读取内部 handler | P1 | 批次 1 已锁定唯一 `RecipeTrie.getVariants` 调用点 |
| `BasinRecipeMixin` | 配方应用读取内部 handler | P1 | 批次 1 已校验 capability/Level/pos/context 并锁定调用点 |
| `BlockEntityConfigurationPacketMixin` | 跨空间配置包 BE 解析 | P1 | 补维度、权限、卸载和预测序列测试 |
| `BlockEntityPersistentDataAccessor` | 无分配读取持久数据字段 | P3 | 批次 1 已复核；默认 remap 正确，保持只读 |
| `BlockBreakingMovementBehaviourMixin` | Contraption 伤害上下文 | P0 | 批次 0 已改为校验式 scope + `@WrapMethod`；补异常行为测试 |
| `BrassTunnelBlockEntityMixin` | 非标准 Belt 的 Tunnel 路由 | P1 | Create 版本逐分支 diff；模拟/提交一致性 |
| `ContraptionMixin` | 自有 Belt 的 Contraption 搬运 | P1 | 测试装配、拆解、跨区块和异常回滚 |
| `DeployerMovementBehaviourMixin` | Deployer 捕获实体/蓝图路径 | P1 | 取消原方法分支需覆盖失败和掉落 |
| `BeltTunnelBlockMixin` | 自有 Belt 的 Tunnel 状态机 | P1 | 建立普通 Create Belt 对照测试 |
| `BeltTunnelItemMixin` | 放置时补充 casing/侧效应 | P2 | 保持与 Create placement 同源 |
| `BeltTunnelBlockEntityCapabilityMixin` | 清理 Tunnel capability 缓存 | P2 | 验证邻居变化/重载后重新解析 |
| `BeltTunnelInteractionHandlerMixin` | Tunnel 插入/抽取桥接 | P2 | 测试模拟、阻塞和 flap |
| `LaunchedItemForBeltMixin` | 持久化链/偏移数据 | P2 | 校验 NBT 长度、旧数据和 place 失败 |
| `SchematicannonBlockEntityMixin` | Schematicannon 链数据发射 | P1 | HEAD 取消路径逐项保留消耗/失败语义 |
| `BeltFunnelBlockMixin` | Surface Funnel 状态/几何/有效性 | P1 | 批次 2 已联动复核；补六面 shape/revert/wrench 矩阵 |
| `BeltFunnelBlockStateMixin` | 增加 attachment surface 属性 | P1 | 批次 2 已联动复核；固定存档协议并补旧世界/结构测试 |
| `BeltFunnelShapeMixin` | Surface shape/碰撞 | P2 | 六向 shape 与 ItemEntity 碰撞矩阵 |
| `FunnelBlockMixin` | 放置、revert、实体进入扩展 | P2 | 批次 2 已增加点击面优先；测试非 Surface 回退和重连 |
| `FunnelBlockEntityMixin` | Funnel 模式、能力、抽取状态机 | P1 | 批次 2 已删除整方法接管/Mode 反射/事后 behaviour 替换，并修复六面 filter slot 交互 |
| `FunnelItemMixin` | Surface Funnel 放置解析 | P2 | 批次 2 已使多 Surface 场景优先使用玩家点击面 |
| `FluidTankBlockEntityMixin` | 特殊流体读取兼容 | P2 | 保护 controller/非 controller 和旧 NBT |
| `FollowTemptationFixedCarrotFishingRodMixin` | Brain 诱惑行为固定目标 | P1 | 迁移独立 behavior，避免取消原 tick |
| `ItemHelperMixin` | 捕获物品延迟抽取预览 | P2 | 批次 1 已锁定调用点；仍测 EXACTLY 和非幂等 handler |
| `MagmaBeltFunnelBlockMixin` | Magma Belt Funnel shape/扳手 | P2 | 与 `BeltFunnelBlockMixin` 统一分派 |
| `LivingEntitySlimeMimicHurtSoundMixin` | 拟态受伤声 | P2 | 普通实体快速回退；服务端/客户端各测 |
| `LivingEntityBiotechDataMixin` | 全 LivingEntity 拟态/旋转同步状态 | P1 | 批次 3 已把 4 个 data item 合并为 1 个版本化 long；补重连/跨维度矩阵 |
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

### 6.2 主配置 client（32）

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
| `client.CreativeModeInventoryScreenMixin` | 创建 screen-only 分区布局并绘制标题 | P2 | 批次 4 已隔离核心内容并在标签切换时重置状态；补 UI 矩阵 |
| `client.DeltaTrackerTimerMixin` | 捕获渲染固定 partial tick | P2 | 批次 3 已改线程局部 token + 常数级活动门卫 |
| `client.EntityRenderDispatcherSlimeMimicMixin` | 包装完整实体 renderer | P1 | 批次 3 已锁定调用点并确认同帧缓存；补第三方 renderer 矩阵 |
| `client.FlapStuffsMixin` | Funnel flap 坐标变换 | P2 | 可嵌套 token + finally |
| `client.FluidTankRendererMixin` | 经验流体球渲染 | P1 | 批次 3 已移除 Throwable 吞噬并用 finally 恢复自有 pose |
| `client.FunnelRendererMixin` | Funnel BER 坐标变换 | P2 | scope/pose 异常恢复 |
| `client.FunnelVisualMixin` | Flywheel Funnel visual 适配 | P2 | Flywheel 开关、重建、卸载测试 |
| `client.GoggleOverlayRendererMixin` | Chamber/拟态 Goggle 信息 | P2 | 缓存目标查找；可用 proxy API 的分支迁移 |
| `client.HumanoidArmorLayerMixin` | 特定纹理 render type 替换 | P3 | 纹理身份门卫充分，升级核对调用点 |
| `client.ItemApplicationCategoryMixin` | 自定义 JEI application 预览 | P2 | 成功才取消已正确；JEI 已收窄到 19.39.x |
| `client.ItemPickerMenuMixin` | 记录创造栏滚动行 | P3 | 批次 4 已锁定 descriptor；由标签切换重置全局状态 |
| `client.JeiRecipeLayoutMixin` | JEI hover/slot context | P1 | 批次 4 已锁定 19.39 调用点、显式 remap 并禁止静默失效 |
| `client.JeiRecipeSlotMixin` | 全局捕获箱槽位 renderer | P1 | 批次 4 已锁定 descriptor 和版本范围，并提前做 ItemStack 快速门卫 |
| `client.LivingEntityRendererMixin` | 包装全 LivingEntity renderer/layer | P1 | 批次 3 已加快速 scope 门卫和调用点约束；补嵌套顺序矩阵 |
| `client.LogisticalStockResponsePacketMixin` | 无线库存响应重路由 | P1 | 增加会话、维度和 holder 生命周期校验 |
| `client.PressingBehaviourMixin` | Chamber Press 动画相位 | P2 | 单点覆盖；升级核对签名 |
| `client.SpoutCategoryMixin` | 旧 JEI Spout 场景补丁 | 已移除 | 2026-09-14 删除；当前自带配方没有触发场景，打印动画由独立分类绘制 |
| `client.StockKeeperRequestScreenMixin` | 无线库存 Screen 生命周期 | P1 | 远程失效、换目标、断线和重开测试 |
| `client.TextureStateShardAccessor` | 调用 cutout texture invoker | P2 | MC 渲染内部 API；资源包矩阵 |
| `client.LevelRendererAccessor` | 读取 renderer ticks | P3 | 优先公开动画时间 API |
| `client.MixinSuperGlueSelectionHandler` | 客户端 Smart Glue 选择 | P2 | 与服务端判定保持一致 |
| `client.ModelPartAccessor` | 遍历 cubes/children | P2 | 模型内部字段；资源重载和自定义模型测试 |
| `client.ModelPartCubeGeometryMixin` | 捕获每个 cube 几何 | P2 | 批次 3 已用原子门卫 + 线程局部 frame；补模型库矩阵 |
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
- `LivingEntitySlimeMimicMixin`、`LivingEntityButterRotationMixin` 已删除；拟态和黄油旋转改由一个版本化同步字段承载。
- `CreativeModeTabMixin`、`client.JeiItemStackListFactoryMixin` 已删除；有效 tab 内容通过公开回调生成，空行只存在于客户端菜单布局。

### 已解决但仍需回归

- `BlockBreakingMovementBehaviourMixin` 已用作用域式 `@WrapMethod` 保证异常退出也关闭伤害上下文，并增加 token/LIFO 校验。
- Basin 相关 Mixin 已统一三种 handler 权限、守卫配方 capability 调用、校验 spoutput 模拟结果，并让旧数据迁移在实体区域就绪后才提交。
- Surface Funnel 已改为 worldize—transform—localize 的 rotation/mirror 变换；抽取流程恢复执行 Create 原状态机，只包装 surface 坐标、插入侧和实体化提交点，私有 `Mode` 反射及注册后的 behaviour 替换均已删除。
- Surface Funnel 放置在多候选面时优先采用玩家实际点击面；自动更新或点击面无效时仍使用稳定的邻面扫描。
- Surface Funnel 的过滤槽会把世界点击面转入 surface 局部帧，并将槽位位置/姿态转回世界帧；倒置与侧挂黄铜漏斗现在可按实际可见槽位交互。
- 全体 `LivingEntity` 的 Biotech 同步开销已从四个 data item 合并为一个版本化 long；拟态 NBT 兼容不变，黄油旋转三元组改为原子更新。
- 捕获实体计时、基础模型测量和 cube 观察已使用线程局部嵌套 token；普通渲染先走单次活动数门卫，异常退出由 finally 恢复。
- `FluidTankRendererMixin` 不再吞掉自定义或 Create fallback 的渲染异常，经验球 renderer 自己拥有的 PoseStack 层会在 finally 中恢复。
- `CreeperRendererMixin` 已改为 `@WrapMethod` 并在 `finally` 恢复 pose/context/swell。
- `JeiRecipeLayoutMixin` 已用 `@WrapOperation` 和 `finally` 结束 hover/slot context。
- 主创造栏 display/search 集合不再含空栈；screen-only 布局保留分区空行，JEI logger workaround 已删除。
- JEI 运行范围已收窄到 19.39.x，两个内部类 Mixin 均使用完整 descriptor、显式 remap 和强制调用点计数。
- `ItemApplicationCategoryMixin` 仅在自定义 renderer 成功时取消，失败可回到 Create 原渲染。
- Sable 兼容已移除主要反射调用，并把 Simulated/Sable 的类存在性门控分开；剩余问题是签名/版本范围和物理前置条件。

### 本轮新增审阅面

- Alternate Current 2 项。
- Basin handler-view/recipe 路由 3 项及无分配持久数据 accessor。
- 创造栏/JEI 空栈布局链路已在批次 4 从 5 个 Mixin 收敛到 3 个 UI/渲染边界。
- 实体完整渲染捕获与模型 cube 几何链路。
- 拟态/黄油旋转共用的 LivingEntity 紧凑同步字段。
- 固定胡萝卜钓竿的 Goal/Brain/Sensor 三条 AI 路径。
- 实体红石索引、SignalGetter 和红石线/Alternate Current 求值。

## 8. 建议实施顺序

1. **批次 0（已完成，P0-01）**：Contraption 伤害上下文改为校验式 scope 和 `try/finally`；专项异常/嵌套行为测试仍待补。
2. **批次 1（已完成代码收敛，P1-04）**：聚合 Basin handler、配方索引/应用、Belt 输出、延迟抽取和旧数据迁移；专项游戏内矩阵仍待补。
3. **批次 2（已完成代码收敛，P1-01、P1-03）**：聚合 Surface Funnel 坐标、放置和抽取状态机；专项方向/传输/结构矩阵仍待补。
4. **批次 3（已完成代码收敛，P1-02、P1-05、P1-06）**：聚合实体同步与渲染热路径，收紧异常边界并减少全局状态/字段；专项网络、shader、第三方 renderer 和异常矩阵仍待补。
5. **批次 4（已完成代码收敛，P1-07、P1-08）**：创造栏核心集合只保留有效物品，空行迁入 screen-only 布局；删除 logger workaround，并把 JEI 兼容与内部调用点锁定到 19.39.x。专项 UI/hover/版本拒绝矩阵仍待补。
6. **批次 5（P1-09、P1-10、P1-11、P1-12）**：按 AI、红石、Sable、无线库存四个边界分别施工并完成版本矩阵。
7. 再处理 P2 的异常恢复、版本 drift 和性能基准；P3 accessor 随依赖升级清理。

## 9. 验证矩阵

| 层级 | 必测内容 | 通过标准 |
| --- | --- | --- |
| 配置/编译 | `./gradlew compileJava --rerun-tasks`；检查生产 remap/refmap 产物 | 92 项配置与源码一致；mapped descriptor 有可检查的映射结果；无 Mixin AP 错误 |
| Create 版本 | 6.0.10 与声明支持的 6.0.11 | 所有 ordinal、private method/field 和复制状态机均有明确结果 |
| 原版/Create 回归 | 普通 Basin/Funnel/Belt/Tunnel/Fluid Tank/Contraption/实体/创造栏 | 非目标路径完整执行原行为，输出、冷却、事件、声音和 UI 不变 |
| Surface | 六个 attachment、四种 rotation、mirror、结构/Schematic/Contraption | 世界朝向、shape、碰撞、目标 handler 和回退 Funnel 一致 |
| Basin | 内/外/Funnel handler、配方索引/应用、部分接收、实体生成失败、旧 NBT | 不重复、不丢失、不泄露控制物品；simulate/commit 一致 |
| AI | Goal/Brain 实体、玩家抢占、POI、多子层级、大型生物群 | memory 可清理，行为不锁死，扫描成本有界 |
| 红石 | vanilla/Alternate Current、强弱信号、导体/线/比较器、增删/卸载 | 两套 evaluator 语义一致；零源开销可接受 |
| 客户端 | JEI 版本矩阵、Iris/Oculus、透明/outline、资源重载、渲染异常 | scope/pose/render state 必定恢复；非目标渲染无变化 |
| 可选依赖 | 无 Sable/Simulated/AC、单独安装、兼容版、不兼容版 | 只禁用对应能力；日志给出明确原因；不在 apply/首 tick 崩溃 |
| 网络/存档 | 旧 NBT、跨维度、菜单换目标、延迟/重复包、断线重连 | 迁移幂等；过期响应不写入新会话 |

### 9.1 批次 2 重点游戏内用例

1. **六面放置、模式与过滤**：组合出 `UP/DOWN/NORTH/SOUTH/EAST/WEST` 六种 attachment，分别放置安山/黄铜漏斗；正反转 Belt 后确认抽取/接收模式、漏斗口、过滤槽和 flap 都对应同一世界方向。黄铜漏斗逐面测试手持普通物品/属性过滤器设定、空手取下以及滚轮配置“精确/至多”数量，槽位高亮、点击面和实际过滤结果必须一致。
2. **状态机回归**：在普通 Create Belt、Magma Belt、水平/竖直/侧向 Slime Belt 上测试空库存、过滤拒绝、精确数量/至多数量、Belt 已占用、Belt 停转、红石暂停和运行中反转；不得丢物、复制、跳过冷却或重复触发 transfer。
3. **结构变换**：对每种侧面 funnel 执行 0/90/180/270 度旋转和 `LEFT_RIGHT`/`FRONT_BACK` mirror，再经结构方块、Schematicannon、Contraption 装配/拆解；attachment、世界漏斗口、shape、碰撞和实际目标必须一起变化。
4. **放置与失效恢复**：让一个空位同时邻接两个可用 Slime Belt surface，逐面点击确认选择点击面；拆除、转向、停转或重载宿主 Belt 时，Belt Funnel 应按预期保留或退回普通 Funnel，且世界 facing、POWERED、EXTRACTING、waterlogged 不错乱。
5. **实体化边界**：从 Basin 经 Funnel 输出 1 个和多个 captured small slime，确认只生成对应数量实体、不把控制物品插入 Belt；阻塞/拒绝模拟时不得先抽取或生成实体，失败回退不得丢物。
6. **视觉与持久化**：六面检查 outline/碰撞、掉落物接触、flap 动画和 Flywheel 渲染；保存退出再进入及区块卸载重载后，方向与传输模式不变。
7. **版本矩阵**：至少在 Create 6.0.10 和 6.0.11 各执行第 1—3 项的代表用例，特别观察启动日志是否出现调用点数量或 descriptor 漂移。

### 9.2 批次 3 重点游戏内用例

1. **同步协议与拟态持久化**：在大型普通生物群中加入、退出、重连和跨维度，确认没有 entity data id 冲突或解码异常；对普通生物、村民和 Slime Bionic 切换拟态，保存重载后拟态、交易、掉落和受伤声保持正确。
2. **黄油旋转连续性**：给多种生物施加、升级、降级、移除和自然到期黄油旋转效果，客户端视觉方向应与服务端近战/弹射物修正一致；重连、跨维度和效果同步时不得跳回零角度或长期停转。玩家不应发生模型旋转或发送无用旋转状态。
3. **经验流体罐**：分别启用普通流体显示和经验球显示，测试空罐、少量/满罐、多方块罐、轻于空气流体、Ponder 以及资源重载；普通水/熔岩和非经验流体必须完整走 Create 原 renderer。调试构建中人为令经验球 renderer 抛错时，PoseStack 必须恢复且异常不能被吞掉。
4. **捕获实体渲染**：覆盖纸箱图标、JEI、手术台预览、活体拟态及死亡拆解；组合隐身、发光、outline、半透明、盔甲/手持物、村民 layer、第三方 renderer 和递归渲染，确认 scope 不串实体、不漏 layer、不在下一帧残留固定 partial tick。
5. **Shader 与同帧复用**：分别在原版渲染、Iris/Oculus 开关状态下观察同一拟态实体的主 pass、阴影 pass 和 outline pass；同帧重复 pass 应复用捕获计划，下一帧动画仍更新，资源重载后旧纹理/模型缓存失效。
6. **普通路径基准**：在无拟态、无纸箱预览的大型生物群中比较批次前后的实体渲染时间与分配；`ModelPart.Cube.compile` 和 layer 包装应停留在单次活动数门卫，不应创建 ThreadLocal deque、列表或捕获计划。

### 9.3 批次 4 重点游戏内用例

1. **主标签内容协议**：进入主创造栏并滚动到底，确认六个 banner、物品顺序、完整行补位和滚动条范围不变；display/search 集合不得出现空气或空栈，复制物品、快捷栏保存及鼠标交互只命中真实物品。
2. **搜索与生成条件**：在全局搜索中确认所有可见项和 `searchOnly` 项都能找到，而 `searchOnly` 项不进入主标签；切换 feature flag、权限和资源重载后，禁用物品不应进入 display/search，分区行应按实际可见物品重新排布。
3. **标签与界面生命周期**：主标签、搜索、背包、其他模组标签之间反复切换，关闭并重开创造栏，再改变 GUI scale；banner 不能残留、错行或覆盖其他标签，`currentRow` 必须从顶部重新建立。
4. **JEI 捕获箱槽位**：在普通配方、铁砧命名配方和 Biotech 自有分类中检查小/大捕获箱；非 hover 显示实体与徽标，hover 恢复箱体，循环配方切换时显示的实体必须与当前栈一致，禁用配置后完整回到 JEI 默认物品 renderer。
5. **JEI 异常与状态恢复**：让捕获实体构建失败、资源重载或 renderer 抛错，确认 slot hover context、PoseStack 和后续配方槽位不串联；普通 ItemStack/FluidStack 槽位不得改变。
6. **版本边界**：无 JEI 时客户端可正常启动；JEI 19.39.0.368 必须正常应用两个内部 Mixin；安装 19.39.x 的其他候选构建需重新跑本节 4—5，19.21—19.38 或 19.40+ 应由模组依赖范围明确拒绝，而不是运行后静默缺功能。

本轮已完成的机械验证：配置清单为 55 common + 32 client + 2 Alternate Current + 3 Sable，92 个配置项与 92 个 Mixin 源文件数量一致；默认 Create 6.0.10 的 `./gradlew compileJava --rerun-tasks --no-daemon` 成功，只有现存的 26 个 deprecated API 警告；Create 6.0.11-295 覆盖参数下的 `compileJava` 也成功；紧凑同步 payload 已执行版本、flag 保留、amplifier/phase、tick 回卷及清理的独立检查，线程局部 render-time scope 已执行嵌套和跨线程隔离检查；阶段 4 的 JEI 19.39.0.368 `./gradlew quickPlaySmoke --no-daemon` 成功并在时限内进入世界，Creative Screen 的新增选择、热刷新和关闭注入均已实际应用。JEI 配方内部类为打开配方界面后才加载的惰性路径，其 draw 调用点仍需本节第 4 项手工触发。构建 jar 已移除 `CreativeModeTabMixin` 和 `JeiItemStackListFactoryMixin`，生成的 `neoforge.mods.toml` 含 `[19.39.0.368,19.40)`。Markdown 表格分别包含 55、32、5 行，文档内相对链接均存在，`git diff --check` 通过。当前构建仍没有生成 `*refmap*.json`，因此不能用 refmap 关闭映射验证项。

冒烟验证证明当前组合能够完成运行时注入、启动和进世界，但不等同于 P0 异常分支、Basin、Surface Funnel、实体同步/渲染或创造栏/JEI 行为矩阵全部正确。最新日志仍有 9 条第三方创造栏空栈错误；与修改前日志数量相同，且本地运行工件中的 Simulated `processItems` 会无条件插入一整行 9 个空栈，因此这些剩余错误不再来自 Create: Biotech 的 tab 内容。后续仍需执行上文各批次的专项游戏内矩阵。
