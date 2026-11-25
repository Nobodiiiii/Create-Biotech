package com.nobodiiiii.createbiotech.content.biocomputing.client;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Shape;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Slot;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchBlockArt.Region;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Datum;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Element;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Tile;

/** Editor silhouettes for preferred input types and the known results of value blocks. */
enum ScratchValueShape {
	ANY(Region.ANY), NUMBER(Region.NUMBER), TEXT(Region.TEXT), BOOLEAN(Region.BOOLEAN),
	ITEM(Region.ITEM), LIST(Region.LIST), MAP(Region.MAP);

	final Region region;

	ScratchValueShape(Region region) { this.region = region; }

	static ScratchValueShape of(Element element) {
		return switch (element) {
			case Datum datum -> of(datum.type);
			case Tile tile -> switch (tile.op) {
				case SUBTRACT, MULTIPLY, DIVIDE, MOD, ROUND, FLOOR, CEIL, ABS, MAP_RANGE, CLAMP, MIN, MAX, POWER, SQRT,
					SIN, COS, TAN, RANDOM, PACK_BITS, LENGTH, PARSE_NUMBER, LIST_LENGTH, MAP_LENGTH, READ_REDSTONE, TIMER,
					STOCK_COUNT, STOCK_TOTAL, STOCK_TYPES, TO_NUMBER -> NUMBER;
				case PICK_TEXT, JOIN, SUBSTRING, REPLACE_TEXT, FORMAT_NUMBER, PROGRESS_BAR, LIST_JOIN, READ_INPUT, TO_TEXT -> TEXT;
				case TO_ITEM -> ITEM;
				case LIST, TO_LIST -> LIST;
				case MAP, TO_MAP -> MAP;
				default -> tile.op.shape() == Shape.BOOLEAN ? BOOLEAN : ANY;
			};
		};
	}

	static ScratchValueShape forInput(Op op, int index) {
		Slot slot = op.slot(index);
		return switch (slot.socket()) {
			case CHANNEL, SLOT -> NUMBER;
			case CONDITION -> op == Op.TO_BOOLEAN ? ANY : BOOLEAN;
			case LIST, LIST_REFERENCE, LIST_TARGET -> LIST;
			case MAP, MAP_REFERENCE, MAP_TARGET -> MAP;
			case VARIABLE_NAME, FIXED_NAME, FREQUENCY -> ANY;
			case VALUE -> switch (op) {
				case ADD, EQUALS, LESS, GREATER, JOIN, PARSE_NUMBER, PICK_LIST, SET, LIST_ADD, MAP_SET, MAP_REMOVE, MAP_GET,
					MAP_HAS, DISPLAY, DISPLAY_LINES, SET_LINE, LIST, MAP, TO_TEXT, TO_NUMBER, TO_ITEM, TO_LIST, TO_MAP -> ANY;
				case LIST_SET -> index == 1 ? NUMBER : ANY;
				default -> of(slot.dataType());
			};
		};
	}

	private static ScratchValueShape of(DataType type) {
		return switch (type) {
			case NUMBER -> NUMBER;
			case TEXT -> TEXT;
			case BOOLEAN -> BOOLEAN;
			case ITEM -> ITEM;
		};
	}

}
