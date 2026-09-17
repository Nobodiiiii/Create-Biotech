# 材质制作工具

离线制作工具：从样例 PNG 和源材质推断 UV 映射，再输出预览与诊断。
Python 不参与游戏运行，也不进入发布 JAR；游戏端直接重映射模型 UV，
详见[项目 README](../README.md#internal-uv-material-rendering-1211)。

## 内容

| 目录 | 用途 |
| --- | --- |
| [material_mapping](material_mapping/README.md) | UV 推断与有序 cutout 贴图层的平面预览 |
| [resourcepacks/connected_spider](resourcepacks/connected_spider/README.md) | 已制作的蜘蛛映射与固定眼睛测试资源包 |
| [test-resources/fixtures](test-resources/fixtures/connected_spider/README.md) | Python 与 Java 共用的原图、源纹理和定义 |
| `tests` | 跨入口测试和共享辅助 |

## 安装与运行

需要 Python 3.10+。从仓库根目录运行：

```powershell
python -m pip install -r tools/requirements.txt
python -B -m tools.material_mapping infer --help
python -B -m tools.material_mapping preview --help
python -B -m pytest -q -p no:cacheprovider tools
```

也可通过 `python tools/material_mapping/cli.py infer ...` 调用。
脚本支持绝对路径和任意当前目录，不需要设置 `PYTHONPATH`。

## 可选模型输入

`infer --model model.bbmodel` 保留：由 `material_mapping/model_uv.py` 直接读取 JSON 中的 Cube 面 UV。
纯 PNG 模式不加载这个读取器。蜘蛛回归模型位于 `test-resources/fixtures/models/spider_body.bbmodel`。

不再包含 Blockbench 配对工程编辑、`init/validate/export`、预览工程生成或旧 CLI 转发入口。
因此不要使用旧的 `tools/blockbench/cli.py` 命令；读取 `.bbmodel` 不要求安装 Blockbench 软件。

## 三类数据不要混用

- **制作输入**：样例 PNG、源 PNG、逻辑网格和可选模型。`--source slot=本地路径` 不会作为运行时路径写入 JSON。
- **运行时数据**：schema 1 目标定义、材质 slot 配置和可选覆盖图；资源标识由命名空间与资源路径决定。
- **查看产物**：PNG 预览、未解决区域标记和报告。`preview` 按索引稳定叠加 cutout 贴图层；模型层只能进游戏检查，不会画进平面 PNG。

临时输出写到 `build/`；不要覆盖共享测试输入。完整参数、退出码和资源配置见[自动 UV 文档](material_mapping/README.md)。
