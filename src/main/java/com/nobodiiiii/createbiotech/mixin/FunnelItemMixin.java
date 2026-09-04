package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelItem;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla {@link FunnelItem#getPlacementState} has its own specialization branch: if the placed FunnelBlock has a
 * horizontal {@code FACING}, it re-creates a {@link com.simibubi.create.content.logistics.funnel.BeltFunnelBlock}
 * with {@code HORIZONTAL_FACING = direction} — where {@code direction} is in <em>world</em> frame.
 * <p>
 * Our model stores {@code HORIZONTAL_FACING} in the belt surface's <em>local</em> frame. Vanilla's branch would
 * silently corrupt that, e.g. right-clicking a vertical belt's surface (worldFacing = SOUTH) would yield a
 * BeltFunnel with HORIZONTAL_FACING = SOUTH in world frame; our code then mis-interprets it as local SOUTH and
 * the funnel mis-renders / mis-extracts.
 * <p>
 * This mixin short-circuits {@link FunnelItem#getPlacementState} whenever the placement position is adjacent to a
 * surface our {@link BeltSurfaceResolver} knows about: we forward straight to {@link FunnelBlock#getStateForPlacement},
 * letting {@link FunnelBlockMixin} make the specialization decision (in local frame, with the correct
 * {@code localFacing.isVertical() → skip} guard).
 */
@Mixin(FunnelItem.class)
public abstract class FunnelItemMixin {

	@Inject(method = "getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("HEAD"), cancellable = true)
	private void createBiotech$bypassVanillaSpecialization(BlockPlaceContext ctx,
		CallbackInfoReturnable<BlockState> cir) {
		BeltSurface surface = BeltSurfaceResolver.resolveForPlacement(ctx.getLevel(), ctx.getClickedPos(),
			ctx.getClickedFace());
		if (surface == null)
			return; // foreign belt or no belt — let vanilla decide

		FunnelItem self = (FunnelItem) (Object) this;
		if (!(self.getBlock() instanceof FunnelBlock funnelBlock))
			return;

		BlockState state = funnelBlock.getStateForPlacement(ctx);
		cir.setReturnValue(state);
	}
}
