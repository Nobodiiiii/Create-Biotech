package com.nobodiiiii.createbiotech.content.beltsurface;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entry points with non-overlapping contracts:
 * <ul>
 *   <li>{@link #resolve(BlockGetter, BlockPos)} — query an <strong>already-placed</strong> {@link BeltFunnelBlock}'s
 *       live hosted surface. The attachment direction comes from
 *       {@link BeltFunnelStateExtensions#ATTACHMENT_SURFACE}, but a result is returned only when the adjacent block
 *       explicitly implements {@link BeltSurfaceProviderBlock} and exposes a matching live
 *       {@link BeltSurfaceHost}. Ordinary Create, magma, and power belts return {@code null} without a block-entity
 *       lookup.</li>
 *   <li>{@link #resolve(BlockGetter, BlockPos, BlockState)} — the same state-encoded lookup when the caller already has
 *       the authoritative state. Geometry and rendering must not call either resolver overload; their transform is
 *       fully determined by the state property and is handled statelessly.</li>
 *   <li>{@link #resolveForPlacement(BlockGetter, BlockPos)} — used only during placement / state-conversion, when the
 *       block at {@code funnelPos} doesn't yet (or no longer) carry a BeltFunnel encoding. Scans the six orthogonal
 *       neighbours for a {@link BeltSurfaceHost} whose outward normal points back at {@code funnelPos}.</li>
 * </ul>
 */
public final class BeltSurfaceResolver {

	private BeltSurfaceResolver() {}

	/**
	 * Resolve via the block state's encoded attachment. Returns {@code null} unless the block at {@code funnelPos}
	 * is an {@link BeltFunnelBlock} attached to a live {@link BeltSurfaceHost}.
	 */
	@Nullable
	public static BeltSurface resolve(BlockGetter world, BlockPos funnelPos) {
		if (world == null || funnelPos == null)
			return null;
		BlockState selfState = world.getBlockState(funnelPos);
		return resolve(world, funnelPos, selfState);
	}

	/**
	 * Resolve from a caller-provided state. This keeps context-free collision queries independent of whether their
	 * synthetic {@link BlockGetter} exposes the state at {@code funnelPos}.
	 */
	@Nullable
	public static BeltSurface resolve(BlockGetter world, BlockPos funnelPos, @Nullable BlockState selfState) {
		if (world == null || funnelPos == null || selfState == null)
			return null;
		if (!(selfState.getBlock() instanceof BeltFunnelBlock))
			return null;
		return resolveFromBeltFunnelState(world, funnelPos, selfState);
	}

	/**
	 * Scan neighbours for a belt-side surface that would accept a funnel at {@code funnelPos}. Used by the placement
	 * mixin when {@code funnelPos} doesn't yet hold a BeltFunnel state (so {@link #resolve} would return null).
	 */
	@Nullable
	public static BeltSurface resolveForPlacement(BlockGetter world, BlockPos funnelPos) {
		return resolveForPlacement(world, funnelPos, null);
	}

	/**
	 * Placement lookup with a player-selected surface hint. The clicked outward face wins when it exposes a live
	 * surface; otherwise the stable six-neighbour scan preserves placement from replaceable blocks and automation.
	 */
	@Nullable
	public static BeltSurface resolveForPlacement(BlockGetter world, BlockPos funnelPos,
		@Nullable Direction preferredOutwardNormal) {
		if (world == null || funnelPos == null)
			return null;
		Direction preferredAttachment = preferredOutwardNormal == null
			? null
			: preferredOutwardNormal.getOpposite();
		if (preferredAttachment != null) {
			BeltSurface preferred = resolvePlacementCandidate(world, funnelPos, preferredAttachment);
			if (preferred != null)
				return preferred;
		}
		for (Direction d : Direction.values()) {
			if (d == preferredAttachment)
				continue;
			BeltSurface surface = resolvePlacementCandidate(world, funnelPos, d);
			if (surface != null)
				return surface;
		}
		return null;
	}

	@Nullable
	private static BeltSurface resolvePlacementCandidate(BlockGetter world, BlockPos funnelPos,
		Direction attachment) {
		BlockPos neighbourPos = funnelPos.relative(attachment);
		if (world instanceof Level level && !level.isLoaded(neighbourPos))
			return null;
		if (!(world.getBlockState(neighbourPos).getBlock() instanceof BeltSurfaceProviderBlock))
			return null;
		BlockEntity be = world.getBlockEntity(neighbourPos);
		if (!(be instanceof BeltSurfaceHost host))
			return null;
		return host.surfaceFor(attachment.getOpposite());
	}

	@Nullable
	private static BeltSurface resolveFromBeltFunnelState(BlockGetter world, BlockPos funnelPos, BlockState state) {
		Direction attachment = state.getOptionalValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE).orElse(null);
		if (attachment == null)
			return null;
		Direction outwardNormal = attachment.getOpposite();
		BlockPos beltPos = funnelPos.relative(attachment);
		if (world instanceof Level level && !level.isLoaded(beltPos))
			return null;

		// Block-state rejection is intentionally before getBlockEntity. Every ordinary BeltFunnel carries the
		// attachment property with DOWN as its default, but only explicit provider blocks can host a BeltSurface.
		if (!(world.getBlockState(beltPos).getBlock() instanceof BeltSurfaceProviderBlock))
			return null;

		BlockEntity be = world.getBlockEntity(beltPos);
		if (!(be instanceof BeltSurfaceHost host))
			return null;
		return host.surfaceFor(outwardNormal);
	}
}
