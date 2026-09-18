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
- 蜘蛛装配台的蜘蛛和发光眼睛
- 岩浆怪燃烧器的岩浆怪
- 苦力怕爆炸室中动态增减的苦力怕
- 万向节的软泥轴

蜘蛛装配台的可换肤外壳仍由普通渲染路径绘制，因为它的纹理由方块实体状态在运行时选择；蜘蛛本体与眼睛继续使用固定的 Flywheel 零件实例。
