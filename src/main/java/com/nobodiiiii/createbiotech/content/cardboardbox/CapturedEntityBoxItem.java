package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;
import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRepair;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public abstract class CapturedEntityBoxItem extends PackageItem {
	private final String descriptionId;
	private final Supplier<? extends Item> emptyBox;

	protected CapturedEntityBoxItem(Properties properties, String descriptionId, PackageStyle style,
		Supplier<? extends Item> emptyBox) {
		super(properties.stacksTo(1), style);
		this.descriptionId = descriptionId;
		this.emptyBox = emptyBox;
		PackageStyles.ALL_BOXES.remove(this);
		PackageStyles.STANDARD_BOXES.remove(this);
		PackageStyles.RARE_BOXES.remove(this);
	}

	public Item emptyBox() {
		return emptyBox.get();
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
		// Keep old filled-box IDs so saved creatures remain intact. Old empty variants
		// become ordinary boxes when carried, including legacy offhand stacks.
		if (!level.isClientSide && entity instanceof Player player
			&& CapturedEntityBoxHelper.isEmptyBox(stack))
			CapturedEntityBoxHelper.replacePlayerStack(player, stack,
				CapturedEntityBoxHelper.createEmptyBox(stack));
	}

	@Override
	public String getDescriptionId() {
		return descriptionId;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
		TooltipFlag isAdvanced) {
		CapturedEntityBoxHelper.appendHoverText(stack, context.registries(), tooltipComponents);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;

		ItemStack stack = context.getItemInHand();
		if (!player.isShiftKeyDown()) {
			InteractionResult repairResult = UniversalJointRepair.useOn(context);
			if (repairResult != InteractionResult.PASS)
				return repairResult;
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		if (!level.isClientSide())
			UniversalJointRepair.clearSelection(stack);
		if (!hasCapturedEntity(stack))
			return InteractionResult.PASS;

		if (!level.isClientSide())
			CapturedEntityBoxHelper.releaseCapturedEntity(context);
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}

	@Override
	public boolean hasCustomEntity(ItemStack stack) {
		return hasCapturedEntity(stack);
	}

	@Override
	public Entity createEntity(Level world, Entity location, ItemStack itemstack) {
		return hasCapturedEntity(itemstack)
			? CardboardBoxEntity.fromDroppedItem(world, location, itemstack) : null;
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return hasCapturedEntity(stack);
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		if (!hasCapturedEntity(stack))
			return ItemStack.EMPTY;

		return CapturedEntityBoxHelper.createEmptyBox(stack).copyWithCount(1);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return 1;
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		return CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	public static boolean isBox(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem;
	}

}
