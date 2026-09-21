package com.nobodiiiii.createbiotech.content.squidprinter;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class EnchantmentBookCopyItem extends Item {

	public EnchantmentBookCopyItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		appendEnchantmentLines(getStoredEnchantments(stack), tooltip);
	}

	public static ItemEnchantments getStoredEnchantments(ItemStack stack) {
		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	public static ItemEnchantments getCopySourceEnchantments(ItemStack stack) {
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!stored.isEmpty())
			return stored;
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	public static boolean hasCopyableEnchantments(ItemStack stack) {
		return !getCopySourceEnchantments(stack).isEmpty();
	}

	public static int sumCopySourceEnchantmentLevels(ItemStack stack) {
		return sumLevels(getCopySourceEnchantments(stack));
	}

	public static int sumStoredEnchantmentLevels(ItemStack stack) {
		return sumLevels(getStoredEnchantments(stack));
	}

	private static int sumLevels(ItemEnchantments enchantments) {
		int total = 0;
		for (var entry : enchantments.entrySet())
			total += Math.max(1, entry.getIntValue());
		return total;
	}

	public static ItemStack fromTemplate(ItemStack template, Item copyItem) {
		return fromTemplate(template, new ItemStack(copyItem));
	}

	public static ItemStack fromTemplate(ItemStack template, ItemStack resultBase) {
		if (resultBase.isEmpty())
			return ItemStack.EMPTY;
		ItemStack out = resultBase.copy();
		ItemEnchantments enchantments = getCopySourceEnchantments(template);
		if (!enchantments.isEmpty())
			out.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
		return out;
	}

	public static ItemStack fromEnchantedBook(ItemStack enchantedBook, Item copyItem) {
		return fromTemplate(enchantedBook, copyItem);
	}

	public static ItemStack toEnchantedBook(ItemStack copyStack) {
		ItemStack out = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments enchantments = getStoredEnchantments(copyStack);
		if (!enchantments.isEmpty())
			out.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
		return out;
	}

	public static boolean hasStoredEnchantments(ItemStack stack) {
		return !getStoredEnchantments(stack).isEmpty();
	}

	private static void appendEnchantmentLines(ItemEnchantments enchantments, List<Component> tooltip) {
		for (var entry : enchantments.entrySet())
			tooltip.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue())
				.copy()
				.withStyle(ChatFormatting.GRAY));
	}
}
