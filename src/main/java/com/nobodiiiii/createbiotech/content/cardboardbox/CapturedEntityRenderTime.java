package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scoped render-time override for captured entity displays.
 * <p>
 * Some third-party entity models ignore the partial tick supplied to their
 * renderer and read Minecraft's global timer directly. Captured entities do not
 * tick, so allowing that global value to wrap from one to zero makes their
 * renderer-owned interpolation visibly snap. This scope lets the client timer
 * mixin return the same partial tick used by the captured-entity bake pass.
 * <p>
 * The fast global count keeps ordinary timer reads to one rejection branch; the
 * actual scope is thread-local so work on another thread can never observe the
 * pinned value. Scope tokens validate thread ownership, nesting order and
 * double-close errors.
 */
public final class CapturedEntityRenderTime {
	public static final float FIXED_PARTIAL_TICK = 1.0f;

	private static final AtomicInteger ACTIVE_SCOPES = new AtomicInteger();
	private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

	private CapturedEntityRenderTime() {}

	public static Scope open() {
		Scope scope = new Scope(Thread.currentThread(), CURRENT_SCOPE.get());
		CURRENT_SCOPE.set(scope);
		ACTIVE_SCOPES.incrementAndGet();
		return scope;
	}

	/** Runs a live captured-entity display at the same fixed frame used by icon baking. */
	public static void runWithFixedPartialTick(Runnable action) {
		try (Scope ignored = open()) {
			action.run();
		}
	}

	public static float overridePartialTick(float original) {
		if (ACTIVE_SCOPES.get() == 0)
			return original;
		return CURRENT_SCOPE.get() == null ? original : FIXED_PARTIAL_TICK;
	}

	public static final class Scope implements AutoCloseable {
		private final Thread owner;
		private final Scope parent;
		private boolean closed;

		private Scope(Thread owner, Scope parent) {
			this.owner = owner;
			this.parent = parent;
		}

		@Override
		public void close() {
			if (closed)
				throw new IllegalStateException("Captured entity render time scope was already closed");
			if (Thread.currentThread() != owner)
				throw new IllegalStateException("Captured entity render time scope closed on a different thread");
			if (CURRENT_SCOPE.get() != this)
				throw new IllegalStateException("Captured entity render time scopes must close in LIFO order");

			closed = true;
			if (parent == null)
				CURRENT_SCOPE.remove();
			else
				CURRENT_SCOPE.set(parent);
			if (ACTIVE_SCOPES.decrementAndGet() < 0) {
				ACTIVE_SCOPES.set(0);
				throw new IllegalStateException("Captured entity render time scope count underflow");
			}
		}
	}
}
