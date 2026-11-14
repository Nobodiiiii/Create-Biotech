package com.nobodiiiii.createbiotech.content.surgery.client;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Unicode line boundaries with Minecraft glyph widths and original selection offsets. */
@OnlyIn(Dist.CLIENT)
public final class SurgeryGuideTextLayout {
	private SurgeryGuideTextLayout() {}

	public static List<Line> wrap(String text, int width, StringSplitter splitter) {
		List<Glyph> glyphs = new ArrayList<>();
		StringBuilder visible = new StringBuilder();
		StringDecomposer.iterateFormatted(text, Style.EMPTY, (index, style, codePoint) -> {
			glyphs.add(new Glyph(index, visible.length(), codePoint, style,
				splitter.stringWidth(FormattedCharSequence.codepoint(codePoint, style))));
			visible.appendCodePoint(codePoint);
			return true;
		});
		BreakIterator boundaries = BreakIterator.getLineInstance(Locale.ROOT);
		boundaries.setText(visible.toString());
		List<Line> lines = new ArrayList<>();
		int start = 0;
		int rawStart = 0;
		while (start < glyphs.size()) {
			float advance = 0;
			int lastBoundary = -1;
			int end = start;
			while (end < glyphs.size()) {
				Glyph glyph = glyphs.get(end);
				if (glyph.codePoint == '\n')
					break;
				if (end > start && boundaries.isBoundary(glyph.visibleIndex))
					lastBoundary = end;
				if (end > start && advance + glyph.width > Math.max(1, width)) {
					if (lastBoundary > start)
						end = lastBoundary;
					break;
				}
				advance += glyph.width;
				end++;
			}
			int rawEnd = end < glyphs.size() ? glyphs.get(end).rawIndex : text.length();
			lines.add(new Line(rawStart, rawEnd, glyphs.get(start).style));
			if (end < glyphs.size() && glyphs.get(end).codePoint == '\n') {
				rawStart = rawEnd + 1;
				start = end + 1;
			} else {
				rawStart = rawEnd;
				start = end;
			}
		}
		if (lines.isEmpty() || text.endsWith("\n"))
			lines.add(new Line(text.length(), text.length(), Style.EMPTY));
		return List.copyOf(lines);
	}

	public record Line(int start, int end, Style style) {
		public FormattedCharSequence contents(String text) {
			return contents(text, end);
		}

		public FormattedCharSequence contents(String text, int until) {
			String value = text.substring(start, until);
			return sink -> StringDecomposer.iterateFormatted(value, 0, style, Style.EMPTY, sink);
		}
	}

	private record Glyph(int rawIndex, int visibleIndex, int codePoint, Style style, float width) {}
}
