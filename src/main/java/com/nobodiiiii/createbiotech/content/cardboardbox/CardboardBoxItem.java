package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CardboardBoxItem extends Item {

	public CardboardBoxItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
		super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("CapturedEntityDescId")) {
			String entityDescId = tag.getString("CapturedEntityDescId");
			if (!entityDescId.isEmpty()) {
				tooltipComponents.add(Component.translatable("item.create_biotech.cardboard_box.filled",
					Component.translatable(entityDescId)));
			}
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.PASS;

		ItemStack stack = context.getItemInHand();

		if (player.isShiftKeyDown() && hasCapturedEntity(stack)) {
			Level level = context.getLevel();
			if (!level.isClientSide())
				releaseAt(context);
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		return InteractionResult.PASS;
	}

	private void releaseAt(UseOnContext context) {
		Level level = context.getLevel();
		ItemStack stack = context.getItemInHand();
		CompoundTag entityData = stack.getOrCreateTag().getCompound("CapturedEntity");

		Entity entity = EntityType.loadEntityRecursive(entityData, level, Function.identity());
		if (entity != null) {
			BlockPos clickedPos = context.getClickedPos();
			Direction face = context.getClickedFace();
			BlockState clickedState = level.getBlockState(clickedPos);
			BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
				? clickedPos : clickedPos.relative(face);

			entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
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
