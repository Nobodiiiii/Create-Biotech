package com.nobodiiiii.createbiotech.registry;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierCargo;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalToolSource;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CBDataComponents {
	private static final DeferredRegister.DataComponents COMPONENTS =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBiotech.MOD_ID);

	public static final Supplier<DataComponentType<AllayCourierCargo>> ALLAY_COURIER_CARGO =
		COMPONENTS.registerComponentType("allay_courier_cargo",
			builder -> builder.persistent(AllayCourierCargo.CODEC)
				.networkSynchronized(AllayCourierCargo.STREAM_CODEC));

	public static final Supplier<DataComponentType<Integer>> ALLAY_COURIER_HEADING =
		COMPONENTS.registerComponentType("allay_courier_heading",
			builder -> builder.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.INT));

	/**
	 * The Frog Stomach room index bound to a {@code GIANT_FROG}. Travels on the dropped item so
	 * breaking and re-placing the frog keeps the same private room.
	 */
	public static final Supplier<DataComponentType<Long>> FROG_STOMACH_SPACE =
		COMPONENTS.registerComponentType("frog_stomach_space",
			builder -> builder.persistent(Codec.LONG)
				.networkSynchronized(ByteBufCodecs.VAR_LONG));

	/** Versioned packed entries recording the Big Dog Sonic Cannon upgrades in installation order. */
	public static final Supplier<DataComponentType<Integer>> SONIC_DOG_CANNON_UPGRADES =
		COMPONENTS.registerComponentType("sonic_dog_cannon_upgrades",
			builder -> builder.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final Supplier<DataComponentType<DyeColor>> SONIC_DOG_CANNON_COLLAR_COLOR =
		COMPONENTS.registerComponentType("sonic_dog_cannon_collar_color",
			builder -> builder.persistent(DyeColor.CODEC)
				.networkSynchronized(DyeColor.STREAM_CODEC));

	public static final Supplier<DataComponentType<Boolean>> SONIC_DOG_CANNON_SCOPE_FOLDED =
		COMPONENTS.registerComponentType("sonic_dog_cannon_scope_folded",
			builder -> builder.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL));

	public static final Supplier<DataComponentType<SurgicalToolSource>> SURGICAL_TOOL_SOURCE =
		COMPONENTS.registerComponentType("surgical_tool_source",
			builder -> builder.persistent(SurgicalToolSource.CODEC)
				.networkSynchronized(SurgicalToolSource.STREAM_CODEC));

	private CBDataComponents() {
	}

	public static void register(IEventBus modEventBus) {
		COMPONENTS.register(modEventBus);
	}
}
