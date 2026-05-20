package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;

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

	private CBParticleTypes() {
	}

	public static void register(IEventBus modEventBus) {
		PARTICLE_TYPES.register(modEventBus);
	}
}
