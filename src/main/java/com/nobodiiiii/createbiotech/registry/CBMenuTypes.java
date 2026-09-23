package com.nobodiiiii.createbiotech.registry;

import net.minecraft.core.registries.Registries;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterMenu;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterMenu;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableMenu;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortMenu;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierMenu;

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

	private CBMenuTypes() {}

	public static void register(IEventBus modEventBus) {
		MENU_TYPES.register(modEventBus);
	}
}
