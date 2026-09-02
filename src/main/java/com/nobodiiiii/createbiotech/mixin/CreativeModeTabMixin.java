package com.nobodiiiii.createbiotech.mixin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {

	@Shadow
	private Collection<ItemStack> displayItems;

	@Shadow
	private Set<ItemStack> displayItemsSearchTab;

	@WrapMethod(method = "buildContents")
	private void createBiotech$buildSectionedContents(CreativeModeTab.ItemDisplayParameters parameters,
		Operation<Void> original) {
		CreativeModeTab self = (CreativeModeTab) (Object) this;
		if (!CBCreativeModeTabs.isMainTab(self)) {
			original.call(parameters);
			return;
		}

		displayItems = new LinkedList<>();
		displayItemsSearchTab = new LinkedHashSet<>();
		CBCreativeModeTabs.buildMainTabContents(displayItems, displayItemsSearchTab);
	}
}
