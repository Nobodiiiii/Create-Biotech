package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class PowerBeltConnectorItem extends BlockItem {

	public PowerBeltConnectorItem(Properties properties) {
		super(CBBlocks.POWER_BELT.get(), properties);
	}

	@Override
	public String getDescriptionId() {
		return getOrCreateDescriptionId();
	}

	@Nonnull
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			context.getItemInHand()
				.setTag(null);
			return InteractionResult.SUCCESS;
		}

		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		boolean validAxis = validateAxis(level, pos);

		if (level.isClientSide)
			return validAxis ? InteractionResult.SUCCESS : InteractionResult.FAIL;

		CompoundTag tag = context.getItemInHand()
			.getOrCreateTag();
		BlockPos firstPulley = null;

		if (tag.contains("FirstPulley")) {
			firstPulley = NbtUtils.readBlockPos(tag.getCompound("FirstPulley"));
			if (!validateAxis(level, firstPulley) || !firstPulley.closerThan(pos, maxLength() * 2)) {
				tag.remove("FirstPulley");
				context.getItemInHand()
					.setTag(tag);
			}
		}

		if (!validAxis || player == null)
			return InteractionResult.FAIL;

		if (tag.contains("FirstPulley")) {
			if (!canConnect(level, firstPulley, pos))
				return InteractionResult.FAIL;

			if (firstPulley != null && !firstPulley.equals(pos)) {
				createBelts(level, firstPulley, pos);
				if (!player.isCreative())
					context.getItemInHand()
						.shrink(1);
			}

			if (!context.getItemInHand()
				.isEmpty()) {
				context.getItemInHand()
					.setTag(null);
				player.getCooldowns()
					.addCooldown(this, 5);
			}
			return InteractionResult.SUCCESS;
		}

		tag.put("FirstPulley", NbtUtils.writeBlockPos(pos));
		context.getItemInHand()
			.setTag(tag);
		player.getCooldowns()
			.addCooldown(this, 5);
		return InteractionResult.SUCCESS;
	}

	public static void createBelts(Level level, BlockPos start, BlockPos end) {
		level.playSound(null, BlockPos.containing(VecHelper.getCenterOf(start.offset(end))
			.scale(.5f)), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, .5f, 1f);

		BeltSlope slope = BeltSlope.HORIZONTAL;
		Direction facing = getFacingFromTo(start, end);
		List<BlockPos> beltsToCreate = getBeltChainBetween(start, end, facing);
		BlockState beltState = CBBlocks.POWER_BELT.get()
			.defaultBlockState();
		boolean failed = false;

		for (BlockPos currentPos : beltsToCreate) {
			BlockState existingBlock = level.getBlockState(currentPos);
			if (existingBlock.getDestroySpeed(level, currentPos) == -1) {
				failed = true;
				break;
			}

			BeltPart part = currentPos.equals(start) ? BeltPart.START : currentPos.equals(end) ? BeltPart.END : BeltPart.MIDDLE;
			BlockState shaftState = level.getBlockState(currentPos);
			boolean pulley = ShaftBlock.isShaft(shaftState);
			if (part == BeltPart.MIDDLE && pulley)
				part = BeltPart.PULLEY;

			if (!existingBlock.canBeReplaced())
				level.destroyBlock(currentPos, false);

			KineticBlockEntity.switchToBlockState(level, currentPos,
				ProperWaterloggedBlock.withWater(level, beltState.setValue(PowerBeltBlock.SLOPE, slope)
					.setValue(PowerBeltBlock.PART, part)
					.setValue(PowerBeltBlock.HORIZONTAL_FACING, facing), currentPos));
		}

		if (!failed)
			return;

		for (BlockPos currentPos : beltsToCreate)
			if (level.getBlockState(currentPos)
				.is(CBBlocks.POWER_BELT.get()))
				level.destroyBlock(currentPos, false);
	}

	private static Direction getFacingFromTo(BlockPos start, BlockPos end) {
		Axis beltAxis = start.getX() == end.getX() ? Axis.Z : Axis.X;
		BlockPos diff = end.subtract(start);
		AxisDirection axisDirection =
			beltAxis.choose(diff.getX(), 0, diff.getZ()) > 0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
		return Direction.get(axisDirection, beltAxis);
	}

	private static List<BlockPos> getBeltChainBetween(BlockPos start, BlockPos end, Direction direction) {
		List<BlockPos> positions = new LinkedList<>();
		int limit = 1000;
		BlockPos current = start;

		do {
			positions.add(current);
			current = current.relative(direction);
		} while (!current.equals(end) && limit-- > 0);

		positions.add(end);
		return positions;
	}

	public static boolean canConnect(Level level, BlockPos first, BlockPos second) {
		if (!level.isLoaded(first) || !level.isLoaded(second))
			return false;
		if (!second.closerThan(first, maxLength()))
			return false;

		BlockPos diff = second.subtract(first);
		if (diff.getY() != 0)
			return false;

		Axis shaftAxis = level.getBlockState(first)
			.getValue(BlockStateProperties.AXIS);
		if (shaftAxis == Axis.Y)
			return false;

		int x = diff.getX();
		int z = diff.getZ();
		if (x == 0 && z == 0)
			return false;
		if (shaftAxis.choose(x, 0, z) != 0)
			return false;
		if (x != 0 && z != 0)
			return false;
		if (shaftAxis != level.getBlockState(second)
			.getValue(BlockStateProperties.AXIS))
			return false;

		BlockEntity blockEntity = level.getBlockEntity(first);
		BlockEntity otherEntity = level.getBlockEntity(second);
		if (!(blockEntity instanceof KineticBlockEntity firstBE) || !(otherEntity instanceof KineticBlockEntity secondBE))
			return false;

		float speed1 = firstBE.getTheoreticalSpeed();
		float speed2 = secondBE.getTheoreticalSpeed();
		if (Math.signum(speed1) != Math.signum(speed2) && speed1 != 0 && speed2 != 0)
			return false;

		BlockPos step = BlockPos.containing(Math.signum(diff.getX()), 0, Math.signum(diff.getZ()));
		int limit = 1000;
		for (BlockPos currentPos = first.offset(step); !currentPos.equals(second) && limit-- > 0;
			currentPos = currentPos.offset(step)) {
			BlockState blockState = level.getBlockState(currentPos);
			if (ShaftBlock.isShaft(blockState) && blockState.getValue(AbstractSimpleShaftBlock.AXIS) == shaftAxis)
				continue;
			if (!blockState.canBeReplaced())
				return false;
		}

		return true;
	}

	public static int maxLength() {
		return AllConfigs.server().kinetics.maxBeltLength.get();
	}

	public static boolean validateAxis(Level level, BlockPos pos) {
		return level.isLoaded(pos) && ShaftBlock.isShaft(level.getBlockState(pos))
			&& level.getBlockState(pos)
				.getValue(BlockStateProperties.AXIS) != Axis.Y;
	}
}
