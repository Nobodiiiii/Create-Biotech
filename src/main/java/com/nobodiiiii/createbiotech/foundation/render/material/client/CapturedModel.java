package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.client.MaterialTextures.SourceSprite;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvFragment;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvQuad;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvVertex;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.IntSize;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;

/** Captures immutable local cube geometry without modifying a vanilla ModelPart or its current pose. */
public final class CapturedModel {
    private final Node root;
    private final List<UvQuad> normalizedQuads;

    private CapturedModel(Node root, List<UvQuad> quads) {
        this.root = root;
        normalizedQuads = List.copyOf(quads);
    }

    public static CapturedModel capture(ModelPart model) {
        Objects.requireNonNull(model);
        Node root = new Node(model);
        List<UvQuad> quads = new ArrayList<>();
        var identity = new PoseStack().last();
        model.visit(new PoseStack(), (ignoredPose, path, cubeIndex, cube) -> {
            Node node = root;
            for (String child : path.split("/")) {
                if (!child.isEmpty()) {
                    ModelPart liveChild = node.part.getChild(child);
                    node = node.children.computeIfAbsent(child, ignored -> new Node(liveChild));
                }
            }
            int first = quads.size();
            Collector collector = new Collector(quads);
            cube.compile(identity, collector, 0, 0, -1);
            collector.finish();
            for (int index = first; index < quads.size(); index++) node.faces.add(index);
        });
        return new CapturedModel(root, quads);
    }

    public List<UvQuad> quads(IntSize targetSize) {
        return normalizedQuads.stream().map(quad -> new UvQuad(quad.vertices().stream()
                .map(v -> new UvVertex(v.x(), v.y(), v.z(), v.u() * targetSize.width(), v.v() * targetSize.height())).toList(),
                quad.nx(), quad.ny(), quad.nz())).toList();
    }

    public Bound bind(UvPlan plan, Map<String, SourceSprite> sources) {
        if (!plan.eligible() || plan.faces().size() != normalizedQuads.size()) {
            throw new IllegalArgumentException("UV plan does not match captured model");
        }
        List<ResourceLocation> textures = sources.values().stream().map(SourceSprite::texture).distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        return new Bound(bindNode(root, plan, sources, textures), textures);
    }

    private static Bound.Node bindNode(Node node, UvPlan plan, Map<String, SourceSprite> sources,
                                             List<ResourceLocation> textures) {
        List<Bound.Quad> quads = new ArrayList<>();
        for (int face : node.faces) {
            for (UvFragment fragment : plan.faces().get(face)) {
                SourceSprite sprite = Objects.requireNonNull(sources.get(fragment.sourceSlot()), "missing source sprite");
                UvQuad quad = fragment.quad();
                quads.add(new Bound.Quad(textures.indexOf(sprite.texture()), new UvQuad(quad.vertices().stream()
                        .map(v -> new UvVertex(v.x(), v.y(), v.z(), sprite.u(v.u()), sprite.v(v.v()))).toList(),
                        quad.nx(), quad.ny(), quad.nz()), fragment.targetQuad()));
            }
        }
        return new Bound.Node(node.part, List.copyOf(quads), node.children.values().stream()
                .map(child -> bindNode(child, plan, sources, textures)).toList());
    }

    private static final class Node {
        final ModelPart part;
        final List<Integer> faces = new ArrayList<>();
        final Map<String, Node> children = new LinkedHashMap<>();
        Node(ModelPart part) { this.part = part; }
    }

    private static final class Collector implements VertexConsumer {
        private final List<UvQuad> quads;
        private final List<UvVertex> vertices = new ArrayList<>(4);
        private float nx, ny, nz;
        Collector(List<UvQuad> quads) { this.quads = quads; }
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float normalX, float normalY, float normalZ) {
            if (vertices.isEmpty()) { nx = normalX; ny = normalY; nz = normalZ; }
            else if (Math.abs(nx - normalX) + Math.abs(ny - normalY) + Math.abs(nz - normalZ) > 0.00001f) {
                throw new IllegalArgumentException("model face has varying normals");
            }
            vertices.add(new UvVertex(x, y, z, u, v));
            if (vertices.size() == 4) {
                quads.add(new UvQuad(List.copyOf(vertices), nx, ny, nz));
                vertices.clear();
            }
        }
        void finish() { if (!vertices.isEmpty()) throw new IllegalArgumentException("incomplete model quad"); }
        public VertexConsumer addVertex(float x,float y,float z){throw unsupported();}
        public VertexConsumer setColor(int r,int g,int b,int a){throw unsupported();}
        public VertexConsumer setUv(float u,float v){throw unsupported();}
        public VertexConsumer setUv1(int u,int v){throw unsupported();}
        public VertexConsumer setUv2(int u,int v){throw unsupported();}
        public VertexConsumer setNormal(float x,float y,float z){throw unsupported();}
        private IllegalArgumentException unsupported() { return new IllegalArgumentException("nonstandard cube vertex emission"); }
    }

    /** Material-bound local mesh. Per frame: live part poses and vertex submission only. */
    public static final class Bound {
    private final Node root;
    private final List<ResourceLocation> textures;

    Bound(Node root, List<ResourceLocation> textures) {
        this.root = root; this.textures = List.copyOf(textures);
    }

    public void render(PoseStack pose, Function<ResourceLocation, VertexConsumer> buffers,
                       int light, int overlay, int color) {
        Vector3f position = new Vector3f(), normal = new Vector3f();
        // A new RenderType can end BufferSource's previous shared builder. Finish
        // submitting one texture before asking for another; never retain stale consumers.
        for (int texture = 0; texture < textures.size(); texture++) {
            VertexConsumer consumer = buffers.apply(textures.get(texture));
            renderNode(root, pose, texture, false, consumer, light, overlay, color, position, normal);
        }
    }

    public int textureBatches() { return textures.size(); }

    /** Renders an additional target-texture layer on exactly the direct mesh's fragmented geometry. */
    public void renderLayer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        renderNode(root, pose, -1, true, buffer, light, overlay, color, new Vector3f(), new Vector3f());
    }

    private static void renderNode(Node node, PoseStack pose, int texture, boolean targetUv, VertexConsumer buffer, int light, int overlay,
                                    int color, Vector3f position, Vector3f normal) {
        if (!node.part.visible) return;
        pose.pushPose();
        try {
            node.part.translateAndRotate(pose);
            if (!node.part.skipDraw) {
                var transform = pose.last();
                for (Quad bound : node.quads) {
                    if (!targetUv && bound.textureIndex != texture) continue;
                    UvQuad quad = targetUv ? bound.targetQuad : bound.quad;
                    transform.transformNormal(quad.nx(), quad.ny(), quad.nz(), normal);
                    for (var vertex : quad.vertices()) {
                        transform.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), position);
                        buffer.addVertex(position.x, position.y, position.z, color, vertex.u(), vertex.v(),
                                overlay, light, normal.x, normal.y, normal.z);
                    }
                }
            }
            for (Node child : node.children) renderNode(child, pose, texture, targetUv, buffer, light, overlay, color, position, normal);
        } finally {
            pose.popPose();
        }
    }

    record Node(ModelPart part, List<Quad> quads, List<Node> children) { }
    record Quad(int textureIndex, UvQuad quad, UvQuad targetQuad) { }
    }
}
