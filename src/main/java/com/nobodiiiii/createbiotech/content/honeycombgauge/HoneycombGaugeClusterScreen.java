package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterScanner.Gauge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Read-only diagram of the server's connected honeycomb and attached factory gauges. */
public class HoneycombGaugeClusterScreen extends AbstractContainerScreen<HoneycombGaugeClusterMenu> {
	private static final int PAPER = 0xfff7f0dd;
	private static final int INK = 0xff454751;
	private static final int GRID = 0xffe5dcc6;
	private static final int HONEY = 0xffe9b959;
	private static final int HONEY_EDGE = 0xffa36a2d;
	private static final int GAUGE = 0xff609aa4;
	private static final int FOCUS = 0xffa85267;

	private double cell = 18;
	private double centerU;
	private double centerV;
	private double panX;
	private double panY;
	private int listScroll;
	private boolean dragging;
	private BlockPos hoveredBlock;
	private final Map<BlockPos, List<Gauge>> gaugesByBase = new HashMap<>();
	private final Direction right;
	private final Direction down;

	public HoneycombGaugeClusterScreen(HoneycombGaugeClusterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		right = switch (menu.facing()) {
			case UP, DOWN, NORTH -> Direction.EAST;
			case SOUTH -> Direction.WEST;
			case EAST -> Direction.SOUTH;
			case WEST -> Direction.NORTH;
		};
		down = switch (menu.facing()) {
			case UP -> Direction.SOUTH;
			case DOWN -> Direction.NORTH;
			default -> Direction.DOWN;
		};
		for (Gauge gauge : menu.snapshot().gauges())
			gaugesByBase.computeIfAbsent(gauge.pos().below(), unused -> new ArrayList<>()).add(gauge);
	}

	@Override
	protected void init() {
		imageWidth = Math.min(430, width - 16);
		imageHeight = Math.min(275, height - 16);
		super.init();
		fitDiagram();
	}

	private int mapX() { return leftPos + 10; }
	private int mapY() { return topPos + 33; }
	private int mapWidth() { return imageWidth - 139; }
	private int mapHeight() { return imageHeight - 59; }
	private int sideX() { return leftPos + imageWidth - 119; }

	private void fitDiagram() {
		int minU = 0, maxU = 0, minV = 0, maxV = 0;
		for (BlockPos pos : menu.snapshot().honeycombs()) {
			int u = project(pos, right);
			int v = project(pos, down);
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}
		centerU = (minU + maxU) / 2.0;
		centerV = (minV + maxV) / 2.0;
		cell = Mth.clamp(Math.min((mapWidth() - 22.0) / (maxU - minU + 1),
			(mapHeight() - 22.0) / (maxV - minV + 1)), 2.0, 24.0);
		panX = 0;
		panY = 0;
	}

	private int project(BlockPos pos, Direction axis) {
		return (pos.getX() - menu.origin().getX()) * axis.getStepX()
			+ (pos.getY() - menu.origin().getY()) * axis.getStepY()
			+ (pos.getZ() - menu.origin().getZ()) * axis.getStepZ();
	}

	private int tileX(BlockPos pos) {
		return (int) Math.round(mapX() + mapWidth() / 2.0 + panX
			+ (project(pos, right) - centerU) * cell - cell / 2.0);
	}

	private int tileY(BlockPos pos) {
		return (int) Math.round(mapY() + mapHeight() / 2.0 + panY
			+ (project(pos, down) - centerV) * cell - cell / 2.0);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PAPER);
		border(graphics, leftPos, topPos, imageWidth, imageHeight, INK);
		graphics.drawString(font, title, leftPos + 13, topPos + 10, INK, false);
		graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.controls"),
			leftPos + 13, topPos + 21, 0xff77736d, false);
		graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.summary",
			menu.snapshot().honeycombs().size(), menu.snapshot().gauges().size()),
			leftPos + 13, topPos + imageHeight - 16, INK, false);
		graphics.fill(mapX(), mapY(), mapX() + mapWidth(), mapY() + mapHeight(), 0xffefe7d4);
		border(graphics, mapX(), mapY(), mapWidth(), mapHeight(), 0xffb9a98e);
		graphics.enableScissor(mapX() + 1, mapY() + 1, mapX() + mapWidth() - 1,
			mapY() + mapHeight() - 1);
		for (int offset = 0; offset <= mapWidth(); offset += 20)
			graphics.fill(mapX() + offset, mapY(), mapX() + offset + 1, mapY() + mapHeight(), GRID);
		for (int offset = 0; offset <= mapHeight(); offset += 20)
			graphics.fill(mapX(), mapY() + offset, mapX() + mapWidth(), mapY() + offset + 1, GRID);
		hoveredBlock = null;
		for (BlockPos pos : menu.snapshot().honeycombs())
			drawTile(graphics, pos, false, mouseX, mouseY);
		drawTile(graphics, menu.origin(), true, mouseX, mouseY);
		graphics.disableScissor();
		graphics.drawString(font, "-  +  R", mapX() + mapWidth() - 49, mapY() + 5, INK, false);

		int x = sideX();
		graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.gauges"),
			x, mapY() + 2, INK, false);
		List<Gauge> gauges = menu.snapshot().gauges();
		int visible = Math.max(1, (mapHeight() - 24) / 27);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, gauges.size() - visible));
		for (int i = listScroll; i < Math.min(gauges.size(), listScroll + visible); i++) {
			Gauge gauge = gauges.get(i);
			int y = mapY() + 20 + (i - listScroll) * 27;
			graphics.fill(x - 3, y - 2, x + 108, y + 23, 0xffece1c8);
			if (!gauge.filter().isEmpty())
				graphics.renderItem(gauge.filter(), x, y + 2);
			graphics.drawString(font, gauge.filter().isEmpty()
				? Component.translatable("create_biotech.honeycomb_gauge_cluster.unfiltered")
				: Component.literal(font.substrByWidth(gauge.filter().getHoverName(), 83).getString()),
				x + 19, y + 2, INK, false);
			graphics.drawString(font, Component.literal(relative(gauge.pos().below()) + "  "
				+ gauge.slot().getSerializedName()), x + 19, y + 12, 0xff7a7772, false);
		}
		if (gauges.isEmpty())
			graphics.drawWordWrap(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.empty"),
				x, mapY() + 24, 105, INK);
		if (menu.snapshot().limited())
			graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.limit"),
				x, topPos + imageHeight - 16, FOCUS, false);
	}

	private void drawTile(GuiGraphics graphics, BlockPos pos, boolean controller, int mouseX, int mouseY) {
		int x = tileX(pos), y = tileY(pos);
		int size = Math.max(2, (int) Math.ceil(cell));
		graphics.fill(x, y, x + size, y + size, controller ? FOCUS : HONEY_EDGE);
		if (size >= 5)
			graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, controller ? 0xffcf8191 : HONEY);
		if (!controller && gaugesByBase.containsKey(pos)) {
			int radius = Math.max(1, size / 4);
			graphics.fill(x + size / 2 - radius, y + size / 2 - radius,
				x + size / 2 + radius + 1, y + size / 2 + radius + 1, GAUGE);
		}
		if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size
			&& mouseX >= mapX() && mouseX < mapX() + mapWidth()
			&& mouseY >= mapY() && mouseY < mapY() + mapHeight())
			hoveredBlock = pos;
	}

	private String relative(BlockPos pos) {
		return String.format("%+d,%+d,%+d", pos.getX() - menu.origin().getX(),
			pos.getY() - menu.origin().getY(), pos.getZ() - menu.origin().getZ());
	}

	private static void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		if (hoveredBlock != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(relative(hoveredBlock)));
			if (hoveredBlock.equals(menu.origin()))
				lines.add(Component.translatable("block.create_biotech.honeycomb_gauge_cluster"));
			for (Gauge gauge : gaugesByBase.getOrDefault(hoveredBlock, List.of()))
				lines.add(Component.literal(gauge.slot().getSerializedName() + ": ").append(
					gauge.filter().isEmpty()
						? Component.translatable("create_biotech.honeycomb_gauge_cluster.unfiltered")
						: gauge.filter().getHoverName()));
			graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && inMap(mouseX, mouseY)) {
			if (mouseY < mapY() + 16 && mouseX >= mapX() + mapWidth() - 55) {
				if (mouseX < mapX() + mapWidth() - 38)
					cell = Math.max(2, cell - 2);
				else if (mouseX < mapX() + mapWidth() - 22)
					cell = Math.min(48, cell + 2);
				else
					fitDiagram();
			} else
				dragging = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dragging && button == 0) {
			panX += dragX;
			panY += dragY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (inMap(mouseX, mouseY)) {
			cell = Mth.clamp(cell + scrollY * 2, 2.0, 48.0);
			return true;
		}
		if (mouseX >= sideX() && mouseX < sideX() + 110 && mouseY >= mapY()) {
			listScroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_R) {
			fitDiagram();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private boolean inMap(double x, double y) {
		return x >= mapX() && x < mapX() + mapWidth() && y >= mapY() && y < mapY() + mapHeight();
	}
}
