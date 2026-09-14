package com.nobodiiiii.createbiotech.foundation.render;

import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * A machine-owned model, with no entity renderer, global model layer or entity animation state.
 * Geometry is baked from our fixed definitions; each renderer owns its mutable pose. Texture
 * locations are resolved by the normal render types, so resource reloads do not invalidate this
 * geometry or leave references to a departed client level.
 */
public final class MachineCreatureModel {

	private final ModelPart root;
	private final List<RestPart> restParts;
	private final Function<ResourceLocation, RenderType> renderType;

	MachineCreatureModel(ModelPart root) {
		this(root, RenderType::entityCutoutNoCull);
	}

	MachineCreatureModel(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
		this.root = root;
		this.renderType = renderType;
		this.restParts = root.getAllParts()
			.map(part -> new RestPart(part, part.visible, part.skipDraw))
			.toList();
	}

	public ModelPart root() {
		return root;
	}

	public void resetPose() {
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
		root.render(poseStack, buffer, packedLight, packedOverlay, color);
	}

	private record RestPart(ModelPart part, boolean visible, boolean skipDraw) {}
}
