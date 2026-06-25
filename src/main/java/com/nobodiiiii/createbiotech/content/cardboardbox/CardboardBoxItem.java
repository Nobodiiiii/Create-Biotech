package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

public class CardboardBoxItem extends CapturedEntityBoxItem {

	public CardboardBoxItem(Properties properties) {
		super(properties, "item.create_biotech.cardboard_box", "item.create_biotech.cardboard_box.filled",
			new PackageStyle("cardboard", 12, 10, 21f, false));
	}
}
