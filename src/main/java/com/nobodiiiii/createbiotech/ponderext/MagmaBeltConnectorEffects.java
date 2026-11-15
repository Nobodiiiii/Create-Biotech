package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Author-owned effects; belt movement and smelting use the real magma belt inventory. */
final class MagmaBeltConnectorEffects {
    private static final String CYCLE_STATE = "magma_belt.smelting_cycle";
    private static final BlockPos INPUT_BELT = new BlockPos(3, 1, 2);
    private static final BlockPos INPUT_FUNNEL = INPUT_BELT.above();
    private static final BlockPos OUTPUT_FUNNEL = new BlockPos(1, 2, 2);
    private static final int INGOT_DISPLAY_TICKS = 16;
    private static final int NEXT_ITEM_DELAY = 10;

    private MagmaBeltConnectorEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/magma_belt_connector/start_smelting",
            "开始逐件烧炼粗铁", MagmaBeltConnectorEffects::startSmelting);
        engine.steps().registerJava("create_biotech:java/magma_belt_connector/stop_smelting",
            "结束逐件烧炼粗铁", MagmaBeltConnectorEffects::stopSmelting);
    }

    private static void startSmelting(StepBuildContext ctx) {
        var scene = new CreateSceneBuilder(ctx.builder());
        scene.world().setFilterData(ctx.util().select().position(OUTPUT_FUNNEL),
            FunnelBlockEntity.class, new ItemStack(Items.IRON_INGOT));
        var cycle = new SmeltingCycle();
        ctx.state().put(CYCLE_STATE, cycle);
        ctx.builder().addInstruction(cycle);
    }

    private static void stopSmelting(StepBuildContext ctx) {
        var cycle = (SmeltingCycle) ctx.state().get(CYCLE_STATE);
        if (cycle != null)
            ctx.builder().addInstruction(scene -> cycle.stopped = true);
    }

    private static void flapFunnel(PonderScene scene, BlockPos pos, boolean inward) {
        if (scene.getWorld().getBlockEntity(pos) instanceof FunnelBlockEntity funnel)
            funnel.flap(inward);
    }

    private static final class SmeltingCycle extends TickingInstruction {
        private boolean stopped;
        private boolean itemInFlight;
        private int ingotTicks;
        private int cooldown;

        private SmeltingCycle() {
            super(false, Integer.MAX_VALUE);
        }

        @Override
        protected void firstTick(PonderScene scene) {
            stopped = false;
            itemInFlight = false;
            ingotTicks = 0;
            cooldown = 0;
            // The exported NBT contains controller coordinates from the original world.
            // Create's Ponder controller fix only handles its own BeltBlockEntity class.
            for (int offset = 0; offset < 3; offset++) {
                if (scene.getWorld().getBlockEntity(INPUT_BELT.west(offset)) instanceof MagmaBeltBlockEntity belt)
                    belt.setController(INPUT_BELT);
            }
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            if (stopped || !(scene.getWorld().getBlockEntity(INPUT_BELT) instanceof MagmaBeltBlockEntity belt))
                return;

            if (!itemInFlight) {
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                var input = belt.getBehaviour(DirectBeltInputBehaviour.TYPE);
                if (input != null && input.handleInsertion(new ItemStack(Items.RAW_IRON), Direction.WEST, false).isEmpty()) {
                    itemInFlight = true;
                    flapFunnel(scene, INPUT_FUNNEL, false);
                }
                return;
            }

            var items = belt.getInventory().getTransportedItems();
            if (items.stream().noneMatch(item -> item.stack.is(Items.IRON_INGOT)))
                return;
            if (++ingotTicks < INGOT_DISPLAY_TICKS)
                return;

            // Ponder's client world blocks automatic funnel intake. Show it explicitly
            // after the real smelting result has remained visible for a short time.
            items.clear();
            flapFunnel(scene, OUTPUT_FUNNEL, true);
            itemInFlight = false;
            ingotTicks = 0;
            cooldown = NEXT_ITEM_DELAY;
        }

        @Override
        public boolean isComplete() {
            return stopped;
        }
    }
}
