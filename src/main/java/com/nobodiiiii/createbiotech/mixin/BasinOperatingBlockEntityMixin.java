package com.nobodiiiii.createbiotech.mixin;

import java.util.Optional;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.foundation.recipe.trie.AbstractVariant;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Lets Create's recipe index see the basin's hidden control items. */
@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
public abstract class BasinOperatingBlockEntityMixin {

	@Shadow
	protected abstract Optional<BasinBlockEntity> getBasin();

	@WrapOperation(
		method = "getMatchingRecipes()Ljava/util/List;",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/foundation/recipe/trie/RecipeTrie;getVariants(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;)Ljava/util/Set;"))
	private Set<AbstractVariant> createBiotech$indexInternalBasinItems(IItemHandler publicItems,
		IFluidHandler fluids, Operation<Set<AbstractVariant>> original) {
		IItemHandler items = getBasin()
			.<IItemHandler>map(BasinEntityProcessing::getInternalItemHandler)
			.orElse(publicItems);
		return original.call(items, fluids);
	}
}
