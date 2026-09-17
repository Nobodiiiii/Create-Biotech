# Create: Biotech

[English](README.md) · [中文](README.zh-CN.md) · [玩家介绍](docs/INTRODUCTION.zh-CN.md) · [Player Intro](docs/INTRODUCTION.md)

一个基于 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 的 Minecraft 1.21.1 NeoForge 附属模组，核心方向是把生物和生物材料接进 Create 现有的动能、运输和加工系统。这里有史莱姆传送带、恶魂热气球、经验相关机械，也有把生物接到工作盆、装置、漏斗和 JEI 里的各种实现。

本 README 主要面向贡献者和 AI 编码代理。想先了解玩法的话，请看 [docs/INTRODUCTION.zh-CN.md](docs/INTRODUCTION.zh-CN.md)。

---

## 构建环境

| 项目           | 值                                                                       |
| -------------- | ------------------------------------------------------------------------ |
| Minecraft      | 1.21.1                                                                   |
| 加载器         | NeoForge 21.1.219（通过 `net.neoforged.moddev`）                         |
| Java           | 21                                                                       |
| 模组 id / 版本 | `create_biotech` / 见 [gradle.properties](gradle.properties)              |
| 硬依赖         | Create 6.0.10、Registrate、Flywheel、Ponder                              |
| 软依赖         | JEI、Jade                                                                |
| Mixin 配置     | [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json) |
| 映射表         | Parchment 2024.11.17                                                     |

```bash
./gradlew build                     # 构建 jar
./gradlew runClient                 # 启动开发客户端
./gradlew runServer                 # 启动开发服务端
./gradlew runData                   # 重新生成数据生成产物
./gradlew quickPlayClient -Pinstance=<name>   # 构建 + 拷贝 + 通过 test.py 启动外部实例
./gradlew quickPlaySmoke -Pinstance=<name>    # 进入世界、确认日志标记后自动结束客户端
```

## 内部 UV 材质渲染

材质渲染属于 `foundation/render/material`。
根层负责注册、API 与 NBT 状态，`palette` 负责机壳发现/校验和同步，`mapping` 负责定义/UV 解释与几何计划，
`client` 负责重载、源图绑定与渲染。机壳必须在物品和方块的 `#create:casing` 中同名配对，且 BlockItem 指向对应方块。

蜘蛛方块与物品共用 `CastedMaterialsClient.resolveModel(...)`。静态且碎片化的映射默认只合成一次，
缓存目标尺寸纹理后使用原模型面绘制；不需要拆面的专用 body 直接使用原图。动画、高清、重采样或
无法无损读取的源图保留直接 UV，不冻结动画、不静默降采样。两条路径都失败才回退基础模型/材质。
`layers` 按 `index` 升序应用，同索引保持声明顺序。贴图层以 cutout alpha 裁剪/缩放到目标 UV；模型层把
现有 baked model 挂到实时命名部件。材质覆盖中出现 `layers` 就替换目标列表（空数组也有效），缺省则继承。
旧 `overlay` 仅作兼容并位于显式层下方。生成结果仅存在于纹理内存，不写入 PNG 文件。复用活动模型 root，每次渲染取得
handle，不跨资源重载长期持有。

- 每层必须给出整数 `index`，以及 `texture` 或 `model` 二选一；没有样式轮播或切换状态。
- 贴图层：`grid` 是源逻辑尺寸，`source` / `destination` 为 `[x,y,w,h]`；省略时分别使用目标尺寸、整个源网格、整个目标。`emissive` 默认 `false`，只让最终未被覆盖的片段发光。
- 模型层：`part` 默认 `head`，`offset` 为模型像素，`rotation` 为角度，`scale` 为倍率，默认分别 `[0,0,0]`、`[0,0,0]`、`[1,1,1]`。可用 `source_slot` 和 `source` 将帽子 UV 映射到当前机壳的指定源矩形；省略则保留模型原贴图。模型层 `emissive` 使用满亮度。
- 蜘蛛每种材质只选一张眼睛 PNG，替换而非叠加默认眼睛；透明处露出机壳，内置蜘蛛不再添加帽子模型。所有眼睛统一在 `assets/create_biotech/textures/entity/spider_assembly_table/`，共用 64×32 画布和 UV（头部正面 `[40,12,8,8]`）：`eye_00.png` 是默认眼睛，`eye_01.png` 是另一张通用候选，`eye_copper_casing.png` / `eye_railway_casing.png` 只包含从 Create 包裹抽取的眼睛像素，不带纸箱底色。
- 眼睛层已开启 `material_variants: true` 命名糖，角色由默认文件名确定：`eye_00` 只查 `eye_`，`body_` 只查机壳专用 `body_`；其他默认名不猜角色，只使用显式贴图。在默认贴图同目录内，优先 `<命名空间>/<角色>_<机壳名>.png`，其次 `<角色>_<机壳名>.png`。眼睛再尝试编号候选，最后默认 `texture`；身体没有编号候选。例如 `create/eye_copper_casing.png` 优先于 `eye_copper_casing.png`，绝不读取 `body_copper_casing.png` 或无前缀的 `copper_casing.png`。嵌套材质 `addon:machines/casing` 对应 `addon/machines/eye_casing.png`。
- 通用候选**仅眼睛**：同命名空间、同一直接目录的 `eye_<非负整数>.png` 按数字递增排序（允许编号缺口；同编号按文件名排序）。按同步材质表的零起始索引 `%` 有效候选数量循环分配；材质表按命名空间、路径排序，专用图对应的材质也占索引。同机壳固定一种，不随时间切换；材质表或候选集变化后分配可能变化。扫描仅在资源重载时进行，候选尺寸和 cutout 校验结果缓存；无效候选不占位置，全透明候选有效。
- 身体优先使用专用素材：目标的 `body_textures` 指定编辑目录（蜘蛛为 `create_biotech:entity/spider_assembly_table`），先查 `<命名空间>/body_<机壳名>.png`，再查 `body_<机壳名>.png`；嵌套材质路径规则与眼睛相同。无有效专用图才使用机壳源纹理的自动 UV 映射，绝不扫描或分配 `body_编号`。因此 `body_andesite_casing.png` 在正常渲染中直接生效，不只是回退素材。
- 专用身体使用目标尺寸的 UV 画布（蜘蛛 64×32，允许等比整数倍高清）；透明区域保持透明，不透出生成材质。它先替换身体底图，再应用显式 `overlay` 和按 `index` 排序的图层；显式图层优先，眼睛独立替换。身体不自动发光，复用现有 UV 解释器；无碎片的专用 body 不生成额外副本；不存在、尺寸不符或不支持的透明度会跳过专用候选。
- 角色前缀只用于这些可替换 PNG，不改变机壳来源的普通 / `_connected` 纹理查找。候选尺寸不兼容时跳过；全透明专用图仍有效（隐藏眼睛）。普通贴图层默认不启用命名糖。
- 显式 `material_overrides.<机壳ID>.layers` 仍优先；直接指定 `texture` 且省略 / 关闭 `material_variants` 即可固定眼睛。内置材质覆盖只选择机壳源图并继承单一眼睛层；铜 / 铁路的物流帽和列车帽示例已移除。资源包可覆盖，F3+T 清除缓存重载；半透明层不受支持，会回退基础外观。


- 目标定义：`assets/<namespace>/casted_materials/targets/<path>.json`，ID 为 `<namespace>:<path>`。
- 材质 slot 配置：`assets/<namespace>/casted_materials/materials/<material-path>.json`。
- [正式蜘蛛定义](src/main/resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json)可作为格式示例。
- 目标宽、高为 1～256；源网格不受该上限限制，源图需为逻辑网格的整数等比缩放。
- 显式源配置优先；默认模型贴图不满足网格时，尝试同名 `_connected`。

预算只由目标 JSON 的 `render_policy` 控制，不再提供材质渲染客户端配置组。缺省值为：

```json
"render_policy": {
  "max_pieces_per_face": 64,
  "max_additional_quads": 2048,
  "max_texture_batches": 4
}
```

几何预算按所选路径的实际模型计算；合成路径不增加身体 quad，不依赖 `computed_cost` 声明。
旧 JSON 的 `mode` 仅保留语法兼容；后端自动选择，不新增客户端配置项。

生成纹理缓存最多保留 128 种外观／16 MiB RGBA 像素载荷，跨模型实例共享。标准 64×32 身体只上传
8 KiB，有发光眼时额外缓存一张 8 KiB 发光 mask；不再上传整张 512×512 图集，也不逐帧合成。
容量或上传失败时保留直接 UV，不在一帧中途驱逐仍被绘制批次引用的纹理。F3+T 主动释放生成纹理并
清除模型、来源与候选缓存，即使之后没有装壳蜘蛛参与渲染也会释放。

装壳只设置外观，生存和创造模式均不消耗手持机壳；已有机壳不会直接替换。
普通或潜行使用扳手都先清除外观，不返还机壳，也不拆除工作台；无壳时沿用正常扳手行为。
为兼容存档，保留 `casted_materials:material` 组件、`casted_materials:material_palette` payload 和
`CastedMaterial` NBT 字段。这不是独立 Mod；迁移旧实例时应移除旧 Casted Materials JAR。
Sable Companion 嵌入不变，原模块 [MIT 声明](src/main/resources/META-INF/licenses/casted-materials-MIT.txt)保留。

[制作工具](tools/README.md)支持图片推断、RGB 容差、模型面 UV 读取、最小源外接框与限时选区优化。
制作期源路径不等于运行时资源 ID，诊断预览不必打包。不再提供 Blockbench 配对编辑/导出入口，
仅保留可选 `.bbmodel` 读取器。更多运行时接口说明见[英文对应章节](README.md#internal-uv-material-rendering-1211)。

```powershell
./gradlew.bat --offline test build verifyMaterialRenderingModule
python -B -m pytest -q -p no:cacheprovider tools
python -B -m unittest discover -s tests -p 'test_*.py'
```

## 仓库结构

```
src/main/java/com/nobodiiiii/createbiotech/
  CreateBiotech.java        # @Mod 入口，负责注册表与事件总线接线
  registry/                 # CBBlocks、CBItems、CBFluids、CB*Types 等 Registrate 注册
  content/                  # 按特性分包（slimebelt、ghasthotairballoon、processing/basin…）
  client/                   # 仅客户端的渲染器、粒子、GUI 钩子
  network/                  # CBPackets 与各类数据包定义
  mixin/                    # Create 与原版 mixin（完整列表见 mixins.json）
  compat/                   # JEI、Jade 集成
  ponder/                   # Ponder 场景脚本
  foundation/, infrastructure/, event/   # 共享工具、GUI 与装置移动辅助

src/main/resources/
  assets/create_biotech/    # 模型、贴图、lang（en_us.json、zh_cn.json）、ponder
  data/                     # 配方、tag、进度（手写 + 数据生成混合）
  META-INF/mods.toml        # 模组元数据，按 gradle.properties 模板化
  create_biotech.mixins.json

ref/                        # 仓库内置的 Create + JEI 参考源码
run/                        # 开发期运行时（存档、配置、日志）
tools/, test.py             # 辅助脚本；test.py 驱动 quickPlayClient
```

## 工作约定

默认按下面的习惯工作：

1. **`ref/Create/` 与 `ref/jei/` 是 Create 和 JEI 的本地权威源码。** 修改集成代码前先用 `rg` 在这里搜索。**不要**反编译 jar，也不要联网获取上游代码，除非用户明确要求。
2. **注册一律走 Registrate。** 新的 block / item / fluid / entity / menu 加在对应的 `registry/CB*.java` 里，不要另起炉灶。
3. **特性代码集中在 `content/` 下的单一包内。** 每个特性尽量自带 block、block entity、渲染器、item 和相关 handler。跨特性的公用代码放 `foundation/` 或 `infrastructure/`。
4. **修改 Create 或原版行为通过 mixin 完成。** 每个新 mixin 都要登记到 [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json)；涉及客户端代码的放到 `client` 列表。
5. **lang 键双语对齐。** 每新增一个 key，`en_us.json` 与 `zh_cn.json` 都要补。

## 主要系统速览

- **自定义传送带** —— 史莱姆传送带、岩浆怪传送带、人力传送带。它们都按 Create 传送带的使用习惯来做，并支持漏斗、通道和相关 mixin 行为。
- **工作盆生物加工** —— 实体可以通过漏斗进入工作盆，再被机械压机或搅拌器当作配料处理。配方位于 `content/processing/basin/`。
- **装置 (contraption)** —— 恶魂热气球可以组装成移动装置；缓冲垫提供按颜色区分的移动行为。
- **专用机械** —— 蜘蛛组装台、鱿鱼打印机（附魔书复印）、唤魔者附魔室、苦力怕爆破室、生物打包机、经验泵 / 经验簇 / 经验罐、薛定谔的猫（量子红石）、万向节（三维旋转传递）、骨棘轮、史莱姆离合器、固定胡萝卜钓竿、防爆物品仓、纸箱捕获生物。
- **流体** —— 液态活史莱姆，以及一种与经验等价的流体。

打开 [src/main/java/com/nobodiiiii/createbiotech/content/](src/main/java/com/nobodiiiii/createbiotech/content/)，对照子文件夹名称就能快速找到对应特性。

## 许可证

本仓库不是单一许可证结构。

原创代码采用 MIT，Create: Biotech 原创资源通常为保留所有权利，另外仓库中还包含改编自 `SylviaX-390/createbuttercat` 的 MIT 许可内容。详见 [LICENSE.md](LICENSE.md) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
