package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** v1 addon API. Register pure formula factories/defaults during common setup, before data loading. */
public final class BionicStatApi {
	public static final int API_VERSION = 1;
	private BionicStatApi() {}
	public static void registerFormulaType(ResourceLocation id, BiFunction<BionicStat, JsonObject, BionicStatFormula> factory) { BionicStats.registerType(id, factory); }
	public static void registerDefault(BionicStat stat, JsonObject definition) { BionicStats.registerDefault(stat, definition); }
	public static Set<String> variables(BionicStat stat) { return BionicStats.variables(stat); }
	public static Map<String, Double> parameters(Level level) { return BionicStats.parameters(level); }
	public static long revision(Level level) { return BionicStats.revision(level); }
	public static double evaluate(BionicStat stat, Level level, Map<String, Double> inputs) { return BionicStats.evaluate(stat, level, inputs); }
}
