package com.nobodiiiii.createbiotech.api.bionic;

import java.util.List;
import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Public extension entry point. Version 1: namespaced IDs, JSON factories and immutable results. */
public final class BionicTraitApi {
	public static final int API_VERSION = 1;
	private BionicTraitApi() {}
	public static List<BionicTrait> definitions(Level level) { return BionicTraitRegistry.traits(level); }
	public static BionicTrait find(Level level, ResourceLocation id) { return BionicTraitRegistry.find(level, id); }
	public static BionicTraitResult result(SlimeBionicEntity entity, ResourceLocation id) {
		return entity.getBionicTraits().result(BionicTrait.reference(id));
	}
	public static BionicTraitSet evaluate(SurgicalAssembly assembly, Level level) {
		return BionicTraitResolver.resolve(assembly, level);
	}
	/** Register on the mod's common setup enqueueWork, before the first data load. Packs can override it. */
	public static void registerDefault(ResourceLocation id, JsonObject definition) {
		BionicExtensions.registerDefault(id, definition);
	}
	public static void registerBehavior(ResourceLocation id, Factory<BionicBehavior> factory) {
		BionicExtensions.registerBehavior(id, factory);
	}
	public static void registerCondition(ResourceLocation id, Factory<BionicCondition> factory) {
		BionicExtensions.registerCondition(id, factory);
	}
	public static void registerCarrierSelector(ResourceLocation id, Factory<BionicCarrierSelector> factory) {
		BionicExtensions.registerSelector(id, factory);
	}
	@FunctionalInterface public interface Factory<T> { T parse(JsonObject configuration); }
}
