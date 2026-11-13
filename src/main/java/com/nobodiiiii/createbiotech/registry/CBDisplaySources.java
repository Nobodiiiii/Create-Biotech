package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterTargetAddressDisplaySource;
import com.simibubi.create.AllDisplaySources;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CBDisplaySources {
	private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
		DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, CreateBiotech.MOD_ID);

	public static final DeferredHolder<DisplaySource, ShulkerTeleporterTargetAddressDisplaySource> SHULKER_TELEPORTER_TARGET_ADDRESS =
		DISPLAY_SOURCES.register("shulker_teleporter_target_address", ShulkerTeleporterTargetAddressDisplaySource::new);

	private static boolean registered;

	private CBDisplaySources() {}

	public static void register(IEventBus modEventBus) {
		DISPLAY_SOURCES.register(modEventBus);
	}

	public static void register() {
		if (registered)
			return;
		DisplaySource.BY_BLOCK.add(CBBlocks.SLIME_BELT.get(), AllDisplaySources.ITEM_NAMES.get());
		DisplaySource.BY_BLOCK.add(CBBlocks.MAGMA_BELT.get(), AllDisplaySources.ITEM_NAMES.get());
		DisplaySource.BY_BLOCK.add(CBBlocks.SHULKER_TELEPORTER.get(), SHULKER_TELEPORTER_TARGET_ADDRESS.get());
		registered = true;
	}
}
