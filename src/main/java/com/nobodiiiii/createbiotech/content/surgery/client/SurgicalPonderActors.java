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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalBodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned surgery models. No world subjects, packets, save formats or global preview entities are edited. */
public final class SurgicalPonderActors extends PonderElementBase implements PonderSceneElement {
    public static final Vec3 ZOMBIE_POSITION = new Vec3(3.5, 2, 4.5);
    public static final Vec3 GOLEM_POSITION = new Vec3(7, 2, 4.5);
    public static final Vec3 RESULT_POSITION = new Vec3(5, 1, 9);
    private static final SurgicalLayPose FLAT = new SurgicalLayPose(SurgicalLayPose.RotationAxis.X, 0, Vec3.ZERO);
    private final Model zombie;
    private final Model golem;
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
        zombieArm = zombie.part("right_arm");
        golemArm = golem.part("right_arm");
        torso = zombie.part("body");
        AABB body = bounds(zombie.cube(torso).corners());
        AABB arm = bounds(golem.cube(golemArm).corners());
        double side = Math.signum(center(zombie.cube(zombieArm)).x - body.getCenter().x);
        armRotation = SurgicalCubeRotation.around(new Vec3(0, 0, 1), side * 55);
        // Raise the long donor arm outwards so the original feet still touch the ground.
        Vec3 contact = new Vec3(side > 0 ? arm.minX : arm.maxX, arm.maxY, arm.getCenter().z);
        Vec3 rotatedContact = arm.getCenter().add(armRotation.rotate(contact.subtract(arm.getCenter())));
        Vec3 target = new Vec3(side > 0 ? body.maxX - 1.0 / 32 : body.minX + 1.0 / 32,
            body.maxY - 1.0 / 16, body.getCenter().z);
        armOffset = target.subtract(rotatedContact);
        reset(null);
    }

    public void setStage(int value) { stage = value; movement = 1; }
    public void setMovement(float value) { movement = value; }
    public void setSourceAppearance(boolean value) { sourceAppearance = value; }
    public void setJoints(int value) { joints = value; }
    public void showResult(boolean value) { resultVisible = value; resultTicks = 0; }
    public void packBody() { packed = true; stage = 7; }
    public void unpackBody() { packed = false; }

    @Override
    public void reset(PonderScene scene) {
        stage = 0;
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
        body.entity.tick();
        body.entity.setDeltaMovement(Vec3.ZERO);
        body.entity.setOnGround(true);
        body.entity.walkAnimation.update(joints == 0 ? 0 : 0.65f, 0.4f);
        body.entity.yHeadRotO = body.entity.yHeadRot;
        body.entity.yHeadRot = (float) Math.sin(resultTicks * 0.08) * 35;
        if (joints > 0 && resultTicks % 80 == 30)
            body.entity.applyAttackAction(resultTicks / 80 + 1, true, body.replacementOnLeft, 0,
                false, false, 20, 20, 0, 0, 0);
        resultTicks++;
    }

    public Vec3 point(String name) { return box(name).getCenter(); }

    public AABB box(String name) {
        return switch (name) {
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
        PoseStack pose = graphics.pose();
        if (stage > 0) {
            for (int cube = 0; cube < zombie.count(); cube++) {
                if (packed && cube != zombieArm) continue;
                Vec3 offset = Vec3.ZERO;
                if (cube == zombieArm && stage >= 3) {
                    Vec3 move = stage == 3 ? new Vec3(-0.45 * movement, 0, 0)
                        : new Vec3(-0.45, 0, 0).lerp(new Vec3(-1.3, 0, 1.7), movement);
                    offset = FLAT.inverseRotate(move);
                }
                draw(zombie, cube, zombie.origin, true, offset, SurgicalCubeRotation.IDENTITY, pose, buffer);
            }
        }
        if (stage >= 2) {
            for (int cube = 0; cube < golem.count(); cube++) {
                if (cube == golemArm && stage >= 5) continue;
                draw(golem, cube, golem.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, pose, buffer);
            }
            if (stage >= 5 && !packed) {
                float progress = stage == 5 ? 0 : stage == 6 ? movement : 1;
                Vec3 origin = golem.origin.lerp(zombie.origin, progress);
                Vec3 offset = armOffset.scale(progress).add(FLAT.inverseRotate(new Vec3(0.5 * (1 - progress), 0, 0.5 * (1 - progress))));
                SurgicalCubeRotation rotation = new SurgicalCubeRotation(0, 0,
                    Math.sin(Math.asin(armRotation.z()) * progress), Math.cos(Math.asin(armRotation.z()) * progress));
                draw(golem, golemArm, origin, true, offset, rotation, pose, buffer);
            }
        }
        if (resultVisible) renderResult(pose, buffer, pt);
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
            Map.of(), Map.of(), false, null, sourceAppearance, null);
        pose.popPose();
    }

    private void renderResult(PoseStack pose, MultiBufferSource buffer, float pt) {
        Body body = body(joints);
        List<SlimeBionicAnimator.Frame> frames = SlimeBionicAnimator.resolve(body.entity, body.rig, pt);
        pose.pushPose();
        double hop = joints == 0 ? Math.abs(Math.sin((resultTicks + pt) * Math.PI / 25)) * 0.55 : 0;
        double walk = joints == 0 ? Math.sin((resultTicks + pt) * 0.03) * 0.4 : Math.sin((resultTicks + pt) * 0.025) * 1.2;
        pose.translate(RESULT_POSITION.x + walk, RESULT_POSITION.y + hop, RESULT_POSITION.z);
        pose.mulPose(Axis.YP.rotationDegrees(160));
        // Match the body's measured origin while keeping native donor geometry and real animator frames.
        pose.translate(-body.origin.x, -body.origin.y, -body.origin.z);
        for (int source = 0; source < 2; source++) {
            Model model = source == 0 ? zombie : golem;
            SurgicalAssembly.Source saved = body.assembly.sources().get(source);
            SlimeBionicAnimator.Frame frame = frames.size() > source ? frames.get(source) : SlimeBionicAnimator.Frame.EMPTY;
            Map<Integer, Vec3> offsets = frame.offsets().isEmpty() ? saved.cubeOffsets() : frame.offsets();
            Map<Integer, SurgicalCubeRotation> rotations = frame.rotations().isEmpty() ? saved.cubeRotations() : frame.rotations();
            BitSet present = saved.presentCubes();
            for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
                draw(model, cube, Vec3.ZERO, false, offsets.getOrDefault(cube, Vec3.ZERO),
                    rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY), pose, buffer);
        }
        pose.popPose();
    }

    private Body body(int count) {
        return bodies.computeIfAbsent(count, this::createBody);
    }

    private Body createBody(int count) {
        BitSet remaining = zombie.all();
        remaining.clear(zombieArm);
        BitSet replacement = new BitSet();
        replacement.set(golemArm);
        List<SurgicalAssembly.Source> sources = List.of(zombie.source(remaining, Map.of(), Map.of()),
            golem.source(replacement, Map.of(golemArm, armOffset), Map.of(golemArm, armRotation)));
        List<SurgicalAssembly.Limb> limbs = new ArrayList<>();
        if (count >= 1) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.SHOULDER, 1, golemArm, 0, torso));
        if (count >= 2) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.SHOULDER, 0, zombie.part("left_arm"), 0, torso));
        if (count >= 3) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.NECK, 0, zombie.part("head"), 0, torso));
        if (count >= 4) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.HIP, 0, zombie.part("right_leg"), 0, torso));
        if (count >= 5) limbs.add(new SurgicalAssembly.Limb(SurgicalLimbType.HIP, 0, zombie.part("left_leg"), 0, torso));
        SurgicalAssembly assembly = Objects.requireNonNull(SurgicalAssembly.createComposite(sources,
            List.of(new SurgicalAssembly.Joint(0, torso, 1, golemArm)), List.of(), limbs,
            net.minecraft.core.Direction.NORTH, SurgicalLayPose.IDENTITY), "Invalid Ponder assembly");
        List<SlimeBionicAnimator.SourceState> states = new ArrayList<>();
        List<Map<Integer, List<Vec3>>> hitboxes = new ArrayList<>();
        List<List<Vec3>> all = new ArrayList<>();
        List<List<Vec3>> rigid = new ArrayList<>();
        for (int source = 0; source < 2; source++) {
            Model model = source == 0 ? zombie : golem;
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
            List.of(zombie.donorGeometry(), golem.donorGeometry()));
        if (attack != null) assembly = assembly.withAttackGeometry(attack);
        SlimeBionicEntity entity = Objects.requireNonNull(CBEntityTypes.SLIME_BIONIC.get().create(zombie.entity.level()));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setAssembly(assembly);
        entity.setOnGround(true);
        Vec3 donorCenter = center(golem.cube(golemArm)).add(armOffset).subtract(origin);
        double leftDistance = attack == null ? Double.POSITIVE_INFINITY : attack.left().stream()
            .mapToDouble(arm -> arm.origin().distanceToSqr(donorCenter)).min().orElse(Double.POSITIVE_INFINITY);
        double rightDistance = attack == null ? Double.POSITIVE_INFINITY : attack.right().stream()
            .mapToDouble(arm -> arm.origin().distanceToSqr(donorCenter)).min().orElse(Double.POSITIVE_INFINITY);
        return new Body(assembly, entity, SlimeBionicAnimator.rig(assembly, states), origin, leftDistance < rightDistance);
    }

    private record Body(SurgicalAssembly assembly, SlimeBionicEntity entity, SlimeBionicAnimator.Rig rig,
                        Vec3 origin, boolean replacementOnLeft) {}

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
