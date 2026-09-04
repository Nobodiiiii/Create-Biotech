package com.nobodiiiii.createbiotech.content.processing.basin;

/**
 * Names the three inventory contracts exposed by a basin.
 */
public enum BasinItemHandlerAccess {
	/** Basin-owned recipe and migration code; captured-slime control items are ordinary items. */
	INTERNAL,
	/** General block capability; captured-slime control items are hidden and immutable. */
	EXTERNAL,
	/** Create funnel extraction; control items are visible for extraction but cannot be inserted. */
	FUNNEL
}
