package com.nobodiiiii.createbiotech.infrastructure;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.MissingMappingsEvent.Mapping;

import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class CBRemapHelper {
	private static final Logger LOGGER = LogUtils.getLogger();

	private CBRemapHelper() {
	}

	@SubscribeEvent
	public static void remapBlocks(MissingMappingsEvent event) {
		for (Mapping<Block> mapping : event.getMappings(Registries.BLOCK, CreateBiotech.MOD_ID)) {
			Block remapped = switch (mapping.getKey()
				.getPath()) {
				case "experience_pipe" -> AllBlocks.FLUID_PIPE.get();
				case "encased_experience_pipe" -> AllBlocks.ENCASED_FLUID_PIPE.get();
				case "experience_tank" -> AllBlocks.FLUID_TANK.get();
				default -> null;
			};
			remap(mapping, remapped);
		}
	}

	@SubscribeEvent
	public static void remapItems(MissingMappingsEvent event) {
		for (Mapping<Item> mapping : event.getMappings(Registries.ITEM, CreateBiotech.MOD_ID)) {
			Item remapped = switch (mapping.getKey()
				.getPath()) {
				case "experience_pipe", "encased_experience_pipe" -> AllBlocks.FLUID_PIPE.asItem();
				case "experience_tank" -> AllBlocks.FLUID_TANK.asItem();
				default -> null;
			};
			remap(mapping, remapped);
		}
	}

	@SubscribeEvent
	public static void remapBlockEntities(MissingMappingsEvent event) {
		for (Mapping<BlockEntityType<?>> mapping : event.getMappings(Registries.BLOCK_ENTITY_TYPE,
			CreateBiotech.MOD_ID)) {
			BlockEntityType<?> remapped = switch (mapping.getKey()
				.getPath()) {
				case "experience_pipe" -> AllBlockEntityTypes.FLUID_PIPE.get();
				case "encased_experience_pipe" -> AllBlockEntityTypes.ENCASED_FLUID_PIPE.get();
				case "experience_tank" -> AllBlockEntityTypes.FLUID_TANK.get();
				default -> null;
			};
			remap(mapping, remapped);
		}
	}

	private static <T> void remap(Mapping<T> mapping, T remapped) {
		if (remapped == null)
			return;
		try {
			LOGGER.warn("Remapping legacy Create Biotech entry '{}' to '{}'", mapping.getKey(), remapped);
			mapping.remap(remapped);
		} catch (Throwable t) {
			LOGGER.warn("Failed to remap legacy Create Biotech entry '{}'", mapping.getKey(), t);
		}
	}
}
