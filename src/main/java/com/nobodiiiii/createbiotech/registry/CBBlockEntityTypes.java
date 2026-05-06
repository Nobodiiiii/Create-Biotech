package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankBlockEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlockEntity;

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

	public static final RegistryObject<BlockEntityType<EvokerTankBlockEntity>> EVOKER_TANK =
		BLOCK_ENTITY_TYPES.register("evoker_tank",
			() -> BlockEntityType.Builder.of(EvokerTankBlockEntity::new, CBBlocks.EVOKER_TANK.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<UniversalJointBlockEntity>> UNIVERSAL_JOINT =
		BLOCK_ENTITY_TYPES.register("universal_joint",
			() -> BlockEntityType.Builder.of(UniversalJointBlockEntity::new, CBBlocks.UNIVERSAL_JOINT.get())
				.build(null));

	private CBBlockEntityTypes() {}

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITY_TYPES.register(modEventBus);
	}
}
