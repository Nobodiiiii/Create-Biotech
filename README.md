# Create: Biotech

[English](README.md) · [中文](README.zh-CN.md) · [Player Intro](docs/INTRODUCTION.md) · [玩家介绍](docs/INTRODUCTION.zh-CN.md)

A Minecraft 1.20.1 Forge addon based on [Create](https://www.curseforge.com/minecraft/mc-mods/create), focused on bringing mobs and biological materials into Create's existing kinetic, transport, and processing systems. It includes slime belts, ghast balloons, experience machinery, and the plumbing needed to connect living creatures to basins, contraptions, funnels, and JEI.

This README is mainly for contributors and AI coding agents. If you want a player-facing overview first, read [docs/INTRODUCTION.md](docs/INTRODUCTION.md).

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
