package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

public final class ComputerItemReference {

	private ComputerItemReference() {}

	public static String write(ItemStack stack, boolean includeColor) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		DyedItemColor color = includeColor ? stack.get(DataComponents.DYED_COLOR) : null;
		return color == null ? id.toString() : "%s@%06X".formatted(id, color.rgb() & 0xffffff);
	}

	public static ItemStack read(String text, boolean includeColor) {
		int separator = includeColor ? text.indexOf('@') : -1;
		ResourceLocation id = parseId(separator < 0 ? text : text.substring(0, separator));
		ItemStack stack = id == null ? ItemStack.EMPTY
			: BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
		if (stack.isEmpty() || separator < 0)
			return stack;
		String rgb = text.substring(separator + 1).strip();
		if (rgb.length() != 6 || !rgb.chars().allMatch(c -> Character.digit(c, 16) >= 0))
			return ItemStack.EMPTY;
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(rgb, 16), false));
		return stack;
	}

	@Nullable
	static ResourceLocation parseId(String text) {
		return ResourceLocation.tryParse(text.strip().toLowerCase(Locale.ROOT));
	}

	public static Component displayName(String reference) {
		ItemStack item = read(reference, true);
		return item.isEmpty() ? Component.literal(reference) : item.getHoverName();
	}
}
