package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExplosionProofItemVaultConversionHandler {

	private ExplosionProofItemVaultConversionHandler() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		if (player.isShiftKeyDown() || !player.mayBuild())
			return;

		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.ITEM_VAULT.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (!AllItems.STURDY_SHEET.isIn(heldItem))
			return;

		event.setCanceled(true);
		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = convert(level, pos, player, heldItem, state);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static boolean convert(Level level, BlockPos pos, Player player, ItemStack heldItem, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof ItemVaultBlockEntity vault))
			return false;

		ItemStackHandler inventoryCopy = copyInventory(vault.getInventoryOfBlock());
		ConnectivityHandler.splitMulti(vault);

		level.removeBlockEntity(pos);
		BlockState newState = CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get()
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
		if (!player.isCreative())
			heldItem.shrink(1);
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
