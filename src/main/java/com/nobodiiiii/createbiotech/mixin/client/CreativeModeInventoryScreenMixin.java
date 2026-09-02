package com.nobodiiiii.createbiotech.mixin.client;

import com.nobodiiiii.createbiotech.client.CreativeTabSectionRenderer;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

	@Shadow
	private static CreativeModeTab selectedTab;

	@Inject(method = "render", at = @At("TAIL"))
	private void createBiotech$renderSectionBanners(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo ci) {
		if (CBCreativeModeTabs.isMainTab(selectedTab))
			CreativeTabSectionRenderer.render((CreativeModeInventoryScreen) (Object) this, graphics);
	}
}
