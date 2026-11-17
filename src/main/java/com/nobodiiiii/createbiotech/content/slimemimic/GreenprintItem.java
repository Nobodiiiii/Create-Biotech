package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** A reusable snapshot of a single mimic, including its saved anatomy and equipment. */
public class GreenprintItem extends Item {

	private static final String ENTITY_DATA = "MimicEntity";

	public GreenprintItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return recordedEntity(stack) != null;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
		InteractionHand hand) {
		if (player.isSpectator())
			return InteractionResult.PASS;
		if (player.isShiftKeyDown())
			return clearBinding(stack, player);
		if (target instanceof Player || !target.isAlive()
			|| !SlimeMimicHandler.isSlimeMimic(target))
			return InteractionResult.PASS;
		// A bound greenprint keeps its snapshot until the player explicitly clears it.
		if (recordedEntity(stack) != null)
			return InteractionResult.sidedSuccess(player.level().isClientSide);
		if (player.level().isClientSide)
			return InteractionResult.SUCCESS;

		CompoundTag entityData = new CompoundTag();
		if (!target.saveAsPassenger(entityData))
			return InteractionResult.FAIL;
		prepareCopy(entityData);
		CBItemData.edit(stack, data -> data.put(ENTITY_DATA, entityData));
		player.displayClientMessage(Component.translatable("message.create_biotech.greenprint.recorded",
			target.getDisplayName()), true);
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		return player.isShiftKeyDown()
			? new InteractionResultHolder<>(clearBinding(stack, player), stack)
			: InteractionResultHolder.pass(stack);
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Player player = context.getPlayer();
		// Clear before chests and other interactive blocks can consume the click.
		return player != null && player.isShiftKeyDown()
			? clearBinding(stack, player) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown())
			return clearBinding(stack, player);
		CompoundTag recorded = recordedEntity(stack);
		if (recorded == null)
			return InteractionResult.PASS;

		Level level = context.getLevel();
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

	InteractionResult clearBinding(ItemStack stack, Player player) {
		if (player.isSpectator())
			return InteractionResult.PASS;
		if (!player.level().isClientSide && recordedEntity(stack) != null) {
			CBItemData.edit(stack, data -> data.remove(ENTITY_DATA));
			player.displayClientMessage(Component.translatable("message.create_biotech.greenprint.cleared"), true);
		}
		return InteractionResult.sidedSuccess(player.level().isClientSide);
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
			player.displayClientMessage(Component.translatable("message.create_biotech.greenprint.release_failed"), true);
		return InteractionResult.FAIL;
	}
}
