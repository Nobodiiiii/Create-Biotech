package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Set;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/** Server-thread events, posted once per active trait. Listeners must not recursively query damage. */
public abstract class BionicTraitEvent extends Event {
	private final SlimeBionicEntity entity;
	private final BionicTrait trait;
	private final BionicTraitResult result;
	protected BionicTraitEvent(SlimeBionicEntity entity, BionicTrait trait, BionicTraitResult result) {
		this.entity = entity; this.trait = trait; this.result = result;
	}
	public SlimeBionicEntity getEntity() { return entity; }
	public BionicTrait getTrait() { return trait; }
	public BionicTraitResult getResult() { return result; }

	public static final class Tick extends BionicTraitEvent {
		public Tick(SlimeBionicEntity entity, BionicTrait trait, BionicTraitResult result) { super(entity, trait, result); }
	}
	public static final class Melee extends BionicTraitEvent {
		private final LivingEntity target;
		private final Set<SurgicalAssembly.CombinationMember> arm;
		public Melee(SlimeBionicEntity entity, BionicTrait trait, BionicTraitResult result,
			LivingEntity target, Set<SurgicalAssembly.CombinationMember> arm) {
			super(entity, trait, result); this.target = target; this.arm = Set.copyOf(arm);
		}
		public LivingEntity getTarget() { return target; }
		public Set<SurgicalAssembly.CombinationMember> getArm() { return arm; }
	}
	public static final class Hurt extends BionicTraitEvent {
		private final DamageSource source;
		private float amount;
		public Hurt(SlimeBionicEntity entity, BionicTrait trait, BionicTraitResult result, DamageSource source, float amount) {
			super(entity, trait, result); this.source = source; setAmount(amount);
		}
		public DamageSource getSource() { return source; }
		public float getAmount() { return amount; }
		public void setAmount(float amount) {
			if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid damage amount");
			this.amount = amount;
		}
	}
}
