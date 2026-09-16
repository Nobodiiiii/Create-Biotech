package com.nobodiiiii.createbiotech.registry;

import net.minecraft.core.registries.Registries;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineBlock;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetBlock;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlock;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlock;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterBlock;
import com.nobodiiiii.createbiotech.content.buttercat.block.SuperButterBlock;
import com.nobodiiiii.createbiotech.content.bufferpad.BufferPadBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.experience.BuddingExperienceBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogDigestiveTractBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogDigestiveTractWallBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachFungusBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachFoldBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachSecretionBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachMucosaBlock;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachWallBlock;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmBlock;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerBlock;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishBlock;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimeclutch.SlimeClutchBlock;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlock;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.nobodiiiii.createbiotech.content.tablecloth.CBTableClothBlock;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlock;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerBlock;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlock;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.ExplosionProofCasingBlock;
import com.nobodiiiii.createbiotech.content.decoration.AsurineSlidingDoorBlock;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.MetalLadderBlock;
import com.simibubi.create.content.decoration.MetalScaffoldingBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;
import com.simibubi.create.api.stress.BlockStressValues;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

public class CBBlocks {

	public static final DeferredRegister<Block> BLOCKS =
		DeferredRegister.create(Registries.BLOCK, CreateBiotech.MOD_ID);

	public static final DeferredHolder<Block, SlimeBeltBlock> SLIME_BELT = BLOCKS.register("slime_belt",
		() -> new SlimeBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_LIGHT_GREEN)
			.noOcclusion()));

	public static final DeferredHolder<Block, MagmaBeltBlock> MAGMA_BELT = BLOCKS.register("magma_belt",
		() -> new MagmaBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_RED)
			.noOcclusion()));

	public static final DeferredHolder<Block, MagmaCubeBurnerBlock> MAGMA_CUBE_BURNER =
		BLOCKS.register("magma_cube_burner",
			() -> new MagmaCubeBurnerBlock(CBSharedProperties.createSoftMetal()
				.mapColor(MapColor.COLOR_GRAY)
				.lightLevel(MagmaCubeBurnerBlock::getLight)
				.noOcclusion()));

	public static final DeferredHolder<Block, PowerBeltBlock> POWER_BELT = BLOCKS.register("power_belt",
		() -> new PowerBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_GRAY)
			.noOcclusion()));

	public static final DeferredHolder<Block, AutomaticFishReleaseMachineBlock> AUTOMATIC_FISH_RELEASE_MACHINE =
		BLOCKS.register("automatic_fish_release_machine",
			() -> new AutomaticFishReleaseMachineBlock(CBSharedProperties.createWooden()
				.noOcclusion()
				.mapColor(MapColor.DIRT)));

	public static final DeferredHolder<Block, EvokerEnchantingChamberBlock> EVOKER_ENCHANTING_CHAMBER =
		BLOCKS.register("evoker_enchanting_chamber",
			() -> new EvokerEnchantingChamberBlock(CBSharedProperties.enchantingTable()
				.noOcclusion()));

	public static final DeferredHolder<Block, ExperiencePumpBlock> EXPERIENCE_PUMP = BLOCKS.register("experience_pump",
		() -> new ExperiencePumpBlock(CBSharedProperties.createCopperMetal()
			.mapColor(MapColor.STONE)));

	public static final DeferredHolder<Block, BuddingExperienceBlock> BUDDING_EXPERIENCE =
		BLOCKS.register("budding_experience",
			() -> new BuddingExperienceBlock(CBSharedProperties.buddingExperience()
				.randomTicks()));

	public static final DeferredHolder<Block, ExperienceClusterBlock> SMALL_EXPERIENCE_BUD =
		BLOCKS.register("small_experience_bud",
			() -> new ExperienceClusterBlock(3, 4, ExperienceConstants::smallBudXpValue,
				CBSharedProperties.smallExperienceBud()));

	public static final DeferredHolder<Block, ExperienceClusterBlock> MEDIUM_EXPERIENCE_BUD =
		BLOCKS.register("medium_experience_bud",
			() -> new ExperienceClusterBlock(4, 3, ExperienceConstants::mediumBudXpValue,
				CBSharedProperties.mediumExperienceBud()));

	public static final DeferredHolder<Block, ExperienceClusterBlock> LARGE_EXPERIENCE_BUD =
		BLOCKS.register("large_experience_bud",
			() -> new ExperienceClusterBlock(5, 3, ExperienceConstants::largeBudXpValue,
				CBSharedProperties.largeExperienceBud()));

	public static final DeferredHolder<Block, ExperienceClusterBlock> EXPERIENCE_CLUSTER =
		BLOCKS.register("experience_cluster",
			() -> new ExperienceClusterBlock(7, 3, ExperienceConstants::clusterXpValue,
				CBSharedProperties.experienceCluster()));

	public static final DeferredHolder<Block, SquidPrinterBlock> SQUID_PRINTER = BLOCKS.register("squid_printer",
		() -> new SquidPrinterBlock(CBSharedProperties.createCopperMetal()
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.noOcclusion()));

	public static final DeferredHolder<Block, PetriDishBlock> PETRI_DISH = BLOCKS.register("petri_dish",
		() -> new PetriDishBlock(CBSharedProperties.createStone()
			.sound(SoundType.GLASS)
			.strength(1.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final DeferredHolder<Block, SurgicalTableBlock> SURGICAL_TABLE = BLOCKS.register("surgical_table",
		() -> new SurgicalTableBlock(CBSharedProperties.createSoftMetal()
			.sound(SoundType.METAL)
			.mapColor(MapColor.COLOR_LIGHT_BLUE)
			.noOcclusion()));

	public static final DeferredHolder<Block, UniversalJointBlock> UNIVERSAL_JOINT = BLOCKS.register("universal_joint",
		() -> new UniversalJointBlock(CBSharedProperties.createStone()
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final DeferredHolder<Block, HalfShaftBlock> HALF_SHAFT = BLOCKS.register("half_shaft",
		() -> new HalfShaftBlock(CBSharedProperties.createStone()
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final DeferredHolder<Block, SlimeClutchBlock> SLIME_CLUTCH = BLOCKS.register("slime_clutch",
		() -> new SlimeClutchBlock(CBSharedProperties.createStone()
			.sound(SoundType.WOOD)
			.mapColor(MapColor.PODZOL)
			.noOcclusion()));

	public static final DeferredHolder<Block, BoneRatchetBlock> BONE_RATCHET = BLOCKS.register("bone_ratchet",
		() -> new BoneRatchetBlock(CBSharedProperties.createStone()
			.sound(SoundType.BONE_BLOCK)
			.mapColor(MapColor.SAND)
			.noOcclusion()));

	public static final DeferredHolder<Block, FixedCarrotFishingRodBlock> FIXED_CARROT_FISHING_ROD =
		BLOCKS.register("fixed_carrot_fishing_rod",
			() -> new FixedCarrotFishingRodBlock(CBSharedProperties.createWooden()
				.sound(SoundType.WOOD)
				.strength(0.4f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final DeferredHolder<Block, GhastHotAirBalloonAssemblyStationBlock> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		BLOCKS.register("ghast_hot_air_balloon_assembly_station",
			() -> new GhastHotAirBalloonAssemblyStationBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final DeferredHolder<Block, GhastHelmBlock> GHAST_HELM = BLOCKS.register("ghast_helm",
		() -> new GhastHelmBlock(CBSharedProperties.createSoftMetal()
			.sound(SoundType.NETHERITE_BLOCK)
			.mapColor(MapColor.TERRACOTTA_BROWN)
			.noOcclusion()));

	public static final DeferredHolder<Block, SchrodingersCatBlock> SCHRODINGERS_CAT =
		BLOCKS.register("schrodingers_cat",
			() -> new SchrodingersCatBlock(CBSharedProperties.createWooden()
				.sound(SoundType.WOOL)
				.strength(0.8f)
				.mapColor(MapColor.COLOR_BROWN)
				.noOcclusion()));

	public static final DeferredHolder<Block, SpiderAssemblyTableBlock> SPIDER_ASSEMBLY_TABLE =
		BLOCKS.register("spider_assembly_table",
			() -> new SpiderAssemblyTableBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final DeferredHolder<Block, SpiderAssemblyTableCogBlock> SPIDER_ASSEMBLY_TABLE_COG =
		BLOCKS.register("spider_assembly_table_cog",
			() -> new SpiderAssemblyTableCogBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final DeferredHolder<Block, CreeperBlastChamberBlock> CREEPER_BLAST_CHAMBER =
		BLOCKS.register("creeper_blast_chamber",
			() -> new CreeperBlastChamberBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
				.sound(SoundType.WOOD)
				.noOcclusion()));

	public static final DeferredHolder<Block, CasingBlock> ASURINE_CASING =
		BLOCKS.register("asurine_casing",
			() -> new CasingBlock(CBSharedProperties.createStone()
				.sound(SoundType.WOOD)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final DeferredHolder<Block, MetalScaffoldingBlock> ASURINE_SCAFFOLDING =
		BLOCKS.register("asurine_scaffolding",
			() -> new MetalScaffoldingBlock(Block.Properties.ofFullCopy(Blocks.SCAFFOLDING)
				.sound(SoundType.COPPER)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final DeferredHolder<Block, MetalLadderBlock> ASURINE_LADDER =
		BLOCKS.register("asurine_ladder",
			() -> new MetalLadderBlock(Block.Properties.ofFullCopy(Blocks.LADDER)
				.sound(SoundType.COPPER)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final DeferredHolder<Block, IronBarsBlock> ASURINE_BARS =
		BLOCKS.register("asurine_bars",
			() -> new IronBarsBlock(Block.Properties.ofFullCopy(Blocks.IRON_BARS)
				.sound(SoundType.COPPER)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final DeferredHolder<Block, AsurineSlidingDoorBlock> ASURINE_DOOR =
		BLOCKS.register("asurine_door",
			() -> new AsurineSlidingDoorBlock(Block.Properties.ofFullCopy(Blocks.IRON_DOOR)
				.requiresCorrectToolForDrops()
				.strength(3.0f, 6.0f)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)
				.noOcclusion(), SlidingDoorBlock.STONE_SET_TYPE.get()));

	public static final DeferredHolder<Block, CasingBlock> BIOTECH_CASING =
		BLOCKS.register("biotech_casing",
			() -> new CasingBlock(CBSharedProperties.createStone()
				.sound(SoundType.WOOD)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final DeferredHolder<Block, CBTableClothBlock> ASURINE_TABLE_CLOTH =
		BLOCKS.register("asurine_table_cloth",
			() -> new CBTableClothBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_LIGHT_BLUE)
				.requiresCorrectToolForDrops(), "asurine"));

	public static final DeferredHolder<Block, ExplosionProofCasingBlock> EXPLOSION_PROOF_CASING =
		BLOCKS.register("explosion_proof_casing",
			() -> new ExplosionProofCasingBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
				.sound(SoundType.WOOD)));

	public static final DeferredHolder<Block, ExplosionProofItemVaultBlock> EXPLOSION_PROOF_ITEM_VAULT =
		BLOCKS.register("explosion_proof_item_vault",
			() -> new ExplosionProofItemVaultBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createSoftMetal())
				.mapColor(MapColor.TERRACOTTA_BLUE)
				.sound(SoundType.NETHERITE_BLOCK)));

	public static final DeferredHolder<Block, TransparentBlock> BLAST_PROOF_GLASS =
		BLOCKS.register("blast_proof_glass",
			() -> new TransparentBlock(blastProofGlassProperties()));

	public static final DeferredHolder<Block, BlastProofChainDriveBlock> BLAST_PROOF_CHAIN_DRIVE =
		BLOCKS.register("blast_proof_chain_drive",
				() -> new BlastProofChainDriveBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
					.noOcclusion()
					.mapColor(MapColor.PODZOL)));

	public static final DeferredHolder<Block, BioPackagerBlock> BIO_PACKAGER = BLOCKS.register("bio_packager",
		() -> new BioPackagerBlock(CBSharedProperties.createSoftMetal()
			.noOcclusion()
			.isRedstoneConductor(($1, $2, $3) -> false)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.sound(SoundType.NETHERITE_BLOCK)));

	public static final DeferredHolder<Block, ShulkerPackagerBlock> SHULKER_PACKAGER = BLOCKS.register("shulker_packager",
		() -> new ShulkerPackagerBlock(CBSharedProperties.createSoftMetal()
			.noOcclusion()
			.isRedstoneConductor(($1, $2, $3) -> false)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.sound(SoundType.NETHERITE_BLOCK)));

	public static final DeferredHolder<Block, ShulkerTeleporterBlock> SHULKER_TELEPORTER =
		BLOCKS.register("shulker_teleporter",
			() -> new ShulkerTeleporterBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_PURPLE)
				.noOcclusion()));

	public static final DeferredHolder<Block, AllayPortBlock> ALLAY_PORT =
		BLOCKS.register("allay_port",
			() -> new AllayPortBlock(CBSharedProperties.createSoftMetal()
				.sound(SoundType.NETHERITE_BLOCK)
				.mapColor(MapColor.TERRACOTTA_BLUE)
				.noOcclusion()));

	public static final DeferredHolder<Block, ConnectedGlassBlock> BLAST_PROOF_FRAMED_GLASS =
		BLOCKS.register("blast_proof_framed_glass",
			() -> new ConnectedGlassBlock(blastProofGlassProperties()));

	public static final Map<DyeColor, DeferredHolder<Block, BufferPadBlock>> BUFFER_PADS = registerBufferPads();
	public static final DeferredHolder<Block, BufferPadBlock> BUFFER_PAD = BUFFER_PADS.get(DyeColor.RED);

	public static final DeferredHolder<Block, GiantFrogBlock> GIANT_FROG =
		BLOCKS.register("giant_frog",
			() -> new GiantFrogBlock(Block.Properties.of()
				.sound(SoundType.SLIME_BLOCK)
				.strength(1.0f)
				.mapColor(MapColor.COLOR_GREEN)
				.noOcclusion()));

	// Indestructible shell of every Frog Stomach room; placed by FrogStomachSpace, never obtainable.
	public static final DeferredHolder<Block, FrogStomachWallBlock> FROG_STOMACH_WALL =
		BLOCKS.register("frog_stomach_wall",
			() -> new FrogStomachWallBlock(Block.Properties.ofFullCopy(Blocks.ORANGE_CONCRETE)
				.strength(-1.0f, 3600000.0f)
				.sound(SoundType.SLIME_BLOCK)
				.noLootTable()));

	// Pink living terrain lining the floor, ceiling, and walls of every new stomach room.
	public static final DeferredHolder<Block, FrogStomachMucosaBlock> FROG_STOMACH_MUCOSA =
		BLOCKS.register("frog_stomach_mucosa",
			() -> new FrogStomachMucosaBlock(Block.Properties.ofFullCopy(Blocks.PINK_CONCRETE)
				.strength(0.8f)
				.sound(SoundType.SLIME_BLOCK)));

	public static final DeferredHolder<Block, FrogStomachFoldBlock> FROG_STOMACH_FOLD =
		BLOCKS.register("frog_stomach_fold",
			() -> new FrogStomachFoldBlock(Block.Properties.ofFullCopy(Blocks.PINK_CONCRETE)
				.strength(0.8f)
				.sound(SoundType.SLIME_BLOCK)));

	// Six-way stomach plant with a dedicated mature structure palette.
	public static final DeferredHolder<Block, FrogStomachFungusBlock> FROG_STOMACH_FUNGUS =
		BLOCKS.register("frog_stomach_fungus",
			() -> new FrogStomachFungusBlock(Block.Properties.ofFullCopy(Blocks.CRIMSON_FUNGUS)));

	// Internal blocks used only by the mature Frog Stomach Fungus structure.
	public static final DeferredHolder<Block, RotatedPillarBlock> FROG_STOMACH_FUNGUS_STEM =
		BLOCKS.register("frog_stomach_fungus_stem",
			() -> new RotatedPillarBlock(Block.Properties.ofFullCopy(Blocks.CRIMSON_STEM)
				.noLootTable()));

	public static final DeferredHolder<Block, Block> FROG_STOMACH_FUNGUS_CAP =
		BLOCKS.register("frog_stomach_fungus_cap",
			() -> new Block(Block.Properties.ofFullCopy(Blocks.NETHER_WART_BLOCK)
				.noLootTable()));

	public static final DeferredHolder<Block, Block> FROG_STOMACH_FUNGUS_LIGHT =
		BLOCKS.register("frog_stomach_fungus_light",
			() -> new Block(Block.Properties.ofFullCopy(Blocks.SHROOMLIGHT)
				.noLootTable()));

	// Slime-like secretion that absorbs slime experience and spreads across supported surfaces.
	// Honey's inset collision shape is not a valid ON_GROUND spawn surface, so permit slimes explicitly.
	public static final DeferredHolder<Block, FrogStomachSecretionBlock> FROG_STOMACH_SECRETION =
		BLOCKS.register("frog_stomach_secretion",
			() -> new FrogStomachSecretionBlock(Block.Properties.ofFullCopy(Blocks.HONEY_BLOCK)
				.isValidSpawn((state, level, pos, entityType) -> entityType == EntityType.SLIME)));

	// Indestructible return portal inside every Frog Stomach room; placed with the room, never obtainable.
	public static final DeferredHolder<Block, FrogDigestiveTractBlock> FROG_DIGESTIVE_TRACT =
		BLOCKS.register("frog_digestive_tract",
			() -> new FrogDigestiveTractBlock(Block.Properties.of()
				.sound(SoundType.SLIME_BLOCK)
				.strength(-1.0f, 3600000.0f)
				.mapColor(MapColor.COLOR_ORANGE)
				.noCollission()
				.lightLevel(state -> 11)
				.noOcclusion()
				.noLootTable()));

	// Upright digestive-tract wall; generated with each Frog Stomach room and filled with slimeballs.
	public static final DeferredHolder<Block, FrogDigestiveTractWallBlock> FROG_DIGESTIVE_TRACT_WALL =
		BLOCKS.register("frog_digestive_tract_wall",
			() -> new FrogDigestiveTractWallBlock(Block.Properties.ofFullCopy(Blocks.END_PORTAL_FRAME)
				.strength(-1.0f, 3600000.0f)
				.mapColor(MapColor.COLOR_ORANGE)
				.sound(SoundType.SLIME_BLOCK)
				.noLootTable()));

	public static final DeferredHolder<Block, ButterCatEngineBlock> CUTE_CAT_ON_SHAFT =
		BLOCKS.register("cute_cat_on_shaft",
			() -> new ButterCatEngineBlock(CBSharedProperties.withLegacyNonSolid(CBSharedProperties.createStone()
				.noOcclusion()
				.mapColor(MapColor.METAL))));

	public static final DeferredHolder<Block, ButterCatEngineBlock> BUTTER_CAT_ENGINE =
		BLOCKS.register("butter_cat_engine",
			() -> new ButterCatEngineBlock(CBSharedProperties.withLegacyNonSolid(CBSharedProperties.createStone()
				.noOcclusion()
				.mapColor(MapColor.METAL))));

	public static final DeferredHolder<Block, ButterBlock> BUTTER_BLOCK =
		BLOCKS.register("butter_block",
			() -> new ButterBlock(Block.Properties.ofFullCopy(Blocks.HONEY_BLOCK)
				.speedFactor(1.0F)));

	public static final DeferredHolder<Block, SuperButterBlock> SUPER_BUTTER_BLOCK =
		BLOCKS.register("super_butter_block",
			() -> new SuperButterBlock(Block.Properties.ofFullCopy(Blocks.HONEY_BLOCK)
				.speedFactor(1.0F)));

	private static Block.Properties blastProofGlassProperties() {
		return CBSharedProperties.withExplosionProofResistance(CBSharedProperties.vanillaGlass());
	}

	private static Map<DyeColor, DeferredHolder<Block, BufferPadBlock>> registerBufferPads() {
		EnumMap<DyeColor, DeferredHolder<Block, BufferPadBlock>> bufferPads = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			bufferPads.put(color, BLOCKS.register(bufferPadId(color),
				() -> new BufferPadBlock(Block.Properties.of()
					.sound(SoundType.WOOL)
					.strength(0.4f)
					.mapColor(color.getMapColor())
					.noOcclusion())));
		}
		return Collections.unmodifiableMap(bufferPads);
	}

	public static String bufferPadId(DyeColor color) {
		return color == DyeColor.RED ? "buffer_pad" : color.getName() + "_buffer_pad";
	}

	public static Iterable<DeferredHolder<Block, BufferPadBlock>> allBufferPads() {
		return BUFFER_PADS.values();
	}

	private CBBlocks() {}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}

	public static void registerButterCatStressValues() {
		double maxGeneratedRpm = butterCatMaxGeneratedRpm();
		BlockStressValues.GeneratedRpm generatedRpm =
			new BlockStressValues.GeneratedRpm((int) Math.round(maxGeneratedRpm), true);

		for (ButterCatEngineBlock block : new ButterCatEngineBlock[] {
			CUTE_CAT_ON_SHAFT.get(), BUTTER_CAT_ENGINE.get()
		}) {
			BlockStressValues.CAPACITIES.register(block, CBBlocks::butterCatCapacityPerRpm);
			BlockStressValues.RPM.register(block, generatedRpm);
		}
	}

	private static double butterCatCapacityPerRpm() {
		double maxGeneratedRpm = butterCatMaxGeneratedRpm();
		return maxGeneratedRpm == 0 ? 0 : butterCatMaxStressCapacity() / maxGeneratedRpm;
	}

	private static double butterCatMaxStressCapacity() {
		return CBConfigs.SERVER_SPEC.isLoaded()
			? CBConfigs.SERVER.butterCat.maxStressCapacity.get()
			: CBConfigs.SERVER.butterCat.maxStressCapacity.getDefault();
	}

	private static double butterCatMaxGeneratedRpm() {
		return CBConfigs.SERVER_SPEC.isLoaded()
			? CBConfigs.SERVER.butterCat.maxGeneratedRpm.get()
			: CBConfigs.SERVER.butterCat.maxGeneratedRpm.getDefault();
	}
}
