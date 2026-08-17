package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SonicDogCannonStunEffect extends MobEffect {
	/** 1.20.1 identifies attribute modifiers by UUID rather than by resource location. */
	private static final String STUN_SPEED_MODIFIER = "d3c0f3a4-6b57-4e2f-9d5a-2f0a1c6b8e21";

	public SonicDogCannonStunEffect() {
		super(MobEffectCategory.HARMFUL, 0xFF8C00);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, STUN_SPEED_MODIFIER, -0.5d,
			AttributeModifier.Operation.ADDITION);
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return duration > 0;
	}
}
