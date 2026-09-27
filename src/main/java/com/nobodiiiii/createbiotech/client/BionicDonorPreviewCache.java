package com.nobodiiiii.createbiotech.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.BakedDonorPreview;
import com.nobodiiiii.createbiotech.foundation.render.RenderProxyEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Render-thread cooperative queue, limited by visibility, entry count and retained mesh bytes. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicDonorPreviewCache {
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final int MAX_CACHED = 64;
	private static final int MAX_PENDING = 32;
	private static final int MAX_FAILURES = 256;
	private static final Map<EntityType<?>, BakedDonorPreview> CACHE = new LinkedHashMap<>(64, .75f, true);
	private static final Map<EntityType<?>, Long> PENDING = new LinkedHashMap<>();
	private static final Set<EntityType<?>> FAILURES = new LinkedHashSet<>();
	private static Level level;
	private static long frame;
	private static int bytes;

	private BionicDonorPreviewCache() {}

	@Nullable
	public static BakedDonorPreview getOrSchedule(EntityType<?> type) {
		ensureLevel();
		BakedDonorPreview preview = CACHE.get(type);
		if (preview == null && level != null && !FAILURES.contains(type)) {
			PENDING.put(type, frame);
			while (PENDING.size() > MAX_PENDING)
				PENDING.remove(PENDING.keySet().iterator().next());
		}
		return preview;
	}

	public static boolean failed(EntityType<?> type) { return FAILURES.contains(type); }

	@SubscribeEvent
	public static void onFrame(RenderFrameEvent.Post event) {
		ensureLevel();
		// Only requests refreshed by this frame's visible cells remain eligible.
		PENDING.entrySet().removeIf(entry -> entry.getValue() != frame);
		frame++;
		if (level == null || Minecraft.getInstance().screen == null || PENDING.isEmpty())
			return;
		EntityType<?> type = PENDING.keySet().iterator().next();
		PENDING.remove(type);
		// One indivisible model capture at most per frame. Vertex/layer limits bound the capture itself.
		LivingEntity entity = null;
		try {
			if (!(type.create(level) instanceof LivingEntity living))
				throw new IllegalStateException("Donor no longer creates a living entity");
			entity = RenderProxyEntities.mark(living);
			if (entity instanceof Mob mob)
				mob.setNoAi(true);
			entity.setCustomNameVisible(false);
			entity.setSilent(true);
			entity.setOnGround(true);
			entity.tickCount = 0;
			entity.hurtTime = entity.deathTime = 0;
			BakedDonorPreview preview = BakedDonorPreview.capture(entity);
			if (preview.bytes() > MAX_BYTES)
				throw new IllegalStateException("Donor preview exceeds cache budget");
			while (!CACHE.isEmpty() && (CACHE.size() >= MAX_CACHED || bytes + preview.bytes() > MAX_BYTES)) {
				EntityType<?> oldest = CACHE.keySet().iterator().next();
				bytes -= CACHE.remove(oldest).bytes();
			}
			CACHE.put(type, preview);
			bytes += preview.bytes();
		} catch (RuntimeException | StackOverflowError exception) {
			FAILURES.add(type);
			while (FAILURES.size() > MAX_FAILURES)
				FAILURES.remove(FAILURES.iterator().next());
			LogUtils.getLogger().warn("Unable to prepare bionic donor preview for {}", BuiltInRegistries.ENTITY_TYPE.getKey(type), exception);
		} finally {
			if (entity != null)
				RenderProxyEntities.forget(entity);
		}
	}

	private static void ensureLevel() {
		Level current = Minecraft.getInstance().level;
		if (level != current) {
			clear();
			level = current;
		}
	}

	public static void clear() {
		CACHE.clear();
		PENDING.clear();
		FAILURES.clear();
		bytes = 0;
		level = null;
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
