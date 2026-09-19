package com.nobodiiiii.createbiotech.content.bionicmechanism;

import java.util.Objects;
import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Bridges hand rendering and GUI hover state to the item renderer. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicMechanismHoverTracker {
	private static final long FIRST_PERSON_RENDER_WINDOW_NANOS = 1_000_000_000L;
	private static Supplier<ItemStack> recipeViewerHoveredStack = () -> ItemStack.EMPTY;
	private static Screen cachedScreen;
	private static ItemStack cachedContainerStack = ItemStack.EMPTY;
	private static ItemStack cachedRecipeViewerStack = ItemStack.EMPTY;
	private static ItemStack firstPersonHandStack = ItemStack.EMPTY;
	private static long firstPersonHandRenderTime;

	private BionicMechanismHoverTracker() {
	}

	public static boolean isHovered(ItemStack renderedStack) {
		if (Minecraft.getInstance().screen != cachedScreen)
			return false;
		if (renderedStack == cachedContainerStack)
			return true;
		return !cachedRecipeViewerStack.isEmpty()
			&& ItemStack.isSameItemSameComponents(renderedStack, cachedRecipeViewerStack);
	}

	public static boolean isRenderingFirstPersonHand(ItemStack renderedStack) {
		long elapsed = System.nanoTime() - firstPersonHandRenderTime;
		return renderedStack == firstPersonHandStack
			&& elapsed >= 0
			&& elapsed <= FIRST_PERSON_RENDER_WINDOW_NANOS;
	}

	@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		firstPersonHandStack = event.getItemStack();
		firstPersonHandRenderTime = System.nanoTime();
	}

	@SubscribeEvent
	public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
		cachedScreen = event.getScreen();
		cachedContainerStack = ItemStack.EMPTY;
		if (cachedScreen instanceof AbstractContainerScreen<?> containerScreen) {
			Slot hoveredSlot = containerScreen.getSlotUnderMouse();
			if (hoveredSlot != null && hoveredSlot.hasItem())
				cachedContainerStack = hoveredSlot.getItem();
		}
		cachedRecipeViewerStack = recipeViewerHoveredStack.get();
	}

	public static void setRecipeViewerHoveredStack(Supplier<ItemStack> hoveredStack) {
		recipeViewerHoveredStack = Objects.requireNonNull(hoveredStack);
	}

	public static void clearRecipeViewerHoveredStack() {
		recipeViewerHoveredStack = () -> ItemStack.EMPTY;
		cachedRecipeViewerStack = ItemStack.EMPTY;
	}
}
