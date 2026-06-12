package com.nobodiiiii.createbiotech.mixin;

import com.nobodiiiii.createbiotech.content.experience.LegacyExperienceCompat;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidTankBlockEntity.class, priority = 1001)
public abstract class FluidTankBlockEntityMixin {
	@Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("HEAD"), remap = false)
	private void createBiotech$migrateLegacyExperienceTankNbt(CompoundTag compound, boolean clientPacket,
		CallbackInfo ci) {
		LegacyExperienceCompat.migrateTankNbt(compound);
	}
}
