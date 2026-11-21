package com.nobodiiiii.createbiotech.registry;

import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.block.SuperButterStreak;
import com.nobodiiiii.createbiotech.entity.inventory.DevourerInventory;

import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CBAttachmentTypes {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CreateBiotech.MOD_ID);

	public static final Supplier<AttachmentType<SuperButterStreak>> SUPER_BUTTER_STREAK =
		ATTACHMENT_TYPES.register("super_butter_streak",
			() -> AttachmentType.builder(SuperButterStreak::new).build());

	public static final DeferredHolder<AttachmentType<?>, AttachmentType<DevourerInventory>> DEVOURER_INVENTORY =
		ATTACHMENT_TYPES.register("devourer_inventory",
			() -> AttachmentType.serializable(holder -> new DevourerInventory((Mob) holder)).build());

	private CBAttachmentTypes() {}

	public static void register(IEventBus modEventBus) {
		ATTACHMENT_TYPES.register(modEventBus);
	}
}
