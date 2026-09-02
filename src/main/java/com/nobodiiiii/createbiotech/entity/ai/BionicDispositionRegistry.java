package com.nobodiiiii.createbiotech.entity.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * Independent head disposition detection. Vanilla and conforming modded mobs are classified from
 * their existing {@link NeutralMob}/{@link Enemy} contracts; data-pack entries override exceptional
 * entity types.
 */
public final class BionicDispositionRegistry {
	private static volatile Map<ResourceLocation, BionicDisposition> DATA = Map.of();
	private static final Map<ResourceLocation, BionicDisposition> DETECTED = new ConcurrentHashMap<>();

	private BionicDispositionRegistry() {}

	static void replaceData(Map<ResourceLocation, BionicDisposition> data) {
		DATA = Map.copyOf(data);
	}

	public static BionicDisposition get(MimicProfile profile, Level level) {
		return profile == null ? BionicDisposition.NEUTRAL : get(profile.entityTypeId(), level);
	}

	public static BionicDisposition get(ResourceLocation entityTypeId, Level level) {
		if (entityTypeId == null || level == null)
			return BionicDisposition.NEUTRAL;
		BionicDisposition override = DATA.get(entityTypeId);
		return override != null ? override
			: DETECTED.computeIfAbsent(entityTypeId, id -> detect(id, level));
	}

	public static BionicDisposition get(LivingEntity entity) {
		if (entity == null)
			return BionicDisposition.NEUTRAL;
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		BionicDisposition override = id == null ? null : DATA.get(id);
		if (override != null)
			return override;
		if (entity instanceof NeutralMob)
			return BionicDisposition.NEUTRAL;
		if (entity instanceof Enemy)
			return BionicDisposition.HOSTILE;
		return BionicDisposition.FRIENDLY;
	}

	private static BionicDisposition detect(ResourceLocation entityTypeId, Level level) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElse(null);
		if (type == null)
			return BionicDisposition.NEUTRAL;
		Entity created = type.create(level);
		return created instanceof LivingEntity living ? get(living) : BionicDisposition.NEUTRAL;
	}
}
