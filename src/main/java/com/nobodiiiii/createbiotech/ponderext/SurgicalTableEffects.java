package com.nobodiiiii.createbiotech.ponderext;

import java.util.List;
import java.util.function.Consumer;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.simibubi.create.foundation.gui.AllGuiTextures;

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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Compiled model effects; all narration and timing remain editable JavaScene data. */
final class SurgicalTableEffects {
    private static final String PREFIX = "create_biotech:java/surgical_table/";
    private static final String ACTORS = "create_biotech:surgical_table.actors";

    private SurgicalTableEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava(PREFIX + "prepare", "准备手术演示模型", ctx -> actors(ctx));
        register(engine, "empty", "清空演示台面", actors -> actors.setStage(0));
        register(engine, "place_zombie", "放置僵尸供体", actors -> actors.setStage(1));
        register(engine, "place_golem", "放置铁傀儡供体", actors -> actors.setStage(2));
        register(engine, "original_appearance", "显示供体原本外观", actors -> actors.setSourceAppearance(true));
        register(engine, "slime_appearance", "恢复手术外观", actors -> actors.setSourceAppearance(false));
        register(engine, "hide_result", "收起演示成品", actors -> actors.showResult(false));
        register(engine, "return_to_table", "将成品放回台面", actors -> { actors.showResult(false); actors.unpackBody(); actors.setStage(7); });
        engine.steps().registerJava(PREFIX + "cut_arm", "剪开僵尸肩部连接", ctx -> move(ctx, 3, 22));
        engine.steps().registerJava(PREFIX + "move_arm", "移开旧手臂", ctx -> move(ctx, 4, 30));
        engine.steps().registerJava(PREFIX + "cut_donor", "剪下铁傀儡手臂", ctx -> {
            highlight(ctx, "donor_arm", PonderPalette.RED, 35);
            ctx.builder().overlay().showControls(actors(ctx).point("donor_arm"), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(Items.SHEARS));
            ctx.builder().addInstruction(s -> actors(ctx).setStage(5));
        });
        engine.steps().registerJava(PREFIX + "glue_arm", "按选点顺序粘合替换手臂", SurgicalTableEffects::glueArm);
        engine.steps().registerJava(PREFIX + "assembled", "恢复已替换手臂的台面", ctx ->
            ctx.builder().addInstruction(s -> actors(ctx).setStage(7)));
        engine.steps().registerJava(PREFIX + "tool_wheel", "演示手术盒工具轮盘", ctx -> wheel(ctx, false, 90));
        engine.steps().registerJava(PREFIX + "joint_wheel", "演示五种关节轮盘", ctx -> wheel(ctx, true, 170));
        engine.steps().registerJava(PREFIX + "shoulder", "安装替换手臂的肩关节", ctx -> joint(ctx, "new_arm", SurgicalLimbType.SHOULDER, 1));
        engine.steps().registerJava(PREFIX + "other_joints", "安装其余肩颈髋关节", ctx -> {
            joint(ctx, "left_arm", SurgicalLimbType.SHOULDER, 2);
            joint(ctx, "head", SurgicalLimbType.NECK, 3);
            joint(ctx, "right_leg", SurgicalLimbType.HIP, 4);
            joint(ctx, "left_leg", SurgicalLimbType.HIP, 5);
        });
        engine.steps().registerJava(PREFIX + "highlight_body", "标示躯干与各个体块", ctx -> {
            for (String part : List.of("body", "head", "left_arm", "right_leg", "left_leg"))
                highlight(ctx, part, PonderPalette.BLUE, 75);
        });
        engine.steps().registerJava(PREFIX + "highlight_joint", "标示关节两侧的独立部件", ctx -> {
            highlight(ctx, "new_arm", PonderPalette.GREEN, 110);
            highlight(ctx, "body", PonderPalette.BLUE, 110);
            ctx.builder().overlay().showLine(PonderPalette.GREEN, actors(ctx).point("new_arm"), actors(ctx).point("body"), 110);
        });
        engine.steps().registerJava(PREFIX + "release_hopping", "释放无关节成品并演示跳跃", ctx -> release(ctx, false));
        engine.steps().registerJava(PREFIX + "release_walking", "释放成品并演示转头行走挥击", ctx -> release(ctx, true));
        engine.steps().registerJava(PREFIX + "result_intro", "展示装配完成的仿生生物", ctx -> {
            ctx.builder().addInstruction(s -> { actors(ctx).setJoints(5); actors(ctx).showResult(true); });
        });
        engine.steps().registerJava(PREFIX + "stats", "展示生命伤害移速与成品属性", SurgicalTableEffects::stats);
        engine.steps().registerJava(PREFIX + "tools_note", "右上角工具章节提示", ctx -> {
            TimedOverlay overlay = new TimedOverlay(110, false, true);
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
        ctx.builder().overlay().chaseBoundingBoxOutline(color, part, actors(ctx).box(part).inflate(0.025), duration);
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
        highlight(ctx, part, PonderPalette.GREEN, 60);
        scene.overlay().showControls(actors(ctx).point(part), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.idle(25);
        scene.overlay().showControls(actors(ctx).point("body"), Pointing.DOWN, 25)
            .rightClick().withItem(SurgicalJointItem.createStack(type));
        scene.overlay().showLine(PonderPalette.GREEN, actors(ctx).point(part), actors(ctx).point("body"), 30);
        scene.addInstruction(s -> actors(ctx).setJoints(count));
        scene.idle(30);
    }

    private static void release(StepBuildContext ctx, boolean articulated) {
        var scene = ctx.builder();
        scene.overlay().showControls(actors(ctx).point("body"), Pointing.DOWN, 25)
            .rightClick().withItem(new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get()));
        scene.idle(25);
        scene.addInstruction(s -> { actors(ctx).packBody(); actors(ctx).setJoints(articulated ? 5 : 0); });
        scene.overlay().showControls(SurgicalPonderActors.RESULT_POSITION.add(0, 1.5, 0), Pointing.DOWN, 30)
            .rightClick().whileSneaking().withItem(new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get()));
        scene.idle(20);
        scene.addInstruction(s -> actors(ctx).showResult(true));
    }

    private static void wheel(StepBuildContext ctx, boolean joints, int duration) {
        TimedOverlay overlay = new TimedOverlay(duration, joints, false);
        showOverlay(ctx, overlay);
    }

    private static void showOverlay(StepBuildContext ctx, TimedOverlay overlay) {
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
        TimedOverlay overlay = new TimedOverlay(120, false, false);
        overlay.stats = box;
        overlay.statsActors = actors(ctx);
        showOverlay(ctx, overlay);
    }

    /** A non-interactive view of the real wheel; never opens a screen or sends selection packets. */
    private static final class TimedOverlay extends PonderElementBase implements PonderOverlayElement {
        private final int duration;
        private final boolean joints;
        private final boolean note;
        private int age;
        private boolean active;
        private ItemStack stats;
        private SurgicalPonderActors statsActors;

        TimedOverlay(int duration, boolean joints, boolean note) {
            this.duration = duration; this.joints = joints; this.note = note;
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
            if (note) {
                var text = Component.literal(LocalizedText.ref("create_biotech:surgical_table", "recombining.more_tools").resolve());
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, 150);
                int x = screen.width - 164;
                graphics.fill(x - 5, 44, screen.width - 9, 54 + lines.size() * 10, 0xdd202830);
                for (int i = 0; i < lines.size(); i++) graphics.drawString(font, lines.get(i), x, 50 + i * 10, 0xffeeeeee, false);
                return;
            }
            if (stats != null) {
                int x = screen.width / 2 - 90, y = screen.height / 2 - 15;
                graphics.renderItem(stats, x, y);
                var entity = statsActors.result();
                var assembly = entity.getAssembly();
                double damage = SurgicalCombatCalibration.nominalDamagePerSecond(entity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                    assembly.attackGeometry(), entity.level());
                List<Component> lines = List.of(Component.translatable("create_biotech.tooltip.base_stats"),
                    Component.literal("Alt").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable(Attributes.MAX_HEALTH.value().getDescriptionId()).append(": " + format(entity.getMaxHealth())),
                    Component.translatable(Attributes.ATTACK_DAMAGE.value().getDescriptionId()).append(": " + format(damage) + " DPS"),
                    Component.translatable(Attributes.MOVEMENT_SPEED.value().getDescriptionId()).append(": " + format(SurgicalGait.movementSpeed(assembly.bodyBounds(), entity.level()))));
                graphics.renderComponentTooltip(font, lines, x + 22, y + 18);
                return;
            }
            List<ItemStack> options = joints ? SurgicalJointItem.TYPES.stream().map(SurgicalJointItem::previewStack).toList()
                : SurgicalKitItem.availableTools(new ItemStack(CBItems.SURGICAL_KIT.get())).stream().map(SurgicalKitItem.Tool::displayStack).toList();
            int selected = joints ? Math.min(options.size() - 1, age / 32) : 0;
            graphics.pose().pushPose();
            graphics.pose().translate(screen.width / 2.0, screen.height / 2.0 - 4, 300);
            double fade = Math.min(1, (age + pt) / 10.0);
            for (int slot = 0; slot < options.size(); slot++) {
                int x = SurgicalRadialLayout.slotX(slot, options.size(), fade);
                int y = SurgicalRadialLayout.slotY(slot, options.size(), fade);
                AllGuiTextures.TOOLBELT_SLOT.render(graphics, x, y);
                graphics.renderItem(options.get(slot), x + 3, y + 3);
                if (slot == selected) AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, x - 1, y - 1);
            }
            AllGuiTextures.TOOLBELT_SLOT.render(graphics, -11, -11);
            graphics.renderItem(new ItemStack(joints ? CBItems.SURGICAL_JOINT.get() : CBItems.SURGICAL_KIT.get()), -8, -8);
            Component name = joints ? SurgicalJointItem.typeName(SurgicalJointItem.TYPES.get(selected)) : options.get(selected).getHoverName();
            graphics.drawString(font, name, -font.width(name) / 2, 72, 0xffffd080, false);
            graphics.pose().popPose();
        }
        private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    }
}
