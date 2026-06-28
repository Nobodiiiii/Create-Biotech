package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.box.PackageItem;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public class CardboardBoxPartials {

	public static final PartialModel SMALL_BOX = PartialModel.of(CreateBiotech.asResource("item/small_cardboard_box"));
	public static final PartialModel LARGE_BOX = PartialModel.of(CreateBiotech.asResource("item/large_cardboard_box"));

	private CardboardBoxPartials() {}

	public static void register() {
		register(CBItems.CARDBOARD_BOX.get(), SMALL_BOX);
		register(CBItems.LARGE_CARDBOARD_BOX.get(), LARGE_BOX);
	}

	private static void register(Item item, PartialModel box) {
		ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
		if (key == null || !(item instanceof PackageItem packageItem))
			return;

		AllPartialModels.PACKAGES.put(key, box);
		AllPartialModels.PACKAGE_RIGGING.put(key, PartialModel.of(packageItem.style.getRiggingModel()));
	}
}
