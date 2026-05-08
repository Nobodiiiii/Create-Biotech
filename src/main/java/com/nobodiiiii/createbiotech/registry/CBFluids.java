package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeFluidType;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBFluids {

	public static final DeferredRegister<FluidType> FLUID_TYPES =
		DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Fluid> FLUIDS =
		DeferredRegister.create(ForgeRegistries.FLUIDS, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Block> FLUID_BLOCKS =
		DeferredRegister.create(ForgeRegistries.BLOCKS, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Item> FLUID_ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, CreateBiotech.MOD_ID);

	public static final RegistryObject<LiquidLivingSlimeFluidType> LIQUID_LIVING_SLIME_TYPE =
		FLUID_TYPES.register("liquid_living_slime",
			() -> new LiquidLivingSlimeFluidType(FluidType.Properties.create()
				.viscosity(5000)
				.density(1400)));

	public static final RegistryObject<ForgeFlowingFluid.Source> LIQUID_LIVING_SLIME =
		FLUIDS.register("liquid_living_slime",
			() -> new ForgeFlowingFluid.Source(CBFluids.liquidLivingSlimeProperties()));

	public static final RegistryObject<ForgeFlowingFluid.Flowing> LIQUID_LIVING_SLIME_FLOWING =
		FLUIDS.register("liquid_living_slime_flowing",
			() -> new ForgeFlowingFluid.Flowing(CBFluids.liquidLivingSlimeProperties()));

	public static final RegistryObject<LiquidBlock> LIQUID_LIVING_SLIME_BLOCK =
		FLUID_BLOCKS.register("liquid_living_slime",
			() -> new LiquidBlock(LIQUID_LIVING_SLIME, Block.Properties.of()
				.noCollission()
				.strength(100f)
				.noLootTable()
				.liquid()));

	public static final RegistryObject<BucketItem> LIQUID_LIVING_SLIME_BUCKET =
		FLUID_ITEMS.register("liquid_living_slime_bucket",
			() -> new BucketItem(LIQUID_LIVING_SLIME, new Item.Properties()
				.craftRemainder(Items.BUCKET)
				.stacksTo(1)));

	private static ForgeFlowingFluid.Properties liquidLivingSlimeProperties() {
		return new ForgeFlowingFluid.Properties(
			LIQUID_LIVING_SLIME_TYPE,
			LIQUID_LIVING_SLIME,
			LIQUID_LIVING_SLIME_FLOWING)
			.levelDecreasePerBlock(2)
			.tickRate(60)
			.slopeFindDistance(4)
			.explosionResistance(100f);
	}

	private CBFluids() {}

	public static void register(IEventBus modEventBus) {
		FLUID_TYPES.register(modEventBus);
		FLUIDS.register(modEventBus);
		FLUID_BLOCKS.register(modEventBus);
		FLUID_ITEMS.register(modEventBus);
	}
}
