package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;

import net.minecraft.world.item.ItemStack;

@Mixin(PackagerItemHandler.class)
public abstract class PackagerItemHandlerMixin {
	@Shadow(remap = false) private PackagerBlockEntity blockEntity;

	@ModifyReturnValue(method = "isItemValid", at = @At("RETURN"))
	private boolean createBiotech$validateBioPackage(boolean valid, int slot, ItemStack stack) {
		return valid && (!(blockEntity instanceof BioPackagerBlockEntity)
			|| slot == 0 && BioPackagerBlockEntity.isCreaturePackage(stack));
	}
}
