# 仿生特性扩展示例

- 将 `datapack/` 整个目录放入世界的 `datapacks/`，执行 `/reload`。示例在特性内部命名 `#frost_hand` 为“霜化手臂”，独立绑定文件精确指定僵尸的手臂模型名，提供 `example:frost_arm`，装在手臂槽位且覆盖率达到 60% 后，成功近战会施加 5 秒缓慢。
- 安装 KubeJS 2101.7.2-build.381 后，可单独将 `kubejs/server_scripts/bionic_traits.js` 放入实例对应目录并执行 `/reload`。牛组织提供再生特性；脚本展示数据定义、独立绑定、修改、tick 和受伤回调。
- 两个示例互相独立，可任选其一。移除示例文件后再次 `/reload`，其自定义特性消失。
- 附属模组可以直接内置同样的 `data/` 资源。

完整格式和 Java 接口见 [扩展说明](../../docs/bionic-traits-extension.zh-CN.md)。
