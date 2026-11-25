package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.OrderEntry;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class ComputerLogistics {
	private static final int ORDER_COOLDOWN = 20;
	private static final int MAX_ORDER_ITEMS = PackageItem.SLOTS * 64;
	// Create's performPackageRequests calls each packager at most this many times.
	private static final int MAX_PACKAGES_PER_PACKAGER = 100;

	@Nullable
	private UUID network;
	@Nullable
	private UUID operator;
	private long nextOrderTick;

	boolean isBound() {
		return network != null;
	}

	boolean mayEdit(Player player) {
		return network == null || Create.LOGISTICS.mayInteract(network, player);
	}

	boolean bind(@Nullable UUID network) {
		if (Objects.equals(this.network, network))
			return false;
		this.network = network;
		return true;
	}

	void setOperator(@Nullable UUID operator) {
		this.operator = operator;
	}

	long count(List<String> filters) {
		Set<Item> items = new HashSet<>();
		Set<TagKey<Item>> tags = new HashSet<>();
		for (String filter : filters) {
			if (filter.startsWith("#")) {
				ResourceLocation id = ResourceLocation.tryParse(filter.substring(1));
				if (id != null)
					tags.add(TagKey.create(Registries.ITEM, id));
			} else {
				Item item = item(filter);
				if (item != null)
					items.add(item);
			}
		}
		if (items.isEmpty() && tags.isEmpty())
			return 0;
		var stock = summary().getItemMap();
		if (tags.isEmpty())
			return items.stream().flatMap(item -> stock.getOrDefault(item, List.of()).stream())
				.mapToLong(ComputerLogistics::count).sum();
		return stock.values().stream().flatMap(List::stream)
			.filter(entry -> items.contains(entry.stack.getItem()) || tags.stream().anyMatch(entry.stack::is))
			.mapToLong(ComputerLogistics::count).sum();
	}

	long total() {
		return summary().getItemMap().values().stream().flatMap(List::stream)
			.mapToLong(ComputerLogistics::count).sum();
	}

	int types() {
		return (int) summary().getItemMap().values().stream()
			.filter(entries -> entries.stream().anyMatch(entry -> entry.count > 0)).count();
	}

	boolean order(String address, List<OrderEntry> items, long now, Runnable markChanged) {
		if (address.isBlank() || now < nextOrderTick || !mayUseNetwork())
			return false;
		List<BigItemStack> stacks = orderItems(items);
		if (stacks.isEmpty())
			return false;

		nextOrderTick = now + ORDER_COOLDOWN;
		markChanged.run();
		var requests = LogisticsManager.findPackagersForRequest(network, PackageOrderWithCrafts.simple(stacks), null, address);
		int requested = stacks.stream().mapToInt(entry -> entry.count).sum();
		int available = requests.values().stream().mapToInt(PackagingRequest::getCount).sum();
		if (available != requested || requests.keySet().stream().anyMatch(packager -> packager.isTooBusyFor(RequestType.REDSTONE)))
			return false;
		for (var requestsAtPackager : requests.asMap().values()) {
			int packages = 0;
			for (PackagingRequest request : requestsAtPackager) {
				int itemsPerPackage = request.item().canFitInsideContainerItems() ? PackageItem.SLOTS : 1;
				packages += Math.ceilDiv(request.getCount(), itemsPerPackage);
			}
			if (packages > MAX_PACKAGES_PER_PACKAGER)
				return false;
		}
		LogisticsManager.performPackageRequests(requests);
		return requests.values().stream().allMatch(PackagingRequest::isEmpty);
	}

	private InventorySummary summary() {
		return mayUseNetwork() ? LogisticsManager.getSummaryOfNetwork(network, true) : InventorySummary.EMPTY;
	}

	private boolean mayUseNetwork() {
		LogisticsNetwork linked = network == null ? null : Create.LOGISTICS.logisticsNetworks.get(network);
		return linked != null && (!linked.locked || linked.owner == null || linked.owner.equals(operator));
	}

	private static long count(BigItemStack entry) {
		return Math.max(0, entry.count);
	}

	@Nullable
	private static Item item(String text) {
		ResourceLocation id = ResourceLocation.tryParse(text.strip());
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	private static List<BigItemStack> orderItems(List<OrderEntry> entries) {
		Map<Item, Integer> counts = new LinkedHashMap<>();
		int total = 0;
		for (OrderEntry entry : entries) {
			Item item = item(entry.item());
			long amount = entry.count();
			if (item == null || item.getDefaultInstance().isEmpty() || amount <= 0 || amount > MAX_ORDER_ITEMS - total)
				return List.of();
			int count = (int) amount;
			total += count;
			counts.merge(item, count, Integer::sum);
		}
		List<BigItemStack> items = new ArrayList<>(counts.size());
		counts.forEach((item, amount) -> items.add(new BigItemStack(item.getDefaultInstance(), amount)));
		return items;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		if (network != null)
			tag.putUUID("Network", network);
		if (operator != null)
			tag.putUUID("Operator", operator);
		tag.putLong("NextOrderTick", nextOrderTick);
		return tag;
	}

	void load(CompoundTag tag) {
		network = tag.hasUUID("Network") ? tag.getUUID("Network") : null;
		operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
		nextOrderTick = tag.getLong("NextOrderTick");
	}
}
