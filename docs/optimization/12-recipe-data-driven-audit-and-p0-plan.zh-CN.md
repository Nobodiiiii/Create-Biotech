# 配方数据驱动审计与 P0 实施方案

## 结论

当前配方资源已经覆盖大多数常规合成与 Create 加工，但运行时仍有三组关键逻辑绕过配方数据：鱿鱼打印机只把 JSON 当展示信息，三种特殊手持应用在 Java 中重复声明原料与产物，巨蛙工厂的随机包裹内容完全硬编码。它们会造成“改了数据包但玩法不变”或 JEI、运行时互相漂移，是本轮 P0。

本轮目标不是消灭 Java 配方类，而是明确边界：

- 数据包决定可替换的内容：输入、输出、流体成本、处理时间、随机物品池与数量范围。
- Java 保留不可由普通配方表达的算法：传送带整链替换、方块实体数据迁移、包裹序列化、附魔组件复制。
- 运行时、JEI 与配方查看器读取同一份数据；删除或覆盖配方后，不应还有对应的 Java 后门继续生效。

## 审计基线

- 审计日期：2026-09-21
- 目标环境：Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281、JEI 19.39.0.368
- Create 行为参考：`ref/1.21.1/Create/`，与当前 Create 6.0.10 版本一致
- 资源范围：`src/main/resources/data/**/recipe/**/*.json`

静态扫描结果：

| 指标 | 数量 |
| --- | ---: |
| 配方 JSON | 110 |
| Vanilla/Create 根配方类型 | 97 |
| 自定义根配方类型 | 13 |
| 带条件配方 | 76 |
| 无条件配方 | 34 |
| 直接 `item` 选择器 | 194 |
| `tag` 选择器 | 13 |
| 直接 `fluid` 选择器 | 11 |
| `fluid_tag` 选择器 | 0 |

所有配方 JSON 均可解析，未发现重复资源 ID。当前没有 RecipeProvider；`CBDataGenerators` 只生成方块标签，因此配方结构、条件和 ID 仍主要靠手工维护。

根配方类型分布：

| 配方类型 | 数量 |
| --- | ---: |
| `minecraft:crafting_shaped` | 20 |
| `minecraft:crafting_shapeless` | 4 |
| `minecraft:stonecutting` | 9 |
| `create:cutting` | 19 |
| `create:item_application` | 15 |
| `create:mixing` | 8 |
| `create:crushing` | 6 |
| `create:filling` | 5 |
| `create:sequenced_assembly` | 5 |
| `create:compacting` | 3 |
| `create:mechanical_crafting` | 2 |
| `create:pressing` | 1 |
| `create_biotech:high_pressure_processing` | 8 |
| `create_biotech:sonic_dog_cannon_upgrade` | 4 |
| `create_biotech:squid_printer` | 1 |

自定义注册层目前包含 4 个 serializer、2 个独立 RecipeType；巨蛙内容包装复用物品应用类型，音波升级复用锻造类型。这种复用本身没有问题，风险来自“资源已经存在，但运行时另走一套硬编码判断”。

## 发现与优先级

### P0-1：鱿鱼打印机的 JSON 不是运行时唯一来源

`SquidPrinterRecipe` 声明了输入、结果、流体与处理时间，但方块实体仍硬编码原版书输入、附魔书副本输出，并按配置项分周期扣水。结果是：

- 数据包替换输入或输出不会完整生效；
- JSON 的流体数量不等于实际消耗；
- JEI 再次硬编码书与输出，形成第三份事实来源；
- 配方在处理途中重载时，完成产物没有稳定快照。

实施：匹配输入、基础输出、流体总成本和处理时长全部读取 `SquidPrinterRecipe`；开始处理时一次扣除该次配方所需流体并保存实际输出快照；JEI 从同一配方实例展开输入与输出。

### P0-2：特殊手持应用重复声明配方

动力传送带、防爆物品保险库和潜影盒打包机已经各有 `create:item_application` JSON，但事件处理器又硬编码了触发物品和目标方块。Java 的确需要保留整链替换、库存迁移与自定义校验，但不应再决定配方材料。

实施：建立统一查询工具，按完整资源 ID 从 Create 的物品应用配方表中查找并匹配“被处理方块物品 + 手持物品”，从配方首个结果解析目标方块，并统一遵守 `keep_held_item`、耐久和合成剩余物规则。特殊处理器仅执行其不可替代的世界状态迁移。

同时修正通用手持应用处理器的排除 ID；原代码缺少 `item_application/` 路径前缀，资源 ID 与真实配方并不一致。

### P0-3：巨蛙工厂包裹内容硬编码

`FrogContentsPackagingRecipe` 在 Java 中固定九类物品和最大数量，数据包作者无法调整生态掉落、兼容其他模组物品或修改产量。

实施：增加 `data/*/frog_package_contents/*.json` 可重载数据目录。每个条目使用物品或物品标签选择候选物，并声明 `min`/`max` 数量；配方算法只负责掷骰、装箱和跳过空结果。默认资源复现当前数值，不改变原始平衡。

实际 P0 schema 支持 `item` 或 `tag` 二选一；标签内物品等概率选择，数量在闭区间内均匀抽取。例如：

```json
{
  "entries": [
    { "item": "minecraft:slime_block", "min": 0, "max": 10 },
    { "tag": "create_biotech:froglights", "min": 0, "max": 8 }
  ]
}
```

同资源 ID 遵循数据包优先级覆盖；外部模组物品可以直接作为 `item`，也可通过扩展标签接入。

## P0 验收标准

1. 修改鱿鱼打印机配方的输入、结果、流体数量或处理时间后，运行时与 JEI 同步变化；删除配方后机器不再接受对应输入。
2. 修改三个特殊物品应用配方的原料或兼容目标方块后，事件处理器遵循修改；删除配方后转换不再发生。
3. 修改或用高优先级数据包覆盖巨蛙内容文件后，新生成包裹使用新池与新数量范围。
4. 数据错误只忽略有问题的文件或条目并记录警告，不让服务器因单个内容条目崩溃。
5. `compileJava`、`processResources` 和 `build` 通过；本轮不涉及 Mixin，按项目规则不启动客户端烟雾测试。

## 后续优化

### P1

- 为叮咚鸡、语音包、史莱姆传送带、岩浆捕获和生物物品应用建立正式序列化模型，替代仅用于 JEI 的合成配方对象。
- 扩大物品/流体标签覆盖率，优先处理矿物、桶装流体和具有明确跨模组语义的材料；不把专用机器零件为了“标签率”强行泛化。
- 统一自定义配方字段命名与数值约束；高压处理的区间、概率权重和无效字段需要单独迁移兼容层。
- 让配方条件过滤覆盖外部命名空间，避免兼容数据在依赖缺失时进入加载流程。

### P2

- 增加 RecipeProvider 与数据校验任务，生成重复结构并验证资源 ID、字段范围、输出为空、不可达条件和查看器同步。
- 为自定义数据目录补充 schema 文档和示例数据包。
- 将配方审计做成构建期报告，持续追踪直接物品选择器、无条件兼容配方和 Java 硬编码配方常量。

## 非目标

- 本轮不重写 Create 的配方系统，也不把方块实体迁移算法塞进 JSON。
- 本轮不改动平衡数值；默认数据必须复现原有行为。
- 本轮不处理 Mixin、附身移动或史莱姆仿生实体的现有未提交改动。
- 本轮不创建测试文件。

## P0 实施记录

状态：已完成（2026-09-21）。

- 新增 `RecipeBackedItemApplication`，统一按完整资源 ID 查询 Create 物品应用配方、读取声明结果并执行手持物消耗规则。
- 动力传送带、防爆物品保险库、潜影盒打包机保留各自的世界迁移算法，但不再硬编码安山合金、坚固板或捕获潜影贝的箱子；其兼容目标方块也从配方结果解析。
- 修正通用手持应用处理器的三个特殊配方排除 ID，避免特殊转换落入通用单方块替换路径。
- 鱿鱼打印机不再硬编码原版书和附魔书副本；运行时与 JEI 从同一配方读取输入、结果、流体和处理时间。开工时按模板附魔总等级一次扣除完整流体成本，并保存结果快照。
- 删除已失去事实来源资格的 `squidPrinter.cycleTicks` 和 `squidPrinter.cycleWaterCost` 配置项；水耗与基础时长改由配方包覆盖。
- 新增可重载的 `frog_package_contents` 数据目录和默认 `giant_frog_factory.json`，并用 `create_biotech:froglights` 物品标签表达三种蛙明灯候选。

验证结果：

- `./gradlew compileJava`：通过。
- `./gradlew build`：通过，包含 `processResources` 与现有测试任务。
- `src/main/resources/data` 下 282 个 JSON：语法检查全部通过。
- 未运行 `quickPlaySmoke`：本轮没有 Mixin 改动，遵守项目对非 Mixin 变更的验证限制。
