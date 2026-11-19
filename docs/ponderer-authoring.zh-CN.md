# 思索场景与自动导出

21 组场景使用 Ponderer 的标准 **Java + Lib** 导出结构。`ponder/generated/` 和 `.ponderer-export/manifest.json` 由真实导出器生成；可以手改场景中的 `JavaScene` 数据调用，Java 实现放在独立扩展类中。

Java 输出使用直接参数调用，每个分段独立成方法，例如 `scene.idle(20)`、`scene.keyframe().showText(...)` 和 `scene.setBlock(...)`。步骤 ID 全部由 API 内部生成和保存，插入步骤无需编号；源码无需 `.at(...)`，也没有额外的 ID 对照资源。

| 内容 | 维护位置 |
| --- | --- |
| 可编辑场景源码 | `src/main/java/com/nobodiiiii/createbiotech/ponder/generated/scenes/*.java` |
| 历史迁移文档 | `examples/ponderer/scripts/*.json`，不再作为 Java 场景的本地覆盖加载 |
| 场景多语言文本 | `src/main/resources/assets/create_biotech/ponderer/texts/<场景>/<语言>.json` |
| 生成的 Java 文档、索引和客户端入口 | `src/main/java/com/nobodiiiii/createbiotech/ponder/generated/` |
| 自定义步骤注册入口 | `src/main/java/com/nobodiiiii/createbiotech/ponderext/PonderExtensions.java` |
| 黄油猫引擎、悦灵配送、压机、药水、步行动画和潜影打包机 | `ponderext/*Effects.java` |
| 场景范围 | Lib 根据结构和普通步骤坐标维护，无需额外的 Java 边界回调 |
| 播放器增强请求 | `ponderext/PonderClientFeatures.java` |
| 苦力怕压缩和机械压机专属实现 | `ponder/PonderSupportExt.java` |

`PonderExtensions.java` 只在首次导出时创建，后续导出不会覆盖；其他扩展类也不属于导出器管理范围。自定义步骤通过 `engine.steps().registerJava` 注册，编辑器显示中文名称和 Java 标记，保留调用位置并跳过 Java 实现解析。普通动作仍可编辑。

经验泵和悦灵驿站的玩家展示使用 Lib 的 `scene.createEntity("minecraft:player", ...)` 数据调用，显示当前观看者的玩家模型，位置、朝向与实体链接可在编辑器中修改。退场通过相同 `linkId` 的 `scene.clearEntities(...)` 执行，保留 10 tick 向下退场和原有等待时序；无需自定义 Java 步骤或单独保存玩家元素链接。玩家参与普通实体清理，避免在退场前先用全场清理将其立即移除。

经验泵入口的经验球仍由普通 `createEntity` 步骤放置，链接为 `experience.input_orb`；`ExperiencePumpOrbEffects` 的 `orbit_input` 步骤在后续 60 tick 内以该位置为中心，按半径 0.2 格、60 tick 一圈的水平轨迹运动，持续到经验球被清除。出口用二十个可编辑的 `createEntity` 步骤，每隔 5 tick 喷出一球，覆盖对应文本的完整 100 tick；通过 NBT 的 `Motion` 设置向外且略向上的初速度、`NoGravity:0b` 恢复自然下落，总等待时序保持不变。场景中两次展示的双层储罐均装入 16000 mB 经验，单格储罐装入 8000 mB，使用 1.21.1 的流体 NBT 和场景内控制器坐标。动画依据本地 `ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/instruction/TickingInstruction.java` 与 `foundation/PonderScene.java` 的非阻塞指令及逐 tick 调度，物理依据 `ref/1.21.1/Minecraft/net/minecraft/world/entity/ExperienceOrb.java`；储罐容量与读取方式参见 `ref/1.21.1/Create/src/main/java/com/simibubi/create/infrastructure/config/CFluids.java` 和 `content/fluids/tank/FluidTankBlockEntity.java`。

思索中普通经验球与储罐经验球的面向由 Ponderer Lib 的 `core` 统一适配：整段场景渲染期间使用思索场景相机的朝向，覆盖普通实体与方块实体，结束后恢复主游戏相机朝向。更新 Lib 后生效，修改经验球的 yaw/pitch 无法替代此修复。`FluidTankExperienceOrbRenderer` / `ExperienceOrbModelRenderer` 保留游戏内与思索共用的储罐经验球数量、游动和绘制逻辑，不再包含思索专用的相机计算分支。

生成 Java 使用 `scene.showText(text("scene_1.text_1"), ...)` 引用文本，正文和标题分别维护在各场景的 `zh_cn.json` / `en_us.json` 中，不再合并进模组总语言文件。已有文本键在调整步骤顺序时保留；手写内容可使用语义键。Lib 负责语言选择、英文回退、资源包覆盖和重载。编辑器直接写回目标工程的 Java 与独立文本资源。

文本取值 Mixin 随新版 Lib 分发并由导出能力声明启用，无需给本模组添加 Mixin 配置。资源包可覆盖单个场景的单个语言文件；该文件整体替换，缺少的条目回退到英文。署名分类的中英文资源统一由 Lib 提供。

## 手术台基础思索

`create_biotech:surgical_table` 已实现“放置供体”“切分与重组”“安装关节”“特性介绍”四章，分别使用稳定分段 ID `placing`、`recombining`、`joints`、`traits`。它们绑定手术台、手术盒、关节和手术指南；各章独立建立起始状态，第二章从供体已放置开始，第三章从手臂已替换开始，第四章使用苦力怕和幻翼组成的示例。后续章节可以继续追加到同一文档的 `document.scenes`。

- 场景源码：`src/main/java/com/nobodiiiii/createbiotech/ponder/generated/scenes/GeneratedSurgicalTable_7431e38e.java`。
- 中英文字幕：`src/main/resources/assets/create_biotech/ponderer/texts/surgical_table/`。
- 编译扩展：`ponderext/SurgicalTableEffects.java`；模型、台面摆放与成品骨架由 `content/surgery/client/SurgicalPonderActors.java` 管理。
- 共用结构：`assets/create_biotech/ponder/generated/create_biotech/surgical_table/basic_xl.nbt`；可用 `python tools/generate_surgical_ponder_structure.py` 确定性重建。

第一章开场使用随场景打包的 `surgical_table_intro.nbt`。第三章的僵尸成品使用 `surgical_table_zombie_golem.nbt`，跳跃演示沿用绿图里的部件、摆放和粘接，并省去可动关节。第四章的台面模型和演示指令位于 `SurgicalTraitPonderActors.java` 和 `SurgicalTraitPonderEffects.java`。第四章成品使用苦力怕幻翼绿图，特性说明由 Ponder 原生字幕和操作提示呈现。

扩展使用场景独占的供体模型、当前解剖快照和真实装配骨架演示，不操作玩家世界中的手术台或共享预览。装箱后仅隐藏已连接的成品，旧手臂和剩余供体留在台面。第四章的体块从供体原位向外分开并落到台面，保留大致的左右和前后关系。未参与组装的散件在成品起飞和爆炸时留在台面；爆炸演示后通过独立的 `traits_return_to_table` 步骤，让成品从上方淡入台面，同时让散件向上淡出，两段动画均为 20 tick。后续装箱与关节演示不再显示这些散件，重播时恢复。工具轮盘复用 `SurgicalKitRadialScreen`，成品属性来自当前装配的实际计算结果。模型与覆盖层通过播放指令加入元素列表，不能在编译故事板时直接加入，否则会被 `PonderScene.begin()` 清除。三章在初始化时统一将整体视角绕 Y 轴转 180°；重播时重新注册元素并重置部件状态、动画计时和覆盖层。

原生覆盖层与逐 tick 指令的参考为 `ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/api/element/PonderSceneElement.java`、`api/element/PonderOverlayElement.java` 和 `foundation/instruction/TickingInstruction.java`。该 Ponder 参考为 1.21.1 开发分支快照，API 已通过项目锁定的 Ponder 1.0.82 与 Ponderer Lib 0.1.0 编译检查。

## 编辑与再次导出

1. 同时安装当前版本的完整 Ponderer、Ponderer Lib 和本模组，执行 `/ponderer java bind D:\Files\create_biotech` 绑定源码工程。之后可在思索列表或物品的思索界面中编辑 `create_biotech:*` 场景；未绑定工程时只读，不需要重新导入旧文档。
2. 编辑器保存直接更新目标 Java、文本资源和清单，并刷新本次会话。手改 `JavaScene` 调用增删步骤后，在 V 界面点击“重载”；不通过 tick 监听。修改 `*Effects.java`、`PonderSupportExt.java` 等 Java 实现后，需要重新编译并启动客户端。
3. 使用 **Java + Lib** 导出到本项目根目录 `D:/Files/create_biotech`，选择已迁移的 `create_biotech:*` 场景。场景 ID 与 `runtimeSource` 保持一致，分段 ID 保持稳定；步骤 ID 由 API 管理，无需手写。
4. 临时场景覆盖在退出游戏时释放，Java 源码保留。下次启动使用模组已编译的内容；重新编译并加载新 JAR 后，修改才随模组生效。历史 JSON 留作迁移记录，不再参与 Java 场景重载。

场景 ID 统一为 `create_biotech:*`，故事板路径、物品绑定、已有结构资源、中英文本和 Create 分类保持对应。生成类名末尾的标识随场景命名空间变化，由导出器生成；请通过 `GeneratedPonderIndex` 注册，不要直接引用某个生成类。

完整再次导出仍会检查手改冲突。手改数据调用后，可先通过编辑器确认保存，由保存流程更新清单；任意 Java 实现应放在 `ponderext`，不要手动修改 manifest 快照绕过保护。其他导出模式有不同的运行方式；本项目中的 Java 扩展以 Java + Lib 为维护入口。

## 音效维护

只为演示中实际发生、且能从玩法实现或对应版本原版源码确认有声音的动作补音效。优先使用可编辑的 `scene.playSound(...)` 数据调用；自定义 Java 步骤内部发生的动作使用 `PonderActions.playSound(...)`，与动作同一时间执行，不增加等待时间。

| 演示动作 | 已确认的音效与依据 |
| --- | --- |
| 生物打包机、恶魂装配站和驾驶台场景中拉下拉杆 | `minecraft:block.lever.click`，音量 `0.3`、音高 `0.6`；`ref/1.21.1/Minecraft/net/minecraft/world/level/block/LeverBlock.java` 的开启音效。 |
| 培养皿放入仿生构件 | `minecraft:entity.item_frame.add_item`，音量 `0.5`、音高 `1.0`；`content/petridish/PetriDishBlockEntity.java` 的 `use`。 |
| 培养皿场景搅拌活体史莱姆 | 沿用 Create 的 `MIXING` 双层音效：`minecraft:block.gilded_blackstone.break` 和 `minecraft:block.netherrack.break`，每层音量 `0.75 × 0.125`、音高 `0.5`。搅拌头下降 20 tick 后，每隔 2 tick 播放一次，共 23 次，保留原有 70 tick 等待；依据 `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/kinetics/mixer/MechanicalMixerBlockEntity.java` 的 `tickAudio` 与 `AllSoundEvents.java` 的 `MIXING`。 |
| 培养皿开始出芽、完成生成 | `minecraft:entity.slime.jump`（`0.8 / 0.9`）、`minecraft:block.slime_block.place`（`0.7 / 0.9`）；`content/petridish/PetriDishCultivation.java` 的 `beginEmergence` / `finishEmergence`。 |
| 固定钓竿放入小麦诱饵 | `minecraft:entity.item_frame.add_item`，音量 `1.0`、音高 `1.0`；`content/fixedcarrotfishingrod/FixedCarrotFishingRodBlock.java` 的 `interact`。 |
| 鱿鱼打印机和附魔室流水线开始附魔 | `minecraft:entity.evoker.prepare_summon`，音量 `0.7`、音高 `1.0`；沿用 `content/evokerenchantingchamber/EvokerEnchantingChamberBlockEntity.java` 的 `tryInsertFromPlayer` 中已有的准备声，在思索切换到施法状态时播放。 |
| 鱿鱼打印机和附魔室流水线完成附魔 | `minecraft:block.enchantment_table.use`，音量 `0.85`、音高 `1.0`；`content/evokerenchantingchamber/EvokerEnchantingChamberBlockEntity.java` 的 `completeCasting`。 |
| 蜘蛛装配站的六次机械手装配 | 与 `content/spiderassemblytable/SpiderAssemblyTableBlockEntity.java` 的 `playMachineSound` 一致，播放 `minecraft:entity.item.pickup`（音量 `0.25`、音高 `0.75`）；依据 `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/kinetics/deployer/BeltDeployerCallbacks.java` 的装配完成音效，不叠加机械合成器的 `CRAFTER_CLICK`。玩法中材料耗尽或工具损坏时额外播放 `minecraft:entity.item.break`（`0.25 / 1.0`），思索中每个材料槽有 64 个物品，无需该音效。每轮 32 tick 的加工在第 16 tick 接触工件时播放，将原等待拆为 `16 + 4`，总时序保持不变。 |

表中的 `content/` 路径均相对于 `src/main/java/com/nobodiiiii/createbiotech/`。保留已有的传送、苦力怕爆炸、鱿鱼脱水音效。生物打包机动画已有客户端声音调用，不再手工叠加；只有操作提示、场景入场、相机运动、结构展示或没有明确音效依据的动作保持静音。蜘蛛装配场景依据现有仿生构件配方和槽位内容，六步均按机械手处理；其旧 `ActiveMachine` 值误用了槽位序号，已统一修正为机械手枚举值 `0`，仅调整思索数据。

培养皿的再生药水演示按设计保持静音。药水消失后保留一次喷溅粒子，并在村民周围每 tick 继续生成一个再生效果粒子，持续剩余的 278 tick，覆盖鼓风机缠魂、转化和最后的文字展示。粒子使用非阻塞的 `emitParticles`，不改变场景等待时序；对应实现为 `ponderext/PetriDishEffects.java`。

## 运行库与构建

开发依赖直接读取本项目 `libs/` 中的 JAR，不使用本地 Maven、相邻工程的 Maven 仓库或 `publishToMavenLocal`。版本号由 `gradle.properties` 中的 `ponderer_lib_version` 和 `ponderer_version` 决定：

- `libs/pondererlib-1.21.1-neoforge-0.1.0.jar`：编译和运行依赖，不通过 JarJar 内嵌。
- `libs/ponderer-1.21.1-neoforge-1.12.0.beta1.jar`：开发运行时使用的完整编辑器。

两份 JAR 均由 `syncQuickPlayMods` 暂存到 `build/quickplay/mods`，正常 quickPlay 构建与复制流程会同步到开发实例。`libs/*.jar` 为本机开发产物，不纳入 Git。

```powershell
# 关闭游戏后，在本仓库运行相邻 Ponderer 仓库的同步脚本
python ..\ponderer\sync_biotech.py

# 游戏运行期间，仅更新开发依赖与暂存目录
python ..\ponderer\sync_biotech.py --skip-instance
```

脚本只构建当前 Ponderer 与 Lib、同步版本号和 `libs/`，直接更新 `build/quickplay/mods/` 及实例中的编辑器与 Lib；不执行本工程的 Gradle 任务，也不替换 Biotech 本体。默认离线，构建限时 900 秒；`--online` 允许下载依赖，`--biotech` 和 `--instance` 可指定其他路径。完整用法见 Ponderer 仓库的 `docs/SYNC_BIOTECH.md`，构建日志位于该仓库的 `build/biotech-sync/`。

安装时单独放置与当前导出 API 兼容的 `pondererlib-1.21.1-neoforge-0.1.0.jar`。完整 Ponderer 是编辑器，可选；上游 Ponder 仍由宿主现有依赖提供。能力声明位于 `META-INF/pondererlib.features.properties`，播放器增强请求的所有者为 `create_biotech`，用户的功能开关优先。

思索半透明场景区块的延后绘制修复现由 Ponderer Lib 的 `core` 提供，本模组不再携带 `client.WorldSectionElementImplMixin`。更新开发依赖时需要同步包含此修复的 Lib JAR。

参数调用形式依赖新版 Lib 的 `JavaScene` API，旧 `.at(...)` 接口已移除。更新时同时替换 `libs/` 中的 Lib 与编辑器，再执行暂存任务；运行客户端时也需要使用对应的新 JAR。本次自动 ID 改动前的生成代码和源文档备份在 `build/ponder-internal-ids-before.zip`；更早的参数格式调整备份在 `build/ponder-parameters-before.zip`。

拆分独立文本资源前的生成源码、源文档、语言文件、扩展与清单备份在 `build/ponder-text-resources-before.zip`。本次 20 组场景的源 JSON 已同步改用文本引用，需与 `ponderer/texts` 资源一起维护或加载。

迁移前的自定义 Java、manifest、语言文件及能力资源备份在 `build/ponder-migration-before.zip`。黄油猫引擎现已迁入 Java + Lib；智能强力胶仍沿用独立故事板，存档格式保持不变。

智能强力胶通过 `create` 命名空间直接注册 `ChassisScenes::superGlue`，标题和正文沿用 Create 的 `create.ponder.super_glue.*` 语言键。不要对这个原生故事板调用 `RuntimeScenes.adopt`：Lib 的文本 Mixin 会将已接管场景的标题和正文视为已翻译文本，从而绕过 Create 的语言键并显示源码中的英文。对应中文可查 `ref/1.21.1/Create/src/main/resources/assets/create/lang/zh_cn.json`。

## 黄油猫引擎

`create_biotech:butter_cat_engine` 使用同一套 Java + Lib 注册与编辑流程，保留原来的猫入场、纸箱捕获、传动杆装猫、面包、六次添加黄油和超级黄油演示，以及两处关键帧和 770 tick 的等待时序。中英文本已迁到 `ponderer/texts/butter_cat_engine/`，旧手写故事板与总语言文件中的旧思索键已移除。

`ButterCatEngineEffects` 提供初始化、添加黄油并提高转速、添加超级黄油三个 Java 步骤。普通实体、操作提示、方块切换、仪表数值和提示特效仍是可编辑的数据调用；扩展仅保留基座设置、原有 `addButterCount` / `setInfinite` 调用和 Create 转速更新。累计次数放在每次场景构建独立的 `StepBuildContext.state` 中，重播和关键帧重建不会继承上次计数。

该场景通过本项目 `libs/` 中的真实思索者导出器生成，清单与索引由导出流程更新。新版索引在插件构造时调用 `prepare()`，因此旧的 19 个场景同步补上预加载入口，原有场景数据调用保持不变。迁移前备份位于 `build/ponder-butter-cat/before.zip` 和 `prepare-hooks-before.zip`。

## 手术台前三节的演示实现

第二章的操作提示、等待和相机设置直接写在 `JavaScene` 中；高亮通过独立、可编辑参数的 `highlight_parts` 步骤调用。剪下手臂、移动手臂、剪下双腿、移动双腿和固定部件各自使用单一的 Java 模型步骤，不在其中夹带提示或等待。铁傀儡手臂剪下后保持 Z 不变，沿 −X 移开并落到台面；随后按原目标粘接到僵尸肩部。第二章统一按“剪刀与红色高亮 → 强力胶与绿色高亮散件 → 强力胶与绿色高亮目标 → 移动到位后同时绿色高亮连接双方”演示。各次操作提示与高亮持续 40 tick，随后留出 10 tick 间隔；粘合移动本身持续 40 tick，到位后再开始结果高亮。双腿的剪切、选点、移动和结果高亮均同时进行，操作提示放在两腿中间。

第三章的装箱、放出和关节安装提示使用可独立编辑的 `showControls` 与 `idle` 步骤。回收成品时用 `clear_mimic` 清除演示拟态实体，放回台面后用 `highlight_parts` 的 `assembled_body` 选中所有已装配的体块，包括铁傀儡手臂与掠夺者小腿。

`GeneratedSurgicalTable_7431e38e` 保持 `placing`、`recombining`、`joints` 三个稳定章节 ID；原有大纲及中英文字幕资源保持原文。后续章节继续由作者补充。

底板直接收录思索者自带的 XL 模板 `basic_xl`（9×9），与 `../ponderer/Common/src/main/resources/data/ponderer/default_structures/basic_xl.nbt` 字节一致。`tools/generate_surgical_ponder_structure.py` 仅导入该原生模板，台面的放置和分段显示由可编辑 `JavaScene` 步骤完成。僵尸、铁傀儡、掠夺者分别放在台面左、中、右侧；铁傀儡手臂竖直连接躯干，掠夺者双腿接在僵尸双腿下方，最后分别安装膝关节。

红石外观演示之后始终使用供体原生外观。成品使用实际 `SlimeBionicEntity` 的移动控制器、跳跃控制器、地面碰撞和 `SlimeBionicAnimator`，仅由演示提供往返目标点及攻击触发时机。客户端预览的生命、移速、攻击属性使用与服务器相同的校准函数；不再通过渲染时的正弦平移模拟行走或跳跃。

手术台使用原生 `interface_panel` 步骤，内含按界面 ID 打开的 `show_interface`。工具轮盘、关节轮盘和纸箱属性页的 ID 分别为 `create_biotech:ponder_surgical_tool_wheel`、`create_biotech:ponder_surgical_joint_wheel`、`create_biotech:ponder_surgical_box_stats`。轮盘页显示中心 144×144 区域。第三章在纸箱属性页打开后，通过 `pack_result_for_stats` 把场景当前成品装进演示纸箱，再填入属性页的槽位。`META-INF/pondererlib.features.properties` 通过 `interfaces` 启用界面沙盒，页面及动画状态随场景回放重建。

第四章在补装肩关节后、再次装箱前，用 `create_biotech:ponder_surgical_box_hover` 演示手持空纸箱悬停检查。`traits_preview_box` 读取装好双翼的幻翼苦力怕的实际特性，沿用手术台提示的操作文案。

依赖接口已针对项目使用的 Ponder 1.0.82、Lib 0.1.0 编译确认。`ref/1.21.1/Ponder/` 是开发分支快照，生命周期参考 `foundation/PonderScene.java` 和 `foundation/instruction/TickingInstruction.java`；它不是 1.0.82 的精确发布源码。

`create_biotech:java/surgical_table/highlight_parts` 是可编辑参数的独立自定义步骤。`parts` 选择部件，`color` 选择颜色，`duration` 指定持续时间。`highlight_zombie` 描出第一幕台面上僵尸的可见体块。`SurgicalPonderHighlight` 从模型当前的角点建立各体块的局部坐标系，再由 Ponder 场景的 `Outliner` 和 `AABBOutline` 沿体块边缘绘制。

成品朝向与 `SlimeBionicRenderer.BodyFrame` 一致，使用 `-yaw`。第一幕由 `result_intro` 让成品从 `(1.5, 1, 4.5)` 入场，走到 `intro_target` 盔甲架旁持续挥击，再由 `hide_result` 开始退场。成品的入场和退场直接跟随盔甲架的竖直位移，手术台在两者退场后出现。关节幕和特性幕的成品从 `(1.5, 1, 7.5)` 朝 `+X` 出场，盔甲架位于 `(7.5, 1, 7.5)` 并朝 `−X`。关节幕在抵达盔甲架前并落地后触发一次撞击或挥击。特性幕的成品和盔甲架同时出现，成品飞到盔甲架旁后点燃引信。闪白沿用仿生生物的自爆渲染，引信声和爆炸声由场景步骤播放。
