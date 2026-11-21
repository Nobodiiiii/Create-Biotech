# 原版供体最终生命与伤害修正总表（Minecraft 1.21.1）

下表列出有默认修正的原版供体。固定值见 [health_offset.json](../src/main/resources/data/create_biotech/bionic_traits/health_offset.json) 和 [damage_offset.json](../src/main/resources/data/create_biotech/bionic_traits/damage_offset.json)。

生命修正是完整供体的体质总额；单次伤害修正是每条完整原生手臂每次命中的伤害加减值，均以伤害点计。

| 生物 | 最终生命修正 | 最终单次伤害修正 |
| --- | ---: | ---: |
| 悦灵 `allay` | +10 | +0 |
| 骆驼 `camel` | -10 | +0 |
| 牛 `cow` | -10 | +0 |
| 远古守卫者 `elder_guardian` | -50 | +0 |
| 末影人 `enderman` | +20 | +3 |
| 恶魂 `ghast` | -200 | +0 |
| 巨人 `giant` | -200 | +30 |
| 发光鱿鱼 `glow_squid` | -10 | +0 |
| 山羊 `goat` | -10 | +0 |
| 疣猪兽 `hoglin` | -10 | +0 |
| 马 `horse` | -10 | +0 |
| 铁傀儡 `iron_golem` | +50 | +10 |
| 羊驼 `llama` | -10 | +0 |
| 岩浆怪 `magma_cube` | 随尺寸，小／中／大为 −10／−35／−110 | +0 |
| 哞菇 `mooshroom` | -10 | +0 |
| 熊猫 `panda` | -10 | +0 |
| 猪 `pig` | -10 | +0 |
| 猪灵 `piglin` | +0 | +2 |
| 猪灵蛮兵 `piglin_brute` | +30 | +3 |
| 掠夺者 `pillager` | +0 | +2 |
| 北极熊 `polar_bear` | -10 | +0 |
| 羊 `sheep` | -10 | +0 |
| 骷髅马 `skeleton_horse` | -10 | +0 |
| 史莱姆 `slime` | 随尺寸，小／中／大为 −10／−35／−110 | +0 |
| 嗅探兽 `sniffer` | -100 | +0 |
| 雪傀儡 `snow_golem` | -10 | +0 |
| 鱿鱼 `squid` | -10 | +0 |
| 炽足兽 `strider` | -10 | +0 |
| 行商羊驼 `trader_llama` | -10 | +0 |
| 恼鬼 `vex` | +0 | +3 |
| 卫道士 `vindicator` | +0 | +2 |
| 监守者 `warden` | +100 | +15 |
| 凋灵 `wither` | +100 | +0 |
| 凋灵骷髅 `wither_skeleton` | +0 | +1 |
| 僵尸疣猪兽 `zoglin` | -10 | +0 |
| 僵尸马 `zombie_horse` | -10 | +0 |
| 僵尸猪灵 `zombified_piglin` | +0 | +2 |

史莱姆与岩浆怪的完整供体体质等于原生血量减去默认几何血量，再取最接近的 5 的倍数。实际尺寸 `s` 取 1～127，参考体积为 `s³ / 8`。尺寸 3 的原版血量为 9，几何血量约 78.51，体质为 −70。模组的吞噬者也用这条公式，原生血量为 `4s²`，自然三档体质为 −5／−20／−60。三种供体的原生血量均以 1024 为上限。

[计算过程、旧值对照与推荐方案归档](vanilla-stat-offset-survey-archive-2026-11-13.zh-CN.md)。
