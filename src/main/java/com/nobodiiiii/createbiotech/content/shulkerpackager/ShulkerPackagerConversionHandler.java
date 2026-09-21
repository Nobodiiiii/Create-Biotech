package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.itemapplication.RecipeBackedItemApplication;
import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class ShulkerPackagerConversionHandler {
	private static final ResourceLocation RECIPE_ID =
		CreateBiotech.asResource("item_application/shulker_packager_manual_only");

	private ShulkerPackagerConversionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!CBFeature.SHULKER_PACKAGER.isEnabled())
			return;
		Player player = event.getEntity();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.PACKAGER.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		ItemStack processedItem = state.getCloneItemStack(event.getHitVec(), level, pos, player);
		var recipe = RecipeBackedItemApplication.find(level, RECIPE_ID, state, processedItem, heldItem);
		if (recipe.isEmpty())
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		if (!player.mayBuild())
			return;
		var resultBlock = RecipeBackedItemApplication.resultBlock(recipe.get());
		if (resultBlock.isEmpty() || !(resultBlock.get() instanceof ShulkerPackagerBlock shulkerPackagerBlock))
			return;

		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = convert(level, pos, player, event.getHand(), heldItem, state, recipe.get(),
			shulkerPackagerBlock);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static boolean convert(Level level, BlockPos pos, Player player,
		InteractionHand hand, ItemStack heldItem, BlockState state,
		ManualApplicationRecipe recipe, ShulkerPackagerBlock shulkerPackagerBlock) {
		if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager))
			return false;

		CompoundTag packagerData = packager.saveWithoutMetadata(level.registryAccess());
		level.removeBlockEntity(pos);

		BlockState newState = shulkerPackagerBlock
			.defaultBlockState()
			.setValue(ShulkerPackagerBlock.FACING, state.getValue(PackagerBlock.FACING))
			.setValue(ShulkerPackagerBlock.POWERED, state.getValue(PackagerBlock.POWERED))
			.setValue(ShulkerPackagerBlock.LINKED, state.getValue(PackagerBlock.LINKED));
		level.setBlock(pos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
		level.levelEvent(2001, pos, Block.getId(newState));

		if (!(level.getBlockEntity(pos) instanceof ShulkerPackagerBlockEntity shulkerPackager))
			return false;

		shulkerPackager.loadWithComponents(packagerData, level.registryAccess());
		shulkerPackager.notifyUpdate();
		level.playSound(null, pos, SoundEvents.SHULKER_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
		RecipeBackedItemApplication.consumeHeldItem(recipe, player, hand, heldItem);
		return true;
	}
}
