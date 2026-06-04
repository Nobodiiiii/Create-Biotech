package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModFluids;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeFluidType;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.SoundActions;
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
				.motionScale(0.004D)
				.fallDistanceModifier(0F)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.SLIME_JUMP)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.SLIME_JUMP)
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
				.sound(SoundType.SLIME_BLOCK)
				.strength(100f)
				.noLootTable()
				.liquid()));

	public static final RegistryObject<BucketItem> LIQUID_LIVING_SLIME_BUCKET =
		FLUID_ITEMS.register("liquid_living_slime_bucket",
			() -> new BucketItem(LIQUID_LIVING_SLIME, new Item.Properties()
				.craftRemainder(Items.BUCKET)
				.stacksTo(1)));

	// Butter Cat content is registered through the shared ButterCat registrate, and re-exported
	// here so the project's primary fluid registry remains the place to inspect mod fluids.
	public static final FluidEntry<ForgeFlowingFluid.Flowing> CREAM = ModFluids.CREAM;

	private static ForgeFlowingFluid.Properties liquidLivingSlimeProperties() {
		return new ForgeFlowingFluid.Properties(
			LIQUID_LIVING_SLIME_TYPE,
			LIQUID_LIVING_SLIME,
			LIQUID_LIVING_SLIME_FLOWING)
			.bucket(LIQUID_LIVING_SLIME_BUCKET)
			.block(LIQUID_LIVING_SLIME_BLOCK)
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
