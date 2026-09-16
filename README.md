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

Rendering uses existing source textures and bounded UV splitting, not generated spider textures.
Explicit target/material slot bindings precede default model sprites; incompatible default tiles can
fall back to a same-name `_connected` sheet. Source dimensions must be a uniform positive integer
multiple of their logical grid. An invalid authoritative PNG does not silently become another material.

An `overlay` uses the target dimensions: alpha 0 keeps the original mapping, alpha 255 replaces it.
Partial alpha, unavailable resources, unsupported UVs or exceeded budgets use the base model/material;
there is no runtime compositor. Offline preview PNGs do not need to be shipped.

Budgets belong to the target JSON's `render_policy`; there is no material-rendering client config group.
Omitted fields use these defaults:

```json
"render_policy": {
  "max_pieces_per_face": 64,
  "max_additional_quads": 2048,
  "max_texture_batches": 4
}
```

The target's values are used directly, with no second client-global limit. Java computes actual model costs;
`computed_cost` is not trusted as a runtime budget check. F3+T reloads definitions and clears cached bindings.
Legacy JSON `mode` values `auto/direct/composite` remain readable but do not select another backend.

Installing a casing on an unencased spider table sets its appearance without consuming the held item,
in both survival and creative. An existing casing is not replaced. Sneak-wrench once to clear its appearance
without returning a casing item; the table remains in place.

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
