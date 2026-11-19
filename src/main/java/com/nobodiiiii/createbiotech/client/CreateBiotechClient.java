package com.nobodiiiii.createbiotech.client;

import java.io.IOException;

import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineVisual;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingMachineVisual;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestScreen;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineVisual;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerVisual;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberVisual;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderManager;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxPartials;
import com.nobodiiiii.createbiotech.content.capturenet.client.CaptureNetItemDecorator;
import com.nobodiiiii.createbiotech.content.capturenet.client.CaptureNetRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonMagnetSnapOverlay;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonEntity;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterScreen;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogMachineVisual;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltVisual;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerVisual;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltVisual;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableClientHandler;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableInteractionOverlay;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableModel;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalKitItemDecorator;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalJointItemDecorator;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderInterfaces;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagePartials;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerVisual;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterScreen;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltVisual;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCasingModel;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableScreen;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableVisual;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterVisual;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftVisual;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointVisual;
import com.nobodiiiii.createbiotech.entity.SlimeBionicRenderer;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestScreen;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortScreen;
import com.nobodiiiii.createbiotech.content.allay.client.gui.hud.AllayCourierHudOverlay;
import com.nobodiiiii.createbiotech.content.allay.client.render.AllayPortVisual;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierScreen;
import com.nobodiiiii.createbiotech.foundation.ponder.CreateBiotechPonderPlugin;
import com.nobodiiiii.createbiotech.foundation.ponder.CreatePonderCompatPlugin;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;

import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;

import net.createmod.ponder.foundation.PonderIndex;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public class CreateBiotechClient {

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		CBClientRenderRegistration.registerRenderers(event);
	}

	@SubscribeEvent
	public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
		CBClientRenderRegistration.addEntityLayers(event);
	}

	@SubscribeEvent
	public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
		CBClientModelRegistration.registerAdditionalModels(event);
	}

	@SubscribeEvent
	public static void registerMenuScreens(RegisterMenuScreensEvent event) {
		SurgicalPonderInterfaces.registerScreens(event);
		event.register(CBMenuTypes.GREENPRINT_TABLE.get(), com.nobodiiiii.createbiotech.content.slimemimic.GreenprintTableScreen::new);
		event.register(CBMenuTypes.SPIDER_ASSEMBLY_TABLE.get(), SpiderAssemblyTableScreen::new);
		event.register(CBMenuTypes.BIONIC_CARGO.get(), com.nobodiiiii.createbiotech.entity.inventory.BionicCargoScreen::new);
		event.register(CBMenuTypes.ALLAY_PORT.get(), AllayPortScreen::new);
		event.register(CBMenuTypes.ALLAY_COURIER.get(), AllayCourierScreen::new);
		event.register(CBMenuTypes.SHULKER_TELEPORTER.get(), ShulkerTeleporterScreen::new);
		event.register(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get(), HoneycombGaugeClusterScreen::new);
		registerWirelessStockKeeperScreen(event);
		registerEndermanStockKeeperScreen(event);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerWirelessStockKeeperScreen(RegisterMenuScreensEvent event) {
		event.register((net.minecraft.world.inventory.MenuType) CBMenuTypes.WIRELESS_STOCK_KEEPER_REQUEST.get(),
			(net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor) (menu, inventory, title) ->
				new WirelessStockKeeperRequestScreen((WirelessStockKeeperRequestMenu) menu, inventory, title));
	}

	@SubscribeEvent
	public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.HOTBAR, CreateBiotech.asResource("ghast_balloon_magnet_prompt"),
			GhastBalloonMagnetSnapOverlay.INSTANCE);
		event.registerAbove(VanillaGuiLayers.HOTBAR, CreateBiotech.asResource("allay_courier_eta"),
			AllayCourierHudOverlay.INSTANCE);
		event.registerAbove(VanillaGuiLayers.HOTBAR, CreateBiotech.asResource("surgical_table_interaction"),
			SurgicalTableInteractionOverlay.INSTANCE);
	}

	@SubscribeEvent
	public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
		CBClientModelRegistration.modifyBakedModels(event);
	}

	@SubscribeEvent
	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		SlimeBeltSpriteShifts.init();
		MagmaBeltSpriteShifts.init();
		PowerBeltSpriteShifts.init();
		CBSpriteShifts.init();
		CBClientModelRegistration.registerCustomBlockModels();
		event.registerReloadListener(new ResourceManagerReloadListener() {
			@Override
			public void onResourceManagerReload(ResourceManager resourceManager) {
				CapturedEntityRenderManager.clearForResourceReload();
				SurgicalTableClientHandler.clear();
				SurgicalTableModel.reload();
				SurgicalSourceModelRenderer.clear();
				SlimeBionicRenderer.clearCache();
				SpiderAssemblyTableCasingModel.clearTextureCache();
			}
		});
		event.registerReloadListener(SlimeBeltHelper.LISTENER);
		event.registerReloadListener(MagmaBeltHelper.LISTENER);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerEndermanStockKeeperScreen(RegisterMenuScreensEvent event) {
		event.register((net.minecraft.world.inventory.MenuType) CBMenuTypes.ENDERMAN_STOCK_KEEPER_REQUEST.get(),
			(net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor) (menu, inventory, title) ->
				new EndermanStockKeeperRequestScreen((EndermanStockKeeperRequestMenu) menu, inventory, title));
	}

	@SubscribeEvent
	public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		CBClientRenderRegistration.registerParticleProviders(event);
	}

	@SubscribeEvent
	public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		CBClientRenderRegistration.registerItemColors(event);
	}

	@SubscribeEvent
	public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		CBClientRenderRegistration.registerClientExtensions(event);
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry
				.addClientCatalogListener(SurgicalSourceModelRenderer::clear);
			CBClientTooltipRegistration.registerItemTooltips();
			CBClientModelRegistration.registerCardboardBoxModelProperties();
			CBClientModelRegistration.registerSurgicalKitModelProperties();
			PonderIndex.addPlugin(new CreateBiotechPonderPlugin());
			PonderIndex.addPlugin(new CreatePonderCompatPlugin());
			CardboardBoxPartials.register();
			ShulkerPackagePartials.register();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.EXPERIENCE_PUMP.get())
				.factory(SingleAxisRotatingVisual.ofZ(ExperiencePumpRenderer.COG))
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.AUTOMATIC_FISH_RELEASE_MACHINE.get())
				.factory(AutomaticFishReleaseMachineVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.SQUID_PRINTER.get())
				.factory(SquidPrinterVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get())
				.factory(EvokerEnchantingMachineVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get())
				.factory(CreeperBlastChamberVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.GIANT_FROG.get())
				.factory(GiantFrogMachineVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.MAGMA_CUBE_BURNER.get())
				.factory(MagmaCubeBurnerVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get())
				.factory(SpiderAssemblyTableVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.MAGMA_BELT.get())
				.factory(MagmaBeltVisual::new)
				.skipVanillaRender(be -> !be.shouldRenderNormally())
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.SLIME_BELT.get())
				.factory(SlimeBeltVisual::new)
				.skipVanillaRender(be -> !be.shouldRenderNormally())
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.POWER_BELT.get())
				.factory(PowerBeltVisual::new)
				.skipVanillaRender(be -> true)
				.apply();
			SimpleEntityVisualizer.<GhastHotAirBalloonEntity>builder(CBEntityTypes.GHAST_HOT_AIR_BALLOON.get())
				.factory(ContraptionVisual::new)
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.BIO_PACKAGER.get())
				.factory(BioPackagerVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.SHULKER_PACKAGER.get())
				.factory(ShulkerPackagerVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.ALLAY_PORT.get())
				.factory(AllayPortVisual::new)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.BUTTER_CAT_ENGINE.get())
				.factory(ButterCatEngineVisual::new)
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.BONE_RATCHET.get())
				.factory((context, blockEntity, partialTick) -> new EncasedCogVisual(context, blockEntity, false,
					partialTick, Models.partial(BoneRatchetRenderer.COGWHEEL)))
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.SLIME_CLUTCH.get())
				.factory(SplitShaftVisual::new)
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.HALF_SHAFT.get())
				.factory(HalfShaftVisual::new)
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.UNIVERSAL_JOINT.get())
				.factory(UniversalJointVisual::new)
				.neverSkipVanillaRender()
				.apply();
			ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME_FLOWING.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.TELEPORTATION.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.TELEPORTATION_FLOWING.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.CREAM.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.CREAM_FLOWING.get(), RenderType.translucent());
			CreateClient.CASING_CONNECTIVITY.makeCasing(CBBlocks.ASURINE_CASING.get(),
				CBSpriteShifts.ASURINE_CASING);
			CreateClient.CASING_CONNECTIVITY.makeCasing(CBBlocks.BIOTECH_CASING.get(),
				CBSpriteShifts.BIOTECH_CASING);
			CreateClient.CASING_CONNECTIVITY.makeCasing(CBBlocks.EXPLOSION_PROOF_CASING.get(),
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE);
			CreateClient.CASING_CONNECTIVITY.make(CBBlocks.CREEPER_BLAST_CHAMBER.get(),
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE,
				(state, face) -> state.hasProperty(CreeperBlastChamberBlock.FORMED)
					&& state.getValue(CreeperBlastChamberBlock.FORMED));
			CreateClient.CASING_CONNECTIVITY.make(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get(),
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE,
				(state, face) -> face.getAxis() != state.getValue(BlockStateProperties.AXIS));
		});
	}

	@SubscribeEvent
	public static void registerShaders(RegisterShadersEvent event) throws IOException {
		CaptureNetRenderer.registerShaders(event);
	}

	@SubscribeEvent
	public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
		event.register(CBItems.WOODEN_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.STONE_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.IRON_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.GOLDEN_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.DIAMOND_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.NETHERITE_CAPTURE_NET.get(), CaptureNetItemDecorator.INSTANCE);
		event.register(CBItems.SURGICAL_JOINT.get(), SurgicalJointItemDecorator.INSTANCE);
		event.register(CBItems.SURGICAL_KIT.get(), SurgicalKitItemDecorator.INSTANCE);
		event.register(CBItems.CREATIVE_SURGICAL_KIT.get(), SurgicalKitItemDecorator.INSTANCE);
	}
}
