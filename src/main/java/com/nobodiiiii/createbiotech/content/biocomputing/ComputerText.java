package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerValue.Scalar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Builds bounded text while retaining whole translated parts for display. */
final class ComputerText {
	private final StringBuilder text = new StringBuilder();
	@Nullable
	private final List<Part> parts;
	private boolean translated;
	private boolean clipped;

	ComputerText(boolean forDisplay) {
		parts = forDisplay ? new ArrayList<>() : null;
	}

	boolean isFull() {
		return clipped || text.length() >= ComputerRuntime.MAX_VALUE_LENGTH;
	}

	void append(String value) {
		append(value, null);
	}

	void append(Scalar scalar) {
		if (isFull() || scalar.text().isEmpty())
			return;
		if (parts == null) {
			append(scalar.text());
			return;
		}
		if (!scalar.displayParts().isEmpty()) {
			for (Part part : scalar.displayParts())
				append(part.text(), part.display());
			return;
		}
		Component display = switch (scalar.type()) {
			case ITEM -> ComputerItemReference.displayName(scalar.text());
			case BOOLEAN -> Component.translatable("create_biotech.biocomputing.scratch.boolean." + scalar.text());
			case NUMBER, TEXT -> null;
		};
		append(scalar.text(), display);
	}

	private void append(String value, @Nullable Component display) {
		if (value.isEmpty() || isFull())
			return;
		int end = Math.min(value.length(), ComputerRuntime.MAX_VALUE_LENGTH - text.length());
		if (end < value.length()) {
			clipped = true;
			if (Character.isHighSurrogate(value.charAt(end - 1)))
				end--;
			display = null;
		}
		String fragment = value.substring(0, end);
		text.append(fragment);
		if (parts != null && !fragment.isEmpty()) {
			parts.add(new Part(fragment, display));
			translated |= display != null;
		}
	}

	String text() {
		return text.toString();
	}

	Scalar scalar() {
		return new Scalar(text(), DataType.TEXT, translated ? parts : List.of());
	}

	MutableComponent component() {
		if (parts == null || !translated)
			return Component.literal(text());
		MutableComponent result = Component.empty();
		for (Part part : parts)
			result.append(part.display() == null ? Component.literal(part.text()) : part.display().copy());
		return result;
	}

	record Part(String text, @Nullable Component display) {}
}
