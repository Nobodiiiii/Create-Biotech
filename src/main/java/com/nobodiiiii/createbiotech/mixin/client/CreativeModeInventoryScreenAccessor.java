package com.nobodiiiii.createbiotech.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface CreativeModeInventoryScreenAccessor {

	@Accessor("leftPos")
	int createBiotech$getLeftPos();

	@Accessor("topPos")
	int createBiotech$getTopPos();
}
