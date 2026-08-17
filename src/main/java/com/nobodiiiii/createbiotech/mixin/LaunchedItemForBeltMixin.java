package com.nobodiiiii.createbiotech.mixin;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.foundation.block.CBBeltChainData;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltChainPlacement;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

@Mixin(value = LaunchedItem.ForBelt.class, remap = false)
public abstract class LaunchedItemForBeltMixin implements CBBeltChainData {

	@Unique
	private int[] createBiotech$pulleyOffsets;

	@Override
	@Unique
	public void createBiotech$setPulleyOffsets(int[] offsets) {
		createBiotech$pulleyOffsets = offsets == null ? null : Arrays.copyOf(offsets, offsets.length);
	}

	@Override
	@Unique
	public int[] createBiotech$getPulleyOffsets() {
		return createBiotech$pulleyOffsets == null ? null
			: Arrays.copyOf(createBiotech$pulleyOffsets, createBiotech$pulleyOffsets.length);
	}

	@Inject(method = "serializeNBT", at = @At("RETURN"))
	private void createBiotech$serializeBeltChain(CallbackInfoReturnable<CompoundTag> cir) {
		LaunchedItem.ForBelt belt = (LaunchedItem.ForBelt) (Object) this;
		if (!CBBeltChainPlacement.isPlacementBelt(belt.state) || createBiotech$pulleyOffsets == null)
			return;
		cir.getReturnValue().putIntArray(CBBeltChainPlacement.PULLEY_OFFSETS_TAG, createBiotech$pulleyOffsets);
	}

	@Inject(method = "readNBT", at = @At("TAIL"))
	private void createBiotech$readBeltChain(CompoundTag nbt, HolderGetter<Block> holderGetter, CallbackInfo ci) {
		if (nbt.contains(CBBeltChainPlacement.PULLEY_OFFSETS_TAG))
			createBiotech$pulleyOffsets = nbt.getIntArray(CBBeltChainPlacement.PULLEY_OFFSETS_TAG);
	}

	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void createBiotech$placeBeltChain(Level world, CallbackInfo ci) {
		LaunchedItem.ForBelt belt = (LaunchedItem.ForBelt) (Object) this;
		if (!CBBeltChainPlacement.isPlacementBelt(belt.state))
			return;
		int[] pulleys = createBiotech$pulleyOffsets == null ? new int[0] : createBiotech$pulleyOffsets;
		CasingType[] beltCasings = belt.casings == null ? new CasingType[belt.length] : belt.casings;
		if (belt.casings == null)
			Arrays.fill(beltCasings, CasingType.NONE);
		CBBeltChainPlacement.placeAtomically(world, belt.state,
			CBBeltChainPlacement.positionsFromPayload(belt.state, belt.target, belt.length), pulleys, beltCasings);
		ci.cancel();
	}
}
