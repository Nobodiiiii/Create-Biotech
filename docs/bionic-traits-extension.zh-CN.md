# 仿生特性扩展

适用 Minecraft 1.21.1、NeoForge 21.1.234。动态目录、行为组件、可选 KubeJS 插件与公开 Java API 已接入。内置目录包含 66 项特性；新增 ID 通过组件或事件执行行为。当前网络协议 **45**，联机双方需要更新本模组。

## 数据包和附属模组

基础定义仍使用 [第一阶段格式](bionic-traits-extension-phase1.zh-CN.md)。各目录分别负责：

| 路径 | 内容 |
| --- | --- |
| `data/<namespace>/bionic_traits/<path>.json` | 声明 `<namespace>:<path>`，含 `schema_version: 1`、范围、值类型、继承规则等 |
| `data/<namespace>/bionic_trait_bindings/<path>.json` | 独立向一个特性补充供体和载体 |
| `data/<namespace>/bionic_trait_modifiers/<path>.json` | 定向修改一个特性的配置或禁用它 |
| `data/<namespace>/bionic_parts/<path>.json` | 定义部位 ID、显示名称和自动识别规则 |
| `data/<namespace>/bionic_anatomy/<entity_namespace>/<entity_path>.json` | 按原始体块索引声明精确部位模板 |

同一资源 ID 仍按数据包优先级整体替换。加载顺序是：基础定义 → 按完整资源 ID 排序的绑定 → 按 `priority` 升序、完整资源 ID 排序的修改器。修改器后应用的字段覆盖先应用的字段。

绑定格式：

```json
{
  "trait": "example:frost_arm",
  "values": { "minecraft:zombie": true },
  "carriers": {
    "minecraft:zombie": { "create_biotech:attack_hand": ["right_arm", "left_arm"] }
  }
}
```

绑定可包含 `values`、`carriers`、`parts`、`effects`、`variants`，对象按键递归合并；标签和精确实体值仍按既有规则解析。独立绑定可以扩充任意命名空间的特性，无需复制原定义。

### 统一部位定义

自动识别、特性载体和 JEI“供体：”共用一份部位目录。通用部位来自 `bionic_parts`，特性专用部位可在特性内部声明；部位不再是固定枚举。文件路径决定完整 ID，例如 `data/example/bionic_parts/antenna.json` 声明 `example:antenna`：

```json
{
  "name": { "text": "触角" },
  "order": 25,
  "match": {
    "contains": ["antenna"],
    "regex": ["^feelers?[0-9]*$"]
  },
  "include_children": false
}
```

- `name` 必填，使用 Minecraft 文本组件；内置定义用 `{"translate":"create_biotech.bionic_part.head"}`。字面文本可直接随服务端数据包同步，翻译键需要客户端语言资源。
- `order` 是显示排序用的整数，缺省 `0`；同序按部位完整 ID 排序。
- `match` 可省略。`exact` 为精确名称，`contains` 为子串关键词，`regex` 为 Java 正则；每种最多 64 项，每项最多 256 字符，任一命中即赋予该部位。同一体块可属于多个部位。
- `target` 缺省 `canonical`，模型标签和关键词转小写并删除非 `a-z0-9` 字符；`raw` 保留原始名称；`leaf` 取最后一个 `/` 或 `.` 后的名称，保留大小写。正则对选定名称执行 `find()`，需要完整匹配时加 `^`、`$`，忽略大小写可加 `(?i)`。
- `entity_namespaces` 限定实体命名空间，省略表示不限；`excluded_entity_namespaces` 排除指定命名空间。`any_of` 可组合多个匹配对象，任一分支命中即可，最多嵌套 4 层、每层 64 项。
- `fallback_to_all` 是可选对象，接受相同命名空间过滤字段。当该来源的完整原始体块中没有该部位时，将其原始全部体块归入该部位。先检查原始全集再考虑切割，因此切掉已识别部位不会触发回退。
- `include_children` 缺省 `false`。设为 `true` 时，匹配节点及其全部后代体块；原始模型名保持不变。规则读取通用父节点数据，不另存“头部归属”。内置 `create_biotech:head` 使用此规则；已删除 `captured_head`、独立头部快照及整身兜底。

新增特性可在 `carrier.roles` 中填写 `["example:antenna"]`。已存在特性的 `carriers` 也可用此 ID 精确绑定某物种的模型标签或体块索引；`carrier.roles`、`fallback`、`excluded`、子特性／攻击效果的 `roles` 和解剖模板的 `roles` 键全部使用完整部位 ID。原始模型标签仍是 `left_arm` 等名称，不改写成部位 ID。

对于限定载体的特性，特性定义中的 `carrier.roles` 或 `fallback` 也需要引用相应部位；仅增加 `carriers` 的键不会改写其载体选择规则。部位识别规则由 `bionic_parts` 决定，物种的精确载体绑定仍优先，空绑定仍表示明确排除。

内置通用部位共 15 个，命名空间均为 `create_biotech`：

| 部位路径 | 显示名称 | 默认自动识别关键词 |
| --- | --- | --- |
| `head` | 头 | `head`、`skull`，包含模型子节点 |
| `gill` | 鳃 | `gill` |
| `torso` | 身体 | `torso`、`chest` |
| `back` | 背部 | 原版精确 `body`；其他命名空间按文件中的背部正则匹配名称末段 |
| `leg` | 腿 | `leg`、`foot`、`feet`、`paw`、`hoof` |
| `wing` | 翅膀 | `wing`，包含左右翼 |
| `fin` | 鳍 | `fin` |
| `tail` | 尾巴 | `tail` |
| `tentacle` | 触须 | `tentacle`、`tendril` |
| `shell` | 壳 | `shell`、`carapace` |
| `spine` | 刺 | `spine`、`spike`、`stinger`、`quill`、`thorn` |
| `mouth` | 口器 | `mouth`、`fang`、`jaw`、`beak`、`snout`、`muzzle` |
| `attack_hand` | 手臂 | `arm`、`hand` |
| `horn` | 角 | `horn`、`tusk` |
| `eye` | 眼 | `eye` |

全身表示载体未限定部位，不是部位定义。`body` 等含义不确定的模型名称仍由物种绑定或解剖模板指定。已删除蘑菇分类；腿／足、左右翼、触须／卷须分别共用 `leg`、`wing`、`tentacle`。监守者现有 `body`、`left_ribcage`、`right_ribcage` 绑定和完整胸腔条件保留；翼和腿的安装范围、有效肢体数量及覆盖率条件继续独立判定。

高优先级数据包用同 ID 文件覆盖内置定义；用 `{"disabled":true}` 移除部位。撤下定义该部位的数据包也会在下次重载移除它。删除时需要同时清理引用它的特性和解剖模板；未定义的部位引用、非法正则或超限会拒绝本次加载，保留上次快照，避免悄悄变成全身载体。最多 1024 个部位，每项特性最多引用 256 个不同部位。

部位、解剖模板与特性在一次 `/reload` 中校验并一起替换、同步到客户端，刷新判定、JEI 和供体缓存。旧大写枚举名称与旧协议不再兼容；内置供体数值和已有器官归属保留。

`back.json` 中声明骑乘背部识别及非原版实体缺少背部时的全组织回退；`rideable.json` 的 `carrier.roles` 引用 `create_biotech:back`。鞍座安装直接使用已经判定生效的骑乘体块，已删除独立背部选择器及重复缓存。数据包修改该部位或骑乘定义后，特性、JEI 和鞍座安装一起更新。

`carrier.replace_with_bindings` 缺省 `false`。设为 `true` 时，某物种存在非空 `carriers` 对象便用其中全部部位替代默认 `carrier.roles`，否则保留默认选择；空名称数组仍表示排除。精确绑定不会再额外并入头部；需要子树时在绑定中显式设置 `include_children`。内置 `attack_knockback.json` 通过此字段声明监守者手臂、劫掠兽整个头与口器的归属，已删除按击退特性 ID 判断的代码分支。其他特性也可使用该字段。

劫掠兽原有的头部主体分类与体块归属已合并到整个 `head`，独立 `head_core` 部位定义、显示名称及专用补标签已删除。近战击退通过 `head` 绑定的 `include_children: true` 明确包含头部子树，并保留 `mouth` 绑定；角、耳等原有归属保持。`horn` 的显示名称统一为“角”，仍匹配 `horn`、`tusk`。

捕获器保留原始模型名，并统一补充 `material/<纹理命名空间>/<纹理路径>/<模型名>` 来源标签，例如 `material/minecraft/textures/entity/sheep/sheep_fur.png/body`。剪羊毛数据仅绑定该羊毛材质的身体，避免把头腿毛层或裸身体算作产出器官。旧 `body_wool`、特性专用 `wool` 和 `mushroom` 补标签已删除，不做旧标签兼容；剪毛隐藏毛层和蘑菇原材质外观仍由渲染材质信息处理。

`material/` 是通用来源标签的保留前缀，不能用于部位推断中的普通模型名。仅 `target: "raw"` 或精确 `carriers` 绑定可显式选择此类标签，防止纹理路径中的 `arm`、`eye` 等词误把身体归入其他器官。标签遵循现有 64 字符、每体块 16 项上限，超限的限定标签不补写，原始模型名保留。

### 特性内部的命名供体

在特性根对象的 `parts` 中定义局部部位，再在 `carrier.roles`、`fallback`、`excluded`、攻击效果／子特性的 `roles` 或 `carriers` 键中用 `#局部名称` 引用。例如完整的特性定义：

```json
{
  "schema_version": 1,
  "scopes": ["whole_body"],
  "value_kind": "ability",
  "inheritance": { "type": "presence" },
  "automatic_detection": false,
  "values": { "example:beast": true },
  "carrier": { "roles": ["#crystal_core"] },
  "parts": {
    "crystal_core": {
      "name": { "text": "结晶核心" },
      "order": 25,
      "bindings": { "example:beast": [7] }
    }
  }
}
```

`bindings` 按实体完整 ID 指定模型标签或原始体块编号，例如 `["crystal"]`、`[7]` 或 `["#7"]`。编号从 0 开始，随实体模型版本变化；同名模型节点可能有多个体块，单个体块用编号选择。`name` 是显示名称，不参与匹配；字面名称随目录同步到客户端并用于 JEI 和缺失器官提示。`values` 或自动检测仍决定该生物是否提供特性，部位定义本身不赋予特性。

局部部位也可使用与通用定义相同的 `match` 正则、命名空间过滤、`include_children`、`fallback_to_all`。指定物种的 `bindings` 优先于该局部规则，包括空数组和未命中的数组；缺少该物种绑定时才使用规则。根对象的同部位 `carriers` 显式配置优先于局部 `bindings`。

局部键仅允许小写字母、数字和下划线。内部 ID 为 `<特性命名空间>:trait/<特性路径>/<局部键>`，只供所属特性使用，不参与其他特性的通用部位推断，也不能被其他特性或全局解剖模板引用。不同特性可以使用相同局部键并定义不同名字。局部部位可与通用部位一起放在 `carrier.roles` 中；只写 `parts` 不会自动改变特性的载体选择。独立绑定文件支持补充 `parts`，修改器可用 `"parts": {"key": null}` 删除局部定义，同时清理引用。

普通 `carriers` 名称／编号数组是精确选择，即使引用的通用部位定义启用了子节点匹配，也不会自动扩大。需要整个节点子树时使用对象形式：

```json
"carriers": {
  "minecraft:ravager": {
    "create_biotech:head": { "names": ["head"], "include_children": true }
  }
}
```

局部部位的 `include_children` 同时作为它的绑定默认值；绑定对象中的同名字段可单独覆盖。数字编号始终精确，不因 `include_children` 扩展成同一节点下的其他体块。

内置 5 个专用定义已迁入所属特性，名称、绑定与条件保持：

| 特性 | 局部供体 |
| --- | --- |
| `block_attachment` | `#base` 底座 |
| `wool_shearing` | `#wool` 身体羊毛 |
| `milkable` | `#udder` 乳房 |
| `sonic_boom` | `#left_ribcage` 左胸腔、`#right_ribcage` 右胸腔 |

### 通用原始数据与存储

`bionic_anatomy` 资源还支持 `model_aliases`，按实体、模型节点和节点内的体块序号补名。例如 `data/example/bionic_anatomy/minecraft/cow.json`：

```json
{
  "model_aliases": [
    { "part": "body", "cubes": [1], "names": ["udder"] }
  ]
}
```

该规则选择牛的 `body` 节点中第 2 个体块（序号从 0 开始），给它补上 `udder`；它仍保留 `body` 等原名。`cubes` 省略表示节点内全部体块，空数组表示不选择。这里的序号属于节点自己的几何集合，和特性绑定中 `[7]` 表示的最终全模型体块编号不同。`part` 精确匹配原始节点名称，不连锁匹配其他规则补上的别名。模型资源改变节点内部顺序时需要更新数据。

资源路径按 `<实体命名空间>/<实体路径>` 选择实体，也可用 `entity_type` 指定完整实体 ID。规则适用于该实体的主模型及图层模型、成年和幼年；`baby`、`cube_count`、`seams` 只限制同文件内的服务端部位模板，不限制补名规则。仅配置补名时不需要模板字段。每文件最多 256 条规则，每条最多 16 个别名，节点内编号范围为 0～1023。使用同 ID 数据包覆盖或 `{"disabled":true}` 可移除；重载时与供体目录一起验证、同步、清理客户端采集缓存。已有身体仍保留采集时的原始标签，新规则用于后续采集。

原版乳房、灾厄村民交叉手臂和犰狳内外壳均使用此规则，数据位于 `data/create_biotech/bionic_anatomy/minecraft/`。牛和哞菇为 `body[1] → udder`；灾厄村民为 `arms[0] → right_arm`、`left_shoulder[*] → left_arm`；犰狳为 `body[0] → outer_shell`、`body[1] → inner_body`、`cube[*] → outer_shell`。蜷缩外壳共用 `outer_shell` 别名，通过通用同名体块匹配关联原外壳，不再保留 `rolled_shell` 分支。已删除 `SurgicalNativePartLabels` 中按模型类、尺寸和序号判断的 Java 特判。

原版 `ModelPart` 与通用集合模型共用上述规则；集合模型的序号对应节点内遍历的几何集合。集合不可拦截或模型不暴露节点名时，仍可使用最终体块编号绑定。

采集时保存模型节点名称、父节点关系、体块引用和材质来源，以及原始体块体积。原版 `ModelPart` 和通用集合模型共用此结构。切割保留完整原始数据，仅修改剩余体块集合；覆盖率仍以原始组织为依据。没有 `OriginalHeadCubes`、`HeadCubes` 或头部已知标记；头部和其他部位一样通过数据规则选择。缰绳等外观附着仍可在客户端缓存由规则推导出的头部标记，不写入身体 NBT。

NBT 用名称字典、父节点字典和整数引用去重；体积以保存浮点位模式的整数数组编码。匹配按唯一名称组和节点复用结果，同一次特性求值中的部位选择缓存，实体已有装配／目录版本缓存继续生效。上限为每来源 4096 个节点、父链深度 64、每体块 16 个直接标签和 16 个节点引用；拒绝非法父引用及超限数据。

网络协议 45、装配体格式 4、手术台格式 3。本次不兼容旧头部快照和旧解剖 NBT 格式。

修改器格式：

```json
{
  "trait": "example:frost_arm",
  "priority": 10,
  "patch": {
    "inheritance": { "min_coverage": 0.6 },
    "parameters": { "min_legs": 1 }
  }
}
```

`priority` 为 `[-100000,100000]` 的整数，缺省 `0`。`patch` 使用 JSON merge patch 语义：对象递归合并、数组与标量整体替换、`null` 删除字段。`{"patch":{"disabled":true}}` 禁用目标；禁用内置特性也会阻止它出现在判定结果中。目标不存在默认报错；绑定和修改器可设置 `"optional": true`，允许依赖的附属模组未安装时跳过。`create_biotech:__extension/` 是同步保留路径，不能用于声明特性。

结构、未知服务端组件或组件参数错误会拒绝本次目录加载，保留旧快照。完整目录到齐后客户端才切换，刷新 JEI 和供体缓存；客户端不执行组件行为。所有原始定义、绑定、修改器、部位和解剖模板均纳入第一阶段的同步大小限制。

## 行为组件

在定义根对象添加 `behaviors` 数组，每个元素必须有完整 `type` ID。每种数组最多 64 项。参数在重载时编译，运行时不解析 JSON；仅对通过载体、范围、覆盖率、条件与覆盖关系判定的特性执行。

```json
"behaviors": [
  {
    "type": "create_biotech:on_hit_effect",
    "effect": "minecraft:slowness",
    "duration_ticks": 100,
    "amplifier": 0,
    "chance": 1
  }
]
```

| 类型 | 参数与执行规则 |
| --- | --- |
| `create_biotech:attribute` | 必填 `attribute`；`amount` 默认 0、范围 ±1024；`operation` 为 `add_value`（默认）、`add_multiplied_base`、`add_multiplied_total`；`scale` 为 `strength`（默认）、`value`、`none`。为该实体已有属性添加临时修正，不创建缺失属性 |
| `create_biotech:status_effect` | 必填 `effect`；`duration_ticks` 默认 100（1–24000），`amplifier` 默认 0（0–255），`interval_ticks` 默认 20（1–24000），`chance` 默认 1（0–1）。定期给自己施加效果 |
| `create_biotech:on_hit_effect` | 同样的 `effect`、时长、等级、概率；成功近战且实际攻击手臂提供该特性时执行，不使用 `interval_ticks` |
| `create_biotech:damage_multiplier` | `multiplier` 默认 1（0–100）；可选 `damage_type` 或 `damage_tag` 完整 ID；都填时必须同时匹配。倍率按强度从 1 插值，在原有防御前调整传入伤害；0 在满强度时免疫 |
| `create_biotech:regeneration` | `interval_ticks` 默认 100（1–24000），`amount` 默认 1（0–1024），按强度回复生命 |
| `create_biotech:item_production` | 必填 `item`，`count` 默认 1（1–64），`interval_ticks` 默认 6000（20–24000）；存活且 AI 启用时生成掉落物，不按组织数量倍增 |
| `create_biotech:effect_immunity` | 将该 `effect_set` 特性已经判定生效的效果集合用于状态免疫，无额外参数 |
| `create_biotech:inherited_attack_effects` | 将该 `attack_effect_set` 特性已判定的攻击效果用于实际近战手臂，保留各项概率、无武器限制、时长与等级，无额外参数 |

周期以世界时间和实体 ID 错开，重载不会额外发放一次产物或回复，也不补发离线期间的产物。属性修正在下一次服务端 tick 更新；特性移除、被覆盖、装配改变、重载或实体移除都会清理原组件的临时修正。已经施加的药水效果按原时长自然结束；已经生成的物品保留。行为组件抛出运行时异常会记录日志并停止该实体上的该组件，直到装配或目录重新判定；其他组件继续执行。

## 条件与载体选择器

`conditions` 是纯装配条件数组。原有范围、覆盖率和内置条件优先；条件只会进一步禁用特性。条件在原生判定后收敛，并在覆盖规则执行后再收敛，依赖项失败或被覆盖会阻止依赖它的特性。互相依赖且都已通过覆盖率的特性可以同时存在，不会凭空产生缺失供体。

| 类型 | 参数 |
| --- | --- |
| `create_biotech:body_volume` | `min` 默认 0，可选 `max`；需要已有服务端体积测量，要求体积落在闭区间内 |
| `create_biotech:requires_trait` | 必填完整 `trait` ID，要求该特性生效 |
| `create_biotech:source_count` | `min` 默认 1（1–4096），要求实际有效载体至少来自相应数量的不同来源 |

`carrier.selectors` 可以在部位定义之外继续限制模型体块。多个选择器取并集，再与原载体取交集；空结果不会回退到其他组织。内置选择器 `create_biotech:parts` 接受非空 `names` 数组，精确匹配保存的模型部件标签。缺少标签时不猜测体块。附属模组可注册任意新的命名空间选择器。

```json
"carrier": {
  "selectors": [{ "type": "create_biotech:parts", "names": ["crystal_core"] }]
},
"conditions": [{ "type": "create_biotech:source_count", "min": 1 }]
```

条件和选择器参与装配缓存，只能依赖保存的装配与判定结果。时间、天气、水中状态等动态逻辑应在行为或运行时事件中检查。服务端必须有所有自定义组件类型；客户端缺少行为类型时只跳过执行，缺少条件／选择器类型时采用保守的未生效结果。附属模组若要求客户端准确预览自定义条件或体块选择，必须同时安装在客户端。

## KubeJS

可选依赖 KubeJS **2101.7.2-build.381**、Rhino **2101.2.7-build.81**。未安装 KubeJS 时不加载插件，也不需要脚本依赖。插件不会将 KubeJS 或 Rhino 打包进模组。

在 `kubejs/server_scripts/` 中使用：

```js
BionicTraitEvents.definitions(event => {
  event.register('example:regeneration', {
    schema_version: 1,
    scopes: ['whole_body'],
    value_kind: 'ability',
    inheritance: { type: 'presence' },
    automatic_detection: false,
    behaviors: [
      { type: 'create_biotech:regeneration', interval_ticks: 100, amount: 1 }
    ]
  })
  event.bind('example:cow_regeneration', {
    trait: 'example:regeneration', values: { 'minecraft:cow': true }
  })
})

BionicTraitEvents.tick('example:regeneration', event => {
  if (event.entity.tickCount % 20 === 0 && event.entity.isInWater()) {
    event.entity.heal(0.25)
  }
})

BionicTraitEvents.hurt('example:regeneration', event => {
  event.amount *= 0.8
  // event.cancel() 将本次传入伤害设为 0。
})
```

| 入口 | 说明 |
| --- | --- |
| `definitions` | 服务端脚本加载后、数据包生成期间执行；`register(id, json)`、`bind(id, json)`、`modify(id, json)`、`anatomy(id, json)` 分别写入特性、绑定、修改器和解剖规则目录。同一次事件不能重复写同一资源 ID |
| `tick` | 每个有效特性每服务端 tick 一次，组件 tick 之后；支持可选特性 ID 过滤 |
| `melee` | 成功近战且该特性的有效载体与实际攻击手臂相交；额外提供 `target`、`arm`，组件近战回调之后 |
| `hurt` | 原有防御前，组件伤害倍率之后；提供 `source`、可写的非负有限 `amount`；支持 `cancel()`，按特性目录顺序累积 |
| `BionicTraits` | 绑定到 Java 的 `BionicTraitApi`；例如 `BionicTraits.result(entity, 'example:regeneration').active()` |

`tick`／`melee`／`hurt` 共有 `entity`、完整 `id`、不可变 `result`。回调保存在 KubeJS 的事件系统里，`/reload` 会按 KubeJS 规则重新加载；脚本删除定义后下次重载会移除该特性。脚本生成内容遵循 KubeJS 内部数据包的正常优先级，其他高优先级资源包仍可覆盖。客户端只接收生成的 JSON，不需要安装这些服务端脚本。

## Java 附属 API v1

入口是 `com.nobodiiiii.createbiotech.api.bionic.BionicTraitApi`，`API_VERSION = 1`。`definitions(level)`、`find(level,id)`、`result(entity,id)`、`evaluate(assembly,level)` 提供查询；结果为不可变对象。定义元数据在重载后重新查找，ID 句柄按完整 ID 比较。

在公共初始化的 `enqueueWork` 中、首次数据加载前注册类型：

```java
BionicTraitApi.registerBehavior(ResourceLocation.parse("addon:wet_regeneration"), json ->
    new BionicBehavior() {
        @Override public void tick(Context context) {
            if (context.every(20) && context.entity().isInWater()) context.entity().heal(1);
        }
    });
```

- `registerBehavior(id, factory)`：JSON 编译到不可变 `BionicBehavior`，提供 `tick`、`melee`、`incomingDamage`、`allowsEffect`、`removed`。`Context.modifierId()` 为每个特性和组件下标生成唯一属性修正 ID。
- `registerCondition(id, factory)`：JSON 编译到纯 `BionicCondition`，上下文有装配、世界、当前特性、候选结果集合和当前结果。
- `registerCarrierSelector(id, factory)`：JSON 编译到纯 `BionicCarrierSelector`，返回原始体块索引的 `BitSet`；返回新集合，勿修改保存的装配。
- `registerDefault(id, json)`：注册没有资源文件的默认定义；数据包同 ID 定义优先。实际资源随目录同步，不要求客户端重复注册默认值。
- `registerAnatomyDefault(id, json)`：同样注册默认 `bionic_anatomy` 资源，支持部位模板与 `model_aliases`。例如 ID `addon:minecraft/cow`，内容同上；KJS 可在 `BionicTraitEvents.definitions` 中使用 `event.anatomy('addon:minecraft/cow', {...})` 生成相同规则。

重复类型 ID、重复默认定义和第一次服务端加载后的注册均明确报错。工厂在每次重载时调用，返回的组件在实体间共享，不能把单个实体状态放在组件字段中。需要临时属性等副作用时在 `removed` 中对称清理。公开的 `BionicTraitEvent.Tick`、`.Melee`、`.Hurt` 可由附属模组在 `NeoForge.EVENT_BUS` 监听，时机与 KubeJS 相同；受伤事件可 `setAmount`。这些入口仅服务端触发。

API v1 保持上述接口语义；内部 `entity.trait` 数据加载、快照与规则实现不属于兼容承诺。内置的导航、头部分类和特殊攻击继续保留专属实现，新特性不会凭同名自动套用。

## 示例与验证边界

[可直接安装的示例](../examples/bionic-traits/README.zh-CN.md) 包含独立数据包和独立 KubeJS 脚本。

本次不修改 Mixin、不创建测试源文件、不启动客户端。编译和内存校验验证加载、覆盖顺序、注册、协议数据及移除等代码路径；实体动作、客户端 JEI 热更新和 KubeJS 实际脚本执行仍需在安装环境中验证。

KubeJS 接口参考本地 `ref/1.21.1/KubeJS/` 的 `KubeJSPlugin`、`EventGroup`、`KubeResourceGenerator`、`ServerScriptManager`；来源与版本差异记录于 [ref/SOURCES.md](../ref/SOURCES.md#kubejs-extension-reference)。该源分支使用 NeoForge 21.1.199，实际接口已按本项目 21.1.234 与发布版 KubeJS 381 编译核对。属性修正参考 `ref/1.21.1/Minecraft/net/minecraft/world/entity/ai/attributes/AttributeInstance.java` 的 1.21.1 源码。

## 体质与力量

内置 `create_biotech:health_offset`（体质）与 `create_biotech:damage_offset`（力量）可通过 `values` 覆写供体的有符号数值，标签绑定和独立绑定沿用现有机制。默认值对应[最终修正表](vanilla-stat-offset-survey.zh-CN.md)；未配置供体为 0。只有这两项内置数值特性接受负值，其他数值特性仍要求非负数。

力量默认值保留原有人工平衡值，仅将末影人与猪灵蛮兵设为 +3，凋灵骷髅设为 +1，其余供体保持本轮 DPS 测算前的原始数值。DPS 测算与候选方案仅保存在[归档](vanilla-stat-offset-survey-archive-2026-11-13.zh-CN.md)，最终采用值以总表和资源文件为准。例如末影人的每臂力量为 +3；该值是每次命中的伤害偏移，不是直接增加 3 DPS。

`value_kind: "number"` 特性（包括内置数值特性和附属模组新增数值特性）默认按原供体体积分配：

`体块贡献 = 供体 values 数值 × 体块捕获体积 / 原供体全部体块捕获体积之和`

力量（`damage_offset`）只由原生手臂携带，单独使用 `体块贡献 = 供体力量 × 体块捕获体积 / 该体块所属原生手臂的完整捕获体积`。每条完整原生手臂各有完整的供体力量值，左右臂分别计算；不会平均到全身。模型标签区分左右手臂，原始连接将未命名的同臂段归组。原始手臂体积包含已切除部分，拆开后仍保留各碎片原有份额；无原生手臂标签或测量时不猜测力量。

装配后，直接求和生效部位内体块的贡献；臂、腿按每条关节链独立求和。`result.memberValues()` 保存每个体块未舍入的贡献，`armValue()` 对传入的唯一体块求和后统一归零或舍入。肢体特性的共用标签显示最终贡献绝对值最大的链，不把别的臂链用于当前出手。

原供体体积固定为捕获时完整数组的和，切割和重新拼装不会改变分母。相同来源的碎片仍分摊原数值，另一个独立供体有自己的分配总额；添加无修正组织只增加其自身贡献 0。载体、安装范围、附加条件仍生效，不会把范围外组织算入该特性。缺失原供体体积的旧组织贡献为 0。

数值继承仍声明 `coverage_scaled`；不再读取数值特性的 `min_coverage`（包含旧数据包或修改器中的体积预算）。供体总额和各体块贡献保留完整精度，在全身、躯干或每条关节链求和后处理：绝对值不足 0.1 直接归零，不显示也不生效；其余按四舍五入保留小数点后 1 位，显示和实际效果共用该值。正负值使用相同规则，例如 ±0.09 均归零，±0.15 分别成为 ±0.2。多个小额体块可先累加到 0.1，不逐块舍入。原有基础体积公式继续提供基础数值，生命、伤害等最终属性仍保留其范围限制。

例如：完整末影人的体质为 +20，切下原体积 30% 的组织贡献 +6；其余 70% 贡献 +14，两部分合回仍为 +20。再拼入一个或两个等体积的无修正体块，体质都保持 +20；若使用两个完整末影人供体则为 +40。牛的体质 −10 按相同方式分配，不会被额外的 0 值组织稀释。铁傀儡的力量 +10 则是每条原生手臂的总额：某条出手臂安装了其一条原生手臂体积 30% 的组织，就取得 +3 力量；完整该臂取得 +10，两条完整原生手臂拼成同一出手臂则为 +20。

体质在全身几何血量后相加；力量在出手臂的几何伤害后、附魔和正常伤害处理前相加。更新后 `/reload` 会重算已有实体，增加生命上限不会自动治疗。

## 玩法规则扩展

关系表、原生产物、自定义采集与周期、装备参数、器官组合、供体检测器、攻击类型及 Java/KubeJS 操作事件，见 [仿生玩法规则与扩展接口](bionic-gameplay-extension.zh-CN.md)。
