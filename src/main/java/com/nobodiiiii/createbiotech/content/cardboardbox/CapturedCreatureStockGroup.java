package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class CapturedCreatureStockGroup extends BigItemStack {
	private final Key key;
	private final ItemStack preview;
	private final boolean expanded;

	private CapturedCreatureStockGroup(Key key, List<BigItemStack> members, boolean expanded) {
		// Empty stack keeps the group out of item matching. The preview supplies its icon only.
		super(ItemStack.EMPTY, (int) Math.min(INF, members.stream().mapToLong(member -> member.count).sum()));
		this.key = key;
		this.preview = members.getFirst().stack;
		this.expanded = expanded;
	}

	public static List<BigItemStack> group(List<BigItemStack> entries, int category, Set<Key> expandedGroups) {
		Map<EntityType<?>, List<BigItemStack>> byType = new HashMap<>();
		for (BigItemStack entry : entries) {
			EntityType<?> type = getCreatureType(entry);
			if (type != null)
				byType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(entry);
		}
		if (byType.values().stream().noneMatch(members -> members.size() > 1))
			return entries;

		List<BigItemStack> result = new ArrayList<>(entries.size());
		Set<EntityType<?>> emittedGroups = new HashSet<>();
		for (BigItemStack entry : entries) {
			EntityType<?> type = getCreatureType(entry);
			List<BigItemStack> members = byType.get(type);
			if (members == null || members.size() == 1) {
				result.add(entry);
			} else if (emittedGroups.add(type)) {
				Key key = new Key(category, type);
				boolean expanded = expandedGroups.contains(key);
				result.add(new CapturedCreatureStockGroup(key, members, expanded));
				if (expanded)
					result.addAll(members);
			}
		}
		return result;
	}

	@Nullable
	private static EntityType<?> getCreatureType(BigItemStack entry) {
		return entry.count > 0 && CapturedEntityBoxItem.isBox(entry.stack)
			? CapturedEntityBoxHelper.getCapturedEntityType(entry.stack) : null;
	}

	public Key key() {
		return key;
	}

	public ItemStack preview() {
		return preview;
	}

	public boolean expanded() {
		return expanded;
	}

	public record Key(int category, EntityType<?> type) {}
}
