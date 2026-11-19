# Maven 依赖与 Minecraft 1.21.1 发布

当前分支使用 Minecraft 1.21.1、NeoForge 21.1.234、Java 21。依赖配置位于根目录的 `gradle.properties` 和 `build.gradle`；发布脚本需要 Python 3.11 或以上。

## Ponderer 依赖

参考 `D:\Files\ponderer\NeoForge\runtime\build.gradle`、`docs/PONDERER_LIB.md` 和 `.github/workflows/release-1.21.1.yml`。参考项目的自有 Maven publication 仍只暂存到本地，因此本项目使用已经公开的 Modrinth Maven：

| 用途 | 版本 | Maven 坐标 |
| --- | --- | --- |
| 编译、开发运行及 Modrinth 内嵌 Lib | 0.1.1，NeoForge 1.21.1 | `maven.modrinth:voXQB5Hr:zGf6mIsf` |
| 开发运行编辑器 | 1.12.0，NeoForge 1.21.1 | `maven.modrinth:the-ponderer:8UWz9exc` |

Lib 和编辑器属于同一个 Modrinth 项目，必须使用不同的模块别名，避免 Gradle 的同模块版本冲突处理替换掉其中一个。版本 ID 固定具体加载器文件，不依赖同名版本在不同加载器之间的选择顺序。更新依赖时同时更新可读版本号、坐标和 Lib 的兼容范围。

无需复制 `libs/`、使用 `mavenLocal()` 或构建相邻仓库。开发运行同时加载 Lib 和编辑器；发布产物不携带编辑器。`syncQuickPlayMods` 将 Maven 文件重命名为原有启动脚本管理的 `pondererlib-…jar` 和 `ponderer-…jar`，保留旧版清理逻辑。

## 两站产物

执行 `./gradlew build` 会生成两份同名、内容不同的分发 JAR：

| 路径 | Lib 分发方式 | 平台依赖声明 |
| --- | --- | --- |
| `build/release/curseforge/create_biotech-<mc>-<version>.jar` | 外置，玩家单独安装 Ponderer Lib | Create 与 `ponderer-lib`（项目 ID `1808352`）为 requiredDependency |
| `build/release/modrinth/create_biotech-<mc>-<version>.jar` | 通过 NeoForge JarJar 内嵌 | Create 必需，具体 Lib 版本标为 embedded |

普通 `build/libs/create_biotech-<mc>-<version>.jar` 仍要求外置 Lib，用于开发和快速启动。Lib 的模组 ID 始终为 `pondererlib`，宿主元数据保留必需依赖，内嵌时由加载器满足。Modrinth 分发保留基础 JAR 的全部内容和原有 JarJar 依赖，增加独立的 Lib JAR，不合并 Lib 的类。内嵌记录使用其自有 Maven 身份 `com.nododiiiii:RuntimeNeoForge`、语义版本 `0.1.1` 和兼容范围 `[0.1.1,0.2.0)`，不把下载用的 Modrinth 版本 ID 当作库版本。Lib 自带的 Ponder/Flywheel 后备依赖保留原样；其 Ponder 身份已按参考项目与 Create 提供的新版协调。

## 本地仅测试

```powershell
.\gradlew.bat build syncQuickPlayMods --console=plain
python publish.py --dry-run --skip-build --non-interactive
python publish.py --dry-run --skip-build --non-interactive --only-curseforge
python publish.py --dry-run --skip-build --non-interactive --only-modrinth
```

`--dry-run` 不读取 `.env`、不要求发布令牌、不请求上传 API。它检查实际 JAR 的模组身份、版本、Lib 必需依赖、内嵌数量及 JarJar 身份，打印各站将使用的文件和依赖关系。缺少产物或打包方式错误时返回非零退出码。`--skip-build` 只检查已有分发文件，支持 CI 下载的产物。以上命令不启动游戏，也不创建测试文件。

## GitHub Actions

仅支持 `1.21.1` 分支的手动运行，无 push 或 tag 自动发布触发器：

- **全部发布**：构建、检查两份产物，再按选择发布两站。
- **发布 CurseForge**：复用同一工作流，只选择 CurseForge 上传任务。
- **发布 Modrinth**：复用同一工作流，只选择 Modrinth 上传任务。

三个入口的 `dry_run` 默认均为 `true`，只构建、运行现有检查、检查分发并保存 `create-biotech-release-jars` 工件。上传任务只在明确关闭 `dry_run` 时运行，读取环境 `release-1.21.1` 中对应的 `CURSEFORGE_TOKEN` 或 `MODRINTH_TOKEN`。构建任务不接收令牌。上传下载已经检查过的工件，不重新构建；脚本上传前再次检查目标文件。

`version_type=auto` 根据 `mod_version` 中的 `alpha`/`beta` 判断，否则使用 release；也可明确选择类型。更新日志取对应版本的 `CHANGELOG.md`，不存在时沿用脚本的 Git 提交说明回退，仍为空则终止。上传请求有超时，不自动重试创建版本；平台未确认返回有效文件 ID 时任务失败，重试前应检查平台是否已经接收文件。

本地脚本也支持 `--version-type beta`、`--non-interactive` 和平台选择；真实上传使用环境变量，或本地可选的 `.env`，同名环境变量优先。
