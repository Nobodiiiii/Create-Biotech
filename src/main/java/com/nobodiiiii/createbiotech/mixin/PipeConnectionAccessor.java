package com.nobodiiiii.createbiotech.mixin;

import java.util.Optional;

import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.FluidNetwork;
import com.simibubi.create.content.fluids.PipeConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PipeConnection.class)
public interface PipeConnectionAccessor {
	// Both fields erase to java.util.Optional, with no mapped Minecraft descriptor types.
	@Accessor(value = "source", remap = false)
	Optional<FlowSource> createBiotech$getSource();

	@Accessor(value = "source", remap = false)
	void createBiotech$setSource(Optional<FlowSource> source);

	@Accessor(value = "network", remap = false)
	void createBiotech$setNetwork(Optional<FluidNetwork> network);
}
