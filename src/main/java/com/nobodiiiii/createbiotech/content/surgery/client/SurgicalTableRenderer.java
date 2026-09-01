package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.simibubi.create.foundation.mixin.accessor.LevelRendererAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SurgicalTableRenderer implements BlockEntityRenderer<SurgicalTableBlockEntity> {
	private static final BitSet EMPTY_CUBES = new BitSet();
	private static final int MAX_COLD_SUBJECTS_PER_FRAME = 4;
	private static final long COLD_BUILD_BUDGET_NANOS = 2_000_000L;
	private static int coldSubjectsThisFrame;
	private static long coldBuildStarted;

	public SurgicalTableRenderer(BlockEntityRendererProvider.Context context) {}

	static void beginFrame() {
		coldSubjectsThisFrame = 0;
		coldBuildStarted = 0L;
		SurgicalSourceModelRenderer.collectCompletedPlans();
	}

	@Override
	public AABB getRenderBoundingBox(SurgicalTableBlockEntity table) {
		return table.getRenderBoundingBox();
	}

	@Override
	public boolean shouldRenderOffScreen(SurgicalTableBlockEntity table) {
		// Keep the one subject-owning controller independent of its render section. A large table
		// can remain visible after the controller's section has left the frustum; NeoForge still
		// tests getRenderBoundingBox() before dispatching global block entities, and shouldRender()
		// retains the distance check, so this does not disable culling for the table contents.
		return table.hasSubjects();
	}

	@Override
	public boolean shouldRender(SurgicalTableBlockEntity table, Vec3 cameraPosition) {
		if (!table.hasSubjects())
			return false;
		AABB bounds = table.getRenderBoundingBox();
		double closestX = Math.max(bounds.minX, Math.min(cameraPosition.x, bounds.maxX));
		double closestY = Math.max(bounds.minY, Math.min(cameraPosition.y, bounds.maxY));
		double closestZ = Math.max(bounds.minZ, Math.min(cameraPosition.z, bounds.maxZ));
		double deltaX = cameraPosition.x - closestX;
		double deltaY = cameraPosition.y - closestY;
		double deltaZ = cameraPosition.z - closestZ;
		double viewDistance = getViewDistance();
		return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= viewDistance * viewDistance;
	}

	@Override
	public void render(SurgicalTableBlockEntity table, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		List<SurgicalSubject> subjects = table.getSubjects();
		if (subjects.isEmpty())
			return;
		boolean projectSourceGeometry = projectsSourceGeometry(table);
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Frustum frustum = currentFrustum();
		List<RenderCandidate> visible = new java.util.ArrayList<>();
		Map<Integer, SurgicalSubject> subjectsById = new java.util.HashMap<>();
		for (SurgicalSubject subject : subjects) {
			subjectsById.put(subject.id(), subject);
			AABB bounds = subjectRenderBounds(table, subject);
			if (frustum == null || frustum.isVisible(bounds))
				visible.add(new RenderCandidate(subject, bounds, distanceToSqr(bounds, camera)));
		}
		if (visible.isEmpty())
			return;

		// The controller remains active through the whole connected table AABB. Only the contents are
		// narrowed here, and any visible linked subject pulls its entire logical grounding group into
		// the preparation queue. This keeps remote table tiles independent of the controller position
		// without exposing a half-refreshed glued body.
		Set<Integer> prepareIds = new LinkedHashSet<>();
		Map<Integer, Set<Integer>> preparationGroups = new java.util.HashMap<>();
		for (RenderCandidate candidate : visible) {
			SurgicalSubject subject = candidate.subject;
			Set<Integer> group;
			if (subject.linkedToOtherSubjects()) {
				Set<Integer> connected = new LinkedHashSet<>();
				connected.add(subject.id());
				connected.addAll(table.connectedSubjectIds(subject.id()));
				group = Set.copyOf(connected);
			} else {
				group = Set.of(subject.id());
			}
			preparationGroups.put(subject.id(), group);
			prepareIds.addAll(group);
		}
		List<RenderCandidate> preparation = new java.util.ArrayList<>(prepareIds.size());
		for (int subjectId : prepareIds) {
			SurgicalSubject subject = subjectsById.get(subjectId);
			if (subject == null)
				continue;
			AABB bounds = subjectRenderBounds(table, subject);
			preparation.add(new RenderCandidate(subject, bounds, distanceToSqr(bounds, camera)));
		}
		preparation.sort(Comparator.comparingDouble(RenderCandidate::distanceToCameraSqr));
		for (RenderCandidate candidate : preparation) {
			if (!prepareSubjectGeometry(table, candidate.subject, poseStack, camera))
				break;
		}
		SurgicalTableClientHandler.completePlacementHandoffIfReady(table);
		Map<Set<Integer>, Boolean> groupReadiness = new java.util.HashMap<>();
		for (RenderCandidate candidate : visible) {
			SurgicalSubject subject = candidate.subject;
			Set<Integer> group = preparationGroups.get(subject.id());
			Boolean ready = groupReadiness.get(group);
			if (ready == null) {
				ready = isPreparationGroupReady(table, group);
				groupReadiness.put(group, ready);
			}
			if (ready
				&& !SurgicalTableClientHandler.suppressForPlacementHandoff(table, subject))
				renderSubject(table, subject, poseStack, buffer, packedLight, projectSourceGeometry, camera);
		}
	}

	static boolean projectsSourceGeometry(SurgicalTableBlockEntity table) {
		return table.getLevel() != null && table.clientProjectsSourceGeometry();
	}

	/** Returns false only when this frame's main-thread cold-build budget is exhausted. */
	private static boolean prepareSubjectGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		PoseStack poseStack, Vec3 camera) {
		// Called unconditionally, exactly as before: needsGeometryUpdate() is what drives refresh(),
		// which polls pending async topology and reports this subject's render bounds. Gating it behind
		// plan state could shrink the table's accumulated bounds until the block entity culled itself
		// out of the very render pass that would have restored them.
		boolean geometryUpdate = SurgicalTableClientHandler.needsGeometryUpdate(table, subject);
		LivingEntity preview = SurgicalSourceModelRenderer.preview(subject.profile());
		if (preview == null)
			return true;
		SurgicalSourceModelRenderer.RenderPlanState planState =
			SurgicalSourceModelRenderer.renderPlanState(preview, 0.0f);
		if (planState == SurgicalSourceModelRenderer.RenderPlanState.PENDING)
			return true;
		// The plan LRU is independent of the geometry cache, so a subject whose geometry is still valid
		// can nonetheless have lost its plan to eviction. Resubmitting it here is what keeps
		// renderSubject() on the cached path instead of plan()'s synchronous capture, so the geometry
		// check may only skip preparation once the plan is known to be live.
		if (planState == SurgicalSourceModelRenderer.RenderPlanState.READY && !geometryUpdate)
			return true;
		if (!claimColdBuildSlot())
			return false;

		// BlockEntityRenderDispatcher has already established the table's world/camera transform on
		// this stack. Geometry and grounding operate in that coordinate space, so retain the exact
		// renderer base pose just as the former one-pass capture did.
		poseStack.pushPose();
		poseStack.translate(subject.originOffsetX(), 0.0d, subject.originOffsetZ());
		SurgicalTablePoseResolver.resolve(subject.layPose()).apply(poseStack);
		int storedCount = subject.cubeCount();
		BitSet present = storedCount > 0
			? SurgicalTableClientHandler.presentCubesFor(table, subject, storedCount) : EMPTY_CUBES;
		SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.captureGeometryDeferred(preview,
			storedCount, present, poseStack, 0.0f, 0.0f, camera);
		poseStack.popPose();
		if (snapshot != null)
			SurgicalTableClientHandler.updateGeometry(table, subject, snapshot);
		return true;
	}

	private static boolean isPreparationGroupReady(SurgicalTableBlockEntity table, Set<Integer> group) {
		for (int subjectId : group) {
			SurgicalSubject connected = table.getSubject(subjectId);
			if (connected == null || !SurgicalTableClientHandler.isRenderReady(table, connected))
				return false;
			// Geometry readiness and plan readiness are separate caches, and renderSubject() needs both.
			// A cached snapshot whose plan has been evicted is precisely the case that used to fall
			// through to the synchronous capture, so hold the group back one frame and let the
			// preparation pass above resubmit the plan asynchronously instead.
			LivingEntity preview = SurgicalSourceModelRenderer.preview(connected.profile());
			if (preview != null && SurgicalSourceModelRenderer.renderPlanState(preview, 0.0f)
				!= SurgicalSourceModelRenderer.RenderPlanState.READY)
				return false;
		}
		return true;
	}

	private static void renderSubject(SurgicalTableBlockEntity table, SurgicalSubject subject,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean projectSourceGeometry,
		Vec3 camera) {
		MimicProfile profile = subject.profile();
		LivingEntity preview = SurgicalSourceModelRenderer.preview(profile);
		if (preview == null)
			return;

		poseStack.pushPose();
		poseStack.translate(subject.originOffsetX(), 0.0d, subject.originOffsetZ());
		SurgicalTablePoseResolver.resolve(subject.layPose()).apply(poseStack);
		int storedCount = subject.cubeCount();
		BitSet present = storedCount > 0
			? SurgicalTableClientHandler.presentCubesFor(table, subject, storedCount) : EMPTY_CUBES;
		Map<Integer, Vec3> offsets = SurgicalTableClientHandler.offsetsFor(table, subject);
		Map<Integer, SurgicalCubeRotation> rotations = SurgicalTableClientHandler.rotationsFor(table, subject);
		// The immutable captured source plan is cached by MimicProfile. Lay pose, grounded Y,
		// cuts and component offsets remain dynamic table-space state and are applied here
		// exactly once, matching the pre-mesh-cache coordinate semantics.
		SurgicalSourceModelRenderer.render(preview, storedCount, present, offsets, rotations, poseStack, buffer,
			packedLight, 0.0f, 0.0f, false, camera, projectSourceGeometry);
		poseStack.popPose();
	}

	private static AABB subjectRenderBounds(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		AABB cached = SurgicalTableClientHandler.cachedRenderBounds(table, subject);
		if (cached != null)
			return cached.inflate(2.0d);
		double minX = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (var footprint : subject.occupiedFootprints()) {
			minX = Math.min(minX, footprint.minX());
			minZ = Math.min(minZ, footprint.minZ());
			maxX = Math.max(maxX, footprint.maxX());
			maxZ = Math.max(maxZ, footprint.maxZ());
		}
		if (!Double.isFinite(minX)) {
			minX = table.getBlockPos().getX() + subject.originOffsetX();
			minZ = table.getBlockPos().getZ() + subject.originOffsetZ();
			maxX = minX + 1.0d;
			maxZ = minZ + 1.0d;
		}
		// This is used only before exact source-model bounds exist. Keep the vertical range deliberately
		// conservative so a tall modded entity cannot disappear during its cold-plan build.
		double surfaceY = SurgicalTablePlane.surfaceY(table.getBlockPos().getY());
		return new AABB(minX, surfaceY - 16.0d, minZ, maxX, surfaceY + 32.0d, maxZ).inflate(0.5d);
	}

	private static double distanceToSqr(AABB bounds, Vec3 point) {
		double x = Math.max(bounds.minX, Math.min(point.x, bounds.maxX));
		double y = Math.max(bounds.minY, Math.min(point.y, bounds.maxY));
		double z = Math.max(bounds.minZ, Math.min(point.z, bounds.maxZ));
		return point.distanceToSqr(x, y, z);
	}

	private static boolean claimColdBuildSlot() {
		if (coldSubjectsThisFrame >= MAX_COLD_SUBJECTS_PER_FRAME)
			return false;
		long now = System.nanoTime();
		if (coldBuildStarted == 0L)
			coldBuildStarted = now;
		else if (now - coldBuildStarted >= COLD_BUILD_BUDGET_NANOS)
			return false;
		coldSubjectsThisFrame++;
		return true;
	}

	private static Frustum currentFrustum() {
		if (!(Minecraft.getInstance().levelRenderer instanceof LevelRendererAccessor accessor))
			return null;
		Frustum captured = accessor.create$getCapturedFrustum();
		return captured != null ? captured : accessor.create$getCullingFrustum();
	}

	private record RenderCandidate(SurgicalSubject subject, AABB bounds, double distanceToCameraSqr) {}
}
