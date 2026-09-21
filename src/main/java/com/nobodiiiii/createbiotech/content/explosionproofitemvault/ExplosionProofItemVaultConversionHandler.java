package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.itemapplication.RecipeBackedItemApplication;
import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.items.ItemStackHandler;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class ExplosionProofItemVaultConversionHandler {
	private static final ResourceLocation RECIPE_ID =
		CreateBiotech.asResource("item_application/explosion_proof_item_vault");

	private ExplosionProofItemVaultConversionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!CBFeature.CREEPER_BLAST_CHAMBER.isEnabled())
			return;
		Player player = event.getEntity();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.ITEM_VAULT.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		ItemStack processedItem = state.getCloneItemStack(event.getHitVec(), level, pos, player);
		var recipe = RecipeBackedItemApplication.find(level, RECIPE_ID, state, processedItem, heldItem);
		if (recipe.isEmpty())
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		if (player.isShiftKeyDown() || !player.mayBuild())
			return;
		var resultBlock = RecipeBackedItemApplication.resultBlock(recipe.get());
		if (resultBlock.isEmpty()
			|| !(resultBlock.get() instanceof ExplosionProofItemVaultBlock explosionProofVault))
			return;

		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = convert(level, pos, player, event.getHand(), heldItem, state, recipe.get(),
			explosionProofVault);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static boolean convert(Level level, BlockPos pos, Player player,
		InteractionHand hand, ItemStack heldItem, BlockState state,
		ManualApplicationRecipe recipe, ExplosionProofItemVaultBlock explosionProofVault) {
		if (!(level.getBlockEntity(pos) instanceof ItemVaultBlockEntity vault))
			return false;

		ItemStackHandler inventoryCopy = copyInventory(vault.getInventoryOfBlock());
		ConnectivityHandler.splitMulti(vault);

		level.removeBlockEntity(pos);
		BlockState newState = explosionProofVault
			.defaultBlockState()
			.setValue(ItemVaultBlock.HORIZONTAL_AXIS, state.getValue(ItemVaultBlock.HORIZONTAL_AXIS))
			.setValue(ItemVaultBlock.LARGE, false);
		level.setBlock(pos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
		level.levelEvent(2001, pos, Block.getId(newState));

		if (!(level.getBlockEntity(pos) instanceof ExplosionProofItemVaultBlockEntity newVault))
			return false;

		newVault.applyInventoryToBlock(inventoryCopy);
		newVault.setChanged();
		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
		RecipeBackedItemApplication.consumeHeldItem(recipe, player, hand, heldItem);
		return true;
	}

	private static ItemStackHandler copyInventory(ItemStackHandler source) {
		ItemStackHandler copy = new ItemStackHandler(source.getSlots());
		for (int slot = 0; slot < source.getSlots(); slot++)
			copy.setStackInSlot(slot, source.getStackInSlot(slot)
				.copy());
		return copy;
	}
}
