# 仿生生物近战机制

适用版本：Minecraft 1.21.1、NeoForge 21.1.234。生物外观与关节继续使用打包时保存的几何；战斗使用独立的服务端判定。

## 攻击范围

- `ArmAttackGeometry.origin` 是静态肩部位置，`reach` 是沿肩—肘—手端分段累加的有效臂长，已包含手端半径；攻击不会再次把半径加到最远距离上。
- 发起攻击前，身体须转到目标方向左右各 55° 内。瞄准中心的俯仰最多 ±60°，并受该手臂的装配活动范围限制；实际命中仍检查目标碰撞箱与范围的交集。距离检测单独保留，便于寻路接近身后目标并先转身。
- 命中扇区完全由肩到肘（无肘时为肩到手端）的大臂装配方向确定，目标位置只参与 AABB 相交检测，不会移动、旋转或拉伸扇区。纵向总张角固定为 90°：水平大臂覆盖上下各 45°；垂直下垂的大臂按极角 180°（Minecraft 俯仰 +90°）计算，覆盖水平面到正下方的 0°–90°，不能命中肩部水平面上方；垂直上举则对称覆盖 −90°–0°。中间姿态的扇区中心按大臂俯仰角的一半连续线性插值。该扇区再与装配活动范围、身体前半空间、肩部半径为 `reach` 的球体取交集。
- 判定直接使用目标 AABB：先裁切水平扇区，再检查可达水平距离与高度构成的俯仰扇区。目标仅部分进入范围也可命中；目标高度不会被换算成横向碰撞半径。
- 准备期间允许有限的转向和瞄准修正，接触阶段同时锁定世界方向与肩部旋转基准。实体移动仍会平移攻击区域，动画和头部转动不会改变它。
- 攻击还要求目标碰撞箱可见：从肩部向目标完整 AABB 的最近点进行方块射线检查。原版近战的眼睛感知门槛不参与出手，接触阶段会再次执行同一检查。
- 缺少手臂几何时使用身体中心发起的短距离攻击：距离为 `clamp(身体宽度 / 2 + 0.6, 0.75, 1.5)`，不使用手持武器。这个保底也适用于缺少几何的旧存档。

## 装配姿态特化

打包时保存每只手臂在身体坐标中的单位方向 `restDirection` 和 `hasElbow`：有肘时优先取肩到肘的方向，单段手臂取肩到手端的方向；肩肘重合时再用肩到手端。这个数据与肩部位置、臂长一起保存和同步，不取样待机、行走或攻击动画几何。

| 装配姿态 | 垂直活动范围（相对肩部水平线） | 水平活动范围 |
| --- | --- | --- |
| 下垂 | 向下 90°、向上 35° | 身体正前方 180° |
| 向前平伸 | 向下 50°、向上 50° | 身体正前方 180° |
| 上举 | 向下 35°、向上 90° | 身体正前方 180° |
| 向侧面平举 | 向下 50°、向上 50° | 向本侧偏置 20° 后钳制到前方，跨身一侧收窄为 70°，本侧保留 90° |

参数按装配单位方向连续插值，斜向装配没有分类跳变。纵向中心从水平大臂的 0° 线性过渡到垂直大臂的 ±45°；水平方向从前伸大臂的中心 0° 连续过渡到侧伸大臂的中心 ±55°，并乘以大臂单位方向的水平分量。这样越接近竖直的大臂越不受其微小侧倾的投影方位影响，真正水平侧伸时才取得完整侧向偏置。横向总张角保持 70° 并钳制在身体正前方。随后直接检查目标完整 AABB，因此目标仅部分进入固定扇区仍可命中。有效臂长和伤害不因姿态改变。

选臂会优先考虑装配方向与目标方向的匹配程度，低处优先下垂手臂、高处优先上举手臂，同侧目标优先侧伸手臂；恢复状态、武器与轮换仍参与选择。姿态匹配权重固定为 1.0，不受头部智力分类影响。

旧存档没有 `RestDirection` 时继续使用原有通用范围，身体攻击也保持通用规则。新打包的生物会保存装配朝向；姿态特化本身不增加攻速修正、恢复惩罚或动作时长。

## 频率与固定协调参数

原有手臂体积、粗细校准保留：体积决定基础输出，粗细使攻击在较快的小伤害与较慢的大伤害之间变化，解剖基础间隔为 10–40 tick。

| 参数 | 固定值 |
| --- | --- |
| 手臂恢复间隔系数 | 1.0 |
| 转身上限 | 10°/tick |
| 准备期间瞄准修正上限 | 12°/tick |
| 姿态匹配权重 | 1.0 |
| 单只标准僵尸手臂间隔 | 20 tick |

手臂恢复间隔是解剖基础间隔，至少 12 tick。全局间隔再乘以可用手臂协调系数：1 只为 1.0，2 只为 0.9，3–8 只为 0.8，最终仍至少 12 tick。协调计数保留特化前的条件：手臂已恢复、通用范围的方向和距离合适、路径没有方块遮挡；计数发生在装配姿态筛选之前，避免单纯改变姿态就改变频率系数。每次只选一只能够实际命中的手臂出手。若新装配范围允许攻击原先通用发起条件以外的高低位目标，协调计数至少按一只手臂处理。

智力仍通过 `bionic_head/intelligence` 数据包分类读取，多头取最高级，无头或未分类时为简单级，但当前只保留为分类信息：不修改伤害、范围、间隔、转身、瞄准或选臂权重。

## 攻击生命周期

1. 全局冷却结束、目标有效且可见时，选择符合条件的手臂。
2. 立即记录全局及该手臂的下次可用游戏时间。即使落空、被格挡、目标离开或 AI 中断，这些时间也不清零。
3. 无肘手臂以 `clamp(round(全局间隔 × 0.2), 3, 6) + 2` tick 定位窗口；有肘手臂按实际播放长度，将空手动画的第 15/25 曲线 tick 或武器动画的 0.5833/1.125 秒关键帧换算并四舍五入，再整体前移 4 tick。准备期间有限追踪，暂停主动追赶。
4. 接触窗口持续 5 tick，使用锁定方向判定。首次接触只调用一次伤害流程，即使伤害被盾牌、无敌帧或事件拒绝也不会重复调用。
5. 接触窗口结束后恢复追赶，等待全局与手臂冷却。换目标会取消旧动作，但不会把旧动作转移到新目标或刷新冷却。

攻击阶段按服务端游戏时间推进；AI 暂停后恢复不会补发已经过期的攻击。攻击过程中每 tick 重新检查目标存活、世界、当前目标身份、队伍及创造/旁观状态。站在攻击范围内时，寻路完成不会导致近战 Goal 不断退出重启。

客户端接收攻击开始、准备期间的追踪、方向锁定和取消事件；方向更新同时校正逻辑动作的剩余时间，不重启动画。无肘和有肘逻辑动作统一使用动画播放时长：快速攻击为 12–19 tick，标准及更慢攻击从起手到完全复位为 20 tick。三种动作的接触窗口均为 5 tick，仅位置不同；标准间隔下无肘位于第 6–10 tick、有肘空手位于第 8–12 tick、有肘持武器位于第 6–10 tick。手部轨迹以近竖直手臂攻击正前方等高目标的现有动作作为零偏移基准，末端轻度偏向所选手臂最终攻击扇区的角度中心；服务端命中不取样每帧手部位置，但有肘动作的基础关键帧位置来自对应动画。

## 攻击范围预览

预览与命中共用 `SlimeBionicCombat.attackRange`，使用静态肩部位置、真实臂长、装配活动范围和服务端同步的朝向，以连续的半透明球面扇区显示最终交集。曲面、侧面和轮廓一起裁切到身体前方，并补齐裁切面；不会用小方块把边界撑大。轮廓加深色衬边，两条稀疏截线帮助辨认立体范围，中心箭头也限制在可达区域内。

准备阶段为逐渐变亮的青蓝色，服务端确认进入 5 tick 接触阶段后变为更醒目的橙色；攻击结束或取消即撤掉范围。预览随准备期间的实际瞄准更新，接触阶段保持锁定方向，始终不取样手臂动画。曲面使用细分网格近似，方块遮挡仍由命中时的视线检查处理，范围面不代表穿墙攻击。

## 代码与验证

- 范围及阶段：`src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicCombat.java`
- 选臂、追踪、命中、冷却：`src/main/java/com/nobodiiiii/createbiotech/entity/SlimeBionicEntity.java`
- 体积与频率校准：`src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalCombatCalibration.java`
- 智力分类：`src/main/java/com/nobodiiiii/createbiotech/entity/ai/BionicIntelligence.java`
- 预览几何与绘制：`src/main/java/com/nobodiiiii/createbiotech/entity/client/SlimeBionicAttackRangeGeometry.java`、`SlimeBionicAttackRangeRenderer.java`
- 自动测试：`src/test/java/com/nobodiiiii/createbiotech/entity/SlimeBionicCombatTimingTest.java`、`src/test/java/com/nobodiiiii/createbiotech/entity/SlimeBionicCombatTest.java`、`src/test/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalCombatCalibrationTest.java`
- 预览测试：`src/test/java/com/nobodiiiii/createbiotech/entity/client/SlimeBionicAttackRangeGeometryTest.java`
- 姿态及兼容测试：`src/test/java/com/nobodiiiii/createbiotech/entity/SlimeBionicPostureCombatTest.java`、`src/test/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalArmPosturePersistenceTest.java`

运行 `gradlew test` 可检查方向、距离、部分相交、高大目标、肩部偏移、转向角度环绕、保底攻击、阶段边界、智力数值独立性与多臂频率上限；姿态测试还核对各姿态的高低位边界、前方 180° 限制、连续插值、频率不受姿态修正、旧存档与网络传输兼容，预览测试核对裁切面封闭性与真实命中区域的吻合，不启动游戏客户端。纸箱中的 DPS 使用全部手臂可用的固定协调参数，不受智力或装配朝向影响。

实现以项目自身的几何和 AI 为基础。核对原版近战 Goal、移动及转身调用顺序时，先检索了 `ref/`；其中缺少原版类，故使用当前构建生成的 `build/moddev/artifacts/neoforge-21.1.234-sources.jar` 内的 `net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java`、`net/minecraft/world/entity/Mob.java` 和 `net/minecraft/world/entity/ai/control/MoveControl.java`。

测试依赖配置参考 `ref/1.21.1/JustEnoughItems/Common/build.gradle.kts` 中的 `addModdingDependenciesTo`。该参考是 1.21.1 分支快照，使用 NeoForge 21.1.215；已另行核对本项目 ModDevGradle 2.0.141 提供的同名 API，实际测试使用本项目的 Minecraft 与 NeoForge 版本。
