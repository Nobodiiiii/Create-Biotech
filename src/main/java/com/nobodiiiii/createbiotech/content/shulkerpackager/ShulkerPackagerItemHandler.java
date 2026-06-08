package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;

public class ShulkerPackagerItemHandler extends PackagerItemHandler {

	public ShulkerPackagerItemHandler(ShulkerPackagerBlockEntity blockEntity) {
		super(blockEntity);
	}

	@Override
	public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
		return PackageItem.isPackage(stack);
	}
}
