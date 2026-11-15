# 思索场景与自动导出

19 组场景使用 Ponderer 的标准 **Java + Lib** 导出结构。`ponder/generated/` 和 `.ponderer-export/manifest.json` 由真实导出器生成；可以手改场景中的 `JavaScene` 数据调用，Java 实现放在独立扩展类中。

Java 输出使用直接参数调用，每个分段独立成方法，例如 `scene.idle(20)`、`scene.keyframe().showText(...)` 和 `scene.setBlock(...)`。步骤 ID 全部由 API 内部生成和保存，插入步骤无需编号；源码无需 `.at(...)`，也没有额外的 ID 对照资源。

| 内容 | 维护位置 |
| --- | --- |
| 可编辑场景源码 | `src/main/java/com/nobodiiiii/createbiotech/ponder/generated/scenes/*.java` |
| 历史迁移文档 | `examples/ponderer/scripts/*.json`，不再作为 Java 场景的本地覆盖加载 |
| 场景多语言文本 | `src/main/resources/assets/create_biotech/ponderer/texts/<场景>/<语言>.json` |
| 生成的 Java 文档、索引和客户端入口 | `src/main/java/com/nobodiiiii/createbiotech/ponder/generated/` |
| 自定义步骤注册入口 | `src/main/java/com/nobodiiiii/createbiotech/ponderext/PonderExtensions.java` |
| 悦灵配送、压机、玩家、药水、步行动画和潜影打包机 | `ponderext/*Effects.java` |
| 场景范围 | Lib 根据结构和普通步骤坐标维护，无需额外的 Java 边界回调 |
| 播放器增强请求 | `ponderext/PonderClientFeatures.java` |
| 苦力怕压缩和机械压机专属实现 | `ponder/PonderSupportExt.java` |

`PonderExtensions.java` 只在首次导出时创建，后续导出不会覆盖；其他扩展类也不属于导出器管理范围。24 个自定义步骤通过 `engine.steps().registerJava` 注册，编辑器显示中文名称和 Java 标记，保留调用位置并跳过 Java 实现解析。普通动作仍可编辑。

生成 Java 使用 `scene.showText(text("scene_1.text_1"), ...)` 引用文本，正文和标题分别维护在各场景的 `zh_cn.json` / `en_us.json` 中，不再合并进模组总语言文件。已有文本键在调整步骤顺序时保留；手写内容可使用语义键。Lib 负责语言选择、英文回退、资源包覆盖和重载。编辑器直接写回目标工程的 Java 与独立文本资源。

文本取值 Mixin 随新版 Lib 分发并由导出能力声明启用，无需给本模组添加 Mixin 配置。资源包可覆盖单个场景的单个语言文件；该文件整体替换，缺少的条目回退到英文。署名分类的中英文资源统一由 Lib 提供。

## 编辑与再次导出

1. 同时安装当前版本的完整 Ponderer、Ponderer Lib 和本模组，执行 `/ponderer java bind D:\Files\create_biotech` 绑定源码工程。之后可在思索列表或物品的思索界面中编辑 `create_biotech:*` 场景；未绑定工程时只读，不需要重新导入旧文档。
2. 编辑器保存直接更新目标 Java、文本资源和清单，并刷新本次会话。手改 `JavaScene` 调用增删步骤后，在 V 界面点击“重载”；不通过 tick 监听。修改 `*Effects.java`、`PonderSupportExt.java` 等 Java 实现后，需要重新编译并启动客户端。
3. 使用 **Java + Lib** 导出到本项目根目录 `D:/Files/create_biotech`，选择已迁移的 `create_biotech:*` 场景。场景 ID 与 `runtimeSource` 保持一致，分段 ID 保持稳定；步骤 ID 由 API 管理，无需手写。
4. 临时场景覆盖在退出游戏时释放，Java 源码保留。下次启动使用模组已编译的内容；重新编译并加载新 JAR 后，修改才随模组生效。历史 JSON 留作迁移记录，不再参与 Java 场景重载。

场景 ID 统一为 `create_biotech:*`，故事板路径、物品绑定、已有结构资源、中英文本和 Create 分类保持对应。生成类名末尾的标识随场景命名空间变化，由导出器生成；请通过 `GeneratedPonderIndex` 注册，不要直接引用某个生成类。

完整再次导出仍会检查手改冲突。手改数据调用后，可先通过编辑器确认保存，由保存流程更新清单；任意 Java 实现应放在 `ponderext`，不要手动修改 manifest 快照绕过保护。其他导出模式有不同的运行方式；本项目中的 Java 扩展以 Java + Lib 为维护入口。

## 运行库与构建

开发依赖直接读取本项目 `libs/` 中的 JAR，不使用本地 Maven、相邻工程的 Maven 仓库或 `publishToMavenLocal`。版本号由 `gradle.properties` 中的 `ponderer_lib_version` 和 `ponderer_version` 决定：

- `libs/pondererlib-1.21.1-neoforge-0.1.0.jar`：编译和运行依赖，不通过 JarJar 内嵌。
- `libs/ponderer-1.21.1-neoforge-1.12.0.beta1.jar`：开发运行时使用的完整编辑器。

两份 JAR 均由 `syncQuickPlayMods` 暂存到 `build/quickplay/mods`，正常 quickPlay 构建与复制流程会同步到开发实例。`libs/*.jar` 为本机开发产物，不纳入 Git。

```powershell
# 关闭游戏后，在本仓库运行相邻 Ponderer 仓库的同步脚本
python ..\ponderer\sync_biotech.py

# 游戏运行期间，仅更新开发依赖与暂存目录
python ..\ponderer\sync_biotech.py --skip-instance
```

脚本只构建当前 Ponderer 与 Lib、同步版本号和 `libs/`，直接更新 `build/quickplay/mods/` 及实例中的编辑器与 Lib；不执行本工程的 Gradle 任务，也不替换 Biotech 本体。默认离线，构建限时 900 秒；`--online` 允许下载依赖，`--biotech` 和 `--instance` 可指定其他路径。完整用法见 Ponderer 仓库的 `docs/SYNC_BIOTECH.md`，构建日志位于该仓库的 `build/biotech-sync/`。

安装时单独放置与当前导出 API 兼容的 `pondererlib-1.21.1-neoforge-0.1.0.jar`。完整 Ponderer 是编辑器，可选；上游 Ponder 仍由宿主现有依赖提供。能力声明位于 `META-INF/pondererlib.features.properties`，播放器增强请求的所有者为 `create_biotech`，用户的功能开关优先。

思索半透明场景区块的延后绘制修复现由 Ponderer Lib 的 `core` 提供，本模组不再携带 `client.WorldSectionElementImplMixin`。更新开发依赖时需要同步包含此修复的 Lib JAR。

参数调用形式依赖新版 Lib 的 `JavaScene` API，旧 `.at(...)` 接口已移除。更新时同时替换 `libs/` 中的 Lib 与编辑器，再执行暂存任务；运行客户端时也需要使用对应的新 JAR。本次自动 ID 改动前的生成代码和源文档备份在 `build/ponder-internal-ids-before.zip`；更早的参数格式调整备份在 `build/ponder-parameters-before.zip`。

拆分独立文本资源前的生成源码、源文档、语言文件、扩展与清单备份在 `build/ponder-text-resources-before.zip`。本次 19 组场景的源 JSON 已同步改用文本引用，需与 `ponderer/texts` 资源一起维护或加载。

迁移前的自定义 Java、manifest、语言文件及能力资源备份在 `build/ponder-migration-before.zip`。本次未改变黄油猫引擎、智能强力胶的独立故事板，也未修改存档格式。
