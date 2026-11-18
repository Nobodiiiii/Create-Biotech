package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Scene-owned geometry highlight; never uses Ponder's block/box outline instructions. */
public final class SurgicalPonderHighlight extends PonderElementBase implements PonderSceneElement {
    // The vanilla line shader applies VIEW_SHRINK even without VIEW_OFFSET_Z_LAYERING.
    // In Ponder's GUI projection that brings hidden edges several model units forward.
    // Plain color lines keep exactly the same projection and depth as the scene geometry.
    private static final RenderType DEPTH_TESTED_LINES = RenderType.create("create_biotech_surgical_ponder_highlight",
        DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2)))
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setLayeringState(RenderStateShard.NO_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false));

    private static final Set<String> PARTS = Set.of("body", "head", "left_arm", "right_leg", "left_leg",
        "old_arm", "new_arm", "donor_arm", "donor_right_leg", "donor_left_leg", "right_knee", "left_knee");

    public record Spec(List<String> parts, PonderPalette color, int duration) {
        public Spec {
            parts = List.copyOf(parts);
            if (parts.isEmpty() || !PARTS.containsAll(parts) || color == null || duration < 1 || duration > 1200)
                throw new IllegalArgumentException("Highlight requires known parts, a color and duration 1..1200");
        }
    }

    public static final StepCodec<Spec> CODEC = new StepCodec<>() {
        @Override public Spec decode(JsonObject data) {
            var parts = new java.util.ArrayList<String>();
            for (var part : data.getAsJsonArray("parts")) parts.add(part.getAsString());
            var color = PonderPalette.valueOf(data.has("color")
                ? data.get("color").getAsString().toUpperCase(Locale.ROOT) : "BLUE");
            return new Spec(parts, color, data.has("duration") ? data.get("duration").getAsInt() : 60);
        }
        @Override public JsonObject encode(Spec spec) {
            var data = new JsonObject();
            var parts = new JsonArray();
            spec.parts.forEach(parts::add);
            data.add("parts", parts);
            data.addProperty("color", spec.color.name().toLowerCase(Locale.ROOT));
            data.addProperty("duration", spec.duration);
            return data;
        }
    };

    public static void schedule(SceneBuilder scene, SurgicalPonderActors actors, Spec spec) {
        var highlight = new SurgicalPonderHighlight(actors, spec);
        scene.addInstruction(playback -> {
            highlight.reset(playback);
            highlight.setVisible(true);
            playback.addElement(highlight);
        });
    }

    private final SurgicalPonderActors actors;
    private final Spec spec;
    private int age;

    private SurgicalPonderHighlight(SurgicalPonderActors actors, Spec spec) {
        this.actors = actors;
        this.spec = spec;
        setVisible(false);
    }

    @Override public void reset(PonderScene scene) { age = 0; setVisible(false); }
    @Override public void tick(PonderScene scene) { if (++age >= spec.duration) setVisible(false); }
    @Override public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {}
    @Override public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {}

    @Override public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        int rgb = spec.color.getColor();
        float alpha = Mth.clamp(Math.min((age + pt) / 4, (spec.duration - age - pt) / 4), 0, 1);
        var lines = buffer instanceof SuperRenderTypeBuffer sceneBuffer
            ? sceneBuffer.getLateBuffer(DEPTH_TESTED_LINES) : buffer.getBuffer(DEPTH_TESTED_LINES);
        for (var box : actors.highlightBounds(spec.parts, pt))
            drawBox(graphics.pose().last(), lines, box,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer lines, AABB box,
                               float red, float green, float blue, float alpha) {
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 1; axis <= 4; axis <<= 1) {
                if ((corner & axis) != 0) continue;
                vertex(pose, lines, box, corner, red, green, blue, alpha);
                vertex(pose, lines, box, corner | axis, red, green, blue, alpha);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer lines, AABB box, int corner,
                               float red, float green, float blue, float alpha) {
        lines.addVertex(pose, (float) ((corner & 1) == 0 ? box.minX : box.maxX),
                (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                (float) ((corner & 4) == 0 ? box.minZ : box.maxZ))
            .setColor(red, green, blue, alpha);
    }
}
