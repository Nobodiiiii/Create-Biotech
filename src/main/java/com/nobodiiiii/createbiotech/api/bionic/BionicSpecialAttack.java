package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Set;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Immutable attack implementation; scheduling, cooldowns and ownership stay with Biotech. Server only. */
@FunctionalInterface
public interface BionicSpecialAttack {
	void fire(Context context);
	default boolean canUse(Context context) { return true; }
	record Context(SlimeBionicEntity entity, LivingEntity target, ResourceLocation trait,
		ResourceLocation variant, Set<SurgicalAssembly.CombinationMember> members, Vec3 origin) {
		public Context { members = Set.copyOf(members); }
	}
}
