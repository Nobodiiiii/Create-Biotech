package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Stable coordinates for one point on a model cube face. */
public record SurgicalGlueContact(int faceIndex, double u, double v) {
	private static final String FACE_TAG = "Face";
	private static final String U_TAG = "U";
	private static final String V_TAG = "V";
	private static final int FACE_COUNT = 6;
	private static final double EPSILON = 1.0e-6d;

	public SurgicalGlueContact {
		if (faceIndex < 0 || faceIndex >= FACE_COUNT || !finiteUnit(u) || !finiteUnit(v))
			throw new IllegalArgumentException("Invalid surgical glue contact");
		u = clampUnit(u);
		v = clampUnit(v);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeByte(faceIndex);
		buffer.writeDouble(u);
		buffer.writeDouble(v);
	}

	public static SurgicalGlueContact read(FriendlyByteBuf buffer) {
		return new SurgicalGlueContact(buffer.readByte(), buffer.readDouble(), buffer.readDouble());
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putByte(FACE_TAG, (byte) faceIndex);
		tag.putDouble(U_TAG, u);
		tag.putDouble(V_TAG, v);
		return tag;
	}

	@Nullable
	static SurgicalGlueContact load(CompoundTag tag) {
		if (tag == null || !tag.contains(FACE_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(U_TAG, Tag.TAG_ANY_NUMERIC) || !tag.contains(V_TAG, Tag.TAG_ANY_NUMERIC))
			return null;
		try {
			return new SurgicalGlueContact(tag.getInt(FACE_TAG), tag.getDouble(U_TAG), tag.getDouble(V_TAG));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static boolean finiteUnit(double value) {
		return Double.isFinite(value) && value >= -EPSILON && value <= 1.0d + EPSILON;
	}

	private static double clampUnit(double value) {
		return Math.max(0.0d, Math.min(1.0d, value));
	}
}
