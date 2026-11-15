package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nododiiiii.ponderer.runtime.api.PonderActions;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Author-owned effects; the warning particles are shared with the actual printer. */
final class SquidPrinterEffects {
    private static final BlockPos PRINTER = new BlockPos(5, 3, 4);
    private static final Vec3 SQUID = new Vec3(5.5, 4.0, 4.5);

    private SquidPrinterEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/squid_printer/dehydration",
            "演示鱿鱼缺水与脱水死亡", SquidPrinterEffects::showDehydration);
    }

    private static void showDehydration(StepBuildContext ctx) {
        // Ponder is also available from the main menu, before a server config is loaded.
        if (CBConfigs.SERVER_SPEC.isLoaded() && !CBConfigs.SERVER.squidPrinter.dehydrationKillsSquid.get())
            return;

        var scene = ctx.builder();
        scene.addKeyframe();
        PonderActions.showText(scene,
            LocalizedText.ref("create_biotech:squid_printer", "scene_1.dehydration_warning").resolve(),
            SQUID, 100, null, true);
        scene.idle(10);
        scene.world().modifyBlockEntity(PRINTER, SquidPrinterBlockEntity.class, printer -> {
            var fluid = printer.getFluidCapability(Direction.UP);
            if (fluid != null)
                fluid.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        });
        showWarningParticles(scene);
        scene.idle(100);

        scene.addKeyframe();
        PonderActions.showText(scene,
            LocalizedText.ref("create_biotech:squid_printer", "scene_1.dehydration_death").resolve(),
            SQUID, 120, null, true);
        scene.idle(10);
        for (int warning = 0; warning < 3; warning++) {
            showWarningParticles(scene);
            PonderActions.playSound(scene, "minecraft:entity.squid.hurt", 0.4f, 1.0f, "blocks");
            scene.idle(20);
        }
        new CreateSceneBuilder(scene).world().setBlock(PRINTER, AllBlocks.SPOUT.getDefaultState(), false);
        scene.world().createItemEntity(Vec3.atCenterOf(PRINTER), new Vec3(0, 0.1, 0),
            new ItemStack(Items.INK_SAC));
        PonderActions.playSound(scene, "minecraft:entity.squid.death", 0.4f, 1.0f, "blocks");
        scene.idle(60);
    }

    private static void showWarningParticles(SceneBuilder scene) {
        scene.effects().emitParticles(SQUID, (level, x, y, z) ->
            SquidPrinterBlockEntity.forEachDehydrationParticle(level, PRINTER,
                (px, py, pz, dx, dy, dz) -> level.addParticle(ParticleTypes.ANGRY_VILLAGER,
                    px, py, pz, dx, dy, dz)), 1, 1);
    }
}
