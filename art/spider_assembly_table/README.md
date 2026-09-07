# 装壳蜘蛛装配台 · 安山机壳材质

材质采用灰绿色安山金属包边、木质嵌板、腹部贯通箍带、整像素铆钉与琥珀色八眼。
包框直接使用安山机壳的金属色阶：外圈深灰、内圈浅灰，交叉箍带不会覆盖这两层边缘。

- 游戏贴图：`src/main/resources/assets/create_biotech/textures/entity/spider_assembly_table/spider_andesite_encased.png`
- Blockbench 工程：`art/spider_assembly_table_andesite.bbmodel`，内嵌贴图，含完整八腿静止姿态。
- 分辨率：64 × 32 RGBA；已使用的 1,328 个纹理像素完全不透明，未使用区域透明。
- 密度：1 个纹理像素 = 1 个模型单位 = 1/16 方块；每个面的 UV 边界均为整数。

| 部件 | 体块尺寸 X × Y × Z | Box UV 原点 | 数量 |
| --- | --- | --- | --- |
| 头部 | 8 × 8 × 8 | 32, 4 | 1 |
| 胸部 | 6 × 6 × 6 | 0, 0 | 1 |
| 腹部 | 10 × 8 × 12 | 0, 12 | 1 |
| 腿部 | 16 × 2 × 2 | 18, 0 | 8 |

工程以 Bedrock Entity 编辑格式保存，尺寸、盒式 UV 和静止姿态对应模组实际使用的 Java `SpiderModel`。
编辑器坐标采用 `(x, y, z) = (-MinecraftX, 24 - MinecraftY, MinecraftZ)`，左腿启用镜像 UV。
腿部骨骼旋转保留原版姿态；旋转不会改变纹理像素密度。

本次已校验全部 66 个面的尺寸与整数 UV 边界，以及 264 个顶点的坐标/UV 对应关系。
校验直接对照 Minecraft 1.21.1 的 `SpiderModel`、`ModelPart.Cube` 与
`SpiderAssemblyTableRenderer` 的缩放和姿态。运行时替换独立装壳贴图即可生效。

参考：`ref/1.21.1/Create/src/main/resources/assets/create/textures/block/andesite_casing.png`
及同目录的 `andesite_casing_short.png`、`andesite_block.png`。
版本依据 `ref/SOURCES.md`：Create 6.0.10 官方标签，与当前 `6.0.10-281` 依赖对应。
`ref/` 缺少 1.21.1 原版蜘蛛源码，尺寸/UV 从本机 NeoForm 已生成的 1.21.1 源码缓存核对。

需要重绘脚本版本时，在仓库根目录运行（需要 Python 和 Pillow）：

```powershell
python art/spider_assembly_table/generate_texture.py
```

脚本按整数坐标重绘 PNG，不缩放、不插值；放大预览使用最近邻采样。
运行脚本会覆盖游戏 PNG。手工修改 Blockbench 贴图后，应将贴图导出到上述游戏资源路径并保存工程。
脚本重绘后，可在 Blockbench 中重新载入游戏 PNG，更新工程内嵌贴图。
