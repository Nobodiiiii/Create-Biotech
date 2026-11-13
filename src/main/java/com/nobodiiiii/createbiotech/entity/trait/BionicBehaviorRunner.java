package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.api.bionic.BionicBehavior;
import com.nobodiiiii.createbiotech.api.bionic.BionicTraitEvent;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/** Per-entity lifecycle. Losing/reloading a trait cleans transient effects before activating replacements. */
public final class BionicBehaviorRunner {
	private final SlimeBionicEntity entity;
	private BionicTraitSet last;
	private List<BionicTrait> active = List.of();
	private List<Binding> bindings = List.of();
	private final Set<Binding> failed = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean dispatchingDamage;
	private boolean changing;
	private record Binding(BionicBehavior behavior, BionicBehavior.Context context) {}
	public BionicBehaviorRunner(SlimeBionicEntity entity) { this.entity = entity; }

	private BionicTraitSet refresh() {
		var traits = entity.getBionicTraits();
		if (traits == last || changing || entity.isRemoved()) return traits;
		changing = true;
		try {
			var previous = bindings;
			bindings = List.of(); active = List.of(); failed.clear(); last = traits;
			for (var binding : previous) remove(binding);
			List<Binding> next = new ArrayList<>();
			var snapshot = BionicTraitRegistry.snapshot(entity.level());
			for (var trait : traits.definitions()) {
				if (!traits.has(trait)) continue;
				var components = snapshot.get(trait).extensions().behaviors();
				for (int i = 0; i < components.size(); i++) next.add(new Binding(components.get(i),
					new BionicBehavior.Context(entity, trait, traits.result(trait), i)));
			}
			if (!entity.isRemoved()) {
				bindings = List.copyOf(next);
				active = traits.definitions().stream().filter(traits::has).toList();
			}
			return traits;
		} finally { changing = false; }
	}
	private void remove(Binding binding) {
		try { binding.behavior.removed(binding.context); }
		catch (RuntimeException exception) { LogUtils.getLogger().error("Bionic behavior cleanup failed: {}", binding.context.trait(), exception); }
	}
	private void invoke(Binding binding, Consumer<Binding> action) {
		if (failed.contains(binding) || entity.isRemoved()) return;
		try { action.accept(binding); }
		catch (RuntimeException exception) {
			failed.add(binding); remove(binding);
			LogUtils.getLogger().error("Bionic behavior disabled until reevaluation: {} component {}",
				binding.context.trait(), binding.context.index(), exception);
		}
	}
	public void tick() {
		if (entity.level().isClientSide || !entity.isAlive()) return;
		var traits = refresh();
		for (var binding : bindings) invoke(binding, b -> b.behavior.tick(b.context));
		for (var trait : active)
			NeoForge.EVENT_BUS.post(new BionicTraitEvent.Tick(entity, trait, traits.result(trait)));
	}
	public void clear() {
		if (changing) return;
		changing = true;
		try {
			var previous = bindings;
			bindings = List.of(); active = List.of(); failed.clear(); last = null;
			for (var binding : previous) remove(binding);
		} finally { changing = false; }
	}
	public boolean allowsEffect(net.minecraft.resources.ResourceLocation effect) {
		if (entity.level().isClientSide || effect == null) return true;
		refresh();
		boolean[] allowed = {true};
		for (var binding : bindings) invoke(binding, b -> allowed[0] &= b.behavior.allowsEffect(b.context, effect));
		return allowed[0];
	}
	public void melee(LivingEntity target, Collection<SurgicalAssembly.CombinationMember> members) {
		if (entity.level().isClientSide) return;
		var traits = refresh();
		var arm = Set.copyOf(members);
		for (var binding : bindings) if (binding.context.uses(arm)) invoke(binding, b -> b.behavior.melee(b.context, target, arm));
		for (var trait : active) if (arm.stream().anyMatch(traits.members(trait)::contains))
			NeoForge.EVENT_BUS.post(new BionicTraitEvent.Melee(entity, trait, traits.result(trait), target, arm));
	}
	public float incomingDamage(DamageSource source, float amount) {
		if (entity.level().isClientSide || dispatchingDamage || !Float.isFinite(amount) || amount <= 0) return amount;
		dispatchingDamage = true;
		try {
			var traits = refresh();
			float[] damage = {amount};
			for (var binding : bindings) {
				invoke(binding, b -> {
					float value = b.behavior.incomingDamage(b.context, source, damage[0]);
					if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid bionic damage");
					damage[0] = value;
				});
				if (damage[0] == 0) return 0;
			}
			for (var trait : active) {
				var event = new BionicTraitEvent.Hurt(entity, trait, traits.result(trait), source, damage[0]);
				NeoForge.EVENT_BUS.post(event); damage[0] = event.getAmount();
				if (damage[0] == 0) return 0;
			}
			return damage[0];
		} finally { dispatchingDamage = false; }
	}
}
