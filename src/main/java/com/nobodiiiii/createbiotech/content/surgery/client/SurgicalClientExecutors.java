package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Bounded workers for immutable surgical render/topology data. */
final class SurgicalClientExecutors {
	/**
	 * Cold builds arrive in bursts - a table with many subjects coming into view - and then stop, so
	 * the pool is sized for the burst rather than for steady state. Two workers serialised an entire
	 * queue of captures behind them; the upper cap keeps this below-normal-priority pool from crowding
	 * the render and chunk-build threads on smaller machines.
	 */
	private static final int WORKERS = Math.max(2,
		Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
	private static final AtomicInteger THREAD_IDS = new AtomicInteger();
	private static final ThreadFactory THREAD_FACTORY = task -> {
		Thread thread = new Thread(task,
			"Create Biotech Surgical Worker " + THREAD_IDS.incrementAndGet());
		thread.setDaemon(true);
		thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
		return thread;
	};
	private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(WORKERS, WORKERS,
		30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), THREAD_FACTORY,
		new ThreadPoolExecutor.AbortPolicy());

	static {
		EXECUTOR.allowCoreThreadTimeOut(true);
	}

	private SurgicalClientExecutors() {}

	static <T> CompletableFuture<T> submit(Supplier<T> task) {
		try {
			return CompletableFuture.supplyAsync(task, EXECUTOR);
		} catch (RejectedExecutionException ignored) {
			return CompletableFuture.failedFuture(new QueueFullException());
		}
	}

	static boolean wasQueueFull(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause())
			if (current instanceof QueueFullException)
				return true;
		return false;
	}

	private static final class QueueFullException extends RejectedExecutionException {
		private static final long serialVersionUID = 1L;

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
}
