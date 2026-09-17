# 自动 UV 推断

输入样例 PNG、源 PNG 和源逻辑网格，输出 schema 1 坐标映射及诊断文件。
工具只在制作期运行，不会在游戏里反推图片，也不会为每种机壳生成运行时蜘蛛贴图。

[工具总览](../README.md) · [测试素材](../test-resources/fixtures/connected_spider/README.md)

## 快速使用

在仓库根目录安装依赖后运行：

```powershell
python -m pip install -r tools/requirements.txt
python -B -m tools.material_mapping infer `
  --sample sample.png `
  --source all=andesite_casing_connected.png `
  --source-grid all=128x128 `
  --target-json build/inferred/target.json `
  --preview build/inferred/preview.png `
  --report build/inferred/report.json `
  --unresolved-mask build/inferred/unresolved.png
```

每个 `--source SLOT=PNG` 必须对应一个 `--source-grid SLOT=WIDTHxHEIGHT`，可重复指定多个 slot。
图片必须是本地文件；Minecraft 资源标识或 JAR 内路径需要先解析为图片。
输出 JSON 不保存本地路径，运行时源图配置见下文。

## 匹配与草稿

默认完整 RGBA 精确匹配，支持 `identity`、90/180/270 度旋转和水平/垂直镜像。
优先匹配整块矩形；无法匹配或包含背景孔洞时继续细分。只有全零 `(0,0,0,0)` 算背景，
透明像素中的非零 RGB 仍参与比较。

默认多解不猜测源坐标，输出已确定部分，其余保留草稿：

| 输出参数 | 内容 |
| --- | --- |
| `--target-json` | 已确定的源/目标坐标；草稿可被 Java 解析，不代表完整材质 |
| `--preview` | 从源图采样的预览；未解决区域透明 |
| `--report` | 覆盖统计、候选位置、匹配误差和优化状态 |
| `--unresolved-mask` | 黄色表示多解，红色表示无匹配，其余透明 |

退出码：`0` 完整匹配，`3` 已发布草稿，`2` 输入或校验错误。
没有任何已确定像素时，只写诊断文件，**不创建或覆盖目标 JSON**；检查报告中的
`target_written`，不要仅凭文件存在判断本轮生成成功。全零样例返回输入错误。

`--max-candidates` 默认 16，限制报告展示的候选数。候选被截断时，报告中的数量可能只是下界；
相同坐标映射的退化变换不会重复算作多解。

## RGB 容差

例如在命令后添加：

```text
--rgb-rmse 12 --rgb-max-error 32 --rgb-ambiguity-margin 1
```

- `rgb-rmse`：整块矩形的 `sqrt(mean(dr² + dg² + db²))` 上限。
- `rgb-max-error`：任何单像素 RGB 欧氏距离的上限，必须不小于 RMSE 上限。
- `rgb-ambiguity-margin`：与最佳候选的 RMS 距离差，默认 1；差距在此范围内的候选保留为多解。

数值是 8 位 RGB 距离，不是百分比。默认两个误差上限均为 0；Alpha 始终精确匹配。
工具不模糊、平滑或改写输入图片，只允许源图与样例颜色存在误差；导出预览仍采样源图的真实颜色。
参数越宽松，外观相似但跨材质语义错误的匹配越容易进入候选，应换源材质检查结果。

## 两个独立的优化阶段

### 1. 多命中时最小化源外接矩形

```text
--ambiguity-policy min-bounds --bounds-seconds 2
```

默认策略是 `report`。开启 `min-bounds` 后，为各独立多解区域选取一个完整候选，
最小化所有源 slot 的外接矩形面积之和：`Σ(width × height)`。
固定 UV 也计入外接框；这是**外接矩形**，不是选框并集面积，也不是选框面积简单相加。

此模式收集完整候选集合，`--max-candidates` 仅限制显示。`source_bounds_optimization` 报告
初始/最终面积、每个 slot 的外接框、选中候选、下界、超时和 `optimal`。
证明范围是 `fixed_independent_candidate_regions`：已生成的独立候选组合，不是所有可能分区或作者唯一原意。
重叠模型面造成的逐像素证据冲突不参与整块择优，继续保留草稿。

### 2. 固定 UV 后最小化选区数量

```text
--optimize-seconds 2
```

目标是最少的 `len(regions) + len(pixels)`；每个稀疏像素也算一个选区。
这个阶段不改变源 slot/坐标，不跨透明孔洞，也不通过改选同色像素合并选区。
`selection_optimization` 报告当前最好方案、下界、`optimal`、`timed_out`；未证明最优时标为 `best_found`。
证明范围是 `fixed_resolved_uv_mapping`，与前一阶段的外接框最优性分开。

两项秒数都是有限非负的搜索预算，不是整个 CLI 的总超时；不包括候选枚举、准备、校验和发布。
`0` 跳过限时搜索，仍保留可行基线。限时结果不保证每次在完全相同的进度停止。
顶层 `complete` 只表示没有未解决区域，不表示选区最少、模型片数最少或 FPS 最佳。

## 可选模型面约束

添加 `--model model.bbmodel`，借助实际面 UV 边界匹配较大的矩形，并计算面片成本。
此功能使用本包的 [model_uv.py](model_uv.py) 直接读取 `.bbmodel` JSON，不依赖已移除的 Blockbench 工具目录。
它不生成编辑器预览，也不读取内嵌纹理作为源图；不需要安装 Blockbench 软件。

- 模型分辨率必须与样例一致；仅支持 Cube 的非空、整数、边界内矩形 UV，允许反向端点和直角 UV 旋转。
- 普通模型使用导出面；配对工程仅使用 `CM_DEST`。非导出元素/祖先组和 `texture=null` 的面排除，隐藏面仍参与。
- 复用 UV 只推断一次，但每个实际面分别计入成本。重叠面的证据冲突保持草稿，不按遍历顺序覆盖。
- 未引用的图片区域计入 `unused_pixels`，不参与推断。Mesh、分数/越界/退化 UV 等无效输入在发布前报错。

`model_geometry_cost` 包含实际面数、原始相交次数、合并后的片段数、单面最大片数和新增 quad 数。
新增 quad 为 `Σ max(0,该面片数−1)`，透明空面不抵消其他面的新增片数。
这些是指定模型及已导出映射的离线几何成本，不是 draw call 或帧率测量。
无模型时 `computed_cost` 只是兼容字段，Java 始终按运行时模型重新计算并验证预算。

### 仓库内的手绘蜘蛛样例

```powershell
$fixtures = "tools/test-resources/fixtures"
python -B -m tools.material_mapping infer `
  --sample "$fixtures/connected_spider/spider_andesite_authored.png" `
  --source "all=$fixtures/connected_spider/andesite_casing_connected.png" `
  --source-grid all=128x128 `
  --model "$fixtures/models/spider_body.bbmodel" `
  --rgb-rmse 48 --rgb-max-error 128 `
  --ambiguity-policy min-bounds --bounds-seconds 2 --optimize-seconds 2 `
  --target-json build/inferred/spider/target.json `
  --preview build/inferred/spider/preview.png `
  --report build/inferred/spider/report.json `
  --unresolved-mask build/inferred/spider/unresolved.png
```

这是容差匹配与多命中择优的测试输入，不是重新发布正式蜘蛛定义的命令。
手绘眼睛等区域仍可能无匹配并以 `3` 返回草稿；先检查报告和标记图，不要直接覆盖生产 target。

## 尺寸与输出校验

目标宽、高各为 1～256；源逻辑网格不受这个上限限制。
源 PNG 必须是逻辑网格的正整数等比缩放，采样坐标为 `logical × scale + floor(scale / 2)`。

发布前检查输入/输出路径冲突、优化前后的 UV 坐标，以及 JSON 往返渲染的模式、尺寸和完整 RGBA 字节。
RGB 容差不会放宽往返一致性检查。校验失败时旧输出不变；成功后通过临时文件逐个替换，
不承诺多文件发布是单一事务。四个输出彼此不同，且不能覆盖任何输入，包括模型和硬链接别名。

## 有序层平面预览

`preview` 读取现有目标定义、材质覆盖和本地资源映射，先渲染基础 UV，再把旧 `overlay` 放在显式层下方，最后按 `index` 升序（同索引保持声明顺序）应用 cutout 贴图层：

```powershell
python -B -m tools.material_mapping preview `
  --target-json src/main/resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json `
  --material create:copper_casing `
  --source create:block/copper_casing_connected=build/assets/copper_casing_connected.png `
  --texture create_biotech:entity/spider_assembly_table/eye_copper_casing=src/main/resources/assets/create_biotech/textures/entity/spider_assembly_table/eye_copper_casing.png `
  --output build/preview/copper.png
```

`material_variants: true` 从默认文件名识别 `eye_` / `body_` 角色，与运行时采用相同顺序：默认贴图同目录中的 `<命名空间>/<角色>_<机壳名>`、`<角色>_<机壳名>`，眼睛再选编号候选，最后默认 `texture`。前缀加在材质路径最后的文件名上（如 `addon/machines/eye_casing`），不跨角色、不查无前缀名称；未知默认角色仅使用显式图。用 `--material` 指定机壳，并用 `--texture` 提供本地可用候选；预览工具不会扫描游戏资源包。每层仅选择一张图，透明处不保留默认眼睛。

只有 `eye_<非负整数>` 参与通用候选，按数字排序（缺号允许，同号按文件名排序），限同命名空间和同一直接目录。提供多张通用眼睛且未命中专用图时，须传 `--material-index N`：这是游戏同步材质表中从 0 起始的位置，材质表按命名空间、路径排序，专用图材质也占位；选择 `N % 有效候选数`。预览只知道通过 `--texture` 提供的候选，须提供与游戏一致的完整候选集以复现分配。尺寸或 cutout alpha 不合要求的候选会跳过，全透明图有效。工具无法从单个机壳 ID 推断完整游戏材质表，故不猜测该索引。

Body 不参与编号循环。目标可用 `"body_textures": "create_biotech:entity/spider_assembly_table"` 指定专用身体目录；按 `<命名空间>/body_<机壳名>`、`body_<机壳名>` 查找。通过 `--texture` 传入匹配图片后，先以专用身体代替整个 UV 底图（透明处保持透明），再应用显式 `overlay` 和有序图层；没有有效专用图才使用 `--source` 自动映射。选中专用 body 时无需提供或解码机壳源图片。身体画布等于目标尺寸，允许等比整数倍高清；无法读取或解码的 PNG、半透明等无法表达的候选跳过，与运行时一致；必需的显式贴图若最终缺失仍报错且不覆盖输出。显式图层关闭 / 省略 `material_variants` 可固定贴图，优先于自动选择。

`--source` 左侧是材质资源 ID，`--texture` 左侧是层资源 ID；右侧均为本地 PNG。贴图层支持 `grid`、`source`、`destination` 的逻辑像素裁剪和最近邻缩放，并拒绝半透明或 HD 逻辑像素内部 alpha 不一致或缩放后无法表达的 alpha 边界的输入。模型层依赖 baked geometry 与实时部件姿态，明确不显示在平面 PNG 中，须进游戏验证。工具不会合成或发布新的运行时贴图。

## 接入游戏

把目标定义放到 `assets/<namespace>/casted_materials/targets/<path>.json`，目标 ID 即 `<namespace>:<path>`。
例如为材质 `example:my_casing` 配置 `assets/example/casted_materials/materials/my_casing.json`：

```json
{"schema": 1, "slots": {"all": "example:block/casing"}}
```

`example:block/casing` 是 sprite 标识，对应 `assets/example/textures/block/casing.png`，不是本地文件路径。
也可使用目标的 `material_overrides`，或默认模型贴图及同名 `_connected` 查找。
预览 PNG 不必打包。无法匹配的手绘区域不会自动变成固定覆盖层；需要另外制作透明底的覆盖 PNG 并配置 `overlay`。

```powershell
python -B -m pytest -q -p no:cacheprovider tools/material_mapping/tests tools/tests
# 包含 --model 读取、CLI 及跨语言夹具回归：
python -B -m pytest -q -p no:cacheprovider tools
```
