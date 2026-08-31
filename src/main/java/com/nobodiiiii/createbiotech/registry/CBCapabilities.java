package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltPort;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltPortResolver;
import com.nobodiiiii.createbiotech.content.fluid.LiquidLivingSlimeBottleFluidHandler;
import com.simibubi.create.AllBlockEntityTypes;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class CBCapabilities {

	private CBCapabilities() {}

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerItem(Capabilities.FluidHandler.ITEM,
			(stack, context) -> new LiquidLivingSlimeBottleFluidHandler(stack),
			CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get());
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.BIO_PACKAGER.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.SHULKER_PACKAGER.get(),
			(be, side) -> be.shulkerInventory);
		// Registered per block rather than per block entity: the chamber's upper half has
		// no block entity of its own, and pipes docking there still have to reach the
		// controller below.
		event.registerBlock(Capabilities.ItemHandler.BLOCK,
			(level, pos, state, be, side) -> {
				EvokerEnchantingChamberBlockEntity chamber =
					EvokerEnchantingChamberBlockEntity.resolveController(level, pos, state, be);
				return chamber == null ? null : chamber.getItemCapability(side);
			},
			CBBlocks.EVOKER_ENCHANTING_CHAMBER.get());
		event.registerBlock(Capabilities.FluidHandler.BLOCK,
			(level, pos, state, be, side) -> {
				EvokerEnchantingChamberBlockEntity chamber =
					EvokerEnchantingChamberBlockEntity.resolveController(level, pos, state, be);
				return chamber == null ? null : chamber.getFluidCapability(side);
			},
			CBBlocks.EVOKER_ENCHANTING_CHAMBER.get());
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.BUDDING_EXPERIENCE.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.EXPERIENCE_PUMP.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.NETHER_PORTAL_FLUID.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.FIXED_CARROT_FISHING_ROD.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.PETRI_DISH.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.PETRI_DISH.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.SQUID_PRINTER.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CBBlockEntityTypes.MAGMA_CUBE_BURNER.get(),
			(be, side) -> be.getFluidCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.SLIME_BELT.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AllBlockEntityTypes.ANDESITE_TUNNEL.get(),
			(be, side) -> {
				if (be.getLevel() == null)
					return null;
				StandardItemBeltPort belt =
					StandardItemBeltPortResolver.getHorizontalPort(be.getLevel(), be.getBlockPos().below());
				return belt == null ? null : belt.createBiotech$getItemHandler();
			});
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.MAGMA_BELT.get(),
			(be, side) -> be.getItemCapability(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CBBlockEntityTypes.ALLAY_PORT.get(),
			(be, side) -> be.getItemHandler(side));
	}
}
