package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;

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

	private CBBlockEntityTypes() {}

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITY_TYPES.register(modEventBus);
	}
}
