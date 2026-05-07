package com.nobodiiiii.createbiotech.mixin;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Predicate;

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
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(FunnelBlockEntity.class)
public abstract class FunnelBlockEntityMixin {

	private static final String FUNNEL_MODE_CLASS = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity$Mode";

	@Shadow(remap = false)
	private InvManipulationBehaviour invManipulation;

	@Shadow(remap = false)
	private VersionedInventoryTrackerBehaviour invVersionTracker;

	@Shadow(remap = false)
	private int extractionCooldown;

	@Shadow(remap = false)
	private WeakReference<Entity> lastObserved;

	@Shadow(remap = false)
	private AABB getEntityOverflowScanningArea() {
		throw new AssertionError();
	}

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

	@Inject(method = "activateExtractor()V", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$extractCapturedSmallSlime(CallbackInfo ci) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		Level level = funnel.getLevel();
		if (level == null || invVersionTracker.stillWaiting(invManipulation))
			return;

		BlockState blockState = funnel.getBlockState();
		Direction facing = AbstractFunnelBlock.getFunnelFacing(blockState);
		if (facing == null)
			return;

		BasinBlockEntity basin = createBiotech$getTargetBasin(funnel);
		if (basin == null || !BasinEntityProcessing.hasCapturedSmallSlimes(basin))
			return;

		int amountToExtract = funnel.getAmountToExtract();
		ExtractionCountMode modeToExtract = funnel.getModeToExtract();
		if (!invManipulation.simulate()
			.extract(modeToExtract, amountToExtract)
			.isEmpty())
			return;
		if (createBiotech$isExtractorOutputBlocked(funnel))
			return;

		Predicate<ItemStack> filter = createBiotech$getFunnelFilter(funnel, extracted -> true);
		ItemStack stack =
			BasinEntityProcessing.getCapturedSmallSlimeExtractionStack(basin, modeToExtract, amountToExtract, filter);
		if (stack.isEmpty())
			return;
		List<Slime> slimes =
			BasinEntityProcessing.extractCapturedSmallSlimes(basin, modeToExtract, amountToExtract, filter, false);
		if (slimes.isEmpty())
			return;

		funnel.flap(false);
		funnel.onTransfer(stack);
		createBiotech$outputCapturedSlimes(slimes, createBiotech$getExtractorSlimeOutputPosition(funnel, facing),
			facing == Direction.UP ? new Vec3(0, 4 / 16f, 0) : Vec3.ZERO, facing);

		extractionCooldown = AllConfigs.server()
			.logistics.defaultExtractionTimer.get();
		ci.cancel();
	}

	@Inject(method = "activateExtractingBeltFunnel()V", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$activateExtractingBeltFunnel(CallbackInfo ci) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		if (funnel.getLevel() == null)
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(funnel.getLevel(), funnel.getBlockPos());
		if (support == null) {
			BlockState blockState = funnel.getBlockState();
			DirectBeltInputBehaviour inputBehaviour =
				BlockEntityBehaviour.get(funnel.getLevel(), funnel.getBlockPos().below(), DirectBeltInputBehaviour.TYPE);
			if (createBiotech$tryExtractCapturedSlimeToBelt(funnel, blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING),
				funnel.getBlockPos().below(), inputBehaviour))
				ci.cancel();
			return;
		}

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
			if (createBiotech$tryExtractCapturedSlimeToBelt(funnel, insertSide, support.segment().getBlockPos(),
				inputBehaviour))
				return;
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

	private boolean createBiotech$tryExtractCapturedSlimeToBelt(FunnelBlockEntity funnel, Direction insertSide,
		BlockPos beltPos, DirectBeltInputBehaviour inputBehaviour) {
		if (invVersionTracker.stillWaiting(invManipulation) || inputBehaviour == null)
			return false;
		if (!inputBehaviour.canInsertFromSide(insertSide))
			return false;
		if (inputBehaviour.isOccupied(insertSide))
			return false;

		BasinBlockEntity basin = createBiotech$getTargetBasin(funnel);
		if (basin == null || !BasinEntityProcessing.hasCapturedSmallSlimes(basin))
			return false;

		int amountToExtract = funnel.getAmountToExtract();
		ExtractionCountMode modeToExtract = funnel.getModeToExtract();
		if (!invManipulation.simulate()
			.extract(modeToExtract, amountToExtract)
			.isEmpty())
			return false;

		Predicate<ItemStack> filter = createBiotech$getFunnelFilter(funnel, extracted -> true);
		ItemStack stack =
			BasinEntityProcessing.getCapturedSmallSlimeExtractionStack(basin, modeToExtract, amountToExtract, filter);
		if (stack.isEmpty())
			return false;
		List<Slime> slimes =
			BasinEntityProcessing.extractCapturedSmallSlimes(basin, modeToExtract, amountToExtract, filter, false);
		if (slimes.isEmpty())
			return false;

		funnel.flap(false);
		funnel.onTransfer(stack);
		createBiotech$outputCapturedSlimes(slimes, createBiotech$getBeltSlimeOutputPosition(beltPos),
			Vec3.atLowerCornerOf(insertSide.getNormal())
				.scale(1 / 16d),
			insertSide);
		extractionCooldown = AllConfigs.server()
			.logistics.defaultExtractionTimer.get();
		return true;
	}

	private BasinBlockEntity createBiotech$getTargetBasin(FunnelBlockEntity funnel) {
		Level level = funnel.getLevel();
		if (level == null)
			return null;

		BlockFace targetFace = invManipulation.getTarget()
			.getOpposite();
		BlockPos targetPos = targetFace.getPos();
		return level.getBlockEntity(targetPos) instanceof BasinBlockEntity basin ? basin : null;
	}

	private Predicate<ItemStack> createBiotech$getFunnelFilter(FunnelBlockEntity funnel,
		Predicate<ItemStack> customFilter) {
		FilteringBehaviour filtering = BlockEntityBehaviour.get(funnel.getLevel(), funnel.getBlockPos(),
			FilteringBehaviour.TYPE);
		return stack -> (filtering == null || filtering.test(stack)) && customFilter.test(stack);
	}

	private Vec3 createBiotech$getExtractorSlimeOutputPosition(FunnelBlockEntity funnel, Direction facing) {
		Vec3 outputPos = VecHelper.getCenterOf(funnel.getBlockPos());
		boolean vertical = facing.getAxis()
			.isVertical();
		boolean up = facing == Direction.UP;

		outputPos = outputPos.add(Vec3.atLowerCornerOf(facing.getNormal())
			.scale(vertical ? up ? .15f : .5f : .25f));
		if (!vertical)
			outputPos = outputPos.subtract(0, .45f, 0);
		return outputPos;
	}

	private Vec3 createBiotech$getBeltSlimeOutputPosition(BlockPos beltPos) {
		return VecHelper.getCenterOf(beltPos)
			.add(0, .6f, 0);
	}

	private void createBiotech$outputCapturedSlimes(List<Slime> slimes, Vec3 outputPos, Vec3 motion,
		Direction facing) {
		for (int i = 0; i < slimes.size(); i++) {
			Slime slime = slimes.get(i);
			Vec3 position = outputPos.add(createBiotech$getSlimeOutputSpread(facing, i, slimes.size()));
			slime.stopRiding();
			slime.moveTo(position.x, position.y, position.z, slime.getYRot(), slime.getXRot());
			slime.fallDistance = 0;
			slime.setDeltaMovement(motion);
			lastObserved = new WeakReference<>(slime);
		}
	}

	private Vec3 createBiotech$getSlimeOutputSpread(Direction facing, int index, int count) {
		if (count <= 1)
			return Vec3.ZERO;
		double offset = (index - (count - 1) / 2d) * .25d;
		if (facing.getAxis()
			.isHorizontal())
			return Vec3.atLowerCornerOf(facing.getClockWise()
				.getNormal())
				.scale(offset);
		return new Vec3(offset, 0, 0);
	}

	private boolean createBiotech$isExtractorOutputBlocked(FunnelBlockEntity funnel) {
		AABB area = getEntityOverflowScanningArea();
		if (lastObserved != null) {
			Entity lastEntity = lastObserved.get();
			if (lastEntity != null && lastEntity.isAlive() && lastEntity.getBoundingBox()
				.intersects(area))
				return true;
			lastObserved = null;
		}

		for (Entity entity : funnel.getLevel()
			.getEntities(null, area)) {
			if (entity instanceof ItemEntity || entity instanceof PackageEntity) {
				lastObserved = new WeakReference<>(entity);
				return true;
			}
		}

		return false;
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
