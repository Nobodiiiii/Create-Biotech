package com.nobodiiiii.createbiotech.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortWakeupHandler;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {
	@WrapOperation(method = "registerCapabilities",
		at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent;registerBlockEntity(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/neoforged/neoforge/capabilities/ICapabilityProvider;)V"))
	private static <T, C> void createBiotech$registerBioPackagerCapabilities(RegisterCapabilitiesEvent event,
		BlockCapability<T, C> capability, BlockEntityType<PackagerBlockEntity> type,
		ICapabilityProvider<? super PackagerBlockEntity, C, T> provider, Operation<Void> original) {
		original.call(event, capability, type, provider);
		event.registerBlockEntity(capability, CBBlockEntityTypes.BIO_PACKAGER.get(), provider);
	}

	@ModifyConstant(method = "tick", constant = @Constant(intValue = PackagerBlockEntity.CYCLE, ordinal = 1), remap = false)
	private int createBiotech$useBiologicalCycleForTraySound(int cycle) {
		return (Object) this instanceof BioPackagerBlockEntity bioPackager ? bioPackager.getAnimationCycle() : cycle;
	}

	@ModifyExpressionValue(method = {"getAvailableItems", "attemptToSend"}, at = {
		@At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/crate/BottomlessItemHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"),
		@At(value = "INVOKE", target = "Lnet/neoforged/neoforge/items/IItemHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"),
		@At(value = "INVOKE", target = "Lnet/neoforged/neoforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;",
			ordinal = 0)
	}, require = 3)
	private ItemStack createBiotech$limitBioPackagerStock(ItemStack stack) {
		return (Object) this instanceof BioPackagerBlockEntity && !BioPackagerBlockEntity.isCreaturePackage(stack)
			? ItemStack.EMPTY : stack;
	}

	@Inject(method = "wakeTheFrogs", at = @At("TAIL"), remap = false)
	private void createBiotech$wakeAllayPortAbove(CallbackInfo ci) {
		AllayPortWakeupHandler.tryWakePortAbove((PackagerBlockEntity) (Object) this);
	}
}
