package com.nobodiiiii.createbiotech.content.surgery.client;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.nobodiiiii.createbiotech.CreateBiotech;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Scene-owned geometry highlight; never uses Ponder's block/box outline instructions. */
public final class SurgicalPonderHighlight extends PonderElementBase implements PonderSceneElement {
    private static final float STROKE_WIDTH_GUI = 2;
    private static ShaderInstance highlightShader;
    // Expand line segments into screen-space quads without the vanilla shader's VIEW_SHRINK.
    // This preserves scene depth and avoids driver limits on native OpenGL line width.
    private static final RenderType DEPTH_TESTED_LINES = RenderType.create("create_biotech_surgical_ponder_highlight",
        DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> highlightShader))
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setLayeringState(RenderStateShard.NO_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false));

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            CreateBiotech.asResource("surgical_ponder_highlight"), DefaultVertexFormat.POSITION_COLOR_NORMAL),
            shader -> highlightShader = shader);
    }

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
        schedule(scene, spec.color(), spec.duration(), pt -> actors.highlightBounds(spec.parts(), pt));
    }

    public static void schedule(SceneBuilder scene, PonderPalette color, int duration,
                                Function<Float, List<AABB>> bounds) {
        var highlight = new SurgicalPonderHighlight(color, duration, bounds);
        scene.addInstruction(playback -> {
            highlight.reset(playback);
            highlight.setVisible(true);
            playback.addElement(highlight);
        });
    }

    private final PonderPalette color;
    private final int duration;
    private final Function<Float, List<AABB>> bounds;
    private int age;

    private SurgicalPonderHighlight(PonderPalette color, int duration, Function<Float, List<AABB>> bounds) {
        this.color = color;
        this.duration = duration;
        this.bounds = bounds;
        setVisible(false);
    }

    @Override public void reset(PonderScene scene) { age = 0; setVisible(false); }
    @Override public void tick(PonderScene scene) { if (++age >= duration) setVisible(false); }
    @Override public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {}
    @Override public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {}

    @Override public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        if (highlightShader == null) return;
        highlightShader.safeGetUniform("StrokeWidth").set(
            (float) (STROKE_WIDTH_GUI * Minecraft.getInstance().getWindow().getGuiScale()));
        int rgb = color.getColor();
        float alpha = Mth.clamp(Math.min((age + pt) / 4, (duration - age - pt) / 4), 0, 1);
        var lines = buffer instanceof SuperRenderTypeBuffer sceneBuffer
            ? sceneBuffer.getLateBuffer(DEPTH_TESTED_LINES) : buffer.getBuffer(DEPTH_TESTED_LINES);
        for (var box : bounds.apply(pt))
            drawBox(graphics.pose().last(), lines, box,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer lines, AABB box,
                               float red, float green, float blue, float alpha) {
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 1; axis <= 4; axis <<= 1) {
                if ((corner & axis) != 0) continue;
                float nx = axis == 1 ? 1 : 0;
                float ny = axis == 2 ? 1 : 0;
                float nz = axis == 4 ? 1 : 0;
                vertex(pose, lines, box, corner, nx, ny, nz, red, green, blue, alpha);
                vertex(pose, lines, box, corner | axis, nx, ny, nz, red, green, blue, alpha);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer lines, AABB box, int corner,
                               float nx, float ny, float nz, float red, float green, float blue, float alpha) {
        lines.addVertex(pose, (float) ((corner & 1) == 0 ? box.minX : box.maxX),
                (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                (float) ((corner & 4) == 0 ? box.minZ : box.maxZ))
            .setColor(red, green, blue, alpha).setNormal(pose, nx, ny, nz);
    }
}
