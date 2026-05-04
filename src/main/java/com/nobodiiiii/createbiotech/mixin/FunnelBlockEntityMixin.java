package com.nobodiiiii.createbiotech.mixin;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.FunnelSupport;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FunnelBlockEntity.class)
public abstract class FunnelBlockEntityMixin {

	private static final String FUNNEL_MODE_CLASS = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity$Mode";

	@Shadow(remap = false)
	private InvManipulationBehaviour invManipulation;

	@Shadow(remap = false)
	private VersionedInventoryTrackerBehaviour invVersionTracker;

	@Shadow(remap = false)
	private int extractionCooldown;

	@Inject(method = "determineCurrentMode()Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity$Mode;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$determineCurrentMode(CallbackInfoReturnable<Object> cir) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return;

		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PULLING || shape == Shape.PUSHING || funnel.getLevel() == null)
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(funnel.getLevel(), funnel.getBlockPos());
		if (support == null)
			return;

		Direction facing = blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
		cir.setReturnValue(getMode(SlimeBeltHelper.getMovementFacingForTrack(support.controller(), support.track()) == facing
			? "PUSHING_TO_BELT" : "TAKING_FROM_BELT"));
	}

	@Inject(method = "supportsAmountOnFilter()Z", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$supportsAmountOnFilter(CallbackInfoReturnable<Boolean> cir) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock) || funnel.getLevel() == null)
			return;

		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PUSHING)
			return;

		if (SlimeBeltHelper.getFunnelSupport(funnel.getLevel(), funnel.getBlockPos()) != null)
			cir.setReturnValue(true);
	}

	@Inject(method = "activateExtractingBeltFunnel()V", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$activateExtractingBeltFunnel(CallbackInfo ci) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		if (funnel.getLevel() == null)
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(funnel.getLevel(), funnel.getBlockPos());
		if (support == null)
			return;

		ci.cancel();
		if (invVersionTracker.stillWaiting(invManipulation))
			return;

		Direction insertSide = support.side();
		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(funnel.getLevel(), support.segment().getBlockPos(), DirectBeltInputBehaviour.TYPE);
		if (inputBehaviour == null)
			return;
		if (!inputBehaviour.canInsertFromSide(insertSide))
			return;
		if (inputBehaviour.isOccupied(insertSide))
			return;

		int amountToExtract = funnel.getAmountToExtract();
		ExtractionCountMode modeToExtract = funnel.getModeToExtract();
		MutableBoolean deniedByInsertion = new MutableBoolean(false);
		ItemStack stack = invManipulation.extract(modeToExtract, amountToExtract, extracted -> {
			ItemStack remainder = inputBehaviour.handleInsertion(extracted, insertSide, true);
			if (remainder.isEmpty())
				return true;
			deniedByInsertion.setTrue();
			return false;
		});
		if (stack.isEmpty()) {
			if (deniedByInsertion.isFalse())
				invVersionTracker.awaitNewVersion(invManipulation.getInventory());
			return;
		}

		funnel.flap(false);
		funnel.onTransfer(stack);
		inputBehaviour.handleInsertion(stack, insertSide, false);
		extractionCooldown = AllConfigs.server()
			.logistics.defaultExtractionTimer.get();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object getMode(String name) {
		try {
			Class enumClass = Class.forName(FUNNEL_MODE_CLASS);
			return Enum.valueOf(enumClass, name);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to resolve FunnelBlockEntity mode " + name, exception);
		}
	}

}
