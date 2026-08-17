package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Render-thread-only cache and cooperative scheduler for captured entity icons.
 * <p>
 * Icons are prepared on demand, once per captured entity and box face: the
 * entity is constructed, its geometry measured, and the clipped face icon baked
 * into a {@link BakedCapturedEntityIcon}. Per-frame rendering only replays
 * baked meshes; entity construction, measurement, and baking stay on the render
 * thread behind a per-frame budget. Prototype icons (spawn-egg style boxes with
 * no per-instance NBT) are level-independent and survive dimension changes;
 * everything level-bound is dropped when the level goes away.
 */
@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class CapturedEntityRenderManager {
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final int ENTITY_CACHE_CAPACITY = 64;
	private static final int ICON_CACHE_CAPACITY = 1_024;
	private static final int FAILURE_CACHE_CAPACITY = 256;
	private static final int PENDING_CAPACITY = 512;
	private static final long ENTITY_IDLE_NANOS = 60L * 1_000_000_000L;
	private static final long ICON_IDLE_NANOS = 10L * 60L * 1_000_000_000L;
	private static final long FAILURE_RETRY_NANOS = 30L * 1_000_000_000L;
	private static final long REQUEST_IDLE_NANOS = 2L * 1_000_000_000L;
	private static final long PRUNE_INTERVAL_NANOS = 1_000_000_000L;
	private static final long TARGET_FRAME_NANOS = 18_500_000L;
	private static final long MIN_PREPARATION_BUDGET_NANOS = 250_000L;
	private static final long GAME_PREPARATION_BUDGET_NANOS = 750_000L;
	private static final long SCREEN_PREPARATION_BUDGET_NANOS = 1_500_000L;

	private static final BoundedLruMap<RenderKey, CacheEntry<LivingEntity>> ENTITY_CACHE =
		new BoundedLruMap<>(ENTITY_CACHE_CAPACITY);
	private static final BoundedLruMap<RenderKey, CacheEntry<IconAssets>> ICON_CACHE =
		new BoundedLruMap<>(ICON_CACHE_CAPACITY);
	private static final BoundedLruMap<RenderKey, Long> FAILURE_CACHE =
		new BoundedLruMap<>(FAILURE_CACHE_CAPACITY);
	private static final BoundedLruMap<RenderKey, PendingRequest> PENDING =
		new BoundedLruMap<>(PENDING_CAPACITY);

	@Nullable
	private static Level activeLevel;
	private static long lastFrameNanos;
	private static long lastPruneNanos;
	private static double averageFrameNanos = 16_666_667.0d;

	private CapturedEntityRenderManager() {
	}

	@Nullable
	static BakedCapturedEntityIcon getOrSchedule(CapturedEntityBoxHelper.CapturedEntityRenderData renderData,
		boolean largeFace, RequestPriority priority) {
		if (!CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get())
			return null;

		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null)
			return null;

		ensureLevel(level);
		long now = System.nanoTime();
		RenderKey key = RenderKey.of(renderData);
		Long retryAtNanos = FAILURE_CACHE.get(key);
		if (retryAtNanos != null) {
			if (now < retryAtNanos)
				return null;
			FAILURE_CACHE.remove(key);
		}

		IconAssets assets = getCached(ICON_CACHE, key, now);
		if (assets != null) {
			BakedCapturedEntityIcon icon = assets.forFace(largeFace);
			if (icon != null)
				return icon;
		}

		enqueue(key, renderData, largeFace, priority, now);
		return null;
	}

	public static void clearForResourceReload() {
		clearAll();
	}

	@SubscribeEvent
	public static void onRenderFrame(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;

		if (!CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get()) {
			if (activeLevel != null || !ENTITY_CACHE.isEmpty() || !ICON_CACHE.isEmpty() || !FAILURE_CACHE.isEmpty()
				|| !PENDING.isEmpty())
				clearAll();
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null || minecraft.player == null) {
			if (activeLevel != null)
				clearLevelBound();
			return;
		}

		ensureLevel(level);
		long now = System.nanoTime();
		updateFrameTiming(now);
		if (now - lastPruneNanos >= PRUNE_INTERVAL_NANOS) {
			lastPruneNanos = now;
			pruneExpiredEntries(now);
		}
		processPending(minecraft, now);
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		clearLevelBound();
	}

	private static void updateFrameTiming(long now) {
		if (lastFrameNanos != 0L) {
			long duration = Math.min(now - lastFrameNanos, 250_000_000L);
			averageFrameNanos += (duration - averageFrameNanos) * 0.1d;
		}
		lastFrameNanos = now;
	}

	private static void ensureLevel(Level level) {
		if (activeLevel == level)
			return;
		clearLevelBound();
		activeLevel = level;
	}

	private static void enqueue(RenderKey key, CapturedEntityBoxHelper.CapturedEntityRenderData renderData,
		boolean largeFace, RequestPriority priority, long now) {
		PendingRequest current = PENDING.get(key);
		if (current == null) {
			PENDING.put(key, new PendingRequest(renderData, largeFace, priority, now));
			return;
		}

		current.lastRequestedNanos = now;
		current.requestFace(largeFace);
		if (priority.weight > current.priority.weight)
			current.priority = priority;
	}

	private static void processPending(Minecraft minecraft, long startNanos) {
		if (PENDING.isEmpty())
			return;

		Level level = activeLevel;
		if (level == null)
			return;

		long configuredBudget = minecraft.screen == null
			? GAME_PREPARATION_BUDGET_NANOS : SCREEN_PREPARATION_BUDGET_NANOS;
		long measuredHeadroom = Math.max(MIN_PREPARATION_BUDGET_NANOS,
			(long) ((TARGET_FRAME_NANOS - averageFrameNanos) * 0.25d));
		long budget = Math.min(configuredBudget, measuredHeadroom);
		int maxTasks = minecraft.screen == null ? 1 : 2;
		int completed = 0;

		PendingSelection selection = selectPending(startNanos);
		while (selection != null && completed < maxTasks) {
			PENDING.remove(selection.key);
			prepare(selection.key, selection.request, level);
			completed++;
			if (System.nanoTime() - startNanos >= budget)
				break;

			selection = selectPending(startNanos);
		}
	}

	@Nullable
	private static PendingSelection selectPending(long now) {
		RenderKey selectedKey = null;
		PendingRequest selected = null;
		Iterator<Map.Entry<RenderKey, PendingRequest>> iterator = PENDING.entrySet()
			.iterator();
		while (iterator.hasNext()) {
			Map.Entry<RenderKey, PendingRequest> entry = iterator.next();
			PendingRequest request = entry.getValue();
			if (now - request.lastRequestedNanos > REQUEST_IDLE_NANOS) {
				iterator.remove();
				continue;
			}

			if (selected == null || request.priority.weight > selected.priority.weight
				|| request.priority == selected.priority
					&& request.lastRequestedNanos > selected.lastRequestedNanos) {
				selectedKey = entry.getKey();
				selected = request;
			}
		}
		return selected == null ? null : new PendingSelection(selectedKey, selected);
	}

	private static void prepare(RenderKey key, PendingRequest request, Level level) {
		try {
			long now = System.nanoTime();
			LivingEntity entity = getCached(ENTITY_CACHE, key, now);
			if (entity == null) {
				Entity loaded = CapturedEntityBoxHelper.createCapturedEntity(request.renderData, level);
				if (!(loaded instanceof LivingEntity living))
					throw new IllegalStateException(
						"Captured entity is not a living entity: " + request.renderData.entityId());
				stabilize(living);
				entity = living;
			}

			IconAssets assets = getCached(ICON_CACHE, key, now);
			if (assets == null)
				assets = new IconAssets(CapturedEntityBoxIconRenderer.prepareGeometry(entity));
			if (request.smallFaceRequested && assets.small == null)
				assets.small = CapturedEntityBoxIconRenderer.bakeIcon(entity, assets.profile, false);
			if (request.largeFaceRequested && assets.large == null)
				assets.large = CapturedEntityBoxIconRenderer.bakeIcon(entity, assets.profile, true);

			ENTITY_CACHE.put(key, new CacheEntry<>(entity, now));
			ICON_CACHE.put(key, new CacheEntry<>(assets, now));
		} catch (RuntimeException exception) {
			FAILURE_CACHE.put(key, System.nanoTime() + FAILURE_RETRY_NANOS);
			LOGGER.warn("Unable to prepare captured entity icon for {}", request.renderData.entityId(), exception);
		}
	}

	private static void stabilize(LivingEntity entity) {
		if (entity instanceof Mob mob)
			mob.setNoAi(true);
		entity.setSilent(true);
		entity.setOnGround(true);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.tickCount = 0;
		entity.hurtTime = 0;
		entity.deathTime = 0;
		entity.hurtMarked = false;
		entity.setYRot(0.0f);
		entity.yRotO = 0.0f;
		entity.setXRot(0.0f);
		entity.xRotO = 0.0f;
		entity.setYBodyRot(0.0f);
		entity.yBodyRotO = 0.0f;
		entity.yHeadRot = 0.0f;
		entity.yHeadRotO = 0.0f;
	}

	@Nullable
	private static <T> T getCached(BoundedLruMap<RenderKey, CacheEntry<T>> cache, RenderKey key, long now) {
		CacheEntry<T> entry = cache.get(key);
		if (entry == null)
			return null;
		entry.lastAccessNanos = now;
		return entry.value;
	}

	private static void pruneExpiredEntries(long now) {
		pruneCache(ENTITY_CACHE, ENTITY_IDLE_NANOS, now);
		pruneCache(ICON_CACHE, ICON_IDLE_NANOS, now);
		FAILURE_CACHE.entrySet()
			.removeIf(entry -> now >= entry.getValue());
	}

	private static <T> void pruneCache(BoundedLruMap<RenderKey, CacheEntry<T>> cache, long maxIdleNanos, long now) {
		cache.entrySet()
			.removeIf(entry -> now - entry.getValue().lastAccessNanos > maxIdleNanos);
	}

	/**
	 * Drops everything tied to the active level: live entities (which pin their
	 * client level), pending requests and failures (whose render data may
	 * reference stacks from that level), and icons keyed by tag identity.
	 * Prototype icons carry no level references and are kept.
	 */
	private static void clearLevelBound() {
		ENTITY_CACHE.clear();
		FAILURE_CACHE.clear();
		PENDING.clear();
		ICON_CACHE.entrySet()
			.removeIf(entry -> entry.getKey() instanceof TagIdentityKey);
		activeLevel = null;
		lastFrameNanos = 0L;
	}

	private static void clearAll() {
		clearLevelBound();
		ICON_CACHE.clear();
	}

	private static final class IconAssets {
		private final CapturedEntityBoxIconRenderer.GeometryProfile profile;
		@Nullable
		private BakedCapturedEntityIcon small;
		@Nullable
		private BakedCapturedEntityIcon large;

		private IconAssets(CapturedEntityBoxIconRenderer.GeometryProfile profile) {
			this.profile = profile;
		}

		@Nullable
		private BakedCapturedEntityIcon forFace(boolean largeFace) {
			return largeFace ? large : small;
		}
	}

	enum RequestPriority {
		WORLD(1),
		GUI(2),
		HELD(3);

		private final int weight;

		RequestPriority(int weight) {
			this.weight = weight;
		}

		static RequestPriority forDisplayContext(ItemDisplayContext context) {
			return switch (context) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> HELD;
			case GUI -> GUI;
			default -> WORLD;
			};
		}
	}

	private interface RenderKey {
		static RenderKey of(CapturedEntityBoxHelper.CapturedEntityRenderData renderData) {
			return renderData.prototype()
				? new PrototypeKey(renderData.entityId()) : new TagIdentityKey(renderData.root());
		}
	}

	private record PrototypeKey(String entityId) implements RenderKey {
	}

	/**
	 * Identity of the stack's own tag. Value equality is deliberately avoided:
	 * hashing captured entity NBT every frame would cost more than the occasional
	 * rebake a re-synced stack triggers.
	 */
	private static final class TagIdentityKey implements RenderKey {
		private final CompoundTag root;
		private final int hash;

		private TagIdentityKey(CompoundTag root) {
			this.root = root;
			hash = System.identityHashCode(root);
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof TagIdentityKey other && root == other.root;
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	private static final class PendingRequest {
		private final CapturedEntityBoxHelper.CapturedEntityRenderData renderData;
		private RequestPriority priority;
		private long lastRequestedNanos;
		private boolean smallFaceRequested;
		private boolean largeFaceRequested;

		private PendingRequest(CapturedEntityBoxHelper.CapturedEntityRenderData renderData, boolean largeFace,
			RequestPriority priority, long now) {
			this.renderData = renderData;
			this.priority = priority;
			lastRequestedNanos = now;
			requestFace(largeFace);
		}

		private void requestFace(boolean largeFace) {
			if (largeFace)
				largeFaceRequested = true;
			else
				smallFaceRequested = true;
		}
	}

	private record PendingSelection(RenderKey key, PendingRequest request) {
	}

	private static final class CacheEntry<T> {
		private final T value;
		private long lastAccessNanos;

		private CacheEntry(T value, long lastAccessNanos) {
			this.value = value;
			this.lastAccessNanos = lastAccessNanos;
		}
	}

	private static final class BoundedLruMap<K, V> extends LinkedHashMap<K, V> {
		private final int capacity;

		private BoundedLruMap(int capacity) {
			super(capacity, 0.75f, true);
			this.capacity = capacity;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			return size() > capacity;
		}
	}
}
