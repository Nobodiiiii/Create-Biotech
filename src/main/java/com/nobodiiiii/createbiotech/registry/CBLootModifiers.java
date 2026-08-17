package com.nobodiiiii.createbiotech.registry;

import com.mojang.serialization.Codec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.loot.AddTableLootModifier;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CBLootModifiers {

	private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
		DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateBiotech.MOD_ID);

	public static final RegistryObject<Codec<AddTableLootModifier>> ADD_TABLE =
		LOOT_MODIFIERS.register("add_table", () -> AddTableLootModifier.CODEC);

	private CBLootModifiers() {}

	public static void register(IEventBus modEventBus) {
		LOOT_MODIFIERS.register(modEventBus);
	}
}
