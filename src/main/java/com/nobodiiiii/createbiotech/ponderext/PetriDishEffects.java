package com.nobodiiiii.createbiotech.ponderext;

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.PonderActions;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class PetriDishEffects {
    // Remaining waits after the potion splashes: 78 + 90 + 20 + 90 ticks.
    private static final int REGENERATION_PARTICLE_TICKS = 278;

    private PetriDishEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/petri_dish/mixing", "搅拌史莱姆并播放音效", PetriDishEffects::mixSlime);
        engine.steps().registerJava("create_biotech:java/petri_dish/1", "投掷再生药水", PetriDishEffects::applyRegeneration);
        engine.steps().registerJava("create_biotech:java/petri_dish/2", "启动鼓风机", PetriDishEffects::startFan);
        engine.steps().registerJava("create_biotech:java/petri_dish/3", "刷新鼓风机前方方块", PetriDishEffects::refreshFan);
    }

    private static void mixSlime(StepBuildContext ctx) {
        var scene = ctx.builder();
        scene.idle(20);
        // Match Create's MIXING sound at 64 RPM: both layers every other tick.
        for (int cycle = 0; cycle < 23; cycle++) {
            PonderActions.playSound(scene, "minecraft:block.gilded_blackstone.break", 0.75f * 0.125f, 0.5f, "blocks");
            PonderActions.playSound(scene, "minecraft:block.netherrack.break", 0.75f * 0.125f, 0.5f, "blocks");
            scene.idle(2);
        }
        scene.idle(4);
    }

    private static void applyRegeneration(StepBuildContext ctx) {
        var scene = ctx.builder();
        ElementLink<EntityElement> regenerationPotion = scene.world().createEntity(level -> {
            ThrownPotion potion = new ThrownPotion(level, 3.5, 2.5, 3.5);
            potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.REGENERATION));
            potion.setDeltaMovement(0.0, -0.12, 0.0);
            potion.setNoGravity(true);
            potion.noPhysics = true;
            return potion;
        });
        scene.idle(12);
        scene.world().modifyEntity(regenerationPotion, Entity::discard);
        ColorParticleOption regenerationParticles =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.8f, 0.36f, 0.67f);
        scene.effects().emitParticles(
            new Vec3(3.5, 1.05, 3.5),
            scene.effects().particleEmitterWithinBlockSpace(regenerationParticles, new Vec3(0.0, 0.08, 0.0)),
            24.0f,
            1
        );
        scene.effects().emitParticles(
            new Vec3(3.5, 1.05, 3.5),
            scene.effects().particleEmitterWithinBlockSpace(regenerationParticles, new Vec3(0.0, 0.08, 0.0)),
            1.0f,
            REGENERATION_PARTICLE_TICKS
        );
        scene.world().modifyEntities(Villager.class,
            villager -> villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0)));
    }

    private static void startFan(StepBuildContext ctx) {
        var scene = ctx.builder();
        var util = ctx.util();
        var createScene = new CreateSceneBuilder(scene);
        var fanPos = new BlockPos(3, 1, 6);
        createScene.world().setKineticSpeed(util.select().position(fanPos), -32.0f);
    }

    private static void refreshFan(StepBuildContext ctx) {
        var scene = ctx.builder();
        var fanPos = new BlockPos(3, 1, 6);
        scene.world().modifyBlockEntity(
            fanPos,
            EncasedFanBlockEntity.class,
            EncasedFanBlockEntity::blockInFrontChanged
        );
    }


}
