> 归档日期：2026-11-13。本文保存精简前的完整总表，包括计算过程、生命候选、旧值对照、DPS 推荐与来源；最终采用值见[现行修正总表](vanilla-stat-offset-survey.zh-CN.md)。

> 最终确认：仅将末影人的单次伤害修正设为 +3、猪灵蛮兵设为 +3、凋灵骷髅设为 +1；其他生命与伤害修正均沿用本轮 DPS 测算前的原始数值。下文是历史快照，其中“推荐”及“已采用”等表述不代表现行配置，其他 DPS 候选值未采用。

# 原版供体生命与近战 DPS 偏移校准（Minecraft 1.21.1）

调研日期：2026-11-13。遍历本地 `DefaultAttributes` 的 82 种非玩家、非盔甲架原版生物，包含仅命令生成的巨人和幻术师。

本文是**离线源码与模型几何初筛**，不是游戏内实际捕获的测量结果。原版属性从对应实体类的属性工厂及必要的生成覆盖读取；模型在独立无窗口 JVM 中烘焙，应用成年渲染缩放、常见可见性与贴身表层归并后，使用项目已编译的 `SurgicalVolumeSampler` 测量。未启动客户端或创建测试文件。

模型的完整固定动画姿态、纹理透明过滤、额外图层与动态重复绘制没有全部复现；因此下列生命整数与力量小数适合作为初版校准，最终值以相同状态的手术台实际捕获体积为准。末影龙尤其不能使用静态模型工厂结果定值。

## 比较规则

- 使用当前源码默认公式和默认参数，默认生命上限 **400**；保留体积 0.40625 → 20 血、体积 5.18423 → 100 血两个锚点。不读取玩家世界的配置或数据包覆写。
- 血量差距同时满足：绝对值至少 4 点，且至少为原版血量的 25%。
- 力量改为按完整双臂装配体的空手近战 DPS 反算，包含双臂协调；不再使用原有“单次伤害差至少 2 且达到 25%”的筛选门槛。所有有固定近战间隔的原生双臂供体均校准，允许负偏移。
- 生命偏移沿用原有整数候选与人工最终修正。力量偏移先由未舍入的 DPS 反算，按运行时精度四舍五入至小数点后 1 位；最终末影人与猪灵蛮兵按人工指定取 +3、凋灵骷髅取 +1，其他修正不变。展示的 DPS 保留两位小数。
- 攻击比较普通难度口径、20 TPS、持续贴身且命中的空手近战 DPS；不计装备、附魔、护甲、难度放大、追击停顿和特殊攻击。基准攻击力为 3；按左右各一条完整原生手臂的体积、最长轴长度估算伤害与间隔，逐臂计算后取平均，不能把双臂 DPS 直接相加。手臂实际关节链长度可能与模型最长轴不同；改变关节、肘、手臂数量或配置后不保证仍对齐。
- 非原生手臂生物可以把头、腿、身体等装成攻击肢体，不同选件没有唯一计算伤害，因此本轮不给这些生物猜测统一攻击偏移。
- 多数原版近战供体冷却为 20 tick；监守者使用 `MeleeAttack.create(18)`，原版近战目标为 30 × 20 / 18 ≈ 33.33 DPS。铁傀儡取随机裸伤均值 14.5，凋灵骷髅取生成时设置的空手攻击力 4；骷髅类按移除弓后的近战行为，卫道士按展开双臂的近战姿态估算。
- 巨人没有原版近战 AI；恼鬼冲刺后重新出手依赖距离与随机判定；掠夺者、唤魔者、幻术师没有空手近战行为。这五种不虚构固定近战 DPS，原有巨人 +30、恼鬼 +3、掠夺者 +2 人工力量保留，并明确不属于本次 DPS 对齐结果。

默认血量公式：`max(1, 400 / (1 + 19 * (0.40625 / volume)^e))`，`e = 0.7248744657`。表内“当前计算血量”是应用供体偏移之前的公式输出，单位为点；偏移后血量按 1–400 钳制。

生命实装采用下表“现有生命修正（本次不变）”列；生命“整数偏移”和“偏移后血量”仅保留初筛对齐结果。力量同时列出“现有单次伤害修正（校准前）”与“推荐单次伤害修正”，并分别估算两套修正下的 DPS。“现有”列记录 2026-11-13 按 DPS 校准前的人工值，未配置按 0 处理；资源文件当前已采用推荐值，旧值保留用于对照。体质按原供体完整体积分配到各体块，力量则按每条原生手臂的完整体积分配，再对全身／每条出手臂的体块贡献直接求和；无修正体块贡献为 0，不稀释已有组织。力量的最终值表示每一条完整原生手臂的修正，不与另一条手臂或全身组织平摊。

## 生命偏移候选

| 生物                          | 当前计算血量（偏移前） | 原版血量/基准 | 整数偏移 | 偏移后血量 | 状态或限制                                   | 现有生命修正（本次不变） |
| ----------------------------- | ---------------------: | ------------: | -------: | ---------: | -------------------------------------------- | -------- |
| 监守者 `warden`             |                  69.47 |           500 |     +431 |     400.00 | 原版500血超过当前400上限，偏移后仍受上限限制 | +100     |
| 恶魂 `ghast`                |                 295.56 |            10 |     -286 |       9.56 | 成年默认状态                                 | -200     |
| 凋灵 `wither`               |                  66.63 |           300 |     +233 |     299.63 | 原版300血低于当前400上限，可用偏移对齐       | +100     |
| 巨人 `giant`                |                 288.23 |           100 |     -188 |     100.23 | 仅命令生成                                   | -200     |
| 嗅探兽 `sniffer`            |                 132.63 |            14 |     -119 |      13.63 | 成年默认状态                                 | -100     |
| 远古守卫者 `elder_guardian` |                 156.18 |            80 |      -76 |      80.18 | 成年默认状态                                 | -50      |
| 铁傀儡 `iron_golem`         |                  46.03 |           100 |      +54 |     100.03 | 成年默认状态                                 | +50      |
| 熊猫 `panda`                |                  59.32 |            20 |      -39 |      20.32 | 普通基因；虚弱基因血量10另算                 | -10      |
| 骆驼 `camel`                |                  61.19 |            32 |      -29 |      32.19 | 成年默认状态                                 | -10      |
| 猪灵蛮兵 `piglin_brute`     |                  21.49 |            50 |      +29 |      50.49 | 成年默认状态                                 | +30      |
| 末影人 `enderman`           |                  17.09 |            40 |      +23 |      40.09 | 成年默认状态                                 | +20      |
| 羊 `sheep`                  |                  31.08 |             8 |      -23 |       8.08 | 未剪毛，含羊毛层；剪毛状态另算               | -10      |
| 牛 `cow`                    |                  32.10 |            10 |      -22 |      10.10 | 成年默认状态                                 | -10      |
| 哞菇 `mooshroom`            |                  32.10 |            10 |      -22 |      10.10 | 成年默认状态                                 | -10      |
| 雪傀儡 `snow_golem`         |                  25.86 |             4 |      -22 |       3.86 | 成年默认状态                                 | -10      |
| 发光鱿鱼 `glow_squid`       |                  29.08 |            10 |      -19 |      10.08 | 成年默认状态                                 | -10      |
| 疣猪兽 `hoglin`             |                  58.72 |            40 |      -19 |      39.72 | 成年默认状态                                 | -10      |
| 骷髅马 `skeleton_horse`     |                  34.08 |            15 |      -19 |      15.08 | 成年默认状态                                 | -10      |
| 鱿鱼 `squid`                |                  29.08 |            10 |      -19 |      10.08 | 成年默认状态                                 | -10      |
| 僵尸疣猪兽 `zoglin`         |                  58.72 |            40 |      -19 |      39.72 | 成年默认状态                                 | -10      |
| 僵尸马 `zombie_horse`       |                  34.08 |            15 |      -19 |      15.08 | 成年默认状态                                 | -10      |
| 山羊 `goat`                 |                  28.45 |            10 |      -18 |      10.45 | 成年默认状态                                 | -10      |
| 悦灵 `allay`                |                   3.81 |            20 |      +16 |      19.81 | 成年默认状态                                 | +10      |
| 炽足兽 `strider`            |                  36.45 |            20 |      -16 |      20.45 | 成年默认状态                                 | -10      |
| 猪 `pig`                    |                  23.34 |            10 |      -13 |      10.34 | 成年默认状态                                 | -10      |
| 马 `horse`                  |                  34.08 |          22.5 |      -12 |      22.08 | 随机血量15–30，取期望22.5                   | -10      |
| 幻术师 `illusioner`         |                  19.59 |            32 |      +12 |      31.59 | 成年默认状态                                 |          |
| 羊驼 `llama`                |                  34.62 |          22.5 |      -12 |      22.62 | 随机血量15–30，取期望22.5                   | -10      |
| 行商羊驼 `trader_llama`     |                  34.62 |          22.5 |      -12 |      22.62 | 随机血量15–30，取期望22.5                   | -10      |
| 北极熊 `polar_bear`         |                  40.62 |            30 |      -11 |      29.62 | 成年默认状态                                 | -10      |
| 恼鬼 `vex`                  |                   3.93 |            14 |      +10 |      13.93 | 成年默认状态                                 |          |
| 幻翼 `phantom`              |                  11.13 |            20 |       +9 |      20.13 | 尺寸0                                        |          |
| 海豚 `dolphin`              |                  17.60 |            10 |       -8 |       9.60 | 成年默认状态                                 |          |
| 岩浆怪 `magma_cube`         |                   8.71 |             1 |       -8 |       1.00 | 此行为尺寸1；尺寸2、4需另算                  |          |
| 史莱姆 `slime`              |                   8.76 |             1 |       -8 |       1.00 | 此行为尺寸1；尺寸2、4需另算                  |          |
| 烈焰人 `blaze`              |                  12.99 |            20 |       +7 |      19.99 | 成年默认状态                                 |          |
| 蜘蛛 `spider`               |                  22.75 |            16 |       -7 |      15.75 | 成年默认状态                                 |          |
| 骡 `mule`                   |                  28.93 |          22.5 |       -6 |      22.93 | 随机血量15–30，取期望22.5                   |          |
| 美西螈 `axolotl`            |                   8.86 |            14 |       +5 |      13.86 | 成年默认状态                                 |          |
| 鸡 `chicken`                |                   8.71 |             4 |       -5 |       3.71 | 成年默认状态                                 |          |
| 猪灵 `piglin`               |                  21.49 |            16 |       -5 |      16.49 | 成年默认状态                                 |          |
| 骷髅 `skeleton`             |                  14.90 |            20 |       +5 |      19.90 | 成年默认状态                                 |          |
| 流浪者 `stray`              |                  14.90 |            20 |       +5 |      19.90 | 成年默认状态                                 |          |
| 蝌蚪 `tadpole`              |                   1.00 |             6 |       +5 |       6.00 | 成年默认状态                                 |          |
| 狼 `wolf`                   |                  13.35 |             8 |       -5 |       8.35 | 野生；驯服后血量40未作为组织基准             |          |
| 蝙蝠 `bat`                  |                   1.75 |             6 |       +4 |       5.75 | 成年默认状态                                 |          |
| 猫 `cat`                    |                   5.93 |            10 |       +4 |       9.93 | 成年默认状态                                 |          |
| 末影螨 `endermite`          |                   3.73 |             8 |       +4 |       7.73 | 成年默认状态                                 |          |

凋灵的原版 300 血已在默认 400 上限内，偏移可将其对齐到约 300 血。监守者的原版 500 血仍超过上限；其偏移列为数学差值，按当前上限结算最多为 400 血。

## 原生双臂近战 DPS 校准

单臂几何伤害仍由原有体积／粗细公式生成。令 `I` 为该臂单臂恢复时间计入双臂协调后的全局间隔（tick），则完整对称双臂：

`未修正 DPS = 几何单次伤害 × 20 / I`

`推荐单次伤害修正 ΔD = round1(原版空手 DPS × I / 20 − 几何单次伤害)`

`修正后单次伤害 = max(0, 几何单次伤害 + ΔD)`

`修正后 DPS = 修正后单次伤害 × 20 / I`

所有力量修正列的单位均为**伤害点／次**；DPS 列仅展示应用该单次伤害修正后的估算结果。

左右臂几何不同时分别计算，再对 DPS 取平均；偏移按共同的线性系数反算。最终按人工指定，末影人与猪灵蛮兵取 +3、凋灵骷髅取 +1，其余保持原有校准值。这 15 种可校准供体的模型估算 DPS 相对误差均不超过 6.9%。力量仍是**每次命中的伤害加值**，不是直接给 DPS 相加，也不改攻击间隔。

| 生物 | 原版近战间隔（tick） | 装配单臂／全局间隔（tick） | 未修正 DPS | 原版空手 DPS | 现有单次伤害修正（校准前） | 现有伤害修正下 DPS | 推荐单次伤害修正 | 推荐伤害修正后 DPS | DPS 误差 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 监守者 `warden` | 18 | 28 / 25 | 5.54 | 33.33 | +15 | 17.54 | +34.7 | 33.30 | -0.09% |
| 铁傀儡 `iron_golem` | 20 | 22 / 20 | 4.50 | 14.50 | +10 | 14.50 | +10.0 | 14.50 | +0.02% |
| 末影人 `enderman` | 20 | 14 / 13 | 2.87 | 7.00 | +5 | 10.56 | +3.0 | 7.48 | +6.88% |
| 猪灵蛮兵 `piglin_brute` | 20 | 20 / 18 | 3.33 | 7.00 | +4 | 7.78 | +3.0 | 6.67 | -4.76% |
| 猪灵 `piglin` | 20 | 20 / 18 | 3.33 | 5.00 | +2 | 5.56 | +1.5 | 5.00 | +0.00% |
| 卫道士 `vindicator` | 20 | 19 / 17 | 3.19 | 5.00 | +2 | 5.55 | +1.5 | 4.96 | -0.84% |
| 僵尸猪灵 `zombified_piglin` | 20 | 20 / 18 | 3.33 | 5.00 | +2 | 5.56 | +1.5 | 5.00 | +0.00% |
| 凋灵骷髅 `wither_skeleton` | 20 | 15 / 14 | 2.59 | 4.00 | +2 | 5.44 | +1.0 | 4.02 | +0.41% |
| 溺尸 `drowned` | 20 | 20 / 18 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 | +0.00% |
| 尸壳 `husk` | 20 | 21 / 19 | 3.47 | 3.00 | +0 | 3.47 | -0.4 | 3.05 | +1.59% |
| 僵尸 `zombie` | 20 | 20 / 18 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 | +0.00% |
| 僵尸村民 `zombie_villager` | 20 | 20 / 18 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 | +0.00% |
| 沼骸 `bogged` | 20 | 14 / 13 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 | -3.25% |
| 骷髅 `skeleton` | 20 | 14 / 13 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 | -3.25% |
| 流浪者 `stray` | 20 | 14 / 13 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 | -3.25% |

末影人的力量由 +5 调整为 **+3**：DPS 公式反算值为 +2.7，最终按人工指定取 +3。在本次最长轴估算下，单次几何伤害约 1.86、双臂全局间隔 13 tick，修正后约 **7.48 DPS**，目标为原版 **7 DPS**。若实物关节链使解剖间隔变为 15 tick、全局间隔 14 tick，同一偏移下约为 **7.14 DPS**；这是离线几何近似带来的差异，需以实际装配数据进一步校准。猪灵蛮兵由公式反算的 +3.3 取为 **+3**，修正后约 **6.67 DPS**；凋灵骷髅维持 **+1**，修正后约 **4.02 DPS**。

| 无固定空手近战 DPS 的供体 | 未修正装配 DPS | 现有单次伤害修正（校准前） | 现有伤害修正下 DPS | 推荐单次伤害修正 | 推荐伤害修正后 DPS | 限制 |
| --- | ---: | ---: | ---: | --- | ---: | --- |
| 唤魔者 `evoker` | 3.19 | +0 | 3.19 | 保留 +0 | 3.19 | 原版施法，无空手近战目标 |
| 巨人 `giant` | 8.89 | +30 | 25.56 | 保留 +30 | 25.56 | 无原版近战 AI；保留人工力量 +30 |
| 幻术师 `illusioner` | 3.19 | +0 | 3.19 | 保留 +0 | 3.19 | 原版弓箭／施法，无空手近战目标 |
| 掠夺者 `pillager` | 3.19 | +2 | 5.55 | 保留 +2 | 5.55 | 原版仅弩攻击，无空手近战目标；保留人工力量 +2 |
| 恼鬼 `vex` | 1.53 | +3 | 6.53 | 保留 +3 | 6.53 | 冲刺命中后退出，重新冲刺取决于距离与随机判定；保留人工力量 +3 |

## 状态变化复核

同一物种仅配置一个固定数值时，不能同时对齐下列全部状态。马、骡、羊驼的偏移只对随机血量的期望值 22.5 标定，不代表所有个体都存在同等偏差。羊的含毛/剪毛状态、幻翼尺寸、史莱姆尺寸、河豚膨胀程度也会改变结果。

| 状态             | 当前计算血量（偏移前） | 原版血量 | 达标时的整数偏移 |
| ---------------- | ---------------------: | -------: | ---------------: |
| 史莱姆（尺寸4）  |                 125.37 |       16 |             -109 |
| 岩浆怪（尺寸4）  |                 124.88 |       16 |             -109 |
| 熊猫（虚弱基因） |                  59.32 |       10 |              -49 |
| 史莱姆（尺寸2）  |                  36.73 |        4 |              -33 |
| 岩浆怪（尺寸2）  |                  36.54 |        4 |              -33 |
| 狼（驯服）       |                  13.35 |       40 |              +27 |
| 羊（剪毛）       |                  21.07 |        8 |              -13 |
| 幻翼（尺寸0）    |                  11.13 |       20 |               +9 |
| 幻翼（尺寸4）    |                  29.47 |       20 |               -9 |
| 史莱姆（尺寸1）  |                   8.76 |        1 |               -8 |
| 岩浆怪（尺寸1）  |                   8.71 |        1 |               -8 |
| 河豚（完全膨胀） |                   8.96 |        3 |               -6 |
| 幻翼（尺寸2）    |                  19.28 |       20 |               +0 |
| 河豚（收缩）     |                   1.00 |        3 |               +0 |
| 河豚（半膨胀）   |                   3.84 |        3 |               +0 |

## 完整检索清单（82 种）

生命候选偏移与现有实装体质分别列出，本次不调整生命。现有力量为本轮校准前人工值；推荐力量对应资源文件当前采用的数值，包含末影人与猪灵蛮兵 +3、凋灵骷髅 +1 的人工指定值。“—”表示没有可直接定标的统一原生双臂／近战 DPS，或明确暂缓定值；没有原生手臂的生物不提供统一力量推荐。巨人、恼鬼、掠夺者的推荐为保留原人工值。

| 生物 | 体积（方块³） | 当前计算血量（偏移前） | 原版血量/基准 | 生命候选偏移 | 现有生命修正 | 双臂 DPS（偏移前） | 原版空手 DPS | 现有单次伤害修正（校准前） | 现有伤害修正下 DPS | 推荐单次伤害修正 | 推荐伤害修正后 DPS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 悦灵 `allay` | 0.038960 | 3.81 | 20 | +16 | +10 | — | — | +0 | — | — | — |
| 犰狳 `armadillo` | 0.201427 | 12.27 | 12 | +0 | +0 | — | — | +0 | — | — | — |
| 美西螈 `axolotl` | 0.127064 | 8.86 | 14 | +5 | +0 | — | — | +0 | — | — | — |
| 蝙蝠 `bat` | 0.013184 | 1.75 | 6 | +4 | +0 | — | — | +0 | — | — | — |
| 蜜蜂 `bee` | 0.122610 | 8.64 | 10 | +0 | +0 | — | — | +0 | — | — | — |
| 烈焰人 `blaze` | 0.218506 | 12.99 | 20 | +7 | +0 | — | — | +0 | — | — | — |
| 沼骸 `bogged` | 0.265625 | 14.90 | 16 | +0 | +0 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 |
| 猫 `cat` | 0.072190 | 5.93 | 10 | +4 | +0 | — | — | +0 | — | — | — |
| 骆驼 `camel` | 2.226013 | 61.19 | 32 | -29 | -10 | — | — | +0 | — | — | — |
| 洞穴蜘蛛 `cave_spider` | 0.168150 | 10.81 | 12 | +0 | +0 | — | — | +0 | — | — | — |
| 鸡 `chicken` | 0.123962 | 8.71 | 4 | -5 | +0 | — | — | +0 | — | — | — |
| 鳕鱼 `cod` | 0.020996 | 2.44 | 3 | +0 | +0 | — | — | +0 | — | — | — |
| 牛 `cow` | 0.815918 | 32.10 | 10 | -22 | -10 | — | — | +0 | — | — | — |
| 苦力怕 `creeper` | 0.312500 | 16.68 | 20 | +0 | +0 | — | — | +0 | — | — | — |
| 海豚 `dolphin` | 0.337669 | 17.60 | 10 | -8 | +0 | — | — | +0 | — | — | — |
| 驴 `donkey` | 0.590837 | 25.84 | 22.5 | +0 | +0 | — | — | +0 | — | — | — |
| 溺尸 `drowned` | 0.403687 | 19.91 | 20 | +0 | +0 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 |
| 远古守卫者 `elder_guardian` | 12.765019 | 156.18 | 80 | -76 | -50 | — | — | +0 | — | — | — |
| 末影人 `enderman` | 0.323700 | 17.09 | 40 | +23 | +20 | 2.87 | 7.00 | +5 | 10.56 | +3 | 7.48 |
| 末影螨 `endermite` | 0.037842 | 3.73 | 8 | +4 | +0 | — | — | +0 | — | — | — |
| 末影龙 `ender_dragon` | 20.635493 | 190.28 | 200 | — | +0 | — | — | +0 | — | — | — |
| 唤魔者 `evoker` | 0.394184 | 19.59 | 24 | +0 | +0 | 3.19 | — | +0 | 3.19 | +0 | 3.19 |
| 旋风人 `breeze` | 0.990788 | 36.51 | 30 | +0 | +0 | — | — | +0 | — | — | — |
| 狐狸 `fox` | 0.235346 | 13.69 | 10 | +0 | +0 | — | — | +0 | — | — | — |
| 青蛙 `frog` | 0.107409 | 7.87 | 10 | +0 | +0 | — | — | +0 | — | — | — |
| 恶魂 `ghast` | 99.118742 | 295.56 | 10 | -286 | -200 | — | — | +0 | — | — | — |
| 巨人 `giant` | 87.196290 | 288.23 | 100 | -188 | -200 | 8.89 | — | +30 | 25.56 | +30 | 25.56 |
| 发光鱿鱼 `glow_squid` | 0.703902 | 29.08 | 10 | -19 | -10 | — | — | +0 | — | — | — |
| 山羊 `goat` | 0.681487 | 28.45 | 10 | -18 | -10 | — | — | +0 | — | — | — |
| 守卫者 `guardian` | 0.971016 | 36.03 | 30 | +0 | +0 | — | — | +0 | — | — | — |
| 疣猪兽 `hoglin` | 2.082062 | 58.72 | 40 | -19 | -10 | — | — | +0 | — | — | — |
| 马 `horse` | 0.892897 | 34.08 | 22.5 | -12 | -10 | — | — | +0 | — | — | — |
| 尸壳 `husk` | 0.484207 | 22.56 | 20 | +0 | +0 | 3.47 | 3.00 | +0 | 3.47 | -0.4 | 3.05 |
| 幻术师 `illusioner` | 0.394184 | 19.59 | 32 | +12 | +0 | 3.19 | — | +0 | 3.19 | +0 | 3.19 |
| 铁傀儡 `iron_golem` | 1.415024 | 46.03 | 100 | +54 | +50 | 4.50 | 14.50 | +10 | 14.50 | +10 | 14.50 |
| 羊驼 `llama` | 0.914123 | 34.62 | 22.5 | -12 | -10 | — | — | +0 | — | — | — |
| 岩浆怪 `magma_cube` | 0.124023 | 8.71 | 1 | -8 | +0 | — | — | +0 | — | — | — |
| 哞菇 `mooshroom` | 0.815918 | 32.10 | 10 | -22 | -10 | — | — | +0 | — | — | — |
| 骡 `mule` | 0.698672 | 28.93 | 22.5 | -6 | +0 | — | — | +0 | — | — | — |
| 豹猫 `ocelot` | 0.140905 | 9.54 | 10 | +0 | +0 | — | — | +0 | — | — | — |
| 熊猫 `panda` | 2.116253 | 59.32 | 20 | -39 | -10 | — | — | +0 | — | — | — |
| 鹦鹉 `parrot` | 0.025906 | 2.84 | 6 | +0 | +0 | — | — | +0 | — | — | — |
| 幻翼 `phantom` | 0.175289 | 11.13 | 20 | +9 | +0 | — | — | +0 | — | — | — |
| 猪 `pig` | 0.508789 | 23.34 | 10 | -13 | -10 | — | — | +0 | — | — | — |
| 猪灵 `piglin` | 0.450951 | 21.49 | 16 | -5 | +0 | 3.33 | 5.00 | +2 | 5.56 | +1.5 | 5.00 |
| 猪灵蛮兵 `piglin_brute` | 0.450951 | 21.49 | 50 | +29 | +30 | 3.33 | 7.00 | +4 | 7.78 | +3 | 6.67 |
| 掠夺者 `pillager` | 0.394184 | 19.59 | 24 | +0 | +0 | 3.19 | — | +2 | 5.55 | +2 | 5.55 |
| 北极熊 `polar_bear` | 1.166031 | 40.62 | 30 | -11 | -10 | — | — | +0 | — | — | — |
| 河豚 `pufferfish` | 0.004883 | 1.00 | 3 | +0 | +0 | — | — | +0 | — | — | — |
| 兔子 `rabbit` | 0.029782 | 3.14 | 3 | +0 | +0 | — | — | +0 | — | — | — |
| 劫掠兽 `ravager` | 5.241790 | 100.60 | 100 | +0 | +0 | — | — | +0 | — | — | — |
| 鲑鱼 `salmon` | 0.035156 | 3.54 | 3 | +0 | +0 | — | — | +0 | — | — | — |
| 羊 `sheep` | 0.777420 | 31.08 | 8 | -23 | -10 | — | — | +0 | — | — | — |
| 潜影贝 `shulker` | 0.982468 | 36.31 | 30 | +0 | +0 | — | — | +0 | — | — | — |
| 蠹虫 `silverfish` | 0.102974 | 7.64 | 8 | +0 | +0 | — | — | +0 | — | — | — |
| 骷髅 `skeleton` | 0.265625 | 14.90 | 20 | +5 | +0 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 |
| 骷髅马 `skeleton_horse` | 0.892897 | 34.08 | 15 | -19 | -10 | — | — | +0 | — | — | — |
| 史莱姆 `slime` | 0.125000 | 8.76 | 1 | -8 | +0 | — | — | +0 | — | — | — |
| 嗅探兽 `sniffer` | 8.971241 | 132.63 | 14 | -119 | -100 | — | — | +0 | — | — | — |
| 雪傀儡 `snow_golem` | 0.591747 | 25.86 | 4 | -22 | -10 | — | — | +0 | — | — | — |
| 蜘蛛 `spider` | 0.490234 | 22.75 | 16 | -7 | +0 | — | — | +0 | — | — | — |
| 鱿鱼 `squid` | 0.703902 | 29.08 | 10 | -19 | -10 | — | — | +0 | — | — | — |
| 流浪者 `stray` | 0.265625 | 14.90 | 20 | +5 | +0 | 2.24 | 2.00 | +0 | 2.24 | -0.2 | 1.94 |
| 炽足兽 `strider` | 0.988281 | 36.45 | 20 | -16 | -10 | — | — | +0 | — | — | — |
| 蝌蚪 `tadpole` | 0.004395 | 1.00 | 6 | +5 | +0 | — | — | +0 | — | — | — |
| 行商羊驼 `trader_llama` | 0.914123 | 34.62 | 22.5 | -12 | -10 | — | — | +0 | — | — | — |
| 热带鱼 `tropical_fish` | 0.008789 | 1.30 | 3 | +0 | +0 | — | — | +0 | — | — | — |
| 海龟 `turtle` | 0.795122 | 31.55 | 30 | +0 | +0 | — | — | +0 | — | — | — |
| 恼鬼 `vex` | 0.040666 | 3.93 | 14 | +10 | +0 | 1.53 | — | +3 | 6.53 | +3 | 6.53 |
| 村民 `villager` | 0.394184 | 19.59 | 20 | +0 | +0 | — | — | +0 | — | — | — |
| 卫道士 `vindicator` | 0.394184 | 19.59 | 24 | +0 | +0 | 3.19 | 5.00 | +2 | 5.55 | +1.5 | 4.96 |
| 监守者 `warden` | 2.743652 | 69.47 | 500 | +431 | +100 | 5.54 | 33.33 | +15 | 17.54 | +34.7 | 33.30 |
| 流浪商人 `wandering_trader` | 0.394184 | 19.59 | 20 | +0 | +0 | — | — | +0 | — | — | — |
| 女巫 `witch` | 0.507752 | 23.30 | 26 | +0 | +0 | — | — | +0 | — | — | — |
| 凋灵 `wither` | 2.559784 | 66.63 | 300 | +233 | +100 | — | — | +0 | — | — | — |
| 凋灵骷髅 `wither_skeleton` | 0.459000 | 21.75 | 20 | +0 | +0 | 2.59 | 4.00 | +2 | 5.44 | +1 | 4.02 |
| 狼 `wolf` | 0.227089 | 13.35 | 8 | -5 | +0 | — | — | +0 | — | — | — |
| 僵尸疣猪兽 `zoglin` | 2.082062 | 58.72 | 40 | -19 | -10 | — | — | +0 | — | — | — |
| 僵尸 `zombie` | 0.403687 | 19.91 | 20 | +0 | +0 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 |
| 僵尸马 `zombie_horse` | 0.892897 | 34.08 | 15 | -19 | -10 | — | — | +0 | — | — | — |
| 僵尸村民 `zombie_villager` | 0.488281 | 22.69 | 20 | +0 | +0 | 3.33 | 3.00 | +0 | 3.33 | -0.3 | 3.00 |
| 僵尸猪灵 `zombified_piglin` | 0.450951 | 21.49 | 20 | +0 | +0 | 3.33 | 5.00 | +2 | 5.56 | +1.5 | 5.00 |

## 本地来源

- [来源版本与溯源](../ref/SOURCES.md)：Minecraft 1.21.1 的本地原版快照；未经 NeoForge 补丁的源码，对应版本与本项目一致。
- [完整属性注册清单](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)。
- [默认近战 20 tick 冷却](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java)、[监守者 18 tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/warden/WardenAi.java)、[猪灵 20 tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinAi.java)、[猪灵蛮兵 20 tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinBruteAi.java)。
- [装配体伤害与 DPS 共用计算](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalCombatCalibration.java)、[实装力量默认值](../src/main/resources/data/create_biotech/bionic_traits/damage_offset.json)。
- [原版模型映射](../ref/1.21.1/Minecraft/net/minecraft/client/model/geom/LayerDefinitions.java)。
- [当前默认属性表达式](../src/main/java/com/nobodiiiii/createbiotech/api/bionic/BionicStat.java)。
- [当前默认校准参数](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/stats/BionicStatParameters.java)。
- [项目体积算法](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalVolumeSampler.java)。
- [实际攻击几何烘焙](../src/main/java/com/nobodiiiii/createbiotech/entity/client/SlimeBionicAnimator.java)。
- [几何导出与初筛脚本、原始数据](../build/diagnostics/vanilla-stat-offset-survey/)；DPS 复算脚本为同目录 `recalibrate_dps.py`，参数、左右臂间隔和复算结果保存在 `dps-calibration.json`。

逐供体属性源码：

- 悦灵：[Allay.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/allay/Allay.java)。
- 犰狳：[Armadillo.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java)。
- 美西螈：[Axolotl.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/axolotl/Axolotl.java)。
- 蝙蝠：[Bat.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ambient/Bat.java)。
- 蜜蜂：[Bee.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Bee.java)。
- 烈焰人：[Blaze.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Blaze.java)。
- 沼骸：[Bogged.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Bogged.java)。
- 猫：[Cat.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Cat.java)。
- 骆驼：[Camel.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/camel/Camel.java)。
- 洞穴蜘蛛：[CaveSpider.createCaveSpider](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/CaveSpider.java)。
- 鸡：[Chicken.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Chicken.java)。
- 鳕鱼：[AbstractFish.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/AbstractFish.java)。
- 牛：[Cow.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Cow.java)。
- 苦力怕：[Creeper.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Creeper.java)。
- 海豚：[Dolphin.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Dolphin.java)。
- 驴：[AbstractChestedHorse.createBaseChestedHorseAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java)。
- 溺尸：[Drowned.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Drowned.java)。
- 远古守卫者：[ElderGuardian.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/ElderGuardian.java)。
- 末影人：[EnderMan.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java)。
- 末影螨：[Endermite.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Endermite.java)。
- 末影龙：[EnderDragon.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java)。
- 唤魔者：[Evoker.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Evoker.java)。
- 旋风人：[Breeze.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/breeze/Breeze.java)。
- 狐狸：[Fox.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Fox.java)。
- 青蛙：[Frog.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/frog/Frog.java)。
- 恶魂：[Ghast.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ghast.java)。
- 巨人：[Giant.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Giant.java)。
- 发光鱿鱼：[GlowSquid.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/GlowSquid.java)。
- 山羊：[Goat.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/goat/Goat.java)。
- 守卫者：[Guardian.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java)。
- 疣猪兽：[Hoglin.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/hoglin/Hoglin.java)。
- 马：[AbstractHorse.createBaseHorseAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java)。
- 尸壳：[Zombie.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)。
- 幻术师：[Illusioner.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Illusioner.java)。
- 铁傀儡：[IronGolem.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/IronGolem.java)。
- 羊驼：[Llama.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/Llama.java)。
- 岩浆怪：[MagmaCube.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/MagmaCube.java)。
- 哞菇：[Cow.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Cow.java)。
- 骡：[AbstractChestedHorse.createBaseChestedHorseAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java)。
- 豹猫：[Ocelot.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Ocelot.java)。
- 熊猫：[Panda.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Panda.java)。
- 鹦鹉：[Parrot.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Parrot.java)。
- 幻翼：[Monster.createMonsterAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Monster.java)。
- 猪：[Pig.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Pig.java)。
- 猪灵：[Piglin.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/Piglin.java)。
- 猪灵蛮兵：[PiglinBrute.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinBrute.java)。
- 掠夺者：[Pillager.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Pillager.java)。
- 北极熊：[PolarBear.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/PolarBear.java)。
- 河豚：[AbstractFish.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/AbstractFish.java)。
- 兔子：[Rabbit.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Rabbit.java)。
- 劫掠兽：[Ravager.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ravager.java)。
- 鲑鱼：[AbstractFish.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/AbstractFish.java)。
- 羊：[Sheep.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Sheep.java)。
- 潜影贝：[Shulker.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java)。
- 蠹虫：[Silverfish.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Silverfish.java)。
- 骷髅：[AbstractSkeleton.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/AbstractSkeleton.java)。
- 骷髅马：[SkeletonHorse.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/SkeletonHorse.java)。
- 史莱姆：[Monster.createMonsterAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Monster.java)。
- 嗅探兽：[Sniffer.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/sniffer/Sniffer.java)。
- 雪傀儡：[SnowGolem.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java)。
- 蜘蛛：[Spider.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java)。
- 鱿鱼：[Squid.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Squid.java)。
- 流浪者：[AbstractSkeleton.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/AbstractSkeleton.java)。
- 炽足兽：[Strider.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Strider.java)。
- 蝌蚪：[Tadpole.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/frog/Tadpole.java)。
- 行商羊驼：[Llama.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/Llama.java)。
- 热带鱼：[AbstractFish.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/AbstractFish.java)。
- 海龟：[Turtle.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Turtle.java)。
- 恼鬼：[Vex.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Vex.java)。
- 村民：[Villager.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/npc/Villager.java)。
- 卫道士：[Vindicator.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Vindicator.java)。
- 监守者：[Warden.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/warden/Warden.java)。
- 流浪商人：[Mob.createMobAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)。
- 女巫：[Witch.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Witch.java)。
- 凋灵：[WitherBoss.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java)。
- 凋灵骷髅：[AbstractSkeleton.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/AbstractSkeleton.java)。
- 狼：[Wolf.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Wolf.java)。
- 僵尸疣猪兽：[Zoglin.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zoglin.java)。
- 僵尸：[Zombie.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)。
- 僵尸马：[ZombieHorse.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/ZombieHorse.java)。
- 僵尸村民：[Zombie.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)。
- 僵尸猪灵：[ZombifiedPiglin.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/ZombifiedPiglin.java)。
