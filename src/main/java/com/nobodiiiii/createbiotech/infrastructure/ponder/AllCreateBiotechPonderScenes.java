package com.nobodiiiii.createbiotech.infrastructure.ponder;

import com.nododiiiii.ponderer.runtime.api.RuntimeScenes;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.content.buttercat.ponder.ModPonderScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.ChassisScenes;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class AllCreateBiotechPonderScenes {

	private static final ResourceLocation CREATE_SUPER_GLUE_SCHEMATIC =
		ResourceLocation.fromNamespaceAndPath("create", "super_glue");
	private static final ResourceLocation BUTTER_CAT_ENGINE_SCHEMATIC =
		CreateBiotech.asResource("butter_cat_engine");

	public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<DeferredHolder<?, ?>> HELPER = helper.withKeyFunction(DeferredHolder::getId);
		HELPER.forComponents(CBItems.BUTTER_CAT_ENGINE)
			.addStoryBoard(BUTTER_CAT_ENGINE_SCHEMATIC, ModPonderScenes::butterCatEngine,
				AllCreatePonderTags.KINETIC_SOURCES);
	}

	public static void registerCreateCompat(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<DeferredHolder<?, ?>> HELPER = helper.withKeyFunction(DeferredHolder::getId);
		HELPER.forComponents(CBItems.SMART_SUPER_GLUE)
			.addStoryBoard(CREATE_SUPER_GLUE_SCHEMATIC, (scene, util) -> {
				RuntimeScenes.adopt(scene.getScene());
				ChassisScenes.superGlue(scene, util);
			},
				AllCreatePonderTags.CONTRAPTION_ASSEMBLY);
	}
}
