package com.nobodiiiii.createbiotech.content.surgery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.BionicRiding;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Bakes the attachment base's upright correction into every retained cube before boxing. */
public record SurgicalPackingAlignment(SurgicalAssembly.CombinationMember carrier,
	SurgicalCubeRotation rotation) {
	public SurgicalPackingAlignment {
		if (carrier == null || carrier.source() < 0 || carrier.source() >= SurgicalAssembly.MAX_SOURCES
			|| carrier.cube() < 0 || carrier.cube() >= SurgicalAssembly.MAX_CUBES || rotation == null)
			throw new IllegalArgumentException("Invalid surgical packing alignment");
	}

	/** The client has measured the original, uncorrected rest pose. */
	@Nullable
	public SurgicalAssembly applyToMeasured(SurgicalAssembly assembly,
		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes) {
		return apply(assembly, cubes, false);
	}

	/** Packed carrier corners are already corrected and translated to the new entity origin. */
	@Nullable
	public SurgicalAssembly applyToPacked(SurgicalAssembly assembly,
		SurgicalAssembly.AttackGeometry geometry) {
		return geometry == null ? null : apply(assembly, geometry.carrierCubes(), true);
	}

	@Nullable
	private SurgicalAssembly apply(SurgicalAssembly assembly,
		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes, boolean aligned) {
		List<Vec3> anchor = cubes.get(carrier);
		if (anchor == null || anchor.size() != 8) return null;
		Vec3 pivot = center(anchor);
		SurgicalCubeRotation inverse = BionicRiding.inverse(rotation);
		SurgicalLayPose layout = assembly.layoutLayPose();
		Vec3 axis = layout.rotate(new Vec3(rotation.x(), rotation.y(), rotation.z()));
		SurgicalCubeRotation tableRotation = new SurgicalCubeRotation(axis.x, axis.y, axis.z, rotation.w());
		SurgicalCubeRotation inverseTable = BionicRiding.inverse(tableRotation);
		var sources = new java.util.ArrayList<SurgicalAssembly.Source>();
		for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++) {
			var source = assembly.sources().get(sourceId);
			Map<Integer, Vec3> offsets = new HashMap<>(source.cubeOffsets());
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>(source.cubeRotations());
			var present = source.presentCubes();
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
				List<Vec3> corners = cubes.get(new SurgicalAssembly.CombinationMember(sourceId, cube));
				if (corners == null || corners.size() != 8) return null;
				Vec3 relative = center(corners).subtract(pivot);
				// Both expressions rotate around the original base centre. Subtracting the
				// carrier centre cancels the recentering performed when baking physical data.
				Vec3 delta = aligned ? relative.subtract(inverse.rotate(relative))
					: rotation.rotate(relative).subtract(relative);
				offsets.put(cube, offsets.getOrDefault(cube, Vec3.ZERO).add(layout.rotate(delta)));
				rotations.put(cube, rotations.getOrDefault(cube, SurgicalCubeRotation.IDENTITY).then(tableRotation));
			}
			var corrected = SurgicalAssembly.Source.create(source.profile(), source.donorId(), source.cubeCount(),
				present, source.headCubes(), source.originalHeadCubes(), source.originalHeadKnown(), source.anatomy(),
				source.seams(), source.cutSeams(), source.cutOrder(), source.facing(), source.layPose(),
				source.originOffset(), offsets, rotations);
			if (corrected == null) return null;
			sources.add(corrected);
		}
		// Glue replay adjustments use table axes too; the contact itself stays cube-local.
		var joints = assembly.joints().stream().map(joint -> joint.replay() == null ? joint
			: new SurgicalAssembly.Joint(joint.firstSource(), joint.firstCube(), joint.secondSource(), joint.secondCube(),
				new SurgicalAssembly.JointReplay(joint.replay().movingSource(), joint.replay().movingCube(),
					new SurgicalGlueTransform(tableRotation.rotate(joint.replay().transform().translation()),
						inverseTable.then(joint.replay().transform().rotation()).then(tableRotation)),
					joint.replay().anchorContact()))).toList();
		SurgicalAssembly result = SurgicalAssembly.createComposite(sources, joints, assembly.combinations(),
			assembly.limbs(), assembly.layoutFacing(), layout);
		return result == null ? null : result.withBlockAttachmentCarrier(carrier);
	}

	private static Vec3 center(List<Vec3> corners) {
		Vec3 result = Vec3.ZERO;
		for (Vec3 point : corners) result = result.add(point);
		return result.scale(1.0d / corners.size());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(carrier.source());
		buffer.writeVarInt(carrier.cube());
		rotation.write(buffer);
	}

	public static SurgicalPackingAlignment read(FriendlyByteBuf buffer) {
		return new SurgicalPackingAlignment(new SurgicalAssembly.CombinationMember(buffer.readVarInt(),
			buffer.readVarInt()), SurgicalCubeRotation.read(buffer));
	}
}
