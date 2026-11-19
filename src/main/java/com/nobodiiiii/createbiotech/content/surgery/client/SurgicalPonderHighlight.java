package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
    private static final Set<String> PARTS = Set.of("assembled_body", "body", "head", "left_arm", "right_leg", "left_leg",
        "old_arm", "new_arm", "donor_arm", "donor_right_leg", "donor_left_leg", "right_knee", "left_knee",
        "creeper_body", "creeper_head", "phantom_wings");

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
        schedule(scene, spec.color(), spec.duration(), pt -> actors.highlightCorners(spec.parts(), pt), true);
    }

    public static void schedule(SceneBuilder scene, PonderPalette color, int duration,
                                Function<Float, List<List<Vec3>>> cuboids, boolean fadeOut) {
        var outline = new PartOutline(cuboids);
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
        private final Function<Float, List<List<Vec3>>> cuboids;

        private PartOutline(Function<Float, List<List<Vec3>>> cuboids) {
            super(new AABB(Vec3.ZERO, Vec3.ZERO));
            this.cuboids = cuboids;
        }

        @Override
        public void render(PoseStack pose, SuperRenderTypeBuffer buffer, Vec3 camera, float partialTick) {
            for (List<Vec3> corners : cuboids.apply(partialTick))
                renderCuboid(pose, buffer, camera, partialTick, corners);
        }

        private void renderCuboid(PoseStack pose, SuperRenderTypeBuffer buffer, Vec3 camera,
                                  float partialTick, List<Vec3> corners) {
            // The captured corner indices 1, 2 and 4 describe the three edges from corner 0.
            Vec3 origin = corners.getFirst();
            Vector3f x = corners.get(1).subtract(origin).toVector3f();
            Vector3f y = corners.get(2).subtract(origin).toVector3f();
            Vector3f z = corners.get(4).subtract(origin).toVector3f();
            float width = x.length();
            float height = y.length();
            float depth = z.length();
            x.div(width);
            y.div(height);
            Vector3f normal = x.cross(y, new Vector3f());
            if (depth == 0) {
                z.set(normal).normalize();
            } else {
                z.div(depth);
                // A mirrored capture needs a positive frame to keep Ponder's face winding.
                if (normal.dot(z) < 0) {
                    z.negate();
                    depth = -depth;
                }
            }
            Matrix4f axes = new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0,
                z.x, z.y, z.z, 0, 0, 0, 0, 1);
            Matrix4f inverseAxes = new Matrix4f(axes).invert();

            // Ponder subtracts the camera in box space, so transform it relative to the first corner.
            Vector3f localCamera = inverseAxes.transformDirection(camera.subtract(origin).toVector3f());
            // Ponder uses the transformed normals without normalizing them. Keep the scene's zoom out of their lengths.
            Matrix3f faceNormals = new Matrix3f(inverseAxes).transpose();
            Vector3f faceNormal = new Vector3f();
            for (int axis = 0; axis < 3; axis++)
                faceNormals.setColumn(axis, faceNormals.getColumn(axis, faceNormal).normalize());

            setBounds(new AABB(0, 0, 0, width, height, depth));
            pose.pushPose();
            PoseStack.Pose cuboidPose = pose.last();
            cuboidPose.pose().mul(axes);
            cuboidPose.normal().mul(faceNormals);
            super.render(pose, buffer, new Vec3(localCamera.x, localCamera.y, localCamera.z), partialTick);
            pose.popPose();
        }
    }
}
