package com.nobodiiiii.createbiotech.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.foundation.block.CBBeltChainData;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltChainPlacement;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementBlock;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementSegment;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class SchematicannonBlockEntityMixin {

	@Shadow
	public List<LaunchedItem> flyingBlocks;

	@Shadow
	public int blocksPlaced;

	@Shadow
	protected abstract void launchBlock(BlockPos target, ItemStack stack, BlockState state, CompoundTag data);

	@Shadow
	public abstract void playFiringSound();

	@Inject(method = "shouldIgnoreBlockState", at = @At("HEAD"), cancellable = true)
	private void createBiotech$ignorePayloadSegments(BlockState state, BlockEntity blockEntity,
		CallbackInfoReturnable<Boolean> cir) {
		if (!(state.getBlock() instanceof CBBeltPlacementBlock belt))
			return;
		BeltPart part = state.getValue(belt.createBiotech$partProperty());
		if (part == BeltPart.MIDDLE)
			cir.setReturnValue(true);
	}

	@Inject(method = "launchBlockOrBelt", at = @At("HEAD"), cancellable = true)
	private void createBiotech$launchBeltChain(BlockPos target, ItemStack icon, BlockState state,
		BlockEntity blockEntity, CallbackInfo ci) {
		if (!(state.getBlock() instanceof CBBeltPlacementBlock belt))
			return;

		BeltPart part = state.getValue(belt.createBiotech$partProperty());
		if (part == BeltPart.MIDDLE) {
			ci.cancel();
			return;
		}
		if (!CBBeltChainPlacement.isLastEndpoint(state)) {
			launchBlock(target, icon, CBBeltChainPlacement.shaftState(state), null);
			ci.cancel();
			return;
		}
		if (!(blockEntity instanceof CBBeltPlacementSegment segment) || blockEntity.getLevel() == null) {
			ci.cancel();
			return;
		}

		CBBeltChainPlacement.Payload payload = CBBeltChainPlacement.collectPayload(blockEntity.getLevel(), target,
			state, segment);
		if (payload == null) {
			ci.cancel();
			return;
		}

		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) (Object) this;
		LaunchedItem.ForBelt launched = new LaunchedItem.ForBelt(cannon.getBlockPos(), target,
			belt.createBiotech$connectorStack(), state, payload.casings());
		((CBBeltChainData) launched).createBiotech$setPulleyOffsets(payload.pulleyOffsets());
		flyingBlocks.add(launched);
		blocksPlaced++;
		playFiringSound();
		ci.cancel();
	}
}
