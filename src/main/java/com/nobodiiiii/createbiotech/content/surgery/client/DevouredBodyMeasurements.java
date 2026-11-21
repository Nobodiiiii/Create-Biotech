package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.DevouredBodyMeasurementPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Complete a tracked creature's new tissue with the same capture and packing jobs as a surgical table. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class DevouredBodyMeasurements {
	private static final Map<SlimeBionicEntity, DevouredBodyMeasurements> JOBS = new WeakHashMap<>();
	private final SurgicalAssembly pending;
	private final DevouredPlacementModels models;
	private final int resources;
	private SurgicalPackedPreviewService.PackedPreparation packing;
	private int nextReport;

	private DevouredBodyMeasurements(SurgicalAssembly pending) {
		this.pending = pending;
		models = new DevouredPlacementModels(pending);
		resources = SurgicalSourceModelRenderer.resourceGeneration();
	}

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() && event.getEntity() instanceof SlimeBionicEntity body) schedule(body);
	}

	public static void schedule(SlimeBionicEntity body) {
		var player = Minecraft.getInstance().player;
		var job = JOBS.get(body);
		boolean selected = player != null && player.getId() == body.growthObserver();
		if (!selected && job == null) return;
		var pending = body.getPendingGrowth();
		if (job != null && job.pending == pending && job.resources == SurgicalSourceModelRenderer.resourceGeneration()) return;
		if (job != null) job.cancel();
		if (pending == null || !selected) JOBS.remove(body);
		else JOBS.put(body, new DevouredBodyMeasurements(pending));
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		var level = Minecraft.getInstance().level;
		var player = Minecraft.getInstance().player;
		for (var iter = JOBS.entrySet().iterator(); iter.hasNext();) {
			var entry = iter.next();
			var body = entry.getKey();
			var job = entry.getValue();
			if (level == null || level != body.level() || !body.isAlive() || body.getPendingGrowth() != job.pending) {
				job.cancel();
				iter.remove();
				continue;
			}
			// A failover can revisit a slow renderer. Resume the same capture when this client is selected again.
			if (player == null || player.getId() != body.growthObserver()) continue;
			if (job.resources != SurgicalSourceModelRenderer.resourceGeneration()) {
				job.cancel();
				job = new DevouredBodyMeasurements(job.pending);
				entry.setValue(job);
			}
			job.poll(body, level);
		}
	}

	private void poll(SlimeBionicEntity body, ClientLevel level) {
		if (body.tickCount < nextReport) return;
		if (packing == null) {
			var expanded = models.prepare(resources);
			if (expanded == null) return;
			packing = new SurgicalPackedPreviewService.PackedPreparation(expanded);
		}
		var metrics = packing.pollMetrics(level);
		if (metrics == null || metrics.attackGeometry() == null) return;
		CBPackets.sendToServer(new DevouredBodyMeasurementPacket(body.getId(), body.getUUID(),
			pending.sources().getLast().donorId(), models.observations(), metrics.bodyBounds(), metrics.bodyVolume(),
			metrics.hitboxGeometry(), metrics.attackGeometry(), metrics.packingAlignment()));
		nextReport = body.tickCount + 100;
	}

	private void cancel() {
		models.cancel();
		if (packing != null) packing.cancel();
	}
}
