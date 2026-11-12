package com.nobodiiiii.createbiotech.content.surgery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Keeps large table snapshots below the vanilla block-entity packet's 2 MiB NBT budget. */
final class SurgicalTableSubjectSync {
	static final String SUBJECTS_TAG = "SurgicalSubjects";
	private static final String COMPRESSED_SUBJECTS_TAG = "CompressedSurgicalSubjects";
	private static final int COMPRESSION_THRESHOLD = 256 * 1024;
	private static final int MAX_COMPRESSED_BYTES = 1024 * 1024;
	/** Decoded NBT heap accounting, rather than the compressed stream's byte count. */
	private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

	private SurgicalTableSubjectSync() {}

	static void write(CompoundTag target, CompoundTag snapshot) {
		if (snapshot.sizeInBytes() < COMPRESSION_THRESHOLD) {
			target.remove(COMPRESSED_SUBJECTS_TAG);
			target.put(SUBJECTS_TAG, snapshot.getList(SUBJECTS_TAG, Tag.TAG_COMPOUND));
			if (snapshot.contains(SurgicalStorage.SHARED_TAG, Tag.TAG_COMPOUND))
				target.put(SurgicalStorage.SHARED_TAG, snapshot.getCompound(SurgicalStorage.SHARED_TAG));
			else
				target.remove(SurgicalStorage.SHARED_TAG);
			return;
		}
		if (snapshot.sizeInBytes() > MAX_DECOMPRESSED_BYTES)
			throw new IllegalArgumentException("Surgical table snapshot exceeds decompressed sync budget");
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			NbtIo.writeCompressed(snapshot, output);
			byte[] compressed = output.toByteArray();
			if (compressed.length > MAX_COMPRESSED_BYTES)
				throw new IllegalArgumentException("Compressed surgical table snapshot exceeds sync budget");
			target.remove(SUBJECTS_TAG);
			target.remove(SurgicalStorage.SHARED_TAG);
			target.putByteArray(COMPRESSED_SUBJECTS_TAG, compressed);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to encode surgical table snapshot", exception);
		}
	}

	static CompoundTag read(CompoundTag source) {
		if (!source.contains(COMPRESSED_SUBJECTS_TAG, Tag.TAG_BYTE_ARRAY))
			return source;
		byte[] compressed = source.getByteArray(COMPRESSED_SUBJECTS_TAG);
		if (compressed.length > MAX_COMPRESSED_BYTES)
			throw new IllegalArgumentException("Compressed surgical table snapshot exceeds sync budget");
		try {
			return NbtIo.readCompressed(new ByteArrayInputStream(compressed),
				NbtAccounter.create(MAX_DECOMPRESSED_BYTES));
		} catch (IOException exception) {
			throw new IllegalArgumentException("Unable to decode surgical table snapshot", exception);
		}
	}
}
