package com.nobodiiiii.createbiotech.entity.client;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Bone;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.LegStyle;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a stitched body from its installed anatomical joints.
 *
 * <p>The captured source geometry stays a still frame. Every joint instead contributes a rigid
 * rotation of one cube group around a pivot, expressed through the per-cube offset and rotation
 * maps the surgical render path already understands, so no part of the capture pipeline has to run
 * again per frame. Animation sampling lives in {@link SlimeBionicAnimations}; this class only
 * resolves geometry and retargets the sampled bone hierarchy onto the installed joints.</p>
 */
public final class SlimeBionicAnimator {
	static final int AXIS_X = 0;
	static final int AXIS_Y = 1;
	static final int AXIS_Z = 2;
	static final double GEOMETRY_EPSILON = 1.0e-10d;
	/** Includes SpiderModel's outer legs, whose rest pose is exactly 45 degrees from horizontal. */
	static final double SPIDER_HORIZONTAL_TO_VERTICAL_RATIO = 0.75d;
	static final double GROUND_CONTACT_TOLERANCE = 2.0d / 16.0d;
	/**
	 * Connection graphs keyed by the immutable assembly they describe. Weak so a despawned body's
	 * graph is collected with it.
	 */
	static final Map<SurgicalAssembly, SurgicalConnectionGraph<Integer>> CONNECTION_GRAPHS =
		java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final double PRINCIPAL_AXIS_SHARE = 0.55d;
	static final Basis BODY_SPACE = Basis.bodySpace();
	/**
	 * The present authored strike already looks centred for a hanging arm aimed level and forward.
	 * That posture's final vertical sector is -35..45 degrees, whose midpoint is five degrees down.
	 */
	static final float HANGING_ARM_REFERENCE_CENTER_PITCH = 5.0f;

	private SlimeBionicAnimator() {}

	public static void clearCache() {
		SlimeBionicAnimations.clearCache();
	}

	/**
	 * Reduces one source's captured cubes to what the joint maths needs.
	 *
	 * <p>Snapshots are measured in the body's yaw-zero render frame. Keeping this rest geometry out
	 * of world space makes the selected side, hinge and animation axes stable while the creature
	 * turns.</p>
	 */
	public static Map<Integer, CubeBox> measure(SurgicalModelRenderContext.Snapshot snapshot) {
		Map<Integer, CubeBox> boxes = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : snapshot.cubes())
			boxes.put(cube.cubeId(), CubeBox.of(cube.corners(), BODY_SPACE));
		return Map.copyOf(boxes);
	}

	/** Bakes immutable dimensions for every articulated or single-piece arm. */
	@Nullable
	public static SurgicalAssembly.AttackGeometry bakeAttackGeometry(SurgicalAssembly assembly,
		List<SourceState> sources, Vec3 bodyOrigin) {
		return BionicAttackGeometryBaker.bakeAttackGeometry(assembly, sources, bodyOrigin);
	}

	/** All native renderer data is frozen by the client before this worker-safe calculation. */
	@Nullable
	public static SurgicalAssembly.AttackGeometry bakeAttackGeometry(SurgicalAssembly assembly,
		List<SourceState> sources, Vec3 bodyOrigin, List<SurgicalSourceModelRenderer.DonorGeometry> donors) {
		return BionicAttackGeometryBaker.bakeAttackGeometry(assembly, sources, bodyOrigin, donors);
	}

	/** Translate an already baked rest frame without repeating limb/volume or accessory measurements. */
	@Nullable
	public static SurgicalAssembly.AttackGeometry rebaseAttackGeometry(SurgicalAssembly.AttackGeometry geometry,
		Vec3 bodyOrigin) {
		return BionicAttackGeometryBaker.rebaseAttackGeometry(geometry, bodyOrigin);
	}

	/**
	 * Everything the per-frame solve needs that reads only the assembly and the rest-pose sources -
	 * never the entity, the pose or the partial tick: the limb solve (a fixed 16-iteration power
	 * method per limb plus a second one per shoulder and hip), the gait curve derived from its leg
	 * length, the torso pivot and the leg cubes the torso rotation skips. The renderer holds one of
	 * these for as long as the body is unchanged, which is exactly how long it stays valid.
	 */
	public static final class Rig {
		final SurgicalAssembly assembly;
		final List<SourceState> sources;
		final List<ResolvedLimb> limbs;
		/** Limb indices with every shoulder and hip ahead of the elbow or knee hanging from it. */
		final int[] applyOrder;
		final float maximumSwingAmount;
		final float animationFrequencyScale;
		final Vec3 bodyPivot;
		final float restSwimPitch;
		final Set<Member> legMembers;
		final Map<Integer, WingHinge> wingHinges = new HashMap<>();
		final BionicNativePose.Attachments nativeAttachments;

		private Rig(SurgicalAssembly assembly, List<SourceState> sources, List<ResolvedLimb> limbs) {
			this.assembly = assembly;
			this.sources = sources;
			this.limbs = limbs;
			this.applyOrder = java.util.stream.IntStream.range(0, limbs.size()).boxed()
				.sorted(java.util.Comparator.comparingInt(index -> BionicPoseSolver.hierarchyDepth(index, limbs)))
				.mapToInt(Integer::intValue).toArray();
			float legLength = BionicMobilityMeasurements.averageEffectiveLegLength(limbs, sources);
			this.maximumSwingAmount = SurgicalGait.maximumHumanoidSwingAmount(legLength);
			this.animationFrequencyScale = SurgicalGait.animationFrequencyScale(legLength);
			this.bodyPivot = BionicRigBuilder.bodyPivot(limbs, sources);
			this.restSwimPitch = BionicPoseSolver.restSwimPitch(limbs, sources, bodyPivot);
			this.legMembers = BionicPoseSolver.legMembers(limbs);
			this.nativeAttachments = new BionicNativePose.Attachments(assembly, limbs);
		}
	}

	public static Rig rig(SurgicalAssembly assembly, List<SourceState> sources) {
		return new Rig(assembly, sources, BionicRigBuilder.resolveLimbs(assembly, sources));
	}

	/**
	 * Selects one real arm on the requested side for first-person rendering. An articulated arm is
	 * returned as one combined upper-arm and forearm selection, from upper arm through forearm.
	 */
	@Nullable
	public static List<SurgicalAssembly.CombinationMember> firstPersonArm(
		SlimeBionicEntity entity, SurgicalAssembly assembly, List<SourceState> sources, boolean left) {
		List<ResolvedLimb> limbs = BionicRigBuilder.resolveLimbs(assembly, sources);
		int shoulderIndex = -1;
		int lowestSlot = Integer.MAX_VALUE;
		for (int index = 0; index < limbs.size(); index++) {
			ResolvedLimb limb = limbs.get(index);
			if (limb.type() != SurgicalLimbType.SHOULDER || limb.arm() == null
				|| limb.arm().left() != left || limb.arm().slot() >= lowestSlot)
				continue;
			var geometry = assembly.attackGeometry();
			var arm = geometry == null ? null : geometry.arm(limb.arm().left(), limb.arm().slot());
			if (arm != null && entity.isBionicWing(arm)) continue;
			shoulderIndex = index;
			lowestSlot = limb.arm().slot();
		}
		if (shoulderIndex < 0)
			return null;

		Set<SurgicalAssembly.CombinationMember> members = new LinkedHashSet<>();
		ResolvedLimb shoulder = limbs.get(shoulderIndex);
		shoulder.members().stream()
			.map(member -> new SurgicalAssembly.CombinationMember(member.source(), member.cube()))
			.forEach(members::add);
		for (ResolvedLimb candidate : limbs)
			if (candidate.type() == SurgicalLimbType.ELBOW
				&& candidate.parentIndex() == shoulderIndex) {
				candidate.members().stream()
					.map(member -> new SurgicalAssembly.CombinationMember(member.source(), member.cube()))
					.forEach(members::add);
				break;
			}
		return List.copyOf(members);
	}

	/**
	 * Resolves one animation frame into per-source cube transforms.
	 *
	 * <p>{@code rig.sources} is indexed like {@link SurgicalAssembly#sources()}; each returned frame
	 * only contains the cubes an installed joint actually moves.</p>
	 */
	public static List<Frame> resolve(SlimeBionicEntity entity, Rig rig, float partialTick) {
		return BionicPoseSolver.resolve(entity, rig, partialTick);
	}

	/**
	 * Measures the grounded legs that may animate and contribute to movement. A leg touches the
	 * body's rest-pose ground plane when its lowest point is less than two model pixels above the
	 * complete body's lowest point. Its effective length is the geometric mean of vertical reach and
	 * the hip-to-sole joint-chain length, so a tilted leg gains some real-length benefit without a
	 * nearly horizontal appendage receiving its full span as stride.
	 */
	public static MobilityMetrics measureMobility(SurgicalAssembly assembly, List<SourceState> sources) {
		return BionicMobilityMeasurements.measureMobility(assembly, sources);
	}

	/** Y centre of the same neck-driven group assigned to the visible head animation. */
	public static double primaryHeadCenterY(SurgicalAssembly assembly, List<SourceState> sources) {
		return BionicMobilityMeasurements.primaryHeadCenterY(assembly, sources);
	}

	record Member(int source, int cube) {}
	record Connection(Member child, Member parent) {}
	record WingHinge(Vec3 start, Vec3 end) {}
	record LimbGeometry(SurgicalLimbType type, List<Member> members, Member anchor,
		Member parent, CubeBox child, CubeBox parentBox) {}
	record TipGeometry(Vec3 center, float radius) {}
	record ArmChannel(boolean left, int slot, float phase) {}
	record GaitChannel(LegStyle style, boolean left, float phase) {}
	record LegMeasurement(double soleHeight, double effectiveLength) {}
	record AttackAnimationTarget(float yaw, float pitch, float referencePitch) {
		/** Unused presentation values for a body that is not running an authored attack. */
		static final AttackAnimationTarget NONE = new AttackAnimationTarget(0.0f, 0.0f, 0.0f);
	}

	/** Immutable rest-pose measurements used by authoritative movement-speed calibration. */
	public record MobilityMetrics(float averageLegLength, int groundedLegCount,
		int groundedKneeCount, float legVolumeRatio) {
		public static final MobilityMetrics EMPTY = new MobilityMetrics(0.0f, 0, 0, 0.0f);
	}

	record ResolvedLimb(SurgicalLimbType type, List<Member> members, Member anchor, Member parent,
		Vec3 pivot, Vec3 restDirection, double side, double longitudinal,
		SurgicalCubeRotation restAlignment,
		@Nullable Bone bone, int parentIndex, @Nullable ArmChannel arm,
		@Nullable GaitChannel gait) {
		ResolvedLimb withBone(@Nullable Bone bone) {
			return new ResolvedLimb(type, members, anchor, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, arm, gait);
		}

		ResolvedLimb withParent(int parentIndex) {
			return new ResolvedLimb(type, members, anchor, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, arm, gait);
		}

		ResolvedLimb withArm(@Nullable ArmChannel arm) {
			return new ResolvedLimb(type, members, anchor, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, arm, gait);
		}

		ResolvedLimb withGait(GaitChannel gait) {
			return new ResolvedLimb(type, members, anchor, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, arm, gait);
		}
	}

	/** One affine body-space transform accumulated through a parent-to-child joint chain. */
	record Transform(SurgicalCubeRotation rotation, Vec3 translation) {
		static final Transform IDENTITY =
			new Transform(SurgicalCubeRotation.IDENTITY, Vec3.ZERO);

		Vec3 apply(Vec3 point) {
			return rotation.rotate(point).add(translation);
		}

		Transform then(Transform next) {
			if (next == IDENTITY) return this;
			if (this == IDENTITY) return next;
			return new Transform(rotation.then(next.rotation), next.apply(translation));
		}

		Transform inverse() {
			if (this == IDENTITY) return this;
			SurgicalCubeRotation inverse = new SurgicalCubeRotation(-rotation.x(), -rotation.y(),
				-rotation.z(), rotation.w());
			return new Transform(inverse, inverse.rotate(translation).scale(-1));
		}

		Transform rotateAround(Vec3 pivot, SurgicalCubeRotation delta) {
			if (delta.isIdentity())
				return this;
			Vec3 nextTranslation = pivot.add(delta.rotate(translation.subtract(pivot)));
			return new Transform(rotation.then(delta), nextTranslation);
		}

		boolean isIdentity() {
			return rotation.isIdentity() && translation.lengthSqr() < GEOMETRY_EPSILON;
		}
	}

	/** The rest state of one source, exactly as the visible render receives it. */
	public record SourceState(Map<Integer, CubeBox> boxes, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations) {}

	/**
	 * One source's cube transforms for the current frame, in the yaw-zero body frame. Each entry
	 * already includes the cube's static transform, so it replaces rather than adds to the rest state.
	 */
	public record Frame(Map<Integer, Vec3> offsets, Map<Integer, SurgicalCubeRotation> rotations) {
		public static final Frame EMPTY = new Frame(Map.of(), Map.of());
	}

	/**
	 * The body-local frame the capture baked into its vertices.
	 *
	 * <p>{@code LivingEntityRenderer} turns the model by {@code 180° - yaw} and then mirrors it with
	 * {@code scale(-1, -1, 1)}, so vanilla model space arrives rotated and flipped: model {@code +X}
	 * is the body's left, {@code +Y} points down and {@code +Z} points backwards. Reproducing that
	 * exact frame is what lets zombie joint angles apply unchanged.</p>
	 */
	record Basis(Quaternionf modelToWorld, Vec3 modelX, Vec3 modelY, Vec3 modelZ) {
		private static Basis bodySpace() {
			return new Basis(new Quaternionf().rotateY((float) Math.PI).rotateZ((float) Math.PI),
				new Vec3(1.0d, 0.0d, 0.0d), new Vec3(0.0d, -1.0d, 0.0d),
				new Vec3(0.0d, 0.0d, -1.0d));
		}

		private Vec3 axis(int index) {
			return index == AXIS_Y ? modelY : index == AXIS_Z ? modelZ : modelX;
		}

		double project(Vec3 vector, int index) {
			return vector.dot(axis(index));
		}

		/** Physical upward is negative model Y in the captured body frame. */
		boolean pointsUp(Vec3 vector) {
			return project(vector, AXIS_Y) < -GEOMETRY_EPSILON;
		}

		Vec3 point(double x, double y, double z) {
			return modelX.scale(x).add(modelY.scale(y)).add(modelZ.scale(z));
		}

		/** Builds the rest-frame rotation that maps a canonical humanoid limb onto this child. */
		SurgicalCubeRotation restAlignment(SurgicalLimbType type, Vec3 restDirection) {
			Vec3 actual = new Vec3(project(restDirection, AXIS_X), project(restDirection, AXIS_Y),
				project(restDirection, AXIS_Z)).normalize();
			Vector3f canonical = new Vector3f(0.0f,
				type == SurgicalLimbType.NECK ? -1.0f : 1.0f, 0.0f);
			Vector3f actualVector = new Vector3f((float) actual.x, (float) actual.y, (float) actual.z);
			Quaternionf alignment = new Quaternionf().rotationTo(canonical, actualVector);

			// rotationTo fixes the long direction but leaves twist underdetermined. Define the
			// swing axis geometrically: it is perpendicular to the installed limb and body forward,
			// just as canonical humanoid X is perpendicular to a hanging limb and model Z.
			Vec3 targetX = actual.cross(new Vec3(0.0d, 0.0d, 1.0d))
				.scale(type == SurgicalLimbType.NECK ? -1.0d : 1.0d);
			if (targetX.lengthSqr() < GEOMETRY_EPSILON)
				targetX = new Vec3(1.0d, 0.0d, 0.0d)
					.subtract(actual.scale(actual.x));
			targetX = targetX.normalize();
			Vector3f mapped = alignment.transform(new Vector3f(1.0f, 0.0f, 0.0f));
			Vec3 mappedX = new Vec3(mapped.x, mapped.y, mapped.z).normalize();
			double sine = actual.dot(mappedX.cross(targetX));
			double cosine = Mth.clamp(mappedX.dot(targetX), -1.0d, 1.0d);
			float twistAngle = (float) Math.atan2(sine, cosine);
			if (Math.abs(twistAngle) > 1.0e-6f) {
				Quaternionf twist = new Quaternionf().rotationAxis(twistAngle, actualVector);
				alignment = twist.mul(alignment);
			}
			return new SurgicalCubeRotation(alignment.x(), alignment.y(), alignment.z(), alignment.w());
		}

		/** Retargets vanilla {@link ModelPart} Euler angles through the measured rest frame. */
		SurgicalCubeRotation reframe(SurgicalCubeRotation restAlignment,
			float zRot, float yRot, float xRot) {
			if (zRot == 0.0f && yRot == 0.0f && xRot == 0.0f)
				return SurgicalCubeRotation.IDENTITY;
			Quaternionf alignment = new Quaternionf((float) restAlignment.x(),
				(float) restAlignment.y(), (float) restAlignment.z(), (float) restAlignment.w());
			Quaternionf modelRotation = new Quaternionf(alignment)
				.mul(new Quaternionf().rotationZYX(zRot, yRot, xRot))
				.mul(new Quaternionf(alignment).conjugate());
			Quaternionf world = new Quaternionf(modelToWorld)
				.mul(modelRotation)
				.mul(new Quaternionf(modelToWorld).conjugate());
			return new SurgicalCubeRotation(world.x(), world.y(), world.z(), world.w());
		}

		/** Retargets an authored torso-then-limb rotation through one installed rest frame. */
		SurgicalCubeRotation reframeComposite(SurgicalCubeRotation restAlignment,
			Rotation parent, Rotation local) {
			Quaternionf alignment = new Quaternionf((float) restAlignment.x(),
				(float) restAlignment.y(), (float) restAlignment.z(), (float) restAlignment.w());
			Quaternionf modelRotation = new Quaternionf(alignment)
				.mul(new Quaternionf().rotationZYX(parent.z(), parent.y(), parent.x()))
				.mul(new Quaternionf().rotationZYX(local.z(), local.y(), local.x()))
				.mul(new Quaternionf(alignment).conjugate());
			Quaternionf world = new Quaternionf(modelToWorld)
				.mul(modelRotation)
				.mul(new Quaternionf(modelToWorld).conjugate());
			return new SurgicalCubeRotation(world.x(), world.y(), world.z(), world.w());
		}

		/**
		 * Retargets a hanging-arm rotation to the vertical reflection of that arm. Reflections are not
		 * rotations, so a 180-degree rest alignment cannot reproduce this path: both the reference rest
		 * direction and the resulting rotation must be reflected across the attachment's horizontal plane.
		 */
		SurgicalCubeRotation mirroredArmReframe(SurgicalLimbType type, Vec3 restDirection,
			float zRot, float yRot, float xRot) {
			SurgicalCubeRotation hanging = reframe(restAlignment(type,
				mirrorAcrossHorizontal(restDirection)), zRot, yRot, xRot);
			return mirrorAcrossHorizontal(hanging);
		}

		/** Horizontal-reflection counterpart of {@link #reframeComposite}. */
		SurgicalCubeRotation mirroredArmReframeComposite(SurgicalLimbType type,
			Vec3 restDirection, Rotation parent, Rotation local) {
			SurgicalCubeRotation hanging = reframeComposite(restAlignment(type,
				mirrorAcrossHorizontal(restDirection)), parent, local);
			return mirrorAcrossHorizontal(hanging);
		}

		/** Reflects a vector across the plane normal to the body's vertical axis. */
		private Vec3 mirrorAcrossHorizontal(Vec3 vector) {
			return vector.subtract(modelY.scale(2.0d * vector.dot(modelY)));
		}

		/**
		 * Conjugates a proper rotation by the horizontal reflection. A quaternion's vector part is an
		 * axial vector, so reflection applies {@code -H} to it while leaving the scalar part unchanged.
		 */
		private SurgicalCubeRotation mirrorAcrossHorizontal(SurgicalCubeRotation rotation) {
			Vec3 axis = new Vec3(rotation.x(), rotation.y(), rotation.z());
			Vec3 reflectedAxis = modelY.scale(2.0d * axis.dot(modelY)).subtract(axis);
			return new SurgicalCubeRotation(reflectedAxis.x, reflectedAxis.y, reflectedAxis.z,
				rotation.w());
		}

		/**
		 * Applies SpiderModel's Y/Z Euler additions relative to the measured static leg pose. Vanilla
		 * spider legs originate along model X rather than humanoid model Y, so passing these angles
		 * through {@link #reframe} would rotate them around the wrong inferred axes. The measured
		 * direction uniquely recovers the base Y/Z angles of an outward-pointing left or right leg;
		 * {@code animated * inverse(rest)} then gives the exact pose delta to apply to baked geometry.
		 */
		SurgicalCubeRotation spiderReframe(Vec3 restDirection, boolean left,
			float zRot, float yRot) {
			if (zRot == 0.0f && yRot == 0.0f)
				return SurgicalCubeRotation.IDENTITY;
			Vec3 actual = new Vec3(project(restDirection, AXIS_X),
				project(restDirection, AXIS_Y), project(restDirection, AXIS_Z)).normalize();
			float side = left ? 1.0f : -1.0f;
			float horizontal = (float) Math.sqrt(actual.x * actual.x + actual.y * actual.y);
			float baseY = (float) Math.atan2(-side * actual.z, horizontal);
			float baseZ = (float) Math.atan2(side * actual.y, side * actual.x);
			Quaternionf rest = new Quaternionf().rotationZYX(baseZ, baseY, 0.0f);
			Quaternionf animated = new Quaternionf().rotationZYX(baseZ + zRot,
				baseY + yRot, 0.0f);
			Quaternionf modelDelta = animated.mul(new Quaternionf(rest).conjugate());
			Quaternionf world = new Quaternionf(modelToWorld)
				.mul(modelDelta)
				.mul(new Quaternionf(modelToWorld).conjugate());
			return new SurgicalCubeRotation(world.x(), world.y(), world.z(), world.w());
		}
	}

	/**
	 * One rest-state cube: its world centre plus its span along the body-local axes.
	 *
	 * <p>The centre is the mean of the transformed corners, which is exactly the point the render
	 * path rotates a cube around once its static offset is included.</p>
	 */
	public record CubeBox(Vec3 center, double[] min, double[] max, List<Vec3> points) {
		public CubeBox {
			points = List.copyOf(points);
		}

		/** {@code corners} are a captured cube's eight corners, which its geometry record guarantees. */
		private static CubeBox of(List<Vec3> corners, Basis basis) {
			double[] min = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
			double[] max = { -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
			Vec3 sum = Vec3.ZERO;
			for (Vec3 corner : corners) {
				for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
					double projected = basis.project(corner, axis);
					min[axis] = Math.min(min[axis], projected);
					max[axis] = Math.max(max[axis], projected);
				}
				sum = sum.add(corner);
			}
			return new CubeBox(sum.scale(1.0d / corners.size()), min, max, corners);
		}

		/** Longest covariance axis, or null when the group is too cube-like to define one. */
		@Nullable
		Vec3 principalAxis(Basis basis) {
			Vec3 mean = Vec3.ZERO;
			for (Vec3 point : points)
				mean = mean.add(point);
			mean = mean.scale(1.0d / points.size());
			double xx = 0.0d;
			double xy = 0.0d;
			double xz = 0.0d;
			double yy = 0.0d;
			double yz = 0.0d;
			double zz = 0.0d;
			for (Vec3 point : points) {
				Vec3 delta = point.subtract(mean);
				xx += delta.x * delta.x;
				xy += delta.x * delta.y;
				xz += delta.x * delta.z;
				yy += delta.y * delta.y;
				yz += delta.y * delta.z;
				zz += delta.z * delta.z;
			}
			double trace = xx + yy + zz;
			if (trace < GEOMETRY_EPSILON)
				return null;
			int seedAxis = AXIS_X;
			for (int axis = AXIS_Y; axis <= AXIS_Z; axis++)
				if (max[axis] - min[axis] > max[seedAxis] - min[seedAxis])
					seedAxis = axis;
			Vec3 axis = basis.axis(seedAxis);
			for (int iteration = 0; iteration < 16; iteration++) {
				Vec3 next = new Vec3(xx * axis.x + xy * axis.y + xz * axis.z,
					xy * axis.x + yy * axis.y + yz * axis.z,
					xz * axis.x + yz * axis.y + zz * axis.z);
				if (next.lengthSqr() < GEOMETRY_EPSILON)
					return null;
				axis = next.normalize();
			}
			Vec3 multiplied = new Vec3(xx * axis.x + xy * axis.y + xz * axis.z,
				xy * axis.x + yy * axis.y + yz * axis.z,
				xz * axis.x + yz * axis.y + zz * axis.z);
			double eigenvalue = axis.dot(multiplied);
			return eigenvalue / trace >= PRINCIPAL_AXIS_SHARE ? axis : null;
		}

		double supportRadius(Vec3 axis) {
			double radius = 0.0d;
			for (Vec3 point : points)
				radius = Math.max(radius, Math.abs(point.subtract(center).dot(axis)));
			return radius;
		}

		double distanceToSqr(Vec3 point, Basis basis) {
			double distance = 0.0d;
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				double projected = basis.project(point, axis);
				double outside = projected < min[axis] ? min[axis] - projected
					: projected > max[axis] ? projected - max[axis] : 0.0d;
				distance += outside * outside;
			}
			return distance;
		}

		/** Centre of the nearest shared/connecting region between this cube and another. */
		Vec3 contactCenter(CubeBox other, Basis basis) {
			double[] coordinate = new double[3];
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				if (max[axis] < other.min[axis])
					coordinate[axis] = (max[axis] + other.min[axis]) * 0.5d;
				else if (other.max[axis] < min[axis])
					coordinate[axis] = (min[axis] + other.max[axis]) * 0.5d;
				else
					coordinate[axis] = (Math.max(min[axis], other.min[axis])
						+ Math.min(max[axis], other.max[axis])) * 0.5d;
			}
			return basis.point(coordinate[AXIS_X], coordinate[AXIS_Y], coordinate[AXIS_Z]);
		}

		Vec3 surfaceToward(Vec3 target, SurgicalLimbType type, Basis basis) {
			Vec3 direction = target.subtract(center);
			if (direction.lengthSqr() < GEOMETRY_EPSILON)
				direction = basis.axis(AXIS_Y).scale(type == SurgicalLimbType.NECK ? 1.0d : -1.0d);
			double scale = Double.POSITIVE_INFINITY;
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				double component = basis.project(direction, axis);
				if (Math.abs(component) <= GEOMETRY_EPSILON)
					continue;
				double centerProjection = basis.project(center, axis);
				double boundary = component > 0.0d ? max[axis] : min[axis];
				scale = Math.min(scale, (boundary - centerProjection) / component);
			}
			return Double.isFinite(scale) ? center.add(direction.scale(scale)) : center;
		}
	}
}
