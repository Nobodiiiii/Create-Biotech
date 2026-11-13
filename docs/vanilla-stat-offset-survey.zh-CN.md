# 原版供体生命与攻击偏移初筛（Minecraft 1.21.1）

调研日期：2026-11-13。遍历本地 `DefaultAttributes` 的 82 种非玩家、非盔甲架原版生物，包含仅命令生成的巨人和幻术师。

本文是**离线源码与模型几何初筛**，不是游戏内实际捕获的测量结果。原版属性从对应实体类的属性工厂及必要的生成覆盖读取；模型在独立无窗口 JVM 中烘焙，应用成年渲染缩放、常见可见性与贴身表层归并后，使用项目已编译的 `SurgicalVolumeSampler` 测量。未启动客户端或创建测试文件。

模型的完整固定动画姿态、纹理透明过滤、额外图层与动态重复绘制没有全部复现；因此下列整数适合作为初版候选，最终值以相同状态的手术台实际捕获体积为准。末影龙尤其不能使用静态模型工厂结果定值。

## 比较规则

- 使用当前源码默认公式和默认参数，默认生命上限 **400**；保留体积 0.40625 → 20 血、体积 5.18423 → 100 血两个锚点。不读取玩家世界的配置或数据包覆写。
- 血量差距同时满足：绝对值至少 4 点，且至少为原版血量的 25%。
- 攻击差距同时满足：绝对值至少 2 点，且至少为对照值的 25%。
- 偏移先按未舍入值求差，再取最近整数；展示的计算值保留两位小数。
- 攻击比较空手单次组织伤害，基准攻击力 3，按一条完整原生手臂的体积、最长轴长度估算；不比较 DPS，不计装备、附魔、难度放大和特殊攻击。
- 非原生手臂生物可以把头、腿、身体等装成攻击肢体，不同选件没有唯一计算伤害，因此本轮不给这些生物猜测统一攻击偏移。
- 掠夺者一行以其攻击属性 5 为组织基准，不表示原版 AI 会徒手近战。铁傀儡取随机裸伤均值 14.5，凋灵骷髅使用生成时设置的空手攻击力 4。

默认血量公式：`max(1, 400 / (1 + 19 * (0.40625 / volume)^e))`，`e = 0.7248744657`。表内“当前计算血量”是应用供体偏移之前的公式输出，单位为点；偏移后血量按 1–400 钳制。

实装采用下表生命候选与完整清单中手工填写的“最终修正”列：前者为体质，后者为力量；留空按 0 处理。“整数偏移”和“偏移后血量”保留初筛对齐结果，不代表最终采用值。体质按原供体完整体积分配到各体块，力量则按每条原生手臂的完整体积分配，再对全身／每条出手臂的体块贡献直接求和；无修正体块贡献为 0，不稀释已有组织。力量的最终值表示每一条完整原生手臂的修正，不与另一条手臂或全身组织平摊。

## 生命偏移候选

| 生物                          | 当前计算血量（偏移前） | 原版血量/基准 | 整数偏移 | 偏移后血量 | 状态或限制                                   | 最终修正 |
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

## 原生手臂攻击偏移候选

| 生物                          | 当前计算单次伤害（偏移前） | 原版空手基准 | 整数偏移 |
| ----------------------------- | -------------------------: | -----------: | -------: |
| 巨人 `giant`                |                      16.00 |           50 |      +34 |
| 监守者 `warden`             |                       6.93 |           30 |      +23 |
| 铁傀儡 `iron_golem`         |                       4.50 |         14.5 |      +10 |
| 末影人 `enderman`           |                       1.86 |            7 |       +5 |
| 猪灵蛮兵 `piglin_brute`     |                       3.00 |            7 |       +4 |
| 恼鬼 `vex`                  |                       0.92 |            4 |       +3 |
| 猪灵 `piglin`               |                       3.00 |            5 |       +2 |
| 掠夺者 `pillager`           |                       2.71 |            5 |       +2 |
| 卫道士 `vindicator`         |                       2.71 |            5 |       +2 |
| 凋灵骷髅 `wither_skeleton`  |                       1.81 |            4 |       +2 |
| 僵尸猪灵 `zombified_piglin` |                       3.00 |            5 |       +2 |

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

`0` 表示未达到本次偏差门槛；`—` 表示没有可直接定标的统一原生手臂/攻击基准，或明确暂缓定值。

| 生物                          | 体积（方块³） | 当前计算血量（偏移前） | 原版血量/基准 | 生命偏移 | 当前计算臂伤害（偏移前） | 攻击对照 | 攻击偏移 | 最终修正 |
| ----------------------------- | -------------: | ---------------------: | ------------: | -------: | -----------------------: | -------: | -------: | -------- |
| 悦灵 `allay`                |       0.038960 |                   3.81 |            20 |      +16 |                       — |       — |       — |          |
| 犰狳 `armadillo`            |       0.201427 |                  12.27 |            12 |       +0 |                       — |       — |       — |          |
| 美西螈 `axolotl`            |       0.127064 |                   8.86 |            14 |       +5 |                       — |       — |       — |          |
| 蝙蝠 `bat`                  |       0.013184 |                   1.75 |             6 |       +4 |                       — |       — |       — |          |
| 蜜蜂 `bee`                  |       0.122610 |                   8.64 |            10 |       +0 |                       — |       — |       — |          |
| 烈焰人 `blaze`              |       0.218506 |                  12.99 |            20 |       +7 |                       — |       — |       — |          |
| 沼骸 `bogged`               |       0.265625 |                  14.90 |            16 |       +0 |                     1.46 |        2 |       +0 |          |
| 猫 `cat`                    |       0.072190 |                   5.93 |            10 |       +4 |                       — |       — |       — |          |
| 骆驼 `camel`                |       2.226013 |                  61.19 |            32 |      -29 |                       — |       — |       — |          |
| 洞穴蜘蛛 `cave_spider`      |       0.168150 |                  10.81 |            12 |       +0 |                       — |       — |       — |          |
| 鸡 `chicken`                |       0.123962 |                   8.71 |             4 |       -5 |                       — |       — |       — |          |
| 鳕鱼 `cod`                  |       0.020996 |                   2.44 |             3 |       +0 |                       — |       — |       — |          |
| 牛 `cow`                    |       0.815918 |                  32.10 |            10 |      -22 |                       — |       — |       — |          |
| 苦力怕 `creeper`            |       0.312500 |                  16.68 |            20 |       +0 |                       — |       — |       — |          |
| 海豚 `dolphin`              |       0.337669 |                  17.60 |            10 |       -8 |                       — |       — |       — |          |
| 驴 `donkey`                 |       0.590837 |                  25.84 |          22.5 |       +0 |                       — |       — |       — |          |
| 溺尸 `drowned`              |       0.403687 |                  19.91 |            20 |       +0 |                     3.00 |        3 |       +0 |          |
| 远古守卫者 `elder_guardian` |      12.765019 |                 156.18 |            80 |      -76 |                       — |       — |       — |          |
| 末影人 `enderman`           |       0.323700 |                  17.09 |            40 |      +23 |                     1.86 |        7 |       +5 | +5       |
| 末影螨 `endermite`          |       0.037842 |                   3.73 |             8 |       +4 |                       — |       — |       — |          |
| 末影龙 `ender_dragon`       |      20.635493 |                 190.28 |           200 |       — |                       — |       — |       — |          |
| 唤魔者 `evoker`             |       0.394184 |                  19.59 |            24 |       +0 |                     2.71 |        2 |       +0 |          |
| 旋风人 `breeze`             |       0.990788 |                  36.51 |            30 |       +0 |                       — |       — |       — |          |
| 狐狸 `fox`                  |       0.235346 |                  13.69 |            10 |       +0 |                       — |       — |       — |          |
| 青蛙 `frog`                 |       0.107409 |                   7.87 |            10 |       +0 |                       — |       — |       — |          |
| 恶魂 `ghast`                |      99.118742 |                 295.56 |            10 |     -286 |                       — |       — |       — |          |
| 巨人 `giant`                |      87.196290 |                 288.23 |           100 |     -188 |                    16.00 |       50 |      +34 | +30      |
| 发光鱿鱼 `glow_squid`       |       0.703902 |                  29.08 |            10 |      -19 |                       — |       — |       — |          |
| 山羊 `goat`                 |       0.681487 |                  28.45 |            10 |      -18 |                       — |       — |       — |          |
| 守卫者 `guardian`           |       0.971016 |                  36.03 |            30 |       +0 |                       — |       — |       — |          |
| 疣猪兽 `hoglin`             |       2.082062 |                  58.72 |            40 |      -19 |                       — |       — |       — |          |
| 马 `horse`                  |       0.892897 |                  34.08 |          22.5 |      -12 |                       — |       — |       — |          |
| 尸壳 `husk`                 |       0.484207 |                  22.56 |            20 |       +0 |                     3.30 |        3 |       +0 |          |
| 幻术师 `illusioner`         |       0.394184 |                  19.59 |            32 |      +12 |                     2.71 |        2 |       +0 |          |
| 铁傀儡 `iron_golem`         |       1.415024 |                  46.03 |           100 |      +54 |                     4.50 |     14.5 |      +10 | +10      |
| 羊驼 `llama`                |       0.914123 |                  34.62 |          22.5 |      -12 |                       — |       — |       — |          |
| 岩浆怪 `magma_cube`         |       0.124023 |                   8.71 |             1 |       -8 |                       — |       — |       — |          |
| 哞菇 `mooshroom`            |       0.815918 |                  32.10 |            10 |      -22 |                       — |       — |       — |          |
| 骡 `mule`                   |       0.698672 |                  28.93 |          22.5 |       -6 |                       — |       — |       — |          |
| 豹猫 `ocelot`               |       0.140905 |                   9.54 |            10 |       +0 |                       — |       — |       — |          |
| 熊猫 `panda`                |       2.116253 |                  59.32 |            20 |      -39 |                       — |       — |       — |          |
| 鹦鹉 `parrot`               |       0.025906 |                   2.84 |             6 |       +0 |                       — |       — |       — |          |
| 幻翼 `phantom`              |       0.175289 |                  11.13 |            20 |       +9 |                       — |       — |       — |          |
| 猪 `pig`                    |       0.508789 |                  23.34 |            10 |      -13 |                       — |       — |       — |          |
| 猪灵 `piglin`               |       0.450951 |                  21.49 |            16 |       -5 |                     3.00 |        5 |       +2 | +2       |
| 猪灵蛮兵 `piglin_brute`     |       0.450951 |                  21.49 |            50 |      +29 |                     3.00 |        7 |       +4 | +4       |
| 掠夺者 `pillager`           |       0.394184 |                  19.59 |            24 |       +0 |                     2.71 |        5 |       +2 | +2       |
| 北极熊 `polar_bear`         |       1.166031 |                  40.62 |            30 |      -11 |                       — |       — |       — |          |
| 河豚 `pufferfish`           |       0.004883 |                   1.00 |             3 |       +0 |                       — |       — |       — |          |
| 兔子 `rabbit`               |       0.029782 |                   3.14 |             3 |       +0 |                       — |       — |       — |          |
| 劫掠兽 `ravager`            |       5.241790 |                 100.60 |           100 |       +0 |                       — |       — |       — |          |
| 鲑鱼 `salmon`               |       0.035156 |                   3.54 |             3 |       +0 |                       — |       — |       — |          |
| 羊 `sheep`                  |       0.777420 |                  31.08 |             8 |      -23 |                       — |       — |       — |          |
| 潜影贝 `shulker`            |       0.982468 |                  36.31 |            30 |       +0 |                       — |       — |       — |          |
| 蠹虫 `silverfish`           |       0.102974 |                   7.64 |             8 |       +0 |                       — |       — |       — |          |
| 骷髅 `skeleton`             |       0.265625 |                  14.90 |            20 |       +5 |                     1.46 |        2 |       +0 |          |
| 骷髅马 `skeleton_horse`     |       0.892897 |                  34.08 |            15 |      -19 |                       — |       — |       — |          |
| 史莱姆 `slime`              |       0.125000 |                   8.76 |             1 |       -8 |                       — |       — |       — |          |
| 嗅探兽 `sniffer`            |       8.971241 |                 132.63 |            14 |     -119 |                       — |       — |       — |          |
| 雪傀儡 `snow_golem`         |       0.591747 |                  25.86 |             4 |      -22 |                       — |       — |       — |          |
| 蜘蛛 `spider`               |       0.490234 |                  22.75 |            16 |       -7 |                       — |       — |       — |          |
| 鱿鱼 `squid`                |       0.703902 |                  29.08 |            10 |      -19 |                       — |       — |       — |          |
| 流浪者 `stray`              |       0.265625 |                  14.90 |            20 |       +5 |                     1.46 |        2 |       +0 |          |
| 炽足兽 `strider`            |       0.988281 |                  36.45 |            20 |      -16 |                       — |       — |       — |          |
| 蝌蚪 `tadpole`              |       0.004395 |                   1.00 |             6 |       +5 |                       — |       — |       — |          |
| 行商羊驼 `trader_llama`     |       0.914123 |                  34.62 |          22.5 |      -12 |                       — |       — |       — |          |
| 热带鱼 `tropical_fish`      |       0.008789 |                   1.30 |             3 |       +0 |                       — |       — |       — |          |
| 海龟 `turtle`               |       0.795122 |                  31.55 |            30 |       +0 |                       — |       — |       — |          |
| 恼鬼 `vex`                  |       0.040666 |                   3.93 |            14 |      +10 |                     0.92 |        4 |       +3 | +3       |
| 村民 `villager`             |       0.394184 |                  19.59 |            20 |       +0 |                       — |       — |       — |          |
| 卫道士 `vindicator`         |       0.394184 |                  19.59 |            24 |       +0 |                     2.71 |        5 |       +2 | +2       |
| 监守者 `warden`             |       2.743652 |                  69.47 |           500 |     +431 |                     6.93 |       30 |      +23 | +15      |
| 流浪商人 `wandering_trader` |       0.394184 |                  19.59 |            20 |       +0 |                       — |       — |       — |          |
| 女巫 `witch`                |       0.507752 |                  23.30 |            26 |       +0 |                       — |       — |       — |          |
| 凋灵 `wither`               |       2.559784 |                  66.63 |           300 |     +233 |                       — |       — |       — |          |
| 凋灵骷髅 `wither_skeleton`  |       0.459000 |                  21.75 |            20 |       +0 |                     1.81 |        4 |       +2 | +2       |
| 狼 `wolf`                   |       0.227089 |                  13.35 |             8 |       -5 |                       — |       — |       — |          |
| 僵尸疣猪兽 `zoglin`         |       2.082062 |                  58.72 |            40 |      -19 |                       — |       — |       — |          |
| 僵尸 `zombie`               |       0.403687 |                  19.91 |            20 |       +0 |                     3.00 |        3 |       +0 |          |
| 僵尸马 `zombie_horse`       |       0.892897 |                  34.08 |            15 |      -19 |                       — |       — |       — |          |
| 僵尸村民 `zombie_villager`  |       0.488281 |                  22.69 |            20 |       +0 |                     3.00 |        3 |       +0 |          |
| 僵尸猪灵 `zombified_piglin` |       0.450951 |                  21.49 |            20 |       +0 |                     3.00 |        5 |       +2 | +2       |

## 本地来源

- [来源版本与溯源](../ref/SOURCES.md)：Minecraft 1.21.1 的本地原版快照；未经 NeoForge 补丁的源码，对应版本与本项目一致。
- [完整属性注册清单](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)。
- [原版模型映射](../ref/1.21.1/Minecraft/net/minecraft/client/model/geom/LayerDefinitions.java)。
- [当前默认属性表达式](../src/main/java/com/nobodiiiii/createbiotech/api/bionic/BionicStat.java)。
- [当前默认校准参数](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/stats/BionicStatParameters.java)。
- [项目体积算法](../src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalVolumeSampler.java)。
- [实际攻击几何烘焙](../src/main/java/com/nobodiiiii/createbiotech/entity/client/SlimeBionicAnimator.java)。
- [几何导出与初筛脚本、原始数据](../build/diagnostics/vanilla-stat-offset-survey/)。

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
