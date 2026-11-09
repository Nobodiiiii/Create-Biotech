package com.nobodiiiii.createbiotech.content.allay.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierItem;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureItemRenderer;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Courier items share the station's JSON body parts and fixed hat, without a render entity. */
public final class AllayCourierItemRenderer extends MachineCreatureItemRenderer {

	private static final PartialModel CARGO = PartialModel.of(CreateBiotech.asResource("item/allay_courier_package"));
	private final boolean hasHat;
	private boolean hasCargo;

	public AllayCourierItemRenderer(boolean hasHat, float scale) {
		super(MachineCreatureModels.allay(), scale);
		this.hasHat = hasHat;
	}

	@Override
	protected void prepareModel(ItemStack stack, boolean measuring) {
		model.resetPose();
		hasCargo = hasHat && !measuring && !AllayCourierItem.copyCargoPackage(stack).isEmpty();
		// Preserve the former frozen vanilla item pose (age = 1, no walking or dancing).
		model.root().y += Mth.cos(9 * Mth.DEG_TO_RAD) * .25f;
		float wingSwing = Mth.cos(20 * Mth.DEG_TO_RAD) * Mth.PI * .15f;
		model.part("body/right_wing").xRot = model.part("body/left_wing").xRot = 25 * Mth.DEG_TO_RAD;
		model.part("body/right_wing").yRot = -Mth.PI / 4 + wingSwing;
		model.part("body/left_wing").yRot = Mth.PI / 4 - wingSwing;
		float armRoll = 25 * Mth.DEG_TO_RAD
			- Mth.cos(9 * Mth.DEG_TO_RAD + Mth.PI * 1.5f) * Mth.PI * .075f;
		model.part("body/right_arm").zRot = armRoll;
		model.part("body/left_arm").zRot = -armRoll;
		if (hasCargo) {
			model.part("body/right_arm").xRot = model.part("body/left_arm").xRot = -Mth.PI / 3;
			model.part("body/right_arm").yRot = .27925268f;
			model.part("body/left_arm").yRot = -.27925268f;
		}
	}

	@Override
	protected float additionalYRotation(ItemStack stack, ItemDisplayContext displayContext) {
		return hasHat && (displayContext == ItemDisplayContext.FIXED || displayContext == ItemDisplayContext.GROUND)
			? AllayCourierItem.getHeadingAngle(stack) : 0;
	}

	@Override
	protected void renderModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.renderModel(poseStack, buffer, packedLight);
		if (hasHat)
			AllayLogisticsHatRenderer.render(model, poseStack, buffer, packedLight);
		if (!hasCargo)
			return;
		poseStack.pushPose();
		model.applyTransformTo("body", poseStack);
		poseStack.scale(1, -1, -1);
		CachedBuffers.partial(CARGO, Blocks.AIR.defaultBlockState())
			.light(packedLight)
			.renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
		poseStack.popPose();
	}
}
