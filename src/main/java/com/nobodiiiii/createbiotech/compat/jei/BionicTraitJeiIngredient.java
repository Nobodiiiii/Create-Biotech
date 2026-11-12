package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** JEI-only entries: distinct searchable traits, with no obtainable in-game item. */
public final class BionicTraitJeiIngredient implements IIngredientHelper<BionicTrait> {
	public static final IIngredientType<BionicTrait> TYPE = new IIngredientType<>() {
		@Override
		public String getUid() {
			return CreateBiotech.asResource("bionic_trait").toString();
		}

		@Override
		public Class<? extends BionicTrait> getIngredientClass() {
			return BionicTrait.class;
		}
	};
	public static final Codec<BionicTrait> CODEC = ResourceLocation.CODEC.xmap(BionicTrait::reference, BionicTrait::id);

	public static Component name(BionicTrait trait) {
		BionicTrait current = BionicTraitRegistry.clientTrait(trait.id());
		return (current == null ? trait : current).displayName();
	}

	public static ResourceLocation id(BionicTrait trait) {
		return ResourceLocation.fromNamespaceAndPath(trait.id().getNamespace(), "bionic_trait/" + trait.id().getPath());
	}

	@Override
	public IIngredientType<BionicTrait> getIngredientType() {
		return TYPE;
	}

	@Override
	public String getDisplayName(BionicTrait ingredient) {
		return name(ingredient).getString();
	}

	@SuppressWarnings("removal")
	@Override
	public String getUniqueId(BionicTrait ingredient, UidContext context) {
		return id(ingredient).toString();
	}

	@Override
	public Object getUid(BionicTrait ingredient, UidContext context) {
		return id(ingredient);
	}

	@Override
	public Object getGroupingUid(BionicTrait ingredient) {
		return id(ingredient);
	}

	@Override
	public ResourceLocation getResourceLocation(BionicTrait ingredient) {
		return id(ingredient);
	}

	@Override
	public BionicTrait copyIngredient(BionicTrait ingredient) {
		return ingredient;
	}

	@Override
	public String getErrorInfo(@Nullable BionicTrait ingredient) {
		return ingredient == null ? "bionic trait: null" : id(ingredient).toString();
	}

	public static final class Renderer implements IIngredientRenderer<BionicTrait> {
		private final ItemStack icon = new ItemStack(CBItems.SURGERY_GUIDE.get());

		@Override
		public void render(GuiGraphics graphics, BionicTrait ingredient) {
			graphics.renderItem(icon, 0, 0);
		}

		@Override
		public List<Component> getTooltip(BionicTrait ingredient, TooltipFlag flag) {
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(name(ingredient));
			tooltip.add(Component.translatable("create_biotech.jei.traits").withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("create_biotech.jei.trait.open").withStyle(ChatFormatting.GRAY));
			if (flag.isAdvanced())
				tooltip.add(Component.literal(id(ingredient).toString()).withStyle(ChatFormatting.DARK_GRAY));
			return tooltip;
		}
	}
}
