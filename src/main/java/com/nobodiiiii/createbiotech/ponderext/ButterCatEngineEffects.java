package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.minecraft.core.BlockPos;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class ButterCatEngineEffects {
    private static final BlockPos ENGINE_POS = new BlockPos(2, 2, 2);
    private static final String BUTTER_COUNT = "butter_cat_engine.butter_count";

    private ButterCatEngineEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/butter_cat_engine/1", "初始化黄油猫引擎", ButterCatEngineEffects::initialize);
        engine.steps().registerJava("create_biotech:java/butter_cat_engine/2", "添加黄油并提高转速", ButterCatEngineEffects::addButter);
        engine.steps().registerJava("create_biotech:java/butter_cat_engine/3", "添加超级黄油", ButterCatEngineEffects::addSuperButter);
    }

    private static void initialize(StepBuildContext ctx) {
        ctx.builder().configureBasePlate(0, 0, 5);
        ctx.state().put(BUTTER_COUNT, 0);
        new CreateSceneBuilder(ctx.builder()).world().setKineticSpeed(ctx.util().select().everywhere(), 0);
    }

    private static void addButter(StepBuildContext ctx) {
        int totalButter = (int) ctx.state().getOrDefault(BUTTER_COUNT, 0) + 1;
        ctx.state().put(BUTTER_COUNT, totalButter);
        var scene = new CreateSceneBuilder(ctx.builder());
        scene.world().modifyBlockEntity(ENGINE_POS, ButterCatEngineBlockEntity.class, be -> be.addButterCount(1));
        scene.world().setKineticSpeed(ctx.util().select().everywhere(), Math.min(256, totalButter * 16));
    }

    private static void addSuperButter(StepBuildContext ctx) {
        var scene = new CreateSceneBuilder(ctx.builder());
        scene.world().modifyBlockEntity(ENGINE_POS, ButterCatEngineBlockEntity.class, be -> be.setInfinite(true));
        scene.world().setKineticSpeed(ctx.util().select().everywhere(), 256);
    }
}
