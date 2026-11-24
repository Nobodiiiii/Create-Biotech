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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Server-side tracker for in-flight biological packaging operations inside moving contraptions.
 *
 * Contraption-stored block entities do not tick, so the tracker deposits the box and
 * keeps the tray busy during a successful deposit's animation. If the contraption
 * disassembles before the deposit, the tracker releases the captured mob.
 */
public final class BioPackagerContraptionTracker {

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
		entry.states.put(localPos, new PackagingState(filledBox.copy()));
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
				if (!state.deposited) {
					BlockPos localPos = stateEntry.getKey();
					if (!depositIntoContraption(contraption, localPos, state.box)) {
						inner.remove();
						continue;
					}
					state.deposited = true;
					state.ticksRemaining = BioPackagerBlockEntity.getCycleTicks();
					CBPackets.sendToTrackingEntity(new BioPackagerContraptionAnimationPacket(contraption.getId(),
						localPos, ItemStack.EMPTY, state.box, true), contraption);
					continue;
				}
				if (--state.ticksRemaining <= 0)
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
			Entity captured = CapturedEntityBoxHelper.createCapturedEntity(state.box, level);
			if (captured == null) {
				Containers.dropItemStack(level, anchor.x, anchor.y, anchor.z, state.box.copy());
				continue;
			}
			captured.setPos(anchor.x, anchor.y, anchor.z);
			if (captured instanceof Mob mob)
				mob.setNoAi(false);
			CapturedEntityBoxHelper.unmarkAiDisabledByMod(captured);
			if (!level.addFreshEntity(captured))
				Containers.dropItemStack(level, anchor.x, anchor.y, anchor.z, state.box.copy());
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
		boolean preferSmall, boolean allowLargeBox) {
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
			return allowLargeBox ? findAndExtract(allItems, false) : ItemStack.EMPTY;
		}
		return allowLargeBox ? findAndExtract(allItems, false) : ItemStack.EMPTY;
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
		return CapturedEntityBoxHelper.isEmptySmallBox(stack);
	}

	private static boolean isLargeBox(ItemStack stack) {
		return CapturedEntityBoxHelper.isEmptyLargeBox(stack);
	}

	private static boolean depositIntoContraption(AbstractContraptionEntity contraptionEntity, BlockPos packagerLocal,
		ItemStack box) {
		Contraption contraption = contraptionEntity.getContraption();
		IItemHandlerModifiable allItems = contraption == null ? null : getAllItems(contraption);
		ItemStack leftover = allItems == null ? box : ItemHandlerHelper.insertItemStacked(allItems, box.copy(), false);
		if (leftover.isEmpty())
			return true;
		dropAtAnchor(contraptionEntity, packagerLocal, leftover);
		return false;
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

	private static class PackagingState {
		final ItemStack box;
		int ticksRemaining;
		boolean deposited;

		PackagingState(ItemStack box) {
			this.box = box;
		}
	}
}
