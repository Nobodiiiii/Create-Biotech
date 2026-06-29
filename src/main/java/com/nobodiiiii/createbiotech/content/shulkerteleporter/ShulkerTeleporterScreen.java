package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class ShulkerTeleporterScreen extends AbstractSimiContainerScreen<ShulkerTeleporterMenu> {

	private static final Component GUI_TITLE = Component.translatable("block.create_biotech.shulker_packager");
	private static final Component SEARCH_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.search");
	private static final Component OWN_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.own_address");
	private static final Component TARGET_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.target_address");
	private static final Component NEW_ADDRESS_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.new_address");
	private static final Component NO_ADDRESSES_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.no_addresses");

	private static final int WINDOW_WIDTH = 224;
	private static final int BODY_SLICES = 6;
	private static final int WINDOW_HEIGHT =
		GuiTexture.TOP.height + GuiTexture.BODY.height * BODY_SLICES + GuiTexture.FOOTER.height;
	private static final int ROW_HEIGHT = 20;

	private static final int TITLE_Y = 7;
	private static final int SEARCH_X = 58;
	private static final int SEARCH_Y = 24;
	private static final int SEARCH_WIDTH = 116;
	private static final int TARGET_LABEL_X = 24;
	private static final int TARGET_LABEL_Y = 45;
	private static final int TARGET_BAR_X = 81;
	private static final int TARGET_BAR_Y = 45;
	private static final int TARGET_BAR_WIDTH = 104;
	private static final int ROW_X = 26;
	private static final int ACTION_X = 193;
	private static final int FOOTER_TEXT_Y = 8;
	private static final int FOOTER_TEXT_WIDTH = 158;

	private static final int TITLE_COLOR = 0x3D3C48;
	private static final int SEARCH_TEXT_COLOR = 0xC8BFCE;
	private static final int SEARCH_HINT_COLOR = 0x8A8290;
	private static final int LABEL_COLOR = 0xA9A1AE;
	private static final int VALUE_COLOR = 0x4A2D31;
	private static final int LIST_TEXT_COLOR = 0x625C67;
	private static final int LIST_HINT_COLOR = 0x9B92A0;
	private static final int SELECTED_FILL = 0x33F6DEE7;
	private static final int SELECTED_BORDER = 0x66D7B7C8;

	private final List<String> candidateAddresses;
	private String targetAddress;
	private boolean addingAddress;
	private String pendingNewAddress = "";
	private double scrollOffset;

	private EditBox searchBox;
	private EditBox ownAddressBox;
	private EditBox newAddressBox;

	public ShulkerTeleporterScreen(ShulkerTeleporterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		candidateAddresses = new ArrayList<>(menu.getCandidateAddresses());
		targetAddress = menu.getTargetAddress();
	}

	@Override
	protected void init() {
		String searchValue = searchBox == null ? "" : searchBox.getValue();
		String ownAddressValue = ownAddressBox == null ? menu.getOwnAddress() : ownAddressBox.getValue();

		setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
		super.init();
		clearWidgets();

		NoShadowFontWrapper noShadowFont = new NoShadowFontWrapper(font);

		searchBox = new EditBox(noShadowFont, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, 10, SEARCH_LABEL);
		searchBox.setBordered(false);
		searchBox.setMaxLength(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		searchBox.setTextColor(SEARCH_TEXT_COLOR);
		searchBox.setValue(searchValue);
		searchBox.setResponder($ -> {
			scrollOffset = 0;
			clampScroll();
		});
		addRenderableWidget(searchBox);

		ownAddressBox = new EditBox(noShadowFont, leftPos + 32, getFooterY() + FOOTER_TEXT_Y, FOOTER_TEXT_WIDTH, 10,
			OWN_LABEL);
		ownAddressBox.setBordered(false);
		ownAddressBox.setMaxLength(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		ownAddressBox.setTextColor(VALUE_COLOR);
		ownAddressBox.setValue(ownAddressValue);
		ownAddressBox.setFocused(false);
		ownAddressBox.mouseClicked(0, 0, 0);
		ownAddressBox.setResponder($ -> updateOwnAddressBoxPosition());
		updateOwnAddressBoxPosition();
		addRenderableWidget(ownAddressBox);

		if (addingAddress)
			createNewAddressBox(noShadowFont);

		setInitialFocus(searchBox);
		clampScroll();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		searchBox.tick();
		ownAddressBox.tick();
		if (newAddressBox != null)
			newAddressBox.tick();
		updateOwnAddressBoxPosition();
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int y = topPos;
		GuiTexture.TOP.render(graphics, leftPos + 7, y);
		y += GuiTexture.TOP.height;
		for (int i = 0; i < BODY_SLICES; i++) {
			GuiTexture.BODY.render(graphics, leftPos + 16, y);
			y += GuiTexture.BODY.height;
		}
		GuiTexture.FOOTER.render(graphics, leftPos, y);

		drawCenteredString(graphics, GUI_TITLE.getString(), leftPos + WINDOW_WIDTH / 2, topPos + TITLE_Y, TITLE_COLOR);
		graphics.drawString(font, TARGET_LABEL, leftPos + TARGET_LABEL_X, topPos + TARGET_LABEL_Y, LABEL_COLOR, false);
		drawCenteredClippedString(graphics, targetAddress, leftPos + TARGET_BAR_X, topPos + TARGET_BAR_Y + 1,
			TARGET_BAR_WIDTH, VALUE_COLOR);

		if (searchBox.getValue().isBlank() && !searchBox.isFocused())
			graphics.drawString(font, SEARCH_LABEL, searchBox.getX(), searchBox.getY(), SEARCH_HINT_COLOR, false);

		renderAddRow(graphics);
		renderNewEntryRow(graphics);
		renderCandidates(graphics);
		renderFooter(graphics);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && addingAddress && isWithinPendingEntryRow(mouseX, mouseY)) {
			boolean clicked = super.mouseClicked(mouseX, mouseY, button);
			focusEditBox(newAddressBox);
			return clicked || true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && addingAddress)
			finishAddingAddress(true);

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isWithinOwnAddressRow(mouseX, mouseY)) {
			focusEditBox(ownAddressBox);
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isWithinAddButton(mouseX, mouseY)) {
			startAddingAddress();
			playClick();
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			CandidateHit hit = getCandidateHit(mouseX, mouseY);
			if (hit != null) {
				applyCandidateHit(hit);
				playClick();
				return true;
			}
		}

		boolean clicked = super.mouseClicked(mouseX, mouseY, button);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && searchBox.isMouseOver(mouseX, mouseY))
			focusEditBox(searchBox);
		return clicked;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (isWithinCandidateViewport(mouseX, mouseY) || isWithinScrollbar(mouseX, mouseY)) {
			scrollOffset = Mth.clamp(scrollOffset - delta * 12.0d, 0.0d, getMaxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean hitEnter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
		boolean hitEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;

		if (newAddressBox != null && newAddressBox.isFocused()) {
			if (hitEnter) {
				finishAddingAddress(true);
				return true;
			}
			if (hitEscape) {
				finishAddingAddress(false);
				return true;
			}
		}

		if (searchBox.isFocused()) {
			if (hitEnter || hitEscape) {
				searchBox.setFocused(false);
				return true;
			}
		}

		if (ownAddressBox.isFocused()) {
			if (hitEnter || hitEscape) {
				ownAddressBox.setFocused(false);
				return true;
			}
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		finishAddingAddress(true);
		CBPackets.sendToServer(new ShulkerTeleporterConfigPacket(menu.getBlockPos(),
			ownAddressBox == null ? menu.getOwnAddress() : ownAddressBox.getValue(), targetAddress, candidateAddresses));
		super.removed();
	}

	private void renderAddRow(GuiGraphics graphics) {
		GuiTexture.ADD.render(graphics, leftPos + ROW_X, getAddButtonY());
	}

	private void renderNewEntryRow(GuiGraphics graphics) {
		if (!addingAddress)
			return;
		GuiTexture.ENTRY.render(graphics, leftPos + ROW_X, getPendingEntryY());
		if (newAddressBox != null && newAddressBox.getValue().isBlank() && !newAddressBox.isFocused())
			graphics.drawString(font, NEW_ADDRESS_LABEL, newAddressBox.getX(), newAddressBox.getY(), LIST_HINT_COLOR,
				false);
	}

	private void renderCandidates(GuiGraphics graphics) {
		List<CandidateView> visibleCandidates = getVisibleCandidates();
		int listTop = getListTop();
		int listHeight = getListHeight();

		graphics.enableScissor(leftPos + ROW_X, listTop, leftPos + ROW_X + 176, listTop + listHeight);
		for (int i = 0; i < visibleCandidates.size(); i++) {
			int rowY = listTop + i * ROW_HEIGHT - (int) scrollOffset;
			if (rowY + GuiTexture.ENTRY.height < listTop || rowY > listTop + listHeight)
				continue;

			CandidateView candidate = visibleCandidates.get(i);
			boolean selected = candidate.address().equals(targetAddress);
			if (selected) {
				graphics.fill(leftPos + ROW_X + 1, rowY + 1, leftPos + ROW_X + GuiTexture.ENTRY.width - 1,
					rowY + GuiTexture.ENTRY.height - 1, SELECTED_FILL);
				graphics.fill(leftPos + ROW_X, rowY, leftPos + ROW_X + GuiTexture.ENTRY.width, rowY + 1,
					SELECTED_BORDER);
				graphics.fill(leftPos + ROW_X, rowY + GuiTexture.ENTRY.height - 1, leftPos + ROW_X + GuiTexture.ENTRY.width,
					rowY + GuiTexture.ENTRY.height, SELECTED_BORDER);
			}

			GuiTexture.ENTRY.render(graphics, leftPos + ROW_X, rowY);
			drawClippedString(graphics, candidate.address(), leftPos + ROW_X + 9, rowY + 5, 120,
				selected ? VALUE_COLOR : LIST_TEXT_COLOR);

			if (i > 0)
				GuiTexture.UP.render(graphics, leftPos + ACTION_X, rowY + 1);
			if (i < visibleCandidates.size() - 1)
				GuiTexture.DOWN.render(graphics, leftPos + ACTION_X, rowY + 10);
		}
		graphics.disableScissor();

		if (visibleCandidates.isEmpty())
			drawCenteredString(graphics, NO_ADDRESSES_LABEL.getString(), leftPos + WINDOW_WIDTH / 2,
				listTop + listHeight / 2 - 4, LIST_HINT_COLOR);

		renderScrollbar(graphics, visibleCandidates.size(), listHeight);
	}

	private void renderScrollbar(GuiGraphics graphics, int visibleCount, int listHeight) {
		int contentHeight = visibleCount * ROW_HEIGHT;
		if (contentHeight <= listHeight)
			return;

		int barX = leftPos + ACTION_X + 13;
		int barY = getListTop();
		int barSize = Math.max(12, Math.round((float) listHeight * listHeight / contentHeight));
		int trackHeight = listHeight - barSize;
		int maxScroll = getMaxScroll();
		int offset = maxScroll == 0 ? 0 : Math.round((float) scrollOffset / maxScroll * trackHeight);

		AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY + offset);
		for (int i = 4; i < barSize - 5; i++)
			AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD.render(graphics, barX, barY + offset + i);
		if (barSize > 9)
			AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + offset + barSize / 2 - 4);
		AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + offset + barSize - 5);
	}

	private void renderFooter(GuiGraphics graphics) {
		if (ownAddressBox.getValue().isBlank() && !ownAddressBox.isFocused())
			drawCenteredString(graphics, OWN_LABEL.getString(), leftPos + WINDOW_WIDTH / 2,
				getFooterY() + FOOTER_TEXT_Y, LIST_HINT_COLOR);

		if (!ownAddressBox.isFocused()) {
			String displayText = getDisplayedOwnAddress();
			int textX = ownAddressBoxX(displayText);
			int editX = Math.min(leftPos + 198, textX + Math.min(font.width(displayText), ownAddressBox.getWidth()) + 5);
			GuiTexture.EDIT.render(graphics, editX, getFooterY() + 5);
		}
	}

	private void startAddingAddress() {
		if (addingAddress) {
			focusEditBox(newAddressBox);
			return;
		}

		addingAddress = true;
		scrollOffset = 0;
		createNewAddressBox(new NoShadowFontWrapper(font));
		focusEditBox(newAddressBox);
	}

	private void finishAddingAddress(boolean commit) {
		if (!addingAddress)
			return;

		if (newAddressBox != null)
			pendingNewAddress = newAddressBox.getValue();

		if (commit) {
			String normalizedAddress = ShulkerTeleporterBlockEntity.normalizeAddress(pendingNewAddress);
			if (!normalizedAddress.isBlank()) {
				candidateAddresses.remove(normalizedAddress);
				candidateAddresses.add(0, normalizedAddress);
				targetAddress = normalizedAddress;
			}
		}

		addingAddress = false;
		pendingNewAddress = "";
		if (newAddressBox != null) {
			removeWidget(newAddressBox);
			newAddressBox = null;
		}
		clampScroll();
	}

	private void applyCandidateHit(CandidateHit hit) {
		switch (hit.action()) {
			case SELECT -> targetAddress = hit.address();
			case DELETE -> {
				candidateAddresses.remove(hit.actualIndex());
				if (targetAddress.equals(hit.address()))
					targetAddress = "";
				clampScroll();
			}
			case MOVE_UP -> moveVisibleCandidate(hit.visibleIndex(), -1);
			case MOVE_DOWN -> moveVisibleCandidate(hit.visibleIndex(), 1);
		}
	}

	private void moveVisibleCandidate(int visibleIndex, int direction) {
		List<CandidateView> visibleCandidates = getVisibleCandidates();
		int swapIndex = visibleIndex + direction;
		if (visibleIndex < 0 || visibleIndex >= visibleCandidates.size() || swapIndex < 0
			|| swapIndex >= visibleCandidates.size())
			return;

		int currentActualIndex = visibleCandidates.get(visibleIndex).actualIndex();
		int targetActualIndex = visibleCandidates.get(swapIndex).actualIndex();
		Collections.swap(candidateAddresses, currentActualIndex, targetActualIndex);
	}

	private CandidateHit getCandidateHit(double mouseX, double mouseY) {
		if (!isWithinCandidateViewport(mouseX, mouseY))
			return null;

		List<CandidateView> visibleCandidates = getVisibleCandidates();
		int relativeY = (int) (mouseY - getListTop() + scrollOffset);
		int visibleIndex = relativeY / ROW_HEIGHT;
		if (visibleIndex < 0 || visibleIndex >= visibleCandidates.size())
			return null;

		int rowY = getListTop() + visibleIndex * ROW_HEIGHT - (int) scrollOffset;
		int localX = (int) mouseX - leftPos;
		int localY = (int) mouseY - rowY;
		if (localY < 0 || localY > GuiTexture.ENTRY.height)
			return null;

		CandidateView candidate = visibleCandidates.get(visibleIndex);
		if (localX >= ACTION_X && localX < ACTION_X + GuiTexture.UP.width) {
			if (localY < 9 && visibleIndex > 0)
				return new CandidateHit(candidate.actualIndex(), visibleIndex, candidate.address(), CandidateAction.MOVE_UP);
			if (localY >= 9 && visibleIndex < visibleCandidates.size() - 1)
				return new CandidateHit(candidate.actualIndex(), visibleIndex, candidate.address(),
					CandidateAction.MOVE_DOWN);
		}

		if (localX >= ROW_X + GuiTexture.ENTRY.width - 18 && localX < ROW_X + GuiTexture.ENTRY.width - 2)
			return new CandidateHit(candidate.actualIndex(), visibleIndex, candidate.address(), CandidateAction.DELETE);

		if (localX >= ROW_X && localX < ROW_X + GuiTexture.ENTRY.width)
			return new CandidateHit(candidate.actualIndex(), visibleIndex, candidate.address(), CandidateAction.SELECT);

		return null;
	}

	private List<CandidateView> getVisibleCandidates() {
		List<CandidateView> visibleCandidates = new ArrayList<>();
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		for (int i = 0; i < candidateAddresses.size(); i++) {
			String candidateAddress = candidateAddresses.get(i);
			if (!query.isBlank() && !candidateAddress.contains(query))
				continue;
			visibleCandidates.add(new CandidateView(i, candidateAddress));
		}
		return visibleCandidates;
	}

	private void clampScroll() {
		scrollOffset = Mth.clamp(scrollOffset, 0.0d, getMaxScroll());
	}

	private int getMaxScroll() {
		int contentHeight = getVisibleCandidates().size() * ROW_HEIGHT;
		return Math.max(0, contentHeight - getListHeight());
	}

	private void createNewAddressBox(NoShadowFontWrapper noShadowFont) {
		if (newAddressBox != null)
			removeWidget(newAddressBox);

		newAddressBox = new EditBox(noShadowFont, leftPos + ROW_X + 9, getPendingEntryY() + 5, 120, 10, NEW_ADDRESS_LABEL);
		newAddressBox.setBordered(false);
		newAddressBox.setMaxLength(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		newAddressBox.setTextColor(VALUE_COLOR);
		newAddressBox.setValue(pendingNewAddress);
		addRenderableWidget(newAddressBox);
	}

	private void updateOwnAddressBoxPosition() {
		if (ownAddressBox == null)
			return;
		ownAddressBox.setX(ownAddressBoxX(getDisplayedOwnAddress()));
	}

	private int ownAddressBoxX(String text) {
		return leftPos + WINDOW_WIDTH / 2 - (Math.min(font.width(text), ownAddressBox.getWidth()) + 10) / 2;
	}

	private String getDisplayedOwnAddress() {
		if (ownAddressBox == null)
			return OWN_LABEL.getString();
		if (ownAddressBox.getValue().isBlank() && !ownAddressBox.isFocused())
			return OWN_LABEL.getString();
		return ownAddressBox.getValue();
	}

	private boolean isWithinAddButton(double mouseX, double mouseY) {
		return mouseX >= leftPos + ROW_X && mouseX < leftPos + ROW_X + GuiTexture.ADD.width && mouseY >= getAddButtonY()
			&& mouseY < getAddButtonY() + GuiTexture.ADD.height;
	}

	private boolean isWithinPendingEntryRow(double mouseX, double mouseY) {
		return addingAddress && mouseX >= leftPos + ROW_X && mouseX < leftPos + ROW_X + GuiTexture.ENTRY.width
			&& mouseY >= getPendingEntryY() && mouseY < getPendingEntryY() + GuiTexture.ENTRY.height;
	}

	private boolean isWithinCandidateViewport(double mouseX, double mouseY) {
		return mouseX >= leftPos + ROW_X && mouseX < leftPos + ROW_X + GuiTexture.ENTRY.width + 18
			&& mouseY >= getListTop() && mouseY < getListTop() + getListHeight();
	}

	private boolean isWithinScrollbar(double mouseX, double mouseY) {
		return mouseX >= leftPos + ACTION_X + 13 && mouseX < leftPos + ACTION_X + 18 && mouseY >= getListTop()
			&& mouseY < getListTop() + getListHeight();
	}

	private boolean isWithinOwnAddressRow(double mouseX, double mouseY) {
		return mouseX >= leftPos + 16 && mouseX < leftPos + 208 && mouseY >= getFooterY() + 2
			&& mouseY < getFooterY() + 22;
	}

	private int getAddButtonY() {
		return topPos + GuiTexture.TOP.height + 8;
	}

	private int getPendingEntryY() {
		return getAddButtonY() + ROW_HEIGHT;
	}

	private int getListTop() {
		return getAddButtonY() + ROW_HEIGHT + 4 + (addingAddress ? ROW_HEIGHT : 0);
	}

	private int getListHeight() {
		return getFooterY() - 8 - getListTop();
	}

	private int getFooterY() {
		return topPos + GuiTexture.TOP.height + GuiTexture.BODY.height * BODY_SLICES;
	}

	private void focusEditBox(EditBox box) {
		if (box == null)
			return;
		setFocused(box);
		box.setFocused(true);
		box.moveCursorToEnd();
		box.setHighlightPos(0);
	}

	private void playClick() {
		playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
	}

	private void drawCenteredString(GuiGraphics graphics, String text, int centerX, int y, int color) {
		graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	private void drawCenteredClippedString(GuiGraphics graphics, String text, int x, int y, int width, int color) {
		drawClippedString(graphics, text, x + Math.max(0, (width - font.width(clip(text, width))) / 2), y, width, color);
	}

	private void drawClippedString(GuiGraphics graphics, String text, int x, int y, int width, int color) {
		graphics.drawString(font, clip(text, width), x, y, color, false);
	}

	private String clip(String text, int width) {
		if (text == null || text.isBlank())
			return "";
		if (font.width(text) <= width)
			return text;
		String ellipsis = "...";
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
	}

	private enum CandidateAction {
		SELECT,
		DELETE,
		MOVE_UP,
		MOVE_DOWN
	}

	private record CandidateView(int actualIndex, String address) {}

	private record CandidateHit(int actualIndex, int visibleIndex, String address, CandidateAction action) {}

	private enum GuiTexture implements ScreenElement {
		TOP(23, 0, 210, 63),
		BODY(32, 79, 192, 24),
		FOOTER(16, 121, 224, 42),
		ADD(42, 179, 21, 18),
		ENTRY(42, 211, 165, 18),
		EDIT(242, 125, 13, 13),
		UP(209, 212, 8, 8),
		DOWN(209, 221, 8, 8);

		private static final ResourceLocation LOCATION =
			CreateBiotech.asResource("textures/gui/shulker_teleporter.png");

		private final int startX;
		private final int startY;
		private final int width;
		private final int height;

		GuiTexture(int startX, int startY, int width, int height) {
			this.startX = startX;
			this.startY = startY;
			this.width = width;
			this.height = height;
		}

		@Override
		public void render(GuiGraphics graphics, int x, int y) {
			graphics.blit(LOCATION, x, y, startX, startY, width, height, 256, 256);
		}
	}
}
