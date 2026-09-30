import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalJointIconModel;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalJointItemDecorator;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exports production geometry and item transforms without Minecraft, a window or an OpenGL context. */
public class ExportJointIcons {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Map<String, Object> result = new LinkedHashMap<>();
        Matrix4f lighting = new Matrix4f().scaling(1, -1, 1)
            .rotateYXZ(1.0821041f, 3.2375858f, 0)
            .rotateYXZ((float) -Math.PI / 8, (float) Math.PI * 3 / 4, 0);
        result.put("lights", List.of(
            xyz(lighting.transformDirection(new Vector3f(.2f, 1, -.7f).normalize())),
            xyz(lighting.transformDirection(new Vector3f(-.2f, 1, .7f).normalize()))));
        result.put("badge", Map.of("scale", SurgicalJointItemDecorator.BADGE_SCALE,
            "offset", SurgicalJointItemDecorator.BADGE_OFFSET, "depth", SurgicalJointItemDecorator.BADGE_DEPTH));
        List<Object> slots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int x = SurgicalRadialLayout.slotX(i, 5, 1);
            int y = SurgicalRadialLayout.slotY(i, 5, 1);
            slots.add(Map.of("x", x, "y", y, "hit", SurgicalRadialLayout.hoveredSlot(x + 11, y + 11, 5)));
        }
        result.put("wheel", Map.of("slots", slots, "iconOffset", SurgicalRadialLayout.ICON_OFFSET,
            "centerHit", SurgicalRadialLayout.hoveredSlot(0, 0, 5),
            "outsideHit", SurgicalRadialLayout.hoveredSlot(100, 100, 5)));
        for (SurgicalLimbType type : SurgicalLimbType.values()) {
            JsonObject display = display(root, "create_biotech:item/" + type.id() + "_joint");
            JsonObject gui = display.getAsJsonObject("gui");
            PoseStack pose = new PoseStack();
            pose.translate(8, 8, 150);
            // Match GuiGraphics.renderItem, including its position-only Y reflection.
            pose.mulPose(new Matrix4f().scaling(1, -1, 1));
            pose.scale(16, 16, 16);
            ItemTransform transform = new ItemTransform(vector(gui, "rotation", 0),
                vector(gui, "translation", 0).mul(1 / 16f), vector(gui, "scale", 1));
            transform.apply(false, pose);
            pose.translate(-.5f, -.5f, -.5f);
            SurgicalJointIconModel model = SurgicalJointIconModel.of(type);
            model.element(ItemDisplayContext.GUI, 0xf000f0).applyTransform(pose);
            Capture capture = new Capture();
            model.render(pose, capture, 0xf000f0);
            result.put(type.id(), capture.vertices);
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, new Gson().toJson(result));
        try (var stream = ExportJointIcons.class.getClassLoader().getResourceAsStream(
            "assets/minecraft/" + SurgicalJointIconModel.TEXTURE.getPath())) {
            if (stream == null) throw new IllegalStateException("Steve texture missing from runtime resources");
            Files.write(output.resolveSibling("steve.png"), stream.readAllBytes());
        }
        for (String path : List.of("textures/item/chain.png", "textures/font/ascii.png")) {
            try (var stream = ExportJointIcons.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path)) {
                if (stream != null) Files.write(output.resolveSibling(Path.of(path).getFileName()), stream.readAllBytes());
            }
        }
        System.out.println("Exported all five runtime meshes to " + output);
    }

    private static float[] xyz(Vector3f v) { return new float[]{v.x, v.y, v.z}; }

    private static JsonObject display(Path root, String model) throws Exception {
        String[] id = model.split(":");
        Path file = root.resolve("src/main/resources/assets/" + id[0] + "/models/" + id[1] + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject result = json.has("parent") && !json.get("parent").getAsString().equals("builtin/entity")
            ? display(root, json.get("parent").getAsString()) : new JsonObject();
        if (json.has("display")) json.getAsJsonObject("display").entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
        return result;
    }

    private static Vector3f vector(JsonObject json, String key, float fallback) {
        if (!json.has(key)) return new Vector3f(fallback);
        var array = json.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float x, y, z, u, v;
        public VertexConsumer addVertex(float x, float y, float z) { this.x=x; this.y=y; this.z=z; return this; }
        public VertexConsumer setUv(float u, float v) { this.u=u; this.v=v; return this; }
        public VertexConsumer setNormal(float x, float y, float z) {
            vertices.add(new float[]{this.x, this.y, this.z, u, v, x, y, z}); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
    }
}
