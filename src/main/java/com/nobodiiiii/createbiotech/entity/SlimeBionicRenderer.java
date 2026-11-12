package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCubeGeometry;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.slimemimic.client.SlimeMimicDeathClient;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalClientTopology;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalClientExecutors;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTablePoseResolver;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.entity.client.BionicGuardianBeamRenderer;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAttackRangeRenderer;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SlimeBionicRenderer extends EntityRenderer<SlimeBionicEntity> {
	private static final float MIN_SHADOW_RADIUS = 0.15f;
	private static final float MAX_SHADOW_RADIUS = 1.0f;
	private static final float SITTING_HEIGHT_SCALE = 0.8f;
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final Map<SlimeBionicEntity, CachedGeometry> GEOMETRY = new WeakHashMap<>();
	private static final Map<SlimeBionicEntity, CompositeCachedGeometry> COMPOSITE_GEOMETRY = new WeakHashMap<>();
	private static final Map<SlimeBionicEntity, FirstPersonArms> FIRST_PERSON_ARMS = new WeakHashMap<>();
	private static final Cache<GeometryKey, CachedGeometry> SHARED_GEOMETRY = CacheBuilder.newBuilder()
		.concurrencyLevel(1).maximumSize(64).build();
	private static final Cache<GeometryKey, CompositeCachedGeometry> SHARED_COMPOSITE_GEOMETRY = CacheBuilder.newBuilder()
		.concurrencyLevel(1).maximumSize(64).build();

	public SlimeBionicRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.25f;
	}

	public static void clearCache() {
		GEOMETRY.clear();
		COMPOSITE_GEOMETRY.clear();
		FIRST_PERSON_ARMS.clear();
		SHARED_GEOMETRY.invalidateAll();
		SHARED_COMPOSITE_GEOMETRY.invalidateAll();
		SlimeBionicAnimator.clearCache();
		com.nobodiiiii.createbiotech.entity.client.BionicWingAnimation.clear();
		SlimeBionicAttackRangeRenderer.clearCache();
	}

	/** Renders one genuine arm chain, including both upper arm and forearm when an elbow is present. */
	public boolean renderFirstPersonArm(SlimeBionicEntity entity, HumanoidArm side,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly == null)
			return false;
		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		List<SlimeBionicAnimator.SourceState> sourceStates = firstPersonSourceStates(entity, assembly,
			partialTick, packedLight);
		if (sourceStates == null)
			return false;

		FirstPersonArms arms = FIRST_PERSON_ARMS.get(entity);
		if (arms == null || arms.assembly != assembly || arms.sourceStates != sourceStates
			|| !arms.wings.equals(entity.getBionicTraits().wingMembers())) {
			arms = new FirstPersonArms(assembly, sourceStates, entity.getBionicTraits().wingMembers(),
				measureFirstPersonArm(entity, assembly, sourceStates, false),
				measureFirstPersonArm(entity, assembly, sourceStates, true));
			FIRST_PERSON_ARMS.put(entity, arms);
		}

		FirstPersonArm arm = side == HumanoidArm.LEFT ? arms.left : arms.right;
		if (arm == null)
			return false;
		EntityGeometry.Bounds bounds = arm.bounds;
		float anchorX = side == HumanoidArm.RIGHT ? -6.0f / 16.0f : 6.0f / 16.0f;
		float visibleLength = Mth.clamp(bounds.sizeY(), 12.0f / 16.0f, 16.0f / 16.0f);
		poseStack.pushPose();
		// Position the complete arm chain as one unit and move any length beyond 16 pixels outside
		// the viewport.
		poseStack.translate(anchorX - bounds.centerX(), visibleLength + bounds.minY(),
			bounds.centerZ());
		poseStack.scale(1.0f, -1.0f, -1.0f);
		// The selected chain keeps its captured rest pose and does not inherit the world-view
		// shoulder/elbow animation.
		BodyFrame componentFrame = BodyFrame.of(0.0f, poseStack.last().pose());
		renderFirstPersonSources(entity, assembly, sourceStates, arm.cubes, componentFrame, poseStack, buffer,
			packedLight, partialTick);
		poseStack.popPose();
		return true;
	}

	/** The cached rest states of the body being possessed, measuring them first when needed. */
	@Nullable
	private static List<SlimeBionicAnimator.SourceState> firstPersonSourceStates(SlimeBionicEntity entity,
		SurgicalAssembly assembly, float partialTick, int packedLight) {
		boolean slimeForm = SlimeMimicHandler.isSlimeMimic(entity);
		if (isComposite(assembly)) {
			CompositeCachedGeometry cached = COMPOSITE_GEOMETRY.get(entity);
			if (cached == null || cached.assembly != assembly || cached.slimeForm != slimeForm) {
				cached = measureComposite(entity, assembly, partialTick, packedLight, slimeForm);
				if (cached == null)
					return null;
				COMPOSITE_GEOMETRY.put(entity, cached);
			}
			cached.bodyGeometry.update(entity);
			return cached.sources;
		}

		LivingEntity preview = SurgicalSourceModelRenderer.preview(assembly.profile());
		if (preview == null)
			return null;
		((SlimeMimicAccess) (Object) preview).createBiotech$setSlimeMimic(true);
		CachedGeometry cached = GEOMETRY.get(entity);
		if (cached == null || cached.assembly != assembly || cached.slimeForm != slimeForm) {
			cached = rebuildGeometry(entity, preview, assembly, partialTick, packedLight, slimeForm);
			if (cached == null)
				return null;
			GEOMETRY.put(entity, cached);
		}
		cached.bodyGeometry.update(entity);
		return cached.sourceStates;
	}

	/** Warm the rest geometry and await only its worker result before baking an icon. Never blocks. */
	public static boolean prepareBodyGeometry(SlimeBionicEntity entity) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly == null || firstPersonSourceStates(entity, assembly, 1.0f,
			net.minecraft.client.renderer.LightTexture.FULL_BRIGHT) == null)
			return true;
		return isComposite(assembly) ? COMPOSITE_GEOMETRY.get(entity).bodyGeometry.resolved
			: GEOMETRY.get(entity).bodyGeometry.resolved;
	}

	/** A body whose sources keep their table layout, as opposed to one unedited single donor. */
	private static boolean isComposite(SurgicalAssembly assembly) {
		return assembly.preservesLayout() || assembly.sources().size() > 1;
	}

	@Nullable
	private static FirstPersonArm measureFirstPersonArm(SlimeBionicEntity entity, SurgicalAssembly assembly,
		List<SlimeBionicAnimator.SourceState> sourceStates, boolean left) {
		List<SurgicalAssembly.CombinationMember> members = SlimeBionicAnimator
			.firstPersonArm(entity, assembly, sourceStates, left);
		if (members == null)
			return null;
		Map<Integer, BitSet> cubes = new java.util.HashMap<>();
		for (SurgicalAssembly.CombinationMember member : members)
			cubes.computeIfAbsent(member.source(), ignored -> new BitSet()).set(member.cube());
		EntityGeometry.Bounds bounds = new EntityGeometry.Bounds();
		for (SurgicalAssembly.CombinationMember member : members) {
			SlimeBionicAnimator.CubeBox box = sourceStates.get(member.source()).boxes().get(member.cube());
			if (box == null)
				continue;
			for (Vec3 point : box.points())
				bounds.include((float) point.x, (float) point.y, (float) point.z);
		}
		return bounds.hasVertices() ? new FirstPersonArm(cubes, bounds) : null;
	}

	private static void renderFirstPersonSources(SlimeBionicEntity entity, SurgicalAssembly assembly,
		List<SlimeBionicAnimator.SourceState> sourceStates, Map<Integer, BitSet> cubes, BodyFrame componentFrame,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
		boolean composite = isComposite(assembly);
		poseStack.pushPose();
		if (composite)
			SurgicalTablePoseResolver.applyInverseRotation(poseStack, assembly.layoutLayPose());
		for (int sourceIndex = 0; sourceIndex < assembly.sources().size(); sourceIndex++) {
			BitSet selected = cubes.get(sourceIndex);
			if (selected == null || selected.isEmpty())
				continue;
			SurgicalAssembly.Source source = assembly.sources().get(sourceIndex);
			LivingEntity preview = SurgicalSourceModelRenderer.preview(source.profile());
			if (preview == null)
				continue;
			poseStack.pushPose();
			if (composite) {
				poseStack.translate(source.originOffset().x, source.originOffset().y,
					source.originOffset().z);
				if (assembly.preservesLayout())
					SurgicalTablePoseResolver.resolve(source.layPose()).apply(poseStack);
			}
			SlimeBionicAnimator.SourceState state = sourceStates.get(sourceIndex);
			// First-person arms do not display the body's hurt overlay.
			SurgicalSourceModelRenderer.renderEquipped(preview, source.cubeCount(),
				SurgicalSourceModelRenderer.visibleCubes(preview, selected, entity.isSheared()),
				componentFrame.rotateOffsets(state.offsets(), Map.of()),
				componentFrame.rotateRotations(state.rotations(), Map.of()),
				poseStack, buffer, packedLight, partialTick, false, null, true, false,
				entity.getBionicEquipment().saddleCubes(sourceIndex), entity.getBionicEquipment().chestCubes(sourceIndex), entity, sourceIndex);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	@Override
	public boolean shouldRender(SlimeBionicEntity entity, Frustum frustum, double cameraX, double cameraY,
		double cameraZ) {
		return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)
			|| BionicGuardianBeamRenderer.isVisible(entity, frustum);
	}

	@Override
	public void render(SlimeBionicEntity entity, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		// Scaling by one is an exact no-op, so only a lit fuse pays for the trait lookup.
		int fuseTicks = entity.getProductionFlags() >> 2;
		if (fuseTicks != 0) {
			float fuse = fuseTicks / (float) Math.max(1,
				entity.getBionicTraits().parameter(BionicTrait.SELF_DESTRUCT, "fuse_ticks", 30));
			float swell = 1 + Math.min(1, fuse) * 0.2f;
			poseStack.scale(swell, swell, swell);
		}
		renderBody(entity, yaw, partialTick, poseStack, buffer, packedLight);
		poseStack.popPose();
		if (!entity.isDeadOrDying()) com.nobodiiiii.createbiotech.entity.client.BionicTraitClient.render(entity, partialTick, poseStack, buffer, packedLight);
	}

	private void renderBody(SlimeBionicEntity entity, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		boolean hideOriginal = entity.isDeadOrDying() && SlimeMimicHandler.isSlimeMimic(entity);
		if (hideOriginal && SlimeMimicDeathClient.hasReported(entity))
			return;
		MultiBufferSource sourceBuffer = hideOriginal ? hiddenBuffer() : buffer;
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly != null) {
			sourceBuffer = sittingModelBuffer(entity, poseStack, sourceBuffer);
			float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
			BodyFrame bodyFrame = BodyFrame.of(entity, bodyYaw, poseStack.last().pose());
			if (isComposite(assembly)) {
				renderComposite(entity, assembly, bodyFrame, partialTick, poseStack, sourceBuffer, packedLight);
				if (hideOriginal)
					return;
				SlimeBionicAttackRangeRenderer.render(entity, assembly, partialTick, poseStack, buffer);
				super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
				return;
			}
			COMPOSITE_GEOMETRY.remove(entity);
			poseStack.pushPose();
			LivingEntity preview = SurgicalSourceModelRenderer.preview(assembly.profile());
			boolean slimeForm = SlimeMimicHandler.isSlimeMimic(entity);
			if (preview != null)
				((SlimeMimicAccess) (Object) preview).createBiotech$setSlimeMimic(true);
			CachedGeometry cached = GEOMETRY.get(entity);
			boolean rebuildGeometry = cached == null || cached.assembly != assembly
				|| cached.slimeForm != slimeForm;
			if (rebuildGeometry) {
				cached = rebuildGeometry(entity, preview, assembly, partialTick, packedLight, slimeForm);
				if (cached != null) {
					GEOMETRY.put(entity, cached);
				} else {
					GEOMETRY.remove(entity);
				}
			}

			BitSet presentCubes = cached == null ? assembly.presentCubes() : cached.presentCubes;
			Map<Integer, Vec3> localOffsets = Map.of();
			Map<Integer, SurgicalCubeRotation> localRotations = assembly.cubeRotations();
			SlimeBionicAnimator.Frame frame = SlimeBionicAnimator.Frame.EMPTY;
			bodyFrame.apply(poseStack);
			if (cached != null) {
				cached.bodyGeometry.update(entity);
				Vec3 offset = cached.bodyGeometry.modelOffset;
				poseStack.translate(offset.x, offset.y, offset.z);
				localOffsets = cached.sourceStates.getFirst().offsets();
				frame = SlimeBionicAnimator.resolve(entity, cached.rig, partialTick).getFirst();
			}
			if (preview != null) {
				boolean reportDeath = hideOriginal;
				Vec3 cameraPosition = reportDeath
					? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition() : null;
				SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.renderEquipped(preview,
					assembly.cubeCount(), SurgicalSourceModelRenderer.visibleCubes(preview, presentCubes, entity.isSheared()),
					bodyFrame.rotateOffsets(localOffsets, frame.offsets()),
					bodyFrame.rotateRotations(localRotations, frame.rotations()),
					poseStack, sourceBuffer, packedLight, partialTick, reportDeath, cameraPosition,
					!slimeForm, entity.hurtTime > 0 || entity.deathTime > 0,
					entity.getBionicEquipment().saddleCubes(0), entity.getBionicEquipment().chestCubes(0), entity, 0);
				if (reportDeath)
					SlimeMimicDeathClient.report(entity, snapshot.cubes().stream()
						.map(cube -> new SlimeMimicCubeGeometry(0, cube.cubeId(), cube.corners()))
						.toList());
			}
			poseStack.popPose();
			if (hideOriginal)
				return;
			SlimeBionicAttackRangeRenderer.render(entity, assembly, partialTick, poseStack, buffer);
		} else {
			GEOMETRY.remove(entity);
			COMPOSITE_GEOMETRY.remove(entity);
		}
		if (!hideOriginal)
			super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
	}

	/** The player's shoulder layer draws the preserved body without world labels or combat overlays. */
	public void renderOnShoulder(SlimeBionicEntity entity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly != null)
			renderComposite(entity, assembly, BodyFrame.of(0, poseStack.last().pose()), partialTick,
				poseStack, buffer, packedLight);
	}

	private static MultiBufferSource hiddenBuffer() {
		EntityGeometry.Collector hidden = EntityGeometry.Collector.boundsOnly();
		return renderType -> hidden;
	}

	private static MultiBufferSource sittingModelBuffer(SlimeBionicEntity entity, PoseStack poseStack,
		MultiBufferSource buffer) {
		if (entity.isDeadOrDying() || entity.getBionicOwnerId() == null || !entity.isOrderedToSit()
			|| !entity.getBionicTraits().has(BionicTrait.TAMEABLE))
			return buffer;
		// Cube rotations are applied after the pose stack. Compress the finished model so every
		// articulated part and its equipment share the same vertical scale, anchored at the feet.
		Matrix4f incoming = poseStack.last().pose();
		Matrix4f transform = new Matrix4f(incoming).scale(1.0f, SITTING_HEIGHT_SCALE, 1.0f)
			.mul(new Matrix4f(incoming).invert());
		Matrix3f normal = new Matrix3f(transform).invert().transpose();
		return renderType -> new SittingVertexConsumer(buffer.getBuffer(renderType), transform, normal,
			new Vector3f());
	}

	private record SittingVertexConsumer(VertexConsumer delegate, Matrix4f transform, Matrix3f normal,
		Vector3f scratch) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			transform.transformPosition(x, y, z, scratch);
			delegate.addVertex(scratch.x, scratch.y, scratch.z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			normal.transform(x, y, z, scratch);
			if (scratch.lengthSquared() > 1.0e-8f)
				scratch.normalize();
			delegate.setNormal(scratch.x, scratch.y, scratch.z);
			return this;
		}
	}

	private static void renderComposite(SlimeBionicEntity entity, SurgicalAssembly assembly,
		BodyFrame bodyFrame, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		GEOMETRY.remove(entity);
		boolean slimeForm = SlimeMimicHandler.isSlimeMimic(entity);
		CompositeCachedGeometry cached = COMPOSITE_GEOMETRY.get(entity);
		if (cached == null || cached.assembly != assembly || cached.slimeForm != slimeForm) {
			cached = measureComposite(entity, assembly, partialTick, packedLight, slimeForm);
			if (cached == null)
				COMPOSITE_GEOMETRY.remove(entity);
			else
				COMPOSITE_GEOMETRY.put(entity, cached);
		}

		List<SlimeBionicAnimator.Frame> frames = cached == null ? List.of()
			: SlimeBionicAnimator.resolve(entity, cached.rig, partialTick);
		List<SlimeMimicCubeGeometry> deathGeometry = entity.isDeadOrDying() && slimeForm
			? new ArrayList<>() : null;
		Vec3 cameraPosition = deathGeometry == null ? null
			: Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		poseStack.pushPose();
		bodyFrame.apply(poseStack);
		if (cached != null) {
			cached.bodyGeometry.update(entity);
			Vec3 offset = cached.bodyGeometry.modelOffset;
			poseStack.translate(offset.x, offset.y, offset.z);
		}
		renderCompositeSources(entity, assembly, bodyFrame, partialTick, poseStack, buffer, packedLight,
			!slimeForm, cached == null ? null : cached.sources, frames, null, deathGeometry, cameraPosition);
		poseStack.popPose();
		if (deathGeometry != null)
			SlimeMimicDeathClient.report(entity, deathGeometry);
	}

	/**
	 * Measures the rest pose once per cached configuration.
	 *
	 * <p>The model offset that grounds the body and places its arm-free collision-core projection at
	 * the entity origin has to come from the still rest pose: remeasuring an animated frame would
	 * make a walking body bob as its own bounds shift.</p>
	 */
	@Nullable
	private static CompositeCachedGeometry measureComposite(SlimeBionicEntity entity,
		SurgicalAssembly assembly, float partialTick, int packedLight, boolean slimeForm) {
		GeometryKey key = new GeometryKey(assembly, slimeForm, SurgicalSourceModelRenderer.resourceGeneration());
		CompositeCachedGeometry shared = SHARED_COMPOSITE_GEOMETRY.getIfPresent(key);
		if (shared != null) {
			SurgicalProfiler.count("bionicGeometry(compositeCacheHit)");
			shared.bodyGeometry.update(entity);
			return shared;
		}
		EntityGeometry.Collector geometry = EntityGeometry.Collector.boundsOnly();
		MultiBufferSource measuringBuffer = renderType -> geometry;
		List<SlimeBionicAnimator.SourceState> sources = new ArrayList<>(assembly.sources().size());
		renderCompositeSources(entity, assembly, BodyFrame.IDENTITY, partialTick, new PoseStack(),
			measuringBuffer, packedLight, !slimeForm, null, List.of(), sources, null, null);
		if (!geometry.hasVertices())
			return null;
		EntityGeometry.Bounds bounds = geometry.bounds();
		List<SlimeBionicAnimator.SourceState> frozenSources = List.copyOf(sources);
		CompositeCachedGeometry measured = new CompositeCachedGeometry(assembly, slimeForm,
			frozenSources, SlimeBionicAnimator.rig(assembly, frozenSources),
			new BodyMeasurement(assembly, bounds, frozenSources));
		SHARED_COMPOSITE_GEOMETRY.put(key, measured);
		return measured;
	}

	@Nullable
	private static BodyGeometry measureBodyGeometry(SurgicalAssembly assembly,
		SurgicalBodyBounds.Envelope visible, List<SlimeBionicAnimator.SourceState> sources) {
		long started = SurgicalProfiler.begin();
		try {
			return computeBodyGeometry(assembly, visible, sources);
		} finally {
			SurgicalProfiler.end("bionicBodyMetrics(worker)", started);
		}
	}

	@Nullable
	private static BodyGeometry computeBodyGeometry(SurgicalAssembly assembly,
		SurgicalBodyBounds.Envelope visible, List<SlimeBionicAnimator.SourceState> sources) {
		Set<SurgicalAssembly.CombinationMember> armCubes = new HashSet<>();
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs())
			if (limb.type() == SurgicalLimbType.SHOULDER || limb.type() == SurgicalLimbType.ELBOW)
				armCubes.addAll(assembly.rotatingGroup(limb.childSource(), limb.childCube()));
		List<List<Vec3>> allCubes = new ArrayList<>();
		List<List<Vec3>> bodyCubes = new ArrayList<>();
		for (int source = 0; source < sources.size(); source++)
			for (Map.Entry<Integer, SlimeBionicAnimator.CubeBox> entry
				: sources.get(source).boxes().entrySet()) {
				List<Vec3> points = entry.getValue().points();
				allCubes.add(points);
				if (!armCubes.contains(new SurgicalAssembly.CombinationMember(source, entry.getKey())))
					bodyCubes.add(points);
			}
		SurgicalBodyBounds.Measurement bodyMeasurement = SurgicalBodyBounds.measureWithVolume(bodyCubes, allCubes, visible);
		if (bodyMeasurement == null)
			return null;
		SurgicalAssembly.BodyBounds bodyBounds = bodyMeasurement.bounds();
		double headCenterY = SlimeBionicAnimator.primaryHeadCenterY(assembly, sources);
		if (Double.isFinite(headCenterY))
			bodyBounds = bodyBounds.withEyeHeight(Math.max(0.0d, headCenterY - visible.minY()));
		SlimeBionicAnimator.MobilityMetrics mobility =
			SlimeBionicAnimator.measureMobility(assembly, sources);
		bodyBounds = bodyBounds.withMobility(mobility.averageLegLength(),
			mobility.groundedLegCount(), mobility.groundedKneeCount(), mobility.legVolumeRatio());
		List<Map<Integer, List<Vec3>>> hitboxCubes = sources.stream()
			.map(source -> source.boxes().entrySet().stream().collect(
				java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
					entry -> entry.getValue().points())))
			.toList();
		SurgicalAssembly.HitboxGeometry hitboxGeometry = SurgicalHitboxGeometry.measure(assembly,
			hitboxCubes, visible, bodyBounds);
		double bodyVolume = bodyMeasurement.volume();
		return new BodyGeometry(bodyBounds, hitboxGeometry, bodyVolume);
	}

	private static void applyBodyGeometry(SlimeBionicEntity entity, SurgicalAssembly assembly,
		@Nullable BodyGeometry geometry) {
		if (geometry != null && geometry.hitboxes != null)
			entity.setClientBodyGeometry(assembly, geometry.bounds, geometry.hitboxes, geometry.volume);
	}

	/** Centres the world entity on the already measured arm-free collision core's XZ projection. */
	private static Vec3 modelOffset(SurgicalBodyBounds.Envelope visible,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds) {
		// Preserve Bounds.centerX/Z's float rounding from the captured render geometry.
		double centerX = ((float) visible.minX() + (float) visible.maxX()) * 0.5f;
		double centerZ = ((float) visible.minZ() + (float) visible.maxZ()) * 0.5f;
		if (bodyBounds != null) {
			centerX += bodyBounds.centerX();
			centerZ += bodyBounds.centerZ();
		}
		return new Vec3(-centerX, -visible.minY(), -centerZ);
	}

	/**
	 * Renders or measures every source of a composite body.
	 *
	 * <p>When {@code restStates} is supplied each source's rest geometry is collected instead of an
	 * animation frame being applied, which is how the still reference pose is captured. A render pass
	 * hands those states back as {@code cachedStates}, so the upright component transforms measured
	 * with them are reused instead of being derived again.</p>
	 */
	private static void renderCompositeSources(SlimeBionicEntity entity, SurgicalAssembly assembly,
		BodyFrame bodyFrame, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		boolean renderSourceGeometry, @Nullable List<SlimeBionicAnimator.SourceState> cachedStates,
		List<SlimeBionicAnimator.Frame> frames,
		@Nullable List<SlimeBionicAnimator.SourceState> restStates,
		@Nullable List<SlimeMimicCubeGeometry> deathGeometry, @Nullable Vec3 cameraPosition) {
		poseStack.pushPose();
		SurgicalTablePoseResolver.applyInverseRotation(poseStack, assembly.layoutLayPose());
		List<SurgicalAssembly.Source> sources = assembly.sources();
		for (int index = 0; index < sources.size(); index++) {
			SurgicalAssembly.Source source = sources.get(index);
			Map<Integer, Vec3> offsets = cachedStates == null
				? uprightOffsets(assembly, source) : cachedStates.get(index).offsets();
			Map<Integer, SurgicalCubeRotation> rotations = cachedStates == null
				? uprightRotations(assembly, source) : cachedStates.get(index).rotations();
			LivingEntity preview = SurgicalSourceModelRenderer.preview(source.profile());
			if (preview == null) {
				if (restStates != null)
					restStates.add(new SlimeBionicAnimator.SourceState(Map.of(), offsets, rotations));
				continue;
			}
			((SlimeMimicAccess) (Object) preview).createBiotech$setSlimeMimic(true);
			SlimeBionicAnimator.Frame frame = index < frames.size() ? frames.get(index)
				: SlimeBionicAnimator.Frame.EMPTY;
			Map<Integer, Vec3> renderOffsets = restStates == null
				? bodyFrame.rotateOffsets(offsets, frame.offsets()) : offsets;
			Map<Integer, SurgicalCubeRotation> renderRotations = restStates == null
				? bodyFrame.rotateRotations(rotations, frame.rotations()) : rotations;
			poseStack.pushPose();
			poseStack.translate(source.originOffset().x, source.originOffset().y, source.originOffset().z);
			if (assembly.preservesLayout())
				SurgicalTablePoseResolver.resolve(source.layPose()).apply(poseStack);
			SurgicalModelRenderContext.Snapshot snapshot = restStates != null
				? SurgicalSourceModelRenderer.render(preview, source.cubeCount(), source.presentCubes(),
					renderOffsets, renderRotations, poseStack, buffer, packedLight, 0, partialTick, true, cameraPosition,
					renderSourceGeometry)
				: SurgicalSourceModelRenderer.renderEquipped(preview, source.cubeCount(),
					SurgicalSourceModelRenderer.visibleCubes(preview, source.presentCubes(), entity.isSheared()),
					renderOffsets, renderRotations, poseStack, buffer, packedLight, partialTick, deathGeometry != null,
					cameraPosition, renderSourceGeometry, entity.hurtTime > 0 || entity.deathTime > 0,
					entity.getBionicEquipment().saddleCubes(index), entity.getBionicEquipment().chestCubes(index), entity, index);
			if (deathGeometry != null) {
				int sourceIndex = index;
				snapshot.cubes().forEach(cube -> deathGeometry.add(new SlimeMimicCubeGeometry(
					sourceIndex, cube.cubeId(), cube.corners())));
			}
			if (restStates != null)
				restStates.add(new SlimeBionicAnimator.SourceState(
					SlimeBionicAnimator.measure(snapshot), offsets, rotations));
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** A source's stored component offsets, turned from the table layout into the upright body frame. */
	private static Map<Integer, Vec3> uprightOffsets(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<Integer, Vec3> offsets = source.cubeOffsets();
		if (offsets.isEmpty())
			return offsets;
		SurgicalLayPose pose = assembly.layoutLayPose();
		Map<Integer, Vec3> transformed = new java.util.HashMap<>();
		offsets.forEach((cube, offset) -> transformed.put(cube, pose.inverseRotate(offset)));
		return Map.copyOf(transformed);
	}

	private static Map<Integer, SurgicalCubeRotation> uprightRotations(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<Integer, SurgicalCubeRotation> rotations = source.cubeRotations();
		if (rotations.isEmpty())
			return rotations;
		SurgicalLayPose pose = assembly.layoutLayPose();
		Map<Integer, SurgicalCubeRotation> transformed = new java.util.HashMap<>();
		rotations.forEach((cube, rotation) -> transformed.put(cube, rotation.inverseRotate(pose)));
		return Map.copyOf(transformed);
	}

	@Nullable
	private static CachedGeometry rebuildGeometry(SlimeBionicEntity entity, LivingEntity preview,
		SurgicalAssembly assembly,
		float partialTick, int packedLight, boolean slimeForm) {
		if (preview == null)
			return null;
		GeometryKey key = new GeometryKey(assembly, slimeForm, SurgicalSourceModelRenderer.resourceGeneration());
		CachedGeometry shared = SHARED_GEOMETRY.getIfPresent(key);
		if (shared != null) {
			SurgicalProfiler.count("bionicGeometry(singleCacheHit)");
			shared.bodyGeometry.update(entity);
			return shared;
		}

		BitSet presentCubes = assembly.presentCubes();
		Map<Integer, Vec3> offsets = componentOffsets(preview, assembly, presentCubes, partialTick,
			packedLight);

		// Measure the active visual body's geometry, not the source creature's model origin.
		// EntityGeometry suppresses optional RenderLayers here, so clothes, armor and held items
		// follow the resulting translation without affecting where the body is centered or grounded.
		EntityGeometry.Collector bodyGeometry = EntityGeometry.Collector.boundsOnly();
		MultiBufferSource measuringBuffer = renderType -> bodyGeometry;
		SurgicalModelRenderContext.Snapshot[] restPose = new SurgicalModelRenderContext.Snapshot[1];
		EntityGeometry.measureBaseModelWithFallback(preview, bodyGeometry, () ->
			restPose[0] = SurgicalSourceModelRenderer.render(preview, assembly.cubeCount(), presentCubes,
				offsets, assembly.cubeRotations(),
				new PoseStack(), measuringBuffer, packedLight, 0.0f, partialTick, true, null, !slimeForm));
		EntityGeometry.Bounds bounds = bodyGeometry.bounds();
		Map<Integer, SlimeBionicAnimator.CubeBox> restBoxes = restPose[0] == null ? Map.of()
			: SlimeBionicAnimator.measure(restPose[0]);
		// Built once and kept, so the per-frame animator gets a stable list identity to cache its
		// limb solve against; all three arguments are already fixed for this cache entry's lifetime.
		List<SlimeBionicAnimator.SourceState> sourceStates =
			List.of(new SlimeBionicAnimator.SourceState(restBoxes, offsets, assembly.cubeRotations()));
		CachedGeometry measured = new CachedGeometry(assembly, slimeForm, presentCubes,
			sourceStates, SlimeBionicAnimator.rig(assembly, sourceStates),
			new BodyMeasurement(assembly, bounds, sourceStates));
		SHARED_GEOMETRY.put(key, measured);
		return measured;
	}

	private static Map<Integer, Vec3> componentOffsets(LivingEntity preview, SurgicalAssembly assembly,
		BitSet presentCubes, float partialTick, int packedLight) {
		EntityGeometry.Collector discardedVertices = EntityGeometry.Collector.boundsOnly();
		MultiBufferSource discardedBuffer = renderType -> discardedVertices;
		SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(preview,
			assembly.cubeCount(), presentCubes, Map.of(), new PoseStack(), discardedBuffer, packedLight,
			0.0f, partialTick, true, null);
		return SurgicalClientTopology.componentOffsets(assembly.cubeCount(), presentCubes,
			assembly.seams(), assembly.cutSeams(), snapshot.cubes());
	}

	@Override
	public ResourceLocation getTextureLocation(SlimeBionicEntity entity) {
		return SLIME_TEXTURE;
	}

	@Override
	protected float getShadowRadius(SlimeBionicEntity entity) {
		SurgicalAssembly.BodyBounds bounds = entity.renderBodyBounds();
		if (bounds == null)
			return super.getShadowRadius(entity);
		float horizontalRadius = Math.max(bounds.width(), bounds.depth()) * 0.5f;
		return Mth.clamp(horizontalRadius, MIN_SHADOW_RADIUS, MAX_SHADOW_RADIUS);
	}

	/** {@code presentCubes} is the assembly's own copy, taken once when the entry is built. */
	private record CachedGeometry(SurgicalAssembly assembly, boolean slimeForm, BitSet presentCubes,
		List<SlimeBionicAnimator.SourceState> sourceStates, SlimeBionicAnimator.Rig rig,
		BodyMeasurement bodyGeometry) {}

	private record CompositeCachedGeometry(SurgicalAssembly assembly, boolean slimeForm,
		List<SlimeBionicAnimator.SourceState> sources, SlimeBionicAnimator.Rig rig,
		BodyMeasurement bodyGeometry) {}

	private record GeometryKey(SurgicalAssembly assembly, boolean slimeForm, int resourceGeneration) {}
	private record BodyGeometry(SurgicalAssembly.BodyBounds bounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxes, double volume) {}

	/** Only immutable geometry crosses the worker boundary; polling and publication stay on the render thread. */
	private static final class BodyMeasurement {
		private final SurgicalAssembly assembly;
		private final SurgicalBodyBounds.Envelope visible;
		private final List<SlimeBionicAnimator.SourceState> sources;
		@Nullable private CompletableFuture<BodyGeometry> pending;
		@Nullable private BodyGeometry geometry;
		private boolean resolved;
		private Vec3 modelOffset;

		private BodyMeasurement(SurgicalAssembly assembly, EntityGeometry.Bounds bounds,
			List<SlimeBionicAnimator.SourceState> sources) {
			this.assembly = assembly;
			this.sources = sources;
			visible = new SurgicalBodyBounds.Envelope(bounds.minX(), bounds.minY(), bounds.minZ(),
				bounds.maxX(), bounds.maxY(), bounds.maxZ());
			// The assembly's frozen body remains usable while this client's visual geometry is measured.
			modelOffset = modelOffset(visible, assembly.bodyBounds());
			pending = SurgicalClientExecutors.submit(() -> measureBodyGeometry(assembly, visible, sources));
		}

		private void update(SlimeBionicEntity entity) {
			if (!resolved) {
				if (pending == null)
					pending = SurgicalClientExecutors.submit(() -> measureBodyGeometry(assembly, visible, sources));
				if (pending.isDone()) {
					try {
						geometry = pending.join();
						modelOffset = modelOffset(visible, geometry == null ? null : geometry.bounds);
						resolved = true;
					} catch (CompletionException exception) {
						if (!SurgicalClientExecutors.wasQueueFull(exception)) {
							resolved = true;
							LogUtils.getLogger().warn("Unable to measure bionic body geometry", exception);
						}
					}
					pending = null;
				}
			}
			applyBodyGeometry(entity, assembly, geometry);
		}
	}

	private record FirstPersonArms(SurgicalAssembly assembly,
		List<SlimeBionicAnimator.SourceState> sourceStates,
		Set<SurgicalAssembly.CombinationMember> wings,
		@Nullable FirstPersonArm right, @Nullable FirstPersonArm left) {}

	private record FirstPersonArm(Map<Integer, BitSet> cubes, EntityGeometry.Bounds bounds) {
		private FirstPersonArm {
			Map<Integer, BitSet> frozen = new java.util.HashMap<>();
			cubes.forEach((source, selected) -> frozen.put(source, (BitSet) selected.clone()));
			cubes = Map.copyOf(frozen);
		}
	}

	/**
	 * Converts cached yaw-zero component transforms into the final render-pass axes. Base geometry
	 * and source origins are transformed by the pose stack, while component offsets and quaternions
	 * are deliberately applied after that pose so they remain in the assembly-wide frame instead of
	 * inheriting an individual source's lay pose.
	 *
	 * <p>The incoming pose can already contain a view rotation. Iris does this for its shadow pass,
	 * where it supplies the light-space model-view stack directly to entity rendering. Reframing only
	 * by body yaw therefore leaves component transforms in camera-space axes while their base vertices
	 * are in light-space axes. Capture the incoming linear transform and include it here so both passes
	 * produce the same articulated silhouette.</p>
	 */
	private record BodyFrame(float yaw, Matrix4f axes, Quaternionf rotation, Quaternionf attachment) {
		private static final BodyFrame IDENTITY = new BodyFrame(0.0f, new Matrix4f(), new Quaternionf(), new Quaternionf());

		private static BodyFrame of(SlimeBionicEntity entity, float yaw, Matrix4f incomingPose) {
			if (!entity.isBlockAttached()) return of(yaw, incomingPose);
			Quaternionf attachment = BionicRiding.quaternion(entity.blockAttachmentRotation());
			Matrix4f axes = new Matrix4f(incomingPose).rotate(attachment);
			return new BodyFrame(0, axes, axes.getUnnormalizedRotation(new Quaternionf()), attachment);
		}

		private static BodyFrame of(float yaw, Matrix4f incomingPose) {
			float wrapped = Mth.wrapDegrees(yaw);
			Matrix4f axes = new Matrix4f(incomingPose);
			if (Math.abs(wrapped) > 1.0e-6f)
				axes.rotateY((float) Math.toRadians(-wrapped));
			return new BodyFrame(wrapped, axes,
				axes.getUnnormalizedRotation(new Quaternionf()), new Quaternionf());
		}

		private void apply(PoseStack poseStack) {
			poseStack.mulPose(attachment);
			if (Math.abs(yaw) > 1.0e-6f)
				poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
		}

		/**
		 * Reframes {@code base} overlaid with this frame's {@code animated} entries in one pass. The
		 * result is handed straight to the render plan, which only looks cubes up by id.
		 */
		private Map<Integer, Vec3> rotateOffsets(Map<Integer, Vec3> base, Map<Integer, Vec3> animated) {
			if (base.isEmpty() && animated.isEmpty())
				return base;
			Map<Integer, Vec3> rotated = new java.util.HashMap<>((base.size() + animated.size()) * 2);
			Vector3f scratch = new Vector3f();
			for (Map.Entry<Integer, Vec3> entry : base.entrySet())
				if (!animated.containsKey(entry.getKey()))
					rotated.put(entry.getKey(), rotate(entry.getValue(), scratch));
			for (Map.Entry<Integer, Vec3> entry : animated.entrySet())
				rotated.put(entry.getKey(), rotate(entry.getValue(), scratch));
			return rotated;
		}

		private Vec3 rotate(Vec3 offset, Vector3f scratch) {
			axes.transformDirection((float) offset.x, (float) offset.y, (float) offset.z, scratch);
			return new Vec3(scratch.x, scratch.y, scratch.z);
		}

		/** Rotation counterpart of {@link #rotateOffsets}. */
		private Map<Integer, SurgicalCubeRotation> rotateRotations(Map<Integer, SurgicalCubeRotation> base,
			Map<Integer, SurgicalCubeRotation> animated) {
			if (base.isEmpty() && animated.isEmpty())
				return base;
			Quaternionf inverse = new Quaternionf(rotation).conjugate();
			Quaternionf scratch = new Quaternionf();
			Map<Integer, SurgicalCubeRotation> rotated =
				new java.util.HashMap<>((base.size() + animated.size()) * 2);
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : base.entrySet())
				if (!animated.containsKey(entry.getKey()))
					rotated.put(entry.getKey(), rotate(entry.getValue(), inverse, scratch));
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : animated.entrySet())
				rotated.put(entry.getKey(), rotate(entry.getValue(), inverse, scratch));
			return rotated;
		}

		private SurgicalCubeRotation rotate(SurgicalCubeRotation local, Quaternionf inverse,
			Quaternionf scratch) {
			scratch.set(rotation).mul((float) local.x(), (float) local.y(), (float) local.z(),
				(float) local.w()).mul(inverse);
			return new SurgicalCubeRotation(scratch.x(), scratch.y(), scratch.z(), scratch.w());
		}
	}
}
