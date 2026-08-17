package com.nobodiiiii.createbiotech.content.cardboardbox;

/**
 * Scoped render-time override for captured entity icons.
 * <p>
 * Some third-party entity models ignore the partial tick supplied to their
 * renderer and read Minecraft's global timer directly. Captured entities do not
 * tick, so allowing that global value to wrap from one to zero makes their
 * renderer-owned interpolation visibly snap. This scope lets the client timer
 * mixin return the same partial tick used by the captured-entity bake pass.
 * <p>
 * Bake passes only run on the render thread, so a plain counter keeps the
 * timer mixin's per-call cost to one static read. Another thread reading the
 * timer during a bake would observe the pinned value; bakes are rare and
 * short, and the icon pipeline itself never runs off the render thread.
 */
public final class CapturedEntityRenderTime {
	public static final float FIXED_PARTIAL_TICK = 1.0f;

	private static int depth;

	private CapturedEntityRenderTime() {}

	static void push() {
		depth++;
	}

	static void pop() {
		if (depth <= 0)
			throw new IllegalStateException("Captured entity render time scope is unbalanced");
		depth--;
	}

	public static float overridePartialTick(float original) {
		return depth > 0 ? FIXED_PARTIAL_TICK : original;
	}
}
