package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;
import java.util.function.Function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CardboardBoxItem extends Item {

	public CardboardBoxItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
		super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
		if (hasCapturedEntity(stack)) {
			String entityDescId = stack.getOrCreateTag().getString("CapturedEntityDescId");
			if (!entityDescId.isEmpty()) {
				tooltipComponents.add(Component.translatable("item.create_biotech.cardboard_box.filled",
					Component.translatable(entityDescId)));
			}
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.isShiftKeyDown() && hasCapturedEntity(stack)) {
			if (!level.isClientSide())
				releaseEntity(level, player, stack);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		return InteractionResultHolder.pass(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.PASS;

		ItemStack stack = context.getItemInHand();

		if (player.isShiftKeyDown() && hasCapturedEntity(stack)) {
			if (!context.getLevel().isClientSide())
				releaseEntity(context.getLevel(), player, stack);
			return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
		}

		return InteractionResult.PASS;
	}

	private void releaseEntity(Level level, Player player, ItemStack stack) {
		CompoundTag entityData = stack.getOrCreateTag().getCompound("CapturedEntity");

		Entity entity = EntityType.loadEntityRecursive(entityData, level, Function.identity());
		if (entity != null) {
			Vec3 look = player.getLookAngle();
			entity.setPos(player.getX() + look.x * 1.5, player.getY() + 0.5, player.getZ() + look.z * 1.5);
			level.addFreshEntity(entity);
		}

		stack.getOrCreateTag().remove("CapturedEntity");
		stack.getOrCreateTag().remove("CapturedEntityDescId");
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains("CapturedEntity");
	}
}
