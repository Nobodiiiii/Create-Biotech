package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.PonderEntityAnimations;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class PowerBeltConnectorEffects {
    private static final int SHEEP_TIMELINE_TICKS = 110;
    private static final int SHEEP_RUN_TICKS = 60 * 20;

    private PowerBeltConnectorEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/power_belt_connector/1", "村民入场", PowerBeltConnectorEffects::showVillager);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/2", "村民行走", PowerBeltConnectorEffects::walkVillager);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/3", "第一只僵尸入场", PowerBeltConnectorEffects::showFirstZombie);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/4", "村民与僵尸行走", PowerBeltConnectorEffects::walkWithFirstZombie);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/5", "第二只僵尸入场", PowerBeltConnectorEffects::showSecondZombie);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/6", "村民与两只僵尸行走", PowerBeltConnectorEffects::walkWithBothZombies);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/7", "生物升起退场", PowerBeltConnectorEffects::clearCreatures);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/8", "绵羊入场", PowerBeltConnectorEffects::showSheep);
        engine.steps().registerJava("create_biotech:java/power_belt_connector/9", "绵羊行走", PowerBeltConnectorEffects::walkSheep);
    }

    private static void showVillager(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.createEntityWithDropIn(scene, entityContext, "villager", "minecraft:villager", new Vec3(4.5, 1.8, 3.5), new Vec3(100, 2.8, 3.5), null, null, "{AbsorptionAmount:0.0f,Age:1,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.5d,Name:\"minecraft:generic.movement_speed\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.08d,Name:\"forge:entity_gravity\"}],Brain:{memories:{}},CanPickUpLoot:1b,CanUpdate:1b,FallFlying:0b,FoodLevel:0b,ForcedAge:0,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06667855189484745d,create_biotech.was_touching_liquid_living_slime:0b},Gossips:[],HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Inventory:[],Invulnerable:0b,LastGossipDecay:3319438L,LastRestock:0L,LeftHanded:0b,PersistenceRequired:0b,RestocksToday:0,VillagerData:{level:1,profession:\"minecraft:none\",type:\"minecraft:plains\"},Xp:0}", 1.0, 10);
    }

    private static void walkVillager(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.playWalkAnimation(scene, entityContext, Map.ofEntries(Map.entry("villager", 0.5f)), 120);
    }

    private static void showFirstZombie(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.createEntityWithDropIn(scene, entityContext, "zombie_1", "minecraft:zombie", new Vec3(2.5, 1.8, 3.5), new Vec3(5.5, 1.8, 3.5), null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"}],Brain:{memories:{}},CanBreakDoors:0b,CanPickUpLoot:0b,CanUpdate:1b,DrownedConversionTime:-1,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.078375d,create_biotech.was_touching_liquid_living_slime:0b},HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,InWaterTime:-1,Invulnerable:0b,IsBaby:0b,LeftHanded:0b,PersistenceRequired:0b}", 1.0, 10);
    }

    private static void walkWithFirstZombie(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.playWalkAnimation(scene, entityContext, Map.ofEntries(Map.entry("villager", 1.0f), Map.entry("zombie_1", 0.5f)), 110);
    }

    private static void showSecondZombie(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.createEntityWithDropIn(scene, entityContext, "zombie_2", "minecraft:zombie", new Vec3(1.5, 1.8, 3.5), new Vec3(5.5, 1.8, 3.5), null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"}],Brain:{memories:{}},CanBreakDoors:0b,CanPickUpLoot:0b,CanUpdate:1b,DrownedConversionTime:-1,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.078375d,create_biotech.was_touching_liquid_living_slime:0b},HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,InWaterTime:-1,Invulnerable:0b,IsBaby:0b,LeftHanded:0b,PersistenceRequired:0b}", 1.0, 10);
    }

    private static void walkWithBothZombies(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.playWalkAnimation(scene, entityContext, Map.ofEntries(Map.entry("villager", 0.8f), Map.entry("zombie_1", 0.5f), Map.entry("zombie_2", 0.5f)), 110);
    }

    private static void clearCreatures(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.clearEntitiesWithRiseOut(scene, entityContext, 1.0, 10);
    }

    private static void showSheep(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        PonderEntityAnimations.createEntityWithDropIn(scene, entityContext, "sheep", "minecraft:sheep", new Vec3(2.5, 1.8, 3.5), new Vec3(5.5, 1.8, 3.5), null, null, "{AbsorptionAmount:0.0f,Age:1,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,Color:0b,FallFlying:0b,ForcedAge:0,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.078375d,create_biotech.was_touching_liquid_living_slime:0b},HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:8.0f,HurtByTimestamp:0,InLove:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,Sheared:0b}", 1.0, 10);
    }

    private static void walkSheep(StepBuildContext ctx) {
        var scene = ctx.builder();
        var entityContext = (PonderEntityAnimations.EntityAnimContext) ctx.state().computeIfAbsent("power_belt.entities", key -> PonderEntityAnimations.newEntityAnimContext());
        var speeds = Map.of("sheep", 0.5f);
        PonderEntityAnimations.playWalkAnimation(scene, entityContext, speeds, SHEEP_TIMELINE_TICKS);
        // Keep the original progress duration while the sheep and 80 RPM structure continue.
        // The following scene step restores the stopped belt after this extended run.
        scene.markAsFinished();
        PonderEntityAnimations.playWalkAnimation(scene, entityContext, speeds,
            SHEEP_RUN_TICKS - SHEEP_TIMELINE_TICKS);
    }


}
