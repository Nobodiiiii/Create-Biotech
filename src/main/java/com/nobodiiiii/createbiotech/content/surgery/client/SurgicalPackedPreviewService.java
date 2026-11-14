package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalPackingAlignment;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableInteractionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicBlockAttachment;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalPackedPreviewService implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalPackedPreviewService {
	private SurgicalPackedPreviewService() {}

	static PendingPacking pendingPacking;
	/** Each retained cube indexes the same preview as every other member of its connected component. */
	static final Map<SurgicalTableBlockEntity, PackedBodyPreviewCache> PACKED_BODY_PREVIEWS = new WeakHashMap<>();
	/** Stable member revisions let unrelated table edits retain completed measurements. */
	static final Map<PackedComponentKey, PackedPreparation> PACKED_MEASUREMENTS =
		new java.util.LinkedHashMap<>(16, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<PackedComponentKey, PackedPreparation> eldest) {
				if (size() <= 32) return false;
				eldest.getValue().cancel();
				return true;
			}
		};

	static void appendPackedProperties(List<Component> tooltip, ClientLevel level,
		@Nullable SurgicalSelectionResolver.CubeHit hit) {
		if (hit == null
			|| !(level.getBlockEntity(hit.tablePos()) instanceof SurgicalTableBlockEntity table))
			return;
		PackedBodyPreview preview = packedBodyPreview(level, table, hit);
		if (preview != null)
			CapturedEntityBoxStatsTooltip.appendPropertiesSection(tooltip, preview.traits());
	}

	@Nullable
	static PackedBodyPreview packedBodyPreview(ClientLevel level,
		SurgicalTableBlockEntity table, SurgicalSelectionResolver.CubeHit hit) {
		return packedBodyPreview(level, table, hit.geometry().subjectId, hit.cubeId(),
			hit.geometry().observedCubeCount, hit.geometry().seams);
	}

	@Nullable
	static PackedBodyPreview packedBodyPreview(ClientLevel level,
		SurgicalTableBlockEntity table, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams) {
		int tableRevision = table.clientDataRevision();
		BionicTraitResolver.Revision traitRevision = BionicTraitResolver.revision(net.minecraft.client.Minecraft.getInstance().level);
		int resourceGeneration = SurgicalSourceModelRenderer.resourceGeneration();
		PackedBodyPreviewCache cache = PACKED_BODY_PREVIEWS.get(table);
		if (cache == null || !cache.matches(tableRevision, traitRevision, resourceGeneration)) {
			cache = new PackedBodyPreviewCache(tableRevision, traitRevision, resourceGeneration, new HashMap<>(), new HashMap<>());
			PACKED_BODY_PREVIEWS.put(table, cache);
		}
		PackedBodyPreview cached = cache.get(subjectId, cubeId);
		if (cached != null) {
			SurgicalProfiler.count("packedPreview(cacheHit)");
			return cached;
		}
		PackedBodyMember member = new PackedBodyMember(subjectId, cubeId);
		PackedPreparation preparation = cache.preparing().get(member);
		if (preparation == null || preparation.cancelled) {
			if (!SurgicalPreparationBudget.begin(false)) return null;
			try {
				var component = table.previewPackedComponent(subjectId, cubeId, observedCubeCount, seams);
				if (component == null) return null;
				var revisions = component.components().entrySet().stream().sorted(Map.Entry.comparingByKey())
					.map(entry -> new PackedSubjectRevision(table.getSubject(entry.getKey()).persistentId(),
						table.getSubject(entry.getKey()).clientRenderRevision(), (BitSet) entry.getValue().clone())).toList();
				var key = new PackedComponentKey(level, revisions.getFirst().subject(), revisions,
					traitRevision, resourceGeneration);
				preparation = PACKED_MEASUREMENTS.computeIfAbsent(key, ignored -> new PackedPreparation(component.assembly()));
				for (var entry : component.components().entrySet())
					for (int cube = entry.getValue().nextSetBit(0); cube >= 0; cube = entry.getValue().nextSetBit(cube + 1))
						cache.preparing().put(new PackedBodyMember(entry.getKey(), cube), preparation);
			} finally { SurgicalPreparationBudget.end(); }
		}
		PackedBodyPreview ready = preparation.poll(level);
		if (ready != null) for (var entry : cache.preparing().entrySet())
			if (entry.getValue() == preparation) cache.byMember().put(entry.getKey(), ready);
		return ready;
	}

	record PendingPacking(SurgicalSelectionResolver.Selection selection, InteractionHand hand, ItemStack held,
		SurgicalTableInteractionPacket.Action action, int revision, int resources, long expires) {}

	static void queuePacking(SurgicalSelectionResolver.Selection selection, InteractionHand hand, ItemStack held,
		SurgicalTableInteractionPacket.Action action) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.getBlockEntity(selection.tablePos()) instanceof SurgicalTableBlockEntity table) {
			// Repeated use events must not extend the timeout or replace an already queued click.
			if (pendingPacking == null)
				pendingPacking = new PendingPacking(selection, hand, held.copy(), action, table.clientDataRevision(),
					SurgicalSourceModelRenderer.resourceGeneration(), level.getGameTime() + 200);
		}
	}

	static void updatePendingPacking() {
		PendingPacking pending = pendingPacking;
		if (pending == null) return;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || minecraft.screen != null
			|| !ItemStack.isSameItemSameComponents(player.getItemInHand(pending.hand()), pending.held())
			|| SurgicalSourceModelRenderer.resourceGeneration() != pending.resources()
			|| !(level.getBlockEntity(pending.selection().tablePos()) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != pending.revision()
			|| player.distanceToSqr(Vec3.atCenterOf(pending.selection().tablePos())) > 64.0d) {
			pendingPacking = null;
			return;
		}
		PackedBodyMetrics metrics = selectedBodyMetrics(pending.selection());
		if (metrics != null) {
			pendingPacking = null;
			SurgicalTableClientHandler.sendInteraction(pending.selection(), pending.hand(), pending.action(), SurgicalTableLayout.Proposal.EMPTY,
				metrics.bodyBounds(), metrics.hitboxGeometry(), metrics.bodyVolume(), metrics.attackGeometry(), metrics.packingAlignment());
		} else if (level.getGameTime() >= pending.expires()) {
			pendingPacking = null;
			SurgicalTableFailure.MODEL_UNAVAILABLE.display(player);
		}
	}

	/** Measures and permanently bakes the selected body's invariant physical data. */
	@Nullable
	static PackedBodyMetrics selectedBodyMetrics(SurgicalSelectionResolver.Selection selection) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null
			|| !(level.getBlockEntity(selection.tablePos()) instanceof SurgicalTableBlockEntity table))
			return null;
		PackedBodyPreview preview = packedBodyPreview(level, table, selection.subjectId(), selection.targetId(),
			selection.observedCubeCount(), selection.seams());
		return preview == null ? null : preview.metrics();
	}

	@Nullable
	static PackedBodyMetrics measureBodyMetrics(SurgicalAssembly preview, PackedBodyMeasurement measured,
		List<SurgicalSourceModelRenderer.DonorGeometry> donors, @Nullable SurgicalPackingAlignment alignment,
		@Nullable SurgicalAssembly.AttackGeometry originalGeometry) {
		Set<SurgicalAssembly.CombinationMember> armCubes = new java.util.HashSet<>();
		for (SurgicalAssembly.Limb limb : preview.effectiveLimbs())
			if (limb.type() == SurgicalLimbType.SHOULDER || limb.type() == SurgicalLimbType.ELBOW)
				armCubes.addAll(preview.rotatingGroup(limb.childSource(), limb.childCube()));
		List<List<Vec3>> allCubes = new ArrayList<>();
		List<List<Vec3>> bodyCubes = new ArrayList<>();
		for (int source = 0; source < measured.sources().size(); source++)
			for (Map.Entry<Integer, SlimeBionicAnimator.CubeBox> entry
				: measured.sources().get(source).boxes().entrySet()) {
				allCubes.add(entry.getValue().points());
				if (!armCubes.contains(new SurgicalAssembly.CombinationMember(source, entry.getKey())))
					bodyCubes.add(entry.getValue().points());
			}
		SurgicalBodyBounds.Measurement bodyMeasurement = SurgicalBodyBounds.measureWithVolume(bodyCubes, allCubes,
			measured.visible());
		if (bodyMeasurement == null)
			return null;
		SurgicalAssembly.BodyBounds bodyBounds = bodyMeasurement.bounds();
		SlimeBionicAnimator.MobilityMetrics mobility =
			SlimeBionicAnimator.measureMobility(preview, measured.sources());
		bodyBounds = bodyBounds.withMobility(mobility.averageLegLength(),
			mobility.groundedLegCount(), mobility.groundedKneeCount(), mobility.legVolumeRatio());
		SurgicalBodyBounds.Envelope visible = measured.visible();
		Vec3 bodyOrigin = new Vec3((visible.minX() + visible.maxX()) * 0.5d + bodyBounds.centerX(),
			visible.minY(), (visible.minZ() + visible.maxZ()) * 0.5d + bodyBounds.centerZ());
		SurgicalAssembly.AttackGeometry attackGeometry = originalGeometry == null ? null
			: SlimeBionicAnimator.rebaseAttackGeometry(originalGeometry, bodyOrigin);
		if (attackGeometry == null)
			attackGeometry = SlimeBionicAnimator.bakeAttackGeometry(preview, measured.sources(), bodyOrigin, donors);
		List<Map<Integer, List<Vec3>>> hitboxCubes = measured.sources().stream()
			.map(source -> source.boxes().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> entry.getValue().points())))
			.toList();
		SurgicalAssembly.HitboxGeometry hitboxGeometry = SurgicalHitboxGeometry.measure(preview,
			hitboxCubes, visible, bodyBounds);
		double bodyVolume = bodyMeasurement.volume();
		if (hitboxGeometry == null
			|| !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return null;
		return new PackedBodyMetrics(bodyBounds, hitboxGeometry, bodyVolume, attackGeometry, alignment);
	}

	/** Resumable client acquisition; no model replay and no entity access from worker jobs. */
	static final class PackedCapture {
		private final SurgicalAssembly assembly;
		private final List<SlimeBionicAnimator.SourceState> sources = new ArrayList<>();
		private final List<SurgicalSourceModelRenderer.DonorGeometry> donors = new ArrayList<>();
		private AABB envelope;
		private boolean invalid;
		private PackedCapture(SurgicalAssembly assembly) { this.assembly = assembly; }
		private PackedBodyMeasurement poll() {
			if (invalid) return null;
			while (sources.size() < assembly.sources().size()) {
				var source = assembly.sources().get(sources.size());
				var donor = SurgicalSourceModelRenderer.donorGeometryDeferred(source.profile());
				if (donor == null || !SurgicalPreparationBudget.begin(false)) return null;
				try {
					LivingEntity entity = SurgicalSourceModelRenderer.previewDeferred(source.profile());
					if (entity == null) return null;
					PoseStack pose = new PoseStack();
					SurgicalTablePoseResolver.applyInverseRotation(pose, assembly.layoutLayPose());
					pose.translate(source.originOffset().x, source.originOffset().y, source.originOffset().z);
					if (assembly.preservesLayout()) SurgicalTablePoseResolver.resolve(source.layPose()).apply(pose);
					var offsets = packedUprightOffsets(assembly, source);
					var rotations = packedUprightRotations(assembly, source);
					var snapshot = SurgicalSourceModelRenderer.geometryDeferred(entity, source.cubeCount(), source.presentCubes(),
						offsets, rotations, pose, 0, null);
					if (snapshot == null) return null;
					if (snapshot.observedCubeCount() != source.cubeCount()
						|| snapshot.cubes().size() != source.presentCubes().cardinality()) { invalid = true; return null; }
					AABB bounds = SurgicalSelectionResolver.logicalBounds(snapshot.cubes(), Map.of());
					if (bounds == null) { invalid = true; return null; }
					envelope = envelope == null ? bounds : envelope.minmax(bounds);
					sources.add(new SlimeBionicAnimator.SourceState(SlimeBionicAnimator.measure(snapshot), offsets, rotations));
					donors.add(donor);
				} finally { SurgicalPreparationBudget.end(); }
			}
			return envelope == null ? null : new PackedBodyMeasurement(List.copyOf(sources),
				new SurgicalBodyBounds.Envelope(envelope.minX, envelope.minY, envelope.minZ,
					envelope.maxX, envelope.maxY, envelope.maxZ));
		}
	}

	record PackedSubjectRevision(UUID subject, int revision, BitSet cubes) {}
	record PackedComponentKey(ClientLevel level, UUID anchor, List<PackedSubjectRevision> members,
		BionicTraitResolver.Revision traits, int resources) {}

	static final class PackedPreparation {
		private final SurgicalAssembly original;
		private SurgicalAssembly measuredAssembly;
		private PackedCapture capture;
		private PackedBodyMeasurement measured;
		private SurgicalClientJob<SurgicalAssembly.AttackGeometry> originalGeometry;
		private SurgicalClientJob<SurgicalAssembly> correction;
		private SurgicalClientJob<PackedBodyMetrics> metrics;
		private SurgicalPackingAlignment alignment;
		private boolean alignmentResolved, correctionApplied, failed, cancelled;
		private PackedBodyPreview result;
		private PackedPreparation(SurgicalAssembly assembly) {
			original = measuredAssembly = assembly;
			capture = new PackedCapture(assembly);
		}
		private PackedBodyPreview poll(ClientLevel level) {
			if (cancelled) return null;
			if (result != null || failed) return result;
			if (measured == null) {
				measured = capture.poll();
				if (measured == null) { failed = capture.invalid; return null; }
			}
			if (originalGeometry == null) {
				var frozen = measured;
				var donors = List.copyOf(capture.donors);
				originalGeometry = new SurgicalClientJob<>("attackGeometry(async)",
					() -> SlimeBionicAnimator.bakeAttackGeometry(original, frozen.sources(), Vec3.ZERO, donors));
			}
			var geometry = originalGeometry.poll();
			if (!originalGeometry.finished()) return null;
			if (!alignmentResolved) {
				if (!SurgicalPreparationBudget.begin(false)) return null;
				try {
					if (geometry != null) {
						var withGeometry = original.withAttackGeometry(geometry);
						alignment = BionicBlockAttachment.packingAlignment(BionicTraitResolver.resolve(withGeometry, level), geometry.carrierCubes());
						if (alignment != null) {
							var selectedAlignment = alignment;
							correction = new SurgicalClientJob<>("packingAlignment(async)",
								() -> selectedAlignment.applyToMeasured(withGeometry, geometry.carrierCubes()));
						}
					}
					alignmentResolved = true;
				} finally { SurgicalPreparationBudget.end(); }
			}
			if (correction != null && !correctionApplied) {
				var corrected = correction.poll();
				if (!correction.finished()) return null;
				if (corrected == null) { failed = true; return null; }
				measuredAssembly = corrected;
				capture = new PackedCapture(corrected);
				measured = null;
				correctionApplied = true;
				return null;
			}
			if (metrics == null) {
				var assembly = measuredAssembly;
				var frozen = measured;
				var donors = List.copyOf(capture.donors);
				var selectedAlignment = alignment;
				// An alignment changes the rest frame: only unchanged geometry can be rebased directly.
				var reusableGeometry = correctionApplied ? null : geometry;
				metrics = new SurgicalClientJob<>("bodyMetrics(async)",
					() -> measureBodyMetrics(assembly, frozen, donors, selectedAlignment, reusableGeometry));
			}
			var ready = metrics.poll();
			if (!metrics.finished() || !SurgicalPreparationBudget.begin(false)) return null;
			try {
				SurgicalAssembly packed = original;
				if (ready != null) {
					if (ready.packingAlignment() != null) packed = ready.packingAlignment().applyToPacked(packed, ready.attackGeometry());
					if (packed == null) { failed = true; return null; }
					packed = packed.withBodyGeometry(ready.bodyBounds(), ready.hitboxGeometry(), ready.bodyVolume());
					if (ready.attackGeometry() != null) packed = packed.withAttackGeometry(ready.attackGeometry());
				}
				return result = new PackedBodyPreview(packed, ready, BionicTraitResolver.resolve(packed, level));
			} finally { SurgicalPreparationBudget.end(); }
		}
		void cancel() {
			cancelled = true;
			if (originalGeometry != null) originalGeometry.cancel();
			if (correction != null) correction.cancel();
			if (metrics != null) metrics.cancel();
		}
	}

	static Map<Integer, Vec3> packedUprightOffsets(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<Integer, Vec3> transformed = new HashMap<>();
		source.cubeOffsets().forEach((cube, offset) ->
			transformed.put(cube, assembly.layoutLayPose().inverseRotate(offset)));
		return Map.copyOf(transformed);
	}

	static Map<Integer, SurgicalCubeRotation> packedUprightRotations(
		SurgicalAssembly assembly, SurgicalAssembly.Source source) {
		Map<Integer, SurgicalCubeRotation> transformed = new HashMap<>();
		source.cubeRotations().forEach((cube, rotation) ->
			transformed.put(cube, rotation.inverseRotate(assembly.layoutLayPose())));
		return Map.copyOf(transformed);
	}
	record PackedBodyMetrics(SurgicalAssembly.BodyBounds bodyBounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry,
		double bodyVolume,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {}
	record PackedBodyMember(int subjectId, int cubeId) {}
	record PackedBodyPreview(SurgicalAssembly assembly, @Nullable PackedBodyMetrics metrics,
		BionicTraitSet traits) {}
	record PackedBodyPreviewCache(int tableRevision, BionicTraitResolver.Revision traitRevision,
		int resourceGeneration,
		Map<PackedBodyMember, PackedBodyPreview> byMember, Map<PackedBodyMember, PackedPreparation> preparing) {
		private boolean matches(int revision, BionicTraitResolver.Revision traits, int resources) {
			return tableRevision == revision && traitRevision.equals(traits) && resourceGeneration == resources;
		}

		@Nullable
		private PackedBodyPreview get(int subjectId, int cubeId) {
			return byMember.get(new PackedBodyMember(subjectId, cubeId));
		}

		private void put(Map<Integer, BitSet> components, PackedBodyPreview preview) {
			for (var entry : components.entrySet()) {
				BitSet cubes = entry.getValue();
				for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
					byMember.put(new PackedBodyMember(entry.getKey(), cube), preview);
			}
		}
	}
	record PackedBodyMeasurement(List<SlimeBionicAnimator.SourceState> sources,
		SurgicalBodyBounds.Envelope visible) {}
}
