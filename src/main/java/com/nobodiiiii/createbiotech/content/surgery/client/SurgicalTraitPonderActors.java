package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.FLAT;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.bounds;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.center;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Limb;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.Model;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.PlacedCube;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderGreenprint.SourcePose;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned donors and assembled bodies for the trait chapter. */
public final class SurgicalTraitPonderActors extends PonderElementBase implements PonderSceneElement {
    private static final Vec3 TABLE = new Vec3(4.5, 2 + SurgicalTablePlane.SURFACE_HEIGHT + 0.005, 3.5);
    private static final Vec3 BLAST_POSITION = new Vec3(7.15, 1.05, 7.5);
    private static final int FLIGHT_TICKS = 42;
    private static final int FUSE_TICKS = 30;
    private static final int DISSECT_TICKS = 35;
    private static final double DISSECTION_SPREAD = 0.25;
    private static final double CUBE_SPACING = 0.0625;
    private static final double SEPARATION_TOLERANCE = 0.0001;
    private static final int ASSEMBLE_TICKS = 52;
    private static final int TABLE_TRANSITION_TICKS = 20;
    private static final double TABLE_TRANSITION_DISTANCE = 0.5;

    public enum View { HIDDEN, DONORS, DISSECTING, ASSEMBLING, FLIGHT, FUSE, DISPLAY, TABLE, PACKED }
    public enum Assembly { COMPLETE, ONE_SHOULDER }

    private final PonderLevel level;
    private final SurgicalAssembly greenprint;
    private final List<SourcePose> greenprintSources;
    private final SourcePose creeper;
    private final SourcePose phantom;
    private final int phantomIndex;
    private final List<Integer> leftWing;
    private final List<Integer> rightWing;
    private final Matrix4f tableRoot;
    private final List<DonorCube> donors;
    private final List<PlacedCube> unusedDonorParts;
    private final Map<Assembly, Body> bodies = new EnumMap<>(Assembly.class);
    private View view = View.HIDDEN;
    private Assembly variant = Assembly.COMPLETE;
    private boolean donorsDissected;
    private boolean returningToTable;
    private int unusedPartsExitAge = -1;
    private int age;

    public SurgicalTraitPonderActors(PonderLevel level) {
        this.level = level;
        var exported = SurgicalPonderGreenprint.load(level, "surgical_table_creeper_phantom");
        greenprint = exported.assembly();
        greenprintSources = exported.sources();
        creeper = donor(EntityType.CREEPER);
        phantom = donor(EntityType.PHANTOM);
        phantomIndex = greenprintSources.indexOf(phantom);
        leftWing = List.of(phantom.model().part("left_wing_base"), phantom.model().part("left_wing_tip"));
        rightWing = List.of(phantom.model().part("right_wing_base"), phantom.model().part("right_wing_tip"));
        // Undo the saved Phantom facing so its model lines up with the donor at native yaw zero.
        Quaternionf phantomFacing = phantom.tableFrame().getNormalizedRotation(new Quaternionf());
        Matrix4f alignment = new Matrix4f().rotation(phantomFacing.conjugate());
        AABB layout = bounds(greenprintSources.stream().flatMap(source -> source.parts(alignment,
            SlimeBionicAnimator.Frame.EMPTY).stream()).flatMap(part -> part.worldCorners().stream()).toList());
        tableRoot = translation(groundAt(layout, TABLE)).mul(alignment);
        List<DonorCube> cubes = new ArrayList<>();
        // At native yaw zero the face points along +Z. FLAT tips that face up and sends the head towards -Z.
        addDonor(cubes, creeper, FLAT, 2.5);
        addDonor(cubes, phantom, SurgicalLayPose.IDENTITY, 6.5);
        donors = List.copyOf(cubes);
        unusedDonorParts = donors.stream().filter(cube -> !cube.source().saved().containsCube(cube.cube()))
            .map(DonorCube::loosePart).toList();
    }

    private SourcePose donor(EntityType<?> type) {
        return greenprintSources.stream().filter(source -> source.model().entity.getType() == type)
            .findFirst().orElseThrow();
    }

    private void addDonor(List<DonorCube> cubes, SourcePose source, SurgicalLayPose donorPose, double x) {
        Matrix4f frame = new Matrix4f(tableRoot).mul(source.tableFrame());
        PoseStack pose = new PoseStack();
        SurgicalTablePoseResolver.applyRotation(pose, donorPose);
        Matrix4f donorFrame = pose.last().pose();
        Model model = source.model();
        AABB intact = model.all().stream()
            .mapToObj(cube -> bounds(source.place(cube, donorFrame, Vec3.ZERO, SurgicalCubeRotation.IDENTITY).worldCorners()))
            .reduce(AABB::minmax).orElseThrow();
        Vec3 donorCenter = new Vec3(x, TABLE.y, TABLE.z);
        Vec3 donorOffset = groundAt(intact, donorCenter);
        Matrix4f worldToSource = new Matrix4f(frame).invert();
        Matrix4f relative = new Matrix4f(worldToSource).mul(translation(donorOffset)).mul(donorFrame);
        var q = relative.getNormalizedRotation(new Quaternionf());
        var intactRotation = source == phantom ? SurgicalCubeRotation.IDENTITY
            : new SurgicalCubeRotation(q.x, q.y, q.z, q.w);
        List<Vec3> intactOffsets = new ArrayList<>(model.count());
        List<AABB> partBounds = new ArrayList<>(model.count());
        for (int cube = 0; cube < model.count(); cube++) {
            Vec3 pivot = center(model.cube(cube));
            var placedPivot = relative.transformPosition((float) pivot.x, (float) pivot.y, (float) pivot.z, new Vector3f());
            Vec3 start = new Vec3(placedPivot.x, placedPivot.y, placedPivot.z).subtract(pivot);
            intactOffsets.add(start);
            partBounds.add(bounds(source.place(cube, frame, start, intactRotation).worldCorners()));
        }
        List<Vec3> displacements = dissectionDisplacements(partBounds, donorCenter);
        for (int cube = 0; cube < model.count(); cube++) {
            Vec3 start = intactOffsets.get(cube);
            Vec3 move = displacements.get(cube);
            var localMove = worldToSource.transformDirection((float) move.x, (float) move.y, (float) move.z, new Vector3f());
            Vec3 loose = start.add(localMove.x, localMove.y, localMove.z);
            boolean selected = source.saved().containsCube(cube);
            var assembledRotation = selected
                ? source.rotations().getOrDefault(cube, SurgicalCubeRotation.IDENTITY) : intactRotation;
            Vec3 assembled = selected ? source.offsets().getOrDefault(cube, Vec3.ZERO) : loose;
            cubes.add(new DonorCube(source, cube, frame, start, intactRotation, loose, assembled, assembledRotation));
        }
    }

    private static List<Vec3> dissectionDisplacements(List<AABB> pieces, Vec3 donorCenter) {
        List<Vec3> moves = new ArrayList<>(pieces.size());
        for (AABB piece : pieces) {
            Vec3 spread = piece.getCenter().subtract(donorCenter).scale(DISSECTION_SPREAD);
            moves.add(new Vec3(spread.x, TABLE.y - piece.minY, spread.z));
        }
        // Only separate pieces whose footprints would overlap after they are laid on the table.
        for (int pass = 0; pass < pieces.size() * 4; pass++) {
            boolean adjusted = false;
            for (int first = 0; first < pieces.size(); first++) {
                for (int second = first + 1; second < pieces.size(); second++) {
                    Vec3 firstMove = moves.get(first);
                    Vec3 secondMove = moves.get(second);
                    Vec3 shift = footprintSeparation(pieces.get(first), firstMove, pieces.get(second), secondMove);
                    if (shift == Vec3.ZERO) continue;
                    moves.set(first, firstMove.subtract(shift));
                    moves.set(second, secondMove.add(shift));
                    adjusted = true;
                }
            }
            if (!adjusted) break;
        }
        AABB spread = pieces.getFirst().move(moves.getFirst());
        for (int index = 1; index < pieces.size(); index++)
            spread = spread.minmax(pieces.get(index).move(moves.get(index)));
        double dx = Mth.clamp(0, 1 - spread.minX, 8 - spread.maxX);
        double dz = Mth.clamp(0, 1 - spread.minZ, 6 - spread.maxZ);
        return moves.stream().map(move -> move.add(dx, 0, dz)).toList();
    }

    private static Vec3 footprintSeparation(AABB first, Vec3 firstMove, AABB second, Vec3 secondMove) {
        Vec3 original = second.getCenter().subtract(first.getCenter());
        Vec3 delta = original.add(secondMove).subtract(firstMove);
        double overlapX = (first.getXsize() + second.getXsize()) / 2 + CUBE_SPACING - Math.abs(delta.x);
        double overlapZ = (first.getZsize() + second.getZsize()) / 2 + CUBE_SPACING - Math.abs(delta.z);
        if (overlapX <= SEPARATION_TOLERANCE || overlapZ <= SEPARATION_TOLERANCE) return Vec3.ZERO;

        // Stacked parts, such as the Creeper's legs, separate front to back on their original side.
        boolean stacked = original.horizontalDistanceSqr() < SEPARATION_TOLERANCE;
        if (stacked || overlapZ < overlapX) {
            double direction = Math.abs(delta.z) > SEPARATION_TOLERANCE ? delta.z : original.y;
            return new Vec3(0, 0, Math.copySign(overlapZ / 2, direction));
        }
        return new Vec3(Math.copySign(overlapX / 2, delta.x), 0, 0);
    }

    private static Vec3 groundAt(AABB box, Vec3 target) {
        Vec3 center = box.getCenter();
        return target.subtract(center.x, box.minY, center.z);
    }

    private static Matrix4f translation(Vec3 offset) {
        return new Matrix4f().translation((float) offset.x, (float) offset.y, (float) offset.z);
    }

    @Override
    public void reset(PonderScene scene) {
        bodies.clear();
        donorsDissected = false;
        unusedPartsExitAge = -1;
        show(View.HIDDEN, Assembly.COMPLETE);
        setVisible(true);
    }

    public void show(View next, Assembly assembly) {
        if (next == View.DONORS) {
            donorsDissected = false;
            unusedPartsExitAge = -1;
        }
        if (next == View.DISSECTING || next == View.ASSEMBLING) donorsDissected = true;
        boolean continuingFlight = view == View.FLIGHT && next == View.FUSE && variant == assembly;
        returningToTable = false;
        view = next;
        variant = assembly;
        age = 0;
        if (view == View.FLIGHT || view == View.FUSE || view == View.DISPLAY) {
            SlimeBionicEntity entity = body().entity();
            if (!continuingFlight) entity.tickCount = 0;
            entity.setOnGround(view == View.DISPLAY);
            float yaw = SurgicalPonderActors.RESULT_START_YAW;
            entity.setYRot(yaw);
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw;
        }
    }

    public void returnToTable() {
        show(View.TABLE, Assembly.COMPLETE);
        returningToTable = true;
        unusedPartsExitAge = 0;
    }

    @Override
    public void tick(PonderScene scene) {
        age++;
        if (unusedPartsExitAge >= 0 && unusedPartsExitAge < TABLE_TRANSITION_TICKS) unusedPartsExitAge++;
        if (view == View.FLIGHT || view == View.FUSE) body().entity().tickCount++;
    }

    public BionicTraitSet traits() {
        return body().entity().getBionicTraits();
    }

    private Body body() { return bodies.computeIfAbsent(variant, this::createBody); }

    private Body createBody(Assembly version) {
        SurgicalAssembly assembly = greenprint;
        Matrix4f upright = SurgicalPonderGreenprint.upright(greenprint);
        List<SlimeBionicAnimator.SourceState> rest = greenprintSources.stream().map(source -> source.measure(upright)).toList();
        if (version == Assembly.ONE_SHOULDER) {
            List<Limb> limbs = new ArrayList<>(greenprint.limbs());
            limbs.removeIf(limb -> limb.type() == SurgicalLimbType.SHOULDER
                && limb.childSource() == phantomIndex && rightWing.contains(limb.childCube()));
            var incomplete = Objects.requireNonNull(SurgicalAssembly.createComposite(greenprint.sources(), greenprint.joints(),
                greenprint.combinations(), limbs, greenprint.layoutFacing(), greenprint.layoutLayPose()));
            assembly = SurgicalPonderActors.measureBody(incomplete, greenprintSources.stream().map(SourcePose::model).toList(), rest,
                member -> member.source() != phantomIndex || !isWing(member.cube()));
        }
        Vec3 origin = SurgicalPonderActors.bodyOrigin(assembly, rest);
        SlimeBionicEntity entity = Objects.requireNonNull(CBEntityTypes.SLIME_BIONIC.get().create(level));
        ((SlimeMimicAccess) (Object) entity).createBiotech$setSlimeMimic(false);
        entity.setNoAi(true);
        entity.setSilent(true);
        entity.setAssembly(assembly);
        return new Body(entity, SlimeBionicAnimator.rig(assembly, rest), upright, origin);
    }

    private boolean isWing(int cube) {
        return leftWing.contains(cube) || rightWing.contains(cube);
    }

    @Override
    public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {}

    @Override
    public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {}

    @Override
    public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        PoseStack pose = graphics.pose();
        float time = age + pt;
        if (view == View.DONORS || view == View.DISSECTING || view == View.ASSEMBLING) {
            for (DonorCube cube : donors) cube.place(view, time).draw(pose, buffer, true);
            return;
        }
        float unusedExit = unusedPartsExitAge < 0 ? 0 : eased(unusedPartsExitAge + pt, TABLE_TRANSITION_TICKS);
        if (donorsDissected && unusedExit < 1) {
            pose.pushPose();
            pose.translate(0, TABLE_TRANSITION_DISTANCE * unusedExit, 0);
            MultiBufferSource fading = SurgicalSourceModelRenderer.alphaBuffer(buffer, 1 - unusedExit);
            for (PlacedCube part : unusedDonorParts) part.draw(pose, fading, true);
            pose.popPose();
        }
        if (view == View.HIDDEN || view == View.PACKED) return;

        Body body = body();
        Matrix4f root = tableRoot;
        if (returningToTable) {
            float entrance = eased(time, TABLE_TRANSITION_TICKS);
            if (entrance == 0) return;
            root = translation(new Vec3(0, TABLE_TRANSITION_DISTANCE * (1 - entrance), 0)).mul(tableRoot);
            buffer = SurgicalSourceModelRenderer.alphaBuffer(buffer, entrance);
        }
        List<SlimeBionicAnimator.Frame> frames = List.of();
        if (view != View.TABLE) {
            Vec3 position = switch (view) {
                case FLIGHT -> flightPosition(time);
                case FUSE -> BLAST_POSITION;
                default -> SurgicalPonderActors.RESULT_POSITION;
            };
            Vec3 origin = body.origin();
            float swelling = view == View.FUSE ? Mth.clamp(time / FUSE_TICKS, 0, 1) : 0;
            root = translation(position)
                .rotateY(-SurgicalPonderActors.RESULT_START_YAW * Mth.DEG_TO_RAD).scale(1 + swelling * swelling * 0.3f)
                .translate((float) -origin.x, (float) -origin.y, (float) -origin.z).mul(body.upright());
            frames = SlimeBionicAnimator.resolve(body.entity(), body.rig(), view == View.DISPLAY ? 0 : pt);
            if (view == View.FUSE) buffer = SurgicalSourceModelRenderer.selfDestructBuffer(buffer, swelling);
        }
        for (int index = 0; index < greenprintSources.size(); index++) {
            var frame = frames.size() > index ? frames.get(index) : SlimeBionicAnimator.Frame.EMPTY;
            for (PlacedCube part : greenprintSources.get(index).parts(root, frame)) part.draw(pose, buffer, true);
        }
    }

    private static Vec3 flightPosition(float time) {
        float progress = eased(time, FLIGHT_TICKS);
        return SurgicalPonderActors.RESULT_POSITION.lerp(BLAST_POSITION, progress)
            .add(0, Math.sin(Math.PI * progress) * 1.35, 0);
    }

    private static float eased(float time, int duration) {
        float t = Mth.clamp(time / duration, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record DonorCube(SourcePose source, int cube, Matrix4f frame, Vec3 intact,
                             SurgicalCubeRotation intactRotation, Vec3 loose, Vec3 assembled,
                             SurgicalCubeRotation assembledRotation) {
        PlacedCube loosePart() {
            return source.place(cube, frame, loose, intactRotation);
        }

        PlacedCube place(View view, float time) {
            if (view == View.DONORS)
                return source.place(cube, frame, intact, intactRotation);
            if (view == View.DISSECTING) {
                float t = eased(time, DISSECT_TICKS);
                return source.place(cube, frame, intact.lerp(loose, t), intactRotation);
            }
            int delay = cube * 2;
            float t = eased(time - delay, ASSEMBLE_TICKS - delay);
            return source.place(cube, frame, loose.lerp(assembled, t), assemblyRotation(t));
        }

        private SurgicalCubeRotation assemblyRotation(float progress) {
            if (progress == 0 || intactRotation.equals(assembledRotation)) return intactRotation;
            if (progress == 1) return assembledRotation;
            var q = new Quaternionf((float) intactRotation.x(), (float) intactRotation.y(),
                (float) intactRotation.z(), (float) intactRotation.w())
                .slerp(new Quaternionf((float) assembledRotation.x(), (float) assembledRotation.y(),
                    (float) assembledRotation.z(), (float) assembledRotation.w()), progress);
            return new SurgicalCubeRotation(q.x, q.y, q.z, q.w);
        }
    }

    private record Body(SlimeBionicEntity entity, SlimeBionicAnimator.Rig rig, Matrix4f upright, Vec3 origin) {}
}
