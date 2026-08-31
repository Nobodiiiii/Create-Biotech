package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The canonical corner block entity for all surgical subjects on one connected table plane. */
public class SurgicalTableBlockEntity extends SmartBlockEntity {
	public static final int MAX_SUBJECTS = SurgicalTablePlane.MAX_TILES * SurgicalTableLayout.SLOTS_PER_TILE;
	private static final String SUBJECTS_TAG = "SurgicalSubjects";
	private static final String NEXT_SUBJECT_ID_TAG = "NextSubjectId";
	private static final String LEGACY_PROFILE_TAG = "MimicProfile";
	private static final int CLIENT_PLANE_CACHE_TICKS = 5;

	private final List<SurgicalSubject> subjects = new ArrayList<>();
	private final List<SurgicalSubject> subjectsView = Collections.unmodifiableList(subjects);
	private final Map<Integer, SurgicalSubject> subjectsById = new HashMap<>();
	private final Map<UUID, SurgicalSubject> subjectsByPersistentId = new HashMap<>();
	private int nextSubjectId;
	private int clientDataRevision;
	@Nullable
	private AABB clientRenderBounds;
	@Nullable
	private SurgicalTablePlane.Plane clientPlane;
	@Nullable
	private AABB clientPlaneBounds;
	private boolean clientProjectsSourceGeometry;
	private long clientPlaneCacheUntil = Long.MIN_VALUE;
	private int clientPlaneLayout = -1;
	@Nullable
	private SurgicalConnectionGraph<UUID> cachedConnectionGraph;
	private long cachedConnectionSignature = Long.MIN_VALUE;
	private boolean cachedConnectionValid;
	private Map<Integer, Set<Integer>> cachedConnectedSubjectGroups = Map.of();
	private long cachedConnectedSubjectGroupsSignature = Long.MIN_VALUE;
	private boolean cachedConnectedSubjectGroupsValid;
	@Nullable
	private SurgicalTablePlane.Plane serverPlane;
	private int serverPlaneLayout = -1;
	private int consolidatedPlaneLayout = -1;
	private int consolidatedPlaneTiles = -1;
	@Nullable
	private SurgicalConnectionGraph<UUID> variantConnectionGraph;
	private long variantConnectionSignature = Long.MIN_VALUE;
	private Set<SurgicalGlueJoint> variantConnectionExcluded = Set.of();
	private Map<UUID, BitSet> variantConnectionOverrides = Map.of();
	private boolean variantConnectionCombinations;
	private boolean variantConnectionValid;
	/** Bumped by {@link #invalidateTableLayout()}; see {@link #getServerPlane()}. */
	private static int tableLayoutRevision;

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
			clientPlane = SurgicalTablePlane.scan(level, worldPosition);
			clientPlaneCacheUntil = now + CLIENT_PLANE_CACHE_TICKS;
			clientPlaneLayout = tableLayoutRevision;
			clientPlaneBounds = new AABB(worldPosition);
			clientProjectsSourceGeometry = false;
			for (BlockPos tablePos : clientPlane.tiles()) {
				clientPlaneBounds = clientPlaneBounds.minmax(new AABB(tablePos));
				if (!clientProjectsSourceGeometry
					&& level.getBlockState(tablePos).is(CBBlocks.PROJECTION_SURGICAL_TABLE.get()))
					clientProjectsSourceGeometry = true;
			}
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

	private void addSubject(SurgicalSubject subject) {
		subjects.add(subject);
		subjectsById.put(subject.id(), subject);
		subjectsByPersistentId.put(subject.persistentId(), subject);
		cachedConnectedSubjectGroupsValid = false;
		SurgicalTableSupportManager.track(this);
	}

	private void addSubjects(List<SurgicalSubject> added) {
		for (SurgicalSubject subject : added)
			addSubject(subject);
	}

	private void removeSubject(SurgicalSubject subject) {
		subjects.remove(subject);
		subjectsById.remove(subject.id(), subject);
		subjectsByPersistentId.remove(subject.persistentId(), subject);
		cachedConnectedSubjectGroupsValid = false;
		SurgicalTableSupportManager.track(this);
	}

	private void clearSubjects() {
		subjects.clear();
		subjectsById.clear();
		subjectsByPersistentId.clear();
		cachedConnectedSubjectGroupsValid = false;
		SurgicalTableSupportManager.track(this);
	}

	private void normalizeCombinations() {
		Map<UUID, SurgicalCombination> byId = new HashMap<>();
		Set<UUID> conflicting = new HashSet<>();
		for (SurgicalSubject subject : subjects)
			for (SurgicalCombination combination : subject.combinations()) {
				SurgicalCombination previous = byId.putIfAbsent(combination.id(), combination);
				if (previous != null && !previous.members().equals(combination.members()))
					conflicting.add(combination.id());
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
			attachCombination(combination);
	}

	public void includeClientRenderBounds(AABB bounds) {
		clientRenderBounds = clientRenderBounds == null ? bounds : clientRenderBounds.minmax(bounds);
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
				body = connectedGroup(parent, joint.parent().cubeId());
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

	private void attachLimbJoint(SurgicalLimbJoint joint) {
		SurgicalSubject child = getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parent = getSubjectByPersistentId(joint.parent().subjectKey());
		if (child == null || parent == null)
			return;
		child.addLimbJoint(joint);
		if (parent != child)
			parent.addLimbJoint(joint);
	}

	private Set<SurgicalLimbJoint> allLimbJoints() {
		Set<SurgicalLimbJoint> joints = new java.util.LinkedHashSet<>();
		for (SurgicalSubject subject : subjects)
			joints.addAll(subject.limbJoints());
		return joints;
	}

	private Set<SurgicalLimbJoint> limbsWithin(ComponentGroup group) {
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
		return List.copyOf(limbsWithin(connectedGroup(subject, cubeId)));
	}

	/** All anatomical joints installed on this table, without the per-subject storage duplicates. */
	public List<SurgicalLimbJoint> limbJoints() {
		return List.copyOf(allLimbJoints());
	}

	public SurgicalTablePlacementResult tryPlaceSubject(ItemStack box, SurgicalTablePlane.Plane plane,
		Direction placementFacing,
		SurgicalLayPose layPose, double placedOriginOffsetX, double placedOriginOffsetZ,
		SurgicalTableLayout.Proposal proposal,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		List<SurgicalTableLayout.Footprint> componentFootprints,
		List<SurgicalTableLayout.Proposal> sourceLayouts) {
		TemporaryMoveSource temporaryMove = SurgicalKitItem.hasTemporaryCapture(box)
			? resolveTemporaryMove(box) : null;
		if (SurgicalKitItem.isTemporaryBox(box) && temporaryMove == null)
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		List<SurgicalTableLayout.Footprint> occupied = temporaryMove != null
			&& temporaryMove.sourceTable == this
			? occupiedOutside(temporaryMove.group) : occupiedForValidation(plane, -1);
		if (level == null || level.isClientSide || !worldPosition.equals(plane.source()) || occupied == null)
			return SurgicalTablePlacementResult.INVALID_TABLE;
		if (!(box.getItem() instanceof CapturedEntityBoxItem) && temporaryMove == null
			|| !CapturedEntityBoxHelper.hasCapturedEntity(box) || observedSeams == null
			|| componentFootprints == null || sourceLayouts == null
			|| layPose == null || !layPose.valid())
			return SurgicalTablePlacementResult.INVALID_CAPTURE;

		Entity captured = CapturedEntityBoxHelper.createCapturedEntity(box, level);
		if (captured == null)
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		SurgicalAssembly placementAssembly = null;
		MimicProfile profile;
		int cubeCount;
		BitSet present;
		List<SurgicalAssembly.Seam> seams;
		BitSet cuts;
		List<Integer> cutOrder;
		List<SurgicalTableLayout.Footprint> storedFootprints;
		if (captured instanceof SlimeBionicEntity bionic) {
			SurgicalAssembly assembly = bionic.getAssembly();
			if (assembly == null)
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			placementAssembly = assembly;
			if (assembly.preservesLayout() || assembly.sources().size() != 1) {
				if (!SurgicalTableLayout.validateSubjectPlacement(plane, assembly, placementFacing, layPose,
					placedOriginOffsetX, placedOriginOffsetZ, proposal, observedCubeCount, observedSeams,
					componentFootprints, sourceLayouts, occupied))
					return SurgicalTablePlacementResult.NO_SPACE;
				return tryPlaceComposite(box, placementFacing, placedOriginOffsetX, placedOriginOffsetZ,
					sourceLayouts, assembly, temporaryMove);
			}
			if (!sourceLayouts.isEmpty())
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			profile = assembly.profile();
			cubeCount = assembly.cubeCount();
			present = assembly.presentCubes();
			seams = assembly.seams();
			cuts = assembly.cutSeams();
			cutOrder = assembly.cutOrder();
			storedFootprints = proposal.footprints();
		} else if (captured instanceof LivingEntity living && SlimeMimicHandler.isSlimeMimic(living)) {
			if (!sourceLayouts.isEmpty())
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			if (!SurgicalAssembly.validTopology(observedCubeCount, observedSeams))
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			profile = MimicProfile.capture(living);
			if (profile == null)
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			cubeCount = observedCubeCount;
			present = new BitSet(cubeCount);
			if (cubeCount > 0)
				present.set(0, cubeCount);
			seams = List.copyOf(observedSeams);
			cuts = new BitSet();
			cutOrder = List.of();
			storedFootprints = List.copyOf(componentFootprints);
		} else if (captured instanceof LivingEntity)
			return SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT;
		else
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		if (effectiveSubjectCount(temporaryMove) >= MAX_SUBJECTS)
			return SurgicalTablePlacementResult.TABLE_FULL;
		if (!SurgicalTableLayout.validateSubjectPlacement(plane, placementAssembly, placementFacing, layPose,
			placedOriginOffsetX, placedOriginOffsetZ, proposal, observedCubeCount, observedSeams,
			componentFootprints, sourceLayouts, occupied))
			return SurgicalTablePlacementResult.NO_SPACE;

		SurgicalSubject subject = new SurgicalSubject(allocateSubjectId(), profile, placementFacing, layPose, cubeCount,
			present, seams, cuts, cutOrder, placedOriginOffsetX, placedOriginOffsetZ, placementOffsets(proposal),
			storedFootprints);
		commitTemporaryMove(temporaryMove);
		addSubject(subject);
		finishSubjectPlacement(box, temporaryMove);
		return SurgicalTablePlacementResult.SUCCESS;
	}

	private SurgicalTablePlacementResult tryPlaceComposite(ItemStack box, Direction placementFacing,
		double placedOriginOffsetX, double placedOriginOffsetZ,
		List<SurgicalTableLayout.Proposal> sourceLayouts, SurgicalAssembly assembly,
		@Nullable TemporaryMoveSource temporaryMove) {
		List<SurgicalAssembly.Source> assemblySources = assembly.sources();
		List<SurgicalAssembly.PlacedSource> placedSources = assembly.placedSources(placementFacing);
		if (sourceLayouts.size() != assemblySources.size()
			|| placedSources.size() != assemblySources.size())
			return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
		if (effectiveSubjectCount(temporaryMove) > MAX_SUBJECTS - assemblySources.size())
			return SurgicalTablePlacementResult.TABLE_FULL;

		// Restore sources as normal table subjects so each source keeps its own model topology and
		// automatically participates in the existing ray selection and shears workflow.
		List<SurgicalSubject> restored = new ArrayList<>(assemblySources.size());
		for (int sourceId = 0; sourceId < assemblySources.size(); sourceId++) {
			SurgicalAssembly.PlacedSource placed = placedSources.get(sourceId);
			SurgicalAssembly.Source source = placed.source();
			SurgicalSubject subject = new SurgicalSubject(allocateSubjectId(), source.profile(), placed.facing(),
				placed.layPose(),
				source.cubeCount(), source.presentCubes(), source.seams(), source.cutSeams(), source.cutOrder(),
				placedOriginOffsetX + placed.originOffset().x,
				placedOriginOffsetZ + placed.originOffset().z, restoredOffsets(placed), placed.cubeRotations(),
				sourceLayouts.get(sourceId).footprints());
			restored.add(subject);
		}
		for (SurgicalAssembly.Joint encoded : assembly.placedJoints(placementFacing)) {
			SurgicalSubject first = restored.get(encoded.firstSource());
			SurgicalSubject second = restored.get(encoded.secondSource());
			SurgicalGlueJoint.Endpoint firstEndpoint =
				new SurgicalGlueJoint.Endpoint(first.persistentId(), encoded.firstCube());
			SurgicalGlueJoint.Endpoint secondEndpoint =
				new SurgicalGlueJoint.Endpoint(second.persistentId(), encoded.secondCube());
			SurgicalGlueJoint.Replay replay = null;
			if (encoded.replay() != null) {
				SurgicalSubject moving = restored.get(encoded.replay().movingSource());
				replay = new SurgicalGlueJoint.Replay(new SurgicalGlueJoint.Endpoint(
					moving.persistentId(), encoded.replay().movingCube()), encoded.replay().transform(),
					encoded.replay().anchorContact());
			}
			SurgicalGlueJoint joint = SurgicalGlueJoint.of(firstEndpoint, secondEndpoint, replay);
			first.addGlueJoint(joint);
			if (second != first)
				second.addGlueJoint(joint);
		}
		for (SurgicalAssembly.Combination encoded : assembly.combinations()) {
			List<SurgicalCombination.Member> members = new ArrayList<>(encoded.members().size());
			for (SurgicalAssembly.CombinationMember member : encoded.members()) {
				SurgicalSubject restoredSubject = restored.get(member.source());
				members.add(new SurgicalCombination.Member(restoredSubject.persistentId(), member.cube()));
			}
			SurgicalCombination combination = SurgicalCombination.create(encoded.id(), members);
			if (combination == null)
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			for (int source : encoded.members().stream()
				.mapToInt(SurgicalAssembly.CombinationMember::source).distinct().toArray())
				restored.get(source).addCombination(combination);
		}
		for (SurgicalAssembly.Limb encoded : assembly.limbs()) {
			SurgicalSubject child = restored.get(encoded.childSource());
			SurgicalSubject parent = restored.get(encoded.parentSource());
			SurgicalLimbJoint limb = new SurgicalLimbJoint(encoded.type(),
				new SurgicalGlueJoint.Endpoint(child.persistentId(), encoded.childCube()),
				new SurgicalGlueJoint.Endpoint(parent.persistentId(), encoded.parentCube()));
			child.addLimbJoint(limb);
			if (parent != child)
				parent.addLimbJoint(limb);
		}
		commitTemporaryMove(temporaryMove);
		addSubjects(restored);
		finishSubjectPlacement(box, temporaryMove);
		return SurgicalTablePlacementResult.SUCCESS;
	}

	@Nullable
	private TemporaryMoveSource resolveTemporaryMove(ItemStack kit) {
		SurgicalKitItem.TemporaryMove move = SurgicalKitItem.temporaryMove(kit);
		if (move == null || !(level instanceof ServerLevel sourceLevel)
			|| !level.dimension().location().equals(move.dimension()) || !level.isLoaded(move.tablePos()))
			return null;
		TemporaryMoveSource resolved = resolveTemporaryMoveSource(sourceLevel, move);
		if (resolved == null)
			SurgicalKitItem.clearTemporaryCapture(kit);
		return resolved;
	}

	/** Performs a read-only validity check once the source chunk is available. */
	public static boolean isTemporaryMoveValid(ServerLevel sourceLevel,
		SurgicalKitItem.TemporaryMove move) {
		return sourceLevel != null && move != null && sourceLevel.isLoaded(move.tablePos())
			&& resolveTemporaryMoveSource(sourceLevel, move) != null;
	}

	@Nullable
	private static TemporaryMoveSource resolveTemporaryMoveSource(ServerLevel sourceLevel,
		SurgicalKitItem.TemporaryMove move) {
		if (!sourceLevel.dimension().location().equals(move.dimension()))
			return null;
		SurgicalTablePlane.Plane sourcePlane = SurgicalTablePlane.scan(sourceLevel, move.tablePos());
		SurgicalTableBlockEntity sourceTable = controller(sourceLevel, sourcePlane);
		if (sourceTable == null)
			return null;
		Map<UUID, BitSet> components = move.components();
		SurgicalSubject anchor = sourceTable.getSubjectByPersistentId(move.anchorSubject());
		int anchorCube = move.anchorCube();
		BitSet storedAnchorComponent = components.get(move.anchorSubject());
		if (anchor == null || storedAnchorComponent == null || !storedAnchorComponent.get(anchorCube)
			|| !anchor.validPresentCube(anchorCube))
			return null;
		for (Map.Entry<UUID, BitSet> entry : components.entrySet()) {
			SurgicalSubject candidate = sourceTable.getSubjectByPersistentId(entry.getKey());
			int firstCube = entry.getValue().nextSetBit(0);
			CompoundTag sourceState = move.sourceSubject(entry.getKey());
			if (candidate == null || sourceState == null || !candidate.save().equals(sourceState)
				|| firstCube < 0 || !candidate.validPresentCube(firstCube))
				return null;
		}
		ComponentGroup group = sourceTable.connectedGroup(anchor, anchorCube);
		if (!group.components.equals(components))
			return null;
		BitSet anchorComponent = group.components.get(anchor.persistentId());
		SurgicalAssembly current = sourceTable.packedAssembly(anchor, anchorComponent, group,
			sourceTable.jointsWithin(group), sourceTable.combinationsWithin(group), sourceTable.limbsWithin(group));
		if (current == null || !current.save().equals(move.sourceAssembly()))
			return null;
		return new TemporaryMoveSource(sourceTable, group, sourceTable.freedSubjectSlots(group));
	}

	@Nullable
	private List<SurgicalTableLayout.Footprint> occupiedOutside(ComponentGroup group) {
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : subjects) {
			if (subject.occupiedFootprints().isEmpty())
				return null;
			BitSet moved = group.components.get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint))
					occupied.add(footprint);
		}
		return List.copyOf(occupied);
	}

	private int freedSubjectSlots(ComponentGroup group) {
		int freed = 0;
		for (SurgicalSubject subject : subjects) {
			BitSet moved = group.components.get(subject.persistentId());
			if (moved != null && moved.equals(subject.presentCubes))
				freed++;
		}
		return freed;
	}

	private int effectiveSubjectCount(@Nullable TemporaryMoveSource move) {
		return subjects.size() - (move != null && move.sourceTable == this ? move.freedSubjects : 0);
	}

	private static void commitTemporaryMove(@Nullable TemporaryMoveSource move) {
		if (move != null)
			move.sourceTable.removeTemporaryGroup(move.group);
	}

	private void removeTemporaryGroup(ComponentGroup group) {
		Set<SurgicalGlueJoint> removedJoints = jointsWithin(group);
		Set<SurgicalCombination> removedCombinations = combinationsWithin(group);
		Set<SurgicalLimbJoint> removedLimbs = limbsWithin(group);
		for (SurgicalSubject groupedSubject : List.copyOf(subjects)) {
			BitSet removed = group.components.get(groupedSubject.persistentId());
			if (removed != null)
				groupedSubject.removeComponent(removed);
			groupedSubject.removeGlueJoints(removedJoints);
			groupedSubject.removeCombinations(removedCombinations);
			groupedSubject.removeLimbJoints(removedLimbs);
			if (groupedSubject.isEmpty())
				removeSubject(groupedSubject);
		}
		clientRenderBounds = null;
	}

	/** Removes the complete server-authoritative connectivity group selected with a shovel. */
	public boolean shovelConnectedGroup(Player player, ItemStack shovel, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		double volume) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShovel(shovel)
			|| !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || !Double.isFinite(volume) || volume < 0.0d
			|| !canPayInteractionCost(shovel, 1, player) || level == null)
			return false;
		ComponentGroup group = connectedGroup(subject, cubeId);
		if (group.components.isEmpty())
			return false;

		damageInteractionTool(shovel, 1, player, hand);
		removeTemporaryGroup(group);
		setChangedAndSync();
		int drops = SurgicalSlimeDrops.roll(volume, level.getRandom());
		if (drops > 0)
			Containers.dropItemStack(level, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.1d,
				worldPosition.getZ() + 0.5d, new ItemStack(Items.SLIME_BALL, drops));
		level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_BREAK,
			SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	private void finishSubjectPlacement(ItemStack box, @Nullable TemporaryMoveSource move) {
		clientRenderBounds = null;
		CapturedEntityBoxHelper.clearCapturedEntity(box);
		if (move != null)
			SurgicalKitItem.clearTemporaryMove(box);
		setChangedAndSync();
		if (move != null && move.sourceTable != this)
			move.sourceTable.setChangedAndSync();
		level.playSound(null, worldPosition, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
	}

	private static Map<Integer, Vec3> placementOffsets(SurgicalTableLayout.Proposal proposal) {
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (SurgicalTableLayout.CubeOffset offset : proposal.offsets()) {
			Vec3 value = new Vec3(offset.x(), offset.y(), offset.z());
			if (value.lengthSqr() > 1.0e-24d)
				offsets.put(offset.cubeId(), value);
		}
		return Map.copyOf(offsets);
	}

	private static Map<Integer, Vec3> restoredOffsets(SurgicalAssembly.PlacedSource placed) {
		SurgicalAssembly.Source source = placed.source();
		Map<Integer, Vec3> restored = new HashMap<>();
		BitSet present = source.presentCubes();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			Vec3 offset = placed.cubeOffsets().getOrDefault(cube, Vec3.ZERO)
				.add(0.0d, placed.originOffset().y, 0.0d);
			if (offset.lengthSqr() > 1.0e-24d)
				restored.put(cube, offset);
		}
		return Map.copyOf(restored);
	}

	public boolean cutSeam(Player player, ItemStack shears, InteractionHand hand, int subjectId, int seamId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, double moveX, double moveZ) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| seamId < 0 || seamId >= subject.seams.size() || subject.cutSeams.get(seamId))
			return false;
		SurgicalAssembly.Seam seam = subject.seams.get(seamId);
		if (!subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second()))
			return false;
		SurgicalCombination protectedCombination = subject.combinationContaining(seam.first());
		if (protectedCombination != null
			&& protectedCombination.contains(subject.persistentId(), seam.second()))
			return false;
		SeamCutState cut = seamCutState(subject, seamId);
		if (cut == null || !validateSeamCut(subject, cut, proposal, moveX, moveZ, plane)
			|| !canPayInteractionCost(shears, 1, player))
			return false;

		subject.cutSeams = cut.proposedCuts;
		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		updatedOrder.add(seamId);
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		if (cut.separates)
			translateOtherSubjects(cut.moving, subject.persistentId(), new Vec3(moveX, 0.0d, moveZ));
		damageInteractionTool(shears, 1, player, hand);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	public boolean cutGlueJoint(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int glueJointId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, double moveX, double moveZ) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !canApplyGlueCut(subjectId, glueJointId, moveX, moveZ, plane)
			|| !canPayInteractionCost(shears, 1, player))
			return false;
		GlueCutState cut = glueCutState(subject, glueJointId);
		Vec3 delta = new Vec3(moveX, 0.0d, moveZ);

		Set<SurgicalGlueJoint> removed = Set.of(cut.joint);
		for (SurgicalSubject connected : subjects)
			connected.removeGlueJoints(removed);
		if (cut.separates)
			for (Map.Entry<UUID, BitSet> entry : cut.moving.components.entrySet()) {
				SurgicalSubject moved = getSubjectByPersistentId(entry.getKey());
				if (moved != null)
					moved.translateComponent(entry.getValue(), delta);
			}
		damageInteractionTool(shears, 1, player, hand);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	public boolean cutCubeConnections(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return false;
		if (subject.combinationContaining(cubeId) != null)
			return false;

		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		int seamCutCount = 0;
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			if (subject.cutSeams.get(seamId))
				continue;
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if (seam.first() != cubeId && seam.second() != cubeId
				|| !subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second()))
				continue;
			proposedCuts.set(seamId);
			updatedOrder.add(seamId);
			seamCutCount++;
		}
		Set<SurgicalGlueJoint> glueCuts = new HashSet<>();
		for (SurgicalGlueJoint joint : subject.glueJoints())
			if (joint.touches(subject.persistentId(), cubeId))
				glueCuts.add(joint);
		int cutCount = seamCutCount + glueCuts.size();
		if (cutCount == 0)
			return false;
		if (!canApplyComponentLayout(subjectId, proposedCuts, proposal, plane)
			|| !canPayInteractionCost(shears, cutCount, player))
			return false;

		subject.cutSeams = proposedCuts;
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		if (!glueCuts.isEmpty())
			for (SurgicalSubject connected : subjects)
				connected.removeGlueJoints(glueCuts);
		damageInteractionTool(shears, cutCount, player, hand);
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	/**
	 * Server-authoritative batch cut for layouts that may move retained glue/combination groups across
	 * several subjects. The client supplies only one delta per deterministically ordered post-cut
	 * group; group membership is always rebuilt from server topology before validation or mutation.
	 */
	public boolean cutCubeConnections(Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return false;
		BatchCutState cut = batchCutState(subject, cubeId);
		if (cut == null || !validateBatchCut(subject, cut, proposal, groupDeltas, plane)
			|| !canPayInteractionCost(shears, cut.cutCount, player))
			return false;

		subject.cutSeams = cut.proposedCuts;
		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		updatedOrder.addAll(cut.cutSeamIds);
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		for (int groupId = 0; groupId < cut.groups.size(); groupId++) {
			Vec3 delta = groupDeltas.get(groupId);
			if (delta.horizontalDistanceSqr() <= 1.0e-24d)
				continue;
			for (Map.Entry<UUID, BitSet> entry : cut.groups.get(groupId).components.entrySet()) {
				if (entry.getKey().equals(subject.persistentId()))
					continue;
				SurgicalSubject moved = getSubjectByPersistentId(entry.getKey());
				if (moved != null)
					moved.translateComponent(entry.getValue(), delta);
			}
		}
		if (!cut.glueCuts.isEmpty())
			for (SurgicalSubject connected : subjects)
				connected.removeGlueJoints(cut.glueCuts);
		damageInteractionTool(shears, cut.cutCount, player, hand);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	public boolean canApplyBatchCut(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return false;
		BatchCutState cut = batchCutState(subject, cubeId);
		return cut != null && validateBatchCut(subject, cut, proposal, groupDeltas, plane);
	}

	private boolean validateBatchCut(SurgicalSubject edited, BatchCutState cut,
		SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas, SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || groupDeltas == null || groupDeltas.size() != cut.groups.size()
			|| groupDeltas.isEmpty())
			return false;
		for (int groupId = 0; groupId < groupDeltas.size(); groupId++) {
			Vec3 delta = groupDeltas.get(groupId);
			if (delta == null || !validCutDelta(delta.x, delta.z) || Math.abs(delta.y) > 1.0e-9d
				|| groupId == 0 && delta.horizontalDistanceSqr() > 1.0e-18d
				|| groupId > 0 && delta.horizontalDistanceSqr() <= 1.0e-18d)
				return false;
		}

		BatchGroupIndex groupIndex = new BatchGroupIndex(cut.groups);
		Map<Integer, Vec3> expectedOffsets = new HashMap<>();
		for (int cube = edited.presentCubes.nextSetBit(0); cube >= 0;
			cube = edited.presentCubes.nextSetBit(cube + 1)) {
			int groupId = groupIndex.groupOf(edited.persistentId(), cube);
			Vec3 delta = groupId < 0 ? Vec3.ZERO : groupDeltas.get(groupId);
			expectedOffsets.put(cube, edited.componentOffsets.getOrDefault(cube, Vec3.ZERO).add(delta));
		}
		if (!layoutMatchesTranslations(proposal, expectedOffsets)
			|| !SurgicalTableLayout.validateEditedGlueComponents(plane, edited.cubeCount,
				edited.presentCubes, edited.seams, cut.proposedCuts, proposal, List.of()))
			return false;

		List<List<SurgicalTableLayout.Footprint>> groupFootprints = new ArrayList<>(cut.groups.size());
		for (int groupId = 0; groupId < cut.groups.size(); groupId++)
			groupFootprints.add(new ArrayList<>());
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : proposal.footprints()) {
			int groupId = groupIndex.groupOf(edited.persistentId(), footprint.componentRoot());
			(groupId < 0 ? obstacles : groupFootprints.get(groupId)).add(footprint);
		}
		for (SurgicalSubject other : subjects) {
			if (other == edited)
				continue;
			if (other.occupiedFootprints().isEmpty())
				return false;
			// A footprint with no component root stands for the whole subject, which is a per-subject
			// question rather than a per-footprint one; resolve it once instead of inside the loop.
			int wholeSubjectGroup = groupIndex.wholeSubjectGroupOf(cut.groups, other);
			for (SurgicalTableLayout.Footprint footprint : other.occupiedFootprints()) {
				int groupId = footprint.componentRoot() < 0 ? wholeSubjectGroup
					: groupIndex.groupOf(other.persistentId(), footprint.componentRoot());
				if (groupId < 0) {
					obstacles.add(footprint);
					continue;
				}
				Vec3 delta = groupDeltas.get(groupId);
				SurgicalTableLayout.Footprint translated = translateFootprint(footprint, delta);
				if (!plane.workArea().contains(translated.minX(), translated.minZ(), translated.maxX(),
					translated.maxZ(), 1.0e-6d))
					return false;
				groupFootprints.get(groupId).add(translated);
			}
		}
		for (List<SurgicalTableLayout.Footprint> group : groupFootprints)
			if (group.isEmpty())
				return false;
		return noCrossGroupConflict(groupFootprints, obstacles);
	}

	/**
	 * Rejects any pair of footprints carrying different group tags. Obstacles all share one tag and
	 * same-group pairs were never compared, so this is exactly the pair of nested loops it replaces -
	 * group against obstacle, and group against every other group - expressed over one tagged set.
	 *
	 * <p>Sorting by {@code minX} lets the inner scan stop at the first footprint that starts beyond
	 * the outer one's reach, since none of the ones after it start any earlier. Cube counts, and
	 * therefore footprint counts, are bounded only by {@link SurgicalAssembly#MAX_CUBES}, and the
	 * whole set arrives from the client.
	 */
	private static boolean noCrossGroupConflict(List<List<SurgicalTableLayout.Footprint>> groupFootprints,
		List<SurgicalTableLayout.Footprint> obstacles) {
		List<TaggedFootprint> tagged = new ArrayList<>(obstacles.size());
		for (SurgicalTableLayout.Footprint obstacle : obstacles)
			tagged.add(new TaggedFootprint(-1, obstacle));
		for (int groupId = 0; groupId < groupFootprints.size(); groupId++)
			for (SurgicalTableLayout.Footprint footprint : groupFootprints.get(groupId))
				tagged.add(new TaggedFootprint(groupId, footprint));
		tagged.sort(Comparator.comparingDouble(entry -> entry.footprint.minX()));

		for (int first = 0; first < tagged.size(); first++) {
			TaggedFootprint start = tagged.get(first);
			// Deliberately not subtracting conflictsWith's epsilon: a slightly long reach only costs a
			// few extra exact tests, while a short one could skip a real conflict.
			double reach = start.footprint.maxX() + SurgicalTableLayout.COMPONENT_CLEARANCE;
			for (int second = first + 1; second < tagged.size(); second++) {
				TaggedFootprint candidate = tagged.get(second);
				if (candidate.footprint.minX() >= reach)
					break;
				if (start.group != candidate.group
					&& start.footprint.conflictsWith(candidate.footprint))
					return false;
			}
		}
		return true;
	}

	private record TaggedFootprint(int group, SurgicalTableLayout.Footprint footprint) {}

	/**
	 * Group membership indexed by cube id. Resolving it by scanning {@code cut.groups} cost one
	 * UUID-keyed map probe per group, and the callers ask once per cube and once per footprint, so
	 * the scan was quadratic in two client-supplied quantities.
	 */
	private static final class BatchGroupIndex {
		private final Map<UUID, int[]> cubeGroups = new HashMap<>();

		private BatchGroupIndex(List<ComponentGroup> groups) {
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

		private int groupOf(UUID subjectKey, int cubeId) {
			if (cubeId < 0)
				return -1;
			int[] index = cubeGroups.get(subjectKey);
			return index == null || cubeId >= index.length ? -1 : index[cubeId];
		}

		private int wholeSubjectGroupOf(List<ComponentGroup> groups, SurgicalSubject subject) {
			for (int groupId = 0; groupId < groups.size(); groupId++) {
				BitSet cubes = groups.get(groupId).components.get(subject.persistentId());
				if (cubes != null && cubes.equals(subject.presentCubes))
					return groupId;
			}
			return -1;
		}
	}

	private static SurgicalTableLayout.Footprint translateFootprint(SurgicalTableLayout.Footprint footprint,
		Vec3 delta) {
		return new SurgicalTableLayout.Footprint(footprint.componentRoot(),
			footprint.minX() + delta.x, footprint.minZ() + delta.z,
			footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
			SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
	}

	public boolean packComponent(Player player, ItemStack boxes, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || !CapturedEntityBoxItem.isBox(boxes)
			|| CapturedEntityBoxItem.hasCapturedEntity(boxes) || bodyBounds == null
			|| hitboxGeometry == null)
			return false;

		ComponentGroup group = connectedGroup(subject, cubeId);
		BitSet component = group.components.get(subject.persistentId());
		if (component == null || component.isEmpty() || level == null)
			return false;
		Set<SurgicalGlueJoint> groupJoints = jointsWithin(group);
		Set<SurgicalCombination> groupCombinations = combinationsWithin(group);
		Set<SurgicalLimbJoint> groupLimbs = limbsWithin(group);
		SurgicalAssembly assembly = packedAssembly(subject, component, group, groupJoints,
			groupCombinations, groupLimbs);
		if (assembly == null)
			return false;
		if (!validHitboxGeometry(assembly, bodyBounds, hitboxGeometry)
			|| !validMobilityMeasurements(assembly, bodyBounds))
			return false;
		assembly = assembly.withBodyGeometry(bodyBounds, hitboxGeometry);
		// Invalid or stale client combat geometry must not make an otherwise valid body unpackable.
		// Keep it only when it still describes exactly the effective shoulder joints in this assembly.
		int installedArms = (int) assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.SHOULDER).count();
		if (attackGeometry != null && attackGeometry.armCount() == installedArms)
			assembly = assembly.withAttackGeometry(attackGeometry);
		SlimeBionicEntity bionic = CBEntityTypes.SLIME_BIONIC.get().create(level);
		if (bionic == null)
			return false;
		bionic.setAssembly(assembly);
		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(boxes, player, bionic))
			return false;

		for (SurgicalSubject groupedSubject : List.copyOf(subjects)) {
			BitSet removed = group.components.get(groupedSubject.persistentId());
			if (removed != null)
				groupedSubject.removeComponent(removed);
			groupedSubject.removeGlueJoints(groupJoints);
			groupedSubject.removeCombinations(groupCombinations);
			groupedSubject.removeLimbJoints(groupLimbs);
			if (groupedSubject.isEmpty())
				removeSubject(groupedSubject);
		}
		clientRenderBounds = null;
		setChangedAndSync();
		level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 0.85f);
		return true;
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
		SurgicalSubject childSubject = getSubject(childSubjectId);
		SurgicalSubject parentSubject = getSubject(parentSubjectId);
		if (type == null || SurgicalKitItem.limbType(jointItem) != type
			|| childSubject == null || parentSubject == null
			|| !childSubject.initializeOrMatchTopology(childCubeCount, childSeams)
			|| !parentSubject.initializeOrMatchTopology(parentCubeCount, parentSeams)
			|| !childSubject.validPresentCube(childCubeId)
			|| !parentSubject.validPresentCube(parentCubeId)
			|| childSubject == parentSubject && childCubeId == parentCubeId)
			return false;

		SurgicalGlueJoint.Endpoint selectedChild = new SurgicalGlueJoint.Endpoint(childSubject.persistentId(),
			childCubeId);
		SurgicalGlueJoint.Endpoint selectedParent = new SurgicalGlueJoint.Endpoint(parentSubject.persistentId(),
			parentCubeId);
		SurgicalCombination childCombination = childSubject.combinationContaining(childCubeId);
		if (childCombination != null
			&& childCombination.contains(selectedParent.subjectKey(), selectedParent.cubeId()))
			return refuse(player, "limb_same_combination");
		DirectConnection connection = directConnection(selectedChild, selectedParent);
		if (connection == null)
			return refuse(player, "limb_not_connected");
		SurgicalGlueJoint.Endpoint child = connection.child();
		SurgicalGlueJoint.Endpoint parent = connection.parent();

		ComponentGroup body = connectedGroup(parentSubject, parentCubeId);
		Set<SurgicalLimbJoint> installed = limbsWithin(body);
		int used = 0;
		for (SurgicalLimbJoint existing : installed) {
			if (type.primary() && existing.type() == type)
				used++;
			if (rotatesTogether(existing.child(), child))
				return refuse(player, "limb_already_driven");
		}
		if (type.primary() && used >= type.maxPerBody())
			return refuse(player, "limb_limit");

		SurgicalLimbJoint installedJoint = new SurgicalLimbJoint(type, child, parent);
		Set<SurgicalLimbJoint> prospective = new java.util.LinkedHashSet<>(allLimbJoints());
		prospective.add(installedJoint);
		TableLimbTopology topology = limbTopology(prospective);
		if (type.primary() && (!topology.effective.contains(installedJoint)
			|| primaryTouchesDrivenPart(installedJoint, topology)))
			return refuse(player, "limb_primary_conflict");
		if (exceedsSecondaryCapacity(installedJoint, prospective, topology))
			return refuse(player, "limb_secondary_limit_" + type.id());
		attachLimbJoint(installedJoint);
		if (!player.getAbilities().instabuild)
			jointItem.shrink(1);
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
		String result = type.secondary() && !topology.effective.contains(installedJoint)
			? "limb_attached_pending_" : "limb_attached_";
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table." + result + type.id()), true);
		return true;
	}

	/** Captures a placement snapshot without removing the source component from its table. */
	public boolean captureTemporaryComponent(Player player, ItemStack kit, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || !SurgicalKitItem.isEmptyTemporaryBox(kit)
			|| bodyBounds == null || hitboxGeometry == null || level == null)
			return false;

		ComponentGroup group = connectedGroup(subject, cubeId);
		BitSet component = group.components.get(subject.persistentId());
		if (component == null || component.isEmpty())
			return false;
		SurgicalAssembly sourceAssembly = packedAssembly(subject, component, group, jointsWithin(group),
			combinationsWithin(group), limbsWithin(group));
		if (sourceAssembly == null || !validHitboxGeometry(sourceAssembly, bodyBounds, hitboxGeometry)
			|| !validMobilityMeasurements(sourceAssembly, bodyBounds))
			return false;

		SurgicalAssembly capturedAssembly = sourceAssembly.withBodyGeometry(bodyBounds, hitboxGeometry);
		int installedArms = (int) capturedAssembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.SHOULDER).count();
		if (attackGeometry != null && attackGeometry.armCount() == installedArms)
			capturedAssembly = capturedAssembly.withAttackGeometry(attackGeometry);
		Map<UUID, CompoundTag> sourceSubjects = new HashMap<>();
		for (UUID subjectKey : group.components.keySet()) {
			SurgicalSubject sourceSubject = getSubjectByPersistentId(subjectKey);
			if (sourceSubject == null)
				return false;
			sourceSubjects.put(subjectKey, sourceSubject.save());
		}
		SlimeBionicEntity bionic = CBEntityTypes.SLIME_BIONIC.get().create(level);
		if (bionic == null)
			return false;
		bionic.setAssembly(capturedAssembly);
		if (!CapturedEntityBoxHelper.captureEntity(kit, bionic))
			return false;
		SurgicalKitItem.setTemporaryMove(kit, level.dimension().location(), worldPosition,
			subject.persistentId(), cubeId, group.components, sourceSubjects, sourceAssembly.save());
		level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 0.85f);
		return true;
	}

	private static boolean validHitboxGeometry(SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds, SurgicalAssembly.HitboxGeometry hitboxGeometry) {
		long primaryLimbs = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type().primary()).count();
		if (primaryLimbs != hitboxGeometry.limbs().size())
			return false;
		SurgicalAssembly.VisualBounds collision = SurgicalAssembly.VisualBounds.create(
			-bodyBounds.width() * 0.5d, bodyBounds.minY(), -bodyBounds.depth() * 0.5d,
			bodyBounds.width() * 0.5d, bodyBounds.minY() + bodyBounds.height(),
			bodyBounds.depth() * 0.5d);
		return collision != null && hitboxGeometry.overall().contains(collision);
	}

	private static boolean validMobilityMeasurements(SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds) {
		long installedHips = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.HIP).count();
		long installedKnees = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.KNEE).count();
		return bodyBounds.groundedLegCount() <= installedHips
			&& bodyBounds.groundedKneeCount() <= installedKnees;
	}

	/** Removes one server-authoritative joint and returns its item, following Create's wrench pickup rules. */
	public boolean detachLimb(Player player, SurgicalLimbJoint joint) {
		if (joint == null || !allLimbJoints().contains(joint))
			return false;
		SurgicalSubject child = getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parent = getSubjectByPersistentId(joint.parent().subjectKey());
		if (child == null || parent == null || !child.validPresentCube(joint.child().cubeId())
			|| !parent.validPresentCube(joint.parent().cubeId()))
			return false;

		Set<SurgicalLimbJoint> removed = Set.of(joint);
		for (SurgicalSubject subject : subjects)
			subject.removeLimbJoints(removed);
		if (!player.getAbilities().instabuild)
			player.getInventory().placeItemBackInInventory(limbItem(joint.type()));
		setChangedAndSync();
		if (level != null)
			IWrenchable.playRemoveSound(level, worldPosition);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.limb_detached_" + joint.type().id()), true);
		return true;
	}

	private static ItemStack limbItem(SurgicalLimbType type) {
		return switch (type) {
		case NECK -> new ItemStack(CBItems.NECK_JOINT.get());
		case SHOULDER -> new ItemStack(CBItems.SHOULDER_JOINT.get());
		case ELBOW -> new ItemStack(CBItems.ELBOW_JOINT.get());
		case HIP -> new ItemStack(CBItems.HIP_JOINT.get());
		case KNEE -> new ItemStack(CBItems.KNEE_JOINT.get());
		};
	}

	private boolean refuse(Player player, String message) {
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table." + message), true);
		return false;
	}

	/**
	 * Finds the real cube pair through which two selected parts physically touch.
	 *
	 * <p>Comparison happens between whole rotating groups rather than single cubes: a honey
	 * combination behaves as one part, so clicking any of its cubes has to find the seam or glue
	 * joint that actually holds the combination against the body.</p>
	 */
	@Nullable
	private DirectConnection directConnection(SurgicalGlueJoint.Endpoint first,
		SurgicalGlueJoint.Endpoint second) {
		List<SurgicalGlueJoint.Endpoint> firstGroup = rotatingGroup(first);
		List<SurgicalGlueJoint.Endpoint> secondGroup = rotatingGroup(second);
		SurgicalConnectionGraph<UUID> graph = connectionGraph(Set.of(), Map.of(), false);
		if (graph == null)
			return null;
		if (directlyConnected(first, second, graph))
			return new DirectConnection(first, second);
		for (SurgicalGlueJoint.Endpoint child : firstGroup)
			for (SurgicalGlueJoint.Endpoint parent : secondGroup)
				if (directlyConnected(child, parent, graph))
					return new DirectConnection(child, parent);
		return null;
	}

	/** Client-safe preview check used to hide a joint's invalid second target before it is clicked. */
	public boolean canConnectLimbTargets(int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		SurgicalSubject firstSubject = getSubject(firstSubjectId);
		SurgicalSubject secondSubject = getSubject(secondSubjectId);
		if (firstSubject == null || secondSubject == null
			|| !firstSubject.validPresentCube(firstCubeId)
			|| !secondSubject.validPresentCube(secondCubeId))
			return false;
		SurgicalGlueJoint.Endpoint first = new SurgicalGlueJoint.Endpoint(firstSubject.persistentId(),
			firstCubeId);
		SurgicalGlueJoint.Endpoint second = new SurgicalGlueJoint.Endpoint(secondSubject.persistentId(),
			secondCubeId);
		return !rotatesTogether(first, second) && directConnection(first, second) != null;
	}

	private boolean directlyConnected(SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second,
		SurgicalConnectionGraph<UUID> graph) {
		return graph.directConnections(first.subjectKey(), first.cubeId())
			.contains(second.subjectKey(), second.cubeId());
	}

	private record DirectConnection(SurgicalGlueJoint.Endpoint child,
		SurgicalGlueJoint.Endpoint parent) {}

	/** The cubes that would turn together with {@code endpoint}: its combination, or itself. */
	private List<SurgicalGlueJoint.Endpoint> rotatingGroup(SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = getSubjectByPersistentId(endpoint.subjectKey());
		SurgicalCombination combination = subject == null ? null
			: subject.combinationContaining(endpoint.cubeId());
		if (combination == null)
			return List.of(endpoint);
		List<SurgicalGlueJoint.Endpoint> members = new ArrayList<>(combination.members().size());
		for (SurgicalCombination.Member member : combination.members())
			members.add(new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId()));
		return List.copyOf(members);
	}

	/** Whether two endpoints already move as one part because honey fused their combination. */
	private boolean rotatesTogether(SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second) {
		if (first.equals(second))
			return true;
		for (SurgicalCombination combination : allCombinations())
			if (combination.contains(first.subjectKey(), first.cubeId())
				&& combination.contains(second.subjectKey(), second.cubeId()))
				return true;
		return false;
	}

	/** A first-level joint may use an existing second-level parent, but never a joint-driven island. */
	private static boolean primaryTouchesDrivenPart(SurgicalLimbJoint candidate,
		TableLimbTopology topology) {
		Integer child = topology.components.get(candidate.child());
		Integer parent = topology.components.get(candidate.parent());
		if (child == null || parent == null)
			return true;
		return hasOtherChildAttachment(topology.attachments.get(child), candidate)
			|| hasOtherChildAttachment(topology.attachments.get(parent), candidate);
	}

	/** Enforces the matching second-level allowance on only the arm or leg being edited. */
	private static boolean exceedsSecondaryCapacity(SurgicalLimbJoint candidate,
		Set<SurgicalLimbJoint> prospective, TableLimbTopology topology) {
		SurgicalLimbType primaryType = candidate.type().primary()
			? candidate.type() : candidate.type().matchingPrimary();
		if (primaryType == null || primaryType.secondaryCapacity() <= 0)
			return false;
		SurgicalGlueJoint.Endpoint primaryChild = candidate.type().primary()
			? candidate.child() : candidate.parent();
		Integer primaryChildComponent = topology.components.get(primaryChild);
		if (primaryChildComponent == null)
			return false;

		int used = 0;
		for (SurgicalLimbJoint joint : prospective) {
			if (joint.type().matchingPrimary() != primaryType)
				continue;
			Integer secondaryParentComponent = topology.components.get(joint.parent());
			if (primaryChildComponent.equals(secondaryParentComponent)) {
				used += joint.type().secondaryCost();
				if (used > primaryType.secondaryCapacity())
					return true;
			}
		}
		return false;
	}

	private static boolean hasOtherChildAttachment(@Nullable List<TableLimbAttachment> attachments,
		SurgicalLimbJoint candidate) {
		if (attachments == null)
			return false;
		for (TableLimbAttachment attachment : attachments)
			if (attachment.child() && !attachment.limb().equals(candidate))
				return true;
		return false;
	}

	/**
	 * Resolves automatic cube/combination ownership for the currently installed anatomical joints.
	 * Second-level joints are retained even when absent from {@link TableLimbTopology#effective}; a
	 * later matching first-level joint can activate them without reinstalling the knee or elbow.
	 */
	private TableLimbTopology limbTopology(Set<SurgicalLimbJoint> installed) {
		if (installed.isEmpty())
			return TableLimbTopology.EMPTY;
		// Normal seams and glue do not merge anatomical ownership. Only an explicit honey combination
		// turns several cubes into one unit for the rules below.
		Map<SurgicalGlueJoint.Endpoint, Integer> componentIds = new HashMap<>();
		int nextComponentId = 0;
		for (SurgicalCombination combination : allCombinations()) {
			int componentId = nextComponentId++;
			for (SurgicalCombination.Member member : combination.members())
				componentIds.put(new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId()),
					componentId);
		}
		for (SurgicalSubject subject : subjects) {
			for (int cubeId = subject.presentCubes.nextSetBit(0); cubeId >= 0;
				cubeId = subject.presentCubes.nextSetBit(cubeId + 1)) {
				SurgicalGlueJoint.Endpoint endpoint =
					new SurgicalGlueJoint.Endpoint(subject.persistentId(), cubeId);
				if (!componentIds.containsKey(endpoint))
					componentIds.put(endpoint, nextComponentId++);
			}
		}

		Map<Integer, List<TableLimbAttachment>> attachments = new HashMap<>();
		Map<SurgicalLimbJoint, Integer> children = new HashMap<>();
		Map<SurgicalLimbJoint, Integer> parents = new HashMap<>();
		for (SurgicalLimbJoint limb : installed) {
			Integer child = componentIds.get(limb.child());
			Integer parent = componentIds.get(limb.parent());
			if (child == null || parent == null || child.equals(parent))
				continue;
			children.put(limb, child);
			parents.put(limb, parent);
			attachments.computeIfAbsent(child, ignored -> new ArrayList<>())
				.add(new TableLimbAttachment(limb, true));
			attachments.computeIfAbsent(parent, ignored -> new ArrayList<>())
				.add(new TableLimbAttachment(limb, false));
		}

		Map<Integer, SurgicalLimbJoint> owners = new HashMap<>();
		for (Map.Entry<Integer, List<TableLimbAttachment>> entry : attachments.entrySet()) {
			List<TableLimbAttachment> attached = entry.getValue();
			List<SurgicalLimbJoint> primaryChildren = new ArrayList<>();
			boolean primaryParent = false;
			List<SurgicalLimbJoint> secondaryChildren = new ArrayList<>();
			for (TableLimbAttachment attachment : attached) {
				if (attachment.child() && attachment.limb().type().primary())
					primaryChildren.add(attachment.limb());
				else if (!attachment.child() && attachment.limb().type().primary())
					primaryParent = true;
				else if (attachment.child() && attachment.limb().type().secondary())
					secondaryChildren.add(attachment.limb());
			}
			if (primaryChildren.size() == 1 && !primaryParent)
				owners.put(entry.getKey(), primaryChildren.getFirst());
			else if (primaryChildren.isEmpty() && !primaryParent && secondaryChildren.size() == 1
				&& attached.size() == 1)
				owners.put(entry.getKey(), secondaryChildren.getFirst());
		}

		Set<SurgicalLimbJoint> effective = new java.util.LinkedHashSet<>();
		for (SurgicalLimbJoint limb : installed) {
			Integer child = children.get(limb);
			Integer parent = parents.get(limb);
			if (child == null || parent == null || !limb.equals(owners.get(child)))
				continue;
			if (limb.type().primary() && attachments.getOrDefault(parent, List.of()).stream()
				.anyMatch(attachment -> attachment.child() && attachment.limb().type().primary()))
				continue;
			if (limb.type().secondary()) {
				SurgicalLimbJoint primary = owners.get(parent);
				if (primary == null || primary.type() != limb.type().matchingPrimary())
					continue;
			}
			effective.add(limb);
		}
		Map<Integer, List<TableLimbAttachment>> frozenAttachments = new HashMap<>();
		attachments.forEach((component, attached) ->
			frozenAttachments.put(component, List.copyOf(attached)));
		return new TableLimbTopology(Set.copyOf(effective), Map.copyOf(componentIds),
			Map.copyOf(frozenAttachments));
	}

	private record TableLimbTopology(Set<SurgicalLimbJoint> effective,
		Map<SurgicalGlueJoint.Endpoint, Integer> components,
		Map<Integer, List<TableLimbAttachment>> attachments) {
		private static final TableLimbTopology EMPTY =
			new TableLimbTopology(Set.of(), Map.of(), Map.of());
	}

	private record TableLimbAttachment(SurgicalLimbJoint limb, boolean child) {}

	public boolean combineConnected(Player player, ItemStack honeyBottle, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isHoneyBottle(honeyBottle)
			|| !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return false;
		ComponentGroup connected = connectedGroup(subject, cubeId);
		List<SurgicalCombination.Member> members = new ArrayList<>();
		for (Map.Entry<UUID, BitSet> entry : connected.components.entrySet())
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1))
				members.add(new SurgicalCombination.Member(entry.getKey(), cube));
		if (members.size() < 2)
			return false;

		Set<SurgicalCombination> replaced = new HashSet<>();
		for (SurgicalCombination existing : allCombinations()) {
			boolean intersects = false;
			for (SurgicalCombination.Member member : existing.members())
				if (connected.contains(member)) {
					intersects = true;
					break;
				}
			if (intersects)
				replaced.add(existing);
		}
		if (replaced.size() == 1) {
			SurgicalCombination existing = replaced.iterator().next();
			if (existing.members().size() == members.size() && existing.members().containsAll(members))
				return false;
		}
		SurgicalCombination combination = SurgicalCombination.create(members);
		if (combination == null || !canPayInteractionCost(honeyBottle, 1, player))
			return false;
		for (SurgicalSubject candidate : subjects)
			candidate.removeCombinations(replaced);
		attachCombination(combination);
		consumeHoneyBottle(player, honeyBottle, hand);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.8f, 1.1f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combine_success"), true);
		return true;
	}

	public boolean breakCombination(Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShears(shears)
			|| !subject.initializeOrMatchTopology(observedCubeCount, observedSeams))
			return false;
		SurgicalCombination combination = subject.combinationContaining(cubeId);
		if (combination == null || !externalCombinationJoints(combination).isEmpty()
			|| !canPayInteractionCost(shears, 1, player))
			return false;
		Set<SurgicalCombination> removed = Set.of(combination);
		for (SurgicalSubject candidate : subjects)
			candidate.removeCombinations(removed);
		damageInteractionTool(shears, 1, player, hand);
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.05f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combination_broken"), true);
		return true;
	}

	public boolean detachCombination(Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShears(shears)
			|| !subject.initializeOrMatchTopology(observedCubeCount, observedSeams))
			return false;
		SurgicalCombination combination = subject.combinationContaining(cubeId);
		if (combination == null || !canPayInteractionCost(shears, 1, player))
			return false;
		List<SurgicalGlueJoint> external = externalCombinationJoints(combination);
		if (external.isEmpty())
			return false;
		Set<SurgicalGlueJoint> removed = Set.copyOf(external);
		for (SurgicalSubject candidate : subjects)
			candidate.removeGlueJoints(removed);
		damageInteractionTool(shears, 1, player, hand);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.1f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combination_detached"), true);
		return true;
	}

	private static void consumeHoneyBottle(Player player, ItemStack honeyBottle, InteractionHand hand) {
		if (!consumeInteractionItems() || player.getAbilities().instabuild)
			return;
		if (SurgicalKitItem.isKit(honeyBottle)) {
			damageInteractionTool(honeyBottle, 1, player, hand);
			return;
		}
		honeyBottle.shrink(1);
		ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
		if (honeyBottle.isEmpty()) {
			player.setItemInHand(hand, emptyBottle);
			return;
		}
		if (!player.getInventory().add(emptyBottle))
			player.drop(emptyBottle, false);
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
		SurgicalSubject first = getSubject(firstSubjectId);
		SurgicalSubject second = getSubject(secondSubjectId);
		if (!SurgicalKitItem.isSlimeBall(slimeBall) || first == null || second == null
			|| !first.initializeOrMatchTopology(firstObservedCubeCount, firstObservedSeams)
			|| !second.initializeOrMatchTopology(secondObservedCubeCount, secondObservedSeams)
			|| !canAddSlimeSeamTargets(firstSubjectId, firstCubeId, secondSubjectId, secondCubeId)
			|| !canPayInteractionCost(slimeBall, 1, player))
			return false;

		SurgicalGlueJoint joint = SurgicalGlueJoint.of(
			new SurgicalGlueJoint.Endpoint(first.persistentId(), firstCubeId),
			new SurgicalGlueJoint.Endpoint(second.persistentId(), secondCubeId));
		attachJoint(joint);
		if (consumeInteractionItems() && !player.getAbilities().instabuild)
			consumeMaterial(slimeBall, player, hand);
		setChangedAndSync();
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_PLACE,
				SoundSource.BLOCKS, 0.6f, 1.05f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.slime_seam_success"), true);
		return true;
	}

	/** Client-safe structural half of slime-seam validation; current cube geometry is checked client-side. */
	public boolean canAddSlimeSeamTargets(int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		SurgicalSubject first = getSubject(firstSubjectId);
		SurgicalSubject second = getSubject(secondSubjectId);
		if (first == null || second == null || !first.validPresentCube(firstCubeId)
			|| !second.validPresentCube(secondCubeId)
			|| first == second && firstCubeId == secondCubeId
			|| allGlueJoints().size() >= SurgicalAssembly.MAX_SEAMS)
			return false;
		SurgicalConnectionGraph<UUID> abstractGraph = connectionGraph(Set.of(), Map.of(), false);
		return abstractGraph != null
			&& !abstractGraph.directConnections(first.persistentId(), firstCubeId)
				.contains(second.persistentId(), secondCubeId);
	}

	public boolean glueComponents(Player player, ItemStack glue, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		SurgicalLayPose targetPose, List<SurgicalTableGluePacket.Move> moves,
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves, SurgicalGlueTransform replayTransform,
		SurgicalGlueContact anchorContact,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		boolean smartGlue = SurgicalKitItem.isSmartGlue(glue);
		if (replayTransform == null || anchorContact == null
			|| !smartGlue && !replayTransform.isIdentity()
			|| !canPayInteractionCost(glue, 1, player))
			return false;
		ValidatedGluePlan plan = validateGluePlan(firstSubjectId, firstCubeId,
			secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			smartGlue);
		if (plan == null)
			return false;
		return applyGluePlan(player, glue, hand, firstCubeId, secondCubeId, targetPose,
			firstLayout, secondLayout, plan, replayTransform, anchorContact, true);
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
		SurgicalSubject referenceSubject = getSubject(referenceSubjectId);
		SurgicalSubject referenceAnchorSubject = getSubjectByPersistentId(referenceAnchorSubjectKey);
		SurgicalSubject mirroredAnchorSubject = getSubject(secondSubjectId);
		if (referenceSubject == null || referenceAnchorSubject == null || mirroredAnchorSubject == null
			|| !referenceSubject.validPresentCube(referenceCubeId)
			|| !referenceAnchorSubject.validPresentCube(referenceAnchorCubeId))
			return false;
		SurgicalGlueJoint.Endpoint referenceEndpoint =
			new SurgicalGlueJoint.Endpoint(referenceSubject.persistentId(), referenceCubeId);
		SurgicalGlueJoint.Endpoint referenceAnchor =
			new SurgicalGlueJoint.Endpoint(referenceAnchorSubjectKey, referenceAnchorCubeId);
		SurgicalGlueJoint referenceJoint = findGlueJoint(referenceSubject, referenceEndpoint, referenceAnchor);
		if (referenceJoint == null || replayTransform == null || anchorContact == null
			|| isInternalCombinationJoint(referenceJoint))
			return false;
		SurgicalGlueTransform recorded = referenceJoint.replayFrom(referenceEndpoint);
		if ((referenceJoint.replay() != null && recorded == null)
			|| (recorded == null && !replayTransform.isIdentity())
			|| (recorded != null && !recorded.mirrorMagnitudeMatches(replayTransform)))
			return false;
		SurgicalCombination combination = referenceAnchorSubject.combinationContaining(referenceAnchorCubeId);
		boolean validAnchor = singleCube
			? mirroredAnchorSubject.persistentId().equals(referenceAnchorSubjectKey)
				&& secondCubeId == referenceAnchorCubeId
			: combination != null && combination.contains(mirroredAnchorSubject.persistentId(), secondCubeId);
		if (!validAnchor)
			return false;

		ValidatedGluePlan plan = validateGluePlan(firstSubjectId, firstCubeId,
			secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			!replayTransform.isIdentity());
		if (plan == null)
			return false;
		return applyGluePlan(player, wand, hand, firstCubeId, secondCubeId, targetPose,
			firstLayout, secondLayout, plan, replayTransform, anchorContact, false);
	}

	@Nullable
	private static SurgicalGlueJoint findGlueJoint(SurgicalSubject subject,
		SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second) {
		for (SurgicalGlueJoint joint : subject.glueJoints())
			if (joint.touches(first.subjectKey(), first.cubeId())
				&& joint.touches(second.subjectKey(), second.cubeId()))
				return joint;
		return null;
	}

	private boolean applyGluePlan(Player player, ItemStack tool, InteractionHand hand,
		int firstCubeId, int secondCubeId, SurgicalLayPose targetPose,
		SurgicalTableLayout.Proposal firstLayout, SurgicalTableLayout.Proposal secondLayout,
		ValidatedGluePlan plan, SurgicalGlueTransform replayTransform,
		SurgicalGlueContact anchorContact, boolean damageTool) {
		SurgicalSubject first = plan.first;
		SurgicalSubject second = plan.second;
		ComponentGroup moving = plan.moving;
		Map<UUID, ValidatedGlueMove> validated = plan.moves;
		Map<UUID, Map<Integer, Vec3>> validatedAnchors = plan.anchorMoves;

		// Endpoint normalization is part of the same accepted plan. Apply it only after every move
		// and anchor has passed validation, so a rejected packet cannot partially change the table.
		first.applyLayout(firstLayout);
		if (first != second)
			second.applyLayout(secondLayout);

		Set<SurgicalGlueJoint> existingJoints = allGlueJoints();
		Set<SurgicalCombination> existingCombinations = allCombinations();
		Set<SurgicalLimbJoint> existingLimbs = allLimbJoints();
		Map<UUID, ExtractedSubject> extracted = new HashMap<>();
		for (Map.Entry<UUID, BitSet> entry : moving.components.entrySet()) {
			SurgicalSubject original = getSubjectByPersistentId(entry.getKey());
			ValidatedGlueMove move = validated.get(entry.getKey());
			if (original == null || move == null)
				return false;
			BitSet selected = entry.getValue();
			SurgicalSubject moved = original;
			if (!selected.equals(original.presentCubes)) {
				moved = original.extract(allocateSubjectId(), selected);
				addSubject(moved);
				extracted.put(original.persistentId(), new ExtractedSubject((BitSet) selected.clone(), moved));
			}
			moved.applyGlueMove(second.placementFacing(), targetPose, move.offsets, move.rotations,
				move.layout.footprints());
		}
		for (Map.Entry<UUID, Map<Integer, Vec3>> entry : validatedAnchors.entrySet()) {
			SurgicalSubject anchoredSubject = getSubjectByPersistentId(entry.getKey());
			BitSet selected = plan.anchored.components.get(entry.getKey());
			if (anchoredSubject != null && selected != null)
				anchoredSubject.applyComponentOffsets(selected, entry.getValue());
		}

		List<SurgicalGlueJoint> remappedJoints = existingJoints.stream()
			.map(joint -> remapJoint(joint, extracted)).toList();
		for (SurgicalSubject subject : subjects)
			subject.replaceGlueJoints(List.of());
		for (SurgicalGlueJoint joint : remappedJoints)
			attachJoint(joint);
		// Glue joins motion components but never fuses their honey identities. Reattach every
		// pre-existing combination separately so the blue glue boundary remains meaningful.
		for (SurgicalSubject subject : subjects)
			subject.replaceCombinations(List.of());
		for (SurgicalCombination combination : existingCombinations) {
			SurgicalCombination remapped = remapCombination(combination, extracted);
			if (remapped != null)
				attachCombination(remapped);
		}
		for (SurgicalSubject subject : subjects)
			subject.replaceLimbJoints(List.of());
		for (SurgicalLimbJoint limb : existingLimbs)
			attachLimbJoint(new SurgicalLimbJoint(limb.type(), remapEndpoint(limb.child(), extracted),
				remapEndpoint(limb.parent(), extracted)));

		SurgicalSubject movedFirst = remappedSubject(first, firstCubeId, extracted);
		SurgicalGlueJoint joint = SurgicalGlueJoint.attached(
			new SurgicalGlueJoint.Endpoint(movedFirst.persistentId(), firstCubeId),
			new SurgicalGlueJoint.Endpoint(second.persistentId(), secondCubeId), replayTransform, anchorContact);
		attachJoint(joint);
		if (damageTool)
			damageInteractionTool(tool, 1, player, hand);
		else
			player.getCooldowns().addCooldown(tool.getItem(), 5);
		clientRenderBounds = null;
		setChangedAndSync();
		if (level != null) {
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1.0f);
			level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 0.9f);
			if (!damageTool)
				level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.35f);
		}
		return true;
	}

	private static boolean consumeInteractionItems() {
		return CBConfigs.SERVER.surgicalTable.consumeInteractionItems.get();
	}

	private static boolean canPayInteractionCost(ItemStack stack, int amount, Player player) {
		return !consumeInteractionItems() || player.getAbilities().instabuild
			|| SurgicalKitItem.hasDurability(stack, amount);
	}

	private static void consumeMaterial(ItemStack stack, Player player, InteractionHand hand) {
		if (SurgicalKitItem.isKit(stack))
			damageInteractionTool(stack, 1, player, hand);
		else
			stack.shrink(1);
	}

	private static void damageInteractionTool(ItemStack tool, int amount, Player player,
		InteractionHand hand) {
		if (consumeInteractionItems())
			tool.hurtAndBreak(amount, player, LivingEntity.getSlotForHand(hand));
	}

	/**
	 * Builds the immutable assembly that packing would create, without changing table state.
	 * The client uses this exact source ordering while baking generated combat geometry.
	 */
	@Nullable
	public SurgicalAssembly previewPackedAssembly(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return null;
		ComponentGroup group = connectedGroup(subject, cubeId);
		BitSet component = group.components.get(subject.persistentId());
		if (component == null || component.isEmpty())
			return null;
		return packedAssembly(subject, component, group, jointsWithin(group), combinationsWithin(group),
			limbsWithin(group));
	}

	@Nullable
	private SurgicalAssembly packedAssembly(SurgicalSubject subject, BitSet component,
		ComponentGroup group, Set<SurgicalGlueJoint> groupJoints,
		Set<SurgicalCombination> groupCombinations, Set<SurgicalLimbJoint> groupLimbs) {
		return groupJoints.isEmpty() && groupCombinations.isEmpty() && groupLimbs.isEmpty()
			? SurgicalAssembly.create(subject.profile(), subject.cubeCount, component,
				subject.seams, subject.cutSeams, subject.cutOrder)
			: compositeAssembly(group, groupJoints, groupCombinations, groupLimbs, subject);
	}

	public boolean canApplyGlueCut(int subjectId, int glueJointId, double moveX, double moveZ,
		SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = getSubject(subjectId);
		GlueCutState cut = subject == null ? null : glueCutState(subject, glueJointId);
		return cut != null && canApplySeparatedCut(cut.moving, cut.separates, moveX, moveZ, plane);
	}

	public boolean canApplySeamCut(int subjectId, int seamId, SurgicalTableLayout.Proposal proposal,
		double moveX, double moveZ, SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = getSubject(subjectId);
		SeamCutState cut = subject == null ? null : seamCutState(subject, seamId);
		return cut != null && validateSeamCut(subject, cut, proposal, moveX, moveZ, plane);
	}

	private boolean canApplySeparatedCut(ComponentGroup moving, boolean separates, double moveX, double moveZ,
		SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || !validCutDelta(moveX, moveZ)
			|| !separates && (Math.abs(moveX) > 1.0e-9d || Math.abs(moveZ) > 1.0e-9d))
			return false;
		return !separates || canPlaceSeparatedGroup(moving, new Vec3(moveX, 0.0d, moveZ), plane);
	}

	private boolean validateSeamCut(SurgicalSubject subject, SeamCutState cut,
		SurgicalTableLayout.Proposal proposal, double moveX, double moveZ, SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || !validCutDelta(moveX, moveZ)
			|| !cut.separates && (Math.abs(moveX) > 1.0e-9d || Math.abs(moveZ) > 1.0e-9d))
			return false;
		BitSet movedHere = cut.moving.components.getOrDefault(subject.persistentId(), new BitSet());
		Map<Integer, Vec3> expected = new HashMap<>();
		Vec3 delta = new Vec3(moveX, 0.0d, moveZ);
		for (int cube = subject.presentCubes.nextSetBit(0); cube >= 0;
			cube = subject.presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = subject.componentOffsets.getOrDefault(cube, Vec3.ZERO);
			expected.put(cube, movedHere.get(cube) ? offset.add(delta) : offset);
		}
		if (!layoutMatchesTranslations(proposal, expected)
			|| !SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cut.proposedCuts, proposal, List.of()))
			return false;

		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> fixed = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : proposal.footprints())
			(movedHere.get(footprint.componentRoot()) ? moving : fixed).add(footprint);
		for (SurgicalSubject other : subjects) {
			if (other == subject)
				continue;
			BitSet moved = cut.moving.components.get(other.persistentId());
			if (moved != null && !moved.isEmpty() && other.occupiedFootprints().isEmpty())
				return false;
			for (SurgicalTableLayout.Footprint footprint : other.occupiedFootprints()) {
				if (!other.containsFootprint(moved, footprint)) {
					fixed.add(footprint);
					continue;
				}
				SurgicalTableLayout.Footprint translated = new SurgicalTableLayout.Footprint(
					footprint.componentRoot(), footprint.minX() + moveX, footprint.minZ() + moveZ,
					footprint.maxX() + moveX, footprint.maxZ() + moveZ,
					SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
				if (!plane.workArea().contains(translated.minX(), translated.minZ(), translated.maxX(),
					translated.maxZ(), 1.0e-6d))
					return false;
				moving.add(translated);
			}
		}
		for (SurgicalTableLayout.Footprint moved : moving)
			for (SurgicalTableLayout.Footprint obstacle : fixed)
				if (moved.conflictsWith(obstacle))
					return false;
		return true;
	}

	private void translateOtherSubjects(ComponentGroup moving, UUID currentSubject, Vec3 delta) {
		for (Map.Entry<UUID, BitSet> entry : moving.components.entrySet()) {
			if (entry.getKey().equals(currentSubject))
				continue;
			SurgicalSubject subject = getSubjectByPersistentId(entry.getKey());
			if (subject != null)
				subject.translateComponent(entry.getValue(), delta);
		}
	}

	/** Uses the exact same non-mutating acceptance path as {@link #glueComponents}. */
	public boolean canGlueComponents(ItemStack glue, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalGlueTransform replayTransform,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		if (replayTransform == null)
			return false;
		return validateGluePlan(firstSubjectId, firstCubeId, secondSubjectId, secondCubeId,
			targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			SurgicalKitItem.isSmartGlue(glue) || !replayTransform.isIdentity()) != null;
	}

	@Nullable
	private ValidatedGluePlan validateGluePlan(int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout, boolean allowEditedTransforms) {
		SurgicalSubject first = getSubject(firstSubjectId);
		SurgicalSubject second = getSubject(secondSubjectId);
		if (first == null || second == null || !first.validPresentCube(firstCubeId)
			|| !second.validPresentCube(secondCubeId) || targetPose == null
			|| !targetPose.equals(second.layPose()) || !plane.valid()
			|| !validateGlueLayout(first, plane, firstLayout)
			|| first != second && !validateGlueLayout(second, plane, secondLayout))
			return null;

		ComponentGroup moving = connectedGroup(first, firstCubeId);
		ComponentGroup anchored = connectedGroup(second, secondCubeId);
		Map<UUID, ValidatedGlueMove> validated = validateGlueMoves(moving, anchored, moves, plane,
			allowEditedTransforms);
		Map<UUID, Map<Integer, Vec3>> validatedAnchors = validateGlueAnchors(anchored, anchorMoves);
		if (moving.intersects(anchored) || validated == null || validatedAnchors == null)
			return null;
		return new ValidatedGluePlan(first, second, moving, anchored, validated, validatedAnchors);
	}

	@Nullable
	private Map<UUID, ValidatedGlueMove> validateGlueMoves(ComponentGroup moving, ComponentGroup anchored,
		List<SurgicalTableGluePacket.Move> moves, SurgicalTablePlane.Plane plane,
		boolean allowPerCubeOffsets) {
		if (moves == null || moves.size() != moving.components.size())
			return null;
		int requiredSplits = 0;
		Map<UUID, ValidatedGlueMove> validated = new HashMap<>();
		List<SurgicalTableLayout.Footprint> obstacles = glueMoveObstacles(moving, anchored);
		for (SurgicalTableGluePacket.Move move : moves) {
			SurgicalSubject subject = getSubject(move.subjectId());
			if (subject == null || validated.containsKey(subject.persistentId()))
				return null;
			BitSet selected = moving.components.get(subject.persistentId());
			if (selected == null || selected.isEmpty())
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (SurgicalTableGluePacket.CubeTranslation translation : move.translations())
				if (!translation.valid() || !selected.get(translation.cubeId())
					|| offsets.putIfAbsent(translation.cubeId(), translation.offset()) != null
					|| rotations.putIfAbsent(translation.cubeId(), translation.rotation()) != null)
					return null;
			boolean permitPerCubeOffsets = allowPerCubeOffsets
				|| hasNonUniformStoredOffsets(subject, selected);
			if (offsets.size() != selected.cardinality()
				|| !layoutMatchesTranslations(move.layout(), offsets)
				|| !permitPerCubeOffsets && !componentYTranslationsMatch(subject, selected, offsets)
				|| !componentRotationsMatch(subject, selected, rotations)
				|| !(permitPerCubeOffsets
					? SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount, selected,
						subject.seams, subject.cutSeams, move.layout(), obstacles)
					: SurgicalTableLayout.validateGlueComponents(plane, subject.cubeCount, selected,
						subject.seams, subject.cutSeams, move.layout(), obstacles)))
				return null;
			if (!selected.equals(subject.presentCubes))
				requiredSplits++;
			validated.put(subject.persistentId(), new ValidatedGlueMove(Map.copyOf(offsets),
				Map.copyOf(rotations), move.layout()));
		}
		return validated.keySet().equals(moving.components.keySet())
			&& subjects.size() <= MAX_SUBJECTS - requiredSplits ? Map.copyOf(validated) : null;
	}

	@Nullable
	private Map<UUID, Map<Integer, Vec3>> validateGlueAnchors(ComponentGroup anchored,
		List<SurgicalTableGluePacket.AnchorMove> moves) {
		if (moves == null || moves.size() != anchored.components.size())
			return null;
		Map<UUID, Map<Integer, Vec3>> validated = new HashMap<>();
		Double commonLift = null;
		for (SurgicalTableGluePacket.AnchorMove move : moves) {
			SurgicalSubject subject = getSubject(move.subjectId());
			if (subject == null || validated.containsKey(subject.persistentId()))
				return null;
			BitSet selected = anchored.components.get(subject.persistentId());
			if (selected == null || selected.isEmpty())
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			for (SurgicalTableGluePacket.CubeTranslation translation : move.translations()) {
				Vec3 existing = subject.componentOffsets.getOrDefault(translation.cubeId(), Vec3.ZERO);
				SurgicalCubeRotation existingRotation = subject.componentRotations.getOrDefault(
					translation.cubeId(), SurgicalCubeRotation.IDENTITY);
				double lift = translation.offset().y - existing.y;
				if (!translation.valid() || !selected.get(translation.cubeId())
					|| Math.abs(translation.offset().x - existing.x) > 1.0e-6d
					|| Math.abs(translation.offset().z - existing.z) > 1.0e-6d
					|| commonLift != null && Math.abs(lift - commonLift) > 1.0e-6d
					|| !translation.rotation().approximatelyEquals(existingRotation, 1.0e-6d)
					|| offsets.putIfAbsent(translation.cubeId(), translation.offset()) != null)
					return null;
				if (commonLift == null)
					commonLift = lift;
			}
			if (offsets.size() != selected.cardinality())
				return null;
			validated.put(subject.persistentId(), Map.copyOf(offsets));
		}
		return validated.keySet().equals(anchored.components.keySet()) ? Map.copyOf(validated) : null;
	}

	private static boolean layoutMatchesTranslations(SurgicalTableLayout.Proposal layout,
		Map<Integer, Vec3> offsets) {
		if (layout.offsets().size() != offsets.size())
			return false;
		Set<Integer> seen = new HashSet<>();
		for (SurgicalTableLayout.CubeOffset proposed : layout.offsets()) {
			Vec3 expected = offsets.get(proposed.cubeId());
			if (expected == null || !seen.add(proposed.cubeId())
				|| Math.abs(expected.x - proposed.x()) > 1.0e-6d
				|| Math.abs(expected.y - proposed.y()) > 1.0e-6d
				|| Math.abs(expected.z - proposed.z()) > 1.0e-6d)
				return false;
		}
		return true;
	}

	private static boolean componentYTranslationsMatch(SurgicalSubject subject, BitSet selected,
		Map<Integer, Vec3> offsets) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			double expected = offsets.get(component.nextSetBit(0)).y;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				if (Math.abs(offsets.get(cube).y - expected) > 1.0e-6d)
					return false;
		}
		return true;
	}

	public boolean canApplyComponentLayout(int subjectId, BitSet cutSeams,
		SurgicalTableLayout.Proposal proposal, SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = getSubject(subjectId);
		List<SurgicalTableLayout.Footprint> occupied = subject == null ? null
			: occupiedForValidation(plane, connectedSubjectIds(subjectId));
		return subject != null && occupied != null
			&& validateComponentLayout(subject, cutSeams, proposal, plane, occupied);
	}

	private static boolean validateComponentLayout(SurgicalSubject subject, BitSet cutSeams,
		SurgicalTableLayout.Proposal proposal, SurgicalTablePlane.Plane plane,
		List<SurgicalTableLayout.Footprint> occupied) {
		return hasNonUniformStoredOffsets(subject, subject.presentCubes)
			? SurgicalTableLayout.validateTransformedComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cutSeams, proposal, occupied)
			: SurgicalTableLayout.validateComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cutSeams, proposal, occupied);
	}

	/** Whether a previously edited native component already needs distinct per-cube translations. */
	private static boolean hasNonUniformStoredOffsets(SurgicalSubject subject, BitSet selected) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			Vec3 expected = subject.componentOffsets.getOrDefault(component.nextSetBit(0), Vec3.ZERO);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				Vec3 actual = subject.componentOffsets.getOrDefault(cube, Vec3.ZERO);
				if (Math.abs(actual.x - expected.x) > 1.0e-6d
					|| Math.abs(actual.y - expected.y) > 1.0e-6d
					|| Math.abs(actual.z - expected.z) > 1.0e-6d)
					return true;
			}
		}
		return false;
	}

	private static boolean componentRotationsMatch(SurgicalSubject subject, BitSet selected,
		Map<Integer, SurgicalCubeRotation> rotations) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			SurgicalCubeRotation expected = rotations.get(component.nextSetBit(0));
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				if (expected == null || !expected.approximatelyEquals(rotations.get(cube), 1.0e-6d))
					return false;
		}
		return true;
	}

	private List<SurgicalTableLayout.Footprint> glueMoveObstacles(ComponentGroup moving,
		ComponentGroup anchored) {
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		for (SurgicalSubject subject : subjects) {
			BitSet moved = moving.components.get(subject.persistentId());
			BitSet fixed = anchored.components.get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint)
					&& !subject.containsFootprint(fixed, footprint))
					obstacles.add(footprint);
		}
		return List.copyOf(obstacles);
	}

	private static SurgicalGlueJoint remapJoint(SurgicalGlueJoint joint,
		Map<UUID, ExtractedSubject> extracted) {
		SurgicalGlueJoint.Replay replay = joint.replay() == null ? null
			: new SurgicalGlueJoint.Replay(remapEndpoint(joint.replay().moving(), extracted),
				joint.replay().transform(), joint.replay().anchorContact());
		return SurgicalGlueJoint.of(remapEndpoint(joint.first(), extracted),
			remapEndpoint(joint.second(), extracted), replay);
	}

	private static SurgicalGlueJoint.Endpoint remapEndpoint(SurgicalGlueJoint.Endpoint endpoint,
		Map<UUID, ExtractedSubject> extracted) {
		ExtractedSubject moved = extracted.get(endpoint.subjectKey());
		return moved != null && moved.cubes.get(endpoint.cubeId())
			? new SurgicalGlueJoint.Endpoint(moved.subject.persistentId(), endpoint.cubeId()) : endpoint;
	}

	@Nullable
	private static SurgicalCombination remapCombination(SurgicalCombination combination,
		Map<UUID, ExtractedSubject> extracted) {
		List<SurgicalCombination.Member> members = new ArrayList<>(combination.members().size());
		for (SurgicalCombination.Member member : combination.members()) {
			ExtractedSubject moved = extracted.get(member.subjectKey());
			UUID subjectKey = moved != null && moved.cubes.get(member.cubeId())
				? moved.subject.persistentId() : member.subjectKey();
			members.add(new SurgicalCombination.Member(subjectKey, member.cubeId()));
		}
		return SurgicalCombination.create(combination.id(), members);
	}

	private static SurgicalSubject remappedSubject(SurgicalSubject original, int cubeId,
		Map<UUID, ExtractedSubject> extracted) {
		ExtractedSubject moved = extracted.get(original.persistentId());
		return moved != null && moved.cubes.get(cubeId) ? moved.subject : original;
	}

	private void attachJoint(SurgicalGlueJoint joint) {
		SurgicalSubject first = getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject second = getSubjectByPersistentId(joint.second().subjectKey());
		if (first == null || second == null)
			return;
		first.addGlueJoint(joint);
		if (second != first)
			second.addGlueJoint(joint);
	}

	private void attachCombination(SurgicalCombination combination) {
		Set<UUID> attached = new HashSet<>();
		for (SurgicalCombination.Member member : combination.members()) {
			if (!attached.add(member.subjectKey()))
				continue;
			SurgicalSubject subject = getSubjectByPersistentId(member.subjectKey());
			if (subject != null)
				subject.addCombination(combination);
		}
	}

	private record ValidatedGlueMove(Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations,
		SurgicalTableLayout.Proposal layout) {}

	private record ValidatedGluePlan(SurgicalSubject first, SurgicalSubject second,
		ComponentGroup moving, ComponentGroup anchored,
		Map<UUID, ValidatedGlueMove> moves, Map<UUID, Map<Integer, Vec3>> anchorMoves) {}

	private record ExtractedSubject(BitSet cubes, SurgicalSubject subject) {}

	public boolean validateGlueLayout(SurgicalSubject subject, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal) {
		List<SurgicalTableLayout.Footprint> occupied = occupiedForValidation(plane,
			connectedSubjectIds(subject.id()));
		if (occupied == null || !layoutMatchesStoredOffsets(subject, proposal)
			|| !SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, subject.cutSeams, proposal, occupied))
			return false;
		return true;
	}

	/** The endpoint layout is a refreshed footprint for current server state, not another move. */
	private static boolean layoutMatchesStoredOffsets(SurgicalSubject subject,
		SurgicalTableLayout.Proposal proposal) {
		if (proposal.offsets().size() != subject.presentCubes.cardinality())
			return false;
		Map<Integer, Vec3> proposedOffsets = new HashMap<>();
		for (SurgicalTableLayout.CubeOffset proposed : proposal.offsets()) {
			if (!subject.presentCubes.get(proposed.cubeId())
				|| proposedOffsets.putIfAbsent(proposed.cubeId(),
					new Vec3(proposed.x(), proposed.y(), proposed.z())) != null)
				return false;
			Vec3 expected = subject.componentOffsets.getOrDefault(proposed.cubeId(), Vec3.ZERO);
			if (Math.abs(proposed.x() - expected.x) > 1.0e-6d
				|| Math.abs(proposed.z() - expected.z) > 1.0e-6d)
				return false;
		}
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, subject.presentCubes,
			subject.seams, subject.cutSeams)) {
			int root = component.nextSetBit(0);
			double lift = proposedOffsets.get(root).y
				- subject.componentOffsets.getOrDefault(root, Vec3.ZERO).y;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				double cubeLift = proposedOffsets.get(cube).y
					- subject.componentOffsets.getOrDefault(cube, Vec3.ZERO).y;
				if (Math.abs(cubeLift - lift) > 1.0e-6d)
					return false;
			}
		}
		return true;
	}

	private ComponentGroup connectedGroup(SurgicalSubject startSubject, int startCube) {
		return connectedGroup(startSubject, startCube, null);
	}

	private ComponentGroup connectedGroup(SurgicalSubject startSubject, int startCube,
		@Nullable SurgicalGlueJoint excluded) {
		SurgicalConnectionGraph<UUID> graph = connectionGraph(excluded);
		return graph == null ? new ComponentGroup(Map.of())
			: new ComponentGroup(graph.componentContaining(startSubject.persistentId(), startCube).members());
	}

	/** All cubes joined through the unified native-seam/glue/combination graph, keyed by subject id. */
	public Map<Integer, BitSet> connectedComponents(int subjectId, int cubeId) {
		SurgicalSubject start = getSubject(subjectId);
		if (start == null || !start.validPresentCube(cubeId))
			return Map.of();
		ComponentGroup group = connectedGroup(start, cubeId);
		Map<Integer, BitSet> result = new HashMap<>();
		for (SurgicalSubject subject : subjects) {
			BitSet included = group.components.get(subject.persistentId());
			if (included != null && !included.isEmpty())
				result.put(subject.id(), (BitSet) included.clone());
		}
		return Map.copyOf(result);
	}

	/** Client-side topology-aware variant for untouched subjects that are still lazily initialized. */
	public Map<Integer, BitSet> connectedComponents(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject start = getSubject(subjectId);
		if (start == null || !start.initializeOrMatchTopology(observedCubeCount, observedSeams))
			return Map.of();
		return connectedComponents(subjectId, cubeId);
	}

	/** The clicked cube and all cubes joined to it by one native or explicit connection edge. */
	public Map<Integer, BitSet> directConnections(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject start = getSubject(subjectId);
		if (start == null || !start.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !start.validPresentCube(cubeId))
			return Map.of();
		SurgicalConnectionGraph<UUID> graph = connectionGraph(null);
		return graph == null ? Map.of()
			: componentsBySubjectId(new ComponentGroup(
				graph.directConnections(start.persistentId(), cubeId).members()));
	}

	/** Connectivity after Ctrl-shears removes every native and glue edge touching {@code cutCube}. */
	public Map<Integer, BitSet> connectedComponentsAfterCuttingCube(int subjectId, int startCube, int cutCube,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(startCube) || !subject.validPresentCube(cutCube))
			return Map.of();
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if (seam.first() == cutCube || seam.second() == cutCube)
				proposedCuts.set(seamId);
		}
		Set<SurgicalGlueJoint> removedJoints = new HashSet<>();
		for (SurgicalGlueJoint joint : allGlueJoints())
			if (joint.touches(subject.persistentId(), cutCube))
				removedJoints.add(joint);
		SurgicalConnectionGraph<UUID> graph = connectionGraph(removedJoints,
			Map.of(subject.persistentId(), proposedCuts));
		return graph == null ? Map.of()
			: componentsBySubjectId(new ComponentGroup(
				graph.componentContaining(subject.persistentId(), startCube).members()));
	}

	/** Complete post-cut groups for the Ctrl-shears operation, largest and stable remainder first. */
	@Nullable
	public BatchCutPlan batchCutPlan(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || !subject.initializeOrMatchTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return null;
		BatchCutState state = batchCutState(subject, cubeId);
		if (state == null)
			return null;
		List<Map<Integer, BitSet>> groups = state.groups.stream()
			.map(this::componentsBySubjectId).toList();
		return new BatchCutPlan(state.proposedCuts, groups);
	}

	@Nullable
	private BatchCutState batchCutState(SurgicalSubject subject, int cubeId) {
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		List<Integer> seamIds = new ArrayList<>();
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			if (subject.cutSeams.get(seamId))
				continue;
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if ((seam.first() == cubeId || seam.second() == cubeId)
				&& subject.validPresentCube(seam.first()) && subject.validPresentCube(seam.second())) {
				proposedCuts.set(seamId);
				seamIds.add(seamId);
			}
		}
		Set<SurgicalGlueJoint> glueCuts = new HashSet<>();
		for (SurgicalGlueJoint joint : allGlueJoints())
			if (joint.touches(subject.persistentId(), cubeId))
				glueCuts.add(joint);
		int cutCount = seamIds.size() + glueCuts.size();
		if (cutCount == 0)
			return null;

		SurgicalConnectionGraph<UUID> before = connectionGraph(null);
		SurgicalConnectionGraph<UUID> after = connectionGraph(glueCuts,
			Map.of(subject.persistentId(), proposedCuts));
		if (before == null || after == null)
			return null;
		ComponentGroup affected = new ComponentGroup(
			before.componentContaining(subject.persistentId(), cubeId).members());
		if (affected.components.isEmpty())
			return null;
		List<ComponentGroup> groups = new ArrayList<>();
		int covered = 0;
		for (SurgicalConnectionGraph.Component<UUID> component : after.components()) {
			ComponentGroup group = new ComponentGroup(component.members());
			if (!group.intersects(affected))
				continue;
			groups.add(group);
			covered += componentSize(group);
		}
		if (groups.isEmpty() || covered != componentSize(affected)
			|| groups.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		return new BatchCutState(proposedCuts, List.copyOf(seamIds), Set.copyOf(glueCuts),
			List.copyOf(groups), cutCount);
	}

	@Nullable
	public SeamCutPlan seamCutPlan(int subjectId, int seamId) {
		SurgicalSubject subject = getSubject(subjectId);
		SeamCutState state = subject == null ? null : seamCutState(subject, seamId);
		return state == null ? null : new SeamCutPlan(componentsBySubjectId(state.moving),
			state.proposedCuts, state.separates);
	}

	@Nullable
	private SeamCutState seamCutState(SurgicalSubject subject, int seamId) {
		if (seamId < 0 || seamId >= subject.seams.size() || subject.cutSeams.get(seamId))
			return null;
		SurgicalAssembly.Seam seam = subject.seams.get(seamId);
		if (!subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second())
			|| isInternalCombinationSeam(subject.id(), seam))
			return null;
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		proposedCuts.set(seamId);
		SurgicalConnectionGraph<UUID> graph = connectionGraph(Set.of(),
			Map.of(subject.persistentId(), proposedCuts));
		if (graph == null)
			return null;
		ComponentGroup first = new ComponentGroup(
			graph.componentContaining(subject.persistentId(), seam.first()).members());
		ComponentGroup second = new ComponentGroup(
			graph.componentContaining(subject.persistentId(), seam.second()).members());
		if (first.intersects(second))
			return new SeamCutState(proposedCuts, new ComponentGroup(Map.of()), false);
		ComponentGroup moving = componentSize(first) < componentSize(second) ? first : second;
		return new SeamCutState(proposedCuts, moving, true);
	}

	@Nullable
	public GlueCutPlan glueCutPlan(int subjectId, int glueJointId) {
		SurgicalSubject subject = getSubject(subjectId);
		GlueCutState state = subject == null ? null : glueCutState(subject, glueJointId);
		return state == null ? null : new GlueCutPlan(state.joint,
			componentsBySubjectId(state.moving), state.separates);
	}

	@Nullable
	private GlueCutState glueCutState(SurgicalSubject subject, int glueJointId) {
		if (glueJointId < 0 || glueJointId >= subject.glueJoints().size())
			return null;
		SurgicalGlueJoint joint = subject.glueJoints().get(glueJointId);
		if (!joint.touches(subject.persistentId()))
			return null;
		if (isInternalCombinationJoint(joint))
			return null;
		SurgicalSubject firstSubject = getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject secondSubject = getSubjectByPersistentId(joint.second().subjectKey());
		if (firstSubject == null || secondSubject == null
			|| !firstSubject.validPresentCube(joint.first().cubeId())
			|| !secondSubject.validPresentCube(joint.second().cubeId()))
			return null;
		ComponentGroup first = connectedGroup(firstSubject, joint.first().cubeId(), joint);
		ComponentGroup second = connectedGroup(secondSubject, joint.second().cubeId(), joint);
		if (first.intersects(second))
			return new GlueCutState(joint, new ComponentGroup(Map.of()), false);
		ComponentGroup moving = componentSize(first) < componentSize(second) ? first : second;
		return new GlueCutState(joint, moving, true);
	}

	private Map<Integer, BitSet> componentsBySubjectId(ComponentGroup group) {
		Map<Integer, BitSet> result = new HashMap<>();
		for (SurgicalSubject subject : subjects) {
			BitSet component = group.components.get(subject.persistentId());
			if (component != null && !component.isEmpty())
				result.put(subject.id(), (BitSet) component.clone());
		}
		return Map.copyOf(result);
	}

	private static int componentSize(ComponentGroup group) {
		int size = 0;
		for (BitSet component : group.components.values())
			size += component.cardinality();
		return size;
	}

	private boolean canPlaceSeparatedGroup(ComponentGroup moving, Vec3 delta,
		SurgicalTablePlane.Plane plane) {
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> translated = new ArrayList<>();
		for (SurgicalSubject subject : subjects) {
			if (subject.occupiedFootprints().isEmpty())
				return false;
			BitSet moved = moving.components.get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (!subject.containsFootprint(moved, footprint)) {
					obstacles.add(footprint);
					continue;
				}
				SurgicalTableLayout.Footprint placed = new SurgicalTableLayout.Footprint(
					footprint.componentRoot(), footprint.minX() + delta.x, footprint.minZ() + delta.z,
					footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
					SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
				if (!plane.workArea().contains(placed.minX(), placed.minZ(), placed.maxX(), placed.maxZ(), 1.0e-6d))
					return false;
				translated.add(placed);
			}
		}
		if (translated.isEmpty())
			return false;
		for (SurgicalTableLayout.Footprint placed : translated)
			for (SurgicalTableLayout.Footprint obstacle : obstacles)
				if (placed.conflictsWith(obstacle))
					return false;
		return true;
	}

	private static boolean validCutDelta(double x, double z) {
		double bound = SurgicalTablePlane.MAX_TILES + 2.0d;
		return Double.isFinite(x) && Double.isFinite(z) && Math.abs(x) <= bound && Math.abs(z) <= bound;
	}

	@Nullable
	private SurgicalConnectionGraph<UUID> connectionGraph(@Nullable SurgicalGlueJoint excluded) {
		if (excluded != null)
			return connectionGraph(Set.of(excluded), Map.of());
		// The unexcluded graph is what connectedGroup, connectedSubjectIds and directConnections all
		// ask for, several times per edit and — through the cut and selection previews — several times
		// per frame. Rebuilding it each time is what made unifying glue and native seams expensive, so
		// it is memoized against a signature of everything it is derived from.
		long signature = connectionSignature();
		if (cachedConnectionValid && cachedConnectionSignature == signature)
			return cachedConnectionGraph;
		cachedConnectionGraph = buildConnectionGraph(Set.of(), Map.of(), true);
		cachedConnectionSignature = signature;
		cachedConnectionValid = true;
		return cachedConnectionGraph;
	}

	@Nullable
	private SurgicalConnectionGraph<UUID> connectionGraph(Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides) {
		return connectionGraph(excluded, cutOverrides, true);
	}

	/**
	 * A one-entry memo for the parameterized graphs. The cut and glue paths ask for the same excluded
	 * joints and cut overrides two to four times inside a single edit — {@code cutGlueJoint} alone runs
	 * {@code glueCutState} twice, each building two graphs — and the client previews repeat the same
	 * request every frame while a cut is held.
	 */
	@Nullable
	private SurgicalConnectionGraph<UUID> connectionGraph(Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides, boolean includeCombinations) {
		long signature = connectionSignature();
		if (variantConnectionValid && variantConnectionSignature == signature
			&& variantConnectionCombinations == includeCombinations
			&& variantConnectionExcluded.equals(excluded)
			&& variantConnectionOverrides.equals(cutOverrides))
			return variantConnectionGraph;
		variantConnectionGraph = buildConnectionGraph(excluded, cutOverrides, includeCombinations);
		variantConnectionSignature = signature;
		variantConnectionExcluded = Set.copyOf(excluded);
		// The override BitSets belong to the caller, so they are copied rather than aliased.
		Map<UUID, BitSet> overrides = new HashMap<>();
		cutOverrides.forEach((key, cuts) -> overrides.put(key, (BitSet) cuts.clone()));
		variantConnectionOverrides = overrides;
		variantConnectionCombinations = includeCombinations;
		variantConnectionValid = true;
		return variantConnectionGraph;
	}

	/** @see SurgicalSubject#connectionSignature() */
	private long connectionSignature() {
		long signature = subjects.size();
		for (SurgicalSubject subject : subjects) {
			signature = signature * 31L + subject.persistentId().hashCode();
			signature = signature * 31L + subject.connectionSignature();
		}
		return signature;
	}

	@Nullable
	private SurgicalConnectionGraph<UUID> buildConnectionGraph(Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides, boolean includeCombinations) {
		List<SurgicalConnectionGraph.Body<UUID>> bodies = new ArrayList<>();
		for (SurgicalSubject subject : subjects)
			if (SurgicalAssembly.validTopology(subject.cubeCount, subject.seams))
				bodies.add(new SurgicalConnectionGraph.Body<>(subject.persistentId(), subject.cubeCount,
					subject.presentCubes, subject.seams,
					cutOverrides.getOrDefault(subject.persistentId(), subject.cutSeams)));
		if (bodies.isEmpty())
			return null;

		List<SurgicalConnectionGraph.Link<UUID>> links = new ArrayList<>();
		for (SurgicalGlueJoint joint : allGlueJoints()) {
			if (excluded.contains(joint))
				continue;
			links.add(new SurgicalConnectionGraph.Link<>(joint.first().subjectKey(), joint.first().cubeId(),
				joint.second().subjectKey(), joint.second().cubeId()));
		}
		if (includeCombinations)
			for (SurgicalCombination combination : allCombinations()) {
				SurgicalCombination.Member anchor = combination.members().getFirst();
				for (SurgicalCombination.Member member : combination.members().subList(1,
					combination.members().size()))
					links.add(new SurgicalConnectionGraph.Link<>(anchor.subjectKey(), anchor.cubeId(),
						member.subjectKey(), member.cubeId()));
			}
		return SurgicalConnectionGraph.create(bodies, links);
	}

	private Set<SurgicalGlueJoint> allGlueJoints() {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalSubject subject : subjects)
			joints.addAll(subject.glueJoints());
		return joints;
	}

	private Set<SurgicalCombination> allCombinations() {
		Set<SurgicalCombination> combinations = new HashSet<>();
		for (SurgicalSubject subject : subjects)
			combinations.addAll(subject.combinations());
		return combinations;
	}

	@Nullable
	public SurgicalCombination combinationContaining(int subjectId, int cubeId) {
		SurgicalSubject subject = getSubject(subjectId);
		return subject == null ? null : subject.combinationContaining(cubeId);
	}

	public boolean isInternalCombinationJoint(SurgicalGlueJoint joint) {
		if (joint == null)
			return false;
		for (SurgicalCombination combination : allCombinations())
			if (combination.contains(joint.first().subjectKey(), joint.first().cubeId())
				&& combination.contains(joint.second().subjectKey(), joint.second().cubeId()))
				return true;
		return false;
	}

	public boolean isInternalCombinationSeam(int subjectId, SurgicalAssembly.Seam seam) {
		SurgicalSubject subject = getSubject(subjectId);
		if (subject == null || seam == null)
			return false;
		SurgicalCombination first = subject.combinationContaining(seam.first());
		return first != null && first.contains(subject.persistentId(), seam.second());
	}

	public List<SurgicalGlueJoint> externalCombinationJoints(SurgicalCombination combination) {
		if (combination == null)
			return List.of();
		List<SurgicalGlueJoint> external = new ArrayList<>();
		for (SurgicalGlueJoint joint : allGlueJoints()) {
			boolean first = combination.contains(joint.first().subjectKey(), joint.first().cubeId());
			boolean second = combination.contains(joint.second().subjectKey(), joint.second().cubeId());
			if (first != second)
				external.add(joint);
		}
		return List.copyOf(external);
	}

	/** Subject ids that currently form one logical editing group through glue or combinations. */
	public Set<Integer> connectedSubjectIds(int subjectId) {
		if (!hasSubject(subjectId))
			return Set.of();
		return connectedSubjectGroups().getOrDefault(subjectId, Set.of(subjectId));
	}

	/**
	 * Projects cube-level links onto subject ownership and closes that graph transitively. A subject may
	 * own several disconnected native components, so reaching any one of them also has to pull in links
	 * stored on its other components; otherwise grounding can collect a link whose endpoint body was
	 * omitted. The projection is rebuilt only when the same topology signature used by the connection
	 * graph changes, then every lookup returns one shared immutable set.
	 */
	private Map<Integer, Set<Integer>> connectedSubjectGroups() {
		long signature = connectionSignature();
		if (cachedConnectedSubjectGroupsValid && cachedConnectedSubjectGroupsSignature == signature)
			return cachedConnectedSubjectGroups;

		Map<UUID, Set<UUID>> adjacency = new HashMap<>();
		for (SurgicalSubject subject : subjects)
			adjacency.put(subject.persistentId(), new HashSet<>());
		for (SurgicalGlueJoint joint : allGlueJoints())
			connectSubjectOwners(adjacency, joint.first().subjectKey(), joint.second().subjectKey());
		for (SurgicalCombination combination : allCombinations()) {
			UUID anchor = combination.members().getFirst().subjectKey();
			for (SurgicalCombination.Member member : combination.members().subList(1,
				combination.members().size()))
				connectSubjectOwners(adjacency, anchor, member.subjectKey());
		}

		Map<Integer, Set<Integer>> groups = new HashMap<>();
		Set<UUID> visited = new HashSet<>();
		for (SurgicalSubject start : subjects) {
			if (!visited.add(start.persistentId()))
				continue;
			Set<Integer> ids = new HashSet<>();
			ArrayDeque<UUID> pending = new ArrayDeque<>();
			pending.addLast(start.persistentId());
			while (!pending.isEmpty()) {
				UUID current = pending.removeFirst();
				SurgicalSubject subject = subjectsByPersistentId.get(current);
				if (subject != null)
					ids.add(subject.id());
				for (UUID neighbour : adjacency.getOrDefault(current, Set.of()))
					if (visited.add(neighbour))
						pending.addLast(neighbour);
			}
			Set<Integer> frozen = Set.copyOf(ids);
			for (int id : frozen)
				groups.put(id, frozen);
		}
		cachedConnectedSubjectGroups = Map.copyOf(groups);
		cachedConnectedSubjectGroupsSignature = signature;
		cachedConnectedSubjectGroupsValid = true;
		return cachedConnectedSubjectGroups;
	}

	private static void connectSubjectOwners(Map<UUID, Set<UUID>> adjacency, UUID first, UUID second) {
		if (first.equals(second) || !adjacency.containsKey(first) || !adjacency.containsKey(second))
			return;
		adjacency.get(first).add(second);
		adjacency.get(second).add(first);
	}

	private Set<SurgicalGlueJoint> jointsWithin(ComponentGroup group) {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalGlueJoint joint : allGlueJoints())
			if (group.contains(joint.first()) && group.contains(joint.second()))
				joints.add(joint);
		return joints;
	}

	private Set<SurgicalCombination> combinationsWithin(ComponentGroup group) {
		Set<SurgicalCombination> combinations = new HashSet<>();
		for (SurgicalCombination combination : allCombinations()) {
			boolean included = true;
			for (SurgicalCombination.Member member : combination.members())
				if (!group.contains(member)) {
					included = false;
					break;
				}
			if (included)
				combinations.add(combination);
		}
		return combinations;
	}

	@Nullable
	private SurgicalAssembly compositeAssembly(ComponentGroup group, Set<SurgicalGlueJoint> joints,
		Set<SurgicalCombination> combinations, Set<SurgicalLimbJoint> limbs, SurgicalSubject anchor) {
		List<SurgicalAssembly.Source> sources = new ArrayList<>();
		Map<UUID, Integer> sourceIds = new HashMap<>();
		for (SurgicalSubject grouped : subjects) {
			BitSet included = group.components.get(grouped.persistentId());
			if (included == null || included.isEmpty())
				continue;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (int cube = included.nextSetBit(0); cube >= 0; cube = included.nextSetBit(cube + 1)) {
				Vec3 offset = grouped.componentOffsets.get(cube);
				if (offset != null)
					offsets.put(cube, offset);
				SurgicalCubeRotation rotation = grouped.componentRotations.get(cube);
				if (rotation != null)
					rotations.put(cube, rotation);
			}
			SurgicalAssembly.Source source = SurgicalAssembly.Source.create(grouped.profile(), grouped.cubeCount,
				included, grouped.seams, grouped.cutSeams, grouped.cutOrder, grouped.placementFacing(),
				grouped.layPose(),
				new Vec3(grouped.originOffsetX() - anchor.originOffsetX(), 0.0d,
					grouped.originOffsetZ() - anchor.originOffsetZ()), offsets, rotations);
			if (source == null)
				return null;
			sourceIds.put(grouped.persistentId(), sources.size());
			sources.add(source);
		}
		List<SurgicalAssembly.Joint> encodedJoints = new ArrayList<>();
		for (SurgicalGlueJoint joint : joints) {
			Integer firstSource = sourceIds.get(joint.first().subjectKey());
			Integer secondSource = sourceIds.get(joint.second().subjectKey());
			if (firstSource == null || secondSource == null)
				return null;
			SurgicalAssembly.JointReplay replay = null;
			if (joint.replay() != null) {
				Integer movingSource = sourceIds.get(joint.replay().moving().subjectKey());
				if (movingSource == null)
					return null;
				replay = new SurgicalAssembly.JointReplay(movingSource,
					joint.replay().moving().cubeId(), joint.replay().transform(),
					joint.replay().anchorContact());
			}
			encodedJoints.add(new SurgicalAssembly.Joint(firstSource, joint.first().cubeId(),
				secondSource, joint.second().cubeId(), replay));
		}
		List<SurgicalAssembly.Combination> encodedCombinations = new ArrayList<>();
		for (SurgicalCombination combination : combinations) {
			List<SurgicalAssembly.CombinationMember> members = new ArrayList<>();
			for (SurgicalCombination.Member member : combination.members()) {
				Integer source = sourceIds.get(member.subjectKey());
				if (source == null)
					return null;
				members.add(new SurgicalAssembly.CombinationMember(source, member.cubeId()));
			}
			encodedCombinations.add(new SurgicalAssembly.Combination(combination.id(), members));
		}
		List<SurgicalAssembly.Limb> encodedLimbs = new ArrayList<>();
		for (SurgicalLimbJoint limb : limbs) {
			Integer childSource = sourceIds.get(limb.child().subjectKey());
			Integer parentSource = sourceIds.get(limb.parent().subjectKey());
			if (childSource == null || parentSource == null)
				return null;
			encodedLimbs.add(new SurgicalAssembly.Limb(limb.type(), childSource, limb.child().cubeId(),
				parentSource, limb.parent().cubeId()));
		}
		return SurgicalAssembly.createComposite(sources, encodedJoints, encodedCombinations, encodedLimbs,
			anchor.placementFacing(), anchor.layPose());
	}

	private record ComponentGroup(Map<UUID, BitSet> components) {
		private ComponentGroup {
			Map<UUID, BitSet> frozen = new HashMap<>();
			components.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			components = Map.copyOf(frozen);
		}

		private boolean contains(SurgicalGlueJoint.Endpoint endpoint) {
			BitSet cubes = components.get(endpoint.subjectKey());
			return cubes != null && cubes.get(endpoint.cubeId());
		}

		private boolean contains(SurgicalCombination.Member member) {
			BitSet cubes = components.get(member.subjectKey());
			return cubes != null && cubes.get(member.cubeId());
		}

		private boolean intersects(ComponentGroup other) {
			for (Map.Entry<UUID, BitSet> entry : components.entrySet()) {
				BitSet otherCubes = other.components.get(entry.getKey());
				if (otherCubes != null && entry.getValue().intersects(otherCubes))
					return true;
			}
			return false;
		}
	}

	private record TemporaryMoveSource(SurgicalTableBlockEntity sourceTable, ComponentGroup group,
		int freedSubjects) {}

	private record GlueCutState(SurgicalGlueJoint joint, ComponentGroup moving, boolean separates) {}
	private record SeamCutState(BitSet proposedCuts, ComponentGroup moving, boolean separates) {
		private SeamCutState {
			proposedCuts = (BitSet) proposedCuts.clone();
		}
	}

	public record SeamCutPlan(Map<Integer, BitSet> movingComponents, BitSet proposedCuts,
		boolean separates) {
		public SeamCutPlan {
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			movingComponents = Map.copyOf(frozen);
			proposedCuts = (BitSet) proposedCuts.clone();
		}
	}

	public record GlueCutPlan(SurgicalGlueJoint joint, Map<Integer, BitSet> movingComponents,
		boolean separates) {
		public GlueCutPlan {
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			movingComponents = Map.copyOf(frozen);
		}
	}

	public record BatchCutPlan(BitSet proposedCuts, List<Map<Integer, BitSet>> groups) {
		public BatchCutPlan {
			proposedCuts = (BitSet) proposedCuts.clone();
			List<Map<Integer, BitSet>> frozenGroups = new ArrayList<>(groups.size());
			for (Map<Integer, BitSet> group : groups) {
				Map<Integer, BitSet> frozen = new HashMap<>();
				group.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
				frozenGroups.add(Map.copyOf(frozen));
			}
			groups = List.copyOf(frozenGroups);
		}
	}

	private record BatchCutState(BitSet proposedCuts, List<Integer> cutSeamIds,
		Set<SurgicalGlueJoint> glueCuts, List<ComponentGroup> groups, int cutCount) {
		private BatchCutState {
			proposedCuts = (BitSet) proposedCuts.clone();
			cutSeamIds = List.copyOf(cutSeamIds);
			glueCuts = Set.copyOf(glueCuts);
			groups = List.copyOf(groups);
		}
	}

	@Nullable
	private List<SurgicalTableLayout.Footprint> occupiedForValidation(SurgicalTablePlane.Plane plane,
		int excludedSubjectId) {
		return level == null ? null : SurgicalTablePlane.occupiedFootprints(level, plane, excludedSubjectId);
	}

	@Nullable
	private List<SurgicalTableLayout.Footprint> occupiedForValidation(SurgicalTablePlane.Plane plane,
		Set<Integer> excludedSubjectIds) {
		return level == null ? null : SurgicalTablePlane.occupiedFootprints(level, plane, excludedSubjectIds);
	}

	private int allocateSubjectId() {
		while (nextSubjectId < 0 || getSubject(nextSubjectId) != null)
			nextSubjectId++;
		return nextSubjectId++;
	}

	private void adoptSubjects(BlockPos previousController, List<SurgicalSubject> migrated) {
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

	private List<SurgicalSubject> detachSubjects() {
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
		SurgicalConnectionGraph<UUID> graph = connectionGraph(null);
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
			removeTemporaryGroup(new ComponentGroup(unsupported));
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
		double minY = worldPosition.getY() + 1.0d;
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

	List<SurgicalSubject> detachSubjectsForSupport() {
		return detachSubjects();
	}

	void adoptSubjectsForSupport(BlockPos previousController, List<SurgicalSubject> migrated) {
		adoptSubjects(previousController, migrated);
	}

	void syncAfterSupportChange() {
		setChangedAndSync();
	}

	@FunctionalInterface
	private interface TilePredicate {
		boolean test(int x, int z);
	}

	private void setChangedAndSync() {
		normalizeLimbJoints();
		setChanged();
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		long started = SurgicalProfiler.begin();
		if (!subjects.isEmpty()) {
			ListTag encoded = new ListTag();
			for (SurgicalSubject subject : subjects)
				encoded.add(subject.save());
			tag.put(SUBJECTS_TAG, encoded);
		}
		tag.putInt(NEXT_SUBJECT_ID_TAG, nextSubjectId);
		super.write(tag, registries, clientPacket);
		SurgicalProfiler.end(clientPacket ? "write(sync)" : "write(save)", started);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		long started = SurgicalProfiler.begin();
		boolean previouslyHadSubjects = hasSubjects();
		Map<UUID, SurgicalSubject> previousSubjects = clientPacket
			? new HashMap<>(subjectsByPersistentId) : Map.of();
		super.read(tag, registries, clientPacket);
		List<SurgicalSubject> loaded = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		Set<UUID> persistentIds = new HashSet<>();
		if (tag.contains(SUBJECTS_TAG, Tag.TAG_LIST)) {
			ListTag encoded = tag.getList(SUBJECTS_TAG, Tag.TAG_COMPOUND);
			for (int index = 0; index < encoded.size() && loaded.size() < MAX_SUBJECTS; index++) {
				SurgicalSubject subject = SurgicalSubject.load(encoded.getCompound(index), index, Direction.NORTH);
				if (subject == null || !persistentIds.add(subject.persistentId()))
					continue;
				if (subject.id() < 0 || !ids.add(subject.id())) {
					int replacement = 0;
					while (ids.contains(replacement))
						replacement++;
					subject.setId(replacement);
					ids.add(replacement);
				}
				loaded.add(subject);
			}
		} else if (tag.contains(LEGACY_PROFILE_TAG, Tag.TAG_COMPOUND)) {
			SurgicalSubject legacy = SurgicalSubject.load(tag, 0, Direction.NORTH);
			if (legacy != null) {
				loaded.add(legacy);
				ids.add(legacy.id());
				persistentIds.add(legacy.persistentId());
			}
		}
		clearSubjects();
		addSubjects(loaded);
		nextSubjectId = Math.max(tag.getInt(NEXT_SUBJECT_ID_TAG),
			ids.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1);
		if (clientPacket) {
			clientPlane = null;
			clientPlaneBounds = null;
			clientPlaneCacheUntil = Long.MIN_VALUE;
			boolean structureChanged = subjects.size() != previousSubjects.size();
			Set<UUID> changed = adoptUnchangedSubjects(previousSubjects);
			// Normalizing before the adopt would rewrite the freshly decoded subjects that the adopt is
			// about to throw away, and both normalizers walk every subject on the table.
			if (structureChanged || !changed.isEmpty()) {
				normalizeCombinations();
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
			normalizeCombinations();
			normalizeLimbJoints();
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
		Map<Integer, Set<Integer>> groups = connectedSubjectGroups();
		for (UUID persistentId : changed) {
			SurgicalSubject subject = subjectsByPersistentId.get(persistentId);
			if (subject == null)
				continue;
			for (int subjectId : groups.getOrDefault(subject.id(), Set.of(subject.id()))) {
				SurgicalSubject dependent = getSubject(subjectId);
				if (dependent != null)
					stale.add(dependent.persistentId());
			}
		}
		return stale;
	}

	/**
	 * Every sync packet re-decodes every subject on the table, even the ones the edit never touched.
	 * Handing those back the instance the client already had keeps the model-preview and captured-
	 * geometry caches — both keyed on subject identity — warm, so only the subjects that really moved
	 * pay for a rebuild.
	 *
	 * @return the persistent ids of the subjects whose content differs from what the client already had
	 */
	private Set<UUID> adoptUnchangedSubjects(Map<UUID, SurgicalSubject> previous) {
		Set<UUID> changed = new HashSet<>();
		List<SurgicalSubject> reconciled = new ArrayList<>(subjects.size());
		for (SurgicalSubject subject : subjects) {
			SurgicalSubject retained = previous.get(subject.persistentId());
			if (retained != null && retained.contentEquals(subject)) {
				reconciled.add(retained);
				continue;
			}
			changed.add(subject.persistentId());
			reconciled.add(subject);
		}
		clearSubjects();
		addSubjects(reconciled);
		return changed;
	}

	@Override
	public AABB getRenderBoundingBox() {
		AABB bounds = new AABB(worldPosition);
		if (level != null && hasSubjects()) {
			if (level.isClientSide) {
				getClientPlane();
				if (clientPlaneBounds != null)
					bounds = bounds.minmax(clientPlaneBounds);
			} else {
				SurgicalTablePlane.Plane plane = getServerPlane();
				for (BlockPos tablePos : plane.tiles())
					bounds = bounds.minmax(new AABB(tablePos));
			}
		}
		if (clientRenderBounds != null)
			return bounds.minmax(clientRenderBounds).inflate(0.25d);
		for (SurgicalSubject subject : subjects) {
			AABB placedCenter = new AABB(worldPosition.getX() + subject.originOffsetX(), worldPosition.getY(),
				worldPosition.getZ() + subject.originOffsetZ(),
				worldPosition.getX() + subject.originOffsetX() + 1.0d, worldPosition.getY() + 1.0d,
				worldPosition.getZ() + subject.originOffsetZ() + 1.0d);
			bounds = bounds.minmax(placedCenter);
		}
		return bounds.inflate(8.0d);
	}
}
