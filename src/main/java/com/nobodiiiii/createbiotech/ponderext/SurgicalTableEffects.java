package com.nobodiiiii.createbiotech.ponderext;

import java.util.List;
import java.util.function.Consumer;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.runtime.api.StepType;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.nododiiiii.ponderer.scene.LocalizedText;
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
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
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
        engine.steps().registerJava(PREFIX + "prepare", "准备手术演示模型", ctx -> actors(ctx));
        register(engine, "empty", "清空演示台面", actors -> actors.setStage(0));
        register(engine, "place_zombie", "放置僵尸供体", actors -> actors.setStage(1));
        register(engine, "place_golem", "放置铁傀儡供体", actors -> actors.setStage(2));
        register(engine, "place_pillager", "放置掠夺者供体", SurgicalPonderActors::placePillager);
        register(engine, "original_appearance", "显示供体原本外观", actors -> actors.setSourceAppearance(true));
        register(engine, "slime_appearance", "恢复手术外观", actors -> actors.setSourceAppearance(false));
        register(engine, "hide_result", "收起演示成品", actors -> actors.showResult(false));
        register(engine, "return_to_table", "将成品放回台面", actors -> { actors.showResult(false); actors.unpackBody(); actors.setStage(10); });
        engine.steps().registerJava(PREFIX + "cut_arm", "剪开僵尸肩部连接", ctx -> move(ctx, 3, 22));
        engine.steps().registerJava(PREFIX + "move_arm", "移开旧手臂", ctx -> move(ctx, 4, 30));
        engine.steps().registerJava(PREFIX + "cut_donor", "剪下铁傀儡手臂", ctx -> {
            highlight(ctx, "donor_arm", PonderPalette.RED, 35);
            ctx.builder().overlay().showControls(actors(ctx).point("donor_arm"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(Items.SHEARS));
            ctx.builder().addInstruction(s -> actors(ctx).setStage(5));
        });
        engine.steps().registerJava(PREFIX + "glue_arm", "按选点顺序粘合替换手臂", SurgicalTableEffects::glueArm);
        engine.steps().registerJava(PREFIX + "glue_legs", "截取掠夺者双腿并安装小腿", SurgicalTableEffects::glueLegs);
        engine.steps().registerJava(PREFIX + "assembled", "恢复已替换手臂的台面", ctx ->
            ctx.builder().addInstruction(s -> { actors(ctx).placePillager(); actors(ctx).setStage(10); }));
        engine.steps().registerJava(PREFIX + "tool_wheel", "演示手术盒工具轮盘", ctx -> wheel(ctx, false, 90));
        engine.steps().registerJava(PREFIX + "joint_wheel", "演示五种关节轮盘", ctx -> wheel(ctx, true, 170));
        engine.steps().registerJava(PREFIX + "shoulder", "安装替换手臂的肩关节", ctx -> joint(ctx, "new_arm", SurgicalLimbType.SHOULDER, 1));
        engine.steps().registerJava(PREFIX + "other_joints", "安装其余肩颈髋膝关节", ctx -> {
            joint(ctx, "left_arm", SurgicalLimbType.SHOULDER, 2);
            joint(ctx, "head", SurgicalLimbType.NECK, 3);
            joint(ctx, "right_leg", SurgicalLimbType.HIP, 4);
            joint(ctx, "left_leg", SurgicalLimbType.HIP, 5);
            joint(ctx, "right_knee", SurgicalLimbType.KNEE, 6);
            joint(ctx, "left_knee", SurgicalLimbType.KNEE, 7);
        });
        engine.steps().registerJava(PREFIX + "release_hopping", "释放无关节成品并演示跳跃", ctx -> release(ctx, false));
        engine.steps().registerJava(PREFIX + "release_walking", "释放成品并演示转头行走挥击", ctx -> release(ctx, true));
        engine.steps().registerJava(PREFIX + "result_intro", "展示装配完成的仿生生物", ctx -> {
            ctx.builder().addInstruction(s -> { actors(ctx).setJoints(7); actors(ctx).showResult(true); });
        });
        engine.steps().registerJava(PREFIX + "stats", "展示生命伤害移速与成品属性", SurgicalTableEffects::stats);
        engine.steps().registerJava(PREFIX + "tools_note", "右上角工具章节提示", ctx -> {
            ChapterNote overlay = new ChapterNote(110);
            showOverlay(ctx, overlay);
        });
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
            @Override protected void firstTick(PonderScene scene) { actors.setStage(stage); actors.setMovement(0); }
            @Override public void tick(PonderScene scene) {
                super.tick(scene);
                float p = 1 - remainingTicks / (float) totalTicks;
                actors.setMovement(p * p * (3 - 2 * p));
            }
        });
    }

    private static void highlight(StepBuildContext ctx, String part, PonderPalette color, int duration) {
        SurgicalPonderHighlight.schedule(ctx.builder(), actors(ctx),
            new SurgicalPonderHighlight.Spec(List.of(part), color, duration));
    }

    private static void glueArm(StepBuildContext ctx) {
        var scene = ctx.builder();
        scene.overlay().showControls(actors(ctx).point("donor_arm"), Pointing.DOWN, 35)
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

    private static void joint(StepBuildContext ctx, String part, SurgicalLimbType type, int count) {
        var scene = ctx.builder();
        String parent = part.equals("right_knee") ? "right_leg" : part.equals("left_knee") ? "left_leg" : "body";
        highlight(ctx, part, PonderPalette.GREEN, 60);
        scene.overlay().showControls(actors(ctx).point(part), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.idle(25);
        scene.overlay().showControls(actors(ctx).point(parent), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.addInstruction(s -> actors(ctx).setJoints(count));
        scene.idle(30);
    }

    private static void glueLegs(StepBuildContext ctx) {
        var scene = ctx.builder();
        for (String leg : List.of("donor_right_leg", "donor_left_leg")) {
            highlight(ctx, leg, PonderPalette.RED, 30);
            scene.overlay().showControls(actors(ctx).point(leg), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(Items.SHEARS));
            scene.idle(30);
        }
        scene.addInstruction(s -> actors(ctx).setStage(8));
        for (String side : List.of("right", "left")) {
            scene.overlay().showControls(actors(ctx).point("donor_" + side + "_leg"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
            scene.idle(25);
            scene.overlay().showControls(actors(ctx).point(side + "_leg"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.SUPER_GLUE.get()));
            scene.idle(25);
        }
        move(ctx, 9, 40);
        scene.idle(45);
        scene.addInstruction(s -> actors(ctx).setStage(10));
        highlight(ctx, "right_knee", PonderPalette.GREEN, 40);
        highlight(ctx, "left_knee", PonderPalette.GREEN, 40);
    }

    private static void release(StepBuildContext ctx, boolean articulated) {
        var scene = ctx.builder();
        scene.overlay().showControls(actors(ctx).point("body"), Pointing.DOWN, 25)
            .rightClick().withItem(new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get()));
        scene.idle(25);
        scene.addInstruction(s -> { actors(ctx).packBody(); actors(ctx).setJoints(articulated ? 7 : 0); });
        scene.overlay().showControls(SurgicalPonderActors.RESULT_POSITION.add(0, 1.5, 0), Pointing.DOWN, 30)
            .rightClick().whileSneaking().withItem(new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get()));
        scene.idle(20);
        scene.addInstruction(s -> actors(ctx).showResult(true));
    }

    private static void wheel(StepBuildContext ctx, boolean joints, int duration) {
        SurgicalPonderInterfaces.wheel(ctx.builder(), joints, duration);
    }

    private static void showOverlay(StepBuildContext ctx, ChapterNote overlay) {
        ctx.builder().addInstruction(scene -> {
            scene.addElement(overlay);
            overlay.activate();
        });
    }

    private static void stats(StepBuildContext ctx) {
        ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
        var scene = ctx.builder();
        scene.addInstruction(s -> {
            var result = actors(ctx).result();
            CompoundTag entity = new CompoundTag();
            result.saveWithoutId(entity);
            entity.putString("id", "create_biotech:slime_bionic");
            // Capture gameplay is server-only. This private preview stack uses the actual current entity snapshot.
            CBItemData.edit(box, tag -> {
                tag.put("CapturedEntity", entity);
                tag.putString("CapturedEntityDescId", result.getType().getDescriptionId());
                tag.putFloat("CapturedEntityHealth", result.getHealth());
            });
            actors(ctx).showResult(false);
        });
        SurgicalPonderInterfaces.stats(scene, box, 120);
    }

    /** The author's original fixed chapter note. */
    private static final class ChapterNote extends PonderElementBase implements PonderOverlayElement {
        private final int duration;
        private int age;
        private boolean active;

        ChapterNote(int duration) {
            this.duration = duration;
            setVisible(false);
        }
        void activate() { age = 0; active = true; setVisible(true); }
        @Override public void reset(PonderScene scene) { active = false; age = 0; setVisible(false); }
        @Override public void tick(PonderScene scene) {
            if (active && ++age >= duration) { active = false; setVisible(false); }
        }
        @Override public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt) {
            if (!active) return;
            var font = screen.getFontRenderer();
            var text = Component.literal(LocalizedText.ref("create_biotech:surgical_table", "recombining.more_tools").resolve());
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, 150);
            int x = screen.width - 164;
            graphics.fill(x - 5, 44, screen.width - 9, 54 + lines.size() * 10, 0xdd202830);
            for (int i = 0; i < lines.size(); i++) graphics.drawString(font, lines.get(i), x, 50 + i * 10, 0xffeeeeee, false);
        }
    }
}
