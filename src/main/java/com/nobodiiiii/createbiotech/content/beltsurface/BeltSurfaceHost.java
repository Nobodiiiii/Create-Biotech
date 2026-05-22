package com.nobodiiiii.createbiotech.content.beltsurface;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

/**
 * Implemented by any belt block-entity that wants to participate in the unified funnel-specialization machinery.
 * <p>
 * A host advertises one or more {@link BeltSurface BeltSurfaces} on the segment it occupies. Each surface is a
 * (block-position, outward-normal, movement-facing) triple — i.e. one "side of the belt" that a funnel can attach to.
 * <p>
 * Belt implementations only need to enumerate their surfaces; the rest of the funnel logic (placement, render tilt,
 * runtime extract, mode determination) is shared and works off the {@code BeltSurface} alone.
 */
public interface BeltSurfaceHost {

	/** All belt-track surfaces exposed by this segment (typically one or two). May be empty if the host isn't ready. */
	List<BeltSurface> surfaces();

	/**
	 * Find the surface whose discretised {@code outwardNormal} equals the given direction.
	 * <p>
	 * Used by {@link BeltSurfaceResolver} to attach a funnel: a funnel at {@code P} adjacent to a host at
	 * {@code P.relative(D)} matches that host's surface whose normal points back toward the funnel (= {@code D.opposite()}).
	 */
	@Nullable
	default BeltSurface surfaceFor(Direction outwardNormal) {
		for (BeltSurface s : surfaces())
			if (s.outwardNormal() == outwardNormal)
				return s;
		return null;
	}
}
