# Spider Assembly Table Implementation Plan

## Goal

Add a new kinetic block, "Spider Assembly Table", that acts as a single-block sequenced assembly pipeline for an item placed on a Create Depot two blocks below it.

## Scope Decisions

- Use the local `ref/Create/` sources as the authoritative reference for Create recipe, depot, kinetic, and renderer behavior.
- Support the sequenced assembly machine blocks that Create exposes through assembly recipes and that are practical for a single block pipeline:
  - Deployer (`AllRecipeTypes.DEPLOYING`)
  - Mechanical Press (`AllRecipeTypes.PRESSING`)
  - Mechanical Saw (`AllRecipeTypes.CUTTING`)
  - Spout (`AllRecipeTypes.FILLING`)
- Split the cache UI into item and fluid rows because Minecraft inventory slots cannot directly hold both arbitrary item stacks and raw fluids:
  - Row 1: six machine slots, one non-stackable machine block per slot, not exposed to automation.
  - Row 2: six item cache slots, one stack each, exposed through item automation.
  - Row 3: six fluid-container slots backed by six internal 1B fluid tanks, exposed through fluid automation.
- Processing uses only sequenced assembly recipes. If the current item has no matching sequenced assembly recipe for a configured machine slot, that machine is skipped.

## Implementation Steps

1. Register content:
   - Add `spider_assembly_table` block, item, block entity, menu type, renderer, screen, lang entries, creative tab entry, blockstate/model/loot data, and mining tags.

2. Kinetic block behavior:
   - Implement a horizontal kinetic block with a tail shaft aligned to the spider facing axis.
   - Accept shaft input from the tail side.
   - Compute base stress impact as the sum of Create stress impacts for the machine blocks placed in the six machine slots.
   - Recalculate network stress when machine slots change.

3. Inventory and fluid capabilities:
   - Store 18 item slots: 6 machine, 6 item cache, 6 fluid-container helper slots.
   - Limit machine slots to one supported machine block.
   - Expose only item cache slots to `ForgeCapabilities.ITEM_HANDLER`.
   - Store six 1000 mB fluid tanks and expose them to `ForgeCapabilities.FLUID_HANDLER`.
   - Let fluid-container slots move fluid into/out of their corresponding tanks during server ticks.

4. GUI:
   - Open on right click.
   - Show the six machine slots, six item-cache slots, six fluid-container slots, player inventory, and six fluid gauges.
   - Support quick move between player inventory and the correct table rows.

5. Sequenced assembly executor:
   - Require a Create Depot block entity exactly two blocks below the assembly table.
   - Walk configured machine slots in order.
   - Start a processing cycle only when the selected machine has a matching sequenced assembly recipe and its corresponding cache has the required item/fluid.
   - Use Create's recipe helpers so sequenced assembly progress NBT advances exactly like normal Create machines.
   - Consume deployer-held items and spout fluids from the matching cache slot/tank.
   - Apply the processed output back to the Depot item and drop extra outputs near the Depot if a recipe produces more than one stack.

6. Timing and animation state:
   - Derive internal processing durations from the corresponding Create machine logic at the current speed:
     - Press: `PressingBehaviour.CYCLE` with the press running tick speed formula.
     - Deployer: extension plus retraction timers with the deployer timer speed formula.
     - Saw: cutting duration divided by saw processing speed.
     - Spout: fixed `SpoutBlockEntity.FILLING_TIME`.
   - Sync active slot, elapsed ticks, and total ticks to the client.

7. Renderer:
   - Render a cave-spider-sized block-form spider using ordinary spider texture based partial models.
   - Render a rotating shaft along the facing axis.
   - Number the six legs by slot internally and animate the active leg downward and back over the processing cycle.

8. Verification:
   - Run a Gradle compile check.
   - Audit that every explicit requirement has a concrete code/resource artifact or a documented implementation decision.
