package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The canonical corner block entity for all surgical subjects on one connected table plane. */
public class SurgicalTableBlockEntity extends SmartBlockEntity {
	public static final int MAX_SUBJECTS = SurgicalTablePlane.MAX_TILES * SurgicalTableLayout.SLOTS_PER_TILE;
	private static final String SUBJECTS_TAG = SurgicalTableSubjectSync.SUBJECTS_TAG;
	private static final String NEXT_SUBJECT_ID_TAG = "NextSubjectId";
	private static final String FORMAT_VERSION_TAG = "SurgicalFormatVersion";
	private static final int FORMAT_VERSION = 3;
	private static final int CLIENT_PLANE_CACHE_TICKS = 5;

	final List<SurgicalSubject> subjects = new ArrayList<>();
	private final List<SurgicalSubject> subjectsView = Collections.unmodifiableList(subjects);
	private final Map<Integer, SurgicalSubject> subjectsById = new HashMap<>();
	final Map<UUID, SurgicalSubject> subjectsByPersistentId = new HashMap<>();
	private int nextSubjectId;
	private int clientDataRevision;
	private boolean pendingSavedSubjectNormalization;
	private boolean pendingReleaseMigrationSave;
	@Nullable
	AABB clientRenderBounds;
	@Nullable
	private SurgicalTablePlane.Plane clientPlane;
	@Nullable
	private AABB clientPlaneBounds;
	private boolean clientProjectsSourceGeometry;
	private long clientPlaneCacheUntil = Long.MIN_VALUE;
	private int clientPlaneLayout = -1;
	private int clientPlanePower = -1;
	@Nullable
	SurgicalConnectionGraph<UUID> cachedConnectionGraph;
	boolean cachedConnectionValid;
	Map<Integer, Set<Integer>> cachedConnectedSubjectGroups = Map.of();
	boolean cachedConnectedSubjectGroupsValid;
	@Nullable
	private SurgicalTablePlane.Plane serverPlane;
	private int serverPlaneLayout = -1;
	private int consolidatedPlaneLayout = -1;
	private int consolidatedPlaneTiles = -1;
	@Nullable
	SurgicalConnectionGraph<UUID> variantConnectionGraph;
	Set<SurgicalGlueJoint> variantConnectionExcluded = Set.of();
	Map<UUID, BitSet> variantConnectionOverrides = Map.of();
	boolean variantConnectionCombinations;
	boolean variantConnectionValid;
	/** Bumped by {@link #invalidateTableLayout()}; see {@link #getServerPlane()}. */
	private static int tableLayoutRevision;
	/** Bumped when a table tile's local redstone input changes. */
	private static int tablePowerRevision;

	public SurgicalTableBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SURGICAL_TABLE.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	/**
	 * A loading tile can reshape every cached plane in two ways: it may carry subjects that belong to
	 * the controller, and it may complete a surface that {@link SurgicalTablePlane#scan} previously
	 * stopped short of, because that scan drops neighbours in unloaded chunks without reporting the
	 * result as incomplete. Cached planes outlive a tick, so both have to invalidate them.
	 */
	@Override
	public void initialize() {
		invalidateTableLayout();
		if (pendingSavedSubjectNormalization)
			normalizeSavedSubjects();
		super.initialize();
		SurgicalTableSupportManager.track(this);
	}

	@Override
	public void onChunkUnloaded() {
		SurgicalTableSupportManager.untrack(this);
		super.onChunkUnloaded();
		invalidateTableLayout();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level == null || level.isClientSide || isRemoved() || !hasSubjects())
			return;
		SurgicalTablePlane.Plane plane = getServerPlane();
		if (plane.valid())
			consolidatePlane(level, plane);
	}

	public boolean hasSubjects() {
		return !subjects.isEmpty();
	}

	public boolean hasSubject(int subjectId) {
		return subjectsById.containsKey(subjectId);
	}

	public List<SurgicalSubject> getSubjects() {
		return subjectsView;
	}

	/**
	 * Client render/input code asks for the same connected plane several times per frame. A short
	 * cache keeps topology changes responsive while collapsing those BFS scans to at most four per
	 * second for each actively used controller.
	 */
	@Nullable
	public SurgicalTablePlane.Plane getClientPlane() {
		if (level == null)
			return null;
		if (!level.isClientSide)
			return getServerPlane();
		long now = level.getGameTime();
		if (clientPlane == null || now >= clientPlaneCacheUntil || clientPlaneLayout != tableLayoutRevision) {
			SurgicalTablePlane.Plane scanned = SurgicalTablePlane.scan(level, worldPosition);
			// An unchanged surface keeps its instance. Preview planning reuses its result while the
			// work area is the same object, so a periodic rescan must not look like a new table.
			if (clientPlane == null || clientPlane.complete() != scanned.complete()
				|| !clientPlane.tiles().equals(scanned.tiles()))
				clientPlane = scanned;
			clientPlaneCacheUntil = now + CLIENT_PLANE_CACHE_TICKS;
			clientPlaneLayout = tableLayoutRevision;
			int minX = worldPosition.getX();
			int minZ = worldPosition.getZ();
			int maxX = minX;
			int maxZ = minZ;
			for (BlockPos tablePos : clientPlane.tiles()) {
				minX = Math.min(minX, tablePos.getX());
				minZ = Math.min(minZ, tablePos.getZ());
				maxX = Math.max(maxX, tablePos.getX());
				maxZ = Math.max(maxZ, tablePos.getZ());
			}
			int y = worldPosition.getY();
			clientPlaneBounds = new AABB(minX, y, minZ, maxX + 1, y + 1, maxZ + 1);
			// A topology refresh must also validate the aggregate in case a chunk packet bypassed the
			// normal block-state transition callback.
			clientPlanePower = -1;
		}
		if (clientPlanePower != tablePowerRevision) {
			clientProjectsSourceGeometry = false;
			// Fold local input states over the cached component, so a redstone transition never
			// performs another connectivity BFS.
			for (BlockPos tablePos : clientPlane.tiles())
				if (level.getBlockState(tablePos).getValue(SurgicalTableBlock.POWERED)) {
					clientProjectsSourceGeometry = true;
					break;
				}
			clientPlanePower = tablePowerRevision;
		}
		return clientPlane;
	}

	public boolean clientProjectsSourceGeometry() {
		getClientPlane();
		return clientProjectsSourceGeometry;
	}

	/**
	 * The server asks for the same plane repeatedly inside a single edit: once in the packet handler
	 * and again from every validator that needs the occupied footprints. Each scan is a BFS over the
	 * whole connected surface, so on a wide table that dominated the edit. The scan result is a pure
	 * function of the surrounding block layout and of which neighbouring chunks are loaded, so the
	 * cache is keyed on {@link #tableLayoutRevision} alone; that counter is bumped on block
	 * placement and removal and, because {@link SurgicalTablePlane#scan} silently stops at an
	 * unloaded neighbour, on table block entity load and unload as well.
	 */
	private SurgicalTablePlane.Plane getServerPlane() {
		if (serverPlane == null || serverPlaneLayout != tableLayoutRevision) {
			serverPlane = SurgicalTablePlane.scan(level, worldPosition);
			serverPlaneLayout = tableLayoutRevision;
		}
		return serverPlane;
	}

	/**
	 * Invalidates every cached plane. Called when a surgical table block is placed or broken, which is
	 * the only thing that can reshape a connected surface.
	 */
	public static void invalidateTableLayout() {
		tableLayoutRevision++;
	}

	public static void invalidateTablePower() {
		tablePowerRevision++;
	}

	public int clientDataRevision() {
		return clientDataRevision;
	}

	@Nullable
	public SurgicalSubject getSubject(int subjectId) {
		return subjectsById.get(subjectId);
	}

	@Nullable
	public SurgicalSubject getSubjectByPersistentId(UUID persistentId) {
		return subjectsByPersistentId.get(persistentId);
	}

	void addSubject(SurgicalSubject subject) {
		subject.setTable(this);
		subjects.add(subject);
		subjectsById.put(subject.id(), subject);
		subjectsByPersistentId.put(subject.persistentId(), subject);
		SurgicalTableConnections.invalidateConnections(this);
		SurgicalTableSupportManager.track(this);
	}

	void addSubjects(List<SurgicalSubject> added) {
		for (SurgicalSubject subject : added)
			addSubject(subject);
	}

	void removeSubject(SurgicalSubject subject) {
		subject.setTable(null);
		subjects.remove(subject);
		subjectsById.remove(subject.id(), subject);
		subjectsByPersistentId.remove(subject.persistentId(), subject);
		SurgicalTableConnections.invalidateConnections(this);
		SurgicalTableSupportManager.track(this);
	}

	private void clearSubjects() {
		for (SurgicalSubject subject : subjects)
			subject.setTable(null);
		subjects.clear();
		subjectsById.clear();
		subjectsByPersistentId.clear();
		SurgicalTableConnections.invalidateConnections(this);
		SurgicalTableSupportManager.track(this);
	}

	private void normalizeSavedSubjects() {
		// Initial NBT decoding may precede setLevel; only the server assigns replacement IDs.
		if (level == null)
			return;
		pendingSavedSubjectNormalization = false;
		boolean migrated = pendingReleaseMigrationSave && !level.isClientSide;
		pendingReleaseMigrationSave = false;
		boolean repaired = normalizeCombinations(!level.isClientSide);
		normalizeLimbJoints();
		if (repaired || migrated) {
			setChanged();
			sendData();
		}
	}

	private boolean normalizeCombinations(boolean repairConflicts) {
		Map<UUID, SurgicalCombination> byId = new HashMap<>();
		Set<UUID> conflicting = new HashSet<>();
		Map<UUID, Map<List<SurgicalCombination.Member>, SurgicalCombination>> variants = new HashMap<>();
		for (SurgicalSubject subject : subjects)
			for (SurgicalCombination combination : subject.combinations()) {
				SurgicalCombination previous = byId.putIfAbsent(combination.id(), combination);
				if (previous != null && !previous.members().equals(combination.members())) {
					conflicting.add(combination.id());
					if (repairConflicts) {
						Map<List<SurgicalCombination.Member>, SurgicalCombination> copies =
							variants.computeIfAbsent(combination.id(), ignored -> new HashMap<>());
						copies.putIfAbsent(previous.members(), previous);
						copies.putIfAbsent(combination.members(), combination);
					}
				}
			}
		boolean repaired = false;
		// Only saved, disjoint copies can be recovered. Repeated storage of the same group
		// on several subjects is one variant; overlapping or dangling variants remain invalid.
		for (Map<List<SurgicalCombination.Member>, SurgicalCombination> copies : variants.values()) {
			Set<SurgicalCombination.Member> members = new HashSet<>();
			boolean complete = true;
			for (SurgicalCombination copy : copies.values())
				for (SurgicalCombination.Member member : copy.members()) {
					SurgicalSubject owner = getSubjectByPersistentId(member.subjectKey());
					if (owner == null || !owner.validPresentCube(member.cubeId()) || !members.add(member))
						complete = false;
				}
			if (!complete)
				continue;
			for (SurgicalCombination copy : copies.values()) {
				UUID id;
				do {
					id = UUID.randomUUID();
				} while (byId.containsKey(id));
				byId.put(id, SurgicalCombination.create(id, copy.members()));
			}
			repaired = true;
		}
		List<SurgicalCombination> valid = new ArrayList<>();
		Set<SurgicalCombination.Member> occupied = new HashSet<>();
		for (SurgicalCombination combination : byId.values()) {
			if (conflicting.contains(combination.id()))
				continue;
			boolean complete = true;
			for (SurgicalCombination.Member member : combination.members()) {
				SurgicalSubject subject = getSubjectByPersistentId(member.subjectKey());
				if (subject == null || !subject.validPresentCube(member.cubeId()) || occupied.contains(member)) {
					complete = false;
					break;
				}
			}
			if (!complete)
				continue;
			occupied.addAll(combination.members());
			valid.add(combination);
		}
		for (SurgicalSubject subject : subjects)
			subject.replaceCombinations(List.of());
		for (SurgicalCombination combination : valid)
			SurgicalGlueOperations.attachCombination(this, combination);
		return repaired;
	}

	/** Reported for every prepared subject on every frame, so an already covered box costs nothing. */
	public void includeClientRenderBounds(AABB bounds) {
		AABB current = clientRenderBounds;
		if (current == null)
			clientRenderBounds = bounds;
		else if (bounds.minX < current.minX || bounds.minY < current.minY || bounds.minZ < current.minZ
			|| bounds.maxX > current.maxX || bounds.maxY > current.maxY || bounds.maxZ > current.maxZ)
			clientRenderBounds = current.minmax(bounds);
	}

	/**
	 * Rebuilds the limb-joint index from whatever survived the last edit. Cutting a limb away or
	 * packing it into a box leaves dangling endpoints behind. Joint slot limits are intentionally not
	 * applied here: new excess joints are rejected by {@link #attachLimb}, while existing data remains
	 * packable.
	 */
	private void normalizeLimbJoints() {
		Set<SurgicalLimbJoint> seen = new java.util.LinkedHashSet<>();
		for (SurgicalSubject subject : subjects)
			seen.addAll(subject.limbJoints());
		if (seen.isEmpty()) {
			for (SurgicalSubject subject : subjects)
				if (!subject.limbJoints().isEmpty())
					subject.replaceLimbJoints(List.of());
			return;
		}
		List<SurgicalLimbJoint> valid = new ArrayList<>();
		Set<SurgicalGlueJoint.Endpoint> children = new HashSet<>();
		// Reuse already discovered bodies before falling back to another traversal. This method runs on
		// every edit and again on every sync, once per limb joint on the whole table.
		List<ComponentGroup> knownBodies = new ArrayList<>();
		for (SurgicalLimbJoint joint : seen) {
			SurgicalSubject child = getSubjectByPersistentId(joint.child().subjectKey());
			SurgicalSubject parent = getSubjectByPersistentId(joint.parent().subjectKey());
			if (child == null || parent == null || !child.validPresentCube(joint.child().cubeId())
				|| !parent.validPresentCube(joint.parent().cubeId())
				|| !children.add(joint.child()))
				continue;
			ComponentGroup body = null;
			for (ComponentGroup known : knownBodies)
				if (known.contains(joint.parent())) {
					body = known;
					break;
				}
			if (body == null) {
				body = SurgicalTableConnections.connectedGroup(this, parent, joint.parent().cubeId());
				knownBodies.add(body);
			}
			if (!body.contains(joint.child()))
				continue;
			valid.add(joint);
		}
		for (SurgicalSubject subject : subjects)
			subject.replaceLimbJoints(List.of());
		for (SurgicalLimbJoint joint : valid)
			attachLimbJoint(joint);
	}

	void attachLimbJoint(SurgicalLimbJoint joint) {
		SurgicalSubject child = getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parent = getSubjectByPersistentId(joint.parent().subjectKey());
		if (child == null || parent == null)
			return;
		child.addLimbJoint(joint);
		if (parent != child)
			parent.addLimbJoint(joint);
	}

	Set<SurgicalLimbJoint> allLimbJoints() {
		Set<SurgicalLimbJoint> joints = new java.util.LinkedHashSet<>();
		for (SurgicalSubject subject : subjects)
			joints.addAll(subject.limbJoints());
		return joints;
	}

	Set<SurgicalLimbJoint> limbsWithin(ComponentGroup group) {
		Set<SurgicalLimbJoint> joints = new java.util.LinkedHashSet<>();
		for (SurgicalLimbJoint joint : allLimbJoints())
			if (group.contains(joint.child()) && group.contains(joint.parent()))
				joints.add(joint);
		return joints;
	}

	/** The limb joints already installed on the body that owns {@code cubeId}. */
	public List<SurgicalLimbJoint> bodyLimbJoints(int subjectId, int cubeId) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.validPresentCube(cubeId))
			return List.of();
		return List.copyOf(limbsWithin(SurgicalTableConnections.connectedGroup(this, subject, cubeId)));
	}

	/** All anatomical joints installed on this table, without the per-subject storage duplicates. */
	public List<SurgicalLimbJoint> limbJoints() {
		return List.copyOf(allLimbJoints());
	}

	/** Creature containers accepted by both placement previews and server validation. */
	public static boolean isPlacementContainer(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem || stack.getItem() instanceof CaptureNetItem;
	}

	public SurgicalTablePlacementResult tryPlaceSubject(ItemStack box, SurgicalTablePlane.Plane plane,
		Direction placementFacing,
		SurgicalLayPose layPose, double placedOriginOffsetX, double placedOriginOffsetZ,
		SurgicalTableLayout.Proposal proposal,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		BionicAnatomySnapshot anatomy,
		List<SurgicalTableLayout.Footprint> componentFootprints,
		List<SurgicalTableLayout.Proposal> sourceLayouts) {
		return tryPlaceSubject(box, plane, placementFacing, layPose, placedOriginOffsetX, placedOriginOffsetZ, proposal,
			observedCubeCount, observedSeams, anatomy, componentFootprints, sourceLayouts, List.of());
	}

	public SurgicalTablePlacementResult tryPlaceSubject(ItemStack box, SurgicalTablePlane.Plane plane,
		Direction placementFacing,
		SurgicalLayPose layPose, double placedOriginOffsetX, double placedOriginOffsetZ,
		SurgicalTableLayout.Proposal proposal,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		BionicAnatomySnapshot anatomy,
		List<SurgicalTableLayout.Footprint> componentFootprints,
		List<SurgicalTableLayout.Proposal> sourceLayouts, List<DevouredDonorTopology> preyModels) {
		return SurgicalPlacementOperations.tryPlaceSubject(this, box, plane, placementFacing, layPose, placedOriginOffsetX, placedOriginOffsetZ, proposal, observedCubeCount, observedSeams, anatomy, componentFootprints, sourceLayouts, preyModels);
	}

	/** Performs a read-only validity check once the source chunk is available. */
	public static boolean isTemporaryMoveValid(ServerLevel sourceLevel,
		SurgicalKitItem.TemporaryMove move) {
		return SurgicalPlacementOperations.isTemporaryMoveValid(sourceLevel, move);
	}

	/**
	 * Removes the complete server-authoritative connectivity group selected with a shovel.
	 *
	 * <p>When the client's live model no longer matches the saved topology its cube ids may name
	 * different parts, so per-cube selection is dropped and every component of the subject is
	 * removed instead, closed over the connection graph. That degraded path reads only server-side
	 * state, which keeps a body with a changed model recoverable rather than stranded on the table.
	 * The reported volume is still used for the slime yield: the degraded group covers at least the
	 * group the client measured, so an untrusted measurement can only under-reward.</p>
	 */
	public boolean shovelConnectedGroup(Player player, ItemStack shovel, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		double volume, Vec3 dropPosition) {
		return SurgicalPlacementOperations.shovelConnectedGroup(this, player, shovel, hand, subjectId, cubeId, observedCubeCount, observedSeams, volume, dropPosition);
	}

	/**
	 * Removes every complete connectivity group whose saved horizontal projection has positive-area
	 * overlap with {@code tilePos}. Selection uses only server-owned footprints and topology, so it
	 * also reaches subjects whose model is unavailable, whose model no longer matches its saved cube
	 * ids, including subjects whose placement is represented by one whole-model envelope footprint.
	 */
	public boolean shovelIntersectingTile(Player player, ItemStack shovel, InteractionHand hand,
		BlockPos tilePos, SurgicalTablePlane.Plane plane, double volume) {
		return SurgicalPlacementOperations.shovelIntersectingTile(this, player, shovel, hand, tilePos, plane, volume);
	}

	public boolean cutSeam(Player player, ItemStack shears, InteractionHand hand, int subjectId, int seamId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, double moveX, double moveZ) {
		return SurgicalCutOperations.cutSeam(this, player, shears, hand, subjectId, seamId, observedCubeCount, observedSeams, plane, proposal, moveX, moveZ);
	}

	public boolean cutGlueJoint(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int glueJointId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, double moveX, double moveZ) {
		return SurgicalCutOperations.cutGlueJoint(this, player, shears, hand, subjectId, glueJointId, observedCubeCount, observedSeams, plane, moveX, moveZ);
	}

	public boolean cutCubeConnections(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal) {
		return SurgicalCutOperations.cutCubeConnections(this, player, shears, hand, subjectId, cubeId, observedCubeCount, observedSeams, plane, proposal);
	}

	/**
	 * Server-authoritative batch cut for layouts that may move retained glue/combination groups across
	 * several subjects. The client supplies only one delta per deterministically ordered post-cut
	 * group; group membership is always rebuilt from server topology before validation or mutation.
	 */
	public boolean cutCubeConnections(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		return SurgicalCutOperations.cutCubeConnections(this, player, shears, hand, subjectId, cubeId, observedCubeCount, observedSeams, plane, proposal, groupDeltas);
	}

	public boolean canApplyBatchCut(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		return SurgicalCutOperations.canApplyBatchCut(this, subjectId, cubeId, observedCubeCount, observedSeams, plane, proposal, groupDeltas);
	}

	record TaggedFootprint(int group, SurgicalTableLayout.Footprint footprint) {}

	/**
	 * Group membership indexed by cube id. Resolving it by scanning {@code cut.groups} cost one
	 * UUID-keyed map probe per group, and the callers ask once per cube and once per footprint, so
	 * the scan was quadratic in two client-supplied quantities.
	 */
	static final class BatchGroupIndex {
		private final Map<UUID, int[]> cubeGroups = new HashMap<>();

		BatchGroupIndex(List<ComponentGroup> groups) {
			for (int groupId = 0; groupId < groups.size(); groupId++)
				for (Map.Entry<UUID, BitSet> entry : groups.get(groupId).components.entrySet())
					index(entry.getKey(), entry.getValue(), groupId);
		}

		/** Lowest group id wins, matching the first-match order of the scan this replaces. */
		private void index(UUID subjectKey, BitSet cubes, int groupId) {
			if (cubes == null || cubes.isEmpty())
				return;
			int required = cubes.length();
			int[] index = cubeGroups.get(subjectKey);
			if (index == null) {
				index = new int[required];
				Arrays.fill(index, -1);
			} else if (index.length < required) {
				int previous = index.length;
				index = Arrays.copyOf(index, required);
				Arrays.fill(index, previous, required, -1);
			}
			cubeGroups.put(subjectKey, index);
			for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
				if (index[cube] < 0)
					index[cube] = groupId;
		}

		int groupOf(UUID subjectKey, int cubeId) {
			if (cubeId < 0)
				return -1;
			int[] index = cubeGroups.get(subjectKey);
			return index == null || cubeId >= index.length ? -1 : index[cubeId];
		}

		int wholeSubjectGroupOf(List<ComponentGroup> groups, SurgicalSubject subject) {
			for (int groupId = 0; groupId < groups.size(); groupId++) {
				BitSet cubes = groups.get(groupId).components.get(subject.persistentId());
				if (cubes != null && cubes.equals(subject.presentCubes))
					return groupId;
			}
			return -1;
		}
	}

	public boolean packComponent(Player player, ItemStack boxes, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		return SurgicalPackingOperations.packComponent(this, player, boxes, subjectId, cubeId, observedCubeCount, observedSeams, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, packingAlignment);
	}

	/**
	 * Installs one anatomical joint. Click order selects the rotating child part and parent part;
	 * when either is a honey combination, the stored endpoints are narrowed to the two cubes that
	 * actually share the seam or glue joint between those parts.
	 */
	public boolean attachLimb(Player player, ItemStack jointItem, InteractionHand hand,
		SurgicalLimbType type, int childSubjectId, int childCubeId, int parentSubjectId, int parentCubeId,
		int childCubeCount, List<SurgicalAssembly.Seam> childSeams, int parentCubeCount,
		List<SurgicalAssembly.Seam> parentSeams) {
		return SurgicalLimbOperations.attachLimb(this, player, jointItem, hand, type, childSubjectId, childCubeId, parentSubjectId, parentCubeId, childCubeCount, childSeams, parentCubeCount, parentSeams);
	}

	/** Captures a placement snapshot without removing the source component from its table. */
	public boolean captureTemporaryComponent(Player player, ItemStack kit, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		return SurgicalPackingOperations.captureTemporaryComponent(this, player, kit, subjectId, cubeId, observedCubeCount, observedSeams, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, packingAlignment);
	}

	/** Stores the connected component as an independent snapshot without changing its source. */
	public boolean captureCopiedComponent(Player player, ItemStack kit, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds, double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		return SurgicalPackingOperations.captureCopiedComponent(this, player, kit, subjectId, cubeId, observedCubeCount, observedSeams, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, packingAlignment);
	}

	/** Removes one server-authoritative joint with shears and returns its item. */
	public boolean detachLimb(Player player, SurgicalLimbJoint joint) {
		return SurgicalLimbOperations.detachLimb(this, player, joint);
	}

	/** Client-safe preview check used to hide a joint's invalid second target before it is clicked. */
	public boolean canConnectLimbTargets(int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		return SurgicalLimbOperations.canConnectLimbTargets(this, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId);
	}

	record DirectConnection(SurgicalGlueJoint.Endpoint child,
		SurgicalGlueJoint.Endpoint parent) {}

	record TableLimbTopology(Set<SurgicalLimbJoint> effective,
		Map<SurgicalGlueJoint.Endpoint, Integer> components,
		Map<Integer, List<TableLimbAttachment>> attachments) {
		static final TableLimbTopology EMPTY =
			new TableLimbTopology(Set.of(), Map.of(), Map.of());
	}

	record TableLimbAttachment(SurgicalLimbJoint limb, boolean child) {}

	public boolean combineConnected(Player player, ItemStack honeyBottle, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalGlueOperations.combineConnected(this, player, honeyBottle, hand, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	public boolean breakCombination(Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalGlueOperations.breakCombination(this, player, shears, hand, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	public boolean detachCombination(Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalGlueOperations.detachCombination(this, player, shears, hand, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	/**
	 * Adds an in-place glue edge between two physically touching cubes. The new seam may close a cycle in
	 * the model's native/glue graph, but it must not duplicate an existing native seam or glue joint.
	 */
	public boolean addSlimeSeam(Player player, ItemStack slimeBall, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int firstObservedCubeCount,
		List<SurgicalAssembly.Seam> firstObservedSeams,
		int secondSubjectId, int secondCubeId, int secondObservedCubeCount,
		List<SurgicalAssembly.Seam> secondObservedSeams) {
		return SurgicalGlueOperations.addSlimeSeam(this, player, slimeBall, hand, firstSubjectId, firstCubeId, firstObservedCubeCount, firstObservedSeams, secondSubjectId, secondCubeId, secondObservedCubeCount, secondObservedSeams);
	}

	/** Client-safe structural half of slime-seam validation; current cube geometry is checked client-side. */
	public boolean canAddSlimeSeamTargets(int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		return SurgicalGlueOperations.canAddSlimeSeamTargets(this, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId);
	}

	public boolean glueComponents(Player player, ItemStack glue, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		SurgicalLayPose targetPose, List<SurgicalTableGluePacket.Move> moves,
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves, SurgicalGlueTransform replayTransform,
		SurgicalGlueContact anchorContact,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		return SurgicalGlueOperations.glueComponents(this, player, glue, hand, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, replayTransform, anchorContact, plane, firstLayout, secondLayout);
	}

	/**
	 * Applies the same movement and joint transaction as strong glue after verifying the chosen
	 * reference joint. A normal click requires its anchor to belong to the selected honey combination;
	 * Ctrl mirrors around that one anchor cube directly. The selected centre plane is already expressed
	 * by the mirrored endpoint and replay transform. The wand itself is neither consumed nor damaged.
	 */
	public boolean symmetryGlueComponents(Player player, ItemStack wand, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		int referenceSubjectId, int referenceCubeId, UUID referenceAnchorSubjectKey,
		int referenceAnchorCubeId, boolean singleCube, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalGlueTransform replayTransform, SurgicalGlueContact anchorContact,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		return SurgicalGlueOperations.symmetryGlueComponents(this, player, wand, hand, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId, referenceSubjectId, referenceCubeId, referenceAnchorSubjectKey, referenceAnchorCubeId, singleCube, targetPose, moves, anchorMoves, replayTransform, anchorContact, plane, firstLayout, secondLayout);
	}

	/**
	 * Builds the immutable assembly that packing would create, without changing table state.
	 * The client uses this exact source ordering while baking generated combat geometry.
	 */
	@Nullable
	public SurgicalAssembly previewPackedAssembly(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalGlueOperations.previewPackedAssembly(this, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	/** Assembly and membership from one connectivity traversal, for indexing a whole component's preview. */
	@Nullable
	public PackedAssemblyPreview previewPackedComponent(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalPackingOperations.previewPackedComponent(this, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	public record PackedAssemblyPreview(SurgicalAssembly assembly, Map<Integer, BitSet> components) {}


	public boolean canApplyGlueCut(int subjectId, int glueJointId, double moveX, double moveZ,
		SurgicalTablePlane.Plane plane) {
		return SurgicalCutOperations.canApplyGlueCut(this, subjectId, glueJointId, moveX, moveZ, plane);
	}

	public boolean canApplySeamCut(int subjectId, int seamId, SurgicalTableLayout.Proposal proposal,
		double moveX, double moveZ, SurgicalTablePlane.Plane plane) {
		return SurgicalCutOperations.canApplySeamCut(this, subjectId, seamId, proposal, moveX, moveZ, plane);
	}

	/** Uses the exact same non-mutating acceptance path as {@link #glueComponents}. */
	public boolean canGlueComponents(ItemStack glue, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalGlueTransform replayTransform,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		return SurgicalGlueOperations.canGlueComponents(this, glue, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, replayTransform, plane, firstLayout, secondLayout);
	}

	public boolean canApplyComponentLayout(int subjectId, BitSet cutSeams,
		SurgicalTableLayout.Proposal proposal, SurgicalTablePlane.Plane plane) {
		return SurgicalGlueOperations.canApplyComponentLayout(this, subjectId, cutSeams, proposal, plane);
	}

	record ValidatedGlueMove(Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations,
		SurgicalTableLayout.Proposal layout) {}

	record ValidatedGluePlan(SurgicalSubject first, SurgicalSubject second,
		ComponentGroup moving, ComponentGroup anchored,
		Map<UUID, ValidatedGlueMove> moves, Map<UUID, Map<Integer, Vec3>> anchorMoves) {}

	record ExtractedSubject(BitSet cubes, SurgicalSubject subject) {}

	/** Validates the complete snapshot before calculating any wrench mutation. */
	public SurgicalWrenchRotation.Plan planWrenchRotation(UUID subjectId, int cubeId,
		SurgicalCubeRotation groupRotation, SurgicalCubeRotation selectedRotation,
		Map<Integer, BitSet> expectedGroup, List<SurgicalWrenchRotation.Body> bodies, SurgicalTablePlane.Plane plane,
		Vec3 groupTranslation, Vec3 selectedTranslation) {
		return SurgicalGlueOperations.planWrenchRotation(this, subjectId, cubeId, groupRotation, selectedRotation, expectedGroup, bodies, plane, groupTranslation, selectedTranslation);
	}

	/** Commits every subject together, only after the complete plan has passed validation. */
	void applyWrenchRotation(SurgicalWrenchRotation.Plan plan) {
		SurgicalGlueOperations.applyWrenchRotation(this, plan);
	}

	record WrenchDisconnections(Map<Integer, BitSet> cuts, Set<SurgicalGlueJoint> glue,
		Set<SurgicalLimbJoint> limbs, Set<SurgicalCombination> combinations,
		List<SurgicalCombination> remainingCombinations, Set<SurgicalGlueJoint> addedGlue) {}

	/** Validates an existing endpoint snapshot; destination clearance is checked by validateGlueMoves. */
	public boolean validateGlueLayout(SurgicalSubject subject, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal) {
		return SurgicalGlueOperations.validateGlueLayout(this, subject, plane, proposal);
	}

	/** All cubes joined through the unified native-seam/glue/combination graph, keyed by subject id. */
	public Map<Integer, BitSet> connectedComponents(int subjectId, int cubeId) {
		return SurgicalTableConnections.connectedComponents(this, subjectId, cubeId);
	}

	/** Client-side topology-aware variant that rejects a model differing from the stored cube ids. */
	public Map<Integer, BitSet> connectedComponents(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalTableConnections.connectedComponents(this, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	/** The clicked cube and all cubes joined to it by one native or explicit connection edge. */
	public Map<Integer, BitSet> directConnections(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalTableConnections.directConnections(this, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	/** Connectivity after Ctrl-shears removes every native and glue edge touching {@code cutCube}. */
	public Map<Integer, BitSet> connectedComponentsAfterCuttingCube(int subjectId, int startCube, int cutCube,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalCutOperations.connectedComponentsAfterCuttingCube(this, subjectId, startCube, cutCube, observedCubeCount, observedSeams);
	}

	/** Complete post-cut groups for the Ctrl-shears operation, largest and stable remainder first. */
	@Nullable
	public BatchCutPlan batchCutPlan(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		return SurgicalCutOperations.batchCutPlan(this, subjectId, cubeId, observedCubeCount, observedSeams);
	}

	@Nullable
	public SeamCutPlan seamCutPlan(int subjectId, int seamId) {
		return SurgicalCutOperations.seamCutPlan(this, subjectId, seamId);
	}

	@Nullable
	public GlueCutPlan glueCutPlan(int subjectId, int glueJointId) {
		return SurgicalCutOperations.glueCutPlan(this, subjectId, glueJointId);
	}

	Map<Integer, BitSet> componentsBySubjectId(ComponentGroup group) {
		return SurgicalCutOperations.componentsBySubjectId(this, group);
	}

	/** All topology writes and subject ownership changes invalidate the same derived caches. */
	void invalidateConnections() {
		SurgicalTableConnections.invalidateConnections(this);
	}

	@Nullable
	public SurgicalCombination combinationContaining(int subjectId, int cubeId) {
		return SurgicalTableConnections.combinationContaining(this, subjectId, cubeId);
	}

	public boolean isInternalCombinationJoint(SurgicalGlueJoint joint) {
		return SurgicalTableConnections.isInternalCombinationJoint(this, joint);
	}

	public boolean isInternalCombinationSeam(int subjectId, SurgicalAssembly.Seam seam) {
		return SurgicalTableConnections.isInternalCombinationSeam(this, subjectId, seam);
	}

	public List<SurgicalGlueJoint> externalCombinationJoints(SurgicalCombination combination) {
		return SurgicalTableConnections.externalCombinationJoints(this, combination);
	}

	/** Subject ids that currently form one logical editing group through glue or combinations. */
	public Set<Integer> connectedSubjectIds(int subjectId) {
		return SurgicalTableConnections.connectedSubjectIds(this, subjectId);
	}

	/**
	 * Projects cube-level links onto subject ownership and closes that graph transitively. A subject may
	 * own several disconnected native components, so reaching any one of them also has to pull in links
	 * stored on its other components; otherwise grounding can collect a link whose endpoint body was
	 * omitted. Topology edits invalidate this projection with the connection graph; lookups within
	 * each group share the same immutable member set.
	 */
	public Map<Integer, Set<Integer>> connectedSubjectGroups() {
		return SurgicalTableConnections.connectedSubjectGroups(this);
	}

	/**
	 * Cubes selected per subject. The map and its sets are adopted rather than copied: every caller
	 * hands over either a graph component's freshly materialized members or a map built for this group.
	 */
	record ComponentGroup(Map<UUID, BitSet> components) {
		boolean contains(SurgicalGlueJoint.Endpoint endpoint) {
			BitSet cubes = components.get(endpoint.subjectKey());
			return cubes != null && cubes.get(endpoint.cubeId());
		}

		boolean contains(SurgicalCombination.Member member) {
			BitSet cubes = components.get(member.subjectKey());
			return cubes != null && cubes.get(member.cubeId());
		}

		boolean intersects(ComponentGroup other) {
			for (Map.Entry<UUID, BitSet> entry : components.entrySet()) {
				BitSet otherCubes = other.components.get(entry.getKey());
				if (otherCubes != null && entry.getValue().intersects(otherCubes))
					return true;
			}
			return false;
		}
	}

	record TemporaryMoveSource(SurgicalTableBlockEntity sourceTable, ComponentGroup group,
		int freedSubjects) {}

	record GlueCutState(SurgicalGlueJoint joint, ComponentGroup moving, boolean separates) {}
	record SeamCutState(BitSet proposedCuts, ComponentGroup moving, boolean separates) {}

	// The plan records below are built only from freshly computed, exclusively owned collections
	// (see componentsBySubjectId and the cut-state builders), so they adopt them as given.
	public record SeamCutPlan(Map<Integer, BitSet> movingComponents, BitSet proposedCuts,
		boolean separates) {}

	public record GlueCutPlan(SurgicalGlueJoint joint, Map<Integer, BitSet> movingComponents,
		boolean separates) {}

	public record BatchCutPlan(BitSet proposedCuts, List<Map<Integer, BitSet>> groups) {}

	record BatchCutState(BitSet proposedCuts, List<Integer> cutSeamIds,
		Set<SurgicalGlueJoint> glueCuts, List<ComponentGroup> groups, int cutCount) {}

	@Nullable
	List<SurgicalTableLayout.Footprint> occupiedForValidation(SurgicalTablePlane.Plane plane,
		int excludedSubjectId) {
		return level == null ? null : SurgicalTablePlane.occupiedFootprints(level, plane, excludedSubjectId);
	}

	@Nullable
	List<SurgicalTableLayout.Footprint> occupiedForValidation(SurgicalTablePlane.Plane plane,
		Set<Integer> excludedSubjectIds) {
		return level == null ? null : SurgicalTablePlane.occupiedFootprints(level, plane, excludedSubjectIds);
	}

	int allocateSubjectId() {
		while (nextSubjectId < 0 || getSubject(nextSubjectId) != null)
			nextSubjectId++;
		return nextSubjectId++;
	}

	void adoptSubjects(BlockPos previousController, List<SurgicalSubject> migrated) {
		for (SurgicalSubject subject : migrated) {
			subject.rebase(previousController, worldPosition);
			if (subject.id() < 0 || getSubject(subject.id()) != null)
				subject.setId(allocateSubjectId());
			else
				nextSubjectId = Math.max(nextSubjectId, subject.id() + 1);
			addSubject(subject);
		}
		clientRenderBounds = null;
	}

	List<SurgicalSubject> detachSubjects() {
		List<SurgicalSubject> detached = new ArrayList<>(subjects);
		clearSubjects();
		clientRenderBounds = null;
		return detached;
	}

	@Nullable
	public static SurgicalTableBlockEntity controller(Level level, SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || plane.source() == null)
			return null;
		if (!level.isClientSide)
			consolidatePlane(level, plane);
		return level.getBlockEntity(plane.source()) instanceof SurgicalTableBlockEntity table ? table : null;
	}

	public static void consolidatePlane(Level level, SurgicalTablePlane.Plane plane) {
		if (level.isClientSide || !plane.valid() || plane.source() == null
			|| !(level.getBlockEntity(plane.source()) instanceof SurgicalTableBlockEntity controller))
			return;
		// controller() runs this on every plane lookup, and one edit looks the plane up two or three
		// times; each pass is a block-entity fetch for every tile of the surface. Subjects can only
		// appear on a non-source tile when the plane's block layout changes or when a tile that
		// already holds them loads, and both bump the layout revision, so a result carried over from
		// an earlier tick still holds. The tile count is compared as well so a layout change that
		// happens to land on the same revision is not missed.
		if (controller.consolidatedPlaneLayout == tableLayoutRevision
			&& controller.consolidatedPlaneTiles == plane.tiles().size())
			return;
		boolean changed = false;
		for (BlockPos tile : plane.tiles()) {
			if (tile.equals(plane.source())
				|| !(level.getBlockEntity(tile) instanceof SurgicalTableBlockEntity other)
				|| !other.hasSubjects())
				continue;
			controller.adoptSubjects(tile, other.detachSubjects());
			other.setChangedAndSync();
			changed = true;
		}
		controller.consolidatedPlaneLayout = tableLayoutRevision;
		controller.consolidatedPlaneTiles = plane.tiles().size();
		if (changed)
			controller.setChangedAndSync();
	}

	/** Removes every complete connectivity component whose horizontal projection has no table support. */
	List<SurgicalTableSupportManager.ReleasedCube> releaseUnsupportedComponents() {
		if (level == null || level.isClientSide || subjects.isEmpty())
			return List.of();
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(this, null);
		if (graph == null) {
			List<SurgicalTableSupportManager.ReleasedCube> released = new ArrayList<>();
			for (SurgicalSubject subject : List.copyOf(subjects)) {
				if (subjectHasProjectionSupport(subject))
					continue;
				BitSet cubes = (BitSet) subject.presentCubes.clone();
				if (cubes.isEmpty())
					cubes.set(0);
				released.addAll(releasedCubes(subject, cubes));
				removeSubject(subject);
			}
			clientRenderBounds = null;
			return List.copyOf(released);
		}

		Map<UUID, BitSet> unsupported = new HashMap<>();
		List<SurgicalTableSupportManager.ReleasedCube> released = new ArrayList<>();
		for (SurgicalConnectionGraph.Component<UUID> component : graph.components()) {
			ComponentGroup group = new ComponentGroup(component.members());
			if (groupHasProjectionSupport(group))
				continue;
			group.components.forEach((subjectKey, cubes) ->
				unsupported.computeIfAbsent(subjectKey, ignored -> new BitSet()).or(cubes));
			for (SurgicalSubject subject : subjects) {
				BitSet cubes = group.components.get(subject.persistentId());
				if (cubes != null && !cubes.isEmpty())
					released.addAll(releasedCubes(subject, cubes));
			}
		}
		if (!unsupported.isEmpty())
			SurgicalPlacementOperations.removeGroup(this, new ComponentGroup(unsupported));
		return List.copyOf(released);
	}

	/** Fast reverse lookup used to find data whose owner sits on another disconnected table plane. */
	boolean projectionTouches(Set<Long> tableTiles) {
		if (tableTiles.isEmpty())
			return false;
		for (SurgicalSubject subject : subjects)
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (footprintTouches(footprint, (x, z) ->
					tableTiles.contains(BlockPos.asLong(x, worldPosition.getY(), z))))
					return true;
		return false;
	}

	private boolean groupHasProjectionSupport(ComponentGroup group) {
		for (SurgicalSubject subject : subjects) {
			BitSet cubes = group.components.get(subject.persistentId());
			if (cubes == null)
				continue;
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (subject.containsFootprint(cubes, footprint) && footprintHasTableSupport(footprint))
					return true;
		}
		return false;
	}

	private boolean subjectHasProjectionSupport(SurgicalSubject subject) {
		for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
			if (footprintHasTableSupport(footprint))
				return true;
		return false;
	}

	private boolean footprintHasTableSupport(SurgicalTableLayout.Footprint footprint) {
		return footprintTouches(footprint, (x, z) -> {
			BlockPos pos = new BlockPos(x, worldPosition.getY(), z);
			// An unloaded candidate is kept conservatively: no block can be destroyed there until it loads.
			if (!level.isLoaded(pos))
				return !isRemoved();
			return level.getBlockState(pos).getBlock() instanceof SurgicalTableBlock;
		});
	}

	private static boolean footprintTouches(SurgicalTableLayout.Footprint footprint, TilePredicate predicate) {
		if (footprint == null || !Double.isFinite(footprint.minX()) || !Double.isFinite(footprint.minZ())
			|| !Double.isFinite(footprint.maxX()) || !Double.isFinite(footprint.maxZ())
			|| footprint.maxX() <= footprint.minX() || footprint.maxZ() <= footprint.minZ())
			return false;
		int firstX = (int) Math.floor(footprint.minX());
		int lastX = (int) Math.ceil(footprint.maxX()) - 1;
		int firstZ = (int) Math.floor(footprint.minZ());
		int lastZ = (int) Math.ceil(footprint.maxZ()) - 1;
		if ((long) lastX - firstX > SurgicalTablePlane.MAX_TILES
			|| (long) lastZ - firstZ > SurgicalTablePlane.MAX_TILES)
			return false;
		for (int x = firstX; x <= lastX; x++) {
			double overlapX = Math.min(footprint.maxX(), x + 1.0d) - Math.max(footprint.minX(), x);
			if (overlapX <= 1.0e-9d)
				continue;
			for (int z = firstZ; z <= lastZ; z++) {
				double overlapZ = Math.min(footprint.maxZ(), z + 1.0d) - Math.max(footprint.minZ(), z);
				if (overlapZ > 1.0e-9d && predicate.test(x, z))
					return true;
			}
		}
		return false;
	}

	private List<SurgicalTableSupportManager.ReleasedCube> releasedCubes(SurgicalSubject subject,
		BitSet cubes) {
		List<SurgicalTableLayout.Footprint> footprints = subject.occupiedFootprints().stream()
			.filter(footprint -> subject.containsFootprint(cubes, footprint)).toList();
		double envelopeMinX = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::minX).min()
			.orElse(worldPosition.getX() + subject.originOffsetX());
		double envelopeMinZ = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::minZ).min()
			.orElse(worldPosition.getZ() + subject.originOffsetZ());
		double envelopeMaxX = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::maxX).max()
			.orElse(envelopeMinX + 1.0d);
		double envelopeMaxZ = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::maxZ).max()
			.orElse(envelopeMinZ + 1.0d);
		double minY = SurgicalTablePlane.surfaceY(worldPosition.getY());
		List<SurgicalTableSupportManager.ReleasedCube> released = new ArrayList<>(cubes.cardinality());
		int cubeCount = Math.max(1, cubes.cardinality());
		boolean perCubeFootprints = footprints.size() == cubeCount;
		double sharedScale = perCubeFootprints ? 1.0d : 1.0d / Math.cbrt(cubeCount);
		int footprintIndex = 0;
		for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
			SurgicalTableLayout.Footprint footprint = perCubeFootprints ? footprints.get(footprintIndex++) : null;
			double sourceMinX = footprint == null ? envelopeMinX : footprint.minX();
			double sourceMinZ = footprint == null ? envelopeMinZ : footprint.minZ();
			double sourceMaxX = footprint == null ? envelopeMaxX : footprint.maxX();
			double sourceMaxZ = footprint == null ? envelopeMaxZ : footprint.maxZ();
			double centerX = (sourceMinX + sourceMaxX) * 0.5d;
			double centerZ = (sourceMinZ + sourceMaxZ) * 0.5d;
			double width = Math.max(1.0d / 16.0d, (sourceMaxX - sourceMinX) * sharedScale);
			double depth = Math.max(1.0d / 16.0d, (sourceMaxZ - sourceMinZ) * sharedScale);
			double height = Math.max(1.0d / 16.0d, Math.min(2.0d, Math.max(width, depth)));
			List<Vec3> fallback = boxCorners(centerX - width * 0.5d, minY, centerZ - depth * 0.5d,
				centerX + width * 0.5d, minY + height, centerZ + depth * 0.5d);
			released.add(new SurgicalTableSupportManager.ReleasedCube(subject.persistentId(),
				subject.profile(), cube, fallback));
		}
		return released;
	}

	private static List<Vec3> boxCorners(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ) {
		return List.of(new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ),
			new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ),
			new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ),
			new Vec3(minX, maxY, maxZ), new Vec3(maxX, maxY, maxZ));
	}

	@FunctionalInterface
	private interface TilePredicate {
		boolean test(int x, int z);
	}

	void setChangedAndSync() {
		normalizeLimbJoints();
		setChanged();
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		long started = SurgicalProfiler.begin();
		tag.putInt(FORMAT_VERSION_TAG, FORMAT_VERSION);
		if (!subjects.isEmpty()) {
			SurgicalStorage.Writer storage = new SurgicalStorage.Writer();
			ListTag encoded = new ListTag();
			for (SurgicalSubject subject : subjects)
				encoded.add(subject.save(storage));
			CompoundTag snapshot = new CompoundTag();
			snapshot.put(SUBJECTS_TAG, encoded);
			storage.save(snapshot);
			if (clientPacket)
				SurgicalTableSubjectSync.write(tag, snapshot);
			else
				for (String key : snapshot.getAllKeys())
					tag.put(key, snapshot.get(key));
		}
		tag.putInt(NEXT_SUBJECT_ID_TAG, nextSubjectId);
		super.write(tag, registries, clientPacket);
		SurgicalProfiler.end(clientPacket ? "write(sync)" : "write(save)", started);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		long started = SurgicalProfiler.begin();
		boolean previouslyHadSubjects = hasSubjects();
		super.read(tag, registries, clientPacket);
		boolean currentFormat = tag.contains(FORMAT_VERSION_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.getInt(FORMAT_VERSION_TAG) == FORMAT_VERSION
			&& tag.contains(NEXT_SUBJECT_ID_TAG, Tag.TAG_ANY_NUMERIC);
		boolean releasedFormat = !clientPacket && SurgicalReleaseMigration.supportsTable(tag.getInt(FORMAT_VERSION_TAG))
			&& tag.contains(NEXT_SUBJECT_ID_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.getList(SUBJECTS_TAG, Tag.TAG_COMPOUND).size() <= MAX_SUBJECTS
			&& !tag.contains(SurgicalStorage.SHARED_TAG);
		pendingReleaseMigrationSave = releasedFormat;
		List<SurgicalSubject> loaded = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		Set<UUID> persistentIds = new HashSet<>();
		if (currentFormat || releasedFormat) {
			CompoundTag snapshot = clientPacket ? SurgicalTableSubjectSync.read(tag) : tag;
			SurgicalStorage.Reader storage = new SurgicalStorage.Reader(snapshot, MAX_SUBJECTS);
			ListTag encoded = snapshot.getList(SUBJECTS_TAG, Tag.TAG_COMPOUND);
			Map<UUID, BitSet> donorHeads = releasedFormat ? SurgicalReleaseMigration.donorHeads(encoded) : Map.of();
			for (int index = 0; index < encoded.size() && loaded.size() < MAX_SUBJECTS; index++) {
				CompoundTag savedSubject = encoded.getCompound(index);
				if (releasedFormat)
					savedSubject = SurgicalReleaseMigration.subject(savedSubject, donorHeads);
				SurgicalSubject subject = savedSubject == null ? null : SurgicalSubject.load(savedSubject, storage);
				if (subject == null || !ids.add(subject.id()) || !persistentIds.add(subject.persistentId()))
					continue;
				loaded.add(subject);
			}
		}
		boolean structureChanged = loaded.size() != subjects.size();
		Set<UUID> changed = clientPacket ? retainUnchangedSubjects(loaded) : Set.of();
		clearSubjects();
		addSubjects(loaded);
		nextSubjectId = Math.max(currentFormat || releasedFormat ? tag.getInt(NEXT_SUBJECT_ID_TAG) : 0,
			ids.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1);
		if (clientPacket) {
			pendingSavedSubjectNormalization = false;
			clientPlane = null;
			clientPlaneBounds = null;
			clientPlaneCacheUntil = Long.MIN_VALUE;
			// Both normalizers walk every subject on the table, and a retained subject was already
			// normalized when it first arrived, so they only run when the packet changed something.
			if (structureChanged || !changed.isEmpty()) {
				normalizeCombinations(false);
				normalizeLimbJoints();
				// Dropping the measured bounds is safe now that each geometry re-reports its cached
				// bounds without a transform pass; a subject leaving or arriving must not force every
				// other subject on the table to rebuild.
				clientRenderBounds = null;
				clientDataRevision++;
				Set<UUID> stale = groundingDependents(changed);
				for (SurgicalSubject subject : subjects)
					if (stale.contains(subject.persistentId()))
						subject.setClientRenderRevision(clientDataRevision);
			}
			if (previouslyHadSubjects != hasSubjects() && level != null)
				level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 16);
		} else {
			pendingSavedSubjectNormalization = true;
			normalizeSavedSubjects();
		}
		SurgicalProfiler.end(clientPacket ? "read(sync)" : "read(load)", started);
	}

	/**
	 * Every subject that has to re-run its client geometry because one of {@code changed} moved.
	 *
	 * <p>A subject is grounded together with the ones it shares a connection component with, so a
	 * change only propagates along glue joints and combinations. Treating every linked subject on the
	 * table as stale — which is what a plain {@code linkedToOtherSubjects()} test does — puts a
	 * full-table grounding pass on each of them, so a glue-heavy table paid for all of them on every
	 * packet no matter which one was actually edited.</p>
	 */
	private Set<UUID> groundingDependents(Set<UUID> changed) {
		Set<UUID> stale = new HashSet<>(changed);
		Map<Integer, Set<Integer>> groups = SurgicalTableConnections.connectedSubjectGroups(this);
		for (UUID persistentId : changed)
			for (int subjectId : groups.get(subjectsByPersistentId.get(persistentId).id()))
				stale.add(getSubject(subjectId).persistentId());
		return stale;
	}

	/**
	 * Every sync packet re-decodes every subject on the table, even the ones the edit never touched.
	 * Swapping those for the instance the client already had, before the decoded list is installed,
	 * keeps the model-preview and captured-geometry caches — both keyed on subject identity — warm,
	 * so only the subjects that really moved pay for a rebuild.
	 *
	 * @return the persistent ids of the subjects whose content differs from what the client already had
	 */
	private Set<UUID> retainUnchangedSubjects(List<SurgicalSubject> decoded) {
		Set<UUID> changed = new HashSet<>();
		for (int index = 0; index < decoded.size(); index++) {
			SurgicalSubject subject = decoded.get(index);
			SurgicalSubject retained = subjectsByPersistentId.get(subject.persistentId());
			if (retained != null && retained.contentEquals(subject))
				decoded.set(index, retained);
			else
				changed.add(subject.persistentId());
		}
		return changed;
	}

	@Override
	public AABB getRenderBoundingBox() {
		AABB bounds = new AABB(worldPosition);
		// Only the controller owns subjects. Every other tile of the surface draws nothing, so its own
		// block is all the frustum test needs; the wide fallback below would keep each of them
		// dispatched to the renderer for no output.
		if (subjects.isEmpty())
			return bounds;
		if (getClientPlane() != null && clientPlaneBounds != null)
			bounds = bounds.minmax(clientPlaneBounds);
		if (clientRenderBounds != null)
			return bounds.minmax(clientRenderBounds).inflate(0.25d);
		double surfaceY = SurgicalTablePlane.surfaceY(worldPosition.getY());
		for (SurgicalSubject subject : subjects) {
			AABB placedCenter = new AABB(worldPosition.getX() + subject.originOffsetX(), surfaceY,
				worldPosition.getZ() + subject.originOffsetZ(),
				worldPosition.getX() + subject.originOffsetX() + 1.0d, surfaceY + 1.0d,
				worldPosition.getZ() + subject.originOffsetZ() + 1.0d);
			bounds = bounds.minmax(placedCenter);
		}
		return bounds.inflate(8.0d);
	}
}
