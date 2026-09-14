package com.nobodiiiii.createbiotech.content.processing.basin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Shares the basin's fixed slime geometry in every item display context. */
public final class CapturedSmallSlimeItemRenderer extends BlockEntityWithoutLevelRenderer {

	private final float scale;

	private CapturedSmallSlimeItemRenderer(float scale) {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		this.scale = scale;
	}

	public static IClientItemExtensions create(float scale) {
		return new IClientItemExtensions() {
			private final BlockEntityWithoutLevelRenderer renderer = new CapturedSmallSlimeItemRenderer(scale);

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		MachineCreatureRenderer.renderCenteredSlime(poseStack, buffer, packedLight,
			GuiEntityItemElement.baseYRotation(displayContext), 1, scale, false);
	}
}
