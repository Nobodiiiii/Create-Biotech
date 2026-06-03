package com.nobodiiiii.createbiotech.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetBlock;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlock;
import com.nobodiiiii.createbiotech.content.bufferpad.BufferPadBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.experience.BuddingExperienceBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceCrystallizerBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceTankBlock;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceEncasedPipeBlock;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperiencePipeBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonAssemblyStationBlock;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmBlock;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.petridish.PetriDishBlock;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimeclutch.SlimeClutchBlock;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlock;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlock;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableCogBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.ExplosionProofCasingBlock;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
		() -> new SlimeBeltBlock(Block.Properties.of()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_LIGHT_GREEN)
			.noOcclusion()));

	public static final RegistryObject<MagmaBeltBlock> MAGMA_BELT = BLOCKS.register("magma_belt",
		() -> new MagmaBeltBlock(Block.Properties.of()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_RED)
			.noOcclusion()));

	public static final RegistryObject<PowerBeltBlock> POWER_BELT = BLOCKS.register("power_belt",
		() -> new PowerBeltBlock(Block.Properties.of()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_GRAY)
			.noOcclusion()));

	public static final RegistryObject<EvokerEnchantingChamberBlock> EVOKER_ENCHANTING_CHAMBER =
		BLOCKS.register("evoker_enchanting_chamber",
			() -> new EvokerEnchantingChamberBlock(Block.Properties.of()
				.sound(SoundType.COPPER)
				.strength(2.5f)
				.mapColor(MapColor.METAL)
				.noOcclusion()));

	public static final RegistryObject<ExperiencePumpBlock> EXPERIENCE_PUMP = BLOCKS.register("experience_pump",
		() -> new ExperiencePumpBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<ExperienceCrystallizerBlock> EXPERIENCE_CRYSTALLIZER =
		BLOCKS.register("experience_crystallizer",
			() -> new ExperienceCrystallizerBlock(Block.Properties.of()
				.sound(SoundType.WOOD)
				.strength(2.0f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<BuddingExperienceBlock> BUDDING_EXPERIENCE =
		BLOCKS.register("budding_experience",
			() -> new BuddingExperienceBlock(Block.Properties.of()
				.sound(SoundType.AMETHYST)
				.strength(1.5f)
				.mapColor(MapColor.COLOR_PURPLE)
				.randomTicks()));

	public static final RegistryObject<ExperienceClusterBlock> SMALL_EXPERIENCE_BUD =
		BLOCKS.register("small_experience_bud",
			() -> new ExperienceClusterBlock(3, 4, ExperienceConstants.SMALL_BUD_NUGGET_VALUE, clusterProperties()));

	public static final RegistryObject<ExperienceClusterBlock> MEDIUM_EXPERIENCE_BUD =
		BLOCKS.register("medium_experience_bud",
			() -> new ExperienceClusterBlock(4, 3, ExperienceConstants.MEDIUM_BUD_NUGGET_VALUE, clusterProperties()));

	public static final RegistryObject<ExperienceClusterBlock> LARGE_EXPERIENCE_BUD =
		BLOCKS.register("large_experience_bud",
			() -> new ExperienceClusterBlock(5, 3, ExperienceConstants.LARGE_BUD_NUGGET_VALUE, clusterProperties()));

	public static final RegistryObject<ExperienceClusterBlock> EXPERIENCE_CLUSTER =
		BLOCKS.register("experience_cluster",
			() -> new ExperienceClusterBlock(7, 3, ExperienceConstants.CLUSTER_NUGGET_VALUE, clusterProperties()));

	public static final RegistryObject<ExperienceTankBlock> EXPERIENCE_TANK = BLOCKS.register("experience_tank",
		() -> new ExperienceTankBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()
			.isRedstoneConductor((p1, p2, p3) -> true)));

	public static final RegistryObject<ExperiencePipeBlock> EXPERIENCE_PIPE = BLOCKS.register("experience_pipe",
		() -> new ExperiencePipeBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.0f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<ExperienceEncasedPipeBlock> ENCASED_EXPERIENCE_PIPE =
		BLOCKS.register("encased_experience_pipe",
			() -> new ExperienceEncasedPipeBlock(Block.Properties.of()
				.sound(SoundType.COPPER)
				.strength(2.0f)
				.mapColor(MapColor.METAL)
				.noOcclusion(), com.simibubi.create.AllBlocks.COPPER_CASING::get));

	public static final RegistryObject<SquidPrinterBlock> SQUID_PRINTER = BLOCKS.register("squid_printer",
		() -> new SquidPrinterBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.0f)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.noOcclusion()));

	public static final RegistryObject<PetriDishBlock> PETRI_DISH = BLOCKS.register("petri_dish",
		() -> new PetriDishBlock(Block.Properties.of()
			.sound(SoundType.GLASS)
			.strength(1.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<UniversalJointBlock> UNIVERSAL_JOINT = BLOCKS.register("universal_joint",
		() -> new UniversalJointBlock(Block.Properties.of()
			.sound(SoundType.STONE)
			.strength(0.8f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<SlimeClutchBlock> SLIME_CLUTCH = BLOCKS.register("slime_clutch",
		() -> new SlimeClutchBlock(Block.Properties.of()
			.sound(SoundType.WOOD)
			.strength(0.8f)
			.mapColor(MapColor.PODZOL)
			.noOcclusion()));

	public static final RegistryObject<BoneRatchetBlock> BONE_RATCHET = BLOCKS.register("bone_ratchet",
		() -> new BoneRatchetBlock(Block.Properties.of()
			.sound(SoundType.BONE_BLOCK)
			.strength(0.8f)
			.mapColor(MapColor.SAND)
			.noOcclusion()));

	public static final RegistryObject<FixedCarrotFishingRodBlock> FIXED_CARROT_FISHING_ROD =
		BLOCKS.register("fixed_carrot_fishing_rod",
			() -> new FixedCarrotFishingRodBlock(Block.Properties.of()
				.sound(SoundType.WOOD)
				.strength(0.4f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<GhastHotAirBalloonAssemblyStationBlock> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		BLOCKS.register("ghast_hot_air_balloon_assembly_station",
			() -> new GhastHotAirBalloonAssemblyStationBlock(Block.Properties.of()
				.sound(SoundType.WOOD)
				.strength(2.0f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<GhastHelmBlock> GHAST_HELM = BLOCKS.register("ghast_helm",
		() -> new GhastHelmBlock(Block.Properties.of()
			.sound(SoundType.WOOD)
			.strength(2.0f)
			.mapColor(MapColor.WOOD)
			.noOcclusion()));

	public static final RegistryObject<SchrodingersCatBlock> SCHRODINGERS_CAT =
		BLOCKS.register("schrodingers_cat",
			() -> new SchrodingersCatBlock(Block.Properties.of()
				.sound(SoundType.WOOL)
				.strength(0.8f)
				.mapColor(MapColor.COLOR_BROWN)
				.noOcclusion()));

	public static final RegistryObject<SpiderAssemblyTableBlock> SPIDER_ASSEMBLY_TABLE =
		BLOCKS.register("spider_assembly_table",
			() -> new SpiderAssemblyTableBlock(Block.Properties.of()
				.sound(SoundType.WOOL)
				.strength(1.2f)
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final RegistryObject<SpiderAssemblyTableCogBlock> SPIDER_ASSEMBLY_TABLE_COG =
		BLOCKS.register("spider_assembly_table_cog",
			() -> new SpiderAssemblyTableCogBlock(Block.Properties.of()
				.sound(SoundType.WOOL)
				.strength(1.2f)
				.mapColor(MapColor.COLOR_BLACK)
				.noOcclusion()));

	public static final RegistryObject<CreeperBlastChamberBlock> CREEPER_BLAST_CHAMBER =
		BLOCKS.register("creeper_blast_chamber",
			() -> new CreeperBlastChamberBlock(Block.Properties.of()
				.sound(SoundType.NETHERITE_BLOCK)
				.strength(50.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.mapColor(MapColor.COLOR_GRAY)
				.noOcclusion()));

	public static final RegistryObject<CasingBlock> ASURINE_CASING =
		BLOCKS.register("asurine_casing",
			() -> new CasingBlock(Block.Properties.copy(Blocks.ANDESITE)
				.sound(SoundType.WOOD)
				.mapColor(MapColor.COLOR_LIGHT_BLUE)));

	public static final RegistryObject<ExplosionProofCasingBlock> EXPLOSION_PROOF_CASING =
		BLOCKS.register("explosion_proof_casing",
			() -> new ExplosionProofCasingBlock(Block.Properties.of()
				.sound(SoundType.NETHERITE_BLOCK)
				.strength(50.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.mapColor(MapColor.COLOR_GRAY)));

	public static final RegistryObject<ExplosionProofItemVaultBlock> EXPLOSION_PROOF_ITEM_VAULT =
		BLOCKS.register("explosion_proof_item_vault",
			() -> new ExplosionProofItemVaultBlock(Block.Properties.copy(Blocks.GOLD_BLOCK)
				.mapColor(MapColor.COLOR_GRAY)
				.sound(SoundType.NETHERITE_BLOCK)
				.explosionResistance(1200.0f)));

	public static final RegistryObject<GlassBlock> BLAST_PROOF_GLASS =
		BLOCKS.register("blast_proof_glass",
			() -> new GlassBlock(blastProofGlassProperties()));

	public static final RegistryObject<BlastProofChainDriveBlock> BLAST_PROOF_CHAIN_DRIVE =
			BLOCKS.register("blast_proof_chain_drive",
				() -> new BlastProofChainDriveBlock(Block.Properties.of()
					.sound(SoundType.NETHERITE_BLOCK)
					.strength(50.0f, 1200.0f)
					.requiresCorrectToolForDrops()
					.noOcclusion()
					.mapColor(MapColor.COLOR_GRAY)));

	public static final RegistryObject<BioPackagerBlock> BIO_PACKAGER = BLOCKS.register("bio_packager",
		() -> new BioPackagerBlock(Block.Properties.of()
			.sound(SoundType.WOOD)
			.strength(2.0f)
			.mapColor(MapColor.WOOD)
			.noOcclusion()));

	public static final RegistryObject<ConnectedGlassBlock> BLAST_PROOF_FRAMED_GLASS =
		BLOCKS.register("blast_proof_framed_glass",
			() -> new ConnectedGlassBlock(blastProofGlassProperties()));

	public static final Map<DyeColor, RegistryObject<BufferPadBlock>> BUFFER_PADS = registerBufferPads();
	public static final RegistryObject<BufferPadBlock> BUFFER_PAD = BUFFER_PADS.get(DyeColor.RED);

	private static Block.Properties blastProofGlassProperties() {
		return Block.Properties.copy(Blocks.GLASS)
			.strength(50.0f, 1200.0f);
	}

	private static Block.Properties clusterProperties() {
		return Block.Properties.of()
			.sound(SoundType.AMETHYST_CLUSTER)
			.strength(1.5f)
			.mapColor(MapColor.COLOR_PURPLE)
			.noOcclusion()
			.lightLevel(state -> 5);
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
}
