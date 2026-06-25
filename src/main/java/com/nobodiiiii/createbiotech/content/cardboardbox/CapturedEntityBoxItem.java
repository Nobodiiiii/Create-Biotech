package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public abstract class CapturedEntityBoxItem extends PackageItem {
	private static final int EMPTY_BOX_MAX_STACK_SIZE = 16;

	private final String descriptionId;
	private final String filledTranslationKey;

	protected CapturedEntityBoxItem(Properties properties, String descriptionId, String filledTranslationKey,
		PackageStyle style) {
		super(properties, style);
		this.descriptionId = descriptionId;
		this.filledTranslationKey = filledTranslationKey;
		PackageStyles.ALL_BOXES.remove(this);
		PackageStyles.STANDARD_BOXES.remove(this);
		PackageStyles.RARE_BOXES.remove(this);
	}

	@Override
	public String getDescriptionId() {
		return descriptionId;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
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

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}

	@Override
	public Entity createEntity(Level world, Entity location, ItemStack itemstack) {
		return CardboardBoxEntity.fromDroppedItem(world, location, itemstack);
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return hasCapturedEntity(stack);
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		if (!hasCapturedEntity(stack))
			return ItemStack.EMPTY;

		ItemStack remainder = stack.copy();
		remainder.setCount(1);
		CapturedEntityBoxHelper.clearCapturedEntity(remainder);
		return remainder;
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return hasCapturedEntity(stack) ? 1 : EMPTY_BOX_MAX_STACK_SIZE;
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		return CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	public static boolean isBox(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem;
	}
}
