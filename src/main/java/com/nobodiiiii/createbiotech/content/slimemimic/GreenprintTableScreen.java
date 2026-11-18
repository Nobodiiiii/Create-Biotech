package com.nobodiiiii.createbiotech.content.slimemimic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class GreenprintTableScreen extends AbstractContainerScreen<GreenprintTableMenu> {

	private static final String UI = "gui.create_biotech.greenprint_table.";
	private final Path directory;
	private List<Path> files = List.of();
	private int selected;
	private Button previous;
	private Button next;
	private Button importButton;
	private Button exportButton;

	public GreenprintTableScreen(GreenprintTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageHeight = 202;
		inventoryLabelY = 109;
		directory = Minecraft.getInstance().gameDirectory.toPath().resolve("greenprints");
	}

	@Override
	protected void init() {
		super.init();
		previous = button(8, 20, 20, Component.literal("<"), () -> select(-1));
		next = button(148, 20, 20, Component.literal(">"), () -> select(1));
		previous.setTooltip(Tooltip.create(Component.translatable(UI + "previous")));
		next.setTooltip(Tooltip.create(Component.translatable(UI + "next")));
		button(8, 45, 78, Component.translatable(UI + "refresh"), this::refresh);
		button(90, 45, 78, Component.translatable(UI + "folder"), this::openFolder);
		importButton = button(55, 76, 66, Component.translatable(UI + "import"), this::importFile);
		importButton.setTooltip(Tooltip.create(Component.translatable(UI
			+ (minecraft.player != null && !minecraft.player.isCreative() ? "creative" : "blank_hint"))));
		exportButton = button(55, 94, 66, Component.translatable(UI + "export"), this::exportFile);
		refresh();
	}

	private Button button(int x, int y, int width, Component caption, Runnable click) {
		return addRenderableWidget(Button.builder(caption, button -> click.run())
			.bounds(leftPos + x, topPos + y, width, 16).build());
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		previous.active = next.active = files.size() > 1;
		boolean canImport = minecraft.player != null && menu.canImport(minecraft.player);
		importButton.active = canImport && !files.isEmpty();
		exportButton.active = menu.input().is(CBItems.GREENPRINT.get()) && !GreenprintItem.isBlank(menu.input());
	}

	private void refresh() {
		Path current = files.isEmpty() ? null : files.get(selected);
		try {
			Files.createDirectories(directory);
			try (var paths = Files.list(directory)) {
				files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
					.sorted().toList();
			}
		} catch (IOException | RuntimeException exception) {
			files = List.of();
			message("file_error");
		}
		selected = current == null ? 0 : Math.max(0, files.indexOf(current));
		updateButtons();
	}

	private void select(int direction) {
		selected = Math.floorMod(selected + direction, files.size());
	}

	private void importFile() {
		try (InputStream input = Files.newInputStream(files.get(selected), LinkOption.NOFOLLOW_LINKS)) {
			byte[] data = input.readNBytes(GreenprintImportPacket.MAX_FILE_BYTES + 1);
			if (data.length > GreenprintImportPacket.MAX_FILE_BYTES) {
				message("too_large");
				return;
			}
			CBPackets.sendToServer(new GreenprintImportPacket(menu.containerId, data));
		} catch (IOException | RuntimeException exception) {
			message("file_error");
		}
	}

	private void exportFile() {
		CompoundTag file = GreenprintItem.exportBinding(menu.input());
		if (file == null)
			return;
		if (file.sizeInBytes() > GreenprintImportPacket.MAX_NBT_BYTES) {
			message("too_large");
			return;
		}
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, output);
			if (output.size() > GreenprintImportPacket.MAX_FILE_BYTES) {
				message("too_large");
				return;
			}
			Files.createDirectories(directory);
			Path path = directory.resolve("greenprint-" + UUID.randomUUID() + ".nbt");
			Files.write(path, output.toByteArray(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			refresh();
			selected = Math.max(0, files.indexOf(path));
			message("exported", path.getFileName().toString());
		} catch (IOException | RuntimeException exception) {
			message("file_error");
		}
	}

	private void openFolder() {
		try {
			Files.createDirectories(directory);
			Util.getPlatform().openFile(directory.toFile());
		} catch (IOException exception) {
			message("file_error");
		}
	}

	private void message(String key, Object... args) {
		if (minecraft.player != null)
			minecraft.player.displayClientMessage(Component.translatable("message.create_biotech.greenprint_table." + key, args), false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		if (!files.isEmpty() && mouseX >= leftPos + 32 && mouseX < leftPos + 144
			&& mouseY >= topPos + 20 && mouseY < topPos + 36)
			graphics.renderTooltip(font, Component.literal(files.get(selected).getFileName().toString()), mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		String name = files.isEmpty() ? Component.translatable(UI + "no_files").getString()
			: files.get(selected).getFileName().toString();
		if (font.width(name) > 106)
			name = font.plainSubstrByWidth(name, 100) + "…";
		graphics.drawCenteredString(font, name, imageWidth / 2, 24, 0xe0f3d7);
		graphics.drawString(font, Component.translatable(UI + "input"), 25, 65, 0x314a39, false);
		graphics.drawString(font, Component.translatable(UI + "output"), 121, 65, 0x314a39, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff263830);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xffbccbb4);
		graphics.fill(leftPos + 31, topPos + 19, leftPos + 145, topPos + 37, 0xff30493b);
		for (var slot : menu.slots) {
			int x = leftPos + slot.x, y = topPos + slot.y;
			graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xff657d68);
			graphics.fill(x, y, x + 16, y + 16, 0xff93a896);
		}
	}
}
