package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Set;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Immutable shared behavior. Keep per-entity state on the entity, never on this object. Server only. */
public interface BionicBehavior {
	default void tick(Context context) {}
	default void removed(Context context) {}
	default void melee(Context context, LivingEntity target, Set<SurgicalAssembly.CombinationMember> arm) {}
	default float incomingDamage(Context context, DamageSource source, float amount) { return amount; }
	default boolean allowsEffect(Context context, ResourceLocation effect) { return true; }

	record Context(SlimeBionicEntity entity, BionicTrait trait, BionicTraitResult result, int index) {
		public ResourceLocation modifierId() {
			return ResourceLocation.fromNamespaceAndPath(trait.id().getNamespace(),
				"bionic_trait/" + trait.id().getPath() + "/" + index);
		}
		/** Cadence depends on world time, so reloads cannot grant an extra immediate output. */
		public boolean every(int ticks) {
			return Math.floorMod(entity.level().getGameTime() + entity.getId(), ticks) == 0;
		}
		public boolean uses(Set<SurgicalAssembly.CombinationMember> arm) {
			return arm.stream().anyMatch(result.members()::contains);
		}
	}
}
