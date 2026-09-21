package com.nobodiiiii.createbiotech.content.itemapplication;

import java.util.Optional;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** Shared runtime access for manual item-application recipes with custom world migration logic. */
public final class RecipeBackedItemApplication {

	private RecipeBackedItemApplication() {}

	public static Optional<ManualApplicationRecipe> find(Level level, ResourceLocation recipeId,
		BlockState processedState, ItemStack processedItem, ItemStack heldItem) {
		RecipeType<Recipe<RecipeWrapper>> type = AllRecipeTypes.ITEM_APPLICATION.getType();
		return level.getRecipeManager()
			.getAllRecipesFor(type)
			.stream()
			.filter(holder -> recipeId.equals(holder.id()))
			.map(holder -> holder.value())
			.filter(ManualApplicationRecipe.class::isInstance)
			.map(ManualApplicationRecipe.class::cast)
			.filter(recipe -> recipe.testBlock(processedState) || recipe.getProcessedItem().test(processedItem))
			.filter(recipe -> recipe.getRequiredHeldItem().test(heldItem))
			.findFirst();
	}

	public static Optional<Block> resultBlock(ManualApplicationRecipe recipe) {
		if (recipe.getRollableResults().isEmpty())
			return Optional.empty();
		ItemStack declaredResult = recipe.getRollableResults().getFirst().getStack();
		return declaredResult.getItem() instanceof BlockItem blockItem
			? Optional.of(blockItem.getBlock()) : Optional.empty();
	}

	public static void consumeHeldItem(ManualApplicationRecipe recipe, Player player, InteractionHand hand,
		ItemStack heldItem) {
		if (recipe.shouldKeepHeldItem() || player.isCreative() || heldItem.has(DataComponents.UNBREAKABLE))
			return;

		if (heldItem.getMaxDamage() > 0) {
			heldItem.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND
				: EquipmentSlot.MAINHAND);
			return;
		}

		ItemStack leftover = heldItem.getCraftingRemainingItem();
		heldItem.shrink(1);
		if (leftover.isEmpty())
			return;
		if (heldItem.isEmpty()) {
			player.setItemInHand(hand, leftover);
			return;
		}
		if (!player.getInventory().add(leftover))
			player.drop(leftover, false);
	}
}
