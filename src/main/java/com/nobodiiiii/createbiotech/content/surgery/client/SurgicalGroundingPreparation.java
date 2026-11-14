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
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalGroundingPreparation implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalGroundingPreparation {
	private SurgicalGroundingPreparation() {}

	static final Map<SurgicalTableBlockEntity, TableGroundingCache> GROUP_GROUNDING = new WeakHashMap<>();
	static final Map<SurgicalTableBlockEntity, GroupPreparationCache> GROUP_PREPARATIONS = new WeakHashMap<>();

	/** One dependency counter and worker job per connected group, rather than one graph per subject. */
	static void queueGroupedGrounding(SurgicalTableBlockEntity table, SurgicalSubject subject,
		SurgicalGeometryStore.TableGeometry geometry) {
		GroupPreparationCache cache = GROUP_PREPARATIONS.get(table);
		if (cache == null || cache.revision != table.clientDataRevision()) {
			if (cache != null) cache.cancel();
			cache = new GroupPreparationCache(table);
			GROUP_PREPARATIONS.put(table, cache);
		}
		GroundingPreparation group = cache.bySubject.get(subject.id());
		if (group == null) return;
		group.changed(subject, geometry);
	}

	static void prepareGroundingGroups(SurgicalTableBlockEntity table) {
		GroupPreparationCache cache = GROUP_PREPARATIONS.get(table);
		if (cache == null) return;
		if (cache.revision != table.clientDataRevision()) {
			cache.cancel();
			cache = new GroupPreparationCache(table);
			GROUP_PREPARATIONS.put(table, cache);
		}
		for (GroundingPreparation group : cache.groups) {
			if (!group.dirty || group.ready.size() != group.members.size()
				|| group.ready.values().stream().anyMatch(geometry -> geometry.stagedPreview != null)) continue;
			if (group.job == null) {
				if (!SurgicalPreparationBudget.begin(false)) return;
				try {
					List<GroundingInput> inputs = new ArrayList<>();
					Set<SurgicalGlueJoint> joints = new HashSet<>();
					Set<SurgicalCombination> combinations = new HashSet<>();
					for (int id : group.members) {
						SurgicalSubject subject = table.getSubject(id);
						SurgicalGeometryStore.TableGeometry geometry = group.ready.get(id);
						inputs.add(new GroundingInput(geometry,
							new SurgicalClientTopology.GroundingBody<>(subject.persistentId(), geometry.observedCubeCount,
								(BitSet) geometry.presentCubes.clone(), geometry.seams, (BitSet) geometry.cutSeams.clone(),
								geometry.layoutCubes, geometry.serverOffsets), geometry.groundingInputRevision,
							geometry.baseCubes, geometry.baseCubesById, geometry.baseContacts, geometry.serverRotations));
						joints.addAll(subject.glueJoints());
						combinations.addAll(subject.combinations());
					}
					List<SurgicalClientTopology.GroundingLink<UUID>> links = new ArrayList<>();
					for (var joint : joints)
						links.add(new SurgicalClientTopology.GroundingLink<>(joint.first().subjectKey(), joint.first().cubeId(),
							joint.second().subjectKey(), joint.second().cubeId()));
					for (var combination : combinations) {
						var anchor = combination.members().getFirst();
						for (var member : combination.members().subList(1, combination.members().size()))
							links.add(new SurgicalClientTopology.GroundingLink<>(anchor.subjectKey(), anchor.cubeId(),
								member.subjectKey(), member.cubeId()));
					}
					var frozenInputs = List.copyOf(inputs);
					var frozenLinks = List.copyOf(links);
					double surface = SurgicalTablePlane.surfaceY(table.getBlockPos().getY()) + SurgicalTablePoseResolver.TABLE_CLEARANCE;
					group.job = new SurgicalClientJob<>("groundGroup(async)", () -> prepareGrounding(frozenInputs, frozenLinks, surface));
				} finally { SurgicalPreparationBudget.end(); }
			}
			List<PreparedTableGeometry> prepared = group.job.poll();
			if (prepared == null) {
				if (group.job.finished()) group.dirty = false;
				continue;
			}
			if (!SurgicalPreparationBudget.begin(false)) return;
			try {
				boolean valid = prepared.stream().allMatch(result -> {
					var input = result.input();
					SurgicalGeometryStore.TableGeometry geometry = input.geometry();
					SurgicalSubject subject = table.getSubject(geometry.subjectId);
					return subject != null && SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(table.getBlockPos(), geometry.subjectId)) == geometry
						&& geometry.groundingInputRevision == input.revision() && geometry.stagedPreview == null
						&& geometry.topologyReady() && geometry.renderRevision == subject.clientRenderRevision();
				});
				if (!valid) {
					group.job.cancel();
					group.job = null;
					group.ready.entrySet().removeIf(entry -> SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(table.getBlockPos(), entry.getKey())) != entry.getValue());
					continue;
				}
				// No readiness query can observe a partly published group on this thread.
				for (var result : prepared) {
					SurgicalGeometryStore.TableGeometry geometry = result.input().geometry();
					geometry.offsets = result.offsets();
					geometry.rotations = result.input().rotations();
					geometry.cubes = result.cubes();
					geometry.cubesById = result.byId();
					geometry.cubeTargets = result.targets();
					geometry.bounds = result.bounds();
					geometry.contacts = result.contacts();
					geometry.contactsByCube = result.contactsByCube();
					geometry.groundingPending = false;
					geometry.transformGeneration++;
					geometry.reportBounds(table);
				}
				group.dirty = false;
				SurgicalGeometryStore.geometryGeneration++;
				SurgicalProfiler.count("groundGroup(publish)");
			} finally { SurgicalPreparationBudget.end(); }
		}
	}

	static List<PreparedTableGeometry> prepareGrounding(List<GroundingInput> inputs,
		List<SurgicalClientTopology.GroundingLink<UUID>> links, double surface) {
		var grounded = SurgicalClientTopology.groundConnectedBodies(inputs.stream().map(GroundingInput::body).toList(), links, surface);
		if (grounded == null) return null;
		List<PreparedTableGeometry> results = new ArrayList<>();
		for (var input : inputs) {
			var offsets = grounded.get(input.body().key());
			if (offsets == null) return null;
			var cubes = SurgicalGluePreviewPlanner.transformCubes(input.baseCubes(), input.rotations(), offsets);
			var targets = SurgicalGeometryStore.TableGeometry.cubeTargets(cubes);
			var contacts = SurgicalGeometryStore.TableGeometry.transformContacts(input.contacts(), input.baseById(), input.rotations(), offsets);
			results.add(new PreparedTableGeometry(input, Map.copyOf(offsets), cubes, SurgicalGluePreviewPlanner.indexCubes(cubes), targets,
				SurgicalGeometryStore.TableGeometry.boundsFor(targets), contacts, SurgicalGeometryStore.TableGeometry.contactsByCube(input.body().cubeCount(), contacts)));
		}
		return List.copyOf(results);
	}

	record GroundingInput(SurgicalGeometryStore.TableGeometry geometry, SurgicalClientTopology.GroundingBody<UUID> body, long revision,
		List<SurgicalModelRenderContext.CubeGeometry> baseCubes, Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseById,
		List<SurgicalClientTopology.Contact> contacts, Map<Integer, SurgicalCubeRotation> rotations) {}
	record PreparedTableGeometry(GroundingInput input, Map<Integer, Vec3> offsets,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId,
		List<SurgicalSelectionResolver.CubeTarget> targets, AABB bounds, List<SurgicalClientTopology.Contact> contacts,
		List<List<SurgicalClientTopology.Contact>> contactsByCube) {}

	static final class GroundingPreparation {
		private final Set<Integer> members;
		private final Map<Integer, SurgicalGeometryStore.TableGeometry> ready = new HashMap<>();
		private SurgicalClientJob<List<PreparedTableGeometry>> job;
		private boolean dirty;
		private GroundingPreparation(Set<Integer> members) { this.members = Set.copyOf(members); }
		private void changed(SurgicalSubject subject, SurgicalGeometryStore.TableGeometry geometry) {
			if (job != null) job.cancel();
			job = null;
			dirty = true;
			if (geometry.topologyReady() && geometry.renderRevision == subject.clientRenderRevision())
				ready.put(subject.id(), geometry);
			else ready.remove(subject.id());
		}
	}

	static final class GroupPreparationCache {
		private final int revision;
		private final Map<Integer, GroundingPreparation> bySubject = new HashMap<>();
		private final List<GroundingPreparation> groups = new ArrayList<>();
		private GroupPreparationCache(SurgicalTableBlockEntity table) {
			revision = table.clientDataRevision();
			for (var subject : table.getSubjects()) {
				if (bySubject.containsKey(subject.id()) || !subject.linkedToOtherSubjects()) continue;
				GroundingPreparation group = new GroundingPreparation(table.connectedSubjectIds(subject.id()));
				groups.add(group);
				for (int id : group.members) {
					bySubject.put(id, group);
					var member = table.getSubject(id);
					var geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(table.getBlockPos(), id));
					// A different group may have changed while this one was still preparing.
					// Its unchanged members will not refresh again, so carry their pending work over.
					if (geometry != null && geometry.groundingPending) group.dirty = true;
					if (geometry != null && member != null && geometry.topologyReady()
						&& geometry.renderRevision == member.clientRenderRevision()) group.ready.put(id, geometry);
				}
			}
		}
		void cancel() { for (var group : groups) if (group.job != null) group.job.cancel(); }
	}

	/**
	 * Grounding needs every connected body in its rotated-but-unoffset form. That is exactly what each
	 * geometry already caches as {@code layoutCubes}, including the caller's own — {@code applyTransforms}
	 * rebuilds it from the same rotations immediately before calling here — so the cubes are reused
	 * rather than re-transformed once per body per subject.
	 */
	@Nullable
	static Map<Integer, Vec3> groundConnectedComponents(SurgicalTableBlockEntity table,
		SurgicalSubject currentSubject, SurgicalGeometryStore.TableGeometry currentGeometry, Map<Integer, Vec3> currentOffsets,
		BitSet currentCutSeams,
		@Nullable SurgicalGlueJoint excludedJoint, double surfaceY) {
		List<SurgicalClientTopology.GroundingBody<UUID>> bodies = new ArrayList<>();
		Set<SurgicalGlueJoint> joints = new java.util.HashSet<>();
		Set<SurgicalCombination> combinations = new java.util.HashSet<>();
		Set<Integer> connectedSubjectIds = table.connectedSubjectIds(currentSubject.id());
		for (SurgicalSubject subject : table.getSubjects()) {
			if (!connectedSubjectIds.contains(subject.id()))
				continue;
			for (SurgicalGlueJoint joint : subject.glueJoints())
				if (!joint.equals(excludedJoint))
					joints.add(joint);
			combinations.addAll(subject.combinations());
			boolean currentSubjectEntry = subject.persistentId().equals(currentSubject.persistentId());
			SurgicalGeometryStore.TableGeometry geometry = currentSubjectEntry ? currentGeometry
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(table.getBlockPos(), subject.id()));
			if (geometry == null || !geometry.topologyReady()
				|| geometry.renderRevision != subject.clientRenderRevision()
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			SurgicalGeometryStore.GeometryPreview preview = geometry.stagedPreview;
			Map<Integer, Vec3> offsets = currentSubjectEntry ? currentOffsets
				: preview == null ? geometry.serverOffsets : preview.previewOffsets();
			BitSet cutSeams = currentSubjectEntry ? currentCutSeams
				: preview == null ? geometry.cutSeams : preview.previewCutSeams();
			bodies.add(new SurgicalClientTopology.GroundingBody<>(subject.persistentId(),
				geometry.observedCubeCount, geometry.presentCubes, geometry.seams, cutSeams,
				geometry.layoutCubes, offsets));
		}
		List<SurgicalClientTopology.GroundingLink<UUID>> links = new ArrayList<>(joints.stream()
			.map(joint -> new SurgicalClientTopology.GroundingLink<>(joint.first().subjectKey(),
				joint.first().cubeId(), joint.second().subjectKey(), joint.second().cubeId()))
			.toList());
		for (SurgicalCombination combination : combinations) {
			SurgicalCombination.Member anchor = combination.members().getFirst();
			for (SurgicalCombination.Member member : combination.members().subList(1,
				combination.members().size()))
				links.add(new SurgicalClientTopology.GroundingLink<>(anchor.subjectKey(), anchor.cubeId(),
					member.subjectKey(), member.cubeId()));
		}
		if (bodies.isEmpty())
			return null;
		TableGroundingCache cache = GROUP_GROUNDING.get(table);
		if (cache == null || cache.tableRevision() != table.clientDataRevision()) {
			cache = new TableGroundingCache(table.clientDataRevision(), new HashMap<>());
			GROUP_GROUNDING.put(table, cache);
		}
		UUID group = bodies.getFirst().key();
		Set<SurgicalClientTopology.GroundingLink<UUID>> linkSet = Set.copyOf(links);
		GroundedGroup previous = cache.groups().get(group);
		Map<UUID, Map<Integer, Vec3>> grounded;
		if (previous != null && previous.surfaceY() == surfaceY
			&& previous.bodies().equals(bodies) && previous.links().equals(linkSet)) {
			grounded = previous.offsets();
			SurgicalProfiler.count("groundConnected(cacheHit)");
		} else {
			grounded = SurgicalClientTopology.groundConnectedBodies(bodies, links, surfaceY);
			if (grounded != null)
				cache.groups().put(group, new GroundedGroup(List.copyOf(bodies), linkSet, surfaceY, grounded));
		}
		return grounded == null ? null : grounded.get(currentSubject.persistentId());
	}
	record TableGroundingCache(int tableRevision, Map<UUID, GroundedGroup> groups) {}
	record GroundedGroup(List<SurgicalClientTopology.GroundingBody<UUID>> bodies,
		Set<SurgicalClientTopology.GroundingLink<UUID>> links, double surfaceY,
		Map<UUID, Map<Integer, Vec3>> offsets) {}
}
