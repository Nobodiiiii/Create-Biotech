package com.nobodiiiii.createbiotech.content.slimebelt;

import static com.simibubi.create.content.kinetics.belt.BeltPart.MIDDLE;
import static com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL;
import static net.minecraft.core.Direction.AxisDirection.NEGATIVE;
import static net.minecraft.core.Direction.AxisDirection.POSITIVE;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.nobodiiiii.createbiotech.content.slimebelt.transport.SlimeBeltInventory;
import com.nobodiiiii.createbiotech.content.slimebelt.transport.SlimeItemHandlerBeltSegment;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public class SlimeBeltBlockEntity extends KineticBlockEntity {

	public int beltLength;
	public int index;
	protected BlockPos controller;
	protected SlimeBeltInventory inventory;
	public VersionedInventoryTrackerBehaviour invVersionTracker;
	public CompoundTag trackerUpdateTag;

	private final Map<Direction, LazyOptional<IItemHandler>> sidedHandlers;
	private LazyOptional<IItemHandler> nullSideHandler;

	public SlimeBeltBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		controller = BlockPos.ZERO;
		sidedHandlers = new EnumMap<>(Direction.class);
		nullSideHandler = LazyOptional.empty();
	}

	public SlimeBeltBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.SLIME_BELT.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(this::canInsertFrom)
			.allowingBeltFunnels()
			.setInsertionHandler(this::tryInsertingFromSide)
			.considerOccupiedWhen(this::isOccupied));
		behaviours.add(new TransportedItemStackHandlerBehaviour(this, this::applyToAllItems)
			.withStackPlacement(this::getWorldPositionOf));
		behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));
	}

	@Override
	public void tick() {
		if (beltLength == 0)
			SlimeBeltBlock.initBelt(level, worldPosition);

		super.tick();

		if (!CBBlocks.SLIME_BELT.get().equals(level.getBlockState(worldPosition).getBlock()))
			return;

		if (!isController())
			return;

		invalidateRenderBoundingBox();
		getInventory().tick();
	}

	@Override
	public float calculateStressApplied() {
		return isController() ? super.calculateStressApplied() : 0;
	}

	@Override
	public AABB createRenderBoundingBox() {
		return isController() ? super.createRenderBoundingBox().inflate(beltLength + 1) : super.createRenderBoundingBox();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (!isItemHandlerCap(cap))
			return super.getCapability(cap, side);
		if (!SlimeBeltBlock.canTransportObjects(getBlockState()))
			return super.getCapability(cap, side);
		return getItemHandler(side).cast();
	}

	@Override
	public void destroy() {
		super.destroy();
		if (isController())
			getInventory().ejectAll();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		invalidateItemHandlers();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		if (controller != null)
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		compound.putBoolean("IsController", isController());
		compound.putInt("Length", beltLength);
		compound.putInt("Index", index);

		if (isController())
			compound.put("Inventory", getInventory().write());

		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);

		if (compound.getBoolean("IsController"))
			controller = worldPosition;

		if (!wasMoved) {
			if (!isController())
				controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));
			trackerUpdateTag = compound;
			index = compound.getInt("Index");
			beltLength = compound.getInt("Length");
		}

		if (isController())
			getInventory().read(compound.getCompound("Inventory"));
	}

	@Override
	public void clearKineticInformation() {
		super.clearKineticInformation();
		beltLength = 0;
		index = 0;
		controller = null;
		trackerUpdateTag = new CompoundTag();
		invalidateItemHandlers();
	}

	public SlimeBeltBlockEntity getControllerBE() {
		if (controller == null || !level.isLoaded(controller))
			return null;
		BlockEntity be = level.getBlockEntity(controller);
		return be instanceof SlimeBeltBlockEntity slimeBelt ? slimeBelt : null;
	}

	public void setController(BlockPos controller) {
		this.controller = controller;
	}

	public BlockPos getController() {
		return controller == null ? worldPosition : controller;
	}

	public boolean isController() {
		return controller != null && worldPosition.equals(controller);
	}

	public float getBeltMovementSpeed() {
		return getSpeed() / 480f;
	}

	public float getDirectionAwareBeltMovementSpeed() {
		int offset = getBeltFacing().getAxisDirection()
			.getStep();
		if (getBeltFacing().getAxis() == Axis.X)
			offset *= -1;
		return getBeltMovementSpeed() * offset;
	}

	public boolean hasPulley() {
		return CBBlocks.SLIME_BELT.get().equals(getBlockState().getBlock())
			&& getBlockState().getValue(SlimeBeltBlock.PART) != MIDDLE;
	}

	public Vec3i getMovementDirection(boolean firstHalf) {
		return getMovementDirection(firstHalf, false);
	}

	public Vec3i getBeltChainDirection() {
		return getMovementDirection(true, true);
	}

	protected Vec3i getMovementDirection(boolean firstHalf, boolean ignoreHalves) {
		if (getSpeed() == 0)
			return BlockPos.ZERO;

		BlockState blockState = getBlockState();
		Direction beltFacing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BeltSlope slope = blockState.getValue(SlimeBeltBlock.SLOPE);
		if (slope == BeltSlope.VERTICAL) {
			int chainStep = beltFacing.getAxisDirection()
				.getStep();
			int y = getDirectionAwareBeltMovementSpeed() > 0 ? chainStep : -chainStep;
			return new Vec3i(0, y, 0);
		}
		BeltPart part = blockState.getValue(SlimeBeltBlock.PART);
		Axis axis = beltFacing.getAxis();

		Direction movementFacing = Direction.get(axis == Axis.X ? NEGATIVE : POSITIVE, axis);
		boolean notHorizontal = blockState.getValue(SlimeBeltBlock.SLOPE) != HORIZONTAL;
		if (getSpeed() < 0)
			movementFacing = movementFacing.getOpposite();
		Vec3i movement = movementFacing.getNormal();

		boolean slopeBeforeHalf = (part == BeltPart.END) == (beltFacing.getAxisDirection() == POSITIVE);
		boolean onSlope = notHorizontal && (part == MIDDLE || slopeBeforeHalf == firstHalf || ignoreHalves);
		boolean movingUp = onSlope && slope == (movementFacing == beltFacing ? BeltSlope.UPWARD : BeltSlope.DOWNWARD);

		if (!onSlope)
			return movement;

		return new Vec3i(movement.getX(), movingUp ? 1 : -1, movement.getZ());
	}

	public Direction getMovementFacing() {
		if (getBlockState().getValue(SlimeBeltBlock.SLOPE) == BeltSlope.VERTICAL)
			return getDirectionAwareBeltMovementSpeed() > 0 ? Direction.UP : Direction.DOWN;
		Axis axis = getBeltFacing().getAxis();
		return Direction.fromAxisAndDirection(axis, getBeltMovementSpeed() < 0 ^ axis == Axis.X ? NEGATIVE : POSITIVE);
	}

	public Direction getBeltFacing() {
		return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	public SlimeBeltInventory getInventory() {
		if (!isController()) {
			SlimeBeltBlockEntity controllerBE = getControllerBE();
			return controllerBE == null ? null : controllerBE.getInventory();
		}
		if (inventory == null)
			inventory = new SlimeBeltInventory(this);
		return inventory;
	}

	public void invalidateItemHandlers() {
		sidedHandlers.values()
			.forEach(LazyOptional::invalidate);
		sidedHandlers.clear();
		nullSideHandler.invalidate();
		nullSideHandler = LazyOptional.empty();
	}

	private LazyOptional<IItemHandler> getItemHandler(Direction side) {
		if (side == null) {
			if (!nullSideHandler.isPresent())
				nullSideHandler = LazyOptional.of(() -> new SlimeItemHandlerBeltSegment(getInventory(), index, Direction.UP));
			return nullSideHandler;
		}
		return sidedHandlers.computeIfAbsent(side,
			dir -> LazyOptional.of(() -> new SlimeItemHandlerBeltSegment(getInventory(), index, dir)));
	}

	private boolean canInsertFrom(Direction side) {
		if (getSpeed() == 0)
			return false;
		BlockState state = getBlockState();
		if (state.hasProperty(SlimeBeltBlock.SLOPE) && (state.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.SIDEWAYS
			|| state.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.VERTICAL))
			return false;
		return side != getMovementFacing().getOpposite();
	}

	private boolean isOccupied(Direction side) {
		SlimeBeltInventory beltInventory = getInventory();
		return beltInventory == null || getSpeed() == 0 || !beltInventory.canInsertAtFromSide(index, side);
	}

	private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
		SlimeBeltInventory beltInventory = getInventory();
		if (!SlimeBeltBlock.canTransportObjects(getBlockState()) || beltInventory == null)
			return transportedStack.stack;
		if (!canInsertFrom(side) || !beltInventory.canInsertAtFromSide(index, side))
			return transportedStack.stack;
		if (simulate)
			return ItemStack.EMPTY;

		TransportedItemStack copied = transportedStack.copy();
		beltInventory.prepareInsertedItem(copied, index, side);
		Direction movementFacing = getMovementFacing();
		if (!side.getAxis()
			.isVertical()) {
			Direction frontInputSide = SlimeBeltHelper.getFrontInputSide(getBlockState());
			boolean trackFaceInsert = side == frontInputSide || side == frontInputSide.getOpposite();
			if (side == movementFacing) {
				float extraOffset = copied.prevBeltPosition != 0
					&& SlimeBeltHelper.getSegmentBE(level, worldPosition.relative(movementFacing.getOpposite())) != null ? .26f : 0;
				copied.beltPosition =
					beltInventory.getSmoothInsertionPosition(index, side, extraOffset);
			} else if (!trackFaceInsert) {
				copied.sideOffset = side.getAxisDirection()
					.getStep() * .675f;
				if (side.getAxis() == Axis.X)
					copied.sideOffset *= -1;
			}
		}
		copied.prevBeltPosition = copied.beltPosition;
		copied.prevSideOffset = copied.sideOffset;
		beltInventory.addItem(copied);
		setChanged();
		sendData();
		return ItemStack.EMPTY;
	}

	private void applyToAllItems(float maxDistanceFromCenter,
		Function<TransportedItemStack, TransportedResult> processFunction) {
		SlimeBeltBlockEntity controller = getControllerBE();
		if (controller == null)
			return;
		SlimeBeltInventory beltInventory = controller.getInventory();
		if (beltInventory != null)
			beltInventory.applyToEachWithin(index + .5f, maxDistanceFromCenter, processFunction);
	}

	private Vec3 getWorldPositionOf(TransportedItemStack transported) {
		SlimeBeltBlockEntity controllerBE = getControllerBE();
		return controllerBE == null ? Vec3.ZERO : SlimeBeltHelper.getVectorForOffset(controllerBE, transported.beltPosition);
	}

	@Override
	protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
		return state.hasProperty(SlimeBeltBlock.SLOPE)
			&& (state.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.UPWARD
				|| state.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.DOWNWARD);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
		boolean connectedViaAxes, boolean connectedViaCogs) {
		if (target instanceof SlimeBeltBlockEntity belt && !connectedViaAxes)
			return getController().equals(belt.getController()) ? 1 : 0;
		return 0;
	}

	public boolean shouldRenderNormally() {
		if (level == null)
			return isController();
		BlockState state = getBlockState();
		return state != null && state.hasProperty(SlimeBeltBlock.PART) && state.getValue(SlimeBeltBlock.PART) == BeltPart.START;
	}
}
