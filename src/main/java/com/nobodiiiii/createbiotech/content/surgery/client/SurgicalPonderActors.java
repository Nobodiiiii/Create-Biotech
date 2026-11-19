package com.nobodiiiii.createbiotech.content.surgery.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderGreenprint.SourcePose;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.entity.SlimeBionicCombat;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned surgery models. No world subjects, packets, save formats or global preview entities are edited. */
public final class SurgicalPonderActors extends PonderElementBase implements PonderSceneElement {
    public static final Vec3 ZOMBIE_POSITION = new Vec3(2.5, 2, 3.5);
    public static final Vec3 GOLEM_POSITION = new Vec3(4.5, 2, 3.5);
    public static final Vec3 PILLAGER_POSITION = GOLEM_POSITION.add(GOLEM_POSITION.subtract(ZOMBIE_POSITION));
    public static final Vec3 RESULT_POSITION = new Vec3(1.5, 1, 7.5);
    private static final Vec3 INTRO_POSITION = new Vec3(1.5, 1, 4.5);
    private static final double TABLETOP_OFFSET = SurgicalTablePlane.SURFACE_HEIGHT + 0.005;
    private static final float INTRO_START_YAW = 20;
    static final float RESULT_START_YAW = -90;
    private static final float RESULT_DISPLAY_YAW = -55;
    private static final int RESULT_TURN_TICKS = 18;
    private static final double CONTACT_GAP = 0.05;
    private static final double ARM_STRIKE_DISTANCE = 1.0;
    private static final double ARRIVAL_TOLERANCE = 0.001;
    private static final float[] HIT_YAW_OFFSETS = {0, 8, -4, 0, 0};
    static final SurgicalLayPose FLAT = new SurgicalLayPose(SurgicalLayPose.RotationAxis.X, 0, Vec3.ZERO);

    private enum ResultVariant { INTRO, HOPPING, WALKING }

    private final Model zombie;
    private final Model golem;
    private final Model pillager;
    private final int rightKnee;
    private final int leftKnee;
    private final Map<Integer, Vec3> cutLegOffsets;
    private final Map<Integer, Vec3> kneeOffsets;
    private boolean pillagerPlaced;
    private final int zombieArm;
    private final int golemArm;
    private final int torso;
    private final Vec3 cutZombieArmOffset;
    private final Vec3 parkedZombieArmOffset;
    private final Vec3 cutArmOffset;
    private final SurgicalCubeRotation armRotation;
    private final Vec3 armOffset;
    private final Map<ResultVariant, Body> bodies = new EnumMap<>(ResultVariant.class);
    private final Map<String, SurgicalPonderGreenprint> greenprints = new HashMap<>();
    private int stage;
    private ResultVariant resultVariant;
    private final LerpedFloat movement = LerpedFloat.linear();
    private boolean sourceAppearance;
    private boolean resultVisible;
    private boolean packed;
    private int resultTicks;
    private ArmorStand resultTarget;
    private Vec3 approachDirection = Vec3.ZERO;
    private float resultYaw;
    private boolean reachedTarget;
    private boolean introExiting;
    private int nextAttackTick;

    public SurgicalPonderActors(PonderLevel level) {
        zombie = new Model(Objects.requireNonNull(EntityType.ZOMBIE.create(level)), ZOMBIE_POSITION);
        golem = new Model(Objects.requireNonNull(EntityType.IRON_GOLEM.create(level)), GOLEM_POSITION);
        pillager = new Model(Objects.requireNonNull(EntityType.PILLAGER.create(level)), PILLAGER_POSITION);
        rightKnee = pillager.part("right_leg");
        leftKnee = pillager.part("left_leg");
        double rightLegSide = Math.signum(center(pillager.cube(rightKnee)).x - center(pillager.cube(leftKnee)).x);
        cutLegOffsets = Map.of(
            rightKnee, tabletopOffset(pillager, rightKnee, rightLegSide / 8, 0.25),
            leftKnee, tabletopOffset(pillager, leftKnee, -rightLegSide / 8, 0.25));
        kneeOffsets = Map.of(rightKnee, kneeOffset("right_leg", rightKnee),
            leftKnee, kneeOffset("left_leg", leftKnee));
        zombieArm = zombie.part("right_arm");
        golemArm = golem.part("right_arm");
        torso = zombie.part("body");
        AABB body = bounds(zombie.cube(torso).corners());
        AABB arm = bounds(golem.cube(golemArm).corners());
        double donorArmSide = Math.signum(arm.getCenter().x - center(golem.cube(golem.part("body"))).x);
        cutArmOffset = tabletopOffset(golem, golemArm, donorArmSide * 0.5, 0.5);
        double side = Math.signum(center(zombie.cube(zombieArm)).x - body.getCenter().x);
        cutZombieArmOffset = tabletopOffset(zombie, zombieArm, side * 0.45, 0);
        Vec3 zombieArmCenter = tableBounds(zombie, zombieArm, Vec3.ZERO, SurgicalCubeRotation.IDENTITY).getCenter();
        parkedZombieArmOffset = tabletopOffset(zombie, zombieArm, 1.6 - zombieArmCenter.x, 5.2 - zombieArmCenter.z);
        armRotation = SurgicalCubeRotation.IDENTITY;
        Vec3 contact = new Vec3(side > 0 ? arm.minX : arm.maxX, arm.maxY, arm.getCenter().z);
        Vec3 target = new Vec3(side > 0 ? body.maxX - 1.0 / 32 : body.minX + 1.0 / 32,
            body.maxY - 1.0 / 16, body.getCenter().z);
        armOffset = target.subtract(contact);
        reset(null);
    }

    public void placePillager() { pillagerPlaced = true; }
    public void setStage(int value) { stage = value; movement.startWithValue(1); }
    public void startMovement(int value) { stage = value; movement.startWithValue(0); }
    public void setMovement(float progress) { movement.setValue(progress); }
    public void setSourceAppearance(boolean value) { sourceAppearance = value; }
    public void releaseResult(boolean withJoints) {
        showResultAt(RESULT_POSITION, withJoints ? ResultVariant.WALKING : ResultVariant.HOPPING);
    }

    public void hideResult() {
        showResultAt(null, ResultVariant.HOPPING);
    }

    public void showIntroResult() {
        showResultAt(INTRO_POSITION, ResultVariant.INTRO);
    }

    public void hideIntroResult() {
        if (resultVariant != ResultVariant.INTRO || resultTarget == null) {
            hideResult();
            return;
        }
        introExiting = true;
    }

    private void showResultAt(Vec3 position, ResultVariant variant) {
        resultVisible = position != null;
        resultVariant = variant;
        resultTicks = 0;
        resultTarget = null;
        approachDirection = Vec3.ZERO;
        resultYaw = variant == ResultVariant.INTRO ? RESULT_DISPLAY_YAW : RESULT_START_YAW;
        reachedTarget = false;
        introExiting = false;
        nextAttackTick = 0;
        if (position != null) {
            var entity = body().entity;
            float yaw = startingYaw();
            entity.moveTo(position.x, position.y, position.z, yaw, 0);
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw;
            entity.setOldPosAndRot();
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setOnGround(true);
        }
    }

    public void approachTarget(ArmorStand target) {
        resultTarget = target;
        // ArmorStandRenderer uses level time for its native wobble, which need not advance in Ponder.
        target.lastHit = target.level().getGameTime() - 5;
        approachDirection = target.position().subtract(body().entity.position()).multiply(1, 0, 1).normalize();
        resultYaw = SlimeBionicCombat.yaw(approachDirection);
    }

    public void packBody() { packed = true; stage = 10; }
    public void unpackBody() { packed = false; }

    @Override
    public void reset(PonderScene scene) {
        setStage(0);
        pillagerPlaced = false;
        sourceAppearance = false;
        packed = false;
        hideResult();
        greenprints.clear();
        bodies.clear();
        setVisible(true);
    }

    @Override
    public void tick(PonderScene scene) {
        // Keep the final position stable after the movement instruction stops updating the value.
        movement.tickChaser();
        if (!resultVisible) return;
        ArmorStand target = resultTarget;
        boolean hasTarget = target != null && target.isAlive();
        if (introExiting && !hasTarget) {
            hideResult();
            return;
        }
        if (hasTarget) updateHitPose(target);
        resultTicks++;
        Body body = body();
        float bodyYaw = turningYaw(RESULT_TURN_TICKS);
        float headYaw = turningYaw(RESULT_TURN_TICKS - 6);
        SceneBionicEntity entity = body.entity;
        boolean facingTarget = hasTarget && !introExiting && resultTicks > RESULT_TURN_TICKS;
        Vec3 stop = null;
        Vec3 destination = null;
        if (facingTarget && !reachedTarget) {
            double stopDistance = body.attackArm == null
                ? (entity.getBbWidth() + target.getBbWidth()) / 2.0 + CONTACT_GAP : ARM_STRIKE_DISTANCE;
            stop = target.position().subtract(approachDirection.scale(stopDistance));
            if (beforeStop(entity, stop)) destination = body.attackArm == null ? target.position() : stop;
        }
        entity.tickScene(bodyYaw, headYaw, destination);
        if (!facingTarget) return;

        boolean atStop = stop != null && !beforeStop(entity, stop);
        if (atStop && body.attackArm != null) {
            entity.setPos(stop.x, entity.getY(), stop.z);
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 1, 0));
        }
        boolean justArrived = atStop && entity.onGround();
        if (justArrived) reachedTarget = true;
        if (body.attackArm == null) {
            if (justArrived) showImpact(scene.getWorld(), target);
            return;
        }
        if (justArrived || (resultVariant == ResultVariant.INTRO && reachedTarget && resultTicks >= nextAttackTick)) {
            Vec3 origin = SlimeBionicCombat.worldOrigin(entity.position(), bodyYaw, body.attackArm);
            Vec3 aim = SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, bodyYaw);
            entity.applyAttackAction(entity.getAttackActionSequence() + 1, true, body.attackOnLeft, 0,
                false, body.attackArm.hasElbow(), body.attackInterval, body.attackInterval,
                SlimeBionicCombat.yaw(aim), SlimeBionicCombat.pitch(aim), bodyYaw);
            nextAttackTick = resultTicks + body.attackInterval;
        }
        if (entity.getAttackActionTick() > 0
            && entity.getAttackActionDuration() - entity.getAttackActionTick() == entity.getAttackActionContactStartTick())
            showImpact(scene.getWorld(), target);
    }

    private boolean beforeStop(SceneBionicEntity entity, Vec3 stop) {
        // Compare progress along the release path so a final step across the stop never reverses the body.
        return stop.subtract(entity.position()).dot(approachDirection) > ARRIVAL_TOLERANCE;
    }

    private float turningYaw(int duration) {
        return Mth.rotLerp(ease((float) resultTicks / duration), startingYaw(), resultYaw);
    }

    private float startingYaw() {
        return resultVariant == ResultVariant.INTRO ? INTRO_START_YAW : RESULT_START_YAW;
    }

    private static float ease(float progress) {
        progress = Mth.clamp(progress, 0, 1);
        return progress * progress * (3 - 2 * progress);
    }

    private static void showImpact(PonderLevel level, ArmorStand target) {
        target.hurtTime = target.hurtDuration = HIT_YAW_OFFSETS.length - 1;
        updateHitPose(target);
        level.playLocalSound(target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_STAND_HIT,
            target.getSoundSource(), 0.3f, 1.0f, false);
        Vec3 center = target.getBoundingBox().getCenter();
        var random = target.getRandom();
        for (int i = 0; i < 8; i++)
            level.addParticle(ParticleTypes.CRIT, center.x, center.y, center.z,
                (random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25);
    }

    private static void updateHitPose(ArmorStand target) {
        int elapsed = HIT_YAW_OFFSETS.length - 1 - target.hurtTime;
        if (elapsed < 0 || elapsed >= HIT_YAW_OFFSETS.length - 1) return;
        float yaw = target.getYRot();
        target.yBodyRotO = yaw + HIT_YAW_OFFSETS[elapsed];
        target.yBodyRot = yaw + HIT_YAW_OFFSETS[elapsed + 1];
    }

    public Vec3 point(String name) { return box(name).getCenter(); }

    public Vec3 detachedDonorPoint(String name) {
        Vec3 offset = switch (name) {
            case "donor_arm" -> cutArmOffset;
            case "donor_right_leg" -> cutLegOffsets.get(rightKnee);
            case "donor_left_leg" -> cutLegOffsets.get(leftKnee);
            default -> throw new IllegalArgumentException("Unknown donor part: " + name);
        };
        return point(name).add(FLAT.rotate(offset));
    }

    public AABB box(String name) {
        return switch (name) {
            case "donor_right_leg" -> tableBounds(pillager, rightKnee, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "donor_left_leg" -> tableBounds(pillager, leftKnee, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "right_knee" -> tableBounds(zombie, pillager, rightKnee, kneeOffsets.get(rightKnee), SurgicalCubeRotation.IDENTITY);
            case "left_knee" -> tableBounds(zombie, pillager, leftKnee, kneeOffsets.get(leftKnee), SurgicalCubeRotation.IDENTITY);
            case "donor_arm" -> tableBounds(golem, golemArm, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "new_arm" -> tableBounds(zombie, golem, golemArm, armOffset, armRotation);
            case "old_arm" -> tableBounds(zombie, zombieArm, parkedZombieArmOffset, SurgicalCubeRotation.IDENTITY);
            case "head" -> tableBounds(zombie, zombie.part("head"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "left_arm" -> tableBounds(zombie, zombie.part("left_arm"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "right_leg" -> tableBounds(zombie, zombie.part("right_leg"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "left_leg" -> tableBounds(zombie, zombie.part("left_leg"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            default -> tableBounds(zombie, torso, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
        };
    }

    public SlimeBionicEntity result() { return body().entity; }

    private AABB tableBounds(Model frame, int cube, Vec3 offset, SurgicalCubeRotation rotation) {
        return tableBounds(frame, frame, cube, offset, rotation);
    }

    private Vec3 tabletopOffset(Model model, int cube, double dx, double dz) {
        AABB box = tableBounds(model, cube, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
        return FLAT.inverseRotate(new Vec3(dx, ZOMBIE_POSITION.y + TABLETOP_OFFSET - box.minY, dz));
    }

    private AABB tableBounds(Model frame, Model model, int cube, Vec3 offset, SurgicalCubeRotation rotation) {
        Vec3 center = center(model.cube(cube));
        return bounds(model.cube(cube).corners().stream().map(p ->
            frame.origin.add(FLAT.rotate(center.add(rotation.rotate(p.subtract(center))).add(offset)))).toList());
    }

    @Override
    public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {}

    @Override
    public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {}

    @Override
    public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        for (PlacedCube part : visibleParts(pt))
            part.draw(graphics.pose(), buffer, resultVisible || sourceAppearance);
    }

    private List<PlacedCube> visibleParts(float pt) {
        float movement = ease(this.movement.getValue(pt));
        List<PlacedCube> parts = new ArrayList<>();
        Matrix4f tableRoot = new Matrix4f();
        if (stage > 0) {
            for (int cube = 0; cube < zombie.count(); cube++) {
                if (packed && cube != zombieArm) continue;
                Vec3 offset = Vec3.ZERO;
                if (cube == zombieArm && stage >= 3)
                    offset = stage == 3 ? cutZombieArmOffset.scale(movement)
                        : cutZombieArmOffset.lerp(parkedZombieArmOffset, stage == 4 ? movement : 1);
                parts.add(new PlacedCube(zombie, cube, zombie.origin, true, offset, SurgicalCubeRotation.IDENTITY, tableRoot));
            }
        }
        if (stage >= 2) {
            for (int cube = 0; cube < golem.count(); cube++) {
                if (cube == golemArm && stage >= 5) continue;
                parts.add(new PlacedCube(golem, cube, golem.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, tableRoot));
            }
            if (stage >= 5 && !packed) {
                float progress = stage == 5 ? 0 : stage == 6 ? movement : 1;
                Vec3 origin = golem.origin.lerp(zombie.origin, progress);
                Vec3 offset = cutArmOffset.scale(stage == 5 ? movement : 1).lerp(armOffset, progress);
                parts.add(new PlacedCube(golem, golemArm, origin, true, offset, armRotation, tableRoot));
            }
        }
        if (pillagerPlaced) {
            for (int cube = 0; cube < pillager.count(); cube++) {
                boolean leg = cube == rightKnee || cube == leftKnee;
                if (leg && stage >= 8) continue;
                parts.add(new PlacedCube(pillager, cube, pillager.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, tableRoot));
            }
            if (stage >= 8 && !packed) {
                float progress = stage == 8 ? 0 : stage == 9 ? movement : 1;
                Vec3 origin = pillager.origin.lerp(zombie.origin, progress);
                for (int cube : List.of(rightKnee, leftKnee)) {
                    Vec3 offset = cutLegOffsets.get(cube).scale(stage == 8 ? movement : 1)
                        .lerp(kneeOffsets.get(cube), progress);
                    parts.add(new PlacedCube(pillager, cube, origin, true, offset,
                        SurgicalCubeRotation.IDENTITY, tableRoot));
                }
            }
        }
        if (resultVisible) addResultParts(parts, pt);
        return parts;
    }

    private void addResultParts(List<PlacedCube> parts, float pt) {
        Body body = body();
        List<SlimeBionicAnimator.Frame> frames = SlimeBionicAnimator.resolve(body.entity, body.rig, pt);
        Vec3 position = body.entity.getPosition(pt);
        if (resultVariant == ResultVariant.INTRO && resultTarget != null) {
            // Match PonderLevel's entity interpolation, including the armor stand's first frame.
            double targetY = resultTarget.tickCount == 0 ? resultTarget.getY()
                : Mth.lerp(pt, resultTarget.yOld, resultTarget.getY());
            position = position.add(0, targetY - INTRO_POSITION.y, 0);
        }
        // Canonical surgical vertices already face forward; match SlimeBionicRenderer.BodyFrame.
        Matrix4f root = new Matrix4f().translation((float) position.x, (float) position.y, (float) position.z)
            .rotateY((float) Math.toRadians(-Mth.rotLerp(pt, body.entity.yBodyRotO, body.entity.yBodyRot)))
            .translate((float) -body.origin.x, (float) -body.origin.y, (float) -body.origin.z)
            .mul(body.upright);
        for (int source = 0; source < body.sources.size(); source++) {
            SlimeBionicAnimator.Frame frame = frames.size() > source ? frames.get(source) : SlimeBionicAnimator.Frame.EMPTY;
            parts.addAll(body.sources.get(source).parts(root, frame));
        }
    }

    /** The highlight step consumes the very same live transforms as the model renderer. */
    public List<AABB> highlightBounds(List<String> names, float partialTick) {
        List<AABB> boxes = new ArrayList<>();
        for (PlacedCube part : visibleParts(partialTick))
            if (names.stream().anyMatch(name -> matches(part, name)))
                boxes.add(part.worldBounds());
        return boxes;
    }

    public List<AABB> zombieHighlightBounds(float partialTick) {
        return visibleParts(partialTick).stream().filter(part -> part.model == zombie && part.flat)
            .map(PlacedCube::worldBounds).reduce(AABB::minmax).stream().toList();
    }

    private boolean matches(PlacedCube part, String name) {
        return switch (name) {
            case "donor_arm" -> part.matches(EntityType.IRON_GOLEM, "right_arm");
            case "new_arm" -> part.matches(EntityType.IRON_GOLEM, "right_arm") && (!part.flat || stage >= 5);
            case "old_arm" -> part.matches(EntityType.ZOMBIE, "right_arm");
            case "donor_right_leg" -> part.matches(EntityType.PILLAGER, "right_leg");
            case "donor_left_leg" -> part.matches(EntityType.PILLAGER, "left_leg");
            case "right_knee" -> part.matches(EntityType.PILLAGER, "right_leg") && (!part.flat || stage >= 8);
            case "left_knee" -> part.matches(EntityType.PILLAGER, "left_leg") && (!part.flat || stage >= 8);
            case "body", "head", "left_arm", "right_leg", "left_leg" ->
                part.matches(EntityType.ZOMBIE, name);
            default -> throw new IllegalArgumentException("Unknown surgical Ponder part: " + name);
        };
    }

    record PlacedCube(Model model, int cube, Vec3 origin, boolean flat, Vec3 offset,
                      SurgicalCubeRotation rotation, Matrix4f root) {
        boolean matches(EntityType<?> type, String name) {
            return model.entity.getType() == type && cube == model.part(name);
        }

        void draw(PoseStack pose, MultiBufferSource buffer, boolean sourceAppearance) {
            pose.pushPose();
            pose.mulPose(root);
            pose.translate(origin.x, origin.y, origin.z);
            if (flat) SurgicalTablePoseResolver.applyRotation(pose, FLAT);
            pose.translate(offset.x, offset.y, offset.z);
            if (!rotation.isIdentity()) {
                Vec3 pivot = center(model.cube(cube));
                pose.translate(pivot.x, pivot.y, pivot.z);
                pose.mulPose(new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()));
                pose.translate(-pivot.x, -pivot.y, -pivot.z);
            }
            BitSet selected = new BitSet();
            selected.set(cube);
            model.plan.render(pose, buffer, LightTexture.FULL_BRIGHT, model.count(), selected,
                Map.of(), Map.of(), false, null, sourceAppearance, null);
            pose.popPose();
        }

        AABB worldBounds() {
            return bounds(worldCorners()).inflate(0.015);
        }

        List<Vec3> worldCorners() {
            Vec3 pivot = center(model.cube(cube));
            return model.cube(cube).corners().stream().map(point -> {
                Vec3 p = pivot.add(rotation.rotate(point.subtract(pivot))).add(offset);
                if (flat) p = FLAT.rotate(p);
                p = p.add(origin);
                Vector3f world = root.transformPosition(new Vector3f((float) p.x, (float) p.y, (float) p.z));
                return new Vec3(world.x, world.y, world.z);
            }).toList();
        }
    }

    private Body body() {
        return bodies.computeIfAbsent(resultVariant, this::createBody);
    }

    private Body createBody(ResultVariant variant) {
        String name = variant == ResultVariant.INTRO ? "surgical_table_intro" : "surgical_table_zombie_golem";
        var resultGreenprint = greenprints.computeIfAbsent(name, id -> SurgicalPonderGreenprint.load(zombie.entity.level(), id));
        SurgicalAssembly assembly = resultGreenprint.assembly();
        List<SourcePose> sources = resultGreenprint.sources();
        Matrix4f upright = SurgicalPonderGreenprint.upright(assembly);
        List<SlimeBionicAnimator.SourceState> rest = sources.stream().map(source -> source.measure(upright)).toList();
        boolean withJoints = variant != ResultVariant.HOPPING;
        if (!withJoints) {
            assembly = Objects.requireNonNull(SurgicalAssembly.createComposite(assembly.sources(), assembly.joints(),
                assembly.combinations(), List.of(), assembly.layoutFacing(), assembly.layoutLayPose()));
            assembly = measureBody(assembly, sources.stream().map(SourcePose::model).toList(), rest, member -> true);
        }
        var bounds = assembly.bodyBounds();
        Vec3 origin = bodyOrigin(assembly, rest);
        var attack = assembly.attackGeometry();
        SceneBionicEntity entity = new SceneBionicEntity(zombie.entity.level());
        ((SlimeMimicAccess) (Object) entity).createBiotech$setSlimeMimic(false);
        entity.setAssembly(assembly);
        // A real client receives these server-calibrated attributes over the network.
        // This scene owns no server entity, so seed them from the same gameplay calculations.
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SurgicalHealthCalibration.maximumHealth(
            assembly.bodyVolume(), entity.level(), entity.getBionicTraits().value(BionicTrait.HEALTH_OFFSET)));
        entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SurgicalGait.movementSpeed(bounds, entity.level()));
        entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BionicStats.evaluate(BionicStat.ATTACK_DAMAGE,
            entity.level(), Map.of("volume", assembly.bodyVolume())));
        entity.setHealth(entity.getMaxHealth());
        entity.getRandom().setSeed(0x5A71C0DEL + (withJoints ? 1 : 0));
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setOnGround(true);
        boolean attackOnLeft = attack != null && attack.right().isEmpty() && !attack.left().isEmpty();
        List<SurgicalAssembly.ArmAttackGeometry> arms = attack == null ? List.of()
            : attackOnLeft ? attack.left() : attack.right();
        var arm = arms.isEmpty() ? null : arms.getFirst();
        int interval = SurgicalCombatCalibration.attackInterval(SurgicalCombatCalibration.stats(arm, entity.level()),
            attack == null ? 1 : attack.arms().size(), entity.level());
        return new Body(sources, upright, entity, SlimeBionicAnimator.rig(assembly, rest), origin, arm, attackOnLeft, interval);
    }

    static Vec3 bodyOrigin(SurgicalAssembly assembly, List<SlimeBionicAnimator.SourceState> rest) {
        AABB envelope = bounds(rest.stream().flatMap(source -> source.boxes().values().stream())
            .flatMap(box -> box.points().stream()).toList());
        return bodyOrigin(envelope, assembly.bodyBounds());
    }

    private static Vec3 bodyOrigin(AABB envelope, SurgicalAssembly.BodyBounds bounds) {
        Vec3 center = envelope.getCenter();
        return new Vec3(center.x + bounds.centerX(), envelope.minY, center.z + bounds.centerZ());
    }

    static SurgicalAssembly measureBody(SurgicalAssembly assembly, List<Model> models,
                                        List<SlimeBionicAnimator.SourceState> states,
                                        Predicate<SurgicalAssembly.CombinationMember> inCore) {
        List<Map<Integer, List<Vec3>>> hitboxes = new ArrayList<>();
        List<List<Vec3>> all = new ArrayList<>();
        List<List<Vec3>> rigid = new ArrayList<>();
        for (int source = 0; source < states.size(); source++) {
            Map<Integer, List<Vec3>> boxes = new HashMap<>();
            var measured = states.get(source).boxes();
            BitSet present = assembly.sources().get(source).presentCubes();
            for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
                List<Vec3> corners = measured.get(cube).points();
                boxes.put(cube, corners);
                all.add(corners);
                if (inCore.test(new SurgicalAssembly.CombinationMember(source, cube))) rigid.add(corners);
            }
            hitboxes.add(Map.copyOf(boxes));
        }
        AABB envelope = bounds(all.stream().flatMap(List::stream).toList());
        var visible = new SurgicalBodyBounds.Envelope(envelope.minX, envelope.minY, envelope.minZ,
            envelope.maxX, envelope.maxY, envelope.maxZ);
        var measurement = Objects.requireNonNull(SurgicalBodyBounds.measureWithVolume(rigid, all, visible));
        var mobility = SlimeBionicAnimator.measureMobility(assembly, states);
        var bounds = measurement.bounds().withMobility(mobility.averageLegLength(), mobility.groundedLegCount(),
            mobility.groundedKneeCount(), mobility.legVolumeRatio());
        var hitbox = Objects.requireNonNull(SurgicalHitboxGeometry.measure(assembly, hitboxes, visible, bounds));
        Vec3 origin = bodyOrigin(envelope, bounds);
        assembly = assembly.withBodyGeometry(bounds, hitbox, measurement.volume());
        var attack = SlimeBionicAnimator.bakeAttackGeometry(assembly, states, origin,
            models.stream().map(Model::donorGeometry).toList());
        return attack == null ? assembly : assembly.withAttackGeometry(attack);
    }

    private Vec3 kneeOffset(String upperLeg, int lowerLeg) {
        AABB upper = bounds(zombie.cube(zombie.part(upperLeg)).corners());
        AABB lower = bounds(pillager.cube(lowerLeg).corners());
        return new Vec3(upper.getCenter().x - lower.getCenter().x,
            upper.minY - lower.maxY + 1.0 / 32, upper.getCenter().z - lower.getCenter().z);
    }

    /** Client-owned Ponder body: native travel runs locally, with server AI and packets absent. */
    private static final class SceneBionicEntity extends SlimeBionicEntity {
        private float sceneBodyYaw;
        private float sceneHeadYaw;
        private Vec3 destination;

        SceneBionicEntity(Level level) { super(CBEntityTypes.SLIME_BIONIC.get(), level); }

        void tickScene(float bodyYaw, float headYaw, Vec3 destination) {
            setOldPosAndRot();
            sceneBodyYaw = bodyYaw;
            sceneHeadYaw = headYaw;
            this.destination = destination;
            tickCount++;
            tick();
        }

        @Override public boolean isControlledByLocalInstance() { return true; }

        @Override public void aiStep() {
            if (destination != null) {
                getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 0.65);
                getMoveControl().tick();
            } else {
                setSpeed(0);
                setXxa(0);
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            }
            setYRot(sceneBodyYaw);
            getJumpControl().tick();
            super.aiStep();
        }

        @Override protected float tickHeadTurn(float wantedBodyYaw, float walkSpeed) {
            // The scene supplies one facing for travel and the rendered body. Keep the previous tick for interpolation.
            yBodyRot = sceneBodyYaw;
            yHeadRot = sceneHeadYaw;
            return walkSpeed;
        }
    }

    private record Body(List<SourcePose> sources, Matrix4f upright, SceneBionicEntity entity, SlimeBionicAnimator.Rig rig,
                        Vec3 origin, SurgicalAssembly.ArmAttackGeometry attackArm, boolean attackOnLeft,
                        int attackInterval) {}

    static final class Model {
        final LivingEntity entity;
        final MimicProfile profile;
        final SurgicalCapturedRenderPlan plan;
        final List<SurgicalModelRenderContext.CubeGeometry> cubes;
        final List<SurgicalAssembly.Seam> seams;
        final BionicAnatomySnapshot anatomy;
        final Vec3 origin;
        private final Map<String, Integer> partIds = new HashMap<>();

        Model(LivingEntity entity, Vec3 position) {
            this.entity = entity;
            if (entity instanceof Mob mob) mob.setNoAi(true);
            profile = MimicProfile.capture(entity);
            plan = SurgicalSourcePlanCache.plan(entity, 0);
            cubes = plan.snapshot(new PoseStack(), 0, new BitSet(), Map.of(), Map.of(), null).cubes();
            seams = SurgicalClientTopology.buildContactTopology(cubes.size(), cubes).seams();
            anatomy = SurgicalPlacementController.discoveredAnatomy(cubes, cubes.size());
            AABB flat = bounds(cubes.stream().flatMap(c -> c.corners().stream()).map(FLAT::rotate).toList());
            origin = position.add(-flat.getCenter().x, TABLETOP_OFFSET - flat.minY, -flat.getCenter().z);
        }

        int count() { return cubes.size(); }
        SurgicalModelRenderContext.CubeGeometry cube(int id) { return cubes.get(id); }
        int part(String name) {
            return partIds.computeIfAbsent(name, requested -> findPart(cubes, requested));
        }
        private static int findPart(List<SurgicalModelRenderContext.CubeGeometry> cubes, String name) {
            // HumanoidModel exposes fields such as rightArm; hierarchical models expose right_arm children.
            // SurgicalRenderCapture retains both kinds of native labels without normalizing their spelling.
            String key = partKey(name);
            return cubes.stream().filter(c -> c.partNames().stream().anyMatch(label -> partKey(label).equals(key)))
                .mapToInt(SurgicalModelRenderContext.CubeGeometry::cubeId).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing surgical Ponder model part: " + name
                    + "; captured labels: " + cubes.stream().flatMap(c -> c.partNames().stream()).distinct().sorted().toList()));
        }
        private static String partKey(String name) { return name.replace("_", "").toLowerCase(Locale.ROOT); }
        BitSet all() { BitSet all = new BitSet(); all.set(0, count()); return all; }
        SurgicalSourceModelRenderer.DonorGeometry donorGeometry() {
            Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId = new HashMap<>();
            for (var cube : cubes) byId.put(cube.cubeId(), cube);
            return new SurgicalSourceModelRenderer.DonorGeometry(byId, Map.of(), Map.of(), Map.of());
        }
        SurgicalAssembly.Source source(BitSet present, Map<Integer, Vec3> offsets, Map<Integer, SurgicalCubeRotation> rotations) {
            return Objects.requireNonNull(SurgicalAssembly.Source.create(profile,
                UUID.nameUUIDFromBytes(profile.entityTypeId().toString().getBytes(StandardCharsets.UTF_8)), count(), present,
                anatomy, seams, new BitSet(), List.of(), net.minecraft.core.Direction.NORTH,
                SurgicalLayPose.IDENTITY, Vec3.ZERO, offsets, rotations));
        }
    }

    static Vec3 center(SurgicalModelRenderContext.CubeGeometry cube) { return bounds(cube.corners()).getCenter(); }
    static AABB bounds(List<Vec3> corners) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Vec3 p : corners) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
