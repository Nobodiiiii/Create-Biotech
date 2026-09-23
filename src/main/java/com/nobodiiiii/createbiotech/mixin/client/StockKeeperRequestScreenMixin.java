package com.nobodiiiii.createbiotech.mixin.client;

import java.lang.ref.WeakReference;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestScreen;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.GaugeOutputBigItemStack;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestScreen;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;

import net.createmod.catnip.data.Couple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

	@Shadow
	WeakReference<LivingEntity> stockKeeper;

	@Shadow
	WeakReference<BlazeBurnerBlockEntity> blaze;

	@Shadow
	public java.util.List<java.util.List<BigItemStack>> displayedItems;

	@Shadow
	protected abstract Couple<Integer> getHoveredSlot(int x, int y);

	@Shadow
	protected abstract void drawItemCount(GuiGraphics graphics, int count, int customCount);

	@Inject(method = "<init>", at = @At("RETURN"))
	private void createBiotech$hideWirelessTerminalKeeper(StockKeeperRequestMenu menu, Inventory inventory,
		Component title, CallbackInfo ci) {
		if (!((Object) this instanceof WirelessStockKeeperRequestScreen)
			&& !((Object) this instanceof EndermanStockKeeperRequestScreen))
			return;

		stockKeeper = new WeakReference<>(null);
		blaze = new WeakReference<>(null);
	}

	@WrapOperation(
		method = "containerTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;closeContainer()V",
			remap = true
		)
	)
	private void createBiotech$keepRemoteWirelessTerminalOpen(Player player, Operation<Void> original) {
		if ((Object) this instanceof WirelessStockKeeperRequestScreen
			|| (Object) this instanceof EndermanStockKeeperRequestScreen)
			return;
		original.call(player);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createBiotech$ignoreOrderingZeroGaugeEntries(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof EndermanStockKeeperRequestScreen screen) || button < 0 || button > 1)
			return;

		Couple<Integer> hovered = getHoveredSlot((int) mouseX, (int) mouseY);
		int category = hovered.getFirst();
		int slot = hovered.getSecond();
		if (category < 0 || category >= displayedItems.size() || slot < 0
			|| slot >= displayedItems.get(category).size())
			return;
		BigItemStack entry = displayedItems.get(category).get(slot);
		if (button == 0 && screen.isCraftable(entry.stack)) {
			screen.beginCraft(entry.stack);
			cir.setReturnValue(true);
		} else if (entry instanceof GaugeOutputBigItemStack)
			cir.setReturnValue(true);
	}

	@Inject(method = "renderItemEntry", at = @At("HEAD"))
	private void createBiotech$renderZeroGaugeIcon(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders, CallbackInfo ci) {
		if (entry instanceof GaugeOutputBigItemStack)
			entry.count = 1;
	}

	@Inject(method = "renderItemEntry", at = @At("RETURN"))
	private void createBiotech$renderZeroGaugeCount(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders, CallbackInfo ci) {
		if (!(entry instanceof GaugeOutputBigItemStack))
			return;

		entry.count = 0;
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(0, 0, 200);
		drawItemCount(graphics, 0, 0);
		poseStack.popPose();
	}
}
