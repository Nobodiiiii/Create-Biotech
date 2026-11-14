package com.nobodiiiii.createbiotech.content.shulkerpackager;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * A shulker packager output with an explicit logical-space identity.
 * <p>
 * Targets in the same logical space as the source use Create's usual relative addressing. This
 * keeps legacy saves compatible and lets a source and target that are assembled together move to
 * plot coordinates without breaking their link. Cross-space targets use their absolute raw plot
 * position plus the target sublevel UUID; a {@code null} UUID explicitly identifies the outer
 * world.
 */
public final class ShulkerPackagerTarget extends ArmInteractionPoint {

	private static final String ABSOLUTE_TARGET_KEY = "CreateBiotechAbsoluteTarget";
	private static final String TARGET_SPACE_KEY = "CreateBiotechTargetSpace";

	private final boolean absoluteTarget;
	@Nullable
	private final UUID targetSubLevelId;
	@Nullable
	private ArmInteractionPoint armPoint;

	private ShulkerPackagerTarget(Level level, BlockPos pos, boolean absoluteTarget,
		@Nullable UUID targetSubLevelId) {
		super(ShulkerPackagerArmInteractions.SHULKER_PACKAGER, level, pos, Blocks.AIR.defaultBlockState());
		this.absoluteTarget = absoluteTarget;
		this.targetSubLevelId = targetSubLevelId;
	}

	@Nullable
	public static ShulkerPackagerTarget selected(Level level, BlockPos pos) {
		if (!SubLevelCompat.isValidSpacePosition(level, pos))
			return null;
		return new ShulkerPackagerTarget(level, pos, true, SubLevelCompat.getSpaceId(level, pos));
	}

	@Nullable
	public static ShulkerPackagerTarget fromTag(CompoundTag tag, Level level, BlockPos anchor) {
		boolean absoluteTarget = tag.getBoolean(ABSOLUTE_TARGET_KEY);
		BlockPos targetPos = decodePosition(tag, anchor, absoluteTarget);
		if (targetPos == null)
			return null;

		UUID targetSubLevelId = absoluteTarget
			? tag.hasUUID(TARGET_SPACE_KEY) ? tag.getUUID(TARGET_SPACE_KEY) : null
			: SubLevelCompat.getSpaceId(level, anchor);
		return new ShulkerPackagerTarget(level, targetPos, absoluteTarget, targetSubLevelId);
	}

	/** Decodes both the current int-array BlockPos format and pre-1.21 compound positions. */
	@Nullable
	static BlockPos decodePosition(CompoundTag tag, BlockPos anchor, boolean absoluteTarget) {
		BlockPos storedPos = NbtUtils.readBlockPos(tag, "Pos")
			.orElseGet(() -> readLegacyPosition(tag));
		if (storedPos == null)
			return null;
		return absoluteTarget ? storedPos : storedPos.offset(anchor);
	}

	@Nullable
	private static BlockPos readLegacyPosition(CompoundTag tag) {
		if (!tag.contains("Pos", Tag.TAG_COMPOUND))
			return null;
		CompoundTag pos = tag.getCompound("Pos");
		if (!pos.contains("X", Tag.TAG_ANY_NUMERIC) || !pos.contains("Y", Tag.TAG_ANY_NUMERIC)
			|| !pos.contains("Z", Tag.TAG_ANY_NUMERIC))
			return null;
		return new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z"));
	}

	public ShulkerPackagerTarget forAnchor(@Nullable UUID anchorSubLevelId) {
		boolean sameSpace = Objects.equals(anchorSubLevelId, targetSubLevelId);
		return new ShulkerPackagerTarget(level, pos, !sameSpace, targetSubLevelId);
	}

	@Nullable
	public UUID targetSubLevelId() {
		return targetSubLevelId;
	}

	public Address address() {
		return new Address(pos, targetSubLevelId);
	}

	@Override
	public boolean isValid() {
		return level != null && level.isLoaded(pos)
			&& SubLevelCompat.matchesSpace(level, pos, targetSubLevelId)
			&& ShulkerPackagerArmInteractions.isSelectable(level, pos, level.getBlockState(pos));
	}

	/** Resolve the live output type without changing the persisted logical-space address. */
	@Nullable
	public ArmInteractionPoint getArmPoint() {
		if (!isValid()) {
			armPoint = null;
			return null;
		}
		ArmInteractionPointType outputType = ArmInteractionPointType.getPrimaryType(level, pos, level.getBlockState(pos));
		if (outputType == null) {
			armPoint = null;
			return null;
		}
		if (armPoint == null || armPoint.getType() != outputType)
			armPoint = outputType.createPoint(level, pos, level.getBlockState(pos));
		return armPoint != null && armPoint.isValid() ? armPoint : null;
	}

	@Override
	public void setLevel(Level level) {
		if (this.level != level)
			armPoint = null;
		super.setLevel(level);
	}

	@Override
	protected void serialize(CompoundTag tag, BlockPos anchor) {
		super.serialize(tag, anchor);
		if (!absoluteTarget) {
			tag.remove(ABSOLUTE_TARGET_KEY);
			tag.remove(TARGET_SPACE_KEY);
			return;
		}

		tag.putBoolean(ABSOLUTE_TARGET_KEY, true);
		tag.put("Pos", NbtUtils.writeBlockPos(pos));
		if (targetSubLevelId == null)
			tag.remove(TARGET_SPACE_KEY);
		else
			tag.putUUID(TARGET_SPACE_KEY, targetSubLevelId);
	}

	public record Address(BlockPos pos, @Nullable UUID subLevelId) {}
}
