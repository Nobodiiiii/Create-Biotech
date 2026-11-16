package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Runs only Mob's ambient-sound clock; the cached proxy never joins or ticks in the world. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class CaptureNetAmbientSounds {
	// Stack copies share their immutable component, preserving the clock across slots and dropping.
	// Values never retain the weak component key or write back into captured entity data.
	private static final Cache<CustomData, AmbientState> STATES = CacheBuilder.newBuilder()
		.weakKeys()
		.maximumSize(512)
		.expireAfterAccess(1, TimeUnit.MINUTES)
		.build();

	private CaptureNetAmbientSounds() {}

	static void captured(ItemStack stack, ServerLevel level, int ambientSoundTime) {
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		if (component == null) return;
		AmbientState state = new AmbientState(stack, level);
		if (state.mob != null) state.mob.ambientSoundTime = ambientSoundTime;
		state.lastTick = level.getGameTime();
		STATES.put(component, state);
	}

	static void tick(ItemStack stack, ServerLevel level, Entity holder) {
		if (!CaptureNetItem.isOccupied(stack)) return;
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		if (component == null) return;
		AmbientState state = STATES.getIfPresent(component);
		if (state == null || state.level != level) {
			state = new AmbientState(stack, level);
			STATES.put(component, state);
		}
		long now = level.getGameTime();
		if (state.mob == null || state.lastTick == now) return;
		state.lastTick = now;
		Mob mob = state.mob;
		mob.setPos(holder.position());
		// Match ref/1.21.1/Minecraft/net/minecraft/world/entity/Mob.java#baseTick.
		if (mob.isAlive() && mob.getRandom().nextInt(1000) < mob.ambientSoundTime++) {
			mob.ambientSoundTime = -mob.getAmbientSoundInterval();
			mob.playAmbientSound();
		}
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!event.getLevel().isClientSide())
			STATES.asMap().values().removeIf(state -> state.level == event.getLevel());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		STATES.invalidateAll();
	}

	private static final class AmbientState {
		private final ServerLevel level;
		@Nullable
		private final Mob mob;
		private long lastTick = Long.MIN_VALUE;

		private AmbientState(ItemStack stack, ServerLevel level) {
			this.level = level;
			Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
			mob = entity instanceof Mob captured ? captured : null;
			if (mob != null) mob.ambientSoundTime = -mob.getAmbientSoundInterval();
		}
	}
}
