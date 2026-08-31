package com.nobodiiiii.createbiotech.content.surgery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Opt-in timing probe for the surgical table, enabled with
 * {@code -Dcreate_biotech.surgery.profile=true} on the game or dedicated-server command line.
 *
 * <p>The table has several independently expensive phases spread across the packet-decode path, the
 * render thread and a worker pool, and an averaged frame-rate readout hides a single long frame
 * almost completely. This reports any individual phase that runs long, plus a running total per
 * phase, so a stutter can be attributed instead of guessed at.
 */
public final class SurgicalProfiler {
	public static final boolean ENABLED = Boolean.getBoolean("create_biotech.surgery.profile");
	/** Only individually slow runs are worth a line; anything quicker is noise at 50 ms per tick. */
	private static final long REPORT_THRESHOLD_NANOS = 2_000_000L;
	/**
	 * Some phases matter only in aggregate: a cache probe costs microseconds and never trips the
	 * threshold above, but running it hundreds of times per frame does show up in the frame time.
	 * Totals are therefore flushed on this interval and reset, so each line reads as a rate.
	 */
	private static final long SUMMARY_INTERVAL_NANOS = 5_000_000_000L;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<String, AtomicLong> TOTAL_NANOS = new ConcurrentHashMap<>();
	private static final Map<String, AtomicLong> CALLS = new ConcurrentHashMap<>();
	private static final AtomicLong LAST_SUMMARY = new AtomicLong();

	private SurgicalProfiler() {}

	/** Returns a start stamp, or 0 when profiling is off so callers cost nothing. */
	public static long begin() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	public static void end(String phase, long started) {
		if (!ENABLED || started == 0L)
			return;
		long finished = System.nanoTime();
		long elapsed = finished - started;
		long total = TOTAL_NANOS.computeIfAbsent(phase, ignored -> new AtomicLong()).addAndGet(elapsed);
		long calls = CALLS.computeIfAbsent(phase, ignored -> new AtomicLong()).incrementAndGet();
		if (elapsed >= REPORT_THRESHOLD_NANOS)
			LOGGER.info("[surgery] {} took {} ms on thread {} (call #{}, {} ms total)",
				phase, format(elapsed), Thread.currentThread().getName(), calls, format(total));
		summarize(finished);
	}

	/**
	 * Counts an occurrence without timing it. For probes whose own cost is comparable to a
	 * {@code nanoTime} pair, the count is the trustworthy number and the timing is not.
	 */
	public static void count(String phase) {
		if (!ENABLED)
			return;
		CALLS.computeIfAbsent(phase, ignored -> new AtomicLong()).incrementAndGet();
		summarize(System.nanoTime());
	}

	private static void summarize(long now) {
		long last = LAST_SUMMARY.get();
		if (last == 0L) {
			LAST_SUMMARY.compareAndSet(0L, now);
			return;
		}
		long elapsed = now - last;
		if (elapsed < SUMMARY_INTERVAL_NANOS || !LAST_SUMMARY.compareAndSet(last, now))
			return;
		double seconds = elapsed / 1.0e9d;
		LOGGER.info("[surgery] --- {} s summary ---", format(elapsed * 1000L));
		CALLS.forEach((phase, callCounter) -> {
			long calls = callCounter.getAndSet(0L);
			if (calls == 0L)
				return;
			AtomicLong totalCounter = TOTAL_NANOS.get(phase);
			long total = totalCounter == null ? 0L : totalCounter.getAndSet(0L);
			if (total == 0L) {
				LOGGER.info("[surgery]   {}: {} calls ({}/s)", phase, calls,
					String.format("%.0f", calls / seconds));
				return;
			}
			LOGGER.info("[surgery]   {}: {} calls ({}/s), {} ms total, {} us avg",
				phase, calls, String.format("%.0f", calls / seconds), format(total),
				String.format("%.2f", total / 1.0e3d / calls));
		});
	}

	private static String format(long nanos) {
		return String.format("%.2f", nanos / 1.0e6d);
	}
}
