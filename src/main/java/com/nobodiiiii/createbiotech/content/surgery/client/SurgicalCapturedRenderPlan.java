package com.nobodiiiii.createbiotech.content.surgery.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCubeGeometry;
import com.nobodiiiii.createbiotech.content.slimemimic.client.SlimeMimicDeathClient;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.mixin.client.CompositeRenderStateAccessor;
import com.nobodiiiii.createbiotech.mixin.client.CompositeRenderTypeAccessor;
import com.nobodiiiii.createbiotech.mixin.client.TextureStateShardAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable, model-library-neutral output of one living-entity render.
 *
 * <p>The source renderer is executed once into a recording buffer. Complete and partial
 * cuboids are recovered from the final vertex stream, deduplicated across base and layer
 * passes, and assigned stable surgical ids. Remaining connected model-textured quad islands
 * receive a synthetic oriented bounding box unless they are an exact surface overlay.
 * Everything else is retained as original geometry. This deliberately observes only the public
 * {@link VertexConsumer} contract;
 * vanilla {@code ModelPart}, third-party model libraries and hand-written render layers require no
 * individual model bridge.</p>
 */
public final class SurgicalCapturedRenderPlan {
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final RenderType INNER_RENDER_TYPE = RenderType.entityCutoutNoCull(SLIME_TEXTURE);
	private static final RenderType OUTER_RENDER_TYPE = RenderType.entityTranslucent(SLIME_TEXTURE);
	// Record with a recognizable non-full-bright value so ambient lighting cannot be mistaken for
	// an emissive layer. Vanilla supplies full block light for burning entities; capturing that
	// value directly would otherwise make every recorded vertex permanently glow.
	private static final int CAPTURE_LIGHT = LightTexture.pack(7, 11);
	private static final float SLIME_CENTER_Y = 20.0f / 16.0f;
	/** Total render-only size removed from each local axis of the translucent slime shell. */
	private static final float OUTER_SHELL_SHRINK_CLOSE = 0.0005f;
	private static final float OUTER_SHELL_SHRINK_MID = 0.002f;
	private static final float OUTER_SHELL_SHRINK_FAR = 0.01f;
	private static final float OUTER_SHELL_MID_DISTANCE = 6.0f;
	private static final float OUTER_SHELL_MID_DISTANCE_SQR =
		OUTER_SHELL_MID_DISTANCE * OUTER_SHELL_MID_DISTANCE;
	private static final float OUTER_SHELL_FAR_DISTANCE = 16.0f;
	private static final float OUTER_SHELL_FAR_DISTANCE_SQR =
		OUTER_SHELL_FAR_DISTANCE * OUTER_SHELL_FAR_DISTANCE;
	private static final float THIN_EDGE = 0.05f / 16.0f;
	private static final float OVERLAY_EXPANSION_MAX = 1.1f / 16.0f;
	private static final float OVERLAY_CENTER_EPSILON = 0.1f / 16.0f;
	private static final float PARALLEL_DOT_MIN = 0.999f;
	private static final float SYNTHETIC_PARALLEL_DOT_MIN = 0.995f;
	private static final float POSITION_EPSILON = 2.0e-5f;
	private static final float POSITION_QUANTIZATION = 100_000.0f;

	private static final Map<ResourceLocation, CachedAlphaMask> ALPHA_MASKS = new ConcurrentHashMap<>();
	private static volatile int resourceGeneration;
	private static final ThreadLocal<Deque<List<ObservedCube>>> MODEL_CUBE_CAPTURES =
		ThreadLocal.withInitial(ArrayDeque::new);
	private static volatile int activeModelCubeCaptureCount;
	private static ModelPart innerCube;
	private static ModelPart outerCube;
	private static final BitSet ALL_COMPONENTS = new BitSet();
	private static final Map<Integer, Vec3> NO_OFFSETS = Map.of();
	private static final Map<Integer, SurgicalCubeRotation> NO_ROTATIONS = Map.of();
	/** Render-thread only; cleared every frame by {@link #beginFrame()}. */
	private static final Map<LivingEntity, LiveFrame> LIVE_FRAMES = new IdentityHashMap<>();

	private final List<Component> components;
	private final List<SourceBatch> extras;

	private SurgicalCapturedRenderPlan(List<Component> components, List<SourceBatch> extras) {
		this.components = List.copyOf(components);
		this.extras = List.copyOf(extras);
	}

	/**
	 * @param topology whether the caller will read {@code modelCorners} and {@code faceGrids} off the
	 *   resulting components. Only {@link #snapshot} exposes them, so the live and death paths pass
	 *   {@code false} and skip both the per-cube model observation and the recovery work behind it.
	 */
	static SurgicalCapturedRenderPlan capture(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		return build(captureInput(renderer, preview, yaw, partialTick, topology));
	}

	static CapturedInput captureInput(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		RecordingBuffer recording = new RecordingBuffer();
		PoseStack neutralPose = new PoseStack();
		List<ObservedCube> observedCubes = topology ? new ArrayList<>() : List.of();
		// Leaving the counter alone keeps observeModelCube - which the mixin runs for every cube of
		// every entity model in the game - on its single-field-read rejection for this capture.
		Deque<List<ObservedCube>> captures = null;
		if (topology) {
			captures = MODEL_CUBE_CAPTURES.get();
			captures.push(observedCubes);
			activeModelCubeCaptureCount++;
		}
		try {
			renderer.render(preview, yaw, partialTick, neutralPose, recording, CAPTURE_LIGHT);
		} finally {
			recording.finish();
			if (captures != null) {
				captures.pop();
				activeModelCubeCaptureCount = Math.max(0, activeModelCubeCaptureCount - 1);
				if (captures.isEmpty())
					MODEL_CUBE_CAPTURES.remove();
			}
		}
		return new CapturedInput(List.copyOf(recording.streams), List.copyOf(observedCubes), topology);
	}

	static SurgicalCapturedRenderPlan build(CapturedInput captured) {
		return build(captured.streams, captured.observedCubes, captured.topology);
	}

	/** Records unscaled ModelPart pixel bounds while the renderer emits the matching transformed vertices. */
	public static void observeModelCube(ModelPart.Cube cube, PoseStack.Pose pose) {
		if (activeModelCubeCaptureCount == 0)
			return;
		Deque<List<ObservedCube>> captures = MODEL_CUBE_CAPTURES.get();
		if (captures.isEmpty())
			return;
		List<Vector3f> transformed = new ArrayList<>(8);
		List<Vec3> model = new ArrayList<>(8);
		Matrix4f matrix = pose.pose();
		for (int z = 0; z < 2; z++) {
			for (int y = 0; y < 2; y++) {
				for (int x = 0; x < 2; x++) {
					Vec3 source = new Vec3(x == 0 ? cube.minX : cube.maxX,
						y == 0 ? cube.minY : cube.maxY, z == 0 ? cube.minZ : cube.maxZ);
					model.add(source);
					transformed.add(matrix.transformPosition(new Vector3f((float) (source.x / 16.0d),
						(float) (source.y / 16.0d), (float) (source.z / 16.0d))));
				}
			}
		}
		captures.peek().add(new ObservedCube(transformed, model));
	}

	/**
	 * Replaces a live entity's complete renderer output without knowing which model
	 * library produced it. This entry point is intentionally above
	 * {@code LivingEntityRenderer}: independently owned layer models are part of the
	 * same capture and therefore become normal, separately recoverable components.
	 */
	@SuppressWarnings("unchecked")
	public static boolean tryRenderSlimeMimic(EntityRenderer<?> renderer, LivingEntity entity, float yaw,
		float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (entity instanceof SlimeBionicEntity || !SlimeMimicHandler.isSlimeMimic(entity)
			|| entity.isInvisible())
			return false;
		if (entity.isDeadOrDying() && SlimeMimicDeathClient.hasReported(entity))
			return true;
		SurgicalCapturedRenderPlan frame = liveFrame((EntityRenderer<LivingEntity>) renderer, entity,
			yaw, partialTick);
		if (entity.isDeadOrDying()) {
			SlimeMimicDeathClient.report(entity, frame.deathGeometry(0, entity.position()));
			return true;
		}
		frame.render(poseStack, buffer, packedLight, 0, ALL_COMPONENTS, NO_OFFSETS, NO_ROTATIONS,
			false, null, false);
		return true;
	}

	/**
	 * A live mimic's pose changes every frame, so its plan cannot be cached across frames - but the
	 * entity renderer runs more than once within a frame (a shadow pass under Iris/Oculus is the
	 * common case), and every one of those passes rebuilds an identical plan. Reusing the plan for a
	 * repeated (entity, yaw, partialTick) inside one frame is therefore free of visual difference.
	 */
	private static SurgicalCapturedRenderPlan liveFrame(EntityRenderer<LivingEntity> renderer,
		LivingEntity entity, float yaw, float partialTick) {
		LiveFrame cached = LIVE_FRAMES.get(entity);
		if (cached != null && cached.yaw == yaw && cached.partialTick == partialTick
			&& cached.renderer == renderer)
			return cached.plan;
		SurgicalCapturedRenderPlan plan = capture(renderer, entity, yaw, partialTick, false);
		LIVE_FRAMES.put(entity, new LiveFrame(renderer, yaw, partialTick, plan));
		return plan;
	}

	/** Drops the per-frame live mimic plans; called once per frame before any entity is drawn. */
	static void beginFrame() {
		LIVE_FRAMES.clear();
	}

	private record LiveFrame(EntityRenderer<LivingEntity> renderer, float yaw, float partialTick,
		SurgicalCapturedRenderPlan plan) {}

	int cubeCount() {
		return components.size();
	}

	SurgicalModelRenderContext.Snapshot render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int expectedCubeCount, BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations,
		boolean collectGeometry, @Nullable Vec3 cameraPosition, boolean renderSourceGeometry) {
		return render(poseStack, buffer, packedLight, expectedCubeCount, presentCubes, cubeOffsets,
			cubeRotations, collectGeometry, cameraPosition, renderSourceGeometry, true);
	}

	private SurgicalModelRenderContext.Snapshot render(PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int expectedCubeCount, BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, boolean collectGeometry,
		@Nullable Vec3 cameraPosition, boolean renderSourceGeometry, boolean renderExtras) {
		if (renderSourceGeometry) {
			for (Component component : components)
				if (isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSource(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), true);
		} else {
			float outerShellShrink = outerShellShrink(poseStack.last().pose());
			for (Component component : components) {
				if (!isPresent(component.id, expectedCubeCount, presentCubes))
					continue;
				if (component.preserveSource)
					component.renderSource(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), false);
				else
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), false, outerShellShrink);
			}
			for (Component component : components)
				if (!component.preserveSource && isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), true, outerShellShrink);
			// Villager professions, emissive eyes and similar layers intentionally redraw the
			// same model cube with another material. Keep those pixels attached to the one
			// surgical component instead of admitting duplicate topology or discarding them.
			for (Component component : components)
				if (!component.preserveSource && isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSurfaceOverlays(poseStack, buffer, packedLight,
						cubeOffsets.get(component.id), cubeRotations.get(component.id));
		}

		// Lines, text, beams and genuinely non-cuboid meshes are visual effects rather than
		// surgical topology. Preserve them exactly and keep them out of cube numbering.
		if (renderExtras)
			for (SourceBatch extra : extras)
				extra.render(poseStack, buffer, packedLight, null);

		if (!collectGeometry)
			return new SurgicalModelRenderContext.Snapshot(components.size(), List.of());
		return snapshot(poseStack, expectedCubeCount, presentCubes, cubeOffsets, cubeRotations, cameraPosition);
	}

	SurgicalModelRenderContext.Snapshot renderSingleCube(PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int cube) {
		if (cube < 0 || cube >= components.size())
			return new SurgicalModelRenderContext.Snapshot(0, List.of());
		BitSet present = new BitSet(components.size());
		present.set(cube);
		return render(poseStack, buffer, packedLight, components.size(), present, NO_OFFSETS,
			NO_ROTATIONS, false, null, false, false);
	}

	@Nullable
	SurgicalModelRenderContext.CubeGeometry singleCubeGeometry(int cube) {
		if (cube < 0 || cube >= components.size())
			return null;
		return components.get(cube).geometry(new PoseStack(), null, null, null);
	}

	private List<SlimeMimicCubeGeometry> deathGeometry(int source, Vec3 worldOrigin) {
		List<SlimeMimicCubeGeometry> geometry = new ArrayList<>(components.size());
		PoseStack poseStack = new PoseStack();
		for (Component component : components)
			geometry.add(new SlimeMimicCubeGeometry(source, component.id,
				component.geometry(poseStack, null, null, worldOrigin).corners()));
		return List.copyOf(geometry);
	}

	SurgicalModelRenderContext.Snapshot snapshot(PoseStack poseStack, int expectedCubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, @Nullable Vec3 cameraPosition) {
		List<SurgicalModelRenderContext.CubeGeometry> geometry = new ArrayList<>(components.size());
		for (Component component : components) {
			if (!isPresent(component.id, expectedCubeCount, presentCubes))
				continue;
			Vec3 offset = cubeOffsets.get(component.id);
			geometry.add(component.geometry(poseStack, offset, cubeRotations.get(component.id), cameraPosition));
		}
		return new SurgicalModelRenderContext.Snapshot(components.size(), geometry);
	}

	static void clearResources() {
		resourceGeneration++;
		ALPHA_MASKS.clear();
		innerCube = null;
		outerCube = null;
	}

	private static SurgicalCapturedRenderPlan build(List<CaptureStream> streams,
		List<ObservedCube> observedCubes, boolean topology) {
		Map<GeometryKey, List<ComponentBuilder>> recovered = new LinkedHashMap<>();
		List<SourceBatch> extras = new ArrayList<>();
		List<PendingQuad> pendingQuads = new ArrayList<>();
		int order = 0;

		for (CaptureStream stream : streams) {
			List<CapturedVertex> vertices = stream.vertices;
			if (stream.renderType.mode() != VertexFormat.Mode.QUADS) {
				if (!vertices.isEmpty())
					extras.add(new SourceBatch(stream.renderType, List.copyOf(vertices), false));
				continue;
			}

			int cursor = 0;
			while (cursor + 4 <= vertices.size()) {
				RecoveredCandidate candidate = recoverCandidate(vertices, cursor);
				if (candidate == null) {
					List<CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
					if (quadVisible(stream.renderType, quad))
						pendingQuads.add(new PendingQuad(stream.id, stream.renderType,
							List.copyOf(quad), order++));
					cursor += 4;
					continue;
				}

				List<CapturedVertex> candidateVertices = candidate.vertices;
				RecoveredCuboid cuboid = candidate.cuboid;
				GeometryKey key = GeometryKey.of(cuboid.corners);
				List<ComponentBuilder> matches = recovered.computeIfAbsent(key, ignored -> new ArrayList<>());
				ComponentBuilder builder = matches.stream()
					.filter(match -> !match.captureStreams.get(stream.id))
					.findFirst()
					.orElse(null);
				if (builder == null) {
					builder = new ComponentBuilder(order++, cuboid,
						topology ? matchingModelCorners(cuboid, key, observedCubes) : List.of(), topology);
					matches.add(builder);
				}
				builder.captureStreams.set(stream.id);
				builder.observeMaterial(stream.renderType);
				if (hasVisibleQuad(stream.renderType, candidateVertices))
					builder.addVisibleBatch(stream.renderType, candidateVertices);
				cursor += candidateVertices.size();
			}
			if (cursor < vertices.size())
				extras.add(new SourceBatch(stream.renderType,
					List.copyOf(vertices.subList(cursor, vertices.size())), false));
		}

		List<ComponentBuilder> recoveredBuilders = recovered.values().stream()
			.flatMap(List::stream)
			.toList();
		for (PendingMesh mesh : connectedPendingMeshes(pendingQuads)) {
			ComponentBuilder overlayOwner = surfaceOverlayOwner(mesh.vertices, recoveredBuilders);
			if (overlayOwner != null) {
				overlayOwner.addSurfaceOverlayBatch(mesh.renderType, mesh.vertices);
				continue;
			}

			RecoveredCuboid bounds = syntheticBounds(mesh.vertices);
			if (bounds == null || isFlat(bounds) && !sharesCapturedTexture(mesh.renderType, recoveredBuilders)) {
				extras.add(new SourceBatch(mesh.renderType, mesh.vertices, false));
				continue;
			}

			GeometryKey key = GeometryKey.of(bounds.corners);
			List<ComponentBuilder> matches = recovered.computeIfAbsent(key, ignored -> new ArrayList<>());
			ComponentBuilder builder = matches.stream()
				.filter(candidate -> !candidate.captureStreams.get(mesh.streamId))
				.findFirst()
				.orElse(null);
			if (builder == null) {
				builder = new ComponentBuilder(mesh.order, bounds,
					topology ? matchingModelCorners(bounds, key, observedCubes) : List.of(), topology);
				matches.add(builder);
				recoveredBuilders = recovered.values().stream().flatMap(List::stream).toList();
			}
			builder.captureStreams.set(mesh.streamId);
			builder.observeMaterial(mesh.renderType);
			builder.addVisibleBatch(mesh.renderType, mesh.vertices);
		}

		List<ComponentBuilder> visible = recovered.values().stream()
			.flatMap(List::stream)
			.filter(builder -> !builder.batches.isEmpty())
			.sorted(Comparator.comparingInt(builder -> builder.order))
			.toList();
		List<Component> components = new ArrayList<>(visible.size());
		for (int id = 0; id < visible.size(); id++) {
			ComponentBuilder builder = visible.get(id);
			components.add(builder.build(id, shouldPreserveSource(builder, visible)));
		}
		return new SurgicalCapturedRenderPlan(components, extras);
	}

	/**
	 * A normal model cube contributes six consecutive quads. Some renderers omit hidden or
	 * unavailable faces, so also accept a shorter prefix when its vertices still describe faces of
	 * one three-dimensional oriented box. A lone quad is deliberately left for the overlay/model
	 * texture classifier: promoting every glyph and nameplate quad would turn effects into topology.
	 */
	@Nullable
	private static RecoveredCandidate recoverCandidate(List<CapturedVertex> vertices, int cursor) {
		int maxFaces = Math.min(6, (vertices.size() - cursor) / 4);
		for (int faces = maxFaces; faces >= 2; faces--) {
			List<CapturedVertex> candidate = vertices.subList(cursor, cursor + faces * 4);
			RecoveredCuboid cuboid = recoverCuboid(candidate);
			if (faces < 6 && cuboid != null
				&& (isFlat(cuboid) || !facesBelongToOneBox(candidate)))
				cuboid = null;
			if (cuboid == null)
				cuboid = recoverPartialCuboid(candidate);
			if (cuboid != null)
				return new RecoveredCandidate(cuboid, List.copyOf(candidate));
		}
		return null;
	}

	@Nullable
	private static RecoveredCuboid recoverPartialCuboid(List<CapturedVertex> vertices) {
		RecoveredCuboid bounds = syntheticBounds(vertices);
		if (bounds == null || isFlat(bounds) || !facesBelongToOneBox(vertices))
			return null;
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			if (!isParallelogram(quad) || !quadOnBoundsFace(quad, bounds))
				return null;
		}
		return bounds;
	}

	private static boolean facesBelongToOneBox(List<CapturedVertex> vertices) {
		int faceCount = vertices.size() / 4;
		if (faceCount < 2)
			return false;
		if (faceCount == 2) {
			List<CapturedVertex> first = vertices.subList(0, 4);
			List<CapturedVertex> second = vertices.subList(4, 8);
			if (sharesEdge(first, second))
				return true;
			Vector3f firstNormal = quadNormal(first);
			Vector3f secondNormal = quadNormal(second);
			return firstNormal != null && secondNormal != null
				&& firstNormal.dot(secondNormal) <= -SYNTHETIC_PARALLEL_DOT_MIN;
		}

		BitSet connected = new BitSet(faceCount);
		connected.set(0);
		for (int pass = 0; pass < faceCount; pass++) {
			boolean changed = false;
			for (int known = connected.nextSetBit(0); known >= 0; known = connected.nextSetBit(known + 1)) {
				List<CapturedVertex> knownFace = vertices.subList(known * 4, known * 4 + 4);
				for (int candidate = 0; candidate < faceCount; candidate++) {
					if (connected.get(candidate))
						continue;
					List<CapturedVertex> candidateFace = vertices.subList(candidate * 4, candidate * 4 + 4);
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

	private static List<PendingMesh> connectedPendingMeshes(List<PendingQuad> quads) {
		List<PendingMesh> meshes = new ArrayList<>();
		BitSet claimed = new BitSet(quads.size());
		for (int start = 0; start < quads.size(); start++) {
			if (claimed.get(start))
				continue;
			PendingQuad seed = quads.get(start);
			claimed.set(start);
			List<Integer> open = new ArrayList<>();
			open.add(start);
			List<CapturedVertex> vertices = new ArrayList<>(seed.vertices);
			int order = seed.order;
			for (int openIndex = 0; openIndex < open.size(); openIndex++) {
				PendingQuad current = quads.get(open.get(openIndex));
				for (int candidateIndex = start + 1; candidateIndex < quads.size(); candidateIndex++) {
					if (claimed.get(candidateIndex))
						continue;
					PendingQuad candidate = quads.get(candidateIndex);
					if (candidate.streamId != seed.streamId
						|| !sharesEdge(current.vertices, candidate.vertices))
						continue;
					claimed.set(candidateIndex);
					open.add(candidateIndex);
					vertices.addAll(candidate.vertices);
					order = Math.min(order, candidate.order);
				}
			}
			meshes.add(new PendingMesh(seed.streamId, seed.renderType, List.copyOf(vertices), order));
		}
		return List.copyOf(meshes);
	}

	private static boolean sharesEdge(List<CapturedVertex> first, List<CapturedVertex> second) {
		int matches = 0;
		for (CapturedVertex left : first) {
			for (CapturedVertex right : second) {
				if (!samePosition(left, position(right)))
					continue;
				matches++;
				break;
			}
		}
		return matches >= 2;
	}

	private static boolean sharesCapturedTexture(RenderType renderType, List<ComponentBuilder> components) {
		ResourceLocation texture = renderTypeTexture(renderType);
		if (texture == null)
			return false;
		for (ComponentBuilder component : components)
			for (SourceBatch batch : component.batches)
				if (Objects.equals(texture, renderTypeTexture(batch.renderType)))
					return true;
		return false;
	}

	@Nullable
	private static ComponentBuilder surfaceOverlayOwner(List<CapturedVertex> vertices,
		List<ComponentBuilder> components) {
		ComponentBuilder best = null;
		float bestScore = Float.POSITIVE_INFINITY;
		for (ComponentBuilder component : components) {
			if (component.batches.isEmpty())
				continue;
			float score = surfaceOverlayScore(vertices, component.cuboid);
			if (score < bestScore) {
				best = component;
				bestScore = score;
			}
		}
		return best;
	}

	private static float surfaceOverlayScore(List<CapturedVertex> vertices, RecoveredCuboid cuboid) {
		Vector3f origin = cuboid.corners.getFirst();
		Vector3f[] edges = { cuboid.a, cuboid.b, cuboid.c };
		Vector3f[] axes = new Vector3f[3];
		float[] lengths = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			lengths[axis] = edges[axis].length();
			if (lengths[axis] <= POSITION_EPSILON)
				return Float.POSITIVE_INFINITY;
			axes[axis] = new Vector3f(edges[axis]).div(lengths[axis]);
		}

		float score = 0.0f;
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			Vector3f normal = quadNormal(quad);
			if (normal == null)
				return Float.POSITIVE_INFINITY;
			int planeAxis = -1;
			float bestParallel = 0.0f;
			for (int axis = 0; axis < 3; axis++) {
				float parallel = Math.abs(normal.dot(axes[axis]));
				if (parallel > bestParallel) {
					bestParallel = parallel;
					planeAxis = axis;
				}
			}
			if (bestParallel < SYNTHETIC_PARALLEL_DOT_MIN)
				return Float.POSITIVE_INFINITY;

			float[][] ranges = projectedRanges(quad, origin, axes);
			float planeMin = ranges[planeAxis][0];
			float planeMax = ranges[planeAxis][1];
			if (planeMax - planeMin > OVERLAY_CENTER_EPSILON)
				return Float.POSITIVE_INFINITY;
			float plane = (planeMin + planeMax) * 0.5f;
			float distance = Math.min(Math.abs(plane), Math.abs(plane - lengths[planeAxis]));
			if (distance > OVERLAY_CENTER_EPSILON)
				return Float.POSITIVE_INFINITY;
			for (int axis = 0; axis < 3; axis++) {
				if (axis == planeAxis)
					continue;
				if (ranges[axis][0] < -OVERLAY_EXPANSION_MAX
					|| ranges[axis][1] > lengths[axis] + OVERLAY_EXPANSION_MAX
					|| ranges[axis][1] < -POSITION_EPSILON
					|| ranges[axis][0] > lengths[axis] + POSITION_EPSILON)
					return Float.POSITIVE_INFINITY;
			}
			score += distance;
		}
		return score;
	}

	private static float[][] projectedRanges(List<CapturedVertex> vertices, Vector3f origin,
		Vector3f[] axes) {
		float[][] ranges = {
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY },
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY },
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }
		};
		for (CapturedVertex vertex : vertices) {
			Vector3f relative = position(vertex).sub(origin);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				ranges[axis][0] = Math.min(ranges[axis][0], projection);
				ranges[axis][1] = Math.max(ranges[axis][1], projection);
			}
		}
		return ranges;
	}

	/**
	 * Besides truly flat boxes, keep close-fitting shells in their source material.
	 * Vanilla clothing is usually authored as a normal cuboid inflated by 0.25-0.5
	 * pixels (the villager jacket is additionally extended downwards), so testing only
	 * the shortest edge would incorrectly turn it into a second solid slime body.
	 */
	private static boolean shouldPreserveSource(ComponentBuilder candidate, List<ComponentBuilder> components) {
		RecoveredCuboid cuboid = candidate.cuboid;
		float shortestEdge = Math.min(cuboid.a.length(), Math.min(cuboid.b.length(), cuboid.c.length()));
		if (shortestEdge <= THIN_EDGE)
			return true;

		for (ComponentBuilder base : components) {
			if (base.order >= candidate.order || base == candidate)
				continue;
			if (isCloseFittingOverlay(cuboid, base.cuboid))
				return true;
		}
		return false;
	}

	private static boolean isCloseFittingOverlay(RecoveredCuboid candidate, RecoveredCuboid base) {
		Vector3f[] candidateEdges = { candidate.a, candidate.b, candidate.c };
		Vector3f[] baseEdges = { base.a, base.b, base.c };
		boolean[] usedBaseEdges = new boolean[3];
		Vector3f centerDelta = center(candidate).sub(center(base));
		float candidateVolume = 1.0f;
		float baseVolume = 1.0f;
		int closeExpandedAxes = 0;

		for (Vector3f candidateEdge : candidateEdges) {
			float candidateLength = candidateEdge.length();
			if (candidateLength <= POSITION_EPSILON)
				return false;
			Vector3f candidateAxis = new Vector3f(candidateEdge).div(candidateLength);
			int match = -1;
			for (int baseIndex = 0; baseIndex < baseEdges.length; baseIndex++) {
				if (usedBaseEdges[baseIndex])
					continue;
				float baseLength = baseEdges[baseIndex].length();
				if (baseLength <= POSITION_EPSILON)
					continue;
				float dot = Math.abs(candidateAxis.dot(new Vector3f(baseEdges[baseIndex]).div(baseLength)));
				if (dot >= PARALLEL_DOT_MIN) {
					match = baseIndex;
					break;
				}
			}
			if (match < 0)
				return false;

			usedBaseEdges[match] = true;
			float baseLength = baseEdges[match].length();
			float axialCenterDelta = Math.abs(centerDelta.dot(candidateAxis));
			if (axialCenterDelta > (candidateLength + baseLength) * 0.5f + POSITION_EPSILON)
				return false;

			float expansion = candidateLength - baseLength;
			if (expansion > POSITION_EPSILON && expansion <= OVERLAY_EXPANSION_MAX
				&& axialCenterDelta <= OVERLAY_CENTER_EPSILON)
				closeExpandedAxes++;
			candidateVolume *= candidateLength;
			baseVolume *= baseLength;
		}

		return closeExpandedAxes >= 2 && candidateVolume > baseVolume + POSITION_EPSILON;
	}

	private static Vector3f center(RecoveredCuboid cuboid) {
		return new Vector3f(cuboid.corners.getFirst())
			.add(new Vector3f(cuboid.a).mul(0.5f))
			.add(new Vector3f(cuboid.b).mul(0.5f))
			.add(new Vector3f(cuboid.c).mul(0.5f));
	}

	private static boolean hasVisibleQuad(RenderType renderType, List<CapturedVertex> vertices) {
		for (int vertex = 0; vertex + 4 <= vertices.size(); vertex += 4)
			if (quadVisible(renderType, vertices.subList(vertex, vertex + 4)))
				return true;
		return false;
	}

	private static boolean quadVisible(RenderType renderType, List<CapturedVertex> quad) {
		boolean vertexVisible = false;
		float minU = Float.POSITIVE_INFINITY;
		float minV = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY;
		float maxV = Float.NEGATIVE_INFINITY;
		for (CapturedVertex vertex : quad) {
			vertexVisible |= vertex.alpha > 0;
			minU = Math.min(minU, vertex.u);
			minV = Math.min(minV, vertex.v);
			maxU = Math.max(maxU, vertex.u);
			maxV = Math.max(maxV, vertex.v);
		}
		if (!vertexVisible)
			return false;
		ResourceLocation texture = renderTypeTexture(renderType);
		return texture == null || alphaMask(texture).hasVisiblePixels(minU, minV, maxU, maxV);
	}

	@Nullable
	private static ResourceLocation renderTypeTexture(RenderType renderType) {
		if (!(renderType instanceof CompositeRenderTypeAccessor compositeAccessor))
			return null;
		RenderType.CompositeState state = compositeAccessor.createBiotech$getState();
		CompositeRenderStateAccessor stateAccessor = (CompositeRenderStateAccessor) (Object) state;
		Object textureState = stateAccessor.createBiotech$getTextureState();
		TextureStateShardAccessor textureAccessor = (TextureStateShardAccessor) textureState;
		return textureAccessor.createBiotech$getTexture().orElse(null);
	}

	static RenderType translucentPreviewType(RenderType renderType) {
		if (renderType.mode() != VertexFormat.Mode.QUADS)
			return renderType;
		ResourceLocation texture = renderTypeTexture(renderType);
		return texture == null ? renderType : RenderType.entityTranslucent(texture);
	}

	private static AlphaMask alphaMask(ResourceLocation texture) {
		int generation = resourceGeneration;
		CachedAlphaMask cached = ALPHA_MASKS.get(texture);
		if (cached != null && cached.generation == generation)
			return cached.mask;
		AlphaMask loaded = loadAlphaMask(texture);
		if (generation != resourceGeneration)
			return loaded;
		CachedAlphaMask candidate = new CachedAlphaMask(generation, loaded);
		CachedAlphaMask stored = ALPHA_MASKS.compute(texture, (ignored, existing) -> {
			if (generation != resourceGeneration)
				return existing;
			return existing != null && existing.generation == generation ? existing : candidate;
		});
		if (generation != resourceGeneration) {
			ALPHA_MASKS.remove(texture, candidate);
			return loaded;
		}
		return stored == null ? loaded : stored.mask;
	}

	private static AlphaMask loadAlphaMask(ResourceLocation texture) {
		Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
		if (resource == null)
			return AlphaMask.OPAQUE;
		try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
			int width = image.getWidth();
			int height = image.getHeight();
			if (!image.format().hasAlpha())
				return new AlphaMask(width, height, new BitSet(), true);
			BitSet pixels = new BitSet(width * height);
			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++)
					if ((image.getPixelRGBA(x, y) >>> 24 & 0xff) != 0)
						pixels.set(y * width + x);
			return new AlphaMask(width, height, pixels, false);
		} catch (IOException | RuntimeException ignored) {
			return AlphaMask.OPAQUE;
		}
	}

	@Nullable
	private static RecoveredCuboid recoverCuboid(List<CapturedVertex> vertices) {
		List<Vector3f> points = uniquePositions(vertices);
		if (points.size() == 8)
			return recoverSolid(vertices, points);
		if (points.size() == 4)
			return recoverFlat(vertices, points);
		return null;
	}

	@Nullable
	private static RecoveredCuboid recoverSolid(List<CapturedVertex> vertices, List<Vector3f> points) {
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
				return new RecoveredCuboid(corners, a, b, c);
		}
		return null;
	}

	@Nullable
	private static RecoveredCuboid recoverFlat(List<CapturedVertex> vertices, List<Vector3f> points) {
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
			return new RecoveredCuboid(parallelepiped(origin, a, b, zero), a, b, zero);
		}
		return null;
	}

	/**
	 * Fits a deterministic model-local oriented bounding box. The first non-degenerate quad supplies
	 * the local basis, which keeps rotated accessories tight instead of expanding them to renderer
	 * axes. The captured source vertices remain unchanged; this box is only surgical topology.
	 */
	@Nullable
	private static RecoveredCuboid syntheticBounds(List<CapturedVertex> vertices) {
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
		for (CapturedVertex vertex : vertices) {
			Vector3f relative = position(vertex).sub(reference);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				minimum[axis] = Math.min(minimum[axis], projection);
				maximum[axis] = Math.max(maximum[axis], projection);
			}
		}
		if (maximum[0] - minimum[0] <= POSITION_EPSILON
			|| maximum[1] - minimum[1] <= POSITION_EPSILON)
			return null;

		Vector3f origin = new Vector3f(reference);
		for (int axis = 0; axis < 3; axis++)
			origin.add(new Vector3f(axes[axis]).mul(minimum[axis]));
		Vector3f a = new Vector3f(u).mul(maximum[0] - minimum[0]);
		Vector3f b = new Vector3f(v).mul(maximum[1] - minimum[1]);
		float depth = maximum[2] - minimum[2];
		Vector3f c = depth <= POSITION_EPSILON ? new Vector3f() : new Vector3f(w).mul(depth);
		return new RecoveredCuboid(parallelepiped(origin, a, b, c), a, b, c);
	}

	private static boolean isFlat(RecoveredCuboid cuboid) {
		return cuboid.a.length() <= POSITION_EPSILON
			|| cuboid.b.length() <= POSITION_EPSILON
			|| cuboid.c.length() <= POSITION_EPSILON;
	}

	private static boolean isParallelogram(List<CapturedVertex> quad) {
		if (quad.size() != 4)
			return false;
		Vector3f firstDiagonal = position(quad.get(0)).add(position(quad.get(2)));
		Vector3f secondDiagonal = position(quad.get(1)).add(position(quad.get(3)));
		return firstDiagonal.distanceSquared(secondDiagonal)
			<= POSITION_EPSILON * POSITION_EPSILON * 16.0f;
	}

	@Nullable
	private static Vector3f quadNormal(List<CapturedVertex> quad) {
		if (quad.size() != 4)
			return null;
		Vector3f captured = new Vector3f();
		for (CapturedVertex vertex : quad)
			captured.add(vertex.normalX, vertex.normalY, vertex.normalZ);
		if (captured.lengthSquared() > 1.0e-8f)
			return captured.normalize();
		Vector3f origin = position(quad.get(0));
		Vector3f normal = position(quad.get(1)).sub(origin)
			.cross(position(quad.get(3)).sub(origin));
		if (normal.lengthSquared() <= 1.0e-8f)
			return null;
		return normal.normalize();
	}

	private static boolean quadOnBoundsFace(List<CapturedVertex> quad, RecoveredCuboid bounds) {
		Vector3f origin = bounds.corners.getFirst();
		Vector3f[] edges = { bounds.a, bounds.b, bounds.c };
		Vector3f[] axes = new Vector3f[3];
		float[] lengths = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			lengths[axis] = edges[axis].length();
			if (lengths[axis] <= POSITION_EPSILON)
				return false;
			axes[axis] = new Vector3f(edges[axis]).div(lengths[axis]);
		}

		float[][] ranges = projectedRanges(quad, origin, axes);
		boolean onFace = false;
		for (int axis = 0; axis < 3; axis++) {
			boolean atMinimum = Math.abs(ranges[axis][0]) <= POSITION_EPSILON * 4.0f
				&& Math.abs(ranges[axis][1]) <= POSITION_EPSILON * 4.0f;
			boolean atMaximum = Math.abs(ranges[axis][0] - lengths[axis]) <= POSITION_EPSILON * 4.0f
				&& Math.abs(ranges[axis][1] - lengths[axis]) <= POSITION_EPSILON * 4.0f;
			onFace |= atMinimum || atMaximum;
		}
		if (!onFace)
			return false;
		for (CapturedVertex vertex : quad) {
			Vector3f relative = position(vertex).sub(origin);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				if (Math.abs(projection) > POSITION_EPSILON * 4.0f
					&& Math.abs(projection - lengths[axis]) > POSITION_EPSILON * 4.0f)
					return false;
			}
		}
		return true;
	}

	private static Vector3f position(CapturedVertex vertex) {
		return new Vector3f(vertex.x, vertex.y, vertex.z);
	}

	private static List<Vector3f> parallelepiped(Vector3f origin, Vector3f a, Vector3f b, Vector3f c) {
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

	private static List<Vector3f> uniquePositions(List<CapturedVertex> vertices) {
		List<Vector3f> points = new ArrayList<>(8);
		for (CapturedVertex vertex : vertices) {
			Vector3f point = new Vector3f(vertex.x, vertex.y, vertex.z);
			if (findPoint(points, point) == null)
				points.add(point);
		}
		return points;
	}

	private static boolean samePointSet(List<Vector3f> first, List<Vector3f> second) {
		if (first.size() != second.size())
			return false;
		for (Vector3f point : first)
			if (findPoint(second, point) == null)
				return false;
		return true;
	}

	@Nullable
	private static Vector3f findPoint(List<Vector3f> points, Vector3f target) {
		for (Vector3f point : points)
			if (Math.abs(point.x - target.x) <= POSITION_EPSILON
				&& Math.abs(point.y - target.y) <= POSITION_EPSILON
				&& Math.abs(point.z - target.z) <= POSITION_EPSILON)
				return point;
		return null;
	}

	private static boolean isPresent(int cubeId, int expectedCubeCount, BitSet presentCubes) {
		return expectedCubeCount <= 0 || presentCubes.get(cubeId);
	}

	private static float outerShellShrink(Matrix4f pose) {
		float x = pose.m30();
		float y = pose.m31();
		float z = pose.m32();
		float distanceSqr = x * x + y * y + z * z;
		if (distanceSqr >= OUTER_SHELL_FAR_DISTANCE_SQR)
			return OUTER_SHELL_SHRINK_FAR;
		return distanceSqr >= OUTER_SHELL_MID_DISTANCE_SQR
			? OUTER_SHELL_SHRINK_MID : OUTER_SHELL_SHRINK_CLOSE;
	}

	private static ModelPart innerCube() {
		if (innerCube == null)
			innerCube = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SLIME).getChild("cube");
		return innerCube;
	}

	private static ModelPart outerCube() {
		if (outerCube == null)
			outerCube = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SLIME_OUTER).getChild("cube");
		return outerCube;
	}

	private static int packed(int low, int high) {
		return low & 0xffff | (high & 0xffff) << 16;
	}

	private static final class RecordingBuffer implements MultiBufferSource {
		private final List<CaptureStream> streams = new ArrayList<>();

		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			CaptureStream stream = new CaptureStream(streams.size(), renderType);
			streams.add(stream);
			return stream.consumer;
		}

		private void finish() {
			for (CaptureStream stream : streams)
				stream.consumer.finish();
		}
	}

	private static final class CaptureStream {
		private final int id;
		private final RenderType renderType;
		private final List<CapturedVertex> vertices = new ArrayList<>();
		private final RecordingConsumer consumer = new RecordingConsumer(vertices);

		private CaptureStream(int id, RenderType renderType) {
			this.id = id;
			this.renderType = renderType;
		}
	}

	private static final class RecordingConsumer implements VertexConsumer {
		private final List<CapturedVertex> target;
		@Nullable
		private MutableVertex current;

		private RecordingConsumer(List<CapturedVertex> target) {
			this.target = target;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			finish();
			current = new MutableVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			if (current != null) {
				current.red = red;
				current.green = green;
				current.blue = blue;
				current.alpha = alpha;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (current != null) {
				current.u = u;
				current.v = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			if (current != null) {
				current.overlayU = u;
				current.overlayV = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			if (current != null) {
				current.lightU = u;
				current.lightV = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			if (current != null) {
				current.normalX = x;
				current.normalY = y;
				current.normalZ = z;
			}
			return this;
		}

		private void finish() {
			if (current == null)
				return;
			target.add(current.freeze());
			current = null;
		}
	}

	private static final class MutableVertex {
		private final float x;
		private final float y;
		private final float z;
		private int red = 255;
		private int green = 255;
		private int blue = 255;
		private int alpha = 255;
		private float u;
		private float v;
		private int overlayU;
		private int overlayV;
		private int lightU;
		private int lightV;
		private float normalX;
		private float normalY = 1.0f;
		private float normalZ;

		private MutableVertex(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		private CapturedVertex freeze() {
			return new CapturedVertex(x, y, z, red, green, blue, alpha, u, v,
				overlayU, overlayV, lightU, lightV, normalX, normalY, normalZ);
		}
	}

	private record CapturedVertex(float x, float y, float z, int red, int green, int blue, int alpha,
		float u, float v, int overlayU, int overlayV, int lightU, int lightV,
		float normalX, float normalY, float normalZ) {}

	private record RecoveredCandidate(RecoveredCuboid cuboid, List<CapturedVertex> vertices) {}

	private record PendingQuad(int streamId, RenderType renderType, List<CapturedVertex> vertices,
		int order) {}

	private record PendingMesh(int streamId, RenderType renderType, List<CapturedVertex> vertices,
		int order) {}

	static record CapturedInput(List<CaptureStream> streams, List<ObservedCube> observedCubes,
		boolean topology) {}

	private record CachedAlphaMask(int generation, AlphaMask mask) {}

	/**
	 * @param targetKey the caller's already-computed key for {@code cuboid}; recomputing it here, and
	 *   recomputing every observed cube's key on every call, made this quadratic in stream pipelines.
	 */
	private static List<Vec3> matchingModelCorners(RecoveredCuboid cuboid, GeometryKey targetKey,
		List<ObservedCube> observedCubes) {
		for (ObservedCube observed : observedCubes) {
			if (!targetKey.equals(observed.key))
				continue;
			List<Vec3> ordered = new ArrayList<>(8);
			for (Vector3f corner : cuboid.corners) {
				int match = matchingCorner(observed.transformedCorners, corner);
				if (match < 0) {
					ordered.clear();
					break;
				}
				ordered.add(observed.modelCorners.get(match));
			}
			if (ordered.size() == 8)
				return List.copyOf(ordered);
		}
		return List.of();
	}

	private static int matchingCorner(List<Vector3f> corners, Vector3f target) {
		for (int index = 0; index < corners.size(); index++) {
			Vector3f candidate = corners.get(index);
			if (Math.abs(candidate.x - target.x) <= POSITION_EPSILON
				&& Math.abs(candidate.y - target.y) <= POSITION_EPSILON
				&& Math.abs(candidate.z - target.z) <= POSITION_EPSILON)
				return index;
		}
		return -1;
	}

	private static List<SurgicalModelRenderContext.FaceGrid> recoverFaceGrids(RecoveredCuboid cuboid,
		RenderType renderType, List<CapturedVertex> vertices) {
		ResourceLocation texture = renderTypeTexture(renderType);
		AlphaMask textureInfo = texture == null ? null : alphaMask(texture);
		if (textureInfo == null || textureInfo.width <= 0 || textureInfo.height <= 0 || vertices.size() < 4)
			return List.of();
		List<SurgicalModelRenderContext.FaceGrid> grids = new ArrayList<>(6);
		for (int[] face : SurgicalClientTopology.CUBE_FACES) {
			List<CapturedVertex> quad = matchingFaceQuad(cuboid.corners, face, vertices);
			if (quad == null) {
				grids.add(new SurgicalModelRenderContext.FaceGrid(0.0d, 0.0d));
				continue;
			}
			CapturedVertex origin = vertexAt(quad, cuboid.corners.get(face[0]));
			CapturedVertex alongU = vertexAt(quad, cuboid.corners.get(face[1]));
			CapturedVertex alongV = vertexAt(quad, cuboid.corners.get(face[3]));
			if (origin == null || alongU == null || alongV == null) {
				grids.add(new SurgicalModelRenderContext.FaceGrid(0.0d, 0.0d));
				continue;
			}
			grids.add(new SurgicalModelRenderContext.FaceGrid(
				texturePixelDistance(origin, alongU, textureInfo.width, textureInfo.height),
				texturePixelDistance(origin, alongV, textureInfo.width, textureInfo.height)));
		}
		return List.copyOf(grids);
	}

	@Nullable
	private static List<CapturedVertex> matchingFaceQuad(List<Vector3f> corners, int[] face,
		List<CapturedVertex> vertices) {
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			boolean matches = true;
			for (CapturedVertex vertex : quad) {
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
	private static CapturedVertex vertexAt(List<CapturedVertex> vertices, Vector3f position) {
		for (CapturedVertex vertex : vertices)
			if (samePosition(vertex, position))
				return vertex;
		return null;
	}

	private static boolean samePosition(CapturedVertex vertex, Vector3f position) {
		return Math.abs(vertex.x - position.x) <= POSITION_EPSILON
			&& Math.abs(vertex.y - position.y) <= POSITION_EPSILON
			&& Math.abs(vertex.z - position.z) <= POSITION_EPSILON;
	}

	private static double texturePixelDistance(CapturedVertex first, CapturedVertex second,
		int textureWidth, int textureHeight) {
		double u = (second.u - first.u) * textureWidth;
		double v = (second.v - first.v) * textureHeight;
		return Math.hypot(u, v);
	}

	private static final class ComponentBuilder {
		private final int order;
		private final RecoveredCuboid cuboid;
		private final List<Vec3> modelCorners;
		private final boolean topology;
		private final List<SourceBatch> batches = new ArrayList<>();
		private final List<SourceBatch> surfaceOverlays = new ArrayList<>();
		private final BitSet captureStreams = new BitSet();
		@Nullable
		private RenderType primaryRenderType;
		private List<SurgicalModelRenderContext.FaceGrid> faceGrids = List.of();

		private ComponentBuilder(int order, RecoveredCuboid cuboid, List<Vec3> modelCorners,
			boolean topology) {
			this.order = order;
			this.cuboid = cuboid;
			this.modelCorners = List.copyOf(modelCorners);
			this.topology = topology;
		}

		private void observeMaterial(RenderType renderType) {
			if (primaryRenderType == null)
				primaryRenderType = renderType;
		}

		private void addVisibleBatch(RenderType renderType, List<CapturedVertex> vertices) {
			boolean surfaceOverlay = primaryRenderType != renderType;
			if (topology && faceGrids.isEmpty())
				faceGrids = recoverFaceGrids(cuboid, renderType, vertices);
			SourceBatch batch = new SourceBatch(renderType, List.copyOf(vertices), surfaceOverlay);
			batches.add(batch);
			if (surfaceOverlay)
				surfaceOverlays.add(batch);
		}

		private void addSurfaceOverlayBatch(RenderType renderType, List<CapturedVertex> vertices) {
			SourceBatch batch = new SourceBatch(renderType, List.copyOf(vertices), true);
			batches.add(batch);
			surfaceOverlays.add(batch);
		}

		private Component build(int id, boolean preserveSource) {
			return new Component(id, cuboid.corners, cuboid.a, cuboid.b, cuboid.c,
				modelCorners, faceGrids, preserveSource, List.copyOf(batches), List.copyOf(surfaceOverlays));
		}
	}

	private record RecoveredCuboid(List<Vector3f> corners, Vector3f a, Vector3f b, Vector3f c) {}

	private static final class ObservedCube {
		private final List<Vector3f> transformedCorners;
		private final List<Vec3> modelCorners;
		/** Computed once here rather than per comparison in {@link #matchingModelCorners}. */
		private final GeometryKey key;

		private ObservedCube(List<Vector3f> transformedCorners, List<Vec3> modelCorners) {
			// Both lists are built fresh per cube at the single call site and handed straight over, so
			// they only need freezing, not the deep Vector3f copy this used to make.
			this.transformedCorners = List.copyOf(transformedCorners);
			this.modelCorners = List.copyOf(modelCorners);
			this.key = GeometryKey.of(this.transformedCorners);
		}
	}

	private record GeometryKey(List<QuantizedPoint> points) {
		private static GeometryKey of(List<Vector3f> corners) {
			List<QuantizedPoint> points = corners.stream()
				.map(QuantizedPoint::of)
				.distinct()
				.sorted()
				.toList();
			return new GeometryKey(points);
		}
	}

	private record QuantizedPoint(int x, int y, int z) implements Comparable<QuantizedPoint> {
		private static QuantizedPoint of(Vector3f point) {
			return new QuantizedPoint(Math.round(point.x * POSITION_QUANTIZATION),
				Math.round(point.y * POSITION_QUANTIZATION), Math.round(point.z * POSITION_QUANTIZATION));
		}

		@Override
		public int compareTo(QuantizedPoint other) {
			int compareX = Integer.compare(x, other.x);
			if (compareX != 0)
				return compareX;
			int compareY = Integer.compare(y, other.y);
			return compareY != 0 ? compareY : Integer.compare(z, other.z);
		}
	}

	private static final class Component {
		private final int id;
		private final List<Vector3f> corners;
		private final Vector3f a;
		private final Vector3f b;
		private final Vector3f c;
		private final List<Vec3> modelCorners;
		private final List<SurgicalModelRenderContext.FaceGrid> faceGrids;
		private final boolean preserveSource;
		private final List<SourceBatch> batches;
		private final List<SourceBatch> surfaceOverlays;

		private Component(int id, List<Vector3f> corners, Vector3f a, Vector3f b, Vector3f c,
			List<Vec3> modelCorners, List<SurgicalModelRenderContext.FaceGrid> faceGrids, boolean preserveSource,
			List<SourceBatch> batches, List<SourceBatch> surfaceOverlays) {
			this.id = id;
			this.corners = corners.stream().map(Vector3f::new).toList();
			this.a = new Vector3f(a);
			this.b = new Vector3f(b);
			this.c = new Vector3f(c);
			this.modelCorners = List.copyOf(modelCorners);
			this.faceGrids = List.copyOf(faceGrids);
			this.preserveSource = preserveSource;
			this.batches = batches;
			this.surfaceOverlays = surfaceOverlays;
		}

		private void renderSource(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation,
			boolean offsetSurfaceOverlays) {
			for (SourceBatch batch : batches)
				batch.renderSource(poseStack, buffer, packedLight, offset, rotation, center(),
					offsetSurfaceOverlays);
		}

		private void renderSurfaceOverlays(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation) {
			for (SourceBatch batch : surfaceOverlays)
				batch.renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center());
		}

		private void renderSlime(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, boolean outer,
			float outerShellShrink) {
			PoseStack cubePose = new PoseStack();
			Matrix4f basePose = new Matrix4f(poseStack.last().pose());
			Vector3f center = center();
			Vector3f worldCenter = basePose.transformPosition(center, new Vector3f());
			Matrix4f transformedPose = new Matrix4f().translation(
				offset == null ? 0.0f : (float) offset.x,
				offset == null ? 0.0f : (float) offset.y,
				offset == null ? 0.0f : (float) offset.z);
			if (rotation != null && !rotation.isIdentity())
				transformedPose.translate(worldCenter)
					.rotate(new Quaternionf((float) rotation.x(), (float) rotation.y(),
						(float) rotation.z(), (float) rotation.w()))
					.translate(-worldCenter.x, -worldCenter.y, -worldCenter.z);
			transformedPose.mul(basePose);
			cubePose.mulPose(transformedPose);

			float aScale = slimeAxisScale(a, outer, outerShellShrink);
			float bScale = slimeAxisScale(b, outer, outerShellShrink);
			float cScale = slimeAxisScale(c, outer, outerShellShrink);
			Matrix4f transform = new Matrix4f().identity();
			transform.m00(aScale * a.x).m01(aScale * a.y).m02(aScale * a.z);
			transform.m10(bScale * b.x).m11(bScale * b.y).m12(bScale * b.z);
			transform.m20(cScale * c.x).m21(cScale * c.y).m22(cScale * c.z);
			transform.m30(center.x).m31(center.y).m32(center.z);
			cubePose.mulPose(transform);
			cubePose.translate(0.0f, -SLIME_CENTER_Y, 0.0f);

			int overlay = batches.isEmpty() || batches.getFirst().vertices.isEmpty()
				? OverlayTexture.NO_OVERLAY
				: packed(batches.getFirst().vertices.getFirst().overlayU,
					batches.getFirst().vertices.getFirst().overlayV);
			VertexConsumer consumer = buffer.getBuffer(outer ? OUTER_RENDER_TYPE : INNER_RENDER_TYPE);
			(outer ? outerCube() : innerCube()).render(cubePose, consumer, packedLight, overlay, 0xFFFFFFFF);
		}

		/**
		 * The baked outer slime cube spans half a model unit, so a multiplier of two fits it
		 * exactly to the captured component. Pulling each local axis in by a fixed world-space
		 * amount keeps adjacent components visually joined without changing their topology.
		 */
		private static float slimeAxisScale(Vector3f axis, boolean outer, float outerShellShrink) {
			if (!outer)
				return 2.0f;
			float length = axis.length();
			if (length <= POSITION_EPSILON)
				return 2.0f;
			float renderedLength = Math.max(length - outerShellShrink, POSITION_EPSILON);
			return 2.0f * renderedLength / length;
		}

		private SurgicalModelRenderContext.CubeGeometry geometry(PoseStack poseStack, @Nullable Vec3 offset,
			@Nullable SurgicalCubeRotation rotation, @Nullable Vec3 cameraPosition) {
			Matrix4f pose = poseStack.last().pose();
			Vector3f transformedCenter = pose.transformPosition(center(), new Vector3f());
			Vec3 worldCenter = new Vec3(transformedCenter.x, transformedCenter.y, transformedCenter.z);
			double offsetX = offset == null ? 0.0d : offset.x;
			double offsetY = offset == null ? 0.0d : offset.y;
			double offsetZ = offset == null ? 0.0d : offset.z;
			double cameraX = cameraPosition == null ? 0.0d : cameraPosition.x;
			double cameraY = cameraPosition == null ? 0.0d : cameraPosition.y;
			double cameraZ = cameraPosition == null ? 0.0d : cameraPosition.z;
			List<Vec3> transformed = new ArrayList<>(8);
			for (Vector3f corner : corners) {
				Vector3f point = pose.transformPosition(corner, new Vector3f());
				Vec3 worldPoint = new Vec3(point.x, point.y, point.z);
				if (rotation != null && !rotation.isIdentity())
					worldPoint = worldCenter.add(rotation.rotate(worldPoint.subtract(worldCenter)));
				transformed.add(worldPoint.add(offsetX + cameraX, offsetY + cameraY, offsetZ + cameraZ));
			}
			return new SurgicalModelRenderContext.CubeGeometry(id, transformed, modelCorners, faceGrids);
		}

		private Vector3f center() {
			return new Vector3f(corners.getFirst())
				.add(new Vector3f(a).mul(0.5f))
				.add(new Vector3f(b).mul(0.5f))
				.add(new Vector3f(c).mul(0.5f));
		}
	}

	private record SourceBatch(RenderType renderType, List<CapturedVertex> vertices, boolean surfaceOverlay) {
		private void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, @Nullable Vec3 offset) {
			render(poseStack, buffer, packedLight, offset, SurgicalCubeRotation.IDENTITY,
				new Vector3f(), renderType);
		}

		private void renderSource(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center,
			boolean offsetSurfaceOverlay) {
			if (surfaceOverlay)
				renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center,
					offsetSurfaceOverlay);
			else
				render(poseStack, buffer, packedLight, offset, rotation, center, renderType);
		}

		private void renderSurfaceOverlay(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center) {
			renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center, false);
		}

		private void renderSurfaceOverlay(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center,
			boolean depthOffset) {
			RenderType effectiveRenderType = renderType;
			ResourceLocation texture = renderTypeTexture(renderType);
			// Villager type, profession and level layers redraw the exact source-model vertices.
			// The projected source has no inset slime shell to separate those coplanar fragments,
			// so retain the original material but use vanilla's view-space Z offset for cutout layers.
			if (depthOffset && texture != null && renderType == RenderType.entityCutoutNoCull(texture))
				effectiveRenderType = RenderType.entityCutoutNoCullZOffset(texture);
			render(poseStack, buffer, packedLight, offset, rotation, center, effectiveRenderType);
		}

		private void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, @Nullable Vec3 offset,
			@Nullable SurgicalCubeRotation rotation, Vector3f center, RenderType effectiveRenderType) {
			VertexConsumer consumer = buffer.getBuffer(effectiveRenderType);
			Matrix4f pose = poseStack.last().pose();
			Matrix3f normal = poseStack.last().normal();
			float offsetX = offset == null ? 0.0f : (float) offset.x;
			float offsetY = offset == null ? 0.0f : (float) offset.y;
			float offsetZ = offset == null ? 0.0f : (float) offset.z;
			Vector4f position = new Vector4f();
			Vector3f transformedNormal = new Vector3f();
			Vector3f transformedCenter = pose.transformPosition(center, new Vector3f());
			Vec3 worldCenter = new Vec3(transformedCenter.x, transformedCenter.y, transformedCenter.z);
			for (CapturedVertex vertex : vertices) {
				position.set(vertex.x, vertex.y, vertex.z, 1.0f);
				pose.transform(position);
				transformedNormal.set(vertex.normalX, vertex.normalY, vertex.normalZ);
				normal.transform(transformedNormal);
				if (rotation != null && !rotation.isIdentity()) {
					Vec3 rotatedPosition = worldCenter.add(rotation.rotate(
						new Vec3(position.x, position.y, position.z).subtract(worldCenter)));
					position.set((float) rotatedPosition.x, (float) rotatedPosition.y,
						(float) rotatedPosition.z, 1.0f);
					Vec3 rotatedNormal = rotation.rotate(new Vec3(transformedNormal.x,
						transformedNormal.y, transformedNormal.z));
					transformedNormal.set((float) rotatedNormal.x, (float) rotatedNormal.y,
						(float) rotatedNormal.z);
				}
				if (transformedNormal.lengthSquared() > 1.0e-8f)
					transformedNormal.normalize();
				int capturedLight = packed(vertex.lightU, vertex.lightV);
				int light = capturedLight == LightTexture.FULL_BRIGHT ? capturedLight : packedLight;
				consumer.addVertex(position.x + offsetX, position.y + offsetY, position.z + offsetZ)
					.setColor(vertex.red, vertex.green, vertex.blue, vertex.alpha)
					.setUv(vertex.u, vertex.v)
					.setUv1(vertex.overlayU, vertex.overlayV)
					.setUv2(light & 0xffff, light >>> 16 & 0xffff)
					.setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
			}
		}
	}

	private record AlphaMask(int width, int height, BitSet pixels, boolean opaque) {
		private static final AlphaMask OPAQUE = new AlphaMask(0, 0, new BitSet(), true);

		private boolean hasVisiblePixels(float minU, float minV, float maxU, float maxV) {
			if (opaque)
				return true;
			int minX = clamp((int) Math.floor(Math.min(minU, maxU) * width), width);
			int minY = clamp((int) Math.floor(Math.min(minV, maxV) * height), height);
			int maxX = clamp((int) Math.ceil(Math.max(minU, maxU) * width) - 1, width);
			int maxY = clamp((int) Math.ceil(Math.max(minV, maxV) * height) - 1, height);
			for (int y = minY; y <= maxY; y++) {
				int set = pixels.nextSetBit(y * width + minX);
				if (set >= 0 && set <= y * width + maxX)
					return true;
			}
			return false;
		}

		private static int clamp(int value, int maxExclusive) {
			return maxExclusive <= 0 ? 0 : Math.max(0, Math.min(value, maxExclusive - 1));
		}
	}
}
