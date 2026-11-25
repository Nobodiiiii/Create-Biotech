package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;

/** Immutable values shared by variables and collection entries during execution. */
sealed interface ComputerValue permits ComputerValue.Scalar, ComputerValue.ListValue, ComputerValue.MapValue, ComputerValue.Missing {

	Bounds bounds();

	enum Missing implements ComputerValue {
		INSTANCE;

		@Override public Bounds bounds() { return Scalar.BOUNDS; }
	}

	record Scalar(String text, DataType type) implements ComputerValue {
		private static final Bounds BOUNDS = new Bounds(1, 0);
		private static final Pattern NUMERAL = Pattern.compile(
			"([+-]?(?:(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)"
				+ "(\\s*[%°\\p{L}][%°\\p{L}0-9/²³]*)?");

		Scalar(String text) {
			this(text, DataType.TEXT);
		}

		public Scalar {
			text = ComputerRuntime.clampValue(text);
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

	record MapValue(Map<String, ComputerValue> entries, Bounds bounds) implements ComputerValue {
		MapValue(Map<String, ComputerValue> entries) {
			this(Collections.unmodifiableMap(new LinkedHashMap<>(entries)), measure(entries.values()));
		}
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
			case MapValue map -> map.entries.entrySet().stream()
				.<ComputerValue>map(entry -> new ListValue(List.of(new Scalar(entry.getKey()), entry.getValue())))
				.toList();
			case Missing ignored -> List.of();
		};
	}

	default Map<String, ComputerValue> asMap() {
		if (this instanceof MapValue map)
			return map.entries;
		if (!(this instanceof ListValue list))
			return Map.of();
		Map<String, ComputerValue> entries = new LinkedHashMap<>();
		for (ComputerValue entry : list.entries) {
			if (!(entry instanceof ListValue pair) || pair.entries.size() != 2)
				return Map.of();
			entries.put(pair.entries.getFirst().asText(), pair.entries.getLast());
		}
		return entries;
	}

	default boolean equivalent(ComputerValue other) {
		if (this instanceof Scalar first && other instanceof Scalar second) {
			if (first.type == DataType.VARIABLE_NAME || second.type == DataType.VARIABLE_NAME)
				return first.type == second.type && first.text.strip().equals(second.text.strip());
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
				&& first.entries.entrySet().stream().allMatch(entry -> entry.getValue().equivalent(second.entries.get(entry.getKey())));
		return this == other;
	}

	default String asText() {
		if (this instanceof Scalar scalar)
			return scalar.text;
		if (this == Missing.INSTANCE)
			return "";
		StringBuilder text = new StringBuilder();
		append(this, text);
		return ComputerRuntime.clampValue(text.toString());
	}

	private static void append(ComputerValue value, StringBuilder text) {
		switch (value) {
			case Missing ignored -> {}
			case Scalar scalar -> text.append(scalar.text);
			case ListValue list -> {
				text.append('[');
				for (int i = 0; i < list.entries.size() && text.length() < ComputerRuntime.MAX_VALUE_LENGTH; i++) {
					if (i > 0)
						text.append(", ");
					append(list.entries.get(i), text);
				}
				text.append(']');
			}
			case MapValue map -> {
				text.append('{');
				boolean first = true;
				for (Map.Entry<String, ComputerValue> entry : map.entries.entrySet()) {
					if (text.length() >= ComputerRuntime.MAX_VALUE_LENGTH)
						break;
					if (!first)
						text.append(", ");
					text.append(entry.getKey()).append('=');
					append(entry.getValue(), text);
					first = false;
				}
				text.append('}');
			}
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
