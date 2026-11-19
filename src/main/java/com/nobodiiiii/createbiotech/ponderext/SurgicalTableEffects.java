package com.nobodiiiii.createbiotech.ponderext;

import java.util.List;
import java.util.function.Consumer;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.runtime.api.StepType;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderInterfaces;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderHighlight;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Compiled model effects; all narration and timing remain editable JavaScene data. */
final class SurgicalTableEffects {
    private static final String PREFIX = "create_biotech:java/surgical_table/";
    private static final String ACTORS = "create_biotech:surgical_table.actors";

    private SurgicalTableEffects() {}

    static void install(RuntimeEngine engine) {
        SurgicalTraitPonderEffects.install(engine);
        engine.steps().register(PREFIX + "highlight_parts", new StepType<SurgicalPonderHighlight.Spec>() {
            @Override public StepCodec<SurgicalPonderHighlight.Spec> codec() { return SurgicalPonderHighlight.CODEC; }
            @Override public String editorLabel() { return "自动计算并高亮手术部件"; }
            @Override public void build(SurgicalPonderHighlight.Spec parameters, StepBuildContext ctx) {
                SurgicalPonderHighlight.schedule(ctx.builder(), actors(ctx), parameters);
            }
        });
        // The 100-tick irreversible warning has five ticks of native fade on each end.
        engine.steps().registerJava(PREFIX + "highlight_zombie", "高亮僵尸供体全身", ctx ->
            SurgicalPonderHighlight.schedule(ctx.builder(), PonderPalette.BLUE, 110, actors(ctx)::zombieHighlightCorners, false));
        engine.steps().registerJava(PREFIX + "prepare", "准备手术演示模型", ctx -> actors(ctx));
        engine.steps().registerJava(PREFIX + "clear_petri_dish", "清空培养完成的史莱姆培养皿", ctx ->
            ctx.builder().world().modifyBlockEntity(new BlockPos(0, 1, 7), PetriDishBlockEntity.class,
                PetriDishBlockEntity::clearContent));
        register(engine, "empty", "清空演示台面", actors -> actors.setStage(0));
        register(engine, "place_zombie", "放置僵尸供体", actors -> actors.setStage(1));
        register(engine, "place_golem", "放置铁傀儡供体", actors -> actors.setStage(2));
        register(engine, "place_pillager", "放置掠夺者供体", SurgicalPonderActors::placePillager);
        register(engine, "original_appearance", "显示供体原本外观", actors -> actors.setSourceAppearance(true));
        register(engine, "slime_appearance", "恢复手术外观", actors -> actors.setSourceAppearance(false));
        register(engine, "hide_result", "让演示成品退场", SurgicalPonderActors::hideIntroResult);
        register(engine, "return_to_table", "将成品放回台面", actors -> { actors.hideResult(); actors.unpackBody(); actors.setStage(10); });
        engine.steps().registerJava(PREFIX + "cut_arm", "剪开僵尸肩部连接", ctx -> move(ctx, 3, 22));
        register(engine, "pick_up_arm", "收起旧手臂", SurgicalPonderActors::pickUpOldArm);
        engine.steps().registerJava(PREFIX + "place_arm_hint", "展示装有旧手臂的纸箱", ctx -> {
            ItemStack box = previewBox(actors(ctx).createOldArmPreview());
            ctx.builder().overlay().showControls(new Vec3(1.8, 3, 5.2), Pointing.DOWN, 40).rightClick().withItem(box);
        });
        register(engine, "move_arm", "在台面边缘放下旧手臂", SurgicalPonderActors::placeOldArm);
        engine.steps().registerJava(PREFIX + "cut_donor", "剪下铁傀儡手臂", ctx -> move(ctx, 5, 16));
        engine.steps().registerJava(PREFIX + "glue_arm", "移动替换手臂到肩部", ctx -> move(ctx, 6, 40));
        register(engine, "finish_glue_arm", "固定替换手臂", actors -> actors.setStage(7));
        engine.steps().registerJava(PREFIX + "cut_legs", "剪下掠夺者双腿", ctx -> move(ctx, 8, 15));
        engine.steps().registerJava(PREFIX + "glue_legs", "移动供体双腿到小腿位置", ctx -> move(ctx, 9, 40));
        register(engine, "finish_glue_legs", "固定替换小腿", actors -> actors.setStage(10));
        engine.steps().registerJava(PREFIX + "assembled", "恢复已替换手臂的台面", ctx ->
            ctx.builder().addInstruction(s -> { actors(ctx).placePillager(); actors(ctx).setStage(10); }));
        engine.steps().registerJava(PREFIX + "shoulder", "安装替换手臂的肩关节", SurgicalTableEffects::installShoulder);
        engine.steps().registerJava(PREFIX + "other_joints", "同时安装颈髋膝关节", SurgicalTableEffects::installOtherJoints);
        engine.steps().registerJava(PREFIX + "release_hopping", "释放无关节成品并演示跳跃", ctx -> release(ctx, false));
        engine.steps().registerJava(PREFIX + "release_walking", "释放成品并演示转头行走挥击", ctx -> release(ctx, true));
        engine.steps().registerJava(PREFIX + "result_intro", "让仿生生物入场并攻击盔甲架", ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(s -> actors.showIntroResult());
            bindTarget(ctx, actors, "intro_target");
        });
        engine.steps().registerJava(PREFIX + "pack_result_for_stats", "把演示成品装箱并填入属性页", SurgicalTableEffects::packResultForStats);
    }

    private static void register(RuntimeEngine engine, String id, String label, Consumer<SurgicalPonderActors> action) {
        engine.steps().registerJava(PREFIX + id, label, ctx -> {
            SurgicalPonderActors actors = actors(ctx);
            ctx.builder().addInstruction(s -> action.accept(actors));
        });
    }

    private static SurgicalPonderActors actors(StepBuildContext ctx) {
        return (SurgicalPonderActors) ctx.state().computeIfAbsent(ACTORS, key -> {
            var actors = new SurgicalPonderActors(ctx.builder().getScene().getWorld());
            // PonderScene.begin() clears the element list after compilation, including on replay.
            // Register the models through the playback schedule so they survive that reset.
            ctx.builder().addInstruction(scene -> {
                actors.reset(scene);
                scene.addElement(actors);
            });
            return actors;
        });
    }

    private static void move(StepBuildContext ctx, int stage, int ticks) {
        SurgicalPonderActors actors = actors(ctx);
        ctx.builder().addInstruction(new TickingInstruction(false, ticks) {
            @Override protected void firstTick(PonderScene scene) { actors.startMovement(stage); }
            @Override public void tick(PonderScene scene) {
                super.tick(scene);
                actors.setMovement(1 - remainingTicks / (float) totalTicks);
            }
        });
    }

    private static void installShoulder(StepBuildContext ctx) {
        var scene = ctx.builder();
        var actors = actors(ctx);
        SurgicalPonderHighlight.schedule(scene, actors,
            new SurgicalPonderHighlight.Spec(List.of("new_arm"), PonderPalette.GREEN, 60));
        showJointControl(scene, actors.point("new_arm"), Pointing.DOWN, 25, SurgicalLimbType.SHOULDER);
        scene.idle(25);
        showJointControl(scene, actors.point("body"), Pointing.DOWN, 25, SurgicalLimbType.SHOULDER);
        scene.idle(30);
    }

    private static void installOtherJoints(StepBuildContext ctx) {
        var scene = ctx.builder();
        var actors = actors(ctx);
        int duration = 40;
        SurgicalPonderHighlight.schedule(scene, actors, new SurgicalPonderHighlight.Spec(
            List.of("head", "right_leg", "left_leg", "right_knee", "left_knee"), PonderPalette.GREEN, duration + 10));
        showJointControl(scene, actors.point("head"), Pointing.DOWN, duration, SurgicalLimbType.NECK);

        // Use both sides of the body and stagger the leg prompts so the item icons remain visible.
        showJointControl(scene, actors.point("right_leg").add(0, 0.25, 0), Pointing.RIGHT, duration, SurgicalLimbType.HIP);
        showJointControl(scene, actors.point("left_leg").add(0, 0.25, 0), Pointing.LEFT, duration, SurgicalLimbType.HIP);
        showJointControl(scene, actors.point("right_knee").add(0, -0.85, 0), Pointing.RIGHT, duration, SurgicalLimbType.KNEE);
        showJointControl(scene, actors.point("left_knee").add(0, -0.85, 0), Pointing.LEFT, duration, SurgicalLimbType.KNEE);
    }

    private static void showJointControl(SceneBuilder scene, Vec3 point, Pointing direction, int duration, SurgicalLimbType type) {
        scene.overlay().showControls(point, direction, duration)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
    }

    private static void release(StepBuildContext ctx, boolean articulated) {
        var scene = ctx.builder();
        var actors = actors(ctx);
        scene.overlay().showControls(actors.point("body"), Pointing.DOWN, 25)
            .rightClick().withItem(new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get()));
        scene.idle(25);
        scene.addInstruction(s -> actors.packBody());
        scene.overlay().showControls(SurgicalPonderActors.RESULT_POSITION.add(0, 1.5, 0), Pointing.DOWN, 30)
            .rightClick().whileSneaking().withItem(new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get()));
        scene.idle(20);
        scene.addInstruction(s -> actors.releaseResult(articulated));
        bindTarget(ctx, actors, articulated ? "strike_target" : "contact_target");
    }

    private static void bindTarget(StepBuildContext ctx, SurgicalPonderActors actors, String target) {
        for (var link : ctx.entities().getOrDefault(target, List.of())) {
            ctx.builder().world().modifyEntity(link, entity -> {
                if (entity instanceof ArmorStand stand) actors.approachTarget(stand);
            });
        }
    }

    private static void packResultForStats(StepBuildContext ctx) {
        var actors = actors(ctx);
        ctx.builder().addInstruction(scene -> {
            ItemStack box = previewBox(actors.result());
            actors.hideResult();
            SurgicalPonderInterfaces.setCapturedBox(scene, box);
        });
    }

    private static ItemStack previewBox(SlimeBionicEntity entity) {
        ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
        CompoundTag snapshot = entity.saveWithoutId(new CompoundTag());
        snapshot.putString("id", EntityType.getKey(entity.getType()).toString());
        // The scene owns this snapshot. Gameplay capture runs on the server.
        CBItemData.edit(box, tag -> {
            tag.put("CapturedEntity", snapshot);
            tag.putString("CapturedEntityDescId", entity.getType().getDescriptionId());
            tag.putFloat("CapturedEntityHealth", entity.getHealth());
        });
        return box;
    }
}
