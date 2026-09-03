package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Localised surgery-guide body assembled for the scrollable text field. */
@OnlyIn(Dist.CLIENT)
public final class SurgeryGuideText {
	private static final String PREFIX = "create_biotech.surgery_guide.";
	private static final List<Block> CONTENT = content();

	private SurgeryGuideText() {}

	public static String build() {
		StringBuilder text = new StringBuilder();
		Kind previous = null;
		for (Block block : CONTENT) {
			if (!text.isEmpty())
				text.append(previous == Kind.SUBHEADING ? "\n" : "\n\n");
			text.append(format(block));
			previous = block.kind();
		}
		return text.toString();
	}

	private static String format(Block block) {
		String content = Component.translatable(PREFIX + block.key()).getString();
		if (block.key().matches("quick_start\\.\\d+"))
			content = content.replaceFirst("^(\\d+\\.)\\s+", "$1");
		return switch (block.kind()) {
			case HEADING -> ChatFormatting.GOLD.toString() + ChatFormatting.BOLD
				+ "— " + content + " —" + ChatFormatting.RESET;
			case SUBHEADING -> ChatFormatting.WHITE.toString() + ChatFormatting.BOLD
				+ content + ChatFormatting.RESET;
			case BODY -> ChatFormatting.RESET + content;
		};
	}

	private static List<Block> content() {
		List<Block> out = new ArrayList<>();
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

	private static void entries(List<Block> out, String prefix, String... names) {
		for (String name : names) {
			out.add(new Block(Kind.SUBHEADING, prefix + "." + name));
			out.add(new Block(Kind.BODY, prefix + "." + name + ".desc"));
		}
	}

	private enum Kind {
		HEADING,
		SUBHEADING,
		BODY
	}

	private record Block(Kind kind, String key) {}
}
