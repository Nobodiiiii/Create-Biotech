package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.runtime.api.StepCodec;

import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SurgicalPonderHighlight {
    private static final Set<String> PARTS = Set.of("body", "head", "left_arm", "right_leg", "left_leg",
        "old_arm", "new_arm", "donor_arm", "donor_right_leg", "donor_left_leg", "right_knee", "left_knee");

    private SurgicalPonderHighlight() {}

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
        schedule(scene, spec.color(), spec.duration(), pt -> actors.highlightBounds(spec.parts(), pt), true);
    }

    public static void schedule(SceneBuilder scene, PonderPalette color, int duration,
                                Function<Float, List<AABB>> bounds, boolean fadeOut) {
        var outline = new PartOutline(bounds);
        scene.addInstruction(new TickingInstruction(false, duration) {
            @Override
            protected void firstTick(PonderScene playback) {
                playback.getOutliner().showOutline(this, outline)
                    .lineWidth(1 / 16f)
                    .colored(color.getColor());
            }

            @Override
            public void tick(PonderScene playback) {
                super.tick(playback);
                if (isComplete() && !fadeOut)
                    playback.getOutliner().remove(this);
                else
                    playback.getOutliner().keep(this);
            }

            @Override
            public void reset(PonderScene playback) {
                super.reset(playback);
                playback.getOutliner().remove(this);
            }
        });
    }

    private static final class PartOutline extends AABBOutline {
        private final Function<Float, List<AABB>> bounds;

        private PartOutline(Function<Float, List<AABB>> bounds) {
            super(new AABB(Vec3.ZERO, Vec3.ZERO));
            this.bounds = bounds;
        }

        @Override
        public void render(PoseStack pose, SuperRenderTypeBuffer buffer, Vec3 camera, float partialTick) {
            for (AABB box : bounds.apply(partialTick)) {
                setBounds(box);
                super.render(pose, buffer, camera, partialTick);
            }
        }
    }
}
