package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.content.biocomputing.client.ComputerScratchScreen;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ComputerScratchScreen.ItemDropTarget;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

final class ComputerGhostIngredientHandler implements IGhostIngredientHandler<ComputerScratchScreen> {
	@Override
	public <I> List<Target<I>> getTargetsTyped(ComputerScratchScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
		if (ingredient.getItemStack().filter(stack -> !stack.isEmpty()).isEmpty())
			return List.of();
		return screen.itemDropTargets().stream().<Target<I>>map(ItemTarget::new).toList();
	}

	@Override
	public void onComplete() {}

	private record ItemTarget<I>(ItemDropTarget target) implements Target<I> {
		@Override public Rect2i getArea() { return target.area(); }

		@Override
		public void accept(I ingredient) {
			if (ingredient instanceof ItemStack stack)
				target.accept().accept(stack);
		}
	}
}
