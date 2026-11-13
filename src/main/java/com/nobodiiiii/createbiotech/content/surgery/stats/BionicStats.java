package com.nobodiiiii.createbiotech.content.surgery.stats;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.api.bionic.BionicStatFormula;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Server-owned formula snapshots. Client previews use only the server-synchronized snapshot. */
public final class BionicStats {
	private static final Map<ResourceLocation, BiFunction<BionicStat, JsonObject, BionicStatFormula>> TYPES = new HashMap<>();
	private static final Map<BionicStat, JsonObject> ADDON_DEFAULTS = new EnumMap<>(BionicStat.class);
	private static final Map<BionicStat, BionicStatFormula> BUILT_INS = new EnumMap<>(BionicStat.class);
	private static volatile Snapshot current;
	private static volatile Snapshot client;
	private static Map<ResourceLocation, JsonElement> resources = Map.of();
	private static boolean sealed;
	static {
		for (BionicStat stat : BionicStat.values())
			BUILT_INS.put(stat, BionicStatExpression.compile(stat.defaultExpression(), variables(stat)));
		current = build(0, BionicStatParameters.defaults(), Map.of(), true);
		client = build(0, BionicStatParameters.defaults(), Map.of(), false);
	}
	private BionicStats() {}
	public static Set<String> variables(BionicStat stat) {
		Set<String> names = new HashSet<>(BionicStatParameters.defaults().keySet());
		names.addAll(stat.inputs()); return Set.copyOf(names);
	}
	public static synchronized void registerType(ResourceLocation id, BiFunction<BionicStat, JsonObject, BionicStatFormula> factory) {
		if (sealed) throw new IllegalStateException("Register bionic formulas before the first data reload");
		if (id.equals(ResourceLocation.parse("create_biotech:expression")) || TYPES.putIfAbsent(id, java.util.Objects.requireNonNull(factory)) != null)
			throw new IllegalArgumentException("Duplicate bionic formula type " + id);
	}
	public static synchronized void registerDefault(BionicStat stat, JsonObject definition) {
		if (sealed) throw new IllegalStateException("Register bionic defaults before the first data reload");
		if (ADDON_DEFAULTS.putIfAbsent(stat, definition.deepCopy()) != null)
			throw new IllegalArgumentException("Duplicate bionic default " + stat);
	}
	public static long revision(Level level) { return snapshot(level).revision; }
	public static Map<String, Double> parameters(Level level) { return snapshot(level).parameters; }
	public static double parameter(Level level, String name) { return parameters(level).get(name); }
	private static Snapshot snapshot(Level level) { return level != null && level.isClientSide ? client : current; }
	public static double evaluate(BionicStat stat, Level level, Map<String, Double> inputs) {
		Snapshot snapshot = snapshot(level);
		for (String name : stat.inputs())
			if (!inputs.containsKey(name) || !Double.isFinite(inputs.get(name)))
				throw new IllegalArgumentException("Missing or non-finite input " + name + " for " + stat.id());
		BionicStatFormula.Context context = new BionicStatFormula.Context(Map.copyOf(inputs), snapshot.parameters);
		double value;
		try {
			value = snapshot.formulas.get(stat).evaluate(context);
			if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite result");
		} catch (RuntimeException exception) {
			if (snapshot.failed.add(stat)) LogUtils.getLogger().error("Bionic formula {} failed; using calibrated default for invalid evaluations (logged once per reload)", stat.id(), exception);
			value = BUILT_INS.get(stat).evaluate(context);
		}
		return Double.isFinite(value) ? Math.clamp(value, stat.minimum(), stat.maximum()) : stat.minimum();
	}
	public static synchronized void reload(Map<ResourceLocation, JsonElement> loaded) {
		Snapshot next = build(current.revision + 1, current.parameters, loaded, true);
		resources = copy(loaded);
		current = next;
		sealed = true;
	}
	public static synchronized void configure(Map<String, Double> values) {
		Map<String, Double> parameters;
		try { parameters = BionicStatParameters.calibrate(values); }
		catch (IllegalArgumentException exception) {
			LogUtils.getLogger().error("Invalid bionic stat calibration; retaining the previous valid parameters", exception);
			return;
		}
		current = build(current.revision + 1, parameters, resources, true);
	}
	public static JsonObject exportServer() {
		return encode(current);
	}
	private static JsonObject encode(Snapshot snapshot) {
		JsonObject root = new JsonObject(), parameters = new JsonObject(), definitions = new JsonObject();
		BionicStatParameters.SETTINGS.forEach(setting -> parameters.addProperty(setting.key(), snapshot.parameters.get(setting.key())));
		snapshot.definitions.forEach((stat, json) -> definitions.add(stat.id(), json.deepCopy()));
		root.add("parameters", parameters); root.add("definitions", definitions);
		return root;
	}
	public static void acceptClient(JsonObject root) {
		Map<String, Double> parameters = new HashMap<>();
		root.getAsJsonObject("parameters").entrySet().forEach(entry -> parameters.put(entry.getKey(), entry.getValue().getAsDouble()));
		Map<ResourceLocation, JsonElement> definitions = new HashMap<>();
		root.getAsJsonObject("definitions").entrySet().forEach(entry -> definitions.put(ResourceLocation.parse("create_biotech:" + entry.getKey()), entry.getValue()));
		client = build(client.revision + 1, BionicStatParameters.calibrate(parameters), definitions, false);
	}
	public static void resetClient() { client = build(client.revision + 1, BionicStatParameters.defaults(), Map.of(), false); }
	public static synchronized void resetServer() {
		resources = Map.of(); current = build(current.revision + 1, BionicStatParameters.defaults(), Map.of(), true);
	}
	private static Snapshot build(long revision, Map<String, Double> parameters, Map<ResourceLocation, JsonElement> raw, boolean strict) {
		Map<BionicStat, JsonObject> definitions = new EnumMap<>(BionicStat.class);
		// Addon defaults are server-owned and included in the network snapshot.
		if (strict) ADDON_DEFAULTS.forEach((stat, json) -> definitions.put(stat, json.deepCopy()));
		for (var entry : raw.entrySet()) {
			BionicStat stat;
			try {
				if (!entry.getKey().getNamespace().equals("create_biotech")) throw new IllegalArgumentException("Unknown namespace");
				stat = BionicStat.valueOf(entry.getKey().getPath().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown base stat resource " + entry.getKey(), e); }
			if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Expected formula object for " + stat.id());
			definitions.put(stat, entry.getValue().getAsJsonObject().deepCopy());
		}
		Map<BionicStat, BionicStatFormula> formulas = new EnumMap<>(BionicStat.class);
		for (BionicStat stat : BionicStat.values()) {
			JsonObject json = definitions.get(stat);
			if (json == null) {
				json = new JsonObject(); json.addProperty("expression", stat.defaultExpression()); definitions.put(stat, json);
			}
			ResourceLocation type = ResourceLocation.parse(json.has("type") ? json.get("type").getAsString() : "create_biotech:expression");
			try {
				BionicStatFormula formula;
				if (type.equals(ResourceLocation.parse("create_biotech:expression")))
					formula = BionicStatExpression.compile(json.get("expression").getAsString(), variables(stat));
				else if (TYPES.containsKey(type)) formula = TYPES.get(type).apply(stat, json.deepCopy());
				else if (!strict) { formula = BUILT_INS.get(stat); LogUtils.getLogger().warn("Client is missing bionic formula type {}; using default preview for {}", type, stat.id()); }
				else throw new IllegalArgumentException("Unknown formula type " + type);
				formulas.put(stat, java.util.Objects.requireNonNull(formula));
			} catch (RuntimeException exception) { throw new IllegalArgumentException("Cannot compile bionic stat " + stat.id(), exception); }
		}
		Snapshot snapshot = new Snapshot(revision, Map.copyOf(parameters), Map.copyOf(formulas), Map.copyOf(definitions), ConcurrentHashMap.newKeySet());
		if (encode(snapshot).toString().length() > BionicStatsPacket.MAX_LENGTH)
			throw new IllegalArgumentException("Bionic stat snapshot exceeds " + BionicStatsPacket.MAX_LENGTH + " characters");
		return snapshot;
	}
	private static Map<ResourceLocation, JsonElement> copy(Map<ResourceLocation, JsonElement> raw) {
		Map<ResourceLocation, JsonElement> result = new HashMap<>(); raw.forEach((id, json) -> result.put(id, json.deepCopy())); return Map.copyOf(result);
	}
	private record Snapshot(long revision, Map<String, Double> parameters, Map<BionicStat, BionicStatFormula> formulas,
		Map<BionicStat, JsonObject> definitions, Set<BionicStat> failed) {}
}
