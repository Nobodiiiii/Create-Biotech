package com.nobodiiiii.createbiotech.content.universaljoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class UniversalJointItem extends BlockItem {

	public static final String FIRST_TARGET_KEY = "FirstUniversalJointTarget";
	public static final String FIRST_FACE_KEY = "FirstUniversalJointFace";
	private static final int RANGE_RADIUS = 2;
	private static final int PREVIEW_RANGE = 16;

	public UniversalJointItem(Properties properties) {
		super(CBBlocks.UNIVERSAL_JOINT.get(), properties);
	}

	@Nonnull
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			context.getItemInHand().setTag(null);
			return InteractionResult.SUCCESS;
		}

		Level level = context.getLevel();
		Endpoint clickedEndpoint = Endpoint.fromClick(level, context.getClickedPos(), context.getClickedFace());

		if (level.isClientSide)
			return clickedEndpoint != null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		if (player == null || clickedEndpoint == null)
			return InteractionResult.FAIL;

		CompoundTag tag = context.getItemInHand().getOrCreateTag();
		Endpoint firstEndpoint = readFirstEndpoint(level, tag);
		if (firstEndpoint == null && tag.contains(FIRST_TARGET_KEY)) {
			context.getItemInHand().setTag(null);
			tag = context.getItemInHand().getOrCreateTag();
		}

		if (firstEndpoint != null) {
			if (!canPair(firstEndpoint, clickedEndpoint))
				return InteractionResult.FAIL;
			if (!placeJointPair(level, firstEndpoint, clickedEndpoint))
				return InteractionResult.FAIL;

			if (!player.isCreative())
				context.getItemInHand().shrink(1);
			if (!context.getItemInHand().isEmpty())
				context.getItemInHand().setTag(null);
			player.getCooldowns().addCooldown(this, 5);
			return InteractionResult.SUCCESS;
		}

		writeFirstEndpoint(tag, clickedEndpoint);
		context.getItemInHand().setTag(tag);
		player.getCooldowns().addCooldown(this, 5);
		return InteractionResult.SUCCESS;
	}

	@Nullable
	private static Endpoint readFirstEndpoint(Level level, CompoundTag tag) {
		if (!tag.contains(FIRST_TARGET_KEY) || !tag.contains(FIRST_FACE_KEY))
			return null;

		Direction face = Direction.byName(tag.getString(FIRST_FACE_KEY));
		if (face == null)
			return null;

		BlockPos target = NbtUtils.readBlockPos(tag.getCompound(FIRST_TARGET_KEY));
		return Endpoint.fromClick(level, target, face);
	}

	private static void writeFirstEndpoint(CompoundTag tag, Endpoint endpoint) {
		tag.put(FIRST_TARGET_KEY, NbtUtils.writeBlockPos(endpoint.targetPos));
		tag.putString(FIRST_FACE_KEY, endpoint.clickedFace.getName());
	}

	private static boolean canPair(Endpoint first, Endpoint second) {
		return !first.jointPos.equals(second.jointPos)
			&& !first.targetPos.equals(second.targetPos)
			&& isWithinHitRange(first.jointPos, second.jointPos);
	}

	public static boolean canConnect(Level level, BlockPos firstTarget, Direction firstFace, BlockPos secondTarget,
		Direction secondFace) {
		Endpoint firstEndpoint = Endpoint.fromClick(level, firstTarget, firstFace);
		Endpoint secondEndpoint = Endpoint.fromClick(level, secondTarget, secondFace);
		return firstEndpoint != null && secondEndpoint != null && canPair(firstEndpoint, secondEndpoint);
	}

	public static BlockPos getJointPos(BlockPos targetPos, Direction clickedFace) {
		return targetPos.relative(clickedFace);
	}

	public static boolean isWithinHitRange(BlockPos firstJoint, BlockPos secondJoint) {
		BlockPos diff = secondJoint.subtract(firstJoint);
		return Math.abs(diff.getX()) <= RANGE_RADIUS
			&& Math.abs(diff.getY()) <= RANGE_RADIUS
			&& Math.abs(diff.getZ()) <= RANGE_RADIUS;
	}

	public static boolean isWithinPreviewRange(BlockPos firstJoint, BlockPos secondJoint) {
		BlockPos diff = secondJoint.subtract(firstJoint);
		return Math.abs(diff.getX()) < PREVIEW_RANGE
			&& Math.abs(diff.getY()) < PREVIEW_RANGE
			&& Math.abs(diff.getZ()) < PREVIEW_RANGE;
	}

	private static boolean placeJointPair(Level level, Endpoint first, Endpoint second) {
		BlockState firstState = stateForEndpoint(level, first);
		BlockState secondState = stateForEndpoint(level, second);

		boolean placedFirst = level.setBlock(first.jointPos, firstState, Block.UPDATE_ALL);
		boolean placedSecond = level.setBlock(second.jointPos, secondState, Block.UPDATE_ALL);
		if (!placedFirst || !placedSecond) {
			if (placedFirst)
				level.destroyBlock(first.jointPos, false);
			if (placedSecond)
				level.destroyBlock(second.jointPos, false);
			return false;
		}

		BlockEntity firstBlockEntity = level.getBlockEntity(first.jointPos);
		BlockEntity secondBlockEntity = level.getBlockEntity(second.jointPos);
		if (!(firstBlockEntity instanceof UniversalJointBlockEntity firstJoint)
			|| !(secondBlockEntity instanceof UniversalJointBlockEntity secondJoint)) {
			level.destroyBlock(first.jointPos, false);
			level.destroyBlock(second.jointPos, false);
			return false;
		}

		firstJoint.setLinkedPos(second.jointPos);
		secondJoint.setLinkedPos(first.jointPos);

		level.playSound(null, first.jointPos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		level.playSound(null, second.jointPos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
		return true;
	}

	private static BlockState stateForEndpoint(Level level, Endpoint endpoint) {
		BlockState state = CBBlocks.UNIVERSAL_JOINT.get()
			.defaultBlockState()
			.setValue(UniversalJointBlock.FACING, endpoint.jointFacing);
		return ProperWaterloggedBlock.withWater(level, state, endpoint.jointPos);
	}

	private record Endpoint(BlockPos targetPos, Direction clickedFace, BlockPos jointPos, Direction jointFacing) {

		@Nullable
		private static Endpoint fromClick(Level level, BlockPos targetPos, Direction clickedFace) {
			if (!isValidTarget(level, targetPos, clickedFace))
				return null;

			BlockPos jointPos = targetPos.relative(clickedFace);
			if (!canPlaceEndpoint(level, jointPos))
				return null;

			return new Endpoint(targetPos.immutable(), clickedFace, jointPos.immutable(), clickedFace.getOpposite());
		}

		private static boolean isValidTarget(Level level, BlockPos targetPos, Direction clickedFace) {
			if (!level.isLoaded(targetPos))
				return false;

			BlockState targetState = level.getBlockState(targetPos);
			return !targetState.isAir() && !targetState.canBeReplaced();
		}

		private static boolean canPlaceEndpoint(Level level, BlockPos jointPos) {
			if (jointPos.getY() < level.getMinBuildHeight() || jointPos.getY() >= level.getMaxBuildHeight())
				return false;
			if (!level.isLoaded(jointPos))
				return false;
			BlockState state = level.getBlockState(jointPos);
			return state.canBeReplaced() || state.getFluidState().getType() == Fluids.WATER;
		}
	}
}
