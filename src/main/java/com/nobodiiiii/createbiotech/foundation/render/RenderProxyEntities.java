package com.nobodiiiii.createbiotech.foundation.render;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

/**
 * Identity registry for client-only render proxies: entities that are never added to a level and
 * exist only to be drawn by a renderer that already applies its own transforms.
 * <p>
 * Mixins decorating entity rendering must skip proxies, or a transform meant for the real world
 * entity gets applied a second time on top of the one the renderer already did. Identity lookup is
 * used rather than a persistent-data flag so that probing an ordinary entity stays allocation-free.
 * <p>
 * Shared by the render thread and Flywheel workers. Even lookups must be synchronized because
 * {@link WeakHashMap} may expunge collected keys during a read.
 */
public final class RenderProxyEntities {
	private static final Set<Entity> PROXIES = Collections.newSetFromMap(new WeakHashMap<>());

	private RenderProxyEntities() {}

	public static synchronized <T extends Entity> T mark(T entity) {
		if (entity != null)
			PROXIES.add(entity);
		return entity;
	}

	public static synchronized void forget(Entity entity) {
		if (!PROXIES.isEmpty())
			PROXIES.remove(entity);
	}

	public static synchronized boolean isProxy(Entity entity) {
		return !PROXIES.isEmpty() && PROXIES.contains(entity);
	}
}
