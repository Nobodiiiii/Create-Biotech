package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;
import java.util.Optional;

import com.nobodiiiii.createbiotech.client.BionicTraitDonorIndex;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** Inventory-sized creature previews with the captured slime item's GUI transform. */
final class BionicTraitDonorWidget implements ISlottedRecipeWidget, IJeiInputHandler {
	private static final int WIDTH = 170;
	private static final int COLUMNS = 9;
	private static final int CELL = 18;
	private static final int GRID_X = (WIDTH - COLUMNS * CELL) / 2;
	private static final int GRID_Y = 13;
	static final int HEIGHT = GRID_Y + CELL;
	static final int PAGE_SIZE = COLUMNS;
	private final BionicTrait trait;
	private final int y;
	private final List<IRecipeSlotDrawable> slots;
	private final IDrawable slotBackground;
	private final EntityType<?>[] displayedTypes = new EntityType<?>[PAGE_SIZE];
	private final ItemStack[] displayedItems = new ItemStack[PAGE_SIZE];
	private int page;
	private int visibleCount;

	BionicTraitDonorWidget(BionicTrait trait, int y, List<IRecipeSlotDrawable> slots, IDrawable slotBackground) {
		this.trait = trait;
		this.y = y;
		this.slots = List.copyOf(slots);
		this.slotBackground = slotBackground;
		for (int cell = 0; cell < PAGE_SIZE; cell++) {
			displayedItems[cell] = ItemStack.EMPTY;
			this.slots.get(cell).setPosition(GRID_X + cell * CELL + 1, GRID_Y + 1);
		}
	}

	@Override
	public ScreenPosition getPosition() { return new ScreenPosition(0, y); }
	@Override
	public ScreenRectangle getArea() { return new ScreenRectangle(0, y, WIDTH, HEIGHT); }

	@Override
	public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
		BionicTraitDonorIndex.requestIfNeeded();
		List<EntityType<?>> donors = BionicTraitDonorIndex.donors(trait);
		updateSlots(donors);
		var font = Minecraft.getInstance().font;
		graphics.drawString(font, tr("title"), 2, 1, 0x303030, false);
		int start = page * PAGE_SIZE;
		int end = Math.min(start + PAGE_SIZE, donors.size());
		Component counter = tr("page", donors.isEmpty() ? 0 : start + 1, end, donors.size());
		int counterRight = BionicTraitDonorIndex.failures() > 0 ? WIDTH - 12 : WIDTH - 2;
		graphics.drawString(font, counter, counterRight - font.width(counter), 1, 0x606060, false);
		if (BionicTraitDonorIndex.failures() > 0)
			graphics.drawString(font, "!", WIDTH - 9, 1, 0xA06020, false);
		if (donors.isEmpty()) {
			graphics.drawString(font, tr(BionicTraitDonorIndex.complete() ? "empty" : "waiting"),
				GRID_X, GRID_Y + (CELL - font.lineHeight) / 2, 0x606060, false);
			return;
		}
		for (int cell = 0; cell < COLUMNS; cell++) {
			int x = GRID_X + cell * CELL;
			int top = GRID_Y;
			slotBackground.draw(graphics, x, top);
			if (cell >= visibleCount)
				continue;
			graphics.renderItem(displayedItems[cell], x + 1, top + 1);
			if (slots.get(cell).isMouseOver(mouseX, mouseY))
				slots.get(cell).drawHighlight(graphics, 0x80FFFFFF);
		}
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
		if (mouseX < 0 || mouseX >= WIDTH || mouseY < 0 || mouseY >= HEIGHT)
			return;
		if (mouseY < GRID_Y) {
			tooltip.add(tr("scroll"));
			if (!BionicTraitDonorIndex.complete())
				tooltip.add(tr("loading", BionicTraitDonorIndex.checked(), BionicTraitDonorIndex.total()));
			if (BionicTraitDonorIndex.failures() > 0)
				tooltip.add(tr("incomplete", BionicTraitDonorIndex.failures()));
		}
	}

	@Override
	public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
		updateSlots(BionicTraitDonorIndex.donors(trait));
		for (int cell = 0; cell < visibleCount; cell++) {
			IRecipeSlotDrawable slot = slots.get(cell);
			if (slot.isMouseOver(mouseX, mouseY))
				return Optional.of(new RecipeSlotUnderMouse(slot, getPosition()));
		}
		return Optional.empty();
	}

	private void updateSlots(List<EntityType<?>> donors) {
		page = Math.min(page, lastPage(donors));
		int start = page * PAGE_SIZE;
		visibleCount = Math.min(PAGE_SIZE, donors.size() - start);
		for (int cell = 0; cell < PAGE_SIZE; cell++) {
			EntityType<?> type = cell < visibleCount ? donors.get(start + cell) : null;
			if (displayedTypes[cell] != type) {
				displayedTypes[cell] = type;
				displayedItems[cell] = type == null ? ItemStack.EMPTY
					: CBItems.BIONIC_DONOR_PREVIEW.get().createStack(type);
			}
			IRecipeSlotDrawable slot = slots.get(cell);
			ItemStack stack = displayedItems[cell];
			if (!ItemStack.isSameItemSameComponents(slot.getDisplayedItemStack().orElse(ItemStack.EMPTY), stack)) {
				slot.clearDisplayOverrides();
				if (!stack.isEmpty())
					slot.createDisplayOverrides().addItemStack(stack);
			}
		}
	}

	@Override
	public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0)
			return false;
		page = Math.max(0, Math.min(lastPage(BionicTraitDonorIndex.donors(trait)), page + (scrollY > 0 ? -1 : 1)));
		return true;
	}

	private static int lastPage(List<?> donors) { return Math.max(0, (donors.size() - 1) / PAGE_SIZE); }
	private static Component tr(String key, Object... args) {
		return Component.translatable("create_biotech.jei.trait.donors." + key, args);
	}
}
