package com.nobodiiiii.createbiotech.foundation.render;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Holds one lazily created entity used purely as a render subject, never ticked
 * and never added to a level.
 *
 * <p>Display code needs a live entity instance to hand to the entity renderer,
 * but constructing one per frame is wasteful and constructing one eagerly is
 * impossible before a level exists. This caches a single instance keyed by the
 * level (so it is dropped on world change) and optionally by a caller-supplied
 * key, and keeps it in a state that renders deterministically: no AI, silent,
 * grounded, and pinned to frame zero of every animation.
 *
 * @param <T> entity type being rendered
 * @param <K> cache key type; {@link Void} when the level alone identifies the
 *            entity
 */
public final class CachedRenderEntity<T extends LivingEntity, K> {

	private final BiFunction<Level, K, T> factory;
	private BiPredicate<K, K> keyEquality = (a, b) -> a == b || (a != null && a.equals(b));
	private UnaryOperator<K> keyCopier = UnaryOperator.identity();
	@Nullable
	private Consumer<T> configurer;

	@Nullable
	private T cachedEntity;
	@Nullable
	private Level cachedLevel;
	@Nullable
	private K cachedKey;

	private CachedRenderEntity(BiFunction<Level, K, T> factory) {
		this.factory = factory;
	}

	/**
	 * Caches one instance of {@code entityType}, keyed by level alone.
	 */
	public static <T extends LivingEntity> CachedRenderEntity<T, Void> of(EntityType<T> entityType) {
		return new CachedRenderEntity<>((level, key) -> entityType.create(level));
	}

	/**
	 * Caches an entity built by {@code factory}, keyed by level alone.
	 */
	public static <T extends LivingEntity> CachedRenderEntity<T, Void> of(Function<Level, T> factory) {
		return new CachedRenderEntity<>((level, key) -> factory.apply(level));
	}

	/**
	 * Caches an entity built by {@code factory}, keyed by level plus the key passed
	 * to {@link #get(Level, Object)}. Declare the lambda's parameter types so the
	 * key type is inferred.
	 */
	public static <T extends LivingEntity, K> CachedRenderEntity<T, K> keyed(BiFunction<Level, K, T> factory) {
		return new CachedRenderEntity<>(factory);
	}

	/**
	 * Overrides key comparison. Needed for keys whose {@code equals} is the wrong
	 * question, such as {@link net.minecraft.world.item.ItemStack}.
	 */
	public CachedRenderEntity<T, K> keyEquality(BiPredicate<K, K> keyEquality) {
		this.keyEquality = keyEquality;
		return this;
	}

	/**
	 * Defensively copies the key before storing it, for mutable key types.
	 */
	public CachedRenderEntity<T, K> keyCopier(UnaryOperator<K> keyCopier) {
		this.keyCopier = keyCopier;
		return this;
	}

	/**
	 * Extra setup applied once, when the entity is created.
	 */
	public CachedRenderEntity<T, K> configure(Consumer<T> configurer) {
		this.configurer = configurer;
		return this;
	}

	@Nullable
	public T get(@Nullable Level level) {
		return get(level, null);
	}

	@Nullable
	public T get(@Nullable Level level, @Nullable K key) {
		if (level == null)
			return null;

		if (cachedEntity != null && cachedLevel == level && keysMatch(cachedKey, key)) {
			resetAnimationState(cachedEntity);
			return cachedEntity;
		}

		T entity = factory.apply(level, key);
		if (entity == null)
			return null;

		if (configurer != null)
			configurer.accept(entity);
		applyDisplayState(entity);

		cachedLevel = level;
		cachedKey = key == null ? null : keyCopier.apply(key);
		cachedEntity = entity;
		return entity;
	}

	public void clear() {
		cachedEntity = null;
		cachedLevel = null;
		cachedKey = null;
	}

	private boolean keysMatch(@Nullable K cached, @Nullable K key) {
		if (cached == null || key == null)
			return cached == key;
		return keyEquality.test(cached, key);
	}

	/**
	 * Full one-time setup: inert, silent, grounded, facing south, at frame zero.
	 * Orientation is also pinned per render by
	 * {@link EntityRenderHelper#renderUnoriented}; zeroing it here keeps the cached
	 * instance consistent for callers that read it directly.
	 */
	private static void applyDisplayState(LivingEntity entity) {
		if (entity instanceof Mob mob)
			mob.setNoAi(true);
		entity.setSilent(true);
		entity.setOnGround(true);
		entity.setYRot(0.0f);
		entity.yRotO = 0.0f;
		entity.setXRot(0.0f);
		entity.xRotO = 0.0f;
		entity.setYBodyRot(0.0f);
		entity.yBodyRotO = 0.0f;
		entity.yHeadRot = 0.0f;
		entity.yHeadRotO = 0.0f;
		resetAnimationState(entity);
	}

	/**
	 * Re-applied on every fetch so a cached entity cannot drift into a hurt, dying
	 * or mid-animation pose between frames.
	 */
	private static void resetAnimationState(LivingEntity entity) {
		entity.tickCount = 0;
		entity.hurtTime = 0;
		entity.deathTime = 0;
		entity.hurtMarked = false;
	}
}
