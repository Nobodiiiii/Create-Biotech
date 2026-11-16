package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.simibubi.create.AllBlocks;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Shared egg replacement odds for living chickens and retained bionic torsos. */
public final class DingDongChickenEggDrops {

	private DingDongChickenEggDrops() {}

	public static Item roll(RandomSource random) {
		int roll = random.nextInt(1000);
		if (roll < 1) return Items.GOLD_BLOCK; // 0.1%
		if (roll < 6) return AllBlocks.DESK_BELL.get().asItem(); // 0.5%
		if (roll < 16) return Items.GOLD_INGOT; // 1%
		if (roll < 46) return Items.GOLD_NUGGET; // 3%
		return Items.EGG;
	}
}
