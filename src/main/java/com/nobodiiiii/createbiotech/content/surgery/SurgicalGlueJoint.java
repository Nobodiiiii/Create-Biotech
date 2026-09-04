package com.nobodiiiii.createbiotech.content.surgery;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** A persistent glue connection between two source-model cubes on a surgical table. */
public record SurgicalGlueJoint(Endpoint first, Endpoint second, @Nullable Replay replay) {
	private static final String FIRST_SUBJECT_TAG = "FirstSubject";
	private static final String FIRST_CUBE_TAG = "FirstCube";
	private static final String SECOND_SUBJECT_TAG = "SecondSubject";
	private static final String SECOND_CUBE_TAG = "SecondCube";
	private static final String REPLAY_TAG = "GlueReplay";
	private static final String MOVING_SUBJECT_TAG = "MovingSubject";
	private static final String MOVING_CUBE_TAG = "MovingCube";
	private static final String TRANSFORM_TAG = "Transform";
	private static final String ANCHOR_CONTACT_TAG = "AnchorContact";

	public SurgicalGlueJoint {
		if (first == null || second == null || first.equals(second))
			throw new IllegalArgumentException("A surgical glue joint requires two endpoints");
		if (replay != null && !replay.moving.equals(first) && !replay.moving.equals(second))
			throw new IllegalArgumentException("A surgical glue replay must belong to one joint endpoint");
	}

	public SurgicalGlueJoint(Endpoint first, Endpoint second) {
		this(first, second, null);
	}

	public static SurgicalGlueJoint of(Endpoint first, Endpoint second) {
		return of(first, second, null);
	}

	public static SurgicalGlueJoint attached(Endpoint moving, Endpoint anchor,
		SurgicalGlueTransform transform, SurgicalGlueContact anchorContact) {
		return of(moving, anchor, new Replay(moving,
			transform == null ? SurgicalGlueTransform.IDENTITY : transform, anchorContact));
	}

	public static SurgicalGlueJoint of(Endpoint first, Endpoint second, @Nullable Replay replay) {
		return compare(first, second) <= 0 ? new SurgicalGlueJoint(first, second, replay)
			: new SurgicalGlueJoint(second, first, replay);
	}

	public boolean touches(UUID subjectKey, int cubeId) {
		return first.matches(subjectKey, cubeId) || second.matches(subjectKey, cubeId);
	}

	public boolean touches(UUID subjectKey) {
		return first.subjectKey.equals(subjectKey) || second.subjectKey.equals(subjectKey);
	}

	@Nullable
	public Endpoint other(Endpoint endpoint) {
		if (first.equals(endpoint))
			return second;
		if (second.equals(endpoint))
			return first;
		return null;
	}

	/** Returns the recorded third-stage edit only when this is the side originally moved onto the anchor. */
	@Nullable
	public SurgicalGlueTransform replayFrom(Endpoint moving) {
		return replay != null && replay.moving.equals(moving) ? replay.transform : null;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID(FIRST_SUBJECT_TAG, first.subjectKey);
		tag.putInt(FIRST_CUBE_TAG, first.cubeId);
		tag.putUUID(SECOND_SUBJECT_TAG, second.subjectKey);
		tag.putInt(SECOND_CUBE_TAG, second.cubeId);
		if (replay != null) {
			CompoundTag encodedReplay = new CompoundTag();
			encodedReplay.putUUID(MOVING_SUBJECT_TAG, replay.moving.subjectKey);
			encodedReplay.putInt(MOVING_CUBE_TAG, replay.moving.cubeId);
			encodedReplay.put(TRANSFORM_TAG, replay.transform.save());
			encodedReplay.put(ANCHOR_CONTACT_TAG, replay.anchorContact.save());
			tag.put(REPLAY_TAG, encodedReplay);
		}
		return tag;
	}

	@Nullable
	static SurgicalGlueJoint load(CompoundTag tag) {
		if (!tag.hasUUID(FIRST_SUBJECT_TAG) || !tag.hasUUID(SECOND_SUBJECT_TAG)
			|| !tag.contains(FIRST_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SECOND_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
			return null;
		Endpoint first = new Endpoint(tag.getUUID(FIRST_SUBJECT_TAG), tag.getInt(FIRST_CUBE_TAG));
		Endpoint second = new Endpoint(tag.getUUID(SECOND_SUBJECT_TAG), tag.getInt(SECOND_CUBE_TAG));
		if (first.equals(second))
			return null;
		Replay replay = null;
		if (tag.contains(REPLAY_TAG)) {
			if (!tag.contains(REPLAY_TAG, Tag.TAG_COMPOUND))
				return null;
			CompoundTag encodedReplay = tag.getCompound(REPLAY_TAG);
			SurgicalGlueTransform transform = encodedReplay.contains(TRANSFORM_TAG, Tag.TAG_COMPOUND)
				? SurgicalGlueTransform.load(encodedReplay.getCompound(TRANSFORM_TAG)) : null;
			SurgicalGlueContact anchorContact = encodedReplay.contains(ANCHOR_CONTACT_TAG, Tag.TAG_COMPOUND)
				? SurgicalGlueContact.load(encodedReplay.getCompound(ANCHOR_CONTACT_TAG)) : null;
			if (!encodedReplay.hasUUID(MOVING_SUBJECT_TAG)
				|| !encodedReplay.contains(MOVING_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
				|| transform == null || anchorContact == null)
				return null;
			try {
				Replay candidate = new Replay(new Endpoint(encodedReplay.getUUID(MOVING_SUBJECT_TAG),
					encodedReplay.getInt(MOVING_CUBE_TAG)), transform, anchorContact);
				if (!candidate.moving.equals(first) && !candidate.moving.equals(second))
					return null;
				replay = candidate;
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}
		try {
			return of(first, second, replay);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static int compare(Endpoint first, Endpoint second) {
		int most = Long.compareUnsigned(first.subjectKey.getMostSignificantBits(),
			second.subjectKey.getMostSignificantBits());
		if (most != 0)
			return most;
		int least = Long.compareUnsigned(first.subjectKey.getLeastSignificantBits(),
			second.subjectKey.getLeastSignificantBits());
		return least != 0 ? least : Integer.compare(first.cubeId, second.cubeId);
	}

	public record Endpoint(UUID subjectKey, int cubeId) {
		public Endpoint {
			if (subjectKey == null || cubeId < 0)
				throw new IllegalArgumentException("Invalid surgical glue endpoint");
		}

		public boolean matches(UUID expectedSubject, int expectedCube) {
			return subjectKey.equals(expectedSubject) && cubeId == expectedCube;
		}
	}

	public record Replay(Endpoint moving, SurgicalGlueTransform transform,
		SurgicalGlueContact anchorContact) {
		public Replay {
			if (moving == null || transform == null || anchorContact == null)
				throw new IllegalArgumentException("Invalid surgical glue replay");
		}
	}
}
