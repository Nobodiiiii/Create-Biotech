# 装壳蜘蛛装配台 · 通用机壳材质

运行时接受 `create:casing` 方块标签中的任意机壳以及物品保险库，并直接采样连接材质：
普通机壳使用 Create `CasingConnectivity` 中注册的 128 × 128 材质；保险库按
`art/spider_assembly_table_vault - Converted.bbmodel` 逐面使用 `vault_front_large`、
`vault_front_small` 和 `vault_side_large`。普通机壳的 UV、翻转和旋转来自
`art/spider_assembly_table_andesite - Converted.bbmodel`；特殊的头部正脸始终按机壳从统一图集取样。

- 正脸图集：`src/main/resources/assets/create_biotech/textures/block/spider_assembly_table_face.png`（32 × 32，每格 8 × 8）
- Blockbench UV 工程：`art/spider_assembly_table_andesite - Converted.bbmodel`
- 保险库 UV 工程：`art/spider_assembly_table_vault - Converted.bbmodel`

图集前三列依次放置安山、黄铜、铜、暗影钢、璀璨玫瑰石、铁路、蓝辉石、生物科技、
防爆机壳；第四列首格 `(24, 0)` 为物品保险库。10 个槽位可以在同一文件中分别重绘。
其他模组的机壳若没有专用槽位，正脸回退到该机壳连接材质中从 `(12, 8)` 开始的 8 × 8 区域；
若机壳没有注册连接材质，则最后回退到普通方块粒子贴图。

| 部件 | 体块尺寸 X × Y × Z | Box UV 原点 | 数量 |
| --- | --- | --- | --- |
| 头部 | 8 × 8 × 8 | 32, 4 | 1 |
| 胸部 | 6 × 6 × 6 | 0, 0 | 1 |
| 腹部 | 10 × 8 × 12 | 0, 12 | 1 |
| 腿部 | 16 × 2 × 2 | 18, 0 | 8 |

工程以 Bedrock Entity 编辑格式保存，尺寸、盒式 UV 和静止姿态对应模组实际使用的 Java `SpiderModel`。
编辑器坐标采用 `(x, y, z) = (-MinecraftX, 24 - MinecraftY, MinecraftZ)`，左腿启用镜像 UV。
腿部骨骼旋转保留原版姿态；旋转不会改变纹理像素密度。

运行时模型包含 bbmodel 中 81 个有材质的面，其中一个为按机壳选择的特殊正脸；腹部分成四块，
以保留逐面 UV 旋转，头顶的三个根级薄片（含 22.5° 斜片）也按原始枢轴烘焙。
资源重载时会清除按机壳烘焙的四边形缓存。

参考：`ref/1.21.1/Create/src/main/resources/assets/create/textures/block/andesite_casing.png`
及同目录的 `andesite_casing_short.png`、`andesite_block.png`。
版本依据 `ref/SOURCES.md`：Create 6.0.10 官方标签，与当前 `6.0.10-281` 依赖对应。
`ref/` 缺少 1.21.1 原版蜘蛛源码，尺寸/UV 从本机 NeoForm 已生成的 1.21.1 源码缓存核对。

要从 `art/texture.png` 中 `(12, 0)` 的现有正脸重新生成 9 个专用槽位，运行：

```powershell
python art/spider_assembly_table/generate_face_atlas.py
```
