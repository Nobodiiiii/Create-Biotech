package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltFunnelStateExtensions;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceHost;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceProviderBlock;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.logistics.funnel.AbstractHorizontalFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BeltFunnelBlock.class)
public abstract class BeltFunnelBlockMixin extends AbstractHorizontalFunnelBlock {

	protected BeltFunnelBlockMixin(Properties properties) {
		super(properties);
	}

	@Override
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		super.entityInside(state, level, pos, entity);
		BasinEntityProcessing.handleFunnelEntityInside(level, pos, entity);
	}

	/**
	 * Vanilla {@link BeltFunnelBlock#updateShape} reverts a BeltFunnel to its parent FunnelBlock when the belt is gone,
	 * copying {@code state.getValue(HORIZONTAL_FACING)} straight into {@code FunnelBlock.FACING}. Since we store
	 * {@code HORIZONTAL_FACING} in surface-local (canonical) frame, that copy puts a local-frame value into a
	 * world-frame slot — the reverted funnel ends up facing the wrong direction (the long-standing Bug 2).
	 * <p>
	 * Intercept the single {@code setValue(FunnelBlock.FACING, ...)} call in {@code updateShape} and worldize the
	 * value first using the attached surface that's still encoded in {@link BeltFunnelStateExtensions#ATTACHMENT_SURFACE}.
	 * Other {@code setValue} calls in {@code updateShape} (POWERED / EXTRACTING / SHAPE) pass through unchanged
	 * because the property check below filters by reference identity.
	 */
	@Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("RETURN"), cancellable = true)
	private void createBiotech$worldizeRevertFacing(BlockState state, Direction direction, BlockState neighbour,
		LevelAccessor world, BlockPos pos, BlockPos neighbourPos, CallbackInfoReturnable<BlockState> cir) {
		BlockState result = cir.getReturnValue();
		// Vanilla returns a FunnelBlock state only when reverting; non-revert paths keep returning a BeltFunnel.
		if (!(result.getBlock() instanceof FunnelBlock))
			return;
		// THE FIX: worldize the local-frame HORIZONTAL_FACING using the stored ATTACHMENT_SURFACE before
		// it lands in FunnelBlock.FACING (which is world frame). Restores the funnel's original world
		// orientation that placement captured — long-standing Bug 2.
		Direction localFacing = state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
		Direction attachment = state.getOptionalValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE).orElse(Direction.DOWN);
		Direction worldFacing = BeltSurface.worldizeCanonical(localFacing, attachment.getOpposite());
		cir.setReturnValue(result.setValue(FunnelBlock.FACING, worldFacing));
	}

	@Inject(method = "getShapeForPosition(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lcom/simibubi/create/content/logistics/funnel/BeltFunnelBlock$Shape;",
		at = @At("HEAD"), cancellable = true)
	private static void createBiotech$getShapeForPosition(BlockGetter world, BlockPos pos, Direction localFacing,
		boolean extracting, CallbackInfoReturnable<Shape> cir) {
		// Two call sites with different state at {@code pos}:
		// 1. runtime updateShape on an already-placed BeltFunnel — state-encoded {@link #resolve} succeeds.
		// 2. placement (our buildBeltFunnelState) before the block is in world — {@code pos} is still AIR;
		//    fall back to the neighbour scan so SHAPE is derived from the same surface we just discovered.
		// Without the fallback, vanilla's stock implementation looks at pos.below() only, doesn't see the belt
		// attached to a lateral face, and always returns the perpendicular (PUSHING/PULLING) shape — which leaves
		// the placed funnel's body unrotated (vanilla geometry) while the rest of our tilt machinery treats it
		// as a RETRACTED-style attached funnel. Result is the "wrong body + correct base" hybrid.
		BeltSurface surface = BeltSurfaceResolver.resolve(world, pos);
		if (surface == null)
			surface = BeltSurfaceResolver.resolveForPlacement(world, pos);
		// Only claim authority over live surface-host belts. Ordinary vanilla, magma, and power belts return null
		// and fall through to their own implementations, which read the actual belt facing.
		if (surface == null)
			return;
		// localFacing here is in surface-local (canonical) frame; project back to world to compare against
		// the belt's actual movement axis. RETRACTED iff the funnel sits in-line with belt motion.
		Shape perpendicular = extracting ? Shape.PUSHING : Shape.PULLING;
		Direction worldFacing = surface.worldize(localFacing);
		cir.setReturnValue(
			worldFacing.getAxis() != surface.movementFacing().getAxis() ? perpendicular : Shape.RETRACTED);
	}

	/**
	 * Mirror vanilla's {@code isOnValidBelt} semantics, but extended for the surface model: a BeltFunnel stays
	 * specialised iff there is still an actual {@link BeltSurfaceHost} adjacent in the encoded
	 * {@link BeltFunnelStateExtensions#ATTACHMENT_SURFACE} direction <em>and</em> that host still exposes a surface
	 * with the matching outward normal. When either condition fails (belt destroyed, replaced with a different
	 * orientation, casing change that drops the track, etc.), this returns {@code false} and vanilla's
	 * {@code updateShape} reverts the BeltFunnel to its parent {@link FunnelBlock} — at which point the
	 * {@link #createBiotech$worldizeRevertFacing} {@code @Inject} above feeds the original world facing back
	 * into {@link FunnelBlock#FACING} using {@code worldizeCanonical(HORIZONTAL_FACING, outward)}.
	 * <p>
	 * This validity hook performs the provider check directly so a missing provider is rejected immediately and
	 * the BeltFunnel can revert to its parent block.
	 */
	@Inject(method = "isOnValidBelt(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"), cancellable = true)
	private static void createBiotech$isOnValidBelt(BlockState state, LevelReader world, BlockPos pos,
		CallbackInfoReturnable<Boolean> cir) {
		Direction attachment = state.getOptionalValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE).orElse(null);
		if (attachment != null) {
			BlockPos beltPos = pos.relative(attachment);
			BlockState beltState = world.getBlockState(beltPos);
			if (MagmaBeltBlock.isMagmaBelt(beltState)) {
				cir.setReturnValue(MagmaBeltBlock.canTransportObjects(beltState));
				return;
			}
			if (beltState.getBlock() instanceof BeltSurfaceProviderBlock) {
				// Surface-based belt (e.g. slime belt): authoritative answer comes from the host's surface
				// table — covers lateral and vertical-track attachments that vanilla can't reason about.
				BlockEntity be = world.getBlockEntity(beltPos);
				cir.setReturnValue(be instanceof BeltSurfaceHost host
					&& host.surfaceFor(attachment.getOpposite()) != null);
				return;
			}
			// Not a surface host. For non-canonical attachments (lateral / top-of-vertical) vanilla's
			// pos.below()-only check is meaningless, so the funnel must revert.
			if (attachment != Direction.DOWN) {
				cir.setReturnValue(false);
				return;
			}
			// Canonical DOWN attachment over a non-surface-host belt — fall through to vanilla's
			// isOnValidBelt, which handles vanilla BeltBlock instanceof + DirectBeltInputBehaviour.
			return;
		}
		// No attachment encoded yet (e.g. vanilla's placement-time check on a fresh default state):
		// fall back to a surface scan so the original placement path can still find a neighbouring belt.
		if (BeltSurfaceResolver.resolve(world, pos) != null)
			cir.setReturnValue(true);
	}

	@Inject(method = "onWrenched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"), cancellable = true)
	private void createBiotech$onWrenched(BlockState state, UseOnContext context,
		CallbackInfoReturnable<InteractionResult> cir) {
		Level world = context.getLevel();
		BeltSurface surface = BeltSurfaceResolver.resolve(world, context.getClickedPos(), state);
		// Only claim wrench authority over surface-host belts (slime belt). Vanilla, magma, and power belts
		// return null and defer to their own wrench handlers.
		if (surface == null)
			return;
		if (world.isClientSide) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		Shape shape = state.getValue(BeltFunnelBlock.SHAPE);
		Shape newShape = shape;
		if (shape == Shape.PULLING)
			newShape = Shape.PUSHING;
		else if (shape == Shape.PUSHING)
			newShape = Shape.PULLING;
		else if (shape == Shape.EXTENDED)
			newShape = Shape.RETRACTED;
		else if (shape == Shape.RETRACTED) {
			// EXTENDED is only meaningful on the canonical "horizontal belt, top track" surface:
			// outwardNormal = UP and belt motion is horizontal. Otherwise stay RETRACTED.
			boolean canExtend = surface.outwardNormal() == Direction.UP
				&& surface.movementFacing().getAxis().isHorizontal();
			if (canExtend)
				newShape = Shape.EXTENDED;
		}

		if (newShape == shape) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		world.setBlockAndUpdate(context.getClickedPos(), state.setValue(BeltFunnelBlock.SHAPE, newShape));

		if (newShape == Shape.EXTENDED) {
			Direction localFacing = state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			BlockState opposite = world.getBlockState(context.getClickedPos().relative(surface.worldize(localFacing)));
			if (opposite.getBlock() instanceof BeltFunnelBlock
				&& opposite.getValue(BeltFunnelBlock.SHAPE) == Shape.EXTENDED
				&& opposite.getValue(BeltFunnelBlock.HORIZONTAL_FACING) == localFacing.getOpposite())
				AllAdvancements.FUNNEL_KISS.awardTo(context.getPlayer());
		}

		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
