# 青蛙胃室 3D 预览

Three.js 0.160.1 + 原生 JavaScript + 体素表面网格。仅用于设计评审，不修改模组源码或资源。

- 外壳为 X 48 × Z 48 × Y 32 格，与 `CBConfigs.FrogStomach` 默认值一致。
- 入口内侧位于 Z=1、Y=22；出口位于 Z=46、Y=6；传送区域为 4×4 格，框架为 6×6 格，对应 `FrogStomachSpace` 的默认配置。
- 地形、菌落及腺体是优化方案示例，不是当前世界的导出，也不是 Java 地形生成器的逐块复刻。
- 四壁褶皱直接使用 `FrogStomachFoldGeometry` 的 Java 算法导出，替换了原预览的水平平台。褶皱侧面及端面使用新游戏方块 `frog_stomach_fold` 的真实 16×16 贴图，两种材质模式共用。
- 视图：剖视、俯视、室内尺度、完整外壳。可拖动旋转、滚轮缩放、右键平移；触屏支持双指缩放。
- 贴图：所有方块贴图严格为 16×16 RGBA，最近邻过滤、关闭 mipmap。完整方块每面 UV 为 0～1；小模型按实际面尺寸缩放 UV，保持 16 像素/格密度。
- 优化配色用确定性像素脚本生成；现有材质取自项目 PNG。原素材没有的预览专属材质在两个模式中共用，来源见 `texture-manifest.json`。
- 1.8 格高的人形用于判断房间大小。
- 隐藏体素面被剔除、按材质合并网格；无物理模拟，无生态 tick。按交互重绘，不持续执行动画。

## 构建与检查

```powershell
npm.cmd ci --no-audit --no-fund
python build.py
```

交互片段在本任务的可视化目录中生成。`preview-standalone.html` 是以可视化技能的渲染脚本生成的独立包装，可离线打开；Three.js 和纹理均嵌入，没有运行时网络请求。

Three.js 原始许可证保留在内嵌库头部及 `node_modules/three/LICENSE` 中。技术参考：[DataTexture](https://threejs.org/docs/#api/en/textures/DataTexture)、[BufferGeometry](https://threejs.org/docs/#api/en/core/BufferGeometry)。
