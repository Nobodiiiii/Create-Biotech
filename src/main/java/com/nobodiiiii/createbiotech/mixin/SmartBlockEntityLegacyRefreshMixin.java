package com.nobodiiiii.createbiotech.mixin;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmartBlockEntity.class)
public abstract class SmartBlockEntityLegacyRefreshMixin extends CachedRenderBBBlockEntity {
	@Unique
	private static final String CREATE_BIOTECH_ID_PREFIX = CreateBiotech.MOD_ID + ":";
	@Unique
	private boolean createBiotech$pendingLegacyStateRefresh;

	protected SmartBlockEntityLegacyRefreshMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "read", at = @At("HEAD"), remap = false)
	private void createBiotech$markLegacyRefresh(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
		createBiotech$pendingLegacyStateRefresh = !clientPacket && isLegacyExperienceMigration(tag);
	}

	@Inject(method = "tick", at = @At("TAIL"), remap = false)
	private void createBiotech$refreshLegacyStateOnce(CallbackInfo ci) {
		if (!createBiotech$pendingLegacyStateRefresh || !hasLevel() || level.isClientSide)
			return;

		createBiotech$pendingLegacyStateRefresh = false;
		setChanged();
		level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
		level.getChunkSource()
			.getLightEngine()
			.checkBlock(getBlockPos());
	}

	@Unique
	private static boolean isLegacyExperienceMigration(CompoundTag tag) {
		String id = tag.getString("id");
		if (!id.startsWith(CREATE_BIOTECH_ID_PREFIX))
			return false;
		return switch (id.substring(CREATE_BIOTECH_ID_PREFIX.length())) {
			case "experience_tank", "experience_pipe", "encased_experience_pipe" -> true;
			default -> false;
		};
	}
}
