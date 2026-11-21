package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Meals remain durable until their placement snapshot is ready. New kills append behind the in-flight batch. */
final class DevourGrowthQueue {
	record Meal(MimicProfile profile, float width, float height, UUID donor, UUID shellDonor, int shellCube, long seed) {
		boolean valid() {
			return profile != null && width > 0 && width <= SurgicalAssembly.MAX_BODY_SIZE
				&& height > 0 && height <= SurgicalAssembly.MAX_BODY_SIZE && shellCube >= 0 && shellCube < SurgicalAssembly.MAX_CUBES;
		}

		CompoundTag save() {
			var tag = new CompoundTag();
			tag.put("Profile", profile.save());
			tag.putFloat("Width", width);
			tag.putFloat("Height", height);
			tag.putUUID("Donor", donor);
			tag.putUUID("Shell", shellDonor);
			tag.putInt("Cube", shellCube);
			tag.putLong("Seed", seed);
			return tag;
		}
	}

	private record Prepared(SurgicalAssembly assembly, CompoundTag encoded) {}
	private final List<Meal> meals = new ArrayList<>();
	private CompletableFuture<Prepared> preparing;
	private int batchSize;
	private long retryAfter;

	void add(Meal meal, int existingSources) {
		if (existingSources + meals.size() < SurgicalAssembly.MAX_SOURCES) meals.add(meal);
	}

	ListTag save() {
		var list = new ListTag();
		for (var meal : meals) list.add(meal.save());
		return list;
	}

	static DevourGrowthQueue load(ListTag list) {
		var queue = new DevourGrowthQueue();
		for (int i = 0; i < Math.min(list.size(), SurgicalAssembly.MAX_SOURCES); i++) {
			var tag = list.getCompound(i);
			if (!tag.hasUUID("Donor") || !tag.hasUUID("Shell") || !tag.contains("Cube", Tag.TAG_INT)
				|| !tag.contains("Seed", Tag.TAG_LONG)) continue;
			var meal = new Meal(MimicProfile.load(tag.getCompound("Profile")), tag.getFloat("Width"), tag.getFloat("Height"),
				tag.getUUID("Donor"), tag.getUUID("Shell"), tag.getInt("Cube"), tag.getLong("Seed"));
			if (meal.valid()) queue.meals.add(meal);
		}
		return queue;
	}

	static SurgicalAssembly plan(SurgicalAssembly base, List<Meal> batch) {
		for (var meal : batch) {
			var grown = DevourGrowth.append(base, meal);
			if (grown != null) base = grown;
		}
		return base;
	}

	SurgicalAssembly forPlacement(SurgicalAssembly base) {
		return plan(base, meals);
	}

	void invalidate() {
		if (preparing != null) preparing.cancel(false);
		preparing = null;
		retryAfter = 0;
	}

	/** Runs on the owning server tick. Workers never hold the entity, level or live queue. */
	boolean tick(SlimeBionicEntity body) {
		if (meals.isEmpty()) return true;
		if (body.getPendingGrowth() != null || body.tickCount < retryAfter) return false;
		if (preparing == null) {
			var base = body.getAssembly();
			if (base == null) return false;
			var batch = List.copyOf(meals);
			batchSize = batch.size();
			preparing = CompletableFuture.supplyAsync(() -> {
				var assembly = plan(base, batch);
				return assembly == base ? null : new Prepared(assembly, assembly.save());
			}, Util.backgroundExecutor());
		} else if (preparing.isDone()) {
			try {
				var ready = preparing.join();
				if (ready != null) body.setPendingGrowth(ready.assembly, ready.encoded);
				meals.subList(0, batchSize).clear();
			} catch (java.util.concurrent.CompletionException failure) {
				LogUtils.getLogger().warn("Could not prepare devour growth for {}", body.getUUID(), failure);
				retryAfter = (long) body.tickCount + 100;
			} finally {
				preparing = null;
			}
		}
		return meals.isEmpty();
	}
}
