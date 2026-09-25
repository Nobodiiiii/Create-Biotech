package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Per-trait donor facts and head-region coverage; no transient donor state is inherited. */
public final class BionicHeadTraitRegistry extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final BionicHeadTraitRegistry INSTANCE = new BionicHeadTraitRegistry();
	private static final Map<MimicProfile.BiologicalKey, Set<BionicHeadTrait>> DETECTED =
		new ConcurrentHashMap<>();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile Map<BionicHeadTrait, Rule> RULES = defaultRules();
	private static boolean registered;

	private BionicHeadTraitRegistry() { super(new Gson(), "bionic_head_traits"); }

	public static void register() {
		if (registered)
			return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicHeadTraitRegistry::onTagsUpdated);
	}

	private static void onTagsUpdated(TagsUpdatedEvent event) { invalidate(); }
	private static void invalidate() { DETECTED.clear(); GENERATION.incrementAndGet(); }
	public static long generation() { return GENERATION.get(); }

	public static BionicHeadTraits resolve(@Nullable SurgicalAssembly assembly, Level level) {
		if (assembly == null || level == null)
			return BionicHeadTraits.EMPTY;
		EnumSet<BionicHeadTrait> result = EnumSet.noneOf(BionicHeadTrait.class);
		shares(assembly, level).forEach((trait, share) -> {
			if (share.reaches(RULES.get(trait).minCoverage()))
				result.add(trait);
		});
		return new BionicHeadTraits(result,
			result.contains(BionicHeadTrait.LONG_BREATH)
				? RULES.get(BionicHeadTrait.LONG_BREATH).maxAirSupply() : 300);
	}

	/**
	 * Local coverage of every head ability that at least one source's species has. Gills and
	 * air-breathing heads form one respiratory region, so a head stitched from several donors
	 * breathes the way most of its volume does.
	 */
	private static Map<BionicHeadTrait, BionicTissue.Share> shares(SurgicalAssembly assembly,
		Level level) {
		BionicTissue tissue = BionicTissue.of(assembly);
		EnumMap<BionicHeadTrait, Map<Integer, BitSet>> carriers = new EnumMap<>(BionicHeadTrait.class);
		for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++)
			for (BionicHeadTrait trait : get(tissue.source(sourceId).profile(), level))
				carriers.computeIfAbsent(trait, ignored -> new HashMap<>())
					.put(sourceId, carrierCubes(trait, tissue, sourceId));
		EnumMap<BionicHeadTrait, BionicTissue.Share> shares = new EnumMap<>(BionicHeadTrait.class);
		BitSet none = new BitSet();
		carriers.forEach((trait, bySource) -> shares.put(trait, tissue.share(respiratory(trait)
				? new BionicAnatomyRole[] { BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD }
				: new BionicAnatomyRole[] { BionicAnatomyRole.HEAD },
			sourceId -> bySource.getOrDefault(sourceId, none))));
		return shares;
	}

	private static boolean respiratory(BionicHeadTrait trait) {
		return trait == BionicHeadTrait.WATER_BREATHING || trait == BionicHeadTrait.DRY_SUFFOCATION;
	}

	private static BitSet carrierCubes(BionicHeadTrait trait, BionicTissue tissue, int sourceId) {
		SurgicalAssembly.Source source = tissue.source(sourceId);
		BionicAnatomyRole role = respiratory(trait) ? BionicAnatomyRole.GILL : BionicAnatomyRole.HEAD;
		BitSet cubes = tissue.carriers(sourceId, role, BionicTraitCarrierRegistry.headCubes(trait,
			source.profile().entityTypeId(), source.anatomy().parts(), source.cubeCount(), role));
		// A donor without mapped gills breathes through its whole head.
		return cubes.isEmpty() && role == BionicAnatomyRole.GILL
			? tissue.carriers(sourceId, BionicAnatomyRole.HEAD, null) : cubes;
	}

	public enum InactiveReason {
		INSUFFICIENT_COVERAGE
	}

	/** Head abilities whose carriers are present but make up too little of their region. */
	public static Map<BionicHeadTrait, InactiveReason> inactiveReasons(
		@Nullable SurgicalAssembly assembly, Level level) {
		if (assembly == null || level == null)
			return Map.of();
		EnumMap<BionicHeadTrait, InactiveReason> reasons =
			new EnumMap<>(BionicHeadTrait.class);
		shares(assembly, level).forEach((trait, share) -> {
			if (!share.members().isEmpty() && !share.reaches(RULES.get(trait).minCoverage()))
				reasons.put(trait, InactiveReason.INSUFFICIENT_COVERAGE);
		});
		return Map.copyOf(reasons);
	}

	private static Set<BionicHeadTrait> get(MimicProfile profile, Level level) {
		return DETECTED.computeIfAbsent(profile.biologicalKey(), ignored -> {
			try {
				LivingEntity donor = profile.createBiologicalEntity(level);
				if (donor == null)
					return Set.of();
				EntityType<?> type = donor.getType();
				ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
				EnumSet<BionicHeadTrait> traits = EnumSet.noneOf(BionicHeadTrait.class);
				for (BionicHeadTrait trait : BionicHeadTrait.values()) {
					Rule rule = RULES.get(trait);
					boolean present = rule.automaticDetection() && automatic(trait, donor);
					for (Map.Entry<TagKey<EntityType<?>>, Boolean> tagged : rule.tags().entrySet()
						.stream().sorted(Map.Entry.comparingByKey(
							java.util.Comparator.comparing(key -> key.location().toString()))).toList())
						if (type.is(tagged.getKey()))
							present = tagged.getValue();
					Boolean exact = rule.entityTypes().get(id);
					if (exact != null)
						present = exact;
					if (present)
						traits.add(trait);
				}
				return Set.copyOf(traits);
			} catch (RuntimeException exception) {
				LOGGER.warn("Could not inspect bionic head traits for {}", profile.entityTypeId(), exception);
				return Set.of();
			}
		});
	}

	/** Biological facts only; the donor's head must still form enough of the assembled head. */
	public static Set<BionicHeadTrait> donorFacts(MimicProfile profile, Level level) {
		return get(profile, level);
	}

	private static boolean automatic(BionicHeadTrait trait, LivingEntity donor) {
		return switch (trait) {
		case WATER_BREATHING -> donor.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER)
			&& !donor.getType().is(EntityTypeTags.UNDEAD)
			&& donor.getType() != EntityType.ARMOR_STAND
			&& !donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value());
		case DRY_SUFFOCATION -> false;
		case LONG_BREATH -> donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value())
			&& donor.getMaxAirSupply() > 300;
		case TAMEABLE -> donor instanceof TamableAnimal
			|| donor instanceof AbstractHorse && donor.getType() != EntityType.SKELETON_HORSE
				&& donor.getType() != EntityType.ZOMBIE_HORSE
				&& donor.getType() != EntityType.CAMEL;
		};
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		EnumMap<BionicHeadTrait, Rule> parsed = new EnumMap<>(BionicHeadTrait.class);
		for (BionicHeadTrait trait : BionicHeadTrait.values()) {
			ResourceLocation primary = CreateBiotech.asResource(trait.id());
			Rule fallback = defaultRules().get(trait);
			boolean automatic = fallback.automaticDetection();
			double threshold = fallback.minCoverage();
			int maxAirSupply = fallback.maxAirSupply();
			Map<ResourceLocation, Boolean> entities = new HashMap<>();
			Map<TagKey<EntityType<?>>, Boolean> tags = new HashMap<>();
			JsonElement primaryFile = resources.get(primary);
			if (primaryFile != null && primaryFile.isJsonObject()) {
				JsonObject root = primaryFile.getAsJsonObject();
				if (root.has("automatic_detection") && root.get("automatic_detection").isJsonPrimitive())
					automatic = root.get("automatic_detection").getAsBoolean();
				if (root.has("min_coverage") && root.get("min_coverage").isJsonPrimitive()) {
					try { threshold = Mth.clamp(root.get("min_coverage").getAsDouble(), 0.0d, 1.0d); }
					catch (RuntimeException ignored) { LOGGER.warn("Invalid head trait coverage in {}", primary); }
				}
				if (root.has("max_air_supply") && root.get("max_air_supply").isJsonPrimitive())
					try { maxAirSupply = Mth.clamp(root.get("max_air_supply").getAsInt(), 300, 12000); }
					catch (RuntimeException ignored) { LOGGER.warn("Invalid head trait air supply in {}", primary); }
			}
			for (Map.Entry<ResourceLocation, JsonElement> file : resources.entrySet().stream()
				.filter(entry -> entry.getKey().getPath().equals(trait.id()))
				.sorted(Map.Entry.comparingByKey()).toList()) {
				if (!file.getValue().isJsonObject())
					continue;
				JsonElement values = file.getValue().getAsJsonObject().get("values");
				if (values == null || !values.isJsonObject())
					continue;
				for (Map.Entry<String, JsonElement> value : values.getAsJsonObject().entrySet()) {
					if (!value.getValue().isJsonPrimitive()
						|| !value.getValue().getAsJsonPrimitive().isBoolean())
						continue;
					boolean enabled = value.getValue().getAsBoolean();
					String selector = value.getKey();
					boolean tagged = selector.startsWith("#");
					ResourceLocation id = ResourceLocation.tryParse(tagged ? selector.substring(1) : selector);
					if (id == null) {
						LOGGER.warn("Invalid head trait selector {} in {}", selector, file.getKey());
						continue;
					}
					if (tagged)
						tags.put(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id), enabled);
					else
						entities.put(id, enabled);
				}
			}
			parsed.put(trait, new Rule(automatic, threshold, maxAirSupply,
				Map.copyOf(entities), Map.copyOf(tags)));
		}
		RULES = Map.copyOf(parsed);
		invalidate();
	}

	private static Map<BionicHeadTrait, Rule> defaultRules() {
		EnumMap<BionicHeadTrait, Rule> defaults = new EnumMap<>(BionicHeadTrait.class);
		for (BionicHeadTrait trait : BionicHeadTrait.values())
			defaults.put(trait, new Rule(trait != BionicHeadTrait.DRY_SUFFOCATION,
				0.5d, trait == BionicHeadTrait.LONG_BREATH ? 4800 : 300,
				Map.of(), Map.of()));
		return Map.copyOf(defaults);
	}

	private record Rule(boolean automaticDetection, double minCoverage, int maxAirSupply,
		Map<ResourceLocation, Boolean> entityTypes, Map<TagKey<EntityType<?>>, Boolean> tags) {}
}
