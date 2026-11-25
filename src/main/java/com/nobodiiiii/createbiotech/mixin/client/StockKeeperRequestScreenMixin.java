package com.nobodiiiii.createbiotech.mixin.client;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedCreatureStockGroup;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxIconRenderer;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestScreen;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.GaugeOutputBigItemStack;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestScreen;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.GuiGameElement.GuiRenderBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {
	@Unique
	private final Set<CapturedCreatureStockGroup.Key> createBiotech$expandedCreatureGroups = new HashSet<>();

	@Shadow
	WeakReference<LivingEntity> stockKeeper;

	@Shadow
	WeakReference<BlazeBurnerBlockEntity> blaze;

	@Shadow
	public List<List<BigItemStack>> displayedItems;

	@Shadow
	public List<List<BigItemStack>> currentItemSource;

	@Shadow
	protected abstract void refreshSearchResults(boolean scrollBackUp);

	@Shadow
	protected abstract Couple<Integer> getHoveredSlot(int x, int y);

	@Shadow
	protected abstract void drawItemCount(GuiGraphics graphics, int count, int customCount);

	@WrapOperation(method = "refreshSearchResults",
		at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
	private int createBiotech$groupCreatureSlots(List<?> list, Operation<Integer> original) {
		int size = original.call(list);
		if (size < 2 || list == displayedItems || list == currentItemSource)
			return size;

		// Create counts a category's visible slots after filtering to lay out the following rows.
		for (int category = 0; category < displayedItems.size(); category++) {
			List<BigItemStack> entries = displayedItems.get(category);
			if (entries != list)
				continue;
			List<BigItemStack> grouped = CapturedCreatureStockGroup.group(entries, category,
				createBiotech$expandedCreatureGroups);
			if (grouped != entries)
				displayedItems.set(category, grouped);
			return grouped.size();
		}
		return size;
	}

	@Inject(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"),
		cancellable = true)
	private void createBiotech$toggleCreatureGroup(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir, @Local BigItemStack entry) {
		if (!(entry instanceof CapturedCreatureStockGroup group))
			return;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (!createBiotech$expandedCreatureGroups.add(group.key()))
				createBiotech$expandedCreatureGroups.remove(group.key());
			refreshSearchResults(false);
		}
		cir.setReturnValue(true);
	}

	@Inject(method = "mouseScrolled", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"),
		cancellable = true)
	private void createBiotech$skipCreatureGroupQuantity(double mouseX, double mouseY, double scrollX, double scrollY,
		CallbackInfoReturnable<Boolean> cir, @Local BigItemStack entry) {
		if (entry instanceof CapturedCreatureStockGroup)
			cir.setReturnValue(true);
	}

	@ModifyReturnValue(method = "getHoveredIngredient", at = @At("RETURN"))
	private Optional<Pair<ItemStack, Rect2i>> createBiotech$onlyExposeStockIngredients(
		Optional<Pair<ItemStack, Rect2i>> hovered) {
		return hovered.filter(ingredient -> !ingredient.getFirst().isEmpty());
	}

	@WrapOperation(method = "renderItemEntry", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void createBiotech$renderCapturedCreature(GuiRenderBuilder renderer, GuiGraphics graphics,
		Operation<Void> original, @Local(argsOnly = true) BigItemStack entry) {
		if (entry instanceof CapturedCreatureStockGroup group) {
			if (!CapturedEntityBoxIconRenderer.renderInStockSlot(group.preview(), graphics))
				GuiGameElement.of(group.preview()).render(graphics);
		} else if (!CapturedEntityBoxIconRenderer.renderInStockSlot(entry.stack, graphics)) {
			original.call(renderer, graphics);
		}
	}

	@WrapOperation(method = "renderForeground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void createBiotech$renderCreatureGroupTooltip(GuiGraphics graphics, Font font, ItemStack stack,
		int mouseX, int mouseY, Operation<Void> original, @Local BigItemStack entry) {
		if (!(entry instanceof CapturedCreatureStockGroup group)) {
			original.call(graphics, font, stack, mouseX, mouseY);
			return;
		}
		String action = group.expanded() ? "create_biotech.gui.stock_keeper.collapse_creatures"
			: "create_biotech.gui.stock_keeper.expand_creatures";
		graphics.renderComponentTooltip(font, List.of(group.key().type().getDescription(),
			Component.translatable(action).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
	}

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
		if (button == 0 && entry.count <= 0 && screen.isCraftable(entry.stack)) {
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
	private void createBiotech$renderStockSlotOverlay(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders, CallbackInfo ci) {
		boolean zeroGauge = entry instanceof GaugeOutputBigItemStack;
		if (!zeroGauge && !(entry instanceof CapturedCreatureStockGroup))
			return;

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(0, 0, 200);
		if (entry instanceof CapturedCreatureStockGroup group) {
			poseStack.scale(0.625f, 0.625f, 1.0f);
			(group.expanded() ? AllGuiTextures.STOCK_KEEPER_CATEGORY_SHOWN : AllGuiTextures.STOCK_KEEPER_CATEGORY_HIDDEN)
				.render(graphics, 2, 2);
		} else {
			entry.count = 0;
			drawItemCount(graphics, 0, 0);
		}
		poseStack.popPose();
	}
}
