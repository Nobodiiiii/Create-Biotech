package com.nobodiiiii.createbiotech.content.surgery.client;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Shows the surgery guide in Create's train-schedule frame with one scrollable text field. */
@OnlyIn(Dist.CLIENT)
public class SurgeryGuideScreen extends AbstractSimiScreen {
	private static final AllGuiTextures BACKGROUND = AllGuiTextures.SCHEDULE;
	private static final int FIELD_X = 18;
	private static final int FIELD_Y = 18;
	private static final int FIELD_WIDTH = 216;
	private static final int FIELD_HEIGHT = 169;
	/** Shared by guide openings for this client session; the first opening starts at the top. */
	private static double rememberedScrollAmount;

	private GuideTextField textField;

	private SurgeryGuideScreen() {
		super(Component.translatable("create_biotech.surgery_guide.title"));
	}

	@Override
	protected void init() {
		// Resizing rebuilds widgets on the same screen without necessarily removing it first.
		rememberScrollPosition();
		setWindowSize(BACKGROUND.getWidth(), BACKGROUND.getHeight());
		super.init();
		clearWidgets();

		textField = new GuideTextField(font, guiLeft + FIELD_X, guiTop + FIELD_Y,
			FIELD_WIDTH, FIELD_HEIGHT, title);
		textField.setValue(SurgeryGuideText.build());
		textField.setFocused(false);
		// setValue scrolls to the end of the text, so restore only after it has been populated.
		textField.restoreScrollPosition(rememberedScrollAmount);
		addRenderableWidget(textField);

		IconButton closeButton = new IconButton(guiLeft + BACKGROUND.getWidth() - 42,
			guiTop + BACKGROUND.getHeight() - 30, AllIcons.I_CONFIRM);
		closeButton.withCallback(this::onClose);
		addRenderableWidget(closeButton);
	}

	@Override
	public void removed() {
		rememberScrollPosition();
		super.removed();
	}

	private void rememberScrollPosition() {
		if (textField != null)
			rememberedScrollAmount = textField.scrollPosition();
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		BACKGROUND.render(graphics, guiLeft, guiTop);

		FormattedCharSequence visualTitle = title.copy()
			.withStyle(ChatFormatting.BOLD, ChatFormatting.BLACK)
			.getVisualOrderText();
		int center = guiLeft + (BACKGROUND.getWidth() - 8) / 2;
		graphics.drawString(font, visualTitle, center - font.width(visualTitle) / 2.0f,
			guiTop + 4.0f, 0x000000, false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	public static void open() {
		ScreenOpener.open(new SurgeryGuideScreen());
	}

	/** Keeps guide text selectable and copyable while preventing accidental edits. */
	private static class GuideTextField extends MultiLineEditBox {
		GuideTextField(Font font, int x, int y, int width, int height, Component message) {
			super(font, x, y, width, height, CommonComponents.EMPTY, message);
		}

		double scrollPosition() {
			return scrollAmount();
		}

		void restoreScrollPosition(double amount) {
			setScrollAmount(amount);
		}

		@Override
		public boolean charTyped(char codePoint, int modifiers) {
			return false;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE
				|| keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| Screen.isCut(keyCode) || Screen.isPaste(keyCode))
				return true;
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		protected void renderBackground(GuiGraphics graphics) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x60303030);
		}

		@Override
		protected void renderBorder(GuiGraphics graphics, int x, int y, int width, int height) {
			graphics.fill(x, y, x + width, y + 1, 0x80909090);
			graphics.fill(x, y, x + 1, y + height, 0x80909090);
			graphics.fill(x, y + height - 1, x + width, y + height, 0x80505050);
			graphics.fill(x + width - 1, y, x + width, y + height, 0x80505050);
		}
	}
}
