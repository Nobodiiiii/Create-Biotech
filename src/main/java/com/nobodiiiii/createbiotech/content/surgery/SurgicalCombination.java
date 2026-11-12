package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** A persistent set of surgical cubes that honey makes behave as one atomic part. */
public final class SurgicalCombination {
	private static final String ID_TAG = "Id";
	private static final String MEMBERS_TAG = "Members";
	private static final String SUBJECT_TAG = "Subject";
	private static final String CUBE_TAG = "Cube";
	private static final Comparator<Member> MEMBER_ORDER = Comparator
		.comparing((Member member) -> member.subjectKey().getMostSignificantBits(), Long::compareUnsigned)
		.thenComparing(member -> member.subjectKey().getLeastSignificantBits(), Long::compareUnsigned)
		.thenComparingInt(Member::cubeId);

	private final UUID id;
	private final List<Member> members;

	private SurgicalCombination(UUID id, List<Member> members) {
		this.id = id;
		this.members = List.copyOf(members);
	}

	@Nullable
	public static SurgicalCombination create(UUID id, Collection<Member> members) {
		if (id == null || members == null || members.size() < 2
			|| members.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		Set<Member> unique = new HashSet<>();
		for (Member member : members)
			if (member == null || !unique.add(member))
				return null;
		List<Member> normalized = new ArrayList<>(unique);
		normalized.sort(MEMBER_ORDER);
		return new SurgicalCombination(id, normalized);
	}

	@Nullable
	public static SurgicalCombination create(Collection<Member> members) {
		return create(UUID.randomUUID(), members);
	}

	public UUID id() {
		return id;
	}

	public List<Member> members() {
		return members;
	}

	public boolean contains(UUID subjectKey, int cubeId) {
		for (Member member : members)
			if (member.cubeId == cubeId && member.subjectKey.equals(subjectKey))
				return true;
		return false;
	}

	public boolean contains(Member member) {
		return members.contains(member);
	}

	public boolean touches(UUID subjectKey) {
		for (Member member : members)
			if (member.subjectKey.equals(subjectKey))
				return true;
		return false;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID(ID_TAG, id);
		ListTag encoded = new ListTag();
		for (Member member : members) {
			CompoundTag entry = new CompoundTag();
			entry.putUUID(SUBJECT_TAG, member.subjectKey);
			entry.putInt(CUBE_TAG, member.cubeId);
			encoded.add(entry);
		}
		tag.put(MEMBERS_TAG, encoded);
		return tag;
	}

	@Nullable
	static SurgicalCombination load(CompoundTag tag) {
		if (!tag.hasUUID(ID_TAG) || !tag.contains(MEMBERS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = tag.getList(MEMBERS_TAG, Tag.TAG_COMPOUND);
		if (encoded.size() < 2 || encoded.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		List<Member> members = new ArrayList<>(encoded.size());
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag entry = encoded.getCompound(index);
			if (!entry.hasUUID(SUBJECT_TAG) || !entry.contains(CUBE_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			int cube = entry.getInt(CUBE_TAG);
			if (cube < 0)
				return null;
			members.add(new Member(entry.getUUID(SUBJECT_TAG), cube));
		}
		return create(tag.getUUID(ID_TAG), members);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SurgicalCombination combination && id.equals(combination.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public record Member(UUID subjectKey, int cubeId) {
		public Member {
			if (subjectKey == null || cubeId < 0)
				throw new IllegalArgumentException("Invalid surgical combination member");
		}
	}
}
