package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBFluids;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class ExperienceFluidHelper {
	private static final ResourceLocation OWN_EXPERIENCE = CreateBiotech.asResource("experience");
	private static final ResourceLocation OWN_FLOWING_EXPERIENCE = CreateBiotech.asResource("flowing_experience");
	private static final ResourceLocation CEI_EXPERIENCE =
		new ResourceLocation("create_enchantment_industry", "experience");
	private static final ResourceLocation CEI_FLOWING_EXPERIENCE =
		new ResourceLocation("create_enchantment_industry", "flowing_experience");

	private ExperienceFluidHelper() {
	}

	public static FluidStack experienceStack(int amount) {
		if (amount <= 0)
			return FluidStack.EMPTY;
		return new FluidStack(CBFluids.EXPERIENCE.get(), amount);
	}

	public static boolean isExperience(FluidStack stack) {
		return !stack.isEmpty() && isExperience(stack.getFluid());
	}

	public static boolean isExperience(Fluid fluid) {
		ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
		return OWN_EXPERIENCE.equals(id)
			|| OWN_FLOWING_EXPERIENCE.equals(id)
			|| CEI_EXPERIENCE.equals(id)
			|| CEI_FLOWING_EXPERIENCE.equals(id);
	}

	public static boolean isPrimaryExperience(FluidStack stack) {
		if (stack.isEmpty())
			return false;
		ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
		return OWN_EXPERIENCE.equals(id) || OWN_FLOWING_EXPERIENCE.equals(id);
	}

	public static int fluidAmountToXp(int amount) {
		return Math.max(0, amount);
	}

	public static int xpToFluidAmount(int xp) {
		return Math.max(0, xp);
	}
}
