package com.nobodiiiii.createbiotech.mixin.client;

import com.nobodiiiii.createbiotech.client.CreativeTabSectionRenderer;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class ItemPickerMenuMixin {

	@Shadow
	protected abstract int getRowIndexForScroll(float scrollPosition);

	@Inject(method = "scrollTo(F)V", at = @At("HEAD"), require = 1, expect = 1)
	private void createBiotech$trackCreativeTabRow(float scrollPosition, CallbackInfo ci) {
		CreativeTabSectionRenderer.setCurrentRow(getRowIndexForScroll(scrollPosition));
	}
}
