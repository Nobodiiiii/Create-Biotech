package com.nobodiiiii.createbiotech.content.squidprinter;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

public class EnchantmentBookCopyItem extends Item {

	public static final String STORED_ENCHANTMENTS_TAG = "StoredEnchantments";

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
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		ListTag list = getStoredEnchantments(stack);
		appendEnchantmentLines(list, tooltip);
	}

	public static ListTag getStoredEnchantments(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? new ListTag() : tag.getList(STORED_ENCHANTMENTS_TAG, Tag.TAG_COMPOUND);
	}

	public static ItemStack fromEnchantedBook(ItemStack enchantedBook, Item copyItem) {
		ItemStack out = new ItemStack(copyItem);
		ListTag enchantments = EnchantedBookItem.getEnchantments(enchantedBook);
		if (enchantments.isEmpty())
			return out;
		out.getOrCreateTag().put(STORED_ENCHANTMENTS_TAG, enchantments.copy());
		return out;
	}

	public static ItemStack toEnchantedBook(ItemStack copyStack) {
		ListTag enchantments = getStoredEnchantments(copyStack);
		ItemStack out = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
		for (int i = 0; i < enchantments.size(); i++) {
			CompoundTag entry = enchantments.getCompound(i);
			net.minecraft.resources.ResourceLocation id =
				net.minecraft.resources.ResourceLocation.tryParse(entry.getString("id"));
			if (id == null)
				continue;
			Enchantment enchantment = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(id);
			if (enchantment == null)
				continue;
			int level = entry.getShort("lvl");
			EnchantedBookItem.addEnchantment(out, new EnchantmentInstance(enchantment, level));
		}
		return out;
	}

	public static boolean hasStoredEnchantments(ItemStack stack) {
		return !getStoredEnchantments(stack).isEmpty();
	}

	private static void appendEnchantmentLines(ListTag enchantments, List<Component> tooltip) {
		for (int i = 0; i < enchantments.size(); i++) {
			CompoundTag entry = enchantments.getCompound(i);
			net.minecraft.resources.ResourceLocation id =
				net.minecraft.resources.ResourceLocation.tryParse(entry.getString("id"));
			if (id == null)
				continue;
			Enchantment enchantment = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(id);
			if (enchantment == null)
				continue;
			int level = entry.getShort("lvl");
			tooltip.add(enchantment.getFullname(level)
				.copy()
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
