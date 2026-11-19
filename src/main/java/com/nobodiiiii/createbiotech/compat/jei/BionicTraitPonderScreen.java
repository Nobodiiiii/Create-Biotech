package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalPonderMenu;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Native JEI trait layout with the vanilla Fire Resistance donor results bundled for Ponder. */
public final class BionicTraitPonderScreen extends AbstractContainerScreen<SurgicalPonderMenu> {
    private static final List<EntityType<?>> FIRE_RESISTANCE_DONORS = List.of(
        EntityType.BLAZE, EntityType.ENDER_DRAGON, EntityType.GHAST, EntityType.MAGMA_CUBE,
        EntityType.SHULKER, EntityType.STRIDER, EntityType.VEX, EntityType.WARDEN,
        EntityType.WITHER, EntityType.WITHER_SKELETON, EntityType.ZOGLIN, EntityType.ZOMBIFIED_PIGLIN);

    private IRecipeLayoutDrawable<BionicTrait> recipe;

    private BionicTraitPonderScreen(SurgicalPonderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    public static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(CBMenuTypes.PONDER_FIRE_RESISTANCE_JEI.get(), BionicTraitPonderScreen::new);
    }

    @Override
    protected void init() {
        var runtime = CreateBiotechJeiPlugin.traitRuntime();
        var helpers = runtime.getJeiHelpers();
        var category = new BionicTraitJeiCategory(helpers.getGuiHelper(), FIRE_RESISTANCE_DONORS);
        var trait = BionicTraitRegistry.clientTrait(BionicTrait.FIRE_RESISTANCE.id());
        recipe = runtime.getRecipeManager().createRecipeLayoutDrawableOrShowError(category,
            trait == null ? BionicTrait.FIRE_RESISTANCE : trait, helpers.getFocusFactory().getEmptyFocusGroup());
        var area = recipe.getRect();
        var bounds = recipe.getRectWithBorder();
        int insetX = area.getX() - bounds.getX();
        int insetY = area.getY() - bounds.getY();
        imageWidth = bounds.getWidth();
        imageHeight = bounds.getHeight();
        super.init();
        recipe.setPosition(leftPos + insetX, topPos + insetY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recipe.drawRecipe(graphics, mouseX, mouseY);
        recipe.drawOverlays(graphics, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        recipe.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return recipe.getInputHandler().handleMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
