package com.nobodiiiii.createbiotech.foundation.ponder;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.ChassisScenes;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers scenes that intentionally use Create's schematic and localization namespace.
 */
public class CreatePonderAliasPlugin implements PonderPlugin {

	@Override
	public String getModId() {
		return Create.ID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		helper.forComponents(CBItems.SMART_SUPER_GLUE.getId())
			.addStoryBoard("super_glue", ChassisScenes::superGlue, AllCreatePonderTags.CONTRAPTION_ASSEMBLY);
	}
}
