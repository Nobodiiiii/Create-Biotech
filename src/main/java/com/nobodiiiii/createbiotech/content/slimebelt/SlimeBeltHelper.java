package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.Map;

import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.kinetics.belt.BeltSlope;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class SlimeBeltHelper {

	private static final double SURFACE_HALF_THICKNESS = 8d / 16d;
	private static final double EXTRA_TURN_AND_SLOPE_OFFSET = 1d / 16d;

	public enum Track {
		FRONT,
		BACK
	}

	public enum LoopSection {
		FRONT,
		END_TURN,
		BACK,
		START_TURN
	}

	public static Map<Item, Boolean> uprightCache = new Object2BooleanOpenHashMap<>();
	public static final ResourceManagerReloadListener LISTENER = resourceManager -> uprightCache.clear();

	public static boolean isItemUpright(ItemStack stack) {
		return uprightCache.computeIfAbsent(
			stack.getItem(),
			item -> {
				boolean isFluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
				boolean useUpright = AllItemTags.UPRIGHT_ON_BELT.matches(stack);
				boolean forceDisableUpright = !AllItemTags.NOT_UPRIGHT_ON_BELT.matches(stack);

				return (isFluidHandler || useUpright) && forceDisableUpright;
			}
		);
	}

	public static SlimeBeltBlockEntity getSegmentBE(LevelAccessor world, BlockPos pos) {
		if (world instanceof Level l && !l.isLoaded(pos))
			return null;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (!(blockEntity instanceof SlimeBeltBlockEntity))
			return null;
		return (SlimeBeltBlockEntity) blockEntity;
	}

	public static SlimeBeltBlockEntity getControllerBE(LevelAccessor world, BlockPos pos) {
		SlimeBeltBlockEntity segment = getSegmentBE(world, pos);
		if (segment == null)
			return null;
		BlockPos controllerPos = segment.getController();
		if (controllerPos == null)
			return null;
		return getSegmentBE(world, controllerPos);
	}

	public static SlimeBeltBlockEntity getBeltForOffset(SlimeBeltBlockEntity controller, float offset) {
		return getBeltAtSegment(controller, (int) Math.floor(offset));
	}

	public static SlimeBeltBlockEntity getBeltAtSegment(SlimeBeltBlockEntity controller, int segment) {
		BlockPos pos = getPositionForOffset(controller, segment);
		BlockEntity be = controller.getLevel()
			.getBlockEntity(pos);
		if (be == null || !(be instanceof SlimeBeltBlockEntity))
			return null;
		return (SlimeBeltBlockEntity) be;
	}

	public static BlockPos getPositionForOffset(SlimeBeltBlockEntity controller, int offset) {
		BlockPos pos = controller.getBlockPos();
		Vec3i vec = controller.getBeltFacing()
			.getNormal();
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL) {
			int chainStep = controller.getBeltFacing()
				.getAxisDirection()
				.getStep();
			return pos.above(offset * chainStep);
		}
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;

		return pos.offset(offset * vec.getX(), Mth.clamp(offset, 0, controller.beltLength - 1) * verticality,
			offset * vec.getZ());
	}

	public static float getLoopLength(SlimeBeltBlockEntity controller) {
		return controller.beltLength * 2f + getConnectorLength(controller) * 2f;
	}

	public static float normalizeLoopPosition(SlimeBeltBlockEntity controller, float loopPosition) {
		float loopLength = getLoopLength(controller);
		if (loopLength <= 0)
			return 0;
		float normalized = loopPosition % loopLength;
		if (normalized < 0)
			normalized += loopLength;
		return normalized;
	}

	public static boolean isBackTrack(SlimeBeltBlockEntity controller, float loopPosition) {
		return getLoopSection(controller, loopPosition) == LoopSection.BACK;
	}

	public static float getFrontOffsetForLoopPosition(SlimeBeltBlockEntity controller, float loopPosition) {
		float normalized = normalizeLoopPosition(controller, loopPosition);
		float beltLength = controller.beltLength;
		float connectorLength = getConnectorLength(controller);
		if (normalized <= beltLength)
			return normalized;
		if (normalized < beltLength + connectorLength)
			return beltLength;
		if (normalized <= beltLength + connectorLength + beltLength)
			return 2 * beltLength + connectorLength - normalized;
		return 0;
	}

	public static Direction getFrontInputSide(net.minecraft.world.level.block.state.BlockState state) {
		BeltSlope slope = state.getValue(SlimeBeltBlock.SLOPE);
		Direction facing = state.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		return slope == BeltSlope.VERTICAL ? facing.getOpposite() : facing.getClockWise();
	}

	public static Track resolveInputTrack(net.minecraft.world.level.block.state.BlockState state, Direction side) {
		if (side == null || side == Direction.UP)
			return Track.FRONT;
		if (side == Direction.DOWN)
			return Track.BACK;

		Direction frontInputSide = getFrontInputSide(state);
		if (side == frontInputSide)
			return Track.FRONT;
		if (side == frontInputSide.getOpposite())
			return Track.BACK;

		return Track.FRONT;
	}

	public static Vec3 getFrontSurfaceNormal(SlimeBeltBlockEntity controller) {
		BlockState state = controller.getBlockState();
		BeltSlope slope = state.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL)
			return Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
				.getOpposite()
				.getNormal());
		if (slope == BeltSlope.SIDEWAYS)
			return Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
				.getClockWise()
				.getNormal());
		if (slope == BeltSlope.HORIZONTAL)
			return new Vec3(0, 1, 0);

		int verticality = slope == BeltSlope.DOWNWARD ? -1 : 1;
		Vec3 travel = Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
			.getNormal())
			.add(0, verticality, 0)
			.normalize();
		Vec3 across = Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
			.getClockWise()
			.getNormal());
		return across.cross(travel)
			.normalize();
	}

	public static Vec3 getTrackNormal(SlimeBeltBlockEntity controller, float loopPosition) {
		Vec3 frontNormal = getFrontSurfaceNormal(controller);
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		LoopSection section = getLoopSection(controller, loopPosition);
		if (isDiagonalSlope(slope)) {
			float frontOffset = getFrontOffsetForLoopPosition(controller, loopPosition);
			if (section == LoopSection.FRONT)
				return frontNormal;
			if (section == LoopSection.END_TURN)
				return getHorizontalEndSurfaceNormal(controller);
			if (section == LoopSection.BACK)
				return isSlopeMiddle(controller, frontOffset) ? frontNormal.scale(-1) : new Vec3(0, -1, 0);
			return getHorizontalEndSurfaceNormal(controller).scale(-1);
		}
		if (section == LoopSection.FRONT)
			return frontNormal;
		if (section == LoopSection.END_TURN)
			return getEndSurfaceNormal(controller);
		if (section == LoopSection.BACK)
			return frontNormal.scale(-1);
		return getEndSurfaceNormal(controller).scale(-1);
	}

	public static Vec3 getTrackShift(SlimeBeltBlockEntity controller, float loopPosition) {
		return getVectorForOffset(controller, loopPosition).subtract(getStraightBaseVector(controller,
			getFrontOffsetForLoopPosition(controller, loopPosition)));
	}

	public static Vec3 getPathAxis(SlimeBeltBlockEntity controller) {
		BlockState state = controller.getBlockState();
		BeltSlope slope = state.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL)
			return new Vec3(0, state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
				.getAxisDirection()
				.getStep(), 0);
		if (slope == BeltSlope.SIDEWAYS)
			return Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
				.getNormal());
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		return Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
			.getNormal())
			.add(0, verticality, 0)
			.normalize();
	}

	public static Vec3 getVectorForOffset(SlimeBeltBlockEntity controller, float loopPosition) {
		float normalized = normalizeLoopPosition(controller, loopPosition);
		float beltLength = controller.beltLength;
		float connectorLength = getConnectorLength(controller);
		LoopSection section = getLoopSection(controller, normalized);
		Vec3 position;

		if (section == LoopSection.FRONT)
			position = getStraightVectorForTrack(controller, Track.FRONT, normalized);
		else if (section == LoopSection.END_TURN) {
			float turnProgress = connectorLength <= 0 ? 1 : (normalized - beltLength) / connectorLength;
			position = getStraightVectorForTrack(controller, Track.FRONT, beltLength)
				.lerp(getStraightVectorForTrack(controller, Track.BACK, beltLength), turnProgress);
		} else if (section == LoopSection.BACK) {
			float progress = normalized - beltLength - connectorLength;
			position = getStraightVectorForTrack(controller, Track.BACK, beltLength - progress);
		} else {
			float turnProgress = connectorLength <= 0 ? 1
				: (normalized - (beltLength + connectorLength + beltLength)) / connectorLength;
			position = getStraightVectorForTrack(controller, Track.BACK, 0)
				.lerp(getStraightVectorForTrack(controller, Track.FRONT, 0), turnProgress);
		}

		return position.add(getAdditionalSurfaceOffset(controller, normalized, section));
	}

	public static float getConnectorLength(SlimeBeltBlockEntity controller) {
		Vec3 frontPoint = getStraightVectorForTrack(controller, Track.FRONT, 0);
		Vec3 backPoint = getStraightVectorForTrack(controller, Track.BACK, 0);
		return (float) frontPoint.distanceTo(backPoint);
	}

	public static LoopSection getLoopSection(SlimeBeltBlockEntity controller, float loopPosition) {
		float normalized = normalizeLoopPosition(controller, loopPosition);
		float beltLength = controller.beltLength;
		float connectorLength = getConnectorLength(controller);
		if (normalized <= beltLength)
			return LoopSection.FRONT;
		if (normalized < beltLength + connectorLength)
			return LoopSection.END_TURN;
		if (normalized <= beltLength + connectorLength + beltLength)
			return LoopSection.BACK;
		return LoopSection.START_TURN;
	}

	private static Vec3 getStraightVectorForTrack(SlimeBeltBlockEntity controller, Track track, float frontOffset) {
		return getStraightBaseVector(controller, frontOffset).add(getTrackOffsetVector(controller, track));
	}

	private static Vec3 getStraightBaseVector(SlimeBeltBlockEntity controller, float frontOffset) {
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL) {
			int chainStep = controller.getBeltFacing()
				.getAxisDirection()
				.getStep();
			double y = chainStep > 0 ? frontOffset : 1d - frontOffset;
			return Vec3.atLowerCornerOf(controller.getBlockPos())
				.add(.5d, y, .5d);
		}
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		float verticalMovement = frontOffset < .5f ? 0
			: verticality * (Math.min(frontOffset, controller.beltLength - .5f) - .5f);
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(controller.getBeltFacing()
			.getNormal())
			.scale(frontOffset - .5f);
		return VecHelper.getCenterOf(controller.getBlockPos())
			.add(horizontalMovement)
			.add(0, verticalMovement, 0);
	}

	private static Vec3 getTrackOffsetVector(SlimeBeltBlockEntity controller, Track track) {
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		Vec3 offset = slope == BeltSlope.VERTICAL || slope == BeltSlope.SIDEWAYS ? getFrontSurfaceNormal(controller).scale(SURFACE_HALF_THICKNESS)
			: new Vec3(0, SURFACE_HALF_THICKNESS, 0);
		return track == Track.FRONT ? offset : offset.scale(-1);
	}

	private static Vec3 getEndSurfaceNormal(SlimeBeltBlockEntity controller) {
		Vec3 pathAxis = getPathAxis(controller);
		if (pathAxis.lengthSqr() < 1.0E-6)
			return Vec3.atLowerCornerOf(controller.getBeltFacing()
				.getNormal());
		return pathAxis.normalize();
	}

	private static Vec3 getHorizontalEndSurfaceNormal(SlimeBeltBlockEntity controller) {
		return Vec3.atLowerCornerOf(controller.getBeltFacing()
			.getNormal());
	}

	private static boolean isDiagonalSlope(BeltSlope slope) {
		return slope == BeltSlope.UPWARD || slope == BeltSlope.DOWNWARD;
	}

	private static boolean isSlopeMiddle(SlimeBeltBlockEntity controller, float frontOffset) {
		return Mth.clamp(frontOffset, .5f, controller.beltLength - .5f) == frontOffset;
	}

	private static Vec3 getAdditionalSurfaceOffset(SlimeBeltBlockEntity controller, float loopPosition, LoopSection section) {
		if (section == LoopSection.END_TURN || section == LoopSection.START_TURN)
			return getTrackNormal(controller, loopPosition).scale(EXTRA_TURN_AND_SLOPE_OFFSET);

		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		if (!isDiagonalSlope(slope))
			return Vec3.ZERO;

		float frontOffset = getFrontOffsetForLoopPosition(controller, loopPosition);
		if (!isSlopeMiddle(controller, frontOffset))
			return Vec3.ZERO;

		return getTrackNormal(controller, loopPosition).scale(EXTRA_TURN_AND_SLOPE_OFFSET);
	}

	public static Vec3 getBeltVector(BlockState state) {
		BeltSlope slope = state.getValue(SlimeBeltBlock.SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
			.getNormal());
		if (slope == BeltSlope.VERTICAL)
			return new Vec3(0, state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
				.getAxisDirection()
				.getStep(), 0);
		return new Vec3(0, verticality, 0).add(horizontalMovement);
	}

}

