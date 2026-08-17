package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.box.PackageRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CardboardBoxEntityRenderer extends EntityRenderer<CardboardBoxEntity> {
	private static final PartialModel SMALL_CARDBOARD_BOX =
		PartialModel.of(CreateBiotech.asResource("item/small_cardboard_box"));
	private static final PartialModel SMALL_CARDBOARD_BOX_CAPTURED =
		PartialModel.of(CreateBiotech.asResource("item/small_cardboard_box_captured"));
	private static final PartialModel LARGE_CARDBOARD_BOX =
		PartialModel.of(CreateBiotech.asResource("item/large_cardboard_box"));
	private static final PartialModel LARGE_CARDBOARD_BOX_CAPTURED =
		PartialModel.of(CreateBiotech.asResource("item/large_cardboard_box_captured"));
	// The icon is under 0.6 blocks tall; past this distance it covers a few
	// pixels at most, so only the box model is drawn.
	private static final double MAX_ICON_DISTANCE_SQUARED = 32.0d * 32.0d;

	public CardboardBoxEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.5f;
	}

	@Override
	public void render(CardboardBoxEntity entity, float yaw, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int light) {
		ItemStack stack = entity.getBox();
		boolean captured = CapturedEntityBoxHelper.hasCapturedEntity(stack);
		boolean renderCapturedEntity = captured && CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get();
		PackageRenderer.renderBox(entity, yaw, poseStack, buffer, light,
			getModel(stack, captured, renderCapturedEntity));
		if (renderCapturedEntity && isIconWithinDistance(entity))
			CapturedEntityBoxIconRenderer.renderOnEntity(stack, true, yaw, poseStack, buffer, light);
		super.render(entity, yaw, partialTicks, poseStack, buffer, light);
	}

	private boolean isIconWithinDistance(CardboardBoxEntity entity) {
		Camera camera = entityRenderDispatcher.camera;
		return camera == null || entity.distanceToSqr(camera.getPosition()) <= MAX_ICON_DISTANCE_SQUARED;
	}

	private PartialModel getModel(ItemStack stack, boolean captured, boolean renderCapturedEntity) {
		if (captured && !renderCapturedEntity)
			return stack.is(CBItems.LARGE_CARDBOARD_BOX.get())
				? CardboardBoxPartials.LARGE_BOX_LOGISTICS : CardboardBoxPartials.SMALL_BOX_LOGISTICS;
		if (stack.is(CBItems.LARGE_CARDBOARD_BOX.get()))
			return captured ? LARGE_CARDBOARD_BOX_CAPTURED : LARGE_CARDBOARD_BOX;
		return captured ? SMALL_CARDBOARD_BOX_CAPTURED : SMALL_CARDBOARD_BOX;
	}

	@Override
	public ResourceLocation getTextureLocation(CardboardBoxEntity entity) {
		return null;
	}
}
