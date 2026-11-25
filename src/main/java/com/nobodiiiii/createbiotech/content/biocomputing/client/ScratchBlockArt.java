package com.nobodiiiii.createbiotech.content.biocomputing.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/** Stretches the centers of the shared grayscale sprites and tints them when drawn. */
final class ScratchBlockArt {
	private static final ResourceLocation TEXTURE = CreateBiotech.asResource("textures/gui/biocomputing/scratch_blocks.png");
	private static final int TEXTURE_WIDTH = 512;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int OUTLINE_V = 128;

	enum Outline {
		NONE(0), ACTIVE(0xffc9ab74), PROBLEM(0xffeb8f80);

		private final int tint;

		Outline(int tint) { this.tint = tint; }
	}

	enum Region {
		HAT(0, 0, 75, 39, 50, 5, 12, 8, 4),
		STACK(80, 0, 84, 30, 26, 5, 6, 7, 4),
		MOUTH(168, 0, 82, 30, 42, 5, 6, 7, 4),
		FOOT(256, 0, 82, 18, 42, 5, 6, 7, 4),
		CAP(344, 0, 84, 26, 26, 5, 6, 4, 0),
		RAIL(432, 0, 18, 6, 3, 3, 0, 0, 0),
		MIDDLE(0, 48, 82, 24, 42, 5, 8, 7, 4),

		ANY(0, 4, 4, 4, 4),
		NUMBER(40, 3, 3, 3, 3),
		TEXT(80, 2, 8, 7, 2),
		BOOLEAN(120, 11, 11, 7, 7),
		ITEM(160, 5, 5, 5, 5),
		LIST(200, 5, 5, 4, 4),
		MAP(240, 5, 5, 5, 5);

		private final int[] u, v;
		private final int left, right, top, bottom, tab;

		Region(int u, int left, int right, int top, int bottom) {
			this(u, 80, 32, 15, left, right, top, bottom, 0);
		}

		Region(int u, int v, int width, int height, int left, int right, int top, int bottom, int tab) {
			this.u = new int[] {u, u + left, u + width - right, u + width};
			this.v = new int[] {v, v + top, v + height - bottom, v + height};
			this.left = left;
			this.right = right;
			this.top = top;
			this.bottom = bottom;
			this.tab = tab;
		}
	}

	private ScratchBlockArt() {}

	static int tint(Category category) {
		return switch (category) {
			case EVENTS -> 0xffac8750;
			case CONTROL -> 0xffa46b49;
			case OPERATORS -> 0xff77875e;
			case VARIABLES -> 0xff9c7666;
			case DATA -> 0xff807d71;
			case OUTPUT -> 0xff627d8e;
			case SENSING -> 0xff5e7d6d;
		};
	}

	static Rect paintedBounds(Rect box, Region region) {
		int overlap = region == Region.MIDDLE ? 2 : 0;
		return new Rect(box.x() - 1, box.y() - 1 - overlap, box.width() + 2, box.height() + region.tab + 2 + overlap);
	}

	static void draw(GuiGraphics graphics, Rect box, Category category, Region region, Outline outline) {
		drawTinted(graphics, paintedBounds(box, region), region, tint(category), outline.tint);
	}

	static void drawValue(GuiGraphics graphics, Rect bounds, ScratchValueShape shape, int tint, int outline) {
		drawTinted(graphics, bounds, shape.region, tint, outline);
	}

	private static void drawTinted(GuiGraphics graphics, Rect bounds, Region region, int tint, int outline) {
		int[] x = cuts(bounds.x(), bounds.width(), region.left, region.right);
		int[] y = cuts(bounds.y(), bounds.height(), region.top, region.bottom);
		Matrix4f pose = graphics.pose().last().pose();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		addSlices(buffer, pose, region, x, y, 0, tint);
		if (outline != 0)
			addSlices(buffer, pose, region, x, y, OUTLINE_V, outline);
		RenderSystem.setShaderTexture(0, TEXTURE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.enableBlend();
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.disableBlend();
	}

	private static int[] cuts(int origin, int length, int first, int last) {
		first = Math.min(first, Math.max(0, length - last - 1));
		last = Math.min(last, Math.max(0, length - first - 1));
		return new int[] {origin, origin + first, origin + length - last, origin + length};
	}

	private static void addSlices(BufferBuilder buffer, Matrix4f pose, Region region, int[] x, int[] y, int offsetV, int tint) {
		for (int row = 0; row < 3; row++) {
			if (y[row + 1] <= y[row])
				continue;
			float v0 = (region.v[row] + offsetV) / (float) TEXTURE_HEIGHT;
			float v1 = (region.v[row + 1] + offsetV) / (float) TEXTURE_HEIGHT;
			for (int column = 0; column < 3; column++) {
				int left = x[column];
				int right = x[column + 1];
				if (right <= left)
					continue;
				float u0 = region.u[column] / (float) TEXTURE_WIDTH;
				float u1 = region.u[column + 1] / (float) TEXTURE_WIDTH;
				buffer.addVertex(pose, left, y[row], 0).setUv(u0, v0).setColor(tint);
				buffer.addVertex(pose, left, y[row + 1], 0).setUv(u0, v1).setColor(tint);
				buffer.addVertex(pose, right, y[row + 1], 0).setUv(u1, v1).setColor(tint);
				buffer.addVertex(pose, right, y[row], 0).setUv(u1, v0).setColor(tint);
			}
		}
	}
}
