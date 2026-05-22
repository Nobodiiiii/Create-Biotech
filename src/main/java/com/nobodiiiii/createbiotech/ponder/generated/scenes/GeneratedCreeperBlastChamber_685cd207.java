package com.nobodiiiii.createbiotech.ponder.generated.scenes;

import com.nobodiiiii.createbiotech.ponder.PonderSupportExt;
import com.nobodiiiii.createbiotech.ponder.generated.GeneratedPonderAttribution;
import com.nobodiiiii.createbiotech.ponder.generated.GeneratedPonderSupport;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

public final class GeneratedCreeperBlastChamber_685cd207 {
    private GeneratedCreeperBlastChamber_685cd207() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation[] tags = new ResourceLocation[]{new ResourceLocation("create_biotech", "ponderer_exported")};
        var multi = helper.forComponents(java.util.List.of(new ResourceLocation("create_biotech", "creeper_blast_chamber")));
        multi.addStoryBoard(new ResourceLocation("create_biotech", "generated/ponderer/creeper_blast_chamber_base_xl"), GeneratedCreeperBlastChamber_685cd207::storyboard$0, tags);
        multi.addStoryBoard(new ResourceLocation("create_biotech", "generated/ponderer/creeper_blast_chamber_base_xl"), GeneratedCreeperBlastChamber_685cd207::storyboard$1, tags);
        multi.addStoryBoard(new ResourceLocation("create_biotech", "generated/ponderer/creeper_blast_chamber_base_xl"), GeneratedCreeperBlastChamber_685cd207::storyboard$2, tags);
        multi.addStoryBoard(new ResourceLocation("create_biotech", "generated/ponderer/creeper_blast_chamber_base_xl"), GeneratedCreeperBlastChamber_685cd207::storyboard$3, tags);
        multi.addStoryBoard(new ResourceLocation("create_biotech", "generated/ponderer/creeper_blast_chamber_base_xl"), GeneratedCreeperBlastChamber_685cd207::storyboard$4, tags);
    }

    public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation tag = GeneratedPonderAttribution.tag();
        helper.addTagToComponent(new ResourceLocation("create_biotech", "creeper_blast_chamber"), tag);
    }

    private static void storyboard$0(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creeper_blast_chamber_scene_1", "Scene 1");
        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();
        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(9, 5, 9));
        scene.addKeyframe();
        GeneratedPonderSupport.showStructure(scene, context, new BlockPos(0, 0, 0), new BlockPos(9, 0, 9), null, null);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_large.nbt"), new BlockPos(2, 1, 2), 0, false, false, false, "none", null, null, null, null, null);
        GeneratedPonderSupport.showSectionAndMerge(scene, context, new BlockPos(6, 1, 2), null, null, 20, "down", null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showSectionAndMerge(scene, context, new BlockPos(6, 1, 2), new BlockPos(2, 5, 6), null, 20, "down", "west", 20, 1, true);
        scene.idle(20);
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(4.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06403808123981149d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b}");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06403808123981149d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b}");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(3.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06403808123981149d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b}");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06403808123981149d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b}");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 4.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.06403808123981149d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b}");
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "这是一个苦力怕爆破室", new Vec3(3.5, 4.5, 2.0), 60, null, true);
        scene.idle(70);
        GeneratedPonderSupport.showText(scene, "它可以高效批量地执行粉碎配方", new Vec3(3.5, 4.5, 2.0), 100, null, true);
        scene.idle(110);
        GeneratedPonderSupport.showText(scene, "或者...你想来点更劲爆的？", new Vec3(3.5, 4.5, 2.0), 100, null, true);
        scene.idle(110);
    }

    private static void storyboard$1(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creeper_blast_chamber_s_10040", "苦力怕爆破室 搭建");
        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();
        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(0, 0, 0), new BlockPos(6, 5, 6));
        GeneratedPonderSupport.showStructure(scene, context, null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_small.nbt"), new BlockPos(4, 1, 2), 0, false, false, false, "simultaneous", 20, 1, true, null, "down");
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "结构支持3种尺寸", new Vec3(4.5, 4.5, 2.0), 120, null, true);
        scene.idle(30);
        GeneratedPonderSupport.setBlock(scene, context, "minecraft:air", null, new BlockPos(0, 1, 0), new BlockPos(6, 5, 6), null, true, null, "none", null, null, null, null, null);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_medium.nbt"), new BlockPos(3, 1, 2), 0, false, false, false, "none", null, null, null, null, null);
        GeneratedPonderSupport.showSectionAndMerge(scene, context, new BlockPos(0, 0, 0), new BlockPos(6, 5, 6), null, 0, "down", null, null, null, null);
        scene.idle(30);
        GeneratedPonderSupport.setBlock(scene, context, "minecraft:air", null, new BlockPos(0, 1, 0), new BlockPos(6, 5, 6), null, true, null, "none", null, null, null, null, null);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_large.nbt"), new BlockPos(2, 1, 2), 0, false, false, false, "none", null, null, null, null, null);
        GeneratedPonderSupport.showSectionAndMerge(scene, context, new BlockPos(0, 0, 0), new BlockPos(6, 5, 6), null, 0, "down", null, null, null, null);
        scene.idle(80);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "结构顶面中心需为动力冲压机", new Vec3(4.5, 4.0, 4.5), 100, null, true);
        scene.idle(110);
        scene.addKeyframe();
        GeneratedPonderSupport.hideSection(scene, context, new BlockPos(2, 2, 2), new BlockPos(6, 4, 6), 20, "up");
        scene.idle(20);
        GeneratedPonderSupport.showText(scene, "结构底面中心需为生物打包机", new Vec3(4.5, 1.0, 4.5), 100, null, true);
        scene.idle(110);
        GeneratedPonderSupport.setBlock(scene, context, "create_biotech:explosion_proof_casing", null, new BlockPos(5, 4, 2), new BlockPos(3, 4, 2), null, false, false, "none", null, null, null, null, null);
        scene.addKeyframe();
        GeneratedPonderSupport.showSectionAndMerge(scene, context, new BlockPos(2, 2, 2), new BlockPos(6, 4, 6), null, 20, "down", null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showText(scene, "结构的框架由防爆机壳搭成", new Vec3(2.5, 3.5, 2.0), 100, null, true);
        scene.idle(110);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "除了顶面和底面的任意面中心可以被替换为防爆玻璃", new Vec3(4.5, 3.5, 2.0), 120, null, true);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "除此之外，每个结构还必须包括一个爆破室核心", new Vec3(6.5, 1.5, 2.0), 120, null, true);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "以及两个任意大小的防爆保险库（连接的保险库算做一个）", new Vec3(2.0, 3.5, 4.5), 120, null, true);
        scene.idle(40);
        GeneratedPonderSupport.setBlock(scene, context, "create_biotech:explosion_proof_item_vault", Map.ofEntries(Map.entry("large", "false"), Map.entry("axis", "x")), new BlockPos(2, 2, 3), new BlockPos(2, 3, 4), "{BlastChamberController:-11819750039612L,BlastChamberRole:1,Controller:{X:-44,Y:-59,Z:-10},ForgeCaps:{},Inventory:{Items:[],Size:20},LastKnownPos:{X:-44,Y:-58,Z:-9},ScrollValue:1,StorageType:\"CombinedInv\"}", true, null, "none", null, null, null, null, null);
        scene.idle(90);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "结构成型以后，顶面的棱会被自动替换为链式传动箱", new Vec3(3.5, 4.5, 2.0), 120, null, true);
        GeneratedPonderSupport.setBlock(scene, context, "create_biotech:blast_proof_chain_drive", Map.ofEntries(Map.entry("part", "middle"), Map.entry("axis", "z"), Map.entry("axis_along_first", "true")), new BlockPos(5, 4, 2), new BlockPos(3, 4, 2), "{ForgeCaps:{},Speed:0.0f}", true, null, "none", null, null, null, null, null);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "使用扳手可以在机壳和传动箱之间切换，但是需要保证能够剩下的传动箱足以驱动所有冲压机", new Vec3(4.5, 4.5, 2.0), 180, null, true);
        GeneratedPonderSupport.showControls(scene, new Vec3(4.5, 5.0, 2.5), "down", 60, "right", "create:wrench", null, false, false);
        GeneratedPonderSupport.setBlock(scene, context, "create_biotech:explosion_proof_casing", Map.ofEntries(Map.entry("part", "middle"), Map.entry("axis", "z"), Map.entry("axis_along_first", "true")), new BlockPos(4, 4, 2), null, null, true, null, "none", null, null, null, null, null);
        scene.idle(190);
    }

    private static void storyboard$2(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creeper_blast_chamber_s_c38b1", "苦力怕爆破室 运作");
        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();
        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(2, 1, 1), new BlockPos(6, 4, 5));
        GeneratedPonderSupport.showStructure(scene, context, null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_large.nbt"), new BlockPos(2, 1, 2), 0, false, false, false, "simultaneous", 20, 1, true, null, "down");
        scene.idle(40);
        GeneratedPonderSupport.setBlock(scene, context, "create:andesite_funnel", Map.ofEntries(Map.entry("waterlogged", "false"), Map.entry("powered", "false"), Map.entry("facing", "north"), Map.entry("extracting", "false")), new BlockPos(6, 1, 1), null, "{Filter:{Count:1b,id:\"minecraft:air\"},FilterAmount:64,ForgeCaps:{},Owner:[I;-503341427,743590127,-1630936259,1659302309],TransferCooldown:0,UpTo:1b}", true, null, "none", null, null, null, null, null);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "爆炸室核心可以通过纸箱输入和输出苦力怕", new Vec3(6.5, 1.0, 1.5), 150, null, true);
        scene.idle(40);
        GeneratedPonderSupport.createItemEntity(scene, "create_biotech:large_cardboard_box", 1, new Vec3(6.5, 1.5, 1.5), new Vec3(0.0, 0.0, 0.0), null);
        scene.idle(20);
        GeneratedPonderSupport.clearItemEntities(scene, true, null, null, null);
        scene.addKeyframe();
        GeneratedPonderSupport.rotateCameraY(scene, 35.0f, 35.0f, 10);
        scene.idle(10);
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 5.5), null, null, null, null);
        scene.idle(20);
        scene.idle(60);
        GeneratedPonderSupport.hideSection(scene, context, new BlockPos(6, 1, 1), null, 20, "up");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(3.5, 2.0, 3.5), null, null, null, null);
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(4.5, 2.0, 3.5), null, null, null, null);
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 3.5), null, null, null, null);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "最大的爆破室最多可以有9个苦力怕同时工作", new Vec3(4.5, 3.5, 2.0), 110, null, true);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "通入应力...", new Vec3(3.5, 4.5, 2.0), 60, null, true);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_press_array.nbt"), new BlockPos(3, 4, 2), 0, false, true, false, "none", null, null, null, null, null);
        PonderSupportExt.startPressCycle(scene, new BlockPos(3, 4, 3), new BlockPos(5, 4, 5), -64.0f);
        PonderSupportExt.startCompressionAnimation(scene, "minecraft:creeper", 0, 64, 1.0f);
        GeneratedPonderSupport.playSound(scene, "minecraft:entity.creeper.primed", 0.55f, 1.0f, "blocks");
        scene.idle(9);
        GeneratedPonderSupport.playSound(scene, "minecraft:entity.creeper.primed", 0.7f, 1.15f, "blocks");
        scene.idle(6);
        GeneratedPonderSupport.playSound(scene, "minecraft:entity.generic.explode", 1.0f, 1.15f, "blocks");
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(3.5, 2.5, 3.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(4.5, 2.5, 3.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(5.5, 2.5, 3.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(3.5, 2.5, 4.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(4.5, 2.5, 4.5), new Vec3(0.0, 0.05, 0.0), 2.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(5.5, 2.5, 4.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(3.5, 2.5, 5.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(4.5, 2.5, 5.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:large_smoke", new Vec3(5.5, 2.5, 5.5), new Vec3(0.0, 0.05, 0.0), 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(3.5, 2.5, 3.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(4.5, 2.5, 3.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(5.5, 2.5, 3.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(3.5, 2.5, 4.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion_emitter", new Vec3(4.5, 2.5, 4.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(5.5, 2.5, 4.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(3.5, 2.5, 5.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(4.5, 2.5, 5.5), Vec3.ZERO, 1.0f, 1);
        PonderSupportExt.emitParticles(scene, "minecraft:explosion", new Vec3(5.5, 2.5, 5.5), Vec3.ZERO, 1.0f, 1);
        GeneratedPonderSupport.rotateCameraY(scene, 4.0f, 4.0f, 2);
        GeneratedPonderSupport.rotateCameraY(scene, -8.0f, -8.0f, 2);
        GeneratedPonderSupport.rotateCameraY(scene, 4.0f, 4.0f, 2);
        scene.idle(15);
        PonderSupportExt.clearCompressionAnimation(scene, "minecraft:creeper");
        GeneratedPonderSupport.rotateCameraY(scene, -35.0f, -35.0f, 10);
        GeneratedPonderSupport.modifyBlockEntity(scene, Map.ofEntries(Map.entry("axis", "z"), Map.entry("axis_along_first", "true")), "{ForgeCaps:{},Speed:0.0f}", null, new BlockPos(3, 4, 2), new BlockPos(5, 4, 1));
        scene.idle(20);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "每有一只苦力怕，爆破室就会提取输入保险库中的一组物品，执行粉碎轮的粉碎配方，输出到输出保险库", new Vec3(2.0, 3.5, 4.5), 200, null, true);
        scene.idle(210);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "右键长按保险库可以调整其为输入或输出", new Vec3(2.0, 3.5, 4.5), 100, null, true);
        scene.idle(110);
    }

    private static void storyboard$3(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creeper_blast_chamber_s_2c227", "苦力怕爆破室 过载");
        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();
        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(2, 1, 1), new BlockPos(3, 4, 2));
        GeneratedPonderSupport.showStructure(scene, context, null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_large.nbt"), new BlockPos(2, 1, 2), 0, false, false, false, "simultaneous", 20, 1, false, null, "down");
        scene.idle(30);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "苦力怕爆破室最高接受128rpm的转速", new Vec3(4.5, 4.5, 2.0), 120, null, true);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_128rpm.nbt"), new BlockPos(3, 4, 1), 0, false, true, false, "none", null, null, null, null, null);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "超过128rpm的转速会使其进入过载状态", new Vec3(4.0, 4.5, 1.5), 120, "red", true);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_256rpm.nbt"), new BlockPos(3, 4, 1), 0, false, true, false, "none", null, null, null, null, null);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "不要长时间过载，除非你想引起一次足以炸毁你基地的大爆炸！", new Vec3(4.0, 4.5, 1.5), 120, "red", true);
        scene.idle(130);
    }

    private static void storyboard$4(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creeper_blast_chamber_s_a77f7", "苦力怕爆破室 高压聚爆");
        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();
        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(2, 1, 2), new BlockPos(2, 1, 2));
        GeneratedPonderSupport.showStructure(scene, context, null, null, null, null);
        scene.idle(20);
        GeneratedPonderSupport.showExtraStructure(scene, context, new ResourceLocation("create_biotech", "ponder/generated/ponderer/creeper_blast_chamber_large.nbt"), new BlockPos(2, 1, 2), 0, false, false, false, "simultaneous", 20, 1, true, null, "down");
        scene.idle(20);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "向爆破室中输入高压苦力怕...", new Vec3(6.5, 1.5, 2.0), 100, null, true);
        scene.idle(20);
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(5.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.078375d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b,powered:1b}");
        GeneratedPonderSupport.createEntity(scene, "minecraft:creeper", new Vec3(4.5, 2.0, 3.5), null, null, null, "{AbsorptionAmount:0.0f,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.25d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,ExplosionRadius:3b,FallFlying:0b,ForgeData:{create_biotech.previous_liquid_living_slime_vertical_speed:-0.078375d,create_biotech.was_touching_liquid_living_slime:0b},Fuse:30s,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,Invulnerable:0b,LeftHanded:0b,PersistenceRequired:0b,ignited:0b,powered:1b}");
        scene.idle(90);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "可以将配方调整为“高压聚爆”模式", new Vec3(6.5, 1.5, 2.0), 100, "input", true);
        scene.idle(110);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "“高压聚爆”模式有单独的配方，不再执行粉碎配方", new Vec3(6.5, 1.5, 2.0), 120, null, true);
        scene.idle(130);
        scene.addKeyframe();
        GeneratedPonderSupport.showText(scene, "当然，过载引起的爆炸烈度也会成倍增加", new Vec3(4.0, 4.5, 1.5), 120, "red", true);
        scene.idle(130);
    }

}
