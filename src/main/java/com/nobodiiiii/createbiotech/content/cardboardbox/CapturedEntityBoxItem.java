package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public abstract class CapturedEntityBoxItem extends Item {
	private final String filledTranslationKey;

	protected CapturedEntityBoxItem(Properties properties, String filledTranslationKey) {
		super(properties);
		this.filledTranslationKey = filledTranslationKey;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
		super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
		CapturedEntityBoxHelper.appendHoverText(stack, tooltipComponents, filledTranslationKey);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;

		ItemStack stack = context.getItemInHand();
		if (!player.isShiftKeyDown() || !hasCapturedEntity(stack))
			return InteractionResult.PASS;

		Level level = context.getLevel();
		if (!level.isClientSide())
			CapturedEntityBoxHelper.releaseCapturedEntity(context);
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		return CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}
}
