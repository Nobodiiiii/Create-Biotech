package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.mixin.MobAccessor;
import com.nobodiiiii.createbiotech.network.ContainedEntityHandoffPacket;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** ChamberContainment algorithms over the canonical CreeperBlastChamberBlockEntity state. */
final class ChamberContainment {
	private ChamberContainment() {}


	static void tickPendingUnpacks(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || operationOwner.pendingUnpacks.isEmpty())
			return;

		if (!operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;

		boolean changed = false;
		boolean transitionStructureValidated = false;
		Iterator<CreeperBlastChamberBlockEntity.PendingUnpack> iterator = operationOwner.pendingUnpacks.iterator();
		while (iterator.hasNext()) {
			CreeperBlastChamberBlockEntity.PendingUnpack pending = iterator.next();
			BioPackagerBlockEntity packager = operationOwner.getPackager(pending.packagerPos);
			if (!ChamberStructure.isPackagerPartOfStructure(operationOwner, pending.packagerPos) || packager == null) {
				ChamberContainment.dropBox(operationOwner, pending);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.getCycleTicks()) {
				if (!transitionStructureValidated) {
					transitionStructureValidated = true;
					if (!ChamberContainment.validateStructureForUnpackTransition(operationOwner, level))
						return;
				}
				if (!ChamberContainment.completePendingUnpack(operationOwner, pending)) {
					ChamberContainment.dropBox(operationOwner, pending);
					ChamberContainment.clearPackagerAnimationState(operationOwner, pending.packagerPos);
					iterator.remove();
					changed = true;
					continue;
				}
				pending.transitioned = true;
				ItemStack emptyBox = CapturedEntityBoxHelper.createEmptyBox(pending.boxStack).copyWithCount(1);
				packager.heldBox = emptyBox;
				packager.previouslyUnwrapped = ItemStack.EMPTY;
				packager.animationInward = true;
				packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			ChamberContainment.clearPackagerAnimationState(operationOwner, pending.packagerPos);
			iterator.remove();
			changed = true;
		}

		if (changed)
			operationOwner.setChanged();
	}

	static boolean validateStructureForUnpackTransition(CreeperBlastChamberBlockEntity operationOwner, Level level) {
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null)
			return false;
		if (ChamberStructure.scanStructure(operationOwner, level, operationOwner.structureOrigin, operationOwner.structureSize) != null) {
			operationOwner.recheckTimer = CreeperBlastChamberBlockEntity.STRUCTURE_RECHECK_INTERVAL_TICKS;
			return true;
		}

		int oldSize = operationOwner.structureSize;
		BlockPos oldOrigin = operationOwner.structureOrigin;
		Axis oldPressAxis = operationOwner.structurePressAxis;
		ChamberStructure.setStructure(operationOwner, level, false, 0, null, null, null);
		ChamberStructure.onStructureBroken(operationOwner, level, oldSize, oldOrigin, oldPressAxis);
		return false;
	}

	static void tickPendingAppearances(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.pendingAppearances.isEmpty())
			return;

		boolean changed = false;
		Iterator<CreeperBlastChamberBlockEntity.PendingAppearance> iterator = operationOwner.pendingAppearances.iterator();
		while (iterator.hasNext()) {
			CreeperBlastChamberBlockEntity.PendingAppearance pending = iterator.next();
			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (pending.ticksRemaining > 0)
				continue;

			iterator.remove();
			changed = true;
		}

		if (changed) {
			operationOwner.setChanged();
			operationOwner.notifyUpdate();
		}
	}

	static void tickPendingPackagings(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || operationOwner.pendingPackagings.isEmpty())
			return;

		boolean changed = false;
		Iterator<CreeperBlastChamberBlockEntity.PendingPackaging> iterator = operationOwner.pendingPackagings.iterator();
		while (iterator.hasNext()) {
			CreeperBlastChamberBlockEntity.PendingPackaging pending = iterator.next();
			BioPackagerBlockEntity packager = operationOwner.getPackager(pending.packagerPos);
			if (packager == null) {
				ChamberContainment.restorePendingPackaging(operationOwner, pending, true);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.getCycleTicks()) {
				pending.transitioned = true;
				packager.heldBox = pending.boxStack.copy();
				packager.previouslyUnwrapped = ItemStack.EMPTY;
				packager.animationInward = true;
				packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			packager.heldBox = ItemStack.EMPTY;
			packager.animationTicks = 0;
			packager.notifyUpdate();
			packager.setChanged();
			operationOwner.storedCreepers.remove(pending.packagerPos);
			operationOwner.readyOutputs.add(new CreeperBlastChamberBlockEntity.ReadyOutput(pending.packagerPos, pending.boxStack.copy(), CreeperBlastChamberBlockEntity.getReadyOutputTimeout()));
			iterator.remove();
			changed = true;
		}

		if (changed) {
			operationOwner.setChanged();
			operationOwner.notifyUpdate();
		}
	}

	static void tickReadyOutputs(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || operationOwner.readyOutputs.isEmpty() || !operationOwner.structureValid)
			return;

		boolean changed = false;
		Iterator<CreeperBlastChamberBlockEntity.ReadyOutput> iterator = operationOwner.readyOutputs.iterator();
		while (iterator.hasNext()) {
			CreeperBlastChamberBlockEntity.ReadyOutput readyOutput = iterator.next();
			if (ChamberContainment.isControllerOutputRequestActive(operationOwner)) {
				if (readyOutput.ticksRemaining != CreeperBlastChamberBlockEntity.getReadyOutputTimeout()) {
					readyOutput.ticksRemaining = CreeperBlastChamberBlockEntity.getReadyOutputTimeout();
					changed = true;
				}
				continue;
			}
			if (readyOutput.ticksRemaining > 0)
				readyOutput.ticksRemaining--;
			if (readyOutput.ticksRemaining > 0)
				continue;

			BlockPos targetPackager = ChamberContainment.findFirstEmptyPackagerSlot(operationOwner);
			if (targetPackager != null && ChamberContainment.queueUnpack(operationOwner, targetPackager, readyOutput.boxStack, true)) {
				iterator.remove();
				changed = true;
			}
		}

		if (changed)
			operationOwner.setChanged();
	}

	static void tickControllerOutputRequest(CreeperBlastChamberBlockEntity operationOwner) {
		if (!operationOwner.controllerOutputRequested)
			return;
		if (ChamberStructure.isPausedForPartialChunkUnload(operationOwner)) {
			operationOwner.controllerOutputRequested = false;
			operationOwner.controllerOutputRequestTicks = 0;
			operationOwner.setChanged();
			return;
		}

		boolean changed = ChamberContainment.packageMarkedCreepersForOutput(operationOwner);
		if (operationOwner.controllerOutputRequestTicks > 0)
			operationOwner.controllerOutputRequestTicks--;
		if (operationOwner.controllerOutputRequestTicks <= 0) {
			operationOwner.controllerOutputRequested = false;
			changed = true;
		}

		if (changed)
			operationOwner.setChanged();
	}

	static @Nullable
	BlockPos findFirstEmptyPackagerSlot(CreeperBlastChamberBlockEntity operationOwner) {
		for (BlockPos packagerPos : ChamberStructure.getPackagerPositions(operationOwner)) {
			if (ChamberContainment.isPackagerSlotEmpty(operationOwner, packagerPos) && ChamberContainment.canUsePackagerForInternalTransfer(operationOwner, operationOwner.getPackager(packagerPos)))
				return packagerPos;
		}
		return null;
	}

	static int getHeldCreeperCount(CreeperBlastChamberBlockEntity operationOwner) {
		int uncommittedInputs = 0;
		for (CreeperBlastChamberBlockEntity.PendingUnpack pending : operationOwner.pendingUnpacks)
			if (!pending.transitioned)
				uncommittedInputs++;
		return uncommittedInputs + operationOwner.readyOutputs.size() + operationOwner.storedCreepers.size();
	}

	static CreeperBlastChamberBlockEntity.CreeperCountSummary summarizeWorkableMarkedCreepers(CreeperBlastChamberBlockEntity operationOwner) {
		int normalCount = 0;
		int chargedCount = 0;
		for (CreeperBlastChamberBlockEntity.StoredCreeper stored : operationOwner.storedCreepers.values()) {
			BlockPos packagerPos = stored.packagerPos();
			if (operationOwner.isPackagerAppearing(packagerPos) || ChamberContainment.isPackagerPackaging(operationOwner, packagerPos))
				continue;
			if (stored.charged())
				chargedCount++;
			else
				normalCount++;
		}
		return new CreeperBlastChamberBlockEntity.CreeperCountSummary(normalCount, chargedCount);
	}

	static CreeperBlastChamberBlockEntity.ChamberCreeperKind getHeldCreeperKind(CreeperBlastChamberBlockEntity operationOwner) {
		CreeperBlastChamberBlockEntity.ChamberCreeperKind kind = CreeperBlastChamberBlockEntity.ChamberCreeperKind.NONE;
		for (CreeperBlastChamberBlockEntity.PendingUnpack pending : operationOwner.pendingUnpacks)
			kind = kind.merge(ChamberContainment.getBoxedCreeperKind(operationOwner, pending.boxStack));
		for (CreeperBlastChamberBlockEntity.PendingPackaging pending : operationOwner.pendingPackagings)
			kind = kind.merge(ChamberContainment.getBoxedCreeperKind(operationOwner, pending.boxStack));
		for (CreeperBlastChamberBlockEntity.ReadyOutput readyOutput : operationOwner.readyOutputs)
			kind = kind.merge(ChamberContainment.getBoxedCreeperKind(operationOwner, readyOutput.boxStack));
		for (CreeperBlastChamberBlockEntity.StoredCreeper stored : operationOwner.storedCreepers.values())
			kind = kind.merge(stored.charged() ? CreeperBlastChamberBlockEntity.ChamberCreeperKind.CHARGED : CreeperBlastChamberBlockEntity.ChamberCreeperKind.NORMAL);
		return kind;
	}

	static boolean canAcceptIncomingCreeperBox(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack) {
		CreeperBlastChamberBlockEntity.ChamberCreeperKind incomingKind = ChamberContainment.getBoxedCreeperKind(operationOwner, stack);
		if (incomingKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.NONE || incomingKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.MIXED)
			return false;

		CreeperBlastChamberBlockEntity.ChamberCreeperKind heldKind = ChamberContainment.getHeldCreeperKind(operationOwner);
		if (heldKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.NONE)
			return true;
		if (heldKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.NORMAL)
			return incomingKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.NORMAL;
		return incomingKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.CHARGED;
	}

	static boolean isPackagerSlotEmpty(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos) {
		return !operationOwner.isPackagerReserved(packagerPos)
			&& !ChamberContainment.isPackagerPackaging(operationOwner, packagerPos)
			&& !operationOwner.storedCreepers.containsKey(packagerPos);
	}

	static boolean canUsePackagerForInternalTransfer(CreeperBlastChamberBlockEntity operationOwner, @Nullable BioPackagerBlockEntity packager) {
		return packager != null
			&& packager.animationTicks <= 0
			&& packager.heldBox.isEmpty();
	}

	static boolean queueUnpack(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos, ItemStack boxStack, boolean returnBox) {
		BioPackagerBlockEntity packager = operationOwner.getPackager(packagerPos);
		if (!ChamberContainment.isPackagerSlotEmpty(operationOwner, packagerPos) || !ChamberContainment.canUsePackagerForInternalTransfer(operationOwner, packager))
			return false;

		packager.heldBox = boxStack.copy();
		packager.previouslyUnwrapped = ItemStack.EMPTY;
		packager.animationInward = false;
		packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();

		operationOwner.pendingUnpacks.add(new CreeperBlastChamberBlockEntity.PendingUnpack(packagerPos, boxStack.copy(),
			BioPackagerBlockEntity.getCycleTicks() * 2, false, returnBox));
		operationOwner.setChanged();
		return true;
	}

	static CreeperBlastChamberBlockEntity.InsertResult tryInsertLargeCreeperBox(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack, boolean simulate, boolean returnBox) {
		if (!ChamberContainment.isValidLargeCreeperBox(operationOwner, stack))
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);

		Level level = operationOwner.getLevel();
		if (level == null || level.isClientSide)
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);

		if (!operationOwner.structureValid || operationOwner.recheckTimer <= 0) {
			ChamberStructure.tryDetectStructure(operationOwner);
			operationOwner.recheckTimer = CreeperBlastChamberBlockEntity.STRUCTURE_RECHECK_INTERVAL_TICKS;
		}
		if (!operationOwner.structureValid)
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);
		if (ChamberStructure.isPausedForPartialChunkUnload(operationOwner))
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);
		if (!ChamberContainment.canAcceptIncomingCreeperBox(operationOwner, stack))
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);

		BlockPos targetPackager = ChamberClientEffects.findAvailablePackagerForNewInput(operationOwner);
		if (targetPackager == null)
			return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);

		if (!simulate) {
			ItemStack boxToInsert = stack.copy();
			boxToInsert.setCount(1);
			if (!ChamberContainment.queueUnpack(operationOwner, targetPackager, boxToInsert, returnBox))
				return new CreeperBlastChamberBlockEntity.InsertResult(false, stack);
		}

		if (stack.getCount() <= 1)
			return new CreeperBlastChamberBlockEntity.InsertResult(true, ItemStack.EMPTY);

		ItemStack remainder = stack.copy();
		remainder.shrink(1);
		return new CreeperBlastChamberBlockEntity.InsertResult(true, remainder);
	}

	static boolean isValidLargeCreeperBox(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack) {
		return stack.is(CBItems.LARGE_CARDBOARD_BOX.get())
			&& CapturedEntityBoxHelper.containsEntityType(stack, EntityType.CREEPER);
	}

	static CreeperBlastChamberBlockEntity.ChamberCreeperKind getCreeperKind(CreeperBlastChamberBlockEntity operationOwner, Creeper creeper) {
		return creeper.isPowered() ? CreeperBlastChamberBlockEntity.ChamberCreeperKind.CHARGED : CreeperBlastChamberBlockEntity.ChamberCreeperKind.NORMAL;
	}

	static CreeperBlastChamberBlockEntity.ChamberCreeperKind getBoxedCreeperKind(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack) {
		Level level = operationOwner.getLevel();
		if (level != null) {
			Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
			if (entity instanceof Creeper creeper)
				return ChamberContainment.getCreeperKind(operationOwner, creeper);
		}

		CompoundTag boxTag = CBItemData.get(stack);
		if (boxTag == null || !boxTag.contains("CapturedEntity", Tag.TAG_COMPOUND))
			return CreeperBlastChamberBlockEntity.ChamberCreeperKind.NONE;

		CompoundTag entityData = boxTag.getCompound("CapturedEntity");
		ResourceLocation entityId = ResourceLocation.tryParse(entityData.getString("id"));
		if (entityId == null || !entityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.CREEPER)))
			return CreeperBlastChamberBlockEntity.ChamberCreeperKind.NONE;
		return entityData.getBoolean("powered") ? CreeperBlastChamberBlockEntity.ChamberCreeperKind.CHARGED : CreeperBlastChamberBlockEntity.ChamberCreeperKind.NORMAL;
	}

	static @Nullable
	ItemStack getActualControllerOutput(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.readyOutputs.isEmpty())
			return null;
		return operationOwner.readyOutputs.get(0).boxStack.copy();
	}

	static ItemStack getDeferredControllerOutputPreview(CreeperBlastChamberBlockEntity operationOwner) {
		CreeperBlastChamberBlockEntity.StoredCreeperTarget target = ChamberContainment.findStoredCreeperForOutput(operationOwner);
		if (target == null)
			return ItemStack.EMPTY;
		return CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			target.stored().normalizedPayloadBox(), CBItems.LARGE_CARDBOARD_BOX.get());
	}

	static void requestControllerOutput(CreeperBlastChamberBlockEntity operationOwner) {
		if (ChamberStructure.isPausedForPartialChunkUnload(operationOwner))
			return;
		operationOwner.controllerOutputRequested = true;
		operationOwner.controllerOutputRequestTicks = CreeperBlastChamberBlockEntity.OUTPUT_REQUEST_KEEPALIVE_TICKS;
		operationOwner.setChanged();
	}

	static boolean isControllerOutputRequestActive(CreeperBlastChamberBlockEntity operationOwner) {
		return operationOwner.controllerOutputRequested && operationOwner.controllerOutputRequestTicks > 0;
	}

	static @Nullable
	ItemStack extractReadyOutput(CreeperBlastChamberBlockEntity operationOwner, boolean simulate) {
		if (operationOwner.readyOutputs.isEmpty())
			return null;

		ItemStack output = operationOwner.readyOutputs.get(0).boxStack.copy();
		if (!simulate) {
			operationOwner.readyOutputs.remove(0);
			operationOwner.setChanged();
		}
		return output;
	}

	static boolean packageMarkedCreepersForOutput(CreeperBlastChamberBlockEntity operationOwner) {
		boolean packagedAny = false;
		while (true) {
			CreeperBlastChamberBlockEntity.StoredCreeperTarget target = ChamberContainment.findStoredCreeperForOutput(operationOwner);
			if (target == null)
				return packagedAny;
			if (!ChamberContainment.packageMarkedCreeperForOutput(operationOwner, target))
				return packagedAny;
			packagedAny = true;
		}
	}

	static boolean packageMarkedCreeperForOutput(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.StoredCreeperTarget target) {
		ItemStack output = CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			target.stored().normalizedPayloadBox(), CBItems.LARGE_CARDBOARD_BOX.get());
		if (output.isEmpty())
			return false;

		BioPackagerBlockEntity packager = operationOwner.getPackager(target.packagerPos());
		if (!ChamberContainment.canUsePackagerForInternalTransfer(operationOwner, packager))
			return false;

		packager.heldBox = ChamberContainment.createEmptyPackageVisual(operationOwner);
		packager.previouslyUnwrapped = ItemStack.EMPTY;
		packager.animationInward = false;
		packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();
		operationOwner.pendingPackagings.add(new CreeperBlastChamberBlockEntity.PendingPackaging(target.packagerPos(), output.copy(),
			BioPackagerBlockEntity.getCycleTicks() * 2, false));
		operationOwner.setChanged();
		operationOwner.notifyUpdate();
		return true;
	}

	static ItemStack createEmptyPackageVisual(CreeperBlastChamberBlockEntity operationOwner) {
		return new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get());
	}

	static @Nullable
	CreeperBlastChamberBlockEntity.StoredCreeperTarget findStoredCreeperForOutput(CreeperBlastChamberBlockEntity operationOwner) {
		if (!operationOwner.structureValid)
			return null;

		for (BlockPos packagerPos : ChamberStructure.getPackagerPositions(operationOwner)) {
			if (operationOwner.isPackagerReserved(packagerPos) || operationOwner.isPackagerAppearing(packagerPos) || ChamberContainment.isPackagerPackaging(operationOwner, packagerPos))
				continue;
			BioPackagerBlockEntity packager = operationOwner.getPackager(packagerPos);
			if (!ChamberContainment.canUsePackagerForInternalTransfer(operationOwner, packager))
				continue;

			CreeperBlastChamberBlockEntity.StoredCreeper stored = operationOwner.storedCreepers.get(packagerPos);
			if (stored != null)
				return new CreeperBlastChamberBlockEntity.StoredCreeperTarget(packagerPos, stored);
		}

		return null;
	}

	static boolean isPackagerPackaging(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos) {
		for (CreeperBlastChamberBlockEntity.PendingPackaging pending : operationOwner.pendingPackagings) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	static @Nullable
	ItemStack createBoxedCreeper(CreeperBlastChamberBlockEntity operationOwner, Creeper creeper) {
		ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
		if (!CapturedEntityBoxHelper.captureEntity(box, creeper))
			return null;
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(box);
		return box;
	}

	static @Nullable
	CreeperBlastChamberBlockEntity.StoredCreeper createStoredCreeper(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos, ItemStack sourceBox) {
		ItemStack normalized = CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			sourceBox, CBItems.LARGE_CARDBOARD_BOX.get());
		if (normalized.isEmpty() || operationOwner.getLevel() == null)
			return null;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(normalized, operationOwner.getLevel());
		if (!(entity instanceof Creeper creeper))
			return null;
		long renderSeed = creeper.getUUID().getMostSignificantBits() ^ creeper.getUUID().getLeastSignificantBits()
			^ packagerPos.asLong();
		return new CreeperBlastChamberBlockEntity.StoredCreeper(packagerPos.immutable(), normalized, creeper.isPowered(), renderSeed,
			ChamberContainment.getPreferredCreeperYaw(operationOwner));
	}

	static float getPreferredCreeperYaw(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !operationOwner.structureValid || operationOwner.structureOrigin == null || operationOwner.structureSize <= 2)
			return Direction.NORTH.toYRot();

		Direction preferred = CreeperBlastChamberBlockEntity.CREEPER_WINDOW_FACING_PRIORITY[0];
		int greatestWindowCount = -1;
		for (Direction side : CreeperBlastChamberBlockEntity.CREEPER_WINDOW_FACING_PRIORITY) {
			int windowCount = ChamberContainment.countBlastProofWindows(operationOwner, level, side);
			if (windowCount <= greatestWindowCount)
				continue;
			preferred = side;
			greatestWindowCount = windowCount;
		}
		return preferred.toYRot();
	}

	static int countBlastProofWindows(CreeperBlastChamberBlockEntity operationOwner, Level level, Direction side) {
		if (operationOwner.structureOrigin == null)
			return 0;
		int count = 0;
		for (int y = 1; y <= 2; y++) {
			for (int lateral = 1; lateral < operationOwner.structureSize - 1; lateral++) {
				BlockPos pos = switch (side) {
					case NORTH -> operationOwner.structureOrigin.offset(lateral, y, 0);
					case SOUTH -> operationOwner.structureOrigin.offset(lateral, y, operationOwner.structureSize - 1);
					case WEST -> operationOwner.structureOrigin.offset(0, y, lateral);
					case EAST -> operationOwner.structureOrigin.offset(operationOwner.structureSize - 1, y, lateral);
					default -> throw new IllegalArgumentException("Expected a horizontal side, got " + side);
				};
				BlockState state = level.getBlockState(pos);
				if (state.is(CBBlocks.BLAST_PROOF_GLASS.get())
					|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get()))
					count++;
			}
		}
		return count;
	}

	static boolean completePendingUnpack(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.PendingUnpack pending) {
		Level level = operationOwner.getLevel();
		if (level == null || operationOwner.storedCreepers.containsKey(pending.packagerPos))
			return false;

		CreeperBlastChamberBlockEntity.StoredCreeper stored = ChamberContainment.createStoredCreeper(operationOwner, pending.packagerPos, pending.boxStack);
		if (stored == null)
			return false;
		operationOwner.storedCreepers.put(pending.packagerPos, stored);
		operationOwner.pendingAppearances.add(new CreeperBlastChamberBlockEntity.PendingAppearance(pending.packagerPos, CreeperBlastChamberBlockEntity.CREEPER_ENTRY_ANIMATION_TICKS));
		operationOwner.setChanged();
		operationOwner.notifyUpdate();

		return true;
	}

	static void cancelPendingUnpacks(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.pendingUnpacks.isEmpty())
			return;

		for (CreeperBlastChamberBlockEntity.PendingUnpack pending : operationOwner.pendingUnpacks)
			ChamberContainment.dropBox(operationOwner, pending);
		operationOwner.pendingUnpacks.clear();
		operationOwner.setChanged();
	}

	static void releaseManagedCreepers(CreeperBlastChamberBlockEntity operationOwner, BlockPos origin, int size) {
		Level level = operationOwner.getLevel();
		if (level == null || level.isClientSide)
			return;
		ChamberContainment.migrateLegacyContainedCreepers(operationOwner);

		for (CreeperBlastChamberBlockEntity.PendingPackaging pending : operationOwner.pendingPackagings)
			ChamberContainment.clearPackagerAnimationState(operationOwner, pending.packagerPos);

		for (CreeperBlastChamberBlockEntity.StoredCreeper stored : new ArrayList<>(operationOwner.storedCreepers.values())) {
			if (!ChamberContainment.materializeStoredCreeper(operationOwner, stored))
				ChamberContainment.dropPackagedBox(operationOwner, stored.normalizedPayloadBox(), stored.packagerPos());
			operationOwner.storedCreepers.remove(stored.packagerPos());
		}

		operationOwner.pendingAppearances.clear();
		operationOwner.pendingPackagings.clear();
		operationOwner.legacyMarkedCreepers.clear();
		operationOwner.controllerOutputRequested = false;
		operationOwner.controllerOutputRequestTicks = 0;
		operationOwner.setChanged();
		operationOwner.notifyUpdate();
	}

	static boolean materializeStoredCreeper(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.StoredCreeper stored) {
		if (!(operationOwner.getLevel() instanceof ServerLevel serverLevel))
			return false;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stored.normalizedPayloadBox(), operationOwner.getLevel());
		if (!(entity instanceof Creeper creeper))
			return false;
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(creeper);
		Vec3 position = Vec3.atBottomCenterOf(stored.packagerPos().above());
		creeper.stopRiding();
		creeper.moveTo(position.x, position.y, position.z, creeper.getYRot(), creeper.getXRot());
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		creeper.setInvisible(false);
		ContainedEntityHandoffPacket.announce(serverLevel, creeper, operationOwner.getBlockPos(), stored.packagerPos(),
			stored.renderSeed(), 0);
		if (operationOwner.getLevel().addFreshEntity(creeper))
			return true;
		ContainedEntityHandoffPacket.cancel(serverLevel, creeper, operationOwner.getBlockPos());
		return false;
	}

	static void migrateLegacyContainedCreepers(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.containedDataVersion >= CreeperBlastChamberBlockEntity.CONTAINED_DATA_VERSION || !(operationOwner.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (operationOwner.structureValid && ChamberStructure.isPausedForPartialChunkUnload(operationOwner))
			return;

		Set<UUID> migrated = new HashSet<>();
		for (Map.Entry<UUID, BlockPos> entry : new ArrayList<>(operationOwner.legacyMarkedCreepers.entrySet())) {
			UUID uuid = entry.getKey();
			BlockPos packagerPos = entry.getValue();
			Entity found = serverLevel.getEntity(uuid);
			CreeperBlastChamberBlockEntity.PendingPackaging packaging = ChamberContainment.findPendingPackaging(operationOwner, packagerPos);
			if (packaging != null) {
				CreeperBlastChamberBlockEntity.StoredCreeper stored = ChamberContainment.createStoredCreeper(operationOwner, packagerPos, packaging.boxStack);
				if (stored != null && !operationOwner.storedCreepers.containsKey(packagerPos)) {
					operationOwner.storedCreepers.put(packagerPos, stored);
					if (found instanceof Creeper creeper)
						creeper.discard();
					migrated.add(uuid);
					continue;
				}
			}
			if (found instanceof Creeper creeper && operationOwner.isMarkedCreeperForThisChamber(creeper, packagerPos)) {
				ItemStack captured = ChamberContainment.createBoxedCreeper(operationOwner, creeper);
				if (captured != null)
					ChamberContainment.clearLegacyMachineMutations(captured);
				CreeperBlastChamberBlockEntity.StoredCreeper stored = captured == null ? null : ChamberContainment.createStoredCreeper(operationOwner, packagerPos, captured);
				if (stored != null && !operationOwner.storedCreepers.containsKey(packagerPos)) {
					operationOwner.storedCreepers.put(packagerPos, new CreeperBlastChamberBlockEntity.StoredCreeper(packagerPos, stored.normalizedPayloadBox(),
						creeper.isPowered(), uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()
							^ packagerPos.asLong(), stored.defaultYaw()));
					creeper.discard();
					migrated.add(uuid);
					continue;
				}
				ChamberContainment.releaseManagedCreeper(operationOwner, creeper);
				continue;
			}

			CreeperBlastChamberBlockEntity.LOGGER.warn("Could not migrate contained creeper {} for blast chamber at {}; releasing the empty slot",
				uuid, operationOwner.getBlockPos());
		}

		if (operationOwner.structureOrigin != null) {
			AABB bounds = operationOwner.getMarkedCreeperSearchBounds();
			for (Creeper creeper : operationOwner.getLevel().getEntitiesOfClass(Creeper.class, bounds,
				entity -> entity.isAlive() && operationOwner.isMarkedCreeperForThisChamber(entity, null))) {
				if (!migrated.contains(creeper.getUUID()))
					ChamberContainment.releaseManagedCreeper(operationOwner, creeper);
			}
		}

		operationOwner.legacyMarkedCreepers.clear();
		operationOwner.containedDataVersion = CreeperBlastChamberBlockEntity.CONTAINED_DATA_VERSION;
		operationOwner.pendingAppearances.removeIf(pending -> !operationOwner.storedCreepers.containsKey(pending.packagerPos));
		operationOwner.setChanged();
		operationOwner.notifyUpdate();
	}

	static @Nullable
	CreeperBlastChamberBlockEntity.PendingPackaging findPendingPackaging(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos) {
		for (CreeperBlastChamberBlockEntity.PendingPackaging pending : operationOwner.pendingPackagings)
			if (pending.packagerPos.equals(packagerPos))
				return pending;
		return null;
	}

	static void clearLegacyMachineMutations(ItemStack box) {
		CBItemData.edit(box, root -> {
			if (!root.contains("CapturedEntity", Tag.TAG_COMPOUND))
				return;
			CompoundTag entityData = root.getCompound("CapturedEntity");
			entityData.remove("NoAI");
			entityData.remove("PersistenceRequired");
			entityData.remove("Invisible");
		});
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(box);
	}

	static void clearPackagerAnimationState(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos) {
		BioPackagerBlockEntity packager = operationOwner.getPackager(packagerPos);
		if (packager == null)
			return;
		packager.heldBox = ItemStack.EMPTY;
		packager.animationTicks = 0;
		packager.notifyUpdate();
		packager.setChanged();
	}

	static void releaseManagedCreeper(CreeperBlastChamberBlockEntity operationOwner, Creeper creeper) {
		ChamberContainment.clearMarkedCreeperData(operationOwner, creeper);
		creeper.setNoAi(false);
		CapturedEntityBoxHelper.unmarkAiDisabledByMod(creeper);
		creeper.setInvisible(false);
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		((MobAccessor) creeper).createBiotech$setPersistenceRequired(false);
	}

	static void clearMarkedCreeperData(CreeperBlastChamberBlockEntity operationOwner, Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(CreeperBlastChamberBlockEntity.DATA_ROOT, Tag.TAG_COMPOUND))
			return;

		CompoundTag data = persistentData.getCompound(CreeperBlastChamberBlockEntity.DATA_ROOT);
		data.remove(CreeperBlastChamberBlockEntity.MARKED_CREEPER_TAG);
		data.remove(CreeperBlastChamberBlockEntity.CONTROLLER_POS_TAG);
		data.remove(CreeperBlastChamberBlockEntity.PACKAGER_POS_TAG);
		if (data.isEmpty()) {
			persistentData.remove(CreeperBlastChamberBlockEntity.DATA_ROOT);
			return;
		}
		persistentData.put(CreeperBlastChamberBlockEntity.DATA_ROOT, data);
	}

	static void dropBox(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.PendingUnpack pending) {
		Level level = operationOwner.getLevel();
		if (level == null || pending.boxStack.isEmpty() || !pending.returnBox)
			return;
		if (pending.transitioned)
			return;
		ItemStack toDrop = pending.boxStack.copy();
		Block.popResource(level, pending.packagerPos.above(), toDrop);
	}

	static void dropPackagedBox(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack, BlockPos pos) {
		Level level = operationOwner.getLevel();
		if (level == null || stack.isEmpty())
			return;
		Block.popResource(level, pos.above(), stack.copy());
	}

	static void restorePendingPackaging(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.PendingPackaging pending, boolean dropIfMissing) {
		if (operationOwner.storedCreepers.containsKey(pending.packagerPos))
			return;
		if (dropIfMissing)
			ChamberContainment.dropPackagedBox(operationOwner, pending.boxStack, pending.packagerPos);
	}
}
