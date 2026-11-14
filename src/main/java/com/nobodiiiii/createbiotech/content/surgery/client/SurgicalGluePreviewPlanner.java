package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueContact;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableGluePacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalWrenchRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalGluePreviewPlanner implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalGluePreviewPlanner {
	private SurgicalGluePreviewPlanner() {}


	static void commitGluePreview(ClientLevel level, GluePreview preview) {
		SurgicalVisualCommitCoordinator.beginVisualCommit(level, preview.ownerPos, preview.tableRevision, List.of(), false, true);
		SurgicalGlueController.pendingGlue = null;
		SurgicalGlueController.hoveredGluePoint = null;
		SurgicalGlueController.gluePreview = preview;
		SurgicalGlueController.glueEditor = null;
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
		SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
	}

	static void commitSymmetryPreview(ClientLevel level, GluePreview preview) {
		SurgicalVisualCommitCoordinator.beginVisualCommit(level, preview.ownerPos, preview.tableRevision, List.of(), false, true);
		SurgicalSymmetryController.pendingSymmetry = null;
		SurgicalSymmetryController.pendingSymmetryReference = null;
		SurgicalSymmetryController.symmetryPlaneSelection = null;
		SurgicalSelectionResolver.symmetrySelection = null;
		SurgicalGlueController.hoveredGluePoint = null;
		SurgicalGlueController.gluePreview = preview;
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
		SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
		SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
	}

	static void refreshGlueEditGuide(LocalPlayer player, ClientLevel level, SurgicalGlueController.GlueEditor editor) {
		if (!updateGlueEditAxis(player, level, editor)) {
			SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
			return;
		}
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.show(glueEditGuideEdges(editor, Screen.hasControlDown()), SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
	}

	static boolean updateGlueEditAxis(LocalPlayer player, ClientLevel level, SurgicalGlueController.GlueEditor editor) {
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
	static GluePreviewCubeHit findNearestGluePreviewHit(LocalPlayer player, ClientLevel level,
		SurgicalGlueController.GlueEditor editor) {
		GluePreview preview = editor.preview;
		SurgicalSelectionResolver.Ray ray = SurgicalSelectionResolver.playerRay(player);
		GluePreviewCubeHit best = null;
		double bestDistance = Double.MAX_VALUE;
		GlueSubjectPreview subject = previewSubject(preview.subjects,
			editor.first.subjectId(), editor.first.cubeId());
		SurgicalModelRenderContext.CubeGeometry cube = subject == null ? null
			: previewCube(subject, editor.first.cubeId());
		if (subject == null || cube == null || !SurgicalSelectionResolver.rayIntersectsBounds(ray, cubeBounds(cube), 1.0e-6d))
			return null;
		for (int faceIndex = 0; faceIndex < SurgicalClientTopology.CUBE_FACES.length; faceIndex++) {
			Vec3 location = SurgicalSelectionResolver.intersectQuad(ray.start(), ray.end(), cube,
				SurgicalClientTopology.CUBE_FACES[faceIndex]);
			if (location == null)
				continue;
			double distance = ray.start().distanceToSqr(location);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = new GluePreviewCubeHit(subject, cube, editor.first.cubeId(), faceIndex, location);
		}
		return best != null && SurgicalSelectionResolver.isOccluded(level, player, ray.start(), best.location, preview.ownerPos)
			? null : best;
	}

	static List<SurgicalClientTopology.Edge> glueEditGuideEdges(SurgicalGlueController.GlueEditor editor, boolean rotating) {
		return cubeEditGuideEdges(editor.axis, editor.axisCenter, editor.faceCenter, editor.guideRadius, rotating);
	}

	static List<SurgicalClientTopology.Edge> cubeEditGuideEdges(Vec3 axis, Vec3 center,
		Vec3 faceCenter, double radius, boolean rotating) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		Vec3 axisU = perpendicularAxis(axis);
		Vec3 axisV = axis.cross(axisU).normalize();
		if (!rotating) {
			double faceDistance = Math.max(0.0d, faceCenter.subtract(center).dot(axis));
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
		for (int segment = 1; segment <= SurgicalTableClientHandler.GLUE_EDIT_CIRCLE_SEGMENTS; segment++) {
			double angle = Math.PI * 2.0d * segment / SurgicalTableClientHandler.GLUE_EDIT_CIRCLE_SEGMENTS;
			Vec3 current = center.add(axisU.scale(Math.cos(angle) * radius))
				.add(axisV.scale(Math.sin(angle) * radius));
			edges.add(new SurgicalClientTopology.Edge(previous, current));
			previous = current;
		}
		return List.copyOf(edges);
	}

	static Vec3 perpendicularAxis(Vec3 axis) {
		Vec3 helper = Math.abs(axis.y) < 0.9d ? new Vec3(0.0d, 1.0d, 0.0d)
			: new Vec3(1.0d, 0.0d, 0.0d);
		return axis.cross(helper).normalize();
	}

	@Nullable
	static Vec3 mappedOriginalFaceNormal(@Nullable GluePreviewCubeHit hit) {
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
	static Vec3 cubeFaceAxis(@Nullable SurgicalModelRenderContext.CubeGeometry cube, int faceIndex) {
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
	static Vec3 cubeFaceCenter(@Nullable SurgicalModelRenderContext.CubeGeometry cube, int faceIndex) {
		if (cube == null || faceIndex < 0 || faceIndex >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		int[] face = SurgicalClientTopology.CUBE_FACES[faceIndex];
		List<Vec3> corners = cube.corners();
		return corners.get(face[0]).add(corners.get(face[1])).add(corners.get(face[2]))
			.add(corners.get(face[3])).scale(0.25d);
	}

	/** Encodes a world-space face point so it follows the cube through later table and packing rotations. */
	@Nullable
	static SurgicalGlueContact glueContact(@Nullable SurgicalModelRenderContext.CubeGeometry cube,
		int preferredFace, Vec3 point) {
		if (cube == null || point == null)
			return null;
		SurgicalSelectionResolver.ContactProjection best = null;
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
			if (best == null || distance < best.distance())
				best = new SurgicalSelectionResolver.ContactProjection(new SurgicalGlueContact(faceIndex, u, v), distance);
		}
		return best == null ? null : best.contact();
	}

	@Nullable
	static Vec3 glueContactPoint(@Nullable SurgicalModelRenderContext.CubeGeometry cube,
		SurgicalGlueContact contact) {
		if (cube == null || contact == null || contact.faceIndex() < 0
			|| contact.faceIndex() >= SurgicalClientTopology.CUBE_FACES.length)
			return null;
		int[] face = SurgicalClientTopology.CUBE_FACES[contact.faceIndex()];
		Vec3 origin = cube.corners().get(face[0]);
		return origin.add(cube.corners().get(face[1]).subtract(origin).scale(contact.u()))
			.add(cube.corners().get(face[3]).subtract(origin).scale(contact.v()));
	}

	static double glueEditGuideRadius(SurgicalModelRenderContext.CubeGeometry cube,
		Vec3 center, Vec3 axis) {
		double radius = 0.0d;
		for (Vec3 corner : cube.corners()) {
			Vec3 relative = corner.subtract(center);
			Vec3 radial = relative.subtract(axis.scale(relative.dot(axis)));
			radius = Math.max(radius, radial.length());
		}
		return Math.max(0.25d, radius + SurgicalTableClientHandler.GLUE_EDIT_GUIDE_MARGIN);
	}

	static boolean glueEndpointsActuallyIntersect(List<GlueSubjectPreview> previews,
		SurgicalTableGluePacket.Endpoint first, SurgicalTableGluePacket.Endpoint second) {
		SurgicalModelRenderContext.CubeGeometry firstCube = previewCube(previews,
			first.subjectId(), first.cubeId());
		SurgicalModelRenderContext.CubeGeometry secondCube = previewCube(previews,
			second.subjectId(), second.cubeId());
		return SurgicalClientTopology.cubesActuallyIntersect(firstCube, secondCube);
	}

	static boolean glueEndpointsConnectWithinTolerance(List<GlueSubjectPreview> previews,
		SurgicalTableGluePacket.Endpoint first, SurgicalTableGluePacket.Endpoint second) {
		return SurgicalClientTopology.cubesConnectWithinTolerance(
			previewCube(previews, first.subjectId(), first.cubeId()),
			previewCube(previews, second.subjectId(), second.cubeId()));
	}

	static boolean glueCubesConnectWithinTolerance(List<GlueSubjectPreview> previews,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId) {
		return SurgicalClientTopology.cubesConnectWithinTolerance(
			previewCube(previews, firstSubjectId, firstCubeId),
			previewCube(previews, secondSubjectId, secondCubeId));
	}

	@Nullable
	static SurgicalModelRenderContext.CubeGeometry previewCube(List<GlueSubjectPreview> previews,
		int subjectId, int cubeId) {
		GlueSubjectPreview preview = previewSubject(previews, subjectId, cubeId);
		return preview == null ? null : previewCube(preview, cubeId);
	}

	@Nullable
	static GlueSubjectPreview previewSubject(List<GlueSubjectPreview> previews,
		int subjectId, int cubeId) {
		for (GlueSubjectPreview preview : previews)
			if (preview.subjectId == subjectId && preview.cubes.get(cubeId))
				return preview;
		return null;
	}

	@Nullable
	static SurgicalModelRenderContext.CubeGeometry previewCube(GlueSubjectPreview preview, int cubeId) {
		return preview.transformedCubes.get(cubeId);
	}

	@Nullable
	static SurgicalModelRenderContext.CubeGeometry transformedPreviewCube(
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

	static AABB cubeBounds(SurgicalModelRenderContext.CubeGeometry cube) {
		Vec3 first = cube.corners().getFirst();
		AABB bounds = new AABB(first, first);
		for (int corner = 1; corner < cube.corners().size(); corner++) {
			Vec3 point = cube.corners().get(corner);
			bounds = bounds.minmax(new AABB(point, point));
		}
		return bounds;
	}

	@Nullable
	static SurgicalTableGluePacket.Endpoint glueEndpoint(SurgicalSelectionResolver.Selection selection, Vec3 hit,
		SurgicalTableLayout.Proposal layout, int faceIndex) {
		SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selection.tablePos(), selection.subjectId()));
		SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
			: geometry.cubesById.get(selection.targetId());
		Vec3 worldHit = Vec3.atLowerCornerOf(selection.tablePos()).add(hit);
		SurgicalGlueContact contact = glueContact(cube, faceIndex, worldHit);
		return contact == null ? null : new SurgicalTableGluePacket.Endpoint(selection.subjectId(),
			selection.targetId(), selection.observedCubeCount(), selection.seams(), hit, layout, contact);
	}

	@Nullable
	static SurgicalTableLayout.Proposal currentGlueLayout(ClientLevel level, SurgicalSelectionResolver.Selection selection) {
		SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selection.tablePos(), selection.subjectId()));
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, selection.tablePos());
		SurgicalTableBlockEntity table = level.getBlockEntity(selection.tablePos())
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject subject = table == null ? null : table.getSubject(selection.subjectId());
		if (table == null || subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
			|| !plane.valid() || !selection.tablePos().equals(plane.source()))
			return null;
		// Capture existing overlaps unchanged; the glue preview checks destination clearance.
		SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
			geometry.observedCubeCount, geometry.presentCubes, geometry.seams, geometry.cutSeams,
			geometry.layoutCubes, geometry.offsets, plane.workArea(), List.of());
		return planned == null ? null : planned.proposal();
	}

	@Nullable
	static GluePreview planSymmetryPreview(ClientLevel level, SurgicalGlueController.PendingGlue first,
		SurgicalSymmetryController.SymmetryTarget target) {
		GluePreview baseline = planGluePreview(level, first, target.mirroredAnchorHit()).preview();
		return baseline == null || target.replayTransform().isIdentity() ? baseline
			: replayGlueTransform(level, baseline, first.selection().subjectId(), first.selection().targetId(),
				target.mirroredAnchorHit().geometry().subjectId, target.mirroredAnchorHit().cubeId(),
				target.replayTransform());
	}

	static GluePreviewPlan planGluePreview(ClientLevel level, SurgicalGlueController.PendingGlue first, SurgicalSelectionResolver.CubeHit targetHit) {
		if (!first.selection().tablePos().equals(targetHit.tablePos())
			|| !(level.getBlockEntity(targetHit.tablePos()) instanceof SurgicalTableBlockEntity table))
			return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
		SurgicalSubject targetSubject = table.getSubject(targetHit.geometry().subjectId);
		if (targetSubject == null)
			return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
		Map<Integer, BitSet> moving = table.connectedComponents(first.selection().subjectId(),
			first.selection().targetId(), first.selection().observedCubeCount(), first.selection().seams());
		Map<Integer, BitSet> anchored = table.connectedComponents(targetHit.geometry().subjectId,
			targetHit.cubeId(), targetHit.geometry().observedCubeCount, targetHit.geometry().seams);
		if (moving.isEmpty() || anchored.isEmpty() || SurgicalVisualCommitCoordinator.componentMapsIntersect(moving, anchored))
			return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);

		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, targetHit.tablePos());
		if (!plane.valid() || !targetHit.tablePos().equals(plane.source()))
			return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
		List<SurgicalTableLayout.Footprint> obstacles = gluePreviewObstacles(table, moving, anchored);
		Vec3 firstPoint = Vec3.atLowerCornerOf(targetHit.tablePos()).add(first.hit());
		Vec3 secondPoint = targetHit.location();
		SurgicalLayPose targetPose = targetSubject.layPose();
		List<GluePlanningSubject> planning = new ArrayList<>();
		double combinedLowestY = Double.POSITIVE_INFINITY;

		for (Map.Entry<Integer, BitSet> entry : moving.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(targetHit.tablePos(), entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
			BitSet cubes = (BitSet) entry.getValue().clone();
			Vec3 subjectOrigin = Vec3.atLowerCornerOf(targetHit.tablePos())
				.add(subject.originOffsetX(), 0.0d, subject.originOffsetZ());
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> baseTarget = new HashMap<>();
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> desired = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> targetRotations = new HashMap<>();
			for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry base = geometry.baseCubesById.get(cube);
				SurgicalModelRenderContext.CubeGeometry current = geometry.cubesById.get(cube);
				if (base == null || current == null)
					return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
				SurgicalModelRenderContext.CubeGeometry reframedBase = reframeBaseCube(base, subjectOrigin,
					subject.layPose(), targetPose);
				SurgicalModelRenderContext.CubeGeometry reframedCurrent = reframeCurrentCube(current,
					firstPoint, secondPoint, subject.layPose(), targetPose);
				baseTarget.put(cube, reframedBase);
				desired.put(cube, reframedCurrent);
				targetRotations.put(cube, geometry.serverRotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY)
					.reframe(subject.layPose(), targetPose));
				combinedLowestY = Math.min(combinedLowestY, SurgicalVisualCommitCoordinator.lowestY(reframedCurrent));
			}
			planning.add(new GluePlanningSubject(subject, cubes, List.copyOf(baseTarget.values()),
				Map.copyOf(desired), Map.copyOf(targetRotations)));
		}
		for (Map.Entry<Integer, BitSet> entry : anchored.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(targetHit.tablePos(), entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry current = geometry.cubesById.get(cube);
				if (current == null)
					return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
				combinedLowestY = Math.min(combinedLowestY, SurgicalVisualCommitCoordinator.lowestY(current));
			}
		}
		double surfaceY = plane.workArea().surfaceY() + SurgicalTablePoseResolver.TABLE_CLEARANCE;
		double groundLiftY = Math.max(0.0d, surfaceY - combinedLowestY);
		if (!Double.isFinite(groundLiftY) || groundLiftY > SurgicalTablePlane.MAX_TILES + 2.0d)
			return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
		List<GlueSubjectPreview> previews = new ArrayList<>(planning.size());
		List<SurgicalTableGluePacket.Move> moves = new ArrayList<>(planning.size());
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves = new ArrayList<>(anchored.size());
		for (Map.Entry<Integer, BitSet> entry : anchored.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(targetHit.tablePos(), entry.getKey()));
			if (subject == null || geometry == null)
				return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
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
					return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
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
					return GluePreviewPlan.failed(SurgicalTableFailure.OPERATION_FAILED);
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
				return GluePreviewPlan.failed(glueLayoutFailure(source, rotatedBase, confirmedOffsets,
					plane.workArea(), obstacles));
			List<SurgicalTableGluePacket.CubeTranslation> translations = new ArrayList<>(source.cubes.cardinality());
			for (int cube = source.cubes.nextSetBit(0); cube >= 0; cube = source.cubes.nextSetBit(cube + 1))
				translations.add(new SurgicalTableGluePacket.CubeTranslation(cube,
					planned.offsets().getOrDefault(cube, Vec3.ZERO), source.targetRotations.getOrDefault(cube,
						SurgicalCubeRotation.IDENTITY)));
			moves.add(new SurgicalTableGluePacket.Move(source.subject.id(), translations, planned.proposal()));
			previews.add(new GlueSubjectPreview(source.subject.id(), source.cubes, targetPose,
				source.baseTarget, planned.offsets(), source.targetRotations, true));
		}
		return new GluePreviewPlan(new GluePreview(first, targetHit.tablePos(), targetHit.geometry().subjectId, targetHit.cubeId(),
			targetHit.location(), table.clientDataRevision(), targetPose, plane.workArea(), obstacles,
			previews, moves, anchorMoves), null);
	}

	/** Diagnoses a rejected initial layout without treating missing geometry as a space failure. */
	static SurgicalTableFailure glueLayoutFailure(GluePlanningSubject source,
		List<SurgicalModelRenderContext.CubeGeometry> rotatedBase, Map<Integer, Vec3> offsets,
		SurgicalTablePlane.WorkArea area, List<SurgicalTableLayout.Footprint> obstacles) {
		List<BitSet> components = SurgicalAssembly.components(source.subject.cubeCount(), source.cubes,
			source.subject.seams(), source.subject.cutSeamsForRender());
		if (components.isEmpty())
			return SurgicalTableFailure.OPERATION_FAILED;
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexed = indexCubes(rotatedBase);
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> envelopes = new ArrayList<>();
		for (BitSet component : components) {
			int root = component.nextSetBit(0);
			AABB envelope = null;
			for (int cube = root; cube >= 0; cube = component.nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry geometry = indexed.get(cube);
				Vec3 offset = offsets.get(cube);
				if (!SurgicalGeometryStore.finiteCubeGeometry(geometry) || offset == null || !Double.isFinite(offset.x)
					|| !Double.isFinite(offset.y) || !Double.isFinite(offset.z))
					return SurgicalTableFailure.OPERATION_FAILED;
				AABB bounds = cubeBounds(geometry).move(offset);
				envelope = envelope == null ? bounds : envelope.minmax(bounds);
				footprints.add(new SurgicalTableLayout.Footprint(root, bounds.minX, bounds.minZ,
					bounds.maxX, bounds.maxZ, SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED));
			}
			envelopes.add(new SurgicalTableLayout.Footprint(root, envelope.minX, envelope.minZ,
				envelope.maxX, envelope.maxZ, SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED));
		}
		return SurgicalTableLayout.lacksSpace(area, envelopes, List.of())
			|| SurgicalTableLayout.lacksSpace(area, footprints, obstacles)
			? SurgicalTableFailure.NO_SPACE : SurgicalTableFailure.OPERATION_FAILED;
	}

	/** Applies a previously recorded smart-glue edit once to a freshly aligned symmetry preview. */
	@Nullable
	static GluePreview replayGlueTransform(ClientLevel level, GluePreview current,
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
			if (!SurgicalVisualCommitCoordinator.validEditedVertical(preview.cubes, rotatedBase, offsets, current.workArea.surfaceY()
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
			|| !glueCubesConnectWithinTolerance(previews, firstSubjectId, firstCubeId,
				secondSubjectId, secondCubeId))
			return null;
		return new GluePreview(current.request, current.ownerPos, current.targetSubjectId,
			current.targetCubeId, current.targetHit, current.tableRevision, current.targetPose,
			current.workArea, current.obstacles, previews, moves, current.anchorMoves);
	}

	@Nullable
	static GluePreview adjustGluePreview(ClientLevel level, SurgicalGlueController.GlueEditor editor, Vec3 translation,
		SurgicalCubeRotation deltaRotation) {
		GluePreview current = editor.preview;
		if (!(level.getBlockEntity(current.ownerPos) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != current.tableRevision)
			return null;
		if (!SurgicalGlueTransform.withinEditRange(editor.replayTransform.translation().add(translation))) {
			SurgicalTableFailure.EDIT_LIMIT.display(Minecraft.getInstance().player);
			return null;
		}
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
				Vec3 baseCenter = cubeCenter(base);
				SurgicalGlueTransform adjustment = new SurgicalGlueTransform(translation, deltaRotation);
				Vec3 newOffset = adjustment.apply(baseCenter.add(oldOffset), editor.axisCenter).subtract(baseCenter);
				SurgicalCubeRotation newRotation = oldRotation.then(deltaRotation);
				offsets.put(cube, newOffset);
				rotations.put(cube, newRotation);
			}

			List<SurgicalModelRenderContext.CubeGeometry> rotatedBase = transformCubes(
				preview.baseCubes, rotations, Map.of());
			if (!SurgicalVisualCommitCoordinator.validEditedVertical(preview.cubes, rotatedBase, offsets, current.workArea.surfaceY()
				+ SurgicalTablePoseResolver.TABLE_CLEARANCE))
				return null;
			List<SurgicalWrenchRotation.Cube> layoutCubes = new ArrayList<>();
			for (SurgicalModelRenderContext.CubeGeometry cube : rotatedBase) {
				if (!preview.cubes.get(cube.cubeId()))
					continue;
				Vec3 offset = offsets.getOrDefault(cube.cubeId(), Vec3.ZERO);
				layoutCubes.add(new SurgicalWrenchRotation.Cube(cube.cubeId(),
					cube.corners().stream().map(corner -> corner.add(offset)).toList(), offset,
					rotations.getOrDefault(cube.cubeId(), SurgicalCubeRotation.IDENTITY)));
			}
			SurgicalTableLayout.Proposal layout = new SurgicalWrenchRotation.Body(subject.id(),
				subject.persistentId(), subject.cubeCount(), subject.seams(), layoutCubes)
				.layout(preview.cubes, subject.cutSeamsForRender());
			List<SurgicalTableGluePacket.CubeTranslation> transforms =
				new ArrayList<>(preview.cubes.cardinality());
			for (int cube = preview.cubes.nextSetBit(0); cube >= 0; cube = preview.cubes.nextSetBit(cube + 1)) {
				SurgicalTableGluePacket.CubeTranslation transform = new SurgicalTableGluePacket.CubeTranslation(
					cube, offsets.getOrDefault(cube, Vec3.ZERO), rotations.get(cube));
				if (!transform.valid())
					return null;
				transforms.add(transform);
			}
			moves.add(new SurgicalTableGluePacket.Move(preview.subjectId, transforms, layout));
			previews.add(new GlueSubjectPreview(preview.subjectId, preview.cubes, preview.pose,
				preview.baseCubes, offsets, rotations, true));
		}
		if (moves.size() != current.moves.size())
			return null;
		GluePreview adjusted = new GluePreview(current.request, current.ownerPos,
			current.targetSubjectId, current.targetCubeId,
			current.targetHit, current.tableRevision, current.targetPose,
			current.workArea, current.obstacles, previews, moves, current.anchorMoves);
		return adjusted;
	}

	static List<SurgicalTableLayout.Footprint> gluePreviewObstacles(SurgicalTableBlockEntity table,
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

	static SurgicalModelRenderContext.CubeGeometry reframeBaseCube(
		SurgicalModelRenderContext.CubeGeometry cube, Vec3 subjectOrigin, SurgicalLayPose source,
		SurgicalLayPose target) {
		Vec3 sourceAnchor = subjectOrigin.add(source.translation());
		Vec3 targetAnchor = subjectOrigin.add(target.translation());
		List<Vec3> corners = cube.corners().stream()
			.map(corner -> targetAnchor.add(source.rotateInto(target, corner.subtract(sourceAnchor))))
			.toList();
		return cube.withCorners(corners);
	}

	static SurgicalModelRenderContext.CubeGeometry reframeCurrentCube(
		SurgicalModelRenderContext.CubeGeometry cube, Vec3 firstPoint, Vec3 secondPoint,
		SurgicalLayPose source, SurgicalLayPose target) {
		List<Vec3> corners = cube.corners().stream()
			.map(corner -> secondPoint.add(source.rotateInto(target, corner.subtract(firstPoint))))
			.toList();
		return cube.withCorners(corners);
	}

	@Nullable
	static SurgicalModelRenderContext.CubeGeometry cubeById(
		List<SurgicalModelRenderContext.CubeGeometry> cubes, int cubeId) {
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (cube.cubeId() == cubeId)
				return cube;
		return null;
	}

	static Vec3 cubeCenter(SurgicalModelRenderContext.CubeGeometry cube) {
		Vec3 sum = Vec3.ZERO;
		for (Vec3 corner : cube.corners())
			sum = sum.add(corner);
		return sum.scale(1.0d / cube.corners().size());
	}

	static Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexCubes(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexed = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			indexed.put(cube.cubeId(), cube);
		return Map.copyOf(indexed);
	}

	static List<SurgicalModelRenderContext.CubeGeometry> transformCubes(
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

	record GluePreviewCubeHit(GlueSubjectPreview subject,
		SurgicalModelRenderContext.CubeGeometry geometry, int cubeId, int faceIndex, Vec3 location) {}

	record GluePlanningSubject(SurgicalSubject subject, BitSet cubes,
		List<SurgicalModelRenderContext.CubeGeometry> baseTarget,
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> desired,
		Map<Integer, SurgicalCubeRotation> targetRotations) {
		GluePlanningSubject {
			cubes = (BitSet) cubes.clone();
			baseTarget = List.copyOf(baseTarget);
			desired = Map.copyOf(desired);
			targetRotations = Map.copyOf(targetRotations);
		}
	}

	static final class GlueSubjectPreview {
		final int subjectId;
		final BitSet cubes;
		final SurgicalLayPose pose;
		final List<SurgicalModelRenderContext.CubeGeometry> baseCubes;
		final Map<Integer, Vec3> offsets;
		final Map<Integer, SurgicalCubeRotation> rotations;
		final boolean editable;
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

	record GluePreviewPlan(@Nullable GluePreview preview, @Nullable SurgicalTableFailure failure) {
		static GluePreviewPlan failed(SurgicalTableFailure failure) {
			return new GluePreviewPlan(null, failure);
		}
	}

	record GluePreview(SurgicalGlueController.PendingGlue request, BlockPos ownerPos, int targetSubjectId, int targetCubeId,
		Vec3 targetHit, int tableRevision, SurgicalLayPose targetPose,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> obstacles,
		List<GlueSubjectPreview> subjects, List<SurgicalTableGluePacket.Move> moves,
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves) {
		GluePreview {
			obstacles = List.copyOf(obstacles);
			subjects = List.copyOf(subjects);
			moves = List.copyOf(moves);
			anchorMoves = List.copyOf(anchorMoves);
		}

		boolean matches(SurgicalGlueController.PendingGlue pending, SurgicalSelectionResolver.CubeHit hit, int revision) {
			return request == pending && ownerPos.equals(hit.tablePos())
				&& targetSubjectId == hit.geometry().subjectId && targetCubeId == hit.cubeId()
				&& tableRevision == revision && targetHit.distanceToSqr(hit.location()) <= 1.0e-14d;
		}
	}
}
