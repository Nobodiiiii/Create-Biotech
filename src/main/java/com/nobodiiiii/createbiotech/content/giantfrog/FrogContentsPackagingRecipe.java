package com.nobodiiiii.createbiotech.content.giantfrog;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A cardboard step of the Giant Frog Factory assembly. The declared empty package output keeps the
 * byproduct visible in recipe viewers; at processing time it is replaced by a package containing
 * randomized blocks drawn from the current Frog Stomach ecology.
 */
public class FrogContentsPackagingRecipe extends DeployerApplicationRecipe {

	private static final Item[] FROGLIGHTS = {
		Items.OCHRE_FROGLIGHT,
		Items.PEARLESCENT_FROGLIGHT,
		Items.VERDANT_FROGLIGHT
	};

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
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_MUCOSA.get(), 40, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_FOLD.get(), 16, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_FUNGUS.get(), 8, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_FUNGUS_STEM.get(), 18, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_FUNGUS_CAP.get(), 24, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_FUNGUS_GILLS.get(), 18, random);
		slot = addRandomStack(contents, slot, CBItems.FROG_STOMACH_SECRETION.get(), 16, random);
		slot = addRandomStack(contents, slot, Items.SLIME_BLOCK, 10, random);
		addRandomStack(contents, slot, FROGLIGHTS[random.nextInt(FROGLIGHTS.length)], 8, random);
		return PackageItem.containing(contents);
	}

	private static int addRandomStack(ItemStackHandler contents, int slot, Item item, int maximum,
		RandomSource random) {
		int count = random.nextInt(maximum + 1);
		if (count == 0)
			return slot;
		contents.setStackInSlot(slot, new ItemStack(item, count));
		return slot + 1;
	}
}
