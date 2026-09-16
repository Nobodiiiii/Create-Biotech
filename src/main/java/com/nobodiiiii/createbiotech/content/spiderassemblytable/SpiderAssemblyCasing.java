package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi.CastedMaterialHolder;
import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi;
import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi.MaterialSetResult;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class SpiderAssemblyCasing {

	private SpiderAssemblyCasing() {
	}

	/** Casing is an appearance choice: installation never consumes the held item. */
	public static MaterialSetResult install(CastedMaterialHolder holder, ItemStack held) {
		if (CastedMaterialsApi.getMaterial(holder).isPresent())
			return MaterialSetResult.UNCHANGED;
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
		return CastedMaterialsApi.setMaterial(holder, id);
	}

	public static MaterialSetResult remove(CastedMaterialHolder holder) {
		return CastedMaterialsApi.clearMaterial(holder);
	}
}
