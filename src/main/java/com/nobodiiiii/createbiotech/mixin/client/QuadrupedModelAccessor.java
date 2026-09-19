package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;

@Mixin(QuadrupedModel.class)
public interface QuadrupedModelAccessor {

	@Accessor("rightFrontLeg")
	ModelPart createBiotech$getRightFrontLeg();

	@Accessor("leftFrontLeg")
	ModelPart createBiotech$getLeftFrontLeg();
}
