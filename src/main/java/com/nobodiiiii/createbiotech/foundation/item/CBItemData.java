package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class CBItemData {
	// Weak keys compare immutable components by identity, without hashing their full NBT.
	// Snapshots own their tags and never retain the component used as the cache key.
	private static final LoadingCache<CustomData, CompoundTag> READ_SNAPSHOTS = CacheBuilder.newBuilder()
		.weakKeys()
		.maximumWeight(8 * 1024 * 1024)
		.weigher((CustomData component, CompoundTag snapshot) -> snapshot.sizeInBytes())
		.build(CacheLoader.from(CustomData::copyTag));

	private CBItemData() {
	}

	@Nullable
	public static CompoundTag get(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag();
	}

	/**
	 * Returns the immutable component instance stored on the stack, suitable as a cache key.
	 */
	@Nullable
	public static CustomData getReadOnlyComponent(ItemStack stack) {
		return stack.get(DataComponents.CUSTOM_DATA);
	}

	/**
	 * Returns a cached snapshot of the stack's custom data. Callers must treat the
	 * snapshot and all nested tags as read-only; use {@link #edit(ItemStack, Consumer)} for writes.
	 */
	@Nullable
	public static CompoundTag getReadOnly(ItemStack stack) {
		CustomData data = getReadOnlyComponent(stack);
		return data == null ? null : readOnlySnapshot(data);
	}

	/** Returns a shared read-only snapshot without exposing the component's backing tag. */
	public static CompoundTag readOnlySnapshot(CustomData component) {
		return READ_SNAPSHOTS.getUnchecked(component);
	}

	public static CompoundTag getOrEmpty(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	public static boolean has(ItemStack stack) {
		return stack.has(DataComponents.CUSTOM_DATA);
	}

	public static void set(ItemStack stack, @Nullable CompoundTag tag) {
		if (tag == null || tag.isEmpty())
			stack.remove(DataComponents.CUSTOM_DATA);
		else
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static void edit(ItemStack stack, Consumer<CompoundTag> editor) {
		CompoundTag tag = getOrEmpty(stack);
		editor.accept(tag);
		set(stack, tag);
	}
}
