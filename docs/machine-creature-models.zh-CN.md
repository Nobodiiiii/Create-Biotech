# 生物机器方块模型

生物机器现在使用与 Create 动画零件相同的结构：几何体是标准 Java 方块模型，Java 只保存层级、枢轴和动画，Flywheel 为每个可动零件创建 `TransformedInstance`。

## 资源位置

每个能够独立运动的刚性零件各占一个普通方块模型：

```text
assets/create_biotech/models/block/machine_creature/<模型>/<零件>.json
```

例如蜘蛛头是：

```text
assets/create_biotech/models/block/machine_creature/spider/head.json
```

这些文件使用 Java Block/Item 模型格式，包含 `textures`、`elements` 和逐面的 `uv`，可以直接用 Blockbench 的 Java Block/Item 模式打开。一个文件只显示一个可动零件是预期行为；完整生物由运行时按 Java 层级组合。

实体材质被模型引用后必须进入方块纹理图集。对应的 `single` 来源登记在：

```text
assets/minecraft/atlases/blocks.json
```

## Java 层级与动画

`MachineCreatureModelData` 定义各零件的父子关系、枢轴、初始旋转、可见性和安装点。这里的坐标保持原 Java `ModelPart` 的模型像素语义，运行时由 `MachineCreatureModel.Part` 依次应用父子变换。

新增或重命名零件时，需要同步修改两处：

1. 在 `models/block/machine_creature/<模型>/` 中添加或重命名标准方块模型。
2. 在 `MachineCreatureModelData` 中更新对应的 `PartSpec`。`modelName` 对应文件名，`path` 和 `parentPath` 对应 Java 动画访问的层级路径。

`MachineCreatureModels.allPartials()` 会把所有有几何体的零件登记进 Minecraft 模型烘焙流程。这里不再使用自定义 JSON 加载器，也不再在资源重载阶段自行烘焙 `ModelPart`。

## 两条渲染路径

普通渲染回退使用 `CachedBuffers.partial(...)` 绘制相同的 `PartialModel`。物品、JEI 预览、盆地内容以及不支持 Flywheel 的环境都走这条路径。

世界中的机器优先使用 `MachineCreatureVisualModel`。它为每个有几何体的零件持有一个 `TransformedInstance`，每帧只更新 Java 动画产生的变换、光照、覆层和颜色。机器的流体、物品、动态贴图等不适合固定实例的内容仍由方块实体渲染器绘制。

目前已接入 Flywheel 的生物零件包括：

- 自动放鱼机的鲑鱼和夹具
- 鱿鱼打印机的鱿鱼
- 巨蛙及舌头
- 唤魔者附魔室的唤魔者和书
- 悦灵端口的悦灵
- 潜影传送器的壳体
- 蜘蛛装配台的蜘蛛本体
- 岩浆怪燃烧器的岩浆怪
- 苦力怕爆炸室中动态增减的苦力怕
- 万向节的软泥轴

蜘蛛装配台的可换肤外壳仍由普通渲染路径绘制，因为它的纹理由方块实体状态在运行时选择；蜘蛛本体继续使用固定的 Flywheel 零件实例。

## GUI 预览与特殊材质

GUI 中绘制方块实体时，真实世界支持 Flywheel 并不代表 GUI 内存在对应的实例。唤魔者附魔室的 JEI 预览使用 `EvokerEnchantingChamberRenderer.renderGuiPreview(...)`，显式绘制唤魔者和书；蜘蛛装配台的界面也使用自己的 `renderGuiPreview(...)`。不能直接根据预览所绑定世界的 `supportsVisualization(...)` 跳过这些内容，否则它们会在启用 Flywheel 时消失。

标准方块材质不能完整替代实体的特殊附加层：

- 苦力怕的闪电层使用 `RenderType.energySwirl(...)`，恢复原版的加法混合、双面绘制与每刻 `0.01` 的双轴 UV 滚动。JEI 与世界回退共用此路径；世界中开启 Flywheel 时，仅本体实例化，闪电层仍由方块实体渲染器绘制。
- 苦力怕本体的普通渲染回退使用实体裁切材质，保留膨胀时的闪白覆层。方块 `cutout` 顶点格式不包含该覆层。
- 蜘蛛眼睛使用 `RenderType.eyes(...)` 的无光照加法混合层，由普通渲染路径绘制。仅把普通裁切实例的亮度设为最大值不能还原该效果。

这些路径仍使用原来的标准方块模型几何体。`MachineCreatureModel.renderTextured(...)` 把方块图集 UV 还原成独立纹理的 `0..1` UV，并补齐实体顶点格式所需的覆层属性。滚动直接作用于独立纹理，避免 UV 越出图集中的精灵区域、采样到邻接贴图。

对应的 Minecraft 1.21.1 本地参考为：

- `ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/layers/EnergySwirlLayer.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/layers/CreeperPowerLayer.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/layers/EyesLayer.java`
- `ref/1.21.1/Minecraft/net/minecraft/client/renderer/RenderType.java`

GUI 调用流程参考 `ref/1.21.1/Ponder/common/src/main/java/net/createmod/catnip/gui/element/GuiGameElement.java`。该 Ponder checkout 是 1.21.1 开发分支快照；`SuperByteBuffer` 不输出覆层属性的行为另已核对项目锁定的 `ponder-neoforge:1.0.82+mc1.21.1` 源码包。

## 手持生物物品与固定帽子

悦灵快递员、未完成的悦灵快递员、叮咚鸡和内部 JEI 鸡物品使用 `MachineCreatureItemRenderer`，直接绘制标准 JSON 方块模型零件，不再为物品展示创建或调用生物实体渲染器。悦灵复用驿站的 `machine_creature/allay/` 零件；鸡的几何体和 UV 按本地 `ref/1.21.1/Minecraft/net/minecraft/client/model/ChickenModel.java` 定义在 `machine_creature/chicken/`。叮咚鸡的背铃由 `chicken/bell.json` 固定组合已有的三个铃铛零件。

物品继续沿用原来的第一人称、第三人称、掉落和展示框变换。快递员的包裹按物品数据绘制，装载时切换为抱持姿势；包裹不参与居中测量，掉落和展示框中的自定义朝向也保留。未完成的快递员保持无帽、无包裹。

小型史莱姆已经通过 `MachineCreatureRenderer` 共用工作盆的 JSON 模型。大狗音波炮和猫猫已经使用普通 JSON 零件，继续由原有物品渲染器处理动画、配件和品种。

悦灵的物流帽统一使用 `machine_creature/allay_hat/hat.json`。帽子在 JSON 中固定偏移、缩放和倾角，挂在头部局部坐标系下；驿站、驿站物品、手持快递员及飞行快递员共用该零件，不再按头部包围盒或 `TrainHatInfo` 动态计算尺寸。驿站开启 Flywheel 时帽子也实例化，普通回退不会重复绘制。帽子引用本地 Create 6.0.10 的标准模型 `ref/1.21.1/Create/src/main/resources/assets/create/models/entity/logistics_hat.json`。

悦灵翅膀、巨蛙的手脚和舌头等薄片、鲑鱼鱼鳍，以及带透明区域的鸡脚需要双面显示。它们在普通渲染和 Flywheel 中均关闭背面剔除；潜影传送器的壳体也显式使用双面材质，让站在内部的玩家能看到内壁。几何体、UV 和物品摆放保持不变。

双面材质还必须正确丢弃透明像素。原版悦灵翅膀的东西两面使用不同 UV，其中一面的采样区域完全透明。普通方块半透明着色器不执行 alpha discard，透明面仍会写入深度，可能挡住另一面的翅膀，或让思索中稍后绘制的背景留下黑色空洞。`MachineCreatureRenderTypes` 的半透明回退因此使用裁切着色器配合半透明混合：alpha 小于 `0.1` 的像素不写颜色或深度，其余像素保留原本的透明度。该层继续使用方块图集、`BLOCK` 顶点格式和主渲染目标，适用于世界、物品、JEI 与思索。Flywheel 的所有半透明生物零件也设置 `CutoutShaders.ONE_TENTH` 并关闭 mipmap，不能只关闭背面剔除。

双面和 alpha discard 行为核对自本地 Minecraft 1.21.1 的 `AllayModel.java`、`HierarchicalModel.java` 和 `RenderType.java`，以及缓存的官方 1.21.1 客户端中 `rendertype_translucent.fsh`、`rendertype_entity_translucent.fsh`、`rendertype_cutout.fsh` 和悦灵纹理。Flywheel 材质构建参考本地 Create 6.0.10 的 `foundation/render/SpecialModels.java`，并核对项目锁定的 Flywheel 1.0.6 源码包中的 `Materials`、`ModelUtil` 和 `BakedModelBuilder`。
