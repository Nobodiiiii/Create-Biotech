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
	static final int COMMAND_WIDTH = 64;
	static final int MIN_VALUE_WIDTH = 16;
	static final int TAB_HEIGHT = 4;
	static final int RAIL_WIDTH = 16;
	private static final int COMMAND_SLICE_X = 61;
	private static final int COMMAND_SLICE_Y = 15;
	private static final int VALUE_SLICE_X = 15;
	private static final int VALUE_SLICE_Y = 7;
	private static final int UNSLICED = -1;

	enum Outline {
		NONE(0), ACTIVE(0xffc9ab74), PROBLEM(0xffeb8f80), MISMATCH(0xffffc084);

		private final int tint;

		Outline(int tint) { this.tint = tint; }

		int tint() { return tint; }
	}

	enum Region {
		HAT(0, 24, COMMAND_SLICE_Y, TAB_HEIGHT),
		STACK(80, 24, COMMAND_SLICE_Y, TAB_HEIGHT),
		MOUTH(160, 24, COMMAND_SLICE_Y, TAB_HEIGHT),
		FOOT(240, 16, UNSLICED, TAB_HEIGHT),
		CAP(320, 20, COMMAND_SLICE_Y, 0),
		MIDDLE(400, 24, COMMAND_SLICE_Y, TAB_HEIGHT),
		RAIL(480, 0, RAIL_WIDTH, 1, UNSLICED, UNSLICED, 0),

		ANY(0, 20),
		NUMBER(48, 16),
		TEXT(96, 16),
		BOOLEAN(144, 16),
		ITEM(192, 16),
		LIST(240, 20),
		MAP(288, 20);

		private final Axis x, y;
		private final int tab;

		Region(int u, int height) {
			this(u, 48, 32, height, VALUE_SLICE_X, VALUE_SLICE_Y, 0);
		}

		Region(int u, int height, int sliceY, int tab) {
			this(u, 0, COMMAND_WIDTH, height, COMMAND_SLICE_X, sliceY, tab);
		}

		Region(int u, int v, int width, int height, int sliceX, int sliceY, int tab) {
			this.x = new Axis(u, width, sliceX);
			this.y = new Axis(v, height, sliceY);
			this.tab = tab;
		}

		int width() { return x.size(); }
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
		return region.tab == 0 ? box : new Rect(box.x(), box.y(), box.width(), box.height() + region.tab);
	}

	static void draw(GuiGraphics graphics, Rect bounds, Category category, Region region, Outline outline) {
		drawTinted(graphics, bounds, region, tint(category), outline.tint);
	}

	static void drawValue(GuiGraphics graphics, Rect bounds, ScratchValueShape shape, int tint, int outline) {
		drawTinted(graphics, bounds, shape.region, tint, outline);
	}

	private static void drawTinted(GuiGraphics graphics, Rect bounds, Region region, int tint, int outline) {
		Slice[] columns = region.x.slices(bounds.x(), bounds.width());
		Slice[] rows = region.y.slices(bounds.y(), bounds.height());
		Matrix4f pose = graphics.pose().last().pose();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		addSlices(buffer, pose, columns, rows, 0, tint);
		if (outline != 0)
			addSlices(buffer, pose, columns, rows, OUTLINE_V, outline);
		RenderSystem.setShaderTexture(0, TEXTURE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.enableBlend();
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.disableBlend();
	}

	private record Axis(int start, int size, int stretch) {
		Slice[] slices(int origin, int length) {
			if (stretch == UNSLICED || size == length)
				return new Slice[] {new Slice(origin, origin + length, start, start + size)};

			// Trim identical texels on both sides of the stretch strip to preserve the edges of small values.
			int trim = Math.max(0, size - length);
			int trimLeading = trim / 2;
			int trimTrailing = trim - trimLeading;
			int leading = stretch - trimLeading;
			int trailing = size - stretch - 1 - trimTrailing;
			int centerStart = origin + leading;
			int centerEnd = origin + length - trailing;
			return new Slice[] {
				new Slice(origin, centerStart, start, start + leading),
				new Slice(centerStart, centerEnd, start + stretch, start + stretch + 1),
				new Slice(centerEnd, origin + length, start + size - trailing, start + size)
			};
		}
	}

	private record Slice(int start, int end, int textureStart, int textureEnd) {}

	private static void addSlices(BufferBuilder buffer, Matrix4f pose, Slice[] columns, Slice[] rows, int offsetV, int tint) {
		for (Slice row : rows) {
			float v0 = (row.textureStart + offsetV) / (float) TEXTURE_HEIGHT;
			float v1 = (row.textureEnd + offsetV) / (float) TEXTURE_HEIGHT;
			for (Slice column : columns) {
				float u0 = column.textureStart / (float) TEXTURE_WIDTH;
				float u1 = column.textureEnd / (float) TEXTURE_WIDTH;
				buffer.addVertex(pose, column.start, row.start, 0).setUv(u0, v0).setColor(tint);
				buffer.addVertex(pose, column.start, row.end, 0).setUv(u0, v1).setColor(tint);
				buffer.addVertex(pose, column.end, row.end, 0).setUv(u1, v1).setColor(tint);
				buffer.addVertex(pose, column.end, row.start, 0).setUv(u1, v0).setColor(tint);
			}
		}
	}
}
