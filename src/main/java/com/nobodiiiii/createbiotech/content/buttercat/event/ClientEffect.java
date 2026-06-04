package com.nobodiiiii.createbiotech.content.buttercat.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Map;

public final class ClientEffect{
    public enum EffectType{
        BUTTER, SUPER_BUTTER,BREAD,CAT,FAIL
    }

    static Map<EffectType, SoundEvent> SOUND_MAP = Map.of(
            EffectType.BUTTER, SoundEvents.SLIME_JUMP_SMALL,
            EffectType.SUPER_BUTTER, SoundEvents.SLIME_JUMP_SMALL,
            EffectType.BREAD, SoundEvents.CAT_AMBIENT,
            EffectType.CAT, SoundEvents.CAT_AMBIENT,
            EffectType.FAIL, SoundEvents.CAT_HISS
    );


    static Map<EffectType, ParticleOptions> PARTICLE_MAP = Map.of(
            EffectType.BUTTER, ParticleTypes.HAPPY_VILLAGER,
            EffectType.SUPER_BUTTER, ParticleTypes.END_ROD,
            EffectType.BREAD, ParticleTypes.HAPPY_VILLAGER,
            EffectType.CAT, ParticleTypes.HEART,
            EffectType.FAIL, ParticleTypes.ANGRY_VILLAGER
    );

    public static void create(Level level, BlockPos pos, EffectType type){
        if (!(level instanceof ServerLevel serverLevel)) return;

        ParticleOptions particle = PARTICLE_MAP.get(type);
        SoundEvent soundEvent = SOUND_MAP.get(type);

        serverLevel.playSound(null, pos, soundEvent,
                SoundSource.BLOCKS, 1.0f, 1.0f);

        (serverLevel).sendParticles(
                particle,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                5, 0.25, 0.25, 0.25, 0.1);
    }
}
