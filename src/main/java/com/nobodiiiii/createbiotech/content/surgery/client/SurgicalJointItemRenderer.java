package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Inventory and JEI both use the same fixed Steve close-up through the ordinary item renderer. */
public final class SurgicalJointItemRenderer extends BlockEntityWithoutLevelRenderer {
	public SurgicalJointItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay) {
		if (stack.getItem() instanceof SurgicalJointItem joint)
			SurgicalJointIconModel.of(joint.limbType()).element(context, light).render(pose, buffer);
	}
}
