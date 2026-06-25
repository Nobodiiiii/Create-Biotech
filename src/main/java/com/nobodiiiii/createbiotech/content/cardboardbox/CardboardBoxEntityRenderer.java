package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.box.PackageRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CardboardBoxEntityRenderer extends EntityRenderer<CardboardBoxEntity> {
	private static final PartialModel CARDBOARD_BOX = PartialModel.of(CreateBiotech.asResource("item/cardboard_box"));
	private static final PartialModel LARGE_CARDBOARD_BOX =
		PartialModel.of(CreateBiotech.asResource("item/large_cardboard_box"));

	public CardboardBoxEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.5f;
	}

	@Override
	public void render(CardboardBoxEntity entity, float yaw, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int light) {
		PackageRenderer.renderBox(entity, yaw, poseStack, buffer, light, getModel(entity.getBox()));
		super.render(entity, yaw, partialTicks, poseStack, buffer, light);
	}

	private PartialModel getModel(ItemStack stack) {
		return stack.is(CBItems.LARGE_CARDBOARD_BOX.get()) ? LARGE_CARDBOARD_BOX : CARDBOARD_BOX;
	}

	@Override
	public ResourceLocation getTextureLocation(CardboardBoxEntity entity) {
		return null;
	}
}
