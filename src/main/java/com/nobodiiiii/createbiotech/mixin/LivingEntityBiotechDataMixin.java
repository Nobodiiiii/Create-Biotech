package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.buttercat.ButterRotationAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.foundation.entity.LivingEntitySyncedState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

/** One versioned data item for all Biotech state that must be synchronized on every living-entity type. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBiotechDataMixin implements SlimeMimicAccess, ButterRotationAccess {

	@Unique
	private static final EntityDataAccessor<Long> CREATE_BIOTECH$SYNCED_STATE = SynchedEntityData.defineId(
		LivingEntity.class, EntityDataSerializers.LONG);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void createBiotech$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(CREATE_BIOTECH$SYNCED_STATE, LivingEntitySyncedState.empty());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void createBiotech$saveSlimeMimicData(CompoundTag tag, CallbackInfo ci) {
		if (createBiotech$isSlimeMimic())
			tag.putBoolean(SlimeMimicHandler.SLIME_MIMIC_TAG, true);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void createBiotech$readSlimeMimicData(CompoundTag tag, CallbackInfo ci) {
		createBiotech$setSlimeMimic(tag.contains(SlimeMimicHandler.SLIME_MIMIC_TAG, Tag.TAG_BYTE)
			&& tag.getBoolean(SlimeMimicHandler.SLIME_MIMIC_TAG));
	}

	@Override
	public boolean createBiotech$isSlimeMimic() {
		return LivingEntitySyncedState.isSlimeMimic(createBiotech$getSyncedState());
	}

	@Override
	public void createBiotech$setSlimeMimic(boolean slimeMimic) {
		createBiotech$setSyncedState(
			LivingEntitySyncedState.withSlimeMimic(createBiotech$getSyncedState(), slimeMimic));
	}

	@Override
	public int createBiotech$getButterRotationAmplifier() {
		return LivingEntitySyncedState.butterRotationAmplifier(createBiotech$getSyncedState());
	}

	@Override
	public float createBiotech$getButterRotationPhase() {
		return LivingEntitySyncedState.butterRotationPhase(createBiotech$getSyncedState());
	}

	@Override
	public long createBiotech$getButterRotationPhaseStartTick() {
		LivingEntity entity = (LivingEntity) (Object) this;
		return LivingEntitySyncedState.butterRotationPhaseStartTick(createBiotech$getSyncedState(),
			entity.level().getGameTime());
	}

	@Override
	public void createBiotech$setButterRotationState(int amplifier, float phase, long phaseStartTick) {
		createBiotech$setSyncedState(LivingEntitySyncedState.withButterRotation(createBiotech$getSyncedState(),
			amplifier, phase, phaseStartTick));
	}

	@Unique
	private long createBiotech$getSyncedState() {
		return ((LivingEntity) (Object) this).getEntityData().get(CREATE_BIOTECH$SYNCED_STATE);
	}

	@Unique
	private void createBiotech$setSyncedState(long state) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.getEntityData().get(CREATE_BIOTECH$SYNCED_STATE) != state)
			entity.getEntityData().set(CREATE_BIOTECH$SYNCED_STATE, state);
	}
}
