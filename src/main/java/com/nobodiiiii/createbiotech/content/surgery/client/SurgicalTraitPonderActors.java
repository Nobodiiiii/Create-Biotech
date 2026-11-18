package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.FLAT;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.bounds;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.center;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.joml.Matrix4f;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Limb;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.MeasuredBody;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.Model;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.PlacedCube;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.ai.BionicMind;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-owned donors and assembled bodies for the trait chapter. */
public final class SurgicalTraitPonderActors extends PonderElementBase implements PonderSceneElement {
    private static final Vec3 TABLE = new Vec3(3.6, 2 + SurgicalTablePlane.SURFACE_HEIGHT + 0.005, 3.5);
    private static final Vec3 DISPLAY = new Vec3(4.6, 1, 7.2);
    private static final Vec3 BLAST = new Vec3(5.8, 1.45, 7.2);
    private static final Vec3 TAKEOFF = new Vec3(2.1, 1.1, 7.2);
    private static final Vec3 CRUISE = new Vec3(6.5, 2.6, 7.2);
    private static final List<String> LEGS = List.of("left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg");
    private static final int CREEPER = 0;
    private static final int PHANTOM = 1;
    private static final int GOLEM = 2;

    public enum View { HIDDEN, FLIGHT, DIVE, DISPLAY, DONORS, TABLE, PACKED }
    public enum Assembly { COMPLETE, EXTRA_TORSO, ONE_SHOULDER }
    public enum Part { ALL, CREEPER, PHANTOM_BODY, TORSO, EXTRA_TORSO, HEAD, LEFT_WING, RIGHT_WING, LEGS }

    private final PonderLevel level;
    private final List<Model> models;
    private final int torso;
    private final int head;
    private final List<Integer> legs;
    private final int extraTorso;
    private final List<Integer> leftWing;
    private final List<Integer> rightWing;
    private final double phantomFloor;
    private final Map<Integer, Vec3> wingOffsets = new HashMap<>();
    private final Vec3 extraOffset;
    private final Map<Assembly, Body> bodies = new EnumMap<>(Assembly.class);
    private View view = View.HIDDEN;
    private Assembly variant = Assembly.COMPLETE;
    private int age;

    public SurgicalTraitPonderActors(PonderLevel level) {
        this.level = level;
        Model creeper = new Model(Objects.requireNonNull(EntityType.CREEPER.create(level)), new Vec3(2.5, 2, 3.5));
        Model phantom = new Model(Objects.requireNonNull(EntityType.PHANTOM.create(level)), Vec3.ZERO);
        Model golem = new Model(Objects.requireNonNull(EntityType.IRON_GOLEM.create(level)), Vec3.ZERO);
        models = List.of(creeper, phantom, golem);
        torso = creeper.part("body");
        head = creeper.part("head");
        legs = creeper.cubes.stream().filter(cube -> cube.partNames().stream().anyMatch(LEGS::contains))
            .map(SurgicalModelRenderContext.CubeGeometry::cubeId).toList();
        extraTorso = golem.part("body");
        leftWing = List.of(phantom.part("left_wing_base"), phantom.part("left_wing_tip"));
        rightWing = List.of(phantom.part("right_wing_base"), phantom.part("right_wing_tip"));
        phantomFloor = bounds(phantom.cubes.stream().flatMap(cube -> cube.corners().stream()).toList()).minY;
        AABB core = bounds(creeper.cube(torso).corners());
        placeWing(phantom, leftWing, core);
        placeWing(phantom, rightWing, core);
        AABB extra = bounds(golem.cube(extraTorso).corners());
        extraOffset = new Vec3(core.getCenter().x - extra.getCenter().x,
            core.minY - extra.minY, core.maxZ - extra.minZ - 1.0 / 64);
    }

    private void placeWing(Model phantom, List<Integer> wing, AABB core) {
        AABB base = bounds(phantom.cube(wing.getFirst()).corners());
        double side = Math.signum(base.getCenter().x);
        double x = side > 0 ? core.maxX - base.minX - 1.0 / 64 : core.minX - base.maxX + 1.0 / 64;
        Vec3 offset = new Vec3(x, core.maxY - base.maxY, core.getCenter().z - base.getCenter().z);
        for (int cube : wing) wingOffsets.put(cube, offset);
    }

    @Override
    public void reset(PonderScene scene) {
        bodies.clear();
        show(View.HIDDEN, Assembly.COMPLETE);
        setVisible(true);
    }

    public void show(View next, Assembly assembly) {
        view = next;
        variant = assembly;
        age = 0;
        if (view == View.FLIGHT || view == View.DIVE || view == View.DISPLAY) {
            SlimeBionicEntity entity = body().entity();
            entity.tickCount = 0;
            entity.setOnGround(view == View.DISPLAY);
            entity.setYRot(-90);
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = -90;
        }
    }

    @Override
    public void tick(PonderScene scene) {
        age++;
        if (view == View.FLIGHT || view == View.DIVE) body().entity().tickCount = age;
    }

    public BionicTraitSet traits() { return body().entity().getBionicTraits(); }
    public BionicMind mind() { return body().entity().getMind(); }
    public boolean packed() { return view == View.PACKED; }

    public CompoundTag boxedEntity() {
        CompoundTag data = new CompoundTag();
        body().entity().saveWithoutId(data);
        data.putString("id", "create_biotech:slime_bionic");
        return data;
    }

    private Body body() { return bodies.computeIfAbsent(variant, this::createBody); }

    private Body createBody(Assembly version) {
        Model creeper = models.get(CREEPER);
        Model phantom = models.get(PHANTOM);
        BitSet wings = new BitSet();
        wingOffsets.keySet().forEach(wings::set);
        List<SurgicalAssembly.Source> sources = new ArrayList<>(List.of(
            creeper.source(creeper.all(), Map.of(), Map.of()), phantom.source(wings, wingOffsets, Map.of())));
        List<SurgicalAssembly.Joint> connections = new ArrayList<>(List.of(
            new SurgicalAssembly.Joint(CREEPER, torso, PHANTOM, leftWing.getFirst()),
            new SurgicalAssembly.Joint(CREEPER, torso, PHANTOM, rightWing.getFirst())));
        List<Limb> limbs = new ArrayList<>(List.of(new Limb(SurgicalLimbType.NECK, CREEPER, head, CREEPER, torso),
            new Limb(SurgicalLimbType.SHOULDER, PHANTOM, leftWing.getFirst(), CREEPER, torso)));
        if (version != Assembly.ONE_SHOULDER)
            limbs.add(new Limb(SurgicalLimbType.SHOULDER, PHANTOM, rightWing.getFirst(), CREEPER, torso));
        for (int leg : legs) limbs.add(new Limb(SurgicalLimbType.HIP, CREEPER, leg, CREEPER, torso));
        if (version == Assembly.EXTRA_TORSO) {
            BitSet extra = new BitSet();
            extra.set(extraTorso);
            sources.add(models.get(GOLEM).source(extra, Map.of(extraTorso, extraOffset), Map.of()));
            connections.add(new SurgicalAssembly.Joint(CREEPER, torso, GOLEM, extraTorso));
        }
        var assembly = Objects.requireNonNull(SurgicalAssembly.createComposite(sources, connections,
            List.of(wingGroup(leftWing), wingGroup(rightWing)), limbs, Direction.NORTH, SurgicalLayPose.IDENTITY));
        var measured = SurgicalPonderActors.measureBody(assembly, models.subList(0, sources.size()),
            member -> member.source() != PHANTOM);
        SlimeBionicEntity entity = Objects.requireNonNull(CBEntityTypes.SLIME_BIONIC.get().create(level));
        ((SlimeMimicAccess) (Object) entity).createBiotech$setSlimeMimic(false);
        entity.setNoAi(true);
        entity.setSilent(true);
        entity.setAssembly(measured.assembly());
        double floor = Double.POSITIVE_INFINITY;
        for (int index = 0; index < sources.size(); index++) {
            var source = sources.get(index);
            Model model = models.get(index);
            BitSet present = source.presentCubes();
            for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
                Vec3 offset = source.cubeOffsets().getOrDefault(cube, Vec3.ZERO);
                for (Vec3 corner : model.cube(cube).corners())
                    floor = Math.min(floor, FLAT.rotate(corner.add(offset)).y);
            }
        }
        return new Body(measured, entity, TABLE.subtract(0, floor, 0));
    }

    private static SurgicalAssembly.Combination wingGroup(List<Integer> wing) {
        List<CombinationMember> members = wing.stream().map(cube -> new CombinationMember(PHANTOM, cube)).toList();
        return new SurgicalAssembly.Combination(new UUID(0, wing.getFirst() + 1), members);
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

    public List<AABB> highlight(Part selection, float pt) {
        var boxes = parts(pt).stream().filter(part -> matches(part, selection)).map(PlacedCube::worldBounds);
        return selection == Part.ALL ? boxes.reduce(AABB::minmax).stream().toList() : boxes.toList();
    }

    private boolean matches(PlacedCube part, Part selection) {
        int source = models.indexOf(part.model());
        return switch (selection) {
            case ALL -> true;
            case CREEPER -> source == CREEPER;
            case PHANTOM_BODY -> source == PHANTOM && !wingOffsets.containsKey(part.cube());
            case TORSO -> source == CREEPER && part.cube() == torso;
            case EXTRA_TORSO -> source == GOLEM;
            case HEAD -> source == CREEPER && part.cube() == head;
            case LEFT_WING -> source == PHANTOM && leftWing.contains(part.cube());
            case RIGHT_WING -> source == PHANTOM && rightWing.contains(part.cube());
            case LEGS -> source == CREEPER && legs.contains(part.cube());
        };
    }

    private List<PlacedCube> parts(float pt) {
        if (view == View.HIDDEN || view == View.PACKED) return List.of();
        if (view == View.DONORS) return donorParts();
        Body body = body();
        var measured = body.measured();
        boolean flat = view == View.TABLE;
        float time = age + pt;
        Vec3 position = switch (view) {
            case FLIGHT -> TAKEOFF.lerp(CRUISE, Mth.clamp(time / 85, 0, 1))
                .add(0, Math.sin(time / 8) * 0.08, 0);
            case DIVE -> new Vec3(2.8, 2.6, 7.2).lerp(BLAST.add(0, -0.4, 0), Mth.clamp(time / 52, 0, 1));
            case TABLE -> body.tableOrigin();
            default -> DISPLAY;
        };
        Matrix4f root = new Matrix4f();
        List<SlimeBionicAnimator.Frame> frames = List.of();
        if (!flat) {
            Vec3 origin = measured.origin();
            float swelling = view == View.DIVE ? Mth.clamp((time - 22) / 30, 0, 1) : 0;
            root.translation((float) position.x, (float) position.y, (float) position.z)
                .rotateY(Mth.HALF_PI).scale(1 + swelling * swelling * 0.3f)
                .translate((float) -origin.x, (float) -origin.y, (float) -origin.z);
            frames = SlimeBionicAnimator.resolve(body.entity(), measured.rig(), view == View.DISPLAY ? 0 : pt);
        }
        List<PlacedCube> parts = new ArrayList<>();
        for (int index = 0; index < measured.assembly().sources().size(); index++) {
            var source = measured.assembly().sources().get(index);
            var frame = frames.size() > index ? frames.get(index) : SlimeBionicAnimator.Frame.EMPTY;
            var offsets = frame.offsets().isEmpty() ? source.cubeOffsets() : frame.offsets();
            var rotations = frame.rotations().isEmpty() ? source.cubeRotations() : frame.rotations();
            Vec3 cubeOrigin = flat ? position : Vec3.ZERO;
            Model model = models.get(index);
            source.presentCubes().stream().forEach(cube -> parts.add(new PlacedCube(model, cube, cubeOrigin, flat,
                offsets.getOrDefault(cube, Vec3.ZERO), rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY), root)));
        }
        return parts;
    }

    private List<PlacedCube> donorParts() {
        Model creeper = models.get(CREEPER);
        Model phantom = models.get(PHANTOM);
        List<PlacedCube> parts = new ArrayList<>();
        Matrix4f root = new Matrix4f();
        for (int cube = 0; cube < creeper.count(); cube++)
            parts.add(new PlacedCube(creeper, cube, creeper.origin, true, Vec3.ZERO, SurgicalCubeRotation.IDENTITY, root));
        for (int cube = 0; cube < phantom.count(); cube++) {
            double spread = leftWing.contains(cube) || rightWing.contains(cube)
                ? Math.signum(center(phantom.cube(cube)).x) * 0.16 : 0;
            parts.add(new PlacedCube(phantom, cube, new Vec3(5.4 + spread, TABLE.y - phantomFloor, 3.5), false,
                Vec3.ZERO, SurgicalCubeRotation.IDENTITY, root));
        }
        return parts;
    }

    private record Body(MeasuredBody measured, SlimeBionicEntity entity, Vec3 tableOrigin) {}
}
