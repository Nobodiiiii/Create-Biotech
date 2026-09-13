package com.nobodiiiii.createbiotech.registry;

import java.util.Map;

import net.minecraft.core.registries.Registries;

import net.minecraft.core.registries.BuiltInRegistries;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.fluid.CreamBucketDispenseBehavior;
import com.nobodiiiii.createbiotech.content.buttercat.fluid.CreamFluidType;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeBlock;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeBottleItem;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeFluidType;
import com.nobodiiiii.createbiotech.content.fluid.TeleportationFluid;
import com.nobodiiiii.createbiotech.content.fluid.TeleportationLiquidBlock;
import com.nobodiiiii.createbiotech.foundation.fluid.CBFluidType;
import com.simibubi.create.content.fluids.VirtualFluid;
import org.joml.Vector3f;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.neoforged.neoforge.registries.DeferredHolder;

public class CBFluids {

	public static final DeferredRegister<FluidType> FLUID_TYPES =
		DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Fluid> FLUIDS =
		DeferredRegister.create(BuiltInRegistries.FLUID, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Block> FLUID_BLOCKS =
		DeferredRegister.create(Registries.BLOCK, CreateBiotech.MOD_ID);

	public static final DeferredRegister<Item> FLUID_ITEMS =
		DeferredRegister.create(BuiltInRegistries.ITEM, CreateBiotech.MOD_ID);

	private static final ResourceLocation EXPERIENCE_STILL_TEXTURE =
		CreateBiotech.asResource("fluid/experience_still");
	private static final ResourceLocation EXPERIENCE_FLOW_TEXTURE =
		CreateBiotech.asResource("fluid/experience_flow");
	private static final ResourceLocation NETHER_PORTAL_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("minecraft", "block/nether_portal");
	private static final ResourceLocation CREAM_STILL_TEXTURE =
		CreateBiotech.asResource("fluid/cream_still");
	private static final ResourceLocation CREAM_FLOW_TEXTURE =
		CreateBiotech.asResource("fluid/cream_flow");
	private static final Vector3f TELEPORTATION_SUBMERGED_FOG_COLOR = new Vector3f(0.72F, 0.48F, 0.86F);
	private static final float TELEPORTATION_FOG_DISTANCE_MODIFIER = 1F / 10F;

	public static final DeferredHolder<FluidType, CBFluidType> EXPERIENCE_TYPE =
		FLUID_TYPES.register("experience",
			() -> new CBFluidType(FluidType.Properties.create()
				.lightLevel(15), EXPERIENCE_STILL_TEXTURE, EXPERIENCE_FLOW_TEXTURE));

	public static final DeferredHolder<Fluid, VirtualFluid> EXPERIENCE =
		FLUIDS.register("experience", () -> VirtualFluid.createSource(experienceProperties()));

	public static final DeferredHolder<Fluid, VirtualFluid> EXPERIENCE_FLOWING =
		FLUIDS.register("flowing_experience", () -> VirtualFluid.createFlowing(experienceProperties()));

	public static final DeferredHolder<FluidType, FluidType> TELEPORTATION_TYPE =
		FLUID_TYPES.register("teleportation",
			() -> new CBFluidType(FluidType.Properties.create()
				.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
				.density(3000)
				.viscosity(6000)
				.lightLevel(11), NETHER_PORTAL_TEXTURE, NETHER_PORTAL_TEXTURE) {
				@Override
				protected Vector3f getCustomFogColor() {
					return TELEPORTATION_SUBMERGED_FOG_COLOR;
				}

				@Override
				protected float getFogDistanceModifier() {
					return TELEPORTATION_FOG_DISTANCE_MODIFIER;
				}
			});

	public static final DeferredHolder<Fluid, TeleportationFluid.Source> TELEPORTATION =
		FLUIDS.register("teleportation", () -> new TeleportationFluid.Source(teleportationProperties()));

	public static final DeferredHolder<Fluid, TeleportationFluid.Flowing> TELEPORTATION_FLOWING =
		FLUIDS.register("flowing_teleportation", () -> new TeleportationFluid.Flowing(teleportationProperties()));

	public static final DeferredHolder<Block, TeleportationLiquidBlock> TELEPORTATION_BLOCK =
		FLUID_BLOCKS.register("teleportation",
			() -> new TeleportationLiquidBlock(TELEPORTATION.get(), Block.Properties.of()
				.mapColor(MapColor.COLOR_PURPLE)
				.replaceable()
				.noCollission()
				.strength(100.0F)
				.lightLevel(state -> 11)
				.pushReaction(PushReaction.DESTROY)
				.noLootTable()
				.liquid()
				.sound(SoundType.EMPTY)));

	public static final DeferredHolder<Item, BucketItem> TELEPORTATION_BUCKET =
		FLUID_ITEMS.register("teleportation_bucket",
			() -> new BucketItem(TELEPORTATION.get(), new Item.Properties()
				.craftRemainder(Items.BUCKET)
				.stacksTo(1)));

	public static final DeferredHolder<FluidType, LiquidLivingSlimeFluidType> LIQUID_LIVING_SLIME_TYPE =
		FLUID_TYPES.register("liquid_living_slime",
			() -> new LiquidLivingSlimeFluidType(FluidType.Properties.create()
				.motionScale(0.004D)
				.fallDistanceModifier(0F)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.SLIME_JUMP)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.SLIME_JUMP)
				.viscosity(5000)
				.density(1400)));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_LIVING_SLIME =
		FLUIDS.register("liquid_living_slime",
			() -> new BaseFlowingFluid.Source(CBFluids.liquidLivingSlimeProperties()));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> LIQUID_LIVING_SLIME_FLOWING =
		FLUIDS.register("liquid_living_slime_flowing",
			() -> new BaseFlowingFluid.Flowing(CBFluids.liquidLivingSlimeProperties()));

	public static final DeferredHolder<Block, LiquidLivingSlimeBlock> LIQUID_LIVING_SLIME_BLOCK =
		FLUID_BLOCKS.register("liquid_living_slime",
			() -> new LiquidLivingSlimeBlock(
				(net.minecraft.world.level.material.FlowingFluid) LIQUID_LIVING_SLIME.get(),
				Block.Properties.of()
				.noCollission()
				.sound(SoundType.SLIME_BLOCK)
				.strength(-1.0F, 100.0F)
				.dynamicShape()
				.noLootTable()
				.liquid()));

	public static final DeferredHolder<Item, BucketItem> LIQUID_LIVING_SLIME_BUCKET =
		FLUID_ITEMS.register("liquid_living_slime_bucket",
			() -> new BucketItem(LIQUID_LIVING_SLIME.get(), new Item.Properties()
				.craftRemainder(Items.BUCKET)
				.stacksTo(1)));

	public static final DeferredHolder<Item, LiquidLivingSlimeBottleItem> LIQUID_LIVING_SLIME_BOTTLE =
		FLUID_ITEMS.register("liquid_living_slime_bottle",
			() -> new LiquidLivingSlimeBottleItem(new Item.Properties()
				.craftRemainder(Items.GLASS_BOTTLE)
				.food(new FoodProperties.Builder()
					.nutrition(6)
					.saturationModifier(0.1F)
					.effect(new MobEffectInstance(MobEffects.OOZING, 5 * 20), 1.0F)
					.effect(new MobEffectInstance(CBMobEffects.BOUNCING, 30 * 20), 1.0F)
					.usingConvertsTo(Items.GLASS_BOTTLE)
					.build())
				.stacksTo(1)));

	public static final DeferredHolder<FluidType, CreamFluidType> CREAM_TYPE = FLUID_TYPES.register("cream",
		() -> new CreamFluidType(FluidType.Properties.create()
			.viscosity(100)
			.canSwim(false)
			.canPushEntity(false), CREAM_STILL_TEXTURE, CREAM_FLOW_TEXTURE));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREAM = FLUIDS.register("cream",
		() -> new BaseFlowingFluid.Source(creamProperties()));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CREAM_FLOWING = FLUIDS.register("flowing_cream",
		() -> new BaseFlowingFluid.Flowing(creamProperties()));

	public static final DeferredHolder<Block, LiquidBlock> CREAM_BLOCK = FLUID_BLOCKS.register("cream",
		() -> new LiquidBlock(CREAM.get(), Block.Properties.ofFullCopy(Blocks.WATER)
			.mapColor(MapColor.TERRACOTTA_WHITE)));

	public static final DeferredHolder<Item, BucketItem> CREAM_BUCKET = FLUID_ITEMS.register("cream_bucket",
		() -> new BucketItem(CREAM.get(), new Item.Properties()
			.craftRemainder(Items.BUCKET)
			.stacksTo(1)));

	private static BaseFlowingFluid.Properties experienceProperties() {
		return new BaseFlowingFluid.Properties(EXPERIENCE_TYPE, EXPERIENCE, EXPERIENCE_FLOWING);
	}

	private static BaseFlowingFluid.Properties teleportationProperties() {
		return new BaseFlowingFluid.Properties(TELEPORTATION_TYPE, TELEPORTATION, TELEPORTATION_FLOWING)
			.bucket(TELEPORTATION_BUCKET)
			.block(TELEPORTATION_BLOCK)
			.levelDecreasePerBlock(2)
			.tickRate(30)
			.slopeFindDistance(2)
			.explosionResistance(100f);
	}

	private static BaseFlowingFluid.Properties liquidLivingSlimeProperties() {
		return new BaseFlowingFluid.Properties(
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

	private static BaseFlowingFluid.Properties creamProperties() {
		return new BaseFlowingFluid.Properties(CREAM_TYPE, CREAM, CREAM_FLOWING)
			.bucket(CREAM_BUCKET)
			.block(CREAM_BLOCK)
			.levelDecreasePerBlock(2)
			.tickRate(60)
			.slopeFindDistance(2)
			.explosionResistance(50F);
	}

	private CBFluids() {}

	public static void register(IEventBus modEventBus) {
		FLUID_TYPES.register(modEventBus);
		FLUIDS.register(modEventBus);
		FLUID_BLOCKS.register(modEventBus);
		FLUID_ITEMS.register(modEventBus);
	}

	public static void registerFluidInteractions() {
		Map<FluidType, Block> flowingLavaResults = Map.of(
			CREAM_TYPE.get(), Blocks.TUFF,
			TELEPORTATION_TYPE.get(), Blocks.NETHERRACK,
			LIQUID_LIVING_SLIME_TYPE.get(), Blocks.MUD);

		FLUID_TYPES.getEntries()
			.forEach(fluidType -> {
				Block flowingLavaResult = flowingLavaResults.getOrDefault(fluidType.get(), Blocks.COBBLESTONE);
				FluidInteractionRegistry.addInteraction(
					NeoForgeMod.LAVA_TYPE.value(),
					new InteractionInformation(fluidType.get(),
						fluidState -> fluidState.isSource()
							? Blocks.OBSIDIAN.defaultBlockState()
							: flowingLavaResult.defaultBlockState()));
			});
	}

	public static void registerCreamDispenseBehavior() {
		DispenserBlock.registerBehavior(CREAM_BUCKET.get(), new CreamBucketDispenseBehavior());
	}
}
