package com.nobodiiiii.createbiotech.content.beltsurface;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.items.ItemHandlerHelper;

/**
 * The single funnel-interaction algorithm shared by all belt implementations.
 * <p>
 * Verbatim port of Create's {@code BeltFunnelInteractionHandler.checkForFunnels}, parameterised over
 * {@link FunnelTickContext} so that any belt geometry (vanilla horizontal, slime-belt FRONT/BACK, ...) can reuse
 * exactly the same control flow. Bug fixes here apply everywhere.
 */
public final class FunnelInteractionCore {

	private FunnelInteractionCore() {}

	public static boolean check(FunnelTickContext ctx, TransportedItemStack currentItem, float nextOffset) {
		boolean fwd = ctx.movingTowardHigherSegments();
		int firstSegment = (int) Math.floor(ctx.currentSegmentPosition(currentItem));
		int step = fwd ? 1 : -1;
		firstSegment = Mth.clamp(firstSegment, 0, ctx.beltLength() - 1);
		Direction movementFacing = ctx.movementFacing();
		Level world = ctx.level();
		VersionedInventoryTrackerBehaviour tracker = ctx.invVersionTracker();

		for (int segment = firstSegment; fwd ? segment <= nextOffset : segment + 1 >= nextOffset; segment += step) {
			BlockPos funnelPos = ctx.funnelPosFor(segment);
			BlockState funnelState = world.getBlockState(funnelPos);
			if (!(funnelState.getBlock() instanceof BeltFunnelBlock))
				continue;
			Direction funnelFacing = ctx.worldizeFunnelFacing(funnelState.getValue(BeltFunnelBlock.HORIZONTAL_FACING));
			boolean blocking = funnelFacing == movementFacing.getOpposite();
			if (funnelFacing == movementFacing)
				continue;
			if (funnelState.getValue(BeltFunnelBlock.SHAPE) == Shape.PUSHING)
				continue;

			float funnelEntry = segment + .5f;
			if (funnelState.getValue(BeltFunnelBlock.SHAPE) == Shape.EXTENDED)
				funnelEntry += .499f * (fwd ? -1 : 1);
			boolean hasCrossed = fwd ? nextOffset > funnelEntry : nextOffset < funnelEntry;
			if (!hasCrossed)
				return false;
			if (blocking)
				ctx.lockItemAtEntry(currentItem, funnelEntry);

			if (world.isClientSide || funnelState.getOptionalValue(BeltFunnelBlock.POWERED).orElse(false))
				if (blocking)
					return true;
				else
					continue;

			BlockEntity be = world.getBlockEntity(funnelPos);
			if (!(be instanceof FunnelBlockEntity funnelBE))
				return true;

			InvManipulationBehaviour inserting = funnelBE.getBehaviour(InvManipulationBehaviour.TYPE);
			FilteringBehaviour filtering = funnelBE.getBehaviour(FilteringBehaviour.TYPE);

			if (inserting == null || filtering != null && !filtering.test(currentItem.stack))
				if (blocking)
					return true;
				else
					continue;

			if (tracker.stillWaiting(inserting))
				continue;

			int amountToExtract = funnelBE.getAmountToExtract();
			ExtractionCountMode modeToExtract = funnelBE.getModeToExtract();

			ItemStack toInsert = currentItem.stack.copy();
			if (amountToExtract > toInsert.getCount() && modeToExtract != ExtractionCountMode.UPTO)
				if (blocking)
					return true;
				else
					continue;

			if (amountToExtract != -1 && modeToExtract != ExtractionCountMode.UPTO) {
				toInsert.setCount(Math.min(amountToExtract, toInsert.getCount()));
				ItemStack remainder = inserting.simulate().insert(toInsert);
				if (!remainder.isEmpty())
					if (blocking)
						return true;
					else
						continue;
				else
					tracker.awaitNewVersion(inserting);
			}

			ItemStack remainder = inserting.insert(toInsert);
			if (toInsert.equals(remainder, false)) {
				tracker.awaitNewVersion(inserting);
				if (blocking)
					return true;
				else
					continue;
			}

			int notFilled = currentItem.stack.getCount() - toInsert.getCount();
			if (!remainder.isEmpty())
				remainder.grow(notFilled);
			else if (notFilled > 0)
				remainder = ItemHandlerHelper.copyStackWithSize(currentItem.stack, notFilled);

			funnelBE.flap(true);
			funnelBE.onTransfer(toInsert);
			currentItem.stack = remainder;
			ctx.notifyUpdate();
			if (blocking)
				return true;
		}

		return false;
	}
}
