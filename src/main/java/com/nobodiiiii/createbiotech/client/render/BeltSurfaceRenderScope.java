package com.nobodiiiii.createbiotech.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.simibubi.create.content.logistics.FlapStuffs;

import dev.engine_room.flywheel.lib.transform.Translate;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

/**
 * Thread-local render scope for the active {@link BeltSurface} during a funnel's render pass.
 * <p>
 * The vanilla funnel renderer's {@code renderSafe} pushes a scope here so the static {@link FlapStuffs} hooks
 * (which don't see the funnel BE) can pick up the surface and apply its tilt.
 * <p>
 * Geometry methods ({@link #applyTilt}, {@link #tiltedCommonTransform}) live here rather than on {@link BeltSurface}
 * so the latter stays free of client-only types ({@link PoseStack}, FlapStuffs constants).
 */
public final class BeltSurfaceRenderScope {

	private static final ThreadLocal<BeltSurface> CURRENT = new ThreadLocal<>();

	private BeltSurfaceRenderScope() {}

	public static void push(BlockGetter world, BlockPos funnelPos) {
		BeltSurface s = BeltSurfaceResolver.resolve(world, funnelPos);
		if (s != null)
			CURRENT.set(s);
	}

	public static void pop() {
		CURRENT.remove();
	}

	public static BeltSurface current() {
		return CURRENT.get();
	}

	/** Apply this surface's tilt rotation around the block centre to a {@link PoseStack}. Caller pushes/pops. */
	public static void applyTilt(PoseStack poseStack, BeltSurface surface) {
		poseStack.translate(.5d, .5d, .5d);
		poseStack.mulPose(surface.surfaceToWorld());
		poseStack.translate(-.5d, -.5d, -.5d);
	}

	/** Mirror of {@link FlapStuffs#commonTransform} but composed with this surface's tilt. */
	public static Matrix4f tiltedCommonTransform(BlockPos visualPosition, Direction funnelFacing, float baseZOffset,
		BeltSurface surface) {
		float horizontalAngle = AngleHelper.horizontalAngle(funnelFacing.getOpposite());
		return new Matrix4f()
			.translate(visualPosition.getX(), visualPosition.getY(), visualPosition.getZ())
			.translate(.5f, .5f, .5f)
			.rotate(surface.surfaceToWorld())
			.translate(-.5f, -.5f, -.5f)
			.translate(Translate.CENTER, Translate.CENTER, Translate.CENTER)
			.rotateY(Mth.DEG_TO_RAD * horizontalAngle)
			.translate(-Translate.CENTER, -Translate.CENTER, -Translate.CENTER)
			.translate(FlapStuffs.X_OFFSET, 0, baseZOffset);
	}
}
