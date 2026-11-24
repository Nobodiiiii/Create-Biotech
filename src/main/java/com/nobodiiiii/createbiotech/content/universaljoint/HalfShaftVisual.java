package com.nobodiiiii.createbiotech.content.universaljoint;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;

/**
 * The partial is authored facing north, so its base rotation must retain the signed facing rather
 * than orienting every instance towards the positive direction of its axis.
 */
public class HalfShaftVisual extends SingleAxisRotatingVisual<HalfShaftBlockEntity> {

	public static final PartialModel MODEL =
		PartialModel.of(CreateBiotech.asResource("block/half_shaft/world"));

	public HalfShaftVisual(VisualizationContext context, HalfShaftBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick, Direction.NORTH, Models.partial(MODEL));
		Direction facing = blockEntity.getBlockState().getValue(HalfShaftBlock.FACING);
		rotatingModel.rotation.identity();
		rotatingModel.rotateToFace(Direction.NORTH, facing);
		rotatingModel.setChanged();
	}
}
