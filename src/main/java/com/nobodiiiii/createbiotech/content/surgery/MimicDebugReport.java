package com.nobodiiiii.createbiotech.content.surgery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DisplayEntry;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

/** Human-readable, versioned snapshots; all data is captured on the authoritative server thread. */
public final class MimicDebugReport {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
		.serializeNulls().create();
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
		.withZone(ZoneOffset.UTC);

	private MimicDebugReport() {}

	public static Path write(ServerLevel level, LivingEntity entity) throws IOException {
		Instant captured = Instant.now();
		JsonObject report = capture(level, entity, captured);
		Path directory = level.getServer().getWorldPath(LevelResource.ROOT)
			.resolve("create_biotech/diagnostics/mimics").toAbsolutePath().normalize();
		Files.createDirectories(directory);
		Path file = Files.createTempFile(directory, FILE_TIME.format(captured) + "_" + entity.getUUID() + "_", ".json");
		try {
			Files.writeString(file, GSON.toJson(report) + "\n", StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException exception) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw exception;
		}
		return file;
	}

	private static JsonObject capture(ServerLevel level, LivingEntity entity, Instant captured) {
		BionicTraitSet resolvedTraits = entity instanceof SlimeBionicEntity bionic
			? bionic.getBionicTraits() : BionicTraitSet.EMPTY;
		JsonObject report = new JsonObject();
		report.addProperty("format", "create_biotech:mimic_diagnostic");
		report.addProperty("schema_version", 1);
		report.addProperty("captured_at_utc", captured.toString());
		report.addProperty("minecraft_version", SharedConstants.getCurrentVersion().getName());
		report.addProperty("world", level.getServer().getWorldData().getLevelName());
		report.addProperty("dimension", level.dimension().location().toString());
		report.addProperty("game_time", level.getGameTime());
		report.addProperty("trait_registry_generation", BionicTraitRegistry.generation());
		report.add("enabled_data_packs", strings(level.getServer().getPackRepository().getSelectedIds()));
		JsonObject versions = new JsonObject();
		ModList.get().getMods().stream().sorted(Comparator.comparing(mod -> mod.getModId()))
			.forEach(mod -> versions.addProperty(mod.getModId(), mod.getVersion().toString()));
		report.add("mod_versions", versions);

		JsonObject identity = new JsonObject();
		identity.addProperty("uuid", entity.getUUID().toString());
		identity.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		identity.addProperty("name", entity.getName().getString());
		identity.addProperty("x", entity.getX());
		identity.addProperty("y", entity.getY());
		identity.addProperty("z", entity.getZ());
		identity.addProperty("health", entity.getHealth());
		identity.addProperty("max_health", entity.getMaxHealth());
		identity.addProperty("slime_mimic", SlimeMimicHandler.isSlimeMimic(entity));
		identity.addProperty("haunted_mimic", SlimeMimicHandler.isHauntedMimic(entity));
		identity.add("bounding_box", GSON.toJsonTree(entity.getBoundingBox()));
		identity.add("eye_position", GSON.toJsonTree(entity.getEyePosition()));
		identity.add("view_vector", GSON.toJsonTree(entity.getViewVector(1)));
		report.add("entity", identity);

		CompoundTag saved = entity.saveWithoutId(new CompoundTag());
		// SNBT preserves numeric types, bitsets and UUID arrays exactly, including third-party NBT.
		report.addProperty("entity_snbt", saved.toString());
		if (entity instanceof SlimeBionicEntity bionic) {
			JsonObject attachment = new JsonObject();
			attachment.addProperty("attached", bionic.isBlockAttached());
			attachment.addProperty("face", bionic.getBlockAttachmentFace().getSerializedName());
			var cell = bionic.getBlockAttachmentCell();
			attachment.add("cell", cell == null ? JsonNull.INSTANCE : GSON.toJsonTree(new int[] {
				cell.getX(), cell.getY(), cell.getZ() }));
			attachment.add("carrier", GSON.toJsonTree(bionic.getBlockAttachment().carrier()));
			attachment.add("rotation", GSON.toJsonTree(bionic.blockAttachmentRotation()));
			attachment.addProperty("suffocating", bionic.isInWall());
			report.add("block_attachment", attachment);
			SurgicalAssembly assembly = bionic.getAssembly();
			report.addProperty("assembly_status", assembly == null ? "invalid_or_missing" : "assembled");
			report.add("assembly", saved.contains("SurgicalAssembly", Tag.TAG_COMPOUND)
				? NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, saved.getCompound("SurgicalAssembly")) : JsonNull.INSTANCE);
			if (assembly != null)
				report.add("assembly_summary", assemblySummary(assembly));
			report.add("traits", traits(resolvedTraits));
		} else {
			report.addProperty("assembly_status", "unassembled_mimic");
			report.add("assembly", JsonNull.INSTANCE);
			MimicProfile profile = MimicProfile.capture(entity);
			if (profile != null)
				report.add("mimic_profile", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, profile.save()));
		}
		return report;
	}

	private static JsonObject assemblySummary(SurgicalAssembly assembly) {
		JsonObject summary = new JsonObject();
		summary.addProperty("source_count", assembly.sources().size());
		summary.addProperty("present_cube_count", assembly.sources().stream()
			.mapToInt(source -> source.presentCubes().cardinality()).sum());
		summary.addProperty("connected_cube_count", assembly.connectedMembers().size());
		summary.addProperty("glue_joint_count", assembly.joints().size());
		summary.addProperty("combination_count", assembly.combinations().size());
		summary.addProperty("stored_limb_count", assembly.limbs().size());
		summary.add("effective_limbs", GSON.toJsonTree(assembly.effectiveLimbs()));
		summary.addProperty("ready_for_entity", assembly.isReadyForEntity());
		JsonArray sources = new JsonArray();
		for (int index = 0; index < assembly.sources().size(); index++) {
			SurgicalAssembly.Source source = assembly.sources().get(index);
			JsonObject entry = new JsonObject();
			entry.addProperty("source", index);
			entry.addProperty("donor_uuid", source.donorId().toString());
			entry.addProperty("entity_type", source.profile().entityTypeId().toString());
			entry.addProperty("original_cube_count", source.cubeCount());
			entry.add("present_cube_ids", GSON.toJsonTree(source.presentCubes().stream().toArray()));
			entry.add("head_cube_ids", GSON.toJsonTree(source.headCubes().stream().toArray()));
			entry.add("original_head_cube_ids", GSON.toJsonTree(source.originalHeadCubes().stream().toArray()));
			entry.addProperty("original_head_known", source.originalHeadKnown());
			sources.add(entry);
		}
		summary.add("sources", sources);
		return summary;
	}

	private static JsonObject traits(BionicTraitSet traits) {
		JsonObject entries = new JsonObject();
		for (BionicTrait trait : traits.definitions()) {
			var result = traits.result(trait);
			JsonObject entry = new JsonObject();
			entry.addProperty("present", result.present());
			entry.addProperty("active", result.active());
			entry.addProperty("inactive_reason", result.inactiveReason() == null ? null : result.inactiveReason().name());
			JsonArray missingParts = new JsonArray();
			for (var alternative : result.missingParts()) {
				JsonArray parts = new JsonArray();
				for (var part : alternative) {
					JsonObject missing = new JsonObject();
					missing.addProperty("entity_type", part.species().toString());
					missing.addProperty("role", part.role().name());
					parts.add(missing);
				}
				missingParts.add(parts);
			}
			entry.add("missing_part_alternatives", missingParts);
			entry.addProperty("coverage", result.coverage());
			entry.addProperty("strength", result.strength());
			entry.addProperty("value", result.value());
			entry.add("bindings", members(result.bindings()));
			entry.add("members", members(result.members()));
			entry.add("source_types", strings(result.sourceTypes()));
			entry.add("immune_effects", strings(result.immuneEffects()));
			entry.add("deterrence_targets", strings(result.deterrenceTargets()));
			JsonArray displayEntries = new JsonArray();
			result.displayEntries().stream().sorted(Comparator
				.comparing(DisplayEntry::kind).thenComparing(DisplayEntry::id))
				.forEach(detail -> {
					JsonObject display = new JsonObject();
					display.addProperty("kind", detail.kind().name());
					display.addProperty("id", detail.id().toString());
					display.addProperty("active", detail.active());
					display.addProperty("inactive_reason", detail.inactiveReason() == null ? null : detail.inactiveReason().name());
					display.add("missing_part_alternatives", GSON.toJsonTree(detail.missingParts().stream()
						.map(parts -> parts.stream().map(part -> Map.of("entity_type", part.species().toString(), "role", part.role().name())).toList())
						.toList()));
					displayEntries.add(display);
				});
			entry.add("display_entries", displayEntries);
			JsonArray memberValues = new JsonArray();
			result.memberValues().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(MEMBER_ORDER))
				.forEach(value -> {
					JsonObject member = member(value.getKey());
					member.addProperty("value", value.getValue());
					memberValues.add(member);
				});
			entry.add("member_values", memberValues);
			JsonObject variants = new JsonObject();
			result.subtraits().entrySet().stream().sorted(Comparator.comparing(v -> v.getKey().id().toString()))
				.forEach(variant -> {
					JsonObject details = new JsonObject();
					details.add("members", members(variant.getValue()));
					details.add("parameters", GSON.toJsonTree(variant.getKey().parameters()));
					variants.add(variant.getKey().id().toString(), details);
				});
			entry.add("subtraits", variants);
			JsonArray effects = new JsonArray();
			result.attackEffects().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().effect().toString()))
				.forEach(effect -> {
					var definition = effect.getKey();
					JsonObject details = new JsonObject();
					details.addProperty("effect", definition.effect().toString());
					details.addProperty("duration_ticks", definition.durationTicks());
					details.addProperty("amplifier", definition.amplifier());
					details.addProperty("chance", definition.chance());
					details.addProperty("unarmed_only", definition.unarmedOnly());
					details.add("members", members(effect.getValue()));
					effects.add(details);
				});
			entry.add("attack_effects", effects);
			entries.add(trait.id().toString(), entry);
		}
		return entries;
	}

	private static final Comparator<SurgicalAssembly.CombinationMember> MEMBER_ORDER =
		Comparator.comparingInt(SurgicalAssembly.CombinationMember::source)
			.thenComparingInt(SurgicalAssembly.CombinationMember::cube);

	private static JsonObject member(SurgicalAssembly.CombinationMember member) {
		JsonObject entry = new JsonObject();
		entry.addProperty("source", member.source());
		entry.addProperty("cube", member.cube());
		return entry;
	}

	private static JsonArray members(Collection<SurgicalAssembly.CombinationMember> members) {
		JsonArray entries = new JsonArray();
		members.stream().sorted(MEMBER_ORDER).forEach(member -> entries.add(member(member)));
		return entries;
	}

	private static JsonArray strings(Collection<?> values) {
		JsonArray entries = new JsonArray();
		values.stream().map(Object::toString).sorted().forEach(entries::add);
		return entries;
	}
}
