package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
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
			.icon(() -> CBItems.SPIDER_ASSEMBLY_TABLE.get()
				.getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(CBItems.CARDBOARD_BOX.get());
				output.accept(CBItems.LARGE_CARDBOARD_BOX.get());
				output.accept(CBItems.SCHRODINGERS_CAT.get());
				output.accept(CBItems.SMART_SUPER_GLUE.get());
				output.accept(CBItems.WIRELESS_TERMINAL.get());
				output.accept(CBItems.BIO_PACKAGER.get());
				output.accept(CBItems.SHULKER_PACKAGER.get());
				output.accept(CBItems.SHULKER_TELEPORTER.get());
				output.accept(CBItems.UNIVERSAL_JOINT.get());
				output.accept(CBItems.SLIME_CLUTCH.get());
				output.accept(CBItems.BONE_RATCHET.get());
				output.accept(CBItems.SPIDER_ASSEMBLY_TABLE.get());
				output.accept(CBItems.FIXED_CARROT_FISHING_ROD.get());
				output.accept(CBItems.POWER_BELT_CONNECTOR.get());
				output.accept(CBItems.SLIME_BELT_CONNECTOR.get());
				output.accept(CBItems.MAGMA_BELT_CONNECTOR.get());
				output.accept(CBItems.EVOKER_ENCHANTING_CHAMBER.get());
				output.accept(CBItems.SQUID_PRINTER.get());
				output.accept(CBItems.EXPERIENCE_PUMP.get());
				output.accept(CBItems.BUDDING_EXPERIENCE.get());
				output.accept(CBItems.SMALL_EXPERIENCE_BUD.get());
				output.accept(CBItems.MEDIUM_EXPERIENCE_BUD.get());
				output.accept(CBItems.LARGE_EXPERIENCE_BUD.get());
				output.accept(CBItems.EXPERIENCE_CLUSTER.get());
				output.accept(CBItems.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get());
				output.accept(CBItems.GHAST_HELM.get());
				acceptBufferPads(output);
				output.accept(CBItems.CREEPER_BLAST_CHAMBER.get());
				output.accept(CBItems.EXPLOSION_PROOF_CASING.get());
				output.accept(CBItems.EXPLOSION_PROOF_ITEM_VAULT.get());
				output.accept(CBItems.BLAST_PROOF_GLASS.get());
				output.accept(CBItems.BLAST_PROOF_FRAMED_GLASS.get());
				output.accept(CBItems.ASURINE_CASING.get());
				output.accept(CBItems.BIOTECH_CASING.get());
				output.accept(CBItems.PETRI_DISH.get());
				output.accept(CBItems.ASURINE_ALLOY.get());
				output.accept(CBItems.CARBON_POWDER.get());
				output.accept(CBItems.GRAPHITE.get());
				output.accept(CBItems.ZINC_SHEET.get());
				output.accept(CBItems.BIONIC_MECHANISM.get());
				output.accept(CBItems.ENCHANTMENT_BOOK_COPY.get());
				output.accept(CBItems.HALF_SHAFT.get());
				output.accept(CBFluids.LIQUID_LIVING_SLIME_BUCKET.get());
				output.accept(CBItems.BUTTER.get());
				output.accept(CBItems.HONEY_BUTTER.get());
				output.accept(CBItems.INCOMPLETE_SUPER_BUTTER.get());
				output.accept(CBItems.SUPER_BUTTER.get());
				CBFluids.CREAM.getBucket().ifPresent(output::accept);
				output.accept(CBBlocks.CUTE_CAT_ON_SHAFT.get().asItem());
				output.accept(CBBlocks.BUTTER_CAT_ENGINE.get().asItem());
			})
			.build());

	private CBCreativeModeTabs() {}

	private static void acceptBufferPads(Output output) {
		for (DyeColor color : DyeColor.values()) {
			if (color == DyeColor.RED) {
				output.accept(CBItems.BUFFER_PADS.get(color).get());
				continue;
			}
			output.accept(CBItems.BUFFER_PADS.get(color).get(), TabVisibility.SEARCH_TAB_ONLY);
		}
	}

	public static void register(IEventBus modEventBus) {
		CREATIVE_MODE_TABS.register(modEventBus);
	}
}
