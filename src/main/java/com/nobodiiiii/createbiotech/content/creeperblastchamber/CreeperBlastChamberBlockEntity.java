package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity {

	private static final int MIN_SIZE = 3;
	private static final int MAX_SIZE = 5;
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String MARKED_CREEPER_TAG = "CreeperBlastChamberMarked";
	private static final String CONTROLLER_POS_TAG = "CreeperBlastChamberControllerPos";
	private static final String PACKAGER_POS_TAG = "CreeperBlastChamberPackagerPos";
	private static final String PENDING_UNPACKS_TAG = "PendingUnpacks";

	public LerpedFloat gauge = LerpedFloat.linear();
	public LerpedFloat displayGauge = LerpedFloat.linear();
	private boolean structureValid;
	private int structureSize;
	private BlockPos structureOrigin;
	private BlockPos bottomCenter;
	private int recheckTimer;
	private final List<PendingUnpack> pendingUnpacks = new ArrayList<>();

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
			return;
		}

		be.tickPendingUnpacks();

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = 20;

		be.tryDetectStructure();
	}

	public InteractionResult tryInsertLargeCreeperBox(Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (!heldItem.is(CBItems.LARGE_CARDBOARD_BOX.get()))
			return InteractionResult.PASS;
		if (!structureValid) {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.creeper_blast_chamber.status.not_formed"), true);
			return InteractionResult.SUCCESS;
		}
		if (!CapturedEntityBoxHelper.containsEntityType(heldItem, EntityType.CREEPER)) {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.creeper_blast_chamber.status.invalid_input"), true);
			return InteractionResult.SUCCESS;
		}

		BlockPos targetPackager = findAvailablePackager();
		if (targetPackager == null) {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.creeper_blast_chamber.status.full"), true);
			return InteractionResult.SUCCESS;
		}

		ItemStack boxToInsert = heldItem.copy();
		boxToInsert.setCount(1);
		boolean consumeBox = !player.getAbilities().instabuild;
		if (!queueUnpack(targetPackager, boxToInsert, consumeBox)) {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.creeper_blast_chamber.status.full"), true);
			return InteractionResult.SUCCESS;
		}

		if (consumeBox)
			heldItem.shrink(1);
		player.displayClientMessage(
			Component.translatable("block.create_biotech.creeper_blast_chamber.status.accepted"), true);
		return InteractionResult.SUCCESS;
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

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("StructureValid", structureValid);
		tag.putInt("StructureSize", structureSize);
		ListTag pendingList = new ListTag();
		for (PendingUnpack pending : pendingUnpacks)
			pendingList.add(pending.write());
		tag.put(PENDING_UNPACKS_TAG, pendingList);
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
			if (!isPackagerPartOfStructure(pending.packagerPos) || getPackager(pending.packagerPos) == null) {
				dropBox(pending);
				iterator.remove();
				changed = true;
				continue;
			}

			pending.ticksRemaining--;
			if (pending.ticksRemaining > 0)
				continue;

			if (!completePendingUnpack(pending))
				dropBox(pending);
			iterator.remove();
			changed = true;
		}

		if (changed)
			setChanged();
	}

	@Nullable
	private BlockPos findAvailablePackager() {
		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerAvailable(packagerPos))
				return packagerPos;
		}
		return null;
	}

	private boolean isPackagerAvailable(BlockPos packagerPos) {
		if (isPackagerReserved(packagerPos) || hasMarkedCreeperAtPackager(packagerPos))
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
		if (packager == null || !isPackagerAvailable(packagerPos))
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
		markCreeper(creeper, pending.packagerPos);
		if (!level.addFreshEntity(creeper))
			return false;

		if (pending.returnBox) {
			ItemStack emptyBox = pending.boxStack.copy();
			emptyBox.setCount(1);
			CapturedEntityBoxHelper.clearCapturedEntity(emptyBox);
			if (!emptyBox.isEmpty())
				Block.popResource(level, pending.packagerPos.above(), emptyBox);
		}

		return true;
	}

	private void cancelPendingUnpacks() {
		if (pendingUnpacks.isEmpty())
			return;

		for (PendingUnpack pending : pendingUnpacks)
			dropBox(pending);
		pendingUnpacks.clear();
		setChanged();
	}

	private void dropBox(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || pending.boxStack.isEmpty() || !pending.returnBox)
			return;
		Block.popResource(level, pending.packagerPos.above(), pending.boxStack.copy());
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
}
