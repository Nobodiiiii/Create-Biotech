package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** One independently placeable and editable creature stored by a surgical-table controller. */
public final class SurgicalSubject {
	static final String ID_TAG = "SubjectId";
	static final String PERSISTENT_ID_TAG = "PersistentId";
	static final String DONOR_ID_TAG = "DonorId";
	static final String FACING_TAG = "PlacementFacing";
	static final String LAY_AXIS_TAG = "LayAxis";
	static final String LAY_YAW_TAG = "LayYaw";
	static final String LAY_TRANSLATE_X_TAG = "LayTranslateX";
	static final String LAY_TRANSLATE_Y_TAG = "LayTranslateY";
	static final String LAY_TRANSLATE_Z_TAG = "LayTranslateZ";
	static final String PRESENT_CUBES_TAG = "PresentCubes";
	static final String CUT_SEAMS_TAG = "CutSeams";
	static final String CUT_ORDER_TAG = "CutOrder";
	static final String ORIGIN_OFFSET_X_TAG = "OriginOffsetX";
	static final String ORIGIN_OFFSET_Z_TAG = "OriginOffsetZ";
	static final String OFFSET_CUBES_TAG = "OffsetCubes";
	static final String OFFSET_X_TAG = "OffsetX";
	static final String OFFSET_Y_TAG = "OffsetY";
	static final String OFFSET_Z_TAG = "OffsetZ";
	static final String ROTATIONS_TAG = "CubeRotations";
	static final String ROTATION_CUBE_TAG = "Cube";
	static final String ROTATION_VALUE_TAG = "Rotation";
	static final String GLUE_JOINTS_TAG = "GlueJoints";
	static final String COMBINATIONS_TAG = "Combinations";
	static final String LIMB_JOINTS_TAG = "LimbJoints";
	static final String FOOTPRINTS_TAG = "Footprints";
	static final String FOOTPRINT_ROOT_TAG = "Root";
	static final String FOOTPRINT_MIN_X_TAG = "MinX";
	static final String FOOTPRINT_MIN_Z_TAG = "MinZ";
	static final String FOOTPRINT_MAX_X_TAG = "MaxX";
	static final String FOOTPRINT_MAX_Z_TAG = "MaxZ";
	static final String FOOTPRINT_GRID_X_TAG = "GridX";
	static final String FOOTPRINT_GRID_Z_TAG = "GridZ";

	int id;
	final UUID persistentId;
	final UUID donorId;
	final BionicAnatomySnapshot anatomy;
	final MimicProfile profile;
	Direction placementFacing;
	SurgicalLayPose layPose;
	final int cubeCount;
	final BitSet presentCubes;
	final List<SurgicalAssembly.Seam> seams;
	BitSet cutSeams;
	List<Integer> cutOrder;
	double originOffsetX;
	double originOffsetZ;
	Map<Integer, Vec3> componentOffsets;
	Map<Integer, SurgicalCubeRotation> componentRotations;
	List<SurgicalTableLayout.Footprint> occupiedFootprints;
	List<SurgicalGlueJoint> glueJoints;
	List<SurgicalCombination> combinations;
	List<SurgicalLimbJoint> limbJoints;
	private int clientRenderRevision;
	@Nullable
	private SurgicalTableBlockEntity table;

	SurgicalSubject(int id, UUID persistentId, UUID donorId, BionicAnatomySnapshot anatomy,
		MimicProfile profile, Direction placementFacing,
		SurgicalLayPose layPose, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<Integer> cutOrder,
		double originOffsetX, double originOffsetZ, Map<Integer, Vec3> componentOffsets,
		Map<Integer, SurgicalCubeRotation> componentRotations,
		List<SurgicalTableLayout.Footprint> occupiedFootprints, List<SurgicalGlueJoint> glueJoints,
		List<SurgicalCombination> combinations, List<SurgicalLimbJoint> limbJoints) {
		this.limbJoints = List.copyOf(limbJoints);
		this.id = id;
		this.persistentId = persistentId;
		this.donorId = donorId;
		this.anatomy = anatomy;
		this.profile = profile;
		this.placementFacing = horizontal(placementFacing);
		this.layPose = layPose == null ? SurgicalLayPose.IDENTITY : layPose;
		this.cubeCount = cubeCount;
		this.presentCubes = (BitSet) presentCubes.clone();
		this.seams = List.copyOf(seams);
		this.cutSeams = (BitSet) cutSeams.clone();
		this.cutOrder = List.copyOf(cutOrder);
		this.originOffsetX = originOffsetX;
		this.originOffsetZ = originOffsetZ;
		this.componentOffsets = Map.copyOf(componentOffsets);
		this.componentRotations = Map.copyOf(componentRotations);
		this.occupiedFootprints = List.copyOf(occupiedFootprints);
		this.glueJoints = List.copyOf(glueJoints);
		this.combinations = List.copyOf(combinations);
	}

	public int id() {
		return id;
	}

	public UUID persistentId() {
		return persistentId;
	}

	public UUID donorId() { return donorId; }
	public BionicAnatomySnapshot anatomy() { return anatomy; }

	void setId(int id) {
		this.id = id;
		connectionsChanged();
	}

	void setTable(@Nullable SurgicalTableBlockEntity table) {
		this.table = table;
	}

	private void connectionsChanged() {
		if (table != null)
			table.invalidateConnections();
	}

	void setCutSeams(BitSet cuts) {
		cutSeams = cuts;
		connectionsChanged();
	}

	public MimicProfile profile() {
		return profile;
	}

	public Direction placementFacing() {
		return placementFacing;
	}

	public SurgicalLayPose layPose() {
		return layPose;
	}

	public int cubeCount() {
		return cubeCount;
	}

	/**
	 * This subject's own topology was validated when it was created, so an observation that equals it
	 * is valid by construction and needs no second validation pass.
	 */
	public boolean matchesObservedTopology(int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		return cubeCount == observedCubeCount && seams.equals(observedSeams);
	}

	public BitSet presentCubes() {
		return (BitSet) presentCubes.clone();
	}


	public List<SurgicalAssembly.Seam> seams() {
		return seams;
	}

	/** Clone-free comparisons for the client's per-frame "did this subject change" check. */
	public boolean presentCubesEqual(BitSet cubes) {
		return presentCubes.equals(cubes);
	}

	public boolean cutSeamsEqual(BitSet cuts) {
		return cutSeams.equals(cuts);
	}

	public BitSet cutSeamsForRender() {
		return (BitSet) cutSeams.clone();
	}

	public List<Integer> cutOrderForRender() {
		return cutOrder;
	}

	public double originOffsetX() {
		return originOffsetX;
	}

	public double originOffsetZ() {
		return originOffsetZ;
	}

	public Map<Integer, Vec3> componentOffsetsForRender() {
		return componentOffsets;
	}

	public Map<Integer, SurgicalCubeRotation> componentRotationsForRender() {
		return componentRotations;
	}

	public List<SurgicalTableLayout.Footprint> occupiedFootprints() {
		return occupiedFootprints;
	}

	public boolean containsFootprint(BitSet cubes, SurgicalTableLayout.Footprint footprint) {
		if (cubes == null || footprint == null)
			return false;
		int root = footprint.componentRoot();
		return root == -1 ? cubes.equals(presentCubes) : root >= 0 && cubes.get(root);
	}

	public List<SurgicalGlueJoint> glueJoints() {
		return glueJoints;
	}

	public List<SurgicalLimbJoint> limbJoints() {
		return limbJoints;
	}

	public List<SurgicalCombination> combinations() {
		return combinations;
	}

	@Nullable
	public SurgicalCombination combinationContaining(int cubeId) {
		if (!validPresentCube(cubeId))
			return null;
		for (SurgicalCombination combination : combinations)
			if (combination.contains(persistentId, cubeId))
				return combination;
		return null;
	}

	public int clientRenderRevision() {
		return clientRenderRevision;
	}

	void setClientRenderRevision(int revision) {
		clientRenderRevision = revision;
	}

	/**
	 * Compares everything a client renderer can observe. A sync packet re-decodes every subject on the
	 * table, including the ones the edit never touched, so this is what lets an untouched subject keep
	 * its previous instance instead of forcing a full geometry rebuild. Cheap scalar fields are tested
	 * before the seam list and the profile tags, which are by far the largest.
	 */
	boolean contentEquals(@Nullable SurgicalSubject other) {
		if (this == other)
			return true;
		return other != null
			&& id == other.id
			&& cubeCount == other.cubeCount
			&& placementFacing == other.placementFacing
			&& Double.compare(originOffsetX, other.originOffsetX) == 0
			&& Double.compare(originOffsetZ, other.originOffsetZ) == 0
			&& persistentId.equals(other.persistentId)
			&& donorId.equals(other.donorId)
			&& anatomy.equals(other.anatomy)
			&& layPose.equals(other.layPose)
			&& presentCubes.equals(other.presentCubes)
			&& cutSeams.equals(other.cutSeams)
			&& cutOrder.equals(other.cutOrder)
			&& componentOffsets.equals(other.componentOffsets)
			&& componentRotations.equals(other.componentRotations)
			&& occupiedFootprints.equals(other.occupiedFootprints)
			&& glueJoints.equals(other.glueJoints)
			&& limbJoints.equals(other.limbJoints)
			&& sameCombinations(combinations, other.combinations)
			&& seams.equals(other.seams)
			&& profile.equals(other.profile);
	}

	/**
	 * {@link SurgicalCombination#equals} matches on id alone, but a combination that kept its id while
	 * gaining or losing members is a visible change.
	 */
	private static boolean sameCombinations(List<SurgicalCombination> first,
		List<SurgicalCombination> second) {
		if (first.size() != second.size())
			return false;
		for (int index = 0; index < first.size(); index++) {
			SurgicalCombination left = first.get(index);
			SurgicalCombination right = second.get(index);
			if (!left.id().equals(right.id()) || !left.members().equals(right.members()))
				return false;
		}
		return true;
	}

	/**
	 * Whether this subject can share a grounding component with another one. Grounding links are built
	 * from glue joints and combinations only, so a subject with neither is grounded purely from its own
	 * data and stays valid while its neighbours change.
	 */
	public boolean linkedToOtherSubjects() {
		return !glueJoints.isEmpty() || !combinations.isEmpty();
	}

	void rebase(BlockPos previousController, BlockPos nextController) {
		originOffsetX += previousController.getX() - nextController.getX();
		originOffsetZ += previousController.getZ() - nextController.getZ();
	}

	boolean validPresentCube(int cubeId) {
		return cubeId >= 0 && cubeId < cubeCount && presentCubes.get(cubeId);
	}

	void applyLayout(SurgicalTableLayout.Proposal proposal) {
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (SurgicalTableLayout.CubeOffset offset : proposal.offsets()) {
			if (Math.abs(offset.x()) <= 1.0e-12d && Math.abs(offset.y()) <= 1.0e-12d
				&& Math.abs(offset.z()) <= 1.0e-12d)
				continue;
			offsets.put(offset.cubeId(), new Vec3(offset.x(), offset.y(), offset.z()));
		}
		componentOffsets = Map.copyOf(offsets);
		occupiedFootprints = proposal.footprints();
	}

	void removeComponent(BitSet component) {
		presentCubes.andNot(component);
		connectionsChanged();
		java.util.Set<Integer> packedRoots = new java.util.HashSet<>();
		for (BitSet nativeComponent : SurgicalAssembly.components(cubeCount, component, seams, cutSeams))
			packedRoots.add(nativeComponent.nextSetBit(0));
		occupiedFootprints = occupiedFootprints.stream()
			.filter(footprint -> !packedRoots.contains(footprint.componentRoot()))
			.toList();
		if (!componentOffsets.isEmpty()) {
			Map<Integer, Vec3> retainedOffsets = new HashMap<>(componentOffsets);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				retainedOffsets.remove(cube);
			componentOffsets = Map.copyOf(retainedOffsets);
		}
		if (!componentRotations.isEmpty()) {
			Map<Integer, SurgicalCubeRotation> retainedRotations = new HashMap<>(componentRotations);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				retainedRotations.remove(cube);
			componentRotations = Map.copyOf(retainedRotations);
		}
	}

	void translateComponent(BitSet component, Vec3 delta) {
		Map<Integer, Vec3> translated = new HashMap<>(componentOffsets);
		for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
			Vec3 offset = translated.getOrDefault(cube, Vec3.ZERO).add(delta);
			if (offset.lengthSqr() <= 1.0e-24d)
				translated.remove(cube);
			else
				translated.put(cube, offset);
		}
		componentOffsets = Map.copyOf(translated);
		if (Math.abs(delta.x) <= 1.0e-12d && Math.abs(delta.z) <= 1.0e-12d)
			return;
		java.util.Set<Integer> movedRoots = new java.util.HashSet<>();
		for (BitSet nativeComponent : SurgicalAssembly.components(cubeCount, component, seams, cutSeams))
			movedRoots.add(nativeComponent.nextSetBit(0));
		occupiedFootprints = occupiedFootprints.stream().map(footprint -> movedRoots.contains(footprint.componentRoot())
			? new SurgicalTableLayout.Footprint(footprint.componentRoot(), footprint.minX() + delta.x,
				footprint.minZ() + delta.z, footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
				SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED)
			: footprint).toList();
	}

	void applyComponentOffsets(BitSet component, Map<Integer, Vec3> offsets) {
		if (offsets.size() != component.cardinality())
			throw new IllegalArgumentException("Incomplete surgical component offsets");
		Map<Integer, Vec3> updated = new HashMap<>(componentOffsets);
		for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
			Vec3 offset = offsets.get(cube);
			if (offset == null)
				throw new IllegalArgumentException("Missing surgical component offset");
			if (offset.lengthSqr() <= 1.0e-24d)
				updated.remove(cube);
			else
				updated.put(cube, offset);
		}
		componentOffsets = Map.copyOf(updated);
	}

	void addGlueJoint(SurgicalGlueJoint joint) {
		if (glueJoints.contains(joint))
			return;
		List<SurgicalGlueJoint> updated = new ArrayList<>(glueJoints);
		updated.add(joint);
		glueJoints = List.copyOf(updated);
		connectionsChanged();
	}

	void removeGlueJoints(java.util.Set<SurgicalGlueJoint> removed) {
		if (affects(glueJoints, removed)) {
			glueJoints = glueJoints.stream().filter(joint -> !removed.contains(joint)).toList();
			connectionsChanged();
		}
	}

	void replaceGlueJoints(List<SurgicalGlueJoint> joints) {
		glueJoints = List.copyOf(joints);
		connectionsChanged();
	}

	void addCombination(SurgicalCombination combination) {
		if (combinations.contains(combination))
			return;
		List<SurgicalCombination> updated = new ArrayList<>(combinations);
		updated.add(combination);
		combinations = List.copyOf(updated);
		connectionsChanged();
	}

	void removeCombinations(java.util.Set<SurgicalCombination> removed) {
		if (affects(combinations, removed)) {
			combinations = combinations.stream().filter(combination -> !removed.contains(combination)).toList();
			connectionsChanged();
		}
	}

	void replaceCombinations(List<SurgicalCombination> replacement) {
		combinations = List.copyOf(replacement);
		connectionsChanged();
	}

	void addLimbJoint(SurgicalLimbJoint joint) {
		if (limbJoints.contains(joint))
			return;
		List<SurgicalLimbJoint> updated = new ArrayList<>(limbJoints);
		updated.add(joint);
		limbJoints = List.copyOf(updated);
	}

	void removeLimbJoints(java.util.Set<SurgicalLimbJoint> removed) {
		if (affects(limbJoints, removed))
			limbJoints = limbJoints.stream().filter(joint -> !removed.contains(joint)).toList();
	}

	/**
	 * Whether {@code removed} holds anything {@code held} actually contains.
	 *
	 * <p>Packing walks every subject on the table and hands each one the group's joint, combination and
	 * limb sets. Without this guard each of those calls rebuilt the list through a stream even for
	 * subjects that had nothing in common with the packed group.</p>
	 */
	private static <T> boolean affects(List<T> held, java.util.Set<T> removed) {
		if (removed.isEmpty() || held.isEmpty())
			return false;
		for (T entry : held)
			if (removed.contains(entry))
				return true;
		return false;
	}

	void replaceLimbJoints(List<SurgicalLimbJoint> replacement) {
		limbJoints = List.copyOf(replacement);
	}

	SurgicalSubject extract(int extractedId, BitSet extracted) {
		BitSet selected = (BitSet) extracted.clone();
		selected.and(presentCubes);
		if (selected.isEmpty() || selected.equals(presentCubes))
			throw new IllegalArgumentException("A surgical extraction must be a proper non-empty subset");
		Map<Integer, Vec3> extractedOffsets = new HashMap<>();
		Map<Integer, SurgicalCubeRotation> extractedRotations = new HashMap<>();
		for (int cube = selected.nextSetBit(0); cube >= 0; cube = selected.nextSetBit(cube + 1)) {
			Vec3 offset = componentOffsets.get(cube);
			if (offset != null)
				extractedOffsets.put(cube, offset);
			SurgicalCubeRotation rotation = componentRotations.get(cube);
			if (rotation != null)
				extractedRotations.put(cube, rotation);
		}
		List<SurgicalTableLayout.Footprint> extractedFootprints = occupiedFootprints.stream()
			.filter(footprint -> containsFootprint(selected, footprint)).toList();
		SurgicalSubject result = new SurgicalSubject(extractedId, UUID.randomUUID(), donorId,
			anatomy, profile, placementFacing, layPose,
			cubeCount, selected, seams, cutSeams, cutOrder, originOffsetX, originOffsetZ,
			extractedOffsets, extractedRotations, extractedFootprints, List.of(), List.of(), List.of());
		removeComponent(selected);
		return result;
	}

	void applyGlueMove(Direction targetFacing, SurgicalLayPose targetPose, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations,
		List<SurgicalTableLayout.Footprint> footprints) {
		if (targetPose == null || offsets.size() != presentCubes.cardinality()
			|| rotations.size() != presentCubes.cardinality())
			throw new IllegalArgumentException("Incomplete surgical glue move");
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1))
			if (!offsets.containsKey(cube) || !rotations.containsKey(cube))
				throw new IllegalArgumentException("Missing surgical glue cube offset");
		placementFacing = horizontal(targetFacing);
		layPose = targetPose;
		componentOffsets = Map.copyOf(offsets);
		componentRotations = sanitizeRotations(rotations);
		occupiedFootprints = List.copyOf(footprints);
	}

	boolean isEmpty() {
		return presentCubes.isEmpty();
	}

	CompoundTag save() {
		return SurgicalSubjectPersistence.save(this);
	}

	CompoundTag save(@Nullable SurgicalStorage.Writer storage) {
		return SurgicalSubjectPersistence.save(this, storage);
	}

	@Nullable
	static SurgicalSubject load(CompoundTag tag) {
		return SurgicalSubjectPersistence.load(tag);
	}

	@Nullable
	static SurgicalSubject load(CompoundTag tag, @Nullable SurgicalStorage.Reader storage) {
		return SurgicalSubjectPersistence.load(tag, storage);
	}

	static Map<Integer, SurgicalCubeRotation> sanitizeRotations(
		Map<Integer, SurgicalCubeRotation> rotations) {
		Map<Integer, SurgicalCubeRotation> sanitized = new HashMap<>();
		rotations.forEach((cube, rotation) -> {
			if (rotation != null && !rotation.isIdentity())
				sanitized.put(cube, rotation);
		});
		return Map.copyOf(sanitized);
	}

	static boolean finiteOffset(double value) {
		return Double.isFinite(value) && Math.abs(value) <= SurgicalTablePlane.MAX_TILES + 2.0d;
	}

	private static Direction horizontal(Direction direction) {
		return direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
	}
}
