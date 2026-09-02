package com.nobodiiiii.createbiotech.content.processing.basin;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Separates a basin's ordinary internal inventory from the views exposed at machine boundaries.
 */
public interface BasinInternalItemAccess {

	IItemHandlerModifiable createBiotech$getInternalItemHandler();

	IItemHandlerModifiable createBiotech$getFunnelItemHandler();
}
