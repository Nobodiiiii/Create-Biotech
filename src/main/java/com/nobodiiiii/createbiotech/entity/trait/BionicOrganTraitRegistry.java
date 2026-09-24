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
import java.util.UUID;
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

/** Per-trait donor rules, evaluated only against server-owned original-cube role templates. */
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
	public static long generation() { return GENERATION.get(); }
	public static double flightLoadPerCube() {
		return RULES.get(BionicOrganTrait.WING_FLIGHT).maxLoadPerCube;
	}
	public static double parameter(BionicOrganTrait trait, String key, double fallback) {
		Rule rule = RULES.get(trait);
		double value = rule == null ? fallback : rule.parameters.getOrDefault(key, fallback);
		if (key.equals("chance") || key.endsWith("multiplier"))
			return Math.min(1.0d, value);
		return Math.min(key.endsWith("duration_ticks") ? 12000.0d : 100.0d, value);
	}

	public static BionicOrganTraits resolve(@Nullable SurgicalAssembly assembly) {
		if (assembly == null || assembly.sources().isEmpty())
			return BionicOrganTraits.EMPTY;
		Map<UUID, Donor> donors = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> connected = assembly.connectedMembers();
		for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++) {
			SurgicalAssembly.Source source = assembly.sources().get(sourceId);
			BionicAnatomyRegistry.Template template = BionicAnatomyRegistry.getForSource(source);
			if (template == null)
				continue;
			Donor donor = donors.computeIfAbsent(source.donorId(), ignored ->
				new Donor(source, template));
			BitSet present = source.presentCubes();
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
				if (!connected.contains(new SurgicalAssembly.CombinationMember(sourceId, cube)))
					present.clear(cube);
			donor.fragments.add(new Fragment(sourceId, present));
		}
		EnumMap<BionicOrganTrait, Set<SurgicalAssembly.CombinationMember>> resolved =
			new EnumMap<>(BionicOrganTrait.class);
		EnumMap<BionicOrganTrait, Double> weights = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			// This entity has no projectile firing path yet; an inherited arrow effect would be inert.
			if (trait == BionicOrganTrait.RANGED_EFFECT)
				continue;
			Rule rule = RULES.get(trait);
			if (rule == null)
				continue;
			Set<SurgicalAssembly.CombinationMember> eligible = new HashSet<>();
			for (Donor donor : donors.values()) {
				if (!matchesDonor(trait, rule, donor.source.profile()))
					continue;
				List<Set<SurgicalAssembly.CombinationMember>> byRole = new ArrayList<>();
				for (BionicAnatomyRole role : trait.roles()) {
					BitSet original = carrierCubes(trait, donor.source, role);
					if (original.isEmpty())
						continue;
					double total = 0.0d;
					double retained = 0.0d;
					BitSet union = new BitSet();
					for (Fragment fragment : donor.fragments)
						union.or(fragment.present);
					for (int cube = original.nextSetBit(0); cube >= 0;
						cube = original.nextSetBit(cube + 1)) {
						total += donor.template.weight(cube);
						if (union.get(cube))
							retained += donor.template.weight(cube);
					}
					if (total <= 0.0d || retained / total + 1.0e-8d < rule.minCompleteness)
						continue;
					Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
					for (Fragment fragment : donor.fragments) {
						BitSet intersection = (BitSet) fragment.present.clone();
						intersection.and(original);
						for (int cube = intersection.nextSetBit(0); cube >= 0;
							cube = intersection.nextSetBit(cube + 1))
							members.add(new SurgicalAssembly.CombinationMember(fragment.sourceId, cube));
					}
					byRole.add(members);
				}
				if (trait == BionicOrganTrait.WING_FLIGHT
				&& byRole.size() != trait.roles().length)
					continue;
				for (Set<SurgicalAssembly.CombinationMember> roleMembers : byRole)
					eligible.addAll(roleMembers);
			}
			if (eligible.isEmpty())
				continue;
			if (requiresLeg(trait)) {
				int requiredLegs = (int) parameter(trait, "min_legs",
					trait == BionicOrganTrait.AGILE_LANDING
						|| trait == BionicOrganTrait.WALL_CLIMB ? 2.0d : 1.0d);
				int groundedLegs = assembly.bodyBounds() == null ? 0
					: assembly.bodyBounds().groundedLegCount();
				int contributingLegs = Math.min(groundedLegs, eligible.size());
				if (contributingLegs < requiredLegs
					|| assembly.hasBodyVolume() && assembly.bodyVolume() > contributingLegs
						* parameter(trait, "max_body_volume_per_leg", 16.0d))
					continue;
			}
			if (!eligible.isEmpty()) {
				resolved.put(trait, eligible);
				double weight = 0.0d;
				for (SurgicalAssembly.CombinationMember member : eligible) {
					SurgicalAssembly.Source source = assembly.sources().get(member.source());
					Donor donor = donors.get(source.donorId());
					if (donor != null)
						weight += donor.template.weight(member.cube());
				}
				weights.put(trait, weight);
			}
		}
		return new BionicOrganTraits(resolved, weights);
	}

	private static BitSet carrierCubes(BionicOrganTrait trait, SurgicalAssembly.Source source,
		BionicAnatomyRole role) {
		BitSet named = BionicTraitCarrierRegistry.organCubes(trait,
			source.profile().entityTypeId(), source.anatomy().parts(), source.cubeCount(), role);
		if (named != null)
			return named;
		BitSet legacy = source.anatomy().cubes(role);
		if (!legacy.isEmpty())
			return legacy;
		BionicAnatomyRegistry.Template configured = BionicAnatomyRegistry.get(source);
		return configured == null ? new BitSet() : configured.cubes(role);
	}

	private static boolean requiresLeg(BionicOrganTrait trait) {
		return switch (trait) {
		case AGILE_LANDING, FALL_REDUCTION, POWDER_SNOW_WALK, LAVA_WALK, WALL_CLIMB -> true;
		default -> false;
		};
	}

	public static Set<BionicOrganTrait> donorFacts(MimicProfile profile) {
		EnumMap<BionicOrganTrait, Boolean> facts = new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			Rule rule = RULES.get(trait);
			if (rule != null && matchesDonor(trait, rule, profile))
				facts.put(trait, true);
		}
		return facts.isEmpty() ? Set.of() : Set.copyOf(facts.keySet());
	}

	public enum InactiveReason {
		UNKNOWN_ANATOMY, INCOMPLETE_OR_DISCONNECTED, BODY_MEASUREMENT_UNAVAILABLE,
		PURPOSE_MISMATCH, NO_RANGED_ATTACK
	}

	/** Explains why a known donor fact is absent from the effective assembly abilities. */
	public static Map<BionicOrganTrait, InactiveReason> inactiveReasons(
		@Nullable SurgicalAssembly assembly) {
		if (assembly == null)
			return Map.of();
		BionicOrganTraits active = resolve(assembly);
		Set<SurgicalAssembly.CombinationMember> connected = assembly.connectedMembers();
		Map<UUID, BitSet> presentByDonor = new HashMap<>();
		Set<UUID> donorsWithHeads = new HashSet<>();
		for (SurgicalAssembly.Source source : assembly.sources()) {
			presentByDonor.computeIfAbsent(source.donorId(), ignored -> new BitSet())
				.or(source.presentCubes());
			if (!source.headCubes().isEmpty())
				donorsWithHeads.add(source.donorId());
		}
		EnumMap<BionicOrganTrait, InactiveReason> reasons =
			new EnumMap<>(BionicOrganTrait.class);
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			if (active.has(trait))
				continue;
			Rule rule = RULES.get(trait);
			if (rule == null)
				continue;
			boolean fact = false;
			boolean mapped = false;
			boolean sufficientlyRetained = false;
			Set<UUID> seen = new HashSet<>();
			for (SurgicalAssembly.Source source : assembly.sources()) {
				if (!seen.add(source.donorId()) || !matchesDonor(trait, rule, source.profile()))
					continue;
				BionicAnatomyRegistry.Template template = BionicAnatomyRegistry.getForSource(source);
				BitSet present = presentByDonor.get(source.donorId());
				boolean carrierPresent = false;
				boolean carrierKnown = false;
				int qualifyingRoles = 0;
				for (BionicAnatomyRole role : trait.roles()) {
					BitSet original = carrierCubes(trait, source, role);
					if (original.isEmpty())
						continue;
					carrierKnown = true;
					if (!original.intersects(present))
						continue;
					carrierPresent = true;
					if (template == null)
						continue;
					mapped = true;
					double total = 0.0d;
					double retained = 0.0d;
					for (int cube = original.nextSetBit(0); cube >= 0;
						cube = original.nextSetBit(cube + 1)) {
						total += template.weight(cube);
						for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++)
							if (assembly.sources().get(sourceId).donorId().equals(source.donorId())
								&& connected.contains(new SurgicalAssembly.CombinationMember(sourceId, cube))) {
								retained += template.weight(cube);
								break;
							}
					}
					if (total > 0.0d && retained / total + 1.0e-8d >= rule.minCompleteness)
						qualifyingRoles++;
				}
				if (qualifyingRoles >= (trait == BionicOrganTrait.WING_FLIGHT
					? trait.roles().length : 1))
					sufficientlyRetained = true;
				if (!carrierKnown && donorsWithHeads.contains(source.donorId()))
					for (BionicAnatomyRole role : trait.roles())
						if (role == BionicAnatomyRole.HEAD) {
							carrierPresent = true;
							break;
						}
				fact |= carrierPresent;
			}
			if (fact)
				reasons.put(trait, trait == BionicOrganTrait.RANGED_EFFECT
					? InactiveReason.NO_RANGED_ATTACK
					: sufficientlyRetained && requiresLeg(trait) && assembly.bodyBounds() == null
						? InactiveReason.BODY_MEASUREMENT_UNAVAILABLE
					: sufficientlyRetained ? InactiveReason.PURPOSE_MISMATCH
					: mapped ? InactiveReason.INCOMPLETE_OR_DISCONNECTED
					: InactiveReason.UNKNOWN_ANATOMY);
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
			double completeness = base.minCompleteness;
			double maxLoadPerCube = base.maxLoadPerCube;
			Map<String, Double> parameters = new HashMap<>();
			JsonElement primaryFile = resources.get(primary);
			if (primaryFile != null && primaryFile.isJsonObject()) {
				JsonObject root = primaryFile.getAsJsonObject();
				try {
					if (root.has("automatic_detection"))
						automatic = root.get("automatic_detection").getAsBoolean();
					if (root.has("min_completeness"))
						completeness = root.get("min_completeness").getAsDouble();
					if (root.has("max_body_volume_per_cube"))
						maxLoadPerCube = root.get("max_body_volume_per_cube").getAsDouble();
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
			if (!Double.isFinite(completeness) || completeness < 0.0d || completeness > 1.0d)
				completeness = base.minCompleteness;
			if (!Double.isFinite(maxLoadPerCube) || maxLoadPerCube <= 0.0d)
				maxLoadPerCube = base.maxLoadPerCube;
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
			loaded.put(trait, new Rule(automatic, completeness, maxLoadPerCube,
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

	private static final class Donor {
		private final SurgicalAssembly.Source source;
		private final BionicAnatomyRegistry.Template template;
		private final List<Fragment> fragments = new ArrayList<>();
		private Donor(SurgicalAssembly.Source source, BionicAnatomyRegistry.Template template) {
			this.source = source;
			this.template = template;
		}
	}
	private record Fragment(int sourceId, BitSet present) {}
	private record Rule(boolean automaticDetection, double minCompleteness,
		double maxLoadPerCube,
		Map<ResourceLocation, Boolean> entityTypes, Map<TagKey<EntityType<?>>, Boolean> tags,
		Map<String, Double> parameters) {}
}
