package com.nobodiiiii.createbiotech.content.biocomputing.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Nine-slice rendering of the Scratch Blocks atlas. */
final class ScratchBlockArt {
	private static final int SCALE = 2;
	private static final int STRIDE_X = 88;
	private static final int STRIDE_Y = 48;
	private static final int TEXTURE_WIDTH = 12 * STRIDE_X * SCALE;
	private static final int TEXTURE_HEIGHT = 10 * STRIDE_Y * SCALE;
	private static final ResourceLocation TEXTURE = CreateBiotech.asResource("textures/gui/biocomputing/scratch_blocks.png");

	enum Region {
		HAT(0, 75, 39, 50, 5, 12, 8, 4),
		STACK(1, 84, 30, 26, 5, 6, 7, 4),
		MOUTH(2, 82, 30, 42, 5, 6, 7, 4),
		FOOT(3, 82, 18, 42, 5, 6, 7, 4),
		REPORTER(4, 60, 22, 11, 12, 0, 0, 0),
		BOOLEAN(5, 76, 22, 11, 12, 0, 0, 0),
		RAIL(7, 18, 6, 0, 0, 0, 0, 0),
		CAP(10, 84, 26, 26, 5, 6, 4, 0),
		MIDDLE(11, 82, 24, 42, 5, 8, 7, 4);

		private final int column, width, height, left, right, top, bottom, tab;

		Region(int column, int width, int height, int left, int right, int top, int bottom, int tab) {
			this.column = column;
			this.width = width;
			this.height = height;
			this.left = left;
			this.right = right;
			this.top = top;
			this.bottom = bottom;
			this.tab = tab;
		}
	}

	private ScratchBlockArt() {}

	private static int row(Category category) {
		return switch (category) {
			case EVENTS -> 0;
			case CONTROL -> 1;
			case OPERATORS -> 2;
			case VARIABLES -> 3;
			case DATA -> 4;
			case SENSING -> 6;
			case OUTPUT -> 5;
		};
	}

	static ScratchCanvas.Rect paintedBounds(ScratchCanvas.Rect box, Region region) {
		int overlap = region == Region.MIDDLE ? 2 : 0;
		return new ScratchCanvas.Rect(box.x() - 1, box.y() - 1 - overlap, box.width() + 2,
			box.height() + region.tab + 2 + overlap);
	}

	static void draw(GuiGraphics graphics, ScratchCanvas.Rect box, Category category, Region region) {
		ScratchCanvas.Rect bounds = paintedBounds(box, region);
		int left = Math.min(region.left, Math.max(0, bounds.width() - region.right - 1));
		int right = Math.min(region.right, bounds.width() - left - 1);
		int top = Math.min(region.top, bounds.height() - region.bottom - 1);
		int[] xs = {bounds.x(), bounds.x() + left, bounds.right() - right, bounds.right()};
		int[] ys = {bounds.y(), bounds.y() + top, bounds.bottom() - region.bottom, bounds.bottom()};
		int u = region.column * STRIDE_X;
		int v = row(category) * STRIDE_Y;
		int[] us = {u, u + region.left, u + region.width - region.right, u + region.width};
		int[] vs = {v, v + region.top, v + region.height - region.bottom, v + region.height};
		for (int j = 0; j < 3; j++) {
			for (int i = 0; i < 3; i++) {
				int width = xs[i + 1] - xs[i];
				int height = ys[j + 1] - ys[j];
				if (width > 0 && height > 0)
					graphics.blit(TEXTURE, xs[i], ys[j], width, height, us[i] * SCALE, vs[j] * SCALE,
						(us[i + 1] - us[i]) * SCALE, (vs[j + 1] - vs[j]) * SCALE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
			}
		}
	}
}
