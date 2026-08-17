package com.nobodiiiii.createbiotech.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineBlock;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetBlock;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlock;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlock;
import com.nobodiiiii.createbiotech.content.bufferpad.BufferPadBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.experience.BuddingExperienceBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmBlock;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerBlock;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishBlock;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimeclutch.SlimeClutchBlock;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlock;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlock;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerBlock;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftBlock;
import com.yision.allay.block.allayport.AllayPortBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.ExplosionProofCasingBlock;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;
import com.simibubi.create.api.stress.BlockStressValues;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBBlocks {

	public static final DeferredRegister<Block> BLOCKS =
		DeferredRegister.create(ForgeRegistries.BLOCKS, CreateBiotech.MOD_ID);

	public static final RegistryObject<SlimeBeltBlock> SLIME_BELT = BLOCKS.register("slime_belt",
		() -> new SlimeBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_LIGHT_GREEN)
			.noOcclusion()));

	public static final RegistryObject<MagmaBeltBlock> MAGMA_BELT = BLOCKS.register("magma_belt",
		() -> new MagmaBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_RED)
			.noOcclusion()));

	public static final RegistryObject<MagmaCubeBurnerBlock> MAGMA_CUBE_BURNER =
		BLOCKS.register("magma_cube_burner",
			() -> new MagmaCubeBurnerBlock(CBSharedProperties.createSoftMetal()
				.mapColor(MapColor.COLOR_GRAY)
				.lightLevel(MagmaCubeBurnerBlock::getLight)
				.noOcclusion()));

	public static final RegistryObject<PowerBeltBlock> POWER_BELT = BLOCKS.register("power_belt",
		() -> new PowerBeltBlock(CBSharedProperties.createWooden()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_GRAY)
			.noOcclusion()));

	public static final RegistryObject<AutomaticFishReleaseMachineBlock> AUTOMATIC_FISH_RELEASE_MACHINE =
	BLOCKS.register("automatic_fish_release_machine",
			() -> new AutomaticFishReleaseMachineBlock(CBSharedProperties.createWooden()
				.noOcclusion()
				.mapColor(MapColor.DIRT)));

	public static final RegistryObject<EvokerEnchantingChamberBlock> EVOKER_ENCHANTING_CHAMBER =
		BLOCKS.register("evoker_enchanting_chamber",
			() -> new EvokerEnchantingChamberBlock(CBSharedProperties.enchantingTable()
				.noOcclusion()));

	public static final RegistryObject<ExperiencePumpBlock> EXPERIENCE_PUMP = BLOCKS.register("experience_pump",
		() -> new ExperiencePumpBlock(CBSharedProperties.createCopperMetal()
			.mapColor(MapColor.STONE)));

	public static final RegistryObject<BuddingExperienceBlock> BUDDING_EXPERIENCE =
		BLOCKS.register("budding_experience",
			() -> new BuddingExperienceBlock(CBSharedProperties.buddingExperience()
				.randomTicks()));

	public static final RegistryObject<ExperienceClusterBlock> SMALL_EXPERIENCE_BUD =
		BLOCKS.register("small_experience_bud",
			() -> new ExperienceClusterBlock(3, 4, ExperienceConstants::smallBudXpValue,
				CBSharedProperties.smallExperienceBud()));

	public static final RegistryObject<ExperienceClusterBlock> MEDIUM_EXPERIENCE_BUD =
		BLOCKS.register("medium_experience_bud",
			() -> new ExperienceClusterBlock(4, 3, ExperienceConstants::mediumBudXpValue,
				CBSharedProperties.mediumExperienceBud()));

	public static final RegistryObject<ExperienceClusterBlock> LARGE_EXPERIENCE_BUD =
		BLOCKS.register("large_experience_bud",
			() -> new ExperienceClusterBlock(5, 3, ExperienceConstants::largeBudXpValue,
				CBSharedProperties.largeExperienceBud()));

	public static final RegistryObject<ExperienceClusterBlock> EXPERIENCE_CLUSTER =
		BLOCKS.register("experience_cluster",
			() -> new ExperienceClusterBlock(7, 3, ExperienceConstants::clusterXpValue,
				CBSharedProperties.experienceCluster()));

	public static final RegistryObject<SquidPrinterBlock> SQUID_PRINTER = BLOCKS.register("squid_printer",
		() -> new SquidPrinterBlock(CBSharedProperties.createCopperMetal()
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.noOcclusion()));

	public static final RegistryObject<PetriDishBlock> PETRI_DISH = BLOCKS.register("petri_dish",
		() -> new PetriDishBlock(CBSharedProperties.createStone()
			.sound(SoundType.GLASS)
			.strength(1.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<UniversalJointBlock> UNIVERSAL_JOINT = BLOCKS.register("universal_joint",
		() -> new UniversalJointBlock(CBSharedProperties.createStone()
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<HalfShaftBlock> HALF_SHAFT = BLOCKS.register("half_shaft",
		() -> new HalfShaftBlock(CBSharedProperties.createStone()
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<SlimeClutchBlock> SLIME_CLUTCH = BLOCKS.register("slime_clutch",
		() -> new SlimeClutchBlock(CBSharedProperties.createStone()
			.sound(SoundType.WOOD)
			.mapColor(MapColor.PODZOL)
			.noOcclusion()));

	public static final RegistryObject<BoneRatchetBlock> BONE_RATCHET = BLOCKS.register("bone_ratchet",
		() -> new BoneRatchetBlock(CBSharedProperties.createStone()
			.sound(SoundType.BONE_BLOCK)
			.mapColor(MapColor.SAND)
			.noOcclusion()));

	public static final RegistryObject<FixedCarrotFishingRodBlock> FIXED_CARROT_FISHING_ROD =
		BLOCKS.register("fixed_carrot_fishing_rod",
			() -> new FixedCarrotFishingRodBlock(CBSharedProperties.createWooden()
				.sound(SoundType.WOOD)
				.strength(0.4f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<GhastHotAirBalloonAssemblyStationBlock> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		BLOCKS.register("ghast_hot_air_balloon_assembly_station",
			() -> new GhastHotAirBalloonAssemblyStationBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<GhastHelmBlock> GHAST_HELM = BLOCKS.register("ghast_helm",
		() -> new GhastHelmBlock(CBSharedProperties.createSoftMetal()
			.sound(SoundType.NETHERITE_BLOCK)
			.mapColor(MapColor.TERRACOTTA_BROWN)
			.noOcclusion()));

	public static final RegistryObject<SchrodingersCatBlock> SCHRODINGERS_CAT =
		BLOCKS.register("schrodingers_cat",
			() -> new SchrodingersCatBlock(CBSharedProperties.createWooden()
				.sound(SoundType.WOOL)
				.strength(0.8f)
				.mapColor(MapColor.COLOR_BROWN)
				.noOcclusion()));

	public static final RegistryObject<SpiderAssemblyTableBlock> SPIDER_ASSEMBLY_TABLE =
		BLOCKS.register("spider_assembly_table",
			() -> new SpiderAssemblyTableBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final RegistryObject<SpiderAssemblyTableCogBlock> SPIDER_ASSEMBLY_TABLE_COG =
		BLOCKS.register("spider_assembly_table_cog",
			() -> new SpiderAssemblyTableCogBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final RegistryObject<CreeperBlastChamberBlock> CREEPER_BLAST_CHAMBER =
		BLOCKS.register("creeper_blast_chamber",
			() -> new CreeperBlastChamberBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
				.sound(SoundType.WOOD)
				.noOcclusion()));

	public static final RegistryObject<CasingBlock> ASURINE_CASING =
		BLOCKS.register("asurine_casing",
			() -> new CasingBlock(CBSharedProperties.createStone()
				.sound(SoundType.WOOD)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final RegistryObject<CasingBlock> BIOTECH_CASING =
		BLOCKS.register("biotech_casing",
			() -> new CasingBlock(CBSharedProperties.createStone()
				.sound(SoundType.WOOD)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final RegistryObject<ExplosionProofCasingBlock> EXPLOSION_PROOF_CASING =
		BLOCKS.register("explosion_proof_casing",
			() -> new ExplosionProofCasingBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
				.sound(SoundType.WOOD)));

	public static final RegistryObject<ExplosionProofItemVaultBlock> EXPLOSION_PROOF_ITEM_VAULT =
		BLOCKS.register("explosion_proof_item_vault",
			() -> new ExplosionProofItemVaultBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createSoftMetal())
				.mapColor(MapColor.TERRACOTTA_BLUE)
				.sound(SoundType.NETHERITE_BLOCK)));

	public static final RegistryObject<GlassBlock> BLAST_PROOF_GLASS =
		BLOCKS.register("blast_proof_glass",
			() -> new GlassBlock(blastProofGlassProperties()));

	public static final RegistryObject<BlastProofChainDriveBlock> BLAST_PROOF_CHAIN_DRIVE =
		BLOCKS.register("blast_proof_chain_drive",
				() -> new BlastProofChainDriveBlock(CBSharedProperties.withExplosionProofResistance(CBSharedProperties.createStone())
					.noOcclusion()
					.mapColor(MapColor.PODZOL)));

	public static final RegistryObject<BioPackagerBlock> BIO_PACKAGER = BLOCKS.register("bio_packager",
		() -> new BioPackagerBlock(CBSharedProperties.createSoftMetal()
			.noOcclusion()
			.isRedstoneConductor(($1, $2, $3) -> false)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.sound(SoundType.NETHERITE_BLOCK)));

	public static final RegistryObject<ShulkerPackagerBlock> SHULKER_PACKAGER = BLOCKS.register("shulker_packager",
		() -> new ShulkerPackagerBlock(CBSharedProperties.createSoftMetal()
			.noOcclusion()
			.isRedstoneConductor(($1, $2, $3) -> false)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.sound(SoundType.NETHERITE_BLOCK)));

	public static final RegistryObject<ShulkerTeleporterBlock> SHULKER_TELEPORTER =
		BLOCKS.register("shulker_teleporter",
			() -> new ShulkerTeleporterBlock(CBSharedProperties.createStone()
				.mapColor(MapColor.COLOR_PURPLE)
				.noOcclusion()));

	public static final RegistryObject<AllayPortBlock> ALLAY_PORT =
		BLOCKS.register("allay_port",
			() -> new AllayPortBlock(CBSharedProperties.createSoftMetal()
				.sound(SoundType.NETHERITE_BLOCK)
				.mapColor(MapColor.TERRACOTTA_BLUE)
				.noOcclusion()));

	public static final RegistryObject<ConnectedGlassBlock> BLAST_PROOF_FRAMED_GLASS =
		BLOCKS.register("blast_proof_framed_glass",
			() -> new ConnectedGlassBlock(blastProofGlassProperties()));

	public static final Map<DyeColor, RegistryObject<BufferPadBlock>> BUFFER_PADS = registerBufferPads();
	public static final RegistryObject<BufferPadBlock> BUFFER_PAD = BUFFER_PADS.get(DyeColor.RED);

	public static final RegistryObject<ButterCatEngineBlock> CUTE_CAT_ON_SHAFT =
		BLOCKS.register("cute_cat_on_shaft",
			() -> new ButterCatEngineBlock(CBSharedProperties.createStone()
				.noOcclusion()
				.mapColor(MapColor.METAL)
				.forceSolidOff()));

	public static final RegistryObject<ButterCatEngineBlock> BUTTER_CAT_ENGINE =
		BLOCKS.register("butter_cat_engine",
			() -> new ButterCatEngineBlock(CBSharedProperties.createStone()
				.noOcclusion()
				.mapColor(MapColor.METAL)
				.forceSolidOff()));

	private static Block.Properties blastProofGlassProperties() {
		return CBSharedProperties.withExplosionProofResistance(CBSharedProperties.vanillaGlass());
	}

	private static Map<DyeColor, RegistryObject<BufferPadBlock>> registerBufferPads() {
		EnumMap<DyeColor, RegistryObject<BufferPadBlock>> bufferPads = new EnumMap<>(DyeColor.class);
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

	public static Iterable<RegistryObject<BufferPadBlock>> allBufferPads() {
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
