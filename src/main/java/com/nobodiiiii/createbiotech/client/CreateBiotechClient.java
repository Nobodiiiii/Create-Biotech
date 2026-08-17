package com.nobodiiiii.createbiotech.client;

import java.util.function.Predicate;

import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineRenderer;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberRenderer;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineVisual;
import com.nobodiiiii.createbiotech.content.buttercat.event.RotationHandler;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerRenderer;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerVisual;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetRenderer;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxEntityRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberRenderer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelModel;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderManager;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxPartials;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultCTBehaviour;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonMagnetSnapOverlay;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonEntity;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonEntityRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonSeatEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltVisual;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltRenderer;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltVisual;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishRenderer;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatRenderer;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerConnectionHandler;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagePartials;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerRenderer;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerVisual;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltVisual;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogRenderer;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableRenderer;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRenderer;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftVisual;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.yision.allay.client.gui.hud.AllayCourierHudOverlay;
import com.yision.allay.client.render.AllayCourierEntityRenderer;
import com.yision.allay.client.render.AllayPortRenderer;
import com.yision.allay.client.render.AllayPortVisual;
import com.nobodiiiii.createbiotech.foundation.ponder.CreateBiotechPonderPlugin;
import com.nobodiiiii.createbiotech.client.particle.CourierNoteParticle;
import com.nobodiiiii.createbiotech.client.particle.SquidPrinterInkParticle;
import com.nobodiiiii.createbiotech.client.particle.StraightEnchantParticle;
import com.nobodiiiii.createbiotech.client.render.SlimeMimicRenderLayer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.nobodiiiii.createbiotech.client.CasingConnectedHorizontalCTBehaviour;
import com.simibubi.create.Create;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelVisual;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;

import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateBiotechClient {
	private static boolean customBlockModelsRegistered;

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(CBBlockEntityTypes.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
			AutomaticFishReleaseMachineRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(),
			EvokerEnchantingChamberRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), ExperiencePumpRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SQUID_PRINTER.get(), SquidPrinterRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_BELT.get(), SlimeBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.MAGMA_BELT.get(), MagmaBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.POWER_BELT.get(), PowerBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), UniversalJointRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.HALF_SHAFT.get(), KineticBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_CLUTCH.get(), SplitShaftRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SCHRODINGERS_CAT.get(), SchrodingersCatRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get(),
			SpiderAssemblyTableRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE_COG.get(),
			SpiderAssemblyTableCogRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.FIXED_CARROT_FISHING_ROD.get(),
			FixedCarrotFishingRodRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.BLAST_PROOF_CHAIN_DRIVE.get(),
			BlastProofChainDriveRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(),
			CreeperBlastChamberRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(),
			GhastHotAirBalloonAssemblyStationRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.BIO_PACKAGER.get(), BioPackagerRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SHULKER_PACKAGER.get(), ShulkerPackagerRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SHULKER_TELEPORTER.get(), ShulkerTeleporterRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.BONE_RATCHET.get(), BoneRatchetRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.PETRI_DISH.get(), PetriDishRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.BUTTER_CAT_ENGINE.get(), ButterCatEngineRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.ALLAY_PORT.get(), AllayPortRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.GHAST_HOT_AIR_BALLOON.get(),
			GhastHotAirBalloonEntityRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.GHAST_HOT_AIR_BALLOON_SEAT.get(),
			GhastHotAirBalloonSeatEntity.Render::new);
		event.registerEntityRenderer(CBEntityTypes.CARDBOARD_BOX.get(), CardboardBoxEntityRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.ALLAY_COURIER.get(),
			context -> new AllayCourierEntityRenderer(context));
		event.registerEntityRenderer(CBEntityTypes.CARDBOARD_BOX.get(), CardboardBoxEntityRenderer::new);
	}

	@SubscribeEvent
	public static void addEntityRenderLayers(EntityRenderersEvent.AddLayers event) {
		SlimeMimicRenderLayer.registerOnAll(Minecraft.getInstance().getEntityRenderDispatcher());
	}

	@SubscribeEvent
	public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
		event.register(CreateBiotech.asResource("block/universal_joint_endpoint_slime_overlay"));
		event.register(AutomaticFishReleaseMachineRenderer.BLADE_CLAMP_MODEL_LOCATION);
		event.register(HalfShaftVisual.MODEL.modelLocation());
		event.register(CreateBiotech.asResource("block/blast_chamber_display/panel"));
		event.register(CreateBiotech.asResource("block/blast_chamber_display/dial"));
		event.register(CreateBiotech.asResource("block/blast_chamber_display/creeper_face"));
		event.register(BoneRatchetRenderer.COGWHEEL_MODEL_LOCATION);
		event.register(ExperiencePumpRenderer.COG_MODEL_LOCATION);
		event.register(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_on"));
		event.register(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_off"));
		event.register(CreateBiotech.asResource("block/spider_assembly_table/body"));
		event.register(CreateBiotech.asResource("block/spider_assembly_table/head"));
		event.register(CreateBiotech.asResource("block/spider_assembly_table/abdomen"));
		event.register(CreateBiotech.asResource("block/spider_assembly_table/leg"));
		event.register(CreateBiotech.asResource("block/ghast_helm/block_open"));
		event.register(CreateBiotech.asResource("block/ghast_helm/train/cover"));
		event.register(CreateBiotech.asResource("block/ghast_helm/train/lever"));
		event.register(CreateBiotech.asResource("block/bio_packager/hatch_open"));
		event.register(CreateBiotech.asResource("block/bio_packager/hatch_closed"));
		event.register(CreateBiotech.asResource("block/bio_packager/tray"));
		event.register(CreateBiotech.asResource("block/shulker_packager/hatch_open"));
		event.register(CreateBiotech.asResource("block/shulker_packager/hatch_closed"));
		event.register(CreateBiotech.asResource("block/shulker_packager/tray"));
		AllayPortRenderer.CURTAIN_MODEL_LOCATIONS.forEach(event::register);
		event.register(CreateBiotech.asResource("item/shulker_package"));
		event.register(CreateBiotech.asResource("item/cardboard_box"));
		event.register(CreateBiotech.asResource("item/small_cardboard_box"));
		event.register(CreateBiotech.asResource("item/small_cardboard_box_captured"));
		event.register(CardboardBoxPartials.SMALL_BOX_LOGISTICS_LOCATION);
		event.register(CreateBiotech.asResource("item/large_cardboard_box"));
		event.register(CreateBiotech.asResource("item/large_cardboard_box_captured"));
		event.register(CardboardBoxPartials.LARGE_BOX_LOGISTICS_LOCATION);
		event.register(CreateBiotech.asResource("item/allay_courier_package"));
		ButterCatPartials.allModels()
			.forEach(model -> event.register(model.modelLocation()));
	}

	@SubscribeEvent
	public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "ghast_balloon_magnet_prompt",
			GhastBalloonMagnetSnapOverlay.INSTANCE);
		event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "allay_courier_eta",
			AllayCourierHudOverlay.INSTANCE);
	}

	@SubscribeEvent
	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		SlimeBeltSpriteShifts.init();
		MagmaBeltSpriteShifts.init();
		PowerBeltSpriteShifts.init();
		CBSpriteShifts.init();
		registerCustomBlockModels();
		event.registerReloadListener(new ResourceManagerReloadListener() {
			@Override
			public void onResourceManagerReload(ResourceManager resourceManager) {
				SlimeMimicRenderLayer.clearCachedTextureData();
				CapturedEntityRenderManager.clearForResourceReload();
			}
		});
		event.registerReloadListener(SlimeBeltHelper.LISTENER);
		event.registerReloadListener(MagmaBeltHelper.LISTENER);
	}

	@SubscribeEvent
	public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(CBParticleTypes.STRAIGHT_ENCHANT.get(), StraightEnchantParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.ALLAY_COURIER_NOTE.get(), CourierNoteParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.SQUID_PRINTER_INK.get(), SquidPrinterInkParticle.Provider::new);
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		MinecraftForge.EVENT_BUS.addListener(RotationHandler::onClientTick);
		MinecraftForge.EVENT_BUS.addListener(RotationHandler::onRender);
		event.enqueueWork(() -> {
			registerItemTooltips();
			registerCardboardBoxModelProperties();
			PonderIndex.addPlugin(new CreateBiotechPonderPlugin());
			CardboardBoxPartials.register();
			ShulkerPackagePartials.register();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.AUTOMATIC_FISH_RELEASE_MACHINE.get())
				.factory(WaterWheelVisual::large)
				.neverSkipVanillaRender()
				.apply();
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.EXPERIENCE_PUMP.get())
				.factory(SingleAxisRotatingVisual.ofZ(ExperiencePumpRenderer.COG))
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
			SimpleBlockEntityVisualizer.builder(CBBlockEntityTypes.BUTTER_CAT_ENGINE.get())
				.factory(ButterCatEngineVisual::new)
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
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BIO_PACKAGER.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.SHULKER_PACKAGER.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.SHULKER_TELEPORTER.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.ALLAY_PORT.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.EXPERIENCE_PUMP.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.MAGMA_BELT.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.POWER_BELT.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.SMALL_EXPERIENCE_BUD.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.MEDIUM_EXPERIENCE_BUD.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.LARGE_EXPERIENCE_BUD.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.EXPERIENCE_CLUSTER.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.SQUID_PRINTER.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.PETRI_DISH.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.SLIME_CLUTCH.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BONE_RATCHET.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.FIXED_CARROT_FISHING_ROD.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BLAST_PROOF_GLASS.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.CUTE_CAT_ON_SHAFT.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BUTTER_CAT_ENGINE.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME_FLOWING.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME_BLOCK.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.TELEPORTATION.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.TELEPORTATION_FLOWING.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.TELEPORTATION_BLOCK.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.CREAM.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.CREAM_FLOWING.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBFluids.CREAM_BLOCK.get(), RenderType.translucent());
			CBMenuScreens.register();
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

	/** Register custom models before Create snapshots this registry during the first model bake. */
	private static synchronized void registerCustomBlockModels() {
		if (customBlockModelsRegistered)
			return;

		var customBlockModels = CreateClient.MODEL_SWAPPER.getCustomBlockModels();
		customBlockModels.register(CreateBiotech.asResource("experience_pump"), PipeAttachmentModel::withAO);
		customBlockModels.register(Create.asResource("andesite_belt_funnel"), SlimeBeltFunnelModel::new);
		customBlockModels.register(Create.asResource("brass_belt_funnel"), SlimeBeltFunnelModel::new);
		customBlockModels.register(CreateBiotech.asResource("magma_belt"),
			com.simibubi.create.content.kinetics.belt.BeltModel::new);
		customBlockModels.register(CreateBiotech.asResource("power_belt"),
			com.simibubi.create.content.kinetics.belt.BeltModel::new);
		customBlockModels.register(CreateBiotech.asResource("asurine_casing"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.ASURINE_CASING)));
		customBlockModels.register(CreateBiotech.asResource("biotech_casing"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.BIOTECH_CASING)));
		customBlockModels.register(CreateBiotech.asResource("explosion_proof_casing"),
			model -> new CTModel(model, new CasingConnectedHorizontalCTBehaviour(
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE, CBSpriteShifts.EXPLOSION_PROOF_CASING)));
		customBlockModels.register(CreateBiotech.asResource("creeper_blast_chamber"),
			model -> new CTModel(model, new CasingConnectedHorizontalCTBehaviour(
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE, CBSpriteShifts.EXPLOSION_PROOF_CASING)));
		customBlockModels.register(CreateBiotech.asResource("explosion_proof_item_vault"),
			model -> new CTModel(model, new ExplosionProofItemVaultCTBehaviour()));
		customBlockModels.register(CreateBiotech.asResource("blast_proof_chain_drive"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE)));
		customBlockModels.register(CreateBiotech.asResource("blast_proof_framed_glass"),
			model -> new CTModel(model, new SimpleCTBehaviour(CBSpriteShifts.BLAST_PROOF_FRAMED_GLASS)));
		customBlockModelsRegistered = true;
	}

	private static void registerItemTooltips() {
		ItemDescription.useKey(CBFluids.TELEPORTATION_BUCKET.get(), "fluid.create_biotech.teleportation");
		ItemDescription.useKey(CBItems.SMALL_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		ItemDescription.useKey(CBItems.MEDIUM_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		ItemDescription.useKey(CBItems.LARGE_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		CBItems.BUFFER_PADS.values()
			.forEach(entry -> ItemDescription.useKey(entry.get(), "block.create_biotech.buffer_pad"));

		registerCreateStyleTooltip(CBFluids.TELEPORTATION_BUCKET.get());
		registerCreateStyleTooltip(CBItems.BUDDING_EXPERIENCE.get());
		registerCreateStyleTooltip(CBItems.AUTOMATIC_FISH_RELEASE_MACHINE.get());
		registerCreateStyleTooltip(CBItems.SMALL_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.MEDIUM_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.LARGE_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.EXPERIENCE_CLUSTER.get());
		registerCreateStyleTooltip(CBItems.CARDBOARD_BOX.get(), CapturedEntityBoxHelper::hasCapturedEntity);
		registerCreateStyleTooltip(CBItems.LARGE_CARDBOARD_BOX.get(), CapturedEntityBoxHelper::hasCapturedEntity);
		registerCreateStyleTooltip(CBItems.CAPTURED_SMALL_SLIME.get());
		registerCreateStyleTooltip(CBItems.SMART_SUPER_GLUE.get());
		registerCreateStyleTooltip(CBItems.FIXED_CARROT_FISHING_ROD.get());
		registerCreateStyleTooltip(CBItems.WIRELESS_TERMINAL.get());
		registerCreateStyleTooltip(CBItems.SHULKER_PACKAGER.get());
		registerCreateStyleTooltip(CBItems.SHULKER_TELEPORTER.get());
		registerCreateStyleTooltip(CBItems.ALLAY_PORT.get());
		registerCreateStyleTooltip(CBItems.ALLAY_COURIER.get());
		registerCreateStyleTooltip(CBItems.CUTE_CAT_ON_SHAFT.get());
		registerKineticCreateStyleTooltip(CBItems.BUTTER_CAT_ENGINE.get());
		registerKineticCreateStyleTooltip(CBItems.BUTTER.get());
		registerKineticCreateStyleTooltip(CBItems.INCOMPLETE_SUPER_BUTTER.get());
		registerKineticCreateStyleTooltip(CBItems.SUPER_BUTTER.get());
		registerKineticCreateStyleTooltip(CBFluids.CREAM_BUCKET.get());
		CBItems.BUFFER_PADS.values()
			.forEach(entry -> registerCreateStyleTooltip(entry.get()));
	}

	private static void registerCreateStyleTooltip(Item item) {
		registerCreateStyleTooltip(item, stack -> false);
	}

	private static void registerCreateStyleTooltip(Item item, Predicate<ItemStack> skipCondition) {
		TooltipModifier description = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
		TooltipModifier.REGISTRY.register(item, context -> {
			if (skipCondition.test(context.getItemStack()))
				return;
			description.modify(context);
		});
	}

	private static void registerKineticCreateStyleTooltip(Item item) {
		TooltipModifier modifier = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
			.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
		TooltipModifier.REGISTRY.register(item, modifier::modify);
	}

	private static void registerCardboardBoxModelProperties() {
		ItemProperties.register(CBItems.CARDBOARD_BOX.get(), CreateBiotech.asResource("captured"),
			(stack, level, entity, seed) -> CapturedEntityBoxHelper.hasCapturedEntity(stack) ? 1.0f : 0.0f);
		ItemProperties.register(CBItems.LARGE_CARDBOARD_BOX.get(), CreateBiotech.asResource("captured"),
			(stack, level, entity, seed) -> CapturedEntityBoxHelper.hasCapturedEntity(stack) ? 1.0f : 0.0f);
	}
}
