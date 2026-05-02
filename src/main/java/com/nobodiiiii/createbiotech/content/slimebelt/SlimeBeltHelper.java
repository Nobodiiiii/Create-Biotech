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

	public enum Track {
		FRONT,
		BACK
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
		return controller.beltLength * 2f;
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
		return normalizeLoopPosition(controller, loopPosition) > controller.beltLength;
	}

	public static float getFrontOffsetForLoopPosition(SlimeBeltBlockEntity controller, float loopPosition) {
		float normalized = normalizeLoopPosition(controller, loopPosition);
		float beltLength = controller.beltLength;
		return normalized <= beltLength ? normalized : 2 * beltLength - normalized;
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
		return isBackTrack(controller, loopPosition) ? frontNormal.scale(-1) : frontNormal;
	}

	public static Vec3 getTrackShift(SlimeBeltBlockEntity controller, float loopPosition) {
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL)
			return getTrackNormal(controller, loopPosition).scale(7d / 16d);
		if (!isBackTrack(controller, loopPosition))
			return Vec3.ZERO;
		return getTrackNormal(controller, loopPosition).scale(10d / 16d);
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
		float offset = getFrontOffsetForLoopPosition(controller, loopPosition);
		BeltSlope slope = controller.getBlockState()
			.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL) {
			int chainStep = controller.getBeltFacing()
				.getAxisDirection()
				.getStep();
			double y = chainStep > 0 ? offset : 1d - offset;
			Vec3 vec = Vec3.atLowerCornerOf(controller.getBlockPos())
				.add(.5d, y, .5d);
			return vec.add(getTrackShift(controller, loopPosition));
		}
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		float verticalMovement = verticality;
		if (offset < .5)
			verticalMovement = 0;
		verticalMovement = verticalMovement * (Math.min(offset, controller.beltLength - .5f) - .5f);
		Vec3 vec = VecHelper.getCenterOf(controller.getBlockPos());
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(controller.getBeltFacing()
			.getNormal())
			.scale(offset - .5f);

		if (slope == BeltSlope.VERTICAL)
			horizontalMovement = Vec3.ZERO;

		vec = vec.add(horizontalMovement)
			.add(0, verticalMovement, 0);
		return vec.add(getTrackShift(controller, loopPosition));
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

