package com.nobodiiiii.createbiotech.foundation.render;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** A resource-backed machine model with independent mutable pose state. */
public final class MachineCreatureModel {

	private final MachineCreatureModels.ModelSpec spec;
	private final Function<ResourceLocation, RenderType> renderType;
	private ModelPart root;
	private List<RestPart> restParts = List.of();
	private Map<String, Anchor> anchors = Map.of();
	private int loadedGeneration = -1;

	MachineCreatureModel(MachineCreatureModels.ModelSpec spec) {
		this(spec, RenderType::entityCutoutNoCull);
	}

	MachineCreatureModel(MachineCreatureModels.ModelSpec spec,
		Function<ResourceLocation, RenderType> renderType) {
		this.spec = spec;
		this.renderType = renderType;
	}

	public ModelPart root() {
		ensureLoaded();
		return root;
	}

	public Anchor anchor(String name) {
		ensureLoaded();
		Anchor anchor = anchors.get(name);
		if (anchor == null)
			throw new IllegalArgumentException("Unknown anchor '" + name + "' in machine model " + spec.id());
		return anchor;
	}

	public void resetPose() {
		ensureLoaded();
		for (RestPart rest : restParts) {
			rest.part.resetPose();
			rest.part.visible = rest.visible;
			rest.part.skipDraw = rest.skipDraw;
		}
	}

	public RenderType renderType(ResourceLocation texture) {
		return renderType.apply(texture);
	}

	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
		int packedOverlay, int color) {
		ensureLoaded();
		root.render(poseStack, buffer, packedLight, packedOverlay, color);
	}

	private void ensureLoaded() {
		int generation = MachineCreatureModelLoader.generation();
		if (root != null && loadedGeneration == generation)
			return;
		synchronized (this) {
			generation = MachineCreatureModelLoader.generation();
			if (root != null && loadedGeneration == generation)
				return;
			MachineCreatureModelLoader.BakedModel baked = MachineCreatureModelLoader.bake(spec);
			root = baked.root();
			anchors = baked.anchors();
			restParts = root.getAllParts()
				.map(part -> new RestPart(part, part.visible, part.skipDraw))
				.toList();
			loadedGeneration = generation;
		}
	}

	/** A point in the local coordinate system of a named model part. */
	public record Anchor(ModelPart part, float x, float y, float z) {}

	private record RestPart(ModelPart part, boolean visible, boolean skipDraw) {}
}
