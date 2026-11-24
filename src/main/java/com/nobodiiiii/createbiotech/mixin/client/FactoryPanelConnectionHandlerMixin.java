package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.state.BlockState;

@Mixin(FactoryPanelConnectionHandler.class)
public abstract class FactoryPanelConnectionHandlerMixin {
	@WrapOperation(method = "clientTick",
		at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private static boolean createBiotech$recognizeBioPackager(BlockEntry<?> entry, BlockState state, Operation<Boolean> original) {
		return original.call(entry, state) || entry == AllBlocks.PACKAGER && state.getBlock() instanceof BioPackagerBlock;
	}
}
