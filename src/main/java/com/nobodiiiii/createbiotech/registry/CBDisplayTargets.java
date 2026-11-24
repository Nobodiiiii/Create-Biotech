package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerDisplayTarget;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CBDisplayTargets {
	private static final DeferredRegister<DisplayTarget> DISPLAY_TARGETS =
		DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, CreateBiotech.MOD_ID);

	public static final DeferredHolder<DisplayTarget, ComputerDisplayTarget> COMPUTER_INPUT =
		DISPLAY_TARGETS.register("computer_input", ComputerDisplayTarget::new);

	private static boolean registered;

	private CBDisplayTargets() {}

	public static void register(IEventBus modEventBus) {
		DISPLAY_TARGETS.register(modEventBus);
	}

	public static void register() {
		if (registered)
			return;
		DisplayTarget.BY_BLOCK.register(CBBlocks.COMPUTER.get(), COMPUTER_INPUT.get());
		DisplayTarget.BY_BLOCK.register(CBBlocks.CREATIVE_COMPUTER.get(), COMPUTER_INPUT.get());
		registered = true;
	}
}
