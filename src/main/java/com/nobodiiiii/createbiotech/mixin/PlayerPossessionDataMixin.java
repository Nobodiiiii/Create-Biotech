package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/** Persistent server state plus a compact synchronized rendering view for player possession. */
@Mixin(Player.class)
public abstract class PlayerPossessionDataMixin implements PossessionAccess {
	@Unique
	private static final EntityDataAccessor<CompoundTag> CREATE_BIOTECH$POSSESSION =
		SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
	@Unique
	private CompoundTag createBiotech$possessionState = new CompoundTag();

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void createBiotech$definePossessionData(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(CREATE_BIOTECH$POSSESSION, new CompoundTag());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void createBiotech$savePossession(CompoundTag tag, CallbackInfo ci) {
		if (EchoShardPossession.isActive(createBiotech$possessionState))
			tag.put(EchoShardPossession.PLAYER_STATE_TAG, createBiotech$possessionState.copy());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void createBiotech$loadPossession(CompoundTag tag, CallbackInfo ci) {
		CompoundTag state = tag.contains(EchoShardPossession.PLAYER_STATE_TAG, Tag.TAG_COMPOUND)
			? tag.getCompound(EchoShardPossession.PLAYER_STATE_TAG).copy() : new CompoundTag();
		createBiotech$setPossessionState(state);
	}

	@Override
	public CompoundTag createBiotech$getPossessionState() {
		Player player = (Player) (Object) this;
		return player.level().isClientSide()
			? player.getEntityData().get(CREATE_BIOTECH$POSSESSION)
			: createBiotech$possessionState;
	}

	@Override
	public void createBiotech$setPossessionState(CompoundTag state) {
		Player player = (Player) (Object) this;
		createBiotech$possessionState = state.copy();
		CompoundTag synchronizedState = EchoShardPossession.synchronizedView(createBiotech$possessionState);
		if (!player.getEntityData().get(CREATE_BIOTECH$POSSESSION).equals(synchronizedState))
			player.getEntityData().set(CREATE_BIOTECH$POSSESSION, synchronizedState);
		player.refreshDimensions();
	}

	@Override
	public boolean createBiotech$isPossessionData(EntityDataAccessor<?> key) {
		return CREATE_BIOTECH$POSSESSION.equals(key);
	}
}
