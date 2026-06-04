package com.nobodiiiii.createbiotech.content.buttercat.event;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.mob_effect.ButterRotationEffect;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = ButterCatModule.MODID, value = Dist.CLIENT)
public class RotationHandler {
    static float acceleration = 0;
    static int amplifier = -1;
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null ) return;
        MobEffectInstance effect = player.getEffect(ModEffects.BUTTER_ROTATION_EFFECT.get());

        acceleration = Mth.clamp(acceleration+0.1f*(effect!=null?1:-1),0,1);
        if(effect!=null){
            int amplifier0 = player.getEffect(ModEffects.BUTTER_ROTATION_EFFECT.get()).getAmplifier();
            if(amplifier0 != amplifier)
                amplifier = amplifier0;
        }
    }

    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || acceleration==0) return;

        float pt = event.renderTickTime;
        float y= player.getYRot()+ getAngle(pt,amplifier);

        player.setYRot(y);
    }
    public static float getAngle(float pt,int a) {
        return (getTickAngleSpeed(a) * pt ) % 360;
    }
    private static float getTickAngleSpeed(int amplifier){
        return (3*amplifier+1)* ButterRotationEffect.ROTATION_ANGULAR_SPEED * acceleration;
    }
}

