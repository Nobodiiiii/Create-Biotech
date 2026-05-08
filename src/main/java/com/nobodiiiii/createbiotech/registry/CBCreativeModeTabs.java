package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CBCreativeModeTabs {

	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBiotech.MOD_ID);

	public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.create_biotech.main"))
			.withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
			.icon(() -> CBItems.EVOKER_TANK.get()
				.getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(CBItems.EVOKER_TANK.get());
				output.accept(CBItems.SLIME_BELT_CONNECTOR.get());
				output.accept(CBItems.MAGMA_BELT_CONNECTOR.get());
				output.accept(CBItems.POWER_BELT_CONNECTOR.get());
				output.accept(CBItems.HALF_SHAFT.get());
				output.accept(CBItems.UNIVERSAL_JOINT.get());
				output.accept(CBItems.CARDBOARD_BOX.get());
				output.accept(CBItems.SCHRODINGERS_CAT.get());
				output.accept(CBItems.FIXED_CARROT_FISHING_ROD.get());
				output.accept(CBItems.CREEPER_BLAST_CHAMBER.get());
				output.accept(CBItems.EXPLOSION_PROOF_CASING.get());
				output.accept(CBFluids.LIQUID_LIVING_SLIME_BUCKET.get());
			})
			.build());

	private CBCreativeModeTabs() {}

	public static void register(IEventBus modEventBus) {
		CREATIVE_MODE_TABS.register(modEventBus);
	}
}
