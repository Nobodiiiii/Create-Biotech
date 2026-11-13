package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Map;

/** Immutable, pure and thread-safe; shared by all bodies and both gameplay and previews. */
@FunctionalInterface
public interface BionicStatFormula {
	double evaluate(Context context);

	record Context(Map<String, Double> inputs, Map<String, Double> parameters) {
		public Context { inputs = Map.copyOf(inputs); parameters = Map.copyOf(parameters); }
		public double get(String name) {
			Double value = inputs.get(name);
			if (value == null) value = parameters.get(name);
			if (value == null) throw new IllegalArgumentException("Unknown bionic stat variable: " + name);
			return value;
		}
	}
}
