package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

import net.minecraft.world.item.Item;

public class LargeCardboardBoxItem extends CapturedEntityBoxItem {

	public LargeCardboardBoxItem(Properties properties) {
		this(properties, CBItems.EMPTY_LARGE_CARDBOARD_BOX);
	}

	public LargeCardboardBoxItem(Properties properties, Supplier<? extends Item> emptyBox) {
		super(properties, "item.create_biotech.large_cardboard_box",
			new PackageStyle("cardboard", 12, 12, 23f, false), emptyBox);
	}
}
