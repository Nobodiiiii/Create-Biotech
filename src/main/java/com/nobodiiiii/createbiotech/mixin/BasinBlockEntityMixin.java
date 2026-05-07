package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinCapturedSlimeItemHandler;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

@Mixin(BasinBlockEntity.class)
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
}
