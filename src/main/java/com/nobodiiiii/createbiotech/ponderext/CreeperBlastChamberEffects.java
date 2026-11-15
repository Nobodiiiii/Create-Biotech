package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxEntity;
import com.nobodiiiii.createbiotech.ponder.PonderSupportExt;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class CreeperBlastChamberEffects {
    private CreeperBlastChamberEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/1", "启动压机联动", CreeperBlastChamberEffects::startPressCycle);
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/2", "开始苦力怕压缩", CreeperBlastChamberEffects::startCompression);
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/3", "结束苦力怕压缩", CreeperBlastChamberEffects::clearCompression);
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/4", "将纸箱推入漏斗", CreeperBlastChamberEffects::pushInputBox);
    }

    private static void startPressCycle(StepBuildContext ctx) {
        var scene = ctx.builder();
        PonderSupportExt.startPressCycle(scene, new BlockPos(3, 4, 3), new BlockPos(5, 4, 5), -64.0f);
    }

    private static void startCompression(StepBuildContext ctx) {
        var scene = ctx.builder();
        PonderSupportExt.startCompressionAnimation(scene, "minecraft:creeper", 0, 64, 1.0f);
    }

    private static void clearCompression(StepBuildContext ctx) {
        var scene = ctx.builder();
        PonderSupportExt.clearCompressionAnimation(scene, "minecraft:creeper");
    }

    private static void pushInputBox(StepBuildContext ctx) {
        ctx.builder().addInstruction(new InputBoxInstruction());
    }

    private static final class InputBoxInstruction extends TickingInstruction {
        private static final int TRAVEL_TICKS = 10;
        private static final int INTAKE_TICKS = 2;
        private static final BlockPos FUNNEL_POS = new BlockPos(6, 1, 1);
        private static final Vec3 START = new Vec3(6.5, 1.0, 1.0);
        private static final Vec3 VELOCITY = new Vec3(0, 0, 0.05);
        private CardboardBoxEntity box;

        private InputBoxInstruction() {
            super(true, TRAVEL_TICKS + INTAKE_TICKS);
        }

        @Override
        public void reset(PonderScene scene) {
            super.reset(scene);
            box = null;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            var stack = CapturedEntityBoxHelper.createFilledBox(CBItems.LARGE_CARDBOARD_BOX.get(), EntityType.CREEPER);
            box = CardboardBoxEntity.fromItemStack(scene.getWorld(), START, stack);
            if (box == null)
                return;
            box.setNoGravity(true);
            box.setYRot(0);
            box.setDeltaMovement(VELOCITY);
            box.setOldPosAndRot();
            scene.getWorld().addFreshEntity(box);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            if (box == null || !box.isAlive())
                return;

            int elapsed = totalTicks - remainingTicks;
            // Restore the authored path after physics, keeping interpolation between authored positions.
            box.setPos(START.add(VELOCITY.scale(Math.min(elapsed - 1, TRAVEL_TICKS))));
            box.setYRot(0);
            box.setOldPosAndRot();
            box.setPos(START.add(VELOCITY.scale(Math.min(elapsed, TRAVEL_TICKS))));
            box.setDeltaMovement(elapsed < TRAVEL_TICKS ? VELOCITY : Vec3.ZERO);

            if (elapsed == TRAVEL_TICKS && scene.getWorld().getBlockEntity(FUNNEL_POS) instanceof FunnelBlockEntity funnel)
                funnel.flap(true);
            if (isComplete())
                box.discard();
        }
    }


}
