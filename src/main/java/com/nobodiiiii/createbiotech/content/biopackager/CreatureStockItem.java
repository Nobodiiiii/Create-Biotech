package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.List;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/** Carries a stock entry through Create's inventory and order packets. */
public final class CreatureStockItem extends Item {
	public CreatureStockItem(Properties properties) {
		super(properties);
	}

	static ItemStack ofBox(ItemStack box) {
		ItemStack stock = new ItemStack(CBItems.CREATURE_STOCK.get());
		stock.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(box.copyWithCount(1))));
		return stock;
	}

	public static boolean isCreatureStock(ItemStack stack) {
		return stack.getItem() instanceof CreatureStockItem;
	}

	public static boolean isCreatureOrder(PackageOrderWithCrafts order) {
		for (BigItemStack entry : order.stacks())
			if (isCreatureStock(entry.stack))
				return true;
		return false;
	}

	public static ItemStack getBox(ItemStack stock) {
		return stock.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
	}

	@Override
	public Component getName(ItemStack stack) {
		EntityType<?> type = CapturedEntityBoxHelper.getCapturedEntityType(getBox(stack));
		return type == null ? super.getName(stack) : type.getDescription();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Component name = CapturedEntityBoxHelper.getCapturedEntityCustomName(getBox(stack), context.registries());
		if (name != null)
			tooltip.add(name);
		tooltip.add(Component.translatable("create_biotech.gui.stock_keeper.creature_release")
			.withStyle(ChatFormatting.GRAY));
	}
}
