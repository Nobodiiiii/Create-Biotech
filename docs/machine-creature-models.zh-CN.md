# 生物机器模型 JSON

生物机器采用两层模型：不动的机器结构仍使用普通方块模型，需要动画的生物结构从
`assets/create_biotech/models/machine_creature/` 加载。客户端资源重载时，JSON 会重新解析并烘焙为
`ModelPart`；动画、材质选择、发光层和透明层仍由 Java Renderer/Visual 控制。

## 格式

```json
{
  "format_version": "1.0.0",
  "description": {
    "identifier": "create_biotech:example",
    "texture_width": 64,
    "texture_height": 32,
    "root_bone": "root"
  },
  "bones": [
    {
      "name": "root",
      "pivot": [0, 24, 0]
    },
    {
      "name": "body",
      "parent": "root",
      "pivot": [0, -4, 0],
      "rotation": [0, 0, 0],
      "cubes": [
        {
          "origin": [-4, -4, -4],
          "size": [8, 8, 8],
          "uv": [0, 0],
          "inflate": 0,
          "mirror": false
        }
      ]
    }
  ],
  "anchors": {
    "tool_socket": {
      "bone": "body",
      "position": [0, 0, -4]
    }
  }
}
```

格式借鉴基岩版的骨骼、枢轴和方块表达，但坐标直接对应 Java 版 `ModelPart`：

- `pivot` 是相对于父骨骼的平移，单位为模型像素。
- `rotation` 使用角度，旋转顺序与 `ModelPart` 一致。
- `cube.origin` 是相对于所属骨骼枢轴的坐标。
- `cube.size` 允许某一轴为 `0`，用于鳍、翅膀等平面。
- `inflate`、`mirror`、`visible` 和 `skip_draw` 均可省略。
- `root_bone` 可省略；指定后，该骨骼会成为 Renderer 取得的模型根。
- `anchors` 表示机器安装点。`bone` 使用骨骼名，也可使用 `$root`；`position` 位于该骨骼的局部坐标系。

Java 端会校验每台机器动画所需的骨骼和锚点。资源包删除这些结构、制造重复骨骼、循环父子关系，
或提供非有限数值时，资源重载会拒绝该组模型；已有的有效模型会继续使用。
