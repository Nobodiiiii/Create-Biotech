package com.nobodiiiii.createbiotech.mixin;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.FunnelSupport;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

	@Inject(method = "tick()V", at = @At("HEAD"), remap = false)
	private void createBiotech$captureSmallSlimeForBasin(CallbackInfo ci) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		if (funnel.getLevel() == null || funnel.getLevel()
			.isClientSide || extractionCooldown > 0)
			return;

		if (!BasinEntityProcessing.tryCaptureSmallSlimeFromFunnel(funnel))
			return;

		funnel.flap(true);
		extractionCooldown = AllConfigs.server()
			.logistics.defaultExtractionTimer.get();
	}

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

		Direction facing =
			SlimeBeltHelper.getWorldFunnelFacing(support, blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING));
		cir.setReturnValue(getMode(SlimeBeltHelper.getMovementFacingForTrack(support.controller(), support.track()) == facing
			? "PUSHING_TO_BELT" : "TAKING_FROM_BELT"));
	}

	@Inject(method = "addBehaviours(Ljava/util/List;)V", at = @At("TAIL"), remap = false)
	private void createBiotech$replaceInventoryTarget(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
		int index = behaviours.indexOf(invManipulation);
		if (index == -1)
			return;
		InvManipulationBehaviour remapped = new InvManipulationBehaviour((FunnelBlockEntity) (Object) this,
			FunnelBlockEntityMixin::createBiotech$getInventoryTarget);
		behaviours.set(index, remapped);
		invManipulation = remapped;
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

	private static BlockFace createBiotech$getInventoryTarget(Level world, net.minecraft.core.BlockPos pos,
		BlockState state) {
		Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
		if (world != null && facing != null && state.getBlock() instanceof BeltFunnelBlock) {
			FunnelSupport support = SlimeBeltHelper.getFunnelSupport(world, pos);
			if (support != null)
				facing = SlimeBeltHelper.getWorldFunnelFacing(support, facing);
		}
		return new BlockFace(pos, facing == null ? Direction.DOWN : facing.getOpposite());
	}

}
