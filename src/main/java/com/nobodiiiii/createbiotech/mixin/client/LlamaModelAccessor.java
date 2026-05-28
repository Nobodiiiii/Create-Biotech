package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelPart;

@Mixin(LlamaModel.class)
public interface LlamaModelAccessor {

	@Accessor("head")
	ModelPart createBiotech$getHead();

	@Accessor("body")
	ModelPart createBiotech$getBody();

	@Accessor("rightChest")
	ModelPart createBiotech$getRightChest();

	@Accessor("leftChest")
	ModelPart createBiotech$getLeftChest();

	@Accessor("rightHindLeg")
	ModelPart createBiotech$getRightHindLeg();

	@Accessor("leftHindLeg")
	ModelPart createBiotech$getLeftHindLeg();

	@Accessor("rightFrontLeg")
	ModelPart createBiotech$getRightFrontLeg();

	@Accessor("leftFrontLeg")
	ModelPart createBiotech$getLeftFrontLeg();
}
