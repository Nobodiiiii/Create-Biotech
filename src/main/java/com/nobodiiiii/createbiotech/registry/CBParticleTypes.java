package com.nobodiiiii.createbiotech.registry;

import com.mojang.serialization.Codec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicConeWaveParticleOption;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterInkParticleOption;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBParticleTypes {

	public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
		DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, CreateBiotech.MOD_ID);

	public static final RegistryObject<SimpleParticleType> STRAIGHT_ENCHANT =
		PARTICLE_TYPES.register("straight_enchant", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> ALLAY_COURIER_NOTE =
		PARTICLE_TYPES.register("allay_courier_note", () -> new SimpleParticleType(false));
	public static final RegistryObject<ParticleType<SquidPrinterInkParticleOption>> SQUID_PRINTER_INK =
		PARTICLE_TYPES.register("squid_printer_ink",
			() -> new ParticleType<SquidPrinterInkParticleOption>(false, SquidPrinterInkParticleOption.DESERIALIZER) {
				@Override
				public Codec<SquidPrinterInkParticleOption> codec() {
					return SquidPrinterInkParticleOption.CODEC;
				}
			});

	public static final RegistryObject<ParticleType<SonicConeWaveParticleOption>> SONIC_CONE_WAVE =
		PARTICLE_TYPES.register("sonic_cone_wave",
			() -> new ParticleType<SonicConeWaveParticleOption>(true, SonicConeWaveParticleOption.DESERIALIZER) {
				@Override
				public Codec<SonicConeWaveParticleOption> codec() {
					return SonicConeWaveParticleOption.CODEC;
				}
			});

	private CBParticleTypes() {
	}

	public static void register(IEventBus modEventBus) {
		PARTICLE_TYPES.register(modEventBus);
	}
}
