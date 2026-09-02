package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Localised body of the surgery guide, flowed into book pages when the book is opened.
 * Laying it out against the live font instead of shipping fixed pages lets every
 * translation paginate on its own, since a page holds far fewer Latin words than
 * Chinese characters.
 */
@OnlyIn(Dist.CLIENT)
public final class SurgeryGuidePages {
	private static final String PREFIX = "create_biotech.surgery_guide.";
	/** Mirrors {@code BookViewScreen.TEXT_WIDTH}, which is protected. */
	private static final int PAGE_WIDTH = 114;
	/** {@code BookViewScreen} draws at most {@code TEXT_HEIGHT / 9} lines of a page. */
	private static final int PAGE_LINES = 128 / 9;
	/** Lines a subheading needs below it before it is allowed to sit at the foot of a page. */
	private static final int SUBHEADING_LOOKAHEAD = 2;

	private static final Style TITLE_STYLE = Style.EMPTY.withBold(true)
		.withColor(TextColor.fromRgb(0x2E3A57));
	private static final Style HEADING_STYLE = TITLE_STYLE.withUnderlined(true);
	private static final Style SUBHEADING_STYLE = Style.EMPTY.withBold(true);

	private static final List<Block> CONTENT = content();

	private SurgeryGuidePages() {}

	/** Splits the guide into pages that {@code BookViewScreen} can render one at a time. */
	public static List<Component> build(Font font) {
		StringSplitter splitter = font.getSplitter();
		Layout layout = new Layout();
		Kind previous = null;

		for (Block block : CONTENT) {
			List<Component> lines = wrap(splitter, block);

			// Every top-level section opens a fresh page.
			if (block.kind() == Kind.HEADING) {
				layout.breakPage();
				previous = null;
			}

			int gap = layout.isEmpty() || previous == Kind.SUBHEADING ? 0 : 1;
			int reserved = block.kind() == Kind.SUBHEADING ? SUBHEADING_LOOKAHEAD : 0;

			if (layout.used() + gap + lines.size() + reserved > PAGE_LINES) {
				// Running a paragraph over the page edge is only worth it to keep a heading
				// from being stranded above it, or when it is too tall for any page at all.
				boolean anchored = previous == Kind.HEADING || previous == Kind.SUBHEADING;
				if (anchored || lines.size() > PAGE_LINES) {
					if (gap > 0)
						layout.blank();
					layout.flow(lines);
					previous = block.kind();
					continue;
				}
				layout.breakPage();
				gap = 0;
			}

			if (gap > 0)
				layout.blank();
			layout.add(lines);
			previous = block.kind();
		}

		return layout.finish();
	}

	/** Collects lines into book pages, holding the page being filled. */
	private static final class Layout {
		private final List<Component> pages = new ArrayList<>();
		private final List<Component> page = new ArrayList<>();

		boolean isEmpty() {
			return page.isEmpty();
		}

		int used() {
			return page.size();
		}

		void blank() {
			page.add(Component.empty());
		}

		void add(List<Component> lines) {
			page.addAll(lines);
		}

		void breakPage() {
			if (page.isEmpty())
				return;
			pages.add(join(page));
			page.clear();
		}

		/**
		 * Runs lines past the page edge, then spreads what is left as evenly as the page
		 * count allows, so the tail never ends up alone on a page of its own.
		 */
		void flow(List<Component> lines) {
			if (page.size() >= PAGE_LINES)
				breakPage();
			int taken = Math.min(lines.size(), PAGE_LINES - page.size());
			page.addAll(lines.subList(0, taken));
			List<Component> rest = lines.subList(taken, lines.size());
			breakPage();

			int chunks = Math.max(1, (rest.size() + PAGE_LINES - 1) / PAGE_LINES);
			int from = 0;
			for (int chunk = 0; chunk < chunks; chunk++) {
				List<Component> slice = rest.subList(from, from + (rest.size() - from) / (chunks - chunk));
				from += slice.size();
				page.addAll(slice);
				// The last slice stays open so the next block can share the page.
				if (chunk < chunks - 1)
					breakPage();
			}
		}

		List<Component> finish() {
			breakPage();
			return List.copyOf(pages);
		}
	}

	private static List<Component> wrap(StringSplitter splitter, Block block) {
		List<Component> lines = new ArrayList<>();
		for (FormattedText line : splitter.splitLines(block.styled(), PAGE_WIDTH, Style.EMPTY))
			lines.add(toComponent(line));
		if (lines.isEmpty())
			lines.add(Component.empty());
		return lines;
	}

	/** Rebuilds a wrapped line as a component, keeping the styles the splitter carried over. */
	private static Component toComponent(FormattedText line) {
		MutableComponent rebuilt = Component.empty();
		line.visit((style, content) -> {
			rebuilt.append(Component.literal(content).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return rebuilt;
	}

	private static Component join(List<Component> lines) {
		MutableComponent page = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0)
				page.append("\n");
			page.append(lines.get(i));
		}
		return page;
	}

	private static List<Block> content() {
		List<Block> out = new ArrayList<>();
		out.add(new Block(Kind.TITLE, "title"));
		numbered(out, "intro", 4);

		out.add(new Block(Kind.HEADING, "quick_start"));
		numbered(out, "quick_start", 7);

		out.add(new Block(Kind.HEADING, "basics"));
		numbered(out, "basics", 4);

		out.add(new Block(Kind.HEADING, "tools"));
		out.add(new Block(Kind.BODY, "tools.note"));
		entries(out, "tools", "shears", "shovel", "glue", "honey", "slime_ball", "symmetry",
			"wrench", "temporary_box", "empty_box");
		out.add(new Block(Kind.BODY, "tools.cost"));

		out.add(new Block(Kind.BODY, "joints.intro"));
		entries(out, "joints", "neck", "shoulder", "elbow", "hip", "knee");
		out.add(new Block(Kind.BODY, "joints.rules"));
		out.add(new Block(Kind.BODY, "joints.animation"));

		out.add(new Block(Kind.HEADING, "stats"));
		numbered(out, "stats", 2);
		out.add(new Block(Kind.SUBHEADING, "stats.inspect"));
		numbered(out, "stats.inspect", 2);

		return List.copyOf(out);
	}

	private static void numbered(List<Block> out, String prefix, int count) {
		for (int i = 1; i <= count; i++)
			out.add(new Block(Kind.BODY, prefix + "." + i));
	}

	/** Adds the former table rows as a subheading per entry followed by its description. */
	private static void entries(List<Block> out, String prefix, String... names) {
		for (String name : names) {
			out.add(new Block(Kind.SUBHEADING, prefix + "." + name));
			out.add(new Block(Kind.BODY, prefix + "." + name + ".desc"));
		}
	}

	private enum Kind {
		TITLE,
		HEADING,
		SUBHEADING,
		BODY
	}

	private record Block(Kind kind, String key) {
		Component styled() {
			Component text = Component.translatable(PREFIX + key);
			return switch (kind) {
				case TITLE -> text.copy().withStyle(TITLE_STYLE);
				case HEADING -> text.copy().withStyle(HEADING_STYLE);
				case SUBHEADING -> text.copy().withStyle(SUBHEADING_STYLE);
				case BODY -> text;
			};
		}
	}
}
