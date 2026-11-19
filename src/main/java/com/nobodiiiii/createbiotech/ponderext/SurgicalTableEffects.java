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
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        engine.steps().registerJava(PREFIX + "highlight_zombie", "高亮僵尸供体全身", ctx ->
            SurgicalPonderHighlight.schedule(ctx.builder(), PonderPalette.BLUE, 75, actors(ctx)::zombieHighlightBounds));
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
        engine.steps().registerJava(PREFIX + "move_arm", "移开旧手臂", ctx -> move(ctx, 4, 30));
        engine.steps().registerJava(PREFIX + "cut_donor", "剪下铁傀儡手臂", ctx -> {
            highlight(ctx, "donor_arm", PonderPalette.RED, 35);
            ctx.builder().overlay().showControls(actors(ctx).point("donor_arm"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(Items.SHEARS));
            move(ctx, 5, 16);
        });
        engine.steps().registerJava(PREFIX + "glue_arm", "按选点顺序粘合替换手臂", SurgicalTableEffects::glueArm);
        engine.steps().registerJava(PREFIX + "glue_legs", "截取掠夺者双腿并安装小腿", SurgicalTableEffects::glueLegs);
        engine.steps().registerJava(PREFIX + "assembled", "恢复已替换手臂的台面", ctx ->
            ctx.builder().addInstruction(s -> { actors(ctx).placePillager(); actors(ctx).setStage(10); }));
        engine.steps().registerJava(PREFIX + "shoulder", "安装替换手臂的肩关节", ctx -> joint(ctx, "new_arm", SurgicalLimbType.SHOULDER));
        engine.steps().registerJava(PREFIX + "other_joints", "安装颈髋膝关节", ctx -> {
            joint(ctx, "head", SurgicalLimbType.NECK);
            joint(ctx, "right_leg", SurgicalLimbType.HIP);
            joint(ctx, "left_leg", SurgicalLimbType.HIP);
            joint(ctx, "right_knee", SurgicalLimbType.KNEE);
            joint(ctx, "left_knee", SurgicalLimbType.KNEE);
        });
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
                var rotation = scene.getTransform().yRotation;
                rotation.startWithValue(rotation.getValue() + 180);
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

    private static void highlight(StepBuildContext ctx, String part, PonderPalette color, int duration) {
        SurgicalPonderHighlight.schedule(ctx.builder(), actors(ctx),
            new SurgicalPonderHighlight.Spec(List.of(part), color, duration));
    }

    private static void glueArm(StepBuildContext ctx) {
        var scene = ctx.builder();
        scene.overlay().showControls(actors(ctx).detachedDonorPoint("donor_arm"), Pointing.DOWN, 35)
            .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
        highlight(ctx, "donor_arm", PonderPalette.GREEN, 45);
        scene.idle(35);
        scene.overlay().showControls(actors(ctx).point("body"), Pointing.DOWN, 35)
            .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
        highlight(ctx, "body", PonderPalette.BLUE, 70);
        scene.idle(20);
        move(ctx, 6, 40);
        scene.idle(45);
        scene.addInstruction(s -> actors(ctx).setStage(7));
        highlight(ctx, "new_arm", PonderPalette.GREEN, 45);
        scene.overlay().showControls(actors(ctx).point("new_arm"), Pointing.DOWN, 25)
            .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
    }

    private static void joint(StepBuildContext ctx, String part, SurgicalLimbType type) {
        var scene = ctx.builder();
        String parent = part.equals("right_knee") ? "right_leg" : part.equals("left_knee") ? "left_leg" : "body";
        highlight(ctx, part, PonderPalette.GREEN, 60);
        scene.overlay().showControls(actors(ctx).point(part), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.idle(25);
        scene.overlay().showControls(actors(ctx).point(parent), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.idle(30);
    }

    private static void glueLegs(StepBuildContext ctx) {
        var scene = ctx.builder();
        var actors = actors(ctx);
        var legs = actors.box("donor_right_leg").minmax(actors.box("donor_left_leg"));
        SurgicalPonderHighlight.schedule(scene, actors, new SurgicalPonderHighlight.Spec(
            List.of("donor_right_leg", "donor_left_leg"), PonderPalette.RED, 30));
        scene.overlay().showControls(new Vec3(legs.getCenter().x, legs.maxY, legs.minZ), Pointing.DOWN, 25)
            .rightClick().withItem(new ItemStack(Items.SHEARS));
        scene.idle(15);
        move(ctx, 8, 15);
        scene.idle(25);
        for (String side : List.of("right", "left")) {
            scene.overlay().showControls(actors.detachedDonorPoint("donor_" + side + "_leg"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
            scene.idle(25);
            scene.overlay().showControls(actors.point(side + "_leg"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
            scene.idle(25);
        }
        move(ctx, 9, 40);
        scene.idle(45);
        scene.addInstruction(s -> actors.setStage(10));
        highlight(ctx, "right_knee", PonderPalette.GREEN, 40);
        highlight(ctx, "left_knee", PonderPalette.GREEN, 40);
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
            var result = actors.result();
            ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
            CompoundTag entity = new CompoundTag();
            result.saveWithoutId(entity);
            entity.putString("id", "create_biotech:slime_bionic");
            // Capture gameplay is server-only. This private preview stack uses the actual current entity snapshot.
            CBItemData.edit(box, tag -> {
                tag.put("CapturedEntity", entity);
                tag.putString("CapturedEntityDescId", result.getType().getDescriptionId());
                tag.putFloat("CapturedEntityHealth", result.getHealth());
            });
            actors.hideResult();
            SurgicalPonderInterfaces.setCapturedBox(scene, box);
        });
    }
}
