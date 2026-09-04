package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltFunnelStateExtensions;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.nobodiiiii.createbiotech.content.beltsurface.SurfaceFunnelFilterSlotPositioning;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinAwareFunnelInventoryBehaviour;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.processing.basin.SlimeCaptureFunnelAccess;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelFilterSlotPositioning;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

@Mixin(value = FunnelBlockEntity.class, priority = 1001)
public abstract class FunnelBlockEntityMixin implements SlimeCaptureFunnelAccess {

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

	@ModifyArg(
		method = "activateExtractor()V",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/ref/WeakReference;<init>(Ljava/lang/Object;)V",
			ordinal = 1),
		index = 0,
		require = 1,
		expect = 1,
		remap = false)
	private Object createBiotech$observeMaterializedSlimeInsteadOfTemporaryItem(Object observed) {
		if (!(observed instanceof ItemEntity itemEntity)
			|| !BasinEntityProcessing.isCapturedSmallSlimeItem(itemEntity.getItem()))
			return observed;
		Entity replacement = CapturedSmallSlimeItem.consumeMaterializedReplacement(itemEntity);
		return replacement == null ? observed : replacement;
	}

	@WrapOperation(
		method = "determineCurrentMode()Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity$Mode;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"),
		require = 3,
		expect = 3)
	private Comparable<?> createBiotech$resolveSurfaceMode(BlockState blockState, Property<?> property,
		Operation<Comparable<?>> original) {
		Comparable<?> originalValue = original.call(blockState, property);
		if (property != BeltFunnelBlock.SHAPE || !(originalValue instanceof Shape shape)
			|| shape == Shape.PULLING || shape == Shape.PUSHING)
			return originalValue;

		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		Level level = funnel.getLevel();
		if (level == null)
			return originalValue;
		BeltSurface surface = BeltSurfaceResolver.resolve(level, funnel.getBlockPos(), blockState);
		Direction facing;
		Direction movementFacing;
		if (surface != null) {
			facing = surface.worldize(blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING));
			movementFacing = surface.movementFacing();
		} else {
			Direction attachment =
				blockState.getOptionalValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE).orElse(Direction.DOWN);
			if (attachment != Direction.DOWN)
				return originalValue;
			MagmaBeltBlockEntity magmaBelt =
				MagmaBeltHelper.getSegmentBE(level, funnel.getBlockPos().below());
			if (magmaBelt == null)
				return originalValue;
			facing = blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			movementFacing = magmaBelt.getMovementFacing();
		}
		// Feed an equivalent public Shape into Create's original branch so it returns its own private Mode.
		return movementFacing == facing ? Shape.PUSHING : Shape.PULLING;
	}

	@WrapOperation(
		method = "addBehaviours(Ljava/util/List;)V",
		at = @At(value = "NEW",
			target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;"),
		require = 1,
		expect = 1,
		remap = false)
	private InvManipulationBehaviour createBiotech$createSurfaceAwareInventoryBehaviour(
		SmartBlockEntity blockEntity, InvManipulationBehaviour.InterfaceProvider originalTarget,
		Operation<InvManipulationBehaviour> original) {
		if (!(blockEntity instanceof FunnelBlockEntity funnel))
			return original.call(blockEntity, originalTarget);
		return new BasinAwareFunnelInventoryBehaviour(funnel,
			FunnelBlockEntityMixin::createBiotech$getInventoryTarget);
	}

	@WrapOperation(
		method = "addBehaviours(Ljava/util/List;)V",
		at = @At(value = "NEW",
			target = "Lcom/simibubi/create/content/logistics/funnel/FunnelFilterSlotPositioning;"),
		require = 1,
		expect = 1,
		remap = false)
	private FunnelFilterSlotPositioning createBiotech$createSurfaceAwareFilterSlot(
		Operation<FunnelFilterSlotPositioning> original) {
		return new SurfaceFunnelFilterSlotPositioning();
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

	@WrapOperation(
		method = "activateExtractingBeltFunnel()V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"),
		require = 1,
		expect = 1)
	private Comparable<?> createBiotech$useSurfaceInsertionSide(BlockState blockState, Property<?> property,
		Operation<Comparable<?>> original) {
		Comparable<?> originalValue = original.call(blockState, property);
		if (property != BeltFunnelBlock.HORIZONTAL_FACING || !(originalValue instanceof Direction))
			return originalValue;
		BeltSurface surface = createBiotech$resolveSurface(blockState);
		return surface == null ? originalValue : surface.outwardNormal();
	}

	@ModifyArg(
		method = "activateExtractingBeltFunnel()V",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"),
		index = 1,
		require = 1,
		expect = 1)
	private BlockPos createBiotech$useSurfaceBeltPosition(BlockPos originalPosition) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BeltSurface surface = createBiotech$resolveSurface(funnel.getBlockState());
		return surface == null ? originalPosition : surface.beltPos();
	}

	@WrapOperation(
		method = "activateExtractingBeltFunnel()V",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/content/kinetics/belt/behaviour/DirectBeltInputBehaviour;handleInsertion(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/item/ItemStack;"),
		require = 1,
		expect = 1)
	private ItemStack createBiotech$materializeCommittedSmallSlimes(DirectBeltInputBehaviour inputBehaviour,
		ItemStack stack, Direction side, boolean simulate, Operation<ItemStack> original) {
		if (!simulate && createBiotech$materializeCapturedSmallSlimes(stack))
			return ItemStack.EMPTY;
		return original.call(inputBehaviour, stack, side, simulate);
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

	@Unique
	private BeltSurface createBiotech$resolveSurface(BlockState blockState) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		return BeltSurfaceResolver.resolve(funnel.getLevel(), funnel.getBlockPos(), blockState);
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
