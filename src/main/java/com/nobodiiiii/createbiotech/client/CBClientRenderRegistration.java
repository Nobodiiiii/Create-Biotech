package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.entity.client.DevourerRenderer;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineRenderer;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberRenderer;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineRenderer;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerRenderer;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetRenderer;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxEntityRenderer;
import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberRenderer;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonEntityRenderer;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterRenderer;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonSeatEntity;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltRenderer;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerRenderer;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltRenderer;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishRenderer;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableRenderer;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatRenderer;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerRenderer;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltRenderer;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogRenderer;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableRenderer;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRenderer;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgrade;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRenderer;
import com.nobodiiiii.createbiotech.entity.PlayerMimicRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeMimicCubeRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.logistics.tableCloth.TableClothRenderer;
import com.nobodiiiii.createbiotech.content.allay.client.render.AllayCourierEntityRenderer;
import com.nobodiiiii.createbiotech.content.allay.client.render.AllayPortRenderer;
import com.nobodiiiii.createbiotech.client.particle.CourierNoteParticle;
import com.nobodiiiii.createbiotech.client.particle.FrogPortalParticle;
import com.nobodiiiii.createbiotech.client.particle.SonicConeWaveParticle;
import com.nobodiiiii.createbiotech.client.particle.SquidPrinterInkParticle;
import com.nobodiiiii.createbiotech.client.particle.StraightEnchantParticle;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorRenderer;
import com.simibubi.create.content.equipment.armor.CardboardArmorStealthOverlay;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Internal CBClientRenderRegistration implementation; public entry points remain on CreateBiotechClient. */
final class CBClientRenderRegistration {
	private CBClientRenderRegistration() {}

	static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(CBBlockEntityTypes.HONEYCOMB_GAUGE_CLUSTER.get(),
			HoneycombGaugeClusterRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
			AutomaticFishReleaseMachineRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(),
			EvokerEnchantingChamberRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), ExperiencePumpRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SQUID_PRINTER.get(), SquidPrinterRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_BELT.get(), SlimeBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.MAGMA_BELT.get(), MagmaBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.MAGMA_CUBE_BURNER.get(), MagmaCubeBurnerRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.POWER_BELT.get(), PowerBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), UniversalJointRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.HALF_SHAFT.get(),
			KineticBlockEntityRenderer::new);
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
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SURGICAL_TABLE.get(), SurgicalTableRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.BUTTER_CAT_ENGINE.get(), ButterCatEngineRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.ALLAY_PORT.get(), AllayPortRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.GIANT_FROG.get(), GiantFrogRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.TABLE_CLOTH.get(), TableClothRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.ASURINE_DOOR.get(), SlidingDoorRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.GHAST_HOT_AIR_BALLOON.get(),
			GhastHotAirBalloonEntityRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.GHAST_HOT_AIR_BALLOON_SEAT.get(),
			GhastHotAirBalloonSeatEntity.Render::new);
		event.registerEntityRenderer(CBEntityTypes.CARDBOARD_BOX.get(), CardboardBoxEntityRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.ALLAY_COURIER.get(),
			context -> new AllayCourierEntityRenderer(context));
		event.registerEntityRenderer(CBEntityTypes.DEVOURER.get(), DevourerRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.DING_DONG_CHICKEN.get(), DingDongChickenRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.SLIME_BIONIC.get(), SlimeBionicRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.BIONIC_EFFECT_CLOUD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.PLAYER_MIMIC.get(), PlayerMimicRenderer::new);
		event.registerEntityRenderer(CBEntityTypes.SLIME_MIMIC_CUBE.get(), SlimeMimicCubeRenderer::new);
	}

	static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
		for (var skin : event.getSkins()) {
			net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
			if (renderer != null)
				renderer.addLayer(new com.nobodiiiii.createbiotech.entity.client.BionicOnShoulderLayer(renderer));
		}
	}

	static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(CBParticleTypes.STRAIGHT_ENCHANT.get(), StraightEnchantParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.ALLAY_COURIER_NOTE.get(), CourierNoteParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.FROG_PORTAL.get(), FrogPortalParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.SONIC_CONE_WAVE.get(), SonicConeWaveParticle.Provider::new);
		event.registerSpriteSet(CBParticleTypes.SQUID_PRINTER_INK.get(), SquidPrinterInkParticle.Provider::new);
	}

	static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		event.register((stack, tintIndex) -> tintIndex == 0
			&& SonicDogCannonUpgrade.DOG_COLLAR.isInstalled(stack)
			? SonicDogCannonUpgrade.getCollarColor(stack).getTextureDiffuseColor()
			: -1, CBItems.SONIC_DOG_CANNON.get());
		event.register((stack, tintIndex) -> tintIndex == 0
			&& SurgicalKitItem.selectedTool(stack) == SurgicalKitItem.Tool.MIMIC_RESTORATIVE
			? net.minecraft.util.FastColor.ARGB32.opaque(0xCD5CAB) : -1, CBItems.CREATIVE_SURGICAL_KIT.get());
	}

	static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		CBClientExtensions.register(event);
		// Slime armour shares Create's cardboard stealth blur overlay on the helmet slot; the overlay's
		// opacity is driven by Create's testForStealth, which a full slime set satisfies.
		event.registerItem(new CardboardArmorStealthOverlay(), CBItems.SLIME_HELMET.get());
	}
}
