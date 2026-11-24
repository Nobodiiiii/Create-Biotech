package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.menu.MenuBase;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

public final class BioPackagerLogistics {
	private BioPackagerLogistics() {}

	public static boolean mayRequestCreatures(ServerPlayer player, StockTickerBlockEntity ticker) {
		if (!(player.containerMenu instanceof MenuBase<?> menu) || menu.contentHolder != ticker
			|| !(menu instanceof StockKeeperRequestMenu || menu.getType() == AllMenuTypes.STOCK_KEEPER_REQUEST.get())
			|| !menu.stillValid(player) || !ticker.behaviour.mayInteract(player))
			return false;
		ItemStack held = player.getMainHandItem();
		return !AllBlocks.REDSTONE_REQUESTER.isIn(held) && !AllItemTags.TABLE_CLOTHS.matches(held);
	}

	public static InventorySummary addCreatureStock(InventorySummary items, UUID network) {
		InventorySummary creatures = new InventorySummary();
		Set<InventoryKey> counted = new HashSet<>();
		for (BioPackagerBlockEntity packager : getBioPackagers(network)) {
			IdentifiedInventory inventory = packager.targetInventory.getIdentifiedInventory();
			if (inventory == null)
				continue;
			Object identity = inventory.identifier() == null ? inventory.handler() : inventory.identifier();
			if (!counted.add(new InventoryKey(packager.getLevel(), identity)))
				continue;
			for (BigItemStack entry : packager.getAvailableItems().getStacks())
				if (BioPackagerBlockEntity.isCreaturePackage(entry.stack))
					creatures.add(CreatureStockItem.ofBox(entry.stack), entry.count);
		}
		if (creatures.isEmpty())
			return items;
		creatures.add(items);
		return creatures;
	}

	public static boolean placeOrder(UUID network, PackageOrderWithCrafts order, String address) {
		List<BigItemStack> packages = new ArrayList<>();
		InventorySummary creatures = new InventorySummary();
		for (BigItemStack entry : order.stacks()) {
			if (!CreatureStockItem.isCreatureStock(entry.stack)) {
				packages.add(entry);
				continue;
			}
			ItemStack box = CreatureStockItem.getBox(entry.stack);
			if (entry.count <= 0 || !BioPackagerBlockEntity.isCreaturePackage(box))
				return false;
			creatures.add(box, entry.count);
		}
		PackageOrderWithCrafts boxOrder = new PackageOrderWithCrafts(new PackageOrder(packages), order.orderedCrafts());

		Set<BioPackagerBlockEntity> packagers = getBioPackagers(network);
		List<ReservedRelease> releases = new ArrayList<>();
		boolean committed = false;
		try {
			// Release orders can only be sourced by Bio Packagers. Reserve those boxes first so
			// Create plans the shipments from the remaining stock, including shared inventories.
			if (!reserveReleases(creatures, packagers, releases))
				return false;

			Multimap<PackagerBlockEntity, PackagingRequest> shipments =
				LogisticsManager.findPackagersForRequest(network, boxOrder, null, address);
			if (!canShip(packages, shipments))
				return false;

			releases.forEach(release -> release.packager().queueCreatureRelease(release.box()));
			committed = true;
			Set<BioPackagerBlockEntity> releasing = new LinkedHashSet<>();
			releases.forEach(release -> releasing.add(release.packager()));
			for (BioPackagerBlockEntity packager : releasing) {
				packager.flashLink();
				// An idle tray starts the release before any outgoing package can occupy it.
				packager.tryStartQueuedCreatureRelease();
			}
			LogisticsManager.performPackageRequests(shipments);
			return true;
		} finally {
			if (!committed)
				for (ReservedRelease release : releases)
					release.packager().restoreCreatureBox(release.inventory(), release.slot(), release.box());
			if (!releases.isEmpty()) {
				LogisticsManager.ACCURATE_SUMMARIES.invalidate(network);
				LogisticsManager.SUMMARIES.invalidate(network);
			}
		}
	}

	private static boolean reserveReleases(InventorySummary creatures, Set<BioPackagerBlockEntity> packagers,
		List<ReservedRelease> releases) {
		Map<BioPackagerBlockEntity, Integer> reserved = new HashMap<>();
		for (BigItemStack entry : creatures.getStacks()) {
			int remaining = entry.count;
			for (BioPackagerBlockEntity packager : packagers) {
				int alreadyReserved = reserved.getOrDefault(packager, 0);
				int capacity = Math.min(remaining, packager.getCreatureReleaseCapacity() - alreadyReserved);
				IItemHandler inventory = packager.getAttachedInventory();
				if (capacity <= 0 || inventory == null)
					continue;
				int taken = 0;
				for (int slot = 0; slot < inventory.getSlots() && taken < capacity; slot++) {
					while (taken < capacity) {
						ItemStack box = packager.takeCreatureBox(inventory, slot, entry.stack);
						if (box.isEmpty())
							break;
						releases.add(new ReservedRelease(packager, inventory, slot, box));
						taken++;
					}
				}
				reserved.put(packager, alreadyReserved + taken);
				remaining -= taken;
				if (remaining == 0)
					break;
			}
			if (remaining != 0)
				return false;
		}
		return true;
	}

	private static boolean canShip(List<BigItemStack> packages,
		Multimap<PackagerBlockEntity, PackagingRequest> shipments) {
		for (PackagerBlockEntity packager : shipments.keySet())
			if (packager.isTooBusyFor(RequestType.PLAYER))
				return false;

		InventorySummary allocated = new InventorySummary();
		for (PackagingRequest shipment : shipments.values())
			allocated.add(shipment.item(), shipment.getCount());
		for (BigItemStack entry : packages)
			if (allocated.getCountOf(entry.stack) < entry.count)
				return false;
		return true;
	}

	private static Set<BioPackagerBlockEntity> getBioPackagers(UUID network) {
		Set<BioPackagerBlockEntity> packagers = new LinkedHashSet<>();
		for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, true))
			if (link.blockEntity instanceof PackagerLinkBlockEntity stockLink
				&& stockLink.getPackager() instanceof BioPackagerBlockEntity packager && !packager.isRemoved())
				packagers.add(packager);
		return packagers;
	}

	private record InventoryKey(Level level, Object identity) {}

	private record ReservedRelease(BioPackagerBlockEntity packager, IItemHandler inventory, int slot, ItemStack box) {}
}
