package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.render.RenderTypes;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** An orthographic factory board, using Create's actual models and filtering renderer. */
final class GaugeCraftTreeView {
	private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath("create", "textures/block/factory_panel.png");
	private static final ResourceLocation CONNECTIONS = ResourceLocation.fromNamespaceAndPath("create", "textures/block/factory_panel_connections.png");
	// One Blockbench unit has one scale everywhere. Native panels occupy 8 x 8 units,
	// have a 6 x 6 face, and connect with 8 x 4 segments (FactoryPanelPosition/Renderer).
	private static final int UNIT = 4;
	private static final int CELL = 8 * UNIT;
	private static final int COLUMN_STEP = 2; // One empty gauge cell between production columns.
	private static final float BLOCK_SCALE = 16 * UNIT;
	private static final int PADDING = 8;
	private static final int GREEN = 0xFF9EFF7F;
	private final Font font;
	private GaugeCraftTree tree;
	private Rect2i bounds = new Rect2i(0, 0, 1, 1);
	private Rect2i viewport = new Rect2i(0, 0, 1, 1);
	private int[] nodeX = new int[0], nodeY = new int[0];
	private final List<List<Cell>> paths = new ArrayList<>();
	private int minColumn, minRow;
	private int graphWidth, graphHeight;
	private int screenWidth, screenHeight;
	private double panX, panY;
	private float zoom = 1;
	private boolean dragging;
	private record Cell(int column, int row) {
		Cell offset(int x, int y) { return new Cell(column + x, row + y); }
	}

	GaugeCraftTreeView(Font font) {
		this.font = font;
	}

	void resize(int width, int height) {
		screenWidth = width;
		screenHeight = height;
		int w = Math.min(Math.min(460, width - 20), Math.max(300, graphWidth * 2 + 24));
		int h = Math.min(Math.min(310, height - 20), Math.max(190, graphHeight * 2 + 102));
		bounds = new Rect2i((width - w) / 2, (height - h) / 2, w, h);
		viewport = new Rect2i(bounds.getX() + 12, bounds.getY() + 57, w - 24, h - 102);
		fit();
	}

	Rect2i bounds() { return bounds; }
	boolean terminal() { return tree != null && tree.terminal(); }

	void reset() {
		tree = null;
		dragging = false;
		nodeX = nodeY = new int[0];
		paths.clear();
		graphWidth = graphHeight = 0;
		resize(screenWidth, screenHeight);
	}

	void accept(GaugeCraftTree snapshot) {
		boolean layoutChanged = tree == null || tree.nodes().size() != snapshot.nodes().size()
			|| !tree.edges().equals(snapshot.edges());
		tree = snapshot;
		if (layoutChanged) {
			layout();
			resize(screenWidth, screenHeight);
		}
	}

	private void layout() {
		int size = tree.nodes().size();
		nodeX = new int[size];
		nodeY = new int[size];
		int[] layers = new int[size];
		// Packet edges are topologically ordered: every source precedes its consumer.
		for (GaugeCraftTree.Edge edge : tree.edges())
			layers[edge.to()] = Math.max(layers[edge.to()], layers[edge.from()] + 1);
		int maxLayer = 0;
		for (int layer : layers)
			maxLayer = Math.max(maxLayer, layer);
		int[] rows = new int[maxLayer + 1];
		for (int layer : layers)
			rows[layer]++;
		int maxRows = 1;
		for (int row : rows)
			maxRows = Math.max(maxRows, row);
		int[] next = new int[rows.length];
		List<Cell> cells = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			// Never half-offset a column: native gauges and wires share the same half-block grid.
			cells.add(new Cell(layers[i] * COLUMN_STEP, (maxRows - rows[layers[i]]) / 2 + next[layers[i]]++));
		}
		Set<Cell> occupied = new HashSet<>(cells);
		paths.clear();
		minColumn = minRow = 0;
		int lastGaugeColumn = maxLayer * COLUMN_STEP;
		int maxColumn = lastGaugeColumn, maxRow = maxRows - 1;
		for (GaugeCraftTree.Edge edge : tree.edges()) {
			List<Cell> path = route(cells.get(edge.from()), cells.get(edge.to()), occupied, lastGaugeColumn, maxRows);
			paths.add(path);
			for (Cell cell : path) {
				minColumn = Math.min(minColumn, cell.column());
				minRow = Math.min(minRow, cell.row());
				maxColumn = Math.max(maxColumn, cell.column());
				maxRow = Math.max(maxRow, cell.row());
			}
		}
		for (int i = 0; i < size; i++) {
			nodeX[i] = cellX(cells.get(i));
			nodeY[i] = cellY(cells.get(i));
		}
		graphWidth = (maxColumn - minColumn + 1) * CELL + PADDING * 2;
		graphHeight = (maxRow - minRow + 1) * CELL + PADDING * 2;
	}

	private int cellX(Cell cell) { return PADDING + (cell.column() - minColumn) * CELL; }
	private int cellY(Cell cell) { return PADDING + (cell.row() - minRow) * CELL; }

	private static List<Cell> route(Cell from, Cell to, Set<Cell> occupied, int columns, int rows) {
		// Native connection-sized steps through empty cells. Prefer rightward travel;
		// the one-cell perimeter leaves room to route around densely packed branches.
		ArrayDeque<Cell> queue = new ArrayDeque<>();
		Map<Cell, Cell> previous = new HashMap<>();
		queue.add(from);
		previous.put(from, from);
		while (!queue.isEmpty()) {
			Cell current = queue.removeFirst();
			if (current.equals(to)) {
				List<Cell> path = new ArrayList<>();
				for (Cell cell = to; !cell.equals(from); cell = previous.get(cell))
					path.add(cell);
				path.add(from);
				Collections.reverse(path);
				return List.copyOf(path);
			}
			int vertical = to.row() >= current.row() ? 1 : -1;
			for (Cell next : List.of(current.offset(1, 0), current.offset(0, vertical),
				current.offset(0, -vertical), current.offset(-1, 0))) {
				if (next.column() < -1 || next.column() > columns + 1 || next.row() < -1 || next.row() > rows
					|| previous.containsKey(next) || occupied.contains(next) && !next.equals(to))
					continue;
				previous.put(next, current);
				queue.addLast(next);
			}
		}
		// Like FactoryPanelConnection's last bend mode, retain the connection even
		// when a gauge is enclosed on all four sides by other gauges.
		List<Cell> fallback = new ArrayList<>();
		Cell cell = from;
		fallback.add(cell);
		while (cell.column() != to.column()) {
			cell = cell.offset(Integer.signum(to.column() - cell.column()), 0);
			fallback.add(cell);
		}
		while (cell.row() != to.row()) {
			cell = cell.offset(0, Integer.signum(to.row() - cell.row()));
			fallback.add(cell);
		}
		return List.copyOf(fallback);
	}

	void fit() {
		if (tree == null || tree.nodes().isEmpty())
			return;
		zoom = fitZoom();
		panX = (viewport.getWidth() - graphWidth * zoom) / 2;
		panY = (viewport.getHeight() - graphHeight * zoom) / 2;
		clampPan();
	}

	private float fitZoom() {
		return Math.min(2, Math.min((float) viewport.getWidth() / Math.max(1, graphWidth),
			(float) viewport.getHeight() / Math.max(1, graphHeight)));
	}

	void render(GuiGraphics graphics, int mouseX, int mouseY, ItemStack output, int requested) {
		int x = bounds.getX(), y = bounds.getY(), w = bounds.getWidth(), h = bounds.getHeight();
		renderBackground(graphics, x, y, w, h);
		graphics.drawCenteredString(font, Component.translatable("create_biotech.gauge_craft.tree.title"),
			x + w / 2, y + 4, 0x4D3E30);
		graphics.renderItem(output, x + 13, y + 22);
		graphics.drawString(font, font.plainSubstrByWidth(output.getHoverName().getString() + " ×" + requested, w - 58),
			x + 35, y + 20, 0xF4E4CB, false);
		Component status = Component.translatable(tree == null ? "create_biotech.gauge_craft.tree.loading"
			: "create_biotech.gauge_craft.status." + tree.status());
		graphics.drawString(font, font.plainSubstrByWidth(status.getString(), w - 58), x + 35, y + 32,
			tree != null && "complete".equals(tree.status()) ? GREEN : 0xD6C2A8, false);
		graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.tree.inputs"), x + 13, y + 46, 0xD6C2A8, false);
		Component outputs = Component.translatable("create_biotech.gauge_craft.tree.output");
		graphics.drawString(font, outputs, x + w - 13 - font.width(outputs), y + 46, 0xD6C2A8, false);
		graphics.enableScissor(viewport.getX(), viewport.getY(), viewport.getX() + viewport.getWidth(),
			viewport.getY() + viewport.getHeight());
		int hovered = -1;
		if (tree != null && !tree.nodes().isEmpty()) {
			double localX = (mouseX - viewport.getX() - panX) / zoom;
			double localY = (mouseY - viewport.getY() - panY) / zoom;
			graphics.pose().pushPose();
			graphics.pose().translate(viewport.getX() + panX, viewport.getY() + panY, 0);
			graphics.pose().scale(zoom, zoom, 1);
			RenderSystem.enableDepthTest();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			Lighting.setupFor3DItems();
			for (int i = 0; i < tree.edges().size(); i++)
				renderEdge(graphics, tree.edges().get(i), paths.get(i));
			for (int i = 0; i < tree.nodes().size(); i++) {
				if (!visible(nodeX[i], nodeY[i], CELL, CELL))
					continue;
				if (contains(viewport, mouseX, mouseY) && localX >= nodeX[i] && localX < nodeX[i] + CELL
					&& localY >= nodeY[i] && localY < nodeY[i] + CELL)
					hovered = i;
				renderNode(graphics, tree.nodes().get(i), nodeX[i], nodeY[i], hovered == i);
			}
			graphics.bufferSource().endBatch();
			graphics.pose().popPose();
		} else {
			graphics.drawCenteredString(font, Component.translatable(tree == null
				? "create_biotech.gauge_craft.tree.loading" : "create_biotech.gauge_craft.status.unavailable"),
				x + w / 2, y + h / 2, 0xF4E4CB);
		}
		graphics.disableScissor();
		arrow(graphics, x + 14, y + h - 41, true);
		graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.tree.crafting"), x + 33, y + h - 42, 0xF4E4CB, false);
		int lampX = x + 43 + font.width(Component.translatable("create_biotech.gauge_craft.tree.crafting"));
		bulb(graphics, lampX, y + h - 41, GaugeCraftTree.State.COMPLETE);
		graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.tree.complete"), lampX + 11, y + h - 42, 0xF4E4CB, false);
		graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("create_biotech.gauge_craft.tree.controls").getString(), w - 153),
			x + 13, y + h - 24, 0xD6C2A8, false);
		if (hovered >= 0) {
			GaugeCraftTree.Node node = tree.nodes().get(hovered);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(node.stack().getHoverName());
			tooltip.add(Component.translatable("create_biotech.gauge_craft.tree.required", node.count()));
			tooltip.add(Component.translatable("create_biotech.gauge_craft.tree.state."
				+ node.state().name().toLowerCase(Locale.ROOT)));
			if (node.state() != GaugeCraftTree.State.STOCKED)
				tooltip.add(Component.translatable("create_biotech.gauge_craft.tree.received", node.completed(), node.count()));
			graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private boolean visible(int x, int y, int width, int height) {
		return (x + width + 1) * zoom + panX >= 0 && (x - 1) * zoom + panX <= viewport.getWidth()
			&& (y + height + 1) * zoom + panY >= 0 && (y - 1) * zoom + panY <= viewport.getHeight();
	}

	private void renderNode(GuiGraphics graphics, GaugeCraftTree.Node node, int x, int y, boolean hovered) {
		renderModel(graphics, AllPartialModels.FACTORY_PANEL_WITH_BULB, x, y, 0xFFFFFF, false, RenderType.cutoutMipped());
		boolean lit = node.state() == GaugeCraftTree.State.COMPLETE || node.state() == GaugeCraftTree.State.STOCKED;
		PartialModel light = node.state() == GaugeCraftTree.State.PAUSED
			? AllPartialModels.FACTORY_PANEL_RED_LIGHT : AllPartialModels.FACTORY_PANEL_LIGHT;
		// The bulb texture is opaque; keep its base in the main GUI target even in Fabulous mode.
		renderModel(graphics, light, x, y, lit || node.state() == GaugeCraftTree.State.PAUSED ? 0xFFFFFF : 0x484848,
			false, RenderType.cutoutMipped());
		if (lit)
			renderModel(graphics, light, x, y, 0xC8C8C8, false, RenderTypes.additive());
		graphics.bufferSource().endBatch();

		// FilteringRenderer + ValueBoxTransform: the slot is at the centre and has scale .5.
		// Reuse FIXED rendering, including Create's distinct block/flat/filter item scales.
		graphics.pose().pushPose();
		graphics.pose().translate(x + CELL / 2f, y + CELL / 2f, 100);
		graphics.pose().scale(BLOCK_SCALE * .5f, -BLOCK_SCALE * .5f, BLOCK_SCALE * .5f);
		boolean flat = !Minecraft.getInstance().getItemRenderer().getModel(node.stack(), null, null, 0).usesBlockLight();
		if (flat)
			Lighting.setupForFlatItems();
		ValueBoxRenderer.renderItemIntoValueBox(node.stack(), graphics.pose(), graphics.bufferSource(),
			LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		graphics.bufferSource().endBatch();
		if (flat)
			Lighting.setupFor3DItems();
		graphics.pose().popPose();
		renderCount(graphics, node, x, y);
		if (hovered) {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 210);
			graphics.renderOutline(x, y, CELL, CELL, 0xFFEED2A8);
			graphics.pose().popPose();
		}
	}

	private void renderCount(GuiGraphics graphics, GaugeCraftTree.Node node, int x, int y) {
		// ValueBox.ItemValueBox.renderContents: .5 slot scale, 1/64 font scale,
		// (17.5 - width - 7, -5 + 10) offset, then 1.5 text scale and an 8-way outline.
		Component count = Component.literal(Integer.toString(node.count()));
		boolean filter = node.stack().getItem() instanceof FilterItem;
		float fontScale = BLOCK_SCALE * .5f / 64;
		graphics.pose().pushPose();
		graphics.pose().translate(x + CELL / 2f, y + CELL / 2f, 200);
		graphics.pose().scale(fontScale, fontScale, 1);
		graphics.pose().translate((filter ? 12.5f : 10.5f) - font.width(count), filter ? 3 : 5, 0);
		graphics.pose().scale(1.5f, 1.5f, 1);
		font.drawInBatch8xOutline(count.getVisualOrderText(), 0, 0, filter ? 0xFFFFFFFF : 0xFFEDEDED, 0xFF333333,
			graphics.pose().last().pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT);
		graphics.flush();
		graphics.pose().popPose();
	}

	private void renderEdge(GuiGraphics graphics, GaugeCraftTree.Edge edge, List<Cell> path) {
		boolean active = tree.nodes().get(edge.to()).state() == GaugeCraftTree.State.CRAFTING;
		for (int i = 1; i < path.size(); i++) {
			Cell from = path.get(i - 1), to = path.get(i);
			int x = cellX(from) + CELL / 2, y = cellY(from) + CELL / 2;
			if (!visible(Math.min(cellX(from), cellX(to)), Math.min(cellY(from), cellY(to)),
				CELL * 2, CELL * 2))
				continue;
			Direction direction = to.column() > from.column() ? Direction.EAST
				: to.column() < from.column() ? Direction.WEST : to.row() > from.row() ? Direction.SOUTH : Direction.NORTH;
			PartialModel segment = (i == path.size() - 1 ? AllPartialModels.FACTORY_PANEL_ARROWS
				: AllPartialModels.FACTORY_PANEL_LINES).get(direction);
			renderModel(graphics, segment, x, y, active ? GREEN & 0xFFFFFF : 0x888898, active, RenderType.cutoutMipped());
		}
	}

	private static void renderModel(GuiGraphics graphics, PartialModel model, int x, int y, int color,
		boolean animated, RenderType layer) {
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(BLOCK_SCALE, -BLOCK_SCALE, BLOCK_SCALE);
		graphics.pose().mulPose(Axis.XP.rotationDegrees(90));
		SuperByteBuffer buffer = CachedBuffers.partial(model, AllBlocks.FACTORY_GAUGE.getDefaultState())
			.light(LightTexture.FULL_BRIGHT).color(color);
		if (animated)
			buffer.shiftUV(AllSpriteShifts.FACTORY_PANEL_CONNECTIONS);
		buffer.renderInto(graphics.pose(), graphics.bufferSource().getBuffer(layer));
		graphics.pose().popPose();
	}

	private static void bulb(GuiGraphics graphics, int x, int y, GaugeCraftTree.State state) {
		boolean lit = state == GaugeCraftTree.State.COMPLETE || state == GaugeCraftTree.State.STOCKED;
		if (lit)
			graphics.fill(x - 1, y - 1, x + 9, y + 9, 0x486CDF73);
		float shade = lit || state == GaugeCraftTree.State.PAUSED ? 1 : .35f;
		graphics.setColor(shade, shade, shade, 1);
		graphics.blit(PANEL, x, y, 8, 8, state == GaugeCraftTree.State.PAUSED ? 11 : 9, 8, 2, 2, 16, 16);
		graphics.setColor(1, 1, 1, 1);
	}

	private static void arrow(GuiGraphics graphics, int x, int y, boolean active) {
		graphics.setColor(active ? .44f : .72f, active ? .9f : .67f, active ? .5f : .58f, 1);
		graphics.pose().pushPose();
		graphics.pose().translate(x + 16, y + 8, 0);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(180));
		graphics.blit(CONNECTIONS, 0, 0, 16, 8, 8, 8, 8, 4, 16, 16);
		graphics.pose().popPose();
		graphics.setColor(1, 1, 1, 1);
	}

	private static void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
		ResourceLocation texture = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY.location;
		for (int row = 14; row < height - 8; row += 20) {
			int h = Math.min(20, height - 8 - row);
			graphics.blit(texture, x, y + row, 8, h, 25, 48, 12, h, 256, 256);
			graphics.blit(texture, x + 8, y + row, width - 16, h, 37, 48, 182, h, 256, 256);
			graphics.blit(texture, x + width - 8, y + row, 8, h, 219, 48, 12, h, 256, 256);
		}
		graphics.blit(texture, x, y, 10, 16, 25, 0, 10, 16, 256, 256);
		graphics.blit(texture, x + 10, y, width - 20, 16, 35, 0, 184, 16, 256, 256);
		graphics.blit(texture, x + width - 10, y, 10, 16, 219, 0, 15, 16, 256, 256);
		graphics.blit(texture, x, y + height - 8, width, 8, 25, 150, 206, 8, 256, 256);
	}

	boolean mouseClicked(double x, double y, int button) {
		if (!contains(viewport, x, y))
			return false;
		if (button == 0 || button == 2)
			dragging = true;
		return true;
	}

	boolean mouseDragged(double dx, double dy) {
		if (!dragging)
			return false;
		panX += dx;
		panY += dy;
		clampPan();
		return true;
	}

	void mouseReleased() { dragging = false; }

	void scroll(double x, double y, double amount) {
		if (tree == null || tree.nodes().isEmpty() || !contains(viewport, x, y))
			return;
		float next = Mth.clamp(zoom * (float) Math.pow(1.15, amount), Math.min(.25f, fitZoom()), 4);
		double anchorX = x - viewport.getX(), anchorY = y - viewport.getY();
		panX = anchorX - (anchorX - panX) * next / zoom;
		panY = anchorY - (anchorY - panY) * next / zoom;
		zoom = next;
		clampPan();
	}

	void pan(int x, int y) {
		panX += x;
		panY += y;
		clampPan();
	}

	private void clampPan() {
		double w = graphWidth * zoom, h = graphHeight * zoom;
		panX = w <= viewport.getWidth() ? (viewport.getWidth() - w) / 2
			: Mth.clamp(panX, viewport.getWidth() - w - 12, 12);
		panY = h <= viewport.getHeight() ? (viewport.getHeight() - h) / 2
			: Mth.clamp(panY, viewport.getHeight() - h - 12, 12);
	}

	private static boolean contains(Rect2i rectangle, double x, double y) {
		return x >= rectangle.getX() && x < rectangle.getX() + rectangle.getWidth()
			&& y >= rectangle.getY() && y < rectangle.getY() + rectangle.getHeight();
	}
}
