package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class ShulkerPackagerEffects {
    private ShulkerPackagerEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/shulker_packager/1", "连接打包机", ShulkerPackagerEffects::linkEffect);
        engine.steps().registerJava("create_biotech:java/shulker_packager/2", "打包动画", ShulkerPackagerEffects::createPackage);
        engine.steps().registerJava("create_biotech:java/shulker_packager/3", "移出包裹", ShulkerPackagerEffects::clearPackage);
        engine.steps().registerJava("create_biotech:java/shulker_packager/4", "拆包动画", ShulkerPackagerEffects::unpackPackage);
    }

    private static void linkEffect(StepBuildContext ctx) {
        var scene = ctx.builder();
        var createScene = new CreateSceneBuilder(scene);
        PonderHilo.linkEffect(createScene, new BlockPos(0, 1, 1));
    }

    private static void createPackage(StepBuildContext ctx) {
        var scene = ctx.builder();
        var createScene = new CreateSceneBuilder(scene);
        var sourcePackager = new BlockPos(0, 1, 2);
        var shulkerPackage = (ItemStack) ctx.state().computeIfAbsent("shulker.package", key -> new ItemStack(CBItems.SHULKER_PACKAGE.get()));
        PonderHilo.packagerCreate(createScene, sourcePackager, shulkerPackage);
    }

    private static void clearPackage(StepBuildContext ctx) {
        var scene = ctx.builder();
        var createScene = new CreateSceneBuilder(scene);
        var sourcePackager = new BlockPos(0, 1, 2);
        PonderHilo.packagerClear(createScene, sourcePackager);
    }

    private static void unpackPackage(StepBuildContext ctx) {
        var scene = ctx.builder();
        var createScene = new CreateSceneBuilder(scene);
        var targetPackager = new BlockPos(2, 1, 2);
        var shulkerPackage = (ItemStack) ctx.state().computeIfAbsent("shulker.package", key -> new ItemStack(CBItems.SHULKER_PACKAGE.get()));
        PonderHilo.packagerUnpack(createScene, targetPackager, shulkerPackage);
    }


}
