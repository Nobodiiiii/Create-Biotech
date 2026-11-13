package com.nobodiiiii.createbiotech.content.surgery.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.nobodiiiii.createbiotech.api.bionic.BionicStatFormula;

/** Bounded pure arithmetic compiled once per reload; no script engine or arbitrary Java access. */
public final class BionicStatExpression {
	private final String source;
	private final Set<String> variables;
	private int cursor, nodes, depth;
	private BionicStatExpression(String source, Set<String> variables) {
		if (source == null || source.isBlank() || source.length() > 4096)
			throw new IllegalArgumentException("Bionic expression must contain 1..4096 characters");
		this.source = source; this.variables = variables;
	}
	public static BionicStatFormula compile(String expression, Set<String> variables) {
		var parser = new BionicStatExpression(expression, variables);
		BionicStatFormula result = parser.conditional();
		parser.space();
		if (parser.cursor != expression.length()) throw parser.error("Unexpected token");
		return result;
	}
	private BionicStatFormula conditional() {
		if (++depth > 64) throw error("Expression is too deeply nested");
		BionicStatFormula condition = binary(0);
		if (take("?")) {
			BionicStatFormula yes = conditional();
			expect(":");
			BionicStatFormula no = conditional(), test = condition;
			condition = node(c -> truth(test.evaluate(c)) ? yes.evaluate(c) : no.evaluate(c));
		}
		depth--;
		return condition;
	}
	private static final String[][] OPERATORS = {
		{"||"}, {"&&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"+", "-"}, {"*", "/", "%"}
	};
	private BionicStatFormula binary(int precedence) {
		if (precedence == OPERATORS.length) return unary();
		BionicStatFormula result = binary(precedence + 1);
		while (true) {
			String found = null;
			for (String operator : OPERATORS[precedence]) if (take(operator)) { found = operator; break; }
			if (found == null) return result;
			String op = found;
			BionicStatFormula a = result, b = binary(precedence + 1);
			result = node(c -> {
				double x = a.evaluate(c);
				if (op.equals("&&")) return truth(x) && truth(b.evaluate(c)) ? 1 : 0;
				if (op.equals("||")) return truth(x) || truth(b.evaluate(c)) ? 1 : 0;
				double y = b.evaluate(c);
				return switch (op) {
					case "+" -> x + y; case "-" -> x - y; case "*" -> x * y;
					case "/" -> x / y; case "%" -> x % y;
					case "==" -> x == y ? 1 : 0; case "!=" -> x != y ? 1 : 0;
					case "<" -> x < y ? 1 : 0; case ">" -> x > y ? 1 : 0;
					case "<=" -> x <= y ? 1 : 0; case ">=" -> x >= y ? 1 : 0;
					default -> throw new IllegalStateException(op);
				};
			});
		}
	}
	private BionicStatFormula unary() {
		if (++depth > 64) throw error("Expression is too deeply nested");
		BionicStatFormula result;
		if (take("-")) { var value = unary(); result = node(c -> -value.evaluate(c)); }
		else if (take("+")) result = unary();
		else if (take("!")) { var value = unary(); result = node(c -> truth(value.evaluate(c)) ? 0 : 1); }
		else result = primary();
		depth--;
		return result;
	}
	private BionicStatFormula primary() {
		space();
		if (take("(")) { var value = conditional(); expect(")"); return value; }
		if (cursor >= source.length()) throw error("Expected a number, variable or function");
		char next = source.charAt(cursor);
		if (Character.isDigit(next) || next == '.') {
			int start = cursor++;
			while (cursor < source.length() && (Character.isDigit(source.charAt(cursor)) || source.charAt(cursor) == '.')) cursor++;
			if (cursor < source.length() && (source.charAt(cursor) == 'e' || source.charAt(cursor) == 'E')) {
				cursor++;
				if (cursor < source.length() && (source.charAt(cursor) == '+' || source.charAt(cursor) == '-')) cursor++;
				while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor++;
			}
			double value;
			try { value = Double.parseDouble(source.substring(start, cursor)); }
			catch (NumberFormatException e) { throw error("Invalid number"); }
			if (!Double.isFinite(value)) throw error("Non-finite literal");
			return node(c -> value);
		}
		int start = cursor;
		while (cursor < source.length() && (Character.isLetterOrDigit(source.charAt(cursor)) || source.charAt(cursor) == '_')) cursor++;
		if (start == cursor) throw error("Invalid token");
		String name = source.substring(start, cursor);
		if (take("(")) {
			List<BionicStatFormula> args = new ArrayList<>();
			if (!take(")")) {
				do { args.add(conditional()); } while (take(","));
				expect(")");
			}
			int count = switch (name) {
				case "min", "max", "pow" -> 2;
				case "clamp", "lerp" -> 3;
				case "abs", "sqrt", "cbrt", "log", "ln", "log1p", "exp", "floor", "ceil", "round", "sin", "cos" -> 1;
				default -> throw error("Unknown function " + name);
			};
			if (args.size() != count) throw error(name + " expects " + count + " arguments");
			return node(c -> {
				double a = args.getFirst().evaluate(c);
				return switch (name) {
					case "abs" -> Math.abs(a); case "sqrt" -> Math.sqrt(a); case "cbrt" -> Math.cbrt(a);
					case "log", "ln" -> Math.log(a); case "log1p" -> Math.log1p(a); case "exp" -> Math.exp(a);
					case "floor" -> Math.floor(a); case "ceil" -> Math.ceil(a); case "round" -> (double) Math.round(a);
					case "sin" -> Math.sin(a); case "cos" -> Math.cos(a);
					case "min" -> Math.min(a, args.get(1).evaluate(c)); case "max" -> Math.max(a, args.get(1).evaluate(c));
					case "pow" -> Math.pow(a, args.get(1).evaluate(c));
					case "clamp" -> Math.max(args.get(1).evaluate(c), Math.min(args.get(2).evaluate(c), a));
					case "lerp" -> { double b = args.get(1).evaluate(c); yield b + a * (args.get(2).evaluate(c) - b); }
					default -> throw new IllegalStateException(name);
				};
			});
		}
		if (name.equals("pi")) return node(c -> Math.PI);
		if (name.equals("e")) return node(c -> Math.E);
		if (!variables.contains(name)) throw error("Unknown variable " + name);
		return node(c -> c.get(name));
	}
	private BionicStatFormula node(BionicStatFormula formula) {
		if (++nodes > 256) throw error("Expression exceeds 256 nodes");
		return formula;
	}
	private boolean take(String token) {
		space();
		if (!source.startsWith(token, cursor)) return false;
		cursor += token.length(); return true;
	}
	private void expect(String token) { if (!take(token)) throw error("Expected " + token); }
	private void space() { while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++; }
	private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at character " + cursor); }
	private static boolean truth(double value) { return Double.isFinite(value) && value != 0; }
}
