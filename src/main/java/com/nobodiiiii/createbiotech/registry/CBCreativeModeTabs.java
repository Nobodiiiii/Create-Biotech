package com.nobodiiiii.createbiotech.registry;

import java.util.HashSet;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CBCreativeModeTabs {

	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBiotech.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.create_biotech.main"))
			.withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
			.icon(() -> CBItems.SPIDER_ASSEMBLY_TABLE.get()
				.getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(CBItems.CARDBOARD_BOX.get());
				output.accept(CBItems.LARGE_CARDBOARD_BOX.get());
				output.accept(CBItems.SURGICAL_KIT.get());
				output.accept(CBItems.SMART_SUPER_GLUE.get());
				output.accept(CBItems.NECK_JOINT.get());
				output.accept(CBItems.SHOULDER_JOINT.get());
				output.accept(CBItems.ELBOW_JOINT.get());
				output.accept(CBItems.HIP_JOINT.get());
				output.accept(CBItems.KNEE_JOINT.get());
				output.accept(CBItems.WIRELESS_TERMINAL.get());
				output.accept(CBItems.SONIC_DOG_CANNON.get());
				output.accept(CBItems.DING_DONG_CHICKEN.get());
				output.accept(CBItems.CAPTURED_SMALL_SLIME.get());
				output.accept(CBItems.BIO_PACKAGER.get());
				output.accept(CBItems.SHULKER_PACKAGER.get());
				output.accept(CBItems.SHULKER_TELEPORTER.get());
				output.accept(CBItems.ALLAY_PORT.get());
				output.accept(CBItems.ALLAY_COURIER.get());
				output.accept(CBItems.HALF_SHAFT.get());
				output.accept(CBItems.UNIVERSAL_JOINT.get());
				output.accept(CBItems.SLIME_CLUTCH.get());
				output.accept(CBItems.BONE_RATCHET.get());
				output.accept(CBItems.SPIDER_ASSEMBLY_TABLE.get());
				output.accept(CBItems.EMPTY_MAGMA_CUBE_BURNER.get());
				output.accept(CBItems.MAGMA_CUBE_BURNER.get());
				output.accept(CBItems.AUTOMATIC_FISH_RELEASE_MACHINE.get());
				output.accept(CBItems.CUTE_CAT_ON_SHAFT.get(), TabVisibility.SEARCH_TAB_ONLY);
				output.accept(CBItems.BUTTER_CAT_ENGINE.get());
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
				output.accept(CBItems.ASURINE_TABLE_CLOTH.get());
				output.accept(CBItems.PETRI_DISH.get());
				output.accept(CBItems.SURGICAL_TABLE.get());
				output.accept(CBItems.SCHRODINGERS_CAT.get());
				output.accept(CBItems.ASURINE_ALLOY.get());
				output.accept(CBItems.CARBON_POWDER.get());
				output.accept(CBItems.GRAPHITE.get());
				output.accept(CBItems.ZINC_SHEET.get());
				output.accept(CBItems.BIONIC_MECHANISM.get());
				output.accept(CBItems.ENCHANTMENT_BOOK_COPY.get());
				output.accept(CBItems.BUTTER.get());
				output.accept(CBItems.SUPER_BUTTER.get());
				output.accept(CBItems.BUTTER_BLOCK.get());
				output.accept(CBItems.SUPER_BUTTER_BLOCK.get());
				output.accept(CBItems.SLIME_HELMET.get());
				output.accept(CBItems.SLIME_CHESTPLATE.get());
				output.accept(CBItems.SLIME_LEGGINGS.get());
				output.accept(CBItems.SLIME_BOOTS.get());
				output.accept(CBFluids.TELEPORTATION_BUCKET.get());
				output.accept(CBFluids.LIQUID_LIVING_SLIME_BUCKET.get());
				output.accept(CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get());
				output.accept(CBFluids.CREAM_BUCKET.get());
				output.accept(CBItems.DING_DONG_CHICKEN_SPAWN_EGG.get());
				output.accept(CBItems.GIANT_FROG.get());
				output.accept(CBItems.FROG_STOMACH_WALL.get(), TabVisibility.SEARCH_TAB_ONLY);
				output.accept(CBItems.FROG_STOMACH_MUCOSA.get());
				output.accept(CBItems.FROG_STOMACH_FUNGUS.get());
				output.accept(CBItems.FROG_STOMACH_SECRETION.get());
				output.accept(CBItems.FROG_DIGESTIVE_TRACT.get(), TabVisibility.SEARCH_TAB_ONLY);
				output.accept(CBItems.FROG_DIGESTIVE_TRACT_WALL.get(), TabVisibility.SEARCH_TAB_ONLY);
			})
			.build());

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LARGE_CARDBOARD_BOXES =
		CREATIVE_MODE_TABS.register("large_cardboard_boxes",
			() -> CreativeModeTab.builder()
				.title(Component.translatable("itemGroup.create_biotech.large_cardboard_boxes"))
				.withTabsBefore(MAIN.getKey())
				.icon(() -> CapturedEntityBoxHelper.createFilledBox(CBItems.LARGE_CARDBOARD_BOX.get(),
					EntityType.CREEPER))
				.displayItems(CBCreativeModeTabs::acceptLargeCardboardBoxes)
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

	private static void acceptLargeCardboardBoxes(ItemDisplayParameters parameters, Output output) {
		output.accept(CBItems.CARDBOARD_BOX.get());
		output.accept(CBItems.LARGE_CARDBOARD_BOX.get());

		Set<EntityType<?>> addedEntityTypes = new HashSet<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (!(item instanceof SpawnEggItem spawnEggItem) || !item.isEnabled(parameters.enabledFeatures()))
				continue;

			EntityType<?> entityType = spawnEggItem.getType(item.getDefaultInstance());
			if (entityType != null && addedEntityTypes.add(entityType))
				output.accept(CapturedEntityBoxHelper.createFilledBox(CBItems.LARGE_CARDBOARD_BOX.get(), entityType));
		}
	}

	public static void register(IEventBus modEventBus) {
		CREATIVE_MODE_TABS.register(modEventBus);
	}
}
