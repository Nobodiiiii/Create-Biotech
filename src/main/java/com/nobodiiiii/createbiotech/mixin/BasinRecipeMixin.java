package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Routes Create's own recipe pass to the basin's unfiltered internal inventory.
 *
 * <p>{@code BasinRecipe} normally obtains the same public capability as hoppers and pipes. That
 * boundary deliberately hides captured-slime control items, while recipes must treat them as
 * ordinary ingredients. Both matching and application pass through this private overload.</p>
 */
@Mixin(BasinRecipe.class)
public abstract class BasinRecipeMixin {

	@WrapOperation(
		method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;",
			ordinal = 0))
	private static Object createBiotech$useInternalItemInventory(Level level,
		BlockCapability<?, ?> capability, BlockPos pos, Object context, Operation<Object> original,
		BasinBlockEntity basin, Recipe<?> recipe, boolean test) {
		return BasinEntityProcessing.getInternalItemHandler(basin);
	}
}
