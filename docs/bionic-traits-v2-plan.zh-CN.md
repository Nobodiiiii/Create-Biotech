# 仿生生物特性 v2 建议表

整理日期：2026-09-28。依据本分支的 Minecraft 1.21.1、NeoForge 21.1.234、Create 6.0.10-281。

本文在[特性总表](bionic-traits-reference.zh-CN.md)现有 37 项之外，列出 v2 建议新增的 39 项：公共特性 14 项、专属特性 21 项、生产特性 4 项。表格沿用总表的列和“全身、躯干、头、臂、腿”的顺序，另加“批次”列和留空的“修正”列，批注确认后再并入总表。本文只是建议，没有改动代码或数据。

表中 c 与总表相同，指该特性声明范围内的覆盖率（0–1，按体积加权）；门槛型默认 `min_coverage = 0.5`。原版数值取自本地参考源码，逐项出处见第七节。

## 设计约束

- 沿用“供体生命、移速、伤害、攻速不继承”。v2 只新增定性能力；唯一的新数值项“击退强化”与现有“抗击退”对称。
- 每项特性仍只声明一个作用范围，载体标签只筛选分子；规则仍是存在型、门槛型、比例型三类。
- 同一机制的物种差异合并为一个特性 ID 加子项，做法同 `effect_attack`：子项各自声明载体和参数，Tooltip 显示为“特性名（子项…）”。
- 破坏方块、引火和爆炸都受 `mobGriefing` 约束（NeoForge 为 `EventHooks.canEntityGrief`）。范围效果一律跳过主人和盟友（`isAlliedTo`）。
- 自动探测只使用两端结果一致的事实：类、接口、属性和实体标签。不扫描目标选择器：`Mob` 只在服务端调用 `registerGoals()`（`Mob.java:148-150`），而成品在客户端也会解析特性。因此原版靠 Goal 实现的能力（如扑击）改用数据列表。
- 以下两项不需要新增：
  - 拴绳：`Mob.canBeLeashed()` 为 `!(this instanceof Enemy)`（`Mob.java:1345-1348`），成品不是 `Enemy`，本来就能拴。
  - 回响碎片附身是玩家换用成品的身体；本文的“可骑乘”保留成品实体，玩家作为乘客，两者不冲突。

## 批次与共用基础设施

| 批次 | 含义 |
| --- | --- |
| A | 在现有的受伤、命中、交互、tick 或 Goal 中接入，最多新增一个 `Condition` 或 `ValueKind` |
| B | 需要骑乘层，或装备与物品栏（I1、I2） |
| C | 需要特殊攻击调度，部分还需要头部发射几何（I3、I4） |
| D | 需要生产框架、改动方块或改写供体（I9、I10） |

| 代号 | 内容 | 使用者 |
| --- | --- | --- |
| I1 骑乘层 | `getControllingPassenger`、`tickRidden`、`getRiddenInput`、`getRiddenSpeed`；按躯干顶面计算座位；鞍的装卸、渲染与掉落；跳跃键（蓄力跳跃或冲刺）；飞行和游泳坐骑的升降；下马位置与同步 | `rideable`、`strong_jump` 的骑乘跳跃 |
| I2 装备与物品栏 | `BODY` 槽、驮箱物品栏与界面、自动拾取。另建议补一个非特性的通用交互：主人潜行右击，把手持武器交给成品。目前 `mobInteract` 只处理驯服交互，我没有找到生存模式下让成品持械的途径 | `cargo_chest`、`body_armor`、`loot_pickup`、`ranged_weapon`、`bartering` |
| I3 特殊攻击调度 | 统一的冷却、蓄力、目标判定（视线或振动追踪）、发射点（载体体块的世界坐标质心）和客户端动作同步（沿用 `SlimeBionicAttackActionPacket` 的做法），按智力选择可用子项 | `sonic_boom`、`head_projectile`、`guardian_beam`、`spellcasting`、`ranged_weapon`、`roar`、`head_strike` |
| I4 头部发射几何 | 打包时按颈关节保存头部发射点和头部触及距离，类似 `ArmAttackGeometry`；现有 `AttackGeometry` 只有左右臂 | `head_strike`、`head_projectile`、`guardian_beam`、`tongue_catch` |
| I5 关系表 | 仿照 `BionicDeterrence.targets()` 的物种关系表，解析结果按载体供体合并，同 `deterrenceTargets` | `hunting_instinct`、`pack_alert`、`block_aversion` |
| I6 子项集合 | 把 `ATTACK_EFFECT_SET` 和 JSON 的 `effects` 推广为通用子项：每个子项独立声明 `roles`、参数和生效成员 | `enchant_vulnerability`、`rideable`、`ranged_weapon`、`head_projectile`、`head_strike`、`launch_strike`、`spellcasting`、`ender_teleport` |
| I7 新载体标签 | 角 `HORN`：山羊、疣猪兽、僵尸疣猪兽、劫掠兽的 `left_horn`/`right_horn`；眼 `EYE`：守卫者的 `eye`。`inferNamedRoles` 补充 horn、tusk、eye 的推断 | `head_strike`、`roar`、`guardian_beam` |
| I8 逐臂数值 | 数值项按每条臂各取前 `min_coverage` 体积加权，命中时使用出手臂的值 | `attack_knockback` |
| I9 生产框架 | 载体 + 输入物品或环境 + 产物 + 冷却／再生。物品交互走 `mobInteract`：Create 机械手 USE 模式会调用 `entity.interact`（`ref/1.21.1/Create/src/main/java/com/simibubi/create/content/kinetics/deployer/DeployerHandler.java:186`）。剪刀在 NeoForge 中走 `IShearable`（`ShearsItem.interactLivingEntity`），机械手同样可用 | 第三节全部、`bartering`、`sniff_digging`、`tongue_catch`、`pollination` |
| I10 供体改写 | 运行时把部分源的 `MimicProfile` 换成模型相同的另一物种，然后重新解析特性 | `nether_zombification`、可选的山羊断角 |

## 一、公共特性

| 作用部位 | 特性 ID | 中文名 | 供体载体标签（不代表安装位置） | 对应生物（来源） | 生效条件 / 汇总方式 | 效果 | 批次 | 修正 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 全身 | `enchant_vulnerability` | 附魔易伤（亡灵杀手／节肢杀手／穿刺） | 供体所有体块，不筛选载体标签 | 自动：实体标签 `sensitive_to_smite`（即 `#undead`）、`sensitive_to_bane_of_arthropods`（即 `#arthropod`：蜜蜂、末影螨、蠹虫、蜘蛛、洞穴蜘蛛）、`sensitive_to_impaling`（即 `#aquatic`：海龟、美西螈、守卫者、远古守卫者、鳕鱼、鲑鱼、河豚、热带鱼、海豚、鱿鱼、发光鱿鱼、蝌蚪） | 每种附魔的易伤组织全身覆盖率分别 ≥ 0.5（同效果免疫） | 被带对应附魔的武器、箭或三叉戟命中时，每级 +2.5 伤害；节肢杀手直接命中另加缓慢 IV，持续 1.5 至 1.5 + 0.5 ×（等级 − 1）秒 | A（I6） | |
| 躯干 | `rideable` | 可骑乘（直接操控／诱饵操控／仅乘坐） | 供体所有体块，不筛选载体标签 | 自动：`Saddleable`，即马、驴、骡、骷髅马、僵尸马、骆驼、羊驼、行商羊驼、猪、炽足兽 | c ≥ 0.5；新增条件：身体体积 ≥ `min_body_volume`（建议约 0.5 方块³，接近猪） | 装鞍后可骑乘；操控方式、座位数和跳跃键由贡献体积最大的供体决定，见子表 1。已获得可驯服时只有主人能骑。移动沿用成品现有方式：步行、跳跃（着地腿少于 2 条时）、飞行或游泳；飞行坐骑按视线俯仰前进，跳跃键上升 | B（I1、I6） | |
| 躯干 | `cargo_chest` | 驮运箱 | 供体所有体块，不筛选载体标签 | 自动：`AbstractChestedHorse`，即驴、骡、羊驼、行商羊驼 | c ≥ 0.5 | 用箱子右击装上驮箱，死亡时掉落箱子和内容。容量取贡献体积最大的供体：驴、骡 15 格；羊驼按供体保存的 `Strength`（1–5）× 3 格。已驯服时只有主人能打开 | B（I2） | |
| 躯干 | `body_armor` | 披甲（马铠／狼铠／地毯） | 供体所有体块，不筛选载体标签 | 自动：`canUseSlot(BODY)` 为真，即马、狼、羊驼、行商羊驼 | c ≥ 0.5 | 可装备身体装备，类型取贡献体积最大的供体。马铠提供护甲值；狼铠用耐久代替生命承伤（`bypasses_wolf_armor` 伤害除外）；地毯只作装饰。原版铠甲贴图绑定原模型，渲染需要另做 | B（I2） | |
| 头 | `hunting_instinct` | 捕猎本能（目标生物…） | 头 `HEAD`：`head`、`head_cube`；未声明时用捕获头部 | 关系表（代码，同生物威慑）：见子表 2 | 对应头部载体存在（不设占比门槛） | 主动以关系表中的非玩家生物为目标，不受头部性情“友好／中立”的限制；原版只在未驯服时成立的关系，成品有主人后停用。对玩家仍只按性情判定 | A（I5） | |
| 头 | `pack_alert` | 呼唤同伴 | 头 `HEAD`：`head`、`head_cube` | 数据：狼、蜜蜂、海豚、烈焰人、末影螨、蠹虫、僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、潜影贝、唤魔者、幻术师、掠夺者、劫掠兽、恼鬼、卫道士、猪灵、疣猪兽 | 对应头部载体存在（不设占比门槛） | 被活体伤害后，追随范围（35 格，纵向 ±10）内同一主人、或同样具备本特性且无主人的仿生生物以攻击者为目标。可选：同时唤起附近的该供体物种，例如僵尸猪灵头唤起僵尸猪灵 | A（I5） | |
| 头 | `block_aversion` | 畏惧方块（…）（负面） | 头 `HEAD`：`head`、`head_cube` | 数据：猪灵 → `#piglin_repellents`（灵魂火、灵魂火把、灵魂灯笼、点燃的灵魂营火）；疣猪兽 → `#hoglin_repellents`（诡异菌、盆栽诡异菌、下界传送门、重生锚） | 对应头部载体存在（不设占比门槛） | 水平 8 格、垂直 4 格内有对应方块时远离，并提高其周围的寻路代价；疣猪兽头另在 200 tick 内不主动攻击 | A（I5） | |
| 臂 | `ranged_weapon` | 远程武器（弓／弩／三叉戟） | 攻击手 `ATTACK_HAND`：`right_arm`、`left_arm`、`rightArm`、`leftArm` | 自动：`AbstractSkeleton`（骷髅、流浪者、沼骸、凋灵骷髅）→ 弓；`CrossbowAttackMob`（掠夺者、猪灵）→ 弩；溺尸 → 三叉戟 | 装成臂的对应载体存在；该臂的持物槽拿着对应武器 | 目标在近战范围外、武器射程内时由该臂射击；不耗弹药，射出的箭或三叉戟不可拾取（同原版生物）。参数见子表 3。已删除的 `ranged_effect` 在此恢复为箭矢附效：流浪者缓慢、沼骸中毒、凋灵骷髅燃烧 | C（I2、I3、I6） | |
| 臂 | `attack_knockback` | 击退强化（…） | 供体所有体块，不筛选载体标签 | 自动：基础 `ATTACK_KNOCKBACK`，即疣猪兽 1.0、僵尸疣猪兽 1.0、劫掠兽 1.5、监守者 1.5 | 每条臂取该臂数值最高的前 50% 体积加权平均 | 该臂命中时按原版近战附加击退 `knockback(值 × 0.5)` | A（I8） | |
| 臂 | `door_handling` | 开门（开关门／破门） | 攻击手 `ATTACK_HAND`：村民为 `arms`；猪灵、猪灵蛮兵、僵尸类为 `right_arm`、`left_arm` | 数据：开关门——村民、猪灵、猪灵蛮兵；破门——僵尸、尸壳、僵尸村民、僵尸猪灵 | 装成臂的对应载体存在 | 开关门：寻路可穿过 `#mob_interactable_doors`（木门和铜门），经过时开门并在身后关上。破门：仅困难难度，贴门 240 tick 后破坏，受 `canEntityDestroy` 约束 | A | |
| 臂 | `wing_slow_fall` | 振翅缓降 | 左翅 `LEFT_WING`／右翅 `RIGHT_WING`：`left_wing`、`right_wing` | 数据：鸡、鹦鹉 | 安装为臂；每条臂覆盖率 ≥ 0.5 才计为有效翼，有效翼 ≥ 2（`min_wings`）；不检查承重 | 离地下落时纵向速度每 tick × 0.6，免疫摔落；扑翼飞行或悬浮飞行生效时隐藏 | A | |
| 臂 | `loot_pickup` | 拾取装备 | 攻击手 `ATTACK_HAND`：`right_arm`、`left_arm` | 数据：僵尸、尸壳、僵尸村民、溺尸、僵尸猪灵、骷髅、流浪者、沼骸、凋灵骷髅、猪灵 | 装成臂的对应载体存在 | 拾取 1 格内该臂能用的武器（近战武器；具备远程武器时也拾取对应远程武器），放入该臂的持物槽，更好的会替换原物品；受 `canEntityGrief` 约束 | B（I2） | |
| 腿 | `leap_attack` | 扑击 | 腿 `LEG`：四肢部件名；蜘蛛为 8 条腿部件 | 数据：蜘蛛、洞穴蜘蛛、狼、狐狸（`yd` 0.4）；猫、豹猫（`yd` 0.3）。原版均注册 `LeapAtTargetGoal` | 每条腿覆盖率 ≥ 0.5 才有效；有效腿 ≥ 2 | 目标在 2–4 格内且自身着地时，每次目标评估有 1/3 概率扑出：水平速度 = 指向目标 × 0.4 + 当前速度 × 0.2，纵向速度设为有效腿供体 `yd` 的最大值 | A | |
| 腿 | `strong_jump` | 强健跳跃 | 腿 `LEG`：四肢部件名；兔为 `left_haunch`、`right_haunch`、`left_hind_foot`、`right_hind_foot` | 数据：马、骷髅马、僵尸马、兔、山羊、青蛙 | 每条腿覆盖率 ≥ 0.5 才有效；有效腿 ≥ 2 | 起跳速度 +0.2（原版默认 0.42 约跳 1.25 格，0.62 约 2.5 格），寻路允许跳上 2 格高台。被骑乘时跳跃键为蓄力跳跃，倍率 0.4–1.0。可选：空闲时像山羊、青蛙那样远跳到随机落点 | A；骑乘跳跃 B | |

### 子表 1：可骑乘的操控方式

| 供体 | 操控 | 鞍 | 座位 | 跳跃键 | 其他 |
| --- | --- | --- | --- | --- | --- |
| 马、驴、骡 | 直接操控；侧移 × 0.5、后退 × 0.25 | 需要 | 1 | 蓄力跳跃 | 原版未驯服会甩人，成品改由可驯服决定 |
| 骷髅马、僵尸马 | 同上 | 需要 | 1 | 蓄力跳跃 | 骷髅马不在 `dismounts_underwater` 标签中，可以水下骑乘 |
| 骆驼 | 直接操控；疾跑时速度 +0.1 | 需要 | 2 | 冲刺，冷却 55 tick | 冲刺在视线方向叠加水平与纵向动量 |
| 猪 | 诱饵：骑手持胡萝卜钓竿，总是前进，朝向跟随骑手 | 需要 | 1 | — | 使用钓竿加速 140–980 tick，峰值 × 2.15，每次消耗 7 耐久 |
| 炽足兽 | 诱饵：诡异菌钓竿 | 需要 | 1 | — | 每次消耗 1 耐久；同时具备熔岩行者时可载人过熔岩 |
| 羊驼、行商羊驼 | 仅乘坐，不响应操控 | 不需要 | 1 | — | 原版 `isSaddleable()` 为假 |

### 子表 2：捕猎本能的关系

| 供体头 | 目标 | 有主人后 |
| --- | --- | --- |
| 狼 | 羊、兔、狐狸、陆上幼年海龟；骷髅、流浪者、凋灵骷髅、沼骸 | 只保留骷髅类 |
| 猫 | 兔、陆上幼年海龟 | 停用 |
| 豹猫 | 鸡、陆上幼年海龟 | 保留 |
| 狐狸 | 鸡、兔、陆上幼年海龟、鳕鱼、鲑鱼、热带鱼 | 保留 |
| 北极熊 | 狐狸 | 保留 |
| 美西螈 | 水中 8 格内：溺尸、守卫者、远古守卫者；热带鱼、河豚、鲑鱼、鳕鱼、鱿鱼、发光鱿鱼、蝌蚪（后七种战斗结束后冷却 2400 tick） | 保留 |
| 铁傀儡 | 除苦力怕外的敌对生物（`Enemy`） | 保留 |
| 雪傀儡 | 敌对生物（`Enemy`） | 保留 |
| 守卫者、远古守卫者 | 鱿鱼、发光鱿鱼、美西螈（3 格以外） | 保留 |
| 末影人 | 末影螨 | 保留 |
| 蜘蛛、洞穴蜘蛛 | 铁傀儡（原版仅在亮度低于 0.5 时） | 保留 |
| 史莱姆、岩浆怪 | 铁傀儡 | 保留 |
| 猪灵 | 凋灵骷髅、凋灵；成年疣猪兽（猎杀后 30–120 秒内不再狩猎） | 保留 |
| 猪灵蛮兵 | 凋灵骷髅、凋灵 | 保留 |
| 僵尸、尸壳、僵尸村民 | 村民与流浪商人、铁傀儡、陆上幼年海龟 | 保留 |
| 溺尸 | 村民与流浪商人、铁傀儡、美西螈、陆上幼年海龟 | 保留 |
| 骷髅、流浪者、沼骸 | 铁傀儡、陆上幼年海龟 | 保留 |
| 凋灵骷髅 | 铁傀儡、陆上幼年海龟、猪灵、猪灵蛮兵 | 保留 |
| 卫道士、掠夺者、唤魔者、幻术师 | 村民与流浪商人、铁傀儡 | 保留 |
| 僵尸疣猪兽 | 除僵尸疣猪兽和苦力怕外的所有可攻击生物 | 保留 |
| 旋风人 | 铁傀儡 | 保留 |
| 凋灵 | 不在 `#wither_friends`（即 `#undead`）中的生物 | 保留 |
| 羊驼、行商羊驼 | 10 格内未驯服的狼 | 保留 |

现有敌对性情对村民的固定索敌（`SlimeBionicEntity.java:257`）可以迁移为本表中僵尸类和灾厄村民头的数据，是否迁移待定。

### 子表 3：远程武器

| 子项 | 供体 | 射程 | 节奏 | 附效与备注 |
| --- | --- | --- | --- | --- |
| 弓 | 骷髅、流浪者、凋灵骷髅 | 15 格 | 拉弓至少 20 tick；间隔困难 20 tick，其他难度 40 tick | 流浪者：缓慢 600 tick；凋灵骷髅：燃烧箭，命中点燃目标 5 秒 |
| 弓 | 沼骸 | 15 格 | 间隔困难 50 tick，其他难度 70 tick | 中毒 100 tick |
| 弩 | 掠夺者、猪灵 | 8 格 | 装填 25 tick（快速装填更短），之后等待 20–39 tick 再射 | — |
| 三叉戟 | 溺尸 | 10 格 | 40 tick；须主手持三叉戟 | 投出一支无附魔的新三叉戟，8 点伤害，手中的不消耗 |

## 二、专属特性

| 作用部位 | 特性 ID | 中文名 | 供体载体标签（不代表安装位置） | 对应生物（来源） | 生效条件 / 汇总方式 | 效果 | 批次 | 修正 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 全身 | `ender_teleport` | 末影瞬移（末影人／潜影贝） | 供体所有体块，不筛选载体标签 | 数据：末影人、潜影贝 | c ≥ 0.5 | 末影人：被弹射物（`#is_projectile`、投掷药水）命中时最多尝试 64 次 ±32 格瞬移，成功则不受伤；受到非活体来源的伤害后 90% 瞬移一次；追击目标超过 16 格时约每 30 tick 向目标瞬移。潜影贝：受伤后生命 < 50% 时 25% 在 ±8 格内瞬移。坐下或被拴时不随机瞬移；触发 NeoForge `EntityTeleportEvent.EnderEntity` | A（I6） | |
| 全身 | `magic_resistance` | 魔法抗性 | 供体所有体块，不筛选载体标签 | 数据：女巫 | c > 0 | `#witch_resistant_to`（魔法、间接魔法、音波、荆棘）伤害 ×（1 − 0.85c）；因此也能减免接触反伤和体表刺击的荆棘伤害 | A | |
| 全身 | `wither_shield` | 凋灵护甲 | 供体所有体块，不筛选载体标签 | 数据：凋灵 | c ≥ 0.5 | 生命 ≤ 50% 时，直接实体为 `AbstractArrow`（含三叉戟）或风弹的伤害无效 | A | |
| 全身 | `iron_repair` | 铁锭修补 | 供体所有体块，不筛选载体标签 | 数据：铁傀儡 | c > 0 | 未满血时用铁锭右击，消耗 1 个恢复 25c 点生命（原版铁傀儡恢复 25 点）；机械手可以自动修补 | A | |
| 全身 | `darkness_pulse` | 黑暗脉冲 | 供体所有体块，不筛选载体标签 | 数据：监守者 | c ≥ 0.5 | 每 120 tick 给 20 格内非盟友的生存、冒险模式玩家施加黑暗 260 tick；主人和盟友不受影响 | A | |
| 全身 | `elder_curse` | 远古诅咒 | 供体所有体块，不筛选载体标签 | 数据：远古守卫者 | c ≥ 0.5 | 每 1200 tick 给 50 格内非盟友的生存、冒险模式玩家施加挖掘疲劳 III 6000 tick | A | |
| 全身 | `nether_zombification` | 异界尸化（负面） | 供体所有体块，不筛选载体标签 | 自动：`AbstractPiglin`（猪灵、猪灵蛮兵）和疣猪兽 | 存在即生效，逐块组织转化（不设占比门槛） | 在非 `piglin_safe` 维度（原版只有下界安全）累计 300 tick 后，这些组织的供体改为僵尸猪灵或僵尸疣猪兽（模型相同），施加反胃 200 tick，并重新解析特性：获得亡灵体质，失去以物易物 | D（I10） | |
| 躯干 | `sonic_boom` | 音波冲击 | 躯干 `TORSO`：监守者 `body`、`right_ribcage`、`left_ribcage` | 数据：监守者 | 安装在躯干；监守者胸腔载体占全部躯干体积 ≥ 0.5 | 目标在水平 15 格、垂直 20 格内时，蓄力 34 tick 后从载体质心发射：10 点 `sonic_boom` 伤害（无视护甲、盾牌和保护类附魔，受抗性效果减免），推开目标水平 2.5 ×（1 − 抗击退）、纵向 0.5 ×（1 − 抗击退）。整个动作 60 tick，结束后冷却 40 tick，近战挥击也会设 40 tick 冷却。原版不查视线、可以穿墙；建议改为要求目标可见，或正被振动感知追踪 | C（I3） | |
| 躯干 | `self_destruct` | 自爆 | 供体所有体块，不筛选载体标签 | 数据：苦力怕 | c ≥ 0.5 | 目标进入 3 格时开始膨胀，超过 7 格或失去视线时回缩，满 30 tick 引爆：威力 3，被闪电击中后充能为 6；打火石或火焰弹（`#creeper_igniters`）点燃后必定引爆。成品随之死亡、不再分裂，带有效果时留下药效云。建议有主人时只在主人点燃后引爆 | D | |
| 头 | `head_strike` | 头部攻击（冲撞／挑飞） | 角 `HORN`：山羊的 `left_horn`、`right_horn`；疣猪兽、僵尸疣猪兽的 `right_horn`、`left_horn`（獠牙） | 数据：山羊；疣猪兽、僵尸疣猪兽（仅成年供体） | 对应头部载体存在（不设占比门槛） | 冲撞（山羊）：目标在 4–7 格时低头蓄力 20 tick，以 3.0 速度冲出，命中造成 2 点不引仇恨的伤害，击退 2.5 × clamp(速度 × 1.65, 0.2, 3.0)。原版 600–6000 tick 的冷却用于闲逛撞击，建议战斗中改用尖叫山羊的 100–300 tick；撞上 `#snaps_goat_horn` 方块时可选掉落山羊角，并切除该角体块。挑飞（疣猪兽）：头部命中后按（1.0 − 目标抗击退）抛起目标，水平 0.2–0.7 倍、纵向 0–0.5 倍并随机偏转，被盾牌挡住也会抛起 | C（I3、I4、I7） | |
| 头 | `roar` | 咆哮 | 头 `HEAD`／角 `HORN`：劫掠兽的 `head`、`mouth`、`left_horn`、`right_horn` | 数据：劫掠兽 | 对应头部载体存在（不设占比门槛） | 近战被盾牌格挡时 50% 眩晕 40 tick（不能移动或攻击），眩晕结束 10 tick 后咆哮：4 格方框内的非盟友活体受 6 点伤害，并被强力推开（水平 4 / 距离、纵向 0.2，不受抗击退减免）。未眩晕时直接强力推开格挡者 | C（I3、I7） | |
| 头 | `head_projectile` | 头部投射（唾沫／雪球／小火球／火球／潜影弹／凋灵之首／龙息／风弹） | 头 `HEAD`：各供体的头部件，见子表 4 | 数据：羊驼、行商羊驼、雪傀儡、烈焰人、恶魂、潜影贝、凋灵、末影龙、旋风人 | 对应头部载体存在（不设占比门槛）；多个子项并存时按智力选择 | 从头部发射点向目标发射对应弹射物，射程、节奏和伤害沿用各供体的原版值（子表 4）；爆炸和引火受 `mobGriefing` 约束 | C（I3、I4、I6） | |
| 头 | `guardian_beam` | 守卫者光束 | 眼 `EYE`：守卫者、远古守卫者的 `eye` | 数据：守卫者、远古守卫者 | 对应头部载体存在（不设占比门槛） | 16 格内有视线的目标（守卫者还要求距离大于 3 格）：预热 10 tick 后光束持续 80 tick（远古 60 tick），期间失去视线即中断；结束时造成 1 点间接魔法伤害（困难 +2、远古 +2）和 6 点（远古 8 点）近战伤害 | C（I3、I4、I7） | |
| 头 | `play_dead` | 装死 | 头 `HEAD`：美西螈的 `head` | 数据：美西螈 | 对应头部载体存在（不设占比门槛） | 在水中受到实体伤害且未致死时，有 1/3 概率（还需随机 0–2 小于伤害，或生命 < 50%）装死 200 tick：停止寻路和攻击，获得生命恢复 I 200 tick，期间不能被选为攻击目标 | A | |
| 头 | `tongue_catch` | 舌卷捕食 | 头 `HEAD`：青蛙的 `head`、`eyes`、`tongue` | 数据：青蛙 | 对应头部载体存在（不设占比门槛） | 10 格内尺寸为 1 的史莱姆或岩浆怪（`#frog_food`）：走到 1.75 格内卷舌吞食。原版蛙明灯来自岩浆怪战利品表对青蛙击杀者的判定，成品需要按供体保存的 `variant` 直接产出：温带赭黄、热带珠光、寒带青翠；史莱姆产出 1 个黏液球。必须排除本模组的拟态史莱姆和主人的分裂史莱姆 | D（I4、I9） | |
| 头 | `bartering` | 以物易物 | 头 `HEAD`：猪灵的 `head` | 数据：猪灵（仅成年供体） | 对应头部载体存在（不设占比门槛） | 接受金锭（玩家或机械手右击，或拾取掉落的金锭），端详 119 tick 后按 `gameplay/piglin_bartering` 掉落一次；被玩家攻击后 400 tick 内不交易。猪灵组织在主世界会先触发异界尸化 | D（I2、I9） | |
| 头 | `sniff_digging` | 嗅探挖掘 | 头 `HEAD`：嗅探兽的 `head`、`nose`、`lower_beak` | 数据：嗅探兽（仅成年供体） | 对应头部载体存在（不设占比门槛） | 空闲时嗅探 40–80 tick，在 10–18 格内寻找 `#sniffer_diggable_block`，挖掘 160–180 tick（第 120 tick 出物），按 `gameplay/sniffer_digging` 掉落火把花种子或瓶子草荚果；完成后冷却 9600 tick，并记住最近 20 个已挖位置 | D（I9） | |
| 臂 | `launch_strike` | 近战击飞（上挑／拍飞） | 攻击手 `ATTACK_HAND`：铁傀儡的 `right_arm`、`left_arm`；左翅 `LEFT_WING`／右翅 `RIGHT_WING`：末影龙的 `left_wing`、`right_wing` 及其 `_tip` | 数据：铁傀儡；末影龙 | 装成臂的对应载体存在；必须由包含该载体的攻击臂命中 | 上挑（铁傀儡）：命中后目标纵向速度 +0.4 ×（1 − 抗击退），与通用受伤击退叠加，着地目标约获得 0.76 的纵向速度；原版没有额外的水平击退。拍飞（末影龙翼）：命中后推开目标，水平 4 / 距离、纵向 0.2，不受抗击退减免 | A（I6） | |
| 臂 | `spellcasting` | 施法（唤魔尖牙／喷溅药水） | 攻击手 `ATTACK_HAND`：唤魔者的 `arms`、`right_arm`、`left_arm`；女巫的 `arms` | 数据：唤魔者、女巫 | 装成臂的对应载体存在 | 唤魔尖牙：施法 40 tick，每 100 tick 一次；目标在 3 格内时生成内 5、外 8 两圈尖牙，否则沿直线生成 16 个（1.25–20 格）；每个尖牙造成 6 点间接魔法伤害，跳过主人和盟友。喷溅药水：每 60 tick 一次、射程 10 格，依次选择缓慢（≥ 8 格）、中毒（目标生命 ≥ 8）、虚弱（≤ 3 格，25%）、伤害。可选：女巫的自我用药（着火时抗火、水中水下呼吸、受伤时治疗） | C（I3、I6） | |
| 腿 | `snow_trail` | 积雪足迹 | 足 `FOOT`：雪傀儡的 `lower_body` | 数据：雪傀儡 | 每条腿覆盖率 ≥ 0.5 才有效；有效腿 ≥ 1 | 在脚下四角（±0.25）为空气且雪能存留的位置放置雪片；受 `mobGriefing` 约束。原版没有温度判定 | D | |
| 腿 | `pollination` | 授粉催熟 | 腿 `LEG`：蜜蜂的 `front_legs`、`middle_legs`、`back_legs` | 数据：蜜蜂 | 每条腿覆盖率 ≥ 0.5 才有效；有效腿 ≥ 1 | 在 5 格内的花（`#flowers`）旁停留超过 400 tick 后携带花粉；之后约每 30 tick 一次，使脚下 1–2 格的 `#bee_growables` 生长一阶，每次携粉最多 10 株；下雨时不采集 | D（I9） | |

### 子表 4：头部投射

| 子项 | 供体 | 载体部件 | 射程 | 节奏 | 效果 |
| --- | --- | --- | --- | --- | --- |
| 唾沫 | 羊驼、行商羊驼 | `head` | 20 格 | 40 tick | 1 点伤害；原版只对攻击者吐一次，并对未驯服的狼吐 |
| 雪球 | 雪傀儡 | `head` | 10 格 | 20 tick | 0 点伤害（对烈焰人 3 点），只有击退 |
| 小火球 | 烈焰人 | `head` | 48 格 | 蓄力 60 tick，连发 3 发、间隔 6 tick，冷却 100 tick（每轮 178 tick） | 每发 5 点并点燃 5 秒；落地引火受 `mobGriefing` 约束 |
| 火球 | 恶魂 | `body`（恶魂的脸就是身体） | 64 格，需要视线 | 蓄力 20 tick，每 60 tick 一发 | 直接命中 6 点，爆炸威力 1（引火和破坏受 `mobGriefing` 约束）；可被打回 |
| 潜影弹 | 潜影贝 | `head`（由 `ShulkerHeadLayer` 单独渲染，需确认捕获时包含它，否则改用 `base`） | 20 格 | 20–110 tick | 追踪；4 点伤害并漂浮 200 tick |
| 凋灵之首 | 凋灵 | `center_head`、`left_head`、`right_head` | 20 格 | 40 tick | 8 点伤害并凋零 II（普通 200 tick、困难 800 tick）；爆炸威力 1；0.1% 为蓝色头颅 |
| 龙息 | 末影龙 | `head`、`jaw` | 64 格，需要视线 | 建议 100 tick（原版由飞行阶段决定，没有固定间隔） | 落点生成半径 3→7、持续 600 tick 的龙息云，每 20 tick 造成 6 点伤害；原版 `BottleItem` 只认末影龙所有的药效云，装瓶需要扩展 |
| 风弹 | 旋风人 | `head`、`eyes` | 2–16 格 | 蓄力 15 tick，约 20 tick 一发 | 直接命中 1 点；半径 3 的风爆击退并触发按钮和门，不破坏方块 |

## 三、生产特性

生产特性都通过 `mobInteract` 或定时产出实现，因此 Create 机械手可以直接用于自动化。幼年供体的组织不产出。

| 作用部位 | 特性 ID | 中文名 | 供体载体标签（不代表安装位置） | 对应生物（来源） | 生效条件 / 汇总方式 | 效果 | 批次 | 修正 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 躯干 | `milkable` | 可挤奶（牛奶／蘑菇煲） | 供体所有体块，不筛选载体标签 | 数据：牛、哞菇、山羊 | c ≥ 0.5 | 空桶右击得牛奶桶；哞菇另可用碗得蘑菇煲；和原版一样没有冷却 | D（I9） | |
| 躯干 | `wool_shearing` | 可剪毛 | 供体所有体块，不筛选载体标签 | 数据：绵羊 | c ≥ 0.5 | 剪刀剪下 1–3 个羊毛，颜色取供体保存的 `Color`；之后需吃草（成年每次检查 1/1000）或按 `regrow_ticks` 再生；通过 NeoForge `IShearable` 接入 | D（I9） | |
| 躯干 | `egg_laying` | 产蛋 | 供体所有体块，不筛选载体标签 | 数据：鸡 | c ≥ 0.5 | 每 6000–11999 tick 产 1 个蛋 | D（I9） | |
| 躯干 | `scute_shedding` | 脱落鳞甲 | 供体所有体块，不筛选载体标签 | 数据：犰狳 | c ≥ 0.5 | 每 6000–11999 tick 掉落 1 个犰狳鳞甲；用刷子可以额外刷下 1 个，刷子消耗 16 耐久 | D（I9） | |

沼骸的蘑菇和雪傀儡的南瓜也能剪，但剪后外观会变，需要改写供体的稳定字段（I10），列为候补。

## 四、现有特性的补充建议

- `shell_defense`：潜影贝子项可补上原版闭壳效果——闭壳时直接实体为 `AbstractArrow`（含三叉戟）的伤害无效，护甲 +20。目前只有伤害 × 0.5。
- `wing_flight`：承重不足时，是否把翼部供体降级为 `wing_slow_fall`，待定。
- `vibration_sense`：与 `sonic_boom` 联动，允许对正在追踪、但看不见的目标发动音波冲击。

## 五、候补与不建议

| 能力 | 供体 | 处理 | 原因 |
| --- | --- | --- | --- |
| 伏击扑击 | 狐狸 | 候补，可作为扑击的子项 | 需要蹲伏潜行阶段，约 15 tick 后以水平 0.8、纵向 0.9 扑出 |
| 海豚的恩惠、寻宝 | 海豚 | 候补 | 恩惠只给 10 格内正在游泳的玩家 100 tick；寻宝需要喂鱼和结构搜索 |
| 叼物、物品收集 | 狐狸、悦灵 | 候补 | 与悦灵快递的功能重叠 |
| 搬运方块、注视激怒 | 末影人 | 候补 | 会破坏方块，或属于负面行为 |
| 水晶治疗 | 末影龙 | 候补 | 依赖末影水晶，每 10 tick 恢复 1 点，偏 Boss 机制 |
| 远跳 | 旋风人 | 候补 | 需要空中落点搜索 |
| 俯冲攻击 | 幻翼 | 候补 | 需要飞行攻击路径 |
| 离熔岩发冷（负面） | 炽足兽 | 候补 | 移速 −34%，影响小 |
| 碰撞破坏树叶 | 劫掠兽 | 候补 | 破坏方块 |
| 嗅觉索敌 | 监守者 | 候补 | 可以作为振动感知的参数 |
| 剪蘑菇、剪南瓜 | 沼骸、雪傀儡 | 候补 | 剪后外观改变，需要 I10 |
| 金甲中立 | 猪灵 | 候补 | 属于性情修饰，需并入 `BionicMind` |
| 穿墙 | 恼鬼 | 不建议 | `noPhysics` 会破坏碰撞和寻路的前提 |
| 召唤 | 唤魔者（恼鬼）、僵尸（增援） | 不建议 | 会生成新实体，不是保留组织带来的能力 |
| 感染、雷击转化 | 僵尸、猪、村民、哞菇 | 不建议 | 会改写其他实体 |
| 藏入方块、受伤毁方块、钻地 | 蠹虫、凋灵、监守者 | 不建议 | 大范围破坏方块，或依赖专用动画 |
| 交易、职业农作 | 村民、流浪商人 | 不建议 | `MimicProfile` 只保存村民的 `type`，不保存职业；交易界面与成品无关 |
| 分身、隐身 | 幻术师 | 不建议 | 需要专用渲染与 AI |
| 基因、模仿声音、晨间礼物 | 熊猫、鹦鹉、猫 | 不建议 | 价值低 |

## 六、按生物反查（v2 新增部分）

只列 v2 新增项；已有特性见总表第四节。

| 生物 | 全身 | 躯干 | 头 | 臂 | 腿 |
| --- | --- | --- | --- | --- | --- |
| 美西螈 | 附魔易伤（穿刺） | — | 捕猎本能、装死 | — | — |
| 犰狳 | — | 脱落鳞甲 | — | — | — |
| 蜜蜂 | 附魔易伤（节肢） | — | 呼唤同伴 | — | 授粉催熟 |
| 烈焰人 | — | — | 呼唤同伴、头部投射（小火球） | — | — |
| 沼骸 | 附魔易伤（亡灵） | — | 捕猎本能 | 远程武器（弓、中毒箭）、拾取装备 | — |
| 旋风人 | — | — | 捕猎本能、头部投射（风弹） | — | — |
| 骆驼 | — | 可骑乘（双座、冲刺） | — | — | — |
| 猫、豹猫 | — | — | 捕猎本能 | — | 扑击 |
| 洞穴蜘蛛、蜘蛛 | 附魔易伤（节肢） | — | 捕猎本能（铁傀儡） | — | 扑击 |
| 鸡 | — | 产蛋 | — | 振翅缓降 | — |
| 牛 | — | 可挤奶 | — | — | — |
| 苦力怕 | — | 自爆 | — | — | — |
| 海豚 | 附魔易伤（穿刺） | — | 呼唤同伴 | — | — |
| 驴、骡 | — | 可骑乘、驮运箱 | — | — | — |
| 溺尸 | 附魔易伤（亡灵） | — | 捕猎本能、呼唤同伴 | 远程武器（三叉戟）、拾取装备 | — |
| 远古守卫者 | 附魔易伤（穿刺）、远古诅咒 | — | 捕猎本能、守卫者光束 | — | — |
| 末影龙 | — | — | 头部投射（龙息） | 近战击飞（拍飞） | — |
| 末影人 | 末影瞬移 | — | 捕猎本能（末影螨） | — | — |
| 末影螨、蠹虫 | 附魔易伤（节肢） | — | 呼唤同伴 | — | — |
| 唤魔者 | — | — | 捕猎本能、呼唤同伴 | 施法（唤魔尖牙） | — |
| 狐狸 | — | — | 捕猎本能 | — | 扑击 |
| 青蛙 | — | — | 舌卷捕食 | — | 强健跳跃 |
| 恶魂 | — | — | 头部投射（火球） | — | — |
| 山羊 | — | 可挤奶 | 头部攻击（冲撞） | — | 强健跳跃 |
| 守卫者 | 附魔易伤（穿刺） | — | 捕猎本能、守卫者光束 | — | — |
| 疣猪兽 | 异界尸化 | — | 呼唤同伴、畏惧方块、头部攻击（挑飞） | 击退强化 1.0 | — |
| 马 | — | 可骑乘、披甲（马铠） | — | — | 强健跳跃 |
| 尸壳、僵尸、僵尸村民 | 附魔易伤（亡灵） | — | 捕猎本能、呼唤同伴 | 开门（破门）、拾取装备 | — |
| 幻术师、卫道士 | — | — | 捕猎本能、呼唤同伴 | — | — |
| 铁傀儡 | 铁锭修补 | — | 捕猎本能 | 近战击飞（上挑） | — |
| 羊驼、行商羊驼 | — | 可骑乘（仅乘坐）、驮运箱、披甲（地毯） | 捕猎本能、头部投射（唾沫） | — | — |
| 哞菇 | — | 可挤奶（含蘑菇煲） | — | — | — |
| 鹦鹉 | — | — | — | 振翅缓降 | — |
| 幻翼 | 附魔易伤（亡灵） | — | — | — | — |
| 猪 | — | 可骑乘（胡萝卜钓竿） | — | — | — |
| 猪灵 | 异界尸化 | — | 捕猎本能、呼唤同伴、畏惧方块、以物易物 | 远程武器（弩）、开门、拾取装备 | — |
| 猪灵蛮兵 | 异界尸化 | — | 捕猎本能 | 开门 | — |
| 掠夺者 | — | — | 捕猎本能、呼唤同伴 | 远程武器（弩） | — |
| 北极熊 | — | — | 捕猎本能（狐狸） | — | — |
| 兔 | — | — | — | — | 强健跳跃 |
| 劫掠兽 | — | — | 呼唤同伴、咆哮 | 击退强化 1.5 | — |
| 绵羊 | — | 可剪毛 | — | — | — |
| 潜影贝 | 末影瞬移 | — | 呼唤同伴、头部投射（潜影弹） | — | — |
| 骷髅、流浪者 | 附魔易伤（亡灵） | — | 捕猎本能 | 远程武器（弓；流浪者为缓慢箭）、拾取装备 | — |
| 骷髅马、僵尸马 | 附魔易伤（亡灵） | 可骑乘（骷髅马可水下骑乘） | — | — | 强健跳跃 |
| 史莱姆、岩浆怪 | — | — | 捕猎本能（铁傀儡） | — | — |
| 嗅探兽 | — | — | 嗅探挖掘 | — | — |
| 雪傀儡 | — | — | 捕猎本能、头部投射（雪球） | — | 积雪足迹 |
| 炽足兽 | — | 可骑乘（诡异菌钓竿） | — | — | — |
| 鳕鱼、鲑鱼、热带鱼、河豚、鱿鱼、发光鱿鱼、蝌蚪、海龟 | 附魔易伤（穿刺） | — | — | — | — |
| 恼鬼 | — | — | 呼唤同伴 | — | — |
| 村民 | — | — | — | 开门 | — |
| 监守者 | 黑暗脉冲 | 音波冲击 | — | 击退强化 1.5 | — |
| 女巫 | 魔法抗性 | — | — | 施法（喷溅药水） | — |
| 凋灵 | 附魔易伤（亡灵）、凋灵护甲 | — | 捕猎本能、头部投射（凋灵之首） | — | — |
| 凋灵骷髅 | 附魔易伤（亡灵） | — | 捕猎本能 | 远程武器（弓、燃烧箭）、拾取装备 | — |
| 狼 | — | 披甲（狼铠） | 捕猎本能、呼唤同伴 | — | 扑击 |
| 僵尸疣猪兽 | 附魔易伤（亡灵） | — | 捕猎本能、头部攻击（挑飞） | 击退强化 1.0 | — |
| 僵尸猪灵 | 附魔易伤（亡灵） | — | 呼唤同伴 | 开门（破门）、拾取装备 | — |

## 七、原版依据

Java 路径相对 `ref/1.21.1/Minecraft/net/minecraft/`（原版反编译快照，不含 NeoForge 补丁，来源见 `ref/SOURCES.md`）。NeoForge 的改动另查 `build/moddev/artifacts/neoforge-21.1.234-sources.jar`；原版标签、附魔与战利品表取自同目录的 `neoforge-21.1.234-client-extra-aka-minecraft-resources.jar`。模型部件名取自 `client/model/` 下对应模型的 `createBodyLayer`，末影龙取自 `client/renderer/entity/EnderDragonRenderer.java`。

| 特性 | 依据 |
| --- | --- |
| `enchant_vulnerability` | 标签 `data/minecraft/tags/entity_type/{undead,arthropod,aquatic}.json`；附魔 `data/minecraft/enchantment/{smite,bane_of_arthropods,impaling}.json`；缓慢效果 `world/item/enchantment/effects/ApplyMobEffect.java:37-47` |
| `rideable` | `world/entity/animal/horse/AbstractHorse.java:793-824, 938-966, 1046-1057`；`animal/camel/Camel.java:54-64, 243-289, 357-359`；`world/entity/ItemBasedSteering.java:29-48`；`animal/Pig.java:71-81, 232-248`；`monster/Strider.java:204-210, 255-271`；`animal/horse/Llama.java:294-297`；`data/tags/EntityTypeTagsProvider.java:92-106`（`dismounts_underwater`） |
| `cargo_chest` | `animal/horse/AbstractChestedHorse.java:149-190`；`animal/horse/Llama.java:87-94, 279-282` |
| `body_armor` | `world/entity/Mob.java:881-892`；`animal/horse/Horse.java:214-226`；`animal/Wolf.java:363-398, 439-463`；`animal/horse/Llama.java:284-292` |
| `hunting_instinct` | `animal/Wolf.java:133-140`；`animal/Cat.java:121-122`；`animal/Ocelot.java:103-104`；`animal/Fox.java:144-150, 341-351`；`animal/PolarBear.java:90-94`；`ai/sensing/AxolotlAttackablesSensor.java:8-28`；`animal/IronGolem.java:73-80, 134-140`；`animal/SnowGolem.java:55`；`monster/Guardian.java:83, 442-452`；`monster/EnderMan.java:106`；`monster/Spider.java:63-64, 224-227`；`monster/Slime.java:72`；`ai/sensing/PiglinSpecificSensor.java:94-99`；`monster/piglin/StartHuntingHoglin.java:11-39`；`monster/Zombie.java:109-117`；`monster/Drowned.java:74-87`；`monster/AbstractSkeleton.java:70-81`；`monster/WitherSkeleton.java:35-38`；`monster/Vindicator.java:64-68`、`Pillager.java:70-73`、`Evoker.java:65-67`、`Illusioner.java:70-72`；`monster/Zoglin.java:133-143`；`monster/breeze/Breeze.java:248-250`；`boss/wither/WitherBoss.java:78-80`；`animal/horse/Llama.java:455-464` |
| `pack_alert` | `ai/goal/target/HurtByTargetGoal.java:54-112`；各生物 `setAlertOthers` 调用处；`monster/ZombifiedPiglin.java:97-152`；`monster/piglin/PiglinAi.java:667-692`；`monster/hoglin/HoglinAi.java:202-231` |
| `block_aversion` | 方块标签 `piglin_repellents`、`hoglin_repellents`；`monster/piglin/PiglinAi.java:77-78, 284-286`；`monster/hoglin/HoglinAi.java:41-42, 77-94, 166-170` |
| `ranged_weapon` | `monster/AbstractSkeleton.java:49-50, 156-195`；`ai/goal/RangedBowAttackGoal.java:86-136`；`monster/Stray.java:59-66`；`monster/Bogged.java:111-128`；`monster/WitherSkeleton.java:101-105`；`ai/goal/RangedCrossbowAttackGoal.java:100-127`；`ai/behavior/CrossbowAttack.java:26-81`；`monster/Drowned.java:76, 255-264, 491-501`；`projectile/AbstractArrow.java:62, 573-576` |
| `attack_knockback` | `monster/hoglin/Hoglin.java:103`；`monster/Zoglin.java:164`；`monster/Ravager.java:94`；`monster/warden/Warden.java:183`；`world/entity/Mob.java:1426-1454` |
| `door_handling` | `ai/behavior/InteractWithDoor.java:33-150`；`monster/piglin/AbstractPiglin.java:32-41`；`ai/goal/BreakDoorGoal.java:10-82`；`monster/Zombie.java:87, 145-164` |
| `wing_slow_fall` | `animal/Chicken.java:87-90`；`animal/Parrot.java:212-215`；`data/tags/EntityTypeTagsProvider.java:73-91`（`fall_damage_immune`） |
| `loot_pickup` | `world/entity/Mob.java:535-600`；`monster/Zombie.java:458`；`monster/AbstractSkeleton.java:142`；`monster/piglin/PiglinAi.java:457-476` |
| `leap_attack` | `ai/goal/LeapAtTargetGoal.java:20-52`；`monster/Spider.java:57`；`animal/Wolf.java:125`；`animal/Ocelot.java:98`；`animal/Cat.java:116`；`animal/Fox.java:173`；`world/entity/Mob.java:148-150` |
| `strong_jump` | `ai/attributes/Attributes.java:53-55`；`animal/horse/AbstractHorse.java:431-439, 938-954, 1006-1008`；`animal/Rabbit.java:110-146`；`animal/goat/GoatAi.java:46-49, 118-135`；`animal/frog/FrogAi.java:57-60, 185-202` |
| `ender_teleport` | `monster/EnderMan.java:272-318, 368-393, 561-590`；`monster/Shulker.java:379-443` |
| `magic_resistance` | `monster/Witch.java:205-217`；`data/tags/DamageTypeTagsProvider.java:66` |
| `wither_shield` | `boss/wither/WitherBoss.java:455-485, 544-546` |
| `iron_repair` | `animal/IronGolem.java:261-277` |
| `darkness_pulse` | `monster/warden/Warden.java:87-90, 288-290, 408-411`；`world/effect/MobEffectUtil.java:47-63` |
| `elder_curse` | `monster/ElderGuardian.java:20-24, 64-73` |
| `nether_zombification` | `monster/piglin/AbstractPiglin.java:26-100`；`monster/hoglin/Hoglin.java:156-170, 258-263` |
| `sonic_boom` | `ai/behavior/warden/SonicBoom.java:19-90`；`monster/warden/Warden.java:219-224, 529-534`；`world/entity/EntityType.java:725`（`WARDEN_CHEST`）；`data/tags/DamageTypeTagsProvider.java:19-66` |
| `self_destruct` | `monster/Creeper.java:50-51, 130-155, 207-261`；`ai/goal/SwellGoal.java:19-51` |
| `head_strike` | `ai/behavior/RamTarget.java:69-119`；`animal/goat/GoatAi.java:38-57`；`animal/goat/Goat.java:119-131, 314-339`；`monster/hoglin/HoglinBase.java:15-53`；`monster/hoglin/Hoglin.java:121-125` |
| `roar` | `monster/Ravager.java:156-245` |
| `head_projectile` | `animal/horse/Llama.java:119, 343-366`；`projectile/LlamaSpit.java:63-73`；`animal/SnowGolem.java:51, 114-125`；`projectile/Snowball.java:53-59`；`monster/Blaze.java:201-241`；`projectile/SmallFireball.java:33-61`；`monster/Ghast.java:258-289`；`projectile/LargeFireball.java:29-48`；`monster/Shulker.java:566-620`；`projectile/ShulkerBullet.java:278-294`；`boss/wither/WitherBoss.java:101, 417-425`；`projectile/WitherSkull.java:56-97`；`projectile/DragonFireball.java:29-61`；`world/item/BottleItem.java:31-48`；`monster/breeze/Shoot.java:19-112`；`projectile/windcharge/BreezeWindCharge.java:22-38` |
| `guardian_beam` | `monster/Guardian.java:114-116, 359-440`；`client/model/GuardianModel.java:25-44` |
| `play_dead` | `animal/axolotl/Axolotl.java:313-327, 380-383`；`animal/axolotl/PlayDead.java:13-30` |
| `tongue_catch` | `animal/frog/ShootTongue.java:23-153`；`ai/sensing/FrogAttackablesSensor.java:11-21`；`data/loot/packs/VanillaEntityLoot.java:455-487` |
| `bartering` | `monster/piglin/PiglinAi.java:79-85, 443-451, 553-555` |
| `sniff_digging` | `animal/sniffer/SnifferAi.java:118-387`；`animal/sniffer/Sniffer.java:67-320` |
| `launch_strike` | `animal/IronGolem.java:187-205`；`boss/enderdragon/EnderDragon.java:313-325, 426-444` |
| `spellcasting` | `monster/Evoker.java:145-222`；`monster/SpellcasterIllager.java:176-232`；`projectile/EvokerFangs.java:98-132`；`monster/Witch.java:66, 108-178, 219-264` |
| `snow_trail` | `animal/SnowGolem.java:87-112` |
| `pollination` | `animal/Bee.java:921-975, 1052-1239` |
| 生产特性 | `animal/Cow.java:88-98`；`animal/MushroomCow.java:86-157`；`animal/goat/Goat.java:216-232`；`animal/Sheep.java:230-253, 337-343`；`ai/goal/EatBlockGoal.java`；`animal/Chicken.java:46, 93-98`；`animal/armadillo/Armadillo.java:68, 140-152, 301-326` |
| 现有特性补充 | `monster/Shulker.java:420-443, 492-506` |
