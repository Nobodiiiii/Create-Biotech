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
		NONE(0), ACTIVE(0xffc9ab74), PROBLEM(0xffeb8f80), MISMATCH(0xffffc084);

		private final int tint;

		Outline(int tint) { this.tint = tint; }

		int tint() { return tint; }
	}

	enum Region {
		HAT(0, 0, 64, 26, 50, 5, 9, 8, 4),
		STACK(72, 0, 64, 26, 26, 5, 6, 7, 4),
		MOUTH(144, 0, 64, 26, 42, 5, 6, 7, 4),
		FOOT(216, 0, 64, 18, 42, 5, 6, 7, 4),
		CAP(288, 0, 64, 22, 26, 5, 6, 4, 0),
		MIDDLE(360, 0, 64, 20, 42, 5, 8, 7, 4),
		RAIL(432, 0, 16, 15, 3, 3, 0, 0, 0),

		ANY(0, 20, 4, 4, 4, 4),
		NUMBER(40, 16, 3, 3, 3, 3),
		TEXT(80, 16, 2, 8, 7, 2),
		BOOLEAN(120, 16, 11, 11, 7, 7),
		ITEM(160, 16, 5, 5, 5, 5),
		LIST(200, 20, 5, 5, 4, 4),
		MAP(240, 20, 5, 5, 5, 5);

		private final int[] u, v;
		private final int left, right, top, bottom, tab;

		Region(int u, int height, int left, int right, int top, int bottom) {
			this(u, 40, 32, height, left, right, top, bottom, 0);
		}

		Region(int u, int v, int width, int height, int left, int right, int top, int bottom, int tab) {
			this.u = cuts(u, width, left, right);
			this.v = cuts(v, height, top, bottom);
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
		int caps = first + last;
		int available = Math.max(0, length - 1);
		if (caps > available) {
			first = first * available / caps;
			last = last * available / caps;
		}
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
