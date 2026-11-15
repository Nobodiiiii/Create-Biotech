package com.nobodiiiii.createbiotech.foundation.ponder;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.infrastructure.ponder.AllCreateBiotechPonderTags;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreateBiotechPonderPlugin implements PonderPlugin {

	@Override
	public String getModId() {
		return CreateBiotech.MOD_ID;
	}

	@Override
	public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
		AllCreateBiotechPonderTags.register(helper);
	}
}
