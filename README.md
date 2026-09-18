# Create: Biotech

[English](README.md) · [中文](README.zh-CN.md) · [Player Intro](docs/INTRODUCTION.md) · [玩家介绍](docs/INTRODUCTION.zh-CN.md)

A Minecraft 1.21.1 NeoForge addon based on [Create](https://www.curseforge.com/minecraft/mc-mods/create), focused on bringing mobs and biological materials into Create's existing kinetic, transport, and processing systems. It includes slime belts, ghast balloons, experience machinery, and the plumbing needed to connect living creatures to basins, contraptions, funnels, and JEI.

This README is mainly for contributors and AI coding agents. If you want a player-facing overview first, read [docs/INTRODUCTION.md](docs/INTRODUCTION.md).

---

## Build environment

| Field | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.219 (via `net.neoforged.moddev`) |
| Java | 21 |
| Mod id / version | `create_biotech` / see [gradle.properties](gradle.properties) |
| Hard deps | Create 6.0.10, Registrate, Flywheel, Ponder |
| Soft deps | JEI, Jade |
| Mixin config | [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json) |
| Mappings | Parchment 2024.11.17 |

```bash
./gradlew build                     # build the jar
./gradlew runClient                 # launch dev client
./gradlew runServer                 # launch dev server
./gradlew runData                   # regenerate datagen output
./gradlew quickPlayClient -Pinstance=<name>   # build + copy + launch external instance via test.py
./gradlew quickPlaySmoke -Pinstance=<name>    # enter a world, verify the log marker, then stop the client
```

## Internal UV material rendering (1.21.1)

Biotech owns the material renderer in `foundation/render/material`; no separate Casted Materials Mod,
external library JAR or library-path build flag is needed. The spider block and item renderers use the same
`CastedMaterialsClient.resolveModel(...)` entry point. Reuse the live model root and obtain a handle each
render; resource reloads invalidate cached plans. Additional eye passes use `handle.renderLayer(...)`
so their geometry matches the UV-split base.

| Package | Responsibility |
| --- | --- |
| Module root | Registration, material API and NBT state |
| `palette` | Casing discovery/validation, immutable IDs and client/server synchronization |
| `mapping` | Definitions, the shared UV interpreter and geometry-plan compilation |
| `client` | Resource reload, source texture binding, model capture and rendering |

Only `client` depends on client rendering APIs. Casing discovery requires matching item/block IDs in
both `#create:casing` tags, with a BlockItem pointing to that block.

### Resources and rendering

A target lives at `assets/<namespace>/casted_materials/targets/<path>.json`; its ID is `<namespace>:<path>`.
Material slot definitions use `assets/<namespace>/casted_materials/materials/<material-path>.json`.
The [spider target](src/main/resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json)
is a complete example. Targets are limited to 1–256 pixels on each axis; source grids may be larger.

Fragmented static skins are sampled once from the existing UV interpretation into cached, exact-size
textures, then rendered with the original model faces. Unfragmented dedicated bodies stay direct.
Animated, high-resolution, resampled or unavailable bake sources retain direct UV rendering without
silently freezing frames or reducing detail. Both paths preserve body/eye priority and independent glow.
Explicit target/material slot bindings take precedence. Automatic discovery queries Create's
`CasingConnectivity` registry for the casing's connected target sprite; only unregistered blocks use
the baked model's particle sprite. No `_connected` filename guessing or model-face scanning is performed.
Discovery runs on binding-cache misses, not each frame. Source dimensions must be a uniform positive
integer multiple of their logical grid. Incompatible sources use the existing base-appearance fallback;
a 16x16 particle tile is not stretched into a 128x128 CT sheet. An invalid authoritative PNG does not
silently become another material.

`layers` are applied by ascending `index` with stable declaration order for ties. Texture layers use cutout
alpha and can crop/scale into target UVs; model layers attach existing baked models to a named live part. A
material override's present `layers` replaces the target list (including an empty list), while an absent list
inherits it. Legacy `overlay` remains authoring-compatible below explicit layers. Generated skins live
only in GPU/CPU texture memory, never as files. Missing resources or a mapping unsupported by both
paths uses the base model/material.

- Each layer requires an integer `index` and exactly one of `texture` or `model`; there is no style cycling state.
- Texture layers use logical `grid`, `source: [x,y,w,h]`, and `destination: [x,y,w,h]`. Defaults are target size, the full source grid, and the full target. `emissive` defaults to `false` and only surviving, unoccluded fragments glow.
- Model layers default to `part: "head"`, `offset: [0,0,0]` in model pixels, `rotation: [0,0,0]` in degrees, and `scale: [1,1,1]`. Optional `source_slot` plus `source` remaps the model UVs into a casing source rectangle; otherwise its original textures remain. Model `emissive` uses full brightness.
- A material selects one eye PNG, replacing rather than stacking with the default eyes. Transparent pixels expose the casing; the built-in spider no longer adds hat models. All spider eyes share the 64×32 canvas/UVs (head front `[40,12,8,8]`) in `assets/create_biotech/textures/entity/spider_assembly_table/`: fallback `eye_default.png`, numbered generic candidate `eye_00.png`, and `eye_copper_casing.png` / `eye_railway_casing.png` containing only extracted Create package eye strokes, never cardboard backgrounds.
- `material_variants: true` derives the role from the default filename: `eye_00` only searches `eye_` variants; `body_` only searches casing-specific `body_` variants. Unknown prefixes use only the explicit default. Beside that default, try `<material-namespace>/<role>_<casing-name>.png`, then `<role>_<casing-name>.png`. Eyes then try numbered candidates before `texture`; bodies have no numbered pool. Thus `create/eye_copper_casing.png` outranks `eye_copper_casing.png`; neither `body_copper_casing.png` nor bare `copper_casing.png` can replace eyes. Nested `addon:machines/casing` uses `addon/machines/eye_casing.png`.
- Generic candidates are **eye-only**: discover `eye_<non-negative integer>.png` in the same namespace and immediate folder, sort numerically (gaps allowed; filename breaks numeric ties), and select `synchronized material index % valid candidate count`. The palette sorts by namespace/path; materials with dedicated textures still occupy indices. Selection is stable for a material, never time-based; changing the palette or candidate set can change assignments. Discovery happens on resource reload, with cached size/cutout validation; invalid candidates are skipped and fully transparent candidates remain valid.
- Dedicated bodies take precedence over generated UVs. Target `body_textures` specifies the editing directory (the spider uses `create_biotech:entity/spider_assembly_table`): try `<material-namespace>/body_<casing-name>.png`, then `body_<casing-name>.png`, preserving nested material paths. Only absent/invalid dedicated bodies use casing-source UV mapping. There is no `body_<number>` pool. `body_andesite_casing.png` now applies in the normal rendering path, not only on fallback.
- A dedicated body uses the target canvas (64×32 for spiders; uniform integer HD scaling supported) and replaces the base, including transparent holes. Explicit `overlay` and ordered layers apply afterward and take precedence; eyes remain independently selected. Bodies are non-emissive and reuse the same UV interpreter. An unfragmented dedicated body needs no generated copy. Missing, incompatible-size or unsupported-alpha candidates are skipped.
- This rule applies to replaceable PNGs, not Create's casing-source registry lookup. Incompatible dimensions are skipped; a fully transparent replacement hides the eyes. Other texture layers opt out by default.
- Explicit material `layers` still replace the root list. Pin a texture by omitting/disabling `material_variants`. Built-in material overrides only select casing sources and inherit the single eye layer; the logistics/train hat examples have been removed. Resource packs can override these files; F3+T invalidates cached selections. Unsupported translucent layers use the base appearance.


Budgets belong to the target JSON's `render_policy`; there is no material-rendering client config group.
Omitted fields use these defaults:

```json
"render_policy": {
  "max_pieces_per_face": 64,
  "max_additional_quads": 2048,
  "max_texture_batches": 4
}
```

Geometry budgets apply to the selected path's actual mesh; a cached skin has no additional body quads.
`computed_cost` is not trusted as a runtime budget check. Legacy JSON `mode` values remain readable but
are not switches; backend selection is automatic and adds no client config group.

The generated-texture cache holds at most 128 appearances / 16 MiB of RGBA pixel payload, shared across
model instances. A 64×32 body uploads 8 KiB once; an emission mask adds another 8 KiB when needed.
There is no 512×512 atlas re-upload and no per-frame pixel composition. Capacity or upload failure
retains direct UV, without evicting textures referenced by pending draw batches. F3+T releases generated
textures and invalidates source/model caches, even if no encased spider is subsequently rendered.

Installing a casing on an unencased spider table sets its appearance without consuming the held item,
in both survival and creative. An existing casing is not replaced. Only sneak-wrench clears an installed casing without returning an
item or removing the table; without a casing it delegates to Create's default dismantling behavior.
The normal-wrench callback uses Create's default behavior, not casing removal. Wrench and table-item
interactions follow upstream `PASS_TO_DEFAULT_BLOCK_INTERACTION`: ordinary main-hand use may open the
table menu before item `useOn`. No custom menu-bypass interception is retained.
The `casted_materials:material` component, `casted_materials:material_palette` payload and `CastedMaterial`
NBT key retain their IDs for save compatibility; they are not a separate mod registration. Remove any
old standalone Casted Materials Mod from a migrated test instance. Sable Companion embedding is unchanged.
The adapted module's MIT notice remains in [licenses](src/main/resources/META-INF/licenses/casted-materials-MIT.txt).

### Authoring and verification

[Authoring tools](tools/README.md) provide exact/RGB-tolerant sample inference, optional model face bounds,
minimum-source-bounds selection and time-limited selection-count optimization. Local source paths,
runtime resource IDs and diagnostic previews are distinct inputs/outputs. There are no Blockbench
editing/export commands; only the optional `.bbmodel` reader is retained.

```powershell
./gradlew.bat --offline test build verifyMaterialRenderingModule
python -B -m pytest -q -p no:cacheprovider tools
python -B -m unittest discover -s tests -p 'test_*.py'
```

`check` verifies native module packaging, legacy resources/licenses, and the absence of a standalone
Casted Materials JAR. Java tests cover source selection, UV/pixel parity, budgets, persistence and palette
sync; Python tests cover matching, draft output and optimization. Game rendering should also be checked
in an isolated client when changing runtime code.

## Repository layout

```
src/main/java/com/nobodiiiii/createbiotech/
  CreateBiotech.java        # @Mod entrypoint, wires registries and the event bus
  registry/                 # CBBlocks, CBItems, CBFluids, CB*Types and other Registrate registrations
  content/                  # grouped by feature (slimebelt, ghasthotairballoon, processing/basin, …)
  client/                   # client-only renderers, particles, GUI hooks
  network/                  # CBPackets and packet definitions
  mixin/                    # Create + vanilla mixins (see mixins.json for the full list)
  compat/                   # JEI, Jade integration
  ponder/                   # Ponder scene scripts
  foundation/, infrastructure/, event/   # shared utilities, GUIs, and contraption movement helpers

src/main/resources/
  assets/create_biotech/    # models, textures, lang (en_us.json, zh_cn.json), ponder
  data/                     # recipes, tags, advancements (mix of hand-written + datagen)
  META-INF/mods.toml        # mod metadata, templated from gradle.properties
  create_biotech.mixins.json

ref/                        # bundled Create + JEI reference sources
run/                        # dev runtime (worlds, configs, logs)
tools/, test.py             # auxiliary scripts; test.py drives quickPlayClient
```

## Working conventions

By default, follow these conventions:

1. **`ref/Create/` and `ref/jei/` are the authoritative local sources for Create and JEI.** Search them with `rg` before touching any integration code. Do **not** decompile jars or fetch upstream from the web unless the user explicitly asks.
2. **Registration goes through Registrate.** Add new blocks/items/fluids/entities/menus in the matching `registry/CB*.java` rather than reinventing the wiring.
3. **Feature code lives in one package under `content/`.** As much as possible, each feature should own its block, block entity, renderer, item, and related handlers. Cross-feature plumbing belongs in `foundation/` or `infrastructure/`.
4. **Behaviour changes to Create or vanilla classes are done through mixins.** Register every new mixin in [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json); pick the `client` list if it touches client-only code.
5. **Lang keys are bilingual.** Every new key needs both `en_us.json` and `zh_cn.json` entries.

## Key systems at a glance

- **Custom belts** — Slime Belt, Magma Cube Belt, and Power Belt. They follow the usual Create belt behavior and support funnels, tunnels, and the related mixin behavior.
- **Basin entity processing** — entities can enter a Basin through funnels and then be processed as ingredients by the Mechanical Press or Mixer. Recipes live under `content/processing/basin/`.
- **Contraptions** — the Ghast Hot Air Balloon can be assembled into a moving contraption; Buffer Pads provide color-coded movement behavior.
- **Specialty machines** — Spider Assembly Table, Squid Printer (enchanted book copier), Evoker Enchanting Chamber, Creeper Blast Chamber, Bio Packager, Experience pump/buds/tank, Schrödinger's Cat (quantum redstone), Universal Joint (3D rotation transfer), Bone Ratchet, Slime Clutch, Fixed Carrot Fishing Rod, Explosion-Proof Item Vault, Cardboard Box mob capture.
- **Fluids** — Liquid Living Slime and a fluid equivalent of experience.

Open [src/main/java/com/nobodiiiii/createbiotech/content/](src/main/java/com/nobodiiiii/createbiotech/content/) and the folder names will quickly lead you to the matching feature.

## License

This repository is not license-uniform.

Original code is MIT, original Create: Biotech assets are generally All Rights Reserved, and the repository also includes adapted material from `SylviaX-390/createbuttercat` under MIT. See [LICENSE.md](LICENSE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
