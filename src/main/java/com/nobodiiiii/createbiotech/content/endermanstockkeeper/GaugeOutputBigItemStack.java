package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.world.item.ItemStack;

/** A zero-stock entry which remains visible in the Enderman stock keeper screen. */
public class GaugeOutputBigItemStack extends BigItemStack {

	public GaugeOutputBigItemStack(ItemStack stack) {
		super(stack.copyWithCount(1), 0);
	}
}
