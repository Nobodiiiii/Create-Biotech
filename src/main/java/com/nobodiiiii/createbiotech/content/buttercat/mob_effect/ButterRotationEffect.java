package com.nobodiiiii.createbiotech.content.buttercat.mob_effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class ButterRotationEffect extends MobEffect {
    public static final float ROTATION_ANGULAR_SPEED = 3.14159f / 2;
    public ButterRotationEffect() {
        super(MobEffectCategory.NEUTRAL,  0xFFAA00);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if(entity.level().isClientSide) return;
        float rotationSpeed = ROTATION_ANGULAR_SPEED * (6 * amplifier + 1);
        float newYaw = entity.getYRot() + rotationSpeed;

        entity.setYRot(newYaw);
        entity.setYHeadRot(newYaw);
        entity.setYBodyRot(newYaw);


    }

    @Override
    public boolean isDurationEffectTick(int p_19455_, int p_19456_) {
        return true;
    }
}

