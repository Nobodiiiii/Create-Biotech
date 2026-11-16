# 捕捉网

右键生物捕捉，蹲下右键方块、空气或生物可放出。每张网容纳一个生物，可重复使用。
判定使用生物的**当前血量**，2 点血量为 1 颗心，因此可以先削弱生物再捕捉。

| 品质 | 默认可捕获血量上限 |
| --- | ---: |
| 木质 | 8 |
| 石质 | 12 |
| 铁质 | 20 |
| 金质 | 16 |
| 钻石 | 30 |
| 下界合金 | 40 |

服务端配置 `create_biotech-server.toml` 的 `[captureNet]` 节可分别调整
`woodenMaxHealth`、`stoneMaxHealth`、`ironMaxHealth`、`goldenMaxHealth`、
`diamondMaxHealth` 和 `netheriteMaxHealth`。

`fastestEscapeSeconds` 默认为 10，`slowestEscapeSeconds` 默认为 60。
捕获时固定挣脱时间，按当前血量占该网血量上限的比例线性插值：

```text
挣脱秒数 = 最慢秒数 - (最慢秒数 - 最快秒数) × 当前血量 / 网的血量上限
```

例如铁网捕捉 20 血生物约 10 秒后挣脱，捕捉 10 血生物约 35 秒后挣脱。
倒计时使用世界游戏刻；切换背包格、交给其他玩家、存取容器不会重置，世界暂停时不计时。
在玩家背包或作为掉落物时，每 10 刻检查并尝试放出。容器中不主动生成生物，
超时后取回背包或丢出会在下次检查时尝试挣脱。

释放会检查周围 3 格内的空间、世界边界和已加载区块；没有空间时保留生物并重试，
不会清除网中数据。保存和还原复用纸箱的捕获数据路径，保留名称、血量、装备及生物数据，
兼容仿生生物已有的发布版本迁移。
玩家、非生物实体、死亡生物和无法保存的实体不能捕捉。
带乘客、正在骑乘或拴绳中的生物需先解除连接。
实体类型标签 `create_biotech:capture_net_blacklist` 默认排除末影龙和凋灵，可通过数据包扩展。

木至钻石品质使用对应材料 ×2、线 ×4、木棍 ×1 合成；下界合金品质由钻石网、
下界合金升级模板和下界合金锭在锻造台升级，保留原网数据。六种网均加入设备与装备创造栏。

模型以用户提供的 `art/reference/capture_net.png` 为造型参考，使用简单方形网框、
细长木柄和金属接头。六种品质共用结构，分别使用对应材料的网框贴图。
体块的端点、长宽高及 UV 贴图像素坐标均为整数。
木柄与网框的每个面都有独立的 UV 区域，保持 **1 模型像素 = 1 贴图像素**。
64×64 材质图使用整像素的木纹、磨损和材质明暗色阶；UV 周围保留 1 像素的边缘扩展。

网兜只有 **1 个竖直长方体**，范围为 `[3, 9, 9]` 至 `[13, 19, 15]`，
只在它的外表面绘制透明方格，不拼接片状体块，也没有内部网线几何。
下沿收至网框底部，不再向木柄方向垂出。前侧没有面，作为网框入口；其余五面完整贴图。
网兜使用 `entityCutoutNoCull` 双面绘制，确保从入口看过去仍能看到后壁的内侧。
网格材质使用 **1 模型像素 = 1 贴图像素**，网线宽度为 1 像素、间隔为 2 像素，
不使用半像素材质或缩放贴图。颜色直接采自原版 Minecraft 1.21.1 的蜘蛛网贴图：
`#FFFFFF`、`#E4E9E9`、`#C4CED2`。
原始取色图保存在 `art/reference/cobweb.png`，提取自本地 Minecraft 资源包的
`assets/minecraft/textures/block/cobweb.png`。

Blockbench MCP 编辑源为 `art/capture_net.bbmodel`，含 `grip → frame / bag`
层级和 `capture`（0.6 秒）、`release`（0.6 秒）、`occupied`（2 秒循环）三个动画。
捕捉和放出经过蓄力、挥动、回弹及归位。所有动画只驱动共同的 `grip` 根节点，
网框与网兜始终保持固定连接；装有生物时整张网以不超过 1.5° 的短促颤动表现挣扎。
动画仅使用刚性旋转、位移，左右手镜像，不拉伸网兜或体块。

保存 Blockbench 源文件后，运行 `python tools/generate_capture_net_assets.py`（需要 Pillow），
可重建像素贴图、六种品质模型、单个网兜模型和 `CaptureNetAnimation.java`。
游戏动画直接使用导出的关键帧与枢轴，按 Generic Model 的 ZYX 旋转顺序执行。
关键帧使用单点线性插值；捕捉与放出总时长对应物品的 12 刻冷却。
导出器会拒绝小数坐标、半像素 UV、非 1:1 贴图密度、膨胀和缩放动画。
物品栏使用 `[-29.13, 157.28, 38.71]` 的 XYZ 旋转，与参考图的网框边方向对齐，
并调整居中与等比显示尺寸；六种材质共用同一组显示参数。
合成配方、捕捉判定及存档数据不由此美术导出器改写。

实现参考：

- `ref/1.21.1/Create/src/main/java/com/simibubi/create/content/equipment/wrench/WrenchItemRenderer.java`
- `ref/1.21.1/Create/src/main/resources/assets/create/models/item/wrench/item.json`
- `src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalKitItemRenderer.java`
- `src/main/java/com/nobodiiiii/createbiotech/content/cardboardbox/CapturedEntityBoxHelper.java`
