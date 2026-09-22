package com.nobodiiiii.createbiotech.content.squidprinter;

import java.util.Optional;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public class SquidPrinterRecipe extends ProcessingRecipe<RecipeWrapper, SquidPrinterRecipe.Params> {

	private static final IRecipeTypeInfo TYPE_INFO = new IRecipeTypeInfo() {
		@Override
		public ResourceLocation getId() {
			return CBRecipeTypes.SQUID_PRINTER_TYPE.getId();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends RecipeSerializer<?>> T getSerializer() {
			return (T) CBRecipeTypes.SQUID_PRINTER_SERIALIZER.get();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <I extends net.minecraft.world.item.crafting.RecipeInput,
			R extends net.minecraft.world.item.crafting.Recipe<I>>
			net.minecraft.world.item.crafting.RecipeType<R> getType() {
			return (net.minecraft.world.item.crafting.RecipeType<R>) CBRecipeTypes.SQUID_PRINTER_TYPE.get();
		}
	};

	private final Optional<Ingredient> templateIngredient;
	private final boolean copyEnchantments;

	public SquidPrinterRecipe(Params params) {
		super(TYPE_INFO, params);
		templateIngredient = params.templateIngredient;
		copyEnchantments = params.copyEnchantments;
	}

	@Override
	public boolean matches(RecipeWrapper inv, Level level) {
		return !ingredients.isEmpty() && ingredients.getFirst().test(inv.getItem(0));
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 1;
	}

	@Override
	protected int getMaxFluidInputCount() {
		return 1;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}

	public Optional<SizedFluidIngredient> getRequiredFluid() {
		return fluidIngredients.isEmpty() ? Optional.empty() : Optional.of(fluidIngredients.getFirst());
	}

	public Optional<Ingredient> getTemplateIngredient() {
		return templateIngredient;
	}

	public boolean copiesEnchantments() {
		return copyEnchantments;
	}

	public boolean matchesTemplate(ItemStack template, Level level) {
		if (!copyEnchantments)
			return templateIngredient.map(ingredient -> ingredient.test(template)).orElseGet(template::isEmpty);
		if (!EnchantmentBookCopyItem.hasCopyableEnchantments(template))
			return false;
		if (templateIngredient.isPresent() && !templateIngredient.get().test(template))
			return false;
		return SquidPrinterEnchantmentRuleRecipe.allows(level.getRecipeManager(), template);
	}

	public int getCostMultiplier(ItemStack template) {
		return copyEnchantments
			? Math.max(1, EnchantmentBookCopyItem.sumCopySourceEnchantmentLevels(template)) : 1;
	}

	public int getRequiredTicks(ItemStack template) {
		return multiplyClamped(Math.max(1, getProcessingDuration()), getCostMultiplier(template));
	}

	public int getRequiredFluidAmount(ItemStack template) {
		return getRequiredFluid()
			.map(fluid -> multiplyClamped(Math.max(1, fluid.amount()), getCostMultiplier(template)))
			.orElse(0);
	}

	private static int multiplyClamped(int value, int multiplier) {
		return (int) Math.min(Integer.MAX_VALUE, (long) value * multiplier);
	}

	public ItemStack rollResult(ItemStack template, RandomSource random) {
		if (getRollableResults().isEmpty())
			return ItemStack.EMPTY;
		ItemStack result = getRollableResults().getFirst().rollOutput(random);
		return copyEnchantments ? EnchantmentBookCopyItem.fromTemplate(template, result) : result;
	}

	public ItemStack createDisplayResult(ItemStack template) {
		if (getRollableResults().isEmpty())
			return ItemStack.EMPTY;
		ItemStack result = getRollableResults().getFirst().getStack();
		return copyEnchantments ? EnchantmentBookCopyItem.fromTemplate(template, result) : result;
	}

	public static class Params extends ProcessingRecipeParams {
		public static final MapCodec<Params> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			codec(Params::new).forGetter(Function.identity()),
			Ingredient.CODEC.optionalFieldOf("template").forGetter(params -> params.templateIngredient),
			Codec.BOOL.optionalFieldOf("copy_enchantments", false).forGetter(params -> params.copyEnchantments)
		).apply(instance, (params, templateIngredient, copyEnchantments) -> {
			params.templateIngredient = templateIngredient;
			params.copyEnchantments = copyEnchantments;
			return params;
		}));
		public static final StreamCodec<RegistryFriendlyByteBuf, Params> STREAM_CODEC = streamCodec(Params::new);

		private Optional<Ingredient> templateIngredient = Optional.empty();
		private boolean copyEnchantments;

		@Override
		protected void encode(RegistryFriendlyByteBuf buffer) {
			super.encode(buffer);
			ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).encode(buffer, templateIngredient);
			ByteBufCodecs.BOOL.encode(buffer, copyEnchantments);
		}

		@Override
		protected void decode(RegistryFriendlyByteBuf buffer) {
			super.decode(buffer);
			templateIngredient = ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
			copyEnchantments = ByteBufCodecs.BOOL.decode(buffer);
		}
	}

	public static class Serializer implements RecipeSerializer<SquidPrinterRecipe> {
		private final MapCodec<SquidPrinterRecipe> codec =
			ProcessingRecipe.codec(SquidPrinterRecipe::new, Params.CODEC);
		private final StreamCodec<RegistryFriendlyByteBuf, SquidPrinterRecipe> streamCodec =
			ProcessingRecipe.streamCodec(SquidPrinterRecipe::new, Params.STREAM_CODEC);

		@Override
		public MapCodec<SquidPrinterRecipe> codec() {
			return codec;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SquidPrinterRecipe> streamCodec() {
			return streamCodec;
		}
	}
}
