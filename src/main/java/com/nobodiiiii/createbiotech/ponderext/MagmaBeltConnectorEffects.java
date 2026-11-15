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
    private static final int INPUT_INTERVAL_TICKS = 60;
    private static final int INGOT_DISPLAY_TICKS = 16;

    private MagmaBeltConnectorEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/magma_belt_connector/start_smelting",
            "开始持续烧炼粗铁", MagmaBeltConnectorEffects::startSmelting);
        engine.steps().registerJava("create_biotech:java/magma_belt_connector/stop_smelting",
            "结束持续烧炼粗铁", MagmaBeltConnectorEffects::stopSmelting);
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
        private int ingotTicks;
        private int inputCooldown;

        private SmeltingCycle() {
            super(false, Integer.MAX_VALUE);
        }

        @Override
        protected void firstTick(PonderScene scene) {
            stopped = false;
            ingotTicks = 0;
            inputCooldown = 0;
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

            // Feed on a regular beat while earlier items continue moving and smelting.
            // Retry when blocked without tying the next item to the previous output.
            if (inputCooldown > 0)
                inputCooldown--;
            if (inputCooldown == 0) {
                var input = belt.getBehaviour(DirectBeltInputBehaviour.TYPE);
                if (input != null && input.handleInsertion(new ItemStack(Items.RAW_IRON), Direction.WEST, false).isEmpty()) {
                    inputCooldown = INPUT_INTERVAL_TICKS;
                    flapFunnel(scene, INPUT_FUNNEL, false);
                }
            }

            var inventory = belt.getInventory();
            var output = inventory.getStackAtOffset(belt.beltLength - 1);
            if (output == null || !output.stack.is(Items.IRON_INGOT)
                || output.beltPosition < belt.beltLength - .5f) {
                ingotTicks = 0;
                return;
            }
            if (++ingotTicks < INGOT_DISPLAY_TICKS)
                return;

            // Ponder's client world blocks automatic funnel intake. Show it explicitly
            // for the finished item at the outlet, preserving all items behind it.
            inventory.getTransportedItems().remove(output);
            flapFunnel(scene, OUTPUT_FUNNEL, true);
            ingotTicks = 0;
        }

        @Override
        public boolean isComplete() {
            return stopped;
        }
    }
}
