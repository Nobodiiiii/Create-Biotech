package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShulkerPackagerConversionHandler {

	private ShulkerPackagerConversionHandler() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		if (!player.mayBuild())
			return;

		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.PACKAGER.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (!CapturedEntityBoxHelper.containsEntityType(heldItem, EntityType.SHULKER))
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
		if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager))
			return false;

		CompoundTag packagerData = packager.saveWithoutMetadata();
		level.removeBlockEntity(pos);

		BlockState newState = CBBlocks.SHULKER_PACKAGER.get()
			.defaultBlockState()
			.setValue(ShulkerPackagerBlock.FACING, state.getValue(PackagerBlock.FACING))
			.setValue(ShulkerPackagerBlock.POWERED, state.getValue(PackagerBlock.POWERED))
			.setValue(ShulkerPackagerBlock.LINKED, state.getValue(PackagerBlock.LINKED));
		level.setBlock(pos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
		level.levelEvent(2001, pos, Block.getId(newState));

		if (!(level.getBlockEntity(pos) instanceof ShulkerPackagerBlockEntity shulkerPackager))
			return false;

		shulkerPackager.load(packagerData);
		shulkerPackager.notifyUpdate();
		level.playSound(null, pos, SoundEvents.SHULKER_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
		if (!player.isCreative()) {
			ItemStack remainder = heldItem.hasCraftingRemainingItem() ? heldItem.getCraftingRemainingItem() : ItemStack.EMPTY;
			heldItem.shrink(1);
			if (!remainder.isEmpty())
				player.getInventory().placeItemBackInInventory(remainder);
		}
		return true;
	}
}
