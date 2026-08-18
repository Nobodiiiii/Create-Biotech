package com.nobodiiiii.createbiotech.mixin;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltFunnelStateExtensions;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.processing.basin.SlimeCaptureFunnelAccess;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

@Mixin(value = FunnelBlockEntity.class, priority = 1001)
public abstract class FunnelBlockEntityMixin implements SlimeCaptureFunnelAccess {

	private static final String FUNNEL_MODE_CLASS = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity$Mode";

	@Shadow(remap = false)
	private InvManipulationBehaviour invManipulation;

	@Shadow(remap = false)
	private VersionedInventoryTrackerBehaviour invVersionTracker;

	@Shadow(remap = false)
	private int extractionCooldown;

	@Unique
	private long createBiotech$nextSmallSlimeCaptureTime;

	@Override
	public boolean createBiotech$tryCaptureSmallSlime(Slime slime) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		Level level = funnel.getLevel();
		if (level == null || level.isClientSide)
			return false;
		long gameTime = level.getGameTime();
		if (gameTime < createBiotech$nextSmallSlimeCaptureTime)
			return false;
		if (!BasinEntityProcessing.tryCaptureSmallSlimeFromFunnel(funnel, slime))
			return false;

		funnel.flap(true);
		int cooldown = Math.max(1, AllConfigs.server()
			.logistics.defaultExtractionTimer.get());
		createBiotech$nextSmallSlimeCaptureTime = gameTime + cooldown;
		return true;
	}

	@Inject(method = "determineCurrentMode()Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity$Mode;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$determineCurrentMode(CallbackInfoReturnable<Object> cir) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return;
		// Create handles POWERED before resolving the belt below. Let the original method
		// return PAUSED instead of replacing it with a custom belt mode.
		if (blockState.getOptionalValue(BlockStateProperties.POWERED).orElse(false))
			return;

		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PULLING || shape == Shape.PUSHING || funnel.getLevel() == null)
			return;

		BeltSurface surface =
			BeltSurfaceResolver.resolve(funnel.getLevel(), funnel.getBlockPos(), blockState);
		Direction facing;
		Direction movementFacing;
		if (surface != null) {
			facing = surface.worldize(blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING));
			movementFacing = surface.movementFacing();
		} else {
			Direction attachment =
				blockState.getValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE);
			if (attachment != Direction.DOWN)
				return;
			MagmaBeltBlockEntity magmaBelt =
				MagmaBeltHelper.getSegmentBE(funnel.getLevel(), funnel.getBlockPos().below());
			if (magmaBelt == null)
				return;
			facing = blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			movementFacing = magmaBelt.getMovementFacing();
		}
		cir.setReturnValue(getMode(movementFacing == facing ? "PUSHING_TO_BELT" : "TAKING_FROM_BELT"));
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

		BeltSurface surface =
			BeltSurfaceResolver.resolve(funnel.getLevel(), funnel.getBlockPos(), blockState);
		if (surface != null
			|| MagmaBeltHelper.getSegmentBE(funnel.getLevel(), funnel.getBlockPos().below()) != null)
			cir.setReturnValue(true);
	}

	@Inject(method = "activateExtractingBeltFunnel()V", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$activateExtractingBeltFunnel(CallbackInfo ci) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		if (funnel.getLevel() == null)
			return;

		BlockState blockState = funnel.getBlockState();
		BeltSurface surface =
			BeltSurfaceResolver.resolve(funnel.getLevel(), funnel.getBlockPos(), blockState);
		Direction insertSide;
		BlockPos beltPos;
		if (surface != null) {
			insertSide = surface.outwardNormal();
			beltPos = surface.beltPos();
		} else {
			insertSide = blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			beltPos = funnel.getBlockPos().below();
		}
		ci.cancel();
		if (invVersionTracker.stillWaiting(invManipulation))
			return;

		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(funnel.getLevel(), beltPos, DirectBeltInputBehaviour.TYPE);
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
		if (!createBiotech$materializeCapturedSmallSlimes(stack))
			inputBehaviour.handleInsertion(stack, insertSide, false);
		extractionCooldown = AllConfigs.server()
			.logistics.defaultExtractionTimer.get();
	}

	@Unique
	private boolean createBiotech$materializeCapturedSmallSlimes(ItemStack stack) {
		if (!BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			return false;

		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		Level level = funnel.getLevel();
		if (level == null)
			return false;

		Direction attachment = funnel.getBlockState()
			.getOptionalValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE)
			.orElse(Direction.DOWN);
		Vec3 surfacePosition = Vec3.atCenterOf(funnel.getBlockPos())
			.add(Vec3.atLowerCornerOf(attachment.getNormal()).scale(.5d));
		return CapturedSmallSlimeItem.materializeTransportedStack(level, surfacePosition, Vec3.ZERO, stack);
	}

	private static volatile Class cachedModeClass;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object getMode(String name) {
		try {
			Class modeClass = cachedModeClass;
			if (modeClass == null) {
				synchronized (FunnelBlockEntityMixin.class) {
					modeClass = cachedModeClass;
					if (modeClass == null)
						cachedModeClass = modeClass = Class.forName(FUNNEL_MODE_CLASS);
				}
			}
			return Enum.valueOf(modeClass, name);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to resolve FunnelBlockEntity mode " + name, exception);
		}
	}

	private static BlockFace createBiotech$getInventoryTarget(Level world, net.minecraft.core.BlockPos pos,
		BlockState state) {
		Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
		Direction outwardNormal = BeltFunnelStateExtensions.tiltedOutwardNormal(state);
		if (world != null && facing != null && state.getBlock() instanceof BeltFunnelBlock
			&& outwardNormal != null)
			facing = BeltSurface.worldizeCanonical(facing, outwardNormal);
		return new BlockFace(pos, facing == null ? Direction.DOWN : facing.getOpposite());
	}

}
