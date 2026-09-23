package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterMenu;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeRecipePlacement;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeRecipePlacement.Ingredient;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeRecipePlacementPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** The JEI + button imports the displayed item ingredients into real Create panels. */
public final class HoneycombGaugeRecipeTransferHandler
	implements IUniversalRecipeTransferHandler<HoneycombGaugeClusterMenu> {
	private final IRecipeTransferHandlerHelper errors;

	public HoneycombGaugeRecipeTransferHandler(IRecipeTransferHandlerHelper errors) {
		this.errors = errors;
	}

	@Override
	public Class<? extends HoneycombGaugeClusterMenu> getContainerClass() {
		return HoneycombGaugeClusterMenu.class;
	}

	@Override
	public Optional<MenuType<HoneycombGaugeClusterMenu>> getMenuType() {
		return Optional.of(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get());
	}

	@Override
	public @Nullable IRecipeTransferError transferRecipe(HoneycombGaugeClusterMenu menu, Object recipe,
		IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
		Slots inputs = read(recipeSlots.getSlotViews(RecipeIngredientRole.INPUT));
		Slots outputs = read(recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT));
		if (inputs.unsupported() || outputs.unsupported()
			|| inputs.ingredients().isEmpty() || outputs.ingredients().isEmpty())
			return error("unsupported");
		var plan = HoneycombGaugeRecipePlacement.plan(player, menu.origin(), menu.facing(),
			menu.workspaceUp(), inputs.ingredients(), outputs.ingredients(), menu.snapshot());
		if (!plan.ready())
			return error(plan.error());
		if (doTransfer)
			CBPackets.sendToServer(new HoneycombGaugeRecipePlacementPacket(menu.origin(),
				inputs.ingredients(), outputs.ingredients()));
		return null;
	}

	private IRecipeTransferError error(String reason) {
		return errors.createUserErrorWithTooltip(HoneycombGaugeRecipePlacement.message(reason));
	}

	private record Slots(List<Ingredient> ingredients, boolean unsupported) {}

	private static Slots read(List<IRecipeSlotView> slots) {
		List<Ingredient> result = new ArrayList<>();
		for (IRecipeSlotView slot : slots) {
			if (slot.isEmpty())
				continue;
			ItemStack stack = slot.getDisplayedItemStack().orElseGet(() ->
				slot.getItemStacks().findFirst().orElse(ItemStack.EMPTY));
			if (stack.isEmpty())
				return new Slots(List.of(), true);
			result.add(new Ingredient(stack.copyWithCount(1), stack.getCount()));
		}
		return new Slots(List.copyOf(result), false);
	}
}
