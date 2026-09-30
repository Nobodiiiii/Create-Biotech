package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;

import net.neoforged.neoforge.client.IItemDecorator;

/** The chain remains the main icon; the selected close-up occupies its lower-right quarter. */
public final class SurgicalJointItemDecorator {
	public static final float BADGE_SCALE = 0.5f;
	public static final float BADGE_OFFSET = 8.0f;
	// Item geometry is at Z=150 and the stack count at Z=200. The badge lands in between.
	public static final float BADGE_DEPTH = 100.0f;

	public static final IItemDecorator INSTANCE = (graphics, font, stack, x, y) -> {
		SurgicalLimbType type = SurgicalJointItem.selectedType(stack);
		if (type == null)
			return false;
		PoseStack pose = graphics.pose();
		pose.pushPose();
		try {
			pose.translate(x + BADGE_OFFSET, y + BADGE_OFFSET, BADGE_DEPTH);
			pose.scale(BADGE_SCALE, BADGE_SCALE, BADGE_SCALE);
			graphics.renderItem(SurgicalJointItem.previewStack(type), 0, 0);
		} finally {
			pose.popPose();
		}
		return false;
	};

	private SurgicalJointItemDecorator() {}
}
