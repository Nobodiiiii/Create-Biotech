package com.nobodiiiii.createbiotech.infrastructure.ponder;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.ChassisScenes;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class AllCreateBiotechPonderScenes {

	private static final ResourceLocation CREATE_SUPER_GLUE_SCHEMATIC =
		ResourceLocation.fromNamespaceAndPath("create", "super_glue");

	public static void registerCreateCompat(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<DeferredHolder<?, ?>> HELPER = helper.withKeyFunction(DeferredHolder::getId);
		// Keep Create's language-key-based storyboard outside RuntimeScenes.adopt,
		// which makes Lib treat scene text and titles as already-localized literals.
		HELPER.forComponents(CBItems.SMART_SUPER_GLUE)
			.addStoryBoard(CREATE_SUPER_GLUE_SCHEMATIC, ChassisScenes::superGlue,
				AllCreatePonderTags.CONTRAPTION_ASSEMBLY);
	}
}
