package com.nobodiiiii.createbiotech.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SonicDogCannonArmPose {

	/**
	 * 1.20.1's Forge extends {@code ArmPose} through {@link HumanoidModel.ArmPose#create} rather
	 * than through an {@code enumextensions.json} entry.
	 */
	public static final HumanoidModel.ArmPose ARM_POSE = HumanoidModel.ArmPose.create(
		"CREATE_BIOTECH_SONIC_DOG_CANNON", true, SonicDogCannonArmPose::applyPose);

	private SonicDogCannonArmPose() {}

	private static void applyPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm holdingArm) {
		ModelPart holding = holdingArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart supporting = holdingArm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
		float mirror = holdingArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;

		// Keep the tuned pose as the neutral position, then track the head like vanilla ranged weapons.
		holding.xRot = radians(-60.0f) + model.head.xRot;
		holding.yRot = model.head.yRot;
		holding.zRot = 0.0f;

		// Reach the free hand inward and rest it on the cannon body.
		supporting.xRot = radians(-75.0f) + model.head.xRot;
		supporting.yRot = mirror * radians(35.0f) + model.head.yRot;
		supporting.zRot = mirror * radians(10.0f);
	}

	private static float radians(float degrees) {
		return degrees * (float) Math.PI / 180.0f;
	}
}
