package com.nobodiiiii.createbiotech.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

@Mixin(PackagerLinkBlockEntity.class)
public abstract class PackagerLinkBlockEntityMixin {
	@ModifyReturnValue(method = "getPackager", at = @At("RETURN"))
	@Nullable
	private PackagerBlockEntity createBiotech$skipBioPackager(@Nullable PackagerBlockEntity packager) {
		return packager instanceof BioPackagerBlockEntity ? null : packager;
	}
}
