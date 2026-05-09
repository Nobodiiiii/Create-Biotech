package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity {

	private static final int MIN_SIZE = 3;
	private static final int MAX_SIZE = 5;
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String MARKED_CREEPER_TAG = "CreeperBlastChamberMarked";
	private static final String CONTROLLER_POS_TAG = "CreeperBlastChamberControllerPos";
	private static final String PACKAGER_POS_TAG = "CreeperBlastChamberPackagerPos";
	private static final String PENDING_UNPACKS_TAG = "PendingUnpacks";
	private static final String PENDING_PACKAGINGS_TAG = "PendingPackagings";
	private static final String PENDING_APPEARANCES_TAG = "PendingAppearances";
	private static final String READY_OUTPUTS_TAG = "ReadyOutputs";
	private static final int READY_OUTPUT_TIMEOUT = 20 * 5;
	private static final int CREEPER_ENTRY_ANIMATION_TICKS = 10;

	public LerpedFloat gauge = LerpedFloat.linear();
	public LerpedFloat displayGauge = LerpedFloat.linear();
	private boolean structureValid;
	private int structureSize;
	private BlockPos structureOrigin;
	private BlockPos bottomCenter;
	private int recheckTimer;
	private final List<PendingUnpack> pendingUnpacks = new ArrayList<>();
	private final List<PendingAppearance> pendingAppearances = new ArrayList<>();
	private final List<PendingPackaging> pendingPackagings = new ArrayList<>();
	private final List<ReadyOutput> readyOutputs = new ArrayList<>();
	private final ChamberInputHandler inputHandler = new ChamberInputHandler();
	private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inputHandler);

	public CreeperBlastChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(), pos, state);
		gauge.startWithValue(0);
		displayGauge.startWithValue(0);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CreeperBlastChamberBlockEntity be) {
		if (level.isClientSide) {
			float target = be.structureValid ? (float) be.structureSize / MAX_SIZE : 0f;
			be.gauge.chase(target, 0.125f, Chaser.EXP);
			be.gauge.tickChaser();
			float displayTarget = be.structureValid ? (be.structureSize - MIN_SIZE + 1f) / (MAX_SIZE - MIN_SIZE + 1f) : 0f;
			be.displayGauge.chase(displayTarget, 0.125f, Chaser.EXP);
			be.displayGauge.tickChaser();
			be.tickClientAnimations();
			return;
		}

		be.tickPendingUnpacks();
		be.tickPendingAppearances();
		be.tickPendingPackagings();
		be.tickReadyOutputs();

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = 20;

		be.tryDetectStructure();
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		itemCapability.invalidate();
	}

	private void tryDetectStructure() {
		Level level = getLevel();
		if (level == null)
			return;
		BlockPos pos = getBlockPos();

		for (int s = MIN_SIZE; s <= MAX_SIZE; s++) {
			BlockPos origin = findOrigin(level, pos, s);
			if (origin != null) {
				boolean wasValid = structureValid;
				int oldSize = structureSize;
				setStructure(level, true, s, origin);
				if (!wasValid || oldSize != s)
					onStructureFormed(level, s, origin);
				return;
			}
		}
		boolean wasValid = structureValid;
		int oldSize = structureSize;
		BlockPos oldOrigin = structureOrigin;
		setStructure(level, false, 0, null);
		if (wasValid)
			onStructureBroken(level, oldSize, oldOrigin);
	}

	@Nullable
	private static BlockPos findOrigin(Level level, BlockPos controllerPos, int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				BlockPos origin = controllerPos.offset(-dx, 0, -dz);
				if (verifyStructure(level, origin, size))
					return origin;
			}
		}
		return null;
	}

	private static boolean verifyStructure(Level level, BlockPos origin, int size) {
		int innerMin = 1;
		int innerMax = size - 2;
		BlockState centerPressState = level.getBlockState(origin.offset(size / 2, 3, size / 2));

		if (!AllBlocks.MECHANICAL_PRESS.has(centerPressState))
			return false;
		Axis pressShaftAxis = getPressShaftAxis(centerPressState);

		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					BlockPos pos = origin.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					boolean isInnerX = (x >= innerMin && x <= innerMax);
					boolean isInnerZ = (z >= innerMin && z <= innerMax);
					boolean isCenter = isInnerX && isInnerZ;

					if (isCenter && y == 3) {
						if (!isMechanicalPressOnAxis(state, pressShaftAxis))
							return false;
					} else if (isCenter && (y == 1 || y == 2)) {
						if (!state.isAir())
							return false;
					} else if (isCenter && y == 0) {
						if (!AllBlocks.PACKAGER.has(state))
							return false;
					} else {
						boolean isController = state.getBlock() instanceof CreeperBlastChamberBlock;
						boolean isCasing = state.is(CBBlocks.EXPLOSION_PROOF_CASING.get());
						boolean isChainDrive = isBlastProofChainDriveOnAxis(state, pressShaftAxis);
						boolean isVerticalEdge = !isInnerX && !isInnerZ;
						boolean isTopOrBottom = (y == 0 || y == 3);

						if (isTopOrBottom || isVerticalEdge) {
							if (!isController && !isCasing && !isChainDrive)
								return false;
						} else {
							boolean isGlass = state.is(CBBlocks.BLAST_PROOF_GLASS.get())
								|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get());
							if (!isController && !isCasing && !isGlass)
								return false;
						}
					}
				}
			}
		}
		return true;
	}

	private static Axis getPressShaftAxis(BlockState state) {
		return state.getValue(BlockStateProperties.HORIZONTAL_FACING)
			.getAxis();
	}

	private static boolean isMechanicalPressOnAxis(BlockState state, Axis axis) {
		return AllBlocks.MECHANICAL_PRESS.has(state) && getPressShaftAxis(state) == axis;
	}

	private static boolean isBlastProofChainDriveOnAxis(BlockState state, Axis axis) {
		return state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())
			&& state.hasProperty(BlockStateProperties.AXIS)
			&& state.getValue(BlockStateProperties.AXIS) == axis;
	}

	private void setStructure(Level level, boolean valid, int size, @Nullable BlockPos origin) {
		structureValid = valid;
		structureSize = size;
		structureOrigin = origin;
		bottomCenter = valid && origin != null
			? origin.offset(size / 2, 0, size / 2)
			: null;
		notifyUpdate();
	}

	private void onStructureFormed(Level level, int size, BlockPos origin) {
		BlockPos pressPos = origin.offset(size / 2, 3, size / 2);
		BlockState pressState = level.getBlockState(pressPos);
		Direction shaftFacing = pressState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		setPackagerOutputsDown(level, origin, size);
		// Chain drives sit on the two side edges that line up with the press shaft.
		Axis axis = getPressShaftAxis(pressState);
		Direction alongEdge = shaftFacing.getClockWise();
		Axis alongEdgeAxis = alongEdge.getAxis();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState chainState = CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get().defaultBlockState()
			.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
		if ((size & 1) == 0) {
			int topY = 3;
			if (axis == Axis.X) {
				for (int z = 1; z < size - 1; z++) {
					BlockPos westEdgePos = origin.offset(0, topY, z);
					BlockPos eastEdgePos = origin.offset(size - 1, topY, z);
					if (!level.getBlockState(westEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
						level.setBlock(westEdgePos, chainState, 3);
					if (!level.getBlockState(eastEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
						level.setBlock(eastEdgePos, chainState, 3);
				}
				for (int z = 1; z < size - 1; z++) {
					updateChainDriveState(level, origin.offset(0, topY, z), axis, alongEdgeAxis);
					updateChainDriveState(level, origin.offset(size - 1, topY, z), axis, alongEdgeAxis);
				}
				return;
			}

			for (int x = 1; x < size - 1; x++) {
				BlockPos northEdgePos = origin.offset(x, topY, 0);
				BlockPos southEdgePos = origin.offset(x, topY, size - 1);
				if (!level.getBlockState(northEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
					level.setBlock(northEdgePos, chainState, 3);
				if (!level.getBlockState(southEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
					level.setBlock(southEdgePos, chainState, 3);
			}

			for (int x = 1; x < size - 1; x++) {
				updateChainDriveState(level, origin.offset(x, topY, 0), axis, alongEdgeAxis);
				updateChainDriveState(level, origin.offset(x, topY, size - 1), axis, alongEdgeAxis);
			}
			return;
		}

		int perSide = size - 2;
		// Edge 1: the edge in the shaft direction from press
		// Edge 2: the edge in the opposite shaft direction from press
		Direction towardEdge1 = shaftFacing;
		Direction towardEdge2 = shaftFacing.getOpposite();
		int distFromPressToEdge = size / 2;
		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			if (!level.getBlockState(edge1Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge1Pos, chainState, 3);
			if (!level.getBlockState(edge2Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge2Pos, chainState, 3);
		}

		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			updateChainDriveState(level, edge1Pos, axis, alongEdgeAxis);
			updateChainDriveState(level, edge2Pos, axis, alongEdgeAxis);
		}
	}

	private void onStructureBroken(Level level, int size, BlockPos origin) {
		int innerMin = 1;
		int innerMax = size - 2;
		int topY = 3;

		for (int i = innerMin; i <= innerMax; i++) {
			revertToCasing(level, origin.offset(i, topY, 0));
			revertToCasing(level, origin.offset(i, topY, size - 1));
			revertToCasing(level, origin.offset(0, topY, i));
			revertToCasing(level, origin.offset(size - 1, topY, i));
		}

		cancelPendingUnpacks();
	}

	private void revertToCasing(Level level, BlockPos pos) {
		if (level.getBlockState(pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			level.setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
	}

	private void updateChainDriveState(Level level, BlockPos pos, Axis axis, Axis alongEdgeAxis) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			return;
		ChainDriveBlock chainDrive = (ChainDriveBlock) CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState updated = state.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
		for (Direction facing : Iterate.directions) {
			if (facing.getAxis() == axis)
				continue;
			BlockPos offset = pos.relative(facing);
			updated = chainDrive.updateShape(updated, facing, level.getBlockState(offset), level, pos, offset);
		}
		if (updated != state)
			level.setBlock(pos, updated, 3);
	}

	private void setPackagerOutputsDown(Level level, BlockPos origin, int size) {
		for (int x = 1; x < size - 1; x++) {
			for (int z = 1; z < size - 1; z++) {
				BlockPos packagerPos = origin.offset(x, 0, z);
				BlockState packagerState = level.getBlockState(packagerPos);
				if (!AllBlocks.PACKAGER.has(packagerState) || !packagerState.hasProperty(PackagerBlock.FACING)
					|| packagerState.getValue(PackagerBlock.FACING) == Direction.UP)
					continue;
				level.setBlock(packagerPos, packagerState.setValue(PackagerBlock.FACING, Direction.UP), 3);
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("StructureValid", structureValid);
		tag.putInt("StructureSize", structureSize);
		ListTag pendingList = new ListTag();
		for (PendingUnpack pending : pendingUnpacks)
			pendingList.add(pending.write());
		tag.put(PENDING_UNPACKS_TAG, pendingList);
		ListTag pendingPackagingList = new ListTag();
		for (PendingPackaging pending : pendingPackagings)
			pendingPackagingList.add(pending.write());
		tag.put(PENDING_PACKAGINGS_TAG, pendingPackagingList);
		ListTag pendingAppearanceList = new ListTag();
		for (PendingAppearance pending : pendingAppearances)
			pendingAppearanceList.add(pending.write());
		tag.put(PENDING_APPEARANCES_TAG, pendingAppearanceList);
		ListTag readyOutputList = new ListTag();
		for (ReadyOutput readyOutput : readyOutputs)
			readyOutputList.add(readyOutput.write());
		tag.put(READY_OUTPUTS_TAG, readyOutputList);
		if (structureOrigin != null) {
			tag.putInt("OriginX", structureOrigin.getX());
			tag.putInt("OriginY", structureOrigin.getY());
			tag.putInt("OriginZ", structureOrigin.getZ());
		}
		if (bottomCenter != null) {
			tag.putInt("BottomX", bottomCenter.getX());
			tag.putInt("BottomY", bottomCenter.getY());
			tag.putInt("BottomZ", bottomCenter.getZ());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		structureValid = tag.getBoolean("StructureValid");
		structureSize = tag.getInt("StructureSize");
		pendingUnpacks.clear();
		for (Tag pendingTag : tag.getList(PENDING_UNPACKS_TAG, Tag.TAG_COMPOUND))
			pendingUnpacks.add(PendingUnpack.read((CompoundTag) pendingTag));
		pendingPackagings.clear();
		for (Tag pendingTag : tag.getList(PENDING_PACKAGINGS_TAG, Tag.TAG_COMPOUND))
			pendingPackagings.add(PendingPackaging.read((CompoundTag) pendingTag));
		pendingAppearances.clear();
		for (Tag pendingTag : tag.getList(PENDING_APPEARANCES_TAG, Tag.TAG_COMPOUND))
			pendingAppearances.add(PendingAppearance.read((CompoundTag) pendingTag));
		readyOutputs.clear();
		for (Tag readyOutputTag : tag.getList(READY_OUTPUTS_TAG, Tag.TAG_COMPOUND))
			readyOutputs.add(ReadyOutput.read((CompoundTag) readyOutputTag));
		if (tag.contains("OriginX")) {
			structureOrigin = new BlockPos(
				tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
		} else {
			structureOrigin = null;
		}
		if (tag.contains("BottomX")) {
			bottomCenter = new BlockPos(
				tag.getInt("BottomX"), tag.getInt("BottomY"), tag.getInt("BottomZ"));
		} else {
			bottomCenter = null;
		}
	}

	public void forceStructureCheck() {
		recheckTimer = 0;
		tryDetectStructure();
	}

	public boolean isStructureValid() {
		return structureValid;
	}

	public int getStructureSize() {
		return structureSize;
	}

	@Nullable
	public BlockPos getStructureOrigin() {
		return structureOrigin;
	}

	@Nullable
	public BlockPos getBottomCenter() {
		return bottomCenter;
	}

	private void tickPendingUnpacks() {
		Level level = getLevel();
		if (level == null || pendingUnpacks.isEmpty())
			return;

		if (!structureValid || structureOrigin == null || !verifyStructure(level, structureOrigin, structureSize)) {
			if (structureValid && structureOrigin != null) {
				int oldSize = structureSize;
				BlockPos oldOrigin = structureOrigin;
				setStructure(level, false, 0, null);
				onStructureBroken(level, oldSize, oldOrigin);
			}
			return;
		}

		boolean changed = false;
		Iterator<PendingUnpack> iterator = pendingUnpacks.iterator();
		while (iterator.hasNext()) {
			PendingUnpack pending = iterator.next();
			PackagerBlockEntity packager = getPackager(pending.packagerPos);
			if (!isPackagerPartOfStructure(pending.packagerPos) || packager == null) {
				dropBox(pending);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (packager.animationTicks > 0)
				continue;

			if (!completePendingUnpack(pending))
				dropBox(pending);
			iterator.remove();
			changed = true;
		}

		if (changed)
			setChanged();
	}

	private void tickPendingAppearances() {
		Level level = getLevel();
		if (level == null || pendingAppearances.isEmpty())
			return;

		boolean changed = false;
		Iterator<PendingAppearance> iterator = pendingAppearances.iterator();
		while (iterator.hasNext()) {
			PendingAppearance pending = iterator.next();
			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (pending.ticksRemaining > 0)
				continue;

			Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
			if (creeper != null)
				creeper.setInvisible(false);
			iterator.remove();
			changed = true;
		}

		if (changed) {
			setChanged();
			notifyUpdate();
		}
	}

	private void tickPendingPackagings() {
		Level level = getLevel();
		if (level == null || pendingPackagings.isEmpty())
			return;

		boolean changed = false;
		Iterator<PendingPackaging> iterator = pendingPackagings.iterator();
		while (iterator.hasNext()) {
			PendingPackaging pending = iterator.next();
			PackagerBlockEntity packager = getPackager(pending.packagerPos);
			if (packager == null) {
				restorePendingPackaging(pending, true);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (packager.animationTicks > 0)
				continue;

			packager.heldBox = ItemStack.EMPTY;
			packager.animationTicks = 0;
			packager.notifyUpdate();
			packager.setChanged();
			Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
			if (creeper != null)
				creeper.discard();
			readyOutputs.add(new ReadyOutput(pending.packagerPos, pending.boxStack.copy(), READY_OUTPUT_TIMEOUT));
			iterator.remove();
			changed = true;
		}

		if (changed) {
			setChanged();
			notifyUpdate();
		}
	}

	private void tickReadyOutputs() {
		Level level = getLevel();
		if (level == null || readyOutputs.isEmpty() || !structureValid)
			return;

		boolean changed = false;
		Iterator<ReadyOutput> iterator = readyOutputs.iterator();
		while (iterator.hasNext()) {
			ReadyOutput readyOutput = iterator.next();
			if (readyOutput.ticksRemaining > 0)
				readyOutput.ticksRemaining--;
			if (readyOutput.ticksRemaining > 0)
				continue;

			BlockPos targetPackager = findIdlePackager();
			if (targetPackager != null && queueUnpack(targetPackager, readyOutput.boxStack, false)) {
				iterator.remove();
				changed = true;
			}
		}

		if (changed)
			setChanged();
	}

	private void tickClientAnimations() {
		tickClientAnimationList(pendingAppearances);
		tickClientAnimationList(pendingPackagings);
	}

	private static <T extends TimedAnimation> void tickClientAnimationList(List<T> animations) {
		Iterator<T> iterator = animations.iterator();
		while (iterator.hasNext()) {
			T animation = iterator.next();
			if (animation.ticksRemaining > 0)
				animation.ticksRemaining--;
			if (animation.ticksRemaining <= 0)
				iterator.remove();
		}
	}

	@Nullable
	private BlockPos findAvailablePackager() {
		if (!hasAvailableCreeperSlot())
			return null;
		return findIdlePackager();
	}

	@Nullable
	private BlockPos findIdlePackager() {
		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerIdle(packagerPos))
				return packagerPos;
		}
		return null;
	}

	private boolean hasAvailableCreeperSlot() {
		return getOccupiedCreeperSlots() < getPackagerPositions().size();
	}

	private int getOccupiedCreeperSlots() {
		return pendingUnpacks.size() + readyOutputs.size() + countMarkedCreepers();
	}

	private int countMarkedCreepers() {
		Level level = getLevel();
		if (level == null)
			return 0;
		return level.getEntitiesOfClass(Creeper.class, getMarkedCreeperSearchBounds(),
			creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, null))
			.size();
	}

	private boolean isPackagerIdle(BlockPos packagerPos) {
		if (isPackagerReserved(packagerPos) || isPackagerPackaging(packagerPos)
			|| hasMarkedCreeperAtPackager(packagerPos))
			return false;

		PackagerBlockEntity packager = getPackager(packagerPos);
		return packager != null
			&& packager.animationTicks <= 0
			&& packager.heldBox.isEmpty()
			&& packager.previouslyUnwrapped.isEmpty()
			&& packager.queuedExitingPackages.isEmpty();
	}

	private boolean queueUnpack(BlockPos packagerPos, ItemStack boxStack, boolean returnBox) {
		PackagerBlockEntity packager = getPackager(packagerPos);
		if (packager == null || !isPackagerIdle(packagerPos))
			return false;

		packager.previouslyUnwrapped = boxStack.copy();
		packager.animationInward = true;
		packager.animationTicks = PackagerBlockEntity.CYCLE;
		packager.notifyUpdate();
		packager.setChanged();

		pendingUnpacks.add(new PendingUnpack(packagerPos, boxStack.copy(), PackagerBlockEntity.CYCLE, returnBox));
		setChanged();
		return true;
	}

	private InsertResult tryInsertLargeCreeperBox(ItemStack stack, boolean simulate, boolean returnBox) {
		if (!isValidLargeCreeperBox(stack))
			return new InsertResult(false, stack);

		Level level = getLevel();
		if (level == null || level.isClientSide)
			return new InsertResult(false, stack);

		tryDetectStructure();
		if (!structureValid)
			return new InsertResult(false, stack);

		BlockPos targetPackager = findAvailablePackager();
		if (targetPackager == null)
			return new InsertResult(false, stack);

		if (!simulate) {
			ItemStack boxToInsert = stack.copy();
			boxToInsert.setCount(1);
			if (!queueUnpack(targetPackager, boxToInsert, returnBox))
				return new InsertResult(false, stack);
		}

		if (stack.getCount() <= 1)
			return new InsertResult(true, ItemStack.EMPTY);

		ItemStack remainder = stack.copy();
		remainder.shrink(1);
		return new InsertResult(true, remainder);
	}

	private boolean isValidLargeCreeperBox(ItemStack stack) {
		return stack.is(CBItems.LARGE_CARDBOARD_BOX.get())
			&& CapturedEntityBoxHelper.containsEntityType(stack, EntityType.CREEPER);
	}

	@Nullable
	private ItemStack getAvailableOutputPreview() {
		if (!readyOutputs.isEmpty())
			return readyOutputs.get(0).boxStack.copy();

		MarkedCreeperTarget target = findMarkedCreeperForOutput();
		return target == null ? null : createBoxedCreeper(target.creeper);
	}

	@Nullable
	private ItemStack extractReadyOutput(boolean simulate) {
		if (readyOutputs.isEmpty())
			return null;

		ItemStack output = readyOutputs.get(0).boxStack.copy();
		if (!simulate) {
			readyOutputs.remove(0);
			setChanged();
		}
		return output;
	}

	private boolean packageMarkedCreeperForOutput() {
		MarkedCreeperTarget target = findMarkedCreeperForOutput();
		if (target == null)
			return false;

		ItemStack output = createBoxedCreeper(target.creeper);
		if (output == null)
			return false;

		PackagerBlockEntity packager = getPackager(target.packagerPos);
		if (packager == null || packager.animationTicks > 0 || !packager.heldBox.isEmpty())
			return false;

		target.creeper.setInvisible(true);
		target.creeper.setDeltaMovement(Vec3.ZERO);
		target.creeper.fallDistance = 0;
		packager.heldBox = output.copy();
		packager.animationInward = false;
		packager.animationTicks = PackagerBlockEntity.CYCLE;
		packager.notifyUpdate();
		packager.setChanged();
		pendingPackagings.add(new PendingPackaging(target.packagerPos, output.copy(), target.creeper.getUUID(),
			PackagerBlockEntity.CYCLE));
		setChanged();
		notifyUpdate();
		return true;
	}

	@Nullable
	private MarkedCreeperTarget findMarkedCreeperForOutput() {
		Level level = getLevel();
		if (level == null || !structureValid)
			return null;

		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerReserved(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			PackagerBlockEntity packager = getPackager(packagerPos);
			if (packager == null || packager.animationTicks > 0 || !packager.heldBox.isEmpty()
				|| !packager.previouslyUnwrapped.isEmpty() || !packager.queuedExitingPackages.isEmpty())
				continue;

			List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getPackagerCreeperSearchBounds(packagerPos),
				creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, packagerPos));
			if (!creepers.isEmpty())
				return new MarkedCreeperTarget(packagerPos, creepers.get(0));
		}

		return null;
	}

	private AABB getPackagerCreeperSearchBounds(BlockPos packagerPos) {
		return new AABB(
			packagerPos.getX() + 0.1d,
			packagerPos.getY() + 0.75d,
			packagerPos.getZ() + 0.1d,
			packagerPos.getX() + 0.9d,
			packagerPos.getY() + 2.25d,
			packagerPos.getZ() + 0.9d);
	}

	private boolean isPackagerPackaging(BlockPos packagerPos) {
		for (PendingPackaging pending : pendingPackagings) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	private boolean hasReadyOutput(BlockPos packagerPos) {
		for (ReadyOutput readyOutput : readyOutputs) {
			if (readyOutput.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	@Nullable
	private ItemStack createBoxedCreeper(Creeper creeper) {
		ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
		if (!CapturedEntityBoxHelper.captureEntity(box, creeper))
			return null;

		CompoundTag boxTag = box.getTag();
		if (boxTag == null || !boxTag.contains("CapturedEntity", Tag.TAG_COMPOUND))
			return box;

		CompoundTag entityData = boxTag.getCompound("CapturedEntity");
		entityData.remove("NoAI");
		entityData.remove("PersistenceRequired");
		if (entityData.contains("ForgeData", Tag.TAG_COMPOUND)) {
			CompoundTag forgeData = entityData.getCompound("ForgeData");
			forgeData.remove(DATA_ROOT);
			if (forgeData.isEmpty())
				entityData.remove("ForgeData");
		}
		return box;
	}

	private boolean completePendingUnpack(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || hasMarkedCreeperAtPackager(pending.packagerPos))
			return false;

		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(pending.boxStack, level);
		if (!(entity instanceof Creeper creeper))
			return false;

		creeper.stopRiding();
		creeper.moveTo(
			pending.packagerPos.getX() + 0.5d,
			pending.packagerPos.getY() + 1d,
			pending.packagerPos.getZ() + 0.5d,
			creeper.getYRot(),
			creeper.getXRot());
		creeper.setNoAi(true);
		creeper.setPersistenceRequired();
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		creeper.setInvisible(true);
		markCreeper(creeper, pending.packagerPos);
		if (!level.addFreshEntity(creeper))
			return false;
		pendingAppearances.add(new PendingAppearance(pending.packagerPos, creeper.getUUID(), CREEPER_ENTRY_ANIMATION_TICKS));
		notifyUpdate();

		return true;
	}

	private void cancelPendingUnpacks() {
		if (pendingUnpacks.isEmpty())
			return;

		for (PendingUnpack pending : pendingUnpacks)
			dropBox(pending);
		pendingUnpacks.clear();
		cancelPendingPackagings();
		setChanged();
	}

	private void cancelPendingPackagings() {
		for (PendingPackaging pending : pendingPackagings) {
			PackagerBlockEntity packager = getPackager(pending.packagerPos);
			if (packager != null) {
				packager.heldBox = ItemStack.EMPTY;
				packager.animationTicks = 0;
				packager.notifyUpdate();
				packager.setChanged();
			}
			restorePendingPackaging(pending, true);
		}
		pendingPackagings.clear();
		notifyUpdate();
	}

	private void dropBox(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || pending.boxStack.isEmpty() || !pending.returnBox)
			return;
		Block.popResource(level, pending.packagerPos.above(), pending.boxStack.copy());
	}

	private void dropPackagedBox(ItemStack stack, BlockPos pos) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return;
		Block.popResource(level, pos.above(), stack.copy());
	}

	private void restorePendingPackaging(PendingPackaging pending, boolean dropIfMissing) {
		Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
		if (creeper != null) {
			creeper.setInvisible(false);
			creeper.setDeltaMovement(Vec3.ZERO);
			creeper.fallDistance = 0;
			return;
		}
		if (dropIfMissing)
			dropPackagedBox(pending.boxStack, pending.packagerPos);
	}

	private List<BlockPos> getPackagerPositions() {
		List<BlockPos> packagers = new ArrayList<>();
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null)
			return packagers;

		for (int x = 1; x < structureSize - 1; x++) {
			for (int z = 1; z < structureSize - 1; z++) {
				BlockPos packagerPos = structureOrigin.offset(x, 0, z);
				if (AllBlocks.PACKAGER.has(level.getBlockState(packagerPos)))
					packagers.add(packagerPos);
			}
		}
		return packagers;
	}

	private boolean isPackagerPartOfStructure(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null || packagerPos.getY() != structureOrigin.getY())
			return false;

		int x = packagerPos.getX() - structureOrigin.getX();
		int z = packagerPos.getZ() - structureOrigin.getZ();
		return x >= 1 && x < structureSize - 1
			&& z >= 1 && z < structureSize - 1
			&& AllBlocks.PACKAGER.has(level.getBlockState(packagerPos));
	}

	private boolean isPackagerReserved(BlockPos packagerPos) {
		for (PendingUnpack pending : pendingUnpacks) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	private boolean hasMarkedCreeperAtPackager(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null)
			return false;

		return !level.getEntitiesOfClass(Creeper.class, getMarkedCreeperSearchBounds(),
			creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, packagerPos))
			.isEmpty();
	}

	private AABB getMarkedCreeperSearchBounds() {
		if (structureOrigin == null)
			return new AABB(getBlockPos()).inflate(32);
		return new AABB(structureOrigin, structureOrigin.offset(structureSize, 4, structureSize)).inflate(32);
	}

	private boolean isMarkedCreeperForThisChamber(Creeper creeper, @Nullable BlockPos packagerPos) {
		CompoundTag data = getExistingCreateBiotechData(creeper);
		if (data == null || !data.getBoolean(MARKED_CREEPER_TAG))
			return false;
		if (data.getLong(CONTROLLER_POS_TAG) != getBlockPos().asLong())
			return false;
		return packagerPos == null || data.getLong(PACKAGER_POS_TAG) == packagerPos.asLong();
	}

	private void markCreeper(Creeper creeper, BlockPos packagerPos) {
		CompoundTag data = getCreateBiotechData(creeper);
		data.putBoolean(MARKED_CREEPER_TAG, true);
		data.putLong(CONTROLLER_POS_TAG, getBlockPos().asLong());
		data.putLong(PACKAGER_POS_TAG, packagerPos.asLong());
	}

	@Nullable
	private PackagerBlockEntity getPackager(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null || !AllBlocks.PACKAGER.has(level.getBlockState(packagerPos)))
			return null;
		BlockEntity blockEntity = level.getBlockEntity(packagerPos);
		return blockEntity instanceof PackagerBlockEntity packager ? packager : null;
	}

	@Nullable
	Creeper getAnimatedCreeper(UUID creeperUuid, BlockPos packagerPos) {
		return findMarkedCreeperByUuid(creeperUuid, packagerPos);
	}

	private Creeper findMarkedCreeperByUuid(UUID creeperUuid, BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null)
			return null;

		List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getPackagerCreeperSearchBounds(packagerPos),
			creeper -> creeper.isAlive() && creeperUuid.equals(creeper.getUUID())
				&& isMarkedCreeperForThisChamber(creeper, packagerPos));
		return creepers.isEmpty() ? null : creepers.get(0);
	}

	List<RenderCreeperAnimation> getRenderAnimations() {
		List<RenderCreeperAnimation> animations = new ArrayList<>(pendingAppearances.size() + pendingPackagings.size());
		for (PendingAppearance pending : pendingAppearances)
			animations.add(new RenderCreeperAnimation(pending.packagerPos, pending.creeperUuid, pending.ticksRemaining,
				pending.totalTicks, false));
		for (PendingPackaging pending : pendingPackagings)
			animations.add(new RenderCreeperAnimation(pending.packagerPos, pending.creeperUuid, pending.ticksRemaining,
				pending.totalTicks, true));
		return animations;
	}

	private static CompoundTag getCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}

	private static CompoundTag getExistingCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		return persistentData.contains(DATA_ROOT) ? persistentData.getCompound(DATA_ROOT) : null;
	}

	private static class PendingUnpack {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private int ticksRemaining;
		private final boolean returnBox;

		private PendingUnpack(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining, boolean returnBox) {
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.ticksRemaining = ticksRemaining;
			this.returnBox = returnBox;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putInt("TicksRemaining", ticksRemaining);
			tag.putBoolean("ReturnBox", returnBox);
			return tag;
		}

		private static PendingUnpack read(CompoundTag tag) {
			return new PendingUnpack(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
				tag.getInt("TicksRemaining"),
				tag.getBoolean("ReturnBox"));
		}
	}

	private abstract static class TimedAnimation {
		protected int ticksRemaining;
		protected final int totalTicks;

		private TimedAnimation(int ticksRemaining, int totalTicks) {
			this.ticksRemaining = ticksRemaining;
			this.totalTicks = totalTicks;
		}
	}

	private static class PendingAppearance extends TimedAnimation {
		private final BlockPos packagerPos;
		private final UUID creeperUuid;

		private PendingAppearance(BlockPos packagerPos, UUID creeperUuid, int ticksRemaining) {
			super(ticksRemaining, CREEPER_ENTRY_ANIMATION_TICKS);
			this.packagerPos = packagerPos;
			this.creeperUuid = creeperUuid;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.putUUID("CreeperUuid", creeperUuid);
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static PendingAppearance read(CompoundTag tag) {
			return new PendingAppearance(
				BlockPos.of(tag.getLong("PackagerPos")),
				tag.getUUID("CreeperUuid"),
				tag.getInt("TicksRemaining"));
		}
	}

	private static class PendingPackaging extends TimedAnimation {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private final UUID creeperUuid;

		private PendingPackaging(BlockPos packagerPos, ItemStack boxStack, UUID creeperUuid, int ticksRemaining) {
			super(ticksRemaining, PackagerBlockEntity.CYCLE);
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.creeperUuid = creeperUuid;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putUUID("CreeperUuid", creeperUuid);
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static PendingPackaging read(CompoundTag tag) {
			return new PendingPackaging(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
				tag.getUUID("CreeperUuid"),
				tag.getInt("TicksRemaining"));
		}
	}

	private static class ReadyOutput {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private int ticksRemaining;

		private ReadyOutput(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining) {
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.ticksRemaining = ticksRemaining;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static ReadyOutput read(CompoundTag tag) {
			return new ReadyOutput(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
				tag.getInt("TicksRemaining"));
		}
	}

	private static class InsertResult {
		private final boolean accepted;
		private final ItemStack remainder;

		private InsertResult(boolean accepted, ItemStack remainder) {
			this.accepted = accepted;
			this.remainder = remainder;
		}

		private boolean accepted() {
			return accepted;
		}

		private ItemStack remainder() {
			return remainder;
		}
	}

	private class ChamberInputHandler implements IItemHandler {
		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			validateSlot(slot);
			ItemStack preview = getAvailableOutputPreview();
			return preview == null ? ItemStack.EMPTY : preview;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			validateSlot(slot);
			if (stack.isEmpty())
				return ItemStack.EMPTY;

			InsertResult result = tryInsertLargeCreeperBox(stack, simulate, true);
			return result.accepted() ? result.remainder() : stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			validateSlot(slot);
			if (amount <= 0)
				return ItemStack.EMPTY;

			ItemStack extracted = extractReadyOutput(simulate);
			if (extracted != null)
				return extracted;

			if (simulate) {
				ItemStack preview = getAvailableOutputPreview();
				return preview == null ? ItemStack.EMPTY : preview;
			}

			packageMarkedCreeperForOutput();
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			validateSlot(slot);
			return 1;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			validateSlot(slot);
			return isValidLargeCreeperBox(stack);
		}

		private void validateSlot(int slot) {
			if (slot != 0)
				throw new RuntimeException("Slot " + slot + " not in valid range - [0,1)");
		}
	}

	record RenderCreeperAnimation(BlockPos packagerPos, UUID creeperUuid, int ticksRemaining, int totalTicks,
		boolean exiting) {}

	private record MarkedCreeperTarget(BlockPos packagerPos, Creeper creeper) {}
}
