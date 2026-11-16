# 经验换算、传输与存档

默认 `1 XP = 1 mB`，经验颗粒为 `3 XP`。粉碎产出的数量保持为二的幂：

| 阶段 | 自身经验 / 母岩累计生长阈值 | 粉碎颗粒数 |
| --- | --- | --- |
| 小型芽 | 24 XP / 24 mB | 8 |
| 中型芽 | 48 XP / 48 mB | 16 |
| 大型芽 | 96 XP / 96 mB | 32 |
| 成熟簇 | 192 XP / 192 mB | 64 |

自然生长仍提供免费进度；纯灌注、粉碎、抽取循环的经验总量守恒。

## 经验泵

- 直接复用 Create 的 `PumpBlockEntity` 输送行为，只在经验泵自己的 `PipeConnection` 子类中组合经验过滤条件；保留原生泵 Behaviour 类型和压力传播，不注入全局流体过滤方法。连接对象在构造、读取和转向重建时保持该子类，不在 tick 中替换或迁移连接。
- 连接普通流体能力端点、原版可抽取流体方块或管道时，入口和出口都交给 Create 管网处理，包括压力、分流、流速、堵塞、直接接储罐及开放出口。Mixin 只保留 `PipeConnection` 的 `source` / `network` 字段访问，用于紧贴特殊入口时挂接缓存源、切换时清除旧网络；过滤不依赖 Mixin。
- 只有紧贴入口的物品容器、开放经验球 / 玩家入口及输入喷嘴参与特殊吸收，不隔着管道抽取物品。
- 每个运行 tick 将缓存补充到本次按转速计算的额度，不询问输出目标余量。整件物品向上取整吸收，超出的经验留在缓存；缓存已经足够时不再消耗输入。
- 例如 256 RPM 时额度为 128 mB：空缓存吸收一个簇得到 192，正常输出 128 后余 64；下一次补充一个簇后得到 256，再输出 128 后余 128。堵塞时管网不扣缓存，吸收达到额度后暂停。
- `CachedExperience` 使用 long 保存，支持退出、区块卸载和重载；旧泵没有该字段时按空缓存加载。接上普通流体输入时保留缓存，恢复特殊入口后继续使用。
- 缓存仅在实际执行流体抽取时扣除，模拟操作不消耗物品、经验或缓存。没有新增界面、护目镜信息或其他前端提示。

参考实现：

- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/fluids/pump/PumpBlockEntity.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/fluids/FluidTransportBehaviour.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/fluids/PipeConnection.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/fluids/FluidNetwork.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/fluids/pipes/SmartFluidPipeBlockEntity.java`

来源和依赖版本见 `ref/SOURCES.md`，对应 Create 6.0.10 官方源码及本项目的 `6.0.10-281` 依赖。

## 母岩与一次性迁移

- 每个面分别保存当前生长进度 `Face0..Face5` 和 long 余量数组 `FaceOverflow`，本轮成熟后多余输入不丢弃、不转移到其他面。
- 再次催化该成熟面时，收获一个经验簇，剩余经验连同新输入用于同一面的下一轮生长。手动收获同样保留已支付的余量；下次该面生长时继续使用。
- `GrowthThresholds` 记录数据所属的四阶段阈值。旧版本只有 `Face0..Face5`，新版本只在服务端方块实体 `onLoad` 时迁移，保留原有生长阶段及阶段内进度比例，并标记保存。当前格式不会再次缩放。
- 旧默认阈值 `16 / 32 / 64 / 128` 转为 `24 / 48 / 96 / 192`。未改动的自定义阈值保持原单位；新格式能依据保存的阈值在加载时转换。
- 配置只在加载 / 重载事件中用 `experience.configVersion` 迁移一次旧默认值，保留自定义数值。没有旧存档检测、格式分支或迁移调用进入 tick、流体能力查询、生长计算或渲染。
