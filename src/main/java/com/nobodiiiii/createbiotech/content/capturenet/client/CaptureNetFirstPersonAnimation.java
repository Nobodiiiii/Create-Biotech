package com.nobodiiiii.createbiotech.content.capturenet.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Camera-space scoop: a fixed handle butt in the holding hand and a centered, descending mouth. */
final class CaptureNetFirstPersonAnimation {
	// Model [8, -8, 8], centered on [8, 8, 8]: the actual bottom of the wooden handle.
	private static final float HANDLE_BOTTOM_Y = -1;
	private static final float SCOOP_SCALE = 1.2f;
	private static final float RAISE_END = 0.22f;
	private static final float FOLLOW_THROUGH_END = 0.65f;
	private static final float RECOVER_START = 0.72f;

	private CaptureNetFirstPersonAnimation() {}

	static void apply(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
		float equip, float progress) {
		boolean left = arm == HumanoidArm.LEFT;
		float side = left ? -1 : 1;
		ItemDisplayContext context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			: ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		var model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
		PoseStack display = new PoseStack();
		model.applyTransform(context, display, left);
		Matrix4f displayTransform = display.last().pose();
		Matrix4f resting = new Matrix4f().translation(side * 0.56f, -0.52f - equip * 0.6f, -0.72f)
			.mul(displayTransform);
		Vector3f restingButt = resting.transformPosition(new Vector3f(0, HANDLE_BOTTOM_Y, 0));
		Vector3f restingScale = resting.getScale(new Vector3f());
		Quaternionf restingRotation = resting.getUnnormalizedRotation(new Quaternionf());

		float contact = (float) CaptureNetItem.CAPTURE_CONTACT_TICK / CaptureNetItem.ANIMATION_TICKS;
		float mouthY;
		if (progress < RAISE_END)
			mouthY = 0.64f;
		else if (progress < contact)
			mouthY = Mth.lerp(ease((progress - RAISE_END) / (contact - RAISE_END)), 0.64f, 0);
		else
			mouthY = Mth.lerp(ease((progress - contact) / (FOLLOW_THROUGH_END - contact)), 0, -0.20f);

		Vector3f hand = new Vector3f(side * 0.56f, -0.52f - equip * 0.6f, -0.72f);
		float handleToMouth = (CaptureNetAnimation.BAG_PIVOT[1] / 16 - HANDLE_BOTTOM_Y) * SCOOP_SCALE;
		float dy = mouthY - hand.y;
		// Solve the forward distance instead of translating the model sideways: the butt stays in the hand.
		float dz = -Mth.sqrt(Math.max(0.01f, handleToMouth * handleToMouth - hand.x * hand.x - dy * dy));
		Vector3f alongHandle = new Vector3f(-hand.x, dy, dz).normalize();
		Vector3f acrossMouth = alongHandle.cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
		// The pouch extends upward behind the mouth, so its open -Z face points down onto the creature.
		Vector3f intoPouch = acrossMouth.cross(alongHandle, new Vector3f()).normalize();
		Matrix3f axes = new Matrix3f().setColumn(0, acrossMouth).setColumn(1, alongHandle).setColumn(2, intoPouch);
		Quaternionf scoopRotation = new Quaternionf().setFromNormalized(axes);
		float weight = progress < RAISE_END ? ease(progress / RAISE_END)
			: progress > RECOVER_START ? ease((1 - progress) / (1 - RECOVER_START)) : 1;
		Vector3f butt = restingButt.lerp(hand, weight);
		Vector3f scale = restingScale.lerp(new Vector3f(SCOOP_SCALE), weight);
		pose.translate(butt.x, butt.y, butt.z);
		pose.mulPose(restingRotation.slerp(scoopRotation, weight));
		pose.scale(scale.x, scale.y, scale.z);
		pose.translate(0, -HANDLE_BOTTOM_Y, 0);
		// ItemRenderer applies the baked display transform after this hook; cancel it exactly once.
		pose.mulPose(new Matrix4f(displayTransform).invert());
	}

	private static float ease(float value) {
		float clamped = Mth.clamp(value, 0, 1);
		return clamped * clamped * (3 - 2 * clamped);
	}
}
