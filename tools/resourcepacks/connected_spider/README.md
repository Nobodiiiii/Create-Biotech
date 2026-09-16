# 蜘蛛连接纹理测试资源包

用于 Minecraft 1.21.1、Create 与 Biotech 的资源包。目录包含现成的蜘蛛 UV 映射和固定眼睛覆盖图，
不包含 Create 的源机壳纹理，也不需要独立 Casted Materials Mod。

## 内容

- 目标：`create_biotech:spider_assembly_table/spider`。
- 定义：`assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json`。
- 目标尺寸 64×32、源逻辑网格 128×128，使用明确的源坐标选区。
- 对安山、黄铜、铜、暗影钢、光辉、铁路机壳配置 `_connected` 源图；铁路使用顶面连接纹理。
- `casted_fixed_eyes.png` 保留 14 个固定眼睛像素；目标轮廓对应[手绘样例](../../test-resources/fixtures/connected_spider/README.md)。

该映射是受样例外观约束的适配，不声称恢复作者的原始源坐标。
它与 Biotech 内置蜘蛛定义相同，正常使用无需额外启用；主要用于隔离测试和资源包覆盖实验。

## 使用

将本目录复制到测试实例的 `resourcepacks` 下，然后在游戏资源包界面启用；压缩时让 `pack.mcmeta`
与 `assets` 位于压缩包根层。修改后用 F3+T 重载，测试结束后禁用资源包，恢复内置资源。

当前运行时只使用直接 UV 或基础模型后备，**不生成合成蜘蛛贴图**。
完全不透明的覆盖像素替换对应 UV，透明像素保留原映射；半透明覆盖层、缺失源图或超预算会回退。
选区数量不等于运行时 quad 数，实际成本由 Java 对宿主模型计算。

## 离线验证

从仓库根目录运行：

```powershell
./gradlew.bat --offline test --tests '*ConnectedSpiderMaterialTest' --tests '*ProductionUvParityTest'
```

测试检查源图选择、轮廓与固定眼睛、不同材质和分辨率的采样，以及生产模型 UV 与像素对照的一致性。
预览位于 `build/casted-materials-preview/connected-spider/java/`，几何成本位于
`build/material-rendering/production-cost.json`。这些是离线验证结果，不替代游戏内观察。

[工具总览](../../README.md) · [自动 UV 推断](../../material_mapping/README.md)
