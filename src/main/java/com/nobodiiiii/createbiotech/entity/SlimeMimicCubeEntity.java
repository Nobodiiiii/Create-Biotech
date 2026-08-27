package com.nobodiiiii.createbiotech.entity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/** One physical cuboid released by a dying slime mimic. */
public class SlimeMimicCubeEntity extends Entity implements IEntityWithComplexSpawn {
	public static final int MORPH_TICKS = 20;
	private static final int MAX_FALL_TICKS = 1200;
	private static final double GRAVITY = 0.04d;
	private static final double AIR_DRAG = 0.98d;
	private static final float MIN_COLLISION_SIZE = 1.0f / 32.0f;
	private static final EntityDataAccessor<Boolean> MORPHING = SynchedEntityData.defineId(
		SlimeMimicCubeEntity.class, EntityDataSerializers.BOOLEAN);
	private static final String PROFILE_TAG = "MimicProfile";
	private static final String CUBE_TAG = "Cube";
	private static final String WIDTH_TAG = "Width";
	private static final String HEIGHT_TAG = "Height";
	private static final String DEPTH_TAG = "Depth";
	private static final String VOLUME_TAG = "Volume";
	private static final String SLIME_SIZE_TAG = "SlimeSize";
	private static final String MORPH_TAG = "MorphTicks";
	private static final String FALL_TAG = "FallTicks";
	private static final String FRAME_TAG = "InitialFrame";

	@Nullable
	private MimicProfile profile;
	private int cube;
	private float initialWidth = 0.5f;
	private float initialHeight = 0.5f;
	private float initialDepth = 0.5f;
	private double volume;
	private int slimeSize;
	private int morphTicks = -1;
	private int fallTicks;
	private CubeFrame initialFrame = axisAlignedFrame(0.5f, 0.5f, 0.5f);

	public SlimeMimicCubeEntity(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Nullable
	public static SlimeMimicCubeEntity create(Level level, MimicProfile profile, int cube,
		Vec3 center, float width, float height, float depth, double volume, int slimeSize,
		List<Vec3> corners) {
		SlimeMimicCubeEntity fragment = CBEntityTypes.SLIME_MIMIC_CUBE.get().create(level);
		if (fragment == null || profile == null || cube < 0 || corners == null || corners.size() != 8)
			return null;
		fragment.profile = profile;
		fragment.cube = cube;
		fragment.initialWidth = sanitizeDimension(width);
		fragment.initialHeight = sanitizeDimension(height);
		fragment.initialDepth = sanitizeDimension(depth);
		fragment.volume = Math.max(0.0d, volume);
		fragment.slimeSize = Mth.clamp(slimeSize, 0, 127);
		fragment.refreshDimensions();
		Vec3 entityOrigin = new Vec3(center.x, center.y - fragment.initialHeight * 0.5d, center.z);
		fragment.setPos(entityOrigin);
		fragment.initialFrame = new CubeFrame(corners.get(0).subtract(entityOrigin),
			corners.get(1).subtract(corners.get(0)), corners.get(2).subtract(corners.get(0)),
			corners.get(4).subtract(corners.get(0)));
		fragment.setDeltaMovement(Vec3.ZERO);
		return fragment;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(MORPHING, false);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.fixed(Math.max(initialWidth, initialDepth), initialHeight);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		float targetSide = slimeSize > 0 ? slimeSize * 0.5f : 0.0f;
		double halfWidth = Math.max(initialWidth, targetSide) * 0.5d;
		double halfDepth = Math.max(initialDepth, targetSide) * 0.5d;
		double height = Math.max(initialHeight, targetSide);
		return new AABB(getX() - halfWidth, getY(), getZ() - halfDepth,
			getX() + halfWidth, getY() + height, getZ() + halfDepth);
	}

	@Override
	public void tick() {
		super.tick();
		if (entityData.get(MORPHING) && morphTicks < 0)
			morphTicks = 0;
		if (profile == null) {
			if (!level().isClientSide)
				discard();
			return;
		}

		if (morphTicks >= 0) {
			setDeltaMovement(Vec3.ZERO);
			morphTicks++;
			if (!level().isClientSide && morphTicks > MORPH_TICKS)
				finishMorph();
			return;
		}

		fallTicks++;
		if (fallTicks > MAX_FALL_TICKS || getY() < level().getMinBuildHeight() - 64) {
			if (!level().isClientSide)
				discard();
			return;
		}

		Vec3 motion = getDeltaMovement();
		if (!isNoGravity())
			motion = motion.add(0.0d, -GRAVITY, 0.0d);
		move(MoverType.SELF, motion);
		if (onGround()) {
			setDeltaMovement(Vec3.ZERO);
			if (!level().isClientSide) {
				morphTicks = 0;
				entityData.set(MORPHING, true);
			}
		} else {
			setDeltaMovement(motion.scale(AIR_DRAG));
		}
	}

	private void finishMorph() {
		if (slimeSize > 0) {
			Slime slime = EntityType.SLIME.create(level());
			if (slime != null) {
				slime.setSize(slimeSize, true);
				slime.moveTo(getX(), getY(), getZ(), random.nextFloat() * 360.0f, 0.0f);
				slime.setDeltaMovement(Vec3.ZERO);
				level().addFreshEntity(slime);
			}
		}
		discard();
	}

	public float morphProgress(float partialTick) {
		return morphTicks < 0 ? 0.0f : Mth.clamp((morphTicks + partialTick) / MORPH_TICKS, 0.0f, 1.0f);
	}

	public float visualWidth(float partialTick) {
		return visualDimension(initialWidth, partialTick);
	}

	public float visualHeight(float partialTick) {
		return visualDimension(initialHeight, partialTick);
	}

	public float visualDepth(float partialTick) {
		return visualDimension(initialDepth, partialTick);
	}

	public CubeFrame visualFrame(float partialTick) {
		float progress = morphProgress(partialTick);
		if (progress <= 0.0f)
			return initialFrame;
		CubeFrame target;
		if (slimeSize > 0) {
			float side = slimeSize * 0.5f;
			target = axisAlignedFrame(side, side, side);
		} else {
			Vec3 center = initialFrame.origin.add(initialFrame.a.add(initialFrame.b).add(initialFrame.c).scale(0.5d));
			target = new CubeFrame(center, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
		}
		return initialFrame.lerp(target, progress);
	}

	private float visualDimension(float initial, float partialTick) {
		float target = slimeSize > 0 ? slimeSize * 0.5f : 0.0f;
		return Mth.lerp(morphProgress(partialTick), initial, target);
	}

	@Nullable
	public MimicProfile profile() {
		return profile;
	}

	public int cube() {
		return cube;
	}

	public double volume() {
		return volume;
	}

	public int slimeSize() {
		return slimeSize;
	}

	public float initialWidth() {
		return initialWidth;
	}

	public float initialHeight() {
		return initialHeight;
	}

	public float initialDepth() {
		return initialDepth;
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeNbt(saveFragmentData());
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buffer) {
		CompoundTag tag = buffer.readNbt();
		if (tag != null)
			loadFragmentData(tag);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		loadFragmentData(tag);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.merge(saveFragmentData());
	}

	private CompoundTag saveFragmentData() {
		CompoundTag tag = new CompoundTag();
		if (profile != null)
			tag.put(PROFILE_TAG, profile.save());
		tag.putInt(CUBE_TAG, cube);
		tag.putFloat(WIDTH_TAG, initialWidth);
		tag.putFloat(HEIGHT_TAG, initialHeight);
		tag.putFloat(DEPTH_TAG, initialDepth);
		tag.putDouble(VOLUME_TAG, volume);
		tag.putInt(SLIME_SIZE_TAG, slimeSize);
		tag.putInt(MORPH_TAG, morphTicks);
		tag.putInt(FALL_TAG, fallTicks);
		tag.put(FRAME_TAG, initialFrame.save());
		return tag;
	}

	private void loadFragmentData(CompoundTag tag) {
		profile = tag.contains(PROFILE_TAG, Tag.TAG_COMPOUND)
			? MimicProfile.load(tag.getCompound(PROFILE_TAG)) : null;
		cube = Math.max(0, tag.getInt(CUBE_TAG));
		initialWidth = sanitizeDimension(tag.getFloat(WIDTH_TAG));
		initialHeight = sanitizeDimension(tag.getFloat(HEIGHT_TAG));
		initialDepth = sanitizeDimension(tag.getFloat(DEPTH_TAG));
		volume = Math.max(0.0d, tag.getDouble(VOLUME_TAG));
		slimeSize = Mth.clamp(tag.getInt(SLIME_SIZE_TAG), 0, 127);
		morphTicks = Math.max(-1, tag.getInt(MORPH_TAG));
		if (morphTicks >= 0)
			entityData.set(MORPHING, true);
		fallTicks = Math.max(0, tag.getInt(FALL_TAG));
		initialFrame = tag.contains(FRAME_TAG, Tag.TAG_COMPOUND)
			? CubeFrame.load(tag.getCompound(FRAME_TAG), axisAlignedFrame(initialWidth, initialHeight, initialDepth))
			: axisAlignedFrame(initialWidth, initialHeight, initialDepth);
		refreshDimensions();
	}

	private static float sanitizeDimension(float value) {
		return Float.isFinite(value) ? Mth.clamp(value, MIN_COLLISION_SIZE, 64.0f) : 0.5f;
	}

	private static CubeFrame axisAlignedFrame(float width, float height, float depth) {
		return new CubeFrame(new Vec3(-width * 0.5d, 0.0d, -depth * 0.5d),
			new Vec3(width, 0.0d, 0.0d), new Vec3(0.0d, height, 0.0d),
			new Vec3(0.0d, 0.0d, depth));
	}

	public record CubeFrame(Vec3 origin, Vec3 a, Vec3 b, Vec3 c) {
		private static final double MAX_COMPONENT = 64.0d;

		public CubeFrame {
			origin = sanitize(origin, Vec3.ZERO);
			a = sanitize(a, new Vec3(0.5d, 0.0d, 0.0d));
			b = sanitize(b, new Vec3(0.0d, 0.5d, 0.0d));
			c = sanitize(c, new Vec3(0.0d, 0.0d, 0.5d));
		}

		private CubeFrame lerp(CubeFrame target, double progress) {
			return new CubeFrame(origin.lerp(target.origin, progress), a.lerp(target.a, progress),
				b.lerp(target.b, progress), c.lerp(target.c, progress));
		}

		private CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			put(tag, "Origin", origin);
			put(tag, "A", a);
			put(tag, "B", b);
			put(tag, "C", c);
			return tag;
		}

		private static CubeFrame load(CompoundTag tag, CubeFrame fallback) {
			return new CubeFrame(get(tag, "Origin", fallback.origin), get(tag, "A", fallback.a),
				get(tag, "B", fallback.b), get(tag, "C", fallback.c));
		}

		private static void put(CompoundTag tag, String prefix, Vec3 value) {
			tag.putDouble(prefix + "X", value.x);
			tag.putDouble(prefix + "Y", value.y);
			tag.putDouble(prefix + "Z", value.z);
		}

		private static Vec3 get(CompoundTag tag, String prefix, Vec3 fallback) {
			if (!tag.contains(prefix + "X", Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(prefix + "Y", Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(prefix + "Z", Tag.TAG_ANY_NUMERIC))
				return fallback;
			return sanitize(new Vec3(tag.getDouble(prefix + "X"), tag.getDouble(prefix + "Y"),
				tag.getDouble(prefix + "Z")), fallback);
		}

		private static Vec3 sanitize(Vec3 value, Vec3 fallback) {
			if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y)
				|| !Double.isFinite(value.z))
				return fallback;
			return new Vec3(Mth.clamp(value.x, -MAX_COMPONENT, MAX_COMPONENT),
				Mth.clamp(value.y, -MAX_COMPONENT, MAX_COMPONENT),
				Mth.clamp(value.z, -MAX_COMPONENT, MAX_COMPONENT));
		}
	}
}
