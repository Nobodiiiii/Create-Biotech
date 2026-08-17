package com.nobodiiiii.createbiotech.mixin.compat.alternatecurrent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.nobodiiiii.createbiotech.content.dingdongchicken.AlternateCurrentNodeAccess;

import net.minecraft.core.BlockPos;

@Pseudo
@Mixin(targets = "alternate.current.wire.Node")
public interface AlternateCurrentNodeMixin extends AlternateCurrentNodeAccess {

	@Override
	@Accessor(value = "pos", remap = false)
	BlockPos createBiotech$getPos();
}
