package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalPickingGeometry implementation; public entry points remain on SurgicalSelectionResolver. */
final class SurgicalPickingGeometry {
	private SurgicalPickingGeometry() {}


	/** Pulls a box inward on every side, leaving axes too thin to give it up collapsed, not inverted. */
	static AABB insetBox(AABB box, double inset) {
		return box.inflate(-axisInset(box.maxX - box.minX, inset), -axisInset(box.maxY - box.minY, inset),
			-axisInset(box.maxZ - box.minZ, inset));
	}

	static double axisInset(double size, double inset) {
		return Math.min(inset, Math.max(0.0d, size * 0.5d - 1.0e-4d));
	}

	static Vec3 tableSurfaceTarget(SurgicalSelectionResolver.Ray ray, double surfaceY) {
		double deltaY = ray.end().y - ray.start().y;
		if (Math.abs(deltaY) <= 1.0e-9d)
			return ray.end();
		double progress = (surfaceY - ray.start().y) / deltaY;
		if (!Double.isFinite(progress) || progress < 0.0d)
			return ray.end();
		return ray.start().add(ray.end().subtract(ray.start()).scale(progress));
	}

	static boolean sameHorizontalTarget(Vec3 target, double previousX, double previousZ) {
		return Math.abs(target.x - previousX) <= 1.0e-7d
			&& Math.abs(target.z - previousZ) <= 1.0e-7d;
	}

	static SurgicalModelRenderContext.CubeGeometry boundsGeometry(AABB bounds,
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

	static List<SurgicalModelRenderContext.CubeGeometry> translateCubes(
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
	static AABB logicalBounds(List<SurgicalModelRenderContext.CubeGeometry> cubes,
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

	@Nullable
	static Vec3 intersectContact(SurgicalSelectionResolver.Ray ray, SurgicalClientTopology.Contact contact) {
		Vec3 nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (List<Vec3> face : contact.faces()) {
			Vec3 hit = intersectPolygon(ray.start(), ray.end(), face);
			if (hit == null)
				continue;
			double distance = ray.start().distanceToSqr(hit);
			if (distance < nearestDistance) {
				nearest = hit;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	/**
	 * Divides the selected face into model-pixel cells. A hit snaps either to the
	 * containing cell or to its nearest grid intersection, including intersections on
	 * the outer cube boundary.
	 */
	@Nullable
	static SurgicalSelectionResolver.GluePoint snapGluePoint(SurgicalModelRenderContext.CubeGeometry cube,
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
		SurgicalSelectionResolver.ModelPixelSpan modelU = null;
		SurgicalSelectionResolver.ModelPixelSpan modelV = null;
		if (!cube.modelCorners().isEmpty()) {
			Vec3 modelOrigin = cube.modelCorners().get(face[0]);
			modelU = modelPixelSpan(modelOrigin, cube.modelCorners().get(face[1]));
			modelV = modelPixelSpan(modelOrigin, cube.modelCorners().get(face[3]));
		}
		SurgicalModelRenderContext.FaceGrid faceGrid = cube.faceGrids().isEmpty()
			? null : cube.faceGrids().get(faceIndex);
		double pixelsU = normalizedPixelLength(modelU != null ? modelU.length()
			: faceGrid != null && faceGrid.valid() ? faceGrid.pixelsU() : Math.sqrt(uu) / SurgicalTableClientHandler.MODEL_PIXEL_SIZE);
		double pixelsV = normalizedPixelLength(modelV != null ? modelV.length()
			: faceGrid != null && faceGrid.valid() ? faceGrid.pixelsV() : Math.sqrt(vv) / SurgicalTableClientHandler.MODEL_PIXEL_SIZE);
		SurgicalSelectionResolver.PixelInterval cellU = modelU != null
			? pixelInterval(u, modelU.start(), modelU.end()) : pixelInterval(u, 0.0d, pixelsU);
		SurgicalSelectionResolver.PixelInterval cellV = modelV != null
			? pixelInterval(v, modelV.start(), modelV.end()) : pixelInterval(v, 0.0d, pixelsV);
		if (cellU == null || cellV == null)
			return null;

		Vec3 cellCenter = origin.add(edgeU.scale(cellU.center())).add(edgeV.scale(cellV.center()));
		double intersectionU = nearestEndpoint(u, cellU);
		double intersectionV = nearestEndpoint(v, cellV);
		Vec3 intersection = origin.add(edgeU.scale(intersectionU)).add(edgeV.scale(intersectionV));
		boolean selectIntersection = rawHit.distanceToSqr(intersection) < rawHit.distanceToSqr(cellCenter);
		Vec3 location = selectIntersection ? intersection : cellCenter;

		Vec3 normal = edgeU.cross(edgeV).normalize();
		if (normal.dot(location.subtract(SurgicalGluePreviewPlanner.cubeCenter(cube))) < 0.0d)
			normal = normal.scale(-1.0d);
		Vec3 surfaceOffset = normal.scale(SurgicalTableClientHandler.GLUE_POINT_SURFACE_OFFSET);
		List<SurgicalClientTopology.Edge> marker = new ArrayList<>(4);
		if (!selectIntersection) {
			Vec3 first = origin.add(edgeU.scale(cellU.start()))
				.add(edgeV.scale(cellV.start())).add(surfaceOffset);
			Vec3 second = origin.add(edgeU.scale(cellU.end()))
				.add(edgeV.scale(cellV.start())).add(surfaceOffset);
			Vec3 third = origin.add(edgeU.scale(cellU.end()))
				.add(edgeV.scale(cellV.end())).add(surfaceOffset);
			Vec3 fourth = origin.add(edgeU.scale(cellU.start()))
				.add(edgeV.scale(cellV.end())).add(surfaceOffset);
			marker.add(new SurgicalClientTopology.Edge(first, second));
			marker.add(new SurgicalClientTopology.Edge(second, third));
			marker.add(new SurgicalClientTopology.Edge(third, fourth));
			marker.add(new SurgicalClientTopology.Edge(fourth, first));
		} else {
			Vec3 markerCenter = intersection.add(surfaceOffset);
			Vec3 halfU = edgeU.scale(SurgicalTableClientHandler.GLUE_POINT_CROSS_HALF_LENGTH_PIXELS / pixelsU);
			Vec3 halfV = edgeV.scale(SurgicalTableClientHandler.GLUE_POINT_CROSS_HALF_LENGTH_PIXELS / pixelsV);
			marker.add(new SurgicalClientTopology.Edge(markerCenter.subtract(halfU),
				markerCenter.add(halfU)));
			marker.add(new SurgicalClientTopology.Edge(markerCenter.subtract(halfV),
				markerCenter.add(halfV)));
		}
		return new SurgicalSelectionResolver.GluePoint(location, marker);
	}

	static double nearestEndpoint(double progress, SurgicalSelectionResolver.PixelInterval interval) {
		return Math.abs(progress - interval.start()) < Math.abs(progress - interval.end())
			? interval.start() : interval.end();
	}

	static double normalizedPixelLength(double length) {
		double nearest = Math.rint(length);
		return Math.abs(length - nearest) <= SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON ? nearest : length;
	}

	@Nullable
	static SurgicalSelectionResolver.ModelPixelSpan modelPixelSpan(Vec3 first, Vec3 second) {
		Vec3 delta = second.subtract(first);
		double x = Math.abs(delta.x);
		double y = Math.abs(delta.y);
		double z = Math.abs(delta.z);
		if (x >= y && x >= z && x > SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON)
			return new SurgicalSelectionResolver.ModelPixelSpan(first.x, second.x);
		if (y >= z && y > SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON)
			return new SurgicalSelectionResolver.ModelPixelSpan(first.y, second.y);
		return z > SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON ? new SurgicalSelectionResolver.ModelPixelSpan(first.z, second.z) : null;
	}

	@Nullable
	static SurgicalSelectionResolver.PixelInterval pixelInterval(double progress, double spanStart, double spanEnd) {
		double minimum = Math.min(spanStart, spanEnd);
		double maximum = Math.max(spanStart, spanEnd);
		if (!Double.isFinite(progress) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
			|| maximum - minimum <= SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON)
			return null;
		double coordinate = spanStart + (spanEnd - spanStart) * Math.max(0.0d, Math.min(1.0d, progress));
		double sample = coordinate >= maximum - SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON
			? Math.nextDown(maximum) : Math.max(minimum, coordinate);
		double lower = Math.max(minimum, Math.floor(sample));
		double upper = Math.min(maximum, Math.floor(sample) + 1.0d);
		if (upper <= lower + SurgicalTableClientHandler.GLUE_POINT_PIXEL_EPSILON)
			return null;
		double first = (lower - spanStart) / (spanEnd - spanStart);
		double second = (upper - spanStart) / (spanEnd - spanStart);
		double start = Math.min(first, second);
		double end = Math.max(first, second);
		return new SurgicalSelectionResolver.PixelInterval(start, end, (start + end) * 0.5d);
	}

	static SurgicalSelectionResolver.Ray playerRay(LocalPlayer player) {
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		Vec3 start = player.getEyePosition();
		return new SurgicalSelectionResolver.Ray(start, start.add(player.getViewVector(1.0f).scale(range)));
	}

	static boolean isOccluded(ClientLevel level, LocalPlayer player, Vec3 start, Vec3 hit,
		BlockPos tablePos) {
		BlockHitResult blockHit = level.clip(new ClipContext(start, hit, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.BLOCK
			|| start.distanceToSqr(blockHit.getLocation()) + 1.0e-5d >= start.distanceToSqr(hit))
			return false;
		BlockPos obstruction = blockHit.getBlockPos();
		if (level.getBlockState(obstruction).getBlock() instanceof SurgicalTableBlock) {
			SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, obstruction);
			if (plane.workArea().containsTile(tablePos))
				return false;
		}
		return true;
	}

	/** Allocation-free segment/AABB broad phase used before oriented cube/contact intersection. */
	static boolean rayIntersectsBounds(SurgicalSelectionResolver.Ray ray, AABB bounds, double inflation) {
		double startX = ray.start().x;
		double startY = ray.start().y;
		double startZ = ray.start().z;
		double deltaX = ray.end().x - startX;
		double deltaY = ray.end().y - startY;
		double deltaZ = ray.end().z - startZ;
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
	static Vec3 intersectQuad(Vec3 start, Vec3 end,
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
	static Vec3 intersectPolygon(Vec3 start, Vec3 end, List<Vec3> polygon) {
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

	static double pointToContactDistance(Vec3 point, SurgicalClientTopology.Contact contact) {
		double best = Double.MAX_VALUE;
		for (List<Vec3> face : contact.faces())
			best = Math.min(best, pointToPolygonDistance(point, face));
		return best;
	}

	static double pointToPolygonDistance(Vec3 point, List<Vec3> polygon) {
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

	static boolean insideConvexPolygon(Vec3 point, List<Vec3> polygon, Vec3 normal) {
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

	static double pointToSegmentDistance(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSquared = segment.lengthSqr();
		if (lengthSquared <= 1.0e-12d)
			return point.distanceTo(start);
		double amount = point.subtract(start).dot(segment) / lengthSquared;
		amount = Math.max(0.0d, Math.min(1.0d, amount));
		return point.distanceTo(start.add(segment.scale(amount)));
	}
}
