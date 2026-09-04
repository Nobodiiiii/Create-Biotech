package com.nobodiiiii.createbiotech.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nobodiiiii.createbiotech.foundation.item.DeferredExtractionPreviewProvider;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

@Mixin(ItemHelper.class)
public abstract class ItemHelperMixin {

	@WrapOperation(
		method = "extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;IZ)Lnet/minecraft/world/item/ItemStack;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/neoforged/neoforge/items/IItemHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"
		),
		require = 1,
		expect = 1
	)
	private static ItemStack createBiotech$previewDeferredExtraction(IItemHandler handler, int slot,
		Operation<ItemStack> original, @Local(argsOnly = true) Predicate<ItemStack> test) {
		ItemStack stack = original.call(handler, slot);
		if (!stack.isEmpty() || !(handler instanceof DeferredExtractionPreviewProvider previewProvider))
			return stack;

		ItemStack preview = previewProvider.getDeferredExtractionPreview(slot);
		return !preview.isEmpty() && test.test(preview) ? preview : stack;
	}
}
