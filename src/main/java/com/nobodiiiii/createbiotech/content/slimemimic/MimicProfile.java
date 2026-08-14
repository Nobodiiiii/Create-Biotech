package com.nobodiiiii.createbiotech.content.slimemimic;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A deliberately small snapshot of stable biological and appearance data.
 * Identity, inventory, ownership, AI memories, health and other gameplay state
 * are never copied into the profile.
 */
public final class MimicProfile {
	private static final int CURRENT_VERSION = 1;
	private static final String VERSION_TAG = "Version";
	private static final String ENTITY_TYPE_TAG = "EntityType";
	private static final String STABLE_DATA_TAG = "StableData";
	private static final String BABY_TAG = "Baby";
	private static final String ATTRIBUTES_TAG = "Attributes";
	private static final String VILLAGER_DATA_TAG = "VillagerData";
	private static final String VILLAGER_TYPE_TAG = "type";

	private static final Map<EntityType<?>, List<String>> STABLE_FIELDS = Map.ofEntries(
		entry(EntityType.AXOLOTL, List.of("Variant")),
		entry(EntityType.CAT, List.of("variant")),
		entry(EntityType.FOX, List.of("Type")),
		entry(EntityType.FROG, List.of("variant")),
		entry(EntityType.GOAT, List.of("IsScreamingGoat", "HasLeftHorn", "HasRightHorn")),
		entry(EntityType.HORSE, List.of("Variant")),
		entry(EntityType.LLAMA, List.of("Variant", "Strength")),
		entry(EntityType.MAGMA_CUBE, List.of("Size")),
		entry(EntityType.MOOSHROOM, List.of("Type")),
		entry(EntityType.PANDA, List.of("MainGene", "HiddenGene")),
		entry(EntityType.PARROT, List.of("Variant")),
		entry(EntityType.PHANTOM, List.of("Size")),
		entry(EntityType.RABBIT, List.of("RabbitType")),
		entry(EntityType.SHEEP, List.of("Color")),
		entry(EntityType.SHULKER, List.of("Color")),
		entry(EntityType.SLIME, List.of("Size")),
		entry(EntityType.SNOW_GOLEM, List.of("Pumpkin")),
		entry(EntityType.TADPOLE, List.of("Age")),
		entry(EntityType.TROPICAL_FISH, List.of("Variant"))
	);

	private final ResourceLocation entityTypeId;
	private final CompoundTag stableData;
	@Nullable
	private final Boolean baby;

	private MimicProfile(ResourceLocation entityTypeId, CompoundTag stableData, @Nullable Boolean baby) {
		this.entityTypeId = entityTypeId;
		this.stableData = stableData.copy();
		this.baby = baby;
	}

	@Nullable
	public static MimicProfile capture(LivingEntity entity) {
		ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		if (entityTypeId == null)
			return null;

		CompoundTag completeData = entity.saveWithoutId(new CompoundTag());
		CompoundTag stableData = new CompoundTag();

		if (entity instanceof AgeableMob) {
			copyField(completeData, stableData, "Age");
			copyField(completeData, stableData, "ForcedAge");
		}

		for (String field : STABLE_FIELDS.getOrDefault(entity.getType(), List.of()))
			copyField(completeData, stableData, field);

		if (entity.getType() == EntityType.VILLAGER || entity.getType() == EntityType.ZOMBIE_VILLAGER)
			copyVillagerType(completeData, stableData);

		Boolean baby = entity instanceof Mob && !(entity instanceof AgeableMob) ? entity.isBaby() : null;
		return new MimicProfile(entityTypeId, stableData, baby);
	}

	@Nullable
	public static MimicProfile load(CompoundTag tag) {
		if (tag.getInt(VERSION_TAG) != CURRENT_VERSION || !tag.contains(ENTITY_TYPE_TAG, Tag.TAG_STRING))
			return null;

		ResourceLocation entityTypeId = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE_TAG));
		if (entityTypeId == null)
			return null;

		CompoundTag savedStableData = tag.contains(STABLE_DATA_TAG, Tag.TAG_COMPOUND)
			? tag.getCompound(STABLE_DATA_TAG) : new CompoundTag();
		EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
		CompoundTag stableData = sanitizeSavedData(entityType, savedStableData);
		Boolean baby = tag.contains(BABY_TAG, Tag.TAG_BYTE) ? tag.getBoolean(BABY_TAG) : null;
		return new MimicProfile(entityTypeId, stableData, baby);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt(VERSION_TAG, CURRENT_VERSION);
		tag.putString(ENTITY_TYPE_TAG, entityTypeId.toString());
		if (!stableData.isEmpty())
			tag.put(STABLE_DATA_TAG, stableData.copy());
		if (baby != null)
			tag.putBoolean(BABY_TAG, baby);
		return tag;
	}

	public boolean matches(@Nullable ResourceLocation expectedEntityTypeId) {
		return entityTypeId.equals(expectedEntityTypeId);
	}

	public void apply(LivingEntity entity) {
		ResourceLocation targetTypeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		if (!matches(targetTypeId))
			return;

		if (!stableData.isEmpty()) {
			CompoundTag mergedData = entity.saveWithoutId(new CompoundTag());
			UUID originalUuid = entity.getUUID();

			// Do not reload the pre-profile attribute snapshot. Some stable features,
			// notably slime size, intentionally recalculate their base attributes.
			mergedData.remove(ATTRIBUTES_TAG);
			mergedData.merge(stableData);
			entity.load(mergedData);
			entity.setUUID(originalUuid);
		}

		if (baby != null && entity instanceof Mob mob && !(entity instanceof AgeableMob))
			mob.setBaby(baby);

		// A cultivated entity is new life, not a copy of the source's injuries.
		entity.setHealth(entity.getMaxHealth());
	}

	private static void copyField(CompoundTag source, CompoundTag target, String field) {
		Tag value = source.get(field);
		if (value != null)
			target.put(field, value.copy());
	}

	private static void copyVillagerType(CompoundTag source, CompoundTag target) {
		if (!source.contains(VILLAGER_DATA_TAG, Tag.TAG_COMPOUND))
			return;

		CompoundTag sourceVillagerData = source.getCompound(VILLAGER_DATA_TAG);
		Tag villagerType = sourceVillagerData.get(VILLAGER_TYPE_TAG);
		if (villagerType == null)
			return;

		CompoundTag stableVillagerData = new CompoundTag();
		stableVillagerData.put(VILLAGER_TYPE_TAG, villagerType.copy());
		target.put(VILLAGER_DATA_TAG, stableVillagerData);
	}

	private static CompoundTag sanitizeSavedData(@Nullable EntityType<?> entityType, CompoundTag savedData) {
		CompoundTag sanitizedData = new CompoundTag();
		if (entityType == null)
			return sanitizedData;

		// Age is shared by AgeableMob and tadpoles. Unsupported entity classes
		// simply ignore these keys when the filtered data is merged back in.
		copyField(savedData, sanitizedData, "Age");
		copyField(savedData, sanitizedData, "ForcedAge");
		for (String field : STABLE_FIELDS.getOrDefault(entityType, List.of()))
			copyField(savedData, sanitizedData, field);

		if (entityType == EntityType.VILLAGER || entityType == EntityType.ZOMBIE_VILLAGER)
			copyVillagerType(savedData, sanitizedData);
		return sanitizedData;
	}
}
