package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** Per-trait donor facts and source-head completeness; no transient donor state is inherited. */
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
		Map<UUID, BitSet> retainedHeads = new HashMap<>();
		Map<UUID, BitSet> retainedRespiration = new HashMap<>();
		Map<UUID, BitSet> originalHeads = new HashMap<>();
		Map<UUID, BitSet> originalRespiration = new HashMap<>();
		Map<UUID, BionicAnatomyRegistry.Template> templates = new HashMap<>();
		Map<UUID, SurgicalAssembly.Source> origins = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> connected = assembly.connectedMembers();
		for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++) {
			SurgicalAssembly.Source source = assembly.sources().get(sourceId);
			BionicAnatomyRegistry.Template template = BionicAnatomyRegistry.getForIntactDonor(source);
			BitSet originalHead = template == null ? new BitSet()
				: template.cubes(BionicAnatomyRole.HEAD);
			BitSet originalResp = template == null ? new BitSet()
				: template.cubes(BionicAnatomyRole.GILL);
			if (originalResp.isEmpty())
				originalResp = (BitSet) originalHead.clone();
			if (originalHead.isEmpty() && originalResp.isEmpty())
				continue;
			origins.putIfAbsent(source.donorId(), source);
			templates.putIfAbsent(source.donorId(), template);
			originalHeads.putIfAbsent(source.donorId(), originalHead);
			originalRespiration.putIfAbsent(source.donorId(), originalResp);
			BitSet presentHead = source.presentCubes();
			presentHead.and(originalHead);
			BitSet presentResp = source.presentCubes();
			presentResp.and(originalResp);
			for (int cube = presentHead.nextSetBit(0); cube >= 0; cube = presentHead.nextSetBit(cube + 1))
				if (!connected.contains(new SurgicalAssembly.CombinationMember(sourceId, cube)))
					presentHead.clear(cube);
			for (int cube = presentResp.nextSetBit(0); cube >= 0; cube = presentResp.nextSetBit(cube + 1))
				if (!connected.contains(new SurgicalAssembly.CombinationMember(sourceId, cube)))
					presentResp.clear(cube);
			retainedHeads.computeIfAbsent(source.donorId(), ignored -> new BitSet())
				.or(presentHead);
			retainedRespiration.computeIfAbsent(source.donorId(), ignored -> new BitSet())
				.or(presentResp);
		}
		boolean hasAirBreathingHead = false;
		boolean hasDryHead = false;
		for (Map.Entry<UUID, SurgicalAssembly.Source> entry : origins.entrySet()) {
			SurgicalAssembly.Source source = entry.getValue();
			BitSet originalHead = originalHeads.get(entry.getKey());
			BitSet originalResp = originalRespiration.get(entry.getKey());
			BitSet head = retainedHeads.get(entry.getKey());
			BitSet resp = retainedRespiration.get(entry.getKey());
			BionicAnatomyRegistry.Template template = templates.get(entry.getKey());
			double headCompleteness = completeness(originalHead, head, template);
			double respCompleteness = completeness(originalResp, resp, template);
			Set<BionicHeadTrait> facts = get(source.profile(), level);
			for (BionicHeadTrait trait : facts) {
				Rule rule = RULES.get(trait);
				double completeness = trait == BionicHeadTrait.WATER_BREATHING
					|| trait == BionicHeadTrait.DRY_SUFFOCATION
					? respCompleteness : headCompleteness;
				if (rule != null && completeness >= rule.minCompleteness())
					result.add(trait);
			}
			if (headCompleteness >= 0.5d || respCompleteness >= 0.5d) {
				if (facts.contains(BionicHeadTrait.DRY_SUFFOCATION))
					hasDryHead = true;
				else
					hasAirBreathingHead = true;
			}
		}
		if (!hasDryHead || hasAirBreathingHead)
			result.remove(BionicHeadTrait.DRY_SUFFOCATION);
		return new BionicHeadTraits(result,
			result.contains(BionicHeadTrait.LONG_BREATH)
				? RULES.get(BionicHeadTrait.LONG_BREATH).maxAirSupply() : 300);
	}

	private static double completeness(BitSet original, BitSet retained,
		BionicAnatomyRegistry.Template template) {
		if (original.isEmpty() || retained == null || template == null)
			return 0.0d;
		double total = 0.0d;
		double present = 0.0d;
		for (int cube = original.nextSetBit(0); cube >= 0;
			cube = original.nextSetBit(cube + 1)) {
			double weight = template.weight(cube);
			total += weight;
			if (retained.get(cube))
				present += weight;
		}
		return total <= 0.0d ? 0.0d : present / total;
	}

	public enum InactiveReason {
		UNKNOWN_ANATOMY, INCOMPLETE_OR_DISCONNECTED, AIR_BREATHING_HEAD_PRESENT
	}

	public static Map<BionicHeadTrait, InactiveReason> inactiveReasons(
		@Nullable SurgicalAssembly assembly, Level level) {
		if (assembly == null || level == null)
			return Map.of();
		BionicHeadTraits active = resolve(assembly, level);
		Set<SurgicalAssembly.CombinationMember> connected = assembly.connectedMembers();
		EnumMap<BionicHeadTrait, InactiveReason> reasons =
			new EnumMap<>(BionicHeadTrait.class);
		for (BionicHeadTrait trait : BionicHeadTrait.values()) {
			if (active.has(trait))
				continue;
			boolean fact = false;
			boolean mapped = false;
			boolean complete = false;
			Set<UUID> seen = new java.util.HashSet<>();
			for (SurgicalAssembly.Source source : assembly.sources()) {
				if (!seen.add(source.donorId()) || !get(source.profile(), level).contains(trait))
					continue;
				fact = true;
				BionicAnatomyRegistry.Template template =
					BionicAnatomyRegistry.getForIntactDonor(source);
				if (template == null)
					continue;
				mapped = true;
				BitSet original = trait == BionicHeadTrait.WATER_BREATHING
					|| trait == BionicHeadTrait.DRY_SUFFOCATION
					? template.cubes(BionicAnatomyRole.GILL) : new BitSet();
				if (original.isEmpty())
					original = template.cubes(BionicAnatomyRole.HEAD);
				BitSet retained = new BitSet();
				for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++) {
					SurgicalAssembly.Source fragment = assembly.sources().get(sourceId);
					if (!fragment.donorId().equals(source.donorId()))
						continue;
					BitSet present = fragment.presentCubes();
					present.and(original);
					for (int cube = present.nextSetBit(0); cube >= 0;
						cube = present.nextSetBit(cube + 1))
						if (connected.contains(new SurgicalAssembly.CombinationMember(sourceId, cube)))
							retained.set(cube);
				}
				Rule rule = RULES.get(trait);
				if (rule != null && completeness(original, retained, template)
					>= rule.minCompleteness())
					complete = true;
			}
			if (fact)
				reasons.put(trait, complete && trait == BionicHeadTrait.DRY_SUFFOCATION
					? InactiveReason.AIR_BREATHING_HEAD_PRESENT
					: mapped ? InactiveReason.INCOMPLETE_OR_DISCONNECTED
					: InactiveReason.UNKNOWN_ANATOMY);
		}
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

	/** Biological facts only; a captured donor still needs a retained, connected head to use them. */
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
			double threshold = fallback.minCompleteness();
			int maxAirSupply = fallback.maxAirSupply();
			Map<ResourceLocation, Boolean> entities = new HashMap<>();
			Map<TagKey<EntityType<?>>, Boolean> tags = new HashMap<>();
			JsonElement primaryFile = resources.get(primary);
			if (primaryFile != null && primaryFile.isJsonObject()) {
				JsonObject root = primaryFile.getAsJsonObject();
				if (root.has("automatic_detection") && root.get("automatic_detection").isJsonPrimitive())
					automatic = root.get("automatic_detection").getAsBoolean();
				if (root.has("min_completeness") && root.get("min_completeness").isJsonPrimitive()) {
					try { threshold = Mth.clamp(root.get("min_completeness").getAsDouble(), 0.0d, 1.0d); }
					catch (RuntimeException ignored) { LOGGER.warn("Invalid head trait completeness in {}", primary); }
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

	private record Rule(boolean automaticDetection, double minCompleteness, int maxAirSupply,
		Map<ResourceLocation, Boolean> entityTypes, Map<TagKey<EntityType<?>>, Boolean> tags) {}
}
