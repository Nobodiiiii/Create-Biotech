package com.nobodiiiii.createbiotech.content.bionicmechanism;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Bridges vanilla container and optional recipe-viewer hover state to the item renderer. */
public final class BionicMechanismHoverTracker {
	private static Supplier<ItemStack> recipeViewerHoveredStack = () -> ItemStack.EMPTY;

	private BionicMechanismHoverTracker() {
	}

	public static boolean isHovered(ItemStack renderedStack) {
		if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen) {
			Slot hoveredSlot = containerScreen.getSlotUnderMouse();
			if (hoveredSlot != null && hoveredSlot.getItem() == renderedStack)
				return true;
		}

		ItemStack hoveredStack = recipeViewerHoveredStack.get();
		return !hoveredStack.isEmpty() && ItemStack.isSameItemSameComponents(renderedStack, hoveredStack);
	}

	public static void setRecipeViewerHoveredStack(Supplier<ItemStack> hoveredStack) {
		recipeViewerHoveredStack = Objects.requireNonNull(hoveredStack);
	}

	public static void clearRecipeViewerHoveredStack() {
		recipeViewerHoveredStack = () -> ItemStack.EMPTY;
	}
}
