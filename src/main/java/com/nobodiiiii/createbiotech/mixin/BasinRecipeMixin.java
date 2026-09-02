package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Marks Create's basin recipe pass as an authorised captured-slime item mover.
 *
 * <p>{@code BasinRecipe} reads and extracts ingredients through the basin's item capability - the
 * same handler hoppers and pipes see - so {@code BasinInventoryMixin} cannot tell the two apart on
 * its own. Both {@code match} and {@code apply} funnel through this private overload, so scoping it
 * covers ingredient extraction and, transitively, {@code acceptOutputs}.</p>
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
