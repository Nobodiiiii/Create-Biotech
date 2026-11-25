# 资源排布与资源包升级

资源以 Minecraft 1.21.1 和 Create 6.0.10 为基准。发布包的客户端资源和服务端数据共用根级包描述，供 NeoForge 按包类型确定兼容性。

- `blockstates/<方块 ID>.json` 和 `models/item/<物品 ID>.json` 使用注册 ID。状态表只列影响模型的属性。单一选择器用 `variants`，可独立叠加的部件用 `multipart`。
- 简单模型和贴图使用内容 ID。多部件设备在 `models/block/<设备 ID>/` 和 `textures/block/<设备 ID>/` 内按部件命名。共享的机器生物模型仍按物种组织。
- 普通手写资源在 `src/main/resources`。构建用 `generateCompactResources` 展开 `src/main/resource-templates` 内的传送带状态、放鱼机物品模型和蜂巢仪表簇模型。仪表模板的每行朝向依次对应 0°、90°、180°、270°。标签 datagen 的输出在 `src/generated/resources`。
- `data` 下的配方、掉落表、进度、标签和可重载自定义定义沿用加载器路径。Ponderer 的结构、文本及导出清单按场景维护。

## 资源包路径更新

覆盖以下旧文件的资源包应采用新路径。方块、物品及方块状态属性的存档名称沿用原值。

| 资源 | 旧路径 | 新路径 |
| --- | --- | --- |
| 皓蓝梯贴图 | `textures/block/ladder_asurine{,_hoop}.png` | `textures/block/asurine_ladder{,_hoop}.png` |
| 皓蓝铁栏杆模型 | `models/block/asurine_<部件>.json` | `models/block/asurine_bars_<部件>.json` |
| 皓蓝脚手架贴图 | `textures/block/scaffold/asurine_scaffold{,_connected,_inside,_inside_connected}.png` | `textures/block/asurine_scaffolding/{side,side_connected,inside,inside_connected}.png` |
| 皓蓝脚手架顶部 | `textures/block/funnel/asurine_funnel_frame.png` | `textures/block/asurine_scaffolding/top.png` |
| 电脑模型和方块贴图 | `block/biocomputing/computer/<部件>` | `block/computer/<部件>` |
| 苦力怕爆破室显示器模型和贴图 | `block/blast_chamber_display/<部件>` | `block/creeper_blast_chamber/display/<部件>` |
| 苦力怕爆破室附属模型和物品侧面 | `block/creeper_blast_chamber_{formed,item,item_side}` | `block/creeper_blast_chamber/{block_formed,item,item_side}` |
| 末影库存管理器世界模型、OBJ、MTL | `models/block/enderman_portal/enderman_portal.*` | `models/block/enderman_stock_keeper/portal.*` |
| 末影库存管理器传送门贴图 | `textures/block/enderman_portal.png` | `textures/block/enderman_stock_keeper/portal.png` |
| 万向联轴器部件模型和史莱姆贴图 | `block/universal_joint_endpoint{,_slime_overlay}` | `block/universal_joint/endpoint{,_slime_overlay}` |
| 半轴、恶魂热气球装配站、岩浆怪燃烧器、唤魔者附魔室附属模型 | `block/half_shaft_world`、`block/ghast_hot_air_balloon_assembly_station_powered`、`block/magma_cube_burner_burning`、`block/evoker_enchanting_chamber_placeholder` | `block/half_shaft/world`、`block/ghast_hot_air_balloon_assembly_station/block_powered`、`block/magma_cube_burner/block_burning`、`block/evoker_enchanting_chamber/particle` |
| 生物打包机世界模型 | `block/bio_packager/horizontal_<状态>` 和 `block/bio_packager/vertical_<状态>` | 水平用 `block/bio_packager/block{,_powered}`，垂直用 `block/bio_packager/block_vertical{,_powered}` |
| 生物打包机物品模型部件 | `block/bio_packager/inventory` | `block/bio_packager/item` |

当前创造栏分类使用 `work_in_progress` 图标。旧 `logistics` 与 `structures` 图标已从内置资源移除。
