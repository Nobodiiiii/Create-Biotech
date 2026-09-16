package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

import net.minecraft.world.item.Item;

public class CardboardBoxItem extends CapturedEntityBoxItem {

	public CardboardBoxItem(Properties properties) {
		this(properties, CBItems.EMPTY_CARDBOARD_BOX);
	}

	public CardboardBoxItem(Properties properties, Supplier<? extends Item> emptyBox) {
		super(properties, "item.create_biotech.cardboard_box", new PackageStyle("cardboard", 12, 10, 21f, false),
			emptyBox);
	}
}
