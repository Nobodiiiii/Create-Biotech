package com.nobodiiiii.createbiotech.content.biocomputing.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

final class ScratchPanelArt {
	private static final ResourceLocation TEXTURE = CreateBiotech.asResource("textures/gui/biocomputing/editor_widgets.png");
	private static final int TEXTURE_WIDTH = 128;
	private static final int TEXTURE_HEIGHT = 512;
	private static final int TILE = 120;
	static final int SYMBOL_SIZE = 12;
	static final int LABEL_COLOR = 0xfff2ecdf;

	private static final Finish BRASS = new Finish(0xffe4b763, 0xfff6d487, 0xffb08a46, 0xff231a12);
	private static final Finish STEEL = new Finish(0xffc6c6c6, 0xffeeefed, 0xff93938f, 0xff5d5e59);
	private static final Finish BUTTON = new Finish(0xff878889, 0xffd5d6d4, 0xff525253, 0xff3b3b3b);
	private static final Finish TAB = new Finish(0xff646465, 0xffa5a4a2, 0xff454344, 0xff201d1b);
	private static final Finish SELECTED_TAB = new Finish(0xff72583a, 0xffd4ad67, 0xff48351f, 0xff271e17);
	private static final Finish SAVE = new Finish(0xff537044, 0xffafb98a, 0xff364c30, 0xff30392a);
	private static final Finish DISABLED = new Finish(0xff656461, 0xff8a8985, 0xff51514f, 0xff474642);

	enum Grain {
		CASE(0), PALETTE(120), CANVAS(240);

		private final int v;

		Grain(int v) { this.v = v; }
	}

	enum Symbol {
		RUN(0, 0xffe4efcb), STOP(1, 0xffedcdc7), SAVE(2), COPY(3), PASTE(4),
		CENTER(5), HELP(6), RELOAD(7), FLAG(8), LOOP(9);

		private final int index;
		private final int color;

		Symbol(int index) { this(index, LABEL_COLOR); }

		Symbol(int index, int color) {
			this.index = index;
			this.color = color;
		}

		void draw(GuiGraphics graphics, int x, int y, int tint) {
			RenderSystem.enableBlend();
			graphics.setColor(FastColor.ARGB32.red(tint) / 255f, FastColor.ARGB32.green(tint) / 255f,
				FastColor.ARGB32.blue(tint) / 255f, 1);
			graphics.blit(TEXTURE, x, y, index % 8 * 16, 368 + index / 8 * 16,
				SYMBOL_SIZE, SYMBOL_SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
			graphics.setColor(1, 1, 1, 1);
			RenderSystem.disableBlend();
		}
	}

	private ScratchPanelArt() {}

	static void panel(GuiGraphics graphics, Rect bounds, int titleHeight, Rect toolbar) {
		graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() + 2, bounds.bottom() + 2, 0x900f0d0b);
		fill(graphics, bounds, 0xff211a13);
		fill(graphics, bounds.inflate(-1), 0xff796347);
		grain(graphics, bounds.inflate(-2), Grain.CASE);
		bevel(graphics, new Rect(bounds.x(), bounds.y(), bounds.width(), titleHeight), BRASS);
		bevel(graphics, toolbar, STEEL);
	}

	static void grain(GuiGraphics graphics, Rect area, Grain grain) {
		for (int y = 0; y < area.height(); y += TILE) {
			int height = Math.min(TILE, area.height() - y);
			for (int x = 0; x < area.width(); x += TILE) {
				int width = Math.min(TILE, area.width() - x);
				graphics.blit(TEXTURE, area.x() + x, area.y() + y, 0, grain.v, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
			}
		}
	}

	static void rim(GuiGraphics graphics, Rect area) {
		fill(graphics, area, 0xff796347);
		graphics.fill(area.x(), area.y(), area.right() - 1, area.bottom() - 1, 0xff201a15);
		fill(graphics, area.inflate(-1), 0xff50443a);
	}

	static void recess(GuiGraphics graphics, Rect area, int color, int lip) {
		fill(graphics, area, lip);
		graphics.fill(area.x(), area.y(), area.right() - 1, area.bottom() - 1, 0xff25241e);
		fill(graphics, area.inflate(-1), color);
	}

	static void rail(GuiGraphics graphics, Rect area) {
		fill(graphics, area, 0xff555450);
		graphics.fill(area.x(), area.y(), area.right(), area.y() + 1, 0xff76726b);
		graphics.fill(area.x(), area.bottom() - 1, area.right(), area.bottom(), 0xff232220);
	}

	static void scrollBar(GuiGraphics graphics, int x, int y, int visible, int total, int scroll) {
		if (visible <= 0 || total <= visible)
			return;
		int thumb = Math.min(visible, Math.max(6, visible * visible / total));
		int offset = (visible - thumb) * scroll / (total - visible);
		recess(graphics, new Rect(x, y, 5, visible), 0xff362d24, 0xff594a3b);
		graphics.fill(x + 1, y + offset, x + 4, y + offset + thumb, 0xff9a8265);
		graphics.fill(x + 1, y + offset, x + 4, y + offset + 1, 0xffc0a689);
	}

	static void fill(GuiGraphics graphics, Rect area, int color) {
		if (area.width() > 0 && area.height() > 0)
			graphics.fill(area.x(), area.y(), area.right(), area.bottom(), color);
	}

	private static void bevel(GuiGraphics graphics, Rect bounds, Finish finish) {
		fill(graphics, bounds, finish.edge());
		fill(graphics, bounds.inflate(-1), finish.light());
		graphics.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() - 1, bounds.bottom() - 1, finish.shade());
		fill(graphics, bounds.inflate(-2), finish.face());
	}

	static final class PanelButton extends Button {
		@Nullable
		private final Symbol symbol;
		private final boolean iconOnly;
		private final int mark;
		boolean selected;

		PanelButton(Rect area, Component label, Symbol symbol, boolean iconOnly, Runnable action) {
			this(area, label, symbol, iconOnly, 0, action);
		}

		PanelButton(Rect area, Component label, int mark, Runnable action) {
			this(area, label, null, false, mark, action);
		}

		private PanelButton(Rect area, Component label, @Nullable Symbol symbol, boolean iconOnly, int mark, Runnable action) {
			super(area.x(), area.y(), area.width(), area.height(), label, ignored -> action.run(), DEFAULT_NARRATION);
			this.symbol = symbol;
			this.iconOnly = iconOnly;
			this.mark = mark;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			Rect area = new Rect(getX(), getY(), getWidth(), getHeight());
			Finish finish;
			if (!active)
				finish = DISABLED;
			else if (selected)
				finish = SELECTED_TAB;
			else if (symbol == null)
				finish = TAB;
			else
				finish = symbol == Symbol.SAVE ? SAVE : BUTTON;
			bevel(graphics, area, finish);
			if (active && isHoveredOrFocused())
				graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), 0xffe0cc9f);

			Font font = Minecraft.getInstance().font;
			boolean hasMark = mark != 0 && area.width() >= 28;
			int iconSpace = symbol != null && !iconOnly ? SYMBOL_SIZE + 3 : 0;
			int markSpace = hasMark ? 11 : 0;
			int center = area.x() + (area.width() + markSpace) / 2;
			String caption = iconOnly ? "" : ScratchCanvas.shorten(font, getMessage().getString(),
				area.width() - 6 - markSpace - iconSpace);
			int x = center - (font.width(caption) + (caption.isEmpty() ? 0 : iconSpace)) / 2;
			int y = area.y() + (area.height() - font.lineHeight) / 2;
			if (hasMark) {
				int markY = area.y() + (area.height() - 5) / 2;
				graphics.fill(area.x() + 5, markY, area.x() + 10, markY + 5, finish.edge());
				graphics.fill(area.x() + 6, markY + 1, area.x() + 9, markY + 4, mark);
			}
			if (symbol != null) {
				int iconX = caption.isEmpty() ? center - SYMBOL_SIZE / 2 : x;
				int iconY = area.y() + (area.height() - SYMBOL_SIZE) / 2;
				symbol.draw(graphics, iconX + 1, iconY + 1, finish.shade());
				symbol.draw(graphics, iconX, iconY, active ? symbol.color : 0xffb2afa6);
			}
			if (!caption.isEmpty())
				graphics.drawString(font, caption, x + iconSpace, y, active ? LABEL_COLOR : 0xffc5c0b5, true);
		}
	}

	private record Finish(int face, int light, int shade, int edge) {}
}
