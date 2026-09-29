package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

/** Vanilla cloud lifetime and particles, with allegiance checked before every effect, including healing. */
public final class BionicAreaEffectCloud extends AreaEffectCloud {
	private PotionContents contents = PotionContents.EMPTY;
	private final Map<UUID, Integer> affected = new HashMap<>();
	private int reapplicationDelay = 20;

	public BionicAreaEffectCloud(EntityType<? extends BionicAreaEffectCloud> type, Level level) {
		super(type, level);
	}

	@Override
	public void setPotionContents(PotionContents contents) {
		this.contents = contents;
		// Leave the base cloud empty so its private application loop cannot affect allies.
		super.setPotionContents(PotionContents.EMPTY);
		if (getParticle() instanceof ColorParticleOption color)
			setParticle(ColorParticleOption.create(color.getType(), FastColor.ARGB32.opaque(contents.getColor())));
	}

	@Override
	public void addEffect(MobEffectInstance effect) {
		setPotionContents(contents.withEffectAdded(effect));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide || isRemoved() || tickCount < getWaitTime() || tickCount % 5 != 0) return;
		affected.values().removeIf(expiry -> tickCount >= expiry);
		List<MobEffectInstance> effects = new ArrayList<>();
		contents.potion().ifPresent(potion -> potion.value().getEffects().forEach(effect -> effects.add(
			new MobEffectInstance(effect.getEffect(), effect.mapDuration(duration -> duration / 4),
				effect.getAmplifier(), effect.isAmbient(), effect.isVisible()))));
		effects.addAll(contents.customEffects());
		if (effects.isEmpty()) return;
		for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox())) {
			if (affected.containsKey(victim.getUUID()) || !victim.isAffectedByPotions()
				|| BionicCombatEvents.protectedFrom(this, victim) || effects.stream().noneMatch(victim::canBeAffected)) continue;
			double dx = victim.getX() - getX(), dz = victim.getZ() - getZ();
			if (dx * dx + dz * dz > getRadius() * getRadius()) continue;
			affected.put(victim.getUUID(), tickCount + reapplicationDelay);
			for (MobEffectInstance effect : effects) {
				if (effect.getEffect().value().isInstantenous())
					effect.getEffect().value().applyInstantenousEffect(this, getOwner(), victim, effect.getAmplifier(), 0.5);
				else victim.addEffect(new MobEffectInstance(effect), this);
			}
			if (getRadiusOnUse() != 0) {
				float radius = getRadius() + getRadiusOnUse();
				if (radius < 0.5f) { discard(); return; }
				setRadius(radius);
			}
			if (getDurationOnUse() != 0) {
				setDuration(getDuration() + getDurationOnUse());
				if (getDuration() <= 0) { discard(); return; }
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.put("potion_contents", PotionContents.CODEC.encodeStart(registryAccess().createSerializationContext(NbtOps.INSTANCE), contents).getOrThrow());
		tag.putInt("ReapplicationDelay", reapplicationDelay);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		reapplicationDelay = Math.max(1, tag.getInt("ReapplicationDelay"));
		affected.clear();
	}
}
