package com.nobodiiiii.createbiotech.content.beltsurface;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Single entry point for the question "is the funnel at {@code funnelPos} attached to a belt surface, and if so which one?"
 * <p>
 * Scans the six orthogonal neighbours, finds the first {@link BeltSurfaceHost} whose {@link BeltSurfaceHost#surfaceFor}
 * matches the corresponding outward direction, and returns its {@link BeltSurface}.
 * <p>
 * No caching for now — caching is layered on later (see Step 6 of the refactor).
 */
public final class BeltSurfaceResolver {

	private BeltSurfaceResolver() {}

	@Nullable
	public static BeltSurface resolve(BlockGetter world, BlockPos funnelPos) {
		if (world == null || funnelPos == null)
			return null;
		for (Direction d : Direction.values()) {
			BlockPos neighbourPos = funnelPos.relative(d);
			if (world instanceof Level level && !level.isLoaded(neighbourPos))
				continue;
			BlockEntity be = world.getBlockEntity(neighbourPos);
			if (!(be instanceof BeltSurfaceHost host))
				continue;
			BeltSurface s = host.surfaceFor(d.getOpposite());
			if (s != null)
				return s;
		}
		return null;
	}
}
