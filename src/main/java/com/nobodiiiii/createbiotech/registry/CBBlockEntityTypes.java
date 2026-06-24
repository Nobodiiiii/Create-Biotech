package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetBlockEntity;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlockEnetities;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.experience.BuddingExperienceBlockEntity;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlockEntity;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlockEntity;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationBlockEntity;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlockEntity;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterBlockEntity;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogBlockEntity;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlockEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishBlockEntity;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimeclutch.SlimeClutchBlockEntity;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlockEntity;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlockEntity;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public class CBBlockEntityTypes {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
		DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateBiotech.MOD_ID);

	public static final RegistryObject<BlockEntityType<SlimeBeltBlockEntity>> SLIME_BELT =
		BLOCK_ENTITY_TYPES.register("slime_belt",
			() -> BlockEntityType.Builder.of(SlimeBeltBlockEntity::new, CBBlocks.SLIME_BELT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<MagmaBeltBlockEntity>> MAGMA_BELT =
		BLOCK_ENTITY_TYPES.register("magma_belt",
			() -> BlockEntityType.Builder.of(MagmaBeltBlockEntity::new, CBBlocks.MAGMA_BELT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<PowerBeltBlockEntity>> POWER_BELT =
		BLOCK_ENTITY_TYPES.register("power_belt",
			() -> BlockEntityType.Builder.of(PowerBeltBlockEntity::new, CBBlocks.POWER_BELT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SchrodingersCatBlockEntity>> SCHRODINGERS_CAT =
		BLOCK_ENTITY_TYPES.register("schrodingers_cat",
			() -> BlockEntityType.Builder
				.of(SchrodingersCatBlockEntity::new, CBBlocks.SCHRODINGERS_CAT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SpiderAssemblyTableBlockEntity>> SPIDER_ASSEMBLY_TABLE =
		BLOCK_ENTITY_TYPES.register("spider_assembly_table",
			() -> BlockEntityType.Builder
				.of(SpiderAssemblyTableBlockEntity::new, CBBlocks.SPIDER_ASSEMBLY_TABLE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SpiderAssemblyTableCogBlockEntity>> SPIDER_ASSEMBLY_TABLE_COG =
		BLOCK_ENTITY_TYPES.register("spider_assembly_table_cog",
			() -> BlockEntityType.Builder
				.of(SpiderAssemblyTableCogBlockEntity::new, CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<EvokerEnchantingChamberBlockEntity>> EVOKER_ENCHANTING_CHAMBER =
		BLOCK_ENTITY_TYPES.register("evoker_enchanting_chamber",
			() -> BlockEntityType.Builder
				.of(EvokerEnchantingChamberBlockEntity::new, CBBlocks.EVOKER_ENCHANTING_CHAMBER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ExperiencePumpBlockEntity>> EXPERIENCE_PUMP =
		BLOCK_ENTITY_TYPES.register("experience_pump",
			() -> BlockEntityType.Builder
				.of(ExperiencePumpBlockEntity::new, CBBlocks.EXPERIENCE_PUMP.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<BuddingExperienceBlockEntity>> BUDDING_EXPERIENCE =
		BLOCK_ENTITY_TYPES.register("budding_experience",
			() -> BlockEntityType.Builder
				.of(BuddingExperienceBlockEntity::new, CBBlocks.BUDDING_EXPERIENCE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SquidPrinterBlockEntity>> SQUID_PRINTER =
		BLOCK_ENTITY_TYPES.register("squid_printer",
			() -> BlockEntityType.Builder
				.of((pos, state) -> new SquidPrinterBlockEntity(CBBlockEntityTypes.SQUID_PRINTER.get(), pos, state),
					CBBlocks.SQUID_PRINTER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<PetriDishBlockEntity>> PETRI_DISH =
		BLOCK_ENTITY_TYPES.register("petri_dish",
			() -> BlockEntityType.Builder
				.of(PetriDishBlockEntity::new, CBBlocks.PETRI_DISH.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<UniversalJointBlockEntity>> UNIVERSAL_JOINT =
		BLOCK_ENTITY_TYPES.register("universal_joint",
			() -> BlockEntityType.Builder.of(UniversalJointBlockEntity::new, CBBlocks.UNIVERSAL_JOINT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SlimeClutchBlockEntity>> SLIME_CLUTCH =
		BLOCK_ENTITY_TYPES.register("slime_clutch",
			() -> BlockEntityType.Builder.of(SlimeClutchBlockEntity::new, CBBlocks.SLIME_CLUTCH.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<BoneRatchetBlockEntity>> BONE_RATCHET =
		BLOCK_ENTITY_TYPES.register("bone_ratchet",
			() -> BlockEntityType.Builder.of(BoneRatchetBlockEntity::new, CBBlocks.BONE_RATCHET.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<FixedCarrotFishingRodBlockEntity>> FIXED_CARROT_FISHING_ROD =
		BLOCK_ENTITY_TYPES.register("fixed_carrot_fishing_rod",
			() -> BlockEntityType.Builder
				.of(FixedCarrotFishingRodBlockEntity::new, CBBlocks.FIXED_CARROT_FISHING_ROD.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<GhastHotAirBalloonAssemblyStationBlockEntity>> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		BLOCK_ENTITY_TYPES.register("ghast_hot_air_balloon_assembly_station",
			() -> BlockEntityType.Builder
				.of(GhastHotAirBalloonAssemblyStationBlockEntity::new,
					CBBlocks.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<CreeperBlastChamberBlockEntity>> CREEPER_BLAST_CHAMBER =
		BLOCK_ENTITY_TYPES.register("creeper_blast_chamber",
			() -> BlockEntityType.Builder
				.of(CreeperBlastChamberBlockEntity::new, CBBlocks.CREEPER_BLAST_CHAMBER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ExplosionProofItemVaultBlockEntity>> EXPLOSION_PROOF_ITEM_VAULT =
		BLOCK_ENTITY_TYPES.register("explosion_proof_item_vault",
			() -> BlockEntityType.Builder
				.of(ExplosionProofItemVaultBlockEntity::new, CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<BlastProofChainDriveBlockEntity>> BLAST_PROOF_CHAIN_DRIVE =
		BLOCK_ENTITY_TYPES.register("blast_proof_chain_drive",
			() -> BlockEntityType.Builder
				.of(BlastProofChainDriveBlockEntity::new, CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<BioPackagerBlockEntity>> BIO_PACKAGER =
		BLOCK_ENTITY_TYPES.register("bio_packager",
			() -> BlockEntityType.Builder
				.of(BioPackagerBlockEntity::new, CBBlocks.BIO_PACKAGER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ShulkerPackagerBlockEntity>> SHULKER_PACKAGER =
		BLOCK_ENTITY_TYPES.register("shulker_packager",
			() -> BlockEntityType.Builder
				.of(ShulkerPackagerBlockEntity::new, CBBlocks.SHULKER_PACKAGER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ShulkerTeleporterBlockEntity>> SHULKER_TELEPORTER =
		BLOCK_ENTITY_TYPES.register("shulker_teleporter",
			() -> BlockEntityType.Builder
				.of(ShulkerTeleporterBlockEntity::new, CBBlocks.SHULKER_TELEPORTER.get())
				.build(null));

	// Butter Cat content is registered through the shared ButterCat registrate, and re-exported
	// here so the project's primary block entity registry remains the place to inspect mod blocks.
	public static final BlockEntityEntry<ButterCatEngineBlockEntity> BUTTER_CAT_ENGINE =
		ModBlockEnetities.BUTTER_CAT_ENGINE_BE;

	private CBBlockEntityTypes() {}

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITY_TYPES.register(modEventBus);
	}
}
