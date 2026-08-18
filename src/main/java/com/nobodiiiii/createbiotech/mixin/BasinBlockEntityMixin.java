package com.nobodiiiii.createbiotech.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

@Mixin(value = BasinBlockEntity.class, priority = 1001)
public abstract class BasinBlockEntityMixin {
	@Shadow(remap = false)
	protected List<ItemStack> spoutputBuffer;

	@Inject(method = "tick()V", at = @At("TAIL"), remap = false)
	private void createBiotech$materializeCapturedSmallSlimeItems(CallbackInfo ci) {
		CapturedSmallSlimeItem.syncInBasin((BasinBlockEntity) (Object) this);
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
		BlockState blockState = basin.getBlockState();
		if (blockState.getBlock() instanceof BasinBlock
			&& blockState.getValue(BasinBlock.FACING) != Direction.DOWN)
			return;

		List<ItemStack> capturedSlimeItems = List.of(new ItemStack(outputItems.stream()
			.filter(BasinEntityProcessing::isCapturedSmallSlimeItem)
			.findFirst()
			.orElse(ItemStack.EMPTY)
			.getItem(), capturedSlimeCount));
		if (!BasinEntityProcessing.acceptsCapturedSmallSlimeOutput(basin, capturedSlimeItems, true)
			|| !basin.acceptOutputs(otherItems, outputFluids, true)) {
			cir.setReturnValue(false);
			return;
		}

		if (!simulate) {
			if (!BasinEntityProcessing.acceptsCapturedSmallSlimeOutput(basin, capturedSlimeItems, false)) {
				cir.setReturnValue(false);
				return;
			}
			if (!basin.acceptOutputs(otherItems, outputFluids, false)) {
				cir.setReturnValue(false);
				return;
			}
			CapturedSmallSlimeItem.syncInBasin(basin);
		}

		cir.setReturnValue(true);
	}

	@Inject(method = "tryClearingSpoutputOverflow()V", at = @At("HEAD"), remap = false)
	private void createBiotech$materializeCapturedSmallSlimeSpoutput(CallbackInfo ci) {
		BasinBlockEntity basin = (BasinBlockEntity) (Object) this;
		Level level = basin.getLevel();
		if (level == null || level.isClientSide || spoutputBuffer.isEmpty())
			return;

		BlockState blockState = basin.getBlockState();
		if (!(blockState.getBlock() instanceof BasinBlock))
			return;
		Direction direction = blockState.getValue(BasinBlock.FACING);
		if (!direction.getAxis().isHorizontal())
			return;
		if (!BasinBlock.canOutputTo(level, basin.getBlockPos(), direction))
			return;

		Vec3 directionVector = Vec3.atLowerCornerOf(direction.getNormal());
		Vec3 outputPosition = Vec3.atCenterOf(basin.getBlockPos())
			.add(directionVector.scale(.65d))
			.subtract(0, .25d, 0);
		Vec3 outputMotion = directionVector.scale(1 / 16d)
			.add(0, -1 / 16d, 0);
		boolean changed = false;
		for (Iterator<ItemStack> iterator = spoutputBuffer.iterator(); iterator.hasNext();) {
			ItemStack stack = iterator.next();
			if (!BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
				continue;
			if (!CapturedSmallSlimeItem.materializeTransportedStack(level, outputPosition, outputMotion, stack))
				continue;
			iterator.remove();
			changed = true;
		}

		if (changed) {
			basin.notifyChangeOfContents();
			basin.notifyUpdate();
		}
	}
}
