package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicBoomEnchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CBEnchantments {

	private static final DeferredRegister<Enchantment> ENCHANTMENTS =
		DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CreateBiotech.MOD_ID);

	public static final RegistryObject<Enchantment> SONIC_BOOM =
		ENCHANTMENTS.register("sonic_boom", SonicBoomEnchantment::new);

	private CBEnchantments() {}

	public static void register(IEventBus modEventBus) {
		ENCHANTMENTS.register(modEventBus);
	}
}
