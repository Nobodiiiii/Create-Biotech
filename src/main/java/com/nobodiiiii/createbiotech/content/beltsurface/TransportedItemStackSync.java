package com.nobodiiiii.createbiotech.content.beltsurface;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Keeps client-side interpolation history stable when a belt inventory packet
 * replaces the authoritative transported-item state.
 */
public final class TransportedItemStackSync {

	private static final String SYNC_ID = "CreateBiotechSyncId";

	@FunctionalInterface
	public interface PositionDistance {
		float distance(float first, float second);
	}

	private final IdentityHashMap<TransportedItemStack, Long> ids = new IdentityHashMap<>();
	private long nextId = 1;

	/**
	 * Reads one complete inventory snapshot. Existing client items with the same id
	 * retain their predicted interpolation state when they are already close to the
	 * server; larger corrections start at the last client position rather than
	 * teleporting from the server's serialized previous tick.
	 */
	@Nullable
	public TransportedItemStack read(CompoundTag nbt, HolderLookup.Provider registries,
		List<TransportedItemStack> items, @Nullable TransportedItemStack currentLazyItem,
		boolean clientPacket, PositionDistance positionDistance, float predictionTolerance) {
		Map<Long, TransportedItemStack> existingById = new HashMap<>();
		if (clientPacket) {
			for (TransportedItemStack item : items)
				addExisting(existingById, item);
			if (currentLazyItem != null)
				addExisting(existingById, currentLazyItem);
		}

		IdentityHashMap<TransportedItemStack, Long> incomingIds = new IdentityHashMap<>();
		items.clear();
		for (Tag raw : nbt.getList("Items", Tag.TAG_COMPOUND)) {
			CompoundTag itemTag = (CompoundTag) raw;
			TransportedItemStack item = readItem(itemTag, registries, existingById, clientPacket,
				positionDistance, predictionTolerance);
			items.add(item);
			incomingIds.put(item, readOrCreateId(itemTag));
		}

		TransportedItemStack lazyItem = null;
		if (nbt.contains("LazyItem", Tag.TAG_COMPOUND)) {
			CompoundTag itemTag = nbt.getCompound("LazyItem");
			lazyItem = readItem(itemTag, registries, existingById, clientPacket,
				positionDistance, predictionTolerance);
			incomingIds.put(lazyItem, readOrCreateId(itemTag));
		}

		ids.clear();
		ids.putAll(incomingIds);
		return lazyItem;
	}

	public void write(CompoundTag nbt, HolderLookup.Provider registries, List<TransportedItemStack> items,
		@Nullable TransportedItemStack lazyItem) {
		ids.keySet().removeIf(item -> item != lazyItem && !items.contains(item));
		ListTag itemList = new ListTag();
		for (TransportedItemStack item : items)
			itemList.add(writeItem(item, registries));
		nbt.put("Items", itemList);
		if (lazyItem != null)
			nbt.put("LazyItem", writeItem(lazyItem, registries));
	}

	private void addExisting(Map<Long, TransportedItemStack> existingById, TransportedItemStack item) {
		Long id = ids.get(item);
		if (id != null)
			existingById.put(id, item);
	}

	private TransportedItemStack readItem(CompoundTag itemTag, HolderLookup.Provider registries,
		Map<Long, TransportedItemStack> existingById, boolean clientPacket,
		PositionDistance positionDistance, float predictionTolerance) {
		TransportedItemStack incoming = TransportedItemStack.read(itemTag, registries);
		if (!clientPacket || !itemTag.contains(SYNC_ID, Tag.TAG_LONG))
			return incoming;

		TransportedItemStack existing = existingById.remove(itemTag.getLong(SYNC_ID));
		if (existing == null)
			return incoming;

		float distance = positionDistance.distance(existing.beltPosition, incoming.beltPosition);
		float sideDistance = Math.abs(existing.sideOffset - incoming.sideOffset);
		boolean sameMotionState = existing.locked == incoming.locked
			&& existing.lockedExternally == incoming.lockedExternally;
		if (sameMotionState && distance <= predictionTolerance && sideDistance <= .125f) {
			incoming.prevBeltPosition = existing.prevBeltPosition;
			incoming.beltPosition = existing.beltPosition;
			incoming.prevSideOffset = existing.prevSideOffset;
			incoming.sideOffset = existing.sideOffset;
		} else {
			incoming.prevBeltPosition = existing.beltPosition;
			incoming.prevSideOffset = existing.sideOffset;
		}
		return incoming;
	}

	private CompoundTag writeItem(TransportedItemStack item, HolderLookup.Provider registries) {
		CompoundTag itemTag = item.serializeNBT(registries);
		itemTag.putLong(SYNC_ID, ids.computeIfAbsent(item, ignored -> nextId++));
		return itemTag;
	}

	private long readOrCreateId(CompoundTag itemTag) {
		if (!itemTag.contains(SYNC_ID, Tag.TAG_LONG))
			return nextId++;
		long id = itemTag.getLong(SYNC_ID);
		if (id <= 0)
			return nextId++;
		nextId = Math.max(nextId, id + 1);
		return id;
	}
}
