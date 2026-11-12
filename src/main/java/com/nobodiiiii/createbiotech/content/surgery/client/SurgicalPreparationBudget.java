package com.nobodiiiii.createbiotech.content.surgery.client;

/** Shared client-thread preparation budget. Cheap instances do not consume model-capture slots. */
final class SurgicalPreparationBudget {
	private static final long BUDGET_NANOS = 2_000_000L;
	private static long spent;
	private static int captures;
	private static int depth;
	private static long started;

	private SurgicalPreparationBudget() {}

	static void beginFrame() {
		spent = 0;
		captures = 0;
		depth = 0;
	}

	static boolean available() {
		return spent + (depth == 0 ? 0 : System.nanoTime() - started) < BUDGET_NANOS;
	}

	static boolean begin(boolean capture) {
		if (!available() || capture && captures >= 4)
			return false;
		if (capture) captures++;
		if (depth++ == 0) started = System.nanoTime();
		return true;
	}

	static void end() {
		if (--depth == 0) spent += System.nanoTime() - started;
	}
}
