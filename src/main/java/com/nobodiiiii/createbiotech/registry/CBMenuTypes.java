package com.nobodiiiii.createbiotech.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterMenu;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterMenu;
import com.nobodiiiii.createbiotech.content.slimemimic.GreenprintTableMenu;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableMenu;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalPonderMenu;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortMenu;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierMenu;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

public class CBMenuTypes {

	public static final DeferredRegister<MenuType<?>> MENU_TYPES =
		DeferredRegister.create(Registries.MENU, CreateBiotech.MOD_ID);

	public static final DeferredHolder<MenuType<?>, MenuType<SpiderAssemblyTableMenu>> SPIDER_ASSEMBLY_TABLE =
		MENU_TYPES.register("spider_assembly_table", () -> IMenuTypeExtension.create(SpiderAssemblyTableMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<WirelessStockKeeperRequestMenu>> WIRELESS_STOCK_KEEPER_REQUEST =
		MENU_TYPES.register("wireless_stock_keeper_request",
			() -> IMenuTypeExtension.create(WirelessStockKeeperRequestMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<EndermanStockKeeperRequestMenu>> ENDERMAN_STOCK_KEEPER_REQUEST =
		MENU_TYPES.register("enderman_stock_keeper_request",
			() -> IMenuTypeExtension.create(EndermanStockKeeperRequestMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<HoneycombGaugeClusterMenu>> HONEYCOMB_GAUGE_CLUSTER =
		MENU_TYPES.register("honeycomb_gauge_cluster",
			() -> IMenuTypeExtension.create(HoneycombGaugeClusterMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<ShulkerTeleporterMenu>> SHULKER_TELEPORTER =
		MENU_TYPES.register("shulker_teleporter", () -> IMenuTypeExtension.create(ShulkerTeleporterMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<AllayPortMenu>> ALLAY_PORT =
		MENU_TYPES.register("allay_port", () -> IMenuTypeExtension.create(AllayPortMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<AllayCourierMenu>> ALLAY_COURIER =
		MENU_TYPES.register("allay_courier", () -> IMenuTypeExtension.create(AllayCourierMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<com.nobodiiiii.createbiotech.entity.inventory.BionicCargoMenu>> BIONIC_CARGO =
		MENU_TYPES.register("bionic_cargo", () -> IMenuTypeExtension.create(com.nobodiiiii.createbiotech.entity.inventory.BionicCargoMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<GreenprintTableMenu>> GREENPRINT_TABLE =
		MENU_TYPES.register("greenprint_table", () -> IMenuTypeExtension.create(GreenprintTableMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> PONDER_SURGICAL_TOOL_WHEEL =
		ponderPage("ponder_surgical_tool_wheel", () -> CBMenuTypes.PONDER_SURGICAL_TOOL_WHEEL.get(), false);

	public static final DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> PONDER_SURGICAL_JOINT_WHEEL =
		ponderPage("ponder_surgical_joint_wheel", () -> CBMenuTypes.PONDER_SURGICAL_JOINT_WHEEL.get(), false);

	public static final DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> PONDER_SURGICAL_BOX_STATS =
		ponderPage("ponder_surgical_box_stats", () -> CBMenuTypes.PONDER_SURGICAL_BOX_STATS.get(), true);

	public static final DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> PONDER_SURGICAL_BOX_HOVER =
		ponderPage("ponder_surgical_box_hover", () -> CBMenuTypes.PONDER_SURGICAL_BOX_HOVER.get(), false);

	public static final DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> PONDER_SURGICAL_SMART_GLUE_HOVER =
		ponderPage("ponder_surgical_smart_glue_hover", () -> CBMenuTypes.PONDER_SURGICAL_SMART_GLUE_HOVER.get(), false);

	private CBMenuTypes() {}

	private static DeferredHolder<MenuType<?>, MenuType<SurgicalPonderMenu>> ponderPage(
		String id, Supplier<MenuType<SurgicalPonderMenu>> type, boolean showBox) {
		return MENU_TYPES.register(id, () -> new MenuType<>((containerId, inventory) ->
			new SurgicalPonderMenu(type.get(), containerId, showBox), FeatureFlags.VANILLA_SET));
	}

	public static void register(IEventBus modEventBus) {
		MENU_TYPES.register(modEventBus);
	}
}
