package com.nobodiiiii.createbiotech.infrastructure.ponder;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.content.buttercat.ponder.ModPonderScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

public class AllCreateBiotechPonderScenes {

	public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<RegistryObject<?>> HELPER = helper.withKeyFunction(RegistryObject::getId);
		HELPER.forComponents(CBItems.BUTTER_CAT_ENGINE)
			.addStoryBoard("butter_cat_engine", ModPonderScenes::butterCatEngine,
				AllCreatePonderTags.KINETIC_SOURCES);
	}
}
