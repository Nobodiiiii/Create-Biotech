package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCubeGeometry;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.slimemimic.client.SlimeMimicDeathClient;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalClientTopology;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTablePoseResolver;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SlimeBionicRenderer extends EntityRenderer<SlimeBionicEntity> {
	private static final boolean RENDER_ATTACK_RANGE = true;
	private static final int ATTACK_RANGE_GRID_STEPS = 12;
	private static final float MIN_SHADOW_RADIUS = 0.15f;
	private static final float MAX_SHADOW_RADIUS = 32.0f;
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final Map<SlimeBionicEntity, CachedGeometry> GEOMETRY = new WeakHashMap<>();
	private static final Map<SlimeBionicEntity, CompositeCachedGeometry> COMPOSITE_GEOMETRY = new WeakHashMap<>();
	private static final Map<SurgicalAssembly, Map<SurgicalAssembly.Source, Map<Integer, Vec3>>>
		UPRIGHT_OFFSETS = new WeakHashMap<>();
	private static final Map<SurgicalAssembly, Map<SurgicalAssembly.Source, Map<Integer, SurgicalCubeRotation>>>
		UPRIGHT_ROTATIONS = new WeakHashMap<>();

	public SlimeBionicRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.25f;
	}

	public static void clearCache() {
		GEOMETRY.clear();
		COMPOSITE_GEOMETRY.clear();
		UPRIGHT_OFFSETS.clear();
		UPRIGHT_ROTATIONS.clear();
		SlimeBionicAnimator.clearCache();
	}

	@Override
	public void render(SlimeBionicEntity entity, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		boolean hideOriginal = entity.isDeadOrDying() && SlimeMimicHandler.isSlimeMimic(entity);
		if (hideOriginal && SlimeMimicDeathClient.hasReported(entity))
			return;
		MultiBufferSource sourceBuffer = hideOriginal ? hiddenBuffer() : buffer;
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly != null) {
			float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
			BodyFrame bodyFrame = BodyFrame.of(bodyYaw, poseStack.last().pose());
			if (assembly.preservesLayout() || assembly.sources().size() > 1) {
				renderComposite(entity, assembly, bodyFrame, partialTick, poseStack, sourceBuffer, packedLight);
				if (hideOriginal)
					return;
				renderAttackRange(entity, assembly, bodyFrame, poseStack, buffer);
				super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
				return;
			}
			COMPOSITE_GEOMETRY.remove(entity);
			poseStack.pushPose();
			LivingEntity preview = SurgicalSourceModelRenderer.preview(entity, assembly.profile());
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
			Map<Integer, Vec3> localOffsets = cached == null ? Map.of() : cached.offsets;
			Map<Integer, SurgicalCubeRotation> localRotations = assembly.cubeRotations();
			bodyFrame.apply(poseStack);
			if (cached != null) {
				poseStack.translate(cached.modelOffset.x, cached.modelOffset.y, cached.modelOffset.z);
				SlimeBionicAnimator.Frame frame = SlimeBionicAnimator.resolve(entity, assembly,
					cached.sourceStates, cached.rig, partialTick).getFirst();
				localOffsets = frame.mergeOffsets(localOffsets);
				localRotations = frame.mergeRotations(localRotations);
			}
			if (preview != null) {
				boolean reportDeath = hideOriginal;
				Vec3 cameraPosition = reportDeath
					? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition() : null;
				SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(preview,
					assembly.cubeCount(), presentCubes,
					bodyFrame.rotateOffsets(localOffsets), bodyFrame.rotateRotations(localRotations),
					poseStack, sourceBuffer, packedLight, 0.0f, partialTick, reportDeath, cameraPosition,
					!slimeForm);
				if (reportDeath)
					SlimeMimicDeathClient.report(entity, snapshot.cubes().stream()
						.map(cube -> new SlimeMimicCubeGeometry(0, cube.cubeId(), cube.corners()))
						.toList());
			}
			poseStack.popPose();
			if (hideOriginal)
				return;
			renderAttackRange(entity, assembly, bodyFrame, poseStack, buffer);
		} else {
			GEOMETRY.remove(entity);
			COMPOSITE_GEOMETRY.remove(entity);
		}
		if (!hideOriginal)
			super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
	}

	private static MultiBufferSource hiddenBuffer() {
		EntityGeometry.Collector hidden = EntityGeometry.Collector.boundsOnly();
		return renderType -> hidden;
	}

	/** Temporary combat debug view: the server-authoritative attack sector in blue. */
	private static void renderAttackRange(SlimeBionicEntity entity, SurgicalAssembly assembly,
		BodyFrame bodyFrame, PoseStack poseStack, MultiBufferSource buffer) {
		int duration = entity.getAttackActionDuration();
		int elapsed = duration - entity.getAttackActionTick();
		if (!RENDER_ATTACK_RANGE || entity.getAttackActionTick() <= 0
			|| assembly.attackGeometry() == null)
			return;
		boolean active = SlimeBionicCombat.isActiveTick(elapsed, duration);
		float alpha = active ? 0.32f : 0.14f;
		SurgicalAssembly.ArmAttackGeometry arm = assembly.attackGeometry()
			.arm(entity.isAttackActionLeft());
		if (arm == null)
			return;
		VertexConsumer vertices = buffer.getBuffer(RenderType.debugFilledBox());
		poseStack.pushPose();
		bodyFrame.apply(poseStack);
		double cell = arm.reach() / ATTACK_RANGE_GRID_STEPS;
		double halfCell = cell * 0.52d;
		Vec3 origin = arm.origin();
		for (int xCell = -ATTACK_RANGE_GRID_STEPS; xCell < ATTACK_RANGE_GRID_STEPS; xCell++)
			for (int zCell = 0; zCell < ATTACK_RANGE_GRID_STEPS; zCell++) {
				double x = (xCell + 0.5d) * cell;
				double z = (zCell + 0.5d) * cell;
				if (x * x + z * z > arm.reach() * arm.reach())
					continue;
				double angle = Math.atan2(Math.abs(x), z) * Mth.RAD_TO_DEG;
				if (angle > SlimeBionicCombat.SECTOR_HALF_ANGLE_DEGREES)
					continue;
				LevelRenderer.addChainedFilledBoxVertices(poseStack, vertices,
					origin.x + x - halfCell, arm.minimumY(), origin.z + z - halfCell,
					origin.x + x + halfCell, arm.maximumY(), origin.z + z + halfCell,
					0.08f, 0.42f, 1.0f, alpha);
			}
		poseStack.popPose();
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
			: SlimeBionicAnimator.resolve(entity, assembly, cached.sources, cached.rig, partialTick);
		List<SlimeMimicCubeGeometry> deathGeometry = entity.isDeadOrDying()
			&& SlimeMimicHandler.isSlimeMimic(entity) ? new ArrayList<>() : null;
		Vec3 cameraPosition = deathGeometry == null ? null
			: Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		poseStack.pushPose();
		bodyFrame.apply(poseStack);
		if (cached != null)
			poseStack.translate(cached.modelOffset.x, cached.modelOffset.y, cached.modelOffset.z);
		renderCompositeSources(entity, assembly, bodyFrame, partialTick, poseStack, buffer, packedLight,
			!slimeForm, frames, null, deathGeometry, cameraPosition);
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
		EntityGeometry.Collector geometry = EntityGeometry.Collector.boundsOnly();
		MultiBufferSource measuringBuffer = renderType -> geometry;
		List<SlimeBionicAnimator.SourceState> sources = new ArrayList<>(assembly.sources().size());
		renderCompositeSources(entity, assembly, BodyFrame.IDENTITY, partialTick, new PoseStack(),
			measuringBuffer, packedLight, !slimeForm, List.of(), sources, null, null);
		if (!geometry.hasVertices())
			return null;
		EntityGeometry.Bounds bounds = geometry.bounds();
		SurgicalAssembly.BodyBounds bodyBounds = updateClientBodyBounds(entity, assembly, bounds, sources);
		List<SlimeBionicAnimator.SourceState> frozenSources = List.copyOf(sources);
		return new CompositeCachedGeometry(assembly, slimeForm,
			modelOffset(bounds, bodyBounds), frozenSources,
			SlimeBionicAnimator.rig(assembly, frozenSources));
	}

	@Nullable
	private static SurgicalAssembly.BodyBounds updateClientBodyBounds(SlimeBionicEntity entity,
		SurgicalAssembly assembly,
		EntityGeometry.Bounds bounds, List<SlimeBionicAnimator.SourceState> sources) {
		Set<SurgicalAssembly.CombinationMember> armCubes = new HashSet<>();
		for (SurgicalAssembly.Limb limb : assembly.limbs())
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
		SurgicalBodyBounds.Envelope visible = new SurgicalBodyBounds.Envelope(
			bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
		SurgicalAssembly.BodyBounds bodyBounds = SurgicalBodyBounds.measure(bodyCubes, allCubes, visible);
		if (bodyBounds != null) {
			float legLength = SlimeBionicAnimator.effectiveLegLength(assembly, sources);
			if (legLength >= SurgicalAssembly.MIN_BODY_SIZE
				&& legLength <= SurgicalAssembly.MAX_BODY_SIZE)
				bodyBounds = bodyBounds.withLegLength(legLength);
			entity.setClientBodyBounds(assembly, bodyBounds);
		}
		return bodyBounds;
	}

	/** Centres the world entity on the already measured arm-free collision core's XZ projection. */
	private static Vec3 modelOffset(EntityGeometry.Bounds visible,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds) {
		double centerX = visible.centerX();
		double centerZ = visible.centerZ();
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
	 * animation frame being applied, which is how the still reference pose is captured.</p>
	 */
	private static void renderCompositeSources(SlimeBionicEntity entity, SurgicalAssembly assembly,
		BodyFrame bodyFrame, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		boolean renderSourceGeometry, List<SlimeBionicAnimator.Frame> frames,
		@Nullable List<SlimeBionicAnimator.SourceState> restStates,
		@Nullable List<SlimeMimicCubeGeometry> deathGeometry, @Nullable Vec3 cameraPosition) {
		poseStack.pushPose();
		SurgicalTablePoseResolver.applyInverseRotation(poseStack, assembly.layoutLayPose());
		List<SurgicalAssembly.Source> sources = assembly.sources();
		for (int index = 0; index < sources.size(); index++) {
			SurgicalAssembly.Source source = sources.get(index);
			LivingEntity preview = SurgicalSourceModelRenderer.preview(entity, source.profile());
			if (preview == null) {
				if (restStates != null)
					restStates.add(new SlimeBionicAnimator.SourceState(Map.of(), Map.of(), Map.of()));
				continue;
			}
			((SlimeMimicAccess) (Object) preview).createBiotech$setSlimeMimic(true);
			Map<Integer, Vec3> offsets = uprightOffsets(assembly, source);
			Map<Integer, SurgicalCubeRotation> rotations = uprightRotations(assembly, source);
			SlimeBionicAnimator.Frame frame = index < frames.size() ? frames.get(index)
				: SlimeBionicAnimator.Frame.EMPTY;
			Map<Integer, Vec3> renderOffsets = restStates == null
				? bodyFrame.rotateOffsets(frame.mergeOffsets(offsets)) : offsets;
			Map<Integer, SurgicalCubeRotation> renderRotations = restStates == null
				? bodyFrame.rotateRotations(frame.mergeRotations(rotations)) : rotations;
			poseStack.pushPose();
			poseStack.translate(source.originOffset().x, source.originOffset().y, source.originOffset().z);
			if (assembly.preservesLayout())
				SurgicalTablePoseResolver.resolve(source.layPose()).apply(poseStack);
			SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourceModelRenderer.render(preview,
				source.cubeCount(), source.presentCubes(), renderOffsets, renderRotations,
				poseStack, buffer, packedLight, 0.0f, partialTick,
				restStates != null || deathGeometry != null, cameraPosition,
				renderSourceGeometry);
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

	private static Map<Integer, Vec3> uprightOffsets(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<SurgicalAssembly.Source, Map<Integer, Vec3>> assemblyOffsets = UPRIGHT_OFFSETS
			.computeIfAbsent(assembly, ignored -> new IdentityHashMap<>());
		Map<Integer, Vec3> cached = assemblyOffsets.get(source);
		if (cached != null)
			return cached;
		SurgicalLayPose pose = assembly.layoutLayPose();
		Map<Integer, Vec3> offsets = source.cubeOffsets();
		if (offsets.isEmpty())
			return offsets;
		Map<Integer, Vec3> transformed = new java.util.HashMap<>();
		offsets.forEach((cube, offset) -> transformed.put(cube, pose.inverseRotate(offset)));
		Map<Integer, Vec3> result = Map.copyOf(transformed);
		assemblyOffsets.put(source, result);
		return result;
	}

	private static Map<Integer, SurgicalCubeRotation> uprightRotations(SurgicalAssembly assembly,
		SurgicalAssembly.Source source) {
		Map<SurgicalAssembly.Source, Map<Integer, SurgicalCubeRotation>> assemblyRotations = UPRIGHT_ROTATIONS
			.computeIfAbsent(assembly, ignored -> new IdentityHashMap<>());
		Map<Integer, SurgicalCubeRotation> cached = assemblyRotations.get(source);
		if (cached != null)
			return cached;
		SurgicalLayPose pose = assembly.layoutLayPose();
		Map<Integer, SurgicalCubeRotation> rotations = source.cubeRotations();
		if (rotations.isEmpty())
			return rotations;
		Map<Integer, SurgicalCubeRotation> transformed = new java.util.HashMap<>();
		rotations.forEach((cube, rotation) -> transformed.put(cube, rotation.inverseRotate(pose)));
		Map<Integer, SurgicalCubeRotation> result = Map.copyOf(transformed);
		assemblyRotations.put(source, result);
		return result;
	}

	@Nullable
	private static CachedGeometry rebuildGeometry(SlimeBionicEntity entity, LivingEntity preview,
		SurgicalAssembly assembly,
		float partialTick, int packedLight, boolean slimeForm) {
		if (preview == null)
			return null;

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
		SurgicalAssembly.BodyBounds bodyBounds = updateClientBodyBounds(entity, assembly, bounds,
			sourceStates);
		return new CachedGeometry(assembly, slimeForm, presentCubes, offsets,
			modelOffset(bounds, bodyBounds), restBoxes, sourceStates,
			SlimeBionicAnimator.rig(assembly, sourceStates));
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
		SurgicalAssembly.BodyBounds bounds = entity.activeBodyBounds();
		if (bounds == null)
			return super.getShadowRadius(entity);
		float horizontalRadius = Math.max(bounds.width(), bounds.depth()) * 0.5f;
		return Mth.clamp(horizontalRadius, MIN_SHADOW_RADIUS, MAX_SHADOW_RADIUS);
	}

	private record CachedGeometry(SurgicalAssembly assembly, boolean slimeForm, BitSet presentCubes,
		Map<Integer, Vec3> offsets, Vec3 modelOffset,
		Map<Integer, SlimeBionicAnimator.CubeBox> restBoxes,
		List<SlimeBionicAnimator.SourceState> sourceStates, SlimeBionicAnimator.Rig rig) {
		private CachedGeometry {
			presentCubes = (BitSet) presentCubes.clone();
			offsets = Map.copyOf(offsets);
		}
	}

	private record CompositeCachedGeometry(SurgicalAssembly assembly, boolean slimeForm,
		Vec3 modelOffset, List<SlimeBionicAnimator.SourceState> sources, SlimeBionicAnimator.Rig rig) {}

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
	private record BodyFrame(float yaw, Matrix4f axes, Quaternionf rotation) {
		private static final BodyFrame IDENTITY = new BodyFrame(0.0f, new Matrix4f(), new Quaternionf());

		private static BodyFrame of(float yaw, Matrix4f incomingPose) {
			float wrapped = Mth.wrapDegrees(yaw);
			Matrix4f axes = new Matrix4f(incomingPose);
			if (Math.abs(wrapped) > 1.0e-6f)
				axes.rotateY((float) Math.toRadians(-wrapped));
			return new BodyFrame(wrapped, axes,
				axes.getUnnormalizedRotation(new Quaternionf()));
		}

		private void apply(PoseStack poseStack) {
			if (Math.abs(yaw) > 1.0e-6f)
				poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
		}

		private Map<Integer, Vec3> rotateOffsets(Map<Integer, Vec3> offsets) {
			if (this == IDENTITY || offsets.isEmpty())
				return offsets;
			Map<Integer, Vec3> rotated = new java.util.HashMap<>(offsets.size());
			offsets.forEach((cube, offset) -> {
				Vector3f transformed = axes.transformDirection(
					(float) offset.x, (float) offset.y, (float) offset.z, new Vector3f());
				rotated.put(cube, new Vec3(transformed.x, transformed.y, transformed.z));
			});
			return Map.copyOf(rotated);
		}

		private Map<Integer, SurgicalCubeRotation> rotateRotations(
			Map<Integer, SurgicalCubeRotation> rotations) {
			if (this == IDENTITY || rotations.isEmpty())
				return rotations;
			Quaternionf frame = new Quaternionf(rotation);
			Quaternionf inverse = new Quaternionf(frame).conjugate();
			Map<Integer, SurgicalCubeRotation> rotated = new java.util.HashMap<>(rotations.size());
			rotations.forEach((cube, local) -> {
				Quaternionf reframed = new Quaternionf(frame).mul(quaternion(local)).mul(inverse);
				rotated.put(cube, new SurgicalCubeRotation(reframed.x(), reframed.y(), reframed.z(),
					reframed.w()));
			});
			return Map.copyOf(rotated);
		}

		private static Quaternionf quaternion(SurgicalCubeRotation rotation) {
			return new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(),
				(float) rotation.w());
		}
	}
}
