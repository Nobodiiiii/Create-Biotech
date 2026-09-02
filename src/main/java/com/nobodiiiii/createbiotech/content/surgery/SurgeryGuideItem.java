package com.nobodiiiii.createbiotech.content.surgery;

import com.nobodiiiii.createbiotech.content.surgery.client.SurgeryGuideScreen;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A readable guide to the surgical table and the bionic creatures it produces. */
public class SurgeryGuideItem extends Item {
	public SurgeryGuideItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide)
			SurgeryGuideScreen.open();
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
