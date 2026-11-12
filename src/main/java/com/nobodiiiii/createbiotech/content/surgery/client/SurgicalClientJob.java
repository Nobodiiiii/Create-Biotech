package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;

/** Client-thread handle for immutable worker inputs. Saturation retries without running inline. */
final class SurgicalClientJob<T> {
	private final String phase;
	private final Supplier<T> work;
	private CompletableFuture<T> future;
	private boolean finished;
	private T result;

	SurgicalClientJob(String phase, Supplier<T> work) {
		this.phase = phase;
		this.work = work;
	}

	@Nullable T poll() {
		if (finished) return result;
		if (future == null) {
			future = SurgicalClientExecutors.submit(() -> {
				long started = SurgicalProfiler.begin();
				try { return work.get(); }
				finally { SurgicalProfiler.end(phase, started); }
			});
			return null;
		}
		if (!future.isDone()) return null;
		try {
			result = future.join();
			finished = true;
		} catch (RuntimeException exception) {
			if (!SurgicalClientExecutors.wasQueueFull(exception)) {
				finished = true;
				LogUtils.getLogger().warn("Surgical preparation failed: {}", phase, exception);
			}
		} finally { future = null; }
		return result;
	}

	boolean finished() { return finished; }

	void cancel() {
		if (future != null) future.cancel(false);
		finished = true;
	}
}
