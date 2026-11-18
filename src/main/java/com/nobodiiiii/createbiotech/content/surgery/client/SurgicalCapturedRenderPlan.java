package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCubeGeometry;
import com.nobodiiiii.createbiotech.content.slimemimic.client.SlimeMimicDeathClient;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelNode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable, model-library-neutral output of one living-entity render.
 *
 * <p>The source renderer is executed once into a recording buffer. Complete and partial
 * cuboids are recovered from the final vertex stream, deduplicated across base and layer
 * passes, and assigned stable surgical ids. Remaining connected model-textured quad islands
 * receive a synthetic oriented bounding box unless they are an exact surface overlay.
 * Everything else is retained as original geometry. Geometry recovery observes only the public
 * {@link VertexConsumer} contract; part-name provenance is collected separately during capture.
 * vanilla {@code ModelPart}, third-party model libraries and hand-written render layers require no
 * individual model bridge.</p>
 */
public final class SurgicalCapturedRenderPlan {
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final ResourceLocation SHEEP_WOOL_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_fur.png");
	private static final RenderType INNER_RENDER_TYPE = RenderType.entityCutoutNoCull(SLIME_TEXTURE);
	private static final RenderType OUTER_RENDER_TYPE = RenderType.entityTranslucent(SLIME_TEXTURE);
	static final Set<ResourceLocation> SADDLE_TEXTURES = Set.of(
		ResourceLocation.withDefaultNamespace("textures/entity/pig/pig_saddle.png"),
		ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_saddle.png"));
	// Record with a recognizable non-full-bright value so ambient lighting cannot be mistaken for
	// an emissive layer. Vanilla supplies full block light for burning entities; capturing that
	// value directly would otherwise make every recorded vertex permanently glow.
	static final int CAPTURE_LIGHT = LightTexture.pack(7, 11);
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
	static final float THIN_EDGE = 0.05f / 16.0f;
	static final float OVERLAY_EXPANSION_MAX = 1.1f / 16.0f;
	static final float OVERLAY_CENTER_EPSILON = 0.1f / 16.0f;
	static final float PARALLEL_DOT_MIN = 0.999f;
	static final float SYNTHETIC_PARALLEL_DOT_MIN = 0.995f;
	static final float POSITION_EPSILON = 2.0e-5f;
	private static final float POSITION_QUANTIZATION = 100_000.0f;
	static volatile int resourceGeneration;
	private static ModelPart innerCube;
	private static ModelPart outerCube;
	private static final BitSet ALL_COMPONENTS = new BitSet();
	private static final Map<Integer, Vec3> NO_OFFSETS = Map.of();
	private static final Map<Integer, SurgicalCubeRotation> NO_ROTATIONS = Map.of();
	/** Render-thread only; cleared every frame by {@link #beginFrame()}. */
	private static final Map<LivingEntity, LiveFrame> LIVE_FRAMES = new IdentityHashMap<>();
	/** Weak keys; recipes retain vertex indices, never entities or a previous frame's vertices. */
	private static final Map<LivingEntity, LiveRecovery> LIVE_RECOVERY = new WeakHashMap<>();

	private final List<Component> components;
	private final List<Component> appearanceComponents;
	private final boolean traitAppearance;
	private final List<SourceBatch> extras;
	private final BitSet woolCubes = new BitSet();
	private final Map<SurgicalCapturedRenderPlan, List<Component>> equipmentLayers = new IdentityHashMap<>();

	SurgicalCapturedRenderPlan(List<Component> components, List<SourceBatch> extras) {
		this(components, components, extras, false);
	}

	private SurgicalCapturedRenderPlan(List<Component> components, List<Component> appearanceComponents,
		List<SourceBatch> extras, boolean traitAppearance) {
		this.components = List.copyOf(components);
		this.appearanceComponents = List.copyOf(appearanceComponents);
		this.traitAppearance = traitAppearance;
		this.extras = List.copyOf(extras);
		for (Component component : components)
			if (component.batches.stream().anyMatch(batch -> SHEEP_WOOL_TEXTURE.equals(SurgicalMaterialBinding.renderTypeTexture(batch.renderType))))
				woolCubes.set(component.id);
	}

	/** Hide wool without changing the saved topology, cube ids or retained tissue. */
	BitSet withoutWool(BitSet presentCubes) {
		if (!presentCubes.intersects(woolCubes))
			return presentCubes;
		BitSet visible = (BitSet) presentCubes.clone();
		visible.andNot(woolCubes);
		return visible;
	}

	/**
	 * Keep surgery's original ids and geometry while drawing an equipped source model. Saddle layers
	 * and newly visible tack belong to an existing body cube, so removing or rotating that cube also
	 * removes or rotates its equipment. Never use the equipped renderer's shifted cube indices.
	 */
	SurgicalCapturedRenderPlan withAppearance(SurgicalCapturedRenderPlan appearance) {
		return withAppearance(appearance, false);
	}

	SurgicalCapturedRenderPlan withTraitAppearance(SurgicalCapturedRenderPlan appearance) {
		return withAppearance(appearance, true);
	}

	/** Rigid motion already baked into each bound native appearance, in the donor's render frame. */
	Map<Integer, SurgicalNativeAnimations.CubePose> nativePoses() {
		Map<Integer, SurgicalNativeAnimations.CubePose> poses = new HashMap<>();
		for (Component visual : appearanceComponents) {
			Component rest = components.get(visual.id);
			SurgicalCubeRotation rotation = SurgicalCubeRotation.IDENTITY;
			Vec3 translation = Vec3.ZERO;
			if (visual.modelPose != null && rest.modelPose != null
				&& !visual.modelPose.equals(rest.modelPose, POSITION_EPSILON)
				&& Math.abs(visual.modelPose.determinant()) > 1.0e-12f
				&& Math.abs(rest.modelPose.determinant()) > 1.0e-12f) {
				Matrix4f motion = new Matrix4f(visual.modelPose).mul(new Matrix4f(rest.modelPose).invert());
				Quaternionf q = motion.getUnnormalizedRotation(new Quaternionf());
				rotation = new SurgicalCubeRotation(q.x, q.y, q.z, q.w);
				Vector3f center = visual.center();
				Vector3f restCenter = rest.center();
				translation = new Vec3(center.x, center.y, center.z)
					.subtract(rotation.rotate(new Vec3(restCenter.x, restCenter.y, restCenter.z)));
			}
			poses.put(visual.id, new SurgicalNativeAnimations.CubePose(rotation, translation, rest.nodes));
		}
		return Map.copyOf(poses);
	}

	private SurgicalCapturedRenderPlan withAppearance(SurgicalCapturedRenderPlan appearance, boolean matchParts) {
		if (components.isEmpty())
			return this;
		Map<GeometryKey, List<Component>> originalCubes = new HashMap<>();
		for (Component component : components)
			originalCubes.computeIfAbsent(GeometryKey.of(component.corners), ignored -> new ArrayList<>())
				.add(component);
		Set<Component> matched = new HashSet<>();
		List<Component> bound = new ArrayList<>(appearance.components.size());
		for (Component visual : appearance.components) {
			Component owner = originalCubes.getOrDefault(GeometryKey.of(visual.corners), List.of()).stream()
				.filter(candidate -> !matched.contains(candidate))
				.filter(candidate -> !matchParts || candidate.partNames.equals(visual.partNames))
				.findFirst().orElse(null);
			if (owner == null && matchParts) {
				owner = components.stream().filter(candidate -> !matched.contains(candidate))
					.filter(candidate -> visual.partNames.stream()
						.anyMatch(name -> !name.equals("root") && candidate.partNames.contains(name)))
					// Moving parts retain their anatomical ids even when another cube is now closer.
					.min(Comparator.comparing((Component candidate) -> !candidate.partNames.equals(visual.partNames))
						.thenComparingDouble(candidate -> visual.center().distanceSquared(candidate.center()))).orElse(null);
				// A state must never introduce organs which were absent from the original capture.
				if (owner == null) continue;
			}
			if (owner != null)
				matched.add(owner);
			else
				owner = appearanceOwner(visual);
			bound.add(new Component(visual, owner));
		}
		return new SurgicalCapturedRenderPlan(components, bound, appearance.extras, matchParts);
	}

	/** Draw back-mounted accessories at one retained carrier, using that cube's articulation. */
	void renderEquipment(SurgicalCapturedRenderPlan equipped, int carrier, Vec3 nativeSeat,
		PoseStack poseStack, MultiBufferSource buffer, int light, @Nullable Vec3 offset,
		@Nullable SurgicalCubeRotation rotation, @Nullable Boolean hurt) {
		if (carrier < 0 || carrier >= components.size()) return;
		List<Component> layers = equipmentLayers(equipped);
		Vec3 displacement = equipmentDisplacement(carrier, nativeSeat);
		Component target = components.get(carrier);
		Vector3f center = target.center().sub((float) displacement.x, (float) displacement.y, (float) displacement.z);
		poseStack.pushPose();
		poseStack.translate(displacement.x, displacement.y, displacement.z);
		for (Component layer : layers) {
			if (SurgicalMaterialBinding.isHeadEquipment(layer)) continue;
			for (SourceBatch batch : layer.batches)
				batch.renderSource(poseStack, buffer, light, offset, rotation, center, true, hurt);
		}
		poseStack.popPose();
	}

	/** Head tack follows its own retained tissue, once per donor rather than once per saddle. */
	void renderHeadEquipment(SurgicalCapturedRenderPlan equipped, int expectedCubeCount, BitSet presentCubes,
		Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations,
		PoseStack poseStack, MultiBufferSource buffer, int light, @Nullable Boolean hurt) {
		for (Component layer : equipmentLayers(equipped)) {
			if (!SurgicalMaterialBinding.isHeadEquipment(layer) || !isPresent(layer.id, expectedCubeCount, presentCubes)) continue;
			layer.renderSource(poseStack, buffer, light, cubeOffsets.get(layer.id), cubeRotations.get(layer.id), true, hurt);
		}
	}

	List<Vec3> equipmentCorners(SurgicalCapturedRenderPlan equipped, int carrier, Vec3 nativeSeat) {
		if (carrier < 0 || carrier >= components.size()) return List.of();
		// A saddle's picking envelope must not include head tack at the donor's old head position.
		var layers = equipmentLayers(equipped).stream().filter(layer -> !SurgicalMaterialBinding.isHeadEquipment(layer)).toList();
		if (layers.isEmpty()) return List.of();
		Vec3 displacement = equipmentDisplacement(carrier, nativeSeat);
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
		for (Component layer : layers) for (Vector3f corner : layer.corners) {
			minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
			maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
		}
		List<Vec3> corners = new ArrayList<>(8);
		for (int corner = 0; corner < 8; corner++) corners.add(new Vec3(
			(corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? minY : maxY,
			(corner & 4) == 0 ? minZ : maxZ).add(displacement));
		return List.copyOf(corners);
	}

	private List<Component> equipmentLayers(SurgicalCapturedRenderPlan equipped) {
		return equipmentLayers.computeIfAbsent(equipped, plan -> {
			Set<GeometryKey> naked = new HashSet<>();
			appearanceComponents.forEach(component -> naked.add(GeometryKey.of(component.corners)));
			// The saved donor itself may have worn a saddle. Bind to its naked appearance so tack
			// cannot own itself through the original capture's optional equipment cube ids.
			List<Component> tissue = appearanceComponents.stream()
				.filter(component -> component.partNames.stream().noneMatch(SurgicalRenderCapture::isSaddlePart)).toList();
			if (tissue.isEmpty()) return List.of();
			return plan.appearanceComponents.stream().filter(component ->
				component.partNames.stream().anyMatch(SurgicalRenderCapture::isSaddlePart)
				|| !naked.contains(GeometryKey.of(component.corners)))
				.map(component -> new Component(component, appearanceOwner(component, tissue))).toList();
		});
	}

	private Vec3 equipmentDisplacement(int carrier, Vec3 nativeSeat) {
		Vector3f attachment = new Vector3f((float) nativeSeat.x, (float) nativeSeat.y, (float) nativeSeat.z);
		// Root part labels can be absent in runtime captures. Recover the native carrier from
		// the attachment's nearest cuboid instead of making every equipped cube its own origin.
		Component body = components.stream().min(Comparator
			.comparing((Component component) -> !component.partNames.contains("body"))
			.thenComparingDouble(component -> SurgicalMaterialBinding.distanceToBoxSquared(attachment, component)))
			.orElseThrow();
		return carrierSurface(carrier, nativeSeat).subtract(carrierSurface(body.id, nativeSeat));
	}

	/** Keep the native passenger-to-saddle offset while rebasing both onto the same carrier. */
	Vec3 equipmentSeat(int carrier, Vec3 nativeSeat) {
		return nativeSeat.add(equipmentDisplacement(carrier, nativeSeat));
	}

	/** Project the donor's front/back seat coordinates onto this retained cube's surface. */
	Vec3 carrierSurface(int carrier, Vec3 nativeSeat) {
		double top = Double.NEGATIVE_INFINITY;
		for (Component component : components) {
			if (component.id != carrier) continue;
			Vector3f origin = component.corners.getFirst();
			Vector3f horizontal = new Vector3f((float) nativeSeat.x, 0, (float) nativeSeat.z).sub(origin);
			double lower = Double.NEGATIVE_INFINITY;
			double upper = Double.POSITIVE_INFINITY;
			for (Vector3f edge : List.of(component.a, component.b, component.c)) {
				double length = edge.lengthSquared();
				if (length < POSITION_EPSILON * POSITION_EPSILON) { upper = Double.NEGATIVE_INFINITY; break; }
				double offset = horizontal.dot(edge) / length;
				double slope = edge.y / length;
				if (Math.abs(slope) < POSITION_EPSILON) {
					if (offset < -POSITION_EPSILON || offset > 1 + POSITION_EPSILON)
						upper = Double.NEGATIVE_INFINITY;
				} else {
					double first = -offset / slope;
					double second = (1 - offset) / slope;
					lower = Math.max(lower, Math.min(first, second));
					upper = Math.min(upper, Math.max(first, second));
				}
			}
			if (lower <= upper && Double.isFinite(upper)) top = Math.max(top, upper);
		}
		if (Double.isFinite(top)) return new Vec3(nativeSeat.x, top, nativeSeat.z);
		// A donor's native seat can lie outside the retained carrier's footprint. Use an
		// actual face point instead of returning an unrelated native attachment height.
		for (Component component : components) {
			if (component.id != carrier) continue;
			Vector3f center = component.center();
			double rise = Double.POSITIVE_INFINITY;
			for (Vector3f edge : List.of(component.a, component.b, component.c))
				if (Math.abs(edge.y) > POSITION_EPSILON)
					rise = Math.min(rise, edge.lengthSquared() / (2.0 * Math.abs(edge.y)));
			if (Double.isFinite(rise)) return new Vec3(center.x, center.y + rise, center.z);
		}
		return nativeSeat;
	}

	private Component appearanceOwner(Component visual) {
		return appearanceOwner(visual, components);
	}

	private static Component appearanceOwner(Component visual, List<Component> candidates) {
		// Expanded pig/strider saddle shells fit their underlying cuboid. Horse/camel tack can
		// cover only part of a body or head, so compare distance to the whole oriented box rather
		// than its center (which could incorrectly pick a nearby leg or ear).
		boolean hasHead = SurgicalMaterialBinding.isHeadEquipment(visual) && candidates.stream().anyMatch(component -> component.head);
		boolean chest = visual.partNames.stream().anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("chest"));
		boolean hasBody = chest && candidates.stream().anyMatch(component -> component.partNames.contains("body"));
		Component closest = candidates.getFirst();
		float closestScore = Float.POSITIVE_INFINITY;
		for (Component candidate : candidates) {
			if (hasHead && !candidate.head)
				continue;
			if (hasBody && !candidate.partNames.contains("body")) continue;
			float score = 0.0f;
			for (Vector3f corner : visual.corners)
				score += SurgicalMaterialBinding.distanceToBoxSquared(corner, candidate);
			// Prefer the most centered fit when several boxes contain the entire accessory.
			score += visual.center().distanceSquared(candidate.center()) * 0.0001f;
			if (score < closestScore) {
				closest = candidate;
				closestScore = score;
			}
		}
		return closest;
	}

	/**
	 * @param topology whether the caller will read {@code modelCorners} and {@code faceGrids} off the
	 *   resulting components. Only {@link #snapshot} exposes them, so the live and death paths pass
	 *   {@code false} and skip topology recovery. Saddled models still identify their tack so it
	 *   retains its source appearance even when it shares the body's texture.
	 */
	static SurgicalCapturedRenderPlan capture(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		return SurgicalRenderCapture.capture(renderer, preview, yaw, partialTick, topology);
	}

	static CapturedInput captureInput(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		return SurgicalRenderCapture.captureInput(renderer, preview, yaw, partialTick, topology);
	}

	/** Held-item layers retain their native geometry, material and glint instead of becoming tissue. */
	public static MultiBufferSource bufferForLayer(RenderLayer<?, ?> layer, MultiBufferSource buffer) {
		return SurgicalRenderCapture.bufferForLayer(layer, buffer);
	}

	static SurgicalCapturedRenderPlan build(CapturedInput captured) {
		return SurgicalMeshRecovery.build(captured);
	}

	/** Records unscaled ModelPart pixel bounds while the renderer emits the matching transformed vertices. */
	public static void observeModelCube(ModelPart.Cube cube, PoseStack.Pose pose) {
		SurgicalRenderCapture.observeModelCube(cube, pose);
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
			false, null, false, entity.hurtTime > 0 || entity.deathTime > 0);
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
		LiveRecovery recovery = LIVE_RECOVERY.get(entity);
		if (recovery == null || recovery.renderer != renderer) {
			recovery = new LiveRecovery(renderer, new SurgicalLiveMeshCache());
			LIVE_RECOVERY.put(entity, recovery);
		}
		CapturedInput input = SurgicalRenderCapture.captureInput(renderer, entity, yaw, partialTick, false);
		SurgicalCapturedRenderPlan plan = SurgicalMeshRecovery.build(input, recovery.cache);
		LIVE_FRAMES.put(entity, new LiveFrame(renderer, yaw, partialTick, plan));
		return plan;
	}

	/** Drops the per-frame live mimic plans; called once per frame before any entity is drawn. */
	static void beginFrame() {
		LIVE_FRAMES.clear();
	}

	private record LiveFrame(EntityRenderer<LivingEntity> renderer, float yaw, float partialTick,
		SurgicalCapturedRenderPlan plan) {}

	private record LiveRecovery(EntityRenderer<LivingEntity> renderer, SurgicalLiveMeshCache cache) {}

	int cubeCount() {
		return components.size();
	}

	SurgicalModelRenderContext.Snapshot render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int expectedCubeCount, BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations,
		boolean collectGeometry, @Nullable Vec3 cameraPosition, boolean renderSourceGeometry) {
		return render(poseStack, buffer, packedLight, expectedCubeCount, presentCubes, cubeOffsets,
			cubeRotations, collectGeometry, cameraPosition, renderSourceGeometry, true, null);
	}

	SurgicalModelRenderContext.Snapshot render(PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int expectedCubeCount, BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, boolean collectGeometry,
		@Nullable Vec3 cameraPosition, boolean renderSourceGeometry, @Nullable Boolean hurtOverlay) {
		return render(poseStack, buffer, packedLight, expectedCubeCount, presentCubes, cubeOffsets,
			cubeRotations, collectGeometry, cameraPosition, renderSourceGeometry, true, hurtOverlay);
	}

	private SurgicalModelRenderContext.Snapshot render(PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int expectedCubeCount, BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, boolean collectGeometry,
		@Nullable Vec3 cameraPosition, boolean renderSourceGeometry, boolean renderExtras,
		@Nullable Boolean hurtOverlay) {
		if (renderSourceGeometry) {
			for (Component component : appearanceComponents)
				if (isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSource(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), true, hurtOverlay);
		} else {
			float outerShellShrink = outerShellShrink(poseStack.last().pose());
			for (Component component : appearanceComponents) {
				if (!isPresent(component.id, expectedCubeCount, presentCubes))
					continue;
				if (component.preserveSource)
					component.renderSource(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), false, hurtOverlay);
				else
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), false, outerShellShrink, hurtOverlay);
			}
			for (Component component : appearanceComponents)
				if (!component.preserveSource && isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSlime(poseStack, buffer, packedLight, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), true, outerShellShrink, hurtOverlay);
			// Villager professions, emissive eyes and similar layers intentionally redraw the
			// same model cube with another material. Keep those pixels attached to the one
			// surgical component instead of admitting duplicate topology or discarding them.
			for (Component component : appearanceComponents)
				if (!component.preserveSource && isPresent(component.id, expectedCubeCount, presentCubes))
					component.renderSurfaceOverlays(poseStack, buffer, packedLight,
						cubeOffsets.get(component.id), cubeRotations.get(component.id), hurtOverlay);
		}

		// Lines, text, beams and genuinely non-cuboid meshes are visual effects rather than
		// surgical topology. Preserve them exactly and keep them out of cube numbering.
		if (renderExtras)
			for (SourceBatch extra : extras)
				extra.render(poseStack, buffer, packedLight, null, null);

		if (!collectGeometry)
			return new SurgicalModelRenderContext.Snapshot(components.size(), List.of());
		if (traitAppearance) {
			List<SurgicalModelRenderContext.CubeGeometry> geometry = new ArrayList<>(appearanceComponents.size());
			for (Component component : appearanceComponents)
				if (isPresent(component.id, expectedCubeCount, presentCubes))
					geometry.add(component.geometry(poseStack, cubeOffsets.get(component.id),
						cubeRotations.get(component.id), cameraPosition));
			return new SurgicalModelRenderContext.Snapshot(components.size(), geometry);
		}
		return snapshot(poseStack, expectedCubeCount, presentCubes, cubeOffsets, cubeRotations, cameraPosition);
	}

	SurgicalModelRenderContext.Snapshot renderSingleCube(PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int cube) {
		if (cube < 0 || cube >= components.size())
			return new SurgicalModelRenderContext.Snapshot(0, List.of());
		BitSet present = new BitSet(components.size());
		present.set(cube);
		return render(poseStack, buffer, packedLight, components.size(), present, NO_OFFSETS,
			NO_ROTATIONS, false, null, false, false, null);
	}

	@Nullable
	SurgicalModelRenderContext.CubeGeometry singleCubeGeometry(int cube) {
		if (cube < 0 || cube >= components.size())
			return null;
		return components.get(cube).geometry(new PoseStack(), null, null, null);
	}

	/** Attach the live item to the hand's measured model frame after all surgical animation. */
	void renderMainHandItem(SlimeBionicEntity owner, int cube, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, @Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation) {
		if (cube < 0 || cube >= components.size() || owner.getMainHandItem().isEmpty()) return;
		Component hand = traitAppearance ? appearanceComponents.stream()
			.filter(component -> component.id == cube).findFirst().orElse(null) : components.get(cube);
		if (hand == null || hand.modelCorners.isEmpty()) return;
		Matrix4f modelFrame = cornerFrame(hand.modelCorners, 1.0f / 16.0f);
		if (Math.abs(modelFrame.determinant()) < 1.0e-12f) return;
		var geometry = hand.geometry(poseStack, offset, rotation, null);
		Matrix4f transform = cornerFrame(geometry.corners(), 1.0f).mul(modelFrame.invert());
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		for (Vec3 point : hand.modelCorners) {
			minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y); minZ = Math.min(minZ, point.z);
		}
		PoseStack itemPose = new PoseStack();
		itemPose.mulPose(transform);
		// The vanilla humanoid grip is at the lower, front edge of the arm. Use the measured
		// dimensions so short, long, scaled and transplanted forearms keep their own grip.
		itemPose.translate((minX + maxX) / 32.0d, maxY / 16.0d, minZ / 16.0d);
		itemPose.mulPose(Axis.XP.rotationDegrees(-90.0f));
		itemPose.mulPose(Axis.YP.rotationDegrees(180.0f));
		boolean left = owner.getMainArm() == HumanoidArm.LEFT;
		Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(owner,
			owner.getMainHandItem(), left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				: ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, itemPose, buffer, packedLight);
	}

	private static Matrix4f cornerFrame(List<Vec3> corners, float scale) {
		Vec3 origin = corners.getFirst().scale(scale);
		Vec3 a = corners.get(1).scale(scale).subtract(origin);
		Vec3 b = corners.get(2).scale(scale).subtract(origin);
		Vec3 c = corners.get(4).scale(scale).subtract(origin);
		return new Matrix4f().setColumn(0, new Vector4f((float) a.x, (float) a.y, (float) a.z, 0))
			.setColumn(1, new Vector4f((float) b.x, (float) b.y, (float) b.z, 0))
			.setColumn(2, new Vector4f((float) c.x, (float) c.y, (float) c.z, 0))
			.setColumn(3, new Vector4f((float) origin.x, (float) origin.y, (float) origin.z, 1));
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
		int count = expectedCubeCount <= 0 ? components.size() : presentCubes.cardinality();
		List<SurgicalModelRenderContext.CubeGeometry> geometry = new ArrayList<>(count);
		for (int cube = expectedCubeCount <= 0 ? 0 : presentCubes.nextSetBit(0);
			cube >= 0 && cube < components.size();
			cube = expectedCubeCount <= 0 ? cube + 1 : presentCubes.nextSetBit(cube + 1)) {
			Component component = components.get(cube);
			geometry.add(component.geometry(poseStack, cubeOffsets.get(cube), cubeRotations.get(cube), cameraPosition));
		}
		return new SurgicalModelRenderContext.Snapshot(components.size(), geometry);
	}

	static void clearResources() {
		resourceGeneration++;
		LIVE_FRAMES.clear();
		LIVE_RECOVERY.clear();
		SurgicalTextureMasks.ALPHA_MASKS.clear();
		innerCube = null;
		outerCube = null;
	}

	static RenderType translucentPreviewType(RenderType renderType) {
		return SurgicalMaterialBinding.translucentPreviewType(renderType);
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

	record RecoveredCandidate(RecoveredCuboid cuboid, List<SurgicalRenderCapture.CapturedVertex> vertices) {}

	record PendingQuad(int streamId, RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices,
		int order) {}

	record PendingMesh(int streamId, RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices,
		int order) {}

	static record CapturedInput(List<SurgicalRenderCapture.CaptureStream> streams, List<ObservedCube> observedCubes,
		boolean topology, boolean bindPlayerSkinLayers, Set<GeometryKey> saddleCubes) {}

	record ModelCubeMatch(List<Vec3> corners, boolean head,
		Set<String> partNames, Set<BionicModelNode> nodes, @Nullable Matrix4f pose) {
		static final ModelCubeMatch NONE = new ModelCubeMatch(List.of(), false, Set.of(), Set.of(), null);

		ModelCubeMatch {
			corners = List.copyOf(corners);
			partNames = Set.copyOf(partNames);
		}
	}

	static final class ComponentBuilder {
		final int order;
		final RecoveredCuboid cuboid;
		final SurgicalMaterialBinding.OverlayBox overlayBox;
		private final List<Vec3> modelCorners;
		private boolean head;
		boolean preserveMaterial;
		private final Set<String> partNames;
		private final Set<BionicModelNode> nodes;
		@Nullable
		private final Matrix4f modelPose;
		private final boolean topology;
		final List<SourceBatch> batches = new ArrayList<>();
		private final List<SourceBatch> surfaceOverlays = new ArrayList<>();
		final BitSet captureStreams = new BitSet();
		@Nullable
		RenderType primaryRenderType;
		private List<SurgicalModelRenderContext.FaceGrid> faceGrids = List.of();

		ComponentBuilder(int order, RecoveredCuboid cuboid, ModelCubeMatch modelCube,
			boolean topology) {
			this.order = order;
			this.cuboid = cuboid;
			this.overlayBox = new SurgicalMaterialBinding.OverlayBox(cuboid);
			this.modelCorners = modelCube.corners();
			this.head = modelCube.head();
			this.partNames = new HashSet<>(modelCube.partNames());
			this.nodes = new HashSet<>(modelCube.nodes());
			this.modelPose = modelCube.pose();
			this.topology = topology;
		}

		/** Only shared provenance may label a recovered box spanning several emitted faces. */
		void observeNames(List<SurgicalRenderCapture.CapturedVertex> vertices) {
			if (vertices.isEmpty()) return;
			var first = vertices.getFirst().source();
			Set<String> common = new HashSet<>(first.names());
			Set<BionicModelNode> commonNodes = new HashSet<>(first.nodes());
			boolean capturedHead = first.head();
			for (SurgicalRenderCapture.CapturedVertex vertex : vertices) {
				if (vertex.source() != first) { common.retainAll(vertex.source().names()); commonNodes.retainAll(vertex.source().nodes()); }
				capturedHead &= vertex.source().head();
			}
			for (String name : common)
				if (partNames.size() < 16) partNames.add(name);
			head |= capturedHead;
			for (BionicModelNode node : commonNodes) if (nodes.size() < 16) nodes.add(node);
		}

		void observeMaterial(RenderType renderType) {
			if (primaryRenderType == null)
				primaryRenderType = renderType;
			ResourceLocation texture = SurgicalMaterialBinding.renderTypeTexture(renderType);
			if (texture == null) return;
			for (String name : List.copyOf(partNames)) {
				if (name.startsWith("material/")) continue;
				String qualified = "material/" + texture.getNamespace() + "/" + texture.getPath() + "/" + name;
				if (partNames.size() < 16 && BionicAnatomySnapshot.validPartName(qualified)) partNames.add(qualified);
			}
		}

		void addVisibleBatch(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices) {
			preserveMaterial |= SurgicalMaterialBinding.isMushroomMaterial(renderType, vertices);
			boolean surfaceOverlay = primaryRenderType != renderType;
			if (topology && faceGrids.isEmpty())
				faceGrids = SurgicalMeshRecovery.recoverFaceGrids(cuboid, renderType, vertices);
			SourceBatch batch = new SourceBatch(renderType, List.copyOf(vertices), surfaceOverlay);
			batches.add(batch);
			if (surfaceOverlay)
				surfaceOverlays.add(batch);
		}

		void addSurfaceOverlayBatch(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices) {
			SourceBatch batch = new SourceBatch(renderType, List.copyOf(vertices), true);
			batches.add(batch);
			surfaceOverlays.add(batch);
		}

		void bindSkinLayer(ComponentBuilder layer) {
			batches.addAll(layer.batches);
		}

		Component build(int id, boolean preserveSource) {
			return new Component(id, cuboid.corners, cuboid.a, cuboid.b, cuboid.c,
				modelCorners, faceGrids, head, partNames, nodes, modelPose, preserveSource,
				List.copyOf(batches), List.copyOf(surfaceOverlays));
		}
	}

	record RecoveredCuboid(List<Vector3f> corners, Vector3f a, Vector3f b, Vector3f c) {}

	static final class ObservedCube {
		final List<Vector3f> transformedCorners;
		final List<Vec3> modelCorners;
		final boolean head;
		final Set<String> partNames;
		final Set<BionicModelNode> nodes;
		final Matrix4f pose;
		/** Computed once here rather than per comparison in {@link #matchingModelCube}. */
		final GeometryKey key;

		ObservedCube(List<Vector3f> transformedCorners, List<Vec3> modelCorners,
			boolean head, Set<String> partNames, Set<BionicModelNode> nodes, Matrix4f pose) {
			// Both lists are built fresh per cube at the single call site and handed straight over, so
			// they only need freezing, not the deep Vector3f copy this used to make.
			this.transformedCorners = List.copyOf(transformedCorners);
			this.modelCorners = List.copyOf(modelCorners);
			this.head = head;
			this.partNames = Set.copyOf(partNames);
			this.nodes = Set.copyOf(nodes);
			this.pose = pose;
			this.key = GeometryKey.of(this.transformedCorners);
		}
	}

	record GeometryKey(List<QuantizedPoint> points) {
		static GeometryKey of(List<Vector3f> corners) {
			QuantizedPoint[] points = new QuantizedPoint[corners.size()];
			for (int i = 0; i < points.length; i++)
				points[i] = QuantizedPoint.of(corners.get(i));
			Arrays.sort(points);
			int unique = 0;
			for (int i = 0; i < points.length; i++)
				if (unique == 0 || !points[i].equals(points[unique - 1]))
					points[unique++] = points[i];
			return new GeometryKey(List.copyOf(Arrays.asList(points).subList(0, unique)));
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

	static final class Component {
		private final int id;
		final List<Vector3f> corners;
		final Vector3f a;
		final Vector3f b;
		final Vector3f c;
		private final List<Vec3> modelCorners;
		private final List<SurgicalModelRenderContext.FaceGrid> faceGrids;
		final boolean head;
		final Set<String> partNames;
		private final Set<BionicModelNode> nodes;
		@Nullable
		private final Matrix4f modelPose;
		private final boolean preserveSource;
		private final List<SourceBatch> batches;
		private final List<SourceBatch> surfaceOverlays;
		private final Vector3f rotationCenter;

		private Component(int id, List<Vector3f> corners, Vector3f a, Vector3f b, Vector3f c,
			List<Vec3> modelCorners, List<SurgicalModelRenderContext.FaceGrid> faceGrids, boolean head,
			Set<String> partNames, Set<BionicModelNode> nodes, @Nullable Matrix4f modelPose,
			boolean preserveSource,
			List<SourceBatch> batches, List<SourceBatch> surfaceOverlays) {
			this.id = id;
			this.corners = corners.stream().map(Vector3f::new).toList();
			this.a = new Vector3f(a);
			this.b = new Vector3f(b);
			this.c = new Vector3f(c);
			this.modelCorners = List.copyOf(modelCorners);
			this.faceGrids = List.copyOf(faceGrids);
			this.head = head;
			this.partNames = Set.copyOf(partNames);
			this.nodes = Set.copyOf(nodes);
			this.modelPose = modelPose;
			this.preserveSource = preserveSource;
			this.batches = batches;
			this.surfaceOverlays = surfaceOverlays;
			this.rotationCenter = center();
		}

		private Component(Component appearance, Component owner) {
			this.id = owner.id;
			this.corners = appearance.corners;
			this.a = appearance.a;
			this.b = appearance.b;
			this.c = appearance.c;
			this.modelCorners = appearance.modelCorners;
			this.faceGrids = appearance.faceGrids;
			this.head = appearance.head;
			this.partNames = appearance.partNames;
			this.nodes = appearance.nodes;
			this.modelPose = appearance.modelPose;
			this.preserveSource = appearance.preserveSource;
			this.batches = appearance.batches;
			this.surfaceOverlays = appearance.surfaceOverlays;
			this.rotationCenter = owner.center();
		}

		private void renderSource(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation,
			boolean offsetSurfaceOverlays, @Nullable Boolean hurtOverlay) {
			for (SourceBatch batch : batches)
				batch.renderSource(poseStack, buffer, packedLight, offset, rotation, rotationCenter,
					offsetSurfaceOverlays, hurtOverlay);
		}

		private void renderSurfaceOverlays(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation,
			@Nullable Boolean hurtOverlay) {
			for (SourceBatch batch : surfaceOverlays)
				batch.renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, rotationCenter,
					hurtOverlay);
		}

		private void renderSlime(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, boolean outer,
			float outerShellShrink, @Nullable Boolean hurtOverlay) {
			PoseStack cubePose = new PoseStack();
			Matrix4f basePose = new Matrix4f(poseStack.last().pose());
			Vector3f center = center();
			Vector3f worldCenter = basePose.transformPosition(rotationCenter, new Vector3f());
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
				: packed(batches.getFirst().vertices.getFirst().overlayU(),
					overlayV(batches.getFirst().vertices.getFirst(), hurtOverlay));
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
			Vector3f transformedCenter = pose.transformPosition(rotationCenter, new Vector3f());
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
			return new SurgicalModelRenderContext.CubeGeometry(id, transformed, modelCorners, faceGrids,
				head, partNames, nodes);
		}

		private Vector3f center() {
			return new Vector3f(corners.getFirst())
				.add(new Vector3f(a).mul(0.5f))
				.add(new Vector3f(b).mul(0.5f))
				.add(new Vector3f(c).mul(0.5f));
		}
	}

	record SourceBatch(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices, boolean surfaceOverlay) {
		private void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable Boolean hurtOverlay) {
			render(poseStack, buffer, packedLight, offset, SurgicalCubeRotation.IDENTITY,
				new Vector3f(), renderType, hurtOverlay);
		}

		private void renderSource(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center,
			boolean offsetSurfaceOverlay, @Nullable Boolean hurtOverlay) {
			if (surfaceOverlay)
				renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center,
					offsetSurfaceOverlay, hurtOverlay);
			else
				render(poseStack, buffer, packedLight, offset, rotation, center, renderType, hurtOverlay);
		}

		private void renderSurfaceOverlay(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center,
			@Nullable Boolean hurtOverlay) {
			renderSurfaceOverlay(poseStack, buffer, packedLight, offset, rotation, center, false, hurtOverlay);
		}

		private void renderSurfaceOverlay(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			@Nullable Vec3 offset, @Nullable SurgicalCubeRotation rotation, Vector3f center,
			boolean depthOffset, @Nullable Boolean hurtOverlay) {
			RenderType effectiveRenderType = renderType;
			ResourceLocation texture = SurgicalMaterialBinding.renderTypeTexture(renderType);
			// Villager type, profession and level layers redraw the exact source-model vertices.
			// The projected source has no inset slime shell to separate those coplanar fragments,
			// so retain the original material but use vanilla's view-space Z offset for cutout layers.
			if (depthOffset && texture != null && renderType == RenderType.entityCutoutNoCull(texture))
				effectiveRenderType = RenderType.entityCutoutNoCullZOffset(texture);
			render(poseStack, buffer, packedLight, offset, rotation, center, effectiveRenderType,
				hurtOverlay);
		}

		private void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, @Nullable Vec3 offset,
			@Nullable SurgicalCubeRotation rotation, Vector3f center, RenderType effectiveRenderType,
			@Nullable Boolean hurtOverlay) {
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
			for (SurgicalRenderCapture.CapturedVertex vertex : vertices) {
				position.set(vertex.x(), vertex.y(), vertex.z(), 1.0f);
				pose.transform(position);
				transformedNormal.set(vertex.normalX(), vertex.normalY(), vertex.normalZ());
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
				int capturedLight = packed(vertex.lightU(), vertex.lightV());
				int light = capturedLight == LightTexture.FULL_BRIGHT ? capturedLight : packedLight;
				consumer.addVertex(position.x + offsetX, position.y + offsetY, position.z + offsetZ)
					.setColor(vertex.red(), vertex.green(), vertex.blue(), vertex.alpha())
					.setUv(vertex.u(), vertex.v())
					.setUv1(vertex.overlayU(), overlayV(vertex, hurtOverlay))
					.setUv2(light & 0xffff, light >>> 16 & 0xffff)
					.setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
			}
		}
	}

	private static int overlayV(SurgicalRenderCapture.CapturedVertex vertex, @Nullable Boolean hurtOverlay) {
		// Live bodies supply their own damage state; previews keep the captured overlay.
		// Preserve U so renderer-specific white flashes still work.
		return hurtOverlay == null ? vertex.overlayV() : OverlayTexture.v(hurtOverlay);
	}
}
