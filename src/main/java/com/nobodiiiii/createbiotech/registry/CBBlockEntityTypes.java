package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankBlockEntity;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodBlockEntity;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatBlockEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlockEntity;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.BlastProofChainDriveBlockEntity;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

	public static final RegistryObject<BlockEntityType<EvokerTankBlockEntity>> EVOKER_TANK =
		BLOCK_ENTITY_TYPES.register("evoker_tank",
			() -> BlockEntityType.Builder.of(EvokerTankBlockEntity::new, CBBlocks.EVOKER_TANK.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<UniversalJointBlockEntity>> UNIVERSAL_JOINT =
		BLOCK_ENTITY_TYPES.register("universal_joint",
			() -> BlockEntityType.Builder.of(UniversalJointBlockEntity::new, CBBlocks.UNIVERSAL_JOINT.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<FixedCarrotFishingRodBlockEntity>> FIXED_CARROT_FISHING_ROD =
		BLOCK_ENTITY_TYPES.register("fixed_carrot_fishing_rod",
			() -> BlockEntityType.Builder
				.of(FixedCarrotFishingRodBlockEntity::new, CBBlocks.FIXED_CARROT_FISHING_ROD.get())
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

	private CBBlockEntityTypes() {}

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITY_TYPES.register(modEventBus);
	}
}
