package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalMeshRecovery implementation; public entry points remain on SurgicalCapturedRenderPlan. */
final class SurgicalMeshRecovery {
	private SurgicalMeshRecovery() {}


	static SurgicalCapturedRenderPlan build(SurgicalCapturedRenderPlan.CapturedInput captured) {
		return build(captured.streams(), captured.observedCubes(), captured.topology(),
			captured.bindPlayerSkinLayers(), captured.saddleCubes());
	}

	static SurgicalCapturedRenderPlan build(List<SurgicalRenderCapture.CaptureStream> streams,
		List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes, boolean topology, boolean bindPlayerSkinLayers,
		Set<SurgicalCapturedRenderPlan.GeometryKey> saddleCubes) {
		Map<SurgicalCapturedRenderPlan.GeometryKey, List<SurgicalCapturedRenderPlan.ComponentBuilder>> recovered = new LinkedHashMap<>();
		List<SurgicalCapturedRenderPlan.SourceBatch> extras = new ArrayList<>();
		List<SurgicalCapturedRenderPlan.PendingQuad> pendingQuads = new ArrayList<>();
		int order = 0;

		for (SurgicalRenderCapture.CaptureStream stream : streams) {
			List<SurgicalRenderCapture.CapturedVertex> vertices = stream.vertices;
			if (stream.preserveSource || stream.renderType.mode() != VertexFormat.Mode.QUADS) {
				if (!vertices.isEmpty())
					extras.add(new SurgicalCapturedRenderPlan.SourceBatch(stream.renderType, List.copyOf(vertices), false));
				continue;
			}

			int cursor = 0;
			while (cursor + 4 <= vertices.size()) {
				SurgicalCapturedRenderPlan.RecoveredCandidate candidate = recoverCandidate(vertices, cursor);
				if (candidate == null) {
					List<SurgicalRenderCapture.CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
					if (SurgicalMaterialBinding.quadVisible(stream.renderType, quad))
						pendingQuads.add(new SurgicalCapturedRenderPlan.PendingQuad(stream.id, stream.renderType,
							List.copyOf(quad), order++));
					cursor += 4;
					continue;
				}

				List<SurgicalRenderCapture.CapturedVertex> candidateVertices = candidate.vertices();
				SurgicalCapturedRenderPlan.RecoveredCuboid cuboid = candidate.cuboid();
				SurgicalCapturedRenderPlan.GeometryKey key = SurgicalCapturedRenderPlan.GeometryKey.of(cuboid.corners());
				List<SurgicalCapturedRenderPlan.ComponentBuilder> matches = recovered.computeIfAbsent(key, ignored -> new ArrayList<>());
				SurgicalCapturedRenderPlan.ComponentBuilder builder = matches.stream()
					.filter(match -> !match.captureStreams.get(stream.id))
					.findFirst()
					.orElse(null);
				if (builder == null) {
					builder = new SurgicalCapturedRenderPlan.ComponentBuilder(order++, cuboid,
						topology ? matchingModelCube(cuboid, key, observedCubes) : SurgicalCapturedRenderPlan.ModelCubeMatch.NONE, topology);
					matches.add(builder);
				}
				builder.captureStreams.set(stream.id);
				if (topology) builder.observeNames(candidateVertices);
				builder.observeMaterial(stream.renderType);
				if (SurgicalMaterialBinding.hasVisibleQuad(stream.renderType, candidateVertices))
					builder.addVisibleBatch(stream.renderType, candidateVertices);
				cursor += candidateVertices.size();
			}
			if (cursor < vertices.size())
				extras.add(new SurgicalCapturedRenderPlan.SourceBatch(stream.renderType,
					List.copyOf(vertices.subList(cursor, vertices.size())), false));
		}

		List<SurgicalCapturedRenderPlan.ComponentBuilder> recoveredBuilders = recovered.values().stream()
			.flatMap(List::stream)
			.toList();
		for (SurgicalCapturedRenderPlan.PendingMesh mesh : connectedPendingMeshes(pendingQuads)) {
			SurgicalCapturedRenderPlan.ComponentBuilder overlayOwner = SurgicalMaterialBinding.surfaceOverlayOwner(mesh.vertices(), recoveredBuilders);
			if (overlayOwner != null) {
				overlayOwner.addSurfaceOverlayBatch(mesh.renderType(), mesh.vertices());
				continue;
			}

			SurgicalCapturedRenderPlan.RecoveredCuboid bounds = syntheticBounds(mesh.vertices());
			if (bounds == null || isFlat(bounds) && !sharesCapturedTexture(mesh.renderType(), recoveredBuilders)
				&& !SurgicalMaterialBinding.isMushroomMaterial(mesh.renderType(), mesh.vertices())) {
				extras.add(new SurgicalCapturedRenderPlan.SourceBatch(mesh.renderType(), mesh.vertices(), false));
				continue;
			}

			SurgicalCapturedRenderPlan.GeometryKey key = SurgicalCapturedRenderPlan.GeometryKey.of(bounds.corners());
			List<SurgicalCapturedRenderPlan.ComponentBuilder> matches = recovered.computeIfAbsent(key, ignored -> new ArrayList<>());
			SurgicalCapturedRenderPlan.ComponentBuilder builder = matches.stream()
				.filter(candidate -> !candidate.captureStreams.get(mesh.streamId()))
				.findFirst()
				.orElse(null);
			if (builder == null) {
				builder = new SurgicalCapturedRenderPlan.ComponentBuilder(mesh.order(), bounds,
					topology ? matchingModelCube(bounds, key, observedCubes) : SurgicalCapturedRenderPlan.ModelCubeMatch.NONE, topology);
				matches.add(builder);
				recoveredBuilders = recovered.values().stream().flatMap(List::stream).toList();
			}
			builder.captureStreams.set(mesh.streamId());
			if (topology) builder.observeNames(mesh.vertices());
			builder.observeMaterial(mesh.renderType());
			builder.addVisibleBatch(mesh.renderType(), mesh.vertices());
		}

		List<SurgicalCapturedRenderPlan.ComponentBuilder> visible = recovered.values().stream()
			.flatMap(List::stream)
			.filter(builder -> !builder.batches.isEmpty())
			.sorted(Comparator.comparingInt(builder -> builder.order))
			.toList();
		if (bindPlayerSkinLayers)
			visible = bindPlayerSkinLayers(visible);
		List<SurgicalCapturedRenderPlan.Component> components = new ArrayList<>(visible.size());
		for (int id = 0; id < visible.size(); id++) {
			SurgicalCapturedRenderPlan.ComponentBuilder builder = visible.get(id);
			components.add(builder.build(id, !saddleCubes.isEmpty()
				&& saddleCubes.contains(SurgicalCapturedRenderPlan.GeometryKey.of(builder.cuboid.corners()))
				|| SurgicalMaterialBinding.shouldPreserveSource(builder, visible)));
		}
		return new SurgicalCapturedRenderPlan(components, extras);
	}

	/**
	 * Player hats, jackets, sleeves and trouser legs are inflated copies of the six body cubes.
	 * Attach their captured vertices to the underlying cube so surgery moves and rotates both as one
	 * component. The original skin layers remain part of source-form projection, while slime form
	 * renders only the bound body's slime geometry.
	 */
	static List<SurgicalCapturedRenderPlan.ComponentBuilder> bindPlayerSkinLayers(List<SurgicalCapturedRenderPlan.ComponentBuilder> visible) {
		Set<SurgicalCapturedRenderPlan.ComponentBuilder> boundLayers = Collections.newSetFromMap(new IdentityHashMap<>());
		List<SurgicalCapturedRenderPlan.ComponentBuilder> byVolume = visible.stream()
			.sorted(Comparator.comparingDouble(builder -> cuboidVolume(builder.cuboid)))
			.toList();

		for (SurgicalCapturedRenderPlan.ComponentBuilder layer : byVolume) {
			if (boundLayers.contains(layer))
				continue;
			SurgicalCapturedRenderPlan.ComponentBuilder owner = null;
			float closestVolume = Float.NEGATIVE_INFINITY;
			for (SurgicalCapturedRenderPlan.ComponentBuilder candidate : byVolume) {
				if (candidate == layer || boundLayers.contains(candidate)
					|| !SurgicalMaterialBinding.isCloseFittingOverlay(layer.cuboid, candidate.cuboid))
					continue;
				float volume = cuboidVolume(candidate.cuboid);
				if (volume > closestVolume) {
					owner = candidate;
					closestVolume = volume;
				}
			}
			if (owner == null)
				continue;
			owner.bindSkinLayer(layer);
			boundLayers.add(layer);
		}

		return visible.stream()
			.filter(builder -> !boundLayers.contains(builder))
			.toList();
	}

	static float cuboidVolume(SurgicalCapturedRenderPlan.RecoveredCuboid cuboid) {
		return cuboid.a().length() * cuboid.b().length() * cuboid.c().length();
	}

	/**
	 * A normal model cube contributes six consecutive quads. Some renderers omit hidden or
	 * unavailable faces, so also accept a shorter prefix when its vertices still describe faces of
	 * one three-dimensional oriented box. A lone quad is deliberately left for the overlay/model
	 * texture classifier: promoting every glyph and nameplate quad would turn effects into topology.
	 */
	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCandidate recoverCandidate(List<SurgicalRenderCapture.CapturedVertex> vertices, int cursor) {
		int maxFaces = Math.min(6, (vertices.size() - cursor) / 4);
		for (int faces = maxFaces; faces >= 2; faces--) {
			List<SurgicalRenderCapture.CapturedVertex> candidate = vertices.subList(cursor, cursor + faces * 4);
			SurgicalCapturedRenderPlan.RecoveredCuboid cuboid = recoverCuboid(candidate);
			if (faces < 6 && cuboid != null
				&& (isFlat(cuboid) || !facesBelongToOneBox(candidate)))
				cuboid = null;
			if (cuboid == null)
				cuboid = recoverPartialCuboid(candidate);
			if (cuboid != null)
				return new SurgicalCapturedRenderPlan.RecoveredCandidate(cuboid, List.copyOf(candidate));
		}
		return null;
	}

	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCuboid recoverPartialCuboid(List<SurgicalRenderCapture.CapturedVertex> vertices) {
		SurgicalCapturedRenderPlan.RecoveredCuboid bounds = syntheticBounds(vertices);
		if (bounds == null || isFlat(bounds) || !facesBelongToOneBox(vertices))
			return null;
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<SurgicalRenderCapture.CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			if (!isParallelogram(quad) || !quadOnBoundsFace(quad, bounds))
				return null;
		}
		return bounds;
	}

	static boolean facesBelongToOneBox(List<SurgicalRenderCapture.CapturedVertex> vertices) {
		int faceCount = vertices.size() / 4;
		if (faceCount < 2)
			return false;
		if (faceCount == 2) {
			List<SurgicalRenderCapture.CapturedVertex> first = vertices.subList(0, 4);
			List<SurgicalRenderCapture.CapturedVertex> second = vertices.subList(4, 8);
			if (sharesEdge(first, second))
				return true;
			Vector3f firstNormal = quadNormal(first);
			Vector3f secondNormal = quadNormal(second);
			return firstNormal != null && secondNormal != null
				&& firstNormal.dot(secondNormal) <= -SurgicalCapturedRenderPlan.SYNTHETIC_PARALLEL_DOT_MIN;
		}

		BitSet connected = new BitSet(faceCount);
		connected.set(0);
		for (int pass = 0; pass < faceCount; pass++) {
			boolean changed = false;
			for (int known = connected.nextSetBit(0); known >= 0; known = connected.nextSetBit(known + 1)) {
				List<SurgicalRenderCapture.CapturedVertex> knownFace = vertices.subList(known * 4, known * 4 + 4);
				for (int candidate = 0; candidate < faceCount; candidate++) {
					if (connected.get(candidate))
						continue;
					List<SurgicalRenderCapture.CapturedVertex> candidateFace = vertices.subList(candidate * 4, candidate * 4 + 4);
					if (!sharesEdge(knownFace, candidateFace))
						continue;
					connected.set(candidate);
					changed = true;
				}
			}
			if (!changed)
				break;
		}
		return connected.cardinality() == faceCount;
	}

	static List<SurgicalCapturedRenderPlan.PendingMesh> connectedPendingMeshes(List<SurgicalCapturedRenderPlan.PendingQuad> quads) {
		List<SurgicalCapturedRenderPlan.PendingMesh> meshes = new ArrayList<>();
		BitSet claimed = new BitSet(quads.size());
		for (int start = 0; start < quads.size(); start++) {
			if (claimed.get(start))
				continue;
			SurgicalCapturedRenderPlan.PendingQuad seed = quads.get(start);
			claimed.set(start);
			List<Integer> open = new ArrayList<>();
			open.add(start);
			List<SurgicalRenderCapture.CapturedVertex> vertices = new ArrayList<>(seed.vertices());
			int order = seed.order();
			for (int openIndex = 0; openIndex < open.size(); openIndex++) {
				SurgicalCapturedRenderPlan.PendingQuad current = quads.get(open.get(openIndex));
				for (int candidateIndex = start + 1; candidateIndex < quads.size(); candidateIndex++) {
					if (claimed.get(candidateIndex))
						continue;
					SurgicalCapturedRenderPlan.PendingQuad candidate = quads.get(candidateIndex);
					if (candidate.streamId() != seed.streamId()
						|| !sharesEdge(current.vertices(), candidate.vertices()))
						continue;
					claimed.set(candidateIndex);
					open.add(candidateIndex);
					vertices.addAll(candidate.vertices());
					order = Math.min(order, candidate.order());
				}
			}
			meshes.add(new SurgicalCapturedRenderPlan.PendingMesh(seed.streamId(), seed.renderType(), List.copyOf(vertices), order));
		}
		return List.copyOf(meshes);
	}

	static boolean sharesEdge(List<SurgicalRenderCapture.CapturedVertex> first, List<SurgicalRenderCapture.CapturedVertex> second) {
		int matches = 0;
		for (SurgicalRenderCapture.CapturedVertex left : first) {
			for (SurgicalRenderCapture.CapturedVertex right : second) {
				if (!samePosition(left, position(right)))
					continue;
				matches++;
				break;
			}
		}
		return matches >= 2;
	}

	static boolean sharesCapturedTexture(RenderType renderType, List<SurgicalCapturedRenderPlan.ComponentBuilder> components) {
		ResourceLocation texture = SurgicalMaterialBinding.renderTypeTexture(renderType);
		if (texture == null)
			return false;
		for (SurgicalCapturedRenderPlan.ComponentBuilder component : components)
			for (SurgicalCapturedRenderPlan.SourceBatch batch : component.batches)
				if (Objects.equals(texture, SurgicalMaterialBinding.renderTypeTexture(batch.renderType())))
					return true;
		return false;
	}

	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCuboid recoverCuboid(List<SurgicalRenderCapture.CapturedVertex> vertices) {
		List<Vector3f> points = uniquePositions(vertices);
		if (points.size() == 8)
			return recoverSolid(vertices, points);
		if (points.size() == 4)
			return recoverFlat(vertices, points);
		return null;
	}

	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCuboid recoverSolid(List<SurgicalRenderCapture.CapturedVertex> vertices, List<Vector3f> points) {
		// A ModelPart cube emits each face as an ordered quad. Deriving the first two
		// axes from that order keeps the slime UV basis attached to the source cube.
		// Choosing the numerically "best" equivalent corner basis made the axes swap
		// as animated parts rotated and their floating-point errors changed.
		Vector3f origin = position(vertices.get(0));
		Vector3f a = position(vertices.get(1)).sub(origin);
		Vector3f b = position(vertices.get(3)).sub(origin);
		if (new Vector3f(a).cross(b).lengthSquared() <= 1.0e-8f)
			return null;

		for (Vector3f point : points) {
			Vector3f c = new Vector3f(point).sub(origin);
			if (Math.abs(new Vector3f(a).cross(b).dot(c)) <= 1.0e-8f)
				continue;
			List<Vector3f> corners = parallelepiped(origin, a, b, c);
			if (samePointSet(corners, points))
				return new SurgicalCapturedRenderPlan.RecoveredCuboid(corners, a, b, c);
		}
		return null;
	}

	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCuboid recoverFlat(List<SurgicalRenderCapture.CapturedVertex> vertices, List<Vector3f> points) {
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			Vector3f origin = position(vertices.get(cursor));
			Vector3f a = position(vertices.get(cursor + 1)).sub(origin);
			Vector3f b = position(vertices.get(cursor + 3)).sub(origin);
			if (new Vector3f(a).cross(b).lengthSquared() <= 1.0e-8f)
				continue;
			List<Vector3f> face = List.of(new Vector3f(origin), new Vector3f(origin).add(a),
				new Vector3f(origin).add(b), new Vector3f(origin).add(a).add(b));
			if (!samePointSet(face, points))
				continue;
			Vector3f zero = new Vector3f();
			return new SurgicalCapturedRenderPlan.RecoveredCuboid(parallelepiped(origin, a, b, zero), a, b, zero);
		}
		return null;
	}

	/**
	 * Fits a deterministic model-local oriented bounding box. The first non-degenerate quad supplies
	 * the local basis, which keeps rotated accessories tight instead of expanding them to renderer
	 * axes. The captured source vertices remain unchanged; this box is only surgical topology.
	 */
	@Nullable
	static SurgicalCapturedRenderPlan.RecoveredCuboid syntheticBounds(List<SurgicalRenderCapture.CapturedVertex> vertices) {
		if (vertices.size() < 4)
			return null;
		Vector3f reference = null;
		Vector3f u = null;
		Vector3f v = null;
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			Vector3f candidateReference = position(vertices.get(cursor));
			Vector3f candidateU = position(vertices.get(cursor + 1)).sub(candidateReference);
			if (candidateU.lengthSquared() <= 1.0e-8f)
				continue;
			candidateU.normalize();
			Vector3f candidateV = position(vertices.get(cursor + 3)).sub(candidateReference);
			candidateV.sub(new Vector3f(candidateU).mul(candidateV.dot(candidateU)));
			if (candidateV.lengthSquared() <= 1.0e-8f) {
				candidateV = position(vertices.get(cursor + 2)).sub(candidateReference);
				candidateV.sub(new Vector3f(candidateU).mul(candidateV.dot(candidateU)));
			}
			if (candidateV.lengthSquared() <= 1.0e-8f)
				continue;
			reference = candidateReference;
			u = candidateU;
			v = candidateV.normalize();
			break;
		}
		if (reference == null || u == null || v == null)
			return null;

		Vector3f w = new Vector3f(u).cross(v);
		if (w.lengthSquared() <= 1.0e-8f)
			return null;
		w.normalize();
		Vector3f[] axes = { u, v, w };
		float[] minimum = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY };
		float[] maximum = { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };
		for (SurgicalRenderCapture.CapturedVertex vertex : vertices) {
			Vector3f relative = position(vertex).sub(reference);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				minimum[axis] = Math.min(minimum[axis], projection);
				maximum[axis] = Math.max(maximum[axis], projection);
			}
		}
		if (maximum[0] - minimum[0] <= SurgicalCapturedRenderPlan.POSITION_EPSILON
			|| maximum[1] - minimum[1] <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
			return null;

		Vector3f origin = new Vector3f(reference);
		for (int axis = 0; axis < 3; axis++)
			origin.add(new Vector3f(axes[axis]).mul(minimum[axis]));
		Vector3f a = new Vector3f(u).mul(maximum[0] - minimum[0]);
		Vector3f b = new Vector3f(v).mul(maximum[1] - minimum[1]);
		float depth = maximum[2] - minimum[2];
		Vector3f c = depth <= SurgicalCapturedRenderPlan.POSITION_EPSILON ? new Vector3f() : new Vector3f(w).mul(depth);
		return new SurgicalCapturedRenderPlan.RecoveredCuboid(parallelepiped(origin, a, b, c), a, b, c);
	}

	static boolean isFlat(SurgicalCapturedRenderPlan.RecoveredCuboid cuboid) {
		return cuboid.a().length() <= SurgicalCapturedRenderPlan.POSITION_EPSILON
			|| cuboid.b().length() <= SurgicalCapturedRenderPlan.POSITION_EPSILON
			|| cuboid.c().length() <= SurgicalCapturedRenderPlan.POSITION_EPSILON;
	}

	static boolean isParallelogram(List<SurgicalRenderCapture.CapturedVertex> quad) {
		if (quad.size() != 4)
			return false;
		Vector3f firstDiagonal = position(quad.get(0)).add(position(quad.get(2)));
		Vector3f secondDiagonal = position(quad.get(1)).add(position(quad.get(3)));
		return firstDiagonal.distanceSquared(secondDiagonal)
			<= SurgicalCapturedRenderPlan.POSITION_EPSILON * SurgicalCapturedRenderPlan.POSITION_EPSILON * 16.0f;
	}

	@Nullable
	static Vector3f quadNormal(List<SurgicalRenderCapture.CapturedVertex> quad) {
		if (quad.size() != 4)
			return null;
		Vector3f captured = new Vector3f();
		for (SurgicalRenderCapture.CapturedVertex vertex : quad)
			captured.add(vertex.normalX(), vertex.normalY(), vertex.normalZ());
		if (captured.lengthSquared() > 1.0e-8f)
			return captured.normalize();
		Vector3f origin = position(quad.get(0));
		Vector3f normal = position(quad.get(1)).sub(origin)
			.cross(position(quad.get(3)).sub(origin));
		if (normal.lengthSquared() <= 1.0e-8f)
			return null;
		return normal.normalize();
	}

	static boolean quadOnBoundsFace(List<SurgicalRenderCapture.CapturedVertex> quad, SurgicalCapturedRenderPlan.RecoveredCuboid bounds) {
		Vector3f origin = bounds.corners().getFirst();
		Vector3f[] edges = { bounds.a(), bounds.b(), bounds.c() };
		Vector3f[] axes = new Vector3f[3];
		float[] lengths = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			lengths[axis] = edges[axis].length();
			if (lengths[axis] <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return false;
			axes[axis] = new Vector3f(edges[axis]).div(lengths[axis]);
		}

		float[][] ranges = SurgicalMaterialBinding.projectedRanges(quad, origin, axes);
		boolean onFace = false;
		for (int axis = 0; axis < 3; axis++) {
			boolean atMinimum = Math.abs(ranges[axis][0]) <= SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f
				&& Math.abs(ranges[axis][1]) <= SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f;
			boolean atMaximum = Math.abs(ranges[axis][0] - lengths[axis]) <= SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f
				&& Math.abs(ranges[axis][1] - lengths[axis]) <= SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f;
			onFace |= atMinimum || atMaximum;
		}
		if (!onFace)
			return false;
		for (SurgicalRenderCapture.CapturedVertex vertex : quad) {
			Vector3f relative = position(vertex).sub(origin);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				if (Math.abs(projection) > SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f
					&& Math.abs(projection - lengths[axis]) > SurgicalCapturedRenderPlan.POSITION_EPSILON * 4.0f)
					return false;
			}
		}
		return true;
	}

	static Vector3f position(SurgicalRenderCapture.CapturedVertex vertex) {
		return new Vector3f(vertex.x(), vertex.y(), vertex.z());
	}

	static List<Vector3f> parallelepiped(Vector3f origin, Vector3f a, Vector3f b, Vector3f c) {
		return List.of(
			new Vector3f(origin),
			new Vector3f(origin).add(a),
			new Vector3f(origin).add(b),
			new Vector3f(origin).add(a).add(b),
			new Vector3f(origin).add(c),
			new Vector3f(origin).add(a).add(c),
			new Vector3f(origin).add(b).add(c),
			new Vector3f(origin).add(a).add(b).add(c));
	}

	static List<Vector3f> uniquePositions(List<SurgicalRenderCapture.CapturedVertex> vertices) {
		List<Vector3f> points = new ArrayList<>(8);
		for (SurgicalRenderCapture.CapturedVertex vertex : vertices) {
			Vector3f point = new Vector3f(vertex.x(), vertex.y(), vertex.z());
			if (findPoint(points, point) == null)
				points.add(point);
		}
		return points;
	}

	static boolean samePointSet(List<Vector3f> first, List<Vector3f> second) {
		if (first.size() != second.size())
			return false;
		for (Vector3f point : first)
			if (findPoint(second, point) == null)
				return false;
		return true;
	}

	@Nullable
	static Vector3f findPoint(List<Vector3f> points, Vector3f target) {
		for (Vector3f point : points)
			if (Math.abs(point.x - target.x) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(point.y - target.y) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(point.z - target.z) <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return point;
		return null;
	}

	/**
	 * @param targetKey the caller's already-computed key for {@code cuboid}; recomputing it here, and
	 *   recomputing every observed cube's key on every call, made this quadratic in stream pipelines.
	 */
	static SurgicalCapturedRenderPlan.ModelCubeMatch matchingModelCube(SurgicalCapturedRenderPlan.RecoveredCuboid cuboid, SurgicalCapturedRenderPlan.GeometryKey targetKey,
		List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes) {
		for (SurgicalCapturedRenderPlan.ObservedCube observed : observedCubes) {
			if (!targetKey.equals(observed.key))
				continue;
			List<Vec3> ordered = new ArrayList<>(8);
			for (Vector3f corner : cuboid.corners()) {
				int match = matchingCorner(observed.transformedCorners, corner);
				if (match < 0) {
					ordered.clear();
					break;
				}
				ordered.add(observed.modelCorners.get(match));
			}
			if (ordered.size() == 8)
				return new SurgicalCapturedRenderPlan.ModelCubeMatch(ordered, observed.head, observed.partNames, observed.nodes);
		}
		return SurgicalCapturedRenderPlan.ModelCubeMatch.NONE;
	}

	static int matchingCorner(List<Vector3f> corners, Vector3f target) {
		for (int index = 0; index < corners.size(); index++) {
			Vector3f candidate = corners.get(index);
			if (Math.abs(candidate.x - target.x) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(candidate.y - target.y) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(candidate.z - target.z) <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return index;
		}
		return -1;
	}

	static List<SurgicalModelRenderContext.FaceGrid> recoverFaceGrids(SurgicalCapturedRenderPlan.RecoveredCuboid cuboid,
		RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices) {
		ResourceLocation texture = SurgicalMaterialBinding.renderTypeTexture(renderType);
		SurgicalTextureMasks.AlphaMask textureInfo = texture == null ? null : SurgicalTextureMasks.alphaMask(texture);
		if (textureInfo == null || textureInfo.width() <= 0 || textureInfo.height() <= 0 || vertices.size() < 4)
			return List.of();
		List<SurgicalModelRenderContext.FaceGrid> grids = new ArrayList<>(6);
		for (int[] face : SurgicalClientTopology.CUBE_FACES) {
			List<SurgicalRenderCapture.CapturedVertex> quad = matchingFaceQuad(cuboid.corners(), face, vertices);
			if (quad == null) {
				grids.add(new SurgicalModelRenderContext.FaceGrid(0.0d, 0.0d));
				continue;
			}
			SurgicalRenderCapture.CapturedVertex origin = vertexAt(quad, cuboid.corners().get(face[0]));
			SurgicalRenderCapture.CapturedVertex alongU = vertexAt(quad, cuboid.corners().get(face[1]));
			SurgicalRenderCapture.CapturedVertex alongV = vertexAt(quad, cuboid.corners().get(face[3]));
			if (origin == null || alongU == null || alongV == null) {
				grids.add(new SurgicalModelRenderContext.FaceGrid(0.0d, 0.0d));
				continue;
			}
			grids.add(new SurgicalModelRenderContext.FaceGrid(
				texturePixelDistance(origin, alongU, textureInfo.width(), textureInfo.height()),
				texturePixelDistance(origin, alongV, textureInfo.width(), textureInfo.height())));
		}
		return List.copyOf(grids);
	}

	@Nullable
	static List<SurgicalRenderCapture.CapturedVertex> matchingFaceQuad(List<Vector3f> corners, int[] face,
		List<SurgicalRenderCapture.CapturedVertex> vertices) {
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<SurgicalRenderCapture.CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			boolean matches = true;
			for (SurgicalRenderCapture.CapturedVertex vertex : quad) {
				boolean belongs = false;
				for (int corner : face) {
					if (samePosition(vertex, corners.get(corner))) {
						belongs = true;
						break;
					}
				}
				if (!belongs) {
					matches = false;
					break;
				}
			}
			if (matches)
				return quad;
		}
		return null;
	}

	@Nullable
	static SurgicalRenderCapture.CapturedVertex vertexAt(List<SurgicalRenderCapture.CapturedVertex> vertices, Vector3f position) {
		for (SurgicalRenderCapture.CapturedVertex vertex : vertices)
			if (samePosition(vertex, position))
				return vertex;
		return null;
	}

	static boolean samePosition(SurgicalRenderCapture.CapturedVertex vertex, Vector3f position) {
		return Math.abs(vertex.x() - position.x) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
			&& Math.abs(vertex.y() - position.y) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
			&& Math.abs(vertex.z() - position.z) <= SurgicalCapturedRenderPlan.POSITION_EPSILON;
	}

	static double texturePixelDistance(SurgicalRenderCapture.CapturedVertex first, SurgicalRenderCapture.CapturedVertex second,
		int textureWidth, int textureHeight) {
		double u = (second.u() - first.u()) * textureWidth;
		double v = (second.v() - first.v()) * textureHeight;
		return Math.hypot(u, v);
	}
}
