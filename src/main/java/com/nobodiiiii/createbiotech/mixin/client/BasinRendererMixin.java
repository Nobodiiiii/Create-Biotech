package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinRenderer;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

@Mixin(BasinRenderer.class)
public abstract class BasinRendererMixin {

	@Redirect(method = "renderSafe(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/IItemHandlerModifiable;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"),
		remap = false)
	private ItemStack createBiotech$hideVirtualSlimeItems(IItemHandlerModifiable inventory, int slot) {
		ItemStack stack = inventory.getStackInSlot(slot);
		return BasinEntityProcessing.isCapturedSmallSlimeItem(stack) ? ItemStack.EMPTY : stack;
	}
}
