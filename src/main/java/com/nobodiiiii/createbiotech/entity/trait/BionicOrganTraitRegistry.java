package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Per-trait donor rules, evaluated as local coverage of the region made by the trait's roles. */
public final class BionicOrganTraitRegistry extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final BionicOrganTraitRegistry INSTANCE = new BionicOrganTraitRegistry();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile Map<BionicOrganTrait, Rule> RULES = defaults();
	private static boolean registered;

	private BionicOrganTraitRegistry() { super(new Gson(), "bionic_organ_traits"); }
	public static void register() {
		if (registered)
			return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicOrganTraitRegistry::onTagsUpdated);
	}
	private static void onTagsUpdated(TagsUpdatedEvent event) { GENERATION.incrementAndGet(); }
	public static long generation() {
		return GENERATION.get();
	}

	/** Whether the wings' own tissue volume can lift the whole body. */
	public static boolean liftsBody(@Nullable SurgicalAssembly assembly,
		Set<SurgicalAssembly.CombinationMember> wings) {
		return BionicTraits.ENABLED
			&& assembly != null && assembly.hasBodyVolume()
			&& assembly.bodyVolume() <= RULES.get(BionicOrganTrait.WING_FLIGHT).maxLoadPerVolume
				* BionicTissue.of(assembly).weight(wings);
	}
	public static double parameter(BionicOrganTrait trait, String key, double fallback) {
		Rule rule = RULES.get(trait);
		double value = rule == null ? fallback : rule.parameters.getOrDefault(key, fallback);
		if (key.equals("chance") || key.endsWith("multiplier"))
			return Math.min(1.0d, value);
		return Math.min(key.endsWith("duration_ticks") ? 12000.0d : 100.0d, value);
	}

	public static BionicOrganTraits resolve(@Nullable SurgicalAssembly assembly) {
		if (!BionicTraits.ENABLED
			|| assembly == null || assembly.sources().isEmpty())
			return BionicOrganTraits.EMPTY;
		EnumMap<BionicOrganTrait, Set<SurgicalAssembly.CombinationMember>> resolved =
			new EnumMap<>(BionicOrganTrait.class);
		evaluate(assembly).forEach((trait, coverage) -> {
			// This entity has no projectile firing path yet; an inherited arrow effect would be inert.
			if (trait != BionicOrganTrait.RANGED_EFFECT
				&& coverage.reaches(RULES.get(trait).minCoverage)
				&& carriesBody(trait, assembly, coverage.carrierVolume()))
				resolved.put(trait, coverage.members());
		});
		return new BionicOrganTraits(resolved);
	}

	/** Local coverage of every organ ability that at least one source's species has. */
	private static Map<BionicOrganTrait, Coverage> evaluate(SurgicalAssembly assembly) {
		BionicTissue tissue = BionicTissue.of(assembly);
		EnumMap<BionicOrganTrait, Coverage> result = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			Rule rule = RULES.get(trait);
			Map<Integer, Map<BionicAnatomyRole, BitSet>> carriers = new HashMap<>();
			for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++) {
				if (rule == null || !matchesDonor(trait, rule, tissue.source(sourceId).profile()))
					continue;
				EnumMap<BionicAnatomyRole, BitSet> byRole = new EnumMap<>(BionicAnatomyRole.class);
				for (BionicAnatomyRole role : trait.roles())
					byRole.put(role, carrierCubes(trait, tissue, sourceId, role));
				carriers.put(sourceId, byRole);
			}
			if (carriers.isEmpty())
				continue;
			List<BionicTissue.Share> shares = new ArrayList<>();
			for (BionicAnatomyRole[] region : regions(trait))
				shares.add(tissue.share(region, sourceId -> {
					BitSet cubes = new BitSet();
					Map<BionicAnatomyRole, BitSet> byRole = carriers.get(sourceId);
					if (byRole != null)
						for (BionicAnatomyRole role : region)
							cubes.or(byRole.get(role));
					return cubes;
				}));
			result.put(trait, new Coverage(shares));
		}
		return result;
	}

	/** Flight needs lift on both sides; every other organ is one region over all of its roles. */
	private static BionicAnatomyRole[][] regions(BionicOrganTrait trait) {
		return trait == BionicOrganTrait.WING_FLIGHT
			? new BionicAnatomyRole[][] {
				{ BionicAnatomyRole.LEFT_WING }, { BionicAnatomyRole.RIGHT_WING } }
			: new BionicAnatomyRole[][] { trait.roles() };
	}

	private static BitSet carrierCubes(BionicOrganTrait trait, BionicTissue tissue, int sourceId,
		BionicAnatomyRole role) {
		SurgicalAssembly.Source source = tissue.source(sourceId);
		return tissue.carriers(sourceId, role, BionicTraitCarrierRegistry.organCubes(trait,
			source.profile().entityTypeId(), source.anatomy().parts(), source.cubeCount(), role));
	}

	/**
	 * Leg abilities also need enough grounded legs, and enough carrier leg volume for the body's
	 * load.
	 */
	private static boolean carriesBody(BionicOrganTrait trait, SurgicalAssembly assembly,
		double carrierVolume) {
		if (!requiresLeg(trait))
			return true;
		int requiredLegs = (int) parameter(trait, "min_legs",
			trait == BionicOrganTrait.AGILE_LANDING
				|| trait == BionicOrganTrait.WALL_CLIMB ? 2.0d : 1.0d);
		int groundedLegs = assembly.bodyBounds() == null ? 0
			: assembly.bodyBounds().groundedLegCount();
		return groundedLegs >= requiredLegs && carrierVolume > 0.0d
			&& !(assembly.hasBodyVolume() && assembly.bodyVolume() > carrierVolume
				* parameter(trait, "max_body_volume_per_leg_volume", 32.0d));
	}

	private static boolean requiresLeg(BionicOrganTrait trait) {
		return switch (trait) {
		case AGILE_LANDING, FALL_REDUCTION, POWDER_SNOW_WALK, LAVA_WALK, WALL_CLIMB -> true;
		default -> false;
		};
	}

	/** The trait's share of each region it needs. */
	private record Coverage(List<BionicTissue.Share> regions) {
		private Coverage {
			regions = List.copyOf(regions);
		}

		private boolean reaches(double minimum) {
			return regions.stream().allMatch(region -> region.reaches(minimum));
		}

		private boolean misplaced() {
			return regions.stream().anyMatch(BionicTissue.Share::misplaced);
		}

		private double carrierVolume() {
			return regions.stream().mapToDouble(BionicTissue.Share::carrierVolume).sum();
		}

		private Set<SurgicalAssembly.CombinationMember> members() {
			Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
			regions.forEach(region -> members.addAll(region.members()));
			return members;
		}
	}

	public static Set<BionicOrganTrait> donorFacts(MimicProfile profile) {
		if (!BionicTraits.ENABLED)
			return Set.of();
		EnumMap<BionicOrganTrait, Boolean> facts = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			Rule rule = RULES.get(trait);
			if (rule != null && matchesDonor(trait, rule, profile))
				facts.put(trait, true);
		}
		return facts.isEmpty() ? Set.of() : Set.copyOf(facts.keySet());
	}

	public enum InactiveReason {
		INSUFFICIENT_COVERAGE, WRONG_SLOT, BODY_MEASUREMENT_UNAVAILABLE, PURPOSE_MISMATCH,
		NO_RANGED_ATTACK
	}

	/** Explains why a donor fact whose carrier organ is present is absent from the abilities. */
	public static Map<BionicOrganTrait, InactiveReason> inactiveReasons(
		@Nullable SurgicalAssembly assembly) {
		if (!BionicTraits.ENABLED || assembly == null)
			return Map.of();
		EnumMap<BionicOrganTrait, InactiveReason> reasons =
			new EnumMap<>(BionicOrganTrait.class);
		for (Map.Entry<BionicOrganTrait, Coverage> entry : evaluate(assembly).entrySet()) {
			BionicOrganTrait trait = entry.getKey();
			Coverage coverage = entry.getValue();
			Set<SurgicalAssembly.CombinationMember> members = coverage.members();
			if (members.isEmpty() && !coverage.misplaced())
				continue;
			if (trait == BionicOrganTrait.RANGED_EFFECT && !members.isEmpty())
				reasons.put(trait, InactiveReason.NO_RANGED_ATTACK);
			else if (!coverage.reaches(RULES.get(trait).minCoverage))
				reasons.put(trait, coverage.misplaced()
					? InactiveReason.WRONG_SLOT : InactiveReason.INSUFFICIENT_COVERAGE);
			else if (!carriesBody(trait, assembly, coverage.carrierVolume()))
				reasons.put(trait, assembly.bodyBounds() == null
					? InactiveReason.BODY_MEASUREMENT_UNAVAILABLE : InactiveReason.PURPOSE_MISMATCH);
		}
		return Map.copyOf(reasons);
	}

	private static boolean matchesDonor(BionicOrganTrait trait, Rule rule,
		MimicProfile profile) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
			profile.entityTypeId()).orElse(null);
		if (type == null)
			return false;
		boolean present = rule.automaticDetection && automatic(trait, type);
		for (Map.Entry<TagKey<EntityType<?>>, Boolean> tagged : rule.tags.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.location().toString())))
			.toList())
			if (type.is(tagged.getKey()))
				present = tagged.getValue();
		Boolean exact = rule.entityTypes.get(profile.entityTypeId());
		return exact == null ? present : exact;
	}

	private static boolean automatic(BionicOrganTrait trait, EntityType<?> type) {
		return switch (trait) {
		case AGILE_LANDING -> (type == EntityType.CAT || type == EntityType.OCELOT)
			&& type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
		case POWDER_SNOW_WALK -> type.is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS);
		default -> false;
		};
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		EnumMap<BionicOrganTrait, Rule> loaded = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			Rule base = defaults().get(trait);
			ResourceLocation primary = CreateBiotech.asResource(trait.id());
			boolean automatic = base.automaticDetection;
			double coverage = base.minCoverage;
			double maxLoadPerVolume = base.maxLoadPerVolume;
			Map<String, Double> parameters = new HashMap<>();
			JsonElement primaryFile = resources.get(primary);
			if (primaryFile != null && primaryFile.isJsonObject()) {
				JsonObject root = primaryFile.getAsJsonObject();
				try {
					if (root.has("automatic_detection"))
						automatic = root.get("automatic_detection").getAsBoolean();
					if (root.has("min_coverage"))
						coverage = root.get("min_coverage").getAsDouble();
					if (root.has("max_body_volume_per_wing_volume"))
						maxLoadPerVolume = root.get("max_body_volume_per_wing_volume").getAsDouble();
				} catch (RuntimeException exception) {
					LOGGER.warn("Invalid organ trait settings in {}", primary);
				}
				if (root.has("parameters") && root.get("parameters").isJsonObject())
					for (Map.Entry<String, JsonElement> value : root.getAsJsonObject("parameters").entrySet())
						try {
							double number = value.getValue().getAsDouble();
							if (Double.isFinite(number) && number >= 0.0d)
								parameters.put(value.getKey(), number);
						} catch (RuntimeException ignored) {
							LOGGER.warn("Invalid organ trait parameter {} in {}", value.getKey(), primary);
						}
			}
			if (!Double.isFinite(coverage) || coverage < 0.0d || coverage > 1.0d)
				coverage = base.minCoverage;
			if (!Double.isFinite(maxLoadPerVolume) || maxLoadPerVolume <= 0.0d)
				maxLoadPerVolume = base.maxLoadPerVolume;
			Map<ResourceLocation, Boolean> entities = new HashMap<>();
			Map<TagKey<EntityType<?>>, Boolean> tags = new HashMap<>();
			for (Map.Entry<ResourceLocation, JsonElement> file : resources.entrySet().stream()
				.filter(entry -> entry.getKey().getPath().equals(trait.id()))
				.sorted(Map.Entry.comparingByKey()).toList()) {
				if (!file.getValue().isJsonObject())
					continue;
				JsonElement values = file.getValue().getAsJsonObject().get("values");
				if (values == null || !values.isJsonObject())
					continue;
				for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject().entrySet()) {
					if (!entry.getValue().isJsonPrimitive()
						|| !entry.getValue().getAsJsonPrimitive().isBoolean())
						continue;
					String selector = entry.getKey();
					boolean tagged = selector.startsWith("#");
					ResourceLocation id = ResourceLocation.tryParse(tagged ? selector.substring(1) : selector);
					if (id == null) {
						LOGGER.warn("Invalid organ selector {} in {}", selector, file.getKey());
						continue;
					}
					if (tagged)
						tags.put(TagKey.create(Registries.ENTITY_TYPE, id), entry.getValue().getAsBoolean());
					else
						entities.put(id, entry.getValue().getAsBoolean());
				}
			}
			loaded.put(trait, new Rule(automatic, coverage, maxLoadPerVolume,
				Map.copyOf(entities), Map.copyOf(tags), Map.copyOf(parameters)));
		}
		RULES = Map.copyOf(loaded);
		GENERATION.incrementAndGet();
	}

	private static Map<BionicOrganTrait, Rule> defaults() {
		EnumMap<BionicOrganTrait, Rule> rules = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values())
			rules.put(trait, new Rule(trait == BionicOrganTrait.AGILE_LANDING
				|| trait == BionicOrganTrait.POWDER_SNOW_WALK, 0.5d, 16.0d,
				Map.of(), Map.of(), Map.of()));
		return Map.copyOf(rules);
	}

	private record Rule(boolean automaticDetection, double minCoverage,
		double maxLoadPerVolume,
		Map<ResourceLocation, Boolean> entityTypes, Map<TagKey<EntityType<?>>, Boolean> tags,
		Map<String, Double> parameters) {}
}
