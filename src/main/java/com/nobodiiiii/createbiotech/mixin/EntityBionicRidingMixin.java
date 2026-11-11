package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.entity.BionicRiding;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityBionicRidingMixin {
	@ModifyReturnValue(method = "makeBoundingBox", at = @At("RETURN"))
	private AABB createBiotech$riderBounds(AABB original) {
		Entity entity = (Entity) (Object) this;
		var pose = BionicRiding.pose(entity, 1);
		return pose == null ? original : pose.bounds(entity).move(entity.position().subtract(pose.feet(entity)));
	}

	@ModifyReturnValue(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
	private Vec3 createBiotech$riderEyes(Vec3 original) {
		if ((Object) this instanceof SlimeBionicEntity bionic && bionic.isBlockAttached())
			return bionic.bodyToWorld(bionic.getBlockAttachment().eyePoint());
		var pose = BionicRiding.pose((Entity) (Object) this, 1);
		return pose == null ? original : pose.eyes((Entity) (Object) this);
	}

	@ModifyReturnValue(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
	private Vec3 createBiotech$interpolatedRiderEyes(Vec3 original, float partialTick) {
		if ((Object) this instanceof SlimeBionicEntity bionic && bionic.isBlockAttached())
			return bionic.getPosition(partialTick).add(bionic.blockAttachmentRotation()
				.rotate(bionic.getBlockAttachment().eyePoint()));
		var pose = BionicRiding.pose((Entity) (Object) this, partialTick);
		return pose == null ? original : pose.eyes((Entity) (Object) this);
	}

	@ModifyReturnValue(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
	private Vec3 createBiotech$attachedView(Vec3 original, float partialTick) {
		return (Object) this instanceof SlimeBionicEntity bionic && bionic.isBlockAttached()
			? bionic.blockAttachmentRotation().rotate(original) : original;
	}
}
