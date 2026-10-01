package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.entity.BionicRiding;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityBionicRidingViewMixin {
	@ModifyReturnValue(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
	private Vec3 createBiotech$alignMountedPicking(Vec3 original, float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = (Entity) (Object) this;
		if (entity != minecraft.getCameraEntity() || !minecraft.options.getCameraType().isFirstPerson())
			return original;
		// Both Entity.pick (blocks) and GameRenderer.pick (entities) use this direction.
		var pose = BionicRiding.pose(entity, partialTick);
		return pose == null ? original : pose.rotation().rotate(original);
	}
}
