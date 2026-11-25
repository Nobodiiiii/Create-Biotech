package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;

import net.minecraft.network.chat.MutableComponent;

/** Immutable values shared by variables and collection entries during execution. */
sealed interface ComputerValue permits ComputerValue.Scalar, ComputerValue.ListValue, ComputerValue.MapValue, ComputerValue.Missing {

	Bounds bounds();

	enum Missing implements ComputerValue {
		INSTANCE;

		@Override public Bounds bounds() { return Scalar.BOUNDS; }
	}

	record Scalar(String text, DataType type, List<ComputerText.Part> displayParts) implements ComputerValue {
		private static final Bounds BOUNDS = new Bounds(1, 0);
		private static final Pattern NUMERAL = Pattern.compile(
			"([+-]?(?:(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)"
				+ "(\\s*[%°\\p{L}][%°\\p{L}0-9/²³]*)?");

		Scalar(String text) {
			this(text, DataType.TEXT);
		}

		Scalar(String text, DataType type) {
			this(text, type, List.of());
		}

		public Scalar {
			text = ComputerRuntime.clampValue(text);
			displayParts = List.copyOf(displayParts);
		}

		@Override public Bounds bounds() { return BOUNDS; }

		static double parseNumber(String text, boolean allowUnit) {
			var match = NUMERAL.matcher(text.strip());
			if (!match.matches() || (!allowUnit && match.group(2) != null))
				return Double.NaN;
			double value = Double.parseDouble(match.group(1).replace(",", ""));
			return Double.isFinite(value) ? value : Double.NaN;
		}
	}

	record ListValue(List<ComputerValue> entries, Bounds bounds) implements ComputerValue {
		ListValue(List<ComputerValue> entries) {
			this(List.copyOf(entries), measure(entries));
		}
	}

	record MapValue(Map<String, Entry> entries, Bounds bounds) implements ComputerValue {
		MapValue(Map<String, Entry> entries) {
			this(Collections.unmodifiableMap(new LinkedHashMap<>(entries)),
				measure(entries.values().stream().map(Entry::value).toList()));
		}

		record Entry(Scalar key, ComputerValue value) {}
	}

	default double tryNumber(boolean allowUnit) {
		if (!(this instanceof Scalar scalar))
			return Double.NaN;
		String text = scalar.text.strip();
		if (text.equalsIgnoreCase("true"))
			return 1;
		if (text.equalsIgnoreCase("false"))
			return 0;
		return Scalar.parseNumber(text, allowUnit);
	}

	default double asNumber() {
		double number = tryNumber(true);
		return Double.isFinite(number) ? number : 0;
	}

	default boolean asBoolean() {
		return switch (this) {
			case Scalar ignored -> asNumber() != 0;
			case ListValue list -> !list.entries.isEmpty();
			case MapValue map -> !map.entries.isEmpty();
			case Missing ignored -> false;
		};
	}

	default List<ComputerValue> asList() {
		return switch (this) {
			case Scalar scalar -> List.of(scalar);
			case ListValue list -> list.entries;
			case MapValue map -> map.entries.values().stream()
				.<ComputerValue>map(entry -> new ListValue(List.of(entry.key(), entry.value())))
				.toList();
			case Missing ignored -> List.of();
		};
	}

	default Map<String, MapValue.Entry> asMap() {
		if (this instanceof MapValue map)
			return map.entries;
		if (!(this instanceof ListValue list))
			return Map.of();
		Map<String, MapValue.Entry> entries = new LinkedHashMap<>();
		for (ComputerValue entry : list.entries) {
			if (!(entry instanceof ListValue pair) || pair.entries.size() != 2)
				return Map.of();
			Scalar key = pair.entries.getFirst().asScalar();
			entries.put(key.text(), new MapValue.Entry(key, pair.entries.getLast()));
		}
		return entries;
	}

	default boolean equivalent(ComputerValue other) {
		if (this instanceof Scalar first && other instanceof Scalar second) {
			double a = first.tryNumber(true);
			double b = second.tryNumber(true);
			return Double.isFinite(a) && Double.isFinite(b) ? a == b : first.text.equalsIgnoreCase(second.text);
		}
		if (this instanceof ListValue first && other instanceof ListValue second) {
			if (first.entries.size() != second.entries.size())
				return false;
			for (int i = 0; i < first.entries.size(); i++)
				if (!first.entries.get(i).equivalent(second.entries.get(i)))
					return false;
			return true;
		}
		if (this instanceof MapValue first && other instanceof MapValue second)
			return first.entries.keySet().equals(second.entries.keySet())
				&& first.entries.entrySet().stream().allMatch(entry ->
					entry.getValue().value().equivalent(second.entries.get(entry.getKey()).value()));
		return this == other;
	}

	default String asText() {
		if (this instanceof Scalar scalar)
			return scalar.text;
		if (this == Missing.INSTANCE)
			return "";
		ComputerText text = new ComputerText(false);
		appendText(text);
		return text.text();
	}

	default Scalar asScalar() {
		if (this instanceof Scalar scalar)
			return scalar;
		ComputerText text = new ComputerText(true);
		appendText(text);
		return text.scalar();
	}

	default Scalar toText() {
		if (this instanceof Scalar scalar)
			return scalar.type() == DataType.TEXT ? scalar : new Scalar(scalar.text());
		ComputerText text = new ComputerText(true);
		appendText(text, CollectionFormat.COMPACT);
		return text.scalar();
	}

	default MutableComponent asDisplay() {
		return asDisplay(CollectionFormat.STRUCTURED);
	}

	default MutableComponent asDisplayLine() {
		return asDisplay(CollectionFormat.INLINE);
	}

	default List<MutableComponent> asDisplayLines(int maxLines, boolean quantityFirst) {
		return switch (this) {
			case ListValue list -> list.entries.stream()
				.skip(Math.max(0, list.entries.size() - maxLines))
				.map(ComputerValue::asDisplay)
				.toList();
			case MapValue map -> map.entries.values().stream()
				.skip(Math.max(0, map.entries.size() - maxLines))
				.map(entry -> displayEntry(entry, quantityFirst))
				.toList();
			default -> List.of(asDisplay());
		};
	}

	private static MutableComponent displayEntry(MapValue.Entry entry, boolean quantityFirst) {
		ComputerText text = new ComputerText(true);
		if (quantityFirst) {
			entry.value().appendText(text);
			text.append(" ");
			text.append(entry.key());
		} else {
			appendEntry(text, entry, CollectionFormat.INLINE);
		}
		return text.component();
	}

	private MutableComponent asDisplay(CollectionFormat format) {
		ComputerText text = new ComputerText(true);
		appendText(text, format);
		return text.component();
	}

	static Scalar join(List<ComputerValue> values, ComputerValue separator) {
		ComputerText text = new ComputerText(true);
		for (int i = 0; i < values.size() && !text.isFull(); i++) {
			if (i > 0)
				separator.appendText(text);
			values.get(i).appendText(text);
		}
		return text.scalar();
	}

	private void appendText(ComputerText text) {
		appendText(text, CollectionFormat.STRUCTURED);
	}

	private void appendText(ComputerText text, CollectionFormat format) {
		switch (this) {
			case Missing ignored -> {}
			case Scalar scalar -> text.append(scalar);
			case ListValue list -> {
				if (format.brackets)
					text.append("[");
				for (int i = 0; i < list.entries.size() && !text.isFull(); i++) {
					if (i > 0)
						text.append(format.separator);
					list.entries.get(i).appendText(text, format.nested());
				}
				if (format.brackets)
					text.append("]");
			}
			case MapValue map -> {
				if (format.brackets)
					text.append("{");
				boolean first = true;
				for (MapValue.Entry entry : map.entries.values()) {
					if (text.isFull())
						break;
					if (!first)
						text.append(format.separator);
					appendEntry(text, entry, format);
					first = false;
				}
				if (format.brackets)
					text.append("}");
			}
		}
	}

	private static void appendEntry(ComputerText text, MapValue.Entry entry, CollectionFormat format) {
		text.append(entry.key());
		text.append(format.keyValueSeparator);
		entry.value().appendText(text, format.nested());
	}

	enum CollectionFormat {
		STRUCTURED(", ", "=", true),
		COMPACT(",", "=", true),
		INLINE(" ", "→", false);

		private final String separator;
		private final String keyValueSeparator;
		private final boolean brackets;

		CollectionFormat(String separator, String keyValueSeparator, boolean brackets) {
			this.separator = separator;
			this.keyValueSeparator = keyValueSeparator;
			this.brackets = brackets;
		}

		private CollectionFormat nested() {
			return this == INLINE ? STRUCTURED : this;
		}
	}

	private static Bounds measure(Collection<ComputerValue> entries) {
		int size = 1;
		int depth = 1;
		for (ComputerValue entry : entries) {
			Bounds bounds = entry.bounds();
			size += bounds.size;
			depth = Math.max(depth, bounds.depth + 1);
		}
		if (size > ComputerRuntime.MAX_ENTRIES * ComputerProgram.MAX_DEPTH || depth > ComputerProgram.MAX_DEPTH)
			throw new LimitExceeded();
		return new Bounds(size, depth);
	}

	record Bounds(int size, int depth) {}

	final class LimitExceeded extends RuntimeException {
		LimitExceeded() {
			super(null, null, false, false);
		}
	}
}
