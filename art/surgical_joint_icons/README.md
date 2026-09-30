# 关节轮盘与五种特写

玩家使用统一的 `create_biotech:surgical_joint` 物品：主图标为原版锁链，右下角以 8×8 GUI 像素显示当前关节的特写。创造栏与初次制作的关节默认未选择类型，右下角留空，不能安装。按住手术盒快捷键（默认左 Alt）打开共用轮盘，移向类型后松开或左键选择；Esc、移向中心或轮盘之外后松开则取消。轮盘的五个选项只显示正常 16×16 物品尺寸的特写。

选择的类型存储于 `minecraft:custom_data` 的 `SurgicalJointType` 字段，颈关节也显式保存为 `neck`；字段缺失或无效均视为未选择类型。旧版未写字段的统一关节需要重新选择一次，五种旧独立物品仍按其固定类型工作。

切换更新堆叠物品的选择数据，不改变数量。安装仍消耗一个关节，拆下返还相同类型的可切换关节。切石机配方及创造栏只提供统一物品；原有五个固定类型的物品 ID 保留以兼容存档，并作为内部特写显示模型。

图标使用 Minecraft 1.21.1 的宽臂史蒂夫模型和原版 `textures/entity/player/wide/steve.png`，在运行时绘制。没有新增或重绘皮肤贴图。

- 颈：完整头部与上胸，头部转动 22°。
- 肩：上胸与左上臂，左肩外展 18°。
- 肘：原版右臂在中点切分，前臂向前弯曲 30°。
- 髋：腰部与大腿根，一侧大腿前屈 18°。
- 膝：原版右腿在中点切分，大腿前倾 15°，小腿相对大腿向后弯曲 30°。

肘、膝的两端各延长 1 个模型像素，形成轻微交叠，弱化断面并保留弯折轮廓。侧面 UV 随切分插值，衣袖与裤腿纹理保持原来的比例；新切面使用相邻皮肤或布料的像素中心，避免采样边界噪点。

## 运行时入口

`src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalJointIconModel.java` 保存裁切范围与姿态，从原版 `PlayerModel.createMesh` 获取真实顶点。

`SurgicalJointItemRenderer` 经 `CBClientExtensions` 注册，五个内部特写模型继承 `block_centered_rendered_living_entity_item`。`SurgicalJointItemDecorator` 在统一锁链物品上绘制选中类型的角标，物品栏、快捷栏和 JEI 使用同一机制。角标深度处于主物品和数量数字之间，保持计数可读。`GuiEntityItemElement.ofModel` 复用现有的投影居中、缩放、光照参数及物品显示变换，不需要创建世界实体。

`SurgicalKitRadialScreen` 同时服务手术盒与关节，槽位和命中区域由 `SurgicalRadialLayout` 提供。关节选项通过 `GuiGraphics.renderItem` 直接绘制，没有额外放大。切换包验证手持物品与快捷栏槽位，切换到其他槽位会关闭轮盘。

## 离线预览

在仓库根目录运行（JDK 21、Python、Pillow、NumPy）：

```powershell
./gradlew.bat compileJava processResources --offline
python art/surgical_joint_icons/preview.py
```

`ExportJointIcons.java` 是美术导出工具，不是游戏启动器：直接调用编译后的 `SurgicalJointIconModel` 和 `GuiEntityItemElement.applyTransform`，读取五个物品模型的继承显示参数，输出屏幕空间顶点、UV、法线和运行时资源包中的史蒂夫贴图。它不会创建 Minecraft 实例、窗口、世界或 OpenGL 上下文。

`preview.py` 以深度缓冲、最近邻纹理采样和原版物品双光源漫反射生成预览。输出位于 `build/joint-icon-preview/`，包括透明 PNG、16/32/48 像素版本、联系表、轮盘与角标预览，以及 `framing.json`。导出器直接读取运行时的角标常量和轮盘布局，并输出五个槽位中心的命中结果，以及中心、外侧的取消结果。这里展示的是离线软件光栅结果，未包含资源包或第三方着色器的改动；轮盘底色和文字作为预览背景，未启动游戏截图。

交付时已逐张查看三种尺寸：五种特写都居中在 `(8, 8)`，最大投影边长为 14.4 个 GUI 像素，四周至少保留 0.8 像素，不会溢出 16×16 槽位。角标深度范围约 171–179，低于数量文字的 200。最终特写联系表保存在本目录的 `preview.png`，轮盘与选中图标保存在 `wheel-preview.png`。

## 本地参考

来源与工具链说明见 `ref/SOURCES.md`。本次使用的原版几何和显示参考：

- `ref/1.21.1/Minecraft/net/minecraft/client/model/PlayerModel.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/model/HumanoidModel.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/model/geom/ModelPart.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/renderer/block/model/ItemTransform.java`
- `ref/1.21.1/Minecraft/com/mojang/blaze3d/platform/Lighting.java`
- `ref/1.21.1/Minecraft/com/mojang/blaze3d/platform/GlStateManager.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/equipment/toolbox/RadialToolboxMenu.java`
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/foundation/gui/AllGuiTextures.java`
- `ref/1.21.1/Create/src/main/resources/assets/create/textures/gui/widgets.png`

实际导出使用项目编译产物及 NeoForge 21.1.234 的已修补 Minecraft 类，而非单独编译原版参考快照。
