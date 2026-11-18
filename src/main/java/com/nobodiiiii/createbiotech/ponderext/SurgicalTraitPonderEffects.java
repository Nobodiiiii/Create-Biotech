package com.nobodiiiii.createbiotech.ponderext;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Assembly.COMPLETE;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Part.*;
import static net.createmod.ponder.api.PonderPalette.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ObjIntConsumer;

import com.nobodiiiii.createbiotech.compat.jei.CreateBiotechJeiPlugin;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderHighlight;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Assembly;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Part;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.View;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/** Playback instructions for the fourth surgical table chapter. */
final class SurgicalTraitPonderEffects {
    private static final String PREFIX = "create_biotech:java/surgical_table/traits_";
    private static final String ACTORS = "create_biotech:surgical_table.trait_actors";

    private SurgicalTraitPonderEffects() {}

    static void install(RuntimeEngine engine) {
        view(engine, "flight", "展示仿生生物扑翼飞行", View.FLIGHT, COMPLETE);
        view(engine, "dive", "展示仿生生物接敌自爆", View.DIVE, COMPLETE);
        view(engine, "hide", "收起演示成品", View.HIDDEN, COMPLETE);
        view(engine, "display", "定格组装完成的成品", View.DISPLAY, COMPLETE);
        view(engine, "donors", "展示苦力怕和幻翼组织", View.DONORS, COMPLETE);
        view(engine, "table", "展示安装关节后的各部位", View.TABLE, COMPLETE);
        view(engine, "extra_torso", "增加其他供体的躯干组织", View.TABLE, Assembly.EXTRA_TORSO);
        view(engine, "one_shoulder", "拆下一个肩关节", View.TABLE, Assembly.ONE_SHOULDER);
        view(engine, "pack_missing", "装箱检查未生效的成品", View.PACKED, Assembly.ONE_SHOULDER);
        view(engine, "pack_complete", "再次装箱查看已生效的特性", View.PACKED, COMPLETE);
        engine.steps().registerJava(PREFIX + "labels", "标出自爆和扑翼飞行", ctx ->
            labels(ctx, 90, blastLabel(), flightLabel(LEFT_WING)));
        engine.steps().registerJava(PREFIX + "donor_labels", "对照供体和特性载体", ctx ->
            labels(ctx, 125,
                new Label(CREEPER, EntityType.CREEPER.getDescription(), GREEN),
                new Label(PHANTOM_BODY, EntityType.PHANTOM.getDescription(), BLUE),
                flightLabel(RIGHT_WING), new Label(LEFT_WING, null, INPUT)));
        engine.steps().registerJava(PREFIX + "scope_labels", "标出全身、躯干、头、翼臂和腿", ctx ->
            labels(ctx, 235, new Label(ALL, scope(BionicTraitScope.WHOLE_BODY), WHITE),
                blastLabel(), new Label(HEAD, scope(BionicTraitScope.HEAD), OUTPUT),
                flightLabel(LEFT_WING), new Label(RIGHT_WING, null, INPUT),
                new Label(LEGS, scope(BionicTraitScope.LEG), BLUE)));
        engine.steps().registerJava(PREFIX + "coverage", "展示组织比例与自爆门槛", ctx -> {
            var actors = actors(ctx);
            highlight(ctx, TORSO, GREEN, 310);
            highlight(ctx, EXTRA_TORSO, BLUE, 310);
            overlay(ctx, new Coverage(310, actors));
        });
        engine.steps().registerJava(PREFIX + "mind", "定位颈关节头部的性情和智力", ctx -> {
            highlight(ctx, HEAD, OUTPUT, 120);
            overlay(ctx, new Properties(120, actors(ctx), true));
        });
        engine.steps().registerJava(PREFIX + "properties", "显示台面特性与缺失条件", ctx ->
            overlay(ctx, new Properties(275, actors(ctx), false)));
        engine.steps().registerJava(PREFIX + "jei", "预览 JEI 特性条目", ctx -> {
            if (ModList.get().isLoaded("jei"))
                overlay(ctx, new TraitRecipe(145));
        });
    }

    private static void view(RuntimeEngine engine, String id, String label, View view, Assembly assembly) {
        engine.steps().registerJava(PREFIX + id, label, ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(scene -> actors.show(view, assembly));
        });
    }

    private static SurgicalTraitPonderActors actors(StepBuildContext ctx) {
        return (SurgicalTraitPonderActors) ctx.state().computeIfAbsent(ACTORS, key -> {
            var actors = new SurgicalTraitPonderActors(ctx.builder().getScene().getWorld());
            ctx.builder().addInstruction(scene -> {
                actors.reset(scene);
                scene.addElement(actors);
                var rotation = scene.getTransform().yRotation;
                rotation.startWithValue(rotation.getValue() + 180);
            });
            return actors;
        });
    }

    private static void highlight(StepBuildContext ctx, Part part, PonderPalette color, int duration) {
        var actors = actors(ctx);
        SurgicalPonderHighlight.schedule(ctx.builder(), color, duration, pt -> actors.highlight(part, pt));
    }

    private static Component scope(BionicTraitScope scope) { return Component.translatable(scope.descriptionId()); }
    private static Label blastLabel() { return new Label(TORSO, BionicTrait.SELF_DESTRUCT.displayName(), GREEN); }
    private static Label flightLabel(Part wing) { return new Label(wing, BionicTrait.WING_FLIGHT.displayName(), INPUT); }

    private static void labels(StepBuildContext ctx, int duration, Label... labels) {
        for (Label label : labels) highlight(ctx, label.part(), label.color(), duration);
        overlay(ctx, new Labels(duration, actors(ctx), List.of(labels)));
    }

    private static void overlay(StepBuildContext ctx, TimedOverlay overlay) {
        ctx.builder().addInstruction(scene -> {
            overlay.reset(scene);
            scene.addElement(overlay);
        });
    }

    private abstract static class TimedOverlay extends PonderElementBase implements PonderOverlayElement {
        private final int duration;
        private int age;

        TimedOverlay(int duration) { this.duration = duration; }
        @Override public void reset(PonderScene scene) { age = 0; setVisible(true); }
        @Override public void tick(PonderScene scene) { if (++age >= duration) setVisible(false); }
        int age() { return age; }
    }

    private record Label(Part part, Component text, PonderPalette color) {}

    private static final class Labels extends TimedOverlay {
        private final SurgicalTraitPonderActors actors;
        private final List<Label> labels;

        Labels(int duration, SurgicalTraitPonderActors actors, List<Label> labels) {
            super(duration);
            this.actors = actors;
            this.labels = labels;
        }

        @Override public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt) {
            var font = screen.getFontRenderer();
            int y = screen.height / 2 - labels.size() * 8;
            for (Label label : labels) {
                if (label.text() == null) continue;
                List<AABB> boxes = actors.highlight(label.part(), pt);
                if (boxes.isEmpty()) continue;
                AABB bounds = boxes.stream().reduce(AABB::minmax).orElseThrow();
                var target = scene.getTransform().sceneToScreen(bounds.getCenter(), pt);
                int color = label.color().getColor() | 0xff000000;
                int edge = 20 + font.width(label.text());
                int targetX = (int) target.x;
                int targetY = (int) target.y;
                graphics.hLine(edge, targetX, y + 6, color);
                graphics.vLine(targetX, y + 6, targetY, color);
                graphics.fill(8, y - 1, edge, y + 13, 0xee23262c);
                graphics.fill(8, y - 1, 10, y + 13, color);
                graphics.drawString(font, label.text(), 13, y + 2, 0xffeeeeee, false);
                y += 17;
            }
        }
    }

    private static final class Coverage extends TimedOverlay {
        private final SurgicalTraitPonderActors actors;

        Coverage(int duration, SurgicalTraitPonderActors actors) { super(duration); this.actors = actors; }

        @Override public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt) {
            var font = screen.getFontRenderer();
            var result = actors.traits().result(BionicTrait.SELF_DESTRUCT);
            int x = 12;
            int y = screen.height / 2 - 38;
            int width = Math.min(112, screen.width / 3);
            graphics.fill(x - 4, y - 5, x + width + 4, y + 81, 0xee23262c);
            int color = result.active() ? 0xff8cba51 : 0xffaaaaaa;
            graphics.drawString(font, BionicTrait.SELF_DESTRUCT.displayName(), x, y, color, false);
            String percent = Math.round(result.coverage() * 100) + "%";
            graphics.drawString(font, percent, x + width - font.width(percent), y, color, false);
            bar(graphics, x, y + 12, width, result.coverage(), color);
            String[] rules = { "presence", "threshold", "scaled" };
            for (int index = 0; index < rules.length; index++) {
                int row = y + 25 + index * 16;
                graphics.drawString(font, Component.translatable("create_biotech.jei.trait.rule." + rules[index]),
                    x, row, 0xffeeeeee, false);
                double example = switch (index) { case 0 -> 1; case 1 -> result.active() ? 1 : 0; default -> result.coverage(); };
                int chartWidth = width / 3;
                bar(graphics, x + width - chartWidth, row + 2, chartWidth, example, color);
            }
        }

        private static void bar(GuiGraphics graphics, int x, int y, int width, double filled, int color) {
            graphics.fill(x, y, x + width, y + 5, 0xff454956);
            int pixels = (int) Math.round(width * filled);
            if (pixels > 0) graphics.fill(x, y, x + pixels, y + 5, color);
        }
    }

    private static final class Properties extends TimedOverlay {
        private final SurgicalTraitPonderActors actors;
        private final boolean headOnly;
        private final ItemStack emptyBox = new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get());
        private ItemStack filledBox = ItemStack.EMPTY;

        Properties(int duration, SurgicalTraitPonderActors actors, boolean headOnly) {
            super(duration);
            this.actors = actors;
            this.headOnly = headOnly;
        }

        @Override public void reset(PonderScene scene) {
            super.reset(scene);
            filledBox = ItemStack.EMPTY;
        }

        @Override public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt) {
            var font = screen.getFontRenderer();
            List<Component> details = new ArrayList<>();
            if (headOnly) {
                var mind = actors.mind();
                details.add(classification(BionicTrait.DISPOSITION, mind.disposition()));
                details.add(classification(BionicTrait.INTELLIGENCE, mind.intelligence()));
            } else {
                CapturedEntityBoxStatsTooltip.appendPropertiesSection(details, actors.traits());
            }
            int width = Math.min(180, screen.width / 2 - 16);
            List<FormattedCharSequence> lines = details.stream().flatMap(line -> font.split(line, width).stream()).toList();
            if (actors.packed() && filledBox.isEmpty()) {
                filledBox = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
                CBItemData.edit(filledBox, tag -> tag.put("CapturedEntity", actors.boxedEntity()));
            } else if (!actors.packed()) {
                filledBox = ItemStack.EMPTY;
            }
            int height = 25 + lines.size() * 10;
            float scale = Math.min(1, (screen.height - 50f) / height);
            graphics.pose().pushPose();
            graphics.pose().translate(8, Math.max(23, (screen.height - height * scale) / 2), 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.fill(0, 0, width + 12, height, 0xf023262c);
            graphics.renderItem(filledBox.isEmpty() ? emptyBox : filledBox, 5, 4);
            graphics.drawString(font, Component.literal("Alt"), 25, 8, 0xffeeeeee, false);
            for (int i = 0; i < lines.size(); i++)
                graphics.drawString(font, lines.get(i), 5, 22 + i * 10, 0xffeeeeee, false);
            graphics.pose().popPose();
        }

        private static Component classification(BionicTrait trait, Enum<?> value) {
            return Component.translatable(trait.descriptionId(),
                Component.translatable("create_biotech.trait.variant." + value.name().toLowerCase(Locale.ROOT)));
        }
    }

    private static final class TraitRecipe extends TimedOverlay {
        private ObjIntConsumer<GuiGraphics> draw;

        TraitRecipe(int duration) { super(duration); }

        @Override public void reset(PonderScene scene) {
            super.reset(scene);
            draw = CreateBiotechJeiPlugin.traitRecipePreview(BionicTrait.WING_FLIGHT);
        }

        @Override public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt) {
            if (draw == null) return;
            float scale = Math.min(1, Math.min((screen.width / 2f - 18) / 184, (screen.height - 50f) / 143));
            graphics.pose().pushPose();
            graphics.pose().translate(8, (screen.height - 143 * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.fill(0, 0, 184, 143, 0xee23262c);
            draw.accept(graphics, age());
            graphics.pose().popPose();
        }
    }
}
