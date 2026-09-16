# 连接纹理测试素材

此目录是 Python/Java 共用的测试输入，不会随生产 JAR 分发。
它们是原始材质和手绘样例，不是运行时自动生成的蜘蛛贴图。

## 来源

| 文件 | 来源与用途 |
| --- | --- |
| `spider_andesite_authored.png` | 用户提供的 `spider_andesite_encased.png` 原样副本；64×32，1328 个非透明像素 |
| `{andesite,brass,copper,shadow_steel,refined_radiance,railway}_casing_connected.png` | Create 1.21.1 / 6.0.10 中 `assets/create/textures/block/` 的原样文件；128×128 |
| `biotech_casing`、`explosion_proof_casing`、`explosion_proof_casing_side` 的普通/连接 PNG | Biotech 1.2.6 基线包中 `assets/create_biotech/textures/block/` 的原样文件；16×16 /128×128 |

铁路夹具使用顶面连接纹理而非侧面。Biotech 的三组图片用于验证普通纹理不满足源网格时，
自动尝试同名 `_connected` 纹理，不需要为每个新机壳准备蜘蛛贴图。

源素材保留各自的授权与归属；测试用途不改变其许可证。项目说明见
[许可证](../../../../LICENSE.md)和[第三方声明](../../../../THIRD_PARTY_NOTICES.md)。

## 与其他文件的关系

- [测试资源包](../../../resourcepacks/connected_spider/README.md)只引用原资源 ID，不重复分发这些源纹理。
- [自动 UV 工具](../../../material_mapping/README.md)可将手绘样例和源图作为输入；模糊匹配可能仍留下多解或无匹配区域。
- 手绘图不包含源坐标。样例复现正确不代表恢复了原始编辑过程，也不保证换材质后的选区语义正确。

保持这些输入不变；生成的报告和预览请写到 `build/`，不要覆盖夹具。
