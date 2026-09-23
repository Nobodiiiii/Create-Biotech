package com.nobodiiiii.createbiotech.compat.jei;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** Adapts Create's stock keeper recipe transfer logic to a custom stock keeper menu type. */
public class StockKeeperTransferHandlerAdapter<M extends StockKeeperRequestMenu>
	implements IUniversalRecipeTransferHandler<M> {

	private final Class<M> menuClass;
	private final MenuType<M> menuType;
	private final StockKeeperTransferHandler delegate;

	public StockKeeperTransferHandlerAdapter(Class<M> menuClass, MenuType<M> menuType,
		StockKeeperTransferHandler delegate) {
		this.menuClass = menuClass;
		this.menuType = menuType;
		this.delegate = delegate;
	}

	@Override
	public Class<? extends M> getContainerClass() {
		return menuClass;
	}

	@Override
	public Optional<MenuType<M>> getMenuType() {
		return Optional.of(menuType);
	}

	@Override
	public @Nullable IRecipeTransferError transferRecipe(M menu, Object recipe, IRecipeSlotsView recipeSlots,
		Player player, boolean maxTransfer, boolean doTransfer) {
		return delegate.transferRecipe(menu, recipe, recipeSlots, player, maxTransfer, doTransfer);
	}
}
