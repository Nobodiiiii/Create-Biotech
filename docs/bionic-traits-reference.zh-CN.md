# 仿生生物特性总表 v3

修订日期：2026-11-09。适用分支：Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281。

本表按当前工作区代码与数据整理 v3 特性规则与方法选用。现有 65 项经体表刺击并入接触反伤后共 64 项，按全身、躯干、头、臂、腿排列。原“修正”列随完整原表归档于[原修正表](bionic-traits-reference-v2-corrections-2026-11-06.zh-CN.md)，此前记录见[特性修正历史](bionic-traits-history.zh-CN.md)。

## 一、通用规则

### 1. 方法选用与标签

优先直接复用现有方法、Goal、控制器、导航、物品容器与模型部件。绑定供体具体实体类的内部方法，按原方法的分支、计数器、触发时机和调用顺序复刻。相似行为合并后选一个较常见供体作为统一模板，供体共同贡献同一项覆盖率。具有不同效果种类的投射、药效与施法沿用各自子项。

“技能”标记参与索敌、寻路、移动控制、行为动作或主动施放的特性；“被动”标记体质、数值、命中附效与交互产出。技能的使用时机沿用模板 Goal／Brain 的 `canUse`、`start`、`tick`、`stop`、记忆及冷却流程。

装配占比、有效肢体数量、翅膀承重、史莱姆球驯服、主人关系、智力选择、装配座位及明确写出的现有装配系数沿用本项目规则。供体的行为时序按表内原版模板执行。伤害、传送、物品交互和方块变更接入当前 NeoForge 事件流程。

| 合并行为 | 统一模板 | 采用的方法 |
| --- | --- | --- |
| 接触反伤、体表刺击 | 守卫者 | `Guardian.hurt` 的静止反伤分支，沿用 2c 伤害 |
| 末影人、潜影贝瞬移 | 末影人 | `EnderMan.hurt`、`teleport`、`teleportTowards` 与目标 Goal |
| 潜影贝、犰狳缩壳 | 犰狳 | `ArmadilloBallUp` 与卷缩状态、减伤方法 |
| 保湿依赖 | 海豚 | `Dolphin.tick` 的 2400 tick 保湿与干涸伤害 |
| 离水窒息 | 鱼类 | `WaterAnimal.handleAirSupply` 的计时与伤害 |
| 摔落抵抗 | 马 | 安全距离 6、伤害倍率 0.5 |
| 近战击飞 | 铁傀儡 | 成功命中后纵向增加 0.4×抗击退系数 |
| 中毒附效攻击 | 蜜蜂 | 按难度施加中毒的分支 |
| 呼唤同伴 | 狼 | `HurtByTargetGoal.setAlertOthers` 的受伤呼援流程 |
| 振翅缓降 | 鸡 | 下落纵向速度 ×0.6 |
| 游泳物理 | 美西螈 | 平滑游泳控制、中性浮力与水中阻尼 |

### 2. 范围、载体与覆盖率

| 作用范围 | 代码 | 判定体块 |
| --- | --- | --- |
| 全身 | `WHOLE_BODY` | 全部保留体块 |
| 躯干 | `TORSO` | 装配中归入躯干的保留体块 |
| 头 | `HEAD` | 颈关节安装的唯一头部及所属体块 |
| 臂 | `ARM` | 每条肩关节及所属肘关节链 |
| 腿 | `LEG` | 每条髋关节及所属膝关节链 |

供体载体标签用于筛选贡献体块，作用范围由装配位置确定。覆盖率 c = 匹配供体及载体的体积 / 对应范围全部保留体块体积。全身、躯干整体统计；头、臂、腿逐条完整肢体链统计。体积采用捕获模型体块实际尺寸，平面体块每条边至少按 1 像素计。手术台以各原始体块保存的有效体积测量。

手术台悬停提示按当前体块的真实载体筛选特性，纸箱中的装配碎片按实际保留的载体筛选；生效条件仍检查整个装配体。组合能力中只负责支撑的身子、头部主体或底座不会显示执行器官的特性：可剪毛只显示在羊毛上，缩壳防御与脱落鳞甲只显示在外壳上，音波轰击只显示在左右胸腔上，振动感知只显示在触须上，吼叫只显示在嘴部上。可挤奶在乳房和已配合乳房生效的身体上显示，后缀列出实际可获取的“奶桶”“蘑菇煲”；完整牛身体与完整捕获的牛均显示对应交互，普通腿部不显示。普通羊腿不显示可剪毛，独立保留的腿部羊毛仍按羊毛部件判定。

性情、智力采用安装在头部的供体原始头部保留比例，同一来源碎片按体块去重合并。呼吸载体优先使用鳃映射，供体采用头部映射时使用完整头部。有效腿按达标髋关节链计数；有效翼按达标肩关节链计数。扑翼飞行还采用现有承重比 16。

### 3. 三类生效与数值

| 类别 | 判定 | 效果 |
| --- | --- | --- |
| 存在型 `PRESENCE` | 对应范围内保留匹配载体 | 获得完整特性 |
| 门槛型 `COVERAGE_THRESHOLD` | 覆盖率达到 `min_coverage`，默认 0.5，并满足表内附加条件 | 获得固定效果 |
| 比例型 `COVERAGE_SCALED` | c > 0 | 按覆盖率或数值汇总系数执行 |

天然护甲、抗击退、自然恢复、击退强化按供体数值从高到低取前 `min_coverage`（默认 50%）体积加权平均，剩余取值体积以 0 补齐。**汇总原始值达到 0.1 时生效，并四舍五入保留一位小数；计算与提示共用该结果。** 例如单一马系自然恢复约 0.0222／秒，对应有效汇总值为 0.0；完整劫掠兽抗击退贡献 0.75，量化后为 0.8。

效果免疫按每种效果分别累计体积并判定门槛。附效攻击按命中臂上的对应载体执行。腿部特性按有效腿数判定；扑翼飞行按有效翼数与承重判定；游泳专长按躯干覆盖率判定。持续呼吸模式优先采用“无需呼吸”，其次采用水生或空气呼吸模式。

供体覆盖按自动探测、实体标签、精确实体 ID 的顺序应用。规范文件位于 `data/create_biotech/bionic_traits/`；扩展命名空间的同名文件提供 `values` 覆盖。性情和智力分类分别沿用 `bionic_head/disposition/`、`bionic_head/intelligence/`。数值项的 `min_coverage` 表示取值体积比例，门槛型表示获取比例，存在型和普通比例型按各自规则计算。

## 二、按作用部位排列的特性总表（`bionic_traits`）

下表每项均列出供体、载体、生效规则、效果与方法依据。全身和腿部的“摔落免疫”保留各自 ID 与获取范围。

| 作用部位 | 特性 ID | 中文名 | 标签 | 供体载体 | 对应生物（来源） | 生效条件／汇总方式 | 效果 | 方法选用与依据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 全身 | `fire_immune` | 火焰免疫 | 被动 | 供体全部保留体块 | 自动：`EntityType.fireImmune()`，即烈焰人、末影龙、恶魂、岩浆怪、潜影贝、炽足兽、监守者、凋灵、凋灵骷髅、僵尸疣猪兽、僵尸猪灵 | c ≥ 0.5 | 获得火焰伤害免疫与灭火效果；提示显示火焰免疫 | 复用 [Entity.fireImmune](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 与火焰伤害标签 |
| 全身 | `fire_resistance` | 火焰抗性 | 被动 | 供体全部保留体块 | 同火焰免疫的供体，独立数据文件 | c > 0 | 火焰类伤害 ×(1−c)；提示在 c < 0.5 时显示 | 复用 [Entity.fireImmune](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 判定供体；沿用现有覆盖率减伤 |
| 全身 | `water_sensitive` | 遇水受伤 | 被动 | 供体全部保留体块 | 自动：`isSensitiveToWater()`，即烈焰人、末影人、炽足兽、雪傀儡 | c ≥ 0.5 | 在水、雨或气泡中每 tick 尝试受到 1 点溺水伤害，沿用原版伤害冷却 | 复用 [LivingEntity.isSensitiveToWater / aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `freeze_immune` | 冻结免疫 | 被动 | 供体全部保留体块 | 自动：标签`freeze_immune_entity_types`，即流浪者、北极熊、雪傀儡、凋灵 | c ≥ 0.5 | 获得冻结与冰冻伤害免疫；提示显示冻结免疫 | 复用 [Entity.canFreeze](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Entity.java) 与 `FREEZE_IMMUNE_ENTITY_TYPES` |
| 全身 | `freeze_resistance` | 冻结抗性 | 被动 | 供体全部保留体块 | 同冻结免疫的供体，独立数据文件 | c > 0 | 冰冻伤害 ×(1−c)；提示在 c < 0.5 时显示 | 复用 `FREEZE_IMMUNE_ENTITY_TYPES`；沿用现有覆盖率减伤，入口见 [LivingEntity.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `freeze_vulnerable` | 冻结易伤 | 被动 | 供体全部保留体块 | 自动：标签`freeze_hurts_extra_types`，即炽足兽、烈焰人、岩浆怪 | c > 0 | 冰冻伤害 ×(1+4c)，与冻结抗性系数相乘 | 复用 `FREEZE_HURTS_EXTRA_TYPES`；按覆盖率映射原版 5 倍冰冻伤害，见 [LivingEntity.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 全身 | `sun_sensitive` | 阳光灼伤 | 被动 | 供体全部保留体块 | 数据：僵尸、僵尸村民、溺尸、骷髅、流浪者、沼骸、幻翼 | c ≥ 0.5；满足 `isSunBurnTick()` | 日晒时点燃 8 秒；佩戴头盔时先按原方法消耗耐久 | 复用 [Mob.isSunBurnTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)；复刻 [Zombie.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java) 的头盔耐久与点燃顺序 |
| 全身 | `moisture_dependent` | 保湿依赖 | 被动 | 供体全部保留体块 | 数据：美西螈、海豚 | c ≥ 0.5；统一采用海豚模板 | 保湿值 2400 tick；水、雨或气泡使其补满，干燥时逐 tick 减少，归零后每 tick 尝试受到 1 点干涸伤害 | 以海豚为模板，复刻 [Dolphin.tick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Dolphin.java) 的保湿计时与伤害触发 |
| 全身 | `heat_sensitive` | 高温灼伤 | 被动 | 供体全部保留体块 | 数据：雪傀儡 | c ≥ 0.5；位于 `snow_golem_melts` 群系 | 每 tick 尝试受到 1 点火焰伤害，沿用原版伤害冷却 | 复刻 [SnowGolem.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) 的群系标签判断与伤害调用 |
| 全身 | `inverted_healing` | 治疗反转 | 被动 | 供体全部保留体块 | 自动：`isInvertedHealAndHarm()`，即 `#undead`（骷髅类、僵尸类、凋灵、幻翼） | c ≥ 0.5 | 瞬间治疗与瞬间伤害效果互换 | 复用 [LivingEntity.isInvertedHealAndHarm](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 与原版瞬时效果入口 |
| 全身 | `fall_damage_immune` | 摔落免疫 | 被动 | 供体全部保留体块 | 数据：岩浆怪、铁傀儡、雪傀儡、潜影贝、旋风人 | c ≥ 0.5 | 获得摔落伤害免疫 | 复用 [LivingEntity.causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的摔落免疫分支 |
| 全身 | `web_adapted` | 无视蛛网 | 被动 | 供体全部保留体块 | 数据：蜘蛛、洞穴蜘蛛 | c ≥ 0.5 | 穿过蛛网时保持正常移动速度 | 复刻 [Spider.makeStuckInBlock](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java) 的蛛网分支 |
| 全身 | `projectile_deflection` | 弹射物偏转 | 被动 | 供体全部保留体块 | 自动：标签`deflects_projectiles`，即旋风人 | c ≥ 0.5 | 采用原版偏转策略；普通弹射物反弹，风弹沿用原版独立命中规则 | 复用 [Projectile.hitTargetOrDeflectSelf](../ref/1.21.1/Minecraft/net/minecraft/world/entity/projectile/Projectile.java) 与原版偏转策略 |
| 全身 | `contact_retaliation` | 接触反伤 | 被动 | 守卫者、远古守卫者：全部保留体块；河豚：刺 `SPINE`（`body`／`spikes`／`spine`） | 守卫者、远古守卫者、河豚 | c > 0；静止防御状态下受到活体直接攻击 | 向攻击者造成 2c 点荆棘伤害；按守卫者的荆棘伤害标签判定适用伤害源 | 统一采用守卫者模板，复刻 [Guardian.hurt](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java) 的触发条件；伤害沿用现有比例系数 |
| 全身 | `immune_effects` | 免疫效果（…） | 被动 | 供体全部保留体块 | 自动：逐个效果调用`canBeAffected` 探测。`#undead` 免疫中毒和生命恢复；蜘蛛、洞穴蜘蛛免疫中毒；凋灵、凋灵骷髅免疫凋零；蠹虫免疫寄生；史莱姆免疫渗浆 | 每种效果各自的全身覆盖率 ≥ 0.5 | 获得各自达标的药水效果免疫，可同时获得多种免疫 | 逐效果复用 [LivingEntity.canBeAffected](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 及供体覆盖方法 |
| 全身 | `natural_armor` | 天然护甲（…） | 被动 | 供体全部保留体块 | 自动读取基础护甲：僵尸、尸壳、溺尸、僵尸村民、僵尸猪灵 2；凋灵 4；岩浆怪为 Size×3；杀手兔 8 | 按数值最高的前 50% 全身体积加权平均；执行数值精度规则 | 结果作为成品护甲基础值 | 复用 `Attributes.ARMOR`；数值见 [Zombie.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Zombie.java)、[WitherBoss.createAttributes](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java)、[MagmaCube.setSize](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/MagmaCube.java)、[Rabbit.setVariant](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Rabbit.java) |
| 全身 | `passive_regeneration` | 自然恢复（…/秒） | 被动 | 供体全部保留体块 | 悦灵原始值 2.0／秒；凋灵原始值 1.0／秒；马系原始值约 0.0222／秒，按数值精度规则汇总 | 按数值最高的前 50% 全身体积加权平均；执行数值精度规则 | 生命值低于上限时恢复；每 10 tick 回复汇总每秒数值的一半 | 复用 `LivingEntity.heal`；数值见 [Allay.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/allay/Allay.java)、[WitherBoss.customServerAiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java)；以悦灵的 10 tick 节奏统一执行 |
| 全身 | `enchant_vulnerability` | 附魔易伤（亡灵杀手／节肢杀手／穿刺） | 被动 | 全部保留体块 | 自动：对应 `sensitive_to_*` 实体标签 | 亡灵杀手、节肢杀手、穿刺子项各自覆盖率 ≥ 0.5 | 对应武器、箭与三叉戟附魔每级增加 2.5 点伤害；节肢杀手直接命中附加缓慢 IV，时间为 30–[30+10×(等级−1)] tick，按附魔效果定义执行 | 复用附魔注册表、`sensitive_to_*` 标签与 [EnchantmentHelper.modifyDamage / doPostAttackEffects](../ref/1.21.1/Minecraft/net/minecraft/world/item/enchantment/EnchantmentHelper.java)；沿用装配子项映射 |
| 全身 | `ender_teleport` | 末影瞬移 | 技能 | 全部保留体块 | 末影人、潜影贝 | 末影人与潜影贝组织合计 c ≥ 0.5；统一采用末影人模板 | 弹射物命中时最多尝试 64 次 ±32 格瞬移；受到环境等伤害后以 90% 概率瞬移；注视状态下 4 格内随机闪避，远于 16 格时按原 Goal 连续计数后追近；清水药水先结算原版伤害 | 统一采用末影人模板，复刻 [EnderMan.hurt / teleport / teleportTowards / EndermanLookForPlayerGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java)；复用 `LivingEntity.randomTeleport` 与 NeoForge 瞬移事件 |
| 全身 | `magic_resistance` | 魔法抗性 | 被动 | 全部保留体块 | 女巫 | c > 0 | `witch_resistant_to` 伤害 ×(1−0.85c) | 复刻 [Witch.getDamageAfterMagicAbsorb](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Witch.java) 的伤害标签分支；沿用覆盖率系数 |
| 全身 | `wither_shield` | 凋灵护甲 | 被动 | 全部保留体块 | 凋灵 | c ≥ 0.5；生命值 ≤ 50% | 获得凋灵半血护甲对箭、三叉戟和风弹直接伤害的免疫 | 复刻 [WitherBoss.hurt / isPowered](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java) |
| 躯干 | `wingless_flight` | 悬浮飞行 | 技能 | 供体全部保留体块 | 数据：恶魂、凋灵、烈焰人 | c ≥ 0.5 | 采用飞行移动与寻路、悬浮和摔落免疫；保留烈焰人供体的现有完整飞行效果 | 复用 [FlyingMoveControl](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/control/FlyingMoveControl.java)、[FlyingPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/FlyingPathNavigation.java)；沿用现有悬浮飞行规则 |
| 躯干 | `body_bounce` | 身体弹跳 | 技能 | 供体全部保留体块 | 数据：史莱姆、岩浆怪 | c > 0 | 沿用现有装配弹跳：起跳时额外增加 0.1c 纵向速度 | 复刻 [Slime.jumpFromGround](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Slime.java) 的起跳赋值流程；沿用现有 +0.1c 系数 |
| 躯干 | `swim_specialist` | 游泳专长 | 技能 | 供体全部保留体块 | 自动：`WaterAnimal`、`Drowned`，或使用水中／两栖导航、平滑游泳控制的生物；原版供体见反查表 | 供体体块安装在躯干，c ≥ 0.5 | 水中输入 ×1.35；采用平滑游泳控制、两栖寻路与水中漫游，按美西螈中性浮力及 0.9 阻尼移动 | 复用 [SmoothSwimmingMoveControl](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/control/SmoothSwimmingMoveControl.java)、[AmphibiousPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/navigation/AmphibiousPathNavigation.java)；统一复刻 [Axolotl.travel](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/axolotl/Axolotl.java) 的游泳物理 |
| 躯干 | `shell_defense` | 缩壳防御 | 技能 | 壳 `SHELL` + 躯干 `TORSO`：潜影贝 `lid` + `base`；犰狳 `outer_shell` + `inner_body` | 潜影贝、犰狳 | 存在型；躯干内同时保留同种供体的壳与身子 | 由危险记忆触发卷缩，按原版 ROLLING／SCARED／UNROLLING 状态推进、停止移动和恢复；卷缩伤害按 (伤害−1)/2 结算 | 统一采用犰狳模板，复刻 [Armadillo.rollUp / rollOut / hurt / canStayRolledUp](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java) 与 [ArmadilloAi.ArmadilloBallUp](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/ArmadilloAi.java) |
| 躯干 | `knockback_resistance` | 抗击退（…%） | 被动 | 供体全部保留体块 | 自动：抗击退基础值，即铁傀儡 1.0、监守者 1.0、劫掠兽 0.75、疣猪兽 0.6、僵尸疣猪兽 0.6 | 按数值最高的前 50% 躯干体积加权平均；执行数值精度规则 | 成品抗击退 = min(1.0，0.15+汇总值)；供体汇总值保留一位小数，基础抗击退沿用 0.15 | 复用 `Attributes.KNOCKBACK_RESISTANCE` 与 [LivingEntity.knockback](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) |
| 躯干 | `rideable` | 可骑乘 | 技能 | 可装鞍供体的背部体块：原版仅 `body`；模组按名称正则识别，无匹配时回退到点击部件 | 自动：`Saddleable`，即马、驴、骡、骷髅马、僵尸马、骆驼、羊驼、行商羊驼、猪、炽足兽 | 存在型：有效躯干范围内保留任一载体即可，无覆盖率和最小体积门槛 | 仅提供此特性的背部体块可装鞍，空手点击对应体块骑乘，沿用直接操控。供体有背部时头、嘴、腿等其他部件不提供此特性。骆驼背部保留前后双座和冲刺，强健跳跃支持蓄力跳；飞行、游泳按视线升降，跳跃键上升；驯服型由主人骑乘 | 复用 [Saddleable](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Saddleable.java)、[LivingEntity.tickRidden](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；复刻 [AbstractHorse.getRiddenInput / executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java)、[Camel.executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/camel/Camel.java)；复刻 [Camel.getPassengerAttachmentPoint](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/camel/Camel.java) 的前后座次，按所点体块记录座位 |
| 躯干 | `cargo_chest` | 驮运箱 | 被动 | 供体全部保留体块 | 自动：`AbstractChestedHorse`，即驴、骡、羊驼、行商羊驼 | c ≥ 0.5 | 右击对应体块装箱，潜行空手点击该体块开箱；各箱容量相加，分别存物；驴、骡每箱 15 格，羊驼每箱 Strength×3 格；左右箱模型随该体块渲染；潜行持斧拆卸对应空箱，死亡掉落箱子与内容，驯服型由主人操作 | 复用 `SimpleContainer`；复刻 [AbstractChestedHorse.equipChest / setChest / 存档与掉落](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java)、[AbstractHorse.createInventory](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java) 与 [HorseInventoryMenu](../ref/1.21.1/Minecraft/net/minecraft/world/inventory/HorseInventoryMenu.java) 的槽位逻辑；模型见补充 |
| 躯干 | `sonic_boom` | 音波冲击 | 技能 | 躯干 `TORSO` + 左右胸腔：监守者 `body` + `left_ribcage` + `right_ribcage` | 数据：监守者 | 存在型；身子和左右胸腔均安装在躯干；目标处于水平 15 格、垂直 20 格内 | 蓄力 34 tick，动作 60 tick，结束后冷却 40 tick；命中造成 10 点音波伤害；成功伤害后沿射线按水平 2.5、纵向 0.5 及抗击退系数推开目标；近战命中按原方法设置冷却 | 复刻 [SonicBoom.checkExtraStartConditions / start / tick / stop](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/behavior/warden/SonicBoom.java)；复用 `SonicBoom.setCooldown` 与音波伤害源 |
| 躯干 | `self_destruct` | 自爆 | 技能 | 供体全部保留体块 | 数据：苦力怕 | c ≥ 0.5；自主索敌型或主人点燃 | 目标进入 3 格时停止寻路并膨胀；目标保持 7 格内且可见时继续，否则回缩；30 tick 后爆炸，威力 3，闪电充能后为 6；点燃后持续倒计时，爆炸后消亡并生成现有药效云 | 复刻 [SwellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/SwellGoal.java) 和 [Creeper.tick / explodeCreeper / spawnLingeringCloud](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Creeper.java)；沿用主人点燃规则 |
| 躯干 | `milkable` | 可挤奶（奶桶，蘑菇煲） | 被动 | 通用乳房 `UDDER`：牛、哞菇 `udder`；躯干 `TORSO`：牛、哞菇、山羊 `body`，乳房不重复计作身体 | 数据：成年牛、哞菇、山羊 | 存在型；躯干内任意牛或哞菇乳房配任意牛身子可获取奶桶，配任意哞菇身子可获取蘑菇煲，两种身子同时保留可获取两者；成年山羊身子仍可挤奶 | 每次交互填充一个对应的手持空桶或碗；蘑菇煲无需额外蘑菇部件；产量不随部件数量增加；生存模式每份消耗一个对应容器；无冷却并支持机械手；提示只列出实际可获取的类型 | 以牛为挤奶模板，复刻 [Cow.mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Cow.java)；蘑菇煲复刻 [MushroomCow.mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/MushroomCow.java)；复用 `ItemUtils.createFilledResult` |
| 全身 | `wool_shearing` | 可剪毛 | 技能 | 羊身子 `TORSO`：`body`；羊毛 `WOOL`：`wool` | 数据：成年绵羊 | 存在型：至少保留一个成年羊身子，可支持全身任意数量的成年羊毛部件；身子与羊毛可来自不同供体 | 每块羊毛独立剪下 1–3 个对应供体颜色的羊毛，按颜色分别累加；羊毛按拟态生物的被剪毛状态显隐；吃草后统一恢复；支持机械手剪刀 | 复用 [EatBlockGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/EatBlockGoal.java)；复刻 [Sheep.shear / ate / readyForShearing](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Sheep.java)；羊毛材质来自 [SheepFurLayer](../ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/layers/SheepFurLayer.java) |
| 躯干 | `egg_laying` | 产蛋 | 被动 | 躯干 `TORSO`：`body` | 数据：成年鸡 | 存在型；成年鸡 `body` 安装在躯干；供体满足原版产蛋状态 | 每轮 6000–11999 tick 的生产周期，每个有效鸡身子各产 1 个蛋；倒计时随实体保存 | 复刻 [Chicken.aiStep / 存档计时](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Chicken.java) |
| 躯干 | `scute_shedding` | 脱落鳞甲 | 被动 | 壳 `SHELL` + 躯干 `TORSO`：犰狳 `outer_shell` + `inner_body` | 数据：成年犰狳 | 存在型；躯干内至少一个成年犰狳身子支持多个成年犰狳外壳，不要求逐一配对 | 每轮 6000–11999 tick 的生产周期，每个有效外壳各脱落 1 个鳞甲；一次刷取从每个有效外壳各获得 1 个，消耗 16 耐久；倒计时随实体保存，脱落不隐藏外壳 | 复刻 [Armadillo.customServerAiStep / pickNextScuteDropTime / brushOffScute / mobInteract](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java) |
| 头 | `disposition` | 性情（友好／中立／敌对） | 技能 | 头 `HEAD`，包括捕获头部 | 自动：`NeutralMob` 优先为中立，`Enemy` 为敌对，其余友好；`bionic_head/disposition/` 可覆盖 | 保留供体原始头部体积 ≥ 50%，同源碎片合并 | 友好、中立、敌对按供体分类；混合性情取中立；提示与索敌共用结果 | 复用 `NeutralMob`、`Enemy` 分类与 [BionicDispositionRegistry](../src/main/java/com/nobodiiiii/createbiotech/entity/ai/BionicDispositionRegistry.java) 的现有规则 |
| 头 | `intelligence` | 智力（简单／普通／高级） | 技能 | 头 `HEAD`，包括捕获头部 | 自动：`AbstractVillager`、`AbstractPiglin`、`Raider` 及其模组子类高级，其余普通；数据包可覆盖，简单只能由数据包指定 | 保留供体原始头部体积 ≥ 50%，同源碎片合并 | 简单、普通、高级取最高有效等级；默认普通；按等级使用现有攻击选择策略 | 复用 [BionicIntelligenceRegistry](../src/main/java/com/nobodiiiii/createbiotech/entity/ai/BionicIntelligenceRegistry.java) 的现有分类与智力策略 |
| 头 | `no_breathing` | 无需呼吸 | 被动 | 头 `HEAD` | 数据：`#minecraft:undead` | 头部载体覆盖率 ≥ 0.5 | 获得水下与陆地持续呼吸；呼吸提示优先显示本项 | 复用 `UNDEAD` 供体标签与 [LivingEntity.baseTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java) 的呼吸流程 |
| 头 | `water_breathing` | 水下呼吸 | 被动 | 鳃`GILL`：鳕鱼、鲑鱼为 `head`；热带鱼、河豚、鱿鱼、发光鱿鱼、蝌蚪为 `body`；美西螈为 `topGills` / `leftGills` / `rightGills` / `head`；守卫者、远古守卫者、海龟为 `head`；海豚使用头 `HEAD` | 自动读取水下呼吸标签、NeoForge 水中溺水判定和长氧气供体：美西螈、青蛙、守卫者、远古守卫者、海龟、鱿鱼、发光鱿鱼、鳕鱼、鲑鱼、河豚、热带鱼、蝌蚪、海豚 | 头部安装范围内有对应载体 | 获得完整水下呼吸；海豚供体沿用现有完整水下呼吸转换 | 复用 `CAN_BREATHE_UNDER_WATER`、NeoForge `canDrownInFluidType` 与 [LivingEntity.getMaxAirSupply](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；沿用现有头部能力转换 |
| 头 | `dry_suffocation` | 离水窒息（负面） | 被动 | 鳃`GILL`：鳕鱼、鲑鱼为 `head`；其余为 `body` | 数据：鳕鱼、鲑鱼、热带鱼、河豚、鱿鱼、发光鱿鱼、蝌蚪 | 头部载体覆盖率 ≥ 0.5；采用水生呼吸模式 | 离水时按鱼类氧气计数：初始 300 tick，计数至 -20 时受到 2 点溺水伤害并重置为 0；水或气泡使氧气补满 | 统一采用鱼类模板，复刻 [WaterAnimal.handleAirSupply](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/WaterAnimal.java) 的 300 tick 初始氧气与 -20 tick 伤害周期 |
| 头 | `tameable` | 可驯服 | 技能 | 头`HEAD`：`head`、`head_cube` | 自动：`TamableAnimal`；马、驴、骡、羊驼、行商羊驼；数据补充：狐狸、豹猫、悦灵 | 头部安装范围内有对应载体 | 喂史莱姆球获得主人；主人空手切换坐下，超过 10 格开始跟随、近于 2 格停止，远距离按跟随 Goal 尝试回到主人身边；协助主人战斗；分裂史莱姆继承主人 | 沿用史莱姆球驯服与主人继承；复刻 [FollowOwnerGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java)、[SitWhenOrderedToGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java) 与 [TamableAnimal.tryToTeleportToOwner](../ref/1.21.1/Minecraft/net/minecraft/world/entity/TamableAnimal.java) 的行为流程 |
| 头 | `vibration_sense` | 振动感知 | 技能 | 头部主体 `HEAD_CORE` + 触须 `TENDRIL`：监守者 `head` + `left_tendril`／`right_tendril` | 监守者 | 存在型；同一头部链内保留监守者头部主体和至少一根触须；正在追踪目标 | 监听 12 格内已有目标的游戏振动，按潜行与羊毛隔振规则接收；追踪最近声源位置并保留 40 tick 记忆 | 复用 [VibrationSystem.Listener / Ticker / User](../ref/1.21.1/Minecraft/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java) 与现有 [目标跟踪接入](../src/main/java/com/nobodiiiii/createbiotech/entity/trait/BionicVibrationSense.java) |
| 头 | `water_aversion` | 避水偏好 | 技能 | 头 `HEAD`：`head`、`head_cube` | 末影人 | 头部安装范围内有对应载体 | 水域节点代价设为 -1.0，按末影人原版避水规则选择路线 | 复用 [Mob.setPathfindingMalus](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)；水域代价取 [EnderMan 构造器](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/EnderMan.java) |
| 头 | `deterrence` | 生物威慑 | 技能 | 头`HEAD`：`head`、`head_cube` | 猫、豹猫、狼、北极熊、羊驼、行商羊驼、守卫者、远古守卫者、犰狳；村民/行商害怕的敌对生物、僵尸猪灵（见下方关系表） | 头部安装范围内有对应载体 | 按现有威慑关系适配目标生物的避让 Goal／Brain；距离、速度、概率和记忆时长采用关系表模板 | 复用 [AvoidEntityGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java)；Brain 生物沿用 [VillagerHostilesSensor](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/sensing/VillagerHostilesSensor.java)、[PiglinAi.avoidZombified](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinAi.java)；按关系表适配威胁身份 |
| 头 | `hunting_instinct` | 捕猎本能（目标生物…） | 技能 | 头 `HEAD`，包括捕获头部 | 见本表“捕猎关系” | 头部安装范围内有对应载体；目标满足捕猎关系表 | 按供体目标谓词、距离、亮度、驯服状态和冷却主动捕猎；沿用主人与盟友关系 | 复用原版目标 Goal、[AxolotlAttackablesSensor](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/sensing/AxolotlAttackablesSensor.java) 与 [PiglinAi](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/piglin/PiglinAi.java) 的目标谓词；复刻供体约束，见捕猎关系表 |
| 头 | `pack_alert` | 呼唤同伴 | 技能 | 头 `HEAD` | 狼、蜜蜂、海豚、烈焰人、末影螨、蠹虫、僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、潜影贝、唤魔者、幻术师、掠夺者、劫掠兽、恼鬼、卫道士、猪灵、疣猪兽 | 头部安装范围内有对应载体；进入原版受伤报复流程；统一采用狼模板 | 按原版伤害时间戳触发，通知跟随范围与纵向 ±10 格内符合关系的同伴，沿用原方法的目标与盟友判定；同伴身份映射到供体与主人关系 | 统一采用狼的受伤呼援模板，复用 `HurtByTargetGoal.setAlertOthers / alertOther`；供体入口见 [Wolf.registerGoals](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Wolf.java)；同伴身份沿用供体与主人关系 |
| 头 | `roar` | 咆哮 | 技能 | 头部主体 `HEAD_CORE` + 嘴 `MOUTH`：劫掠兽 `head_core` + `mouth` | 数据：劫掠兽 | 存在型；同一头部链内保留劫掠兽头部主体和嘴；角不提供此能力 | 50% 概率眩晕 40 tick，随后进入 20 tick 咆哮阶段，在剩余 10 tick 时结算；4 格范围内按劫掠兽的伤害与击退筛选造成 6 点伤害和强力击退，另一分支直接强力推开格挡者 | 复刻 [Ravager.blockedByShield / aiStep / roar / strongKnockback](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ravager.java) |
| 头 | `head_projectile` | 头部投射 | 技能 | 头`HEAD`：各供体的头部件，见子表 4 | 数据：羊驼、行商羊驼、雪傀儡、烈焰人、恶魂、潜影贝、凋灵、末影龙、旋风人 | 头部安装范围内有对应载体；按智力选择现有投射子项 | 从头部载体质心发射对应原版弹射物，目标选择、预热、射速、连发、散布和冷却采用投射子表；沿用主人、盟友、爆炸与引火规则；龙息云可装瓶 | 复用 [RangedAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/RangedAttackGoal.java) 与原版弹射物实体；按投射子表复刻供体发射方法 |
| 头 | `guardian_beam` | 守卫者光束 | 技能 | 眼`EYE`：守卫者、远古守卫者的 `eye` | 数据：守卫者、远古守卫者 | 头部安装范围内有眼载体；目标可见，普通守卫者距离 >3 格 | 停止寻路并注视目标，预热 10 tick，普通照射 80 tick、远古 60 tick；失去视线时清除攻击目标；结算基础魔法伤害 1（困难 +2、远古 +2）及原版近战伤害 6／8 | 复刻 [Guardian.GuardianAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java) 与 [ElderGuardian.getAttackDuration](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/ElderGuardian.java) |
| 臂 | `wing_flight` | 扑翼飞行 | 技能 | 左翅`LEFT_WING` 或右翅 `RIGHT_WING`：`leftWing` / `left_wing` / `leftWingBase` / `leftWingTip` 等及其右侧对应部件 | 蜜蜂、鹦鹉、蝙蝠、幻翼、悦灵、恼鬼、末影龙 | 翅膀安装为臂；每臂 c ≥ 0.5；有效翼 ≥2；身体体积 ≤16×有效翼载体体积 | 单臂翼覆盖率达标后作为翅膀；飞行按有效翼数与承重判定，播放供体飞行动画并提供摔落免疫；臂腿行走由装配关节动作驱动，攻击臂集合由普通手臂组成 | 复用飞行控制器、导航与供体 `setupAnim`；模型依据见 [BeeModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/BeeModel.java)、[BatModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/BatModel.java)；装配动作见补充 |
| 臂 | `effect_attack` | 附效攻击（中毒／凋零／饥饿） | 被动 | 中毒：口器 `MOUTH` 或刺 `SPINE`；凋零、饥饿：攻击手 `ATTACK_HAND` | 洞穴蜘蛛、蜜蜂、河豚：中毒；凋灵骷髅：凋零；尸壳：饥饿 | 对应口器、刺或攻击手装为臂，并由该臂命中；饥饿采用空手命中 | 中毒统一采用蜜蜂模板：普通 200 tick、困难 360 tick；凋零 I 200 tick；饥饿 I 140×局部有效难度整数 tick；每效果保留现有概率、等级与载体参数 | 复用 `LivingEntity.addEffect`；中毒统一取 [Bee.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Bee.java)，凋零取 [WitherSkeleton.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/WitherSkeleton.java)，饥饿取 [Husk.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Husk.java) |
| 臂 | `attack_knockback` | 击退强化（数值） | 被动 | 全部保留体块 | 自动读取基础攻击击退：疣猪兽、僵尸疣猪兽 1.0；劫掠兽、监守者 1.5 | 每条臂按数值最高的前 50% 体积加权平均；执行数值精度规则 | 使用出手臂的值；原版近战额外击退强度为值×0.5；提示显示最高臂值 | 复用 `Attributes.ATTACK_KNOCKBACK` 与 [Mob.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java) 的额外击退流程 |
| 臂 | `door_handling` | 破门 | 技能 | 对应供体全部保留体块，装配范围 `ARM` | 僵尸、尸壳、僵尸村民、僵尸猪灵 | 对应供体体块安装为臂，载体存在；困难难度且采用地面寻路 | 对路径中的关闭木门持续破坏 240 tick，采用原版破门进度与事件流程 | 直接复用 [BreakDoorGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java) 与 [DoorInteractGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java)；载体取安装为手臂的供体体块 |
| 臂 | `wing_slow_fall` | 振翅缓降 | 技能 | 左右翅 `LEFT_WING` / `RIGHT_WING` | 鸡、鹦鹉 | 翅膀安装为臂；每臂 c ≥ 0.5；有效翼 ≥2 | 下降速度每 tick ×0.6并获得摔落免疫；翼臂播放供体振翅动作；飞行提示优先于缓降提示 | 以鸡为模板，复刻 [Chicken.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Chicken.java) 的下降 ×0.6；复用供体翅膀动画 |
| 臂 | `launch_strike` | 近战击飞 | 被动 | 铁傀儡攻击手、末影龙翼、山羊／成年疣猪兽／成年僵尸疣猪兽的角 `HORN` | 铁傀儡、末影龙、山羊、成年疣猪兽、成年僵尸疣猪兽 | 对应载体装为臂，并由该臂成功命中；统一采用铁傀儡模板 | 目标纵向速度增加 0.4×max(0，1−抗击退)，每次命中执行一次 | 统一采用铁傀儡模板，复刻 [IronGolem.doHurtTarget](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/IronGolem.java) 中命中成功后的纵向击飞段 |
| 臂 | `loot_pickup` | 拾取装备 | 技能 | 攻击手`ATTACK_HAND`：`right_arm`、`left_arm` | 数据：僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、骷髅、流浪者、沼骸、凋灵骷髅、猪灵 | 对应攻击手载体装为臂；处于可拾取状态 | 按 `Mob.aiStep` 的拾取时机检查邻近掉落物，复用原版装备优劣判定，将更好的近战武器交给对应攻击臂；主人可潜行右击交接武器 | 复用 [Mob.aiStep / pickUpItem / equipItemIfPossible / canReplaceCurrentItem](../ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java)；沿用攻击臂接收武器与主人交接 |
| 臂 | `spellcasting` | 施法 | 技能 | 攻击手`ATTACK_HAND`：唤魔者的 `arms`、`right_arm`、`left_arm`；女巫的 `arms` | 数据：唤魔者、女巫 | 对应攻击手载体装为臂；按智力选择现有施法子项 | 尖牙：预热 20 tick、施法姿态 40 tick、从施法开始计 100 tick 再次可用，近处双环、远处 16 个；药水：10 格射程、60 tick 间隔，按女巫方法选择药水与弹道 | 复用 `RangedAttackGoal`、`ThrownPotion`、`EvokerFangs`；复刻 [SpellcasterUseSpellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/SpellcasterIllager.java)、[EvokerAttackSpellGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Evoker.java) 和 [Witch.performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Witch.java) |
| 腿 | `agile_landing` | 摔落免疫 | 被动 | 腿`LEG`：四肢部件名（`leftHindLeg` / `rightFrontLeg` 等） | 自动：猫、豹猫（且属于`fall_damage_immune`） | 每条腿 c ≥ 0.5；有效腿 ≥2 | 获得摔落伤害免疫；与全身摔落免疫共用中文名，提示按生效部位归组 | 复用 `FALL_DAMAGE_IMMUNE` 标签与 [LivingEntity.causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)；沿用有效腿数判定 |
| 腿 | `fall_reduction` | 摔落抵抗 | 被动 | 腿`LEG`：四肢部件名 | 马、驴、骡、羊驼、行商羊驼、山羊 | 每条腿 c ≥ 0.5；有效腿 ≥1；统一采用马模板 | 安全摔落距离 6 格，摔落伤害倍率 0.5；保留现有参数配置入口 | 以马为模板，复用 [AbstractHorse.createBaseHorseAttributes / causeFallDamage](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java) 的 `SAFE_FALL_DISTANCE=6` 与倍率 0.5 |
| 腿 | `powder_snow_walk` | 细雪行者 | 被动 | 足`FOOT`：兔、狐狸为四肢部件名；蠹虫、末影螨为 `body` / `segment0` / `segment_0` | 自动：标签`powder_snow_walkable_mobs`，即兔、狐狸、蠹虫、末影螨 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 在细雪表面行走 | 复用 [PowderSnowBlock.canEntityWalkOnPowderSnow](../ref/1.21.1/Minecraft/net/minecraft/world/level/block/PowderSnowBlock.java) 与现有足部接入 |
| 腿 | `lava_walk` | 熔岩行者 | 技能 | 足`FOOT`：`leftLeg` / `rightLeg` / `left_leg` / `right_leg` | 炽足兽 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 采用炽足兽的熔岩节点、半格表面支撑、浸没上浮与熔岩内摔落距离重置 | 复用地面导航；复刻 [Strider.floatStrider / canStandOnFluid / StriderPathNavigation](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Strider.java) |
| 腿 | `wall_climb` | 攀墙 | 技能 | 腿`LEG`：蜘蛛的 8 条腿部件 | 蜘蛛、洞穴蜘蛛 | 每条腿 c ≥ 0.5；有效腿 ≥2 | 水平碰撞时进入原版攀爬移动流程 | 复刻 [Spider.tick / onClimbable / setClimbing](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Spider.java)；复用原版攀爬移动流程 |
| 腿 | `strong_jump` | 强健跳跃 | 技能 | 腿 `LEG`；兔后腿和后足 | 马、骷髅马、僵尸马、兔、山羊、青蛙 | 每条腿 c ≥ 0.5；有效腿 ≥2 | 沿用现有起跳速度 +0.2、两格高台寻路与普通迈步高度；骑乘时蓄力倍率 0.4–1.0 | 复用 [LivingEntity.jumpFromGround](../ref/1.21.1/Minecraft/net/minecraft/world/entity/LivingEntity.java)、[AbstractHorse.onPlayerJump / executeRidersJump](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/AbstractHorse.java)；沿用现有 +0.2 与两格寻路参数 |
| 腿 | `snow_trail` | 积雪足迹 | 被动 | 足`FOOT`：雪傀儡的 `lower_body` | 数据：雪傀儡 | 每条腿 c ≥ 0.5；有效腿 ≥1 | 在脚下四角可放置位置留下雪片；沿用现有全气候积雪规则与生物破坏事件 | 复刻 [SnowGolem.aiStep](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) 的四点放雪循环；沿用现有气候参数 |
| 腿 | `pollination` | 授粉催熟 | 技能 | 蜜蜂躯干 `TORSO`：`body`；真实腿部 `LEG`：`front_legs`、`middle_legs`、`back_legs` | 数据：蜜蜂 | 存在型；躯干内一个蜜蜂身子可支持任意数量的有效蜜蜂腿部 | 按蜜蜂 Goal 寻找 5 格内有效花，成功采粉超过 400 tick 后携粉；每个有效的真实蜜蜂腿部件独立按原版随机触发规则促生脚下 1–2 格作物，每次携粉各提供最多 10 次促生额度 | 复刻 [Bee.BeePollinateGoal / BeeGrowCropGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/Bee.java) |

## 三、技能与交互补充

### 1. 瞬移、缩壳与动作

末影瞬移统一采用末影人流程：随机落点先检查实体支撑与水域条件，复用 `LivingEntity.randomTeleport`，通过 NeoForge `EventHooks.onEnderTeleport` 处理目标位置。追近使用目标 Goal 内持续累计的计数器；注视状态、骑乘状态及目标距离沿用该 Goal。原潜影贝供体与末影人供体共同贡献本项覆盖率。

缩壳防御统一采用犰狳流程：危险感知写入 `DANGER_DETECTED_RECENTLY`，`ArmadilloBallUp` 驱动卷缩、探头与展开。卷缩维持条件使用 `canStayRolledUp`；原有主人坐下、乘骑等装配状态接入该条件。伤害使用犰狳的 `(amount−1)/2` 分支，后续由原版伤害流程结算。

存在型组合的必要部件作为共享前提，不按供体或部件一一消耗。可挤奶的乳房跨牛、哞菇通用，由身体类型决定产物；其他组合按同种身子支持多个产出部件。可剪毛检查全身保留部件，其余能力沿用各自安装部位；同种供体的部件可共同提供躯干能力；头部能力按每条头部链分别检查，两个不完整的头部不能互补。劫掠兽将头部大体块标记为 `head_core`，鼻部与角不代替头部主体。牛和哞菇的乳房单独标记为 `udder`；犰狳的同部位外壳和身子分别标记为 `outer_shell`、`inner_body`。标记依据原版 [CowModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/CowModel.java) 和 [ArmadilloModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ArmadilloModel.java)。

供体外观随拟态生物自身状态变化：犰狳卷缩和潜影贝开合读取自身缩壳状态；监守者胸腔读取自身音波蓄力状态；蜜蜂携粉纹理读取自身采粉状态。状态变化保留手术体块编号及切除结果，不能凭动画补回已切掉的部件。

扑翼飞行与振翅缓降中单臂翼覆盖率达标的翼臂由翅膀集合管理，飞行能力另按数量与承重判定；普通手臂构成攻击臂集合。臂腿行走使用装配关节动作，翅膀播放供体飞行动画。动画优先复用捕获供体的模型 `setupAnim` 结果，按原模型层级传递到保留翅膀部件，关节变换继续使用装配坐标。

### 2. 驮运箱与骑乘

驮运箱复用原版左右箱模型部件。驴、骡取 [ChestedHorseModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/ChestedHorseModel.java) 的 `left_chest`、`right_chest`；羊驼取 [LlamaModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/LlamaModel.java) 的对应部件。每个装备箱子的体块各绑定一组原版左右箱模型，随该体块的装配位置和旋转同步显示；鞍同样逐体块绑定。

容器采用原版 `SimpleContainer`。装备箱子、调整容量、保留原物品、存档与死亡掉落复刻 `AbstractChestedHorse`／`AbstractHorse` 原方法；菜单按 `HorseInventoryMenu` 的槽位、距离、容器监听与快捷移动逻辑适配成品。每个驴、骡体块的背箱提供 15 格，每个羊驼体块的背箱按该供体 Strength 提供 3–15 格；已安装背箱的容量相加，各箱独立存物。右击对应体块装箱，潜行空手点击该体块开箱，潜行持斧点击该体块拆下空箱；权限沿用主人规则。

每个鞍物品装备到一个背部体块，空手点击已装鞍的体块即从该体块上座。原版可装鞍供体统一只接受模型标签 `body`，依据 [HorseModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/HorseModel.java)、猪继承的 [QuadrupedModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/QuadrupedModel.java)、[CamelModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/CamelModel.java)、[StriderModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/StriderModel.java) 和 [LlamaModel](../ref/1.21.1/Minecraft/net/minecraft/client/model/LlamaModel.java) 的统一躯干命名。非原版供体对部件名最后一段做不区分大小写的正则匹配：`body`、`torso`、`back`、`trunk`、`chest`，可带上/下/主/前/后等英文前后缀及数字；`body/head`、`leftChest`、`body_saddle` 不匹配。原始供体模型完全没有匹配项时才允许点击任意保留体块装鞍；已识别的背部被切掉后不回退到头或腿。可骑乘为存在型特性，载体选择与装鞍规则共用同一实现：只要在有效躯干范围保留一个载体即可获得，头、嘴、腿等非背部不提供此特性；不要求供体组织占比或身体体积达标。

普通背部体块各一座，骆驼背部沿用原版前后双座；各鞍分别记录乘客，首位乘客操控身体。潜行持剪刀点击对应体块卸鞍，该鞍的乘客下座。旧存档中装在无效部件的鞍会让乘客下座并掉落返还。骑乘沿用主人规则，所有供体保持直接操控。通用入口复用 `Saddleable` 与 `LivingEntity` 骑乘流程；马式蓄力跳和骆驼冲刺复刻各自原方法。飞行、游泳使用现有视线与跳跃键升降。

### 3. 头部投射子项

各子项保留现有弹射物种类，具体发射流程按对应原方法选用。

| 供体／子项 | 行为模板与参数 | 方法依据 |
| --- | --- | --- |
| 羊驼、行商羊驼／唾沫 | `RangedAttackGoal`，20 格、40 tick 间隔；保留原版弹道、散布和吐击状态 | [Llama.registerGoals / performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/horse/Llama.java) |
| 雪傀儡／雪球 | `RangedAttackGoal`，10 格、20 tick 间隔；初速 1.6、散布 12 | [SnowGolem.performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/SnowGolem.java) |
| 烈焰人／小火球 | 供体跟随范围内充能 60 tick，三连发间隔 6 tick，之后冷却 100 tick；近身分支沿用原 Goal | [BlazeAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Blaze.java) |
| 恶魂／大火球 | 64 格内且可见时累积蓄力，10 tick 播放准备音，20 tick 发射后计数设为 -40；视线变化按原分支回退计数 | [GhastShootFireballGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Ghast.java) |
| 潜影贝／潜影弹 | 首次等待 20 tick；20 格内发射，后续间隔 `20+random(0..9)×10` tick；弹道复用 `ShulkerBullet` | [ShulkerAttackGoal](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java) |
| 凋灵／凋灵之首 | 统一采用中央头的 `RangedAttackGoal`：20 格、40 tick 间隔，保留原版危险弹概率 | [WitherBoss.customServerAiStep / performRangedAttack](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/wither/WitherBoss.java) |
| 末影龙／龙息火球 | 64 格内可见目标累积至少 5 tick，水平朝向夹角小于 10°时发射；每轮发射一枚，沿用现有 80 tick 轮次间隔 | [DragonStrafePlayerPhase.doServerTick](../ref/1.21.1/Minecraft/net/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase.java) |
| 旋风人／风弹 | 距离大于 2 且小于 16 格，满足原 Brain 射击记忆与站姿；充能 15 tick、恢复 4 tick、结束后冷却 10 tick；按原方法检查朝向与散布 | [breeze.Shoot](../ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/breeze/Shoot.java) |

音波冲击使用 `SonicBoom` 的目标、距离与记忆条件，方向击退在伤害成功后执行。守卫者光束使用原 Goal 的停止寻路、视线中断、伤害结算及目标清理流程。

尖牙施法采用 `SpellcasterUseSpellGoal` 的预热、施法姿态与再次施法时间三套计数。女巫药水按 `performRangedAttack` 顺序选择：8 格外已有目标满足条件时缓慢，生命至少 8 点时中毒，3 格内以 25% 概率虚弱，其余伤害；已有药效判断沿用原方法。投射物、药水和药效云复用原版实体与效果应用入口，主人及盟友关系沿用现有过滤。

### 4. 捕猎关系

捕猎关系保留现有供体与目标。通用目标 Goal 接收现有目标谓词；供体专属 Sensor／Brain 按原条件复刻，目标进入视线、有效攻击范围、环境与冷却后参与原版选取。

| 供体头 | 目标与条件 | 驯服后的规则 | 本地依据 |
| --- | --- | --- | --- |
| 狼 | 羊、兔、狐狸、陆上幼年海龟；骷髅、流浪者、凋灵骷髅、沼骸 | 捕猎骷髅类 | `animal/Wolf.java` 的目标 Goal 与 `PREY_SELECTOR` |
| 猫 | 兔、陆上幼年海龟 | 由主人战斗目标驱动 | `animal/Cat.java` 的目标 Goal |
| 豹猫 | 鸡、陆上幼年海龟 | 保留 | `animal/Ocelot.java` 的目标 Goal |
| 狐狸 | 鸡、兔、陆上幼年海龟、鳕鱼、鲑鱼、热带鱼 | 保留 | `animal/Fox.java` 的陆地／鱼类目标 Goal |
| 北极熊 | 狐狸 | 保留 | `animal/PolarBear.java` 的目标 Goal |
| 美西螈 | 水中 8 格内的溺尸、守卫者、远古守卫者及现有鱼类、鱿鱼、发光鱿鱼、蝌蚪；普通猎物战斗后冷却 2400 tick | 保留 | `ai/sensing/AxolotlAttackablesSensor.java`、`animal/axolotl/AxolotlAi.java` |
| 铁傀儡 | `Enemy` 目标按铁傀儡原版目标谓词选取 | 保留 | `animal/IronGolem.java` |
| 雪傀儡 | `Enemy` 目标 | 保留 | `animal/SnowGolem.java` |
| 守卫者、远古守卫者 | 3 格外的鱿鱼、发光鱿鱼、美西螈 | 保留 | `monster/Guardian.java` 的 `GuardianAttackSelector` |
| 末影人 | 末影螨 | 保留 | `monster/EnderMan.java` |
| 蜘蛛、洞穴蜘蛛 | 亮度低于 0.5 时选择铁傀儡 | 保留 | `monster/Spider.java` 的 `SpiderTargetGoal` |
| 史莱姆、岩浆怪 | 铁傀儡 | 保留 | `monster/Slime.java` |
| 猪灵 | 凋灵骷髅、凋灵；成年疣猪兽；猎杀冷却 30–120 秒 | 保留 | `monster/piglin/PiglinAi.java`、`ai/sensing/PiglinSpecificSensor.java` |
| 猪灵蛮兵 | 凋灵骷髅、凋灵 | 保留 | `monster/piglin/PiglinBruteAi.java`、`ai/sensing/PiglinBruteSpecificSensor.java` |
| 僵尸、尸壳、僵尸村民 | 村民、行商、铁傀儡、陆上幼年海龟 | 保留 | `monster/Zombie.java` |
| 溺尸 | 村民、行商、铁傀儡、美西螈、陆上幼年海龟 | 保留 | `monster/Drowned.java` |
| 骷髅、流浪者、沼骸 | 铁傀儡、陆上幼年海龟 | 保留 | `monster/AbstractSkeleton.java` |
| 凋灵骷髅 | 铁傀儡、陆上幼年海龟、猪灵、猪灵蛮兵 | 保留 | `monster/WitherSkeleton.java` |
| 卫道士、掠夺者、唤魔者、幻术师 | 村民、行商、铁傀儡 | 保留 | `monster/{Vindicator,Pillager,Evoker,Illusioner}.java` |
| 僵尸疣猪兽 | 按 `Zoglin` 原版目标谓词选择可攻击生物 | 保留 | `monster/Zoglin.java` |
| 旋风人 | 铁傀儡 | 保留 | `ai/sensing/BreezeAttackEntitySensor.java` |
| 凋灵 | 按 `WITHER_FRIENDS` 标签选择目标 | 保留 | `boss/wither/WitherBoss.java` |
| 羊驼、行商羊驼 | 10 格内的野生狼 | 保留 | `animal/horse/Llama.java` 的 `LlamaAttackWolfGoal` |

表内相对源码路径的根目录均为 `ref/1.21.1/Minecraft/net/minecraft/world/entity/`。

### 5. 生物威慑关系

保留现有关系，避让行为采用目标生物原有模板。猫与豹猫对幻翼的既有共同关系统一使用猫模板。

| 供体 | 被驱散生物 | 原版行为模板 |
| --- | --- | --- |
| 猫、豹猫 | 苦力怕 | `Creeper` 的 `AvoidEntityGoal`：6 格，速度 1.0／1.2 |
| 猫、豹猫 | 幻翼 | `PhantomSweepAttackGoal`：每 20 tick 检查 16 格内威胁并结束俯冲 |
| 狼 | 骷髅、流浪者、凋灵骷髅、沼骸 | `AbstractSkeleton` 的 `AvoidEntityGoal`：6 格，速度 1.0／1.2 |
| 狼 | 普通兔、狐狸 | 兔采用 `RabbitAvoidEntityGoal` 的 10 格与速度 2.2；狐狸采用 `Fox` 原避让距离和速度 |
| 北极熊 | 狐狸 | `Fox` 的北极熊避让 Goal |
| 羊驼、行商羊驼 | 野生狼 | `WolfAvoidEntityGoal`：24 格，速度 1.5；按 `Strength ≥ random(0..4)` 触发 |
| 守卫者、远古守卫者 | 海豚 | `Dolphin` 的 `AvoidEntityGoal`：8 格，速度 1.0 |
| 犰狳 | 蜘蛛、洞穴蜘蛛 | `Spider` 的犰狳避让 Goal，按原距离与速度执行 |
| 僵尸、尸壳、溺尸、僵尸村民、唤魔者、幻术师、掠夺者、劫掠兽、恼鬼、卫道士、僵尸疣猪兽 | 村民 | `VillagerHostilesSensor` 各物种距离 8–15 格与 `NEAREST_HOSTILE` 记忆 |
| 现有行商威胁供体：僵尸类、僵尸猪灵、唤魔者、幻术师、掠夺者、恼鬼、卫道士、僵尸疣猪兽 | 行商 | 采用 `WanderingTrader` 对应威胁的原避让 Goal 参数；僵尸猪灵沿用僵尸模板 |
| 僵尸猪灵、僵尸疣猪兽 | 猪灵 | `PiglinAi.avoidZombified` 与原版避让记忆、期望距离 |

依据位于同一原版根目录下的 `monster/{Creeper,Phantom,AbstractSkeleton,Spider}.java`、`animal/{Rabbit,Fox,Wolf,Dolphin}.java`、`npc/WanderingTrader.java`、`ai/sensing/VillagerHostilesSensor.java` 与 `monster/piglin/PiglinAi.java`。

### 6. 呼唤同伴与生产

呼唤同伴统一采用狼的受伤呼援，直接复用 `HurtByTargetGoal.setAlertOthers`，按原方法的伤害时间戳、跟随范围、纵向 ±10 格、同伴当前目标与关系条件触发。混合装配的同伴身份按现有供体集合与主人关系映射；坐下状态沿用主人命令。

授粉载体采用蜜蜂全部保留体块，装配生效范围继续为腿。采粉复刻 `BeePollinateGoal` 的有效花检查、5 格搜索、雨天与战斗状态、悬停累计、400 tick 成功时间、结束判定和再次寻花冷却。促生复刻 `BeeGrowCropGoal` 的随机启动、随机 tick 触发、`BEE_GROWABLES`、作物年龄推进与 10 次计数；启动条件使用现有携粉状态、采粉次数和生物破坏规则。

可剪毛为存在型，只需全身任意一个成年羊身子，即可支持所有保留的成年羊毛部件。每块羊毛独立产出 1–3 个自身供体颜色的羊毛，按颜色累加；支撑用身子不计入产量。采集时以原版 `sheep_fur.png` 材质为羊毛体块补充 `wool` 标签，与基础模型的 `body` 标签分别判断；羊毛隐藏不会删除载体或改变装配。羊毛显隐读取拟态生物同步的被剪毛状态，复用 `EatBlockGoal` 的吃草动作和 `ate()` 回调恢复羊毛。产蛋与自然脱鳞甲按各自原始随机区间计时，每轮分别按有效鸡身子与犰狳外壳数量产出，并随存档与纸箱捕获保存。牛和哞菇的乳房不区分来源和产出类型，可配合任意一个牛身子获取奶桶，或任意一个哞菇身子获取蘑菇煲，两种身子同时存在即可获取两者；均沿用躯干安装范围，且乳房不会同时充当身体。挤奶与蘑菇煲每次交互只填充一个手持容器，不按部件数量增产；蘑菇煲无需额外蘑菇部件。创造模式沿用原版结果物去重规则。完整捕获的原版生物按其原生交互显示类型，完整哞菇仍可提供原版的牛奶与蘑菇煲。供体颜色、羊驼 Strength、装备、物品栏与生产状态继续随供体档案保存。机械手沿用现有挤奶、蘑菇煲与剪毛交互入口。

已驯服的仿生生物、继承主人的史莱姆及原版可驯服生物，沿用瓶装活性史莱姆恢复 4 点生命、桶装恢复 16 点生命。生命低于上限时消耗一份并返还玻璃瓶或桶，创造模式保留物品。分裂史莱姆保留主人、跟随、停留和后续分裂继承。


## 四、按生物反查

性情、智力按头部通用规则显示。下表汇总 v3 其余特性；具体载体、部位与门槛见总表。数值效果执行一位小数与 0.1 生效规则。

| 生物 | 全身 | 躯干 | 头 | 臂 | 腿 |
| --- | --- | --- | --- | --- | --- |
| 美西螈 | 保湿依赖、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、捕猎本能（目标生物…） | — | — |
| 蝙蝠 | — | — | — | 扑翼飞行 | — |
| 蜜蜂 | 附魔易伤（节肢杀手） | — | 呼唤同伴 | 扑翼飞行、附效攻击（中毒） | 授粉催熟 |
| 烈焰人 | 火焰免疫／火焰抗性、遇水受伤、冻结易伤 | 悬浮飞行 | 呼唤同伴、头部投射 | — | — |
| 沼骸 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 旋风人 | 摔落免疫、弹射物偏转 | — | 捕猎本能（目标生物…）、头部投射 | — | — |
| 骆驼 | — | 可骑乘 | — | — | — |
| 猫 | — | — | 可驯服、生物威慑、捕猎本能（目标生物…） | — | 摔落免疫 |
| 洞穴蜘蛛 | 无视蛛网、免疫效果（中毒）、附魔易伤（节肢杀手） | — | 捕猎本能（目标生物…） | 附效攻击（中毒） | 攀墙 |
| 鳕鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 鲑鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 热带鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 海豚 | 保湿依赖、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、呼唤同伴 | — | — |
| 驴 | — | 可骑乘、驮运箱 | 可驯服 | — | 摔落抵抗 |
| 骡 | — | 可骑乘、驮运箱 | 可驯服 | — | 摔落抵抗 |
| 马 | — | 可骑乘 | 可驯服 | — | 摔落抵抗、强健跳跃 |
| 溺尸 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | 游泳专长 | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 拾取装备 | — |
| 尸壳 | 治疗反转、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 附效攻击（饥饿）、破门、拾取装备 | — |
| 僵尸 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 破门、拾取装备 | — |
| 僵尸村民 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 破门、拾取装备 | — |
| 远古守卫者 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、生物威慑、捕猎本能（目标生物…）、守卫者光束 | — | — |
| 守卫者 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、生物威慑、捕猎本能（目标生物…）、守卫者光束 | — | — |
| 末影龙 | 火焰免疫／火焰抗性 | — | 头部投射 | 扑翼飞行、近战击飞 | — |
| 末影人 | 遇水受伤、末影瞬移 | — | 避水偏好、捕猎本能（目标生物…） | — | — |
| 末影螨 | 附魔易伤（节肢杀手） | — | 呼唤同伴 | — | 细雪行者 |
| 蠹虫 | 免疫效果（寄生）、附魔易伤（节肢杀手） | — | 呼唤同伴 | — | 细雪行者 |
| 狐狸 | — | — | 可驯服、捕猎本能（目标生物…） | — | 细雪行者 |
| 青蛙 | — | 游泳专长 | 水下呼吸 | — | 强健跳跃 |
| 恶魂 | 火焰免疫／火焰抗性 | 悬浮飞行 | 头部投射 | — | — |
| 山羊 | — | 可挤奶 | — | 近战击飞 | 摔落抵抗、强健跳跃 |
| 河豚 | 接触反伤、附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | 附效攻击（中毒） | — |
| 鱿鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 发光鱿鱼 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 疣猪兽 | — | 抗击退（…%） | 呼唤同伴 | 击退强化（数值）、近战击飞 | — |
| 铁傀儡 | 摔落免疫 | 抗击退（…%） | 捕猎本能（目标生物…） | 近战击飞 | — |
| 羊驼 | — | 可骑乘、驮运箱 | 可驯服、生物威慑、捕猎本能（目标生物…）、头部投射 | — | 摔落抵抗 |
| 行商羊驼 | — | 可骑乘、驮运箱 | 可驯服、生物威慑、捕猎本能（目标生物…）、头部投射 | — | 摔落抵抗 |
| 岩浆怪 | 火焰免疫／火焰抗性、冻结易伤、摔落免疫、天然护甲（…） | 身体弹跳 | 捕猎本能（目标生物…） | — | — |
| 豹猫 | — | — | 可驯服、生物威慑、捕猎本能（目标生物…） | — | 摔落免疫 |
| 鹦鹉 | — | — | 可驯服 | 扑翼飞行、振翅缓降 | — |
| 幻翼 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸 | 扑翼飞行 | — |
| 北极熊 | 冻结免疫／冻结抗性 | — | 生物威慑、捕猎本能（目标生物…） | — | — |
| 兔 | 天然护甲（杀手兔） | — | — | — | 细雪行者、强健跳跃 |
| 劫掠兽 | — | 抗击退（…%） | 生物威慑、呼唤同伴、咆哮 | 击退强化（数值） | — |
| 潜影贝 | 火焰免疫／火焰抗性、摔落免疫、末影瞬移 | 缩壳防御 | 呼唤同伴、头部投射 | — | — |
| 犰狳 | — | 缩壳防御、脱落鳞甲 | 生物威慑 | — | — |
| 骷髅 | 阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 骷髅马 | 治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 可骑乘 | 无需呼吸 | — | 强健跳跃 |
| 僵尸马 | 治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 可骑乘 | 无需呼吸 | — | 强健跳跃 |
| 史莱姆 | 免疫效果（渗浆） | 身体弹跳 | 捕猎本能（目标生物…） | — | — |
| 雪傀儡 | 遇水受伤、冻结免疫／冻结抗性、高温灼伤、摔落免疫 | — | 捕猎本能（目标生物…）、头部投射 | — | 积雪足迹 |
| 蜘蛛 | 无视蛛网、免疫效果（中毒）、附魔易伤（节肢杀手） | — | 捕猎本能（目标生物…） | — | 攀墙 |
| 流浪者 | 冻结免疫／冻结抗性、阳光灼伤、治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 拾取装备 | — |
| 炽足兽 | 火焰免疫／火焰抗性、遇水受伤、冻结易伤 | 可骑乘 | — | — | 熔岩行者 |
| 蝌蚪 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸、离水窒息（负面） | — | — |
| 海龟 | 附魔易伤（穿刺） | 游泳专长 | 水下呼吸 | — | — |
| 恼鬼 | — | — | 生物威慑、呼唤同伴 | 扑翼飞行 | — |
| 悦灵 | 自然恢复（…/秒） | — | 可驯服 | 扑翼飞行 | — |
| 监守者 | 火焰免疫／火焰抗性 | 抗击退（…%）、音波冲击 | 振动感知 | 击退强化（数值） | — |
| 凋灵 | 火焰免疫／火焰抗性、冻结免疫／冻结抗性、治疗反转、免疫效果（中毒、生命恢复、凋零）、天然护甲（…）、自然恢复（…/秒）、附魔易伤（亡灵杀手）、凋灵护甲 | 悬浮飞行 | 无需呼吸、捕猎本能（目标生物…）、头部投射 | — | — |
| 凋灵骷髅 | 火焰免疫／火焰抗性、治疗反转、免疫效果（中毒、生命恢复、凋零）、附魔易伤（亡灵杀手） | — | 无需呼吸、捕猎本能（目标生物…） | 附效攻击（凋零）、拾取装备 | — |
| 狼 | — | — | 可驯服、生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 僵尸疣猪兽 | 火焰免疫／火焰抗性、治疗反转、免疫效果（中毒、生命恢复）、附魔易伤（亡灵杀手） | 抗击退（…%） | 无需呼吸、生物威慑、捕猎本能（目标生物…） | 击退强化（数值）、近战击飞 | — |
| 僵尸猪灵 | 火焰免疫／火焰抗性、治疗反转、免疫效果（中毒、生命恢复）、天然护甲（…）、附魔易伤（亡灵杀手） | — | 无需呼吸、生物威慑、呼唤同伴 | 破门、拾取装备 | — |
| 唤魔者 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | 施法 | — |
| 幻术师 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 掠夺者 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 卫道士 | — | — | 生物威慑、捕猎本能（目标生物…）、呼唤同伴 | — | — |
| 猪灵 | — | — | 捕猎本能（目标生物…）、呼唤同伴 | 拾取装备 | — |
| 猪灵蛮兵 | — | — | 捕猎本能（目标生物…） | — | — |
| 巨人 | — | — | — | — | — |
| 哞菇 | — | 可挤奶 | — | — | — |
| 女巫 | 魔法抗性 | — | — | 施法 | — |
| 牛 | — | 可挤奶 | — | — | — |
| 猪 | — | 可骑乘 | — | — | — |
| 绵羊 | — | 可剪毛 | — | — | — |
| 苦力怕 | — | 自爆 | — | — | — |
| 鸡 | — | 产蛋 | — | 振翅缓降 | — |

## 五、代码入口与来源

| 层 | 现有入口 | 职责 |
| --- | --- | --- |
| 特性定义 | `BionicTrait`、`BionicTraitScope`、`BionicTraitRule` | 范围、载体、获取类型、返回值与附加条件 |
| 数据与供体 | `BionicTraitDataLoader`、`BionicTraitRegistry`、`BionicTraitDonors` | 数据覆盖、不可变快照、供体事实与按世界／数据版本缓存 |
| 测量与解析 | `BionicTissue`、`BionicTraitCarriers`、`BionicAnatomyRegistry`、`BionicTraitResolver`、`BionicTraitConditions` | 体积、肢体链、覆盖率、数量、承重与依赖 |
| 结果与提示 | `BionicTraitSet`、`BionicTraitResult`、手术台、纸箱与 JEI | 共用获取结果、强度、数值、集合、有效成员与提示 |
| 行为执行 | `SlimeBionicEntity`、`BionicDefenses`、`BionicSpecialAttacks`、`BionicHunting`、`BionicVibrationSense` | 体质、技能、索敌、瞬移与振动 |
| 装备与生产 | `BionicEquipment`、`BionicProduction`、`BionicCombatEvents`、`BionicAreaEffectCloud` | 交互、容器、生产、区域效果与主人关系 |
| 细雪接入 | `PowderSnowBionicFootMixin` | 腿部细雪行走 |

提示沿用头部、全身、躯干、手臂、腿部五组，头部的性情和智力排在前两项。技能显示“技能”标签。火焰／冻结免疫、全身摔落免疫、完整飞行分别取得对应抗性、腿部摔落免疫与缓降提示的显示优先级。比例型显示有效占比；数值型显示量化后的有效值。实体、手术台预览、纸箱与 JEI 使用同一解析结果。

`BionicTraitSet.coverage()` 对全身和躯干返回整体占比，对独立肢体返回最高覆盖率；`has()` 表示获取结果，`strength()` 表示执行系数，数量与承重通过附加条件判定。特性功能沿用现有 `BionicTraits.ENABLED` 发布开关。数据与分类覆盖通过玩家登录和 `/reload` 同步并更新缓存。

源码以 [ref/SOURCES.md](../ref/SOURCES.md) 和 [Minecraft/SOURCE.json](../ref/1.21.1/Minecraft/SOURCE.json) 记录的 Minecraft 1.21.1 原版快照为依据；NeoForge 扩展以本地 `build/moddev/artifacts/neoforge-21.1.234-sources.jar` 的 `EnderMan`、`Shulker`、`BreakDoorGoal`、`EventHooks` 与瞬移事件源码为依据。目标 Goal 的方法签名及呼援流程还核对了本地 NeoForm 已有 Java 源码归档 `sourcesAndCompiledWithNeoForge_5478701d24f4f19996755d682a4e8b1da115b3b8_output.jar`。附魔时长与原版标签使用 `build/moddev/artifacts/neoforge-21.1.234-client-extra-aka-minecraft-resources.jar` 中 Minecraft 1.21.1 的原版数据；生物反查表按嵌套实体标签展开。本次内容核对使用源码读取、文档结构检查、Gradle 编译与现有测试。
