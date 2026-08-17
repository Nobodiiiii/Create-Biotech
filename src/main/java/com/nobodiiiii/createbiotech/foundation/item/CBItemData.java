package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Version-neutral access to this mod's custom item NBT. */
public final class CBItemData {
	private CBItemData() {}

	/**
	 * Returns a zero-copy view of the stack's custom data. This is intentionally
	 * read-only; use {@link #edit(ItemStack, Consumer)} for writes.
	 */
	@Nullable
	public static CompoundTag getReadOnly(ItemStack stack) {
		return stack.getTag();
	}

	public static CompoundTag getOrEmpty(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? new CompoundTag() : tag.copy();
	}

	public static boolean has(ItemStack stack) {
		return stack.hasTag();
	}

	public static void set(ItemStack stack, @Nullable CompoundTag tag) {
		stack.setTag(tag == null || tag.isEmpty() ? null : tag);
	}

	public static void edit(ItemStack stack, Consumer<CompoundTag> editor) {
		CompoundTag tag = getOrEmpty(stack);
		editor.accept(tag);
		set(stack, tag);
	}
}
