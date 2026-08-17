package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

public enum SonicDogCannonUpgrade implements StringRepresentable {
	VOICE_PACK("voice_pack"),
	SCOPE("scope"),
	SHRIEK_SONIC_BOOM("shriek_sonic_boom"),
	DOG_COLLAR("dog_collar");

	static final String UPGRADES_TAG = "SonicDogCannonUpgrades";
	static final String COLLAR_COLOR_TAG = "SonicDogCannonCollarColor";
	static final String SCOPE_FOLDED_TAG = "SonicDogCannonScopeFolded";

	private static final int LEGACY_ORDER_ENTRY_BITS = 2;
	private static final int ORDER_ENTRY_BITS = 3;
	private static final int VERSIONED_ORDER_MARKER = Integer.MIN_VALUE;

	private final String serializedName;

	SonicDogCannonUpgrade(String serializedName) {
		this.serializedName = serializedName;
	}

	public boolean isInstalled(ItemStack stack) {
		return getInstallationOrder(stack).contains(this);
	}

	public void install(ItemStack stack) {
		if (isInstalled(stack))
			return;

		List<SonicDogCannonUpgrade> order = getInstallationOrder(stack);
		order.add(this);
		CBItemData.edit(stack, tag -> tag.putInt(UPGRADES_TAG, encodeOrder(order)));
	}

	public static boolean hasInstalledUpgrade(ItemStack stack) {
		return !getInstallationOrder(stack).isEmpty();
	}

	@Nullable
	public static SonicDogCannonUpgrade removeLastInstalled(ItemStack stack) {
		List<SonicDogCannonUpgrade> order = getInstallationOrder(stack);
		if (order.isEmpty())
			return null;

		SonicDogCannonUpgrade removed = order.remove(order.size() - 1);
		CBItemData.edit(stack, tag -> {
			if (removed == DOG_COLLAR)
				tag.remove(COLLAR_COLOR_TAG);
			if (removed == SCOPE)
				tag.remove(SCOPE_FOLDED_TAG);
			if (order.isEmpty())
				tag.remove(UPGRADES_TAG);
			else
				tag.putInt(UPGRADES_TAG, encodeOrder(order));
		});
		return removed;
	}

	public ItemStack getRemovalRefund() {
		return switch (this) {
			case VOICE_PACK -> new ItemStack(Items.NOTE_BLOCK);
			case SCOPE -> new ItemStack(Items.GLASS_PANE);
			case SHRIEK_SONIC_BOOM -> new ItemStack(Items.SCULK_SHRIEKER);
			case DOG_COLLAR -> ItemStack.EMPTY;
		};
	}

	private static List<SonicDogCannonUpgrade> getInstallationOrder(ItemStack stack) {
		List<SonicDogCannonUpgrade> order = new ArrayList<>();
		CompoundTag tag = CBItemData.getReadOnly(stack);
		int encodedOrder = tag == null ? 0 : tag.getInt(UPGRADES_TAG);
		if ((encodedOrder & VERSIONED_ORDER_MARKER) == 0)
			return decodeOrder(encodedOrder, LEGACY_ORDER_ENTRY_BITS, order);

		return decodeOrder(encodedOrder & ~VERSIONED_ORDER_MARKER, ORDER_ENTRY_BITS, order);
	}

	private static List<SonicDogCannonUpgrade> decodeOrder(int encodedOrder, int entryBits,
		List<SonicDogCannonUpgrade> order) {
		int entryMask = (1 << entryBits) - 1;
		while (encodedOrder != 0) {
			int encodedUpgrade = encodedOrder & entryMask;
			encodedOrder >>>= entryBits;
			if (encodedUpgrade == 0 || encodedUpgrade > values().length)
				continue;

			SonicDogCannonUpgrade upgrade = values()[encodedUpgrade - 1];
			if (!order.contains(upgrade))
				order.add(upgrade);
		}
		return order;
	}

	private static int encodeOrder(List<SonicDogCannonUpgrade> order) {
		int encodedOrder = 0;
		for (int i = order.size() - 1; i >= 0; i--)
			encodedOrder = encodedOrder << ORDER_ENTRY_BITS | order.get(i).ordinal() + 1;
		return encodedOrder | VERSIONED_ORDER_MARKER;
	}

	public static DyeColor getCollarColor(ItemStack stack) {
		CompoundTag tag = CBItemData.getReadOnly(stack);
		if (tag == null || !tag.contains(COLLAR_COLOR_TAG, Tag.TAG_STRING))
			return DyeColor.RED;
		return DyeColor.byName(tag.getString(COLLAR_COLOR_TAG), DyeColor.RED);
	}

	public static void setCollarColor(ItemStack stack, DyeColor color) {
		DOG_COLLAR.install(stack);
		CBItemData.edit(stack, tag -> tag.putString(COLLAR_COLOR_TAG, color.getName()));
	}

	public String tooltipKey() {
		return "item.create_biotech.sonic_dog_cannon.upgrade." + serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public static SonicDogCannonUpgrade fromSerializedName(String name) {
		for (SonicDogCannonUpgrade upgrade : values()) {
			if (upgrade.serializedName.equals(name))
				return upgrade;
		}
		throw new IllegalArgumentException("Unknown Big Dog Sonic Cannon upgrade: " + name);
	}
}
