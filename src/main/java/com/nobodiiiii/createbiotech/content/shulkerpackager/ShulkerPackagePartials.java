package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllPartialModels;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class ShulkerPackagePartials {

	public static final PartialModel BOX = PartialModel.of(CreateBiotech.asResource("item/shulker_package"));
	public static final PartialModel RIGGING = PartialModel.of(ShulkerPackageItem.STYLE.getRiggingModel());

	private ShulkerPackagePartials() {}

	public static void register() {
		ResourceLocation key = ForgeRegistries.ITEMS.getKey(CBItems.SHULKER_PACKAGE.get());
		if (key == null)
			return;

		AllPartialModels.PACKAGES.put(key, BOX);
		AllPartialModels.PACKAGE_RIGGING.put(key, RIGGING);
	}
}
