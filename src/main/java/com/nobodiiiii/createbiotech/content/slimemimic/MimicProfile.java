package com.nobodiiiii.createbiotech.content.slimemimic;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Stable biological data plus an immutable render-only snapshot. Gameplay entities
 * receive only the small stable-data whitelist; preview entities may additionally load
 * the sanitized snapshot so paper boxes and surgical parts retain their exact appearance.
 */
public final class MimicProfile {
	private static final int CURRENT_VERSION = 2;
	private static final String VERSION_TAG = "Version";
	private static final String ENTITY_TYPE_TAG = "EntityType";
	private static final String STABLE_DATA_TAG = "StableData";
	private static final String PREVIEW_DATA_TAG = "PreviewData";
	private static final String BABY_TAG = "Baby";
	private static final String ATTRIBUTES_TAG = "attributes";
	private static final String VILLAGER_DATA_TAG = "VillagerData";
	private static final String VILLAGER_TYPE_TAG = "type";

	private static final Map<EntityType<?>, List<String>> STABLE_FIELDS = Map.ofEntries(
		entry(EntityType.AXOLOTL, List.of("Variant")),
		entry(EntityType.BOGGED, List.of("sheared")),
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
		entry(EntityType.TROPICAL_FISH, List.of("Variant")),
		entry(EntityType.WOLF, List.of("variant"))
	);

	private final ResourceLocation entityTypeId;
	private final CompoundTag stableData;
	private final CompoundTag previewData;
	@Nullable
	private final Boolean baby;
	/**
	 * Profiles are immutable and are used as render-cache keys several times per subject per frame,
	 * so the recursive tag hash is paid once here instead of on every lookup.
	 */
	private final int hash;

	private MimicProfile(ResourceLocation entityTypeId, CompoundTag stableData, CompoundTag previewData,
		@Nullable Boolean baby) {
		this.entityTypeId = entityTypeId;
		// Both callers hand over tags they just built: sanitizeSavedData starts from a new CompoundTag
		// and sanitizePreviewData from source.copy(). Copying again duplicated the whole entity NBT -
		// kilobytes for a villager - on every profile, including one per fragment on a mimic's death.
		this.stableData = stableData;
		this.previewData = previewData;
		this.baby = baby;
		this.hash = Objects.hash(entityTypeId, this.stableData, this.previewData, baby);
	}

	@Nullable
	public static MimicProfile capture(LivingEntity entity) {
		ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
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
		return new MimicProfile(entityTypeId, stableData, sanitizePreviewData(completeData), baby);
	}

	@Nullable
	public static MimicProfile load(CompoundTag tag) {
		int version = tag.getInt(VERSION_TAG);
		if ((version != 1 && version != CURRENT_VERSION)
			|| !tag.contains(ENTITY_TYPE_TAG, Tag.TAG_STRING))
			return null;

		ResourceLocation entityTypeId = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE_TAG));
		if (entityTypeId == null)
			return null;

		CompoundTag savedStableData = tag.contains(STABLE_DATA_TAG, Tag.TAG_COMPOUND)
			? tag.getCompound(STABLE_DATA_TAG) : new CompoundTag();
		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId)
			.orElse(null);
		CompoundTag stableData = sanitizeSavedData(entityType, savedStableData);
		CompoundTag previewData = version >= 2 && tag.contains(PREVIEW_DATA_TAG, Tag.TAG_COMPOUND)
			? sanitizePreviewData(tag.getCompound(PREVIEW_DATA_TAG)) : new CompoundTag();
		Boolean baby = tag.contains(BABY_TAG, Tag.TAG_BYTE) ? tag.getBoolean(BABY_TAG) : null;
		return new MimicProfile(entityTypeId, stableData, previewData, baby);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt(VERSION_TAG, CURRENT_VERSION);
		tag.putString(ENTITY_TYPE_TAG, entityTypeId.toString());
		if (!stableData.isEmpty())
			tag.put(STABLE_DATA_TAG, stableData.copy());
		if (!previewData.isEmpty())
			tag.put(PREVIEW_DATA_TAG, previewData.copy());
		if (baby != null)
			tag.putBoolean(BABY_TAG, baby);
		return tag;
	}

	public ResourceLocation entityTypeId() {
		return entityTypeId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof MimicProfile profile))
			return false;
		return hash == profile.hash
			&& entityTypeId.equals(profile.entityTypeId)
			&& stableData.equals(profile.stableData)
			&& previewData.equals(profile.previewData)
			&& Objects.equals(baby, profile.baby);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	/**
	 * Creates the source-model adapter used by client renderers. The returned entity is
	 * deliberately not added to the level and contains only the stable appearance data
	 * captured by this profile.
	 */
	@Nullable
	public LivingEntity createPreviewEntity(Level level) {
		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId)
			.orElse(null);
		if (entityType == null)
			return null;

		net.minecraft.world.entity.Entity created = entityType.create(level);
		if (!(created instanceof LivingEntity living))
			return null;

		if (!previewData.isEmpty()) {
			UUID previewUuid = living.getUUID();
			living.load(previewData.copy());
			living.setUUID(previewUuid);
		}
		apply(living);
		SlimeMimicHandler.setSlimeMimic(living, true);
		living.setYRot(0.0f);
		living.setXRot(0.0f);
		living.yRotO = 0.0f;
		living.xRotO = 0.0f;
		living.yBodyRot = 0.0f;
		living.yBodyRotO = 0.0f;
		living.yHeadRot = 0.0f;
		living.yHeadRotO = 0.0f;
		living.tickCount = 0;
		return living;
	}

	public boolean matches(@Nullable ResourceLocation expectedEntityTypeId) {
		return entityTypeId.equals(expectedEntityTypeId);
	}

	public void apply(LivingEntity entity) {
		ResourceLocation targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
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

	private static CompoundTag sanitizePreviewData(CompoundTag source) {
		CompoundTag preview = source.copy();
		// Equipment is an external render attachment, not part of the biological source model.
		// Keeping it here would replay the same held/armor model for every separately packed part.
		for (String field : List.of("UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air",
			"OnGround", "Invulnerable", "PortalCooldown", "Passengers", "Leash", "Health",
			"AbsorptionAmount", "HurtTime", "DeathTime", "HurtByTimestamp", "Brain", "attributes",
			"Attributes", "SleepingX", "SleepingY", "SleepingZ", "HandItems", "HandDropChances",
			"ArmorItems", "ArmorDropChances", "body_armor_item", "body_armor_drop_chance"))
			preview.remove(field);
		return preview;
	}
}
