package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.client.CapturedModel.Bound;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Rendering plan, not an obligation to produce another texture. Obtain again after resource reload. */
public final class CastedModelHandle {
    public enum Backend { DIRECT_UV, COMPOSITE, FALLBACK }
    private final ModelPart model;
    private final Bound mesh;
    private final Bound emissionMesh;
    private final ResourceLocation texture;
    private final Backend backend;
    private final UvPlan plan;
    private final long generation;
    private final String reason;
    private final MaterialAttachments.Prepared attachments;

    private CastedModelHandle(ModelPart model, Bound mesh, ResourceLocation texture,
                              Backend backend, UvPlan plan, long generation, String reason, MaterialAttachments.Prepared attachments, Bound emissionMesh) {
        this.model = Objects.requireNonNull(model); this.mesh = mesh; this.texture = texture;
        this.backend = backend; this.plan = plan; this.generation = generation; this.reason = reason; this.attachments = attachments; this.emissionMesh = emissionMesh;
    }
    public static CastedModelHandle direct(ModelPart model, Bound mesh, UvPlan plan, long generation) {
        return direct(model, mesh, plan, generation, null);
    }
    static CastedModelHandle direct(ModelPart model, Bound mesh, UvPlan plan, long generation,
                                    MaterialAttachments.Prepared attachments) {
        return new CastedModelHandle(model, Objects.requireNonNull(mesh), null, Backend.DIRECT_UV,
                Objects.requireNonNull(plan), generation, "DIRECT_UV", attachments, null);
    }
    static CastedModelHandle composite(ModelPart model, Bound mesh, Bound emission, UvPlan plan, long generation,
                                       MaterialAttachments.Prepared attachments) {
        return new CastedModelHandle(model, mesh, null, Backend.COMPOSITE, plan, generation, "CACHED_COMPOSITE", attachments, emission);
    }
    public static CastedModelHandle fallback(ModelPart model, ResourceLocation texture) {
        return fallback(model, texture, 0, "NO_MATERIAL");
    }
    public static CastedModelHandle fallback(ModelPart model, ResourceLocation texture, long generation, String reason) {
        return new CastedModelHandle(model, null, Objects.requireNonNull(texture),
                Backend.FALLBACK, null, generation, reason, null, null);
    }
    public Backend backend() { return backend; }
    public Optional<UvPlan> plan() { return Optional.ofNullable(plan); }
    public long generation() { return generation; }
    public String reason() { return reason; }
    public int textureBatches() {
        if (mesh == null) return 1;
        var textures = new java.util.HashSet<>(mesh.textures());
        if (attachments != null) textures.addAll(attachments.textures());
        return textures.size();
    }
    public void render(PoseStack pose, MultiBufferSource buffers, Function<ResourceLocation, RenderType> renderType,
                       int light, int overlay, int color) {
        if (mesh != null) mesh.render(pose, id -> buffers.getBuffer(renderType.apply(id)), light, overlay, color);
        else model.render(pose, buffers.getBuffer(renderType.apply(texture)), light, overlay, color);
        if (attachments != null) attachments.render(pose, buffers, renderType, light, overlay, color);
    }
    public void renderEmissive(PoseStack pose, MultiBufferSource buffers, int light, int overlay, int color) {
        if (emissionMesh != null) emissionMesh.render(pose, id -> buffers.getBuffer(RenderType.eyes(id)), light, overlay, color);
        else if (mesh != null) mesh.renderEmissive(pose, id -> buffers.getBuffer(RenderType.eyes(id)), light, overlay, color);
    }
    public void renderLayer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        if (mesh != null) mesh.renderLayer(pose, buffer, light, overlay, color);
        else model.render(pose, buffer, light, overlay, color);
    }
}
