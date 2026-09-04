package com.nobodiiiii.createbiotech.content.magmabelt;

import net.minecraft.core.HolderLookup;

import static com.simibubi.create.content.kinetics.belt.BeltPart.MIDDLE;
import static com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL;
import static net.minecraft.core.Direction.AxisDirection.NEGATIVE;
import static net.minecraft.core.Direction.AxisDirection.POSITIVE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltModel;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltInventory;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltMovementHandler;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltMovementHandler.TransportedEntityInfo;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltTunnelInteractionHandler;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaItemHandlerBeltSegment;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltTunnelCapabilityInvalidator;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltPort;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementSegment;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;

public class MagmaBeltBlockEntity extends KineticBlockEntity
	implements StandardItemBeltPort, CBBeltPlacementSegment, Clearable {

	/** Ticks to wait before re-attempting a chain init that already failed once. */
	private static final int INIT_RETRY_INTERVAL = 20;

	public Map<Entity, TransportedEntityInfo> passengers;
	public int beltLength;
	public int index;
	public Direction lastInsert;
	public CasingType casing;
	public boolean covered;

	protected BlockPos controller;
	protected MagmaBeltInventory inventory;
	protected IItemHandler itemHandler;
	private int initRetryCooldown;
	public VersionedInventoryTrackerBehaviour invVersionTracker;

	public CompoundTag trackerUpdateTag;

	public MagmaBeltBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		controller = BlockPos.ZERO;
		itemHandler = null;
		casing = CasingType.NONE;
	}

	public MagmaBeltBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.MAGMA_BELT.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(this::canInsertFrom)
			.setInsertionHandler(this::tryInsertingFromSide).considerOccupiedWhen(this::isOccupied));
		behaviours.add(new TransportedItemStackHandlerBehaviour(this, this::applyToAllItems)
			.withStackPlacement(this::getWorldPositionOf));
		behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));
	}

	@Override
	public void tick() {
		// Init belt
		if (beltLength == 0)
			tryInitBelt();

		super.tick();

		// The block entity's cached state is kept in sync by LevelChunk#setBlockState, so this needs no
		// chunk lookup. Create reads through the level here; the power belt already relies on the cache.
		if (!MagmaBeltBlock.isMagmaBelt(getBlockState()))
			return;

		initializeItemHandler();

		// Move Items
		if (!isController())
			return;

		invalidateRenderBoundingBox();

		getInventory().tick();

		if (level.isClientSide)
			spawnFlameParticles();

		if (getSpeed() == 0)
			return;

		// Move Entities
		if (passengers == null)
			passengers = new HashMap<>();

		List<Entity> toRemove = new ArrayList<>();
		passengers.forEach((entity, info) -> {
			boolean canBeTransported = MagmaBeltMovementHandler.canBeTransported(entity);
			boolean leftTheBelt =
				info.getTicksSinceLastCollision() > ((getBlockState().getValue(MagmaBeltBlock.SLOPE) != HORIZONTAL) ? 3 : 1);
			if (!canBeTransported || leftTheBelt) {
				toRemove.add(entity);
				return;
			}

			info.tick();
			MagmaBeltMovementHandler.transportEntity(this, entity, info);
		});
		toRemove.forEach(passengers::remove);
	}

	/**
	 * Chain init is retried from the tick because the chain can span a chunk that was not loaded yet. A failed
	 * attempt walks the chain from this segment back to its start, and every segment attempts it, so retrying
	 * every tick costs O(n²) block lookups per tick for as long as the far end stays unloaded. Only repeated
	 * failures back off — the attempt right after placement, slicing or a contraption disassembly still runs
	 * on the very next tick.
	 */
	private void tryInitBelt() {
		if (initRetryCooldown > 0) {
			initRetryCooldown--;
			return;
		}
		MagmaBeltBlock.initBelt(level, worldPosition);
		if (beltLength == 0)
			initRetryCooldown = INIT_RETRY_INTERVAL;
	}

	private void spawnFlameParticles() {
		if (beltLength <= 0)
			return;

		RandomSource random = level.random;
		CBConfigs.BeltParticles particles = CBConfigs.CLIENT.beltParticles;
		float chance = (float) Math.min(particles.magmaBeltMaxChance.get(),
			particles.magmaBeltBaseChance.get() + beltLength * particles.magmaBeltLengthChance.get());
		if (random.nextFloat() >= chance)
			return;

		int count = random.nextFloat() < .15f ? 2 : 1;
		for (int i = 0; i < count; i++)
			spawnFlameParticle(random);
	}

	private void spawnFlameParticle(RandomSource random) {
		float frontOffset = random.nextFloat() * beltLength;
		Vec3 normal = MagmaBeltHelper.getSurfaceNormal(this)
			.normalize();
		Vec3 across = MagmaBeltHelper.getPathAxis(this)
			.cross(normal);
		if (across.lengthSqr() > 1.0E-6d)
			across = across.normalize()
				.scale((random.nextDouble() - .5d) * .55d);
		else
			across = Vec3.ZERO;

		Vec3 position = MagmaBeltHelper.getVectorForOffset(this, frontOffset)
			.add(across)
			.add(normal.scale(7d / 16d + random.nextDouble() * .05d));
		double drift = .0125d;
		Vec3 motion = normal.scale(.01d + random.nextDouble() * .015d)
			.add((random.nextDouble() - .5d) * drift, .0125d + random.nextDouble() * .025d,
				(random.nextDouble() - .5d) * drift);
		level.addParticle(ParticleTypes.FLAME, position.x, position.y, position.z, motion.x, motion.y, motion.z);
	}

	@Override
	public float calculateStressApplied() {
		if (!isController())
			return 0;
		return super.calculateStressApplied();
	}

	@Override
	public AABB createRenderBoundingBox() {
		if (!isController())
			return super.createRenderBoundingBox();
		else
			return super.createRenderBoundingBox().inflate(beltLength + 1);
	}

	protected void initializeItemHandler() {
		if (level.isClientSide || itemHandler != null)
			return;
		if (beltLength == 0 || controller == null)
			return;
		if (!level.isLoaded(controller))
			return;
		BlockEntity be = level.getBlockEntity(controller);
		if (be == null || !(be instanceof MagmaBeltBlockEntity))
			return;
		MagmaBeltInventory inventory = ((MagmaBeltBlockEntity) be).getInventory();
		if (inventory == null)
			return;
		itemHandler = new MagmaItemHandlerBeltSegment(inventory, index);
		// The chain just became able to accept items. Nothing about the block state changed, so NeoForge
		// will not invalidate on its own — neighbours holding a BlockCapabilityCache (chutes, mechanical
		// arms) would keep the null they cached before the chain was wired up.
		invalidateCapabilities();
	}

	public IItemHandler getItemCapability(Direction side) {
		if (!MagmaBeltBlock.canTransportObjects(getBlockState()))
			return null;
		if (!isRemoved() && itemHandler == null)
			initializeItemHandler();
		return itemHandler;
	}

	/**
	 * Commands that replace a block ({@code /setblock}, {@code /fill}, {@code /clone}) and structure
	 * placement call this before the replacement so container contents vanish rather than pop out.
	 * Create's belt does the same; without it the transported items survive into the removal path.
	 */
	@Override
	public void clearContent() {
		if (inventory != null)
			inventory.getTransportedItems()
				.clear();
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
		invalidateItemHandler();
	}

	@Override
	public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		if (controller != null)
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		compound.putBoolean("IsController", isController());
		compound.putInt("Length", beltLength);
		compound.putInt("Index", index);
		NBTHelper.writeEnum(compound, "Casing", casing);
		compound.putBoolean("Covered", covered);

		if (isController())
			compound.put("Inventory", getInventory().write(registries));
		super.write(compound, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);

		if (compound.getBoolean("IsController"))
			controller = worldPosition;

		if (!wasMoved) {
			if (!isController())
				controller = NbtUtils.readBlockPos(compound, "Controller").orElse(worldPosition);
			trackerUpdateTag = compound;
			index = compound.getInt("Index");
			beltLength = compound.getInt("Length");
		}

		if (isController())
			getInventory().read(compound.getCompound("Inventory"), registries, clientPacket);

		CasingType casingBefore = casing;
		boolean coverBefore = covered;
		casing = NBTHelper.readEnum(compound, "Casing", CasingType.class);
		covered = compound.getBoolean("Covered");

		if (!clientPacket)
			return;

		if (casingBefore == casing && coverBefore == covered)
			return;
		if (!isVirtual())
			requestModelDataUpdate();
		if (hasLevel())
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
	}

	@Override
	public void clearKineticInformation() {
		super.clearKineticInformation();
		beltLength = 0;
		index = 0;
		controller = null;
		trackerUpdateTag = new CompoundTag();
		// Whatever cleared the chain deserves a fresh attempt on the next tick, not a leftover backoff.
		initRetryCooldown = 0;
	}

	public MagmaBeltBlockEntity getControllerBE() {
		if (controller == null || level == null)
			return null;
		if (!level.isLoaded(controller))
			return null;
		if (!SubLevelCompat.sameSpace(level, worldPosition, controller))
			return null;
		BlockEntity be = level.getBlockEntity(controller);
		if (be == null || !(be instanceof MagmaBeltBlockEntity))
			return null;
		return (MagmaBeltBlockEntity) be;
	}

	public void setController(BlockPos controller) {
		this.controller = controller;
	}

	public BlockPos getController() {
		return controller == null ? worldPosition : controller;
	}

	public boolean isController() {
		return controller != null && worldPosition.getX() == controller.getX()
			&& worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
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
		if (!MagmaBeltBlock.isMagmaBelt(getBlockState()))
			return false;
		return getBlockState().getValue(MagmaBeltBlock.PART) != MIDDLE;
	}

	protected boolean isLastBelt() {
		if (getSpeed() == 0)
			return false;

		Direction direction = getBeltFacing();
		if (getBlockState().getValue(MagmaBeltBlock.SLOPE) == BeltSlope.VERTICAL)
			return false;

		BeltPart part = getBlockState().getValue(MagmaBeltBlock.PART);
		if (part == MIDDLE)
			return false;

		boolean movingPositively = (getSpeed() > 0 == (direction.getAxisDirection()
			.getStep() == 1)) ^ direction.getAxis() == Axis.X;
		return part == BeltPart.START ^ movingPositively;
	}

	public Vec3i getMovementDirection(boolean firstHalf) {
		return this.getMovementDirection(firstHalf, false);
	}

	public Vec3i getBeltChainDirection() {
		return this.getMovementDirection(true, true);
	}

	protected Vec3i getMovementDirection(boolean firstHalf, boolean ignoreHalves) {
		if (getSpeed() == 0)
			return BlockPos.ZERO;

		final BlockState blockState = getBlockState();
		final Direction beltFacing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		final BeltSlope slope = blockState.getValue(MagmaBeltBlock.SLOPE);
		final BeltPart part = blockState.getValue(MagmaBeltBlock.PART);
		final Axis axis = beltFacing.getAxis();

		Direction movementFacing = Direction.get(axis == Axis.X ? NEGATIVE : POSITIVE, axis);
		boolean notHorizontal = blockState.getValue(MagmaBeltBlock.SLOPE) != HORIZONTAL;
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
		Axis axis = getBeltFacing().getAxis();
		return Direction.fromAxisAndDirection(axis, getBeltMovementSpeed() < 0 ^ axis == Axis.X ? NEGATIVE : POSITIVE);
	}

	protected Direction getBeltFacing() {
		return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	public MagmaBeltInventory getInventory() {
		if (!isController()) {
			MagmaBeltBlockEntity controllerBE = getControllerBE();
			if (controllerBE != null)
				return controllerBE.getInventory();
			return null;
		}
		if (inventory == null) {
			inventory = new MagmaBeltInventory(this);
		}
		return inventory;
	}

	private void applyToAllItems(float maxDistanceFromCenter,
								 Function<TransportedItemStack, TransportedResult> processFunction) {
		MagmaBeltBlockEntity controller = getControllerBE();
		if (controller == null)
			return;
		MagmaBeltInventory inventory = controller.getInventory();
		if (inventory != null)
			inventory.applyToEachWithin(index + .5f, maxDistanceFromCenter, processFunction);
	}

	private Vec3 getWorldPositionOf(TransportedItemStack transported) {
		MagmaBeltBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return Vec3.ZERO;
		return MagmaBeltHelper.getVectorForOffset(controllerBE, transported.beltPosition);
	}

	public void setCasingType(CasingType type) {
		if (casing == type)
			return;

		BlockState blockState = getBlockState();
		boolean shouldBlockHaveCasing = type != CasingType.NONE;

		if (level.isClientSide) {
			casing = type;
			level.setBlock(worldPosition, blockState.setValue(MagmaBeltBlock.CASING, shouldBlockHaveCasing), 0);
			requestModelDataUpdate();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 16);
			return;
		}

		if (casing != CasingType.NONE)
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, worldPosition,
				Block.getId(casing == CasingType.ANDESITE ? AllBlocks.ANDESITE_CASING.getDefaultState()
					: AllBlocks.BRASS_CASING.getDefaultState()));
		if (blockState.getValue(MagmaBeltBlock.CASING) != shouldBlockHaveCasing)
			KineticBlockEntity.switchToBlockState(level, worldPosition,
				blockState.setValue(MagmaBeltBlock.CASING, shouldBlockHaveCasing));
		casing = type;
		setChanged();
		sendData();
	}

	private boolean canInsertFrom(Direction side) {
		if (getSpeed() == 0)
			return false;
		BlockState state = getBlockState();
		if (state.hasProperty(MagmaBeltBlock.SLOPE) && (state.getValue(MagmaBeltBlock.SLOPE) == BeltSlope.SIDEWAYS
			|| state.getValue(MagmaBeltBlock.SLOPE) == BeltSlope.VERTICAL))
			return false;
		return getMovementFacing() != side.getOpposite();
	}

	private boolean isOccupied(Direction side) {
		MagmaBeltBlockEntity nextBeltController = getControllerBE();
		if (nextBeltController == null)
			return true;
		MagmaBeltInventory nextInventory = nextBeltController.getInventory();
		if (nextInventory == null)
			return true;
		if (getSpeed() == 0)
			return true;
		if (getMovementFacing() == side.getOpposite())
			return true;
		if (!nextInventory.canInsertAtFromSide(index, side))
			return true;
		return false;
	}

	private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
		MagmaBeltBlockEntity nextBeltController = getControllerBE();
		ItemStack inserted = transportedStack.stack;
		ItemStack empty = ItemStack.EMPTY;

		if (!MagmaBeltBlock.canTransportObjects(getBlockState()))
			return inserted;
		if (nextBeltController == null)
			return inserted;
		MagmaBeltInventory nextInventory = nextBeltController.getInventory();
		if (nextInventory == null)
			return inserted;

		BlockEntity teAbove = level.getBlockEntity(worldPosition.above());
		if (teAbove instanceof BrassTunnelBlockEntity tunnelBE) {
			if (tunnelBE.hasDistributionBehaviour()) {
				if (!tunnelBE.getStackToDistribute()
					.isEmpty())
					return inserted;
				if (!tunnelBE.testFlapFilter(side.getOpposite(), inserted))
					return inserted;
				if (!simulate) {
					MagmaBeltTunnelInteractionHandler.flapTunnel(nextInventory, index, side.getOpposite(), true);
					tunnelBE.setStackToDistribute(inserted, side.getOpposite());
				}
				return empty;
			}
		}

		if (isOccupied(side))
			return inserted;
		if (simulate)
			return empty;

		transportedStack = transportedStack.copy();
		transportedStack.beltPosition = index + .5f - Math.signum(getDirectionAwareBeltMovementSpeed()) / 16f;

		Direction movementFacing = getMovementFacing();
		if (!side.getAxis()
			.isVertical()) {
			if (movementFacing != side) {
				transportedStack.sideOffset = side.getAxisDirection()
					.getStep() * .675f;
				if (side.getAxis() == Axis.X)
					transportedStack.sideOffset *= -1;
			} else {
				// This creates a smoother transition from belt to belt
				float extraOffset = transportedStack.prevBeltPosition != 0
					&& MagmaBeltHelper.getSegmentBE(level, worldPosition.relative(movementFacing.getOpposite())) != null
						? .26f
						: 0;
				transportedStack.beltPosition =
					getDirectionAwareBeltMovementSpeed() > 0 ? index - extraOffset : index + 1 + extraOffset;
			}
		}

		transportedStack.prevSideOffset = transportedStack.sideOffset;
		transportedStack.insertedAt = index;
		transportedStack.insertedFrom = side;
		transportedStack.prevBeltPosition = transportedStack.beltPosition;

		MagmaBeltTunnelInteractionHandler.flapTunnel(nextInventory, index, side.getOpposite(), true);

		nextInventory.addItem(transportedStack);
		nextBeltController.setChanged();
		nextBeltController.sendData();
		return empty;
	}

	@Override
	public ModelData getModelData() {
		return ModelData.builder()
			.with(BeltModel.CASING_PROPERTY, casing)
			.with(BeltModel.COVER_PROPERTY, covered)
			.build();
	}

	@Override
	protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
		return state.hasProperty(MagmaBeltBlock.SLOPE) && (state.getValue(MagmaBeltBlock.SLOPE) == BeltSlope.UPWARD
			|| state.getValue(MagmaBeltBlock.SLOPE) == BeltSlope.DOWNWARD);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
									 boolean connectedViaAxes, boolean connectedViaCogs) {
		if (target instanceof MagmaBeltBlockEntity && !connectedViaAxes)
			return getController().equals(((MagmaBeltBlockEntity) target).getController()) ? 1 : 0;
		return 0;
	}

	public void invalidateItemHandler() {
		invalidateCapabilities();
		itemHandler = null;
		if (level != null)
			BeltTunnelCapabilityInvalidator.invalidate(level, worldPosition.above());
	}

	@Override
	public int createBiotech$getBeltLength() {
		return beltLength;
	}

	@Override
	public boolean createBiotech$hasPulley() {
		return hasPulley();
	}

	@Override
	public CasingType createBiotech$getCasingType() {
		return casing;
	}

	@Override
	public void createBiotech$setCasingType(CasingType casing) {
		setCasingType(casing);
	}

	@Override
	public BlockPos createBiotech$getBlockPos() {
		return worldPosition;
	}

	@Override
	public boolean createBiotech$isHorizontalItemPort() {
		return getBlockState().getValue(MagmaBeltBlock.SLOPE) == BeltSlope.HORIZONTAL
			&& MagmaBeltBlock.canTransportObjects(getBlockState());
	}

	@Override
	public boolean createBiotech$addressesItemPort(Direction side) {
		return createBiotech$isHorizontalItemPort() && side.getAxis() == getBeltFacing().getAxis();
	}

	@Override
	public boolean createBiotech$canInsertIntoItemPort(Direction side) {
		return canInsertFrom(side);
	}

	@Override
	public ItemStack createBiotech$insertIntoItemPort(ItemStack stack, Direction side, boolean simulate) {
		return tryInsertingFromSide(new TransportedItemStack(stack), side, simulate);
	}

	@Override
	public IItemHandler createBiotech$getItemHandler() {
		return getItemCapability(Direction.UP);
	}

	@Override
	public Direction createBiotech$getMovementFacing() {
		return getMovementFacing();
	}

	@Override
	public float createBiotech$getSpeed() {
		return getSpeed();
	}

	@Override
	public float createBiotech$getDirectionAwareSpeed() {
		return getDirectionAwareBeltMovementSpeed();
	}

	@Override
	public Vec3 createBiotech$getEjectionPosition() {
		MagmaBeltBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return Vec3.atCenterOf(worldPosition);
		int additionalOffset = getDirectionAwareBeltMovementSpeed() > 0 ? 1 : 0;
		return MagmaBeltHelper.getVectorForOffset(controllerBE, index + additionalOffset);
	}

	public boolean shouldRenderNormally() {
		if (level == null)
			return isController();
		BlockState state = getBlockState();
		return state != null && state.hasProperty(MagmaBeltBlock.PART) && state.getValue(MagmaBeltBlock.PART) == BeltPart.START;
	}

	public void setCovered(boolean blockCoveringBelt) {
		if (blockCoveringBelt == covered)
			return;
		covered = blockCoveringBelt;
		notifyUpdate();
	}
}
