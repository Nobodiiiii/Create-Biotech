package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** A reusable snapshot of a single mimic, including its saved anatomy and equipment. */
public class SlimeDataItem extends Item {

	private static final String ENTITY_DATA = "SlimeEntity";
	private static final String ENTITY_NAME = "SlimeName";

	public SlimeDataItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
		InteractionHand hand) {
		if (player.isSpectator() || target instanceof Player || !target.isAlive()
			|| !SlimeMimicHandler.isSlimeMimic(target))
			return InteractionResult.PASS;
		if (player.level().isClientSide)
			return InteractionResult.SUCCESS;

		CompoundTag entityData = new CompoundTag();
		if (!target.saveAsPassenger(entityData))
			return InteractionResult.FAIL;
		prepareCopy(entityData);
		CBItemData.edit(stack, data -> {
			data.put(ENTITY_DATA, entityData);
			data.putString(ENTITY_NAME, target.getName().getString());
		});
		player.displayClientMessage(Component.translatable("message.create_biotech.slime_data.recorded",
			target.getDisplayName()), true);
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		CompoundTag recorded = recordedEntity(stack);
		if (recorded == null)
			return InteractionResult.PASS;

		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos clickedPos = context.getClickedPos();
		BlockPos spawnPos = level.getBlockState(clickedPos).getCollisionShape(level, clickedPos).isEmpty()
			? clickedPos : clickedPos.relative(context.getClickedFace());
		if (player != null && (player.isSpectator() || !level.mayInteract(player, clickedPos)
			|| !player.mayUseItemAt(spawnPos, context.getClickedFace(), stack)))
			return InteractionResult.FAIL;
		if (!(level instanceof ServerLevel serverLevel))
			return InteractionResult.SUCCESS;

		CompoundTag entityData = recorded.copy();
		prepareCopy(entityData);
		// Loading the saved entity avoids spawn finalization rerolling its variants or equipment.
		Entity copy = EntityType.loadEntityRecursive(entityData, serverLevel, entity -> entity);
		if (!(copy instanceof LivingEntity living) || !SlimeMimicHandler.isSlimeMimic(living))
			return failedPlacement(player);
		// Some entity loaders resize their body after loading health; restore it after the full load.
		if (entityData.contains("Health", Tag.TAG_ANY_NUMERIC))
			living.setHealth(entityData.getFloat("Health"));
		living.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
			living.getYRot(), living.getXRot());
		if (!living.isAlive() || level.isOutsideBuildHeight(spawnPos)
			|| living.getBoundingBox().maxY > level.getMaxBuildHeight()
			|| !level.getWorldBorder().isWithinBounds(living.getBoundingBox())
			|| !level.noCollision(living) || !serverLevel.addFreshEntity(living))
			return failedPlacement(player);

		level.gameEvent(player, GameEvent.ENTITY_PLACE, living.position());
		if (player != null)
			player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		CompoundTag data = CBItemData.getReadOnly(stack);
		if (recordedEntity(stack) != null && data != null)
			tooltip.add(Component.translatable("item.create_biotech.slime_data.recorded", data.getString(ENTITY_NAME))
				.withStyle(ChatFormatting.GREEN));
		else
			tooltip.add(Component.translatable("item.create_biotech.slime_data.empty").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.slime_data.record_hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.slime_data.release_hint").withStyle(ChatFormatting.GRAY));
	}

	@Nullable
	private static CompoundTag recordedEntity(ItemStack stack) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		return data != null && data.contains(ENTITY_DATA, Tag.TAG_COMPOUND) ? data.getCompound(ENTITY_DATA) : null;
	}

	private static void prepareCopy(CompoundTag data) {
		// Identity and world links belong to the original. The snapshot represents only the clicked creature.
		for (String field : List.of("UUID", "Pos", "Motion", "Passengers", "Leash", "FallDistance",
			"OnGround", "PortalCooldown"))
			data.remove(field);
	}

	private static InteractionResult failedPlacement(@Nullable Player player) {
		if (player != null)
			player.displayClientMessage(Component.translatable("message.create_biotech.slime_data.release_failed"), true);
		return InteractionResult.FAIL;
	}
}
