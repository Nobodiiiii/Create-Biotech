package com.nobodiiiii.createbiotech.foundation.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Item-space framing for the same baked JSON parts used by machine creatures. */
public abstract class MachineCreatureItemRenderer extends BlockEntityWithoutLevelRenderer {

	protected final MachineCreatureModel model;
	private final float scale;
	private final EntityGeometry.Collector measurement = EntityGeometry.Collector.boundsOnly();

	protected MachineCreatureItemRenderer(MachineCreatureModel model, float scale) {
		super(null, null);
		this.model = model;
		this.scale = scale;
	}

	@Override
	public final void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		// Optional cargo must not change the center of the item or its scale between contexts.
		prepareModel(stack, true);
		measurement.reset();
		renderAtFeet(new PoseStack(), measurement.asBufferSource(), LightTexture.FULL_BRIGHT);
		Vector3f center = measurement.hasVertices() ? measurement.bounds().center() : new Vector3f();
		prepareModel(stack, false);
		GuiEntityItemElement.ofModel(this::renderAtFeet)
			.blockCentered()
			.geometryCenter(center)
			.fixedScale(scale)
			.yRotation(GuiEntityItemElement.baseYRotation(displayContext) + additionalYRotation(stack, displayContext))
			.packedLight(packedLight)
			.render(poseStack, buffer);
	}

	protected abstract void prepareModel(ItemStack stack, boolean measuring);

	protected float additionalYRotation(ItemStack stack, ItemDisplayContext displayContext) {
		return 0;
	}

	protected void renderModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -1);
	}

	private void renderAtFeet(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		try {
			poseStack.mulPose(Axis.YP.rotationDegrees(180));
			poseStack.scale(-1, -1, 1);
			poseStack.translate(0, -1.501f, 0);
			renderModel(poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
		}
	}
}
