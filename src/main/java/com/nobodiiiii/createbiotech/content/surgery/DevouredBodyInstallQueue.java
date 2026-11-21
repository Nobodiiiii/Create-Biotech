package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-wide budget shared by all measured bodies, including bodies in different dimensions. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class DevouredBodyInstallQueue {
	private static final long TICK_BUDGET_NANOS = 1_500_000L;
	private static final Map<MinecraftServer, DevouredBodyInstallQueue> QUEUES = new IdentityHashMap<>();
	private final Map<SlimeBionicEntity, SurgicalAssembly> pendingByBody = new IdentityHashMap<>();
	private final ArrayDeque<Installation> ready = new ArrayDeque<>();

	private DevouredBodyInstallQueue() {}

	static void submit(SlimeBionicEntity body, ServerLevel level, SurgicalAssembly pending, DevouredBodyMeasurementPacket report) {
		var server = level.getServer();
		var queue = QUEUES.computeIfAbsent(server, ignored -> new DevouredBodyInstallQueue());
		if (queue.pendingByBody.put(body, pending) == pending) return;
		var donors = report.prey().stream().map(DevouredDonorTopology::donor).collect(Collectors.toUnmodifiableSet());
		CompletableFuture.supplyAsync(() -> {
			var measured = report.prepare(pending);
			if (measured == null) return null;
			var installed = measured.installed();
			int firstNew = 0;
			while (firstNew < installed.sources().size() && !donors.contains(installed.sources().get(firstNew).donorId())) firstNew++;
			var save = new SurgicalAssemblyPersistence.PreparedSave(installed, firstNew);
			return new Installation(body, level, pending, measured, save, donors);
		}, Util.backgroundExecutor()).whenCompleteAsync((job, error) -> {
			if (QUEUES.get(server) != queue || queue.pendingByBody.get(body) != pending) return;
			if (error != null) LogUtils.getLogger().warn("Could not prepare devour growth for {}", body.getUUID(), error);
			if (job == null || !job.current()) queue.pendingByBody.remove(body, pending);
			else queue.ready.addLast(job);
		}, server);
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		var queue = QUEUES.get(event.getServer());
		if (queue != null && !queue.ready.isEmpty()) queue.advance();
	}

	private void advance() {
		long start = System.nanoTime();
		do {
			var job = ready.removeFirst();
			Outcome outcome = Outcome.DISCARD;
			try {
				if (job.current()) outcome = job.step();
			} catch (RuntimeException failure) {
				LogUtils.getLogger().warn("Could not install devour growth for {}", job.body.getUUID(), failure);
			}
			if (outcome == Outcome.MORE) ready.addLast(job);
			else pendingByBody.remove(job.body, job.pending);
			// Publishing includes mod event handlers and attribute updates and must stay atomic.
			if (outcome == Outcome.INSTALLED) break;
		} while (!ready.isEmpty() && System.nanoTime() - start < TICK_BUDGET_NANOS);
	}

	@SubscribeEvent
	public static void onStopped(ServerStoppedEvent event) { QUEUES.remove(event.getServer()); }

	private enum Outcome { MORE, DISCARD, INSTALLED }

	private static final class Installation {
		private final SlimeBionicEntity body;
		private final ServerLevel level;
		private final SurgicalAssembly pending;
		private final SurgicalPackingOperations.MeasuredBody measured;
		private final SurgicalAssemblyPersistence.PreparedSave save;
		private final Set<UUID> donors;
		private BionicTraitResolver.Revision revision;
		private long statsRevision;
		private BionicTraitResolver.Evaluation evaluation;
		private boolean checkingAlignment;
		private BionicTraitSet traits;

		Installation(SlimeBionicEntity body, ServerLevel level, SurgicalAssembly pending, SurgicalPackingOperations.MeasuredBody measured,
			SurgicalAssemblyPersistence.PreparedSave save, Set<UUID> donors) {
			this.body = body;
			this.level = level;
			this.pending = pending;
			this.measured = measured;
			this.save = save;
			this.donors = donors;
		}

		boolean current() {
			return body.isAlive() && level.getEntity(body.getId()) == body && body.getPendingGrowth() == pending;
		}

		Outcome step() {
			var activeRevision = BionicTraitResolver.revision(level);
			long activeStats = BionicStats.revision(level);
			if (!activeRevision.equals(revision) || activeStats != statsRevision) {
				revision = activeRevision;
				statsRevision = activeStats;
				checkingAlignment = measured.beforeAlignment() != null;
				evaluation = null;
				traits = null;
			}
			if (traits != null) {
				var installed = measured.installed().startDevouredGrowth(donors, level.getGameTime());
				body.installAssembly(installed, save.finish(installed), traits);
				return Outcome.INSTALLED;
			}
			if (evaluation == null) {
				evaluation = new BionicTraitResolver.Evaluation(checkingAlignment ? measured.beforeAlignment() : measured.installed(), level);
				return Outcome.MORE;
			}
			var result = evaluation.step();
			if (result == null) return Outcome.MORE;
			if (checkingAlignment) {
				if (!measured.acceptsAlignment(result)) return Outcome.DISCARD;
				checkingAlignment = false;
				evaluation = null;
			} else traits = result;
			return Outcome.MORE;
		}
	}
}
