package com.nobodiiiii.createbiotech.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers Create: Biotech's creative tabs and lays out the main tab as a sequence of
 * illustrated sections. The section-row layout follows Create: Aeronautics' creative tab
 * design while keeping all section definitions local and dependency-free.
 */
public class CBCreativeModeTabs {

	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBiotech.MOD_ID);
	private static final Map<CBCreativeTabSection, Integer> SECTION_ROWS =
		new EnumMap<>(CBCreativeTabSection.class);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.create_biotech.main"))
			.withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
			.icon(() -> CBItems.SPIDER_ASSEMBLY_TABLE.get()
				.getDefaultInstance())
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

	public static boolean isMainTab(CreativeModeTab tab) {
		return tab == MAIN.get();
	}

	public static int getSectionRow(CBCreativeTabSection section) {
		return SECTION_ROWS.getOrDefault(section, -1);
	}

	/**
	 * Populates the main tab directly so complete empty rows can be reserved for section banners.
	 * Vanilla's creative-tab output deliberately rejects empty stacks, hence the small mixin bridge.
	 */
	public static void buildMainTabContents(Collection<ItemStack> displayItems, Set<ItemStack> searchItems) {
		displayItems.clear();
		searchItems.clear();
		SECTION_ROWS.clear();

		int row = 0;
		for (SectionContents section : createSectionContents()) {
			SECTION_ROWS.put(section.section(), row);
			addEmptyRow(displayItems);

			int visibleItemCount = 0;
			for (TabEntry entry : section.entries()) {
				ItemStack stack = entry.stack();
				searchItems.add(stack);
				if (entry.searchOnly())
					continue;

				displayItems.add(stack);
				visibleItemCount++;
			}

			int itemRows = (visibleItemCount + 8) / 9;
			int padding = itemRows * 9 - visibleItemCount;
			for (int i = 0; i < padding; i++)
				displayItems.add(ItemStack.EMPTY);
			row += itemRows + 1;
		}
	}

	private static List<SectionContents> createSectionContents() {
		List<SectionContents> sections = new ArrayList<>();

		sections.add(section(CBCreativeTabSection.BIOLOGICAL_CAPTURE,
			visible(CBItems.CARDBOARD_BOX.get()),
			visible(CBItems.LARGE_CARDBOARD_BOX.get()),
			visible(CBItems.CAPTURED_SMALL_SLIME.get()),
			visible(CBItems.BIO_PACKAGER.get()),
			visible(CBItems.AUTOMATIC_FISH_RELEASE_MACHINE.get())));

		sections.add(section(CBCreativeTabSection.POWER_TRANSMISSION,
			visible(CBItems.HALF_SHAFT.get()),
			visible(CBItems.UNIVERSAL_JOINT.get()),
			visible(CBItems.SLIME_CLUTCH.get()),
			visible(CBItems.BONE_RATCHET.get()),
			searchOnly(CBItems.CUTE_CAT_ON_SHAFT.get()),
			visible(CBItems.BUTTER_CAT_ENGINE.get()),
			visible(CBItems.FIXED_CARROT_FISHING_ROD.get()),
			visible(CBItems.POWER_BELT_CONNECTOR.get()),
			visible(CBItems.SLIME_BELT_CONNECTOR.get()),
			visible(CBItems.MAGMA_BELT_CONNECTOR.get())));

		sections.add(section(CBCreativeTabSection.LOGISTICS,
			visible(CBItems.WIRELESS_TERMINAL.get()),
			visible(CBItems.SHULKER_PACKAGER.get()),
			visible(CBItems.SHULKER_TELEPORTER.get()),
			visible(CBItems.ALLAY_PORT.get()),
			visible(CBItems.ALLAY_COURIER.get()),
			visible(CBItems.EXPLOSION_PROOF_ITEM_VAULT.get()),
			visible(CBItems.ASURINE_TABLE_CLOTH.get())));

		List<TabEntry> functionalDevices = new ArrayList<>(List.of(
			visible(CBItems.SONIC_DOG_CANNON.get()),
			visible(CBItems.DING_DONG_CHICKEN.get()),
			visible(CBItems.SPIDER_ASSEMBLY_TABLE.get()),
			visible(CBItems.EMPTY_MAGMA_CUBE_BURNER.get()),
			visible(CBItems.MAGMA_CUBE_BURNER.get()),
			visible(CBItems.SCHRODINGERS_CAT.get()),
			visible(CBItems.SLIME_HELMET.get()),
			visible(CBItems.SLIME_CHESTPLATE.get()),
			visible(CBItems.SLIME_LEGGINGS.get()),
			visible(CBItems.SLIME_BOOTS.get()),
			visible(CBItems.DING_DONG_CHICKEN_SPAWN_EGG.get())));
		addBufferPads(functionalDevices);
		sections.add(new SectionContents(CBCreativeTabSection.FUNCTIONAL_DEVICES, functionalDevices));

		sections.add(section(CBCreativeTabSection.STRUCTURES,
			visible(CBItems.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get()),
			visible(CBItems.GHAST_HELM.get()),
			visible(CBItems.CREEPER_BLAST_CHAMBER.get()),
			visible(CBItems.EXPLOSION_PROOF_CASING.get()),
			visible(CBItems.BLAST_PROOF_GLASS.get()),
			visible(CBItems.BLAST_PROOF_FRAMED_GLASS.get()),
			visible(CBItems.ASURINE_CASING.get()),
			visible(CBItems.BIOTECH_CASING.get())));

		sections.add(section(CBCreativeTabSection.ENCHANTMENT,
			visible(CBItems.EVOKER_ENCHANTING_CHAMBER.get()),
			visible(CBItems.SQUID_PRINTER.get()),
			visible(CBItems.EXPERIENCE_PUMP.get()),
			visible(CBItems.BUDDING_EXPERIENCE.get()),
			visible(CBItems.SMALL_EXPERIENCE_BUD.get()),
			visible(CBItems.MEDIUM_EXPERIENCE_BUD.get()),
			visible(CBItems.LARGE_EXPERIENCE_BUD.get()),
			visible(CBItems.EXPERIENCE_CLUSTER.get()),
			visible(CBItems.ENCHANTMENT_BOOK_COPY.get())));

		sections.add(section(CBCreativeTabSection.MATERIALS,
			visible(CBItems.ASURINE_ALLOY.get()),
			visible(CBItems.CARBON_POWDER.get()),
			visible(CBItems.GRAPHITE.get()),
			visible(CBItems.ZINC_SHEET.get()),
			visible(CBItems.BUTTER.get()),
			visible(CBItems.SUPER_BUTTER.get()),
			visible(CBItems.BUTTER_BLOCK.get()),
			visible(CBItems.SUPER_BUTTER_BLOCK.get()),
			visible(CBFluids.TELEPORTATION_BUCKET.get()),
			visible(CBFluids.LIQUID_LIVING_SLIME_BUCKET.get()),
			visible(CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get()),
			visible(CBFluids.CREAM_BUCKET.get())));

		sections.add(section(CBCreativeTabSection.WORK_IN_PROGRESS,
			visible(CBItems.SURGICAL_KIT.get()),
			visible(CBItems.SMART_SUPER_GLUE.get()),
			visible(CBItems.NECK_JOINT.get()),
			visible(CBItems.SHOULDER_JOINT.get()),
			visible(CBItems.ELBOW_JOINT.get()),
			visible(CBItems.HIP_JOINT.get()),
			visible(CBItems.KNEE_JOINT.get()),
			visible(CBItems.PETRI_DISH.get()),
			visible(CBItems.SURGICAL_TABLE.get()),
			visible(CBItems.BIONIC_MECHANISM.get()),
			visible(CBItems.GIANT_FROG.get()),
			searchOnly(CBItems.FROG_STOMACH_WALL.get()),
			visible(CBItems.FROG_STOMACH_MUCOSA.get()),
			visible(CBItems.FROG_STOMACH_FUNGUS.get()),
			visible(CBItems.FROG_STOMACH_SECRETION.get()),
			searchOnly(CBItems.FROG_DIGESTIVE_TRACT.get()),
			searchOnly(CBItems.FROG_DIGESTIVE_TRACT_WALL.get())));

		return sections;
	}

	private static SectionContents section(CBCreativeTabSection section, TabEntry... entries) {
		return new SectionContents(section, List.of(entries));
	}

	private static TabEntry visible(Item item) {
		return new TabEntry(item.getDefaultInstance(), false);
	}

	private static TabEntry searchOnly(Item item) {
		return new TabEntry(item.getDefaultInstance(), true);
	}

	private static void addBufferPads(List<TabEntry> entries) {
		for (DyeColor color : DyeColor.values()) {
			Item item = CBItems.BUFFER_PADS.get(color).get();
			entries.add(color == DyeColor.RED ? visible(item) : searchOnly(item));
		}
	}

	private static void addEmptyRow(Collection<ItemStack> displayItems) {
		for (int i = 0; i < 9; i++)
			displayItems.add(ItemStack.EMPTY);
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

	private record SectionContents(CBCreativeTabSection section, List<TabEntry> entries) {}

	private record TabEntry(ItemStack stack, boolean searchOnly) {}
}
