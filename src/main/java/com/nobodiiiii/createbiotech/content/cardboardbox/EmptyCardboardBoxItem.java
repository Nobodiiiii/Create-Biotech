package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.function.Supplier;

import net.minecraft.world.item.Item;

/** Ordinary cargo until capturing a creature turns it into a package. */
public class EmptyCardboardBoxItem extends Item {
	private final Supplier<? extends CapturedEntityBoxItem> filledBox;
	private final boolean large;

	public EmptyCardboardBoxItem(Properties properties, boolean large,
		Supplier<? extends CapturedEntityBoxItem> filledBox) {
		super(properties.stacksTo(16));
		this.large = large;
		this.filledBox = filledBox;
	}

	public CapturedEntityBoxItem filledBox() {
		return filledBox.get();
	}

	public boolean isLarge() {
		return large;
	}

	@Override
	public String getDescriptionId() {
		return large ? "item.create_biotech.large_cardboard_box" : "item.create_biotech.cardboard_box";
	}
}
