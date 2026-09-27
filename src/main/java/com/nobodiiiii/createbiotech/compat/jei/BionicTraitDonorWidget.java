package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.nobodiiiii.createbiotech.client.BionicDonorPreviewCache;
import com.nobodiiiii.createbiotech.client.BionicTraitDonorIndex;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.foundation.render.BakedDonorPreview;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** A single visible row; changing pages never creates or draws off-screen donors. */
final class BionicTraitDonorWidget implements IRecipeWidget, IJeiInputHandler {
	private static final int WIDTH = 170;
	private static final int COLUMNS = 5;
	private static final int ROWS = 1;
	private static final int CELL = 32;
	private static final int GRID_Y = 13;
	private static final int FOOTER_Y = GRID_Y + ROWS * CELL + 2;
	static final int HEIGHT = FOOTER_Y + 10;
	private static final int PAGE_SIZE = COLUMNS * ROWS;
	private final BionicTrait trait;
	private final int y;
	private int page;

	BionicTraitDonorWidget(BionicTrait trait, int y) {
		this.trait = trait;
		this.y = y;
	}

	@Override
	public ScreenPosition getPosition() { return new ScreenPosition(0, y); }
	@Override
	public ScreenRectangle getArea() { return new ScreenRectangle(0, y, WIDTH, HEIGHT); }

	@Override
	public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
		BionicTraitDonorIndex.requestIfNeeded();
		List<EntityType<?>> donors = BionicTraitDonorIndex.donors(trait);
		page = Math.min(page, lastPage(donors));
		var font = Minecraft.getInstance().font;
		graphics.drawString(font, tr("title", donors.size()), 2, 1, 0x303030, false);
		graphics.drawString(font, "<", 144, 1, page > 0 ? 0x303030 : 0xAAAAAA, false);
		graphics.drawString(font, ">", 159, 1, page < lastPage(donors) ? 0x303030 : 0xAAAAAA, false);
		int start = page * PAGE_SIZE;
		int end = Math.min(start + PAGE_SIZE, donors.size());
		for (int index = start; index < end; index++) {
			int cell = index - start;
			int x = 5 + cell % COLUMNS * CELL;
			int top = GRID_Y + cell / COLUMNS * CELL;
			EntityType<?> type = donors.get(index);
			boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= top && mouseY < top + CELL;
			graphics.fill(x, top, x + CELL - 1, top + CELL - 1, hovered ? 0x40739A9A : 0x16000000);
			BakedDonorPreview preview = BionicDonorPreviewCache.getOrSchedule(type);
			if (preview != null)
				preview.draw(graphics, x, top, CELL - 1, CELL - 1);
			else
				graphics.drawString(font, BionicDonorPreviewCache.failed(type) ? "?" : "...", x + 12, top + 11, 0x606060, false);
		}
		if (donors.isEmpty())
			graphics.drawString(font, tr(BionicTraitDonorIndex.complete() ? "empty" : "waiting"), 5, GRID_Y + 11, 0x606060, false);
		Component footer = BionicTraitDonorIndex.complete()
			? tr("page", donors.isEmpty() ? 0 : start + 1, end, donors.size())
			: tr("loading", BionicTraitDonorIndex.checked(), BionicTraitDonorIndex.total());
		graphics.drawString(font, footer, 2, FOOTER_Y, 0x606060, false);
		if (BionicTraitDonorIndex.failures() > 0)
			graphics.drawString(font, "!", 161, FOOTER_Y, 0xA06020, false);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
		if (mouseX < 0 || mouseX >= WIDTH || mouseY < 0 || mouseY >= HEIGHT)
			return;
		if (mouseY >= FOOTER_Y - 1) {
			tooltip.add(tr("scroll"));
			if (BionicTraitDonorIndex.failures() > 0)
				tooltip.add(tr("incomplete", BionicTraitDonorIndex.failures()));
			return;
		}
		int column = (int) (mouseX - 5) / CELL;
		int row = (int) (mouseY - GRID_Y) / CELL;
		if (mouseX < 5 || mouseX >= 165 || mouseY < GRID_Y || row >= ROWS)
			return;
		int index = page * PAGE_SIZE + row * COLUMNS + column;
		List<EntityType<?>> donors = BionicTraitDonorIndex.donors(trait);
		if (index >= donors.size())
			return;
		EntityType<?> type = donors.get(index);
		tooltip.add(type.getDescription());
		tooltip.add(Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).withStyle(ChatFormatting.DARK_GRAY));
		if (BionicDonorPreviewCache.failed(type))
			tooltip.add(tr("preview_unavailable").copy().withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
		if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0
			|| mouseY < 0 || mouseY >= 12 || mouseX < 139 || mouseX >= WIDTH)
			return false;
		int delta = mouseX < 154 ? -1 : 1;
		int target = Math.max(0, Math.min(lastPage(BionicTraitDonorIndex.donors(trait)), page + delta));
		if (target == page)
			return false;
		if (!input.isSimulate())
			page = target;
		return true;
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
