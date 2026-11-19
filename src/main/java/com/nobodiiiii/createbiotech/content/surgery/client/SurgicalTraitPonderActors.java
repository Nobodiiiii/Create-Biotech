package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPackedPreviewService.packedUprightOffsets;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPackedPreviewService.packedUprightRotations;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.FLAT;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.bounds;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.center;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Limb;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Source;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.Model;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.PlacedCube;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned donors and assembled bodies for the trait chapter. */
public final class SurgicalTraitPonderActors extends PonderElementBase implements PonderSceneElement {
    private static final Vec3 TABLE = new Vec3(4.5, 2 + SurgicalTablePlane.SURFACE_HEIGHT + 0.005, 3.5);
    private static final Vec3 DISPLAY = new Vec3(4.5, 1, 7.5);
    private static final Vec3 DIVE_END = new Vec3(5.15, 1.05, 7.5);
    private static final Vec3 TAKEOFF = new Vec3(2.5, 1.1, 7.5);
    private static final Vec3 CRUISE = new Vec3(4.5, 2.6, 7.5);
    private static final int DISSECT_TICKS = 35;
    private static final double DISSECT_SPREAD = 1.5;
    private static final int ASSEMBLE_TICKS = 52;

    public enum View { HIDDEN, DONORS, DISSECTING, ASSEMBLING, FLIGHT, DIVE, DISPLAY, TABLE, PACKED }
    public enum Assembly { COMPLETE, EXTRA_TORSO, ONE_SHOULDER }

    private final PonderLevel level;
    private final SurgicalAssembly greenprint;
    private final List<SourcePose> greenprintSources;
    private final SourcePose creeper;
    private final SourcePose phantom;
    private final Model golem;
    private final int creeperIndex;
    private final int phantomIndex;
    private final int torso;
    private final int extraTorso;
    private final List<Integer> leftWing;
    private final List<Integer> rightWing;
    private final Vec3 extraOffset;
    private final Matrix4f tableRoot;
    private final List<DonorCube> donors;
    private final Map<Assembly, Body> bodies = new EnumMap<>(Assembly.class);
    private View view = View.HIDDEN;
    private Assembly variant = Assembly.COMPLETE;
    private Vec3 diveStart = CRUISE;
    private int age;

    public SurgicalTraitPonderActors(PonderLevel level) {
        this.level = level;
        greenprint = loadGreenprint();
        greenprintSources = greenprint.sources().stream().map(source -> {
            Model model = new Model(Objects.requireNonNull(source.profile().createPreviewEntity(level)), Vec3.ZERO);
            if (model.count() != source.cubeCount())
                throw new IllegalStateException("Donor model does not match the surgical table greenprint: "
                    + source.profile().entityTypeId());
            return new SourcePose(greenprint, source, model);
        }).toList();
        creeper = donor(EntityType.CREEPER);
        phantom = donor(EntityType.PHANTOM);
        creeperIndex = greenprintSources.indexOf(creeper);
        phantomIndex = greenprintSources.indexOf(phantom);
        golem = new Model(Objects.requireNonNull(EntityType.IRON_GOLEM.create(level)), Vec3.ZERO);
        torso = creeper.model().part("body");
        extraTorso = golem.part("body");
        leftWing = List.of(phantom.model().part("left_wing_base"), phantom.model().part("left_wing_tip"));
        rightWing = List.of(phantom.model().part("right_wing_base"), phantom.model().part("right_wing_tip"));
        var identity = new Matrix4f();
        AABB layout = bounds(greenprintSources.stream().flatMap(source -> source.parts(identity,
            SlimeBionicAnimator.Frame.EMPTY).stream()).flatMap(part -> part.worldCorners().stream()).toList());
        tableRoot = translation(groundAt(layout, TABLE));
        AABB core = bounds(creeper.restPart(torso, creeper.tableFrame()).worldCorners());
        AABB extra = bounds(golem.cube(extraTorso).corners());
        extraOffset = new Vec3(core.getCenter().x - extra.getCenter().x,
            core.minY - extra.minY, core.maxZ - extra.minZ - 1.0 / 64);
        List<DonorCube> cubes = new ArrayList<>();
        // At native yaw zero the face points along +Z. FLAT tips that face up and sends the head towards -Z.
        addDonor(cubes, creeper, FLAT, 2.5);
        addDonor(cubes, phantom, SurgicalLayPose.IDENTITY, 6.5);
        donors = List.copyOf(cubes);
    }

    private static SurgicalAssembly loadGreenprint() {
        var location = CreateBiotech.asResource("ponder/greenprints/surgical_table_creeper_phantom.nbt");
        try (var file = Minecraft.getInstance().getResourceManager().open(location)) {
            CompoundTag snapshot = NbtIo.readCompressed(file, NbtAccounter.create(1_048_576));
            SurgicalAssembly assembly = SurgicalAssembly.load(snapshot.getCompound("MimicEntity").getCompound("SurgicalAssembly"));
            if (assembly == null || !assembly.isReadyForEntity())
                throw new IllegalStateException("Invalid surgical table Ponder greenprint: " + location);
            return assembly;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read surgical table Ponder greenprint: " + location, error);
        }
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
        List<List<Vec3>> donorCorners = model.all().stream()
            .mapToObj(cube -> source.place(cube, donorFrame, Vec3.ZERO, SurgicalCubeRotation.IDENTITY))
            .map(PlacedCube::worldCorners).toList();
        AABB intact = bounds(donorCorners.stream().flatMap(List::stream).toList());
        Vec3 center = intact.getCenter();
        Vec3 donorOffset = groundAt(intact, new Vec3(x, TABLE.y, TABLE.z));
        // Spread the original arrangement by moving each cube away from the donor's center.
        List<Vec3> separation = donorCorners.stream()
            .map(corners -> bounds(corners).getCenter().subtract(center).scale(DISSECT_SPREAD - 1)).toList();
        List<Vec3> expandedCorners = new ArrayList<>();
        for (int cube = 0; cube < donorCorners.size(); cube++) {
            Vec3 move = separation.get(cube);
            for (Vec3 corner : donorCorners.get(cube)) expandedCorners.add(corner.add(move));
        }
        AABB expanded = bounds(expandedCorners);
        // Shift the spread as a group to keep it over the tabletop shown in this chapter.
        Vec3 expandedOffset = new Vec3(Mth.clamp(donorOffset.x, 1 - expanded.minX, 8 - expanded.maxX),
            TABLE.y - expanded.minY, Mth.clamp(donorOffset.z, 1 - expanded.minZ, 6 - expanded.maxZ));
        Vec3 shift = expandedOffset.subtract(donorOffset);
        Matrix4f relative = new Matrix4f(frame).invert().mul(translation(donorOffset)).mul(donorFrame);
        var q = relative.getNormalizedRotation(new Quaternionf());
        var intactRotation = new SurgicalCubeRotation(q.x, q.y, q.z, q.w);
        var layPose = source.saved().layPose();
        for (int cube = 0; cube < model.count(); cube++) {
            Vec3 pivot = center(model.cube(cube));
            var placedPivot = relative.transformPosition((float) pivot.x, (float) pivot.y, (float) pivot.z, new Vector3f());
            Vec3 start = new Vec3(placedPivot.x, placedPivot.y, placedPivot.z).subtract(pivot);
            Vec3 loose = start.add(layPose.inverseRotate(separation.get(cube).add(shift)));
            boolean selected = source.saved().containsCube(cube);
            var assembledRotation = selected
                ? source.rotations().getOrDefault(cube, SurgicalCubeRotation.IDENTITY) : intactRotation;
            Vec3 assembled = selected ? source.offsets().getOrDefault(cube, Vec3.ZERO) : loose;
            cubes.add(new DonorCube(source, cube, frame, start, intactRotation, loose, assembled, assembledRotation));
        }
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
        show(View.HIDDEN, Assembly.COMPLETE);
        setVisible(true);
    }

    public void show(View next, Assembly assembly) {
        boolean continuingFlight = view == View.FLIGHT && next == View.DIVE && variant == assembly;
        if (next == View.DIVE) diveStart = continuingFlight ? flightPosition(age) : CRUISE;
        view = next;
        variant = assembly;
        age = 0;
        if (view == View.FLIGHT || view == View.DIVE || view == View.DISPLAY) {
            SlimeBionicEntity entity = body().entity();
            if (!continuingFlight) entity.tickCount = 0;
            entity.setOnGround(view == View.DISPLAY);
            entity.setYRot(-90);
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = -90;
        }
    }

    @Override
    public void tick(PonderScene scene) {
        age++;
        if (view == View.FLIGHT || view == View.DIVE) body().entity().tickCount++;
    }

    private Body body() { return bodies.computeIfAbsent(variant, this::createBody); }

    private Body createBody(Assembly version) {
        List<SourcePose> poses = new ArrayList<>(greenprintSources);
        List<Source> sources = new ArrayList<>(greenprint.sources());
        List<SurgicalAssembly.Joint> connections = new ArrayList<>(greenprint.joints());
        List<Limb> limbs = new ArrayList<>(greenprint.limbs());
        if (version == Assembly.ONE_SHOULDER)
            limbs.removeIf(limb -> limb.type() == SurgicalLimbType.SHOULDER
                && limb.childSource() == phantomIndex && rightWing.contains(limb.childCube()));
        if (version == Assembly.EXTRA_TORSO) {
            BitSet extra = new BitSet();
            extra.set(extraTorso);
            Vec3 savedOffset = greenprint.layoutLayPose().rotate(extraOffset);
            Source tissue = golem.source(extra, Map.of(extraTorso, savedOffset), Map.of());
            connections.add(new SurgicalAssembly.Joint(creeperIndex, torso, sources.size(), extraTorso));
            sources.add(tissue);
            poses.add(new SourcePose(greenprint, tissue, golem));
        }
        SurgicalAssembly assembly = version == Assembly.COMPLETE ? greenprint
            : Objects.requireNonNull(SurgicalAssembly.createComposite(sources, connections, greenprint.combinations(),
                limbs, greenprint.layoutFacing(), greenprint.layoutLayPose()));
        PoseStack uprightPose = new PoseStack();
        SurgicalTablePoseResolver.applyInverseRotation(uprightPose, assembly.layoutLayPose());
        Matrix4f upright = new Matrix4f(uprightPose.last().pose());
        List<SlimeBionicAnimator.SourceState> rest = poses.stream().map(source -> source.measure(upright)).toList();
        if (version != Assembly.COMPLETE) {
            assembly = SurgicalPonderActors.measureBody(assembly, poses.stream().map(SourcePose::model).toList(), rest,
                member -> member.source() != phantomIndex || !isWing(member.cube())).assembly();
        }
        AABB envelope = bounds(rest.stream().flatMap(source -> source.boxes().values().stream())
            .flatMap(box -> box.points().stream()).toList());
        Vec3 center = envelope.getCenter();
        var bodyBounds = assembly.bodyBounds();
        Vec3 origin = new Vec3(center.x + bodyBounds.centerX(), envelope.minY, center.z + bodyBounds.centerZ());
        SlimeBionicEntity entity = Objects.requireNonNull(CBEntityTypes.SLIME_BIONIC.get().create(level));
        ((SlimeMimicAccess) (Object) entity).createBiotech$setSlimeMimic(false);
        entity.setNoAi(true);
        entity.setSilent(true);
        entity.setAssembly(assembly);
        return new Body(List.copyOf(poses), entity, SlimeBionicAnimator.rig(assembly, rest), upright, origin);
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
        for (PlacedCube part : parts(pt))
            part.draw(graphics.pose(), buffer, true);
    }

    private List<PlacedCube> parts(float pt) {
        if (view == View.HIDDEN || view == View.PACKED) return List.of();
        float time = age + pt;
        if (view == View.DONORS || view == View.DISSECTING || view == View.ASSEMBLING)
            return donors.stream().map(cube -> cube.place(view, time)).toList();
        Body body = body();
        Matrix4f root = tableRoot;
        List<SlimeBionicAnimator.Frame> frames = List.of();
        if (view != View.TABLE) {
            Vec3 position = switch (view) {
                case FLIGHT -> flightPosition(time);
                case DIVE -> {
                    float progress = Mth.clamp(time / 52, 0, 1);
                    yield diveStart.lerp(DIVE_END, progress * progress);
                }
                default -> DISPLAY;
            };
            Vec3 origin = body.origin();
            float swelling = view == View.DIVE ? Mth.clamp((time - 22) / 30, 0, 1) : 0;
            root = translation(position)
                .rotateY(Mth.HALF_PI).scale(1 + swelling * swelling * 0.3f)
                .translate((float) -origin.x, (float) -origin.y, (float) -origin.z).mul(body.upright());
            frames = SlimeBionicAnimator.resolve(body.entity(), body.rig(), view == View.DISPLAY ? 0 : pt);
        }
        List<PlacedCube> parts = new ArrayList<>();
        for (int index = 0; index < body.sources().size(); index++) {
            var frame = frames.size() > index ? frames.get(index) : SlimeBionicAnimator.Frame.EMPTY;
            parts.addAll(body.sources().get(index).parts(root, frame));
        }
        return parts;
    }

    private static Vec3 flightPosition(float time) {
        return TAKEOFF.lerp(CRUISE, eased(time, 85))
            .add(0, Math.sin(time / 8) * 0.08, 0);
    }

    private static float eased(float time, int duration) {
        float t = Mth.clamp(time / duration, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record DonorCube(SourcePose source, int cube, Matrix4f frame, Vec3 intact,
                             SurgicalCubeRotation intactRotation, Vec3 loose, Vec3 assembled,
                             SurgicalCubeRotation assembledRotation) {
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

    private record SourcePose(Source saved, Model model, Matrix4f tableFrame, Map<Integer, Vec3> offsets,
                              Map<Integer, SurgicalCubeRotation> rotations) {
        SourcePose(SurgicalAssembly assembly, Source source, Model model) {
            this(source, model, tableFrame(source), packedUprightOffsets(assembly, source),
                packedUprightRotations(assembly, source));
        }

        private static Matrix4f tableFrame(Source source) {
            PoseStack pose = new PoseStack();
            Vec3 position = source.originOffset();
            pose.translate(position.x, position.y, position.z);
            SurgicalTablePoseResolver.apply(pose, source.layPose());
            return new Matrix4f(pose.last().pose());
        }

        PlacedCube place(int cube, Matrix4f frame, Vec3 offset, SurgicalCubeRotation rotation) {
            return new PlacedCube(model, cube, Vec3.ZERO, false, offset, rotation, frame);
        }

        PlacedCube restPart(int cube, Matrix4f frame) {
            return place(cube, frame, offsets.getOrDefault(cube, Vec3.ZERO),
                rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY));
        }

        List<PlacedCube> parts(Matrix4f root, SlimeBionicAnimator.Frame animation) {
            Matrix4f frame = new Matrix4f(root).mul(tableFrame);
            var placedOffsets = animation.offsets().isEmpty() ? offsets : animation.offsets();
            var placedRotations = animation.rotations().isEmpty() ? rotations : animation.rotations();
            return saved.presentCubes().stream().mapToObj(cube -> place(cube, frame,
                placedOffsets.getOrDefault(cube, Vec3.ZERO),
                placedRotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY))).toList();
        }

        SlimeBionicAnimator.SourceState measure(Matrix4f upright) {
            Matrix4f frame = new Matrix4f(upright).mul(tableFrame);
            var geometry = saved.presentCubes().stream().mapToObj(cube ->
                model.cube(cube).withCorners(restPart(cube, frame).worldCorners())).toList();
            var snapshot = new SurgicalModelRenderContext.Snapshot(model.count(), geometry);
            return new SlimeBionicAnimator.SourceState(SlimeBionicAnimator.measure(snapshot), offsets, rotations);
        }
    }

    private record Body(List<SourcePose> sources, SlimeBionicEntity entity, SlimeBionicAnimator.Rig rig,
                        Matrix4f upright, Vec3 origin) {}
}
