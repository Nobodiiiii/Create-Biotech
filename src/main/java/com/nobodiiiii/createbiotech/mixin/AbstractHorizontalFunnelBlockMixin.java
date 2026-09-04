package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltFunnelStateExtensions;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.simibubi.create.content.logistics.funnel.AbstractHorizontalFunnelBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla {@link AbstractHorizontalFunnelBlock#rotate} only rotates {@code HORIZONTAL_FACING}. Our funnel state also
 * holds {@link BeltFunnelStateExtensions#ATTACHMENT_SURFACE}; if a rotation leaves it untouched the two properties
 * desync (HORIZONTAL_FACING now means something different in the canonical local frame because the attachment
 * surface — and hence the canonical forward axis — would have moved).
 * <p>
 * Both transforms must happen in world space: worldize the old local facing, transform it and the attachment,
 * then localize the facing under the new attachment frame. This also needs a dedicated mirror path because
 * Create chooses its mirror rotation from {@code HORIZONTAL_FACING}, which is local for surface funnels.
 */
@Mixin(AbstractHorizontalFunnelBlock.class)
public abstract class AbstractHorizontalFunnelBlockMixin {

	@Inject(method = "rotate(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("RETURN"), cancellable = true, require = 1, expect = 1)
	private void createBiotech$rotateAttachmentSurface(BlockState state, Rotation rotation,
		CallbackInfoReturnable<BlockState> cir) {
		BlockState result = cir.getReturnValue();
		if (result == null || !state.hasProperty(BeltFunnelStateExtensions.ATTACHMENT_SURFACE)
			|| !result.hasProperty(BeltFunnelStateExtensions.ATTACHMENT_SURFACE))
			return;

		Direction oldAttachment = state.getValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE);
		Direction oldLocalFacing = state.getValue(AbstractHorizontalFunnelBlock.HORIZONTAL_FACING);
		Direction oldWorldFacing = BeltSurface.worldizeCanonical(oldLocalFacing, oldAttachment.getOpposite());
		Direction newWorldFacing = rotation.rotate(oldWorldFacing);
		Direction newAttachment = rotation.rotate(oldAttachment);
		Direction newLocalFacing =
			BeltSurface.localizeCanonical(newWorldFacing, newAttachment.getOpposite());
		cir.setReturnValue(result
			.setValue(AbstractHorizontalFunnelBlock.HORIZONTAL_FACING, newLocalFacing)
			.setValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE, newAttachment));
	}

	@Inject(method = "mirror(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Mirror;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("HEAD"), cancellable = true, require = 1, expect = 1)
	private void createBiotech$mirrorSurfaceFrame(BlockState state, Mirror mirror,
		CallbackInfoReturnable<BlockState> cir) {
		if (!state.hasProperty(BeltFunnelStateExtensions.ATTACHMENT_SURFACE))
			return;

		Direction oldAttachment = state.getValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE);
		Direction oldLocalFacing = state.getValue(AbstractHorizontalFunnelBlock.HORIZONTAL_FACING);
		Direction oldWorldFacing = BeltSurface.worldizeCanonical(oldLocalFacing, oldAttachment.getOpposite());
		Direction newWorldFacing = mirror.mirror(oldWorldFacing);
		Direction newAttachment = mirror.mirror(oldAttachment);
		Direction newLocalFacing =
			BeltSurface.localizeCanonical(newWorldFacing, newAttachment.getOpposite());
		cir.setReturnValue(state
			.setValue(AbstractHorizontalFunnelBlock.HORIZONTAL_FACING, newLocalFacing)
			.setValue(BeltFunnelStateExtensions.ATTACHMENT_SURFACE, newAttachment));
	}
}
