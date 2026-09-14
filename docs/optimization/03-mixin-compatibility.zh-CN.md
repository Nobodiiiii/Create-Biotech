# 03. Mixin、映射与可选兼容审计

## 结论摘要

两个配置共加载 58 个 Mixin，当前编译可以生成并打包 refmap；但 34 个 Mixin 源文件使用了 `remap = false`，最终 refmap 只包含 27 个类。并非所有 `remap = false` 都错误：纯第三方字段、无参数或纯 primitive 的第三方方法可以合理禁用 remap。高风险部分是方法/`@At` 描述符含 Minecraft、Mojang 渲染类型或继承自 Minecraft 的成员，却被整体排除在 refmap 外。

除此之外，伤害上下文的 HEAD/RETURN 栈维护不具备异常安全性，若 Create 目标方法抛异常会泄漏 ThreadLocal 状态；部分渲染 Mixin 又捕获过宽的 `Throwable`，可能把真正的兼容错误隐藏成长期静默降级。

## 1. 当前状态

- 通用 Mixin：30 个。
- 客户端 Mixin：22 个。
- Allay Mixin：6 个。
- 使用 `remap = false` 的源文件：34 个。
- 生成 refmap 中有映射记录的 Mixin 类：27 个。
- `create_biotech.mixins.json` 和 `create_biotech_allay.mixins.json` 均为 `required: true`，默认 `defaultRequire: 1`。
- `compileJava --rerun-tasks` 成功，生成约 20 KiB 的 `build/mixin/create_biotech.refmap.json`，最终 JAR 已包含该文件。

当前项目版本与 `ref/Create/gradle.properties` 精确匹配，可直接使用 `ref/Create/` 核对 Create 6.0.8 目标。`ref/jei/` 属于 Minecraft 1.20.1 版本线，但其 Forge 基线为 47.3.1且无法从快照直接确认 15.20.0.130 构建号；JEI 内部类核验需保留近似参考 caveat。

## 2. 映射问题

### MIXIN-01：含映射类型的目标描述符被 `remap = false` 排除

- 优先级：P1

以下是已确认需要逐项重新验证 remap 决策的代表性目标；它们目前都没有对应 refmap class entry：

| Mixin | 当前目标 | 风险原因 |
| --- | --- | --- |
| `FluidTankBlockEntityMixin` | `read(CompoundTag, boolean)` | 描述符包含 Minecraft `CompoundTag` |
| `SmartBlockEntityLegacyRefreshMixin` | `read` | 本地 Create 参考声明为 `read(CompoundTag, boolean)` |
| `PackageItemCardboardBoxMixin` | `getContents(ItemStack)` | 描述符包含 Minecraft `ItemStack` |
| `BeltMovementHandlerMixin` | `canBeTransported(Entity)` | 描述符包含 Minecraft `Entity` |
| `BlockBreakingMovementBehaviourMixin` | `damageEntities(MovementContext, BlockPos, Level)` | 包含 `BlockPos` 和 `Level` |
| `ItemHandlerBeltSegmentMixin` | `insertItem(int, ItemStack, boolean)` | 描述符包含 Minecraft `ItemStack` |
| `BasinRendererMixin` | `renderSafe(...PoseStack, MultiBufferSource...)` | 包含 Mojang/Minecraft 渲染类型，嵌套 `@At` 还返回 `ItemStack` |
| `FlapStuffsMixin` | `renderFlaps(PoseStack, VertexConsumer, ..., Vec3, Direction, ...)` | 多个映射类型 |
| `FluidTankRendererMixin` | `renderSafe` 与 `renderFluidBox` | handler/调用点包含 `PoseStack`、`MultiBufferSource` 等 |

项目级映射约定明确要求：即使 owner 是 Create、Catnip、Ponder、Flywheel 或 JEI，只要成员继承自 Minecraft，或描述符含映射的 Minecraft/Mojang 类型，也不能直接用 `remap = false` 把整个 selector/`@At` 排除。

建议：

1. 不要批量删除所有 `remap = false`；按注解级别审计 `@Mixin` target、方法 selector、`@Shadow` 和嵌套 `@At`。
2. 优先修复上表目标，让 annotation processor 生成对应映射。
3. 每次只改一小组，执行 `compileJava` 并检查 refmap 中是否出现预期 selector；如果第三方方法名无法自动映射，应调整 selector 写法，而不是退回到整体禁用描述符映射。
4. 完成编译后再做客户端运行验证，因为“refmap 有记录”不能证明运行时注入点仍存在。

### MIXIN-02：保留真正安全的 `remap = false`

- 优先级：P2

可能合理保留的例子包括：

- `BeltInventoryAccessor` 对 Create 私有字段 `belt`、`beltMovementPositive` 的 accessor。
- `PackagerBlockEntityMixin#wakeTheFrogs()V`，方法名属于 Create 且描述符无映射类型。
- `PressingBehaviourMixin#getRenderedHeadOffset(F)F`，纯 primitive 描述符。
- `SawBlockEntityMixin#applyRecipe()V`，Create 自有无参方法。
- `FunnelBlockEntityMixin` 中纯 Create 类型或 primitive-only 的内部方法。

即使这些例子当前合理，也应在 Mixin 清单中写明“为什么不 remap”，防止后来修改描述符后继续沿用旧判断。

### MIXIN-03：使用“第三方 target 不 remap、Minecraft 调用点仍 remap”的分层模式

- 优先级：P2

`WorldSectionElementImplMixin` 展示了更细粒度的方向：`@Mixin(value = ..., remap = false)` 只用于识别 Ponder/Flywheel 第三方 target，而内部针对 Minecraft `MultiBufferSource#getBuffer(RenderType)` 的操作仍进入 refmap。该类当前确实出现在生成 refmap 中。

建议把类似模式应用到 renderer/JEI/Flap Mixin：不要在最外层 `@WrapOperation/@Inject` 和嵌套 `@At` 上同时机械设置 `remap = false`。

## 3. 注入正确性与异常安全

### MIXIN-04：HEAD/RETURN 无法保证 ThreadLocal 栈成对弹出

- 优先级：P0/P1
- 文件：`BlockBreakingMovementBehaviourMixin.java`

该 Mixin 在 `damageEntities(...)` HEAD 调用 `BioPackagerContraptionDamageTracker.pushDamageContext()`，在 RETURN 调用 `popDamageContext()`。上下文存放在服务端线程的 `ThreadLocal<Deque<DamageContext>>`。

如果目标方法、其他 Mixin 或事件处理器在 HEAD 与 RETURN 之间抛出异常，RETURN 注入不会执行，栈顶上下文会残留。之后同一线程上的其他 crush/drill/roller/saw/run-over 伤害可能被错误归因到旧 contraption，且 ThreadLocal 无法清理。

建议使用 MixinExtras `@WrapMethod`/`@WrapOperation` 包住整个调用，并以 Java `try/finally` 保证 pop；如果目标被取消或递归调用，也应有对应测试。不要仅增加更多 RETURN 注入，因为异常路径仍无法覆盖。

### MIXIN-05：渲染 fallback 捕获 `Throwable` 过宽

- 优先级：P1
- 文件：`FluidTankRendererMixin.java`

自定义经验球渲染失败后回退到原流体渲染是合理的，但当前连原始 `original.call(...)` 也捕获几乎所有 `Throwable`，只重抛 `ThreadDeath` 和 `VirtualMachineError`。这会吞掉 `LinkageError`、`AssertionError` 等本应暴露的二进制兼容/程序错误，并在第一次警告后永久静默丢帧。

建议：

- 自定义渲染路径只捕获预期的 `RuntimeException`，失败后禁用本次/本会话自定义路径并回退。
- 原始 Create 渲染调用不要被宽泛吞掉；若原始路径也失败，应让崩溃报告保留完整根因。
- 将一次性 warning 改成包含目标 Create/Flywheel 版本、流体 id 和方块位置的结构化诊断。

### MIXIN-06：同一目标类的平行 Mixin 增加顺序复杂度

- 优先级：P2

`BeltFunnelBlockMixin` 与 `MagmaBeltFunnelBlockMixin` 都注入 `BeltFunnelBlock#getShapeForPosition` 和 `onWrenched`，通常依靠各自状态判断后提前返回/取消。它们使用默认优先级，未来再增加第三类 belt 或其他 addon 修改相同方法时，行为顺序会更难推断。

建议合并为一个 belt-surface funnel Mixin，由共享策略根据方块类型分派；至少为每个 cancellation 条件写冲突测试，并记录与其他 Create addon 的预期优先级。

### MIXIN-07：`defaultRequire = 1` 应按功能重要性分级

- 优先级：P2

当前任何未单独覆写的注入点失配都会导致启动失败。对存档迁移、服务端数据安全和核心传送带行为，fail-fast 是合理的；对纯粒子、GUI 装饰、JEI 自定义绘制和可选视觉增强，启动失败可能比功能降级更糟。

建议：

- 保持核心行为 required。
- 把可选视觉/JEI Mixin 放入独立配置或为明确可降级的注入设置 `require = 0`，同时在未应用时打印一次版本化 warning。
- 不要全局把 `defaultRequire` 改成 0，否则真实兼容破坏会静默发生。

## 4. JEI 可选依赖

### MIXIN-08：缺失 JEI 的基本处理正确，但版本耦合仍很强

- 优先级：P1/P2

审计时的四个 JEI/Create-JEI Mixin 均使用 `@Pseudo`（其中 `SpoutCategoryMixin` 已于 2026-09-14 移除；打印动画由独立的 `SquidPrinterJeiCategory` 绘制）：

- `ItemApplicationCategoryMixin`
- `SpoutCategoryMixin`
- `JeiRecipeLayoutMixin`
- `JeiRecipeSlotMixin`

这与 `mods.toml` 中 JEI optional 的声明一致，避免单纯因为目标类不存在就直接失败，是当前实现的优点。

剩余风险：

- `RecipeLayout` 与 `RecipeSlot` 属于 JEI library 内部包，不是稳定 API。
- `drawIngredient` 是 private 内部方法；补丁版本也可能移动或改签名。
- Create 的 JEI category 也属于 compat 实现，不是主体稳定 API。
- 当前版本范围没有 JEI 16 上界。

建议收紧 JEI 15.x 上界；增加“安装 JEI”和“不安装 JEI”两套客户端启动 smoke test；优先寻找 JEI drawable/category 扩展 API，只有无法实现时才继续 Mixin 内部类。

## 5. Mixin 清单与 CI

### MIXIN-09：建立机器可检查的 Mixin inventory

- 优先级：P1/P2

建议为 58 个 Mixin 记录以下字段：

| 字段 | 用途 |
| --- | --- |
| target class/member | 明确依赖的内部实现 |
| side | common/client |
| dependency/version | Minecraft、Create、JEI、Ponder 等 |
| remap decision | true/false 及理由 |
| criticality | 核心/可降级视觉/可选 compat |
| expected refmap entry | 是否必须在 refmap 中出现 |
| runtime verification | 对应功能或启动测试 |

CI 至少检查：配置中的类都存在、无遗漏/重复；预期需要映射的类在 refmap 中存在；optional target 有 `@Pseudo` 或 plugin gate；客户端 Mixin 不进入服务端配置；生成 refmap 随源码变化而更新。

## 6. 推荐实施顺序

1. MIXIN-04：先修 ThreadLocal 异常安全，避免错误伤害归因。
2. MIXIN-01：分批修含 mapped descriptor 的 `remap = false`，每批编译并审查 refmap。
3. MIXIN-05：缩窄渲染异常捕获，保留真实兼容错误。
4. MIXIN-08：收紧 JEI 版本并补有/无 JEI 启动验证。
5. MIXIN-06/07/09：合并平行 funnel Mixin，按重要性分级，建立清单和 CI。

## 7. 本阶段验证边界

本阶段没有修改 Mixin，因此未运行 `quickPlayClient`。现有编译和 refmap 只能证明 annotation processor 成功，不能证明 58 个运行时注入全部命中；每次实际修复 Mixin 后，必须按项目规则执行最小编译、检查 refmap，并在具备超时和清理的前提下运行客户端验证。
