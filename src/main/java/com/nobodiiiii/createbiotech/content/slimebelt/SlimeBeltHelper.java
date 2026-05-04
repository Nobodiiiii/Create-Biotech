package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.Map;

import javax.annotation.Nullable;

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

	private static final double TRACK_RENDER_OFFSET = 7d / 16d;
	public static final double VERTICAL_BELT_DROP = 1d / 16d;

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

	public record FunnelSupport(SlimeBeltBlockEntity segment, SlimeBeltBlockEntity controller, Track track,
		Direction side) {
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
		if (section == LoopSection.END_TURN || section == LoopSection.START_TURN)
			return getConnectorNormal(controller, loopPosition, section);
		if (isDiagonalSlope(slope) && section == LoopSection.BACK) {
			float frontOffset = getFrontOffsetForLoopPosition(controller, loopPosition);
			return isSlopeMiddle(controller, frontOffset) ? frontNormal.scale(-1) : new Vec3(0, -1, 0);
		}
		if (section == LoopSection.FRONT)
			return frontNormal;
		return frontNormal.scale(-1);
	}

	public static Vec3 getTrackShift(SlimeBeltBlockEntity controller, float loopPosition) {
		return getVectorForOffset(controller, loopPosition).subtract(getStraightBaseVector(controller,
			getFrontOffsetForLoopPosition(controller, loopPosition)));
	}

	public static Vec3 getTrackCenterVector(SlimeBeltBlockEntity controller, int segment, Track track) {
		float segmentCenter = Mth.clamp(segment + .5f, .5f, Math.max(.5f, controller.beltLength - .5f));
		return getStraightVectorForTrack(controller, track, segmentCenter);
	}

	public static Direction getRepresentativeSideForTrack(SlimeBeltBlockEntity controller, int segment, Track track) {
		Direction frontInputSide = getFrontInputSide(controller.getBlockState());
		Direction primary = track == Track.FRONT ? Direction.UP : Direction.DOWN;
		Direction alternate = track == Track.FRONT ? frontInputSide : frontInputSide.getOpposite();
		Vec3 trackNormal = getTrackNormal(controller, getTrackCenterLoopPosition(controller, segment, track));
		return getAlignment(trackNormal, alternate) > getAlignment(trackNormal, primary) ? alternate : primary;
	}

	public static BlockPos getFunnelPositionForTrack(SlimeBeltBlockEntity controller, int segment, Track track) {
		return getPositionForOffset(controller, segment)
			.relative(getRepresentativeSideForTrack(controller, segment, track));
	}

	public static Direction getMovementFacingForTrack(SlimeBeltBlockEntity controller, Track track) {
		Direction frontMovement = controller.getMovementFacing();
		return track == Track.FRONT ? frontMovement : frontMovement.getOpposite();
	}

	@Nullable
	public static FunnelSupport getFunnelSupport(LevelAccessor world, BlockPos funnelPos) {
		for (Direction side : Direction.values()) {
			BlockPos beltPos = funnelPos.relative(side.getOpposite());
			SlimeBeltBlockEntity segment = getSegmentBE(world, beltPos);
			if (segment == null)
				continue;
			SlimeBeltBlockEntity controller = segment.getControllerBE();
			if (controller == null)
				continue;
			for (Track track : Track.values())
				if (getRepresentativeSideForTrack(controller, segment.index, track) == side)
					return new FunnelSupport(segment, controller, track, side);
		}
		return null;
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
		if (section == LoopSection.FRONT)
			return getStraightVectorForTrack(controller, Track.FRONT, normalized);
		if (section == LoopSection.END_TURN || section == LoopSection.START_TURN)
			return getConnectorVector(controller, normalized, section);
		float progress = normalized - beltLength - connectorLength;
		return getStraightVectorForTrack(controller, Track.BACK, beltLength - progress);
	}

	public static float getConnectorLength(SlimeBeltBlockEntity controller) {
		return (float) getConnectorArcLength(controller, LoopSection.END_TURN);
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

	private static float getTrackCenterLoopPosition(SlimeBeltBlockEntity controller, int segment, Track track) {
		float segmentCenter = Mth.clamp(segment + .5f, .5f, Math.max(.5f, controller.beltLength - .5f));
		if (track == Track.FRONT)
			return segmentCenter;
		return 2 * controller.beltLength + getConnectorLength(controller) - segmentCenter;
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
				.add(.5d, y - VERTICAL_BELT_DROP, .5d);
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
		Vec3 offset = slope == BeltSlope.VERTICAL || slope == BeltSlope.SIDEWAYS
			? getFrontSurfaceNormal(controller).scale(TRACK_RENDER_OFFSET)
			: new Vec3(0, TRACK_RENDER_OFFSET, 0);
		return track == Track.FRONT ? offset : offset.scale(-1);
	}

	private static boolean isDiagonalSlope(BeltSlope slope) {
		return slope == BeltSlope.UPWARD || slope == BeltSlope.DOWNWARD;
	}

	private static boolean isSlopeMiddle(SlimeBeltBlockEntity controller, float frontOffset) {
		return Mth.clamp(frontOffset, .5f, controller.beltLength - .5f) == frontOffset;
	}

	private static Vec3 getConnectorVector(SlimeBeltBlockEntity controller, float loopPosition, LoopSection section) {
		Vec3 center = getConnectorCenter(controller, section);
		Vec3 start = getConnectorStartVector(controller, section);
		Vec3 startRadial = start.subtract(center);
		double startRadius = startRadial.length();
		if (startRadius < 1.0E-6)
			return start;
		Vec3 axis = getConnectorRotationAxis(controller);
		if (axis.lengthSqr() < 1.0E-6)
			return start.lerp(getConnectorEndVector(controller, section), getConnectorProgress(controller, loopPosition, section));
		double progress = Mth.clamp(getConnectorProgress(controller, loopPosition, section), 0, 1);
		double angle = getConnectorAngle(controller, section) * progress;
		double endRadius = getConnectorEndVector(controller, section).subtract(center)
			.length();
		double radius = Mth.lerp(progress, startRadius, endRadius);
		Vec3 rotated = rotateAroundAxis(startRadial.scale(1 / startRadius), axis, angle).scale(radius);
		return center.add(rotated);
	}

	private static Vec3 getConnectorNormal(SlimeBeltBlockEntity controller, float loopPosition, LoopSection section) {
		Vec3 center = getConnectorCenter(controller, section);
		Vec3 normal = getConnectorVector(controller, loopPosition, section).subtract(center);
		if (normal.lengthSqr() < 1.0E-6)
			return getPathAxis(controller);
		return normal.normalize();
	}

	private static Vec3 getConnectorCenter(SlimeBeltBlockEntity controller, LoopSection section) {
		return VecHelper.getCenterOf(getConnectorAxisPosition(controller, section));
	}

	private static Vec3 getConnectorStartVector(SlimeBeltBlockEntity controller, LoopSection section) {
		return getStraightVectorForTrack(controller, section == LoopSection.END_TURN ? Track.FRONT : Track.BACK,
			section == LoopSection.END_TURN ? controller.beltLength : 0);
	}

	private static Vec3 getConnectorEndVector(SlimeBeltBlockEntity controller, LoopSection section) {
		return getStraightVectorForTrack(controller, section == LoopSection.END_TURN ? Track.BACK : Track.FRONT,
			section == LoopSection.END_TURN ? controller.beltLength : 0);
	}

	private static float getConnectorProgress(SlimeBeltBlockEntity controller, float loopPosition, LoopSection section) {
		float normalized = normalizeLoopPosition(controller, loopPosition);
		float beltLength = controller.beltLength;
		float connectorLength = getConnectorLength(controller);
		if (connectorLength <= 0)
			return 1;
		if (section == LoopSection.END_TURN)
			return (normalized - beltLength) / connectorLength;
		return (normalized - (beltLength + connectorLength + beltLength)) / connectorLength;
	}

	private static double getConnectorArcLength(SlimeBeltBlockEntity controller, LoopSection section) {
		Vec3 center = getConnectorCenter(controller, section);
		double startRadius = getConnectorStartVector(controller, section).subtract(center)
			.length();
		double endRadius = getConnectorEndVector(controller, section).subtract(center)
			.length();
		double averageRadius = (startRadius + endRadius) / 2d;
		return averageRadius * Math.abs(getConnectorAngle(controller, section));
	}

	private static double getConnectorAngle(SlimeBeltBlockEntity controller, LoopSection section) {
		Vec3 center = getConnectorCenter(controller, section);
		Vec3 startRadial = getConnectorStartVector(controller, section).subtract(center);
		Vec3 endRadial = getConnectorEndVector(controller, section).subtract(center);
		double startRadius = startRadial.length();
		double endRadius = endRadial.length();
		if (startRadius < 1.0E-6 || endRadius < 1.0E-6)
			return 0;
		Vec3 axis = getConnectorRotationAxis(controller);
		if (axis.lengthSqr() < 1.0E-6)
			return 0;
		Vec3 startNormal = startRadial.scale(1 / startRadius);
		Vec3 endNormal = endRadial.scale(1 / endRadius);
		double sin = axis.dot(startNormal.cross(endNormal));
		double cos = Mth.clamp(startNormal.dot(endNormal), -1d, 1d);
		return Math.atan2(sin, cos);
	}

	private static Vec3 getConnectorRotationAxis(SlimeBeltBlockEntity controller) {
		BlockState state = controller.getBlockState();
		if (state.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.SIDEWAYS)
			return new Vec3(0, 1, 0);
		return Vec3.atLowerCornerOf(state.getValue(SlimeBeltBlock.HORIZONTAL_FACING)
			.getClockWise()
			.getNormal());
	}

	private static BlockPos getConnectorAxisPosition(SlimeBeltBlockEntity controller, LoopSection section) {
		return section == LoopSection.END_TURN ? getPositionForOffset(controller, controller.beltLength - 1)
			: controller.getBlockPos();
	}

	private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double angle) {
		Vec3 normalizedAxis = axis.normalize();
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		Vec3 cross = normalizedAxis.cross(vector);
		double dot = normalizedAxis.dot(vector);
		return vector.scale(cos)
			.add(cross.scale(sin))
			.add(normalizedAxis.scale(dot * (1 - cos)));
	}

	private static double getAlignment(Vec3 normal, Direction side) {
		return normal.dot(Vec3.atLowerCornerOf(side.getNormal()));
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

