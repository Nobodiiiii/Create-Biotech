package com.nobodiiiii.createbiotech.content.biocomputing.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Draws command tiles from the atlas and shares value contours between blocks and sockets. */
final class ScratchBlockArt {
	private static final int SCALE = 2;
	private static final int STRIDE_X = 88;
	private static final int STRIDE_Y = 48;
	private static final int TEXTURE_WIDTH = 12 * STRIDE_X * SCALE;
	private static final int TEXTURE_HEIGHT = 10 * STRIDE_Y * SCALE;
	private static final ResourceLocation TEXTURE = CreateBiotech.asResource("textures/gui/biocomputing/scratch_blocks.png");
	private static final int[] VALUE_COLORS = {0xffffbf00, 0xffffab19, 0xff59c059, 0xffff8c1a, 0xffff661a, 0xff9966ff, 0xff5cb1d6};

	enum Region {
		HAT(0, 75, 39, 50, 5, 12, 8, 4),
		STACK(1, 84, 30, 26, 5, 6, 7, 4),
		MOUTH(2, 82, 30, 42, 5, 6, 7, 4),
		FOOT(3, 82, 18, 42, 5, 6, 7, 4),
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

	static Rect paintedBounds(Rect box, Region region) {
		int overlap = region == Region.MIDDLE ? 2 : 0;
		return new Rect(box.x() - 1, box.y() - 1 - overlap, box.width() + 2,
			box.height() + region.tab + 2 + overlap);
	}

	static void draw(GuiGraphics graphics, Rect box, Category category, Region region) {
		Rect bounds = paintedBounds(box, region);
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

	static void drawValue(GuiGraphics graphics, Rect bounds, Category category, ScratchValueShape shape) {
		int color = VALUE_COLORS[row(category)];
		drawValue(graphics, bounds, shape, color, FastColor.ARGB32.multiply(color, 0xffcccccc));
	}

	static void drawValue(GuiGraphics graphics, Rect bounds, ScratchValueShape shape, int fill, int edge) {
		VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
		Matrix4f pose = graphics.pose().last().pose();
		if (edge != 0)
			fillValue(vertices, pose, bounds, shape, edge, false);
		fillValue(vertices, pose, bounds, shape, fill, edge != 0);
		graphics.flush();
	}

	private static void fillValue(VertexConsumer vertices, Matrix4f pose, Rect bounds, ScratchValueShape shape, int color,
		boolean interior) {
		int height = bounds.height();
		int start = interior ? 1 : 0;
		int end = interior ? height - 1 : height;
		int top = bounds.y() + start;
		int left = shape.inset(start, height, false, interior);
		int right = shape.inset(start, height, true, interior);
		// Submit consecutive rows with the same edges in a single quad.
		for (int y = start + 1; y < end; y++) {
			int nextLeft = shape.inset(y, height, false, interior);
			int nextRight = shape.inset(y, height, true, interior);
			if (nextLeft == left && nextRight == right)
				continue;
			quad(vertices, pose, bounds.x() + left, top, bounds.right() - right, bounds.y() + y, color);
			top = bounds.y() + y;
			left = nextLeft;
			right = nextRight;
		}
		quad(vertices, pose, bounds.x() + left, top, bounds.right() - right, bounds.y() + end, color);
	}

	private static void quad(VertexConsumer vertices, Matrix4f pose, int left, int top, int right, int bottom, int color) {
		vertices.addVertex(pose, right, bottom, 0).setColor(color);
		vertices.addVertex(pose, right, top, 0).setColor(color);
		vertices.addVertex(pose, left, top, 0).setColor(color);
		vertices.addVertex(pose, left, bottom, 0).setColor(color);
	}
}
