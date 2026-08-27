package com.nobodiiiii.createbiotech.content.surgery.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>The source renderer is executed once into a recording buffer. Consecutive six-quad
 * cuboids are recovered from the final vertex stream, deduplicated across base and layer
 * passes, and assigned stable surgical ids. Everything else is retained as original
 * geometry. This deliberately observes only the public {@link VertexConsumer} contract;
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
	private static final float THIN_EDGE = 0.05f / 16.0f;
	private static final float OVERLAY_EXPANSION_MAX = 1.1f / 16.0f;
	private static final float OVERLAY_CENTER_EPSILON = 0.1f / 16.0f;
	private static final float PARALLEL_DOT_MIN = 0.999f;
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

	private final List<Component> components;
	private final List<SourceBatch> extras;

	private SurgicalCapturedRenderPlan(List<Component> components, List<SourceBatch> extras) {
		this.components = List.copyOf(components);
		this.extras = List.copyOf(extras);
	}

	static SurgicalCapturedRenderPlan capture(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick) {
		return build(captureInput(renderer, preview, yaw, partialTick));
	}

	static CapturedInput captureInput(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick) {
		RecordingBuffer recording = new RecordingBuffer();
		PoseStack neutralPose = new PoseStack();
		List<ObservedCube> observedCubes = new ArrayList<>();
		Deque<List<ObservedCube>> captures = MODEL_CUBE_CAPTURES.get();
		captures.push(observedCubes);
		activeModelCubeCaptureCount++;
		try {
			renderer.render(preview, yaw, partialTick, neutralPose, recording, CAPTURE_LIGHT);
		} finally {
			recording.finish();
			captures.pop();
			activeModelCubeCaptureCount = Math.max(0, activeModelCubeCaptureCount - 1);
			if (captures.isEmpty())
				MODEL_CUBE_CAPTURES.remove();
		}
		return new CapturedInput(List.copyOf(recording.streams), List.copyOf(observedCubes));
	}

	static SurgicalCapturedRenderPlan build(CapturedInput captured) {
		return build(captured.streams, captured.observedCubes);
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
		SurgicalCapturedRenderPlan frame = capture((EntityRenderer<LivingEntity>) renderer, entity,
			yaw, partialTick);
		if (entity.isDeadOrDying())
			SlimeMimicDeathClient.report(entity, frame.deathGeometry(0, entity.position()));
		frame.render(poseStack, buffer, packedLight, 0, ALL_COMPONENTS, NO_OFFSETS, NO_ROTATIONS,
			false, null, false);
		return true;
	}

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
						cubeRotations.get(component.id));
		} else {
			for (Component component : components) {
				if (!isPresent(component.id, expectedCubeCount, presentCubes))
					continue;
				if (component.preserveSource)
					component.renderSource(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id));
				else
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), false);
			}
			for (Component component : components)
				if (!component.preserveSource && isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), true);
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

	private static SurgicalCapturedRenderPlan build(List<CaptureStream> streams, List<ObservedCube> observedCubes) {
		Map<GeometryKey, List<ComponentBuilder>> recovered = new LinkedHashMap<>();
		List<SourceBatch> extras = new ArrayList<>();
		int order = 0;

		for (CaptureStream stream : streams) {
			List<CapturedVertex> vertices = stream.vertices;
			if (stream.renderType.mode() != VertexFormat.Mode.QUADS) {
				if (!vertices.isEmpty())
					extras.add(new SourceBatch(stream.renderType, List.copyOf(vertices), false));
				continue;
			}

			int cursor = 0;
			while (cursor + 24 <= vertices.size()) {
				List<CapturedVertex> candidateVertices = vertices.subList(cursor, cursor + 24);
				RecoveredCuboid cuboid = recoverCuboid(candidateVertices);
				if (cuboid == null) {
					addVisibleQuadExtra(stream.renderType, vertices, cursor, extras);
					cursor += 4;
					continue;
				}

				GeometryKey key = GeometryKey.of(cuboid.corners);
				List<ComponentBuilder> matches = recovered.computeIfAbsent(key, ignored -> new ArrayList<>());
				ComponentBuilder builder = matches.stream()
					.filter(candidate -> !candidate.captureStreams.get(stream.id))
					.findFirst()
					.orElse(null);
				if (builder == null) {
					builder = new ComponentBuilder(order++, cuboid,
						matchingModelCorners(cuboid, observedCubes));
					matches.add(builder);
				}
				builder.captureStreams.set(stream.id);
				builder.observeMaterial(stream.renderType);
				if (hasVisibleQuad(stream.renderType, candidateVertices))
					builder.addVisibleBatch(stream.renderType, candidateVertices);
				cursor += 24;
			}

			while (cursor + 4 <= vertices.size()) {
				addVisibleQuadExtra(stream.renderType, vertices, cursor, extras);
				cursor += 4;
			}
			if (cursor < vertices.size())
				extras.add(new SourceBatch(stream.renderType,
					List.copyOf(vertices.subList(cursor, vertices.size())), false));
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

	private static void addVisibleQuadExtra(RenderType renderType, List<CapturedVertex> vertices, int cursor,
		List<SourceBatch> extras) {
		List<CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
		if (quadVisible(renderType, quad))
			extras.add(new SourceBatch(renderType, List.copyOf(quad), false));
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

	static record CapturedInput(List<CaptureStream> streams, List<ObservedCube> observedCubes) {}

	private record CachedAlphaMask(int generation, AlphaMask mask) {}

	private static List<Vec3> matchingModelCorners(RecoveredCuboid cuboid, List<ObservedCube> observedCubes) {
		GeometryKey targetKey = GeometryKey.of(cuboid.corners);
		for (ObservedCube observed : observedCubes) {
			if (!targetKey.equals(GeometryKey.of(observed.transformedCorners)))
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
		if (textureInfo == null || textureInfo.width <= 0 || textureInfo.height <= 0 || vertices.size() < 24)
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
		private final List<SourceBatch> batches = new ArrayList<>();
		private final List<SourceBatch> surfaceOverlays = new ArrayList<>();
		private final BitSet captureStreams = new BitSet();
		@Nullable
		private RenderType primaryRenderType;
		private List<SurgicalModelRenderContext.FaceGrid> faceGrids = List.of();

		private ComponentBuilder(int order, RecoveredCuboid cuboid, List<Vec3> modelCorners) {
			this.order = order;
			this.cuboid = cuboid;
			this.modelCorners = List.copyOf(modelCorners);
		}

		private void observeMaterial(RenderType renderType) {
			if (primaryRenderType == null)
				primaryRenderType = renderType;
		}

		private void addVisibleBatch(RenderType renderType, List<CapturedVertex> vertices) {
			boolean surfaceOverlay = primaryRenderType != renderType;
			if (faceGrids.isEmpty())
				faceGrids = recoverFaceGrids(cuboid, renderType, vertices);
			SourceBatch batch = new SourceBatch(renderType, List.copyOf(vertices), surfaceOverlay);
			batches.add(batch);
			if (surfaceOverlay)
				surfaceOverlays.add(batch);
		}

		private Component build(int id, boolean preserveSource) {
			return new Component(id, cuboid.corners, cuboid.a, cuboid.b, cuboid.c,
				modelCorners, faceGrids, preserveSource, List.copyOf(batches), List.copyOf(surfaceOverlays));
		}
	}

	private record RecoveredCuboid(List<Vector3f> corners, Vector3f a, Vector3f b, Vector3f c) {}

	private record ObservedCube(List<Vector3f> transformedCorners, List<Vec3> modelCorners) {
		private ObservedCube {
			transformedCorners = transformedCorners.stream().map(Vector3f::new).toList();
			modelCorners = List.copyOf(modelCorners);
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
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation) {
			for (SourceBatch batch : batches)
				batch.renderSource(poseStack, buffer, packedLight, offset, rotation, center());
		}

		private void renderSurfaceOverlays(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation) {
			for (SourceBatch batch : surfaceOverlays)
				batch.renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center());
		}

		private void renderSlime(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, boolean outer) {
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

			Matrix4f transform = new Matrix4f().identity();
			transform.m00(2.0f * a.x).m01(2.0f * a.y).m02(2.0f * a.z);
			transform.m10(2.0f * b.x).m11(2.0f * b.y).m12(2.0f * b.z);
			transform.m20(2.0f * c.x).m21(2.0f * c.y).m22(2.0f * c.z);
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
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center) {
			if (surfaceOverlay)
				renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center);
			else
				render(poseStack, buffer, packedLight, offset, rotation, center, renderType);
		}

		private void renderSurfaceOverlay(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center) {
			RenderType offsetRenderType = renderType;
			ResourceLocation texture = renderTypeTexture(renderType);
			// Vanilla profession/type layers use this memoized cutout type. Match by identity so
			// translucent, emissive and other special-material overlays retain their original state.
			if (texture != null && renderType == RenderType.entityCutoutNoCull(texture))
				offsetRenderType = RenderType.entityCutoutNoCullZOffset(texture);
			render(poseStack, buffer, packedLight, offset, rotation, center, offsetRenderType);
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
