# Changelog

## 2.0.1

### 中文

### 新增功能

- **吞噬者**有概率代替自然生成的史莱姆。它会跳起压住猎物，吞噬后长出可拆装的拟态组织并继承符合条件的供体特性。体型足够大时也能吞入玩家，玩家可从内部攻击挣脱。
- **吞噬特性**可由仿生生物继承，需要装配吞噬者的内层和外层组织，并让外层接地。
- **动力捕捉网**可用空捕捉网右击动力臂改装，配合正下方的生物打包机自动捕捉和装箱。网兜可以更换，空手潜行右击可取回网兜并还原动力臂。
- **装有生物的纸箱**可通过普通打包机接入 Create 物流网络，支持库存链接、工厂仪表和按地址发货。仓库界面使用生物模型展示纸箱，并可展开同种生物的库存。
- **手术盒与创造手术盒**可放置在世界中。附近玩家可右击或按住快捷键打开轮盘，选取或收起工具。

### 改进

- **生物打包机**将捕捉成品送回相连库存。它可直接接收装有生物的纸箱，也可用红石信号从库存取箱，再从机器上方释放生物。空纸箱会送回库存，库存无法接收时会掉在机器旁。
- **仿生移动与战斗**按身体体积换算等效史莱姆尺寸，用于计算跳动速度和碰撞伤害。纸箱的属性面板采用相同的基础数值计算。
- **供体体质与力量**改为先折算成等效体积，再计算生命上限和挥臂伤害。史莱姆、岩浆怪和吞噬者的供体体质随实际尺寸计算。
- **资源**精简方块模型状态，按设备整理模型、部件和贴图路径。覆盖旧路径的资源包可查阅[路径对应表](docs/resource-layout.zh-CN.md)。

### 修复

- 补齐手术台思索遗漏的英文字幕，并按中文修正关节、装箱和特性提示。
- 补齐史莱姆培养皿、恶魂驾驶台和恶魂热气球装配站的掉落。
- 修复装箱时因选中的部件不同导致成品朝向不一致的问题。
- 统一捕捉网、纸箱和动态结构的致死装箱判定，以扣除吸收生命值后的伤害为准。
- 修正潜影打包机侧面输出选择框的位置。
- 修复智能强力胶的耐久附魔兼容性。

### English

### New Features

- Added Devourers, which can appear in place of naturally spawning slimes. They leap onto prey and grow detachable mimic tissue after devouring it, inheriting eligible donor traits. Large Devourers can swallow players, who can fight their way out from inside.
- Added a heritable Devour trait for bionic creatures. It requires both inner and outer Devourer tissue, with the outer layer touching the ground.
- Added Powered Capture Nets. Right-click a Mechanical Arm with an empty Capture Net to convert it, then pair it with a Bio Packager directly below to catch and box creatures automatically. Fitted nets can be swapped, or retrieved by sneak-right-clicking with an empty hand to restore the arm.
- Integrated captured-creature boxes into Create's logistics network through standard Packagers, with support for Stock Links, Factory Gauges, and addressed deliveries. The stock-keeping screen displays creature models and lets players expand groups by creature type.
- Made Surgical Kits and Creative Surgical Kits placeable. Nearby players can right-click the kit or hold the hotkey to open its radial menu and select or stow tools.

### Improvements

- Bio Packagers deposit boxed creatures into attached inventories. They can accept a filled box directly or retrieve one from the inventory with a redstone signal, then release the creature above the machine. Empty boxes return to the inventory or drop by the machine if there is no space.
- Bionic hopping speed and contact damage now scale with the body's equivalent slime size, calculated from its volume. Captured-creature tooltips use the same base stat calculations.
- Donor health and strength modifiers now contribute equivalent volume to the maximum-health and arm-damage calculations. Health modifiers for Slimes, Magma Cubes, and Devourers scale with their actual size.
- Simplified block model states and organized models, parts, and textures by device. For pack overrides, see the [resource path changes](docs/resource-layout.zh-CN.md).

### Fixes

- Restored missing English captions in the Surgical Table Ponder and corrected the hints for joints, boxed creatures, and traits to match the Chinese text.
- Restored drops for the Petri Dish, Ghast Helm, and Ghast Hot Air Balloon Assembly Station.
- Fixed the packed body's orientation changing depending on which part of the assembly was selected for packing.
- Made lethal-hit boxing with nets, boxes, and contraptions consistently account for damage blocked by absorption.
- Corrected the position of the Shulker Packager's output selection box.
- Fixed Smart Super Glue's compatibility with durability enchantments.

---

## 2.0.0

### 中文

四轮测试版主要成果：

- 手术台与仿生组装：拆解、重组生物，用关节驱动身体。
- 供体特性与附身：继承生物能力，支持玩家拟态、附身及特性查询与扩展。
- 六种捕捉网：捕捉、搬运生物，并与纸箱和手术台联动。
- 教程与体验完善：扩充装饰和思索，改进经验、物流、渲染及存档兼容。

### 新增功能

- **手术台思索**新增放置供体、切分与重组、安装关节、特性介绍四章动态教程。JEI 特性查询入口改为手术台。
- **绿图与绿图桌（开发中）**支持记录并重复释放拟态或仿生生物副本。绿图桌可导入、导出本地 `.nbt` 文件，导入实体快照需要创造模式。

### 改进

- **仿生飞行**：没有有效腿部的飞行仿生生物会低空悬停并游荡，改善无腿身体的自主移动。
- **仿生动画**：完善供体原生动画与组装关节的衔接，音爆动作按实际肩关节播放，持械位置随动作同步。
- **依赖与分发**：Ponderer Lib 升级至 0.1.1；CurseForge 版本需单独安装该前置，Modrinth 版本内嵌该库。

### 修复

- 修复含有单独旋转体块的部件再次粘合时被错误拒绝，以及仅边或顶点接触的连接被误判的问题。
- 修复不规则手术台将部件间空隙计入占用范围，导致放置或粘合时误报空间不足的问题。
- 修复短粗或侧接肢体的肩、髋关节转轴定位错误的问题。
- 修复回放导出时手术台部件渲染准备未刷新而导致的显示异常。

### English

Highlights from the four beta releases:

- Surgery and bionic assembly: dissect and recombine creatures, then animate their bodies with joints.
- Donor traits and possession: inherit creature abilities, mimic players, possess mimics, and look up or extend traits.
- Six Capture Net tiers: capture and carry creatures, with box and Surgical Table integration.
- Tutorials and polish: expand decorations and Ponder scenes, improving experience systems, logistics, rendering, and save compatibility.

### New Features

- Added four animated Surgical Table tutorial chapters covering donor placement, dissection and recombination, joints, and traits. JEI trait lookup now starts from the Surgical Table.
- Added Greenprints for recording and repeatedly spawning copies of mimics or bionic creatures. The Greenprint Table imports and exports local `.nbt` files. Importing entity snapshots requires Creative mode. Both remain under development.

### Improvements

- Added low-altitude hovering and wandering for flying bionic creatures without functional legs, improving autonomous movement for legless bodies.
- Improved how donor animations follow assembled joints, with Sonic Boom arm motions using the installed shoulders and held items following the animated pose.
- Updated Ponderer Lib to 0.1.1. CurseForge builds require a separate installation of the library; Modrinth builds bundle it.

### Fixes

- Fixed glue operations rejecting components with individually rotated cubes, and connection checks rejecting edge-only or point-only contact.
- Fixed placement and glue previews on irregular Surgical Tables counting gaps between cubes as occupied space and incorrectly reporting insufficient room.
- Fixed shoulder and hip pivot placement for compact or side-mounted limbs.
- Fixed Surgical Table rendering during replay exports when render preparation was not refreshed.

---

## 2.0.0-beta.4

### 中文

### 新增功能

- **仿生特性**：叮咚鸡供体支持产蛋与扑翼缓降；叮咚鸡本体和对应仿生躯干有概率产出金粒、金锭、金块或呼唤铃，产蛋说明会列出实际保留的供体来源。
- **捕捉网**：新增木、石、铁、金、钻石和下界合金六种捕捉网，可反复捕捉、携带和释放单个生物；按当前血量判定捕捉条件与挣脱时间，显示网内生物、挣扎动画和倒计时。
- **捕捉网联动**：支持横扫攻击与致死时消耗背包纸箱自动装箱，可将网内拟态生物直接放上手术台；拟态生物不受捕捉血量上限限制。
- **潜影物流输出**：潜影打包机可选择机械臂交互目标输出包裹，沿用目标的插入规则与容量检查。

### 改进

- **经验泵**：统一使用 Create 管网的压力、分流与堵塞规则，支持直接连接储罐和开放出口；吸收整件物品后的多余经验会缓存保存，低速或堵塞时不再浪费输入。
- **经验母岩**：默认四阶段经验值调整为 24、48、96、192，与粉碎产出一致；多余灌注保留到同一面的下一轮生长，并兼容旧存档进度与自定义配置。
- **思索教程**：重制 20 组现有场景，完善黄油猫、经验泵、悦灵物流、潜影物流等动态演示，以及万向节连接和经验球显示。
- **生物捕捉**：纸箱与捕捉网会在捕捉前安全解除骑乘、乘客及拴绳关系，并尊重其他模组的下坐骑限制。
- **音效反馈**：完善仿生守卫者光束蓄力、潜影传送站闭合与缓冲垫碰撞音效，调整多种机器的音量并减少重复播放。
- **恶魂热气球**：完善第三人称驾驶视距，装配期间静音恶魂、拆卸后恢复原状态，并修正装配站放置和读档后的红石状态同步。
- **渲染性能**：优化完整拟态模型的几何恢复与材质判定，减少重复计算；补充蜘蛛组装台套壳和机械部件的 Flywheel 实例化渲染。
- **依赖兼容**：新增 Ponderer Lib 0.1.0 前置，放宽可选 KubeJS 的最低版本至 2101.7.1-build.181。

### 修复

- 修复悦灵驿站自动化从错误方向存取包裹、空地址误匹配及载体库存变化未及时保存的问题。

### English

### New Features

- Added egg laying and wing-assisted slow falling for Ding-Dong Chicken donors. Both living chickens of this type and their bionic torsos can produce Gold Nuggets, Gold Ingots, Gold Blocks, or Desk Bells, with retained donors listed in egg-laying details.
- Added Wooden, Stone, Iron, Golden, Diamond, and Netherite Capture Nets for repeatedly capturing, carrying, and releasing one creature. Capture limits and escape time use current health, with visible contents, struggle animations, and a countdown bar.
- Added sweeping attacks and lethal-hit boxing using empty boxes from the inventory, plus direct placement of netted mimics on Surgical Tables. Mimics bypass capture health limits.
- Extended Shulker Packager outputs to Mechanical Arm interaction targets, respecting their insertion rules and capacity checks.

### Improvements

- Unified Experience Pumps with Create pipe-network pressure, branching, and blockage handling, including direct tank connections and open outlets. Excess experience from whole items is saved in a persistent buffer to avoid wasting inputs at low speeds or during blockages.
- Changed default experience growth stages to 24, 48, 96, and 192 XP to match crushing yields. Excess infusion is retained for the same face's next growth cycle, with support for old save progress and custom settings.
- Reworked 20 existing Ponder scenes, improving animated demonstrations for Butter Cats, Experience Pumps, Allay and Shulker logistics, alongside joint connections and experience-orb displays.
- Made boxes and nets safely detach mounts, passengers, and leashes before capture, respecting other mods' dismount restrictions.
- Improved bionic Guardian beam charging, Shulker Teleporter closing, and Buffer Pad impact sounds, adjusted machine volumes, and reduced repeated playback.
- Improved third-person balloon driving distance, silenced Ghasts during assembly with their original state restored after dismantling, and corrected assembly-station redstone synchronization after placement and reloading.
- Optimized geometry recovery and material checks for intact mimics to reduce repeated calculations. Added Flywheel instancing for Spider Assembly Table casings and mechanical parts.
- Added Ponderer Lib 0.1.0 as a required dependency and lowered the optional KubeJS minimum version to 2101.7.1-build.181.

### Fixes

- Fixed Allay Post automation accessing packages from incorrect sides, blank addresses matching incorrectly, and carrier inventory changes not being saved promptly.

---

## 2.0.0-beta.3

### 中文

### 新增功能

- **仿生特性**：扩充供体组织能力，加入骑乘、驮箱、持械、远程攻击和生产等玩法，完善移动、防御、驯服及组织判定规则。
- **特性查询**：新增 JEI 特性说明与供体列表，可从刷怪蛋和捕获纸箱反查特性；手术台预览可查看生效状态与缺失条件。
- **特性与属性扩展**：支持通过数据包、KubeJS 和 Java 扩展特性、部位、行为及基础数值公式。
- **手术台调整**：扳手可平移、旋转单个体块或整个连通部件，并支持整体摆正；扳手和智能强力胶均可预览、确认或放弃调整。
- **临时纸箱定向放置**：可从六个落地面朝向中选择部件的放置方向。
- **统一关节**可通过轮盘切换颈、肩、肘、髋或膝五种类型。
- **创造手术盒**新增附身入口和部件复制工具。
- **潜影传送站地址展示**：可通过工程师护目镜查看目标地址，并可将地址输出到显示链接。

### 改进

- **手术台操作**完善连接检查、失败原因提示与操作引导。剪刀可拆卸并回收关节。
- **手术台性能**：减少模型、体积和装箱预览的重复计算，优化复杂身体的准备与同步。
- **存档兼容**：精简手术台与仿生组装数据的重复存储，支持已发布版本存档的加载转换。
- **生物机器渲染**：统一物品、JEI、世界与 Flywheel 中的模型表现，修复重复绘制、界面预览缺失及苦力怕特效异常。

### 修复

- 修复蜘蛛组装台缺料时放走序列组装工件的问题，等待补料时正确检查物品、流体锁定及所需数量。
- 修复拟态生物死亡或放上手术台时，实际携带的装备、鞍及容器内容可能丢失的问题。
- 修复配置尚未加载时查询黄油食物属性或纸箱渲染设置的问题。

### English

### New Features

- Expanded bionic donor abilities with riding, cargo chests, weapons, ranged attacks, and production, alongside refined movement, defense, taming, and tissue evaluation.
- Added JEI trait descriptions and donor lists, trait lookups from spawn eggs and captured-creature boxes, and active/missing-condition details in surgical table previews.
- Added data-pack, KubeJS, and Java support for extending traits, body parts, behaviors, and base-stat formulas.
- Added wrench translation, rotation, and straightening for individual cubes or connected parts, with preview, confirmation, and cancellation for wrench and Smart Super Glue edits.
- Added temporary-box placement with six selectable grounding orientations.
- Added a unified Joint item with a radial selector for neck, shoulder, elbow, hip, and knee types.
- Added possession access and part copying to the Creative Surgical Kit.
- Added Shulker Teleporter target-address readouts through Engineer's Goggles and Display Links.

### Improvements

- Refined surgical connection checks, failure messages, and controls. Shears can remove and return joints.
- Reduced repeated model, volume, and packing-preview calculations, improving preparation and synchronization for complex bodies.
- Reduced duplicate surgical table and assembly data while supporting conversion of saves from published versions on load.
- Unified creature-machine models across inventory, JEI, world, and Flywheel rendering, fixing duplicate rendering, missing GUI previews, and creeper effect issues.

### Fixes

- Fixed Spider Assembly Tables releasing sequenced-assembly workpieces when supplies run out, with waiting checks for item/fluid locks and required amounts.
- Fixed loss of actual carried equipment, saddles, and container contents when mimics die or are placed on surgical tables.
- Fixed butter food-property and box-render-setting queries before configuration loading completes.

---

## 2.0.0-beta.2

### 中文

### 新增功能

- **供体特性**：仿生生物可继承供体的环境适应、移动、攻防、感知和驯服等能力。
- **组织与部位判定**：根据供体组织占比、安装部位与配套器官决定特性是否生效，纸箱提示可查看特性及未满足的条件。
- **特性配置**：支持通过数据包调整身体特性的供体规则，以及免疫效果、护甲和恢复等数值。
- **玩家拟态与附身**：新增玩家拟态生物，培养皿可记录玩家外观，并可使用回响碎片附身控制拟态生物。
- **创造手术盒**：可直接将普通生物转为拟态生物，或恢复其原本状态。
- **巨型青蛙工厂**：新增内部留空的工厂化巨型青蛙，为胃袋内建造保留空间。

### 改进

- **胃袋生态**：新增青蛙胃褶皱，将青蛙胃菇细分为菌柄、伞盖和菌褶，完善胃袋地形与植被。
- **机器定制**：蜘蛛组装台支持更换机壳外观；鱿鱼打印机支持自定义复制配方与附魔许可规则，并同步 JEI 展示。
- **模型与渲染**：更新多种生物机器和纸箱的物品、世界模型，完善 Flywheel 渲染与捕获内容展示。

### English

### New Features

- Added donor traits that let bionic creatures inherit environmental adaptations, movement, combat, senses, and taming abilities.
- Added trait evaluation based on tissue coverage, body slots, and supporting organs, with traits and unmet conditions shown in box tooltips.
- Added data-pack overrides for body-trait donor rules, effect immunities, armor, and regeneration.
- Added player mimics, player appearance recording in Petri Dishes, and Echo Shard possession for controlling mimics.
- Added the Creative Surgical Kit for turning creatures into mimics and restoring their original state.
- Added the Giant Frog Factory, whose empty stomach interior leaves room for building.

### Improvements

- Added Frog Stomach Folds and separate fungus stems, caps, and gills, refining stomach terrain and vegetation.
- Added customizable Spider Assembly Table casings and Squid Printer copying recipes and enchantment rules, with matching JEI displays.
- Updated creature-machine and box models across inventory and world rendering, improving Flywheel support and captured-content displays.

---

## 2.0.0-beta.1

### 中文

### 新增功能

- **手术台与手术盒**：可将拟态生物拆分为部件，使用剪刀、强力胶、蜂蜜瓶和对称之杖进行切割、粘合、组合与对称装配。
- **仿生组装**可用关节连接活动部件，组装能够移动、转头和攻击的仿生生物。
- **临时纸箱**可用于装箱、搬运和放回手术部件。
- **Q弹效果**：新增液态活体史莱姆瓶与 Q弹效果，加入弹跳及身体形变表现。
- **皓蓝装饰与胃菇**：新增皓蓝桌面、脚手架、梯子、栏杆、门和青蛙胃菇。

### 改进

- **拟态生物**：保存供体模型与部件信息，并根据组装形态计算身体体积和生命值。
- **纸箱与配方展示**：区分空纸箱和捕获纸箱的显示，完善组装相关物品的提示与 JEI 兼容。

### English

### New Features

- Added the Surgical Table and Surgical Kit for cutting, gluing, grouping, and mirroring mimic parts with shears, Super Glue, Honey Bottles, and the Symmetry Wand.
- Added joint-based assembly for bionic creatures that can move, turn their heads, and attack.
- Added temporary boxes for packing, moving, and returning surgical parts to the table.
- Added Liquid Living Slime Bottles and the Bouncing effect, with bouncing and body deformation.
- Added Asurine Table Cloths, Scaffolding, Ladders, Bars, Doors, and Frog Stomach Fungus.

### Improvements

- Preserved donor models and part information for mimics, with body volume and health calculated from their assembled form.
- Distinguished empty and captured-creature box displays and improved assembly tooltips and JEI compatibility.

---

## 1.3.1

### 中文

### 新增功能

- **黄油方块**：新增黄油块和超级黄油块，踩踏可获得旋转效果。
- **旋转玩法**：扩展旋转效果对近战、瞄准和弹射物方向的影响，补充延长型旋转药水与旋转之箭。

### 改进

- **黄油猫外观**：完善猫品种识别及方块物品渲染，使放置和物品展示保留对应外观。

### 修复

- 修复工作盆侧向输出小型史莱姆时未能正确释放实体的问题。
- 修正潜影打包机在子空间中的范围判定，使其按实际世界位置检查目标。

### English

### New Features

- Added Butter Blocks and Super Butter Blocks that apply rotation effects when stepped on.
- Extended rotation effects to melee attacks, aiming, and projectile direction, and added extended Rotation Potions and Tipped Arrows of Rotation.

### Improvements

- Improved cat variant recognition and block-item rendering, preserving the matching appearance when displayed and placed.

### Fixes

- Fixed small slimes not materializing correctly from sideways Basin outputs.
- Corrected Shulker Packager range checks in subspaces to use actual world positions.

---

## 1.3.0.1

### 中文

### 改进

- **持械兼容**：为音波汪汪炮补充 Yes Steve Model 的弩类持械姿势兼容。

### 修复

- 修正音波汪汪炮升级说明，明确副手持扳手时需潜行右击才能卸下升级。

### English

### Improvements

- Added Yes Steve Model crossbow holding-pose compatibility for the Sonic Dog Cannon.

### Fixes

- Corrected upgrade instructions to specify sneaking and right-clicking with a wrench in the off hand to remove upgrades.

---

## 1.3.0

### 中文

### 新增功能

- **岩浆怪燃烧室**：新增以岩浆怪为核心的燃烧室及相关配方。
- **叮咚鸡**：新增可敲钟并输出红石信号的叮咚鸡，支持安装音符盒调整发声。
- **音波汪汪炮**：新增蓄力音波武器，可消耗气压背罐空气，并通过锻造升级音色、瞄准和攻击效果。
- **音波附魔**：新增可在远古城市获得的音波宝藏附魔。

### 改进

- **传送带自动化**：完善三类传送带与蓝图、蓝图加农炮、机械手和移动结构的兼容，改善漏斗、隧道、机械臂及粉碎轮交接。
- **纸箱与培养皿**：改善捕获内容的渲染缓存与拟态数据保存，调整纸箱切制产量。
- **配方查询**：补充新机器的 JEI 分类、生物图标和配方展示。

### 修复

- 修复多方块机器在拆除、跨区块卸载和重新加载时的重复掉落或结构残留。
- 修复纸箱释放生物时的 UUID 冲突及培养皿清空状态异常。
- 修复动力传送带上的实体聚集与传送带链路状态不同步的问题。

### English

### New Features

- Added the Magma Cube Burner and its recipes.
- Added the Ding-Dong Chicken, which rings bells and emits redstone signals, with Note Blocks for changing its sound.
- Added the charged Sonic Dog Cannon, using air from backtanks and smithing upgrades for sound, aiming, and attack effects.
- Added a Sonic Boom treasure enchantment obtainable in Ancient Cities.

### Improvements

- Improved all three belt types with Schematics, Schematicannons, Deployers, and contraptions, alongside funnel, tunnel, Mechanical Arm, and Crushing Wheel transfers.
- Improved captured-content rendering caches and mimic data preservation, and adjusted box cutting yields.
- Added JEI categories, creature icons, and recipe displays for new machines.

### Fixes

- Fixed duplicate drops and leftover multiblock parts during dismantling, cross-chunk unloading, and reloading.
- Fixed UUID conflicts when releasing boxed creatures and incorrect Petri Dish clearing state.
- Fixed entity accumulation on Power Belts and belt-chain synchronization issues.

---

## 1.2.6

### 中文

### 改进

- **采掘与扳手**：统一机器材质、采掘工具和掉落判定，兼容通用扳手，并完善定向方块与多方块机器的旋转、拆卸操作。
- **黄油猫自动化**：机械臂可向黄油猫引擎补充黄油和超级黄油，并正确处理容量与容器返还。
- **黄油猫信息**：补充 Jade 黄油余量、消耗时间及无限状态显示，完善猫品种、动力统计与物品渲染。

### 修复

- 修复唤魔者附魔室、潜影传送站扳手拆卸后的残留或重复掉落，以及链式传动箱被错误替换的问题。

### English

### Improvements

- Standardized machine materials, mining tools, and drops, added common wrench compatibility, and refined rotation and dismantling for directional and multiblock machines.
- Added Mechanical Arm input for butter and Super Butter in Butter Cat Engines, respecting capacity and returning containers.
- Added Jade displays for butter supply, consumption time, and infinite operation, and improved cat variants, kinetic statistics, and item rendering.

### Fixes

- Fixed leftover parts and duplicate drops when wrenching Evoker Enchanting Chambers and Shulker Teleporters, and incorrect replacement of Encased Chain Drives.

---

## 1.2.5

### 中文

### 新增功能

- **万向节断裂与修复**：史莱姆万向节过度拉伸后变为半截传动杆，可用纸箱中的史莱姆重新连接。
- **胃袋生态**：新增青蛙胃黏膜及起伏地形、水池、平台、藤蔓和瀑布，分泌物可沿墙面与天花板扩散。
- **内容开关**：新增分项功能开关，统一控制相关配方、放置与使用；补充物流、传送及流体参数配置。

### 改进

- **万向节连接**：采用两次点击建立连接，加入拉伸受力与断裂预览，改善读档及子空间移动后的连接恢复。
- **经验与配方兼容**：经验泵支持可选的 Create Enchantment Industry 经验流体，生化机壳配方接受通用锌板标签。
- **可选兼容**：Sable 改为可选依赖，未安装时也可正常加载本模组。

### 修复

- 修复悦灵返航失败时载体丢失，以及潜影传送目标未加载或重复激活时的异常。

### English

### New Features

- Added Slime Universal Joint breakage into Half Shafts when overstretched, with repairs using a slime from a captured-creature box.
- Added Frog Stomach Mucosa, uneven terrain, pools, platforms, vines, and waterfalls, with secretions spreading across walls and ceilings.
- Added individual content toggles controlling recipes, placement, and use, alongside logistics, teleportation, and fluid settings.

### Improvements

- Added two-click joint connections, tension and breakage previews, and better connection recovery after reloading or subspace movement.
- Added optional Create Enchantment Industry experience-fluid support to Experience Pumps and common zinc plate tags to Biotech Casing recipes.
- Made Sable optional so the mod can load without it installed.

### Fixes

- Fixed carrier loss after failed Allay returns and issues with unloaded destinations or repeated Shulker Teleporter activation.

---

## 1.2.4

### 中文

### 新增功能

- **悦灵物流教程**：新增悦灵驿站思索场景，演示站点、玩家地址和跨维度派送。
- **思索分类**：为现有机器补充动力、物流、流体和移动结构等分类入口。

### 修复

- 修复下界传送门状态检查与传送液逻辑相互干扰的问题。
- 修复末影无线终端远程请求界面因目标实体状态变化而误关闭或崩溃的问题。

### English

### New Features

- Added Allay Post Ponder scenes covering station addresses, player addresses, and deliveries across dimensions.
- Added Ponder category entries for existing kinetic, logistics, fluid, and contraption content.

### Fixes

- Fixed interference between Nether Portal state checks and Teleport Fluid behavior.
- Fixed Ender Wireless Terminal request screens closing unexpectedly or crashing when the target entity's state changed.

---

## 1.2.3

### 中文

### 新增功能

- **功德无量全自动放生机**：新增鲑鱼水车，受水流驱动时可提升附近村民对放置者的声望。
- **青蛙消化道与分泌物**：胃袋加入前后双出口，填充消化道壁可激活尾部出口；分泌物可吸收史莱姆死亡经验并扩散。
- **活体史莱姆采集**：可通过多次敲击液态活体史莱姆源方块采集史莱姆球。

### 改进

- **史莱姆传送带**：完善前后回环、转角、竖直与侧向运输，统一带间交接及物品贴面表现。
- **漏斗与生物加工**：特殊传送带上的漏斗按带面取放物品，小型史莱姆可经漏斗进入工作盆加工。
- **交互与引导**：鱿鱼打印机支持朝向与扳手旋转，补充培养皿、黄油猫和物流进度，并简化生物打包机配方。

### 修复

- 修复悦灵驿站向满容器输出时丢失包裹的问题。
- 修复手动应用配方丢失方块朝向，以及工作盆捕获物品被外部错误存取的问题。

### English

### New Features

- Added the Fully Automatic Fish Release Machine, a salmon waterwheel that improves nearby villagers' reputation toward its placer while powered by water.
- Added front and rear stomach exits, a rear exit activated by filling Digestive Tract Walls, and secretions that absorb slime death experience to spread.
- Added slime-ball harvesting by repeatedly striking Liquid Living Slime source blocks.

### Improvements

- Refined Slime Belt front and rear loops, corners, vertical and sideways transport, belt transfers, and item alignment.
- Made funnels on special belts follow the belt surface and support feeding small slimes into Basins for processing.
- Added directional placement and wrench rotation to Squid Printers, expanded Petri Dish, Butter Cat, and logistics advancements, and simplified the Bio Packager recipe.

### Fixes

- Fixed Allay Posts losing packages when output containers were full.
- Fixed manual application recipes losing block orientation and external automation incorrectly accessing captured Basin creatures.

---

## 1.2.2.1

### 中文

### 新增功能

- **培养皿教程**：新增史莱姆培养皿思索场景。

### 改进

- **组装优先级**：蜘蛛组装台可优先处理下方置物台中进度更高、且仍有可用下一步配方的序列组装工件。

### 修复

- 修复史莱姆传送带竖直接入、动力反转和读档后的交接方向，以及漏斗表面形状判定异常。
- 修复培养皿上方有足够空间时仍拒绝生成生物的问题。

### English

### New Features

- Added a Slime Petri Dish Ponder scene.

### Improvements

- Added Spider Assembly Table priority for more advanced sequenced-assembly workpieces on the Depot below, provided a valid next step exists.

### Fixes

- Fixed Slime Belt transfer directions after vertical connections, reversal, and reloading, alongside incorrect funnel surface shapes.
- Fixed Petri Dishes rejecting creature spawning despite having enough space above them.

---

## 1.2.2

### 中文

### 新增功能

- **巨型青蛙与胃袋**：新增可吞入玩家和物品的巨型青蛙，每只青蛙拥有独立胃袋空间，搬运后可保留关联房间。
- **史莱姆套装**：用液态活体史莱姆填充纸板护甲，可在潜行时伪装成小型史莱姆并进入巨型青蛙。
- **子空间兼容**：传送带、恶魂热气球和潜影物流支持 Sable 移动、旋转子空间中的坐标与交互。

### 改进

- **潜影打包机**：支持多个输出目标及轮询选择，完善跨空间配置、目标检查和思索说明。

### 修复

- 修复潜影打包机在目标满载或插入失败时丢失包裹的问题。
- 修复蜘蛛组装台重复加工、输入变化后继续加工及读档后处理状态错误的问题。

### English

### New Features

- Added Giant Frogs that swallow players and items, each with a private stomach space preserved when the frog is moved.
- Added the Slime Suit by filling Cardboard Armor with Liquid Living Slime, allowing sneaking players to disguise themselves as small slimes and enter Giant Frogs.
- Added Sable moving and rotating subspace support for belts, Ghast Hot Air Balloons, and Shulker logistics.

### Improvements

- Added multiple Shulker Packager outputs and round-robin selection, with improved cross-space configuration, destination checks, and Ponder guidance.

### Fixes

- Fixed Shulker Packagers losing packages when destinations were full or insertion failed.
- Fixed Spider Assembly Tables processing twice, continuing after inputs changed, or restoring incorrect processing state after reloading.

---

## 1.2.1.1

### 中文

### 修复

- 修复公共注册与网络处理加载客户端类而导致的专用服务器启动问题。
- 修复黄油猫引擎在客户端和服务端重复应用效果或消耗物品的问题。

### English

### Fixes

- Fixed dedicated server startup issues caused by client classes loading through common registration and networking.
- Fixed Butter Cat Engines applying effects or consuming items on both client and server.

---

## 1.2.1

### 中文

### 改进

- **平台迁移**：升级至 Minecraft 1.21.1、NeoForge 与 Java 21，适配 Create 6.0.10。
- **数据与兼容**：迁移物品数据组件、网络通信和配方资源，更新 JEI、Jade 与客户端渲染适配。

### English

### Improvements

- Updated to Minecraft 1.21.1, NeoForge, and Java 21, with Create 6.0.10 support.
- Migrated item data components, networking, and recipe resources, and updated JEI, Jade, and client rendering integration.

---

## 1.2.0

### 中文

### 新增功能

- **悦灵物流**：新增悦灵驿站与悦灵快递员，可按站点地址或 `@玩家` 投递包裹，支持通配地址、跨维度派送和返航。
- **传送液**：可从下界传送门抽取传送液，并用于相应传送玩法。

### 改进

- **远程仓储**：末影无线终端支持跨维度、无距离限制的远程仓储访问。
- **物流信息**：完善快递任务保存、状态提示与纸箱地址、内容说明。

### 修复

- 修复思索场景中粒子缩放异常及 Sodium 环境下的显示问题。

### English

### New Features

- Added Allay Posts and Allay Couriers for station or `@player` package deliveries, supporting wildcard addresses, cross-dimensional routes, and return trips.
- Added Teleport Fluid extraction from Nether Portals for teleportation uses.

### Improvements

- Added cross-dimensional remote storage access without a distance limit to the Ender Wireless Terminal.
- Improved courier task persistence, status displays, and box address and content tooltips.

### Fixes

- Fixed incorrect particle scaling in Ponder scenes and display issues with Sodium.

---

## 1.1.0

### 中文

### 新增功能

- **潜影物流**：新增潜影打包机与潜影传送站，分别用于包裹转运和按地址传送实体、物品。
- **标准经验流体**：经验改用 Create 管道与流体储罐运输、存储，替代原有专用经验管道和经验罐。
- **史莱姆加工**：小型史莱姆可进入工作盆，配合压块、搅拌等配方加工。
- **材料配方**：新增碳粉、石墨及染色矿物等加工路线。

### 改进

- **黄油猫装配**：可用装有猫的纸箱在传动杆上放置猫，再添加面包组装黄油猫引擎。
- **配方与提示**：完善捕获生物的 JEI 图标和配方展示，并调整活体构件等名称与说明。

### English

### New Features

- Added Shulker Packagers for package transfers and Shulker Teleporters for address-based entity and item teleportation.
- Switched experience transport and storage to Create fluid pipes and tanks, replacing dedicated experience pipes and tanks.
- Added small-slime Basin processing with compacting, mixing, and related recipes.
- Added Carbon Powder, Graphite, and colored mineral processing routes.

### Improvements

- Added Butter Cat assembly by placing a boxed cat on a Shaft, then adding bread to form a Butter Cat Engine.
- Improved captured-creature JEI icons and recipe displays, and updated Living Mechanism naming and tooltips.

---

## 1.0.7

### 中文

### 新增功能

- **黄油猫引擎**：新增以猫、面包和黄油构成的动力源。
- **奶油与黄油**：新增奶油、黄油、蜂蜜黄油、超级黄油及相关加工配方和旋转效果。
- **皓蓝材料**：新增皓蓝合金、锌板、皓蓝机壳和生化机壳及其配方。

### 改进

- **培养与碰撞**：完善史莱姆培养皿形状及拟态生物生成空间检查。
- **经验生产**：移除已弃用的经验结晶器，保留经验母岩与晶簇路线。

### English

### New Features

- Added the Butter Cat Engine, a power source built from a cat, bread, and butter.
- Added Cream, Butter, Honey Butter, Super Butter, their processing recipes, and rotation effects.
- Added Asurine Alloy, Zinc Sheets, Asurine Casings, and Biotech Casings with recipes.

### Improvements

- Refined Slime Petri Dish shapes and space checks for spawning mimics.
- Removed the deprecated Experience Crystallizer, retaining the Budding Experience and cluster route.

---

## 1.0.4

### 中文

### 新增功能

- **史莱姆培养皿**：可消耗仿生构件与液态活体史莱姆，参照附近生物培养拟态生物。
- **智能强力胶**：新增用于移动结构装配的智能强力胶。
- **无线终端**：可绑定仓储并在范围内远程访问。

### 修复

- 修复爆破室所在区块未加载时的结构处理，避免继续执行无效加工。

### English

### New Features

- Added the Slime Petri Dish, using Bionic Mechanisms and Liquid Living Slime to grow mimics based on nearby creatures.
- Added Smart Super Glue for contraption assembly.
- Added a Wireless Terminal for binding storage and accessing it remotely within range.

### Fixes

- Fixed Blast Chamber structure handling when required chunks were unloaded, preventing invalid processing.

---

## 1.0.3

### 中文

### 修复

- 修复 Create 加工装置无法正确放置在水平史莱姆传送带或岩浆怪传送带上方的问题。

### English

### Fixes

- Fixed Create processing operators failing to place correctly above horizontal Slime Belts and Magma Cube Belts.

---

## 1.0.2

### 中文

### 修复

- 修复启动时移动结构类型注册过早的问题。

### English

### Fixes

- Fixed contraption types being registered too early during startup.

---

## 1.0.1

### 中文

### 修复

- 修复经验管网的端点识别、分支连接与带经验物品抽取不稳定的问题。

### English

### Fixes

- Fixed unreliable endpoint detection, branch connections, and experience-item extraction in experience pipe networks.

---

## 1.0.0

### 中文

### 新增功能

- **生物传送带**：新增史莱姆传送带、岩浆怪传送带和动力传送带，扩展物品运输与动力玩法。
- **动力传动**：新增史莱姆万向节、半截传动杆、史莱姆离合器和骨制棘轮。
- **生物捕获与材料**：新增大小纸箱、生物打包机、液态活体史莱姆和仿生构件。
- **生物加工机器**：新增唤魔者附魔室、鱿鱼打印机、蜘蛛组装台和爆破室核心，支持附魔、附魔书复制、组装与高压爆破加工。
- **经验生产与运输**：新增经验泵、经验管道、经验罐，以及经验母岩、晶芽和晶簇。
- **恶魂热气球**：新增恶魂热气球装配站、恶魂驾驶台与多色缓冲垫。
- **其他装置**：新增固定的钓竿、薛定谔的猫，以及防爆机壳、保险库和玻璃。

### 改进

- **查询与教程**：接入 JEI 配方、Jade 信息提示及思索场景。

### English

### New Features

- Added Slime Belts, Magma Cube Belts, and Power Belts for item transport and kinetic uses.
- Added Slime Universal Joints, Half Shafts, Slime Clutches, and Bone Ratchets.
- Added small and large boxes, the Bio Packager, Liquid Living Slime, and Bionic Mechanisms.
- Added the Evoker Enchanting Chamber, Squid Printer, Spider Assembly Table, and Blast Chamber Core for enchanting, enchanted-book copying, assembly, and high-pressure blasting.
- Added Experience Pumps, Pipes, Tanks, Budding Experience, buds, and clusters.
- Added the Ghast Hot Air Balloon Assembly Station, Ghast Helm, and colored Buffer Pads.
- Added the Fixed Fishing Rod, Schrodinger's Cat, and explosion-proof casings, vaults, and glass.

### Improvements

- Added JEI recipes, Jade information displays, and Ponder scenes.

---

## 0.1.0

### 中文

### 改进

- **项目初始化**：建立 Minecraft 1.20.1 Forge 工程，接入 Create 依赖与基础构建配置。

### English

### Improvements

- Initialized the Minecraft 1.20.1 Forge project with Create dependencies and basic build configuration.
