# 手术台操作失败提示总表

默认失败提示为“操作失败”。只有在空间检查明确失败，或排布搜索确认找不到可用位置时，才显示“手术台没有足够位置放置”。无法确定原因的搜索失败仍回退到“操作失败”。调整导致的断连统一使用“调整超出范围”，优先于空间提示；第三阶段因调整或空间校验失败时，保留预览供继续调整。

本表共 22 项。语言键均以 `message.create_biotech.surgical_table.` 为前缀；下表只列后缀。

| 语言键后缀 | 中文提示 | 触发条件 |
| --- | --- | --- |
| `operation_failed` | 操作失败 | 未能确定具体原因的操作或校验失败；包括剪切、装箱、复制、组合和服务端拒绝。 |
| `no_space` | 手术台没有足够位置放置 | 已确认占位越出台面、与其他部件冲突，或有效排布搜索找不到满足间隙要求的位置。 |
| `preview_changed` | 手术台上的部件已变化，请重新选择后再操作 | 确认旋转、粘结或镜像粘结时，预览对应的台面部件状态已变化。 |
| `different_table` | 只能连接同一手术台工作面上的部件 | 粘结时，两次选中的部件属于不同的手术台工作面。 |
| `edit_limit` | 调整超出范围，请继续调整或左键放弃 | 扳手或智能强力胶累计位移超过 4 格、粘结的垂直调整超限、单体块旋转后与所有原邻居失去接触、旋转会断开活动关节，或智能强力胶第三阶段目标体块失去接触。 |
| `invalid_capture` | 纸箱内容已失效 | 纸箱或临时收纳中的捕获数据无效、已失效，或无法还原实体。 |
| `unsupported_subject` | 手术台只能放置拟态生物 | 尝试将不支持的生物放到手术台上。 |
| `invalid_assembly` | 纸箱中的拟态生物没有有效的组装数据 | 仿生生物缺少有效组装数据，或组装来源布局不合法。 |
| `model_unavailable` | 无法获取该生物的可切分模型 | 放置时无法取得可切分模型；装箱或复制时无法取得所需身体几何数据。 |
| `model_changed` | 该躯体的模型与保存时不一致，只能铲除或破坏手术台取回 | 客户端观察到的模型拓扑与台面保存的模型不匹配。 |
| `invalid_table` | 手术台工作区域无效 | 无法取得有效的手术台控制器或工作区域。 |
| `table_full` | 手术台容纳的生物数量已达上限 | 放置所需的生物来源记录数超过每个连通工作面的硬编码上限 16,384；目前不可配置。 |
| `glue_contact_required` | 两个部件必须接触才能粘结，请重新选择粘合位置 | 初次智能粘结或镜像粘结时，两个目标体块没有满足接触条件。 |
| `symmetry_target_required` | 未选中有效的镜像参考方块 | 镜像粘结时没有选中有效的参考方块。 |
| `symmetry_plane_required` | 未选中有效的对称面 | 镜像粘结时没有选中有效的镜像平面。 |
| `limb_not_connected` | 关节只能连接两个直接相连的部件 | 安装活动关节的两个部件之间没有直接连接。 |
| `limb_same_combination` | 这两个部件已被蜂蜜固化为一体，无法转动 | 安装关节的两个部件已经属于同一个蜂蜜组合。 |
| `limb_already_driven` | 该部件已由另一个关节驱动 | 安装关节的活动部件已经由另一个关节驱动。 |
| `limb_limit` | 这具身体无法再安装该种关节 | 该身体安装的同种一级关节已达到数量上限。 |
| `limb_secondary_limit_elbow` | 该关节无法容纳更多的肘关节 | 安装肘关节会超过对应关节的容量。 |
| `limb_secondary_limit_knee` | 该关节无法容纳更多的膝关节 | 安装膝关节会超过对应关节的容量。 |
| `limb_primary_conflict` | 一级关节不能安装在另一个一级关节上，且活动部件必须由该关节唯一依附 | 一级关节与其他一级关节或活动部件的依附关系冲突。 |

实现入口：

- `src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalTableFailure.java`：通用失败提示。
- `src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalTablePlacementResult.java`、`SurgicalTableLayout.java`：放置失败和空间分类。
- `src/main/java/com/nobodiiiii/createbiotech/content/surgery/SurgicalWrenchRotation.java`：旋转失败分类。
- `src/main/java/com/nobodiiiii/createbiotech/content/surgery/client/SurgicalTableClientHandler.java`：客户端剪切、粘结和预览确认。
- `src/main/resources/assets/create_biotech/lang/zh_cn.json`、`en_us.json`：中英文提示文本。
