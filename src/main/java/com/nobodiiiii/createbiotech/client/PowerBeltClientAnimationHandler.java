package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltWalkAnimation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class PowerBeltClientAnimationHandler {

	private PowerBeltClientAnimationHandler() {}

	public static void handleSurfaceMovement(int entityId, float distance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;

		Entity entity = minecraft.level.getEntity(entityId);
		if (entity instanceof LivingEntity livingEntity)
			PowerBeltWalkAnimation.recordSurfaceMovement(livingEntity, distance);
	}
}
