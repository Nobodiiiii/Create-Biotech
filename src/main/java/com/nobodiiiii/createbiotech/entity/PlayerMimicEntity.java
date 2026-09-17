package com.nobodiiiii.createbiotech.entity;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A peaceful cultivated creature that keeps the name and skin of one player. */
public class PlayerMimicEntity extends PathfinderMob {
	public static final float WIDTH = 0.6f;
	public static final float HEIGHT = 1.8f;
	private static final String PLAYER_PROFILE_TAG = "ImitatedPlayer";
	private static final EntityDataAccessor<CompoundTag> PLAYER_PROFILE = SynchedEntityData.defineId(
		PlayerMimicEntity.class, EntityDataSerializers.COMPOUND_TAG);

	@Nullable
	private GameProfile cachedPlayerProfile;
	private boolean playerProfileDecoded;

	public PlayerMimicEntity(EntityType<? extends PlayerMimicEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0d)
			.add(Attributes.MOVEMENT_SPEED, 0.1d)
			.add(Attributes.FOLLOW_RANGE, 16.0d);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0d));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PLAYER_PROFILE, new CompoundTag());
	}

	public void setImitatedPlayer(GameProfile profile) {
		CompoundTag encoded = encodePlayerProfile(profile);
		GameProfile decoded = decodePlayerProfile(encoded);
		if (decoded == null)
			return;

		entityData.set(PLAYER_PROFILE, encoded);
		cachedPlayerProfile = decoded;
		playerProfileDecoded = true;
		setCustomName(Component.literal(decoded.getName()));
		setCustomNameVisible(true);
	}

	@Nullable
	public GameProfile getImitatedPlayer() {
		if (!playerProfileDecoded) {
			cachedPlayerProfile = decodePlayerProfile(entityData.get(PLAYER_PROFILE));
			playerProfileDecoded = true;
		}
		return cachedPlayerProfile;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (PLAYER_PROFILE.equals(key)) {
			cachedPlayerProfile = null;
			playerProfileDecoded = false;
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		CompoundTag profile = entityData.get(PLAYER_PROFILE);
		if (!profile.isEmpty())
			tag.put(PLAYER_PROFILE_TAG, profile.copy());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (!tag.contains(PLAYER_PROFILE_TAG, Tag.TAG_COMPOUND))
			return;
		GameProfile profile = decodePlayerProfile(tag.getCompound(PLAYER_PROFILE_TAG));
		if (profile != null)
			setImitatedPlayer(profile);
	}

	public static CompoundTag encodePlayerProfile(GameProfile profile) {
		return ExtraCodecs.GAME_PROFILE.encodeStart(NbtOps.INSTANCE, profile)
			.result()
			.filter(CompoundTag.class::isInstance)
			.map(CompoundTag.class::cast)
			.map(CompoundTag::copy)
			.orElseGet(CompoundTag::new);
	}

	@Nullable
	public static GameProfile decodePlayerProfile(CompoundTag tag) {
		if (tag.isEmpty())
			return null;
		return ExtraCodecs.GAME_PROFILE.parse(NbtOps.INSTANCE, tag).result().orElse(null);
	}
}
