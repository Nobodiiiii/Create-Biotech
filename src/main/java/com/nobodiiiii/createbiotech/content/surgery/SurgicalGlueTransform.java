package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * The optional third-stage rigid edit applied after two surgical glue points were first aligned.
 *
 * <p>The translation moves the selected cube's centre away from its automatically aligned centre.
 * The rotation is applied around that selected centre. This makes the edit independent of the table's
 * world position and lets a symmetry wand replay it around another, equivalent first endpoint.</p>
 */
public record SurgicalGlueTransform(Vec3 translation, SurgicalCubeRotation rotation) {
	private static final String TRANSLATE_X_TAG = "TranslateX";
	private static final String TRANSLATE_Y_TAG = "TranslateY";
	private static final String TRANSLATE_Z_TAG = "TranslateZ";
	private static final String ROTATION_TAG = "Rotation";
	private static final double MAX_TRANSLATION = SurgicalTablePlane.MAX_TILES + 2.0d;
	private static final double MIN_NORMAL_LENGTH_SQUARED = 1.0e-12d;
	public static final SurgicalGlueTransform IDENTITY =
		new SurgicalGlueTransform(Vec3.ZERO, SurgicalCubeRotation.IDENTITY);

	public SurgicalGlueTransform {
		translation = translation == null ? Vec3.ZERO : translation;
		rotation = rotation == null ? SurgicalCubeRotation.IDENTITY : rotation;
		if (!validTranslation(translation))
			throw new IllegalArgumentException("Invalid surgical glue edit translation");
	}

	public boolean isIdentity() {
		return translation.lengthSqr() <= 1.0e-24d && rotation.isIdentity();
	}

	/** Reflection preserves translation length and rotation angle, even when its plane is client-derived. */
	public boolean mirrorMagnitudeMatches(SurgicalGlueTransform other) {
		if (other == null)
			return false;
		double translationScale = Math.max(1.0d,
			Math.max(translation.lengthSqr(), other.translation.lengthSqr()));
		return Math.abs(translation.lengthSqr() - other.translation.lengthSqr())
			<= 1.0e-8d * translationScale
			&& Math.abs(rotation.w() - other.rotation.w()) <= 1.0e-8d;
	}

	/** Accumulates one editor scroll operation around the selected cube's current centre. */
	public SurgicalGlueTransform then(Vec3 addedTranslation, SurgicalCubeRotation addedRotation) {
		Vec3 nextTranslation = translation.add(addedTranslation == null ? Vec3.ZERO : addedTranslation);
		SurgicalCubeRotation nextRotation = rotation.then(
			addedRotation == null ? SurgicalCubeRotation.IDENTITY : addedRotation);
		return new SurgicalGlueTransform(nextTranslation, nextRotation);
	}

	/** Mirrors this polar translation and axial rotation across a plane through the glue anchor. */
	public SurgicalGlueTransform mirrorAcross(Vec3 planeNormal) {
		if (planeNormal == null || !finite(planeNormal)
			|| planeNormal.lengthSqr() < MIN_NORMAL_LENGTH_SQUARED)
			return this;
		Vec3 normal = planeNormal.normalize();
		Vec3 mirroredTranslation = reflect(translation, normal);
		// Quaternion xyz is an axial vector. Under a reflection S it transforms as det(S) * S(v).
		Vec3 mirroredAxis = reflect(new Vec3(rotation.x(), rotation.y(), rotation.z()), normal).scale(-1.0d);
		return new SurgicalGlueTransform(mirroredTranslation, new SurgicalCubeRotation(
			mirroredAxis.x, mirroredAxis.y, mirroredAxis.z, rotation.w()));
	}

	/** Rotates table-space edit axes with a packed assembly. */
	public SurgicalGlueTransform rotateClockwise(int turns) {
		Vec3 rotatedTranslation = translation;
		for (int turn = Math.floorMod(turns, 4); turn > 0; turn--)
			rotatedTranslation = new Vec3(-rotatedTranslation.z, rotatedTranslation.y, rotatedTranslation.x);
		return new SurgicalGlueTransform(rotatedTranslation, rotation.rotateClockwise(turns));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeDouble(translation.x);
		buffer.writeDouble(translation.y);
		buffer.writeDouble(translation.z);
		rotation.write(buffer);
	}

	public static SurgicalGlueTransform read(FriendlyByteBuf buffer) {
		return new SurgicalGlueTransform(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
			SurgicalCubeRotation.read(buffer));
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(TRANSLATE_X_TAG, translation.x);
		tag.putDouble(TRANSLATE_Y_TAG, translation.y);
		tag.putDouble(TRANSLATE_Z_TAG, translation.z);
		tag.put(ROTATION_TAG, rotation.save());
		return tag;
	}

	@Nullable
	static SurgicalGlueTransform load(CompoundTag tag) {
		if (tag == null || !tag.contains(TRANSLATE_X_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(TRANSLATE_Y_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(TRANSLATE_Z_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(ROTATION_TAG, Tag.TAG_COMPOUND))
			return null;
		SurgicalCubeRotation rotation = SurgicalCubeRotation.load(tag.getCompound(ROTATION_TAG));
		if (rotation == null)
			return null;
		try {
			return new SurgicalGlueTransform(new Vec3(tag.getDouble(TRANSLATE_X_TAG),
				tag.getDouble(TRANSLATE_Y_TAG), tag.getDouble(TRANSLATE_Z_TAG)), rotation);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static Vec3 reflect(Vec3 vector, Vec3 normal) {
		return vector.subtract(normal.scale(2.0d * vector.dot(normal)));
	}

	private static boolean validTranslation(Vec3 value) {
		return finite(value) && Math.abs(value.x) <= MAX_TRANSLATION
			&& Math.abs(value.y) <= MAX_TRANSLATION && Math.abs(value.z) <= MAX_TRANSLATION;
	}

	private static boolean finite(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
			&& Double.isFinite(value.z);
	}
}
