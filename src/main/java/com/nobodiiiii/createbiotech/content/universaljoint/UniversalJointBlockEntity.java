package com.nobodiiiii.createbiotech.content.universaljoint;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointEndpointBlockEntity.EndpointAddress;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One locally-persisted endpoint of a slime universal joint.
 *
 * <p>Each endpoint stores the exact identity and address of its peer. There is deliberately no
 * world-level pair or address directory.</p>
 */
public class UniversalJointBlockEntity extends UniversalJointEndpointBlockEntity {

	private static final String TAG_LINKED_POS = "LinkedJoint";
	private static final String TAG_LINKED_SUB_LEVEL = "LinkedSubLevel";
	private static final String TAG_LINKED_ENDPOINT = "LinkedEndpointId";
	private static final String TAG_LINKED_REVISION = "LinkedEndpointGeneration";
	private static final String TAG_LINKED_DIMENSION = "LinkedDimension";
	private static final String TAG_LINKED_SPACE_KNOWN = "LinkedSpaceKnown";
	private static final String TAG_LINK_ID = "PairId";
	private static final String TAG_LEGACY_LINK_ID = "LinkId";

	private static final double ENDPOINT_INNER_OFFSET = 4 / 16d;
	private static final double AXIS_EPSILON = 1.0E-5d;
	private static final double SHAFT_RADIUS = 4 / 16d / 2d;
	private static final int LINK_AUDIT_PERIOD_TICKS = 8;

	@Nullable
	private PeerReference peer;
	@Nullable
	private LegacyPeerReference legacyPeer;
	private boolean peerDimensionKnown = true;
	private boolean pendingOverstretchBreak;
	private int linkAuditCountdown;
	@Nullable
	private BridgeSignature bridgeSignature;

	public UniversalJointBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), pos, state);
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries,
		boolean clientPacket) {
		PeerReference savedPeer = peer;
		if (savedPeer != null) {
			EndpointAddress address = effectiveAddress(savedPeer.address());
			compound.put(TAG_LINKED_POS, NbtUtils.writeBlockPos(address.rawPos()));
			if (address.subLevelId() != null)
				compound.putUUID(TAG_LINKED_SUB_LEVEL, address.subLevelId());
			compound.putUUID(TAG_LINKED_ENDPOINT, savedPeer.endpointId());
			compound.putLong(TAG_LINKED_REVISION, savedPeer.minimumMoveRevision());
			compound.putString(TAG_LINKED_DIMENSION,
				address.dimension().location().toString());
			compound.putBoolean(TAG_LINKED_SPACE_KNOWN, true);
			compound.putUUID(TAG_LINK_ID, savedPeer.linkId());
		} else if (legacyPeer != null) {
			compound.put(TAG_LINKED_POS, NbtUtils.writeBlockPos(legacyPeer.rawPos()));
			if (legacyPeer.subLevelId() != null)
				compound.putUUID(TAG_LINKED_SUB_LEVEL, legacyPeer.subLevelId());
			compound.putBoolean(TAG_LINKED_SPACE_KNOWN, legacyPeer.spaceKnown());
			if (legacyPeer.linkId() != null)
				compound.putUUID(TAG_LEGACY_LINK_ID, legacyPeer.linkId());
		}
		super.write(compound, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries,
		boolean clientPacket) {
		PeerReference oldPeer = peer;
		LegacyPeerReference oldLegacyPeer = legacyPeer;
		super.read(compound, registries, clientPacket);

		BlockPos linkedPos = NbtUtils.readBlockPos(compound, TAG_LINKED_POS).orElse(null);
		UUID linkedSubLevelId = compound.hasUUID(TAG_LINKED_SUB_LEVEL)
			? compound.getUUID(TAG_LINKED_SUB_LEVEL) : null;
		UUID linkedEndpointId = compound.hasUUID(TAG_LINKED_ENDPOINT)
			? compound.getUUID(TAG_LINKED_ENDPOINT) : null;
		UUID linkId = compound.hasUUID(TAG_LINK_ID) ? compound.getUUID(TAG_LINK_ID)
			: compound.hasUUID(TAG_LEGACY_LINK_ID)
				? compound.getUUID(TAG_LEGACY_LINK_ID) : null;
		boolean spaceKnown = compound.contains(TAG_LINKED_SPACE_KNOWN, Tag.TAG_BYTE)
			&& compound.getBoolean(TAG_LINKED_SPACE_KNOWN);
		long linkedRevision = Math.max(0, compound.getLong(TAG_LINKED_REVISION));

		peer = null;
		legacyPeer = null;
		java.util.Optional<ResourceKey<Level>> linkedDimension =
			readLinkedDimension(compound);
		peerDimensionKnown = linkedDimension.isPresent();
		if (linkedPos != null && linkedEndpointId != null && linkId != null) {
			ResourceKey<Level> dimension = linkedDimension.orElse(Level.OVERWORLD);
			peer = new PeerReference(linkId, linkedEndpointId, linkedRevision,
				new EndpointAddress(dimension, linkedSubLevelId, linkedPos));
		} else if (linkedPos != null) {
			legacyPeer = new LegacyPeerReference(linkedPos, linkedSubLevelId,
				linkId, spaceKnown);
		}

		if (!Objects.equals(oldPeer, peer)
			|| !Objects.equals(oldLegacyPeer, legacyPeer)) {
			bridgeSignature = null;
			invalidateRenderBoundingBox();
		}
	}

	private static java.util.Optional<ResourceKey<Level>> readLinkedDimension(
		CompoundTag compound) {
		if (!compound.contains(TAG_LINKED_DIMENSION, Tag.TAG_STRING))
			return java.util.Optional.empty();
		ResourceLocation id =
			ResourceLocation.tryParse(compound.getString(TAG_LINKED_DIMENSION));
		return id == null ? java.util.Optional.empty()
			: java.util.Optional.of(ResourceKey.create(Registries.DIMENSION, id));
	}

	@Override
	public void tick() {
		super.tick();
		if (isRemoved())
			return;
		if (!(level instanceof ServerLevel)) {
			UniversalJointBlockEntity partner = getLoadedLinkedJoint();
			applyShaftSlowEffect(partner != null && references(partner)
				&& partner.references(this) ? partner : null);
			return;
		}
		if (!isAtExpectedOwnAddress())
			return;

		normalizeLegacyLink();
		normalizePeerDimension();

		if (pendingOverstretchBreak) {
			pendingOverstretchBreak = false;
			UniversalJointTransactions.requestFracture(this);
		}

		UniversalJointBlockEntity partner = getLoadedLinkedJoint();
		if (partner != null) {
			reconcileReciprocalLink(partner);
			if (references(partner) && partner.references(this))
				refreshBridgeIfChanged(partner);
		}

		if (linkAuditCountdown-- <= 0) {
			linkAuditCountdown = LINK_AUDIT_PERIOD_TICKS;
			if (partner != null
				&& (!references(partner) || !partner.references(this))) {
				clearLocalLink();
				rebuildKineticsAfterTransaction();
			} else if (partner != null) {
				double disconnectRange = getElasticDisconnectRange();
				if (distanceSquaredTo(partner) >= disconnectRange * disconnectRange) {
					UniversalJointTransactions.requestFracture(this);
					return;
				}
			}
		}

		applyShaftSlowEffect(partner != null && references(partner)
			&& partner.references(this) ? partner : null);
	}

	public boolean createMutualLink(UniversalJointBlockEntity other) {
		if (!(level instanceof ServerLevel) || other.level != level || other == this
			|| isRemoved() || other.isRemoved() || hasLink() || other.hasLink()
			|| !level.dimension().equals(other.level.dimension()))
			return false;
		if (Math.sqrt(distanceSquaredTo(other)) > getStrainStartDistance())
			return false;

		UUID linkId = UUID.randomUUID();
		installLocalLink(linkId, other);
		other.installLocalLink(linkId, this);
		if (!references(other) || !other.references(this)) {
			clearLocalLink();
			other.clearLocalLink();
			return false;
		}
		rebuildPairKinetics(other);
		return true;
	}

	void installLocalLink(UUID linkId, UniversalJointBlockEntity other) {
		peer = new PeerReference(linkId, other.getEndpointId(),
			other.getMoveRevision(), other.getEndpointAddress());
		legacyPeer = null;
		peerDimensionKnown = true;
		pendingOverstretchBreak = false;
		bridgeSignature = null;
		synchronizeLink();
	}

	void restoreLocalLink(@Nullable PeerReference reference) {
		peer = reference;
		legacyPeer = null;
		peerDimensionKnown = reference == null || level == null
			|| reference.address().dimension().equals(level.dimension());
		pendingOverstretchBreak = false;
		bridgeSignature = null;
		synchronizeLink();
	}

	void clearLocalLink() {
		peer = null;
		legacyPeer = null;
		peerDimensionKnown = true;
		pendingOverstretchBreak = false;
		bridgeSignature = null;
		synchronizeLink();
	}

	@Nullable
	PeerReference snapshotPeerReference() {
		return peer;
	}

	@Nullable
	public BlockPos getLinkedPos() {
		return peer != null ? effectiveAddress(peer.address()).rawPos()
			: legacyPeer == null ? null : legacyPeer.rawPos();
	}

	@Nullable
	public UUID getLinkedSubLevelId() {
		return peer != null ? effectiveAddress(peer.address()).subLevelId()
			: legacyPeer == null ? null : legacyPeer.subLevelId();
	}

	@Nullable
	public UUID getPairId() {
		return peer != null ? peer.linkId()
			: legacyPeer == null ? null : legacyPeer.linkId();
	}

	public boolean hasLink() {
		return peer != null || legacyPeer != null;
	}

	public boolean hasVerifiedLink() {
		if (!isAtExpectedOwnAddress())
			return false;
		UniversalJointBlockEntity partner = getLoadedLinkedJoint();
		return partner != null && partner.isAtExpectedOwnAddress()
			&& references(partner) && partner.references(this);
	}

	@Nullable
	public UUID getContainingSubLevelId() {
		return level == null ? null : SubLevelCompat.getSpaceId(level, worldPosition);
	}

	@Nullable
	public UniversalJointBlockEntity getLoadedLinkedJoint() {
		if (level == null || !isAtExpectedOwnAddress()) {
			return null;
		}
		if (peer == null)
			return resolveLegacyPeer();

		EndpointAddress address = effectiveAddress(peer.address());
		BlockEntity candidate = isVirtual() && address.subLevelId() == null
			&& level.dimension().equals(address.dimension())
			? level.getBlockEntity(address.rawPos())
			: UniversalJointEndpointBlockEntity.resolveLoaded(level, address);
		if (!(candidate instanceof UniversalJointBlockEntity joint) || joint == this
			|| !joint.isAtExpectedOwnAddress())
			return null;
		UUID candidateLinkId = joint.getPairId();
		return candidateLinkId != null
			&& peer.accepts(joint.getEndpointId(), candidateLinkId, joint.getMoveRevision())
				? joint : null;
	}

	@Nullable
	private UniversalJointBlockEntity resolveLegacyPeer() {
		if (legacyPeer == null || level == null)
			return null;
		// Ponder stores its block entities directly, outside the real world's chunks.
		BlockEntity candidate = isVirtual() && legacyPeer.subLevelId() == null
			? level.getBlockEntity(legacyPeer.rawPos())
			: legacyPeer.spaceKnown()
			? SubLevelCompat.resolveBlockEntityFast(level, legacyPeer.rawPos(),
				legacyPeer.subLevelId())
			: SubLevelCompat.getLoadedBlockEntity(level, legacyPeer.rawPos());
		if (!(candidate instanceof UniversalJointBlockEntity joint) || joint == this
			|| !joint.isAtExpectedOwnAddress())
			return null;
		if (!joint.legacyReferencesPosition(this))
			return null;
		normalizeLegacyPair(joint);
		return peer != null ? joint : null;
	}

	private boolean legacyReferencesPosition(UniversalJointBlockEntity other) {
		if (peer != null)
			return peer.endpointId().equals(other.getEndpointId());
		if (legacyPeer == null)
			return false;
		return legacyPeer.rawPos().equals(other.worldPosition)
			&& (!legacyPeer.spaceKnown()
				|| Objects.equals(legacyPeer.subLevelId(),
					other.getContainingSubLevelId()));
	}

	private void normalizeLegacyLink() {
		if (legacyPeer != null)
			resolveLegacyPeer();
	}

	private void normalizeLegacyPair(UniversalJointBlockEntity other) {
		if (level == null || other.level != level)
			return;
		UUID linkId = chooseLegacyLinkId(other);
		installLocalLink(linkId, other);
		other.installLocalLink(linkId, this);
		rebuildPairKinetics(other);
	}

	private UUID chooseLegacyLinkId(UniversalJointBlockEntity other) {
		UUID ownCandidate = legacyPeer == null ? null : legacyPeer.linkId();
		UUID otherCandidate = other.legacyPeer == null ? null : other.legacyPeer.linkId();
		if (ownCandidate != null && (otherCandidate == null || ownCandidate.equals(otherCandidate)))
			return ownCandidate;
		if (otherCandidate != null && ownCandidate == null)
			return otherCandidate;
		UUID first = getEndpointId().compareTo(other.getEndpointId()) <= 0
			? getEndpointId() : other.getEndpointId();
		UUID second = first.equals(getEndpointId())
			? other.getEndpointId() : getEndpointId();
		return UUID.nameUUIDFromBytes(
			("create_biotech:universal_joint_link:" + first + ':' + second)
				.getBytes(StandardCharsets.UTF_8));
	}

	private void normalizePeerDimension() {
		if (peer == null || peerDimensionKnown || level == null)
			return;
		peer = peer.withAddress(new EndpointAddress(level.dimension(),
			peer.address().subLevelId(), peer.address().rawPos()),
			peer.minimumMoveRevision());
		peerDimensionKnown = true;
		synchronizeLink();
	}

	public boolean references(UniversalJointBlockEntity other) {
		if (other == this || peer == null || level == null || other.level != level)
			return false;
		if (!isAtExpectedOwnAddress() || !other.isAtExpectedOwnAddress())
			return false;
		UUID otherLinkId = other.getPairId();
		if (otherLinkId == null
			|| !peer.accepts(other.getEndpointId(), otherLinkId,
				other.getMoveRevision()))
			return false;
		return effectiveAddress(peer.address()).equals(other.getEndpointAddress());
	}

	public boolean acceptPeerAddress(UUID expectedLinkId, UUID expectedEndpointId,
		EndpointAddress replacement, long replacementRevision) {
		if (!isAtExpectedOwnAddress() || peer == null
			|| !peer.linkId().equals(expectedLinkId)
			|| !peer.endpointId().equals(expectedEndpointId)
			|| replacementRevision < peer.minimumMoveRevision())
			return false;
		EndpointAddress current = effectiveAddress(peer.address());
		if (replacementRevision == peer.minimumMoveRevision()
			&& !current.equals(replacement))
			return false;
		if (replacementRevision == peer.minimumMoveRevision()
			&& current.equals(replacement))
			return true;
		peer = peer.withAddress(replacement, replacementRevision);
		peerDimensionKnown = true;
		bridgeSignature = null;
		synchronizeLink();
		return true;
	}

	private void reconcileReciprocalLink(UniversalJointBlockEntity partner) {
		if (!isAtExpectedOwnAddress() || !partner.isAtExpectedOwnAddress()
			|| peer == null || partner.peer == null
			|| !peer.linkId().equals(partner.peer.linkId())
			|| !peer.endpointId().equals(partner.getEndpointId())
			|| !partner.peer.endpointId().equals(getEndpointId()))
			return;
		boolean advertised = partner.acceptPeerAddress(peer.linkId(), getEndpointId(),
			getEndpointAddress(), getMoveRevision());
		boolean refreshed = acceptPeerAddress(peer.linkId(), partner.getEndpointId(),
			partner.getEndpointAddress(), partner.getMoveRevision());
		if (!(advertised && refreshed))
			bridgeSignature = null;
	}

	public double distanceSquaredTo(UniversalJointBlockEntity other) {
		if (level == null || other.level != level)
			return Double.POSITIVE_INFINITY;
		return getWorldCenter().distanceToSqr(other.getWorldCenter());
	}

	public boolean shouldOwnElasticLink(UniversalJointBlockEntity partner) {
		if (!hasVerifiedLink())
			return false;
		UUID ownSpace = getContainingSubLevelId();
		UUID partnerSpace = partner.getContainingSubLevelId();
		if (ownSpace == null)
			return false;
		if (partnerSpace == null)
			return true;
		if (ownSpace.equals(partnerSpace))
			return false;
		return getEndpointId().compareTo(partner.getEndpointId()) < 0;
	}

	public void requestOverstretchBreak() {
		pendingOverstretchBreak = true;
	}

	public static double getStrainStartDistance() {
		return CBConfigs.SERVER.universalJoint.effectiveStrainStartDistance();
	}

	public static double getElasticDisconnectRange() {
		return CBConfigs.SERVER.universalJoint.effectiveDisconnectRange();
	}

	public static double getStretchProgress(double distance) {
		double start = getStrainStartDistance();
		double span = Math.max(0.01d, getElasticDisconnectRange() - start);
		return Mth.clamp((distance - start) / span, 0.0d, 1.0d);
	}

	public void handleBlockRemoval() {
		if (prepareEndpointRemoval())
			UniversalJointTransactions.dismantle(this);
	}

	void rebuildKineticsAfterTransaction() {
		if (level == null || level.isClientSide)
			return;
		detachKinetics();
		removeSource();
		updateSpeed = true;
		notifyUpdate();
	}

	@Override
	protected void onEndpointAddressChanged(EndpointAddress source,
		EndpointAddress destination, long newRevision) {
		UniversalJointBlockEntity partner = getLoadedLinkedJoint();
		if (partner != null) {
			reconcileReciprocalLink(partner);
			if (references(partner) && partner.references(this))
				rebuildPairKinetics(partner);
		}
	}

	private void synchronizeLink() {
		invalidateRenderBoundingBox();
		notifyUpdate();
	}

	@Override
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state,
		List<BlockPos> neighbours) {
		super.addPropagationLocations(block, state, neighbours);
		UniversalJointBlockEntity partner = getLoadedLinkedJoint();
		BridgeSnapshot snapshot = partner == null ? null : inspectBridge(partner);
		if (snapshot != null && snapshot.driverEndpoint().equals(getEndpointId())
			&& !neighbours.contains(partner.worldPosition))
			neighbours.add(partner.worldPosition);
		return neighbours;
	}

	@Override
	public boolean isCustomConnection(KineticBlockEntity other, BlockState state,
		BlockState otherState) {
		return other instanceof UniversalJointBlockEntity partner
			&& inspectBridge(partner) != null;
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom,
		BlockState stateTo, BlockPos diff, boolean connectedViaAxes,
		boolean connectedViaCogs) {
		if (!(target instanceof UniversalJointBlockEntity partner))
			return super.propagateRotationTo(target, stateFrom, stateTo, diff,
				connectedViaAxes, connectedViaCogs);
		BridgeSnapshot snapshot = inspectBridge(partner);
		return snapshot != null && snapshot.driverEndpoint().equals(getEndpointId())
			? snapshot.rotationModifier() : 0;
	}

	@Nullable
	private BridgeSnapshot inspectBridge(UniversalJointBlockEntity partner) {
		if (level == null || partner.level != level || !references(partner)
			|| !partner.references(this))
			return null;

		int ownClaim = source == null ? 0 : source.equals(partner.worldPosition) ? -1 : 1;
		int partnerClaim =
			partner.source == null ? 0 : partner.source.equals(worldPosition) ? -1 : 1;
		UUID driver;
		if (ownClaim != partnerClaim) {
			driver = ownClaim > partnerClaim ? getEndpointId() : partner.getEndpointId();
		} else if (bridgeSignature != null
			&& isPairEndpoint(bridgeSignature.driverEndpoint(), partner)) {
			driver = bridgeSignature.driverEndpoint();
		} else if (partner.bridgeSignature != null
			&& isPairEndpoint(partner.bridgeSignature.driverEndpoint(), partner)) {
			driver = partner.bridgeSignature.driverEndpoint();
		} else {
			driver = isPrimaryEndpoint(this, partner)
				? getEndpointId() : partner.getEndpointId();
		}
		return new BridgeSnapshot(driver, getWorldRotationModifier(partner));
	}

	private boolean isPairEndpoint(UUID endpointId, UniversalJointBlockEntity partner) {
		return endpointId.equals(getEndpointId())
			|| endpointId.equals(partner.getEndpointId());
	}

	private void refreshBridgeIfChanged(UniversalJointBlockEntity partner) {
		BridgeSnapshot snapshot = inspectBridge(partner);
		if (snapshot == null)
			return;
		BridgeSignature observed = new BridgeSignature(snapshot.driverEndpoint(),
			Float.floatToIntBits(snapshot.rotationModifier()));
		if (observed.equals(bridgeSignature) && observed.equals(partner.bridgeSignature))
			return;
		bridgeSignature = observed;
		partner.bridgeSignature = observed;
		rebuildPairKinetics(partner);
	}

	private void rebuildPairKinetics(UniversalJointBlockEntity partner) {
		rebuildKineticsAfterTransaction();
		partner.rebuildKineticsAfterTransaction();
	}

	private float getWorldRotationModifier(UniversalJointBlockEntity partner) {
		UniversalJointBlockEntity first = isPrimaryEndpoint(this, partner)
			? this : partner;
		UniversalJointBlockEntity second = first == this ? partner : this;
		Vec3 firstAxis = first.getWorldPositiveRotationAxis();
		Vec3 secondAxis = second.getWorldPositiveRotationAxis();
		Vec3 firstFacing = first.getWorldFacingVector();
		Vec3 secondFacing = second.getWorldFacingVector();
		double axisAlignment = firstAxis.dot(secondAxis);
		if (Math.abs(axisAlignment) >= 1 - AXIS_EPSILON) {
			float facingRatio = firstFacing.dot(secondFacing) >= 0 ? -1 : 1;
			float positiveAxisRatio = axisAlignment >= 0 ? 1 : -1;
			return facingRatio * positiveAxisRatio;
		}

		Vec3 bridge = second.getWorldInnerEndpoint()
			.subtract(first.getWorldInnerEndpoint());
		return endpointRollSign(firstAxis, firstFacing, bridge)
			* endpointRollSign(secondAxis, secondFacing, bridge);
	}

	private static float endpointRollSign(Vec3 positiveAxis, Vec3 facing,
		Vec3 bridge) {
		double facingAxisSign = Math.signum(facing.dot(positiveAxis));
		double bridgeSide = bridge.dot(facing);
		double bridgeSideSign =
			Math.abs(bridgeSide) < AXIS_EPSILON ? 1 : Math.signum(bridgeSide);
		return (float) (facingAxisSign * bridgeSideSign);
	}

	private Vec3 getWorldFacingVector() {
		Direction facing = getBlockState().getValue(UniversalJointBlock.FACING);
		Vec3 local = Vec3.atLowerCornerOf(facing.getNormal());
		Vec3 world = level == null ? local
			: SubLevelCompat.localNormalToWorld(level, worldPosition, local);
		return world.lengthSqr() < 1.0E-8d ? local : world.normalize();
	}

	private Vec3 getWorldInnerEndpoint() {
		if (level == null)
			return getInnerEndpoint(worldPosition, getBlockState());
		return SubLevelCompat.toWorld(level, worldPosition,
			getInnerEndpoint(worldPosition, getBlockState()));
	}

	public Vec3 getWorldPositiveRotationAxis() {
		Axis axis =
			((IRotate) getBlockState().getBlock()).getRotationAxis(getBlockState());
		Vec3 local = switch (axis) {
			case X -> new Vec3(1, 0, 0);
			case Y -> new Vec3(0, 1, 0);
			case Z -> new Vec3(0, 0, 1);
		};
		Vec3 world = level == null ? local
			: SubLevelCompat.localNormalToWorld(level, worldPosition, local);
		return world.lengthSqr() < 1.0E-8d ? local : world.normalize();
	}

	private void applyShaftSlowEffect(@Nullable UniversalJointBlockEntity partner) {
		double multiplier = CBConfigs.SERVER.universalJoint.shaftSlowdownMultiplier.get();
		if (multiplier >= 1 || partner == null || !isPrimaryEndpoint(this, partner))
			return;

		Vec3 start = getWorldInnerEndpoint();
		Vec3 end = partner.getWorldInnerEndpoint();
		if (start.distanceToSqr(end) < 1.0E-8d)
			return;

		AABB broadPhase = new AABB(start, end).inflate(SHAFT_RADIUS);
		Vec3 stuck = new Vec3(multiplier, multiplier, multiplier);
		for (Player player : level.getEntitiesOfClass(Player.class, broadPhase)) {
			AABB playerBounds = player.getBoundingBox().inflate(SHAFT_RADIUS);
			if (!player.isSpectator() && (playerBounds.contains(start)
				|| playerBounds.contains(end)
				|| playerBounds.clip(start, end).isPresent()))
				player.makeStuckInBlock(getBlockState(), stuck);
		}
	}

	public static boolean isPrimaryEndpoint(UniversalJointBlockEntity first,
		UniversalJointBlockEntity second) {
		int identityOrder = first.getEndpointId().compareTo(second.getEndpointId());
		if (identityOrder != 0)
			return identityOrder < 0;
		return first.worldPosition.compareTo(second.worldPosition) <= 0;
	}

	public static Vec3 getInnerEndpoint(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(UniversalJointBlock.FACING);
		return Vec3.atLowerCornerOf(pos)
			.add(.5d + facing.getStepX() * ENDPOINT_INNER_OFFSET,
				.5d + facing.getStepY() * ENDPOINT_INNER_OFFSET,
				.5d + facing.getStepZ() * ENDPOINT_INNER_OFFSET);
	}

	@Override
	public AABB createRenderBoundingBox() {
		AABB base = super.createRenderBoundingBox();
		BlockPos linkedPos = getLinkedPos();
		if (linkedPos == null)
			return base;
		if (getContainingSubLevelId() == null && getLinkedSubLevelId() == null)
			return base.minmax(AABB.ofSize(Vec3.atCenterOf(linkedPos), 1, 1, 1))
				.inflate(1);
		double radius = getElasticDisconnectRange() + 1;
		return AABB.ofSize(Vec3.atCenterOf(worldPosition),
			radius * 2, radius * 2, radius * 2);
	}

	private EndpointAddress effectiveAddress(EndpointAddress address) {
		if (peerDimensionKnown || level == null)
			return address;
		return new EndpointAddress(level.dimension(), address.subLevelId(),
			address.rawPos());
	}

	static record PeerReference(UUID linkId, UUID endpointId,
								long minimumMoveRevision, EndpointAddress address) {

		PeerReference {
			Objects.requireNonNull(linkId, "linkId");
			Objects.requireNonNull(endpointId, "endpointId");
			Objects.requireNonNull(address, "address");
			if (minimumMoveRevision < 0)
				throw new IllegalArgumentException(
					"minimumMoveRevision must not be negative");
		}

		boolean accepts(UUID candidateEndpointId, UUID candidateLinkId,
			long candidateMoveRevision) {
			return endpointId.equals(candidateEndpointId)
				&& linkId.equals(candidateLinkId)
				&& candidateMoveRevision >= minimumMoveRevision;
		}

		PeerReference withAddress(EndpointAddress replacement,
			long replacementRevision) {
			return new PeerReference(linkId, endpointId, replacementRevision,
				replacement);
		}
	}

	private record LegacyPeerReference(BlockPos rawPos,
									   @Nullable UUID subLevelId,
									   @Nullable UUID linkId,
									   boolean spaceKnown) {

		private LegacyPeerReference {
			rawPos = rawPos.immutable();
		}
	}

	private record BridgeSnapshot(UUID driverEndpoint, float rotationModifier) {}

	private record BridgeSignature(UUID driverEndpoint, int rotationBits) {}
}
