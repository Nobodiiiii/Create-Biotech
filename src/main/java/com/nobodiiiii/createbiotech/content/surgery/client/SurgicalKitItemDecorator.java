package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** Lower-right kit badge, matching the captured-cardboard-box badge layout used in JEI. */
public final class SurgicalKitItemDecorator {
	private static final ItemStack BADGE = new ItemStack(CBItems.SURGICAL_KIT.get());
	private static final float BADGE_SCALE = 0.55f;

	public static final IItemDecorator INSTANCE = (graphics, font, stack, x, y) -> {
		if (SurgicalKitItem.selectedTool(stack) == null)
			return false;
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x + 8.0f, y + 8.0f, 200.0f);
		poseStack.scale(BADGE_SCALE, BADGE_SCALE, BADGE_SCALE);
		graphics.renderItem(BADGE, 0, 0);
		poseStack.popPose();
		return false;
	};

	private SurgicalKitItemDecorator() {}
}
