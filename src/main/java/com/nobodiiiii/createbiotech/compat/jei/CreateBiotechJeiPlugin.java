package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;
import java.util.Objects;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgradeRecipe;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {
	private static final RecipeType<AbstractCrushingRecipe> CREATE_CRUSHING =
		new RecipeType<>(Create.asResource("crushing"), AbstractCrushingRecipe.class);

	@Override
	public ResourceLocation getPluginUid() {
		return CreateBiotech.asResource("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SlimeTransformationJeiCategory());
		registration.addRecipeCategories(new BiologicalItemApplicationJeiCategory());
		registration.addRecipeCategories(new CreeperBlastChamberHighPressureJeiCategory());
		registration.addRecipeCategories(new SquidPrinterJeiCategory());
		registration.addRecipeCategories(new EvokerEnchantingChamberJeiCategory());
	}

	@Override
	public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
		registration.getSmithingCategory()
			.addExtension(SonicDogCannonUpgradeRecipe.class, new SonicDogCannonUpgradeJeiExtension());
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addIngredientInfo(
			new FluidStack(CBFluids.TELEPORTATION.get(), FluidType.BUCKET_VOLUME),
			NeoForgeTypes.FLUID_STACK,
			Component.translatable("create_biotech.jei.teleportation.info"));
		registration.addRecipes(SlimeTransformationJeiCategory.TYPE, List.of(
			SlimeTransformationJeiRecipe.beltToSlimeBelt(),
			SlimeTransformationJeiRecipe.beltToMagmaBelt()));
		registration.addRecipes(BiologicalItemApplicationJeiCategory.TYPE,
			BiologicalItemApplicationJeiRecipe.createRecipes());
		registration.addRecipes(CreeperBlastChamberHighPressureJeiCategory.TYPE,
			creeperBlastChamberHighPressureRecipes());
		registration.addRecipes(SquidPrinterJeiCategory.TYPE, SquidPrinterJeiRecipes.create());
		registration.addRecipes(EvokerEnchantingChamberJeiCategory.TYPE, EvokerEnchantingChamberJeiRecipes.create());
		registration.addRecipes(RecipeTypes.ANVIL, cardboardBoxNamingRecipes(registration));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(), CREATE_CRUSHING);
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(),
			CreeperBlastChamberHighPressureJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.SQUID_PRINTER.get()), SquidPrinterJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()),
			EvokerEnchantingChamberJeiCategory.TYPE);
	}

	private static List<RecipeHolder<CreeperBlastChamberHighPressureRecipe>> creeperBlastChamberHighPressureRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();

		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get());
	}

	private static List<IJeiAnvilRecipe> cardboardBoxNamingRecipes(IRecipeRegistration registration) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return List.of();

		List<ItemStack> largeInputs = CBCreativeModeTabs.LARGE_CARDBOARD_BOXES.get()
			.getDisplayItems()
			.stream()
			.filter(CapturedEntityBoxHelper::hasCapturedEntity)
			.map(stack -> stack.copyWithCount(1))
			.toList();
		List<ItemStack> smallInputs = CBConfigs.SERVER.cardboardBox.smallBoxEntityAllowlist.get()
			.stream()
			.map(ResourceLocation::tryParse)
			.filter(Objects::nonNull)
			.distinct()
			.map(BuiltInRegistries.ENTITY_TYPE::getOptional)
			.flatMap(optional -> optional.stream())
			.map(entityType -> CapturedEntityBoxHelper.createFilledBox(CBItems.CARDBOARD_BOX.get(), entityType))
			.toList();

		Component exampleName = Component.translatable("create_biotech.jei.cardboard_box.naming.name");
		ItemStack nameTag = new ItemStack(Items.NAME_TAG);
		nameTag.set(DataComponents.CUSTOM_NAME, exampleName);

		IJeiAnvilRecipe smallRecipe = createCardboardBoxNamingRecipe(registration, level,
			smallInputs, nameTag, exampleName, "small_cardboard_box_naming");
		IJeiAnvilRecipe largeRecipe = createCardboardBoxNamingRecipe(registration, level,
			largeInputs, nameTag, exampleName, "cardboard_box_naming");
		if (smallRecipe == null)
			return largeRecipe == null ? List.of() : List.of(largeRecipe);
		return largeRecipe == null ? List.of(smallRecipe) : List.of(smallRecipe, largeRecipe);
	}

	private static IJeiAnvilRecipe createCardboardBoxNamingRecipe(IRecipeRegistration registration, Level level,
		List<ItemStack> inputs, ItemStack nameTag, Component exampleName, String recipeId) {
		if (inputs.isEmpty())
			return null;

		List<ItemStack> outputs = inputs.stream()
			.map(input -> {
				ItemStack output = input.copy();
				CapturedEntityBoxHelper.applyNameToCapturedEntity(output, level.registryAccess(), exampleName);
				return output;
			})
			.toList();

		return registration.getVanillaRecipeFactory()
			.createAnvilRecipe(inputs, List.of(nameTag), outputs,
				CreateBiotech.asResource(recipeId));
	}
}
