package com.nobodiiiii.createbiotech.mixin.client;

import java.util.Collection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nobodiiiii.createbiotech.client.CreativeTabSectionRenderer;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

	@Shadow
	private static CreativeModeTab selectedTab;

	@Inject(method = "selectTab(Lnet/minecraft/world/item/CreativeModeTab;)V", at = @At("HEAD"),
		require = 1, expect = 1)
	private void createBiotech$resetSectionLayout(CreativeModeTab tab, CallbackInfo ci) {
		CreativeTabSectionRenderer.resetLayout();
	}

	@ModifyExpressionValue(
		method = "selectTab(Lnet/minecraft/world/item/CreativeModeTab;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/CreativeModeTab;getDisplayItems()Ljava/util/Collection;"),
		require = 1,
		expect = 1)
	private Collection<ItemStack> createBiotech$applySectionLayout(Collection<ItemStack> displayItems) {
		return CBCreativeModeTabs.isMainTab(selectedTab)
			? CreativeTabSectionRenderer.activateLayout(displayItems)
			: displayItems;
	}

	@ModifyVariable(
		method = "refreshCurrentTabContents(Ljava/util/Collection;)V",
		at = @At("HEAD"),
		argsOnly = true,
		require = 1,
		expect = 1)
	private Collection<ItemStack> createBiotech$refreshSectionLayout(Collection<ItemStack> displayItems) {
		return CBCreativeModeTabs.isMainTab(selectedTab)
			? CreativeTabSectionRenderer.activateLayout(displayItems)
			: displayItems;
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"),
		require = 1, expect = 1)
	private void createBiotech$renderSectionBanners(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick, CallbackInfo ci) {
		if (CBCreativeModeTabs.isMainTab(selectedTab))
			CreativeTabSectionRenderer.render((CreativeModeInventoryScreen) (Object) this, graphics);
	}

	@Inject(method = "removed()V", at = @At("TAIL"), require = 1, expect = 1)
	private void createBiotech$clearSectionLayout(CallbackInfo ci) {
		CreativeTabSectionRenderer.resetLayout();
	}
}
