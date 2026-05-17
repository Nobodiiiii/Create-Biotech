package com.nobodiiiii.createbiotech.content.biopackager;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Server-side tracker for in-flight biological packaging operations inside moving contraptions.
 *
 * Contraption-stored block entities don't tick — we keep transient busy-state plus the deferred
 * deposit here. At CYCLE/2 we drop the filled box into the contraption inventory; on disassembly
 * any still-undeposited entries release the captured mob as a fail-safe so no work is lost.
 */
public final class BioPackagerContraptionTracker {

	public static final int CYCLE = BioPackagerBlockEntity.CYCLE;

	private static final Map<UUID, ContraptionEntry> ACTIVE = new ConcurrentHashMap<>();

	private BioPackagerContraptionTracker() {}

	public static boolean isPackagerOccupied(UUID contraptionId, BlockPos localPos) {
		ContraptionEntry entry = ACTIVE.get(contraptionId);
		return entry != null && entry.states.containsKey(localPos);
	}

	public static void startServerCapture(AbstractContraptionEntity contraptionEntity, BlockPos localPos,
		ItemStack filledBox) {
		UUID id = contraptionEntity.getUUID();
		ContraptionEntry entry = ACTIVE.computeIfAbsent(id, k -> new ContraptionEntry(contraptionEntity));
		entry.entityRef = new WeakReference<>(contraptionEntity);
		entry.states.put(localPos, new PackagingState(filledBox.copy(), CYCLE));
		CBPackets.sendToTrackingEntity(
			new BioPackagerContraptionAnimationPacket(contraptionEntity.getId(), localPos, filledBox, ItemStack.EMPTY,
				false),
			contraptionEntity);
	}

	public static void tickAll(Level level) {
		if (level.isClientSide)
			return;
		Iterator<Map.Entry<UUID, ContraptionEntry>> outer = ACTIVE.entrySet().iterator();
		while (outer.hasNext()) {
			Map.Entry<UUID, ContraptionEntry> entry = outer.next();
			ContraptionEntry contraptionEntry = entry.getValue();
			AbstractContraptionEntity contraption = contraptionEntry.entityRef.get();
			if (contraption == null || contraption.level() != level)
				continue;
			if (contraption.isRemoved()) {
				releaseStates(contraption, contraptionEntry.states);
				outer.remove();
				continue;
			}
			Iterator<Map.Entry<BlockPos, PackagingState>> inner = contraptionEntry.states.entrySet().iterator();
			while (inner.hasNext()) {
				Map.Entry<BlockPos, PackagingState> stateEntry = inner.next();
				PackagingState state = stateEntry.getValue();
				state.ticksRemaining--;
				if (!state.deposited && state.ticksRemaining <= CYCLE / 2) {
					depositIntoContraption(contraption, stateEntry.getKey(), state.filledBox);
					state.deposited = true;
				}
				if (state.ticksRemaining <= 0)
					inner.remove();
			}
			if (contraptionEntry.states.isEmpty())
				outer.remove();
		}
	}

	public static void releaseOnDisassembly(AbstractContraptionEntity contraptionEntity) {
		ContraptionEntry entry = ACTIVE.remove(contraptionEntity.getUUID());
		if (entry == null || entry.states.isEmpty())
			return;
		releaseStates(contraptionEntity, entry.states);
	}

	private static void releaseStates(AbstractContraptionEntity contraptionEntity,
		Map<BlockPos, PackagingState> states) {
		Level level = contraptionEntity.level();
		for (Map.Entry<BlockPos, PackagingState> entry : states.entrySet()) {
			PackagingState state = entry.getValue();
			if (state.deposited)
				continue;
			Vec3 anchor = contraptionEntity.toGlobalVector(Vec3.atCenterOf(entry.getKey()), 1f);
			Entity captured = CapturedEntityBoxHelper.createCapturedEntity(state.filledBox, level);
			if (captured == null) {
				Containers.dropItemStack(level, anchor.x, anchor.y, anchor.z, state.filledBox.copy());
				continue;
			}
			captured.setPos(anchor.x, anchor.y, anchor.z);
			if (captured instanceof Mob mob)
				mob.setNoAi(false);
			CapturedEntityBoxHelper.unmarkAiDisabledByMod(captured);
			if (!level.addFreshEntity(captured))
				Containers.dropItemStack(level, anchor.x, anchor.y, anchor.z, state.filledBox.copy());
		}
		states.clear();
	}

	public static void clearAll() {
		ACTIVE.clear();
	}

	@Nullable
	private static IItemHandlerModifiable getAllItems(Contraption contraption) {
		try {
			MountedStorageManager storage = contraption.getStorage();
			if (storage == null)
				return null;
			return storage.getAllItems();
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * Attempts to consume a single empty cardboard box from the contraption inventory.
	 * Returns the consumed stack (with count=1) or empty if none found / unsuitable.
	 */
	public static ItemStack consumeBoxFromContraption(AbstractContraptionEntity contraptionEntity,
		boolean preferSmall) {
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null)
			return ItemStack.EMPTY;
		IItemHandlerModifiable allItems = getAllItems(contraption);
		if (allItems == null)
			return ItemStack.EMPTY;
		if (preferSmall) {
			ItemStack small = findAndExtract(allItems, true);
			if (!small.isEmpty())
				return small;
			return findAndExtract(allItems, false);
		}
		return findAndExtract(allItems, false);
	}

	private static ItemStack findAndExtract(IItemHandler inv, boolean smallBox) {
		for (int slot = 0; slot < inv.getSlots(); slot++) {
			ItemStack stack = inv.getStackInSlot(slot);
			if (stack.isEmpty())
				continue;
			if (CapturedEntityBoxHelper.hasCapturedEntity(stack))
				continue;
			if (smallBox && !isSmallBox(stack))
				continue;
			if (!smallBox && !isLargeBox(stack))
				continue;
			ItemStack extracted = inv.extractItem(slot, 1, false);
			if (!extracted.isEmpty())
				return extracted;
		}
		return ItemStack.EMPTY;
	}

	private static boolean isSmallBox(ItemStack stack) {
		return stack.is(com.nobodiiiii.createbiotech.registry.CBItems.CARDBOARD_BOX.get());
	}

	private static boolean isLargeBox(ItemStack stack) {
		return stack.is(com.nobodiiiii.createbiotech.registry.CBItems.LARGE_CARDBOARD_BOX.get());
	}

	private static void depositIntoContraption(AbstractContraptionEntity contraptionEntity, BlockPos packagerLocal,
		ItemStack filledBox) {
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null) {
			dropAtAnchor(contraptionEntity, packagerLocal, filledBox);
			return;
		}
		IItemHandlerModifiable allItems = getAllItems(contraption);
		if (allItems != null) {
			ItemStack leftover = ItemHandlerHelper.insertItemStacked(allItems, filledBox.copy(), false);
			if (leftover.isEmpty())
				return;
			filledBox = leftover;
		}
		dropAtAnchor(contraptionEntity, packagerLocal, filledBox);
	}

	private static void dropAtAnchor(AbstractContraptionEntity contraptionEntity, BlockPos packagerLocal,
		ItemStack stack) {
		Vec3 anchor = contraptionEntity.toGlobalVector(Vec3.atCenterOf(packagerLocal), 1f);
		Level level = contraptionEntity.level();
		Containers.dropItemStack(level, anchor.x, anchor.y, anchor.z, stack.copy());
	}

	@Nullable
	public static Direction getPackagerFacing(Contraption contraption, BlockPos local) {
		if (contraption == null || contraption.getBlocks().get(local) == null)
			return null;
		BlockState state = contraption.getBlocks().get(local).state();
		if (state == null || !state.hasProperty(BioPackagerBlock.FACING))
			return null;
		return state.getValue(BioPackagerBlock.FACING);
	}

	private static final class ContraptionEntry {
		WeakReference<AbstractContraptionEntity> entityRef;
		final Map<BlockPos, PackagingState> states = new ConcurrentHashMap<>();

		ContraptionEntry(AbstractContraptionEntity entity) {
			this.entityRef = new WeakReference<>(entity);
		}
	}

	public static class PackagingState {
		public final ItemStack filledBox;
		public int ticksRemaining;
		public boolean deposited;

		public PackagingState(ItemStack filledBox, int ticksRemaining) {
			this.filledBox = filledBox;
			this.ticksRemaining = ticksRemaining;
			this.deposited = false;
		}
	}
}
