package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Shows independently collapsible chapters in Create's train-schedule frame. */
@OnlyIn(Dist.CLIENT)
public class SurgeryGuideScreen extends AbstractSimiScreen {
	private static final AllGuiTextures BACKGROUND = AllGuiTextures.SCHEDULE;
	private static final int FIELD_X = 18;
	private static final int FIELD_Y = 18;
	private static final int FIELD_WIDTH = 216;
	private static final int FIELD_HEIGHT = 169;
	/** Keep the current layout on resize; a newly opened guide starts fully collapsed. */
	private final Set<Integer> expandedChapters = new HashSet<>();
	private double rememberedScrollAmount;

	private GuideContents contents;

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

		contents = new GuideContents(guiLeft + FIELD_X, guiTop + FIELD_Y);
		contents.restoreScrollPosition(rememberedScrollAmount);
		addRenderableWidget(contents);

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
		if (contents != null)
			rememberedScrollAmount = contents.scrollPosition();
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

	private class GuideContents extends AbstractScrollWidget {
		private static final int BUTTON_SIZE = 18;
		private static final int CHAPTER_GAP = 4;
		private final List<ChapterView> chapters = new ArrayList<>();
		private int contentHeight;
		private int focusedChapter;
		private GuideTextField focusedBody;

		GuideContents(int x, int y) {
			super(x, y, FIELD_WIDTH, FIELD_HEIGHT, title);
			for (SurgeryGuideText.Chapter chapter : SurgeryGuideText.chapters()) {
				List<FormattedCharSequence> heading = font.split(chapter.title().copy()
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					width - totalInnerPadding() - BUTTON_SIZE - 4);
				GuideTextField body = new GuideTextField(font, x + innerPadding(), y,
					width - totalInnerPadding(), FIELD_HEIGHT, chapter.title());
				body.setValue(chapter.body());
				body.fitToText();
				chapters.add(new ChapterView(chapter.title(), heading, body,
					Math.max(BUTTON_SIZE, heading.size() * font.lineHeight + 6)));
			}
			layoutChapters();
		}

		private void layoutChapters() {
			int y = innerPadding();
			for (int i = 0; i < chapters.size(); i++) {
				ChapterView chapter = chapters.get(i);
				chapter.top = y;
				y += chapter.headerHeight;
				chapter.body.setY(getY() + y);
				if (expandedChapters.contains(i))
					y += chapter.body.getHeight();
				y += CHAPTER_GAP;
			}
			contentHeight = y;
			setScrollAmount(scrollAmount());
		}

		private void toggleChapter(int index) {
			if (!expandedChapters.add(index))
				expandedChapters.remove(index);
			focusBody(null);
			focusedChapter = index;
			layoutChapters();
			playDownSound(minecraft.getSoundManager());
		}

		private void focusBody(GuideTextField body) {
			if (focusedBody != null)
				focusedBody.setFocused(false);
			focusedBody = body;
			if (body != null)
				body.setFocused(true);
		}

		@Override
		public void setFocused(boolean focused) {
			super.setFocused(focused);
			if (focusedBody != null)
				focusedBody.setFocused(focused);
		}

		double scrollPosition() {
			return scrollAmount();
		}

		void restoreScrollPosition(double amount) {
			setScrollAmount(amount);
		}

		@Override
		protected int getInnerHeight() {
			return contentHeight;
		}

		@Override
		protected double scrollRate() {
			return font.lineHeight * 2;
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			int left = getX() + innerPadding();
			int right = getX() + width - innerPadding();
			for (int i = 0; i < chapters.size(); i++) {
				ChapterView chapter = chapters.get(i);
				int top = getY() + chapter.top;
				if (withinContentAreaTopBottom(top, top + chapter.headerHeight)) {
					boolean hovered = withinContentAreaPoint(mouseX, mouseY)
						&& mouseY + scrollAmount() >= top
						&& mouseY + scrollAmount() < top + chapter.headerHeight;
					boolean focused = isFocused() && focusedBody == null && focusedChapter == i;
					graphics.fill(left, top, right, top + chapter.headerHeight,
						hovered || focused ? 0x80505050 : 0x40303030);
					int textY = top + (chapter.headerHeight - chapter.heading.size() * font.lineHeight) / 2;
					for (FormattedCharSequence line : chapter.heading) {
						graphics.drawString(font, line, left + 2, textY, 0xFFFFFF);
						textY += font.lineHeight;
					}
					int buttonX = right - BUTTON_SIZE;
					int buttonY = top + (chapter.headerHeight - BUTTON_SIZE) / 2;
					(hovered || focused ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON)
						.render(graphics, buttonX, buttonY);
					String symbol = expandedChapters.contains(i) ? "-" : "+";
					graphics.drawString(font, symbol, buttonX + (BUTTON_SIZE - font.width(symbol)) / 2,
						buttonY + (BUTTON_SIZE - font.lineHeight) / 2, 0x373737, false);
				}
				if (expandedChapters.contains(i)
					&& withinContentAreaTopBottom(chapter.body.getY(), chapter.body.getY() + chapter.body.getHeight()))
					chapter.body.renderText(graphics, mouseX, mouseY, partialTicks);
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (visible && active && button == 0 && withinContentAreaPoint(mouseX, mouseY)) {
				double contentY = mouseY + scrollAmount();
				for (int i = 0; i < chapters.size(); i++) {
					ChapterView chapter = chapters.get(i);
					int top = getY() + chapter.top;
					if (contentY >= top && contentY < top + chapter.headerHeight) {
						toggleChapter(i);
						return true;
					}
					if (expandedChapters.contains(i) && chapter.body.isMouseOver(mouseX, contentY)) {
						focusBody(chapter.body);
						focusedChapter = i;
						return chapter.body.mouseClicked(mouseX, contentY, button);
					}
				}
				focusBody(null);
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
			if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY))
				return true;
			return focusedBody != null && withinContentAreaPoint(mouseX, mouseY)
				&& focusedBody.mouseDragged(mouseX, mouseY + scrollAmount(), button, dragX, dragY);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (focusedBody != null)
				return focusedBody.keyPressed(keyCode, scanCode, modifiers);
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| keyCode == GLFW.GLFW_KEY_SPACE) {
				toggleChapter(focusedChapter);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
				focusedChapter = Math.clamp(focusedChapter + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1),
					0, chapters.size() - 1);
				ChapterView chapter = chapters.get(focusedChapter);
				if (chapter.top < scrollAmount())
					setScrollAmount(chapter.top);
				else if (chapter.top + chapter.headerHeight > scrollAmount() + height - innerPadding())
					setScrollAmount(chapter.top + chapter.headerHeight - height + innerPadding());
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, chapters.get(focusedChapter).title);
			output.add(NarratedElementType.USAGE, Component.translatable("create_biotech.surgery_guide."
				+ (expandedChapters.contains(focusedChapter) ? "collapse" : "expand")));
		}

		@Override
		protected void renderBackground(GuiGraphics graphics) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x60303030);
			graphics.renderOutline(getX(), getY(), width, height, 0x80909090);
		}
	}

	private static class ChapterView {
		final Component title;
		final List<FormattedCharSequence> heading;
		final GuideTextField body;
		final int headerHeight;
		int top;

		ChapterView(Component title, List<FormattedCharSequence> heading, GuideTextField body, int headerHeight) {
			this.title = title;
			this.heading = heading;
			this.body = body;
			this.headerHeight = headerHeight;
		}
	}

	/** Keeps guide text selectable and copyable while preventing accidental edits. */
	private static class GuideTextField extends AbstractWidget {
		private static final int PADDING = 4;
		private final Font font;
		private final GuideSelection selection;
		private List<SurgeryGuideTextLayout.Line> lines = List.of();

		GuideTextField(Font font, int x, int y, int width, int height, Component message) {
			super(x, y, width, height, message);
			this.font = font;
			selection = new GuideSelection(font, width - PADDING * 2);
		}

		void setValue(String text) {
			selection.setValue(text);
			lines = SurgeryGuideTextLayout.wrap(text, width - PADDING * 2, font.getSplitter());
		}

		void fitToText() {
			setHeight(lines.size() * font.lineHeight + PADDING * 2);
		}

		void renderText(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			// The enclosing widget supplies clipping and scrolling for every chapter.
			int x = getX() + PADDING;
			int y = getY() + PADDING;
			String text = selection.value();
			for (SurgeryGuideTextLayout.Line line : lines) {
				graphics.drawString(font, line.contents(text), x, y, 0xE0E0E0);
				int start = Math.max(line.start(), selection.selectionStart());
				int end = Math.min(line.end(), selection.selectionEnd());
				if (start < end) {
					int left = x + font.width(line.contents(text, start));
					int right = x + font.width(line.contents(text, end));
					graphics.fill(RenderType.guiTextHighlight(), left, y, right, y + font.lineHeight, 0xFF0000FF);
				}
				y += font.lineHeight;
			}
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			renderText(graphics, mouseX, mouseY, partialTicks);
		}

		private int cursorAt(int lineIndex, int x) {
			SurgeryGuideTextLayout.Line line = lines.get(Math.clamp(lineIndex, 0, lines.size() - 1));
			String text = selection.value().substring(line.start(), line.end());
			return line.start() + font.getSplitter().formattedIndexByWidth(text, Math.max(0, x), line.style());
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button != 0 || !isMouseOver(mouseX, mouseY))
				return false;
			selection.setSelecting(Screen.hasShiftDown());
			selection.seekCursor(Whence.ABSOLUTE,
				cursorAt((int) (mouseY - getY() - PADDING) / font.lineHeight, (int) (mouseX - getX() - PADDING)));
			return true;
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
			if (button != 0 || !isMouseOver(mouseX, mouseY))
				return false;
			selection.setSelecting(true);
			selection.seekCursor(Whence.ABSOLUTE,
				cursorAt((int) (mouseY - getY() - PADDING) / font.lineHeight, (int) (mouseX - getX() - PADDING)));
			return true;
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
			if (!Screen.hasControlDown() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
				|| keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END)) {
				int current = 0;
				while (current + 1 < lines.size() && selection.cursor() > lines.get(current).end())
					current++;
				SurgeryGuideTextLayout.Line line = lines.get(current);
				int cursor = switch (keyCode) {
					case GLFW.GLFW_KEY_HOME -> line.start();
					case GLFW.GLFW_KEY_END -> line.end();
					default -> cursorAt(current + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1),
						font.width(line.contents(selection.value(), selection.cursor())));
				};
				selection.setSelecting(Screen.hasShiftDown());
				selection.seekCursor(Whence.ABSOLUTE, cursor);
				return true;
			}
			return selection.keyPressed(keyCode);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE,
				Component.translatable("gui.narrate.editBox", getMessage(), selection.value()));
		}
	}

	/** Uses vanilla selection and clipboard handling without its whitespace-only line layout. */
	private static class GuideSelection extends MultilineTextField {
		GuideSelection(Font font, int width) {
			super(font, width);
		}

		int selectionStart() {
			return getSelected().beginIndex();
		}

		int selectionEnd() {
			return getSelected().endIndex();
		}
	}
}
