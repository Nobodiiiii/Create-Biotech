package com.nobodiiiii.createbiotech.content.giantfrog;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A cardboard step of the Giant Frog Factory assembly. The declared empty package output keeps the
 * byproduct visible in recipe viewers; at processing time it is replaced by a package containing
 * randomized blocks drawn from the current Frog Stomach ecology.
 */
public class FrogContentsPackagingRecipe extends DeployerApplicationRecipe {
	private static final ResourceLocation CONTENTS_PROFILE =
		CreateBiotech.asResource("giant_frog_factory");

	public FrogContentsPackagingRecipe(ItemApplicationRecipeParams params) {
		super(params);
	}

	@Override
	public List<ItemStack> rollResults(List<ProcessingOutput> rollableResults, RandomSource random) {
		List<ItemStack> rolled = new ArrayList<>(super.rollResults(rollableResults, random));
		rolled.removeIf(PackageItem::isPackage);
		rolled.add(createFrogContentsPackage(random));
		return rolled;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.FROG_CONTENTS_PACKAGING_SERIALIZER.get();
	}

	private static ItemStack createFrogContentsPackage(RandomSource random) {
		ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
		int slot = 0;
		for (ItemStack stack : FrogPackageContentsReloadListener.INSTANCE.roll(CONTENTS_PROFILE, random)) {
			if (slot >= PackageItem.SLOTS)
				break;
			contents.setStackInSlot(slot++, stack);
		}
		return PackageItem.containing(contents);
	}
}
