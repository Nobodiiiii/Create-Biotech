package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;

import net.minecraft.world.level.block.Block;
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

	public static final RegistryObject<EvokerTankBlock> EVOKER_TANK = BLOCKS.register("evoker_tank",
		() -> new EvokerTankBlock(Block.Properties.of()
			.sound(SoundType.COPPER)
			.strength(2.5f)
			.mapColor(MapColor.METAL)
			.noOcclusion()));

	private CBBlocks() {}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
