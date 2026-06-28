package com.nobodiiiii.createbiotech.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinCapturedSlimeItemHandler;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

@Mixin(value = BasinBlockEntity.class, priority = 1001)
public abstract class BasinBlockEntityMixin {

	@Shadow(remap = false)
	protected LazyOptional<IItemHandlerModifiable> itemCapability;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void createBiotech$includeCapturedSlimesInItemCapability(BlockEntityType<?> type, BlockPos pos,
		BlockState state, CallbackInfo ci) {
		BasinBlockEntity basin = (BasinBlockEntity) (Object) this;
		itemCapability = LazyOptional.of(() -> new BasinCapturedSlimeItemHandler(basin,
			new CombinedInvWrapper(basin.getInputInventory(), basin.getOutputInventory())));
	}

	@Inject(method = "tick()V", at = @At("TAIL"), remap = false)
	private void createBiotech$materializeCapturedSmallSlimeItems(CallbackInfo ci) {
		CapturedSmallSlimeItem.materializeInBasin((BasinBlockEntity) (Object) this);
	}

	@Inject(method = "acceptOutputs(Ljava/util/List;Ljava/util/List;Z)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$acceptCapturedSmallSlimeOutputs(List<ItemStack> outputItems,
		List<FluidStack> outputFluids, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
		int capturedSlimeCount = 0;
		List<ItemStack> otherItems = new ArrayList<>();
		for (ItemStack stack : outputItems) {
			if (BasinEntityProcessing.isCapturedSmallSlimeItem(stack)) {
				capturedSlimeCount += stack.getCount();
				continue;
			}
			otherItems.add(stack);
		}
		if (capturedSlimeCount == 0)
			return;

		BasinBlockEntity basin = (BasinBlockEntity) (Object) this;
		if (!BasinEntityProcessing.canAcceptCapturedSmallSlimeOutput(basin, capturedSlimeCount)
			|| !basin.acceptOutputs(otherItems, outputFluids, true)) {
			cir.setReturnValue(false);
			return;
		}

		if (!simulate) {
			if (!BasinEntityProcessing.materializeCapturedSmallSlimeOutput(basin, capturedSlimeCount)) {
				cir.setReturnValue(false);
				return;
			}
			if (!basin.acceptOutputs(otherItems, outputFluids, false)) {
				cir.setReturnValue(false);
				return;
			}
		}

		cir.setReturnValue(true);
	}
}
