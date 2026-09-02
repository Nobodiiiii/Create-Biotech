package com.nobodiiiii.createbiotech.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only timing state for bio-packager release animations. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BioPackagerReleaseAnimationHandler {
	public static final int DURATION_TICKS = 10;
	private static final int ENTITY_PACKET_WAIT_TICKS = 40;
	private static final Map<Integer, ReleaseAnimation> ACTIVE = new HashMap<>();

	private BioPackagerReleaseAnimationHandler() {}

	public static void start(int entityId, Level level) {
		ACTIVE.put(entityId, new ReleaseAnimation(level.getGameTime()));
	}

	/**
	 * Returns eased progress in [0, 1), or -1 when this entity has no active release animation.
	 * Timing begins on its first real render so packet ordering cannot shorten the ten-tick effect.
	 */
	public static float getProgress(LivingEntity entity, float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null || entity.level() != level || level.getEntity(entity.getId()) != entity)
			return -1;
		ReleaseAnimation animation = ACTIVE.get(entity.getId());
		if (animation == null)
			return -1;

		float renderTime = level.getGameTime() + partialTick;
		if (!Float.isFinite(animation.startRenderTime))
			animation.startRenderTime = renderTime;
		float linear = Mth.clamp((renderTime - animation.startRenderTime) / DURATION_TICKS, 0, 1);
		if (linear >= 1) {
			ACTIVE.remove(entity.getId());
			return -1;
		}
		return linear * linear * (3 - 2 * linear);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}

		long gameTime = level.getGameTime();
		Iterator<ReleaseAnimation> iterator = ACTIVE.values().iterator();
		while (iterator.hasNext()) {
			ReleaseAnimation animation = iterator.next();
			if (Float.isFinite(animation.startRenderTime)) {
				if (gameTime - animation.startRenderTime >= DURATION_TICKS)
					iterator.remove();
			} else if (gameTime - animation.receivedTick >= ENTITY_PACKET_WAIT_TICKS) {
				iterator.remove();
			}
		}
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide())
			ACTIVE.clear();
	}

	private static final class ReleaseAnimation {
		private final long receivedTick;
		private float startRenderTime = Float.NaN;

		private ReleaseAnimation(long receivedTick) {
			this.receivedTick = receivedTick;
		}
	}
}
