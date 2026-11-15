package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.PonderActions;
import net.minecraft.core.BlockPos;

/** Original explicit bounds, applied at each scene boundary. */
final class SceneBounds {
    private SceneBounds() {}
    static void install(RuntimeEngine engine) {
        engine.fragments().beforeScene("create_biotech:creeper_blast_chamber", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(9, 5, 9));
        });
        engine.fragments().beforeScene("create_biotech:creeper_blast_chamber", "s_10040", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(6, 5, 6));
        });
        engine.fragments().beforeScene("create_biotech:creeper_blast_chamber", "s_c38b1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(2, 1, 1), new BlockPos(6, 4, 5));
        });
        engine.fragments().beforeScene("create_biotech:creeper_blast_chamber", "s_2c227", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(2, 1, 1), new BlockPos(3, 4, 2));
        });
        engine.fragments().beforeScene("create_biotech:creeper_blast_chamber", "s_a77f7", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(2, 1, 2), new BlockPos(2, 1, 2));
        });
        engine.fragments().beforeScene("create_biotech:bone_ratchet", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(4, 1, 5));
        });
        engine.fragments().beforeScene("create_biotech:evoker_enchanting_chamber", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 1, 3), new BlockPos(6, 4, 6));
        });
        engine.fragments().beforeScene("create_biotech:magma_belt_connector", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(5, 3, 5));
        });
        engine.fragments().beforeScene("create_biotech:slime_clutch", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(4, 1, 5));
        });
        engine.fragments().beforeScene("create_biotech:squid_printer", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 1, 3), new BlockPos(6, 4, 6));
        });
        engine.fragments().beforeScene("create_biotech:universal_joint", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(4, 5, 5));
        });
        engine.fragments().beforeScene("create_biotech:experience_pump", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(4, 2, 5));
        });
        engine.fragments().beforeScene("create_biotech:experience_pump", "s_40666", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(1, 1, 0), new BlockPos(2, 2, 3));
        });
        engine.fragments().beforeScene("create_biotech:schrodingers_cat", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(2, 1, 1), new BlockPos(2, 1, 3));
        });
        engine.fragments().beforeScene("create_biotech:slime_belt_connector", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(7, 7, 7));
        });
        engine.fragments().beforeScene("create_biotech:spider_assembly_table", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(5, 3, 5));
        });
        engine.fragments().beforeScene("create_biotech:bio_packager", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(1, 1, 2), new BlockPos(2, 1, 3));
        });
        engine.fragments().beforeScene("create_biotech:bio_packager", "s_fe8b2", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(6, 3, 6));
        });
        engine.fragments().beforeScene("create_biotech:power_belt_connector", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(6, 2, 3));
        });
        engine.fragments().beforeScene("create_biotech:ghast_helm", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(9, 9, 9));
        });
        engine.fragments().beforeScene("create_biotech:ghast_helm", "s_29671", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(4, 1, 2), new BlockPos(4, 6, 4));
        });
        engine.fragments().beforeScene("create_biotech:ghast_hot_air_balloon_assembly_station", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(9, 9, 9));
        });
        engine.fragments().beforeScene("create_biotech:ghast_hot_air_balloon_assembly_station", "s_29671", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(4, 1, 2), new BlockPos(4, 6, 4));
        });
        engine.fragments().beforeScene("create_biotech:shulker_packager", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 1, 0), new BlockPos(4, 1, 3));
        });
        engine.fragments().beforeScene("create_biotech:shulker_teleporter", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(4, 3, 4));
        });
        engine.fragments().beforeScene("create_biotech:petri_dish", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(1, 1, 3), new BlockPos(4, 3, 3));
        });
        engine.fragments().beforeScene("create_biotech:petri_dish", "s_e7373", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(3, 1, 2), new BlockPos(3, 1, 6));
        });
        engine.fragments().beforeScene("create_biotech:allay_port", "scene_1", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(8, 6, 8));
        });
        engine.fragments().beforeScene("create_biotech:allay_port", "s_21db7", ctx -> {
            var scene = ctx.builder();
            PonderActions.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(8, 6, 8));
        });
    }
}
