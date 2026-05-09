package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlock;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlock;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlock;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SoundType;
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

	public static final RegistryObject<EvokerTankBlock> EVOKER_TANK = BLOCKS.register("evoker_tank",
		() -> new EvokerTankBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<UniversalJointBlock> UNIVERSAL_JOINT = BLOCKS.register("universal_joint",
		() -> new UniversalJointBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(0.8f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	public static final RegistryObject<FixedCarrotFishingRodBlock> FIXED_CARROT_FISHING_ROD =
		BLOCKS.register("fixed_carrot_fishing_rod",
			() -> new FixedCarrotFishingRodBlock(Block.Properties.of()
				.sound(SoundType.WOOD)
				.strength(0.4f)
				.mapColor(MapColor.WOOD)
				.noOcclusion()));

	public static final RegistryObject<SchrodingersCatBlock> SCHRODINGERS_CAT =
		BLOCKS.register("schrodingers_cat",
			() -> new SchrodingersCatBlock(Block.Properties.of()
				.sound(SoundType.WOOL)
				.strength(0.8f)
				.mapColor(MapColor.COLOR_BROWN)
				.noOcclusion()));

	public static final RegistryObject<CreeperBlastChamberBlock> CREEPER_BLAST_CHAMBER =
		BLOCKS.register("creeper_blast_chamber",
			() -> new CreeperBlastChamberBlock(Block.Properties.of()
				.sound(SoundType.COPPER)
				.strength(2.5f)
				.mapColor(MapColor.METAL)
				.noOcclusion()));

	public static final RegistryObject<CasingBlock> EXPLOSION_PROOF_CASING =
		BLOCKS.register("explosion_proof_casing",
			() -> new CasingBlock(Block.Properties.of()
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
			() -> new GlassBlock(Block.Properties.of()
				.sound(SoundType.GLASS)
				.strength(50.0f, 1200.0f)
				.noOcclusion()
				.mapColor(MapColor.NONE)));

	public static final RegistryObject<BlastProofChainDriveBlock> BLAST_PROOF_CHAIN_DRIVE =
			BLOCKS.register("blast_proof_chain_drive",
				() -> new BlastProofChainDriveBlock(Block.Properties.of()
					.sound(SoundType.NETHERITE_BLOCK)
					.strength(50.0f, 1200.0f)
					.requiresCorrectToolForDrops()
					.noOcclusion()
					.mapColor(MapColor.COLOR_GRAY)));

	public static final RegistryObject<ConnectedGlassBlock> BLAST_PROOF_FRAMED_GLASS =
		BLOCKS.register("blast_proof_framed_glass",
			() -> new ConnectedGlassBlock(Block.Properties.of()
				.sound(SoundType.GLASS)
				.strength(50.0f, 1200.0f)
				.noOcclusion()
				.mapColor(MapColor.NONE)));

	private CBBlocks() {}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
