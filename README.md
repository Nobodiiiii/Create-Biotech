# Create: Biotech

[English](README.md) · [中文](README.zh-CN.md) · [Player Intro](docs/INTRODUCTION.md) · [玩家介绍](docs/INTRODUCTION.zh-CN.md)

A Minecraft 1.20.1 Forge addon for [Create](https://www.curseforge.com/minecraft/mc-mods/create) that wires living mobs and biological materials into Create's kinetic and processing systems. Slimes become belts, ghasts become balloons, evokers run enchanting rituals — the rest of the mod plugs those into stress, basin processing, contraptions, funnels, and JEI.

This README targets contributors and AI coding agents. Players should read [docs/INTRODUCTION.md](docs/INTRODUCTION.md).

---

## Build environment

| Field | Value |
| --- | --- |
| Minecraft | 1.20.1 |
| Loader | Forge 47.1.33 (via `net.neoforged.moddev.legacyforge`) |
| Java | 17 |
| Mod id / version | `create_biotech` / see [gradle.properties](gradle.properties) |
| Hard deps | Create 6.0.8, Registrate, Flywheel, Ponder |
| Soft deps | JEI, Jade |
| Mixin config | [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json) |
| Mappings | Parchment 2023.09.03 |

```bash
./gradlew build                     # build the jar
./gradlew runClient                 # launch dev client
./gradlew runServer                 # launch dev server
./gradlew runData                   # regenerate datagen output
./gradlew quickPlayClient -Pinstance=<name>   # build + copy + launch external instance via test.py
```

## Repository layout

```
src/main/java/com/nobodiiiii/createbiotech/
  CreateBiotech.java        # @Mod entrypoint, registry + event bus wiring
  registry/                 # CBBlocks, CBItems, CBFluids, CB*Types — every Registrate registration
  content/                  # one package per feature (slimebelt, ghasthotairballoon, processing/basin, …)
  client/                   # client-only renderers, particles, GUI hooks
  network/                  # CBPackets and packet definitions
  mixin/                    # Create + vanilla mixins (see mixins.json for the full list)
  compat/                   # JEI, Jade integration
  ponder/                   # Ponder scene scripts
  foundation/, infrastructure/, event/   # shared utilities, GUIs, contraption movement helpers

src/main/resources/
  assets/create_biotech/    # models, textures, lang (en_us.json, zh_cn.json), ponder
  data/                     # recipes, tags, advancements (mix of hand-written + datagen)
  META-INF/mods.toml        # mod metadata, templated from gradle.properties
  create_biotech.mixins.json

ref/                        # vendored sources of Create + JEI — see CLAUDE.md rule below
run/                        # dev runtime (worlds, configs, logs)
tools/, test.py             # auxiliary scripts; test.py drives quickPlayClient
```

## Working conventions

Read first, then act:

1. **`ref/Create/` and `ref/jei/` are the authoritative local sources for Create and JEI.** Search them with `rg` before touching any integration code. Do **not** decompile jars or fetch upstream from the web unless the user explicitly asks. See [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md).
2. **Registration goes through Registrate.** Add new blocks/items/fluids/entities/menus in the matching `registry/CB*.java` rather than reinventing the wiring.
3. **Feature code lives in one package under `content/`.** Each feature owns its block, block entity, renderer, item, and any handlers. Cross-feature plumbing belongs in `foundation/` or `infrastructure/`.
4. **Behaviour changes to Create or vanilla classes are done through mixins.** Register every new mixin in [create_biotech.mixins.json](src/main/resources/create_biotech.mixins.json); pick the `client` list if it touches client-only code.
5. **Lang keys are bilingual.** Every new key needs both `en_us.json` and `zh_cn.json` entries.

## Key systems at a glance

- **Custom belts** — Slime Belt (dual-track loop), Magma Cube Belt (destructive fire transport), Power Belt (player-powered generator). All three are belt-shaped Create transports with full funnel / tunnel / mixin support.
- **Basin entity processing** — entities can be funnelled into a Basin and consumed as ingredients by Mechanical Press / Mixer. Recipes live under `content/processing/basin/`.
- **Contraptions** — Ghast Hot Air Balloon assembles a moving contraption with rope/shears/magnet interactions; Buffer Pads provide colour-coded movement behaviour.
- **Specialty machines** — Spider Assembly Table, Squid Printer (enchanted book copier), Evoker Enchanting Chamber, Creeper Blast Chamber, Bio Packager, Experience pump/crystallizer/buds/tank, Schrödinger's Cat (quantum redstone), Universal Joint (3D rotation transfer), Bone Ratchet, Slime Clutch, Fixed Carrot Fishing Rod, Explosion-Proof Item Vault, Cardboard Box mob capture.
- **Fluids** — Liquid Living Slime and an XP-equivalent fluid.

Open [src/main/java/com/nobodiiiii/createbiotech/content/](src/main/java/com/nobodiiiii/createbiotech/content/) and the matching folder names map 1:1 to the feature names above.

## License

MIT. See [gradle.properties](gradle.properties).
