package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterScanner.Gauge;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.ILightingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Orthographic view along the upward-facing surface of floor-mounted Factory Gauges. */
public class HoneycombGaugeClusterScreen extends AbstractContainerScreen<HoneycombGaugeClusterMenu> {
	private static final int PAPER = 0xfff7f0dd;
	private static final int INK = 0xff454751;
	private static final int PANEL = 0xffece1c8;
	private static final int WARNING = 0xffa85267;
	private static final Vector3f VIEW_LIGHT = new Vector3f(0, 0, 1);
	private static final ILightingSettings FRONT_LIGHTING =
		() -> RenderSystem.setShaderLights(VIEW_LIGHT, VIEW_LIGHT);

	private final Set<BlockPos> gaugeBlocks = new LinkedHashSet<>();
	private double sceneScale;
	private double panX;
	private double panY;
	private final float viewYaw;
	private double centerX;
	private double centerY;
	private double centerZ;
	private int listScroll;
	private boolean dragging;
	private Gauge hoveredGauge;

	public HoneycombGaugeClusterScreen(HoneycombGaugeClusterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		viewYaw = menu.facing().toYRot();
		for (Gauge gauge : menu.snapshot().gauges())
			gaugeBlocks.add(gauge.pos());
	}

	@Override
	protected void init() {
		imageWidth = Math.min(430, width - 16);
		imageHeight = Math.min(275, height - 16);
		super.init();
		fitScene();
	}

	private int viewportX() { return leftPos + 10; }
	private int viewportY() { return topPos + 33; }
	private int viewportWidth() { return imageWidth - 139; }
	private int viewportHeight() { return imageHeight - 59; }
	private int sideX() { return leftPos + imageWidth - 119; }

	private void fitScene() {
		int minX = menu.origin().getX(), maxX = minX;
		int minY = menu.origin().getY(), maxY = minY;
		int minZ = menu.origin().getZ(), maxZ = minZ;
		for (BlockPos pos : menu.snapshot().honeycombs()) {
			minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
			minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
			minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
		}
		for (BlockPos pos : gaugeBlocks) {
			minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
			minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
			minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
		}
		centerX = (minX + maxX + 1) / 2.0;
		centerY = (minY + maxY + 1) / 2.0;
		centerZ = (minZ + maxZ + 1) / 2.0;
		double spanX = maxX - minX + 1;
		double spanZ = maxZ - minZ + 1;
		boolean quarterTurn = menu.facing().getAxis() == net.minecraft.core.Direction.Axis.X;
		double viewWidth = quarterTurn ? spanZ : spanX;
		double viewHeight = quarterTurn ? spanX : spanZ;
		sceneScale = Mth.clamp(Math.min((viewportWidth() - 24.0) / viewWidth,
			(viewportHeight() - 24.0) / viewHeight), 2.0, 64.0);
		panX = 0;
		panY = 0;
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
		graphics.fill(viewportX(), viewportY(), viewportX() + viewportWidth(),
			viewportY() + viewportHeight(), 0xffdfd9c9);
		border(graphics, viewportX(), viewportY(), viewportWidth(), viewportHeight(), 0xffb9a98e);
		renderWorldScene(graphics);
		renderGaugeList(graphics, mouseX, mouseY);
		if (menu.snapshot().limited())
			graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.limit"),
				sideX(), topPos + imageHeight - 16, WARNING, false);
	}

	private void renderWorldScene(GuiGraphics graphics) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;
		graphics.flush();
		graphics.enableScissor(viewportX() + 1, viewportY() + 1,
			viewportX() + viewportWidth() - 1, viewportY() + viewportHeight() - 1);
		graphics.pose().pushPose();
		graphics.pose().translate(viewportX() + viewportWidth() / 2.0 + panX,
			viewportY() + viewportHeight() / 2.0 + panY, 150);
		graphics.pose().mulPose(Axis.XP.rotationDegrees(-90));
		graphics.pose().mulPose(Axis.YP.rotationDegrees(viewYaw));
		for (BlockPos pos : menu.snapshot().honeycombs()) {
			if (!isLoaded(level, pos))
				continue;
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.HONEYCOMB_BLOCK))
				renderBlock(graphics, pos, state, null);
		}
		for (BlockPos pos : gaugeBlocks) {
			if (!isLoaded(level, pos))
				continue;
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof FactoryPanelBlock)
				renderBlock(graphics, pos, state, level.getBlockEntity(pos));
		}
		BlockPos origin = menu.origin();
		if (isLoaded(level, origin))
			renderBlock(graphics, origin, level.getBlockState(origin), level.getBlockEntity(origin));
		graphics.pose().popPose();
		graphics.flush();
		graphics.disableScissor();
	}

	private void renderBlock(GuiGraphics graphics, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		GuiGameElement.of(state, blockEntity)
			.lighting(FRONT_LIGHTING)
			.atLocal(pos.getX() - centerX, centerY - pos.getY(), pos.getZ() - centerZ)
			.scale(sceneScale)
			.render(graphics);
	}

	private static boolean isLoaded(ClientLevel level, BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private void renderGaugeList(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = sideX();
		graphics.drawString(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.gauges"),
			x, viewportY() + 2, INK, false);
		List<Gauge> gauges = menu.snapshot().gauges();
		int visible = Math.max(1, (viewportHeight() - 24) / 27);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, gauges.size() - visible));
		hoveredGauge = null;
		for (int i = listScroll; i < Math.min(gauges.size(), listScroll + visible); i++) {
			Gauge gauge = gauges.get(i);
			int y = viewportY() + 20 + (i - listScroll) * 27;
			graphics.fill(x - 3, y - 2, x + 108, y + 23, PANEL);
			if (!gauge.filter().isEmpty())
				graphics.renderItem(gauge.filter(), x, y + 2);
			graphics.drawString(font, gauge.filter().isEmpty()
				? Component.translatable("create_biotech.honeycomb_gauge_cluster.unfiltered")
				: Component.literal(font.substrByWidth(gauge.filter().getHoverName(), 83).getString()),
				x + 19, y + 2, INK, false);
			graphics.drawString(font, Component.literal(relative(gauge.pos().below()) + "  "
				+ gauge.slot().getSerializedName()), x + 19, y + 12, 0xff7a7772, false);
			if (mouseX >= x - 3 && mouseX < x + 108 && mouseY >= y - 2 && mouseY < y + 23)
				hoveredGauge = gauge;
		}
		if (gauges.isEmpty())
			graphics.drawWordWrap(font, Component.translatable("create_biotech.honeycomb_gauge_cluster.empty"),
				x, viewportY() + 24, 105, INK);
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
		if (hoveredGauge != null)
			graphics.renderComponentTooltip(font, List.of(
				Component.literal(relative(hoveredGauge.pos().below())),
				hoveredGauge.filter().isEmpty()
					? Component.translatable("create_biotech.honeycomb_gauge_cluster.unfiltered")
					: hoveredGauge.filter().getHoverName()), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && inViewport(mouseX, mouseY)) {
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
		if (inViewport(mouseX, mouseY))
			return true;
		if (mouseX >= sideX() && mouseX < sideX() + 110 && mouseY >= viewportY()) {
			listScroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean inViewport(double x, double y) {
		return x >= viewportX() && x < viewportX() + viewportWidth()
			&& y >= viewportY() && y < viewportY() + viewportHeight();
	}
}
