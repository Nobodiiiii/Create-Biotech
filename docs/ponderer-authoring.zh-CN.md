# 思索场景与自动导出

19 组场景使用 Ponderer 的标准 **Java + Lib** 导出结构。`ponder/generated/` 和 `.ponderer-export/manifest.json` 由真实导出器生成；不要在这些 Java 文件里添加手写代码。

Java 输出使用直接参数调用，每个分段独立成方法，例如 `scene.idle(20)`、`scene.keyframe().showText(...)` 和 `scene.setBlock(...)`。步骤 ID 全部由 API 内部生成和保存，插入步骤无需编号；源码无需 `.at(...)`，也没有额外的 ID 对照资源。

| 内容 | 维护位置 |
| --- | --- |
| 可编辑场景文档 | `examples/ponderer/scripts/*.json` |
| 场景多语言文本 | `src/main/resources/assets/create_biotech/ponderer/texts/<场景>/<语言>.json` |
| 生成的 Java 文档、索引和客户端入口 | `src/main/java/com/nobodiiiii/createbiotech/ponder/generated/` |
| 自定义步骤注册入口 | `src/main/java/com/nobodiiiii/createbiotech/ponderext/PonderExtensions.java` |
| 悦灵配送、压机、玩家、药水、步行动画和潜影打包机 | `ponderext/*Effects.java` |
| 场景范围 | Lib 根据结构和普通步骤坐标维护，无需额外的 Java 边界回调 |
| 播放器增强请求 | `ponderext/PonderClientFeatures.java` |
| 苦力怕压缩和机械压机专属实现 | `ponder/PonderSupportExt.java` |

`PonderExtensions.java` 只在首次导出时创建，后续导出不会覆盖；其他扩展类也不属于导出器管理范围。24 个自定义步骤通过 `engine.steps().registerJava` 注册，编辑器显示中文名称和 Java 标记，保留调用位置并跳过 Java 实现解析。普通动作仍可编辑。

生成 Java 使用 `scene.showText(text("scene_1.text_1"), ...)` 引用文本，正文和标题分别维护在各场景的 `zh_cn.json` / `en_us.json` 中，不再合并进模组总语言文件。已有文本键在调整步骤顺序时保留；手写内容可使用语义键。Lib 负责语言选择、英文回退、资源包覆盖和重载。编辑器本地保存引用及语言覆盖，再次导出会合并到独立文本文件。

文本取值 Mixin 随新版 Lib 分发并由导出能力声明启用，无需给本模组添加 Mixin 配置。资源包可覆盖单个场景的单个语言文件；该文件整体替换，缺少的条目回退到英文。署名分类的中英文资源统一由 Lib 提供。

## 编辑与再次导出

1. 同时安装当前版本的完整 Ponderer、Ponderer Lib 和本模组。在思索列表或物品的思索界面中编辑 `create_biotech:*` 场景；场景由宿主注册，不需要重新导入旧的 `ponderer:*` 文档。
2. 保存步骤修改后，可通过 Ponderer 重载文档。修改 `*Effects.java`、`PonderSupportExt.java` 等 Java 实现后，需要重新编译并启动客户端。
3. 使用 **Java + Lib** 导出到本项目根目录 `D:/Files/create_biotech`，选择已迁移的 `create_biotech:*` 场景。场景 ID 与 `runtimeSource` 保持一致，分段 ID 保持稳定；步骤 ID 由 API 管理，无需手写。
4. `examples/ponderer/scripts` 保存本次迁移后的文档，可作为源文件维护或恢复本地编辑覆盖。文档的 `runtimeSource` 指向同名宿主场景，结构来自本模组已有资源；恢复后仍需加载本模组的 Java 扩展。继续编辑产生的新版本需要同步回这些源文件。

场景 ID 统一为 `create_biotech:*`，故事板路径、物品绑定、已有结构资源、中英文本和 Create 分类保持对应。生成类名末尾的标识随场景命名空间变化，由导出器生成；请通过 `GeneratedPonderIndex` 注册，不要直接引用某个生成类。

修改过生成 Java 时，导出器会拒绝覆盖。应先将手写部分迁到 `ponderext`，不要改写 manifest 中的快照绕过保护。其他导出模式有不同的运行方式；本项目中的 Java 扩展以 Java + Lib 为维护入口。

## 运行库与构建

宿主使用外置 `com.nododiiiii:RuntimeNeoForge:0.1.0`，不通过 JarJar 内嵌。默认开发仓库为相邻 Ponderer 项目的 `build/lib-repository`，可通过 `-Pponderer_lib_repository=<目录或仓库URL>` 指定其他仓库。

```powershell
# 相邻的 ponderer 仓库
.\gradlew.bat :RuntimeNeoForge:build :RuntimeNeoForge:publishMavenJavaPublicationToLibStagingRepository --offline
# 本仓库
.\gradlew.bat build --offline
```

安装时单独放置与当前导出 API 兼容的 `pondererlib-1.21.1-neoforge-0.1.0.jar`。完整 Ponderer 是编辑器，可选；上游 Ponder 仍由宿主现有依赖提供。能力声明位于 `META-INF/pondererlib.features.properties`，播放器增强请求的所有者为 `create_biotech`，用户的功能开关优先。

参数调用形式依赖新版 Lib 的 `JavaScene` API，旧 `.at(...)` 接口已移除。本地 Maven 仓库已更新；运行客户端时也需要使用本次构建的 Lib JAR。本次自动 ID 改动前的生成代码和源文档备份在 `build/ponder-internal-ids-before.zip`；更早的参数格式调整备份在 `build/ponder-parameters-before.zip`。

拆分独立文本资源前的生成源码、源文档、语言文件、扩展与清单备份在 `build/ponder-text-resources-before.zip`。本次 19 组场景的源 JSON 已同步改用文本引用，需与 `ponderer/texts` 资源一起维护或加载。

迁移前的自定义 Java、manifest、语言文件及能力资源备份在 `build/ponder-migration-before.zip`。本次未改变黄油猫引擎、智能强力胶的独立故事板，也未修改存档格式。
