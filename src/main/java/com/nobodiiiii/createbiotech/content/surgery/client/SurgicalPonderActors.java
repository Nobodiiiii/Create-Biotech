package com.nobodiiiii.createbiotech.content.surgery.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned surgery models. No world subjects, packets, save formats or global preview entities are edited. */
public final class SurgicalPonderActors extends PonderElementBase implements PonderSceneElement {
    public static final Vec3 ZOMBIE_POSITION = new Vec3(2, 2, 3);
    public static final Vec3 GOLEM_POSITION = new Vec3(4.5, 2, 3);
    public static final Vec3 PILLAGER_POSITION = new Vec3(7, 2, 3);
    public static final Vec3 RESULT_POSITION = new Vec3(4.5, 1, 7);
    private static final SurgicalLayPose FLAT = new SurgicalLayPose(SurgicalLayPose.RotationAxis.X, 0, Vec3.ZERO);
    private final Model zombie;
    private final Model golem;
    private final Model pillager;
    private final int rightKnee;
    private final int leftKnee;
    private final Map<Integer, Vec3> kneeOffsets;
    private boolean pillagerPlaced;
    private final int zombieArm;
    private final int golemArm;
    private final int torso;
    private final SurgicalCubeRotation armRotation;
    private final Vec3 armOffset;
    private final Map<Integer, Body> bodies = new HashMap<>();
    private int stage;
    private int joints;
    private float movement;
    private boolean sourceAppearance;
    private boolean resultVisible;
    private boolean packed;
    private int resultTicks;

    public SurgicalPonderActors(PonderLevel level) {
        zombie = new Model(Objects.requireNonNull(EntityType.ZOMBIE.create(level)), ZOMBIE_POSITION);
        golem = new Model(Objects.requireNonNull(EntityType.IRON_GOLEM.create(level)), GOLEM_POSITION);
        pillager = new Model(Objects.requireNonNull(EntityType.PILLAGER.create(level)), PILLAGER_POSITION);
        rightKnee = pillager.part("right_leg");
        leftKnee = pillager.part("left_leg");
        kneeOffsets = Map.of(rightKnee, kneeOffset("right_leg", rightKnee),
            leftKnee, kneeOffset("left_leg", leftKnee));
        zombieArm = zombie.part("right_arm");
        golemArm = golem.part("right_arm");
        torso = zombie.part("body");
        AABB body = bounds(zombie.cube(torso).corners());
        AABB arm = bounds(golem.cube(golemArm).corners());
        double side = Math.signum(center(zombie.cube(zombieArm)).x - body.getCenter().x);
        armRotation = SurgicalCubeRotation.IDENTITY;
        Vec3 contact = new Vec3(side > 0 ? arm.minX : arm.maxX, arm.maxY, arm.getCenter().z);
        Vec3 target = new Vec3(side > 0 ? body.maxX - 1.0 / 32 : body.minX + 1.0 / 32,
            body.maxY - 1.0 / 16, body.getCenter().z);
        armOffset = target.subtract(contact);
        reset(null);
    }

    public void placePillager() { pillagerPlaced = true; }
    public void setStage(int value) { stage = value; movement = 1; }
    public void setMovement(float value) { movement = value; }
    public void setSourceAppearance(boolean value) { sourceAppearance = value; }
    public void setJoints(int value) { joints = value; }
    public void showResult(boolean value) {
        resultVisible = value; resultTicks = 0;
        if (value) {
            var entity = body(joints).entity;
            entity.moveTo(RESULT_POSITION.x, RESULT_POSITION.y, RESULT_POSITION.z, 90, 0);
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = 90;
            entity.setOldPosAndRot();
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setOnGround(true);
        }
    }
    public void packBody() { packed = true; stage = 10; }
    public void unpackBody() { packed = false; }

    @Override
    public void reset(PonderScene scene) {
        stage = 0;
        pillagerPlaced = false;
        joints = 0;
        movement = 1;
        sourceAppearance = false;
        resultVisible = false;
        packed = false;
        resultTicks = 0;
        bodies.clear();
        setVisible(true);
    }

    @Override
    public void tick(PonderScene scene) {
        if (!resultVisible) return;
        Body body = body(joints);
        SlimeBionicEntity entity = body.entity;
        // Run the same controls and travel physics as a live body. Only its waypoint is authored.
        double destination = resultTicks / 100 % 2 == 0 ? 2.4 : 6.6;
        entity.setOldPosAndRot();
        entity.getMoveControl().setWantedPosition(destination, RESULT_POSITION.y, RESULT_POSITION.z, 0.65);
        entity.getLookControl().setLookAt(destination, RESULT_POSITION.y + entity.getEyeHeight(), RESULT_POSITION.z);
        entity.tick();
        int period = Math.max(80, body.attackInterval + 20);
        if (joints > 0 && resultTicks % period == 30)
            entity.applyAttackAction(resultTicks / period + 1, true, body.replacementOnLeft, 0,
                false, false, body.attackInterval, 1200, entity.yBodyRot, 0, entity.yBodyRot);
        resultTicks++;
    }

    public Vec3 point(String name) { return box(name).getCenter(); }

    public AABB box(String name) {
        return switch (name) {
            case "donor_right_leg" -> tableBounds(pillager, rightKnee, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "donor_left_leg" -> tableBounds(pillager, leftKnee, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "right_knee" -> tableBounds(zombie, pillager, rightKnee, kneeOffsets.get(rightKnee), SurgicalCubeRotation.IDENTITY);
            case "left_knee" -> tableBounds(zombie, pillager, leftKnee, kneeOffsets.get(leftKnee), SurgicalCubeRotation.IDENTITY);
            case "donor_arm" -> tableBounds(golem, golemArm, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "new_arm" -> tableBounds(zombie, golem, golemArm, armOffset, armRotation);
            case "old_arm" -> tableBounds(zombie, zombieArm,
                FLAT.inverseRotate(new Vec3(-1.3, 0, 1.7)), SurgicalCubeRotation.IDENTITY);
            case "head" -> tableBounds(zombie, zombie.part("head"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "left_arm" -> tableBounds(zombie, zombie.part("left_arm"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "right_leg" -> tableBounds(zombie, zombie.part("right_leg"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            case "left_leg" -> tableBounds(zombie, zombie.part("left_leg"), Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
            default -> tableBounds(zombie, torso, Vec3.ZERO, SurgicalCubeRotation.IDENTITY);
        };
    }

    public SlimeBionicEntity result() { return body(joints).entity; }

    private AABB tableBounds(Model frame, int cube, Vec3 offset, SurgicalCubeRotation rotation) {
        return tableBounds(frame, frame, cube, offset, rotation);
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
        for (PlacedCube part : visibleParts(pt)) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(part.root);
            draw(part.model, part.cube, part.origin, part.flat, part.offset, part.rotation, graphics.pose(), buffer);
            graphics.pose().popPose();
        }
    }

    private List<PlacedCube> visibleParts(float pt) {
        List<PlacedCube> parts = new ArrayList<>();
        if (stage > 0) {
            for (int cube = 0; cube < zombie.count(); cube++) {
                if (packed && cube != zombieArm) continue;
                Vec3 offset = Vec3.ZERO;
                if (cube == zombieArm && stage >= 3) {
                    Vec3 move = stage == 3 ? new Vec3(-0.45 * movement, 0, 0)
                        : new Vec3(-0.45, 0, 0).lerp(new Vec3(-1.3, 0, 1.7), stage == 4 ? movement : 1);
                    offset = FLAT.inverseRotate(move);
                }
                parts.add(new PlacedCube(zombie, cube, zombie.origin, true, offset, SurgicalCubeRotation.IDENTITY, new Matrix4f()));
            }
        }
        if (stage >= 2) {
            for (int cube = 0; cube < golem.count(); cube++) {
                if (cube == golemArm && stage >= 5) continue;
                parts.add(new PlacedCube(golem, cube, golem.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, new Matrix4f()));
            }
            if (stage >= 5 && !packed) {
                float progress = stage == 5 ? 0 : stage == 6 ? movement : 1;
                Vec3 origin = golem.origin.lerp(zombie.origin, progress);
                Vec3 offset = armOffset.scale(progress).add(FLAT.inverseRotate(new Vec3(0.5 * (1 - progress), 0, 0.5 * (1 - progress))));
                parts.add(new PlacedCube(golem, golemArm, origin, true, offset, armRotation, new Matrix4f()));
            }
        }
        if (pillagerPlaced) {
            for (int cube = 0; cube < pillager.count(); cube++) {
                boolean leg = cube == rightKnee || cube == leftKnee;
                if (leg && stage >= 8) continue;
                parts.add(new PlacedCube(pillager, cube, pillager.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, new Matrix4f()));
            }
            if (stage >= 8 && !packed) {
                float progress = stage == 8 ? 0 : stage == 9 ? movement : 1;
                for (int cube : List.of(rightKnee, leftKnee))
                    parts.add(new PlacedCube(pillager, cube, pillager.origin.lerp(zombie.origin, progress), true,
                        kneeOffsets.get(cube).scale(progress), SurgicalCubeRotation.IDENTITY, new Matrix4f()));
            }
        }
        if (resultVisible) addResultParts(parts, pt);
        return parts;
    }

    private void draw(Model model, int cube, Vec3 origin, boolean flat, Vec3 offset,
                      SurgicalCubeRotation rotation, PoseStack pose, MultiBufferSource buffer) {
        pose.pushPose();
        pose.translate(origin.x, origin.y, origin.z);
        if (flat) SurgicalTablePoseResolver.applyRotation(pose, FLAT);
        pose.translate(offset.x, offset.y, offset.z);
        Vec3 center = center(model.cube(cube));
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()));
        pose.translate(-center.x, -center.y, -center.z);
        BitSet selected = new BitSet();
        selected.set(cube);
        model.plan.render(pose, buffer, LightTexture.FULL_BRIGHT, model.count(), selected,
            Map.of(), Map.of(), false, null, resultVisible || sourceAppearance, null);
        pose.popPose();
    }

    private void addResultParts(List<PlacedCube> parts, float pt) {
        Body body = body(joints);
        List<SlimeBionicAnimator.Frame> frames = SlimeBionicAnimator.resolve(body.entity, body.rig, pt);
        Vec3 position = body.entity.getPosition(pt);
        // Canonical surgical vertices already face forward; match SlimeBionicRenderer.BodyFrame.
        Matrix4f root = new Matrix4f().translation((float) position.x, (float) position.y, (float) position.z)
            .rotateY((float) Math.toRadians(-Mth.rotLerp(pt, body.entity.yBodyRotO, body.entity.yBodyRot)))
            .translate((float) -body.origin.x, (float) -body.origin.y, (float) -body.origin.z);
        for (int source = 0; source < 3; source++) {
            Model model = model(source);
            SurgicalAssembly.Source saved = body.assembly.sources().get(source);
            SlimeBionicAnimator.Frame frame = frames.size() > source ? frames.get(source) : SlimeBionicAnimator.Frame.EMPTY;
            Map<Integer, Vec3> offsets = frame.offsets().isEmpty() ? saved.cubeOffsets() : frame.offsets();
            Map<Integer, SurgicalCubeRotation> rotations = frame.rotations().isEmpty() ? saved.cubeRotations() : frame.rotations();
            BitSet present = saved.presentCubes();
            for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
                parts.add(new PlacedCube(model, cube, Vec3.ZERO, false, offsets.getOrDefault(cube, Vec3.ZERO),
                    rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY), root));
        }
    }

    /** The highlight step consumes the very same live transforms as the model renderer. */
    public List<AABB> highlightBounds(List<String> names, float partialTick) {
        List<AABB> boxes = new ArrayList<>();
        for (PlacedCube part : visibleParts(partialTick)) {
            if (names.stream().noneMatch(name -> matches(part, name))) continue;
            Vec3 pivot = center(part.model.cube(part.cube));
            List<Vec3> corners = part.model.cube(part.cube).corners().stream().map(point -> {
                Vec3 p = pivot.add(part.rotation.rotate(point.subtract(pivot))).add(part.offset);
                if (part.flat) p = FLAT.rotate(p);
                p = p.add(part.origin);
                Vector3f world = part.root.transformPosition(new Vector3f((float) p.x, (float) p.y, (float) p.z));
                return new Vec3(world.x, world.y, world.z);
            }).toList();
            boxes.add(bounds(corners).inflate(0.015));
        }
        return boxes;
    }

    private boolean matches(PlacedCube part, String name) {
        return switch (name) {
            case "donor_arm" -> part.model == golem && part.cube == golemArm;
            case "new_arm" -> part.model == golem && part.cube == golemArm && (!part.flat || stage >= 5);
            case "old_arm" -> part.model == zombie && part.cube == zombieArm;
            case "donor_right_leg" -> part.model == pillager && part.cube == rightKnee;
            case "donor_left_leg" -> part.model == pillager && part.cube == leftKnee;
            case "right_knee" -> part.model == pillager && part.cube == rightKnee && (!part.flat || stage >= 8);
            case "left_knee" -> part.model == pillager && part.cube == leftKnee && (!part.flat || stage >= 8);
            case "body", "head", "left_arm", "right_leg", "left_leg" ->
                part.model == zombie && part.cube == zombie.part(name);
            default -> throw new IllegalArgumentException("Unknown surgical Ponder part: " + name);
        };
    }

    private record PlacedCube(Model model, int cube, Vec3 origin, boolean flat, Vec3 offset,
                              SurgicalCubeRotation rotation, Matrix4f root) {}

    private Body body(int count) {
        return bodies.computeIfAbsent(count, this::createBody);
    }

    private Body createBody(int count) {
        BitSet remaining = zombie.all();
        remaining.clear(zombieArm);
        BitSet replacement = new BitSet();
        replacement.set(golemArm);
        BitSet knees = new BitSet();
        knees.set(rightKnee); knees.set(leftKnee);
        List<SurgicalAssembly.Source> sources = List.of(zombie.source(remaining, Map.of(), Map.of()),
            golem.source(replacement, Map.of(golemArm, armOffset), Map.of(golemArm, armRotation)),
            pillager.source(knees, kneeOffsets, Map.of()));
        List<SurgicalAssembly.Limb> limbs = new ArrayList<>();
        if (count >= 1) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.SHOULDER, 1, golemArm, 0, torso));
        if (count >= 2) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.SHOULDER, 0, zombie.part("left_arm"), 0, torso));
        if (count >= 3) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.NECK, 0, zombie.part("head"), 0, torso));
        if (count >= 4) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.HIP, 0, zombie.part("right_leg"), 0, torso));
        if (count >= 5) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.HIP, 0, zombie.part("left_leg"), 0, torso));
        if (count >= 6) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.KNEE, 2, rightKnee, 0, zombie.part("right_leg")));
        if (count >= 7) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.KNEE, 2, leftKnee, 0, zombie.part("left_leg")));
        SurgicalAssembly assembly = Objects.requireNonNull(SurgicalAssembly.createComposite(sources,
            List.of(new SurgicalAssembly.Joint(0, torso, 1, golemArm),
                new SurgicalAssembly.Joint(0, zombie.part("right_leg"), 2, rightKnee),
                new SurgicalAssembly.Joint(0, zombie.part("left_leg"), 2, leftKnee)), List.of(), limbs,
            net.minecraft.core.Direction.NORTH, SurgicalLayPose.IDENTITY), "Invalid Ponder assembly");
        List<SlimeBionicAnimator.SourceState> states = new ArrayList<>();
        List<Map<Integer, List<Vec3>>> hitboxes = new ArrayList<>();
        List<List<Vec3>> all = new ArrayList<>();
        List<List<Vec3>> rigid = new ArrayList<>();
        for (int source = 0; source < 3; source++) {
            Model model = model(source);
            SurgicalAssembly.Source saved = sources.get(source);
            List<SurgicalModelRenderContext.CubeGeometry> geometry = new ArrayList<>();
            Map<Integer, List<Vec3>> boxes = new HashMap<>();
            BitSet present = saved.presentCubes();
            for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
                var original = model.cube(cube);
                Vec3 center = center(original);
                Vec3 offset = saved.cubeOffsets().getOrDefault(cube, Vec3.ZERO);
                var rotation = saved.cubeRotations().getOrDefault(cube, SurgicalCubeRotation.IDENTITY);
                List<Vec3> corners = original.corners().stream()
                    .map(p -> center.add(rotation.rotate(p.subtract(center))).add(offset)).toList();
                geometry.add(original.withCorners(corners));
                boxes.put(cube, corners);
                all.add(corners);
                if (!(source == 1 && count >= 1)
                    && !(source == 0 && count >= 2 && cube == zombie.part("left_arm"))) rigid.add(corners);
            }
            hitboxes.add(Map.copyOf(boxes));
            states.add(new SlimeBionicAnimator.SourceState(SlimeBionicAnimator.measure(
                new SurgicalModelRenderContext.Snapshot(model.count(), geometry)), saved.cubeOffsets(), saved.cubeRotations()));
        }
        AABB envelope = bounds(all.stream().flatMap(List::stream).toList());
        var visible = new SurgicalBodyBounds.Envelope(envelope.minX, envelope.minY, envelope.minZ,
            envelope.maxX, envelope.maxY, envelope.maxZ);
        var measurement = Objects.requireNonNull(SurgicalBodyBounds.measureWithVolume(count == 0 ? all : rigid, all, visible));
        var mobility = SlimeBionicAnimator.measureMobility(assembly, states);
        var bounds = measurement.bounds().withMobility(mobility.averageLegLength(), mobility.groundedLegCount(),
            mobility.groundedKneeCount(), mobility.legVolumeRatio());
        var hitbox = Objects.requireNonNull(SurgicalHitboxGeometry.measure(assembly, hitboxes, visible, bounds));
        Vec3 origin = new Vec3((envelope.minX + envelope.maxX) / 2 + bounds.centerX(),
            envelope.minY, (envelope.minZ + envelope.maxZ) / 2 + bounds.centerZ());
        assembly = assembly.withBodyGeometry(bounds, hitbox, measurement.volume());
        var attack = SlimeBionicAnimator.bakeAttackGeometry(assembly, states, origin,
            List.of(zombie.donorGeometry(), golem.donorGeometry(), pillager.donorGeometry()));
        if (attack != null) assembly = assembly.withAttackGeometry(attack);
        SlimeBionicEntity entity = new SceneBionicEntity(zombie.entity.level());
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
        entity.getRandom().setSeed(0x5A71C0DEL + count);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setOnGround(true);
        Vec3 donorCenter = center(golem.cube(golemArm)).add(armOffset).subtract(origin);
        double leftDistance = attack == null ? Double.POSITIVE_INFINITY : attack.left().stream()
            .mapToDouble(arm -> arm.origin().distanceToSqr(donorCenter)).min().orElse(Double.POSITIVE_INFINITY);
        double rightDistance = attack == null ? Double.POSITIVE_INFINITY : attack.right().stream()
            .mapToDouble(arm -> arm.origin().distanceToSqr(donorCenter)).min().orElse(Double.POSITIVE_INFINITY);
        boolean replacementOnLeft = leftDistance < rightDistance;
        var arm = attack == null ? null : (replacementOnLeft ? attack.left() : attack.right()).stream()
            .min(java.util.Comparator.comparingDouble(candidate -> candidate.origin().distanceToSqr(donorCenter)))
            .orElse(null);
        int interval = SurgicalCombatCalibration.attackInterval(SurgicalCombatCalibration.stats(arm, entity.level()),
            attack == null ? 1 : attack.arms().size(), entity.level());
        return new Body(assembly, entity, SlimeBionicAnimator.rig(assembly, states), origin, replacementOnLeft, interval);
    }

    private Model model(int source) { return source == 0 ? zombie : source == 1 ? golem : pillager; }

    private Vec3 kneeOffset(String upperLeg, int lowerLeg) {
        AABB upper = bounds(zombie.cube(zombie.part(upperLeg)).corners());
        AABB lower = bounds(pillager.cube(lowerLeg).corners());
        return new Vec3(upper.getCenter().x - lower.getCenter().x,
            upper.minY - lower.maxY + 1.0 / 32, upper.getCenter().z - lower.getCenter().z);
    }

    /** Client-owned Ponder body: native travel runs locally, with server AI and packets absent. */
    private static final class SceneBionicEntity extends SlimeBionicEntity {
        SceneBionicEntity(Level level) { super(CBEntityTypes.SLIME_BIONIC.get(), level); }
        @Override public boolean isControlledByLocalInstance() { return true; }
        @Override public void aiStep() {
            // Like native AI, update controls after baseTick snapshots the previous head/body angles.
            getMoveControl().tick();
            getLookControl().tick();
            getJumpControl().tick();
            super.aiStep();
        }
    }

    private record Body(SurgicalAssembly assembly, SlimeBionicEntity entity, SlimeBionicAnimator.Rig rig,
                        Vec3 origin, boolean replacementOnLeft, int attackInterval) {}

    private static final class Model {
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
            origin = position.add(-flat.getCenter().x, 1.0 / 16 + 0.005 - flat.minY, -flat.getCenter().z);
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

    private static Vec3 center(SurgicalModelRenderContext.CubeGeometry cube) { return bounds(cube.corners()).getCenter(); }
    private static AABB bounds(List<Vec3> corners) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Vec3 p : corners) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
