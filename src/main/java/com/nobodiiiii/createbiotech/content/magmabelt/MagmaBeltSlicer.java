package com.nobodiiiii.createbiotech.content.magmabelt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class MagmaBeltSlicer {

	public static class Feedback {
		int color = 0xffffff;
		AABB bb;
		String langKey;
		ChatFormatting formatting = ChatFormatting.WHITE;
	}

	public static InteractionResult useWrench(BlockState state, Level world, BlockPos pos, Player player,
											  InteractionHand handIn, BlockHitResult hit, Feedback feedBack) {
		MagmaBeltBlockEntity controllerBE = MagmaBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;
		if (state.getValue(MagmaBeltBlock.CASING) && hit.getDirection() != Direction.UP)
			return InteractionResult.PASS;
		if (state.getValue(MagmaBeltBlock.PART) == BeltPart.PULLEY && hit.getDirection()
			.getAxis() != Axis.Y)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == 2)
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(MagmaBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(MagmaBeltBlock.PART);
		List<BlockPos> beltChain = MagmaBeltBlock.getBeltChain(world, controllerBE.getBlockPos());
		boolean creative = player.isCreative();

		// Shorten from End
		if (hoveringEnd(state, hit)) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			for (BlockPos blockPos : beltChain) {
				MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(world, blockPos);
				if (belt == null)
					continue;
				belt.detachKinetics();
				belt.invalidateItemHandler();
				belt.beltLength = 0;
			}

			MagmaBeltInventory inventory = controllerBE.inventory;
			BlockPos next = part == BeltPart.END ? pos.subtract(beltVector) : pos.offset(beltVector);
			BlockState replacedState = world.getBlockState(next);
			MagmaBeltBlockEntity segmentBE = MagmaBeltHelper.getSegmentBE(world, next);
			KineticBlockEntity.switchToBlockState(world, next, ProperWaterloggedBlock.withWater(world,
				state.setValue(MagmaBeltBlock.CASING, segmentBE != null && segmentBE.casing != CasingType.NONE), next));
			world.setBlock(pos, ProperWaterloggedBlock.withWater(world, Blocks.AIR.defaultBlockState(), pos),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.removeBlockEntity(pos);
			world.levelEvent(2001, pos, Block.getId(state));

			if (!creative && MagmaBeltBlock.isMagmaBelt(replacedState)
				&& replacedState.getValue(MagmaBeltBlock.PART) == BeltPart.PULLEY)
				player.getInventory().placeItemBackInInventory(AllBlocks.SHAFT.asStack());

			// Eject overshooting items
			if (part == BeltPart.END && inventory != null) {
				List<TransportedItemStack> toEject = new ArrayList<>();
				for (TransportedItemStack transportedItemStack : inventory.getTransportedItems())
					if (transportedItemStack.beltPosition > beltLength - 1)
						toEject.add(transportedItemStack);
				toEject.forEach(inventory::eject);
				toEject.forEach(inventory.getTransportedItems()::remove);
			}

			// Transfer items to new controller
			if (part == BeltPart.START && segmentBE != null && inventory != null) {
				controllerBE.inventory = null;
				segmentBE.inventory = null;
				segmentBE.setController(next);
				for (TransportedItemStack transportedItemStack : inventory.getTransportedItems()) {
					transportedItemStack.beltPosition -= 1;
					if (transportedItemStack.beltPosition <= 0) {
						ItemEntity entity = new ItemEntity(world, pos.getX() + .5f, pos.getY() + 11 / 16f,
							pos.getZ() + .5f, transportedItemStack.stack);
						entity.setDeltaMovement(Vec3.ZERO);
						entity.setDefaultPickUpDelay();
						entity.hurtMarked = true;
						world.addFreshEntity(entity);
					} else
						segmentBE.getInventory()
							.addItem(transportedItemStack);
				}
			}

			return InteractionResult.SUCCESS;
		}

		MagmaBeltBlockEntity segmentBE = MagmaBeltHelper.getSegmentBE(world, pos);
		if (segmentBE == null)
			return InteractionResult.PASS;

		// Split in half
		int hitSegment = segmentBE.index;
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		Vec3 subtract = hit.getLocation()
			.subtract(centerOf);
		boolean towardPositive = subtract.dot(Vec3.atLowerCornerOf(beltVector)) > 0;
		BlockPos next = !towardPositive ? pos.subtract(beltVector) : pos.offset(beltVector);

		if (hitSegment == 0 || hitSegment == 1 && !towardPositive)
			return InteractionResult.FAIL;
		if (hitSegment == controllerBE.beltLength - 1 || hitSegment == controllerBE.beltLength - 2 && towardPositive)
			return InteractionResult.FAIL;

		// Look for shafts
		if (!creative) {
			int requiredShafts = 0;
			if (!segmentBE.hasPulley())
				requiredShafts++;
			BlockState other = world.getBlockState(next);
			if (MagmaBeltBlock.isMagmaBelt(other) && other.getValue(MagmaBeltBlock.PART) == BeltPart.MIDDLE)
				requiredShafts++;

			int amountRetrieved = 0;
			boolean beltFound = false;
			Search:
			while (true) {
				for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
					if (amountRetrieved == requiredShafts && beltFound)
						break Search;

					ItemStack itemstack = player.getInventory().getItem(i);
					if (itemstack.isEmpty())
						continue;
					int count = itemstack.getCount();

					if (itemstack.is(CBItems.MAGMA_BELT_CONNECTOR.get()) && !beltFound) {
						if (!world.isClientSide)
							itemstack.shrink(1);
						beltFound = true;
						continue;
					}

					if (AllBlocks.SHAFT.isIn(itemstack)) {
						int taken = Math.min(count, requiredShafts - amountRetrieved);
						if (!world.isClientSide)
							if (taken == count)
								player.getInventory().setItem(i, ItemStack.EMPTY);
							else
								itemstack.shrink(taken);
						amountRetrieved += taken;
					}
				}

				if (!world.isClientSide){
					player.getInventory().placeItemBackInInventory(AllBlocks.SHAFT.asStack(amountRetrieved));
					if (beltFound) player.getInventory().placeItemBackInInventory(new ItemStack(CBItems.MAGMA_BELT_CONNECTOR.get()));
				}
				return InteractionResult.FAIL;
			}
		}

		if (!world.isClientSide) {
			for (BlockPos blockPos : beltChain) {
				MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(world, blockPos);
				if (belt == null)
					continue;
				belt.detachKinetics();
				belt.invalidateItemHandler();
				belt.beltLength = 0;
			}

			MagmaBeltInventory inventory = controllerBE.inventory;
			KineticBlockEntity.switchToBlockState(world, pos,
				state.setValue(MagmaBeltBlock.PART, towardPositive ? BeltPart.END : BeltPart.START));
			KineticBlockEntity.switchToBlockState(world, next, world.getBlockState(next)
				.setValue(MagmaBeltBlock.PART, towardPositive ? BeltPart.START : BeltPart.END));
			world.playSound(null, pos, SoundEvents.WOOL_HIT,
				player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 2.3F);

			// Transfer items to new controller
			MagmaBeltBlockEntity newController = towardPositive ? MagmaBeltHelper.getSegmentBE(world, next) : segmentBE;
			if (newController != null && inventory != null) {
				newController.inventory = null;
				newController.setController(newController.getBlockPos());
				for (Iterator<TransportedItemStack> iterator = inventory.getTransportedItems()
					.iterator(); iterator.hasNext(); ) {
					TransportedItemStack transportedItemStack = iterator.next();
					float newPosition = transportedItemStack.beltPosition - hitSegment - (towardPositive ? 1 : 0);
					if (newPosition <= 0)
						continue;
					transportedItemStack.beltPosition = newPosition;
					iterator.remove();
					newController.getInventory()
						.addItem(transportedItemStack);
				}
			}
		}

		return InteractionResult.SUCCESS;
	}

	public static InteractionResult useConnector(BlockState state, Level world, BlockPos pos, Player player,
												 InteractionHand handIn, BlockHitResult hit, Feedback feedBack) {
		MagmaBeltBlockEntity controllerBE = MagmaBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == MagmaBeltConnectorItem.maxLength())
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(MagmaBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(MagmaBeltBlock.PART);
		Direction facing = state.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
		List<BlockPos> beltChain = MagmaBeltBlock.getBeltChain(world, controllerBE.getBlockPos());
		boolean creative = player.isCreative();

		if (!hoveringEnd(state, hit))
			return InteractionResult.PASS;

		BlockPos next = part == BeltPart.START ? pos.subtract(beltVector) : pos.offset(beltVector);
		MagmaBeltBlockEntity mergedController = null;
		int mergedBeltLength = 0;

		// Merge Belts / Extend at End
		BlockState nextState = world.getBlockState(next);
		if (!nextState.canBeReplaced()) {
			if (!MagmaBeltBlock.isMagmaBelt(nextState))
				return InteractionResult.FAIL;
			if (!beltStatesCompatible(state, nextState))
				return InteractionResult.FAIL;

			mergedController = MagmaBeltHelper.getControllerBE(world, next);
			if (mergedController == null)
				return InteractionResult.FAIL;
			if (mergedController.beltLength + beltLength > MagmaBeltConnectorItem.maxLength())
				return InteractionResult.FAIL;

			mergedBeltLength = mergedController.beltLength;

			if (!world.isClientSide) {
				boolean flipBelt = facing != nextState.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
				Optional<DyeColor> color = controllerBE.color;
				for (BlockPos blockPos : MagmaBeltBlock.getBeltChain(world, mergedController.getBlockPos())) {
					MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(world, blockPos);
					if (belt == null)
						continue;
					belt.detachKinetics();
					belt.invalidateItemHandler();
					belt.beltLength = 0;
					belt.color = color;
					if (flipBelt)
						world.setBlock(blockPos, flipBelt(world.getBlockState(blockPos)), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
				}

				// Reverse items
				if (flipBelt && mergedController.inventory != null) {
					List<TransportedItemStack> transportedItems = mergedController.inventory.getTransportedItems();
					for (TransportedItemStack transportedItemStack : transportedItems) {
						transportedItemStack.beltPosition = mergedBeltLength - transportedItemStack.beltPosition;
						transportedItemStack.prevBeltPosition =
							mergedBeltLength - transportedItemStack.prevBeltPosition;
					}
				}
			}
		}

		if (!world.isClientSide) {
			for (BlockPos blockPos : beltChain) {
				MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(world, blockPos);
				if (belt == null)
					continue;
				belt.detachKinetics();
				belt.invalidateItemHandler();
				belt.beltLength = 0;
			}

			MagmaBeltInventory inventory = controllerBE.inventory;
			KineticBlockEntity.switchToBlockState(world, pos, state.setValue(MagmaBeltBlock.PART, BeltPart.MIDDLE));

			if (mergedController == null) {
				// Attach at end
				world.setBlock(next,
					ProperWaterloggedBlock.withWater(world, state.setValue(MagmaBeltBlock.CASING, false), next),
					Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
				MagmaBeltBlockEntity segmentBE = MagmaBeltHelper.getSegmentBE(world, next);
				if (segmentBE != null)
					segmentBE.color = controllerBE.color;
				world.playSound(null, pos, SoundEvents.WOOL_PLACE,
					player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1F);

				// Transfer items to new controller
				if (part == BeltPart.START && segmentBE != null && inventory != null) {
					segmentBE.setController(next);
					for (TransportedItemStack transportedItemStack : inventory.getTransportedItems()) {
						transportedItemStack.beltPosition += 1;
						segmentBE.getInventory()
							.addItem(transportedItemStack);
					}
				}

			} else {
				// Merge with other
				MagmaBeltInventory mergedInventory = mergedController.inventory;
				world.playSound(null, pos, SoundEvents.WOOL_HIT,
					player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1.3F);
				MagmaBeltBlockEntity segmentBE = MagmaBeltHelper.getSegmentBE(world, next);
				KineticBlockEntity.switchToBlockState(world, next,
					state.setValue(MagmaBeltBlock.CASING, segmentBE != null && segmentBE.casing != CasingType.NONE)
						.setValue(MagmaBeltBlock.PART, BeltPart.MIDDLE));

				if (!creative) {
					player.getInventory().placeItemBackInInventory(AllBlocks.SHAFT.asStack(2));
					player.getInventory().placeItemBackInInventory(new ItemStack(CBItems.MAGMA_BELT_CONNECTOR.get()));
				}

				// Transfer items to other controller
				BlockPos search = controllerBE.getBlockPos();
				for (int i = 0; i < 10000; i++) {
					BlockState blockState = world.getBlockState(search);
					if (!MagmaBeltBlock.isMagmaBelt(blockState))
						break;
					if (blockState.getValue(MagmaBeltBlock.PART) != BeltPart.START) {
						search = search.subtract(beltVector);
						continue;
					}

					MagmaBeltBlockEntity newController = MagmaBeltHelper.getSegmentBE(world, search);

					if (newController != controllerBE && inventory != null) {
						newController.setController(search);
						controllerBE.inventory = null;
						for (TransportedItemStack transportedItemStack : inventory.getTransportedItems()) {
							transportedItemStack.beltPosition += mergedBeltLength;
							newController.getInventory()
								.addItem(transportedItemStack);
						}
					}

					if (newController != mergedController && mergedInventory != null) {
						newController.setController(search);
						mergedController.inventory = null;
						for (TransportedItemStack transportedItemStack : mergedInventory.getTransportedItems()) {
							if (newController == controllerBE)
								transportedItemStack.beltPosition += beltLength;
							newController.getInventory()
								.addItem(transportedItemStack);
						}
					}

					break;
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	static boolean beltStatesCompatible(BlockState state, BlockState nextState) {
		Direction facing1 = state.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope1 = state.getValue(MagmaBeltBlock.SLOPE);
		Direction facing2 = nextState.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope2 = nextState.getValue(MagmaBeltBlock.SLOPE);

		switch (slope1) {
			case UPWARD:
				if (slope2 == BeltSlope.DOWNWARD)
					return facing1 == facing2.getOpposite();
				return slope2 == slope1 && facing1 == facing2;
			case DOWNWARD:
				if (slope2 == BeltSlope.UPWARD)
					return facing1 == facing2.getOpposite();
				return slope2 == slope1 && facing1 == facing2;
			default:
				return slope2 == slope1 && facing2.getAxis() == facing1.getAxis();
		}
	}

	static BlockState flipBelt(BlockState state) {
		Direction facing = state.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope = state.getValue(MagmaBeltBlock.SLOPE);
		BeltPart part = state.getValue(MagmaBeltBlock.PART);

		if (slope == BeltSlope.UPWARD)
			state = state.setValue(MagmaBeltBlock.SLOPE, BeltSlope.DOWNWARD);
		else if (slope == BeltSlope.DOWNWARD)
			state = state.setValue(MagmaBeltBlock.SLOPE, BeltSlope.UPWARD);

		if (part == BeltPart.END)
			state = state.setValue(MagmaBeltBlock.PART, BeltPart.START);
		else if (part == BeltPart.START)
			state = state.setValue(MagmaBeltBlock.PART, BeltPart.END);

		return state.setValue(MagmaBeltBlock.HORIZONTAL_FACING, facing.getOpposite());
	}

	static boolean hoveringEnd(BlockState state, BlockHitResult hit) {
		BeltPart part = state.getValue(MagmaBeltBlock.PART);
		if (part == BeltPart.MIDDLE || part == BeltPart.PULLEY)
			return false;

		Vec3 beltVector = MagmaBeltHelper.getBeltVector(state);
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		Vec3 subtract = hit.getLocation()
			.subtract(centerOf);

		return subtract.dot(beltVector) > 0 == (part == BeltPart.END);
	}

	@OnlyIn(Dist.CLIENT)
	public static void tickHoveringInformation() {
		Minecraft mc = Minecraft.getInstance();
		HitResult target = mc.hitResult;
		if (target == null || !(target instanceof BlockHitResult result))
			return;

		ClientLevel world = mc.level;
		BlockPos pos = result.getBlockPos();
		BlockState state = world.getBlockState(pos);
		ItemStack held = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack heldOffHand = mc.player.getItemInHand(InteractionHand.OFF_HAND);

		if (mc.player.isShiftKeyDown())
			return;
		if (!MagmaBeltBlock.isMagmaBelt(state))
			return;

		Feedback feedback = new Feedback();

		// TODO: Populate feedback in the methods for clientside
		if (AllItems.WRENCH.isIn(held) || AllItems.WRENCH.isIn(heldOffHand))
			useWrench(state, world, pos, mc.player, InteractionHand.MAIN_HAND, result, feedback);
		else if (held.is(CBItems.MAGMA_BELT_CONNECTOR.get()) || heldOffHand.is(CBItems.MAGMA_BELT_CONNECTOR.get()))
			useConnector(state, world, pos, mc.player, InteractionHand.MAIN_HAND, result, feedback);
		else
			return;

		if (feedback.langKey != null)
			mc.player.displayClientMessage(CreateLang.translateDirect(feedback.langKey)
				.withStyle(feedback.formatting), true);
		else
			mc.player.displayClientMessage(CommonComponents.EMPTY, true);

		if (feedback.bb != null)
			Outliner.getInstance().chaseAABB("MagmaBeltSlicer", feedback.bb)
				.lineWidth(1 / 16f)
				.colored(feedback.color);
	}

}
