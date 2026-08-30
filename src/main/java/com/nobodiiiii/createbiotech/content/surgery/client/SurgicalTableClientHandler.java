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

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxItem;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueContact;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBatchCutPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableInteractionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableGluePacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbRemovalPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementResult;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableSymmetryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueItem;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalTableClientHandler {
	private static final int SEAM_HIGHLIGHT_COLOR = PonderPalette.RED.getColor();
	private static final int CUBE_HIGHLIGHT_COLOR = PonderPalette.BLUE.getColor();
	private static final int HONEY_HIGHLIGHT_COLOR = 0xE8A43A;
	private static final int LIMB_JOINT_HIGHLIGHT_COLOR = 0xB46CFF;
	private static final float HIGHLIGHT_LINE_WIDTH = 1.0f / 32.0f;
	private static final float SEAM_HIGHLIGHT_LINE_WIDTH = HIGHLIGHT_LINE_WIDTH * 1.25f;
	private static final float GLUE_POINT_LINE_WIDTH = HIGHLIGHT_LINE_WIDTH / 4.0f;
	private static final float GLUE_FIRST_PREVIEW_ALPHA = 0.5f;
	private static final double MAX_SELECTION_THRESHOLD = 3.0d / 16.0d;
	private static final double GLUE_EDIT_TRANSLATION_STEP = 1.0d / 32.0d;
	private static final double GLUE_EDIT_ROTATION_STEP = 5.0d;
	private static final int GLUE_EDIT_CIRCLE_SEGMENTS = 32;
	private static final double GLUE_EDIT_GUIDE_MARGIN = 1.0d / 16.0d;
	private static final double MODEL_PIXEL_SIZE = 1.0d / 16.0d;
	private static final double GLUE_POINT_SURFACE_OFFSET = 1.0d / 1024.0d;
	private static final double GLUE_POINT_CROSS_HALF_LENGTH_PIXELS = 0.3d;
	private static final double GLUE_POINT_PIXEL_EPSILON = 1.0e-4d;
	private static final double SYMMETRY_PLANE_MARGIN = 1.0d / 32.0d;
	private static final int ASYNC_TOPOLOGY_CUBE_THRESHOLD = 32;
	/** Approximate retained cube/vertex/contact units; currently about eight maximum-size subjects. */
	private static final long MAX_GEOMETRY_CACHE_WEIGHT = 262_144L;
	/** RenderFrame follows ClientTick.Post, so protect a short window rather than only equal game-time. */
	private static final int GEOMETRY_CACHE_RECENT_TICKS = 20;
	/** Lets a geometry survive the old-controller-empty -> new-controller-full packet handoff. */
	private static final int GEOMETRY_OWNER_HANDOFF_TICKS = 40;
	private static final int VISUAL_COMMIT_TIMEOUT_TICKS = 40;
	private static final float BATCH_CUT_ANIMATION_TICKS = 5.0f;
	private static final int BATCH_CUT_ANIMATION_TIMEOUT_TICKS = 40;
	private static final InteractionHand[] HANDS = { InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND };
	private static final OutlineState SEAM_OUTLINE = new OutlineState(SEAM_HIGHLIGHT_LINE_WIDTH);
	private static final OutlineState CUBE_OUTLINE = new OutlineState();
	private static final OutlineState COMBINATION_OUTLINE = new OutlineState();
	private static final OutlineState GLUE_JOINT_OUTLINE = new OutlineState(SEAM_HIGHLIGHT_LINE_WIDTH);
	private static final OutlineState LIMB_JOINT_OUTLINE = new OutlineState(SEAM_HIGHLIGHT_LINE_WIDTH);
	private static final OutlineState SYMMETRY_PLANE_OPTIONS_OUTLINE = new OutlineState();
	private static final OutlineState SYMMETRY_PLANE_SELECTED_OUTLINE =
		new OutlineState(SEAM_HIGHLIGHT_LINE_WIDTH);
	private static final OutlineState GLUE_EDIT_OUTLINE = new OutlineState();
	private static final OutlineState GLUE_POINT_OUTLINE = new OutlineState(GLUE_POINT_LINE_WIDTH);
	private static final Object PLACEMENT_OUTLINE_SLOT = new Object();
	/** Current interaction/render address. This changes whenever the north-west controller moves. */
	private static final Map<SubjectKey, TableGeometry> TABLES = new HashMap<>();
	/** Stable geometry ownership. A controller move must not invalidate immutable model geometry. */
	private static final Map<UUID, TableGeometry> SUBJECT_GEOMETRIES = new HashMap<>();
	@Nullable
	private static OwnerHandoffKey validatedOwnerHandoff;
	private static final Set<UUID> SAFE_OWNER_HANDOFFS = new java.util.HashSet<>();
	private static final Set<UUID> UNSAFE_OWNER_HANDOFFS = new java.util.HashSet<>();
	private static long lastPlacementOutlineTick = Long.MIN_VALUE;
	private static long geometryGeneration;
	private static long lastSelectionTick = Long.MIN_VALUE;
	private static long lastSelectionGeneration = Long.MIN_VALUE;
	private static int lastSelectionMode = Integer.MIN_VALUE;
	@Nullable
	private static Ray lastSelectionRay;
	@Nullable
	private static PendingGlue lastSelectionPendingGlue;
	@Nullable
	private static PendingGlue lastSelectionPendingSymmetry;
	@Nullable
	private static SymmetryReference lastSelectionPendingSymmetryReference;
	private static PendingLimb lastSelectionPendingLimb;
	@Nullable
	private static Selection seamSelection;
	@Nullable
	private static Selection cubeSelection;
	@Nullable
	private static Selection componentSelection;
	@Nullable
	private static Selection symmetrySelection;
	private static Selection limbSelection;
	@Nullable
	private static LimbJointSelection wrenchSelection;
	private static PendingLimb pendingLimb;
	@Nullable
	private static PendingCut pendingCut;
	@Nullable
	private static PendingGlueCut pendingGlueCut;
	@Nullable
	private static PendingGlue pendingGlue;
	@Nullable
	private static PendingGlue pendingSymmetry;
	@Nullable
	private static SymmetryReference pendingSymmetryReference;
	@Nullable
	private static SymmetryPlaneCandidate symmetryPlaneSelection;
	@Nullable
	private static GluePoint hoveredGluePoint;
	@Nullable
	private static GluePreview gluePreview;
	@Nullable
	private static GlueEditor glueEditor;
	@Nullable
	private static PlacementCandidate placementCandidate;
	@Nullable
	private static PlacementPreview placementPreview;
	private static SurgicalTablePlacementResult placementPreviewResult = SurgicalTablePlacementResult.NO_SPACE;
	@Nullable
	private static PlacementSuppression placementSuppression;
	@Nullable
	private static PendingVisualCommit pendingVisualCommit;
	@Nullable
	private static BatchCutAnimation batchCutAnimation;
	/** Keeps the cancel click from falling through into vanilla's same-tick/held block attack pass. */
	private static boolean suppressAttackUntilRelease;
	@Nullable
	private static CubeSelectionCache connectedSelectionCache;
	@Nullable
	private static CubeSelectionCache directSelectionCache;

	private SurgicalTableClientHandler() {}

	/** Returns true only when the immutable source-model geometry has to be captured again. */
	public static boolean needsGeometryUpdate(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		if (table.getLevel() == null)
			return false;
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null)
			return true;
		if (geometry.refresh(table, subject))
			geometryGeneration++;
		else
			geometry.reportBounds(table);
		return false;
	}

	public static boolean isRenderReady(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		TableGeometry geometry = geometryFor(table, subject);
		return geometryReadyForUse(table, subject, geometry);
	}

	private static boolean geometryReadyForUse(SurgicalTableBlockEntity table, SurgicalSubject subject,
		@Nullable TableGeometry geometry) {
		if (geometry == null || !geometry.matchesModel(table, subject) || !geometry.topologyReady()
			|| geometry.renderRevision != subject.clientRenderRevision())
			return false;
		if (geometry.retryPendingGrounding(table))
			geometryGeneration++;
		return !geometry.groundingPending;
	}

	@Nullable
	public static AABB cachedRenderBounds(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || geometry.groundingPending || !geometry.matchesModel(table, subject)
			|| geometry.renderRevision != subject.clientRenderRevision())
			return null;
		AABB bounds = geometry.bounds;
		BatchCutAnimation animation = batchCutAnimation;
		BatchCutMotion motion = animation != null && animation.tablePos.equals(table.getBlockPos())
			? animation.motions.get(subject.id()) : null;
		return bounds == null || motion == null || motion.startBounds == null
			? bounds : bounds.minmax(motion.startBounds);
	}

	public static void updateGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		SurgicalModelRenderContext.Snapshot snapshot) {
		MimicProfile profile = subject.profile();
		if (table.getLevel() == null || snapshot.observedCubeCount() <= 0)
			return;
		int cubeCount = snapshot.observedCubeCount();
		List<SurgicalAssembly.Seam> seams;
		List<SurgicalClientTopology.Contact> contacts;
		Supplier<SurgicalClientTopology.ContactTopology> topologyBuild = null;
		CompletableFuture<SurgicalClientTopology.ContactTopology> pendingTopology = null;
		if (subject.cubeCount() == cubeCount) {
			seams = subject.seams();
			if (cubeCount > ASYNC_TOPOLOGY_CUBE_THRESHOLD) {
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
		} else {
			if (cubeCount > ASYNC_TOPOLOGY_CUBE_THRESHOLD) {
				List<SurgicalModelRenderContext.CubeGeometry> frozenCubes = List.copyOf(snapshot.cubes());
				seams = List.of();
				contacts = List.of();
				topologyBuild = () -> {
					long started = SurgicalProfiler.begin();
					try {
						return SurgicalClientTopology.buildContactTopology(cubeCount, frozenCubes);
					} finally {
						SurgicalProfiler.end("buildContactTopology(async)", started);
					}
				};
				pendingTopology = SurgicalClientExecutors.submit(topologyBuild);
			} else {
				long started = SurgicalProfiler.begin();
				SurgicalClientTopology.ContactTopology topology =
					SurgicalClientTopology.buildContactTopology(cubeCount, snapshot.cubes());
				SurgicalProfiler.end("buildContactTopology(sync)", started);
				seams = topology.seams();
				contacts = topology.contacts();
			}
		}
		TableGeometry geometry = new TableGeometry(subject.persistentId(), subject.id(), profile,
			subject.layPose(), worldOriginX(table, subject), worldOriginZ(table, subject), cubeCount,
			snapshot.cubes(), seams, contacts,
			topologyBuild, pendingTopology, table.getLevel().getGameTime());
		geometry.refresh(table, subject);
		registerGeometry(table, subject, geometry);
		geometryGeneration++;
	}

	public static Map<Integer, Vec3> offsetsFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || !geometry.matchesModel(table, subject))
			return subject.componentOffsetsForRender();
		if (geometry.retryPendingGrounding(table))
			geometryGeneration++;
		geometry.markSeen(table);
		return batchCutRenderOffsets(table, subject, geometry);
	}

	public static Map<Integer, SurgicalCubeRotation> rotationsFor(SurgicalTableBlockEntity table,
		SurgicalSubject subject) {
		TableGeometry geometry = geometryFor(table, subject);
		if (geometry == null || !geometry.matchesModel(table, subject))
			return subject.componentRotationsForRender();
		geometry.markSeen(table);
		return geometry.rotations;
	}

	public static BitSet presentCubesFor(SurgicalTableBlockEntity table, SurgicalSubject subject,
		int observedCubeCount) {
		TableGeometry geometry = geometryFor(table, subject);
		BitSet present = geometry != null && geometry.observedCubeCount == observedCubeCount
			&& geometry.matchesModel(table, subject)
			? geometry.presentCubes : subject.presentCubesForRender(observedCubeCount);
		if (gluePreview == null || !gluePreview.ownerPos.equals(table.getBlockPos()))
			return present;
		BitSet visible = null;
		for (GlueSubjectPreview moved : gluePreview.subjects) {
			if (moved.subjectId != subject.id())
				continue;
			if (visible == null)
				visible = (BitSet) present.clone();
			visible.andNot(moved.cubes);
		}
		return visible == null ? present : visible;
	}

	/**
	 * Resolves a geometry by the subject's persistent identity, then publishes it at the controller's
	 * current transient address. Adding or removing the north-west table changes that address and the
	 * subject's controller-relative origin, but server-side rebasing keeps the world origin unchanged,
	 * so neither change requires another model capture.
	 */
	@Nullable
	private static TableGeometry geometryFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
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

	private static void registerGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		TableGeometry geometry) {
		TableGeometry previous = SUBJECT_GEOMETRIES.put(subject.persistentId(), geometry);
		if (previous != null && previous != geometry)
			removeGeometry(previous);
		bindGeometry(new SubjectKey(table.getBlockPos(), subject.id()), geometry);
	}

	private static void bindGeometry(SubjectKey address, TableGeometry geometry) {
		SubjectKey previousAddress = geometry.ownerKey;
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

	private static void removeGeometry(TableGeometry geometry) {
		if (geometry.ownerKey != null)
			TABLES.remove(geometry.ownerKey, geometry);
		SUBJECT_GEOMETRIES.remove(geometry.persistentId, geometry);
		geometry.ownerKey = null;
		geometry.dispose();
	}

	private static double worldOriginX(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return table.getBlockPos().getX() + subject.originOffsetX();
	}

	private static double worldOriginZ(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return table.getBlockPos().getZ() + subject.originOffsetZ();
	}

	/** Verifies that a linked body's complete grounding group is only changing controller address. */
	private static boolean migrationGroupUnchanged(SurgicalTableBlockEntity table, SurgicalSubject subject,
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

	private static boolean ownerAddressIsStale(@Nullable ClientLevel level, TableGeometry geometry) {
		if (level == null || geometry.ownerKey == null)
			return geometry.ownerKey == null;
		if (!(level.getBlockEntity(geometry.ownerKey.tablePos) instanceof SurgicalTableBlockEntity owner))
			return true;
		SurgicalSubject owned = owner.getSubject(geometry.ownerKey.subjectId);
		return owned == null || !geometry.persistentId.equals(owned.persistentId());
	}

	public static void clear() {
		pendingCut = null;
		pendingGlueCut = null;
		pendingGlue = null;
		pendingSymmetry = null;
		pendingSymmetryReference = null;
		symmetryPlaneSelection = null;
		hoveredGluePoint = null;
		gluePreview = null;
		glueEditor = null;
		placementCandidate = null;
		placementPreviewResult = SurgicalTablePlacementResult.NO_SPACE;
		placementSuppression = null;
		pendingVisualCommit = null;
		batchCutAnimation = null;
		suppressAttackUntilRelease = false;
		clearPlacementPreview();
		SUBJECT_GEOMETRIES.values().forEach(TableGeometry::dispose);
		TABLES.clear();
		SUBJECT_GEOMETRIES.clear();
		validatedOwnerHandoff = null;
		SAFE_OWNER_HANDOFFS.clear();
		UNSAFE_OWNER_HANDOFFS.clear();
		geometryGeneration++;
		lastSelectionRay = null;
		lastSelectionPendingGlue = null;
		lastSelectionPendingSymmetry = null;
		lastSelectionPendingLimb = null;
		pendingLimb = null;
		SurgicalTablePoseResolver.clear();
		GLUE_EDIT_OUTLINE.clear();
		GLUE_POINT_OUTLINE.clear();
		clearSymmetryPlaneHighlight();
		clearSelections();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (suppressAttackUntilRelease && !minecraft.options.keyAttack.isDown())
			suppressAttackUntilRelease = false;
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null) {
			cancelPendingVisualCommit(level, true);
			batchCutAnimation = null;
			abortPendingCut();
			abortPendingGlueCut();
			pendingGlue = null;
			pendingSymmetry = null;
			pendingSymmetryReference = null;
			symmetryPlaneSelection = null;
			hoveredGluePoint = null;
			gluePreview = null;
			glueEditor = null;
			GLUE_EDIT_OUTLINE.clear();
			GLUE_POINT_OUTLINE.clear();
			clearPlacementPreview();
			clearSelections();
			return;
		}

		long now = level.getGameTime();
		updatePendingVisualCommit(level);
		updateBatchCutAnimation(level);
		boolean removedGeometry = false;
		for (java.util.Iterator<Map.Entry<SubjectKey, TableGeometry>> iterator = TABLES.entrySet().iterator();
			iterator.hasNext();) {
			Map.Entry<SubjectKey, TableGeometry> entry = iterator.next();
			TableGeometry geometry = entry.getValue();
			SurgicalSubject subject = level.getBlockEntity(entry.getKey().tablePos)
				instanceof SurgicalTableBlockEntity table ? table.getSubject(entry.getKey().subjectId) : null;
			if (subject != null && geometry.persistentId.equals(subject.persistentId()))
				continue;
			iterator.remove();
			geometry.orphan(now);
			removedGeometry = true;
		}
		for (TableGeometry geometry : List.copyOf(SUBJECT_GEOMETRIES.values())) {
			if (geometry.ownerKey != null || geometry.orphanedAtTick == Long.MIN_VALUE
				|| now - geometry.orphanedAtTick <= GEOMETRY_OWNER_HANDOFF_TICKS)
				continue;
			removeGeometry(geometry);
			removedGeometry = true;
		}
		long retainedWeight = SUBJECT_GEOMETRIES.values().stream()
			.mapToLong(TableGeometry::cacheWeight)
			.sum();
		// Last-use ordering evicts the coldest live geometries first. A short grace window protects
		// visible geometry across ClientTick.Post -> RenderFrame ordering and controller handoffs.
		if (retainedWeight > MAX_GEOMETRY_CACHE_WEIGHT) {
			List<TableGeometry> evictionCandidates = SUBJECT_GEOMETRIES.values().stream()
				.filter(geometry -> geometry.lastSeenTick < now - GEOMETRY_CACHE_RECENT_TICKS)
				.sorted(java.util.Comparator.comparingLong(geometry -> geometry.lastSeenTick))
				.toList();
			for (TableGeometry geometry : evictionCandidates) {
				if (retainedWeight <= MAX_GEOMETRY_CACHE_WEIGHT)
					break;
				retainedWeight -= geometry.cacheWeight();
				removeGeometry(geometry);
				removedGeometry = true;
			}
		}
		if (removedGeometry)
			geometryGeneration++;
		if (pendingCut != null && !TABLES.containsKey(new SubjectKey(pendingCut.tablePos, pendingCut.subjectId)))
			abortPendingCut();
		if (pendingGlueCut != null
			&& !TABLES.containsKey(new SubjectKey(pendingGlueCut.tablePos, pendingGlueCut.subjectId)))
			abortPendingGlueCut();
		if (pendingGlue != null && (!TABLES.containsKey(new SubjectKey(pendingGlue.selection.tablePos,
			pendingGlue.selection.subjectId)) || !isSurgicalGlue(player.getItemInHand(pendingGlue.hand))))
			clearPendingGlue();
		if (pendingSymmetry != null && (!TABLES.containsKey(new SubjectKey(pendingSymmetry.selection.tablePos,
			pendingSymmetry.selection.subjectId))
			|| !isSymmetryWand(player.getItemInHand(pendingSymmetry.hand))))
			clearPendingSymmetry();
		if (pendingSymmetryReference != null
			&& (!(level.getBlockEntity(pendingSymmetryReference.referenceHit.tablePos)
				instanceof SurgicalTableBlockEntity table)
				|| table.clientDataRevision() != pendingSymmetryReference.tableRevision))
			clearPendingSymmetry();
		if (glueEditor != null) {
			if (!(level.getBlockEntity(glueEditor.preview.ownerPos) instanceof SurgicalTableBlockEntity table)
				|| table.clientDataRevision() != glueEditor.preview.tableRevision
				|| !isSmartGlue(player.getItemInHand(glueEditor.hand))) {
				clearPendingGlue();
			} else
				refreshGlueEditGuide(player, level, glueEditor);
		}
	}

	@SubscribeEvent
	public static void onRenderFrame(RenderFrameEvent.Pre event) {
		SurgicalCapturedRenderPlan.beginFrame();
		SurgicalTableRenderer.beginFrame();
		if (Minecraft.getInstance().level != null) {
			updatePendingVisualCommit(Minecraft.getInstance().level);
			updateBatchCutAnimation(Minecraft.getInstance().level);
		}
		updatePlacementPreview();
		updateSelections();
	}

	private static void updatePlacementPreview() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (retainsPlacementPreview()) {
			placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
			if (level != null && level.getGameTime() != lastPlacementOutlineTick) {
				Outliner.getInstance().keep(PLACEMENT_OUTLINE_SLOT);
				lastPlacementOutlineTick = level.getGameTime();
			}
			return;
		}
		if (placementSuppression != null && player != null && level != null) {
			PlacementSuppression suppression = placementSuppression;
			boolean inventoryUpdated = !ItemStack.isSameItemSameComponents(suppression.sourceBox,
				player.getItemInHand(suppression.hand));
			if (inventoryUpdated || level.getGameTime() >= suppression.expiresAtTick) {
				placementSuppression = null;
			} else {
				rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
				return;
			}
		}
		if (player == null || level == null || minecraft.screen != null
			|| pendingCut != null || pendingGlueCut != null
			|| !(minecraft.hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock)) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}

		InteractionHand hand = null;
		PlacementSource source = null;
		for (InteractionHand candidateHand : HANDS) {
			ItemStack held = player.getItemInHand(candidateHand);
			if (!(held.getItem() instanceof CapturedEntityBoxItem)
				|| !CapturedEntityBoxHelper.hasCapturedEntity(held))
				continue;
			PlacementCandidate prepared = placementCandidateFor(held, level);
			if (prepared.source() != null) {
				hand = candidateHand;
				source = prepared.source();
				break;
			}
		}
		if (hand == null || source == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT);
			return;
		}

		SurgicalTablePlane.Plane plane = clientPlane(level, hit.getBlockPos());
		if (!plane.valid() || plane.source() == null || plane.workArea().isEmpty()) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		Vec3 target = tableSurfaceTarget(playerRay(player), plane.workArea().y() + 1.01d);
		BlockPos ownerPos = plane.source();
		Direction placementFacing = player.getDirection();
		SurgicalTableBlockEntity controller = level.getBlockEntity(ownerPos) instanceof SurgicalTableBlockEntity table
			? table : null;
		if (controller == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		if (controller.getSubjects().size() > SurgicalTableBlockEntity.MAX_SUBJECTS - source.subjectSlots()) {
			rejectPlacementPreview(SurgicalTablePlacementResult.TABLE_FULL);
			return;
		}
		boolean projectSourceGeometry = controller.clientProjectsSourceGeometry();
		int tableRevision = controller.clientDataRevision();
		PlacementGeometry placementGeometry = source.measure(placementFacing, projectSourceGeometry);
		if (placementGeometry == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.MODEL_UNAVAILABLE);
			return;
		}
		PlacementPreview previous = placementPreview;
		if (previous != null && previous.ownerPos.equals(ownerPos) && previous.hand == hand
			&& previous.source == source && previous.facing == placementFacing
			&& previous.projectSourceGeometry == projectSourceGeometry
			&& previous.tableRevision == tableRevision && previous.workArea == plane.workArea()
			&& sameHorizontalTarget(target, previous.targetX, previous.targetZ)) {
			placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
			refreshPlacementFeedback(player, level);
			return;
		}
		List<SurgicalTableLayout.Footprint> occupied = SurgicalTablePlane.occupiedFootprints(level, plane, -1);
		if (occupied == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		SurgicalClientTopology.PlacementPlan plan;
		DiscoveredPlacement discovered = DiscoveredPlacement.EMPTY;
		AABB localBounds = placementGeometry.bounds();
		SurgicalModelRenderContext.CubeGeometry renderedBounds = boundsGeometry(localBounds,
			Vec3.atLowerCornerOf(ownerPos));
		if (source.assembly == null) {
			List<SurgicalModelRenderContext.CubeGeometry> worldCubes = translateCubes(
				placementGeometry.discoveredCubes(), Vec3.atLowerCornerOf(ownerPos));
			SurgicalClientTopology.DiscoveredPlacementPlan discoveredPlan =
				SurgicalClientTopology.planDiscoveredPlacement(placementGeometry.discoveredCubeCount(),
					placementGeometry.discoveredSeams(), renderedBounds, worldCubes, plane.workArea(), target.x,
					target.z, occupied);
			if (discoveredPlan == null) {
				rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
				return;
			}
			plan = discoveredPlan.placement();
			discovered = new DiscoveredPlacement(placementGeometry.discoveredCubeCount(),
				placementGeometry.discoveredSeams(), discoveredPlan.componentFootprints());
		} else {
			plan = SurgicalClientTopology.planInitialPlacement(List.of(renderedBounds),
				plane.workArea(), target.x, target.z, occupied);
		}
		if (plan == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
			return;
		}
		plan = exactInitialPlacement(source, placementGeometry, plan);
		List<SurgicalTableLayout.Proposal> sourceLayouts = compositeSourceLayouts(source, placementGeometry,
			ownerPos, plan, plane, occupied);
		if (sourceLayouts == null || !SurgicalTableLayout.validateSubjectPlacement(plane, source.assembly,
			placementFacing, placementGeometry.layPose(), plan.originOffsetX(), plan.originOffsetZ(),
			plan.proposal(), discovered.cubeCount(), discovered.seams(), discovered.footprints(),
			sourceLayouts, occupied)) {
			rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
			return;
		}

		placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
		placementPreview = new PlacementPreview(ownerPos, hand, source, placementFacing,
			placementGeometry.layPose(), plan,
			placementGeometry.sources(), discovered, sourceLayouts, projectSourceGeometry,
			plane.workArea(), target.x, target.z, tableRevision);
		SurgicalTableLayout.Footprint footprint = plan.proposal().footprints().getFirst();
		Outliner.getInstance().showAABB(PLACEMENT_OUTLINE_SLOT,
			new AABB(footprint.minX(), plane.workArea().y() + 1.002d, footprint.minZ(),
				footprint.maxX(), plane.workArea().y() + 1.012d, footprint.maxZ()))
			.colored(PonderPalette.GREEN.getColor())
			.disableLineNormals()
			.lineWidth(HIGHLIGHT_LINE_WIDTH);
		lastPlacementOutlineTick = level.getGameTime();
	}

	private static SurgicalClientTopology.PlacementPlan exactInitialPlacement(PlacementSource source,
		PlacementGeometry geometry, SurgicalClientTopology.PlacementPlan plan) {
		if (source.assembly == null || source.isComposite())
			return plan;
		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(source.assembly.presentCubes().cardinality());
		BitSet present = source.assembly.presentCubes();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			Vec3 offset = geometry.cubeOffsets().getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		SurgicalTableLayout.Proposal exact = new SurgicalTableLayout.Proposal(offsets,
			plan.proposal().footprints());
		return new SurgicalClientTopology.PlacementPlan(plan.originOffsetX(), plan.originOffsetZ(), exact);
	}

	private static Map<Integer, Vec3> layoutOffsets(SurgicalTableLayout.Proposal layout) {
		Map<Integer, Vec3> offsets = new HashMap<>(layout.offsets().size());
		for (SurgicalTableLayout.CubeOffset offset : layout.offsets()) {
			Vec3 value = new Vec3(offset.x(), offset.y(), offset.z());
			if (value.lengthSqr() > 1.0e-24d)
				offsets.put(offset.cubeId(), value);
		}
		return Map.copyOf(offsets);
	}

	@Nullable
	private static List<SurgicalTableLayout.Proposal> compositeSourceLayouts(PlacementSource source,
		PlacementGeometry geometry, BlockPos ownerPos, SurgicalClientTopology.PlacementPlan plan,
		SurgicalTablePlane.Plane plane, List<SurgicalTableLayout.Footprint> occupied) {
		if (!source.isComposite())
			return List.of();
		if (source.assembly == null || geometry.sources().size() != source.assembly.sources().size())
			return null;
		Vec3 placementOffset = Vec3.atLowerCornerOf(ownerPos)
			.add(plan.originOffsetX(), 0.0d, plan.originOffsetZ());
		List<SurgicalTableLayout.Proposal> layouts = new ArrayList<>(geometry.sources().size());
		for (SourcePlacementGeometry sourceGeometry : geometry.sources()) {
			SurgicalAssembly.PlacedSource placedSource = sourceGeometry.placedSource();
			SurgicalAssembly.Source assemblySource = placedSource.source();
			List<SurgicalModelRenderContext.CubeGeometry> worldCubes = translateCubes(
				sourceGeometry.baseCubes(), placementOffset);
			SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
				assemblySource.cubeCount(), assemblySource.presentCubes(), assemblySource.seams(),
				assemblySource.cutSeams(), worldCubes, sourceGeometry.renderOffsets(), plane.workArea(), occupied);
			if (planned == null)
				return null;
			layouts.add(planned.proposal());
		}
		return List.copyOf(layouts);
	}

	private static PlacementCandidate placementCandidateFor(ItemStack held, ClientLevel level) {
		if (placementCandidate != null && ItemStack.isSameItemSameComponents(placementCandidate.box(), held))
			return placementCandidate;
		Entity captured = CapturedEntityBoxHelper.createCapturedEntity(held, level);
		if (captured == null)
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		MimicProfile profile;
		SurgicalAssembly assembly = null;
		if (captured instanceof SlimeBionicEntity bionic) {
			assembly = bionic.getAssembly();
			if (assembly == null)
				return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_ASSEMBLY);
			profile = assembly.profile();
		} else if (captured instanceof LivingEntity living && SlimeMimicHandler.isSlimeMimic(living)) {
			profile = MimicProfile.capture(living);
			if (profile == null)
				return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		} else if (captured instanceof LivingEntity)
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT);
		else
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		return cachePlacementCandidate(held,
			new PlacementSource(held.copyWithCount(1), profile, assembly), SurgicalTablePlacementResult.SUCCESS);
	}

	private static PlacementCandidate cachePlacementCandidate(ItemStack held, @Nullable PlacementSource source,
		SurgicalTablePlacementResult result) {
		placementCandidate = new PlacementCandidate(held.copyWithCount(1), source, result);
		return placementCandidate;
	}

	private static void clearPlacementPreview() {
		if (placementPreview == null && lastPlacementOutlineTick == Long.MIN_VALUE)
			return;
		placementPreview = null;
		Outliner.getInstance().remove(PLACEMENT_OUTLINE_SLOT);
		lastPlacementOutlineTick = Long.MIN_VALUE;
	}

	private static void rejectPlacementPreview(SurgicalTablePlacementResult result) {
		clearPlacementPreview();
		placementPreviewResult = result;
	}

	private static void refreshPlacementFeedback(LocalPlayer player, ClientLevel level) {
		long tick = level.getGameTime();
		if (tick != lastPlacementOutlineTick) {
			Outliner.getInstance().keep(PLACEMENT_OUTLINE_SLOT);
			lastPlacementOutlineTick = tick;
		}
	}

	@SubscribeEvent
	public static void renderPlacementPreview(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || placementPreview == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		PlacementPreview placement = placementPreview;

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(placement.ownerPos.getX() - camera.x, placement.ownerPos.getY() - camera.y,
			placement.ownerPos.getZ() - camera.z);
		poseStack.translate(placement.plan.originOffsetX(), 0.0d, placement.plan.originOffsetZ());
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		int light = LevelRenderer.getLightColor(minecraft.level, placement.ownerPos.above());
		float partialTicks = AnimationTickHolder.getPartialTicks();
		if (placement.source.isComposite()) {
			for (int sourceIndex = 0; sourceIndex < placement.sourceGeometries.size(); sourceIndex++) {
				SourcePlacementGeometry sourceGeometry = placement.sourceGeometries.get(sourceIndex);
				SurgicalAssembly.PlacedSource placedSource = sourceGeometry.placedSource();
				SurgicalAssembly.Source assemblySource = placedSource.source();
				LivingEntity preview = placement.source.preview(assemblySource.profile());
				if (preview == null)
					continue;
				poseStack.pushPose();
				poseStack.translate(placedSource.originOffset().x, placedSource.originOffset().y,
					placedSource.originOffset().z);
				SurgicalTablePoseResolver.resolve(placedSource.layPose()).apply(poseStack);
				SurgicalSourceModelRenderer.render(preview, assemblySource.cubeCount(),
					assemblySource.presentCubes(), sourceGeometry.previewOffsets(),
					sourceGeometry.renderRotations(),
					poseStack, buffer, light,
					0.0f, partialTicks, false, camera, placement.projectSourceGeometry);
				poseStack.popPose();
			}
		} else {
			LivingEntity preview = placement.source.preview();
			if (preview != null) {
				SurgicalTablePoseResolver.resolve(placement.layPose).apply(poseStack);
				SurgicalSourceModelRenderer.render(preview, placement.source.cubeCount(),
					placement.source.presentCubes(), layoutOffsets(placement.plan.proposal()), poseStack, buffer, light,
					0.0f, partialTicks, false, camera, placement.projectSourceGeometry);
			}
		}
		poseStack.popPose();
		buffer.endBatch();
	}

	@SubscribeEvent
	public static void renderGluePreview(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || gluePreview == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		GluePreview preview = gluePreview;
		if (level == null
			|| !(level.getBlockEntity(preview.ownerPos) instanceof SurgicalTableBlockEntity table))
			return;

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		int light = LevelRenderer.getLightColor(level, preview.ownerPos.above());
		float partialTicks = AnimationTickHolder.getPartialTicks();
		boolean projectSourceGeometry = SurgicalTableRenderer.projectsSourceGeometry(table);
		for (GlueSubjectPreview moved : preview.subjects) {
			SurgicalSubject subject = table.getSubject(moved.subjectId);
			if (subject == null)
				continue;
			LivingEntity entity = SurgicalSourceModelRenderer.preview(subject.profile());
			if (entity == null)
				continue;
			poseStack.pushPose();
			poseStack.translate(preview.ownerPos.getX() - camera.x + subject.originOffsetX(),
				preview.ownerPos.getY() - camera.y,
				preview.ownerPos.getZ() - camera.z + subject.originOffsetZ());
			SurgicalTablePoseResolver.resolve(moved.pose).apply(poseStack);
			SurgicalSourceModelRenderer.render(entity, subject.cubeCount(), moved.cubes, moved.offsets,
				moved.rotations,
				poseStack, buffer, light, 0.0f, partialTicks, false, camera, projectSourceGeometry,
				moved.editable ? GLUE_FIRST_PREVIEW_ALPHA : 1.0f);
			poseStack.popPose();
		}
		buffer.endBatch();
	}

	private static void updateSelections() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null) {
			abortPendingCut();
			clearSelections();
			return;
		}
		if (retainsGluePreview()) {
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			clearSeamHighlight();
			return;
		}
		if (pendingCut != null) {
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			updatePendingCut(player, level);
			return;
		}
		if (pendingGlueCut != null) {
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			updatePendingGlueCut(player, level);
			return;
		}
		if (glueEditor != null) {
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			seamSelection = null;
			cubeSelection = null;
			componentSelection = null;
			wrenchSelection = null;
			clearSeamHighlight();
			refreshGlueEditGuide(player, level, glueEditor);
			return;
		}

		boolean holdingShears = player.getMainHandItem().is(Items.SHEARS)
			|| player.getOffhandItem().is(Items.SHEARS);
		boolean holdingEmptyBox = isEmptyBox(player.getMainHandItem()) || isEmptyBox(player.getOffhandItem());
		boolean holdingEmptyLargeBox = isEmptyLargeBox(player.getMainHandItem())
			|| isEmptyLargeBox(player.getOffhandItem());
		boolean holdingGlue = isSurgicalGlue(player.getMainHandItem()) || isSurgicalGlue(player.getOffhandItem());
		boolean holdingSymmetry = isSymmetryWand(player.getMainHandItem())
			|| isSymmetryWand(player.getOffhandItem());
		boolean holdingHoney = player.getMainHandItem().is(Items.HONEY_BOTTLE)
			|| player.getOffhandItem().is(Items.HONEY_BOTTLE);
		boolean holdingJoint = heldLimbType(player.getMainHandItem()) != null
			|| heldLimbType(player.getOffhandItem()) != null;
		boolean holdingWrench = CBWrenchHelper.isWrench(player.getMainHandItem())
			|| CBWrenchHelper.isWrench(player.getOffhandItem());
		if (!holdingJoint && pendingLimb != null)
			clearPendingLimb();
		boolean highlightingDirectConnections = holdingShears && Screen.hasControlDown();
		if (!holdingShears && !holdingEmptyBox && !holdingEmptyLargeBox && !holdingGlue && !holdingSymmetry
			&& !holdingHoney
			&& !holdingJoint && !holdingWrench) {
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			gluePreview = null;
			seamSelection = null;
			cubeSelection = null;
			componentSelection = null;
			symmetrySelection = null;
			limbSelection = null;
			wrenchSelection = null;
			lastSelectionMode = Integer.MIN_VALUE;
			lastSelectionRay = null;
			clearSeamHighlight();
			return;
		}
		Ray ray = playerRay(player);
		int selectionMode = (holdingShears ? 1 : 0) | (holdingEmptyBox ? 2 : 0)
			| (holdingEmptyLargeBox ? 4 : 0) | (holdingGlue ? 8 : 0)
			| (highlightingDirectConnections ? 16 : 0) | (holdingHoney ? 32 : 0)
			| (holdingJoint ? 64 : 0) | (holdingWrench ? 128 : 0)
			| (holdingSymmetry ? 256 : 0)
			| (pendingSymmetryReference != null && Screen.hasControlDown() ? 512 : 0);
		if (lastSelectionTick == level.getGameTime() && lastSelectionGeneration == geometryGeneration
			&& lastSelectionMode == selectionMode && ray.equals(lastSelectionRay)
			&& pendingGlue == lastSelectionPendingGlue && pendingSymmetry == lastSelectionPendingSymmetry
			&& pendingSymmetryReference == lastSelectionPendingSymmetryReference
			&& pendingLimb == lastSelectionPendingLimb) {
			refreshCurrentSelectionHighlight();
			return;
		}
		lastSelectionTick = level.getGameTime();
		lastSelectionGeneration = geometryGeneration;
		lastSelectionMode = selectionMode;
		lastSelectionRay = ray;
		lastSelectionPendingGlue = pendingGlue;
		lastSelectionPendingSymmetry = pendingSymmetry;
		lastSelectionPendingSymmetryReference = pendingSymmetryReference;
		lastSelectionPendingLimb = pendingLimb;
		CubeHit cubeHit = holdingShears || holdingEmptyBox || holdingGlue || holdingSymmetry || holdingHoney || holdingJoint
			|| holdingWrench
			? findNearestCubeHit(player, level, ray) : null;
		CubeHit glueHit = holdingGlue || holdingSymmetry && pendingSymmetry == null ? snapGlueHit(cubeHit) : null;
		hoveredGluePoint = glueHit == null ? null : glueHit.gluePoint;
		symmetrySelection = null;
		symmetryPlaneSelection = null;
		if (pendingGlue != null && glueHit != null) {
			int revision = level.getBlockEntity(glueHit.tablePos) instanceof SurgicalTableBlockEntity table
				? table.clientDataRevision() : Integer.MIN_VALUE;
			if (gluePreview == null || !gluePreview.matches(pendingGlue, glueHit, revision))
				gluePreview = planGluePreview(level, pendingGlue, glueHit);
		} else if (pendingSymmetryReference != null && pendingSymmetry != null) {
			boolean singleCube = pendingSymmetryReference.singleCube(Screen.hasControlDown());
			symmetryPlaneSelection = findSymmetryPlane(ray,
				pendingSymmetryReference.mirrorBounds(singleCube),
				pendingSymmetryReference.planes(singleCube));
			SymmetryTarget target = symmetryPlaneSelection == null ? null
				: symmetryTarget(pendingSymmetryReference, symmetryPlaneSelection, singleCube);
			symmetrySelection = pendingSymmetryReference.selection(singleCube);
			gluePreview = target == null ? null : planSymmetryPreview(level, pendingSymmetry, target);
		} else if (pendingSymmetry != null && cubeHit != null) {
			SymmetryReference reference = findSymmetryReference(cubeHit);
			symmetrySelection = reference == null ? null : reference.selection(false);
			gluePreview = null;
		} else {
			gluePreview = null;
		}
		seamSelection = holdingShears && !highlightingDirectConnections
			? findSeamSelection(player, level, ray, cubeHit) : null;
		cubeSelection = holdingEmptyBox ? findConnectedComponentSelection(cubeHit, true) : null;
		limbSelection = holdingJoint ? findLimbTargetSelection(cubeHit) : null;
		wrenchSelection = holdingWrench ? findLimbJointSelection(player, level, ray, cubeHit) : null;
		componentSelection = holdingHoney
			? findConnectedComponentSelection(cubeHit)
			: holdingGlue
			? (pendingGlue == null ? findConnectedComponentSelection(glueHit) : findCubeSelection(glueHit))
			: holdingSymmetry && pendingSymmetry == null
			? findConnectedComponentSelection(glueHit)
			: highlightingDirectConnections
			? findDirectConnectionSelection(cubeHit)
			: !holdingShears && holdingEmptyLargeBox ? findConnectedComponentSelection(cubeHit) : null;
		refreshCurrentSelectionHighlight();
	}

	private static void refreshCurrentSelectionHighlight() {
		refreshGluePointHighlight();
		if (highlightGlueTargetCube())
			return;
		if (pendingLimb != null) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(pendingLimb.selection.cubeEdges);
			if (limbSelection != null)
				edges.addAll(limbSelection.cubeEdges);
			SEAM_OUTLINE.clear();
			COMBINATION_OUTLINE.clear();
			GLUE_JOINT_OUTLINE.clear();
			LIMB_JOINT_OUTLINE.clear();
			clearSymmetryPlaneHighlight();
			CUBE_OUTLINE.show(edges, CUBE_HIGHLIGHT_COLOR);
			return;
		}
		boolean highlightingBox = (lastSelectionMode & 2) != 0;
		if (symmetrySelection != null)
			highlightSelection(symmetrySelection);
		else if (wrenchSelection != null)
			highlightSelection(wrenchSelection.selection);
		else if (limbSelection != null)
			highlightSelection(limbSelection);
		else if (highlightingBox && cubeSelection != null)
			highlightSelection(cubeSelection, true);
		else if (componentSelection != null)
			highlightSelection(componentSelection, highlightingBox);
		else if (seamSelection != null)
			highlightSelection(seamSelection);
		else if (cubeSelection != null)
			highlightSelection(cubeSelection, highlightingBox);
		else
			clearSeamHighlight();
		refreshSymmetryPlaneHighlight();
	}

	private static void refreshGluePointHighlight() {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		if (pendingGlue != null)
			edges.addAll(pendingGlue.point.markerEdges);
		if (pendingSymmetry != null)
			edges.addAll(pendingSymmetry.point.markerEdges);
		if (hoveredGluePoint != null
			&& (pendingGlue == null || !hoveredGluePoint.markerEdges.equals(pendingGlue.point.markerEdges))
			&& (pendingSymmetry == null
				|| !hoveredGluePoint.markerEdges.equals(pendingSymmetry.point.markerEdges)))
			edges.addAll(hoveredGluePoint.markerEdges);
		if (edges.isEmpty())
			GLUE_POINT_OUTLINE.clear();
		else
			GLUE_POINT_OUTLINE.show(edges, CUBE_HIGHLIGHT_COLOR);
	}

	/** During the second glue click, only the hovered target cube is outlined at its preview position. */
	private static boolean highlightGlueTargetCube() {
		if (gluePreview == null || pendingGlue == null)
			return false;
		SurgicalModelRenderContext.CubeGeometry cube = previewCube(gluePreview.subjects,
			gluePreview.targetSubjectId, gluePreview.targetCubeId);
		if (cube == null)
			return false;
		SEAM_OUTLINE.clear();
		CUBE_OUTLINE.show(SurgicalClientTopology.cubeEdges(cube), CUBE_HIGHLIGHT_COLOR);
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		LIMB_JOINT_OUTLINE.clear();
		clearSymmetryPlaneHighlight();
		return true;
	}

	private static void refreshSymmetryPlaneHighlight() {
		if (pendingSymmetryReference == null) {
			clearSymmetryPlaneHighlight();
			return;
		}
		boolean singleCube = pendingSymmetryReference.singleCube(Screen.hasControlDown());
		List<SurgicalClientTopology.Edge> options = new ArrayList<>();
		for (SymmetryPlaneCandidate plane : pendingSymmetryReference.planes(singleCube))
			options.addAll(plane.edges);
		SYMMETRY_PLANE_OPTIONS_OUTLINE.show(options, PonderPalette.WHITE.getColor());
		if (symmetryPlaneSelection == null)
			SYMMETRY_PLANE_SELECTED_OUTLINE.clear();
		else
			SYMMETRY_PLANE_SELECTED_OUTLINE.show(symmetryPlaneSelection.edges, CUBE_HIGHLIGHT_COLOR);
	}

	private static void clearSymmetryPlaneHighlight() {
		SYMMETRY_PLANE_OPTIONS_OUTLINE.clear();
		SYMMETRY_PLANE_SELECTED_OUTLINE.clear();
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null)
			return;
		KeyMapping key = event.getKeyMapping();
		if (key == minecraft.options.keyAttack && suppressAttackUntilRelease) {
			suppressAttack(event);
			return;
		}
		if (batchCutAnimation != null && (event.isUseItem() || key == minecraft.options.keyAttack))
			batchCutAnimation = null;
		if (minecraft.level != null)
			updatePendingVisualCommit(minecraft.level);
		if (pendingVisualCommit != null && key == minecraft.options.keyUse && event.isUseItem()) {
			consumeInteraction(event, event.getHand());
			return;
		}
		if (key == minecraft.options.keyAttack && cancelPendingInteraction()) {
			suppressAttackUntilRelease = true;
			suppressAttack(event);
			return;
		}
		if (key != minecraft.options.keyUse || !event.isUseItem())
			return;

		InteractionHand hand = event.getHand();
		ItemStack held = minecraft.player.getItemInHand(hand);
		ClientLevel level = minecraft.level;
		if (level == null)
			return;
		if (pendingCut != null) {
			confirmPendingCut(minecraft.player);
			consumeInteraction(event, hand);
			return;
		}
		if (pendingGlueCut != null) {
			confirmPendingGlueCut(minecraft.player);
			consumeInteraction(event, hand);
			return;
		}
		if (CapturedEntityBoxHelper.hasCapturedEntity(held)
			&& tryPlaceSubject(minecraft.player, level, hand, held)) {
			consumeInteraction(event, hand);
			return;
		}

		Selection selected;
		Ray ray = playerRay(minecraft.player);
		CubeHit cubeHit = findNearestCubeHit(minecraft.player, level, ray);
		if (CBWrenchHelper.isWrench(held)) {
			LimbJointSelection selectedJoint = findLimbJointSelection(minecraft.player, level, ray, cubeHit);
			wrenchSelection = selectedJoint;
			if (selectedJoint == null)
				return;
			CBPackets.sendToServer(new SurgicalTableLimbRemovalPacket(selectedJoint.selection.tablePos,
				hand, selectedJoint.joint));
			clearSelections();
			consumeInteraction(event, hand);
			return;
		}
		SurgicalLimbType limbType = heldLimbType(held);
		if (limbType != null) {
			if (handleLimbClick(minecraft.player, level, hand, limbType, cubeHit))
				consumeInteraction(event, hand);
			return;
		}
		if (isSymmetryWand(held)) {
			if (handleSymmetryClick(minecraft.player, level, hand, cubeHit))
				consumeInteraction(event, hand);
			return;
		}
		if (isSurgicalGlue(held)) {
			if (handleGlueClick(minecraft.player, level, hand, cubeHit))
				consumeInteraction(event, hand);
			return;
		} else if (held.is(Items.SHEARS)) {
			if (Screen.hasControlDown()) {
				selected = findDirectConnectionCutSelection(cubeHit);
				componentSelection = selected;
				seamSelection = null;
				if (selected == null)
					return;
				if (selected.combination) {
					sendInteraction(selected, hand,
						SurgicalTableInteractionPacket.Action.DETACH_COMBINATION,
						SurgicalTableLayout.Proposal.EMPTY);
					consumeInteraction(event, hand);
					return;
				}
				BatchCutLayout planned = planBatchCut(level, selected);
				if (planned == null) {
					showNoSpace(minecraft.player);
					consumeInteraction(event, hand);
					return;
				}
				beginBatchCutAnimation(level, selected, planned);
				CBPackets.sendToServer(new SurgicalTableBatchCutPacket(selected.tablePos, hand,
					selected.subjectId, selected.targetId, selected.observedCubeCount, selected.seams,
					planned.layout.proposal(), planned.groupDeltas));
			} else {
				selected = findSeamSelection(minecraft.player, level, ray, cubeHit);
				seamSelection = selected;
				if (selected == null)
					return;
				if (selected.combination)
					sendInteraction(selected, hand,
						SurgicalTableInteractionPacket.Action.BREAK_COMBINATION,
						SurgicalTableLayout.Proposal.EMPTY);
				else if (selected.glueJoint)
					beginGlueCut(level, selected, hand);
				else
					beginSingleCut(level, selected, hand);
			}
		} else if (held.is(Items.HONEY_BOTTLE)) {
			selected = findConnectedComponentSelection(cubeHit);
			componentSelection = selected;
			if (selected == null)
				return;
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.COMBINE,
				SurgicalTableLayout.Proposal.EMPTY);
		} else if (isEmptyBox(held)) {
			selected = findConnectedComponentSelection(cubeHit);
			cubeSelection = selected;
			if (selected == null)
				return;
			PackedBodyMetrics metrics = selectedBodyMetrics(selected);
			if (metrics == null) {
				showNoSpace(minecraft.player);
				consumeInteraction(event, hand);
				return;
			}
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.PACK,
				SurgicalTableLayout.Proposal.EMPTY, metrics.bodyBounds(), metrics.hitboxGeometry(),
				metrics.attackGeometry());
		} else {
			return;
		}
		consumeInteraction(event, hand);
	}

	/** Cancels every staged table interaction without letting the attack reach the world. */
	private static boolean cancelPendingInteraction() {
		if (pendingCut == null && pendingGlueCut == null && pendingGlue == null
			&& pendingSymmetry == null && pendingLimb == null)
			return false;
		abortPendingCut();
		abortPendingGlueCut();
		clearPendingGlue();
		clearPendingSymmetry();
		clearPendingLimb();
		clearSelections();
		return true;
	}

	private static void suppressAttack(InputEvent.InteractionKeyMappingTriggered event) {
		event.setSwingHand(false);
		event.setCanceled(true);
	}

	/**
	 * Two right-clicks make one joint. The first click records the part that will rotate, the second
	 * names the part it rotates around, and only then does the server hear about it.
	 */
	private static boolean handleLimbClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		SurgicalLimbType type, @Nullable CubeHit hit) {
		Selection selected = findLimbTargetSelection(hit);
		if (selected == null)
			return pendingLimb != null;
		if (pendingLimb == null || pendingLimb.hand() != hand || pendingLimb.type() != type) {
			pendingLimb = new PendingLimb(type, hand, selected);
			limbSelection = selected;
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.limb_first_" + type.id()), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location), 0.5f, 1.2f, false);
			return true;
		}

		PendingLimb first = pendingLimb;
		clearPendingLimb();
		if (!first.selection().tablePos.equals(selected.tablePos)
			|| first.selection().subjectId == selected.subjectId
			&& first.selection().targetId == selected.targetId) {
			clearSelections();
			return true;
		}
		CBPackets.sendToServer(new SurgicalTableLimbPacket(selected.tablePos, hand, type,
			limbTarget(first.selection()), limbTarget(selected)));
		clearSelections();
		return true;
	}

	private static SurgicalTableLimbPacket.Target limbTarget(Selection selection) {
		return new SurgicalTableLimbPacket.Target(selection.subjectId, selection.targetId,
			selection.observedCubeCount, selection.seams);
	}

	private static boolean handleGlueClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		@Nullable CubeHit hit) {
		if (glueEditor != null) {
			if (glueEditor.hand != hand || !isSmartGlue(player.getItemInHand(hand))) {
				clearPendingGlue();
				return true;
			}
			confirmGlue(player, level, glueEditor);
			return true;
		}
		hit = snapGlueHit(hit);
		if (hit == null)
			return pendingGlue != null;
		Selection selected = findConnectedComponentSelection(hit);
		if (selected == null)
			return false;
		Vec3 localHit = hit.location.subtract(Vec3.atLowerCornerOf(hit.tablePos));
		if (pendingGlue == null) {
			clearPendingSymmetry();
			pendingGlue = new PendingGlue(selected, localHit, hand, hit.faceIndex, hit.gluePoint);
			componentSelection = null;
			clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.glue_first"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location), 0.5f, 0.85f, false);
			return true;
		}

		PendingGlue first = pendingGlue;
		if (first.hand != hand) {
			clearPendingGlue();
			return true;
		}
		if (!first.selection.tablePos.equals(selected.tablePos)) {
			clearPendingGlue();
			showNoSpace(player);
			return true;
		}
		GluePreview preview = planGluePreview(level, first, hit);
		SurgicalTableLayout.Proposal firstLayout = currentGlueLayout(level, first.selection);
		SurgicalTableLayout.Proposal secondLayout = currentGlueLayout(level, selected);
		SurgicalTablePlane.Plane gluePlane = clientPlane(level, selected.tablePos);
		SurgicalTableBlockEntity glueTable = level.getBlockEntity(selected.tablePos)
			instanceof SurgicalTableBlockEntity table ? table : null;
		SurgicalSubject firstSubject = glueTable == null ? null : glueTable.getSubject(first.selection.subjectId);
		SurgicalSubject secondSubject = glueTable == null ? null : glueTable.getSubject(selected.subjectId);
		if (preview == null || firstLayout == null || secondLayout == null || glueTable == null
			|| firstSubject == null || secondSubject == null
			|| firstSubject.cubeCount() != 0
				&& !glueTable.validateGlueLayout(firstSubject, gluePlane, firstLayout)
			|| firstSubject != secondSubject
				&& secondSubject.cubeCount() != 0
				&& !glueTable.validateGlueLayout(secondSubject, gluePlane, secondLayout)) {
			clearPendingGlue();
			showNoSpace(player);
			return true;
		}
		SurgicalTableGluePacket.Endpoint firstEndpoint = glueEndpoint(first.selection, first.hit,
			firstLayout, first.faceIndex);
		SurgicalTableGluePacket.Endpoint secondEndpoint = glueEndpoint(selected, localHit,
			secondLayout, hit.faceIndex);
		if (firstEndpoint == null || secondEndpoint == null) {
			clearPendingGlue();
			showNoSpace(player);
			return true;
		}
		if (glueTopologyKnown(firstSubject, secondSubject)
			&& !glueTable.canGlueComponents(player.getItemInHand(hand), firstEndpoint.subjectId(),
				firstEndpoint.cubeId(), secondEndpoint.subjectId(), secondEndpoint.cubeId(),
				preview.targetPose, preview.moves, preview.anchorMoves, SurgicalGlueTransform.IDENTITY,
				gluePlane, firstLayout, secondLayout)) {
			clearPendingGlue();
			showNoSpace(player);
			return true;
		}
		if (isSmartGlue(player.getItemInHand(hand))) {
			SurgicalModelRenderContext.CubeGeometry editCube = previewCube(preview.subjects,
				firstEndpoint.subjectId(), firstEndpoint.cubeId());
			Vec3 editAxis = cubeFaceAxis(editCube, first.faceIndex);
			Vec3 axisCenter = editCube == null ? null : cubeCenter(editCube);
			Vec3 faceCenter = cubeFaceCenter(editCube, first.faceIndex);
			if (editAxis == null || axisCenter == null || faceCenter == null
				|| !glueEndpointsActuallyIntersect(preview.subjects, firstEndpoint, secondEndpoint)) {
				clearPendingGlue();
				showNoSpace(player);
				return true;
			}
			gluePreview = preview;
			glueEditor = new GlueEditor(hand, firstEndpoint, secondEndpoint, preview,
				axisCenter, faceCenter, editAxis, glueEditGuideRadius(editCube, axisCenter, editAxis));
			hoveredGluePoint = null;
			GLUE_POINT_OUTLINE.clear();
			clearSeamHighlight();
			refreshGlueEditGuide(player, level, glueEditor);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location), 0.5f, 0.9f, false);
			return true;
		}
		CBPackets.sendToServer(new SurgicalTableGluePacket(selected.tablePos, hand,
			firstEndpoint, secondEndpoint, preview.targetPose,
			preview.moves, preview.anchorMoves, SurgicalGlueTransform.IDENTITY));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location), 0.5f, 0.95f, false);
		commitGluePreview(level, preview);
		return true;
	}

	/** Selects the moving glue point, the installed reference, and finally one of its three centre planes. */
	private static boolean handleSymmetryClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		@Nullable CubeHit hit) {
		if (pendingSymmetry == null) {
			CubeHit glueHit = snapGlueHit(hit);
			if (glueHit == null)
				return false;
			Selection selected = findConnectedComponentSelection(glueHit);
			if (selected == null)
				return false;
			clearPendingGlue();
			Vec3 localHit = glueHit.location.subtract(Vec3.atLowerCornerOf(glueHit.tablePos));
			pendingSymmetry = new PendingGlue(selected, localHit, hand, glueHit.faceIndex, glueHit.gluePoint);
			componentSelection = null;
			clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_first"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(glueHit.location), 0.5f, 1.1f, false);
			return true;
		}

		PendingGlue first = pendingSymmetry;
		if (first.hand != hand) {
			clearPendingSymmetry();
			return true;
		}
		if (pendingSymmetryReference == null) {
			SymmetryReference reference = findSymmetryReference(hit);
			if (reference == null || !first.selection.tablePos.equals(reference.referenceHit.tablePos)) {
				player.displayClientMessage(Component.translatable(
					"message.create_biotech.surgical_table.symmetry_target_required"), true);
				return true;
			}
			pendingSymmetryReference = reference;
			symmetrySelection = reference.selection(false);
			symmetryPlaneSelection = null;
			gluePreview = null;
			componentSelection = null;
			clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_reference"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(reference.referenceHit.location),
				0.5f, 1.15f, false);
			return true;
		}

		boolean singleCube = pendingSymmetryReference.singleCube(Screen.hasControlDown());
		SymmetryPlaneCandidate selectedPlane = findSymmetryPlane(playerRay(player),
			pendingSymmetryReference.mirrorBounds(singleCube), pendingSymmetryReference.planes(singleCube));
		SymmetryTarget target = selectedPlane == null ? null
			: symmetryTarget(pendingSymmetryReference, selectedPlane, singleCube);
		if (target == null) {
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_plane_required"), true);
			return true;
		}
		GluePreview preview = planSymmetryPreview(level, first, target);
		SurgicalTableLayout.Proposal firstLayout = currentGlueLayout(level, first.selection);
		Selection anchorSelection = findCubeSelection(target.mirroredAnchorHit);
		SurgicalTableLayout.Proposal anchorLayout = anchorSelection == null ? null
			: currentGlueLayout(level, anchorSelection);
		SurgicalTablePlane.Plane plane = clientPlane(level, target.referenceHit.tablePos);
		SurgicalTableBlockEntity table = level.getBlockEntity(target.referenceHit.tablePos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject firstSubject = table == null ? null : table.getSubject(first.selection.subjectId);
		SurgicalSubject anchorSubject = table == null || anchorSelection == null ? null
			: table.getSubject(anchorSelection.subjectId);
		if (preview == null || firstLayout == null || anchorLayout == null || table == null
			|| firstSubject == null || anchorSubject == null || anchorSelection == null) {
			clearPendingSymmetry();
			showNoSpace(player);
			return true;
		}
		SurgicalTableGluePacket.Endpoint firstEndpoint = glueEndpoint(first.selection, first.hit,
			firstLayout, first.faceIndex);
		Vec3 anchorHit = target.mirroredAnchorHit.location
			.subtract(Vec3.atLowerCornerOf(target.mirroredAnchorHit.tablePos));
		SurgicalTableGluePacket.Endpoint secondEndpoint = glueEndpoint(anchorSelection, anchorHit,
			anchorLayout, target.mirroredAnchorHit.faceIndex);
		if (firstEndpoint == null || secondEndpoint == null
			|| !glueEndpointsActuallyIntersect(preview.subjects, firstEndpoint, secondEndpoint)
			|| glueTopologyKnown(firstSubject, anchorSubject)
				&& !table.canGlueComponents(player.getItemInHand(hand), firstEndpoint.subjectId(),
					firstEndpoint.cubeId(), secondEndpoint.subjectId(), secondEndpoint.cubeId(),
					preview.targetPose, preview.moves, preview.anchorMoves, target.replayTransform,
					plane, firstLayout, anchorLayout)) {
			clearPendingSymmetry();
			showNoSpace(player);
			return true;
		}

		SurgicalTableSymmetryPacket.Reference reference = new SurgicalTableSymmetryPacket.Reference(
			target.referenceHit.geometry.subjectId, target.referenceHit.cubeId,
			target.referenceHit.geometry.observedCubeCount, target.referenceHit.geometry.seams,
			target.referenceAnchor.subjectKey(), target.referenceAnchor.cubeId(), target.singleCube);
		CBPackets.sendToServer(new SurgicalTableSymmetryPacket(target.referenceHit.tablePos, hand,
			firstEndpoint, secondEndpoint, reference, preview.targetPose, preview.moves, preview.anchorMoves,
			target.replayTransform));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(target.mirroredAnchorHit.location),
			0.5f, 1.2f, false);
		commitSymmetryPreview(level, preview);
		return true;
	}

	private static void confirmGlue(LocalPlayer player, ClientLevel level, GlueEditor editor) {
		GluePreview preview = editor.preview;
		SurgicalTableBlockEntity table = level.getBlockEntity(preview.ownerPos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject firstSubject = table == null ? null : table.getSubject(editor.first.subjectId());
		SurgicalSubject secondSubject = table == null ? null : table.getSubject(editor.second.subjectId());
		SurgicalTablePlane.Plane plane = clientPlane(level, preview.ownerPos);
		if (!glueEndpointsActuallyIntersect(preview.subjects, editor.first, editor.second)
			|| table == null || firstSubject == null || secondSubject == null
			|| glueTopologyKnown(firstSubject, secondSubject)
				&& !table.canGlueComponents(player.getItemInHand(editor.hand), editor.first.subjectId(),
					editor.first.cubeId(), editor.second.subjectId(), editor.second.cubeId(),
					preview.targetPose, preview.moves, preview.anchorMoves, editor.replayTransform,
					plane, editor.first.layout(), editor.second.layout())) {
			clearPendingGlue();
			showNoSpace(player);
			return;
		}
		CBPackets.sendToServer(new SurgicalTableGluePacket(preview.ownerPos, editor.hand,
			editor.first, editor.second, preview.targetPose,
			preview.moves, preview.anchorMoves, editor.replayTransform));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(preview.targetHit), 0.5f, 0.95f, false);
		commitGluePreview(level, preview);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (glueEditor == null || player == null || level == null || minecraft.screen != null)
			return;
		if (!isSmartGlue(player.getItemInHand(glueEditor.hand))) {
			clearPendingGlue();
			return;
		}
		double scroll = event.getScrollDeltaY();
		if (Math.abs(scroll) <= 1.0e-9d)
			return;
		if (!updateGlueEditAxis(player, level, glueEditor)) {
			GLUE_EDIT_OUTLINE.clear();
			event.setCanceled(true);
			return;
		}
		Vec3 editDirection = scroll < 0.0d ? glueEditor.axis.scale(-1.0d) : glueEditor.axis;
		Vec3 translation = Vec3.ZERO;
		SurgicalCubeRotation rotation = SurgicalCubeRotation.IDENTITY;
		if (Screen.hasControlDown())
			rotation = SurgicalCubeRotation.around(editDirection, GLUE_EDIT_ROTATION_STEP);
		else
			translation = editDirection.scale(GLUE_EDIT_TRANSLATION_STEP);
		GluePreview adjusted = adjustGluePreview(level, glueEditor, translation, rotation);
		if (adjusted != null) {
			glueEditor.replayTransform = glueEditor.replayTransform.then(translation, rotation);
			glueEditor.preview = adjusted;
			gluePreview = adjusted;
			AllSoundEvents.SCROLL_VALUE.playAt(level, BlockPos.containing(glueEditor.axisCenter),
				0.35f, Screen.hasControlDown() ? 0.85f : 1.0f, false);
		}
		refreshGlueEditGuide(player, level, glueEditor);
		event.setCanceled(true);
	}

	private static void clearPendingGlue() {
		pendingGlue = null;
		hoveredGluePoint = null;
		gluePreview = null;
		glueEditor = null;
		GLUE_EDIT_OUTLINE.clear();
		GLUE_POINT_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
	}

	private static void clearPendingSymmetry() {
		pendingSymmetry = null;
		pendingSymmetryReference = null;
		symmetryPlaneSelection = null;
		symmetrySelection = null;
		hoveredGluePoint = null;
		gluePreview = null;
		GLUE_POINT_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		clearSymmetryPlaneHighlight();
	}

	private static void commitGluePreview(ClientLevel level, GluePreview preview) {
		beginVisualCommit(level, preview.ownerPos, preview.tableRevision, List.of(), false, true);
		pendingGlue = null;
		hoveredGluePoint = null;
		gluePreview = preview;
		glueEditor = null;
		GLUE_EDIT_OUTLINE.clear();
		GLUE_POINT_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
	}

	private static void commitSymmetryPreview(ClientLevel level, GluePreview preview) {
		beginVisualCommit(level, preview.ownerPos, preview.tableRevision, List.of(), false, true);
		pendingSymmetry = null;
		pendingSymmetryReference = null;
		symmetryPlaneSelection = null;
		symmetrySelection = null;
		hoveredGluePoint = null;
		gluePreview = preview;
		GLUE_POINT_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		clearSymmetryPlaneHighlight();
	}

	private static void refreshGlueEditGuide(LocalPlayer player, ClientLevel level, GlueEditor editor) {
		if (!updateGlueEditAxis(player, level, editor)) {
			GLUE_EDIT_OUTLINE.clear();
			return;
		}
		GLUE_EDIT_OUTLINE.show(glueEditGuideEdges(editor, Screen.hasControlDown()), CUBE_HIGHLIGHT_COLOR);
	}

	private static boolean updateGlueEditAxis(LocalPlayer player, ClientLevel level, GlueEditor editor) {
		GluePreviewCubeHit hit = findNearestGluePreviewHit(player, level, editor);
		Vec3 axis = mappedOriginalFaceNormal(hit);
		if (hit == null || axis == null)
			return false;
		editor.axis = axis;
		editor.axisCenter = cubeCenter(hit.geometry);
		Vec3 faceCenter = cubeFaceCenter(hit.geometry, hit.faceIndex);
		if (faceCenter == null)
			return false;
		editor.faceCenter = faceCenter;
		editor.guideRadius = glueEditGuideRadius(hit.geometry, editor.axisCenter, axis);
		return true;
	}

	@Nullable
	private static GluePreviewCubeHit findNearestGluePreviewHit(LocalPlayer player, ClientLevel level,
		GlueEditor editor) {
		GluePreview preview = editor.preview;
		Ray ray = playerRay(player);
		GluePreviewCubeHit best = null;
		double bestDistance = Double.MAX_VALUE;
		GlueSubjectPreview subject = previewSubject(preview.subjects,
			editor.first.subjectId(), editor.first.cubeId());
		SurgicalModelRenderContext.CubeGeometry cube = subject == null ? null
			: previewCube(subject, editor.first.cubeId());
		if (subject == null || cube == null || !rayIntersectsBounds(ray, cubeBounds(cube), 1.0e-6d))
			return null;
		for (int faceIndex = 0; faceIndex < SurgicalClientTopology.CUBE_FACES.length; faceIndex++) {
			Vec3 location = intersectQuad(ray.start, ray.end, cube,
				SurgicalClientTopology.CUBE_FACES[faceIndex]);
			if (location == null)
				continue;
			double distance = ray.start.distanceToSqr(location);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = new GluePreviewCubeHit(subject, cube, editor.first.cubeId(), faceIndex, location);
		}
		return best != null && isOccluded(level, player, ray.start, best.location, preview.ownerPos)
			? null : best;
	}

	private static List<SurgicalClientTopology.Edge> glueEditGuideEdges(GlueEditor editor, boolean rotating) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		Vec3 axis = editor.axis;
		Vec3 center = editor.axisCenter;
		Vec3 axisU = perpendicularAxis(axis);
		Vec3 axisV = axis.cross(axisU).normalize();
		double radius = editor.guideRadius;
		if (!rotating) {
			double faceDistance = Math.max(0.0d, editor.faceCenter.subtract(center).dot(axis));
			double length = Math.max(faceDistance + 0.25d, radius * 1.25d);
			double headLength = Math.min(0.25d, length * 0.3d);
			double headRadius = headLength * 0.55d;
			Vec3 end = center.add(axis.scale(length));
			Vec3 headBase = end.subtract(axis.scale(headLength));
			edges.add(new SurgicalClientTopology.Edge(center, end));
			edges.add(new SurgicalClientTopology.Edge(end, headBase.add(axisU.scale(headRadius))));
			edges.add(new SurgicalClientTopology.Edge(end, headBase.subtract(axisU.scale(headRadius))));
			edges.add(new SurgicalClientTopology.Edge(end, headBase.add(axisV.scale(headRadius))));
			edges.add(new SurgicalClientTopology.Edge(end, headBase.subtract(axisV.scale(headRadius))));
			return List.copyOf(edges);
		}

		double axisLength = Math.max(0.5d, radius * 1.15d);
		edges.add(new SurgicalClientTopology.Edge(center.subtract(axis.scale(axisLength)),
			center.add(axis.scale(axisLength))));
		Vec3 previous = center.add(axisU.scale(radius));
		for (int segment = 1; segment <= GLUE_EDIT_CIRCLE_SEGMENTS; segment++) {
			double angle = Math.PI * 2.0d * segment / GLUE_EDIT_CIRCLE_SEGMENTS;
			Vec3 current = center.add(axisU.scale(Math.cos(angle) * radius))
				.add(axisV.scale(Math.sin(angle) * radius));
			edges.add(new SurgicalClientTopology.Edge(previous, current));
			previous = current;
		}
		return List.copyOf(edges);
	}

	private static Vec3 perpendicularAxis(Vec3 axis) {
		Vec3 helper = Math.abs(axis.y) < 0.9d ? new Vec3(0.0d, 1.0d, 0.0d)
			: new Vec3(1.0d, 0.0d, 0.0d);
		return axis.cross(helper).normalize();
	}

	@Nullable
	private static Vec3 mappedOriginalFaceNormal(@Nullable GluePreviewCubeHit hit) {
		if (hit == null)
			return null;
		SurgicalModelRenderContext.CubeGeometry base = cubeById(hit.subject.baseCubes, hit.cubeId);
		Vec3 original = cubeFaceAxis(base, hit.faceIndex);
		if (original == null)
			return null;
		SurgicalCubeRotation rotation = hit.subject.rotations
			.getOrDefault(hit.cubeId, SurgicalCubeRotation.IDENTITY);
		Vec3 mapped = rotation.rotate(original).normalize();
		Vec3 visible = cubeFaceAxis(hit.geometry, hit.faceIndex);
		if (visible == null)
			return mapped;
		if (mapped.dot(visible) < 0.0d)
			mapped = mapped.scale(-1.0d);
		return mapped.dot(visible) >= 1.0d - 1.0e-8d ? mapped : visible;
	}

	@Nullable
	private static Vec3 cubeFaceAxis(@Nullable SurgicalModelRenderContext.CubeGeometry cube, int faceIndex) {
		if (cube == null || faceIndex < 0 || faceIndex >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		Vec3 faceCenter = cubeFaceCenter(cube, faceIndex);
		Vec3 axis = faceCenter == null ? Vec3.ZERO : faceCenter.subtract(cubeCenter(cube));
		if (axis.lengthSqr() <= 1.0e-18d) {
			// Opposite faces of a planar cube share its center, so recover their directed
			// normal from the face winding instead of rejecting the glue endpoint.
			int[] face = SurgicalClientTopology.CUBE_FACES[faceIndex];
			List<Vec3> corners = cube.corners();
			Vec3 origin = corners.get(face[0]);
			axis = corners.get(face[1]).subtract(origin)
				.cross(corners.get(face[3]).subtract(origin));
		}
		if (axis.lengthSqr() <= 1.0e-18d)
			return null;
		return axis.normalize();
	}

	@Nullable
	private static Vec3 cubeFaceCenter(@Nullable SurgicalModelRenderContext.CubeGeometry cube, int faceIndex) {
		if (cube == null || faceIndex < 0 || faceIndex >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		int[] face = SurgicalClientTopology.CUBE_FACES[faceIndex];
		List<Vec3> corners = cube.corners();
		return corners.get(face[0]).add(corners.get(face[1])).add(corners.get(face[2]))
			.add(corners.get(face[3])).scale(0.25d);
	}

	/** Encodes a world-space face point so it follows the cube through later table and packing rotations. */
	@Nullable
	private static SurgicalGlueContact glueContact(@Nullable SurgicalModelRenderContext.CubeGeometry cube,
		int preferredFace, Vec3 point) {
		if (cube == null || point == null)
			return null;
		ContactProjection best = null;
		int firstFace = preferredFace >= 0 ? preferredFace : 0;
		int lastFace = preferredFace >= 0 ? preferredFace : SurgicalClientTopology.CUBE_FACES.length - 1;
		for (int faceIndex = firstFace; faceIndex <= lastFace; faceIndex++) {
			if (faceIndex < 0 || faceIndex >= SurgicalClientTopology.CUBE_FACES.length)
				continue;
			int[] face = SurgicalClientTopology.CUBE_FACES[faceIndex];
			Vec3 origin = cube.corners().get(face[0]);
			Vec3 edgeU = cube.corners().get(face[1]).subtract(origin);
			Vec3 edgeV = cube.corners().get(face[3]).subtract(origin);
			double uu = edgeU.dot(edgeU);
			double uv = edgeU.dot(edgeV);
			double vv = edgeV.dot(edgeV);
			double determinant = uu * vv - uv * uv;
			if (uu <= 1.0e-18d || vv <= 1.0e-18d || Math.abs(determinant) <= 1.0e-18d)
				continue;
			Vec3 relative = point.subtract(origin);
			double ru = relative.dot(edgeU);
			double rv = relative.dot(edgeV);
			double u = Math.max(0.0d, Math.min(1.0d, (ru * vv - rv * uv) / determinant));
			double v = Math.max(0.0d, Math.min(1.0d, (rv * uu - ru * uv) / determinant));
			Vec3 projected = origin.add(edgeU.scale(u)).add(edgeV.scale(v));
			double distance = projected.distanceToSqr(point);
			if (best == null || distance < best.distance)
				best = new ContactProjection(new SurgicalGlueContact(faceIndex, u, v), distance);
		}
		return best == null ? null : best.contact;
	}

	@Nullable
	private static Vec3 glueContactPoint(@Nullable SurgicalModelRenderContext.CubeGeometry cube,
		SurgicalGlueContact contact) {
		if (cube == null || contact == null || contact.faceIndex() < 0
			|| contact.faceIndex() >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		int[] face = SurgicalClientTopology.CUBE_FACES[contact.faceIndex()];
		Vec3 origin = cube.corners().get(face[0]);
		return origin.add(cube.corners().get(face[1]).subtract(origin).scale(contact.u()))
			.add(cube.corners().get(face[3]).subtract(origin).scale(contact.v()));
	}

	private static double glueEditGuideRadius(SurgicalModelRenderContext.CubeGeometry cube,
		Vec3 center, Vec3 axis) {
		double radius = 0.0d;
		for (Vec3 corner : cube.corners()) {
			Vec3 relative = corner.subtract(center);
			Vec3 radial = relative.subtract(axis.scale(relative.dot(axis)));
			radius = Math.max(radius, radial.length());
		}
		return Math.max(0.25d, radius + GLUE_EDIT_GUIDE_MARGIN);
	}

	private static boolean glueEndpointsActuallyIntersect(List<GlueSubjectPreview> previews,
		SurgicalTableGluePacket.Endpoint first, SurgicalTableGluePacket.Endpoint second) {
		SurgicalModelRenderContext.CubeGeometry firstCube = previewCube(previews,
			first.subjectId(), first.cubeId());
		SurgicalModelRenderContext.CubeGeometry secondCube = previewCube(previews,
			second.subjectId(), second.cubeId());
		return SurgicalClientTopology.cubesActuallyIntersect(firstCube, secondCube);
	}

	private static boolean glueCubesActuallyIntersect(List<GlueSubjectPreview> previews,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId) {
		return SurgicalClientTopology.cubesActuallyIntersect(
			previewCube(previews, firstSubjectId, firstCubeId),
			previewCube(previews, secondSubjectId, secondCubeId));
	}

	@Nullable
	private static SurgicalModelRenderContext.CubeGeometry previewCube(List<GlueSubjectPreview> previews,
		int subjectId, int cubeId) {
		GlueSubjectPreview preview = previewSubject(previews, subjectId, cubeId);
		return preview == null ? null : previewCube(preview, cubeId);
	}

	@Nullable
	private static GlueSubjectPreview previewSubject(List<GlueSubjectPreview> previews,
		int subjectId, int cubeId) {
		for (GlueSubjectPreview preview : previews)
			if (preview.subjectId == subjectId && preview.cubes.get(cubeId))
				return preview;
		return null;
	}

	@Nullable
	private static SurgicalModelRenderContext.CubeGeometry previewCube(GlueSubjectPreview preview, int cubeId) {
		return preview.transformedCubes.get(cubeId);
	}

	@Nullable
	private static SurgicalModelRenderContext.CubeGeometry transformedPreviewCube(
		List<SurgicalModelRenderContext.CubeGeometry> baseCubes, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations, int cubeId) {
		SurgicalModelRenderContext.CubeGeometry base = cubeById(baseCubes, cubeId);
		if (base == null)
			return null;
		SurgicalCubeRotation rotation = rotations
			.getOrDefault(cubeId, SurgicalCubeRotation.IDENTITY);
		Vec3 offset = offsets.getOrDefault(cubeId, Vec3.ZERO);
		Vec3 center = cubeCenter(base);
		return base.withCorners(base.corners().stream()
			.map(corner -> center.add(rotation.rotate(corner.subtract(center))).add(offset)).toList());
	}

	private static AABB cubeBounds(SurgicalModelRenderContext.CubeGeometry cube) {
		Vec3 first = cube.corners().getFirst();
		AABB bounds = new AABB(first, first);
		for (int corner = 1; corner < cube.corners().size(); corner++) {
			Vec3 point = cube.corners().get(corner);
			bounds = bounds.minmax(new AABB(point, point));
		}
		return bounds;
	}

	@Nullable
	private static SurgicalTableGluePacket.Endpoint glueEndpoint(Selection selection, Vec3 hit,
		SurgicalTableLayout.Proposal layout, int faceIndex) {
		TableGeometry geometry = TABLES.get(new SubjectKey(selection.tablePos, selection.subjectId));
		SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
			: geometry.cubesById.get(selection.targetId);
		Vec3 worldHit = Vec3.atLowerCornerOf(selection.tablePos).add(hit);
		SurgicalGlueContact contact = glueContact(cube, faceIndex, worldHit);
		return contact == null ? null : new SurgicalTableGluePacket.Endpoint(selection.subjectId,
			selection.targetId, selection.observedCubeCount, selection.seams, hit, layout, contact);
	}

	@Nullable
	private static SurgicalTableLayout.Proposal currentGlueLayout(ClientLevel level, Selection selection) {
		TableGeometry geometry = TABLES.get(new SubjectKey(selection.tablePos, selection.subjectId));
		SurgicalTablePlane.Plane plane = clientPlane(level, selection.tablePos);
		SurgicalTableBlockEntity table = level.getBlockEntity(selection.tablePos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject subject = table == null ? null : table.getSubject(selection.subjectId);
		if (table == null || subject == null || !geometryReadyForUse(table, subject, geometry)
			|| !plane.valid() || !selection.tablePos.equals(plane.source()))
			return null;
		List<SurgicalTableLayout.Footprint> occupied = occupiedOutsideEditingGroup(level, plane,
			selection.tablePos, selection.subjectId);
		if (occupied == null)
			return null;
		SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.currentLayout(
			geometry.observedCubeCount, geometry.presentCubes, geometry.seams, geometry.cutSeams,
			geometry.layoutCubes, geometry.offsets, plane.workArea(), occupied);
		return planned == null ? null : planned.proposal();
	}

	@Nullable
	private static GluePreview planSymmetryPreview(ClientLevel level, PendingGlue first,
		SymmetryTarget target) {
		GluePreview baseline = planGluePreview(level, first, target.mirroredAnchorHit);
		return baseline == null || target.replayTransform.isIdentity() ? baseline
			: replayGlueTransform(level, baseline, first.selection.subjectId, first.selection.targetId,
				target.mirroredAnchorHit.geometry.subjectId, target.mirroredAnchorHit.cubeId,
				target.replayTransform);
	}

	/** Locks the installed reference joint and both bounds used by the third-stage plane picker. */
	@Nullable
	private static SymmetryReference findSymmetryReference(@Nullable CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		if (hit == null || level == null
			|| !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject referenceSubject = table.getSubject(hit.geometry.subjectId);
		SurgicalModelRenderContext.CubeGeometry referenceCube = hit.geometry.cubesById.get(hit.cubeId);
		if (referenceSubject == null || referenceCube == null
			|| !geometryReadyForUse(table, referenceSubject, hit.geometry))
			return null;

		SymmetryReference best = null;
		double bestDistance = Double.MAX_VALUE;
		SurgicalGlueJoint.Endpoint referenceEndpoint = new SurgicalGlueJoint.Endpoint(
			referenceSubject.persistentId(), hit.cubeId);
		for (int jointId = 0; jointId < referenceSubject.glueJoints().size(); jointId++) {
			SurgicalGlueJoint joint = referenceSubject.glueJoints().get(jointId);
			if (!joint.touches(referenceSubject.persistentId(), hit.cubeId)
				|| table.isInternalCombinationJoint(joint))
				continue;
			SurgicalGlueJoint.Endpoint anchorEndpoint = joint.other(referenceEndpoint);
			SurgicalSubject anchorSubject = anchorEndpoint == null ? null
				: table.getSubjectByPersistentId(anchorEndpoint.subjectKey());
			SurgicalCombination combination = anchorSubject == null ? null
				: anchorSubject.combinationContaining(anchorEndpoint.cubeId());
			if (anchorSubject == null)
				continue;
			SurgicalGlueTransform recordedReplay = joint.replayFrom(referenceEndpoint);
			// New joints remember which side was originally moved. Selecting their anchor cannot
			// reproduce the forward A -> B operation; legacy joints retain the old identity fallback.
			if (joint.replay() != null && recordedReplay == null)
				continue;
			GlueJointSelection jointSelection = glueJointSelection(hit.tablePos, table, referenceSubject,
				hit.geometry, jointId);
			if (jointSelection == null || jointSelection.contact == null)
				continue;
			double distance = pointToContactDistance(hit.location, jointSelection.contact);
			if (distance >= bestDistance)
				continue;

			TableGeometry anchorGeometry = TABLES.get(new SubjectKey(hit.tablePos, anchorSubject.id()));
			SurgicalModelRenderContext.CubeGeometry anchorCube = anchorGeometry == null ? null
				: anchorGeometry.cubesById.get(anchorEndpoint.cubeId());
			AABB singleCubeBounds = anchorCube == null ? null : cubeBounds(anchorCube);
			AABB honeyBounds = anchorCube == null ? null : combination == null ? singleCubeBounds
				: combinationBounds(hit.tablePos, table, combination);
			if (anchorCube == null || singleCubeBounds == null || honeyBounds == null)
				continue;

			Vec3 originalAnchorContact = joint.replay() == null ? contactCenter(jointSelection.contact)
				: glueContactPoint(anchorCube, joint.replay().anchorContact());
			if (originalAnchorContact == null)
				continue;
			List<SurgicalClientTopology.Edge> singleCubeEdges = SurgicalClientTopology.cubeEdges(anchorCube);
			List<SurgicalClientTopology.Edge> honeyEdges = combination == null ? singleCubeEdges
				: combinationCubeEdges(hit.tablePos, table, combination);
			Selection honeySelection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
				hit.geometry.observedCubeCount, hit.geometry.seams, List.of(), jointSelection.contact.edges(),
				SurgicalClientTopology.cubeEdges(referenceCube), honeyEdges, List.of(), false, false);
			Selection singleCubeSelection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
				hit.geometry.observedCubeCount, hit.geometry.seams, List.of(), jointSelection.contact.edges(),
				SurgicalClientTopology.cubeEdges(referenceCube), singleCubeEdges, List.of(), false, false);
			bestDistance = distance;
			best = new SymmetryReference(honeySelection, singleCubeSelection, hit, anchorEndpoint,
				combination, honeyBounds, singleCubeBounds, symmetryPlanes(honeyBounds),
				symmetryPlanes(singleCubeBounds), recordedReplay, originalAnchorContact,
				table.clientDataRevision());
		}
		return best;
	}

	@Nullable
	private static SymmetryTarget symmetryTarget(SymmetryReference reference,
		SymmetryPlaneCandidate plane, boolean singleCube) {
		ClientLevel level = Minecraft.getInstance().level;
		if (reference == null || plane == null || level == null
			|| !(level.getBlockEntity(reference.referenceHit.tablePos) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != reference.tableRevision)
			return null;
		SurgicalSubject anchorSubject = table.getSubjectByPersistentId(reference.referenceAnchor.subjectKey());
		TableGeometry anchorGeometry = anchorSubject == null ? null
			: TABLES.get(new SubjectKey(reference.referenceHit.tablePos, anchorSubject.id()));
		SurgicalModelRenderContext.CubeGeometry anchorCube = anchorGeometry == null ? null
			: anchorGeometry.cubesById.get(reference.referenceAnchor.cubeId());
		if (anchorSubject == null || anchorGeometry == null || anchorCube == null)
			return null;

		Vec3 planeNormal = plane.normal;
		Vec3 planeCenter = plane.center;
		Vec3 anchorCenter = cubeCenter(anchorCube);
		SurgicalGlueJoint.Endpoint mirroredAnchor = reference.referenceAnchor;
		SurgicalSubject mirroredAnchorSubject = anchorSubject;
		TableGeometry mirroredAnchorGeometry = anchorGeometry;
		SurgicalModelRenderContext.CubeGeometry mirroredAnchorCube = anchorCube;
		if (!singleCube) {
			if (reference.combination == null)
				return null;
			Vec3 expectedCenter = reflectPoint(anchorCenter, planeCenter, planeNormal);
			SurgicalCombination.Member nearest = nearestCombinationMember(reference.referenceHit.tablePos,
				table, reference.combination, expectedCenter);
			if (nearest == null)
				return null;
			mirroredAnchor = new SurgicalGlueJoint.Endpoint(nearest.subjectKey(), nearest.cubeId());
			mirroredAnchorSubject = table.getSubjectByPersistentId(nearest.subjectKey());
			mirroredAnchorGeometry = mirroredAnchorSubject == null ? null
				: TABLES.get(new SubjectKey(reference.referenceHit.tablePos, mirroredAnchorSubject.id()));
			mirroredAnchorCube = mirroredAnchorGeometry == null ? null
				: mirroredAnchorGeometry.cubesById.get(nearest.cubeId());
			if (mirroredAnchorSubject == null || mirroredAnchorGeometry == null || mirroredAnchorCube == null)
				return null;
		}

		Vec3 mirroredLocalContact = reflectVector(
			reference.originalAnchorContact.subtract(anchorCenter), planeNormal);
		Vec3 mirroredContact = cubeCenter(mirroredAnchorCube).add(mirroredLocalContact);
		SurgicalGlueContact mirroredContactData = glueContact(mirroredAnchorCube, -1, mirroredContact);
		if (mirroredContactData == null)
			return null;
		mirroredContact = glueContactPoint(mirroredAnchorCube, mirroredContactData);
		if (mirroredContact == null)
			return null;
		CubeHit mirroredHit = new CubeHit(reference.referenceHit.tablePos, mirroredAnchorGeometry,
			mirroredAnchor.cubeId(), mirroredContactData.faceIndex(), mirroredContact, null);
		SurgicalGlueTransform mirroredReplay = reference.recordedReplay == null
			? SurgicalGlueTransform.IDENTITY : reference.recordedReplay.mirrorAcross(planeNormal);
		return new SymmetryTarget(reference.selection(singleCube), reference.referenceHit, mirroredHit,
			reference.referenceAnchor, singleCube, mirroredReplay);
	}

	@Nullable
	private static AABB combinationBounds(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalCombination combination) {
		AABB bounds = null;
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			TableGeometry geometry = subject == null ? null : TABLES.get(new SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube == null)
				return null;
			AABB cubeBounds = cubeBounds(cube);
			bounds = bounds == null ? cubeBounds : bounds.minmax(cubeBounds);
		}
		return bounds;
	}

	private static List<SymmetryPlaneCandidate> symmetryPlanes(AABB bounds) {
		return List.of(symmetryPlane(bounds, Direction.Axis.X), symmetryPlane(bounds, Direction.Axis.Y),
			symmetryPlane(bounds, Direction.Axis.Z));
	}

	private static SymmetryPlaneCandidate symmetryPlane(AABB bounds, Direction.Axis axis) {
		AABB expanded = bounds.inflate(SYMMETRY_PLANE_MARGIN);
		Vec3 center = bounds.getCenter();
		Vec3 first;
		Vec3 second;
		Vec3 third;
		Vec3 fourth;
		Vec3 normal;
		switch (axis) {
			case X -> {
				normal = new Vec3(1.0d, 0.0d, 0.0d);
				first = new Vec3(center.x, expanded.minY, expanded.minZ);
				second = new Vec3(center.x, expanded.maxY, expanded.minZ);
				third = new Vec3(center.x, expanded.maxY, expanded.maxZ);
				fourth = new Vec3(center.x, expanded.minY, expanded.maxZ);
			}
			case Y -> {
				normal = new Vec3(0.0d, 1.0d, 0.0d);
				first = new Vec3(expanded.minX, center.y, expanded.minZ);
				second = new Vec3(expanded.maxX, center.y, expanded.minZ);
				third = new Vec3(expanded.maxX, center.y, expanded.maxZ);
				fourth = new Vec3(expanded.minX, center.y, expanded.maxZ);
			}
			case Z -> {
				normal = new Vec3(0.0d, 0.0d, 1.0d);
				first = new Vec3(expanded.minX, expanded.minY, center.z);
				second = new Vec3(expanded.maxX, expanded.minY, center.z);
				third = new Vec3(expanded.maxX, expanded.maxY, center.z);
				fourth = new Vec3(expanded.minX, expanded.maxY, center.z);
			}
			default -> throw new IllegalStateException("Unexpected symmetry axis " + axis);
		}
		return new SymmetryPlaneCandidate(axis, center, normal, List.of(
			new SurgicalClientTopology.Edge(first, second),
			new SurgicalClientTopology.Edge(second, third),
			new SurgicalClientTopology.Edge(third, fourth),
			new SurgicalClientTopology.Edge(fourth, first)));
	}

	@Nullable
	private static SymmetryPlaneCandidate findSymmetryPlane(Ray ray, AABB mirrorBounds,
		List<SymmetryPlaneCandidate> planes) {
		AABB bounds = mirrorBounds.inflate(SYMMETRY_PLANE_MARGIN * 2.0d);
		Vec3 aimPoint = bounds.contains(ray.start) ? ray.start : bounds.clip(ray.start, ray.end).orElse(null);
		Vec3 view = ray.end.subtract(ray.start);
		if (aimPoint == null || view.lengthSqr() <= 1.0e-18d)
			return null;
		view = view.normalize();
		SymmetryPlaneCandidate best = null;
		double bestAlignment = Double.MAX_VALUE;
		double bestAimOffset = Double.MAX_VALUE;
		for (SymmetryPlaneCandidate plane : planes) {
			// A plane is parallel to the player's view when its normal is perpendicular to it.
			double alignment = Math.abs(view.dot(plane.normal));
			double aimOffset = Math.abs(aimPoint.subtract(plane.center).dot(plane.normal));
			if (alignment > bestAlignment + 1.0e-9d
				|| Math.abs(alignment - bestAlignment) <= 1.0e-9d && aimOffset >= bestAimOffset)
				continue;
			bestAlignment = alignment;
			bestAimOffset = aimOffset;
			best = plane;
		}
		return best;
	}

	@Nullable
	private static SurgicalCombination.Member nearestCombinationMember(BlockPos tablePos,
		SurgicalTableBlockEntity table, SurgicalCombination combination, Vec3 expectedCenter) {
		SurgicalCombination.Member nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			TableGeometry geometry = subject == null ? null : TABLES.get(new SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube == null)
				continue;
			double distance = cubeCenter(cube).distanceToSqr(expectedCenter);
			if (distance >= nearestDistance)
				continue;
			nearestDistance = distance;
			nearest = member;
		}
		return nearest;
	}

	@Nullable
	private static Vec3 contactCenter(SurgicalClientTopology.Contact contact) {
		Vec3 sum = Vec3.ZERO;
		int count = 0;
		for (List<Vec3> face : contact.faces())
			for (Vec3 point : face) {
				sum = sum.add(point);
				count++;
			}
		return count == 0 ? null : sum.scale(1.0d / count);
	}

	@Nullable
	private static Vec3 horizontalUnit(Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0d, vector.z);
		return horizontal.lengthSqr() <= 1.0e-12d ? null : horizontal.normalize();
	}

	private static Vec3 reflectPoint(Vec3 point, Vec3 planeCenter, Vec3 planeNormal) {
		return planeCenter.add(reflectVector(point.subtract(planeCenter), planeNormal));
	}

	private static Vec3 reflectVector(Vec3 vector, Vec3 planeNormal) {
		return vector.subtract(planeNormal.scale(2.0d * vector.dot(planeNormal)));
	}

	@Nullable
	private static GluePreview planGluePreview(ClientLevel level, PendingGlue first, CubeHit targetHit) {
		if (!first.selection.tablePos.equals(targetHit.tablePos)
			|| !(level.getBlockEntity(targetHit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject targetSubject = table.getSubject(targetHit.geometry.subjectId);
		if (targetSubject == null)
			return null;
		Map<Integer, BitSet> moving = table.connectedComponents(first.selection.subjectId,
			first.selection.targetId, first.selection.observedCubeCount, first.selection.seams);
		Map<Integer, BitSet> anchored = table.connectedComponents(targetHit.geometry.subjectId,
			targetHit.cubeId, targetHit.geometry.observedCubeCount, targetHit.geometry.seams);
		if (moving.isEmpty() || anchored.isEmpty() || componentMapsIntersect(moving, anchored))
			return null;

		SurgicalTablePlane.Plane plane = clientPlane(level, targetHit.tablePos);
		if (!plane.valid() || !targetHit.tablePos.equals(plane.source()))
			return null;
		List<SurgicalTableLayout.Footprint> obstacles = gluePreviewObstacles(table, moving, anchored);
		Vec3 firstPoint = Vec3.atLowerCornerOf(targetHit.tablePos).add(first.hit);
		Vec3 secondPoint = targetHit.location;
		SurgicalLayPose targetPose = targetSubject.layPose();
		List<GluePlanningSubject> planning = new ArrayList<>();
		double combinedLowestY = Double.POSITIVE_INFINITY;

		for (Map.Entry<Integer, BitSet> entry : moving.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(targetHit.tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			BitSet cubes = (BitSet) entry.getValue().clone();
			Vec3 subjectOrigin = Vec3.atLowerCornerOf(targetHit.tablePos)
				.add(subject.originOffsetX(), 0.0d, subject.originOffsetZ());
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseTarget = new HashMap<>();
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> desired = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> targetRotations = new HashMap<>();
			for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry base = geometry.baseCubesById.get(cube);
				SurgicalModelRenderContext.CubeGeometry current = geometry.cubesById.get(cube);
				if (base == null || current == null)
					return null;
				SurgicalModelRenderContext.CubeGeometry reframedBase = reframeBaseCube(base, subjectOrigin,
					subject.layPose(), targetPose);
				SurgicalModelRenderContext.CubeGeometry reframedCurrent = reframeCurrentCube(current,
					firstPoint, secondPoint, subject.layPose(), targetPose);
				baseTarget.put(cube, reframedBase);
				desired.put(cube, reframedCurrent);
				targetRotations.put(cube, geometry.serverRotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY)
					.reframe(subject.layPose(), targetPose));
				combinedLowestY = Math.min(combinedLowestY, lowestY(reframedCurrent));
			}
			planning.add(new GluePlanningSubject(subject, cubes, List.copyOf(baseTarget.values()),
				Map.copyOf(desired), Map.copyOf(targetRotations)));
		}
		for (Map.Entry<Integer, BitSet> entry : anchored.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(targetHit.tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry current = geometry.cubesById.get(cube);
				if (current == null)
					return null;
				combinedLowestY = Math.min(combinedLowestY, lowestY(current));
			}
		}
		double surfaceY = plane.workArea().y() + 1.0d + SurgicalTablePoseResolver.TABLE_CLEARANCE;
		double groundLiftY = Math.max(0.0d, surfaceY - combinedLowestY);
		if (!Double.isFinite(groundLiftY) || groundLiftY > SurgicalTablePlane.MAX_TILES + 2.0d)
			return null;
		List<GlueSubjectPreview> previews = new ArrayList<>(planning.size());
		List<SurgicalTableGluePacket.Move> moves = new ArrayList<>(planning.size());
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves = new ArrayList<>(anchored.size());
		for (Map.Entry<Integer, BitSet> entry : anchored.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(targetHit.tablePos, entry.getKey()));
			if (subject == null || geometry == null)
				return null;
			List<SurgicalTableGluePacket.CubeTranslation> translations =
				new ArrayList<>(entry.getValue().cardinality());
			Map<Integer, Vec3> previewOffsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> previewRotations = new HashMap<>();
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				Vec3 offset = geometry.offsets.getOrDefault(cube, Vec3.ZERO).add(0.0d, groundLiftY, 0.0d);
				SurgicalCubeRotation rotation = geometry.serverRotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY);
				SurgicalTableGluePacket.CubeTranslation translation = new SurgicalTableGluePacket.CubeTranslation(
					cube, offset, rotation);
				if (!translation.valid())
					return null;
				translations.add(translation);
				previewOffsets.put(cube, offset);
				previewRotations.put(cube, rotation);
			}
			anchorMoves.add(new SurgicalTableGluePacket.AnchorMove(entry.getKey(), translations));
			previews.add(new GlueSubjectPreview(entry.getKey(), entry.getValue(), subject.layPose(),
				geometry.baseCubes, previewOffsets, previewRotations, false));
		}
		for (GluePlanningSubject source : planning) {
			Map<Integer, Vec3> confirmedOffsets = new HashMap<>();
			for (int cube = source.cubes.nextSetBit(0); cube >= 0; cube = source.cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry base = cubeById(source.baseTarget, cube);
				SurgicalModelRenderContext.CubeGeometry desired = source.desired.get(cube);
				if (base == null || desired == null)
					return null;
				Vec3 previewOffset = cubeCenter(desired).subtract(cubeCenter(base))
					.add(0.0d, groundLiftY, 0.0d);
				confirmedOffsets.put(cube, previewOffset);
			}
			List<SurgicalModelRenderContext.CubeGeometry> rotatedBase = transformCubes(
				source.baseTarget, source.targetRotations, Map.of());
			SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
				source.subject.cubeCount(), source.cubes, source.subject.seams(),
				source.subject.cutSeamsForRender(), rotatedBase, confirmedOffsets,
				plane.workArea(), obstacles);
			if (planned == null)
				return null;
			List<SurgicalTableGluePacket.CubeTranslation> translations = new ArrayList<>(source.cubes.cardinality());
			for (int cube = source.cubes.nextSetBit(0); cube >= 0; cube = source.cubes.nextSetBit(cube + 1))
				translations.add(new SurgicalTableGluePacket.CubeTranslation(cube,
					planned.offsets().getOrDefault(cube, Vec3.ZERO), source.targetRotations.getOrDefault(cube,
						SurgicalCubeRotation.IDENTITY)));
			moves.add(new SurgicalTableGluePacket.Move(source.subject.id(), translations, planned.proposal()));
			previews.add(new GlueSubjectPreview(source.subject.id(), source.cubes, targetPose,
				source.baseTarget, planned.offsets(), source.targetRotations, true));
		}
		return new GluePreview(first, targetHit.tablePos, targetHit.geometry.subjectId, targetHit.cubeId,
			targetHit.location, table.clientDataRevision(), targetPose, plane.workArea(), obstacles,
			previews, moves, anchorMoves);
	}

	/** Applies a previously recorded smart-glue edit once to a freshly aligned symmetry preview. */
	@Nullable
	private static GluePreview replayGlueTransform(ClientLevel level, GluePreview current,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		SurgicalGlueTransform transform) {
		if (transform == null || transform.isIdentity())
			return current;
		if (!(level.getBlockEntity(current.ownerPos) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != current.tableRevision)
			return null;
		SurgicalModelRenderContext.CubeGeometry pivotCube = previewCube(current.subjects,
			firstSubjectId, firstCubeId);
		if (pivotCube == null)
			return null;
		Vec3 pivot = cubeCenter(pivotCube);
		List<GlueSubjectPreview> previews = new ArrayList<>(current.subjects.size());
		List<SurgicalTableGluePacket.Move> moves = new ArrayList<>(current.moves.size());
		for (GlueSubjectPreview preview : current.subjects) {
			if (!preview.editable) {
				previews.add(preview);
				continue;
			}
			SurgicalSubject subject = table.getSubject(preview.subjectId);
			if (subject == null)
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> bases = indexCubes(preview.baseCubes);
			for (int cube = preview.cubes.nextSetBit(0); cube >= 0;
				cube = preview.cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry base = bases.get(cube);
				if (base == null)
					return null;
				Vec3 baseCenter = cubeCenter(base);
				Vec3 currentCenter = baseCenter.add(preview.offsets.getOrDefault(cube, Vec3.ZERO));
				Vec3 transformedCenter = pivot.add(transform.rotation().rotate(currentCenter.subtract(pivot)))
					.add(transform.translation());
				offsets.put(cube, transformedCenter.subtract(baseCenter));
				rotations.put(cube, preview.rotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY).then(transform.rotation()));
			}
			List<SurgicalModelRenderContext.CubeGeometry> rotatedBase = transformCubes(
				preview.baseCubes, rotations, Map.of());
			if (!validEditedVertical(preview.cubes, rotatedBase, offsets, current.workArea.y() + 1.0d
				+ SurgicalTablePoseResolver.TABLE_CLEARANCE))
				return null;
			SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
				subject.cubeCount(), preview.cubes, subject.seams(), subject.cutSeamsForRender(),
				rotatedBase, offsets, current.workArea, current.obstacles);
			if (planned == null)
				return null;
			List<SurgicalTableGluePacket.CubeTranslation> translations =
				new ArrayList<>(preview.cubes.cardinality());
			for (int cube = preview.cubes.nextSetBit(0); cube >= 0;
				cube = preview.cubes.nextSetBit(cube + 1)) {
				SurgicalTableGluePacket.CubeTranslation translation =
					new SurgicalTableGluePacket.CubeTranslation(cube,
						planned.offsets().getOrDefault(cube, Vec3.ZERO), rotations.get(cube));
				if (!translation.valid())
					return null;
				translations.add(translation);
			}
			moves.add(new SurgicalTableGluePacket.Move(preview.subjectId, translations, planned.proposal()));
			previews.add(new GlueSubjectPreview(preview.subjectId, preview.cubes, preview.pose,
				preview.baseCubes, planned.offsets(), rotations, true));
		}
		if (moves.size() != current.moves.size()
			|| !glueCubesActuallyIntersect(previews, firstSubjectId, firstCubeId,
				secondSubjectId, secondCubeId))
			return null;
		return new GluePreview(current.request, current.ownerPos, current.targetSubjectId,
			current.targetCubeId, current.targetHit, current.tableRevision, current.targetPose,
			current.workArea, current.obstacles, previews, moves, current.anchorMoves);
	}

	@Nullable
	private static GluePreview adjustGluePreview(ClientLevel level, GlueEditor editor, Vec3 translation,
		SurgicalCubeRotation deltaRotation) {
		GluePreview current = editor.preview;
		if (!(level.getBlockEntity(current.ownerPos) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != current.tableRevision
			|| !glueEndpointsActuallyIntersect(current.subjects, editor.first, editor.second))
			return null;
		List<GlueSubjectPreview> previews = new ArrayList<>(current.subjects.size());
		List<SurgicalTableGluePacket.Move> moves = new ArrayList<>(current.moves.size());
		for (GlueSubjectPreview preview : current.subjects) {
			if (!preview.editable) {
				previews.add(preview);
				continue;
			}
			SurgicalSubject subject = table.getSubject(preview.subjectId);
			if (subject == null)
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> bases = indexCubes(preview.baseCubes);
			for (int cube = preview.cubes.nextSetBit(0); cube >= 0; cube = preview.cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry base = bases.get(cube);
				if (base == null)
					return null;
				Vec3 oldOffset = preview.offsets.getOrDefault(cube, Vec3.ZERO);
				SurgicalCubeRotation oldRotation = preview.rotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY);
				Vec3 newOffset = oldOffset.add(translation);
				SurgicalCubeRotation newRotation = oldRotation;
				if (!deltaRotation.isIdentity()) {
					Vec3 baseCenter = cubeCenter(base);
					Vec3 currentCenter = baseCenter.add(oldOffset);
					Vec3 rotatedCenter = editor.axisCenter.add(
						deltaRotation.rotate(currentCenter.subtract(editor.axisCenter)));
					newOffset = rotatedCenter.subtract(baseCenter);
					newRotation = oldRotation.then(deltaRotation);
				}
				offsets.put(cube, newOffset);
				rotations.put(cube, newRotation);
			}

			List<SurgicalModelRenderContext.CubeGeometry> rotatedBase = transformCubes(
				preview.baseCubes, rotations, Map.of());
			if (!validEditedVertical(preview.cubes, rotatedBase, offsets, current.workArea.y() + 1.0d
				+ SurgicalTablePoseResolver.TABLE_CLEARANCE))
				return null;
			SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
				subject.cubeCount(), preview.cubes, subject.seams(), subject.cutSeamsForRender(),
				rotatedBase, offsets, current.workArea, current.obstacles);
			if (planned == null)
				return null;
			List<SurgicalTableGluePacket.CubeTranslation> transforms =
				new ArrayList<>(preview.cubes.cardinality());
			for (int cube = preview.cubes.nextSetBit(0); cube >= 0; cube = preview.cubes.nextSetBit(cube + 1)) {
				SurgicalTableGluePacket.CubeTranslation transform = new SurgicalTableGluePacket.CubeTranslation(
					cube, planned.offsets().getOrDefault(cube, Vec3.ZERO), rotations.get(cube));
				if (!transform.valid())
					return null;
				transforms.add(transform);
			}
			moves.add(new SurgicalTableGluePacket.Move(preview.subjectId, transforms, planned.proposal()));
			previews.add(new GlueSubjectPreview(preview.subjectId, preview.cubes, preview.pose,
				preview.baseCubes, planned.offsets(), rotations, true));
		}
		if (moves.size() != current.moves.size())
			return null;
		if (!glueEndpointsActuallyIntersect(previews, editor.first, editor.second))
			return null;
		GluePreview adjusted = new GluePreview(current.request, current.ownerPos,
			current.targetSubjectId, current.targetCubeId,
			current.targetHit, current.tableRevision, current.targetPose,
			current.workArea, current.obstacles, previews, moves, current.anchorMoves);
		SurgicalSubject firstSubject = table.getSubject(editor.first.subjectId());
		SurgicalSubject secondSubject = table.getSubject(editor.second.subjectId());
		SurgicalTablePlane.Plane plane = clientPlane(level, current.ownerPos);
		LocalPlayer player = Minecraft.getInstance().player;
		if (firstSubject == null || secondSubject == null || player == null
			|| glueTopologyKnown(firstSubject, secondSubject)
				&& !table.canGlueComponents(player.getItemInHand(editor.hand), editor.first.subjectId(),
					editor.first.cubeId(), editor.second.subjectId(), editor.second.cubeId(),
					adjusted.targetPose, adjusted.moves, adjusted.anchorMoves,
					editor.replayTransform.then(translation, deltaRotation), plane,
					editor.first.layout(), editor.second.layout()))
			return null;
		return adjusted;
	}

	private static boolean retainsPlacementPreview() {
		return pendingVisualCommit != null && pendingVisualCommit.retainPlacementPreview;
	}

	private static boolean retainsGluePreview() {
		return pendingVisualCommit != null && pendingVisualCommit.retainGluePreview;
	}

	private static void beginVisualCommit(ClientLevel level, BlockPos tablePos, int tableRevision,
		List<Integer> geometrySubjectIds, boolean retainPlacementPreview, boolean retainGluePreview) {
		cancelPendingVisualCommit(level, true);
		int currentRevision = level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table
			? table.clientDataRevision() : tableRevision;
		pendingVisualCommit = new PendingVisualCommit(tablePos, currentRevision,
			level.getGameTime() + VISUAL_COMMIT_TIMEOUT_TICKS, geometrySubjectIds,
			retainPlacementPreview, retainGluePreview);
	}

	private static void updatePendingVisualCommit(ClientLevel level) {
		PendingVisualCommit commit = pendingVisualCommit;
		if (commit == null)
			return;
		SurgicalTableBlockEntity table = level.getBlockEntity(commit.tablePos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		if (table != null && table.clientDataRevision() != commit.tableRevision) {
			if (commit.retainPlacementPreview)
				commit.serverAcknowledged = true;
			else {
				cancelPendingVisualCommit(level, false);
				return;
			}
		}
		if (table == null || level.getGameTime() >= commit.expiresAtTick)
			cancelPendingVisualCommit(level, !commit.serverAcknowledged);
	}

	/** Switches from the placement preview only after every newly added subject is render-ready. */
	static void completePlacementHandoffIfReady(SurgicalTableBlockEntity table) {
		PendingVisualCommit commit = pendingVisualCommit;
		if (commit == null || !commit.retainPlacementPreview || !commit.serverAcknowledged
			|| !commit.tablePos.equals(table.getBlockPos()))
			return;
		boolean foundAddedSubject = false;
		for (SurgicalSubject subject : table.getSubjects()) {
			if (commit.placementBaselineSubjectIds.contains(subject.id()))
				continue;
			foundAddedSubject = true;
			TableGeometry geometry = TABLES.get(new SubjectKey(commit.tablePos, subject.id()));
			if (!geometryReadyForUse(table, subject, geometry))
				return;
		}
		if (foundAddedSubject && table.getLevel() instanceof ClientLevel level)
			cancelPendingVisualCommit(level, false);
	}

	static boolean suppressForPlacementHandoff(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		PendingVisualCommit commit = pendingVisualCommit;
		return commit != null && commit.retainPlacementPreview && commit.serverAcknowledged
			&& commit.tablePos.equals(table.getBlockPos())
			&& !commit.placementBaselineSubjectIds.contains(subject.id());
	}

	private static void cancelPendingVisualCommit(@Nullable ClientLevel level, boolean rollbackGeometry) {
		PendingVisualCommit commit = pendingVisualCommit;
		pendingVisualCommit = null;
		if (commit == null)
			return;
		// Both rollback and acknowledgement must retire the preview transforms. On acknowledgement the
		// table already contains the authoritative server state; clearing here keeps a subject disconnected
		// by the edit from retaining an old pending grounding request outside the post-edit refresh group.
		if (level != null
			&& level.getBlockEntity(commit.tablePos) instanceof SurgicalTableBlockEntity table) {
			for (int subjectId : commit.geometrySubjectIds) {
				TableGeometry geometry = TABLES.get(new SubjectKey(commit.tablePos, subjectId));
				if (geometry != null && table.hasSubject(subjectId))
					geometry.clearPreview(table);
			}
		}
		PlacementPreview committedPlacement = commit.retainPlacementPreview ? placementPreview : null;
		if (!rollbackGeometry && committedPlacement != null && level != null)
			placementSuppression = new PlacementSuppression(committedPlacement.hand,
				committedPlacement.source.box.copy(), level.getGameTime() + VISUAL_COMMIT_TIMEOUT_TICKS);
		if (commit.retainPlacementPreview)
			clearPlacementPreview();
		if (commit.retainGluePreview)
			gluePreview = null;
		geometryGeneration++;
	}

	private static boolean glueTopologyKnown(SurgicalSubject first, SurgicalSubject second) {
		return first.cubeCount() != 0 && second.cubeCount() != 0;
	}

	private static boolean validEditedVertical(BitSet cubes,
		List<SurgicalModelRenderContext.CubeGeometry> rotatedBase, Map<Integer, Vec3> offsets, double surfaceY) {
		double range = SurgicalTablePlane.MAX_TILES + 2.0d;
		double minimumY = surfaceY - range;
		double maximumY = surfaceY + range;
		for (SurgicalModelRenderContext.CubeGeometry cube : rotatedBase) {
			if (!cubes.get(cube.cubeId()))
				continue;
			Vec3 offset = offsets.getOrDefault(cube.cubeId(), Vec3.ZERO);
			for (Vec3 corner : cube.corners()) {
				double y = corner.y + offset.y;
				if (!Double.isFinite(y) || y < minimumY || y > maximumY)
					return false;
			}
		}
		return true;
	}

	private static double lowestY(SurgicalModelRenderContext.CubeGeometry geometry) {
		double lowest = Double.POSITIVE_INFINITY;
		for (Vec3 corner : geometry.corners())
			lowest = Math.min(lowest, corner.y);
		return lowest;
	}

	private static boolean componentMapsIntersect(Map<Integer, BitSet> first, Map<Integer, BitSet> second) {
		for (Map.Entry<Integer, BitSet> entry : first.entrySet()) {
			BitSet other = second.get(entry.getKey());
			if (other != null && entry.getValue().intersects(other))
				return true;
		}
		return false;
	}

	/**
	 * Grounding needs every connected body in its rotated-but-unoffset form. That is exactly what each
	 * geometry already caches as {@code layoutCubes}, including the caller's own — {@code applyTransforms}
	 * rebuilds it from the same rotations immediately before calling here — so the cubes are reused
	 * rather than re-transformed once per body per subject.
	 */
	@Nullable
	private static Map<Integer, Vec3> groundConnectedComponents(SurgicalTableBlockEntity table,
		SurgicalSubject currentSubject, TableGeometry currentGeometry, Map<Integer, Vec3> currentOffsets,
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
			TableGeometry geometry = currentSubjectEntry ? currentGeometry
				: TABLES.get(new SubjectKey(table.getBlockPos(), subject.id()));
			if (geometry == null || !geometry.topologyReady()
				|| geometry.renderRevision != subject.clientRenderRevision()
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			Map<Integer, Vec3> offsets = currentSubjectEntry ? currentOffsets : geometry.serverOffsets;
			BitSet cutSeams = currentSubjectEntry ? currentCutSeams : geometry.cutSeams;
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
		Map<UUID, Map<Integer, Vec3>> grounded = SurgicalClientTopology.groundConnectedBodies(
			bodies, links, surfaceY);
		return grounded == null ? null : grounded.get(currentSubject.persistentId());
	}

	private static List<SurgicalTableLayout.Footprint> gluePreviewObstacles(SurgicalTableBlockEntity table,
		Map<Integer, BitSet> moving, Map<Integer, BitSet> anchored) {
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			BitSet moved = moving.get(subject.id());
			BitSet fixed = anchored.get(subject.id());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint)
					&& !subject.containsFootprint(fixed, footprint))
					obstacles.add(footprint);
		}
		return List.copyOf(obstacles);
	}

	private static SurgicalModelRenderContext.CubeGeometry reframeBaseCube(
		SurgicalModelRenderContext.CubeGeometry cube, Vec3 subjectOrigin, SurgicalLayPose source,
		SurgicalLayPose target) {
		Vec3 sourceAnchor = subjectOrigin.add(source.translation());
		Vec3 targetAnchor = subjectOrigin.add(target.translation());
		List<Vec3> corners = cube.corners().stream()
			.map(corner -> targetAnchor.add(source.rotateInto(target, corner.subtract(sourceAnchor))))
			.toList();
		return cube.withCorners(corners);
	}

	private static SurgicalModelRenderContext.CubeGeometry reframeCurrentCube(
		SurgicalModelRenderContext.CubeGeometry cube, Vec3 firstPoint, Vec3 secondPoint,
		SurgicalLayPose source, SurgicalLayPose target) {
		List<Vec3> corners = cube.corners().stream()
			.map(corner -> secondPoint.add(source.rotateInto(target, corner.subtract(firstPoint))))
			.toList();
		return cube.withCorners(corners);
	}

	@Nullable
	private static SurgicalModelRenderContext.CubeGeometry cubeById(
		List<SurgicalModelRenderContext.CubeGeometry> cubes, int cubeId) {
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (cube.cubeId() == cubeId)
				return cube;
		return null;
	}

	private static Vec3 cubeCenter(SurgicalModelRenderContext.CubeGeometry cube) {
		Vec3 sum = Vec3.ZERO;
		for (Vec3 corner : cube.corners())
			sum = sum.add(corner);
		return sum.scale(1.0d / cube.corners().size());
	}

	private static Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexCubes(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexed = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			indexed.put(cube.cubeId(), cube);
		return Map.copyOf(indexed);
	}

	private static List<SurgicalModelRenderContext.CubeGeometry> transformCubes(
		List<SurgicalModelRenderContext.CubeGeometry> cubes,
		Map<Integer, SurgicalCubeRotation> rotations, Map<Integer, Vec3> offsets) {
		if (rotations.isEmpty() && offsets.isEmpty())
			return cubes;
		List<SurgicalModelRenderContext.CubeGeometry> transformed = new ArrayList<>(cubes.size());
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes) {
			SurgicalCubeRotation rotation = rotations.getOrDefault(cube.cubeId(), SurgicalCubeRotation.IDENTITY);
			Vec3 offset = offsets.getOrDefault(cube.cubeId(), Vec3.ZERO);
			if (rotation.isIdentity() && offset.lengthSqr() <= 1.0e-24d) {
				transformed.add(cube);
				continue;
			}
			Vec3 center = cubeCenter(cube);
			List<Vec3> corners = new ArrayList<>(8);
			for (Vec3 corner : cube.corners())
				corners.add(center.add(rotation.rotate(corner.subtract(center))).add(offset));
			transformed.add(cube.withCorners(corners));
		}
		return List.copyOf(transformed);
	}

	/** Prevents Create's block-area glue selector from consuming clicks aimed at surgical parts. */
	public static boolean shouldOverrideCreateGlue(ItemStack stack) {
		if (!isSurgicalGlue(stack))
			return false;
		if (pendingGlue != null)
			return true;
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.level != null
			&& findNearestCubeHit(minecraft.player, minecraft.level, playerRay(minecraft.player)) != null;
	}

	/** Builds the fixed HUD prompt while aiming at a model cube or placing a compatible captured subject. */
	@Nullable
	public static InteractionPrompt interactionPrompt() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null || pendingVisualCommit != null)
			return null;
		InteractionHand hand = interactionPromptHand(player);
		if (hand == null)
			return null;
		ItemStack stack = player.getItemInHand(hand);
		Ray ray = playerRay(player);
		boolean hitsCube = findNearestCubeHit(player, level, ray) != null;
		if (!hitsCube && glueEditor != null && glueEditor.hand == hand)
			hitsCube = findNearestGluePreviewHit(player, level, glueEditor) != null;
		if (!hitsCube) {
			hand = capturedSubjectPlacementPromptHand(player, level);
			if (hand == null)
				return null;
			stack = player.getItemInHand(hand);
		}

		List<Component> tooltip = new ArrayList<>();
		CreateLang.builder()
			.add(stack.getHoverName())
			.forGoggles(tooltip);
		if (isSurgicalGlue(stack)) {
			boolean selectingFirst = pendingGlue == null && glueEditor == null;
			boolean selectingSecond = pendingGlue != null && glueEditor == null;
			boolean editing = glueEditor != null;
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_first_glue", selectingFirst);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_second_glue", selectingSecond);
			if (isSmartGlue(stack)) {
				addInteractionControl(tooltip, Component.translatable(
					"create_biotech.gui.surgical_table.control.scroll"),
					"create_biotech.gui.surgical_table.action.move_glue", editing);
				addInteractionControl(tooltip, combinedControl(Component.translatable(
					"create_biotech.gui.surgical_table.control.ctrl"),
					Component.translatable("create_biotech.gui.surgical_table.control.scroll")),
					"create_biotech.gui.surgical_table.action.rotate_glue", editing);
				addInteractionControl(tooltip, Component.keybind("key.use"),
					"create_biotech.gui.surgical_table.action.confirm_glue", editing);
			}
			addCancelControl(tooltip, pendingGlue != null);
		} else if (isSymmetryWand(stack)) {
			boolean selectingFirst = pendingSymmetry == null;
			boolean selectingReference = pendingSymmetry != null && pendingSymmetryReference == null;
			boolean selectingPlane = pendingSymmetryReference != null;
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_first_symmetry", selectingFirst);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_combination_symmetry", selectingReference);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_plane_symmetry", selectingPlane);
			addInteractionControl(tooltip, combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.select_cube_symmetry", selectingPlane);
			addCancelControl(tooltip, pendingSymmetry != null);
		} else if (heldLimbType(stack) != null) {
			boolean continuingLimb = pendingLimb != null && pendingLimb.hand() == hand
				&& pendingLimb.type() == heldLimbType(stack);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_moving_limb", !continuingLimb);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_pivot_limb", continuingLimb);
			addCancelControl(tooltip, pendingLimb != null);
		} else if (stack.is(Items.SHEARS)) {
			boolean placingCut = pendingCut != null || pendingGlueCut != null;
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.cut", !placingCut);
			addInteractionControl(tooltip, combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"),
				Component.keybind("key.use")), "create_biotech.gui.surgical_table.action.batch_cut", !placingCut);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.confirm_cut", placingCut);
			addCancelControl(tooltip, placingCut);
		} else if (stack.is(Items.HONEY_BOTTLE)) {
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.combine");
		} else if (isEmptyBox(stack)) {
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.pack");
		} else if (CBWrenchHelper.isWrench(stack)) {
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.remove_joint");
		} else if (CapturedEntityBoxHelper.hasCapturedEntity(stack)) {
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.place_subject");
		} else {
			return null;
		}
		return new InteractionPrompt(stack, tooltip);
	}

	/** A filled box is a valid prompt target on the table surface before any model cube exists. */
	@Nullable
	private static InteractionHand capturedSubjectPlacementPromptHand(LocalPlayer player, ClientLevel level) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock))
			return null;
		for (InteractionHand hand : HANDS) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof CapturedEntityBoxItem
				&& CapturedEntityBoxHelper.hasCapturedEntity(stack)
				&& placementCandidateFor(stack, level).result().succeeded())
				return hand;
		}
		return null;
	}

	@Nullable
	private static InteractionHand interactionPromptHand(LocalPlayer player) {
		if (pendingCut != null)
			return pendingCut.hand;
		if (pendingGlueCut != null)
			return pendingGlueCut.hand;
		if (glueEditor != null)
			return glueEditor.hand;
		if (pendingGlue != null)
			return pendingGlue.hand;
		if (pendingSymmetry != null)
			return pendingSymmetry.hand;
		if (pendingLimb != null)
			return pendingLimb.hand();
		for (InteractionHand hand : HANDS)
			if (isSurgicalInteractionItem(player.getItemInHand(hand)))
				return hand;
		return null;
	}

	private static boolean isSurgicalInteractionItem(ItemStack stack) {
		return CapturedEntityBoxItem.isBox(stack) || stack.is(Items.SHEARS)
			|| isSurgicalGlue(stack) || isSymmetryWand(stack) || stack.is(Items.HONEY_BOTTLE)
			|| heldLimbType(stack) != null || CBWrenchHelper.isWrench(stack);
	}

	private static void addCancelControl(List<Component> tooltip, boolean enabled) {
		addInteractionControl(tooltip, Component.keybind("key.attack"),
			"create_biotech.gui.surgical_table.action.cancel", enabled);
	}

	private static void addInteractionControl(List<Component> tooltip, Component control, String actionKey) {
		addInteractionControl(tooltip, control, actionKey, true);
	}

	private static void addInteractionControl(List<Component> tooltip, Component control, String actionKey,
		boolean enabled) {
		ChatFormatting controlColor = enabled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY;
		ChatFormatting actionColor = enabled ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
		CreateLang.builder()
			.add(control.copy().withStyle(controlColor))
			.text(ChatFormatting.DARK_GRAY, "  ")
			.add(Component.translatable(actionKey).withStyle(actionColor))
			.forGoggles(tooltip, 1);
	}

	private static MutableComponent combinedControl(Component first, Component second) {
		return first.copy()
			.append(Component.literal(" + "))
			.append(second.copy());
	}

	private static void consumeInteraction(InputEvent.InteractionKeyMappingTriggered event, InteractionHand hand) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null)
			minecraft.player.swing(hand);
		event.setSwingHand(false);
		event.setCanceled(true);
	}

	private static void sendInteraction(Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal) {
		sendInteraction(selected, hand, action, proposal, 0.0d, 0.0d, null, null, null);
	}

	private static void sendInteraction(Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {
		sendInteraction(selected, hand, action, proposal, 0.0d, 0.0d, bodyBounds,
			hitboxGeometry, attackGeometry);
	}

	private static void sendInteraction(Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		double originOffsetX, double originOffsetZ) {
		sendInteraction(selected, hand, action, proposal, originOffsetX, originOffsetZ, null, null, null);
	}

	private static void sendInteraction(Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		double originOffsetX, double originOffsetZ, @Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {
		CBPackets.sendToServer(new SurgicalTableInteractionPacket(selected.tablePos, hand, action,
			selected.subjectId, selected.targetId, selected.observedCubeCount, selected.seams,
			originOffsetX, originOffsetZ, proposal, bodyBounds, hitboxGeometry, attackGeometry));
	}

	private static boolean tryPlaceSubject(LocalPlayer player, ClientLevel level, InteractionHand hand,
		ItemStack held) {
		if (!(held.getItem() instanceof CapturedEntityBoxItem)
			|| !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock))
			return false;
		PlacementCandidate candidate = placementCandidateFor(held, level);
		if (!candidate.result().succeeded()) {
			candidate.result().display(player);
			return true;
		}
		updatePlacementPreview();
		PlacementPreview placement = placementPreview;
		if (placement == null || placement.hand != hand
			|| !ItemStack.isSameItemSameComponents(placement.source.box, held)) {
			SurgicalTablePlacementResult result = placementPreviewResult.succeeded()
				? SurgicalTablePlacementResult.NO_SPACE : placementPreviewResult;
			result.display(player);
			return true;
		}

		CBPackets.sendToServer(new SurgicalTablePlacementPacket(placement.ownerPos, hand, placement.facing,
			placement.plan.originOffsetX(), placement.plan.originOffsetZ(), placement.layPose,
			placement.plan.proposal(), placement.discovered.cubeCount(), placement.discovered.seams(),
			placement.discovered.footprints(),
			placement.sourceLayouts));
		beginVisualCommit(level, placement.ownerPos, placement.tableRevision, List.of(), true, false);
		return true;
	}

	private static void beginSingleCut(ClientLevel level, Selection selected, InteractionHand hand) {
		if (!(level.getBlockEntity(selected.tablePos) instanceof SurgicalTableBlockEntity table))
			return;
		SurgicalTableBlockEntity.SeamCutPlan plan = table.seamCutPlan(selected.subjectId, selected.targetId);
		if (plan == null)
			return;
		TableGeometry geometry = TABLES.get(new SubjectKey(selected.tablePos, selected.subjectId));
		SurgicalTablePlane.Plane plane = clientPlane(level, selected.tablePos);
		if (geometry == null || !plane.valid() || !selected.tablePos.equals(plane.source()))
			return;
		if (!plan.separates()) {
			SurgicalClientTopology.PlannedLayout layout = SurgicalClientTopology.translateComponents(
				geometry.observedCubeCount, geometry.presentCubes, geometry.seams, plan.proposedCuts(),
				geometry.layoutCubes, geometry.serverOffsets, plane.workArea(), new BitSet(), Vec3.ZERO, List.of());
			if (layout == null || !table.canApplySeamCut(selected.subjectId, selected.targetId,
				layout.proposal(), 0.0d, 0.0d, plane))
				showNoSpace(Minecraft.getInstance().player);
			else
				sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.CUT,
					layout.proposal());
			return;
		}
		pendingCut = new PendingCut(selected, hand, plan.proposedCuts(), plan.movingComponents(), null);
		updatePendingCut(Minecraft.getInstance().player, level);
	}

	private static void beginGlueCut(ClientLevel level, Selection selected, InteractionHand hand) {
		if (!(level.getBlockEntity(selected.tablePos) instanceof SurgicalTableBlockEntity table))
			return;
		SurgicalTableBlockEntity.GlueCutPlan plan = table.glueCutPlan(selected.subjectId, selected.targetId);
		if (plan == null)
			return;
		if (!plan.separates()) {
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.CUT_GLUE,
				SurgicalTableLayout.Proposal.EMPTY);
			return;
		}
		pendingGlueCut = new PendingGlueCut(selected, hand, plan.joint(), plan.movingComponents(), null);
		updatePendingGlueCut(Minecraft.getInstance().player, level);
	}

	private static void updatePendingGlueCut(@Nullable LocalPlayer player, ClientLevel level) {
		PendingGlueCut pending = pendingGlueCut;
		if (pending == null || player == null)
			return;
		if (!(level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			|| !player.getItemInHand(pending.hand).is(Items.SHEARS)) {
			abortPendingGlueCut();
			return;
		}
		SurgicalTableBlockEntity.GlueCutPlan current = table.glueCutPlan(pending.subjectId, pending.targetId);
		if (current == null || !current.separates() || !pending.joint.equals(current.joint())
			|| !pending.movingComponents.equals(current.movingComponents())) {
			abortPendingGlueCut();
			return;
		}
		SurgicalTablePlane.Plane plane = clientPlane(level, pending.tablePos);
		if (!plane.valid() || !pending.tablePos.equals(plane.source())) {
			abortPendingGlueCut();
			return;
		}
		Vec3 target = tableSurfaceTarget(playerRay(player), plane.workArea().y() + 1.01d);
		if (pending.planned != null && componentSelection != null
			&& pending.plannedTableRevision == table.clientDataRevision()
			&& sameHorizontalTarget(target, pending.lastTargetX, pending.lastTargetZ)) {
			highlightSelection(componentSelection);
			return;
		}
		FootprintGroups footprints = glueCutFootprints(table, pending.movingComponents);
		if (footprints == null) {
			abortPendingGlueCut();
			return;
		}
		SurgicalClientTopology.ConnectedPlacement planned = SurgicalClientTopology.snapConnectedGroup(
			footprints.moving, plane.workArea(), target.x, target.z, footprints.occupied);
		if (planned == null || !table.canApplyGlueCut(pending.subjectId, pending.targetId,
			planned.delta().x, planned.delta().z, plane)) {
			showNoSpace(player);
			abortPendingGlueCut();
			return;
		}

		for (Map.Entry<Integer, BitSet> entry : pending.movingComponents.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(pending.tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams)) {
				abortPendingGlueCut();
				return;
			}
			// The placement delta is absolute relative to the accepted server layout. Starting from
			// geometry.offsets would reuse the previous frame's preview and add the same delta again,
			// making a freshly cut glued group drift farther away on every preview refresh.
			Map<Integer, Vec3> offsets = new HashMap<>(geometry.serverOffsets);
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1))
					offsets.put(cube, offsets.getOrDefault(cube, Vec3.ZERO).add(planned.delta()));
			geometry.applyPreview(table, Map.copyOf(offsets), geometry.cutSeams, pending.joint);
		}
		SelectionHighlightEdges highlighted = connectedSelectionEdges(
			pending.tablePos, table, pending.movingComponents);
		if (highlighted == null) {
			abortPendingGlueCut();
			return;
		}
		pending.planned = planned;
		pending.lastTargetX = target.x;
		pending.lastTargetZ = target.z;
		pending.plannedTableRevision = table.clientDataRevision();
		seamSelection = null;
		cubeSelection = null;
		componentSelection = new Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), highlighted.glueJointEdges,
			highlighted.cubeEdges, highlighted.combinationEdges, List.of(), true, false);
		highlightSelection(componentSelection);
	}

	private static void confirmPendingGlueCut(LocalPlayer player) {
		PendingGlueCut pending = pendingGlueCut;
		if (pending == null)
			return;
		if (!player.getItemInHand(pending.hand).is(Items.SHEARS) || pending.planned == null) {
			showNoSpace(player);
			abortPendingGlueCut();
			return;
		}
		Selection selected = new Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), List.of(), true);
		Vec3 delta = pending.planned.delta();
		sendInteraction(selected, pending.hand, SurgicalTableInteractionPacket.Action.CUT_GLUE,
			SurgicalTableLayout.Proposal.EMPTY, delta.x, delta.z);
		ClientLevel level = Minecraft.getInstance().level;
		pendingGlueCut = null;
		componentSelection = null;
		if (level != null)
			beginVisualCommit(level, pending.tablePos, pending.plannedTableRevision,
				List.copyOf(pending.movingComponents.keySet()), false, false);
	}

	private static void abortPendingGlueCut() {
		PendingGlueCut pending = pendingGlueCut;
		pendingGlueCut = null;
		if (pending == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null
			&& minecraft.level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			for (int subjectId : pending.movingComponents.keySet()) {
				TableGeometry geometry = TABLES.get(new SubjectKey(pending.tablePos, subjectId));
				if (geometry != null && table.hasSubject(subjectId))
					geometry.clearPreview(table);
			}
		componentSelection = null;
	}

	@Nullable
	private static FootprintGroups glueCutFootprints(SurgicalTableBlockEntity table,
		Map<Integer, BitSet> movingComponents) {
		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			if (subject.occupiedFootprints().isEmpty())
				return null;
			BitSet component = movingComponents.get(subject.id());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (subject.containsFootprint(component, footprint))
					moving.add(footprint);
				else
					occupied.add(footprint);
			}
		}
		return moving.isEmpty() ? null : new FootprintGroups(List.copyOf(moving), List.copyOf(occupied));
	}

	@Nullable
	private static FootprintGroups seamCutFootprints(SurgicalTableBlockEntity table, PendingCut pending,
		TableGeometry editedGeometry, BitSet movedHere, SurgicalTablePlane.Plane plane) {
		SurgicalClientTopology.PlannedLayout current = SurgicalClientTopology.translateComponents(
			editedGeometry.observedCubeCount, editedGeometry.presentCubes, editedGeometry.seams,
			pending.proposedCuts, editedGeometry.layoutCubes, editedGeometry.serverOffsets,
			plane.workArea(), movedHere, Vec3.ZERO, List.of());
		if (current == null)
			return null;
		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : current.proposal().footprints())
			(movedHere.get(footprint.componentRoot()) ? moving : occupied).add(footprint);
		for (SurgicalSubject subject : table.getSubjects()) {
			if (subject.id() == pending.subjectId)
				continue;
			BitSet moved = pending.movingComponents.get(subject.id());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				(subject.containsFootprint(moved, footprint) ? moving : occupied).add(footprint);
		}
		return moving.isEmpty() ? null : new FootprintGroups(List.copyOf(moving), List.copyOf(occupied));
	}

	@Nullable
	private static BatchCutLayout planBatchCut(ClientLevel level, Selection selected) {
		TableGeometry geometry = TABLES.get(new SubjectKey(selected.tablePos, selected.subjectId));
		SurgicalTableBlockEntity table = level.getBlockEntity(selected.tablePos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		if (geometry == null || table == null
			|| selected.targetId < 0 || selected.targetId >= geometry.observedCubeCount)
			return null;
		SurgicalTableBlockEntity.BatchCutPlan cut = table.batchCutPlan(selected.subjectId,
			selected.targetId, selected.observedCubeCount, selected.seams);
		if (cut == null)
			return null;
		SurgicalTablePlane.Plane plane = clientPlane(level, selected.tablePos);
		if (!plane.valid() || !selected.tablePos.equals(plane.source()))
			return null;
		SurgicalClientTopology.PlannedLayout base = SurgicalClientTopology.preserveCompositeLayout(
			geometry.observedCubeCount, geometry.presentCubes, geometry.seams, cut.proposedCuts(),
			geometry.layoutCubes, geometry.serverOffsets, plane.workArea(), List.of());
		if (base == null)
			return null;
		BatchCutFootprints footprints = batchCutFootprints(table, selected.subjectId, cut.groups(),
			base.proposal());
		if (footprints == null)
			return null;
		SurgicalClientTopology.ConnectedGroupLayout groupLayout =
			SurgicalClientTopology.autoSnapConnectedGroups(footprints.groups, plane.workArea(),
				footprints.occupied);
		if (groupLayout == null)
			return null;
		SurgicalClientTopology.PlannedLayout planned = translateBatchProposal(base.proposal(),
			cut.groups(), groupLayout.deltas(), selected.subjectId);
		if (planned == null || !table.canApplyBatchCut(selected.subjectId, selected.targetId,
			selected.observedCubeCount, selected.seams, plane, planned.proposal(), groupLayout.deltas()))
			return null;
		return new BatchCutLayout(planned, groupLayout.deltas(), cut.groups(), cut.proposedCuts());
	}

	@Nullable
	private static BatchCutFootprints batchCutFootprints(SurgicalTableBlockEntity table, int editedSubjectId,
		List<Map<Integer, BitSet>> groups, SurgicalTableLayout.Proposal editedLayout) {
		List<List<SurgicalTableLayout.Footprint>> grouped = new ArrayList<>(groups.size());
		for (int groupId = 0; groupId < groups.size(); groupId++)
			grouped.add(new ArrayList<>());
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			List<SurgicalTableLayout.Footprint> footprints = subject.id() == editedSubjectId
				? editedLayout.footprints() : subject.occupiedFootprints();
			if (footprints.isEmpty())
				return null;
			for (SurgicalTableLayout.Footprint footprint : footprints) {
				int groupId = batchGroupContaining(groups, subject.id(), footprint);
				(groupId < 0 ? occupied : grouped.get(groupId)).add(footprint);
			}
		}
		for (List<SurgicalTableLayout.Footprint> group : grouped)
			if (group.isEmpty())
				return null;
		return new BatchCutFootprints(grouped.stream().map(List::copyOf).toList(),
			List.copyOf(occupied));
	}

	@Nullable
	private static SurgicalClientTopology.PlannedLayout translateBatchProposal(
		SurgicalTableLayout.Proposal base, List<Map<Integer, BitSet>> groups, List<Vec3> deltas,
		int subjectId) {
		if (groups.size() != deltas.size())
			return null;
		Map<Integer, Vec3> offsets = new HashMap<>();
		List<SurgicalTableLayout.CubeOffset> translatedOffsets = new ArrayList<>(base.offsets().size());
		for (SurgicalTableLayout.CubeOffset offset : base.offsets()) {
			int groupId = batchGroupContaining(groups, subjectId, offset.cubeId());
			Vec3 delta = groupId < 0 ? Vec3.ZERO : deltas.get(groupId);
			Vec3 translated = new Vec3(offset.x() + delta.x, offset.y(), offset.z() + delta.z);
			offsets.put(offset.cubeId(), translated);
			translatedOffsets.add(new SurgicalTableLayout.CubeOffset(offset.cubeId(), translated.x,
				translated.y, translated.z));
		}
		List<SurgicalTableLayout.Footprint> translatedFootprints = new ArrayList<>(base.footprints().size());
		for (SurgicalTableLayout.Footprint footprint : base.footprints()) {
			int groupId = batchGroupContaining(groups, subjectId, footprint);
			Vec3 delta = groupId < 0 ? Vec3.ZERO : deltas.get(groupId);
			translatedFootprints.add(new SurgicalTableLayout.Footprint(footprint.componentRoot(),
				footprint.minX() + delta.x, footprint.minZ() + delta.z,
				footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
				SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED));
		}
		return new SurgicalClientTopology.PlannedLayout(Map.copyOf(offsets),
			new SurgicalTableLayout.Proposal(translatedOffsets, translatedFootprints));
	}

	private static int batchGroupContaining(List<Map<Integer, BitSet>> groups, int subjectId,
		int cubeId) {
		if (cubeId < 0)
			return -1;
		for (int groupId = 0; groupId < groups.size(); groupId++) {
			BitSet cubes = groups.get(groupId).get(subjectId);
			if (cubes != null && cubes.get(cubeId))
				return groupId;
		}
		return -1;
	}

	private static int batchGroupContaining(List<Map<Integer, BitSet>> groups, int subjectId,
		SurgicalTableLayout.Footprint footprint) {
		if (footprint.componentRoot() >= 0)
			return batchGroupContaining(groups, subjectId, footprint.componentRoot());
		int matched = -1;
		for (int groupId = 0; groupId < groups.size(); groupId++) {
			BitSet cubes = groups.get(groupId).get(subjectId);
			if (cubes == null || cubes.isEmpty())
				continue;
			if (matched >= 0)
				return -1;
			matched = groupId;
		}
		return matched;
	}

	private static void beginBatchCutAnimation(ClientLevel level, Selection selected, BatchCutLayout layout) {
		batchCutAnimation = null;
		if (!(level.getBlockEntity(selected.tablePos) instanceof SurgicalTableBlockEntity table))
			return;
		Map<Integer, BatchCutMotion> motions = new HashMap<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			TableGeometry geometry = TABLES.get(new SubjectKey(selected.tablePos, subject.id()));
			if (geometry == null || !geometry.matchesModel(table, subject))
				continue;
			BitSet movedCubes = new BitSet(geometry.observedCubeCount);
			Map<Integer, Vec3> targetOffsets = new HashMap<>();
			for (int cube = geometry.presentCubes.nextSetBit(0); cube >= 0;
				cube = geometry.presentCubes.nextSetBit(cube + 1)) {
				int groupId = batchGroupContaining(layout.groups, subject.id(), cube);
				Vec3 delta = groupId < 0 ? Vec3.ZERO : layout.groupDeltas.get(groupId);
				Vec3 target = geometry.serverOffsets.getOrDefault(cube, Vec3.ZERO).add(delta);
				if (target.lengthSqr() > 1.0e-24d)
					targetOffsets.put(cube, target);
				// Group zero is the stable remainder and can have no horizontal delta. It still belongs
				// to this batch cut and may move vertically when the newly separated bodies are grounded.
				if (groupId >= 0)
					movedCubes.set(cube);
			}
			if (!movedCubes.isEmpty())
				motions.put(subject.id(), new BatchCutMotion(movedCubes, geometry.offsets,
					Map.copyOf(targetOffsets), geometry.bounds));
		}
		if (motions.isEmpty())
			return;
		batchCutAnimation = new BatchCutAnimation(selected.tablePos, table.clientDataRevision(),
			level.getGameTime() + BATCH_CUT_ANIMATION_TIMEOUT_TICKS, selected.subjectId,
			layout.proposedCuts, Map.copyOf(motions));
	}

	private static void updateBatchCutAnimation(ClientLevel level) {
		BatchCutAnimation animation = batchCutAnimation;
		if (animation == null)
			return;
		if (level.getGameTime() >= animation.expiresAtTick
			|| !(level.getBlockEntity(animation.tablePos) instanceof SurgicalTableBlockEntity table)) {
			batchCutAnimation = null;
			return;
		}
		int revision = table.clientDataRevision();
		if (!animation.acknowledged) {
			if (revision == animation.tableRevision)
				return;
			if (!batchCutStateMatches(table, animation)) {
				batchCutAnimation = null;
				return;
			}
			animation.acknowledged = true;
			animation.acknowledgedRevision = revision;
		} else if (revision != animation.acknowledgedRevision) {
			// Any later edit interrupts this one-shot visual. It is never reconstructed from synced state.
			batchCutAnimation = null;
			return;
		}
		if (!Float.isNaN(animation.startedAt)
			&& AnimationTickHolder.getRenderTime(level) - animation.startedAt >= BATCH_CUT_ANIMATION_TICKS) {
			batchCutAnimation = null;
			return;
		}
		if (!Float.isNaN(animation.startedAt))
			for (int subjectId : animation.motions.keySet())
				if (!TABLES.containsKey(new SubjectKey(animation.tablePos, subjectId))) {
					batchCutAnimation = null;
					return;
				}
	}

	private static boolean batchCutStateMatches(SurgicalTableBlockEntity table,
		BatchCutAnimation animation) {
		SurgicalSubject edited = table.getSubject(animation.editedSubjectId);
		if (edited == null || !edited.cutSeamsForRender().equals(animation.proposedCuts))
			return false;
		for (Map.Entry<Integer, BatchCutMotion> entry : animation.motions.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			if (subject == null || !offsetMapsEqual(subject.componentOffsetsForRender(),
				entry.getValue().targetServerOffsets))
				return false;
		}
		return true;
	}

	private static Map<Integer, Vec3> batchCutRenderOffsets(SurgicalTableBlockEntity table,
		SurgicalSubject subject, TableGeometry geometry) {
		BatchCutAnimation animation = batchCutAnimation;
		if (animation == null || !animation.tablePos.equals(table.getBlockPos()))
			return geometry.offsets;
		BatchCutMotion motion = animation.motions.get(subject.id());
		if (motion == null)
			return geometry.offsets;
		if (table.getLevel() instanceof ClientLevel level)
			updateBatchCutAnimation(level);
		animation = batchCutAnimation;
		if (animation == null || animation.motions.get(subject.id()) != motion || !animation.acknowledged)
			return geometry.offsets;
		if (!offsetMapsEqual(geometry.serverOffsets, motion.targetServerOffsets))
			return motion.interpolate(geometry.offsets, 0.0d);
		float renderTime = AnimationTickHolder.getRenderTime(table.getLevel());
		if (Float.isNaN(animation.startedAt))
			animation.startedAt = renderTime;
		double progress = Math.max(0.0d, Math.min(1.0d,
			(renderTime - animation.startedAt) / BATCH_CUT_ANIMATION_TICKS));
		if (progress >= 1.0d) {
			batchCutAnimation = null;
			return geometry.offsets;
		}
		double remaining = 1.0d - progress;
		double eased = 1.0d - remaining * remaining * remaining;
		return motion.interpolate(geometry.offsets, eased);
	}

	private static boolean offsetMapsEqual(Map<Integer, Vec3> first, Map<Integer, Vec3> second) {
		Set<Integer> cubes = new java.util.HashSet<>(first.keySet());
		cubes.addAll(second.keySet());
		for (int cube : cubes)
			if (first.getOrDefault(cube, Vec3.ZERO)
				.distanceToSqr(second.getOrDefault(cube, Vec3.ZERO)) > 1.0e-12d)
				return false;
		return true;
	}

	private static void updatePendingCut(@Nullable LocalPlayer player, ClientLevel level) {
		PendingCut pending = pendingCut;
		if (pending == null || player == null)
			return;
		if (!(level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			|| !player.getItemInHand(pending.hand).is(Items.SHEARS)) {
			abortPendingCut();
			return;
		}
		SurgicalTablePlane.Plane plane = clientPlane(level, pending.tablePos);
		if (!plane.valid() || !pending.tablePos.equals(plane.source())) {
			abortPendingCut();
			return;
		}
		Vec3 target = tableSurfaceTarget(playerRay(player), plane.workArea().y() + 1.01d);
		// This early-out has to come before the cut plan is re-derived. The plan is a function of the
		// synced table data, which `plannedTableRevision` already pins, so re-deriving it while the
		// crosshair sits still only rebuilds the connection graph once per frame for no answer change.
		if (pending.planned != null && componentSelection != null
			&& pending.plannedTableRevision == table.clientDataRevision()
			&& sameHorizontalTarget(target, pending.lastTargetX, pending.lastTargetZ)) {
			highlightSelection(componentSelection);
			return;
		}
		SurgicalTableBlockEntity.SeamCutPlan current = table.seamCutPlan(pending.subjectId, pending.targetId);
		if (current == null || !current.separates() || !pending.proposedCuts.equals(current.proposedCuts())
			|| !pending.movingComponents.equals(current.movingComponents())) {
			abortPendingCut();
			return;
		}
		TableGeometry editedGeometry = TABLES.get(new SubjectKey(pending.tablePos, pending.subjectId));
		BitSet movedHere = pending.movingComponents.get(pending.subjectId);
		FootprintGroups footprints = editedGeometry == null || movedHere == null ? null
			: seamCutFootprints(table, pending, editedGeometry, movedHere, plane);
		if (footprints == null) {
			showNoSpace(player);
			abortPendingCut();
			return;
		}
		SurgicalClientTopology.ConnectedPlacement planned = SurgicalClientTopology.snapConnectedGroup(
			footprints.moving, plane.workArea(), target.x, target.z, footprints.occupied);
		SurgicalClientTopology.PlannedLayout layout = planned == null ? null
			: SurgicalClientTopology.translateComponents(
				editedGeometry.observedCubeCount, editedGeometry.presentCubes, editedGeometry.seams,
				pending.proposedCuts, editedGeometry.layoutCubes, editedGeometry.serverOffsets,
				plane.workArea(), movedHere, planned.delta(), List.of());
		if (planned == null || layout == null || !table.canApplySeamCut(pending.subjectId, pending.targetId,
			layout.proposal(), planned.delta().x, planned.delta().z, plane)) {
			showNoSpace(player);
			abortPendingCut();
			return;
		}

		for (Map.Entry<Integer, BitSet> entry : pending.movingComponents.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(pending.tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams)) {
				abortPendingCut();
				return;
			}
			Map<Integer, Vec3> offsets = entry.getKey() == pending.subjectId
				? layout.offsets() : new HashMap<>(geometry.serverOffsets);
			if (entry.getKey() != pending.subjectId)
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1))
					offsets.put(cube, offsets.getOrDefault(cube, Vec3.ZERO).add(planned.delta()));
			BitSet cuts = entry.getKey() == pending.subjectId ? pending.proposedCuts : geometry.cutSeams;
			geometry.applyPreview(table, Map.copyOf(offsets), cuts);
		}
		SelectionHighlightEdges highlighted = connectedSelectionEdges(
			pending.tablePos, table, pending.movingComponents);
		if (highlighted == null) {
			abortPendingCut();
			return;
		}
		pending.planned = planned;
		pending.layout = layout.proposal();
		pending.lastTargetX = target.x;
		pending.lastTargetZ = target.z;
		pending.plannedTableRevision = table.clientDataRevision();
		seamSelection = null;
		cubeSelection = null;
		componentSelection = new Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), highlighted.glueJointEdges,
			highlighted.cubeEdges, highlighted.combinationEdges, List.of(), false, false);
		highlightSelection(componentSelection);
	}

	private static void confirmPendingCut(LocalPlayer player) {
		PendingCut pending = pendingCut;
		if (pending == null)
			return;
		if (!player.getItemInHand(pending.hand).is(Items.SHEARS) || pending.planned == null
			|| pending.layout == null) {
			showNoSpace(player);
			abortPendingCut();
			return;
		}
		Selection selected = new Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), List.of());
		Vec3 delta = pending.planned.delta();
		sendInteraction(selected, pending.hand, SurgicalTableInteractionPacket.Action.CUT,
			pending.layout, delta.x, delta.z);
		ClientLevel level = Minecraft.getInstance().level;
		pendingCut = null;
		componentSelection = null;
		if (level != null)
			beginVisualCommit(level, pending.tablePos, pending.plannedTableRevision,
				List.copyOf(pending.movingComponents.keySet()), false, false);
	}

	private static void abortPendingCut() {
		PendingCut pending = pendingCut;
		pendingCut = null;
		if (pending == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null
			&& minecraft.level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			for (int subjectId : pending.movingComponents.keySet()) {
				TableGeometry geometry = TABLES.get(new SubjectKey(pending.tablePos, subjectId));
				if (geometry != null && table.hasSubject(subjectId))
					geometry.clearPreview(table);
			}
		componentSelection = null;
	}

	private static Vec3 tableSurfaceTarget(Ray ray, double surfaceY) {
		double deltaY = ray.end.y - ray.start.y;
		if (Math.abs(deltaY) <= 1.0e-9d)
			return ray.end;
		double progress = (surfaceY - ray.start.y) / deltaY;
		if (!Double.isFinite(progress) || progress < 0.0d)
			return ray.end;
		return ray.start.add(ray.end.subtract(ray.start).scale(progress));
	}

	private static boolean sameHorizontalTarget(Vec3 target, double previousX, double previousZ) {
		return Math.abs(target.x - previousX) <= 1.0e-7d
			&& Math.abs(target.z - previousZ) <= 1.0e-7d;
	}

	private static SurgicalTablePlane.Plane clientPlane(ClientLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof SurgicalTableBlockEntity table) {
			SurgicalTablePlane.Plane cached = table.getClientPlane();
			if (cached != null)
				return cached;
		}
		return SurgicalTablePlane.scan(level, pos);
	}

	private static boolean clientAcceptsComponentLayout(ClientLevel level, BlockPos tablePos,
		int subjectId, BitSet cutSeams, SurgicalTableLayout.Proposal proposal,
		SurgicalTablePlane.Plane plane) {
		if (!(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table))
			return false;
		SurgicalSubject subject = table.getSubject(subjectId);
		return subject != null && (subject.cubeCount() == 0
			|| table.canApplyComponentLayout(subjectId, cutSeams, proposal, plane));
	}

	@Nullable
	private static List<SurgicalTableLayout.Footprint> occupiedOutsideEditingGroup(ClientLevel level,
		SurgicalTablePlane.Plane plane, BlockPos tablePos, int subjectId) {
		if (!(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		return SurgicalTablePlane.occupiedFootprints(level, plane, table.connectedSubjectIds(subjectId));
	}

	private static SurgicalModelRenderContext.CubeGeometry boundsGeometry(AABB bounds,
		Vec3 worldOffset) {
		List<Vec3> corners = new ArrayList<>(8);
		for (int z = 0; z < 2; z++)
			for (int y = 0; y < 2; y++)
				for (int x = 0; x < 2; x++)
					corners.add(new Vec3(x == 0 ? bounds.minX : bounds.maxX,
						y == 0 ? bounds.minY : bounds.maxY, z == 0 ? bounds.minZ : bounds.maxZ)
						.add(worldOffset));
		return new SurgicalModelRenderContext.CubeGeometry(0, corners);
	}

	private static List<SurgicalModelRenderContext.CubeGeometry> translateCubes(
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Vec3 offset) {
		if (offset.lengthSqr() <= 1.0e-24d)
			return cubes;
		List<SurgicalModelRenderContext.CubeGeometry> translated = new ArrayList<>(cubes.size());
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			translated.add(cube.withCorners(
				cube.corners().stream().map(corner -> corner.add(offset)).toList()));
		return List.copyOf(translated);
	}

	/**
	 * Expands a logical model envelope from captured component corners. Render batches are
	 * deliberately not consulted here: translucent-shell shrink, surface overlays and other
	 * presentation-only geometry must never alter table fit or persisted occupied footprints.
	 */
	@Nullable
	private static AABB logicalBounds(List<SurgicalModelRenderContext.CubeGeometry> cubes,
		Map<Integer, Vec3> offsets) {
		if (cubes.isEmpty())
			return null;
		AABB bounds = null;
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes) {
			Vec3 offset = offsets.getOrDefault(cube.cubeId(), Vec3.ZERO);
			for (Vec3 corner : cube.corners()) {
				double x = corner.x + offset.x;
				double y = corner.y + offset.y;
				double z = corner.z + offset.z;
				if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
					return null;
				AABB point = new AABB(x, y, z, x, y, z);
				bounds = bounds == null ? point : bounds.minmax(point);
			}
		}
		return bounds;
	}

	private static void showNoSpace(@Nullable LocalPlayer player) {
		if (player != null)
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.no_space"), true);
	}

	@SubscribeEvent
	public static void hideVanillaTableOutline(RenderHighlightEvent.Block event) {
		BlockPos target = event.getTarget().getBlockPos();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && (belongsToSelectionPlane(level, target, seamSelection)
			|| belongsToSelectionPlane(level, target, cubeSelection)
			|| belongsToSelectionPlane(level, target, componentSelection)
			|| belongsToSelectionPlane(level, target, symmetrySelection)
			|| belongsToSelectionPlane(level, target, limbSelection)
			|| belongsToSelectionPlane(level, target,
				wrenchSelection == null ? null : wrenchSelection.selection)))
			event.setCanceled(true);
	}

	private static boolean belongsToSelectionPlane(ClientLevel level, BlockPos target,
		@Nullable Selection selection) {
		if (selection == null)
			return false;
		SurgicalTablePlane.Plane plane = clientPlane(level, target);
		return plane.workArea().containsTile(selection.tablePos);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!event.getLevel().isClientSide())
			return;
		clear();
		SurgicalSourceModelRenderer.clear();
	}

	@Nullable
	private static Selection findSeamSelection(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable CubeHit cubeHit) {
		if (cubeHit != null && level.getBlockEntity(cubeHit.tablePos) instanceof SurgicalTableBlockEntity table) {
			SurgicalCombination combination = table.combinationContaining(
				cubeHit.geometry.subjectId, cubeHit.cubeId);
			if (combination != null) {
				return table.externalCombinationJoints(combination).isEmpty()
					? combinationSelection(cubeHit, table, combination)
					: findCombinationExternalGlueSelection(cubeHit, table, combination);
			}
		}
		Selection directHit = findDirectSeamSelection(player, level, ray);
		if (directHit != null)
			return directHit;
		if (cubeHit == null)
			return null;

		TableGeometry geometry = cubeHit.geometry;
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalClientTopology.Contact contact : geometry.contactsFor(cubeHit.cubeId)) {
			SurgicalAssembly.Seam seam = contact.seam();
			if (seam.first() != cubeHit.cubeId && seam.second() != cubeHit.cubeId)
				continue;
			Integer seamId = geometry.seamIds.get(seam);
			if (seamId == null || geometry.cutSeams.get(seamId)
				|| !geometry.presentCubes.get(seam.first()) || !geometry.presentCubes.get(seam.second()))
				continue;
			double distance = pointToContactDistance(cubeHit.location, contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = new Selection(cubeHit.tablePos, geometry.subjectId, seamId, geometry.observedCubeCount,
				geometry.seams, contact.edges(), geometry.cubeEdges(seam));
		}
		return best != null ? best : findGlueJointSelection(cubeHit);
	}

	private static Selection combinationSelection(CubeHit hit, SurgicalTableBlockEntity table,
		SurgicalCombination combination) {
		return new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
			combinationCubeEdges(hit.tablePos, table, combination), false, true);
	}

	@Nullable
	private static Selection findCombinationExternalGlueSelection(CubeHit hit,
		SurgicalTableBlockEntity table, SurgicalCombination combination) {
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalGlueJoint joint : table.externalCombinationJoints(combination)) {
			SurgicalSubject jointSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
			if (jointSubject == null)
				continue;
			int jointId = jointSubject.glueJoints().indexOf(joint);
			TableGeometry jointGeometry = TABLES.get(new SubjectKey(hit.tablePos, jointSubject.id()));
			GlueJointSelection candidate = jointId < 0 || jointGeometry == null ? null
				: glueJointSelection(hit.tablePos, table, jointSubject, jointGeometry, jointId);
			if (candidate == null) {
				jointSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
				if (jointSubject == null)
					continue;
				jointId = jointSubject.glueJoints().indexOf(joint);
				jointGeometry = TABLES.get(new SubjectKey(hit.tablePos, jointSubject.id()));
				candidate = jointId < 0 || jointGeometry == null ? null
					: glueJointSelection(hit.tablePos, table, jointSubject, jointGeometry, jointId);
			}
			if (candidate == null)
				continue;
			double distance = candidate.contact == null
				? distanceToGlueJoint(hit.location, table, hit.tablePos, joint)
				: pointToContactDistance(hit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			Selection selected = candidate.selection;
			List<SurgicalClientTopology.Edge> routedEdges = new ArrayList<>();
			List<SurgicalClientTopology.Edge> routedCombinationEdges = new ArrayList<>(
				combinationCubeEdges(hit.tablePos, table, combination));
			SurgicalGlueJoint.Endpoint outside = combination.contains(joint.first().subjectKey(),
				joint.first().cubeId()) ? joint.second() : joint.first();
			SurgicalSubject outsideSubject = table.getSubjectByPersistentId(outside.subjectKey());
			SurgicalCombination outsideCombination = outsideSubject == null ? null
				: outsideSubject.combinationContaining(outside.cubeId());
			if (outsideCombination != null)
				routedCombinationEdges.addAll(combinationCubeEdges(
					hit.tablePos, table, outsideCombination));
			else if (outsideSubject != null) {
				TableGeometry outsideGeometry = TABLES.get(new SubjectKey(hit.tablePos, outsideSubject.id()));
				SurgicalModelRenderContext.CubeGeometry outsideCube = outsideGeometry == null ? null
					: outsideGeometry.cubesById.get(outside.cubeId());
				if (outsideCube != null)
					routedEdges.addAll(SurgicalClientTopology.cubeEdges(outsideCube));
			}
			best = new Selection(selected.tablePos, selected.subjectId, selected.targetId,
				selected.observedCubeCount, selected.seams, selected.edges, List.of(),
				List.copyOf(routedEdges), List.copyOf(routedCombinationEdges), List.of(), true, false);
		}
		return best;
	}

	private static double distanceToGlueJoint(Vec3 point, SurgicalTableBlockEntity table,
		BlockPos tablePos, SurgicalGlueJoint joint) {
		Vec3 first = endpointCubeCenter(table, tablePos, joint.first());
		Vec3 second = endpointCubeCenter(table, tablePos, joint.second());
		if (first == null || second == null)
			return Double.MAX_VALUE;
		return point.distanceTo(first.add(second).scale(0.5d));
	}

	@Nullable
	private static Vec3 endpointCubeCenter(SurgicalTableBlockEntity table, BlockPos tablePos,
		SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = table.getSubjectByPersistentId(endpoint.subjectKey());
		TableGeometry geometry = subject == null ? null
			: TABLES.get(new SubjectKey(tablePos, subject.id()));
		SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
			: geometry.cubesById.get(endpoint.cubeId());
		return cube == null ? null : cubeCenter(cube);
	}

	@Nullable
	private static Selection findDirectSeamSelection(LocalPlayer player, ClientLevel level, Ray ray) {
		Selection best = null;
		Vec3 bestHit = null;
		BlockPos bestTablePos = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SubjectKey, TableGeometry> entry : TABLES.entrySet()) {
			TableGeometry geometry = entry.getValue();
			// Same ordering as findNearestCubeHit: the free slab test decides first, so a table the
			// ray misses costs neither a block-entity fetch nor a per-seam topology check.
			if (geometry.bounds == null
				|| !rayIntersectsBounds(ray, geometry.bounds, MAX_SELECTION_THRESHOLD))
				continue;
			BlockPos tablePos = entry.getKey().tablePos;
			if (!(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table)
				|| !table.hasSubject(entry.getKey().subjectId))
				continue;
			SurgicalSubject subject = table.getSubject(geometry.subjectId);
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				continue;
			for (SurgicalClientTopology.Contact contact : geometry.contacts) {
				SurgicalAssembly.Seam seam = contact.seam();
				Integer seamId = geometry.seamIds.get(seam);
				if (seamId == null || geometry.cutSeams.get(seamId)
					|| table.isInternalCombinationSeam(geometry.subjectId, seam)
					|| !geometry.presentCubes.get(seam.first()) || !geometry.presentCubes.get(seam.second()))
					continue;
				Vec3 hit = intersectContact(ray, contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				bestTablePos = tablePos;
				best = new Selection(tablePos, geometry.subjectId, seamId, geometry.observedCubeCount,
					geometry.seams, contact.edges(), geometry.cubeEdges(seam));
			}
			for (int jointId = 0; jointId < subject.glueJoints().size(); jointId++) {
				if (table.isInternalCombinationJoint(subject.glueJoints().get(jointId)))
					continue;
				GlueJointSelection glue = glueJointSelection(tablePos, table, subject, geometry, jointId);
				if (glue == null || glue.contact == null)
					continue;
				Vec3 hit = intersectContact(ray, glue.contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				bestTablePos = tablePos;
				best = glue.selection;
			}
		}
		return best != null && bestHit != null && bestTablePos != null
			&& isOccluded(level, player, ray.start, bestHit, bestTablePos) ? null : best;
	}

	@Nullable
	private static Selection findGlueJointSelection(CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject subject = table.getSubject(hit.geometry.subjectId);
		if (subject == null)
			return null;
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int jointId = 0; jointId < subject.glueJoints().size(); jointId++) {
			SurgicalGlueJoint joint = subject.glueJoints().get(jointId);
			if (!joint.touches(subject.persistentId(), hit.cubeId)
				|| table.isInternalCombinationJoint(joint))
				continue;
			GlueJointSelection candidate = glueJointSelection(hit.tablePos, table, subject,
				hit.geometry, jointId);
			if (candidate == null)
				continue;
			double distance = candidate.contact == null ? 0.0d
				: pointToContactDistance(hit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = candidate.selection;
		}
		return best;
	}

	@Nullable
	private static GlueJointSelection glueJointSelection(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalSubject subject, TableGeometry geometry, int jointId) {
		if (jointId < 0 || jointId >= subject.glueJoints().size())
			return null;
		SurgicalGlueJoint joint = subject.glueJoints().get(jointId);
		if (!joint.touches(subject.persistentId()) || table.isInternalCombinationJoint(joint))
			return null;
		SurgicalSubject firstSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject secondSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
		if (firstSubject == null || secondSubject == null)
			return null;
		TableGeometry firstGeometry = TABLES.get(new SubjectKey(tablePos, firstSubject.id()));
		TableGeometry secondGeometry = TABLES.get(new SubjectKey(tablePos, secondSubject.id()));
		if (firstGeometry == null || secondGeometry == null || !firstGeometry.topologyReady()
			|| !secondGeometry.topologyReady()
			|| !firstSubject.matchesObservedTopology(firstGeometry.observedCubeCount, firstGeometry.seams)
			|| !secondSubject.matchesObservedTopology(secondGeometry.observedCubeCount, secondGeometry.seams)
			|| !firstGeometry.presentCubes.get(joint.first().cubeId())
			|| !secondGeometry.presentCubes.get(joint.second().cubeId()))
			return null;
		SurgicalModelRenderContext.CubeGeometry first = firstGeometry.cubesById.get(joint.first().cubeId());
		SurgicalModelRenderContext.CubeGeometry second = secondGeometry.cubesById.get(joint.second().cubeId());
		if (first == null || second == null)
			return null;

		SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
			SurgicalAssembly.Seam.of(0, 1), List.of(
				new SurgicalModelRenderContext.CubeGeometry(0, first.corners()),
				new SurgicalModelRenderContext.CubeGeometry(1, second.corners())));
		List<SurgicalClientTopology.Edge> cubeEdges = new ArrayList<>(24);
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(first));
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(second));
		Selection selection = new Selection(tablePos, geometry.subjectId, jointId,
			geometry.observedCubeCount, geometry.seams,
			contact == null ? List.of() : contact.edges(), List.of(), List.copyOf(cubeEdges),
			List.of(), List.of(), true, false);
		return new GlueJointSelection(selection, contact);
	}

	@Nullable
	private static Vec3 intersectContact(Ray ray, SurgicalClientTopology.Contact contact) {
		Vec3 nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (List<Vec3> face : contact.faces()) {
			Vec3 hit = intersectPolygon(ray.start, ray.end, face);
			if (hit == null)
				continue;
			double distance = ray.start.distanceToSqr(hit);
			if (distance < nearestDistance) {
				nearest = hit;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	@Nullable
	private static Selection findConnectedComponentSelection(@Nullable CubeHit hit) {
		return findConnectedComponentSelection(hit, false);
	}

	@Nullable
	private static Selection findConnectedComponentSelection(@Nullable CubeHit hit,
		boolean includeLimbJoints) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		if (connectedSelectionCache != null && connectedSelectionCache.matches(hit,
			table.clientDataRevision(), includeLimbJoints))
			return connectedSelectionCache.selection;
		Map<Integer, BitSet> components = table.connectedComponents(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		if (components.isEmpty())
			return null;
		SelectionHighlightEdges edges = connectedSelectionEdges(hit.tablePos, table, components,
			includeLimbJoints);
		if (edges == null)
			return null;
		Selection selection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(), edges.glueJointEdges,
			edges.cubeEdges, edges.combinationEdges, edges.limbJointEdges, false, false);
		connectedSelectionCache = new CubeSelectionCache(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			table.clientDataRevision(), hit.geometry.renderRevision, selection, includeLimbJoints);
		return selection;
	}

	/** Selects one model cube without expanding through seams, glue joints, or combinations. */
	@Nullable
	private static Selection findCubeSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
		if (cube == null)
			return null;
		return new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
			List.copyOf(SurgicalClientTopology.cubeEdges(cube)));
	}

	/** Measures and permanently bakes the selected body's invariant physical data. */
	@Nullable
	private static PackedBodyMetrics selectedBodyMetrics(Selection selection) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null
			|| !(level.getBlockEntity(selection.tablePos()) instanceof SurgicalTableBlockEntity table))
			return null;
		Map<Integer, BitSet> components = table.connectedComponents(selection.subjectId(),
			selection.targetId(), selection.observedCubeCount(), selection.seams());
		if (components.isEmpty())
			return null;
		SurgicalAssembly preview = table.previewPackedAssembly(selection.subjectId(),
			selection.targetId(), selection.observedCubeCount(), selection.seams());
		if (preview == null)
			return null;

		PackedBodyMeasurement measured = measurePackedBody(preview);
		if (measured == null)
			return null;
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
		SurgicalAssembly.BodyBounds bodyBounds = SurgicalBodyBounds.measure(bodyCubes, allCubes,
			measured.visible());
		if (bodyBounds == null)
			return null;
		SlimeBionicAnimator.MobilityMetrics mobility =
			SlimeBionicAnimator.measureMobility(preview, measured.sources());
		bodyBounds = bodyBounds.withMobility(mobility.averageLegLength(),
			mobility.groundedLegCount(), mobility.groundedKneeCount(), mobility.legVolumeRatio());
		SurgicalBodyBounds.Envelope visible = measured.visible();
		Vec3 bodyOrigin = new Vec3((visible.minX() + visible.maxX()) * 0.5d + bodyBounds.centerX(),
			visible.minY(), (visible.minZ() + visible.maxZ()) * 0.5d + bodyBounds.centerZ());
		SurgicalAssembly.AttackGeometry attackGeometry =
			SlimeBionicAnimator.bakeAttackGeometry(preview, measured.sources(), bodyOrigin);
		List<Map<Integer, List<Vec3>>> hitboxCubes = measured.sources().stream()
			.map(source -> source.boxes().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> entry.getValue().points())))
			.toList();
		SurgicalAssembly.HitboxGeometry hitboxGeometry = SurgicalHitboxGeometry.measure(preview,
			hitboxCubes, visible, bodyBounds);
		if (hitboxGeometry == null)
			return null;
		return new PackedBodyMetrics(bodyBounds, hitboxGeometry, attackGeometry);
	}

	/** Runs the same rest-pose render used by the packed entity, once, while generating it. */
	@Nullable
	private static PackedBodyMeasurement measurePackedBody(SurgicalAssembly assembly) {
		EntityGeometry.Collector discarded = EntityGeometry.Collector.boundsOnly();
		MultiBufferSource discardedBuffer = renderType -> discarded;
		AABB logicalEnvelope = null;
		List<SlimeBionicAnimator.SourceState> sources = new ArrayList<>(assembly.sources().size());
		PoseStack poseStack = new PoseStack();
		SurgicalTablePoseResolver.applyInverseRotation(poseStack, assembly.layoutLayPose());
		for (SurgicalAssembly.Source source : assembly.sources()) {
			LivingEntity entity = SurgicalSourceModelRenderer.preview(source.profile());
			if (entity == null)
				return null;
			((SlimeMimicAccess) (Object) entity).createBiotech$setSlimeMimic(true);
			Map<Integer, Vec3> offsets = packedUprightOffsets(assembly, source);
			Map<Integer, SurgicalCubeRotation> rotations = packedUprightRotations(assembly, source);
			poseStack.pushPose();
			poseStack.translate(source.originOffset().x, source.originOffset().y, source.originOffset().z);
			if (assembly.preservesLayout())
				SurgicalTablePoseResolver.resolve(source.layPose()).apply(poseStack);
			SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(entity,
				source.cubeCount(), source.presentCubes(), offsets, rotations, poseStack, discardedBuffer,
				LightTexture.FULL_BRIGHT, 0.0f, 0.0f, true, null, false);
			poseStack.popPose();
			if (snapshot.observedCubeCount() != source.cubeCount()
				|| snapshot.cubes().size() != source.presentCubes().cardinality())
				return null;
			AABB sourceBounds = logicalBounds(snapshot.cubes(), Map.of());
			if (sourceBounds == null)
				return null;
			logicalEnvelope = logicalEnvelope == null ? sourceBounds : logicalEnvelope.minmax(sourceBounds);
			sources.add(new SlimeBionicAnimator.SourceState(
				SlimeBionicAnimator.measure(snapshot), offsets, rotations));
		}
		if (logicalEnvelope == null)
			return null;
		SurgicalBodyBounds.Envelope envelope = new SurgicalBodyBounds.Envelope(logicalEnvelope.minX,
			logicalEnvelope.minY, logicalEnvelope.minZ, logicalEnvelope.maxX, logicalEnvelope.maxY,
			logicalEnvelope.maxZ);
		return new PackedBodyMeasurement(List.copyOf(sources), envelope);
	}

	private static Map<Integer, Vec3> packedUprightOffsets(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<Integer, Vec3> transformed = new HashMap<>();
		source.cubeOffsets().forEach((cube, offset) ->
			transformed.put(cube, assembly.layoutLayPose().inverseRotate(offset)));
		return Map.copyOf(transformed);
	}

	private static Map<Integer, SurgicalCubeRotation> packedUprightRotations(
		SurgicalAssembly assembly, SurgicalAssembly.Source source) {
		Map<Integer, SurgicalCubeRotation> transformed = new HashMap<>();
		source.cubeRotations().forEach((cube, rotation) ->
			transformed.put(cube, rotation.inverseRotate(assembly.layoutLayPose())));
		return Map.copyOf(transformed);
	}

	@Nullable
	private static SelectionHighlightEdges connectedSelectionEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		return connectedSelectionEdges(tablePos, table, components, false);
	}

	@Nullable
	private static SelectionHighlightEdges connectedSelectionEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components, boolean includeLimbJoints) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		List<SurgicalClientTopology.Edge> combinationEdges = new ArrayList<>();
		java.util.Set<UUID> collapsed = new java.util.HashSet<>();
		for (Map.Entry<Integer, BitSet> entry : components.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			BitSet ordinary = (BitSet) entry.getValue().clone();
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalCombination combination = subject.combinationContaining(cube);
				if (combination == null || !combinationFullySelected(table, components, combination))
					continue;
				for (SurgicalCombination.Member member : combination.members())
					if (member.subjectKey().equals(subject.persistentId()))
						ordinary.clear(member.cubeId());
				if (collapsed.add(combination.id()))
					combinationEdges.addAll(combinationCubeEdges(tablePos, table, combination));
			}
			edges.addAll(geometry.componentCubeEdges(ordinary));
		}
		List<SurgicalClientTopology.Edge> glueJointEdges = selectedGlueJointEdges(tablePos, table, components);
		List<SurgicalClientTopology.Edge> limbJointEdges = includeLimbJoints
			? selectedLimbJointEdges(tablePos, table, components) : List.of();
		return glueJointEdges == null || limbJointEdges == null ? null
			: new SelectionHighlightEdges(edges, combinationEdges, glueJointEdges, limbJointEdges);
	}

	/** Keeps glue boundaries visible when separately honey-fused parts are selected as one rigid group. */
	@Nullable
	private static List<SurgicalClientTopology.Edge> selectedGlueJointEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalSubject subject : table.getSubjects())
			joints.addAll(subject.glueJoints());
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (SurgicalGlueJoint joint : joints) {
			if (table.isInternalCombinationJoint(joint)
				|| !selectedEndpoint(table, components, joint.first())
				|| !selectedEndpoint(table, components, joint.second()))
				continue;
			SurgicalSubject firstSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
			SurgicalSubject secondSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
			TableGeometry firstGeometry = firstSubject == null ? null
				: TABLES.get(new SubjectKey(tablePos, firstSubject.id()));
			TableGeometry secondGeometry = secondSubject == null ? null
				: TABLES.get(new SubjectKey(tablePos, secondSubject.id()));
			if (firstSubject == null || secondSubject == null
				|| !geometryReadyForUse(table, firstSubject, firstGeometry)
				|| !geometryReadyForUse(table, secondSubject, secondGeometry))
				return null;
			SurgicalModelRenderContext.CubeGeometry first =
				firstGeometry.cubesById.get(joint.first().cubeId());
			SurgicalModelRenderContext.CubeGeometry second =
				secondGeometry.cubesById.get(joint.second().cubeId());
			if (first == null || second == null)
				return null;
			SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
				SurgicalAssembly.Seam.of(0, 1), List.of(
					new SurgicalModelRenderContext.CubeGeometry(0, first.corners()),
					new SurgicalModelRenderContext.CubeGeometry(1, second.corners())));
			if (contact != null)
				edges.addAll(contact.edges());
		}
		return List.copyOf(edges);
	}

	/** Uses the same contact-edge geometry as the red cutting preview for selected anatomical joints. */
	@Nullable
	private static List<SurgicalClientTopology.Edge> selectedLimbJointEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (SurgicalLimbJoint joint : table.limbJoints()) {
			if (!selectedEndpoint(table, components, joint.child())
				|| !selectedEndpoint(table, components, joint.parent()))
				continue;
			SurgicalSubject childSubject = table.getSubjectByPersistentId(joint.child().subjectKey());
			SurgicalSubject parentSubject = table.getSubjectByPersistentId(joint.parent().subjectKey());
			TableGeometry childGeometry = childSubject == null ? null
				: TABLES.get(new SubjectKey(tablePos, childSubject.id()));
			TableGeometry parentGeometry = parentSubject == null ? null
				: TABLES.get(new SubjectKey(tablePos, parentSubject.id()));
			if (childSubject == null || parentSubject == null
				|| !geometryReadyForUse(table, childSubject, childGeometry)
				|| !geometryReadyForUse(table, parentSubject, parentGeometry))
				return null;
			SurgicalModelRenderContext.CubeGeometry child =
				childGeometry.cubesById.get(joint.child().cubeId());
			SurgicalModelRenderContext.CubeGeometry parent =
				parentGeometry.cubesById.get(joint.parent().cubeId());
			if (child == null || parent == null)
				return null;

			SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
				SurgicalAssembly.Seam.of(0, 1), List.of(
					new SurgicalModelRenderContext.CubeGeometry(0, child.corners()),
					new SurgicalModelRenderContext.CubeGeometry(1, parent.corners())));
			if (contact != null)
				edges.addAll(contact.edges());
		}
		return List.copyOf(edges);
	}

	private static boolean selectedEndpoint(SurgicalTableBlockEntity table, Map<Integer, BitSet> components,
		SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = table.getSubjectByPersistentId(endpoint.subjectKey());
		BitSet selected = subject == null ? null : components.get(subject.id());
		return selected != null && selected.get(endpoint.cubeId());
	}

	private static boolean combinationFullySelected(SurgicalTableBlockEntity table,
		Map<Integer, BitSet> components, SurgicalCombination combination) {
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			BitSet selected = subject == null ? null : components.get(subject.id());
			if (selected == null || !selected.get(member.cubeId()))
				return false;
		}
		return true;
	}

	/** Uses the ordinary per-cube wireframe for honey combinations; only its colour is special. */
	private static List<SurgicalClientTopology.Edge> combinationCubeEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, SurgicalCombination combination) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>(combination.members().size() * 12);
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			TableGeometry geometry = subject == null ? null
				: TABLES.get(new SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(cube));
		}
		return List.copyOf(edges);
	}

	@Nullable
	private static Selection findDirectConnectionSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		if (combination != null)
			return combinationSelection(hit, table, combination);
		if (table != null && directSelectionCache != null
			&& directSelectionCache.matches(hit, table.clientDataRevision()))
			return directSelectionCache.selection;
		if (table == null)
			return null;
		Map<Integer, BitSet> direct = table.directConnections(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		List<SurgicalClientTopology.Edge> connectedEdges = direct.isEmpty() ? null
			: directConnectionEdges(hit, table, direct);
		SurgicalModelRenderContext.CubeGeometry hitCube = hit.geometry.cubesById.get(hit.cubeId);
		if (connectedEdges == null || hitCube == null)
			return null;
		Selection selection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams,
			SurgicalClientTopology.cubeEdges(hitCube), connectedEdges);
		directSelectionCache = new CubeSelectionCache(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			table.clientDataRevision(), hit.geometry.renderRevision, selection);
		return selection;
	}

	/** Shift-shears renders every one-hop neighbour blue while reserving red for the hit cube. */
	@Nullable
	private static List<SurgicalClientTopology.Edge> directConnectionEdges(CubeHit hit,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> direct) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : direct.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			TableGeometry geometry = TABLES.get(new SubjectKey(hit.tablePos, entry.getKey()));
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				if (entry.getKey() == hit.geometry.subjectId && cube == hit.cubeId)
					continue;
				SurgicalModelRenderContext.CubeGeometry connected = geometry.cubesById.get(cube);
				if (connected != null)
					edges.addAll(SurgicalClientTopology.cubeEdges(connected));
			}
		}
		return List.copyOf(edges);
	}

	@Nullable
	private static Selection findDirectConnectionCutSelection(@Nullable CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = hit != null && level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		if (combination != null)
			return table.externalCombinationJoints(combination).isEmpty()
				? null : combinationSelection(hit, table, combination);
		if (hit == null || table == null)
			return null;
		Map<Integer, BitSet> direct = table.directConnections(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		int connectedCubes = direct.values().stream().mapToInt(BitSet::cardinality).sum();
		return connectedCubes <= 1 ? null : findDirectConnectionSelection(hit);
	}

	@Nullable
	private static CubeHit findNearestCubeHit(LocalPlayer player, ClientLevel level, Ray ray) {
		CubeHit best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SubjectKey, TableGeometry> entry : TABLES.entrySet()) {
			TableGeometry geometry = entry.getValue();
			// The slab test allocates nothing and touches neither the world nor the subject, while
			// matchesObservedTopology boxes a Long per seam. Both are pure rejections, so the free
			// one runs first and the rest of the loop body is skipped for every table the ray misses.
			if (geometry.bounds == null || !rayIntersectsBounds(ray, geometry.bounds, 1.0e-6d))
				continue;
			BlockPos pos = entry.getKey().tablePos;
			if (!(level.getBlockEntity(pos) instanceof SurgicalTableBlockEntity table)
				|| !table.hasSubject(entry.getKey().subjectId))
				continue;
			SurgicalSubject subject = table.getSubject(geometry.subjectId);
			if (subject == null || !geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				continue;
			for (CubeTarget target : geometry.cubeTargets) {
				SurgicalModelRenderContext.CubeGeometry cube = target.geometry;
				if (!geometry.presentCubes.get(cube.cubeId()))
					continue;
				if (!rayIntersectsBounds(ray, target.bounds, 1.0e-6d))
					continue;
				for (int faceIndex = 0; faceIndex < SurgicalClientTopology.CUBE_FACES.length; faceIndex++) {
					int[] faceIndices = SurgicalClientTopology.CUBE_FACES[faceIndex];
					Vec3 hit = intersectQuad(ray.start, ray.end, cube, faceIndices);
					if (hit == null)
						continue;
					double distance = ray.start.distanceToSqr(hit);
					if (distance >= bestDistance)
						continue;
					bestDistance = distance;
					best = new CubeHit(pos, geometry, cube.cubeId(), faceIndex, hit, null);
				}
			}
		}
		return best != null && isOccluded(level, player, ray.start, best.location, best.tablePos) ? null : best;
	}

	@Nullable
	private static CubeHit snapGlueHit(@Nullable CubeHit hit) {
		if (hit == null || hit.gluePoint != null)
			return hit;
		SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
		GluePoint point = cube == null ? null : snapGluePoint(cube, hit.faceIndex, hit.location);
		return point == null ? null : new CubeHit(hit.tablePos, hit.geometry, hit.cubeId,
			hit.faceIndex, point.location, point);
	}

	/**
	 * Divides the selected face into model-pixel cells. A hit snaps either to the
	 * containing cell or to its nearest grid intersection, including intersections on
	 * the outer cube boundary.
	 */
	@Nullable
	private static GluePoint snapGluePoint(SurgicalModelRenderContext.CubeGeometry cube,
		int faceIndex, Vec3 rawHit) {
		if (faceIndex < 0 || faceIndex >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		int[] face = SurgicalClientTopology.CUBE_FACES[faceIndex];
		Vec3 origin = cube.corners().get(face[0]);
		Vec3 edgeU = cube.corners().get(face[1]).subtract(origin);
		Vec3 edgeV = cube.corners().get(face[3]).subtract(origin);
		double uu = edgeU.dot(edgeU);
		double uv = edgeU.dot(edgeV);
		double vv = edgeV.dot(edgeV);
		double determinant = uu * vv - uv * uv;
		if (uu <= 1.0e-18d || vv <= 1.0e-18d || Math.abs(determinant) <= 1.0e-18d)
			return null;

		Vec3 relative = rawHit.subtract(origin);
		double ru = relative.dot(edgeU);
		double rv = relative.dot(edgeV);
		double u = Math.max(0.0d, Math.min(1.0d, (ru * vv - rv * uv) / determinant));
		double v = Math.max(0.0d, Math.min(1.0d, (rv * uu - ru * uv) / determinant));
		ModelPixelSpan modelU = null;
		ModelPixelSpan modelV = null;
		if (!cube.modelCorners().isEmpty()) {
			Vec3 modelOrigin = cube.modelCorners().get(face[0]);
			modelU = modelPixelSpan(modelOrigin, cube.modelCorners().get(face[1]));
			modelV = modelPixelSpan(modelOrigin, cube.modelCorners().get(face[3]));
		}
		SurgicalModelRenderContext.FaceGrid faceGrid = cube.faceGrids().isEmpty()
			? null : cube.faceGrids().get(faceIndex);
		double pixelsU = normalizedPixelLength(modelU != null ? modelU.length()
			: faceGrid != null && faceGrid.valid() ? faceGrid.pixelsU() : Math.sqrt(uu) / MODEL_PIXEL_SIZE);
		double pixelsV = normalizedPixelLength(modelV != null ? modelV.length()
			: faceGrid != null && faceGrid.valid() ? faceGrid.pixelsV() : Math.sqrt(vv) / MODEL_PIXEL_SIZE);
		PixelInterval cellU = modelU != null
			? pixelInterval(u, modelU.start, modelU.end) : pixelInterval(u, 0.0d, pixelsU);
		PixelInterval cellV = modelV != null
			? pixelInterval(v, modelV.start, modelV.end) : pixelInterval(v, 0.0d, pixelsV);
		if (cellU == null || cellV == null)
			return null;

		Vec3 cellCenter = origin.add(edgeU.scale(cellU.center)).add(edgeV.scale(cellV.center));
		double intersectionU = nearestEndpoint(u, cellU);
		double intersectionV = nearestEndpoint(v, cellV);
		Vec3 intersection = origin.add(edgeU.scale(intersectionU)).add(edgeV.scale(intersectionV));
		boolean selectIntersection = rawHit.distanceToSqr(intersection) < rawHit.distanceToSqr(cellCenter);
		Vec3 location = selectIntersection ? intersection : cellCenter;

		Vec3 normal = edgeU.cross(edgeV).normalize();
		if (normal.dot(location.subtract(cubeCenter(cube))) < 0.0d)
			normal = normal.scale(-1.0d);
		Vec3 surfaceOffset = normal.scale(GLUE_POINT_SURFACE_OFFSET);
		List<SurgicalClientTopology.Edge> marker = new ArrayList<>(4);
		if (!selectIntersection) {
			Vec3 first = origin.add(edgeU.scale(cellU.start))
				.add(edgeV.scale(cellV.start)).add(surfaceOffset);
			Vec3 second = origin.add(edgeU.scale(cellU.end))
				.add(edgeV.scale(cellV.start)).add(surfaceOffset);
			Vec3 third = origin.add(edgeU.scale(cellU.end))
				.add(edgeV.scale(cellV.end)).add(surfaceOffset);
			Vec3 fourth = origin.add(edgeU.scale(cellU.start))
				.add(edgeV.scale(cellV.end)).add(surfaceOffset);
			marker.add(new SurgicalClientTopology.Edge(first, second));
			marker.add(new SurgicalClientTopology.Edge(second, third));
			marker.add(new SurgicalClientTopology.Edge(third, fourth));
			marker.add(new SurgicalClientTopology.Edge(fourth, first));
		} else {
			Vec3 markerCenter = intersection.add(surfaceOffset);
			Vec3 halfU = edgeU.scale(GLUE_POINT_CROSS_HALF_LENGTH_PIXELS / pixelsU);
			Vec3 halfV = edgeV.scale(GLUE_POINT_CROSS_HALF_LENGTH_PIXELS / pixelsV);
			marker.add(new SurgicalClientTopology.Edge(markerCenter.subtract(halfU),
				markerCenter.add(halfU)));
			marker.add(new SurgicalClientTopology.Edge(markerCenter.subtract(halfV),
				markerCenter.add(halfV)));
		}
		return new GluePoint(location, marker);
	}

	private static double nearestEndpoint(double progress, PixelInterval interval) {
		return Math.abs(progress - interval.start) < Math.abs(progress - interval.end)
			? interval.start : interval.end;
	}

	private static double normalizedPixelLength(double length) {
		double nearest = Math.rint(length);
		return Math.abs(length - nearest) <= GLUE_POINT_PIXEL_EPSILON ? nearest : length;
	}

	@Nullable
	private static ModelPixelSpan modelPixelSpan(Vec3 first, Vec3 second) {
		Vec3 delta = second.subtract(first);
		double x = Math.abs(delta.x);
		double y = Math.abs(delta.y);
		double z = Math.abs(delta.z);
		if (x >= y && x >= z && x > GLUE_POINT_PIXEL_EPSILON)
			return new ModelPixelSpan(first.x, second.x);
		if (y >= z && y > GLUE_POINT_PIXEL_EPSILON)
			return new ModelPixelSpan(first.y, second.y);
		return z > GLUE_POINT_PIXEL_EPSILON ? new ModelPixelSpan(first.z, second.z) : null;
	}

	@Nullable
	private static PixelInterval pixelInterval(double progress, double spanStart, double spanEnd) {
		double minimum = Math.min(spanStart, spanEnd);
		double maximum = Math.max(spanStart, spanEnd);
		if (!Double.isFinite(progress) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
			|| maximum - minimum <= GLUE_POINT_PIXEL_EPSILON)
			return null;
		double coordinate = spanStart + (spanEnd - spanStart) * Math.max(0.0d, Math.min(1.0d, progress));
		double sample = coordinate >= maximum - GLUE_POINT_PIXEL_EPSILON
			? Math.nextDown(maximum) : Math.max(minimum, coordinate);
		double lower = Math.max(minimum, Math.floor(sample));
		double upper = Math.min(maximum, Math.floor(sample) + 1.0d);
		if (upper <= lower + GLUE_POINT_PIXEL_EPSILON)
			return null;
		double first = (lower - spanStart) / (spanEnd - spanStart);
		double second = (upper - spanStart) / (spanEnd - spanStart);
		double start = Math.min(first, second);
		double end = Math.max(first, second);
		return new PixelInterval(start, end, (start + end) * 0.5d);
	}

	private static Ray playerRay(LocalPlayer player) {
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		Vec3 start = player.getEyePosition();
		return new Ray(start, start.add(player.getViewVector(1.0f).scale(range)));
	}

	private static Map<SurgicalAssembly.Seam, Integer> seamIds(List<SurgicalAssembly.Seam> seams) {
		Map<SurgicalAssembly.Seam, Integer> result = new HashMap<>();
		for (int seamId = 0; seamId < seams.size(); seamId++)
			result.put(seams.get(seamId), seamId);
		return Map.copyOf(result);
	}

	private static boolean isOccluded(ClientLevel level, LocalPlayer player, Vec3 start, Vec3 hit,
		BlockPos tablePos) {
		BlockHitResult blockHit = level.clip(new ClipContext(start, hit, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.BLOCK
			|| start.distanceToSqr(blockHit.getLocation()) + 1.0e-5d >= start.distanceToSqr(hit))
			return false;
		BlockPos obstruction = blockHit.getBlockPos();
		if (level.getBlockState(obstruction).getBlock() instanceof SurgicalTableBlock) {
			SurgicalTablePlane.Plane plane = clientPlane(level, obstruction);
			if (plane.workArea().containsTile(tablePos))
				return false;
		}
		return true;
	}

	/** Allocation-free segment/AABB broad phase used before oriented cube/contact intersection. */
	private static boolean rayIntersectsBounds(Ray ray, AABB bounds, double inflation) {
		double startX = ray.start.x;
		double startY = ray.start.y;
		double startZ = ray.start.z;
		double deltaX = ray.end.x - startX;
		double deltaY = ray.end.y - startY;
		double deltaZ = ray.end.z - startZ;
		double entry = 0.0d;
		double exit = 1.0d;

		double min = bounds.minX - inflation;
		double max = bounds.maxX + inflation;
		if (Math.abs(deltaX) <= 1.0e-12d) {
			if (startX < min || startX > max)
				return false;
		} else {
			double first = (min - startX) / deltaX;
			double second = (max - startX) / deltaX;
			if (first > second) {
				double swap = first;
				first = second;
				second = swap;
			}
			entry = Math.max(entry, first);
			exit = Math.min(exit, second);
			if (entry > exit)
				return false;
		}

		min = bounds.minY - inflation;
		max = bounds.maxY + inflation;
		if (Math.abs(deltaY) <= 1.0e-12d) {
			if (startY < min || startY > max)
				return false;
		} else {
			double first = (min - startY) / deltaY;
			double second = (max - startY) / deltaY;
			if (first > second) {
				double swap = first;
				first = second;
				second = swap;
			}
			entry = Math.max(entry, first);
			exit = Math.min(exit, second);
			if (entry > exit)
				return false;
		}

		min = bounds.minZ - inflation;
		max = bounds.maxZ + inflation;
		if (Math.abs(deltaZ) <= 1.0e-12d)
			return startZ >= min && startZ <= max;
		double first = (min - startZ) / deltaZ;
		double second = (max - startZ) / deltaZ;
		if (first > second) {
			double swap = first;
			first = second;
			second = swap;
		}
		entry = Math.max(entry, first);
		exit = Math.min(exit, second);
		return entry <= exit;
	}

	@Nullable
	private static Vec3 intersectQuad(Vec3 start, Vec3 end,
		SurgicalModelRenderContext.CubeGeometry cube, int[] indices) {
		Vec3 first = cube.corners().get(indices[0]);
		Vec3 edgeU = cube.corners().get(indices[1]).subtract(first);
		Vec3 edgeV = cube.corners().get(indices[3]).subtract(first);
		Vec3 normal = edgeU.cross(edgeV);
		Vec3 ray = end.subtract(start);
		double denominator = normal.dot(ray);
		if (Math.abs(denominator) < 1.0e-9d)
			return null;
		double t = normal.dot(first.subtract(start)) / denominator;
		if (t < 0.0d || t > 1.0d)
			return null;

		Vec3 hit = start.add(ray.scale(t));
		Vec3 relative = hit.subtract(first);
		double uu = edgeU.dot(edgeU);
		double uv = edgeU.dot(edgeV);
		double vv = edgeV.dot(edgeV);
		double ru = relative.dot(edgeU);
		double rv = relative.dot(edgeV);
		double determinant = uu * vv - uv * uv;
		if (Math.abs(determinant) < 1.0e-12d)
			return null;
		double u = (ru * vv - rv * uv) / determinant;
		double v = (rv * uu - ru * uv) / determinant;
		return u >= -1.0e-5d && u <= 1.00001d && v >= -1.0e-5d && v <= 1.00001d ? hit : null;
	}

	@Nullable
	private static Vec3 intersectPolygon(Vec3 start, Vec3 end, List<Vec3> polygon) {
		if (polygon.size() < 3)
			return null;
		Vec3 origin = polygon.getFirst();
		Vec3 normal = Vec3.ZERO;
		for (int vertex = 1; vertex + 1 < polygon.size(); vertex++) {
			normal = polygon.get(vertex).subtract(origin)
				.cross(polygon.get(vertex + 1).subtract(origin));
			if (normal.lengthSqr() > 1.0e-12d)
				break;
		}
		if (normal.lengthSqr() <= 1.0e-12d)
			return null;
		normal = normal.normalize();
		Vec3 direction = end.subtract(start);
		double denominator = normal.dot(direction);
		if (Math.abs(denominator) <= 1.0e-9d)
			return null;
		double amount = normal.dot(origin.subtract(start)) / denominator;
		if (amount < 0.0d || amount > 1.0d)
			return null;
		Vec3 hit = start.add(direction.scale(amount));
		return insideConvexPolygon(hit, polygon, normal) ? hit : null;
	}

	private static double pointToContactDistance(Vec3 point, SurgicalClientTopology.Contact contact) {
		double best = Double.MAX_VALUE;
		for (List<Vec3> face : contact.faces())
			best = Math.min(best, pointToPolygonDistance(point, face));
		return best;
	}

	private static double pointToPolygonDistance(Vec3 point, List<Vec3> polygon) {
		if (polygon.size() < 3)
			return Double.MAX_VALUE;
		Vec3 origin = polygon.getFirst();
		Vec3 normal = Vec3.ZERO;
		for (int i = 1; i + 1 < polygon.size(); i++) {
			normal = polygon.get(i).subtract(origin).cross(polygon.get(i + 1).subtract(origin));
			if (normal.lengthSqr() > 1.0e-12d)
				break;
		}
		if (normal.lengthSqr() <= 1.0e-12d)
			return Double.MAX_VALUE;
		normal = normal.normalize();
		double planeDistance = point.subtract(origin).dot(normal);
		Vec3 projected = point.subtract(normal.scale(planeDistance));
		if (insideConvexPolygon(projected, polygon, normal))
			return Math.abs(planeDistance);

		double best = Double.MAX_VALUE;
		for (int edge = 0; edge < polygon.size(); edge++)
			best = Math.min(best, pointToSegmentDistance(point, polygon.get(edge),
				polygon.get((edge + 1) % polygon.size())));
		return best;
	}

	private static boolean insideConvexPolygon(Vec3 point, List<Vec3> polygon, Vec3 normal) {
		double winding = 0.0d;
		for (int edge = 0; edge < polygon.size(); edge++) {
			Vec3 start = polygon.get(edge);
			Vec3 end = polygon.get((edge + 1) % polygon.size());
			double side = end.subtract(start).cross(point.subtract(start)).dot(normal);
			if (Math.abs(side) <= 1.0e-9d)
				continue;
			if (winding == 0.0d)
				winding = Math.signum(side);
			else if (Math.signum(side) != winding)
				return false;
		}
		return true;
	}

	private static double pointToSegmentDistance(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSquared = segment.lengthSqr();
		if (lengthSquared <= 1.0e-12d)
			return point.distanceTo(start);
		double amount = point.subtract(start).dot(segment) / lengthSquared;
		amount = Math.max(0.0d, Math.min(1.0d, amount));
		return point.distanceTo(start.add(segment.scale(amount)));
	}

	private static void highlightSelection(Selection selection) {
		highlightSelection(selection, false);
	}

	private static void highlightSelection(Selection selection, boolean highlightLimbJoints) {
		SEAM_OUTLINE.show(selection.edges, SEAM_HIGHLIGHT_COLOR);
		CUBE_OUTLINE.show(selection.cubeEdges, CUBE_HIGHLIGHT_COLOR);
		COMBINATION_OUTLINE.show(selection.combinationEdges, HONEY_HIGHLIGHT_COLOR);
		GLUE_JOINT_OUTLINE.show(selection.glueJointEdges, CUBE_HIGHLIGHT_COLOR);
		if (highlightLimbJoints)
			LIMB_JOINT_OUTLINE.show(selection.limbJointEdges, LIMB_JOINT_HIGHLIGHT_COLOR);
		else
			LIMB_JOINT_OUTLINE.clear();
	}

	private static void clearSeamHighlight() {
		SEAM_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		LIMB_JOINT_OUTLINE.clear();
	}

	private static boolean isEmptyBox(ItemStack stack) {
		return CapturedEntityBoxItem.isBox(stack) && !CapturedEntityBoxItem.hasCapturedEntity(stack);
	}

	private static boolean isEmptyLargeBox(ItemStack stack) {
		return stack.getItem() instanceof LargeCardboardBoxItem
			&& !CapturedEntityBoxItem.hasCapturedEntity(stack);
	}

	private static boolean isStandardGlue(ItemStack stack) {
		return stack.getItem() instanceof SuperGlueItem && !(stack.getItem() instanceof SmartSuperGlueItem);
	}

	private static boolean isSmartGlue(ItemStack stack) {
		return stack.getItem() instanceof SmartSuperGlueItem;
	}

	private static boolean isSurgicalGlue(ItemStack stack) {
		return isStandardGlue(stack) || isSmartGlue(stack);
	}

	private static boolean isSymmetryWand(ItemStack stack) {
		return stack.getItem() instanceof SymmetryWandItem;
	}

	@Nullable
	private static SurgicalLimbType heldLimbType(ItemStack stack) {
		return stack.getItem() instanceof SurgicalJointItem joint ? joint.limbType() : null;
	}

	private static void clearPendingLimb() {
		pendingLimb = null;
		limbSelection = null;
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
	}

	@Nullable
	private static LimbJointSelection findLimbJointSelection(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable CubeHit cubeHit) {
		LimbJointSelection direct = findDirectLimbJointSelection(player, level, ray);
		if (direct != null)
			return direct;
		if (cubeHit == null
			|| !(level.getBlockEntity(cubeHit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject hitSubject = table.getSubject(cubeHit.geometry.subjectId);
		if (hitSubject == null)
			return null;

		LimbJointSelection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalLimbJoint joint : table.limbJoints()) {
			if (!joint.touches(hitSubject.persistentId(), cubeHit.cubeId))
				continue;
			LimbJointSelection candidate = limbJointSelection(cubeHit.tablePos, table, joint);
			if (candidate == null)
				continue;
			double distance = candidate.contact == null ? 0.0d
				: pointToContactDistance(cubeHit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = candidate;
		}
		return best;
	}

	@Nullable
	private static LimbJointSelection findDirectLimbJointSelection(LocalPlayer player, ClientLevel level,
		Ray ray) {
		Set<BlockPos> checkedTables = new java.util.HashSet<>();
		LimbJointSelection best = null;
		Vec3 bestHit = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SubjectKey, TableGeometry> entry : TABLES.entrySet()) {
			TableGeometry geometry = entry.getValue();
			if (geometry.bounds == null
				|| !rayIntersectsBounds(ray, geometry.bounds, MAX_SELECTION_THRESHOLD))
				continue;
			BlockPos tablePos = entry.getKey().tablePos;
			if (!checkedTables.add(tablePos)
				|| !(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table))
				continue;
			for (SurgicalLimbJoint joint : table.limbJoints()) {
				LimbJointSelection candidate = limbJointSelection(tablePos, table, joint);
				if (candidate == null || candidate.contact == null)
					continue;
				Vec3 hit = intersectContact(ray, candidate.contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				best = candidate;
			}
		}
		return best != null && bestHit != null
			&& isOccluded(level, player, ray.start, bestHit, best.selection.tablePos) ? null : best;
	}

	@Nullable
	private static LimbJointSelection limbJointSelection(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalLimbJoint joint) {
		SurgicalSubject childSubject = table.getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parentSubject = table.getSubjectByPersistentId(joint.parent().subjectKey());
		if (childSubject == null || parentSubject == null)
			return null;
		TableGeometry childGeometry = TABLES.get(new SubjectKey(tablePos, childSubject.id()));
		TableGeometry parentGeometry = TABLES.get(new SubjectKey(tablePos, parentSubject.id()));
		if (childGeometry == null || parentGeometry == null || !childGeometry.topologyReady()
			|| !parentGeometry.topologyReady()
			|| !childSubject.matchesObservedTopology(childGeometry.observedCubeCount, childGeometry.seams)
			|| !parentSubject.matchesObservedTopology(parentGeometry.observedCubeCount, parentGeometry.seams)
			|| !childGeometry.presentCubes.get(joint.child().cubeId())
			|| !parentGeometry.presentCubes.get(joint.parent().cubeId()))
			return null;
		SurgicalModelRenderContext.CubeGeometry child = childGeometry.cubesById.get(joint.child().cubeId());
		SurgicalModelRenderContext.CubeGeometry parent = parentGeometry.cubesById.get(joint.parent().cubeId());
		if (child == null || parent == null)
			return null;

		SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
			SurgicalAssembly.Seam.of(0, 1), List.of(
				new SurgicalModelRenderContext.CubeGeometry(0, child.corners()),
				new SurgicalModelRenderContext.CubeGeometry(1, parent.corners())));
		List<SurgicalClientTopology.Edge> cubeEdges = new ArrayList<>(24);
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(child));
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(parent));
		Selection selection = new Selection(tablePos, childSubject.id(), joint.child().cubeId(),
			childGeometry.observedCubeCount, childGeometry.seams,
			contact == null ? List.of() : contact.edges(), List.copyOf(cubeEdges));
		return new LimbJointSelection(selection, joint, contact);
	}

	/**
	 * Highlights exactly what a joint would move: a honey combination behaves as one rigid part, so
	 * the whole combination lights up, while a loose cube highlights on its own.
	 */
	@Nullable
	private static Selection findLimbTargetSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		Selection selected;
		if (combination != null) {
			selected = combinationSelection(hit, table, combination);
		} else {
			SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
			if (cube == null)
				return null;
			selected = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
				hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
				List.copyOf(SurgicalClientTopology.cubeEdges(cube)));
		}
		return validSecondLimbTarget(selected, table) ? selected : null;
	}

	/** The first target stays highlighted, but a physically disconnected second target never does. */
	private static boolean validSecondLimbTarget(Selection selected,
		@Nullable SurgicalTableBlockEntity table) {
		PendingLimb first = pendingLimb;
		if (first == null)
			return true;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
			|| heldLimbType(minecraft.player.getItemInHand(first.hand())) != first.type())
			return true;
		return table != null && first.selection().tablePos().equals(selected.tablePos())
			&& table.canConnectLimbTargets(first.selection().subjectId(), first.selection().targetId(),
				selected.subjectId(), selected.targetId());
	}

	private record PendingLimb(SurgicalLimbType type, InteractionHand hand, Selection selection) {}

	private static void clearSelections() {
		seamSelection = null;
		cubeSelection = null;
		componentSelection = null;
		symmetrySelection = null;
		limbSelection = null;
		wrenchSelection = null;
		hoveredGluePoint = null;
		connectedSelectionCache = null;
		directSelectionCache = null;
		lastSelectionMode = Integer.MIN_VALUE;
		lastSelectionRay = null;
		lastSelectionPendingGlue = null;
		lastSelectionPendingSymmetry = null;
		lastSelectionPendingSymmetryReference = null;
		lastSelectionPendingLimb = null;
		GLUE_POINT_OUTLINE.clear();
		clearSeamHighlight();
		clearSymmetryPlaneHighlight();
	}

	private static final class TableGeometry {
		private final UUID persistentId;
		private int subjectId;
		private final MimicProfile profile;
		private final SurgicalLayPose layPose;
		private final double worldOriginX;
		private final double worldOriginZ;
		private final int observedCubeCount;
		private final List<SurgicalModelRenderContext.CubeGeometry> baseCubes;
		private final Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseCubesById;
		private List<SurgicalModelRenderContext.CubeGeometry> layoutCubes;
		private List<SurgicalModelRenderContext.CubeGeometry> cubes;
		private Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubesById;
		private List<CubeTarget> cubeTargets;
		@Nullable
		private AABB bounds;
		private List<SurgicalAssembly.Seam> seams;
		private Map<SurgicalAssembly.Seam, Integer> seamIds;
		private List<SurgicalClientTopology.Contact> baseContacts;
		private List<SurgicalClientTopology.Contact> contacts;
		private List<List<SurgicalClientTopology.Contact>> contactsByCube;
		@Nullable
		private Supplier<SurgicalClientTopology.ContactTopology> topologyBuild;
		@Nullable
		private CompletableFuture<SurgicalClientTopology.ContactTopology> pendingTopology;
		private boolean topologyAvailable;
		private BitSet presentCubes = new BitSet();
		private BitSet cutSeams = new BitSet();
		private Map<Integer, Vec3> serverOffsets = Map.of();
		private Map<Integer, Vec3> offsets = Map.of();
		private Map<Integer, SurgicalCubeRotation> serverRotations = Map.of();
		private Map<Integer, SurgicalCubeRotation> rotations = Map.of();
		private long cacheWeight;
		private Map<Integer, Vec3> pendingGroundingOffsets = Map.of();
		private Map<Integer, SurgicalCubeRotation> pendingGroundingRotations = Map.of();
		private BitSet pendingGroundingCutSeams = new BitSet();
		@Nullable
		private SurgicalGlueJoint pendingGroundingExcludedJoint;
		private boolean groundingPending;
		private long lastGroundingAttemptGeneration = Long.MIN_VALUE;
		/** Bumped by every {@code applyTransforms}, so derived outlines can tell when cubes moved. */
		private long transformGeneration;
		private int renderRevision = Integer.MIN_VALUE;
		private long lastSeenTick;
		@Nullable
		private SubjectKey ownerKey;
		private boolean ownerChanged;
		private long orphanedAtTick = Long.MIN_VALUE;
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
			this.seamIds = seamIds(this.seams);
			this.baseContacts = List.copyOf(contacts);
			this.contacts = this.baseContacts;
			this.contactsByCube = contactsByCube(observedCubeCount, this.contacts);
			this.cacheWeight = estimateCacheWeight(this.baseCubes, this.seams, this.baseContacts);
			this.topologyBuild = topologyBuild;
			this.pendingTopology = pendingTopology;
			this.topologyAvailable = topologyBuild == null;
			this.lastSeenTick = lastSeenTick;
		}

		private boolean matchesModel(SurgicalTableBlockEntity table, SurgicalSubject subject) {
			return persistentId.equals(subject.persistentId()) && profile.equals(subject.profile())
				&& subject.layPose().equals(layPose)
				&& Double.doubleToLongBits(worldOriginX(table, subject)) == Double.doubleToLongBits(worldOriginX)
				&& Double.doubleToLongBits(worldOriginZ(table, subject)) == Double.doubleToLongBits(worldOriginZ)
				&& (subject.cubeCount() == 0 || subject.cubeCount() == observedCubeCount);
		}

		private boolean refresh(SurgicalTableBlockEntity table, SurgicalSubject subject) {
			markSeen(table);
			boolean topologyChanged = resolvePendingTopology();
			int revision = subject.clientRenderRevision();
			boolean stateChanged = subjectState == null
				|| !subjectState.matches(subject, observedCubeCount);
			OwnerHandoffKey handoff = new OwnerHandoffKey(table.getBlockPos(), table.clientDataRevision());
			if (!ownerChanged && revision == renderRevision && !topologyChanged && !stateChanged)
				return false;
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
				seamIds = seamIds(updatedSeams);
				if (observedCubeCount > ASYNC_TOPOLOGY_CUBE_THRESHOLD) {
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
			presentCubes = subject.presentCubesForRender(observedCubeCount);
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
		}

		private void orphan(long now) {
			ownerKey = null;
			ownerChanged = true;
			orphanedAtTick = now;
		}

		private void applyPreview(SurgicalTableBlockEntity table, Map<Integer, Vec3> previewOffsets,
			BitSet previewCutSeams) {
			applyTransforms(table, previewOffsets, serverRotations, previewCutSeams, null);
		}

		private void applyPreview(SurgicalTableBlockEntity table, Map<Integer, Vec3> previewOffsets,
			BitSet previewCutSeams, SurgicalGlueJoint excludedJoint) {
			applyTransforms(table, previewOffsets, serverRotations, previewCutSeams, excludedJoint);
		}

		private void clearPreview(SurgicalTableBlockEntity table) {
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
			double surfaceY = table.getBlockPos().getY() + 1.0d + SurgicalTablePoseResolver.TABLE_CLEARANCE;
			SurgicalSubject subject = table.getSubject(subjectId);
			layoutCubes = SurgicalTableClientHandler.transformCubes(baseCubes, appliedRotations, Map.of());
			// A subject with no glue joint and no combination is its own grounding component, so walking
			// every subject on the table to build a multi-body connection graph cannot change its answer.
			// Unifying glue and native seams dropped this guard, which put the full graph build on every
			// transform of every subject.
			boolean linked = subject != null && subject.linkedToOtherSubjects();
			Map<Integer, Vec3> grounded = subject == null || !linked ? null
				: groundConnectedComponents(table, subject, this, appliedOffsets,
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
			cubes = SurgicalTableClientHandler.transformCubes(baseCubes, rotations, offsets);
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
		private void reportBounds(SurgicalTableBlockEntity table) {
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
			if (!groundingPending || lastGroundingAttemptGeneration == geometryGeneration)
				return false;
			lastGroundingAttemptGeneration = geometryGeneration;
			applyTransforms(table, pendingGroundingOffsets, pendingGroundingRotations,
				pendingGroundingCutSeams, pendingGroundingExcludedJoint);
			return !groundingPending;
		}

		private static List<CubeTarget> cubeTargets(
			List<SurgicalModelRenderContext.CubeGeometry> cubes) {
			List<CubeTarget> targets = new ArrayList<>(cubes.size());
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
					targets.add(new CubeTarget(cube, new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
			}
			return List.copyOf(targets);
		}

		@Nullable
		private static AABB boundsFor(List<CubeTarget> targets) {
			AABB result = null;
			for (CubeTarget target : targets)
				result = result == null ? target.bounds : result.minmax(target.bounds);
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
			seamIds = seamIds(seams);
			baseContacts = topology.contacts();
			cacheWeight = estimateCacheWeight(baseCubes, seams, baseContacts);
			topologyAvailable = true;
			return true;
		}

		private boolean topologyReady() {
			return pendingTopology == null && topologyAvailable;
		}

		private void markSeen(SurgicalTableBlockEntity table) {
			if (table.getLevel() != null)
				lastSeenTick = table.getLevel().getGameTime();
		}

		private long cacheWeight() {
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

		private void dispose() {
			topologyBuild = null;
			if (pendingTopology != null) {
				pendingTopology.cancel(false);
				pendingTopology = null;
			}
		}

		private List<SurgicalClientTopology.Contact> contactsFor(int cubeId) {
			return cubeId >= 0 && cubeId < contactsByCube.size() ? contactsByCube.get(cubeId) : List.of();
		}

		private List<SurgicalClientTopology.Edge> cubeEdges(SurgicalAssembly.Seam seam) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(24);
			SurgicalModelRenderContext.CubeGeometry first = cubesById.get(seam.first());
			SurgicalModelRenderContext.CubeGeometry second = cubesById.get(seam.second());
			if (first != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(first));
			if (second != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(second));
			return List.copyOf(edges);
		}

		private List<SurgicalClientTopology.Edge> componentCubeEdges(BitSet component) {
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

		private static List<SurgicalClientTopology.Contact> transformContacts(
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
				Vec3 center = cubeCenter(base);
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

		private static List<List<SurgicalClientTopology.Contact>> contactsByCube(int cubeCount,
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

	/** Keeps one joined edge mesh per colour and only rewrites it when the selection changes. */
	private static final class OutlineState {
		private final Object slot = new Object();
		private final SurgicalEdgeOutline outline = new SurgicalEdgeOutline();
		private final float lineWidth;
		private List<SurgicalClientTopology.Edge> edges = List.of();
		private int color = -1;
		private long lastRefreshTick = Long.MIN_VALUE;

		private OutlineState() {
			this(HIGHLIGHT_LINE_WIDTH);
		}

		private OutlineState(float lineWidth) {
			this.lineWidth = lineWidth;
		}

		private void show(List<SurgicalClientTopology.Edge> nextEdges, int color) {
			if (nextEdges.isEmpty()) {
				clear();
				return;
			}
			ClientLevel level = Minecraft.getInstance().level;
			long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
			// Reference identity first: these lists are immutable and the unchanged case hands back
			// the very same instance, so the element-wise compare below - two Vec3 per edge, hundreds
			// of edges, three outlines, every frame - is only reached when something actually moved.
			if ((edges == nextEdges || edges.equals(nextEdges)) && this.color == color) {
				if (tick != lastRefreshTick) {
					Outliner.getInstance().keep(slot);
					lastRefreshTick = tick;
				}
				return;
			}

			outline.setEdges(nextEdges);
			Outliner.getInstance().showOutline(slot, outline)
				.lineWidth(lineWidth)
				.disableLineNormals()
				.colored(color);
			edges = List.copyOf(nextEdges);
			this.color = color;
			lastRefreshTick = tick;
		}

		private void clear() {
			if (!edges.isEmpty())
				Outliner.getInstance().remove(slot);
			edges = List.of();
			color = -1;
			lastRefreshTick = Long.MIN_VALUE;
		}
	}

	private record SubjectKey(BlockPos tablePos, int subjectId) {
		private SubjectKey {
			tablePos = tablePos.immutable();
		}
	}

	/** Exact dynamic state whose equality makes an owner-only cache handoff safe. */
	private record SubjectGeometryState(BitSet presentCubes, List<SurgicalAssembly.Seam> seams,
		BitSet cutSeams, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations, Set<SurgicalGlueJoint> glueJoints,
		Map<UUID, List<SurgicalCombination.Member>> combinations) {

		private SubjectGeometryState {
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
			return presentCubes.equals(subject.presentCubesForRender(observedCubeCount))
				&& cutSeams.equals(subject.cutSeamsForRender());
		}

		private static SubjectGeometryState capture(SurgicalSubject subject, int observedCubeCount) {
			Map<UUID, List<SurgicalCombination.Member>> combinations = new HashMap<>();
			for (SurgicalCombination combination : subject.combinations())
				combinations.put(combination.id(), List.copyOf(combination.members()));
			return new SubjectGeometryState(subject.presentCubesForRender(observedCubeCount), subject.seams(),
				subject.cutSeamsForRender(), subject.componentOffsetsForRender(),
				subject.componentRotationsForRender(), Set.copyOf(subject.glueJoints()), combinations);
		}
	}

	private record OwnerHandoffKey(BlockPos tablePos, int tableRevision) {
		private OwnerHandoffKey {
			tablePos = tablePos.immutable();
		}
	}
	private record PackedBodyMetrics(SurgicalAssembly.BodyBounds bodyBounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {}
	private record PackedBodyMeasurement(List<SlimeBionicAnimator.SourceState> sources,
		SurgicalBodyBounds.Envelope visible) {}

	private record SelectionHighlightEdges(List<SurgicalClientTopology.Edge> cubeEdges,
		List<SurgicalClientTopology.Edge> combinationEdges,
		List<SurgicalClientTopology.Edge> glueJointEdges,
		List<SurgicalClientTopology.Edge> limbJointEdges) {
		private SelectionHighlightEdges {
			cubeEdges = List.copyOf(cubeEdges);
			combinationEdges = List.copyOf(combinationEdges);
			glueJointEdges = List.copyOf(glueJointEdges);
			limbJointEdges = List.copyOf(limbJointEdges);
		}
	}

	private record Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
		List<SurgicalClientTopology.Edge> glueJointEdges,
		List<SurgicalClientTopology.Edge> cubeEdges,
		List<SurgicalClientTopology.Edge> combinationEdges,
		List<SurgicalClientTopology.Edge> limbJointEdges, boolean glueJoint, boolean combination) {
		private Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges,
			List<SurgicalClientTopology.Edge> combinationEdges,
			List<SurgicalClientTopology.Edge> limbJointEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				combinationEdges, limbJointEdges, glueJoint, combination);
		}

		private Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges,
			List<SurgicalClientTopology.Edge> combinationEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				combinationEdges, List.of(), glueJoint, combination);
		}

		private Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				List.of(), List.of(), false, false);
		}

		private Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges, boolean glueJoint) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				List.of(), List.of(), glueJoint, false);
		}

		private Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(),
				combination ? List.of() : cubeEdges, combination ? cubeEdges : List.of(), List.of(),
				glueJoint, combination);
		}
	}

	private record GlueJointSelection(Selection selection,
		@Nullable SurgicalClientTopology.Contact contact) {}

	private record LimbJointSelection(Selection selection, SurgicalLimbJoint joint,
		@Nullable SurgicalClientTopology.Contact contact) {}

	private record Ray(Vec3 start, Vec3 end) {}

	private record CubeHit(BlockPos tablePos, TableGeometry geometry, int cubeId, int faceIndex, Vec3 location,
		@Nullable GluePoint gluePoint) {}

	private record GluePoint(Vec3 location, List<SurgicalClientTopology.Edge> markerEdges) {
		private GluePoint {
			markerEdges = List.copyOf(markerEdges);
		}
	}

	private record PixelInterval(double start, double end, double center) {}

	private record ModelPixelSpan(double start, double end) {
		private double length() {
			return Math.abs(end - start);
		}
	}

	private record ContactProjection(SurgicalGlueContact contact, double distance) {}

	private record GluePreviewCubeHit(GlueSubjectPreview subject,
		SurgicalModelRenderContext.CubeGeometry geometry, int cubeId, int faceIndex, Vec3 location) {}

	private record CubeTarget(SurgicalModelRenderContext.CubeGeometry geometry, AABB bounds) {}

	private record CubeSelectionCache(BlockPos tablePos, int subjectId, int cubeId, int tableRevision,
		int renderRevision, Selection selection, boolean limbJointsIncluded) {
		private CubeSelectionCache(BlockPos tablePos, int subjectId, int cubeId, int tableRevision,
			int renderRevision, Selection selection) {
			this(tablePos, subjectId, cubeId, tableRevision, renderRevision, selection, false);
		}

		private boolean matches(CubeHit hit, int revision) {
			return matches(hit, revision, false);
		}

		private boolean matches(CubeHit hit, int revision, boolean includeLimbJoints) {
			return tablePos.equals(hit.tablePos) && subjectId == hit.geometry.subjectId && cubeId == hit.cubeId
				&& tableRevision == revision && renderRevision == hit.geometry.renderRevision
				&& limbJointsIncluded == includeLimbJoints;
		}
	}


	private record PlacementCandidate(ItemStack box, @Nullable PlacementSource source,
		SurgicalTablePlacementResult result) {
		private PlacementCandidate {
			box = box.copyWithCount(1);
		}
	}

	private record PlacementPreview(BlockPos ownerPos, InteractionHand hand, PlacementSource source,
		Direction facing, SurgicalLayPose layPose, SurgicalClientTopology.PlacementPlan plan,
		List<SourcePlacementGeometry> sourceGeometries, DiscoveredPlacement discovered,
		List<SurgicalTableLayout.Proposal> sourceLayouts, boolean projectSourceGeometry,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ, int tableRevision) {
		private PlacementPreview {
			sourceGeometries = List.copyOf(sourceGeometries);
			sourceLayouts = List.copyOf(sourceLayouts);
		}
	}

	private record PlacementSuppression(InteractionHand hand, ItemStack sourceBox, long expiresAtTick) {
		private PlacementSuppression {
			sourceBox = sourceBox.copy();
		}
	}

	private record PlacementGeometry(AABB bounds, SurgicalLayPose layPose,
		Map<Integer, Vec3> cubeOffsets, List<SourcePlacementGeometry> sources,
		int discoveredCubeCount, List<SurgicalAssembly.Seam> discoveredSeams,
		List<SurgicalModelRenderContext.CubeGeometry> discoveredCubes) {
		private PlacementGeometry {
			cubeOffsets = Map.copyOf(cubeOffsets);
			sources = List.copyOf(sources);
			discoveredSeams = List.copyOf(discoveredSeams);
			discoveredCubes = List.copyOf(discoveredCubes);
		}
	}

	private record SourcePlacementGeometry(SurgicalAssembly.PlacedSource placedSource,
		List<SurgicalModelRenderContext.CubeGeometry> baseCubes, Map<Integer, Vec3> renderOffsets,
		Map<Integer, Vec3> previewOffsets,
		Map<Integer, SurgicalCubeRotation> renderRotations) {
		private SourcePlacementGeometry {
			baseCubes = List.copyOf(baseCubes);
			renderOffsets = Map.copyOf(renderOffsets);
			previewOffsets = Map.copyOf(previewOffsets);
			renderRotations = Map.copyOf(renderRotations);
		}
	}

	private static final class PlacementSource {
		private final ItemStack box;
		private final MimicProfile profile;
		@Nullable
		private final SurgicalAssembly assembly;
		@Nullable
		private LivingEntity preview;
		@Nullable
		private Direction measuredFacing;
		private boolean measuredSourceGeometry;
		@Nullable
		private PlacementGeometry measuredGeometry;

		private PlacementSource(ItemStack box, MimicProfile profile, @Nullable SurgicalAssembly assembly) {
			this.box = box;
			this.profile = profile;
			this.assembly = assembly;
		}

		@Nullable
		private LivingEntity preview() {
			if (preview == null)
				preview = preview(profile);
			return preview;
		}

		@Nullable
		private LivingEntity preview(MimicProfile sourceProfile) {
			return SurgicalSourceModelRenderer.preview(sourceProfile);
		}

		private boolean isComposite() {
			return assembly != null && (assembly.preservesLayout() || assembly.sources().size() != 1);
		}

		private int subjectSlots() {
			return assembly != null && isComposite() ? assembly.sources().size() : 1;
		}

		@Nullable
		private PlacementGeometry measure(Direction facing, boolean projectSourceGeometry) {
			if (measuredGeometry != null && measuredFacing == facing
				&& measuredSourceGeometry == projectSourceGeometry)
				return measuredGeometry;
			if (isComposite()) {
				measuredGeometry = measureComposite(facing, projectSourceGeometry);
				measuredFacing = facing;
				measuredSourceGeometry = projectSourceGeometry;
				return measuredGeometry;
			}
			LivingEntity entity = preview();
			if (entity == null)
				return null;

			PoseStack poseStack = new PoseStack();
			SurgicalTablePoseResolver.SurgicalPose resolved =
				SurgicalTablePoseResolver.resolve(this, profile, entity, facing);
			resolved.apply(poseStack);
			EntityGeometry.Collector discarded = EntityGeometry.Collector.boundsOnly();
			MultiBufferSource discardedBuffer = renderType -> discarded;
			SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(entity,
				cubeCount(), presentCubes(), Map.of(), poseStack, discardedBuffer, LightTexture.FULL_BRIGHT,
				0.0f, 0.0f, true, null, projectSourceGeometry);
			if (snapshot.cubes().isEmpty())
				return null;
			int discoveredCubeCount = 0;
			List<SurgicalAssembly.Seam> discoveredSeams = List.of();
			List<SurgicalModelRenderContext.CubeGeometry> discoveredCubes = List.of();
			if (assembly == null) {
				discoveredCubeCount = snapshot.observedCubeCount();
				if (!SurgicalAssembly.validCubeCount(discoveredCubeCount)
					|| snapshot.cubes().size() != discoveredCubeCount)
					return null;
				SurgicalClientTopology.ContactTopology topology = SurgicalClientTopology.buildContactTopology(
					discoveredCubeCount, snapshot.cubes());
				discoveredSeams = topology.seams();
				discoveredCubes = snapshot.cubes();
			}

			Map<Integer, Vec3> cubeOffsets = groundedOffsets(snapshot);
			AABB bounds = logicalBounds(snapshot.cubes(), cubeOffsets);
			if (bounds == null)
				return null;
			measuredFacing = facing;
			measuredSourceGeometry = projectSourceGeometry;
			measuredGeometry = new PlacementGeometry(bounds, resolved.layPose(), cubeOffsets, List.of(),
				discoveredCubeCount, discoveredSeams, discoveredCubes);
			return measuredGeometry;
		}

		@Nullable
		private PlacementGeometry measureComposite(Direction facing, boolean projectSourceGeometry) {
			if (assembly == null)
				return null;
			EntityGeometry.Collector discarded = EntityGeometry.Collector.boundsOnly();
			MultiBufferSource discardedBuffer = renderType -> discarded;
			List<SourcePlacementGeometry> geometries = new ArrayList<>(assembly.sources().size());
			for (SurgicalAssembly.PlacedSource placedSource : assembly.placedSources(facing)) {
				SurgicalAssembly.Source source = placedSource.source();
				LivingEntity entity = preview(source.profile());
				if (entity == null)
					return null;
				PoseStack poseStack = sourcePose(placedSource, entity);
				SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(entity,
					source.cubeCount(), source.presentCubes(), Map.of(), placedSource.cubeRotations(),
					poseStack, discardedBuffer,
					LightTexture.FULL_BRIGHT, 0.0f, 0.0f, true, null, projectSourceGeometry);
				if (snapshot.observedCubeCount() != source.cubeCount()
					|| snapshot.cubes().size() != source.presentCubes().cardinality())
					return null;
				// Preserve the packed relative offsets for validation and restoration. A separate
				// shared grounding pass below applies the same group-wide Y correction used after the
				// sources become table subjects.
				Map<Integer, Vec3> renderOffsets = placedSource.cubeOffsets();
				geometries.add(new SourcePlacementGeometry(placedSource, snapshot.cubes(), renderOffsets,
					renderOffsets, placedSource.cubeRotations()));
				discarded.reset();
			}
			List<SurgicalClientTopology.GroundingBody<Integer>> bodies = new ArrayList<>(geometries.size());
			for (int sourceId = 0; sourceId < geometries.size(); sourceId++) {
				SourcePlacementGeometry geometry = geometries.get(sourceId);
				SurgicalAssembly.Source source = geometry.placedSource().source();
				bodies.add(new SurgicalClientTopology.GroundingBody<>(sourceId, source.cubeCount(),
					source.presentCubes(), source.seams(), source.cutSeams(), geometry.baseCubes(),
					geometry.renderOffsets()));
			}
			List<SurgicalClientTopology.GroundingLink<Integer>> links = new ArrayList<>(assembly.joints().stream()
				.map(joint -> new SurgicalClientTopology.GroundingLink<>(joint.firstSource(), joint.firstCube(),
					joint.secondSource(), joint.secondCube()))
				.toList());
			for (SurgicalAssembly.Combination combination : assembly.combinations()) {
				SurgicalAssembly.CombinationMember anchor = combination.members().getFirst();
				for (SurgicalAssembly.CombinationMember member : combination.members().subList(1,
					combination.members().size()))
					links.add(new SurgicalClientTopology.GroundingLink<>(anchor.source(), anchor.cube(),
						member.source(), member.cube()));
			}
			Map<Integer, Map<Integer, Vec3>> grounded = SurgicalClientTopology.groundConnectedBodies(
				bodies, links, 1.0d + SurgicalTablePoseResolver.TABLE_CLEARANCE);
			if (grounded == null || grounded.size() != geometries.size())
				return null;
			List<SourcePlacementGeometry> previewGeometries = new ArrayList<>(geometries.size());
			for (int sourceId = 0; sourceId < geometries.size(); sourceId++) {
				SourcePlacementGeometry geometry = geometries.get(sourceId);
				Map<Integer, Vec3> previewOffsets = grounded.get(sourceId);
				if (previewOffsets == null)
					return null;
				previewGeometries.add(new SourcePlacementGeometry(geometry.placedSource(), geometry.baseCubes(),
					geometry.renderOffsets(), previewOffsets, geometry.renderRotations()));
			}
			AABB bounds = null;
			for (SourcePlacementGeometry geometry : previewGeometries) {
				AABB sourceBounds = logicalBounds(geometry.baseCubes(), geometry.previewOffsets());
				if (sourceBounds == null)
					return null;
				bounds = bounds == null ? sourceBounds : bounds.minmax(sourceBounds);
			}
			if (bounds == null)
				return null;
			return new PlacementGeometry(bounds, assembly.placedLayPose(facing), Map.of(),
				previewGeometries, 0, List.of(), List.of());
		}

		private PoseStack sourcePose(SurgicalAssembly.PlacedSource placedSource, LivingEntity entity) {
			SurgicalAssembly.Source source = placedSource.source();
			PoseStack poseStack = new PoseStack();
			poseStack.translate(placedSource.originOffset().x, placedSource.originOffset().y,
				placedSource.originOffset().z);
			SurgicalTablePoseResolver.resolve(placedSource.layPose()).apply(poseStack);
			return poseStack;
		}

		private Map<Integer, Vec3> groundedOffsets(SurgicalModelRenderContext.Snapshot snapshot) {
			if (assembly == null || snapshot.observedCubeCount() != assembly.cubeCount())
				return Map.of();
			return SurgicalClientTopology.groundComponents(assembly.cubeCount(), assembly.presentCubes(),
				assembly.seams(), assembly.cutSeams(), snapshot.cubes(), Map.of(),
				1.0d + SurgicalTablePoseResolver.TABLE_CLEARANCE);
		}

		private int cubeCount() {
			return assembly == null ? 0 : assembly.cubeCount();
		}

		private BitSet presentCubes() {
			return assembly == null ? new BitSet() : assembly.presentCubes();
		}
	}

	private static final class PendingVisualCommit {
		private final BlockPos tablePos;
		private final int tableRevision;
		private final long expiresAtTick;
		private final List<Integer> geometrySubjectIds;
		private final List<Integer> placementBaselineSubjectIds;
		private final boolean retainPlacementPreview;
		private final boolean retainGluePreview;
		private boolean serverAcknowledged;

		private PendingVisualCommit(BlockPos tablePos, int tableRevision, long expiresAtTick,
			List<Integer> geometrySubjectIds, boolean retainPlacementPreview, boolean retainGluePreview) {
			this.tablePos = tablePos.immutable();
			this.tableRevision = tableRevision;
			this.expiresAtTick = expiresAtTick;
			this.geometrySubjectIds = List.copyOf(geometrySubjectIds);
			Minecraft minecraft = Minecraft.getInstance();
			this.placementBaselineSubjectIds = retainPlacementPreview && minecraft.level != null
				&& minecraft.level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table
				? table.getSubjects().stream().map(SurgicalSubject::id).toList() : List.of();
			this.retainPlacementPreview = retainPlacementPreview;
			this.retainGluePreview = retainGluePreview;
		}
	}

	private static final class PendingCut {
		private final BlockPos tablePos;
		private final int subjectId;
		private final InteractionHand hand;
		private final int targetId;
		private final int observedCubeCount;
		private final List<SurgicalAssembly.Seam> seams;
		private final BitSet proposedCuts;
		private final Map<Integer, BitSet> movingComponents;
		@Nullable
		private SurgicalClientTopology.ConnectedPlacement planned;
		@Nullable
		private SurgicalTableLayout.Proposal layout;
		private double lastTargetX = Double.NaN;
		private double lastTargetZ = Double.NaN;
		private int plannedTableRevision = Integer.MIN_VALUE;

		private PendingCut(Selection selection, InteractionHand hand, BitSet proposedCuts,
			Map<Integer, BitSet> movingComponents,
			@Nullable SurgicalClientTopology.ConnectedPlacement planned) {
			tablePos = selection.tablePos;
			subjectId = selection.subjectId;
			this.hand = hand;
			targetId = selection.targetId;
			observedCubeCount = selection.observedCubeCount;
			seams = List.copyOf(selection.seams);
			this.proposedCuts = (BitSet) proposedCuts.clone();
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			this.movingComponents = Map.copyOf(frozen);
			this.planned = planned;
		}
	}

	private static final class PendingGlueCut {
		private final BlockPos tablePos;
		private final int subjectId;
		private final InteractionHand hand;
		private final int targetId;
		private final int observedCubeCount;
		private final List<SurgicalAssembly.Seam> seams;
		private final SurgicalGlueJoint joint;
		private final Map<Integer, BitSet> movingComponents;
		@Nullable
		private SurgicalClientTopology.ConnectedPlacement planned;
		private double lastTargetX = Double.NaN;
		private double lastTargetZ = Double.NaN;
		private int plannedTableRevision = Integer.MIN_VALUE;

		private PendingGlueCut(Selection selection, InteractionHand hand, SurgicalGlueJoint joint,
			Map<Integer, BitSet> movingComponents,
			@Nullable SurgicalClientTopology.ConnectedPlacement planned) {
			tablePos = selection.tablePos;
			subjectId = selection.subjectId;
			this.hand = hand;
			targetId = selection.targetId;
			observedCubeCount = selection.observedCubeCount;
			seams = List.copyOf(selection.seams);
			this.joint = joint;
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			this.movingComponents = Map.copyOf(frozen);
			this.planned = planned;
		}
	}

	private record FootprintGroups(List<SurgicalTableLayout.Footprint> moving,
		List<SurgicalTableLayout.Footprint> occupied) {}

	private record BatchCutLayout(SurgicalClientTopology.PlannedLayout layout, List<Vec3> groupDeltas,
		List<Map<Integer, BitSet>> groups, BitSet proposedCuts) {
		private BatchCutLayout {
			groupDeltas = List.copyOf(groupDeltas);
			List<Map<Integer, BitSet>> frozenGroups = new ArrayList<>(groups.size());
			for (Map<Integer, BitSet> group : groups) {
				Map<Integer, BitSet> frozenGroup = new HashMap<>();
				group.forEach((subjectId, cubes) -> frozenGroup.put(subjectId, (BitSet) cubes.clone()));
				frozenGroups.add(Map.copyOf(frozenGroup));
			}
			groups = List.copyOf(frozenGroups);
			proposedCuts = (BitSet) proposedCuts.clone();
		}
	}

	private record DiscoveredPlacement(int cubeCount, List<SurgicalAssembly.Seam> seams,
		List<SurgicalTableLayout.Footprint> footprints) {
		private static final DiscoveredPlacement EMPTY = new DiscoveredPlacement(0, List.of(), List.of());

		private DiscoveredPlacement {
			seams = List.copyOf(seams);
			footprints = List.copyOf(footprints);
		}
	}

	private record BatchCutFootprints(List<List<SurgicalTableLayout.Footprint>> groups,
		List<SurgicalTableLayout.Footprint> occupied) {
		private BatchCutFootprints {
			groups = groups.stream().map(List::copyOf).toList();
			occupied = List.copyOf(occupied);
		}
	}

	public record InteractionPrompt(ItemStack icon, List<Component> tooltip) {
		public InteractionPrompt {
			icon = icon.copy();
			tooltip = List.copyOf(tooltip);
		}
	}

	/** Ephemeral client-only state; server sync can acknowledge it but can never recreate it. */
	private static final class BatchCutAnimation {
		private final BlockPos tablePos;
		private final int tableRevision;
		private final long expiresAtTick;
		private final int editedSubjectId;
		private final BitSet proposedCuts;
		private final Map<Integer, BatchCutMotion> motions;
		private boolean acknowledged;
		private int acknowledgedRevision = Integer.MIN_VALUE;
		private float startedAt = Float.NaN;

		private BatchCutAnimation(BlockPos tablePos, int tableRevision, long expiresAtTick,
			int editedSubjectId, BitSet proposedCuts, Map<Integer, BatchCutMotion> motions) {
			this.tablePos = tablePos.immutable();
			this.tableRevision = tableRevision;
			this.expiresAtTick = expiresAtTick;
			this.editedSubjectId = editedSubjectId;
			this.proposedCuts = (BitSet) proposedCuts.clone();
			this.motions = Map.copyOf(motions);
		}
	}

	private static final class BatchCutMotion {
		private final BitSet movedCubes;
		private final Map<Integer, Vec3> startRenderOffsets;
		private final Map<Integer, Vec3> targetServerOffsets;
		@Nullable
		private final AABB startBounds;

		private BatchCutMotion(BitSet movedCubes, Map<Integer, Vec3> startRenderOffsets,
			Map<Integer, Vec3> targetServerOffsets, @Nullable AABB startBounds) {
			this.movedCubes = (BitSet) movedCubes.clone();
			this.startRenderOffsets = Map.copyOf(startRenderOffsets);
			this.targetServerOffsets = Map.copyOf(targetServerOffsets);
			this.startBounds = startBounds;
		}

		private Map<Integer, Vec3> interpolate(Map<Integer, Vec3> logicalOffsets, double progress) {
			Map<Integer, Vec3> rendered = new HashMap<>(logicalOffsets);
			for (int cube = movedCubes.nextSetBit(0); cube >= 0; cube = movedCubes.nextSetBit(cube + 1)) {
				Vec3 start = startRenderOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 end = logicalOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 offset = start.lerp(end, progress);
				if (offset.lengthSqr() <= 1.0e-24d)
					rendered.remove(cube);
				else
					rendered.put(cube, offset);
			}
			return Map.copyOf(rendered);
		}
	}

	private record PendingGlue(Selection selection, Vec3 hit, InteractionHand hand, int faceIndex,
		GluePoint point) {}

	private record SymmetryReference(Selection honeySelection, Selection singleCubeSelection,
		CubeHit referenceHit, SurgicalGlueJoint.Endpoint referenceAnchor,
		@Nullable SurgicalCombination combination, AABB honeyBounds, AABB singleCubeBounds,
		List<SymmetryPlaneCandidate> honeyPlanes, List<SymmetryPlaneCandidate> singleCubePlanes,
		@Nullable SurgicalGlueTransform recordedReplay,
		Vec3 originalAnchorContact, int tableRevision) {
		private SymmetryReference {
			honeyPlanes = List.copyOf(honeyPlanes);
			singleCubePlanes = List.copyOf(singleCubePlanes);
		}

		private boolean singleCube(boolean controlDown) {
			return controlDown || combination == null;
		}

		private Selection selection(boolean singleCube) {
			return singleCube ? singleCubeSelection : honeySelection;
		}

		private AABB mirrorBounds(boolean singleCube) {
			return singleCube ? singleCubeBounds : honeyBounds;
		}

		private List<SymmetryPlaneCandidate> planes(boolean singleCube) {
			return singleCube ? singleCubePlanes : honeyPlanes;
		}
	}

	private record SymmetryPlaneCandidate(Direction.Axis axis, Vec3 center, Vec3 normal,
		List<SurgicalClientTopology.Edge> edges) {
		private SymmetryPlaneCandidate {
			edges = List.copyOf(edges);
		}
	}

	private record SymmetryTarget(Selection selection, CubeHit referenceHit, CubeHit mirroredAnchorHit,
		SurgicalGlueJoint.Endpoint referenceAnchor, boolean singleCube,
		SurgicalGlueTransform replayTransform) {}

	private record GluePlanningSubject(SurgicalSubject subject, BitSet cubes,
		List<SurgicalModelRenderContext.CubeGeometry> baseTarget,
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> desired,
		Map<Integer, SurgicalCubeRotation> targetRotations) {
		private GluePlanningSubject {
			cubes = (BitSet) cubes.clone();
			baseTarget = List.copyOf(baseTarget);
			desired = Map.copyOf(desired);
			targetRotations = Map.copyOf(targetRotations);
		}
	}

	private static final class GlueSubjectPreview {
		private final int subjectId;
		private final BitSet cubes;
		private final SurgicalLayPose pose;
		private final List<SurgicalModelRenderContext.CubeGeometry> baseCubes;
		private final Map<Integer, Vec3> offsets;
		private final Map<Integer, SurgicalCubeRotation> rotations;
		private final boolean editable;
		private final Map<Integer, SurgicalModelRenderContext.CubeGeometry> transformedCubes;

		private GlueSubjectPreview(int subjectId, BitSet cubes, SurgicalLayPose pose,
			List<SurgicalModelRenderContext.CubeGeometry> baseCubes, Map<Integer, Vec3> offsets,
			Map<Integer, SurgicalCubeRotation> rotations, boolean editable) {
			this.subjectId = subjectId;
			this.cubes = (BitSet) cubes.clone();
			this.pose = pose;
			this.baseCubes = List.copyOf(baseCubes);
			this.offsets = Map.copyOf(offsets);
			this.rotations = Map.copyOf(rotations);
			this.editable = editable;
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> transformed = new HashMap<>();
			for (int cube = this.cubes.nextSetBit(0); cube >= 0; cube = this.cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry geometry = transformedPreviewCube(
					this.baseCubes, this.offsets, this.rotations, cube);
				if (geometry != null)
					transformed.put(cube, geometry);
			}
			this.transformedCubes = Map.copyOf(transformed);
		}
	}

	private record GluePreview(PendingGlue request, BlockPos ownerPos, int targetSubjectId, int targetCubeId,
		Vec3 targetHit, int tableRevision, SurgicalLayPose targetPose,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> obstacles,
		List<GlueSubjectPreview> subjects, List<SurgicalTableGluePacket.Move> moves,
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves) {
		private GluePreview {
			obstacles = List.copyOf(obstacles);
			subjects = List.copyOf(subjects);
			moves = List.copyOf(moves);
			anchorMoves = List.copyOf(anchorMoves);
		}

		private boolean matches(PendingGlue pending, CubeHit hit, int revision) {
			return request == pending && ownerPos.equals(hit.tablePos)
				&& targetSubjectId == hit.geometry.subjectId && targetCubeId == hit.cubeId
				&& tableRevision == revision && targetHit.distanceToSqr(hit.location) <= 1.0e-14d;
		}
	}

	private static final class GlueEditor {
		private final InteractionHand hand;
		private final SurgicalTableGluePacket.Endpoint first;
		private final SurgicalTableGluePacket.Endpoint second;
		private Vec3 axis;
		private double guideRadius;
		private GluePreview preview;
		private Vec3 axisCenter;
		private Vec3 faceCenter;
		private SurgicalGlueTransform replayTransform = SurgicalGlueTransform.IDENTITY;

		private GlueEditor(InteractionHand hand, SurgicalTableGluePacket.Endpoint first,
			SurgicalTableGluePacket.Endpoint second, GluePreview preview, Vec3 axisCenter,
			Vec3 faceCenter, Vec3 axis, double guideRadius) {
			this.hand = hand;
			this.first = first;
			this.second = second;
			this.preview = preview;
			this.axisCenter = axisCenter;
			this.faceCenter = faceCenter;
			this.axis = axis.normalize();
			this.guideRadius = guideRadius;
		}
	}
}
