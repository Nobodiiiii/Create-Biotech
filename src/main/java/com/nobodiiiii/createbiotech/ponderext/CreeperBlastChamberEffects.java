package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.ponder.PonderSupportExt;
import net.minecraft.core.BlockPos;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class CreeperBlastChamberEffects {
    private CreeperBlastChamberEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/1", "启动压机联动", CreeperBlastChamberEffects::startPressCycle);
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/2", "开始苦力怕压缩", CreeperBlastChamberEffects::startCompression);
        engine.steps().registerJava("create_biotech:java/creeper_blast_chamber/3", "结束苦力怕压缩", CreeperBlastChamberEffects::clearCompression);
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


}
