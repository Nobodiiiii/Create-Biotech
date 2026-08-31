package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxIconRenderer;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueHandler;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders logical kit tools whose visible layers cannot be represented by a static item override. */
public class SurgicalKitItemRenderer extends BlockEntityWithoutLevelRenderer {
	private static final ResourceLocation CLOSED_BOX_MODEL =
		CreateBiotech.asResource("item/large_cardboard_box_captured");
	private static final PartialModel WAND_ITEM =
		PartialModel.of(Create.asResource("item/wand_of_symmetry/item"));
	private static final PartialModel WAND_BITS =
		PartialModel.of(Create.asResource("item/wand_of_symmetry/bits"));
	private static final PartialModel WAND_CORE =
		PartialModel.of(Create.asResource("item/wand_of_symmetry/core"));
	private static final PartialModel WAND_CORE_GLOW =
		PartialModel.of(Create.asResource("item/wand_of_symmetry/core_glow"));
	private static final PartialModel WRENCH_ITEM =
		PartialModel.of(Create.asResource("item/wrench/item"));
	private static final PartialModel WRENCH_GEAR =
		PartialModel.of(Create.asResource("item/wrench/gear"));

	public SurgicalKitItemRenderer() {
		super(null, null);
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int light, int overlay) {
		PartialItemModelRenderer renderer = PartialItemModelRenderer.of(stack, transformType,
			poseStack, buffer, overlay);

		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		SurgicalKitItem.Tool tool = SurgicalKitItem.selectedTool(stack);
		if (tool == SurgicalKitItem.Tool.SYMMETRY_WAND)
			renderSymmetryWand(renderer, poseStack, light);
		else if (tool == SurgicalKitItem.Tool.WRENCH)
			renderWrench(renderer, poseStack, light);
		else
			renderTemporaryBox(stack, transformType, renderer, poseStack, buffer, light);
		poseStack.popPose();
	}

	private static void renderSymmetryWand(PartialItemModelRenderer renderer, PoseStack poseStack, int light) {
		int maxLight = LightTexture.FULL_BRIGHT;
		renderer.render(WAND_ITEM.get(), light);
		renderer.renderSolidGlowing(WAND_CORE.get(), maxLight);
		renderer.renderGlowing(WAND_CORE_GLOW.get(), maxLight);

		float worldTime = AnimationTickHolder.getRenderTime() / 20.0f;
		poseStack.translate(0.0f, Mth.sin(worldTime) * 0.05f, 0.0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * -10.0f % 360.0f));
		renderer.renderGlowing(WAND_BITS.get(), maxLight);
	}

	private static void renderWrench(PartialItemModelRenderer renderer, PoseStack poseStack, int light) {
		renderer.render(WRENCH_ITEM.get(), light);
		float xOffset = -1.0f / 16.0f;
		poseStack.translate(-xOffset, 0.0f, 0.0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(
			ScrollValueHandler.getScroll(AnimationTickHolder.getPartialTicks())));
		poseStack.translate(xOffset, 0.0f, 0.0f);
		renderer.render(WRENCH_GEAR.get(), light);
	}

	private static void renderTemporaryBox(ItemStack stack, ItemDisplayContext transformType,
		PartialItemModelRenderer renderer, PoseStack poseStack, MultiBufferSource buffer, int light) {
		BakedModel boxModel = Minecraft.getInstance().getModelManager().getModel(
			new ModelResourceLocation(CLOSED_BOX_MODEL, ModelResourceLocation.STANDALONE_VARIANT));
		renderer.render(boxModel, light);
		CapturedEntityBoxIconRenderer.renderOnLargeItem(stack, transformType, poseStack, buffer, light);
	}
}
