package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableReleaseGeometryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalGeometryStore implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalGeometryStore {
	private SurgicalGeometryStore() {}

	/** Current interaction/render address. This changes whenever the north-west controller moves. */
	static final Map<SubjectKey, TableGeometry> TABLES = new HashMap<>();
	/** Stable geometry ownership. A controller move must not invalidate immutable model geometry. */
	static final Map<UUID, TableGeometry> SUBJECT_GEOMETRIES = new HashMap<>();
	/**
	 * Subjects whose stored topology contradicts the model this client observes, keyed by persistent
	 * id.
	 *
	 * <p>Topology is persisted by the server but re-observed on every client session, by running the
	 * source entity's renderer and recovering cuboids from its vertex stream. A third-party model
	 * replacement therefore changes {@code observedCubeCount} for a creature whose stored count was
	 * measured against the previous model, and {@link TableGeometry#matchesModel} rejects that pairing
	 * permanently: the subject renders nothing and cannot be picked. Its footprints stay reserved on
	 * the server regardless, so the space keeps refusing new placements and the parts still drop when
	 * the table is broken. Marking the subject here is what turns that silent hole into a visible
	 * reserved column.</p>
	 */
	static final Map<UUID, ReservedSubject> RESERVED_SUBJECTS = new HashMap<>();
	@Nullable
	static OwnerHandoffKey validatedOwnerHandoff;
	static final Set<UUID> SAFE_OWNER_HANDOFFS = new java.util.HashSet<>();
	static final Set<UUID> UNSAFE_OWNER_HANDOFFS = new java.util.HashSet<>();
	static long geometryGeneration;

	/** Returns true only when the immutable source-model geometry has to be captured again. */
	static boolean needsGeometryUpdate(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		if (table.getLevel() == null)
			return false;
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null)
			return !reservedObservationIsFresh(table, subject);
		if (geometry.refresh(table, subject))
			geometryGeneration++;
		else
			geometry.reportBounds(table);
		return false;
	}

	static boolean isRenderReady(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		TableGeometry geometry = geometryFor(table, subject);
		return geometryReadyForUse(table, subject, geometry);
	}

	static boolean geometryReadyForUse(SurgicalTableBlockEntity table, SurgicalSubject subject,
		@Nullable TableGeometry geometry) {
		if (geometry == null || !geometry.matchesModel(table, subject) || !geometry.topologyReady()
			|| geometry.renderRevision != subject.clientRenderRevision())
			return false;
		if (geometry.retryPendingGrounding(table))
			geometryGeneration++;
		return !geometry.groundingPending;
	}

	@Nullable
	static AABB cachedRenderBounds(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		SurgicalWrenchController.WrenchSubjectPreview preview = SurgicalWrenchController.wrenchPreviewFor(table, subject);
		if (preview != null)
			return preview.bounds;
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || geometry.groundingPending || !geometry.matchesModel(table, subject)
			|| geometry.renderRevision != subject.clientRenderRevision())
			return null;
		AABB bounds = geometry.bounds;
		SurgicalCutController.BatchCutAnimation animation = SurgicalCutController.batchCutAnimation;
		SurgicalCutController.BatchCutMotion motion = animation != null && animation.tablePos.equals(table.getBlockPos())
			? animation.motions.get(subject.id()) : null;
		return bounds == null || motion == null || motion.startBounds == null
			? bounds : bounds.minmax(motion.startBounds);
	}

	/** Replies once with the current world-space cuboids for an entire unsupported release batch. */
	static void reportReleasedGeometry(
		SurgicalTableReleaseGeometryPacket.ClientBoundRequest request) {
		if (request == null || Minecraft.getInstance().level == null)
			return;
		List<SurgicalTableReleaseGeometryPacket.CubeGeometry> reported = new ArrayList<>();
		for (SurgicalTableReleaseGeometryPacket.SubjectCubes requested : request.subjects()) {
			TableGeometry geometry = SUBJECT_GEOMETRIES.get(requested.subjectKey());
			if (geometry == null || geometry.groundingPending)
				return;
			for (int cubeId : requested.cubes()) {
				SurgicalModelRenderContext.CubeGeometry cube = geometry.cubesById.get(cubeId);
				if (cube == null)
					return;
				reported.add(new SurgicalTableReleaseGeometryPacket.CubeGeometry(
					requested.subjectKey(), cubeId, cube.corners()));
			}
		}
		if (!reported.isEmpty())
			CBPackets.sendToServer(new SurgicalTableReleaseGeometryPacket(request.transaction(), reported));
	}

	static void updateGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		SurgicalModelRenderContext.Snapshot snapshot) {
		MimicProfile profile = subject.profile();
		if (table.getLevel() == null || snapshot.observedCubeCount() <= 0)
			return;
		int cubeCount = snapshot.observedCubeCount();
		if (subject.cubeCount() != cubeCount) {
			// matchesModel() will reject this pairing for as long as the model stays replaced, so a
			// geometry built here is discarded by the very next geometryFor() and rebuilt on the next
			// frame - contact topology, grounding and all. Record the reserved column instead. The
			// observation itself deliberately keeps running: the moment the model that produced the
			// stored count is back, the counts agree again and the subject recovers on its own.
			markReserved(table, subject, snapshot);
			return;
		}
		if (RESERVED_SUBJECTS.remove(subject.persistentId()) != null) {
			// This observation was taken over the whole model, because a reserved subject has no usable
			// present-set to observe through. Now that the counts agree again, let the next frame
			// re-observe properly rather than seeding the geometry with cubes the subject no longer has.
			return;
		}
		List<SurgicalAssembly.Seam> seams = subject.seams();
		List<SurgicalClientTopology.Contact> contacts;
		Supplier<SurgicalClientTopology.ContactTopology> topologyBuild = null;
		CompletableFuture<SurgicalClientTopology.ContactTopology> pendingTopology = null;
		if (cubeCount > SurgicalTableClientHandler.ASYNC_TOPOLOGY_CUBE_THRESHOLD) {
			List<SurgicalAssembly.Seam> frozenSeams = List.copyOf(seams);
			List<SurgicalModelRenderContext.CubeGeometry> frozenCubes = List.copyOf(snapshot.cubes());
			contacts = List.of();
			topologyBuild = () -> {
				long started = SurgicalProfiler.begin();
				try {
					return new SurgicalClientTopology.ContactTopology(frozenSeams,
						SurgicalClientTopology.contactsFor(frozenSeams, frozenCubes));
				} finally {
					SurgicalProfiler.end("contactsFor(async)", started);
				}
			};
			pendingTopology = SurgicalClientExecutors.submit(topologyBuild);
		} else {
			contacts = SurgicalClientTopology.contactsFor(seams, snapshot.cubes());
		}
		TableGeometry geometry = new TableGeometry(subject.persistentId(), subject.id(), profile,
			subject.layPose(), worldOriginX(table, subject), worldOriginZ(table, subject), cubeCount,
			snapshot.cubes(), seams, contacts,
			topologyBuild, pendingTopology, table.getLevel().getGameTime());
		geometry.refresh(table, subject);
		registerGeometry(table, subject, geometry);
		geometryGeneration++;
	}

	/**
	 * Whether this subject's stored topology is known to contradict the model this client observes.
	 *
	 * <p>Such a subject can never be rendered from its stored present-set, and asking for that set
	 * would filter the observation down to an arbitrary prefix of the replacement model's components.
	 * The renderer uses this to observe the whole model instead, so the reserved column can be raised
	 * to the height the source model actually measures.</p>
	 */
	static boolean isReserved(SurgicalSubject subject) {
		ReservedSubject reserved = RESERVED_SUBJECTS.get(subject.persistentId());
		return reserved != null && reserved.storedCubeCount() == subject.cubeCount();
	}

	/**
	 * Records the column a subject reserves while its model cannot be drawn: the footprints the
	 * server still refuses to place anything into, raised from the table surface to the measured
	 * height of the source model. The captured snapshot has not been grounded, so only its vertical
	 * extent is used, never its absolute Y.
	 */
	static void markReserved(SurgicalTableBlockEntity table, SurgicalSubject subject,
		SurgicalModelRenderContext.Snapshot snapshot) {
		double modelMinY = Double.POSITIVE_INFINITY;
		double modelMaxY = Double.NEGATIVE_INFINITY;
		for (SurgicalModelRenderContext.CubeGeometry cube : snapshot.cubes())
			for (Vec3 corner : cube.corners()) {
				modelMinY = Math.min(modelMinY, corner.y);
				modelMaxY = Math.max(modelMaxY, corner.y);
			}
		if (!Double.isFinite(modelMinY)) {
			RESERVED_SUBJECTS.remove(subject.persistentId());
			return;
		}

		double minX = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
			minX = Math.min(minX, footprint.minX());
			minZ = Math.min(minZ, footprint.minZ());
			maxX = Math.max(maxX, footprint.maxX());
			maxZ = Math.max(maxZ, footprint.maxZ());
		}
		double surfaceY = SurgicalTablePlane.surfaceY(table.getBlockPos().getY());
		// The observed model's world corners carry the camera-relative render transform's float
		// rounding, so quantise the height: an unquantised box jitters in its last bits as the player
		// moves, which would rebuild the outline mesh on every frame.
		double height = Math.max(Math.ceil((modelMaxY - modelMinY) * 1024.0d) / 1024.0d, SurgicalTableClientHandler.MODEL_PIXEL_SIZE);
		int observedCubeCount = snapshot.observedCubeCount();
		ReservedSubject previous = RESERVED_SUBJECTS.get(subject.persistentId());
		List<List<Vec3>> cuboids = previous != null && previous.sameSubjectAndTopology(table.getBlockPos(),
			subject.id(), subject.cubeCount(), observedCubeCount) ? previous.cuboids() : cuboidsOf(snapshot);
		RESERVED_SUBJECTS.put(subject.persistentId(), new ReservedSubject(table.getBlockPos(),
			subject.id(), subject.cubeCount(), observedCubeCount, table.getLevel().getGameTime(), cuboids,
			new AABB(minX, surfaceY, minZ, maxX, surfaceY + height, maxZ)));
	}

	static List<List<Vec3>> cuboidsOf(SurgicalModelRenderContext.Snapshot snapshot) {
		List<List<Vec3>> cuboids = new ArrayList<>(snapshot.cubes().size());
		for (SurgicalModelRenderContext.CubeGeometry cube : snapshot.cubes())
			cuboids.add(cube.corners());
		return List.copyOf(cuboids);
	}

	/**
	 * Whether this subject was recently confirmed to contradict its stored topology.
	 *
	 * <p>Such a subject has no geometry to find, so {@link #needsGeometryUpdate} would otherwise ask
	 * for a fresh observation on every frame for as long as it is in view - and every one of those
	 * claims a slot from the shared cold-build budget, which is exactly what the subjects that can
	 * still be built are queued on. The answer cannot change unless the model does, and every path
	 * that changes the model clears the markers outright, so this slow retry only has to cover a
	 * render-plan eviction.</p>
	 */
	static boolean reservedObservationIsFresh(SurgicalTableBlockEntity table,
		SurgicalSubject subject) {
		ReservedSubject reserved = RESERVED_SUBJECTS.get(subject.persistentId());
		if (reserved == null || reserved.storedCubeCount() != subject.cubeCount())
			return false;
		long elapsed = table.getLevel().getGameTime() - reserved.observedAtTick();
		return elapsed >= 0L && elapsed < SurgicalTableClientHandler.RESERVED_REOBSERVE_TICKS;
	}

	/**
	 * Approximates how much creature the shovel is about to remove.
	 *
	 * <p>The two topologies share no cube numbering, so the stored present-set cannot be projected
	 * onto the observed model. The union volume of the whole observed model is the honest measurement
	 * available; scaling it by the fraction of stored cubes still present keeps a subject that was
	 * mostly cut away from paying out as though it were intact.</p>
	 *
	 * <p>Deliberately measured here, on the click, rather than when the marker is recorded:
	 * {@link SurgicalVolumeSampler} is budgeted in millions of coverage tests, and paying that during
	 * a render pass would stall the frame a reserved column first came into view. This is the same
	 * moment the ordinary shovel measures its own component.</p>
	 */
	static double reservedVolume(SurgicalSubject subject, ReservedSubject reserved) {
		double volume = SurgicalVolumeSampler.unionVolume(reserved.cuboids());
		if (!Double.isFinite(volume) || volume <= 0.0d)
			return 0.0d;
		int storedCubeCount = subject.cubeCount();
		int presentCubeCount = subject.presentCubes().cardinality();
		return volume * Math.min(1.0d, (double) presentCubeCount / storedCubeCount);
	}

	static Map<Integer, Vec3> offsetsFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		SurgicalWrenchController.WrenchSubjectPreview preview = SurgicalWrenchController.wrenchPreviewFor(table, subject);
		if (preview != null)
			return preview.offsets;
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || !geometry.matchesModel(table, subject))
			return subject.componentOffsetsForRender();
		if (geometry.retryPendingGrounding(table))
			geometryGeneration++;
		geometry.markSeen(table);
		return SurgicalCutController.batchCutRenderOffsets(table, subject, geometry);
	}

	static Map<Integer, SurgicalCubeRotation> rotationsFor(SurgicalTableBlockEntity table,
		SurgicalSubject subject) {
		SurgicalWrenchController.WrenchSubjectPreview preview = SurgicalWrenchController.wrenchPreviewFor(table, subject);
		if (preview != null)
			return preview.rotations;
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || !geometry.matchesModel(table, subject))
			return subject.componentRotationsForRender();
		geometry.markSeen(table);
		return geometry.rotations;
	}

	static BitSet presentCubesFor(SurgicalTableBlockEntity table, SurgicalSubject subject,
		int observedCubeCount) {
		TableGeometry geometry = geometryFor(table, subject);
		BitSet present = geometry != null && geometry.observedCubeCount == observedCubeCount
			&& geometry.matchesModel(table, subject)
			? geometry.presentCubes : subject.presentCubes();
		BitSet visible = SurgicalPlacementController.temporaryMoveVisibleCubes(subject, present);
		if (SurgicalGlueController.gluePreview == null || !SurgicalGlueController.gluePreview.ownerPos().equals(table.getBlockPos()))
			return visible;
		for (SurgicalGluePreviewPlanner.GlueSubjectPreview moved : SurgicalGlueController.gluePreview.subjects()) {
			if (moved.subjectId != subject.id())
				continue;
			// The unhidden set is the geometry's own, so only a preview that hides cubes copies it.
			if (visible == present)
				visible = (BitSet) present.clone();
			visible.andNot(moved.cubes);
		}
		return visible;
	}

	/**
	 * Resolves a geometry by the subject's persistent identity, then publishes it at the controller's
	 * current transient address. Adding or removing the north-west table changes that address and the
	 * subject's controller-relative origin, but server-side rebasing keeps the world origin unchanged,
	 * so neither change requires another model capture.
	 */
	@Nullable
	static TableGeometry geometryFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		SubjectKey address = new SubjectKey(table.getBlockPos(), subject.id());
		TableGeometry addressed = TABLES.get(address);
		if (addressed != null && !addressed.persistentId.equals(subject.persistentId())) {
			TABLES.remove(address, addressed);
			addressed.orphan(table.getLevel() == null ? Long.MIN_VALUE : table.getLevel().getGameTime());
			addressed = null;
		}
		TableGeometry geometry = addressed != null ? addressed
			: SUBJECT_GEOMETRIES.get(subject.persistentId());
		if (geometry == null)
			return null;
		if (!geometry.matchesModel(table, subject)) {
			removeGeometry(geometry);
			geometryGeneration++;
			return null;
		}
		bindGeometry(address, geometry);
		return geometry;
	}

	static void registerGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		TableGeometry geometry) {
		TableGeometry previous = SUBJECT_GEOMETRIES.put(subject.persistentId(), geometry);
		if (previous != null && previous != geometry)
			removeGeometry(previous);
		bindGeometry(new SubjectKey(table.getBlockPos(), subject.id()), geometry);
	}

	static void bindGeometry(SubjectKey address, TableGeometry geometry) {
		SubjectKey previousAddress = geometry.ownerKey;
		if (address.equals(previousAddress) && TABLES.get(address) == geometry)
			return;
		if (previousAddress != null && !previousAddress.equals(address)) {
			TABLES.remove(previousAddress, geometry);
			geometry.ownerChanged = true;
		}
		TableGeometry displaced = TABLES.put(address, geometry);
		if (displaced != null && displaced != geometry) {
			displaced.orphan(geometry.lastSeenTick);
			if (displaced.ownerKey != null)
				TABLES.remove(displaced.ownerKey, displaced);
		}
		geometry.ownerKey = address;
		geometry.subjectId = address.subjectId;
		geometry.orphanedAtTick = Long.MIN_VALUE;
	}

	static void removeGeometry(TableGeometry geometry) {
		if (geometry.ownerKey != null)
			TABLES.remove(geometry.ownerKey, geometry);
		SUBJECT_GEOMETRIES.remove(geometry.persistentId, geometry);
		geometry.ownerKey = null;
		geometry.dispose();
	}

	static double worldOriginX(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return table.getBlockPos().getX() + subject.originOffsetX();
	}

	static double worldOriginZ(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return table.getBlockPos().getZ() + subject.originOffsetZ();
	}

	/** Verifies that a linked body's complete grounding group is only changing controller address. */
	static boolean migrationGroupUnchanged(SurgicalTableBlockEntity table, SurgicalSubject subject,
		OwnerHandoffKey handoff) {
		if (!handoff.equals(validatedOwnerHandoff)) {
			validatedOwnerHandoff = handoff;
			SAFE_OWNER_HANDOFFS.clear();
			UNSAFE_OWNER_HANDOFFS.clear();
		}
		if (SAFE_OWNER_HANDOFFS.contains(subject.persistentId()))
			return true;
		if (UNSAFE_OWNER_HANDOFFS.contains(subject.persistentId()))
			return false;
		Set<Integer> group = new java.util.HashSet<>();
		group.add(subject.id());
		if (subject.linkedToOtherSubjects())
			group.addAll(table.connectedSubjectIds(subject.id()));
		Set<UUID> persistentIds = new java.util.HashSet<>(group.size());
		boolean unchanged = true;
		for (int subjectId : group) {
			SurgicalSubject member = table.getSubject(subjectId);
			if (member != null)
				persistentIds.add(member.persistentId());
			TableGeometry geometry = member == null ? null
				: SUBJECT_GEOMETRIES.get(member.persistentId());
			if (geometry == null || !geometry.matchesModel(table, member)
				|| geometry.subjectState == null
				|| !geometry.subjectState.matches(member, geometry.observedCubeCount)) {
				unchanged = false;
				break;
			}
			if (geometry.ownerChanged || handoff.equals(geometry.cheapOwnerHandoff)
				|| ownerAddressIsStale(table.getLevel() instanceof ClientLevel level ? level : null, geometry))
				continue;
			unchanged = false;
			break;
		}
		(unchanged ? SAFE_OWNER_HANDOFFS : UNSAFE_OWNER_HANDOFFS).addAll(persistentIds);
		return unchanged;
	}

	static boolean ownerAddressIsStale(@Nullable ClientLevel level, TableGeometry geometry) {
		if (level == null || geometry.ownerKey == null)
			return geometry.ownerKey == null;
		if (!(level.getBlockEntity(geometry.ownerKey.tablePos) instanceof SurgicalTableBlockEntity owner))
			return true;
		SurgicalSubject owned = owner.getSubject(geometry.ownerKey.subjectId);
		return owned == null || !geometry.persistentId.equals(owned.persistentId());
	}

	static boolean finiteCubeGeometry(SurgicalModelRenderContext.CubeGeometry cube) {
		return cube != null && cube.corners().size() == 8 && cube.corners().stream().allMatch(corner ->
			Double.isFinite(corner.x) && Double.isFinite(corner.y) && Double.isFinite(corner.z));
	}

	record GeometryPreview(TableGeometry geometry, Map<Integer, Vec3> previewOffsets,
		BitSet previewCutSeams, @Nullable SurgicalGlueJoint excludedJoint) {
		GeometryPreview {
			java.util.Objects.requireNonNull(geometry, "geometry");
			previewOffsets = Map.copyOf(previewOffsets);
			previewCutSeams = (BitSet) previewCutSeams.clone();
		}
	}

	static final class TableGeometry {
		final UUID persistentId;
		int subjectId;
		private final MimicProfile profile;
		private final SurgicalLayPose layPose;
		private final double worldOriginX;
		private final double worldOriginZ;
		final int observedCubeCount;
		final List<SurgicalModelRenderContext.CubeGeometry> baseCubes;
		final Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseCubesById;
		List<SurgicalModelRenderContext.CubeGeometry> layoutCubes;
		List<SurgicalModelRenderContext.CubeGeometry> cubes;
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubesById;
		List<SurgicalSelectionResolver.CubeTarget> cubeTargets;
		@Nullable
		AABB bounds;
		List<SurgicalAssembly.Seam> seams;
		Map<SurgicalAssembly.Seam, Integer> seamIds;
		List<SurgicalClientTopology.Contact> baseContacts;
		List<SurgicalClientTopology.Contact> contacts;
		List<List<SurgicalClientTopology.Contact>> contactsByCube;
		@Nullable
		private Supplier<SurgicalClientTopology.ContactTopology> topologyBuild;
		@Nullable
		private CompletableFuture<SurgicalClientTopology.ContactTopology> pendingTopology;
		private boolean topologyAvailable;
		BitSet presentCubes = new BitSet();
		BitSet cutSeams = new BitSet();
		Map<Integer, Vec3> serverOffsets = Map.of();
		Map<Integer, Vec3> offsets = Map.of();
		Map<Integer, SurgicalCubeRotation> serverRotations = Map.of();
		Map<Integer, SurgicalCubeRotation> rotations = Map.of();
		@Nullable
		GeometryPreview stagedPreview;
		private long cacheWeight;
		private Map<Integer, Vec3> pendingGroundingOffsets = Map.of();
		private Map<Integer, SurgicalCubeRotation> pendingGroundingRotations = Map.of();
		private BitSet pendingGroundingCutSeams = new BitSet();
		@Nullable
		private SurgicalGlueJoint pendingGroundingExcludedJoint;
		boolean groundingPending;
		private boolean groupedGrounding;
		long groundingInputRevision;
		private long lastGroundingAttemptGeneration = Long.MIN_VALUE;
		/** Bumped by every {@code applyTransforms}, so derived outlines can tell when cubes moved. */
		long transformGeneration;
		int renderRevision = Integer.MIN_VALUE;
		long lastSeenTick;
		@Nullable
		SubjectKey ownerKey;
		private boolean ownerChanged;
		long orphanedAtTick = Long.MIN_VALUE;
		@Nullable
		private SubjectGeometryState subjectState;
		@Nullable
		private OwnerHandoffKey cheapOwnerHandoff;

		private TableGeometry(UUID persistentId, int subjectId, MimicProfile profile, SurgicalLayPose layPose,
			double worldOriginX, double worldOriginZ, int observedCubeCount,
			List<SurgicalModelRenderContext.CubeGeometry> cubes, List<SurgicalAssembly.Seam> seams,
			List<SurgicalClientTopology.Contact> contacts,
			@Nullable Supplier<SurgicalClientTopology.ContactTopology> topologyBuild,
			@Nullable CompletableFuture<SurgicalClientTopology.ContactTopology> pendingTopology,
			long lastSeenTick) {
			this.persistentId = persistentId;
			this.subjectId = subjectId;
			this.profile = profile;
			this.layPose = layPose;
			this.worldOriginX = worldOriginX;
			this.worldOriginZ = worldOriginZ;
			this.observedCubeCount = observedCubeCount;
			this.baseCubes = List.copyOf(cubes);
			this.baseCubesById = indexCubes(this.baseCubes);
			this.layoutCubes = this.baseCubes;
			this.cubes = this.baseCubes;
			this.cubesById = indexCubes(this.cubes);
			this.cubeTargets = cubeTargets(this.cubes);
			this.bounds = boundsFor(this.cubeTargets);
			this.seams = List.copyOf(seams);
			this.seamIds = SurgicalSelectionResolver.seamIds(this.seams);
			this.baseContacts = List.copyOf(contacts);
			this.contacts = this.baseContacts;
			this.contactsByCube = contactsByCube(observedCubeCount, this.contacts);
			this.cacheWeight = estimateCacheWeight(this.baseCubes, this.seams, this.baseContacts);
			this.topologyBuild = topologyBuild;
			this.pendingTopology = pendingTopology;
			this.topologyAvailable = topologyBuild == null;
			this.lastSeenTick = lastSeenTick;
		}

		boolean matchesModel(SurgicalTableBlockEntity table, SurgicalSubject subject) {
			return persistentId.equals(subject.persistentId()) && profile.equals(subject.profile())
				&& subject.layPose().equals(layPose)
				&& Double.doubleToLongBits(worldOriginX(table, subject)) == Double.doubleToLongBits(worldOriginX)
				&& Double.doubleToLongBits(worldOriginZ(table, subject)) == Double.doubleToLongBits(worldOriginZ)
				&& subject.cubeCount() == observedCubeCount;
		}

		private boolean refresh(SurgicalTableBlockEntity table, SurgicalSubject subject) {
			markSeen(table);
			boolean topologyChanged = resolvePendingTopology();
			int revision = subject.clientRenderRevision();
			boolean stateChanged = subjectState == null
				|| !subjectState.matches(subject, observedCubeCount);
			if (!ownerChanged && revision == renderRevision && !topologyChanged && !stateChanged)
				return false;
			if (!SurgicalPreparationBudget.begin(false)) {
				renderRevision = Integer.MIN_VALUE;
				return false;
			}
			try {
				OwnerHandoffKey handoff = new OwnerHandoffKey(table.getBlockPos(), table.clientDataRevision());
				// A controller migration changes the block-entity revision and transient subject address. If
				// every member of this grounding group still has the exact cached state and world anchor, the
				// already-grounded cubes remain valid and only the new owner needs their cached bounds.
				if (ownerChanged && !topologyChanged && !stateChanged
					&& migrationGroupUnchanged(table, subject, handoff)) {
					renderRevision = revision;
					ownerChanged = false;
					cheapOwnerHandoff = handoff;
					return false;
				}

				if (subject.cubeCount() == observedCubeCount && !seams.equals(subject.seams())) {
					List<SurgicalAssembly.Seam> updatedSeams = List.copyOf(subject.seams());
					seams = updatedSeams;
					seamIds = SurgicalSelectionResolver.seamIds(updatedSeams);
					if (observedCubeCount > SurgicalTableClientHandler.ASYNC_TOPOLOGY_CUBE_THRESHOLD) {
						// The same deferral updateGeometry() applies to a first build. contactsFor() runs a
						// C(12,3) convex clip per seam over up to MAX_SEAMS seams, and this path had no
						// threshold at all - so a server seam update on a large body solved the entire
						// contact set inline on the render thread. Publish an empty set now and let
						// resolvePendingTopology() adopt the real one; topologyReady() keeps consumers off
						// it in the meantime, exactly as it does for a cold build.
						List<SurgicalModelRenderContext.CubeGeometry> frozenCubes = List.copyOf(baseCubes);
						baseContacts = List.of();
						topologyAvailable = false;
						topologyBuild = () -> {
							long started = SurgicalProfiler.begin();
							try {
								return new SurgicalClientTopology.ContactTopology(updatedSeams,
									SurgicalClientTopology.contactsFor(updatedSeams, frozenCubes));
							} finally {
								SurgicalProfiler.end("contactsFor(async)", started);
							}
						};
						pendingTopology = SurgicalClientExecutors.submit(topologyBuild);
					} else {
						baseContacts = SurgicalClientTopology.contactsFor(updatedSeams, baseCubes);
					}
					cacheWeight = estimateCacheWeight(baseCubes, seams, baseContacts);
				}
				presentCubes = subject.presentCubes();
				cutSeams = subject.cutSeamsForRender();
				serverOffsets = Map.copyOf(subject.componentOffsetsForRender());
				serverRotations = Map.copyOf(subject.componentRotationsForRender());
				subjectState = SubjectGeometryState.capture(subject, observedCubeCount);
				ownerChanged = false;
				cheapOwnerHandoff = null;
				// Publish the revision before grounding so a newly refreshed connected group can prove
				// that every geometry snapshot belongs to the same table update.
				renderRevision = revision;
				applyTransforms(table, serverOffsets, serverRotations);
				return true;
			} finally { SurgicalPreparationBudget.end(); }
		}

		void orphan(long now) {
			ownerKey = null;
			ownerChanged = true;
			orphanedAtTick = now;
		}

		void stagePreview(GeometryPreview preview) {
			stagedPreview = preview;
		}

		void applyStagedPreview(SurgicalTableBlockEntity table) {
			GeometryPreview preview = stagedPreview;
			if (preview != null)
				applyTransforms(table, preview.previewOffsets(), serverRotations,
					preview.previewCutSeams(), preview.excludedJoint());
		}

		void discardStagedPreview() {
			stagedPreview = null;
		}

		void applyServerTransforms(SurgicalTableBlockEntity table) {
			applyTransforms(table, serverOffsets, serverRotations);
		}

		private void applyTransforms(SurgicalTableBlockEntity table, Map<Integer, Vec3> appliedOffsets,
			Map<Integer, SurgicalCubeRotation> appliedRotations) {
			applyTransforms(table, appliedOffsets, appliedRotations, cutSeams, null);
		}

		private void applyTransforms(SurgicalTableBlockEntity table, Map<Integer, Vec3> appliedOffsets,
			Map<Integer, SurgicalCubeRotation> appliedRotations,
			BitSet appliedCutSeams, @Nullable SurgicalGlueJoint excludedJoint) {
			long started = SurgicalProfiler.begin();
			boolean groundingRequestChanged = !pendingGroundingOffsets.equals(appliedOffsets)
				|| !pendingGroundingRotations.equals(appliedRotations)
				|| !pendingGroundingCutSeams.equals(appliedCutSeams)
				|| !java.util.Objects.equals(pendingGroundingExcludedJoint, excludedJoint);
			if (groundingRequestChanged)
				lastGroundingAttemptGeneration = Long.MIN_VALUE;
			pendingGroundingOffsets = Map.copyOf(appliedOffsets);
			pendingGroundingRotations = Map.copyOf(appliedRotations);
			pendingGroundingCutSeams = (BitSet) appliedCutSeams.clone();
			pendingGroundingExcludedJoint = excludedJoint;
			double surfaceY = SurgicalTablePlane.surfaceY(table.getBlockPos().getY())
				+ SurgicalTablePoseResolver.TABLE_CLEARANCE;
			SurgicalSubject subject = table.getSubject(subjectId);
			layoutCubes = SurgicalGluePreviewPlanner.transformCubes(baseCubes, appliedRotations, Map.of());
			// A subject with no glue joint and no combination is its own grounding component, so walking
			// every subject on the table to build a multi-body connection graph cannot change its answer.
			// Unifying glue and native seams dropped this guard, which put the full graph build on every
			// transform of every subject.
			boolean linked = subject != null && subject.linkedToOtherSubjects();
			if (linked && stagedPreview == null && excludedJoint == null
				&& appliedOffsets.equals(serverOffsets) && appliedRotations.equals(serverRotations)
				&& appliedCutSeams.equals(cutSeams)) {
				groundingPending = true;
				groupedGrounding = true;
				groundingInputRevision++;
				SurgicalGroundingPreparation.queueGroupedGrounding(table, subject, this);
				SurgicalProfiler.end("applyTransforms", started);
				return;
			}
			groupedGrounding = false;
			Map<Integer, Vec3> grounded = subject == null || !linked ? null
				: SurgicalGroundingPreparation.groundConnectedComponents(table, subject, this, appliedOffsets,
					appliedCutSeams, excludedJoint, surfaceY);
			groundingPending = subject != null && linked && grounded == null;
			if (groundingPending) {
				// A linked body's Y is defined by the complete cross-subject connection graph. Falling
				// back to native per-subject grounding here briefly breaks strong-glue constraints and,
				// worse, lets interaction planning persist that derived fallback Y on the server. Keep
				// the last complete transform hidden until every member can be grounded together.
				SurgicalProfiler.end("applyTransforms", started);
				return;
			}
			offsets = linked ? grounded
				: SurgicalClientTopology.groundAllComponents(observedCubeCount, presentCubes,
					seams, appliedCutSeams, layoutCubes, appliedOffsets, surfaceY);
			rotations = Map.copyOf(appliedRotations);
			cubes = SurgicalGluePreviewPlanner.transformCubes(baseCubes, rotations, offsets);
			cubesById = indexCubes(cubes);
			cubeTargets = cubeTargets(cubes);
			bounds = boundsFor(cubeTargets);
			contacts = transformContacts(baseContacts, baseCubesById, rotations, offsets);
			contactsByCube = contactsByCube(observedCubeCount, contacts);
			transformGeneration++;
			if (bounds != null)
				table.includeClientRenderBounds(bounds);
			SurgicalProfiler.end("applyTransforms", started);
		}

		/**
		 * Re-contributes this subject's already-measured bounds to the table.
		 *
		 * <p>The table drops its measured render bounds whenever its subject set changes, and the only
		 * thing that used to put them back was a full transform pass. That forced every subject on the
		 * table to rebuild its geometry just so the bounds could be re-accumulated, which is what made
		 * packing and placing cost the whole table instead of the one subject that moved. The bounds are
		 * already cached here, so re-reporting them is one AABB union.</p>
		 */
		void reportBounds(SurgicalTableBlockEntity table) {
			if (bounds != null)
				table.includeClientRenderBounds(bounds);
		}

		/**
		 * Grounding fails while some geometry in the connected group is still waiting for its async
		 * contact topology. Nothing about that can change without some geometry being created or
		 * refreshed, both of which move {@code geometryGeneration}, so retrying on any other frame would
		 * only re-run a full transform pass for the same reason it failed last time.
		 */
		private boolean retryPendingGrounding(SurgicalTableBlockEntity table) {
			if (!groundingPending || groupedGrounding || lastGroundingAttemptGeneration == geometryGeneration)
				return false;
			lastGroundingAttemptGeneration = geometryGeneration;
			applyTransforms(table, pendingGroundingOffsets, pendingGroundingRotations,
				pendingGroundingCutSeams, pendingGroundingExcludedJoint);
			return !groundingPending;
		}

		static List<SurgicalSelectionResolver.CubeTarget> cubeTargets(
			List<SurgicalModelRenderContext.CubeGeometry> cubes) {
			List<SurgicalSelectionResolver.CubeTarget> targets = new ArrayList<>(cubes.size());
			for (SurgicalModelRenderContext.CubeGeometry cube : cubes) {
				double minX = Double.POSITIVE_INFINITY;
				double minY = Double.POSITIVE_INFINITY;
				double minZ = Double.POSITIVE_INFINITY;
				double maxX = Double.NEGATIVE_INFINITY;
				double maxY = Double.NEGATIVE_INFINITY;
				double maxZ = Double.NEGATIVE_INFINITY;
				for (Vec3 corner : cube.corners()) {
					minX = Math.min(minX, corner.x);
					minY = Math.min(minY, corner.y);
					minZ = Math.min(minZ, corner.z);
					maxX = Math.max(maxX, corner.x);
					maxY = Math.max(maxY, corner.y);
					maxZ = Math.max(maxZ, corner.z);
				}
				if (minX != Double.POSITIVE_INFINITY)
					targets.add(new SurgicalSelectionResolver.CubeTarget(cube, new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
			}
			return List.copyOf(targets);
		}

		@Nullable
		static AABB boundsFor(List<SurgicalSelectionResolver.CubeTarget> targets) {
			AABB result = null;
			for (SurgicalSelectionResolver.CubeTarget target : targets)
				result = result == null ? target.bounds() : result.minmax(target.bounds());
			return result;
		}

		private boolean resolvePendingTopology() {
			if (pendingTopology == null) {
				if (topologyBuild != null)
					pendingTopology = SurgicalClientExecutors.submit(topologyBuild);
				return false;
			}
			if (!pendingTopology.isDone())
				return false;
			SurgicalClientTopology.ContactTopology topology;
			try {
				topology = pendingTopology.join();
			} catch (RuntimeException exception) {
				pendingTopology = null;
				if (!SurgicalClientExecutors.wasQueueFull(exception))
					topologyBuild = null;
				return false;
			}
			pendingTopology = null;
			topologyBuild = null;
			seams = topology.seams();
			seamIds = SurgicalSelectionResolver.seamIds(seams);
			baseContacts = topology.contacts();
			cacheWeight = estimateCacheWeight(baseCubes, seams, baseContacts);
			topologyAvailable = true;
			return true;
		}

		boolean topologyReady() {
			return pendingTopology == null && topologyAvailable;
		}

		private void markSeen(SurgicalTableBlockEntity table) {
			if (table.getLevel() != null)
				lastSeenTick = table.getLevel().getGameTime();
		}

		long cacheWeight() {
			return cacheWeight;
		}

		private static long estimateCacheWeight(List<SurgicalModelRenderContext.CubeGeometry> cubes,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Contact> contacts) {
			long weight = 64L + cubes.size() * 32L + seams.size() * 2L;
			for (SurgicalClientTopology.Contact contact : contacts) {
				weight += 8L;
				for (List<Vec3> face : contact.faces())
					weight += face.size();
			}
			return weight;
		}

		void dispose() {
			topologyBuild = null;
			if (pendingTopology != null) {
				pendingTopology.cancel(false);
				pendingTopology = null;
			}
		}

		List<SurgicalClientTopology.Contact> contactsFor(int cubeId) {
			return cubeId >= 0 && cubeId < contactsByCube.size() ? contactsByCube.get(cubeId) : List.of();
		}

		List<SurgicalClientTopology.Edge> cubeEdges(SurgicalAssembly.Seam seam) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(24);
			SurgicalModelRenderContext.CubeGeometry first = cubesById.get(seam.first());
			SurgicalModelRenderContext.CubeGeometry second = cubesById.get(seam.second());
			if (first != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(first));
			if (second != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(second));
			return List.copyOf(edges);
		}

		List<SurgicalClientTopology.Edge> componentCubeEdges(BitSet component) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(component.cardinality() * 12);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry geometry = cubesById.get(cube);
				if (geometry != null)
					edges.addAll(SurgicalClientTopology.cubeEdges(geometry));
			}
			return List.copyOf(edges);
		}

		private static Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexCubes(
			List<SurgicalModelRenderContext.CubeGeometry> cubes) {
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId = new HashMap<>();
			for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
				byId.putIfAbsent(cube.cubeId(), cube);
			return Map.copyOf(byId);
		}

		static List<SurgicalClientTopology.Contact> transformContacts(
			List<SurgicalClientTopology.Contact> contacts,
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseCubes,
			Map<Integer, SurgicalCubeRotation> rotations, Map<Integer, Vec3> offsets) {
			if (rotations.isEmpty() && offsets.isEmpty())
				return contacts;
			List<SurgicalClientTopology.Contact> translated = new ArrayList<>(contacts.size());
			for (SurgicalClientTopology.Contact contact : contacts) {
				Vec3 offset = offsets.getOrDefault(contact.anchorCubeId(), Vec3.ZERO);
				SurgicalCubeRotation rotation = rotations.getOrDefault(contact.anchorCubeId(),
					SurgicalCubeRotation.IDENTITY);
				SurgicalModelRenderContext.CubeGeometry base = baseCubes.get(contact.anchorCubeId());
				if (base == null || offset.equals(Vec3.ZERO) && rotation.isIdentity()) {
					translated.add(contact);
					continue;
				}
				Vec3 center = SurgicalGluePreviewPlanner.cubeCenter(base);
				List<List<Vec3>> faces = new ArrayList<>(contact.faces().size());
				for (List<Vec3> face : contact.faces()) {
					List<Vec3> translatedFace = new ArrayList<>(face.size());
					for (Vec3 point : face)
						translatedFace.add(center.add(rotation.rotate(point.subtract(center))).add(offset));
					faces.add(translatedFace);
				}
				translated.add(new SurgicalClientTopology.Contact(contact.seam(), contact.anchorCubeId(), faces));
			}
			return List.copyOf(translated);
		}

		static List<List<SurgicalClientTopology.Contact>> contactsByCube(int cubeCount,
			List<SurgicalClientTopology.Contact> contacts) {
			List<List<SurgicalClientTopology.Contact>> byCube = new ArrayList<>(cubeCount);
			for (int cube = 0; cube < cubeCount; cube++)
				byCube.add(new ArrayList<>());
			for (SurgicalClientTopology.Contact contact : contacts) {
				SurgicalAssembly.Seam seam = contact.seam();
				if (seam.first() >= 0 && seam.first() < cubeCount)
					byCube.get(seam.first()).add(contact);
				if (seam.second() >= 0 && seam.second() < cubeCount)
					byCube.get(seam.second()).add(contact);
			}
			for (int cube = 0; cube < cubeCount; cube++)
				byCube.set(cube, List.copyOf(byCube.get(cube)));
			return List.copyOf(byCube);
		}
	}

	record SubjectKey(BlockPos tablePos, int subjectId) {
		SubjectKey {
			tablePos = tablePos.immutable();
		}
	}

	/**
	 * One subject whose space stays reserved while its model cannot be drawn. {@code storedCubeCount}
	 * is the count the mismatch was measured against, so a server-side topology change retires the
	 * marker instead of keeping a stale box alive. {@code observedCubeCount} and {@code cuboids}
	 * describe the model actually observed, and are what a shovel has to send to remove the subject.
	 */
	record ReservedSubject(BlockPos tablePos, int subjectId, int storedCubeCount,
		int observedCubeCount, long observedAtTick, List<List<Vec3>> cuboids, AABB bounds) {
		ReservedSubject {
			tablePos = tablePos.immutable();
			cuboids = List.copyOf(cuboids);
		}

		/** Whether {@code other} describes the same subject, table and pair of contradicting counts. */
		private boolean sameSubjectAndTopology(BlockPos otherTablePos, int otherSubjectId,
			int otherStoredCubeCount, int otherObservedCubeCount) {
			return subjectId == otherSubjectId && storedCubeCount == otherStoredCubeCount
				&& observedCubeCount == otherObservedCubeCount && tablePos.equals(otherTablePos);
		}
	}

	/** Exact dynamic state whose equality makes an owner-only cache handoff safe. */
	record SubjectGeometryState(BitSet presentCubes, List<SurgicalAssembly.Seam> seams,
		BitSet cutSeams, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations, Set<SurgicalGlueJoint> glueJoints,
		Map<UUID, List<SurgicalCombination.Member>> combinations) {

		SubjectGeometryState {
			presentCubes = (BitSet) presentCubes.clone();
			seams = List.copyOf(seams);
			cutSeams = (BitSet) cutSeams.clone();
			offsets = Map.copyOf(offsets);
			rotations = Map.copyOf(rotations);
			glueJoints = Set.copyOf(glueJoints);
			combinations = Map.copyOf(combinations);
		}

		/**
		 * Field-by-field comparison against the live subject. The snapshot this replaces was built and
		 * thrown away on every call, and the callers ask once per subject per frame purely to learn
		 * that nothing moved.
		 *
		 * <p>Relies on the glue-joint list being duplicate-free, which {@code addGlueJoint} enforces;
		 * a duplicate would only cost a permanent cache miss, never a wrong answer.
		 */
		private boolean matches(@Nullable SurgicalSubject subject, int observedCubeCount) {
			if (subject == null)
				return false;
			if (!seams.equals(subject.seams())
				|| !offsets.equals(subject.componentOffsetsForRender())
				|| !rotations.equals(subject.componentRotationsForRender()))
				return false;
			List<SurgicalGlueJoint> subjectJoints = subject.glueJoints();
			if (glueJoints.size() != subjectJoints.size() || !glueJoints.containsAll(subjectJoints))
				return false;
			List<SurgicalCombination> subjectCombinations = subject.combinations();
			if (combinations.size() != subjectCombinations.size())
				return false;
			for (SurgicalCombination combination : subjectCombinations) {
				List<SurgicalCombination.Member> members = combinations.get(combination.id());
				if (members == null || !members.equals(combination.members()))
					return false;
			}
			return subject.presentCubesEqual(presentCubes) && subject.cutSeamsEqual(cutSeams);
		}

		private static SubjectGeometryState capture(SurgicalSubject subject, int observedCubeCount) {
			Map<UUID, List<SurgicalCombination.Member>> combinations = new HashMap<>();
			for (SurgicalCombination combination : subject.combinations())
				combinations.put(combination.id(), List.copyOf(combination.members()));
			return new SubjectGeometryState(subject.presentCubes(), subject.seams(),
				subject.cutSeamsForRender(), subject.componentOffsetsForRender(),
				subject.componentRotationsForRender(), Set.copyOf(subject.glueJoints()), combinations);
		}
	}

	record OwnerHandoffKey(BlockPos tablePos, int tableRevision) {
		OwnerHandoffKey {
			tablePos = tablePos.immutable();
		}
	}
}
