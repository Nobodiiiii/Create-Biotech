package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPackedPreviewService.packedUprightOffsets;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPackedPreviewService.packedUprightRotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Source;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.Model;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderActors.PlacedCube;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

record SurgicalPonderGreenprint(SurgicalAssembly assembly, List<SourcePose> sources) {
    static SurgicalPonderGreenprint load(Level level, String name) {
        var location = CreateBiotech.asResource("ponder/greenprints/" + name + ".nbt");
        try (var file = Minecraft.getInstance().getResourceManager().open(location)) {
            var snapshot = NbtIo.readCompressed(file, NbtAccounter.create(1_048_576));
            var assembly = SurgicalAssembly.load(snapshot.getCompound("MimicEntity").getCompound("SurgicalAssembly"));
            if (assembly == null || !assembly.isReadyForEntity())
                throw new IllegalStateException("Invalid surgical table Ponder greenprint: " + location);
            var sources = assembly.sources().stream().map(source -> {
                Model model = new Model(Objects.requireNonNull(source.profile().createPreviewEntity(level)), Vec3.ZERO);
                if (model.count() != source.cubeCount())
                    throw new IllegalStateException("Donor model does not match the surgical table greenprint: "
                        + source.profile().entityTypeId());
                return new SourcePose(assembly, source, model);
            }).toList();
            return new SurgicalPonderGreenprint(assembly, sources);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read surgical table Ponder greenprint: " + location, error);
        }
    }

    static Matrix4f upright(SurgicalAssembly assembly) {
        PoseStack pose = new PoseStack();
        SurgicalTablePoseResolver.applyInverseRotation(pose, assembly.layoutLayPose());
        return new Matrix4f(pose.last().pose());
    }

    record SourcePose(Source saved, Model model, Matrix4f tableFrame, Map<Integer, Vec3> offsets,
                      Map<Integer, SurgicalCubeRotation> rotations) {
        SourcePose(SurgicalAssembly assembly, Source source, Model model) {
            this(source, model, tableFrame(assembly, source), packedUprightOffsets(assembly, source),
                packedUprightRotations(assembly, source));
        }

        private static Matrix4f tableFrame(SurgicalAssembly assembly, Source source) {
            PoseStack pose = new PoseStack();
            Vec3 position = source.originOffset();
            pose.translate(position.x, position.y, position.z);
            if (assembly.preservesLayout()) SurgicalTablePoseResolver.apply(pose, source.layPose());
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
            return saved.presentCubes().stream().mapToObj(cube -> place(cube, frame,
                animation.offsets().getOrDefault(cube, offsets.getOrDefault(cube, Vec3.ZERO)),
                animation.rotations().getOrDefault(cube, rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY))))
                .toList();
        }

        SlimeBionicAnimator.SourceState measure(Matrix4f upright) {
            Matrix4f frame = new Matrix4f(upright).mul(tableFrame);
            var geometry = saved.presentCubes().stream().mapToObj(cube ->
                model.cube(cube).withCorners(restPart(cube, frame).worldCorners())).toList();
            var snapshot = new SurgicalModelRenderContext.Snapshot(model.count(), geometry);
            return new SlimeBionicAnimator.SourceState(SlimeBionicAnimator.measure(snapshot), offsets, rotations);
        }
    }
}
